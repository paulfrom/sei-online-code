package com.changhong.onlinecode.dto.spec;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * Spec 实体字段定义。契约 §2.2 entities[].fields[]。
 *
 * @author sei-online-code
 */
@Data
@Schema(description = "Spec 实体字段定义")
public class SpecEntityField implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "字段名", example = "code")
    private String name;

    @Schema(description = "字段类型", example = "string")
    private String type;

    @Schema(description = "字段描述")
    private String description;
}