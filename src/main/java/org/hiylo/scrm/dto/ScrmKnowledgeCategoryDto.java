/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeCategoryDto.java
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

/**
 * SCRM 知识分类 DTO。
 * <p>
 * 对应 {@code ScrmKnowledgeCategoryEntity} 的业务字段, 创建/更新接口入参。
 * articleCount / totalViews / totalLikes 为查询返回, 由服务端管理。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmKnowledgeCategoryDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 200, message = "分类名称长度不能超过 200")
    private String categoryName;

    /** 分类编码 (唯一) */
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 50, message = "分类编码长度不能超过 50")
    @Pattern(regexp = "^[A-Za-z0-9_\\-]+$", message = "分类编码仅支持字母/数字/下划线/连字符")
    private String categoryCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 父分类 ID (可空, 顶层分类为 null) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /** 层级 (查询返回) */
    private Integer categoryLevel;

    /** 分类路径 (查询返回) */
    private String categoryPath;

    /** 排序序号 (可空, 默认 0) */
    private Integer sortOrder;

    /** 图标 (可空) */
    @Size(max = 100, message = "图标长度不能超过 100")
    private String icon;

    /** 颜色 (可空) */
    @Size(max = 20, message = "颜色长度不能超过 20")
    private String color;

    /** 文章数 (查询返回) */
    private Integer articleCount;

    /** 总浏览量 (查询返回) */
    private Integer totalViews;

    /** 总点赞数 (查询返回) */
    private Integer totalLikes;

    /** 是否启用 (查询返回) */
    private Boolean enabled;

    /** 可见角色 (可空, 逗号分隔) */
    @Size(max = 500, message = "可见角色长度不能超过 500")
    private String visibleToRoles;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
