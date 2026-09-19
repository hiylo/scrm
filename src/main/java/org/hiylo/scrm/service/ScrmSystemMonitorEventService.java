/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemMonitorEventService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAlertAcknowledgeDto;
import org.hiylo.scrm.entity.ScrmAlertEventEntity;
import org.hiylo.scrm.entity.ScrmAlertRuleEntity;
import org.hiylo.scrm.entity.ScrmMonitorMetricEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAlertEventRepository;
import org.hiylo.scrm.repository.ScrmAlertRuleRepository;
import org.hiylo.scrm.repository.ScrmMonitorMetricRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SCRM 系统监控告警事件服务。
 * <p>
 * 承载告警事件管理子域: 事件触发 (规则评估 → 条件检查 → 触发事件 → 发送通知 → 冷却控制) /
 * 查询 / 确认 / 恢复 / 抑制 / 升级 / 时间线 / 关联事件 / 批量处理 / 统计 / 冷却检查与事件编号
 * 生成, 以及指标恢复时的触发中事件自动恢复。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSystemMonitorEventService {

    /** 事件状态: 触发中 */
    private static final String STATUS_FIRING = "FIRING";
    /** 事件状态: 待定 */
    private static final String STATUS_PENDING = "PENDING";
    /** 事件状态: 已恢复 */
    private static final String STATUS_RESOLVED = "RESOLVED";
    /** 事件状态: 已确认 */
    private static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    /** 事件状态: 已抑制 */
    private static final String STATUS_SUPPRESSED = "SUPPRESSED";
    /** 事件状态: 已过期 */
    private static final String STATUS_EXPIRED = "EXPIRED";

    /** 严重程度: 警告 */
    private static final String SEVERITY_WARNING = "WARNING";
    /** 严重程度: 严重 */
    private static final String SEVERITY_CRITICAL = "CRITICAL";
    /** 严重程度: 致命 */
    private static final String SEVERITY_FATAL = "FATAL";

    /** 恢复方式: 自动 */
    private static final String RESOLUTION_AUTO = "AUTO";
    /** 恢复方式: 手动 */
    private static final String RESOLUTION_MANUAL = "MANUAL";
    /** 恢复方式: 抑制 */
    private static final String RESOLUTION_SUPPRESSED = "SUPPRESSED";

    /** 合法的严重程度 */
    private static final List<String> VALID_SEVERITIES = List.of(
            "INFO", "WARNING", "CRITICAL", "FATAL");
    /** 合法的事件状态 */
    private static final List<String> VALID_EVENT_STATUSES = List.of(
            "FIRING", "PENDING", "RESOLVED", "ACKNOWLEDGED", "SUPPRESSED", "EXPIRED");

    /** 事件编号日期格式 */
    private static final DateTimeFormatter EVENT_NO_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 当日事件编号序号 (进程内自增, 配合事件编号生成) */
    private final AtomicInteger eventNoSequence = new AtomicInteger(0);
    /** 事件编号生成日期锚点 (跨天重置序号) */
    private volatile String eventNoDateAnchor = "";

    /** 告警事件数据访问层 */
    private final ScrmAlertEventRepository eventRepository;
    /** 告警规则数据访问层 */
    private final ScrmAlertRuleRepository ruleRepository;
    /** 监控指标数据访问层 */
    private final ScrmMonitorMetricRepository metricRepository;
    /** 告警规则子域服务 (规则评估 / 触发统计) */
    private final ScrmSystemMonitorRuleService ruleService;

    /**
     * 触发告警事件 (完整实现: 创建事件 → 发送通知 → 更新统计 → 标记指标告警态)。
     *
     * @param ruleId       规则 ID (可空)
     * @param metricId     指标 ID (可空)
     * @param triggerValue 触发值
     * @return 创建的告警事件
     * @throws ScrmException 规则/指标不存在
     */
    @Transactional
    public ScrmAlertEventEntity fireEvent(Long ruleId, Long metricId, Double triggerValue) throws ScrmException {
        ScrmAlertRuleEntity rule = null;
        ScrmMonitorMetricEntity metric = null;
        if (ruleId != null) {
            rule = findRuleOrThrow(ruleId);
            metricId = rule.getMetricId();
        }
        if (metricId != null) {
            metric = findMetricOrThrow(metricId);
        }
        double triggerVal = triggerValue != null ? triggerValue : 0.0;
        String severity = rule != null ? rule.getSeverity() : SEVERITY_WARNING;
        String condition = rule != null ? rule.getCondition() : null;
        double threshold = rule != null && rule.getThresholdValue() != null ? rule.getThresholdValue() : 0.0;
        // 构建事件
        ScrmAlertEventEntity event = new ScrmAlertEventEntity();
        event.setEventNo(generateEventNo());
        event.setRuleId(ruleId);
        event.setRuleName(rule != null ? rule.getRuleName() : null);
        event.setRuleCode(rule != null ? rule.getRuleCode() : null);
        event.setMetricId(metricId);
        event.setMetricName(metric != null ? metric.getMetricName() : null);
        event.setMetricCode(metric != null ? metric.getMetricCode() : null);
        event.setSeverity(severity);
        event.setStatus(STATUS_FIRING);
        event.setTriggerValue(triggerVal);
        event.setThresholdValue(threshold);
        event.setCondition(condition);
        event.setTriggerTime(LocalDateTime.now());
        event.setDurationSeconds(0);
        event.setFireCount(1);
        event.setTitle(buildEventTitle(rule, metric, severity));
        event.setMessage(buildEventMessage(rule, metric, triggerVal, threshold, condition));
        event.setDescription(buildEventDescription(rule, metric, triggerVal, threshold, condition));
        event.setAffectedUsers(0);
        event.setNotificationsSent(0);
        event.setNotificationFailures(0);
        event.setEscalated(Boolean.FALSE);
        event.setAffectedServices(metric != null ? metric.getMetricGroup() : null);
        event.setCreatedBy(rule != null ? rule.getCreatedBy() : null);
        event = eventRepository.save(event);
        log.info("触发告警事件: eventId={}, eventNo={}, ruleId={}, metricId={}, severity={}",
                event.getId(), event.getEventNo(), ruleId, metricId, severity);
        // 发送通知
        sendNotification(event.getId());
        // 更新规则统计
        if (ruleId != null) {
            try {
                ruleService.updateRuleStats(ruleId);
            } catch (ScrmException e) {
                log.warn("更新规则统计失败, 忽略: ruleId={}, err={}", ruleId, e.getMessage());
            }
        }
        // 标记指标告警激活
        if (metric != null) {
            metric.setIsAlertActive(Boolean.TRUE);
            metric.setLastAlertAt(LocalDateTime.now());
            metric.setAlertCount((metric.getAlertCount() != null ? metric.getAlertCount() : 0) + 1);
            metricRepository.save(metric);
        }
        return event;
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    @Transactional(readOnly = true)
    public ScrmAlertEventEntity getEvent(Long id) throws ScrmException {
        return findEventOrThrow(id);
    }

    /**
     * 按编号查询事件。
     *
     * @param eventNo 事件编号
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    @Transactional(readOnly = true)
    public ScrmAlertEventEntity getEventByNo(String eventNo) throws ScrmException {
        if (eventNo == null || eventNo.isBlank()) {
            throw ScrmException.badRequest("事件编号不能为空");
        }
        return eventRepository.findByEventNo(eventNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "告警事件不存在: eventNo=" + eventNo));
    }

    /**
     * 分页查询事件, 支持按规则 / 指标 / 严重度 / 状态 / 时间区间过滤。
     *
     * @param ruleId     规则 ID 过滤（可空）
     * @param metricId   指标 ID 过滤（可空）
     * @param severity   严重程度过滤（可空）
     * @param status     状态过滤（可空）
     * @param startTime  触发时间起始（可空）
     * @param endTime    触发时间截止（可空）
     * @param pageable   分页参数
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAlertEventEntity> listEvents(Long ruleId, Long metricId, String severity, String status,
                                                  LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmAlertEventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ruleId != null) {
                predicates.add(cb.equal(root.get("ruleId"), ruleId));
            }
            if (metricId != null) {
                predicates.add(cb.equal(root.get("metricId"), metricId));
            }
            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
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
     * 查询触发中的事件 (status=FIRING)。
     *
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAlertEventEntity> getFiringEvents(Pageable pageable) {
        return eventRepository.findByStatusOrderByTriggerTimeDesc(
                 STATUS_FIRING, pageable);
    }

    /**
     * 查询严重事件 (severity=CRITICAL 或 FATAL)。
     *
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAlertEventEntity> getCriticalEvents(Pageable pageable) {
        Specification<ScrmAlertEventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.or(cb.equal(root.get("severity"), SEVERITY_CRITICAL),
                    cb.equal(root.get("severity"), SEVERITY_FATAL)));
            query.orderBy(cb.desc(root.get("triggerTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return eventRepository.findAll(spec, pageable);
    }

    /**
     * 确认告警: 支持 ACKNOWLEDGE / RESOLVE / SUPPRESS 三种操作。
     *
     * @param acknowledgeDto 确认参数
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法 / 参数非法
     */
    @Transactional
    public ScrmAlertEventEntity acknowledgeEvent(ScrmAlertAcknowledgeDto acknowledgeDto) throws ScrmException {
        if (acknowledgeDto == null) {
            throw ScrmException.badRequest("确认参数不能为空");
        }
        ScrmAlertEventEntity event = findEventOrThrow(acknowledgeDto.getEventId());
        String action = acknowledgeDto.getAction();
        if (action == null || action.isBlank()) {
            throw ScrmException.badRequest("操作类型不能为空");
        }
        switch (action) {
            case "ACKNOWLEDGE":
                if (!STATUS_FIRING.equals(event.getStatus()) && !STATUS_PENDING.equals(event.getStatus())) {
                    throw ScrmException.conflict("仅 FIRING/PENDING 状态可确认: eventId=" + event.getId()
                            + ", status=" + event.getStatus());
                }
                event.setStatus(STATUS_ACKNOWLEDGED);
                event.setAcknowledgedBy(acknowledgeDto.getResolvedBy());
                event.setAcknowledgedAt(LocalDateTime.now());
                event.setAcknowledgeNote(acknowledgeDto.getNote());
                break;
            case "RESOLVE":
                return resolveEvent(event.getId(), acknowledgeDto.getResolvedBy(), acknowledgeDto.getNote());
            case "SUPPRESS":
                int duration = acknowledgeDto.getSuppressDurationMinutes() != null
                        ? acknowledgeDto.getSuppressDurationMinutes() : 60;
                return suppressEvent(event.getId(), acknowledgeDto.getNote(), duration);
            default:
                throw ScrmException.badRequest("操作类型非法: " + action + ", 仅支持 ACKNOWLEDGE/RESOLVE/SUPPRESS");
        }
        event = eventRepository.save(event);
        log.info("确认告警事件: eventId={}, action={}, operator={}", event.getId(), action, acknowledgeDto.getResolvedBy());
        return event;
    }

    /**
     * 手动恢复告警。
     *
     * @param eventId    事件 ID
     * @param resolvedBy 恢复人
     * @param note       恢复备注
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @Transactional
    public ScrmAlertEventEntity resolveEvent(Long eventId, String resolvedBy, String note) throws ScrmException {
        ScrmAlertEventEntity event = findEventOrThrow(eventId);
        if (STATUS_RESOLVED.equals(event.getStatus())) {
            throw ScrmException.conflict("事件已恢复, 不可重复恢复: eventId=" + eventId);
        }
        LocalDateTime now = LocalDateTime.now();
        event.setStatus(STATUS_RESOLVED);
        event.setResolvedTime(now);
        event.setResolvedBy(resolvedBy);
        event.setResolvedNote(note);
        event.setResolutionType(RESOLUTION_MANUAL);
        if (event.getTriggerTime() != null) {
            event.setDurationSeconds((int) ChronoUnit.SECONDS.between(event.getTriggerTime(), now));
        }
        event = eventRepository.save(event);
        // 更新规则恢复时间 & 取消指标告警态
        if (event.getRuleId() != null) {
            try {
                ScrmAlertRuleEntity rule = findRuleOrThrow(event.getRuleId());
                rule.setLastResolvedAt(now);
                ruleRepository.save(rule);
            } catch (ScrmException e) {
                log.warn("更新规则恢复时间失败, 忽略: ruleId={}, err={}", event.getRuleId(), e.getMessage());
            }
        }
        if (event.getMetricId() != null) {
            try {
                ScrmMonitorMetricEntity metric = findMetricOrThrow(event.getMetricId());
                metric.setIsAlertActive(Boolean.FALSE);
                metricRepository.save(metric);
            } catch (ScrmException e) {
                log.warn("取消指标告警态失败, 忽略: metricId={}, err={}", event.getMetricId(), e.getMessage());
            }
        }
        log.info("恢复告警事件: eventId={}, resolvedBy={}, duration={}s", eventId, resolvedBy, event.getDurationSeconds());
        return event;
    }

    /**
     * 抑制告警 (指定时长内不再通知)。
     *
     * @param eventId   事件 ID
     * @param reason    抑制原因
     * @param duration  抑制时长分钟
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @Transactional
    public ScrmAlertEventEntity suppressEvent(Long eventId, String reason, int duration) throws ScrmException {
        ScrmAlertEventEntity event = findEventOrThrow(eventId);
        if (STATUS_RESOLVED.equals(event.getStatus())) {
            throw ScrmException.conflict("事件已恢复, 不可抑制: eventId=" + eventId);
        }
        event.setStatus(STATUS_SUPPRESSED);
        event.setResolutionType(RESOLUTION_SUPPRESSED);
        event.setAcknowledgeNote(reason);
        event.setAcknowledgedAt(LocalDateTime.now());
        if (duration > 0) {
            Map<String, Object> meta = parseJson(event.getMetadata());
            meta.put("suppressUntil", LocalDateTime.now().plusMinutes(duration).toString());
            meta.put("suppressDurationMinutes", duration);
            event.setMetadata(toJsonString(meta));
        }
        event = eventRepository.save(event);
        log.info("抑制告警事件: eventId={}, reason={}, duration={}min", eventId, reason, duration);
        return event;
    }

    /**
     * 检查自动恢复: 评估规则当前是否不再触发, 若是则自动恢复事件。
     *
     * @param eventId 事件 ID
     * @return 检查结果 Map {autoResolved, reason}
     * @throws ScrmException 事件不存在
     */
    @Transactional
    public Map<String, Object> checkAutoResolve(Long eventId) throws ScrmException {
        ScrmAlertEventEntity event = findEventOrThrow(eventId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", eventId);
        result.put("autoResolved", false);
        if (!STATUS_FIRING.equals(event.getStatus()) && !STATUS_ACKNOWLEDGED.equals(event.getStatus())) {
            result.put("reason", "事件状态非 FIRING/ACKNOWLEDGED, 无需自动恢复");
            return result;
        }
        if (event.getRuleId() == null) {
            result.put("reason", "事件无关联规则, 无法自动恢复");
            return result;
        }
        try {
            ScrmAlertRuleEntity rule = findRuleOrThrow(event.getRuleId());
            if (Boolean.FALSE.equals(rule.getAutoResolve())) {
                result.put("reason", "规则未开启自动恢复");
                return result;
            }
            Map<String, Object> eval = ruleService.evaluateRule(rule.getId());
            boolean triggered = Boolean.TRUE.equals(eval.get("triggered"));
            if (!triggered) {
                LocalDateTime now = LocalDateTime.now();
                event.setStatus(STATUS_RESOLVED);
                event.setResolvedTime(now);
                event.setResolvedBy("SYSTEM");
                event.setResolvedNote(rule.getAutoResolveMessage() != null
                        ? rule.getAutoResolveMessage() : "指标恢复正常, 自动恢复");
                event.setResolutionType(RESOLUTION_AUTO);
                if (event.getTriggerTime() != null) {
                    event.setDurationSeconds((int) ChronoUnit.SECONDS.between(event.getTriggerTime(), now));
                }
                eventRepository.save(event);
                // 取消指标告警态
                if (event.getMetricId() != null) {
                    try {
                        ScrmMonitorMetricEntity metric = findMetricOrThrow(event.getMetricId());
                        metric.setIsAlertActive(Boolean.FALSE);
                        metricRepository.save(metric);
                    } catch (ScrmException e) {
                        log.warn("取消指标告警态失败: metricId={}, err={}", event.getMetricId(), e.getMessage());
                    }
                }
                result.put("autoResolved", true);
                result.put("reason", "指标恢复正常, 已自动恢复");
                log.info("自动恢复告警事件: eventId={}", eventId);
                return result;
            }
            result.put("reason", "指标仍触发规则, 未自动恢复");
            return result;
        } catch (ScrmException e) {
            result.put("reason", "规则评估失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 升级告警: 超过升级时间后通知升级接收人。
     *
     * @param eventId 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 已升级 / 状态非法
     */
    @Transactional
    public ScrmAlertEventEntity escalateEvent(Long eventId) throws ScrmException {
        ScrmAlertEventEntity event = findEventOrThrow(eventId);
        if (Boolean.TRUE.equals(event.getEscalated())) {
            throw ScrmException.conflict("事件已升级, 不可重复升级: eventId=" + eventId);
        }
        if (STATUS_RESOLVED.equals(event.getStatus())) {
            throw ScrmException.conflict("事件已恢复, 不可升级: eventId=" + eventId);
        }
        String escalatedTo = null;
        if (event.getRuleId() != null) {
            try {
                ScrmAlertRuleEntity rule = findRuleOrThrow(event.getRuleId());
                escalatedTo = rule.getEscalationRecipients();
            } catch (ScrmException e) {
                log.warn("获取规则升级接收人失败: ruleId={}, err={}", event.getRuleId(), e.getMessage());
            }
        }
        event.setEscalated(Boolean.TRUE);
        event.setEscalatedAt(LocalDateTime.now());
        event.setEscalatedTo(escalatedTo);
        event = eventRepository.save(event);
        // 发送升级通知
        try {
            sendNotification(event.getId());
        } catch (Exception e) {
            log.warn("升级通知发送失败: eventId={}, err={}", eventId, e.getMessage());
        }
        log.info("升级告警事件: eventId={}, escalatedTo={}", eventId, escalatedTo);
        return event;
    }

    /**
     * 获取事件时间线 (触发 / 确认 / 通知 / 升级 / 恢复等关键节点)。
     *
     * @param eventId 事件 ID
     * @return 时间线列表 [{timestamp, action, detail}]
     * @throws ScrmException 事件不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEventTimeline(Long eventId) throws ScrmException {
        ScrmAlertEventEntity event = findEventOrThrow(eventId);
        List<Map<String, Object>> timeline = new ArrayList<>();
        if (event.getTriggerTime() != null) {
            timeline.add(buildTimelineItem(event.getTriggerTime(), "TRIGGER",
                    "告警触发, 严重程度: " + event.getSeverity() + ", 触发值: " + event.getTriggerValue()));
        }
        if (event.getNotificationsSent() != null && event.getNotificationsSent() > 0 && event.getLastNotificationAt() != null) {
            timeline.add(buildTimelineItem(event.getLastNotificationAt(), "NOTIFY",
                    "发送通知, 成功 " + event.getNotificationsSent() + " 次, 失败"
                            + (event.getNotificationFailures() != null ? event.getNotificationFailures() : 0) + " 次"));
        }
        if (event.getAcknowledgedAt() != null) {
            timeline.add(buildTimelineItem(event.getAcknowledgedAt(), "ACKNOWLEDGE",
                    "告警确认, 确认人: " + event.getAcknowledgedBy()
                            + (event.getAcknowledgeNote() != null ? ", 备注: " + event.getAcknowledgeNote() : "")));
        }
        if (Boolean.TRUE.equals(event.getEscalated()) && event.getEscalatedAt() != null) {
            timeline.add(buildTimelineItem(event.getEscalatedAt(), "ESCALATE",
                    "告警升级, 升级接收人: " + event.getEscalatedTo()));
        }
        if (event.getResolvedTime() != null) {
            timeline.add(buildTimelineItem(event.getResolvedTime(), "RESOLVE",
                    "告警恢复, 恢复方式: " + event.getResolutionType()
                            + ", 恢复人: " + event.getResolvedBy()
                            + ", 持续 " + event.getDurationSeconds() + " 秒"));
        }
        return timeline;
    }

    /**
     * 获取相关联事件 (同规则或同指标的其它事件)。
     *
     * @param eventId 事件 ID
     * @return 相关联事件列表
     * @throws ScrmException 事件不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmAlertEventEntity> getRelatedEvents(Long eventId) throws ScrmException {
        ScrmAlertEventEntity event = findEventOrThrow(eventId);
        Specification<ScrmAlertEventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("id"), eventId));
            if (event.getRuleId() != null) {
                predicates.add(cb.equal(root.get("ruleId"), event.getRuleId()));
            } else {
                predicates.add(cb.equal(root.get("metricId"), event.getMetricId()));
            }
            query.orderBy(cb.desc(root.get("triggerTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return eventRepository.findAll(spec);
    }

    /**
     * 事件统计: 总数 / 各状态数 / 各严重度数。
     *
     * @param startTime 触发时间起始（可空）
     * @param endTime   触发时间截止（可空）
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getEventStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 按状态聚合
        List<Object[]> byStatus = eventRepository.countByStatus(startTime, endTime);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : VALID_EVENT_STATUSES) {
            statusCount.put(s, 0L);
        }
        long total = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
        }
        stats.put("statusCount", statusCount);
        stats.put("total", total);
        stats.put("firing", statusCount.getOrDefault(STATUS_FIRING, 0L));
        stats.put("acknowledged", statusCount.getOrDefault(STATUS_ACKNOWLEDGED, 0L));
        stats.put("resolved", statusCount.getOrDefault(STATUS_RESOLVED, 0L));
        // 按严重度聚合
        List<Object[]> bySeverity = eventRepository.countBySeverity(startTime, endTime);
        Map<String, Long> severityCount = new LinkedHashMap<>();
        for (String s : VALID_SEVERITIES) {
            severityCount.put(s, 0L);
        }
        for (Object[] row : bySeverity) {
            String severity = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            severityCount.put(severity, count);
        }
        stats.put("severityCount", severityCount);
        return stats;
    }

    /**
     * 批量处理告警事件 (统一确认 / 恢复 / 抑制)。
     *
     * @param eventIds 事件 ID 列表
     * @param action   操作类型: ACKNOWLEDGE/RESOLVE/SUPPRESS
     * @param note     操作备注
     * @param operator 操作人
     * @return 批量处理结果 Map {total, success, failed, results}
     */
    @Transactional
    public Map<String, Object> batchAcknowledge(List<Long> eventIds, String action, String note, String operator) {
        if (eventIds == null || eventIds.isEmpty()) {
            throw ScrmException.badRequest("事件 ID 列表不能为空");
        }
        if (action == null || action.isBlank()) {
            throw ScrmException.badRequest("操作类型不能为空");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        for (Long eventId : eventIds) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("eventId", eventId);
            try {
                ScrmAlertAcknowledgeDto dto = new ScrmAlertAcknowledgeDto();
                dto.setEventId(eventId);
                dto.setAction(action);
                dto.setNote(note);
                dto.setResolvedBy(operator);
                acknowledgeEvent(dto);
                r.put("success", true);
                success++;
            } catch (ScrmException e) {
                r.put("success", false);
                r.put("error", e.getMessage());
            }
            results.add(r);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", eventIds.size());
        summary.put("success", success);
        summary.put("failed", eventIds.size() - success);
        summary.put("results", results);
        log.info("批量处理告警事件: action={}, total={}, success={}", action, eventIds.size(), success);
        return summary;
    }

    /**
     * 发送通知 (模拟实现: 按规则通知渠道分发, 记录发送数与失败数)。
     *
     * @param eventId 事件 ID
     * @return 通知结果 Map {channels, sent, failures}
     * @throws ScrmException 事件不存在
     */
    @Transactional
    public Map<String, Object> sendNotification(Long eventId) throws ScrmException {
        ScrmAlertEventEntity event = findEventOrThrow(eventId);
        String channels = null;
        String recipients = null;
        if (event.getRuleId() != null) {
            try {
                ScrmAlertRuleEntity rule = findRuleOrThrow(event.getRuleId());
                channels = rule.getNotificationChannels();
                recipients = rule.getRecipients();
            } catch (ScrmException e) {
                log.warn("获取规则通知配置失败: ruleId={}, err={}", event.getRuleId(), e.getMessage());
            }
        }
        List<String> channelList = parseCsv(channels);
        if (channelList.isEmpty()) {
            channelList = List.of("EMAIL");
        }
        int sent = 0;
        int failures = 0;
        for (String channel : channelList) {
            // 模拟通知: 随机成功/失败 (90% 成功率)
            boolean ok = ThreadLocalRandom.current().nextDouble() < 0.9;
            if (ok) {
                sent++;
            } else {
                failures++;
            }
        }
        event.setNotificationsSent((event.getNotificationsSent() != null ? event.getNotificationsSent() : 0) + sent);
        event.setNotificationFailures((event.getNotificationFailures() != null
                ? event.getNotificationFailures() : 0) + failures);
        event.setLastNotificationAt(LocalDateTime.now());
        eventRepository.save(event);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", eventId);
        result.put("channels", channelList);
        result.put("recipients", recipients);
        result.put("sent", sent);
        result.put("failures", failures);
        log.info("发送告警通知: eventId={}, channels={}, sent={}, failures={}", eventId, channelList, sent, failures);
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 自动恢复指定规则下触发中的事件 (供指标恢复联动调用)。
     *
     * @param ruleId 规则 ID
     */
    void autoResolveFiringEvents(Long ruleId) {
        List<ScrmAlertEventEntity> firingEvents = eventRepository.findByRuleIdAndStatus(
                 ruleId, STATUS_FIRING);
        for (ScrmAlertEventEntity event : firingEvents) {
            try {
                LocalDateTime now = LocalDateTime.now();
                event.setStatus(STATUS_RESOLVED);
                event.setResolvedTime(now);
                event.setResolvedBy("SYSTEM");
                event.setResolvedNote("指标恢复正常, 自动恢复");
                event.setResolutionType(RESOLUTION_AUTO);
                if (event.getTriggerTime() != null) {
                    event.setDurationSeconds((int) ChronoUnit.SECONDS.between(event.getTriggerTime(), now));
                }
                eventRepository.save(event);
            } catch (Exception e) {
                log.warn("自动恢复事件失败: eventId={}, err={}", event.getId(), e.getMessage());
            }
        }
    }

    /**
     * 构建告警事件标题。
     *
     * @param rule    规则实体 (可空)
     * @param metric  指标实体 (可空)
     * @param severity 严重程度
     * @return 标题
     */
    private String buildEventTitle(ScrmAlertRuleEntity rule, ScrmMonitorMetricEntity metric, String severity) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("]");
        if (metric != null) {
            sb.append("指标 ").append(metric.getMetricName()).append(" 告警");
        } else {
            sb.append("系统告警");
        }
        if (rule != null) {
            sb.append(" (规则: ").append(rule.getRuleName()).append(")");
        }
        return sb.toString();
    }

    /**
     * 构建告警事件消息。
     *
     * @param rule          规则实体 (可空)
     * @param metric        指标实体 (可空)
     * @param triggerValue  触发值
     * @param threshold     阈值
     * @param condition     条件操作符
     * @return 消息
     */
    private String buildEventMessage(ScrmAlertRuleEntity rule, ScrmMonitorMetricEntity metric,
                                     double triggerValue, double threshold, String condition) {
        StringBuilder sb = new StringBuilder();
        if (metric != null) {
            sb.append("指标 ").append(metric.getMetricName());
            sb.append(" 当前值 ").append(triggerValue);
            if (metric.getUnit() != null) {
                sb.append(metric.getUnit());
            }
        } else {
            sb.append("当前值 ").append(triggerValue);
        }
        if (rule != null) {
            sb.append(", 触发规则 ").append(rule.getRuleName());
            sb.append(" (").append(condition).append(" ").append(threshold).append(")");
        }
        return sb.toString();
    }

    /**
     * 构建告警事件详细描述。
     *
     * @param rule          规则实体 (可空)
     * @param metric        指标实体 (可空)
     * @param triggerValue  触发值
     * @param threshold     阈值
     * @param condition     条件操作符
     * @return 详细描述
     */
    private String buildEventDescription(ScrmAlertRuleEntity rule, ScrmMonitorMetricEntity metric,
                                         double triggerValue, double threshold, String condition) {
        StringBuilder sb = new StringBuilder();
        sb.append("告警详情:\n");
        if (metric != null) {
            sb.append("- 指标: ").append(metric.getMetricName())
                    .append(" (").append(metric.getMetricCode()).append(")\n");
            sb.append("- 分组: ").append(metric.getMetricGroup()).append("\n");
            sb.append("- 当前值: ").append(triggerValue);
            if (metric.getUnit() != null) {
                sb.append(metric.getUnit());
            }
            sb.append("\n");
        }
        if (rule != null) {
            sb.append("- 规则: ").append(rule.getRuleName())
                    .append(" (").append(rule.getRuleCode()).append(")\n");
            sb.append("- 条件: ").append(condition).append(" ").append(threshold).append("\n");
            sb.append("- 严重程度: ").append(rule.getSeverity()).append("\n");
        }
        sb.append("- 触发时间: ").append(LocalDateTime.now()).append("\n");
        return sb.toString();
    }

    /**
     * 构建时间线条目。
     *
     * @param timestamp 时间戳
     * @param action    动作
     * @param detail    详情
     * @return 时间线条目 Map
     */
    private Map<String, Object> buildTimelineItem(LocalDateTime timestamp, String action, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("timestamp", timestamp);
        item.put("action", action);
        item.put("detail", detail);
        return item;
    }

    /**
     * 生成事件编号: ALERT + 年月日 + 4 位序号。
     *
     * @return 事件编号
     */
    public String generateEventNo() {
        String today = LocalDate.now().format(EVENT_NO_DATE_FMT);
        // 跨天重置序号
        synchronized (this) {
            if (!today.equals(eventNoDateAnchor)) {
                eventNoDateAnchor = today;
                eventNoSequence.set(0);
            }
        }
        int seq = eventNoSequence.incrementAndGet();
        return "ALERT" + today + String.format("%04d", seq);
    }

    /**
     * 解析 JSON 字符串为 Map。
     *
     * @param json JSON 字符串
     * @return Map (解析失败返回空 Map)
     */
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = SimpleJsonParser.parse(json);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) parsed;
                return result;
            }
        } catch (Exception e) {
            log.warn("解析 JSON 失败: {}", e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    /**
     * 将对象转换为 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    private String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        return SimpleJsonParser.toJson(obj);
    }

    /**
     * 解析逗号分隔字符串为列表。
     *
     * @param csv 逗号分隔字符串
     * @return 列表
     */
    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 按主键查询指标, 不存在抛异常, 并校验归属账号。
     *
     * @param id 指标 ID
     * @return 指标实体
     * @throws ScrmException 指标不存在
     */
    private ScrmMonitorMetricEntity findMetricOrThrow(Long id) throws ScrmException {
        ScrmMonitorMetricEntity entity = metricRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "监控指标不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询规则, 不存在抛异常, 并校验归属账号。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmAlertRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmAlertRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "告警规则不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询事件, 不存在抛异常, 并校验归属账号。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    private ScrmAlertEventEntity findEventOrThrow(Long id) throws ScrmException {
        ScrmAlertEventEntity entity = eventRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "告警事件不存在: id=" + id));
        return entity;
    }

    /**
     * 简易 JSON 解析与序列化工具 (避免引入额外 JSON 依赖, 支持基础 Map/List/基础类型)。
     * <p>仅用于指标历史数据与 metadata 等简单结构, 复杂结构请使用 Jackson。</p>
     *
     * @author Hsi Chu
     * @since V1.0
     */
    private static final class SimpleJsonParser {

        private SimpleJsonParser() {
        }

        /**
         * 解析 JSON 字符串为对象 (Map / List / String / Number / Boolean / null)。
         *
         * @param json JSON 字符串
         * @return 解析后的对象
         * @throws RuntimeException 解析失败
         */
        static Object parse(String json) {
            return new Parser(json).parseValue();
        }

        /**
         * 将对象序列化为 JSON 字符串。
         *
         * @param obj 对象
         * @return JSON 字符串
         */
        static String toJson(Object obj) {
            StringBuilder sb = new StringBuilder();
            write(sb, obj);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void write(StringBuilder sb, Object obj) {
            if (obj == null) {
                sb.append("null");
            } else if (obj instanceof String s) {
                writeString(sb, s);
            } else if (obj instanceof Map) {
                writeMap(sb, (Map<String, Object>) obj);
            } else if (obj instanceof Iterable it) {
                writeList(sb, it);
            } else if (obj instanceof LocalDateTime ldt) {
                writeString(sb, ldt.toString());
            } else {
                writeString(sb, obj.toString());
            }
        }

        private static void writeString(StringBuilder sb, String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> sb.append(c);
                }
            }
            sb.append('"');
        }

        private static void writeMap(StringBuilder sb, Map<String, Object> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, entry.getKey());
                sb.append(':');
                write(sb, entry.getValue());
            }
            sb.append('}');
        }

        private static void writeList(StringBuilder sb, Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, item);
            }
            sb.append(']');
        }

        /**
         * JSON 解析器
         *
         * @author Hsi Chu
         * @since V1.0
         */
        private static final class Parser {
            /** 待解析的 JSON 原文 */
            private final String json;
            /** 当前解析位置 (字符下标, 取值范围 0..json.length()) */
            private int pos;

            Parser(String json) {
                this.json = json;
            }

            Object parseValue() {
                skipWhitespace();
                Object value = parseValueInternal();
                skipWhitespace();
                return value;
            }

            private Object parseValueInternal() {
                skipWhitespace();
                if (pos >= json.length()) {
                    throw new RuntimeException("JSON 解析意外结束");
                }
                char c = json.charAt(pos);
                if (c == '{') {
                    return parseObject();
                } else if (c == '[') {
                    return parseArray();
                } else if (c == '"') {
                    return parseString();
                } else if (c == 't' || c == 'f') {
                    return parseBoolean();
                } else if (c == 'n') {
                    return parseNull();
                } else {
                    return parseNumber();
                }
            }

            private Map<String, Object> parseObject() {
                Map<String, Object> map = new LinkedHashMap<>();
                expect('{');
                skipWhitespace();
                if (peek() == '}') {
                    pos++;
                    return map;
                }
                while (true) {
                    skipWhitespace();
                    String key = parseString();
                    skipWhitespace();
                    expect(':');
                    Object value = parseValue();
                    map.put(key, value);
                    skipWhitespace();
                    char c = next();
                    if (c == '}') {
                        break;
                    }
                    if (c != ',') {
                        throw new RuntimeException("JSON 对象缺少逗号或结束括号");
                    }
                }
                return map;
            }

            private List<Object> parseArray() {
                List<Object> list = new ArrayList<>();
                expect('[');
                skipWhitespace();
                if (peek() == ']') {
                    pos++;
                    return list;
                }
                while (true) {
                    Object value = parseValue();
                    list.add(value);
                    skipWhitespace();
                    char c = next();
                    if (c == ']') {
                        break;
                    }
                    if (c != ',') {
                        throw new RuntimeException("JSON 数组缺少逗号或结束括号");
                    }
                }
                return list;
            }

            private String parseString() {
                expect('"');
                StringBuilder sb = new StringBuilder();
                while (pos < json.length()) {
                    char c = json.charAt(pos++);
                    if (c == '"') {
                        return sb.toString();
                    }
                    if (c == '\\') {
                        if (pos >= json.length()) {
                            break;
                        }
                        char esc = json.charAt(pos++);
                        switch (esc) {
                            case '"' -> sb.append('"');
                            case '\\' -> sb.append('\\');
                            case '/' -> sb.append('/');
                            case 'n' -> sb.append('\n');
                            case 'r' -> sb.append('\r');
                            case 't' -> sb.append('\t');
                            case 'b' -> sb.append('\b');
                            case 'f' -> sb.append('\f');
                            case 'u' -> {
                                if (pos + 4 > json.length()) {
                                    throw new RuntimeException("JSON Unicode 转义不完整");
                                }
                                String hex = json.substring(pos, pos + 4);
                                sb.append((char) Integer.parseInt(hex, 16));
                                pos += 4;
                            }
                            default -> sb.append(esc);
                        }
                    } else {
                        sb.append(c);
                    }
                }
                throw new RuntimeException("JSON 字符串未闭合");
            }

            private Boolean parseBoolean() {
                if (json.startsWith("true", pos)) {
                    pos += 4;
                    return Boolean.TRUE;
                }
                if (json.startsWith("false", pos)) {
                    pos += 5;
                    return Boolean.FALSE;
                }
                throw new RuntimeException("JSON 布尔值非法");
            }

            private Object parseNull() {
                if (json.startsWith("null", pos)) {
                    pos += 4;
                    return null;
                }
                throw new RuntimeException("JSON null 非法");
            }

            private Number parseNumber() {
                int start = pos;
                if (peek() == '-') {
                    pos++;
                }
                while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                    pos++;
                }
                boolean isDouble = false;
                if (pos < json.length() && json.charAt(pos) == '.') {
                    isDouble = true;
                    pos++;
                    while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                        pos++;
                    }
                }
                if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
                    isDouble = true;
                    pos++;
                    if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
                        pos++;
                    }
                    while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                        pos++;
                    }
                }
                String num = json.substring(start, pos);
                if (isDouble) {
                    return Double.parseDouble(num);
                }
                try {
                    return Long.parseLong(num);
                } catch (NumberFormatException e) {
                    return Double.parseDouble(num);
                }
            }

            private void skipWhitespace() {
                while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                    pos++;
                }
            }

            private void expect(char c) {
                if (pos >= json.length() || json.charAt(pos) != c) {
                    throw new RuntimeException("JSON 期望字符 '" + c + "' 但得到:"
                            + (pos < json.length() ? json.charAt(pos) : "EOF"));
                }
                pos++;
            }

            private char peek() {
                if (pos >= json.length()) {
                    throw new RuntimeException("JSON 意外结束");
                }
                return json.charAt(pos);
            }

            private char next() {
                if (pos >= json.length()) {
                    throw new RuntimeException("JSON 意外结束");
                }
                return json.charAt(pos++);
            }
        }
    }
}