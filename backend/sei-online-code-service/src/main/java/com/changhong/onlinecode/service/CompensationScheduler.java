package com.changhong.onlinecode.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically reconciles persisted comment-driven requirement loops. */
@Component
public class CompensationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompensationScheduler.class);

    private final CompensationService compensationService;

    public CompensationScheduler(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @Scheduled(fixedDelayString = "${onlinecode.compensation.fixed-delay-ms:60000}")
    public void run() {
        LOGGER.info("CompensationScheduler is running");
        try {
            compensationService.runCycle();
        } catch (Exception e) {
            LOGGER.error("comment-driven loop compensation failed", e);
        }
    }
}
