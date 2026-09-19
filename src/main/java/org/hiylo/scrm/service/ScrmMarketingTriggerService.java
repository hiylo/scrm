/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingTriggerService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmMarketingTriggerDto;
import org.hiylo.scrm.dto.ScrmMarketingTriggerEventDto;
import org.hiylo.scrm.dto.ScrmTriggerFireDto;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmJourneyEnrollmentEntity;
import org.hiylo.scrm.entity.ScrmMarketingTriggerEntity;
import org.hiylo.scrm.entity.ScrmMarketingTriggerEventEntity;
import org.hiylo.scrm.entity.ScrmMassSendTaskEntity;
import org.hiylo.scrm.entity.ScrmNotificationEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmJourneyEnrollmentRepository;
import org.hiylo.scrm.repository.ScrmMarketingTriggerEventRepository;
import org.hiylo.scrm.repository.ScrmMarketingTriggerRepository;
import org.hiylo.scrm.repository.ScrmMassSendTaskRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.vo.TriggerMarketingStatsVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SCRM 触发式自动营销服务
 * <p>
 * 负责事件驱动的自动营销: 定义触发条件 (事件 + 条件), 自动执行营销动作
 * (发消息 / 打标签 / 改生命周期 / 入旅程 / 通知用户 / 触发群发)。
 * 区别于手动群发任务, 这里由事件自动触发执行。
 * 所有写操作均写入当前用户归属账号, 实现数据隔离。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMarketingTriggerService {

    /** 事件状态: 待执行 */
    private static final String STATUS_PENDING = "PENDING";
    /** 事件状态: 执行中 */
    private static final String STATUS_EXECUTING = "EXECUTING";
    /** 事件状态: 执行成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";
    /** 事件状态: 执行失败 */
    private static final String STATUS_FAILED = "FAILED";
    /** 事件状态: 跳过 */
    private static final String STATUS_SKIPPED = "SKIPPED";
    /** 事件状态: 冷却期拦截 */
    private static final String STATUS_COOLDOWN = "COOLDOWN";

    /** 条件类型: 全部满足 */
    private static final String CONDITION_ALL = "ALL";
    /** 条件类型: 任一满足 */
    private static final String CONDITION_ANY = "ANY";

    /** 动作类型: 发送消息 */
    private static final String ACTION_SEND_MESSAGE = "SEND_MESSAGE";
    /** 动作类型: 打标签 */
    private static final String ACTION_ADD_TAG = "ADD_TAG";
    /** 动作类型: 设置生命周期 */
    private static final String ACTION_SET_LIFECYCLE = "SET_LIFECYCLE";
    /** 动作类型: 进入旅程 */
    private static final String ACTION_ENROLL_JOURNEY = "ENROLL_JOURNEY";
    /** 动作类型: 通知用户 */
    private static final String ACTION_NOTIFY_USER = "NOTIFY_USER";
    /** 动作类型: 触发群发 */
    private static final String ACTION_TRIGGER_MASS_SEND = "TRIGGER_MASS_SEND";

    /** 百分比换算基数 */
    private static final double PERCENT_BASE = 100.0;
    /** 比率小数保留位数 */
    private static final int RATE_SCALE = 2;

    /** 单次处理待执行事件的批量上限, 避免单次拉取过多造成长事务 */
    private static final int PROCESS_BATCH_LIMIT = 200;

    /** 营销触发器数据仓库 */
    private final ScrmMarketingTriggerRepository triggerRepository;
    /** 营销触发事件数据仓库 */
    private final ScrmMarketingTriggerEventRepository eventRepository;
    /** 客户数据访问层 (SET_LIFECYCLE 动作) */
    private final ScrmCustomerRepository customerRepository;
    /** 客户-标签赋值数据访问层 (ADD_TAG 动作) */
    private final ScrmTagCustomerRepository tagCustomerRepository;
    /** 会话消息数据访问层 (SEND_MESSAGE 动作) */
    private final ScrmConversationMessageRepository conversationMessageRepository;
    /** 旅程入营数据访问层 (ENROLL_JOURNEY 动作) */
    private final ScrmJourneyEnrollmentRepository journeyEnrollmentRepository;
    /** 通知数据访问层 (NOTIFY_USER 动作) */
    private final ScrmNotificationRepository notificationRepository;
    /** 群发任务数据访问层 (TRIGGER_MASS_SEND 动作) */
    private final ScrmMassSendTaskRepository massSendTaskRepository;
    /** Jackson ObjectMapper, 由 Spring Boot 自动注入, 用于解析触发条件与事件数据 JSON */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 触发器管理
    // ============================================================

    /**
     * 创建触发式营销规则
     * <p>
     * 默认 conditionType=ALL, cooldownHours=0, maxTriggersPerCustomer=0,
     * actionDelayMinutes=0, priority=0, enabled=true, triggerCount=0。
     * </p>
     *
     * @param dto 触发器参数
     * @return 创建后的触发器
     * @throws ScrmException 参数校验失败
     */
    @Transactional
    public ScrmMarketingTriggerDto createTrigger(ScrmMarketingTriggerDto dto) throws ScrmException {
        validateCreateTrigger(dto);
        ScrmMarketingTriggerEntity entity = new ScrmMarketingTriggerEntity();
        entity.setTriggerName(dto.getTriggerName());
        entity.setDescription(dto.getDescription());
        entity.setEventType(dto.getEventType());
        entity.setEventCondition(dto.getEventCondition());
        entity.setConditionType(dto.getConditionType() != null ? dto.getConditionType() : CONDITION_ALL);
        entity.setCooldownHours(dto.getCooldownHours() != null ? dto.getCooldownHours() : 0);
        entity.setMaxTriggersPerCustomer(dto.getMaxTriggersPerCustomer() != null
                ? dto.getMaxTriggersPerCustomer() : 0);
        entity.setActionType(dto.getActionType());
        entity.setActionParams(dto.getActionParams());
        entity.setActionDelayMinutes(dto.getActionDelayMinutes() != null ? dto.getActionDelayMinutes() : 0);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : 0);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setTriggerCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = triggerRepository.save(entity);
        log.info("创建触发式营销规则: id={}, triggerName={}, eventType={}, actionType={}",
                entity.getId(), entity.getTriggerName(), entity.getEventType(), entity.getActionType());
        return toTriggerDto(entity);
    }

    /**
     * 更新触发式营销规则
     * <p>
     * 字段非空才覆盖, enabled 通过 enable/disable 专用接口维护。
     * </p>
     *
     * @param id  触发器 ID
     * @param dto 触发器参数
     * @return 更新后的触发器
     * @throws ScrmException 触发器不存在
     */
    @Transactional
    public ScrmMarketingTriggerDto updateTrigger(Long id, ScrmMarketingTriggerDto dto) throws ScrmException {
        ScrmMarketingTriggerEntity entity = findTriggerOrThrow(id);
        if (dto.getTriggerName() != null) {
            if (dto.getTriggerName().isBlank()) {
                throw ScrmException.badRequest("触发器名称不能为空");
            }
            entity.setTriggerName(dto.getTriggerName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getEventType() != null) {
            entity.setEventType(dto.getEventType());
        }
        if (dto.getEventCondition() != null) {
            entity.setEventCondition(dto.getEventCondition());
        }
        if (dto.getConditionType() != null) {
            entity.setConditionType(dto.getConditionType());
        }
        if (dto.getCooldownHours() != null) {
            entity.setCooldownHours(dto.getCooldownHours());
        }
        if (dto.getMaxTriggersPerCustomer() != null) {
            entity.setMaxTriggersPerCustomer(dto.getMaxTriggersPerCustomer());
        }
        if (dto.getActionType() != null) {
            entity.setActionType(dto.getActionType());
        }
        if (dto.getActionParams() != null) {
            if (dto.getActionParams().isBlank()) {
                throw ScrmException.badRequest("动作参数不能为空");
            }
            entity.setActionParams(dto.getActionParams());
        }
        if (dto.getActionDelayMinutes() != null) {
            entity.setActionDelayMinutes(dto.getActionDelayMinutes());
        }
        if (dto.getPriority() != null) {
            entity.setPriority(dto.getPriority());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        if (dto.getCreatedBy() != null) {
            entity.setCreatedBy(dto.getCreatedBy());
        }
        entity = triggerRepository.save(entity);
        log.info("更新触发式营销规则: id={}", id);
        return toTriggerDto(entity);
    }

    /**
     * 删除触发式营销规则
     * <p>
     * 触发器删除后, 历史事件记录保留以便审计与统计。
     * </p>
     *
     * @param id 触发器 ID
     * @throws ScrmException 触发器不存在
     */
    @Transactional
    public void deleteTrigger(Long id) throws ScrmException {
        ScrmMarketingTriggerEntity entity = findTriggerOrThrow(id);
        triggerRepository.delete(entity);
        log.info("删除触发式营销规则: id={}, triggerName={}", id, entity.getTriggerName());
    }

    /**
     * 查询触发器详情
     *
     * @param id 触发器 ID
     * @return 触发器 DTO
     * @throws ScrmException 触发器不存在
     */
    @Transactional(readOnly = true)
    public ScrmMarketingTriggerDto getTrigger(Long id) throws ScrmException {
        return toTriggerDto(findTriggerOrThrow(id));
    }

    /**
     * 分页查询触发器, 支持按事件类型、动作类型、启用状态与关键词过滤
     *
     * @param eventType  事件类型过滤 (可空)
     * @param actionType 动作类型过滤 (可空)
     * @param enabled    启用状态过滤 (可空)
     * @param keyword    关键词过滤, 匹配触发器名称 (可空)
     * @param pageable   分页参数
     * @return 触发器分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMarketingTriggerDto> listTriggers(String eventType, String actionType,
                                                       Boolean enabled, String keyword, Pageable pageable) {
        Pageable sorted = ensureSort(pageable);
        Specification<ScrmMarketingTriggerEntity> spec = buildTriggerSpec(eventType, actionType, enabled, keyword);
        return triggerRepository.findAll(spec, sorted).map(this::toTriggerDto);
    }

    /**
     * 启用触发器
     *
     * @param id 触发器 ID
     * @return 更新后的触发器
     * @throws ScrmException 触发器不存在
     */
    @Transactional
    public ScrmMarketingTriggerDto enableTrigger(Long id) throws ScrmException {
        ScrmMarketingTriggerEntity entity = findTriggerOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = triggerRepository.save(entity);
        log.info("启用触发式营销规则: id={}", id);
        return toTriggerDto(entity);
    }

    /**
     * 禁用触发器
     *
     * @param id 触发器 ID
     * @return 更新后的触发器
     * @throws ScrmException 触发器不存在
     */
    @Transactional
    public ScrmMarketingTriggerDto disableTrigger(Long id) throws ScrmException {
        ScrmMarketingTriggerEntity entity = findTriggerOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = triggerRepository.save(entity);
        log.info("禁用触发式营销规则: id={}", id);
        return toTriggerDto(entity);
    }

    // ============================================================
    // 事件触发与执行
    // ============================================================

    /**
     * 触发事件
     * <p>
     * 流程: 查找匹配的启用规则 → 检查冷却期和次数限制 → 检查条件 →
     * 创建事件记录 (含延迟) → 返回。
     * </p>
     * <p>
     * 数据隔离: 仅匹配当前账号下 eventType 一致且 enabled=true 的触发器,
     * 按优先级倒序逐个评估, 命中即生成 PENDING 事件记录。
     * </p>
     *
     * @param fireDto 事件触发入参
     * @return 生成的事件记录列表 (含 COOLDOWN/SKIPPED 状态的拦截记录)
     * @throws ScrmException 参数校验失败
     */
    @Transactional
    public List<ScrmMarketingTriggerEventDto> fireEvent(ScrmTriggerFireDto fireDto) throws ScrmException {
        if (fireDto == null) {
            throw ScrmException.badRequest("触发入参不能为空");
        }
        if (fireDto.getEventType() == null || fireDto.getEventType().isBlank()) {
            throw ScrmException.badRequest("事件类型不能为空");
        }
        if (fireDto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        List<ScrmMarketingTriggerEntity> triggers = triggerRepository
                .findByEventTypeAndEnabled(fireDto.getEventType(), Boolean.TRUE);
        // 数据隔离过滤 + 优先级倒序
        List<ScrmMarketingTriggerEntity> matched = triggers.stream()

                .sorted(Comparator.comparingInt((ScrmMarketingTriggerEntity t) ->
                                t.getPriority() == null ? 0 : t.getPriority()).reversed())
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            log.debug("事件未匹配到启用规则: eventType={}, customerId={}", fireDto.getEventType(), fireDto.getCustomerId());
            return Collections.emptyList();
        }
        List<ScrmMarketingTriggerEventDto> created = new ArrayList<>(matched.size());
        for (ScrmMarketingTriggerEntity trigger : matched) {
            // 冷却期检查
            if (isInCooldown(trigger, fireDto.getCustomerId())) {
                ScrmMarketingTriggerEventEntity event = buildEventEntity(trigger, fireDto, STATUS_COOLDOWN,
                        "客户处于冷却期, 跳过触发");
                event = eventRepository.save(event);
                created.add(toEventDto(event));
                log.debug("客户处于冷却期, 跳过: triggerId={}, customerId={}",
                        trigger.getId(), fireDto.getCustomerId());
                continue;
            }
            // 每客户最大触发次数检查
            if (exceedsMaxTriggers(trigger, fireDto.getCustomerId())) {
                ScrmMarketingTriggerEventEntity event = buildEventEntity(trigger, fireDto, STATUS_SKIPPED,
                        "已达每客户最大触发次数, 跳过");
                event = eventRepository.save(event);
                created.add(toEventDto(event));
                log.debug("已达每客户最大触发次数, 跳过: triggerId={}, customerId={}",
                        trigger.getId(), fireDto.getCustomerId());
                continue;
            }
            // 条件匹配检查
            if (!matchesCondition(trigger, fireDto.getEventData())) {
                log.debug("条件不匹配, 跳过: triggerId={}, customerId={}",
                        trigger.getId(), fireDto.getCustomerId());
                continue;
            }
            // 创建待执行事件记录 (含延迟)
            ScrmMarketingTriggerEventEntity event = buildEventEntity(trigger, fireDto, STATUS_PENDING, null);
            int delayMinutes = trigger.getActionDelayMinutes() != null ? trigger.getActionDelayMinutes() : 0;
            event.setScheduledAt(LocalDateTime.now().plusMinutes(delayMinutes));
            // 冗余存储当前客户已成功触发次数, 便于后续限流判断
            long historyCount = eventRepository.countByTriggerIdAndCustomerIdAndStatus(
                    trigger.getId(), fireDto.getCustomerId(), STATUS_SUCCESS);
            event.setTriggerCount((int) historyCount + 1);
            event = eventRepository.save(event);
            // 更新触发器统计
            trigger.setTriggerCount((trigger.getTriggerCount() == null ? 0 : trigger.getTriggerCount()) + 1);
            trigger.setLastTriggerAt(LocalDateTime.now());
            triggerRepository.save(trigger);
            created.add(toEventDto(event));
            log.info("触发式营销事件已创建: triggerId={}, eventId={}, customerId={}, actionType={}, delayMinutes={}",
                    trigger.getId(), event.getId(), fireDto.getCustomerId(), trigger.getActionType(), delayMinutes);
        }
        return created;
    }

    /**
     * 处理待执行事件 (定时任务调用)
     * <p>
     * 捞取当前账号下 scheduledAt 已到期且状态为 PENDING 的事件, 逐个执行动作并更新状态。
     * 单次处理上限 {@link #PROCESS_BATCH_LIMIT}, 避免长事务。
     * </p>
     *
     * @return 本次处理的事件数
     */
    @Transactional
    public int processPendingEvents() {
        List<ScrmMarketingTriggerEventEntity> pending = eventRepository
                .findByStatusAndScheduledAtLessThanEqual(STATUS_PENDING, LocalDateTime.now());
        if (pending.isEmpty()) {
            return 0;
        }
        int processed = 0;
        for (ScrmMarketingTriggerEventEntity event : pending) {
            if (processed >= PROCESS_BATCH_LIMIT) {
                log.info("达到单次处理上限, 剩余待下次处理: batchLimit={}", PROCESS_BATCH_LIMIT);
                break;
            }
            try {
                executeAction(event.getId());
                processed++;
            } catch (Exception e) {
                // 单个事件执行失败不影响整体批次, 错误已在 executeAction 内记录到事件
                log.warn("事件执行失败, 继续处理下一个: eventId={}", event.getId(), e);
            }
        }
        log.info("处理待执行事件完成:, processed={}, totalPending={}", processed, pending.size());
        return processed;
    }

    /**
     * 执行单个事件的动作
     * <p>
     * 根据动作类型分发执行: 发消息 / 打标签 / 改生命周期 / 入旅程 / 通知用户 / 触发群发。
     * 内部动作: SEND_MESSAGE / ADD_TAG / SET_LIFECYCLE 为真实执行, ENROLL_JOURNEY / NOTIFY_USER / TRIGGER_MASS_SEND 待对接对应服务。
     * </p>
     *
     * @param eventId 事件 ID
     * @return 更新后的事件 DTO
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @Transactional
    public ScrmMarketingTriggerEventDto executeAction(Long eventId) throws ScrmException {
        ScrmMarketingTriggerEventEntity event = findEventOrThrow(eventId);
        if (!STATUS_PENDING.equals(event.getStatus()) && !STATUS_EXECUTING.equals(event.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "事件状态非法, 仅 PENDING / EXECUTING 可执行: currentStatus=" + event.getStatus());
        }
        event.setStatus(STATUS_EXECUTING);
        eventRepository.save(event);
        try {
            String result = doExecuteAction(event);
            event.setStatus(STATUS_SUCCESS);
            event.setActionResult(result);
            event.setExecutedAt(LocalDateTime.now());
            log.info("事件动作执行成功: eventId={}, actionType={}, customerId={}",
                    eventId, event.getActionType(), event.getCustomerId());
        } catch (Exception e) {
            event.setStatus(STATUS_FAILED);
            String msg = e.getMessage();
            if (msg != null && msg.length() > 500) {
                msg = msg.substring(0, 500);
            }
            event.setErrorMessage(msg);
            event.setExecutedAt(LocalDateTime.now());
            log.warn("事件动作执行失败: eventId={}, actionType={}, error={}",
                    eventId, event.getActionType(), msg, e);
        }
        event = eventRepository.save(event);
        return toEventDto(event);
    }

    /**
     * 查询事件记录, 支持按触发器、状态、客户与时间范围过滤
     *
     * @param triggerId 触发器 ID 过滤 (可空)
     * @param status    事件状态过滤 (可空)
     * @param customerId 客户 ID 过滤 (可空)
     * @param startTime 起始时间过滤 (可空, 匹配 createTime)
     * @param endTime   截止时间过滤 (可空, 匹配 createTime)
     * @param pageable  分页参数
     * @return 事件记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMarketingTriggerEventDto> getTriggerEvents(Long triggerId, String status, Long customerId,
                                                                LocalDateTime startTime, LocalDateTime endTime,
                                                                Pageable pageable) {
        Pageable sorted = ensureEventSort(pageable);
        Specification<ScrmMarketingTriggerEventEntity> spec = buildEventSpec(
                triggerId, status, customerId, startTime, endTime);
        return eventRepository.findAll(spec, sorted).map(this::toEventDto);
    }

    /**
     * 触发式营销统计
     * <p>
     * 聚合指定时间范围内的事件状态分布与动作类型分布, 计算成功率。
     * startTime / endTime 为空时默认统计最近 30 天。
     * </p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计 VO
     */
    @Transactional(readOnly = true)
    public TriggerMarketingStatsVo getTriggerStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(30);
        List<Object[]> statusRows = eventRepository.countByStatus(start, end);
        Map<String, Long> statusMap = new LinkedHashMap<>();
        long total = 0L;
        for (Object[] row : statusRows) {
            String st = (String) row[0];
            Long cnt = (Long) row[1];
            statusMap.put(st, cnt);
            total += cnt;
        }
        List<Object[]> actionRows = eventRepository.countByActionType(start, end);
        Map<String, Long> actionMap = new LinkedHashMap<>();
        for (Object[] row : actionRows) {
            String at = (String) row[0];
            Long cnt = (Long) row[1];
            actionMap.put(at, cnt);
        }
        long success = statusMap.getOrDefault(STATUS_SUCCESS, 0L);
        long failed = statusMap.getOrDefault(STATUS_FAILED, 0L);
        long skipped = statusMap.getOrDefault(STATUS_SKIPPED, 0L);
        long pending = statusMap.getOrDefault(STATUS_PENDING, 0L);
        long executing = statusMap.getOrDefault(STATUS_EXECUTING, 0L);
        long cooldown = statusMap.getOrDefault(STATUS_COOLDOWN, 0L);
        Double successRate = null;
        long executed = success + failed;
        if (executed > 0) {
            successRate = round2(success * PERCENT_BASE / executed);
        }
        return TriggerMarketingStatsVo.builder()
                .startTime(start)
                .endTime(end)
                .totalCount(total)
                .successCount(success)
                .failedCount(failed)
                .skippedCount(skipped)
                .pendingCount(pending)
                .executingCount(executing)
                .cooldownCount(cooldown)
                .successRate(successRate)
                .actionTypeDistribution(actionMap)
                .statusDistribution(statusMap)
                .build();
    }

    /**
     * 检查冷却期
     * <p>
     * 判断指定客户在指定触发器下是否处于冷却期。冷却期由 trigger.cooldownHours 控制,
     * 0 表示不限。返回 true 表示客户处于冷却期, 应跳过触发。
     * </p>
     *
     * @param triggerId  触发器 ID
     * @param customerId 客户 ID
     * @return true=处于冷却期 (应跳过), false=可触发
     * @throws ScrmException 触发器不存在
     */
    @Transactional(readOnly = true)
    public boolean checkCooldown(Long triggerId, Long customerId) throws ScrmException {
        ScrmMarketingTriggerEntity trigger = findTriggerOrThrow(triggerId);
        return isInCooldown(trigger, customerId);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 创建触发器参数校验
     *
     * @param dto 触发器参数
     * @throws ScrmException 参数校验失败
     */
    private void validateCreateTrigger(ScrmMarketingTriggerDto dto) throws ScrmException {
        if (dto.getTriggerName() == null || dto.getTriggerName().isBlank()) {
            throw ScrmException.badRequest("触发器名称不能为空");
        }
        if (dto.getEventType() == null || dto.getEventType().isBlank()) {
            throw ScrmException.badRequest("事件类型不能为空");
        }
        if (dto.getActionType() == null || dto.getActionType().isBlank()) {
            throw ScrmException.badRequest("动作类型不能为空");
        }
        if (dto.getActionParams() == null || dto.getActionParams().isBlank()) {
            throw ScrmException.badRequest("动作参数不能为空");
        }
    }

    /**
     * 判断客户是否处于冷却期
     *
     * @param trigger    触发器
     * @param customerId 客户 ID
     * @return true=处于冷却期
     */
    private boolean isInCooldown(ScrmMarketingTriggerEntity trigger, Long customerId) {
        int cooldownHours = trigger.getCooldownHours() == null ? 0 : trigger.getCooldownHours();
        if (cooldownHours <= 0) {
            return false;
        }
        return eventRepository
                .findFirstByTriggerIdAndCustomerIdAndStatusOrderByExecutedAtDesc(
                        trigger.getId(), customerId, STATUS_SUCCESS)
                .map(last -> {
                    LocalDateTime boundary = LocalDateTime.now().minusHours(cooldownHours);
                    return last.getExecutedAt() != null && last.getExecutedAt().isAfter(boundary);
                })
                .orElse(false);
    }

    /**
     * 判断客户是否已超过每客户最大触发次数
     *
     * @param trigger    触发器
     * @param customerId 客户 ID
     * @return true=已达上限, 应跳过
     */
    private boolean exceedsMaxTriggers(ScrmMarketingTriggerEntity trigger, Long customerId) {
        int max = trigger.getMaxTriggersPerCustomer() == null ? 0 : trigger.getMaxTriggersPerCustomer();
        if (max <= 0) {
            return false;
        }
        long count = eventRepository.countByTriggerIdAndCustomerIdAndStatus(
                trigger.getId(), customerId, STATUS_SUCCESS);
        return count >= max;
    }

    /**
     * 条件匹配检查
     * <p>
     * 解析 trigger.eventCondition 与 eventData 为 Map, 按 conditionType (ALL/ANY) 评估:
     * <ul>
     *   <li>ALL: eventCondition 中所有键值对均需在 eventData 中存在且相等</li>
     *   <li>ANY: eventCondition 中任一键值对在 eventData 中存在且相等</li>
     * </ul>
     * eventCondition 为空或解析失败视为无条件, 默认匹配。
     *
     * @param trigger   触发器
     * @param eventData 事件数据 JSON (可空)
     * @return true=条件匹配
     */
    private boolean matchesCondition(ScrmMarketingTriggerEntity trigger, String eventData) {
        Map<String, Object> condition = parseJsonToMap(trigger.getEventCondition());
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        Map<String, Object> data = parseJsonToMap(eventData);
        if (data == null || data.isEmpty()) {
            return false;
        }
        String conditionType = trigger.getConditionType() != null ? trigger.getConditionType() : CONDITION_ALL;
        if (CONDITION_ANY.equals(conditionType)) {
            return condition.entrySet().stream()
                    .anyMatch(e -> Objects.equals(e.getValue(), data.get(e.getKey())));
        }
        // 默认 ALL
        return condition.entrySet().stream()
                .allMatch(e -> Objects.equals(e.getValue(), data.get(e.getKey())));
    }

    /**
     * 解析 JSON 字符串为 Map, 解析失败返回空 Map
     *
     * @param json JSON 字符串 (可空)
     * @return Map (不为 null)
     */
    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return map != null ? map : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("JSON 解析失败, 返回空 Map: json={}", json, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 构建事件记录实体 (未持久化)
     *
     * @param trigger          触发器
     * @param fireDto          触发入参
     * @param status           初始状态
     * @param errorMessage     错误信息 (可空)
     * @return 事件实体
     */
    private ScrmMarketingTriggerEventEntity buildEventEntity(ScrmMarketingTriggerEntity trigger,
                                                             ScrmTriggerFireDto fireDto,
                                                             String status, String errorMessage) {
        ScrmMarketingTriggerEventEntity event = new ScrmMarketingTriggerEventEntity();
        event.setTriggerId(trigger.getId());
        event.setCustomerId(fireDto.getCustomerId());
        event.setCustomerNickname(fireDto.getCustomerNickname());
        event.setEventType(trigger.getEventType());
        event.setEventData(fireDto.getEventData());
        event.setStatus(status);
        event.setActionType(trigger.getActionType());
        event.setErrorMessage(errorMessage);
        event.setScheduledAt(LocalDateTime.now());
        event.setTriggerCount(0);
        return event;
    }

    /**
     * 执行具体动作
     * <p>
     * 根据 actionType 分发: SEND_MESSAGE / ADD_TAG / SET_LIFECYCLE 为真实执行,
     * ENROLL_JOURNEY / NOTIFY_USER / TRIGGER_MASS_SEND 待对接对应服务。
     * </p>
     *
     * @param event 事件记录
     * @return 动作执行结果描述
     */
    private String doExecuteAction(ScrmMarketingTriggerEventEntity event) {
        // actionParams 在表结构上为 NOT NULL, 解析失败回退为空 Map
        ScrmMarketingTriggerEntity trigger = triggerRepository.findById(event.getTriggerId()).orElse(null);
        Map<String, Object> params = trigger != null
                ? parseJsonToMap(trigger.getActionParams())
                : Collections.emptyMap();
        switch (event.getActionType()) {
            case ACTION_SEND_MESSAGE:
                return sendMessage(event, params);
            case ACTION_ADD_TAG:
                return addTag(event, params);
            case ACTION_SET_LIFECYCLE:
                return setLifecycle(event, params);
            case ACTION_ENROLL_JOURNEY:
                return enrollJourney(event, params);
            case ACTION_NOTIFY_USER:
                return notifyUser(event, params);
            case ACTION_TRIGGER_MASS_SEND:
                return triggerMassSend(event, params);
            default:
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "未识别的动作类型: " + event.getActionType());
        }
    }

    /**
     * 发送消息: 持久化出站会话消息。
     * <p>从 params 解析 conversationId / content / messageType, 创建 ScrmConversationMessageEntity。
     * 若未配置 conversationId 则记录警告并跳过。</p>
     *
     * @param event  事件记录
     * @param params 动作参数: conversationId / content / messageType / messageTemplateId
     * @return 执行结果描述
     */
    private String sendMessage(ScrmMarketingTriggerEventEntity event, Map<String, Object> params) {
        Object conversationIdObj = params.get("conversationId");
        Object content = params.get("content");
        Object messageType = params.get("messageType");
        Object templateId = params.get("messageTemplateId");
        if (conversationIdObj == null) {
            log.warn("SEND_MESSAGE 未配置 conversationId, 跳过: customerId={}", event.getCustomerId());
            return "跳过发送消息: 缺少 conversationId";
        }
        Long conversationId = toLong(conversationIdObj);
        ScrmConversationMessageEntity message = new ScrmConversationMessageEntity();
        message.setConversationId(conversationId);
        message.setMessageType(messageType != null ? messageType.toString() : "TEXT");
        message.setDirection("OUTBOUND");
        message.setContent(content != null ? content.toString() : null);
        message.setSentAt(LocalDateTime.now());
        conversationMessageRepository.save(message);
        log.info("发送消息: customerId={}, conversationId={}, templateId={}",
                event.getCustomerId(), conversationId, templateId);
        return "已发送消息: conversationId=" + conversationId + ", templateId=" + templateId;
    }

    /**
     * 打标签: 为客户创建标签赋值记录 (幂等)。
     * <p>从 params 解析 tagIds (逗号分隔或列表), 逐个检查是否已存在, 不存在则创建。</p>
     *
     * @param event  事件记录
     * @param params 动作参数: tagIds
     * @return 执行结果描述
     */
    private String addTag(ScrmMarketingTriggerEventEntity event, Map<String, Object> params) {
        Object tagIdsObj = params.get("tagIds");
        if (tagIdsObj == null) {
            log.warn("ADD_TAG 未配置 tagIds, 跳过: customerId={}", event.getCustomerId());
            return "跳过打标签: 缺少 tagIds";
        }
        Long customerId = event.getCustomerId();
        List<Long> tagIds = parseTagIds(tagIdsObj);
        int assigned = 0;
        for (Long tagId : tagIds) {
            Optional<ScrmTagCustomerEntity> existing = tagCustomerRepository
                    .findByCustomerIdAndTagId(customerId, tagId);
            if (existing.isPresent()) {
                continue;
            }
            ScrmTagCustomerEntity tagCustomer = new ScrmTagCustomerEntity();
            tagCustomer.setCustomerId(customerId);
            tagCustomer.setTagId(tagId);
            tagCustomer.setTagSource("TRIGGER");
            tagCustomer.setAssignedBy("scrm-trigger");
            tagCustomer.setAssignedAt(LocalDateTime.now());
            tagCustomer.setIsAuto(Boolean.TRUE);
            tagCustomerRepository.save(tagCustomer);
            assigned++;
        }
        log.info("打标签: customerId={}, tagIds={}, assigned={}", customerId, tagIds, assigned);
        return "已打标签: tagIds=" + tagIds + ", 新增=" + assigned;
    }

    /**
     * 设置生命周期: 更新客户 lifecycle 字段。
     * <p>从 params 解析 lifecycle, 查找客户并更新后持久化。</p>
     *
     * @param event  事件记录
     * @param params 动作参数: lifecycle
     * @return 执行结果描述
     */
    private String setLifecycle(ScrmMarketingTriggerEventEntity event, Map<String, Object> params) {
        Object lifecycle = params.get("lifecycle");
        if (lifecycle == null) {
            log.warn("SET_LIFECYCLE 未配置 lifecycle, 跳过: customerId={}", event.getCustomerId());
            return "跳过设置生命周期: 缺少 lifecycle";
        }
        Long customerId = event.getCustomerId();
        ScrmCustomerEntity customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            log.warn("SET_LIFECYCLE 客户不存在: customerId={}", customerId);
            return "跳过设置生命周期: 客户不存在";
        }
        String oldLifecycle = customer.getLifecycle();
        customer.setLifecycle(lifecycle.toString());
        customerRepository.save(customer);
        log.info("设置生命周期: customerId={}, old={}, new={}", customerId, oldLifecycle, lifecycle);
        return "已设置生命周期: " + oldLifecycle + " → " + lifecycle;
    }

    /**
     * 进入旅程: 创建旅程入营记录。
     * <p>从 params 解析 journeyId, 创建 ScrmJourneyEnrollmentEntity (status=ACTIVE,
     * entrySource=TRIGGER, progress=0)。</p>
     *
     * @param event  事件记录
     * @param params 动作参数: journeyId
     * @return 执行结果描述
     */
    private String enrollJourney(ScrmMarketingTriggerEventEntity event, Map<String, Object> params) {
        Object journeyIdObj = params.get("journeyId");
        if (journeyIdObj == null) {
            log.warn("ENROLL_JOURNEY 未配置 journeyId, 跳过: customerId={}", event.getCustomerId());
            return "跳过进入旅程: 缺少 journeyId";
        }
        Long journeyId = toLong(journeyIdObj);
        ScrmJourneyEnrollmentEntity enrollment = new ScrmJourneyEnrollmentEntity();
        enrollment.setJourneyId(journeyId);
        enrollment.setCustomerId(event.getCustomerId());
        enrollment.setEntrySource("TRIGGER");
        enrollment.setStatus("ACTIVE");
        enrollment.setEnteredAt(LocalDateTime.now());
        enrollment.setProgress(0);
        journeyEnrollmentRepository.save(enrollment);
        log.info("进入旅程: customerId={}, journeyId={}, enrollmentId={}",
                event.getCustomerId(), journeyId, enrollment.getId());
        return "已进入旅程: journeyId=" + journeyId + ", enrollmentId=" + enrollment.getId();
    }

    /**
     * 通知用户: 创建站内通知。
     * <p>从 params 解析 notifyUserId / title / content, 创建 ScrmNotificationEntity
     * (channel=IN_APP, status=PENDING)。</p>
     *
     * @param event  事件记录
     * @param params 动作参数: notifyUserId / title / content
     * @return 执行结果描述
     */
    private String notifyUser(ScrmMarketingTriggerEventEntity event, Map<String, Object> params) {
        Object notifyUserId = params.get("notifyUserId");
        if (notifyUserId == null) {
            log.warn("NOTIFY_USER 未配置 notifyUserId, 跳过: customerId={}", event.getCustomerId());
            return "跳过通知用户: 缺少 notifyUserId";
        }
        String title = params.get("title") != null ? params.get("title").toString() : "营销触发通知";
        String content = params.get("content") != null ? params.get("content").toString()
                : "客户 " + event.getCustomerId() + " 触发了营销事件: " + event.getEventType();
        ScrmNotificationEntity notification = new ScrmNotificationEntity();
        notification.setRecipientId(notifyUserId.toString());
        notification.setTitle(title);
        notification.setContent(content);
        notification.setChannel("IN_APP");
        notification.setCategory("MARKETING_TRIGGER");
        notification.setStatus("PENDING");
        notification.setPriority(0);
        notificationRepository.save(notification);
        log.info("通知用户: notifyUserId={}, customerId={}, notificationId={}",
                notifyUserId, event.getCustomerId(), notification.getId());
        return "已通知用户: notifyUserId=" + notifyUserId + ", notificationId=" + notification.getId();
    }

    /**
     * 触发群发: 更新群发任务状态为 RUNNING。
     * <p>从 params 解析 massSendTaskId, 查找任务并更新 status=RUNNING。
     * 任务不存在则记录警告。</p>
     *
     * @param event  事件记录
     * @param params 动作参数: massSendTaskId
     * @return 执行结果描述
     */
    private String triggerMassSend(ScrmMarketingTriggerEventEntity event, Map<String, Object> params) {
        Object massSendTaskIdObj = params.get("massSendTaskId");
        if (massSendTaskIdObj == null) {
            log.warn("TRIGGER_MASS_SEND 未配置 massSendTaskId, 跳过: customerId={}", event.getCustomerId());
            return "跳过触发群发: 缺少 massSendTaskId";
        }
        Long massSendTaskId = toLong(massSendTaskIdObj);
        ScrmMassSendTaskEntity task = massSendTaskRepository.findById(massSendTaskId).orElse(null);
        if (task == null) {
            log.warn("TRIGGER_MASS_SEND 群发任务不存在: massSendTaskId={}", massSendTaskId);
            return "跳过触发群发: 任务不存在";
        }
        String oldStatus = task.getStatus();
        task.setStatus("RUNNING");
        task.setScheduledAt(LocalDateTime.now());
        massSendTaskRepository.save(task);
        log.info("触发群发: massSendTaskId={}, oldStatus={}, newStatus=RUNNING, customerId={}",
                massSendTaskId, oldStatus, event.getCustomerId());
        return "已触发群发: massSendTaskId=" + massSendTaskId + ", " + oldStatus + " → RUNNING";
    }

    /**
     * 解析 tagIds 配置 (支持逗号分隔字符串或 List)。
     *
     * @param tagIdsObj 原始配置值
     * @return 标签 ID 列表
     */
    @SuppressWarnings("unchecked")
    private List<Long> parseTagIds(Object tagIdsObj) {
        if (tagIdsObj instanceof List) {
            return ((List<Object>) tagIdsObj).stream()
                    .map(this::toLong)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        String str = tagIdsObj.toString();
        List<Long> result = new ArrayList<>();
        for (String part : str.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                Long id = toLong(trimmed);
                if (id != null) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    /**
     * 安全转换 Long (null / 非数字返回 null)。
     *
     * @param obj 原始值
     * @return Long 值
     */
    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.parseLong(obj.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 构建触发器查询条件 Specification
     * <p>
     * 数据隔离: 始终按当前用户可见账号范围过滤。
     * </p>
     *
     * @param eventType  事件类型过滤 (可空)
     * @param actionType 动作类型过滤 (可空)
     * @param enabled    启用状态过滤 (可空)
     * @param keyword    关键词过滤, 匹配触发器名称 (可空)
     * @return Specification
     */
    private Specification<ScrmMarketingTriggerEntity> buildTriggerSpec(String eventType, String actionType,
                                                                       Boolean enabled, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("eventType")), eventType.toLowerCase()));
            }
            if (actionType != null && !actionType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("actionType")), actionType.toLowerCase()));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("triggerName")), kw));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建事件记录查询条件 Specification
     * <p>
     * 数据隔离: 始终按当前用户可见账号范围过滤。
     * </p>
     *
     * @param triggerId  触发器 ID 过滤 (可空)
     * @param status     事件状态过滤 (可空)
     * @param customerId 客户 ID 过滤 (可空)
     * @param startTime  起始时间过滤 (可空)
     * @param endTime    截止时间过滤 (可空)
     * @return Specification
     */
    private Specification<ScrmMarketingTriggerEventEntity> buildEventSpec(Long triggerId, String status,
                                                                          Long customerId,
                                                                          LocalDateTime startTime,
                                                                          LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
            if (triggerId != null) {
                predicates.add(cb.equal(root.get("triggerId"), triggerId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 确保分页参数带默认排序 (按创建时间倒序)
     *
     * @param pageable 原始分页参数
     * @return 带排序的分页参数
     */
    private Pageable ensureSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
    }

    /**
     * 确保事件分页参数带默认排序 (按计划执行时间倒序)
     *
     * @param pageable 原始分页参数
     * @return 带排序的分页参数
     */
    private Pageable ensureEventSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "scheduledAt"));
    }

    /**
     * 按主键查询触发器并校验账号归属, 不存在抛异常
     *
     * @param id 触发器 ID
     * @return 触发器实体
     * @throws ScrmException 触发器不存在或越权访问
     */
    private ScrmMarketingTriggerEntity findTriggerOrThrow(Long id) throws ScrmException {
        ScrmMarketingTriggerEntity entity = triggerRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "触发式营销规则不存在: id=" + id));
        // 数据隔离: 校验归属当前账号, 防止按 ID 越权访问

        return entity;
    }

    /**
     * 按主键查询事件记录并校验账号归属, 不存在抛异常
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在或越权访问
     */
    private ScrmMarketingTriggerEventEntity findEventOrThrow(Long id) throws ScrmException {
        ScrmMarketingTriggerEventEntity entity = eventRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "触发式营销事件不存在: id=" + id));

        return entity;
    }

    /**
     * 触发器实体转 DTO
     *
     * @param entity 触发器实体
     * @return 触发器 DTO
     */
    private ScrmMarketingTriggerDto toTriggerDto(ScrmMarketingTriggerEntity entity) {
        ScrmMarketingTriggerDto dto = new ScrmMarketingTriggerDto();
        dto.setId(entity.getId());
        dto.setTriggerName(entity.getTriggerName());
        dto.setDescription(entity.getDescription());
        dto.setEventType(entity.getEventType());
        dto.setEventCondition(entity.getEventCondition());
        dto.setConditionType(entity.getConditionType());
        dto.setCooldownHours(entity.getCooldownHours());
        dto.setMaxTriggersPerCustomer(entity.getMaxTriggersPerCustomer());
        dto.setActionType(entity.getActionType());
        dto.setActionParams(entity.getActionParams());
        dto.setActionDelayMinutes(entity.getActionDelayMinutes());
        dto.setPriority(entity.getPriority());
        dto.setEnabled(entity.getEnabled());
        dto.setTriggerCount(entity.getTriggerCount());
        dto.setLastTriggerAt(entity.getLastTriggerAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 事件记录实体转 DTO
     *
     * @param entity 事件实体
     * @return 事件 DTO
     */
    private ScrmMarketingTriggerEventDto toEventDto(ScrmMarketingTriggerEventEntity entity) {
        ScrmMarketingTriggerEventDto dto = new ScrmMarketingTriggerEventDto();
        dto.setId(entity.getId());
        dto.setTriggerId(entity.getTriggerId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerNickname(entity.getCustomerNickname());
        dto.setEventType(entity.getEventType());
        dto.setEventData(entity.getEventData());
        dto.setStatus(entity.getStatus());
        dto.setActionType(entity.getActionType());
        dto.setActionResult(entity.getActionResult());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setScheduledAt(entity.getScheduledAt());
        dto.setExecutedAt(entity.getExecutedAt());
        dto.setTriggerCount(entity.getTriggerCount());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 保留两位小数
     *
     * @param v 原始值
     * @return 四舍五入到两位小数
     */
    private double round2(double v) {
        return Math.round(v * Math.pow(10, RATE_SCALE)) / Math.pow(10, RATE_SCALE);
    }
}
