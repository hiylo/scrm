/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskDependencyDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 任务依赖关系 DTO。
 * <p>
 * 对应 {@code ScrmTaskDependencyEntity} 的业务字段。taskId 与 dependsOnTaskId 必填,
 * dependencyType 默认 ON_SUCCESS (仅当父任务成功时触发主任务), isRequired=TRUE 时
 * 依赖未满足会取消主任务, FALSE 时跳过依赖继续执行。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmTaskDependencyDto {

    /** 主任务 ID */
    @NotNull(message = "主任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 主任务编码（可空, 用于校验） */
    @Size(max = 50, message = "主任务编码长度不能超过 50")
    private String taskCode;

    /** 依赖任务 ID */
    @NotNull(message = "依赖任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dependsOnTaskId;

    /** 依赖任务编码（可空, 用于校验） */
    @Size(max = 50, message = "依赖任务编码长度不能超过 50")
    private String dependsOnTaskCode;

    /** 依赖类型: ON_SUCCESS/ON_COMPLETION/ON_FAILURE（默认 ON_SUCCESS） */
    @Size(max = 20, message = "依赖类型长度不能超过 20")
    @Pattern(regexp = "ON_SUCCESS|ON_COMPLETION|ON_FAILURE|",
            message = "依赖类型仅支持 ON_SUCCESS/ON_COMPLETION/ON_FAILURE")
    private String dependencyType;

    /** 条件表达式（基于父执行结果评估） */
    @Size(max = 500, message = "条件表达式长度不能超过 500")
    private String conditionExpression;

    /** 延迟执行秒（默认 0） */
    private Integer delaySeconds;

    /** 是否必须（默认 TRUE） */
    private Boolean isRequired;

    /** 最长等待分钟（默认 60） */
    private Integer maxWaitMinutes;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
