package com.changhong.onlinecode.service.agent;

/**
 * 工作区租约取得结果。
 *
 * @param acquired   是否成功取得租约
 * @param busyRunId  ACQUIRED 时为 null；BUSY 时为当前持有租约的 Run id
 */
public record AcquireResult(boolean acquired, String busyRunId) {
    public static AcquireResult ok() {
        return new AcquireResult(true, null);
    }

    public static AcquireResult busy(String busyRunId) {
        return new AcquireResult(false, busyRunId);
    }
}
