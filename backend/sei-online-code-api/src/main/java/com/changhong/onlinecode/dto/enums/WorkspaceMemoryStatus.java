package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WorkspaceMemory 版本状态。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.2。
 *
 * <p>每个 {@code project_id} 同一时间只有一个 {@link #CURRENT}；重建成功时新版本置 CURRENT、
 * 旧版本置 {@link #ARCHIVED}；重建失败新增 {@link #FAILED} 记录，旧 CURRENT 不变。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "工作区记忆版本状态")
public enum WorkspaceMemoryStatus {

    /** 当前版本，每个 project 唯一。 */
    CURRENT,

    /** 历史归档版本，只在 DB 保留。 */
    ARCHIVED,

    /** 生成失败版本。 */
    FAILED
}