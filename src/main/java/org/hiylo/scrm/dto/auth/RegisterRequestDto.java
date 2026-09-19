/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RegisterRequestDto.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 注册请求 DTO, 供 {@code POST /scrm/auth/register} 使用。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class RegisterRequestDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录用户名 (必填, 3-64 位字母数字下划线) */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度需在 3-64 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母、数字与下划线")
    private String username;

    /** 登录口令明文 (必填, 至少 8 位且含字母与数字, 服务端 BCrypt 加密存储) */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度需在 8-72 位之间")
    private String password;

    /** 展示昵称 (可空, 为空时前端回退展示用户名) */
    @Size(max = 100, message = "昵称长度不能超过 100")
    private String displayName;

    /** 邮箱 (可空) */
    @Email(message = "邮箱格式不合法")
    @Size(max = 200, message = "邮箱长度不能超过 200")
    private String email;
}
