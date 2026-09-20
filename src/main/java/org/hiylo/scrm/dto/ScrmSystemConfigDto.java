/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 系统配置 DTO。
 * <p>
 * 对应 {@code ScrmSystemConfigEntity} 的业务字段, 创建/更新接口入参。
 * configType / environment / uiComponent 以枚举字符串校验合法性;
 * 各开关与统计字段缺省时由服务端填充默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSystemConfigDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 配置键 (全局唯一) */
    @NotBlank(message = "配置键不能为空")
    @Size(max = 200, message = "配置键长度不能超过 200")
    private String configKey;

    /** 配置值 (可空, TEXT) */
    private String configValue;

    /** 默认值 (可空, TEXT) */
    private String defaultValue;

    /** 配置名称 */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 200, message = "配置名称长度不能超过 200")
    private String configName;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 配置分组 (默认 GENERAL) */
    @Size(max = 100, message = "配置分组长度不能超过 100")
    private String configGroup;
 /**配置类型:
 STRING/INTEGER/DOUBLE/BOOLEAN/JSON/XML/DATE/TIME/DATETIME/ENUM/PASSWORD/ENCRYPTED/FILE/URL/EMAIL/PHONE/COLOR/RICH_TEXT
 */
    @NotBlank(message = "配置类型不能为空")
    @Pattern(regexp = "STRING|INTEGER|DOUBLE|BOOLEAN|JSON|XML|DATE|TIME|DATETIME|ENUM|PASSWORD|ENCRYPTED|FILE|URL|EMAIL|PHONE|COLOR|RICH_TEXT",
            message = "配置类型仅支持 STRING/INTEGER/DOUBLE/BOOLEAN/JSON/XML/DATE/TIME/DATETIME/ENUM/PASSWORD/ENCRYPTED/FILE/URL/EMAIL/PHONE/COLOR/RICH_TEXT")
    private String configType;

    /** 数据类型 (可空) */
    @Size(max = 50, message = "数据类型长度不能超过 50")
    private String dataType;

    /** 枚举选项 JSON: [{label,value}] (可空) */
    @Size(max = 1000, message = "枚举选项长度不能超过 1000")
    private String enumOptions;

    /** 验证正则 (可空) */
    @Size(max = 500, message = "验证正则长度不能超过 500")
    private String validationRegex;

    /** 验证提示 (可空) */
    @Size(max = 500, message = "验证提示长度不能超过 500")
    private String validationMessage;

    /** 最小值 (可空) */
    private Double minValue;

    /** 最大值 (可空) */
    private Double maxValue;

    /** 最大长度 (可空) */
    private Integer maxLength;

    /** 是否必填 (默认 FALSE) */
    private Boolean isRequired;

    /** 是否只读 (默认 FALSE) */
    private Boolean isReadOnly;

    /** 是否加密 (默认 FALSE) */
    private Boolean isEncrypted;

    /** 是否敏感信息 (默认 FALSE) */
    private Boolean isSensitive;

    /** 是否系统级 (默认 FALSE) */
    private Boolean isSystem;

    /** 是否可见 (默认 TRUE) */
    private Boolean isVisible;

    /** 是否可搜索 (默认 FALSE) */
    private Boolean isSearchable;

    /** 显示顺序 (默认 0) */
    private Integer displayOrder;

    /** 帮助文本 (可空) */
    @Size(max = 1000, message = "帮助文本长度不能超过 1000")
    private String helpText;

    /** 占位提示 (可空) */
    @Size(max = 500, message = "占位提示长度不能超过 500")
    private String placeholder;

    /** UI 组件 (可空) */
    @Pattern(regexp = "^$|INPUT|TEXTAREA|SELECT|MULTI_SELECT|RADIO|CHECKBOX|SWITCH|SLIDER|DATE_PICKER|TIME_PICKER|COLOR_PICKER|FILE_UPLOAD|RICH_EDITOR|CODE_EDITOR|JSON_EDITOR",
            message = "UI 组件仅支持 INPUT/TEXTAREA/SELECT/MULTI_SELECT/RADIO/CHECKBOX/SWITCH/SLIDER/DATE_PICKER/TIME_PICKER/COLOR_PICKER/FILE_UPLOAD/RICH_EDITOR/CODE_EDITOR/JSON_EDITOR")
    private String uiComponent;

    /** UI 属性 JSON (可空, TEXT) */
    private String uiProps;

    /** 依赖配置键 (可空) */
    @Size(max = 200, message = "依赖配置键长度不能超过 200")
    private String dependsOn;

    /** 依赖条件 (可空) */
    @Size(max = 500, message = "依赖条件长度不能超过 500")
    private String dependencyCondition;

    /** 适用模块 (可空) */
    @Size(max = 500, message = "适用模块长度不能超过 500")
    private String applicableModules;

    /** 可见角色 (可空) */
    @Size(max = 500, message = "可见角色长度不能超过 500")
    private String applicableRoles;

    /** 环境限定: ALL/DEV/STAGING/PRODUCTION (默认 ALL) */
    @Pattern(regexp = "ALL|DEV|STAGING|PRODUCTION",
            message = "环境限定仅支持 ALL/DEV/STAGING/PRODUCTION")
    private String environment;

    /** 账号可覆盖 (默认 TRUE) */
    private Boolean isOverridable;

    /** 可缓存 (默认 TRUE) */
    private Boolean isCachable;

    /** 缓存 TTL 秒 (默认 300) */
    private Integer cacheTtlSeconds;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

    /** 标签 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 变更次数 (查询返回) */
    private Integer changeCount;

    /** 最近变更时间 (查询返回) */
    private LocalDateTime lastChangedAt;

    /** 最近变更人 (查询返回) */
    private String lastChangedBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
