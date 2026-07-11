package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.WorkspaceMemoryDao;
import com.changhong.onlinecode.entity.RequirementDesignContext;
import com.changhong.onlinecode.entity.WorkspaceMemory;
import com.changhong.onlinecode.service.memory.MemoryRealityClaim;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DesignContextPromptAssembler 单元测试。
 *
 * <p>WHY：prompt 组装器把项目记忆、规范、现状、冲突、设计依据拼成生成文档的统一上下文。
 * 必须验证：agent-memory 正文真的进入 prompt（而非只输出 id）、模块切片只含命中当前模块的 reality、
 * 结构化冲突报告被正确渲染。旧实现只输出 id 让项目记忆在 prompt 中形同缺失。</p>
 *
 * @author sei-online-code
 */
class DesignContextPromptAssemblerTest {

    private final WorkspaceMemoryDao workspaceMemoryDao = mock(WorkspaceMemoryDao.class);
    private final DesignContextPromptAssembler assembler = new DesignContextPromptAssembler(workspaceMemoryDao);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void assemble_renderAgentMemoryMarkdownInjected() {
        // WHY：agent-memory 正文必须真正注入，而非只输出 WorkspaceMemoryId。
        RequirementDesignContext context = baseContext();
        WorkspaceMemory memory = new WorkspaceMemory();
        memory.setId("wm-1");
        memory.setAgentMemoryMarkdown("# project-memory\n\n## Hard Rules\n- 前端使用 React\n");
        when(workspaceMemoryDao.findOne("wm-1")).thenReturn(memory);

        String prompt = assembler.assemble(context);

        assertTrue(prompt.contains("前端使用 React"), "agent-memory 正文应注入 prompt");
        assertTrue(prompt.contains("WorkspaceMemory: wm-1"));
    }

    @Test
    void assemble_truncatesOverBudgetAgentMemory() {
        // WHY：agent-memory 可能很长，注入必须有预算截断以防吃光上下文窗口。
        RequirementDesignContext context = baseContext();
        WorkspaceMemory memory = new WorkspaceMemory();
        memory.setId("wm-1");
        memory.setAgentMemoryMarkdown("x".repeat(5000));
        when(workspaceMemoryDao.findOne("wm-1")).thenReturn(memory);

        String prompt = assembler.assemble(context);

        assertTrue(prompt.contains("已按预算截断"));
        // 截断后正文（不含截断提示）不应超过预算+标题开销
        assertTrue(prompt.length() < 5000);
    }

    @Test
    void assemble_readsStructuredConflictReport() {
        RequirementDesignContext context = baseContext();
        when(workspaceMemoryDao.findOne("wm-1")).thenReturn(new WorkspaceMemory());

        String prompt = assembler.assemble(context);

        assertTrue(prompt.contains("禁止直接使用 antd"));
        assertTrue(prompt.contains("使用 suid"));
    }

    @Test
    void assemble_renderRealityFromRelatedSnapshot() {
        RequirementDesignContext context = baseContext();
        context.setRequirementRelatedSnapshotJson(toJson(List.of(
                reality("src/main/java/Foo.java", "class Foo"))));
        when(workspaceMemoryDao.findOne("wm-1")).thenReturn(new WorkspaceMemory());

        String prompt = assembler.assemble(context);

        assertTrue(prompt.contains("src/main/java/Foo.java"));
        assertTrue(prompt.contains("class Foo"));
    }

    @Test
    void assembleModuleRealitySlice_filtersByModuleId() {
        // WHY：详细设计只需当前模块相关现状；切片必须只命中本模块 reality，避免全量注入噪声。
        RequirementDesignContext context = baseContext();
        context.setRequirementRelatedSnapshotJson(toJson(List.of(
                reality("src/main/java/order/OrderService.java", "class OrderService"),
                reality("src/main/java/payment/PaymentService.java", "class PaymentService"))));

        String slice = assembler.assembleModuleRealitySlice(context, "payment", "支付模块");

        assertTrue(slice.contains("PaymentService"));
        assertTrue(slice.contains("【当前模块相关代码现状】"));
        assertFalse(slice.contains("OrderService"), "非当前模块的 reality 不应进入切片");
    }

    @Test
    void assembleModuleRealitySlice_emptyWhenNoMatchReturnsBlank() {
        // WHY：无命中时返回空串，不应注入空段误导模型认为无现状。
        RequirementDesignContext context = baseContext();
        context.setRequirementRelatedSnapshotJson(toJson(List.of(
                reality("src/main/java/order/OrderService.java", "class OrderService"))));

        String slice = assembler.assembleModuleRealitySlice(context, "phantom", "幽灵模块");

        assertEquals("", slice);
    }

    @Test
    void assembleModuleRealitySlice_nullContextReturnsBlank() {
        assertEquals("", assembler.assembleModuleRealitySlice(null, "m", "t"));
    }

    // ============================ fixtures ============================

    private RequirementDesignContext baseContext() {
        RequirementDesignContext context = new RequirementDesignContext();
        context.setId("ctx-1");
        context.setWorkspaceMemoryId("wm-1");
        context.setVersion(2);
        context.setRequirementConflictReportJson("""
                {"high":[{"severity":"HIGH","summary":"禁止直接使用 antd","recommendedHandling":"使用 suid"}],
                 "medium":[],"low":[]}
                """);
        return context;
    }

    private MemoryRealityClaim reality(String source, String content) {
        MemoryRealityClaim c = new MemoryRealityClaim();
        c.setId("reality-1");
        c.setType("backend_code");
        c.setContent(content);
        c.setSource(source);
        c.setSourceHash("h");
        c.setConfidence("source_backed");
        return c;
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}