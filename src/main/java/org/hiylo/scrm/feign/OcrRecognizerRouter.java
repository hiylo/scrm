/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : OcrRecognizerRouter.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.AiProviderRegistry;
import org.hiylo.scrm.config.VisionModelProperties;
import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.hiylo.scrm.feign.dto.ocr.OcrRecognizeResponse;
import org.hiylo.scrm.feign.vision.LocalVisionOcrClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 证照识别路由（AI Provider 多模态优先，本地视觉兜底）。
 * <p>
 * 传统外接 OCR 服务（wx-console ocr-service / RapidOCR）已于 2026-11-08 移除——
 * 多模态大模型语义化抽取效果优于纯文字识别且自带结构化输出，证照 OCR 只保留
 * 两条通道:
 * </p>
 * <ul>
 *   <li><b>AI Provider 多模态（首选）</b>：系统管理内配置的 provider 具备
 *       {@code image} 能力且启用时，路由到该 provider 的视觉模型（如 Magpie
 *       网关 group/sensenova-pool），图片经内网网关识别，不占本机 GPU，
 *       Provider 变更即时生效。</li>
 *   <li>{@code scrm.vision.enabled=true} → {@link LocalVisionOcrClient}
 *       （本地视觉模型, 数据不出内网, 身份证等敏感件首选）</li>
 *   <li>两者都未启用 → 抛 {@link OcrApiException}（fail-closed）</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Primary
@Component
public class OcrRecognizerRouter implements OcrRecognizer {

    /** 本地视觉识别实现 */
    private final LocalVisionOcrClient localVisionOcrClient;

    /** 视觉模型配置（判定是否启用本地通道） */
    private final VisionModelProperties visionProperties;

    /** AI Provider 运行时路由（多模态能力来源） */
    private final AiProviderRegistry providerRegistry;

    /**
     * 构造路由。
     *
     * @param localVisionOcrClient 本地视觉识别实现
     * @param visionProperties     视觉模型配置
     * @param providerRegistry     AI Provider 运行时路由
     */
    public OcrRecognizerRouter(LocalVisionOcrClient localVisionOcrClient,
                               VisionModelProperties visionProperties,
                               AiProviderRegistry providerRegistry) {
        this.localVisionOcrClient = localVisionOcrClient;
        this.visionProperties = visionProperties;
        this.providerRegistry = providerRegistry;
    }

    /**
     * 按能力路由识别请求。
     *
     * @param file      证照图片
     * @param type      识别类型
     * @param requestId 链路标识
     * @return 统一识别响应
     * @throws OcrApiException 两条通道均未启用或调用失败
     */
    @Override
    public OcrRecognizeResponse recognize(MultipartFile file, String type, String requestId)
            throws OcrApiException {
        // 1) AI Provider 多模态（首选，能力路由，不占本机 GPU）
        var imageProvider = providerRegistry.resolveImageProvider();
        if (imageProvider.isPresent()) {
            ScrmAiProviderEntity provider = imageProvider.get();
            log.debug("[证照识别] 路由到 AI Provider 多模态: provider={}, model={}",
                    provider.getProviderName(), provider.getDefaultModel());
            return localVisionOcrClient.recognizeWithProvider(file, type, requestId, provider);
        }
        // 2) 本地视觉（环境变量 scrm.vision.*；数据不出内网，身份证等敏感件兜底）
        if (visionProperties.isEnabled()) {
            log.debug("[证照识别] 路由到本地视觉模型: type={}", type);
            return localVisionOcrClient.recognize(file, type, requestId);
        }
        throw new OcrApiException("证照识别未启用（请先在系统管理配置 AI Provider 多模态能力，或开启本地视觉）", false);
    }
}