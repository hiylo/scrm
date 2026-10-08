/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOutboundMessageDto.java
 * Date : 2026/10/07 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 出站消息队列 DTO（执行侧轮询拉取 + ack 回执共用）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmOutboundMessageDto {

    /** 出站队列记录 ID（ack 时回传） */
    private Long id;

    /** 业务消息 ID（scrm_conversation_message.message_id，日志/排查用） */
    private String businessMessageId;

    /** 会话 ID */
    private Long conversationId;

    /** 账号 ID */
    private Long accountId;

    /** 平台类型（wechat_personal 等） */
    private String platformType;

    /** 目标平台会话标识（个人微信为 wxid / xxx@chatroom） */
    private String targetPlatformId;

    /** 消息类型（TEXT） */
    private String messageType;

    /** 发送内容 */
    private String content;

    /** 来源（AUTO_REPLY / MANUAL / CAMPAIGN） */
    private String source;

    /** 处理状态（PENDING / IN_PROGRESS / SENT / FAILED） */
    private String status;

    /** 失败原因（FAILED 时存在） */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 拉取一批响应体 */
    @Data
    public static class Batch {

        /** 取走的消息（一次最多 20 条） */
        private List<ScrmOutboundMessageDto> items;
    }
}