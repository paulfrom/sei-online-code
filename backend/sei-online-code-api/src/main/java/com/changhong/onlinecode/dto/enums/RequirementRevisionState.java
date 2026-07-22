package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 需求在当前自动化 loop 内的计划修订状态。
 */
@Schema(description = "需求计划修订状态")
public enum RequirementRevisionState {

    /** 当前没有待处理的计划修订。 */
    NONE,

    /** 已收到评论，等待开始修订。 */
    PENDING,

    /** 正在采集任务执行现场。 */
    SNAPSHOTTING,

    /** PM 正在生成计划补丁。 */
    PLANNING,

    /** 正在原子应用计划补丁。 */
    APPLYING,

    /** 修订失败，等待重试或新评论。 */
    FAILED
}
