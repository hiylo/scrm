/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLtvEntity.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 客户 LTV 计算结果实体。
 * <p>
 * 记录单个客户在指定 LTV 模型下的计算结果: 历史 LTV、预测 LTV、累计收入与订单、
 * 平均订单价值与购买频次、客户年龄与距上次购买天数、获客成本与预计收入、客户盈利性与 ROI、
 * 价值层级、流失概率与预测流失日期、增长潜力、置信度、LTV 趋势等。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Entity
@Table(name = "scrm_customer_ltv", schema = "scrm", indexes = {
        @Index(name = "idx_ltv_customer_customer", columnList = "customer_id"),
        @Index(name = "idx_ltv_customer_model", columnList = "model_id"),
        @Index(name = "idx_ltv_customer_tier", columnList = "value_tier"),
        @Index(name = "idx_ltv_customer_ltv", columnList = "predicted_ltv"),
        @Index(name = "idx_ltv_customer_churn", columnList = "churn_probability")
})
@Data
public class ScrmCustomerLtvEntity {

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

    /** 客户名称 (冗余便于展示) */
    @Column(name = "customer_name", length = 200)
    private String customerName;

    /** 使用的 LTV 模型 ID */
    @Column(name = "model_id", nullable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 历史 LTV (基于已发生收入计算) */
    @Column(name = "historical_ltv", nullable = false)
    private Double historicalLtv;

    /** 预测 LTV (基于模型预测的未来价值) */
    @Column(name = "predicted_ltv", nullable = false)
    private Double predictedLtv;

    /** 累计收入 */
    @Column(name = "total_revenue")
    private Double totalRevenue;

    /** 累计订单数 */
    @Column(name = "total_orders")
    private Integer totalOrders;

    /** 平均订单价值 */
    @Column(name = "avg_order_value")
    private Double avgOrderValue;

    /** 平均购买频次 (次/月) */
    @Column(name = "avg_purchase_frequency")
    private Double avgPurchaseFrequency;

    /** 平均购买间隔天数 */
    @Column(name = "avg_purchase_interval_days")
    private Double avgPurchaseIntervalDays;

    /** 客户年龄天数 (自客户创建至今) */
    @Column(name = "customer_age_days")
    private Integer customerAgeDays;

    /** 最后购买日 */
    @Column(name = "last_purchase_date")
    private LocalDate lastPurchaseDate;

    /** 距上次购买天数 */
    @Column(name = "days_since_last_purchase")
    private Integer daysSinceLastPurchase;

    /** 获客成本 */
    @Column(name = "acquisition_cost")
    private Double acquisitionCost;

    /** 预计收入 (预测期内的预计收入) */
    @Column(name = "projected_revenue")
    private Double projectedRevenue;

    /** 预计订单数 (预测期内的预计订单数) */
    @Column(name = "projected_orders")
    private Integer projectedOrders;

    /** 客户盈利性 (预测 LTV - 获客成本) */
    @Column(name = "customer_profitability")
    private Double customerProfitability;

    /** 投资回报率 ((预测 LTV - 获客成本) / 获客成本) */
    @Column(name = "roi")
    private Double roi;

    /** 价值层级: VIP / HIGH / MEDIUM / LOW / AT_RISK */
    @Column(name = "value_tier", length = 20)
    private String valueTier;

    /** 流失概率 (0-1) */
    @Column(name = "churn_probability")
    private Double churnProbability;

    /** 预测流失日期 */
    @Column(name = "predicted_churn_date")
    private LocalDate predictedChurnDate;

    /** 增长潜力: HIGH / MEDIUM / LOW / NONE */
    @Column(name = "growth_potential", length = 20)
    private String growthPotential;

    /** 置信度 (0-1, 基于数据量与模型拟合度) */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    /** LTV 趋势: INCREASING / STABLE / DECREASING */
    @Column(name = "ltv_trend", length = 20)
    private String ltvTrend;

    /** 趋势变化百分比 */
    @Column(name = "trend_change_percent")
    private Double trendChangePercent;

    /** 计算时间 */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    /** 上次 LTV (用于趋势对比) */
    @Column(name = "previous_ltv")
    private Double previousLtv;

    /** 附加数据 (JSON) */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
