package com.changhong.onlinecode.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 执行有界线程池配置（方案 §7）。
 *
 * <p>为 test-agent 验证执行与 pm-agent 交付审阅提供专用有界线程池，避免无界 common pool
 * 占用资源。这是本代码库首个显式 {@code TaskExecutor} bean（现状 {@code @EnableAsync}
 * 走默认 {@code SimpleAsyncTaskExecutor}）。</p>
 *
 * <ul>
 *   <li>有界队列（容量走 {@link OcConfig}）；</li>
 *   <li>明确 core/max 线程数；</li>
 *   <li>拒绝策略 {@code AbortPolicy}：由提交方显式处理过载，禁止回退到调度/事务线程执行 agent；</li>
 *   <li>优雅停机等待在飞任务。</li>
 * </ul>
 *
 * @author sei-online-code
 */
@Configuration
@Slf4j
public class OcAgentExecutorConfig {

    /**
     * test-agent 验证执行线程池。
     *
     * <p>用于 {@code ValidationTaskExecutionService} 的 execute 阶段：事务外运行 test-agent，
     * 不占用 scheduler 数据库事务（方案 §7 三阶段拆分）。</p>
     */
    @Bean(name = "validationAgentExecutor")
    public ThreadPoolTaskExecutor validationAgentExecutor(OcConfig ocConfig) {
        return buildBoundedExecutor("validation-agent-", ocConfig);
    }

    /** coding agent 异步入口，避免使用无界 common pool。 */
    @Bean(name = "agentExecutionExecutor")
    public ThreadPoolTaskExecutor agentExecutionExecutor(OcConfig ocConfig) {
        return buildBoundedExecutor("agent-execution-", ocConfig);
    }

    /**
     * pm-agent 交付审阅线程池。
     *
     * <p>用于 {@code TaskDeliveryReviewEventListener} 的异步 PM 审阅（方案 §5.1 / §7）。</p>
     */
    @Bean(name = "pmReviewExecutor")
    public ThreadPoolTaskExecutor pmReviewExecutor(OcConfig ocConfig) {
        return buildBoundedExecutor("pm-review-", ocConfig);
    }

    private ThreadPoolTaskExecutor buildBoundedExecutor(String threadNamePrefix, OcConfig ocConfig) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int corePoolSize = Math.max(1, ocConfig.getAgentExecutorCorePoolSize());
        int maxPoolSize = Math.max(corePoolSize, ocConfig.getAgentExecutorMaxPoolSize());
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(Math.max(1, ocConfig.getAgentExecutorQueueCapacity()));
        executor.setThreadNamePrefix(threadNamePrefix);
        // 禁止 CallerRunsPolicy 把长时间 agent 调用回退到调度/事件线程。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("agent executor initialized: prefix={}, core={}, max={}, queue={}",
                threadNamePrefix, executor.getCorePoolSize(), executor.getMaxPoolSize(),
                ocConfig.getAgentExecutorQueueCapacity());
        return executor;
    }
}
