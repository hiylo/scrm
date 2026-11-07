/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : QixinApiException.java
 * Date : 2026/10/23 09:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import lombok.Getter;

/**
 * 启信宝企业信息 API 调用异常。
 * <p>
 * 统一承载: 未启用 / 凭证缺失 / 远程调用失败 / 返回码非成功四类失败场景,
 * 由巡检服务捕获后记为 PARTIAL/FAILED 执行结果并落错误快照, 不中断整轮巡检。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Getter
public class QixinApiException extends RuntimeException {

    /** 是否可重试（网络/限流类错误为 true, 凭证/业务码错误为 false） */
    private final boolean retryable;

    /**
     * 构造调用异常。
     *
     * @param message   错误描述
     * @param retryable 是否可重试
     */
    public QixinApiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    /**
     * 构造调用异常（带根因）。
     *
     * @param message   错误描述
     * @param cause     根因
     * @param retryable 是否可重试
     */
    public QixinApiException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }
}