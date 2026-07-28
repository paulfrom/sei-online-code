package com.changhong.onlinecode.dto.evidence;

import com.changhong.onlinecode.dto.enums.TaskDeliveryReviewDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "PM 针对某个交付 Run 的权威反馈")
public class RunFeedbackDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String reviewId;
    private String deliveryRunId;
    private String evidenceId;
    private TaskDeliveryReviewDecision decision;
    private String summary;
    private String remediationBriefJson;
    private String feedbackAppliedRunId;
}
