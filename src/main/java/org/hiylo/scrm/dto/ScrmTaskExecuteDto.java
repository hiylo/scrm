/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskExecuteDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 任务执行请求 DTO。
 * <p>
 * 由 Controller 的 {@code POST /executions/execute} 接口接收, 传入 taskId 与本次参数
 * (覆盖任务默认 parameters), triggeredBy 用于追踪触发者。由
 * {@code ScrmTaskSchedulerService.executeTask} 创建执行记录并模拟执行。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTaskExecuteDto {

    /** 任务 ID */
    @NotNull(message = "任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** JSON 本次参数（覆盖任务默认参数, 可空） */
    private String parameters;

    /** 触发者（用户标识, 可空） */
    @Size(max = 100, message = "触发者长度不能超过 100")
    private String triggeredBy;
}
