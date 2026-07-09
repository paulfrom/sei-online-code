package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.DetailedDesignDao;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DetailedDesignServiceTest {

    @Test
    void parseModules_supportsMarkdownModuleTable() {
        DetailedDesignService service = new DetailedDesignService(
                mock(DetailedDesignDao.class),
                mock(DetailedDesignAgentService.class),
                mock(CodingTaskService.class));

        String overview = """
                # 概览设计

                ## 2. 模块清单

                | moduleId | moduleTitle | summary |
                | --- | --- | --- |
                | user-center | 用户中心 | 用户资料、账户设置 |
                | workflow-engine | 流程引擎 | 审批编排和状态流转 |

                ## 3. 总体架构

                略。
                """;

        List<DetailedDesignService.ModuleRef> modules = service.parseModules(overview);

        assertEquals(2, modules.size());
        assertEquals("user-center", modules.get(0).moduleId());
        assertEquals("用户中心", modules.get(0).moduleTitle());
        assertEquals("workflow-engine", modules.get(1).moduleId());
        assertEquals("流程引擎", modules.get(1).moduleTitle());
    }

    @Test
    void parseModules_keepsLegacyJsonCompatibility() {
        DetailedDesignService service = new DetailedDesignService(
                mock(DetailedDesignDao.class),
                mock(DetailedDesignAgentService.class),
                mock(CodingTaskService.class));

        String legacyJson = """
                {
                  "modules": [
                    { "moduleId": "m1", "moduleTitle": "模块一", "features": [] },
                    { "moduleId": "m2", "moduleTitle": "模块二", "features": [] }
                  ]
                }
                """;

        List<DetailedDesignService.ModuleRef> modules = service.parseModules(legacyJson);

        assertEquals(2, modules.size());
        assertEquals("m1", modules.get(0).moduleId());
        assertEquals("模块一", modules.get(0).moduleTitle());
        assertEquals("m2", modules.get(1).moduleId());
        assertEquals("模块二", modules.get(1).moduleTitle());
    }
}
