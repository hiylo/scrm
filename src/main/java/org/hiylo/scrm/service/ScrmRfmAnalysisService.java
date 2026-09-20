/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmAnalysisService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmRfmAnalysisDto;
import org.hiylo.scrm.dto.ScrmRfmCalculateDto;
import org.hiylo.scrm.dto.ScrmRfmConfigDto;
import org.hiylo.scrm.dto.ScrmRfmSegmentStrategyDto;
import org.hiylo.scrm.entity.ScrmConversationEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmRfmAnalysisEntity;
import org.hiylo.scrm.entity.ScrmRfmConfigEntity;
import org.hiylo.scrm.entity.ScrmRfmSegmentStrategyEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmRfmAnalysisRepository;
import org.hiylo.scrm.repository.ScrmRfmConfigRepository;
import org.hiylo.scrm.repository.ScrmRfmSegmentStrategyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM RFM 客户价值分析服务
 * <p>
 * 基于 RFM 模型 (Recency 最近消费 / Frequency 消费频率 / Monetary 消费金额) 对客户进行
 * 价值评分与自动分群 (8 种 RFM 组合), 提供配置管理、批量计算、分群分布统计与分群策略推荐。
 * 所有写操作均写入当前用户归属账号, 实现数据隔离。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmRfmAnalysisService {

    // ==================== 常量 ====================

    /** R 数据源: 最近互动 */
    private static final String RECENCY_SOURCE_LAST_INTERACTION = "LAST_INTERACTION";
    /** R 数据源: 最近消费 */
    private static final String RECENCY_SOURCE_LAST_ORDER = "LAST_ORDER";
    /** M 数据源: 累计消费 */
    private static final String MONETARY_SOURCE_TOTAL_SPENT = "TOTAL_SPENT";
    /** M 数据源: 手动录入 */
    private static final String MONETARY_SOURCE_MANUAL = "MANUAL";

    /** 分群大类: 冠军客户 */
    private static final String CATEGORY_CHAMPION = "CHAMPION";
    /** 分群大类: 忠诚客户 */
    private static final String CATEGORY_LOYAL = "LOYAL";
    /** 分群大类: 潜力客户 */
    private static final String CATEGORY_POTENTIAL = "POTENTIAL";
    /** 分群大类: 新客户 */
    private static final String CATEGORY_NEW = "NEW";
    /** 分群大类: 流失风险客户 */
    private static final String CATEGORY_AT_RISK = "AT_RISK";
    /** 分群大类: 流失客户 */
    private static final String CATEGORY_LOST = "LOST";
    /** 分群大类: 休眠客户 */
    private static final String CATEGORY_HIBERNATING = "HIBERNATING";
    /** 分群大类: 普通客户 */
    private static final String CATEGORY_NORMAL = "NORMAL";

    /** 默认操作人 (请求头未透传 X-User-Id 时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 评分高/低分界线 (1-5 中 3 为分界) */
    private static final int SCORE_THRESHOLD = 3;

    /** RFM 分群编码 -> 分群名称 映射 (8 种组合) */
    private static final Map<String, String> SEGMENT_NAME_MAP = new HashMap<>();
    /** RFM 分群编码 -> 分群大类 映射 (8 种组合) */
    private static final Map<String, String> SEGMENT_CATEGORY_MAP = new HashMap<>();

    static {
        SEGMENT_NAME_MAP.put("111", "重要价值客户");
        SEGMENT_NAME_MAP.put("110", "重要发展客户");
        SEGMENT_NAME_MAP.put("101", "重要保持客户");
        SEGMENT_NAME_MAP.put("100", "重要挽留客户");
        SEGMENT_NAME_MAP.put("011", "一般价值客户");
        SEGMENT_NAME_MAP.put("010", "一般发展客户");
        SEGMENT_NAME_MAP.put("001", "一般保持客户");
        SEGMENT_NAME_MAP.put("000", "一般挽留客户");

        SEGMENT_CATEGORY_MAP.put("111", CATEGORY_CHAMPION);
        SEGMENT_CATEGORY_MAP.put("110", CATEGORY_LOYAL);
        SEGMENT_CATEGORY_MAP.put("101", CATEGORY_POTENTIAL);
        SEGMENT_CATEGORY_MAP.put("100", CATEGORY_AT_RISK);
        SEGMENT_CATEGORY_MAP.put("011", CATEGORY_NEW);
        SEGMENT_CATEGORY_MAP.put("010", CATEGORY_HIBERNATING);
        SEGMENT_CATEGORY_MAP.put("001", CATEGORY_LOST);
        SEGMENT_CATEGORY_MAP.put("000", CATEGORY_NORMAL);
    }

    /** RFM 配置数据仓库 */
    private final ScrmRfmConfigRepository configRepository;
    /** RFM 分析数据仓库 */
    private final ScrmRfmAnalysisRepository analysisRepository;
    /** RFM 分群策略数据仓库 */
    private final ScrmRfmSegmentStrategyRepository strategyRepository;
    /** 客户数据仓库 */
    private final ScrmCustomerRepository customerRepository;
    /** 会话数据仓库 */
    private final ScrmConversationRepository conversationRepository;

    // ============================================================
    // 配置管理
    // ============================================================

    /**
     * 创建 RFM 配置
     *
     * @param dto 配置参数
     * @return 创建后的配置
     * @throws ScrmException 参数非法 / 默认配置冲突
     */
    @Transactional
    public ScrmRfmConfigDto createConfig(ScrmRfmConfigDto dto) throws ScrmException {
        if (dto.getConfigName() == null || dto.getConfigName().isBlank()) {
            throw ScrmException.badRequest("配置名称不能为空");
        }
        validateWeights(dto.getRWeight(), dto.getFWeight(), dto.getMWeight());
        Boolean isDefault = Boolean.TRUE.equals(dto.getIsDefault());
        // 默认配置唯一性: 同账号下仅允许一个默认配置
        if (isDefault && configRepository.countByIsDefaultTrue() > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "同账号下已存在默认 RFM 配置, 请先取消原默认配置");
        }
        ScrmRfmConfigEntity entity = new ScrmRfmConfigEntity();
        entity.setConfigName(dto.getConfigName());
        entity.setDescription(dto.getDescription());
        entity.setRWeight(dto.getRWeight() != null ? dto.getRWeight() : 0.3);
        entity.setFWeight(dto.getFWeight() != null ? dto.getFWeight() : 0.3);
        entity.setMWeight(dto.getMWeight() != null ? dto.getMWeight() : 0.4);
        entity.setRThreshold(dto.getRThreshold());
        entity.setFThreshold(dto.getFThreshold());
        entity.setMThreshold(dto.getMThreshold());
        entity.setRecencySource(dto.getRecencySource() != null
                ? dto.getRecencySource() : RECENCY_SOURCE_LAST_INTERACTION);
        entity.setMonetarySource(dto.getMonetarySource() != null
                ? dto.getMonetarySource() : MONETARY_SOURCE_TOTAL_SPENT);
        entity.setIsDefault(isDefault);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : DEFAULT_OPERATOR);
        entity = configRepository.save(entity);
        log.info("创建 RFM 配置: id={}, name={}", entity.getId(), entity.getConfigName());
        return toConfigDto(entity);
    }

    /**
     * 更新 RFM 配置
     *
     * @param id  配置 ID
     * @param dto 配置参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 参数非法
     */
    @Transactional
    public ScrmRfmConfigDto updateConfig(Long id, ScrmRfmConfigDto dto) throws ScrmException {
        ScrmRfmConfigEntity entity = findConfigOrThrow(id);
        if (dto.getConfigName() != null) {
            if (dto.getConfigName().isBlank()) {
                throw ScrmException.badRequest("配置名称不能为空");
            }
            entity.setConfigName(dto.getConfigName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getRWeight() != null || dto.getFWeight() != null || dto.getMWeight() != null) {
            Double r = dto.getRWeight() != null ? dto.getRWeight() : entity.getRWeight();
            Double f = dto.getFWeight() != null ? dto.getFWeight() : entity.getFWeight();
            Double m = dto.getMWeight() != null ? dto.getMWeight() : entity.getMWeight();
            validateWeights(r, f, m);
            entity.setRWeight(r);
            entity.setFWeight(f);
            entity.setMWeight(m);
        }
        if (dto.getRThreshold() != null) {
            entity.setRThreshold(dto.getRThreshold());
        }
        if (dto.getFThreshold() != null) {
            entity.setFThreshold(dto.getFThreshold());
        }
        if (dto.getMThreshold() != null) {
            entity.setMThreshold(dto.getMThreshold());
        }
        if (dto.getRecencySource() != null) {
            entity.setRecencySource(dto.getRecencySource());
        }
        if (dto.getMonetarySource() != null) {
            entity.setMonetarySource(dto.getMonetarySource());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        // isDefault 通过 setDefaultConfig 专用接口维护, 此处不直接修改
        entity = configRepository.save(entity);
        return toConfigDto(entity);
    }

    /**
     * 删除 RFM 配置
     * <p>
     * 同时清理该配置下的分析结果。
     * </p>
     *
     * @param id 配置 ID
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public void deleteConfig(Long id) throws ScrmException {
        ScrmRfmConfigEntity entity = findConfigOrThrow(id);
        analysisRepository.deleteByConfigId(id);
        configRepository.delete(entity);
        log.info("删除 RFM 配置: id={}, name={}", id, entity.getConfigName());
    }

    /**
     * 查询 RFM 配置详情
     *
     * @param id 配置 ID
     * @return 配置 DTO
     * @throws ScrmException 配置不存在
     */
    @Transactional(readOnly = true)
    public ScrmRfmConfigDto getConfig(Long id) throws ScrmException {
        return toConfigDto(findConfigOrThrow(id));
    }

    /**
     * 分页查询 RFM 配置, 支持按启用状态过滤
     *
     * @param enabled  启用状态过滤 (可空)
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmRfmConfigDto> listConfigs(Boolean enabled, Pageable pageable) {
        Specification<ScrmRfmConfigEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return configRepository.findAll(spec, sorted).map(this::toConfigDto);
    }

    /**
     * 设置默认配置
     * <p>
     * 取消同账号下原默认配置标记, 将当前配置置为默认。
     * </p>
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmRfmConfigDto setDefaultConfig(Long id) throws ScrmException {
        ScrmRfmConfigEntity entity = findConfigOrThrow(id);
        configRepository.findByIsDefaultTrue().ifPresent(old -> {
            if (!Objects.equals(old.getId(), id)) {
                old.setIsDefault(false);
                configRepository.save(old);
            }
        });
        entity.setIsDefault(true);
        entity = configRepository.save(entity);
        log.info("设置默认 RFM 配置: id={}, name={}", id, entity.getConfigName());
        return toConfigDto(entity);
    }

    /**
     * 启用配置
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmRfmConfigDto enableConfig(Long id) throws ScrmException {
        ScrmRfmConfigEntity entity = findConfigOrThrow(id);
        entity.setEnabled(true);
        entity = configRepository.save(entity);
        log.info("启用 RFM 配置: id={}", id);
        return toConfigDto(entity);
    }

    /**
     * 禁用配置
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public ScrmRfmConfigDto disableConfig(Long id) throws ScrmException {
        ScrmRfmConfigEntity entity = findConfigOrThrow(id);
        entity.setEnabled(false);
        entity = configRepository.save(entity);
        log.info("禁用 RFM 配置: id={}", id);
        return toConfigDto(entity);
    }

    // ============================================================
    // RFM 计算
    // ============================================================

    /**
     * 计算单个客户 RFM
     * <p>
     * 从客户数据取 R/F/M 值 → 评分 → 分群 → 保存。
     * R: 最近互动/消费距今天数 (基于 lastInteractionAt, LAST_ORDER 源暂回退至互动时间)
     * F: 会话数 (客户参与的会话数量, 作为互动频次代理)
     * M: 消费总金额 (当前无订单数据, 默认 0; MANUAL 源同样默认 0)
     * </p>
     *
     * @param configId  配置 ID
     * @param customerId 客户 ID
     * @return 分析结果
     * @throws ScrmException 配置不存在 / 客户不存在
     */
    @Transactional
    public ScrmRfmAnalysisDto calculate(Long configId, Long customerId) throws ScrmException {
        ScrmRfmConfigEntity config = findConfigOrThrow(configId);
        ScrmCustomerEntity customer = findCustomerOrThrow(customerId);

        LocalDateTime now = LocalDateTime.now();

        // 计算 R: 最近互动/消费距今天数
        LocalDateTime referenceTime = resolveRecencyTime(config, customer);
        int recencyDays = referenceTime != null
                ? (int) ChronoUnit.DAYS.between(referenceTime, now) : Integer.MAX_VALUE;

        // 计算 F: 会话数 (互动频次代理)
        int frequency = countCustomerConversations(customerId);

        // 计算 M: 消费总金额 (当前无订单数据, 默认 0)
        double monetary = resolveMonetary(config);

        // 评分
        int rScore = calculateRScore(recencyDays, config.getRThreshold());
        int fScore = calculateFScore(frequency, config.getFThreshold());
        int mScore = calculateMScore(monetary, config.getMThreshold());

        // 分群
        String segment = determineSegment(rScore, fScore, mScore);
        String segmentName = SEGMENT_NAME_MAP.getOrDefault(segment, "未知分群");
        String segmentCategory = SEGMENT_CATEGORY_MAP.getOrDefault(segment, CATEGORY_NORMAL);

        // 综合价值分
        double valueScore = calculateValueScore(rScore, fScore, mScore,
                config.getRWeight(), config.getFWeight(), config.getMWeight());

        // 删除旧记录后写入新记录 (保留历史可改为追加, 此处覆盖最新)
        analysisRepository.deleteByCustomerId(customerId);

        ScrmRfmAnalysisEntity entity = new ScrmRfmAnalysisEntity();
        entity.setCustomerId(customerId);
        entity.setCustomerName(customer.getNickname());
        entity.setConfigId(configId);
        entity.setRecencyDays(recencyDays);
        entity.setFrequency(frequency);
        entity.setMonetary(monetary);
        entity.setRScore(rScore);
        entity.setFScore(fScore);
        entity.setMScore(mScore);
        entity.setRfmSegment(segment);
        entity.setSegmentName(segmentName);
        entity.setSegmentCategory(segmentCategory);
        entity.setValueScore(valueScore);
        entity.setCalculatedAt(now);
        entity = analysisRepository.save(entity);

        // 更新配置的最近计算时间
        config.setLastCalculatedAt(now);
        configRepository.save(config);

        log.info("计算客户 RFM: customerId={}, segment={}, valueScore={}",
                customerId, segment, valueScore);
        return toAnalysisDto(entity);
    }

    /**
     * 批量计算客户 RFM
     *
     * @param dto 批量计算请求 (配置 ID + 客户 ID 列表)
     * @return 计算结果列表
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public List<ScrmRfmAnalysisDto> calculateBatch(ScrmRfmCalculateDto dto) throws ScrmException {
        if (dto.getConfigId() == null) {
            throw ScrmException.badRequest("配置 ID 不能为空");
        }
        if (dto.getCustomerIds() == null || dto.getCustomerIds().isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        List<ScrmRfmAnalysisDto> results = new ArrayList<>();
        for (Long customerId : dto.getCustomerIds()) {
            try {
                results.add(calculate(dto.getConfigId(), customerId));
            } catch (ScrmException e) {
                log.warn("批量计算 RFM 跳过客户: customerId={}, err={}", customerId, e.getMessage());
            }
        }
        log.info("批量计算 RFM 完成: configId={}, success={}/{}",
                dto.getConfigId(), results.size(), dto.getCustomerIds().size());
        return results;
    }

    /**
     * 计算当前账号下所有客户 RFM
     *
     * @param configId 配置 ID
     * @return 计算结果列表
     * @throws ScrmException 配置不存在
     */
    @Transactional
    public List<ScrmRfmAnalysisDto> calculateAll(Long configId) throws ScrmException {
        findConfigOrThrow(configId);
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        List<ScrmRfmAnalysisDto> results = new ArrayList<>();
        for (ScrmCustomerEntity customer : customers) {
            try {
                results.add(calculate(configId, customer.getId()));
            } catch (ScrmException e) {
                log.warn("全量计算 RFM 跳过客户: customerId={}, err={}", customer.getId(), e.getMessage());
            }
        }
        log.info("全量计算 RFM 完成: configId={}, success={}/{}",
                configId, results.size(), customers.size());
        return results;
    }

    // ============================================================
    // 分析结果查询
    // ============================================================

    /**
     * 查询客户 RFM 分析结果 (取最新一条)
     *
     * @param customerId 客户 ID
     * @return 分析结果 DTO
     * @throws ScrmException 分析结果不存在
     */
    @Transactional(readOnly = true)
    public ScrmRfmAnalysisDto getAnalysis(Long customerId) throws ScrmException {
        ScrmRfmAnalysisEntity entity = analysisRepository
                .findFirstByCustomerIdOrderByCalculatedAtDesc(customerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "RFM 分析结果不存在: customerId=" + customerId));
        return toAnalysisDto(entity);
    }

    /**
     * 分页查询 RFM 分析结果, 支持按分群大类、分群编码、价值分范围过滤
     *
     * @param segmentCategory 分群大类过滤 (可空)
     * @param segmentCode     分群编码过滤 (可空)
     * @param minValueScore   最小价值分过滤 (可空)
     * @param maxValueScore   最大价值分过滤 (可空)
     * @param pageable        分页参数
     * @return 分析结果分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmRfmAnalysisDto> listAnalysis(String segmentCategory, String segmentCode,
                                                  Double minValueScore, Double maxValueScore,
                                                  Pageable pageable) {
        Specification<ScrmRfmAnalysisEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (segmentCategory != null && !segmentCategory.isBlank()) {
                predicates.add(cb.equal(root.get("segmentCategory"), segmentCategory));
            }
            if (segmentCode != null && !segmentCode.isBlank()) {
                predicates.add(cb.equal(root.get("rfmSegment"), segmentCode));
            }
            if (minValueScore != null) {
                predicates.add(cb.ge(root.get("valueScore"), minValueScore));
            }
            if (maxValueScore != null) {
                predicates.add(cb.le(root.get("valueScore"), maxValueScore));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "valueScore"));
        return analysisRepository.findAll(spec, sorted).map(this::toAnalysisDto);
    }

    /**
     * 分群分布统计: 各分群大类的客户数、占比与平均价值分
     *
     * @return 分群分布列表, 每项含 segmentCategory / count / percentage / avgValueScore
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSegmentDistribution() {
        List<Object[]> rows = analysisRepository.segmentDistribution();
        long total = rows.stream().mapToLong(r -> r[1] != null ? ((Number) r[1]).longValue() : 0L).sum();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            String category = (String) row[0];
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            double avgScore = row[2] != null ? ((Number) row[2]).doubleValue() : 0d;
            double percentage = total > 0 ? Math.round(count * 10000d / total) / 100d : 0d;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("segmentCategory", category);
            item.put("count", count);
            item.put("percentage", percentage);
            item.put("avgValueScore", Math.round(avgScore * 100d) / 100d);
            result.add(item);
        }
        return result;
    }

    // ============================================================
    // 分群策略管理
    // ============================================================

    /**
     * 创建分群策略
     *
     * @param dto 策略参数
     * @return 创建后的策略
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmRfmSegmentStrategyDto createStrategy(ScrmRfmSegmentStrategyDto dto) throws ScrmException {
        if (dto.getStrategyName() == null || dto.getStrategyName().isBlank()) {
            throw ScrmException.badRequest("策略名称不能为空");
        }
        if (dto.getSegmentCategory() == null || dto.getSegmentCategory().isBlank()) {
            throw ScrmException.badRequest("目标分群大类不能为空");
        }
        if (dto.getStrategyType() == null || dto.getStrategyType().isBlank()) {
            throw ScrmException.badRequest("策略类型不能为空");
        }
        if (dto.getActions() == null || dto.getActions().isBlank()) {
            throw ScrmException.badRequest("推荐动作不能为空");
        }
        ScrmRfmSegmentStrategyEntity entity = new ScrmRfmSegmentStrategyEntity();
        entity.setStrategyName(dto.getStrategyName());
        entity.setSegmentCategory(dto.getSegmentCategory());
        entity.setSegmentCode(dto.getSegmentCode());
        entity.setStrategyType(dto.getStrategyType());
        entity.setDescription(dto.getDescription());
        entity.setActions(dto.getActions());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : 0);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : DEFAULT_OPERATOR);
        entity = strategyRepository.save(entity);
        log.info("创建 RFM 分群策略: id={}, name={}", entity.getId(), entity.getStrategyName());
        return toStrategyDto(entity);
    }

    /**
     * 更新分群策略
     *
     * @param id  策略 ID
     * @param dto 策略参数
     * @return 更新后的策略
     * @throws ScrmException 策略不存在
     */
    @Transactional
    public ScrmRfmSegmentStrategyDto updateStrategy(Long id, ScrmRfmSegmentStrategyDto dto) throws ScrmException {
        ScrmRfmSegmentStrategyEntity entity = findStrategyOrThrow(id);
        if (dto.getStrategyName() != null) {
            if (dto.getStrategyName().isBlank()) {
                throw ScrmException.badRequest("策略名称不能为空");
            }
            entity.setStrategyName(dto.getStrategyName());
        }
        if (dto.getSegmentCategory() != null) {
            entity.setSegmentCategory(dto.getSegmentCategory());
        }
        if (dto.getSegmentCode() != null) {
            entity.setSegmentCode(dto.getSegmentCode());
        }
        if (dto.getStrategyType() != null) {
            entity.setStrategyType(dto.getStrategyType());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getActions() != null) {
            if (dto.getActions().isBlank()) {
                throw ScrmException.badRequest("推荐动作不能为空");
            }
            entity.setActions(dto.getActions());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        if (dto.getPriority() != null) {
            entity.setPriority(dto.getPriority());
        }
        entity = strategyRepository.save(entity);
        return toStrategyDto(entity);
    }

    /**
     * 删除分群策略
     *
     * @param id 策略 ID
     * @throws ScrmException 策略不存在
     */
    @Transactional
    public void deleteStrategy(Long id) throws ScrmException {
        ScrmRfmSegmentStrategyEntity entity = findStrategyOrThrow(id);
        strategyRepository.delete(entity);
        log.info("删除 RFM 分群策略: id={}, name={}", id, entity.getStrategyName());
    }

    /**
     * 查询分群策略详情
     *
     * @param id 策略 ID
     * @return 策略 DTO
     * @throws ScrmException 策略不存在
     */
    @Transactional(readOnly = true)
    public ScrmRfmSegmentStrategyDto getStrategy(Long id) throws ScrmException {
        return toStrategyDto(findStrategyOrThrow(id));
    }

    /**
     * 分页查询分群策略, 支持按分群大类与启用状态过滤
     *
     * @param segmentCategory 分群大类过滤 (可空)
     * @param enabled         启用状态过滤 (可空)
     * @param pageable        分页参数
     * @return 策略分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmRfmSegmentStrategyDto> listStrategies(String segmentCategory, Boolean enabled,
                                                           Pageable pageable) {
        Specification<ScrmRfmSegmentStrategyEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (segmentCategory != null && !segmentCategory.isBlank()) {
                predicates.add(cb.equal(root.get("segmentCategory"), segmentCategory));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "priority").and(Sort.by(Sort.Direction.DESC, "createTime")));
        return strategyRepository.findAll(spec, sorted).map(this::toStrategyDto);
    }

    /**
     * 获取分群推荐策略
     * <p>
     * 匹配规则: segmentCategory 必须匹配, segmentCode 为空的策略 (适用该大类所有) 或
     * segmentCode 精确匹配的策略, 按优先级降序返回。
     * </p>
     *
     * @param segmentCategory 分群大类
     * @param segmentCode     分群编码 (可空, 为空时仅返回大类通用的策略)
     * @return 匹配的策略列表
     */
    @Transactional(readOnly = true)
    public List<ScrmRfmSegmentStrategyDto> getStrategiesForSegment(String segmentCategory, String segmentCode) {
        List<ScrmRfmSegmentStrategyEntity> all = strategyRepository
                .findBySegmentCategoryAndEnabledTrueOrderByPriorityDesc(segmentCategory);
        return all.stream()
                .filter(s -> s.getSegmentCode() == null || s.getSegmentCode().isBlank()
                        || (segmentCode != null && segmentCode.equals(s.getSegmentCode())))
                .map(this::toStrategyDto)
                .collect(Collectors.toList());
    }

    // ============================================================
    // 评分算法
    // ============================================================

    /**
     * R 评分 (1-5)
     * <p>
     * recencyDays 越小 (越近) 评分越高。以 threshold 为高/低分界:
     * ≤ threshold/4 → 5, ≤ threshold/2 → 4, ≤ threshold → 3,
     * ≤ threshold*2 → 2, 否则 → 1。
     * </p>
     *
     * @param recencyDays 最近互动/消费距今天数
     * @param threshold   R 高/低阈值天数
     * @return R 评分 (1-5)
     */
    public int calculateRScore(int recencyDays, int threshold) {
        if (threshold <= 0) {
            return SCORE_THRESHOLD;
        }
        if (recencyDays <= threshold / 4.0) {
            return 5;
        }
        if (recencyDays <= threshold / 2.0) {
            return 4;
        }
        if (recencyDays <= threshold) {
            return 3;
        }
        if (recencyDays <= threshold * 2L) {
            return 2;
        }
        return 1;
    }

    /**
     * F 评分 (1-5)
     * <p>
     * frequency 越大 (越频繁) 评分越高。以 threshold 为高/低分界:
     * > threshold*2 → 5, > threshold*1.5 → 4, > threshold → 3,
     * > threshold/2 → 2, 否则 → 1。
     * </p>
     *
     * @param frequency 消费/互动次数
     * @param threshold F 高/低阈值次数
     * @return F 评分 (1-5)
     */
    public int calculateFScore(int frequency, int threshold) {
        if (threshold <= 0) {
            return frequency > 0 ? 5 : 1;
        }
        if (frequency > threshold * 2L) {
            return 5;
        }
        if (frequency > threshold * 1.5) {
            return 4;
        }
        if (frequency > threshold) {
            return 3;
        }
        if (frequency > threshold / 2.0) {
            return 2;
        }
        return 1;
    }

    /**
     * M 评分 (1-5)
     * <p>
     * monetary 越大 (消费越高) 评分越高。以 threshold 为高/低分界:
     * > threshold*2 → 5, > threshold*1.5 → 4, > threshold → 3,
     * > threshold/2 → 2, 否则 → 1。
     * </p>
     *
     * @param monetary   消费/互动总金额
     * @param threshold  M 高/低阈值金额
     * @return M 评分 (1-5)
     */
    public int calculateMScore(double monetary, double threshold) {
        if (threshold <= 0) {
            return monetary > 0 ? 5 : 1;
        }
        if (monetary > threshold * 2) {
            return 5;
        }
        if (monetary > threshold * 1.5) {
            return 4;
        }
        if (monetary > threshold) {
            return 3;
        }
        if (monetary > threshold / 2.0) {
            return 2;
        }
        return 1;
    }

    /**
     * 确定 RFM 分群编码
     * <p>
     * 以评分 3 为高/低分界: ≥3 为高 (1), &lt;3 为低 (0), 组合为 3 位编码如 "111"。
     * </p>
     *
     * @param rScore R 评分
     * @param fScore F 评分
     * @param mScore M 评分
     * @return RFM 分群编码 (如 "111")
     */
    public String determineSegment(int rScore, int fScore, int mScore) {
        int rBit = rScore >= SCORE_THRESHOLD ? 1 : 0;
        int fBit = fScore >= SCORE_THRESHOLD ? 1 : 0;
        int mBit = mScore >= SCORE_THRESHOLD ? 1 : 0;
        return String.valueOf(rBit) + fBit + mBit;
    }

    /**
     * 计算综合价值分 (0-100)
     * <p>
     * valueScore = (rScore * rWeight + fScore * fWeight + mScore * mWeight) / 5 * 100
     * </p>
     *
     * @param rScore   R 评分
     * @param fScore   F 评分
     * @param mScore   M 评分
     * @param rWeight  R 权重
     * @param fWeight  F 权重
     * @param mWeight  M 权重
     * @return 综合价值分 (0-100)
     */
    public double calculateValueScore(int rScore, int fScore, int mScore,
                                       double rWeight, double fWeight, double mWeight) {
        double weightSum = rWeight + fWeight + mWeight;
        if (weightSum <= 0) {
            return 0d;
        }
        double weighted = (rScore * rWeight + fScore * fWeight + mScore * mWeight) / weightSum;
        return Math.round(weighted / 5 * 100 * 100d) / 100d;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验权重合法性 (R/F/M 权重均需 ≥0)
     */
    private void validateWeights(Double r, Double f, Double m) throws ScrmException {
        if (r == null || f == null || m == null) {
            throw ScrmException.badRequest("R/F/M 权重不能为空");
        }
        if (r < 0 || f < 0 || m < 0) {
            throw ScrmException.badRequest("R/F/M 权重不能为负数");
        }
        if (r + f + m <= 0) {
            throw ScrmException.badRequest("R/F/M 权重之和必须大于 0");
        }
    }

    /**
     * 解析 R 数据源对应的时间点
     * <p>
     * LAST_INTERACTION: 客户的 lastInteractionAt (回退至 createTime)
     * LAST_ORDER: 暂无订单数据, 回退至 lastInteractionAt
     * </p>
     */
    private LocalDateTime resolveRecencyTime(ScrmRfmConfigEntity config, ScrmCustomerEntity customer) {
        LocalDateTime interactionTime = customer.getLastInteractionAt();
        if (interactionTime == null) {
            interactionTime = customer.getCreateTime();
        }
        if (RECENCY_SOURCE_LAST_ORDER.equals(config.getRecencySource())) {
            // 当前无订单数据, 回退至互动时间
            return interactionTime;
        }
        return interactionTime;
    }

    /**
     * 解析 M 数据源对应的金额
     * <p>
     * TOTAL_SPENT: 暂无订单数据, 默认 0
     * MANUAL: 暂无手动录入入口, 默认 0
     * </p>
     */
    private double resolveMonetary(ScrmRfmConfigEntity config) {
        // 当前系统无订单/交易数据, M 值默认 0; 后续接入订单模块后可扩展
        return 0d;
    }

    /**
     * 统计客户参与的会话数 (作为互动频次代理, 数据隔离)
     */
    private int countCustomerConversations(Long customerId) {
        List<ScrmConversationEntity> conversations = conversationRepository.findByCustomerId(customerId);
        return (int) conversations.stream()
                .filter(c -> true)
                .count();
    }

    /**
     * 按主键查询配置, 不存在或越权抛异常
     */
    private ScrmRfmConfigEntity findConfigOrThrow(Long id) throws ScrmException {
        ScrmRfmConfigEntity entity = configRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "RFM 配置不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询客户, 不存在或越权抛异常
     */
    private ScrmCustomerEntity findCustomerOrThrow(Long customerId) throws ScrmException {
        ScrmCustomerEntity entity = customerRepository.findById(customerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + customerId));
        return entity;
    }

    /**
     * 按主键查询策略, 不存在或越权抛异常
     */
    private ScrmRfmSegmentStrategyEntity findStrategyOrThrow(Long id) throws ScrmException {
        ScrmRfmSegmentStrategyEntity entity = strategyRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "RFM 分群策略不存在: id=" + id));
        return entity;
    }


    /**
     * 配置实体转 DTO
     */
    private ScrmRfmConfigDto toConfigDto(ScrmRfmConfigEntity entity) {
        ScrmRfmConfigDto dto = new ScrmRfmConfigDto();
        dto.setId(entity.getId());
        dto.setConfigName(entity.getConfigName());
        dto.setDescription(entity.getDescription());
        dto.setRWeight(entity.getRWeight());
        dto.setFWeight(entity.getFWeight());
        dto.setMWeight(entity.getMWeight());
        dto.setRThreshold(entity.getRThreshold());
        dto.setFThreshold(entity.getFThreshold());
        dto.setMThreshold(entity.getMThreshold());
        dto.setRecencySource(entity.getRecencySource());
        dto.setMonetarySource(entity.getMonetarySource());
        dto.setIsDefault(entity.getIsDefault());
        dto.setEnabled(entity.getEnabled());
        dto.setLastCalculatedAt(entity.getLastCalculatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 分析结果实体转 DTO
     */
    private ScrmRfmAnalysisDto toAnalysisDto(ScrmRfmAnalysisEntity entity) {
        ScrmRfmAnalysisDto dto = new ScrmRfmAnalysisDto();
        dto.setId(entity.getId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setConfigId(entity.getConfigId());
        dto.setRecencyDays(entity.getRecencyDays());
        dto.setFrequency(entity.getFrequency());
        dto.setMonetary(entity.getMonetary());
        dto.setRScore(entity.getRScore());
        dto.setFScore(entity.getFScore());
        dto.setMScore(entity.getMScore());
        dto.setRfmSegment(entity.getRfmSegment());
        dto.setSegmentName(entity.getSegmentName());
        dto.setSegmentCategory(entity.getSegmentCategory());
        dto.setValueScore(entity.getValueScore());
        dto.setCalculatedAt(entity.getCalculatedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 策略实体转 DTO
     */
    private ScrmRfmSegmentStrategyDto toStrategyDto(ScrmRfmSegmentStrategyEntity entity) {
        ScrmRfmSegmentStrategyDto dto = new ScrmRfmSegmentStrategyDto();
        dto.setId(entity.getId());
        dto.setStrategyName(entity.getStrategyName());
        dto.setSegmentCategory(entity.getSegmentCategory());
        dto.setSegmentCode(entity.getSegmentCode());
        dto.setStrategyType(entity.getStrategyType());
        dto.setDescription(entity.getDescription());
        dto.setActions(entity.getActions());
        dto.setEnabled(entity.getEnabled());
        dto.setPriority(entity.getPriority());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
