package com.changhong.onlinecode.service.memory;

import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CodingTask 后代码变更采集结果。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §16.2。
 *
 * <p>由 {@link CodingTaskChangeCollector} 产出，供 {@link CodingTaskMemoryUpdateAssembler}
 * 做增量记忆更新。采集失败时 {@code success=false}，调用方负责降级到全量重建。</p>
 *
 * <p>除变更文件列表外，还携带每个文件相对 HEAD 的变更状态（A/M/D/R，参见 {@link ChangeStatus}），
 * 用于回写时移除已删除或重命名文件的旧 RealityClaim、snapshot source 与 fingerprint，
 * 避免代码现状随多轮回写逐渐失真。</p>
 *
 * @author sei-online-code
 */
@Data
public class CodingTaskChangeResult {

    /** 是否成功采集。 */
    private boolean success = true;

    /** 失败原因（success=false 时有效）。 */
    private String failureReason;

    /** 工作区 HEAD commit。 */
    private String headCommit;

    /** 用于 diff 的 base commit；为空表示与父提交比较。 */
    private String baseCommit;

    /** 变更文件相对路径列表（git diff --name-only）。 */
    private List<String> changedFiles = Collections.emptyList();

    /**
     * 变更文件路径 → 变更状态（A/M/D/R）。仅记录已明确解析出状态的文件；
     * 未在 Map 中的变更文件默认按 {@link ChangeStatus#MODIFIED} 处理，保持向后兼容。
     */
    private Map<String, ChangeStatus> changeStatuses = Collections.emptyMap();

    /** git diff --stat 输出。 */
    private String diffStat;

    /** git diff 摘要文本（受预算截断）。 */
    private String diffSummary;

    /** 变更文件路径 → 文件片段（受预算限制，可能为空）。 */
    private Map<String, String> fileSnippets = Collections.emptyMap();

    /**
     * 变更状态枚举。值与 {@code git diff --name-status} 第一列对应。
     *
     * @author sei-online-code
     */
    public enum ChangeStatus {
        /** 新增文件（git status: A）。 */
        ADDED,
        /** 修改文件（git status: M）。 */
        MODIFIED,
        /** 删除文件（git status: D）。 */
        DELETED,
        /** 重命名文件（git status: R）。重命名时由 collector 同时记录旧路径（DELETED）与新路径（ADDED）。 */
        RENAMED
    }

    /**
     * 构造失败结果。
     *
     * @param reason 失败原因
     * @return 失败结果
     */
    public static CodingTaskChangeResult failure(String reason) {
        CodingTaskChangeResult result = new CodingTaskChangeResult();
        result.setSuccess(false);
        result.setFailureReason(reason);
        return result;
    }

    /**
     * 返回被删除的旧路径集合（含重命名前旧路径），用于回写时移除旧 RealityClaim。
     *
     * @return 不可变路径集合
     */
    public java.util.Set<String> deletedPaths() {
        if (changeStatuses == null || changeStatuses.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        java.util.Set<String> deleted = new java.util.LinkedHashSet<>();
        changeStatuses.forEach((path, status) -> {
            if (status == ChangeStatus.DELETED) {
                deleted.add(path);
            }
        });
        return deleted;
    }
}
