package com.changhong.onlinecode.ws;

import com.changhong.onlinecode.dto.run.RunLogFrame;
import com.changhong.sei.core.util.JsonUtils;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行日志 WebSocket hub（B8）。契约 §3.1，方向仅 server→browser。
 *
 * <p>参考 multica {@code server/internal/daemonws/hub.go}：按 logStreamKey 索引连接，
 * 广播为 best-effort（发送失败即剔除慢连接）。本轮为 compile-only skeleton，
 * 连接注册/剔除与广播已实现，实际日志由 ClaudeRunner 在后续接入时调用 {@link #broadcast}。</p>
 *
 * <p>URL：{@code ws://<host>/ws/run/{logStreamKey}}。</p>
 *
 * @author sei-online-code
 */
@Component
@ServerEndpoint("/ws/run/{logStreamKey}")
public class RunLogWebSocketHub {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunLogWebSocketHub.class);

    /** logStreamKey → 该迭代的浏览器连接集合。 */
    private static final Map<String, Set<Session>> SESSIONS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("logStreamKey") String logStreamKey) {
        SESSIONS.computeIfAbsent(logStreamKey, k -> ConcurrentHashMap.newKeySet()).add(session);
        LOGGER.info("run-log ws connected: logStreamKey={}, sessionId={}", logStreamKey, session.getId());
    }

    @OnClose
    public void onClose(Session session, @PathParam("logStreamKey") String logStreamKey) {
        Set<Session> sessions = SESSIONS.get(logStreamKey);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                SESSIONS.remove(logStreamKey);
            }
        }
        LOGGER.info("run-log ws disconnected: logStreamKey={}, sessionId={}", logStreamKey, session.getId());
    }

    @OnError
    public void onError(Session session, @PathParam("logStreamKey") String logStreamKey, Throwable error) {
        LOGGER.warn("run-log ws error: logStreamKey={}, sessionId={}", logStreamKey, session.getId(), error);
    }

    /**
     * 向订阅该迭代的所有浏览器连接广播一帧运行日志（server→browser）。
     *
     * @param frame 运行日志帧
     */
    public static void broadcast(RunLogFrame frame) {
        if (frame == null || frame.getLogStreamKey() == null) {
            return;
        }
        Set<Session> sessions = SESSIONS.get(frame.getLogStreamKey());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = JsonUtils.mapper().writeValueAsString(frame);
        } catch (Exception e) {
            LOGGER.warn("run-log ws marshal failed", e);
            return;
        }
        for (Session session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.getBasicRemote().sendText(payload);
            } catch (IOException e) {
                LOGGER.debug("run-log ws send failed, dropping session {}", session.getId(), e);
                sessions.remove(session);
            }
        }
    }
}
