package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.exception.ConflictException;
import com.changhong.onlinecode.exception.InvalidSkillImportException;
import com.changhong.sei.core.dto.ResultData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 编码前流程异常处理（D1）。{@link ConflictException} → HTTP 409 + {@code ResultData.fail(msg)}。
 *
 * <p>全局 {@code @RestControllerAdvice}，覆盖所有控制器。当前两类场景：</p>
 * <ul>
 *   <li>编码执行互斥（P12/P12a）+ BUILDING 态编辑拒绝（P8/P9）</li>
 *   <li>Skill 导入同名冲突（Phase 3 §2 端点 16，name 去重）</li>
 * </ul>
 * <p>其余业务错误沿用 200+ResultData.fail。与仓库"全 200"规范的张力标记为待清理项（契约 §6.4）。</p>
 *
 * @author sei-online-code
 */
@RestControllerAdvice
public class PreBuildExceptionHandler {

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ResultData<Void>> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ResultData.fail(e.getMessage()));
    }

    @ExceptionHandler(InvalidSkillImportException.class)
    public ResponseEntity<ResultData<Void>> handleInvalidSkillImport(InvalidSkillImportException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResultData.fail(e.getMessage()));
    }
}
