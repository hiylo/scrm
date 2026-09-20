/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesTargetDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 销售目标 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSalesTargetDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 目标名称 */
    @NotBlank(message = "目标名称不能为空")
    @Size(max = 200, message = "目标名称长度不能超过 200")
    private String targetName;

    /** 目标对象类型: INDIVIDUAL / TEAM / DEPARTMENT */
    @NotBlank(message = "目标对象类型不能为空")
    @Pattern(regexp = "INDIVIDUAL|TEAM|DEPARTMENT",
            message = "目标对象类型仅支持 INDIVIDUAL/TEAM/DEPARTMENT")
    private String targetType;

    /** 目标对象 ID (userId / teamId / deptId) */
    @NotBlank(message = "目标对象 ID 不能为空")
    @Size(max = 100, message = "目标对象 ID 长度不能超过 100")
    private String targetId;

    /** 目标对象名称 */
    @Size(max = 200, message = "目标对象名称长度不能超过 200")
    private String targetNameRef;

    /** 周期类型: WEEKLY / MONTHLY / QUARTERLY / YEARLY */
    @NotBlank(message = "周期类型不能为空")
    @Pattern(regexp = "WEEKLY|MONTHLY|QUARTERLY|YEARLY",
            message = "周期类型仅支持 WEEKLY/MONTHLY/QUARTERLY/YEARLY")
    private String periodType;

    /** 周期开始日期 */
    @NotNull(message = "周期开始日期不能为空")
    private LocalDate periodStart;

    /** 周期结束日期 */
    @NotNull(message = "周期结束日期不能为空")
    private LocalDate periodEnd;

    /** 指标类型: REVENUE / NEW_CUSTOMERS / CONVERSIONS / FOLLOW_UPS / OPPORTUNITIES / CALLS */
    @NotBlank(message = "指标类型不能为空")
    @Pattern(regexp = "REVENUE|NEW_CUSTOMERS|CONVERSIONS|FOLLOW_UPS|OPPORTUNITIES|CALLS",
            message = "指标类型仅支持 REVENUE/NEW_CUSTOMERS/CONVERSIONS/FOLLOW_UPS/OPPORTUNITIES/CALLS")
    private String metricType;

    /** 目标值 */
    @NotNull(message = "目标值不能为空")
    @Positive(message = "目标值必须大于 0")
    private Double targetValue;

    /** 实际完成值 */
    private Double actualValue;

    /** 达成率 (%) */
    private Double achievementRate;

    /** 最近一次实际值更新时间 */
    private LocalDateTime lastUpdated;

    /** 状态: ACTIVE / COMPLETED / EXPIRED / ARCHIVED */
    @Pattern(regexp = "ACTIVE|COMPLETED|EXPIRED|ARCHIVED",
            message = "状态仅支持 ACTIVE/COMPLETED/EXPIRED/ARCHIVED")
    private String status;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
