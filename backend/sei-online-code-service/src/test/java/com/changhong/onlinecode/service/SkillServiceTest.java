package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.AgentSkillDao;
import com.changhong.onlinecode.dao.SkillDao;
import com.changhong.onlinecode.dao.SkillFileDao;
import com.changhong.onlinecode.dto.skill.SkillConfig;
import com.changhong.onlinecode.dto.skill.SkillFileDto;
import com.changhong.onlinecode.entity.AgentSkill;
import com.changhong.onlinecode.entity.Skill;
import com.changhong.onlinecode.entity.SkillFile;
import com.changhong.onlinecode.exception.ConflictException;
import com.changhong.sei.core.context.ApplicationContextHolder;
import com.changhong.sei.core.service.bo.OperateResult;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SkillService 单元测试。
 *
 * <p>Phase 2：preDelete 经 oc_agent_skill 单查询校验「被任一 agent 绑定则拒绝删除」——删除悬空绑定
 * 会留下脏 join 行 / materialize 时找不到技能。Phase 3：importSkill 改 name 去重——同名已存在抛
 * {@link ConflictException}（409），名字空闲则 insert。OperateResult 经 ApplicationContextHolder 解析
 * i18n，{@code @BeforeAll} 注入 mock 上下文（参照 PlanServiceTest）。</p>
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
    private SkillFileDao skillFileDao;
    private SkillService skillService;

    @BeforeEach
    void setUp() {
        skillDao = mock(SkillDao.class);
        agentSkillDao = mock(AgentSkillDao.class);
        skillFileDao = mock(SkillFileDao.class);
        skillService = new SkillService(skillDao, agentSkillDao, skillFileDao);
        // BaseService.validateUniqueCode 调 getDao().getEntityClass() 做 ICodeUnique 判定，
        // mock 默认返回 null 会 NPE；Skill 非 ICodeUnique，返回 Skill.class 即短路成功。
        lenient().when(skillDao.getEntityClass()).thenReturn(Skill.class);
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

    @Test
    void importSkill_throwsConflictWhenNameExists() {
        // WHY: materialize 以 name 为目录名，同名必然冲突；Phase 3 弃 hash 去重改 name 去重，
        //      同名已存在必须 409 拒绝（而非静默返回旧行或建新行），否则 agent 会拿到错误内容。
        Skill existing = new Skill();
        existing.setId("SKIL_EXISTING");
        existing.setName("suid");
        when(skillDao.findByName("suid")).thenReturn(existing);

        assertThrows(ConflictException.class,
                () -> skillService.importSkill("suid", "desc", new SkillConfig("local:suid"),
                        "# content", null));
        verify(skillDao, never()).save(any(Skill.class));
    }

    @Test
    void importSkill_insertsWhenNameFree() {
        // WHY: 名字空闲时必须真正落库写入，否则导入无声失败、agent 绑定到不存在的技能。
        //      Phase 5：辅助文件必须随技能一并持久化并回填到返回值，否则 materialize 漏写文件。
        when(skillDao.findByName("new-skill")).thenReturn(null);
        Skill persisted = new Skill();
        persisted.setId("SKIL_NEW");
        persisted.setName("new-skill");
        when(skillDao.save(any(Skill.class))).thenReturn(persisted);
        when(skillFileDao.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<SkillFileDto> files = List.of(
                new SkillFileDto("resources/foo.md", "# foo"),
                new SkillFileDto("scripts/run.sh", "echo hi"));
        OperateResultWithData<Skill> result = skillService.importSkill(
                "new-skill", "desc", new SkillConfig("local:new"), "# content", files);

        assertFalse(result.notSuccessful());
        assertEquals("new-skill", result.getData().getName());
        List<SkillFile> persistedFiles = result.getData().getFiles();
        assertEquals(2, persistedFiles.size(), "辅助文件应随导入持久化并回填");
        assertEquals("SKIL_NEW", persistedFiles.get(0).getSkillId(), "辅助文件应绑定到新技能 id");
        verify(skillFileDao).saveAll(any());
    }

    @Test
    void findOne_populatesFiles() {
        // WHY: findOne 返回的 Skill 必须带 files[]（契约 §1.1 FileRef[]），供 DTO 映射与
        //      materialize 调用方读取——populate 在 service 层完成，DAO 不碰 @Transient。
        Skill skill = new Skill();
        skill.setId("SKIL_X");
        skill.setName("suid");
        when(skillDao.findOne("SKIL_X")).thenReturn(skill);
        SkillFile f1 = new SkillFile();
        f1.setSkillId("SKIL_X");
        f1.setPath("resources/a.md");
        f1.setContent("a");
        when(skillFileDao.findBySkillId("SKIL_X")).thenReturn(List.of(f1));

        Skill loaded = skillService.findOne("SKIL_X");

        assertEquals("SKIL_X", loaded.getId());
        assertEquals(1, loaded.getFiles().size(), "findOne 应 populate 辅助文件");
        assertEquals("resources/a.md", loaded.getFiles().get(0).getPath());
    }
}
