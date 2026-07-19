package com.changhong.onlinecode.ws;

import com.changhong.onlinecode.dto.progress.RequirementProgressEvent;
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
 * Requirement 进度 WebSocket hub。事件只作为刷新通知，权威状态仍来自 findOverview。
 */
@Component
@ServerEndpoint("/ws/requirement/{requirementId}/progress")
public class RequirementProgressWebSocketHub {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequirementProgressWebSocketHub.class);

    private static final Map<String, Set<Session>> SESSIONS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("requirementId") String requirementId) {
        SESSIONS.computeIfAbsent(requirementId, k -> ConcurrentHashMap.newKeySet()).add(session);
        LOGGER.info("requirement-progress ws connected: requirementId={}, sessionId={}",
                requirementId, session.getId());
    }

    @OnClose
    public void onClose(Session session, @PathParam("requirementId") String requirementId) {
        Set<Session> sessions = SESSIONS.get(requirementId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                SESSIONS.remove(requirementId);
            }
        }
        LOGGER.info("requirement-progress ws disconnected: requirementId={}, sessionId={}",
                requirementId, session.getId());
    }

    @OnError
    public void onError(Session session, @PathParam("requirementId") String requirementId, Throwable error) {
        LOGGER.warn("requirement-progress ws error: requirementId={}, sessionId={}",
                requirementId, session.getId(), error);
    }

    public static void broadcast(RequirementProgressEvent event) {
        if (event == null || event.getRequirementId() == null) {
            return;
        }
        Set<Session> sessions = SESSIONS.get(event.getRequirementId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = JsonUtils.mapper().writeValueAsString(event);
        } catch (Exception e) {
            LOGGER.warn("requirement-progress ws marshal failed", e);
            return;
        }
        for (Session session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.getBasicRemote().sendText(payload);
            } catch (IOException e) {
                LOGGER.debug("requirement-progress ws send failed, dropping session {}", session.getId(), e);
                sessions.remove(session);
            }
        }
    }
}
