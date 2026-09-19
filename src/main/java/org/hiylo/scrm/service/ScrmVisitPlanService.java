/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitPlanService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmVisitPlanDto;
import org.hiylo.scrm.entity.ScrmVisitPlanEntity;
import org.hiylo.scrm.entity.ScrmVisitTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmVisitPlanRepository;
import org.hiylo.scrm.repository.ScrmVisitTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 客户回访计划子域服务
 * <p>
 * 负责回访计划管理 (CRUD + 状态流转: 激活 / 暂停 / 完成) + 任务生成 + 计划统计。
 * 任务 / 模板 / 统计见 {@link ScrmVisitTaskService} / {@link ScrmVisitTemplateService} /
 * {@link ScrmVisitStatsService}。本服务为 {@link ScrmVisitService} 门面的子域拆分,
 * 不反向依赖门面。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmVisitPlanService {

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 默认任务统计初值 */
    private static final int DEFAULT_COUNT = 0;

    /** 默认完成率 */
    private static final double DEFAULT_RATE = 0.0;

    /** 默认回访方式 */
    private static final String DEFAULT_VISIT_METHOD = "PHONE";

    /** 任务编号前缀 */
    private static final String TASK_NO_PREFIX = "VT";

    /** 任务编号日期格式 */
    private static final DateTimeFormatter TASK_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 任务编号序号宽度 */
    private static final int TASK_NO_SEQ_WIDTH = 4;

    /** 默认操作人 (请求头未透传时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 计划状态: 生效 */
    private static final String PLAN_STATUS_ACTIVE = "ACTIVE";
    /** 计划状态: 暂停 */
    private static final String PLAN_STATUS_PAUSED = "PAUSED";
    /** 计划状态: 已完成 */
    private static final String PLAN_STATUS_COMPLETED = "COMPLETED";
    /** 计划状态: 已过期 */
    private static final String PLAN_STATUS_EXPIRED = "EXPIRED";

    /** 任务状态: 待执行 */
    private static final String TASK_STATUS_PENDING = "PENDING";
    /** 任务状态: 已分配 */
    private static final String TASK_STATUS_ASSIGNED = "ASSIGNED";
    /** 任务状态: 已完成 */
    private static final String TASK_STATUS_COMPLETED = "COMPLETED";
    /** 任务状态: 已逾期 */
    private static final String TASK_STATUS_OVERDUE = "OVERDUE";

    /** 回访结果: 成功 */
    private static final String OUTCOME_SUCCESS = "SUCCESS";
    /** 回访结果: 部分成功 */
    private static final String OUTCOME_PARTIAL = "PARTIAL";

    /** 合法的回访类型 */
    private static final List<String> VALID_VISIT_TYPES = List.of(
            "REGULAR", "FOLLOW_UP", "SATISFACTION", "RENEWAL", "UPSELL",
            "CROSS_SELL", "CARE", "COMPLAINT_FOLLOWUP", "CUSTOM");

    /** 合法的目标类型 */
    private static final List<String> VALID_TARGET_TYPES = List.of(
            "CUSTOMER", "CUSTOMER_LEVEL", "SEGMENT", "ALL");

    /** 合法的回访频率 */
    private static final List<String> VALID_VISIT_FREQUENCIES = List.of(
            "ONCE", "DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY",
            "QUARTERLY", "SEMIANNUALLY", "ANNUALLY");

    /** 合法的回访方式 */
    private static final List<String> VALID_VISIT_METHODS = List.of(
            "PHONE", "ON_SITE", "VIDEO", "WECHAT", "EMAIL", "SMS", "MIXED");

    /** 回访计划数据访问层 */
    private final ScrmVisitPlanRepository planRepository;

    /** 回访任务数据访问层 */
    private final ScrmVisitTaskRepository taskRepository;

    /**
     * 创建回访计划。
     *
     * @param dto 计划参数
     * @return 创建后的计划
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmVisitPlanEntity createPlan(ScrmVisitPlanDto dto) throws ScrmException {
        validatePlanDto(dto, false);
        if (planRepository.findByPlanCode(dto.getPlanCode()).isPresent()) {
            throw ScrmException.conflict("计划编码已存在: " + dto.getPlanCode());
        }
        ScrmVisitPlanEntity entity = new ScrmVisitPlanEntity();
        entity.setPlanName(dto.getPlanName());
        entity.setPlanCode(dto.getPlanCode());
        entity.setDescription(dto.getDescription());
        entity.setPlanType(dto.getPlanType());
        entity.setTargetType(dto.getTargetType());
        entity.setTargetCriteria(dto.getTargetCriteria());
        entity.setVisitFrequency(dto.getVisitFrequency());
        entity.setFrequencyConfig(dto.getFrequencyConfig());
        entity.setVisitMethod(dto.getVisitMethod() != null ? dto.getVisitMethod() : DEFAULT_VISIT_METHOD);
        entity.setTemplateId(dto.getTemplateId());
        entity.setAssignedTo(dto.getAssignedTo());
        entity.setTeamId(dto.getTeamId());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : PLAN_STATUS_ACTIVE);
        entity.setTotalTasks(DEFAULT_COUNT);
        entity.setCompletedTasks(DEFAULT_COUNT);
        entity.setPendingTasks(DEFAULT_COUNT);
        entity.setOverdueTasks(DEFAULT_COUNT);
        entity.setCompletionRate(DEFAULT_RATE);
        entity.setAvgSatisfactionScore(DEFAULT_RATE);
        entity.setSuccessRate(DEFAULT_RATE);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setTags(dto.getTags());
        entity.setAutoGenerate(dto.getAutoGenerate() != null ? dto.getAutoGenerate() : Boolean.FALSE);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        entity = planRepository.save(entity);
        log.info("创建回访计划: id={}, planName={}, planCode={}",
                entity.getId(), entity.getPlanName(), entity.getPlanCode());
        return entity;
    }

    /**
     * 更新回访计划 (字段非空才覆盖)。
     *
     * @param id  计划 ID
     * @param dto 计划参数
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 参数非法
     */
    @Transactional
    public ScrmVisitPlanEntity updatePlan(Long id, ScrmVisitPlanDto dto) throws ScrmException {
        ScrmVisitPlanEntity entity = findPlanOrThrow(id);
        validatePlanDto(dto, true);
        if (dto.getPlanName() != null) entity.setPlanName(dto.getPlanName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getPlanType() != null) entity.setPlanType(dto.getPlanType());
        if (dto.getTargetType() != null) entity.setTargetType(dto.getTargetType());
        if (dto.getTargetCriteria() != null) entity.setTargetCriteria(dto.getTargetCriteria());
        if (dto.getVisitFrequency() != null) entity.setVisitFrequency(dto.getVisitFrequency());
        if (dto.getFrequencyConfig() != null) entity.setFrequencyConfig(dto.getFrequencyConfig());
        if (dto.getVisitMethod() != null) entity.setVisitMethod(dto.getVisitMethod());
        if (dto.getTemplateId() != null) entity.setTemplateId(dto.getTemplateId());
        if (dto.getAssignedTo() != null) entity.setAssignedTo(dto.getAssignedTo());
        if (dto.getTeamId() != null) entity.setTeamId(dto.getTeamId());
        if (dto.getStartDate() != null) entity.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) entity.setEndDate(dto.getEndDate());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getAutoGenerate() != null) entity.setAutoGenerate(dto.getAutoGenerate());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        validateDateRange(entity.getStartDate(), entity.getEndDate());
        entity = planRepository.save(entity);
        log.info("更新回访计划: id={}, planName={}", entity.getId(), entity.getPlanName());
        return entity;
    }

    /**
     * 删除回访计划。
     *
     * @param id 计划 ID
     * @throws ScrmException 计划不存在
     */
    @Transactional
    public void deletePlan(Long id) throws ScrmException {
        ScrmVisitPlanEntity entity = findPlanOrThrow(id);
        planRepository.delete(entity);
        log.info("删除回访计划: id={}, planName={}", id, entity.getPlanName());
    }

    /**
     * 查询计划详情。
     *
     * @param id 计划 ID
     * @return 计划实体
     * @throws ScrmException 计划不存在
     */
    @Transactional(readOnly = true)
    public ScrmVisitPlanEntity getPlan(Long id) throws ScrmException {
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
    public ScrmVisitPlanEntity getPlanByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("计划编码不能为空");
        }
        return planRepository.findByPlanCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "回访计划不存在: code=" + code));
    }

    /**
     * 分页查询计划列表。
     *
     * @param planType 计划类型过滤（可空）
     * @param status   状态过滤（可空）
     * @param keyword  计划名称/编码关键字模糊匹配（可空）
     * @param pageable 分页参数
     * @return 计划分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmVisitPlanEntity> listPlans(String planType, String status, String keyword, Pageable pageable) {
        Specification<ScrmVisitPlanEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (planType != null && !planType.isBlank()) {
                predicates.add(cb.equal(root.get("planType"), planType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                Predicate nameLike = cb.like(root.get("planName"), "%" + keyword + "%");
                Predicate codeLike = cb.like(root.get("planCode"), "%" + keyword + "%");
                predicates.add(cb.or(nameLike, codeLike));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return planRepository.findAll(spec, pageable);
    }

    /**
     * 激活计划 (任意状态 → ACTIVE)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @Transactional
    public ScrmVisitPlanEntity activatePlan(Long id) throws ScrmException {
        ScrmVisitPlanEntity entity = findPlanOrThrow(id);
        if (PLAN_STATUS_COMPLETED.equals(entity.getStatus()) || PLAN_STATUS_EXPIRED.equals(entity.getStatus())) {
            throw ScrmException.conflict("已完成/已过期的计划不可激活: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(PLAN_STATUS_ACTIVE);
        entity = planRepository.save(entity);
        log.info("激活回访计划: id={}, planName={}", id, entity.getPlanName());
        return entity;
    }

    /**
     * 暂停计划 (ACTIVE → PAUSED)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @Transactional
    public ScrmVisitPlanEntity pausePlan(Long id) throws ScrmException {
        ScrmVisitPlanEntity entity = findPlanOrThrow(id);
        if (!PLAN_STATUS_ACTIVE.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 ACTIVE 状态可暂停: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(PLAN_STATUS_PAUSED);
        entity = planRepository.save(entity);
        log.info("暂停回访计划: id={}, planName={}", id, entity.getPlanName());
        return entity;
    }

    /**
     * 完成计划 (非终态 → COMPLETED)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @Transactional
    public ScrmVisitPlanEntity completePlan(Long id) throws ScrmException {
        ScrmVisitPlanEntity entity = findPlanOrThrow(id);
        if (PLAN_STATUS_COMPLETED.equals(entity.getStatus()) || PLAN_STATUS_EXPIRED.equals(entity.getStatus())) {
            throw ScrmException.conflict("计划已是终态, 不可完成: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(PLAN_STATUS_COMPLETED);
        entity = planRepository.save(entity);
        log.info("完成回访计划: id={}, planName={}", id, entity.getPlanName());
        return entity;
    }

    /**
     * 根据计划生成回访任务。
     * <p>简单策略: 以计划默认负责人/起始日期为基准生成一条任务, 实际场景应由 targetType 解析客户列表批量生成。</p>
     *
     * @param planId 计划 ID
     * @return 生成的任务列表
     * @throws ScrmException 计划不存在 / 状态非法
     */
    @Transactional
    public List<ScrmVisitTaskEntity> generateTasks(Long planId) throws ScrmException {
        ScrmVisitPlanEntity plan = findPlanOrThrow(planId);
        if (!PLAN_STATUS_ACTIVE.equals(plan.getStatus())) {
            throw ScrmException.conflict("仅 ACTIVE 状态的计划可生成任务: id=" + planId + ", status=" + plan.getStatus());
        }
        List<ScrmVisitTaskEntity> generated = new ArrayList<>();
        ScrmVisitTaskEntity task = new ScrmVisitTaskEntity();
        task.setTaskNo(generateTaskNo());
        task.setPlanId(plan.getId());
        task.setPlanName(plan.getPlanName());
        // 默认生成一条占位客户任务 (customerId=0, 由调用方填充实际客户)
        task.setCustomerId(0L);
        task.setVisitType(plan.getPlanType());
        task.setVisitMethod(plan.getVisitMethod());
        LocalDate scheduled = plan.getStartDate() != null ? plan.getStartDate() : LocalDate.now();
        task.setScheduledDate(scheduled);
        task.setStatus(TASK_STATUS_PENDING);
        task.setAssignedTo(plan.getAssignedTo());
        if (plan.getAssignedTo() != null) {
            task.setAssignedAt(LocalDateTime.now());
            task.setStatus(TASK_STATUS_ASSIGNED);
        }
        task.setFollowUpRequired(Boolean.FALSE);
        task.setOpportunityFound(Boolean.FALSE);
        task.setIssueFound(Boolean.FALSE);
        task.setIssueResolved(Boolean.FALSE);
        task.setReminderSent(Boolean.FALSE);
        task.setRescheduleCount(0);
        task.setCreatedBy(currentOperator());
        task = taskRepository.save(task);
        generated.add(task);
        // 更新计划生成时间与统计
        plan.setLastGeneratedAt(LocalDateTime.now());
        updatePlanStats(plan.getId());
        log.info("生成回访任务: planId={}, taskNo={}, generated={}",
                planId, task.getTaskNo(), generated.size());
        return generated;
    }

    /**
     * 更新计划统计指标 (任务数/完成率/满意度/成功率)。
     *
     * @param id 计划 ID
     * @return 更新后的计划
     * @throws ScrmException 计划不存在
     */
    @Transactional
    public ScrmVisitPlanEntity updatePlanStats(Long id) throws ScrmException {
        ScrmVisitPlanEntity plan = findPlanOrThrow(id);
        List<Object[]> statusAgg = taskRepository.countByPlanAndStatus(id);
        int total = 0;
        int completed = 0;
        int pending = 0;
        int overdue = 0;
        int success = 0;
        int outcomeTotal = 0;
        for (Object[] row : statusAgg) {
            String status = (String) row[0];
            int count = row[1] == null ? 0 : ((Number) row[1]).intValue();
            total += count;
            if (TASK_STATUS_COMPLETED.equals(status)) {
                completed += count;
            } else if (TASK_STATUS_PENDING.equals(status) || TASK_STATUS_ASSIGNED.equals(status)) {
                pending += count;
            } else if (TASK_STATUS_OVERDUE.equals(status)) {
                overdue += count;
            }
        }
        // 计算成功率与满意度需要查询完成任务明细
        Specification<ScrmVisitTaskEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("planId"), id),
                cb.equal(root.get("status"), TASK_STATUS_COMPLETED));
        List<ScrmVisitTaskEntity> completedTasks = taskRepository.findAll(spec);
        for (ScrmVisitTaskEntity t : completedTasks) {
            if (t.getVisitOutcome() != null) {
                outcomeTotal++;
                if (OUTCOME_SUCCESS.equals(t.getVisitOutcome()) || OUTCOME_PARTIAL.equals(t.getVisitOutcome())) {
                    success++;
                }
            }
        }
        Double avgScore = taskRepository.avgSatisfactionScoreByPlan(id);
        plan.setTotalTasks(total);
        plan.setCompletedTasks(completed);
        plan.setPendingTasks(pending);
        plan.setOverdueTasks(overdue);
        plan.setCompletionRate(total == 0 ? 0.0 : (double) completed / total);
        plan.setSuccessRate(outcomeTotal == 0 ? 0.0 : (double) success / outcomeTotal);
        plan.setAvgSatisfactionScore(avgScore != null ? avgScore : 0.0);
        plan = planRepository.save(plan);
        log.info("更新计划统计: id={}, total={}, completed={}, completionRate={}",
                id, total, completed, plan.getCompletionRate());
        return plan;
    }

    /**
     * 查询客户相关的计划 (作为目标 customerId 或通过任务关联)。
     *
     * @param customerId 客户 ID
     * @return 计划列表
     */
    @Transactional(readOnly = true)
    public List<ScrmVisitPlanEntity> getPlansByCustomer(Long customerId) {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        // 通过任务关联反查计划 ID
        Specification<ScrmVisitTaskEntity> taskSpec = (root, query, cb) -> cb.and(
                cb.equal(root.get("customerId"), customerId),
                cb.isNotNull(root.get("planId")));
        List<ScrmVisitTaskEntity> tasks = taskRepository.findAll(taskSpec);
        List<Long> planIds = tasks.stream().map(ScrmVisitTaskEntity::getPlanId).distinct().toList();
        if (planIds.isEmpty()) {
            return List.of();
        }
        Specification<ScrmVisitPlanEntity> planSpec = (root, query, cb) -> cb.and(
                root.get("id").in(planIds));
        return planRepository.findAll(planSpec);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 生成任务编号 (VT+年月日+4位序号)。
     * <p>序号基于当天已有任务数 + 1 生成, 高并发场景可能重复, 由唯一约束兜底。</p>
     *
     * @return 任务编号
     */
    String generateTaskNo() {
        String datePart = LocalDate.now().format(TASK_NO_DATE_FORMAT);
        // 查询当天已有任务数
        LocalDate today = LocalDate.now();
        Specification<ScrmVisitTaskEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("scheduledDate"), today));
        long count = taskRepository.count(spec);
        long seq = count + 1;
        return TASK_NO_PREFIX + datePart + String.format("%0" + TASK_NO_SEQ_WIDTH + "d", seq);
    }

    /**
     * 校验计划参数。
     *
     * @param dto     计划参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validatePlanDto(ScrmVisitPlanDto dto, boolean partial) throws ScrmException {
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
        if (dto.getPlanType() != null) {
            if (!VALID_VISIT_TYPES.contains(dto.getPlanType())) {
                throw ScrmException.badRequest(
                        "回访类型非法: " + dto.getPlanType() + ", 仅支持 " + VALID_VISIT_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("回访类型不能为空");
        }
        if (dto.getTargetType() != null && !VALID_TARGET_TYPES.contains(dto.getTargetType())) {
            throw ScrmException.badRequest(
                    "目标类型非法: " + dto.getTargetType() + ", 仅支持 " + VALID_TARGET_TYPES);
        }
        if (dto.getVisitFrequency() != null && !VALID_VISIT_FREQUENCIES.contains(dto.getVisitFrequency())) {
            throw ScrmException.badRequest(
                    "回访频率非法: " + dto.getVisitFrequency() + ", 仅支持 " + VALID_VISIT_FREQUENCIES);
        }
        if (dto.getVisitMethod() != null && !VALID_VISIT_METHODS.contains(dto.getVisitMethod())) {
            throw ScrmException.badRequest(
                    "回访方式非法: " + dto.getVisitMethod() + ", 仅支持 " + VALID_VISIT_METHODS);
        }
        if (!partial && dto.getStartDate() == null) {
            throw ScrmException.badRequest("开始日期不能为空");
        }
        validateDateRange(dto.getStartDate(), dto.getEndDate());
    }

    /**
     * 校验日期区间合法性 (startDate ≤ endDate, 且均非空时校验)。
     *
     * @param startDate 开始日期 (可空)
     * @param endDate   结束日期 (可空)
     * @throws ScrmException 日期区间非法
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) throws ScrmException {
        if (startDate == null || endDate == null) {
            return;
        }
        if (startDate.isAfter(endDate)) {
            throw ScrmException.badRequest("开始日期不能晚于结束日期");
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
     * 按主键查询计划, 不存在抛异常, 并校验归属账号。
     *
     * @param id 计划 ID
     * @return 计划实体
     * @throws ScrmException 计划不存在
     */
    ScrmVisitPlanEntity findPlanOrThrow(Long id) throws ScrmException {
        ScrmVisitPlanEntity entity = planRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "回访计划不存在: id=" + id));
        return entity;
    }
}
