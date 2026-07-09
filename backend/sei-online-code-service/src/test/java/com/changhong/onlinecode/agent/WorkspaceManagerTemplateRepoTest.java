package com.changhong.onlinecode.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkspaceManagerTemplateRepoTest {

    @Test
    void resolvesHttpGitlabUrlToHostAndProjectPath() {
        WorkspaceManager.TemplateRepo repo =
                WorkspaceManager.resolveTemplateRepo("https://gitlab.example.com/group/sub/demo.git");

        assertEquals("https://gitlab.example.com", repo.host());
        assertEquals("group/sub/demo", repo.projectPath());
    }

    @Test
    void keepsProjectPathWhenConfigStoresPathOnly() {
        WorkspaceManager.TemplateRepo repo = WorkspaceManager.resolveTemplateRepo("group/sub/demo");

        assertEquals("", repo.host());
        assertEquals("group/sub/demo", repo.projectPath());
    }
}
