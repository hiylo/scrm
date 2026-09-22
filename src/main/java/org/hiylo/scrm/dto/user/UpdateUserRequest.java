/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : UpdateUserRequest.java
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 更新用户 DTO, 供管理员 {@code PUT /scrm/users/{id}} 使用。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class UpdateUserRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 展示昵称 (可空, 为空时不更新) */
    @Size(max = 100, message = "昵称长度不能超过 100")
    private String displayName;

    /** 邮箱 (可空, 为空时不更新) */
    @Email(message = "邮箱格式不合法")
    @Size(max = 200, message = "邮箱长度不能超过 200")
    private String email;

    /** 角色列表 (逗号分隔, 如 {@code OPERATOR} 或 {@code ADMIN}) */
    @Size(max = 200, message = "角色长度不能超过 200")
    @Pattern(regexp = "^[A-Za-z,_]*$", message = "角色仅允许大写字母、小写字母、逗号与下划线")
    private String roles;

    /** 状态: 1=启用, 0=禁用 */
    private Integer status;
}