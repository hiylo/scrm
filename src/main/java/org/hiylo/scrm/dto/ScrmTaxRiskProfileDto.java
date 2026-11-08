/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxRiskProfileDto.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 税负风险画像 DTO（竞品差距 P1：好会计/精斗云均有税负监测 + 涉税风险）。
 * <p>
 * 纯只读分析：从申报记录 + 利润表收入聚合出税负率趋势与逾期/欠缴预警，
 * 供代账会计在续费/报税前快速发现异常。不做任何写操作。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrmTaxRiskProfileDto {

    /** 企业 ID */
    private Long companyId;

    /** 分析年度 */
    private Integer year;

    /** 申报记录总数（该年内） */
    private long recordTotal;

    /** 逾期记录数 */
    private long overdueCount;

    /** 欠缴（已到期实缴 &lt; 应缴）记录数 */
    private long underpaidCount;

    /** 税负率趋势（按月） */
    private List<TrendItem> trend = new ArrayList<>();

    /** 风险项（level = WARN / CRITICAL） */
    private List<RiskItem> risks = new ArrayList<>();

    /** 生成时间 */
    private String generatedAt;

    /** 税负率趋势行 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendItem {

        /** 月份（YYYY-MM） */
        private String month;

        /** 当月营业收入（利润表） */
        private String revenue;

        /** 增值税税负率（应纳/收入，百分比字符串，如 "1.2%"；无收入或数据不足为 "-"） */
        private String taxRate;

        /** 标记：LOW（偏低）/ HIGH（偏高）/ NORMAL / NA */
        private String flag;
    }

    /** 风险项 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskItem {

        /** WARN / CRITICAL */
        private String level;

        /** 消息 */
        private String message;

        /** 建议 */
        private String suggestion;
    }
}