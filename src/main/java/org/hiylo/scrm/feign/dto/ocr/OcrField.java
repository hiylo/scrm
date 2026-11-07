/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : OcrField.java
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
 * OCR 识别字段（T07 契约 §2.1 {@code fields[]} 单项）。
 * <p>
 * 直接映射 OCR 服务返回的字段列表结构: 字段键/中文名/值/置信度/是否被确定性 OCR
 * 证实/来源/必填标记/状态/问题明细。证照域按该结构落库 {@code fields_json} 并做
 * 低置信度复核判定（{@code status != ok} 的必填字段 → 人工复核）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrField {

    /** 模板字段键（英文 snake_case, 见 license-fields.md） */
    private String key;

    /** 字段中文名（展示用） */
    private String name;

    /** 识别值; 空/看不清为 null */
    private String value;

    /** 字段级置信度（0~1） */
    private Double confidence;

    /** 是否被确定性 OCR 证实（VL 模式才有意义; ocr 模式恒 true） */
    private Boolean verified;

    /** 来源: vl / ocr / template（模板派生） */
    private String source;

    /** 模板定义的必填项 */
    private Boolean required;

    /** 状态: ok / missing / low_confidence / unverified / invalid */
    private String status;

    /** 该字段的问题明细（如「身份证校验码不通过」） */
    private List<String> problems = new ArrayList<>();
}
