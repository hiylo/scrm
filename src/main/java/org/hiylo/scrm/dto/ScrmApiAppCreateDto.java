/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiAppCreateDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 开放API 应用创建 DTO。
 * <p>
 * 仅包含创建应用时由调用方提供的字段: appName / appCode / description / appType /
 * redirectUris / scopes。clientId 与 clientSecret 由服务端自动生成, 不在此 DTO 中。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmApiAppCreateDto {

    /** 应用名称 */
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 200, message = "应用名称长度不能超过 200")
    private String appName;

    /** 应用编码 (唯一) */
    @NotBlank(message = "应用编码不能为空")
    @Size(max = 100, message = "应用编码长度不能超过 100")
    private String appCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 应用类型: INTERNAL/THIRD_PARTY/PARTNER/WEBHOOK (默认 THIRD_PARTY) */
    @Pattern(regexp = "INTERNAL|THIRD_PARTY|PARTNER|WEBHOOK",
            message = "应用类型仅支持 INTERNAL/THIRD_PARTY/PARTNER/WEBHOOK")
    private String appType;

    /** 回调URL (逗号分隔, 可空) */
    @Size(max = 1000, message = "回调URL长度不能超过 1000")
    private String redirectUris;

    /** 权限范围 (逗号分隔, 可空) */
    @Size(max = 500, message = "权限范围长度不能超过 500")
    private String scopes;
}
