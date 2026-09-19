/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiConversationDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM AI 对话记录 DTO。
 * <p>
 * 用于对话记录查询返回与人工创建场景。sentiment 仅支持
 * POSITIVE/NEUTRAL/NEGATIVE/ANGRY/HAPPY, feedback 仅支持 GOOD/BAD/NONE。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAiConversationDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 关联会话 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 用户消息 */
    @Size(max = 65535, message = "用户消息长度超出限制")
    private String userMessage;

    /** 识别意图 */
    @Size(max = 100, message = "识别意图长度不能超过 100")
    private String detectedIntent;

    /** 意图置信度 (0-1) */
    private Double intentConfidence;

    /** 情感: POSITIVE / NEUTRAL / NEGATIVE / ANGRY / HAPPY */
    @Size(max = 20, message = "情感长度不能超过 20")
    private String sentiment;

    /** 情感分 (-1 到 1) */
    private Double sentimentScore;

    /** 推荐回复 (JSON 数组) */
    private String recommendedReplies;

    /** 实际使用的回复 */
    @Size(max = 2000, message = "实际使用的回复长度不能超过 2000")
    private String usedReply;

    /** AI 生成回复 */
    @Size(max = 2000, message = "AI 生成回复长度不能超过 2000")
    private String aiResponse;

    /** 响应耗时 (毫秒) */
    private Integer responseTimeMs;

    /** 使用的 AI 配置 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 用户反馈: GOOD / BAD / NONE */
    @Size(max = 20, message = "用户反馈长度不能超过 20")
    private String feedback;

    /** 对话发生时间 */
    private LocalDateTime createdAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;
}
