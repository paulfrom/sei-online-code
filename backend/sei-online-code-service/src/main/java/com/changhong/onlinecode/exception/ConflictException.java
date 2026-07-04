package com.changhong.onlinecode.exception;

/**
 * 冲突异常（契约 §6）。
 *
 * <p>对应 HTTP 409，由 PreBuildExceptionHandler 全局映射。当前两类场景：BUILDING 态禁止编辑/重生、
 * Skill 导入同名冲突（Phase 3 name 去重）。</p>
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
