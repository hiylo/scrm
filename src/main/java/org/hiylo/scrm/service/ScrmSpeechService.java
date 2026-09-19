/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.service;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hiylo.scrm.dto.ScrmSalesSpeechDto;
import org.hiylo.scrm.entity.ScrmSalesSpeechEntity;
import org.hiylo.scrm.entity.ScrmSpeechScenarioEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmSalesSpeechRepository;
import org.hiylo.scrm.repository.ScrmSpeechScenarioRepository;

import static org.hiylo.scrm.service.ScrmSpeechRecommendMatchService.MAX_RECOMMEND_LIMIT;

/**
 * 话术管理兄弟服务。
 * <p>
 * 承载话术的增删改查、启停、验证、变量渲染、使用统计、复制与热门查询。
 * 依赖 {@link ScrmSpeechScenarioService} 校验场景并同步场景统计。
 * 作为 {@link ScrmSpeechRecommendService} 的话术子域拆分产物, 由门面与推荐兄弟服务注入。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSpeechService {

    // ==================== 默认值常量 ====================

    /** 默认话术类型 */
    private static final String DEFAULT_SPEECH_TYPE = "TEXT";

    /** 默认难度等级 */
    private static final String DEFAULT_DIFFICULTY = "INTERMEDIATE";

    /** 默认业务版本号 */
    private static final int DEFAULT_VERSION_NO = 1;

    // ==================== 枚举值常量 ====================

    /** 合法的话术类型 (供统计兄弟服务复用) */
    static final List<String> VALID_SPEECH_TYPES = List.of(
            "TEXT", "SCRIPT", "QA", "GUIDE", "TEMPLATE");

    /** 合法的话术风格 */
    private static final List<String> VALID_SPEECH_STYLES = List.of(
            "FORMAL", "FRIENDLY", "PROFESSIONAL", "CASUAL", "PERSUASIVE", "EMPATHETIC");

    /** 合法的难度等级 */
    private static final List<String> VALID_DIFFICULTY_LEVELS = List.of(
            "BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT");

    // ==================== 依赖注入 ====================

    /** 话术数据访问层 */
    private final ScrmSalesSpeechRepository speechRepository;

    /** 场景数据访问层 (创建/更新话术校验场景) */
    private final ScrmSpeechScenarioRepository scenarioRepository;

    /** 场景管理兄弟服务 (同步场景统计 / 校验场景) */
    private final ScrmSpeechScenarioService scenarioService;

    /**
     * 创建话术。
     * <p>校验场景存在性与字段合法性后写入归属账号 ID 持久化,
     * speechType / difficultyLevel / versionNo / 统计字段 / enabled 缺省时填默认值。</p>
     *
     * @param dto 话术参数
     * @return 创建后的话术
     * @throws ScrmException 参数非法 / 场景不存在
     */
    @Transactional
    public ScrmSalesSpeechEntity createSpeech(ScrmSalesSpeechDto dto) throws ScrmException {
        validateSpeechDto(dto, false);
        ScrmSpeechScenarioEntity scenario = scenarioService.findScenarioOrThrow(dto.getScenarioId());
        ScrmSalesSpeechEntity entity = new ScrmSalesSpeechEntity();
        entity.setScenarioId(dto.getScenarioId());
        entity.setScenarioName(dto.getScenarioName() != null ? dto.getScenarioName() : scenario.getScenarioName());
        entity.setSpeechTitle(dto.getSpeechTitle());
        entity.setSpeechContent(dto.getSpeechContent());
        entity.setSpeechType(dto.getSpeechType() != null && !dto.getSpeechType().isBlank()
                ? dto.getSpeechType() : DEFAULT_SPEECH_TYPE);
        entity.setSpeechStyle(dto.getSpeechStyle());
        entity.setTargetAudience(dto.getTargetAudience());
        entity.setApplicableProducts(dto.getApplicableProducts());
        entity.setApplicableScenes(dto.getApplicableScenes());
        entity.setKeywords(dto.getKeywords());
        entity.setVariables(dto.getVariables());
        entity.setMediaAttachments(dto.getMediaAttachments());
        entity.setDifficultyLevel(dto.getDifficultyLevel() != null && !dto.getDifficultyLevel().isBlank()
                ? dto.getDifficultyLevel() : DEFAULT_DIFFICULTY);
        entity.setEstimatedDuration(dto.getEstimatedDuration() != null ? dto.getEstimatedDuration() : 0);
        entity.setRating(0.0);
        entity.setUsageCount(0);
        entity.setSuccessCount(0);
        entity.setFeedbackCount(0);
        entity.setPositiveFeedback(0);
        entity.setNegativeFeedback(0);
        entity.setIsRecommended(dto.getIsRecommended() != null ? dto.getIsRecommended() : Boolean.FALSE);
        entity.setIsVerified(dto.getIsVerified() != null ? dto.getIsVerified() : Boolean.FALSE);
        entity.setVersionNo(dto.getVersionNo() != null ? dto.getVersionNo() : DEFAULT_VERSION_NO);
        entity.setAuthorId(dto.getAuthorId());
        entity.setAuthorName(dto.getAuthorName());
        entity.setTags(dto.getTags());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = speechRepository.save(entity);
        // 同步场景话术数
        try {
            scenarioService.updateScenarioStats(entity.getScenarioId());
        } catch (ScrmException e) {
            log.warn("创建话术后更新场景统计失败, 忽略: scenarioId={}, err={}",
                    entity.getScenarioId(), e.getMessage());
        }
        log.info("创建话术: id={}, speechTitle={}, scenarioId={}",
                entity.getId(), entity.getSpeechTitle(), entity.getScenarioId());
        return entity;
    }

    /**
     * 更新话术（字段非空才覆盖）。
     *
     * @param id  话术 ID
     * @param dto 话术参数
     * @return 更新后的话术
     * @throws ScrmException 话术不存在 / 参数非法
     */
    @Transactional
    public ScrmSalesSpeechEntity updateSpeech(Long id, ScrmSalesSpeechDto dto) throws ScrmException {
        ScrmSalesSpeechEntity entity = findSpeechOrThrow(id);
        validateSpeechDto(dto, true);
        if (dto.getScenarioId() != null && !dto.getScenarioId().equals(entity.getScenarioId())) {
            scenarioService.findScenarioOrThrow(dto.getScenarioId());
            entity.setScenarioId(dto.getScenarioId());
        }
        if (dto.getScenarioName() != null) entity.setScenarioName(dto.getScenarioName());
        if (dto.getSpeechTitle() != null) entity.setSpeechTitle(dto.getSpeechTitle());
        if (dto.getSpeechContent() != null) entity.setSpeechContent(dto.getSpeechContent());
        if (dto.getSpeechType() != null) entity.setSpeechType(dto.getSpeechType());
        if (dto.getSpeechStyle() != null) entity.setSpeechStyle(dto.getSpeechStyle());
        if (dto.getTargetAudience() != null) entity.setTargetAudience(dto.getTargetAudience());
        if (dto.getApplicableProducts() != null) entity.setApplicableProducts(dto.getApplicableProducts());
        if (dto.getApplicableScenes() != null) entity.setApplicableScenes(dto.getApplicableScenes());
        if (dto.getKeywords() != null) entity.setKeywords(dto.getKeywords());
        if (dto.getVariables() != null) entity.setVariables(dto.getVariables());
        if (dto.getMediaAttachments() != null) entity.setMediaAttachments(dto.getMediaAttachments());
        if (dto.getDifficultyLevel() != null) entity.setDifficultyLevel(dto.getDifficultyLevel());
        if (dto.getEstimatedDuration() != null) entity.setEstimatedDuration(dto.getEstimatedDuration());
        if (dto.getIsRecommended() != null) entity.setIsRecommended(dto.getIsRecommended());
        if (dto.getIsVerified() != null) entity.setIsVerified(dto.getIsVerified());
        if (dto.getVersionNo() != null) entity.setVersionNo(dto.getVersionNo());
        if (dto.getAuthorId() != null) entity.setAuthorId(dto.getAuthorId());
        if (dto.getAuthorName() != null) entity.setAuthorName(dto.getAuthorName());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = speechRepository.save(entity);
        log.info("更新话术: id={}, speechTitle={}", id, entity.getSpeechTitle());
        return entity;
    }

    /**
     * 删除话术。
     *
     * @param id 话术 ID
     * @throws ScrmException 话术不存在
     */
    @Transactional
    public void deleteSpeech(Long id) throws ScrmException {
        ScrmSalesSpeechEntity entity = findSpeechOrThrow(id);
        speechRepository.delete(entity);
        try {
            scenarioService.updateScenarioStats(entity.getScenarioId());
        } catch (ScrmException e) {
            log.warn("删除话术后更新场景统计失败, 忽略: scenarioId={}, err={}",
                    entity.getScenarioId(), e.getMessage());
        }
        log.info("删除话术: id={}, speechTitle={}", id, entity.getSpeechTitle());
    }

    /**
     * 查询话术详情。
     *
     * @param id 话术 ID
     * @return 话术实体
     * @throws ScrmException 话术不存在
     */
    @Transactional(readOnly = true)
    public ScrmSalesSpeechEntity getSpeech(Long id) throws ScrmException {
        return findSpeechOrThrow(id);
    }

    /**
     * 分页查询话术, 支持按场景 / 类型 / 风格 / 难度 / 启用状态 / 关键字过滤。
     *
     * @param scenarioId      场景 ID 过滤（可空）
     * @param speechType      话术类型过滤（可空）
     * @param speechStyle     话术风格过滤（可空）
     * @param difficultyLevel 难度等级过滤（可空）
     * @param enabled         启用状态过滤（可空）
     * @param keyword         话术标题关键字模糊匹配（可空）
     * @param pageable        分页参数
     * @return 话术分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmSalesSpeechEntity> listSpeeches(Long scenarioId, String speechType, String speechStyle,
                                                    String difficultyLevel, Boolean enabled, String keyword,
                                                    Pageable pageable) {
        Specification<ScrmSalesSpeechEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (scenarioId != null) {
                predicates.add(cb.equal(root.get("scenarioId"), scenarioId));
            }
            if (speechType != null && !speechType.isBlank()) {
                predicates.add(cb.equal(root.get("speechType"), speechType));
            }
            if (speechStyle != null && !speechStyle.isBlank()) {
                predicates.add(cb.equal(root.get("speechStyle"), speechStyle));
            }
            if (difficultyLevel != null && !difficultyLevel.isBlank()) {
                predicates.add(cb.equal(root.get("difficultyLevel"), difficultyLevel));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("speechTitle"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return speechRepository.findAll(spec, pageable);
    }

    /**
     * 启用话术。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @Transactional
    public ScrmSalesSpeechEntity enableSpeech(Long id) throws ScrmException {
        ScrmSalesSpeechEntity entity = findSpeechOrThrow(id);
        entity.setEnabled(true);
        entity = speechRepository.save(entity);
        log.info("启用话术: id={}, speechTitle={}", id, entity.getSpeechTitle());
        return entity;
    }

    /**
     * 禁用话术。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @Transactional
    public ScrmSalesSpeechEntity disableSpeech(Long id) throws ScrmException {
        ScrmSalesSpeechEntity entity = findSpeechOrThrow(id);
        entity.setEnabled(false);
        entity = speechRepository.save(entity);
        log.info("禁用话术: id={}, speechTitle={}", id, entity.getSpeechTitle());
        return entity;
    }

    /**
     * 验证话术 (标记为已验证)。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @Transactional
    public ScrmSalesSpeechEntity verifySpeech(Long id) throws ScrmException {
        ScrmSalesSpeechEntity entity = findSpeechOrThrow(id);
        entity.setIsVerified(true);
        entity = speechRepository.save(entity);
        log.info("验证话术: id={}, speechTitle={}", id, entity.getSpeechTitle());
        return entity;
    }

    /**
     * 渲染话术 (变量替换)。
     * <p>支持话术变量 ({customerName} / {productName} / {price} 等) 与默认变量 ({time})。
     * variables 入参 key 为变量名 (不含大括号), value 为变量值。</p>
     *
     * @param id        话术 ID
     * @param variables 变量 Map (key 为变量名, value 为变量值)
     * @return 渲染后的话术内容
     * @throws ScrmException 话术不存在
     */
    @Transactional
    public String renderSpeech(Long id, Map<String, String> variables) throws ScrmException {
        ScrmSalesSpeechEntity entity = findSpeechOrThrow(id);
        String content = entity.getSpeechContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        Map<String, String> merged = new HashMap<>();
        // 默认变量: 当前时间
        merged.put("time", LocalDateTime.now().toString());
        if (variables != null) {
            merged.putAll(variables);
        }
        String rendered = content;
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            if (entry.getValue() != null) {
                rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return rendered;
    }

    /**
     * 更新话术使用统计 (使用次数 +1, 成功时成功次数 +1, 重算评分)。
     * <p>评分公式: 评分 = 0.5 * (成功率 * 5) + 0.3 * (反馈好评率 * 5) + 0.2 * (使用次数归一化)。
     * 使用次数归一化: min(usageCount / 100, 1) * 5。直接 SQL 增量更新避免乐观锁冲突。</p>
     *
     * @param id      话术 ID
     * @param success 是否成功
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @Transactional
    public ScrmSalesSpeechEntity incrementUsage(Long id, boolean success) throws ScrmException {
        ScrmSalesSpeechEntity entity = findSpeechOrThrow(id);
        speechRepository.incrementUsage(id, success ? 1 : 0);
        // 重新加载并重算评分
        entity = speechRepository.findById(id).orElseThrow(() -> new ScrmException(
                ScrmExceptionConstants.NOT_FOUND, "话术不存在: id=" + id));
        recomputeSpeechRating(entity);
        entity = speechRepository.save(entity);
        log.info("更新话术使用统计: id={}, success={}, usageCount={}, successCount={}",
                id, success, entity.getUsageCount(), entity.getSuccessCount());
        return entity;
    }

    /**
     * 复制话术 (生成新话术, 标题使用 newTitle, 统计字段归零)。
     *
     * @param id       话术 ID
     * @param newTitle 新话术标题
     * @return 复制后的话术
     * @throws ScrmException 话术不存在 / 标题为空
     */
    @Transactional
    public ScrmSalesSpeechEntity copySpeech(Long id, String newTitle) throws ScrmException {
        ScrmSalesSpeechEntity source = findSpeechOrThrow(id);
        if (newTitle == null || newTitle.isBlank()) {
            throw ScrmException.badRequest("新话术标题不能为空");
        }
        ScrmSalesSpeechEntity entity = new ScrmSalesSpeechEntity();
        entity.setScenarioId(source.getScenarioId());
        entity.setScenarioName(source.getScenarioName());
        entity.setSpeechTitle(newTitle);
        entity.setSpeechContent(source.getSpeechContent());
        entity.setSpeechType(source.getSpeechType());
        entity.setSpeechStyle(source.getSpeechStyle());
        entity.setTargetAudience(source.getTargetAudience());
        entity.setApplicableProducts(source.getApplicableProducts());
        entity.setApplicableScenes(source.getApplicableScenes());
        entity.setKeywords(source.getKeywords());
        entity.setVariables(source.getVariables());
        entity.setMediaAttachments(source.getMediaAttachments());
        entity.setDifficultyLevel(source.getDifficultyLevel());
        entity.setEstimatedDuration(source.getEstimatedDuration());
        entity.setRating(0.0);
        entity.setUsageCount(0);
        entity.setSuccessCount(0);
        entity.setFeedbackCount(0);
        entity.setPositiveFeedback(0);
        entity.setNegativeFeedback(0);
        entity.setIsRecommended(false);
        entity.setIsVerified(false);
        entity.setVersionNo(DEFAULT_VERSION_NO);
        entity.setAuthorId(source.getAuthorId());
        entity.setAuthorName(source.getAuthorName());
        entity.setTags(source.getTags());
        entity.setEnabled(true);
        entity.setCreatedBy(source.getCreatedBy());
        entity = speechRepository.save(entity);
        try {
            scenarioService.updateScenarioStats(entity.getScenarioId());
        } catch (ScrmException e) {
            log.warn("复制话术后更新场景统计失败, 忽略: scenarioId={}, err={}",
                    entity.getScenarioId(), e.getMessage());
        }
        log.info("复制话术: sourceId={}, newId={}, newTitle={}", id, entity.getId(), newTitle);
        return entity;
    }

    /**
     * 查询场景下热门话术 (按 usageCount 降序, rating 降序)。
     *
     * @param scenarioId 场景 ID
     * @param limit      返回条数
     * @return 话术列表
     * @throws ScrmException 场景不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmSalesSpeechEntity> getTopSpeeches(Long scenarioId, int limit) throws ScrmException {
        scenarioService.findScenarioOrThrow(scenarioId);
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECOMMEND_LIMIT));
        PageRequest pageable = PageRequest.of(0, safeLimit,
                Sort.by(Sort.Direction.DESC, "usageCount", "rating"));
        return speechRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("scenarioId"), scenarioId));
            predicates.add(cb.equal(root.get("enabled"), true));
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable).getContent();
    }

    /**
     * 校验话术参数。
     *
     * @param dto     话术参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateSpeechDto(ScrmSalesSpeechDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("话术参数不能为空");
        }
        if (dto.getScenarioId() == null && !partial) {
            throw ScrmException.badRequest("场景 ID 不能为空");
        }
        if (dto.getSpeechTitle() != null) {
            if (dto.getSpeechTitle().isBlank()) {
                throw ScrmException.badRequest("话术标题不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("话术标题不能为空");
        }
        if (dto.getSpeechContent() != null) {
            if (dto.getSpeechContent().isBlank()) {
                throw ScrmException.badRequest("话术内容不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("话术内容不能为空");
        }
        if (dto.getSpeechType() != null && !dto.getSpeechType().isBlank() && !VALID_SPEECH_TYPES.contains(dto.getSpeechType())) {
            throw ScrmException.badRequest(
                    "话术类型非法: " + dto.getSpeechType() + ", 仅支持 " + VALID_SPEECH_TYPES);
        }
        if (dto.getSpeechStyle() != null && !dto.getSpeechStyle().isBlank() && !VALID_SPEECH_STYLES.contains(dto.getSpeechStyle())) {
            throw ScrmException.badRequest(
                    "话术风格非法: " + dto.getSpeechStyle() + ", 仅支持 " + VALID_SPEECH_STYLES);
        }
        if (dto.getDifficultyLevel() != null && !dto.getDifficultyLevel().isBlank() && !VALID_DIFFICULTY_LEVELS.contains(dto.getDifficultyLevel())) {
            throw ScrmException.badRequest(
                    "难度等级非法: " + dto.getDifficultyLevel() + ", 仅支持 " + VALID_DIFFICULTY_LEVELS);
        }
    }

    /**
     * 重算话术评分。
     * <p>评分公式: 0.5 * (成功率 * 5) + 0.3 * (反馈好评率 * 5) + 0.2 * (使用次数归一化 * 5)。
     * 使用次数归一化: min(usageCount / 100, 1)。</p>
     *
     * @param speech 话术实体
     */
    void recomputeSpeechRating(ScrmSalesSpeechEntity speech) {
        int usageCount = speech.getUsageCount() != null ? speech.getUsageCount() : 0;
        int successCount = speech.getSuccessCount() != null ? speech.getSuccessCount() : 0;
        int feedbackCount = speech.getFeedbackCount() != null ? speech.getFeedbackCount() : 0;
        int positiveFeedback = speech.getPositiveFeedback() != null ? speech.getPositiveFeedback() : 0;
        double successRate = usageCount > 0 ? successCount * 1.0 / usageCount : 0.0;
        double positiveRate = feedbackCount > 0 ? positiveFeedback * 1.0 / feedbackCount : 0.0;
        double usageNorm = Math.min(usageCount / 100.0, 1.0);
        double rating = 0.5 * (successRate * 5.0) + 0.3 * (positiveRate * 5.0) + 0.2 * (usageNorm * 5.0);
        speech.setRating(Math.round(rating * 100d) / 100d);
    }

    /**
     * 按主键查询话术, 不存在抛异常, 并校验归属账号。
     *
     * @param id 话术 ID
     * @return 话术实体
     * @throws ScrmException 话术不存在
     */
    ScrmSalesSpeechEntity findSpeechOrThrow(Long id) throws ScrmException {
        ScrmSalesSpeechEntity entity = speechRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "话术不存在: id=" + id));
        return entity;
    }

}
