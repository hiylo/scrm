/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocVoiceService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmVocAnalysisDto;
import org.hiylo.scrm.dto.ScrmVocResponseDto;
import org.hiylo.scrm.dto.ScrmVocVoiceDto;
import org.hiylo.scrm.entity.ScrmVocTopicEntity;
import org.hiylo.scrm.entity.ScrmVocVoiceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmVocTopicRepository;
import org.hiylo.scrm.repository.ScrmVocVoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 客户之声 (VoC) 声音子域服务。
 * <p>
 * 承载声音管理核心能力: 全渠道声音的增删改查 / 分析 / 分配 / 解决 / 关闭 / 归档 / 验证 / 回复 /
 * 相似与时间线, 以及主题分配声音。情感分析与分类匹配等文本分析辅助逻辑在本子域内完成。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmVocVoiceService {

    /** 默认语言 */
    private static final String DEFAULT_LANGUAGE = "zh-CN";
    /** 默认情感 */
    private static final String DEFAULT_SENTIMENT = "NEUTRAL";
    /** 默认情感得分 */
    private static final double DEFAULT_SENTIMENT_SCORE = 0.0;
    /** 默认优先级 */
    private static final String DEFAULT_PRIORITY = "MEDIUM";
    /** 默认声音状态 */
    private static final String DEFAULT_VOICE_STATUS = "NEW";
    /** 情感得分上限 */
    private static final double MAX_SENTIMENT_SCORE = 1.0;
    /** 情感得分下限 */
    private static final double MIN_SENTIMENT_SCORE = -1.0;

    /** 合法的来源渠道 */
    private static final List<String> VALID_SOURCES = List.of(
            "SURVEY", "INTERVIEW", "REVIEW", "SOCIAL_MEDIA", "CALL_CENTER", "EMAIL", "CHAT",
            "APP_STORE", "REFERRAL", "COMPLAINT", "SUGGESTION", "OTHER");
    /** 合法的声音类型 */
    private static final List<String> VALID_VOICE_TYPES = List.of(
            "COMPLAINT", "COMPLIMENT", "SUGGESTION", "QUESTION", "FEEDBACK", "REVIEW", "RATING", "INQUIRY");
    /** 合法的情感 */
    private static final List<String> VALID_SENTIMENTS = List.of("POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED");
    /** 合法的优先级 */
    private static final List<String> VALID_PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "URGENT");
    /** 合法的声音状态 */
    private static final List<String> VALID_VOICE_STATUSES = List.of(
            "NEW", "ANALYZING", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED", "ARCHIVED", "IGNORED");

    /** 正面情感关键词 */
    private static final List<String> POSITIVE_KEYWORDS = List.of(
            "好", "优秀", "满意", "喜欢", "推荐", "赞", "不错", "棒", "谢谢", "感谢", "给力", "流畅",
            "方便", "优质", "贴心", "专业", "热情", "舒适", "完美", "超值", "划算", "信赖", "靠谱",
            "惊喜", "惊艳", "到位", "高效", "耐心", "细致", "划算", "物美价廉", "好评", "点赞");
    /** 负面情感关键词 */
    private static final List<String> NEGATIVE_KEYWORDS = List.of(
            "差", "糟糕", "不满", "失望", "投诉", "问题", "不好", "难用", "慢", "坏", "卡", "退货",
            "报错", "错误", "麻烦", "讨厌", "浪费", "贵", "假", "骗", "坑", "垃圾", "无语", "气愤",
            "退款", "售后差", "态度差", "质量差", "延迟", "过期", "破损", "缺货", "虚假", "敷衍",
            "踢皮球", "不解决", "无人理", "差评", "后悔");

    /** VoC 声音数据访问层 */
    private final ScrmVocVoiceRepository voiceRepository;
    /** VoC 主题数据访问层 */
    private final ScrmVocTopicRepository topicRepository;
    /** VoC 主题子域服务 (复用主题统计 / 分析辅助能力) */
    private final ScrmVocTopicService topicService;

    /**
     * 创建客户声音。
     * <p>生成声音编号, sentiment / priority / status / language 缺省时填默认值, 计数缺省 0。</p>
     *
     * @param dto 声音参数
     * @return 创建后的声音
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmVocVoiceEntity createVoice(ScrmVocVoiceDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("声音参数不能为空");
        }
        validateVoiceEnums(dto, false);
        ScrmVocVoiceEntity entity = new ScrmVocVoiceEntity();
        entity.setVoiceNo(generateVoiceNo());
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setCustomerContact(dto.getCustomerContact());
        entity.setSource(dto.getSource());
        entity.setSourceDetail(dto.getSourceDetail());
        entity.setSourceUrl(dto.getSourceUrl());
        entity.setVoiceType(dto.getVoiceType());
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent());
        entity.setOriginalContent(dto.getOriginalContent());
        entity.setLanguage(dto.getLanguage() != null && !dto.getLanguage().isBlank()
                ? dto.getLanguage() : DEFAULT_LANGUAGE);
        entity.setSentiment(dto.getSentiment() != null ? dto.getSentiment() : DEFAULT_SENTIMENT);
        entity.setSentimentScore(dto.getSentimentScore() != null ? dto.getSentimentScore() : DEFAULT_SENTIMENT_SCORE);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setCategory(dto.getCategory());
        entity.setSubCategory(dto.getSubCategory());
        entity.setTags(dto.getTags());
        entity.setRating(dto.getRating());
        entity.setProductId(dto.getProductId());
        entity.setProductName(dto.getProductName());
        entity.setOrderId(dto.getOrderId());
        entity.setServiceId(dto.getServiceId());
        entity.setDepartment(dto.getDepartment());
        entity.setAssignedTo(dto.getAssignedTo());
        entity.setAssignedAt(dto.getAssignedAt());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : DEFAULT_VOICE_STATUS);
        entity.setCollectedAt(dto.getCollectedAt() != null ? dto.getCollectedAt() : LocalDateTime.now());
        entity.setCollectedBy(dto.getCollectedBy());
        entity.setResolutionTimeHours(0);
        entity.setIsPublic(dto.getIsPublic() != null ? dto.getIsPublic() : Boolean.FALSE);
        entity.setIsVerified(dto.getIsVerified() != null ? dto.getIsVerified() : Boolean.FALSE);
        entity.setVerifiedBy(dto.getVerifiedBy());
        entity.setResponseCount(0);
        entity.setLikeCount(0);
        entity.setViewCount(0);
        entity.setShareCount(0);
        entity.setAttachments(dto.getAttachments());
        entity.setMetadata(dto.getMetadata());
        entity.setRelatedVoiceIds(dto.getRelatedVoiceIds());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : topicService.currentOperator());
        entity = voiceRepository.save(entity);
        log.info("创建客户声音: id={}, voiceNo={}", entity.getId(), entity.getVoiceNo());
        return entity;
    }

    /**
     * 更新客户声音 (字段非空才覆盖)。
     *
     * @param id  声音 ID
     * @param dto 声音参数
     * @return 更新后的声音
     * @throws ScrmException 声音不存在 / 参数非法
     */
    @Transactional
    public ScrmVocVoiceEntity updateVoice(Long id, ScrmVocVoiceDto dto) throws ScrmException {
        ScrmVocVoiceEntity entity = findVoiceOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("声音参数不能为空");
        }
        validateVoiceEnums(dto, true);
        if (dto.getCustomerName() != null) entity.setCustomerName(dto.getCustomerName());
        if (dto.getCustomerContact() != null) entity.setCustomerContact(dto.getCustomerContact());
        if (dto.getSourceDetail() != null) entity.setSourceDetail(dto.getSourceDetail());
        if (dto.getSourceUrl() != null) entity.setSourceUrl(dto.getSourceUrl());
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getOriginalContent() != null) entity.setOriginalContent(dto.getOriginalContent());
        if (dto.getLanguage() != null) entity.setLanguage(dto.getLanguage());
        if (dto.getSentiment() != null) entity.setSentiment(dto.getSentiment());
        if (dto.getSentimentScore() != null) entity.setSentimentScore(dto.getSentimentScore());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getSubCategory() != null) entity.setSubCategory(dto.getSubCategory());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getRating() != null) entity.setRating(dto.getRating());
        if (dto.getProductName() != null) entity.setProductName(dto.getProductName());
        if (dto.getOrderId() != null) entity.setOrderId(dto.getOrderId());
        if (dto.getServiceId() != null) entity.setServiceId(dto.getServiceId());
        if (dto.getDepartment() != null) entity.setDepartment(dto.getDepartment());
        if (dto.getAssignedTo() != null) entity.setAssignedTo(dto.getAssignedTo());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getResolution() != null) entity.setResolution(dto.getResolution());
        if (dto.getCustomerSatisfaction() != null) entity.setCustomerSatisfaction(dto.getCustomerSatisfaction());
        if (dto.getIsPublic() != null) entity.setIsPublic(dto.getIsPublic());
        if (dto.getAttachments() != null) entity.setAttachments(dto.getAttachments());
        if (dto.getMetadata() != null) entity.setMetadata(dto.getMetadata());
        if (dto.getRelatedVoiceIds() != null) entity.setRelatedVoiceIds(dto.getRelatedVoiceIds());
        entity = voiceRepository.save(entity);
        log.info("更新客户声音: id={}, voiceNo={}", id, entity.getVoiceNo());
        return entity;
    }

    /**
     * 删除客户声音。
     *
     * @param id 声音 ID
     * @throws ScrmException 声音不存在
     */
    @Transactional
    public void deleteVoice(Long id) throws ScrmException {
        ScrmVocVoiceEntity entity = findVoiceOrThrow(id);
        voiceRepository.delete(entity);
        log.info("删除客户声音: id={}, voiceNo={}", id, entity.getVoiceNo());
    }

    /**
     * 查询声音详情。
     *
     * @param id 声音 ID
     * @return 声音实体
     * @throws ScrmException 声音不存在
     */
    @Transactional(readOnly = true)
    public ScrmVocVoiceEntity getVoice(Long id) throws ScrmException {
        return findVoiceOrThrow(id);
    }

    /**
     * 按声音编号查询声音。
     *
     * @param voiceNo 声音编号
     * @return 声音实体
     * @throws ScrmException 声音不存在
     */
    @Transactional(readOnly = true)
    public ScrmVocVoiceEntity getVoiceByNo(String voiceNo) throws ScrmException {
        if (voiceNo == null || voiceNo.isBlank()) {
            throw ScrmException.badRequest("声音编号不能为空");
        }
        return voiceRepository.findByVoiceNo(voiceNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户声音不存在: voiceNo=" + voiceNo));
    }

    /**
     * 分页查询声音, 支持多条件过滤。
     *
     * @param source     来源渠道过滤 (可空)
     * @param voiceType  声音类型过滤 (可空)
     * @param sentiment  情感过滤 (可空)
     * @param priority   优先级过滤 (可空)
     * @param status     状态过滤 (可空)
     * @param category   分类过滤 (可空)
     * @param customerId 客户 ID 过滤 (可空)
     * @param keyword    标题/内容关键字 (可空)
     * @param startTime  收集起始时间 (可空)
     * @param endTime    收集截止时间 (可空)
     * @param pageable   分页参数
     * @return 声音分页结果 (按 collectedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmVocVoiceEntity> listVoices(String source, String voiceType, String sentiment, String priority,
                                                String status, String category, Long customerId, String keyword,
                                                LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmVocVoiceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (voiceType != null && !voiceType.isBlank()) {
                predicates.add(cb.equal(root.get("voiceType"), voiceType));
            }
            if (sentiment != null && !sentiment.isBlank()) {
                predicates.add(cb.equal(root.get("sentiment"), sentiment));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("title"), like),
                        cb.like(root.get("content"), like)));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("collectedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("collectedAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("collectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return voiceRepository.findAll(spec, pageable);
    }

    /**
     * 按客户分页查询声音。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 声音分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmVocVoiceEntity> getVoicesByCustomer(Long customerId, Pageable pageable) {
        Specification<ScrmVocVoiceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            query.orderBy(cb.desc(root.get("collectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return voiceRepository.findAll(spec, pageable);
    }

    /**
     * 按主题分页查询声音 (通过主题编码匹配声音 tags)。
     *
     * @param topicId  主题 ID
     * @param pageable 分页参数
     * @return 声音分页结果
     * @throws ScrmException 主题不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmVocVoiceEntity> getVoicesByTopic(Long topicId, Pageable pageable) throws ScrmException {
        ScrmVocTopicEntity topic = topicService.findTopicOrThrow(topicId);
        String topicCode = topic.getTopicCode();
        Specification<ScrmVocVoiceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(root.get("tags"), "%" + topicCode + "%"));
            query.orderBy(cb.desc(root.get("collectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return voiceRepository.findAll(spec, pageable);
    }

    /**
     * 分析单条声音 (设置情感 / 分类 / 主题 / 关键词)。
     * <p>sentiment 为空时自动分析; category 为空时按内容自动匹配; 关键词与自动提取的合并去重写入 tags;
     * topicIds 非空时将主题编码追加到 tags 并刷新主题统计; summary 写入 tags。</p>
     *
     * @param dto 分析参数
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @Transactional
    public ScrmVocVoiceEntity analyzeVoice(ScrmVocAnalysisDto dto) throws ScrmException {
        if (dto == null || dto.getVoiceId() == null) {
            throw ScrmException.badRequest("分析参数不能为空");
        }
        ScrmVocVoiceEntity voice = findVoiceOrThrow(dto.getVoiceId());
        String content = voice.getContent();
        // 情感分析
        String sentiment = dto.getSentiment();
        double sentimentScore;
        if (sentiment == null || sentiment.isBlank()) {
            sentimentScore = analyzeSentiment(content);
            sentiment = sentimentLabel(sentimentScore);
        } else {
            sentimentScore = analyzeSentiment(content);
        }
        voice.setSentiment(sentiment);
        voice.setSentimentScore(BigDecimal.valueOf(sentimentScore).setScale(2, RoundingMode.HALF_UP).doubleValue());
        // 分类
        if (dto.getCategory() != null && !dto.getCategory().isBlank()) {
            voice.setCategory(dto.getCategory());
        } else if (voice.getCategory() == null || voice.getCategory().isBlank()) {
            voice.setCategory(matchCategory(content));
        }
        // 关键词提取与合并
        List<String> autoKeywords = topicService.extractKeywords(content);
        List<String> mergedKeywords = new ArrayList<>(autoKeywords);
        if (dto.getKeywords() != null) {
            for (String kw : dto.getKeywords()) {
                if (kw != null && !kw.isBlank() && !mergedKeywords.contains(kw)) {
                    mergedKeywords.add(kw);
                }
            }
        }
        // 主题关联: 追加主题编码到 tags
        if (dto.getTopicIds() != null && !dto.getTopicIds().isEmpty()) {
            for (Long topicId : dto.getTopicIds()) {
                ScrmVocTopicEntity topic = topicRepository.findById(topicId)
                        .orElse(null);
                if (topic != null && !mergedKeywords.contains(topic.getTopicCode())) {
                    mergedKeywords.add(topic.getTopicCode());
                }
            }
        }
        // summary 追加
        if (dto.getSummary() != null && !dto.getSummary().isBlank()) {
            mergedKeywords.add(dto.getSummary());
        }
        voice.setTags(String.join(",", mergedKeywords));
        voice.setAnalyzedAt(LocalDateTime.now());
        if ("NEW".equals(voice.getStatus())) {
            voice.setStatus("ANALYZING");
        }
        voice = voiceRepository.save(voice);
        // 刷新关联主题统计
        if (dto.getTopicIds() != null) {
            for (Long topicId : dto.getTopicIds()) {
                try {
                    topicService.updateTopicStats(topicId);
                } catch (Exception e) {
                    log.warn("刷新主题统计失败: topicId={}, error={}", topicId, e.getMessage());
                }
            }
        }
        log.info("分析客户声音: id={}, sentiment={}, score={}", voice.getId(), voice.getSentiment(),
                voice.getSentimentScore());
        return voice;
    }

    /**
     * 批量分析声音。
     *
     * @param voiceIds 声音 ID 列表
     * @return 批量结果 {total, success, failed}
     */
    @Transactional
    public Map<String, Integer> batchAnalyze(List<Long> voiceIds) {
        Map<String, Integer> result = new LinkedHashMap<>();
        int success = 0;
        int failed = 0;
        if (voiceIds == null || voiceIds.isEmpty()) {
            result.put("total", 0);
            result.put("success", 0);
            result.put("failed", 0);
            return result;
        }
        for (Long voiceId : voiceIds) {
            try {
                ScrmVocAnalysisDto dto = new ScrmVocAnalysisDto();
                dto.setVoiceId(voiceId);
                analyzeVoice(dto);
                success++;
            } catch (Exception e) {
                log.warn("批量分析声音失败: id={}, error={}", voiceId, e.getMessage());
                failed++;
            }
        }
        result.put("total", voiceIds.size());
        result.put("success", success);
        result.put("failed", failed);
        return result;
    }

    /**
     * 分配声音处理人。
     *
     * @param id         声音 ID
     * @param assigneeId 处理人 ID
     * @param department 责任部门 (可空)
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @Transactional
    public ScrmVocVoiceEntity assignVoice(Long id, String assigneeId, String department) throws ScrmException {
        ScrmVocVoiceEntity voice = findVoiceOrThrow(id);
        voice.setAssignedTo(assigneeId);
        voice.setAssignedAt(LocalDateTime.now());
        if (department != null && !department.isBlank()) {
            voice.setDepartment(department);
        }
        if ("NEW".equals(voice.getStatus()) || "ANALYZING".equals(voice.getStatus())) {
            voice.setStatus("ASSIGNED");
        }
        voice = voiceRepository.save(voice);
        log.info("分配声音: id={}, assignee={}", id, assigneeId);
        return voice;
    }

    /**
     * 解决声音。
     *
     * @param id         声音 ID
     * @param resolution 解决方案
     * @param resolvedBy 解决人 (可空)
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @Transactional
    public ScrmVocVoiceEntity resolveVoice(Long id, String resolution, String resolvedBy) throws ScrmException {
        ScrmVocVoiceEntity voice = findVoiceOrThrow(id);
        voice.setStatus("RESOLVED");
        voice.setResolution(resolution);
        voice.setResolvedAt(LocalDateTime.now());
        if (voice.getCollectedAt() != null) {
            long hours = java.time.Duration.between(voice.getCollectedAt(), voice.getResolvedAt()).toHours();
            voice.setResolutionTimeHours((int) hours);
        }
        voice = voiceRepository.save(voice);
        log.info("解决声音: id={}, resolvedBy={}", id, resolvedBy);
        return voice;
    }

    /**
     * 关闭声音。
     *
     * @param id           声音 ID
     * @param satisfaction 解决后满意度 (1-5, 可空)
     * @param closedBy     关闭人 (可空)
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @Transactional
    public ScrmVocVoiceEntity closeVoice(Long id, Integer satisfaction, String closedBy) throws ScrmException {
        ScrmVocVoiceEntity voice = findVoiceOrThrow(id);
        voice.setStatus("CLOSED");
        if (satisfaction != null) {
            voice.setCustomerSatisfaction(satisfaction);
        }
        voice = voiceRepository.save(voice);
        log.info("关闭声音: id={}, closedBy={}", id, closedBy);
        return voice;
    }

    /**
     * 归档声音。
     *
     * @param id         声音 ID
     * @param archivedBy 归档人 (可空)
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @Transactional
    public ScrmVocVoiceEntity archiveVoice(Long id, String archivedBy) throws ScrmException {
        ScrmVocVoiceEntity voice = findVoiceOrThrow(id);
        voice.setStatus("ARCHIVED");
        voice = voiceRepository.save(voice);
        log.info("归档声音: id={}, archivedBy={}", id, archivedBy);
        return voice;
    }

    /**
     * 验证声音。
     *
     * @param id         声音 ID
     * @param verifiedBy 验证人 (可空)
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @Transactional
    public ScrmVocVoiceEntity verifyVoice(Long id, String verifiedBy) throws ScrmException {
        ScrmVocVoiceEntity voice = findVoiceOrThrow(id);
        voice.setIsVerified(true);
        voice.setVerifiedBy(verifiedBy != null ? verifiedBy : topicService.currentOperator());
        voice = voiceRepository.save(voice);
        log.info("验证声音: id={}, verifiedBy={}", id, voice.getVerifiedBy());
        return voice;
    }

    /**
     * 回复声音 (递增回复数)。
     *
     * @param dto 回复参数
     * @return 更新后的声音
     * @throws ScrmException 声音不存在
     */
    @Transactional
    public ScrmVocVoiceEntity respondToVoice(ScrmVocResponseDto dto) throws ScrmException {
        if (dto == null || dto.getVoiceId() == null) {
            throw ScrmException.badRequest("回复参数不能为空");
        }
        ScrmVocVoiceEntity voice = findVoiceOrThrow(dto.getVoiceId());
        voice.setResponseCount((voice.getResponseCount() != null ? voice.getResponseCount() : 0) + 1);
        if ("NEW".equals(voice.getStatus()) || "ASSIGNED".equals(voice.getStatus())) {
            voice.setStatus("IN_PROGRESS");
        }
        voice = voiceRepository.save(voice);
        log.info("回复声音: id={}, responder={}", dto.getVoiceId(), dto.getResponderId());
        return voice;
    }

    /**
     * 查询新声音 (status = NEW)。
     *
     * @param pageable 分页参数
     * @return 声音分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmVocVoiceEntity> getNewVoices(Pageable pageable) {
        return findVoicesByStatus("NEW", pageable);
    }

    /**
     * 查询待处理声音 (status in NEW / ANALYZING / ASSIGNED / IN_PROGRESS)。
     *
     * @param pageable 分页参数
     * @return 声音分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmVocVoiceEntity> getPendingVoices(Pageable pageable) {
        List<String> pendingStatuses = List.of("NEW", "ANALYZING", "ASSIGNED", "IN_PROGRESS");
        Specification<ScrmVocVoiceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(pendingStatuses));
            query.orderBy(cb.desc(root.get("collectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return voiceRepository.findAll(spec, pageable);
    }

    /**
     * 查询紧急声音 (priority = URGENT)。
     *
     * @param pageable 分页参数
     * @return 声音分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmVocVoiceEntity> getUrgentVoices(Pageable pageable) {
        Specification<ScrmVocVoiceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("priority"), "URGENT"));
            query.orderBy(cb.desc(root.get("collectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return voiceRepository.findAll(spec, pageable);
    }

    /**
     * 查询负面声音 (sentiment = NEGATIVE)。
     *
     * @param pageable 分页参数
     * @return 声音分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmVocVoiceEntity> getNegativeVoices(Pageable pageable) {
        Specification<ScrmVocVoiceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("sentiment"), "NEGATIVE"));
            query.orderBy(cb.desc(root.get("collectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return voiceRepository.findAll(spec, pageable);
    }

    /**
     * 查询未解决声音 (status not in RESOLVED / CLOSED / ARCHIVED / IGNORED)。
     *
     * @param pageable 分页参数
     * @return 声音分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmVocVoiceEntity> getUnresolvedVoices(Pageable pageable) {
        List<String> resolvedStatuses = List.of("RESOLVED", "CLOSED", "ARCHIVED", "IGNORED");
        Specification<ScrmVocVoiceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.not(root.get("status").in(resolvedStatuses)));
            query.orderBy(cb.desc(root.get("collectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return voiceRepository.findAll(spec, pageable);
    }

    /**
     * 客户声音时间线 (按客户查询声音, 按收集时间正序)。
     *
     * @param customerId 客户 ID
     * @return 声音列表
     */
    @Transactional(readOnly = true)
    public List<ScrmVocVoiceEntity> getVoiceTimeline(Long customerId) {
        Specification<ScrmVocVoiceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            query.orderBy(cb.asc(root.get("collectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return voiceRepository.findAll(spec);
    }

    /**
     * 查询相似声音 (按分类与标签匹配)。
     *
     * @param voiceId 声音 ID
     * @return 相似声音列表
     * @throws ScrmException 声音不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmVocVoiceEntity> getSimilarVoices(Long voiceId) throws ScrmException {
        ScrmVocVoiceEntity voice = findVoiceOrThrow(voiceId);
        List<ScrmVocVoiceEntity> candidates = voiceRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("id"), voiceId));
            if (voice.getCategory() != null && !voice.getCategory().isBlank()) {
                predicates.add(cb.equal(root.get("category"), voice.getCategory()));
            }
            query.orderBy(cb.desc(root.get("collectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        // 按标签重叠度排序
        Set<String> voiceTags = topicService.parseCsv(voice.getTags());
        return candidates.stream()
                .sorted((a, b) -> Integer.compare(
                        tagOverlap(topicService.parseCsv(b.getTags()), voiceTags),
                        tagOverlap(topicService.parseCsv(a.getTags()), voiceTags)))
                .limit(20)
                .collect(Collectors.toList());
    }

    /**
     * 将主题分配给声音 (追加主题编码到声音 tags, 刷新主题统计)。
     *
     * @param voiceId 声音 ID
     * @param topicId 主题 ID
     * @return 更新后的声音
     * @throws ScrmException 声音 / 主题不存在
     */
    @Transactional
    public ScrmVocVoiceEntity assignTopicToVoice(Long voiceId, Long topicId) throws ScrmException {
        ScrmVocVoiceEntity voice = findVoiceOrThrow(voiceId);
        ScrmVocTopicEntity topic = topicService.findTopicOrThrow(topicId);
        Set<String> tags = topicService.parseCsv(voice.getTags());
        if (!tags.contains(topic.getTopicCode())) {
            tags.add(topic.getTopicCode());
            voice.setTags(String.join(",", tags));
            voice = voiceRepository.save(voice);
        }
        topicService.updateTopicStats(topicId);
        log.info("主题分配给声音: voiceId={}, topicId={}", voiceId, topicId);
        return voice;
    }

    /**
     * 情感分析 (关键词匹配 → 计算情感分 -1 到 1)。
     * <p>统计正面与负面关键词命中数, 计算 score = (pos - neg) / (pos + neg), 无命中返回 0。</p>
     *
     * @param content 文本内容
     * @return 情感分 (-1.0 ~ 1.0)
     */
    private double analyzeSentiment(String content) {
        if (content == null || content.isBlank()) {
            return 0.0;
        }
        int positive = 0;
        int negative = 0;
        for (String kw : POSITIVE_KEYWORDS) {
            if (content.contains(kw)) {
                positive++;
            }
        }
        for (String kw : NEGATIVE_KEYWORDS) {
            if (content.contains(kw)) {
                negative++;
            }
        }
        if (positive + negative == 0) {
            return 0.0;
        }
        double score = (double) (positive - negative) / (positive + negative);
        return Math.max(MIN_SENTIMENT_SCORE, Math.min(MAX_SENTIMENT_SCORE, score));
    }

    /**
     * 情感分转情感标签。
     */
    private String sentimentLabel(double score) {
        if (score > 0.2) {
            return "POSITIVE";
        } else if (score < -0.2) {
            return "NEGATIVE";
        } else if (score != 0) {
            return "MIXED";
        }
        return "NEUTRAL";
    }

    /**
     * 按内容匹配分类。
     */
    private String matchCategory(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        if (content.contains("价格") || content.contains("贵") || content.contains("便宜") || content.contains("划算")) {
            return "PRICE";
        } else if (content.contains("质量") || content.contains("坏")
                || content.contains("破损") || content.contains("损坏")) {
            return "QUALITY";
        } else if (content.contains("配送") || content.contains("物流")
                || content.contains("快递") || content.contains("发货")) {
            return "DELIVERY";
        } else if (content.contains("客服") || content.contains("售后") || content.contains("服务")) {
            return "SUPPORT";
        } else if (content.contains("产品") || content.contains("商品") || content.contains("功能")) {
            return "PRODUCT";
        } else if (content.contains("体验") || content.contains("界面") || content.contains("操作")) {
            return "EXPERIENCE";
        }
        return "OTHER";
    }

    /**
     * 按主键查询声音, 不存在抛异常。
     */
    private ScrmVocVoiceEntity findVoiceOrThrow(Long id) throws ScrmException {
        return voiceRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户声音不存在: id=" + id));
    }

    /**
     * 按状态分页查询声音。
     */
    private Page<ScrmVocVoiceEntity> findVoicesByStatus(String status, Pageable pageable) {
        Specification<ScrmVocVoiceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), status));
            query.orderBy(cb.desc(root.get("collectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return voiceRepository.findAll(spec, pageable);
    }

    /**
     * 校验声音 DTO 枚举字段。
     */
    private void validateVoiceEnums(ScrmVocVoiceDto dto, boolean isUpdate) throws ScrmException {
        if (!isUpdate) {
            if (dto.getSource() != null && !VALID_SOURCES.contains(dto.getSource())) {
                throw ScrmException.badRequest("来源渠道非法: " + dto.getSource() + ", 仅支持 " + VALID_SOURCES);
            }
            if (dto.getVoiceType() != null && !VALID_VOICE_TYPES.contains(dto.getVoiceType())) {
                throw ScrmException.badRequest("声音类型非法: " + dto.getVoiceType() + ", 仅支持 " + VALID_VOICE_TYPES);
            }
        }
        if (dto.getSentiment() != null && !VALID_SENTIMENTS.contains(dto.getSentiment())) {
            throw ScrmException.badRequest("情感非法: " + dto.getSentiment() + ", 仅支持 " + VALID_SENTIMENTS);
        }
        if (dto.getPriority() != null && !VALID_PRIORITIES.contains(dto.getPriority())) {
            throw ScrmException.badRequest("优先级非法: " + dto.getPriority() + ", 仅支持 " + VALID_PRIORITIES);
        }
        if (dto.getStatus() != null && !VALID_VOICE_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest("状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_VOICE_STATUSES);
        }
    }

    /**
     * 生成声音编号: VOC + 年月日 + 4 位序号。
     *
     * @return 声音编号
     */
    private String generateVoiceNo() {
        String prefix = "VOC" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        long seq = voiceRepository.countByVoiceNoStartingWith(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }

    /**
     * 计算两组标签的重叠数。
     */
    private int tagOverlap(Set<String> a, Set<String> b) {
        int count = 0;
        for (String s : a) {
            if (b.contains(s)) {
                count++;
            }
        }
        return count;
    }
}
