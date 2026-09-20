/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryItemDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 数据字典项 DTO。
 * <p>
 * 用于字典项创建、更新、查询返回。创建时必填字典 ID / 编码 / 标签 / 值, 层级 / 路径
 * 由服务端根据父项自动维护。更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmDataDictionaryItemDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 字典 ID */
    @NotNull(message = "字典 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dictId;

    /** 字典编码 (关联字典) */
    @NotBlank(message = "字典编码不能为空")
    @Size(max = 50, message = "字典编码长度不能超过 50")
    private String dictCode;

    /** 显示标签 */
    @NotBlank(message = "字典项标签不能为空")
    @Size(max = 200, message = "字典项标签长度不能超过 200")
    private String itemLabel;

    /** 字典值 */
    @NotBlank(message = "字典项值不能为空")
    @Size(max = 500, message = "字典项值长度不能超过 500")
    private String itemValue;

    /** 字典项编码 (可空, 字典下编码全局唯一) */
    @Size(max = 100, message = "字典项编码长度不能超过 100")
    private String itemCode;

    /** 父项 ID (用于树形, 可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /** 层级 (根项为 1, 由服务端维护) */
    private Integer itemLevel;

    /** 路径 (形如 1/5/12/, 由服务端维护) */
    @Size(max = 1000, message = "路径长度不能超过 1000")
    private String itemPath;

    /** 排序值 (默认 0) */
    private Integer sortOrder;

    /** 样式: DEFAULT / PRIMARY / SUCCESS / WARNING / DANGER / INFO (可空) */
    @Size(max = 50, message = "样式长度不能超过 50")
    private String itemStyle;

    /** 颜色 (可空) */
    @Size(max = 20, message = "颜色长度不能超过 20")
    private String color;

    /** 图标 (可空) */
    @Size(max = 100, message = "图标长度不能超过 100")
    private String icon;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** JSON 扩展数据 (可空, 如 {abbreviation, isocode, flag, ...}) */
    private String extraData;

    /** 标签 (可空, 逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 是否默认 (默认 false) */
    private Boolean isDefault;

    /** 是否禁用 (默认 false) */
    private Boolean isDisabled;

    /** 是否可见 (默认 true) */
    private Boolean isVisible;

    /** 使用次数 (由服务端维护) */
    private Integer usageCount;

    /** 是否启用 (默认 true) */
    private Boolean enabled;

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
