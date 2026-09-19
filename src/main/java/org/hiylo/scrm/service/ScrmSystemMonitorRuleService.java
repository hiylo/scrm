/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemMonitorRuleService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAlertRuleDto;
import org.hiylo.scrm.entity.ScrmAlertEventEntity;
import org.hiylo.scrm.entity.ScrmAlertRuleEntity;
import org.hiylo.scrm.entity.ScrmMonitorMetricEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAlertRuleRepository;
import org.hiylo.scrm.repository.ScrmMonitorMetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 系统监控告警规则服务。
 * <p>
 * 承载告警规则管理子域: 规则 CRUD / 启停 / 分页与按指标、严重程度查询 / 条件检查 /
 * 单条与批量评估 (评估通过委托 {@link ScrmSystemMonitorEventService} 触发告警事件) /
 * 触发统计更新。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSystemMonitorRuleService {

    /** 默认连续触发次数 */
    private static final int DEFAULT_EVALUATION_PERIODS = 1;
    /** 默认冷却分钟 */
    private static final int DEFAULT_COOLDOWN_MINUTES = 30;
    /** 默认升级时间分钟 */
    private static final int DEFAULT_ESCALATION_AFTER_MINUTES = 60;

    /** 严重程度: 警告 */
    private static final String SEVERITY_WARNING = "WARNING";

    /** 合法的条件操作符 */
    private static final List<String> VALID_CONDITIONS = List.of(
            "GT", "GTE", "LT", "LTE", "EQ", "NE", "CONTAINS", "NOT_CONTAINS");
    /** 合法的严重程度 */
    private static final List<String> VALID_SEVERITIES = List.of(
            "INFO", "WARNING", "CRITICAL", "FATAL");
    /** 合法的通知渠道 */
    private static final List<String> VALID_CHANNELS = List.of(
            "EMAIL", "SMS", "WECHAT", "WEBHOOK", "APP_PUSH", "PHONE");

    /** 告警规则数据访问层 */
    private final ScrmAlertRuleRepository ruleRepository;
    /** 监控指标数据访问层 (规则关联指标的查找与校验) */
    private final ScrmMonitorMetricRepository metricRepository;

    /** 告警事件子域服务 (批量评估触发事件, lazy 避免与事件子域循环依赖) */
    @Autowired
    @Lazy
    private ScrmSystemMonitorEventService eventService;

    /**
     * 创建告警规则。
     * <p>校验 condition / severity / notificationChannels 合法性与 ruleCode 唯一性,
     * 关联指标必须存在, 写入归属账号 ID 持久化。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 编码重复 / 指标不存在
     */
    @Transactional
    public ScrmAlertRuleEntity createRule(ScrmAlertRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        if (ruleRepository.findByRuleCode(dto.getRuleCode()).isPresent()) {
            throw ScrmException.conflict("规则编码已存在: " + dto.getRuleCode());
        }
        ScrmMonitorMetricEntity metric = findMetricOrThrow(dto.getMetricId());
        ScrmAlertRuleEntity entity = new ScrmAlertRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setRuleCode(dto.getRuleCode());
        entity.setDescription(dto.getDescription());
        entity.setMetricId(dto.getMetricId());
        entity.setMetricName(metric.getMetricName());
        entity.setMetricCode(metric.getMetricCode());
        entity.setCondition(dto.getCondition());
        entity.setThresholdValue(dto.getThresholdValue());
        entity.setThresholdValue2(dto.getThresholdValue2() != null ? dto.getThresholdValue2() : 0.0);
        entity.setSeverity(dto.getSeverity() != null ? dto.getSeverity() : SEVERITY_WARNING);
        entity.setDurationSeconds(dto.getDurationSeconds() != null ? dto.getDurationSeconds() : 0);
        entity.setEvaluationPeriods(dto.getEvaluationPeriods() != null
                ? dto.getEvaluationPeriods() : DEFAULT_EVALUATION_PERIODS);
        entity.setCooldownMinutes(dto.getCooldownMinutes() != null
                ? dto.getCooldownMinutes() : DEFAULT_COOLDOWN_MINUTES);
        entity.setNotificationChannels(dto.getNotificationChannels());
        entity.setNotificationTemplateId(dto.getNotificationTemplateId());
        entity.setRecipients(dto.getRecipients());
        entity.setEscalationRecipients(dto.getEscalationRecipients());
        entity.setEscalationAfterMinutes(dto.getEscalationAfterMinutes() != null
                ? dto.getEscalationAfterMinutes() : DEFAULT_ESCALATION_AFTER_MINUTES);
        entity.setAutoResolve(dto.getAutoResolve() != null ? dto.getAutoResolve() : Boolean.TRUE);
        entity.setAutoResolveMessage(dto.getAutoResolveMessage());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setTriggerCount(0);
        entity.setTags(dto.getTags());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("创建告警规则: id={}, ruleName={}, ruleCode={}, metricId={}",
                entity.getId(), entity.getRuleName(), entity.getRuleCode(), entity.getMetricId());
        return entity;
    }

    /**
     * 更新告警规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 编码重复 / 指标不存在
     */
    @Transactional
    public ScrmAlertRuleEntity updateRule(Long id, ScrmAlertRuleDto dto) throws ScrmException {
        ScrmAlertRuleEntity entity = findRuleOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getRuleCode() != null && !dto.getRuleCode().equals(entity.getRuleCode())) {
            if (ruleRepository.findByRuleCode(dto.getRuleCode()).isPresent()) {
                throw ScrmException.conflict("规则编码已存在: " + dto.getRuleCode());
            }
        }
        if (dto.getMetricId() != null && !dto.getMetricId().equals(entity.getMetricId())) {
            ScrmMonitorMetricEntity metric = findMetricOrThrow(dto.getMetricId());
            entity.setMetricId(dto.getMetricId());
            entity.setMetricName(metric.getMetricName());
            entity.setMetricCode(metric.getMetricCode());
        }
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getRuleCode() != null) entity.setRuleCode(dto.getRuleCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getCondition() != null) entity.setCondition(dto.getCondition());
        if (dto.getThresholdValue() != null) entity.setThresholdValue(dto.getThresholdValue());
        if (dto.getThresholdValue2() != null) entity.setThresholdValue2(dto.getThresholdValue2());
        if (dto.getSeverity() != null) entity.setSeverity(dto.getSeverity());
        if (dto.getDurationSeconds() != null) entity.setDurationSeconds(dto.getDurationSeconds());
        if (dto.getEvaluationPeriods() != null) entity.setEvaluationPeriods(dto.getEvaluationPeriods());
        if (dto.getCooldownMinutes() != null) entity.setCooldownMinutes(dto.getCooldownMinutes());
        if (dto.getNotificationChannels() != null) entity.setNotificationChannels(dto.getNotificationChannels());
        if (dto.getNotificationTemplateId() != null) entity.setNotificationTemplateId(dto.getNotificationTemplateId());
        if (dto.getRecipients() != null) entity.setRecipients(dto.getRecipients());
        if (dto.getEscalationRecipients() != null) entity.setEscalationRecipients(dto.getEscalationRecipients());
        if (dto.getEscalationAfterMinutes() != null) entity.setEscalationAfterMinutes(dto.getEscalationAfterMinutes());
        if (dto.getAutoResolve() != null) entity.setAutoResolve(dto.getAutoResolve());
        if (dto.getAutoResolveMessage() != null) entity.setAutoResolveMessage(dto.getAutoResolveMessage());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新告警规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 删除告警规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmAlertRuleEntity entity = findRuleOrThrow(id);
        ruleRepository.delete(entity);
        log.info("删除告警规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmAlertRuleEntity getRule(Long id) throws ScrmException {
        return findRuleOrThrow(id);
    }

    /**
     * 按编码查询规则。
     *
     * @param code 规则编码
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmAlertRuleEntity getRuleByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("规则编码不能为空");
        }
        return ruleRepository.findByRuleCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "告警规则不存在: code=" + code));
    }

    /**
     * 分页查询规则, 支持按指标 / 严重程度 / 启用状态 / 关键字过滤。
     *
     * @param metricId 指标 ID 过滤（可空）
     * @param severity 严重程度过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  规则名称关键字模糊匹配（可空）
     * @param pageable 分页参数
     * @return 规则分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAlertRuleEntity> listRules(Long metricId, String severity, Boolean enabled,
                                                String keyword, Pageable pageable) {
        Specification<ScrmAlertRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (metricId != null) {
                predicates.add(cb.equal(root.get("metricId"), metricId));
            }
            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("ruleName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return ruleRepository.findAll(spec, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmAlertRuleEntity enableRule(Long id) throws ScrmException {
        ScrmAlertRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = ruleRepository.save(entity);
        log.info("启用告警规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmAlertRuleEntity disableRule(Long id) throws ScrmException {
        ScrmAlertRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = ruleRepository.save(entity);
        log.info("禁用告警规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    /**
     * 评估规则: 获取指标当前值 → 比较阈值 → 返回是否触发。
     *
     * @param ruleId 规则 ID
     * @return 评估结果 Map {triggered, currentValue, thresholdValue, condition, severity, message}
     * @throws ScrmException 规则不存在 / 已禁用
     */
    @Transactional
    public Map<String, Object> evaluateRule(Long ruleId) throws ScrmException {
        ScrmAlertRuleEntity rule = findRuleOrThrow(ruleId);
        if (Boolean.FALSE.equals(rule.getEnabled())) {
            throw ScrmException.badRequest("规则已禁用, 无法评估: ruleId=" + ruleId);
        }
        ScrmMonitorMetricEntity metric = findMetricOrThrow(rule.getMetricId());
        double currentValue = metric.getCurrentValue() != null ? metric.getCurrentValue() : 0.0;
        double threshold1 = rule.getThresholdValue() != null ? rule.getThresholdValue() : 0.0;
        double threshold2 = rule.getThresholdValue2() != null ? rule.getThresholdValue2() : 0.0;
        boolean triggered = checkCondition(currentValue, rule.getCondition(), threshold1, threshold2);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", rule.getId());
        result.put("ruleName", rule.getRuleName());
        result.put("metricCode", metric.getMetricCode());
        result.put("currentValue", currentValue);
        result.put("thresholdValue", threshold1);
        result.put("thresholdValue2", threshold2);
        result.put("condition", rule.getCondition());
        result.put("severity", rule.getSeverity());
        result.put("triggered", triggered);
        result.put("message", triggered
                ? "指标 " + metric.getMetricName() + " 当前值 " + currentValue + " 触发规则 " + rule.getRuleName()
                : "指标 " + metric.getMetricName() + " 当前值 " + currentValue + " 未触发规则");
        return result;
    }

    /**
     * 评估所有启用规则: 批量检查 → 触发告警 (冷却期内不重复触发)。
     *
     * @return 评估结果 Map {totalRules, evaluated, triggered, results}
     */
    @Transactional
    public Map<String, Object> evaluateAllRules() {
        List<ScrmAlertRuleEntity> rules = ruleRepository.findByEnabled(Boolean.TRUE);
        List<Map<String, Object>> results = new ArrayList<>();
        int triggered = 0;
        for (ScrmAlertRuleEntity rule : rules) {
            try {
                Map<String, Object> eval = evaluateRule(rule.getId());
                boolean isTriggered = Boolean.TRUE.equals(eval.get("triggered"));
                if (isTriggered) {
                    // 冷却期检查
                    if (checkCooldown(rule.getId())) {
                        // 触发告警事件
                        ScrmMonitorMetricEntity metric = findMetricOrThrow(rule.getMetricId());
                        ScrmAlertEventEntity event = eventService.fireEvent(rule.getId(), rule.getMetricId(),
                                metric.getCurrentValue() != null ? metric.getCurrentValue() : 0.0);
                        eval.put("eventId", event.getId());
                        eval.put("eventNo", event.getEventNo());
                        triggered++;
                    } else {
                        eval.put("message", "规则触发但处于冷却期内, 跳过");
                    }
                }
                results.add(eval);
            } catch (ScrmException e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("ruleId", rule.getId());
                err.put("ruleName", rule.getRuleName());
                err.put("triggered", false);
                err.put("error", e.getMessage());
                results.add(err);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRules", rules.size());
        summary.put("evaluated", results.size());
        summary.put("triggered", triggered);
        summary.put("results", results);
        log.info("评估所有规则: total={}, triggered={}", rules.size(), triggered);
        return summary;
    }

    /**
     * 条件检查: 比较当前值与阈值是否满足触发条件。
     *
     * @param currentValue 当前值
     * @param condition    条件操作符: GT/GTE/LT/LTE/EQ/NE/CONTAINS/NOT_CONTAINS
     * @param threshold1   阈值 1
     * @param threshold2   阈值 2 (范围用)
     * @return 是否触发
     */
    public boolean checkCondition(double currentValue, String condition, double threshold1, double threshold2) {
        if (condition == null) {
            return false;
        }
        switch (condition) {
            case "GT":
                return currentValue > threshold1;
            case "GTE":
                return currentValue >= threshold1;
            case "LT":
                return currentValue < threshold1;
            case "LTE":
                return currentValue <= threshold1;
            case "EQ":
                return Double.compare(currentValue, threshold1) == 0;
            case "NE":
                return Double.compare(currentValue, threshold1) != 0;
            case "CONTAINS":
                // 数值范围包含: threshold2 ≤ currentValue ≤ threshold1
                return currentValue >= Math.min(threshold1, threshold2) && currentValue <= Math.max(threshold1, threshold2);
            case "NOT_CONTAINS":
                // 数值范围不包含: currentValue < min(threshold1,threshold2) 或 > max
                return currentValue < Math.min(threshold1, threshold2)
                        || currentValue > Math.max(threshold1, threshold2);
            default:
                return false;
        }
    }

    /**
     * 按指标查询规则。
     *
     * @param metricId 指标 ID
     * @return 规则列表
     */
    @Transactional(readOnly = true)
    public List<ScrmAlertRuleEntity> getRulesByMetric(Long metricId) {
        return ruleRepository.findByMetricId(metricId);
    }

    /**
     * 按严重程度分页查询规则。
     *
     * @param severity 严重程度
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAlertRuleEntity> getRulesBySeverity(String severity, Pageable pageable) {
        if (severity == null || severity.isBlank()) {
            throw ScrmException.badRequest("严重程度不能为空");
        }
        if (!VALID_SEVERITIES.contains(severity)) {
            throw ScrmException.badRequest("严重程度非法: " + severity + ", 仅支持 " + VALID_SEVERITIES);
        }
        return ruleRepository.findBySeverity(severity, pageable);
    }

    /**
     * 更新规则触发统计 (triggerCount + lastTriggeredAt)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmAlertRuleEntity updateRuleStats(Long id) throws ScrmException {
        ScrmAlertRuleEntity entity = findRuleOrThrow(id);
        entity.setTriggerCount((entity.getTriggerCount() != null ? entity.getTriggerCount() : 0) + 1);
        entity.setLastTriggeredAt(LocalDateTime.now());
        entity = ruleRepository.save(entity);
        return entity;
    }

    /**
     * 检查冷却期: 规则最近一次触发是否在冷却期内。
     *
     * @param ruleId 规则 ID
     * @return true 表示可触发 (不在冷却期), false 表示在冷却期内
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public boolean checkCooldown(Long ruleId) throws ScrmException {
        ScrmAlertRuleEntity rule = findRuleOrThrow(ruleId);
        if (rule.getLastTriggeredAt() == null) {
            return true;
        }
        int cooldownMinutes = rule.getCooldownMinutes() != null ? rule.getCooldownMinutes() : DEFAULT_COOLDOWN_MINUTES;
        LocalDateTime cooldownEnd = rule.getLastTriggeredAt().plusMinutes(cooldownMinutes);
        return LocalDateTime.now().isAfter(cooldownEnd);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验规则参数。
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmAlertRuleDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        if (dto.getRuleName() != null) {
            if (dto.getRuleName().isBlank()) {
                throw ScrmException.badRequest("规则名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则名称不能为空");
        }
        if (dto.getRuleCode() != null) {
            if (dto.getRuleCode().isBlank()) {
                throw ScrmException.badRequest("规则编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则编码不能为空");
        }
        if (dto.getMetricId() == null && !partial) {
            throw ScrmException.badRequest("关联指标 ID 不能为空");
        }
        if (dto.getCondition() != null && !VALID_CONDITIONS.contains(dto.getCondition())) {
            throw ScrmException.badRequest("条件操作符非法: " + dto.getCondition() + ", 仅支持 " + VALID_CONDITIONS);
        } else if (dto.getCondition() == null && !partial) {
            throw ScrmException.badRequest("条件操作符不能为空");
        }
        if (dto.getThresholdValue() == null && !partial) {
            throw ScrmException.badRequest("阈值不能为空");
        }
        if (dto.getSeverity() != null && !VALID_SEVERITIES.contains(dto.getSeverity())) {
            throw ScrmException.badRequest("严重程度非法: " + dto.getSeverity() + ", 仅支持 " + VALID_SEVERITIES);
        }
        if (dto.getNotificationChannels() != null) {
            if (dto.getNotificationChannels().isBlank()) {
                throw ScrmException.badRequest("通知渠道不能为空");
            }
            List<String> channels = parseCsv(dto.getNotificationChannels());
            for (String ch : channels) {
                if (!VALID_CHANNELS.contains(ch)) {
                    throw ScrmException.badRequest("通知渠道非法: " + ch + ", 仅支持 " + VALID_CHANNELS);
                }
            }
        } else if (!partial) {
            throw ScrmException.badRequest("通知渠道不能为空");
        }
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
}