/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : OcrRecognizeClientTest.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.feign.dto.ocr.OcrField;
import org.hiylo.scrm.feign.dto.ocr.OcrRecognizeResponse;
import org.hiylo.scrm.feign.dto.ocr.OcrVerification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OcrRecognizeClient} 单元测试。
 * <p>
 * 覆盖 fail-closed 开关语义（未启用 / 令牌缺失 → 不发请求直接抛异常）、
 * 参数校验（空文件 / 超 20MB / 类型缺失）以及契约响应 JSON 到 DTO 的反序列化
 * （字段/表格/校验结构逐项映射, 未知属性忽略）。真实 HTTP 联调待 ocr-service
 * 部署后走 T07 deploy.md 的 curl 全量走查。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("OcrRecognizeClient 单元测试")
class OcrRecognizeClientTest {

    /** OCR API 配置 */
    private OcrApiConfig config;

    /** 被测客户端 */
    private OcrRecognizeClient client;

    @BeforeEach
    void setUp() {
        config = new OcrApiConfig();
        client = new OcrRecognizeClient(config, new ObjectMapper());
    }

    @Test
    @DisplayName("未启用时拒绝发请求 (fail-closed)")
    void recognize_disabled() {
        config.setEnabled(false);
        config.setToken("some-token");
        MultipartFile file = new MockMultipartFile("file", "a.jpg",
                "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> client.recognize(file, "license", null))
                .isInstanceOf(OcrApiException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    @DisplayName("令牌缺失时拒绝发请求 (fail-closed)")
    void recognize_missingToken() {
        config.setEnabled(true);
        config.setToken("");
        MultipartFile file = new MockMultipartFile("file", "a.jpg",
                "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> client.recognize(file, "license", null))
                .isInstanceOf(OcrApiException.class)
                .hasMessageContaining("令牌未配置");
    }

    @Test
    @DisplayName("空文件 / 超 20MB / 类型缺失 参数校验")
    void recognize_invalidParams() {
        config.setEnabled(true);
        config.setToken("token");
        MultipartFile empty = new MockMultipartFile("file", "a.jpg",
                "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> client.recognize(empty, "license", null))
                .isInstanceOf(OcrApiException.class)
                .hasMessageContaining("不能为空");

        byte[] big = new byte[20 * 1024 * 1024 + 1];
        MultipartFile oversized = new MockMultipartFile("file", "big.jpg",
                "image/jpeg", big);
        assertThatThrownBy(() -> client.recognize(oversized, "license", null))
                .isInstanceOf(OcrApiException.class)
                .hasMessageContaining("20MB");

        MultipartFile ok = new MockMultipartFile("file", "a.jpg",
                "image/jpeg", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> client.recognize(ok, " ", null))
                .isInstanceOf(OcrApiException.class)
                .hasMessageContaining("识别类型不能为空");
        assertThatThrownBy(() -> client.recognize(ok, null, null))
                .isInstanceOf(OcrApiException.class)
                .hasMessageContaining("识别类型不能为空");
    }

    @Test
    @DisplayName("契约响应 JSON 反序列化 (字段/校验/未知属性)")
    void deserialize_contractResponse() throws Exception {
        String json = """
                {
                  "request_id": "ocr-20261023-0001",
                  "ok": true,
                  "mode": "vl",
                  "engine": "qwen3.5",
                  "device": "cpu",
                  "elapsed_ms": 2840,
                  "type": "license",
                  "doc_type": "license",
                  "fields": [
                    {"key": "company_name", "name": "公司名称", "value": "××财税服务有限公司",
                     "confidence": 0.96, "verified": true, "source": "vl",
                     "required": true, "status": "ok"},
                    {"key": "registered_capital", "name": "注册资本", "value": "100万元人民币",
                     "confidence": 0.71, "verified": false, "source": "vl",
                     "required": false, "status": "low_confidence", "problems": ["未检出"]}
                  ],
                  "tables": [],
                  "lines": [
                    {"text": "统一社会信用代码", "bbox": [12, 34, 145, 52], "confidence": 0.99}
                  ],
                  "text": "统一社会信用代码 91440101MA5XXXXXXX",
                  "verification": {"ok": false, "checked": 4,
                                   "problems": ["registered_capital：未在图片原文中检出（疑有误）"]},
                  "template": {"name": "business_license", "version": "v1", "matched": true},
                  "warnings": [],
                  "unknown_field": "应被忽略"
                }
                """;
        ObjectMapper objectMapper = new ObjectMapper();
        OcrRecognizeResponse response =
                objectMapper.readValue(json, OcrRecognizeResponse.class);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getRequestId()).isEqualTo("ocr-20261023-0001");
        assertThat(response.getMode()).isEqualTo("vl");
        assertThat(response.getEngine()).isEqualTo("qwen3.5");
        assertThat(response.getDocType()).isEqualTo("license");

        assertThat(response.getFields()).hasSize(2);
        OcrField company = response.getFields().get(0);
        assertThat(company.getKey()).isEqualTo("company_name");
        assertThat(company.getValue()).isEqualTo("××财税服务有限公司");
        assertThat(company.getConfidence()).isEqualTo(0.96);
        assertThat(company.getVerified()).isTrue();
        assertThat(company.getRequired()).isTrue();
        assertThat(company.getStatus()).isEqualTo("ok");

        OcrField capital = response.getFields().get(1);
        assertThat(capital.getStatus()).isEqualTo("low_confidence");
        assertThat(capital.getProblems()).containsExactly("未检出");

        assertThat(response.getVerification()).isNotNull();
        OcrVerification verification = response.getVerification();
        assertThat(verification.getOk()).isFalse();
        assertThat(verification.getChecked()).isEqualTo(4);
        assertThat(verification.getProblems()).hasSize(1);

        assertThat(response.getLines()).hasSize(1);
        assertThat(response.getLines().get(0).getBbox()).containsExactly(12d, 34d, 145d, 52d);
        assertThat(response.getTemplate()).isNotNull();
        assertThat(response.getTemplate().getName()).isEqualTo("business_license");
        assertThat(response.getText()).contains("统一社会信用代码");
    }
}