/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DefaultQualityAiEvaluator.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service.evaluator;

import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 质检 AI 评估器默认实现 (占位)。
 * <p>
 * 返回固定评分 80 分, 不调用真实 AI 大模型。适用于本地联调与功能验证。
 * 对接真实 AI 大模型 (如 OpenAI / 文心一言 / 通义千问) 时, 新建实现类并标注
 * {@code @Component @Primary} 即可自动替换本实现, 无需修改质检核心代码。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Component
public class DefaultQualityAiEvaluator implements QualityAiEvaluator {

    /** 默认 AI 评分 (占位) */
    private static final double DEFAULT_AI_SCORE = 80.0;

    /**
     * 对会话消息执行质检评估 (占位实现)
     *
     * @param promptTemplate 评估提示词模板
     * @param messages       会话消息列表
     * @param config         评估配置参数
     * @return 固定评分的评估结果
     */
    @Override
    public EvaluationResult evaluate(String promptTemplate,
                                     List<ScrmConversationMessageEntity> messages,
                                     Map<String, Object> config) {
        int msgCount = messages != null ? messages.size() : 0;
        String detail = "AI 评估默认实现 (未对接大模型): promptTemplate=" + promptTemplate
                + ", messages=" + msgCount + ", score=" + DEFAULT_AI_SCORE;
        log.debug("AI 评估 (默认占位): promptTemplate={}, messageCount={}", promptTemplate, msgCount);
        return new EvaluationResult(DEFAULT_AI_SCORE, detail);
    }
}
