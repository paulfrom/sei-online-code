package com.changhong.onlinecode.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 补偿调度器。
 */
@Component
public class CompensationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompensationScheduler.class);

    private final CompensationService compensationService;

    public CompensationScheduler(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    /**
     * 定时触发补偿扫描。
     *
     * <p>捕获全部异常，避免单轮补偿失败影响后续调度。</p>
     */
    @Scheduled(fixedDelayString = "${onlinecode.compensation.fixed-delay-ms:60000}")
    public void run() {
        LOGGER.debug("补偿调度器触发");
        try {
            compensationService.runCycle();
        } catch (Exception e) {
            LOGGER.error("补偿调度器执行异常", e);
        }
    }
}
