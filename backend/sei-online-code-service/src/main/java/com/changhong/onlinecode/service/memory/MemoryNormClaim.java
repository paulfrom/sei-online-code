package com.changhong.onlinecode.service.memory;

import lombok.Data;

import java.util.List;

/**
 * 规范、目标、约束、设计意图声明。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §7.1。
 *
 * @author sei-online-code
 */
@Data
public class MemoryNormClaim {

    private String id;

    private String type;

    private String content;

    /** P0 .. P5S。 */
    private String priority;

    private String source;

    private String sourceHash;

    /** explicit | inferred。 */
    private String confidence;

    private List<String> overrides;
}
