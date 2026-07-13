package com.changhong.onlinecode.service.validation;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 验证命令执行器抽象。
 *
 * <p>调度器在开发任务成功后调用，执行项目/任务级验证命令，返回退出码与输出摘要。</p>
 */
public interface ValidationCommandExecutor {

    /**
     * 在指定工作目录执行验证命令。
     *
     * @param workingDir 工作区路径
     * @param command    待执行命令（按空格拆分后交给 {@link ProcessBuilder}）
     * @return 执行结果
     */
    ValidationResult execute(Path workingDir, String command);

    /**
     * 验证结果。
     */
    class ValidationResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final Duration duration;

        public ValidationResult(int exitCode, String stdout, String stderr, Duration duration) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.duration = duration;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public Duration getDuration() {
            return duration;
        }
    }
}
