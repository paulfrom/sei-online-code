package com.changhong.onlinecode.service.evidence;

import com.changhong.onlinecode.dto.run.RunLogFrame;
import com.changhong.onlinecode.ws.RunLogWebSocketHub;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 将静态 WebSocket 边界接回 Spring 管理的脱敏与持久化服务。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RunLogPersistenceBridge {

    private final RunArtifactService artifactService;
    private final RunLogRedactor redactor;

    @jakarta.annotation.PostConstruct
    void register() {
        RunLogWebSocketHub.setFrameProcessor(this::process);
    }

    @PreDestroy
    void unregister() {
        RunLogWebSocketHub.setFrameProcessor(null);
    }

    RunLogFrame process(RunLogFrame frame) {
        frame.setLine(redactor.redact(frame.getLine()));
        try {
            return artifactService.appendLogFrame(frame);
        } catch (Exception e) {
            // 日志存储故障不改变 Run 的执行语义。
            log.warn("run log persistence failed: runId={}", frame.getRunId(), e);
            return frame;
        }
    }
}
