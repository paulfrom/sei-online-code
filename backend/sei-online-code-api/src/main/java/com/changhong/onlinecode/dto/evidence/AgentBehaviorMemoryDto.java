package com.changhong.onlinecode.dto.evidence;

import com.changhong.onlinecode.dto.enums.AgentBehaviorMemoryStatus;
import com.changhong.onlinecode.dto.enums.BehaviorMemoryOutcome;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Agent 长期行为改进记忆")
public class AgentBehaviorMemoryDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    private String projectId;
    private String agentName;
    private String area;
    private String scopeKey;
    private String ruleText;
    private String rationale;
    private String sourceReviewId;
    private String sourceEvidenceId;
    private AgentBehaviorMemoryStatus status;
    private Integer occurrenceCount;
    private Integer helpfulCount;
    private Integer ineffectiveCount;
    private BehaviorMemoryOutcome lastOutcome;
    private Date lastAppliedAt;
    private Date expiresAt;
}
