/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiChatDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SCRM AI 对话请求 DTO。
 * <p>
 * 单次 AI 对话入参: {@link #customerId} 标识对话客户, {@link #message} 为客户消息,
 * {@link #conversationId} 可选关联已有会话, {@link #configId} 可选指定使用的 AI 配置
 * (未指定则使用账号默认配置)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAiChatDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户消息 */
    @NotBlank(message = "客户消息不能为空")
    private String message;

    /** 关联会话 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 使用的 AI 配置 ID (可空, 未指定则使用账号默认配置) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;
}
