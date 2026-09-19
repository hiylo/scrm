/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQuickReplyCategoryDto.java
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
 * SCRM 快捷回复分类 DTO。
 * <p>
 * 对应 {@code ScrmQuickReplyCategoryEntity} 的业务字段, 不含公共字段
 * (id/createTime/updateTime/version)。创建/更新接口入参, 校验注解
 * 保证必填字段与长度约束。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmQuickReplyCategoryDto {

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 100, message = "分类名称长度不能超过 100")
    private String categoryName;

    /** 图标（可空, 如 emoji 或图标类名） */
    @Size(max = 50, message = "图标长度不能超过 50")
    private String icon;

    /** 排序值（数字越小越靠前, 默认 0） */
    private Integer sortOrder;

    /** 适用平台类型（可空, 空表示全部） */
    @Size(max = 30, message = "平台类型长度不能超过 30")
    private String platformType;

    /** 状态: ACTIVE / INACTIVE（创建时可选, 默认 ACTIVE） */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|INACTIVE|", message = "状态仅支持 ACTIVE/INACTIVE")
    private String status;
}
