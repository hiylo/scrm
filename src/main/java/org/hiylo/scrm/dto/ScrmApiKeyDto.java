/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiKeyDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 开放API 密钥 DTO。
 * <p>
 * 对应 {@code ScrmApiKeyEntity} 的业务字段, 用于密钥增删改查接口入参与返回。
 * apiKey 由服务端自动生成, keyType 区分长效与临时密钥, status 控制密钥状态。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmApiKeyDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属应用 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    /** 密钥名称 */
    @NotBlank(message = "密钥名称不能为空")
    @Size(max = 200, message = "密钥名称长度不能超过 200")
    private String keyName;

    /** API 密钥 (查询返回, 创建时自动生成) */
    private String apiKey;

    /** 密钥密文 (可空) */
    @Size(max = 500, message = "密钥密文长度不能超过 500")
    private String keySecret;

    /** 密钥类型: PERMANENT/TEMPORARY (创建时可选, 默认 PERMANENT) */
    @Pattern(regexp = "PERMANENT|TEMPORARY",
            message = "密钥类型仅支持 PERMANENT/TEMPORARY")
    private String keyType;

    /** 权限范围 (逗号分隔, 可空) */
    @Size(max = 500, message = "权限范围长度不能超过 500")
    private String scopes;

    /** 允许 IP (逗号分隔, 可空) */
    @Size(max = 500, message = "允许IP长度不能超过 500")
    private String allowedIps;

    /** 每分钟速率限制 (可空, 默认 60) */
    private Integer rateLimitPerMinute;

    /** 状态: ACTIVE/EXPIRED/REVOKED */
    @Pattern(regexp = "ACTIVE|EXPIRED|REVOKED",
            message = "状态仅支持 ACTIVE/EXPIRED/REVOKED")
    private String status;

    /** 过期时间 (可空) */
    private LocalDateTime expiresAt;

    /** 最后使用时间 (查询返回) */
    private LocalDateTime lastUsedAt;

    /** 最后使用 IP (查询返回) */
    @Size(max = 100, message = "最后使用IP长度不能超过 100")
    private String lastUsedIp;

    /** 使用次数 (查询返回) */
    private Integer usageCount;

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
