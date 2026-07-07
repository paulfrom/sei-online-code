package com.changhong.onlinecode.dto;

import com.changhong.sei.core.dto.BaseEntityDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Date;

/**
 * 项目 DTO。契约 §2.1 ProjectDto。
 *
 * @author sei-online-code
 */
@Schema(description = "项目 DTO")
public class ProjectDto extends BaseEntityDto {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称长度不能超过200")
    @Schema(description = "项目名称", example = "库存管理台")
    private String name;

    @NotBlank(message = "Project Design 不能为空")
    @Schema(description = "用户输入的原始 Project Design 文字")
    private String design;

    @Schema(description = "编码前聚合状态", example = "READY_TO_BUILD")
    private String state;

    @Schema(description = "当前已确认/活动的 Spec id")
    private String currentSpecId;

    @Schema(description = "创建时间")
    private Date createdDate;

    @Schema(description = "最后编辑时间")
    private Date lastEditedDate;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesign() {
        return design;
    }

    public void setDesign(String design) {
        this.design = design;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCurrentSpecId() {
        return currentSpecId;
    }

    public void setCurrentSpecId(String currentSpecId) {
        this.currentSpecId = currentSpecId;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getLastEditedDate() {
        return lastEditedDate;
    }

    public void setLastEditedDate(Date lastEditedDate) {
        this.lastEditedDate = lastEditedDate;
    }
}
