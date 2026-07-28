package com.changhong.onlinecode.service.evidence;

import com.changhong.onlinecode.dao.RunArtifactChunkDao;
import com.changhong.onlinecode.dao.RunArtifactDao;
import com.changhong.onlinecode.dto.enums.RunArtifactType;
import com.changhong.onlinecode.dto.evidence.RunLogFramePageDto;
import com.changhong.onlinecode.dto.run.RunLogFrame;
import com.changhong.onlinecode.entity.RunArtifact;
import com.changhong.onlinecode.entity.RunArtifactChunk;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunArtifactServiceTest {

    @Test
    void appendLogFrame_persistsOrderedGzipChunksAndReplaysAfterRun() {
        RunArtifactDao artifactDao = mock(RunArtifactDao.class);
        RunArtifactChunkDao chunkDao = mock(RunArtifactChunkDao.class);
        AtomicReference<RunArtifact> artifactRef = new AtomicReference<>();
        List<RunArtifactChunk> chunks = new ArrayList<>();
        when(artifactDao.findTopByRunIdAndArtifactTypeOrderByCreatedDateDesc(
                "run-1", RunArtifactType.RAW_LOG))
                .thenAnswer(ignored -> Optional.ofNullable(artifactRef.get()));
        when(artifactDao.save(any(RunArtifact.class))).thenAnswer(invocation -> {
            RunArtifact artifact = invocation.getArgument(0);
            if (artifact.getId() == null) {
                artifact.setId("artifact-log");
            }
            artifactRef.set(artifact);
            return artifact;
        });
        when(chunkDao.save(any(RunArtifactChunk.class))).thenAnswer(invocation -> {
            RunArtifactChunk chunk = invocation.getArgument(0);
            chunk.setId("chunk-" + (chunks.size() + 1));
            chunks.add(chunk);
            return chunk;
        });
        when(chunkDao.findByArtifactIdOrderBySequenceNoAsc("artifact-log"))
                .thenAnswer(ignored -> List.copyOf(chunks));
        when(chunkDao.findByArtifactIdAndLastLogSequenceGreaterThanOrderBySequenceNoAsc(
                "artifact-log", 0L)).thenAnswer(ignored -> List.copyOf(chunks));

        RunArtifactService service = new RunArtifactService(artifactDao, chunkDao);
        ReflectionTestUtils.setField(service, "maxLogBytes", 1024L * 1024L);
        RunLogFrame first = frame("run-1", "stdout", "hello", null);
        RunLogFrame terminal = frame("run-1", "system", "DONE", "PREVIEW");

        service.appendLogFrame(first);
        service.appendLogFrame(terminal);
        RunLogFramePageDto page = service.readLogFrames("run-1", 0L, 100);

        assertEquals(1L, first.getSequenceNo());
        assertEquals(2L, terminal.getSequenceNo());
        assertEquals(2, page.getFrames().size());
        assertEquals("hello", page.getFrames().get(0).getLine());
        assertEquals("PREVIEW", page.getFrames().get(1).getState());
        assertNotNull(artifactRef.get().getSha256());
        assertEquals("COMPLETE", artifactRef.get().getCompleteness().name());
    }

    private RunLogFrame frame(String runId, String stream, String line, String state) {
        RunLogFrame frame = new RunLogFrame("log-key", stream, line, "2026-07-28T10:00:00");
        frame.setRunId(runId);
        frame.setState(state);
        return frame;
    }
}
