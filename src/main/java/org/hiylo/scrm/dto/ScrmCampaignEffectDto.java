/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignEffectDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 营销活动效果分析 DTO。
 * <p>
 * 对应 {@code ScrmCampaignEffectEntity} 的业务字段, 创建/更新接口入参。
 * segments/channels/funnel/attributions/benchmarks 为 JSON 文本。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCampaignEffectDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 营销活动 ID */
    @NotNull(message = "营销活动 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long campaignId;

    /** 关联营销活动名称 (可空) */
    @Size(max = 200, message = "营销活动名称长度不能超过 200")
    private String campaignName;

    /** 关联营销活动编码 (可空) */
    @Size(max = 100, message = "营销活动编码长度不能超过 100")
    private String campaignCode;

    /** 活动类型 */
    @NotBlank(message = "活动类型不能为空")
    @Pattern(regexp =
            "EMAIL|SMS|PUSH|WECHAT|WEBINAR|CONTENT|COUPON|DISCOUNT|REFERRAL|AD|SEO|SEM|SOCIAL|OFFLINE|COMPOSITE",
            message = "活动类型仅支持 EMAIL/SMS/PUSH/WECHAT/WEBINAR/CONTENT/COUPON/DISCOUNT/REFERRAL/AD/SEO/SEM/SOCIAL/OFFLINE/COMPOSITE")
    private String campaignType;

    /** 活动目标 */
    @NotBlank(message = "活动目标不能为空")
    @Pattern(regexp = "AWARENESS|ENGAGEMENT|CONVERSION|RETENTION|REACTIVATION|CROSS_SELL|UP_SELL|BRAND|LEAD_GEN",
            message =
                    "活动目标仅支持 AWARENESS/ENGAGEMENT/CONVERSION/RETENTION/REACTIVATION/CROSS_SELL/UP_SELL/BRAND/LEAD_GEN")
    private String objective;

    /** 活动开始日期 */
    @NotNull(message = "活动开始日期不能为空")
    private LocalDate startDate;

    /** 活动结束日期 */
    @NotNull(message = "活动结束日期不能为空")
    private LocalDate endDate;

    /** 状态: PLANNED/LAUNCHED/PAUSED/COMPLETED/CANCELLED */
    @Pattern(regexp = "PLANNED|LAUNCHED|PAUSED|COMPLETED|CANCELLED",
            message = "状态仅支持 PLANNED/LAUNCHED/PAUSED/COMPLETED/CANCELLED")
    private String status;

    /** 目标受众数 */
    private Integer targetAudienceCount;

    /** 触达数 */
    private Integer reachedCount;

    /** 互动数 */
    private Integer engagedCount;

    /** 响应数 */
    private Integer respondedCount;

    /** 点击数 */
    private Integer clickedCount;

    /** 打开数 */
    private Integer openedCount;

    /** 转化数 */
    private Integer convertedCount;

    /** 退回数 */
    private Integer bouncedCount;

    /** 退订数 */
    private Integer unsubscribedCount;

    /** 投诉数 */
    private Integer complainedCount;

    /** 分享数 */
    private Integer sharedCount;

    /** 转发数 */
    private Integer forwardedCount;

    /** 下载数 */
    private Integer downloadedCount;

    /** 注册数 */
    private Integer registeredCount;

    /** 购买数 */
    private Integer purchasedCount;

    /** 收入 */
    private Double revenue;

    /** 成本 */
    private Double cost;

    /** 利润 (查询返回) */
    private Double profit;

    /** ROI% (查询返回) */
    private Double roi;

    /** ROAS (查询返回) */
    private Double roas;

    /** CPA (查询返回) */
    private Double cpa;

    /** CPC (查询返回) */
    private Double cpc;

    /** CPM (查询返回) */
    private Double cpm;

    /** CPL (查询返回) */
    private Double cpl;

    /** CPS (查询返回) */
    private Double cps;

    /** 转化率% (查询返回) */
    private Double cvr;

    /** 点击率% (查询返回) */
    private Double ctr;

    /** 打开率% (查询返回) */
    private Double openRate;

    /** 退回率% (查询返回) */
    private Double bounceRate;

    /** 退订率% (查询返回) */
    private Double unsubscribeRate;

    /** 互动率% (查询返回) */
    private Double engagementRate;

    /** 触达率% (查询返回) */
    private Double reachRate;

    /** 响应率% (查询返回) */
    private Double responseRate;

    /** 转化价值 */
    private Double conversionValue;

    /** 平均订单价值 */
    private Double avgOrderValue;

    /** 平均转化时间 (小时) */
    private Double avgConversionTime;

    /** 客户获取成本 (CAC) */
    private Double customerAcquisitionCost;

    /** 新客户 LTV */
    private Double ltvAcquired;

    /** 回收期 (月) */
    private Double paybackPeriod;

    /** JSON 分群效果 (可空) */
    private String segments;

    /** JSON 渠道效果 (可空) */
    private String channels;

    /** JSON 转化漏斗 (可空) */
    private String funnel;

    /** 归因模型 */
    @Pattern(regexp = "FIRST_TOUCH|LAST_TOUCH|LINEAR|TIME_DECAY|POSITION_BASED|DATA_DRIVEN",
            message = "归因模型仅支持 FIRST_TOUCH/LAST_TOUCH/LINEAR/TIME_DECAY/POSITION_BASED/DATA_DRIVEN")
    private String attributionModel;

    /** JSON 归因数据 (可空) */
    private String attributions;

    /** 对照组大小 */
    private Integer controlGroupSize;

    /** 对照组转化数 */
    private Integer controlConversionCount;

    /** 对照组收入 */
    private Double controlRevenue;

    /** 提升率% */
    private Double uplift;

    /** 增量收入 */
    private Double incrementalRevenue;

    /** 统计显著性 p-value */
    private Double statisticalSignificance;

    /** 置信水平 */
    private Double confidenceLevel;

    /** 是否显著 (查询返回) */
    private Boolean isSignificant;

    /** JSON 基准对比 (可空) */
    private String benchmarks;

    /** 最近计算时间 (查询返回) */
    private LocalDateTime lastCalculatedAt;

    /** 计算状态: PENDING/CALCULATING/COMPLETED/FAILED */
    @Pattern(regexp = "PENDING|CALCULATING|COMPLETED|FAILED",
            message = "计算状态仅支持 PENDING/CALCULATING/COMPLETED/FAILED")
    private String calculationStatus;

    /** 备注 (可空) */
    @Size(max = 2000, message = "备注长度不能超过 2000")
    private String notes;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
