package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Requirement 评论。人类评论不可覆盖，agent/system 评论追加记录。
 */
@Entity
@Table(name = "oc_requirement_comment", indexes = {
        @Index(name = "idx_req_comment_requirement", columnList = "requirement_id"),
        @Index(name = "idx_req_comment_loop", columnList = "loop_id"),
        @Index(name = "idx_req_comment_type", columnList = "comment_type")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
public class RequirementComment extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "requirement_id", nullable = false, length = 36)
    private String requirementId;

    @Column(name = "loop_id", length = 64)
    private String loopId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 32)
    private RequirementCommentAuthorType authorType;

    @Column(name = "author_name", length = 100)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_type", nullable = false, length = 64)
    private RequirementCommentType commentType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Override
    @Transient
    public String getDisplay() {
        return requirementId + ":" + commentType;
    }
}
