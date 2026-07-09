package com.changhong.onlinecode.exception;

/**
 * 技能导入请求非法（400）。
 *
 * @author sei-online-code
 */
public class InvalidSkillImportException extends RuntimeException {
    public InvalidSkillImportException(String message) {
        super(message);
    }
}
