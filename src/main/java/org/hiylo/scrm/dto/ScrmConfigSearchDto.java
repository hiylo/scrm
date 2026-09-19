/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConfigSearchDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 配置高级搜索 DTO。
 * <p>
 * 用于 {@code searchConfigs} 接口的入参, 支持按分组 / 类型 / 关键字 / 环境 / 可见性
 * 多维度组合查询。所有字段均可空, 空字段不参与过滤。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmConfigSearchDto {

    /** 配置分组 (可空) */
    @Size(max = 100, message = "配置分组长度不能超过 100")
    private String group;

    /** 配置类型 (可空) */
    @Size(max = 30, message = "配置类型长度不能超过 30")
    private String type;

    /** 关键字 (可空, 匹配 configKey / configName / description) */
    @Size(max = 200, message = "关键字长度不能超过 200")
    private String keyword;

    /** 环境限定 (可空): ALL/DEV/STAGING/PRODUCTION */
    @Size(max = 50, message = "环境限定长度不能超过 50")
    private String environment;

    /** 是否可见 (可空) */
    private Boolean isVisible;

    /** 是否启用 (可空) */
    private Boolean enabled;

    /** 是否敏感 (可空) */
    private Boolean isSensitive;

    /** 是否系统级 (可空) */
    private Boolean isSystem;

    /** 配置分组编码 (可空, 精确匹配) */
    @Size(max = 100, message = "分组编码长度不能超过 100")
    private String groupCode;

    /** 适用模块 (可空, 模糊匹配) */
    @Size(max = 500, message = "适用模块长度不能超过 500")
    private String applicableModule;

    /** 可见角色 (可空, 模糊匹配) */
    @Size(max = 500, message = "可见角色长度不能超过 500")
    private String applicableRole;
}
