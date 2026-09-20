/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CustomerMergeStatsVo.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户合并统计 VO, 描述重复检测数、合并数与回滚数。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerMergeStatsVo {

    /** 重复检测总数 */
    private Long totalDuplicates;

    /** 待处理重复数 */
    private Long pendingCount;

    /** 已确认重复数 */
    private Long confirmedCount;

    /** 已忽略重复数 */
    private Long ignoredCount;

    /** 已合并重复数 */
    private Long mergedCount;

    /** 合并记录总数 */
    private Long totalMergeRecords;

    /** 已完成合并数 */
    private Long completedMerges;

    /** 已回滚合并数 */
    private Long revertedMerges;
}
