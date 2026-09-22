/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : UserViewDto.java
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto.user;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户视图 DTO, 供管理端用户列表 / 详情展示 (不含口令明文)。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class UserViewDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 展示昵称 */
    private String displayName;

    /** 邮箱 */
    private String email;

    /** 角色列表 (逗号分隔, 如 ADMIN / OPERATOR) */
    private String roles;

    /** 状态: 1=启用, 0=禁用 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}