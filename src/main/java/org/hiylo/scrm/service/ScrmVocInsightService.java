/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocInsightService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmVocInsightDto;
import org.hiylo.scrm.entity.ScrmVocInsightEntity;
import org.hiylo.scrm.entity.ScrmVocTopicEntity;
import org.hiylo.scrm.entity.ScrmVocVoiceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmVocInsightRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 客户之声 (VoC) 洞察子域服务。
 * <p>
 * 承载洞察管理核心能力: 增删改查 / 发布 / 归档 / 分享 / 自动生成 / 反馈, 基于主题声音数据
 * 分析趋势、识别模式并生成建议。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmVocInsightService {

    /** 默认优先级 */
    private static final String DEFAULT_PRIORITY = "MEDIUM";
    /** 默认影响等级 */
    private static final String DEFAULT_IMPACT_LEVEL = "MEDIUM";
    /** 默认洞察状态 */
    private static final String DEFAULT_INSIGHT_STATUS = "DRAFT";
    /** 热点主题声音数阈值 */
    private static final int HOT_TOPIC_THRESHOLD = 10;

    /** 合法的洞察类型 */
    private static final List<String> VALID_INSIGHT_TYPES = List.of(
            "TREND", "PATTERN", "ANOMALY", "OPPORTUNITY", "RISK", "ROOT_CAUSE", "BEST_PRACTICE", "LESSON_LEARNED");
    /** 合法的影响等级 */
    private static final List<String> VALID_IMPACT_LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    /** 合法的优先级 */
    private static final List<String> VALID_PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "URGENT");
    /** 合法的洞察状态 */
    private static final List<String> VALID_INSIGHT_STATUSES = List.of(
            "DRAFT", "REVIEW", "PUBLISHED", "ACTED_ON", "ARCHIVED");

    /** VoC 洞察数据访问层 */
    private final ScrmVocInsightRepository insightRepository;
    /** VoC 主题子域服务 (复用主题查询 / 分析辅助能力) */
    private final ScrmVocTopicService topicService;

    /**
     * 创建洞察。
     *
     * @param dto 洞察参数
     * @return 创建后的洞察
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmVocInsightEntity createInsight(ScrmVocInsightDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("洞察参数不能为空");
        }
        validateInsightEnums(dto, false);
        ScrmVocInsightEntity entity = new ScrmVocInsightEntity();
        entity.setInsightTitle(dto.getInsightTitle());
        entity.setInsightType(dto.getInsightType());
        entity.setDescription(dto.getDescription());
        entity.setSummary(dto.getSummary());
        entity.setSourceTopicIds(dto.getSourceTopicIds());
        entity.setSourceVoiceIds(dto.getSourceVoiceIds());
        entity.setRelatedVoiceCount(0);
        entity.setDataPoints(dto.getDataPoints());
        entity.setAnalysis(dto.getAnalysis());
        entity.setImpactLevel(dto.getImpactLevel() != null ? dto.getImpactLevel() : DEFAULT_IMPACT_LEVEL);
        entity.setImpactAreas(dto.getImpactAreas());
        entity.setAffectedSegments(dto.getAffectedSegments());
        entity.setEstimatedImpact(dto.getEstimatedImpact() != null ? dto.getEstimatedImpact() : 0.0);
        entity.setRecommendations(dto.getRecommendations());
        entity.setActionItems(dto.getActionItems());
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : DEFAULT_INSIGHT_STATUS);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : topicService.currentOperator());
        entity.setFeedbackCount(0);
        entity.setFeedbackRating(0.0);
        entity.setTags(dto.getTags());
        entity.setPeriod(dto.getPeriod());
        entity.setAnalysisDate(dto.getAnalysisDate() != null ? dto.getAnalysisDate() : LocalDate.now());
        entity = insightRepository.save(entity);
        log.info("创建洞察: id={}, title={}", entity.getId(), entity.getInsightTitle());
        return entity;
    }

    /**
     * 更新洞察 (字段非空才覆盖)。
     *
     * @param id  洞察 ID
     * @param dto 洞察参数
     * @return 更新后的洞察
     * @throws ScrmException 洞察不存在 / 参数非法
     */
    @Transactional
    public ScrmVocInsightEntity updateInsight(Long id, ScrmVocInsightDto dto) throws ScrmException {
        ScrmVocInsightEntity entity = findInsightOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("洞察参数不能为空");
        }
        validateInsightEnums(dto, true);
        if (dto.getInsightTitle() != null) entity.setInsightTitle(dto.getInsightTitle());
        if (dto.getInsightType() != null) entity.setInsightType(dto.getInsightType());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getSummary() != null) entity.setSummary(dto.getSummary());
        if (dto.getSourceTopicIds() != null) entity.setSourceTopicIds(dto.getSourceTopicIds());
        if (dto.getSourceVoiceIds() != null) entity.setSourceVoiceIds(dto.getSourceVoiceIds());
        if (dto.getDataPoints() != null) entity.setDataPoints(dto.getDataPoints());
        if (dto.getAnalysis() != null) entity.setAnalysis(dto.getAnalysis());
        if (dto.getImpactLevel() != null) entity.setImpactLevel(dto.getImpactLevel());
        if (dto.getImpactAreas() != null) entity.setImpactAreas(dto.getImpactAreas());
        if (dto.getAffectedSegments() != null) entity.setAffectedSegments(dto.getAffectedSegments());
        if (dto.getEstimatedImpact() != null) entity.setEstimatedImpact(dto.getEstimatedImpact());
        if (dto.getRecommendations() != null) entity.setRecommendations(dto.getRecommendations());
        if (dto.getActionItems() != null) entity.setActionItems(dto.getActionItems());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getPeriod() != null) entity.setPeriod(dto.getPeriod());
        if (dto.getAnalysisDate() != null) entity.setAnalysisDate(dto.getAnalysisDate());
        entity = insightRepository.save(entity);
        log.info("更新洞察: id={}", id);
        return entity;
    }

    /**
     * 删除洞察。
     *
     * @param id 洞察 ID
     * @throws ScrmException 洞察不存在
     */
    @Transactional
    public void deleteInsight(Long id) throws ScrmException {
        ScrmVocInsightEntity entity = findInsightOrThrow(id);
        insightRepository.delete(entity);
        log.info("删除洞察: id={}", id);
    }

    /**
     * 查询洞察详情。
     *
     * @param id 洞察 ID
     * @return 洞察实体
     * @throws ScrmException 洞察不存在
     */
    @Transactional(readOnly = true)
    public ScrmVocInsightEntity getInsight(Long id) throws ScrmException {
        return findInsightOrThrow(id);
    }

    /**
     * 分页查询洞察, 支持按类型 / 状态 / 影响等级 / 优先级 / 关键字过滤。
     *
     * @param insightType 洞察类型过滤 (可空)
     * @param status      状态过滤 (可空)
     * @param impactLevel 影响等级过滤 (可空)
     * @param priority    优先级过滤 (可空)
     * @param keyword     标题/摘要关键字 (可空)
     * @param pageable    分页参数
     * @return 洞察分页结果 (按 publishedAt DESC, createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmVocInsightEntity> listInsights(String insightType, String status, String impactLevel,
                                                    String priority, String keyword, Pageable pageable) {
        Specification<ScrmVocInsightEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (insightType != null && !insightType.isBlank()) {
                predicates.add(cb.equal(root.get("insightType"), insightType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (impactLevel != null && !impactLevel.isBlank()) {
                predicates.add(cb.equal(root.get("impactLevel"), impactLevel));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("insightTitle"), like),
                        cb.like(root.get("summary"), like)));
            }
            query.orderBy(cb.desc(root.get("publishedAt")), cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return insightRepository.findAll(spec, pageable);
    }

    /**
     * 发布洞察 (状态置为 PUBLISHED)。
     *
     * @param id          洞察 ID
     * @param publishedBy 发布人 (可空)
     * @return 更新后的洞察
     * @throws ScrmException 洞察不存在
     */
    @Transactional
    public ScrmVocInsightEntity publishInsight(Long id, String publishedBy) throws ScrmException {
        ScrmVocInsightEntity entity = findInsightOrThrow(id);
        entity.setStatus("PUBLISHED");
        entity.setPublishedAt(LocalDateTime.now());
        entity.setPublishedBy(publishedBy != null ? publishedBy : topicService.currentOperator());
        entity = insightRepository.save(entity);
        log.info("发布洞察: id={}, publishedBy={}", id, entity.getPublishedBy());
        return entity;
    }

    /**
     * 归档洞察 (状态置为 ARCHIVED)。
     *
     * @param id 洞察 ID
     * @return 更新后的洞察
     * @throws ScrmException 洞察不存在
     */
    @Transactional
    public ScrmVocInsightEntity archiveInsight(Long id) throws ScrmException {
        ScrmVocInsightEntity entity = findInsightOrThrow(id);
        entity.setStatus("ARCHIVED");
        entity = insightRepository.save(entity);
        log.info("归档洞察: id={}", id);
        return entity;
    }

    /**
     * 分享洞察 (追加分享目标用户)。
     *
     * @param id        洞察 ID
     * @param sharedWith 分享目标用户 ID 列表 (逗号分隔)
     * @return 更新后的洞察
     * @throws ScrmException 洞察不存在
     */
    @Transactional
    public ScrmVocInsightEntity shareInsight(Long id, String sharedWith) throws ScrmException {
        ScrmVocInsightEntity entity = findInsightOrThrow(id);
        Set<String> existing = topicService.parseCsv(entity.getSharedWith());
        Set<String> additions = topicService.parseCsv(sharedWith);
        existing.addAll(additions);
        entity.setSharedWith(String.join(",", existing));
        entity = insightRepository.save(entity);
        log.info("分享洞察: id={}, sharedWith={}", id, sharedWith);
        return entity;
    }

    /**
     * 生成洞察 (分析趋势 → 识别模式 → 生成建议)。
     * <p>基于指定主题的声音数据生成趋势 / 风险 / 模式洞察。</p>
     *
     * @param topicId 主题 ID (可空, 为空则对所有热点主题生成)
     * @param period  分析周期 (可空, 如 2026-Q3)
     * @return 生成的洞察列表
     * @throws ScrmException 主题不存在
     */
    @Transactional
    public List<ScrmVocInsightEntity> generateInsights(Long topicId, String period) throws ScrmException {
        List<ScrmVocInsightEntity> generated = new ArrayList<>();
        List<ScrmVocTopicEntity> topics;
        if (topicId != null) {
            topics = List.of(topicService.findTopicOrThrow(topicId));
        } else {
            topics = topicService.getHotTopics(20);
        }
        for (ScrmVocTopicEntity topic : topics) {
            List<ScrmVocVoiceEntity> voices = topicService.findVoicesForTopic(topic);
            if (voices.isEmpty()) {
                continue;
            }
            // 趋势洞察: 负面率高的主题生成 RISK 洞察
            if (topic.getNegativeRate() > 30) {
                generated.add(createInsightFromTopic(topic, voices, "RISK", period));
            }
            // 模式洞察: 声音数多的主题生成 PATTERN 洞察
            if (topic.getVoiceCount() >= HOT_TOPIC_THRESHOLD) {
                generated.add(createInsightFromTopic(topic, voices, "PATTERN", period));
            }
            // 机会洞察: 正面率高的主题生成 OPPORTUNITY 洞察
            if (topic.getPositiveRate() > 60) {
                generated.add(createInsightFromTopic(topic, voices, "OPPORTUNITY", period));
            }
        }
        log.info("生成洞察: topicId={}, period={}, 生成数={}", topicId, period, generated.size());
        return generated;
    }

    /**
     * 自动生成洞察 (对所有热点与新兴主题自动生成)。
     *
     * @param period 分析周期 (可空)
     * @return 生成的洞察列表
     */
    @Transactional
    public List<ScrmVocInsightEntity> autoGenerateInsights(String period) {
        List<ScrmVocInsightEntity> all = new ArrayList<>();
        try {
            all.addAll(generateInsights(null, period));
        } catch (Exception e) {
            log.warn("自动生成洞察失败: {}", e.getMessage());
        }
        log.info("自动生成洞察: period={}, 生成数={}", period, all.size());
        return all;
    }

    /**
     * 按主题查询洞察。
     *
     * @param topicId 主题 ID
     * @return 洞察列表
     */
    @Transactional(readOnly = true)
    public List<ScrmVocInsightEntity> getInsightsByTopic(Long topicId) {
        String topicIdStr = String.valueOf(topicId);
        return insightRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(root.get("sourceTopicIds"), "%" + topicIdStr + "%"));
            query.orderBy(cb.desc(root.get("publishedAt")), cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    /**
     * 查询可行动洞察 (status in PUBLISHED / ACTED_ON, impact in HIGH / CRITICAL)。
     *
     * @param pageable 分页参数
     * @return 洞察分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmVocInsightEntity> getActionableInsights(Pageable pageable) {
        List<String> statuses = List.of("PUBLISHED", "ACTED_ON");
        List<String> impacts = List.of("HIGH", "CRITICAL");
        Specification<ScrmVocInsightEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(statuses));
            predicates.add(root.get("impactLevel").in(impacts));
            query.orderBy(cb.desc(root.get("publishedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return insightRepository.findAll(spec, pageable);
    }

    /**
     * 添加洞察反馈 (递增反馈数, 更新平均评分)。
     *
     * @param id       洞察 ID
     * @param rating   评分 (1-5)
     * @param comment  反馈内容 (可空)
     * @return 更新后的洞察
     * @throws ScrmException 洞察不存在 / 评分越界
     */
    @Transactional
    public ScrmVocInsightEntity addFeedback(Long id, int rating, String comment) throws ScrmException {
        if (rating < 1 || rating > 5) {
            throw ScrmException.badRequest("评分必须在 1-5 之间: " + rating);
        }
        ScrmVocInsightEntity entity = findInsightOrThrow(id);
        int count = (entity.getFeedbackCount() != null ? entity.getFeedbackCount() : 0) + 1;
        double prevTotal = (entity.getFeedbackRating() != null ? entity.getFeedbackRating() : 0.0)
                * (entity.getFeedbackCount() != null ? entity.getFeedbackCount() : 0);
        double newAvg = topicService.round2((prevTotal + rating) / count);
        entity.setFeedbackCount(count);
        entity.setFeedbackRating(newAvg);
        entity = insightRepository.save(entity);
        log.info("添加洞察反馈: id={}, rating={}", id, rating);
        return entity;
    }

    /**
     * 查询 Top 洞察 (按反馈评分与影响等级排序)。
     *
     * @param limit 返回条数
     * @return 洞察列表
     */
    @Transactional(readOnly = true)
    public List<ScrmVocInsightEntity> getTopInsights(int limit) {
        return insightRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "PUBLISHED"));
            query.orderBy(cb.desc(root.get("feedbackRating")), cb.desc(root.get("publishedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        }).stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 按主键查询洞察, 不存在抛异常。
     */
    private ScrmVocInsightEntity findInsightOrThrow(Long id) throws ScrmException {
        return insightRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "洞察不存在: id=" + id));
    }

    /**
     * 校验洞察 DTO 枚举字段。
     */
    private void validateInsightEnums(ScrmVocInsightDto dto, boolean isUpdate) throws ScrmException {
        if (!isUpdate) {
            if (dto.getInsightType() != null && !VALID_INSIGHT_TYPES.contains(dto.getInsightType())) {
                throw ScrmException.badRequest("洞察类型非法: " + dto.getInsightType() + ", 仅支持 " + VALID_INSIGHT_TYPES);
            }
        }
        if (dto.getImpactLevel() != null && !VALID_IMPACT_LEVELS.contains(dto.getImpactLevel())) {
            throw ScrmException.badRequest("影响等级非法: " + dto.getImpactLevel() + ", 仅支持 " + VALID_IMPACT_LEVELS);
        }
        if (dto.getPriority() != null && !VALID_PRIORITIES.contains(dto.getPriority())) {
            throw ScrmException.badRequest("优先级非法: " + dto.getPriority() + ", 仅支持 " + VALID_PRIORITIES);
        }
        if (dto.getStatus() != null && !VALID_INSIGHT_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest("洞察状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_INSIGHT_STATUSES);
        }
    }

    /**
     * 从主题与声音创建洞察。
     */
    private ScrmVocInsightEntity createInsightFromTopic(ScrmVocTopicEntity topic, List<ScrmVocVoiceEntity> voices,
                                                         String insightType, String period) throws ScrmException {
        ScrmVocInsightDto dto = new ScrmVocInsightDto();
        dto.setInsightTitle("[" + topic.getTopicName() + "] " + insightTypeLabel(insightType) + "洞察");
        dto.setInsightType(insightType);
        String summary = String.format("主题 %s 共 %d 条声音, 正面率 %.1f%%, 负面率 %.1f%%, 平均情感分 %.2f",
                topic.getTopicName(), voices.size(), topic.getPositiveRate(), topic.getNegativeRate(),
                topic.getAvgSentimentScore());
        dto.setSummary(summary);
        dto.setSourceTopicIds(String.valueOf(topic.getId()));
        dto.setRelatedVoiceCount(voices.size());
        dto.setAnalysis(summary);
        dto.setImpactLevel(topic.getNegativeRate() > 50 ? "CRITICAL"
                : topic.getNegativeRate() > 30 ? "HIGH" : "MEDIUM");
        dto.setImpactAreas(topic.getCategory());
        dto.setEstimatedImpact(topic.getPriorityScore());
        dto.setRecommendations(generateRecommendation(topic, insightType));
        dto.setPriority(topic.getPriorityScore() > 75 ? "URGENT" : topic.getPriorityScore() > 50 ? "HIGH" : "MEDIUM");
        dto.setPeriod(period);
        dto.setTags(topic.getTopicCode());
        return createInsight(dto);
    }

    /**
     * 生成建议。
     */
    private String generateRecommendation(ScrmVocTopicEntity topic, String insightType) {
        switch (insightType) {
            case "RISK":
                return "主题 [" + topic.getTopicName() + "] 负面率较高 (" + topic.getNegativeRate()
                        + "%), 建议立即排查根因并制定改进计划, 优先处理负面声音。";
            case "OPPORTUNITY":
                return "主题 [" + topic.getTopicName() + "] 正面反馈较多 (" + topic.getPositiveRate()
                        + "%), 建议总结最佳实践并推广, 持续强化优势。";
            case "PATTERN":
                return "主题 [" + topic.getTopicName() + "] 声音数较多 (" + topic.getVoiceCount()
                        + " 条), 建议深入分析共性问题, 制定系统性改进方案。";
            default:
                return "建议持续关注主题 [" + topic.getTopicName() + "] 的趋势变化。";
        }
    }

    /**
     * 洞察类型中文标签。
     */
    private String insightTypeLabel(String type) {
        switch (type) {
            case "TREND": return "趋势";
            case "PATTERN": return "模式";
            case "ANOMALY": return "异常";
            case "OPPORTUNITY": return "机会";
            case "RISK": return "风险";
            case "ROOT_CAUSE": return "根因";
            case "BEST_PRACTICE": return "最佳实践";
            case "LESSON_LEARNED": return "经验教训";
            default: return type;
        }
    }
}
