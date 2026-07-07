package com.changhong.onlinecode.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 补偿调度器。
 */
@Component
public class CompensationScheduler {

    private final CompensationService compensationService;

    public CompensationScheduler(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @Scheduled(fixedDelayString = "${onlinecode.compensation.fixed-delay-ms:60000}")
    public void run() {
        compensationService.runCycle();
    }
}
