/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackProcessDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 反馈处理请求 DTO。
 * <p>
 * 用于反馈状态变更 (changeStatus) 与解决 (resolveFeedback) 等处理流程。
 * status 为目标状态 (NEW / IN_REVIEW / IN_PROGRESS / RESOLVED / CLOSED / REJECTED / DUPLICATE),
 * resolution 为解决方案 (解决/关闭时填写, 可空), assigneeId 为处理人 ID (可空)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmFeedbackProcessDto {

    /** 反馈 ID */
    @NotNull(message = "反馈 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long feedbackId;

    /** 目标状态: NEW / IN_REVIEW / IN_PROGRESS / RESOLVED / CLOSED / REJECTED / DUPLICATE */
    @NotBlank(message = "目标状态不能为空")
    @Size(max = 20, message = "目标状态长度不能超过 20")
    private String status;

    /** 解决方案 (可空) */
    @Size(max = 1000, message = "解决方案长度不能超过 1000")
    private String resolution;

    /** 处理人 ID (可空) */
    @Size(max = 100, message = "处理人 ID 长度不能超过 100")
    private String assigneeId;
}
