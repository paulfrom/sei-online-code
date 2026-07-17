/**
 * Requirement progress WebSocket client.
 *
 * Subscribes to /ws/requirement/{requirementId}/progress (ADR-001 §11 /
 * 计划 §3). Events only signal "refresh" — they carry snapshotVersion but no
 * authoritative state. The consumer refetches the overview when a higher
 * snapshotVersion arrives; on disconnect the consumer falls back to polling.
 *
 * Differs from run-log-socket: progress frames are a single JSON object per
 * message (not NDJSON), and this client auto-reconnects with a bounded delay
 * so a transient drop recovers without tearing the whole subscription down.
 */

/** Mirrors backend RequirementProgressEvent. Unknown enum values pass through. */
export interface RequirementProgressEvent {
  eventType?: string;
  requirementId?: string;
  entityId?: string | null;
  snapshotVersion?: number | null;
  occurredAt?: string | null;
}

export interface RequirementProgressSocketOptions {
  requirementId: string;
  /** Notified for every inbound event (already JSON-parsed). */
  onEvent: (event: RequirementProgressEvent) => void;
  /** Notified when the socket drops (consumer should mark state stale). */
  onDisconnect?: () => void;
  /** Notified after a successful (re)connect. */
  onReconnect?: () => void;
}

export interface RequirementProgressSocket {
  close: () => void;
}

/** Fixed reconnect delay; polling (5s/30s) remains the convergence fallback. */
const RECONNECT_DELAY = 3000;

/**
 * Subscribe to requirement progress events. Returns a handle whose close()
 * tears the connection down and stops reconnect attempts.
 */
export function subscribeRequirementProgress({
  requirementId,
  onEvent,
  onDisconnect,
  onReconnect,
}: RequirementProgressSocketOptions): RequirementProgressSocket {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsUrl = `${protocol}//${window.location.host}/ws/requirement/${requirementId}/progress`;

  let ws: WebSocket | null = null;
  let closed = false;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  const clearTimer = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
  };

  const connect = () => {
    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      onReconnect?.();
    };

    ws.onmessage = (event) => {
      // Progress frames are a single JSON object per message.
      try {
        const parsed = JSON.parse(event.data);
        if (parsed && typeof parsed === 'object') {
          onEvent(parsed as RequirementProgressEvent);
        }
      } catch {
        // Ignore malformed frames — polling converges authoritative state.
      }
    };

    ws.onclose = () => {
      ws = null;
      if (closed) return;
      onDisconnect?.();
      reconnectTimer = setTimeout(connect, RECONNECT_DELAY);
    };

    // onerror is followed by onclose; no separate handling needed.
    ws.onerror = () => {
      /* close handler performs reconnect / notification */
    };
  };

  connect();

  return {
    close: () => {
      closed = true;
      clearTimer();
      if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
        ws.close();
      }
      ws = null;
    },
  };
}
