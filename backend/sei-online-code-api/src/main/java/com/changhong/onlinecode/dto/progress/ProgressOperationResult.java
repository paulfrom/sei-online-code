package com.changhong.onlinecode.dto.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 进度操作结果（EXE-002）。承载状态、消息与可选数据。
 */
@Data
@Schema(description = "进度操作结果")
public class ProgressOperationResult<T> {

    @Schema(description = "结果状态")
    private ProgressOperationStatus status;

    @Schema(description = "消息（失败原因等）")
    private String message;

    @Schema(description = "成功时的数据")
    private T data;

    public boolean isOk() {
        return status == ProgressOperationStatus.OK;
    }

    public boolean isStaleOwner() {
        return status == ProgressOperationStatus.STALE_OWNER;
    }

    public static <T> ProgressOperationResult<T> ok(T data) {
        ProgressOperationResult<T> result = new ProgressOperationResult<>();
        result.status = ProgressOperationStatus.OK;
        result.data = data;
        return result;
    }

    public static <T> ProgressOperationResult<T> staleOwner(String message) {
        ProgressOperationResult<T> result = new ProgressOperationResult<>();
        result.status = ProgressOperationStatus.STALE_OWNER;
        result.message = message;
        return result;
    }

    public static <T> ProgressOperationResult<T> notFound(String message) {
        ProgressOperationResult<T> result = new ProgressOperationResult<>();
        result.status = ProgressOperationStatus.NOT_FOUND;
        result.message = message;
        return result;
    }

    public static <T> ProgressOperationResult<T> invalidState(String message) {
        ProgressOperationResult<T> result = new ProgressOperationResult<>();
        result.status = ProgressOperationStatus.INVALID_STATE;
        result.message = message;
        return result;
    }

    public static <T> ProgressOperationResult<T> conflict(String message) {
        ProgressOperationResult<T> result = new ProgressOperationResult<>();
        result.status = ProgressOperationStatus.CONFLICT;
        result.message = message;
        return result;
    }
}
