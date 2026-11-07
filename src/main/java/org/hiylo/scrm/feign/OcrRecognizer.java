/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : OcrRecognizer.java
 * Date : 2026/10/23 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import org.hiylo.scrm.feign.dto.ocr.OcrRecognizeResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 证照识别统一入口（实现可替换）。
 * <p>
 * 由 {@link OcrRecognizerRouter} 按能力路由:
 * <ul>
 *   <li>AI Provider 多模态（首选，系统管理内配置的 vision 能力 provider）</li>
 *   <li>{@link org.hiylo.scrm.feign.vision.LocalVisionOcrClient} —— 本地视觉大模型（llama-server, 数据不出内网）</li>
 * </ul>
 * 外接 OCR 服务（wx-console ocr-service / RapidOCR）已于 2026-11-08 移除——
 * 多模态语义化抽取 + 确定性字段校验优于纯文字识别。各实现返回统一
 * {@link OcrRecognizeResponse} 契约, 证照域（任务/字段落库/低置信度复核）
 * 无需感知底层是哪一种。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface OcrRecognizer {

    /**
     * 识别一张证照图片。
     *
     * @param file      证照图片（≤20MB）
     * @param type      识别类型: license / idcard / account_license / general
     * @param requestId 调用方幂等/链路标识（可空）
     * @return 统一识别响应（含字段、交叉校验、问题明细）
     * @throws OcrApiException 未启用 / 凭据缺失 / 文件非法 / 传输或业务错误
     */
    OcrRecognizeResponse recognize(MultipartFile file, String type, String requestId)
            throws OcrApiException;
}
