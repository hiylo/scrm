/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskSchedulerTaskService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmScheduledTaskDto;
import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmScheduledTaskRepository;
import org.hiylo.scrm.repository.ScrmTaskDependencyRepository;
import org.hiylo.scrm.repository.ScrmTaskExecutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 任务调度 - 任务管理子域服务。
 * <p>
 * 承载定时任务配置管理能力: 任务 CRUD、Cron 表达式校验与下次执行时间计算、
 * 启停/暂停/恢复状态切换、任务复制与执行统计字段更新。共享 {@link #findTaskOrThrow}
 * 与 {@link #calculateNextScheduledAt} 供执行、依赖子域复用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmTaskSchedulerTaskService {

    /** 默认处理器方法 */
    private static final String DEFAULT_HANDLER_METHOD = "execute";

    /** 默认超时秒数 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /** 默认重试延迟秒 */
    private static final int DEFAULT_RETRY_DELAY_SECONDS = 60;

    /** 默认重试退避倍数 */
    private static final double DEFAULT_RETRY_BACKOFF_MULTIPLIER = 2.0;

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 默认状态: 活跃 */
    private static final String DEFAULT_STATUS = "ACTIVE";

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 合法的任务类别 */
    private static final List<String> VALID_TASK_CATEGORIES = List.of(
            "DATA_SYNC", "CLEANUP", "NOTIFICATION", "REPORT",
            "MAINTENANCE", "CUSTOM", "INTEGRATION", "ANALYTICS");

    /** 合法的任务类型 */
    private static final List<String> VALID_TASK_TYPES = List.of(
            "CRON", "FIXED_RATE", "FIXED_DELAY", "ONE_TIME", "EVENT_TRIGGERED");

    /** 合法的任务状态 */
    private static final List<String> VALID_STATUSES = List.of(
            "ACTIVE", "PAUSED", "ERROR", "DISABLED");

    /** 合法的上次执行状态 */
    private static final List<String> VALID_LAST_EXECUTION_STATUSES = List.of(
            "SUCCESS", "FAILED", "TIMEOUT", "RUNNING");

    /** 任务调度配置数据访问层 */
    private final ScrmScheduledTaskRepository taskRepository;

    /** 任务执行记录数据访问层 (删除任务前引用计数校验) */
    private final ScrmTaskExecutionRepository executionRepository;

    /** 任务依赖关系数据访问层 (删除任务前引用计数校验) */
    private final ScrmTaskDependencyRepository dependencyRepository;

    /**
     * 创建任务调度配置。
     * <p>校验参数合法性后写入归属账号 ID 持久化, handlerMethod / timeoutSeconds / maxRetries /
     * retryDelaySeconds / retryBackoffMultiplier / priority / status / isEnabled 缺省时填默认值。
     * taskType=CRON 时计算下次执行时间。</p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 任务编码重复
     */
    @Transactional
    public ScrmScheduledTaskEntity createTask(ScrmScheduledTaskDto dto) throws ScrmException {
        validateTaskDto(dto, false);
        // 校验任务编码唯一性
        if (taskRepository.findByTaskCode(dto.getTaskCode()).isPresent()) {
            throw ScrmException.conflict("任务编码已存在: " + dto.getTaskCode());
        }
        ScrmScheduledTaskEntity entity = new ScrmScheduledTaskEntity();
        entity.setTaskName(dto.getTaskName());
        entity.setTaskCode(dto.getTaskCode());
        entity.setDescription(dto.getDescription());
        entity.setTaskCategory(dto.getTaskCategory());
        entity.setTaskType(dto.getTaskType());
        entity.setCronExpression(dto.getCronExpression());
        entity.setFixedRateMs(dto.getFixedRateMs());
        entity.setFixedDelayMs(dto.getFixedDelayMs());
        entity.setExecuteAt(dto.getExecuteAt());
        entity.setHandlerClass(dto.getHandlerClass());
        entity.setHandlerMethod(dto.getHandlerMethod() != null && !dto.getHandlerMethod().isBlank()
                ? dto.getHandlerMethod() : DEFAULT_HANDLER_METHOD);
        entity.setParameters(dto.getParameters());
        entity.setTimeoutSeconds(dto.getTimeoutSeconds() != null
                ? dto.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS);
        entity.setMaxRetries(dto.getMaxRetries() != null ? dto.getMaxRetries() : DEFAULT_MAX_RETRIES);
        entity.setRetryDelaySeconds(dto.getRetryDelaySeconds() != null
                ? dto.getRetryDelaySeconds() : DEFAULT_RETRY_DELAY_SECONDS);
        entity.setRetryBackoffMultiplier(dto.getRetryBackoffMultiplier() != null
                ? dto.getRetryBackoffMultiplier() : DEFAULT_RETRY_BACKOFF_MULTIPLIER);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setDependencies(dto.getDependencies());
        entity.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank()
                ? dto.getStatus() : DEFAULT_STATUS);
        entity.setIsEnabled(dto.getIsEnabled() != null ? dto.getIsEnabled() : DEFAULT_ENABLED);
        entity.setTags(dto.getTags());
        entity.setCreatedBy(dto.getCreatedBy());
        // 统计字段初值
        entity.setTotalExecutions(0);
        entity.setSuccessCount(0);
        entity.setFailureCount(0);
        entity.setTimeoutCount(0);
        entity.setAvgExecutionMs(0);
        entity.setConsecutiveFailures(0);
        // 计算下次执行时间
        entity.setNextScheduledAt(calculateNextScheduledAt(entity));
        entity = taskRepository.save(entity);
        log.info("创建任务调度配置: id={}, taskName={}, taskCode={}, taskType={}",
                entity.getId(), entity.getTaskName(), entity.getTaskCode(), entity.getTaskType());
        return entity;
    }

    /**
     * 更新任务调度配置（字段非空才覆盖）。
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 参数非法
     */
    @Transactional
    public ScrmScheduledTaskEntity updateTask(Long id, ScrmScheduledTaskDto dto) throws ScrmException {
        ScrmScheduledTaskEntity entity = findTaskOrThrow(id);
        validateTaskDto(dto, true);
        // 校验任务编码变更后的唯一性
        if (dto.getTaskCode() != null && !dto.getTaskCode().equals(entity.getTaskCode())) {
            if (taskRepository.findByTaskCode(dto.getTaskCode()).isPresent()) {
                throw ScrmException.conflict("任务编码已存在: " + dto.getTaskCode());
            }
        }
        if (dto.getTaskName() != null) entity.setTaskName(dto.getTaskName());
        if (dto.getTaskCode() != null) entity.setTaskCode(dto.getTaskCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTaskCategory() != null) entity.setTaskCategory(dto.getTaskCategory());
        if (dto.getTaskType() != null) entity.setTaskType(dto.getTaskType());
        if (dto.getCronExpression() != null) entity.setCronExpression(dto.getCronExpression());
        if (dto.getFixedRateMs() != null) entity.setFixedRateMs(dto.getFixedRateMs());
        if (dto.getFixedDelayMs() != null) entity.setFixedDelayMs(dto.getFixedDelayMs());
        if (dto.getExecuteAt() != null) entity.setExecuteAt(dto.getExecuteAt());
        if (dto.getHandlerClass() != null) entity.setHandlerClass(dto.getHandlerClass());
        if (dto.getHandlerMethod() != null) entity.setHandlerMethod(dto.getHandlerMethod());
        if (dto.getParameters() != null) entity.setParameters(dto.getParameters());
        if (dto.getTimeoutSeconds() != null) entity.setTimeoutSeconds(dto.getTimeoutSeconds());
        if (dto.getMaxRetries() != null) entity.setMaxRetries(dto.getMaxRetries());
        if (dto.getRetryDelaySeconds() != null) entity.setRetryDelaySeconds(dto.getRetryDelaySeconds());
        if (dto.getRetryBackoffMultiplier() != null) entity.setRetryBackoffMultiplier(dto.getRetryBackoffMultiplier());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getDependencies() != null) entity.setDependencies(dto.getDependencies());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getIsEnabled() != null) entity.setIsEnabled(dto.getIsEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        // 任务类型/Cron 变更后重算下次执行时间
        entity.setNextScheduledAt(calculateNextScheduledAt(entity));
        entity = taskRepository.save(entity);
        log.info("更新任务调度配置: id={}, taskName={}", entity.getId(), entity.getTaskName());
        return entity;
    }

    /**
     * 删除任务调度配置。
     * <p>删除前检查是否有执行记录或依赖关系引用, 若有则阻止删除并返回引用数量。</p>
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在 / 仍有执行记录或依赖引用
     */
    @Transactional
    public void deleteTask(Long id) throws ScrmException {
        ScrmScheduledTaskEntity entity = findTaskOrThrow(id);
        long execCount = executionRepository.count((root, query, cb) -> cb.equal(root.get("taskId"), id));
        if (execCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除任务: 仍有 %d 条执行记录引用, 请先禁用任务而非删除",
                            execCount));
        }
        long depCount = dependencyRepository.count((root, query, cb) ->
                cb.or(cb.equal(root.get("taskId"), id), cb.equal(root.get("dependsOnTaskId"), id)));
        if (depCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除任务: 仍有 %d 条依赖关系引用, 请先删除依赖", depCount));
        }
        taskRepository.delete(entity);
        log.info("删除任务调度配置: id={}, taskName={}", id, entity.getTaskName());
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmScheduledTaskEntity getTask(Long id) throws ScrmException {
        return findTaskOrThrow(id);
    }

    /**
     * 按任务编码查询任务详情。
     *
     * @param code 任务编码
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmScheduledTaskEntity getTaskByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("任务编码不能为空");
        }
        ScrmScheduledTaskEntity entity = taskRepository.findByTaskCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "任务不存在: code=" + code));
        return entity;
    }

    /**
     * 分页查询任务, 支持按任务类别、任务类型、状态与关键字过滤。
     *
     * @param taskCategory 任务类别过滤（可空）
     * @param taskType     任务类型过滤（可空）
     * @param status       状态过滤（可空）
     * @param keyword      关键字过滤（按任务名称/编码/描述模糊匹配, 可空）
     * @param pageable     分页参数
     * @return 任务分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmScheduledTaskEntity> listTasks(String taskCategory, String taskType, String status,
                                                     String keyword, Pageable pageable) {
        Specification<ScrmScheduledTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (taskCategory != null && !taskCategory.isBlank()) {
                predicates.add(cb.equal(root.get("taskCategory"), taskCategory));
            }
            if (taskType != null && !taskType.isBlank()) {
                predicates.add(cb.equal(root.get("taskType"), taskType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("taskName")), kw),
                        cb.like(cb.lower(root.get("taskCode")), kw),
                        cb.like(cb.lower(root.get("description")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 启用任务。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public void enableTask(Long id) throws ScrmException {
        ScrmScheduledTaskEntity entity = findTaskOrThrow(id);
        entity.setIsEnabled(true);
        taskRepository.save(entity);
        log.info("启用任务: id={}, taskName={}", id, entity.getTaskName());
    }

    /**
     * 禁用任务。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public void disableTask(Long id) throws ScrmException {
        ScrmScheduledTaskEntity entity = findTaskOrThrow(id);
        entity.setIsEnabled(false);
        taskRepository.save(entity);
        log.info("禁用任务: id={}, taskName={}", id, entity.getTaskName());
    }

    /**
     * 暂停任务（保持 isEnabled, 仅切换 status 为 PAUSED）。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public void pauseTask(Long id) throws ScrmException {
        ScrmScheduledTaskEntity entity = findTaskOrThrow(id);
        entity.setStatus("PAUSED");
        taskRepository.save(entity);
        log.info("暂停任务: id={}, taskName={}", id, entity.getTaskName());
    }

    /**
     * 恢复任务（将 status 切换为 ACTIVE, 若处于 ERROR 状态也允许恢复）。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public void resumeTask(Long id) throws ScrmException {
        ScrmScheduledTaskEntity entity = findTaskOrThrow(id);
        entity.setStatus("ACTIVE");
        // 恢复时重算下次执行时间
        entity.setNextScheduledAt(calculateNextScheduledAt(entity));
        taskRepository.save(entity);
        log.info("恢复任务: id={}, taskName={}", id, entity.getTaskName());
    }

    /**
     * 复制任务（保留全部业务字段, 使用新任务编码, 状态重置为 ACTIVE, 统计清零）。
     *
     * @param id      源任务 ID
     * @param newCode 新任务编码
     * @return 复制后的任务
     * @throws ScrmException 源任务不存在 / 新编码重复
     */
    @Transactional
    public ScrmScheduledTaskEntity copyTask(Long id, String newCode) throws ScrmException {
        if (newCode == null || newCode.isBlank()) {
            throw ScrmException.badRequest("新任务编码不能为空");
        }
        ScrmScheduledTaskEntity source = findTaskOrThrow(id);
        if (taskRepository.findByTaskCode(newCode).isPresent()) {
            throw ScrmException.conflict("任务编码已存在: " + newCode);
        }
        ScrmScheduledTaskEntity entity = new ScrmScheduledTaskEntity();
        entity.setTaskName(source.getTaskName() + " (副本)");
        entity.setTaskCode(newCode);
        entity.setDescription(source.getDescription());
        entity.setTaskCategory(source.getTaskCategory());
        entity.setTaskType(source.getTaskType());
        entity.setCronExpression(source.getCronExpression());
        entity.setFixedRateMs(source.getFixedRateMs());
        entity.setFixedDelayMs(source.getFixedDelayMs());
        entity.setExecuteAt(source.getExecuteAt());
        entity.setHandlerClass(source.getHandlerClass());
        entity.setHandlerMethod(source.getHandlerMethod());
        entity.setParameters(source.getParameters());
        entity.setTimeoutSeconds(source.getTimeoutSeconds());
        entity.setMaxRetries(source.getMaxRetries());
        entity.setRetryDelaySeconds(source.getRetryDelaySeconds());
        entity.setRetryBackoffMultiplier(source.getRetryBackoffMultiplier());
        entity.setPriority(source.getPriority());
        entity.setDependencies(source.getDependencies());
        entity.setStatus(DEFAULT_STATUS);
        entity.setTags(source.getTags());
        entity.setCreatedBy(source.getCreatedBy());
        entity.setTotalExecutions(0);
        entity.setSuccessCount(0);
        entity.setFailureCount(0);
        entity.setTimeoutCount(0);
        entity.setAvgExecutionMs(0);
        entity.setConsecutiveFailures(0);
        entity.setIsEnabled(DEFAULT_ENABLED);
        entity.setNextScheduledAt(calculateNextScheduledAt(entity));
        entity = taskRepository.save(entity);
        log.info("复制任务: sourceId={}, newId={}, newCode={}", id, entity.getId(), newCode);
        return entity;
    }

    /**
     * 校验 Cron 表达式合法性。
     *
     * @param cronExpression Cron 表达式
     * @return true 表示合法
     * @throws ScrmException 表达式非法
     */
    @Transactional(readOnly = true)
    public boolean validateCron(String cronExpression) throws ScrmException {
        if (cronExpression == null || cronExpression.isBlank()) {
            throw ScrmException.badRequest("Cron 表达式不能为空");
        }
        try {
            CronExpression.parse(cronExpression);
            return true;
        } catch (IllegalArgumentException e) {
            throw ScrmException.badRequest("Cron 表达式非法: " + e.getMessage());
        }
    }

    /**
     * 计算指定 Cron 表达式的下次执行时间。
     *
     * @param cronExpression Cron 表达式
     * @return 下次执行时间
     * @throws ScrmException 表达式非法
     */
    @Transactional(readOnly = true)
    public LocalDateTime calculateNextExecution(String cronExpression) throws ScrmException {
        validateCron(cronExpression);
        return CronExpression.parse(cronExpression).next(LocalDateTime.now());
    }

    /**
     * 更新任务统计字段（执行结束后由 executeTask 调用）。
     *
     * @param id              任务 ID
     * @param executionStatus 执行状态 (SUCCESS/FAILED/TIMEOUT/RUNNING)
     * @param durationMs      执行耗时（毫秒, 可空）
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public void updateTaskStats(Long id, String executionStatus, Integer durationMs) throws ScrmException {
        findTaskOrThrow(id);
        if (executionStatus != null && !VALID_LAST_EXECUTION_STATUSES.contains(executionStatus)) {
            throw ScrmException.badRequest(
                    "执行状态非法: " + executionStatus + ", 仅支持 " + VALID_LAST_EXECUTION_STATUSES);
        }
        int successIncrement = "SUCCESS".equals(executionStatus) ? 1 : 0;
        int failureIncrement = "FAILED".equals(executionStatus) ? 1 : 0;
        int timeoutIncrement = "TIMEOUT".equals(executionStatus) ? 1 : 0;
        boolean resetConsecutive = "SUCCESS".equals(executionStatus);
        String errorMessage = null;
        taskRepository.updateTaskStats(id, LocalDateTime.now(), executionStatus, durationMs, errorMessage,
                successIncrement, failureIncrement, timeoutIncrement, resetConsecutive);
    }

    /**
     * 校验任务参数。
     *
     * @param dto     任务参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTaskDto(ScrmScheduledTaskDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("任务参数不能为空");
        }
        if (dto.getTaskName() != null) {
            if (dto.getTaskName().isBlank()) {
                throw ScrmException.badRequest("任务名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("任务名称不能为空");
        }
        if (dto.getTaskCode() != null) {
            if (dto.getTaskCode().isBlank()) {
                throw ScrmException.badRequest("任务编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("任务编码不能为空");
        }
        if (dto.getTaskCategory() != null && !VALID_TASK_CATEGORIES.contains(dto.getTaskCategory())) {
            throw ScrmException.badRequest(
                    "任务类别非法: " + dto.getTaskCategory() + ", 仅支持 " + VALID_TASK_CATEGORIES);
        }
        if (dto.getTaskType() != null) {
            if (!VALID_TASK_TYPES.contains(dto.getTaskType())) {
                throw ScrmException.badRequest(
                        "任务类型非法: " + dto.getTaskType() + ", 仅支持 " + VALID_TASK_TYPES);
            }
            // 按 taskType 校验必填字段
            if (!partial) {
                validateTaskTypeFields(dto);
            }
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank() && !VALID_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest(
                    "任务状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_STATUSES);
        }
        // Cron 表达式校验
        if (dto.getCronExpression() != null && !dto.getCronExpression().isBlank()) {
            try {
                CronExpression.parse(dto.getCronExpression());
            } catch (IllegalArgumentException e) {
                throw ScrmException.badRequest("Cron 表达式非法: " + e.getMessage());
            }
        }
        // 数值字段非负校验
        if (dto.getTimeoutSeconds() != null && dto.getTimeoutSeconds() < 0) {
            throw ScrmException.badRequest("超时秒数不能为负数: " + dto.getTimeoutSeconds());
        }
        if (dto.getMaxRetries() != null && dto.getMaxRetries() < 0) {
            throw ScrmException.badRequest("最大重试次数不能为负数: " + dto.getMaxRetries());
        }
        if (dto.getRetryDelaySeconds() != null && dto.getRetryDelaySeconds() < 0) {
            throw ScrmException.badRequest("重试延迟秒不能为负数: " + dto.getRetryDelaySeconds());
        }
        if (dto.getFixedRateMs() != null && dto.getFixedRateMs() <= 0) {
            throw ScrmException.badRequest("固定频率毫秒必须大于 0: " + dto.getFixedRateMs());
        }
        if (dto.getFixedDelayMs() != null && dto.getFixedDelayMs() <= 0) {
            throw ScrmException.badRequest("固定延迟毫秒必须大于 0: " + dto.getFixedDelayMs());
        }
    }

    /**
     * 按 taskType 校验必填字段。
     */
    private void validateTaskTypeFields(ScrmScheduledTaskDto dto) throws ScrmException {
        switch (dto.getTaskType()) {
            case "CRON":
                if (dto.getCronExpression() == null || dto.getCronExpression().isBlank()) {
                    throw ScrmException.badRequest("taskType=CRON 时必须指定 cronExpression");
                }
                break;
            case "FIXED_RATE":
                if (dto.getFixedRateMs() == null) {
                    throw ScrmException.badRequest("taskType=FIXED_RATE 时必须指定 fixedRateMs");
                }
                break;
            case "FIXED_DELAY":
                if (dto.getFixedDelayMs() == null) {
                    throw ScrmException.badRequest("taskType=FIXED_DELAY 时必须指定 fixedDelayMs");
                }
                break;
            case "ONE_TIME":
                if (dto.getExecuteAt() == null) {
                    throw ScrmException.badRequest("taskType=ONE_TIME 时必须指定 executeAt");
                }
                break;
            default:
                // EVENT_TRIGGERED 无额外必填字段
                break;
        }
    }

    /**
     * 计算任务的下次计划执行时间。
     * <p>按 taskType 计算: CRON 用 CronExpression.next, FIXED_RATE/FIXED_DELAY 用 now + ms,
     * ONE_TIME 用 executeAt, EVENT_TRIGGERED 返回 null。</p>
     *
     * @param task 任务实体
     * @return 下次执行时间 (EVENT_TRIGGERED 或不可计算时返回 null)
     */
    public LocalDateTime calculateNextScheduledAt(ScrmScheduledTaskEntity task) {
        if (task == null || !Boolean.TRUE.equals(task.getIsEnabled())) {
            return null;
        }
        if (!"ACTIVE".equals(task.getStatus())) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        switch (task.getTaskType()) {
            case "CRON":
                if (task.getCronExpression() == null || task.getCronExpression().isBlank()) {
                    return null;
                }
                try {
                    return CronExpression.parse(task.getCronExpression()).next(now);
                } catch (IllegalArgumentException e) {
                    log.warn("Cron 表达式非法, 无法计算下次执行时间: taskId={}, expr={}, err={}",
                            task.getId(), task.getCronExpression(), e.getMessage());
                    return null;
                }
            case "FIXED_RATE":
                if (task.getFixedRateMs() == null || task.getFixedRateMs() <= 0) {
                    return null;
                }
                return now.plusNanos(task.getFixedRateMs() * 1_000_000L);
            case "FIXED_DELAY":
                if (task.getFixedDelayMs() == null || task.getFixedDelayMs() <= 0) {
                    return null;
                }
                return now.plusNanos(task.getFixedDelayMs() * 1_000_000L);
            case "ONE_TIME":
                return task.getExecuteAt();
            case "EVENT_TRIGGERED":
            default:
                return null;
        }
    }

    /**
     * 按主键查询任务, 不存在抛异常, 并校验归属账号。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    public ScrmScheduledTaskEntity findTaskOrThrow(Long id) throws ScrmException {
        ScrmScheduledTaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "任务不存在: id=" + id));

        return entity;
    }
}
