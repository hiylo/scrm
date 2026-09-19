/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementScoreEntity.java
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

/**
 * SCRM 客户互动评分实体。
 * <p>
 * 每个客户在每个账号下拥有唯一评分记录 (customer_id 唯一)。{@link #totalScore}
 * 为累计原始得分 (不计衰减), {@link #currentScore} 为应用衰减后的当前有效分。
 * {@link #engagementLevel} (INACTIVE/LOW/MEDIUM/HIGH/VERY_HIGH) 由 {@link #currentScore}
 * 经 {@code determineLevel} 映射得出, {@link #scoreTrend} (UP/STABLE/DOWN) 与
 * {@link #trendChangePercent} 记录与上次计算的变化趋势。
 * </p>
 * <p>
 * 周期得分 {@link #weeklyScore} / {@link #monthlyScore} / {@link #quarterlyScore} /
 * {@link #yearlyScore} 为对应时间窗口内的衰减后得分合计。{@link #streakDays} 记录连续互动天数
 * (有事件发生则 +1, 中断则重置为 0)。{@link #metadata} 以 JSON 承载各行为类型得分明细。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_engagement_score", schema = "scrm", indexes = {
        @Index(name = "idx_engagement_score_customer", columnList = "customer_id"),
        @Index(name = "idx_engagement_score_level", columnList = "engagement_level"),
        @Index(name = "idx_engagement_score_current", columnList = "current_score")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_engagement_score_customer",
                columnNames = {"customer_id"})
})
@Data
public class ScrmEngagementScoreEntity {

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

    /** 冗余客户名称 */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 总分 (累计原始得分, 不计衰减) */
    @Column(name = "total_score", nullable = false)
    private Double totalScore;

    /** 当前分 (应用衰减后的有效分) */
    @Column(name = "current_score", nullable = false)
    private Double currentScore;

    /** 活跃等级: INACTIVE/LOW/MEDIUM/HIGH/VERY_HIGH */
    @Column(name = "engagement_level", nullable = false, length = 20)
    private String engagementLevel;

    /** 评分趋势: UP/STABLE/DOWN (可空) */
    @Column(name = "score_trend", length = 20)
    private String scoreTrend;

    /** 趋势变化百分比 */
    @Column(name = "trend_change_percent")
    private Double trendChangePercent;

    /** 最后互动时间 (可空) */
    @Column(name = "last_event_at")
    private LocalDateTime lastEventAt;

    /** 最后计算时间 */
    @Column(name = "last_calculated_at", nullable = false)
    private LocalDateTime lastCalculatedAt;

    /** 连续互动天数 */
    @Column(name = "streak_days")
    private Integer streakDays;

    /** 总互动次数 */
    @Column(name = "total_events")
    private Integer totalEvents;

    /** 周得分 (近 7 天衰减后合计) */
    @Column(name = "weekly_score")
    private Double weeklyScore;

    /** 月得分 (近 30 天衰减后合计) */
    @Column(name = "monthly_score")
    private Double monthlyScore;

    /** 季度得分 (近 90 天衰减后合计) */
    @Column(name = "quarterly_score")
    private Double quarterlyScore;

    /** 年得分 (近 365 天衰减后合计) */
    @Column(name = "yearly_score")
    private Double yearlyScore;

    /** 等级更新时间 (可空) */
    @Column(name = "level_updated_at")
    private LocalDateTime levelUpdatedAt;

    /** JSON 各行为得分明细 (可空) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
