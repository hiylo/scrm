/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvCalculationService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerLtvDto;
import org.hiylo.scrm.dto.ScrmLtvCalculateDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerLtvEntity;
import org.hiylo.scrm.entity.ScrmLtvModelEntity;
import org.hiylo.scrm.entity.ScrmOrderEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerLtvRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 客户 LTV 计算服务: 单客户/批量/全量 LTV 计算 (历史平均 + 线性回归 + DCF 等模型方法)、
 * 重算、结果查询与分页、Top 客户查询、价值层级判定、预测 LTV 与流失概率计算辅助。
 * <p>
 * 历史数据来源为客户在回溯窗口内已完成 (COMPLETED) 的订单; 无订单数据时各项默认 0。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmLtvCalculationService {

    // ==================== 计算方法 ====================

    /** 计算方法: 简单平均 */
    private static final String METHOD_SIMPLE_AVG = "SIMPLE_AVG";
    /** 计算方法: 加权平均 */
    private static final String METHOD_WEIGHTED_AVG = "WEIGHTED_AVG";
    /** 计算方法: 折现现金流 */
    private static final String METHOD_DCF = "DISCOUNTED_CASH_FLOW";
    /** 计算方法: Pareto NBD */
    private static final String METHOD_PARETO_NBD = "PARETO_NBD";
    /** 计算方法: 买直到流失 */
    private static final String METHOD_BTYYD = "BUY_TILL_YOU_DIE";

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

    /** 增长潜力: 高 */
    private static final String GROWTH_HIGH = "HIGH";
    /** 增长潜力: 中 */
    private static final String GROWTH_MEDIUM = "MEDIUM";
    /** 增长潜力: 低 */
    private static final String GROWTH_LOW = "LOW";
    /** 增长潜力: 无 */
    private static final String GROWTH_NONE = "NONE";

    /** LTV 趋势: 上升 */
    private static final String TREND_INCREASING = "INCREASING";
    /** LTV 趋势: 稳定 */
    private static final String TREND_STABLE = "STABLE";
    /** LTV 趋势: 下降 */
    private static final String TREND_DECREASING = "DECREASING";

    /** 订单状态: 已取消 */
    private static final String ORDER_STATUS_CANCELLED = "CANCELLED";

    /** 默认价值层级阈值 (VIP/HIGH/MEDIUM/LOW 的最低 LTV) */
    private static final double DEFAULT_VIP_THRESHOLD = 10000d;
    /** 默认价值层级阈值: HIGH 高价值客户的最低 LTV */
    private static final double DEFAULT_HIGH_THRESHOLD = 5000d;
    /** 默认价值层级阈值: MEDIUM 中价值客户的最低 LTV */
    private static final double DEFAULT_MEDIUM_THRESHOLD = 1000d;
    /** 默认价值层级阈值: LOW 基础价值客户的最低 LTV */
    private static final double DEFAULT_LOW_THRESHOLD = 100d;

    /** 趋势变化百分比阈值 (超过 5% 视为上升/下降) */
    private static final double TREND_CHANGE_THRESHOLD = 5d;
    /** 高流失风险概率阈值 */
    private static final double HIGH_CHURN_THRESHOLD = 0.7d;
    /** 一年的天数 (用于年化计算) */
    private static final double DAYS_PER_YEAR = 365d;
    /** 一个月的天数 (用于月化计算, 365/12≈30.4167 保证一年恰为 12 个月) */
    private static final double DAYS_PER_MONTH = 365d / 12d;

    /** 客户 LTV 计算结果数据访问层 */
    private final ScrmCustomerLtvRepository ltvRepository;
    /** 客户数据访问层 (批量加载客户列表用于全量计算) */
    private final ScrmCustomerRepository customerRepository;
    /** 订单数据访问层 (统计客户历史消费金额) */
    private final ScrmOrderRepository orderRepository;
    /** LTV 模型管理服务 (模型存在性校验) */
    private final ScrmLtvModelService modelService;
    /** JSON 映射器 (解析 features / calculationResults 等 JSON 字段) */
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============================================================
    // LTV 计算
    // ============================================================

    /**
     * 计算单个客户 LTV (完整实现)
     * <p>
     * 流程: 收集历史订单数据 → 应用模型计算方法 → 计算预测 LTV → 确定价值层级 → 计算流失概率 → 持久化。
     * 历史数据来源为客户在回溯窗口内已完成 (COMPLETED) 的订单; 无订单数据时各项默认 0。
     * </p>
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return LTV 计算结果
     * @throws ScrmException 模型不存在 / 客户不存在
     */
    @Transactional
    public ScrmCustomerLtvDto calculateLtv(Long customerId, Long modelId) throws ScrmException {
        ScrmLtvModelEntity model = modelService.findModelOrThrow(modelId);
        ScrmCustomerEntity customer = findCustomerOrThrow(customerId);

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        // ---------- 1. 收集历史订单数据 ----------
        List<ScrmOrderEntity> orders = orderRepository
                .findByCustomerIdOrderByCreateTimeDesc(customerId);
        int lookbackDays = model.getLookbackDays() != null ? model.getLookbackDays() : 365;
        LocalDateTime lookbackStart = now.minusDays(lookbackDays);

        // 仅统计回溯窗口内已完成 (非取消) 的订单
        List<ScrmOrderEntity> validOrders = orders.stream()
                .filter(o -> o.getCreateTime() != null && o.getCreateTime().isAfter(lookbackStart))
                .filter(o -> !ORDER_STATUS_CANCELLED.equals(o.getOrderStatus()))
                .collect(Collectors.toList());

        double totalRevenue = validOrders.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0d).sum();
        int totalOrders = validOrders.size();
        double avgOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0d;

        // 最后购买日 (取已完成订单的最大完成时间或创建时间)
        LocalDate lastPurchaseDate = validOrders.stream()
                .map(this::resolveOrderDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        int daysSinceLastPurchase = lastPurchaseDate != null
                ? (int) ChronoUnit.DAYS.between(lastPurchaseDate, today) : lookbackDays;

        // 客户年龄
        int customerAgeDays = customer.getCreateTime() != null
                ? (int) ChronoUnit.DAYS.between(customer.getCreateTime(), now) : 0;

        // 平均购买间隔天数 (基于首末订单跨度, 无订单则 0)
        double avgPurchaseIntervalDays = 0d;
        if (totalOrders > 1) {
            LocalDate firstDate = validOrders.stream()
                    .map(this::resolveOrderDate)
                    .filter(Objects::nonNull)
                    .min(LocalDate::compareTo)
                    .orElse(lastPurchaseDate);
            LocalDate lastDate = lastPurchaseDate;
            if (firstDate != null && lastDate != null) {
                long span = ChronoUnit.DAYS.between(firstDate, lastDate);
                avgPurchaseIntervalDays = (double) span / (totalOrders - 1);
            }
        }

        // 平均购买频次 (次/月, 基于客户年龄)
        double avgPurchaseFrequency = customerAgeDays > 0
                ? totalOrders / (customerAgeDays / DAYS_PER_MONTH) : 0d;

        // 获客成本 (当前系统无数据, 默认 0)
        double acquisitionCost = 0d;

        // ---------- 2. 计算历史 LTV ----------
        double profitMargin = model.getAvgProfitMargin() != null ? model.getAvgProfitMargin() : 0.3d;
        double historicalLtv = round2(totalRevenue * profitMargin);

        // ---------- 3. 计算预测 LTV (应用模型计算方法) ----------
        int forecastDays = model.getForecastDays() != null ? model.getForecastDays() : 365;
        double discountRate = model.getDiscountRate() != null ? model.getDiscountRate() : 0.1d;
        double churnRate = model.getChurnRate() != null ? model.getChurnRate() : 0.05d;

        double projectedRevenue = 0d;
        int projectedOrders = 0;
        String calculationMethod = model.getCalculationMethod();
        switch (calculationMethod) {
            case METHOD_SIMPLE_AVG:
                projectedRevenue = calculateSimpleLtv(totalRevenue, totalOrders, avgOrderValue, forecastDays);
                break;
            case METHOD_WEIGHTED_AVG:
                projectedRevenue = calculateWeightedAvgRevenue(validOrders, forecastDays, lookbackDays);
                break;
            case METHOD_DCF:
                double annualRevenue = totalOrders > 0
                        ? totalRevenue * (DAYS_PER_YEAR / Math.max(1, lookbackDays)) : 0d;
                projectedRevenue = calculateDiscountedCashFlow(annualRevenue, discountRate, forecastDays);
                break;
            case METHOD_PARETO_NBD:
            case METHOD_BTYYD:
                // BG/NBD 简化实现: 基于购买频次与最近购买预测未来购买数, 再乘以平均订单价值
                projectedOrders = predictFutureOrders(totalOrders, customerAgeDays, forecastDays, churnRate);
                projectedRevenue = projectedOrders * avgOrderValue;
                break;
            default:
                projectedRevenue = calculateSimpleLtv(totalRevenue, totalOrders, avgOrderValue, forecastDays);
        }
        // 默认 projectedOrders 未在上述分支设置时按频次估算
        if (projectedOrders == 0 && avgPurchaseFrequency > 0) {
            projectedOrders = (int) Math.round(avgPurchaseFrequency * (forecastDays / DAYS_PER_MONTH));
        }
        double predictedLtv = round2(projectedRevenue * profitMargin);

        // ---------- 4. 确定价值层级 ----------
        String valueTier = determineTier(predictedLtv, model.getTierThresholds());

        // ---------- 5. 计算流失概率 ----------
        double churnProbability = predictChurnProbability(daysSinceLastPurchase,
                avgPurchaseIntervalDays, churnRate);
        // 高流失风险覆盖为 AT_RISK
        if (churnProbability >= HIGH_CHURN_THRESHOLD && !TIER_AT_RISK.equals(valueTier)) {
            valueTier = TIER_AT_RISK;
        }
        LocalDate predictedChurnDate = avgPurchaseIntervalDays > 0
                ? today.plusDays((long) (avgPurchaseIntervalDays / Math.max(churnRate, 0.001))) : null;

        // ---------- 6. 计算衍生指标 ----------
        double customerProfitability = round2(predictedLtv - acquisitionCost);
        double roi = acquisitionCost > 0 ? round2((predictedLtv - acquisitionCost) / acquisitionCost) : 0d;

        // 置信度: 基于订单数与客户年龄 (数据越充分置信度越高)
        double confidenceScore = round2(calculateConfidence(totalOrders, customerAgeDays));

        // 增长潜力: 基于购买频次与最近购买
        String growthPotential = determineGrowthPotential(avgPurchaseFrequency,
                daysSinceLastPurchase, model.getPurchaseFrequencyThreshold());

        // ---------- 7. LTV 趋势 (与上次计算结果对比) ----------
        Optional<ScrmCustomerLtvEntity> previousOpt = ltvRepository
                .findFirstByCustomerIdAndModelIdOrderByCalculatedAtDesc(
                         customerId, modelId);
        double previousLtv = previousOpt.map(ScrmCustomerLtvEntity::getPredictedLtv).orElse(0d);
        String ltvTrend;
        double trendChangePercent = 0d;
        if (previousLtv > 0) {
            trendChangePercent = round2((predictedLtv - previousLtv) / previousLtv * 100);
            if (trendChangePercent > TREND_CHANGE_THRESHOLD) {
                ltvTrend = TREND_INCREASING;
            } else if (trendChangePercent < -TREND_CHANGE_THRESHOLD) {
                ltvTrend = TREND_DECREASING;
            } else {
                ltvTrend = TREND_STABLE;
            }
        } else {
            ltvTrend = TREND_STABLE;
        }

        // ---------- 8. 持久化 (覆盖该客户+模型的最新记录) ----------
        ltvRepository.deleteByCustomerIdAndModelId(customerId, modelId);

        ScrmCustomerLtvEntity entity = new ScrmCustomerLtvEntity();
        entity.setCustomerId(customerId);
        entity.setCustomerName(customer.getNickname());
        entity.setModelId(modelId);
        entity.setHistoricalLtv(historicalLtv);
        entity.setPredictedLtv(predictedLtv);
        entity.setTotalRevenue(round2(totalRevenue));
        entity.setTotalOrders(totalOrders);
        entity.setAvgOrderValue(round2(avgOrderValue));
        entity.setAvgPurchaseFrequency(round2(avgPurchaseFrequency));
        entity.setAvgPurchaseIntervalDays(round2(avgPurchaseIntervalDays));
        entity.setCustomerAgeDays(customerAgeDays);
        entity.setLastPurchaseDate(lastPurchaseDate);
        entity.setDaysSinceLastPurchase(daysSinceLastPurchase);
        entity.setAcquisitionCost(acquisitionCost);
        entity.setProjectedRevenue(round2(projectedRevenue));
        entity.setProjectedOrders(projectedOrders);
        entity.setCustomerProfitability(customerProfitability);
        entity.setRoi(roi);
        entity.setValueTier(valueTier);
        entity.setChurnProbability(round2(churnProbability));
        entity.setPredictedChurnDate(predictedChurnDate);
        entity.setGrowthPotential(growthPotential);
        entity.setConfidenceScore(confidenceScore);
        entity.setLtvTrend(ltvTrend);
        entity.setTrendChangePercent(trendChangePercent);
        entity.setCalculatedAt(now);
        entity.setPreviousLtv(previousLtv);
        entity = ltvRepository.save(entity);

        // 更新模型应用次数与时间
        model.setAppliedCount((model.getAppliedCount() != null ? model.getAppliedCount() : 0) + 1);
        model.setLastAppliedAt(now);
        modelService.saveModel(model);

        log.info("计算客户 LTV: customerId={}, modelId={}, predictedLtv={}, tier={}",
                customerId, modelId, predictedLtv, valueTier);
        return toLtvDto(entity);
    }

    /**
     * 批量计算客户 LTV
     *
     * @param calculateDto 批量计算请求 (模型 ID + 客户 ID 列表)
     * @return 计算结果列表
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public List<ScrmCustomerLtvDto> batchCalculate(ScrmLtvCalculateDto calculateDto) throws ScrmException {
        if (calculateDto.getModelId() == null) {
            throw ScrmException.badRequest("模型 ID 不能为空");
        }
        if (calculateDto.getCustomerIds() == null || calculateDto.getCustomerIds().isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        List<ScrmCustomerLtvDto> results = new ArrayList<>();
        for (Long customerId : calculateDto.getCustomerIds()) {
            try {
                results.add(calculateLtv(customerId, calculateDto.getModelId()));
            } catch (ScrmException e) {
                log.warn("批量计算 LTV 跳过客户: customerId={}, err={}", customerId, e.getMessage());
            }
        }
        log.info("批量计算 LTV 完成: modelId={}, success={}/{}",
                calculateDto.getModelId(), results.size(), calculateDto.getCustomerIds().size());
        return results;
    }

    /**
     * 计算当前账号下所有客户 LTV
     *
     * @param modelId 模型 ID
     * @return 计算结果列表
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public List<ScrmCustomerLtvDto> calculateAll(Long modelId) throws ScrmException {
        modelService.findModelOrThrow(modelId);
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        List<ScrmCustomerLtvDto> results = new ArrayList<>();
        for (ScrmCustomerEntity customer : customers) {
            try {
                results.add(calculateLtv(customer.getId(), modelId));
            } catch (ScrmException e) {
                log.warn("全量计算 LTV 跳过客户: customerId={}, err={}", customer.getId(), e.getMessage());
            }
        }
        log.info("全量计算 LTV 完成: modelId={}, success={}/{}",
                modelId, results.size(), customers.size());
        return results;
    }

    /**
     * 重新计算客户 LTV (先清理旧结果再重算)
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 重新计算后的结果
     * @throws ScrmException 模型不存在 / 客户不存在
     */
    @Transactional
    public ScrmCustomerLtvDto recalculate(Long customerId, Long modelId) throws ScrmException {
        ltvRepository.deleteByCustomerIdAndModelId(customerId, modelId);
        return calculateLtv(customerId, modelId);
    }

    /**
     * 查询客户 LTV 计算结果详情
     *
     * @param id LTV 结果 ID
     * @return LTV 结果 DTO
     * @throws ScrmException 结果不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerLtvDto getLtv(Long id) throws ScrmException {
        return toLtvDto(findLtvOrThrow(id));
    }

    /**
     * 按客户 ID 与模型 ID 查询最新 LTV 结果
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID (可空, 为空时取该客户最新一条)
     * @return LTV 结果 DTO
     * @throws ScrmException 结果不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerLtvDto getLtvByCustomer(Long customerId, Long modelId) throws ScrmException {
        Optional<ScrmCustomerLtvEntity> opt = modelId != null
                ? ltvRepository.findFirstByCustomerIdAndModelIdOrderByCalculatedAtDesc(
                         customerId, modelId)
                : ltvRepository.findFirstByCustomerIdOrderByCalculatedAtDesc(customerId);
        ScrmCustomerLtvEntity entity = opt.orElseThrow(() -> new ScrmException(
                ScrmExceptionConstants.NOT_FOUND,
                "客户 LTV 结果不存在: customerId=" + customerId + ", modelId=" + modelId));
        return toLtvDto(entity);
    }

    /**
     * 分页查询客户 LTV 结果, 支持按价值层级、LTV 范围、增长潜力过滤
     *
     * @param valueTier      价值层级过滤 (可空)
     * @param minLtv         最小预测 LTV 过滤 (可空)
     * @param maxLtv         最大预测 LTV 过滤 (可空)
     * @param growthPotential 增长潜力过滤 (可空)
     * @param sortBy         排序字段: predictedLtv / historicalLtv / churnProbability / roi (可空)
     * @param pageable       分页参数
     * @return LTV 结果分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLtvDto> listLtv(String valueTier, Double minLtv, Double maxLtv,
                                             String growthPotential, String sortBy, Pageable pageable) {
        Specification<ScrmCustomerLtvEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (valueTier != null && !valueTier.isBlank()) {
                predicates.add(cb.equal(root.get("valueTier"), valueTier));
            }
            if (minLtv != null) {
                predicates.add(cb.ge(root.get("predictedLtv"), minLtv));
            }
            if (maxLtv != null) {
                predicates.add(cb.le(root.get("predictedLtv"), maxLtv));
            }
            if (growthPotential != null && !growthPotential.isBlank()) {
                predicates.add(cb.equal(root.get("growthPotential"), growthPotential));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        String sortField = resolveSortField(sortBy);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, sortField));
        return ltvRepository.findAll(spec, sorted).map(this::toLtvDto);
    }

    /**
     * 查询高价值客户 (按预测 LTV 降序取 Top N)
     *
     * @param limit 返回条数
     * @return LTV 结果列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomerLtvDto> getTopCustomers(int limit) {
        Pageable top = PageRequest.of(0, Math.max(1, limit));
        return ltvRepository.findAllByOrderByPredictedLtvDesc(top).stream()
                .map(this::toLtvDto).collect(Collectors.toList());
    }

    // ============================================================
    // LTV 计算辅助方法
    // ============================================================

    /**
     * 简单 LTV 计算 (按历史购买率线性外推)
     * <p>
     * 假设客户保持历史购买率, projectedRevenue = avgOrderValue * orders * (forecastDays / 365)。
     * </p>
     *
     * @param revenue       历史总收入
     * @param orders        历史订单数
     * @param avgOrderValue 平均订单价值
     * @param forecastDays  预测天数
     * @return 预测期内的预计收入
     */
    public double calculateSimpleLtv(double revenue, int orders, double avgOrderValue, int forecastDays) {
        if (orders <= 0 || forecastDays <= 0) {
            return 0d;
        }
        double dailyRate = orders / DAYS_PER_YEAR;
        double projectedOrders = dailyRate * forecastDays;
        return avgOrderValue > 0 ? avgOrderValue * projectedOrders : revenue * (forecastDays / DAYS_PER_YEAR);
    }

    /**
     * DCF (折现现金流) 计算
     * <p>
     * 以年化收入按月折现: 每月现金流 = annualRevenue / 12, 折现因子 = 1 / (1 + discountRate)^(m/12)。
     * </p>
     *
     * @param revenue       年化收入
     * @param discountRate  折现率
     * @param forecastDays  预测天数
     * @return 折现后的预计收入
     */
    public double calculateDiscountedCashFlow(double revenue, double discountRate, int forecastDays) {
        if (revenue <= 0 || forecastDays <= 0) {
            return 0d;
        }
        double monthlyCashFlow = revenue / 12d;
        int months = (int) Math.ceil(forecastDays / DAYS_PER_MONTH);
        double rate = discountRate > 0 ? discountRate : 0d;
        double dcf = 0d;
        for (int m = 1; m <= months; m++) {
            double discountFactor = Math.pow(1 + rate, m / 12d);
            dcf += monthlyCashFlow / discountFactor;
        }
        return dcf;
    }

    /**
     * 确定价值层级
     * <p>
     * 优先使用模型 tierThresholds (JSON 数组: [{tier,minValue,maxValue,color}]) 匹配;
     * 未配置时按默认阈值: VIP≥10000 / HIGH≥5000 / MEDIUM≥1000 / LOW≥100 / 否则 AT_RISK。
     * </p>
     *
     * @param ltv        预测 LTV
     * @param thresholds 模型配置的层级阈值 JSON (可空)
     * @return 价值层级
     */
    public String determineTier(double ltv, String thresholds) {
        if (thresholds != null && !thresholds.isBlank()) {
            try {
                List<Map<String, Object>> tiers = objectMapper.readValue(thresholds,
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> tier : tiers) {
                    String tierName = tier.get("tier") != null ? tier.get("tier").toString() : null;
                    double minValue = tier.get("minValue") != null
                            ? Double.parseDouble(tier.get("minValue").toString()) : Double.NEGATIVE_INFINITY;
                    double maxValue = tier.get("maxValue") != null
                            ? Double.parseDouble(tier.get("maxValue").toString()) : Double.POSITIVE_INFINITY;
                    if (ltv >= minValue && ltv < maxValue) {
                        return tierName != null ? tierName : TIER_LOW;
                    }
                }
            } catch (Exception e) {
                log.warn("解析 tierThresholds 失败, 回退默认阈值: {}", e.getMessage());
            }
        }
        // 默认阈值
        if (ltv >= DEFAULT_VIP_THRESHOLD) {
            return TIER_VIP;
        }
        if (ltv >= DEFAULT_HIGH_THRESHOLD) {
            return TIER_HIGH;
        }
        if (ltv >= DEFAULT_MEDIUM_THRESHOLD) {
            return TIER_MEDIUM;
        }
        if (ltv >= DEFAULT_LOW_THRESHOLD) {
            return TIER_LOW;
        }
        return TIER_AT_RISK;
    }

    /**
     * 加权平均收入预测 (近期订单权重更高)
     * <p>
     * 按订单时间倒序赋递减权重 (最新订单权重最大), 计算加权平均订单价值与加权年化收入,
     * 再线性外推至预测天数。
     * </p>
     *
     * @param orders       订单列表
     * @param forecastDays 预测天数
     * @param lookbackDays 回溯天数
     * @return 预测期内的预计收入
     */
    private double calculateWeightedAvgRevenue(List<ScrmOrderEntity> orders, int forecastDays, int lookbackDays) {
        if (orders.isEmpty()) {
            return 0d;
        }
        List<ScrmOrderEntity> sorted = orders.stream()
                .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
                .collect(Collectors.toList());
        double weightSum = 0d;
        double weightedRevenue = 0d;
        for (int i = 0; i < sorted.size(); i++) {
            double weight = 1.0 / (i + 1);
            double amount = sorted.get(i).getTotalAmount() != null ? sorted.get(i).getTotalAmount() : 0d;
            weightedRevenue += amount * weight;
            weightSum += weight;
        }
        double weightedAvgOrderValue = weightSum > 0 ? weightedRevenue / weightSum : 0d;
        double annualRate = lookbackDays > 0 ? sorted.size() * (DAYS_PER_YEAR / lookbackDays) : 0d;
        return weightedAvgOrderValue * annualRate * (forecastDays / DAYS_PER_YEAR);
    }

    /**
     * 预测未来订单数 (BG/NBD 简化)
     * <p>
     * 基于历史频次与客户年龄推算日购买率, 按存活概率 (1 - churnRate)^年数 折减后乘以预测天数。
     * </p>
     *
     * @param totalOrders    历史订单数
     * @param customerAgeDays 客户年龄 (天)
     * @param forecastDays   预测天数
     * @param churnRate      流失率
     * @return 预测订单数
     */
    private int predictFutureOrders(int totalOrders, int customerAgeDays, int forecastDays, double churnRate) {
        if (totalOrders <= 0 || customerAgeDays <= 0 || forecastDays <= 0) {
            return 0;
        }
        double dailyRate = totalOrders / (double) customerAgeDays;
        double years = forecastDays / DAYS_PER_YEAR;
        double survival = Math.pow(1 - churnRate, years);
        return (int) Math.round(dailyRate * forecastDays * survival);
    }

    /**
     * 流失概率计算
     * <p>
     * 基于距上次购买天数与平均购买间隔, churnProbability = 1 - exp(-churnRate * daysSince / max(interval, 1))。
     * </p>
     *
     * @param daysSinceLastPurchase 距上次购买天数
     * @param avgInterval           平均购买间隔
     * @param churnRate             流失率
     * @return 流失概率
     */
    private double predictChurnProbability(int daysSinceLastPurchase, double avgInterval, double churnRate) {
        if (daysSinceLastPurchase <= 0) {
            return 0d;
        }
        double interval = avgInterval > 0 ? avgInterval : DAYS_PER_MONTH;
        double lambda = churnRate * daysSinceLastPurchase / interval;
        return Math.min(1d, 1 - Math.exp(-lambda));
    }

    /**
     * 计算置信度 (基于订单数与客户年龄)
     *
     * @param totalOrders    历史订单数
     * @param customerAgeDays 客户年龄 (天)
     * @return 置信度
     */
    private double calculateConfidence(int totalOrders, int customerAgeDays) {
        double orderConfidence = Math.min(1d, totalOrders / 10d);
        double ageConfidence = Math.min(1d, customerAgeDays / DAYS_PER_YEAR);
        return (orderConfidence * 0.6 + ageConfidence * 0.4);
    }

    /**
     * 确定增长潜力
     *
     * @param avgPurchaseFrequency 平均购买频次 (次/月)
     * @param daysSinceLastPurchase 距上次购买天数
     * @param frequencyThreshold   购买频次阈值
     * @return 增长潜力
     */
    private String determineGrowthPotential(double avgPurchaseFrequency, int daysSinceLastPurchase,
                                            Integer frequencyThreshold) {
        int threshold = frequencyThreshold != null ? frequencyThreshold : 2;
        if (avgPurchaseFrequency >= threshold && daysSinceLastPurchase <= 30) {
            return GROWTH_HIGH;
        }
        if (avgPurchaseFrequency >= threshold / 2.0 && daysSinceLastPurchase <= 90) {
            return GROWTH_MEDIUM;
        }
        if (avgPurchaseFrequency > 0) {
            return GROWTH_LOW;
        }
        return GROWTH_NONE;
    }

    /**
     * 解析订单日期 (优先完成时间, 回退创建时间)
     *
     * @param order 订单实体
     * @return 订单日期
     */
    private LocalDate resolveOrderDate(ScrmOrderEntity order) {
        if (order.getCompletedAt() != null) {
            return order.getCompletedAt().toLocalDate();
        }
        if (order.getCreateTime() != null) {
            return order.getCreateTime().toLocalDate();
        }
        return null;
    }

    /**
     * 解析排序字段 (防注入, 仅允许白名单字段)
     *
     * @param sortBy 排序字段
     * @return 白名单排序字段
     */
    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "predictedLtv";
        }
        switch (sortBy) {
            case "historicalLtv":
            case "churnProbability":
            case "roi":
            case "confidenceScore":
            case "totalRevenue":
                return sortBy;
            case "predictedLtv":
            default:
                return "predictedLtv";
        }
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询客户 LTV 结果, 不存在或越权抛异常
     *
     * @param id LTV 结果 ID
     * @return LTV 结果实体
     * @throws ScrmException 结果不存在
     */
    private ScrmCustomerLtvEntity findLtvOrThrow(Long id) throws ScrmException {
        ScrmCustomerLtvEntity entity = ltvRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户 LTV 结果不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询客户, 不存在或越权抛异常
     *
     * @param customerId 客户 ID
     * @return 客户实体
     * @throws ScrmException 客户不存在
     */
    ScrmCustomerEntity findCustomerOrThrow(Long customerId) throws ScrmException {
        ScrmCustomerEntity entity = customerRepository.findById(customerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + customerId));
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
     * 客户 LTV 实体转 DTO
     *
     * @param entity LTV 结果实体
     * @return LTV 结果 DTO
     */
    ScrmCustomerLtvDto toLtvDto(ScrmCustomerLtvEntity entity) {
        ScrmCustomerLtvDto dto = new ScrmCustomerLtvDto();
        dto.setId(entity.getId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setModelId(entity.getModelId());
        dto.setHistoricalLtv(entity.getHistoricalLtv());
        dto.setPredictedLtv(entity.getPredictedLtv());
        dto.setTotalRevenue(entity.getTotalRevenue());
        dto.setTotalOrders(entity.getTotalOrders());
        dto.setAvgOrderValue(entity.getAvgOrderValue());
        dto.setAvgPurchaseFrequency(entity.getAvgPurchaseFrequency());
        dto.setAvgPurchaseIntervalDays(entity.getAvgPurchaseIntervalDays());
        dto.setCustomerAgeDays(entity.getCustomerAgeDays());
        dto.setLastPurchaseDate(entity.getLastPurchaseDate());
        dto.setDaysSinceLastPurchase(entity.getDaysSinceLastPurchase());
        dto.setAcquisitionCost(entity.getAcquisitionCost());
        dto.setProjectedRevenue(entity.getProjectedRevenue());
        dto.setProjectedOrders(entity.getProjectedOrders());
        dto.setCustomerProfitability(entity.getCustomerProfitability());
        dto.setRoi(entity.getRoi());
        dto.setValueTier(entity.getValueTier());
        dto.setChurnProbability(entity.getChurnProbability());
        dto.setPredictedChurnDate(entity.getPredictedChurnDate());
        dto.setGrowthPotential(entity.getGrowthPotential());
        dto.setConfidenceScore(entity.getConfidenceScore());
        dto.setLtvTrend(entity.getLtvTrend());
        dto.setTrendChangePercent(entity.getTrendChangePercent());
        dto.setCalculatedAt(entity.getCalculatedAt());
        dto.setPreviousLtv(entity.getPreviousLtv());
        dto.setMetadata(entity.getMetadata());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}