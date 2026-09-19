/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DailyStat.java
 * Date : 2026/09/05 14:15:16
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

/**
 * 每日统计数据
 * <p>
 * 封装按日聚合的统计结果, 供图表查询使用。
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0
 */
public record DailyStat(String date, long count) {
}
