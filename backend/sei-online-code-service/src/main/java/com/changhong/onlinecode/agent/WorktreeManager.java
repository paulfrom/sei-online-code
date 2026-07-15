package com.changhong.onlinecode.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * WorktreeManager（B13）。参考 multica {@code server/internal/daemon/execenv/git.go}。
 *
 * <p>每个 Task 获得一个隔离的 git worktree，并行开发互不干扰，完成后合并回主干
 * （ADR-0001）。封装以下 git 命令：</p>
 * <ul>
 *   <li>{@code git -C <root> worktree add -b <branch> <path> <baseRef>} —— 新建 worktree + 分支</li>
 *   <li>{@code git -C <root> worktree remove --force <path>} —— 移除 worktree</li>
 *   <li>{@code git -C <root> branch -D <branch>} —— 删除分支（尽力而为）</li>
 *   <li>{@code git -C <root> merge --ff-only <branch>} —— 快进合并；失败即冲突，回退串行重解</li>
 * </ul>
 *
 * <p>本轮为 compile-only 骨架：命令封装就绪但不在本轮运行；真实的分支名冲突重试、
 * 冲突串行重解（A-primary + B-fallback）、超时/清理属后续接入项。</p>
 *
 * @author sei-online-code
 */
@Component
@Slf4j
public class WorktreeManager {

    /**
     * 为某任务在 gitRoot 下新建 worktree 与分支。
     *
     * @param gitRoot      工作区仓库根目录
     * @param worktreePath worktree 目标路径（git 负责创建）
     * @param branchName   新分支名
     * @param baseRef      基准 ref（如 origin/main 或 HEAD）
     * @return true 表示创建成功
     */
    public boolean addWorktree(String gitRoot, String worktreePath, String branchName, String baseRef) {
        // git worktree add 需要自行创建目录，若占位目录已存在则先删除（参考 git.go setupGitWorktree）。
        File placeholder = new File(worktreePath);
        if (placeholder.exists() && !placeholder.delete()) {
            log.warn("worktree: 占位目录删除失败 path={}", worktreePath);
        }
        // TODO(oma-deferred): 分支名冲突时追加时间戳重试（参考 git.go 的 branchName-<ts> 兜底）
        return runGit(gitRoot, "worktree", "add", "-b", branchName, worktreePath, baseRef);
    }

    /**
     * 快进合并任务分支回当前主干。返回 false 即视为冲突，触发串行重解回退。
     *
     * @param gitRoot    工作区仓库根目录
     * @param branchName 待合并分支
     * @return true 表示 fast-forward 成功；false 表示存在冲突
     */
    public boolean mergeFastForward(String gitRoot, String branchName) {
        boolean ok = runGit(gitRoot, "merge", "--ff-only", branchName);
        if (!ok) {
            // TODO(oma-deferred): 冲突回退（ADR-0001 A-primary + B-fallback）—— 由责任 Task 串行重解后重试
            log.warn("worktree: fast-forward 合并失败（冲突），需串行重解 branch={}", branchName);
        }
        return ok;
    }

    /**
     * 移除 worktree 并删除其分支（尽力而为，失败仅记录日志）。
     *
     * @param gitRoot      工作区仓库根目录
     * @param worktreePath worktree 路径
     * @param branchName   分支名
     */
    public void removeWorktree(String gitRoot, String worktreePath, String branchName) {
        if (!runGit(gitRoot, "worktree", "remove", "--force", worktreePath)) {
            log.warn("worktree: 移除失败 path={}", worktreePath);
        }
        if (branchName != null && !branchName.isBlank()
                && !runGit(gitRoot, "branch", "-D", branchName)) {
            log.warn("worktree: 分支删除失败 branch={}", branchName);
        }
    }

    /**
     * 执行一条 git 子命令（{@code git -C <gitRoot> <args...>}）。
     *
     * @param gitRoot 仓库根目录
     * @param args    git 子命令与参数
     * @return true 表示进程退出码为 0
     */
    private boolean runGit(String gitRoot, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(gitRoot);
        for (String a : args) {
            cmd.add(a);
        }
        try {
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0) {
                log.warn("git 命令失败 code={} cmd={} output={}", code, String.join(" ", cmd), output.trim());
                return false;
            }
            return true;
        } catch (IOException e) {
            log.warn("git 命令执行异常 cmd={}", String.join(" ", cmd), e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
