package com.changhong.onlinecode.exception;

/**
 * 冲突异常（契约 §6）。
 *
 * <p>用于 BUILDING 态禁止编辑/重生的场景，对应 HTTP 409。由 PreBuildExceptionHandler 映射（T14）。
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
