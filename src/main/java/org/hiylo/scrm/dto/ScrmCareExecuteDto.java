/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCareExecuteDto.java
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

/**
 * SCRM 关怀任务执行请求 DTO。
 * <p>
 * {@code executeTask} 接口入参, {@link #taskId} 指定待执行任务, {@link #result} 标注执行结果
 * (SUCCESS/NO_RESPONSE/REJECTED/FAILED), {@link #response} 记录客户回应内容。
 * 服务层据此更新任务状态并写入关怀记录。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCareExecuteDto {

    /** 待执行任务 ID */
    @NotNull(message = "任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 执行结果: SUCCESS / NO_RESPONSE / REJECTED / FAILED */
    @NotBlank(message = "执行结果不能为空")
    @Pattern(regexp = "SUCCESS|NO_RESPONSE|REJECTED|FAILED",
            message = "执行结果仅支持 SUCCESS/NO_RESPONSE/REJECTED/FAILED")
    private String result;

    /** 客户回应 (可空) */
    @Size(max = 500, message = "客户回应长度不能超过 500")
    private String response;

    /** 情感倾向: POSITIVE / NEUTRAL / NEGATIVE (可空) */
    @Pattern(regexp = "POSITIVE|NEUTRAL|NEGATIVE", message = "情感倾向仅支持 POSITIVE/NEUTRAL/NEGATIVE")
    private String sentiment;

    /** 执行备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;
}
