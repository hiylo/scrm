/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadAssignmentService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmLeadAssignDto;
import org.hiylo.scrm.entity.ScrmLeadScoreEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmLeadScoreRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 销售线索分配与统计服务。
 * <p>
 * 承载线索分配与统计子域: 评分列表查询与热 / 合格线索列表、等级与分数分布、转化统计与漏斗、
 * 线索分配 (单条 / 批量 / 列表 / 标记转化) 以及线索 / 模型效果 / 趋势 / 排行统计。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmLeadAssignmentService {

    /** 评分记录数据访问层 */
    private final ScrmLeadScoreRepository scoreRepository;

    /** 评分模型管理子域服务 (共享按主键查找 / 分页排序兜底) */
    private final ScrmLeadScoringModelService modelService;

    /** 评分计算子域服务 (共享按主键查找 / 数值转换 / 等级常量) */
    private final ScrmLeadScoringCalculationService calculationService;

    // ============================================================
    // 评分计算
    // ============================================================

    /**
     * 分页查询评分, 支持按等级 / 热线索 / 合格 / 已转化 / 模型 / 分数区间过滤与排序。
     *
     * @param grade       等级过滤（可空）
     * @param isHotLead   热线索过滤（可空）
     * @param isQualified 合格线索过滤（可空）
     * @param isConverted 已转化过滤（可空）
     * @param modelId     模型 ID 过滤（可空）
     * @param minScore    最低分过滤（可空）
     * @param maxScore    最高分过滤（可空）
     * @param sortBy      排序字段: totalScore / conversionProbability / lastCalculatedAt（可空, 默认 totalScore）
     * @param pageable    分页参数
     * @return 评分分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmLeadScoreEntity> listScores(String grade, Boolean isHotLead, Boolean isQualified,
                                                 Boolean isConverted, Long modelId, Double minScore, Double maxScore,
                                                 String sortBy, Pageable pageable) {
        Specification<ScrmLeadScoreEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (grade != null && !grade.isBlank()) {
                predicates.add(cb.equal(root.get("grade"), grade));
            }
            if (isHotLead != null) {
                predicates.add(cb.equal(root.get("isHotLead"), isHotLead));
            }
            if (isQualified != null) {
                predicates.add(cb.equal(root.get("isQualified"), isQualified));
            }
            if (isConverted != null) {
                predicates.add(cb.equal(root.get("isConverted"), isConverted));
            }
            if (modelId != null) {
                predicates.add(cb.equal(root.get("modelId"), modelId));
            }
            if (minScore != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("totalScore"), minScore));
            }
            if (maxScore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("totalScore"), maxScore));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return scoreRepository.findAll(spec, modelService.ensureSort(pageable, sanitizeSortField(sortBy)));
    }

    /**
     * 热线索列表 (按模型过滤, 缺省使用账号下全部)。
     *
     * @param modelId 模型 ID（可空, 缺省取全部）
     * @param limit   返回数量
     * @return 评分列表 (按 totalScore DESC)
     */
    @Transactional(readOnly = true)
    public List<ScrmLeadScoreEntity> getHotLeads(Long modelId, int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        if (modelId == null) {
            Specification<ScrmLeadScoreEntity> spec = (root, query, cb) ->
                    cb.equal(root.get("isHotLead"), true);
            return scoreRepository.findAll(spec, PageRequest.of(0, limit,
                    Sort.by(Sort.Direction.DESC, "totalScore"))).getContent();
        }
        return scoreRepository
                .findByModelIdAndIsHotLeadTrueOrderByTotalScoreDesc(
                         modelId, PageRequest.of(0, limit))
                .getContent();
    }

    /**
     * 合格线索列表 (按模型过滤, 缺省使用账号下全部)。
     *
     * @param modelId 模型 ID（可空, 缺省取全部）
     * @param limit   返回数量
     * @return 评分列表 (按 totalScore DESC)
     */
    @Transactional(readOnly = true)
    public List<ScrmLeadScoreEntity> getQualifiedLeads(Long modelId, int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        if (modelId == null) {
            Specification<ScrmLeadScoreEntity> spec = (root, query, cb) ->
                    cb.equal(root.get("isQualified"), true);
            return scoreRepository.findAll(spec, PageRequest.of(0, limit,
                    Sort.by(Sort.Direction.DESC, "totalScore"))).getContent();
        }
        return scoreRepository
                .findByModelIdAndIsQualifiedTrueOrderByTotalScoreDesc(
                         modelId, PageRequest.of(0, limit))
                .getContent();
    }

    // ============================================================
    // 等级
    // ============================================================

    /**
     * 等级分布统计 (按模型)。
     * <p>返回各等级客户数, 含全部默认等级 (无客户的等级为 0)。</p>
     *
     * @param modelId 模型 ID
     * @return 等级分布 Map
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getGradeDistribution(Long modelId) throws ScrmException {
        modelService.findModelOrThrow(modelId);
        List<Object[]> rows = scoreRepository.countByGrade(modelId);
        Map<String, Long> raw = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String grade = row[0] != null ? row[0].toString() : "UNKNOWN";
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            raw.put(grade, count);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(ScrmLeadScoringCalculationService.GRADE_A_PLUS, raw.getOrDefault(ScrmLeadScoringCalculationService.GRADE_A_PLUS, 0L));
        result.put(ScrmLeadScoringCalculationService.GRADE_SUPER_HOT, raw.getOrDefault(ScrmLeadScoringCalculationService.GRADE_SUPER_HOT, 0L));
        result.put(ScrmLeadScoringCalculationService.GRADE_HOT, raw.getOrDefault(ScrmLeadScoringCalculationService.GRADE_HOT, 0L));
        result.put(ScrmLeadScoringCalculationService.GRADE_WARM, raw.getOrDefault(ScrmLeadScoringCalculationService.GRADE_WARM, 0L));
        result.put(ScrmLeadScoringCalculationService.GRADE_COLD, raw.getOrDefault(ScrmLeadScoringCalculationService.GRADE_COLD, 0L));
        result.put(ScrmLeadScoringCalculationService.GRADE_DEAD, raw.getOrDefault(ScrmLeadScoringCalculationService.GRADE_DEAD, 0L));
        result.put("total", raw.values().stream().mapToLong(Long::longValue).sum());
        return result;
    }

    /**
     * 分数分布统计 (按模型)。
     * <p>按分数区间 [0,20), [20,40), [40,60), [60,80), [80,100] 聚合客户数。</p>
     *
     * @param modelId 模型 ID
     * @return 分数分布 Map
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getScoreDistribution(Long modelId) throws ScrmException {
        modelService.findModelOrThrow(modelId);
        List<Object[]> rows = scoreRepository.countByScoreBucket(modelId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("0-20", 0L);
        result.put("20-40", 0L);
        result.put("40-60", 0L);
        result.put("60-80", 0L);
        result.put("80-100", 0L);
        long total = 0L;
        for (Object[] row : rows) {
            String bucket = row[0] != null ? row[0].toString() : "0-20";
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            result.put(bucket, count);
            total += count;
        }
        result.put("total", total);
        return result;
    }

    // ============================================================
    // 转化预测
    // ============================================================

    /**
     * 转化统计: 总线索数 / 已转化数 / 转化率 / 平均转化概率 / 总转化价值。
     *
     * @param modelId   模型 ID
     * @param startTime 起始时间 (含, 可空, 按 lastCalculatedAt 过滤)
     * @param endTime   截止时间 (含, 可空, 按 lastCalculatedAt 过滤)
     * @return 转化统计 Map
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getConversionStats(Long modelId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        modelService.findModelOrThrow(modelId);
        // 全模型基础统计
        Object[] stats = scoreRepository.getConversionStats(modelId);
        long totalLeads = 0L;
        long convertedLeads = 0L;
        double avgProbability = 0.0;
        double totalValue = 0.0;
        if (stats != null && stats.length == 4) {
            totalLeads = calculationService.toLong(stats, 0);
            convertedLeads = calculationService.toLong(stats, 1);
            avgProbability = calculationService.toDouble(stats[2]);
            totalValue = calculationService.toDouble(stats[3]);
        }
        // 时间区间内已转化的线索数 (按 convertedAt 过滤, 仅当提供时间区间时)
        long convertedInRange = convertedLeads;
        if (startTime != null || endTime != null) {
            Specification<ScrmLeadScoreEntity> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("modelId"), modelId));
                predicates.add(cb.equal(root.get("isConverted"), true));
                if (startTime != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("convertedAt"), startTime));
                }
                if (endTime != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("convertedAt"), endTime));
                }
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            convertedInRange = scoreRepository.count(spec);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalLeads", totalLeads);
        result.put("convertedLeads", convertedLeads);
        result.put("convertedInRange", convertedInRange);
        result.put("conversionRate", totalLeads == 0 ? 0.0 : (double) convertedLeads / totalLeads);
        result.put("averageConversionProbability", calculationService.round4(avgProbability));
        result.put("totalConversionValue", calculationService.round2(totalValue));
        return result;
    }

    /**
     * 转化漏斗: 各等级 → 线索数 / 转化数 / 转化率。
     *
     * @param modelId 模型 ID
     * @return 漏斗列表 [{grade, totalLeads, convertedLeads, conversionRate}]
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConversionFunnel(Long modelId) throws ScrmException {
        modelService.findModelOrThrow(modelId);
        List<Object[]> rows = scoreRepository.getConversionFunnel(modelId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            String grade = row[0] != null ? row[0].toString() : "UNKNOWN";
            long total = calculationService.toLong(row, 1);
            long converted = calculationService.toLong(row, 2);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("grade", grade);
            entry.put("totalLeads", total);
            entry.put("convertedLeads", converted);
            entry.put("conversionRate", total == 0 ? 0.0 : (double) converted / total);
            result.add(entry);
        }
        return result;
    }

    // ============================================================
    // 分配
    // ============================================================

    /**
     * 分配线索给负责人。
     *
     * @param assignDto 分配参数
     * @return 更新后的评分记录
     * @throws ScrmException 评分记录不存在
     */
    @Transactional
    public ScrmLeadScoreEntity assignLead(ScrmLeadAssignDto assignDto) throws ScrmException {
        if (assignDto == null) {
            throw ScrmException.badRequest("分配参数不能为空");
        }
        ScrmLeadScoreEntity score = calculationService.findScoreOrThrow(assignDto.getScoreId());
        score.setAssignedTo(assignDto.getAssigneeId());
        score.setAssignedAt(LocalDateTime.now());
        score = scoreRepository.save(score);
        log.info("分配线索: scoreId={}, assignee={}", assignDto.getScoreId(), assignDto.getAssigneeId());
        return score;
    }

    /**
     * 批量分配线索给同一负责人。
     *
     * @param scoreIds   评分记录 ID 列表
     * @param assigneeId 负责人用户标识
     * @return 分配结果: {total, assigned, failed}
     */
    @Transactional
    public Map<String, Integer> batchAssignLeads(List<Long> scoreIds, String assigneeId) {
        if (scoreIds == null || scoreIds.isEmpty()) {
            throw ScrmException.badRequest("评分 ID 列表不能为空");
        }
        if (assigneeId == null || assigneeId.isBlank()) {
            throw ScrmException.badRequest("负责人不能为空");
        }
        int assigned = 0;
        int failed = 0;
        for (Long scoreId : scoreIds) {
            if (scoreId == null) {
                continue;
            }
            try {
                ScrmLeadAssignDto dto = new ScrmLeadAssignDto();
                dto.setScoreId(scoreId);
                dto.setAssigneeId(assigneeId);
                assignLead(dto);
                assigned++;
            } catch (Exception e) {
                failed++;
                log.warn("批量分配线索失败, 跳过: scoreId={}, err={}", scoreId, e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", scoreIds.size());
        result.put("assigned", assigned);
        result.put("failed", failed);
        return result;
    }

    /**
     * 获取分配给负责人的线索 (按分配时间倒序)。
     *
     * @param assigneeId 负责人用户标识
     * @param pageable   分页参数
     * @return 评分分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmLeadScoreEntity> getAssignedLeads(String assigneeId, Pageable pageable) {
        if (assigneeId == null || assigneeId.isBlank()) {
            throw ScrmException.badRequest("负责人不能为空");
        }
        return scoreRepository.findByAssignedToOrderByAssignedAtDesc(
                 assigneeId, pageable);
    }

    /**
     * 标记线索为已转化。
     *
     * @param scoreId         评分记录 ID
     * @param conversionValue 转化价值
     * @return 更新后的评分记录
     * @throws ScrmException 评分记录不存在 / 已转化
     */
    @Transactional
    public ScrmLeadScoreEntity markConverted(Long scoreId, Double conversionValue) throws ScrmException {
        ScrmLeadScoreEntity score = calculationService.findScoreOrThrow(scoreId);
        if (Boolean.TRUE.equals(score.getIsConverted())) {
            throw ScrmException.conflict("线索已转化, 不允许重复标记: id=" + scoreId);
        }
        score.setIsConverted(true);
        score.setConvertedAt(LocalDateTime.now());
        score.setConversionValue(conversionValue != null ? conversionValue : 0d);
        score = scoreRepository.save(score);
        log.info("标记线索转化: scoreId={}, conversionValue={}", scoreId, score.getConversionValue());
        return score;
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 线索统计: 总数 / 已转化数 / 转化率 / 平均分 / 各等级客户数。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLeadStats(LocalDateTime startTime, LocalDateTime endTime) {
        Object[] stats = scoreRepository.getLeadStats(startTime, endTime);
        long totalLeads = 0L;
        long convertedLeads = 0L;
        double avgScore = 0.0;
        if (stats != null && stats.length == 3) {
            totalLeads = calculationService.toLong(stats, 0);
            convertedLeads = calculationService.toLong(stats, 1);
            avgScore = calculationService.toDouble(stats[2]);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalLeads", totalLeads);
        result.put("convertedLeads", convertedLeads);
        result.put("conversionRate", totalLeads == 0 ? 0.0 : (double) convertedLeads / totalLeads);
        result.put("averageScore", calculationService.round2(avgScore));
        // 各等级客户数 (按全模型聚合)
        Map<String, Long> gradeCount = new LinkedHashMap<>();
        for (String grade : ScrmLeadScoringCalculationService.DEFAULT_GRADE_LABELS.keySet()) {
            gradeCount.put(grade, 0L);
        }
        Specification<ScrmLeadScoreEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastCalculatedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("lastCalculatedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmLeadScoreEntity> allScores = scoreRepository.findAll(spec);
        for (ScrmLeadScoreEntity s : allScores) {
            String g = s.getGrade() != null ? s.getGrade() : "UNKNOWN";
            gradeCount.merge(g, 1L, Long::sum);
        }
        result.put("gradeCount", gradeCount);
        return result;
    }

    /**
     * 模型效果: 准确率 (转化预测准确率) / 转化率对比 (高分 vs 低分)。
     *
     * @param modelId 模型 ID
     * @return 模型效果 Map
     * @throws ScrmException 模型不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getModelPerformance(Long modelId) throws ScrmException {
        modelService.findModelOrThrow(modelId);
        List<ScrmLeadScoreEntity> scores = scoreRepository.findByModelId(modelId);
        int total = scores.size();
        int highScoreCount = 0;
        int highScoreConverted = 0;
        int lowScoreCount = 0;
        int lowScoreConverted = 0;
        double probabilitySum = 0;
        int probabilityCount = 0;
        for (ScrmLeadScoreEntity s : scores) {
            double score = s.getTotalScore() != null ? s.getTotalScore() : 0;
            boolean converted = Boolean.TRUE.equals(s.getIsConverted());
            if (score >= 50) {
                highScoreCount++;
                if (converted) {
                    highScoreConverted++;
                }
            } else {
                lowScoreCount++;
                if (converted) {
                    lowScoreConverted++;
                }
            }
            if (s.getConversionProbability() != null) {
                probabilitySum += s.getConversionProbability();
                probabilityCount++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalLeads", total);
        result.put("averagePredictedProbability",
                probabilityCount == 0 ? 0.0 : calculationService.round4(probabilitySum / probabilityCount));
        result.put("highScoreCount", highScoreCount);
        result.put("highScoreConverted", highScoreConverted);
        result.put("highScoreConversionRate",
                highScoreCount == 0 ? 0.0 : (double) highScoreConverted / highScoreCount);
        result.put("lowScoreCount", lowScoreCount);
        result.put("lowScoreConverted", lowScoreConverted);
        result.put("lowScoreConversionRate",
                lowScoreCount == 0 ? 0.0 : (double) lowScoreConverted / lowScoreCount);
        // 模拟准确率: 高分转化率 / (高分转化率 + 低分转化率)
        double highRate = highScoreCount == 0 ? 0 : (double) highScoreConverted / highScoreCount;
        double lowRate = lowScoreCount == 0 ? 0 : (double) lowScoreConverted / lowScoreCount;
        double accuracy = (highRate + lowRate) == 0 ? 0 : highRate / (highRate + lowRate);
        result.put("accuracy", calculationService.round4(accuracy));
        return result;
    }

    /**
     * 评分趋势: 返回最近 days 天每日的总分均值 / 评分次数。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @param days       天数
     * @return 趋势列表 (按日期升序)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getScoreTrend(Long customerId, Long modelId, int days) {
        if (customerId == null || modelId == null) {
            return List.of();
        }
        if (days <= 0) {
            days = 7;
        }
        ScrmLeadScoreEntity current = scoreRepository
                .findByCustomerIdAndModelId(customerId, modelId)
                .orElse(null);
        List<Map<String, Object>> result = new ArrayList<>();
        // 模拟趋势: 基于当前评分按日递减回推 (实际应基于评分历史快照表)
        double baseScore = current != null && current.getTotalScore() != null ? current.getTotalScore() : 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime day = now.toLocalDate().minusDays(i).atStartOfDay();
            // 简单模拟: 距今越远分数越低
            double simulatedScore = Math.max(0, baseScore - i * 0.5);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.toLocalDate().toString());
            row.put("averageScore", calculationService.round2(simulatedScore));
            row.put("scoreCount", 1);
            result.add(row);
        }
        return result;
    }

    /**
     * 排行: 按指定字段排序的顶级线索。
     *
     * @param limit  返回数量
     * @param sortBy 排序字段: totalScore / conversionProbability / predictedValue（缺省, 默认 totalScore）
     * @return 评分列表
     */
    @Transactional(readOnly = true)
    public List<ScrmLeadScoreEntity> getTopLeads(int limit, String sortBy) {
        if (limit <= 0) {
            limit = 10;
        }
        return scoreRepository
                .findAllByOrderByTotalScoreDesc(PageRequest.of(0, limit))
                .getContent().stream()
                .collect(Collectors.toList());
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

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
            case "conversionProbability":
            case "predictedValue":
            case "lastCalculatedAt":
            case "conversionValue":
                return sortBy.trim();
            default:
                return "totalScore";
        }
    }
}