package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.AgentDao;
import com.changhong.onlinecode.dao.AgentSkillDao;
import com.changhong.onlinecode.entity.Agent;
import com.changhong.onlinecode.entity.AgentSkill;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AgentService 单元测试（Phase 2：oc_agent_skill join 表）。
 *
 * <p>覆盖 attachSkills 整体替换语义（ep #24）：先删后插、返回 agent 的 skillIds 已 populate。
 * OperateResult 经 ApplicationContextHolder 解析 i18n，单测缺容器会 NPE；
 * {@code @BeforeAll} 注入回显消息码的 mock 上下文（模式参照 PlanServiceTest）。</p>
 */
class AgentServiceTest {

    @BeforeAll
    static void bootstrapContext() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        lenient().when(ctx.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        new ApplicationContextHolder().setApplicationContext(ctx);
    }

    private AgentDao agentDao;
    private AgentSkillDao agentSkillDao;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentDao = mock(AgentDao.class);
        agentSkillDao = mock(AgentSkillDao.class);
        agentService = new AgentService(agentDao, agentSkillDao);
    }

    @Test
    void attachSkills_replacesBindings_wholeReplace() {
        // WHY: ep #24 整体替换——旧绑定必须清空、新绑定全部写入，否则 agent 会残留过期技能。
        Agent agent = new Agent();
        agent.setId("AGENT_001");
        agent.setName("dev-agent");
        when(agentDao.findById("AGENT_001")).thenReturn(Optional.of(agent));

        OperateResultWithData<Agent> result =
                agentService.attachSkills("AGENT_001", List.of("SKIL_A", "SKIL_B"));

        assertFalse(result.notSuccessful());
        verify(agentSkillDao).deleteByAgentId("AGENT_001");
        ArgumentCaptor<AgentSkill> captor = ArgumentCaptor.forClass(AgentSkill.class);
        verify(agentSkillDao, times(2)).save(captor.capture());
        List<String> saved = captor.getAllValues().stream()
                .map(AgentSkill::getSkillId).collect(Collectors.toList());
        assertEquals(List.of("SKIL_A", "SKIL_B"), saved);
        // 返回 agent 的 skillIds 已回填为新列表
        assertEquals(List.of("SKIL_A", "SKIL_B"), result.getData().getSkillIds());
    }

    @Test
    void attachSkills_failsWhenAgentMissing() {
        // WHY: 绑定不存在的 agent 会产生孤儿 join 行，必须拒绝。
        when(agentDao.findById("NOPE")).thenReturn(Optional.empty());

        OperateResultWithData<Agent> result = agentService.attachSkills("NOPE", List.of("SKIL_A"));

        assertTrue(result.notSuccessful());
        verify(agentSkillDao, never()).deleteByAgentId(anyString());
        verify(agentSkillDao, never()).save(any(AgentSkill.class));
    }

    @Test
    void attachSkills_clearsBindings_whenEmptyList() {
        // WHY: 空列表代表解绑全部——必须删旧且不插新。
        Agent agent = new Agent();
        agent.setId("AGENT_002");
        when(agentDao.findById("AGENT_002")).thenReturn(Optional.of(agent));

        OperateResultWithData<Agent> result = agentService.attachSkills("AGENT_002", List.of());

        assertFalse(result.notSuccessful());
        verify(agentSkillDao).deleteByAgentId("AGENT_002");
        verify(agentSkillDao, never()).save(any(AgentSkill.class));
        assertTrue(result.getData().getSkillIds().isEmpty());
    }

    @Test
    void attachSkills_dedupesDuplicateSkillIds() {
        // WHY: 重复 skillId 在 join 表会触发唯一约束冲突，service 层先去重。
        Agent agent = new Agent();
        agent.setId("AGENT_003");
        when(agentDao.findById("AGENT_003")).thenReturn(Optional.of(agent));

        agentService.attachSkills("AGENT_003", List.of("SKIL_A", "SKIL_A", "SKIL_B"));

        verify(agentSkillDao, times(2)).save(any(AgentSkill.class));
    }
}
