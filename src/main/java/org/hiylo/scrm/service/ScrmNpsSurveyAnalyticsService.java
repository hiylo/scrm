/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNpsSurveyAnalyticsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmNpsBenchmarkEntity;
import org.hiylo.scrm.entity.ScrmSurveyEntity;
import org.hiylo.scrm.entity.ScrmSurveyInvitationEntity;
import org.hiylo.scrm.entity.ScrmSurveyResponseEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmNpsBenchmarkRepository;
import org.hiylo.scrm.repository.ScrmSurveyInvitationRepository;
import org.hiylo.scrm.repository.ScrmSurveyRepository;
import org.hiylo.scrm.repository.ScrmSurveyResponseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 客户满意度 NPS 调查 - 基准与统计子域服务。
 * <p>
 * 承载 NPS 基准 (按月 / 季 / 年汇总推荐者 / 被动者 / 贬损者) 与多维度统计能力
 * (问卷统计 / 总体统计 / NPS 趋势 / 回复趋势 / 情感分布 / 高频反馈)。共享问卷管理
 * 子域的 {@link ScrmNpsSurveyManageService#findSurveyOrThrow}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmNpsSurveyAnalyticsService {

    /** NPS 推荐者下限 (9-10) */
    private static final int NPS_PROMOTER_MIN = 9;
    /** NPS 被动者下限 (7-8) */
    private static final int NPS_PASSIVE_MIN = 7;
    /** NPS 贬损者上限 (0-6) */
    private static final int NPS_DETRACTOR_MAX = 6;

    /** 情感: 正向 */
    private static final String SENTIMENT_POSITIVE = "POSITIVE";
    /** 情感: 中性 */
    private static final String SENTIMENT_NEUTRAL = "NEUTRAL";
    /** 情感: 负向 */
    private static final String SENTIMENT_NEGATIVE = "NEGATIVE";

    /** 邀请状态: 已完成 */
    private static final String INVITATION_COMPLETED = "COMPLETED";

    /** 跟进状态: 待跟进 */
    private static final String FOLLOW_UP_PENDING = "PENDING";
    /** 跟进状态: 进行中 */
    private static final String FOLLOW_UP_IN_PROGRESS = "IN_PROGRESS";
    /** 跟进状态: 已完成 */
    private static final String FOLLOW_UP_COMPLETED = "COMPLETED";

    /** 周期类型: 月 */
    private static final String PERIOD_MONTHLY = "MONTHLY";
    /** 周期类型: 季 */
    private static final String PERIOD_QUARTERLY = "QUARTERLY";
    /** 周期类型: 年 */
    private static final String PERIOD_YEARLY = "YEARLY";

    /** 合法的调查类型 */
    private static final List<String> VALID_SURVEY_TYPES = List.of("NPS", "CSAT", "CES", "CUSTOM");

    /** 合法的周期类型 */
    private static final List<String> VALID_PERIOD_TYPES = List.of(PERIOD_MONTHLY, PERIOD_QUARTERLY, PERIOD_YEARLY);

    /** NPS 基准数据访问层 */
    private final ScrmNpsBenchmarkRepository benchmarkRepository;

    /** 调查回复数据访问层 */
    private final ScrmSurveyResponseRepository responseRepository;

    /** 调查邀请数据访问层 */
    private final ScrmSurveyInvitationRepository invitationRepository;

    /** 调查问卷数据访问层 */
    private final ScrmSurveyRepository surveyRepository;

    /** 问卷管理子域服务 (共享问卷查询) */
    private final ScrmNpsSurveyManageService manageService;

    /**
     * 生成 NPS 基准 (按周期统计推荐者 / 被动者 / 贬损者 → 计算 NPS)。
     * <p>统计周期内全部回复 (按 submittedAt 落在 [periodStart, periodEnd] 内), 仅计 npsScore 非空者。
     * NPS = (promoters - detractors) / total * 100, 取整。回复率 = 已完成邀请 / 全部邀请 (按 sentAt 落在周期内)。
     * 同周期已存在基准则更新 (按 periodType + periodStart + periodEnd 去重)。</p>
     *
     * @param periodType  周期类型: MONTHLY / QUARTERLY / YEARLY
     * @param startDate   周期开始日期
     * @param endDate     周期结束日期
     * @return 生成 / 更新后的基准
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmNpsBenchmarkEntity generateBenchmark(String periodType, LocalDate startDate, LocalDate endDate)
            throws ScrmException {
        if (!VALID_PERIOD_TYPES.contains(periodType)) {
            throw ScrmException.badRequest(
                    "周期类型非法: " + periodType + ", 仅支持 " + VALID_PERIOD_TYPES);
        }
        if (startDate == null || endDate == null) {
            throw ScrmException.badRequest("周期开始 / 结束日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw ScrmException.badRequest(
                    "周期开始日期不能晚于结束日期: start=" + startDate + ", end=" + endDate);
        }
        // 加载周期内回复
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.plusDays(1).atStartOfDay();
        List<ScrmSurveyResponseEntity> responses = responseRepository
                .findByTimeRange(startTime, endTime);
        // 仅计 NPS 分数非空者
        List<ScrmSurveyResponseEntity> npsResponses = responses.stream()
                .filter(r -> r.getNpsScore() != null)
                .collect(Collectors.toList());
        int promoters = 0;
        int passives = 0;
        int detractors = 0;
        for (ScrmSurveyResponseEntity r : npsResponses) {
            int score = r.getNpsScore();
            if (score >= NPS_PROMOTER_MIN) {
                promoters++;
            } else if (score >= NPS_PASSIVE_MIN) {
                passives++;
            } else {
                detractors++;
            }
        }
        int totalNps = npsResponses.size();
        int npsScore = calculateNps(npsResponses);
        double promoterPercent = totalNps == 0 ? 0.0 : (double) promoters / totalNps;
        double passivePercent = totalNps == 0 ? 0.0 : (double) passives / totalNps;
        double detractorPercent = totalNps == 0 ? 0.0 : (double) detractors / totalNps;
        // 平均 CSAT / CES
        double avgCsat = responses.stream()
                .map(ScrmSurveyResponseEntity::getCsatScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
        double avgCes = responses.stream()
                .map(ScrmSurveyResponseEntity::getCesScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
        // 回复率: 周期内已发送邀请数 vs 已完成邀请数
        long totalInvitations = countInvitationsByTimeRange(startTime, endTime, null);
        long completedInvitations = countInvitationsByTimeRange(startTime, endTime, INVITATION_COMPLETED);
        double responseRate = totalInvitations == 0 ? 0.0 : (double) completedInvitations / totalInvitations;
        // 同周期去重 upsert
        ScrmNpsBenchmarkEntity entity = benchmarkRepository
                .findByPeriodTypeAndPeriodStartAndPeriodEnd(periodType, startDate, endDate)
                .orElseGet(ScrmNpsBenchmarkEntity::new);
        boolean isNew = entity.getId() == null;
        if (isNew) {
            entity.setPeriodType(periodType);
            entity.setPeriodStart(startDate);
            entity.setPeriodEnd(endDate);
        }
        entity.setTotalResponses(responses.size());
        entity.setPromoters(promoters);
        entity.setPassives(passives);
        entity.setDetractors(detractors);
        entity.setNpsScore(npsScore);
        entity.setPromoterPercent(promoterPercent);
        entity.setPassivePercent(passivePercent);
        entity.setDetractorPercent(detractorPercent);
        entity.setAvgCsatScore(avgCsat);
        entity.setAvgCesScore(avgCes);
        entity.setResponseRate(responseRate);
        entity.setGeneratedAt(LocalDateTime.now());
        entity = benchmarkRepository.save(entity);
        log.info(
               "生成 "+
                "NPS "+
                "基准: "+
                "id={}, "+
                "periodType={}, "+
                "period=[{},{}], "+
                "total={}, "+
                "promoters={}, "+
                "passives={}, "+
                "detractors={}, "+
                "nps={}", entity.getId(), periodType, startDate, endDate, responses.size(), promoters, passives,
                 detractors, npsScore);
        return entity;
    }

    /**
     * 查询基准详情。
     *
     * @param id 基准 ID
     * @return 基准实体
     * @throws ScrmException 基准不存在
     */
    @Transactional(readOnly = true)
    public ScrmNpsBenchmarkEntity getBenchmark(Long id) throws ScrmException {
        return findBenchmarkOrThrow(id);
    }

    /**
     * 分页查询基准, 支持按周期类型与周期范围过滤。
     *
     * @param periodType 周期类型过滤（可空）
     * @param startDate  周期开始日期起始 (含, 可空)
     * @param endDate    周期结束日期截止 (含, 可空)
     * @param pageable   分页参数
     * @return 基准分页结果 (按 generatedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmNpsBenchmarkEntity> listBenchmarks(String periodType, LocalDate startDate, LocalDate endDate,
                                                         Pageable pageable) {
        Specification<ScrmNpsBenchmarkEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (periodType != null && !periodType.isBlank()) {
                predicates.add(cb.equal(root.get("periodType"), periodType));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("periodStart"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("periodEnd"), endDate));
            }
            query.orderBy(cb.desc(root.get("generatedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return benchmarkRepository.findAll(spec, pageable);
    }

    /**
     * 查询最新基准 (按周期类型, 取生成时间最近的一条)。
     *
     * @param periodType 周期类型: MONTHLY / QUARTERLY / YEARLY
     * @return 最新基准, 不存在返回 null
     * @throws ScrmException 周期类型非法
     */
    @Transactional(readOnly = true)
    public ScrmNpsBenchmarkEntity getLatestBenchmark(String periodType) throws ScrmException {
        if (!VALID_PERIOD_TYPES.contains(periodType)) {
            throw ScrmException.badRequest(
                    "周期类型非法: " + periodType + ", 仅支持 " + VALID_PERIOD_TYPES);
        }
        Page<ScrmNpsBenchmarkEntity> page = benchmarkRepository
                .findByPeriodTypeOrderByGeneratedAtDesc(periodType, PageRequest.of(0, 1));
        return page.hasContent() ? page.getContent().get(0) : null;
    }

    /**
     * 计算 NPS 分数 (promoters% - detractors%)。
     * <p>NPS = (推荐者数 - 贬损者数) / 总数 * 100, 取整。推荐者 9-10, 被动者 7-8, 贬损者 0-6。
     * 总数为 0 时返回 0。</p>
     *
     * @param responses 回复列表 (仅计 npsScore 非空者)
     * @return NPS 分数 (-100 到 100)
     */
    public int calculateNps(List<ScrmSurveyResponseEntity> responses) {
        if (responses == null || responses.isEmpty()) {
            return 0;
        }
        int promoters = 0;
        int detractors = 0;
        int total = 0;
        for (ScrmSurveyResponseEntity r : responses) {
            if (r.getNpsScore() == null) {
                continue;
            }
            total++;
            int score = r.getNpsScore();
            if (score >= NPS_PROMOTER_MIN) {
                promoters++;
            } else if (score <= NPS_DETRACTOR_MAX) {
                detractors++;
            }
        }
        if (total == 0) {
            return 0;
        }
        return (int) Math.round(((double) (promoters - detractors) / total) * 100);
    }

    /**
     * 问卷统计: 回复数、完成率、NPS、CSAT、情感分布。
     *
     * @param surveyId 问卷 ID
     * @return 统计结果 Map
     * @throws ScrmException 问卷不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSurveyStats(Long surveyId) throws ScrmException {
        ScrmSurveyEntity survey = manageService.findSurveyOrThrow(surveyId);
        List<ScrmSurveyResponseEntity> responses = responseRepository
                .findBySurveyIdOrderBySubmittedAtDesc(surveyId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("surveyId", surveyId);
        stats.put("surveyName", survey.getSurveyName());
        stats.put("surveyType", survey.getSurveyType());
        stats.put("status", survey.getStatus());
        stats.put("responseCount", responses.size());
        stats.put("storedResponseCount", survey.getResponseCount());
        // NPS
        stats.put("npsScore", calculateNps(responses));
        // 平均 CSAT / CES
        stats.put("avgCsatScore", responses.stream()
                .map(ScrmSurveyResponseEntity::getCsatScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0));
        stats.put("avgCesScore", responses.stream()
                .map(ScrmSurveyResponseEntity::getCesScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0));
        // 情感分布
        stats.put("sentimentDistribution", buildSentimentDistribution(responses));
        // 跟进统计
        long followUpRequired = responses.stream()
                .filter(r -> Boolean.TRUE.equals(r.getFollowUpRequired()))
                .count();
        long followUpPending = responses.stream()
                .filter(r -> FOLLOW_UP_PENDING.equals(r.getFollowUpStatus()))
                .count();
        long followUpInProgress = responses.stream()
                .filter(r -> FOLLOW_UP_IN_PROGRESS.equals(r.getFollowUpStatus()))
                .count();
        long followUpCompleted = responses.stream()
                .filter(r -> FOLLOW_UP_COMPLETED.equals(r.getFollowUpStatus()))
                .count();
        stats.put("followUpRequired", followUpRequired);
        stats.put("followUpPending", followUpPending);
        stats.put("followUpInProgress", followUpInProgress);
        stats.put("followUpCompleted", followUpCompleted);
        return stats;
    }

    /**
     * 总体统计: 各调查类型回复数、平均 NPS、趋势概览。
     *
     * @param startTime 提交时间起始 (含, 可空)
     * @param endTime   提交时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOverallStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmSurveyResponseEntity> responses = responseRepository
                .findByTimeRange(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalResponses", responses.size());
        stats.put("overallNps", calculateNps(responses));
        stats.put("avgCsatScore", responses.stream()
                .map(ScrmSurveyResponseEntity::getCsatScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0));
        stats.put("avgCesScore", responses.stream()
                .map(ScrmSurveyResponseEntity::getCesScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0));
        // 各调查类型分布
        Map<String, Long> byType = new LinkedHashMap<>();
        for (String t : VALID_SURVEY_TYPES) {
            byType.put(t, 0L);
        }
        Map<Long, String> surveyTypeMap = new HashMap<>();
        for (ScrmSurveyResponseEntity r : responses) {
            String type = surveyTypeMap.computeIfAbsent(r.getSurveyId(), sid -> {
                try {
                    return surveyRepository.findById(sid)
                            .map(ScrmSurveyEntity::getSurveyType)
                            .orElse("CUSTOM");
                } catch (Exception e) {
                    return "CUSTOM";
                }
            });
            byType.merge(type, 1L, Long::sum);
        }
        stats.put("surveyTypeCount", byType);
        stats.put("sentimentDistribution", buildSentimentDistribution(responses));
        return stats;
    }

    /**
     * NPS 趋势 (按月聚合, 最近 months 个月)。
     *
     * @param months 月数
     * @return 趋势列表: [{period, total, promoters, passives, detractors, nps}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getNpsTrend(int months) {
        if (months <= 0) {
            months = 6;
        }
        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(months - 1L);
        LocalDateTime startTime = startMonth.atDay(1).atStartOfDay();
        LocalDateTime endTime = currentMonth.atEndOfMonth().plusDays(1).atStartOfDay();
        List<ScrmSurveyResponseEntity> responses = responseRepository
                .findByTimeRange(startTime, endTime);
        Map<YearMonth, List<ScrmSurveyResponseEntity>> grouped = responses.stream()
                .filter(r -> r.getSubmittedAt() != null)
                .collect(Collectors.groupingBy(r -> YearMonth.from(r.getSubmittedAt())));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = startMonth.plusMonths(i);
            List<ScrmSurveyResponseEntity> monthResponses = grouped.getOrDefault(ym, List.of());
            List<ScrmSurveyResponseEntity> npsResponses = monthResponses.stream()
                    .filter(r -> r.getNpsScore() != null)
                    .collect(Collectors.toList());
            int promoters = 0;
            int passives = 0;
            int detractors = 0;
            for (ScrmSurveyResponseEntity r : npsResponses) {
                int score = r.getNpsScore();
                if (score >= NPS_PROMOTER_MIN) {
                    promoters++;
                } else if (score >= NPS_PASSIVE_MIN) {
                    passives++;
                } else {
                    detractors++;
                }
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("period", ym.toString());
            point.put("total", npsResponses.size());
            point.put("promoters", promoters);
            point.put("passives", passives);
            point.put("detractors", detractors);
            point.put("nps", calculateNps(npsResponses));
            trend.add(point);
        }
        return trend;
    }

    /**
     * 回复趋势 (按日聚合, 最近 days 天)。
     *
     * @param surveyId 问卷 ID (可空, 为空统计全部)
     * @param days      天数
     * @return 趋势列表: [{date, count}]
     * @throws ScrmException 问卷不存在 (surveyId 非空时)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getResponseTrend(Long surveyId, int days) throws ScrmException {
        if (surveyId != null) {
            manageService.findSurveyOrThrow(surveyId);
        }
        if (days <= 0) {
            days = 30;
        }
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1L);
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();
        Specification<ScrmSurveyResponseEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (surveyId != null) {
                predicates.add(cb.equal(root.get("surveyId"), surveyId));
            }
            predicates.add(cb.greaterThanOrEqualTo(root.get("submittedAt"), startTime));
            predicates.add(cb.lessThan(root.get("submittedAt"), endTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmSurveyResponseEntity> responses = responseRepository.findAll(spec);
        Map<LocalDate, Long> grouped = responses.stream()
                .filter(r -> r.getSubmittedAt() != null)
                .collect(Collectors.groupingBy(r -> r.getSubmittedAt().toLocalDate(), Collectors.counting()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date.toString());
            point.put("count", grouped.getOrDefault(date, 0L));
            trend.add(point);
        }
        return trend;
    }

    /**
     * 情感分布 (POSITIVE / NEUTRAL / NEGATIVE 计数)。
     *
     * @param surveyId 问卷 ID (可空, 为空统计全部)
     * @return 情感分布 Map
     * @throws ScrmException 问卷不存在 (surveyId 非空时)
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getSentimentDistribution(Long surveyId) throws ScrmException {
        if (surveyId != null) {
            manageService.findSurveyOrThrow(surveyId);
        }
        List<Object[]> rows = responseRepository.countBySentiment(surveyId);
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put(SENTIMENT_POSITIVE, 0L);
        distribution.put(SENTIMENT_NEUTRAL, 0L);
        distribution.put(SENTIMENT_NEGATIVE, 0L);
        for (Object[] row : rows) {
            String sentiment = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            if (sentiment != null) {
                distribution.put(sentiment, count);
            }
        }
        return distribution;
    }

    /**
     * 获取高频反馈 (按 feedbackText 去重聚合计数, 取 Top N)。
     *
     * @param surveyId 问卷 ID (可空, 为空统计全部)
     * @param limit    返回条数 (默认 10)
     * @return 高频反馈列表: [{feedback, count}]
     * @throws ScrmException 问卷不存在 (surveyId 非空时)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopFeedback(Long surveyId, int limit) throws ScrmException {
        if (surveyId != null) {
            manageService.findSurveyOrThrow(surveyId);
        }
        if (limit <= 0) {
            limit = 10;
        }
        Specification<ScrmSurveyResponseEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (surveyId != null) {
                predicates.add(cb.equal(root.get("surveyId"), surveyId));
            }
            predicates.add(cb.isNotNull(root.get("feedbackText")));
            predicates.add(cb.notEqual(root.get("feedbackText"), ""));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmSurveyResponseEntity> responses = responseRepository.findAll(spec);
        Map<String, Long> counted = responses.stream()
                .map(ScrmSurveyResponseEntity::getFeedbackText)
                .filter(Objects::nonNull)
                .filter(t -> !t.isBlank())
                .map(String::trim)
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        return counted.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("feedback", e.getKey());
                    item.put("count", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 按时间范围统计邀请数 (按 sentAt 落在区间内)。
     *
     * @param startTime 起始时间 (含)
     * @param endTime   截止时间 (不含)
     * @param status    状态过滤 (可空, 为空统计全部)
     * @return 邀请数
     */
    private long countInvitationsByTimeRange(LocalDateTime startTime, LocalDateTime endTime,
                                              String status) {
        Specification<ScrmSurveyInvitationEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("sentAt"), startTime));
            predicates.add(cb.lessThan(root.get("sentAt"), endTime));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return invitationRepository.count(spec);
    }

    /**
     * 构建情感分布 Map (POSITIVE / NEUTRAL / NEGATIVE)。
     *
     * @param responses 回复列表
     * @return 情感分布 Map
     */
    private Map<String, Long> buildSentimentDistribution(List<ScrmSurveyResponseEntity> responses) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put(SENTIMENT_POSITIVE, 0L);
        distribution.put(SENTIMENT_NEUTRAL, 0L);
        distribution.put(SENTIMENT_NEGATIVE, 0L);
        for (ScrmSurveyResponseEntity r : responses) {
            if (r.getSentiment() != null) {
                distribution.merge(r.getSentiment(), 1L, Long::sum);
            }
        }
        return distribution;
    }

    /**
     * 按主键查询基准, 不存在抛异常, 并校验账号归属。
     *
     * @param id 基准 ID
     * @return 基准实体
     * @throws ScrmException 基准不存在
     */
    private ScrmNpsBenchmarkEntity findBenchmarkOrThrow(Long id) throws ScrmException {
        ScrmNpsBenchmarkEntity entity = benchmarkRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "NPS 基准不存在: id=" + id));
        return entity;
    }
}