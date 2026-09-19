/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyMatchService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAutoReplyLogDto;
import org.hiylo.scrm.dto.ScrmAutoReplyMatchDto;
import org.hiylo.scrm.dto.ScrmAutoReplyTestDto;
import org.hiylo.scrm.entity.ScrmAutoReplyLogEntity;
import org.hiylo.scrm.entity.ScrmAutoReplyRuleEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAutoReplyLogRepository;
import org.hiylo.scrm.repository.ScrmAutoReplyRuleRepository;
import org.hiylo.scrm.repository.ScrmAutoReplyTemplateRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * SCRM 自动回复匹配引擎服务。
 * <p>
 * 在收到客户消息时加载当前账号全部启用规则 (按 priority ASC), 逐条评估 matchType
 * (EXACT/CONTAINS/STARTS_WITH/ENDS_WITH/REGEX/FUZZY) 与 matchScope, 命中后返回回复内容
 * 并模拟发送 (sendReply 模拟实现), 同时记录回复日志。无规则命中时返回兜底规则回复。
 * 亦承载冷却时间/工作时间过滤、批量匹配、定向匹配与规则测试能力。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAutoReplyMatchService {

    /** 默认匹配方式 */
    private static final String DEFAULT_MATCH_TYPE = "EXACT";

    /** FUZZY 匹配阈值 (Jaccard 相似度, 低于此值视为不匹配) */
    private static final double FUZZY_THRESHOLD = 0.3;

    /** 时间格式 (HH:mm) */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** 模板变量: 客户名称 */
    private static final String VAR_CUSTOMER_NAME = "customerName";

    /** 模板变量: 昵称 */
    private static final String VAR_NICKNAME = "nickname";

    /** 模板变量: 时间 */
    private static final String VAR_TIME = "time";

    /** 发送状态: 成功 */
    private static final String STATUS_SENT = "SENT";
    /** 发送状态: 失败 */
    private static final String STATUS_FAILED = "FAILED";

    /** 自动回复规则数据访问层 */
    private final ScrmAutoReplyRuleRepository ruleRepository;

    /** 自动回复日志数据访问层 */
    private final ScrmAutoReplyLogRepository logRepository;

    /** 自动回复模板数据访问层 (模板使用次数累计) */
    private final ScrmAutoReplyTemplateRepository templateRepository;

    /** 客户数据访问层 (匹配时获取客户名称用于模板变量替换) */
    private final ScrmCustomerRepository customerRepository;

    /** 规则管理服务 (测试匹配需查询规则详情) */
    private final ScrmAutoReplyRuleService ruleService;

    /** 话术模板服务 (命中 TEMPLATE 规则时渲染回复内容) */
    private final ScrmAutoReplyTemplateService templateService;

    /** 回复日志服务 (命中后记录回复日志) */
    private final ScrmAutoReplyLogService logService;

    /**
     * 检查冷却时间是否已过期。
     * <p>查询该规则对该客户最近一次回复日志, 若距上次触发不足 cooldownMinutes 分钟则返回 false。</p>
     *
     * @param ruleId    规则 ID
     * @param customerId 客户 ID
     * @return true 表示冷却已过期可触发, false 表示冷却中
     */
    @Transactional(readOnly = true)
    public boolean checkCooldown(Long ruleId, Long customerId) {
        ScrmAutoReplyRuleEntity rule = ruleRepository.findById(ruleId).orElse(null);
        if (rule == null) {
            return false;
        }
        if (rule.getCooldownMinutes() == null || rule.getCooldownMinutes() <= 0) {
            return true;
        }
        // 查询该规则对该客户最近的回复日志 (按 sentAt DESC 取第一条)
        Page<ScrmAutoReplyLogEntity> recentLogs = logRepository.findAll(
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(root.get("ruleId"), ruleId));
                    predicates.add(cb.equal(root.get("customerId"), customerId));
                    predicates.add(cb.equal(root.get("status"), STATUS_SENT));
                    return cb.and(predicates.toArray(new Predicate[0]));
                }, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "sentAt")));
        if (recentLogs.isEmpty()) {
            return true;
        }
        ScrmAutoReplyLogEntity lastLog = recentLogs.getContent().get(0);
        LocalDateTime cooldownEnd = lastLog.getSentAt().plusMinutes(rule.getCooldownMinutes());
        return LocalDateTime.now().isAfter(cooldownEnd);
    }

    /**
     * 检查当前时间是否在规则的工作时间内。
     * <p>workTimeOnly=FALSE 时直接返回 true。workTimeStart/workTimeEnd 为 HH:mm 格式,
     * workDays 为 1-7 逗号分隔 (1=周一)。当前时间须同时满足工作日与工作时段。</p>
     *
     * @param rule 规则实体
     * @return true 表示在工作时间内
     */
    @Transactional(readOnly = true)
    public boolean checkWorkTime(ScrmAutoReplyRuleEntity rule) {
        if (rule == null || !Boolean.TRUE.equals(rule.getWorkTimeOnly())) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        // 校验工作日 (1=周一 ... 7=周日)
        if (rule.getWorkDays() != null && !rule.getWorkDays().isBlank()) {
            int dayOfWeek = now.getDayOfWeek().getValue();
            Set<Integer> workDays = parseWorkDays(rule.getWorkDays());
            if (!workDays.contains(dayOfWeek)) {
                return false;
            }
        }
        // 校验工作时段
        if (rule.getWorkTimeStart() != null && rule.getWorkTimeEnd() != null) {
            try {
                LocalTime start = LocalTime.parse(rule.getWorkTimeStart(), TIME_FORMATTER);
                LocalTime end = LocalTime.parse(rule.getWorkTimeEnd(), TIME_FORMATTER);
                LocalTime nowTime = now.toLocalTime();
                if (start.isBefore(end)) {
                    // 普通时段: 09:00 - 18:00
                    return !nowTime.isBefore(start) && !nowTime.isAfter(end);
                } else {
                    // 跨夜时段: 22:00 - 06:00
                    return !nowTime.isBefore(start) || !nowTime.isAfter(end);
                }
            } catch (Exception e) {
                log.warn("工作时间解析失败: ruleId={}, start={}, end={}, err={}",
                        rule.getId(), rule.getWorkTimeStart(), rule.getWorkTimeEnd(), e.getMessage());
                return true;
            }
        }
        return true;
    }

    /**
     * 匹配回复。
     * <p>
     * 加载当前账号全部启用规则 (按 priority ASC), 逐条评估 matchType 与 matchScope,
     * 同时校验适用渠道 (applicableChannels)、适用账号 (applicableAccounts)、工作时间
     * (workTimeOnly) 与冷却时间 (cooldownMinutes)。命中后渲染回复内容 (replyType=TEMPLATE
     * 时渲染模板), 模拟发送 (sendReply) 并记录回复日志。无规则命中时返回兜底规则回复。
     * </p>
     *
     * @param matchDto 匹配参数 (客户 ID、消息、渠道、账号 ID)
     * @return 回复日志实体 (包含回复内容与发送状态), 无任何回复时返回 null
     */
    @Transactional
    public ScrmAutoReplyLogEntity matchReply(ScrmAutoReplyMatchDto matchDto) {
        long startMs = System.currentTimeMillis();
        String message = matchDto.getMessage();
        String channel = matchDto.getChannel();

        // 加载启用规则 (按 priority ASC)
        List<ScrmAutoReplyRuleEntity> rules = ruleRepository
                .findByEnabledTrueOrderByPriorityAsc();

        // 解析客户名称 (优先使用 DTO 传入, 否则从客户实体加载)
        String customerName = matchDto.getCustomerName();
        if (customerName == null || customerName.isBlank() && matchDto.getCustomerId() != null) {
            Optional<ScrmCustomerEntity> customerOpt = customerRepository.findById(matchDto.getCustomerId());
            // 仅当 DTO 未携带时回填客户昵称
            customerName = customerOpt
                    .map(ScrmCustomerEntity::getNickname)
                    .filter(n -> !n.isBlank())
                    .orElse(customerName);
        }

        // 逐条评估
        for (ScrmAutoReplyRuleEntity rule : rules) {
            try {
                // 渠道过滤
                if (!isChannelApplicable(rule, channel)) {
                    continue;
                }
                // 账号过滤
                if (!isAccountApplicable(rule, matchDto.getAccountId())) {
                    continue;
                }
                // 工作时间过滤
                if (!checkWorkTime(rule)) {
                    continue;
                }
                // 冷却时间过滤
                if (!checkCooldown(rule.getId(), matchDto.getCustomerId())) {
                    continue;
                }
                // 关键词匹配 (WELCOME/TIMEOUT/OFFLINE/EVENT 类型不依赖消息内容匹配)
                MatchResult matchResult = matchRule(rule, message);
                if (!matchResult.matched()) {
                    continue;
                }
                // 命中: 渲染回复内容
                String replyContent = renderReplyContent(rule, customerName);
                // 模拟发送
                int responseTimeMs = (int) (System.currentTimeMillis() - startMs);
                boolean sent = sendReply(matchDto, rule, replyContent);
                // 记录日志
                ScrmAutoReplyLogEntity logEntity = recordMatchLog(
                        rule, matchDto, customerName, message, matchResult, replyContent, responseTimeMs, sent);
                // 增量更新触发次数
                ruleRepository.incrementTriggerCount(rule.getId(), LocalDateTime.now());
                log.info("自动回复命中: ruleId={}, ruleName={}, customerId={}, matchedKeyword={}",
                        rule.getId(), rule.getRuleName(), matchDto.getCustomerId(), matchResult.keyword());
                return logEntity;
            } catch (Exception e) {
                log.warn("规则评估异常, 跳过该规则: ruleId={}, err={}",
                        rule.getId(), e.getMessage());
            }
        }

        // 无规则命中, 尝试兜底规则
        Optional<ScrmAutoReplyRuleEntity> fallbackOpt = ruleRepository
                .findByFallbackRuleTrueAndEnabledTrue();
        if (fallbackOpt.isPresent()) {
            ScrmAutoReplyRuleEntity fallback = fallbackOpt.get();
            String replyContent = renderReplyContent(fallback, customerName);
            int responseTimeMs = (int) (System.currentTimeMillis() - startMs);
            boolean sent = sendReply(matchDto, fallback, replyContent);
            ScrmAutoReplyLogEntity logEntity = recordFallbackLog(
                    fallback, matchDto, customerName, message, replyContent, responseTimeMs, sent);
            ruleRepository.incrementTriggerCount(fallback.getId(), LocalDateTime.now());
            log.info("自动回复兜底命中: ruleId={}, ruleName={}, customerId={}",
                    fallback.getId(), fallback.getRuleName(), matchDto.getCustomerId());
            return logEntity;
        }
        log.info("自动回复无命中: customerId={}, message={}", matchDto.getCustomerId(),
                message.length() > 100 ? message.substring(0, 100) + "..." : message);
        return null;
    }

    /**
     * 按规则类型匹配回复。
     * <p>仅评估指定 ruleType 的规则, 用于 WELCOME/TIMEOUT/OFFLINE 等场景的定向匹配。</p>
     *
     * @param message  消息内容
     * @param ruleType 规则类型
     * @param channel  渠道（可空, 用于过滤适用渠道）
     * @return 命中的规则列表
     */
    @Transactional(readOnly = true)
    public List<ScrmAutoReplyRuleEntity> matchByType(String message, String ruleType, String channel) {
        List<ScrmAutoReplyRuleEntity> rules = ruleRepository
                .findByRuleTypeAndEnabledTrueOrderByPriorityAsc(ruleType);
        List<ScrmAutoReplyRuleEntity> matched = new ArrayList<>();
        for (ScrmAutoReplyRuleEntity rule : rules) {
            if (!isChannelApplicable(rule, channel)) {
                continue;
            }
            if (!checkWorkTime(rule)) {
                continue;
            }
            MatchResult result = matchRule(rule, message);
            if (result.matched()) {
                matched.add(rule);
            }
        }
        return matched;
    }

    /**
     * 模糊匹配 (Jaccard 相似度)。
     * <p>对消息与关键词列表分别切词后计算 Jaccard 相似度, 返回最大相似度。</p>
     *
     * @param message  消息内容
     * @param keywords 关键词列表 (逗号分隔)
     * @return 最大相似度 (0-1), 0 表示无匹配
     */
    public double fuzzyMatch(String message, String keywords) {
        if (message == null || message.isBlank() || keywords == null || keywords.isBlank()) {
            return 0.0;
        }
        Set<String> messageTokens = tokenize(message);
        double maxScore = 0.0;
        for (String keyword : keywords.split(",")) {
            String trimmed = keyword.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Set<String> keywordTokens = tokenize(trimmed);
            double score = jaccardSimilarity(messageTokens, keywordTokens);
            if (score > maxScore) {
                maxScore = score;
            }
        }
        return maxScore;
    }

    /**
     * 测试匹配 (不发送、不记录日志)。
     * <p>用于规则配置后的效果验证, 返回匹配详情 (匹配结果、匹配关键词、匹配分数)。</p>
     *
     * @param testDto 测试参数 (规则 ID + 测试消息)
     * @return 匹配详情
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> testMatch(ScrmAutoReplyTestDto testDto) throws ScrmException {
        ScrmAutoReplyRuleEntity rule = ruleService.findRuleOrThrow(testDto.getRuleId());
        MatchResult result = matchRule(rule, testDto.getTestMessage());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("ruleId", rule.getId());
        detail.put("ruleName", rule.getRuleName());
        detail.put("ruleType", rule.getRuleType());
        detail.put("matchType", rule.getMatchType());
        detail.put("testMessage", testDto.getTestMessage());
        detail.put("matched", result.matched());
        detail.put("matchedKeyword", result.keyword());
        detail.put("matchScore", result.score());
        detail.put("replyType", rule.getReplyType());
        detail.put("replyContent", rule.getReplyContent());
        return detail;
    }

    /**
     * 批量匹配。
     * <p>对多条消息逐一调用 matchReply, 返回每条消息的匹配结果 (日志实体或 null)。</p>
     *
     * @param messages 匹配参数列表
     * @return 匹配结果列表 (与入参顺序一致, 无命中时对应位置为 null)
     */
    @Transactional
    public List<ScrmAutoReplyLogEntity> batchMatch(List<ScrmAutoReplyMatchDto> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ScrmAutoReplyLogEntity> results = new ArrayList<>(messages.size());
        for (ScrmAutoReplyMatchDto message : messages) {
            results.add(matchReply(message));
        }
        return results;
    }

    /**
     * 判断渠道是否适用。
     *
     * @param rule    规则实体
     * @param channel 渠道
     * @return true 表示适用 (规则未限制渠道或渠道在适用列表中)
     */
    private boolean isChannelApplicable(ScrmAutoReplyRuleEntity rule, String channel) {
        if (rule.getApplicableChannels() == null || rule.getApplicableChannels().isBlank()) {
            return true;
        }
        if (channel == null || channel.isBlank()) {
            return true;
        }
        return parseCsv(rule.getApplicableChannels()).contains(channel);
    }

    /**
     * 判断账号是否适用。
     *
     * @param rule      规则实体
     * @param accountId 账号 ID
     * @return true 表示适用 (规则未限制账号或账号在适用列表中)
     */
    private boolean isAccountApplicable(ScrmAutoReplyRuleEntity rule, Long accountId) {
        if (rule.getApplicableAccounts() == null || rule.getApplicableAccounts().isBlank()) {
            return true;
        }
        if (accountId == null) {
            return true;
        }
        String accountIdStr = String.valueOf(accountId);
        return parseCsv(rule.getApplicableAccounts()).contains(accountIdStr);
    }

    /**
     * 评估规则是否匹配消息。
     * <p>WELCOME/TIMEOUT/OFFLINE/EVENT 类型不依赖消息内容, 直接返回匹配。
     * KEYWORD/FORM/CONDITIONAL 类型按 matchType 评估。</p>
     *
     * @param rule    规则实体
     * @param message 消息内容
     * @return 匹配结果
     */
    private MatchResult matchRule(ScrmAutoReplyRuleEntity rule, String message) {
        String ruleType = rule.getRuleType();
        // 不依赖消息内容的规则类型直接返回匹配
        if ("WELCOME".equals(ruleType) || "TIMEOUT".equals(ruleType)
                || "OFFLINE".equals(ruleType) || "EVENT".equals(ruleType)) {
            return new MatchResult(true, null, 1.0);
        }
        // 依赖关键词的规则类型, 关键词为空时不匹配
        if (rule.getKeywords() == null || rule.getKeywords().isBlank()) {
            return new MatchResult(false, null, 0.0);
        }
        if (message == null || message.isBlank()) {
            return new MatchResult(false, null, 0.0);
        }
        String matchType = rule.getMatchType() != null ? rule.getMatchType() : DEFAULT_MATCH_TYPE;
        String keywords = rule.getKeywords();
        switch (matchType) {
            case "EXACT":
                return matchExact(message, keywords);
            case "CONTAINS":
                return matchContains(message, keywords);
            case "STARTS_WITH":
                return matchStartsWith(message, keywords);
            case "ENDS_WITH":
                return matchEndsWith(message, keywords);
            case "REGEX":
                return matchRegex(message, keywords);
            case "FUZZY":
                return matchFuzzy(message, keywords);
            default:
                return new MatchResult(false, null, 0.0);
        }
    }

    /**
     * EXACT 匹配: 消息与任一关键词完全相等。
     */
    private MatchResult matchExact(String message, String keywords) {
        for (String keyword : keywords.split(",")) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && message.equals(trimmed)) {
                return new MatchResult(true, trimmed, 1.0);
            }
        }
        return new MatchResult(false, null, 0.0);
    }

    /**
     * CONTAINS 匹配: 消息包含任一关键词。
     */
    private MatchResult matchContains(String message, String keywords) {
        for (String keyword : keywords.split(",")) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && message.contains(trimmed)) {
                return new MatchResult(true, trimmed, 1.0);
            }
        }
        return new MatchResult(false, null, 0.0);
    }

    /**
     * STARTS_WITH 匹配: 消息以任一关键词开头。
     */
    private MatchResult matchStartsWith(String message, String keywords) {
        for (String keyword : keywords.split(",")) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && message.startsWith(trimmed)) {
                return new MatchResult(true, trimmed, 1.0);
            }
        }
        return new MatchResult(false, null, 0.0);
    }

    /**
     * ENDS_WITH 匹配: 消息以任一关键词结尾。
     */
    private MatchResult matchEndsWith(String message, String keywords) {
        for (String keyword : keywords.split(",")) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && message.endsWith(trimmed)) {
                return new MatchResult(true, trimmed, 1.0);
            }
        }
        return new MatchResult(false, null, 0.0);
    }

    /**
     * REGEX 匹配: 消息匹配正则表达式。
     */
    private MatchResult matchRegex(String message, String keywords) {
        try {
            Pattern pattern = Pattern.compile(keywords);
            if (pattern.matcher(message).find()) {
                return new MatchResult(true, keywords, 1.0);
            }
        } catch (PatternSyntaxException e) {
            log.warn("正则表达式非法: {}", e.getMessage());
        }
        return new MatchResult(false, null, 0.0);
    }

    /**
     * FUZZY 匹配: Jaccard 相似度 >= 阈值。
     */
    private MatchResult matchFuzzy(String message, String keywords) {
        double bestScore = 0.0;
        String bestKeyword = null;
        Set<String> messageTokens = tokenize(message);
        for (String keyword : keywords.split(",")) {
            String trimmed = keyword.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Set<String> keywordTokens = tokenize(trimmed);
            double score = jaccardSimilarity(messageTokens, keywordTokens);
            if (score > bestScore) {
                bestScore = score;
                bestKeyword = trimmed;
            }
        }
        if (bestScore >= FUZZY_THRESHOLD) {
            return new MatchResult(true, bestKeyword, bestScore);
        }
        return new MatchResult(false, bestKeyword, bestScore);
    }

    /**
     * 渲染回复内容。
     * <p>replyType=TEMPLATE 时渲染模板, 否则直接返回 replyContent (替换 {customerName}/{time})。</p>
     *
     * @param rule         规则实体
     * @param customerName 客户名称 (可空)
     * @return 渲染后的回复内容
     */
    private String renderReplyContent(ScrmAutoReplyRuleEntity rule, String customerName) {
        if ("TEMPLATE".equals(rule.getReplyType()) && rule.getReplyTemplateId() != null) {
            try {
                Map<String, String> variables = new HashMap<>();
                if (customerName != null) {
                    variables.put(VAR_CUSTOMER_NAME, customerName);
                    variables.put(VAR_NICKNAME, customerName);
                }
                String rendered = templateService.renderTemplate(rule.getReplyTemplateId(), variables);
                // 增量更新模板使用次数
                templateRepository.incrementUsageCount(rule.getReplyTemplateId());
                return rendered;
            } catch (Exception e) {
                log.warn("模板渲染失败, 回退到 replyContent: templateId={}, err={}",
                        rule.getReplyTemplateId(), e.getMessage());
                return rule.getReplyContent();
            }
        }
        String content = rule.getReplyContent();
        if (content == null) {
            return "";
        }
        if (customerName != null) {
            content = content.replace("{" + VAR_CUSTOMER_NAME + "}", customerName);
            content = content.replace("{" + VAR_NICKNAME + "}", customerName);
        }
        content = content.replace("{" + VAR_TIME + "}",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return content;
    }

    /**
     * 模拟发送回复。
     * <p>模拟实现: 仅记录日志, 实际项目中应调用渠道适配器 (微信/企微/Web/SMS/EMAIL) 发送。</p>
     *
     * @param matchDto    匹配参数
     * @param rule        命中规则
     * @param replyContent 回复内容
     * @return true 表示发送成功
     */
    private boolean sendReply(ScrmAutoReplyMatchDto matchDto, ScrmAutoReplyRuleEntity rule,
                              String replyContent) {
        // 模拟发送: 实际项目中调用渠道适配器
        log.info("模拟发送回复: customerId={}, channel={}, ruleId={}, replyType={}, contentLen={}",
                matchDto.getCustomerId(), matchDto.getChannel(), rule.getId(),
                rule.getReplyType(), replyContent != null ? replyContent.length() : 0);
        return true;
    }

    /**
     * 记录命中规则的回复日志。
     */
    private ScrmAutoReplyLogEntity recordMatchLog(ScrmAutoReplyRuleEntity rule, ScrmAutoReplyMatchDto matchDto,
                                                   String customerName, String message, MatchResult matchResult,
                                                   String replyContent, int responseTimeMs, boolean sent) {
        ScrmAutoReplyLogDto logDto = new ScrmAutoReplyLogDto();
        logDto.setRuleId(rule.getId());
        logDto.setRuleName(rule.getRuleName());
        logDto.setRuleType(rule.getRuleType());
        logDto.setCustomerId(matchDto.getCustomerId());
        logDto.setCustomerName(customerName);
        logDto.setAccountId(matchDto.getAccountId());
        logDto.setChannel(matchDto.getChannel());
        logDto.setIncomingMessage(message);
        logDto.setMatchedKeyword(matchResult.keyword());
        logDto.setMatchScore(matchResult.score());
        logDto.setReplyType(rule.getReplyType());
        logDto.setReplyContent(replyContent);
        logDto.setSentAt(LocalDateTime.now());
        logDto.setResponseTimeMs(responseTimeMs);
        logDto.setStatus(sent ? STATUS_SENT : STATUS_FAILED);
        logDto.setIsFallback(false);
        logDto.setSessionId(matchDto.getSessionId());
        return logService.recordLog(logDto);
    }

    /**
     * 记录兜底回复日志。
     */
    private ScrmAutoReplyLogEntity recordFallbackLog(ScrmAutoReplyRuleEntity rule, ScrmAutoReplyMatchDto matchDto,
                                                      String customerName, String message, String replyContent,
                                                      int responseTimeMs, boolean sent) {
        ScrmAutoReplyLogDto logDto = new ScrmAutoReplyLogDto();
        logDto.setRuleId(rule.getId());
        logDto.setRuleName(rule.getRuleName());
        logDto.setRuleType(rule.getRuleType());
        logDto.setCustomerId(matchDto.getCustomerId());
        logDto.setCustomerName(customerName);
        logDto.setAccountId(matchDto.getAccountId());
        logDto.setChannel(matchDto.getChannel());
        logDto.setIncomingMessage(message);
        logDto.setMatchedKeyword(null);
        logDto.setMatchScore(0.0);
        logDto.setReplyType(rule.getReplyType());
        logDto.setReplyContent(replyContent);
        logDto.setSentAt(LocalDateTime.now());
        logDto.setResponseTimeMs(responseTimeMs);
        logDto.setStatus(sent ? STATUS_SENT : STATUS_FAILED);
        logDto.setIsFallback(true);
        logDto.setSessionId(matchDto.getSessionId());
        return logService.recordLog(logDto);
    }

    /**
     * 切词 (按字符与标点切分, 兼容中英文)。
     *
     * @param text 文本
     * @return 词元集合 (小写)
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new HashSet<>();
        }
        // 按非字母数字汉字切分
        String[] tokens = text.toLowerCase().split("[^a-z0-9\\u4e00-\\u9fa5]+");
        Set<String> result = new HashSet<>();
        for (String token : tokens) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    /**
     * 计算 Jaccard 相似度。
     *
     * @param setA 集合 A
     * @param setB 集合 B
     * @return 相似度 (0-1)
     */
    private double jaccardSimilarity(Set<String> setA, Set<String> setB) {
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return (double) intersection.size() / union.size();
    }

    /**
     * 解析逗号分隔字符串为集合。
     *
     * @param csv 逗号分隔字符串
     * @return 字符串集合 (去除空白)
     */
    private Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * 解析工作日 (1-7 逗号分隔)。
     *
     * @param workDays 工作日字符串
     * @return 工作日集合
     */
    private Set<Integer> parseWorkDays(String workDays) {
        Set<Integer> result = new HashSet<>();
        for (String day : workDays.split(",")) {
            try {
                result.add(Integer.parseInt(day.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略非法工作日
            }
        }
        return result;
    }

    /**
     * 匹配结果。
     *
     * @param matched 是否匹配
     * @param keyword 匹配的关键词
     * @param score   匹配分数 (0-1)
     */
    private record MatchResult(boolean matched, String keyword, double score) {
    }
}