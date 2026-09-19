/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RiskSignalCallbackDto.java
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto.callback;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风险信号回调 DTO，外部自动化执行引擎命中风控规则时回调携带的数据。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class RiskSignalCallbackDto {

    /** 触发的风险规则 ID */
    private String ruleId;

    /** 关联的人设 ID */
    private String personaId;

    /** 关联的账号 ID */
    private String accountId;

    /** 信号类型（如 login_anomaly / frequency_overflow） */
    private String signalType;

    /** 风险等级（LOW / MEDIUM / HIGH / CRITICAL） */
    private String riskLevel;

    /** 风险详情描述 */
    private String detail;

    /** 风险触发时间 */
    private LocalDateTime triggeredAt;
}
