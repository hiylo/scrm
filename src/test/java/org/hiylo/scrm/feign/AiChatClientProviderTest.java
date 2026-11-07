/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : AiChatClientProviderTest.java
 * Date : 2026/11/07 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AiChatClient#fromProvider} Provider 动态构建单元测试。
 * <p>校验：Provider 配置（base-url / model / capability）正确映射到客户端。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("AiChatClient：按 Provider 动态构建")
class AiChatClientProviderTest {

    /** 构造 Provider 实体 */
    private ScrmAiProviderEntity provider(String baseUrl, String model, String capability,
                                          String apiKey) {
        ScrmAiProviderEntity p = new ScrmAiProviderEntity();
        p.setProviderName("测试网关");
        p.setApiBaseUrl(baseUrl);
        p.setChatPath("/v1/chat/completions");
        p.setDefaultModel(model);
        p.setCapability(capability);
        p.setApiKey(apiKey);
        p.setTemperature(0.3);
        p.setMaxTokens(800);
        p.setEnabled(true);
        p.setDefaultText(true);
        p.setDefaultImage(true);
        return p;
    }

    @Test
    @DisplayName("Provider 的默认模型映射到客户端（不走 DTO 默认 gpt-4o-mini）")
    void fromProvider_usesProviderModel() {
        ScrmAiProviderEntity p = provider("http://192.168.1.150:3425", "group/sensenova-pool",
                "text,image", "sk-test");
        AiChatClient client = AiChatClient.fromProvider(p);

        assertThat(client.getDefaultModel()).isEqualTo("group/sensenova-pool");
    }

    @Test
    @DisplayName("具备 text 能力且 base-url 有效 → 客户端启用")
    void fromProvider_enabledWhenTextCapability() {
        ScrmAiProviderEntity p = provider("http://192.168.1.150:3425", null, "text,image", null);
        AiChatClient client = AiChatClient.fromProvider(p);

        assertThat(client.getDefaultModel()).isEqualTo(AiChatClient.DEFAULT_MODEL);
    }

    @Test
    @DisplayName("纯视觉 provider（无 text 能力）不参与文本对话（客户端按未启用处理）")
    void fromProvider_disabledWhenNoTextCapability() {
        ScrmAiProviderEntity p = provider("http://192.168.1.150:3425", "qwen3vl-4b", "image", null);
        AiChatClient client = AiChatClient.fromProvider(p);

        // 未启用时 chatCompletion 返回降级响应
        assertThat(client.chatCompletion(null).getFirstContent())
                .isEqualTo(AiChatClient.FALLBACK_CONTENT);
    }

    @Test
    @DisplayName("base-url 尾部斜杠被规范化（避免拼接出双斜杠）")
    void fromProvider_trimsTrailingSlash() {
        ScrmAiProviderEntity p = provider("http://192.168.1.150:3425/", null, "text", null);
        AiChatClient client = AiChatClient.fromProvider(p);

        assertThat(client.getDefaultModel()).isNotBlank();
    }
}