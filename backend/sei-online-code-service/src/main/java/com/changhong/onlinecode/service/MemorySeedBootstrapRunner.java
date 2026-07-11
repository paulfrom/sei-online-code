package com.changhong.onlinecode.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动期 seed 记忆模板 bootstrap。契约 WORKSPACE-MEMORY-IMPLEMENTATION-PLAN §6.1.0、§10.1。
 *
 * <p>正常启动时由平台自动保证全局默认模板存在：若 DB 不存在 {@code ACTIVE + is_default=true} 默认模板，
 * 从 classpath {@code memory-seeds/default} 创建一条 builtin 默认模板。避免只在 API 请求或项目初始化时
 * 被动触发，导致首项目创建前默认模板缺失。</p>
 *
 * <p>失败不阻断启动（默认模板缺失时项目初始化兜底仍会再次 bootstrap），仅记录告警。</p>
 *
 * @author sei-online-code
 */
@Component
@Order(20)
public class MemorySeedBootstrapRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemorySeedBootstrapRunner.class);

    private final MemorySeedTemplateService seedTemplateService;

    public MemorySeedBootstrapRunner(MemorySeedTemplateService seedTemplateService) {
        this.seedTemplateService = seedTemplateService;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        try {
            seedTemplateService.bootstrapDefaultIfAbsent();
            LOGGER.info("memory-seed: 启动期默认模板 bootstrap 完成");
        } catch (Exception e) {
            LOGGER.warn("memory-seed: 启动期 bootstrap 默认模板失败，将由项目初始化兜底重试", e);
        }
    }
}
