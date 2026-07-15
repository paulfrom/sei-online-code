package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RequirementCommentDao;
import com.changhong.onlinecode.dto.RequirementCommentDto;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.entity.RequirementComment;
import com.changhong.sei.core.dao.BaseEntityDao;
import com.changhong.sei.core.service.BaseEntityService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Requirement 评论服务。
 */
@Service
@AllArgsConstructor
public class RequirementCommentService extends BaseEntityService<RequirementComment> {

    private final RequirementCommentDao dao;

    @Override
    protected BaseEntityDao<RequirementComment> getDao() {
        return dao;
    }

    public List<RequirementComment> findByRequirementId(String requirementId) {
        return dao.findByRequirementIdOrderByCreatedDateAsc(requirementId);
    }

    @Transactional(rollbackFor = Exception.class)
    public RequirementComment append(String requirementId,
                                     String loopId,
                                     RequirementCommentAuthorType authorType,
                                     String authorName,
                                     RequirementCommentType commentType,
                                     String content,
                                     String metadataJson) {
        RequirementComment comment = new RequirementComment();
        comment.setRequirementId(requirementId);
        comment.setLoopId(loopId);
        comment.setAuthorType(authorType);
        comment.setAuthorName(authorName);
        comment.setCommentType(commentType);
        comment.setContent(content);
        comment.setMetadataJson(metadataJson);
        return dao.save(comment);
    }

    public RequirementCommentDto convertToDto(RequirementComment comment) {
        RequirementCommentDto dto = new RequirementCommentDto();
        dto.setId(comment.getId());
        dto.setRequirementId(comment.getRequirementId());
        dto.setLoopId(comment.getLoopId());
        dto.setAuthorType(comment.getAuthorType());
        dto.setAuthorName(comment.getAuthorName());
        dto.setCommentType(comment.getCommentType());
        dto.setContent(comment.getContent());
        dto.setMetadataJson(comment.getMetadataJson());
        dto.setCreatedDate(comment.getCreatedDate());
        return dto;
    }
}
