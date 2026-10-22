/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRecordingCoverageVo.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 录音覆盖率统计 VO。
 * <p>
 * 覆盖率口径：{@code 录音数 / 通话数}，按用户或全局维度统计，
 * 是评估「本机不支持录音」影响面的核心指标。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrmRecordingCoverageVo {

    /** 统计口径: USER(按用户) / ALL(全局) */
    private String scope;

    /** 目标用户 ID（scope=USER 时有效） */
    private String userId;

    /** 统计起始时间（ISO） */
    private String from;

    /** 统计结束时间（ISO） */
    private String to;

    /** 通话总数 */
    private Long totalCalls;

    /** 录音总数 */
    private Long totalRecordings;

    /** 覆盖率（0~1, 保留 4 位） */
    private Double coverageRate;
}
