/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocTopicService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmVocTopicDto;
import org.hiylo.scrm.entity.ScrmVocTopicEntity;
import org.hiylo.scrm.entity.ScrmVocVoiceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmVocTopicRepository;
import org.hiylo.scrm.repository.ScrmVocVoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 客户之声 (VoC) 主题子域服务。
 * <p>
 * 承载主题管理核心能力: 主题树 / 关键词匹配 / 主题识别 / 趋势 / 情感 / 合并, 并提供被声音、
 * 洞察与统计子域复用的分析辅助能力 (关键词提取 / 标签解析 / 数值四舍五入 / 主题查询 / 主题声音关联查询)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmVocTopicService {

    /** 默认主题层级 */
    private static final int DEFAULT_TOPIC_LEVEL = 1;
    /** 默认趋势方向 */
    private static final String DEFAULT_TREND_DIRECTION = "STABLE";
    /** 默认操作人 */
    private static final String DEFAULT_OPERATOR = "scrm-system";
    /** 关键词提取默认返回数 */
    private static final int DEFAULT_KEYWORD_LIMIT = 10;
    /** 热点主题声音数阈值 */
    private static final int HOT_TOPIC_THRESHOLD = 10;
    /** 新兴主题天数阈值 */
    private static final int EMERGING_TOPIC_DAYS = 14;

    /** 合法的主题分类 */
    private static final List<String> VALID_TOPIC_CATEGORIES = List.of(
            "PRODUCT", "SERVICE", "PRICE", "QUALITY", "EXPERIENCE", "DELIVERY", "SUPPORT", "OTHER");
    /** 合法的趋势方向 */
    private static final List<String> VALID_TREND_DIRECTIONS = List.of("RISING", "STABLE", "FALLING");

    /** 停用词 (关键词提取时过滤) */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "上", "也", "很",
            "到", "说", "要", "去", "你", "会", "着", "没", "看", "好", "自己", "这", "那", "与", "及",
            "或", "等", "但", "而", "还", "把", "被", "让", "从", "向", "对", "为", "以", "于", "之",
            "其", "此", "个", "们", "地", "得", "吧", "吗", "呢", "啊", "哦", "嗯", "就是", "只是",
            "可以", "因为", "所以", "如果", "虽然", "但是", "而且", "以及", "什么", "怎么", "为什么",
            "已经", "可能", "应该", "需要", "没有", "不是", "不要", "不能", "这个", "那个", "一个",
            "一直", "一些", "这样", "那样", "现在", "以后", "以前", "其实", "真的", "感觉", "觉得");

    /** VoC 主题数据访问层 */
    private final ScrmVocTopicRepository topicRepository;
    /** VoC 声音数据访问层 */
    private final ScrmVocVoiceRepository voiceRepository;

    /**
     * 创建主题。
     *
     * @param dto 主题参数
     * @return 创建后的主题
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmVocTopicEntity createTopic(ScrmVocTopicDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("主题参数不能为空");
        }
        validateTopicEnums(dto, false);

        ScrmVocTopicEntity entity = new ScrmVocTopicEntity();
        entity.setTopicName(dto.getTopicName());
        entity.setTopicCode(dto.getTopicCode());
        entity.setDescription(dto.getDescription());
        entity.setParentTopicId(dto.getParentTopicId());
        entity.setTopicLevel(dto.getTopicLevel() != null ? dto.getTopicLevel() : DEFAULT_TOPIC_LEVEL);
        entity.setCategory(dto.getCategory());
        entity.setKeywords(dto.getKeywords());
        entity.setVoiceCount(0);
        entity.setPositiveCount(0);
        entity.setNegativeCount(0);
        entity.setNeutralCount(0);
        entity.setPositiveRate(0.0);
        entity.setNegativeRate(0.0);
        entity.setAvgSentimentScore(0.0);
        entity.setAvgRating(0.0);
        entity.setTrendDirection(dto.getTrendDirection() != null ? dto.getTrendDirection() : DEFAULT_TREND_DIRECTION);
        entity.setTrendPercent(0.0);
        entity.setUrgencyScore(0.0);
        entity.setImpactScore(0.0);
        entity.setPriorityScore(0.0);
        entity.setIsHotTopic(dto.getIsHotTopic() != null ? dto.getIsHotTopic() : Boolean.FALSE);
        entity.setIsEmerging(dto.getIsEmerging() != null ? dto.getIsEmerging() : Boolean.FALSE);
        entity.setAssignedDepartment(dto.getAssignedDepartment());
        entity.setActionTaken(Boolean.FALSE);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        entity = topicRepository.save(entity);
        log.info("创建主题: id={}, code={}", entity.getId(), entity.getTopicCode());
        return entity;
    }

    /**
     * 更新主题 (字段非空才覆盖)。
     *
     * @param id  主题 ID
     * @param dto 主题参数
     * @return 更新后的主题
     * @throws ScrmException 主题不存在 / 参数非法 / 编码重复
     */
    @Transactional
    public ScrmVocTopicEntity updateTopic(Long id, ScrmVocTopicDto dto) throws ScrmException {
        ScrmVocTopicEntity entity = findTopicOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("主题参数不能为空");
        }
        validateTopicEnums(dto, true);
        if (dto.getTopicCode() != null && !dto.getTopicCode().equals(entity.getTopicCode())) {
            Optional<ScrmVocTopicEntity> existing = topicRepository.findByTopicCode(dto.getTopicCode());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw ScrmException.conflict("主题编码已存在: " + dto.getTopicCode());
            }
            entity.setTopicCode(dto.getTopicCode());
        }
        if (dto.getTopicName() != null) entity.setTopicName(dto.getTopicName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getParentTopicId() != null) entity.setParentTopicId(dto.getParentTopicId());
        if (dto.getTopicLevel() != null) entity.setTopicLevel(dto.getTopicLevel());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getKeywords() != null) entity.setKeywords(dto.getKeywords());
        if (dto.getTrendDirection() != null) entity.setTrendDirection(dto.getTrendDirection());
        if (dto.getAssignedDepartment() != null) entity.setAssignedDepartment(dto.getAssignedDepartment());
        if (dto.getIsHotTopic() != null) entity.setIsHotTopic(dto.getIsHotTopic());
        if (dto.getIsEmerging() != null) entity.setIsEmerging(dto.getIsEmerging());
        if (dto.getActionTaken() != null) entity.setActionTaken(dto.getActionTaken());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = topicRepository.save(entity);
        log.info("更新主题: id={}, code={}", id, entity.getTopicCode());
        return entity;
    }

    /**
     * 删除主题。
     *
     * @param id 主题 ID
     * @throws ScrmException 主题不存在
     */
    @Transactional
    public void deleteTopic(Long id) throws ScrmException {
        ScrmVocTopicEntity entity = findTopicOrThrow(id);
        topicRepository.delete(entity);
        log.info("删除主题: id={}, code={}", id, entity.getTopicCode());
    }

    /**
     * 查询主题详情。
     *
     * @param id 主题 ID
     * @return 主题实体
     * @throws ScrmException 主题不存在
     */
    @Transactional(readOnly = true)
    public ScrmVocTopicEntity getTopic(Long id) throws ScrmException {
        return findTopicOrThrow(id);
    }

    /**
     * 按主题编码查询主题。
     *
     * @param code 主题编码
     * @return 主题实体
     * @throws ScrmException 主题不存在
     */
    @Transactional(readOnly = true)
    public ScrmVocTopicEntity getTopicByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("主题编码不能为空");
        }
        return topicRepository.findByTopicCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "主题不存在: code=" + code));
    }

    /**
     * 分页查询主题, 支持按分类 / 启用状态 / 关键字过滤。
     *
     * @param category 分类过滤 (可空)
     * @param enabled  启用状态过滤 (可空)
     * @param keyword  主题名/编码关键字 (可空)
     * @param pageable 分页参数
     * @return 主题分页结果 (按 priorityScore DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmVocTopicEntity> listTopics(String category, Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmVocTopicEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("topicName"), like),
                        cb.like(root.get("topicCode"), like)));
            }
            query.orderBy(cb.desc(root.get("priorityScore")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return topicRepository.findAll(spec, pageable);
    }

    /**
     * 查询主题树 (所有主题按层级组织)。
     *
     * @return 主题树 [{topic, children: [...]}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopicTree() {
        List<ScrmVocTopicEntity> all = topicRepository.findAll(
                (root, query, cb) -> cb.and(),
                Sort.by(Sort.Direction.ASC, "topicLevel"));
        Map<Long, List<ScrmVocTopicEntity>> byParent = all.stream()
                .collect(Collectors.groupingBy(t -> t.getParentTopicId() != null ? t.getParentTopicId() : 0L));
        return buildTopicTree(0L, byParent);
    }

    /**
     * 查询子主题。
     *
     * @param parentId 父主题 ID
     * @return 子主题列表
     */
    @Transactional(readOnly = true)
    public List<ScrmVocTopicEntity> getChildTopics(Long parentId) {
        return topicRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("parentTopicId"), parentId));
            query.orderBy(cb.asc(root.get("topicLevel")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    /**
     * 更新主题统计 (重新计算声音数 / 情感分布 / 评分 / 趋势 / 优先级 / 热点 / 新兴)。
     *
     * @param id 主题 ID
     * @return 更新后的主题
     * @throws ScrmException 主题不存在
     */
    @Transactional
    public ScrmVocTopicEntity updateTopicStats(Long id) throws ScrmException {
        ScrmVocTopicEntity topic = findTopicOrThrow(id);
        List<ScrmVocVoiceEntity> voices = findVoicesForTopic(topic);
        int total = voices.size();
        long positive = voices.stream().filter(v -> "POSITIVE".equals(v.getSentiment())).count();
        long negative = voices.stream().filter(v -> "NEGATIVE".equals(v.getSentiment())).count();
        long neutral = voices.stream().filter(v -> "NEUTRAL".equals(v.getSentiment())).count();
        topic.setVoiceCount(total);
        topic.setPositiveCount((int) positive);
        topic.setNegativeCount((int) negative);
        topic.setNeutralCount((int) neutral);
        topic.setPositiveRate(total > 0 ? round2(positive * 100.0 / total) : 0.0);
        topic.setNegativeRate(total > 0 ? round2(negative * 100.0 / total) : 0.0);
        double avgSentiment = voices.stream()
                .filter(v -> v.getSentimentScore() != null)
                .mapToDouble(ScrmVocVoiceEntity::getSentimentScore)
                .average().orElse(0.0);
        topic.setAvgSentimentScore(round2(avgSentiment));
        double avgRating = voices.stream()
                .filter(v -> v.getRating() != null)
                .mapToInt(ScrmVocVoiceEntity::getRating)
                .average().orElse(0.0);
        topic.setAvgRating(round2(avgRating));
        // 时间范围
        voices.stream().map(ScrmVocVoiceEntity::getCollectedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .ifPresent(topic::setFirstVoiceAt);
        voices.stream().map(ScrmVocVoiceEntity::getCollectedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .ifPresent(topic::setLastVoiceAt);
        // 紧急度: 负面占比 × 50 + 紧急优先级占比 × 50
        double urgentRatio = total > 0
                ? voices.stream().filter(v -> "URGENT".equals(v.getPriority())).count() * 50.0 / total
                : 0;
        double urgency = Math.min(round2(negative * 50.0 / Math.max(total, 1) + urgentRatio), 100.0);
        topic.setUrgencyScore(urgency);
        // 影响度: 声音数归一化 (50 条 → 100) × 0.5 + 负面率 × 0.5
        double impact = Math.min(total * 2.0, 100.0) * 0.5 + topic.getNegativeRate() * 0.5;
        topic.setImpactScore(round2(Math.min(impact, 100.0)));
        // 综合优先级: 紧急度 × 0.4 + 影响度 × 0.4 + 负面率 × 0.2
        double priority = topic.getUrgencyScore() * 0.4 + topic.getImpactScore() * 0.4 + topic.getNegativeRate() * 0.2;
        topic.setPriorityScore(round2(Math.min(priority, 100.0)));
        // 热点: 声音数 ≥ 阈值
        topic.setIsHotTopic(total >= HOT_TOPIC_THRESHOLD);
        // 新兴: 最早声音在 14 天内
        if (topic.getFirstVoiceAt() != null) {
            topic.setIsEmerging(topic.getFirstVoiceAt().isAfter(LocalDateTime.now().minusDays(EMERGING_TOPIC_DAYS)));
        }
        topic.setLastAnalysisAt(LocalDateTime.now());
        topic = topicRepository.save(topic);
        log.info("更新主题统计: id={}, voiceCount={}, priorityScore={}", id, total, topic.getPriorityScore());
        return topic;
    }

    /**
     * 主题识别 (从声音内容提取关键词 → 匹配主题)。
     *
     * @param voiceId 声音 ID
     * @return 匹配的主题列表
     * @throws ScrmException 声音不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmVocTopicEntity> identifyTopics(Long voiceId) throws ScrmException {
        ScrmVocVoiceEntity voice = voiceRepository.findById(voiceId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户声音不存在: id=" + voiceId));
        List<String> keywords = extractKeywords(voice.getContent());
        List<ScrmVocTopicEntity> allTopics = topicRepository.findAll(
                (root, query, cb) -> cb.and());
        List<ScrmVocTopicEntity> matched = new ArrayList<>();
        for (ScrmVocTopicEntity topic : allTopics) {
            Set<String> topicKeywords = parseCsv(topic.getKeywords());
            topicKeywords.add(topic.getTopicName());
            for (String kw : keywords) {
                if (topicKeywords.stream().anyMatch(tk -> tk != null && (tk.contains(kw) || kw.contains(tk)))) {
                    matched.add(topic);
                    break;
                }
            }
        }
        return matched;
    }

    /**
     * 查询热点主题 (isHotTopic = true)。
     *
     * @param limit 返回条数
     * @return 热点主题列表 (按 priorityScore DESC)
     */
    @Transactional(readOnly = true)
    public List<ScrmVocTopicEntity> getHotTopics(int limit) {
        return topicRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isHotTopic"), true));
            predicates.add(cb.equal(root.get("enabled"), true));
            query.orderBy(cb.desc(root.get("priorityScore")));
            return cb.and(predicates.toArray(new Predicate[0]));
        }).stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 查询新兴主题 (isEmerging = true)。
     *
     * @param limit 返回条数
     * @return 新兴主题列表 (按 priorityScore DESC)
     */
    @Transactional(readOnly = true)
    public List<ScrmVocTopicEntity> getEmergingTopics(int limit) {
        return topicRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isEmerging"), true));
            predicates.add(cb.equal(root.get("enabled"), true));
            query.orderBy(cb.desc(root.get("priorityScore")));
            return cb.and(predicates.toArray(new Predicate[0]));
        }).stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 主题趋势 (按月统计声音数)。
     *
     * @param topicId 主题 ID
     * @param months  回溯月数
     * @return 趋势结果 Map {labels, voiceCounts, sentimentScores}
     * @throws ScrmException 主题不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTopicTrend(Long topicId, int months) throws ScrmException {
        ScrmVocTopicEntity topic = findTopicOrThrow(topicId);
        List<ScrmVocVoiceEntity> voices = findVoicesForTopic(topic);
        LocalDateTime startTime = LocalDateTime.now().minusMonths(months);
        Map<String, List<ScrmVocVoiceEntity>> byMonth = voices.stream()
                .filter(v -> v.getCollectedAt() != null && v.getCollectedAt().isAfter(startTime))
                .collect(Collectors.groupingBy(v -> v.getCollectedAt().getYear() + "-"
                        + String.format("%02d", v.getCollectedAt().getMonthValue())));
        List<String> labels = new ArrayList<>();
        List<Long> voiceCounts = new ArrayList<>();
        List<Double> sentimentScores = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDateTime month = LocalDateTime.now().minusMonths(i);
            String key = month.getYear() + "-" + String.format("%02d", month.getMonthValue());
            List<ScrmVocVoiceEntity> monthVoices = byMonth.getOrDefault(key, List.of());
            labels.add(key);
            voiceCounts.add((long) monthVoices.size());
            sentimentScores.add(monthVoices.stream()
                    .filter(v -> v.getSentimentScore() != null)
                    .mapToDouble(ScrmVocVoiceEntity::getSentimentScore)
                    .average().orElse(0.0));
        }
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("topicId", topicId);
        trend.put("topicName", topic.getTopicName());
        trend.put("labels", labels);
        trend.put("voiceCounts", voiceCounts);
        trend.put("sentimentScores", sentimentScores);
        return trend;
    }

    /**
     * 主题情感分布。
     *
     * @param topicId 主题 ID
     * @return 情感分布 Map {positive, negative, neutral, positiveRate, negativeRate, avgSentimentScore}
     * @throws ScrmException 主题不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTopicSentiment(Long topicId) throws ScrmException {
        ScrmVocTopicEntity topic = findTopicOrThrow(topicId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topic.getId());
        result.put("topicName", topic.getTopicName());
        result.put("voiceCount", topic.getVoiceCount());
        result.put("positive", topic.getPositiveCount());
        result.put("negative", topic.getNegativeCount());
        result.put("neutral", topic.getNeutralCount());
        result.put("positiveRate", topic.getPositiveRate());
        result.put("negativeRate", topic.getNegativeRate());
        result.put("avgSentimentScore", topic.getAvgSentimentScore());
        return result;
    }

    /**
     * 合并主题 (将源主题的声音关联转移到目标主题, 源主题禁用)。
     *
     * @param sourceId 源主题 ID
     * @param targetId 目标主题 ID
     * @return 目标主题
     * @throws ScrmException 主题不存在 / 源与目标相同
     */
    @Transactional
    public ScrmVocTopicEntity mergeTopics(Long sourceId, Long targetId) throws ScrmException {
        if (Objects.equals(sourceId, targetId)) {
            throw ScrmException.badRequest("源主题与目标主题不能相同");
        }
        ScrmVocTopicEntity source = findTopicOrThrow(sourceId);
        ScrmVocTopicEntity target = findTopicOrThrow(targetId);
        // 将声音 tags 中的源主题编码替换为目标主题编码
        List<ScrmVocVoiceEntity> voices = findVoicesForTopic(source);
        for (ScrmVocVoiceEntity voice : voices) {
            Set<String> tags = parseCsv(voice.getTags());
            tags.remove(source.getTopicCode());
            tags.add(target.getTopicCode());
            voice.setTags(String.join(",", tags));
            voiceRepository.save(voice);
        }
        // 禁用源主题
        source.setEnabled(false);
        topicRepository.save(source);
        // 刷新目标主题统计
        updateTopicStats(targetId);
        log.info("合并主题: sourceId={}, targetId={}, 迁移声音数={}", sourceId, targetId, voices.size());
        return topicRepository.findById(targetId).orElse(target);
    }

    /**
     * 按主键查询主题, 不存在抛异常。
     *
     * @param id 主题 ID
     * @return 主题实体
     * @throws ScrmException 主题不存在
     */
    public ScrmVocTopicEntity findTopicOrThrow(Long id) throws ScrmException {
        return topicRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "主题不存在: id=" + id));
    }

    /**
     * 查询主题关联的声音 (通过主题编码匹配声音 tags)。
     *
     * @param topic 主题实体
     * @return 关联的声音列表
     */
    public List<ScrmVocVoiceEntity> findVoicesForTopic(ScrmVocTopicEntity topic) {
        return voiceRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(root.get("tags"), "%" + topic.getTopicCode() + "%"));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    /**
     * 关键词提取 (分词 → 去停用词 → 统计频率), 默认返回 {@link #DEFAULT_KEYWORD_LIMIT} 条。
     *
     * @param content 文本内容
     * @return 关键词列表
     */
    public List<String> extractKeywords(String content) {
        return extractKeywords(content, DEFAULT_KEYWORD_LIMIT);
    }

    /**
     * 关键词提取 (分词 → 去停用词 → 统计频率), 指定返回条数。
     *
     * @param content 文本内容
     * @param limit   返回条数
     * @return 关键词列表
     */
    public List<String> extractKeywords(String content, int limit) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        // 分词: 按非汉字/字母/数字字符分割
        String[] tokens = content.split("[^\\u4e00-\\u9fa5a-zA-Z0-9]+");
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            String lower = token.toLowerCase();
            if (STOP_WORDS.contains(lower) || STOP_WORDS.contains(token)) {
                continue;
            }
            freq.merge(token, 1, Integer::sum);
        }
        return freq.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 解析逗号分隔的字符串为 Set。
     *
     * @param csv 逗号分隔字符串 (可空)
     * @return 去重后的标签集合
     */
    public Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return new java.util.LinkedHashSet<>();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * double 保留两位小数。
     *
     * @param value 原始数值
     * @return 保留两位小数后的数值
     */
    public double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 当前操作人 (优先取 UserContext, 缺省 scrm-system)。
     *
     * @return 操作人标识
     */
    public String currentOperator() {
        String userId = UserContext.getUserId();
        return userId != null ? userId : DEFAULT_OPERATOR;
    }

    /**
     * 校验主题 DTO 枚举字段。
     */
    private void validateTopicEnums(ScrmVocTopicDto dto, boolean isUpdate) throws ScrmException {
        if (!isUpdate) {
            if (dto.getCategory() != null && !VALID_TOPIC_CATEGORIES.contains(dto.getCategory())) {
                throw ScrmException.badRequest("主题分类非法: " + dto.getCategory() + ", 仅支持 " + VALID_TOPIC_CATEGORIES);
            }
        }
        if (dto.getTrendDirection() != null && !VALID_TREND_DIRECTIONS.contains(dto.getTrendDirection())) {
            throw ScrmException.badRequest("趋势方向非法: " + dto.getTrendDirection()
                    + ", 仅支持 " + VALID_TREND_DIRECTIONS);
        }
    }

    /**
     * 递归构建主题树。
     */
    private List<Map<String, Object>> buildTopicTree(Long parentId, Map<Long, List<ScrmVocTopicEntity>> byParent) {
        List<Map<String, Object>> tree = new ArrayList<>();
        List<ScrmVocTopicEntity> children = byParent.getOrDefault(parentId, List.of());
        for (ScrmVocTopicEntity topic : children) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("topic", topic);
            node.put("children", buildTopicTree(topic.getId(), byParent));
            tree.add(node);
        }
        return tree;
    }
}
