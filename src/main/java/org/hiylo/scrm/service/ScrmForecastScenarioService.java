/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastScenarioService.java
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

import org.hiylo.scrm.dto.ScrmForecastRunDto;
import org.hiylo.scrm.dto.ScrmForecastScenarioDto;
import org.hiylo.scrm.entity.ScrmForecastModelEntity;
import org.hiylo.scrm.entity.ScrmForecastResultEntity;
import org.hiylo.scrm.entity.ScrmForecastScenarioEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmForecastResultRepository;
import org.hiylo.scrm.repository.ScrmForecastScenarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SCRM 销售预测场景管理服务。
 * <p>
 * 承载预测场景管理子域: 场景增删改查、运行 / 重跑 / 结果查询、审批、归档、What-If 分析
 * 与多场景对比。运行流程: 加载模型 → 获取数据 → 应用参数与调整因子 → 生成预测 →
 * 计算置信区间 → 持久化结果 → 回写场景摘要。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmForecastScenarioService {

    // ==================== 默认值常量 ====================

    /** 默认场景类型 */
    private static final String DEFAULT_SCENARIO_TYPE = "BASELINE";
    /** 默认场景状态 */
    private static final String DEFAULT_SCENARIO_STATUS = "DRAFT";
    /** 默认置信水平 */
    private static final double DEFAULT_CONFIDENCE_LEVEL = 0.95d;
    /** What-If 场景类型 */
    private static final String SCENARIO_TYPE_WHAT_IF = "WHAT_IF";

    // ==================== 合法枚举值 ====================

    /** 合法的场景类型 */
    private static final List<String> VALID_SCENARIO_TYPES = List.of(
            "BASELINE", "OPTIMISTIC", "PESSIMISTIC", "WHAT_IF", "TARGET", "STRESS_TEST");
    /** 合法的场景状态 */
    private static final List<String> VALID_SCENARIO_STATUSES = List.of(
            "DRAFT", "RUNNING", "COMPLETED", "FAILED", "ARCHIVED");

    // ==================== 依赖注入 ====================

    /** 预测场景数据访问层 */
    private final ScrmForecastScenarioRepository scenarioRepository;
    /** 预测结果数据访问层 */
    private final ScrmForecastResultRepository resultRepository;
    /** JSON 解析器 */
    private final ObjectMapper objectMapper;
    /** 预测模型子域服务 (共享查找 / 训练数据 / 预测算法) */
    private final ScrmForecastModelService modelService;

    // ============================================================
    // 场景管理
    // ============================================================

    /**
     * 创建预测场景。
     *
     * @param dto 场景参数
     * @return 创建后的场景
     * @throws ScrmException 参数非法 / 模型不存在 / 编码重复
     */
    @Transactional
    public ScrmForecastScenarioEntity createScenario(ScrmForecastScenarioDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("场景参数不能为空");
        }
        validateScenarioEnums(dto, false);
        ScrmForecastModelEntity model = modelService.findModelOrThrow(dto.getModelId());
        if (scenarioRepository.countByScenarioCode(dto.getScenarioCode()) > 0) {
            throw ScrmException.conflict("场景编码已存在: " + dto.getScenarioCode());
        }
        if (dto.getTargetStartDate() != null && dto.getTargetEndDate() != null && dto.getTargetEndDate().isBefore(dto.getTargetStartDate())) {
            throw ScrmException.badRequest("目标结束日期不能早于开始日期");
        }
        ScrmForecastScenarioEntity entity = new ScrmForecastScenarioEntity();
        entity.setScenarioName(dto.getScenarioName());
        entity.setScenarioCode(dto.getScenarioCode());
        entity.setDescription(dto.getDescription());
        entity.setModelId(model.getId());
        entity.setModelName(model.getModelName());
        entity.setScenarioType(dto.getScenarioType() != null ? dto.getScenarioType() : DEFAULT_SCENARIO_TYPE);
        entity.setTargetPeriod(dto.getTargetPeriod());
        entity.setTargetStartDate(dto.getTargetStartDate());
        entity.setTargetEndDate(dto.getTargetEndDate());
        entity.setGranularity(dto.getGranularity() != null ? dto.getGranularity() : ScrmForecastModelService.DEFAULT_GRANULARITY);
        entity.setAssumptions(dto.getAssumptions());
        entity.setInputParameters(dto.getInputParameters());
        entity.setAdjustmentFactors(dto.getAdjustmentFactors());
        entity.setSegments(dto.getSegments());
        entity.setProducts(dto.getProducts());
        entity.setChannels(dto.getChannels());
        entity.setRegions(dto.getRegions());
        entity.setConfidenceLevel(dto.getConfidenceLevel() != null ? dto.getConfidenceLevel() :
                DEFAULT_CONFIDENCE_LEVEL);
        entity.setRiskFactors(dto.getRiskFactors());
        entity.setOpportunities(dto.getOpportunities());
        entity.setRecommendations(dto.getRecommendations());
        entity.setSharedWith(dto.getSharedWith());
        entity.setTags(dto.getTags());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : modelService.currentOperator());
        entity = scenarioRepository.save(entity);
        log.info("创建预测场景: id={}, code={}, modelId={}", entity.getId(), entity.getScenarioCode(), entity.getModelId());
        return entity;
    }

    /**
     * 更新预测场景。
     *
     * @param id  场景 ID
     * @param dto 场景参数
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 参数非法 / 编码重复
     */
    @Transactional
    public ScrmForecastScenarioEntity updateScenario(Long id, ScrmForecastScenarioDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("场景参数不能为空");
        }
        validateScenarioEnums(dto, true);
        ScrmForecastScenarioEntity entity = findScenarioOrThrow(id);
        if (!Objects.equals(entity.getScenarioCode(), dto.getScenarioCode()) && scenarioRepository.countByScenarioCode(dto.getScenarioCode()) > 0) {
            throw ScrmException.conflict("场景编码已存在: " + dto.getScenarioCode());
        }
        if (dto.getModelId() != null && !Objects.equals(dto.getModelId(), entity.getModelId())) {
            ScrmForecastModelEntity model = modelService.findModelOrThrow(dto.getModelId());
            entity.setModelId(model.getId());
            entity.setModelName(model.getModelName());
        }
        entity.setScenarioName(dto.getScenarioName());
        entity.setScenarioCode(dto.getScenarioCode());
        entity.setDescription(dto.getDescription());
        if (dto.getScenarioType() != null) {
            entity.setScenarioType(dto.getScenarioType());
        }
        entity.setTargetPeriod(dto.getTargetPeriod());
        entity.setTargetStartDate(dto.getTargetStartDate());
        entity.setTargetEndDate(dto.getTargetEndDate());
        if (dto.getGranularity() != null) {
            entity.setGranularity(dto.getGranularity());
        }
        entity.setAssumptions(dto.getAssumptions());
        entity.setInputParameters(dto.getInputParameters());
        entity.setAdjustmentFactors(dto.getAdjustmentFactors());
        entity.setSegments(dto.getSegments());
        entity.setProducts(dto.getProducts());
        entity.setChannels(dto.getChannels());
        entity.setRegions(dto.getRegions());
        if (dto.getConfidenceLevel() != null) {
            entity.setConfidenceLevel(dto.getConfidenceLevel());
        }
        entity.setRiskFactors(dto.getRiskFactors());
        entity.setOpportunities(dto.getOpportunities());
        entity.setRecommendations(dto.getRecommendations());
        entity.setSharedWith(dto.getSharedWith());
        entity.setTags(dto.getTags());
        entity = scenarioRepository.save(entity);
        log.info("更新预测场景: id={}, code={}", entity.getId(), entity.getScenarioCode());
        return entity;
    }

    /**
     * 删除预测场景 (连同结果一并删除)。
     *
     * @param id 场景 ID
     * @throws ScrmException 场景不存在
     */
    @Transactional
    public void deleteScenario(Long id) throws ScrmException {
        ScrmForecastScenarioEntity entity = findScenarioOrThrow(id);
        resultRepository.deleteByScenarioId(id);
        resultRepository.flush();
        scenarioRepository.delete(entity);
        log.info("删除预测场景: id={}, code={}", id, entity.getScenarioCode());
    }

    /**
     * 查询场景详情。
     *
     * @param id 场景 ID
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    @Transactional(readOnly = true)
    public ScrmForecastScenarioEntity getScenario(Long id) throws ScrmException {
        return findScenarioOrThrow(id);
    }

    /**
     * 按场景编码查询场景。
     *
     * @param code 场景编码
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    @Transactional(readOnly = true)
    public ScrmForecastScenarioEntity getScenarioByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("场景编码不能为空");
        }
        return scenarioRepository.findByScenarioCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "场景不存在: code=" + code));
    }

    /**
     * 分页查询场景列表, 支持按模型 / 场景类型 / 状态 / 目标周期 / 关键字过滤。
     *
     * @param modelId       模型 ID 过滤（可空）
     * @param scenarioType  场景类型过滤（可空）
     * @param status        状态过滤（可空）
     * @param targetPeriod  目标周期过滤（可空）
     * @param keyword       名称/编码关键字（可空）
     * @param pageable      分页参数
     * @return 场景分页结果 (按 updateTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmForecastScenarioEntity> listScenarios(Long modelId, String scenarioType, String status,
                                                            String targetPeriod, String keyword, Pageable pageable) {
        Specification<ScrmForecastScenarioEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (modelId != null) {
                predicates.add(cb.equal(root.get("modelId"), modelId));
            }
            if (scenarioType != null && !scenarioType.isBlank()) {
                predicates.add(cb.equal(root.get("scenarioType"), scenarioType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (targetPeriod != null && !targetPeriod.isBlank()) {
                predicates.add(cb.equal(root.get("targetPeriod"), targetPeriod));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("scenarioName"), like),
                        cb.like(root.get("scenarioCode"), like)));
            }
            query.orderBy(cb.desc(root.get("updateTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return scenarioRepository.findAll(spec, pageable);
    }

    /**
     * 运行预测场景: 加载模型 → 获取数据 → 应用参数与调整因子 → 生成预测 → 计算置信区间 → 持久化结果。
     *
     * @param runDto 运行参数
     * @return 更新后的场景
     * @throws ScrmException 场景/模型不存在 / 模型未训练 / 运行失败
     */
    @Transactional
    public ScrmForecastScenarioEntity runScenario(ScrmForecastRunDto runDto) throws ScrmException {
        if (runDto == null || runDto.getScenarioId() == null) {
            throw ScrmException.badRequest("运行参数不能为空");
        }
        ScrmForecastScenarioEntity scenario = findScenarioOrThrow(runDto.getScenarioId());
        ScrmForecastModelEntity model = modelService.findModelOrThrow(scenario.getModelId());
        if (!Boolean.TRUE.equals(model.getIsTrained())) {
            throw ScrmException.badRequest("关联模型未训练, 无法运行场景: modelId=" + model.getId());
        }
        return executeScenarioRun(scenario, model, runDto.getParameters(),
                runDto.getAdjustments(), runDto.getForecastPeriods());
    }

    /**
     * 重跑场景 (使用场景已保存的参数与调整因子)。
     *
     * @param scenarioId 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 模型未训练 / 运行失败
     */
    @Transactional
    public ScrmForecastScenarioEntity rerunScenario(Long scenarioId) throws ScrmException {
        ScrmForecastScenarioEntity scenario = findScenarioOrThrow(scenarioId);
        ScrmForecastModelEntity model = modelService.findModelOrThrow(scenario.getModelId());
        if (!Boolean.TRUE.equals(model.getIsTrained())) {
            throw ScrmException.badRequest("关联模型未训练, 无法运行场景: modelId=" + model.getId());
        }
        List<ScrmForecastRunDto.AdjustmentFactor> adjustments = parseAdjustmentFactors(scenario.getAdjustmentFactors());
        return executeScenarioRun(scenario, model, scenario.getInputParameters(), adjustments, null);
    }

    /**
     * 查询场景预测结果 (按预测日期升序分页)。
     *
     * @param id       场景 ID
     * @param pageable 分页参数
     * @return 预测结果分页
     * @throws ScrmException 场景不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmForecastResultEntity> getScenarioResults(Long id, Pageable pageable) throws ScrmException {
        findScenarioOrThrow(id);
        return resultRepository.findByScenarioIdOrderByForecastDateAsc(
                 id, pageable);
    }

    /**
     * 审批场景。
     *
     * @param scenarioId 场景 ID
     * @param approvedBy 审批人 (可空, 缺省取当前用户)
     * @return 更新后的场景
     * @throws ScrmException 场景不存在 / 状态非法
     */
    @Transactional
    public ScrmForecastScenarioEntity approveScenario(Long scenarioId, String approvedBy) throws ScrmException {
        ScrmForecastScenarioEntity scenario = findScenarioOrThrow(scenarioId);
        if (!ScrmForecastModelService.STATUS_COMPLETED.equals(scenario.getStatus())) {
            throw ScrmException.badRequest("仅已完成的场景可审批: status=" + scenario.getStatus());
        }
        scenario.setIsApproved(Boolean.TRUE);
        scenario.setApprovedBy(approvedBy != null ? approvedBy : modelService.currentOperator());
        scenario.setApprovedAt(LocalDateTime.now());
        scenario = scenarioRepository.save(scenario);
        log.info("审批预测场景: id={}, approvedBy={}", scenarioId, scenario.getApprovedBy());
        return scenario;
    }

    /**
     * 归档场景。
     *
     * @param scenarioId 场景 ID
     * @return 更新后的场景
     * @throws ScrmException 场景不存在
     */
    @Transactional
    public ScrmForecastScenarioEntity archiveScenario(Long scenarioId) throws ScrmException {
        ScrmForecastScenarioEntity scenario = findScenarioOrThrow(scenarioId);
        scenario.setStatus(ScrmForecastModelService.STATUS_ARCHIVED);
        scenario = scenarioRepository.save(scenario);
        log.info("归档预测场景: id={}", scenarioId);
        return scenario;
    }

    /**
     * What-If 分析: 基于入参创建一个 WHAT_IF 类型场景并立即运行, 返回场景与预测结果。
     *
     * @param dto 场景参数 (scenarioType 可空, 强制为 WHAT_IF)
     * @return What-If 分析结果 {scenario, results}
     * @throws ScrmException 参数非法 / 模型不存在 / 模型未训练
     */
    @Transactional
    public Map<String, Object> whatIf(ScrmForecastScenarioDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("What-If 参数不能为空");
        }
        dto.setScenarioType(SCENARIO_TYPE_WHAT_IF);
        ScrmForecastScenarioEntity scenario = createScenario(dto);
        ScrmForecastModelEntity model = modelService.findModelOrThrow(scenario.getModelId());
        if (!Boolean.TRUE.equals(model.getIsTrained())) {
            throw ScrmException.badRequest("关联模型未训练, 无法运行 What-If: modelId=" + model.getId());
        }
        List<ScrmForecastRunDto.AdjustmentFactor> adjustments = parseAdjustmentFactors(scenario.getAdjustmentFactors());
        scenario = executeScenarioRun(scenario, model, scenario.getInputParameters(), adjustments, null);
        List<ScrmForecastResultEntity> results = resultRepository
                .findByScenarioIdOrderByForecastDateAsc(scenario.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", scenario);
        result.put("results", results);
        return result;
    }

    /**
     * 对比多个场景的预测结果。
     *
     * @param scenarioIds 场景 ID 列表
     * @return 对比结果 {scenarios, bestScenarioId}
     * @throws ScrmException 场景不存在 / 列表为空
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareScenarios(List<Long> scenarioIds) throws ScrmException {
        if (scenarioIds == null || scenarioIds.isEmpty()) {
            throw ScrmException.badRequest("对比场景 ID 列表不能为空");
        }
        List<Map<String, Object>> scenarios = new ArrayList<>();
        Long bestId = null;
        double bestAccuracy = -1d;
        for (Long id : scenarioIds) {
            ScrmForecastScenarioEntity s = findScenarioOrThrow(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scenarioId", s.getId());
            m.put("scenarioCode", s.getScenarioCode());
            m.put("scenarioName", s.getScenarioName());
            m.put("scenarioType", s.getScenarioType());
            m.put("modelId", s.getModelId());
            m.put("modelName", s.getModelName());
            m.put("status", s.getStatus());
            m.put("targetPeriod", s.getTargetPeriod());
            m.put("totalForecastValue", s.getTotalForecastValue());
            m.put("confidenceLowerBound", s.getConfidenceLowerBound());
            m.put("confidenceUpperBound", s.getConfidenceUpperBound());
            m.put("confidenceLevel", s.getConfidenceLevel());
            m.put("accuracyEstimate", s.getAccuracyEstimate());
            m.put("isApproved", s.getIsApproved());
            m.put("runCompletedAt", s.getRunCompletedAt());
            scenarios.add(m);
            double acc = s.getAccuracyEstimate() != null ? s.getAccuracyEstimate() : 0d;
            if (acc > bestAccuracy) {
                bestAccuracy = acc;
                bestId = s.getId();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarios", scenarios);
        result.put("bestScenarioId", bestId);
        result.put("bestAccuracyEstimate", bestAccuracy < 0 ? 0d : bestAccuracy);
        return result;
    }

    // ============================================================
    // 包内: 查找与校验
    // ============================================================

    /**
     * 按主键查询场景, 不存在抛异常。
     *
     * @param id 场景 ID
     * @return 场景实体
     * @throws ScrmException 场景不存在
     */
    ScrmForecastScenarioEntity findScenarioOrThrow(Long id) throws ScrmException {
        ScrmForecastScenarioEntity entity = scenarioRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "预测场景不存在: id=" + id));
        return entity;
    }

    /**
     * 校验场景 DTO 枚举字段。
     *
     * @param dto      场景参数
     * @param isUpdate 是否更新场景
     * @throws ScrmException 枚举非法
     */
    private void validateScenarioEnums(ScrmForecastScenarioDto dto, boolean isUpdate) throws ScrmException {
        if (dto.getScenarioType() != null && !VALID_SCENARIO_TYPES.contains(dto.getScenarioType())) {
            throw ScrmException.badRequest("场景类型非法: " + dto.getScenarioType() + ", 仅支持 " + VALID_SCENARIO_TYPES);
        }
        if (dto.getGranularity() != null && !ScrmForecastModelService.VALID_GRANULARITIES.contains(dto.getGranularity())) {
            throw ScrmException.badRequest("粒度非法: " + dto.getGranularity() + ", 仅支持 " + ScrmForecastModelService.VALID_GRANULARITIES);
        }
        if (isUpdate && dto.getStatus() != null && !VALID_SCENARIO_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest("场景状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_SCENARIO_STATUSES);
        }
    }

    // ============================================================
    // 私有: 场景运行核心
    // ============================================================

    /**
     * 场景运行核心逻辑: 加载数据 → 生成预测 → 应用调整因子 → 计算置信区间 → 持久化结果 → 回写场景。
     *
     * @param scenario       场景实体
     * @param model          模型实体
     * @param overrideParams 运行时覆盖的模型参数 JSON (可空)
     * @param adjustments    调整因子列表 (可空)
     * @param overridePeriods 预测周期数覆盖 (可空)
     * @return 更新后的场景
     * @throws ScrmException 运行失败
     */
    private ScrmForecastScenarioEntity executeScenarioRun(ScrmForecastScenarioEntity scenario,
                                                          ScrmForecastModelEntity model,
                                                          String overrideParams,
                                                          List<ScrmForecastRunDto.AdjustmentFactor> adjustments,
                                                          Integer overridePeriods) throws ScrmException {
        long startMs = System.currentTimeMillis();
        scenario.setStatus(ScrmForecastModelService.STATUS_RUNNING);
        scenario.setRunStartedAt(LocalDateTime.now());
        scenario.setRunCompletedAt(null);
        scenario = scenarioRepository.save(scenario);
        try {
            // 1. 加载模型参数 (运行时可覆盖)
            Map<String, Object> modelParams = modelService.parseJsonToMap(model.getParameters());
            Map<String, Object> overrideMap = modelService.parseJsonToMap(overrideParams);
            if (!overrideMap.isEmpty()) {
                modelParams.putAll(overrideMap);
            }
            int windowSize = modelService.getInt(modelParams, "windowSize",
                    model.getLookbackPeriods() != null ? Math.min(model.getLookbackPeriods(), ScrmForecastModelService.DEFAULT_WINDOW_SIZE) :
                            ScrmForecastModelService.DEFAULT_WINDOW_SIZE);
            double alpha = modelService.getDouble(modelParams, "alpha", ScrmForecastModelService.DEFAULT_ALPHA);
            int periods = overridePeriods != null ? overridePeriods
                    : (model.getForecastPeriods() != null ? model.getForecastPeriods() : 3);

            // 2. 获取数据
            List<Double> history = modelService.loadTrainingData(model);

            // 3. 生成预测
            List<Double> forecasts = modelService.generateForecasts(model, history, modelParams, windowSize, alpha, periods);

            // 4. 应用调整因子
            Map<String, Object> inputParams = modelService.parseJsonToMap(scenario.getInputParameters());
            forecasts = applyAdjustments(forecasts, adjustments, inputParams);

            // 5. 计算置信区间
            double rmse = model.getLastRmse() != null ? model.getLastRmse() : 0d;
            double confidenceLevel = scenario.getConfidenceLevel() != null
                    ? scenario.getConfidenceLevel() : DEFAULT_CONFIDENCE_LEVEL;

            // 6. 清理旧结果
            resultRepository.deleteByScenarioId(scenario.getId());
            resultRepository.flush();

            // 7. 持久化新结果
            LocalDate startDate = scenario.getTargetStartDate() != null
                    ? scenario.getTargetStartDate() : LocalDate.now();
            double totalForecast = 0d;
            double lowerSum = 0d;
            double upperSum = 0d;
            List<ScrmForecastResultEntity> toSave = new ArrayList<>();
            for (int i = 0; i < periods; i++) {
                LocalDate date = startDate;
                for (int j = 0; j < i; j++) {
                    date = nextPeriod(date, scenario.getGranularity());
                }
                double f = forecasts.get(i);
                double[] ci = calculateConfidenceInterval(f, rmse, confidenceLevel);
                ScrmForecastResultEntity r = new ScrmForecastResultEntity();
                r.setScenarioId(scenario.getId());
                r.setScenarioName(scenario.getScenarioName());
                r.setModelId(model.getId());
                r.setModelName(model.getModelName());
                r.setForecastDate(date);
                r.setPeriodLabel(periodLabel(date, scenario.getGranularity()));
                r.setPeriodIndex(i);
                r.setForecastValue(f);
                r.setConfidenceLower(ci[0]);
                r.setConfidenceUpper(ci[1]);
                r.setConfidenceRange(ci[2]);
                r.setIsActual(Boolean.FALSE);
                r.setCreatedBy(modelService.currentOperator());
                toSave.add(r);
                totalForecast += f;
                lowerSum += ci[0];
                upperSum += ci[1];
            }
            resultRepository.saveAll(toSave);

            // 8. 回写场景摘要
            scenario.setStatus(ScrmForecastModelService.STATUS_COMPLETED);
            scenario.setRunCompletedAt(LocalDateTime.now());
            scenario.setRunDurationMs((int) (System.currentTimeMillis() - startMs));
            scenario.setTotalForecastValue(totalForecast);
            scenario.setConfidenceLevel(confidenceLevel);
            scenario.setConfidenceLowerBound(periods == 0 ? 0d : lowerSum / periods);
            scenario.setConfidenceUpperBound(periods == 0 ? 0d : upperSum / periods);
            scenario.setAccuracyEstimate(model.getLastAccuracyScore());
            scenario.setModelName(model.getModelName());
            scenario = scenarioRepository.save(scenario);
            log.info("运行预测场景完成: scenarioId={}, periods={}, total={}",
                    scenario.getId(), periods, totalForecast);
            return scenario;
        } catch (ScrmException e) {
            markScenarioFailed(scenario, startMs);
            throw e;
        } catch (Exception e) {
            markScenarioFailed(scenario, startMs);
            log.error("运行预测场景失败: scenarioId={}", scenario.getId(), e);
            throw new ScrmException(ScrmExceptionConstants.INTERNAL_ERROR, "运行场景失败: " + e.getMessage(), e);
        }
    }

    /**
     * 标记场景为失败状态。
     *
     * @param scenario 场景实体
     * @param startMs  开始时间戳
     */
    private void markScenarioFailed(ScrmForecastScenarioEntity scenario, long startMs) {
        try {
            scenario.setStatus(ScrmForecastModelService.STATUS_FAILED);
            scenario.setRunCompletedAt(LocalDateTime.now());
            scenario.setRunDurationMs((int) (System.currentTimeMillis() - startMs));
            scenarioRepository.save(scenario);
        } catch (Exception ex) {
            log.warn("标记场景失败状态异常: scenarioId={}", scenario.getId(), ex);
        }
    }

    /**
     * 应用调整因子到预测序列。
     * <p>growthRate 作为每期递增的趋势分量; seasonalityFactor / marketCondition 作为乘数;
     * 其他因子按 (1 + value) 乘数累乘。</p>
     *
     * @param forecasts    原始预测值
     * @param adjustments  调整因子列表 (可空)
     * @param inputParams  场景输入参数 (可空)
     * @return 调整后预测值
     */
    private List<Double> applyAdjustments(List<Double> forecasts,
                                          List<ScrmForecastRunDto.AdjustmentFactor> adjustments,
                                          Map<String, Object> inputParams) {
        if (forecasts == null || forecasts.isEmpty()) {
            return forecasts;
        }
        double growthRate = 0d;
        double seasonalityFactor = 1d;
        double marketFactor = 1d;
        double otherFactor = 1d;
        if (adjustments != null) {
            for (ScrmForecastRunDto.AdjustmentFactor af : adjustments) {
                if (af == null || af.getFactor() == null || af.getValue() == null) {
                    continue;
                }
                String name = af.getFactor();
                double v = af.getValue();
                switch (name) {
                    case "growthRate":
                        growthRate += v;
                        break;
                    case "seasonalityFactor":
                        seasonalityFactor *= v;
                        break;
                    case "marketCondition":
                        marketFactor *= v;
                        break;
                    default:
                        otherFactor *= (1 + v);
                        break;
                }
            }
        }
        if (inputParams != null) {
            growthRate += modelService.getDouble(inputParams, "growthRate", 0d);
            seasonalityFactor *= modelService.getDouble(inputParams, "seasonalityFactor", 1d);
            marketFactor *= modelService.getDouble(inputParams, "marketCondition", 1d);
        }
        List<Double> adjusted = new ArrayList<>(forecasts.size());
        for (int i = 0; i < forecasts.size(); i++) {
            double trendComp = 1 + growthRate * (i + 1);
            adjusted.add(forecasts.get(i) * trendComp * seasonalityFactor * marketFactor * otherFactor);
        }
        return adjusted;
    }

    /**
     * 计算置信区间: forecast ± z * rmse, 返回 [下界, 上界, 区间宽度]。
     *
     * @param forecast         预测值
     * @param rmse             均方根误差
     * @param confidenceLevel  置信水平 (0-1)
     * @return 数组 [lower, upper, range]
     */
    private double[] calculateConfidenceInterval(double forecast, double rmse, double confidenceLevel) {
        double z = zValue(confidenceLevel);
        double margin = z * (rmse > 0 ? rmse : Math.abs(forecast) * 0.1d);
        double lower = forecast - margin;
        double upper = forecast + margin;
        return new double[]{lower, upper, upper - lower};
    }

    /**
     * 由置信水平查正态分布 z 值 (常用水平查表, 其余按 0.95)。
     *
     * @param confidenceLevel 置信水平
     * @return z 值
     */
    private double zValue(double confidenceLevel) {
        if (confidenceLevel >= 0.99d) {
            return 2.576d;
        }
        if (confidenceLevel >= 0.95d) {
            return 1.96d;
        }
        if (confidenceLevel >= 0.90d) {
            return 1.645d;
        }
        return 1.96d;
    }

    /**
     * 解析调整因子 JSON 数组为调整因子列表。
     *
     * @param json 调整因子 JSON
     * @return 调整因子列表
     */
    private List<ScrmForecastRunDto.AdjustmentFactor> parseAdjustmentFactors(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<ScrmForecastRunDto.AdjustmentFactor> parsed = objectMapper.readValue(json,
                    new TypeReference<List<ScrmForecastRunDto.AdjustmentFactor>>() {
                    });
            return parsed != null ? parsed : new ArrayList<>();
        } catch (Exception e) {
            log.warn("解析调整因子 JSON 失败: {}", json, e);
            return new ArrayList<>();
        }
    }

    /**
     * 按粒度推进日期到下一周期。
     *
     * @param date       当前日期
     * @param granularity 粒度
     * @return 下一周期日期
     */
    private LocalDate nextPeriod(LocalDate date, String granularity) {
        if (date == null) {
            return LocalDate.now();
        }
        if (granularity == null) {
            return date.plusMonths(1);
        }
        switch (granularity) {
            case "DAILY":
                return date.plusDays(1);
            case "WEEKLY":
                return date.plusWeeks(1);
            case "QUARTERLY":
                return date.plusMonths(3);
            case "MONTHLY":
            default:
                return date.plusMonths(1);
        }
    }

    /**
     * 按粒度生成周期标签 (如 2026-08 / 2026-Q3 / 2026-W32 / 2026-08-04)。
     *
     * @param date       日期
     * @param granularity 粒度
     * @return 周期标签
     */
    private String periodLabel(LocalDate date, String granularity) {
        if (date == null) {
            return "";
        }
        if (granularity == null) {
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        switch (granularity) {
            case "DAILY":
                return date.format(DateTimeFormatter.ISO_DATE);
            case "WEEKLY":
                return date.format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
            case "QUARTERLY":
                return date.getYear() + "-Q" + ((date.getMonthValue() - 1) / 3 + 1);
            case "MONTHLY":
            default:
                return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
    }
}
