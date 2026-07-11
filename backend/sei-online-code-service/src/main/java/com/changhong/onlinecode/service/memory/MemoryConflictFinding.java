package com.changhong.onlinecode.service.memory;

import lombok.Data;

import java.util.List;

/**
 * 规范、需求与代码现状之间的冲突。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §7.3。
 *
 * @author sei-online-code
 */
@Data
public class MemoryConflictFinding {

    private String id;

    private String type;

    /** HIGH / MEDIUM / LOW。 */
    private String severity;

    private String summary;

    private List<String> normClaimIds;

    private List<String> realityClaimIds;

    private String recommendedHandling;
}
