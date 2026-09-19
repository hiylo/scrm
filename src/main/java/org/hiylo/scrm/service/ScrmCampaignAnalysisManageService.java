/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignAnalysisManageService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCampaignAnalysisDto;
import org.hiylo.scrm.entity.ScrmCampaignAnalysisEntity;
import org.hiylo.scrm.entity.ScrmCampaignChannelEntity;
import org.hiylo.scrm.entity.ScrmCampaignFunnelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCampaignAnalysisRepository;
import org.hiylo.scrm.repository.ScrmCampaignChannelRepository;
import org.hiylo.scrm.repository.ScrmCampaignFunnelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 营销活动效果分析服务 - 分析管理子域。
 * <p>
 * 承载活动分析的增删改查 / 按活动查询 / 运行中与已完成列表 / 分析 (计算指标 → 生成结论) /
 * 重新分析 / 审批 / 分享 / 汇总 / 对比 / 批量分析, 以及 ROI / ROAS / 转化率 / CAC 等指标计算。
 * 同时作为分析子域的共享核心, 提供分析查询、安全取值与金额精度等跨子域共享能力。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCampaignAnalysisManageService {

    /** 分析状态: 计划中 */
    static final String STATUS_PLANNED = "PLANNED";
    /** 分析状态: 进行中 */
    static final String STATUS_RUNNING = "RUNNING";
    /** 分析状态: 已完成 */
    static final String STATUS_COMPLETED = "COMPLETED";
    /** 分析状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";
    /** 分析状态: 已暂停 */
    private static final String STATUS_PAUSED = "PAUSED";

    /** 默认操作人 */
    static final String DEFAULT_OPERATOR = "scrm-system";
    /** 金额精度 (保留两位小数) */
    private static final double MONEY_SCALE = 100d;
    /** 百分比系数 */
    static final double PERCENT_FACTOR = 100d;
    /** 千次展示系数 */
    static final double CPM_FACTOR = 1000d;

    /** 活动分析数据访问层 */
    private final ScrmCampaignAnalysisRepository analysisRepository;
    /** 渠道效果数据访问层 */
    private final ScrmCampaignChannelRepository channelRepository;
    /** 转化漏斗数据访问层 */
    private final ScrmCampaignFunnelRepository funnelRepository;

    // ============================================================
    // 分析管理
    // ============================================================

    /**
     * 创建活动分析。
     * <p>校验活动周期合法性后写入归属账号 ID 持久化, 缺省字段填默认值。</p>
     *
     * @param dto 分析参数
     * @return 创建后的分析
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmCampaignAnalysisDto createAnalysis(ScrmCampaignAnalysisDto dto) throws ScrmException {
        validateAnalysisDto(dto, false);
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw ScrmException.badRequest("活动结束日期不能早于开始日期");
        }
        ScrmCampaignAnalysisEntity entity = new ScrmCampaignAnalysisEntity();
        entity.setCampaignId(dto.getCampaignId());
        entity.setCampaignName(dto.getCampaignName());
        entity.setCampaignCode(dto.getCampaignCode());
        entity.setCampaignType(dto.getCampaignType());
        entity.setObjective(dto.getObjective());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setDurationDays(dto.getDurationDays() != null ? dto.getDurationDays()
                : (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()));
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_PLANNED);
        entity.setBudget(dto.getBudget() != null ? dto.getBudget() : 0d);
        entity.setActualCost(dto.getActualCost() != null ? dto.getActualCost() : 0d);
        entity.setChannels(dto.getChannels());
        entity.setSegments(dto.getSegments());
        entity.setProducts(dto.getProducts());
        entity.setReachCount(dto.getReachCount());
        entity.setImpressionCount(dto.getImpressionCount());
        entity.setClickCount(dto.getClickCount());
        entity.setRegistrationCount(dto.getRegistrationCount());
        entity.setParticipationCount(dto.getParticipationCount());
        entity.setConversionCount(dto.getConversionCount());
        entity.setRevenue(dto.getRevenue());
        entity.setNewCustomerCount(dto.getNewCustomerCount());
        entity.setRepeatCustomerCount(dto.getRepeatCustomerCount());
        entity.setNpsScore(dto.getNpsScore());
        entity.setCsatScore(dto.getCsatScore());
        entity.setTags(dto.getTags());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : DEFAULT_OPERATOR);
        entity.setIsApproved(false);
        entity = analysisRepository.save(entity);
        log.info("创建活动分析: id={}, campaignId={}, campaignName={}",
                entity.getId(), entity.getCampaignId(), entity.getCampaignName());
        return toAnalysisDto(entity);
    }

    /**
     * 更新活动分析 (字段非空才覆盖)。
     *
     * @param id  分析 ID
     * @param dto 分析参数
     * @return 更新后的分析
     * @throws ScrmException 分析不存在 / 参数非法
     */
    @Transactional
    public ScrmCampaignAnalysisDto updateAnalysis(Long id, ScrmCampaignAnalysisDto dto) throws ScrmException {
        ScrmCampaignAnalysisEntity entity = findAnalysisOrThrow(id);
        validateAnalysisDto(dto, true);
        if (dto.getCampaignName() != null) entity.setCampaignName(dto.getCampaignName());
        if (dto.getCampaignCode() != null) entity.setCampaignCode(dto.getCampaignCode());
        if (dto.getCampaignType() != null) entity.setCampaignType(dto.getCampaignType());
        if (dto.getObjective() != null) entity.setObjective(dto.getObjective());
        if (dto.getStartDate() != null) entity.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) entity.setEndDate(dto.getEndDate());
        if (entity.getEndDate().isBefore(entity.getStartDate())) {
            throw ScrmException.badRequest("活动结束日期不能早于开始日期");
        }
        if (dto.getStartDate() != null || dto.getEndDate() != null) {
            entity.setDurationDays((int) ChronoUnit.DAYS.between(entity.getStartDate(), entity.getEndDate()));
        }
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getBudget() != null) entity.setBudget(dto.getBudget());
        if (dto.getActualCost() != null) entity.setActualCost(dto.getActualCost());
        if (dto.getChannels() != null) entity.setChannels(dto.getChannels());
        if (dto.getSegments() != null) entity.setSegments(dto.getSegments());
        if (dto.getProducts() != null) entity.setProducts(dto.getProducts());
        if (dto.getReachCount() != null) entity.setReachCount(dto.getReachCount());
        if (dto.getImpressionCount() != null) entity.setImpressionCount(dto.getImpressionCount());
        if (dto.getClickCount() != null) entity.setClickCount(dto.getClickCount());
        if (dto.getRegistrationCount() != null) entity.setRegistrationCount(dto.getRegistrationCount());
        if (dto.getParticipationCount() != null) entity.setParticipationCount(dto.getParticipationCount());
        if (dto.getConversionCount() != null) entity.setConversionCount(dto.getConversionCount());
        if (dto.getRevenue() != null) entity.setRevenue(dto.getRevenue());
        if (dto.getNewCustomerCount() != null) entity.setNewCustomerCount(dto.getNewCustomerCount());
        if (dto.getRepeatCustomerCount() != null) entity.setRepeatCustomerCount(dto.getRepeatCustomerCount());
        if (dto.getNpsScore() != null) entity.setNpsScore(dto.getNpsScore());
        if (dto.getCsatScore() != null) entity.setCsatScore(dto.getCsatScore());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getHighlights() != null) entity.setHighlights(dto.getHighlights());
        if (dto.getIssues() != null) entity.setIssues(dto.getIssues());
        if (dto.getRecommendations() != null) entity.setRecommendations(dto.getRecommendations());
        if (dto.getAnalyzedBy() != null) entity.setAnalyzedBy(dto.getAnalyzedBy());
        entity = analysisRepository.save(entity);
        log.info("更新活动分析: id={}, campaignName={}", entity.getId(), entity.getCampaignName());
        return toAnalysisDto(entity);
    }

    /**
     * 删除活动分析。
     * <p>同时清理该分析下的渠道效果与漏斗阶段记录。</p>
     *
     * @param id 分析 ID
     * @throws ScrmException 分析不存在
     */
    @Transactional
    public void deleteAnalysis(Long id) throws ScrmException {
        ScrmCampaignAnalysisEntity entity = findAnalysisOrThrow(id);
        List<ScrmCampaignChannelEntity> channels = channelRepository.findByAnalysisId(id);
        if (!channels.isEmpty()) {
            channelRepository.deleteAll(channels);
        }
        List<ScrmCampaignFunnelEntity> funnels = funnelRepository.findByAnalysisId(id);
        if (!funnels.isEmpty()) {
            funnelRepository.deleteAll(funnels);
        }
        analysisRepository.delete(entity);
        log.info("删除活动分析: id={}, campaignName={}", id, entity.getCampaignName());
    }

    /**
     * 查询分析详情。
     *
     * @param id 分析 ID
     * @return 分析 DTO
     * @throws ScrmException 分析不存在
     */
    @Transactional(readOnly = true)
    public ScrmCampaignAnalysisDto getAnalysis(Long id) throws ScrmException {
        return toAnalysisDto(findAnalysisOrThrow(id));
    }

    /**
     * 按活动 ID 查询分析。
     *
     * @param campaignId 活动 ID
     * @return 分析 DTO
     * @throws ScrmException 分析不存在
     */
    @Transactional(readOnly = true)
    public ScrmCampaignAnalysisDto getAnalysisByCampaign(Long campaignId) throws ScrmException {
        ScrmCampaignAnalysisEntity entity = analysisRepository
                .findByCampaignId(campaignId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "活动分析不存在: campaignId=" + campaignId));
        return toAnalysisDto(entity);
    }

    /**
     * 分页查询活动分析, 支持按活动类型/状态/关键字过滤。
     *
     * @param campaignType 活动类型 (可空)
     * @param status       状态 (可空)
     * @param keyword      关键字 (匹配活动名称/编码, 可空)
     * @param pageable     分页参数
     * @return 分析分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCampaignAnalysisDto> listAnalyses(String campaignType, String status,
                                                       String keyword, Pageable pageable) {
        Specification<ScrmCampaignAnalysisEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (campaignType != null && !campaignType.isBlank()) {
                predicates.add(cb.equal(root.get("campaignType"), campaignType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(cb.like(root.get("campaignName"), like),
                        cb.like(root.get("campaignCode"), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return analysisRepository.findAll(spec, sorted).map(this::toAnalysisDto);
    }

    /**
     * 查询进行中的活动分析列表。
     *
     * @return 分析列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCampaignAnalysisDto> getRunningCampaigns() {
        return analysisRepository.findByStatus(STATUS_RUNNING)
                .stream().map(this::toAnalysisDto).collect(Collectors.toList());
    }

    /**
     * 查询已完成的活动分析列表。
     *
     * @return 分析列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCampaignAnalysisDto> getCompletedCampaigns() {
        return analysisRepository.findByStatus(STATUS_COMPLETED)
                .stream().map(this::toAnalysisDto).collect(Collectors.toList());
    }

    /**
     * 分析活动: 计算指标 → 生成分析 → 识别亮点与问题。
     * <p>
     * 根据原始计数 (触达/展示/点击/转化/收入/成本) 派生 CTR/CVR/ROI/ROAS/CPC/CPA/CPM/CAC/LTV 等
     * 派生指标, 并生成亮点与问题分析结论, 设置分析人与分析时间。
     * </p>
     *
     * @param id 分析 ID
     * @return 更新后的分析
     * @throws ScrmException 分析不存在
     */
    @Transactional
    public ScrmCampaignAnalysisDto analyzeCampaign(Long id) throws ScrmException {
        ScrmCampaignAnalysisEntity entity = findAnalysisOrThrow(id);
        recalcAnalysisMetrics(entity);
        entity.setHighlights(buildHighlights(entity));
        entity.setIssues(buildIssues(entity));
        entity.setRecommendations(buildRecommendations(entity));
        entity.setAnalyzedBy(DEFAULT_OPERATOR);
        entity.setAnalyzedAt(LocalDateTime.now());
        if (STATUS_PLANNED.equals(entity.getStatus())) {
            entity.setStatus(STATUS_RUNNING);
        }
        entity = analysisRepository.save(entity);
        log.info("分析活动: id={}, roi={}, roas={}", id, entity.getRoi(), entity.getRoas());
        return toAnalysisDto(entity);
    }

    /**
     * 重新分析活动 (重新计算指标与结论)。
     *
     * @param id 分析 ID
     * @return 更新后的分析
     * @throws ScrmException 分析不存在
     */
    @Transactional
    public ScrmCampaignAnalysisDto reanalyze(Long id) throws ScrmException {
        return analyzeCampaign(id);
    }

    /**
     * 审批分析。
     *
     * @param id         分析 ID
     * @param approvedBy 审批人
     * @return 更新后的分析
     * @throws ScrmException 分析不存在 / 审批人非法
     */
    @Transactional
    public ScrmCampaignAnalysisDto approveAnalysis(Long id, String approvedBy) throws ScrmException {
        if (approvedBy == null || approvedBy.isBlank()) {
            throw ScrmException.badRequest("审批人不能为空");
        }
        ScrmCampaignAnalysisEntity entity = findAnalysisOrThrow(id);
        entity.setApprovedBy(approvedBy);
        entity.setApprovedAt(LocalDateTime.now());
        entity.setIsApproved(true);
        entity = analysisRepository.save(entity);
        log.info("审批活动分析: id={}, approvedBy={}", id, approvedBy);
        return toAnalysisDto(entity);
    }

    /**
     * 分享分析 (返回分享摘要信息)。
     *
     * @param id 分析 ID
     * @return 分享摘要
     * @throws ScrmException 分析不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> shareAnalysis(Long id) throws ScrmException {
        ScrmCampaignAnalysisEntity entity = findAnalysisOrThrow(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysisId", entity.getId());
        result.put("campaignId", entity.getCampaignId());
        result.put("campaignName", entity.getCampaignName());
        result.put("campaignType", entity.getCampaignType());
        result.put("status", entity.getStatus());
        result.put("roi", entity.getRoi());
        result.put("roas", entity.getRoas());
        result.put("revenue", entity.getRevenue());
        result.put("conversionRate", entity.getConversionRate());
        result.put("isApproved", entity.getIsApproved());
        result.put("shareToken", "ca-" + Long.toHexString(entity.getId()));
        result.put("sharedAt", LocalDateTime.now());
        return result;
    }

    /**
     * 查询分析汇总 (核心指标聚合)。
     *
     * @param id 分析 ID
     * @return 汇总信息
     * @throws ScrmException 分析不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAnalysisSummary(Long id) throws ScrmException {
        ScrmCampaignAnalysisEntity entity = findAnalysisOrThrow(id);
        List<ScrmCampaignChannelEntity> channels = channelRepository.findByAnalysisId(id);
        List<ScrmCampaignFunnelEntity> funnels = funnelRepository.findByAnalysisId(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysisId", entity.getId());
        result.put("campaignId", entity.getCampaignId());
        result.put("campaignName", entity.getCampaignName());
        result.put("campaignType", entity.getCampaignType());
        result.put("status", entity.getStatus());
        result.put("durationDays", entity.getDurationDays());
        result.put("budget", round2(safe(entity.getBudget())));
        result.put("actualCost", round2(safe(entity.getActualCost())));
        result.put("reachCount", entity.getReachCount());
        result.put("clickCount", entity.getClickCount());
        result.put("conversionCount", entity.getConversionCount());
        result.put("revenue", round2(safe(entity.getRevenue())));
        result.put("profit", round2(safe(entity.getProfit())));
        result.put("roi", entity.getRoi());
        result.put("roas", entity.getRoas());
        result.put("cpc", entity.getCpc());
        result.put("cpa", entity.getCpa());
        result.put("cpm", entity.getCpm());
        result.put("cac", entity.getCac());
        result.put("ltv", entity.getLtv());
        result.put("conversionRate", entity.getConversionRate());
        result.put("clickThroughRate", entity.getClickThroughRate());
        result.put("channelCount", channels.size());
        result.put("funnelStageCount", funnels.size());
        result.put("isApproved", entity.getIsApproved());
        return result;
    }

    /**
     * 多活动效果对比。
     *
     * @param campaignIds 分析 ID 列表
     * @return 对比结果列表
     * @throws ScrmException 列表为空
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> compareCampaigns(List<Long> campaignIds) throws ScrmException {
        if (campaignIds == null || campaignIds.isEmpty()) {
            throw ScrmException.badRequest("对比分析 ID 列表不能为空");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : campaignIds) {
            ScrmCampaignAnalysisEntity entity = analysisRepository.findById(id)
                    .filter(e -> true)
                    .orElse(null);
            if (entity == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("analysisId", entity.getId());
            m.put("campaignId", entity.getCampaignId());
            m.put("campaignName", entity.getCampaignName());
            m.put("campaignType", entity.getCampaignType());
            m.put("status", entity.getStatus());
            m.put("revenue", round2(safe(entity.getRevenue())));
            m.put("actualCost", round2(safe(entity.getActualCost())));
            m.put("profit", round2(safe(entity.getProfit())));
            m.put("roi", entity.getRoi());
            m.put("roas", entity.getRoas());
            m.put("cpa", entity.getCpa());
            m.put("cpc", entity.getCpc());
            m.put("conversionRate", entity.getConversionRate());
            m.put("clickThroughRate", entity.getClickThroughRate());
            m.put("reachCount", entity.getReachCount());
            m.put("conversionCount", entity.getConversionCount());
            m.put("newCustomerCount", entity.getNewCustomerCount());
            m.put("cac", entity.getCac());
            m.put("ltv", entity.getLtv());
            result.add(m);
        }
        return result;
    }

    /**
     * 批量分析活动。
     *
     * @param ids 分析 ID 列表
     * @return 已分析的分析列表
     * @throws ScrmException 列表为空
     */
    @Transactional
    public List<ScrmCampaignAnalysisDto> batchAnalyze(List<Long> ids) throws ScrmException {
        if (ids == null || ids.isEmpty()) {
            throw ScrmException.badRequest("批量分析 ID 列表不能为空");
        }
        List<ScrmCampaignAnalysisDto> result = new ArrayList<>();
        for (Long id : ids) {
            try {
                result.add(analyzeCampaign(id));
            } catch (ScrmException e) {
                log.warn("批量分析跳过: id={}, reason={}", id, e.getMessage());
            }
        }
        return result;
    }

    // ============================================================
    // 指标计算
    // ============================================================

    /**
     * 计算 ROI = 利润 / 成本 (成本为 0 返回 0)。
     *
     * @param revenue 收入
     * @param cost    成本
     * @return ROI
     */
    public double calculateROI(Double revenue, Double cost) {
        double rev = safe(revenue);
        double c = safe(cost);
        return c > 0 ? round2((rev - c) / c) : 0d;
    }

    /**
     * 计算 ROAS = 收入 / 成本 (成本为 0 返回 0)。
     *
     * @param revenue 收入
     * @param cost    成本
     * @return ROAS
     */
    public double calculateROAS(Double revenue, Double cost) {
        double rev = safe(revenue);
        double c = safe(cost);
        return c > 0 ? round2(rev / c) : 0d;
    }

    /**
     * 计算转化率 = 转化数 / 触达数 (触达为 0 返回 0)。
     *
     * @param conversionCount 转化数
     * @param reachCount      触达数
     * @return 转化率
     */
    public double calculateConversionRate(Integer conversionCount, Integer reachCount) {
        int conv = safe(conversionCount);
        int reach = safe(reachCount);
        return reach > 0 ? round2((double) conv / reach) : 0d;
    }

    /**
     * 计算 CAC = 成本 / 新客户数 (新客户数为 0 返回 0)。
     *
     * @param cost             成本
     * @param newCustomerCount 新客户数
     * @return CAC
     */
    public double calculateCAC(Double cost, Integer newCustomerCount) {
        double c = safe(cost);
        int customers = safe(newCustomerCount);
        return customers > 0 ? round2(c / customers) : 0d;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询分析, 不存在抛异常。
     */
    ScrmCampaignAnalysisEntity findAnalysisOrThrow(Long id) throws ScrmException {
        ScrmCampaignAnalysisEntity entity = analysisRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "活动分析不存在: id=" + id));
        return entity;
    }

    /**
     * 重算活动分析派生指标 (CTR/CVR/ROI/ROAS/CPC/CPA/CPM/CAC/LTV/LTV-CAC/回收期/客单价/复购/新客占比)。
     */
    private void recalcAnalysisMetrics(ScrmCampaignAnalysisEntity e) {
        double cost = safe(e.getActualCost());
        double revenue = safe(e.getRevenue());
        int reach = safe(e.getReachCount());
        int impression = safe(e.getImpressionCount());
        int click = safe(e.getClickCount());
        int conversion = safe(e.getConversionCount());
        int newCustomer = safe(e.getNewCustomerCount());
        int repeatCustomer = safe(e.getRepeatCustomerCount());
        int totalCustomer = newCustomer + repeatCustomer;
        e.setClickThroughRate(impression > 0 ? round2((double) click / impression) : 0d);
        e.setConversionRate(reach > 0 ? round2((double) conversion / reach) : 0d);
        e.setProfit(round2(revenue - cost));
        e.setRoi(calculateROI(revenue, cost));
        e.setRoas(calculateROAS(revenue, cost));
        e.setCpc(click > 0 ? round2(cost / click) : 0d);
        e.setCpa(conversion > 0 ? round2(cost / conversion) : 0d);
        e.setCpm(impression > 0 ? round2(cost / impression * CPM_FACTOR) : 0d);
        e.setCac(calculateCAC(cost, newCustomer));
        // LTV: 客单价 * 人均订单数 (若无则用 收入/客户数 估算)
        double aov = safe(e.getAverageOrderValue());
        double opc = safe(e.getOrdersPerCustomer());
        if (aov <= 0d) {
            aov = totalCustomer > 0 ? revenue / totalCustomer : 0d;
            e.setAverageOrderValue(round2(aov));
        }
        if (opc <= 0d) {
            opc = totalCustomer > 0 ? (double) conversion / totalCustomer : 0d;
            e.setOrdersPerCustomer(round2(opc));
        }
        double ltv = round2(aov * opc);
        e.setLtv(ltv);
        double cac = e.getCac();
        e.setLtvCacRatio(cac > 0 ? round2(ltv / cac) : 0d);
        e.setPaybackPeriod(cac > 0 && aov > 0 ? round2(cac / aov) : 0d);
        e.setNewCustomerRate(totalCustomer > 0 ? round2((double) newCustomer / totalCustomer) : 0d);
    }

    /**
     * 生成亮点分析结论。
     */
    private String buildHighlights(ScrmCampaignAnalysisEntity e) {
        List<String> highlights = new ArrayList<>();
        if (safe(e.getRoi()) > 0d) {
            highlights.add("活动 ROI 达 " + round2(safe(e.getRoi()) * PERCENT_FACTOR) + "%, 实现正向回报");
        }
        if (safe(e.getRoas()) >= 1d) {
            highlights.add("ROAS 为 " + round2(safe(e.getRoas())) + ", 收入覆盖成本");
        }
        if (safe(e.getConversionRate()) > 0.1d) {
            highlights.add("转化率 " + round2(safe(e.getConversionRate()) * PERCENT_FACTOR) + "%, 高于行业基准");
        }
        if (safe(e.getNewCustomerCount()) > 0) {
            highlights.add("新增客户 " + safe(e.getNewCustomerCount()) + " 人");
        }
        if (safe(e.getNpsScore()) >= 50) {
            highlights.add("NPS 评分 " + safe(e.getNpsScore()) + ", 客户口碑良好");
        }
        return highlights.isEmpty() ? "暂无明显亮点" : String.join(";", highlights);
    }

    /**
     * 生成问题分析结论。
     */
    private String buildIssues(ScrmCampaignAnalysisEntity e) {
        List<String> issues = new ArrayList<>();
        if (safe(e.getRoi()) < 0d) {
            issues.add("活动 ROI 为负 (" + round2(safe(e.getRoi()) * PERCENT_FACTOR) + "%), 未实现盈利");
        }
        if (safe(e.getActualCost()) > 0 && safe(e.getBudget()) > 0 && safe(e.getActualCost()) > safe(e.getBudget())) {
            issues.add("实际成本超预算 " + round2((safe(e.getActualCost()) - safe(e.getBudget()))
                    / safe(e.getBudget()) * PERCENT_FACTOR) + "%");
        }
        if (safe(e.getConversionRate()) > 0 && safe(e.getConversionRate()) < 0.02d) {
            issues.add("转化率偏低 (" + round2(safe(e.getConversionRate()) * PERCENT_FACTOR) + "%)");
        }
        if (safe(e.getCac()) > 0 && safe(e.getLtv()) > 0 && safe(e.getLtvCacRatio()) < 1d) {
            issues.add("LTV/CAC 比率 " + round2(safe(e.getLtvCacRatio())) + " < 1, 获客成本偏高");
        }
        return issues.isEmpty() ? "暂无明显问题" : String.join(";", issues);
    }

    /**
     * 生成优化建议。
     */
    private String buildRecommendations(ScrmCampaignAnalysisEntity e) {
        List<String> recs = new ArrayList<>();
        if (safe(e.getRoi()) < 0d) {
            recs.add("建议优化成本结构, 提升高转化渠道投放占比");
        }
        if (safe(e.getConversionRate()) < 0.05d) {
            recs.add("建议优化转化漏斗瓶颈阶段, 提升整体转化率");
        }
        if (safe(e.getCac()) > 0 && safe(e.getLtvCacRatio()) < 3d) {
            recs.add("建议加强客户留存运营, 提升 LTV/CAC 至 3 以上");
        }
        if (safe(e.getNewCustomerRate()) < 0.3d && safe(e.getNewCustomerCount()) > 0) {
            recs.add("建议加大拉新力度, 提升新客占比");
        }
        return recs.isEmpty() ? "建议持续监测活动指标并保持当前策略" : String.join(";", recs);
    }

    /**
     * 校验分析 DTO。
     */
    private void validateAnalysisDto(ScrmCampaignAnalysisDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("分析参数不能为空");
        }
        if (dto.getCampaignName() != null) {
            if (dto.getCampaignName().isBlank()) {
                throw ScrmException.badRequest("活动名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("活动名称不能为空");
        }
        if (!partial && dto.getCampaignId() == null) {
            throw ScrmException.badRequest("营销活动 ID 不能为空");
        }
        if (!partial && dto.getCampaignType() == null) {
            throw ScrmException.badRequest("活动类型不能为空");
        }
        if (!partial && dto.getStartDate() == null) {
            throw ScrmException.badRequest("活动开始日期不能为空");
        }
        if (!partial && dto.getEndDate() == null) {
            throw ScrmException.badRequest("活动结束日期不能为空");
        }
    }

    /**
     * 安全获取 Integer 值 (null 返回 0)。
     */
    int safe(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * 安全获取 Double 值 (null 返回 0)。
     */
    double safe(Double value) {
        return value != null ? value : 0d;
    }

    /**
     * 金额保留两位小数。
     */
    double round2(double value) {
        return Math.round(value * MONEY_SCALE) / MONEY_SCALE;
    }

    /**
     * 活动分析实体转 DTO。
     */
    ScrmCampaignAnalysisDto toAnalysisDto(ScrmCampaignAnalysisEntity e) {
        ScrmCampaignAnalysisDto dto = new ScrmCampaignAnalysisDto();
        dto.setId(e.getId());
        dto.setCampaignId(e.getCampaignId());
        dto.setCampaignName(e.getCampaignName());
        dto.setCampaignCode(e.getCampaignCode());
        dto.setCampaignType(e.getCampaignType());
        dto.setObjective(e.getObjective());
        dto.setStartDate(e.getStartDate());
        dto.setEndDate(e.getEndDate());
        dto.setDurationDays(e.getDurationDays());
        dto.setStatus(e.getStatus());
        dto.setBudget(e.getBudget());
        dto.setActualCost(e.getActualCost());
        dto.setChannels(e.getChannels());
        dto.setSegments(e.getSegments());
        dto.setProducts(e.getProducts());
        dto.setReachCount(e.getReachCount());
        dto.setImpressionCount(e.getImpressionCount());
        dto.setClickCount(e.getClickCount());
        dto.setClickThroughRate(e.getClickThroughRate());
        dto.setRegistrationCount(e.getRegistrationCount());
        dto.setParticipationCount(e.getParticipationCount());
        dto.setConversionCount(e.getConversionCount());
        dto.setConversionRate(e.getConversionRate());
        dto.setRevenue(e.getRevenue());
        dto.setProfit(e.getProfit());
        dto.setRoi(e.getRoi());
        dto.setRoas(e.getRoas());
        dto.setCpc(e.getCpc());
        dto.setCpa(e.getCpa());
        dto.setCpm(e.getCpm());
        dto.setCac(e.getCac());
        dto.setLtv(e.getLtv());
        dto.setLtvCacRatio(e.getLtvCacRatio());
        dto.setPaybackPeriod(e.getPaybackPeriod());
        dto.setAverageOrderValue(e.getAverageOrderValue());
        dto.setOrdersPerCustomer(e.getOrdersPerCustomer());
        dto.setNewCustomerCount(e.getNewCustomerCount());
        dto.setRepeatCustomerCount(e.getRepeatCustomerCount());
        dto.setNewCustomerRate(e.getNewCustomerRate());
        dto.setRetentionRate(e.getRetentionRate());
        dto.setNpsScore(e.getNpsScore());
        dto.setCsatScore(e.getCsatScore());
        dto.setHighlights(e.getHighlights());
        dto.setIssues(e.getIssues());
        dto.setRecommendations(e.getRecommendations());
        dto.setAnalyzedBy(e.getAnalyzedBy());
        dto.setAnalyzedAt(e.getAnalyzedAt());
        dto.setApprovedBy(e.getApprovedBy());
        dto.setApprovedAt(e.getApprovedAt());
        dto.setIsApproved(e.getIsApproved());
        dto.setTags(e.getTags());
        dto.setCreatedBy(e.getCreatedBy());
        dto.setCreateTime(e.getCreateTime());
        dto.setUpdateTime(e.getUpdateTime());
        return dto;
    }
}