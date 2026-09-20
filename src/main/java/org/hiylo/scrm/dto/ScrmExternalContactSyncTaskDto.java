/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncTaskDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 外部联系人同步任务 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmExternalContactSyncTaskDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 同步配置 ID */
    @NotNull(message = "同步配置 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 200, message = "任务名称长度不能超过 200")
    private String taskName;

    /** 同步模式: INCREMENTAL / FULL */
    @NotBlank(message = "同步模式不能为空")
    @Size(max = 20, message = "同步模式长度不能超过 20")
    @Pattern(regexp = "INCREMENTAL|FULL",
            message = "同步模式仅支持 INCREMENTAL/FULL")
    private String syncMode;

    /** 任务状态: PENDING / RUNNING / SUCCESS / FAILED / CANCELLED */
    @Size(max = 20, message = "任务状态长度不能超过 20")
    @Pattern(regexp = "PENDING|RUNNING|SUCCESS|FAILED|CANCELLED",
            message = "任务状态仅支持 PENDING/RUNNING/SUCCESS/FAILED/CANCELLED")
    private String status;

    /** 任务开始时间 (可空) */
    private LocalDateTime startTime;

    /** 任务结束时间 (可空) */
    private LocalDateTime endTime;

    /** 任务耗时毫秒 (可空) */
    private Integer durationMs;

    /** 总记录数 */
    private Integer totalRecords;

    /** 成功数 */
    private Integer successCount;

    /** 失败数 */
    private Integer failedCount;

    /** 新增数 */
    private Integer newCount;

    /** 更新数 */
    private Integer updateCount;

    /** 跳过数 */
    private Integer skipCount;

    /** 错误信息 (可空) */
    @Size(max = 1000, message = "错误信息长度不能超过 1000")
    private String errorMessage;

    /** 触发者 */
    @Size(max = 100, message = "触发者长度不能超过 100")
    private String triggeredBy;

    /** 触发者类型: MANUAL / SCHEDULED / SYSTEM */
    @Size(max = 20, message = "触发者类型长度不能超过 20")
    @Pattern(regexp = "MANUAL|SCHEDULED|SYSTEM",
            message = "触发者类型仅支持 MANUAL/SCHEDULED/SYSTEM")
    private String triggeredByType;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
