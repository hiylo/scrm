/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConfigGroupDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 配置分组 DTO。
 * <p>
 * 对应 {@code ScrmConfigGroupEntity} 的业务字段, 创建/更新接口入参。
 * environment 以枚举字符串校验合法性; 各开关与统计字段缺省时由服务端填充默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmConfigGroupDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分组名称 */
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 200, message = "分组名称长度不能超过 200")
    private String groupName;

    /** 分组编码 (全局唯一) */
    @NotBlank(message = "分组编码不能为空")
    @Size(max = 100, message = "分组编码长度不能超过 100")
    private String groupCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 父分组编码 (可空) */
    @Size(max = 100, message = "父分组编码长度不能超过 100")
    private String parentGroupCode;

    /** 分组层级 (默认 1) */
    private Integer groupLevel;

    /** 分组图标 (可空) */
    @Size(max = 200, message = "分组图标长度不能超过 200")
    private String groupIcon;

    /** 显示顺序 (默认 0) */
    private Integer displayOrder;

    /** 是否可见 (默认 TRUE) */
    private Boolean isVisible;

    /** 默认展开 (默认 TRUE) */
    private Boolean isExpanded;

    /** 可见角色 (可空) */
    @Size(max = 500, message = "可见角色长度不能超过 500")
    private String applicableRoles;

    /** 适用模块 (可空) */
    @Size(max = 500, message = "适用模块长度不能超过 500")
    private String applicableModules;

    /** 环境限定: ALL/DEV/STAGING/PRODUCTION (默认 ALL) */
    @Pattern(regexp = "ALL|DEV|STAGING|PRODUCTION",
            message = "环境限定仅支持 ALL/DEV/STAGING/PRODUCTION")
    private String environment;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 配置数 (查询返回) */
    private Integer configCount;

    /** 最近修改时间 (查询返回) */
    private LocalDateTime lastModifiedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
