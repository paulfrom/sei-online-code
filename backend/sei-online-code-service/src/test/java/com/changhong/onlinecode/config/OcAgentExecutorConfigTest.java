package com.changhong.onlinecode.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OcAgentExecutorConfigTest {

    @Test
    void validationExecutorUsesAbortPolicy() {
        OcConfig config = mock(OcConfig.class);
        when(config.getAgentExecutorCorePoolSize()).thenReturn(1);
        when(config.getAgentExecutorMaxPoolSize()).thenReturn(2);
        when(config.getAgentExecutorQueueCapacity()).thenReturn(4);

        ThreadPoolTaskExecutor executor = new OcAgentExecutorConfig().validationAgentExecutor(config);
        try {
            assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class,
                    executor.getThreadPoolExecutor().getRejectedExecutionHandler());
        } finally {
            executor.shutdown();
        }
    }
}
