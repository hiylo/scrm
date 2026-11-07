/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : OcrLine.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign.dto.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * OCR 原始文本行（T07 契约 §2.1 {@code lines[]} 单项）。
 * <p>
 * 携带坐标与置信度; VL 模式下作为交叉校验参照, 人工复核时用于阅读原文
 * （复核页展示 lines 中对应值行, 供人工比对）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrLine {

    /** 文本行内容 */
    private String text;

    /** 坐标框 [x0, y0, x1, y1] */
    private java.util.List<Double> bbox;

    /** 文本行置信度（0~1） */
    private Double confidence;
}
