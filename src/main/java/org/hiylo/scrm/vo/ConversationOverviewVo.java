/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ConversationOverviewVo.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话概览 VO, 描述会话总数、消息总量、近 7 天消息量趋势与活跃会话数。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationOverviewVo {

    /** 会话总数 */
    private Long totalConversations;

    /** 消息总量 */
    private Long totalMessages;

    /** 近 7 天消息量趋势 */
    private List<DailyCountVo> recentMessages;

    /** 活跃会话数（近 7 天有消息的会话数） */
    private Long activeConversations;
}
