/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 客户标签定义 DTO。
 * <p>
 * 对应 {@code ScrmTagEntity} 的业务字段, 不含公共字段与统计字段 (customerCount)。
 * 创建/更新接口入参。tagCode 创建后不可修改, tagType 创建后不可修改 (类型决定打标方式)。
 * AUTO 类型标签由 Service 在创建/更新关联规则时反向写入 ruleId。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmTagDto {

    /** 所属分组 ID (可空, 表示未分组) */
    private Long groupId;

    /** 标签名称 */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称长度不能超过 100")
    private String tagName;

    /** 标签编码 (创建时必填, 仅允许字母/数字/下划线) */
    @NotBlank(message = "标签编码不能为空")
    @Size(max = 50, message = "标签编码长度不能超过 50")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "标签编码仅允许字母、数字与下划线")
    private String tagCode;

    /** 标签类型: MANUAL 手动 / AUTO 自动 / COMPUTED 计算 */
    @NotBlank(message = "标签类型不能为空")
    @Size(max = 20, message = "标签类型长度不能超过 20")
    @Pattern(regexp = "MANUAL|AUTO|COMPUTED", message = "标签类型仅支持 MANUAL/AUTO/COMPUTED")
    private String tagType;

    /** 值类型: BOOLEAN / TEXT / NUMBER / DATE / ENUM (可空表示无值标签) */
    @Size(max = 20, message = "值类型长度不能超过 20")
    @Pattern(regexp = "BOOLEAN|TEXT|NUMBER|DATE|ENUM|",
            message = "值类型仅支持 BOOLEAN/TEXT/NUMBER/DATE/ENUM")
    private String valueType;

    /** 标签值 (用于固定值标签, 可空) */
    @Size(max = 500, message = "标签值长度不能超过 500")
    private String tagValue;

    /** 标签描述 */
    @Size(max = 500, message = "标签描述长度不能超过 500")
    private String description;

    /** 标签颜色 (前端展示用, 如 #1890FF) */
    @Size(max = 20, message = "标签颜色长度不能超过 20")
    private String color;

    /** 标签图标 (前端展示用, 如 icon-tag) */
    @Size(max = 100, message = "标签图标长度不能超过 100")
    private String icon;

    /** 排序 (数字越小越靠前, 默认 0) */
    private Integer sortOrder;

    /** 是否系统内置 (创建时可选, 默认 false) */
    private Boolean isSystem;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
