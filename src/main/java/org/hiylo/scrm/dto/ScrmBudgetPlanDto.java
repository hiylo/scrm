/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetPlanDto.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 营销预算方案 DTO。
 * <p>
 * 对应 {@code ScrmBudgetPlanEntity} 的业务字段, 创建/更新接口入参。
 * departments 为逗号分隔的字符串。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmBudgetPlanDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 预算方案名称 */
    @NotBlank(message = "预算方案名称不能为空")
    @Size(max = 200, message = "预算方案名称长度不能超过 200")
    private String planName;

    /** 预算方案编码 (唯一) */
    @NotBlank(message = "预算方案编码不能为空")
    @Size(max = 50, message = "预算方案编码长度不能超过 50")
    private String planCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 财年 */
    @NotNull(message = "财年不能为空")
    private Integer fiscalYear;

    /** 财年周期: ANNUAL/SEMIANNUAL/QUARTERLY/MONTHLY */
    @NotBlank(message = "财年周期不能为空")
    @Pattern(regexp = "ANNUAL|SEMIANNUAL|QUARTERLY|MONTHLY",
            message = "财年周期仅支持 ANNUAL/SEMIANNUAL/QUARTERLY/MONTHLY")
    private String fiscalPeriod;

    /** 周期开始日期 */
    @NotNull(message = "周期开始日期不能为空")
    private LocalDate periodStart;

    /** 周期结束日期 */
    @NotNull(message = "周期结束日期不能为空")
    private LocalDate periodEnd;

    /** 总预算 */
    @NotNull(message = "总预算不能为空")
    private Double totalBudget;

    /** 币种 (默认 CNY) */
    @Pattern(regexp = "CNY|USD|EUR|GBP|JPY",
            message = "币种仅支持 CNY/USD/EUR/GBP/JPY")
    private String currency;

    /** 预算类型: MARKETING/ADVERTISING/PROMOTION/CONTENT/EVENT/CHANNEL/TEAM/PROJECT */
    @NotBlank(message = "预算类型不能为空")
    @Pattern(regexp = "MARKETING|ADVERTISING|PROMOTION|CONTENT|EVENT|CHANNEL|TEAM|PROJECT",
            message = "预算类型仅支持 MARKETING/ADVERTISING/PROMOTION/CONTENT/EVENT/CHANNEL/TEAM/PROJECT")
    private String budgetType;

    /** 部门 (逗号分隔, 可空) */
    @Size(max = 500, message = "部门长度不能超过 500")
    private String departments;

    /** 状态: DRAFT/ACTIVE/PAUSED/EXPIRED/CLOSED */
    @Pattern(regexp = "DRAFT|ACTIVE|PAUSED|EXPIRED|CLOSED",
            message = "状态仅支持 DRAFT/ACTIVE/PAUSED/EXPIRED/CLOSED")
    private String status;

    /** 预警阈值 (0-1, 默认 0.8) */
    private Double alertThreshold;

    /** 已分配预算 (查询返回) */
    private Double allocatedBudget;

    /** 已消耗预算 (查询返回) */
    private Double spentBudget;

    /** 剩余预算 (查询返回) */
    private Double remainingBudget;

    /** 分配率 (查询返回) */
    private Double allocationRate;

    /** 消耗率 (查询返回) */
    private Double spendRate;

    /** 审批人 (查询返回) */
    private String approvedBy;

    /** 审批时间 (查询返回) */
    private LocalDateTime approvedAt;

    /** 审批金额 (查询返回) */
    private Double approvedAmount;

    /** 是否已触发预警 (查询返回) */
    private Boolean isAlertTriggered;

    /** 最近预警时间 (查询返回) */
    private LocalDateTime lastAlertAt;

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
