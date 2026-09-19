/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackAnalysisService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmFeedbackCategoryEntity;
import org.hiylo.scrm.entity.ScrmFeedbackEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmFeedbackCategoryRepository;
import org.hiylo.scrm.repository.ScrmFeedbackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户反馈分析与统计服务: 情感分析、自动分类、标签提取、摘要生成,
 * 以及反馈统计 (总览/情感分布/分类统计/趋势/热点问题/响应时间/SLA 达标率)。
 * <p>
 * 情感分析采用关键词法, 自动分类按分类关键词映射匹配。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmFeedbackAnalysisService {

    /** 情感倾向: POSITIVE 正面 */
    private static final String SENTIMENT_POSITIVE = "POSITIVE";
    /** 情感倾向: NEUTRAL 中性 */
    private static final String SENTIMENT_NEUTRAL = "NEUTRAL";
    /** 情感倾向: NEGATIVE 负面 */
    private static final String SENTIMENT_NEGATIVE = "NEGATIVE";

    /** 反馈类型: SUGGESTION 建议 */
    private static final String TYPE_SUGGESTION = "SUGGESTION";
    /** 反馈类型: COMPLAINT 投诉 */
    private static final String TYPE_COMPLAINT = "COMPLAINT";
    /** 反馈类型: COMPLIMENT 表扬 */
    private static final String TYPE_COMPLIMENT = "COMPLIMENT";
    /** 反馈类型: BUG_REPORT 缺陷报告 */
    private static final String TYPE_BUG_REPORT = "BUG_REPORT";
    /** 反馈类型: FEATURE_REQUEST 功能需求 */
    private static final String TYPE_FEATURE_REQUEST = "FEATURE_REQUEST";
    /** 反馈类型: SERVICE_ISSUE 服务问题 */
    private static final String TYPE_SERVICE_ISSUE = "SERVICE_ISSUE";
    /** 反馈类型: PRODUCT_ISSUE 产品问题 */
    private static final String TYPE_PRODUCT_ISSUE = "PRODUCT_ISSUE";
    /** 反馈类型: OTHER 其他 */
    private static final String TYPE_OTHER = "OTHER";

    /** 状态: 新建 */
    private static final String STATUS_NEW = "NEW";
    /** 状态: 审核中 */
    private static final String STATUS_IN_REVIEW = "IN_REVIEW";
    /** 状态: 处理中 */
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 状态: 已解决 */
    private static final String STATUS_RESOLVED = "RESOLVED";
    /** 状态: 已关闭 */
    private static final String STATUS_CLOSED = "CLOSED";
    /** 状态: 已驳回 */
    private static final String STATUS_REJECTED = "REJECTED";
    /** 状态: 重复 */
    private static final String STATUS_DUPLICATE = "DUPLICATE";

    /** 默认 SLA 时长 (小时) */
    private static final int DEFAULT_SLA_HOURS = 48;
    /** 摘要最大长度 */
    private static final int SUMMARY_MAX_LENGTH = 200;

    /** 负面情感关键词 */
    private static final List<String> NEGATIVE_KEYWORDS = Arrays.asList(
            "差", "烂", "垃圾", "投诉", "不满", "失望", "生气", "愤怒", "糟糕", "坏", "慢", "等待",
            "无法", "不能", "问题", "错误", "故障", "崩溃", "卡", "退款", "赔偿", "骗", "坑",
            "bad", "terrible", "awful", "hate", "disappointed", "angry", "broken", "bug",
            "error", "fail", "slow", "useless", "waste", "worst", "horrible");
    /** 正面情感关键词 */
    private static final List<String> POSITIVE_KEYWORDS = Arrays.asList(
            "好", "棒", "优秀", "赞", "喜欢", "满意", "感谢", "谢谢", "推荐", "不错", "优质", "完美",
            "给力", "贴心", "专业", "great", "good", "excellent", "love", "like", "awesome",
            "perfect", "amazing", "wonderful", "thanks", "recommend", "happy", "satisfied", "best");

    /** 分类关键词映射 (分类 -> 关键词列表) */
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put(TYPE_PRODUCT_ISSUE, Arrays.asList(
                "产品", "质量", "损坏", "破损", "故障", "缺陷", "坏", "次品", "product", "quality", "defect", "broken"));
        CATEGORY_KEYWORDS.put(TYPE_SERVICE_ISSUE, Arrays.asList(
                "客服", "服务", "态度", "处理", "响应", "接待", "售后", "service", "support", "attitude"));
        CATEGORY_KEYWORDS.put(TYPE_BUG_REPORT, Arrays.asList(
                "bug", "错误", "报错", "异常", "崩溃", "闪退", "卡顿", "黑屏", "白屏", "error", "crash", "exception"));
        CATEGORY_KEYWORDS.put(TYPE_FEATURE_REQUEST, Arrays.asList(
                "希望", "期待", "功能", "增加", "新增", "能否", "建议增加", "feature", "request", "hope", "wish"));
        CATEGORY_KEYWORDS.put(TYPE_COMPLAINT, Arrays.asList(
                "投诉", "不满", "失望", "愤怒", "气愤", "complain", "unhappy", "disappointed"));
        CATEGORY_KEYWORDS.put(TYPE_COMPLIMENT, Arrays.asList(
                "表扬", "赞", "棒", "优秀", "感谢", "夸", "compliment", "praise"));
        CATEGORY_KEYWORDS.put(TYPE_SUGGESTION, Arrays.asList(
                "建议", "意见", "改进", "优化", "提升", "suggestion", "advice", "improve"));
    }

    /** 反馈数据访问层 */
    private final ScrmFeedbackRepository feedbackRepository;
    /** 反馈分类数据访问层 */
    private final ScrmFeedbackCategoryRepository categoryRepository;
    /** 反馈管理服务 (反馈存在性校验), 通过 @Lazy 打破 management↔analysis 循环依赖 */
    @Autowired
    @Lazy
    ScrmFeedbackManagementService managementService;

    // ============================================================
    // 分析 Analytics
    // ============================================================

    /**
     * 情感分析 (关键词法)。
     * <p>统计文本中正面与负面关键词出现次数, 正面多于负面返回 POSITIVE, 负面多于正面返回 NEGATIVE,
     * 相等或均无匹配返回 NEUTRAL。</p>
     *
     * @param text 待分析文本
     * @return 情感: POSITIVE / NEUTRAL / NEGATIVE
     */
    public String analyzeSentiment(String text) {
        if (text == null || text.isBlank()) {
            return SENTIMENT_NEUTRAL;
        }
        String lower = text.toLowerCase();
        int positive = 0;
        int negative = 0;
        for (String kw : POSITIVE_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                positive++;
            }
        }
        for (String kw : NEGATIVE_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                negative++;
            }
        }
        if (positive > negative) {
            return SENTIMENT_POSITIVE;
        }
        if (negative > positive) {
            return SENTIMENT_NEGATIVE;
        }
        return SENTIMENT_NEUTRAL;
    }

    /**
     * 自动分类 (关键词匹配)。
     * <p>按 {@link #CATEGORY_KEYWORDS} 顺序匹配, 返回首个命中的分类; 无命中返回 OTHER。</p>
     *
     * @param content 反馈内容
     * @return 反馈类型
     */
    public String autoCategorize(String content) {
        if (content == null || content.isBlank()) {
            return TYPE_OTHER;
        }
        String lower = content.toLowerCase();
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw.toLowerCase())) {
                    return entry.getKey();
                }
            }
        }
        return TYPE_OTHER;
    }

    /**
     * 提取标签 (关键词匹配)。
     * <p>从预定义标签词典中提取文本命中的标签, 逗号分隔返回; 无命中返回 null。</p>
     *
     * @param content 反馈内容
     * @return 标签字符串 (逗号分隔), 无命中返回 null
     */
    public String extractTags(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String lower = content.toLowerCase();
        Set<String> tags = new java.util.LinkedHashSet<>();
        // 标签词典: 标签名 -> 关键词列表
        Map<String, List<String>> tagDict = new LinkedHashMap<>();
        tagDict.put("紧急", Arrays.asList("紧急", "urgent", "立即", "马上"));
        tagDict.put("退款", Arrays.asList("退款", "退钱", "refund", "退货"));
        tagDict.put("质量", Arrays.asList("质量", "损坏", "破损", "quality", "defect"));
        tagDict.put("服务", Arrays.asList("客服", "服务", "态度", "service", "support"));
        tagDict.put("性能", Arrays.asList("慢", "卡", "卡顿", "崩溃", "闪退", "slow", "crash"));
        tagDict.put("体验", Arrays.asList("体验", "难用", "不友好", "ux", "experience"));
        tagDict.put("功能", Arrays.asList("功能", "希望", "建议", "feature", "request"));
        tagDict.put("物流", Arrays.asList("物流", "快递", "配送", "delivery", "shipping"));
        tagDict.put("价格", Arrays.asList("价格", "贵", "便宜", "费用", "price", "cost"));
        for (Map.Entry<String, List<String>> entry : tagDict.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw.toLowerCase())) {
                    tags.add(entry.getKey());
                    break;
                }
            }
        }
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    /**
     * 生成反馈摘要。
     * <p>截取反馈内容前 200 字符并附加情感标签; 内容为空时返回标题。</p>
     *
     * @param feedbackId 反馈 ID
     * @return 摘要文本
     * @throws ScrmException 反馈不存在
     */
    @Transactional(readOnly = true)
    public String generateSummary(Long feedbackId) throws ScrmException {
        ScrmFeedbackEntity entity = managementService.findFeedbackOrThrow(feedbackId);
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(entity.getFeedbackType()).append("]");
        if (entity.getContent() != null && !entity.getContent().isBlank()) {
            String content = entity.getContent();
            sb.append(content.length() > SUMMARY_MAX_LENGTH
                    ? content.substring(0, SUMMARY_MAX_LENGTH) + "..." : content);
        } else {
            sb.append(entity.getTitle());
        }
        if (entity.getSentiment() != null) {
            sb.append(" [情感: ").append(entity.getSentiment()).append("]");
        }
        return sb.toString();
    }

    // ============================================================
    // 统计 Stats
    // ============================================================

    /**
     * 反馈统计: 总数 / 各类型 / 各状态 / 平均评分 / 平均解决时长 / 满意度。
     * <p>时间范围按反馈创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFeedbackStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmFeedbackEntity> spec = buildTimeRangeSpec(startTime, endTime);
        List<ScrmFeedbackEntity> feedbacks = feedbackRepository.findAll(spec,
                Sort.by(Sort.Direction.DESC, "createTime"));
        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (String t : Arrays.asList(TYPE_SUGGESTION, TYPE_COMPLAINT, TYPE_COMPLIMENT, TYPE_BUG_REPORT,
                TYPE_FEATURE_REQUEST, TYPE_SERVICE_ISSUE, TYPE_PRODUCT_ISSUE, TYPE_OTHER)) {
            typeCount.put(t, 0L);
        }
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : Arrays.asList(STATUS_NEW, STATUS_IN_REVIEW, STATUS_IN_PROGRESS, STATUS_RESOLVED,
                STATUS_CLOSED, STATUS_REJECTED, STATUS_DUPLICATE)) {
            statusCount.put(s, 0L);
        }
        long ratingSum = 0;
        long ratingCount = 0;
        long satisfactionSum = 0;
        long satisfactionCount = 0;
        for (ScrmFeedbackEntity f : feedbacks) {
            if (f.getFeedbackType() != null) {
                typeCount.merge(f.getFeedbackType(), 1L, Long::sum);
            }
            if (f.getStatus() != null) {
                statusCount.merge(f.getStatus(), 1L, Long::sum);
            }
            if (f.getRating() != null) {
                ratingSum += f.getRating();
                ratingCount++;
            }
            if (f.getSatisfactionScore() != null) {
                satisfactionSum += f.getSatisfactionScore();
                satisfactionCount++;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) feedbacks.size());
        stats.put("byType", typeCount);
        stats.put("byStatus", statusCount);
        stats.put("avgRating", ratingCount > 0
                ? Math.round((double) ratingSum / ratingCount * 100d) / 100d : 0);
        stats.put("avgResolutionHours", feedbackRepository.avgResolutionHours(startTime, endTime));
        stats.put("avgSatisfaction", satisfactionCount > 0
                ? Math.round((double) satisfactionSum / satisfactionCount * 100d) / 100d : 0);
        stats.put("satisfactionCount", satisfactionCount);
        return stats;
    }

    /**
     * 情感分布统计: 各情感数量与占比。
     * <p>时间范围按反馈创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSentimentDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmFeedbackEntity> spec = buildTimeRangeSpec(startTime, endTime);
        List<ScrmFeedbackEntity> feedbacks = feedbackRepository.findAll(spec);
        Map<String, Long> sentimentCount = new LinkedHashMap<>();
        sentimentCount.put(SENTIMENT_POSITIVE, 0L);
        sentimentCount.put(SENTIMENT_NEUTRAL, 0L);
        sentimentCount.put(SENTIMENT_NEGATIVE, 0L);
        for (ScrmFeedbackEntity f : feedbacks) {
            if (f.getSentiment() != null) {
                sentimentCount.merge(f.getSentiment(), 1L, Long::sum);
            } else {
                sentimentCount.merge(SENTIMENT_NEUTRAL, 1L, Long::sum);
            }
        }
        long total = feedbacks.size();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("bySentiment", sentimentCount);
        Map<String, Object> percentage = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : sentimentCount.entrySet()) {
            percentage.put(e.getKey(), total > 0
                    ? Math.round((double) e.getValue() / total * 10000d) / 100d : 0);
        }
        stats.put("percentage", percentage);
        return stats;
    }

    /**
     * 分类统计: 各分类数量。
     * <p>时间范围按反馈创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCategoryStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmFeedbackEntity> spec = buildTimeRangeSpec(startTime, endTime);
        List<ScrmFeedbackEntity> feedbacks = feedbackRepository.findAll(spec);
        Map<String, Long> categoryCount = new LinkedHashMap<>();
        for (ScrmFeedbackEntity f : feedbacks) {
            String cat = f.getCategory() != null ? f.getCategory() : "UNCLASSIFIED";
            categoryCount.merge(cat, 1L, Long::sum);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) feedbacks.size());
        stats.put("byCategory", categoryCount);
        return stats;
    }

    /**
     * 反馈趋势: 按天统计最近 N 天的反馈数量, 支持按反馈类型过滤。
     *
     * @param days         天数 (统计最近 N 天, 含今天)
     * @param feedbackType 反馈类型过滤 (可空)
     * @return 趋势数据 (date + count)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTrend(int days, String feedbackType) {
        if (days <= 0) {
            days = 7;
        }
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1L);
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();
        Specification<ScrmFeedbackEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            predicates.add(cb.lessThan(root.get("createTime"), endTime));
            if (feedbackType != null && !feedbackType.isBlank()) {
                predicates.add(cb.equal(root.get("feedbackType"), feedbackType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmFeedbackEntity> feedbacks = feedbackRepository.findAll(spec);
        Map<LocalDate, Long> dailyCount = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            dailyCount.put(start.plusDays(i), 0L);
        }
        for (ScrmFeedbackEntity f : feedbacks) {
            if (f.getCreateTime() != null) {
                LocalDate day = f.getCreateTime().toLocalDate();
                dailyCount.merge(day, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Map.Entry<LocalDate, Long> e : dailyCount.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", e.getKey().format(fmt));
            item.put("count", e.getValue());
            trend.add(item);
        }
        return trend;
    }

    /**
     * 热点问题: 按分类汇总最近反馈, 返回 Top N。
     * <p>统计最近 30 天各分类的反馈数量, 按数量倒序返回前 limit 条。</p>
     *
     * @param limit 返回条数 (默认 10)
     * @return 热点问题列表 (category + count + percentage)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopIssues(int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        LocalDateTime startTime = LocalDate.now().minusDays(29).atStartOfDay();
        Specification<ScrmFeedbackEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmFeedbackEntity> feedbacks = feedbackRepository.findAll(spec);
        Map<String, Long> categoryCount = new LinkedHashMap<>();
        for (ScrmFeedbackEntity f : feedbacks) {
            String cat = f.getCategory() != null ? f.getCategory() : "UNCLASSIFIED";
            categoryCount.merge(cat, 1L, Long::sum);
        }
        long total = feedbacks.size();
        List<Map<String, Object>> result = new ArrayList<>();
        categoryCount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .forEach(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("category", e.getKey());
                    item.put("count", e.getValue());
                    item.put("percentage", total > 0
                            ? Math.round((double) e.getValue() / total * 10000d) / 100d : 0);
                    result.add(item);
                });
        return result;
    }

    /**
     * 响应时间统计: 平均响应时长与平均解决时长。
     * <p>时间范围按反馈创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getResponseTimeStats(LocalDateTime startTime, LocalDateTime endTime) {
        Double avgResponse = feedbackRepository.avgResponseHours(startTime, endTime);
        Double avgResolution = feedbackRepository.avgResolutionHours(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("avgResponseHours", avgResponse != null ? Math.round(avgResponse * 100d) / 100d : 0);
        stats.put("avgResolutionHours", avgResolution != null ? Math.round(avgResolution * 100d) / 100d : 0);
        return stats;
    }

    /**
     * SLA 达标率统计。
     * <p>按反馈分类的 slaHours 作为 SLA 阈值, 统计已解决/已关闭反馈中解决时长不超过 SLA 的占比。
     * 无 SLA 配置时按默认 48 小时计算。时间范围按反馈创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 (total / compliant / violation / complianceRate)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSlaCompliance(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmFeedbackEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("resolutionTimeHours").isNotNull());
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmFeedbackEntity> feedbacks = feedbackRepository.findAll(spec);
        // 加载分类 SLA 映射
        Map<String, Integer> categorySla = new LinkedHashMap<>();
        List<ScrmFeedbackCategoryEntity> categories = categoryRepository.findAll();
        for (ScrmFeedbackCategoryEntity c : categories) {
            if (c.getSlaHours() != null) {
                categorySla.put(c.getCategoryCode(), c.getSlaHours());
            }
        }
        long total = feedbacks.size();
        long compliant = 0;
        long violation = 0;
        for (ScrmFeedbackEntity f : feedbacks) {
            int sla = DEFAULT_SLA_HOURS;
            if (f.getCategory() != null && categorySla.containsKey(f.getCategory())) {
                sla = categorySla.get(f.getCategory());
            }
            if (f.getResolutionTimeHours() != null && f.getResolutionTimeHours() <= sla) {
                compliant++;
            } else {
                violation++;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("compliant", compliant);
        stats.put("violation", violation);
        stats.put("complianceRate", total > 0
                ? Math.round((double) compliant / total * 10000d) / 100d : 0);
        return stats;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 构建时间范围查询条件 Specification (按创建时间过滤)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return Specification
     */
    private Specification<ScrmFeedbackEntity> buildTimeRangeSpec(LocalDateTime startTime, LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}