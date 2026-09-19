/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetAllocationDto.java
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
 * SCRM 营销预算分配 DTO。
 * <p>
 * 对应 {@code ScrmBudgetAllocationEntity} 的业务字段, 创建/更新接口入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmBudgetAllocationDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 预算方案 ID */
    @NotNull(message = "预算方案 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;

    /** 预算方案名称 (查询返回, 可空) */
    private String planName;

    /** 分配名称 */
    @NotBlank(message = "分配名称不能为空")
    @Size(max = 200, message = "分配名称长度不能超过 200")
    private String allocationName;

    /** 分配类型: DEPARTMENT/CHANNEL/CAMPAIGN/TEAM/PRODUCT/REGION */
    @NotBlank(message = "分配类型不能为空")
    @Pattern(regexp = "DEPARTMENT|CHANNEL|CAMPAIGN|TEAM|PRODUCT|REGION",
            message = "分配类型仅支持 DEPARTMENT/CHANNEL/CAMPAIGN/TEAM/PRODUCT/REGION")
    private String allocationType;

    /** 目标类型值 (如部门 ID / 渠道名) */
    @NotBlank(message = "目标类型不能为空")
    @Size(max = 100, message = "目标类型长度不能超过 100")
    private String targetType;

    /** 目标名称 (可空) */
    @Size(max = 200, message = "目标名称长度不能超过 200")
    private String targetName;

    /** 分配金额 */
    @NotNull(message = "分配金额不能为空")
    private Double allocatedAmount;

    /** 周期开始日期 */
    @NotNull(message = "周期开始日期不能为空")
    private LocalDate periodStart;

    /** 周期结束日期 */
    @NotNull(message = "周期结束日期不能为空")
    private LocalDate periodEnd;

    /** 状态: ACTIVE/PAUSED/EXHAUSTED/CLOSED */
    @Pattern(regexp = "ACTIVE|PAUSED|EXHAUSTED|CLOSED",
            message = "状态仅支持 ACTIVE/PAUSED/EXHAUSTED/CLOSED")
    private String status;

    /** 预警阈值 (0-1, 默认 0.8) */
    private Double alertThreshold;

    /** 已消耗金额 (查询返回) */
    private Double spentAmount;

    /** 剩余金额 (查询返回) */
    private Double remainingAmount;

    /** 消耗率 (查询返回) */
    private Double spendRate;

    /** 是否已触发预警 (查询返回) */
    private Boolean isAlertTriggered;

    /** 最近预警时间 (查询返回) */
    private LocalDateTime lastAlertAt;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
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
