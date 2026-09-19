/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : FollowUpCalendarVo.java
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

/**
 * SCRM 跟进日历 VO。
 * <p>
 * 描述指定负责人某月的跟进任务按天聚合结果, 用于日历视图展示。
 * 基于计划时间 (plannedAt) 维度聚合。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpCalendarVo {

    /** 负责人 ID (可空表示全员) */
    private String assigneeId;

    /** 月份 (yyyy-MM) */
    private String month;

    /** 当月任务总数 */
    private Long totalCount;

    /** 当月已完成数 */
    private Long completedCount;

    /** 按天聚合的明细列表 */
    private List<DayCountVo> days;

    /**
     * 单日跟进任务统计 VO。
     *
     * @author Hsi Chu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayCountVo {

        /** 日期 (yyyy-MM-dd) */
        private String date;

        /** 当天任务总数 */
        private Long totalCount;

        /** 当天已完成数 */
        private Long completedCount;

        /** 当天待处理数 */
        private Long pendingCount;
    }
}
