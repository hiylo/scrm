/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechRecommendationEntity.java
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
 * SCRM 销售话术推荐记录实体。
 * <p>
 * 描述一次话术推荐的完整轨迹: {@link #recommendedSpeechIds} (逗号分隔) 为推荐话术 ID 列表,
 * {@link #matchContext} (JSON) 承载匹配上下文, {@link #matchScore} / {@link #matchReasons}
 * 记录匹配分数与原因, {@link #selectedSpeechId} / {@link #usedAt} 为用户选择与使用,
 * {@link #feedback} / {@link #feedbackComment} / {@link #outcome} 为反馈与结果,
 * {@link #recommendedBy} / {@link #recommendedByName} 为推荐人。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_speech_recommendation", schema = "scrm", indexes = {
        @Index(name = "idx_speech_rec_customer", columnList = "customer_id"),
        @Index(name = "idx_speech_rec_scenario", columnList = "scenario_id"),
        @Index(name = "idx_speech_rec_time", columnList = "recommended_at"),
        @Index(name = "idx_speech_rec_feedback", columnList = "feedback"),
        @Index(name = "idx_speech_rec_outcome", columnList = "outcome")
})
@Data
public class ScrmSpeechRecommendationEntity {

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

    /** 客户 ID (可空) */
    @Column(name = "customer_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 场景 ID */
    @Column(name = "scenario_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scenarioId;

    /** 场景名称 (冗余, 便于列表展示, 可空) */
    @Column(name = "scenario_name", length = 200)
    private String scenarioName;

    /** 推荐话术 ID 列表 (逗号分隔) */
    @Column(name = "recommended_speech_ids", nullable = false, length = 1000)
    private String recommendedSpeechIds;

    /** 匹配上下文 JSON: {customerStage,channel,productCategory,sentiment,timeOfDay,previousInteraction} */
    @Column(name = "match_context", columnDefinition = "TEXT", nullable = false)
    private String matchContext;

    /** 匹配分数 (默认 0) */
    @Column(name = "match_score")
    private Double matchScore;

    /** 匹配原因 (逗号分隔, 可空) */
    @Column(name = "match_reasons", length = 1000)
    private String matchReasons;

    /** 用户选择的话术 ID (可空) */
    @Column(name = "selected_speech_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long selectedSpeechId;

    /** 使用时间 (可空) */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** 反馈: POSITIVE/NEGATIVE/NEUTRAL (可空) */
    @Column(name = "feedback", length = 20)
    private String feedback;

    /** 反馈注释 (可空) */
    @Column(name = "feedback_comment", length = 500)
    private String feedbackComment;

    /** 使用结果: SUCCESS/PARTIAL/FAILURE/NOT_USED (可空) */
    @Column(name = "outcome", length = 20)
    private String outcome;

    /** 推荐时间 */
    @Column(name = "recommended_at", nullable = false)
    private LocalDateTime recommendedAt;

    /** 推荐人 ID (可空) */
    @Column(name = "recommended_by", length = 100)
    private String recommendedBy;

    /** 推荐人名称 (可空) */
    @Column(name = "recommended_by_name", length = 100)
    private String recommendedByName;
}
