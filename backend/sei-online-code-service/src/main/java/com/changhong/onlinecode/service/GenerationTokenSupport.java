package com.changhong.onlinecode.service;

import java.util.UUID;

/**
 * 为异步生成链路提供稳定 generation token，避免过期回写覆盖最新状态。
 */
public final class GenerationTokenSupport {

    private GenerationTokenSupport() {
    }

    public static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
