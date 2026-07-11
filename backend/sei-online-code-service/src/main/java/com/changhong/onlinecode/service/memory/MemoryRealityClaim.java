package com.changhong.onlinecode.service.memory;

import lombok.Data;

/**
 * 代码现状和事实声明。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §7.2。
 *
 * @author sei-online-code
 */
@Data
public class MemoryRealityClaim {

    private String id;

    private String type;

    private String content;

    private String source;

    private String sourceHash;

    /** source_backed。 */
    private String confidence;
}
