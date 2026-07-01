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

    @Schema(description = "本回合生命周期状态", example = "PREVIEW")
    private LifecycleState state;

    @Schema(description = "预览地址；DEPLOYING 完成前为 null", example = "http://localhost:41001")
    private String previewUrl;

    @Schema(description = "创建时间")
    private Date createdDate;

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
}
