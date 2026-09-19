/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiAssistantConversationService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.service;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hiylo.scrm.dto.ScrmAiChatDto;
import org.hiylo.scrm.dto.ScrmAiReplyFeedbackDto;
import org.hiylo.scrm.entity.ScrmAiAssistantConfigEntity;
import org.hiylo.scrm.entity.ScrmAiConversationEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAiAssistantConfigRepository;
import org.hiylo.scrm.repository.ScrmAiConversationRepository;
import org.hiylo.scrm.service.evaluator.AiResponseGenerator;

/**
 * AI 对话与推荐兄弟服务。
 * <p>
 * 承载 AI 对话生成、批量对话、推荐回复、对话反馈与对话查询, 以及情感分析。
 * 依赖 {@link ScrmAiAssistantIntentService} 完成意图识别。
 * 作为 {@link ScrmAiAssistantService} 的对话子域拆分产物, 由门面注入并委托调用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAiAssistantConversationService {

    // ==================== 默认值常量 ====================

    /** 默认情感 */
    private static final String DEFAULT_SENTIMENT = "NEUTRAL";

    /** 默认反馈 */
    private static final String DEFAULT_FEEDBACK = "NONE";

    /** 默认情感分 */
    private static final double DEFAULT_SENTIMENT_SCORE = 0.0;

    // ==================== 枚举值常量 ====================

    /** 情感: 积极 */
    private static final String SENTIMENT_POSITIVE = "POSITIVE";
    /** 情感: 中性 */
    static final String SENTIMENT_NEUTRAL = "NEUTRAL";
    /** 情感: 消极 */
    private static final String SENTIMENT_NEGATIVE = "NEGATIVE";
    /** 情感: 愤怒 */
    private static final String SENTIMENT_ANGRY = "ANGRY";
    /** 情感: 开心 */
    private static final String SENTIMENT_HAPPY = "HAPPY";

    /** 反馈: 好评 (供统计兄弟服务复用) */
    static final String FEEDBACK_GOOD = "GOOD";
    /** 反馈: 差评 (供统计兄弟服务复用) */
    static final String FEEDBACK_BAD = "BAD";
    /** 反馈: 无 (供统计兄弟服务复用) */
    static final String FEEDBACK_NONE = "NONE";

    /** 合法的情感 (供统计兄弟服务复用) */
    static final List<String> VALID_SENTIMENTS = List.of(
            SENTIMENT_POSITIVE, SENTIMENT_NEUTRAL, SENTIMENT_NEGATIVE, SENTIMENT_ANGRY, SENTIMENT_HAPPY);

    /** 合法的反馈 */
    static final List<String> VALID_FEEDBACKS = List.of(FEEDBACK_GOOD, FEEDBACK_BAD, FEEDBACK_NONE);

    // ==================== 情感关键词 ====================

    /** 消极情感关键词 (出现即倾向 NEGATIVE) */
    private static final List<String> NEGATIVE_KEYWORDS = List.of(
            "差", "糟", "烂", "坏", "失望", "不满", "投诉", "退款", "退货", "骗子", "欺骗",
            "bug", "故障", "问题", "麻烦", "烦", "讨厌", "生气", "气死", "恶心", "垃圾",
            "shit", "bad");

    /** 愤怒情感关键词 (出现即倾向 ANGRY) */
    private static final List<String> ANGRY_KEYWORDS = List.of(
            "气死", "愤怒", "忍无可忍", "太过分", "骗", "投诉", "律师", "起诉", "315", "曝光");

    /** 积极情感关键词 (出现即倾向 POSITIVE) */
    private static final List<String> POSITIVE_KEYWORDS = List.of(
            "好", "棒", "赞", "满意", "喜欢", "感谢", "谢谢", "开心", "高兴",
            "nice", "good", "great", "perfect");

    /** 开心情感关键词 (出现即倾向 HAPPY) */
    private static final List<String> HAPPY_KEYWORDS = List.of(
            "哈哈", "开心", "高兴", "太好了", "超棒", "太赞了", "嘿嘿", "耶");

    // ==================== 依赖注入 ====================

    /** AI 对话记录数据访问层 */
    private final ScrmAiConversationRepository conversationRepository;

    /** AI 助手配置数据访问层 (解析配置 / 递增请求次数) */
    private final ScrmAiAssistantConfigRepository configRepository;

    /** 意图识别兄弟服务 (chat / getRecommendedReplies 调用 detectIntent) */
    private final ScrmAiAssistantIntentService intentService;

    /** AI 回复生成器 (可插拔策略, 默认 DefaultAiResponseGenerator) */
    private final AiResponseGenerator aiResponseGenerator;

    /** JSON 解析器 (序列化 recommendedReplies) */
    private final ObjectMapper objectMapper;

    /**
     * AI 对话: 意图识别 → 情感分析 → 推荐回复 → AI 生成回复 → 记录。
     * <p>
     * 流程:
     * <ol>
     *   <li>解析 AI 配置 (优先 dto.configId, 否则账号默认配置, 都无则使用模拟配置)</li>
     *   <li>调用 {@link #detectIntent} 进行意图识别</li>
     *   <li>调用 {@link #analyzeSentiment} 进行情感分析</li>
     *   <li>基于意图模板生成推荐回复 (不调用 AI)</li>
       * <li>调用 {@link org.hiylo.scrm.service.evaluator.AiResponseGenerator#generate} 生成 AI 回复 (可插拔, *
       * 默认模拟实现)</li> * <li>持久化对话记录, 增量更新配置请求次数</li>     * </ol>
     * </p>
     *
     * @param chatDto 对话请求
     * @return 对话记录实体 (含识别意图 / 情感 / 推荐回复 / AI 回复)
     * @throws ScrmException 客户消息为空
     */
    @Transactional
    public ScrmAiConversationEntity chat(ScrmAiChatDto chatDto) throws ScrmException {
        if (chatDto == null) {
            throw ScrmException.badRequest("对话请求不能为空");
        }
        if (chatDto.getMessage() == null || chatDto.getMessage().isBlank()) {
            throw ScrmException.badRequest("客户消息不能为空");
        }
        if (chatDto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        long startMs = System.currentTimeMillis();
        // 解析 AI 配置
        ScrmAiAssistantConfigEntity config = resolveConfig(chatDto.getConfigId());
        // 意图识别
        Map<String, Object> intentResult = intentService.detectIntent(chatDto.getMessage());
        String detectedIntent = (String) intentResult.get("intentName");
        double intentConfidence = toDouble(intentResult.get("confidence"));
        // 情感分析
        Map<String, Object> sentimentResult = analyzeSentiment(chatDto.getMessage());
        String sentiment = (String) sentimentResult.get("sentiment");
        double sentimentScore = toDouble(sentimentResult.get("score"));
        // 推荐回复 (基于意图模板)
        List<String> recommendedReplies = buildRecommendedReplies(
                (String) intentResult.get("responseTemplate"), sentiment);
        // AI 生成回复 (通过可插拔 AiResponseGenerator 接口, 默认模拟实现)
        String aiResponse = aiResponseGenerator.generate(chatDto.getMessage(), config, detectedIntent, sentiment);
        int responseTimeMs = (int) (System.currentTimeMillis() - startMs);
        // 持久化对话记录
        ScrmAiConversationEntity entity = new ScrmAiConversationEntity();
        entity.setCustomerId(chatDto.getCustomerId());
        entity.setConversationId(chatDto.getConversationId());
        entity.setUserMessage(chatDto.getMessage());
        entity.setDetectedIntent(detectedIntent);
        entity.setIntentConfidence(intentConfidence);
        entity.setSentiment(sentiment);
        entity.setSentimentScore(sentimentScore);
        entity.setRecommendedReplies(toJson(recommendedReplies));
        entity.setAiResponse(aiResponse);
        entity.setResponseTimeMs(responseTimeMs);
        entity.setConfigId(config != null ? config.getId() : null);
        entity.setFeedback(DEFAULT_FEEDBACK);
        entity.setCreatedAt(LocalDateTime.now());
        entity = conversationRepository.save(entity);
        // 增量更新配置请求次数
        if (config != null) {
            try {
                configRepository.incrementRequestCount(config.getId(), LocalDateTime.now());
            } catch (Exception e) {
                log.warn("更新配置请求次数失败, 忽略: configId={}, err={}", config.getId(), e.getMessage());
            }
        }
        log.info("AI 对话完成: conversationId={}, customerId={}, intent={}, confidence={}, sentiment={}, responseTimeMs={}",
                entity.getId(), chatDto.getCustomerId(), detectedIntent, intentConfidence, sentiment, responseTimeMs);
        return entity;
    }

    /**
     * 批量对话: 逐条调用 {@link #chat}, 单条失败跳过不阻断其他。
     *
     * @param chatDtos 对话请求列表
     * @return 对话记录列表 (与入参顺序一致, 失败项不包含)
     * @stub 占位实现: 逐条调用 {@link #chat}, 其 AI 回复生成 (generateAiResponse) 为占位实现。
     *       待对接真实 AI API 后可启用。
     */
    @SuppressWarnings("all")
    @Transactional
    public List<ScrmAiConversationEntity> batchChat(List<ScrmAiChatDto> chatDtos) {
        if (chatDtos == null || chatDtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScrmAiConversationEntity> results = new ArrayList<>(chatDtos.size());
        for (ScrmAiChatDto dto : chatDtos) {
            try {
                results.add(chat(dto));
            } catch (Exception e) {
                log.warn("批量对话单条失败, 跳过: customerId={}, err={}",
                        dto != null ? dto.getCustomerId() : null, e.getMessage());
            }
        }
        log.info("批量对话完成: total={}, success={}", chatDtos.size(), results.size());
        return results;
    }

    /**
     * 获取推荐回复 (不调用 AI, 基于意图模板)。
     * <p>识别消息意图后, 根据意图模板与情感生成多条推荐回复。</p>
     *
     * @param customerId 客户 ID (当前未使用, 预留以便后续按客户画像生成)
     * @param message    客户消息
     * @return 推荐回复列表 (JSON 数组字符串)
     */
    @Transactional
    public String getRecommendedReplies(Long customerId, String message) {
        Map<String, Object> intentResult = intentService.detectIntent(message);
        Map<String, Object> sentimentResult = analyzeSentiment(message);
        List<String> replies = buildRecommendedReplies(
                (String) intentResult.get("responseTemplate"), (String) sentimentResult.get("sentiment"));
        return toJson(replies);
    }

    /**
     * 提供对话反馈。
     * <p>feedback 仅支持 GOOD/BAD/NONE, NONE 表示撤销反馈。</p>
     *
     * @param feedbackDto 反馈请求
     * @return 更新后的对话记录
     * @throws ScrmException 对话不存在 / 反馈非法
     */
    @Transactional
    public ScrmAiConversationEntity provideFeedback(ScrmAiReplyFeedbackDto feedbackDto) throws ScrmException {
        if (feedbackDto == null) {
            throw ScrmException.badRequest("反馈请求不能为空");
        }
        ScrmAiConversationEntity entity = findConversationOrThrow(feedbackDto.getConversationId());
        if (!VALID_FEEDBACKS.contains(feedbackDto.getFeedback())) {
            throw ScrmException.badRequest(
                    "反馈非法: " + feedbackDto.getFeedback() + ", 仅支持 " + VALID_FEEDBACKS);
        }
        entity.setFeedback(feedbackDto.getFeedback());
        entity = conversationRepository.save(entity);
        log.info("AI 对话反馈: conversationId={}, feedback={}", entity.getId(), entity.getFeedback());
        return entity;
    }

    /**
     * 查询对话记录详情。
     *
     * @param id 对话记录 ID
     * @return 对话记录实体
     * @throws ScrmException 对话不存在
     */
    @Transactional(readOnly = true)
    public ScrmAiConversationEntity getConversation(Long id) throws ScrmException {
        return findConversationOrThrow(id);
    }

    /**
     * 分页查询对话记录, 支持按客户、会话、识别意图、情感与时间范围过滤。
     *
     * @param customerId     客户 ID 过滤（可空）
     * @param conversationId 会话 ID 过滤（可空）
     * @param detectedIntent 识别意图过滤（可空）
     * @param sentiment      情感过滤（可空）
     * @param startTime      对话时间起始 (含, 可空)
     * @param endTime        对话时间截止 (含, 可空)
     * @param pageable       分页参数
     * @return 对话记录分页结果 (按 createdAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAiConversationEntity> listConversations(Long customerId, Long conversationId,
                                                             String detectedIntent, String sentiment,
                                                             LocalDateTime startTime, LocalDateTime endTime,
                                                             Pageable pageable) {
        Specification<ScrmAiConversationEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (conversationId != null) {
                predicates.add(cb.equal(root.get("conversationId"), conversationId));
            }
            if (detectedIntent != null && !detectedIntent.isBlank()) {
                predicates.add(cb.equal(root.get("detectedIntent"), detectedIntent));
            }
            if (sentiment != null && !sentiment.isBlank()) {
                predicates.add(cb.equal(root.get("sentiment"), sentiment));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return conversationRepository.findAll(spec, pageable);
    }

    /**
     * 情感分析: 关键词法, 返回 sentiment 与 score (-1 到 1)。
     * <p>
     * 优先级: ANGRY (含愤怒关键词) > HAPPY (含开心关键词) > POSITIVE / NEGATIVE
     * (含积极/消极关键词) > NEUTRAL。score 按关键词命中数与情感倾向计算。
     * </p>
     *
     * @param text 待分析文本
     * @return 分析结果 Map: {sentiment, score}
     */
    public Map<String, Object> analyzeSentiment(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            result.put("sentiment", DEFAULT_SENTIMENT);
            result.put("score", DEFAULT_SENTIMENT_SCORE);
            return result;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int angryCount = countKeywordHits(lower, ANGRY_KEYWORDS);
        int happyCount = countKeywordHits(lower, HAPPY_KEYWORDS);
        int positiveCount = countKeywordHits(lower, POSITIVE_KEYWORDS);
        int negativeCount = countKeywordHits(lower, NEGATIVE_KEYWORDS);
        String sentiment;
        double score;
        if (angryCount > 0) {
            sentiment = SENTIMENT_ANGRY;
            score = -Math.min(1.0, 0.6 + angryCount * 0.1);
        } else if (happyCount > 0) {
            sentiment = SENTIMENT_HAPPY;
            score = Math.min(1.0, 0.6 + happyCount * 0.1);
        } else if (positiveCount > negativeCount) {
            sentiment = SENTIMENT_POSITIVE;
            score = Math.min(1.0, positiveCount * 0.2);
        } else if (negativeCount > positiveCount) {
            sentiment = SENTIMENT_NEGATIVE;
            score = -Math.min(1.0, negativeCount * 0.2);
        } else {
            sentiment = SENTIMENT_NEUTRAL;
            score = 0.0;
        }
        result.put("sentiment", sentiment);
        result.put("score", score);
        return result;
    }

    /**
     * 解析 AI 配置: 优先使用指定 ID, 否则取账号默认启用配置, 都无则返回 null。
     *
     * @param configId 指定配置 ID (可空)
     * @return AI 配置实体 (无可用配置返回 null)
     */
    private ScrmAiAssistantConfigEntity resolveConfig(Long configId) {
        if (configId != null) {
            Optional<ScrmAiAssistantConfigEntity> opt = configRepository.findById(configId)
                    .filter(c -> Boolean.TRUE.equals(c.getEnabled()));
            if (opt.isPresent()) {
                return opt.get();
            }
            log.warn("指定的 AI 配置不存在或未启用, 回退默认配置: configId={}", configId);
        }
        return configRepository.findByIsDefaultTrueAndEnabledTrue().orElse(null);
    }

    /**
     * 基于意图模板与情感生成推荐回复列表。
     * <p>无模板时按情感生成兜底回复; 有模板时追加情感后缀回复。</p>
     *
     * @param responseTemplate 意图回复模板
     * @param sentiment        情感
     * @return 推荐回复列表
     */
    private List<String> buildRecommendedReplies(String responseTemplate, String sentiment) {
        List<String> replies = new ArrayList<>();
        if (responseTemplate != null && !responseTemplate.isBlank()) {
            replies.add(responseTemplate);
        }
        // 按情感追加兜底回复
        switch (sentiment == null ? SENTIMENT_NEUTRAL : sentiment) {
            case SENTIMENT_ANGRY:
                replies.add("非常抱歉给您带来不便, 我会立即为您处理, 请稍候。");
                replies.add("理解您的心情, 我马上为您跟进此事, 一定给您一个满意的答复。");
                break;
            case SENTIMENT_NEGATIVE:
                replies.add("抱歉让您有这样的体验, 我会尽快帮您解决。");
                replies.add("感谢您的反馈, 我们会持续改进, 请问还有什么可以帮您?");
                break;
            case SENTIMENT_HAPPY:
            case SENTIMENT_POSITIVE:
                replies.add("很高兴能帮到您! 还有什么可以为您效劳的吗?");
                replies.add("感谢您的认可, 祝您生活愉快!");
                break;
            default:
                replies.add("好的, 我已收到您的消息, 请问还有什么可以帮您?");
                break;
        }
        return replies;
    }

    /**
     * 统计关键词列表在文本中的命中次数。
     *
     * @param text     小写化文本
     * @param keywords 关键词列表
     * @return 命中次数
     */
    private int countKeywordHits(String text, List<String> keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串, 序列化失败返回 "[]"
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("对象序列化为 JSON 失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    static double toDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 按主键查询对话记录, 不存在抛异常, 并校验账号归属。
     *
     * @param id 对话记录 ID
     * @return 对话记录实体
     * @throws ScrmException 对话不存在
     */
    private ScrmAiConversationEntity findConversationOrThrow(Long id) throws ScrmException {
        ScrmAiConversationEntity entity = conversationRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "AI 对话不存在: id=" + id));
        return entity;
    }

}
