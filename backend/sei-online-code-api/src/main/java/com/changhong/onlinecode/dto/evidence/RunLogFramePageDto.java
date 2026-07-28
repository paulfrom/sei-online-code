package com.changhong.onlinecode.dto.evidence;

import com.changhong.onlinecode.dto.run.RunLogFrame;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "可回放的 Run 日志帧分页")
public class RunLogFramePageDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String runId;
    private Long afterSequence;
    private Long nextSequence;
    private Boolean truncated;
    private List<RunLogFrame> frames = new ArrayList<>();
}
