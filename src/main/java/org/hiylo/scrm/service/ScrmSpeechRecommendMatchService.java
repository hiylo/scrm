/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechRecommendMatchService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.service;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hiylo.scrm.dto.ScrmSpeechFeedbackDto;
import org.hiylo.scrm.dto.ScrmSpeechRecommendRequestDto;
import org.hiylo.scrm.entity.ScrmSalesSpeechEntity;
import org.hiylo.scrm.entity.ScrmSpeechRecommendationEntity;
import org.hiylo.scrm.entity.ScrmSpeechScenarioEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmSalesSpeechRepository;
import org.hiylo.scrm.repository.ScrmSpeechRecommendationRepository;
import org.hiylo.scrm.repository.ScrmSpeechScenarioRepository;

/**
 * 话术推荐匹配兄弟服务。
 * <p>
 * 承载话术推荐、批量推荐、推荐记录查询与反馈回写, 以及场景匹配、话术评分排序。
 * 依赖 {@link ScrmSpeechService} 与 {@link ScrmSpeechScenarioService} 完成评分与统计联动。
 * 作为 {@link ScrmSpeechRecommendService} 的推荐匹配子域拆分产物, 由门面注入并委托调用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSpeechRecommendMatchService {

    // ==================== 默认值常量 ====================

    /** 默认推荐话术数 */
    static final int DEFAULT_RECOMMEND_LIMIT = 5;

    /** 最大推荐话术数 */
    static final int MAX_RECOMMEND_LIMIT = 20;

    // ==================== 枚举值常量 ====================

    /** 合法的反馈 */
    static final List<String> VALID_FEEDBACKS = List.of("POSITIVE", "NEGATIVE", "NEUTRAL");

    /** 合法的使用结果 */
    static final List<String> VALID_OUTCOMES = List.of("SUCCESS", "PARTIAL", "FAILURE", "NOT_USED");

    // ==================== 评分常量 ====================

    /** 情感 → 偏好话术风格映射 */
    private static final Map<String, String> SENTIMENT_STYLE_MAP = Map.of(
            "POSITIVE", "FRIENDLY",
            "NEUTRAL", "PROFESSIONAL",
            "NEGATIVE", "EMPATHETIC");

    /** 评分权重: 评分 */
    private static final double WEIGHT_RATING = 2.0;

    /** 评分权重: 成功率 */
    private static final double WEIGHT_SUCCESS_RATE = 20.0;

    /** 评分权重: 关键词匹配 (单关键词) */
    private static final double WEIGHT_KEYWORD_MATCH = 10.0;

    /** 评分权重: 风格匹配 */
    private static final double WEIGHT_STYLE_MATCH = 15.0;

    /** 评分权重: 推荐加分 */
    private static final double BONUS_RECOMMENDED = 10.0;

    /** 评分权重: 验证加分 */
    private static final double BONUS_VERIFIED = 5.0;

    // ==================== 依赖注入 ====================

    /** 场景数据访问层 */
    private final ScrmSpeechScenarioRepository scenarioRepository;

    /** 话术数据访问层 */
    private final ScrmSalesSpeechRepository speechRepository;

    /** 推荐记录数据访问层 */
    private final ScrmSpeechRecommendationRepository recommendationRepository;

    /** Jackson ObjectMapper, 由 Spring Boot 自动注入, 用于解析 JSON */
    private final ObjectMapper objectMapper;

    /** 话术管理兄弟服务 (评分 / 反馈回写) */
    private final ScrmSpeechService speechService;

    /** 场景管理兄弟服务 (反馈后同步场景统计) */
    private final ScrmSpeechScenarioService scenarioService;

    /**
     * 推荐话术 (匹配场景 → 筛选话术 → 评分排序 → 持久化推荐记录)。
     * <p>完整实现: 按场景编码定位场景, 加载场景下启用话术, 调用 {@link #rankSpeeches}
     * 评分排序取 Top N, 构建匹配上下文与匹配原因, 持久化推荐记录。</p>
     *
     * @param requestDto 推荐请求
     * @return 推荐记录
     * @throws ScrmException 场景不存在 / 无可用话术
     */
    @Transactional
    public ScrmSpeechRecommendationEntity recommend(
            ScrmSpeechRecommendRequestDto requestDto) throws ScrmException {
        if (requestDto == null) {
            throw ScrmException.badRequest("推荐请求不能为空");
        }
        if (requestDto.getScenarioCode() == null || requestDto.getScenarioCode().isBlank()) {
            throw ScrmException.badRequest("场景编码不能为空");
        }
        ScrmSpeechScenarioEntity scenario = scenarioRepository
                .findByScenarioCode(requestDto.getScenarioCode())
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "话术场景不存在: code=" + requestDto.getScenarioCode()));
        if (Boolean.FALSE.equals(scenario.getEnabled())) {
            throw ScrmException.conflict("话术场景已禁用: code=" + requestDto.getScenarioCode());
        }
        // 加载场景下启用话术
        List<ScrmSalesSpeechEntity> speeches = speechRepository
                .findByScenarioIdAndEnabledOrderByRatingDesc(scenario.getId(), Boolean.TRUE);
        if (speeches.isEmpty()) {
            throw ScrmException.badRequest("场景下无可用话术: scenarioId=" + scenario.getId());
        }
        // 构建匹配上下文
        Map<String, Object> contextMap = buildContextMap(requestDto.getContext(), scenario);
        // 评分排序
        List<Map<String, Object>> ranked = rankSpeechesInternal(speeches, contextMap, DEFAULT_RECOMMEND_LIMIT);
        List<Long> topIds = ranked.stream()
                .map(m -> (Long) m.get("speechId"))
                .collect(Collectors.toList());
        List<String> reasons = ranked.stream()
                .map(m -> String.valueOf(m.get("reason")))
                .collect(Collectors.toList());
        double topScore = ranked.isEmpty() ? 0.0 : toDouble(ranked.get(0).get("score"));
        // 持久化推荐记录
        ScrmSpeechRecommendationEntity entity = new ScrmSpeechRecommendationEntity();
        entity.setCustomerId(requestDto.getCustomerId());
        entity.setCustomerName(requestDto.getCustomerName());
        entity.setScenarioId(scenario.getId());
        entity.setScenarioName(scenario.getScenarioName());
        entity.setRecommendedSpeechIds(topIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        entity.setMatchContext(toJson(contextMap));
        entity.setMatchScore(Math.round(topScore * 100d) / 100d);
        entity.setMatchReasons(String.join(";", reasons));
        entity.setRecommendedAt(LocalDateTime.now());
        entity.setRecommendedBy(requestDto.getRecommendedBy());
        entity.setRecommendedByName(requestDto.getRecommendedByName());
        entity = recommendationRepository.save(entity);
        log.info("推荐话术: recommendationId={}, scenarioId={}, customerId={}, speechCount={}, topScore={}",
                entity.getId(), scenario.getId(), requestDto.getCustomerId(), topIds.size(), topScore);
        return entity;
    }

    /**
     * 批量推荐话术。
     *
     * @param requests 推荐请求列表
     * @return 推荐记录列表
     * @throws ScrmException 单条推荐失败时抛出
     */
    @Transactional
    public List<ScrmSpeechRecommendationEntity> batchRecommend(List<ScrmSpeechRecommendRequestDto> requests)
            throws ScrmException {
        if (requests == null || requests.isEmpty()) {
            throw ScrmException.badRequest("推荐请求列表不能为空");
        }
        List<ScrmSpeechRecommendationEntity> results = new ArrayList<>();
        for (ScrmSpeechRecommendRequestDto req : requests) {
            results.add(recommend(req));
        }
        log.info("批量推荐话术: count={}", results.size());
        return results;
    }

    /**
     * 查询推荐记录详情。
     *
     * @param id 推荐 ID
     * @return 推荐记录
     * @throws ScrmException 推荐记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmSpeechRecommendationEntity getRecommendation(Long id) throws ScrmException {
        return findRecommendationOrThrow(id);
    }

    /**
     * 分页查询推荐记录, 支持按客户 / 场景 / 时间范围过滤。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param scenarioId 场景 ID 过滤（可空）
     * @param startTime  推荐时间起始 (含, 可空)
     * @param endTime    推荐时间截止 (含, 可空)
     * @param pageable   分页参数
     * @return 推荐记录分页结果 (按 recommendedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmSpeechRecommendationEntity> listRecommendations(Long customerId, Long scenarioId,
                                                                     LocalDateTime startTime, LocalDateTime endTime,
                                                                     Pageable pageable) {
        Specification<ScrmSpeechRecommendationEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (scenarioId != null) {
                predicates.add(cb.equal(root.get("scenarioId"), scenarioId));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("recommendedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("recommendedAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("recommendedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return recommendationRepository.findAll(spec, pageable);
    }

    /**
     * 提供反馈 (更新推荐记录, 同步话术使用与反馈统计)。
     * <p>更新推荐记录的 selectedSpeechId / feedback / outcome / comment / usedAt;
     * 调用 incrementUsage 累计话术使用次数与成功次数;
     * 调用 incrementFeedback 累计话术反馈统计。</p>
     *
     * @param feedbackDto 反馈参数
     * @return 更新后的推荐记录
     * @throws ScrmException 推荐记录不存在 / 话术不存在 / 参数非法
     */
    @Transactional
    public ScrmSpeechRecommendationEntity provideFeedback(ScrmSpeechFeedbackDto feedbackDto) throws ScrmException {
        if (feedbackDto == null) {
            throw ScrmException.badRequest("反馈参数不能为空");
        }
        if (feedbackDto.getRecommendationId() == null) {
            throw ScrmException.badRequest("推荐 ID 不能为空");
        }
        if (feedbackDto.getSelectedSpeechId() == null) {
            throw ScrmException.badRequest("选择的话术 ID 不能为空");
        }
        if (feedbackDto.getFeedback() == null || !VALID_FEEDBACKS.contains(feedbackDto.getFeedback())) {
            throw ScrmException.badRequest("反馈仅支持 " + VALID_FEEDBACKS);
        }
        if (feedbackDto.getOutcome() == null || !VALID_OUTCOMES.contains(feedbackDto.getOutcome())) {
            throw ScrmException.badRequest("使用结果仅支持 " + VALID_OUTCOMES);
        }
        ScrmSpeechRecommendationEntity entity = findRecommendationOrThrow(feedbackDto.getRecommendationId());
        // 校验选择的话术属于该推荐
        List<Long> recommendedIds = parseLongList(entity.getRecommendedSpeechIds());
        if (!recommendedIds.contains(feedbackDto.getSelectedSpeechId())) {
            throw ScrmException.badRequest("选择的话术不在推荐列表中: speechId=" + feedbackDto.getSelectedSpeechId());
        }
        speechService.findSpeechOrThrow(feedbackDto.getSelectedSpeechId());
        boolean success = "SUCCESS".equals(feedbackDto.getOutcome()) || "PARTIAL".equals(feedbackDto.getOutcome());
        // 更新推荐记录
        entity.setSelectedSpeechId(feedbackDto.getSelectedSpeechId());
        entity.setFeedback(feedbackDto.getFeedback());
        entity.setFeedbackComment(feedbackDto.getComment());
        entity.setOutcome(feedbackDto.getOutcome());
        entity.setUsedAt(LocalDateTime.now());
        entity = recommendationRepository.save(entity);
        // 累计话术使用统计
        speechRepository.incrementUsage(feedbackDto.getSelectedSpeechId(), success ? 1 : 0);
        // 累计话术反馈统计
        int positiveDelta = "POSITIVE".equals(feedbackDto.getFeedback()) ? 1 : 0;
        int negativeDelta = "NEGATIVE".equals(feedbackDto.getFeedback()) ? 1 : 0;
        speechRepository.incrementFeedback(feedbackDto.getSelectedSpeechId(), 1, positiveDelta, negativeDelta);
        // 重新加载话术并重算评分
        ScrmSalesSpeechEntity reloaded = speechRepository.findById(feedbackDto.getSelectedSpeechId())
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "话术不存在: id=" + feedbackDto.getSelectedSpeechId()));
        speechService.recomputeSpeechRating(reloaded);
        speechRepository.save(reloaded);
        // 同步场景统计
        try {
            scenarioService.updateScenarioStats(reloaded.getScenarioId());
        } catch (ScrmException e) {
            log.warn("反馈后更新场景统计失败, 忽略: scenarioId={}, err={}",
                    reloaded.getScenarioId(), e.getMessage());
        }
        log.info("提供话术反馈: recommendationId={}, speechId={}, feedback={}, outcome={}",
                entity.getId(), feedbackDto.getSelectedSpeechId(), feedbackDto.getFeedback(), feedbackDto.getOutcome());
        return entity;
    }

    /**
     * 查询客户推荐历史 (按 recommendedAt 降序)。
     *
     * @param customerId 客户 ID
     * @param limit      返回条数
     * @return 推荐记录列表
     */
    @Transactional(readOnly = true)
    public List<ScrmSpeechRecommendationEntity> getRecommendationHistory(Long customerId, int limit) {
        if (customerId == null) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECOMMEND_LIMIT));
        return recommendationRepository.findByCustomerIdOrderByRecommendedAtDesc(
                 customerId, PageRequest.of(0, safeLimit)).getContent();
    }

    /**
     * 匹配场景 (基于上下文条件)。
     * <p>若上下文含 scenarioCode, 按编码加载; 否则遍历启用场景, 解析 triggerConditions JSON,
     * 按 customerStage / channel / productCategory / timeOfDay / sentiment 匹配, 返回最高分场景。</p>
     *
     * @param context 匹配上下文 (可含 scenarioCode / customerStage / channel / productCategory / timeOfDay / sentiment)
     * @return 匹配的场景
     * @throws ScrmException 无匹配场景
     */
    @Transactional(readOnly = true)
    public ScrmSpeechScenarioEntity matchScenario(Map<String, Object> context) throws ScrmException {
        if (context == null || context.isEmpty()) {
            throw ScrmException.badRequest("匹配上下文不能为空");
        }
        Object codeObj = context.get("scenarioCode");
        if (codeObj != null && !String.valueOf(codeObj).isBlank()) {
            return scenarioRepository.findByScenarioCode(String.valueOf(codeObj))
                    .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                            "话术场景不存在: code=" + codeObj));
        }
        // 上下文匹配: 遍历启用场景, 解析 triggerConditions, 按字段匹配评分
        List<ScrmSpeechScenarioEntity> scenarios = scenarioRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("enabled"), true));
        if (scenarios.isEmpty()) {
            throw ScrmException.notFound("无可用话术场景");
        }
        ScrmSpeechScenarioEntity best = null;
        double bestScore = -1.0;
        for (ScrmSpeechScenarioEntity s : scenarios) {
            double score = scoreScenarioMatch(s, context);
            // 场景优先级加权
            score += (s.getPriority() != null ? s.getPriority() : 0) * 0.1;
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (best == null || bestScore <= 0) {
            throw ScrmException.notFound("无匹配的话术场景");
        }
        return best;
    }

    /**
     * 话术评分 (关键词匹配 + 风格匹配 + 评分权重 + 成功率权重 + 推荐验证加分)。
     * <p>评分项:
     * <ul>
     *   <li>关键词匹配: 话术关键词与上下文 previousInteraction / productCategory 比对, 每命中一个 +10</li>
     *   <li>风格匹配: 由上下文 sentiment 推导偏好风格, 匹配 +15</li>
     *   <li>评分权重: rating * 2</li>
     *   <li>成功率权重: usageCount>0 时 successCount/usageCount * 20</li>
     *   <li>推荐加分: isRecommended +10</li>
     *   <li>验证加分: isVerified +5</li>
     * </ul>
     * </p>
     *
     * @param speechId 话术 ID
     * @param context   匹配上下文
     * @return 评分 (含明细 Map 可通过 {@link #scoreSpeechInternal} 获取)
     * @throws ScrmException 话术不存在
     */
    @Transactional(readOnly = true)
    public double scoreSpeech(Long speechId, Map<String, Object> context) throws ScrmException {
        ScrmSalesSpeechEntity speech = speechService.findSpeechOrThrow(speechId);
        return scoreSpeechInternal(speech, context).getScore();
    }

    /**
     * 排序话术 (按评分降序取 Top limit)。
     *
     * @param speechIds 话术 ID 列表
     * @param context   匹配上下文
     * @param limit     返回条数
     * @return 排序后的话术 ID 列表
     */
    @Transactional(readOnly = true)
    public List<Long> rankSpeeches(List<Long> speechIds, Map<String, Object> context, int limit) {
        if (speechIds == null || speechIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScrmSalesSpeechEntity> speeches = speechRepository.findAll(
                (root, query, cb) -> cb.and(
                        root.get("id").in(speechIds)));
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECOMMEND_LIMIT));
        return rankSpeechesInternal(speeches, context, safeLimit).stream()
                .map(m -> (Long) m.get("speechId"))
                .collect(Collectors.toList());
    }

    /**
     * 构建匹配上下文 Map (合并请求上下文与场景默认值)。
     *
     * @param context  请求上下文 (可空)
     * @param scenario 场景实体
     * @return 匹配上下文 Map
     */
    private Map<String, Object> buildContextMap(ScrmSpeechRecommendRequestDto.RecommendContext context,
                                                ScrmSpeechScenarioEntity scenario) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scenarioCode", scenario.getScenarioCode());
        map.put("scenarioCategory", scenario.getScenarioCategory());
        if (scenario.getCustomerStage() != null) {
            map.put("customerStage", scenario.getCustomerStage());
        }
        if (context != null) {
            if (context.getChannel() != null) {
                map.put("channel", context.getChannel());
            }
            if (context.getProductCategory() != null) {
                map.put("productCategory", context.getProductCategory());
            }
            if (context.getSentiment() != null) {
                map.put("sentiment", context.getSentiment());
            }
            if (context.getCustomerStage() != null) {
                map.put("customerStage", context.getCustomerStage());
            }
            if (context.getTimeOfDay() != null) {
                map.put("timeOfDay", context.getTimeOfDay());
            }
            if (context.getPreviousInteraction() != null) {
                map.put("previousInteraction", context.getPreviousInteraction());
            }
        }
        return map;
    }

    /**
     * 评分场景匹配度 (基于 triggerConditions JSON 与上下文比对)。
     *
     * @param scenario 场景实体
     * @param context  匹配上下文
     * @return 匹配分数
     */
    private double scoreScenarioMatch(ScrmSpeechScenarioEntity scenario, Map<String, Object> context) {
        Map<String, Object> trigger = parseJson(scenario.getTriggerConditions());
        if (trigger.isEmpty()) {
            // 无触发条件, 仅按 customerStage / applicableChannels 简单匹配
            return scoreSimpleScenarioMatch(scenario, context);
        }
        double score = 0.0;
        score += matchField(trigger.get("customerStage"), context.get("customerStage"), 1.0);
        score += matchField(trigger.get("productCategory"), context.get("productCategory"), 1.0);
        score += matchField(trigger.get("channel"), context.get("channel"), 1.0);
        score += matchField(trigger.get("timeOfDay"), context.get("timeOfDay"), 0.5);
        score += matchField(trigger.get("sentiment"), context.get("sentiment"), 0.5);
        return score;
    }

    /**
     * 简单场景匹配 (无 triggerConditions 时按场景字段匹配)。
     *
     * @param scenario 场景实体
     * @param context  匹配上下文
     * @return 匹配分数
     */
    private double scoreSimpleScenarioMatch(ScrmSpeechScenarioEntity scenario, Map<String, Object> context) {
        double score = 0.0;
        score += matchField(scenario.getCustomerStage(), context.get("customerStage"), 1.0);
        // applicableChannels 为逗号分隔, 检查是否包含上下文渠道
        if (context.get("channel") != null && scenario.getApplicableChannels() != null) {
            List<String> channels = splitCsv(scenario.getApplicableChannels());
            if (channels.contains(String.valueOf(context.get("channel")))) {
                score += 1.0;
            }
        }
        // applicableProducts 为逗号分隔, 检查是否包含上下文产品类别
        if (context.get("productCategory") != null && scenario.getApplicableProducts() != null) {
            List<String> products = splitCsv(scenario.getApplicableProducts());
            if (products.contains(String.valueOf(context.get("productCategory")))) {
                score += 1.0;
            }
        }
        return score;
    }

    /**
     * 字段匹配评分 (相等得满分, 否则 0)。
     *
     * @param triggerVal 触发条件值
     * @param contextVal 上下文值
     * @param weight     权重
     * @return 匹配分数
     */
    private double matchField(Object triggerVal, Object contextVal, double weight) {
        if (triggerVal == null || contextVal == null) {
            return 0.0;
        }
        return String.valueOf(triggerVal).equalsIgnoreCase(String.valueOf(contextVal)) ? weight : 0.0;
    }

    /**
     * 话术评分内部实现 (返回含明细的评分结果)。
     *
     * @param speech  话术实体
     * @param context 匹配上下文
     * @return 评分结果 (含 score / reasons)
     */
    private ScoreResult scoreSpeechInternal(ScrmSalesSpeechEntity speech, Map<String, Object> context) {
        List<String> reasons = new ArrayList<>();
        double score = 0.0;
        // 关键词匹配
        if (context != null) {
            List<String> keywords = splitCsv(speech.getKeywords());
            if (!keywords.isEmpty()) {
                String haystack = buildHaystack(context);
                int hitCount = 0;
                for (String kw : keywords) {
                    if (!kw.isBlank() && haystack.toLowerCase().contains(kw.toLowerCase())) {
                        hitCount++;
                    }
                }
                if (hitCount > 0) {
                    double keywordScore = hitCount * WEIGHT_KEYWORD_MATCH;
                    score += keywordScore;
                    reasons.add("关键词命中 " + hitCount + " 个 (+" + Math.round(keywordScore * 100d) / 100d + ")");
                }
            }
            // 风格匹配 (由 sentiment 推导偏好风格)
            Object sentiment = context.get("sentiment");
            if (sentiment != null && speech.getSpeechStyle() != null) {
                String preferredStyle = SENTIMENT_STYLE_MAP.get(String.valueOf(sentiment).toUpperCase());
                if (preferredStyle != null && preferredStyle.equalsIgnoreCase(speech.getSpeechStyle())) {
                    score += WEIGHT_STYLE_MATCH;
                    reasons.add("风格匹配 " + speech.getSpeechStyle() + " (+" + WEIGHT_STYLE_MATCH + ")");
                }
            }
        }
        // 评分权重
        double rating = speech.getRating() != null ? speech.getRating() : 0.0;
        double ratingScore = rating * WEIGHT_RATING;
        score += ratingScore;
        if (rating > 0) {
            reasons.add("评分 " + rating + " (+" + Math.round(ratingScore * 100d) / 100d + ")");
        }
        // 成功率权重
        int usageCount = speech.getUsageCount() != null ? speech.getUsageCount() : 0;
        int successCount = speech.getSuccessCount() != null ? speech.getSuccessCount() : 0;
        if (usageCount > 0) {
            double successRate = successCount * 1.0 / usageCount;
            double successScore = successRate * WEIGHT_SUCCESS_RATE;
            score += successScore;
            reasons.add("成功率 " + Math.round(successRate * 100d) / 100d
                    + " (+" + Math.round(successScore * 100d) / 100d + ")");
        }
        // 推荐加分
        if (Boolean.TRUE.equals(speech.getIsRecommended())) {
            score += BONUS_RECOMMENDED;
            reasons.add("推荐话术 (+" + BONUS_RECOMMENDED + ")");
        }
        // 验证加分
        if (Boolean.TRUE.equals(speech.getIsVerified())) {
            score += BONUS_VERIFIED;
            reasons.add("已验证 (+" + BONUS_VERIFIED + ")");
        }
        return new ScoreResult(speech.getId(), score, reasons);
    }

    /**
     * 排序话术内部实现 (返回含评分明细的列表)。
     *
     * @param speeches 话术列表
     * @param context  匹配上下文
     * @param limit    返回条数
     * @return 排序后的话术评分列表
     */
    private List<Map<String, Object>> rankSpeechesInternal(List<ScrmSalesSpeechEntity> speeches,
                                                            Map<String, Object> context, int limit) {
        List<ScoreResult> results = new ArrayList<>();
        for (ScrmSalesSpeechEntity s : speeches) {
            results.add(scoreSpeechInternal(s, context));
        }
        results.sort((a, b) -> Double.compare(b.score, a.score));
        List<Map<String, Object>> ranked = new ArrayList<>();
        int take = Math.min(limit, results.size());
        for (int i = 0; i < take; i++) {
            ScoreResult r = results.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("speechId", r.speechId);
            m.put("score", Math.round(r.score * 100d) / 100d);
            m.put("reason", r.reasons.isEmpty() ? "综合评分" : String.join(";", r.reasons));
            ranked.add(m);
        }
        return ranked;
    }

    /**
     * 构建关键词匹配的 Haystack (上下文文本拼接)。
     *
     * @param context 匹配上下文
     * @return 拼接后的文本
     */
    private String buildHaystack(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, context.get("previousInteraction"));
        appendIfPresent(sb, context.get("productCategory"));
        appendIfPresent(sb, context.get("channel"));
        appendIfPresent(sb, context.get("customerStage"));
        return sb.toString();
    }

    /**
     * 追加非空对象到 StringBuilder。
     *
     * @param sb  StringBuilder
     * @param obj 对象
     */
    private void appendIfPresent(StringBuilder sb, Object obj) {
        if (obj != null) {
            sb.append(' ').append(obj);
        }
    }

    /**
     * 解析 JSON 字符串为 Map (解析失败返回空 Map)。
     *
     * @param json JSON 字符串
     * @return Map
     */
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("JSON 解析失败, 返回空 Map: json={}", json, e);
            return new HashMap<>();
        }
    }

    /**
     * 将对象序列化为 JSON 字符串 (失败返回 "{}")。
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON 序列化失败, 返回空 JSON: obj={}", obj, e);
            return "{}";
        }
    }

    /**
     * 解析逗号分隔的长整型列表。
     *
     * @param csv 逗号分隔字符串
     * @return 长整型列表
     */
    private List<Long> parseLongList(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 拆分逗号分隔字符串为去空格列表。
     *
     * @param csv 逗号分隔字符串
     * @return 字符串列表
     */
    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    private double toDouble(Object obj) {
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
     * 按主键查询推荐记录, 不存在抛异常, 并校验归属账号。
     *
     * @param id 推荐 ID
     * @return 推荐记录实体
     * @throws ScrmException 推荐记录不存在
     */
    private ScrmSpeechRecommendationEntity findRecommendationOrThrow(Long id) throws ScrmException {
        ScrmSpeechRecommendationEntity entity = recommendationRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "话术推荐记录不存在: id=" + id));
        return entity;
    }

    /**
     * 话术评分结果内部载体。
 * @since V1.0
     * @author Hsi Chu
     */
    private static class ScoreResult {
        /** 话术 ID */
        private final Long speechId;
        /** 评分 */
        private final double score;
        /** 评分原因列表 */
        private final List<String> reasons;

        /**
         * 构造评分结果。
         *
         * @param speechId 话术 ID
         * @param score    评分
         * @param reasons  评分原因列表
         */
        ScoreResult(Long speechId, double score, List<String> reasons) {
            this.speechId = speechId;
            this.score = score;
            this.reasons = reasons;
        }

        /**
         * 获取评分。
         *
         * @return 评分
         */
        public double getScore() {
            return score;
        }
    }

}
