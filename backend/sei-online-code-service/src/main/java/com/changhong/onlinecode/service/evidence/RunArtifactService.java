package com.changhong.onlinecode.service.evidence;

import com.changhong.onlinecode.dao.RunArtifactChunkDao;
import com.changhong.onlinecode.dao.RunArtifactDao;
import com.changhong.onlinecode.dto.enums.EvidenceCompleteness;
import com.changhong.onlinecode.dto.enums.RunArtifactState;
import com.changhong.onlinecode.dto.enums.RunArtifactType;
import com.changhong.onlinecode.dto.evidence.RunArtifactDto;
import com.changhong.onlinecode.dto.evidence.RunLogFramePageDto;
import com.changhong.onlinecode.dto.run.RunLogFrame;
import com.changhong.onlinecode.entity.RunArtifact;
import com.changhong.onlinecode.entity.RunArtifactChunk;
import com.changhong.sei.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 数据库存储的 ArtifactStore 实现。所有内容先脱敏，再 gzip 分块持久化。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RunArtifactService {

    private final RunArtifactDao artifactDao;
    private final RunArtifactChunkDao chunkDao;
    private final Map<String, Object> runLocks = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> logSequences = new ConcurrentHashMap<>();

    @Value("${online-code.evidence.max-log-bytes:10485760}")
    private long maxLogBytes;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunLogFrame appendLogFrame(RunLogFrame frame) {
        if (frame == null || frame.getRunId() == null || frame.getRunId().isBlank()) {
            return frame;
        }
        Object lock = runLocks.computeIfAbsent(frame.getRunId(), ignored -> new Object());
        synchronized (lock) {
            RunArtifact artifact = artifactDao
                    .findTopByRunIdAndArtifactTypeOrderByCreatedDateDesc(
                            frame.getRunId(), RunArtifactType.RAW_LOG)
                    .orElseGet(() -> createArtifact(
                            frame.getRunId(), RunArtifactType.RAW_LOG,
                            "application/x-ndjson", RunArtifactState.OPEN));
            long sequence = logSequences.computeIfAbsent(
                    frame.getRunId(),
                    ignored -> new AtomicLong(Objects.requireNonNullElse(
                            artifact.getChunkCount(), 0))).incrementAndGet();
            frame.setSequenceNo(sequence);
            byte[] plain = serializeFrame(frame);
            long currentBytes = Objects.requireNonNullElse(artifact.getByteLength(), 0L);
            boolean terminal = isTerminal(frame);
            if ((currentBytes + plain.length <= maxLogBytes
                    && artifact.getState() != RunArtifactState.TRUNCATED) || terminal) {
                saveChunk(artifact.getId(), sequence, sequence, sequence, plain);
                artifact.setByteLength(currentBytes + plain.length);
                artifact.setChunkCount(Objects.requireNonNullElse(artifact.getChunkCount(), 0) + 1);
            } else {
                artifact.setState(RunArtifactState.TRUNCATED);
                artifact.setCompleteness(EvidenceCompleteness.TRUNCATED);
            }
            if (terminal) {
                if (artifact.getState() == RunArtifactState.OPEN) {
                    artifact.setState(RunArtifactState.COMPLETE);
                    artifact.setCompleteness(EvidenceCompleteness.COMPLETE);
                }
                artifact.setCompletedAt(new Date());
                artifact.setSha256(computeSha256(artifact.getId()));
                runLocks.remove(frame.getRunId(), lock);
                logSequences.remove(frame.getRunId());
            }
            artifactDao.save(artifact);
            return frame;
        }
    }

    @Transactional
    public RunArtifact storeTextArtifact(String runId, RunArtifactType type,
                                         String contentType, String content,
                                         EvidenceCompleteness completeness) {
        RunArtifact artifact = createArtifact(runId, type, contentType, RunArtifactState.OPEN);
        byte[] bytes = Objects.toString(content, "").getBytes(StandardCharsets.UTF_8);
        saveChunk(artifact.getId(), 1L, null, null, bytes);
        artifact.setByteLength((long) bytes.length);
        artifact.setChunkCount(1);
        artifact.setCompleteness(completeness);
        artifact.setState(switch (completeness) {
            case TRUNCATED -> RunArtifactState.TRUNCATED;
            case MISSING -> RunArtifactState.FAILED;
            default -> RunArtifactState.COMPLETE;
        });
        artifact.setSha256(sha256(bytes));
        artifact.setCompletedAt(new Date());
        return artifactDao.save(artifact);
    }

    @Transactional(readOnly = true)
    public List<RunArtifact> findByRunId(String runId) {
        return artifactDao.findByRunIdOrderByCreatedDateAsc(runId);
    }

    @Transactional(readOnly = true)
    public String readText(String artifactId) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (RunArtifactChunk chunk : chunkDao.findByArtifactIdOrderBySequenceNoAsc(artifactId)) {
            try {
                output.write(decompress(chunk.getCompressedData()));
            } catch (IOException e) {
                throw new IllegalStateException("读取 Run artifact 失败: " + artifactId, e);
            }
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public RunLogFramePageDto readLogFrames(String runId, long afterSequence, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 2_000));
        RunLogFramePageDto page = new RunLogFramePageDto();
        page.setRunId(runId);
        page.setAfterSequence(afterSequence);
        RunArtifact artifact = artifactDao
                .findTopByRunIdAndArtifactTypeOrderByCreatedDateDesc(runId, RunArtifactType.RAW_LOG)
                .orElse(null);
        if (artifact == null) {
            page.setNextSequence(afterSequence);
            page.setTruncated(false);
            return page;
        }
        List<RunLogFrame> frames = new ArrayList<>();
        long next = afterSequence;
        for (RunArtifactChunk chunk : chunkDao
                .findByArtifactIdAndLastLogSequenceGreaterThanOrderBySequenceNoAsc(
                        artifact.getId(), afterSequence)) {
            if (frames.size() >= limit) {
                break;
            }
            try {
                String json = new String(decompress(chunk.getCompressedData()), StandardCharsets.UTF_8).trim();
                if (json.isEmpty()) {
                    continue;
                }
                RunLogFrame frame = JsonUtils.mapper().readValue(json, RunLogFrame.class);
                frames.add(frame);
                next = Math.max(next, Objects.requireNonNullElse(frame.getSequenceNo(), next));
            } catch (Exception e) {
                log.warn("ignoring unreadable log chunk: artifactId={}, chunkId={}",
                        artifact.getId(), chunk.getId(), e);
            }
        }
        page.setFrames(frames);
        page.setNextSequence(next);
        page.setTruncated(artifact.getState() == RunArtifactState.TRUNCATED);
        return page;
    }

    public RunArtifactDto toDto(RunArtifact artifact) {
        RunArtifactDto dto = new RunArtifactDto();
        dto.setId(artifact.getId());
        dto.setRunId(artifact.getRunId());
        dto.setArtifactType(artifact.getArtifactType());
        dto.setState(artifact.getState());
        dto.setCompleteness(artifact.getCompleteness());
        dto.setContentType(artifact.getContentType());
        dto.setEncoding(artifact.getEncoding());
        dto.setByteLength(artifact.getByteLength());
        dto.setChunkCount(artifact.getChunkCount());
        dto.setSha256(artifact.getSha256());
        dto.setRedacted(artifact.getRedacted());
        dto.setMetadataJson(artifact.getMetadataJson());
        dto.setCompletedAt(artifact.getCompletedAt());
        dto.setExpiresAt(artifact.getExpiresAt());
        dto.setCreatedDate(artifact.getCreatedDate());
        dto.setLastEditedDate(artifact.getLastEditedDate());
        return dto;
    }

    private RunArtifact createArtifact(String runId, RunArtifactType type,
                                       String contentType, RunArtifactState state) {
        RunArtifact artifact = new RunArtifact();
        artifact.setRunId(runId);
        artifact.setArtifactType(type);
        artifact.setState(state);
        artifact.setCompleteness(state == RunArtifactState.OPEN
                ? EvidenceCompleteness.PARTIAL : EvidenceCompleteness.COMPLETE);
        artifact.setContentType(contentType);
        artifact.setEncoding("gzip");
        artifact.setByteLength(0L);
        artifact.setChunkCount(0);
        artifact.setRedacted(Boolean.TRUE);
        artifact.setMetadataJson("{\"schemaVersion\":1,\"storage\":\"database-gzip-chunks\"}");
        return artifactDao.save(artifact);
    }

    private void saveChunk(String artifactId, long sequence, Long firstLogSequence,
                           Long lastLogSequence, byte[] plain) {
        RunArtifactChunk chunk = new RunArtifactChunk();
        chunk.setArtifactId(artifactId);
        chunk.setSequenceNo(sequence);
        chunk.setFirstLogSequence(firstLogSequence);
        chunk.setLastLogSequence(lastLogSequence);
        chunk.setUncompressedLength(plain.length);
        chunk.setCompressedData(compress(plain));
        chunkDao.save(chunk);
    }

    private byte[] serializeFrame(RunLogFrame frame) {
        try {
            return (JsonUtils.mapper().writeValueAsString(frame) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 Run 日志失败", e);
        }
    }

    private boolean isTerminal(RunLogFrame frame) {
        return frame.getState() != null && !frame.getState().isBlank();
    }

    private byte[] compress(byte[] value) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(value);
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("压缩 Run artifact 失败", e);
        }
    }

    private byte[] decompress(byte[] value) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(value))) {
            return gzip.readAllBytes();
        }
    }

    private String computeSha256(String artifactId) {
        MessageDigest digest = newDigest();
        for (RunArtifactChunk chunk : chunkDao.findByArtifactIdOrderBySequenceNoAsc(artifactId)) {
            try {
                digest.update(decompress(chunk.getCompressedData()));
            } catch (IOException e) {
                throw new IllegalStateException("计算 Run artifact 摘要失败", e);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String sha256(byte[] value) {
        MessageDigest digest = newDigest();
        return HexFormat.of().formatHex(digest.digest(value));
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }
}
