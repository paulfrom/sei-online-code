package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.LifecycleState;
import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * 迭代 DTO。契约 §2.3 IterationDto —— 一次 Build Loop 回合。
 *
 * @author sei-online-code
 */
@Schema(description = "迭代 DTO")
public class IterationDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @Schema(description = "所属项目 id")
    private String projectId;

    @Schema(description = "所属 Spec id")
    private String specId;

    @Schema(description = "Spec 版本号")
    private Integer specVersion;

    @Schema(description = "项目内 1 起的 Build Loop 回合序号", example = "2")
    private Integer round;

    @Schema(description = "本回合精炼自的上一回合迭代 id；第 1 回合为 null", example = "ITER0001")
    private String parentIterationId;

    @Schema(description = "进入本回合时用户提交的优化诉求；第 1 回合为 null", example = "把库存列表加上导出按钮")
    private String feedback;

    @Schema(description = "本回合生命周期状态", example = "PREVIEW")
    private LifecycleState state;

    @Schema(description = "预览地址；DEPLOYING 完成前为 null", example = "http://localhost:41001")
    private String previewUrl;

    @Schema(description = "创建时间")
    private Date createdDate;

    @Schema(description = "终态落定时间；未终结为 null")
    private Date finishedDate;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSpecId() {
        return specId;
    }

    public void setSpecId(String specId) {
        this.specId = specId;
    }

    public Integer getSpecVersion() {
        return specVersion;
    }

    public void setSpecVersion(Integer specVersion) {
        this.specVersion = specVersion;
    }

    public Integer getRound() {
        return round;
    }

    public void setRound(Integer round) {
        this.round = round;
    }

    public String getParentIterationId() {
        return parentIterationId;
    }

    public void setParentIterationId(String parentIterationId) {
        this.parentIterationId = parentIterationId;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public LifecycleState getState() {
        return state;
    }

    public void setState(LifecycleState state) {
        this.state = state;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getFinishedDate() {
        return finishedDate;
    }

    public void setFinishedDate(Date finishedDate) {
        this.finishedDate = finishedDate;
    }
}
