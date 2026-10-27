/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : VisionModelException.java
 * Date : 2026/10/23 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign.vision;

/**
 * 本地视觉模型异常（启动失败 / 服务不可达 / 调用失败）。
 * <p>
 * 属可降级异常: 证照域捕获后落 FAILED + 转人工复核, 不阻断业务流程。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public class VisionModelException extends Exception {

    /**
     * 构造异常。
     *
     * @param message 错误描述
     */
    public VisionModelException(String message) {
        super(message);
    }

    /**
     * 构造异常（保留根因）。
     *
     * @param message 错误描述
     * @param cause   根因
     */
    public VisionModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
