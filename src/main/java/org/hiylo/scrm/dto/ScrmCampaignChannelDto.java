/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignChannelDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 营销活动渠道效果 DTO。
 * <p>
 * 对应 {@code ScrmCampaignChannelEntity} 的业务字段, 创建/更新接口入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCampaignChannelDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分析 ID */
    @NotNull(message = "分析 ID 不能为空")
    private Long analysisId;

    /** 营销活动 ID (可空) */
    private Long campaignId;

    /** 渠道名称 */
    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 100, message = "渠道名称长度不能超过 100")
    private String channelName;

    /** 渠道类型 */
    @NotBlank(message = "渠道类型不能为空")
    @Pattern(regexp = "WECHAT|EMAIL|SMS|PUSH|WEB|APP|DOUYIN|WEIBO|XIAOHONGSHU|OFFLINE|REFERRAL|OTHER",
            message = "渠道类型仅支持 WECHAT/EMAIL/SMS/PUSH/WEB/APP/DOUYIN/WEIBO/XIAOHONGSHU/OFFLINE/REFERRAL/OTHER")
    private String channelType;

    /** 渠道花费 */
    private Double channelCost;

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

    /** CPC */
    private Double cpc;

    /** CPA */
    private Double cpa;

    /** CPM */
    private Double cpm;

    /** 新客户数 */
    private Integer newCustomerCount;

    /** 老客户数 */
    private Integer repeatCustomerCount;

    /** 平均订单价值 */
    private Double averageOrderValue;

    /** 互动率 */
    private Double engagementRate;

    /** 跳出率 */
    private Double bounceRate;

    /** 分享率 */
    private Double shareRate;

    /** 成本占比 */
    private Double costWeight;

    /** 收入占比 */
    private Double revenueWeight;

    /** 效率评分 */
    private Double efficiencyScore;

    /** 是否最优渠道 */
    private Boolean isBestPerformer;

    /** 是否最差渠道 */
    private Boolean isUnderperforming;

    /** 备注 */
    @Size(max = 1000, message = "备注长度不能超过 1000")
    private String notes;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
