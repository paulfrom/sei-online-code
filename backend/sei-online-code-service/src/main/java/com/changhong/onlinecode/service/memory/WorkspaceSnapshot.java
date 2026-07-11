package com.changhong.onlinecode.service.memory;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工作区代码现状快照。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §5.2。
 *
 * @author sei-online-code
 */
@Data
public class WorkspaceSnapshot {

    private List<String> modules;

    private List<String> entrypoints;

    private List<String> apiSurface;

    private List<String> dataModel;

    private List<String> uiSurface;

    private List<String> stateModel;

    private List<String> integrationPoints;

    private Map<String, Object> scanLimits;

    private List<ScannedSourceFile> sourceFiles;
}
