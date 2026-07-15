package com.changhong.onlinecode.dto.enums;

/**
 * 学生启用/停用状态枚举。契约 PRD §5.1。
 *
 * @author sei-online-code
 */
public enum StudentStatus {
    /** 启用。 */
    ENABLED,
    /** 停用（已毕业/转学，保留历史数据但常规列表不可见）。 */
    DISABLED
}
