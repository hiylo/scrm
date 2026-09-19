/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DefaultAiResponseGenerator.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service.evaluator;

import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmAiAssistantConfigEntity;
import org.springframework.stereotype.Component;

/**
 * AI 回复生成器默认实现 (占位)。
 * <p>
 * 基于意图与情感拼接模拟回复, 不调用真实 AI API。适用于本地联调与功能验证。
 * 对接真实 AI 大模型 (OpenAI / AZURE / ZHIPU / QWEN 等) 时, 新建实现类并标注
 * {@code @Component @Primary} 即可自动替换本实现, 无需修改对话核心代码。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
public class DefaultAiResponseGenerator implements AiResponseGenerator {

    /** 情感取值: NEUTRAL 中性 (未识别到明显情感) */
    private static final String SENTIMENT_NEUTRAL = "NEUTRAL";
    /** 情感取值: ANGRY 愤怒 */
    private static final String SENTIMENT_ANGRY = "ANGRY";
    /** 情感取值: NEGATIVE 负面 */
    private static final String SENTIMENT_NEGATIVE = "NEGATIVE";
    /** 情感取值: HAPPY 开心 */
    private static final String SENTIMENT_HAPPY = "HAPPY";
    /** 情感取值: POSITIVE 正面 */
    private static final String SENTIMENT_POSITIVE = "POSITIVE";

    /**
     * 生成 AI 回复 (占位实现)
     *
     * @param message        用户输入消息
     * @param config         对话配置实体
     * @param detectedIntent 已识别的用户意图
     * @param sentiment      用户情感分析结果
     * @return 生成的回复文本
     */
    @Override
    public String generate(String message, ScrmAiAssistantConfigEntity config,
                           String detectedIntent, String sentiment) {
        log.debug("AI 回复生成 (默认占位): intent={}, sentiment={}", detectedIntent, sentiment);
        StringBuilder sb = new StringBuilder();
        sb.append("[AI 模拟回复]");
        sb.append("已识别您的意图: ").append(detectedIntent).append(", 情感: ").append(sentiment).append("。");
        sb.append("针对您提到的「").append(truncate(message, 80)).append("」, ");
        switch (sentiment == null ? SENTIMENT_NEUTRAL : sentiment) {
            case SENTIMENT_ANGRY:
                sb.append("非常抱歉给您带来困扰, 我会优先为您处理。");
                break;
            case SENTIMENT_NEGATIVE:
                sb.append("抱歉让您有这样的体验, 我会尽快为您解决。");
                break;
            case SENTIMENT_HAPPY:
                sb.append("很高兴能为您服务!");
                break;
            case SENTIMENT_POSITIVE:
                sb.append("感谢您的认可, 我们会继续努力!");
                break;
            default:
                sb.append("我已记录您的问题, 稍后会进一步跟进。");
                break;
        }
        return sb.toString();
    }

    /**
     * 截断字符串用于日志输出, 避免日志过长。
     *
     * @param text   原始文本
     * @param maxLen 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
