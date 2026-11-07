/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : OcrRecognizeClient.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.feign.dto.ocr.OcrRecognizeResponse;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * OCR 识别客户端（对接 T07 契约 {@code POST /api/ocr/recognize}）。
 * <p>
 * 传输层用 Spring {@link RestClient}（与 {@code AiChatClient}/{@code QixinEnterpriseApiClient}
 * 一致, 不引入 spring-cloud-openfeign）。鉴权用请求头 {@code X-OCR-Token}, 令牌仅从
 * {@link OcrApiConfig} 读取, <b>不写日志、不落库</b>。
 * </p>
 * <p><b>开关语义</b>（fail-closed, 缺一即拒绝发请求）:
 * </p>
 * <ul>
 *   <li>{@code scrm.ocr.enabled=false}（默认）→ 抛 {@link OcrApiException}</li>
 *   <li>token 为空 → 抛 {@link OcrApiException}</li>
 * </ul>
 * <p><b>错误处理</b>（对齐契约 §4 错误码与 §7 调用封装）:
 * </p>
 * <ul>
 *   <li>429 限流 → 抛可重试异常（调用方退避重试）</li>
 *   <li>400 / 401 / 503 / 504 → 抛不可重试异常（504 由调用方降级 mode=ocr 重试 / 人工介入）</li>
 *   <li>响应体携带 {@code ok=false}（引擎缺件/图片损坏）→ 正常返回, 由调用方按 error 提示处理</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
public class OcrRecognizeClient implements OcrRecognizer {

    /** 识别接口路径（T07 契约 §2.1） */
    private static final String RECOGNIZE_PATH = "/recognize";

    /** 鉴权请求头名（T07 契约 §2.0 / §6） */
    private static final String AUTH_HEADER = "X-OCR-Token";

    /** 单文件大小上限 (20MB, 契约 §1.3 / §2.1) */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /** OCR API 配置 */
    private final OcrApiConfig config;

    /** JSON 解析器（Spring 容器内的应用级 ObjectMapper） */
    private final ObjectMapper objectMapper;

    /** HTTP 客户端, base-url 由配置注入 */
    private final RestClient restClient;

    /**
     * 构造 OCR 客户端。
     *
     * @param config       OCR API 配置
     * @param objectMapper JSON 解析器
     */
    public OcrRecognizeClient(OcrApiConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.max(1, config.getConnectTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, config.getReadTimeoutSeconds())));
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 调用 OCR 识别接口（multipart/form-data）。
     * <p>
     * 引擎风格失败（图片损坏/识别失败 → {@code ok=false} + {@code error}）不抛异常,
     * 按 {@code OcrRecognizeResponse.isSuccess()} 判定, 由调用方落库失败状态。
     * </p>
     *
     * @param file      证照图片/PDF（≤20MB; 首期以图片为主, PDF 预转图见 T07 契约 §2.5）
     * @param type      识别类型: license / idcard / account_license / general
     * @param requestId 调用方幂等/链路标识（可空, 空则自动生成）
     * @return 统一识别响应
     * @throws OcrApiException 未启用 / 令牌缺失 / 文件非法 / HTTP 传输或业务错误
     */
    public OcrRecognizeResponse recognize(MultipartFile file, String type, String requestId)
            throws OcrApiException {
        requireEnabledAndCredential();
        if (file == null || file.isEmpty()) {
            throw new OcrApiException("OCR 识别文件不能为空", false);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new OcrApiException("OCR 识别文件超过 20MB 上限: size=" + file.getSize(), false);
        }
        if (type == null || type.isBlank()) {
            throw new OcrApiException("OCR 识别类型不能为空 (license/idcard/account_license/general)", false);
        }

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new OcrApiException("读取 OCR 识别文件失败: " + e.getMessage(), e);
        }
        body.part("file", bytes).contentType(resolveContentType(file));
        body.part("type", type);
        String rid = (requestId == null || requestId.isBlank()) ? generateRequestId() : requestId;
        body.part("request_id", rid);

        OcrRecognizeResponse response;
        try {
            response = restClient.post()
                    .uri(RECOGNIZE_PATH)
                    .header(AUTH_HEADER, config.getToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(OcrRecognizeResponse.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("[OCR] 触发限流 429, 建议退避重试: requestId={}", rid);
            throw new OcrApiException("OCR 服务限流 (429), 请稍后重试", true);
        } catch (HttpClientErrorException e) {
            log.warn("[OCR] OCR 服务拒绝请求: requestId={}, http={}, body={}",
                    rid, e.getStatusCode(), safeBody(e.getResponseBodyAsString()));
            throw new OcrApiException("OCR 服务拒绝请求: http=" + e.getStatusCode(), false);
        } catch (org.springframework.web.client.RestClientException e) {
            log.warn("[OCR] OCR 接口调用失败: requestId={}, err={}", rid, e.getMessage());
            throw new OcrApiException("OCR 接口调用失败: " + e.getMessage(), e);
        }
        if (response == null) {
            throw new OcrApiException("OCR 服务返回空响应", true);
        }
        log.info("[OCR] 识别完成: requestId={}, type={}, mode={}, engine={}, "
                        + "elapsedMs={}, fields={}, ok={}",
                rid, type, response.getMode(), response.getEngine(),
                response.getElapsedMs(),
                response.getFields() == null ? 0 : response.getFields().size(),
                response.isSuccess());
        return response;
    }

    /**
     * 脱敏后的 4xx 响应体（限长, 防止敏感回显进日志）。
     *
     * @param body 原始响应体
     * @return 脱敏文本
     */
    private String safeBody(String body) {
        if (body == null) {
            return "";
        }
        String b = body.replaceAll("\\s+", " ").trim();
        return b.length() > 200 ? b.substring(0, 200) + "..." : b;
    }

    /**
     * 生成调用方幂等/链路标识（与业务单号关联见证照域服务）。
     *
     * @return 请求标识
     */
    private String generateRequestId() {
        return "ocr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * 按文件原始名推断 Content-Type（图片基本类型直接透传, 其余回落 octet-stream;
     * OCR 服务按文件内容识别, 不依赖该头）。
     *
     * @param file 上传文件
     * @return MIME 类型
     */
    private MediaType resolveContentType(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return MediaType.IMAGE_JPEG;
            }
            if (lower.endsWith(".png")) {
                return MediaType.IMAGE_PNG;
            }
            if (lower.endsWith(".webp")) {
                return MediaType.parseMediaType("image/webp");
            }
            if (lower.endsWith(".gif")) {
                return MediaType.IMAGE_GIF;
            }
            if (lower.endsWith(".bmp")) {
                return MediaType.parseMediaType("image/bmp");
            }
            if (lower.endsWith(".pdf")) {
                return MediaType.APPLICATION_PDF;
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * 校验开关与凭证（缺一即失败, 不在未启用/无令牌状态下打内部服务）。
     *
     * @throws OcrApiException 未启用或令牌缺失
     */
    private void requireEnabledAndCredential() throws OcrApiException {
        if (!config.isEnabled()) {
            throw new OcrApiException(
                    "OCR 服务未启用 (scrm.ocr.enabled=false)", false);
        }
        if (config.getToken() == null || config.getToken().isBlank()) {
            throw new OcrApiException(
                    "OCR 服务令牌未配置 (scrm.ocr.token)", false);
        }
    }
}