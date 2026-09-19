/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetCategoryDto.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 营销素材分类 DTO。
 * <p>
 * 用于分类创建、更新、查询返回。创建时必填分类名称与编码, 层级 / 排序 / 启用状态等缺省由
 * 服务端补全。更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAssetCategoryDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 200, message = "分类名称长度不能超过 200")
    private String categoryName;

    /** 分类编码 (唯一, 仅允许大写字母/数字/下划线) */
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 50, message = "分类编码长度不能超过 50")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "分类编码需以大写字母开头, 仅允许大写字母/数字/下划线")
    private String categoryCode;

    /** 分类描述 (可空) */
    @Size(max = 500, message = "分类描述长度不能超过 500")
    private String description;

    /** 父分类 ID (可空, 空表示顶级分类) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /** 分类层级 (默认 1) */
    private Integer categoryLevel;

    /** 分类路径 (由服务端维护) */
    private String categoryPath;

    /** 排序值 (默认 0) */
    private Integer sortOrder;

    /** 图标 (可空) */
    @Size(max = 100, message = "图标长度不能超过 100")
    private String icon;

    /** 颜色 (可空) */
    @Size(max = 20, message = "颜色长度不能超过 20")
    private String color;

    /** 素材数 (由服务端维护) */
    private Integer assetCount;

    /** 总大小字节 (由服务端维护) */
    private Long totalSizeBytes;

    /** 是否启用 (默认 true) */
    private Boolean enabled;

    /** 可见角色 (逗号分隔, 可空) */
    @Size(max = 500, message = "可见角色长度不能超过 500")
    private String visibleToRoles;

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
