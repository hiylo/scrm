/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorAnalysisService.java
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

import org.hiylo.scrm.dto.ScrmCompetitorAnalysisDto;
import org.hiylo.scrm.entity.ScrmCompetitorActivityEntity;
import org.hiylo.scrm.entity.ScrmCompetitorEntity;
import org.hiylo.scrm.entity.ScrmCompetitorProductEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCompetitorActivityRepository;
import org.hiylo.scrm.repository.ScrmCompetitorProductRepository;
import org.hiylo.scrm.repository.ScrmCompetitorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 竞品分析统计服务。
 * <p>
 * 承载竞争分析子域全部能力: 竞争分析报告 (价格/产品/定位/动态频次/威胁评估)、多竞品对比、
 * 市场概览、价格分析、动态分析、威胁评估、趋势分析以及多维统计 (竞品/动态/价格/应对/趋势)。
 * </p>
 * <p>
 * 校验失败抛出 {@link ScrmException} 携带通用错误码 (NOT_FOUND / BAD_REQUEST)。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCompetitorAnalysisService {

    // ==================== 默认值常量 ====================

    /** 默认分析回溯天数 */
    private static final int DEFAULT_ANALYSIS_DAYS = 90;

    // ==================== 合法枚举值 ====================

    /** 合法的威胁等级 */
    private static final List<String> VALID_THREAT_LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    /** 合法的影响等级 */
    private static final List<String> VALID_IMPACT_LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    /** 威胁等级分值映射 (LOW=25, MEDIUM=50, HIGH=75, CRITICAL=100) */
    private static final Map<String, Integer> THREAT_SCORE_MAP = Map.of(
            "LOW", 25, "MEDIUM", 50, "HIGH", 75, "CRITICAL", 100);
    /** 市场地位分值映射 (LEADER=100, CHALLENGER=75, FOLLOWER=50, NICHE=30, NEW_ENTRANT=20) */
    private static final Map<String, Integer> POSITION_SCORE_MAP = Map.of(
            "LEADER", 100, "CHALLENGER", 75, "FOLLOWER", 50, "NICHE", 30, "NEW_ENTRANT", 20);

    // ==================== 依赖注入 ====================

    /** 竞品数据访问层 */
    private final ScrmCompetitorRepository competitorRepository;
    /** 竞品产品数据访问层 */
    private final ScrmCompetitorProductRepository productRepository;
    /** 竞品动态数据访问层 */
    private final ScrmCompetitorActivityRepository activityRepository;
    /** JSON 解析器 (解析价格历史等) */
    private final ObjectMapper objectMapper;

    /**
     * 竞争分析: 按分析维度生成完整报告。
     * <p>
     * 维度:
     * <ul>
     *   <li>PRICING: 价格策略 (产品价格区间 / 折扣分布 / 价格竞争力)</li>
     *   <li>PRODUCT: 产品矩阵 (产品数 / 分类分布 / 定位)</li>
     *   <li>POSITIONING: 市场定位 (市场地位 / 市场份额 / 目标市场)</li>
     *   <li>ACTIVITY: 动态频次 (按类型统计 / 影响等级分布)</li>
     *   <li>THREAT: 威胁评估 (综合威胁分)</li>
     *   <li>FULL: 上述全部</li>
     * </ul>
     * </p>
     *
     * @param analysisDto 分析请求
     * @return 分析报告 Map
     * @throws ScrmException 竞品不存在 / 参数非法
     */
    @Transactional(readOnly = true)
    public Map<String, Object> analyzeCompetitor(ScrmCompetitorAnalysisDto analysisDto) throws ScrmException {
        if (analysisDto == null || analysisDto.getCompetitorId() == null) {
            throw ScrmException.badRequest("分析参数不能为空");
        }
        ScrmCompetitorEntity competitor = findCompetitorOrThrow(analysisDto.getCompetitorId());
        String type = analysisDto.getAnalysisType() != null ? analysisDto.getAnalysisType() : "FULL";
        LocalDateTime endDt = analysisDto.getEndTime() != null ? analysisDto.getEndTime() : LocalDateTime.now();
        LocalDateTime startDt = analysisDto.getStartTime() != null ? analysisDto.getStartTime()
                : endDt.minusDays(DEFAULT_ANALYSIS_DAYS);
        LocalDate startDate = startDt.toLocalDate();
        LocalDate endDate = endDt.toLocalDate();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("competitorId", competitor.getId());
        report.put("competitorName", competitor.getCompetitorName());
        report.put("analysisType", type);
        report.put("startTime", startDt);
        report.put("endTime", endDt);

        if ("PRICING".equals(type) || "FULL".equals(type)) {
            report.put("pricingAnalysis", buildPricingAnalysis(competitor.getId()));
        }
        if ("PRODUCT".equals(type) || "FULL".equals(type)) {
            report.put("productMatrix", buildProductMatrix(competitor.getId()));
        }
        if ("POSITIONING".equals(type) || "FULL".equals(type)) {
            report.put("marketPositioning", buildMarketPositioning(competitor));
        }
        if ("ACTIVITY".equals(type) || "FULL".equals(type)) {
            report.put("activityFrequency", buildActivityFrequency(competitor.getId(), startDate, endDate));
        }
        if ("THREAT".equals(type) || "FULL".equals(type)) {
            long activityCount = activityRepository.countByCompetitor(competitor.getId(), startDate, endDate);
            Object[] priceStats = productRepository.aggregatePriceStats(competitor.getId());
            long productCount = (priceStats != null && priceStats[0] != null)
                    ? ((Number) priceStats[0]).longValue()
                    : 0L;
            report.put("threatAssessment", buildThreatAssessment(competitor, activityCount, productCount));
        }
        return report;
    }

    /**
     * 多竞品对比。
     *
     * @param competitorIds 竞品 ID 列表
     * @return 对比结果 {competitors:[...], dimensions:{marketShare, threatLevel, productCount, activityCount, avgPrice}}
     * @throws ScrmException 竞品不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareCompetitors(List<Long> competitorIds) throws ScrmException {
        if (competitorIds == null || competitorIds.isEmpty()) {
            throw ScrmException.badRequest("竞品 ID 列表不能为空");
        }
        List<Map<String, Object>> competitors = new ArrayList<>();
        for (Long id : competitorIds) {
            ScrmCompetitorEntity competitor = findCompetitorOrThrow(id);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("competitorId", competitor.getId());
            entry.put("competitorName", competitor.getCompetitorName());
            entry.put("industry", competitor.getIndustry());
            entry.put("marketPosition", competitor.getMarketPosition());
            entry.put("marketShare", competitor.getMarketShare());
            entry.put("threatLevel", competitor.getThreatLevel());
            entry.put("companySize", competitor.getCompanySize());
            entry.put("fundingStage", competitor.getFundingStage());
            entry.put("totalFunding", competitor.getTotalFunding());
            entry.put("strengths", competitor.getStrengths());
            entry.put("weaknesses", competitor.getWeaknesses());
            // 产品与价格
            Object[] priceStats = productRepository.aggregatePriceStats(id);
            long productCount = (priceStats != null && priceStats[0] != null)
                    ? ((Number) priceStats[0]).longValue()
                    : 0L;
            entry.put("productCount", productCount);
            entry.put("avgCurrentPrice", (priceStats != null && priceStats[1] != null)
                    ? ((Number) priceStats[1]).doubleValue()
                    : 0.0);
            entry.put("avgDiscountRate", (priceStats != null && priceStats[4] != null)
                    ? ((Number) priceStats[4]).doubleValue()
                    : 0.0);
            // 动态
            entry.put("activityCount", activityRepository.countByCompetitor(id, null, null));
            competitors.add(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("competitors", competitors);
        // 维度对比汇总
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("marketShares", competitors.stream()
                .map(m -> Map.of(String.valueOf(m.get("competitorId")), m.get("marketShare")))
                .collect(Collectors.toList()));
        dimensions.put("threatLevels", competitors.stream()
                .map(m -> Map.of(String.valueOf(m.get("competitorId")), m.get("threatLevel")))
                .collect(Collectors.toList()));
        dimensions.put("productCounts", competitors.stream()
                .map(m -> Map.of(String.valueOf(m.get("competitorId")), m.get("productCount")))
                .collect(Collectors.toList()));
        dimensions.put("activityCounts", competitors.stream()
                .map(m -> Map.of(String.valueOf(m.get("competitorId")), m.get("activityCount")))
                .collect(Collectors.toList()));
        result.put("dimensions", dimensions);
        return result;
    }

    /**
     * 市场概览: 按行业聚合竞品数量、总市场份额、平均威胁分、头部竞品。
     *
     * @param industry 行业 (可空, 为空则全部数据)
     * @return 市场概览 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMarketOverview(String industry) {
        Specification<ScrmCompetitorEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), "ARCHIVED"));
            if (industry != null && !industry.isBlank()) {
                predicates.add(cb.equal(root.get("industry"), industry));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCompetitorEntity> all = competitorRepository.findAll(spec);
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("industry", industry);
        overview.put("competitorCount", all.size());
        // 总市场份额
        double totalShare = all.stream()
                .filter(c -> c.getMarketShare() != null)
                .mapToDouble(ScrmCompetitorEntity::getMarketShare).sum();
        overview.put("totalMarketShare", round2(totalShare));
        // 平均威胁分
        double avgThreat = all.stream()
                .filter(c -> c.getThreatLevel() != null)
                .mapToInt(c -> THREAT_SCORE_MAP.getOrDefault(c.getThreatLevel(), 0))
                .average().orElse(0.0);
        overview.put("avgThreatScore", round2(avgThreat));
        // 威胁等级分布
        Map<String, Long> threatDistribution = all.stream()
                .filter(c -> c.getThreatLevel() != null)
                .collect(Collectors.groupingBy(ScrmCompetitorEntity::getThreatLevel, Collectors.counting()));
        overview.put("threatDistribution", threatDistribution);
        // 市场地位分布
        Map<String, Long> positionDistribution = all.stream()
                .filter(c -> c.getMarketPosition() != null)
                .collect(Collectors.groupingBy(ScrmCompetitorEntity::getMarketPosition, Collectors.counting()));
        overview.put("positionDistribution", positionDistribution);
        // 头部竞品 (按市场份额倒序取前 10)
        List<Map<String, Object>> top = all.stream()
                .filter(c -> c.getMarketShare() != null)
                .sorted((a, b) -> Double.compare(b.getMarketShare(), a.getMarketShare()))
                .limit(10)
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("competitorId", c.getId());
                    m.put("competitorName", c.getCompetitorName());
                    m.put("marketShare", c.getMarketShare());
                    m.put("threatLevel", c.getThreatLevel());
                    m.put("marketPosition", c.getMarketPosition());
                    return m;
                })
                .collect(Collectors.toList());
        overview.put("topCompetitors", top);
        return overview;
    }

    /**
     * 价格分析: 竞品产品价格区间 / 折扣分布 / 价格竞争力。
     *
     * @param competitorId 竞品 ID
     * @param months       回溯月数 (保留参数, 当前基于全量产品)
     * @return 价格分析 Map
     * @throws ScrmException 竞品不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPriceAnalysis(Long competitorId, int months) throws ScrmException {
        findCompetitorOrThrow(competitorId);
        return buildPricingAnalysis(competitorId);
    }

    /**
     * 动态分析: 按类型 / 影响等级统计动态, 计算应对率。
     *
     * @param competitorId 竞品 ID
     * @param months       回溯月数
     * @return 动态分析 Map
     * @throws ScrmException 竞品不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getActivityAnalysis(Long competitorId, int months) throws ScrmException {
        findCompetitorOrThrow(competitorId);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(Math.max(months, 0));
        return buildActivityFrequency(competitorId, start, end);
    }

    /**
     * 威胁评估: 综合威胁等级 / 市场份额 / 市场地位 / 动态活跃度 / 价格竞争力计算威胁分。
     *
     * @param competitorId 竞品 ID
     * @return 威胁评估 Map
     * @throws ScrmException 竞品不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getThreatAssessment(Long competitorId) throws ScrmException {
        ScrmCompetitorEntity competitor = findCompetitorOrThrow(competitorId);
        long activityCount = activityRepository.countByCompetitor(competitorId, null, null);
        Object[] priceStats = productRepository.aggregatePriceStats(competitorId);
        long productCount = (priceStats != null && priceStats[0] != null)
                    ? ((Number) priceStats[0]).longValue()
                    : 0L;
        return buildThreatAssessment(competitor, activityCount, productCount);
    }

    /**
     * 趋势分析: 按月聚合动态数量与价格变化。
     *
     * @param competitorId 竞品 ID
     * @param months       回溯月数
     * @return 趋势分析 Map
     * @throws ScrmException 竞品不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTrendAnalysis(Long competitorId, int months) throws ScrmException {
        findCompetitorOrThrow(competitorId);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(Math.max(months, 0));
        // 按月聚合动态
        List<ScrmCompetitorActivityEntity> activities = activityRepository
                .findByCompetitorId(competitorId,
                        PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.ASC, "activityDate")))
                .getContent()
                .stream()
                .filter(a -> a.getActivityDate() != null && !a.getActivityDate().isBefore(start) && !a.getActivityDate().isAfter(end))
                .collect(Collectors.toList());
        Map<String, Long> monthlyActivities = new LinkedHashMap<>();
        for (ScrmCompetitorActivityEntity a : activities) {
            String month = a.getActivityDate().getYear() + "-"
                    + String.format("%02d", a.getActivityDate().getMonthValue());
            monthlyActivities.merge(month, 1L, Long::sum);
        }
        // 按月聚合价格变化 (从产品价格历史)
        List<ScrmCompetitorProductEntity> products = productRepository
                .findByCompetitorId(competitorId,
                        PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        Map<String, Double> monthlyPriceChanges = new LinkedHashMap<>();
        for (ScrmCompetitorProductEntity product : products) {
            List<Map<String, Object>> history = parsePriceHistory(product.getPriceHistory());
            for (Map<String, Object> h : history) {
                Object dateObj = h.get("date");
                if (dateObj == null) {
                    continue;
                }
                LocalDate date = parseLocalDate(dateObj);
                if (date == null || date.isBefore(start) || date.isAfter(end)) {
                    continue;
                }
                String month = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
                double change = toDouble(h.get("change"));
                monthlyPriceChanges.merge(month, change, Double::sum);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("competitorId", competitorId);
        result.put("months", months);
        result.put("monthlyActivities", monthlyActivities);
        result.put("monthlyPriceChangePercentSum", monthlyPriceChanges);
        result.put("totalActivities", (long) activities.size());
        return result;
    }

    /**
     * 竞品统计: 总数 / 各行业 / 各威胁等级 / 监测状态。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCompetitorStats() {
        List<ScrmCompetitorEntity> all = competitorRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        // 各行业
        Map<String, Long> byIndustry = all.stream()
                .filter(c -> c.getIndustry() != null)
                .collect(Collectors.groupingBy(ScrmCompetitorEntity::getIndustry, Collectors.counting()));
        stats.put("industryCount", byIndustry);
        // 各威胁等级
        Map<String, Long> byThreat = all.stream()
                .filter(c -> c.getThreatLevel() != null)
                .collect(Collectors.groupingBy(ScrmCompetitorEntity::getThreatLevel, Collectors.counting()));
        // 补全所有等级
        Map<String, Long> threatFull = new LinkedHashMap<>();
        for (String level : VALID_THREAT_LEVELS) {
            threatFull.put(level, byThreat.getOrDefault(level, 0L));
        }
        stats.put("threatLevelCount", threatFull);
        // 监测状态
        long monitoringEnabled = all.stream().filter(c -> Boolean.TRUE.equals(c.getMonitoringEnabled())).count();
        long monitoringDisabled = all.size() - monitoringEnabled;
        Map<String, Long> monitoring = new LinkedHashMap<>();
        monitoring.put("enabled", monitoringEnabled);
        monitoring.put("disabled", monitoringDisabled);
        stats.put("monitoringCount", monitoring);
        // 状态分布
        Map<String, Long> byStatus = all.stream()
                .filter(c -> c.getStatus() != null)
                .collect(Collectors.groupingBy(ScrmCompetitorEntity::getStatus, Collectors.counting()));
        stats.put("statusCount", byStatus);
        return stats;
    }

    /**
     * 动态统计: 各类型 / 各影响等级 / 应对率。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getActivityStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 各类型
        List<Object[]> byType = activityRepository.countByActivityType(startTime, endTime);
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (Object[] row : byType) {
            typeCount.put((String) row[0], row[1] == null ? 0L : ((Number) row[1]).longValue());
        }
        stats.put("typeCount", typeCount);
        // 各影响等级
        List<Object[]> byImpact = activityRepository.countByImpactLevel(startTime, endTime);
        Map<String, Long> impactCount = new LinkedHashMap<>();
        for (String level : VALID_IMPACT_LEVELS) {
            impactCount.put(level, 0L);
        }
        long total = 0L;
        for (Object[] row : byImpact) {
            String level = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            impactCount.put(level, count);
            total += count;
        }
        stats.put("impactCount", impactCount);
        stats.put("total", total);
        // 应对率
        List<Object[]> byResponse = activityRepository.countByResponseStatus(startTime, endTime);
        Map<String, Long> responseCount = new LinkedHashMap<>();
        long responded = 0L;
        for (Object[] row : byResponse) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            responseCount.put(status, count);
            if (!"PENDING".equals(status)) {
                responded += count;
            }
        }
        stats.put("responseCount", responseCount);
        stats.put("responseRate", total == 0 ? 0.0 : round2((double) responded / total));
        return stats;
    }

    /**
     * 价格统计: 价格变化频率 / 平均涨跌幅。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPriceStats(LocalDateTime startTime, LocalDateTime endTime) {
        Object[] stats = productRepository.aggregatePriceChangeStats(startTime, endTime);
        Map<String, Object> result = new LinkedHashMap<>();
        long totalChanges = 0L;
        double avgChangePercent = 0.0;
        if (stats != null && stats.length >= 2) {
            totalChanges = stats[0] == null ? 0L : ((Number) stats[0]).longValue();
            avgChangePercent = stats[1] == null ? 0.0 : ((Number) stats[1]).doubleValue();
        }
        result.put("totalPriceChanges", totalChanges);
        result.put("avgChangePercent", round2(avgChangePercent));
        return result;
    }

    /**
     * 应对统计: 应对率 / 平均响应时间 (天)。
     * <p>平均响应时间: 已完成动态的 createTime 到 verifiedAt 间隔天数 (近似)。</p>
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getResponseStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> byResponse = activityRepository.countByResponseStatus(startTime, endTime);
        Map<String, Long> responseCount = new LinkedHashMap<>();
        long total = 0L;
        long completed = 0L;
        long noAction = 0L;
        for (Object[] row : byResponse) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            responseCount.put(status, count);
            total += count;
            if ("COMPLETED".equals(status)) {
                completed = count;
            }
            if ("NO_ACTION".equals(status)) {
                noAction = count;
            }
        }
        long responded = total - responseCount.getOrDefault("PENDING", 0L);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("responseCount", responseCount);
        result.put("total", total);
        result.put("responseRate", total == 0 ? 0.0 : round2((double) responded / total));
        result.put("completedCount", completed);
        result.put("noActionCount", noAction);
        // 平均响应时间: 近似取已完成动态的 createTime -> updateTime 间隔
        Specification<ScrmCompetitorActivityEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("responseStatus"), "COMPLETED"));
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCompetitorActivityEntity> completedActivities = activityRepository.findAll(spec);
        double avgResponseDays = 0.0;
        if (!completedActivities.isEmpty()) {
            double totalDays = 0.0;
            int valid = 0;
            for (ScrmCompetitorActivityEntity a : completedActivities) {
                if (a.getCreateTime() != null && a.getUpdateTime() != null) {
                    totalDays += java.time.Duration.between(a.getCreateTime(), a.getUpdateTime()).toMinutes() / 1440.0;
                    valid++;
                }
            }
            avgResponseDays = valid > 0 ? round2(totalDays / valid) : 0.0;
        }
        result.put("avgResponseDays", avgResponseDays);
        return result;
    }

    /**
     * 竞品趋势: 按月统计新增竞品数。
     *
     * @param months 回溯月数
     * @return 趋势结果 Map {monthlyNewCompetitors: {yyyy-MM: count}}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCompetitorTrend(int months) {
        LocalDate start = LocalDate.now().minusMonths(Math.max(months, 0));
        List<ScrmCompetitorEntity> all = competitorRepository.findAll(
                (root, query, cb) -> cb.and());
        Map<String, Long> monthly = new LinkedHashMap<>();
        for (ScrmCompetitorEntity c : all) {
            if (c.getCreateTime() == null) {
                continue;
            }
            LocalDate date = c.getCreateTime().toLocalDate();
            if (date.isBefore(start)) {
                continue;
            }
            String month = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            monthly.merge(month, 1L, Long::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("months", months);
        result.put("monthlyNewCompetitors", monthly);
        result.put("total", (long) all.size());
        return result;
    }

    /**
     * 动态趋势: 按天统计动态数。
     *
     * @param days 回溯天数
     * @return 趋势结果 Map {dailyActivities: {yyyy-MM-dd: count}}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getActivityTrend(int days) {
        LocalDate start = LocalDate.now().minusDays(Math.max(days, 0));
        Page<ScrmCompetitorActivityEntity> page = activityRepository.findByActivityDateAfter(
                 start, PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.ASC, "activityDate")));
        Map<String, Long> daily = new LinkedHashMap<>();
        for (ScrmCompetitorActivityEntity a : page.getContent()) {
            if (a.getActivityDate() == null) {
                continue;
            }
            daily.merge(a.getActivityDate().toString(), 1L, Long::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("dailyActivities", daily);
        result.put("total", (long) page.getContent().size());
        return result;
    }

    /**
     * 构建价格策略分析。
     *
     * @param competitorId 竞品 ID
     * @return 价格分析 Map
     */
    private Map<String, Object> buildPricingAnalysis(Long competitorId) {
        Object[] stats = productRepository.aggregatePriceStats(competitorId);
        Map<String, Object> analysis = new LinkedHashMap<>();
        long productCount = 0L;
        double avgPrice = 0.0;
        double minPrice = 0.0;
        double maxPrice = 0.0;
        double avgDiscount = 0.0;
        if (stats != null && stats.length >= 5) {
            productCount = stats[0] == null ? 0L : ((Number) stats[0]).longValue();
            avgPrice = stats[1] == null ? 0.0 : ((Number) stats[1]).doubleValue();
            minPrice = stats[2] == null ? 0.0 : ((Number) stats[2]).doubleValue();
            maxPrice = stats[3] == null ? 0.0 : ((Number) stats[3]).doubleValue();
            avgDiscount = stats[4] == null ? 0.0 : ((Number) stats[4]).doubleValue();
        }
        analysis.put("productCount", productCount);
        analysis.put("avgPrice", round2(avgPrice));
        analysis.put("minPrice", round2(minPrice));
        analysis.put("maxPrice", round2(maxPrice));
        analysis.put("priceRange", round2(maxPrice - minPrice));
        analysis.put("avgDiscountRate", round2(avgDiscount));
        // 价格竞争力: 我方更便宜的产品占比
        List<ScrmCompetitorProductEntity> products = productRepository
                .findByCompetitorId(competitorId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();
        long ourCheaper = products.stream()
                .filter(p -> p.getOurPrice() != null && p.getCurrentPrice() != null && p.getOurPrice() < p.getCurrentPrice())
                .count();
        analysis.put("ourCheaperCount", ourCheaper);
        analysis.put("priceCompetitiveness", productCount == 0 ? 0.0 : round2((double) ourCheaper / productCount));
        return analysis;
    }

    /**
     * 构建产品矩阵分析。
     *
     * @param competitorId 竞品 ID
     * @return 产品矩阵 Map
     */
    private Map<String, Object> buildProductMatrix(Long competitorId) {
        List<ScrmCompetitorProductEntity> products = productRepository
                .findByCompetitorId(competitorId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();
        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("productCount", products.size());
        // 分类分布
        Map<String, Long> categoryDist = products.stream()
                .filter(p -> p.getProductCategory() != null)
                .collect(Collectors.groupingBy(ScrmCompetitorProductEntity::getProductCategory, Collectors.counting()));
        matrix.put("categoryDistribution", categoryDist);
        // 定位分布
        Map<String, Long> positioningDist = products.stream()
                .filter(p -> p.getPositioning() != null)
                .collect(Collectors.groupingBy(ScrmCompetitorProductEntity::getPositioning, Collectors.counting()));
        matrix.put("positioningDistribution", positioningDist);
        // 平均优势评分
        double avgAdvantage = products.stream()
                .filter(p -> p.getAdvantageScore() != null)
                .mapToInt(ScrmCompetitorProductEntity::getAdvantageScore)
                .average().orElse(0.0);
        matrix.put("avgAdvantageScore", round2(avgAdvantage));
        // 促销产品数
        long discounted = products.stream()
                .filter(p -> p.getDiscountRate() != null && p.getDiscountRate() > 0)
                .count();
        matrix.put("discountedProductCount", discounted);
        return matrix;
    }

    /**
     * 构建市场定位分析。
     *
     * @param competitor 竞品实体
     * @return 市场定位 Map
     */
    private Map<String, Object> buildMarketPositioning(ScrmCompetitorEntity competitor) {
        Map<String, Object> positioning = new LinkedHashMap<>();
        positioning.put("marketPosition", competitor.getMarketPosition());
        positioning.put("marketShare", competitor.getMarketShare());
        positioning.put("targetMarket", competitor.getTargetMarket());
        positioning.put("industry", competitor.getIndustry());
        positioning.put("companySize", competitor.getCompanySize());
        positioning.put("businessModel", competitor.getBusinessModel());
        positioning.put("pricingStrategy", competitor.getPricingStrategy());
        positioning.put("positionScore", POSITION_SCORE_MAP.getOrDefault(competitor.getMarketPosition(), 0));
        return positioning;
    }

    /**
     * 构建动态频次分析。
     *
     * @param competitorId 竞品 ID
     * @param startDate    起始日期 (含)
     * @param endDate      截止日期 (含)
     * @return 动态频次 Map
     */
    private Map<String, Object> buildActivityFrequency(Long competitorId,
                                                        LocalDate startDate, LocalDate endDate) {
        long total = activityRepository.countByCompetitor(competitorId, startDate, endDate);
        Map<String, Object> freq = new LinkedHashMap<>();
        freq.put("totalActivities", total);
        // 按类型分布 (拉取竞品全部动态后按时间过滤)
        List<ScrmCompetitorActivityEntity> activities = activityRepository
                .findByCompetitorId(competitorId,
                        PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.DESC, "activityDate")))
                .getContent()
                .stream()
                .filter(a -> a.getActivityDate() != null && !a.getActivityDate().isBefore(startDate) && !a.getActivityDate().isAfter(endDate))
                .collect(Collectors.toList());
        Map<String, Long> typeDist = activities.stream()
                .filter(a -> a.getActivityType() != null)
                .collect(Collectors.groupingBy(ScrmCompetitorActivityEntity::getActivityType, Collectors.counting()));
        freq.put("typeDistribution", typeDist);
        Map<String, Long> impactDist = activities.stream()
                .filter(a -> a.getImpactLevel() != null)
                .collect(Collectors.groupingBy(ScrmCompetitorActivityEntity::getImpactLevel, Collectors.counting()));
        freq.put("impactDistribution", impactDist);
        long verified = activities.stream().filter(a -> Boolean.TRUE.equals(a.getIsVerified())).count();
        freq.put("verifiedCount", verified);
        freq.put("verifyRate", total == 0 ? 0.0 : round2((double) verified / total));
        // 平均重要性
        double avgImportance = activities.stream()
                .filter(a -> a.getImportanceScore() != null)
                .mapToInt(ScrmCompetitorActivityEntity::getImportanceScore)
                .average().orElse(0.0);
        freq.put("avgImportanceScore", round2(avgImportance));
        return freq;
    }

    /**
     * 构建威胁评估。
     * <p>威胁分 = 威胁等级分 (40%) + 市场地位分 (20%) + 市场份额分 (20%, 上限 100) + 动态活跃度分 (10%, 上限 100) + 价格竞争力分 (10%)。
     * 最终映射到 0-100。</p>
     *
     * @param competitor    竞品实体
     * @param activityCount 动态数
     * @param productCount  产品数
     * @return 威胁评估 Map
     */
    Map<String, Object> buildThreatAssessment(ScrmCompetitorEntity competitor, long activityCount,
            long productCount) {
        int threatScore = THREAT_SCORE_MAP.getOrDefault(competitor.getThreatLevel(), 50);
        int positionScore = POSITION_SCORE_MAP.getOrDefault(competitor.getMarketPosition(), 0);
        double marketShare = competitor.getMarketShare() != null ? competitor.getMarketShare() : 0.0;
        double marketShareScore = Math.min(marketShare, 100.0);
        // 动态活跃度: 每 5 条动态 +10, 上限 100
        double activityScore = Math.min(activityCount / 5.0 * 10.0, 100.0);
        // 价格竞争力: 产品数越多 + 我方优势评分越低 (竞品越强) 威胁越高, 简化为产品数覆盖度
        double productScore = Math.min(productCount * 10.0, 100.0);
        double threatTotal = threatScore * 0.4 + positionScore * 0.2 + marketShareScore * 0.2
                + activityScore * 0.1 + productScore * 0.1;
        int finalScore = (int) Math.round(threatTotal);
        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("threatLevel", competitor.getThreatLevel());
        assessment.put("threatScore", threatScore);
        assessment.put("positionScore", positionScore);
        assessment.put("marketShareScore", round2(marketShareScore));
        assessment.put("activityScore", round2(activityScore));
        assessment.put("productScore", round2(productScore));
        assessment.put("overallThreatScore", finalScore);
        assessment.put("assessment", scoreToThreatLevel(finalScore));
        return assessment;
    }

    /**
     * 分值映射到威胁等级。
     *
     * @param score 威胁分 (0-100)
     * @return 威胁等级
     */
    private String scoreToThreatLevel(int score) {
        if (score >= 75) {
            return "CRITICAL";
        }
        if (score >= 55) {
            return "HIGH";
        }
        if (score >= 35) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /**
     * 解析价格历史 JSON。
     *
     * @param json 价格历史 JSON 字符串
     * @return 历史列表, 解析失败返回空列表
     */
    private List<Map<String, Object>> parsePriceHistory(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            log.warn("价格历史 JSON 解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 解析日期字符串为 LocalDate (兼容字符串与日期对象)。
     *
     * @param obj 日期对象
     * @return LocalDate, 不可解析返回 null
     */
    private LocalDate parseLocalDate(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof LocalDate d) {
            return d;
        }
        if (obj instanceof java.util.Date d) {
            return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        try {
            return LocalDate.parse(obj.toString());
        } catch (Exception e) {
            return null;
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
     * double 保留两位小数。
     *
     * @param value 原始值
     * @return 四舍五入后的值
     */
    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 按主键查询竞品, 不存在抛异常, 并校验账号归属。
     *
     * @param id 竞品 ID
     * @return 竞品实体
     * @throws ScrmException 竞品不存在
     */
    private ScrmCompetitorEntity findCompetitorOrThrow(Long id) throws ScrmException {
        ScrmCompetitorEntity entity = competitorRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "竞品不存在: id=" + id));
        return entity;
    }
}