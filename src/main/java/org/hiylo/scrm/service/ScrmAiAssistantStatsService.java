/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiAssistantStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.service;


import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hiylo.scrm.repository.ScrmAiConversationRepository;

import static org.hiylo.scrm.service.ScrmAiAssistantConversationService.FEEDBACK_BAD;
import static org.hiylo.scrm.service.ScrmAiAssistantConversationService.FEEDBACK_GOOD;
import static org.hiylo.scrm.service.ScrmAiAssistantConversationService.FEEDBACK_NONE;
import static org.hiylo.scrm.service.ScrmAiAssistantConversationService.SENTIMENT_NEUTRAL;
import static org.hiylo.scrm.service.ScrmAiAssistantConversationService.VALID_SENTIMENTS;

/**
 * AI 助手统计兄弟服务。
 * <p>
 * 承载 AI 助手统计概览、意图统计与反馈统计。
 * 作为 {@link ScrmAiAssistantService} 的统计子域拆分产物, 由门面注入并委托调用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAiAssistantStatsService {

    // ==================== 依赖注入 ====================

    /** AI 对话记录数据访问层 */
    private final ScrmAiConversationRepository conversationRepository;

    /**
     * AI 助手统计概览: 对话数、意图分布、情感分布、平均置信度、反馈率。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAssistantStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 对话总数
        long total = conversationRepository.count();
        stats.put("totalConversations", total);
        // 意图分布
        List<Object[]> byIntent = conversationRepository.countByDetectedIntent(startTime, endTime);
        Map<String, Long> intentCount = new LinkedHashMap<>();
        for (Object[] row : byIntent) {
            String intent = row[0] == null ? "UNKNOWN" : (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            intentCount.put(intent, count);
        }
        stats.put("intentDistribution", intentCount);
        // 情感分布
        List<Object[]> bySentiment = conversationRepository.countBySentiment(startTime, endTime);
        Map<String, Long> sentimentCount = new LinkedHashMap<>();
        for (String s : VALID_SENTIMENTS) {
            sentimentCount.put(s, 0L);
        }
        long sentimentTotal = 0L;
        for (Object[] row : bySentiment) {
            String sentiment = row[0] == null ? SENTIMENT_NEUTRAL : (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            sentimentCount.put(sentiment, count);
            sentimentTotal += count;
        }
        stats.put("sentimentDistribution", sentimentCount);
        stats.put("sentimentTotal", sentimentTotal);
        // 平均置信度
        Double avgConfidence = conversationRepository.averageIntentConfidence(startTime, endTime);
        stats.put("averageIntentConfidence", avgConfidence != null ? avgConfidence : 0.0);
        // 反馈统计
        Map<String, Object> feedbackStats = buildFeedbackStats(startTime, endTime);
        stats.put("feedbackStats", feedbackStats);
        return stats;
    }

    /**
     * 意图统计: 按识别意图聚合对话数。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 意图统计 Map {intent -> count}
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getIntentStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = conversationRepository.countByDetectedIntent(startTime, endTime);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String intent = row[0] == null ? "UNKNOWN" : (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            result.put(intent, count);
        }
        return result;
    }

    /**
     * 反馈统计: 好评率 / 差评率。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 反馈统计 Map {total, good, bad, goodRate, badRate}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFeedbackStats(LocalDateTime startTime, LocalDateTime endTime) {
        return buildFeedbackStats(startTime, endTime);
    }

    /**
     * 构建反馈统计: total / good / bad / goodRate / badRate。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 反馈统计 Map
     */
    private Map<String, Object> buildFeedbackStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = conversationRepository.countByFeedback(startTime, endTime);
        long good = 0L;
        long bad = 0L;
        long total = 0L;
        for (Object[] row : rows) {
            String feedback = row[0] == null ? FEEDBACK_NONE : (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            if (FEEDBACK_GOOD.equals(feedback)) {
                good = count;
            } else if (FEEDBACK_BAD.equals(feedback)) {
                bad = count;
            }
            total += count;
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("good", good);
        stats.put("bad", bad);
        stats.put("goodRate", total == 0 ? 0.0 : (double) good / total);
        stats.put("badRate", total == 0 ? 0.0 : (double) bad / total);
        return stats;
    }

}
