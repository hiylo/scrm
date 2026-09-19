/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyProgressLogDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 旅程进度日志 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmJourneyProgressLogDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 入营记录 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long enrollmentId;

    /** 所属旅程 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long journeyId;

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 步骤 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long stepId;

    /** 步骤名称 */
    @Size(max = 200, message = "步骤名称长度不能超过 200")
    private String stepName;

    /** 步骤类型: SEND_MESSAGE / WAIT / CONDITION / ADD_TAG / SET_LIFECYCLE / WEBHOOK / END */
    @NotBlank(message = "步骤类型不能为空")
    @Size(max = 30, message = "步骤类型长度不能超过 30")
    private String stepType;

    /** 执行结果: SUCCESS / FAILED / SKIPPED / WAITING */
    @NotBlank(message = "执行结果不能为空")
    @Size(max = 20, message = "执行结果长度不能超过 20")
    private String actionResult;

    /** 执行详情 */
    @Size(max = 500, message = "执行详情长度不能超过 500")
    private String actionDetail;

    /** 执行时间 */
    @NotNull(message = "执行时间不能为空")
    private LocalDateTime executedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
