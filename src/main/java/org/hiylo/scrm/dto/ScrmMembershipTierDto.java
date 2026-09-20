/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipTierDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 会员等级 DTO。
 * <p>
 * 用于会员等级的创建、更新与查询返回。创建时必填等级名称、编码、等级序号;
 * 升级阈值缺省 0, 升级规则类型缺省 SPEND, 启用与可见缺省 TRUE, 各类统计字段缺省 0。
 * 更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMembershipTierDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 等级名称 */
    @NotBlank(message = "等级名称不能为空")
    @Size(max = 200, message = "等级名称长度不能超过 200")
    private String tierName;

    /** 等级编码 (唯一) */
    @NotBlank(message = "等级编码不能为空")
    @Size(max = 50, message = "等级编码长度不能超过 50")
    private String tierCode;

    /** 等级序号 (1-10, 数值越大等级越高) */
    @NotNull(message = "等级序号不能为空")
    private Integer tierLevel;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 等级颜色 (可空) */
    @Size(max = 20, message = "等级颜色长度不能超过 20")
    private String tierColor;

    /** 等级图标 (可空) */
    @Size(max = 200, message = "等级图标长度不能超过 200")
    private String tierIcon;

    /** 升级阈值 (累计消费/积分, 默认 0) */
    private Double upgradeThreshold;

    /** 降级阈值 (默认 0) */
    private Double downgradeThreshold;

    /** 等级有效期月 (默认 12) */
    private Integer validityPeriodMonths;

    /** 升级规则类型: SPEND / POINTS / ORDER_COUNT / MANUAL (默认 SPEND) */
    @Size(max = 30, message = "升级规则类型长度不能超过 30")
    private String upgradeRuleType;

    /** 权益摘要 (可空) */
    @Size(max = 1000, message = "权益摘要长度不能超过 1000")
    private String benefitsSummary;

    /** 积分倍数 (默认 1.0) */
    private Double pointMultiplier;

    /** 折扣率 0-1 (默认 1.0) */
    private Double discountRate;

    /** 免费配送 (默认 FALSE) */
    private Boolean freeShipping;

    /** 优先支持 (默认 FALSE) */
    private Boolean prioritySupport;

    /** 专属商品 (默认 FALSE) */
    private Boolean exclusiveProducts;

    /** 生日礼金 (默认 0) */
    private Double birthdayBonus;

    /** 注册赠送积分 (默认 0) */
    private Integer signupBonusPoints;

    /** 每月赠送积分 (默认 0) */
    private Integer monthlyBonusPoints;

    /** 年度赠送积分 (默认 0) */
    private Integer annualBonusPoints;

    /** 升级赠送积分 (默认 0) */
    private Integer tierUpBonusPoints;

    /** 最大兑换比例 (默认 0.5) */
    private Double maxRedeemRate;

    /** 免费退货天数 (默认 7) */
    private Integer freeReturnDays;

    /** JSON 自定义权益 (可空): [{benefitName,benefitType,value,description}] */
    private String customBenefits;

    /** 适用商品 (可空) */
    @Size(max = 500, message = "适用商品长度不能超过 500")
    private String applicableProducts;

    /** 适用渠道 (可空) */
    @Size(max = 500, message = "适用渠道长度不能超过 500")
    private String applicableChannels;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

    /** 是否可见 (默认 TRUE) */
    private Boolean isVisible;

    /** 该等级会员数 (默认 0) */
    private Integer memberCount;

    /** 该等级总消费 (默认 0) */
    private Double totalSpend;

    /** 平均消费 (默认 0) */
    private Double avgSpend;

    /** 排序序号 (默认 0) */
    private Integer sortOrder;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
