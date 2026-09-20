/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskRetryDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SCRM 任务重试请求 DTO。
 * <p>
 * 由 Controller 的 {@code POST /executions/retry} 接口接收, 传入原执行 ID 与可选的
 * 自定义延迟秒数 (未指定时由 {@code calculateRetryDelay} 按指数退避计算)。
 * 由 {@code ScrmTaskSchedulerService.retryExecution} 创建新执行并执行。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTaskRetryDto {

    /** 原执行 ID */
    @NotNull(message = "执行 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long executionId;

    /** 自定义延迟秒数（未指定时按指数退避计算） */
    private Integer delaySeconds;
}
