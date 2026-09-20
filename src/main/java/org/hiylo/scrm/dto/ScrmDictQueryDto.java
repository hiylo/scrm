/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDictQueryDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 数据字典查询 DTO。
 * <p>
 * 用于字典分页查询的过滤条件, 所有字段均可空, 为空时不参与过滤。{@link #keyword}
 * 同时匹配字典名称 / 编码 / 描述。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmDictQueryDto {

    /** 字典类型过滤: LIST / TREE / CASCADE / MULTI_LEVEL (可空) */
    @Size(max = 30, message = "字典类型长度不能超过 30")
    private String dictType;

    /** 字典分类过滤: SYSTEM / BUSINESS / CUSTOM / INDUSTRY / REGION (可空) */
    @Size(max = 100, message = "字典分类长度不能超过 100")
    private String category;

    /** 所属模块过滤 (可空) */
    @Size(max = 100, message = "所属模块长度不能超过 100")
    private String module;

    /** 启用状态过滤 (可空, true=仅启用, false=仅禁用) */
    private Boolean enabled;

    /** 是否系统内置过滤 (可空) */
    private Boolean isSystem;

    /** 关键词过滤, 匹配字典名称 / 编码 / 描述 (可空) */
    @Size(max = 200, message = "关键词长度不能超过 200")
    private String keyword;
}
