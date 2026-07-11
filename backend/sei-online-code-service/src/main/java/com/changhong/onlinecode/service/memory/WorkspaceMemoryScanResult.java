package com.changhong.onlinecode.service.memory;

import lombok.Data;

import java.util.List;

/**
 * WorkspaceMemoryScannerService 扫描产出。
 *
 * @author sei-online-code
 */
@Data
public class WorkspaceMemoryScanResult {

    private String agentMemoryFingerprint;
    private String agentMemoryMarkdown;

    private String projectRuleFingerprint;
    private String projectRuleMarkdown;

    private String sourceFingerprintsJson;

    private List<MemoryNormClaim> normClaims;
    private List<MemoryRealityClaim> realityClaims;
    private List<MemoryConflictFinding> conflictFindings;

    private WorkspaceNorms workspaceNorms;
    private WorkspaceSnapshot workspaceSnapshot;

    private boolean scanTruncated;
    private String truncatedReason;
}
