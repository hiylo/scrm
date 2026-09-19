/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : LoginResponseDto.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto.auth;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 登录响应 DTO, 返回访问令牌与调用方身份信息。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 访问令牌 (JWT, 后续请求通过 {@code Authorization: Bearer <token>} 携带) */
    private String accessToken;

    /** 令牌类型, 固定为 Bearer */
    private String tokenType;

    /** 令牌有效期 (秒) */
    private Long expiresIn;

    /** 用户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /** 用户名 */
    private String username;

    /** 角色列表 */
    private List<String> roles;

}
