/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmMarketingBatchGenerationServiceTest.java
 * Date : 2026/10/23 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.config.ScrmMarketingBatchProperties;
import org.hiylo.scrm.dto.ScrmMarketingBatchGenerateDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.feign.AiChatClient;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;
import org.hiylo.scrm.service.ScrmMessageTemplateCenterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmMarketingBatchGenerationService} 单元测试。
 * <p>
 * 覆盖: 主路径 (AI 返回合法 JSON 数组 → 合规校验 → 返回通过变体)、
 * 降级 (AI 不可用)、输出非法 JSON、全部变体不合规、模板不存在、占位符注入。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmMarketingBatchGenerationService 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScrmMarketingBatchGenerationServiceTest {

    /** 模板中心服务 Mock */
    @Mock
    private ScrmMessageTemplateCenterService templateCenterService;

    /** AI 对话客户端 Mock */
    @Mock
    private AiChatClient aiChatClient;

    /** 提示词资源加载器 Mock */
    @Mock
    private ResourceLoader resourceLoader;

    /** 被测对象 */
    private ScrmMarketingBatchGenerationService service;

    /**
     * 测试前装配被测对象 (默认配置 + 主指令资源)。
     */
    @BeforeEach
    void setUp() throws Exception {
        ScrmMarketingBatchProperties properties = new ScrmMarketingBatchProperties();
        MarketingComplianceValidator validator = new MarketingComplianceValidator(properties);
        when(resourceLoader.getResource(anyString()))
                .thenReturn(new ByteArrayResource(PROMPT.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        service = new ScrmMarketingBatchGenerationService(
                templateCenterService, aiChatClient, new ObjectMapper(), resourceLoader, properties, validator);
    }

    /** 精简主指令 (含全部提示词占位符, 供注入断言) */
    private static final String PROMPT = "批量生成 {变体数量} 套。场景:{场景} 标题:{模板标题} "
            + "正文:{模板正文} 参数:{占位符 JSON：客户类型/服务内容/活动信息/期限 等} "
            + "语气:{语气档位} 字数:{字数上限}/{渠道字数上限} 渠道:{渠道：企微/微信/短信/邮件}";

    /**
     * 构造 V12 财税类型模板实体 (TEXT, 企微渠道)。
     */
    private ScrmMessageTemplateCenterEntity buildTemplate() {
        ScrmMessageTemplateCenterEntity t = new ScrmMessageTemplateCenterEntity();
        t.setId(90070101L);
        t.setTemplateCode("TAX_MKT_OPENING_001");
        t.setTemplateName("新办企业开业套餐推广");
        t.setTemplateType("TEXT");
        t.setChannels("WORK_WECHAT,WECHAT");
        t.setCategory("开业推广");
        t.setContent("恭喜开业！{{客户类型}}办理「{{服务内容}}」一站式落地。{{期限}}前签约享礼包。");
        return t;
    }

    /**
     * 构造带参数的生成请求。
     */
    private ScrmMarketingBatchGenerateDto buildDto() {
        ScrmMarketingBatchGenerateDto dto = new ScrmMarketingBatchGenerateDto();
        dto.setTemplateCode("TAX_MKT_OPENING_001");
        Map<String, Object> variables = new HashMap<>();
        variables.put("客户类型", "初创公司");
        variables.put("服务内容", "工商注册+代理记账");
        variables.put("期限", "2026年11月30日前");
        dto.setVariables(variables);
        dto.setTone("亲切自然");
        dto.setVariantCount(2);
        dto.setChannel("WORK_WECHAT");
        return dto;
    }

    /**
     * 构造 AI 成功响应 (含 2 个合法变体)。
     */
    private AiChatResponse buildAiResponse(String content) {
        AiChatResponse response = new AiChatResponse();
        response.setId("resp_1");
        response.setModel("gpt-4o-mini");
        AiChatResponse.AiChatChoice choice = new AiChatResponse.AiChatChoice();
        choice.setMessage(new AiChatRequest.AiChatMessage("assistant", content));
        choice.setFinishReason("stop");
        response.setChoices(Collections.singletonList(choice));
        return response;
    }

    /** 合法 AI 输出 (2 个变体) */
    private static final String OK_JSON =
            "[{\"title\":\"开业贺礼A\",\"content\":\"恭喜开业，初创公司办理工商注册+代理记账一站式落地，详情咨询财税顾问，具体以合同为准。\",\"tone\":\"亲切自然\",\"charCount\":36,\"riskNotes\":[]},"
            + "{\"title\":\"开业贺礼B\",\"content\":\"为您的新公司送上开业祝福，代理记账业务欢迎咨询，以官方政策为准。\",\"tone\":\"亲切自然\",\"charCount\":32,\"riskNotes\":[]}]";

    @Test
    @DisplayName("主路径: AI 返回合法 JSON, 合规通过项返回且数量正确")
    void batchGenerate_success() throws Exception {
        when(templateCenterService.getTemplateByCode("TAX_MKT_OPENING_001")).thenReturn(buildTemplate());
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenReturn(buildAiResponse(OK_JSON));

        Map<String, Object> result = service.batchGenerate(buildDto());

        assertThat(result.get("error")).isNull();
        assertThat(result.get("templateCode")).isEqualTo("TAX_MKT_OPENING_001");
        assertThat(result.get("channel")).isEqualTo("WORK_WECHAT");
        assertThat((List<?>) result.get("variants")).hasSize(2);
        assertThat((List<?>) result.get("warnings")).isEmpty();
    }

    @Test
    @DisplayName("提示词注入: 模板/占位符/语气/字数/渠道/变体数量全部替换")
    void batchGenerate_promptInjection() throws Exception {
        when(templateCenterService.getTemplateByCode("TAX_MKT_OPENING_001")).thenReturn(buildTemplate());
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenReturn(buildAiResponse(OK_JSON));

        service.batchGenerate(buildDto());

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiChatClient, times(1)).chatCompletion(captor.capture());
        AiChatRequest request = captor.getValue();
        String user = request.getMessages().get(1).getContent();
        assertThat(user).contains("批量生成 2 套");
        assertThat(user).contains("场景:开业推广");
        assertThat(user).contains("标题:新办企业开业套餐推广");
        assertThat(user).contains("正文:恭喜开业");
        assertThat(user).contains("\"客户类型\":\"初创公司\"");
        assertThat(user).contains("语气:亲切自然");
        assertThat(user).contains("字数:120/120");
        assertThat(user).contains("渠道:企微");
        assertThat(user).doesNotContain("{模板正文}");
        assertThat(user).doesNotContain("{占位符");
    }

    @Test
    @DisplayName("降级: AI 不可用 (降级内容) 返回 error, 不产生变体")
    void batchGenerate_aiFallback() throws Exception {
        when(templateCenterService.getTemplateByCode("TAX_MKT_OPENING_001")).thenReturn(buildTemplate());
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenReturn(buildAiResponse(AiChatClient.FALLBACK_CONTENT));

        Map<String, Object> result = service.batchGenerate(buildDto());

        assertThat(result.get("error")).isNotNull();
        assertThat((List<?>) result.get("variants")).isEmpty();
    }

    @Test
    @DisplayName("输出非法 JSON: 返回 error, 不产生变体")
    void batchGenerate_invalidJson() throws Exception {
        when(templateCenterService.getTemplateByCode("TAX_MKT_OPENING_001")).thenReturn(buildTemplate());
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenReturn(buildAiResponse("这不是 JSON"));

        Map<String, Object> result = service.batchGenerate(buildDto());

        assertThat(result.get("error")).isNotNull();
        assertThat((List<?>) result.get("variants")).isEmpty();
    }

    @Test
    @DisplayName("全部变体不合规 (残留占位符): 打回并给出 warnings")
    void batchGenerate_allViolated() throws Exception {
        when(templateCenterService.getTemplateByCode("TAX_MKT_OPENING_001")).thenReturn(buildTemplate());
        String bad = "[{\"title\":\"坏变体\",\"content\":\"恭喜{{客户类型}}开业，保证省钱！\",\"tone\":\"g\",\"charCount\":10,\"riskNotes\":[]}]";
        when(aiChatClient.chatCompletion(any(AiChatRequest.class)))
                .thenReturn(buildAiResponse(bad));

        Map<String, Object> result = service.batchGenerate(buildDto());

        assertThat(result.get("error")).isNotNull();
        assertThat((List<?>) result.get("variants")).isEmpty();
        assertThat((List<?>) result.get("warnings")).isNotEmpty();
    }

    @Test
    @DisplayName("模板不存在: 抛 ScrmException NOT_FOUND")
    void batchGenerate_templateNotFound() {
        when(templateCenterService.getTemplateByCode("NOPE"))
                .thenThrow(ScrmException.notFound("消息模板不存在"));

        ScrmMarketingBatchGenerateDto dto = new ScrmMarketingBatchGenerateDto();
        dto.setTemplateCode("NOPE");
        Map<String, Object> vars = new HashMap<>();
        vars.put("客户类型", "x");
        dto.setVariables(vars);

        assertThatThrownBy(() -> service.batchGenerate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息模板不存在");
    }
}