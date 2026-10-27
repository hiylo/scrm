/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OcrVerification.java
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
 * OCR 交叉校验结果（T07 契约 §1.2 / §3 {@code verification}）。
 * <p>
 * VL 模式对数值/标识字段做确定性 OCR 原文比对: {@code ok=false} 表示存在待核对项
 * 或缺少参照文字。证照域依据本结构与字段 {@code status} 共同决定是否转人工复核。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrVerification {

    /** false = 存在待核对项（或缺少参照文字） */
    private Boolean ok;

    /** 参与数值比对的字段数 */
    private Integer checked;

    /** 问题明细（如「registered_capital：未在图片原文中检出（疑有误）」） */
    private List<String> problems = new ArrayList<>();
}
