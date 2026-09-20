/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CampaignReportVo.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 营销任务效果报告 VO, 描述任务执行概况、成功/失败统计、执行频率、按天趋势与 Top 错误。
 * <p>
 * 基于 {@link org.hiylo.scrm.entity.ScrmCampaignExecutionLogEntity} 聚合生成,
 * 供营销任务效果分析接口返回前端。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignReportVo {

    /** 任务 ID */
    private Long campaignId;

    /** 任务名称 */
    private String campaignName;

    /** 任务类型 */
    private String campaignType;

    /** 当前状态 */
    private String status;

    /** 任务开始时间 */
    private LocalDateTime startDate;

    /** 任务结束时间 */
    private LocalDateTime endDate;

    /** 执行天数（startDate 到 endDate 或现在的天数） */
    private Integer durationDays;

    /** 总执行次数 */
    private Long totalExecutions;

    /** 成功次数 */
    private Long successCount;

    /** 失败次数 */
    private Long failedCount;

    /** 进行中次数 */
    private Long runningCount;

    /** 成功率（百分比, 0-100） */
    private Double successRate;

    /** 失败率（百分比, 0-100） */
    private Double failureRate;

    /** 涉及账号数（distinct operatedBy） */
    private Long uniqueAccounts;

    /** 涉及行为流数（distinct behaviorFlowId） */
    private Long uniqueBehaviorFlows;

    /** 首次执行时间 */
    private LocalDateTime firstExecutionAt;

    /** 最近执行时间 */
    private LocalDateTime lastExecutionAt;

    /** 平均执行间隔（分钟, 基于 operatedAt 时间差） */
    private Long avgExecutionIntervalMinutes;

    /** 按天执行趋势 */
    private List<DailyExecutionVo> dailyTrend;

    /** Top 5 错误统计 */
    private List<ErrorStatsVo> topErrors;

    /**
     * 按天执行统计 VO。
     *
     * @author Hsi Chu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyExecutionVo {

        /** 日期（yyyy-MM-dd） */
        private String date;

        /** 当日执行总数 */
        private Long totalCount;

        /** 当日成功数 */
        private Long successCount;

        /** 当日失败数 */
        private Long failedCount;
    }

    /**
     * 错误统计 VO。
     *
     * @author Hsi Chu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorStatsVo {

        /** 错误码 */
        private String errorCode;

        /** 错误消息（截取前 100 字符） */
        private String errorMessage;

        /** 出现次数 */
        private Long count;
    }
}
