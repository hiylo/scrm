/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : OcrTable.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign.dto.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * OCR 表格结构（T07 契约 §2.4 {@code tables[]} 单项）。
 * <p>
 * 统一结构（两个引擎产出都映射到此）: 列名 + 纯文本二维数组 + 维度 + Markdown 渲染。
 * 证照场景一般为空, 通用识别（发票/行程单等）时携带。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrTable {

    /** 是否判定为表格 */
    private Boolean found;

    /** 表格名（VL 模式取 JSON 键; OCR 模式 table_&lt;n&gt;） */
    private String name;

    /** 列名 */
    private List<String> columns = new ArrayList<>();

    /** 纯文本二维数组, 空单元格为空串 */
    private List<List<String>> rows = new ArrayList<>();

    /** 列数 */
    private Integer ncols;

    /** 行数 */
    private Integer nrows;

    /** Markdown 渲染（展示用, 含分隔行） */
    private String markdown;
}
