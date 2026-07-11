package com.changhong.onlinecode.service.memory;

import lombok.Data;

/**
 * 扫描到的源文件摘要。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §11。
 *
 * @author sei-online-code
 */
@Data
public class ScannedSourceFile {

    private String path;

    private String fingerprint;

    private long size;

    /** 首行非空注释、包名或导入等摘要。 */
    private String summary;
}
