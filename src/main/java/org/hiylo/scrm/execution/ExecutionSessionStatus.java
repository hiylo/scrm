/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ExecutionSessionStatus.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行会话状态快照。
 * <p>
 * 用于 {@link TaskExecutionService#getSessionStatus(String)} 返回,
 * 携带会话 ID / 状态 / 错误码 / 错误消息, 供账号健康度检测等场景消费。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionSessionStatus {

    /** 会话 ID */
    private String sessionId;

    /** 会话状态: 如 RUNNING / COMPLETED / FAILED */
    private String status;

    /** 错误码 (可空) */
    private String errorCode;

    /** 错误消息 (可空) */
    private String errorMessage;
}
