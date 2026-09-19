/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionResultService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmQualityInspectionResultDto;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmQualityInspectionResultEntity;
import org.hiylo.scrm.entity.ScrmQualityInspectionRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmQualityInspectionResultRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * SCRM 质检结果与统计服务。
 * <p>
 * 承载质检评估引擎 (关键词/正则/会话时长/响应时长/AI 评估)、加权总分计算、通过判定，
 * 以及质检结果的分页查询、按被质检人查询与质检统计/排名能力。评估与统计所需数据
 * 均直接查询质检结果数据, 不依赖质检任务与执行链路。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmQualityInspectionResultService {

    /** 默认通过分数 */
    private static final double DEFAULT_PASS_SCORE = 80.0;

    /** 默认评分权重 */
    private static final double DEFAULT_SCORE_WEIGHT = 1.0;

    /** 默认最大响应时长 (秒) */
    private static final long DEFAULT_MAX_RESPONSE_SECONDS = 300L;

    /** 默认最小会话时长 (秒) */
    private static final long DEFAULT_MIN_DURATION_SECONDS = 60L;

    /** 消息方向: 客户发送 */
    private static final String DIRECTION_IN = "IN";

    /** 消息方向: 坐席发送 */
    private static final String DIRECTION_OUT = "OUT";

    /** 质检结果数据访问层 */
    private final ScrmQualityInspectionResultRepository resultRepository;

    /** AI 评估器 (可插拔策略, 默认 DefaultQualityAiEvaluator, 对接大模型时替换) */
    private final org.hiylo.scrm.service.evaluator.QualityAiEvaluator qualityAiEvaluator;

    /** JSON 序列化/反序列化 */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 评估引擎
    // ============================================================

    /**
     * 评估单条规则, 返回该规则的质检结果明细。
     * <p>按规则类型分发: KEYWORD_MATCH→关键词匹配 / REGEX→正则 / DURATION→会话时长 /
     * RESPONSE_TIME→平均响应时长 / AI_EVALUATE→AI 评估 (可插拔 QualityAiEvaluator, 默认 80 分)。</p>
     *
     * @param rule     质检规则
     * @param messages 会话消息列表 (按时间升序)
     * @return 规则质检结果明细 Map (ruleId/ruleName/category/ruleType/score/passed/detail/scoreWeight)
     */
    public Map<String, Object> evaluateRule(ScrmQualityInspectionRuleEntity rule,
            List<ScrmConversationMessageEntity> messages) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", String.valueOf(rule.getId()));
        result.put("ruleName", rule.getRuleName());
        result.put("category", rule.getCategory());
        result.put("ruleType", rule.getRuleType());
        double weight = rule.getScoreWeight() != null ? rule.getScoreWeight() : DEFAULT_SCORE_WEIGHT;
        result.put("scoreWeight", weight);

        Map<String, Object> config = parseRuleConfig(rule.getRuleConfig());
        List<ScrmConversationMessageEntity> sorted = messages == null ? List.of() : messages.stream()
                .sorted(Comparator.comparing(ScrmConversationMessageEntity::getSentAt))
                .toList();

        double score;
        boolean passed;
        String detail;
        switch (rule.getRuleType()) {
            case "KEYWORD_MATCH":
                EvaluationResult kw = evaluateKeyword(rule, config, sorted);
                score = kw.score;
                passed = kw.passed;
                detail = kw.detail;
                break;
            case "REGEX":
                EvaluationResult rx = evaluateRegex(rule, config, sorted);
                score = rx.score;
                passed = rx.passed;
                detail = rx.detail;
                break;
            case "DURATION":
                EvaluationResult dur = evaluateDuration(rule, config, sorted);
                score = dur.score;
                passed = dur.passed;
                detail = dur.detail;
                break;
            case "RESPONSE_TIME":
                EvaluationResult rt = evaluateResponseTime(rule, config, sorted);
                score = rt.score;
                passed = rt.passed;
                detail = rt.detail;
                break;
            case "AI_EVALUATE":
                // AI 评估: 通过可插拔 QualityAiEvaluator 接口调用, 默认实现返回 80 分
                String promptTemplate = config.get("promptTemplate") != null
                        ? config.get("promptTemplate").toString() : null;
                org.hiylo.scrm.service.evaluator.QualityAiEvaluator.EvaluationResult aiResult =
                        qualityAiEvaluator.evaluate(promptTemplate, sorted, config);
                score = aiResult.score();
                passed = checkPassed(score, rule.getPassCondition());
                detail = aiResult.detail();
                break;
            default:
                score = 0.0;
                passed = false;
                detail = "未知规则类型: " + rule.getRuleType();
        }
        result.put("score", round2(score));
        result.put("passed", passed);
        result.put("detail", detail);
        return result;
    }

    /**
     * 加权计算总分 (0-100)。
     * <p>totalScore = Σ(score_i × weight_i) / Σ(weight_i), 无规则时返回 0。</p>
     *
     * @param ruleResults 各规则质检结果明细列表
     * @return 加权总分
     */
    public double calculateTotalScore(List<Map<String, Object>> ruleResults) {
        if (ruleResults == null || ruleResults.isEmpty()) {
            return 0.0;
        }
        double scoreSum = 0.0;
        double weightSum = 0.0;
        for (Map<String, Object> rr : ruleResults) {
            double score = toDouble(rr.get("score"));
            double weight = toDouble(rr.get("scoreWeight"), DEFAULT_SCORE_WEIGHT);
            scoreSum += score * weight;
            weightSum += weight;
        }
        if (weightSum == 0) {
            return 0.0;
        }
        return round2(Math.max(0, Math.min(100, scoreSum / weightSum)));
    }

    /**
     * 判断是否通过。
     * <p>passCondition 格式: GTE:N (总分≥N 通过) / LTE:N (总分≤N 通过) / CONTAINS/NOT_CONTAINS (默认按 80 分及格)。</p>
     *
     * @param totalScore    总分
     * @param passCondition 通过条件
     * @return 是否通过
     */
    public boolean checkPassed(double totalScore, String passCondition) {
        if (passCondition == null || passCondition.isBlank()) {
            return totalScore >= DEFAULT_PASS_SCORE;
        }
        if (passCondition.startsWith("GTE:")) {
            return totalScore >= parseDouble(passCondition.substring(4), DEFAULT_PASS_SCORE);
        }
        if (passCondition.startsWith("LTE:")) {
            return totalScore <= parseDouble(passCondition.substring(4), DEFAULT_PASS_SCORE);
        }
        // CONTAINS / NOT_CONTAINS 等定性条件不适用于总分, 默认按 80 分及格
        return totalScore >= DEFAULT_PASS_SCORE;
    }

    // ============================================================
    // 结果查询
    // ============================================================

    /**
     * 查询质检结果详情。
     *
     * @param id 结果 ID
     * @return 质检结果
     * @throws ScrmException 结果不存在
     */
    @Transactional(readOnly = true)
    public ScrmQualityInspectionResultDto getResult(Long id) throws ScrmException {
        return toResultDto(findResultOrThrow(id));
    }

    /**
     * 分页查询质检结果, 支持多条件过滤。
     *
     * @param taskId         任务 ID 过滤（可空）
     * @param conversationId 会话 ID 过滤（可空）
     * @param assigneeId     被质检人 ID 过滤（可空）
     * @param passed         是否通过过滤（可空）
     * @param minValueScore  总分下限（可空）
     * @param maxValueScore  总分上限（可空）
     * @param startTime       质检时间下限（可空）
     * @param endTime         质检时间上限（可空）
     * @param pageable        分页参数
     * @return 质检结果分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmQualityInspectionResultDto> listResults(Long taskId, Long conversationId, String assigneeId,
                                                             Boolean passed, Double minValueScore, Double maxValueScore,
                                                              LocalDateTime startTime,
                                                              LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmQualityInspectionResultEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (taskId != null) {
                predicates.add(cb.equal(root.get("taskId"), taskId));
            }
            if (conversationId != null) {
                predicates.add(cb.equal(root.get("conversationId"), conversationId));
            }
            if (assigneeId != null && !assigneeId.isBlank()) {
                predicates.add(cb.equal(root.get("assigneeId"), assigneeId));
            }
            if (passed != null) {
                predicates.add(cb.equal(root.get("passed"), passed));
            }
            if (minValueScore != null) {
                predicates.add(cb.ge(root.get("totalScore"), minValueScore));
            }
            if (maxValueScore != null) {
                predicates.add(cb.le(root.get("totalScore"), maxValueScore));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("inspectedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("inspectedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ScrmQualityInspectionResultEntity> page = resultRepository.findAll(spec, pageable);
        return page.map(this::toResultDto);
    }

    /**
     * 查询某销售时间范围内的质检结果。
     *
     * @param assigneeId 被质检人 ID
     * @param startTime  起始时间（含）
     * @param endTime    截止时间（含）
     * @return 质检结果列表
     */
    @Transactional(readOnly = true)
    public List<ScrmQualityInspectionResultDto> getResultsByAssignee(String assigneeId,
            LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmQualityInspectionResultEntity> list = resultRepository
                .findByAssigneeIdAndInspectedAtBetween(assigneeId, startTime, endTime);
        return list.stream().map(this::toResultDto).toList();
    }

    // ============================================================
    // 统计与排名
    // ============================================================

    /**
     * 质检统计: 总质检数 / 通过率 / 平均分 / 各类别得分。
     *
     * @param startTime 起始时间（含）
     * @param endTime   截止时间（含）
     * @return 统计信息
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getInspectionStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmQualityInspectionResultEntity> results = resultRepository
                .findByInspectedAtBetween(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        int total = results.size();
        long passedCount = results.stream().filter(r -> Boolean.TRUE.equals(r.getPassed())).count();
        double avgScore = results.stream()
                .mapToDouble(r -> r.getTotalScore() != null ? r.getTotalScore() : 0.0)
                .average().orElse(0.0);
        stats.put("totalInspected", total);
        stats.put("passedCount", passedCount);
        stats.put("failedCount", total - passedCount);
        stats.put("passRate", total > 0 ? round2(passedCount * 100.0 / total) : 0.0);
        stats.put("averageScore", round2(avgScore));
        stats.put("categoryScores", aggregateCategoryScores(results));
        return stats;
    }

    /**
     * 销售质检排名 (按平均分降序)。
     *
     * @param startTime 起始时间（含）
     * @param endTime   截止时间（含）
     * @param pageable  分页参数
     * @return 排名分页结果
     */
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getAssigneeRanking(LocalDateTime startTime,
            LocalDateTime endTime, Pageable pageable) {
        List<ScrmQualityInspectionResultEntity> results = resultRepository
                .findByInspectedAtBetween(startTime, endTime);
        // 按被质检人分组汇总
        Map<String, List<ScrmQualityInspectionResultEntity>> grouped = results.stream()
                .filter(r -> r.getAssigneeId() != null)
                .collect(Collectors.groupingBy(ScrmQualityInspectionResultEntity::getAssigneeId,
                        LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Map.Entry<String, List<ScrmQualityInspectionResultEntity>> entry : grouped.entrySet()) {
            List<ScrmQualityInspectionResultEntity> list = entry.getValue();
            int count = list.size();
            long passed = list.stream().filter(r -> Boolean.TRUE.equals(r.getPassed())).count();
            double avg = list.stream()
                    .mapToDouble(r -> r.getTotalScore() != null ? r.getTotalScore() : 0.0)
                    .average().orElse(0.0);
            String assigneeName = list.stream()
                    .map(ScrmQualityInspectionResultEntity::getAssigneeName)
                    .filter(n -> n != null && !n.isBlank())
                    .findFirst().orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("assigneeId", entry.getKey());
            row.put("assigneeName", assigneeName);
            row.put("totalInspected", count);
            row.put("passedCount", passed);
            row.put("passRate", count > 0 ? round2(passed * 100.0 / count) : 0.0);
            row.put("averageScore", round2(avg));
            ranking.add(row);
        }
        // 按平均分降序排序
        ranking.sort((a, b) -> Double.compare(toDouble(b.get("averageScore")), toDouble(a.get("averageScore"))));
        // 内存分页
        return paginate(ranking, pageable);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 关键词匹配评估。
     * <p>passCondition CONTAINS → 命中关键词为通过; NOT_CONTAINS → 未命中为通过 (敏感词场景)。</p>
     */
    private EvaluationResult evaluateKeyword(ScrmQualityInspectionRuleEntity rule, Map<String, Object> config,
                                              List<ScrmConversationMessageEntity> messages) {
        List<String> keywords = toStringList(config.get("keywords"));
        String combined = combineContent(messages);
        List<String> hit = new ArrayList<>();
        if (!keywords.isEmpty()) {
            String lower = combined.toLowerCase();
            for (String kw : keywords) {
                if (kw != null && !kw.isBlank() && lower.contains(kw.toLowerCase())) {
                    hit.add(kw);
                }
            }
        }
        boolean matched = !hit.isEmpty();
        boolean passed;
        if (rule.getPassCondition() != null && rule.getPassCondition().startsWith("NOT_CONTAINS")) {
            passed = !matched;
        } else {
            // CONTAINS 或默认: 命中即通过
            passed = matched;
        }
        double score = passed ? 100.0 : 0.0;
        String detail = matched
                ? "命中关键词: " + String.join(",", hit)
                : "未命中任何关键词 (共 " + keywords.size() + " 个)";
        return new EvaluationResult(score, passed, detail);
    }

    /**
     * 正则匹配评估。
     */
    private EvaluationResult evaluateRegex(ScrmQualityInspectionRuleEntity rule, Map<String, Object> config,
                                            List<ScrmConversationMessageEntity> messages) {
        Object regexObj = config.get("regex");
        String regex = regexObj == null ? "" : String.valueOf(regexObj);
        String combined = combineContent(messages);
        boolean matched = false;
        String detail;
        if (regex.isBlank()) {
            detail = "正则表达式为空, 无法评估";
        } else {
            try {
                matched = Pattern.compile(regex).matcher(combined).find();
                detail = matched ? "正则匹配命中: " + regex : "正则未匹配: " + regex;
            } catch (PatternSyntaxException e) {
                detail = "正则表达式非法: " + regex;
            }
        }
        boolean passed;
        if (rule.getPassCondition() != null && rule.getPassCondition().startsWith("NOT_CONTAINS")) {
            passed = !matched;
        } else {
            passed = matched;
        }
        double score = passed ? 100.0 : 0.0;
        return new EvaluationResult(score, passed, detail);
    }

    /**
     * 会话时长评估。
     * <p>score = min(100, duration / minDurationSeconds × 100), passCondition 按分数阈值判断。</p>
     */
    private EvaluationResult evaluateDuration(ScrmQualityInspectionRuleEntity rule, Map<String, Object> config,
                                               List<ScrmConversationMessageEntity> messages) {
        if (messages.isEmpty()) {
            return new EvaluationResult(0.0, false, "无消息, 无法评估会话时长");
        }
        long minDuration = toLong(config.get("minDurationSeconds"), DEFAULT_MIN_DURATION_SECONDS);
        long duration = Duration.between(messages.get(0).getSentAt(),
                messages.get(messages.size() - 1).getSentAt()).getSeconds();
        double score = minDuration > 0 ? Math.min(100.0, duration * 100.0 / minDuration) : 100.0;
        boolean passed = checkPassed(score, rule.getPassCondition());
        String detail = "会话时长 " + duration + "s (目标 ≥ " + minDuration + "s), 得分 " + round2(score);
        return new EvaluationResult(score, passed, detail);
    }

    /**
     * 平均响应时长评估。
     * <p>计算客户消息(IN)到下一条坐席消息(OUT)的平均间隔, score = min(100, maxResponseSeconds/avg × 100)。</p>
     */
    private EvaluationResult evaluateResponseTime(ScrmQualityInspectionRuleEntity rule, Map<String, Object> config,
                                                   List<ScrmConversationMessageEntity> messages) {
        long maxResponse = toLong(config.get("maxResponseSeconds"), DEFAULT_MAX_RESPONSE_SECONDS);
        List<Long> responseTimes = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ScrmConversationMessageEntity msg = messages.get(i);
            if (DIRECTION_IN.equalsIgnoreCase(msg.getDirection())) {
                for (int j = i + 1; j < messages.size(); j++) {
                    ScrmConversationMessageEntity next = messages.get(j);
                    if (DIRECTION_OUT.equalsIgnoreCase(next.getDirection())) {
                        responseTimes.add(Duration.between(msg.getSentAt(), next.getSentAt()).getSeconds());
                        break;
                    }
                }
            }
        }
        if (responseTimes.isEmpty()) {
            double score = 100.0;
            boolean passed = checkPassed(score, rule.getPassCondition());
            return new EvaluationResult(score, passed, "无 IN→OUT 响应样本, 默认满分");
        }
        double avg = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double score = avg <= maxResponse ? 100.0 : Math.max(0.0, maxResponse * 100.0 / avg);
        boolean passed = checkPassed(score, rule.getPassCondition());
        String detail = "平均响应时长 " + round2(avg) + "s (阈值 ≤ " + maxResponse + "s), 得分"
                + round2(score);
        return new EvaluationResult(score, passed, detail);
    }

    /**
     * 聚合各类别平均得分。
     */
    private Map<String, Object> aggregateCategoryScores(List<ScrmQualityInspectionResultEntity> results) {
        Map<String, double[]> acc = new HashMap<>(); // category → [scoreSum, count]
        for (ScrmQualityInspectionResultEntity r : results) {
            List<Map<String, Object>> ruleResults = parseRuleResults(r.getRuleResults());
            for (Map<String, Object> rr : ruleResults) {
                String category = String.valueOf(rr.get("category"));
                double score = toDouble(rr.get("score"));
                double[] arr = acc.computeIfAbsent(category, k -> new double[2]);
                arr[0] += score;
                arr[1] += 1;
            }
        }
        Map<String, Object> categoryScores = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> entry : acc.entrySet()) {
            double[] arr = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("count", (int) arr[1]);
            item.put("averageScore", arr[1] > 0 ? round2(arr[0] / arr[1]) : 0.0);
            categoryScores.put(entry.getKey(), item);
        }
        return categoryScores;
    }

    /**
     * 按主键查询质检结果, 不存在抛 404, 并校验归属账号。
     */
    private ScrmQualityInspectionResultEntity findResultOrThrow(Long id) throws ScrmException {
        ScrmQualityInspectionResultEntity entity = resultRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "质检结果不存在: id=" + id));

        return entity;
    }

    /**
     * 实体转 DTO。
     */
    ScrmQualityInspectionResultDto toResultDto(ScrmQualityInspectionResultEntity entity) {
        ScrmQualityInspectionResultDto dto = new ScrmQualityInspectionResultDto();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setConversationId(entity.getConversationId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setAssigneeId(entity.getAssigneeId());
        dto.setAssigneeName(entity.getAssigneeName());
        dto.setTotalScore(entity.getTotalScore());
        dto.setPassed(entity.getPassed());
        dto.setRuleResults(entity.getRuleResults());
        dto.setIssuesFound(entity.getIssuesFound());
        dto.setSuggestions(entity.getSuggestions());
        dto.setInspectedAt(entity.getInspectedAt());
        dto.setInspectorType(entity.getInspectorType());
        dto.setInspectorId(entity.getInspectorId());
        return dto;
    }

    /**
     * 内存分页。
     */
    private <T> Page<T> paginate(List<T> list, Pageable pageable) {
        int total = list.size();
        int from = (int) Math.min(total, pageable.getOffset());
        int to = Math.min(from + pageable.getPageSize(), total);
        List<T> sub = list.subList(from, to);
        return new PageImpl<>(sub, pageable, total);
    }

    /**
     * 解析规则质检结果 JSON 为 List<Map>。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseRuleResults(String ruleResultsJson) {
        if (ruleResultsJson == null || ruleResultsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(ruleResultsJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("规则质检结果 JSON 解析失败: err={}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析规则配置 JSON 为 Map。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRuleConfig(String ruleConfig) {
        if (ruleConfig == null || ruleConfig.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(ruleConfig, Map.class);
        } catch (Exception e) {
            log.warn("规则配置 JSON 解析失败, 返回空 Map: ruleConfig={}, err={}", ruleConfig, e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 合并所有消息文本内容 (仅文本类消息)。
     */
    private String combineContent(List<ScrmConversationMessageEntity> messages) {
        return messages.stream()
                .map(ScrmConversationMessageEntity::getContent)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 转换对象为 List<String>.
     */
    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object obj) {
        if (obj instanceof List) {
            return ((List<?>) obj).stream()
                    .map(String::valueOf)
                    .toList();
        }
        return List.of();
    }

    /**
     * 转换对象为 double。
     */
    private double toDouble(Object obj) {
        return toDouble(obj, 0.0);
    }

    private double toDouble(Object obj, double defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 转换对象为 long。
     */
    private long toLong(Object obj, long defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析字符串为 double, 不可解析返回默认值。
     */
    private double parseDouble(String s, double defaultValue) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 保留两位小数。
     */
    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * 评估结果内部载体。
     *
     * @author Hsi Chu
     * @since V1.0
     */
    private record EvaluationResult(double score, boolean passed, String detail) {
    }
}
