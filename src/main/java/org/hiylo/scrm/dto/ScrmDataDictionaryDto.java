/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryDto.java
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
 * SCRM 数据字典 DTO。
 * <p>
 * 用于字典创建、更新、查询返回。创建时必填字典名称与编码, 字典类型缺省由服务端补全为 LIST,
 * 系统内置 / 缓存 / 启用等布尔字段缺省补全。更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmDataDictionaryDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 字典名称 */
    @NotBlank(message = "字典名称不能为空")
    @Size(max = 200, message = "字典名称长度不能超过 200")
    private String dictName;

    /** 字典编码 (唯一, 仅允许大写字母/数字/下划线) */
    @NotBlank(message = "字典编码不能为空")
    @Size(max = 50, message = "字典编码长度不能超过 50")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "字典编码需以大写字母开头, 仅允许大写字母/数字/下划线")
    private String dictCode;

    /** 字典描述 (可空) */
    @Size(max = 500, message = "字典描述长度不能超过 500")
    private String description;

    /** 字典类型: LIST / TREE / CASCADE / MULTI_LEVEL (默认 LIST) */
    @Size(max = 30, message = "字典类型长度不能超过 30")
    private String dictType;

    /** 字典分类: SYSTEM / BUSINESS / CUSTOM / INDUSTRY / REGION (可空) */
    @Size(max = 100, message = "字典分类长度不能超过 100")
    private String category;

    /** 父字典 ID (用于级联字典, 可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /** 所属模块 (可空) */
    @Size(max = 100, message = "所属模块长度不能超过 100")
    private String module;

    /** 适用场景 (可空) */
    @Size(max = 500, message = "适用场景长度不能超过 500")
    private String applicableScenarios;

    /** 字典项数量 (创建时忽略, 由服务端维护) */
    private Integer itemCount;

    /** 是否系统内置 (默认 false) */
    private Boolean isSystem;

    /** 是否可缓存 (默认 true) */
    private Boolean isCacheable;

    /** 缓存 TTL 秒数 (默认 3600) */
    private Integer cacheTtlSeconds;

    /** 排序值 (默认 0) */
    private Integer sortOrder;

    /** 是否启用 (默认 true) */
    private Boolean enabled;

    /** 引用次数 (由服务端维护) */
    private Integer usageCount;

    /** 最近使用时间 (由服务端维护) */
    private LocalDateTime lastUsedAt;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
