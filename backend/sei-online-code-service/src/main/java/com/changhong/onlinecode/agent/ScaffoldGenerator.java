package com.changhong.onlinecode.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 脚手架生成器（B36）。契约 Phase 5 §4 —— no-template 路径。
 *
 * <p>当平台未配置 templateGitlabUrl 时，本类给出「平台会生成的 canonical SUID 脚手架清单」：
 * Vite + React + TS + {@code @ead/suid} + {@code @ead/antd-style} + {@code @ead/suid-utils-react} + MSW，
 * 采用 split-file 约定（per-page 路由文件、per-feature mock 文件、glob 聚合入口），使并行任务落在
 * 互斥文件上、互不冲突（ADR-0001）。</p>
 *
 * <p>本阶段仅交付<b>确定性清单</b>（{@link ScaffoldFile} 列表，path+purpose）；真实文件落盘挂
 * {@code TODO(oma-deferred)}。清单确定（同输入同输出、不可变），便于单测结构不变量。</p>
 *
 * @author sei-online-code
 */
@Component
public class ScaffoldGenerator {

    /**
     * 脚手架清单中的单个文件条目（路径 + 用途）。
     *
     * @param path    相对工作区根的文件路径
     * @param purpose 该文件用途说明
     */
    public record ScaffoldFile(String path, String purpose) {
    }

    /**
     * 生成 canonical SUID 脚手架清单（确定性、不可变）。
     *
     * <p>包含结构不变量：package.json、vite 配置、per-page 目录（src/pages/*）、
     * per-feature mock（src/mocks/*）、MSW 入口、glob 聚合路由。</p>
     *
     * @return 不可变的脚手架文件清单
     */
    public List<ScaffoldFile> generate() {
        List<ScaffoldFile> files = new ArrayList<>();

        // ---- 工程根：构建与依赖 ----
        files.add(new ScaffoldFile("package.json",
                "依赖与脚本：vite / react / typescript / @ead/suid / @ead/antd-style / @ead/suid-utils-react / msw"));
        files.add(new ScaffoldFile("vite.config.ts", "Vite 构建配置（React 插件 + 路径别名）"));
        files.add(new ScaffoldFile("tsconfig.json", "TypeScript 编译配置"));
        files.add(new ScaffoldFile("index.html", "Vite 入口 HTML，挂载 #root"));

        // ---- 应用入口 ----
        files.add(new ScaffoldFile("src/main.tsx", "应用入口：开发态启动 MSW worker 后再挂载 React 根"));
        files.add(new ScaffoldFile("src/App.tsx", "根组件：接入 glob 聚合路由"));

        // ---- glob 聚合路由入口（聚合 per-page，互不触碰各 page 文件）----
        files.add(new ScaffoldFile("src/router/index.tsx",
                "glob 聚合路由：import.meta.glob 汇聚 src/pages/*/route.tsx，避免并行任务改同一路由清单文件"));

        // ---- per-page 路由文件（每页一目录，非重叠 fileScope）----
        files.add(new ScaffoldFile("src/pages/home/index.tsx", "示例页面 home 组件（per-page 目录）"));
        files.add(new ScaffoldFile("src/pages/home/route.tsx", "示例页面 home 路由声明（被 glob 聚合）"));

        // ---- MSW 入口 + glob 聚合 handlers（per-feature mock）----
        files.add(new ScaffoldFile("src/mocks/browser.ts", "MSW 浏览器 worker 入口：setupWorker(...handlers)"));
        files.add(new ScaffoldFile("src/mocks/handlers.ts",
                "glob 聚合 handlers：import.meta.glob 汇聚 src/mocks/*.handlers.ts，避免并行任务改同一 handlers 清单"));
        files.add(new ScaffoldFile("src/mocks/home.handlers.ts", "示例 per-feature mock handlers（home）"));

        return Collections.unmodifiableList(files);
    }
}
