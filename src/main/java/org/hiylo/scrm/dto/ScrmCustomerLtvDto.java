/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLtvDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 客户 LTV 计算结果 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCustomerLtvDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 使用的 LTV 模型 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 历史 LTV */
    private Double historicalLtv;

    /** 预测 LTV */
    private Double predictedLtv;

    /** 累计收入 */
    private Double totalRevenue;

    /** 累计订单数 */
    private Integer totalOrders;

    /** 平均订单价值 */
    private Double avgOrderValue;

    /** 平均购买频次 (次/月) */
    private Double avgPurchaseFrequency;

    /** 平均购买间隔天数 */
    private Double avgPurchaseIntervalDays;

    /** 客户年龄天数 */
    private Integer customerAgeDays;

    /** 最后购买日 */
    private LocalDate lastPurchaseDate;

    /** 距上次购买天数 */
    private Integer daysSinceLastPurchase;

    /** 获客成本 */
    private Double acquisitionCost;

    /** 预计收入 */
    private Double projectedRevenue;

    /** 预计订单数 */
    private Integer projectedOrders;

    /** 客户盈利性 */
    private Double customerProfitability;

    /** 投资回报率 */
    private Double roi;

    /** 价值层级: VIP / HIGH / MEDIUM / LOW / AT_RISK */
    private String valueTier;

    /** 流失概率 (0-1) */
    private Double churnProbability;

    /** 预测流失日期 */
    private LocalDate predictedChurnDate;

    /** 增长潜力: HIGH / MEDIUM / LOW / NONE */
    private String growthPotential;

    /** 置信度 (0-1) */
    private Double confidenceScore;

    /** LTV 趋势: INCREASING / STABLE / DECREASING */
    private String ltvTrend;

    /** 趋势变化百分比 */
    private Double trendChangePercent;

    /** 计算时间 */
    private LocalDateTime calculatedAt;

    /** 上次 LTV */
    private Double previousLtv;

    /** 附加数据 (JSON) */
    private String metadata;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
