/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : MassSendReportVo.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 群发任务发送报告 VO, 描述任务发送概况与成功/失败统计。
 * <p>
 * 基于 {@link org.hiylo.scrm.entity.ScrmMassSendTaskEntity} 与
 * {@link org.hiylo.scrm.entity.ScrmMassSendTargetEntity} 聚合生成,
 * 供群发任务报告接口返回前端。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MassSendReportVo {

    /** 任务 ID */
    private Long taskId;

    /** 任务名称 */
    private String taskName;

    /** 平台类型 */
    private String platformType;

    /** 当前状态 */
    private String status;

    /** 目标客户总数 */
    private Integer totalCount;

    /** 已发送数 */
    private Integer sentCount;

    /** 发送成功数 */
    private Integer successCount;

    /** 发送失败数 */
    private Integer failCount;

    /** 待发送数 */
    private Integer pendingCount;

    /** 成功率（百分比, 0-100） */
    private Double successRate;

    /** 失败率（百分比, 0-100） */
    private Double failureRate;

    /** 任务开始时间 */
    private LocalDateTime startedAt;

    /** 任务完成时间 */
    private LocalDateTime completedAt;
}
