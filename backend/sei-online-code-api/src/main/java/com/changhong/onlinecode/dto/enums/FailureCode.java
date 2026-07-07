package com.changhong.onlinecode.dto.enums;

/**
 * 失败码。
 */
public enum FailureCode {
    AGENT_TIMEOUT,
    AGENT_JSON_PARSE_FAILED,
    AGENT_PROCESS_EXIT_NONZERO,
    UPSTREAM_MISSING,
    CHAIN_BROKEN,
    BUILD_TIMEOUT,
    BUILD_CONFLICT,
    UNKNOWN
}
