/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerOverviewVo.java
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
import java.util.Map;

/**
 * 客户概览 VO, 描述客户总数、各生命周期分布与近 7 天新增客户趋势。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOverviewVo {

    /** 客户总数 */
    private Long totalCustomers;

    /** 生命周期分布（key=NEW/ACTIVE/DORMANT/LOST, value=客户数） */
    private Map<String, Long> lifecycleDistribution;

    /** 近 7 天新增客户列表 */
    private List<DailyCountVo> recentNewCustomers;
}
