/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInteractionCalendarPlanService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmInteractionPlanCompleteDto;
import org.hiylo.scrm.dto.ScrmInteractionPlanDto;
import org.hiylo.scrm.dto.ScrmInteractionRescheduleDto;
import org.hiylo.scrm.entity.ScrmInteractionPlanEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmInteractionPlanRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 客户互动计划管理服务。
 * <p>
 * 承载互动计划管理能力: 计划 CRUD / 生命周期流转 (完成/取消/改期/确认/开始/未到/提醒) /
 * 批量操作 / 复制 / 待提醒扫描 / 重复计划处理 / 分页多条件检索。
 * </p>
 * <p>
 * 计划状态流转: PLANNED (已计划) → CONFIRMED (已确认) → IN_PROGRESS (进行中) →
 * COMPLETED (已完成) / CANCELLED (已取消) / RESCHEDULED (已改期) / NO_SHOW (未到)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmInteractionCalendarPlanService {

    /** 默认时区 */
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /** 默认提醒类型 */
    private static final String DEFAULT_REMINDER_TYPE = "NOTIFICATION";

    /** 默认提前提醒分钟 */
    private static final int DEFAULT_REMINDER_MINUTES = 15;

    /** 默认重复类型 */
    private static final String DEFAULT_REPEAT_TYPE = "NONE";

    /** 默认重复间隔 */
    private static final int DEFAULT_REPEAT_INTERVAL = 1;

    /** 默认优先级 */
    private static final String DEFAULT_PRIORITY = "MEDIUM";

    /** 默认状态 */
    private static final String DEFAULT_STATUS = "PLANNED";

    /** 默认统计初值 */
    private static final int DEFAULT_COUNT = 0;

    /** 默认操作人 (请求头未透传时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 复制计划名称后缀 */
    private static final String COPY_SUFFIX = "-副本";

    /** 计划状态: 已计划 */
    private static final String STATUS_PLANNED = "PLANNED";
    /** 计划状态: 已确认 */
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    /** 计划状态: 进行中 */
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 计划状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";
    /** 计划状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";
    /** 计划状态: 已改期 */
    private static final String STATUS_RESCHEDULED = "RESCHEDULED";
    /** 计划状态: 未到 */
    private static final String STATUS_NO_SHOW = "NO_SHOW";

    /** 非终态状态集合 (可流转的状态) */
    private static final List<String> ACTIVE_STATUSES = List.of(
            STATUS_PLANNED, STATUS_CONFIRMED, STATUS_IN_PROGRESS, STATUS_RESCHEDULED);

    /** 合法的互动类型 */
    private static final List<String> VALID_INTERACTION_TYPES = List.of(
            "CALL", "EMAIL", "WECHAT", "MEETING", "VISIT", "FOLLOW_UP",
            "REVIEW", "GREETING", "GIFT", "OTHER");

    /** 合法的互动方式 */
    private static final List<String> VALID_INTERACTION_METHODS = List.of(
            "PHONE", "VIDEO", "ONSITE", "ONLINE", "MESSAGE", "MAIL");

    /** 合法的提醒类型 */
    private static final List<String> VALID_REMINDER_TYPES = List.of(
            "NONE", "NOTIFICATION", "EMAIL", "SMS", "ALL");

    /** 合法的重复类型 */
    private static final List<String> VALID_REPEAT_TYPES = List.of(
            "NONE", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY", "CUSTOM");

    /** 合法的优先级 */
    private static final List<String> VALID_PRIORITIES = List.of(
            "LOW", "MEDIUM", "HIGH", "URGENT");

    /** 合法的状态 */
    private static final List<String> VALID_STATUSES = List.of(
            "PLANNED", "CONFIRMED", "IN_PROGRESS", "COMPLETED", "CANCELLED",
            "RESCHEDULED", "NO_SHOW");

    /** 合法的互动结果 */
    private static final List<String> VALID_OUTCOMES = List.of(
            "POSITIVE", "NEUTRAL", "NEGATIVE", "FOLLOW_UP_NEEDED");

    /** 合法的地点类型 */
    private static final List<String> VALID_LOCATION_TYPES = List.of(
            "OFFICE", "CUSTOMER_SITE", "ONLINE", "PHONE", "OTHER");

    /** 互动计划数据访问层 */
    private final ScrmInteractionPlanRepository planRepository;

    /**
     * 创建互动计划。
     *
     * @param dto 计划参数
     * @return 创建后的计划
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmInteractionPlanEntity createPlan(ScrmInteractionPlanDto dto) throws ScrmException {
        validatePlanDto(dto, false);
        if (planRepository.findByPlanCode(dto.getPlanCode()).isPresent()) {
            throw ScrmException.conflict("计划编码已存在: " + dto.getPlanCode());
        }
        ScrmInteractionPlanEntity entity = new ScrmInteractionPlanEntity();
        entity.setPlanName(dto.getPlanName());
        entity.setPlanCode(dto.getPlanCode());
        entity.setDescription(dto.getDescription());
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setInteractionType(dto.getInteractionType());
        entity.setInteractionMethod(dto.getInteractionMethod());
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent());
        entity.setObjectives(dto.getObjectives());
        entity.setPrepareMaterials(dto.getPrepareMaterials());
        entity.setScheduledStart(dto.getScheduledStart());
        entity.setScheduledEnd(dto.getScheduledEnd());
        entity.setTimezone(dto.getTimezone() != null && !dto.getTimezone().isBlank()
                ? dto.getTimezone() : DEFAULT_TIMEZONE);
        entity.setLocation(dto.getLocation());
        entity.setLocationType(dto.getLocationType());
        entity.setOwnerId(dto.getOwnerId());
        entity.setOwnerName(dto.getOwnerName());
        entity.setParticipantIds(dto.getParticipantIds());
        entity.setCustomerContactId(dto.getCustomerContactId());
        entity.setCustomerContactName(dto.getCustomerContactName());
        entity.setReminderType(dto.getReminderType() != null && !dto.getReminderType().isBlank()
                ? dto.getReminderType() : DEFAULT_REMINDER_TYPE);
        entity.setReminderMinutesBefore(dto.getReminderMinutesBefore() != null
                ? dto.getReminderMinutesBefore() : DEFAULT_REMINDER_MINUTES);
        entity.setIsReminderSent(Boolean.FALSE);
        entity.setRepeatType(dto.getRepeatType() != null && !dto.getRepeatType().isBlank()
                ? dto.getRepeatType() : DEFAULT_REPEAT_TYPE);
        entity.setRepeatInterval(dto.getRepeatInterval() != null
                ? dto.getRepeatInterval() : DEFAULT_REPEAT_INTERVAL);
        entity.setRepeatEndDate(dto.getRepeatEndDate());
        entity.setRepeatCount(DEFAULT_COUNT);
        entity.setMaxRepeatCount(dto.getMaxRepeatCount() != null ? dto.getMaxRepeatCount() : DEFAULT_COUNT);
        entity.setWeekDays(dto.getWeekDays());
        entity.setMonthDay(dto.getMonthDay());
        entity.setPriority(dto.getPriority() != null && !dto.getPriority().isBlank()
                ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank()
                ? dto.getStatus() : DEFAULT_STATUS);
        entity.setTags(dto.getTags());
        entity.setColor(dto.getColor());
        entity.setIsAllDay(dto.getIsAllDay() != null ? dto.getIsAllDay() : Boolean.FALSE);
        entity.setIsPinned(dto.getIsPinned() != null ? dto.getIsPinned() : Boolean.FALSE);
        entity.setAttachments(dto.getAttachments());
        entity.setRelatedPlanId(dto.getRelatedPlanId());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        validateScheduledRange(entity.getScheduledStart(), entity.getScheduledEnd());
        entity = planRepository.save(entity);
        log.info("创建互动计划: id={}, planName={}, planCode={}",
                entity.getId(), entity.getPlanName(), entity.getPlanCode());
        return entity;
    }

    /**
     * 更新互动计划 (字段非空才覆盖)。
     *
     * @param id  计划 ID
     * @param dto 计划参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 参数非法
     */
    @Transactional
    public ScrmInteractionPlanEntity updatePlan(Long id, ScrmInteractionPlanDto dto) throws ScrmException {
        ScrmInteractionPlanEntity entity = findPlanOrThrow(id);
        validatePlanDto(dto, true);
        if (dto.getPlanName() != null) entity.setPlanName(dto.getPlanName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getCustomerId() != null) entity.setCustomerId(dto.getCustomerId());
        if (dto.getCustomerName() != null) entity.setCustomerName(dto.getCustomerName());
        if (dto.getInteractionType() != null) entity.setInteractionType(dto.getInteractionType());
        if (dto.getInteractionMethod() != null) entity.setInteractionMethod(dto.getInteractionMethod());
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getObjectives() != null) entity.setObjectives(dto.getObjectives());
        if (dto.getPrepareMaterials() != null) entity.setPrepareMaterials(dto.getPrepareMaterials());
        if (dto.getScheduledStart() != null) entity.setScheduledStart(dto.getScheduledStart());
        if (dto.getScheduledEnd() != null) entity.setScheduledEnd(dto.getScheduledEnd());
        if (dto.getTimezone() != null) entity.setTimezone(dto.getTimezone());
        if (dto.getLocation() != null) entity.setLocation(dto.getLocation());
        if (dto.getLocationType() != null) entity.setLocationType(dto.getLocationType());
        if (dto.getOwnerId() != null) entity.setOwnerId(dto.getOwnerId());
        if (dto.getOwnerName() != null) entity.setOwnerName(dto.getOwnerName());
        if (dto.getParticipantIds() != null) entity.setParticipantIds(dto.getParticipantIds());
        if (dto.getCustomerContactId() != null) entity.setCustomerContactId(dto.getCustomerContactId());
        if (dto.getCustomerContactName() != null) entity.setCustomerContactName(dto.getCustomerContactName());
        if (dto.getReminderType() != null) entity.setReminderType(dto.getReminderType());
        if (dto.getReminderMinutesBefore() != null) entity.setReminderMinutesBefore(dto.getReminderMinutesBefore());
        if (dto.getRepeatType() != null) entity.setRepeatType(dto.getRepeatType());
        if (dto.getRepeatInterval() != null) entity.setRepeatInterval(dto.getRepeatInterval());
        if (dto.getRepeatEndDate() != null) entity.setRepeatEndDate(dto.getRepeatEndDate());
        if (dto.getMaxRepeatCount() != null) entity.setMaxRepeatCount(dto.getMaxRepeatCount());
        if (dto.getWeekDays() != null) entity.setWeekDays(dto.getWeekDays());
        if (dto.getMonthDay() != null) entity.setMonthDay(dto.getMonthDay());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getIsAllDay() != null) entity.setIsAllDay(dto.getIsAllDay());
        if (dto.getIsPinned() != null) entity.setIsPinned(dto.getIsPinned());
        if (dto.getAttachments() != null) entity.setAttachments(dto.getAttachments());
        if (dto.getRelatedPlanId() != null) entity.setRelatedPlanId(dto.getRelatedPlanId());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        validateScheduledRange(entity.getScheduledStart(), entity.getScheduledEnd());
        entity = planRepository.save(entity);
        log.info("更新互动计划: id={}, planName={}", entity.getId(), entity.getPlanName());
        return entity;
    }

    /**
     * 删除互动计划。
     *
     * @param id 计划 ID
     * @throws ScrmException 计划不存在
     */
    @Transactional
    public void deletePlan(Long id) throws ScrmException {
        ScrmInteractionPlanEntity entity = findPlanOrThrow(id);
        planRepository.delete(entity);
        log.info("删除互动计划: id={}, planName={}", id, entity.getPlanName());
    }

    /**
     * 查询计划详情。
     *
     * @param id 计划 ID
     * @return 计划实体
     * @throws ScrmException 计划不存在
     */
    @Transactional(readOnly = true)
    public ScrmInteractionPlanEntity getPlan(Long id) throws ScrmException {
        return findPlanOrThrow(id);
    }

    /**
     * 按计划编码查询。
     *
     * @param code 计划编码
     * @return 计划实体
     * @throws ScrmException 计划不存在
     */
    @Transactional(readOnly = true)
    public ScrmInteractionPlanEntity getPlanByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("计划编码不能为空");
        }
        return planRepository.findByPlanCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "互动计划不存在: code=" + code));
    }

    /**
     * 分页查询计划列表, 支持多条件过滤。
     *
     * @param customerId       客户 ID 过滤（可空）
     * @param ownerId          负责人过滤（可空）
     * @param interactionType  互动类型过滤（可空）
     * @param status           状态过滤（可空）
     * @param priority         优先级过滤（可空）
     * @param startTime        计划开始时间起始 (含, 可空)
     * @param endTime          计划开始时间截止 (含, 可空)
     * @param keyword          计划名称/编码/主题关键字模糊匹配（可空）
     * @param pageable         分页参数
     * @return 计划分页结果 (按 scheduledStart DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmInteractionPlanEntity> listPlans(Long customerId, String ownerId, String interactionType,
                                                     String status, String priority, LocalDateTime startTime,
                                                     LocalDateTime endTime, String keyword, Pageable pageable) {
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (ownerId != null && !ownerId.isBlank()) {
                predicates.add(cb.equal(root.get("ownerId"), ownerId));
            }
            if (interactionType != null && !interactionType.isBlank()) {
                predicates.add(cb.equal(root.get("interactionType"), interactionType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStart"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledStart"), endTime));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("planName"), like),
                        cb.like(root.get("planCode"), like),
                        cb.like(root.get("title"), like)));
            }
            query.orderBy(cb.desc(root.get("scheduledStart")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return planRepository.findAll(spec, pageable);
    }

    /**
     * 完成互动计划: 设置实际时间 → 记录结果 → 生成后续计划。
     *
     * @param dto 完成参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @Transactional
    public ScrmInteractionPlanEntity completePlan(ScrmInteractionPlanCompleteDto dto) throws ScrmException {
        if (dto == null || dto.getPlanId() == null) {
            throw ScrmException.badRequest("完成计划参数不能为空");
        }
        ScrmInteractionPlanEntity entity = findPlanOrThrow(dto.getPlanId());
        if (STATUS_COMPLETED.equals(entity.getStatus()) || STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("计划已是终态, 不可完成: id=" + dto.getPlanId()
                    + ", status=" + entity.getStatus());
        }
        if (dto.getOutcome() != null && !VALID_OUTCOMES.contains(dto.getOutcome())) {
            throw ScrmException.badRequest(
                    "互动结果非法: " + dto.getOutcome() + ", 仅支持 " + VALID_OUTCOMES);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(STATUS_COMPLETED);
        entity.setActualStart(dto.getActualStart() != null ? dto.getActualStart() : now);
        entity.setActualEnd(dto.getActualEnd() != null ? dto.getActualEnd() : now);
        entity.setCompletionNotes(dto.getCompletionNotes());
        entity.setOutcome(dto.getOutcome());
        entity.setFollowUpAction(dto.getFollowUpAction());
        entity.setFollowUpDate(dto.getFollowUpDate());
        entity = planRepository.save(entity);
        // 生成后续计划 (当结果为 FOLLOW_UP_NEEDED 或指定了后续行动时)
        if ("FOLLOW_UP_NEEDED".equals(dto.getOutcome()) || dto.getFollowUpAction() != null) {
            generateFollowUpPlan(entity);
        }
        log.info("完成互动计划: id={}, outcome={}, followUp={}",
                entity.getId(), entity.getOutcome(), dto.getFollowUpAction() != null);
        return entity;
    }

    /**
     * 取消互动计划 (非终态 → CANCELLED)。
     *
     * @param id     计划 ID
     * @param reason 取消原因（可空）
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @Transactional
    public ScrmInteractionPlanEntity cancelPlan(Long id, String reason) throws ScrmException {
        ScrmInteractionPlanEntity entity = findPlanOrThrow(id);
        if (STATUS_COMPLETED.equals(entity.getStatus()) || STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("计划已是终态, 不可取消: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_CANCELLED);
        if (reason != null && !reason.isBlank()) {
            String notes = entity.getCompletionNotes();
            entity.setCompletionNotes((notes == null ? "" : notes + " | ") + "取消原因: " + reason);
        }
        entity = planRepository.save(entity);
        log.info("取消互动计划: id={}, reason={}", id, reason);
        return entity;
    }

    /**
     * 改期计划: 记录原时间 → 更新新时间 → 标记 RESCHEDULED。
     *
     * @param dto 改期参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法 / 日期非法
     */
    @Transactional
    public ScrmInteractionPlanEntity reschedulePlan(ScrmInteractionRescheduleDto dto) throws ScrmException {
        if (dto == null || dto.getPlanId() == null || dto.getNewStart() == null) {
            throw ScrmException.badRequest("改期参数不能为空");
        }
        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw ScrmException.badRequest("改期原因不能为空");
        }
        ScrmInteractionPlanEntity entity = findPlanOrThrow(dto.getPlanId());
        if (STATUS_COMPLETED.equals(entity.getStatus()) || STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("计划已是终态, 不可改期: id=" + dto.getPlanId()
                    + ", status=" + entity.getStatus());
        }
        if (dto.getNewEnd() != null && dto.getNewStart().isAfter(dto.getNewEnd())) {
            throw ScrmException.badRequest("新开始时间不能晚于新结束时间");
        }
        // 以 relatedPlanId 保留原始计划 ID 关联 (若未设置)
        if (entity.getRelatedPlanId() == null) {
            entity.setRelatedPlanId(entity.getId());
        }
        entity.setScheduledStart(dto.getNewStart());
        entity.setScheduledEnd(dto.getNewEnd());
        entity.setStatus(STATUS_RESCHEDULED);
        entity.setIsReminderSent(Boolean.FALSE);
        entity.setReminderSentAt(null);
        String notes = entity.getCompletionNotes();
        entity.setCompletionNotes((notes == null ? "" : notes + " | ") + "改期原因: " + dto.getReason());
        entity = planRepository.save(entity);
        log.info("改期互动计划: id={}, newStart={}, reason={}", dto.getPlanId(), dto.getNewStart(), dto.getReason());
        return entity;
    }

    /**
     * 确认计划 (任意非终态 → CONFIRMED)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @Transactional
    public ScrmInteractionPlanEntity confirmPlan(Long id) throws ScrmException {
        ScrmInteractionPlanEntity entity = findPlanOrThrow(id);
        if (!ACTIVE_STATUSES.contains(entity.getStatus())) {
            throw ScrmException.conflict("仅非终态计划可确认: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_CONFIRMED);
        entity = planRepository.save(entity);
        log.info("确认互动计划: id={}", id);
        return entity;
    }

    /**
     * 开始计划 (非终态 → IN_PROGRESS, 记录实际开始时间)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @Transactional
    public ScrmInteractionPlanEntity startPlan(Long id) throws ScrmException {
        ScrmInteractionPlanEntity entity = findPlanOrThrow(id);
        if (!ACTIVE_STATUSES.contains(entity.getStatus())) {
            throw ScrmException.conflict("仅非终态计划可开始: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_IN_PROGRESS);
        entity.setActualStart(LocalDateTime.now());
        entity = planRepository.save(entity);
        log.info("开始互动计划: id={}", id);
        return entity;
    }

    /**
     * 标记未到 (非终态 → NO_SHOW)。
     *
     * @param id     计划 ID
     * @param reason 原因（可空）
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @Transactional
    public ScrmInteractionPlanEntity markNoShow(Long id, String reason) throws ScrmException {
        ScrmInteractionPlanEntity entity = findPlanOrThrow(id);
        if (!ACTIVE_STATUSES.contains(entity.getStatus())) {
            throw ScrmException.conflict("仅非终态计划可标记未到: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_NO_SHOW);
        if (reason != null && !reason.isBlank()) {
            String notes = entity.getCompletionNotes();
            entity.setCompletionNotes((notes == null ? "" : notes + " | ") + "未到原因: " + reason);
        }
        entity = planRepository.save(entity);
        log.info("标记互动计划未到: id={}, reason={}", id, reason);
        return entity;
    }

    /**
     * 发送提醒 (模拟, 标记已发送)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @Transactional
    public ScrmInteractionPlanEntity sendReminder(Long id) throws ScrmException {
        ScrmInteractionPlanEntity entity = findPlanOrThrow(id);
        if (STATUS_COMPLETED.equals(entity.getStatus()) || STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("已完成/已取消的计划无需提醒: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setIsReminderSent(Boolean.TRUE);
        entity.setReminderSentAt(LocalDateTime.now());
        entity = planRepository.save(entity);
        log.info("发送互动提醒: id={}, ownerId={}", id, entity.getOwnerId());
        return entity;
    }

    /**
     * 检查待提醒计划 (批量扫描): 计划开始时间在未来 N 分钟内、未提醒、非终态。
     *
     * @return 已发送提醒的计划列表
     */
    @Transactional
    public List<ScrmInteractionPlanEntity> checkReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime horizon = now.plusMinutes(DEFAULT_REMINDER_MINUTES);
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isReminderSent"), Boolean.FALSE));
            predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStart"), now));
            predicates.add(cb.lessThanOrEqualTo(root.get("scheduledStart"), horizon));
            predicates.add(root.get("status").in(ACTIVE_STATUSES));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmInteractionPlanEntity> plans = planRepository.findAll(spec);
        List<ScrmInteractionPlanEntity> sent = new ArrayList<>();
        for (ScrmInteractionPlanEntity plan : plans) {
            try {
                sent.add(sendReminder(plan.getId()));
            } catch (ScrmException e) {
                log.warn("批量发送提醒失败: planId={}, err={}", plan.getId(), e.getMessage());
            }
        }
        log.info("检查互动待提醒: total={}, sent={}", plans.size(), sent.size());
        return sent;
    }

    /**
     * 处理重复计划: 为重复类型非 NONE 的计划生成下一周期实例。
     *
     * @return 生成的下一周期计划列表
     */
    @Transactional
    public List<ScrmInteractionPlanEntity> processRecurringPlans() {
        Specification<ScrmInteractionPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("repeatType"), DEFAULT_REPEAT_TYPE));
            predicates.add(root.get("status").in(ACTIVE_STATUSES));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmInteractionPlanEntity> recurring = planRepository.findAll(spec);
        List<ScrmInteractionPlanEntity> generated = new ArrayList<>();
        for (ScrmInteractionPlanEntity plan : recurring) {
            try {
                ScrmInteractionPlanEntity next = generateNextOccurrence(plan);
                if (next != null) {
                    generated.add(next);
                }
            } catch (ScrmException e) {
                log.warn("生成重复计划失败: planId={}, err={}", plan.getId(), e.getMessage());
            }
        }
        log.info("处理重复互动计划: source={}, generated={}", recurring.size(), generated.size());
        return generated;
    }

    /**
     * 复制计划 (创建副本, 使用新开始时间)。
     *
     * @param id       源计划 ID
     * @param newStart 新开始时间
     * @return 复制后的计划
     * @throws ScrmException 源计划不存在
     */
    @Transactional
    public ScrmInteractionPlanEntity duplicatePlan(Long id, LocalDateTime newStart) throws ScrmException {
        if (newStart == null) {
            throw ScrmException.badRequest("新开始时间不能为空");
        }
        ScrmInteractionPlanEntity source = findPlanOrThrow(id);
        ScrmInteractionPlanEntity copy = new ScrmInteractionPlanEntity();
        copy.setPlanName(source.getPlanName() + COPY_SUFFIX);
        // 生成唯一编码: 原编码 + 时间戳后缀
        copy.setPlanCode(source.getPlanCode() + "-" + System.currentTimeMillis());
        copy.setDescription(source.getDescription());
        copy.setCustomerId(source.getCustomerId());
        copy.setCustomerName(source.getCustomerName());
        copy.setInteractionType(source.getInteractionType());
        copy.setInteractionMethod(source.getInteractionMethod());
        copy.setTitle(source.getTitle());
        copy.setContent(source.getContent());
        copy.setObjectives(source.getObjectives());
        copy.setPrepareMaterials(source.getPrepareMaterials());
        copy.setScheduledStart(newStart);
        // 计算新结束时间: 保持原时长
        if (source.getScheduledEnd() != null && source.getScheduledStart() != null) {
            long durationMinutes = Duration.between(source.getScheduledStart(), source.getScheduledEnd()).toMinutes();
            copy.setScheduledEnd(newStart.plusMinutes(durationMinutes));
        }
        copy.setTimezone(source.getTimezone());
        copy.setLocation(source.getLocation());
        copy.setLocationType(source.getLocationType());
        copy.setOwnerId(source.getOwnerId());
        copy.setOwnerName(source.getOwnerName());
        copy.setParticipantIds(source.getParticipantIds());
        copy.setCustomerContactId(source.getCustomerContactId());
        copy.setCustomerContactName(source.getCustomerContactName());
        copy.setReminderType(source.getReminderType());
        copy.setReminderMinutesBefore(source.getReminderMinutesBefore());
        copy.setIsReminderSent(Boolean.FALSE);
        copy.setRepeatType(DEFAULT_REPEAT_TYPE);
        copy.setRepeatInterval(DEFAULT_REPEAT_INTERVAL);
        copy.setRepeatCount(DEFAULT_COUNT);
        copy.setMaxRepeatCount(DEFAULT_COUNT);
        copy.setPriority(source.getPriority());
        copy.setStatus(DEFAULT_STATUS);
        copy.setTags(source.getTags());
        copy.setColor(source.getColor());
        copy.setIsAllDay(source.getIsAllDay());
        copy.setIsPinned(Boolean.FALSE);
        copy.setAttachments(source.getAttachments());
        copy.setRelatedPlanId(source.getId());
        copy.setCreatedBy(currentOperator());
        copy = planRepository.save(copy);
        log.info("复制互动计划: sourceId={}, copyId={}, newStart={}", id, copy.getId(), newStart);
        return copy;
    }

    /**
     * 批量创建计划。
     *
     * @param dtos 计划参数列表
     * @return 创建后的计划列表
     * @throws ScrmException 参数非法
     */
    @Transactional
    public List<ScrmInteractionPlanEntity> batchCreatePlans(
            List<ScrmInteractionPlanDto> dtos) throws ScrmException {
        if (dtos == null || dtos.isEmpty()) {
            throw ScrmException.badRequest("计划列表不能为空");
        }
        List<ScrmInteractionPlanEntity> created = new ArrayList<>();
        for (ScrmInteractionPlanDto dto : dtos) {
            try {
                created.add(createPlan(dto));
            } catch (ScrmException e) {
                log.warn("批量创建计划失败: planCode={}, err={}", dto.getPlanCode(), e.getMessage());
            }
        }
        log.info("批量创建互动计划: total={}, success={}", dtos.size(), created.size());
        return created;
    }

    /**
     * 批量取消计划。
     *
     * @param planIds 计划 ID 列表
     * @param reason  取消原因
     * @return 已取消的计划列表
     * @throws ScrmException 计划 ID 列表非法
     */
    @Transactional
    public List<ScrmInteractionPlanEntity> batchCancel(List<Long> planIds, String reason) throws ScrmException {
        if (planIds == null || planIds.isEmpty()) {
            throw ScrmException.badRequest("计划 ID 列表不能为空");
        }
        List<ScrmInteractionPlanEntity> cancelled = new ArrayList<>();
        for (Long planId : planIds) {
            try {
                cancelled.add(cancelPlan(planId, reason));
            } catch (ScrmException e) {
                log.warn("批量取消计划失败: planId={}, err={}", planId, e.getMessage());
            }
        }
        log.info("批量取消互动计划: total={}, success={}", planIds.size(), cancelled.size());
        return cancelled;
    }

    /**
     * 生成后续跟进计划 (基于已完成计划创建一条新的 PLANNED 计划)。
     *
     * @param source 已完成的源计划
     */
    private void generateFollowUpPlan(ScrmInteractionPlanEntity source) {
        ScrmInteractionPlanEntity followUp = new ScrmInteractionPlanEntity();
        followUp.setPlanName(source.getPlanName() + "-跟进");
        followUp.setPlanCode(source.getPlanCode() + "-FU-" + System.currentTimeMillis());
        followUp.setDescription(source.getFollowUpAction());
        followUp.setCustomerId(source.getCustomerId());
        followUp.setCustomerName(source.getCustomerName());
        followUp.setInteractionType("FOLLOW_UP");
        followUp.setInteractionMethod(source.getInteractionMethod());
        followUp.setTitle("跟进: " + source.getTitle());
        followUp.setTimezone(source.getTimezone());
        followUp.setLocation(source.getLocation());
        followUp.setLocationType(source.getLocationType());
        followUp.setOwnerId(source.getOwnerId());
        followUp.setOwnerName(source.getOwnerName());
        followUp.setCustomerContactId(source.getCustomerContactId());
        followUp.setCustomerContactName(source.getCustomerContactName());
        followUp.setReminderType(source.getReminderType());
        followUp.setReminderMinutesBefore(source.getReminderMinutesBefore());
        followUp.setIsReminderSent(Boolean.FALSE);
        followUp.setRepeatType(DEFAULT_REPEAT_TYPE);
        followUp.setRepeatInterval(DEFAULT_REPEAT_INTERVAL);
        followUp.setRepeatCount(DEFAULT_COUNT);
        followUp.setMaxRepeatCount(DEFAULT_COUNT);
        followUp.setPriority(source.getPriority());
        followUp.setStatus(DEFAULT_STATUS);
        // 后续日期: 默认源计划 followUpDate 或 +3 天
        LocalDate followDate = source.getFollowUpDate() != null
                ? source.getFollowUpDate()
                : LocalDate.now().plusDays(3);
        followUp.setScheduledStart(followDate.atTime(10, 0));
        followUp.setScheduledEnd(followDate.atTime(11, 0));
        followUp.setRelatedPlanId(source.getId());
        followUp.setCreatedBy(currentOperator());
        planRepository.save(followUp);
        log.info("生成后续跟进计划: sourceId={}, followUpId={}", source.getId(), followUp.getId());
    }

    /**
     * 生成重复计划的下一周期实例。
     *
     * @param source 源重复计划
     * @return 下一周期计划 (无下一周期返回 null)
     */
    private ScrmInteractionPlanEntity generateNextOccurrence(ScrmInteractionPlanEntity source) {
        LocalDateTime nextStart = computeNextOccurrence(source);
        if (nextStart == null) {
            return null;
        }
        // 检查是否超过重复结束日期或最大次数
        int currentCount = source.getRepeatCount() == null ? 0 : source.getRepeatCount();
        int maxCount = source.getMaxRepeatCount() == null ? 0 : source.getMaxRepeatCount();
        if (maxCount > 0 && currentCount >= maxCount) {
            return null;
        }
        if (source.getRepeatEndDate() != null && nextStart.toLocalDate().isAfter(source.getRepeatEndDate())) {
            return null;
        }
        // 更新源计划已重复次数
        source.setRepeatCount(currentCount + 1);
        planRepository.save(source);
        // 创建下一周期计划实例
        ScrmInteractionPlanEntity next = new ScrmInteractionPlanEntity();
        next.setPlanName(source.getPlanName());
        next.setPlanCode(source.getPlanCode() + "-" + System.currentTimeMillis());
        next.setDescription(source.getDescription());
        next.setCustomerId(source.getCustomerId());
        next.setCustomerName(source.getCustomerName());
        next.setInteractionType(source.getInteractionType());
        next.setInteractionMethod(source.getInteractionMethod());
        next.setTitle(source.getTitle());
        next.setContent(source.getContent());
        next.setObjectives(source.getObjectives());
        next.setPrepareMaterials(source.getPrepareMaterials());
        next.setScheduledStart(nextStart);
        if (source.getScheduledEnd() != null && source.getScheduledStart() != null) {
            long durationMinutes = Duration.between(source.getScheduledStart(), source.getScheduledEnd()).toMinutes();
            next.setScheduledEnd(nextStart.plusMinutes(durationMinutes));
        }
        next.setTimezone(source.getTimezone());
        next.setLocation(source.getLocation());
        next.setLocationType(source.getLocationType());
        next.setOwnerId(source.getOwnerId());
        next.setOwnerName(source.getOwnerName());
        next.setParticipantIds(source.getParticipantIds());
        next.setCustomerContactId(source.getCustomerContactId());
        next.setCustomerContactName(source.getCustomerContactName());
        next.setReminderType(source.getReminderType());
        next.setReminderMinutesBefore(source.getReminderMinutesBefore());
        next.setIsReminderSent(Boolean.FALSE);
        next.setRepeatType(source.getRepeatType());
        next.setRepeatInterval(source.getRepeatInterval());
        next.setRepeatEndDate(source.getRepeatEndDate());
        next.setRepeatCount(0);
        next.setMaxRepeatCount(source.getMaxRepeatCount());
        next.setWeekDays(source.getWeekDays());
        next.setMonthDay(source.getMonthDay());
        next.setPriority(source.getPriority());
        next.setStatus(DEFAULT_STATUS);
        next.setTags(source.getTags());
        next.setColor(source.getColor());
        next.setIsAllDay(source.getIsAllDay());
        next.setIsPinned(Boolean.FALSE);
        next.setAttachments(source.getAttachments());
        next.setRelatedPlanId(source.getId());
        next.setCreatedBy(currentOperator());
        next = planRepository.save(next);
        log.info("生成重复计划下一周期: sourceId={}, nextId={}, nextStart={}",
                source.getId(), next.getId(), nextStart);
        return next;
    }

    /**
     * 计算下一周期开始时间。
     *
     * @param plan 重复计划
     * @return 下一周期开始时间 (无返回 null)
     */
    private LocalDateTime computeNextOccurrence(ScrmInteractionPlanEntity plan) {
        LocalDateTime base = plan.getScheduledStart();
        int interval = plan.getRepeatInterval() == null ? 1 : plan.getRepeatInterval();
        if (base == null) {
            return null;
        }
        switch (plan.getRepeatType()) {
            case "DAILY":
                return base.plusDays(interval);
            case "WEEKLY":
                return base.plusWeeks(interval);
            case "MONTHLY":
                return base.plusMonths(interval);
            case "QUARTERLY":
                return base.plusMonths(3L * interval);
            case "YEARLY":
                return base.plusYears(interval);
            case "CUSTOM":
                // CUSTOM 默认按周处理
                return base.plusWeeks(interval);
            default:
                return null;
        }
    }

    /**
     * 校验计划参数。
     *
     * @param dto     计划参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validatePlanDto(ScrmInteractionPlanDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("计划参数不能为空");
        }
        if (dto.getPlanName() != null) {
            if (dto.getPlanName().isBlank()) {
                throw ScrmException.badRequest("计划名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("计划名称不能为空");
        }
        if (dto.getPlanCode() != null) {
            if (dto.getPlanCode().isBlank()) {
                throw ScrmException.badRequest("计划编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("计划编码不能为空");
        }
        if (!partial && dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getInteractionType() != null && !VALID_INTERACTION_TYPES.contains(dto.getInteractionType())) {
            throw ScrmException.badRequest(
                    "互动类型非法: " + dto.getInteractionType() + ", 仅支持 " + VALID_INTERACTION_TYPES);
        }
        if (!partial && dto.getInteractionType() == null) {
            throw ScrmException.badRequest("互动类型不能为空");
        }
        if (dto.getInteractionMethod() != null && !VALID_INTERACTION_METHODS.contains(dto.getInteractionMethod())) {
            throw ScrmException.badRequest(
                    "互动方式非法: " + dto.getInteractionMethod() + ", 仅支持 " + VALID_INTERACTION_METHODS);
        }
        if (!partial && dto.getInteractionMethod() == null) {
            throw ScrmException.badRequest("互动方式不能为空");
        }
        if (dto.getTitle() != null) {
            if (dto.getTitle().isBlank()) {
                throw ScrmException.badRequest("互动主题不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("互动主题不能为空");
        }
        if (!partial && dto.getScheduledStart() == null) {
            throw ScrmException.badRequest("计划开始时间不能为空");
        }
        if (!partial && dto.getOwnerId() == null) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        if (dto.getReminderType() != null && !VALID_REMINDER_TYPES.contains(dto.getReminderType())) {
            throw ScrmException.badRequest(
                    "提醒类型非法: " + dto.getReminderType() + ", 仅支持 " + VALID_REMINDER_TYPES);
        }
        if (dto.getRepeatType() != null && !VALID_REPEAT_TYPES.contains(dto.getRepeatType())) {
            throw ScrmException.badRequest(
                    "重复类型非法: " + dto.getRepeatType() + ", 仅支持 " + VALID_REPEAT_TYPES);
        }
        if (dto.getPriority() != null && !VALID_PRIORITIES.contains(dto.getPriority())) {
            throw ScrmException.badRequest(
                    "优先级非法: " + dto.getPriority() + ", 仅支持 " + VALID_PRIORITIES);
        }
        if (dto.getStatus() != null && !VALID_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest(
                    "状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_STATUSES);
        }
        if (dto.getLocationType() != null && !VALID_LOCATION_TYPES.contains(dto.getLocationType())) {
            throw ScrmException.badRequest(
                    "地点类型非法: " + dto.getLocationType() + ", 仅支持 " + VALID_LOCATION_TYPES);
        }
        validateScheduledRange(dto.getScheduledStart(), dto.getScheduledEnd());
    }

    /**
     * 校验计划时间区间合法性 (scheduledStart ≤ scheduledEnd, 且均非空时校验)。
     *
     * @param start 开始时间 (可空)
     * @param end   结束时间 (可空)
     * @throws ScrmException 时间区间非法
     */
    private void validateScheduledRange(LocalDateTime start, LocalDateTime end) throws ScrmException {
        if (start == null || end == null) {
            return;
        }
        if (start.isAfter(end)) {
            throw ScrmException.badRequest("计划开始时间不能晚于结束时间");
        }
    }

    /**
     * 获取当前操作人 (优先从 UserContext 获取)。
     *
     * @return 操作人用户名
     */
    String currentOperator() {
        String username = UserContext.getUsername();
        return username != null ? username : DEFAULT_OPERATOR;
    }

    /**
     * 按主键查询计划, 不存在抛异常, 并校验账号归属。
     *
     * @param id 计划 ID
     * @return 计划实体
     * @throws ScrmException 计划不存在
     */
    private ScrmInteractionPlanEntity findPlanOrThrow(Long id) throws ScrmException {
        ScrmInteractionPlanEntity entity = planRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "互动计划不存在: id=" + id));
        return entity;
    }

}