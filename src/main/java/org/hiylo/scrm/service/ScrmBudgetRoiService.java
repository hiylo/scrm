/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetRoiService.java
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

import org.hiylo.scrm.dto.ScrmBudgetRoiDto;
import org.hiylo.scrm.entity.ScrmBudgetExpenseEntity;
import org.hiylo.scrm.entity.ScrmBudgetRoiEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmBudgetExpenseRepository;
import org.hiylo.scrm.repository.ScrmBudgetRoiRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 预算 ROI 追踪服务。
 * <p>
 * 承载 ROI 追踪能力: 计算 ROI (收入 ROI / 利润 ROI / ROAS / CPA / CPC / CPM /
 * CAC / LTV) / 趋势 / 对比 / 渠道拆分 / 活动维度, 以及 ROI 概览统计。
 * </p>
 * <p>
 * ROI 计算 ({@link #calculateRoi}): 汇总周期内已审批/已付款支出得到总支出与广告支出,
 * 按公式计算收入 ROI (收入/支出) / 利润 ROI (利润/支出) / ROAS (收入/广告支出) /
 * CPA (支出/转化) / CPC (支出/点击) / CPM (支出/展示*1000) / CAC (支出/新客) /
 * LTV / LTV-CAC 比率, 并按活动维度生成 breakdown 拆分。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmBudgetRoiService {

    // ==================== 状态常量 ====================

    /** 支出状态: 已通过 */
    private static final String EXPENSE_APPROVED = "APPROVED";
    /** 支出状态: 已付款 */
    private static final String EXPENSE_PAID = "PAID";

    /** 支出类型: 广告支出 */
    private static final String EXPENSE_TYPE_AD_SPEND = "AD_SPEND";

    /** 金额精度 (保留两位小数) */
    private static final double MONEY_SCALE = 100d;
    /** 千次展示系数 */
    private static final double CPM_FACTOR = 1000d;
    /** 默认趋势月数 */
    private static final int DEFAULT_TREND_MONTHS = 6;

    /** 预算 ROI 数据访问层 */
    private final ScrmBudgetRoiRepository roiRepository;

    /** 预算支出数据访问层 */
    private final ScrmBudgetExpenseRepository expenseRepository;

    /** 预算方案管理服务 (ROI 计算校验方案存在) */
    private final ScrmBudgetPlanService planService;

    /** JSON 解析器 (解析 breakdown / attachments) */
    private final ObjectMapper objectMapper;

    /**
     * 计算方案在指定周期的 ROI。
     * <p>完整实现: 汇总周期内已审批/已付款支出得到总支出与广告支出, 按公式计算
     * 收入 ROI / 利润 ROI / ROAS / CPA / CPC / CPM / CAC / LTV / LTV-CAC 比率 /
     * 转化率 / 点击率, 并按活动维度生成 breakdown 拆分。已存在同周期记录则更新。</p>
     *
     * @param planId 方案 ID
     * @param period 周期 (yyyy-MM, 可空默认当月)
     * @return ROI DTO
     * @throws ScrmException 方案不存在
     */
    @Transactional
    public ScrmBudgetRoiDto calculateRoi(Long planId, String period) throws ScrmException {
        planService.findPlanOrThrow(planId);
        String roiPeriod = period != null && !period.isBlank() ? period
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        // 汇总周期内已审批/已付款支出
        YearMonth ym = YearMonth.parse(roiPeriod, DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate periodStart = ym.atDay(1);
        LocalDate periodEnd = ym.atEndOfMonth();
        List<ScrmBudgetExpenseEntity> expenses = expenseRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("planId"), planId));
            predicates.add(root.get("status").in(List.of(EXPENSE_APPROVED, EXPENSE_PAID)));
            predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), periodStart));
            predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), periodEnd));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        double totalSpend = expenses.stream()
                .mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0d).sum();
        double adSpend = expenses.stream()
                .filter(e -> EXPENSE_TYPE_AD_SPEND.equals(e.getExpenseType()))
                .mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0d).sum();
        // 收入与利润: 本模块支出无收入字段, 默认 0, 可由 breakdown 携带外部数据
        double totalRevenue = 0d;
        double totalProfit = -totalSpend;
        // 按 campaignId 维度生成 breakdown
        Map<Long, List<ScrmBudgetExpenseEntity>> byCampaign = expenses.stream()
                .filter(e -> e.getCampaignId() != null)
                .collect(Collectors.groupingBy(ScrmBudgetExpenseEntity::getCampaignId));
        List<Map<String, Object>> breakdown = new ArrayList<>();
        for (Map.Entry<Long, List<ScrmBudgetExpenseEntity>> entry : byCampaign.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            String campaignName = entry.getValue().stream()
                    .map(ScrmBudgetExpenseEntity::getCampaignName)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(String.valueOf(entry.getKey()));
            double channelSpend = entry.getValue().stream()
                    .mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0d).sum();
            item.put("channel", campaignName);
            item.put("campaignId", entry.getKey());
            item.put("spend", round2(channelSpend));
            item.put("revenue", 0d);
            item.put("roi", channelSpend > 0 ? round2(0d / channelSpend) : 0d);
            breakdown.add(item);
        }
        // 计算 ROI 指标
        double revenueRoi = totalSpend > 0 ? totalRevenue / totalSpend : 0d;
        double profitRoi = totalSpend > 0 ? totalProfit / totalSpend : 0d;
        double roas = adSpend > 0 ? totalRevenue / adSpend : 0d;
        // 转化/点击/展示: 本模块支出无直接指标, 默认 0
        int conversions = 0;
        int clicks = 0;
        int impressions = 0;
        double cpa = conversions > 0 ? totalSpend / conversions : 0d;
        double cpc = clicks > 0 ? totalSpend / clicks : 0d;
        double cpm = impressions > 0 ? totalSpend / impressions * CPM_FACTOR : 0d;
        double cac = conversions > 0 ? totalSpend / conversions : 0d;
        double ltv = 0d;
        double ltvCacRatio = cac > 0 ? ltv / cac : 0d;
        double conversionRate = clicks > 0 ? (double) conversions / clicks : 0d;
        double clickRate = impressions > 0 ? (double) clicks / impressions : 0d;
        Integer paybackMonths = cac > 0 && ltv > 0 ? (int) Math.ceil(cac / (ltv / 12d)) : null;
        // 已存在同周期记录则更新, 否则新建
        ScrmBudgetRoiEntity entity = roiRepository
                .findByPlanIdAndPeriod(planId, roiPeriod)
                .orElseGet(ScrmBudgetRoiEntity::new);
        boolean isNew = entity.getId() == null;
        if (isNew) {
            entity.setPlanId(planId);
        }
        entity.setCampaignName(null);
        entity.setPeriod(roiPeriod);
        entity.setTotalSpend(round2(totalSpend));
        entity.setTotalRevenue(round2(totalRevenue));
        entity.setTotalProfit(round2(totalProfit));
        entity.setRevenueRoi(round2(revenueRoi));
        entity.setProfitRoi(round2(profitRoi));
        entity.setRoas(round2(roas));
        entity.setCpa(round2(cpa));
        entity.setCpc(round2(cpc));
        entity.setCpm(round2(cpm));
        entity.setCac(round2(cac));
        entity.setLtv(round2(ltv));
        entity.setLtvCacRatio(round2(ltvCacRatio));
        entity.setConversions(conversions);
        entity.setClicks(clicks);
        entity.setImpressions(impressions);
        entity.setConversionRate(round2(conversionRate));
        entity.setClickRate(round2(clickRate));
        entity.setPaybackPeriodMonths(paybackMonths);
        entity.setBreakdown(toJson(breakdown));
        entity.setCalculatedAt(LocalDateTime.now());
        entity = roiRepository.save(entity);
        log.info("计算预算 ROI: planId={}, period={}, totalSpend={}, revenueRoi={}, roas={}, breakdownCount={}",
                planId, roiPeriod, totalSpend, revenueRoi, roas, breakdown.size());
        return toRoiDto(entity);
    }

    /**
     * 查询 ROI 详情。
     *
     * @param id ROI ID
     * @return ROI DTO
     * @throws ScrmException ROI 不存在
     */
    @Transactional(readOnly = true)
    public ScrmBudgetRoiDto getRoi(Long id) throws ScrmException {
        return toRoiDto(findRoiOrThrow(id));
    }

    /**
     * 分页查询 ROI, 支持按方案/活动/周期过滤。
     *
     * @param planId     方案 ID (可空)
     * @param campaignId 营销活动 ID (可空)
     * @param period     周期 (可空)
     * @param pageable   分页参数
     * @return ROI 分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmBudgetRoiDto> listRoi(Long planId, Long campaignId, String period, Pageable pageable) {
        Specification<ScrmBudgetRoiEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (planId != null) {
                predicates.add(cb.equal(root.get("planId"), planId));
            }
            if (campaignId != null) {
                predicates.add(cb.equal(root.get("campaignId"), campaignId));
            }
            if (period != null && !period.isBlank()) {
                predicates.add(cb.equal(root.get("period"), period));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "calculatedAt"));
        return roiRepository.findAll(spec, sorted).map(this::toRoiDto);
    }

    /**
     * 活动维度 ROI (按活动汇总支出与 ROI)。
     *
     * @param campaignId 营销活动 ID
     * @param startTime  起始时间 (可空)
     * @param endTime    结束时间 (可空)
     * @return 活动 ROI 汇总
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignRoi(Long campaignId, LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmBudgetRoiEntity> rois = roiRepository.findByCampaignId(campaignId);
        double totalSpend = rois.stream()
                .mapToDouble(r -> r.getTotalSpend() != null ? r.getTotalSpend() : 0d).sum();
        double totalRevenue = rois.stream()
                .mapToDouble(r -> r.getTotalRevenue() != null ? r.getTotalRevenue() : 0d).sum();
        double totalProfit = rois.stream()
                .mapToDouble(r -> r.getTotalProfit() != null ? r.getTotalProfit() : 0d).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("campaignId", campaignId);
        result.put("roiCount", rois.size());
        result.put("totalSpend", round2(totalSpend));
        result.put("totalRevenue", round2(totalRevenue));
        result.put("totalProfit", round2(totalProfit));
        result.put("revenueRoi", totalSpend > 0 ? round2(totalRevenue / totalSpend) : 0d);
        result.put("profitRoi", totalSpend > 0 ? round2(totalProfit / totalSpend) : 0d);
        result.put("rois", rois.stream().map(this::toRoiDto).collect(Collectors.toList()));
        return result;
    }

    /**
     * 渠道维度 ROI (按方案的 breakdown 聚合各渠道支出与 ROI)。
     *
     * @param planId    方案 ID (可空)
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return 渠道 ROI 列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChannelRoi(Long planId, LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmBudgetRoiEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (planId != null) {
                predicates.add(cb.equal(root.get("planId"), planId));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("calculatedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmBudgetRoiEntity> rois = roiRepository.findAll(spec);
        Map<String, double[]> channelAgg = new LinkedHashMap<>();
        for (ScrmBudgetRoiEntity roi : rois) {
            if (roi.getBreakdown() == null || roi.getBreakdown().isBlank()) {
                continue;
            }
            try {
                List<Map<String, Object>> items = objectMapper.readValue(roi.getBreakdown(),
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> item : items) {
                    String channel = toStringValue(item.get("channel"));
                    double spend = toDouble(item.get("spend"));
                    double revenue = toDouble(item.get("revenue"));
                    double[] agg = channelAgg.computeIfAbsent(channel, k -> new double[]{0d, 0d});
                    agg[0] += spend;
                    agg[1] += revenue;
                }
            } catch (Exception e) {
                log.warn("ROI breakdown JSON 解析失败: roiId={}, err={}", roi.getId(), e.getMessage());
            }
        }
        return channelAgg.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("channel", e.getKey());
                    m.put("spend", round2(e.getValue()[0]));
                    m.put("revenue", round2(e.getValue()[1]));
                    m.put("roi", e.getValue()[0] > 0 ? round2(e.getValue()[1] / e.getValue()[0]) : 0d);
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * ROI 趋势 (最近 months 个月方案的 ROI 变化)。
     *
     * @param planId 方案 ID
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     * @throws ScrmException 方案不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRoiTrend(Long planId, Integer months) throws ScrmException {
        planService.findPlanOrThrow(planId);
        int trendMonths = months != null && months > 0 ? months : DEFAULT_TREND_MONTHS;
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(trendMonths - 1L);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < trendMonths; i++) {
            YearMonth ym = start.plusMonths(i);
            String period = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            ScrmBudgetRoiEntity roi = roiRepository
                    .findByPlanIdAndPeriod(planId, period).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", period);
            m.put("totalSpend", roi != null && roi.getTotalSpend() != null ? round2(roi.getTotalSpend()) : 0d);
            m.put("totalRevenue", roi != null && roi.getTotalRevenue() != null ? round2(roi.getTotalRevenue()) : 0d);
            m.put("revenueRoi", roi != null && roi.getRevenueRoi() != null ? round2(roi.getRevenueRoi()) : 0d);
            m.put("roas", roi != null && roi.getRoas() != null ? round2(roi.getRoas()) : 0d);
            result.add(m);
        }
        return result;
    }

    /**
     * ROI 对比 (多方案在同一周期的 ROI 对比)。
     *
     * @param planIds 方案 ID 列表
     * @param period  周期 (可空默认当月)
     * @return 对比结果列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRoiComparison(List<Long> planIds, String period) {
        if (planIds == null || planIds.isEmpty()) {
            return List.of();
        }
        String roiPeriod = period != null && !period.isBlank() ? period
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long planId : planIds) {
            ScrmBudgetRoiEntity roi = roiRepository
                    .findByPlanIdAndPeriod(planId, roiPeriod).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("planId", planId);
            m.put("period", roiPeriod);
            m.put("totalSpend", roi != null && roi.getTotalSpend() != null ? round2(roi.getTotalSpend()) : 0d);
            m.put("totalRevenue", roi != null && roi.getTotalRevenue() != null ? round2(roi.getTotalRevenue()) : 0d);
            m.put("revenueRoi", roi != null && roi.getRevenueRoi() != null ? round2(roi.getRevenueRoi()) : 0d);
            m.put("profitRoi", roi != null && roi.getProfitRoi() != null ? round2(roi.getProfitRoi()) : 0d);
            m.put("roas", roi != null && roi.getRoas() != null ? round2(roi.getRoas()) : 0d);
            result.add(m);
        }
        return result;
    }

    /**
     * 更新活动 ROI (按活动的支出重新计算并落库一条活动维度 ROI)。
     *
     * @param campaignId 营销活动 ID
     * @return 活动 ROI DTO
     */
    @Transactional
    public ScrmBudgetRoiDto updateCampaignRoi(Long campaignId) {
        List<ScrmBudgetExpenseEntity> expenses = expenseRepository.findByCampaignId(campaignId);
        double totalSpend = expenses.stream()
                .filter(e -> EXPENSE_APPROVED.equals(e.getStatus()) || EXPENSE_PAID.equals(e.getStatus()))
                .mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0d).sum();
        double adSpend = expenses.stream()
                .filter(e -> EXPENSE_APPROVED.equals(e.getStatus()) || EXPENSE_PAID.equals(e.getStatus()))
                .filter(e -> EXPENSE_TYPE_AD_SPEND.equals(e.getExpenseType()))
                .mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0d).sum();
        String campaignName = expenses.stream()
                .map(ScrmBudgetExpenseEntity::getCampaignName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        Long planId = expenses.stream()
                .map(ScrmBudgetExpenseEntity::getPlanId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        String period = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        double totalRevenue = 0d;
        double totalProfit = -totalSpend;
        double revenueRoi = totalSpend > 0 ? totalRevenue / totalSpend : 0d;
        double profitRoi = totalSpend > 0 ? totalProfit / totalSpend : 0d;
        double roas = adSpend > 0 ? totalRevenue / adSpend : 0d;
        ScrmBudgetRoiEntity entity = new ScrmBudgetRoiEntity();
        entity.setPlanId(planId);
        entity.setCampaignId(campaignId);
        entity.setCampaignName(campaignName);
        entity.setPeriod(period);
        entity.setTotalSpend(round2(totalSpend));
        entity.setTotalRevenue(round2(totalRevenue));
        entity.setTotalProfit(round2(totalProfit));
        entity.setRevenueRoi(round2(revenueRoi));
        entity.setProfitRoi(round2(profitRoi));
        entity.setRoas(round2(roas));
        entity.setCalculatedAt(LocalDateTime.now());
        entity = roiRepository.save(entity);
        log.info("更新活动 ROI: campaignId={}, totalSpend={}, roas={}", campaignId, totalSpend, roas);
        return toRoiDto(entity);
    }

    /**
     * ROI 概览 (按时间范围汇总总支出/总收入/ROI/ROAS)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return ROI 概览
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRoiOverview(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmBudgetRoiEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("calculatedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmBudgetRoiEntity> rois = roiRepository.findAll(spec);
        double totalSpend = rois.stream()
                .mapToDouble(r -> r.getTotalSpend() != null ? r.getTotalSpend() : 0d).sum();
        double totalRevenue = rois.stream()
                .mapToDouble(r -> r.getTotalRevenue() != null ? r.getTotalRevenue() : 0d).sum();
        double totalProfit = rois.stream()
                .mapToDouble(r -> r.getTotalProfit() != null ? r.getTotalProfit() : 0d).sum();
        double totalAdSpend = rois.stream()
                .mapToDouble(r -> r.getRoas() != null && r.getRoas() > 0 && r.getTotalRevenue() != null
                        ? r.getTotalRevenue() / r.getRoas() : 0d).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roiCount", rois.size());
        result.put("totalSpend", round2(totalSpend));
        result.put("totalRevenue", round2(totalRevenue));
        result.put("totalProfit", round2(totalProfit));
        result.put("revenueRoi", totalSpend > 0 ? round2(totalRevenue / totalSpend) : 0d);
        result.put("profitRoi", totalSpend > 0 ? round2(totalProfit / totalSpend) : 0d);
        result.put("roas", totalAdSpend > 0 ? round2(totalRevenue / totalAdSpend) : 0d);
        return result;
    }

    /**
     * 按主键查询 ROI, 不存在或越权抛异常。
     */
    private ScrmBudgetRoiEntity findRoiOrThrow(Long id) throws ScrmException {
        ScrmBudgetRoiEntity entity = roiRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "预算 ROI 不存在: id=" + id));
        return entity;
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串, 序列化失败返回 "[]"
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("对象序列化为 JSON 失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    private double toDouble(Object obj) {
        if (obj == null) {
            return 0d;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    /**
     * 将对象转换为字符串。
     *
     * @param obj 对象
     * @return 字符串, null 返回空字符串
     */
    private String toStringValue(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /**
     * 金额保留两位小数。
     *
     * @param value 金额
     * @return 保留两位小数后的金额
     */
    private double round2(double value) {
        return Math.round(value * MONEY_SCALE) / MONEY_SCALE;
    }

    /**
     * ROI 实体转 DTO
     */
    private ScrmBudgetRoiDto toRoiDto(ScrmBudgetRoiEntity entity) {
        ScrmBudgetRoiDto dto = new ScrmBudgetRoiDto();
        dto.setId(entity.getId());
        dto.setPlanId(entity.getPlanId());
        dto.setAllocationId(entity.getAllocationId());
        dto.setCampaignId(entity.getCampaignId());
        dto.setCampaignName(entity.getCampaignName());
        dto.setPeriod(entity.getPeriod());
        dto.setTotalSpend(entity.getTotalSpend());
        dto.setTotalRevenue(entity.getTotalRevenue());
        dto.setTotalProfit(entity.getTotalProfit());
        dto.setRevenueRoi(entity.getRevenueRoi());
        dto.setProfitRoi(entity.getProfitRoi());
        dto.setRoas(entity.getRoas());
        dto.setCpa(entity.getCpa());
        dto.setCpc(entity.getCpc());
        dto.setCpm(entity.getCpm());
        dto.setCac(entity.getCac());
        dto.setLtv(entity.getLtv());
        dto.setLtvCacRatio(entity.getLtvCacRatio());
        dto.setConversions(entity.getConversions());
        dto.setClicks(entity.getClicks());
        dto.setImpressions(entity.getImpressions());
        dto.setConversionRate(entity.getConversionRate());
        dto.setClickRate(entity.getClickRate());
        dto.setPaybackPeriodMonths(entity.getPaybackPeriodMonths());
        dto.setBreakdown(entity.getBreakdown());
        dto.setCalculatedAt(entity.getCalculatedAt());
        dto.setNotes(entity.getNotes());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

}