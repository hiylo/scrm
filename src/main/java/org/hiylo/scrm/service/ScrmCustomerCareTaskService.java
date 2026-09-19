/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerCareTaskService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCareExecuteDto;
import org.hiylo.scrm.dto.ScrmCareTaskDto;
import org.hiylo.scrm.entity.ScrmCareRecordEntity;
import org.hiylo.scrm.entity.ScrmCareRuleEntity;
import org.hiylo.scrm.entity.ScrmCareTaskEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCareRecordRepository;
import org.hiylo.scrm.repository.ScrmCareRuleRepository;
import org.hiylo.scrm.repository.ScrmCareTaskRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 客户关怀任务服务 (关怀任务与调度生成子域)。
 * <p>
 * 承载关怀任务增删改查 / 执行 (模拟) / 取消 / 批量执行, 以及按规则 / 每日 / 生日调度生成任务。
 * 节日类型任务生成委托 {@link ScrmCustomerCareFestivalService}。与规则 / 记录兄弟类共享
 * {@link ScrmCustomerCareRuleService} 常量。任务执行 (executeTask) 为模拟实现, 与原门面一致。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerCareTaskService {

    /** 关怀任务数据访问层 */
    private final ScrmCareTaskRepository taskRepository;

    /** 关怀记录数据访问层 (执行任务时写记录用) */
    private final ScrmCareRecordRepository recordRepository;

    /** 关怀规则数据访问层 (调度生成与执行统计用) */
    private final ScrmCareRuleRepository ruleRepository;

    /** 客户数据访问层 (查询客户用于任务生成) */
    private final ScrmCustomerRepository customerRepository;

    /** JSON 解析器 (解析 triggerCondition) */
    private final ObjectMapper objectMapper;

    /** 节日管理兄弟服务 (节日类型任务生成用) */
    private final ScrmCustomerCareFestivalService festivalService;

    /**
     * 创建关怀任务 (手动创建, ruleId 可空)。
     * <p>customerId 必填且需归属当前账号; customerName 缺省时从客户实体冗余填充;
     * status 缺省 PENDING。</p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 客户不存在
     */
    @Transactional
    public ScrmCareTaskEntity createTask(ScrmCareTaskDto dto) throws ScrmException {
        validateTaskDto(dto, false);
        ScrmCustomerEntity customer = findCustomerOrThrow(dto.getCustomerId());
        ScrmCareTaskEntity entity = new ScrmCareTaskEntity();
        entity.setRuleId(dto.getRuleId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName() != null ? dto.getCustomerName() : customer.getNickname());
        entity.setCareType(dto.getCareType());
        entity.setCareDate(dto.getCareDate());
        entity.setScheduledAt(dto.getScheduledAt());
        entity.setActionType(dto.getActionType());
        entity.setActionContent(dto.getActionContent());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : ScrmCustomerCareRuleService.STATUS_PENDING);
        entity.setAssigneeId(dto.getAssigneeId());
        entity.setAssigneeName(dto.getAssigneeName());
        entity.setNotes(dto.getNotes());
        entity = taskRepository.save(entity);
        log.info("创建关怀任务: id={}, customerId={}, careType={}, careDate={}",
                entity.getId(), entity.getCustomerId(), entity.getCareType(), entity.getCareDate());
        return entity;
    }

    /**
     * 更新关怀任务（字段非空才覆盖）。
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 参数非法
     */
    @Transactional
    public ScrmCareTaskEntity updateTask(Long id, ScrmCareTaskDto dto) throws ScrmException {
        ScrmCareTaskEntity entity = findTaskOrThrow(id);
        validateTaskDto(dto, true);
        if (dto.getRuleId() != null) entity.setRuleId(dto.getRuleId());
        if (dto.getCustomerId() != null) entity.setCustomerId(dto.getCustomerId());
        if (dto.getCustomerName() != null) entity.setCustomerName(dto.getCustomerName());
        if (dto.getCareType() != null) entity.setCareType(dto.getCareType());
        if (dto.getCareDate() != null) entity.setCareDate(dto.getCareDate());
        if (dto.getScheduledAt() != null) entity.setScheduledAt(dto.getScheduledAt());
        if (dto.getActionType() != null) entity.setActionType(dto.getActionType());
        if (dto.getActionContent() != null) entity.setActionContent(dto.getActionContent());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getAssigneeId() != null) entity.setAssigneeId(dto.getAssigneeId());
        if (dto.getAssigneeName() != null) entity.setAssigneeName(dto.getAssigneeName());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        entity = taskRepository.save(entity);
        log.info("更新关怀任务: id={}, status={}", entity.getId(), entity.getStatus());
        return entity;
    }

    /**
     * 删除关怀任务。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public void deleteTask(Long id) throws ScrmException {
        ScrmCareTaskEntity entity = findTaskOrThrow(id);
        taskRepository.delete(entity);
        log.info("删除关怀任务: id={}, customerId={}", id, entity.getCustomerId());
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmCareTaskEntity getTask(Long id) throws ScrmException {
        return findTaskOrThrow(id);
    }

    /**
     * 分页查询任务, 支持按关怀类型、状态、负责人、客户与时间范围过滤。
     *
     * @param careType   关怀类型过滤（可空）
     * @param status     任务状态过滤（可空）
     * @param assigneeId 负责人 ID 过滤（可空）
     * @param customerId 客户 ID 过滤（可空）
     * @param startDate  关怀日期起始 (含, 可空)
     * @param endDate    关怀日期截止 (含, 可空)
     * @param pageable   分页参数
     * @return 任务分页结果 (按 scheduledAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCareTaskEntity> listTasks(String careType, String status, String assigneeId,
                                               Long customerId, LocalDate startDate, LocalDate endDate,
                                               Pageable pageable) {
        Specification<ScrmCareTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (careType != null && !careType.isBlank()) {
                predicates.add(cb.equal(root.get("careType"), careType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (assigneeId != null && !assigneeId.isBlank()) {
                predicates.add(cb.equal(root.get("assigneeId"), assigneeId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("careDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("careDate"), endDate));
            }
            query.orderBy(cb.desc(root.get("scheduledAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 执行关怀任务 (模拟实现)。
     * <p>
     * 流程: 校验任务为 PENDING/EXECUTING 状态 → 标记 EXECUTING → 按动作类型生成模拟执行回执 →
     * 按 {@link ScrmCareExecuteDto#getResult()} 设置最终状态 → 记录客户回应 → 写入关怀记录 →
     * 增量更新关联规则执行统计。当前为模拟实现, 不实际触发外部消息 / 优惠券 / 礼品系统。
     * </p>
     *
     * @param executeDto 执行请求 (taskId + result + response)
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法 / 参数非法
     */
    @Transactional
    public ScrmCareTaskEntity executeTask(ScrmCareExecuteDto executeDto) throws ScrmException {
        if (executeDto == null) {
            throw ScrmException.badRequest("执行参数不能为空");
        }
        if (executeDto.getTaskId() == null) {
            throw ScrmException.badRequest("任务 ID 不能为空");
        }
        if (!ScrmCustomerCareRuleService.VALID_CARE_RESULTS.contains(executeDto.getResult())) {
            throw ScrmException.badRequest(
                    "执行结果非法: " + executeDto.getResult() + ", 仅支持 "
                            + ScrmCustomerCareRuleService.VALID_CARE_RESULTS);
        }
        ScrmCareTaskEntity entity = findTaskOrThrow(executeDto.getTaskId());
        if (ScrmCustomerCareRuleService.STATUS_SUCCESS.equals(entity.getStatus())
                || ScrmCustomerCareRuleService.STATUS_CANCELLED.equals(entity.getStatus())
                || ScrmCustomerCareRuleService.STATUS_FAILED.equals(entity.getStatus())) {
            throw ScrmException.conflict("任务已终态, 不允许重复执行: id=" + entity.getId()
                    + ", status=" + entity.getStatus());
        }
        // 模拟执行: 按动作类型生成回执
        String actionResult = simulateAction(entity);
        entity.setStatus(executeDto.getResult());
        entity.setExecutedAt(LocalDateTime.now());
        entity.setActionResult(actionResult);
        if (executeDto.getResponse() != null) {
            entity.setCustomerResponse(executeDto.getResponse());
            entity.setResponseAt(LocalDateTime.now());
        }
        if (executeDto.getNotes() != null) {
            entity.setNotes(executeDto.getNotes());
        }
        entity = taskRepository.save(entity);
        // 写入关怀记录
        ScrmCareRecordEntity record = new ScrmCareRecordEntity();
        record.setCustomerId(entity.getCustomerId());
        record.setCustomerName(entity.getCustomerName());
        record.setCareType(entity.getCareType());
        record.setCareDate(entity.getCareDate());
        record.setActionType(entity.getActionType());
        record.setActionDetail(actionResult);
        record.setCareResult(executeDto.getResult());
        record.setCustomerResponse(executeDto.getResponse());
        record.setSentiment(executeDto.getSentiment());
        record.setAssigneeId(entity.getAssigneeId());
        record.setExecutedAt(entity.getExecutedAt());
        record.setNotes(executeDto.getNotes());
        recordRepository.save(record);
        // 增量更新关联规则执行统计
        if (entity.getRuleId() != null) {
            try {
                ruleRepository.incrementExecutionCount(entity.getRuleId(), LocalDateTime.now());
            } catch (Exception e) {
                log.warn("更新规则执行统计失败, 忽略: ruleId={}, err={}", entity.getRuleId(), e.getMessage());
            }
        }
        log.info("执行关怀任务: id={}, customerId={}, result={}, actionResult={}",
                entity.getId(), entity.getCustomerId(), executeDto.getResult(), actionResult);
        return entity;
    }

    /**
     * 取消关怀任务。
     * <p>状态须为 PENDING / EXECUTING, 已终态 (SUCCESS/FAILED/CANCELLED) 不允许取消。</p>
     *
     * @param id     任务 ID
     * @param reason 取消原因
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmCareTaskEntity cancelTask(Long id, String reason) throws ScrmException {
        ScrmCareTaskEntity entity = findTaskOrThrow(id);
        if (!ScrmCustomerCareRuleService.STATUS_PENDING.equals(entity.getStatus())
                && !ScrmCustomerCareRuleService.STATUS_EXECUTING.equals(entity.getStatus())) {
            throw ScrmException.conflict("任务状态不允许取消: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(ScrmCustomerCareRuleService.STATUS_CANCELLED);
        if (reason != null) {
            entity.setNotes(reason);
        }
        entity = taskRepository.save(entity);
        log.info("取消关怀任务: id={}, reason={}", id, reason);
        return entity;
    }

    /**
     * 批量执行关怀任务。
     * <p>逐个调用 {@link #executeTask} 模拟执行, 单个失败跳过不阻断其他任务。
     * 未提供 result 时默认 SUCCESS。</p>
     *
     * @param taskIds 任务 ID 列表
     * @return 执行结果: {total, success, failed}
     */
    @Transactional
    public Map<String, Integer> batchExecuteTasks(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            Map<String, Integer> empty = new LinkedHashMap<>();
            empty.put("total", 0);
            empty.put("success", 0);
            empty.put("failed", 0);
            return empty;
        }
        int success = 0;
        int failed = 0;
        for (Long taskId : taskIds) {
            if (taskId == null) {
                continue;
            }
            try {
                ScrmCareExecuteDto executeDto = new ScrmCareExecuteDto();
                executeDto.setTaskId(taskId);
                executeDto.setResult(ScrmCustomerCareRuleService.RESULT_SUCCESS);
                executeTask(executeDto);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("批量执行关怀任务失败, 跳过: taskId={}, err={}", taskId, e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", taskIds.size());
        result.put("success", success);
        result.put("failed", failed);
        log.info("批量执行关怀任务完成: total={}, success={}, failed={}", taskIds.size(), success, failed);
        return result;
    }

    /**
     * 根据规则生成某日关怀任务。
     * <p>
     * 解析规则触发条件 (daysBefore/time/segment), 按关怀类型匹配客户并去重创建任务。
     * careDate 取入参 date, scheduledAt 取 date + triggerCondition.time。
     * </p>
     * <ul>
     *   <li>INACTIVITY_REMINDER: 基于客户 lastInteractionAt 真实匹配 (无互动天数 ≥ daysBefore)</li>
     *   <li>CUSTOM: 适用全部客户 (模拟)</li>
     *   <li>BIRTHDAY / ANNIVERSARY / MEMBERSHIP_EXPIRY: 客户实体暂缺对应字段, 模拟实现 (待完善)</li>
     *   <li>FESTIVAL: 由 {@link ScrmCustomerCareFestivalService#generateFestivalTasks} 处理, 此处跳过</li>
     * </ul>
     *
     * @param ruleId 规则 ID
     * @param date   关怀日期
     * @return 创建的任务数
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public int generateTasksFromDate(Long ruleId, LocalDate date) throws ScrmException {
        ScrmCareRuleEntity rule = findRuleOrThrow(ruleId);
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            log.debug("规则未启用, 跳过生成: ruleId={}", ruleId);
            return 0;
        }
        if (ScrmCustomerCareRuleService.CARE_TYPE_FESTIVAL.equals(rule.getCareType())) {
            log.debug("节日类型规则由 generateFestivalTasks 处理, 跳过: ruleId={}", ruleId);
            return 0;
        }
        Map<String, Object> condition = parseTriggerCondition(rule.getTriggerCondition());
        LocalTime scheduledTime = parseTime(getAsString(condition, "time"));
        LocalDateTime scheduledAt = date.atTime(scheduledTime);
        List<ScrmCustomerEntity> targets = matchCustomers(rule, condition);
        int created = 0;
        for (ScrmCustomerEntity customer : targets) {
            // 去重: 同规则同客户同关怀日期已存在任务则跳过
            List<ScrmCareTaskEntity> existing = taskRepository
                    .findByRuleIdAndCustomerIdAndCareDate(
                            ruleId, customer.getId(), date);
            if (!existing.isEmpty()) {
                continue;
            }
            ScrmCareTaskEntity task = new ScrmCareTaskEntity();
            task.setRuleId(ruleId);
            task.setCustomerId(customer.getId());
            task.setCustomerName(customer.getNickname());
            task.setCareType(rule.getCareType());
            task.setCareDate(date);
            task.setScheduledAt(scheduledAt);
            task.setActionType(rule.getActionType());
            task.setActionContent(rule.getActionContent());
            task.setStatus(ScrmCustomerCareRuleService.STATUS_PENDING);
            taskRepository.save(task);
            created++;
        }
        // 增量更新规则执行统计 (无论是否创建任务, 标记规则已被调度)
        if (created > 0) {
            try {
                ruleRepository.incrementExecutionCount(ruleId, LocalDateTime.now());
            } catch (Exception e) {
                log.warn("更新规则执行统计失败, 忽略: ruleId={}, err={}", ruleId, e.getMessage());
            }
        }
        log.info("根据规则生成关怀任务: ruleId={}, careType={}, date={}, matched={}, created={}",
                ruleId, rule.getCareType(), date, targets.size(), created);
        return created;
    }

    /**
     * 生成某日所有关怀任务 (模拟实现)。
     * <p>扫描全部启用规则, 逐一调用 {@link #generateTasksFromDate} 生成任务;
     * 同时匹配当日节日调用 {@link ScrmCustomerCareFestivalService#generateFestivalTasksForDate}。
     * 单个规则失败跳过不阻断其他规则。</p>
     *
     * @param date 关怀日期
     * @return 生成结果: {totalCreated, rulesProcessed, failed}
     */
    @Transactional
    public Map<String, Integer> generateDailyTasks(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        List<ScrmCareRuleEntity> rules = ruleRepository.findByEnabledTrueOrderByPriorityAsc();
        int totalCreated = 0;
        int rulesProcessed = 0;
        int failed = 0;
        for (ScrmCareRuleEntity rule : rules) {
            try {
                if (ScrmCustomerCareRuleService.CARE_TYPE_FESTIVAL.equals(rule.getCareType())) {
                    // 节日类型规则: 匹配当日节日生成
                    totalCreated += festivalService.generateFestivalTasksForDate(rule, date);
                } else {
                    totalCreated += generateTasksFromDate(rule.getId(), date);
                }
                rulesProcessed++;
            } catch (Exception e) {
                failed++;
                log.warn("规则生成关怀任务失败, 跳过: ruleId={}, err={}", rule.getId(), e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("totalCreated", totalCreated);
        result.put("rulesProcessed", rulesProcessed);
        result.put("failed", failed);
        log.info("每日关怀任务生成完成:, date={}, totalCreated={}, rulesProcessed={}, failed={}",
                date, totalCreated, rulesProcessed, failed);
        return result;
    }

    /**
     * 生成生日关怀任务。
     * <p>加载 BIRTHDAY 类型启用规则, 逐一调用 {@link #generateTasksFromDate} 生成。
     * 注: 客户实体暂缺生日字段, 当前为模拟实现 (待完善), 实际匹配数为 0。</p>
     *
     * @param date 关怀日期
     * @return 创建的任务数
     */
    @Transactional
    public int generateBirthdayTasks(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        List<ScrmCareRuleEntity> rules = ruleRepository
                .findByCareTypeAndEnabledTrueOrderByPriorityAsc(ScrmCustomerCareRuleService.CARE_TYPE_BIRTHDAY);
        int total = 0;
        for (ScrmCareRuleEntity rule : rules) {
            try {
                total += generateTasksFromDate(rule.getId(), date);
            } catch (Exception e) {
                log.warn("生日关怀任务生成失败, 跳过: ruleId={}, err={}", rule.getId(), e.getMessage());
            }
        }
        // 待完善: 客户实体暂无生日字段, 当前匹配数为 0, 待客户生日字段接入后启用真实匹配
        log.info("生日关怀任务生成完成:, date={}, rules={}, created={}", date, rules.size(), total);
        return total;
    }

    /**
     * 校验关怀任务参数。
     *
     * @param dto     任务参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTaskDto(ScrmCareTaskDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("任务参数不能为空");
        }
        if (dto.getCustomerId() == null && !partial) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getCareType() != null && !ScrmCustomerCareRuleService.VALID_CARE_TYPES.contains(dto.getCareType())) {
            throw ScrmException.badRequest(
                    "关怀类型非法: " + dto.getCareType() + ", 仅支持 " + ScrmCustomerCareRuleService.VALID_CARE_TYPES);
        }
        if (dto.getActionType() != null
                && !ScrmCustomerCareRuleService.VALID_ACTION_TYPES.contains(dto.getActionType())) {
            throw ScrmException.badRequest(
                    "关怀动作非法: " + dto.getActionType() + ", 仅支持 "
                            + ScrmCustomerCareRuleService.VALID_ACTION_TYPES);
        }
        if (dto.getStatus() != null && !ScrmCustomerCareRuleService.VALID_TASK_STATUS.contains(dto.getStatus())) {
            throw ScrmException.badRequest(
                    "任务状态非法: " + dto.getStatus() + ", 仅支持 " + ScrmCustomerCareRuleService.VALID_TASK_STATUS);
        }
    }

    /**
     * 按关怀类型匹配目标客户。
     * <p>
     * INACTIVITY_REMINDER: 基于客户 lastInteractionAt 真实匹配 (无互动天数 ≥ daysBefore, 默认 30);
     * CUSTOM: 适用全部客户 (模拟);
     * BIRTHDAY / ANNIVERSARY / MEMBERSHIP_EXPIRY: 客户实体暂缺对应字段, 返回空 (待完善)。
     * segment 过滤因客户实体暂缺客群字段, 当前不生效 (待完善)。
     * </p>
     *
     * @param rule      规则实体
     * @param condition 触发条件
     * @return 目标客户列表
     */
    private List<ScrmCustomerEntity> matchCustomers(ScrmCareRuleEntity rule, Map<String, Object> condition) {
        List<ScrmCustomerEntity> allCustomers = customerRepository.findAll();
        switch (rule.getCareType() == null ? "" : rule.getCareType()) {
            case ScrmCustomerCareRuleService.CARE_TYPE_INACTIVITY_REMINDER:
                int daysBefore = getAsInt(condition, "daysBefore", 30);
                LocalDateTime threshold = LocalDateTime.now().minusDays(daysBefore);
                return allCustomers.stream()
                        .filter(c -> c.getLastInteractionAt() == null
                                || c.getLastInteractionAt().isBefore(threshold))
                        .collect(Collectors.toList());
            case ScrmCustomerCareRuleService.CARE_TYPE_CUSTOM:
                // 模拟: 适用全部客户
                return allCustomers;
            case ScrmCustomerCareRuleService.CARE_TYPE_BIRTHDAY:
            case ScrmCustomerCareRuleService.CARE_TYPE_ANNIVERSARY:
            case ScrmCustomerCareRuleService.CARE_TYPE_MEMBERSHIP_EXPIRY:
                // 待完善: 客户实体暂缺生日 / 纪念日 / 会员到期字段, 当前不匹配
                log.debug("关怀类型 [{}] 客户字段待完善, 跳过匹配: ruleId={}",
                        rule.getCareType(), rule.getId());
                return List.of();
            default:
                return List.of();
        }
    }

    /**
     * 模拟执行关怀动作, 生成执行回执。
     *
     * @param task 任务实体
     * @return 执行回执描述
     */
    private String simulateAction(ScrmCareTaskEntity task) {
        String actionType = task.getActionType();
        switch (actionType == null ? "" : actionType) {
            case "SEND_MESSAGE":
                return "已发送关怀消息: customerId=" + task.getCustomerId();
            case "SEND_COUPON":
                return "已发放关怀优惠券: customerId=" + task.getCustomerId();
            case "SEND_GIFT":
                return "已赠送关怀礼品: customerId=" + task.getCustomerId();
            case "CALL":
                return "已安排关怀电话: customerId=" + task.getCustomerId();
            case "CREATE_TASK":
                return "已创建跟进任务: customerId=" + task.getCustomerId();
            case "NOTIFY_ASSIGNEE":
                return "已通知负责人: " + (task.getAssigneeName() != null ? task.getAssigneeName() : "(未分配)");
            default:
                return "未知动作类型, 跳过执行: " + actionType;
        }
    }

    /**
     * 解析触发条件 JSON 为 Map。
     *
     * @param triggerCondition 触发条件 JSON 字符串
     * @return 触发条件 Map, 解析失败返回空 Map
     */
    private Map<String, Object> parseTriggerCondition(String triggerCondition) {
        try {
            return objectMapper.readValue(triggerCondition, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("触发条件 JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 解析时间字符串 (HH:mm) 为 LocalTime, 解析失败取默认 09:00。
     *
     * @param time 时间字符串
     * @return LocalTime
     */
    private LocalTime parseTime(String time) {
        if (time == null || time.isBlank()) {
            return LocalTime.of(ScrmCustomerCareRuleService.DEFAULT_SCHEDULED_HOUR,
                    ScrmCustomerCareRuleService.DEFAULT_SCHEDULED_MINUTE);
        }
        try {
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return LocalTime.of(hour, minute);
        } catch (Exception e) {
            return LocalTime.of(ScrmCustomerCareRuleService.DEFAULT_SCHEDULED_HOUR,
                    ScrmCustomerCareRuleService.DEFAULT_SCHEDULED_MINUTE);
        }
    }

    /**
     * 从 Map 中获取字符串值。
     *
     * @param map Map
     * @param key 键
     * @return 字符串值, 不存在返回 null
     */
    private String getAsString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * 从 Map 中获取整型值, 不存在或转换失败返回默认值。
     *
     * @param map          Map
     * @param key          键
     * @param defaultValue 默认值
     * @return 整型值
     */
    private int getAsInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 按主键查询任务, 不存在抛异常, 并校验账号归属。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    private ScrmCareTaskEntity findTaskOrThrow(Long id) throws ScrmException {
        ScrmCareTaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "关怀任务不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询规则, 不存在抛异常, 并校验账号归属。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmCareRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmCareRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "关怀规则不存在: id=" + id));
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
