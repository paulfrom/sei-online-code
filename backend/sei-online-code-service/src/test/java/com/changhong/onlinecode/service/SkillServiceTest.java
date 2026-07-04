package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.AgentSkillDao;
import com.changhong.onlinecode.dao.SkillDao;
import com.changhong.onlinecode.entity.AgentSkill;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SkillService 单元测试（Phase 2：preDelete 经 oc_agent_skill 单查询）。
 *
 * <p>锁定「被任一 agent 绑定则拒绝删除」契约——删除悬空绑定会留下脏 join 行 / materialize 时
 * 找不到技能。importSkill 的 name 去重 + 409 由 Phase 3 覆盖。OperateResult 经
 * ApplicationContextHolder 解析 i18n，{@code @BeforeAll} 注入 mock 上下文（参照 PlanServiceTest）。</p>
 */
class SkillServiceTest {

    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    private SkillDao skillDao;
    private AgentSkillDao agentSkillDao;
    private SkillService skillService;

    @BeforeEach
    void setUp() {
        skillDao = mock(SkillDao.class);
        agentSkillDao = mock(AgentSkillDao.class);
        skillService = new SkillService(skillDao, agentSkillDao);
    }

    @Test
    void delete_rejectsWhenSkillBoundToAgent() {
        // WHY: 被 agent 绑定的技能删除后，join 行变悬空、dispatch materialize 会找不到技能，必须拒绝。
        AgentSkill binding = new AgentSkill();
        binding.setAgentId("AGENT_001");
        binding.setSkillId("SKIL_BOUND");
        when(agentSkillDao.findBySkillId("SKIL_BOUND")).thenReturn(List.of(binding));

        OperateResult result = skillService.delete("SKIL_BOUND");

        assertTrue(result.notSuccessful());
    }

    @Test
    void preDelete_allowsWhenSkillUnbound() {
        // WHY: 无绑定的技能可安全删除（无悬空 join 行风险）。
        when(agentSkillDao.findBySkillId("SKIL_FREE")).thenReturn(List.of());

        OperateResult result = skillService.preDelete("SKIL_FREE");

        assertFalse(result.notSuccessful());
    }
}
