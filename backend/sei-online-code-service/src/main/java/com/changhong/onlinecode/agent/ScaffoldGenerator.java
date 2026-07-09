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
        files.add(new ScaffoldFile(".gitignore", "工作区忽略规则"));
        files.add(new ScaffoldFile("README.md", "工作区说明"));
        files.add(new ScaffoldFile(".sei/runs/.gitkeep", "运行目录占位"));
        files.add(new ScaffoldFile(".sei/generated/.gitkeep", "生成物目录占位"));
        files.add(new ScaffoldFile(".sei/materials/.gitkeep", "平台物料目录占位"));

        return Collections.unmodifiableList(files);
    }

    /**
     * 返回给定脚手架文件的初始内容；未知路径返回 null。
     */
    public String contentOf(String path) {
        if (path == null) {
            return null;
        }
        switch (path) {
            case "package.json":
                return """
                        {
                          "name": "sei-online-code-workspace",
                          "private": true,
                          "version": "0.0.1",
                          "type": "module",
                          "scripts": {
                            "dev": "vite",
                            "build": "tsc -b && vite build",
                            "preview": "vite preview"
                          },
                          "dependencies": {
                            "@ead/antd-style": "^1.0.0",
                            "@ead/suid": "^1.0.0",
                            "@ead/suid-utils-react": "^1.0.0",
                            "msw": "^2.0.0",
                            "react": "^18.3.1",
                            "react-dom": "^18.3.1",
                            "react-router-dom": "^6.28.0"
                          },
                          "devDependencies": {
                            "@types/react": "^18.3.3",
                            "@types/react-dom": "^18.3.0",
                            "@vitejs/plugin-react": "^4.3.1",
                            "typescript": "^5.5.4",
                            "vite": "^5.4.2"
                          }
                        }
                        """;
            case "vite.config.ts":
                return """
                        import path from 'node:path';
                        import { defineConfig } from 'vite';
                        import react from '@vitejs/plugin-react';

                        export default defineConfig({
                          plugins: [react()],
                          resolve: {
                            alias: {
                              '@': path.resolve(__dirname, 'src'),
                            },
                          },
                        });
                        """;
            case "tsconfig.json":
                return """
                        {
                          "compilerOptions": {
                            "target": "ES2020",
                            "useDefineForClassFields": true,
                            "lib": ["DOM", "DOM.Iterable", "ES2020"],
                            "allowJs": false,
                            "skipLibCheck": true,
                            "esModuleInterop": true,
                            "allowSyntheticDefaultImports": true,
                            "strict": true,
                            "forceConsistentCasingInFileNames": true,
                            "module": "ESNext",
                            "moduleResolution": "Node",
                            "resolveJsonModule": true,
                            "isolatedModules": true,
                            "noEmit": true,
                            "jsx": "react-jsx",
                            "baseUrl": ".",
                            "paths": {
                              "@/*": ["src/*"]
                            }
                          },
                          "include": ["src"],
                          "references": []
                        }
                        """;
            case "index.html":
                return """
                        <!doctype html>
                        <html lang="zh-CN">
                          <head>
                            <meta charset="UTF-8" />
                            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                            <title>SEI Online Code Workspace</title>
                          </head>
                          <body>
                            <div id="root"></div>
                            <script type="module" src="/src/main.tsx"></script>
                          </body>
                        </html>
                        """;
            case "src/main.tsx":
                return """
                        import React from 'react';
                        import ReactDOM from 'react-dom/client';
                        import App from './App';

                        async function bootstrap() {
                          if (import.meta.env.DEV) {
                            const { worker } = await import('./mocks/browser');
                            await worker.start({ onUnhandledRequest: 'bypass' });
                          }

                          ReactDOM.createRoot(document.getElementById('root')!).render(
                            <React.StrictMode>
                              <App />
                            </React.StrictMode>,
                          );
                        }

                        bootstrap();
                        """;
            case "src/App.tsx":
                return """
                        import { RouterView } from './router';

                        export default function App() {
                          return <RouterView />;
                        }
                        """;
            case "src/router/index.tsx":
                return """
                        import { BrowserRouter, Route, Routes } from 'react-router-dom';

                        type RouteModule = {
                          default: {
                            path: string;
                            element: JSX.Element;
                          };
                        };

                        const routeModules = import.meta.glob<RouteModule>('../pages/*/route.tsx', { eager: true });
                        const routes = Object.values(routeModules).map((module) => module.default);

                        export function RouterView() {
                          return (
                            <BrowserRouter>
                              <Routes>
                                {routes.map((route) => (
                                  <Route key={route.path} path={route.path} element={route.element} />
                                ))}
                              </Routes>
                            </BrowserRouter>
                          );
                        }
                        """;
            case "src/pages/home/index.tsx":
                return """
                        export default function HomePage() {
                          return (
                            <main style={{ padding: 24, fontFamily: 'sans-serif' }}>
                              <h1>SEI Online Code Workspace</h1>
                              <p>项目工作区已初始化，后续代码产物会持续落在当前目录。</p>
                            </main>
                          );
                        }
                        """;
            case "src/pages/home/route.tsx":
                return """
                        import HomePage from './index';

                        export default {
                          path: '/',
                          element: <HomePage />,
                        };
                        """;
            case "src/mocks/browser.ts":
                return """
                        import { setupWorker } from 'msw/browser';
                        import { handlers } from './handlers';

                        export const worker = setupWorker(...handlers);
                        """;
            case "src/mocks/handlers.ts":
                return """
                        import type { HttpHandler } from 'msw';

                        type HandlerModule = {
                          default: HttpHandler[];
                        };

                        const modules = import.meta.glob<HandlerModule>('./*.handlers.ts', { eager: true });

                        export const handlers = Object.entries(modules)
                          .filter(([path]) => path !== './handlers.ts')
                          .flatMap(([, module]) => module.default);
                        """;
            case "src/mocks/home.handlers.ts":
                return """
                        import { http, HttpResponse } from 'msw';

                        export default [
                          http.get('/api/home', () =>
                            HttpResponse.json({
                              code: 200,
                              success: true,
                              data: {
                                message: 'workspace ready',
                              },
                            }),
                          ),
                        ];
                        """;
            case ".gitignore":
                return """
                        node_modules
                        dist
                        .DS_Store
                        .sei/runs/*
                        .sei/generated/*
                        !.sei/runs/.gitkeep
                        !.sei/generated/.gitkeep
                        """;
            case "README.md":
                return """
                        # SEI Online Code Workspace

                        该目录是项目的物理工作区。

                        - 业务代码默认落在仓库根目录。
                        - 运行期辅助物料落在 `.sei/`。
                        - Agent 运行说明会写入 `AGENTS.md` 或 `CLAUDE.md`。
                        """;
            case ".sei/runs/.gitkeep":
            case ".sei/generated/.gitkeep":
            case ".sei/materials/.gitkeep":
                return "";
            default:
                return null;
        }
    }

    /**
     * 平台自有工作区物料目录。所有运行态物料应收敛在这些目录中，而不是散落在根目录。
     */
    public List<String> managedPaths() {
        return List.of(
                ".sei/runs/.gitkeep",
                ".sei/generated/.gitkeep",
                ".sei/materials/.gitkeep"
        );
    }
}
