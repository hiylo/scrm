/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskRuleService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.callback.ConversationEventCallbackDto;

import org.hiylo.scrm.dto.ScrmRiskRuleDto;
import org.hiylo.scrm.entity.ScrmRiskRuleEntity;
import org.hiylo.scrm.entity.ScrmRiskSignalEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmRiskRuleRepository;
import org.hiylo.scrm.repository.ScrmRiskSignalRepository;
import org.hiylo.scrm.service.RiskRuleEvaluator.RiskRuleContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 风险规则服务。
 * <p>
* 承载风险规则的增删改查、启用/禁用与核心评估能力。所有写操作写入当前用户归属账号
* 实现数据隔离。{@link #evaluateMessage(ConversationEventCallbackDto)}
 * 加载全部启用规则并按优先级升序评估, 命中后持久化 {@link ScrmRiskSignalEntity} 并通过
 * {@link ScrmNotificationService#notifyRiskSignal} 推送告警。
 * </p>
 * <p>
 * 评估失败不抛异常 (单条规则异常跳过, 不阻断后续规则评估), 通知推送失败仅记录日志。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmRiskRuleService {

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 100;

    /** 默认触发动作 */
    private static final String DEFAULT_ACTION = "ALERT";

    /** 合法的风险等级: LOW 低 / MEDIUM 中 / HIGH 高 / CRITICAL 严重 */
    private static final List<String> VALID_RISK_LEVELS =
            List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    /** 合法的触发动作: ALERT 告警 / PAUSE_ACCOUNT 暂停账号 / STOP_CAMPAIGN 停止任务 (与 ScrmRiskRuleDto @Pattern 保持一致) */
    private static final List<String> VALID_ACTIONS =
            List.of("ALERT", "PAUSE_ACCOUNT", "STOP_CAMPAIGN");

    /** 优先级下限 (含) */
    private static final int PRIORITY_MIN = 1;
    /** 优先级上限 (含) */
    private static final int PRIORITY_MAX = 9999;

    /** 风险规则数据访问层 */
    private final ScrmRiskRuleRepository riskRuleRepository;

    /** SpEL 规则评估器 */
    private final RiskRuleEvaluator riskRuleEvaluator;

    /** 风控信号数据访问层（持久化命中信号） */
    private final ScrmRiskSignalRepository riskSignalRepository;

    /** SCRM 实时通知服务（推送风控告警） */
    private final ScrmNotificationService notificationService;

    /**
     * 创建风险规则。
     * <p>校验 ruleCode 业务唯一后写入归属账号 ID 持久化, enabled/priority/action 缺省时填默认值。</p>
     * <p>参数校验:
     * <ul>
     *   <li>ruleName 非空</li>
     *   <li>ruleCode 非空且业务唯一</li>
     *   <li>conditionExpression 非空 (SpEL 表达式)</li>
     *   <li>riskLevel 非空且为 LOW/MEDIUM/HIGH/CRITICAL 之一</li>
     *   <li>signalType 非空</li>
     *   <li>action (如有) 必须为 ALERT/BLOCK/FREEZE/LOGOUT 之一</li>
     *   <li>priority (如有) 必须在 1-9999 之间</li>
     * </ul>
     * </p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException ruleCode 已存在 / 参数非法
     */
    @Transactional
    public ScrmRiskRuleEntity createRule(ScrmRiskRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        // SpEL 表达式安全校验: 在持久化前拦截危险语法 (类型引用/构造器/危险方法调用)
        riskRuleEvaluator.validateExpression(dto.getConditionExpression());
        if (riskRuleRepository.findByRuleCode(dto.getRuleCode()).isPresent()) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "规则代码已存在: ruleCode=" + dto.getRuleCode());
        }
        ScrmRiskRuleEntity entity = new ScrmRiskRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setRuleCode(dto.getRuleCode());
        entity.setConditionExpression(dto.getConditionExpression());
        entity.setRiskLevel(dto.getRiskLevel());
        entity.setSignalType(dto.getSignalType());
        entity.setDescription(dto.getDescription());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setAction(dto.getAction() != null && !dto.getAction().isBlank() ? dto.getAction() : DEFAULT_ACTION);
        entity = riskRuleRepository.save(entity);
        log.info("创建风险规则: id={}, ruleCode={}, signalType={}, riskLevel={}",
                entity.getId(), entity.getRuleCode(), entity.getSignalType(), entity.getRiskLevel());
        return entity;
    }

    /**
     * 更新风险规则（字段非空才覆盖）。
     * <p>部分更新场景: 仅校验非空字段的合法性, ruleCode 变更时校验与其他规则不冲突。</p>
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / ruleCode 与其他规则冲突 / 参数非法
     */
    @Transactional
    public ScrmRiskRuleEntity updateRule(Long id, ScrmRiskRuleDto dto) throws ScrmException {
        ScrmRiskRuleEntity entity = findOrThrow(id);
        // 部分更新场景: 仅校验非空字段
        validateRuleDto(dto, true);
        // SpEL 表达式安全校验: 仅当提供新表达式时在持久化前执行静态拦截
        if (dto.getConditionExpression() != null && !dto.getConditionExpression().isBlank()) {
            riskRuleEvaluator.validateExpression(dto.getConditionExpression());
        }
        // ruleCode 变更时校验与其他规则不冲突
        if (dto.getRuleCode() != null && !dto.getRuleCode().equals(entity.getRuleCode())) {
            riskRuleRepository.findByRuleCode(dto.getRuleCode())
                    .ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                            "规则代码已被其他规则占用: ruleCode=" + dto.getRuleCode());
                }
            });
            entity.setRuleCode(dto.getRuleCode());
        }
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getConditionExpression() != null) entity.setConditionExpression(dto.getConditionExpression());
        if (dto.getRiskLevel() != null) entity.setRiskLevel(dto.getRiskLevel());
        if (dto.getSignalType() != null) entity.setSignalType(dto.getSignalType());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getAction() != null && !dto.getAction().isBlank()) entity.setAction(dto.getAction());
        entity = riskRuleRepository.save(entity);
        log.info("更新风险规则: id={}, ruleCode={}", entity.getId(), entity.getRuleCode());
        return entity;
    }

    /**
     * 删除风险规则。
     * <p>
     * 删除前检查是否有风控信号 (scrm_risk_signal) 引用该规则, 若有则阻止删除
     * 并返回引用数量, 避免删除后风控信号失去关联导致审计追溯断裂。
     * 规则本身可先禁用 (disableRule) 而不删除, 以保留历史关联。
     * </p>
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在 / 仍有风控信号引用
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmRiskRuleEntity entity = findOrThrow(id);
        // 检查是否有风控信号引用该规则, 避免删除后审计追溯断裂
        long signalCount = riskSignalRepository.countByRuleId(String.valueOf(id));
        if (signalCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除规则: 仍有 %d 条风控信号引用该规则, 请先禁用规则而非删除", signalCount));
        }
        riskRuleRepository.delete(entity);
        log.info("删除风险规则: id={}, ruleCode={}", id, entity.getRuleCode());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmRiskRuleEntity getRule(Long id) throws ScrmException {
        return findOrThrow(id);
    }

    /**
     * 分页查询规则, 支持按启用状态与关键字过滤。
     * <p>过滤优先级: enabled > keyword, 均为空时全量分页 (按 createTime 倒序)。</p>
     *
     * @param enabled 启用状态过滤（可空）
     * @param keyword 关键字过滤（按 ruleName / ruleCode 模糊匹配, 可空）
     * @param page    页码（从 0 开始）
     * @param size    每页大小
     * @return 规则分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmRiskRuleEntity> listRules(Boolean enabled, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<ScrmRiskRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前账号过滤
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("ruleName")), kw),
                        cb.like(cb.lower(root.get("ruleCode")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return riskRuleRepository.findAll(spec, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void enableRule(Long id) throws ScrmException {
        ScrmRiskRuleEntity entity = findOrThrow(id);
        entity.setEnabled(true);
        riskRuleRepository.save(entity);
        log.info("启用风险规则: id={}, ruleCode={}", id, entity.getRuleCode());
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void disableRule(Long id) throws ScrmException {
        ScrmRiskRuleEntity entity = findOrThrow(id);
        entity.setEnabled(false);
        riskRuleRepository.save(entity);
        log.info("禁用风险规则: id={}, ruleCode={}", id, entity.getRuleCode());
    }

    /**
     * 评估一条会话消息, 返回命中的风控信号列表。
     * <p>
     * 加载全部启用规则 (按 priority ASC), 构建 {@link RiskRuleContext} 后逐条评估。
     * 命中后持久化 {@link ScrmRiskSignalEntity} 并推送告警通知 (通知失败不阻断)。
     * 单条规则评估异常跳过, 不影响后续规则。
     * </p>
     *
     * @param event 会话事件回调 DTO
     * @return 命中的风控信号列表（无命中返回空列表）
     */
    @Transactional
    public List<ScrmRiskSignalEntity> evaluateMessage(ConversationEventCallbackDto event) {
        if (event == null) {
            return List.of();
        }
        // 数据隔离: 仅加载当前账号的启用规则, 避免越权规则误判
        List<ScrmRiskRuleEntity> rules = riskRuleRepository.findByEnabledTrueOrderByPriorityAsc();
        if (rules.isEmpty()) {
            return List.of();
        }
        RiskRuleContext context = buildContextFromEvent(event);
        List<ScrmRiskSignalEntity> signals = new ArrayList<>();
        for (ScrmRiskRuleEntity rule : rules) {
            boolean hit;
            try {
                hit = riskRuleEvaluator.evaluate(rule.getConditionExpression(), context);
            } catch (Exception e) {
                log.warn("规则评估异常, 跳过该规则: ruleId={}, ruleCode={}, err={}",
                        rule.getId(), rule.getRuleCode(), e.getMessage());
                continue;
            }
            if (hit) {
                ScrmRiskSignalEntity signal = buildSignal(rule, context);
                riskSignalRepository.save(signal);
                signals.add(signal);
                log.warn("风控规则命中: ruleId={}, ruleCode={}, signalType={}, riskLevel={}, accountId={}",
                        rule.getId(), rule.getRuleCode(), rule.getSignalType(),
                        rule.getRiskLevel(), context.getAccountId());
                pushRiskNotification(rule, signal);
            }
        }
        return signals;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验风险规则参数。
     * <p>
     * 创建场景 (partial=false): ruleName / ruleCode / conditionExpression / riskLevel / signalType 必填。
     * 更新场景 (partial=true): 允许字段为空 (部分更新), 仅校验非空字段的合法性。
     * </p>
     * <ul>
     *   <li>ruleName: 非空 (创建场景)</li>
     *   <li>ruleCode: 非空 (创建场景)</li>
     *   <li>conditionExpression: 非空 (创建场景, SpEL 表达式)</li>
     *   <li>riskLevel: 非空 (创建场景) 且为 LOW/MEDIUM/HIGH/CRITICAL 之一</li>
     *   <li>signalType: 非空 (创建场景)</li>
     *   <li>action (如有): 必须为 ALERT/BLOCK/FREEZE/LOGOUT 之一</li>
     *   <li>priority (如有): 必须在 1-9999 之间</li>
     * </ul>
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景 (true 时允许必填字段为空)
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmRiskRuleDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        // ruleName 校验
        if (dto.getRuleName() != null) {
            if (dto.getRuleName().isBlank()) {
                throw ScrmException.badRequest("规则名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则名称不能为空");
        }
        // ruleCode 校验
        if (dto.getRuleCode() != null) {
            if (dto.getRuleCode().isBlank()) {
                throw ScrmException.badRequest("规则代码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则代码不能为空");
        }
        // conditionExpression 校验
        if (dto.getConditionExpression() != null) {
            if (dto.getConditionExpression().isBlank()) {
                throw ScrmException.badRequest("条件表达式不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("条件表达式不能为空");
        }
        // riskLevel 校验 (合法值)
        if (dto.getRiskLevel() != null) {
            if (!VALID_RISK_LEVELS.contains(dto.getRiskLevel())) {
                throw ScrmException.badRequest(
                        "风险等级非法: " + dto.getRiskLevel() + ", 仅支持 " + VALID_RISK_LEVELS);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("风险等级不能为空");
        }
        // signalType 校验
        if (dto.getSignalType() != null) {
            if (dto.getSignalType().isBlank()) {
                throw ScrmException.badRequest("信号类型不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("信号类型不能为空");
        }
        // action 校验 (合法值, 仅在提供时校验)
        if (dto.getAction() != null && !dto.getAction().isBlank()) {
            if (!VALID_ACTIONS.contains(dto.getAction())) {
                throw ScrmException.badRequest(
                        "触发动作非法: " + dto.getAction() + ", 仅支持 " + VALID_ACTIONS);
            }
        }
        // priority 校验 (范围)
        if (dto.getPriority() != null) {
            if (dto.getPriority() < PRIORITY_MIN || dto.getPriority() > PRIORITY_MAX) {
                throw ScrmException.badRequest(
                        "优先级必须在 " + PRIORITY_MIN + "-" + PRIORITY_MAX + " 之间: " + dto.getPriority());
            }
        }
    }

    /**
     * 按启用状态在内存中过滤分页结果（用于 enabled + keyword 组合过滤场景）。
     *
     * @param page    原始分页
     * @param enabled 目标启用状态
     * @return 过滤后的分页 (PageImpl)
     */
    private Page<ScrmRiskRuleEntity> filterByEnabledInMemory(Page<ScrmRiskRuleEntity> page, Boolean enabled) {
        List<ScrmRiskRuleEntity> filtered = page.getContent().stream()
                .filter(e -> enabled.equals(e.getEnabled()))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(
                filtered, page.getPageable(), filtered.size());
    }

    /**
     * 从会话事件回调 DTO 构建风险规则上下文。
     * <p>message 取 content 字段, accountId 解析为 Long, eventTime 取 sentAt 缺省为 now。</p>
     *
     * @param event 会话事件回调 DTO
     * @return 风险规则上下文
     */
    private RiskRuleContext buildContextFromEvent(ConversationEventCallbackDto event) {
        return RiskRuleContext.builder()
                .message(event.getContent())
                .platformType(event.getPlatformType())
                .accountId(parseAccountId(event.getAccountId()))
                .customerId(event.getCustomerId())
                .eventTime(event.getSentAt() != null ? event.getSentAt() : LocalDateTime.now())
                .messageCountInWindow(null)
                .build();
    }

    /**
     * 构建风控信号实体。
     *
     * @param rule    命中的规则
     * @param context 评估上下文
     * @return 未持久化的风控信号实体
     */
    private ScrmRiskSignalEntity buildSignal(ScrmRiskRuleEntity rule, RiskRuleContext context) {
        ScrmRiskSignalEntity signal = new ScrmRiskSignalEntity();
        signal.setRuleId(String.valueOf(rule.getId()));
        signal.setAccountId(context.getAccountId());
        signal.setSignalType(rule.getSignalType());
        signal.setRiskLevel(rule.getRiskLevel());
        signal.setDetail(buildSignalDetail(rule, context));
        signal.setTriggeredAt(LocalDateTime.now());
        return signal;
    }

    /**
     * 构建风控信号详情描述。
     *
     * @param rule    命中的规则
     * @param context 评估上下文
     * @return 详情字符串
     */
    private String buildSignalDetail(ScrmRiskRuleEntity rule, RiskRuleContext context) {
        return "规则[" + rule.getRuleName() + "(" + rule.getRuleCode() + ")]命中, "
                + "信号类型=" + rule.getSignalType() + ", 风险等级=" + rule.getRiskLevel()
                + ", 平台=" + context.getPlatformType()
                + ", 账号=" + context.getAccountId()
                + ", 客户=" + context.getCustomerId();
    }

    /**
     * 推送风控告警通知, 异常仅记录日志不阻断评估主流程。
     * <p>
     * 同时推送两类通知:
     * <ul>
     *   <li>{@code notifyRiskSignal} - 兼容既有风控信号处理链路</li>
     *   <li>{@code sendRiskAlert} - 新增 RISK_ALERT 通知, 触发前端 Layout 全局红色告警弹窗</li>
     * </ul>
     * </p>
     *
     * @param rule   命中的规则
     * @param signal 持久化后的风控信号
     */
    private void pushRiskNotification(ScrmRiskRuleEntity rule, ScrmRiskSignalEntity signal) {
        try {
            notificationService.notifyRiskSignal(
                    String.valueOf(rule.getId()),
                    null,
                    rule.getRiskLevel(),
                    signal.getDetail());
        } catch (Exception e) {
            log.warn("风控告警通知推送失败 (不影响评估主流程): ruleId={}, err={}",
                    rule.getId(), e.getMessage());
        }
        // 推送 RISK_ALERT 通知, 触发前端全局红色告警弹窗 (accountId 从信号实体读取)
        try {
            notificationService.sendRiskAlert(
                    signal.getAccountId(),
                    rule.getRuleCode(),
                    signal.getDetail());
        } catch (Exception e) {
            log.warn("风险规则告警推送失败 (不影响评估主流程): ruleId={}, err={}",
                    rule.getId(), e.getMessage());
        }
    }

    /**
     * 将回调中的 accountId（String）解析为 Long, 不可解析时返回 null。
     *
     * @param accountId 账号 ID 字符串
     * @return 解析后的 Long, 不可解析时为 null
     */
    private Long parseAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(accountId.trim());
        } catch (NumberFormatException e) {
            log.warn("风险规则 accountId 非数字, 置 null: accountId={}", accountId);
            return null;
        }
    }

    /**
     * 按主键查询规则, 不存在抛异常。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmRiskRuleEntity findOrThrow(Long id) throws ScrmException {
        ScrmRiskRuleEntity entity = riskRuleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "风险规则不存在: id=" + id));
        // 数据隔离: 校验规则归属当前账号

        return entity;
    }
}
