/**
 * RunLog WebSocket client for P2: D4 WS client
 * Connects to /ws/run/{iterationId}, parses NDJSON frames, filters by runId
 *
 * Dependencies:
 * - Backend iterationId === featureDesignId (FeatureDesignBuildService.java:92)
 *   TODO: backend cleanup will require changing to build-returned iterationId
 */

export interface RunLogFrame {
  iterationId: string;
  taskId: string;
  runId: string;
  stream: string;
  line: string;
  ts: string;
  state?: string;
}

export interface RunLogSocketOptions {
  iterationId: string;
  runId: string;
  onLine: (frame: RunLogFrame) => void;
  onTerminal: (state: string) => void;
  onError?: (error: Event) => void;
}

export interface RunLogSocket {
  close: () => void;
}

/**
 * Parse newline-delimited JSON stream
 */
function parseNDJSON(data: string): RunLogFrame[] {
  const lines = data.trim().split('\n');
  const frames: RunLogFrame[] = [];

  for (const line of lines) {
    if (line.trim()) {
      try {
        frames.push(JSON.parse(line));
      } catch {
        // Ignore invalid JSON lines
      }
    }
  }

  return frames;
}

/**
 * Subscribe to run-log WebSocket
 * Returns close() function to clean up connection
 */
export function subscribeRunLog({
  iterationId,
  runId,
  onLine,
  onTerminal,
  onError,
}: RunLogSocketOptions): RunLogSocket {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsUrl = `${protocol}//${window.location.host}/ws/run/${iterationId}`;
  const ws = new WebSocket(wsUrl);

  ws.onmessage = (event) => {
    const frames = parseNDJSON(event.data);
    for (const frame of frames) {
      // Filter by runId to only show logs for this feature
      if (frame.runId === runId) {
        onLine(frame);
        // Terminal frame has state defined
        if (frame.state) {
          onTerminal(frame.state);
        }
      }
    }
  };

  ws.onerror = (error) => {
    if (onError) {
      onError(error);
    }
  };

  return {
    close: () => {
      if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
        ws.close();
      }
    },
  };
}
