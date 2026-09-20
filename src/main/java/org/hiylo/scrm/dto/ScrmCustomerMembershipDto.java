/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerMembershipDto.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 客户会员 DTO。
 * <p>
 * 用于客户会员档案的查询返回与状态更新。会员记录通过 {@link ScrmMembershipEnrollDto} 创建,
 * 创建时由服务端生成卡号并设置初始等级。此 DTO 承载查询返回与状态流转场景。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCustomerMembershipDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 等级 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tierId;

    /** 等级名称快照 (可空) */
    @Size(max = 200, message = "等级名称长度不能超过 200")
    private String tierName;

    /** 当前等级序号 */
    private Integer tierLevel;

    /** 等级编码快照 (可空) */
    @Size(max = 50, message = "等级编码长度不能超过 50")
    private String tierCode;

    /** 会员卡号 (唯一) */
    @NotBlank(message = "会员卡号不能为空")
    @Size(max = 100, message = "会员卡号长度不能超过 100")
    private String memberCardNo;

    /** 会员卡类型: STANDARD / VIP / BLACK_GOLD / DIAMOND / CUSTOM */
    @Size(max = 30, message = "会员卡类型长度不能超过 30")
    private String memberCardType;

    /** 会员状态: ACTIVE / FROZEN / EXPIRED / CANCELLED / PENDING */
    @Size(max = 20, message = "会员状态长度不能超过 20")
    private String membershipStatus;

    /** 加入日期 */
    private LocalDate joinDate;

    /** 当前等级日期 */
    private LocalDate currentTierDate;

    /** 等级到期日 (可空) */
    private LocalDate tierExpiryDate;

    /** 累计消费 */
    private Double totalSpend;

    /** 累计订单 */
    private Integer totalOrders;

    /** 累计积分 */
    private Integer totalPoints;

    /** 可用积分 */
    private Integer availablePoints;

    /** 周期内消费 */
    private Double spendInPeriod;

    /** 周期内订单 */
    private Integer ordersInPeriod;

    /** 周期开始 (可空) */
    private LocalDate periodStartDate;

    /** 周期结束 (可空) */
    private LocalDate periodEndDate;

    /** 距下一等级还需消费 */
    private Double nextTierSpendNeeded;

    /** 下一等级 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long nextTierId;

    /** 下一等级名称 (可空) */
    @Size(max = 200, message = "下一等级名称长度不能超过 200")
    private String nextTierName;

    /** 升级进度 0-1 */
    private Double upgradeProgress;

    /** 降级风险 */
    private Boolean downgradeRisk;

    /** 即将过期积分 */
    private Integer pointsToExpire;

    /** 积分过期日 (可空) */
    private LocalDate pointsExpiryDate;

    /** 权益使用次数 */
    private Integer benefitsUsedCount;

    /** 权益节省金额 */
    private Double benefitsSavedAmount;

    /** 最后活动日 (可空) */
    private LocalDate lastActivityDate;

    /** 最后升级日 (可空) */
    private LocalDate lastUpgradeDate;

    /** 最后降级日 (可空) */
    private LocalDate lastDowngradeDate;

    /** JSON 升降级历史 (可空): [{date,fromTier,toTier,reason,type}] */
    private String upgradeHistory;

    /** 会员推荐码 (可空) */
    @Size(max = 100, message = "会员推荐码长度不能超过 100")
    private String referralCode;

    /** 推荐人 (可空) */
    @Size(max = 100, message = "推荐人长度不能超过 100")
    private String referredBy;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

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
