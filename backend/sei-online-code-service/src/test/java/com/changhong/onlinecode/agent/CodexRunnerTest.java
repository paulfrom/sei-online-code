package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link CodexRunner} 单元测试（PR1）。
 *
 * <p>验证 WHY：codex runner 的两个核心契约——「一次性 exec + --json 参数」与「NDJSON 防御性
 * 解析提取 final_answer 文本」。spawn 真实进程属 env-gated e2e（同 ClaudeRunnerRealClaudeTest
 * 模式），本单测钉死 args 构造与结果提取，不依赖外部 codex 安装。</p>
 */
class CodexRunnerTest {

    @Test
    void buildArgs_usesExecWithJsonFlag() {
        CodexRunner runner = new CodexRunner();
        List<String> args = runner.buildArgs("do something");
        // 默认 executable=codex（未设 CODEX_EXECUTABLE_PATH 时）
        assertEquals("codex", args.get(0));
        assertEquals("exec", args.get(1));
        assertEquals("do something", args.get(2));
        assertEquals("--json", args.get(3));
        assertEquals(4, args.size(), "本 PR 仅 4 个参数（--model 注入属后续 PR）");
    }

    @Test
    void extractResultJson_finalAnswer_returnsText() {
        CodexRunner runner = new CodexRunner();
        String ndjson = "{\"item\":{\"type\":\"agentMessage\",\"phase\":\"final_answer\",\"text\":\"hello\"}}\n";
        assertEquals("hello", runner.extractResultJson(ndjson, "it-1"));
    }

    @Test
    void extractResultJson_multipleFinalAnswers_concatenates() {
        CodexRunner runner = new CodexRunner();
        String ndjson = "{\"item\":{\"type\":\"agentMessage\",\"phase\":\"final_answer\",\"text\":\"a\"}}\n"
                + "{\"item\":{\"type\":\"agentMessage\",\"phase\":\"final_answer\",\"text\":\"b\"}}\n";
        assertEquals("ab", runner.extractResultJson(ndjson, "it-1"));
    }

    @Test
    void extractResultJson_empty_returnsNull() {
        CodexRunner runner = new CodexRunner();
        assertNull(runner.extractResultJson("", "it-1"));
        assertNull(runner.extractResultJson("\n  \n", "it-1"));
    }

    @Test
    void extractResultJson_nonJson_fallsBackToLastNonEmptyLine() {
        CodexRunner runner = new CodexRunner();
        // 无 final_answer 事件时回退最后非空行（裸文本兜底）
        assertEquals("plain text", runner.extractResultJson("garbage\nplain text\n", "it-1"));
    }

    @Test
    void extractResultJson_stripsMarkdownFences() {
        CodexRunner runner = new CodexRunner();
        String ndjson = "{\"item\":{\"type\":\"agentMessage\",\"phase\":\"final_answer\","
                + "\"text\":\"```json\\n{\\\"a\\\":1}\\n```\"}}\n";
        assertEquals("{\"a\":1}", runner.extractResultJson(ndjson, "it-1"));
    }
}
