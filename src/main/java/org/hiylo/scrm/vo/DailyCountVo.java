/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DailyCountVo.java
 * Date : 2026/05/06 11:25:23
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 看板日度计数 VO, 用于近 N 天趋势 / 新增等按日聚合数据。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyCountVo {

    /** 日期（yyyy-MM-dd） */
    private String date;

    /** 当日数量 */
    private Long count;
}
