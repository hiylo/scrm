/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : QualityAiEvaluator.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service.evaluator;

import org.hiylo.scrm.entity.ScrmConversationMessageEntity;

import java.util.List;
import java.util.Map;

/**
 * 质检 AI 评估器接口 (可插拔策略)。
 * <p>
 * 用于 {@code ScrmQualityInspectionService} 的 AI_EVALUATE 规则类型。
 * 默认实现 {@link DefaultQualityAiEvaluator} 返回固定评分 (80 分), 不调用真实 AI 大模型。
 * 对接真实 AI 大模型时, 新建实现类并标注 {@code @Component @Primary} 即可替换, 无需修改核心质检代码。
 * </p>
 * <p>
 * 设计参考 wechat-protocol 的 SignatureVerifier / HardwareSignProvider 接口化方案。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface QualityAiEvaluator {

    /**
     * 评估会话消息, 返回 AI 评分与说明。
     *
     * @param promptTemplate AI 提示词模板 (来自规则配置)
     * @param messages       会话消息列表 (按时间升序)
     * @param config         规则配置 (可能包含 model / temperature 等参数)
     * @return 评估结果 (score 0-100, detail 说明)
     */
    EvaluationResult evaluate(String promptTemplate,
                              List<ScrmConversationMessageEntity> messages,
                              Map<String, Object> config);

    /**
     * AI 评估结果。
     *
 * @since V1.0
     * @author Hsi Chu
     * @param score  评分 (0-100)
     * @param detail 评估说明
     */
    record EvaluationResult(double score, String detail) {
    }
}
