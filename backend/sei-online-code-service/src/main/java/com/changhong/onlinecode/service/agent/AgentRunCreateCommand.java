package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dto.enums.TriggerSource;
import lombok.Data;

/**
 * Agent Run 创建命令。
 *
 * @author sei-online-code
 */
@Data
public class AgentRunCreateCommand {

    private String taskId;
    private String codingTaskId;
    private String requirementId;
    private String iterationId;
    private String loopId;
    private TriggerSource triggerSource;
    private String userPrompt;
    private String memoryContextId;
    private String workspaceMemoryId;
    private String worktreePath;
    private String baseCommit;
    private String agentId;
    private String agentName;
    private String cliTool;
    private String model;
}
