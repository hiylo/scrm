/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesRankingDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 销售业绩排名 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmSalesRankingDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 周期类型: WEEKLY / MONTHLY / QUARTERLY / YEARLY */
    private String periodType;

    /** 周期开始日期 */
    private LocalDate periodStart;

    /** 周期结束日期 */
    private LocalDate periodEnd;

    /** 目标对象类型: INDIVIDUAL / TEAM */
    private String targetType;

    /** 目标对象 ID (userId / teamId) */
    private String targetId;

    /** 目标对象名称 */
    private String targetNameRef;

    /** 指标类型: REVENUE / NEW_CUSTOMERS / CONVERSIONS / FOLLOW_UPS / OPPORTUNITIES / CALLS */
    private String metricType;

    /** 达成值 */
    private Double achievedValue;

    /** 目标值 */
    private Double targetValue;

    /** 达成率 (%) */
    private Double achievementRate;

    /** 排名 (从 1 开始, 1 为第一名) */
    private Integer rank;

    /** 排名日期 */
    private LocalDate rankingDate;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
