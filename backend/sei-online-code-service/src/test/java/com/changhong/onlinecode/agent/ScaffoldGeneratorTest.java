package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScaffoldGenerator} 单元测试（B36）：canonical SUID 脚手架结构不变量。
 *
 * <p>验证 WHY：契约 §4 规定 no-template 路径必须产出「可让并行任务互不冲突」的 canonical SUID 脚手架
 * （ADR-0001 split-file + glob 聚合）。若清单缺少 package.json/vite 配置，产物无法构建；若缺 per-page/
 * per-feature 拆分或 glob 聚合入口，并行任务会同时改同一清单文件而冲突；若缺 MSW 入口，前端无 mock 数据。
 * 故逐项钉死这些结构不变量必须存在。清单还须确定不可变——同输入同输出，才能作为可靠 provision 基线。</p>
 *
 * @author sei-online-code
 */
class ScaffoldGeneratorTest {

    private final ScaffoldGenerator generator = new ScaffoldGenerator();

    @Test
    void generate_containsRequiredStructuralInvariants() {
        Set<String> paths = generator.generate().stream()
                .map(ScaffoldGenerator.ScaffoldFile::path)
                .collect(Collectors.toSet());

        // 构建与依赖基石
        assertTrue(paths.contains("package.json"), "必须含 package.json");
        assertTrue(paths.contains("vite.config.ts"), "必须含 vite 配置");

        // per-page 路由文件（split-file，非重叠 fileScope）
        assertTrue(paths.stream().anyMatch(p -> p.startsWith("src/pages/")),
                "必须含 per-page 目录 src/pages/*");

        // per-feature mock 文件
        assertTrue(paths.stream().anyMatch(p -> p.startsWith("src/mocks/")),
                "必须含 per-feature mock 目录 src/mocks/*");

        // MSW 入口
        assertTrue(paths.contains("src/mocks/browser.ts"), "必须含 MSW 入口 src/mocks/browser.ts");

        // glob 聚合路由入口（并行任务互不触碰同一清单）
        assertTrue(paths.contains("src/router/index.tsx"),
                "必须含 glob 聚合路由入口 src/router/index.tsx");
    }

    @Test
    void generate_isDeterministicAndImmutable() {
        List<ScaffoldGenerator.ScaffoldFile> first = generator.generate();
        List<ScaffoldGenerator.ScaffoldFile> second = generator.generate();

        assertFalse(first.isEmpty(), "清单不应为空");
        // 同输入同输出（确定性）
        assertTrue(first.equals(second), "两次生成的清单必须完全一致（确定性）");
        // 不可变（provision 基线不可被下游篡改）
        assertThrows(UnsupportedOperationException.class,
                () -> first.add(new ScaffoldGenerator.ScaffoldFile("x", "y")),
                "返回的清单必须不可变");
    }
}
