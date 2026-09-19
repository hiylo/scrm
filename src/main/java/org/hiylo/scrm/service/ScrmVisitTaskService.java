/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitTaskService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmVisitCompleteDto;
import org.hiylo.scrm.dto.ScrmVisitRescheduleDto;
import org.hiylo.scrm.dto.ScrmVisitTaskDto;
import org.hiylo.scrm.entity.ScrmVisitPlanEntity;
import org.hiylo.scrm.entity.ScrmVisitTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmVisitTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 客户回访任务子域服务
 * <p>
 * 负责回访任务管理 (CRUD + 分配 / 开始 / 完成 / 取消 / 改期 + 今日 / 逾期 /
 * 按负责人 / 按客户查询 + 批量分配) 与提醒 (单发 + 批发 + 待发查询)。
 * 计划相关能力复用 {@link ScrmVisitPlanService}, 模板使用计数复用
 * {@link ScrmVisitTemplateService}。本服务为 {@link ScrmVisitService} 门面的子域拆分,
 * 不反向依赖门面。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmVisitTaskService {

    /** 任务状态: 待执行 */
    private static final String TASK_STATUS_PENDING = "PENDING";
    /** 任务状态: 已分配 */
    private static final String TASK_STATUS_ASSIGNED = "ASSIGNED";
    /** 任务状态: 进行中 */
    private static final String TASK_STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 任务状态: 已完成 */
    private static final String TASK_STATUS_COMPLETED = "COMPLETED";
    /** 任务状态: 已取消 */
    private static final String TASK_STATUS_CANCELLED = "CANCELLED";
    /** 任务状态: 已改期 */
    private static final String TASK_STATUS_RESCHEDULED = "RESCHEDULED";

    /** 合法的回访结果 */
    private static final List<String> VALID_VISIT_OUTCOMES = List.of(
            "SUCCESS", "PARTIAL", "NO_ANSWER", "REFUSED", "RESCHEDULED", "FAILED");

    /** 合法的回访类型 */
    private static final List<String> VALID_VISIT_TYPES = List.of(
            "REGULAR", "FOLLOW_UP", "SATISFACTION", "RENEWAL", "UPSELL",
            "CROSS_SELL", "CARE", "COMPLAINT_FOLLOWUP", "CUSTOM");

    /** 合法的回访方式 */
    private static final List<String> VALID_VISIT_METHODS = List.of(
            "PHONE", "ON_SITE", "VIDEO", "WECHAT", "EMAIL", "SMS", "MIXED");

    /** 回访任务数据访问层 */
    private final ScrmVisitTaskRepository taskRepository;

    /** 回访计划子域服务 (任务编号 / 操作人 / 计划查询 / 计划统计) */
    private final ScrmVisitPlanService planService;

    /** 回访模板子域服务 (完成回访时递增模板使用计数) */
    private final ScrmVisitTemplateService templateService;

    /**
     * 创建回访任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmVisitTaskEntity createTask(ScrmVisitTaskDto dto) throws ScrmException {
        validateTaskDto(dto, false);
        ScrmVisitTaskEntity entity = new ScrmVisitTaskEntity();
        entity.setTaskNo(dto.getTaskNo() != null && !dto.getTaskNo().isBlank()
                ? dto.getTaskNo() : planService.generateTaskNo());
        entity.setPlanId(dto.getPlanId());
        if (dto.getPlanId() != null) {
            ScrmVisitPlanEntity plan = planService.findPlanOrThrow(dto.getPlanId());
            entity.setPlanName(plan.getPlanName());
        }
        entity.setPlanName(dto.getPlanName() != null ? dto.getPlanName() : entity.getPlanName());
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setCustomerLevel(dto.getCustomerLevel());
        entity.setCustomerPhone(dto.getCustomerPhone());
        entity.setVisitType(dto.getVisitType());
        entity.setVisitMethod(dto.getVisitMethod());
        entity.setScheduledDate(dto.getScheduledDate());
        entity.setScheduledTime(dto.getScheduledTime());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : TASK_STATUS_PENDING);
        entity.setAssignedTo(dto.getAssignedTo());
        if (dto.getAssignedTo() != null && !dto.getAssignedTo().isBlank()) {
            entity.setAssignedAt(LocalDateTime.now());
            if (TASK_STATUS_PENDING.equals(entity.getStatus())) {
                entity.setStatus(TASK_STATUS_ASSIGNED);
            }
        }
        entity.setFollowUpRequired(Boolean.FALSE);
        entity.setOpportunityFound(Boolean.FALSE);
        entity.setIssueFound(Boolean.FALSE);
        entity.setIssueResolved(Boolean.FALSE);
        entity.setReminderSent(Boolean.FALSE);
        entity.setRescheduleCount(0);
        entity.setLocation(dto.getLocation());
        entity.setNotes(dto.getNotes());
        entity.setRecordingUrl(dto.getRecordingUrl());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : planService.currentOperator());
        entity = taskRepository.save(entity);
        // 更新关联计划统计
        if (entity.getPlanId() != null) {
            planService.updatePlanStats(entity.getPlanId());
        }
        log.info("创建回访任务: id={}, taskNo={}, customerId={}",
                entity.getId(), entity.getTaskNo(), entity.getCustomerId());
        return entity;
    }

    /**
     * 更新回访任务 (字段非空才覆盖)。
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 参数非法
     */
    @Transactional
    public ScrmVisitTaskEntity updateTask(Long id, ScrmVisitTaskDto dto) throws ScrmException {
        ScrmVisitTaskEntity entity = findTaskOrThrow(id);
        validateTaskDto(dto, true);
        if (dto.getPlanId() != null) entity.setPlanId(dto.getPlanId());
        if (dto.getPlanName() != null) entity.setPlanName(dto.getPlanName());
        if (dto.getCustomerId() != null) entity.setCustomerId(dto.getCustomerId());
        if (dto.getCustomerName() != null) entity.setCustomerName(dto.getCustomerName());
        if (dto.getCustomerLevel() != null) entity.setCustomerLevel(dto.getCustomerLevel());
        if (dto.getCustomerPhone() != null) entity.setCustomerPhone(dto.getCustomerPhone());
        if (dto.getVisitType() != null) entity.setVisitType(dto.getVisitType());
        if (dto.getVisitMethod() != null) entity.setVisitMethod(dto.getVisitMethod());
        if (dto.getScheduledDate() != null) entity.setScheduledDate(dto.getScheduledDate());
        if (dto.getScheduledTime() != null) entity.setScheduledTime(dto.getScheduledTime());
        if (dto.getAssignedTo() != null) entity.setAssignedTo(dto.getAssignedTo());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getLocation() != null) entity.setLocation(dto.getLocation());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        if (dto.getRecordingUrl() != null) entity.setRecordingUrl(dto.getRecordingUrl());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = taskRepository.save(entity);
        log.info("更新回访任务: id={}, taskNo={}", entity.getId(), entity.getTaskNo());
        return entity;
    }

    /**
     * 删除回访任务。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public void deleteTask(Long id) throws ScrmException {
        ScrmVisitTaskEntity entity = findTaskOrThrow(id);
        Long planId = entity.getPlanId();
        taskRepository.delete(entity);
        if (planId != null) {
            planService.updatePlanStats(planId);
        }
        log.info("删除回访任务: id={}, taskNo={}", id, entity.getTaskNo());
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmVisitTaskEntity getTask(Long id) throws ScrmException {
        return findTaskOrThrow(id);
    }

    /**
     * 按任务编号查询。
     *
     * @param taskNo 任务编号
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmVisitTaskEntity getTaskByNo(String taskNo) throws ScrmException {
        if (taskNo == null || taskNo.isBlank()) {
            throw ScrmException.badRequest("任务编号不能为空");
        }
        return taskRepository.findByTaskNo(taskNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "回访任务不存在: taskNo=" + taskNo));
    }

    /**
     * 分页查询任务列表, 支持多条件过滤。
     *
     * @param planId      计划 ID 过滤（可空）
     * @param customerId  客户 ID 过滤（可空）
     * @param visitType   回访类型过滤（可空）
     * @param visitMethod 回访方式过滤（可空）
     * @param status      状态过滤（可空）
     * @param assignedTo  负责人过滤（可空）
     * @param startDate   计划日期起始 (含, 可空)
     * @param endDate     计划日期截止 (含, 可空)
     * @param keyword     任务编号/客户名称关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 任务分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmVisitTaskEntity> listTasks(Long planId, Long customerId, String visitType,
                                                String visitMethod, String status, String assignedTo,
                                                LocalDate startDate, LocalDate endDate, String keyword,
                                                Pageable pageable) {
        Specification<ScrmVisitTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (planId != null) {
                predicates.add(cb.equal(root.get("planId"), planId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (visitType != null && !visitType.isBlank()) {
                predicates.add(cb.equal(root.get("visitType"), visitType));
            }
            if (visitMethod != null && !visitMethod.isBlank()) {
                predicates.add(cb.equal(root.get("visitMethod"), visitMethod));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (assignedTo != null && !assignedTo.isBlank()) {
                predicates.add(cb.equal(root.get("assignedTo"), assignedTo));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledDate"), endDate));
            }
            if (keyword != null && !keyword.isBlank()) {
                Predicate noLike = cb.like(root.get("taskNo"), "%" + keyword + "%");
                Predicate nameLike = cb.like(root.get("customerName"), "%" + keyword + "%");
                predicates.add(cb.or(noLike, nameLike));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 分配任务给指定负责人 (PENDING → ASSIGNED)。
     *
     * @param id         任务 ID
     * @param assigneeId 负责人 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmVisitTaskEntity assignTask(Long id, String assigneeId) throws ScrmException {
        if (assigneeId == null || assigneeId.isBlank()) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        ScrmVisitTaskEntity entity = findTaskOrThrow(id);
        if (TASK_STATUS_COMPLETED.equals(entity.getStatus()) || TASK_STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("已完成/已取消的任务不可分配: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setAssignedTo(assigneeId);
        entity.setAssignedAt(LocalDateTime.now());
        if (TASK_STATUS_PENDING.equals(entity.getStatus())) {
            entity.setStatus(TASK_STATUS_ASSIGNED);
        }
        entity = taskRepository.save(entity);
        log.info("分配回访任务: id={}, taskNo={}, assigneeId={}", id, entity.getTaskNo(), assigneeId);
        return entity;
    }

    /**
     * 开始回访 (PENDING/ASSIGNED → IN_PROGRESS)。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmVisitTaskEntity startTask(Long id) throws ScrmException {
        ScrmVisitTaskEntity entity = findTaskOrThrow(id);
        if (!TASK_STATUS_PENDING.equals(entity.getStatus()) && !TASK_STATUS_ASSIGNED.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 PENDING/ASSIGNED 状态可开始: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(TASK_STATUS_IN_PROGRESS);
        entity.setStartedAt(LocalDateTime.now());
        entity = taskRepository.save(entity);
        log.info("开始回访任务: id={}, taskNo={}", id, entity.getTaskNo());
        return entity;
    }

    /**
     * 完成回访: 记录结果 → 满意度 → 生成行动项 → 更新统计。
     *
     * @param dto 完成参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmVisitTaskEntity completeTask(ScrmVisitCompleteDto dto) throws ScrmException {
        if (dto == null || dto.getTaskId() == null) {
            throw ScrmException.badRequest("完成任务参数不能为空");
        }
        ScrmVisitTaskEntity entity = findTaskOrThrow(dto.getTaskId());
        if (TASK_STATUS_COMPLETED.equals(entity.getStatus()) || TASK_STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("任务已是终态, 不可完成: id=" + dto.getTaskId() + ", status=" + entity.getStatus());
        }
        if (dto.getVisitOutcome() != null && !VALID_VISIT_OUTCOMES.contains(dto.getVisitOutcome())) {
            throw ScrmException.badRequest(
                    "回访结果非法: " + dto.getVisitOutcome() + ", 仅支持 " + VALID_VISIT_OUTCOMES);
        }
        entity.setStatus(TASK_STATUS_COMPLETED);
        entity.setCompletedAt(LocalDateTime.now());
        entity.setActualVisitDate(LocalDate.now());
        entity.setActualVisitTime(java.time.LocalTime.now());
        entity.setVisitOutcome(dto.getVisitOutcome());
        entity.setSatisfactionScore(dto.getSatisfactionScore());
        entity.setNpsScore(dto.getNpsScore());
        entity.setFeedback(dto.getFeedback());
        entity.setSummary(dto.getSummary());
        entity.setActionItems(dto.getActionItems());
        if (dto.getActualDurationMinutes() != null) {
            entity.setActualDurationMinutes(dto.getActualDurationMinutes());
        }
        entity.setOpportunityFound(dto.getOpportunityFound() != null ? dto.getOpportunityFound() : Boolean.FALSE);
        if (dto.getOpportunityDescription() != null) {
            entity.setOpportunityDescription(dto.getOpportunityDescription());
        }
        entity.setIssueFound(dto.getIssueFound() != null ? dto.getIssueFound() : Boolean.FALSE);
        if (dto.getIssueDescription() != null) {
            entity.setIssueDescription(dto.getIssueDescription());
        }
        entity.setIssueResolved(dto.getIssueResolved() != null ? dto.getIssueResolved() : Boolean.FALSE);
        entity.setFollowUpRequired(dto.getFollowUpRequired() != null ? dto.getFollowUpRequired() : Boolean.FALSE);
        if (entity.getFollowUpRequired()) {
            entity.setFollowUpDate(LocalDate.now().plusDays(3));
        }
        entity = taskRepository.save(entity);
        // 更新关联计划统计
        if (entity.getPlanId() != null) {
            planService.updatePlanStats(entity.getPlanId());
        }
        // 增长模板使用计数与满意度
        if (entity.getPlanId() != null) {
            ScrmVisitPlanEntity plan = planService.findPlanOrThrow(entity.getPlanId());
            if (plan.getTemplateId() != null) {
                templateService.incrementUsage(plan.getTemplateId(), dto.getSatisfactionScore());
            }
        }
        log.info("完成回访任务: id={}, taskNo={}, outcome={}, satisfaction={}",
                entity.getId(), entity.getTaskNo(), entity.getVisitOutcome(), entity.getSatisfactionScore());
        return entity;
    }

    /**
     * 取消任务 (非终态 → CANCELLED)。
     *
     * @param id     任务 ID
     * @param reason 取消原因
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmVisitTaskEntity cancelTask(Long id, String reason) throws ScrmException {
        ScrmVisitTaskEntity entity = findTaskOrThrow(id);
        if (TASK_STATUS_COMPLETED.equals(entity.getStatus()) || TASK_STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("任务已是终态, 不可取消: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(TASK_STATUS_CANCELLED);
        if (reason != null && !reason.isBlank()) {
            String notes = entity.getNotes();
            entity.setNotes((notes == null ? "" : notes + " | ") + "取消原因: " + reason);
        }
        entity = taskRepository.save(entity);
        if (entity.getPlanId() != null) {
            planService.updatePlanStats(entity.getPlanId());
        }
        log.info("取消回访任务: id={}, taskNo={}, reason={}", id, entity.getTaskNo(), reason);
        return entity;
    }

    /**
     * 改期任务 (非终态 → RESCHEDULED, 更新计划日期, 累加改期次数)。
     *
     * @param dto 改期参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法 / 日期非法
     */
    @Transactional
    public ScrmVisitTaskEntity rescheduleTask(ScrmVisitRescheduleDto dto) throws ScrmException {
        if (dto == null || dto.getTaskId() == null || dto.getNewDate() == null) {
            throw ScrmException.badRequest("改期参数不能为空");
        }
        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw ScrmException.badRequest("改期原因不能为空");
        }
        ScrmVisitTaskEntity entity = findTaskOrThrow(dto.getTaskId());
        if (TASK_STATUS_COMPLETED.equals(entity.getStatus()) || TASK_STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("任务已是终态, 不可改期: id=" + dto.getTaskId() + ", status=" + entity.getStatus());
        }
        // 保留原始日期
        if (entity.getOriginalDate() == null) {
            entity.setOriginalDate(entity.getScheduledDate());
        }
        entity.setScheduledDate(dto.getNewDate());
        if (dto.getNewTime() != null) {
            entity.setScheduledTime(dto.getNewTime());
        }
        entity.setStatus(TASK_STATUS_RESCHEDULED);
        entity.setRescheduleCount((entity.getRescheduleCount() == null ? 0 : entity.getRescheduleCount()) + 1);
        String notes = entity.getNotes();
        entity.setNotes((notes == null ? "" : notes + " | ") + "改期原因: " + dto.getReason());
        entity = taskRepository.save(entity);
        log.info("改期回访任务: id={}, taskNo={}, newDate={}, reason={}",
                dto.getTaskId(), entity.getTaskNo(), dto.getNewDate(), dto.getReason());
        return entity;
    }

    /**
     * 分页查询逾期任务 (scheduledDate < today 且非终态)。
     *
     * @param pageable 分页参数
     * @return 任务分页结果 (按 scheduledDate ASC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmVisitTaskEntity> getOverdueTasks(Pageable pageable) {
        LocalDate today = LocalDate.now();
        Specification<ScrmVisitTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.lessThan(root.get("scheduledDate"), today));
            predicates.add(root.get("status").in(List.of(
                    TASK_STATUS_PENDING, TASK_STATUS_ASSIGNED, TASK_STATUS_IN_PROGRESS, TASK_STATUS_RESCHEDULED)));
            query.orderBy(cb.asc(root.get("scheduledDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 查询指定负责人的今日任务。
     *
     * @param assigneeId 负责人 ID (可空, 为空则查询全部今日任务)
     * @param pageable   分页参数
     * @return 任务分页结果 (按 scheduledTime ASC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmVisitTaskEntity> getTodayTasks(String assigneeId, Pageable pageable) {
        LocalDate today = LocalDate.now();
        Specification<ScrmVisitTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("scheduledDate"), today));
            predicates.add(root.get("status").in(List.of(
                    TASK_STATUS_PENDING, TASK_STATUS_ASSIGNED, TASK_STATUS_IN_PROGRESS, TASK_STATUS_RESCHEDULED)));
            if (assigneeId != null && !assigneeId.isBlank()) {
                predicates.add(cb.equal(root.get("assignedTo"), assigneeId));
            }
            query.orderBy(cb.asc(root.get("scheduledTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 按负责人查询任务。
     *
     * @param assigneeId 负责人 ID
     * @param status     状态过滤（可空）
     * @param pageable   分页参数
     * @return 任务分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmVisitTaskEntity> getTasksByAssignee(String assigneeId, String status, Pageable pageable) {
        if (assigneeId == null || assigneeId.isBlank()) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        Specification<ScrmVisitTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("assignedTo"), assigneeId));
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 按客户查询任务。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 任务分页结果 (按 scheduledDate DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmVisitTaskEntity> getTasksByCustomer(Long customerId, Pageable pageable) {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        Specification<ScrmVisitTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            query.orderBy(cb.desc(root.get("scheduledDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 批量分配任务给指定负责人。
     *
     * @param taskIds   任务 ID 列表
     * @param assigneeId 负责人 ID
     * @return 已分配的任务列表
     * @throws ScrmException 负责人 ID 非法
     */
    @Transactional
    public List<ScrmVisitTaskEntity> batchAssignTasks(List<Long> taskIds, String assigneeId) throws ScrmException {
        if (taskIds == null || taskIds.isEmpty()) {
            throw ScrmException.badRequest("任务 ID 列表不能为空");
        }
        if (assigneeId == null || assigneeId.isBlank()) {
            throw ScrmException.badRequest("负责人 ID 不能为空");
        }
        List<ScrmVisitTaskEntity> result = new ArrayList<>();
        for (Long taskId : taskIds) {
            try {
                result.add(assignTask(taskId, assigneeId));
            } catch (ScrmException e) {
                log.warn("批量分配任务失败: taskId={}, err={}", taskId, e.getMessage());
            }
        }
        log.info("批量分配任务: total={}, success={}, assigneeId={}",
                taskIds.size(), result.size(), assigneeId);
        return result;
    }

    // ============================================================
    // 提醒
    // ============================================================

    /**
     * 发送任务提醒 (模拟)。
     *
     * @param taskId 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public ScrmVisitTaskEntity sendReminder(Long taskId) throws ScrmException {
        ScrmVisitTaskEntity entity = findTaskOrThrow(taskId);
        if (TASK_STATUS_COMPLETED.equals(entity.getStatus()) || TASK_STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("已完成/已取消的任务无需提醒: id=" + taskId + ", status=" + entity.getStatus());
        }
        entity.setReminderSent(Boolean.TRUE);
        entity.setReminderSentAt(LocalDateTime.now());
        entity = taskRepository.save(entity);
        log.info("发送回访提醒: taskId={}, taskNo={}, assignedTo={}",
                taskId, entity.getTaskNo(), entity.getAssignedTo());
        return entity;
    }

    /**
     * 批量发送提醒 (针对今日及未来 N 天内的未提醒任务)。
     *
     * @return 已发送提醒的任务列表
     */
    @Transactional
    public List<ScrmVisitTaskEntity> batchSendReminders() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(1);
        Specification<ScrmVisitTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("reminderSent"), Boolean.FALSE));
            predicates.add(cb.lessThanOrEqualTo(root.get("scheduledDate"), horizon));
            predicates.add(root.get("status").in(List.of(
                    TASK_STATUS_PENDING, TASK_STATUS_ASSIGNED, TASK_STATUS_IN_PROGRESS, TASK_STATUS_RESCHEDULED)));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmVisitTaskEntity> tasks = taskRepository.findAll(spec);
        List<ScrmVisitTaskEntity> sent = new ArrayList<>();
        for (ScrmVisitTaskEntity task : tasks) {
            try {
                sent.add(sendReminder(task.getId()));
            } catch (ScrmException e) {
                log.warn("批量发送提醒失败: taskId={}, err={}", task.getId(), e.getMessage());
            }
        }
        log.info("批量发送提醒: total={}, sent={}", tasks.size(), sent.size());
        return sent;
    }

    /**
     * 查询待发送提醒的任务 (今日及未来 1 天, 未提醒, 非终态)。
     *
     * @return 任务列表
     */
    @Transactional(readOnly = true)
    public List<ScrmVisitTaskEntity> getPendingReminders() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(1);
        Specification<ScrmVisitTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("reminderSent"), Boolean.FALSE));
            predicates.add(cb.lessThanOrEqualTo(root.get("scheduledDate"), horizon));
            predicates.add(root.get("status").in(List.of(
                    TASK_STATUS_PENDING, TASK_STATUS_ASSIGNED, TASK_STATUS_IN_PROGRESS, TASK_STATUS_RESCHEDULED)));
            query.orderBy(cb.asc(root.get("scheduledDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验任务参数。
     *
     * @param dto     任务参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTaskDto(ScrmVisitTaskDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("任务参数不能为空");
        }
        if (!partial && dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getVisitType() != null) {
            if (!VALID_VISIT_TYPES.contains(dto.getVisitType())) {
                throw ScrmException.badRequest(
                        "回访类型非法: " + dto.getVisitType() + ", 仅支持 " + VALID_VISIT_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("回访类型不能为空");
        }
        if (dto.getVisitMethod() != null && !VALID_VISIT_METHODS.contains(dto.getVisitMethod())) {
            throw ScrmException.badRequest(
                    "回访方式非法: " + dto.getVisitMethod() + ", 仅支持 " + VALID_VISIT_METHODS);
        }
        if (!partial && dto.getScheduledDate() == null) {
            throw ScrmException.badRequest("计划回访日期不能为空");
        }
    }

    /**
     * 按主键查询任务, 不存在抛异常, 并校验归属账号。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    private ScrmVisitTaskEntity findTaskOrThrow(Long id) throws ScrmException {
        ScrmVisitTaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "回访任务不存在: id=" + id));
        return entity;
    }
}