/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementScoreDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户互动评分 DTO。
 * <p>
 * 对应 {@code ScrmEngagementScoreEntity} 的字段, 用于评分查询/排行/分布接口返回。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmEngagementScoreDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 总分 (累计原始得分) */
    private Double totalScore;

    /** 当前分 (应用衰减后) */
    private Double currentScore;

    /** 活跃等级: INACTIVE/LOW/MEDIUM/HIGH/VERY_HIGH */
    private String engagementLevel;

    /** 评分趋势: UP/STABLE/DOWN */
    private String scoreTrend;

    /** 趋势变化百分比 */
    private Double trendChangePercent;

    /** 最后互动时间 */
    private LocalDateTime lastEventAt;

    /** 最后计算时间 */
    private LocalDateTime lastCalculatedAt;

    /** 连续互动天数 */
    private Integer streakDays;

    /** 总互动次数 */
    private Integer totalEvents;

    /** 周得分 */
    private Double weeklyScore;

    /** 月得分 */
    private Double monthlyScore;

    /** 季度得分 */
    private Double quarterlyScore;

    /** 年得分 */
    private Double yearlyScore;

    /** 等级更新时间 */
    private LocalDateTime levelUpdatedAt;

    /** JSON 各行为得分明细 */
    private String metadata;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
