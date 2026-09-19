/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : TriggerMarketingStatsVo.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 触发式营销统计 VO, 描述指定时间范围内的触发概况与各动作类型分布。
 * <p>
 * 基于 {@link org.hiylo.scrm.entity.ScrmMarketingTriggerEventEntity} 聚合生成,
 * 供触发式营销统计接口返回前端。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerMarketingStatsVo {

    /** 统计起始时间 */
    private LocalDateTime startTime;

    /** 统计截止时间 */
    private LocalDateTime endTime;

    /** 事件总触发次数 */
    private Long totalCount;

    /** 执行成功次数 */
    private Long successCount;

    /** 执行失败次数 */
    private Long failedCount;

    /** 跳过次数 (含冷却期/次数限制跳过) */
    private Long skippedCount;

    /** 待执行次数 */
    private Long pendingCount;

    /** 执行中次数 */
    private Long executingCount;

    /** 冷却期拦截次数 */
    private Long cooldownCount;

    /** 成功率（百分比, 0-100） */
    private Double successRate;

    /** 按动作类型分布: actionType → count */
    private Map<String, Long> actionTypeDistribution;

    /** 按事件状态分布: status → count */
    private Map<String, Long> statusDistribution;
}
