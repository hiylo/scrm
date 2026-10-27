/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OcrRecognizerRouter.java
 * Date : 2026/10/23 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.VisionModelProperties;
import org.hiylo.scrm.feign.dto.ocr.OcrRecognizeResponse;
import org.hiylo.scrm.feign.vision.LocalVisionOcrClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 证照识别路由（按配置选择本地视觉模型或外接 OCR 服务）。
 * <p>
 * 标注 {@code @Primary}, 证照域注入 {@link OcrRecognizer} 时默认走本路由,
 * 由配置决定实际实现:
 * </p>
 * <ul>
 *   <li>{@code scrm.vision.enabled=true} → {@link LocalVisionOcrClient}
 *       （<b>本地视觉模型, 数据不出内网, 身份证等敏感件首选</b>）</li>
 *   <li>否则 {@code scrm.ocr.enabled=true} → {@link OcrRecognizeClient}
 *       （外接 OCR 服务, 适合大批量、非敏感材料）</li>
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

    /** 外接 OCR 服务实现 */
    private final OcrRecognizeClient ocrRecognizeClient;

    /** 视觉模型配置（判定是否启用本地通道） */
    private final VisionModelProperties visionProperties;

    /**
     * 构造路由。
     *
     * @param localVisionOcrClient 本地视觉识别实现
     * @param ocrRecognizeClient   外接 OCR 服务实现
     * @param visionProperties     视觉模型配置
     */
    public OcrRecognizerRouter(LocalVisionOcrClient localVisionOcrClient,
                               OcrRecognizeClient ocrRecognizeClient,
                               VisionModelProperties visionProperties) {
        this.localVisionOcrClient = localVisionOcrClient;
        this.ocrRecognizeClient = ocrRecognizeClient;
        this.visionProperties = visionProperties;
    }

    /**
     * 按配置路由识别请求。
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
        if (visionProperties.isEnabled()) {
            log.debug("[证照识别] 路由到本地视觉模型: type={}", type);
            return localVisionOcrClient.recognize(file, type, requestId);
        }
        log.debug("[证照识别] 路由到外接 OCR 服务: type={}", type);
        return ocrRecognizeClient.recognize(file, type, requestId);
    }
}
