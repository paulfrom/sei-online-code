package com.changhong.onlinecode.service.memory;

import com.changhong.onlinecode.dto.enums.WorkspaceMemoryFreshness;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * WorkspaceMemory 新鲜度检查器。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.2、§12.1。
 *
 * <p>通过重新扫描工作区并比对上一版 CURRENT 记忆的 agent-memory、项目规范、代码现状指纹，
 * 判定是否需要刷新。不修改 WorkspaceMemory，仅返回目标 freshness。</p>
 *
 * <p>WHY：此前版本中保留了三套独立的 {@code compute*Fingerprint} 私有方法，但 {@code check}
 * 实际重新构造 {@link WorkspaceMemoryScannerService} 做全量扫描，这些私有方法从未参与判断，
 * 形成死代码并增加维护歧义。本版本直接复用 scanner 产出的指纹，删除死代码。</p>
 *
 * @author sei-online-code
 */
@Component
public class WorkspaceMemoryFreshnessChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceMemoryFreshnessChecker.class);

    /** 平台当前 memory spec 版本。模板/规范破坏性变化时递增。 */
    static final int CURRENT_MEMORY_SPEC_VERSION = 1;

    private final WorkspaceMemoryScannerService scannerService;

    public WorkspaceMemoryFreshnessChecker(WorkspaceMemoryScannerService scannerService) {
        this.scannerService = scannerService;
    }

    /**
     * 检查当前 WorkspaceMemory 是否仍新鲜。
     *
     * @param current       当前 CURRENT WorkspaceMemory（不可为 null）
     * @param workspacePath 工作区根目录
     * @return 目标 freshness
     */
    public WorkspaceMemoryFreshness check(WorkspaceMemory current, String workspacePath) {
        Objects.requireNonNull(current, "current WorkspaceMemory 不能为空");
        if (current.getFreshness() == WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT) {
            return WorkspaceMemoryFreshness.PLATFORM_MEMORY_DRIFT;
        }
        if (!Objects.equals(current.getMemorySpecVersion(), CURRENT_MEMORY_SPEC_VERSION)) {
            return WorkspaceMemoryFreshness.STALE_BY_SPEC_CHANGE;
        }
        if (workspacePath == null || workspacePath.isBlank()) {
            return current.getFreshness();
        }
        WorkspaceMemoryScanResult scan = scannerService.scan(current.getProjectId(), workspacePath);

        String agentMemoryFingerprint = scan.getAgentMemoryFingerprint();
        String projectRuleFingerprint = scan.getProjectRuleFingerprint();
        String sourceFingerprintsJson = scan.getSourceFingerprintsJson();

        if (!Objects.equals(current.getAgentMemoryFingerprint(), agentMemoryFingerprint)) {
            return WorkspaceMemoryFreshness.STALE_BY_PROJECT_MEMORY_CHANGE;
        }
        if (!Objects.equals(current.getProjectRuleFingerprint(), projectRuleFingerprint)) {
            return WorkspaceMemoryFreshness.STALE_BY_RULE_CHANGE;
        }
        if (!Objects.equals(current.getSourceFingerprintsJson(), sourceFingerprintsJson)) {
            return WorkspaceMemoryFreshness.STALE_BY_SOURCE_CHANGE;
        }
        return WorkspaceMemoryFreshness.FRESH;
    }
}
