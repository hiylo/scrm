/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : LoginRequestDto.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录请求 DTO, 供 {@code POST /scrm/auth/login} 使用。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class LoginRequestDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录用户名 (必填) */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度需在 3-64 位之间")
    private String username;

    /** 登录口令明文 (必填, 服务端 BCrypt 比对后不落库) */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度需在 8-72 位之间")
    private String password;
}
