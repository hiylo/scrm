/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : AiProviderInitializer.java
 * Date : 2026/11/07 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.hiylo.scrm.repository.ScrmAiProviderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * AI Provider 启动种子。
 * <p>
 * 仅当 {@code scrm_ai_provider} 表为空且环境变量 {@code SCRM_AI_BASE_URL}
 * 已配置时，自动创建一个默认 provider（沿用 {@code SCRM_AI_BASE_URL} /
 * {@code SCRM_AI_API_KEY} / {@code SCRM_AI_MODEL} 环境变量，若 key 未配置则
 * 视为本地无鉴权网关）。这样首次启动即有可用的文本+多模态 provider，
 * 之后可在系统管理内自由增删改/provider 切换，无需改环境变量。
 * </p>
 * <p>
 * 种子不含任何硬编码密钥：api_key 只从环境变量读取，不落代码、不入迁移 SQL。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
public class AiProviderInitializer implements ApplicationRunner {

    /** Provider 数据访问 */
    private final ScrmAiProviderRepository providerRepository;

    /** 运行时路由注册表 */
    private final AiProviderRegistry providerRegistry;

    /** AI 网关基础地址（可选，未配置则不自动种子） */
    @Value("${scrm.ai.base-url:}")
    private String aiBaseUrl;

    /** AI 网关 API 密钥（可选） */
    @Value("${scrm.ai.api-key:}")
    private String aiApiKey;

    /** AI 默认模型名（可选） */
    @Value("${scrm.ai.model:}")
    private String aiModel;

    /**
     * 构造启动种子。
     *
     * @param providerRepository Provider 数据访问
     * @param providerRegistry   Provider 运行时路由
     */
    public AiProviderInitializer(ScrmAiProviderRepository providerRepository,
                                 AiProviderRegistry providerRegistry) {
        this.providerRepository = providerRepository;
        this.providerRegistry = providerRegistry;
    }

    /**
     * 应用启动后执行种子逻辑。
     *
     * @param args 应用参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (providerRepository.count() > 0) {
            return;
        }
        String baseUrl = aiBaseUrl == null ? "" : aiBaseUrl.trim();
        if (baseUrl.isEmpty()) {
            log.info("AI Provider 表为空且未配置 SCRM_AI_BASE_URL, 跳过自动种子（可在系统管理手动添加）");
            return;
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        ScrmAiProviderEntity provider = new ScrmAiProviderEntity();
        provider.setProviderName("默认网关");
        provider.setProviderType("OPENAI_LLM");
        provider.setApiBaseUrl(baseUrl);
        provider.setChatPath("/v1/chat/completions");
        provider.setHealthPath("/v1/models");
        String key = aiApiKey == null ? "" : aiApiKey.trim();
        if (!key.isEmpty()) {
            provider.setApiKey(key);
        }
        provider.setCapability("text,image");
        String model = (aiModel == null || aiModel.isBlank()) ? "gpt-4o-mini" : aiModel.trim();
        provider.setDefaultModel(model);
        provider.setTemperature(0.7);
        provider.setMaxTokens(500);
        provider.setEnabled(true);
        provider.setDefaultText(true);
        provider.setDefaultImage(true);
        provider.setPriority(10);
        provider.setConnectionStatus("UNKNOWN");
        provider.setRemark("启动自动种子（SCRM_AI_BASE_URL 生成），可在系统管理修改或删除");
        ScrmAiProviderEntity saved = providerRepository.save(provider);
        providerRegistry.reload();
        log.info("AI Provider 自动种子已创建: id={}, name={}, base={}, key={}",
                saved.getId(), saved.getProviderName(), baseUrl,
                key.isEmpty() ? "<无鉴权>" : "<已配置>");
    }
}