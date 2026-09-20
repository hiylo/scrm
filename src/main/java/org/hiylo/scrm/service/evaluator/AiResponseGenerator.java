/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AiResponseGenerator.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.evaluator;

import org.hiylo.scrm.entity.ScrmAiAssistantConfigEntity;

/**
 * AI 回复生成器接口 (可插拔策略)。
 * <p>
 * 用于 {@code ScrmAiAssistantService} 的 AI 回复生成。默认实现
 * {@link DefaultAiResponseGenerator} 基于意图与情感拼接模拟回复, 不调用真实 AI API。
 * 对接真实 AI 大模型 (OpenAI / AZURE / ZHIPU / QWEN 等) 时, 新建实现类并标注
 * {@code @Component @Primary} 即可替换, 无需修改核心对话代码。
 * </p>
 * <p>
 * 设计参考 wechat-protocol 的 SignatureVerifier / HardwareSignProvider 接口化方案,
 * 以及本项目的 {@link QualityAiEvaluator}。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface AiResponseGenerator {

    /**
     * 生成 AI 回复。
     *
     * @param message        客户消息
     * @param config         AI 配置 (含 systemPrompt / model 等, 可空)
     * @param detectedIntent 识别意图
     * @param sentiment      情感 (ANGRY / NEGATIVE / NEUTRAL / POSITIVE / HAPPY)
     * @return AI 回复文本
     */
    String generate(String message, ScrmAiAssistantConfigEntity config,
                    String detectedIntent, String sentiment);
}
