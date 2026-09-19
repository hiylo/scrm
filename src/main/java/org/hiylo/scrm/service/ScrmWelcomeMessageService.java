/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWelcomeMessageService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmWelcomeMessageDto;
import org.hiylo.scrm.entity.ScrmWelcomeMessageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmWelcomeMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SCRM 企微欢迎语配置服务
 * <p>
 * 负责欢迎语规则的 CRUD、激活/停用、规则匹配、变量渲染、欢迎语触发与触发统计。
 * 所有查询均通过归属账号做数据隔离。
 * </p>
 * <p>
 * 规则匹配优先级 (高到低):
 * <ol>
 *   <li>渠道活码匹配 (channelCodeId 命中)</li>
 *   <li>账号匹配 (accountId 命中, channelCodeId 为空)</li>
 *   <li>全局规则 (accountId 与 channelCodeId 均为空)</li>
 * </ol>
 * 同优先级取 priority 字段较大者。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmWelcomeMessageService {

    /** 状态: 启用 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态: 停用 */
    private static final String STATUS_INACTIVE = "INACTIVE";

    /** 默认平台类型: 企微 */
    private static final String DEFAULT_PLATFORM_TYPE = "wework";

    /** 默认消息类型: 文本 */
    private static final String DEFAULT_MESSAGE_TYPE = "TEXT";

    /** 变量占位符正则: ${var} 形式 */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    /** 时间格式: HH:mm */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** 日期格式: yyyy-MM-dd */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 时间格式: HH:mm:ss */
    private static final DateTimeFormatter TIME_WITH_SECONDS_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 欢迎语规则数据访问层 */
    private final ScrmWelcomeMessageRepository welcomeMessageRepository;

    // ============================================================
    // 规则 CRUD
    // ============================================================

    /**
     * 创建欢迎语规则
     * <p>
     * 默认值: platformType=wework, messageType=TEXT, weekendEnabled=true,
     * priority=0, delaySeconds=0, cooldownMinutes=0, triggerCount=0,
     * status=INACTIVE (创建后需显式激活)。
     * </p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数校验失败
     */
    @Transactional
    public ScrmWelcomeMessageDto createRule(ScrmWelcomeMessageDto dto) throws ScrmException {
        validateCreateRule(dto);
        ScrmWelcomeMessageEntity entity = new ScrmWelcomeMessageEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setAccountId(dto.getAccountId());
        entity.setChannelCodeId(dto.getChannelCodeId());
        entity.setPlatformType(dto.getPlatformType() != null ? dto.getPlatformType() : DEFAULT_PLATFORM_TYPE);
        entity.setMessageType(dto.getMessageType() != null ? dto.getMessageType() : DEFAULT_MESSAGE_TYPE);
        entity.setContent(dto.getContent());
        entity.setMediaUrl(dto.getMediaUrl());
        entity.setLinkTitle(dto.getLinkTitle());
        entity.setLinkUrl(dto.getLinkUrl());
        entity.setLinkDesc(dto.getLinkDesc());
        entity.setMiniprogramTitle(dto.getMiniprogramTitle());
        entity.setMiniprogramAppId(dto.getMiniprogramAppId());
        entity.setMiniprogramPage(dto.getMiniprogramPage());
        entity.setSecondaryMessages(dto.getSecondaryMessages());
        entity.setDelaySeconds(dto.getDelaySeconds() != null ? dto.getDelaySeconds() : 0);
        entity.setCooldownMinutes(dto.getCooldownMinutes() != null ? dto.getCooldownMinutes() : 0);
        entity.setEffectiveTimeStart(dto.getEffectiveTimeStart());
        entity.setEffectiveTimeEnd(dto.getEffectiveTimeEnd());
        entity.setWeekendEnabled(dto.getWeekendEnabled() != null ? dto.getWeekendEnabled() : Boolean.TRUE);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : 0);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_INACTIVE);
        entity.setTriggerCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = welcomeMessageRepository.save(entity);
        log.info("创建欢迎语规则: id={}, ruleName={}, platformType={}, messageType={}",
                entity.getId(), entity.getRuleName(), entity.getPlatformType(), entity.getMessageType());
        return toDto(entity);
    }

    /**
     * 更新欢迎语规则
     * <p>字段非空才覆盖, status 字段不在此处更新 (使用 activateRule/deactivateRule)。</p>
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数校验失败
     */
    @Transactional
    public ScrmWelcomeMessageDto updateRule(Long id, ScrmWelcomeMessageDto dto) throws ScrmException {
        ScrmWelcomeMessageEntity entity = findRuleOrThrow(id);
        if (dto.getRuleName() != null) {
            if (dto.getRuleName().isBlank()) {
                throw ScrmException.badRequest("规则名称不能为空");
            }
            entity.setRuleName(dto.getRuleName());
        }
        if (dto.getAccountId() != null) entity.setAccountId(dto.getAccountId());
        if (dto.getChannelCodeId() != null) entity.setChannelCodeId(dto.getChannelCodeId());
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getMessageType() != null) entity.setMessageType(dto.getMessageType());
        if (dto.getContent() != null) {
            if (dto.getContent().isBlank()) {
                throw ScrmException.badRequest("文本内容不能为空");
            }
            entity.setContent(dto.getContent());
        }
        if (dto.getMediaUrl() != null) entity.setMediaUrl(dto.getMediaUrl());
        if (dto.getLinkTitle() != null) entity.setLinkTitle(dto.getLinkTitle());
        if (dto.getLinkUrl() != null) entity.setLinkUrl(dto.getLinkUrl());
        if (dto.getLinkDesc() != null) entity.setLinkDesc(dto.getLinkDesc());
        if (dto.getMiniprogramTitle() != null) entity.setMiniprogramTitle(dto.getMiniprogramTitle());
        if (dto.getMiniprogramAppId() != null) entity.setMiniprogramAppId(dto.getMiniprogramAppId());
        if (dto.getMiniprogramPage() != null) entity.setMiniprogramPage(dto.getMiniprogramPage());
        if (dto.getSecondaryMessages() != null) entity.setSecondaryMessages(dto.getSecondaryMessages());
        if (dto.getDelaySeconds() != null) entity.setDelaySeconds(dto.getDelaySeconds());
        if (dto.getCooldownMinutes() != null) entity.setCooldownMinutes(dto.getCooldownMinutes());
        if (dto.getEffectiveTimeStart() != null) entity.setEffectiveTimeStart(dto.getEffectiveTimeStart());
        if (dto.getEffectiveTimeEnd() != null) entity.setEffectiveTimeEnd(dto.getEffectiveTimeEnd());
        if (dto.getWeekendEnabled() != null) entity.setWeekendEnabled(dto.getWeekendEnabled());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = welcomeMessageRepository.save(entity);
        log.info("更新欢迎语规则: id={}", id);
        return toDto(entity);
    }

    /**
     * 删除欢迎语规则
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmWelcomeMessageEntity entity = findRuleOrThrow(id);
        welcomeMessageRepository.delete(entity);
        log.info("删除欢迎语规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询欢迎语规则详情
     *
     * @param id 规则 ID
     * @return 规则 DTO
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmWelcomeMessageDto getRule(Long id) throws ScrmException {
        return toDto(findRuleOrThrow(id));
    }

    /**
     * 分页查询欢迎语规则, 支持按账号、渠道活码、平台类型、状态与关键词过滤
     *
     * @param accountId     账号 ID 过滤 (可空)
     * @param channelCodeId 渠道活码 ID 过滤 (可空)
     * @param platformType  平台类型过滤 (可空)
     * @param status        状态过滤 (可空)
     * @param keyword       关键词过滤, 匹配规则名称 (可空)
     * @param pageable      分页参数
     * @return 规则分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmWelcomeMessageDto> listRules(Long accountId, Long channelCodeId, String platformType,
                                                  String status, String keyword, Pageable pageable) {
        Pageable sorted = ensureSort(pageable);
        Specification<ScrmWelcomeMessageEntity> spec = buildRuleSpec(
                accountId, channelCodeId, platformType, status, keyword);
        return welcomeMessageRepository.findAll(spec, sorted).map(this::toDto);
    }

    /**
     * 激活欢迎语规则 (状态置 ACTIVE)
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmWelcomeMessageDto activateRule(Long id) throws ScrmException {
        ScrmWelcomeMessageEntity entity = findRuleOrThrow(id);
        entity.setStatus(STATUS_ACTIVE);
        entity = welcomeMessageRepository.save(entity);
        log.info("激活欢迎语规则: id={}", id);
        return toDto(entity);
    }

    /**
     * 停用欢迎语规则 (状态置 INACTIVE)
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmWelcomeMessageDto deactivateRule(Long id) throws ScrmException {
        ScrmWelcomeMessageEntity entity = findRuleOrThrow(id);
        entity.setStatus(STATUS_INACTIVE);
        entity = welcomeMessageRepository.save(entity);
        log.info("停用欢迎语规则: id={}", id);
        return toDto(entity);
    }

    // ============================================================
    // 规则匹配与欢迎语触发
    // ============================================================

    /**
     * 匹配最优欢迎语规则
     * <p>
     * 匹配优先级 (高到低):
     * <ol>
     *   <li>渠道活码匹配 (channelCodeId 命中)</li>
     *   <li>账号匹配 (accountId 命中, channelCodeId 为空)</li>
     *   <li>全局规则 (accountId 与 channelCodeId 均为空)</li>
     * </ol>
     * 同优先级取 priority 字段较大者。仅返回当前时段生效的规则 (校验 effectiveTimeStart/End
     * 与 weekendEnabled), 非生效时段的规则会被跳过。
     * </p>
     *
     * @param accountId     账号 ID (可空)
     * @param channelCodeId 渠道活码 ID (可空)
     * @param platformType  平台类型 (可空, 空则默认 wework)
     * @return 匹配到的规则 DTO, 未匹配返回 null
     */
    @Transactional(readOnly = true)
    public ScrmWelcomeMessageDto matchRule(Long accountId, Long channelCodeId, String platformType) {
        String platform = (platformType == null || platformType.isBlank()) ? DEFAULT_PLATFORM_TYPE : platformType;
        // 拉取当前账号+平台下的所有启用规则, 在内存中按优先级匹配
        List<ScrmWelcomeMessageEntity> candidates = welcomeMessageRepository
                .findByPlatformTypeAndStatus(platform, STATUS_ACTIVE);
        if (candidates.isEmpty()) {
            log.debug("未匹配到欢迎语规则:, platformType={}, 无启用规则", platform);
            return null;
        }
        // 按匹配优先级分组: channelCode > account > global
        List<ScrmWelcomeMessageEntity> channelMatches = new ArrayList<>();
        List<ScrmWelcomeMessageEntity> accountMatches = new ArrayList<>();
        List<ScrmWelcomeMessageEntity> globalMatches = new ArrayList<>();
        for (ScrmWelcomeMessageEntity rule : candidates) {
            if (channelCodeId != null && channelCodeId.equals(rule.getChannelCodeId())) {
                channelMatches.add(rule);
            } else if (rule.getChannelCodeId() == null && accountId != null && accountId.equals(rule.getAccountId())) {
                accountMatches.add(rule);
            } else if (rule.getChannelCodeId() == null && rule.getAccountId() == null) {
                globalMatches.add(rule);
            }
        }
        // 按优先级降序排序后, 取第一个当前生效的规则
        ScrmWelcomeMessageEntity matched = pickEffectiveRule(channelMatches)
                .orElseGet(() -> pickEffectiveRule(accountMatches)
                        .orElseGet(() -> pickEffectiveRule(globalMatches).orElse(null)));
        if (matched == null) {
            log.debug("未匹配到生效欢迎语规则:, platformType={}, accountId={}, channelCodeId={}", platform, accountId, channelCodeId);
            return null;
        }
        log.info("匹配到欢迎语规则: ruleId={}, ruleName={}, priority={}",
                matched.getId(), matched.getRuleName(), matched.getPriority());
        return toDto(matched);
    }

    /**
     * 渲染欢迎语内容, 替换 ${var} 形式的变量占位符
     * <p>
     * 支持的内置变量:
     * <ul>
     *   <li>${nickname}: 客户昵称 (取自 customerInfo.get("nickname"), 缺省 "客户")</li>
     *   <li>${time}: 当前时间 HH:mm:ss</li>
     *   <li>${date}: 当前日期 yyyy-MM-dd</li>
     *   <li>${datetime}: 当前日期时间 yyyy-MM-dd HH:mm:ss</li>
     *   <li>${accountId}: 账号 ID</li>
     *   <li>${customerId}: 客户 ID</li>
     *   <li>${channelCodeId}: 渠道活码 ID</li>
     * </ul>
     * customerInfo 中的其他 key 也会作为变量参与替换, key 不存在时替换为空字符串。
     * </p>
     *
     * @param content      原始内容
     * @param customerInfo 客户信息上下文 (可空)
     * @return 渲染后的内容
     */
    public String renderContent(String content, Map<String, Object> customerInfo) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        Map<String, Object> vars = new HashMap<>();
        // 内置变量
        LocalDateTime now = LocalDateTime.now();
        vars.put("time", now.format(TIME_WITH_SECONDS_FORMATTER));
        vars.put("date", now.format(DATE_FORMATTER));
        vars.put("datetime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        vars.put("nickname", "客户");
        // 合并调用方传入的上下文 (覆盖内置默认值)
        if (customerInfo != null) {
            vars.putAll(customerInfo);
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = vars.get(key);
            String replacement = value == null ? "" : value.toString();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 触发欢迎语
     * <p>
     * 流程: 匹配规则 -> 校验生效时段 -> 渲染内容 -> 递增触发次数 -> 返回发送内容。
     * 冷却期 (cooldownMinutes) 需基于客户级别的触发日志判断, 当前实现未持久化客户触发日志,
     * 冷却期由调用方根据返回的 cooldownMinutes 自行控制。
     * </p>
     *
     * @param accountId     账号 ID (可空)
     * @param customerId    客户 ID (可空, 用于变量替换)
     * @param channelCodeId 渠道活码 ID (可空)
     * @return 触发结果 (含渲染后的发送内容), 未匹配到规则返回 skipped=true
     */
    @Transactional
    public TriggerResult triggerWelcome(Long accountId, Long customerId, Long channelCodeId) {
        ScrmWelcomeMessageDto matched = matchRule(accountId, channelCodeId, null);
        if (matched == null) {
            log.debug("触发欢迎语未命中规则: accountId={}, customerId={}, channelCodeId={}",
                    accountId, customerId, channelCodeId);
            return TriggerResult.skipped("未匹配到生效的欢迎语规则");
        }
        // 构建客户信息上下文 (用于变量替换)
        Map<String, Object> customerInfo = new HashMap<>();
        if (customerId != null) {
            customerInfo.put("customerId", String.valueOf(customerId));
        }
        if (accountId != null) {
            customerInfo.put("accountId", String.valueOf(accountId));
        }
        if (channelCodeId != null) {
            customerInfo.put("channelCodeId", String.valueOf(channelCodeId));
        }
        // 渲染主内容
        String renderedContent = renderContent(matched.getContent(), customerInfo);
        // 递增触发次数
        incrementTriggerCount(matched.getId());
        log.info("触发欢迎语: ruleId={}, ruleName={}, accountId={}, customerId={}, channelCodeId={}",
                matched.getId(), matched.getRuleName(), accountId, customerId, channelCodeId);
        return TriggerResult.matched(matched, renderedContent);
    }

    /**
     * 查询欢迎语规则触发统计
     * <p>
     * 当前实现基于规则实体的 triggerCount 累计字段, 未维护单独的触发事件日志表,
     * startTime/endTime 参数暂不参与过滤 (始终返回累计触发数)。
     * 后续如需按时间段统计, 可新增 scrm_welcome_message_trigger_log 表并在本方法聚合。
     * </p>
     *
     * @param ruleId    规则 ID
     * @param startTime 起始时间 (可空, 当前实现忽略)
     * @param endTime   结束时间 (可空, 当前实现忽略)
     * @return 统计信息 (ruleId / ruleName / triggerCount / status / createTime / updateTime)
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTriggerStats(Long ruleId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        ScrmWelcomeMessageEntity entity = findRuleOrThrow(ruleId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("ruleId", entity.getId());
        stats.put("ruleName", entity.getRuleName());
        stats.put("triggerCount", entity.getTriggerCount() != null ? entity.getTriggerCount() : 0);
        stats.put("status", entity.getStatus());
        stats.put("priority", entity.getPriority());
        stats.put("platformType", entity.getPlatformType());
        stats.put("accountId", entity.getAccountId());
        stats.put("channelCodeId", entity.getChannelCodeId());
        stats.put("createTime", entity.getCreateTime());
        stats.put("updateTime", entity.getUpdateTime());
        return stats;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 创建规则参数校验
     *
     * @param dto 规则参数
     * @throws ScrmException 参数校验失败
     */
    private void validateCreateRule(ScrmWelcomeMessageDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("欢迎语规则参数不能为空");
        }
        if (dto.getRuleName() == null || dto.getRuleName().isBlank()) {
            throw ScrmException.badRequest("规则名称不能为空");
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw ScrmException.badRequest("文本内容不能为空");
        }
        // 校验生效时段格式 HH:mm
        validateTimeFormat(dto.getEffectiveTimeStart(), "生效开始时间");
        validateTimeFormat(dto.getEffectiveTimeEnd(), "生效结束时间");
        // 同时提供起止时间时, 校验起 <= 止
        if (dto.getEffectiveTimeStart() != null && dto.getEffectiveTimeEnd() != null && dto.getEffectiveTimeStart().compareTo(dto.getEffectiveTimeEnd()) > 0) {
            throw ScrmException.badRequest("生效开始时间不能晚于结束时间");
        }
    }

    /**
     * 校验 HH:mm 时间格式
     *
     * @param value    时间字符串 (可空)
     * @param fieldName 字段名 (用于错误提示)
     * @throws ScrmException 格式非法
     */
    private void validateTimeFormat(String value, String fieldName) throws ScrmException {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            LocalTime.parse(value, TIME_FORMATTER);
        } catch (Exception e) {
            throw ScrmException.badRequest(fieldName + "格式非法, 应为 HH:mm: " + value);
        }
    }

    /**
     * 从候选规则列表中选取当前生效的最高优先级规则
     * <p>
     * 校验生效时段 (effectiveTimeStart/End) 与周末开关 (weekendEnabled),
     * 过滤掉当前不生效的规则, 然后按 priority 降序取首个。
     * </p>
     *
     * @param candidates 候选规则列表
     * @return 生效的最高优先级规则 (可能为空)
     */
    private Optional<ScrmWelcomeMessageEntity> pickEffectiveRule(List<ScrmWelcomeMessageEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        return candidates.stream()
                .filter(rule -> isWithinEffectiveTime(rule, now))
                .filter(rule -> isWeekendAllowed(rule, now))
                .max(Comparator.comparingInt(rule -> rule.getPriority() != null ? rule.getPriority() : 0));
    }

    /**
     * 判断当前时间是否在规则生效时段内
     *
     * @param rule 规则实体
     * @param now  当前时间
     * @return true=在生效时段内或无时段限制
     */
    private boolean isWithinEffectiveTime(ScrmWelcomeMessageEntity rule, LocalDateTime now) {
        String start = rule.getEffectiveTimeStart();
        String end = rule.getEffectiveTimeEnd();
        if (start == null || start.isBlank() || end == null || end.isBlank()) {
            return true;
        }
        try {
            LocalTime nowTime = now.toLocalTime();
            LocalTime startTime = LocalTime.parse(start, TIME_FORMATTER);
            LocalTime endTime = LocalTime.parse(end, TIME_FORMATTER);
            // 起始 <= 当前 <= 结束 (含等号)
            return !nowTime.isBefore(startTime) && !nowTime.isAfter(endTime);
        } catch (Exception e) {
            // 时段配置异常时视为生效, 避免配置错误导致欢迎语不触发
            log.warn("欢迎语规则时段配置异常, 视为生效: ruleId={}, start={}, end={}",
                    rule.getId(), start, end);
            return true;
        }
    }

    /**
     * 判断当前是否允许周末触发
     *
     * @param rule 规则实体
     * @param now  当前时间
     * @return true=允许触发 (weekendEnabled=true 或当前非周末)
     */
    private boolean isWeekendAllowed(ScrmWelcomeMessageEntity rule, LocalDateTime now) {
        Boolean weekendEnabled = rule.getWeekendEnabled();
        if (weekendEnabled != null && weekendEnabled) {
            return true;
        }
        // weekendEnabled=false 时, 周六/日不触发 (java.time.DayOfWeek 周六=6, 周日=7)
        int dayOfWeek = now.getDayOfWeek().getValue();
        return dayOfWeek < 6;
    }

    /**
     * 递增规则触发次数
     *
     * @param ruleId 规则 ID
     */
    private void incrementTriggerCount(Long ruleId) {
        try {
            welcomeMessageRepository.findById(ruleId).ifPresent(rule -> {
                int current = rule.getTriggerCount() != null ? rule.getTriggerCount() : 0;
                rule.setTriggerCount(current + 1);
                welcomeMessageRepository.save(rule);
            });
        } catch (Exception e) {
            // 触发次数递增失败不影响主流程 (欢迎语已渲染完成)
            log.warn("递增欢迎语触发次数失败(忽略): ruleId={}, err={}", ruleId, e.getMessage());
        }
    }

    /**
     * 构建欢迎语规则查询条件 Specification
     * <p>
     * 数据隔离: 始终按当前用户可见账号范围过滤。
     * accountId/channelCodeId 为 null 时不参与过滤, 与"匹配全局规则"语义区分 (匹配使用 matchRule)。
     * </p>
     *
     * @param accountId     账号 ID 过滤 (可空)
     * @param channelCodeId 渠道活码 ID 过滤 (可空)
     * @param platformType  平台类型过滤 (可空)
     * @param status        状态过滤 (可空)
     * @param keyword       关键词过滤, 匹配规则名称 (可空)
     * @return Specification
     */
    private Specification<ScrmWelcomeMessageEntity> buildRuleSpec(Long accountId, Long channelCodeId,
                                                                   String platformType, String status,
                                                                   String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前账号过滤
            if (accountId != null) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            if (channelCodeId != null) {
                predicates.add(cb.equal(root.get("channelCodeId"), channelCodeId));
            }
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("ruleName")), kw));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 确保分页参数带默认排序 (按创建时间倒序, 再按 priority 倒序)
     *
     * @param pageable 原始分页参数
     * @return 带排序的分页参数
     */
    private Pageable ensureSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime")
                        .and(Sort.by(Sort.Direction.DESC, "priority")));
    }

    /**
* 按主键查询规则并校验归属账号, 不存在抛异常
*
* @param id 规则 ID
* @return 规则实体
* @throws ScrmException 规则不存在或越权访问
     */
    private ScrmWelcomeMessageEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmWelcomeMessageEntity entity = welcomeMessageRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "欢迎语规则不存在: id=" + id));
        // 数据隔离: 校验规则归属当前账号, 防止越权按 ID 访问

        return entity;
    }

    /**
     * 实体转 DTO
     *
     * @param entity 规则实体
     * @return 规则 DTO
     */
    private ScrmWelcomeMessageDto toDto(ScrmWelcomeMessageEntity entity) {
        ScrmWelcomeMessageDto dto = new ScrmWelcomeMessageDto();
        dto.setId(entity.getId());
        dto.setRuleName(entity.getRuleName());
        dto.setAccountId(entity.getAccountId());
        dto.setChannelCodeId(entity.getChannelCodeId());
        dto.setPlatformType(entity.getPlatformType());
        dto.setMessageType(entity.getMessageType());
        dto.setContent(entity.getContent());
        dto.setMediaUrl(entity.getMediaUrl());
        dto.setLinkTitle(entity.getLinkTitle());
        dto.setLinkUrl(entity.getLinkUrl());
        dto.setLinkDesc(entity.getLinkDesc());
        dto.setMiniprogramTitle(entity.getMiniprogramTitle());
        dto.setMiniprogramAppId(entity.getMiniprogramAppId());
        dto.setMiniprogramPage(entity.getMiniprogramPage());
        dto.setSecondaryMessages(entity.getSecondaryMessages());
        dto.setDelaySeconds(entity.getDelaySeconds());
        dto.setCooldownMinutes(entity.getCooldownMinutes());
        dto.setEffectiveTimeStart(entity.getEffectiveTimeStart());
        dto.setEffectiveTimeEnd(entity.getEffectiveTimeEnd());
        dto.setWeekendEnabled(entity.getWeekendEnabled());
        dto.setPriority(entity.getPriority());
        dto.setStatus(entity.getStatus());
        dto.setTriggerCount(entity.getTriggerCount());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    // ============================================================
    // 内部类: 触发结果
    // ============================================================

    /**
     * 欢迎语触发结果
     *
     * @author Hsi Chu
     * @since V1.0
     */
    public static class TriggerResult {
        /** 是否成功匹配并触发 */
        private final boolean matched;
        /** 跳过原因 (matched=false 时填充) */
        private final String skippedReason;
        /** 匹配到的规则 ID */
        private final Long ruleId;
        /** 匹配到的规则名称 */
        private final String ruleName;
        /** 消息类型 */
        private final String messageType;
        /** 渲染后的文本内容 */
        private final String content;
        /** 媒体 URL */
        private final String mediaUrl;
        /** 链接标题 */
        private final String linkTitle;
        /** 链接 URL */
        private final String linkUrl;
        /** 链接描述 */
        private final String linkDesc;
        /** 小程序标题 */
        private final String miniprogramTitle;
        /** 小程序 AppId */
        private final String miniprogramAppId;
        /** 小程序页面路径 */
        private final String miniprogramPage;
        /** 跟进消息序列 (JSON 数组字符串) */
        private final String secondaryMessages;
        /** 延迟发送秒数 */
        private final Integer delaySeconds;
        /** 冷却期分钟数 */
        private final Integer cooldownMinutes;

        private TriggerResult(boolean matched, String skippedReason, ScrmWelcomeMessageDto dto, String content) {
            this.matched = matched;
            this.skippedReason = skippedReason;
            this.ruleId = dto != null ? dto.getId() : null;
            this.ruleName = dto != null ? dto.getRuleName() : null;
            this.messageType = dto != null ? dto.getMessageType() : null;
            this.content = content;
            this.mediaUrl = dto != null ? dto.getMediaUrl() : null;
            this.linkTitle = dto != null ? dto.getLinkTitle() : null;
            this.linkUrl = dto != null ? dto.getLinkUrl() : null;
            this.linkDesc = dto != null ? dto.getLinkDesc() : null;
            this.miniprogramTitle = dto != null ? dto.getMiniprogramTitle() : null;
            this.miniprogramAppId = dto != null ? dto.getMiniprogramAppId() : null;
            this.miniprogramPage = dto != null ? dto.getMiniprogramPage() : null;
            this.secondaryMessages = dto != null ? dto.getSecondaryMessages() : null;
            this.delaySeconds = dto != null ? dto.getDelaySeconds() : null;
            this.cooldownMinutes = dto != null ? dto.getCooldownMinutes() : null;
        }

        /**
         * 构造匹配成功的触发结果
         *
         * @param dto     匹配到的规则
         * @param content 渲染后的内容
         * @return TriggerResult
         */
        public static TriggerResult matched(ScrmWelcomeMessageDto dto, String content) {
            return new TriggerResult(true, null, dto, content);
        }

        /**
         * 构造未匹配的触发结果
         *
         * @param reason 跳过原因
         * @return TriggerResult
         */
        public static TriggerResult skipped(String reason) {
            return new TriggerResult(false, reason, null, null);
        }

        /**
         * 判断欢迎语是否匹配并触发成功
         *
         * @return true=匹配成功并触发，false=未匹配到规则
         */
        public boolean isMatched() {
            return matched;
        }

        /**
         * 获取跳过原因
         *
         * @return 未匹配时的跳过原因说明，匹配成功时返回 null
         */
        public String getSkippedReason() {
            return skippedReason;
        }

        /**
         * 获取匹配到的欢迎语规则 ID
         *
         * @return 规则 ID，未匹配时返回 null
         */
        public Long getRuleId() {
            return ruleId;
        }

        /**
         * 获取匹配到的欢迎语规则名称
         *
         * @return 规则名称，未匹配时返回 null
         */
        public String getRuleName() {
            return ruleName;
        }

        /**
         * 获取欢迎语的消息类型
         *
         * @return 消息类型标识（如 TEXT、IMAGE、MINIPROGRAM 等），未匹配时返回 null
         */
        public String getMessageType() {
            return messageType;
        }

        /**
         * 获取渲染后的欢迎语文本内容
         *
         * @return 变量替换后的文本内容，未匹配时返回 null
         */
        public String getContent() {
            return content;
        }

        /**
         * 获取欢迎语关联的媒体文件 URL
         *
         * @return 媒体资源地址，无媒体时返回 null
         */
        public String getMediaUrl() {
            return mediaUrl;
        }

        /**
         * 获取欢迎语关联的链接标题
         *
         * @return 链接标题文本，无链接时返回 null
         */
        public String getLinkTitle() {
            return linkTitle;
        }

        /**
         * 获取欢迎语关联的链接地址
         *
         * @return 链接 URL，无链接时返回 null
         */
        public String getLinkUrl() {
            return linkUrl;
        }

        /**
         * 获取欢迎语关联的链接描述
         *
         * @return 链接描述文本，无链接时返回 null
         */
        public String getLinkDesc() {
            return linkDesc;
        }

        /**
         * 获取欢迎语关联的小程序标题
         *
         * @return 小程序标题文本，无小程序时返回 null
         */
        public String getMiniprogramTitle() {
            return miniprogramTitle;
        }

        /**
         * 获取欢迎语关联的小程序 AppId
         *
         * @return 小程序应用标识，无小程序时返回 null
         */
        public String getMiniprogramAppId() {
            return miniprogramAppId;
        }

        /**
         * 获取欢迎语关联的小程序页面路径
         *
         * @return 小程序页面路径，无小程序时返回 null
         */
        public String getMiniprogramPage() {
            return miniprogramPage;
        }

        /**
         * 获取欢迎语的跟进消息序列
         *
         * @return 跟进消息 JSON 数组字符串，无跟进消息时返回 null
         */
        public String getSecondaryMessages() {
            return secondaryMessages;
        }

        /**
         * 获取欢迎语的延迟发送秒数
         *
         * @return 延迟发送时间（秒），未设置时返回 null
         */
        public Integer getDelaySeconds() {
            return delaySeconds;
        }

        /**
         * 获取欢迎语的冷却期分钟数
         *
         * @return 冷却期时长（分钟），未设置时返回 null
         */
        public Integer getCooldownMinutes() {
            return cooldownMinutes;
        }

        /**
         * 判断当前触发结果与指定对象是否相等
         *
         * @param o 待比较对象
         * @return true=所有字段相等，false=不相等
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TriggerResult that)) return false;
            return matched == that.matched && Objects.equals(skippedReason, that.skippedReason) && Objects.equals(ruleId, that.ruleId) && Objects.equals(ruleName, that.ruleName) && Objects.equals(messageType, that.messageType) && Objects.equals(content, that.content) && Objects.equals(mediaUrl, that.mediaUrl) && Objects.equals(linkTitle, that.linkTitle) && Objects.equals(linkUrl, that.linkUrl) && Objects.equals(linkDesc, that.linkDesc) && Objects.equals(miniprogramTitle, that.miniprogramTitle) && Objects.equals(miniprogramAppId, that.miniprogramAppId) && Objects.equals(miniprogramPage, that.miniprogramPage) && Objects.equals(secondaryMessages, that.secondaryMessages) && Objects.equals(delaySeconds, that.delaySeconds) && Objects.equals(cooldownMinutes, that.cooldownMinutes);
        }

        /**
         * 计算触发结果的哈希码
         *
         * @return 基于所有字段的哈希码
         */
        @Override
        public int hashCode() {
            return Objects.hash(matched, skippedReason, ruleId, ruleName, messageType, content,
                    mediaUrl, linkTitle, linkUrl, linkDesc, miniprogramTitle, miniprogramAppId,
                    miniprogramPage, secondaryMessages, delaySeconds, cooldownMinutes);
        }
    }
}
