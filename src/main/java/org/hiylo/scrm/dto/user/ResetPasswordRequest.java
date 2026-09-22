/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ResetPasswordRequest.java
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 重置密码请求 DTO, 供管理员 {@code POST /scrm/users/{id}/reset-password} 使用。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ResetPasswordRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 新口令明文 (必填, 至少 8 位且含字母与数字, 服务端 BCrypt 加密存储) */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度需在 8-72 位之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "密码需同时包含字母与数字且长度 8-72 位")
    private String newPassword;
}