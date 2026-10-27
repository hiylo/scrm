/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OcrRecognizeResponse.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign.dto.ocr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * OCR 识别统一响应（T07 契约 §2.1 / §3 顶层结构）。
 * <p>
 * 字段/表格/原始文本行/全文/交叉校验/模板命中与警告全部携带, 证照域据此落库
 * 并对低置信度字段转人工复核。响应 JSON 结构见
 * {@code docs/tasks/T07/ocr-api.md}（约定即契约）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrRecognizeResponse {

    /** 调用方幂等/链路标识, 响应原样带回 */
    @JsonProperty("request_id")
    private String requestId;

    /** 识别整体成功与否（引擎缺件/图片损坏 → false + error, 不抛 HTTP 5xx） */
    private Boolean ok;

    /** 实际执行模式: auto / vl / ocr */
    private String mode;

    /** 引擎标识: rapidocr / qwen3.5 等 */
    private String engine;

    /** 运行设备: cpu / directml 等 */
    private String device;

    /** 识别耗时（毫秒） */
    @JsonProperty("elapsed_ms")
    private Long elapsedMs;

    /** 请求识别类型: license / idcard / account_license / general */
    private String type;

    /** 服务端模板判定结果（可经字段特征再确认） */
    @JsonProperty("doc_type")
    private String docType;

    /** 字段列表（type 非 general 时按模板输出） */
    private List<OcrField> fields = new ArrayList<>();

    /** 表格结构列表 */
    private List<OcrTable> tables = new ArrayList<>();

    /** 原始文本行（坐标 + 置信度, VL 模式用于校验参照） */
    private List<OcrLine> lines = new ArrayList<>();

    /** 全文（阅读顺序拼接） */
    private String text;

    /** 交叉校验结果（VL 模式必有; ocr 模式恒 ok） */
    private OcrVerification verification;

    /** 命中的证照模板信息 */
    private OcrTemplate template;

    /** 非致命提示（如 doc_side_unknown、PDF 仅首页） */
    private List<String> warnings = new ArrayList<>();

    /** 识别失败原因（ok=false 时携带, 引擎风格不抛异常） */
    private String error;

    /**
     * 是否识别成功。
     *
     * @return 成功返回 true（ok 缺省按 true 处理, 兼容旧版响应未带 ok 的场景）
     */
    public boolean isSuccess() {
        return ok == null || ok;
    }
}