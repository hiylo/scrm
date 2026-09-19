/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocTopicEntity.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 客户之声 (VoC) 主题实体。
 * <p>
 * 承载从客户声音中识别出的主题: 主题名称与编码、父主题 (主题树)、层级、分类、关键词、
 * 声音数与情感分布统计 (正面/负面/中性数与占比)、平均情感分与评分、趋势方向与变化幅度、
 * 时间范围 (最早/最新声音)、紧急度/影响度/综合优先级评分、热点与新兴标识、责任部门与行动标识。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_voc_topic", schema = "scrm", indexes = {
        @Index(name = "idx_voc_topic_parent", columnList = "parent_topic_id"),
        @Index(name = "idx_voc_topic_category", columnList = "category"),
        @Index(name = "idx_voc_topic_hot", columnList = "is_hot_topic"),
        @Index(name = "idx_voc_topic_emerging", columnList = "is_emerging"),
        @Index(name = "idx_voc_topic_priority_score", columnList = "priority_score")
})
@Data
public class ScrmVocTopicEntity {

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

    /** 主题名称 */
    @Column(name = "topic_name", nullable = false, length = 200)
    private String topicName;

    /** 主题编码 (唯一) */
    @Column(name = "topic_code", nullable = false, length = 50, unique = true)
    private String topicCode;

    /** 描述 (可空) */
    @Column(name = "description", length = 500)
    private String description;

    /** 父主题 ID (可空, 顶层主题为空) */
    @Column(name = "parent_topic_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentTopicId;

    /** 主题层级 (默认 1, 顶层为 1) */
    @Column(name = "topic_level", nullable = false)
    private Integer topicLevel;

    /** 主题分类: PRODUCT/SERVICE/PRICE/QUALITY/EXPERIENCE/DELIVERY/SUPPORT/OTHER (可空) */
    @Column(name = "category", length = 100)
    private String category;

    /** 关键词 (可空, 逗号分隔) */
    @Column(name = "keywords", length = 500)
    private String keywords;

    /** 声音数 (默认 0) */
    @Column(name = "voice_count", nullable = false)
    private Integer voiceCount;

    /** 正面数 (默认 0) */
    @Column(name = "positive_count", nullable = false)
    private Integer positiveCount;

    /** 负面数 (默认 0) */
    @Column(name = "negative_count", nullable = false)
    private Integer negativeCount;

    /** 中性数 (默认 0) */
    @Column(name = "neutral_count", nullable = false)
    private Integer neutralCount;

    /** 正面率 (0-100, 默认 0) */
    @Column(name = "positive_rate", nullable = false)
    private Double positiveRate;

    /** 负面率 (0-100, 默认 0) */
    @Column(name = "negative_rate", nullable = false)
    private Double negativeRate;

    /** 平均情感分 (-1.0 ~ 1.0, 默认 0) */
    @Column(name = "avg_sentiment_score", nullable = false)
    private Double avgSentimentScore;

    /** 平均评分 (0-5, 默认 0) */
    @Column(name = "avg_rating", nullable = false)
    private Double avgRating;

    /** 趋势方向: RISING/STABLE/FALLING (默认 STABLE) */
    @Column(name = "trend_direction", nullable = false, length = 20)
    private String trendDirection;

    /** 趋势变化 (%) (默认 0) */
    @Column(name = "trend_percent", nullable = false)
    private Double trendPercent;

    /** 最新声音时间 (可空) */
    @Column(name = "last_voice_at")
    private LocalDateTime lastVoiceAt;

    /** 最早声音时间 (可空) */
    @Column(name = "first_voice_at")
    private LocalDateTime firstVoiceAt;

    /** 紧急度评分 (0-100, 默认 0) */
    @Column(name = "urgency_score", nullable = false)
    private Double urgencyScore;

    /** 影响度评分 (0-100, 默认 0) */
    @Column(name = "impact_score", nullable = false)
    private Double impactScore;

    /** 综合优先级评分 (0-100, 默认 0) */
    @Column(name = "priority_score", nullable = false)
    private Double priorityScore;

    /** 是否热点主题 (默认 FALSE) */
    @Column(name = "is_hot_topic", nullable = false)
    private Boolean isHotTopic;

    /** 是否新兴主题 (默认 FALSE) */
    @Column(name = "is_emerging", nullable = false)
    private Boolean isEmerging;

    /** 责任部门 (可空) */
    @Column(name = "assigned_department", length = 200)
    private String assignedDepartment;

    /** 是否已采取行动 (默认 FALSE) */
    @Column(name = "action_taken", nullable = false)
    private Boolean actionTaken;

    /** 最近分析时间 (可空) */
    @Column(name = "last_analysis_at")
    private LocalDateTime lastAnalysisAt;

    /** 是否启用 (默认 TRUE) */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 创建人 (可空) */
    @Column(name = "created_by", length = 100)
    private String createdBy;
}
