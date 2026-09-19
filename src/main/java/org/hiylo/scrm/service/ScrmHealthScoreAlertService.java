/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthScoreAlertService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmHealthAlertActionDto;
import org.hiylo.scrm.dto.ScrmHealthAlertDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerHealthScoreEntity;
import org.hiylo.scrm.entity.ScrmHealthAlertEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerHealthScoreRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmHealthAlertRepository;
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
 * SCRM 客户健康度告警管理服务。
 * <p>
 * 承载健康度告警管理子域: 告警创建 / 检查并生成 (评分下降 / 低分 / 不活跃 / 流失风险 /
 * 阈值突破等) / 查询 / 确认 / 解决 / 忽略 / 分配 / 批量检查。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmHealthScoreAlertService {

    // ==================== 告警类型常量 ====================

    /** 告警类型: 评分下降 */
    private static final String ALERT_SCORE_DROP = "SCORE_DROP";
    /** 告警类型: 低分 */
    private static final String ALERT_LOW_SCORE = "LOW_SCORE";
    /** 告警类型: 不活跃 */
    private static final String ALERT_INACTIVITY = "INACTIVITY";
    /** 告警类型: 支付问题 */
    private static final String ALERT_PAYMENT_ISSUE = "PAYMENT_ISSUE";
    /** 告警类型: 流失风险 */
    private static final String ALERT_CHURN_RISK = "CHURN_RISK";
    /** 告警类型: 支持超载 */
    private static final String ALERT_SUPPORT_OVERLOAD = "SUPPORT_OVERLOAD";
    /** 告警类型: 风险因素 */
    private static final String ALERT_RISK_FACTOR = "RISK_FACTOR";
    /** 告警类型: 阈值突破 */
    private static final String ALERT_THRESHOLD_BREACH = "THRESHOLD_BREACH";

    // ==================== 告警严重度常量 ====================

    /** 严重度: 信息 */
    private static final String SEVERITY_INFO = "INFO";
    /** 严重度: 警告 */
    private static final String SEVERITY_WARNING = "WARNING";
    /** 严重度: 紧急 */
    private static final String SEVERITY_URGENT = "URGENT";
    /** 严重度: 危急 */
    private static final String SEVERITY_CRITICAL = "CRITICAL";

    // ==================== 告警状态常量 ====================

    /** 状态: 活跃 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态: 已确认 */
    private static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";
    /** 状态: 已解决 */
    private static final String STATUS_RESOLVED = "RESOLVED";
    /** 状态: 已忽略 */
    private static final String STATUS_DISMISSED = "DISMISSED";

    // ==================== 默认值与阈值常量 ====================

    /** 健康等级: 危急 */
    private static final String LEVEL_CRITICAL = "CRITICAL";
    /** 低分告警阈值 */
    private static final double LOW_SCORE_THRESHOLD = 30.0;
    /** 评分下降告警阈值 */
    private static final double SCORE_DROP_THRESHOLD = 10.0;
    /** 快速下降阈值 */
    private static final double RAPID_DECLINE_THRESHOLD = 10.0;
    /** 风险客户分数阈值 */
    private static final double RISK_SCORE_THRESHOLD = 40.0;
    /** 不活跃告警天数阈值 */
    private static final int INACTIVITY_ALERT_DAYS = 14;
    /** 工单过载阈值 */
    private static final int SUPPORT_OVERLOAD_THRESHOLD = 5;

    /** 合法的告警类型 */
    private static final List<String> VALID_ALERT_TYPES = List.of(
            ALERT_SCORE_DROP, ALERT_LOW_SCORE, ALERT_INACTIVITY, ALERT_PAYMENT_ISSUE,
            ALERT_CHURN_RISK, ALERT_SUPPORT_OVERLOAD, ALERT_RISK_FACTOR, ALERT_THRESHOLD_BREACH);

    /** 合法的告警严重度 */
    private static final List<String> VALID_SEVERITIES = List.of(
            SEVERITY_INFO, SEVERITY_WARNING, SEVERITY_URGENT, SEVERITY_CRITICAL);

    /** 合法的告警状态 */
    private static final List<String> VALID_STATUSES = List.of(
            STATUS_ACTIVE, STATUS_ACKNOWLEDGED, STATUS_RESOLVED, STATUS_DISMISSED);

    /** 健康度告警数据访问层 */
    private final ScrmHealthAlertRepository alertRepository;

    /** 健康度评分数据访问层 (检查并生成告警) */
    private final ScrmCustomerHealthScoreRepository scoreRepository;

    /** 客户数据访问层 (告警客户信息) */
    private final ScrmCustomerRepository customerRepository;

    /**
     * 创建告警。
     *
     * @param dto 告警参数
     * @return 创建后的告警
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmHealthAlertEntity createAlert(ScrmHealthAlertDto dto) throws ScrmException {
        validateAlertDto(dto, false);
        ScrmHealthAlertEntity entity = new ScrmHealthAlertEntity();
        entity.setAlertName(dto.getAlertName());
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setHealthScoreId(dto.getHealthScoreId());
        entity.setAlertType(dto.getAlertType());
        entity.setSeverity(dto.getSeverity() != null ? dto.getSeverity() : SEVERITY_WARNING);
        entity.setTriggerValue(dto.getTriggerValue());
        entity.setThresholdValue(dto.getThresholdValue());
        entity.setCondition(dto.getCondition());
        entity.setDescription(dto.getDescription());
        entity.setRiskFactors(dto.getRiskFactors());
        entity.setRecommendedActions(dto.getRecommendedActions());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_ACTIVE);
        entity.setTriggeredAt(dto.getTriggeredAt() != null ? dto.getTriggeredAt() : LocalDateTime.now());
        entity.setMetadata(dto.getMetadata());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = alertRepository.save(entity);
        log.info("创建健康度告警: id={}, alertName={}, alertType={}, severity={}",
                entity.getId(), entity.getAlertName(), entity.getAlertType(), entity.getSeverity());
        return entity;
    }

    /**
     * 检查客户健康度并生成告警 (评分下降 / 低分 / 不活跃 / 流失风险 / 阈值突破等)。
     * <p>基于客户最新评分与上下文生成多条告警, 已存在的同类活跃告警不重复生成。</p>
     *
     * @param customerId 客户 ID
     * @return 生成的告警列表
     * @throws ScrmException 客户不存在
     */
    @Transactional
    public List<ScrmHealthAlertEntity> checkAndGenerateAlerts(Long customerId) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        // 通过 Specification 查找该客户的所有评分
        Specification<ScrmCustomerHealthScoreEntity> spec = (root, query, cb) ->
                cb.equal(root.get("customerId"), customerId);
        List<ScrmCustomerHealthScoreEntity> customerScores = scoreRepository.findAll(spec);
        if (customerScores.isEmpty()) {
            return List.of();
        }
        // 取最近计算的评分
        ScrmCustomerHealthScoreEntity score = customerScores.stream()
                .max((a, b) -> a.getCalculatedAt().compareTo(b.getCalculatedAt()))
                .orElse(null);
        if (score == null) {
            return List.of();
        }
        // 查找客户实体
        ScrmCustomerEntity customer = findCustomerOrThrow(customerId);
        List<ScrmHealthAlertEntity> generated = new ArrayList<>();
        // 1. 评分下降告警
        if (score.getPreviousScore() != null && score.getTotalScore() != null) {
            double drop = score.getPreviousScore() - score.getTotalScore();
            if (drop >= SCORE_DROP_THRESHOLD) {
                generated.add(buildAlert(score, customer, ALERT_SCORE_DROP,
                        drop >= RAPID_DECLINE_THRESHOLD ? SEVERITY_URGENT : SEVERITY_WARNING,
                        score.getTotalScore(), score.getPreviousScore(),
                        "评分下降 " + round2(drop) + " 分",
                        "评分下降过多, 建议关注客户近期互动情况"));
            }
        }
        // 2. 低分告警
        if (score.getTotalScore() != null && score.getTotalScore() < LOW_SCORE_THRESHOLD) {
            generated.add(buildAlert(score, customer, ALERT_LOW_SCORE, SEVERITY_URGENT,
                    score.getTotalScore(), LOW_SCORE_THRESHOLD,
                    "健康度评分低于 " + LOW_SCORE_THRESHOLD,
                    "客户健康度评分过低, 需立即介入"));
        }
        // 3. 不活跃告警
        if (score.getLastInteractionDays() != null && score.getLastInteractionDays() >= INACTIVITY_ALERT_DAYS) {
            generated.add(buildAlert(score, customer, ALERT_INACTIVITY, SEVERITY_WARNING,
                    (double) score.getLastInteractionDays(), (double) INACTIVITY_ALERT_DAYS,
                    "距上次互动 " + score.getLastInteractionDays() + " 天",
                    "客户长期未互动, 建议主动联系"));
        }
        // 4. 流失风险告警
        if (Boolean.TRUE.equals(score.getIsChurnRisk())) {
            generated.add(buildAlert(score, customer, ALERT_CHURN_RISK, SEVERITY_CRITICAL,
                    score.getTotalScore(), RISK_SCORE_THRESHOLD,
                    "客户存在流失风险",
                    "客户有流失风险, 建议高层介入制定挽留方案"));
        }
        // 5. 支持超载告警
        if (score.getOpenTickets() != null && score.getOpenTickets() >= SUPPORT_OVERLOAD_THRESHOLD) {
            generated.add(buildAlert(score, customer, ALERT_SUPPORT_OVERLOAD, SEVERITY_WARNING,
                    (double) score.getOpenTickets(), (double) SUPPORT_OVERLOAD_THRESHOLD,
                    "待处理工单 " + score.getOpenTickets() + " 个",
                    "客户待处理工单过多, 建议优先处理"));
        }
        // 6. 阈值突破告警 (健康等级为 CRITICAL)
        if (LEVEL_CRITICAL.equals(score.getHealthLevel())) {
            generated.add(buildAlert(score, customer, ALERT_THRESHOLD_BREACH, SEVERITY_CRITICAL,
                    score.getTotalScore(), RISK_SCORE_THRESHOLD,
                    "健康等级为 CRITICAL",
                    "客户健康度突破危急阈值, 需立即处理"));
        }
        // 7. 风险因素告警
        if (score.getRiskFactors() != null && !score.getRiskFactors().isBlank()) {
            generated.add(buildAlert(score, customer, ALERT_RISK_FACTOR, SEVERITY_WARNING,
                    score.getTotalScore(), RISK_SCORE_THRESHOLD,
                    "风险因素: " + score.getRiskFactors(),
                    "客户存在多个风险因素, 建议综合评估"));
        }
        // 持久化并去重 (同客户同类型 ACTIVE 状态的告警不重复生成)
        List<ScrmHealthAlertEntity> saved = new ArrayList<>();
        for (ScrmHealthAlertEntity alert : generated) {
            if (!hasActiveAlert(customerId, alert.getAlertType())) {
                saved.add(alertRepository.save(alert));
            }
        }
        log.info("检查并生成告警: customerId={}, generated={}, saved={}",
                customerId, generated.size(), saved.size());
        return saved;
    }

    /**
     * 查询告警详情。
     *
     * @param id 告警 ID
     * @return 告警实体
     * @throws ScrmException 告警不存在
     */
    @Transactional(readOnly = true)
    public ScrmHealthAlertEntity getAlert(Long id) throws ScrmException {
        return findAlertOrThrow(id);
    }

    /**
     * 分页查询告警, 支持按客户 / 告警类型 / 严重度 / 状态 / 时间区间过滤。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param alertType  告警类型过滤（可空）
     * @param severity   严重度过滤（可空）
     * @param status     状态过滤（可空）
     * @param startTime  触发起始时间（可空）
     * @param endTime    触发截止时间（可空）
     * @param pageable   分页参数
     * @return 告警分页结果 (按 triggeredAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmHealthAlertEntity> listAlerts(Long customerId, String alertType, String severity, String status,
                                                  LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmHealthAlertEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (alertType != null && !alertType.isBlank()) {
                predicates.add(cb.equal(root.get("alertType"), alertType));
            }
            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("triggeredAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("triggeredAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return alertRepository.findAll(spec, ensureSort(pageable, "triggeredAt"));
    }

    /**
     * 活跃告警列表 (status = ACTIVE, 按 triggeredAt DESC)。
     *
     * @param limit 返回数量
     * @return 告警列表
     */
    @Transactional(readOnly = true)
    public List<ScrmHealthAlertEntity> getActiveAlerts(int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        return alertRepository
                .findByStatusOrderByTriggeredAtDesc(STATUS_ACTIVE, PageRequest.of(0, limit))
                .getContent();
    }

    /**
     * 确认告警 (ACTIVE → ACKNOWLEDGED)。
     *
     * @param actionDto 动作参数 (alertId + note + assigneeId)
     * @return 更新后的告警
     * @throws ScrmException 告警不存在 / 状态非法
     */
    @Transactional
    public ScrmHealthAlertEntity acknowledgeAlert(ScrmHealthAlertActionDto actionDto) throws ScrmException {
        ScrmHealthAlertEntity alert = findAlertOrThrow(actionDto.getAlertId());
        if (!STATUS_ACTIVE.equals(alert.getStatus())) {
            throw ScrmException.conflict("告警状态非 ACTIVE, 无法确认: id=" + alert.getId());
        }
        alert.setStatus(STATUS_ACKNOWLEDGED);
        alert.setAcknowledgedBy(actionDto.getAssigneeId());
        alert.setAcknowledgedAt(LocalDateTime.now());
        if (actionDto.getNote() != null) {
            alert.setResolutionNote(actionDto.getNote());
        }
        if (actionDto.getAssigneeId() != null) {
            alert.setAssignedTo(actionDto.getAssigneeId());
            alert.setAssignedAt(LocalDateTime.now());
        }
        alert = alertRepository.save(alert);
        log.info("确认告警: id={}, acknowledgedBy={}", alert.getId(), actionDto.getAssigneeId());
        return alert;
    }

    /**
     * 解决告警 (ACKNOWLEDGED → RESOLVED)。
     *
     * @param actionDto 动作参数 (alertId + note + assigneeId)
     * @return 更新后的告警
     * @throws ScrmException 告警不存在 / 状态非法
     */
    @Transactional
    public ScrmHealthAlertEntity resolveAlert(ScrmHealthAlertActionDto actionDto) throws ScrmException {
        ScrmHealthAlertEntity alert = findAlertOrThrow(actionDto.getAlertId());
        if (STATUS_RESOLVED.equals(alert.getStatus()) || STATUS_DISMISSED.equals(alert.getStatus())) {
            throw ScrmException.conflict("告警已结束, 无法再次解决: id=" + alert.getId());
        }
        alert.setStatus(STATUS_RESOLVED);
        alert.setResolvedBy(actionDto.getAssigneeId());
        alert.setResolvedAt(LocalDateTime.now());
        if (actionDto.getNote() != null) {
            alert.setResolutionNote(actionDto.getNote());
        }
        alert = alertRepository.save(alert);
        log.info("解决告警: id={}, resolvedBy={}", alert.getId(), actionDto.getAssigneeId());
        return alert;
    }

    /**
     * 忽略告警 (任意状态 → DISMISSED)。
     *
     * @param actionDto 动作参数 (alertId + note + assigneeId)
     * @return 更新后的告警
     * @throws ScrmException 告警不存在
     */
    @Transactional
    public ScrmHealthAlertEntity dismissAlert(ScrmHealthAlertActionDto actionDto) throws ScrmException {
        ScrmHealthAlertEntity alert = findAlertOrThrow(actionDto.getAlertId());
        if (STATUS_DISMISSED.equals(alert.getStatus())) {
            throw ScrmException.conflict("告警已忽略, 无需重复操作: id=" + alert.getId());
        }
        alert.setStatus(STATUS_DISMISSED);
        alert.setResolvedBy(actionDto.getAssigneeId());
        alert.setResolvedAt(LocalDateTime.now());
        if (actionDto.getNote() != null) {
            alert.setResolutionNote(actionDto.getNote());
        }
        alert = alertRepository.save(alert);
        log.info("忽略告警: id={}, dismissedBy={}", alert.getId(), actionDto.getAssigneeId());
        return alert;
    }

    /**
     * 分配告警给负责人。
     *
     * @param alertId    告警 ID
     * @param assigneeId 负责人用户标识
     * @return 更新后的告警
     * @throws ScrmException 告警不存在
     */
    @Transactional
    public ScrmHealthAlertEntity assignAlert(Long alertId, String assigneeId) throws ScrmException {
        if (assigneeId == null || assigneeId.isBlank()) {
            throw ScrmException.badRequest("负责人不能为空");
        }
        ScrmHealthAlertEntity alert = findAlertOrThrow(alertId);
        alert.setAssignedTo(assigneeId);
        alert.setAssignedAt(LocalDateTime.now());
        alert = alertRepository.save(alert);
        log.info("分配告警: id={}, assignee={}", alertId, assigneeId);
        return alert;
    }

    /**
     * 批量检查所有风险客户的告警 (遍历账号下所有风险客户, 逐一检查并生成告警)。
     *
     * @return 检查结果: {checked, generated}
     */
    @Transactional
    public Map<String, Integer> batchCheckAlerts() {
        // 查找所有风险客户 (取去重 customerId)
        Specification<ScrmCustomerHealthScoreEntity> spec = (root, query, cb) ->
                cb.equal(root.get("isAtRisk"), true);
        List<ScrmCustomerHealthScoreEntity> atRiskScores = scoreRepository.findAll(spec);
        // 去重 customerId
        List<Long> customerIds = atRiskScores.stream()
                .map(ScrmCustomerHealthScoreEntity::getCustomerId)
                .distinct()
                .collect(Collectors.toList());
        int checked = 0;
        int generated = 0;
        for (Long customerId : customerIds) {
            try {
                List<ScrmHealthAlertEntity> alerts = checkAndGenerateAlerts(customerId);
                checked++;
                generated += alerts.size();
            } catch (Exception e) {
                log.warn("批量检查告警失败, 跳过: customerId={}, err={}", customerId, e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("checked", checked);
        result.put("generated", generated);
        log.info("批量检查告警完成:, checked={}, generated={}", checked, generated);
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验告警参数。
     *
     * @param dto     告警参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateAlertDto(ScrmHealthAlertDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("告警参数不能为空");
        }
        if (dto.getAlertName() != null) {
            if (dto.getAlertName().isBlank()) {
                throw ScrmException.badRequest("告警名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("告警名称不能为空");
        }
        if (dto.getCustomerId() == null && !partial) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getAlertType() != null && !VALID_ALERT_TYPES.contains(dto.getAlertType())) {
            throw ScrmException.badRequest(
                    "告警类型非法: " + dto.getAlertType() + ", 仅支持 " + VALID_ALERT_TYPES);
        }
        if (dto.getSeverity() != null && !VALID_SEVERITIES.contains(dto.getSeverity())) {
            throw ScrmException.badRequest(
                    "严重度非法: " + dto.getSeverity() + ", 仅支持 " + VALID_SEVERITIES);
        }
        if (dto.getStatus() != null && !VALID_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest(
                    "状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_STATUSES);
        }
    }

    /**
     * 构建告警实体 (辅助方法, 用于 checkAndGenerateAlerts)。
     *
     * @param score          健康度评分
     * @param customer       客户实体
     * @param alertType      告警类型
     * @param severity       严重度
     * @param triggerValue   触发值
     * @param thresholdValue 阈值
     * @param condition      触发条件
     * @param description    告警描述
     * @return 告警实体 (未持久化)
     */
    private ScrmHealthAlertEntity buildAlert(ScrmCustomerHealthScoreEntity score, ScrmCustomerEntity customer,
                                            String alertType, String severity, double triggerValue,
                                            double thresholdValue, String condition, String description) {
        ScrmHealthAlertEntity alert = new ScrmHealthAlertEntity();
        alert.setAlertName(alertType + " - " + customer.getNickname());
        alert.setCustomerId(score.getCustomerId());
        alert.setCustomerName(customer.getNickname());
        alert.setHealthScoreId(score.getId());
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setTriggerValue(round2(triggerValue));
        alert.setThresholdValue(round2(thresholdValue));
        alert.setCondition(condition);
        alert.setDescription(description);
        alert.setRiskFactors(score.getRiskFactors());
        alert.setRecommendedActions(score.getRecommendedActions());
        alert.setStatus(STATUS_ACTIVE);
        alert.setTriggeredAt(LocalDateTime.now());
        return alert;
    }

    /**
     * 检查是否已存在同客户同类型的活跃告警。
     *
     * @param customerId 客户 ID
     * @param alertType  告警类型
     * @return 是否已存在
     */
    private boolean hasActiveAlert(Long customerId, String alertType) {
        Specification<ScrmHealthAlertEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("customerId"), customerId),
                cb.equal(root.get("alertType"), alertType),
                cb.equal(root.get("status"), STATUS_ACTIVE));
        return alertRepository.count(spec) > 0;
    }

    /**
     * 保留两位小数。
     *
     * @param value 原始值
     * @return 保留两位小数后的值
     */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 确保分页参数带默认排序 (按指定字段倒序)。
     *
     * @param pageable 分页参数
     * @param field    默认排序字段
     * @return 处理后的分页参数
     */
    private Pageable ensureSort(Pageable pageable, String field) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, field));
    }

    /**
     * 按主键查询告警, 不存在抛异常, 并校验账号归属。
     *
     * @param id 告警 ID
     * @return 告警实体
     * @throws ScrmException 告警不存在
     */
    private ScrmHealthAlertEntity findAlertOrThrow(Long id) throws ScrmException {
        ScrmHealthAlertEntity entity = alertRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "健康度告警不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询客户, 不存在抛异常, 并校验账号归属。
     *
     * @param id 客户 ID
     * @return 客户实体
     * @throws ScrmException 客户不存在
     */
    private ScrmCustomerEntity findCustomerOrThrow(Long id) throws ScrmException {
        ScrmCustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + id));
        return customer;
    }
}