/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesRankingEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.hibernate.annotations.GenericGenerator;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 销售业绩排名实体。
 * <p>
 * 周期内按指标类型与目标对象类型 (个人/团队) 计算业绩排名, 同时记录达成值、目标值与达成率,
 * 用于排行榜展示与业绩对比分析。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_sales_ranking", schema = "scrm", indexes = {
        @Index(name = "idx_sales_ranking_period",
                columnList = "period_type,period_start,period_end,metric_type,target_type"),
        @Index(name = "idx_sales_ranking_target", columnList = "target_type,target_id"),
        @Index(name = "idx_sales_ranking_date", columnList = "ranking_date")
})
@Data
public class ScrmSalesRankingEntity {

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

    /** 周期类型: WEEKLY / MONTHLY / QUARTERLY / YEARLY */
    @Column(name = "period_type", nullable = false, length = 10)
    private String periodType;

    /** 周期开始日期 */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** 周期结束日期 */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 目标对象类型: INDIVIDUAL 个人 / TEAM 团队 */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    /** 目标对象 ID (userId / teamId) */
    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;

    /** 目标对象名称 (冗余便于展示) */
    @Column(name = "target_name_ref", length = 200)
    private String targetNameRef;

    /** 指标类型: REVENUE / NEW_CUSTOMERS / CONVERSIONS / FOLLOW_UPS / OPPORTUNITIES / CALLS */
    @Column(name = "metric_type", nullable = false, length = 30)
    private String metricType;

    /** 达成值 */
    @Column(name = "achieved_value", nullable = false)
    private Double achievedValue;

    /** 目标值 */
    @Column(name = "target_value", nullable = false)
    private Double targetValue;

    /** 达成率 (%) */
    @Column(name = "achievement_rate", nullable = false)
    private Double achievementRate;

    /** 排名 (从 1 开始, 1 为第一名) */
    @Column(name = "rank", nullable = false)
    private Integer rank;

    /** 排名日期 */
    @Column(name = "ranking_date", nullable = false)
    private LocalDate rankingDate;
}
