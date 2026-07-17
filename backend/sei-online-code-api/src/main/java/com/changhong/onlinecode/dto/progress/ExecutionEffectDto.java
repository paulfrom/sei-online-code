package com.changhong.onlinecode.dto.progress;

import com.changhong.onlinecode.dto.enums.ExecutionEffectStatus;
import com.changhong.onlinecode.dto.enums.ExecutionEffectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * ExecutionEffect 查询 DTO（计划 §3 findEffects）。request_snapshot/result_snapshot 默认脱敏不返回。
 */
@Data
@Schema(description = "ExecutionEffect 查询视图")
public class ExecutionEffectDto {

    private String effectId;
    private String effectKey;
    private String executionId;
    private String stepId;
    private ExecutionEffectType effectType;
    private ExecutionEffectStatus status;
    private String externalReference;
    private Date preparedAt;
    private Date appliedAt;
    private Date confirmedAt;
    private Date lastReconciledAt;
}
