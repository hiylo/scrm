/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmScheduledTaskDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 任务调度配置 DTO。
 * <p>
 * 对应 {@code ScrmScheduledTaskEntity} 的业务字段, 不含公共字段与统计字段
 * (id/createTime/updateTime/version/totalExecutions 等)。创建/更新接口入参,
 * 校验注解保证必填字段与取值约束。taskType=CRON 时 cronExpression 必填,
 * FIXED_RATE/FIXED_DELAY/ONE_TIME 时分别需要 fixedRateMs/fixedDelayMs/executeAt。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmScheduledTaskDto {

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String taskName;

    /** 任务编码（唯一标识, 用于依赖引用） */
    @NotBlank(message = "任务编码不能为空")
    @Size(max = 50, message = "任务编码长度不能超过 50")
    private String taskCode;

    /** 任务描述 */
    @Size(max = 500, message = "任务描述长度不能超过 500")
    private String description;

    /** 任务类别: DATA_SYNC/CLEANUP/NOTIFICATION/REPORT/MAINTENANCE/CUSTOM/INTEGRATION/ANALYTICS */
    @NotBlank(message = "任务类别不能为空")
    @Size(max = 50, message = "任务类别长度不能超过 50")
    @Pattern(regexp = "DATA_SYNC|CLEANUP|NOTIFICATION|REPORT|MAINTENANCE|CUSTOM|INTEGRATION|ANALYTICS",
            message = "任务类别仅支持 DATA_SYNC/CLEANUP/NOTIFICATION/REPORT/MAINTENANCE/CUSTOM/INTEGRATION/ANALYTICS")
    private String taskCategory;

    /** 任务类型: CRON/FIXED_RATE/FIXED_DELAY/ONE_TIME/EVENT_TRIGGERED */
    @NotBlank(message = "任务类型不能为空")
    @Size(max = 30, message = "任务类型长度不能超过 30")
    @Pattern(regexp = "CRON|FIXED_RATE|FIXED_DELAY|ONE_TIME|EVENT_TRIGGERED",
            message = "任务类型仅支持 CRON/FIXED_RATE/FIXED_DELAY/ONE_TIME/EVENT_TRIGGERED")
    private String taskType;

    /** Cron 表达式（taskType=CRON 时必填） */
    @Size(max = 100, message = "Cron 表达式长度不能超过 100")
    private String cronExpression;

    /** 固定频率毫秒（taskType=FIXED_RATE 时必填） */
    private Integer fixedRateMs;

    /** 固定延迟毫秒（taskType=FIXED_DELAY 时必填） */
    private Integer fixedDelayMs;

    /** 一次性任务执行时间（taskType=ONE_TIME 时必填） */
    private LocalDateTime executeAt;

    /** 处理器类全名 */
    @NotBlank(message = "处理器类不能为空")
    @Size(max = 500, message = "处理器类长度不能超过 500")
    private String handlerClass;

    /** 处理器方法（默认 execute） */
    @Size(max = 100, message = "处理器方法长度不能超过 100")
    private String handlerMethod;

    /** JSON 任务参数 */
    private String parameters;

    /** 超时秒数（默认 300） */
    private Integer timeoutSeconds;

    /** 最大重试次数（默认 3） */
    private Integer maxRetries;

    /** 重试延迟秒（默认 60） */
    private Integer retryDelaySeconds;

    /** 重试退避倍数（默认 2.0） */
    private Double retryBackoffMultiplier;

    /** 优先级（默认 0） */
    private Integer priority;

    /** 依赖任务编码（逗号分隔） */
    @Size(max = 500, message = "依赖任务编码长度不能超过 500")
    private String dependencies;

    /** 状态: ACTIVE/PAUSED/ERROR/DISABLED（默认 ACTIVE） */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|PAUSED|ERROR|DISABLED|",
            message = "状态仅支持 ACTIVE/PAUSED/ERROR/DISABLED")
    private String status;

    /** 标签（逗号分隔） */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 是否启用（默认 TRUE） */
    private Boolean isEnabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
