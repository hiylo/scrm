/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWebhookConfigDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM Webhook 配置 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWebhookConfigDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** Webhook 名称 */
    @NotBlank(message = "Webhook 名称不能为空")
    @Size(max = 200, message = "Webhook 名称长度不能超过 200")
    private String webhookName;

    /** 接收 URL */
    @NotBlank(message = "目标 URL 不能为空")
    @Size(max = 500, message = "目标 URL 长度不能超过 500")
    private String targetUrl;

    /** 签名密钥（可空） */
    @Size(max = 200, message = "签名密钥长度不能超过 200")
    private String secret;

    /** 订阅事件类型列表 JSON (如 ["CUSTOMER_CREATED", "MESSAGE_RECEIVED"]) */
    @NotBlank(message = "订阅事件不能为空")
    private String subscribedEvents;

    /** 事件过滤条件 JSON（可空） */
    private String eventFilter;

    /** HTTP 方法, 默认 POST */
    @Size(max = 10, message = "HTTP 方法长度不能超过 10")
    @Pattern(regexp = "POST|PUT|GET", message = "HTTP 方法仅支持 POST/PUT/GET")
    private String httpMethod;

    /** 自定义 HTTP 头 JSON（可空） */
    private String headers;

    /** 请求超时秒数 */
    @PositiveOrZero(message = "请求超时秒数必须 >= 0")
    private Integer timeoutSeconds;

    /** 最大重试次数 */
    @PositiveOrZero(message = "最大重试次数必须 >= 0")
    private Integer maxRetries;

    /** 重试间隔秒数 */
    @PositiveOrZero(message = "重试间隔秒数必须 >= 0")
    private Integer retryIntervalSeconds;

    /** 状态: ACTIVE / INACTIVE / ERROR */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|INACTIVE|ERROR", message = "状态仅支持 ACTIVE/INACTIVE/ERROR")
    private String status;

    /** 最近触发时间 */
    private LocalDateTime lastTriggerAt;

    /** 最近一次响应码 */
    private Integer lastStatusCode;

    /** 最近一次错误信息 */
    @Size(max = 500, message = "错误信息长度不能超过 500")
    private String lastError;

    /** 成功推送次数 */
    private Integer successCount;

    /** 失败推送次数 */
    private Integer failCount;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
