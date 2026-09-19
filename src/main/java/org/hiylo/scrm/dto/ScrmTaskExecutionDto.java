/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskExecutionDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 任务执行记录 DTO。
 * <p>
 * 对应 {@code ScrmTaskExecutionEntity} 的业务字段, 用于执行结果回填与执行记录查询。
 * 执行记录由 {@code ScrmTaskSchedulerService.executeTask} 自动创建并维护状态流转,
 * progress / progressMessage 由 {@code updateProgress} 增量上报。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTaskExecutionDto {

    /** 任务 ID (引用 scrm_scheduled_task.id) */
    @NotNull(message = "任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 任务名称（执行时快照） */
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String taskName;

    /** 任务编码（执行时快照） */
    @Size(max = 50, message = "任务编码长度不能超过 50")
    private String taskCode;

    /** 执行编号（唯一, 未指定时由 Service 自动生成） */
    @Size(max = 100, message = "执行编号长度不能超过 100")
    private String executionNo;

    /** 触发类型: SCHEDULED/MANUAL/RETRY/DEPENDENCY/EVENT（默认 SCHEDULED） */
    @Size(max = 20, message = "触发类型长度不能超过 20")
    private String triggerType;

    /** 执行状态: PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELLED/SKIPPED */
    @Size(max = 20, message = "执行状态长度不能超过 20")
    private String status;

    /** 计划执行时间 */
    private LocalDateTime scheduledAt;

    /** 实际开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 执行时长毫秒 */
    private Integer durationMs;

    /** 执行节点 ID */
    @Size(max = 100, message = "执行节点 ID 长度不能超过 100")
    private String workerId;

    /** 执行节点名称 */
    @Size(max = 100, message = "执行节点名称长度不能超过 100")
    private String workerName;

    /** JSON 本次参数 */
    private String parameters;

    /** JSON 执行结果 */
    private String result;

    /** 返回值 */
    @Size(max = 2000, message = "返回值长度不能超过 2000")
    private String returnValue;

    /** 执行日志 */
    private String outputLogs;

    /** 错误信息 */
    @Size(max = 2000, message = "错误信息长度不能超过 2000")
    private String errorMessage;

    /** 错误堆栈 */
    private String errorStack;

    /** 当前重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetries;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 触发者 */
    @Size(max = 100, message = "触发者长度不能超过 100")
    private String triggeredBy;

    /** 触发者名称 */
    @Size(max = 100, message = "触发者名称长度不能超过 100")
    private String triggeredByName;

    /** 依赖的执行 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dependencyExecutionId;

    /** 是否为重试执行 */
    private Boolean isRetried;

    /** 重试执行 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long retryExecutionId;

    /** 执行进度 0-100 */
    private Integer progress;

    /** 进度消息 */
    @Size(max = 500, message = "进度消息长度不能超过 500")
    private String progressMessage;

    /** JSON 附加数据 */
    private String metadata;
}
