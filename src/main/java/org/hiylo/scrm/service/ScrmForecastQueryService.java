/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastQueryService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmForecastResultEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmForecastResultRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 销售预测结果查询服务。
 * <p>
 * 承载预测结果查询子域: 按场景 / 日期 / 客群 / 产品查询预测结果、查询预测趋势、
 * 查询预测准确度与导出预测结果。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmForecastQueryService {

    /** 预测结果数据访问层 */
    private final ScrmForecastResultRepository resultRepository;
    /** 预测场景子域服务 (共享场景查找) */
    private final ScrmForecastScenarioService scenarioService;
    /** 预测模型子域服务 (共享准确度指标算法) */
    private final ScrmForecastModelService modelService;

    /**
     * 查询场景预测结果 (按预测日期升序分页)。
     *
     * @param scenarioId 场景 ID
     * @param pageable   分页参数
     * @return 预测结果分页
     * @throws ScrmException 场景不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmForecastResultEntity> getForecast(Long scenarioId, Pageable pageable) throws ScrmException {
        return scenarioService.getScenarioResults(scenarioId, pageable);
    }

    /**
     * 按预测日期查询结果 (跨场景)。
     *
     * @param date 预测日期
     * @return 预测结果列表
     */
    @Transactional(readOnly = true)
    public List<ScrmForecastResultEntity> getForecastByDate(LocalDate date) {
        if (date == null) {
            return List.of();
        }
        return resultRepository.findByForecastDate(date);
    }

    /**
     * 按客群查询预测结果。
     *
     * @param segment 客群
     * @return 预测结果列表
     * @throws ScrmException 客群为空
     */
    @Transactional(readOnly = true)
    public List<ScrmForecastResultEntity> getForecastBySegment(String segment) throws ScrmException {
        if (segment == null || segment.isBlank()) {
            throw ScrmException.badRequest("客群不能为空");
        }
        return findByDimension("segment", segment);
    }

    /**
     * 按产品查询预测结果。
     *
     * @param product 产品
     * @return 预测结果列表
     * @throws ScrmException 产品为空
     */
    @Transactional(readOnly = true)
    public List<ScrmForecastResultEntity> getForecastByProduct(String product) throws ScrmException {
        if (product == null || product.isBlank()) {
            throw ScrmException.badRequest("产品不能为空");
        }
        return findByDimension("product", product);
    }

    /**
     * 查询预测趋势 (按场景汇总各周期预测值与置信区间)。
     *
     * @param scenarioId 场景 ID
     * @param periods    返回最近周期数 (可空, 缺省全部)
     * @return 趋势结果 Map
     * @throws ScrmException 场景不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getForecastTrend(Long scenarioId, Integer periods) throws ScrmException {
        scenarioService.findScenarioOrThrow(scenarioId);
        List<ScrmForecastResultEntity> all = resultRepository
                .findByScenarioIdOrderByForecastDateAsc(scenarioId);
        List<ScrmForecastResultEntity> filtered = all;
        if (periods != null && periods > 0 && periods < all.size()) {
            filtered = all.subList(all.size() - periods, all.size());
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (ScrmForecastResultEntity r : filtered) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("forecastDate", r.getForecastDate());
            point.put("periodLabel", r.getPeriodLabel());
            point.put("periodIndex", r.getPeriodIndex());
            point.put("forecastValue", r.getForecastValue());
            point.put("actualValue", r.getActualValue());
            point.put("confidenceLower", r.getConfidenceLower());
            point.put("confidenceUpper", r.getConfidenceUpper());
            point.put("isActual", r.getIsActual());
            trend.add(point);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarioId", scenarioId);
        result.put("points", trend);
        result.put("total", trend.size());
        return result;
    }

    /**
     * 查询场景预测准确度 (基于已回填实际值的结果)。
     *
     * @param scenarioId 场景 ID
     * @return 准确度指标 Map
     * @throws ScrmException 场景不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getForecastAccuracy(Long scenarioId) throws ScrmException {
        scenarioService.findScenarioOrThrow(scenarioId);
        List<ScrmForecastResultEntity> results = resultRepository
                .findByScenarioIdOrderByForecastDateAsc(scenarioId);
        List<Double> actuals = new ArrayList<>();
        List<Double> forecasts = new ArrayList<>();
        for (ScrmForecastResultEntity r : results) {
            if (r.getActualValue() != null) {
                actuals.add(r.getActualValue());
                forecasts.add(r.getForecastValue());
            }
        }
        Map<String, Double> metrics = modelService.calculateAccuracyMetrics(actuals, forecasts);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarioId", scenarioId);
        result.put("comparedPoints", actuals.size());
        result.put("totalPoints", results.size());
        result.putAll(metrics);
        return result;
    }

    /**
     * 导出场景预测结果。
     *
     * @param scenarioId 场景 ID (可空, 为空导出全部)
     * @return 预测结果列表
     * @throws ScrmException 场景不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmForecastResultEntity> exportForecast(Long scenarioId) throws ScrmException {
        if (scenarioId != null) {
            scenarioService.findScenarioOrThrow(scenarioId);
            return resultRepository.findByScenarioIdOrderByForecastDateAsc(scenarioId);
        }
        return resultRepository.findAll(Sort.by(Sort.Direction.ASC, "forecastDate"));
    }

    // ============================================================
    // 私有: 工具方法
    // ============================================================

    /**
     * 按维度字段查询结果 (segment / product / channel / region)。
     *
     * @param field 字段名
     * @param value 字段值
     * @return 结果列表
     */
    private List<ScrmForecastResultEntity> findByDimension(String field, String value) {
        Specification<ScrmForecastResultEntity> spec = (root, query, cb) -> {
            query.orderBy(cb.asc(root.get("forecastDate")));
            return cb.and(
                    cb.equal(root.get(field), value));
        };
        return resultRepository.findAll(spec);
    }
}
