package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.ProjectDao;
import com.changhong.onlinecode.dto.enums.MemorySeedTemplateSourceType;
import com.changhong.onlinecode.dto.enums.MemorySeedTemplateStatus;
import com.changhong.onlinecode.entity.MemorySeedTemplate;
import com.changhong.onlinecode.entity.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgentMemoryTemplateService 单元测试。
 *
 * <p>WHY：审核发现 seed 文件依赖模板正文自觉携带 front matter，classpath 默认模板缺 memorySeedTemplateId，
 * 用户配置模板更无法保证（P1-5）。改为 service 侧强制注入/覆盖五个平台权威字段后，无论模板正文是否带
 * front matter，写入工作区的 seed 文件都必须包含 memorySeedTemplateId/agentMemorySeedVersion/origin/reviewStatus。
 * 本测试覆盖三种正文形态：无 front matter、有 front matter 但缺字段、有 front matter 且含旧模板 id（需覆盖）。</p>
 */
class AgentMemoryTemplateServiceTest {

    private ProjectDao projectDao;
    private MemorySeedTemplateService seedTemplateService;
    private AgentMemoryTemplateService service;

    @BeforeEach
    void setUp() {
        projectDao = mock(ProjectDao.class);
        seedTemplateService = mock(MemorySeedTemplateService.class);
        service = new AgentMemoryTemplateService(projectDao, seedTemplateService);
    }

    private MemorySeedTemplate seed(String id, String code, int version) {
        MemorySeedTemplate t = new MemorySeedTemplate();
        t.setId(id);
        t.setCode(code);
        t.setName(code);
        t.setVersion(version);
        t.setStatus(MemorySeedTemplateStatus.ACTIVE);
        t.setSourceType(MemorySeedTemplateSourceType.USER_CONFIG);
        return t;
    }

    private Project boundProject(String projectId, String seedId) {
        Project p = new Project();
        p.setId(projectId);
        p.setMemorySeedTemplateId(seedId);
        return p;
    }

    @Test
    void ensureAgentMemory_templateWithoutFrontMatter_injectsSeedFields(@TempDir Path workspace) throws Exception {
        // WHY：用户配置模板正文不带任何 front matter 时，写入工作区的文件仍必须含平台权威字段。
        MemorySeedTemplate seed = seed("seed-1", "java-service", 2);
        seed.setProjectMemoryTemplate("# Project Memory\n\n## Hard Rules\n- 待维护\n");
        when(seedTemplateService.resolveForProject("seed-1")).thenReturn(seed);
        when(projectDao.findOne("proj-1")).thenReturn(boundProject("proj-1", "seed-1"));

        service.ensureAgentMemory("proj-1", workspace.toString());

        String written = Files.readString(workspace.resolve("agent-memory/project-memory.md"));
        assertTrue(written.contains("origin: platform_seed"), "应注入 origin=platform_seed");
        assertTrue(written.contains("memorySeedTemplateId: seed-1"), "应注入实际模板 id");
        assertTrue(written.contains("memorySeedTemplateCode: java-service"));
        assertTrue(written.contains("agentMemorySeedVersion: 2"));
        assertTrue(written.contains("reviewStatus: unreviewed"));
        assertTrue(written.contains("# Project Memory"), "原正文应保留在 front matter 之后");
    }

    @Test
    void ensureAgentMemory_templateWithPartialFrontMatter_overridesMissingSeedFields(@TempDir Path workspace) throws Exception {
        // WHY：classpath 默认模板有 front matter 但缺 memorySeedTemplateId；service 必须补齐并覆盖 code/version。
        MemorySeedTemplate seed = seed("seed-1", "default", 1);
        seed.setProjectMemoryTemplate("---\nowner: project\norigin: platform_seed\nmemorySeedTemplateCode: default\n"
                + "agentMemorySeedVersion: 1\nreviewStatus: unreviewed\nlastReviewedAt:\n---\n\n# Project Memory\n");
        when(seedTemplateService.resolveForProject("seed-1")).thenReturn(seed);
        when(projectDao.findOne("proj-1")).thenReturn(boundProject("proj-1", "seed-1"));

        service.ensureAgentMemory("proj-1", workspace.toString());

        String written = Files.readString(workspace.resolve("agent-memory/project-memory.md"));
        // 关键：补齐 memorySeedTemplateId（classpath 模板原本没有）
        assertTrue(written.contains("memorySeedTemplateId: seed-1"));
        // 已有字段保留且以平台权威值为准
        assertTrue(written.contains("origin: platform_seed"));
        assertTrue(written.contains("agentMemorySeedVersion: 1"));
        // front matter 闭合后正文仍在
        assertTrue(written.contains("# Project Memory"));
    }

    @Test
    void ensureAgentMemory_existingFileNotOverwritten(@TempDir Path workspace) throws Exception {
        // WHY：契约 §3.1、§13.2 明确已有 agent-memory 文件绝不覆盖，即便正文/front matter 不规范。
        MemorySeedTemplate seed = seed("seed-1", "default", 1);
        seed.setProjectMemoryTemplate("---\norigin: platform_seed\n---\n# Project Memory\n");
        when(seedTemplateService.resolveForProject("seed-1")).thenReturn(seed);
        when(projectDao.findOne("proj-1")).thenReturn(boundProject("proj-1", "seed-1"));

        Path agentDir = workspace.resolve("agent-memory");
        Files.createDirectories(agentDir);
        Path existing = agentDir.resolve("project-memory.md");
        String userContent = "---\nowner: project\nreviewStatus: reviewed\n---\n# 我自己的项目记忆\n";
        Files.writeString(existing, userContent);

        service.ensureAgentMemory("proj-1", workspace.toString());

        assertEquals(userContent, Files.readString(existing), "已有文件不得被覆盖");
    }

    @Test
    void ensureAgentMemory_unboundProject_bindsDefaultSeed(@TempDir Path workspace) {
        // WHY：项目未绑定模板时解析全局默认并写回 Project.memorySeedTemplateId（契约 §13.1、§13.2）。
        MemorySeedTemplate def = seed("default:1", "default", 1);
        when(seedTemplateService.resolveForProject(null)).thenReturn(def);
        Project unbound = boundProject("proj-1", null);
        when(projectDao.findOne("proj-1")).thenReturn(unbound);
        when(projectDao.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        service.ensureAgentMemory("proj-1", workspace.toString());

        assertEquals("default:1", unbound.getMemorySeedTemplateId(), "未绑定项目应回填默认模板 id");
        verify(projectDao).save(unbound);
    }
}
