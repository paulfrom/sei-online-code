package com.changhong.onlinecode.service.memory;

import com.changhong.onlinecode.dao.MemoryJobDao;
import com.changhong.onlinecode.dto.enums.MemoryJobStatus;
import com.changhong.onlinecode.entity.MemoryJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * MemoryJob 调度器。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §10.6、§12。
 *
 * <p>定期轮询可执行的 PENDING job，按 projectId 串行：同一项目若已有 RUNNING job 则跳过。</p>
 *
 * @author sei-online-code
 */
@Component
public class MemoryJobScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryJobScheduler.class);

    private final MemoryJobDao memoryJobDao;
    private final MemoryJobExecutor executor;

    public MemoryJobScheduler(MemoryJobDao memoryJobDao, MemoryJobExecutor executor) {
        this.memoryJobDao = memoryJobDao;
        this.executor = executor;
    }

    /**
     * 轮询可执行 job。默认 30 秒一次，可通过 {@code oc.memory.job.poll-ms} 覆盖。
     */
    @Scheduled(fixedDelayString = "${oc.memory.job.poll-ms:30000}")
    public void poll() {
        List<MemoryJob> runnable = memoryJobDao.findRunnableJobs(MemoryJobStatus.PENDING, new Date());
        if (runnable.isEmpty()) {
            return;
        }
        Set<String> runningProjects = new HashSet<>();
        for (MemoryJob job : runnable) {
            String projectId = job.getProjectId();
            if (runningProjects.contains(projectId)) {
                continue;
            }
            if (hasActiveJob(projectId)) {
                runningProjects.add(projectId);
                continue;
            }
            runningProjects.add(projectId);
            try {
                executor.execute(job);
            } catch (Exception e) {
                LOGGER.error("memory-scheduler: 执行 job 异常 jobId={}", job.getId(), e);
            }
        }
    }

    private boolean hasActiveJob(String projectId) {
        List<MemoryJob> active = memoryJobDao.findByProjectIdAndStatusIn(projectId,
                List.of(MemoryJobStatus.PENDING, MemoryJobStatus.RUNNING));
        // 排除当前轮询准备执行的 PENDING？调度器逻辑保证同项目只取一个执行，因此存在 RUNNING 才算阻塞。
        return active.stream().anyMatch(j -> Objects.equals(j.getStatus(), MemoryJobStatus.RUNNING));
    }
}
