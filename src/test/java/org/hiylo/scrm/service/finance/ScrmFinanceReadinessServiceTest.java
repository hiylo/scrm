/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmFinanceReadinessServiceTest.java
 * Date : 2026/10/24 17:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance;

import org.hiylo.scrm.config.AiProviderRegistry;
import org.hiylo.scrm.config.ScrmInspectionProperties;
import org.hiylo.scrm.feign.QixinApiConfig;
import org.hiylo.scrm.integration.wework.config.WeworkConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmFinanceReadinessService} 单元测试。
 * <p>
 * 验证就绪汇总的核心语义：
 * <ul>
 *   <li>{@code ready} 必须是「开关已开 <b>且</b> 凭证/参数齐备」，只有开关不算就绪</li>
 *   <li>未就绪必须给出可执行的 {@code blocker}，而不是笼统的「不可用」</li>
 *   <li>全部就绪时 {@code allReady=true}</li>
 *   <li><b>绝不回显 token/secret 明文</b></li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("财税域就绪汇总")
class ScrmFinanceReadinessServiceTest {

    @Mock
    private Environment environment;

    /** AI Provider 运行时路由 mock（默认无 image provider, OCR 走本地视觉判定） */
    @Mock
    private AiProviderRegistry providerRegistry;

    private org.hiylo.scrm.config.VisionModelProperties visionProperties;

    private QixinApiConfig qixinApiConfig;

    private ScrmInspectionProperties inspectionProperties;

    private WeworkConfig weworkConfig;

    private ScrmFinanceReadinessService service;

    @BeforeEach
    void setUp() {
        visionProperties = new org.hiylo.scrm.config.VisionModelProperties();
        qixinApiConfig = new QixinApiConfig();
        inspectionProperties = new ScrmInspectionProperties();
        weworkConfig = new WeworkConfig();
        when(environment.getProperty("scrm.ai.enabled", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("scrm.ai.base-url", "")).thenReturn("http://localhost:14000");
        service = new ScrmFinanceReadinessService(environment, providerRegistry, visionProperties,
                qixinApiConfig, inspectionProperties, weworkConfig);
    }

    @Test
    @DisplayName("默认配置：全部未就绪，每项都有可执行的 blocker")
    void defaultConfig_nothingReady() {
        Map<String, Object> result = service.readiness();

        assertThat(result.get("allReady")).isEqualTo(false);
        // 5 条外部依赖链路：AI / 证照 OCR / 工商 API / 公告抓取 / 企微通道
        // 个推已移除——无移动客户端时没有推送目标，列出来只会误导
        assertThat(result.get("totalCount")).isEqualTo(5);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) result.get("modules");
        assertThat(modules).hasSize(5);
        // 未就绪项必须都能说清缺什么
        assertThat(modules).allSatisfy(m -> {
            if (!Boolean.TRUE.equals(m.get("ready"))) {
                assertThat(String.valueOf(m.get("blocker"))).isNotBlank();
            }
        });
    }

    @Test
    @DisplayName("公告抓取：广东/浙江默认开启，应判定就绪")
    void noticeReady_byDefaultProvinces() {
        Map<String, Object> result = service.readiness();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) result.get("modules");
        Map<String, Object> notice = modules.stream()
                .filter(m -> "noticeScraping".equals(m.get("key")))
                .findFirst().orElseThrow();

        assertThat(notice.get("ready")).isEqualTo(true);
        assertThat(String.valueOf(notice.get("detail"))).contains("GD").contains("ZJ");
    }

    @Test
    @DisplayName("公告抓取：全部源关闭则不就绪")
    void noticeNotReady_whenAllSourcesOff() {
        inspectionProperties.getNotice().setGuangdongEnabled(false);
        inspectionProperties.getNotice().setZhejiangEnabled(false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) service.readiness().get("modules");
        Map<String, Object> notice = modules.stream()
                .filter(m -> "noticeScraping".equals(m.get("key")))
                .findFirst().orElseThrow();

        assertThat(notice.get("ready")).isEqualTo(false);
        assertThat(String.valueOf(notice.get("blocker"))).contains("公告源");
    }

    @Test
    @DisplayName("启信宝：开关开了但没 token → 不就绪且说明缺 token")
    void qixinNeedsToken() {
        qixinApiConfig.setEnabled(true);
        qixinApiConfig.setToken("");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) service.readiness().get("modules");
        Map<String, Object> qixin = modules.stream()
                .filter(m -> "businessInspection".equals(m.get("key")))
                .findFirst().orElseThrow();

        assertThat(qixin.get("ready")).isEqualTo(false);
        assertThat(String.valueOf(qixin.get("detail"))).contains("token");
    }

    @Test
    @DisplayName("启信宝：开关 + token 齐备 → 就绪")
    void qixinReadyWithToken() {
        qixinApiConfig.setEnabled(true);
        qixinApiConfig.setToken("some-token");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) service.readiness().get("modules");
        Map<String, Object> qixin = modules.stream()
                .filter(m -> "businessInspection".equals(m.get("key")))
                .findFirst().orElseThrow();

        assertThat(qixin.get("ready")).isEqualTo(true);
        assertThat(qixin.get("blocker")).isNull();
    }

    @Test
    @DisplayName("全部依赖就绪：allReady=true")
    void allReady_whenEverythingConfigured() {
        when(environment.getProperty("scrm.ai.enabled", Boolean.class, false)).thenReturn(true);
        when(providerRegistry.resolveImageProvider())
                .thenReturn(Optional.of(provider("http://192.168.1.150:3425", "group/sensenova-pool")));
        visionProperties.setEnabled(true);
        qixinApiConfig.setEnabled(true);
        qixinApiConfig.setToken("t");
        weworkConfig.setRealApiEnabled(true);
        weworkConfig.setMockMode(false);

        Map<String, Object> result = service.readiness();

        assertThat(result.get("allReady")).isEqualTo(true);
        assertThat(result.get("readyCount")).isEqualTo(5L);
    }

    @Test
    @DisplayName("证照 OCR：AI Provider 多模态就绪 → 判定就绪（不走本地视觉）")
    void ocrReady_whenProviderImageConfigured() {
        when(providerRegistry.resolveImageProvider())
                .thenReturn(Optional.of(provider("http://192.168.1.150:3425", "group/sensenova-pool")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) service.readiness().get("modules");
        Map<String, Object> ocr = modules.stream()
                .filter(m -> "licenseOcr".equals(m.get("key")))
                .findFirst().orElseThrow();

        assertThat(ocr.get("ready")).isEqualTo(true);
        assertThat(String.valueOf(ocr.get("detail"))).contains("group/sensenova-pool");
    }

    @Test
    @DisplayName("证照 OCR：本地视觉启用 → 就绪（Provider 无 image 时兜底）")
    void ocrReady_whenLocalVisionEnabled() {
        visionProperties.setEnabled(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) service.readiness().get("modules");
        Map<String, Object> ocr = modules.stream()
                .filter(m -> "licenseOcr".equals(m.get("key")))
                .findFirst().orElseThrow();

        assertThat(ocr.get("ready")).isEqualTo(true);
        assertThat(String.valueOf(ocr.get("detail"))).contains("视觉通道");
    }

    @Test
    @DisplayName("证照 OCR：Provider 与本地视觉都无 → 不就绪且提示系统管理配置")
    void ocrNotReady_whenNoChannel() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) service.readiness().get("modules");
        Map<String, Object> ocr = modules.stream()
                .filter(m -> "licenseOcr".equals(m.get("key")))
                .findFirst().orElseThrow();

        assertThat(ocr.get("ready")).isEqualTo(false);
        assertThat(String.valueOf(ocr.get("blocker"))).contains("AI Providers");
    }

    @Test
    @DisplayName("安全：汇总结果不含 token 明文")
    void doesNotLeakToken() {
        qixinApiConfig.setEnabled(true);
        qixinApiConfig.setToken("super-secret-qixin-token");

        String dump = service.readiness().toString();

        assertThat(dump).doesNotContain("super-secret-qixin-token");
    }

    /** 构造一个启用中的多模态 provider 实体 */
    private org.hiylo.scrm.entity.ScrmAiProviderEntity provider(String baseUrl, String model) {
        org.hiylo.scrm.entity.ScrmAiProviderEntity p =
                new org.hiylo.scrm.entity.ScrmAiProviderEntity();
        p.setProviderName("测试网关");
        p.setApiBaseUrl(baseUrl);
        p.setDefaultModel(model);
        p.setCapability("text,image");
        p.setEnabled(true);
        p.setDefaultImage(true);
        return p;
    }
}
