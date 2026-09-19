/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RiskOverviewVo.java
 * Date : 2026/07/27 02:41:22
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
 * 风控概览 VO, 描述风控信号总数、各风险等级分布、各信号类型分布与近 7 天触发趋势。
 * <p>
 * 由 {@code ScrmDashboardService.getRiskOverview()} 从 {@code scrm_risk_signal} 表聚合填充。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskOverviewVo {

    /** 风控信号总数 */
    private Long totalRiskSignals;

    /** 风险等级分布（key=LOW/MEDIUM/HIGH/CRITICAL, value=信号数） */
    private Map<String, Long> riskLevelDistribution;

    /** 信号类型分布（key=signalType, value=信号数） */
    private Map<String, Long> signalTypeDistribution;

    /** 近 7 天风控信号触发趋势 */
    private List<DailyCountVo> recentTrend;
}
