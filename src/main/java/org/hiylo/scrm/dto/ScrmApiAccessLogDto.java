/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiAccessLogDto.java
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
 * SCRM 开放API 调用日志 DTO。
 * <p>
 * 对应 {@code ScrmApiAccessLogEntity} 的业务字段, 用于记录与查询 API 调用日志。
 * endpoint / method / requestIp / responseStatus / accessedAt 为必填。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmApiAccessLogDto {

    /** 主键 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属应用 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    /** 调用所用密钥 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long apiKeyId;

    /** 客户端 ID (可空) */
    @Size(max = 200, message = "客户端ID长度不能超过 200")
    private String clientId;

    /** 请求路径 */
    @NotBlank(message = "请求路径不能为空")
    @Size(max = 500, message = "请求路径长度不能超过 500")
    private String endpoint;

    /** HTTP 方法: GET/POST/PUT/DELETE */
    @NotBlank(message = "HTTP方法不能为空")
    @Pattern(regexp = "GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS",
            message = "HTTP方法仅支持 GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS")
    private String method;

    /** 请求 IP */
    @NotBlank(message = "请求IP不能为空")
    @Size(max = 100, message = "请求IP长度不能超过 100")
    private String requestIp;

    /** User-Agent (可空) */
    @Size(max = 500, message = "User-Agent长度不能超过 500")
    private String userAgent;

    /** JSON 请求参数 (可空) */
    private String requestParams;

    /** 请求体 (可空) */
    private String requestBody;

    /** HTTP 状态码 */
    @NotNull(message = "HTTP状态码不能为空")
    private Integer responseStatus;

    /** 响应时间 (毫秒, 可空) */
    private Integer responseTimeMs;

    /** 错误码 (可空) */
    @Size(max = 50, message = "错误码长度不能超过 50")
    private String errorCode;

    /** 错误信息 (可空) */
    @Size(max = 500, message = "错误信息长度不能超过 500")
    private String errorMessage;

    /** 请求 ID (链路追踪, 可空) */
    @Size(max = 100, message = "请求ID长度不能超过 100")
    private String requestId;

    /** 访问时间 */
    @NotNull(message = "访问时间不能为空")
    private LocalDateTime accessedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
