/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthScoreCalculationService.java
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

import org.hiylo.scrm.dto.ScrmHealthCalculateDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerHealthScoreEntity;
import org.hiylo.scrm.entity.ScrmHealthScoreModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerHealthScoreRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmHealthScoreModelRepository;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户健康度评分计算服务。
 * <p>
 * 承载健康度计算子域 (单客户 / 批量 / 全量 / 重算, 含指标评分 + 权重汇总 + 等级判定完整实现),
 * 健康评分查询 (评分记录 / 按客户查询 / 分页 / 风险客户与危急客户列表) 与健康等级判定
 * (等级分布)。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmHealthScoreCalculationService {

    // ==================== 评分类型常量 ====================

    /** 评分类型: 简单求和 */
    private static final String SCORING_SIMPLE = "SIMPLE";
    /** 评分类型: 加权 */
    private static final String SCORING_WEIGHTED = "WEIGHTED";
    /** 评分类型: 动态 */
    private static final String SCORING_DYNAMIC = "DYNAMIC";

    // ==================== 健康等级常量 ====================

    /** 健康等级: 危急 */
    private static final String LEVEL_CRITICAL = "CRITICAL";
    /** 健康等级: 风险 */
    private static final String LEVEL_AT_RISK = "AT_RISK";
    /** 健康等级: 中性 */
    private static final String LEVEL_NEUTRAL = "NEUTRAL";
    /** 健康等级: 健康 */
    private static final String LEVEL_HEALTHY = "HEALTHY";
    /** 健康等级: 优秀 */
    private static final String LEVEL_EXCELLENT = "EXCELLENT";

    // ==================== 风险等级常量 ====================

    /** 风险等级: 无 */
    private static final String RISK_NONE = "NONE";
    /** 风险等级: 低 */
    private static final String RISK_LOW = "LOW";
    /** 风险等级: 中 */
    private static final String RISK_MEDIUM = "MEDIUM";
    /** 风险等级: 高 */
    private static final String RISK_HIGH = "HIGH";
    /** 风险等级: 危急 */
    private static final String RISK_CRITICAL = "CRITICAL";

    // ==================== 趋势常量 ====================

    /** 趋势: 改善 */
    private static final String TREND_IMPROVING = "IMPROVING";
    /** 趋势: 持平 */
    private static final String TREND_STABLE = "STABLE";
    /** 趋势: 下降 */
    private static final String TREND_DECLINING = "DECLINING";
    /** 趋势: 快速下降 */
    private static final String TREND_RAPID_DECLINE = "RAPID_DECLINE";

    // ==================== 更新频率常量 ====================

    /** 更新频率: 实时 */
    private static final String FREQ_REALTIME = "REALTIME";
    /** 更新频率: 每日 */
    private static final String FREQ_DAILY = "DAILY";
    /** 更新频率: 每周 */
    private static final String FREQ_WEEKLY = "WEEKLY";
    /** 更新频率: 每月 */
    private static final String FREQ_MONTHLY = "MONTHLY";

    // ==================== 默认值常量 ====================

    /** 默认评分类型 */
    private static final String DEFAULT_SCORING_TYPE = SCORING_WEIGHTED;
    /** 默认总分上限 */
    private static final int DEFAULT_TOTAL_MAX_SCORE = 100;
    /** 默认更新频率 */
    private static final String DEFAULT_UPDATE_FREQUENCY = FREQ_DAILY;
    /** 默认指标权重 */
    private static final double DEFAULT_WEIGHT = 1.0;
    /** 默认指标最高分 */
    private static final int DEFAULT_MAX_SCORE = 20;
    /** 趋势变化阈值 */
    private static final double TREND_THRESHOLD = 0.5;
    /** 快速下降阈值 */
    private static final double RAPID_DECLINE_THRESHOLD = 10.0;
    /** 风险客户分数阈值 */
    private static final double RISK_SCORE_THRESHOLD = 40.0;
    /** 流失风险互动天数阈值 */
    private static final int CHURN_INACTIVITY_DAYS = 30;
    /** 不活跃告警天数阈值 */
    private static final int INACTIVITY_ALERT_DAYS = 14;
    /** 工单过载阈值 */
    private static final int SUPPORT_OVERLOAD_THRESHOLD = 5;

    /** 默认健康阈值 (按分数百分比映射) */
    private static final double[][] DEFAULT_HEALTH_THRESHOLDS = {
            {0.85, 1.00},   // EXCELLENT
            {0.70, 0.85},   // HEALTHY
            {0.50, 0.70},   // NEUTRAL
            {0.30, 0.50},   // AT_RISK
            {0.00, 0.30}    // CRITICAL
    };

    /** 默认健康等级标签 */
    private static final Map<String, String> DEFAULT_HEALTH_LABELS;

    static {
        DEFAULT_HEALTH_LABELS = new LinkedHashMap<>();
        DEFAULT_HEALTH_LABELS.put(LEVEL_EXCELLENT, "优秀");
        DEFAULT_HEALTH_LABELS.put(LEVEL_HEALTHY, "健康");
        DEFAULT_HEALTH_LABELS.put(LEVEL_NEUTRAL, "中性");
        DEFAULT_HEALTH_LABELS.put(LEVEL_AT_RISK, "风险");
        DEFAULT_HEALTH_LABELS.put(LEVEL_CRITICAL, "危急");
    }

    /** 默认健康等级建议动作 */
    private static final Map<String, String> DEFAULT_LEVEL_ACTIONS;

    static {
        DEFAULT_LEVEL_ACTIONS = new LinkedHashMap<>();
        DEFAULT_LEVEL_ACTIONS.put(LEVEL_EXCELLENT, "保持现状, 探索增购机会");
        DEFAULT_LEVEL_ACTIONS.put(LEVEL_HEALTHY, "持续关注, 推荐增值服务");
        DEFAULT_LEVEL_ACTIONS.put(LEVEL_NEUTRAL, "加强互动, 提升使用深度");
        DEFAULT_LEVEL_ACTIONS.put(LEVEL_AT_RISK, "主动联系, 制定恢复计划");
        DEFAULT_LEVEL_ACTIONS.put(LEVEL_CRITICAL, "立即介入, 高层关注");
    }

    /** 健康度评分数据访问层 */
    private final ScrmCustomerHealthScoreRepository scoreRepository;

    /** 健康度模型数据访问层 */
    private final ScrmHealthScoreModelRepository modelRepository;

    /** 客户数据访问层 (查询客户属性用于评分计算) */
    private final ScrmCustomerRepository customerRepository;

    /** JSON 解析器 (解析 metrics / scoringRules / metricScores) */
    private final ObjectMapper objectMapper;

    /**
     * 计算单客户健康度评分 (加载模型→收集指标数据→逐指标评分→汇总→确定等级→生成建议)。
     * <p>完整实现: 解析 metrics 配置, 评估每个指标的 scoringRules, 按 scoringType 汇总得分,
     * 根据阈值确定健康等级, 计算风险等级 / 风险因素 / 建议动作, 更新评分记录 (含趋势对比)。
     * 若该客户在该模型下已有评分记录且未强制重算, 则按频率判断是否跳过。</p>
     *
     * @param calculateDto 计算参数 (customerId + modelId + forceRecalculate)
     * @return 健康度评分记录
     * @throws ScrmException 客户 / 模型不存在
     */
    @Transactional
    public ScrmCustomerHealthScoreEntity calculateHealthScore(ScrmHealthCalculateDto calculateDto)
            throws ScrmException {
        if (calculateDto == null) {
            throw ScrmException.badRequest("计算参数不能为空");
        }
        Long customerId = calculateDto.getCustomerId();
        Long modelId = calculateDto.getModelId();
        boolean forceRecalculate = Boolean.TRUE.equals(calculateDto.getForceRecalculate());
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (modelId == null) {
            throw ScrmException.badRequest("模型 ID 不能为空");
        }
        ScrmCustomerEntity customer = findCustomerOrThrow(customerId);
        ScrmHealthScoreModelEntity model = findModelOrThrow(modelId);
        // 频率判断 (非强制重算时)
        ScrmCustomerHealthScoreEntity existing = scoreRepository
                .findByCustomerIdAndModelId(customerId, modelId)
                .orElse(null);
        if (!forceRecalculate && existing != null && existing.getNextCalculationAt() != null && existing.getNextCalculationAt().isAfter(LocalDateTime.now())) {
            log.info("健康度评分未到下次计算时间, 跳过: customerId={}, modelId={}, nextAt={}",
                    customerId, modelId, existing.getNextCalculationAt());
            return existing;
        }
        // 构建客户上下文 (用于指标评估)
        Map<String, Object> context = buildCustomerContext(customer);
        // 解析模型指标配置
        List<Map<String, Object>> metrics = parseJsonArray(model.getMetrics());
        double maxScore = DEFAULT_TOTAL_MAX_SCORE;
        if (model.getTotalMaxScore() != null) {
            maxScore = model.getTotalMaxScore();
        }
        String scoringType = model.getScoringType() != null ? model.getScoringType() : DEFAULT_SCORING_TYPE;
        List<Map<String, Object>> metricScores = new ArrayList<>();
        double totalScore = 0;
        // 维度分数累加器
        double engagementScore = 0;
        double usageScore = 0;
        double satisfactionScore = 0;
        double paymentScore = 0;
        double growthScore = 0;
        double supportScore = 0;
        for (Map<String, Object> metricConfig : metrics) {
            String metricCode = (String) metricConfig.get("metricCode");
            String metricName = (String) metricConfig.get("metricName");
            double weight = toDouble(metricConfig.get("weight"));
            if (weight <= 0) {
                weight = DEFAULT_WEIGHT;
            }
            double metricMaxScore = toDouble(metricConfig.get("maxScore"));
            if (metricMaxScore <= 0) {
                metricMaxScore = DEFAULT_MAX_SCORE;
            }
            String metricScoringType = (String) metricConfig.get("scoringType");
            if (metricScoringType == null || metricScoringType.isBlank()) {
                metricScoringType = scoringType;
            }
            List<Map<String, Object>> scoringRules = parseFields(metricConfig.get("scoringRules"));
            double metricScore = 0;
            List<String> details = new ArrayList<>();
            for (Map<String, Object> rule : scoringRules) {
                String field = (String) rule.get("field");
                String operator = (String) rule.get("operator");
                Object value = rule.get("value");
                double score = toDouble(rule.get("score"));
                Object fieldValue = context.get(field);
                if (evaluateCondition(fieldValue, operator, value)) {
                    metricScore += score;
                    details.add(field + " " + operator + " " + value + " => +" + score);
                }
            }
            // 应用指标上限
            metricScore = Math.min(metricScore, metricMaxScore);
            // 按 scoringType 汇总
            double weighted;
            switch (scoringType) {
                case SCORING_SIMPLE:
                    weighted = metricScore;
                    break;
                case SCORING_DYNAMIC:
                    // 动态: 按命中规则数加权
                    double dynamicFactor = scoringRules.isEmpty() ? 1.0
                            : Math.min(1.0, (double) details.size() / scoringRules.size());
                    weighted = metricScore * weight * dynamicFactor;
                    break;
                case SCORING_WEIGHTED:
                default:
                    weighted = metricScore * weight;
                    break;
            }
            totalScore += weighted;
            // 累加到对应维度
            String dimensionCategory = categorizeMetric(metricCode);
            switch (dimensionCategory) {
                case "ENGAGEMENT":
                    engagementScore += weighted;
                    break;
                case "USAGE":
                    usageScore += weighted;
                    break;
                case "SATISFACTION":
                    satisfactionScore += weighted;
                    break;
                case "PAYMENT":
                    paymentScore += weighted;
                    break;
                case "GROWTH":
                    growthScore += weighted;
                    break;
                case "SUPPORT":
                    supportScore += weighted;
                    break;
                default:
                    break;
            }
            Map<String, Object> metricResult = new LinkedHashMap<>();
            metricResult.put("metricCode", metricCode);
            metricResult.put("metricName", metricName);
            metricResult.put("score", round2(metricScore));
            metricResult.put("maxScore", metricMaxScore);
            metricResult.put("details", String.join("; ", details));
            metricResult.put("status", metricScore >= metricMaxScore * 0.6 ? "GOOD"
                    : metricScore >= metricMaxScore * 0.3 ? "WARNING" : "POOR");
            metricScores.add(metricResult);
        }
        // 应用模型总分上限
        totalScore = Math.min(totalScore, maxScore);
        // 确定健康等级
        double percent = maxScore > 0 ? totalScore / maxScore : 0;
        String healthLevel = determineLevel(totalScore, maxScore, model.getHealthThresholds());
        String healthLabel = DEFAULT_HEALTH_LABELS.getOrDefault(healthLevel, healthLevel);
        // 计算风险等级
        String riskLevel = determineRiskLevel(totalScore, maxScore, context);
        // 收集风险因素与建议动作
        List<String> riskFactors = collectRiskFactors(totalScore, maxScore, context, healthLevel);
        List<String> recommendedActions = collectRecommendedActions(healthLevel, riskFactors);
        boolean isAtRisk = totalScore < RISK_SCORE_THRESHOLD || LEVEL_CRITICAL.equals(healthLevel)
                || LEVEL_AT_RISK.equals(healthLevel);
        boolean isChurnRisk = isChurnRisk(context, totalScore);
        // 计算下次计算时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextCalculationAt = calculateNextTime(model.getUpdateFrequency(), now);
        // 获取或创建评分记录
        ScrmCustomerHealthScoreEntity score = existing;
        if (score == null) {
            score = new ScrmCustomerHealthScoreEntity();
            score.setCustomerId(customerId);
            score.setModelId(modelId);
            score.setPreviousScore(0d);
        }
        double previousScore = score.getTotalScore() != null ? score.getTotalScore() : 0;
        score.setCustomerName(customer.getNickname());
        score.setTotalScore(round2(totalScore));
        score.setMaxScore((double) maxScore);
        score.setScorePercent(round2(percent * 100));
        score.setHealthLevel(healthLevel);
        score.setHealthLabel(healthLabel);
        score.setMetricScores(toJson(metricScores));
        score.setEngagementScore(round2(engagementScore));
        score.setUsageScore(round2(usageScore));
        score.setSatisfactionScore(round2(satisfactionScore));
        score.setPaymentScore(round2(paymentScore));
        score.setGrowthScore(round2(growthScore));
        score.setSupportScore(round2(supportScore));
        score.setRiskLevel(riskLevel);
        score.setRiskFactors(riskFactors.isEmpty() ? null : String.join(",", riskFactors));
        score.setRecommendedActions(recommendedActions.isEmpty() ? null : String.join(",", recommendedActions));
        score.setIsAtRisk(isAtRisk);
        score.setIsChurnRisk(isChurnRisk);
        score.setLastInteractionDays(toInt(context.get("lastInteractionDays")));
        score.setDaysSinceLastOrder(toInt(context.get("daysSinceLastOrder")));
        score.setOpenTickets(toInt(context.get("openTickets")));
        score.setNpsScore(context.get("npsScore") == null ? null : toInt(context.get("npsScore")));
        score.setCalculatedAt(now);
        score.setNextCalculationAt(nextCalculationAt);
        score.setPreviousScore(round2(previousScore));
        applyTrend(score, previousScore, totalScore);
        score = scoreRepository.save(score);
        // 增量更新模型应用统计
        try {
            modelRepository.incrementAppliedCount(modelId, now);
        } catch (Exception e) {
            log.warn("更新模型应用统计失败, 忽略: modelId={}, err={}", modelId, e.getMessage());
        }
        log.info("计算健康度评分: customerId={}, modelId={}, score={}, level={}, riskLevel={}",
                customerId, modelId, totalScore, healthLevel, riskLevel);
        return score;
    }

    /**
     * 批量计算客户健康度评分。
     * <p>单个客户失败跳过, 不阻断其他客户。</p>
     *
     * @param customerIds 客户 ID 列表
     * @param modelId     模型 ID
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public Map<String, Integer> batchCalculate(List<Long> customerIds, Long modelId) throws ScrmException {
        if (modelId == null) {
            throw ScrmException.badRequest("模型 ID 不能为空");
        }
        if (customerIds == null || customerIds.isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        // 校验模型存在
        findModelOrThrow(modelId);
        int processed = 0;
        int failed = 0;
        for (Long customerId : customerIds) {
            if (customerId == null) {
                continue;
            }
            try {
                ScrmHealthCalculateDto dto = new ScrmHealthCalculateDto();
                dto.setCustomerId(customerId);
                dto.setModelId(modelId);
                dto.setForceRecalculate(true);
                calculateHealthScore(dto);
                processed++;
            } catch (Exception e) {
                failed++;
                log.warn("批量计算健康度评分失败, 跳过: customerId={}, err={}", customerId, e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", customerIds.size());
        result.put("processed", processed);
        result.put("failed", failed);
        log.info("批量计算健康度评分完成: modelId={}, total={}, processed={}, failed={}",
                modelId, customerIds.size(), processed, failed);
        return result;
    }

    /**
     * 计算所有客户健康度评分 (按下所有客户)。
     *
     * @param modelId 模型 ID
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public Map<String, Integer> calculateAll(Long modelId) throws ScrmException {
        if (modelId == null) {
            throw ScrmException.badRequest("模型 ID 不能为空");
        }
        findModelOrThrow(modelId);
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        int processed = 0;
        int failed = 0;
        for (ScrmCustomerEntity customer : customers) {
            try {
                ScrmHealthCalculateDto dto = new ScrmHealthCalculateDto();
                dto.setCustomerId(customer.getId());
                dto.setModelId(modelId);
                dto.setForceRecalculate(true);
                calculateHealthScore(dto);
                processed++;
            } catch (Exception e) {
                failed++;
                log.warn("计算健康度评分失败, 跳过: customerId={}, err={}", customer.getId(), e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", customers.size());
        result.put("processed", processed);
        result.put("failed", failed);
        log.info("计算所有客户健康度评分完成:, modelId={}, total={}, processed={}, failed={}", modelId, customers.size(), processed, failed);
        return result;
    }

    /**
     * 重新计算指定评分记录 (按评分记录的 customerId 与 modelId 重算, 强制重算)。
     *
     * @param scoreId 评分记录 ID
     * @return 健康度评分记录
     * @throws ScrmException 评分记录不存在
     */
    @Transactional
    public ScrmCustomerHealthScoreEntity recalculate(Long scoreId) throws ScrmException {
        ScrmCustomerHealthScoreEntity score = findScoreOrThrow(scoreId);
        ScrmHealthCalculateDto dto = new ScrmHealthCalculateDto();
        dto.setCustomerId(score.getCustomerId());
        dto.setModelId(score.getModelId());
        dto.setForceRecalculate(true);
        return calculateHealthScore(dto);
    }

    /**
     * 查询评分详情。
     *
     * @param id 评分记录 ID
     * @return 评分实体
     * @throws ScrmException 评分记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerHealthScoreEntity getScore(Long id) throws ScrmException {
        return findScoreOrThrow(id);
    }

    /**
     * 按客户与模型查询评分。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 评分实体 (不存在返回 null)
     */
    @Transactional(readOnly = true)
    public ScrmCustomerHealthScoreEntity getScoreByCustomer(Long customerId, Long modelId) {
        if (customerId == null || modelId == null) {
            return null;
        }
        return scoreRepository
                .findByCustomerIdAndModelId(customerId, modelId)
                .orElse(null);
    }

    /**
     * 分页查询评分, 支持按健康等级 / 风险客户 / 风险等级 / 分数区间过滤与排序。
     *
     * @param healthLevel 健康等级过滤（可空）
     * @param isAtRisk    风险客户过滤（可空）
     * @param riskLevel   风险等级过滤（可空）
     * @param minScore    最低分过滤（可空）
     * @param maxScore    最高分过滤（可空）
     * @param sortBy      排序字段: totalScore / calculatedAt / scorePercent（可空, 默认 totalScore）
     * @param pageable    分页参数
     * @return 评分分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerHealthScoreEntity> listScores(String healthLevel, Boolean isAtRisk, String riskLevel,
                                                          Double minScore, Double maxScore, String sortBy,
                                                          Pageable pageable) {
        Specification<ScrmCustomerHealthScoreEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (healthLevel != null && !healthLevel.isBlank()) {
                predicates.add(cb.equal(root.get("healthLevel"), healthLevel));
            }
            if (isAtRisk != null) {
                predicates.add(cb.equal(root.get("isAtRisk"), isAtRisk));
            }
            if (riskLevel != null && !riskLevel.isBlank()) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (minScore != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalScore"), minScore));
            }
            if (maxScore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalScore"), maxScore));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return scoreRepository.findAll(spec, ensureSort(pageable, sanitizeSortField(sortBy)));
    }

    /**
     * 风险客户列表 (按 totalScore ASC, 分越低越危险)。
     *
     * @param limit 返回数量
     * @return 评分列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomerHealthScoreEntity> getAtRiskCustomers(int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        return scoreRepository
                .findByIsAtRiskTrueOrderByTotalScoreAsc(PageRequest.of(0, limit))
                .getContent();
    }

    /**
     * 危急客户列表 (健康等级为 CRITICAL, 按 totalScore ASC)。
     *
     * @param limit 返回数量
     * @return 评分列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomerHealthScoreEntity> getCriticalCustomers(int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        return scoreRepository
                .findByHealthLevelOrderByTotalScoreAsc(LEVEL_CRITICAL, PageRequest.of(0, limit))
                .getContent();
    }

    /**
     * 根据分数与健康阈值确定健康等级。
     * <p>healthThresholds 非空时按其配置 (JSON: [{level, minScore, maxScore, color, action}]) 匹配;
     * 否则按默认阈值 (基于分数百分比): ≥85% EXCELLENT, ≥70% HEALTHY, ≥50% NEUTRAL,
     * ≥30% AT_RISK, 否则 CRITICAL。</p>
     *
     * @param score      分数
     * @param maxScore   满分
     * @param thresholds 健康阈值 JSON (可空)
     * @return 健康等级
     */
    public String determineLevel(double score, double maxScore, String thresholds) {
        if (thresholds != null && !thresholds.isBlank()) {
            List<Map<String, Object>> list = parseJsonArray(thresholds);
            for (Map<String, Object> t : list) {
                String level = (String) t.get("level");
                double minScore = toDouble(t.get("minScore"));
                double tMaxScore = toDouble(t.get("maxScore"));
                if (score >= minScore && score <= tMaxScore) {
                    return level;
                }
            }
        }
        // 默认阈值 (基于分数百分比)
        double percent = maxScore > 0 ? score / maxScore : 0;
        for (int i = 0; i < DEFAULT_HEALTH_THRESHOLDS.length; i++) {
            double[] range = DEFAULT_HEALTH_THRESHOLDS[i];
            if (percent >= range[0] && percent <= range[1]) {
                switch (i) {
                    case 0:
                        return LEVEL_EXCELLENT;
                    case 1:
                        return LEVEL_HEALTHY;
                    case 2:
                        return LEVEL_NEUTRAL;
                    case 3:
                        return LEVEL_AT_RISK;
                    default:
                        return LEVEL_CRITICAL;
                }
            }
        }
        return LEVEL_CRITICAL;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 构建客户上下文 (指标评估用)。
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
        int lastInteractionDays;
        if (customer.getLastInteractionAt() != null) {
            lastInteractionDays = (int) ChronoUnit.DAYS.between(
                    customer.getLastInteractionAt().toLocalDate(), now.toLocalDate());
        } else {
            lastInteractionDays = Integer.MAX_VALUE;
        }
        context.put("lastInteractionDays", lastInteractionDays);
        context.put("noInteractionDays", lastInteractionDays);
        // 入客天数
        if (customer.getCreateTime() != null) {
            long days = ChronoUnit.DAYS.between(customer.getCreateTime().toLocalDate(), now.toLocalDate());
            context.put("customerDays", days);
        } else {
            context.put("customerDays", 0L);
        }
        // 模拟字段 (待对接业务系统, 当前缺省 0)
        context.put("totalInteractions", 0);
        context.put("orderFrequency", 0);
        context.put("totalOrders", 0);
        context.put("totalSpent", 0);
        context.put("engagementScore", 0);
        context.put("daysSinceLastOrder", 0);
        context.put("openTickets", 0);
        context.put("npsScore", null);
        context.put("satisfactionScore", 0);
        context.put("paymentStatus", "NORMAL");
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
     * 根据指标编码归类到维度。
     *
     * @param metricCode 指标编码
     * @return 维度类别: ENGAGEMENT / USAGE / SATISFACTION / PAYMENT / GROWTH / SUPPORT / OTHER
     */
    private String categorizeMetric(String metricCode) {
        if (metricCode == null) {
            return "OTHER";
        }
        String upper = metricCode.toUpperCase();
        if (upper.contains("ENGAGE") || upper.contains("INTERACT") || upper.contains("CONTACT")) {
            return "ENGAGEMENT";
        }
        if (upper.contains("USAGE") || upper.contains("ACTIVE") || upper.contains("LOGIN")) {
            return "USAGE";
        }
        if (upper.contains("SATIS") || upper.contains("NPS") || upper.contains("FEEDBACK")) {
            return "SATISFACTION";
        }
        if (upper.contains("PAY") || upper.contains("ORDER") || upper.contains("REVENUE")) {
            return "PAYMENT";
        }
        if (upper.contains("GROWTH") || upper.contains("INCREASE") || upper.contains("EXPAND")) {
            return "GROWTH";
        }
        if (upper.contains("SUPPORT") || upper.contains("TICKET") || upper.contains("SERVICE")) {
            return "SUPPORT";
        }
        return "OTHER";
    }

    /**
     * 计算风险等级 (基于分数百分比与上下文风险信号)。
     *
     * @param score    总分
     * @param maxScore 满分
     * @param context  客户上下文
     * @return 风险等级
     */
    private String determineRiskLevel(double score, double maxScore, Map<String, Object> context) {
        double percent = maxScore > 0 ? score / maxScore : 0;
        int lastInteractionDays = toInt(context.get("lastInteractionDays"));
        int openTickets = toInt(context.get("openTickets"));
        // 综合判定
        int riskPoints = 0;
        if (percent < 0.2) {
            riskPoints += 4;
        } else if (percent < 0.4) {
            riskPoints += 3;
        } else if (percent < 0.6) {
            riskPoints += 1;
        }
        if (lastInteractionDays >= CHURN_INACTIVITY_DAYS) {
            riskPoints += 2;
        } else if (lastInteractionDays >= INACTIVITY_ALERT_DAYS) {
            riskPoints += 1;
        }
        if (openTickets >= SUPPORT_OVERLOAD_THRESHOLD) {
            riskPoints += 1;
        }
        if (riskPoints >= 5) {
            return RISK_CRITICAL;
        } else if (riskPoints >= 3) {
            return RISK_HIGH;
        } else if (riskPoints >= 2) {
            return RISK_MEDIUM;
        } else if (riskPoints >= 1) {
            return RISK_LOW;
        }
        return RISK_NONE;
    }

    /**
     * 收集风险因素列表。
     *
     * @param score       总分
     * @param maxScore    满分
     * @param context     客户上下文
     * @param healthLevel 健康等级
     * @return 风险因素列表
     */
    private List<String> collectRiskFactors(double score, double maxScore, Map<String, Object> context,
                                            String healthLevel) {
        List<String> factors = new ArrayList<>();
        if (LEVEL_CRITICAL.equals(healthLevel) || LEVEL_AT_RISK.equals(healthLevel)) {
            factors.add("健康度低");
        }
        int lastInteractionDays = toInt(context.get("lastInteractionDays"));
        if (lastInteractionDays >= CHURN_INACTIVITY_DAYS) {
            factors.add("长期未互动");
        } else if (lastInteractionDays >= INACTIVITY_ALERT_DAYS) {
            factors.add("互动频率低");
        }
        int openTickets = toInt(context.get("openTickets"));
        if (openTickets >= SUPPORT_OVERLOAD_THRESHOLD) {
            factors.add("工单积压");
        }
        String paymentStatus = (String) context.get("paymentStatus");
        if (paymentStatus != null && !"NORMAL".equals(paymentStatus)) {
            factors.add("支付异常");
        }
        double percent = maxScore > 0 ? score / maxScore : 0;
        if (percent < 0.4) {
            factors.add("评分偏低");
        }
        return factors;
    }

    /**
     * 收集建议动作列表。
     *
     * @param healthLevel 健康等级
     * @param riskFactors 风险因素
     * @return 建议动作列表
     */
    private List<String> collectRecommendedActions(String healthLevel, List<String> riskFactors) {
        List<String> actions = new ArrayList<>();
        String levelAction = DEFAULT_LEVEL_ACTIONS.get(healthLevel);
        if (levelAction != null) {
            actions.add(levelAction);
        }
        if (riskFactors.contains("长期未互动")) {
            actions.add("立即主动联系客户");
        }
        if (riskFactors.contains("工单积压")) {
            actions.add("优先处理待办工单");
        }
        if (riskFactors.contains("支付异常")) {
            actions.add("核查支付状态");
        }
        if (riskFactors.contains("互动频率低")) {
            actions.add("加强互动频率");
        }
        return actions;
    }

    /**
     * 判断是否流失风险。
     *
     * @param context 客户上下文
     * @param score   总分
     * @return 是否流失风险
     */
    private boolean isChurnRisk(Map<String, Object> context, double score) {
        int lastInteractionDays = toInt(context.get("lastInteractionDays"));
        return lastInteractionDays >= CHURN_INACTIVITY_DAYS || score < RISK_SCORE_THRESHOLD / 2;
    }

    /**
     * 根据更新频率计算下次计算时间。
     *
     * @param frequency 更新频率
     * @param now       当前时间
     * @return 下次计算时间
     */
    private LocalDateTime calculateNextTime(String frequency, LocalDateTime now) {
        if (frequency == null) {
            frequency = DEFAULT_UPDATE_FREQUENCY;
        }
        switch (frequency) {
            case FREQ_REALTIME:
                return now.plusHours(1);
            case FREQ_WEEKLY:
                return now.plusWeeks(1);
            case FREQ_MONTHLY:
                return now.plusMonths(1);
            case FREQ_DAILY:
            default:
                return now.plusDays(1);
        }
    }

    /**
     * 计算并设置评分趋势。
     *
     * @param score    评分实体
     * @param oldScore 旧总分
     * @param newScore 新总分
     */
    private void applyTrend(ScrmCustomerHealthScoreEntity score, double oldScore, double newScore) {
        String trend;
        double change = newScore - oldScore;
        if (oldScore == 0 && newScore == 0) {
            trend = TREND_STABLE;
        } else if (change <= -RAPID_DECLINE_THRESHOLD) {
            trend = TREND_RAPID_DECLINE;
        } else if (change > TREND_THRESHOLD) {
            trend = TREND_IMPROVING;
        } else if (change < -TREND_THRESHOLD) {
            trend = TREND_DECLINING;
        } else {
            trend = TREND_STABLE;
        }
        score.setScoreTrend(trend);
        score.setTrendChange(round2(change));
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
    private double toDouble(Object obj) {
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
     * 将对象转换为 int 数值。
     *
     * @param obj 对象
     * @return int 值, 不可转换时返回 0
     */
    private int toInt(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
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
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 确保分页参数带默认排序 (按指定字段倒序)。
     *
     * @param pageable 分页参数
     * @param field    默认排序字段
     * @return 处理后的分页参数
     */
    private Pageable ensureSort(Pageable pageable, String field) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, field));
    }

    /**
     * 白名单校验排序字段, 防止注入非法字段名。
     *
     * @param sortBy 排序字段
     * @return 合法排序字段
     */
    private String sanitizeSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "totalScore";
        }
        switch (sortBy.trim()) {
            case "totalScore":
            case "calculatedAt":
            case "scorePercent":
            case "previousScore":
            case "trendChange":
                return sortBy.trim();
            default:
                return "totalScore";
        }
    }

    /**
     * 按主键查询模型, 不存在抛异常, 并校验账号归属。
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    private ScrmHealthScoreModelEntity findModelOrThrow(Long id) throws ScrmException {
        ScrmHealthScoreModelEntity entity = modelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "健康度模型不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询评分记录, 不存在抛异常, 并校验账号归属。
     *
     * @param id 评分记录 ID
     * @return 评分实体
     * @throws ScrmException 评分记录不存在
     */
    private ScrmCustomerHealthScoreEntity findScoreOrThrow(Long id) throws ScrmException {
        ScrmCustomerHealthScoreEntity entity = scoreRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "健康度评分记录不存在: id=" + id));
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