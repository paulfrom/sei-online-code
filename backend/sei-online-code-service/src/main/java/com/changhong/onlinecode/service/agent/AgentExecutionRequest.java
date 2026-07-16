package com.changhong.onlinecode.service.agent;

import com.changhong.onlinecode.dto.enums.TriggerSource;
import lombok.Data;

/**
 * Agent 执行请求。
 *
 * <p>业务服务只描述本次调用的上下文和期望任务；Agent 的角色、规则、模型、CLI、MCP 和技能由
 * Agent 配置决定。</p>
 */
@Data
public class AgentExecutionRequest {

    private String runId;
    private String projectId;
    private String requirementId;
    private String logStreamKey;
    private String loopId;
    private String codingTaskId;
    private String taskId;
    private String prompt;
    private TriggerSource triggerSource;
    private String memoryContextId;
    private String workspaceMemoryId;
    private String parentRunId;
    private String compensatesRunId;
    private Integer attemptNo;
    private long timeoutSeconds = 1_800L;
}
