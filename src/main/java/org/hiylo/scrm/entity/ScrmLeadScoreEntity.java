/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadScoreEntity.java
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
 * SCRM 销售线索评分实体。
 * <p>
 * 记录单个客户在某个评分模型下的得分结果: {@link #totalScore} 总分 / {@link #maxScore} 满分 /
 * {@link #scorePercent} 得分百分比, {@link #grade} (A_PLUS / SUPER_HOT / HOT / WARM / COLD /
 * DEAD / A/B/C/D/E) 等级, {@link #dimensionScores} 各维度得分明细 (JSON: [{dimension, score,
 * maxScore, details}])。
 * </p>
 * <p>
 * 转化相关: {@link #conversionProbability} 转化概率 (0-1), {@link #predictedValue} 预测价值,
 * {@link #isHotLead} / {@link #isQualified} 标记热线索 / 合格线索。
 * </p>
 * <p>
 * 趋势: {@link #scoreTrend} (UP/STABLE/DOWN) 与 {@link #trendChange} / {@link #previousScore}
 * 跟踪与上次计算的变化。分配与转化: {@link #assignedTo} / {@link #assignedAt} / {@link #contactedAt} /
 * {@link #convertedAt} / {@link #isConverted} / {@link #conversionValue}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_lead_score", schema = "scrm", indexes = {
        @Index(name = "idx_lead_score_customer", columnList = "customer_id"),
        @Index(name = "idx_lead_score_model", columnList = "model_id"),
        @Index(name = "idx_lead_score_grade", columnList = "grade"),
        @Index(name = "idx_lead_score_hot", columnList = "is_hot_lead"),
        @Index(name = "idx_lead_score_qualified", columnList = "is_qualified"),
        @Index(name = "idx_lead_score_converted", columnList = "is_converted"),
        @Index(name = "idx_lead_score_assigned", columnList = "assigned_to"),
        @Index(name = "idx_lead_score_total", columnList = "total_score")
})
@Data
public class ScrmLeadScoreEntity {

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
        if (lastCalculatedAt == null) {
            lastCalculatedAt = now;
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

    /** 评分模型 ID */
    @Column(name = "model_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 总分 */
    @Column(name = "total_score", nullable = false)
    private Double totalScore;

    /** 满分 */
    @Column(name = "max_score")
    private Double maxScore;

    /** 得分百分比 */
    @Column(name = "score_percent")
    private Double scorePercent;

    /** 等级: A_PLUS / SUPER_HOT / HOT / WARM / COLD / DEAD / A / B / C / D / E */
    @Column(name = "grade", length = 20)
    private String grade;

    /** 等级标签 (可空, 用于展示) */
    @Column(name = "grade_label", length = 50)
    private String gradeLabel;

    /** 各维度得分 JSON: [{dimension, score, maxScore, details}] */
    @Column(name = "dimension_scores", columnDefinition = "TEXT")
    private String dimensionScores;

    /** 转化概率 (0-1) */
    @Column(name = "conversion_probability")
    private Double conversionProbability;

    /** 预测价值 */
    @Column(name = "predicted_value")
    private Double predictedValue;

    /** 是否热线索 (默认 false) */
    @Column(name = "is_hot_lead", nullable = false)
    private Boolean isHotLead;

    /** 是否合格线索 (默认 false) */
    @Column(name = "is_qualified", nullable = false)
    private Boolean isQualified;

    /** 最近计算时间 */
    @Column(name = "last_calculated_at", nullable = false)
    private LocalDateTime lastCalculatedAt;

    /** 评分趋势: UP / STABLE / DOWN (可空) */
    @Column(name = "score_trend", length = 20)
    private String scoreTrend;

    /** 趋势变化 (本次 - 上次) */
    @Column(name = "trend_change")
    private Double trendChange;

    /** 上次得分 */
    @Column(name = "previous_score")
    private Double previousScore;

    /** 分配给 (负责人用户标识, 可空) */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    /** 分配时间 (可空) */
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** 最近联系时间 (可空) */
    @Column(name = "contacted_at")
    private LocalDateTime contactedAt;

    /** 转化时间 (可空) */
    @Column(name = "converted_at")
    private LocalDateTime convertedAt;

    /** 是否已转化 (默认 false) */
    @Column(name = "is_converted", nullable = false)
    private Boolean isConverted;

    /** 转化价值 */
    @Column(name = "conversion_value")
    private Double conversionValue;
}
