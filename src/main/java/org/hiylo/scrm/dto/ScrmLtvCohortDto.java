/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvCohortDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM LTV 分组分析 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmLtvCohortDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分组名称 */
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 200, message = "分组名称长度不能超过 200")
    private String cohortName;

    /** 获客分组类型: ACQUISITION_MONTH / ACQUISITION_CHANNEL / CUSTOMER_TIER / GEOGRAPHY */
    @Pattern(regexp = "ACQUISITION_MONTH|ACQUISITION_CHANNEL|CUSTOMER_TIER|GEOGRAPHY",
            message = "分组类型仅支持 ACQUISITION_MONTH/ACQUISITION_CHANNEL/CUSTOMER_TIER/GEOGRAPHY")
    private String cohortType;

    /** 分组键值 */
    @NotBlank(message = "分组键值不能为空")
    @Size(max = 200, message = "分组键值长度不能超过 200")
    private String cohortKey;

    /** 分组开始日期 */
    @NotNull(message = "分组开始日期不能为空")
    private LocalDate cohortStartDate;

    /** 分组客户数 */
    @Min(value = 0, message = "分组客户数不能小于 0")
    private Integer cohortSize;

    /** 周期月数 */
    @NotNull(message = "周期月数不能为空")
    @Min(value = 1, message = "周期月数不能小于 1")
    private Integer periodMonths;

    /** 平均 LTV */
    private Double avgLtv;

    /** 中位 LTV */
    private Double medianLtv;

    /** 总收入 */
    private Double totalRevenue;

    /** 平均收入 */
    private Double avgRevenue;

    /** 平均订单数 */
    private Integer avgOrders;

    /** 留存率 (0-1) */
    private Double retentionRate;

    /** 活跃客户数 */
    private Integer activeCustomers;

    /** 流失客户数 */
    private Integer churnedCustomers;

    /** 平均客户年龄天数 */
    private Integer avgCustomerAgeDays;

    /** 高价值客户数 */
    private Integer topTierCustomers;

    /** 计算时间 */
    private LocalDateTime calculatedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
