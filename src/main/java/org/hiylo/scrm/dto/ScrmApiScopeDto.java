/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiScopeDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 开放API 权限范围 DTO。
 * <p>
 * 对应 {@code ScrmApiScopeEntity} 的业务字段, 用于权限范围增删改查接口入参与返回。
 * scopeName 唯一, resource 标识受保护资源, actions 声明允许的操作集合。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmApiScopeDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 权限范围名 (唯一, 如 customer:read) */
    @NotBlank(message = "权限范围名不能为空")
    @Size(max = 100, message = "权限范围名长度不能超过 100")
    private String scopeName;

    /** 显示名称 */
    @NotBlank(message = "显示名称不能为空")
    @Size(max = 200, message = "显示名称长度不能超过 200")
    private String displayName;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 资源: customer/contact/follow/order 等 */
    @NotBlank(message = "资源不能为空")
    @Size(max = 100, message = "资源长度不能超过 100")
    private String resource;

    /** 允许操作: read/write/admin (逗号分隔) */
    @NotBlank(message = "允许操作不能为空")
    @Size(max = 200, message = "允许操作长度不能超过 200")
    private String actions;

    /** 是否默认权限 (默认 FALSE) */
    private Boolean isDefault;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

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
