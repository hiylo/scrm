/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWebhookLogDto.java
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

import java.time.LocalDateTime;

/**
 * SCRM Webhook 推送日志 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmWebhookLogDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** Webhook 配置 ID */
    @NotNull(message = "Webhook ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long webhookId;

    /** 事件类型 */
    @NotBlank(message = "事件类型不能为空")
    @Size(max = 50, message = "事件类型长度不能超过 50")
    private String eventType;

    /** 事件唯一 ID */
    @NotBlank(message = "事件 ID 不能为空")
    @Size(max = 200, message = "事件 ID 长度不能超过 200")
    private String eventId;

    /** 事件负载 JSON */
    @NotBlank(message = "事件负载不能为空")
    private String payload;

    /** 实际发送的请求体 */
    private String requestBody;

    /** HTTP 响应码 */
    private Integer responseStatus;

    /** 响应体摘要 */
    @Size(max = 2000, message = "响应体摘要长度不能超过 2000")
    private String responseBody;

    /** 状态: PENDING / SENDING / SUCCESS / FAILED / RETRY / EXPIRED */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "PENDING|SENDING|SUCCESS|FAILED|RETRY|EXPIRED",
            message = "状态仅支持 PENDING/SENDING/SUCCESS/FAILED/RETRY/EXPIRED")
    private String status;

    /** 已尝试次数 */
    private Integer attemptCount;

    /** 最大尝试次数 */
    private Integer maxAttempts;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 实际发送时间 */
    private LocalDateTime sentAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 错误信息 */
    @Size(max = 500, message = "错误信息长度不能超过 500")
    private String errorMessage;

    /** 耗时毫秒 */
    private Integer durationMs;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
