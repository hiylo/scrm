/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReportResultDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 报表执行结果 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmReportResultDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 报表模板 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 执行人用户 ID */
    private String runBy;

    /** 执行时间 */
    private LocalDateTime runAt;

    /** 时间范围起始 */
    private LocalDateTime timeRangeStart;

    /** 时间范围结束 */
    private LocalDateTime timeRangeEnd;

    /** 结果数据 JSON */
    private String resultData;

    /** 结果行数 */
    private Integer rowCount;

    /** 执行状态：SUCCESS / FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;
}
