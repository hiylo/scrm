/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionPlanDto.java
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
 * SCRM 销售佣金方案 DTO。
 * <p>
 * 对应 {@code ScrmCommissionPlanEntity} 的业务字段, 创建/更新接口入参。
 * applicableProducts / applicableTeams 为逗号分隔的字符串。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCommissionPlanDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 方案名称 */
    @NotBlank(message = "方案名称不能为空")
    @Size(max = 200, message = "方案名称长度不能超过 200")
    private String planName;

    /** 方案编码 (唯一) */
    @NotBlank(message = "方案编码不能为空")
    @Size(max = 50, message = "方案编码长度不能超过 50")
    private String planCode;

    /** 方案描述 (可空) */
    @Size(max = 500, message = "方案描述长度不能超过 500")
    private String description;

    /** 方案类型: REVENUE_BASED/PROFIT_BASED/QUOTA_BASED/TIERED/BONUS/COMBO */
    @NotBlank(message = "方案类型不能为空")
    @Pattern(regexp = "REVENUE_BASED|PROFIT_BASED|QUOTA_BASED|TIERED|BONUS|COMBO",
            message = "方案类型仅支持 REVENUE_BASED/PROFIT_BASED/QUOTA_BASED/TIERED/BONUS/COMBO")
    private String planType;

    /** 计算基础: ORDER_AMOUNT/ORDER_PROFIT/ORDER_COUNT/REVENUE_TARGET/UNITS_SOLD */
    @NotBlank(message = "计算基础不能为空")
    @Pattern(regexp = "ORDER_AMOUNT|ORDER_PROFIT|ORDER_COUNT|REVENUE_TARGET|UNITS_SOLD",
            message = "计算基础仅支持 ORDER_AMOUNT/ORDER_PROFIT/ORDER_COUNT/REVENUE_TARGET/UNITS_SOLD")
    private String calculationBasis;

    /** 生效开始日期 */
    @NotNull(message = "生效开始日期不能为空")
    private LocalDate startDate;

    /** 生效结束日期 (可空, 表示长期有效) */
    private LocalDate endDate;

    /** 状态: ACTIVE/PAUSED/EXPIRED/DRAFT */
    @Pattern(regexp = "ACTIVE|PAUSED|EXPIRED|DRAFT",
            message = "状态仅支持 ACTIVE/PAUSED/EXPIRED/DRAFT")
    private String status;

    /** 目标金额 (用于 QUOTA_BASED 类型) */
    private Double targetAmount;

    /** 佣金上限 (0 表示无限) */
    private Double capAmount;

    /** 佣金下限 */
    private Double minAmount;

    /** 退款追回天数 */
    private Integer clawbackDays;

    /** 发放频率: WEEKLY/BIWEEKLY/MONTHLY/QUARTERLY/ON_DEMAND */
    @Pattern(regexp = "WEEKLY|BIWEEKLY|MONTHLY|QUARTERLY|ON_DEMAND",
            message = "发放频率仅支持 WEEKLY/BIWEEKLY/MONTHLY/QUARTERLY/ON_DEMAND")
    private String payoutFrequency;

    /** 发放日 (1-31) */
    private Integer payoutDay;

    /** 适用产品 (逗号分隔) */
    @Size(max = 500, message = "适用产品长度不能超过 500")
    private String applicableProducts;

    /** 适用团队 (逗号分隔) */
    @Size(max = 500, message = "适用团队长度不能超过 500")
    private String applicableTeams;

    /** 是否为默认方案 */
    private Boolean isDefault;

    /** 已发放佣金总额 (查询返回) */
    private Double totalCommissionPaid;

    /** 已计算销售总额 (查询返回) */
    private Double totalSalesAmount;

    /** 已计算订单总数 (查询返回) */
    private Integer totalOrders;

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
