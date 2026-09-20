/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignAnalysisDto.java
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
 * 对应 {@code ScrmCampaignAnalysisEntity} 的业务字段, 创建/更新接口入参。
 * channels/segments/products/tags 为逗号分隔字符串。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCampaignAnalysisDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 营销活动 ID */
    @NotNull(message = "营销活动 ID 不能为空")
    private Long campaignId;

    /** 活动名称 */
    @NotBlank(message = "活动名称不能为空")
    @Size(max = 200, message = "活动名称长度不能超过 200")
    private String campaignName;

    /** 活动编码 (可空) */
    @Size(max = 100, message = "活动编码长度不能超过 100")
    private String campaignCode;

    /** 活动类型 */
    @NotBlank(message = "活动类型不能为空")
    @Pattern(regexp = "PROMOTION|DISCOUNT|COUPON|POINTS|GIVEAWAY|CONTENT|REFERRAL|LOYALTY|FLASH_SALE|GROUP_BUY|OTHER",
            message = "活动类型仅支持 PROMOTION/DISCOUNT/COUPON/POINTS/GIVEAWAY/CONTENT/REFERRAL/LOYALTY/FLASH_SALE/GROUP_BUY/OTHER")
    private String campaignType;

    /** 活动目标 (可空) */
    @Size(max = 200, message = "活动目标长度不能超过 200")
    private String objective;

    /** 活动开始日期 */
    @NotNull(message = "活动开始日期不能为空")
    private LocalDate startDate;

    /** 活动结束日期 */
    @NotNull(message = "活动结束日期不能为空")
    private LocalDate endDate;

    /** 活动持续天数 */
    private Integer durationDays;

    /** 状态: PLANNED/RUNNING/COMPLETED/CANCELLED/PAUSED */
    @Pattern(regexp = "PLANNED|RUNNING|COMPLETED|CANCELLED|PAUSED",
            message = "状态仅支持 PLANNED/RUNNING/COMPLETED/CANCELLED/PAUSED")
    private String status;

    /** 预算 */
    private Double budget;

    /** 实际成本 */
    private Double actualCost;

    /** 渠道 (逗号分隔) */
    @Size(max = 500, message = "渠道长度不能超过 500")
    private String channels;

    /** 分群 (逗号分隔) */
    @Size(max = 500, message = "分群长度不能超过 500")
    private String segments;

    /** 商品 (逗号分隔) */
    @Size(max = 500, message = "商品长度不能超过 500")
    private String products;

    /** 触达数 */
    private Integer reachCount;

    /** 展示数 */
    private Integer impressionCount;

    /** 点击数 */
    private Integer clickCount;

    /** 点击率 */
    private Double clickThroughRate;

    /** 注册数 */
    private Integer registrationCount;

    /** 参与数 */
    private Integer participationCount;

    /** 转化数 */
    private Integer conversionCount;

    /** 转化率 */
    private Double conversionRate;

    /** 收入 */
    private Double revenue;

    /** 利润 */
    private Double profit;

    /** ROI */
    private Double roi;

    /** ROAS */
    private Double roas;

    /** CPC */
    private Double cpc;

    /** CPA */
    private Double cpa;

    /** CPM */
    private Double cpm;

    /** CAC 客户获取成本 */
    private Double cac;

    /** LTV 客户终身价值 */
    private Double ltv;

    /** LTV/CAC 比率 */
    private Double ltvCacRatio;

    /** 回收期 */
    private Double paybackPeriod;

    /** 平均订单价值 */
    private Double averageOrderValue;

    /** 人均订单数 */
    private Double ordersPerCustomer;

    /** 新客户数 */
    private Integer newCustomerCount;

    /** 老客户数 */
    private Integer repeatCustomerCount;

    /** 新客户占比 */
    private Double newCustomerRate;

    /** 留存率 */
    private Double retentionRate;

    /** NPS 净推荐值 */
    private Integer npsScore;

    /** CSAT 客户满意度 */
    private Double csatScore;

    /** 亮点 */
    @Size(max = 2000, message = "亮点长度不能超过 2000")
    private String highlights;

    /** 问题 */
    @Size(max = 2000, message = "问题长度不能超过 2000")
    private String issues;

    /** 建议 */
    @Size(max = 2000, message = "建议长度不能超过 2000")
    private String recommendations;

    /** 分析人 */
    @Size(max = 100, message = "分析人长度不能超过 100")
    private String analyzedBy;

    /** 分析时间 */
    private LocalDateTime analyzedAt;

    /** 审批人 */
    @Size(max = 100, message = "审批人长度不能超过 100")
    private String approvedBy;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    /** 是否已审批 */
    private Boolean isApproved;

    /** 标签 (逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
