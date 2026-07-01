package com.changhong.onlinecode.dto.enums;

/**
 * 工作区首次 provision 来源（B33）。契约 Phase 5 §2 端点 33 / §3。
 *
 * <ul>
 *   <li>CLONE    —— 配置了 templateGitlabUrl，从模板仓库 clone-once</li>
 *   <li>SCAFFOLD —— 未配置模板地址，由 ScaffoldGenerator 生成 canonical SUID 脚手架</li>
 * </ul>
 *
 * @author sei-online-code
 */
public enum WorkspaceSource {
    CLONE,
    SCAFFOLD
}
