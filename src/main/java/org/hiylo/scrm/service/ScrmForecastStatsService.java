/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmForecastModelEntity;
import org.hiylo.scrm.entity.ScrmForecastResultEntity;
import org.hiylo.scrm.entity.ScrmForecastScenarioEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmForecastModelRepository;
import org.hiylo.scrm.repository.ScrmForecastResultRepository;
import org.hiylo.scrm.repository.ScrmForecastScenarioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 销售预测统计与概览服务。
 * <p>
 * 承载预测统计子域: 预测概览、准确度趋势、模型表现与预测 vs 实际对比。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmForecastStatsService {

    /** 预测模型数据访问层 */
    private final ScrmForecastModelRepository modelRepository;
    /** 预测场景数据访问层 */
    private final ScrmForecastScenarioRepository scenarioRepository;
    /** 预测结果数据访问层 */
    private final ScrmForecastResultRepository resultRepository;
    /** 预测场景子域服务 (共享场景查找) */
    private final ScrmForecastScenarioService scenarioService;

    /**
     * 预测统计: 模型数 / 场景数 / 结果数 / 已训练模型平均准确度等。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getForecastStats() {
        List<ScrmForecastModelEntity> models = modelRepository.findAll();
        List<ScrmForecastScenarioEntity> scenarios = scenarioRepository.findAll();
        long modelCount = models.size();
        long trainedModelCount = models.stream().filter(m -> Boolean.TRUE.equals(m.getIsTrained())).count();
        long enabledModelCount = models.stream().filter(m -> Boolean.TRUE.equals(m.getEnabled())).count();
        double avgModelAccuracy = models.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsTrained()))
                .filter(m -> m.getLastAccuracyScore() != null)
                .mapToDouble(ScrmForecastModelEntity::getLastAccuracyScore)
                .average().orElse(0d);
        long scenarioCount = scenarios.size();
        long completedScenarioCount = scenarios.stream()
                .filter(s -> ScrmForecastModelService.STATUS_COMPLETED.equals(s.getStatus())).count();
        long approvedScenarioCount = scenarios.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsApproved())).count();
        double totalForecastValue = scenarios.stream()
                .filter(s -> ScrmForecastModelService.STATUS_COMPLETED.equals(s.getStatus()))
                .filter(s -> s.getTotalForecastValue() != null)
                .mapToDouble(ScrmForecastScenarioEntity::getTotalForecastValue).sum();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("modelCount", modelCount);
        stats.put("trainedModelCount", trainedModelCount);
        stats.put("enabledModelCount", enabledModelCount);
        stats.put("avgModelAccuracy", avgModelAccuracy);
        stats.put("scenarioCount", scenarioCount);
        stats.put("completedScenarioCount", completedScenarioCount);
        stats.put("approvedScenarioCount", approvedScenarioCount);
        stats.put("totalForecastValue", totalForecastValue);
        return stats;
    }

    /**
     * 准确度趋势: 按模型最近训练时间聚合平均准确度。
     *
     * @param months 回溯月数 (可空, 缺省 12)
     * @return 趋势结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAccuracyTrend(Integer months) {
        int lookback = months != null && months > 0 ? months : 12;
        LocalDateTime since = LocalDateTime.now().minusMonths(lookback);
        List<ScrmForecastModelEntity> models = modelRepository.findAll();
        List<Map<String, Object>> points = new ArrayList<>();
        for (ScrmForecastModelEntity m : models) {
            if (m.getLastTrainedAt() == null || m.getLastTrainedAt().isBefore(since)) {
                continue;
            }
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("modelId", m.getId());
            p.put("modelCode", m.getModelCode());
            p.put("trainedAt", m.getLastTrainedAt());
            p.put("accuracyScore", m.getLastAccuracyScore());
            p.put("mape", m.getLastMape());
            p.put("rmse", m.getLastRmse());
            p.put("modelVersion", m.getModelVersion());
            points.add(p);
        }
        points.sort(Comparator.comparing(p -> (Comparable) p.get("trainedAt")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("months", lookback);
        result.put("points", points);
        result.put("total", points.size());
        return result;
    }

    /**
     * 模型表现: 列出所有已训练模型的关键准确度指标。
     *
     * @return 模型表现列表
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getModelPerformance() {
        List<ScrmForecastModelEntity> models = modelRepository.findAll(
                (root, query, cb) -> cb.and());
        List<Map<String, Object>> performance = models.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsTrained()))
                .sorted(Comparator.comparingDouble(
                        (ScrmForecastModelEntity m) -> m.getLastAccuracyScore() != null ? m.getLastAccuracyScore() : 0d)
                        .reversed())
                .map(m -> {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("modelId", m.getId());
                    p.put("modelCode", m.getModelCode());
                    p.put("modelName", m.getModelName());
                    p.put("modelType", m.getModelType());
                    p.put("targetMetric", m.getTargetMetric());
                    p.put("accuracyScore", m.getLastAccuracyScore());
                    p.put("mape", m.getLastMape());
                    p.put("mae", m.getLastMae());
                    p.put("rmse", m.getLastRmse());
                    p.put("r2", m.getLastR2());
                    p.put("crossValidationScore", m.getCrossValidationScore());
                    p.put("lastTrainedAt", m.getLastTrainedAt());
                    p.put("trainingDataPoints", m.getTrainingDataPoints());
                    return p;
                })
                .collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("models", performance);
        result.put("total", performance.size());
        return result;
    }

    /**
     * 预测 vs 实际: 对比场景中已回填实际值的预测点。
     *
     * @param scenarioId 场景 ID
     * @return 对比结果 Map
     * @throws ScrmException 场景不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getForecastVsActual(Long scenarioId) throws ScrmException {
        scenarioService.findScenarioOrThrow(scenarioId);
        List<ScrmForecastResultEntity> results = resultRepository
                .findByScenarioIdOrderByForecastDateAsc(scenarioId);
        List<Map<String, Object>> points = new ArrayList<>();
        double totalForecast = 0d;
        double totalActual = 0d;
        int compared = 0;
        for (ScrmForecastResultEntity r : results) {
            if (r.getActualValue() == null) {
                continue;
            }
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("forecastDate", r.getForecastDate());
            p.put("periodLabel", r.getPeriodLabel());
            p.put("forecastValue", r.getForecastValue());
            p.put("actualValue", r.getActualValue());
            p.put("variance", r.getVariance());
            p.put("variancePercent", r.getVariancePercent());
            points.add(p);
            totalForecast += r.getForecastValue();
            totalActual += r.getActualValue();
            compared++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarioId", scenarioId);
        result.put("comparedPoints", compared);
        result.put("totalPoints", results.size());
        result.put("totalForecast", totalForecast);
        result.put("totalActual", totalActual);
        result.put("totalVariance", totalActual - totalForecast);
        result.put("totalVariancePercent", totalForecast == 0 ? 0d :
                (totalActual - totalForecast) / totalForecast * 100);
        result.put("points", points);
        return result;
    }

    /**
     * 预测概览: 顶层仪表盘汇总。
     *
     * @return 概览 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getForecastOverview() {
        Map<String, Object> stats = getForecastStats();
        // 最近完成的场景 Top 5
        List<ScrmForecastScenarioEntity> recent = scenarioRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("status"), ScrmForecastModelService.STATUS_COMPLETED)),
                Sort.by(Sort.Direction.DESC, "runCompletedAt"));
        List<Map<String, Object>> recentScenarios = new ArrayList<>();
        int limit = Math.min(5, recent.size());
        for (int i = 0; i < limit; i++) {
            ScrmForecastScenarioEntity s = recent.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scenarioId", s.getId());
            m.put("scenarioName", s.getScenarioName());
            m.put("scenarioType", s.getScenarioType());
            m.put("targetPeriod", s.getTargetPeriod());
            m.put("totalForecastValue", s.getTotalForecastValue());
            m.put("accuracyEstimate", s.getAccuracyEstimate());
            m.put("runCompletedAt", s.getRunCompletedAt());
            recentScenarios.add(m);
        }
        // 模型类型分布
        List<ScrmForecastModelEntity> models = modelRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Long> typeDistribution = models.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getModelType() != null ? m.getModelType() : "UNKNOWN",
                        Collectors.counting()));
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("stats", stats);
        overview.put("recentScenarios", recentScenarios);
        overview.put("modelTypeDistribution", typeDistribution);
        return overview;
    }
}
