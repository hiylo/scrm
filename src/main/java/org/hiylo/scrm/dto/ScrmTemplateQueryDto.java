/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTemplateQueryDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import lombok.Data;

/**
 * SCRM 模板查询 DTO。
 * <p>
 * 模板列表分页查询的过滤条件, 所有字段均可空, 组合使用时按 AND 关系过滤。
 * {@code channels} 按渠道模糊匹配 (模板 channels 字段包含该渠道), {@code keyword}
 * 按模板名称/编码模糊匹配。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTemplateQueryDto {

    /** 所属分组 ID 过滤（可空） */
    private Long groupId;

    /** 模板类型过滤（可空） */
    private String templateType;

    /** 适用渠道过滤（可空, 模糊匹配 channels 字段） */
    private String channels;

    /** 状态过滤（可空） */
    private String status;

    /** 模板分类过滤（可空） */
    private String category;

    /** 关键字过滤（按模板名称/编码模糊匹配, 可空） */
    private String keyword;
}
