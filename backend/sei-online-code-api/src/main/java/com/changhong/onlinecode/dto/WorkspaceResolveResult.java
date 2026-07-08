package com.changhong.onlinecode.dto;

import com.changhong.onlinecode.dto.enums.WorkspaceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 工作区解析结果（B33）。契约 Phase 5 §2 端点 33 / §3。
 *
 * <p>{@code provisioned=true} 表示目录此前已存在（clone-once 复用，不再 clone/生成）；
 * {@code source} 标记本工作区的首次 provision 来源（CLONE/SCAFFOLD）。</p>
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "工作区解析结果")
public class WorkspaceResolveResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "解析出的项目工作区目录（root/projectId）", example = "/tmp/sei-online-code/PRJ0001")
    private String path;

    @Schema(description = "目录此前是否已存在（clone-once 复用）", example = "false")
    private boolean provisioned;

    @Schema(description = "provision 来源：CLONE（有模板地址）/ SCAFFOLD（无模板地址）", example = "SCAFFOLD")
    private WorkspaceSource source;

    public WorkspaceResolveResult() {
    }

    public WorkspaceResolveResult(String path, boolean provisioned, WorkspaceSource source) {
        this.path = path;
        this.provisioned = provisioned;
        this.source = source;
    }
}