package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 记忆记录版本状态。用于 RequirementDesignContext 等。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §8.3。
 *
 * @author sei-online-code
 */
@Schema(description = "记忆记录版本状态")
public enum MemoryRecordStatus {

    /** 当前版本，每个需求唯一。 */
    CURRENT,

    /** 历史归档版本。 */
    ARCHIVED,

    /** 生成失败版本。 */
    FAILED
}