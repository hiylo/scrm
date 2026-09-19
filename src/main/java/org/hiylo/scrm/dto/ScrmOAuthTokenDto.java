/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOAuthTokenDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM OAuth2 令牌请求 DTO。
 * <p>
 * 用于 OAuth2 令牌端点 (token endpoint) 的令牌申请: clientId / clientSecret 为客户端凭证,
 * grantType 标识授权类型 (authorization_code / refresh_token / client_credentials),
 * code 为授权码 (authorization_code 模式), redirectUri 为回调地址。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmOAuthTokenDto {

    /** 客户端 ID */
    @NotBlank(message = "客户端ID不能为空")
    @Size(max = 200, message = "客户端ID长度不能超过 200")
    private String clientId;

    /** 客户端密钥 */
    @NotBlank(message = "客户端密钥不能为空")
    @Size(max = 500, message = "客户端密钥长度不能超过 500")
    private String clientSecret;

    /** 授权类型: authorization_code / refresh_token / client_credentials */
    @NotBlank(message = "授权类型不能为空")
    @Size(max = 50, message = "授权类型长度不能超过 50")
    private String grantType;

    /** 授权码 (authorization_code 模式必填, 可空) */
    @Size(max = 500, message = "授权码长度不能超过 500")
    private String code;

    /** 回调地址 (authorization_code 模式必填, 可空) */
    @Size(max = 1000, message = "回调地址长度不能超过 1000")
    private String redirectUri;
}
