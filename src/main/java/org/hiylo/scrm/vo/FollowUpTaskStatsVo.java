/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : FollowUpTaskStatsVo.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SCRM 跟进任务统计 VO。
 * <p>
 * 描述指定负责人 (或全员) 在时间区间内的任务执行概要: 总数 / 完成数 / 完成率 / 逾期数 /
 * 各类型分布。基于 {@link org.hiylo.scrm.entity.ScrmFollowUpTaskEntity} 聚合生成。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpTaskStatsVo {

    /** 负责人 ID (统计维度, 可空表示全员) */
    private String assigneeId;

    /** 统计起始时间 */
    private String startDate;

    /** 统计截止时间 */
    private String endDate;

    /** 任务总数 */
    private Long totalCount;

    /** 已完成数 */
    private Long completedCount;

    /** 完成率 (completedCount / totalCount * 100, 百分比) */
    private Double completionRate;

    /** 逾期任务数 (状态为 OVERDUE 或计划时间早于当前且未完成) */
    private Long overdueCount;

    /** 进行中任务数 */
    private Long inProgressCount;

    /** 待处理任务数 */
    private Long pendingCount;

    /** 已取消任务数 */
    private Long cancelledCount;

    /** 各跟进类型任务数分布 (key: taskType, value: count) */
    private Map<String, Long> taskTypeDistribution;

    /** 各优先级任务数分布 (key: priority, value: count) */
    private Map<String, Long> priorityDistribution;

    /** 各类型完成率明细 */
    private List<TaskTypeStatsVo> typeStats;

    /**
     * 单个跟进类型的统计明细 VO。
     *
     * @author Hsi Chu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskTypeStatsVo {

        /** 跟进类型 */
        private String taskType;

        /** 该类型任务总数 */
        private Long totalCount;

        /** 该类型已完成数 */
        private Long completedCount;

        /** 该类型完成率 (百分比) */
        private Double completionRate;
    }
}
