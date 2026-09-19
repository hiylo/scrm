/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetSearchDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 营销素材搜索 DTO。
 * <p>
 * 用于素材列表与搜索接口入参, 支持按分类 / 类型 / 标签 / 状态 / 关键词过滤,
 * {@code sortBy} 控制排序维度 (NEWEST / POPULAR / DOWNLOADS / VIEWS / SIZE)。
 * 关键词匹配素材名称 / 描述 / 标签, 排序按相关度。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAssetSearchDto {

    /** 分类 ID 过滤 (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    /** 素材类型过滤 (可空) */
    @Size(max = 30, message = "素材类型长度不能超过 30")
    private String assetType;

    /** 标签过滤 (可空, 精确匹配逗号分隔标签中的任一) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 状态过滤 (可空) */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 关键词 (匹配名称 / 描述 / 标签, 可空) */
    @Size(max = 500, message = "关键词长度不能超过 500")
    private String keyword;

    /** 排序维度: NEWEST / POPULAR / DOWNLOADS / VIEWS / SIZE (默认 NEWEST) */
    @Size(max = 20, message = "排序维度长度不能超过 20")
    private String sortBy;
}
