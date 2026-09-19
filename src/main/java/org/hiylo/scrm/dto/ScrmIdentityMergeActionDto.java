/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeActionDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 合并任务审核动作 DTO。
 * <p>
 * 用于审核合并任务 (APPROVE 通过 / REJECT 拒绝 / CANCEL 取消), 携带审核意见。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmIdentityMergeActionDto {

    /** 任务 ID */
    @NotNull(message = "任务 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 动作: APPROVE(通过) / REJECT(拒绝) / CANCEL(取消) */
    @NotBlank(message = "动作不能为空")
    @Pattern(regexp = "APPROVE|REJECT|CANCEL", message = "动作仅支持 APPROVE/REJECT/CANCEL")
    private String action;

    /** 审核意见 (可空) */
    @Size(max = 500, message = "审核意见长度不能超过 500")
    private String comment;
}
