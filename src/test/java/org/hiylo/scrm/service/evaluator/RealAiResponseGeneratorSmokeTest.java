/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RealAiResponseGeneratorSmokeTest.java
 * Date : 2026/10/08 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.evaluator;

import org.hiylo.scrm.feign.AiChatClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RealAiResponseGenerator} 真实 LLM 冒烟测试 (默认不执行)。
 * <p>
 * 通过环境变量 {@code SCRM_SMOKE_AI_URL} / {@code SCRM_SMOKE_AI_KEY} /
 * {@code SCRM_SMOKE_AI_MODEL} 显式开启, 验证:
 * <ul>
 *   <li>AiChatClient 以 Bearer 鉴权调用 OpenAI 兼容端点</li>
 *   <li>RealAiResponseGenerator 返回真实模型回复 (而非降级/模拟)</li>
 * </ul>
 * 未设置上述环境变量时本测试自动跳过, 不影响常规测试基线 (1625 绿)。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("RealAiResponseGenerator 真实 LLM 冒烟 (需环境变量显式开启)")
class RealAiResponseGeneratorSmokeTest {

    /**
     * 端到端调用真实 LLM 生成回复。
     * <p>使用 SCRM_SMOKE_AI_* 环境变量构造 AiChatClient 与生成器,
     * 断言返回内容非空且不含模拟/降级标记。</p>
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "SCRM_SMOKE_AI_URL", matches = ".+")
    @DisplayName("smoke: 真实调用 LLM 返回非降级回复")
    void smoke_realLlmReply() {
        String rawUrl = System.getenv("SCRM_SMOKE_AI_URL");
        // STARBURST_LLM_URL 可能已含 /v1 前缀; 剥离后作为裸 host 传入, 由 AiChatClient 拼接 chat-path
        String baseUrl = rawUrl.replaceAll("/v1/?$", "");
        String apiKey = System.getenv("SCRM_SMOKE_AI_KEY");
        String model = System.getenv().getOrDefault("SCRM_SMOKE_AI_MODEL", "intern-deepseek-v4-flash-0731");

        AiChatClient client = new AiChatClient(baseUrl, true, "/v1/chat/completions", apiKey);
        RealAiResponseGenerator generator = new RealAiResponseGenerator(
                client, new DefaultAiResponseGenerator(), model, 0.7, 200);

        String reply = generator.generate(
                "请用一句话介绍你自己", null, "GREETING", "NEUTRAL");

        assertThat(reply)
                .as("真实 LLM 回复不应为空")
                .isNotBlank();
        assertThat(reply)
                .as("真实 LLM 回复不应为降级/模拟内容")
                .doesNotContain(AiChatClient.FALLBACK_CONTENT)
                .doesNotContain("[AI 模拟回复]");
        System.out.println("SMOKE_REPLY: " + reply);
    }
}
