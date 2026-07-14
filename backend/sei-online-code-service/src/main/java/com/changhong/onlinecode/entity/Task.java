package com.changhong.onlinecode.entity;

import com.changhong.onlinecode.dto.enums.TaskState;
import com.changhong.onlinecode.entity.converter.StringListConverter;
import com.changhong.sei.core.entity.BaseAuditableEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Task 实体。契约 Phase 2 §1.1 —— Dispatch Agent 按 fileScope 切分出的一个非重叠工作单元。
 *
 * <p>fileScope 为自由结构（字符串数组），复用 {@link StringListConverter} 以 JSON 列（TEXT）持久化。</p>
 *
 * @author sei-online-code
 */
@Entity
@Table(name = "oc_task", indexes = {
        @Index(name = "idx_task_iteration", columnList = "iteration_id")
})
@Access(AccessType.FIELD)
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class Task extends BaseAuditableEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "iteration_id", nullable = false, length = 36)
    private String iterationId;

    @Column(name = "feature_design_id", length = 36)
    private String featureDesignId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Convert(converter = StringListConverter.class)
    @Column(name = "file_scope", columnDefinition = "TEXT")
    private List<String> fileScope;

    @Column(name = "assigned_agent", length = 100)
    private String assignedAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private TaskState state;

    @Column(name = "worktree_branch", length = 200)
    private String worktreeBranch;

    @Column(name = "seq")
    private Integer seq;

    @Override
    @Transient
    public String getDisplay() {
        return title + " [" + state + "]";
    }
}
