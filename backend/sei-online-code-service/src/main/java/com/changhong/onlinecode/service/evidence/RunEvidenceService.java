package com.changhong.onlinecode.service.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.changhong.onlinecode.agent.CliRunResult;
import com.changhong.onlinecode.dao.CodingTaskDao;
import com.changhong.onlinecode.dao.RunEvidenceDao;
import com.changhong.onlinecode.dto.enums.EvidenceCompleteness;
import com.changhong.onlinecode.dto.enums.RunArtifactState;
import com.changhong.onlinecode.dto.enums.RunArtifactType;
import com.changhong.onlinecode.dto.enums.RunEvidenceStatus;
import com.changhong.onlinecode.dto.evidence.RunEvidenceDto;
import com.changhong.onlinecode.entity.CodingTask;
import com.changhong.onlinecode.entity.Run;
import com.changhong.onlinecode.entity.RunArtifact;
import com.changhong.onlinecode.entity.RunEvidence;
import com.changhong.sei.core.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 在 Runner 返回后，把进程结果、Git 状态和验收结果固化成版本化证据包。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RunEvidenceService {

    private final RunEvidenceDao evidenceDao;
    private final CodingTaskDao codingTaskDao;
    private final RunArtifactService artifactService;
    private final RunLogRedactor redactor;

    @Value("${online-code.evidence.max-diff-bytes:5242880}")
    private int maxDiffBytes;

    @Transactional
    public RunEvidence capture(Run run, CliRunResult result, Path workspace) {
        if (run == null || run.getId() == null) {
            return null;
        }
        List<RunArtifact> artifacts = new ArrayList<>(artifactService.findByRunId(run.getId()));
        String finalOutput = redactor.redact(firstNonBlank(
                result == null ? null : result.getOutput(),
                result == null ? null : result.getFailureReason()));
        if (finalOutput != null) {
            artifacts.add(artifactService.storeTextArtifact(
                    run.getId(), RunArtifactType.FINAL_OUTPUT, "text/plain; charset=utf-8",
                    finalOutput, EvidenceCompleteness.COMPLETE));
        }

        GitSnapshot git = captureGit(workspace, run.getBaseCommit());
        if (git.content() != null) {
            artifacts.add(artifactService.storeTextArtifact(
                    run.getId(), RunArtifactType.GIT_DIFF, "text/x-diff; charset=utf-8",
                    redactor.redact(git.content()), git.completeness()));
        }

        JsonNode report = readJsonReport(result == null ? null : result.getOutput());
        CodingTask task = run.getCodingTaskId() == null ? null : codingTaskDao.findOne(run.getCodingTaskId());
        JsonNode commands = report != null && report.path("commands").isArray()
                ? report.path("commands") : JsonUtils.mapper().createArrayNode();
        JsonNode findings = report != null && report.path("findings").isArray()
                ? report.path("findings") : JsonUtils.mapper().createArrayNode();
        CriteriaSnapshot criteria = acceptanceCriteria(report, task);

        EvidenceCompleteness completeness = completeness(
                result, artifacts, git.completeness(), criteria.complete());
        int version = evidenceDao.findTopByRunIdOrderByEvidenceVersionDesc(run.getId())
                .map(existing -> existing.getEvidenceVersion() + 1)
                .orElse(1);
        RunEvidence evidence = new RunEvidence();
        evidence.setRunId(run.getId());
        evidence.setEvidenceVersion(version);
        evidence.setStatus(completeness == EvidenceCompleteness.COMPLETE
                ? RunEvidenceStatus.READY : RunEvidenceStatus.PARTIAL);
        evidence.setCompleteness(completeness);
        evidence.setProcessExitCode(result == null ? null : result.getExitCode());
        evidence.setGitBaseCommit(run.getBaseCommit());
        evidence.setGitHeadCommit(git.headCommit());
        evidence.setSummary(report == null
                ? firstNonBlank(finalOutput, "Runner 未返回可解析的结构化结果")
                : report.path("summary").asText(firstNonBlank(finalOutput, "")));
        evidence.setCommandResultsJson(writeJson(commands));
        evidence.setAcceptanceCriteriaJson(writeJson(criteria.values()));
        evidence.setFindingsJson(writeJson(findings));
        evidence.setArtifactRefsJson(writeJson(artifactRefs(artifacts)));
        evidence.setCapturedAt(new Date());
        return evidenceDao.save(evidence);
    }

    @Transactional(readOnly = true)
    public RunEvidence findLatest(String runId) {
        return evidenceDao.findTopByRunIdOrderByEvidenceVersionDesc(runId).orElse(null);
    }

    @Transactional(readOnly = true)
    public RunEvidenceDto findLatestDto(String runId) {
        RunEvidence evidence = findLatest(runId);
        if (evidence == null) {
            return null;
        }
        RunEvidenceDto dto = new RunEvidenceDto();
        dto.setId(evidence.getId());
        dto.setRunId(evidence.getRunId());
        dto.setEvidenceVersion(evidence.getEvidenceVersion());
        dto.setStatus(evidence.getStatus());
        dto.setCompleteness(evidence.getCompleteness());
        dto.setProcessExitCode(evidence.getProcessExitCode());
        dto.setGitBaseCommit(evidence.getGitBaseCommit());
        dto.setGitHeadCommit(evidence.getGitHeadCommit());
        dto.setSummary(evidence.getSummary());
        dto.setCommandResultsJson(evidence.getCommandResultsJson());
        dto.setAcceptanceCriteriaJson(evidence.getAcceptanceCriteriaJson());
        dto.setFindingsJson(evidence.getFindingsJson());
        dto.setCapturedAt(evidence.getCapturedAt());
        dto.setArtifacts(artifactService.findByRunId(runId).stream()
                .map(artifactService::toDto)
                .toList());
        return dto;
    }

    private CriteriaSnapshot acceptanceCriteria(JsonNode report, CodingTask task) {
        JsonNode reported = report == null ? null : firstArray(
                report.path("acceptanceCriteria"),
                report.path("acceptanceResults"));
        if (reported != null) {
            boolean complete = true;
            for (JsonNode item : reported) {
                String status = item.path("status").asText("").toUpperCase();
                if (!List.of("PASSED", "FAILED", "NOT_APPLICABLE").contains(status)) {
                    complete = false;
                }
            }
            return new CriteriaSnapshot(reported, complete);
        }
        ArrayNode values = JsonUtils.mapper().createArrayNode();
        if (task == null || task.getAcceptanceCriteria() == null
                || task.getAcceptanceCriteria().isEmpty()) {
            return new CriteriaSnapshot(values, true);
        }
        for (String criterion : task.getAcceptanceCriteria()) {
            ObjectNode value = values.addObject();
            value.put("criterion", criterion);
            value.put("status", "NOT_VERIFIED");
            value.set("evidence", JsonUtils.mapper().createArrayNode());
        }
        return new CriteriaSnapshot(values, false);
    }

    private JsonNode firstArray(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate != null && candidate.isArray() && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private EvidenceCompleteness completeness(CliRunResult result, List<RunArtifact> artifacts,
                                               EvidenceCompleteness gitCompleteness,
                                               boolean criteriaComplete) {
        boolean hasRawLog = artifacts.stream().anyMatch(artifact ->
                artifact.getArtifactType() == RunArtifactType.RAW_LOG);
        boolean hasFinalOutput = artifacts.stream().anyMatch(artifact ->
                artifact.getArtifactType() == RunArtifactType.FINAL_OUTPUT);
        boolean hasGitDiff = artifacts.stream().anyMatch(artifact ->
                artifact.getArtifactType() == RunArtifactType.GIT_DIFF);
        boolean truncated = artifacts.stream().anyMatch(artifact ->
                artifact.getState() == RunArtifactState.TRUNCATED);
        if (truncated || gitCompleteness == EvidenceCompleteness.TRUNCATED) {
            return EvidenceCompleteness.TRUNCATED;
        }
        if (result == null || result.getExitCode() == null || !hasRawLog
                || !hasFinalOutput || !hasGitDiff
                || gitCompleteness == EvidenceCompleteness.MISSING || !criteriaComplete) {
            return EvidenceCompleteness.PARTIAL;
        }
        return EvidenceCompleteness.COMPLETE;
    }

    private GitSnapshot captureGit(Path workspace, String baseCommit) {
        if (workspace == null) {
            return new GitSnapshot(null, null, EvidenceCompleteness.MISSING);
        }
        try {
            CommandResult head = run(workspace, "git", "rev-parse", "HEAD");
            CommandResult status = run(workspace, "git", "status", "--porcelain=v1");
            List<String> diffCommand = new ArrayList<>(List.of("git", "diff", "--binary"));
            if (baseCommit != null && !baseCommit.isBlank()
                    && !baseCommit.chars().allMatch(ch -> ch == '0')) {
                diffCommand.add(baseCommit);
            } else {
                diffCommand.add("HEAD");
            }
            CommandResult diff = run(workspace, diffCommand.toArray(String[]::new));
            String content = "# git status --porcelain=v1\n" + status.output()
                    + "\n# git diff --binary " + diffCommand.get(diffCommand.size() - 1)
                    + "\n" + diff.output();
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > maxDiffBytes) {
                content = new String(bytes, 0, maxDiffBytes, StandardCharsets.UTF_8)
                        + "\n\n[DIFF TRUNCATED]\n";
                return new GitSnapshot(head.output().trim(), content, EvidenceCompleteness.TRUNCATED);
            }
            EvidenceCompleteness completeness = head.exitCode() == 0
                    && status.exitCode() == 0 && diff.exitCode() == 0
                    ? EvidenceCompleteness.COMPLETE : EvidenceCompleteness.PARTIAL;
            return new GitSnapshot(head.output().trim(), content, completeness);
        } catch (Exception e) {
            log.warn("git evidence capture failed: workspace={}", workspace, e);
            return new GitSnapshot(null, "Git evidence capture failed: " + e.getMessage(),
                    EvidenceCompleteness.MISSING);
        }
    }

    private CommandResult run(Path cwd, String... command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new CommandResult(exitCode, output);
    }

    private JsonNode readJsonReport(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                value = value.substring(firstNewline + 1, lastFence).trim();
            }
        }
        try {
            JsonNode root = JsonUtils.mapper().readTree(value);
            return root != null && root.isObject() ? root : null;
        } catch (Exception ignored) {
            int start = value.indexOf('{');
            int end = value.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    JsonNode root = JsonUtils.mapper().readTree(value.substring(start, end + 1));
                    return root != null && root.isObject() ? root : null;
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    private List<Map<String, Object>> artifactRefs(List<RunArtifact> artifacts) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (RunArtifact artifact : artifacts) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("artifactId", artifact.getId());
            ref.put("type", artifact.getArtifactType());
            ref.put("state", artifact.getState());
            ref.put("completeness", artifact.getCompleteness());
            ref.put("sha256", artifact.getSha256());
            refs.add(ref);
        }
        return refs;
    }

    private String writeJson(Object value) {
        try {
            return JsonUtils.mapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 Run evidence 失败", e);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record CriteriaSnapshot(JsonNode values, boolean complete) {
    }

    private record GitSnapshot(String headCommit, String content,
                               EvidenceCompleteness completeness) {
    }

    private record CommandResult(int exitCode, String output) {
    }
}
