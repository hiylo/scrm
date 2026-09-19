/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastModelService.java
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

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmForecastModelDto;
import org.hiylo.scrm.entity.ScrmForecastModelEntity;
import org.hiylo.scrm.entity.ScrmForecastResultEntity;
import org.hiylo.scrm.entity.ScrmForecastScenarioEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmForecastModelRepository;
import org.hiylo.scrm.repository.ScrmForecastResultRepository;
import org.hiylo.scrm.repository.ScrmForecastScenarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 销售预测模型管理服务。
 * <p>
 * 承载预测模型管理子域: 模型增删改查、启停、训练 / 重训练 / 自动重训练、准确度查询、
 * 历史查询、多模型对比、最佳模型与统计更新。训练完整实现移动平均 (Moving Average) 与
 * 指数平滑 (Exponential Smoothing) 两种算法, 并以 holdout 验证计算 MAPE / MAE / RMSE / R2。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmForecastModelService {

    // ==================== 默认值常量 ====================

    /** 默认操作人 */
    private static final String DEFAULT_OPERATOR = "scrm-system";
    /** 默认粒度 */
    static final String DEFAULT_GRANULARITY = "MONTHLY";
    /** 默认指数平滑系数 alpha */
    static final double DEFAULT_ALPHA = 0.3d;
    /** 默认移动平均窗口大小 */
    static final int DEFAULT_WINDOW_SIZE = 6;
    /** 合成训练数据基准值 */
    private static final double SYNTHETIC_BASE = 1000d;
    /** 合成训练数据趋势系数 */
    private static final double SYNTHETIC_TREND = 0.05d;
    /** 合成训练数据季节系数 */
    private static final double SYNTHETIC_SEASONALITY = 0.15d;
    /** 合成训练数据季节周期 */
    private static final int SYNTHETIC_SEASON_PERIOD = 12;

    // ==================== 合法枚举值 ====================

    /** 合法的模型类型 */
    private static final List<String> VALID_MODEL_TYPES = List.of(
            "LINEAR_REGRESSION", "MOVING_AVERAGE", "EXPONENTIAL_SMOOTHING", "SEASONAL_ARIMA",
            "ML_RANDOM_FOREST", "ML_GRADIENT_BOOST", "ENSEMBLE", "CUSTOM");
    /** 合法的目标指标 */
    private static final List<String> VALID_TARGET_METRICS = List.of(
            "REVENUE", "ORDER_COUNT", "CUSTOMER_COUNT", "DEAL_COUNT", "AVG_ORDER_VALUE", "CONVERSION_RATE");
    /** 合法的粒度 */
    static final List<String> VALID_GRANULARITIES = List.of("DAILY", "WEEKLY", "MONTHLY", "QUARTERLY");
    /** 合法的重训练频率 */
    private static final List<String> VALID_RETRAIN_FREQUENCIES = List.of("DAILY", "WEEKLY", "MONTHLY", "QUARTERLY");
    /** 已完成的场景状态 */
    static final String STATUS_COMPLETED = "COMPLETED";
    /** 运行中的场景状态 */
    static final String STATUS_RUNNING = "RUNNING";
    /** 失败的场景状态 */
    static final String STATUS_FAILED = "FAILED";
    /** 已归档的场景状态 */
    static final String STATUS_ARCHIVED = "ARCHIVED";

    // ==================== 依赖注入 ====================

    /** 预测模型数据访问层 */
    private final ScrmForecastModelRepository modelRepository;
    /** 预测场景数据访问层 */
    private final ScrmForecastScenarioRepository scenarioRepository;
    /** 预测结果数据访问层 */
    private final ScrmForecastResultRepository resultRepository;
    /** JSON 解析器 */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 模型管理
    // ============================================================

    /**
     * 创建预测模型。
     * <p>校验模型类型 / 目标指标 / 粒度合法, modelCode 唯一, 缺省字段填默认值。</p>
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmForecastModelEntity createModel(ScrmForecastModelDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模型参数不能为空");
        }
        validateModelEnums(dto, false);
        if (modelRepository.countByModelCode(dto.getModelCode()) > 0) {
            throw ScrmException.conflict("模型编码已存在: " + dto.getModelCode());
        }
        ScrmForecastModelEntity entity = new ScrmForecastModelEntity();
        entity.setModelName(dto.getModelName());
        entity.setModelCode(dto.getModelCode());
        entity.setDescription(dto.getDescription());
        entity.setModelType(dto.getModelType());
        entity.setAlgorithm(dto.getAlgorithm());
        entity.setTargetMetric(dto.getTargetMetric());
        entity.setGranularity(dto.getGranularity() != null ? dto.getGranularity() : DEFAULT_GRANULARITY);
        entity.setLookbackPeriods(dto.getLookbackPeriods());
        entity.setForecastPeriods(dto.getForecastPeriods());
        entity.setSeasonalityPeriod(dto.getSeasonalityPeriod());
        entity.setParameters(dto.getParameters());
        entity.setTrainingDataStart(dto.getTrainingDataStart());
        entity.setTrainingDataEnd(dto.getTrainingDataEnd());
        entity.setIsAutoRetrain(dto.getIsAutoRetrain());
        entity.setRetrainFrequency(dto.getRetrainFrequency() != null ? dto.getRetrainFrequency() : "MONTHLY");
        entity.setApplicableSegments(dto.getApplicableSegments());
        entity.setApplicableProducts(dto.getApplicableProducts());
        entity.setApplicableChannels(dto.getApplicableChannels());
        entity.setApplicableRegions(dto.getApplicableRegions());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        entity = modelRepository.save(entity);
        log.info("创建预测模型: id={}, code={}, type={}", entity.getId(), entity.getModelCode(), entity.getModelType());
        return entity;
    }

    /**
     * 更新预测模型。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / 编码重复
     */
    @Transactional
    public ScrmForecastModelEntity updateModel(Long id, ScrmForecastModelDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模型参数不能为空");
        }
        validateModelEnums(dto, true);
        ScrmForecastModelEntity entity = findModelOrThrow(id);
        if (!Objects.equals(entity.getModelCode(), dto.getModelCode()) && modelRepository.countByModelCode(dto.getModelCode()) > 0) {
            throw ScrmException.conflict("模型编码已存在: " + dto.getModelCode());
        }
        entity.setModelName(dto.getModelName());
        entity.setModelCode(dto.getModelCode());
        entity.setDescription(dto.getDescription());
        if (dto.getModelType() != null) {
            entity.setModelType(dto.getModelType());
        }
        entity.setAlgorithm(dto.getAlgorithm());
        if (dto.getTargetMetric() != null) {
            entity.setTargetMetric(dto.getTargetMetric());
        }
        if (dto.getGranularity() != null) {
            entity.setGranularity(dto.getGranularity());
        }
        if (dto.getLookbackPeriods() != null) {
            entity.setLookbackPeriods(dto.getLookbackPeriods());
        }
        if (dto.getForecastPeriods() != null) {
            entity.setForecastPeriods(dto.getForecastPeriods());
        }
        entity.setSeasonalityPeriod(dto.getSeasonalityPeriod());
        entity.setParameters(dto.getParameters());
        entity.setTrainingDataStart(dto.getTrainingDataStart());
        entity.setTrainingDataEnd(dto.getTrainingDataEnd());
        if (dto.getIsAutoRetrain() != null) {
            entity.setIsAutoRetrain(dto.getIsAutoRetrain());
        }
        if (dto.getRetrainFrequency() != null) {
            entity.setRetrainFrequency(dto.getRetrainFrequency());
        }
        entity.setApplicableSegments(dto.getApplicableSegments());
        entity.setApplicableProducts(dto.getApplicableProducts());
        entity.setApplicableChannels(dto.getApplicableChannels());
        entity.setApplicableRegions(dto.getApplicableRegions());
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        entity.setNotes(dto.getNotes());
        entity = modelRepository.save(entity);
        log.info("更新预测模型: id={}, code={}", entity.getId(), entity.getModelCode());
        return entity;
    }

    /**
     * 删除预测模型 (存在关联场景时拒绝删除)。
     *
     * @param id 模型 ID
     * @throws ScrmException 模型不存在 / 存在关联场景
     */
    @Transactional
    public void deleteModel(Long id) throws ScrmException {
        ScrmForecastModelEntity entity = findModelOrThrow(id);
        List<ScrmForecastScenarioEntity> related = scenarioRepository.findByModelId(
                 id);
        if (!related.isEmpty()) {
            throw ScrmException.badRequest("模型存在关联场景, 无法删除: scenarioCount=" + related.size());
        }
        modelRepository.delete(entity);
        log.info("删除预测模型: id={}, code={}", id, entity.getModelCode());
    }

    /**
     * 查询模型详情。
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public ScrmForecastModelEntity getModel(Long id) throws ScrmException {
        return findModelOrThrow(id);
    }

    /**
     * 按模型编码查询模型。
     *
     * @param code 模型编码
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public ScrmForecastModelEntity getModelByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("模型编码不能为空");
        }
        return modelRepository.findByModelCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "模型不存在: code=" + code));
    }

    /**
     * 分页查询模型列表, 支持按模型类型 / 目标指标 / 启用状态 / 训练状态 / 关键字过滤。
     *
     * @param modelType     模型类型过滤（可空）
     * @param targetMetric  目标指标过滤（可空）
     * @param enabled       启用状态过滤（可空）
     * @param isTrained     训练状态过滤（可空）
     * @param keyword       名称/编码关键字（可空）
     * @param pageable      分页参数
     * @return 模型分页结果 (按 updateTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmForecastModelEntity> listModels(String modelType, String targetMetric, Boolean enabled,
                                                     Boolean isTrained, String keyword, Pageable pageable) {
        Specification<ScrmForecastModelEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (modelType != null && !modelType.isBlank()) {
                predicates.add(cb.equal(root.get("modelType"), modelType));
            }
            if (targetMetric != null && !targetMetric.isBlank()) {
                predicates.add(cb.equal(root.get("targetMetric"), targetMetric));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (isTrained != null) {
                predicates.add(cb.equal(root.get("isTrained"), isTrained));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("modelName"), like),
                        cb.like(root.get("modelCode"), like)));
            }
            query.orderBy(cb.desc(root.get("updateTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return modelRepository.findAll(spec, pageable);
    }

    /**
     * 启用模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public ScrmForecastModelEntity enableModel(Long id) throws ScrmException {
        ScrmForecastModelEntity entity = findModelOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = modelRepository.save(entity);
        log.info("启用预测模型: id={}", id);
        return entity;
    }

    /**
     * 禁用模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public ScrmForecastModelEntity disableModel(Long id) throws ScrmException {
        ScrmForecastModelEntity entity = findModelOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = modelRepository.save(entity);
        log.info("禁用预测模型: id={}", id);
        return entity;
    }

    /**
     * 训练模型 (完整实现移动平均 + 指数平滑)。
     * <p>训练流程: 加载历史数据 (优先取回填的实际值, 缺省生成合成历史) → 按模型类型生成 holdout 预测
     * → 计算 MAPE / MAE / RMSE / R2 / 准确度评分 → 滚动交叉验证评分 → 回写模型训练指标与版本号。</p>
     *
     * @param modelId 模型 ID
     * @return 训练后的模型
     * @throws ScrmException 模型不存在 / 未启用 / 训练数据为空
     */
    @Transactional
    public ScrmForecastModelEntity trainModel(Long modelId) throws ScrmException {
        ScrmForecastModelEntity model = findModelOrThrow(modelId);
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw ScrmException.badRequest("模型未启用, 无法训练: id=" + modelId);
        }
        Map<String, Object> params = parseJsonToMap(model.getParameters());
        int windowSize = getInt(params, "windowSize",
                model.getLookbackPeriods() != null ? Math.min(model.getLookbackPeriods(), DEFAULT_WINDOW_SIZE) :
                        DEFAULT_WINDOW_SIZE);
        double alpha = getDouble(params, "alpha", DEFAULT_ALPHA);

        List<Double> history = loadTrainingData(model);
        if (history == null || history.isEmpty()) {
            throw ScrmException.badRequest("训练数据为空, 无法训练模型: id=" + modelId);
        }

        // holdout: 取末尾 min(forecastPeriods, size/4) 作为验证集
        int forecastPeriods = model.getForecastPeriods() != null ? model.getForecastPeriods() : 3;
        int holdout = Math.min(forecastPeriods, Math.max(1, history.size() / 4));
        int trainSize = history.size() - holdout;
        if (trainSize < 1) {
            trainSize = Math.max(1, history.size() - 1);
            holdout = history.size() - trainSize;
        }
        List<Double> train = new ArrayList<>(history.subList(0, trainSize));
        List<Double> testActuals = new ArrayList<>(history.subList(trainSize, history.size()));
        List<Double> holdoutForecasts = generateForecasts(model, train, params, windowSize, alpha, holdout);
        Map<String, Double> metrics = calculateAccuracyMetrics(testActuals, holdoutForecasts);

        // 滚动交叉验证: 多次切分取准确度均值
        double cvScore = computeCrossValidationScore(model, history, params, windowSize, alpha);

        model.setIsTrained(Boolean.TRUE);
        model.setLastTrainedAt(LocalDateTime.now());
        model.setTrainingDataPoints(history.size());
        if (model.getTrainingDataStart() == null) {
            model.setTrainingDataStart(LocalDate.now().minus(history.size(),
                    granularityToChronoUnit(model.getGranularity())));
        }
        if (model.getTrainingDataEnd() == null) {
            model.setTrainingDataEnd(LocalDate.now());
        }
        model.setLastMape(metrics.get("mape"));
        model.setLastMae(metrics.get("mae"));
        model.setLastRmse(metrics.get("rmse"));
        model.setLastR2(metrics.get("r2"));
        model.setLastAccuracyScore(metrics.get("accuracyScore"));
        model.setCrossValidationScore(cvScore);
        model.setModelVersion((model.getModelVersion() != null ? model.getModelVersion() : 1) + 1);
        model = modelRepository.save(model);
        log.info("训练预测模型完成: id={}, type={}, mape={}, accuracy={}",
                model.getId(), model.getModelType(), model.getLastMape(), model.getLastAccuracyScore());
        return model;
    }

    /**
     * 重新训练模型 (等价于 trainModel)。
     *
     * @param modelId 模型 ID
     * @return 训练后的模型
     * @throws ScrmException 模型不存在 / 未启用 / 训练数据为空
     */
    @Transactional
    public ScrmForecastModelEntity retrainModel(Long modelId) throws ScrmException {
        return trainModel(modelId);
    }

    /**
     * 自动重训练: 扫描所有启用且开启自动重训练的模型并逐个重训练。
     *
     * @return 重训练汇总 {total, success, failed, details}
     */
    @Transactional
    public Map<String, Object> autoRetrain() {
        List<ScrmForecastModelEntity> candidates = modelRepository
                .findByEnabledAndIsAutoRetrainTrue(Boolean.TRUE);
        int success = 0;
        int failed = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (ScrmForecastModelEntity model : candidates) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("modelId", model.getId());
            detail.put("modelCode", model.getModelCode());
            try {
                ScrmForecastModelEntity trained = trainModel(model.getId());
                detail.put("status", "SUCCESS");
                detail.put("accuracyScore", trained.getLastAccuracyScore());
                detail.put("mape", trained.getLastMape());
                success++;
            } catch (Exception e) {
                detail.put("status", "FAILED");
                detail.put("error", e.getMessage());
                failed++;
                log.warn("自动重训练失败: modelId={}", model.getId(), e);
            }
            details.add(detail);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", candidates.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("details", details);
        log.info("自动重训练完成: total={}, success={}, failed={}", candidates.size(), success, failed);
        return result;
    }

    /**
     * 查询模型准确度指标。
     *
     * @param id 模型 ID
     * @return 准确度指标 Map
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getModelAccuracy(Long id) throws ScrmException {
        ScrmForecastModelEntity model = findModelOrThrow(id);
        Map<String, Object> accuracy = new LinkedHashMap<>();
        accuracy.put("modelId", model.getId());
        accuracy.put("modelCode", model.getModelCode());
        accuracy.put("isTrained", model.getIsTrained());
        accuracy.put("accuracyScore", model.getLastAccuracyScore());
        accuracy.put("mape", model.getLastMape());
        accuracy.put("mae", model.getLastMae());
        accuracy.put("rmse", model.getLastRmse());
        accuracy.put("r2", model.getLastR2());
        accuracy.put("crossValidationScore", model.getCrossValidationScore());
        accuracy.put("lastTrainedAt", model.getLastTrainedAt());
        accuracy.put("trainingDataPoints", model.getTrainingDataPoints());
        accuracy.put("modelVersion", model.getModelVersion());
        return accuracy;
    }

    /**
     * 查询模型预测历史 (按预测日期升序分页)。
     *
     * @param id       模型 ID
     * @param pageable 分页参数
     * @return 预测结果分页
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmForecastResultEntity> getModelHistory(Long id, Pageable pageable) throws ScrmException {
        findModelOrThrow(id);
        Specification<ScrmForecastResultEntity> spec = (root, query, cb) -> {
            query.orderBy(cb.asc(root.get("forecastDate")));
            return cb.equal(root.get("modelId"), id);
        };
        return resultRepository.findAll(spec, pageable);
    }

    /**
     * 对比多个模型的准确度指标。
     *
     * @param modelIds 模型 ID 列表
     * @return 对比结果 {models, bestModelId}
     * @throws ScrmException 模型不存在 / 列表为空
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareModels(List<Long> modelIds) throws ScrmException {
        if (modelIds == null || modelIds.isEmpty()) {
            throw ScrmException.badRequest("对比模型 ID 列表不能为空");
        }
        List<Map<String, Object>> models = new ArrayList<>();
        Long bestId = null;
        double bestScore = -1d;
        for (Long id : modelIds) {
            ScrmForecastModelEntity model = findModelOrThrow(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("modelId", model.getId());
            m.put("modelCode", model.getModelCode());
            m.put("modelName", model.getModelName());
            m.put("modelType", model.getModelType());
            m.put("targetMetric", model.getTargetMetric());
            m.put("isTrained", model.getIsTrained());
            m.put("accuracyScore", model.getLastAccuracyScore());
            m.put("mape", model.getLastMape());
            m.put("mae", model.getLastMae());
            m.put("rmse", model.getLastRmse());
            m.put("r2", model.getLastR2());
            m.put("crossValidationScore", model.getCrossValidationScore());
            m.put("modelVersion", model.getModelVersion());
            m.put("lastTrainedAt", model.getLastTrainedAt());
            models.add(m);
            double score = model.getLastAccuracyScore() != null ? model.getLastAccuracyScore() : 0d;
            if (score > bestScore) {
                bestScore = score;
                bestId = model.getId();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("models", models);
        result.put("bestModelId", bestId);
        result.put("bestAccuracyScore", bestScore < 0 ? 0d : bestScore);
        return result;
    }

    /**
     * 获取指定目标指标下准确度最高的已训练模型。
     *
     * @param targetMetric 目标指标 (可空, 为空取所有已训练模型)
     * @return 最佳模型
     * @throws ScrmException 无符合条件的模型
     */
    @Transactional(readOnly = true)
    public ScrmForecastModelEntity getBestModel(String targetMetric) throws ScrmException {
        List<ScrmForecastModelEntity> candidates;
        if (targetMetric != null && !targetMetric.isBlank()) {
            candidates = modelRepository.findByTargetMetricAndIsTrainedAndEnabledTrue(
                     targetMetric, Boolean.TRUE);
        } else {
            candidates = modelRepository.findByEnabledAndIsAutoRetrainTrue(Boolean.TRUE)
                    .stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsTrained()))
                    .collect(Collectors.toList());
        }
        return candidates.stream()
                .max(Comparator.comparingDouble(m -> m.getLastAccuracyScore() != null ? m.getLastAccuracyScore() : 0d))
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "未找到已训练的启用模型: targetMetric=" + targetMetric));
    }

    /**
     * 更新模型统计: 重新计算模型关联场景数 / 结果数 / 平均准确度, 并回写交叉验证评分。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public ScrmForecastModelEntity updateModelStats(Long id) throws ScrmException {
        ScrmForecastModelEntity model = findModelOrThrow(id);
        List<ScrmForecastScenarioEntity> scenarios = scenarioRepository.findByModelId(id);
        long completedScenarios = scenarios.stream()
                .filter(s -> STATUS_COMPLETED.equals(s.getStatus()))
                .count();
        double avgAccuracy = scenarios.stream()
                .filter(s -> STATUS_COMPLETED.equals(s.getStatus()))
                .filter(s -> s.getAccuracyEstimate() != null)
                .mapToDouble(ScrmForecastScenarioEntity::getAccuracyEstimate)
                .average()
                .orElse(0d);
        // 以已完成场景的平均准确度作为交叉验证评分的运行时反馈
        model.setCrossValidationScore(avgAccuracy);
        model = modelRepository.save(model);
        log.info("更新模型统计: id={}, scenarioCount={}, completed={}, avgAccuracy={}",
                id, scenarios.size(), completedScenarios, avgAccuracy);
        return model;
    }

    // ============================================================
    // 私有/包内: 查找与校验
    // ============================================================

    /**
     * 按主键查询模型, 不存在抛异常。
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    ScrmForecastModelEntity findModelOrThrow(Long id) throws ScrmException {
        ScrmForecastModelEntity entity = modelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "预测模型不存在: id=" + id));
        return entity;
    }

    /**
     * 校验模型 DTO 枚举字段。
     *
     * @param dto      模型参数
     * @param isUpdate 是否更新场景
     * @throws ScrmException 枚举非法
     */
    private void validateModelEnums(ScrmForecastModelDto dto, boolean isUpdate) throws ScrmException {
        if (!isUpdate || dto.getModelType() != null) {
            if (dto.getModelType() != null && !VALID_MODEL_TYPES.contains(dto.getModelType())) {
                throw ScrmException.badRequest("模型类型非法: " + dto.getModelType() + ", 仅支持 " + VALID_MODEL_TYPES);
            }
        }
        if (!isUpdate || dto.getTargetMetric() != null) {
            if (dto.getTargetMetric() != null && !VALID_TARGET_METRICS.contains(dto.getTargetMetric())) {
                throw ScrmException.badRequest("目标指标非法: " + dto.getTargetMetric() + ", 仅支持 " +
                        VALID_TARGET_METRICS);
            }
        }
        if (dto.getGranularity() != null && !VALID_GRANULARITIES.contains(dto.getGranularity())) {
            throw ScrmException.badRequest("粒度非法: " + dto.getGranularity() + ", 仅支持 " + VALID_GRANULARITIES);
        }
        if (dto.getRetrainFrequency() != null && !VALID_RETRAIN_FREQUENCIES.contains(dto.getRetrainFrequency())) {
            throw ScrmException.badRequest("重训练频率非法: " + dto.getRetrainFrequency() + ", 仅支持 " +
                    VALID_RETRAIN_FREQUENCIES);
        }
    }

    /**
     * 当前操作人 (优先取 UserContext, 缺省 scrm-system)。
     *
     * @return 操作人标识
     */
    String currentOperator() {
        String userId = UserContext.getUserId();
        return userId != null ? userId : DEFAULT_OPERATOR;
    }

    // ============================================================
    // 包内: 训练与预测算法
    // ============================================================

    /**
     * 加载训练数据: 优先取模型回填的实际值, 缺省生成合成历史序列。
     *
     * @param model 模型实体
     * @return 历史数据点列表 (按时间升序)
     */
    List<Double> loadTrainingData(ScrmForecastModelEntity model) {
        List<ScrmForecastResultEntity> actuals = resultRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("modelId"), model.getId()),
                        cb.isTrue(root.get("isActual"))));
        if (actuals != null && !actuals.isEmpty()) {
            actuals.sort(Comparator.comparing(ScrmForecastResultEntity::getForecastDate));
            return actuals.stream()
                    .map(e -> e.getActualValue() != null ? e.getActualValue() : e.getForecastValue())
                    .collect(Collectors.toList());
        }
        int lookback = model.getLookbackPeriods() != null ? model.getLookbackPeriods() : 12;
        return generateSyntheticHistory(lookback);
    }

    /**
     * 生成合成历史序列 (趋势 + 季节性, 确定性便于回归)。
     *
     * @param points 数据点数
     * @return 合成历史数据列表
     */
    private List<Double> generateSyntheticHistory(int points) {
        int n = Math.max(points, 4);
        int period = SYNTHETIC_SEASON_PERIOD;
        List<Double> data = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            double value = SYNTHETIC_BASE * (1 + SYNTHETIC_TREND * t)
                    * (1 + SYNTHETIC_SEASONALITY * Math.sin(2 * Math.PI * t / period));
            data.add(value);
        }
        return data;
    }

    /**
     * 按模型类型生成预测序列。
     * <p>MOVING_AVERAGE 使用移动平均, EXPONENTIAL_SMOOTHING 使用指数平滑,
     * ENSEMBLE 取两者均值, 其他类型回退到指数平滑。</p>
     *
     * @param model      模型实体
     * @param history    历史数据
     * @param params     模型参数
     * @param windowSize 移动平均窗口
     * @param alpha      指数平滑系数
     * @param periods    预测周期数
     * @return 预测值列表
     */
    List<Double> generateForecasts(ScrmForecastModelEntity model, List<Double> history,
                                   Map<String, Object> params, int windowSize, double alpha, int periods) {
        String type = model.getModelType();
        if ("MOVING_AVERAGE".equals(type)) {
            return calculateMovingAverage(history, windowSize, periods);
        }
        if ("EXPONENTIAL_SMOOTHING".equals(type)) {
            return calculateExponentialSmoothing(history, alpha, periods);
        }
        if ("ENSEMBLE".equals(type)) {
            List<Double> ma = calculateMovingAverage(history, windowSize, periods);
            List<Double> es = calculateExponentialSmoothing(history, alpha, periods);
            List<Double> ensemble = new ArrayList<>(periods);
            for (int i = 0; i < periods; i++) {
                ensemble.add((ma.get(i) + es.get(i)) / 2d);
            }
            return ensemble;
        }
        // LINEAR_REGRESSION / SEASONAL_ARIMA / ML_* / CUSTOM 默认指数平滑
        return calculateExponentialSmoothing(history, alpha, periods);
    }

    /**
     * 滚动交叉验证: 多次切分历史数据计算平均准确度评分。
     *
     * @param model      模型实体
     * @param history    历史数据
     * @param params     模型参数
     * @param windowSize 移动平均窗口
     * @param alpha      指数平滑系数
     * @return 交叉验证评分 0-1
     */
    private double computeCrossValidationScore(ScrmForecastModelEntity model, List<Double> history,
                                               Map<String, Object> params, int windowSize, double alpha) {
        int n = history.size();
        if (n < 4) {
            return 0d;
        }
        int folds = Math.min(3, n / 2);
        double totalScore = 0d;
        int validFolds = 0;
        for (int f = 0; f < folds; f++) {
            int testSize = Math.max(1, n / (folds + 1));
            int trainEnd = n - testSize * (folds - f);
            if (trainEnd <= 0 || trainEnd >= n) {
                continue;
            }
            int actualTestSize = Math.min(testSize, n - trainEnd);
            if (actualTestSize <= 0) {
                continue;
            }
            List<Double> train = new ArrayList<>(history.subList(0, trainEnd));
            List<Double> test = new ArrayList<>(history.subList(trainEnd, trainEnd + actualTestSize));
            List<Double> fcast = generateForecasts(model, train, params, windowSize, alpha, actualTestSize);
            Map<String, Double> metrics = calculateAccuracyMetrics(test, fcast);
            totalScore += metrics.get("accuracyScore");
            validFolds++;
        }
        return validFolds == 0 ? 0d : totalScore / validFolds;
    }

    /**
     * 移动平均预测: 取末尾 windowSize 个点的均值作为下一期预测, 多步预测将预测值追加到序列继续滚动。
     *
     * @param history    历史数据
     * @param windowSize 移动平均窗口
     * @param periods    预测周期数
     * @return 预测值列表
     */
    private List<Double> calculateMovingAverage(List<Double> history, int windowSize, int periods) {
        List<Double> forecasts = new ArrayList<>(periods);
        if (history == null || history.isEmpty()) {
            for (int i = 0; i < periods; i++) {
                forecasts.add(0d);
            }
            return forecasts;
        }
        int window = windowSize;
        if (window <= 0 || window > history.size()) {
            window = Math.min(3, history.size());
        }
        if (window <= 0) {
            window = 1;
        }
        List<Double> working = new ArrayList<>(history);
        for (int i = 0; i < periods; i++) {
            double sum = 0d;
            int start = working.size() - window;
            for (int j = start; j < working.size(); j++) {
                sum += working.get(j);
            }
            double f = sum / window;
            forecasts.add(f);
            working.add(f);
        }
        return forecasts;
    }

    /**
     * 指数平滑预测: s[t] = alpha * x[t] + (1-alpha) * s[t-1], 未来各期预测取末尾平滑值 (平推)。
     *
     * @param history 历史数据
     * @param alpha   平滑系数 (0-1)
     * @param periods 预测周期数
     * @return 预测值列表
     */
    private List<Double> calculateExponentialSmoothing(List<Double> history, double alpha, int periods) {
        List<Double> forecasts = new ArrayList<>(periods);
        if (history == null || history.isEmpty()) {
            for (int i = 0; i < periods; i++) {
                forecasts.add(0d);
            }
            return forecasts;
        }
        double a = Math.min(1d, Math.max(0d, alpha));
        double s = history.get(0);
        for (int t = 1; t < history.size(); t++) {
            s = a * history.get(t) + (1 - a) * s;
        }
        for (int i = 0; i < periods; i++) {
            forecasts.add(s);
        }
        return forecasts;
    }

    /**
     * 计算准确度指标: MAPE / MAE / RMSE / R2 / accuracyScore。
     * <p>accuracyScore = max(0, 1 - MAPE/100)。</p>
     *
     * @param actuals   实际值列表
     * @param forecasts 预测值列表
     * @return 指标 Map
     */
    Map<String, Double> calculateAccuracyMetrics(List<Double> actuals, List<Double> forecasts) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        if (actuals == null || forecasts == null || actuals.isEmpty() || forecasts.isEmpty()) {
            metrics.put("mape", 0d);
            metrics.put("mae", 0d);
            metrics.put("rmse", 0d);
            metrics.put("r2", 0d);
            metrics.put("accuracyScore", 0d);
            return metrics;
        }
        int n = Math.min(actuals.size(), forecasts.size());
        if (n == 0) {
            metrics.put("mape", 0d);
            metrics.put("mae", 0d);
            metrics.put("rmse", 0d);
            metrics.put("r2", 0d);
            metrics.put("accuracyScore", 0d);
            return metrics;
        }
        double sumAbs = 0d;
        double sumSq = 0d;
        double sumAbsPct = 0d;
        int mapeCount = 0;
        double sumActual = 0d;
        for (int i = 0; i < n; i++) {
            double a = actuals.get(i);
            double f = forecasts.get(i);
            double err = a - f;
            sumAbs += Math.abs(err);
            sumSq += err * err;
            sumActual += a;
            if (Math.abs(a) > 1e-9d) {
                sumAbsPct += Math.abs(err / a);
                mapeCount++;
            }
        }
        double mae = sumAbs / n;
        double rmse = Math.sqrt(sumSq / n);
        double mape = mapeCount > 0 ? (sumAbsPct / mapeCount) * 100d : 0d;
        double meanActual = sumActual / n;
        double ssTot = 0d;
        double ssRes = 0d;
        for (int i = 0; i < n; i++) {
            double a = actuals.get(i);
            double f = forecasts.get(i);
            ssTot += Math.pow(a - meanActual, 2);
            ssRes += Math.pow(a - f, 2);
        }
        double r2 = ssTot > 0 ? 1 - ssRes / ssTot : 0d;
        if (r2 < 0) {
            r2 = 0d;
        }
        double accuracyScore = 1 - mape / 100d;
        if (accuracyScore < 0) {
            accuracyScore = 0d;
        }
        metrics.put("mape", mape);
        metrics.put("mae", mae);
        metrics.put("rmse", rmse);
        metrics.put("r2", r2);
        metrics.put("accuracyScore", accuracyScore);
        return metrics;
    }

    // ============================================================
    // 私有: 工具方法
    // ============================================================

    /**
     * 解析 JSON 文本为 Map, 解析失败返回空 Map。
     *
     * @param json JSON 文本
     * @return Map
     */
    Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return parsed != null ? parsed : new LinkedHashMap<>();
        } catch (Exception e) {
            log.warn("解析 JSON 失败: {}", json, e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 从 Map 取 double 值, 缺省返回默认值。
     *
     * @param map Map
     * @param key 键
     * @param def 默认值
     * @return 值
     */
    double getDouble(Map<String, Object> map, String key, double def) {
        if (map == null) {
            return def;
        }
        Object v = map.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * 从 Map 取 int 值, 缺省返回默认值。
     *
     * @param map Map
     * @param key 键
     * @param def 默认值
     * @return 值
     */
    int getInt(Map<String, Object> map, String key, int def) {
        if (map == null) {
            return def;
        }
        Object v = map.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * 粒度转 ChronoUnit (用于训练数据起始日期估算)。
     *
     * @param granularity 粒度
     * @return ChronoUnit
     */
    private ChronoUnit granularityToChronoUnit(String granularity) {
        if (granularity == null) {
            return ChronoUnit.MONTHS;
        }
        switch (granularity) {
            case "DAILY":
                return ChronoUnit.DAYS;
            case "WEEKLY":
                return ChronoUnit.WEEKS;
            case "QUARTERLY":
                return ChronoUnit.MONTHS;
            case "MONTHLY":
            default:
                return ChronoUnit.MONTHS;
        }
    }
}
