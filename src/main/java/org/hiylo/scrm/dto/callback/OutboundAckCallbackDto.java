/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OutboundAckCallbackDto.java
 * Date : 2026/10/07 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto.callback;

import lombok.Data;

/**
 * 出站消息发送回执回调 DTO。
 * <p>
 * 执行侧（wx-console / wechat-agent）通过 {@code POST /scrm/callback/outbound/ack}
 * 上报一条出站消息的最终发送结果。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class OutboundAckCallbackDto {

    /** 出站队列记录 ID（必须） */
    private Long outboundId;

    /** 发送状态：SENT（成功）或 FAILED（失败，须附 errorMessage） */
    private String status;

    /** 失败原因（status=FAILED 时必填） */
    private String errorMessage;

    /** 平台侧消息 ID（可选，发送成功后微信侧的消息标识） */
    private String platformMessageId;
}