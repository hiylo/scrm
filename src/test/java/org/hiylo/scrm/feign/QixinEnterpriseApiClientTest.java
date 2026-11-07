/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : QixinEnterpriseApiClientTest.java
 * Date : 2026/10/24 11:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hiylo.scrm.feign.dto.QixinEnterpriseProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link QixinEnterpriseApiClient} 契约测试（本地 mock HTTP，不依赖真实凭证与网络）。
 * <p>
 * <b>验证目标</b>（D05 待真 token 联调前先把可离线验证的契约锁住）：
 * <ul>
 *   <li><b>开关/凭证闸门</b>：未启用或 token 为空时必须在发请求前失败（不打第三方接口）</li>
 *   <li><b>鉴权头</b>：{@code Authorization: <token>} 正确下发</li>
 *   <li><b>信封解析</b>：{@code {status/code, message, data}} 多别名 + data 缺失回落整包</li>
 *   <li><b>字段多别名</b>：regStatus/businessStatus、legalPerson/operName、partners/shareholderList</li>
 *   <li><b>错误码</b>：业务码非成功 / 非 JSON / 空响应 → {@link QixinApiException}</li>
 *   <li><b>入参校验</b>：企业名与信用代码同时为空 → 失败</li>
 * </ul>
 * 用 JDK 内置 {@link HttpServer} 起桩，零额外依赖。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("启信宝客户端 契约")
class QixinEnterpriseApiClientTest {

    /** 本地桩服务 */
    private HttpServer server;

    /** 桩服务基址 */
    private String baseUrl;

    /** 记录桩收到的请求路径与鉴权头 */
    private final List<String> recordedPaths = new CopyOnWriteArrayList<>();

    /** 记录桩收到的 Authorization 头 */
    private final List<String> recordedAuth = new CopyOnWriteArrayList<>();

    /** 桩要返回的响应体 */
    private volatile String responseBody = "{}";

    /** 桩要返回的 HTTP 状态码 */
    private volatile int responseStatus = 200;

    /** 被测客户端使用的配置 */
    private QixinApiConfig config;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        recordedPaths.clear();
        recordedAuth.clear();
        responseBody = "{}";
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        config = new QixinApiConfig();
        config.setEnabled(true);
        config.setBaseUrl(baseUrl);
        config.setToken("test-token-value");
        config.setConnectTimeoutSeconds(2);
        config.setReadTimeoutSeconds(2);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 桩处理器：记录请求并回写预设响应。
     *
     * @param exchange 请求
     * @throws IOException IO 异常
     */
    private void handle(HttpExchange exchange) throws IOException {
        recordedPaths.add(exchange.getRequestURI().getPath());
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null) {
            recordedAuth.add(auth);
        }
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ==================== 闸门 ====================

    @Test
    @DisplayName("未启用：发请求前直接失败，不触碰网络")
    void disabled_failsBeforeRequest() {
        config.setEnabled(false);

        assertThatThrownBy(() -> new QixinEnterpriseApiClient(config, objectMapper)
                .queryProfile("示例企业", "91330100MA27X00001"))
                .isInstanceOf(QixinApiException.class)
                .hasMessageContaining("未启用");
        assertThat(recordedPaths).isEmpty();
    }

    @Test
    @DisplayName("token 为空：发请求前直接失败，不触碰网络")
    void blankToken_failsBeforeRequest() {
        config.setToken("  ");

        assertThatThrownBy(() -> new QixinEnterpriseApiClient(config, objectMapper)
                .queryProfile("示例企业", null))
                .isInstanceOf(QixinApiException.class)
                .hasMessageContaining("访问令牌未配置");
        assertThat(recordedPaths).isEmpty();
    }

    @Test
    @DisplayName("企业名与信用代码同时为空：失败且不发请求")
    void bothEmpty_fails() {
        assertThatThrownBy(() -> new QixinEnterpriseApiClient(config, objectMapper)
                .queryProfile(null, "  "))
                .isInstanceOf(QixinApiException.class)
                .hasMessageContaining("不能同时为空");
        assertThat(recordedPaths).isEmpty();
    }

    // ==================== 正常解析 ====================

    @Test
    @DisplayName("照面：鉴权头正确 + status 信封 + 字段多别名 + 股东展开")
    void queryProfile_parsesEnvelopeAndAliases() {
        responseBody = """
                {"status":"200","message":"ok","data":{
                  "name":"示例企业有限公司",
                  "creditCode":"91330100MA27X00001",
                  "operName":"张三",
                  "regStatus":"存续",
                  "partners":[
                    {"name":"李四","percent":"60%"},
                    {"stockName":"王五","stockPercent":"40%"}
                  ]
                }}""";

        QixinEnterpriseProfile profile = new QixinEnterpriseApiClient(config, objectMapper)
                .queryProfile("示例企业", "91330100MA27X00001");

        assertThat(recordedPaths).containsExactly("/api/v1/enterprise/profile");
        assertThat(recordedAuth).containsExactly("test-token-value");
        assertThat(profile.getEnterpriseName()).isEqualTo("示例企业有限公司");
        assertThat(profile.getCreditCode()).isEqualTo("91330100MA27X00001");
        assertThat(profile.getLegalPerson()).isEqualTo("张三");
        assertThat(profile.getBusinessStatus()).isEqualTo("存续");
        assertThat(profile.getShareholders()).containsExactly("李四(60%)", "王五(40%)");
        assertThat(profile.getRawJson()).contains("示例企业有限公司");
    }

    @Test
    @DisplayName("照面：data 缺失时回落整个响应体，字段取不到则用入参兜底")
    void queryProfile_fallsBackWhenDataMissing() {
        responseBody = "{\"status\":\"200\",\"message\":\"ok\"}";

        QixinEnterpriseProfile profile = new QixinEnterpriseApiClient(config, objectMapper)
                .queryProfile("兜底企业名", "91330100MA27X00002");

        assertThat(profile.getEnterpriseName()).isEqualTo("兜底企业名");
        assertThat(profile.getCreditCode()).isEqualTo("91330100MA27X00002");
        assertThat(profile.getShareholders()).isEmpty();
    }

    @Test
    @DisplayName("经营异常：list 非空 → abnormal=true 并取首条原因与日期")
    void queryAbnormal_detectsRecord() {
        responseBody = """
                {"code":"0","data":{"creditCode":"91330100MA27X00003","items":[
                  {"inReason":"未按规定期限公示年度报告","inDate":"2026-05-01"}
                ]}}""";

        QixinEnterpriseProfile profile = new QixinEnterpriseApiClient(config, objectMapper)
                .queryAbnormal("异常企业", "91330100MA27X00003");

        assertThat(recordedPaths).containsExactly("/api/v1/enterprise/abnormal");
        assertThat(profile.isAbnormal()).isTrue();
        assertThat(profile.getAbnormalReason()).isEqualTo("未按规定期限公示年度报告");
        assertThat(profile.getAbnormalDate()).isEqualTo("2026-05-01");
    }

    @Test
    @DisplayName("经营异常：列表为空 → abnormal=false")
    void queryAbnormal_emptyList() {
        responseBody = "{\"status\":\"200\",\"data\":{\"items\":[]}}";

        QixinEnterpriseProfile profile = new QixinEnterpriseApiClient(config, objectMapper)
                .queryAbnormal("正常企业", "91330100MA27X00004");

        assertThat(profile.isAbnormal()).isFalse();
    }

    // ==================== 错误路径 ====================

    @Test
    @DisplayName("业务码非成功：抛异常并带 code/message，标记不可重试")
    void businessCodeNotSuccess_throws() {
        responseBody = "{\"status\":\"401\",\"message\":\"token invalid\"}";

        assertThatThrownBy(() -> new QixinEnterpriseApiClient(config, objectMapper)
                .queryProfile("示例企业", null))
                .isInstanceOf(QixinApiException.class)
                .hasMessageContaining("业务失败")
                .hasMessageContaining("401")
                .hasMessageContaining("token invalid");
    }

    @Test
    @DisplayName("非 JSON 响应：抛异常标记可重试")
    void nonJsonResponse_throwsRetryable() {
        responseBody = "<html>502 Bad Gateway</html>";

        assertThatThrownBy(() -> new QixinEnterpriseApiClient(config, objectMapper)
                .queryProfile("示例企业", null))
                .isInstanceOf(QixinApiException.class)
                .hasMessageContaining("非合法 JSON");
    }

    @Test
    @DisplayName("空响应：抛异常")
    void emptyResponse_throws() {
        responseBody = "";

        assertThatThrownBy(() -> new QixinEnterpriseApiClient(config, objectMapper)
                .queryProfile("示例企业", null))
                .isInstanceOf(QixinApiException.class)
                .hasMessageContaining("空响应");
    }

    @Test
    @DisplayName("HTTP 5xx：抛可重试异常（连接层失败）")
    void httpError_throwsRetryable() {
        responseStatus = 500;
        responseBody = "server error";

        assertThatThrownBy(() -> new QixinEnterpriseApiClient(config, objectMapper)
                .queryProfile("示例企业", null))
                .isInstanceOf(QixinApiException.class)
                .hasMessageContaining("调用失败");
    }

    // ==================== 凭证不外泄 ====================

    @Test
    @DisplayName("错误信息不含完整 token（凭证不外泄）")
    void errorMessage_doesNotLeakToken() {
        config.setToken("super-secret-token-12345");
        responseBody = "{\"status\":\"401\",\"message\":\"denied\"}";

        assertThatThrownBy(() -> new QixinEnterpriseApiClient(config, objectMapper)
                .queryProfile("示例企业", null))
                .isInstanceOf(QixinApiException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("super-secret-token-12345"));
    }
}
