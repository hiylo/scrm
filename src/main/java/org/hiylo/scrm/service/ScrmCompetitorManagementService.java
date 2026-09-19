/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorManagementService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCompetitorDto;
import org.hiylo.scrm.entity.ScrmCompetitorEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCompetitorActivityRepository;
import org.hiylo.scrm.repository.ScrmCompetitorProductRepository;
import org.hiylo.scrm.repository.ScrmCompetitorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SCRM 竞品信息管理服务。
 * <p>
 * 承载竞品信息子域的全部能力: 竞品增删改查、按编码查询、分页过滤、监测开关、频率调整、
 * 最近监测时间、按威胁等级/行业查询、归档与竞品概要 (产品/动态/价格/威胁评估)。
 * </p>
 * <p>
 * 校验失败抛出 {@link ScrmException} 携带通用错误码 (NOT_FOUND / BAD_REQUEST / CONFLICT)。
 * 威胁评估委托 {@link ScrmCompetitorAnalysisService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCompetitorManagementService {

    // ==================== 默认值常量 ====================

    /** 默认威胁等级 */
    private static final String DEFAULT_THREAT_LEVEL = "MEDIUM";
    /** 默认监测开关 */
    private static final boolean DEFAULT_MONITORING_ENABLED = true;
    /** 默认监测频率 */
    private static final String DEFAULT_MONITORING_FREQUENCY = "DAILY";
    /** 默认竞品状态 */
    private static final String DEFAULT_COMPETITOR_STATUS = "ACTIVE";

    // ==================== 合法枚举值 ====================

    /** 合法的威胁等级 */
    private static final List<String> VALID_THREAT_LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    /** 合法的监测频率 */
    private static final List<String> VALID_FREQUENCIES = List.of("REALTIME", "DAILY", "WEEKLY", "MONTHLY");
    /** 合法的竞品状态 */
    private static final List<String> VALID_COMPETITOR_STATUSES = List.of("ACTIVE", "INACTIVE", "ARCHIVED");
    /** 合法的公司规模 */
    private static final List<String> VALID_COMPANY_SIZES =
            List.of("STARTUP", "SMALL", "MEDIUM", "LARGE", "ENTERPRISE");
    /** 合法的市场地位 */
    private static final List<String> VALID_MARKET_POSITIONS =
            List.of("LEADER", "CHALLENGER", "FOLLOWER", "NICHE", "NEW_ENTRANT");
    /** 合法的融资阶段 */
    private static final List<String> VALID_FUNDING_STAGES =
            List.of("BOOTSTRAP", "SEED", "A", "B", "C", "IPO");

    // ==================== 依赖注入 ====================

    /** 竞品数据访问层 */
    private final ScrmCompetitorRepository competitorRepository;
    /** 竞品产品数据访问层 */
    private final ScrmCompetitorProductRepository productRepository;
    /** 竞品动态数据访问层 */
    private final ScrmCompetitorActivityRepository activityRepository;
    /** 竞品分析统计服务 */
    private final ScrmCompetitorAnalysisService analysisService;

    /**
     * 创建竞品。
     * <p>校验 competitorCode 唯一, threatLevel / monitoringFrequency / status 缺省时填默认值。</p>
     *
     * @param dto 竞品参数
     * @return 创建后的竞品
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmCompetitorEntity createCompetitor(ScrmCompetitorDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("竞品参数不能为空");
        }
        if (competitorRepository.findByCompetitorCode(dto.getCompetitorCode()).isPresent()) {
            throw ScrmException.conflict("竞品编码已存在: " + dto.getCompetitorCode());
        }
        validateCompetitorEnums(dto, false);
        ScrmCompetitorEntity entity = new ScrmCompetitorEntity();
        entity.setCompetitorName(dto.getCompetitorName());
        entity.setCompetitorCode(dto.getCompetitorCode());
        entity.setShortName(dto.getShortName());
        entity.setDescription(dto.getDescription());
        entity.setWebsite(dto.getWebsite());
        entity.setLogoUrl(dto.getLogoUrl());
        entity.setIndustry(dto.getIndustry());
        entity.setFoundedYear(dto.getFoundedYear());
        entity.setCompanySize(dto.getCompanySize());
        entity.setHeadquarters(dto.getHeadquarters());
        entity.setMarketPosition(dto.getMarketPosition());
        entity.setMarketShare(dto.getMarketShare() != null ? dto.getMarketShare() : 0.0);
        entity.setStrengths(dto.getStrengths());
        entity.setWeaknesses(dto.getWeaknesses());
        entity.setThreatLevel(dto.getThreatLevel() != null ? dto.getThreatLevel() : DEFAULT_THREAT_LEVEL);
        entity.setCompetitiveProducts(dto.getCompetitiveProducts());
        entity.setTargetMarket(dto.getTargetMarket());
        entity.setPricingStrategy(dto.getPricingStrategy());
        entity.setBusinessModel(dto.getBusinessModel());
        entity.setFundingStage(dto.getFundingStage());
        entity.setTotalFunding(dto.getTotalFunding() != null ? dto.getTotalFunding() : 0.0);
        entity.setKeyPersonnel(dto.getKeyPersonnel());
        entity.setSocialMedia(dto.getSocialMedia());
        entity.setMonitoringEnabled(dto.getMonitoringEnabled() != null
                ? dto.getMonitoringEnabled()
                : DEFAULT_MONITORING_ENABLED);
        entity.setMonitoringFrequency(dto.getMonitoringFrequency() != null
                ? dto.getMonitoringFrequency()
                : DEFAULT_MONITORING_FREQUENCY);
        entity.setLastMonitoredAt(dto.getLastMonitoredAt());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : DEFAULT_COMPETITOR_STATUS);
        entity.setTags(dto.getTags());
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = competitorRepository.save(entity);
        log.info("创建竞品: id={}, name={}, code={}", entity.getId(), entity.getCompetitorName(),
                entity.getCompetitorCode());
        return entity;
    }

    /**
     * 更新竞品（字段非空才覆盖）。
     *
     * @param id  竞品 ID
     * @param dto 竞品参数
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在 / 参数非法 / 编码重复
     */
    @Transactional
    public ScrmCompetitorEntity updateCompetitor(Long id, ScrmCompetitorDto dto) throws ScrmException {
        ScrmCompetitorEntity entity = findCompetitorOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("竞品参数不能为空");
        }
        validateCompetitorEnums(dto, true);
        // 编码变更需校验唯一性
        if (dto.getCompetitorCode() != null && !dto.getCompetitorCode().equals(entity.getCompetitorCode())) {
            Optional<ScrmCompetitorEntity> existing = competitorRepository
                    .findByCompetitorCode(dto.getCompetitorCode());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw ScrmException.conflict("竞品编码已存在: " + dto.getCompetitorCode());
            }
            entity.setCompetitorCode(dto.getCompetitorCode());
        }
        if (dto.getCompetitorName() != null) entity.setCompetitorName(dto.getCompetitorName());
        if (dto.getShortName() != null) entity.setShortName(dto.getShortName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getWebsite() != null) entity.setWebsite(dto.getWebsite());
        if (dto.getLogoUrl() != null) entity.setLogoUrl(dto.getLogoUrl());
        if (dto.getIndustry() != null) entity.setIndustry(dto.getIndustry());
        if (dto.getFoundedYear() != null) entity.setFoundedYear(dto.getFoundedYear());
        if (dto.getCompanySize() != null) entity.setCompanySize(dto.getCompanySize());
        if (dto.getHeadquarters() != null) entity.setHeadquarters(dto.getHeadquarters());
        if (dto.getMarketPosition() != null) entity.setMarketPosition(dto.getMarketPosition());
        if (dto.getMarketShare() != null) entity.setMarketShare(dto.getMarketShare());
        if (dto.getStrengths() != null) entity.setStrengths(dto.getStrengths());
        if (dto.getWeaknesses() != null) entity.setWeaknesses(dto.getWeaknesses());
        if (dto.getThreatLevel() != null) entity.setThreatLevel(dto.getThreatLevel());
        if (dto.getCompetitiveProducts() != null) entity.setCompetitiveProducts(dto.getCompetitiveProducts());
        if (dto.getTargetMarket() != null) entity.setTargetMarket(dto.getTargetMarket());
        if (dto.getPricingStrategy() != null) entity.setPricingStrategy(dto.getPricingStrategy());
        if (dto.getBusinessModel() != null) entity.setBusinessModel(dto.getBusinessModel());
        if (dto.getFundingStage() != null) entity.setFundingStage(dto.getFundingStage());
        if (dto.getTotalFunding() != null) entity.setTotalFunding(dto.getTotalFunding());
        if (dto.getKeyPersonnel() != null) entity.setKeyPersonnel(dto.getKeyPersonnel());
        if (dto.getSocialMedia() != null) entity.setSocialMedia(dto.getSocialMedia());
        if (dto.getMonitoringEnabled() != null) entity.setMonitoringEnabled(dto.getMonitoringEnabled());
        if (dto.getMonitoringFrequency() != null) entity.setMonitoringFrequency(dto.getMonitoringFrequency());
        if (dto.getLastMonitoredAt() != null) entity.setLastMonitoredAt(dto.getLastMonitoredAt());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = competitorRepository.save(entity);
        log.info("更新竞品: id={}, name={}", entity.getId(), entity.getCompetitorName());
        return entity;
    }

    /**
     * 删除竞品。
     *
     * @param id 竞品 ID
     * @throws ScrmException 竞品不存在
     */
    @Transactional
    public void deleteCompetitor(Long id) throws ScrmException {
        ScrmCompetitorEntity entity = findCompetitorOrThrow(id);
        competitorRepository.delete(entity);
        log.info("删除竞品: id={}, name={}", id, entity.getCompetitorName());
    }

    /**
     * 查询竞品详情。
     *
     * @param id 竞品 ID
     * @return 竞品实体
     * @throws ScrmException 竞品不存在
     */
    @Transactional(readOnly = true)
    public ScrmCompetitorEntity getCompetitor(Long id) throws ScrmException {
        return findCompetitorOrThrow(id);
    }

    /**
     * 按编码查询竞品。
     *
     * @param code 竞品编码
     * @return 竞品实体
     * @throws ScrmException 竞品不存在
     */
    @Transactional(readOnly = true)
    public ScrmCompetitorEntity getCompetitorByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("竞品编码不能为空");
        }
        return competitorRepository.findByCompetitorCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "竞品不存在: code=" + code));
    }

    /**
     * 分页查询竞品, 支持按行业、威胁等级、状态与关键字过滤。
     *
     * @param industry    行业过滤（可空）
     * @param threatLevel 威胁等级过滤（可空）
     * @param status      状态过滤（可空）
     * @param keyword     名称/编码/简称关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 竞品分页结果 (按 updateTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorEntity> listCompetitors(String industry, String threatLevel, String status,
                                                       String keyword, Pageable pageable) {
        Specification<ScrmCompetitorEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (industry != null && !industry.isBlank()) {
                predicates.add(cb.equal(root.get("industry"), industry));
            }
            if (threatLevel != null && !threatLevel.isBlank()) {
                predicates.add(cb.equal(root.get("threatLevel"), threatLevel));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("competitorName"), like),
                        cb.like(root.get("competitorCode"), like),
                        cb.like(root.get("shortName"), like)));
            }
            query.orderBy(cb.desc(root.get("updateTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return competitorRepository.findAll(spec, pageable);
    }

    /**
     * 启用竞品监测。
     *
     * @param id 竞品 ID
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在
     */
    @Transactional
    public ScrmCompetitorEntity enableMonitoring(Long id) throws ScrmException {
        ScrmCompetitorEntity entity = findCompetitorOrThrow(id);
        entity.setMonitoringEnabled(true);
        entity = competitorRepository.save(entity);
        log.info("启用竞品监测: id={}", id);
        return entity;
    }

    /**
     * 停止竞品监测。
     *
     * @param id 竞品 ID
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在
     */
    @Transactional
    public ScrmCompetitorEntity disableMonitoring(Long id) throws ScrmException {
        ScrmCompetitorEntity entity = findCompetitorOrThrow(id);
        entity.setMonitoringEnabled(false);
        entity = competitorRepository.save(entity);
        log.info("停止竞品监测: id={}", id);
        return entity;
    }

    /**
     * 更新竞品监测频率。
     *
     * @param id        竞品 ID
     * @param frequency 监测频率: REALTIME / DAILY / WEEKLY / MONTHLY
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在 / 频率非法
     */
    @Transactional
    public ScrmCompetitorEntity updateMonitoringFrequency(Long id, String frequency) throws ScrmException {
        if (frequency == null || !VALID_FREQUENCIES.contains(frequency)) {
            throw ScrmException.badRequest("监测频率非法: " + frequency + ", 仅支持 " + VALID_FREQUENCIES);
        }
        ScrmCompetitorEntity entity = findCompetitorOrThrow(id);
        entity.setMonitoringFrequency(frequency);
        entity = competitorRepository.save(entity);
        log.info("更新竞品监测频率: id={}, frequency={}", id, frequency);
        return entity;
    }

    /**
     * 更新竞品最近监测时间为当前时间。
     *
     * @param id 竞品 ID
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在
     */
    @Transactional
    public ScrmCompetitorEntity updateLastMonitored(Long id) throws ScrmException {
        ScrmCompetitorEntity entity = findCompetitorOrThrow(id);
        entity.setLastMonitoredAt(LocalDateTime.now());
        entity = competitorRepository.save(entity);
        log.info("更新竞品最近监测时间: id={}", id);
        return entity;
    }

    /**
     * 按威胁等级分页查询竞品。
     *
     * @param level    威胁等级
     * @param pageable 分页参数
     * @return 竞品分页结果 (按威胁等级排序, 再按更新时间倒序)
     * @throws ScrmException 威胁等级非法
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorEntity> getCompetitorsByThreat(String level, Pageable pageable)
            throws ScrmException {
        if (level == null || !VALID_THREAT_LEVELS.contains(level)) {
            throw ScrmException.badRequest("威胁等级非法: " + level + ", 仅支持 " + VALID_THREAT_LEVELS);
        }
        return competitorRepository.findByThreatLevel(level, pageable);
    }

    /**
     * 按行业分页查询竞品。
     *
     * @param industry 行业
     * @param pageable 分页参数
     * @return 竞品分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorEntity> getCompetitorsByIndustry(String industry, Pageable pageable) {
        return competitorRepository.findByIndustry(industry, pageable);
    }

    /**
     * 归档竞品 (状态置为 ARCHIVED)。
     *
     * @param id 竞品 ID
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在
     */
    @Transactional
    public ScrmCompetitorEntity archiveCompetitor(Long id) throws ScrmException {
        ScrmCompetitorEntity entity = findCompetitorOrThrow(id);
        entity.setStatus("ARCHIVED");
        entity = competitorRepository.save(entity);
        log.info("归档竞品: id={}", id);
        return entity;
    }

    /**
     * 竞品概要: 基础信息 + 产品数 + 动态数 + 价格统计 + 威胁评估。
     *
     * @param id 竞品 ID
     * @return 概要 Map
     * @throws ScrmException 竞品不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCompetitorSummary(Long id) throws ScrmException {
        ScrmCompetitorEntity competitor = findCompetitorOrThrow(id);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("competitor", competitor);
        // 产品数与价格统计
        Object[] priceStats = productRepository.aggregatePriceStats(id);
        long productCount = 0L;
        if (priceStats != null && priceStats.length >= 5) {
            productCount = priceStats[0] == null ? 0L : ((Number) priceStats[0]).longValue();
            Map<String, Object> price = new LinkedHashMap<>();
            price.put("productCount", productCount);
            price.put("avgCurrentPrice", priceStats[1] == null ? 0.0 : ((Number) priceStats[1]).doubleValue());
            price.put("minCurrentPrice", priceStats[2] == null ? 0.0 : ((Number) priceStats[2]).doubleValue());
            price.put("maxCurrentPrice", priceStats[3] == null ? 0.0 : ((Number) priceStats[3]).doubleValue());
            price.put("avgDiscountRate", priceStats[4] == null ? 0.0 : ((Number) priceStats[4]).doubleValue());
            summary.put("priceStats", price);
        } else {
            summary.put("priceStats", Map.of("productCount", 0L));
        }
        // 动态数
        long activityCount = activityRepository.countByCompetitor(id, null, null);
        summary.put("activityCount", activityCount);
        // 威胁评估
        summary.put("threatAssessment", analysisService.buildThreatAssessment(competitor, activityCount, productCount));
        return summary;
    }

    /**
     * 校验竞品枚举字段合法性。
     *
     * @param dto     竞品参数
     * @param partial 是否为部分更新
     * @throws ScrmException 参数非法
     */
    private void validateCompetitorEnums(ScrmCompetitorDto dto, boolean partial) throws ScrmException {
        if (dto.getThreatLevel() != null && !VALID_THREAT_LEVELS.contains(dto.getThreatLevel())) {
            throw ScrmException.badRequest("威胁等级非法: " + dto.getThreatLevel() + ", 仅支持 " + VALID_THREAT_LEVELS);
        }
        if (dto.getMonitoringFrequency() != null && !VALID_FREQUENCIES.contains(dto.getMonitoringFrequency())) {
            throw ScrmException.badRequest("监测频率非法: " + dto.getMonitoringFrequency()
                    + ", 仅支持 " + VALID_FREQUENCIES);
        }
        if (dto.getStatus() != null && !VALID_COMPETITOR_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest("状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_COMPETITOR_STATUSES);
        }
        if (dto.getCompanySize() != null && !VALID_COMPANY_SIZES.contains(dto.getCompanySize())) {
            throw ScrmException.badRequest("公司规模非法: " + dto.getCompanySize() + ", 仅支持 " + VALID_COMPANY_SIZES);
        }
        if (dto.getMarketPosition() != null && !VALID_MARKET_POSITIONS.contains(dto.getMarketPosition())) {
            throw ScrmException.badRequest("市场地位非法: " + dto.getMarketPosition()
                    + ", 仅支持 " + VALID_MARKET_POSITIONS);
        }
        if (dto.getFundingStage() != null && !VALID_FUNDING_STAGES.contains(dto.getFundingStage())) {
            throw ScrmException.badRequest("融资阶段非法: " + dto.getFundingStage() + ", 仅支持 " + VALID_FUNDING_STAGES);
        }
    }

    /**
     * 按主键查询竞品, 不存在抛异常, 并校验账号归属。
     *
     * @param id 竞品 ID
     * @return 竞品实体
     * @throws ScrmException 竞品不存在
     */
    ScrmCompetitorEntity findCompetitorOrThrow(Long id) throws ScrmException {
        ScrmCompetitorEntity entity = competitorRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "竞品不存在: id=" + id));
        return entity;
    }
}