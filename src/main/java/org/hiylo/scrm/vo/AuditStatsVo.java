/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AuditStatsVo.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 审计日志统计 VO, 描述总操作数、成功 / 失败数与按资源分布。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditStatsVo {

    /** 审计日志总数 */
    private Long totalOperations;

    /** 成功操作数 */
    private Long successCount;

    /** 失败操作数 */
    private Long failedCount;

    /** 成功率 (0~1) */
    private Double successRate;

    /** 按资源分布 (key=资源标识, value=操作数) */
    private Map<String, Long> resourceDistribution;
}
