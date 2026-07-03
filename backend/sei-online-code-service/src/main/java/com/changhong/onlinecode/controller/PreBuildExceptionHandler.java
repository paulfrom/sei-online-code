package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.exception.ConflictException;
import com.changhong.sei.core.dto.ResultData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 编码前流程异常处理（D1）。{@link ConflictException} → HTTP 409 + {@code ResultData.fail(msg)}。
 *
 * <p>仅用于编码执行互斥（P12/P12a）+ BUILDING 态编辑拒绝（P8/P9）。其余业务错误沿用 200+ResultData.fail。
 * 与仓库"全 200"规范的张力标记为待清理项（契约 §6.4）。</p>
 *
 * @author sei-online-code
 */
@RestControllerAdvice
public class PreBuildExceptionHandler {

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ResultData<Void>> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ResultData.fail(e.getMessage()));
    }
}
