/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadScoringCalculationService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmLeadScoreResultDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmLeadScoreEntity;
import org.hiylo.scrm.entity.ScrmLeadScoringModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmLeadScoreRepository;
import org.hiylo.scrm.repository.ScrmLeadScoringModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 销售线索评分计算服务。
 * <p>
 * 承载评分计算子域: 单客户 / 批量 / 全量 / 单条重算, 评分记录查询, 转化概率预测与等级判定。
 * 同时托管评分共享常量 (等级 / 趋势 / 默认阈值与标签) 与辅助方法 (客户上下文构建、规则评估、
 * 等级与趋势判定、JSON 解析、数值转换、分页兜底), 供分配统计兄弟类以 package 级访问复用。
 * </p>
 * <p>
 * 评分计算流程 ({@link #calculateScore}):
 * <ol>
 *   <li>校验客户存在且归属当前账号</li>
 *   <li>解析模型 dimensions JSON, 遍历每个维度</li>
 *   <li>对每个维度的 fields 评估规则 (eq/gt/lt/between/in/contains), 累加 score</li>
 *   <li>应用维度 maxScore 上限, 加权求和得到总分</li>
 *   <li>应用模型 totalMaxScore 总分上限</li>
 *   <li>根据 gradeThresholds 或默认阈值确定等级</li>
 *   <li>计算转化概率 (基于分数百分比与命中维度数)</li>
 *   <li>更新评分记录 (含趋势对比: UP/STABLE/DOWN)</li>
 * </ol>
 * 评分计算与转化预测均为模拟实现, 基于规则简单匹配, 不依赖外部 ML 服务。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmLeadScoringCalculationService {

    // ==================== 等级常量 ====================

    /** 等级: A+ */
    static final String GRADE_A_PLUS = "A_PLUS";
    /** 等级: 超级热线索 */
    static final String GRADE_SUPER_HOT = "SUPER_HOT";
    /** 等级: 热线索 */
    static final String GRADE_HOT = "HOT";
    /** 等级: 暖线索 */
    static final String GRADE_WARM = "WARM";
    /** 等级: 冷线索 */
    static final String GRADE_COLD = "COLD";
    /** 等级: 死线索 */
    static final String GRADE_DEAD = "DEAD";
    /** 等级: A */
    static final String GRADE_A = "A";
    /** 等级: B */
    static final String GRADE_B = "B";
    /** 等级: C */
    static final String GRADE_C = "C";
    /** 等级: D */
    static final String GRADE_D = "D";
    /** 等级: E */
    static final String GRADE_E = "E";

    // ==================== 趋势常量 ====================

    /** 趋势: 上升 */
    private static final String TREND_UP = "UP";
    /** 趋势: 持平 */
    private static final String TREND_STABLE = "STABLE";
    /** 趋势: 下降 */
    private static final String TREND_DOWN = "DOWN";

    /** 趋势变化阈值 */
    private static final double TREND_THRESHOLD = 0.5;

    /** 默认等级阈值 (按分数百分比映射) */
    static final double[][] DEFAULT_GRADE_THRESHOLDS = {
            {0.85, 1.00},   // A_PLUS
            {0.70, 0.85},   // SUPER_HOT
            {0.55, 0.70},   // HOT
            {0.40, 0.55},   // WARM
            {0.20, 0.40},   // COLD
            {0.00, 0.20}    // DEAD
    };

    /** 默认等级标签 */
    static final Map<String, String> DEFAULT_GRADE_LABELS;

    static {
        DEFAULT_GRADE_LABELS = new LinkedHashMap<>();
        DEFAULT_GRADE_LABELS.put(GRADE_A_PLUS, "顶级线索");
        DEFAULT_GRADE_LABELS.put(GRADE_SUPER_HOT, "超级热线索");
        DEFAULT_GRADE_LABELS.put(GRADE_HOT, "热线索");
        DEFAULT_GRADE_LABELS.put(GRADE_WARM, "暖线索");
        DEFAULT_GRADE_LABELS.put(GRADE_COLD, "冷线索");
        DEFAULT_GRADE_LABELS.put(GRADE_DEAD, "无效线索");
    }

    /** 评分模型数据访问层 (递增模型应用统计) */
    private final ScrmLeadScoringModelRepository modelRepository;

    /** 评分记录数据访问层 */
    private final ScrmLeadScoreRepository scoreRepository;

    /** 客户数据访问层 (查询客户属性用于评分计算) */
    private final ScrmCustomerRepository customerRepository;

    /** JSON 解析器 (解析 dimensions / scoringRules / dimensionScores) */
    private final ObjectMapper objectMapper;

    /** 评分模型管理子域服务 (共享按主键查找) */
    private final ScrmLeadScoringModelService modelService;

    // ============================================================
    // 评分计算
    // ============================================================

    /**
     * 计算单客户线索评分 (遍历维度→评估规则→汇总得分→确定等级→计算转化概率)。
     * <p>模拟实现, 基于规则简单匹配。若该客户在该模型下已有评分记录, 则更新 (含趋势对比);
     * 否则新建评分记录。同时增量更新模型应用次数与最近应用时间。</p>
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 评分计算结果
     * @throws ScrmException 客户 / 模型不存在
     */
    @Transactional
    public ScrmLeadScoreResultDto calculateScore(Long customerId, Long modelId) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (modelId == null) {
            throw ScrmException.badRequest("模型 ID 不能为空");
        }
        ScrmCustomerEntity customer = findCustomerOrThrow(customerId);
        ScrmLeadScoringModelEntity model = modelService.findModelOrThrow(modelId);
        // 构建客户上下文 (用于规则评估)
        Map<String, Object> context = buildCustomerContext(customer);
        // 解析模型维度配置
        List<Map<String, Object>> dimensions = parseJsonArray(model.getDimensions());
        double maxScore = ScrmLeadScoringModelService.DEFAULT_TOTAL_MAX_SCORE;
        if (model.getTotalMaxScore() != null) {
            maxScore = model.getTotalMaxScore();
        }
        List<Map<String, Object>> dimensionScores = new ArrayList<>();
        double totalScore = 0;
        int matchedDimensions = 0;
        for (Map<String, Object> dimConfig : dimensions) {
            String dimension = (String) dimConfig.get("dimension");
            double weight = toDouble(dimConfig.get("weight"));
            if (weight <= 0) {
                weight = ScrmLeadDimensionService.DEFAULT_WEIGHT;
            }
            double dimMaxScore = toDouble(dimConfig.get("maxScore"));
            if (dimMaxScore <= 0) {
                dimMaxScore = ScrmLeadDimensionService.DEFAULT_MAX_SCORE;
            }
            List<Map<String, Object>> fields = parseFields(dimConfig.get("fields"));
            double dimScore = 0;
            List<String> details = new ArrayList<>();
            for (Map<String, Object> rule : fields) {
                String field = (String) rule.get("field");
                String operator = (String) rule.get("operator");
                Object value = rule.get("value");
                double score = toDouble(rule.get("score"));
                Object fieldValue = context.get(field);
                if (evaluateCondition(fieldValue, operator, value)) {
                    dimScore += score;
                    details.add(field + " " + operator + " " + value + " => +" + score);
                }
            }
            // 应用维度上限
            dimScore = Math.min(dimScore, dimMaxScore);
            // 加权后计入总分
            double weighted = dimScore * weight;
            totalScore += weighted;
            if (dimScore > 0) {
                matchedDimensions++;
            }
            Map<String, Object> dimResult = new LinkedHashMap<>();
            dimResult.put("dimension", dimension);
            dimResult.put("score", round2(dimScore));
            dimResult.put("maxScore", dimMaxScore);
            dimResult.put("details", String.join("; ", details));
            dimensionScores.add(dimResult);
        }
        // 应用模型总分上限
        totalScore = Math.min(totalScore, maxScore);
        // 确定等级
        double percent = maxScore > 0 ? totalScore / maxScore : 0;
        String grade = determineGrade(totalScore, maxScore, model.getGradeThresholds());
        String gradeLabel = DEFAULT_GRADE_LABELS.getOrDefault(grade, grade);
        // 计算转化概率 (基于分数百分比与命中维度数, 模拟)
        double conversionProbability = predictConversionProbability(percent, matchedDimensions, dimensions.size());
        // 获取或创建评分记录
        ScrmLeadScoreEntity score = scoreRepository
                .findByCustomerIdAndModelId(customerId, modelId)
                .orElseGet(() -> {
                    ScrmLeadScoreEntity s = new ScrmLeadScoreEntity();
                    s.setCustomerId(customerId);
                    s.setModelId(modelId);
                    s.setPreviousScore(0d);
                    return s;
                });
        double previousScore = score.getTotalScore() != null ? score.getTotalScore() : 0;
        score.setCustomerName(customer.getNickname());
        score.setTotalScore(round2(totalScore));
        score.setMaxScore(maxScore);
        score.setScorePercent(round2(percent * 100));
        score.setGrade(grade);
        score.setGradeLabel(gradeLabel);
        score.setDimensionScores(toJson(dimensionScores));
        score.setConversionProbability(round4(conversionProbability));
        score.setPredictedValue(round2(conversionProbability * 1000));
        score.setIsHotLead(isHotLead(grade, percent));
        score.setIsQualified(isQualified(grade, percent));
        score.setLastCalculatedAt(LocalDateTime.now());
        score.setPreviousScore(round2(previousScore));
        applyTrend(score, previousScore, totalScore);
        score = scoreRepository.save(score);
        // 增量更新模型应用统计
        try {
            modelRepository.incrementAppliedCount(modelId, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("更新模型应用统计失败, 忽略: modelId={}, err={}", modelId, e.getMessage());
        }
        log.info("计算线索评分: customerId={}, modelId={}, score={}, grade={}, probability={}",
                customerId, modelId, totalScore, grade, conversionProbability);
        return toResultDto(score);
    }

    /**
     * 批量计算客户线索评分。
     * <p>单个客户失败跳过, 不阻断其他客户。</p>
     *
     * @param modelId     模型 ID
     * @param customerIds 客户 ID 列表
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public Map<String, Integer> batchCalculateScores(Long modelId, List<Long> customerIds) throws ScrmException {
        if (modelId == null) {
            throw ScrmException.badRequest("模型 ID 不能为空");
        }
        if (customerIds == null || customerIds.isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        // 校验模型存在
        modelService.findModelOrThrow(modelId);
        int processed = 0;
        int failed = 0;
        for (Long customerId : customerIds) {
            if (customerId == null) {
                continue;
            }
            try {
                calculateScore(customerId, modelId);
                processed++;
            } catch (Exception e) {
                failed++;
                log.warn("批量计算线索评分失败, 跳过: customerId={}, err={}", customerId, e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", customerIds.size());
        result.put("processed", processed);
        result.put("failed", failed);
        log.info("批量计算线索评分完成: modelId={}, total={}, processed={}, failed={}",
                modelId, customerIds.size(), processed, failed);
        return result;
    }

    /**
     * 计算所有客户评分 (按下所有客户)。
     *
     * @param modelId 模型 ID
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public Map<String, Integer> calculateAllScores(Long modelId) throws ScrmException {
        if (modelId == null) {
            throw ScrmException.badRequest("模型 ID 不能为空");
        }
        modelService.findModelOrThrow(modelId);
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        int processed = 0;
        int failed = 0;
        for (ScrmCustomerEntity customer : customers) {
            try {
                calculateScore(customer.getId(), modelId);
                processed++;
            } catch (Exception e) {
                failed++;
                log.warn("计算线索评分失败, 跳过: customerId={}, err={}", customer.getId(), e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", customers.size());
        result.put("processed", processed);
        result.put("failed", failed);
        log.info("计算所有客户评分完成:, modelId={}, total={}, processed={}, failed={}", modelId, customers.size(), processed, failed);
        return result;
    }

    /**
     * 重新计算指定评分记录 (按评分记录的 customerId 与 modelId 重算)。
     *
     * @param scoreId 评分记录 ID
     * @return 评分计算结果
     * @throws ScrmException 评分记录不存在
     */
    @Transactional
    public ScrmLeadScoreResultDto recalculateScore(Long scoreId) throws ScrmException {
        ScrmLeadScoreEntity score = findScoreOrThrow(scoreId);
        return calculateScore(score.getCustomerId(), score.getModelId());
    }

    /**
     * 查询评分详情。
     *
     * @param scoreId 评分记录 ID
     * @return 评分实体
     * @throws ScrmException 评分记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmLeadScoreEntity getScore(Long scoreId) throws ScrmException {
        return findScoreOrThrow(scoreId);
    }

    /**
     * 按客户与模型查询评分。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 评分实体 (不存在返回 null)
     */
    @Transactional(readOnly = true)
    public ScrmLeadScoreEntity getScoreByCustomer(Long customerId, Long modelId) {
        if (customerId == null || modelId == null) {
            return null;
        }
        return scoreRepository
                .findByCustomerIdAndModelId(customerId, modelId)
                .orElse(null);
    }

    // ============================================================
    // 转化预测
    // ============================================================

    /**
     * 预测客户转化概率 (模拟, 基于分数和维度)。
     * <p>调用 {@link #calculateScore} 重算后返回转化概率, 若评分记录不存在则按 0 处理。</p>
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 转化概率 (0-1)
     * @throws ScrmException 客户 / 模型不存在
     */
    @Transactional
    public Double predictConversion(Long customerId, Long modelId) throws ScrmException {
        ScrmLeadScoreResultDto result = calculateScore(customerId, modelId);
        return result.getConversionProbability();
    }

    /**
     * 根据分数与等级阈值确定等级。
     * <p>gradeThresholds 非空时按其配置 (JSON: [{grade, minScore, maxScore, color}]) 匹配;
     * 否则按默认阈值 (基于分数百分比): ≥85% A_PLUS, ≥70% SUPER_HOT, ≥55% HOT,
     * ≥40% WARM, ≥20% COLD, 否则 DEAD。</p>
     *
     * @param score      分数
     * @param maxScore   满分
     * @param thresholds 等级阈值 JSON (可空)
     * @return 等级编码
     */
    public String determineGrade(double score, double maxScore, String thresholds) {
        if (thresholds != null && !thresholds.isBlank()) {
            List<Map<String, Object>> list = parseJsonArray(thresholds);
            for (Map<String, Object> t : list) {
                String grade = (String) t.get("grade");
                double minScore = toDouble(t.get("minScore"));
                double tMaxScore = toDouble(t.get("maxScore"));
                if (score >= minScore && score <= tMaxScore) {
                    return grade;
                }
            }
        }
        // 默认阈值 (基于分数百分比)
        double percent = maxScore > 0 ? score / maxScore : 0;
        for (int i = 0; i < DEFAULT_GRADE_THRESHOLDS.length; i++) {
            double[] range = DEFAULT_GRADE_THRESHOLDS[i];
            if (percent >= range[0] && percent <= range[1]) {
                switch (i) {
                    case 0:
                        return GRADE_A_PLUS;
                    case 1:
                        return GRADE_SUPER_HOT;
                    case 2:
                        return GRADE_HOT;
                    case 3:
                        return GRADE_WARM;
                    case 4:
                        return GRADE_COLD;
                    default:
                        return GRADE_DEAD;
                }
            }
        }
        return GRADE_DEAD;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 构建客户上下文 (规则评估用)。
     * <p>从客户实体抽取评分规则可评估的字段, 缺省字段取合理默认值。</p>
     *
     * @param customer 客户实体
     * @return 客户上下文 Map
     */
    private Map<String, Object> buildCustomerContext(ScrmCustomerEntity customer) {
        Map<String, Object> context = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        // 客户属性
        context.put("lifecycle", customer.getLifecycle());
        context.put("platformType", customer.getPlatformType());
        // 最近互动距今天数
        if (customer.getLastInteractionAt() != null) {
            long days = ChronoUnit.DAYS.between(customer.getLastInteractionAt().toLocalDate(), now.toLocalDate());
            context.put("lastInteractionDays", days);
            context.put("noInteractionDays", days);
        } else {
            context.put("lastInteractionDays", Long.MAX_VALUE);
            context.put("noInteractionDays", Long.MAX_VALUE);
        }
        // 入客天数
        if (customer.getCreateTime() != null) {
            long days = ChronoUnit.DAYS.between(customer.getCreateTime().toLocalDate(), now.toLocalDate());
            context.put("customerDays", days);
        } else {
            context.put("customerDays", 0L);
        }
        // 模拟字段 (待对接, 当前缺省 0)
        context.put("totalInteractions", 0);
        context.put("orderFrequency", 0);
        context.put("totalOrders", 0);
        context.put("totalSpent", 0);
        context.put("engagementScore", 0);
        return context;
    }

    /**
     * 评估单个条件。
     * <p>支持 eq/ne/gt/lt/ge/le/between/in/contains 操作符, 自动处理类型转换。</p>
     *
     * @param fieldValue     客户属性值
     * @param operator       操作符
     * @param conditionValue 条件值
     * @return 条件是否满足
     */
    private boolean evaluateCondition(Object fieldValue, String operator, Object conditionValue) {
        if (operator == null) {
            return false;
        }
        switch (operator) {
            case "eq":
                return toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "ne":
                return !toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "gt":
                return toDouble(fieldValue) > toDouble(conditionValue);
            case "lt":
                return toDouble(fieldValue) < toDouble(conditionValue);
            case "ge":
                return toDouble(fieldValue) >= toDouble(conditionValue);
            case "le":
                return toDouble(fieldValue) <= toDouble(conditionValue);
            case "between":
                return isBetween(fieldValue, conditionValue);
            case "in":
                return isIn(fieldValue, conditionValue);
            case "contains":
                return toStringValue(fieldValue).contains(toStringValue(conditionValue));
            default:
                return false;
        }
    }

    /**
     * 判断字段值是否在区间内 (between 操作符)。
     * <p>条件值应为 [min, max] 二元数组, 区间两端均包含。</p>
     *
     * @param fieldValue    字段值
     * @param conditionValue 条件值 ([min, max])
     * @return 是否在区间内
     */
    private boolean isBetween(Object fieldValue, Object conditionValue) {
        if (conditionValue instanceof Collection<?> col && col.size() == 2) {
            Object[] arr = col.toArray();
            double min = toDouble(arr[0]);
            double max = toDouble(arr[1]);
            double val = toDouble(fieldValue);
            return val >= min && val <= max;
        }
        return false;
    }

    /**
     * 判断字段值是否在集合内 (in 操作符)。
     *
     * @param fieldValue    字段值
     * @param conditionValue 条件值 (集合)
     * @return 是否在集合内
     */
    private boolean isIn(Object fieldValue, Object conditionValue) {
        if (conditionValue instanceof Collection<?> col) {
            return col.stream().anyMatch(v -> toStringValue(v).equals(toStringValue(fieldValue)));
        }
        return false;
    }

    /**
     * 计算并设置评分趋势。
     *
     * @param score      评分实体
     * @param oldScore   旧总分
     * @param newScore   新总分
     */
    private void applyTrend(ScrmLeadScoreEntity score, double oldScore, double newScore) {
        String trend;
        double change = newScore - oldScore;
        if (oldScore == 0 && newScore == 0) {
            trend = TREND_STABLE;
        } else if (change > TREND_THRESHOLD) {
            trend = TREND_UP;
        } else if (change < -TREND_THRESHOLD) {
            trend = TREND_DOWN;
        } else {
            trend = TREND_STABLE;
        }
        score.setScoreTrend(trend);
        score.setTrendChange(round2(change));
    }

    /**
     * 根据等级与分数百分比判断是否热线索。
     * <p>A_PLUS / SUPER_HOT / HOT 或百分比 ≥55% 视为热线索。</p>
     *
     * @param grade   等级
     * @param percent 分数百分比 (0-1)
     * @return 是否热线索
     */
    private boolean isHotLead(String grade, double percent) {
        return GRADE_A_PLUS.equals(grade) || GRADE_SUPER_HOT.equals(grade) || GRADE_HOT.equals(grade)
                || percent >= 0.55;
    }

    /**
     * 根据等级与分数百分比判断是否合格线索。
     * <p>非 DEAD / COLD 或百分比 ≥40% 视为合格。</p>
     *
     * @param grade   等级
     * @param percent 分数百分比 (0-1)
     * @return 是否合格线索
     */
    private boolean isQualified(String grade, double percent) {
        return !GRADE_DEAD.equals(grade) && !GRADE_COLD.equals(grade) && percent >= 0.40;
    }

    /**
     * 模拟转化概率计算: 基于分数百分比与命中维度数加权。
     *
     * @param percent          分数百分比 (0-1)
     * @param matchedDimensions 命中维度数
     * @param totalDimensions   总维度数
     * @return 转化概率 (0-1)
     */
    private double predictConversionProbability(double percent, int matchedDimensions, int totalDimensions) {
        double scoreFactor = percent;
        double dimensionFactor = totalDimensions > 0 ? (double) matchedDimensions / totalDimensions : 0;
        double probability = 0.6 * scoreFactor + 0.4 * dimensionFactor;
        return Math.max(0, Math.min(1, probability));
    }

    /**
     * 解析 JSON 数组为 List。
     *
     * @param json JSON 字符串
     * @return List, 解析失败返回空列表
     */
    private List<Map<String, Object>> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("JSON 数组解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析 fields 字段 (Object → List)。
     *
     * @param fields 字段对象
     * @return List
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFields(Object fields) {
        if (fields instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> typed = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        typed.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    result.add(typed);
                }
            }
            return result;
        }
        return List.of();
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
    double toDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 将统计结果数组的指定位置转为 long。
     *
     * @param stats 统计结果数组
     * @param index 索引
     * @return long 值
     */
    long toLong(Object[] stats, int index) {
        if (stats == null || index >= stats.length || stats[index] == null) {
            return 0L;
        }
        if (stats[index] instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(stats[index].toString());
        } catch (NumberFormatException e) {
            return 0L;
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
     * 保留两位小数。
     *
     * @param value 原始值
     * @return 保留两位小数后的值
     */
    double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 保留四位小数。
     *
     * @param value 原始值
     * @return 保留四位小数后的值
     */
    double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /**
     * 将评分实体转换为计算结果 DTO。
     *
     * @param score 评分实体
     * @return 计算结果 DTO
     */
    private ScrmLeadScoreResultDto toResultDto(ScrmLeadScoreEntity score) {
        ScrmLeadScoreResultDto dto = new ScrmLeadScoreResultDto();
        dto.setCustomerId(score.getCustomerId());
        dto.setTotalScore(score.getTotalScore());
        dto.setGrade(score.getGrade());
        dto.setDimensionScores(score.getDimensionScores());
        dto.setConversionProbability(score.getConversionProbability());
        return dto;
    }

    /**
     * 按主键查询评分记录, 不存在抛异常, 并校验账号归属。
     *
     * @param id 评分记录 ID
     * @return 评分实体
     * @throws ScrmException 评分记录不存在
     */
    ScrmLeadScoreEntity findScoreOrThrow(Long id) throws ScrmException {
        ScrmLeadScoreEntity entity = scoreRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "评分记录不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询客户, 不存在抛异常, 并校验账号归属。
     *
     * @param id 客户 ID
     * @return 客户实体
     * @throws ScrmException 客户不存在
     */
    private ScrmCustomerEntity findCustomerOrThrow(Long id) throws ScrmException {
        ScrmCustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + id));
        return customer;
    }
}