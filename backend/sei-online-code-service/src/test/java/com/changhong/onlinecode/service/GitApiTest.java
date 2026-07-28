package com.changhong.onlinecode.service;

import org.gitlab4j.api.GitLabApi;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class GitApiTest {

    @Test
    void upload_usesHostFromRepositoryTarget() {
        TrackingGitApi gitApi = new TrackingGitApi();
        GitApi.RepositoryTarget target = new GitApi.RepositoryTarget(
                "https://project.gitlab.example.com", "group/project");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> gitApi.upload(Path.of("."), target, "feature/req-1", "main", "deliver"));

        assertEquals(target.host(), gitApi.requestedHost);
        assertEquals("通过 Git API 上传仓库变更失败: host=https://project.gitlab.example.com, "
                + "project=group/project, branch=feature/req-1", exception.getMessage());
    }

    @Test
    void getBranchHead_usesHostFromRepositoryTarget() {
        TrackingGitApi gitApi = new TrackingGitApi();
        GitApi.RepositoryTarget target = new GitApi.RepositoryTarget(
                "https://project.gitlab.example.com", "group/project");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> gitApi.getBranchHead(target, "feature/req-1"));

        assertEquals(target.host(), gitApi.requestedHost);
        assertEquals("通过 Git API 获取分支失败: host=https://project.gitlab.example.com, "
                + "project=group/project, branch=feature/req-1", exception.getMessage());
    }

    private static final class TrackingGitApi extends GitApi {

        private String requestedHost;

        private TrackingGitApi() {
            super(mock(ConfigService.class));
        }

        @Override
        public GitLabApi client(String host) {
            requestedHost = host;
            throw new IllegalStateException("stop before network access");
        }
    }
}
