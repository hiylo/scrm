/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AiChatClientTest.java
 * Date : 2026/10/27 16:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AiChatClient} 单元测试。
 * <p>
 * 重点锁住模型名注入行为。历史问题：{@code scrm.ai.model} 配置项存在但从未传入请求，
 * 请求体模型名落到 DTO 硬编码的 {@code gpt-4o-mini}，Magpie 网关不认识该名字返回 404，
 * 异常又被 {@code AiChatClient} 吞成降级响应，最终表现为「AI 服务不可用」并连续重试 3 次。
 * </p>
 * <p>
 * 本测试不发真实 HTTP（网络异常同样会被降级吞掉），只验证「请求体最终带的是哪个模型名」——
 * 通过降级响应之外的路径无法观察，因此断言 {@link AiChatClient#getDefaultModel()} 与
 * 配置注入的一致性。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("AiChatClient：模型名注入")
class AiChatClientTest {

    /** 构造一个未指定模型的客户端（其余参数取测试常用值） */
    private AiChatClient clientWithModel(String model) {
        return new AiChatClient("http://localhost:1", false, "/v1/chat/completions",
                "", model, false, 1_000, 1_000);
    }

    @Test
    @DisplayName("配置了模型名时使用配置值，而不是 DTO 默认的 gpt-4o-mini")
    void usesConfiguredModel() {
        AiChatClient client = clientWithModel("group/sensenova-pool");

        assertThat(client.getDefaultModel()).isEqualTo("group/sensenova-pool");
    }

    @Test
    @DisplayName("模型名留空时回退到默认值，避免发空模型名给网关")
    void fallsBackWhenModelBlank() {
        assertThat(clientWithModel("   ").getDefaultModel()).isEqualTo(AiChatClient.DEFAULT_MODEL);
        assertThat(clientWithModel(null).getDefaultModel()).isEqualTo(AiChatClient.DEFAULT_MODEL);
    }

    @Test
    @DisplayName("请求 DTO 的 model 默认是 null：表示未指定，交由客户端注入")
    void requestModelDefaultsToNull() {
        assertThat(new AiChatRequest().getModel()).isNull();
    }

    @Test
    @DisplayName("未启用时返回降级响应，不发起网络请求")
    void disabledReturnsFallback() {
        AiChatClient client = clientWithModel("group/sensenova-pool");

        AiChatResponse response = client.chatCompletion(new AiChatRequest());

        assertThat(response.getFirstContent()).isEqualTo(AiChatClient.FALLBACK_CONTENT);
    }
}
