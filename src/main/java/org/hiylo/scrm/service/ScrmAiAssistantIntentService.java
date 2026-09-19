/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiAssistantIntentService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.service;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hiylo.scrm.dto.ScrmAiIntentDto;
import org.hiylo.scrm.entity.ScrmAiIntentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAiIntentRepository;

import static org.hiylo.scrm.service.ScrmAiAssistantConfigService.DEFAULT_ENABLED;

/**
 * AI 意图识别兄弟服务。
 * <p>
 * 承载 AI 意图的增删改查、启停管理与意图识别 (关键词匹配 + 示例 Jaccard 相似度)。
 * 作为 {@link ScrmAiAssistantService} 的意图子域拆分产物, 由门面与对话兄弟服务注入。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAiAssistantIntentService {

    // ==================== 默认值常量 ====================

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 默认建议动作 */
    private static final String DEFAULT_SUGGESTED_ACTION = "NONE";

    /** 默认匹配次数初值 */
    private static final int DEFAULT_MATCH_COUNT = 0;

    /** 默认意图置信度 */
    private static final double DEFAULT_INTENT_CONFIDENCE = 0.0;

    /** 意图识别置信度阈值 (低于此值视为未识别) */
    private static final double INTENT_CONFIDENCE_THRESHOLD = 0.3;

    /** 关键词匹配置信度 */
    private static final double KEYWORD_MATCH_CONFIDENCE = 0.8;

    /** 示例相似度匹配置信度基数 */
    private static final double EXAMPLE_MATCH_CONFIDENCE_BASE = 0.5;

    // ==================== 枚举值常量 ====================

    /** 意图类别: 咨询 */
    private static final String CATEGORY_INQUIRY = "INQUIRY";
    /** 意图类别: 投诉 */
    private static final String CATEGORY_COMPLAINT = "COMPLAINT";
    /** 意图类别: 购买 */
    private static final String CATEGORY_PURCHASE = "PURCHASE";
    /** 意图类别: 支持 */
    private static final String CATEGORY_SUPPORT = "SUPPORT";
    /** 意图类别: 反馈 */
    private static final String CATEGORY_FEEDBACK = "FEEDBACK";
    /** 意图类别: 问候 */
    private static final String CATEGORY_GREETING = "GREETING";
    /** 意图类别: 告别 */
    private static final String CATEGORY_FAREWELL = "FAREWELL";
    /** 意图类别: 提问 */
    private static final String CATEGORY_QUESTION = "QUESTION";
    /** 意图类别: 请求 */
    private static final String CATEGORY_REQUEST = "REQUEST";

    /** 建议动作: 推荐产品 */
    private static final String ACTION_RECOMMEND_PRODUCT = "RECOMMEND_PRODUCT";
    /** 建议动作: 创建工单 */
    private static final String ACTION_CREATE_TICKET = "CREATE_TICKET";
    /** 建议动作: 分配客服 */
    private static final String ACTION_ASSIGN_AGENT = "ASSIGN_AGENT";
    /** 建议动作: 升级处理 */
    private static final String ACTION_ESCALATE = "ESCALATE";
    /** 建议动作: 无 */
    private static final String ACTION_NONE = "NONE";

    /** 合法的意图类别 */
    private static final List<String> VALID_INTENT_CATEGORIES = List.of(
            CATEGORY_INQUIRY, CATEGORY_COMPLAINT, CATEGORY_PURCHASE, CATEGORY_SUPPORT,
            CATEGORY_FEEDBACK, CATEGORY_GREETING, CATEGORY_FAREWELL, CATEGORY_QUESTION, CATEGORY_REQUEST);

    /** 合法的建议动作 */
    private static final List<String> VALID_SUGGESTED_ACTIONS = List.of(
            ACTION_RECOMMEND_PRODUCT, ACTION_CREATE_TICKET, ACTION_ASSIGN_AGENT, ACTION_ESCALATE, ACTION_NONE);

    // ==================== 依赖注入 ====================

    /** AI 意图数据访问层 */
    private final ScrmAiIntentRepository intentRepository;

    /** JSON 解析器 (解析 examples 数组) */
    private final ObjectMapper objectMapper;

    /**
     * 创建 AI 意图。
     * <p>校验 intentCategory / suggestedAction 合法性后写入归属账号 ID 持久化,
     * priority / enabled 缺省时填默认值。</p>
     *
     * @param dto 意图参数
     * @return 创建后的意图
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmAiIntentEntity createIntent(ScrmAiIntentDto dto) throws ScrmException {
        validateIntentDto(dto, false);
        ScrmAiIntentEntity entity = new ScrmAiIntentEntity();
        entity.setIntentName(dto.getIntentName());
        entity.setIntentCategory(dto.getIntentCategory());
        entity.setKeywords(dto.getKeywords());
        entity.setExamples(dto.getExamples());
        entity.setResponseTemplate(dto.getResponseTemplate());
        entity.setSuggestedAction(dto.getSuggestedAction() != null ?
                dto.getSuggestedAction() : DEFAULT_SUGGESTED_ACTION);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setMatchCount(DEFAULT_MATCH_COUNT);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = intentRepository.save(entity);
        log.info("创建 AI 意图: id={}, intentName={}, category={}",
                entity.getId(), entity.getIntentName(), entity.getIntentCategory());
        return entity;
    }

    /**
     * 更新 AI 意图（字段非空才覆盖）。
     *
     * @param id  意图 ID
     * @param dto 意图参数
     * @return 更新后的意图
     * @throws ScrmException 意图不存在 / 参数非法
     */
    @Transactional
    public ScrmAiIntentEntity updateIntent(Long id, ScrmAiIntentDto dto) throws ScrmException {
        ScrmAiIntentEntity entity = findIntentOrThrow(id);
        validateIntentDto(dto, true);
        if (dto.getIntentName() != null) entity.setIntentName(dto.getIntentName());
        if (dto.getIntentCategory() != null) entity.setIntentCategory(dto.getIntentCategory());
        if (dto.getKeywords() != null) entity.setKeywords(dto.getKeywords());
        if (dto.getExamples() != null) entity.setExamples(dto.getExamples());
        if (dto.getResponseTemplate() != null) entity.setResponseTemplate(dto.getResponseTemplate());
        if (dto.getSuggestedAction() != null) entity.setSuggestedAction(dto.getSuggestedAction());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = intentRepository.save(entity);
        log.info("更新 AI 意图: id={}, intentName={}", entity.getId(), entity.getIntentName());
        return entity;
    }

    /**
     * 删除 AI 意图。
     *
     * @param id 意图 ID
     * @throws ScrmException 意图不存在
     */
    @Transactional
    public void deleteIntent(Long id) throws ScrmException {
        ScrmAiIntentEntity entity = findIntentOrThrow(id);
        intentRepository.delete(entity);
        log.info("删除 AI 意图: id={}, intentName={}", id, entity.getIntentName());
    }

    /**
     * 查询意图详情。
     *
     * @param id 意图 ID
     * @return 意图实体
     * @throws ScrmException 意图不存在
     */
    @Transactional(readOnly = true)
    public ScrmAiIntentEntity getIntent(Long id) throws ScrmException {
        return findIntentOrThrow(id);
    }

    /**
     * 分页查询意图, 支持按意图类别、启用状态与关键字过滤。
     *
     * @param category 意图类别过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  意图名称/关键词模糊匹配（可空）
     * @param pageable 分页参数
     * @return 意图分页结果 (按 priority DESC, createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAiIntentEntity> listIntents(String category, Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmAiIntentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("intentCategory"), category));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("intentName"), pattern),
                        cb.like(root.get("keywords"), pattern)));
            }
            query.orderBy(cb.desc(root.get("priority")), cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return intentRepository.findAll(spec, pageable);
    }

    /**
     * 启用意图。
     *
     * @param id 意图 ID
     * @return 更新后的意图
     * @throws ScrmException 意图不存在
     */
    @Transactional
    public ScrmAiIntentEntity enableIntent(Long id) throws ScrmException {
        ScrmAiIntentEntity entity = findIntentOrThrow(id);
        entity.setEnabled(true);
        entity = intentRepository.save(entity);
        log.info("启用 AI 意图: id={}, intentName={}", id, entity.getIntentName());
        return entity;
    }

    /**
     * 禁用意图。
     *
     * @param id 意图 ID
     * @return 更新后的意图
     * @throws ScrmException 意图不存在
     */
    @Transactional
    public ScrmAiIntentEntity disableIntent(Long id) throws ScrmException {
        ScrmAiIntentEntity entity = findIntentOrThrow(id);
        entity.setEnabled(false);
        entity = intentRepository.save(entity);
        log.info("禁用 AI 意图: id={}, intentName={}", id, entity.getIntentName());
        return entity;
    }

    /**
     * 意图识别: 关键词匹配 + 示例相似度, 返回识别结果 (意图 + 置信度)。
     * <p>
     * 流程:
     * <ol>
     *   <li>加载账号启用意图 (priority DESC)</li>
     *   <li>对每条意图: 若 keywords 任一命中消息, 置信度 0.8; 若 examples 任一与消息
     *       Jaccard 相似度 ≥ 0.3, 置信度 0.5 + 相似度 * 0.5 (上限 1.0)</li>
     *   <li>取置信度最高且 ≥ 0.3 的意图为识别结果, 增量更新其匹配次数</li>
     *   <li>未识别返回 UNKNOWN 意图, 置信度 0</li>
     * </ol>
     * </p>
     *
     * @param message 客户消息
     * @return 识别结果 Map: {intentName, intentCategory, confidence, suggestedAction, responseTemplate}
     */
    @Transactional
    public Map<String, Object> detectIntent(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (message == null || message.isBlank()) {
            result.put("intentName", "UNKNOWN");
            result.put("intentCategory", CATEGORY_QUESTION);
            result.put("confidence", DEFAULT_INTENT_CONFIDENCE);
            result.put("suggestedAction", ACTION_NONE);
            result.put("responseTemplate", null);
            return result;
        }
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        List<ScrmAiIntentEntity> intents = intentRepository.findByEnabledTrueOrderByPriorityDesc();
        ScrmAiIntentEntity bestMatch = null;
        double bestConfidence = DEFAULT_INTENT_CONFIDENCE;
        for (ScrmAiIntentEntity intent : intents) {
            double confidence = computeIntentConfidence(intent, lowerMessage);
            if (confidence > bestConfidence) {
                bestConfidence = confidence;
                bestMatch = intent;
            }
        }
        if (bestMatch == null || bestConfidence < INTENT_CONFIDENCE_THRESHOLD) {
            result.put("intentName", "UNKNOWN");
            result.put("intentCategory", CATEGORY_QUESTION);
            result.put("confidence", DEFAULT_INTENT_CONFIDENCE);
            result.put("suggestedAction", ACTION_NONE);
            result.put("responseTemplate", null);
            return result;
        }
        // 增量更新命中意图的匹配次数
        try {
            intentRepository.incrementMatchCount(bestMatch.getId());
        } catch (Exception e) {
            log.warn("更新意图匹配次数失败, 忽略: intentId={}, err={}", bestMatch.getId(), e.getMessage());
        }
        result.put("intentName", bestMatch.getIntentName());
        result.put("intentCategory", bestMatch.getIntentCategory());
        result.put("confidence", bestConfidence);
        result.put("suggestedAction", bestMatch.getSuggestedAction() != null
                ? bestMatch.getSuggestedAction() : ACTION_NONE);
        result.put("responseTemplate", bestMatch.getResponseTemplate());
        return result;
    }

    /**
     * 校验 AI 意图参数。
     *
     * @param dto     意图参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateIntentDto(ScrmAiIntentDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("意图参数不能为空");
        }
        if (dto.getIntentName() != null) {
            if (dto.getIntentName().isBlank()) {
                throw ScrmException.badRequest("意图名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("意图名称不能为空");
        }
        if (dto.getIntentCategory() != null) {
            if (!VALID_INTENT_CATEGORIES.contains(dto.getIntentCategory())) {
                throw ScrmException.badRequest(
                        "意图类别非法: " + dto.getIntentCategory() + ", 仅支持 " + VALID_INTENT_CATEGORIES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("意图类别不能为空");
        }
        if (dto.getSuggestedAction() != null && !VALID_SUGGESTED_ACTIONS.contains(dto.getSuggestedAction())) {
            throw ScrmException.badRequest(
                    "建议动作非法: " + dto.getSuggestedAction() + ", 仅支持 " + VALID_SUGGESTED_ACTIONS);
        }
        if (dto.getExamples() != null && !dto.getExamples().isBlank()) {
            try {
                objectMapper.readTree(dto.getExamples());
            } catch (Exception e) {
                throw ScrmException.badRequest("示例文本 JSON 解析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 计算单条意图与消息的置信度。
     * <p>关键词命中置信度 0.8; 示例 Jaccard 相似度 ≥ 0.3 时置信度 0.5 + 相似度 * 0.5。
     * 取两者较大值。</p>
     *
     * @param intent       意图实体
     * @param lowerMessage 小写化的客户消息
     * @return 置信度 (0-1)
     */
    private double computeIntentConfidence(ScrmAiIntentEntity intent, String lowerMessage) {
        double keywordConfidence = 0.0;
        if (intent.getKeywords() != null && !intent.getKeywords().isBlank()) {
            List<String> keywords = splitCsv(intent.getKeywords());
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank() && lowerMessage.contains(keyword.toLowerCase(Locale.ROOT))) {
                    keywordConfidence = KEYWORD_MATCH_CONFIDENCE;
                    break;
                }
            }
        }
        double exampleConfidence = 0.0;
        if (intent.getExamples() != null && !intent.getExamples().isBlank()) {
            List<String> examples = parseStringList(intent.getExamples());
            for (String example : examples) {
                if (example == null || example.isBlank()) {
                    continue;
                }
                double similarity = jaccardSimilarity(lowerMessage, example.toLowerCase(Locale.ROOT));
                if (similarity >= INTENT_CONFIDENCE_THRESHOLD) {
                    double confidence = EXAMPLE_MATCH_CONFIDENCE_BASE + similarity * 0.5;
                    exampleConfidence = Math.max(exampleConfidence, Math.min(1.0, confidence));
                }
            }
        }
        return Math.max(keywordConfidence, exampleConfidence);
    }

    /**
     * 计算两个字符串的 Jaccard 相似度 (按字符 bigram 分词)。
     *
     * @param a 字符串 A
     * @param b 字符串 B
     * @return 相似度 (0-1)
     */
    private double jaccardSimilarity(String a, String b) {
        if (a == null || b == null || a.length() < 2 || b.length() < 2) {
            return 0.0;
        }
        java.util.Set<String> setA = new java.util.HashSet<>();
        for (int i = 0; i < a.length() - 1; i++) {
            setA.add(a.substring(i, i + 2));
        }
        java.util.Set<String> setB = new java.util.HashSet<>();
        for (int i = 0; i < b.length() - 1; i++) {
            setB.add(b.substring(i, i + 2));
        }
        int intersection = 0;
        for (String bigram : setA) {
            if (setB.contains(bigram)) {
                intersection++;
            }
        }
        int union = setA.size() + setB.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    /**
     * 拆分逗号分隔字符串为列表 (自动去空白)。
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
                .toList();
    }

    /**
     * 解析 JSON 字符串数组。
     *
     * @param json JSON 字符串
     * @return 字符串列表, 解析失败返回空列表
     */
    private List<String> parseStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("JSON 数组解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按主键查询意图, 不存在抛异常, 并校验账号归属。
     *
     * @param id 意图 ID
     * @return 意图实体
     * @throws ScrmException 意图不存在
     */
    private ScrmAiIntentEntity findIntentOrThrow(Long id) throws ScrmException {
        ScrmAiIntentEntity entity = intentRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "AI 意图不存在: id=" + id));
        return entity;
    }

}
