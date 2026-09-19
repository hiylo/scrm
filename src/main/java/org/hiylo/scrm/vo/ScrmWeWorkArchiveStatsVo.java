/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeWorkArchiveStatsVo.java
 * Date : 2026/08/04 08:40:58
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
 * 企微会话存档拉取统计 VO。
 * <p>
 * 描述指定配置在时间范围内的消息数、类型分布与动作分布, 用于存档监控看板。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrmWeWorkArchiveStatsVo {

    /** 存档配置 ID (null 表示全部配置) */
    private Long configId;

    /** 统计起始时间 */
    private java.time.LocalDateTime from;

    /** 统计结束时间 */
    private java.time.LocalDateTime to;

    /** 消息总数 */
    private Long totalCount;

    /** 发送消息数 */
    private Long sendCount;

    /** 撤回消息数 */
    private Long recallCount;

    /** 按消息类型分布 (key=消息类型, value=消息数) */
    private Map<String, Long> typeDistribution;

    /** 按发送者分布 (key=发送者, value=消息数) */
    private Map<String, Long> fromDistribution;
}
