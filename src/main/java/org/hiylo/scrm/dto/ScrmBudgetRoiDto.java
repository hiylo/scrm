/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetRoiDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 营销预算 ROI DTO。
 * <p>
 * 对应 {@code ScrmBudgetRoiEntity} 的业务字段, 用于 ROI 计算结果返回与列表查询。
 * breakdown 为 JSON 文本: {@code [{channel,spend,revenue,roi}]}。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmBudgetRoiDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 预算方案 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;

    /** 预算分配 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long allocationId;

    /** 关联营销活动 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 关联营销活动名称 (可空) */
    private String campaignName;

    /** 周期 (可空, 格式 yyyy-MM) */
    private String period;

    /** 总支出 */
    private Double totalSpend;

    /** 总收入 */
    private Double totalRevenue;

    /** 总利润 */
    private Double totalProfit;

    /** 收入 ROI = 收入 / 支出 */
    private Double revenueRoi;

    /** 利润 ROI = 利润 / 支出 */
    private Double profitRoi;

    /** ROAS = 收入 / 广告支出 */
    private Double roas;

    /** 每行动成本 (CPA) */
    private Double cpa;

    /** 每点击成本 (CPC) */
    private Double cpc;

    /** 千次展示成本 (CPM) */
    private Double cpm;

    /** 获客成本 (CAC) */
    private Double cac;

    /** 客户 LTV */
    private Double ltv;

    /** LTV / CAC 比率 */
    private Double ltvCacRatio;

    /** 转化数 */
    private Integer conversions;

    /** 点击数 */
    private Integer clicks;

    /** 展示数 */
    private Integer impressions;

    /** 转化率 = 转化数 / 点击数 */
    private Double conversionRate;

    /** 点击率 = 点击数 / 展示数 */
    private Double clickRate;

    /** 回本月数 (可空) */
    private Integer paybackPeriodMonths;

    /** 渠道/产品拆分 JSON (可空): [{channel,spend,revenue,roi}] */
    private String breakdown;

    /** 计算时间 */
    private LocalDateTime calculatedAt;

    /** 备注 (可空) */
    private String notes;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
