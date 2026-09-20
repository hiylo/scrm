/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDictBatchImportDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SCRM 数据字典批量导入 DTO。
 * <p>
 * 向已存在的字典 (按 dictCode 定位) 批量追加字典项。服务端按 itemCode / itemValue
 * 去重: 已存在的项跳过或更新 (默认跳过), 不存在的项新增。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmDictBatchImportDto {

    /** 字典编码 (目标字典) */
    @NotBlank(message = "字典编码不能为空")
    @Size(max = 50, message = "字典编码长度不能超过 50")
    private String dictCode;

    /** 待导入的字典项列表 (至少 1 条) */
    @NotEmpty(message = "字典项列表不能为空")
    @Valid
    private List<ScrmDataDictionaryItemDto> items;

    /** 是否覆盖已存在的项 (默认 false, 仅新增) */
    private Boolean overwrite;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
