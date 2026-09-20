/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestStartDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * SCRM A/B 测试排期 DTO。
 * <p>
 * 计划测试接口入参, 携带测试 ID 与计划起止日期。排期后测试状态由 DRAFT → SCHEDULED,
 * 后续可通过启动接口转为 RUNNING。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAbTestStartDto {

    /** 测试 ID */
    @NotNull(message = "测试 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long testId;

    /** 计划开始日期 (可空, 缺省取当前日期) */
    private LocalDate startDate;

    /** 计划结束日期 (可空) */
    private LocalDate endDate;
}
