/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : OcrTemplate.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign.dto.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * OCR 命中的证照模板信息（T07 契约 §3 {@code template}）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrTemplate {

    /** 模板名（如 business_license） */
    private String name;

    /** 模板版本（如 v1） */
    private String version;

    /** 是否命中模板 */
    private Boolean matched;
}
