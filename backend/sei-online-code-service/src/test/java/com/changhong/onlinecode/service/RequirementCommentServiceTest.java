package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RequirementCommentDao;
import com.changhong.onlinecode.dto.enums.RequirementCommentAuthorType;
import com.changhong.onlinecode.dto.enums.RequirementCommentType;
import com.changhong.onlinecode.entity.RequirementComment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequirementCommentServiceTest {

    @Test
    void append_persistsAppendOnlyCommentFields() {
        RequirementCommentDao dao = mock(RequirementCommentDao.class);
        when(dao.save(any(RequirementComment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RequirementCommentService service = new RequirementCommentService(dao);

        RequirementComment comment = service.append("req-1", "loop-1",
                RequirementCommentAuthorType.HUMAN, "human",
                RequirementCommentType.HUMAN_FEEDBACK, "change", "{}");

        assertEquals("req-1", comment.getRequirementId());
        assertEquals("loop-1", comment.getLoopId());
        assertEquals(RequirementCommentType.HUMAN_FEEDBACK, comment.getCommentType());
        assertEquals("change", comment.getContent());
    }
}
