/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskEventService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmBlacklistDto;
import org.hiylo.scrm.dto.ScrmRiskEventDto;
import org.hiylo.scrm.entity.ScrmBlacklistEntity;
import org.hiylo.scrm.entity.ScrmBlacklistRuleEntity;
import org.hiylo.scrm.entity.ScrmRiskEventEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmBlacklistRuleRepository;
import org.hiylo.scrm.repository.ScrmRiskEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 风险事件管理服务。
 * <p>
 * 承载风险事件子域: 事件创建 / 触发 / 分配 / 调查 / 确认 / 误报 / 解决 / 升级、待处理
 * 与严重事件查询、事件时间线与相关事件、动作执行 (含自动加入黑名单) 与批量解决、
 * 事件编号生成。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmRiskEventService {

    // ==================== 默认值常量 ====================

    /** 默认事件动作状态 */
    private static final String DEFAULT_ACTION_STATUS = "PENDING";
    /** 默认事件状态 */
    private static final String DEFAULT_EVENT_STATUS = "OPEN";
    /** 默认检测方式 */
    private static final String DEFAULT_DETECTION_METHOD = "RULE";
    /** 阻断类动作 (命中后加入黑名单) */
    private static final Set<String> BLOCK_ACTIONS = Set.of("BLOCK", "AUTO_BLACKLIST");

    // ==================== 合法枚举值 (共享) ====================

    /** 严重风险等级 */
    static final List<String> CRITICAL_LEVELS = List.of("HIGH", "CRITICAL");

    // ==================== 依赖注入 ====================

    /** 风险事件数据访问层 */
    private final ScrmRiskEventRepository eventRepository;
    /** 风控规则数据访问层 (事件触发 / 误报统计) */
    private final ScrmBlacklistRuleRepository ruleRepository;
    /** 黑名单管理子域服务 (动作执行时加入黑名单) */
    private final ScrmBlacklistManageService blacklistService;

    /**
     * 创建风险事件。
     * <p>eventNo 缺省时自动生成, action / actionStatus / status 缺省时填默认值。</p>
     *
     * @param dto 事件参数
     * @return 创建后的事件
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmRiskEventEntity createEvent(ScrmRiskEventDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("事件参数不能为空");
        }
        ScrmRiskEventEntity entity = new ScrmRiskEventEntity();
        entity.setEventNo(dto.getEventNo() != null && !dto.getEventNo().isBlank() ?
                dto.getEventNo() : generateEventNo());
        entity.setRuleId(dto.getRuleId());
        entity.setRuleName(dto.getRuleName());
        entity.setRuleCode(dto.getRuleCode());
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setTargetType(dto.getTargetType());
        entity.setTargetValue(dto.getTargetValue());
        entity.setRiskCategory(dto.getRiskCategory());
        entity.setRiskLevel(dto.getRiskLevel());
        entity.setRiskScore(dto.getRiskScore() != null ? dto.getRiskScore()
                : ScrmBlacklistManageService.DEFAULT_RISK_SCORE);
        entity.setTriggerReason(dto.getTriggerReason());
        entity.setTriggerData(dto.getTriggerData());
        entity.setTriggerTime(dto.getTriggerTime() != null ? dto.getTriggerTime() : LocalDateTime.now());
        entity.setDetectedBy(dto.getDetectedBy() != null ? dto.getDetectedBy() : currentOperator());
        entity.setDetectionMethod(dto.getDetectionMethod() != null ? dto.getDetectionMethod() : "MANUAL");
        entity.setAction(dto.getAction() != null ? dto.getAction() : ScrmBlacklistManageService.DEFAULT_ACTION);
        entity.setActionStatus(DEFAULT_ACTION_STATUS);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : DEFAULT_EVENT_STATUS);
        entity.setIsFalsePositive(false);
        entity.setNotificationsSent(0);
        entity.setResolutionTimeHours(0);
        entity.setAffectedEntities(dto.getAffectedEntities());
        entity.setImpactAssessment(dto.getImpactAssessment());
        entity.setTags(dto.getTags());
        entity.setMetadata(dto.getMetadata());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        entity = eventRepository.save(entity);
        log.info("创建风险事件: id={}, eventNo={}", entity.getId(), entity.getEventNo());
        return entity;
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    @Transactional(readOnly = true)
    public ScrmRiskEventEntity getEvent(Long id) throws ScrmException {
        return findEventOrThrow(id);
    }

    /**
     * 按事件编号查询事件。
     *
     * @param eventNo 事件编号
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    @Transactional(readOnly = true)
    public ScrmRiskEventEntity getEventByNo(String eventNo) throws ScrmException {
        if (eventNo == null || eventNo.isBlank()) {
            throw ScrmException.badRequest("事件编号不能为空");
        }
        return eventRepository.findByEventNo(eventNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "风险事件不存在: eventNo=" + eventNo));
    }

    /**
     * 分页查询事件, 支持按规则 / 客户 / 风险类别 / 风险等级 / 状态 / 时间区间过滤。
     *
     * @param ruleId       规则 ID 过滤（可空）
     * @param customerId   客户 ID 过滤（可空）
     * @param riskCategory 风险类别过滤（可空）
     * @param riskLevel    风险等级过滤（可空）
     * @param status       状态过滤（可空）
     * @param startTime    触发起始时间（可空）
     * @param endTime      触发截止时间（可空）
     * @param pageable     分页参数
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmRiskEventEntity> listEvents(Long ruleId, Long customerId, String riskCategory, String riskLevel,
                                                 String status, LocalDateTime startTime, LocalDateTime endTime,
                                                 Pageable pageable) {
        Specification<ScrmRiskEventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ruleId != null) {
                predicates.add(cb.equal(root.get("ruleId"), ruleId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (riskCategory != null && !riskCategory.isBlank()) {
                predicates.add(cb.equal(root.get("riskCategory"), riskCategory));
            }
            if (riskLevel != null && !riskLevel.isBlank()) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("triggerTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("triggerTime"), endTime));
            }
            query.orderBy(cb.desc(root.get("triggerTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return eventRepository.findAll(spec, pageable);
    }

    /**
     * 触发风险事件 (完整实现)。
     * <p>根据规则创建事件 → 保存 → 执行动作 → 发送通知, 返回创建后的事件。</p>
     *
     * @param ruleId      规则 ID
     * @param customerId  客户 ID (可空)
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @param triggerData JSON 触发数据
     * @return 创建后的事件
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmRiskEventEntity fireEvent(Long ruleId, Long customerId, String targetType, String targetValue,
                                          String triggerData) throws ScrmException {
        ScrmBlacklistRuleEntity rule = findRuleOrThrow(ruleId);
        ScrmRiskEventEntity event = new ScrmRiskEventEntity();
        event.setEventNo(generateEventNo());
        event.setRuleId(rule.getId());
        event.setRuleName(rule.getRuleName());
        event.setRuleCode(rule.getRuleCode());
        event.setCustomerId(customerId);
        event.setTargetType(targetType);
        event.setTargetValue(targetValue);
        event.setRiskCategory(rule.getRiskCategory());
        event.setRiskLevel(rule.getSeverity());
        event.setRiskScore(ScrmBlacklistManageService.RISK_LEVEL_SCORE.getOrDefault(rule.getSeverity(), 50.0));
        event.setTriggerReason("规则 [" + rule.getRuleCode() + "] 命中: " + rule.getConditionField()
                + " " + rule.getConditionOperator() + " " + rule.getConditionValue());
        event.setTriggerData(triggerData);
        event.setTriggerTime(LocalDateTime.now());
        event.setDetectedBy(currentOperator());
        event.setDetectionMethod(DEFAULT_DETECTION_METHOD);
        event.setAction(rule.getAction());
        event.setActionStatus(DEFAULT_ACTION_STATUS);
        event.setStatus(DEFAULT_EVENT_STATUS);
        event.setIsFalsePositive(false);
        event.setNotificationsSent(0);
        event.setResolutionTimeHours(0);
        event.setCreatedBy(currentOperator());
        event = eventRepository.save(event);
        log.info("触发风险事件: eventNo={}, ruleId={}, targetType={}, targetValue={}",
                event.getEventNo(), ruleId, targetType, targetValue);
        // 执行动作
        executeAction(event.getId());
        return event;
    }

    /**
     * 分配事件处理人。
     *
     * @param id         事件 ID
     * @param assigneeId 处理人 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @Transactional
    public ScrmRiskEventEntity assignEvent(Long id, String assigneeId) throws ScrmException {
        ScrmRiskEventEntity event = findEventOrThrow(id);
        event.setAssignedTo(assigneeId);
        event.setAssignedAt(LocalDateTime.now());
        if ("OPEN".equals(event.getStatus())) {
            event.setStatus("INVESTIGATING");
        }
        event = eventRepository.save(event);
        log.info("分配事件: id={}, assignee={}", id, assigneeId);
        return event;
    }

    /**
     * 调查事件。
     *
     * @param id             事件 ID
     * @param investigatorId 调查人 ID
     * @param notes          调查备注
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @Transactional
    public ScrmRiskEventEntity investigate(Long id, String investigatorId, String notes) throws ScrmException {
        ScrmRiskEventEntity event = findEventOrThrow(id);
        event.setInvestigatedBy(investigatorId);
        event.setInvestigatedAt(LocalDateTime.now());
        event.setInvestigationNotes(notes);
        if ("OPEN".equals(event.getStatus())) {
            event.setStatus("INVESTIGATING");
        }
        event = eventRepository.save(event);
        log.info("调查事件: id={}, investigator={}", id, investigatorId);
        return event;
    }

    /**
     * 确认风险。
     *
     * @param id           事件 ID
     * @param confirmedBy 确认人 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @Transactional
    public ScrmRiskEventEntity confirmRisk(Long id, String confirmedBy) throws ScrmException {
        ScrmRiskEventEntity event = findEventOrThrow(id);
        event.setConfirmedRisk(true);
        event.setStatus("CONFIRMED");
        event.setInvestigatedBy(confirmedBy != null ? confirmedBy : currentOperator());
        event.setInvestigatedAt(LocalDateTime.now());
        event = eventRepository.save(event);
        log.info("确认风险: id={}, confirmedBy={}", id, confirmedBy);
        return event;
    }

    /**
     * 标记事件为误报。
     *
     * @param id     事件 ID
     * @param reason 误报原因
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @Transactional
    public ScrmRiskEventEntity markFalsePositive(Long id, String reason) throws ScrmException {
        ScrmRiskEventEntity event = findEventOrThrow(id);
        event.setIsFalsePositive(true);
        event.setStatus("FALSE_POSITIVE");
        event.setResolution(reason);
        event.setResolvedBy(currentOperator());
        event.setResolvedAt(LocalDateTime.now());
        calcResolutionTime(event);
        // 若关联规则, 同步误报统计
        if (event.getRuleId() != null) {
            ruleRepository.findById(event.getRuleId()).ifPresent(rule -> {
                rule.setFalsePositiveCount((rule.getFalsePositiveCount() != null ?
                        rule.getFalsePositiveCount() : 0) + 1);
                ruleRepository.save(rule);
            });
        }
        event = eventRepository.save(event);
        log.info("标记误报: id={}, reason={}", id, reason);
        return event;
    }

    /**
     * 解决事件。
     *
     * @param id         事件 ID
     * @param resolution 处理结果
     * @param resolvedBy 解决人 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @Transactional
    public ScrmRiskEventEntity resolveEvent(Long id, String resolution, String resolvedBy) throws ScrmException {
        ScrmRiskEventEntity event = findEventOrThrow(id);
        event.setStatus("RESOLVED");
        event.setResolution(resolution);
        event.setResolvedBy(resolvedBy != null ? resolvedBy : currentOperator());
        event.setResolvedAt(LocalDateTime.now());
        calcResolutionTime(event);
        event = eventRepository.save(event);
        log.info("解决事件: id={}, resolvedBy={}", id, event.getResolvedBy());
        return event;
    }

    /**
     * 升级事件。
     *
     * @param id           事件 ID
     * @param escalatedTo  升级给
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @Transactional
    public ScrmRiskEventEntity escalateEvent(Long id, String escalatedTo) throws ScrmException {
        ScrmRiskEventEntity event = findEventOrThrow(id);
        event.setStatus("ESCALATED");
        event.setEscalatedTo(escalatedTo);
        event.setEscalatedAt(LocalDateTime.now());
        event = eventRepository.save(event);
        log.info("升级事件: id={}, escalatedTo={}", id, escalatedTo);
        return event;
    }

    /**
     * 查询待处理事件 (status = OPEN)。
     *
     * @param pageable 分页参数
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmRiskEventEntity> getOpenEvents(Pageable pageable) {
        return eventRepository.findByStatus("OPEN", pageable);
    }

    /**
     * 查询严重事件 (riskLevel = HIGH / CRITICAL)。
     *
     * @param pageable 分页参数
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmRiskEventEntity> getCriticalEvents(Pageable pageable) {
        Specification<ScrmRiskEventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("riskLevel").in(CRITICAL_LEVELS));
            query.orderBy(cb.desc(root.get("triggerTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return eventRepository.findAll(spec, pageable);
    }

    /**
     * 按客户分页查询事件。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 事件分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmRiskEventEntity> getEventsByCustomer(Long customerId, Pageable pageable) {
        return eventRepository.findByCustomerId(customerId, pageable);
    }

    /**
     * 按规则分页查询事件。
     *
     * @param ruleId   规则 ID
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmRiskEventEntity> getEventsByRule(Long ruleId, Pageable pageable) {
        return eventRepository.findByRuleId(ruleId, pageable);
    }

    /**
     * 事件时间线 (按事件创建时间正序返回该事件及其相关事件)。
     *
     * @param id 事件 ID
     * @return 事件列表
     * @throws ScrmException 事件不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmRiskEventEntity> getEventTimeline(Long id) throws ScrmException {
        ScrmRiskEventEntity event = findEventOrThrow(id);
        List<ScrmRiskEventEntity> timeline = new ArrayList<>();
        timeline.add(event);
        if (event.getRelatedEventIds() != null && !event.getRelatedEventIds().isBlank()) {
            List<Long> relatedIds = parseLongList(event.getRelatedEventIds());
            for (Long rid : relatedIds) {
                eventRepository.findById(rid).ifPresent(timeline::add);
            }
        }
        timeline.sort((a, b) -> a.getTriggerTime().compareTo(b.getTriggerTime()));
        return timeline;
    }

    /**
     * 查询相关事件 (同一目标的其它事件)。
     *
     * @param id 事件 ID
     * @return 相关事件列表
     * @throws ScrmException 事件不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmRiskEventEntity> getRelatedEvents(Long id) throws ScrmException {
        ScrmRiskEventEntity event = findEventOrThrow(id);
        return eventRepository.findByTargetTypeAndTargetValue(
                 event.getTargetType(), event.getTargetValue()).stream()
                .filter(e -> !Objects.equals(e.getId(), id))
                .sorted((a, b) -> b.getTriggerTime().compareTo(a.getTriggerTime()))
                .collect(Collectors.toList());
    }

    /**
     * 执行事件动作 (完整实现)。
     * <p>BLOCK / AUTO_BLACKLIST → 将目标加入黑名单; ALERT / NOTIFY → 发送通知并计数;
     * QUARANTINE → 标记隔离并记录被阻止行为; REVIEW → 置为调查中。</p>
     *
     * @param eventId 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    @Transactional
    public ScrmRiskEventEntity executeAction(Long eventId) throws ScrmException {
        ScrmRiskEventEntity event = findEventOrThrow(eventId);
        String action = event.getAction() != null ? event.getAction() : ScrmBlacklistManageService.DEFAULT_ACTION;
        String result;
        try {
            if (BLOCK_ACTIONS.contains(action)) {
                // 加入黑名单
                ScrmBlacklistDto dto = new ScrmBlacklistDto();
                dto.setListType("BLACKLIST");
                dto.setTargetType(event.getTargetType());
                dto.setTargetValue(event.getTargetValue());
                dto.setCustomerId(event.getCustomerId());
                dto.setReason("风险事件触发自动加入: " + event.getEventNo());
                dto.setRiskLevel(event.getRiskLevel());
                dto.setRiskScore(event.getRiskScore());
                dto.setSource("RULE");
                dto.setSourceDetail("规则: " + (event.getRuleCode() != null
                        ? event.getRuleCode() : event.getRuleId()));
                dto.setRelatedEventId(event.getId());
                dto.setAddedBy(currentOperator());
                dto.setEffectiveDate(LocalDate.now());
                ScrmBlacklistEntity blacklist = blacklistService.addToBlacklist(dto);
                result = "已加入黑名单: blacklistId=" + blacklist.getId();
            } else if ("ALERT".equals(action) || "NOTIFY".equals(action)) {
                // 发送通知 (此处仅记录日志与计数, 实际通知由通知服务异步处理)
                event.setNotificationsSent((event.getNotificationsSent() != null ?
                        event.getNotificationsSent() : 0) + 1);
                result = "已发送告警通知";
                log.info("风险事件告警: eventNo={}, target={}", event.getEventNo(), event.getTargetValue());
            } else if ("QUARANTINE".equals(action)) {
                // 隔离: 记录被阻止的行为
                event.setBlockedAction("目标 " + event.getTargetType() + ":" + event.getTargetValue() + " 已隔离");
                result = "已隔离目标";
            } else if ("REVIEW".equals(action)) {
                event.setStatus("INVESTIGATING");
                result = "转人工审核";
            } else {
                result = "无动作";
            }
            event.setActionStatus("EXECUTED");
        } catch (Exception e) {
            event.setActionStatus("FAILED");
            result = "动作执行失败: " + e.getMessage();
            log.warn("风险事件动作执行失败: eventNo={}, error={}", event.getEventNo(), e.getMessage());
        }
        event.setActionResult(result);
        event.setActionExecutedAt(LocalDateTime.now());
        event = eventRepository.save(event);
        return event;
    }

    /**
     * 批量解决事件。
     *
     * @param eventIds  事件 ID 列表
     * @param resolution 处理结果
     * @param resolvedBy 解决人 ID
     * @return 批量结果 {total, success, failed}
     */
    @Transactional
    public Map<String, Integer> batchResolve(List<Long> eventIds, String resolution, String resolvedBy) {
        Map<String, Integer> result = new LinkedHashMap<>();
        int success = 0;
        int failed = 0;
        if (eventIds == null || eventIds.isEmpty()) {
            result.put("total", 0);
            result.put("success", 0);
            result.put("failed", 0);
            return result;
        }
        for (Long id : eventIds) {
            try {
                resolveEvent(id, resolution, resolvedBy);
                success++;
            } catch (Exception e) {
                log.warn("批量解决事件失败: id={}, error={}", id, e.getMessage());
                failed++;
            }
        }
        result.put("total", eventIds.size());
        result.put("success", success);
        result.put("failed", failed);
        return result;
    }

    /**
     * 生成事件编号: RISK + 年月日 + 4 位序号。
     *
     * @return 事件编号
     */
    public String generateEventNo() {
        String prefix = "RISK" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        long seq = eventRepository.countByEventNoStartingWith(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 按主键查询事件, 不存在抛异常。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    private ScrmRiskEventEntity findEventOrThrow(Long id) throws ScrmException {
        ScrmRiskEventEntity entity = eventRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "风险事件不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询规则, 不存在抛异常。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmBlacklistRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmBlacklistRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "风控规则不存在: id=" + id));
        return entity;
    }

    /**
     * 当前操作人 (优先取 UserContext, 缺省 scrm-system)。
     *
     * @return 当前操作人
     */
    private String currentOperator() {
        String userId = UserContext.getUserId();
        return userId != null ? userId : ScrmBlacklistManageService.DEFAULT_OPERATOR;
    }

    /**
     * 计算事件处理时长 (小时)。
     *
     * @param event 事件实体
     */
    private void calcResolutionTime(ScrmRiskEventEntity event) {
        if (event.getTriggerTime() != null && event.getResolvedAt() != null) {
            long hours = java.time.Duration.between(event.getTriggerTime(), event.getResolvedAt()).toHours();
            event.setResolutionTimeHours((int) hours);
        }
    }

    /**
     * 解析逗号分隔的 Long 列表。
     *
     * @param csv 逗号分隔字符串
     * @return Long 列表
     */
    private List<Long> parseLongList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String s : csv.split(",")) {
            try {
                result.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略非数字
            }
        }
        return result;
    }
}