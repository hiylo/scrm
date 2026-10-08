/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OutboundReplyRequestedEvent.java
 * Date : 2026/10/07 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.springframework.context.ApplicationEvent;

/**
 * 出站回复请求事件。
 * <p>
 * 自动回复引擎 {@link ScrmAutoReplyMatchService#sendReply} 命中规则并渲染出回复内容后
 * 发布此事件；{@link ScrmConversationMessageService} 监听并落一条 direction=OUT 的会话
 * 消息（随后进入平台适配层：企微走开放 API，个人微信走 {@code scrm_outbound_message}
 * 出站队列由执行侧轮询发送）。
 * </p>
 * <p>
 * 用事件解耦：自动回复引擎不依赖会话消息服务，避免服务间循环依赖；单元测试
 * 不发布事件时行为与原来一致（仅记录日志），现有测试不受影响。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public class OutboundReplyRequestedEvent extends ApplicationEvent {

    /** 会话 ID（scrm_conversation.id） */
    private final Long conversationId;

    /** 账号 ID（scrm_account.id） */
    private final Long accountId;

    /** 平台类型（wework / wechat_personal / ...） */
    private final String platformType;

    /** 客户平台 UID（个人微信为对方 wxid；企微为 external_userid） */
    private final String targetPlatformUid;

    /** 回复内容 */
    private final String replyContent;

    /** 来源（AUTO_REPLY） */
    private final String source;

    /**
     * 构造出站回复请求事件。
     *
     * @param source           事件发布源（自动回复引擎）
     * @param conversationId   会话 ID
     * @param accountId        账号 ID
     * @param platformType     平台类型
     * @param targetPlatformUid 目标平台客户 UID
     * @param replyContent     回复内容
     */
    public OutboundReplyRequestedEvent(Object source, Long conversationId, Long accountId,
                                       String platformType, String targetPlatformUid,
                                       String replyContent) {
        super(source);
        this.conversationId = conversationId;
        this.accountId = accountId;
        this.platformType = platformType;
        this.targetPlatformUid = targetPlatformUid;
        this.replyContent = replyContent;
        this.source = "AUTO_REPLY";
    }

    /** 会话 ID */
    public Long getConversationId() {
        return conversationId;
    }

    /** 账号 ID */
    public Long getAccountId() {
        return accountId;
    }

    /** 平台类型 */
    public String getPlatformType() {
        return platformType;
    }

    /** 目标平台客户 UID */
    public String getTargetPlatformUid() {
        return targetPlatformUid;
    }

    /** 回复内容 */
    public String getReplyContent() {
        return replyContent;
    }

    /** 来源 */
    public String getSource() {
        return source;
    }
}