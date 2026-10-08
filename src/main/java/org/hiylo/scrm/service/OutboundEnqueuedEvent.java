/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OutboundEnqueuedEvent.java
 * Date : 2026/10/08 15:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.springframework.context.ApplicationEvent;

/**
 * 出站消息入队事件。
 * <p>
 * {@link ScrmOutboundMessageService#enqueueFromMessage} 成功写入
 * {@code scrm_outbound_message} 后发布；{@code AgentHubWebSocketHandler}
 * 以 {@code AFTER_COMMIT} 监听，对在线设备即时推送 {@code wechat.send_text}
 * 指令（设备离线时消息保持 PENDING，由轮询兜底或设备上线后追补）。
 * </p>
 * <p>
 * 用事件解耦：出站队列服务不依赖 WebSocket 层，推送逻辑挂在事件监听器上，
 * 单元测试可独立验证入队与推送两侧。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public class OutboundEnqueuedEvent extends ApplicationEvent {

    /** 出站队列记录 ID（scrm_outbound_message.id） */
    private final Long outboundId;

    /** 账号 ID（scrm_account.id） */
    private final Long accountId;

    /** 目标平台会话标识（个人微信为对方 wxid / xxx@chatroom） */
    private final String targetPlatformId;

    /** 发送内容（TEXT 消息正文；媒体消息为空） */
    private final String content;

    /** 消息类型（TEXT / IMAGE / FILE 等，AgentHub 据此选择 send_text/send_image/send_file） */
    private final String messageType;

    /** 媒体对象存储 key（IMAGE / FILE 消息，下发指令时生成预签名 URL） */
    private final String mediaObjectKey;

    /** 媒体原始文件名（IMAGE / FILE 消息） */
    private final String mediaFileName;

    /** 平台类型（wechat_personal 等） */
    private final String platformType;

    /** 业务消息 ID（日志/排查用） */
    private final String businessMessageId;

    /**
     * 构造出站入队事件。
     *
     * @param source            事件发布源
     * @param outboundId        出站队列记录 ID
     * @param accountId         账号 ID
     * @param targetPlatformId  目标平台会话标识
     * @param content           发送内容（TEXT 正文；媒体消息为空）
     * @param messageType       消息类型（TEXT / IMAGE / FILE）
     * @param mediaObjectKey    媒体对象存储 key（媒体消息）
     * @param mediaFileName     媒体原始文件名（媒体消息）
     * @param platformType      平台类型
     * @param businessMessageId 业务消息 ID
     */
    public OutboundEnqueuedEvent(Object source, Long outboundId, Long accountId,
                                 String targetPlatformId, String content,
                                 String messageType, String mediaObjectKey, String mediaFileName,
                                 String platformType, String businessMessageId) {
        super(source);
        this.outboundId = outboundId;
        this.accountId = accountId;
        this.targetPlatformId = targetPlatformId;
        this.content = content;
        this.messageType = messageType;
        this.mediaObjectKey = mediaObjectKey;
        this.mediaFileName = mediaFileName;
        this.platformType = platformType;
        this.businessMessageId = businessMessageId;
    }

    /** 出站队列记录 ID */
    public Long getOutboundId() {
        return outboundId;
    }

    /** 账号 ID */
    public Long getAccountId() {
        return accountId;
    }

    /** 目标平台会话标识 */
    public String getTargetPlatformId() {
        return targetPlatformId;
    }

    /** 发送内容 */
    public String getContent() {
        return content;
    }

    /** 消息类型 */
    public String getMessageType() {
        return messageType;
    }

    /** 媒体对象存储 key */
    public String getMediaObjectKey() {
        return mediaObjectKey;
    }

    /** 媒体原始文件名 */
    public String getMediaFileName() {
        return mediaFileName;
    }

    /** 平台类型 */
    public String getPlatformType() {
        return platformType;
    }

    /** 业务消息 ID */
    public String getBusinessMessageId() {
        return businessMessageId;
    }
}
