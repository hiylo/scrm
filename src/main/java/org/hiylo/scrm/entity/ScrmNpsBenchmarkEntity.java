/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNpsBenchmarkEntity.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM NPS 基准实体。
 * <p>
 * 汇总某周期 (月 / 季 / 年) 内的 NPS / CSAT / CES 统计指标。{@link #periodType} 标注周期类型
 * (MONTHLY/QUARTERLY/YEARLY), {@link #periodStart} / {@link #periodEnd} 标注周期范围。
 * </p>
 * <p>
 * NPS 计算口径: 推荐者 (9-10) - 贬损者 (0-6), 被动者 (7-8) 不计入差值但计入分母;
 * {@link #promoterPercent} / {@link #passivePercent} / {@link #detractorPercent} 为各自占比;
 * {@link #npsScore} 取值范围 -100 到 100。{@link #benchmarkIndustry} / {@link #benchmarkScore}
 * 为外部行业基准 (可空, 用于横向对比)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_nps_benchmark", schema = "scrm", indexes = {
        @Index(name = "idx_nps_benchmark_period_type", columnList = "period_type"),
        @Index(name = "idx_nps_benchmark_period_start", columnList = "period_start"),
        @Index(name = "idx_nps_benchmark_period_end", columnList = "period_end"),
        @Index(name = "idx_nps_benchmark_generated", columnList = "generated_at")
})
@Data
public class ScrmNpsBenchmarkEntity {

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

    /** 周期类型: MONTHLY / QUARTERLY / YEARLY */
    @Column(name = "period_type", nullable = false, length = 20)
    private String periodType;

    /** 周期开始日期 */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** 周期结束日期 */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** 总回复数 (默认 0) */
    @Column(name = "total_responses")
    private Integer totalResponses;

    /** 推荐者数 (9-10, 默认 0) */
    @Column(name = "promoters")
    private Integer promoters;

    /** 被动者数 (7-8, 默认 0) */
    @Column(name = "passives")
    private Integer passives;

    /** 贬损者数 (0-6, 默认 0) */
    @Column(name = "detractors")
    private Integer detractors;

    /** NPS 得分 -100 到 100 (默认 0) */
    @Column(name = "nps_score")
    private Integer npsScore;

    /** 推荐者占比 (默认 0) */
    @Column(name = "promoter_percent")
    private Double promoterPercent;

    /** 被动者占比 (默认 0) */
    @Column(name = "passive_percent")
    private Double passivePercent;

    /** 贬损者占比 (默认 0) */
    @Column(name = "detractor_percent")
    private Double detractorPercent;

    /** 平均 CSAT 得分 (默认 0) */
    @Column(name = "avg_csat_score")
    private Double avgCsatScore;

    /** 平均 CES 得分 (默认 0) */
    @Column(name = "avg_ces_score")
    private Double avgCesScore;

    /** 回复率 (默认 0) */
    @Column(name = "response_rate")
    private Double responseRate;

    /** 行业基准 (可空) */
    @Column(name = "benchmark_industry", length = 100)
    private String benchmarkIndustry;

    /** 行业 NPS 基准 (可空) */
    @Column(name = "benchmark_score")
    private Integer benchmarkScore;

    /** 生成时间 */
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
