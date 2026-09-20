/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CampaignOverviewVo.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 营销任务概览 VO, 描述任务总数、各状态分布与近 7 天创建趋势。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignOverviewVo {

    /** 任务总数 */
    private Long totalCampaigns;

    /** 各状态任务分布（key=DRAFT/RUNNING/PAUSED/COMPLETED/FAILED, value=任务数） */
    private java.util.Map<String, Long> statusDistribution;

    /** 近 7 天任务创建趋势 */
    private List<DailyCountVo> recentTrend;
}
