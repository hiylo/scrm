/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OcrApiException.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import lombok.Getter;

/**
 * OCR 服务调用异常。
 * <p>
 * 未启用 / 令牌缺失 / HTTP 调用失败 / 响应不可解析时抛出, 由证照域服务捕获后
 * 落库失败状态（FAILED）并保留错误信息。错误信息中不得携带令牌等敏感内容。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Getter
public class OcrApiException extends RuntimeException {

    /** 是否可重试（限流 429 等瞬时故障可重试, 参数/鉴权类不可重试） */
    private final boolean retryable;

    /**
     * 构造 OCR 调用异常。
     *
     * @param message   错误描述
     * @param retryable 是否可重试
     */
    public OcrApiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    /**
     * 构造 OCR 调用异常（带原始原因, 默认不可重试）。
     *
     * @param message 错误描述
     * @param cause   原始异常
     */
    public OcrApiException(String message, Throwable cause) {
        super(message, cause);
        this.retryable = false;
    }
}