package com.changhong.onlinecode.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically reconciles persisted comment-driven requirement loops. */
@Component
@Slf4j
@AllArgsConstructor
public class CompensationScheduler {

    private final CompensationService compensationService;

    @Scheduled(fixedDelayString = "${onlinecode.compensation.fixed-delay-ms:60000}")
    public void run() {
        log.info("CompensationScheduler is running");
        try {
            compensationService.runCycle();
        } catch (Exception e) {
            log.error("comment-driven loop compensation failed", e);
        }
    }
}
