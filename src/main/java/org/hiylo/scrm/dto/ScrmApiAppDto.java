/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiAppDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 开放API 应用 DTO。
 * <p>
 * 对应 {@code ScrmApiAppEntity} 的业务字段, 用于应用增删改查接口入参与返回。
 * appType 标识应用类型, status 控制应用状态, clientId/clientSecret 由服务端自动生成。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmApiAppDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 应用名称 */
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 200, message = "应用名称长度不能超过 200")
    private String appName;

    /** 应用编码 (唯一) */
    @Size(max = 100, message = "应用编码长度不能超过 100")
    private String appCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 应用类型: INTERNAL/THIRD_PARTY/PARTNER/WEBHOOK (创建时可选, 默认 THIRD_PARTY) */
    @Pattern(regexp = "INTERNAL|THIRD_PARTY|PARTNER|WEBHOOK",
            message = "应用类型仅支持 INTERNAL/THIRD_PARTY/PARTNER/WEBHOOK")
    private String appType;

    /** 客户端 ID (查询返回, 创建时自动生成) */
    private String clientId;

    /** 客户端密钥 (查询返回, 加密存储, 创建时自动生成) */
    private String clientSecret;

    /** 回调URL (逗号分隔, 可空) */
    @Size(max = 1000, message = "回调URL长度不能超过 1000")
    private String redirectUris;

    /** 权限范围 (逗号分隔, 可空) */
    @Size(max = 500, message = "权限范围长度不能超过 500")
    private String scopes;

    /** 每分钟速率限制 (可空, 默认 60) */
    private Integer rateLimitPerMinute;

    /** 每日速率限制 (可空, 默认 10000) */
    private Integer rateLimitPerDay;

    /** IP 白名单 (逗号分隔, 可空) */
    @Size(max = 500, message = "IP白名单长度不能超过 500")
    private String ipWhitelist;

    /** 状态: ACTIVE/SUSPENDED/REVOKED */
    @Pattern(regexp = "ACTIVE|SUSPENDED|REVOKED",
            message = "状态仅支持 ACTIVE/SUSPENDED/REVOKED")
    private String status;

    /** 过期时间 (可空) */
    private LocalDateTime expiresAt;

    /** 最后访问时间 (查询返回) */
    private LocalDateTime lastAccessAt;

    /** 累计请求总数 (查询返回) */
    private Integer totalRequestCount;

    /** 今日请求总数 (查询返回) */
    private Integer todayRequestCount;

    /** 负责人 (可空) */
    @Size(max = 100, message = "负责人长度不能超过 100")
    private String ownerName;

    /** 联系邮箱 (可空) */
    @Size(max = 200, message = "联系邮箱长度不能超过 200")
    private String contactEmail;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
