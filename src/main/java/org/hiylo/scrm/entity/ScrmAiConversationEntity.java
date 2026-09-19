/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiConversationEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM AI 对话记录实体。
 * <p>
 * 记录单次 AI 辅助对话全过程: 用户消息 ({@link #userMessage}) → 意图识别
 * ({@link #detectedIntent} / {@link #intentConfidence} 0-1) → 情感分析
 * ({@link #sentiment}: POSITIVE/NEUTRAL/NEGATIVE/ANGRY/HAPPY / {@link #sentimentScore}
 * -1 到 1) → 推荐回复 ({@link #recommendedReplies} JSON 数组) → AI 生成回复
 * ({@link #aiResponse}) → 用户反馈 ({@link #feedback}: GOOD/BAD/NONE)。
 * {@link #responseTimeMs} 记录响应耗时, {@link #configId} 标记使用的 AI 配置。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_ai_conversation", schema = "scrm", indexes = {
        @Index(name = "idx_ai_conversation_customer", columnList = "customer_id"),
        @Index(name = "idx_ai_conversation_session", columnList = "conversation_id"),
        @Index(name = "idx_ai_conversation_intent", columnList = "detected_intent"),
        @Index(name = "idx_ai_conversation_sentiment", columnList = "sentiment"),
        @Index(name = "idx_ai_conversation_created", columnList = "created_at")
})
@Data
public class ScrmAiConversationEntity {

    // ==================== 公共字段 ====================

    /** 主键 ID（Snowflake 雪花算法生成） */
    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 创建时间 */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /** 乐观锁版本号（并发更新保护, 后写入者触发 OptimisticLockException） */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 持久化前回调: 自动填充创建/更新时间与版本号初值
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
        if (version == null) {
            version = 0L;
        }
    }

    /**
     * 更新前回调: 自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ==================== 业务字段 ====================

    /** 客户 ID */
    @Column(name = "customer_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (冗余, 便于列表展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 关联会话 ID (可空) */
    @Column(name = "conversation_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 用户消息 */
    @Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
    private String userMessage;

    /** 识别意图 (可空) */
    @Column(name = "detected_intent", length = 100)
    private String detectedIntent;

    /** 意图置信度 (0-1, 默认 0) */
    @Column(name = "intent_confidence")
    private Double intentConfidence;

    /** 情感: POSITIVE / NEUTRAL / NEGATIVE / ANGRY / HAPPY */
    @Column(name = "sentiment", length = 20)
    private String sentiment;

    /** 情感分 (-1 到 1, 默认 0) */
    @Column(name = "sentiment_score")
    private Double sentimentScore;

    /** 推荐回复 (JSON 数组, 可空) */
    @Column(name = "recommended_replies", columnDefinition = "TEXT")
    private String recommendedReplies;

    /** 实际使用的回复 (可空) */
    @Column(name = "used_reply", length = 2000)
    private String usedReply;

    /** AI 生成回复 (可空) */
    @Column(name = "ai_response", length = 2000)
    private String aiResponse;

    /** 响应耗时 (毫秒, 可空) */
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    /** 使用的 AI 配置 ID (可空) */
    @Column(name = "config_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 用户反馈: GOOD / BAD / NONE */
    @Column(name = "feedback", length = 20)
    private String feedback;

    /** 对话发生时间 (业务时间, 默认当前时间) */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
