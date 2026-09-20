/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ProfileTrendVo.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * SCRM 画像生成趋势 VO。
 * <p>
 * 按天聚合画像生成数量, 用于趋势可视化。基于
 * {@link org.hiylo.scrm.entity.ScrmCustomerProfileEntity#getGeneratedAt()}
 * 聚合生成。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileTrendVo {

    /** 起始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 每日生成数量 (按日期升序) */
    private List<DailyCount> dailyCounts;

    /** 总生成数 */
    private Long totalCount;

    /**
     * 单日生成数量 VO。
     *
     * @author Hsi Chu
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCount {

        /** 日期 */
        private LocalDate date;

        /** 当日生成数 */
        private Long count;
    }
}
