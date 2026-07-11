package com.changhong.onlinecode.service.memory;

import lombok.Data;

import java.util.List;

/**
 * 工作区规范聚合。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §5.1。
 *
 * @author sei-online-code
 */
@Data
public class WorkspaceNorms {

    private List<MemoryNormClaim> projectMemoryOverrides;

    private List<MemoryNormClaim> hardRules;

    private List<MemoryNormClaim> preferredDirection;

    private List<MemoryNormClaim> forbiddenChoices;

    private List<MemoryNormClaim> documentationRules;

    private List<MemoryNormClaim> testingAndDeliveryRules;

    private List<String> sourceFiles;
}
