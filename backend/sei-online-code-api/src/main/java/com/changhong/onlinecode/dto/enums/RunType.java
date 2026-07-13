package com.changhong.onlinecode.dto.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Run 类型。
 */
@Schema(description = "Run 类型")
public enum RunType {

    /** 开发任务执行。 */
    DEVELOPMENT,

    /** 验证命令执行。 */
    VALIDATION_COMMAND,

    /** 测试代理评审。 */
    TEST_REVIEW,

    /** PM 计划生成。 */
    PM_PLANNING,

    /** PM 验收。 */
    PM_ACCEPTANCE,

    /** 交付（commit/push/MR）。 */
    DELIVERY
}
