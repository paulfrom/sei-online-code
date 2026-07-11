package com.changhong.onlinecode.service.memory;

import lombok.Data;

import java.util.List;

/**
 * WorkspaceFileScanner 扫描结果。
 *
 * @author sei-online-code
 */
@Data
public class ScanResult {

    private List<ScannedSourceFile> files;

    private boolean truncated;

    private String reason;

    private int totalFiles;

    private long totalBytes;
}
