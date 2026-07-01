package com.changhong.onlinecode.dto.run;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 运行日志帧。契约 §3.1 —— server→browser 的 newline-delimited JSON。
 *
 * <pre>
 * { "iterationId": "ITER0001", "stream": "stdout", "line": "vite v5 building…", "ts": "2026-07-01T10:10:03" }
 * </pre>
 *
 * <p>终帧携带 state：{ "stream": "system", "line": "DONE", "state": "PREVIEW" }。</p>
 *
 * @author sei-online-code
 */
@Schema(description = "运行日志帧")
public class RunLogFrame implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "迭代 id")
    private String iterationId;

    @Schema(description = "流类型：stdout | stderr | system")
    private String stream;

    @Schema(description = "日志行")
    private String line;

    @Schema(description = "时间戳（ISO-8601）")
    private String ts;

    @Schema(description = "终帧携带的生命周期状态；非终帧为 null")
    private String state;

    public RunLogFrame() {
    }

    public RunLogFrame(String iterationId, String stream, String line, String ts) {
        this.iterationId = iterationId;
        this.stream = stream;
        this.line = line;
        this.ts = ts;
    }

    public String getIterationId() {
        return iterationId;
    }

    public void setIterationId(String iterationId) {
        this.iterationId = iterationId;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
