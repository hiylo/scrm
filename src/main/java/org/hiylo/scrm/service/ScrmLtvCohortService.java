/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvCohortService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmLtvCohortDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerLtvEntity;
import org.hiylo.scrm.entity.ScrmLtvCohortEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerLtvRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmLtvCohortRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 客户 LTV 同期群与统计服务: 价值层级分布/层级统计、同期群分组 (增删改查/生成/趋势)、
 * LTV 统计概览、模型效果统计、LTV 日趋势、ROI 统计、同期群对比。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmLtvCohortService {

    /** 价值层级: VIP */
    private static final String TIER_VIP = "VIP";
    /** 价值层级: 高价值 */
    private static final String TIER_HIGH = "HIGH";
    /** 价值层级: 中价值 */
    private static final String TIER_MEDIUM = "MEDIUM";
    /** 价值层级: 低价值 */
    private static final String TIER_LOW = "LOW";
    /** 价值层级: 流失风险 */
    private static final String TIER_AT_RISK = "AT_RISK";

    /** 同期群类型: 获客月份 */
    private static final String COHORT_ACQUISITION_MONTH = "ACQUISITION_MONTH";
    /** 同期群类型: 获客渠道 */
    private static final String COHORT_ACQUISITION_CHANNEL = "ACQUISITION_CHANNEL";
    /** 同期群类型: 客户层级 */
    private static final String COHORT_CUSTOMER_TIER = "CUSTOMER_TIER";
    /** 同期群类型: 地域 */
    private static final String COHORT_GEOGRAPHY = "GEOGRAPHY";

    /** 高流失风险概率阈值 */
    private static final double HIGH_CHURN_THRESHOLD = 0.7d;

    /** 客户 LTV 计算结果数据访问层 */
    private final ScrmCustomerLtvRepository ltvRepository;
    /** LTV 同期群分组数据访问层 */
    private final ScrmLtvCohortRepository cohortRepository;
    /** 客户数据访问层 (批量加载客户列表用于同期群生成) */
    private final ScrmCustomerRepository customerRepository;
    /** LTV 模型管理服务 (模型存在性校验) */
    private final ScrmLtvModelService modelService;

    // ============================================================
    // 价值层级
    // ============================================================

    /**
     * 价值层级分布统计: 各层级的客户数、占比与平均 LTV
     *
     * @param modelId 模型 ID (可空, 为空时统计全部)
     * @return 层级分布列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTierDistribution(Long modelId) {
        List<ScrmCustomerLtvEntity> all = modelId != null
                ? ltvRepository.findByModelId(modelId)
                : ltvRepository.findAll((root, query, cb) ->
                        cb.and());
        long total = all.size();
        Map<String, List<ScrmCustomerLtvEntity>> grouped = all.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getValueTier() != null ? e.getValueTier() : TIER_LOW));
        List<String> tierOrder = Arrays.asList(TIER_VIP, TIER_HIGH, TIER_MEDIUM, TIER_LOW, TIER_AT_RISK);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String tier : tierOrder) {
            List<ScrmCustomerLtvEntity> group = grouped.getOrDefault(tier, Collections.emptyList());
            if (group.isEmpty()) {
                continue;
            }
            long count = group.size();
            double avgLtv = group.stream().mapToDouble(e -> e.getPredictedLtv() != null
                    ? e.getPredictedLtv() : 0d).average().orElse(0d);
            double percentage = total > 0 ? Math.round(count * 10000d / total) / 100d : 0d;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tier", tier);
            item.put("count", count);
            item.put("percentage", percentage);
            item.put("avgPredictedLtv", round2(avgLtv));
            result.add(item);
        }
        return result;
    }

    /**
     * 各层级统计: 指定层级的客户数、平均/中位 LTV、平均 ROI
     *
     * @param modelId 模型 ID (可空)
     * @param tier    价值层级
     * @return 层级统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTierStats(Long modelId, String tier) {
        List<ScrmCustomerLtvEntity> all = modelId != null
                ? ltvRepository.findByModelId(modelId)
                : ltvRepository.findAll((root, query, cb) ->
                        cb.and());
        List<ScrmCustomerLtvEntity> group = all.stream()
                .filter(e -> tier.equals(e.getValueTier()))
                .collect(Collectors.toList());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("tier", tier);
        stats.put("count", group.size());
        double avgLtv = group.stream().mapToDouble(e -> e.getPredictedLtv() != null
                ? e.getPredictedLtv() : 0d).average().orElse(0d);
        double avgRoi = group.stream().mapToDouble(e -> e.getRoi() != null ? e.getRoi() : 0d).average().orElse(0d);
        List<Double> sortedLtv = group.stream()
                .map(e -> e.getPredictedLtv() != null ? e.getPredictedLtv() : 0d)
                .sorted().collect(Collectors.toList());
        double medianLtv = 0d;
        if (!sortedLtv.isEmpty()) {
            medianLtv = sortedLtv.size() % 2 == 0
                    ? (sortedLtv.get(sortedLtv.size() / 2 - 1) + sortedLtv.get(sortedLtv.size() / 2)) / 2
                    : sortedLtv.get(sortedLtv.size() / 2);
        }
        stats.put("avgPredictedLtv", round2(avgLtv));
        stats.put("medianPredictedLtv", round2(medianLtv));
        stats.put("avgRoi", round2(avgRoi));
        return stats;
    }

    // ============================================================
    // 同期群 (Cohort)
    // ============================================================

    /**
     * 创建同期群分组
     *
     * @param dto 分组参数
     * @return 创建后的分组
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmLtvCohortDto createCohort(ScrmLtvCohortDto dto) throws ScrmException {
        if (dto.getCohortName() == null || dto.getCohortName().isBlank()) {
            throw ScrmException.badRequest("分组名称不能为空");
        }
        if (dto.getCohortKey() == null || dto.getCohortKey().isBlank()) {
            throw ScrmException.badRequest("分组键值不能为空");
        }
        if (dto.getCohortStartDate() == null) {
            throw ScrmException.badRequest("分组开始日期不能为空");
        }
        if (dto.getPeriodMonths() == null) {
            throw ScrmException.badRequest("周期月数不能为空");
        }
        ScrmLtvCohortEntity entity = new ScrmLtvCohortEntity();
        entity.setCohortName(dto.getCohortName());
        entity.setCohortType(dto.getCohortType() != null ? dto.getCohortType() : COHORT_ACQUISITION_MONTH);
        entity.setCohortKey(dto.getCohortKey());
        entity.setCohortStartDate(dto.getCohortStartDate());
        entity.setCohortSize(dto.getCohortSize() != null ? dto.getCohortSize() : 0);
        entity.setPeriodMonths(dto.getPeriodMonths());
        entity.setAvgLtv(dto.getAvgLtv());
        entity.setMedianLtv(dto.getMedianLtv());
        entity.setTotalRevenue(dto.getTotalRevenue());
        entity.setAvgRevenue(dto.getAvgRevenue());
        entity.setAvgOrders(dto.getAvgOrders());
        entity.setRetentionRate(dto.getRetentionRate());
        entity.setActiveCustomers(dto.getActiveCustomers());
        entity.setChurnedCustomers(dto.getChurnedCustomers());
        entity.setAvgCustomerAgeDays(dto.getAvgCustomerAgeDays());
        entity.setTopTierCustomers(dto.getTopTierCustomers());
        entity.setCalculatedAt(LocalDateTime.now());
        entity = cohortRepository.save(entity);
        log.info("创建 LTV 同期群: id={}, name={}", entity.getId(), entity.getCohortName());
        return toCohortDto(entity);
    }

    /**
     * 更新同期群分组
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    @Transactional
    public ScrmLtvCohortDto updateCohort(Long id, ScrmLtvCohortDto dto) throws ScrmException {
        ScrmLtvCohortEntity entity = findCohortOrThrow(id);
        if (dto.getCohortName() != null) {
            entity.setCohortName(dto.getCohortName());
        }
        if (dto.getCohortType() != null) {
            entity.setCohortType(dto.getCohortType());
        }
        if (dto.getCohortKey() != null) {
            entity.setCohortKey(dto.getCohortKey());
        }
        if (dto.getCohortStartDate() != null) {
            entity.setCohortStartDate(dto.getCohortStartDate());
        }
        if (dto.getCohortSize() != null) {
            entity.setCohortSize(dto.getCohortSize());
        }
        if (dto.getPeriodMonths() != null) {
            entity.setPeriodMonths(dto.getPeriodMonths());
        }
        if (dto.getAvgLtv() != null) {
            entity.setAvgLtv(dto.getAvgLtv());
        }
        if (dto.getMedianLtv() != null) {
            entity.setMedianLtv(dto.getMedianLtv());
        }
        if (dto.getTotalRevenue() != null) {
            entity.setTotalRevenue(dto.getTotalRevenue());
        }
        if (dto.getAvgRevenue() != null) {
            entity.setAvgRevenue(dto.getAvgRevenue());
        }
        if (dto.getAvgOrders() != null) {
            entity.setAvgOrders(dto.getAvgOrders());
        }
        if (dto.getRetentionRate() != null) {
            entity.setRetentionRate(dto.getRetentionRate());
        }
        if (dto.getActiveCustomers() != null) {
            entity.setActiveCustomers(dto.getActiveCustomers());
        }
        if (dto.getChurnedCustomers() != null) {
            entity.setChurnedCustomers(dto.getChurnedCustomers());
        }
        if (dto.getAvgCustomerAgeDays() != null) {
            entity.setAvgCustomerAgeDays(dto.getAvgCustomerAgeDays());
        }
        if (dto.getTopTierCustomers() != null) {
            entity.setTopTierCustomers(dto.getTopTierCustomers());
        }
        entity = cohortRepository.save(entity);
        return toCohortDto(entity);
    }

    /**
     * 删除同期群分组
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在
     */
    @Transactional
    public void deleteCohort(Long id) throws ScrmException {
        ScrmLtvCohortEntity entity = findCohortOrThrow(id);
        cohortRepository.delete(entity);
        log.info("删除 LTV 同期群: id={}, name={}", id, entity.getCohortName());
    }

    /**
     * 查询同期群分组详情
     *
     * @param id 分组 ID
     * @return 分组 DTO
     * @throws ScrmException 分组不存在
     */
    @Transactional(readOnly = true)
    public ScrmLtvCohortDto getCohort(Long id) throws ScrmException {
        return toCohortDto(findCohortOrThrow(id));
    }

    /**
     * 分页查询同期群分组, 支持按分组类型与日期范围过滤
     *
     * @param cohortType 分组类型过滤 (可空)
     * @param startDate  分组开始日期下限 (可空)
     * @param endDate    分组开始日期上限 (可空)
     * @param pageable   分页参数
     * @return 分组分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmLtvCohortDto> listCohorts(String cohortType, LocalDate startDate, LocalDate endDate,
                                                Pageable pageable) {
        Specification<ScrmLtvCohortEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (cohortType != null && !cohortType.isBlank()) {
                predicates.add(cb.equal(root.get("cohortType"), cohortType));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("cohortStartDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("cohortStartDate"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "cohortStartDate"));
        return cohortRepository.findAll(spec, sorted).map(this::toCohortDto);
    }

    /**
     * 生成同期群分析
     * <p>
     * 按分组类型对客户进行分组并统计各分组的 LTV 表现:
     * ACQUISITION_MONTH 按客户创建月份分组, ACQUISITION_CHANNEL 按订单渠道分组,
     * CUSTOMER_TIER 按价值层级分组, GEOGRAPHY 按订单收货地域分组 (当前取渠道回退)。
     * </p>
     *
     * @param cohortType 分组类型
     * @param startDate  起始日期 (可空)
     * @param endDate    截止日期 (可空)
     * @return 生成的分组列表
     * @throws ScrmException 参数非法
     */
    @Transactional
    public List<ScrmLtvCohortDto> generateCohort(String cohortType, LocalDate startDate, LocalDate endDate)
            throws ScrmException {
        if (cohortType == null || cohortType.isBlank()) {
            throw ScrmException.badRequest("分组类型不能为空");
        }
        List<ScrmCustomerLtvEntity> ltvList = ltvRepository.findAll((root, query, cb) ->
                cb.and());
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        Map<Long, ScrmCustomerEntity> customerMap = customers.stream()
                .collect(Collectors.toMap(ScrmCustomerEntity::getId, c -> c, (a, b) -> a));

        // 按分组键聚合 LTV 结果
        Map<String, List<ScrmCustomerLtvEntity>> grouped = new LinkedHashMap<>();
        for (ScrmCustomerLtvEntity ltv : ltvList) {
            ScrmCustomerEntity customer = customerMap.get(ltv.getCustomerId());
            String key = resolveCohortKey(cohortType, customer, ltv, startDate, endDate);
            if (key == null) {
                continue;
            }
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(ltv);
        }

        List<ScrmLtvCohortDto> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, List<ScrmCustomerLtvEntity>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            List<ScrmCustomerLtvEntity> group = entry.getValue();
            ScrmLtvCohortEntity entity = buildCohortEntity(cohortType, key, group, customerMap, now);
            entity = cohortRepository.save(entity);
            results.add(toCohortDto(entity));
        }
        log.info("生成 LTV 同期群: cohortType={}, groups={}", cohortType, results.size());
        return results;
    }

    /**
     * 查询同期群趋势 (按平均 LTV 降序返回各分组)
     *
     * @param cohortType 分组类型
     * @return 分组列表
     */
    @Transactional(readOnly = true)
    public List<ScrmLtvCohortDto> getCohortTrend(String cohortType) {
        return cohortRepository.findByCohortTypeOrderByAvgLtvDesc(cohortType).stream()
                .map(this::toCohortDto).collect(Collectors.toList());
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * LTV 统计概览 (指定时间范围内的平均/中位/各层级分布/趋势)
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLtvStats(LocalDateTime startTime, LocalDateTime endTime) {
        Object[] row = ltvRepository.ltvStats(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        if (row != null && row.length >= 4) {
            stats.put("customerCount", row[0] != null ? ((Number) row[0]).longValue() : 0L);
            stats.put("avgPredictedLtv", round2(row[1] != null ? ((Number) row[1]).doubleValue() : 0d));
            stats.put("medianPredictedLtv", round2(row[2] != null ? ((Number) row[2]).doubleValue() : 0d));
            stats.put("avgHistoricalLtv", round2(row[3] != null ? ((Number) row[3]).doubleValue() : 0d));
        }
        stats.put("tierDistribution", getTierDistribution(null));
        return stats;
    }

    /**
     * 模型效果统计 (指定模型的客户数、平均预测/历史 LTV、平均置信度)
     *
     * @param modelId 模型 ID
     * @return 模型效果统计
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getModelPerformance(Long modelId) throws ScrmException {
        modelService.findModelOrThrow(modelId);
        Object[] row = ltvRepository.modelPerformance(modelId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("modelId", modelId);
        if (row != null && row.length >= 4) {
            stats.put("customerCount", row[0] != null ? ((Number) row[0]).longValue() : 0L);
            stats.put("avgPredictedLtv", round2(row[1] != null ? ((Number) row[1]).doubleValue() : 0d));
            stats.put("avgHistoricalLtv", round2(row[2] != null ? ((Number) row[2]).doubleValue() : 0d));
            stats.put("avgConfidenceScore", round2(row[3] != null ? ((Number) row[3]).doubleValue() : 0d));
        }
        stats.put("tierDistribution", getTierDistribution(modelId));
        return stats;
    }

    /**
     * LTV 趋势 (近 N 天的平均预测 LTV 按日序列)
     *
     * @param days 天数
     * @return 趋势序列 [{date, avgLtv}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLtvTrend(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(Math.max(1, days));
        List<Object[]> rows = ltvRepository.dailyAvgLtv(from);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", row[0] != null ? row[0].toString() : "");
            item.put("avgLtv", round2(row[1] != null ? ((Number) row[1]).doubleValue() : 0d));
            result.add(item);
        }
        return result;
    }

    /**
     * ROI 统计 (指定时间范围内的平均 ROI、平均盈利性、总获客成本、总预测 LTV)
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return ROI 统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRoiStats(LocalDateTime startTime, LocalDateTime endTime) {
        Object[] row = ltvRepository.roiStats(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        if (row != null && row.length >= 4) {
            stats.put("avgRoi", round2(row[0] != null ? ((Number) row[0]).doubleValue() : 0d));
            stats.put("avgProfitability", round2(row[1] != null ? ((Number) row[1]).doubleValue() : 0d));
            stats.put("totalAcquisitionCost", round2(row[2] != null ? ((Number) row[2]).doubleValue() : 0d));
            stats.put("totalPredictedLtv", round2(row[3] != null ? ((Number) row[3]).doubleValue() : 0d));
        }
        return stats;
    }

    /**
     * 同期群对比 (指定分组类型的聚合: 分组数、总客户数、平均 LTV、平均留存率)
     *
     * @param cohortType 分组类型
     * @param startDate  起始日期 (可空, 当前未参与聚合, 保留接口兼容)
     * @param endDate    截止日期 (可空, 当前未参与聚合, 保留接口兼容)
     * @return 对比结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCohortComparison(String cohortType, LocalDate startDate, LocalDate endDate) {
        Object[] row = cohortRepository.cohortTypeAggregation(cohortType);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("cohortType", cohortType);
        if (row != null && row.length >= 4) {
            stats.put("cohortCount", row[0] != null ? ((Number) row[0]).longValue() : 0L);
            stats.put("totalCustomers", row[1] != null ? ((Number) row[1]).longValue() : 0L);
            stats.put("avgLtv", round2(row[2] != null ? ((Number) row[2]).doubleValue() : 0d));
            stats.put("avgRetentionRate", round2(row[3] != null ? ((Number) row[3]).doubleValue() : 0d));
        }
        // 各分组明细
        stats.put("cohorts", getCohortTrend(cohortType));
        return stats;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 解析同期群分组键
     *
     * @param cohortType 分组类型
     * @param customer   客户实体
     * @param ltv        LTV 结果实体
     * @param startDate  起始日期 (可空)
     * @param endDate    截止日期 (可空)
     * @return 分组键, 客户不存在或超出日期范围时返回 null
     */
    private String resolveCohortKey(String cohortType, ScrmCustomerEntity customer,
                                     ScrmCustomerLtvEntity ltv, LocalDate startDate, LocalDate endDate) {
        if (customer == null || customer.getCreateTime() == null) {
            return null;
        }
        LocalDate createDate = customer.getCreateTime().toLocalDate();
        if (startDate != null && createDate.isBefore(startDate)) {
            return null;
        }
        if (endDate != null && createDate.isAfter(endDate)) {
            return null;
        }
        switch (cohortType) {
            case COHORT_ACQUISITION_MONTH:
                return YearMonth.from(customer.getCreateTime()).toString();
            case COHORT_ACQUISITION_CHANNEL:
                return customer.getPlatformType() != null ? customer.getPlatformType() : "UNKNOWN";
            case COHORT_CUSTOMER_TIER:
                return ltv.getValueTier() != null ? ltv.getValueTier() : TIER_LOW;
            case COHORT_GEOGRAPHY:
                return customer.getPlatformType() != null ? customer.getPlatformType() : "UNKNOWN";
            default:
                return YearMonth.from(customer.getCreateTime()).toString();
        }
    }

    /**
     * 构建同期群实体 (基于分组 LTV 结果聚合统计)
     *
     * @param cohortType  分组类型
     * @param key         分组键
     * @param group       分组 LTV 结果
     * @param customerMap 客户映射
     * @param now         当前时间
     * @return 同期群实体
     */
    private ScrmLtvCohortEntity buildCohortEntity(String cohortType, String key,
                                                    List<ScrmCustomerLtvEntity> group,
                                                    Map<Long, ScrmCustomerEntity> customerMap,
                                                    LocalDateTime now) {
        int size = group.size();
        double totalRevenue = group.stream().mapToDouble(g -> g.getTotalRevenue() != null
                ? g.getTotalRevenue() : 0d).sum();
        List<Double> ltvs = group.stream()
                .map(g -> g.getPredictedLtv() != null ? g.getPredictedLtv() : 0d)
                .sorted().collect(Collectors.toList());
        double avgLtv = ltvs.stream().mapToDouble(d -> d).average().orElse(0d);
        double medianLtv = ltvs.isEmpty() ? 0d : (ltvs.size() % 2 == 0
                ? (ltvs.get(ltvs.size() / 2 - 1) + ltvs.get(ltvs.size() / 2)) / 2
                : ltvs.get(ltvs.size() / 2));
        int activeCustomers = (int) group.stream()
                .filter(g -> g.getChurnProbability() != null && g.getChurnProbability() < HIGH_CHURN_THRESHOLD)
                .count();
        int churnedCustomers = size - activeCustomers;
        int topTierCustomers = (int) group.stream()
                .filter(g -> TIER_VIP.equals(g.getValueTier()) || TIER_HIGH.equals(g.getValueTier()))
                .count();
        double avgAge = group.stream().mapToInt(g -> g.getCustomerAgeDays() != null
                ? g.getCustomerAgeDays() : 0).average().orElse(0d);

        ScrmLtvCohortEntity entity = new ScrmLtvCohortEntity();
        entity.setCohortName(cohortType + " - " + key);
        entity.setCohortType(cohortType);
        entity.setCohortKey(key);
        LocalDate cohortStart = group.stream()
                .map(g -> customerMap.get(g.getCustomerId()))
                .filter(Objects::nonNull)
                .map(c -> c.getCreateTime().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
        entity.setCohortStartDate(cohortStart);
        entity.setCohortSize(size);
        entity.setPeriodMonths(12);
        entity.setAvgLtv(round2(avgLtv));
        entity.setMedianLtv(round2(medianLtv));
        entity.setTotalRevenue(round2(totalRevenue));
        entity.setAvgRevenue(size > 0 ? round2(totalRevenue / size) : 0d);
        entity.setAvgOrders((int) group.stream().mapToInt(g -> g.getTotalOrders() != null
                ? g.getTotalOrders() : 0).average().orElse(0));
        entity.setRetentionRate(size > 0 ? round2((double) activeCustomers / size) : 0d);
        entity.setActiveCustomers(activeCustomers);
        entity.setChurnedCustomers(churnedCustomers);
        entity.setAvgCustomerAgeDays((int) avgAge);
        entity.setTopTierCustomers(topTierCustomers);
        entity.setCalculatedAt(now);
        return entity;
    }

    /**
     * 按主键查询同期群, 不存在或越权抛异常
     *
     * @param id 分组 ID
     * @return 同期群实体
     * @throws ScrmException 分组不存在
     */
    private ScrmLtvCohortEntity findCohortOrThrow(Long id) throws ScrmException {
        ScrmLtvCohortEntity entity = cohortRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "LTV 同期群不存在: id=" + id));
        return entity;
    }

    /**
     * 保留两位小数
     *
     * @param value 原始值
     * @return 保留两位小数后的值
     */
    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /**
     * 同期群实体转 DTO
     *
     * @param entity 同期群实体
     * @return 同期群 DTO
     */
    private ScrmLtvCohortDto toCohortDto(ScrmLtvCohortEntity entity) {
        ScrmLtvCohortDto dto = new ScrmLtvCohortDto();
        dto.setId(entity.getId());
        dto.setCohortName(entity.getCohortName());
        dto.setCohortType(entity.getCohortType());
        dto.setCohortKey(entity.getCohortKey());
        dto.setCohortStartDate(entity.getCohortStartDate());
        dto.setCohortSize(entity.getCohortSize());
        dto.setPeriodMonths(entity.getPeriodMonths());
        dto.setAvgLtv(entity.getAvgLtv());
        dto.setMedianLtv(entity.getMedianLtv());
        dto.setTotalRevenue(entity.getTotalRevenue());
        dto.setAvgRevenue(entity.getAvgRevenue());
        dto.setAvgOrders(entity.getAvgOrders());
        dto.setRetentionRate(entity.getRetentionRate());
        dto.setActiveCustomers(entity.getActiveCustomers());
        dto.setChurnedCustomers(entity.getChurnedCustomers());
        dto.setAvgCustomerAgeDays(entity.getAvgCustomerAgeDays());
        dto.setTopTierCustomers(entity.getTopTierCustomers());
        dto.setCalculatedAt(entity.getCalculatedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}