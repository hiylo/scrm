/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ChatArchiveStatsVo.java
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
 * 会话存档统计 VO, 描述归档总数、质量分布与风险分布。
 *
 * @author Hsi Chu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatArchiveStatsVo {

    /** 归档消息总数 */
    private Long totalArchived;

    /** 正常消息数 */
    private Long normalCount;

    /** 敏感消息数 */
    private Long sensitiveCount;

    /** 违规消息数 */
    private Long violationCount;

    /** 按平台分布 (key=平台类型, value=消息数) */
    private Map<String, Long> platformDistribution;

    /** 按质量标记分布 (key=质量标记, value=消息数) */
    private Map<String, Long> qualityDistribution;

    /** 按风险等级分布 (key=风险等级, value=消息数) */
    private Map<String, Long> riskDistribution;
}
