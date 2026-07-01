package com.changhong.onlinecode.agent;

import com.changhong.onlinecode.dto.enums.WorkspaceSource;
import com.changhong.onlinecode.entity.PlatformConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkspaceManager} 单元测试（B35）：路径安全 + provision 来源决策。
 *
 * <p>验证 WHY：</p>
 * <ul>
 *   <li>isSafeRoot——工作区在根目录下按 projectId 建/删子目录，若把系统根/盘符根当作工作区根，
 *       后续 GC/清理会危及系统目录（数据丢失级）。故必须拒绝黑名单根，只放行安全的绝对路径。</li>
 *   <li>decideSource——契约 §3 规定：配置了模板地址则 CLONE、否则 SCAFFOLD（day-one 路径）。
 *       决策错误会导致该 clone 时误生成脚手架、或该生成时误 clone 空地址。</li>
 * </ul>
 *
 * @author sei-online-code
 */
class WorkspaceManagerTest {

    /** decideSource / isSafeRoot 不触碰 config 持久层与脚手架落盘，传 null 依赖即可纯逻辑单测。 */
    private final WorkspaceManager manager = new WorkspaceManager(null, null);

    @Test
    void isSafeRoot_rejectsSystemAndDriveRoots() {
        // 系统根 / 盘符根 —— 一律拒绝
        assertFalse(manager.isSafeRoot("/"), "根 / 必须拒绝");
        assertFalse(manager.isSafeRoot("/etc"), "/etc 必须拒绝");
        assertFalse(manager.isSafeRoot("/usr"), "/usr 必须拒绝");
        assertFalse(manager.isSafeRoot("/root"), "/root 必须拒绝");
        assertFalse(manager.isSafeRoot("/home"), "/home 必须拒绝");
        assertFalse(manager.isSafeRoot("/var/"), "带尾分隔符的系统根也须拒绝");
        assertFalse(manager.isSafeRoot("C:\\"), "盘符根 C:\\ 必须拒绝");
        assertFalse(manager.isSafeRoot("c:"), "盘符根 c: 必须拒绝（大小写不敏感）");
    }

    @Test
    void isSafeRoot_rejectsBlankAndRelative() {
        assertFalse(manager.isSafeRoot(null), "null 拒绝");
        assertFalse(manager.isSafeRoot("  "), "空白拒绝");
        assertFalse(manager.isSafeRoot("relative/dir"), "相对路径拒绝（落点不确定）");
    }

    @Test
    void isSafeRoot_acceptsSafeAbsoluteDir() {
        assertTrue(manager.isSafeRoot("/tmp/sei-online-code"), "临时区下的专用工作区根应放行");
        assertTrue(manager.isSafeRoot("/data/workspaces"), "非黑名单的绝对路径应放行");
    }

    @Test
    void decideSource_cloneWhenTemplateUrlSet() {
        PlatformConfig config = new PlatformConfig();
        config.setTemplateGitlabUrl("https://gitlab.example.com/tpl/suid-template.git");
        assertEquals(WorkspaceSource.CLONE, manager.decideSource(config),
                "配置了模板地址 → CLONE");
    }

    @Test
    void decideSource_scaffoldWhenTemplateUrlEmptyOrNull() {
        PlatformConfig empty = new PlatformConfig();
        empty.setTemplateGitlabUrl("");
        assertEquals(WorkspaceSource.SCAFFOLD, manager.decideSource(empty),
                "空模板地址 → SCAFFOLD（day-one 路径）");

        PlatformConfig nullUrl = new PlatformConfig();
        assertEquals(WorkspaceSource.SCAFFOLD, manager.decideSource(nullUrl),
                "模板地址为 null → SCAFFOLD");

        assertEquals(WorkspaceSource.SCAFFOLD, manager.decideSource(null),
                "配置缺失 → SCAFFOLD");
    }
}
