package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dao.RunDao;
import com.changhong.onlinecode.entity.Run;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** Assigns monotonic display numbers to Runs within their owning execution scope. */
@Service
public class RunNumberService {

    private final RunDao runDao;

    public RunNumberService(RunDao runDao) {
        this.runDao = runDao;
    }

    public synchronized Run assign(Run run) {
        if (run == null || run.getRunNo() != null) {
            return run;
        }
        run.setRunNo(nextRunNo(run));
        return run;
    }

    private Integer nextRunNo(Run run) {
        List<Run> existing = existingRuns(run);
        return existing.stream()
                .mapToInt(item -> Objects.requireNonNullElse(item.getRunNo(), 0))
                .max()
                .orElse(0) + 1;
    }

    private List<Run> existingRuns(Run run) {
        if (hasText(run.getRequirementId())) {
            if (hasText(run.getLoopId())) {
                return runDao.findByRequirementIdAndLoopId(run.getRequirementId(), run.getLoopId());
            }
            return runDao.findByRequirementIdOrderByCreatedDateDesc(run.getRequirementId());
        }
        if (hasText(run.getCodingTaskId())) {
            return runDao.findByCodingTaskId(run.getCodingTaskId());
        }
        if (hasText(run.getTaskId())) {
            return runDao.findByTaskId(run.getTaskId());
        }
        if (hasText(run.getIterationId())) {
            return runDao.findByIterationId(run.getIterationId());
        }
        return List.of();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
