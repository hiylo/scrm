/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ConversationEventCallbackDto.java
 * Date : 2026/09/19 21:20:11
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto.callback;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话事件回调 DTO，外部自动化执行引擎在消息收发或对话生命周期事件发生时回调携带的数据。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ConversationEventCallbackDto {

    /** 平台类型（如 whatsapp / wechat / telegram） */
    private String platformType;

    /** 账号 ID */
    private String accountId;

    /** 客户 ID */
    private String customerId;

    /** 会话 ID（可空，新会话首次消息时可能尚未建立） */
    private String conversationId;

    /** 消息类型（如 text / image / voice / video） */
    private String messageType;

    /** 消息方向（INBOUND / OUTBOUND） */
    private String direction;

    /** 消息内容（文本内容或描述） */
    private String content;

    /** 媒体对象 key（可空，文本消息无此字段） */
    private String mediaObjectKey;

    /** 平台消息 ID */
    private String platformMessageId;

    /** 消息发送时间 */
    private LocalDateTime sentAt;
}
