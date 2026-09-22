/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CreateUserRequest.java
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建用户请求 DTO, 供管理员 {@code POST /scrm/users} 使用。
 * <p>
 * 关闭公开注册后, 系统用户只能由管理员在「用户管理」侧创建。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class CreateUserRequest implements Serializable {

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
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "密码需同时包含字母与数字且长度 8-72 位")
    private String password;

    /** 展示昵称 (可空, 为空时前端回退展示用户名) */
    @Size(max = 100, message = "昵称长度不能超过 100")
    private String displayName;

    /** 邮箱 (可空) */
    @Email(message = "邮箱格式不合法")
    @Size(max = 200, message = "邮箱长度不能超过 200")
    private String email;

    /** 角色列表 (逗号分隔, 如 {@code OPERATOR} 或 {@code ADMIN}), 默认 OPERATOR */
    @Size(max = 200, message = "角色长度不能超过 200")
    @Pattern(regexp = "^[A-Za-z,_]*$", message = "角色仅允许大写字母、小写字母、逗号与下划线")
    private String roles;
}