/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : FunnelAnalysisVo.java
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
 * 销售漏斗分析 VO, 描述各阶段商机数、金额、转化率与平均停留天数。
 * <p>
 * 基于 {@link org.hiylo.scrm.entity.ScrmOpportunityEntity} 与
 * {@link org.hiylo.scrm.entity.ScrmOpportunityStageHistoryEntity} 聚合生成,
 * 供漏斗分析接口返回前端。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelAnalysisVo {

    /** 漏斗 ID */
    private Long funnelId;

    /** 漏斗名称 */
    private String funnelName;

    /** OPEN 状态商机总数 */
    private Long totalOpenCount;

    /** OPEN 状态商机总金额 */
    private Double totalOpenAmount;

    /** 各阶段统计 (按 stageOrder 升序) */
    private List<StageStatVo> stages;

    /**
     * 单个阶段统计 VO。
     *
     * @author Hsi Chu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageStatVo {

        /** 阶段 ID */
        private Long stageId;

        /** 阶段名称 */
        private String stageName;

        /** 阶段顺序 */
        private Integer stageOrder;

        /** 是否成单阶段 */
        private Boolean isClosedStage;

        /** 是否输单阶段 */
        private Boolean isLostStage;

        /** 成交概率 (0-100) */
        private Integer probability;

        /** 当前阶段 OPEN 商机数 */
        private Long openCount;

        /** 当前阶段 OPEN 商机金额 */
        private Double openAmount;

        /** 转化率 (上一阶段进入此阶段的比例, 百分比 0-100, 首阶段为 100) */
        private Double conversionRate;

        /** 平均停留天数 (基于历史记录) */
        private Double avgDurationDays;
    }
}
