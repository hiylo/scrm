/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerHealthScoreEntity.java
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
 * SCRM 客户健康度评分实体。
 * <p>
 * 记录单个客户在某个健康度模型下的评分结果: {@link #totalScore} 总分 / {@link #maxScore} 满分 /
 * {@link #scorePercent} 得分百分比, {@link #healthLevel} (CRITICAL / AT_RISK / NEUTRAL /
 * HEALTHY / EXCELLENT) 健康等级, {@link #metricScores} 各指标得分明细 (JSON: [{metricCode,
 * metricName, score, maxScore, details, status}])。
 * </p>
 * <p>
 * 维度分数: {@link #engagementScore} 互动分 / {@link #usageScore} 使用分 /
 * {@link #satisfactionScore} 满意度分 / {@link #paymentScore} 支付分 /
 * {@link #growthScore} 增长分 / {@link #supportScore} 支持分。
 * </p>
 * <p>
 * 趋势: {@link #scoreTrend} (IMPROVING / STABLE / DECLINING / RAPID_DECLINE) 与
 * {@link #trendChange} / {@link #previousScore} 跟踪与上次计算的变化。
 * </p>
 * <p>
 * 风险: {@link #riskLevel} (NONE / LOW / MEDIUM / HIGH / CRITICAL), {@link #riskFactors}
 * 风险因素逗号分隔, {@link #recommendedActions} 建议动作逗号分隔, {@link #isAtRisk} /
 * {@link #isChurnRisk} 标记风险客户 / 流失风险。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_health_score", schema = "scrm", indexes = {
        @Index(name = "idx_customer_health_score_customer", columnList = "customer_id"),
        @Index(name = "idx_customer_health_score_model", columnList = "model_id"),
        @Index(name = "idx_customer_health_score_level", columnList = "health_level"),
        @Index(name = "idx_customer_health_score_risk_level", columnList = "risk_level"),
        @Index(name = "idx_customer_health_score_at_risk", columnList = "is_at_risk"),
        @Index(name = "idx_customer_health_score_churn", columnList = "is_churn_risk"),
        @Index(name = "idx_customer_health_score_total", columnList = "total_score"),
        @Index(name = "idx_customer_health_score_calculated", columnList = "calculated_at")
})
@Data
public class ScrmCustomerHealthScoreEntity {

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
        if (calculatedAt == null) {
            calculatedAt = now;
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

    /** 健康度模型 ID */
    @Column(name = "model_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 总分 */
    @Column(name = "total_score", nullable = false)
    private Double totalScore;

    /** 满分 */
    @Column(name = "max_score")
    private Double maxScore;

    /** 健康等级: CRITICAL / AT_RISK / NEUTRAL / HEALTHY / EXCELLENT (可空) */
    @Column(name = "health_level", length = 20)
    private String healthLevel;

    /** 健康标签 (可空, 用于展示) */
    @Column(name = "health_label", length = 50)
    private String healthLabel;

    /** 得分百分比 */
    @Column(name = "score_percent")
    private Double scorePercent;

    /** 各指标得分 JSON: [{metricCode, metricName, score, maxScore, details, status}] */
    @Column(name = "metric_scores", columnDefinition = "TEXT")
    private String metricScores;

    /** 互动分 */
    @Column(name = "engagement_score")
    private Double engagementScore;

    /** 使用分 */
    @Column(name = "usage_score")
    private Double usageScore;

    /** 满意度分 */
    @Column(name = "satisfaction_score")
    private Double satisfactionScore;

    /** 支付分 */
    @Column(name = "payment_score")
    private Double paymentScore;

    /** 增长分 */
    @Column(name = "growth_score")
    private Double growthScore;

    /** 支持分 */
    @Column(name = "support_score")
    private Double supportScore;

    /** 评分趋势: IMPROVING / STABLE / DECLINING / RAPID_DECLINE (可空) */
    @Column(name = "score_trend", length = 20)
    private String scoreTrend;

    /** 趋势变化 (本次 - 上次) */
    @Column(name = "trend_change")
    private Double trendChange;

    /** 上次得分 */
    @Column(name = "previous_score")
    private Double previousScore;

    /** 风险等级: NONE / LOW / MEDIUM / HIGH / CRITICAL (可空) */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    /** 风险因素 (逗号分隔, 可空) */
    @Column(name = "risk_factors", length = 1000)
    private String riskFactors;

    /** 建议动作 (逗号分隔, 可空) */
    @Column(name = "recommended_actions", length = 1000)
    private String recommendedActions;

    /** 是否风险客户 (默认 false) */
    @Column(name = "is_at_risk", nullable = false)
    private Boolean isAtRisk;

    /** 是否流失风险 (默认 false) */
    @Column(name = "is_churn_risk", nullable = false)
    private Boolean isChurnRisk;

    /** 距上次互动天数 */
    @Column(name = "last_interaction_days")
    private Integer lastInteractionDays;

    /** 距上次订单天数 */
    @Column(name = "days_since_last_order")
    private Integer daysSinceLastOrder;

    /** 待处理工单数 */
    @Column(name = "open_tickets")
    private Integer openTickets;

    /** NPS 评分 (可空) */
    @Column(name = "nps_score")
    private Integer npsScore;

    /** 计算时间 */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    /** 下次计算时间 (可空) */
    @Column(name = "next_calculation_at")
    private LocalDateTime nextCalculationAt;

    /** 备注 (可空) */
    @Column(name = "notes", length = 500)
    private String notes;

    /** 负责人 (可空) */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;
}
