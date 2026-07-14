package com.changhong.onlinecode.agent;

import java.nio.file.Path;

/**
 * 由平台为项目解析并校验后的 Agent 工作区绑定。
 *
 * <p>构造器仅对 agent 包可见，业务服务只能通过
 * {@link CliRunnerRegistry#workspace(String)} 获取，不能自行伪造 cwd。</p>
 */
public final class AgentWorkspace {

    private final String projectId;
    private final Path path;

    AgentWorkspace(String projectId, Path path) {
        this.projectId = projectId;
        this.path = path;
    }

    public String projectId() {
        return projectId;
    }

    public Path path() {
        return path;
    }

    public String pathString() {
        return path.toString();
    }
}
