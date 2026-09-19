/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvForecastService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerLtvDto;
import org.hiylo.scrm.entity.ScrmCustomerLtvEntity;
import org.hiylo.scrm.entity.ScrmOrderEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerLtvRepository;
import org.hiylo.scrm.repository.ScrmOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 客户 LTV 预测与流失服务: 基于历史订单月度聚合做线性回归预测未来收入与 LTV、
 * 流失概率与流失日期预测、高流失风险客户查询。
 * <p>
 * 数据不足 (不足 2 个月) 时回退至简单平均法。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmLtvForecastService {

    /** 订单状态: 已取消 */
    private static final String ORDER_STATUS_CANCELLED = "CANCELLED";

    /** 高流失风险概率阈值 */
    private static final double HIGH_CHURN_THRESHOLD = 0.7d;
    /** 一个月的天数 (用于月化计算, 365/12≈30.4167 保证一年恰为 12 个月) */
    private static final double DAYS_PER_MONTH = 365d / 12d;

    /** 客户 LTV 计算结果数据访问层 */
    private final ScrmCustomerLtvRepository ltvRepository;
    /** 订单数据访问层 (统计客户历史消费金额) */
    private final ScrmOrderRepository orderRepository;
    /** 客户 LTV 计算服务 (客户存在性校验 / DTO 转换) */
    private final ScrmLtvCalculationService calculationService;

    // ============================================================
    // 预测
    // ============================================================

    /**
     * 预测客户未来 LTV (基于历史趋势线性回归)
     * <p>
     * 将客户历史订单按月聚合为月度收入序列, 对序列做一元线性回归 (最小二乘),
     * 据此外推未来 forecastDays/30 个月的月度收入, 汇总得预测收入与预测 LTV。
     * 数据不足 (不足 2 个月) 时回退至简单平均法。
     * </p>
     *
     * @param customerId   客户 ID
     * @param forecastDays 预测天数
     * @return 预测结果 (含预测 LTV、月度预测序列、回归斜率等)
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> forecast(Long customerId, int forecastDays) throws ScrmException {
        calculationService.findCustomerOrThrow(customerId);
        List<ScrmOrderEntity> orders = orderRepository
                .findByCustomerIdOrderByCreateTimeDesc(customerId);
        List<ScrmOrderEntity> validOrders = orders.stream()
                .filter(o -> !ORDER_STATUS_CANCELLED.equals(o.getOrderStatus()))
                .collect(Collectors.toList());

        // 按月聚合月度收入 (按订单创建时间所在月份)
        Map<YearMonth, Double> monthlyMap = new TreeMap<>();
        for (ScrmOrderEntity order : validOrders) {
            if (order.getCreateTime() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(order.getCreateTime());
            monthlyMap.merge(ym, order.getTotalAmount() != null ? order.getTotalAmount() : 0d, Double::sum);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("forecastDays", forecastDays);

        if (monthlyMap.size() < 2) {
            // 数据不足, 回退简单平均
            double avgMonthly = monthlyMap.values().stream().mapToDouble(d -> d).findFirst().orElse(0d);
            int forecastMonths = (int) Math.ceil(forecastDays / DAYS_PER_MONTH);
            double projectedRevenue = avgMonthly * forecastMonths;
            result.put("method", "SIMPLE_AVG_FALLBACK");
            result.put("projectedRevenue", round2(projectedRevenue));
            result.put("projectedLtv", round2(projectedRevenue * 0.3d));
            result.put("slope", 0d);
            result.put("intercept", round2(avgMonthly));
            result.put("monthlyForecast", buildMonthlyForecast(avgMonthly, avgMonthly, forecastMonths));
            return result;
        }

        // 线性回归: x = 月份序号 (0,1,2...), y = 月度收入
        List<double[]> points = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<YearMonth, Double> entry : monthlyMap.entrySet()) {
            points.add(new double[]{idx, entry.getValue()});
            idx++;
        }
        double[] regression = linearRegression(points);
        double slope = regression[0];
        double intercept = regression[1];

        int forecastMonths = (int) Math.ceil(forecastDays / DAYS_PER_MONTH);
        int startIndex = monthlyMap.size();
        List<Map<String, Object>> monthlyForecast = new ArrayList<>();
        double projectedRevenue = 0d;
        for (int m = 0; m < forecastMonths; m++) {
            double predicted = Math.max(0, intercept + slope * (startIndex + m));
            projectedRevenue += predicted;
            Map<String, Object> month = new LinkedHashMap<>();
            YearMonth ym = YearMonth.now().plusMonths(m + 1);
            month.put("month", ym.toString());
            month.put("predictedRevenue", round2(predicted));
            monthlyForecast.add(month);
        }

        result.put("method", "LINEAR_REGRESSION");
        result.put("slope", round2(slope));
        result.put("intercept", round2(intercept));
        result.put("projectedRevenue", round2(projectedRevenue));
        result.put("projectedLtv", round2(projectedRevenue * 0.3d));
        result.put("monthlyForecast", monthlyForecast);
        return result;
    }

    /**
     * 预测客户未来收入 (基于线性回归, 返回指定月数的月度预测)
     *
     * @param customerId 客户 ID
     * @param months     预测月数
     * @return 预测结果 (含月度序列与总收入)
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> forecastRevenue(Long customerId, int months) throws ScrmException {
        int forecastDays = (int) (months * DAYS_PER_MONTH);
        Map<String, Object> forecastResult = forecast(customerId, forecastDays);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("months", months);
        result.put("monthlyForecast", forecastResult.get("monthlyForecast"));
        result.put("totalProjectedRevenue", forecastResult.get("projectedRevenue"));
        return result;
    }

    /**
     * 预测客户流失 (基于购买间隔与频次)
     * <p>
     * 流失概率 = 1 - exp(-churnRate * daysSinceLastPurchase / max(avgInterval, 1)),
     * 预测流失日期 = 最后购买日 + 平均购买间隔 / churnRate。
     * </p>
     *
     * @param customerId 客户 ID
     * @return 流失预测结果
     * @throws ScrmException 客户 LTV 结果不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> predictChurn(Long customerId) throws ScrmException {
        ScrmCustomerLtvEntity ltv = ltvRepository
                .findFirstByCustomerIdOrderByCalculatedAtDesc(customerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户 LTV 结果不存在: customerId=" + customerId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("churnProbability", ltv.getChurnProbability());
        result.put("predictedChurnDate", ltv.getPredictedChurnDate());
        result.put("daysSinceLastPurchase", ltv.getDaysSinceLastPurchase());
        result.put("avgPurchaseIntervalDays", ltv.getAvgPurchaseIntervalDays());
        result.put("valueTier", ltv.getValueTier());
        result.put("riskLevel", ltv.getChurnProbability() != null && ltv.getChurnProbability() >= HIGH_CHURN_THRESHOLD
                ? "HIGH" : (ltv.getChurnProbability() != null && ltv.getChurnProbability() >= 0.4d ? "MEDIUM" : "LOW"));
        return result;
    }

    /**
     * 查询高流失风险客户 (按流失概率降序取 Top N)
     *
     * @param limit 返回条数
     * @return LTV 结果列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomerLtvDto> getChurnRiskCustomers(int limit) {
        Pageable top = PageRequest.of(0, Math.max(1, limit));
        return ltvRepository.findAllByOrderByChurnProbabilityDesc(top).stream()
                .map(calculationService::toLtvDto).collect(Collectors.toList());
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 一元线性回归 (最小二乘法)
     *
     * @param points 数据点 [x, y]
     * @return [slope, intercept]
     */
    private double[] linearRegression(List<double[]> points) {
        int n = points.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (double[] p : points) {
            sumX += p[0];
            sumY += p[1];
            sumXY += p[0] * p[1];
            sumX2 += p[0] * p[0];
        }
        double denominator = n * sumX2 - sumX * sumX;
        double slope = denominator != 0 ? (n * sumXY - sumX * sumY) / denominator : 0d;
        double intercept = (sumY - slope * sumX) / n;
        return new double[]{slope, intercept};
    }

    /**
     * 构建月度预测序列 (用于 forecast 回退场景)
     *
     * @param slope     回归斜率
     * @param intercept 回归截距
     * @param months    预测月数
     * @return 月度预测序列
     */
    private List<Map<String, Object>> buildMonthlyForecast(double slope, double intercept, int months) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int m = 0; m < months; m++) {
            double predicted = Math.max(0, intercept + slope * m);
            Map<String, Object> month = new LinkedHashMap<>();
            month.put("month", YearMonth.now().plusMonths(m + 1).toString());
            month.put("predictedRevenue", round2(predicted));
            result.add(month);
        }
        return result;
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
}