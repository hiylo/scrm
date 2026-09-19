/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderSlaService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmWorkOrderDto;
import org.hiylo.scrm.dto.ScrmWorkOrderSlaDto;
import org.hiylo.scrm.entity.ScrmWorkOrderEntity;
import org.hiylo.scrm.entity.ScrmWorkOrderSlaEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmWorkOrderRepository;
import org.hiylo.scrm.repository.ScrmWorkOrderSlaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 工单 SLA 服务。
 * <p>
 * 承载 SLA 子域: SLA 策略增删改查与启停/默认、应用策略到工单、违规扫描 / 预警 / 升级、
 * 达标率与统计、按 SLA 状态查询工单、SLA 截止计算。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmWorkOrderSlaService {

    /** 工单数据访问层 */
    private final ScrmWorkOrderRepository orderRepository;
    /** SLA 策略数据访问层 */
    private final ScrmWorkOrderSlaRepository slaRepository;
    /** 工单订单管理服务 (共享工具) */
    private final ScrmWorkOrderCrudService crudService;
    /** 工单动作服务 (SLA 违规自动升级) */
    private final ScrmWorkOrderActionService actionService;
    /** 工单日志服务 (日志写入口) */
    private final ScrmWorkOrderLogService logService;

    // ============================================================
    // SLA 策略管理
    // ============================================================

    /**
     * 创建 SLA 策略。
     *
     * @param dto SLA 策略参数
     * @return 创建后的 SLA 策略
     * @throws ScrmException 参数非法 / 名称或编码重复
     */
    @Transactional
    public ScrmWorkOrderSlaDto createSlaPolicy(ScrmWorkOrderSlaDto dto) throws ScrmException {
        validateSlaDto(dto, false);
        // 名称与编码唯一性校验
        if (slaRepository.findByPolicyName(dto.getPolicyName()).isPresent()) {
            throw ScrmException.conflict("SLA 策略名称已存在: " + dto.getPolicyName());
        }
        if (slaRepository.findByPolicyCode(dto.getPolicyCode()).isPresent()) {
            throw ScrmException.conflict("SLA 策略编码已存在: " + dto.getPolicyCode());
        }
        ScrmWorkOrderSlaEntity entity = toSlaEntity(dto);
        // 默认值
        applySlaDefaults(entity);
        // 若设为默认, 清除其他默认
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearOtherDefaults(null);
        }
        entity = slaRepository.save(entity);
        log.info("创建 SLA 策略: id={}, code={}", entity.getId(), entity.getPolicyCode());
        return toSlaDto(entity);
    }

    /**
     * 更新 SLA 策略 (字段非空才覆盖)。
     *
     * @param id  SLA 策略 ID
     * @param dto SLA 策略参数
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在 / 参数非法
     */
    @Transactional
    public ScrmWorkOrderSlaDto updateSlaPolicy(Long id, ScrmWorkOrderSlaDto dto) throws ScrmException {
        ScrmWorkOrderSlaEntity entity = findSlaOrThrow(id);
        validateSlaDto(dto, true);
        if (dto.getPolicyName() != null && !dto.getPolicyName().equals(entity.getPolicyName())) {
            if (slaRepository.findByPolicyName(dto.getPolicyName()).isPresent()) {
                throw ScrmException.conflict("SLA 策略名称已存在: " + dto.getPolicyName());
            }
            entity.setPolicyName(dto.getPolicyName());
        }
        if (dto.getPolicyCode() != null && !dto.getPolicyCode().equals(entity.getPolicyCode())) {
            if (slaRepository.findByPolicyCode(dto.getPolicyCode()).isPresent()) {
                throw ScrmException.conflict("SLA 策略编码已存在: " + dto.getPolicyCode());
            }
            entity.setPolicyCode(dto.getPolicyCode());
        }
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getOrderType() != null) entity.setOrderType(dto.getOrderType());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getCustomerSegment() != null) entity.setCustomerSegment(dto.getCustomerSegment());
        if (dto.getResponseTimeMinutes() != null) entity.setResponseTimeMinutes(dto.getResponseTimeMinutes());
        if (dto.getResolutionTimeMinutes() != null) entity.setResolutionTimeMinutes(dto.getResolutionTimeMinutes());
        if (dto.getResponseTimeHours() != null) entity.setResponseTimeHours(dto.getResponseTimeHours());
        if (dto.getResolutionTimeHours() != null) entity.setResolutionTimeHours(dto.getResolutionTimeHours());
        if (dto.getBusinessHoursOnly() != null) entity.setBusinessHoursOnly(dto.getBusinessHoursOnly());
        if (dto.getBusinessHoursStart() != null) entity.setBusinessHoursStart(dto.getBusinessHoursStart());
        if (dto.getBusinessHoursEnd() != null) entity.setBusinessHoursEnd(dto.getBusinessHoursEnd());
        if (dto.getBusinessDays() != null) entity.setBusinessDays(dto.getBusinessDays());
        if (dto.getTimezone() != null) entity.setTimezone(dto.getTimezone());
        if (dto.getEscalationEnabled() != null) entity.setEscalationEnabled(dto.getEscalationEnabled());
        if (dto.getEscalationLevels() != null) entity.setEscalationLevels(dto.getEscalationLevels());
        if (dto.getFirstResponseBreachAction() != null) entity.setFirstResponseBreachAction(
                dto.getFirstResponseBreachAction());
        if (dto.getResolutionBreachAction() != null) entity.setResolutionBreachAction(dto.getResolutionBreachAction());
        if (dto.getWarningBeforeBreach() != null) entity.setWarningBeforeBreach(dto.getWarningBeforeBreach());
        if (dto.getAutoCloseAfterResolution() != null) entity.setAutoCloseAfterResolution(
                dto.getAutoCloseAfterResolution());
        if (dto.getReopenAllowed() != null) entity.setReopenAllowed(dto.getReopenAllowed());
        if (dto.getReopenWithinHours() != null) entity.setReopenWithinHours(dto.getReopenWithinHours());
        if (dto.getPenaltyPerBreach() != null) entity.setPenaltyPerBreach(dto.getPenaltyPerBreach());
        if (dto.getCreditPerMet() != null) entity.setCreditPerMet(dto.getCreditPerMet());
        if (dto.getTargetComplianceRate() != null) entity.setTargetComplianceRate(dto.getTargetComplianceRate());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = slaRepository.save(entity);
        log.info("更新 SLA 策略: id={}", id);
        return toSlaDto(entity);
    }

    /**
     * 删除 SLA 策略。
     *
     * @param id SLA 策略 ID
     * @throws ScrmException 策略不存在
     */
    @Transactional
    public void deleteSlaPolicy(Long id) throws ScrmException {
        ScrmWorkOrderSlaEntity entity = findSlaOrThrow(id);
        slaRepository.delete(entity);
        log.info("删除 SLA 策略: id={}", id);
    }

    /**
     * 查询 SLA 策略详情。
     *
     * @param id SLA 策略 ID
     * @return SLA 策略 DTO
     * @throws ScrmException 策略不存在
     */
    @Transactional(readOnly = true)
    public ScrmWorkOrderSlaDto getSlaPolicy(Long id) throws ScrmException {
        return toSlaDto(findSlaOrThrow(id));
    }

    /**
     * 按策略编码查询 SLA 策略。
     *
     * @param code 策略编码
     * @return SLA 策略 DTO
     * @throws ScrmException 策略不存在
     */
    @Transactional(readOnly = true)
    public ScrmWorkOrderSlaDto getSlaPolicyByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("策略编码不能为空");
        }
        ScrmWorkOrderSlaEntity entity = slaRepository.findByPolicyCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "SLA 策略不存在: code=" + code));

        return toSlaDto(entity);
    }

    /**
     * 查询 SLA 策略列表 (按创建时间倒序)。
     *
     * @return SLA 策略列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderSlaDto> listSlaPolicies() {
        Specification<ScrmWorkOrderSlaEntity> spec = (root, query, cb) ->
                cb.and();
        return slaRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createTime"))
                .stream().map(this::toSlaDto).collect(Collectors.toList());
    }

    /**
     * 启用 SLA 策略。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    @Transactional
    public ScrmWorkOrderSlaDto enableSlaPolicy(Long id) throws ScrmException {
        ScrmWorkOrderSlaEntity entity = findSlaOrThrow(id);
        entity.setEnabled(true);
        entity = slaRepository.save(entity);
        log.info("启用 SLA 策略: id={}", id);
        return toSlaDto(entity);
    }

    /**
     * 禁用 SLA 策略。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    @Transactional
    public ScrmWorkOrderSlaDto disableSlaPolicy(Long id) throws ScrmException {
        ScrmWorkOrderSlaEntity entity = findSlaOrThrow(id);
        entity.setEnabled(false);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            entity.setIsDefault(false);
        }
        entity = slaRepository.save(entity);
        log.info("禁用 SLA 策略: id={}", id);
        return toSlaDto(entity);
    }

    /**
     * 设置默认 SLA 策略 (清除其他默认)。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    @Transactional
    public ScrmWorkOrderSlaDto setDefaultSlaPolicy(Long id) throws ScrmException {
        ScrmWorkOrderSlaEntity entity = findSlaOrThrow(id);
        // 清除其他默认
        clearOtherDefaults(id);
        entity.setIsDefault(true);
        entity.setEnabled(true);
        entity = slaRepository.save(entity);
        log.info("设置默认 SLA 策略: id={}", id);
        return toSlaDto(entity);
    }

    /**
     * 应用 SLA 策略到工单 (计算截止时间)。
     * <p>按策略的响应与解决时间分钟, 计算工单的 SLA 响应截止与解决截止,
     * 写入 slaPolicy / slaResponseDue / slaResolutionDue 字段。</p>
     *
     * @param orderId  工单 ID
     * @param policyId SLA 策略 ID
     * @return 更新后的工单
     * @throws ScrmException 工单或策略不存在
     */
    @Transactional
    public ScrmWorkOrderDto applySlaPolicy(Long orderId, Long policyId) throws ScrmException {
        ScrmWorkOrderEntity order = crudService.findOrderOrThrow(orderId);
        ScrmWorkOrderSlaEntity policy = findSlaOrThrow(policyId);
        LocalDateTime base = order.getCreateTime() != null ? order.getCreateTime() : LocalDateTime.now();
        order.setSlaPolicy(policy.getPolicyName());
        order.setSlaResponseDue(base.plusMinutes(policy.getResponseTimeMinutes()));
        order.setSlaResolutionDue(base.plusMinutes(policy.getResolutionTimeMinutes()));
        // 重新校验达标状态
        LocalDateTime now = LocalDateTime.now();
        if (order.getSlaResponseDue() != null && order.getAcceptedAt() != null) {
            order.setSlaResponseMet(!order.getAcceptedAt().isAfter(order.getSlaResponseDue()));
        }
        if (order.getSlaResolutionDue() != null && order.getResolvedAt() != null) {
            order.setSlaResolutionMet(!order.getResolvedAt().isAfter(order.getSlaResolutionDue()));
        }
        // 若任一截止已过且对应时间未达成, 标记违规
        if (order.getSlaResponseDue() != null && order.getSlaResponseDue().isBefore(now) && order.getAcceptedAt() == null) {
            order.setSlaBreached(true);
        }
        if (order.getSlaResolutionDue() != null && order.getSlaResolutionDue().isBefore(now) && order.getResolvedAt() == null) {
            order.setSlaBreached(true);
        }
        order = orderRepository.save(order);
        logService.addLog(logService.buildLog(order, ScrmWorkOrderCrudService.LOG_INTERNAL, "应用 SLA 策略",
                null, policy.getPolicyName(), now,
                "应用 SLA 策略: policy=" + policy.getPolicyName()
                        + ", responseDue=" + order.getSlaResponseDue()
                        + ", resolutionDue=" + order.getSlaResolutionDue(),
                true, false));
        log.info("应用 SLA 策略: orderId={}, policyId={}", orderId, policyId);
        return crudService.toOrderDto(order);
    }

    /**
     * 检查 SLA 违规 (扫描超时工单 → 标记违规 → 触发升级)。
     * <p>扫描当前账号下 SLA 解决截止早于当前时间且状态未关闭的工单, 标记 slaBreached=true,
     * 记录 SLA_BREACH 日志, 若策略启用升级则触发一级升级。</p>
     *
     * @return 标记违规的工单数
     */
    @Transactional
    public int checkSlaBreaches() {
        List<ScrmWorkOrderEntity> overdue = orderRepository
                .findBySlaResolutionDueBeforeAndOrderStatusIn(LocalDateTime.now(), ScrmWorkOrderCrudService.OPEN_STATUSES);
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ScrmWorkOrderEntity order : overdue) {
            if (Boolean.TRUE.equals(order.getSlaBreached())) {
                continue;
            }
            order.setSlaBreached(true);
            if (order.getSlaResolutionMet() == null) {
                order.setSlaResolutionMet(false);
            }
            orderRepository.save(order);
            // 记录违规日志
            logService.addLog(logService.buildLog(order, ScrmWorkOrderCrudService.LOG_SLA_BREACH, "SLA 违规",
                    order.getSlaResolutionDue() != null ? order.getSlaResolutionDue().toString() : null,
                    now.toString(), now, "SLA 解决截止时间违规: due=" + order.getSlaResolutionDue(),
                    false, true));
            // 触发升级 (若策略启用升级)
            ScrmWorkOrderSlaEntity policy = resolveSlaPolicy(order);
            if (policy != null && Boolean.TRUE.equals(policy.getEscalationEnabled())
                    && !Boolean.TRUE.equals(order.getEscalated())) {
                try {
                    actionService.escalateOrder(order.getId(), "L2-支持团队", "SLA 违规自动升级");
                } catch (ScrmException e) {
                    log.warn("SLA 违规自动升级失败: orderId={}, error={}", order.getId(), e.getMessage());
                }
            }
            count++;
        }
        log.info("检查 SLA 违规:, breached={}", count);
        return count;
    }

    /**
     * 发送 SLA 预警 (扫描即将违规的工单, 记录预警日志)。
     * <p>扫描当前账号下 SLA 解决截止在未来 N 分钟内 (默认 30 分钟) 且状态未关闭的工单,
     * 记录 SLA_BREACH 预警日志。实际推送通道由通知中心异步处理。</p>
     *
     * @return 预警的工单数
     */
    @Transactional
    public int sendSlaWarnings() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningThreshold = now.plusMinutes(30);
        // 扫描即将违规工单 (slaResolutionDue 在 now ~ warningThreshold 之间)
        List<ScrmWorkOrderEntity> all = orderRepository
                .findBySlaResolutionDueBeforeAndOrderStatusIn(warningThreshold, ScrmWorkOrderCrudService.OPEN_STATUSES);
        int count = 0;
        for (ScrmWorkOrderEntity order : all) {
            if (order.getSlaResolutionDue() == null || order.getSlaResolutionDue().isBefore(now)) {
                // 已违规, 由 checkSlaBreaches 处理
                continue;
            }
            logService.addLog(logService.buildLog(order, ScrmWorkOrderCrudService.LOG_SLA_BREACH, "SLA 预警",
                    null, null, now,
                    "SLA 即将违规: due=" + order.getSlaResolutionDue()
                            + ", remainingMinutes=" + Duration.between(now, order.getSlaResolutionDue()).toMinutes(),
                    true, false));
            count++;
        }
        log.info("发送 SLA 预警:, warned={}", count);
        return count;
    }

    /**
     * 升级工单 (SLA 触发, 按级别升级)。
     * <p>根据升级级别设置升级目标 (L1/L2/L3 对应不同团队), 标记 escalated=true,
     * 记录 ESCALATION 日志。</p>
     *
     * @param orderId 工单 ID
     * @param level   升级级别 (1/2/3)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在
     */
    @Transactional
    public ScrmWorkOrderDto escalateOrder(Long orderId, int level) throws ScrmException {
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(orderId);
        String escalatedTo;
        switch (level) {
            case 1:
                escalatedTo = "L1-一线支持";
                break;
            case 2:
                escalatedTo = "L2-二线支持";
                break;
            case 3:
                escalatedTo = "L3-三线支持";
                break;
            default:
                escalatedTo = "L" + level + "-支持团队";
                break;
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setEscalated(true);
        entity.setEscalatedTo(escalatedTo);
        entity.setEscalatedAt(now);
        entity.setEscalationReason("SLA 升级级别: L" + level);
        entity.setIsUrgent(true);
        entity = orderRepository.save(entity);
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_ESCALATION, "SLA 升级",
                null, escalatedTo, now, "SLA 升级: level=" + level + ", to=" + escalatedTo,
                false, true));
        log.info("SLA 升级工单: orderId={}, level={}", orderId, level);
        return crudService.toOrderDto(entity);
    }

    /**
     * 查询 SLA 达标率 (时间范围内已结束工单的达标比例)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return SLA 达标率统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSlaCompliance(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmWorkOrderEntity> spec = crudService.buildTimeRangeSpec(startTime, endTime);
        List<ScrmWorkOrderEntity> orders = orderRepository.findAll(spec);
        long total = orders.size();
        long metCount = 0;
        long breachedCount = 0;
        for (ScrmWorkOrderEntity o : orders) {
            if (Boolean.TRUE.equals(o.getSlaBreached())) {
                breachedCount++;
            } else if (ScrmWorkOrderCrudService.STATUS_RESOLVED.equals(o.getOrderStatus())
                    || ScrmWorkOrderCrudService.STATUS_CLOSED.equals(o.getOrderStatus())) {
                metCount++;
            }
        }
        long ended = metCount + breachedCount;
        double complianceRate = ended > 0 ? Math.round((double) metCount / ended * 10000d) / 100d : 0d;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("metCount", metCount);
        result.put("breachedCount", breachedCount);
        result.put("complianceRate", complianceRate);
        return result;
    }

    /**
     * 查询单个 SLA 策略的统计 (使用策略持久化的统计字段)。
     *
     * @param policyId SLA 策略 ID
     * @return SLA 统计
     * @throws ScrmException 策略不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSlaStats(Long policyId) throws ScrmException {
        ScrmWorkOrderSlaEntity policy = findSlaOrThrow(policyId);
        return toSlaStatsMap(policy);
    }

    /**
     * 更新 SLA 策略的统计字段 (扫描应用该策略的工单, 重新计算达标率与平均时长)。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    @Transactional
    public ScrmWorkOrderSlaDto updateSlaStats(Long id) throws ScrmException {
        ScrmWorkOrderSlaEntity policy = findSlaOrThrow(id);
        final String policyName = policy.getPolicyName();
        // 扫描应用该策略的工单
        Specification<ScrmWorkOrderEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("slaPolicy"), policyName));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmWorkOrderEntity> orders = orderRepository.findAll(spec);
        int total = orders.size();
        int breached = 0;
        int met = 0;
        long responseSum = 0;
        long responseCount = 0;
        long resolutionSum = 0;
        long resolutionCount = 0;
        for (ScrmWorkOrderEntity o : orders) {
            if (Boolean.TRUE.equals(o.getSlaBreached())) {
                breached++;
            } else if (ScrmWorkOrderCrudService.STATUS_RESOLVED.equals(o.getOrderStatus())
                    || ScrmWorkOrderCrudService.STATUS_CLOSED.equals(o.getOrderStatus())) {
                met++;
            }
            if (o.getResponseTimeMinutes() != null) {
                responseSum += o.getResponseTimeMinutes();
                responseCount++;
            }
            if (o.getResolutionTimeMinutes() != null) {
                resolutionSum += o.getResolutionTimeMinutes();
                resolutionCount++;
            }
        }
        policy.setTotalOrders(total);
        policy.setBreachedOrders(breached);
        policy.setMetOrders(met);
        int ended = met + breached;
        policy.setCurrentComplianceRate(ended > 0 ? Math.round((double) met / ended * 10000d) / 100d : 0d);
        policy.setAvgResponseTime(responseCount > 0
                ? Math.round((double) responseSum / responseCount * 100d) / 100d
                : 0d);
        policy.setAvgResolutionTime(resolutionCount > 0
                ? Math.round((double) resolutionSum / resolutionCount * 100d) / 100d
                : 0d);
        policy = slaRepository.save(policy);
        log.info("更新 SLA 统计: policyId={}, total={}, breached={}, met={}", id, total, breached, met);
        return toSlaDto(policy);
    }

    /**
     * 按 SLA 状态查询工单 (BREACHED / MET / PENDING)。
     *
     * @param slaStatus SLA 状态: BREACHED (违规) / MET (达标) / PENDING (待定)
     * @param pageable  分页参数
     * @return 工单分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmWorkOrderDto> getOrdersBySla(String slaStatus, Pageable pageable) {
        Specification<ScrmWorkOrderEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if ("BREACHED".equals(slaStatus)) {
                predicates.add(cb.isTrue(root.get("slaBreached")));
            } else if ("MET".equals(slaStatus)) {
                predicates.add(cb.isFalse(root.get("slaBreached")));
                predicates.add(root.get("orderStatus").in(Arrays.asList(ScrmWorkOrderCrudService.STATUS_RESOLVED,
                        ScrmWorkOrderCrudService.STATUS_CLOSED)));
            } else if ("PENDING".equals(slaStatus)) {
                predicates.add(cb.isFalse(root.get("slaBreached")));
                predicates.add(root.get("orderStatus").in(ScrmWorkOrderCrudService.OPEN_STATUSES));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return orderRepository.findAll(spec, pageable).map(crudService::toOrderDto);
    }

    // ============================================================
    // SLA 计算
    // ============================================================

    /**
     * 计算 SLA 截止时间 (按工单类型与优先级, 查找匹配的 SLA 策略计算)。
     * <p>策略查找顺序: 1) 工单类型 + 优先级匹配; 2) 工单类型匹配; 3) 优先级匹配;
     * 4) 默认策略; 5) 兜底固定规则 (URGENT=4h/HIGH=8h/NORMAL=24h/LOW=48h)。</p>
     *
     * @param orderType 工单类型
     * @param priority  优先级
     * @param createdAt 创建时间
     * @return SLA 截止时间 (解决截止)
     */
    public LocalDateTime calculateSlaDeadline(String orderType, String priority, LocalDateTime createdAt) {
        ScrmWorkOrderSlaEntity policy = crudService.resolveSlaPolicyByMatch(orderType, priority);
        if (policy != null) {
            return createdAt.plusMinutes(policy.getResolutionTimeMinutes());
        }
        // 兜底固定规则
        int hours;
        switch (priority != null ? priority : ScrmWorkOrderCrudService.PRIORITY_NORMAL) {
            case ScrmWorkOrderCrudService.PRIORITY_URGENT:
                hours = 4;
                break;
            case ScrmWorkOrderCrudService.PRIORITY_HIGH:
                hours = 8;
                break;
            case ScrmWorkOrderCrudService.PRIORITY_LOW:
                hours = 48;
                break;
            case ScrmWorkOrderCrudService.PRIORITY_NORMAL:
            default:
                hours = 24;
                break;
        }
        return createdAt.plusHours(hours);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验 SLA 策略参数。
     *
     * @param dto     SLA 策略参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateSlaDto(ScrmWorkOrderSlaDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("SLA 策略参数不能为空");
        }
        if (!partial) {
            if (dto.getPolicyName() == null || dto.getPolicyName().isBlank()) {
                throw ScrmException.badRequest("策略名称不能为空");
            }
            if (dto.getPolicyCode() == null || dto.getPolicyCode().isBlank()) {
                throw ScrmException.badRequest("策略编码不能为空");
            }
            if (dto.getResponseTimeMinutes() == null || dto.getResponseTimeMinutes() < 0) {
                throw ScrmException.badRequest("响应时间分钟不能为空且不能为负数");
            }
            if (dto.getResolutionTimeMinutes() == null || dto.getResolutionTimeMinutes() < 0) {
                throw ScrmException.badRequest("解决时间分钟不能为空且不能为负数");
            }
        }
    }

    /**
     * 解析工单应用的 SLA 策略 (按 slaPolicy 名称查找)。
     *
     * @param order 工单实体
     * @return SLA 策略 (可能为空)
     */
    private ScrmWorkOrderSlaEntity resolveSlaPolicy(ScrmWorkOrderEntity order) {
        if (order.getSlaPolicy() == null || order.getSlaPolicy().isBlank()) {
            return null;
        }
        return slaRepository.findByPolicyName(order.getSlaPolicy()).orElse(null);
    }

    /**
     * 清除其他默认 SLA 策略 (除 excludeId 外)。
     *
     * @param excludeId 排除的策略 ID (可空)
     */
    private void clearOtherDefaults(Long excludeId) {
        slaRepository.findByIsDefaultTrue().ifPresent(p -> {
            if (excludeId == null || !excludeId.equals(p.getId())) {
                p.setIsDefault(false);
                slaRepository.save(p);
            }
        });
    }

    /**
     * 应用 SLA 策略默认值 (字段为空时补全)。
     *
     * @param entity SLA 策略实体
     */
    private void applySlaDefaults(ScrmWorkOrderSlaEntity entity) {
        if (entity.getBusinessHoursOnly() == null) {
            entity.setBusinessHoursOnly(false);
        }
        if (entity.getBusinessHoursStart() == null) {
            entity.setBusinessHoursStart("09:00");
        }
        if (entity.getBusinessHoursEnd() == null) {
            entity.setBusinessHoursEnd("18:00");
        }
        if (entity.getBusinessDays() == null) {
            entity.setBusinessDays("MON-FRI");
        }
        if (entity.getTimezone() == null) {
            entity.setTimezone("Asia/Shanghai");
        }
        if (entity.getEscalationEnabled() == null) {
            entity.setEscalationEnabled(true);
        }
        if (entity.getWarningBeforeBreach() == null) {
            entity.setWarningBeforeBreach(30);
        }
        if (entity.getAutoCloseAfterResolution() == null) {
            entity.setAutoCloseAfterResolution(72);
        }
        if (entity.getReopenAllowed() == null) {
            entity.setReopenAllowed(true);
        }
        if (entity.getReopenWithinHours() == null) {
            entity.setReopenWithinHours(168);
        }
        if (entity.getPenaltyPerBreach() == null) {
            entity.setPenaltyPerBreach(0d);
        }
        if (entity.getCreditPerMet() == null) {
            entity.setCreditPerMet(0d);
        }
        if (entity.getTargetComplianceRate() == null) {
            entity.setTargetComplianceRate(95d);
        }
        if (entity.getCurrentComplianceRate() == null) {
            entity.setCurrentComplianceRate(0d);
        }
        if (entity.getTotalOrders() == null) {
            entity.setTotalOrders(0);
        }
        if (entity.getBreachedOrders() == null) {
            entity.setBreachedOrders(0);
        }
        if (entity.getMetOrders() == null) {
            entity.setMetOrders(0);
        }
        if (entity.getAvgResponseTime() == null) {
            entity.setAvgResponseTime(0d);
        }
        if (entity.getAvgResolutionTime() == null) {
            entity.setAvgResolutionTime(0d);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        if (entity.getIsDefault() == null) {
            entity.setIsDefault(false);
        }
        // 同步 hours 字段
        if (entity.getResponseTimeHours() == null && entity.getResponseTimeMinutes() != null) {
            entity.setResponseTimeHours(entity.getResponseTimeMinutes() / 60);
        }
        if (entity.getResolutionTimeHours() == null && entity.getResolutionTimeMinutes() != null) {
            entity.setResolutionTimeHours(entity.getResolutionTimeMinutes() / 60);
        }
    }

    /**
     * 按主键查询 SLA 策略, 不存在或越权抛异常
     */
    private ScrmWorkOrderSlaEntity findSlaOrThrow(Long id) throws ScrmException {
        ScrmWorkOrderSlaEntity entity = slaRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "SLA 策略不存在: id=" + id));

        return entity;
    }

    /**
     * SLA 策略实体转 DTO
     */
    private ScrmWorkOrderSlaDto toSlaDto(ScrmWorkOrderSlaEntity entity) {
        ScrmWorkOrderSlaDto dto = new ScrmWorkOrderSlaDto();
        dto.setId(entity.getId());
        dto.setPolicyName(entity.getPolicyName());
        dto.setPolicyCode(entity.getPolicyCode());
        dto.setDescription(entity.getDescription());
        dto.setOrderType(entity.getOrderType());
        dto.setPriority(entity.getPriority());
        dto.setCustomerSegment(entity.getCustomerSegment());
        dto.setResponseTimeMinutes(entity.getResponseTimeMinutes());
        dto.setResolutionTimeMinutes(entity.getResolutionTimeMinutes());
        dto.setResponseTimeHours(entity.getResponseTimeHours());
        dto.setResolutionTimeHours(entity.getResolutionTimeHours());
        dto.setBusinessHoursOnly(entity.getBusinessHoursOnly());
        dto.setBusinessHoursStart(entity.getBusinessHoursStart());
        dto.setBusinessHoursEnd(entity.getBusinessHoursEnd());
        dto.setBusinessDays(entity.getBusinessDays());
        dto.setTimezone(entity.getTimezone());
        dto.setEscalationEnabled(entity.getEscalationEnabled());
        dto.setEscalationLevels(entity.getEscalationLevels());
        dto.setFirstResponseBreachAction(entity.getFirstResponseBreachAction());
        dto.setResolutionBreachAction(entity.getResolutionBreachAction());
        dto.setWarningBeforeBreach(entity.getWarningBeforeBreach());
        dto.setAutoCloseAfterResolution(entity.getAutoCloseAfterResolution());
        dto.setReopenAllowed(entity.getReopenAllowed());
        dto.setReopenWithinHours(entity.getReopenWithinHours());
        dto.setPenaltyPerBreach(entity.getPenaltyPerBreach());
        dto.setCreditPerMet(entity.getCreditPerMet());
        dto.setTargetComplianceRate(entity.getTargetComplianceRate());
        dto.setCurrentComplianceRate(entity.getCurrentComplianceRate());
        dto.setTotalOrders(entity.getTotalOrders());
        dto.setBreachedOrders(entity.getBreachedOrders());
        dto.setMetOrders(entity.getMetOrders());
        dto.setAvgResponseTime(entity.getAvgResponseTime());
        dto.setAvgResolutionTime(entity.getAvgResolutionTime());
        dto.setEnabled(entity.getEnabled());
        dto.setIsDefault(entity.getIsDefault());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * SLA 策略 DTO 转实体 (不含  由调用方补全)
     */
    private ScrmWorkOrderSlaEntity toSlaEntity(ScrmWorkOrderSlaDto dto) {
        ScrmWorkOrderSlaEntity entity = new ScrmWorkOrderSlaEntity();
        entity.setPolicyName(dto.getPolicyName());
        entity.setPolicyCode(dto.getPolicyCode());
        entity.setDescription(dto.getDescription());
        entity.setOrderType(dto.getOrderType());
        entity.setPriority(dto.getPriority());
        entity.setCustomerSegment(dto.getCustomerSegment());
        entity.setResponseTimeMinutes(dto.getResponseTimeMinutes());
        entity.setResolutionTimeMinutes(dto.getResolutionTimeMinutes());
        entity.setResponseTimeHours(dto.getResponseTimeHours());
        entity.setResolutionTimeHours(dto.getResolutionTimeHours());
        entity.setBusinessHoursOnly(dto.getBusinessHoursOnly());
        entity.setBusinessHoursStart(dto.getBusinessHoursStart());
        entity.setBusinessHoursEnd(dto.getBusinessHoursEnd());
        entity.setBusinessDays(dto.getBusinessDays());
        entity.setTimezone(dto.getTimezone());
        entity.setEscalationEnabled(dto.getEscalationEnabled());
        entity.setEscalationLevels(dto.getEscalationLevels());
        entity.setFirstResponseBreachAction(dto.getFirstResponseBreachAction());
        entity.setResolutionBreachAction(dto.getResolutionBreachAction());
        entity.setWarningBeforeBreach(dto.getWarningBeforeBreach());
        entity.setAutoCloseAfterResolution(dto.getAutoCloseAfterResolution());
        entity.setReopenAllowed(dto.getReopenAllowed());
        entity.setReopenWithinHours(dto.getReopenWithinHours());
        entity.setPenaltyPerBreach(dto.getPenaltyPerBreach());
        entity.setCreditPerMet(dto.getCreditPerMet());
        entity.setTargetComplianceRate(dto.getTargetComplianceRate());
        entity.setCurrentComplianceRate(dto.getCurrentComplianceRate());
        entity.setTotalOrders(dto.getTotalOrders());
        entity.setBreachedOrders(dto.getBreachedOrders());
        entity.setMetOrders(dto.getMetOrders());
        entity.setAvgResponseTime(dto.getAvgResponseTime());
        entity.setAvgResolutionTime(dto.getAvgResolutionTime());
        entity.setEnabled(dto.getEnabled());
        entity.setIsDefault(dto.getIsDefault());
        entity.setCreatedBy(dto.getCreatedBy());
        return entity;
    }

    /**
     * SLA 策略统计转 Map
     */
    private Map<String, Object> toSlaStatsMap(ScrmWorkOrderSlaEntity policy) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("policyId", policy.getId());
        stats.put("policyName", policy.getPolicyName());
        stats.put("policyCode", policy.getPolicyCode());
        stats.put("totalOrders", policy.getTotalOrders() != null ? policy.getTotalOrders() : 0);
        stats.put("breachedOrders", policy.getBreachedOrders() != null ? policy.getBreachedOrders() : 0);
        stats.put("metOrders", policy.getMetOrders() != null ? policy.getMetOrders() : 0);
        stats.put("currentComplianceRate", policy.getCurrentComplianceRate() != null
                ? policy.getCurrentComplianceRate() : 0d);
        stats.put("targetComplianceRate", policy.getTargetComplianceRate() != null
                ? policy.getTargetComplianceRate() : 95d);
        stats.put("avgResponseTime", policy.getAvgResponseTime() != null ? policy.getAvgResponseTime() : 0d);
        stats.put("avgResolutionTime", policy.getAvgResolutionTime() != null ? policy.getAvgResolutionTime() : 0d);
        return stats;
    }
}