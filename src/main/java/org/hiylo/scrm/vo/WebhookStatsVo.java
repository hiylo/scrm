/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WebhookStatsVo.java
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

/**
 * Webhook 推送统计 VO, 描述指定 Webhook 在指定时间范围内的推送概况。
 * <p>
 * 基于 {@link org.hiylo.scrm.entity.ScrmWebhookLogEntity} 聚合生成,
 * 供 Webhook 统计接口返回前端。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookStatsVo {

    /** Webhook 配置 ID */
    private Long webhookId;

    /** 统计起始时间 */
    private LocalDateTime startTime;

    /** 统计截止时间 */
    private LocalDateTime endTime;

    /** 推送总数 */
    private Long totalCount;

    /** 成功推送次数 */
    private Long successCount;

    /** 失败推送次数 */
    private Long failedCount;

    /** 待处理次数 */
    private Long pendingCount;

    /** 重试中次数 */
    private Long retryCount;

    /** 已过期次数 */
    private Long expiredCount;

    /** 成功率（百分比, 0-100） */
    private Double successRate;

    /** 平均耗时 (毫秒) */
    private Double avgDurationMs;
}
