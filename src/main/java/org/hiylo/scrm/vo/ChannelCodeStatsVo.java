/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ChannelCodeStatsVo.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道活码统计 VO, 描述活码的扫码数 / 添加数与转化率。
 * <p>
 * 基于 {@link org.hiylo.scrm.entity.ScrmChannelCodeEntity} 与
 * {@link org.hiylo.scrm.entity.ScrmChannelCodeScanEntity} 聚合生成,
 * 供渠道活码统计接口返回前端。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCodeStatsVo {

    /** 活码 ID */
    private Long channelCodeId;

    /** 活码名称 */
    private String codeName;

    /** 活码类型 */
    private String codeType;

    /** 平台类型 */
    private String platformType;

    /** 当前状态 */
    private String status;

    /** 累计扫码数 */
    private Long scanCount;

    /** 已添加数 */
    private Long addedCount;

    /** 待添加数 */
    private Long pendingCount;

    /** 已拒绝数 */
    private Long rejectedCount;

    /** 添加转化率（百分比, 0-100, 已添加数 / 扫码数） */
    private Double conversionRate;
}
