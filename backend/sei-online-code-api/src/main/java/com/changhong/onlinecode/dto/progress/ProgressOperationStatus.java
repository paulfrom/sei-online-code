package com.changhong.onlinecode.dto.progress;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 进度操作结果状态（EXE-002）。
 */
@Schema(description = "进度操作结果状态")
public enum ProgressOperationStatus {

    /** 操作成功。 */
    OK,

    /** 旧 owner / 过期 claim / 旧 fencing token——拒绝写入，不产生部分状态。 */
    STALE_OWNER,

    /** 目标实体不存在。 */
    NOT_FOUND,

    /** 唯一键或请求 hash 冲突。 */
    CONFLICT,

    /** 当前状态不允许该操作（如 VERIFIED 回退、状态跃迁非法）。 */
    INVALID_STATE
}
