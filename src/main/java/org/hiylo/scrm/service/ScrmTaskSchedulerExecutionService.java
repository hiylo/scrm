/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskSchedulerExecutionService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmTaskExecuteDto;
import org.hiylo.scrm.dto.ScrmTaskRetryDto;
import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.hiylo.scrm.entity.ScrmTaskExecutionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmScheduledTaskRepository;
import org.hiylo.scrm.repository.ScrmTaskExecutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SCRM 任务调度 - 执行与重试子域服务。
 * <p>
 * 承载任务执行记录流转 (PENDING→RUNNING→SUCCESS/FAILED/TIMEOUT)、失败重试 (指数退避)、
 * 执行超时处理与进度上报。真实调用 {@link ScheduledTaskHandlerRegistry} 白名单内的处理器,
 * 共享 {@link ScrmTaskSchedulerTaskService} 的任务查询与下次执行时间计算能力。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmTaskSchedulerExecutionService {

    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /** 默认重试延迟秒 */
    private static final int DEFAULT_RETRY_DELAY_SECONDS = 60;

    /** 默认重试退避倍数 */
    private static final double DEFAULT_RETRY_BACKOFF_MULTIPLIER = 2.0;

    /** 最大重试延迟上限 (1 小时, 单位秒) */
    private static final int MAX_RETRY_DELAY_SECONDS = 3600;

    /** 默认执行进度 */
    private static final int DEFAULT_PROGRESS = 0;

    /** 完成进度 */
    private static final int COMPLETE_PROGRESS = 100;

    /** 执行编号前缀 */
    private static final String EXECUTION_NO_PREFIX = "EXEC-";

    /** 执行记录错误信息字段长度上限 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    /** 执行记录返回值字段长度上限 */
    private static final int MAX_RETURN_VALUE_LENGTH = 2000;

    /** 默认 worker ID */
    private static final String DEFAULT_WORKER_ID = "scrm-worker-0";

    /** 默认 worker 名称 */
    private static final String DEFAULT_WORKER_NAME = "scrm-scheduler";

    /** 时间格式 (yyyy-MM-dd HH:mm:ss) */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 任务执行记录数据访问层 */
    private final ScrmTaskExecutionRepository executionRepository;

    /** 任务调度配置数据访问层 */
    private final ScrmScheduledTaskRepository taskRepository;

    /** 调度处理器白名单注册表 (唯一允许的 handlerClass 来源) */
    private final ScheduledTaskHandlerRegistry handlerRegistry;

    /** 任务管理子域服务 (共享任务查询与下次执行时间计算) */
    private final ScrmTaskSchedulerTaskService taskService;

    /**
     * 执行任务。
     * <p>
     * 流程: 创建执行记录 (PENDING) → 更新为 RUNNING (记录 startedAt) → 白名单内调用真实处理器
     * ({@link #invokeHandler}) → 记录执行结果与耗时 → 更新任务统计与 nextScheduledAt。
     * 失败时记录错误信息, 触发重试逻辑由 {@link #retryExecution} 单独处理。
     * </p>
     *
     * @param executeDto 执行参数 (任务 ID + 本次参数 + 触发者)
     * @return 执行记录实体 (终态)
     * @throws ScrmException 任务不存在 / 状态不允许执行
     */
    @Transactional
    public ScrmTaskExecutionEntity executeTask(ScrmTaskExecuteDto executeDto) throws ScrmException {
        if (executeDto == null || executeDto.getTaskId() == null) {
            throw ScrmException.badRequest("执行参数与任务 ID 不能为空");
        }
        ScrmScheduledTaskEntity task = taskService.findTaskOrThrow(executeDto.getTaskId());
        // 校验任务可执行
        if (!Boolean.TRUE.equals(task.getIsEnabled())) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_TASK_INVALID_STATE,
                    "任务未启用, 无法执行: id=" + task.getId());
        }
        if ("DISABLED".equals(task.getStatus()) || "PAUSED".equals(task.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_TASK_INVALID_STATE,
                    "任务状态为 " + task.getStatus() + ", 无法执行: id=" + task.getId());
        }
        // 创建执行记录 (PENDING)
        ScrmTaskExecutionEntity execution = new ScrmTaskExecutionEntity();
        execution.setTaskId(task.getId());
        execution.setTaskName(task.getTaskName());
        execution.setTaskCode(task.getTaskCode());
        execution.setExecutionNo(generateExecutionNo());
        execution.setTriggerType("MANUAL");
        execution.setStatus("PENDING");
        execution.setScheduledAt(LocalDateTime.now());
        execution.setParameters(executeDto.getParameters() != null ? executeDto.getParameters()
                : task.getParameters());
        execution.setTriggeredBy(executeDto.getTriggeredBy());
        execution.setRetryCount(0);
        execution.setMaxRetries(task.getMaxRetries());
        execution.setIsRetried(false);
        execution.setProgress(DEFAULT_PROGRESS);
        execution.setWorkerId(DEFAULT_WORKER_ID);
        execution.setWorkerName(DEFAULT_WORKER_NAME);
        execution = executionRepository.save(execution);
        log.info("创建任务执行记录: executionId={}, taskId={}, taskCode={}, executionNo={}",
                execution.getId(), task.getId(), task.getTaskCode(), execution.getExecutionNo());

        // 执行 (PENDING → RUNNING → 终态)
        return runExecution(execution, task);
    }

    /**
     * 查询执行记录详情。
     *
     * @param id 执行 ID
     * @return 执行记录实体
     * @throws ScrmException 执行记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmTaskExecutionEntity getExecution(Long id) throws ScrmException {
        ScrmTaskExecutionEntity entity = executionRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "任务执行记录不存在: id=" + id));

        return entity;
    }

    /**
     * 按执行编号查询执行记录详情。
     *
     * @param executionNo 执行编号
     * @return 执行记录实体
     * @throws ScrmException 执行记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmTaskExecutionEntity getExecutionByNo(String executionNo) throws ScrmException {
        if (executionNo == null || executionNo.isBlank()) {
            throw ScrmException.badRequest("执行编号不能为空");
        }
        return executionRepository.findByExecutionNo(executionNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "任务执行记录不存在: executionNo=" + executionNo));
    }

    /**
     * 分页查询执行记录, 支持按任务 ID、状态、触发类型与时间范围过滤。
     *
     * @param taskId     任务 ID 过滤（可空）
     * @param status     执行状态过滤（可空）
     * @param triggerType 触发类型过滤（可空）
     * @param startTime  起始时间 (含, 可空)
     * @param endTime    截止时间 (含, 可空)
     * @param pageable   分页参数
     * @return 执行记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmTaskExecutionEntity> listExecutions(Long taskId, String status, String triggerType,
                                                          LocalDateTime startTime, LocalDateTime endTime,
                                                          Pageable pageable) {
        Specification<ScrmTaskExecutionEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (taskId != null) {
                predicates.add(cb.equal(root.get("taskId"), taskId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (triggerType != null && !triggerType.isBlank()) {
                predicates.add(cb.equal(root.get("triggerType"), triggerType));
            }
            if (startTime != null && endTime != null) {
                predicates.add(cb.between(root.get("startedAt"), startTime, endTime));
            } else if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), startTime));
            } else if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return executionRepository.findAll(spec, pageable);
    }

    /**
     * 取消执行（仅 PENDING/RUNNING 状态可取消）。
     *
     * @param id     执行 ID
     * @param reason 取消原因
     * @return 更新后的执行记录
     * @throws ScrmException 执行记录不存在 / 状态不允许取消
     */
    @Transactional
    public ScrmTaskExecutionEntity cancelExecution(Long id, String reason) throws ScrmException {
        ScrmTaskExecutionEntity execution = getExecution(id);
        if (!"PENDING".equals(execution.getStatus()) && !"RUNNING".equals(execution.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_TASK_INVALID_STATE,
                    "执行状态为 " + execution.getStatus() + ", 不允许取消");
        }
        executionRepository.updateStatus(id, "CANCELLED", execution.getStartedAt(), LocalDateTime.now());
        execution.setStatus("CANCELLED");
        execution.setCompletedAt(LocalDateTime.now());
        if (reason != null && !reason.isBlank()) {
            execution.setErrorMessage("取消原因: " + reason);
        }
        // 重新读取保证与数据库一致
        return executionRepository.findById(id).orElse(execution);
    }

    /**
     * 查询运行中（RUNNING）的执行记录。
     *
     * @return 运行中执行记录列表
     */
    @Transactional(readOnly = true)
    public List<ScrmTaskExecutionEntity> getRunningExecutions() {
        return executionRepository.findByStatus("RUNNING");
    }

    /**
     * 查询任务最近一次执行记录。
     *
     * @param taskId 任务 ID
     * @return 最近一次执行记录（不存在时返回 null）
     */
    @Transactional(readOnly = true)
    public ScrmTaskExecutionEntity getLatestExecution(Long taskId) {
        return executionRepository.findFirstByTaskIdOrderByStartedAtDesc(taskId).orElse(null);
    }

    /**
     * 带重试执行任务。
     * <p>retryCount > 0 时将 triggerType 设为 RETRY, 超过 maxRetries 时抛出异常。</p>
     *
     * @param taskId     任务 ID
     * @param parameters 本次参数
     * @param retryCount 当前重试次数
     * @return 执行记录实体
     * @throws ScrmException 任务不存在 / 超过最大重试次数
     */
    @Transactional
    public ScrmTaskExecutionEntity executeTaskWithRetry(Long taskId, String parameters, int retryCount)
            throws ScrmException {
        ScrmScheduledTaskEntity task = taskService.findTaskOrThrow(taskId);
        int maxRetries = task.getMaxRetries() != null ? task.getMaxRetries() : DEFAULT_MAX_RETRIES;
        if (retryCount > maxRetries) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_TASK_INVALID_STATE,
                    "超过最大重试次数: maxRetries=" + maxRetries + ", retryCount=" + retryCount);
        }
        ScrmTaskExecutionEntity execution = new ScrmTaskExecutionEntity();
        execution.setTaskId(task.getId());
        execution.setTaskName(task.getTaskName());
        execution.setTaskCode(task.getTaskCode());
        execution.setExecutionNo(generateExecutionNo());
        execution.setTriggerType(retryCount > 0 ? "RETRY" : "MANUAL");
        execution.setStatus("PENDING");
        execution.setScheduledAt(LocalDateTime.now());
        execution.setParameters(parameters != null ? parameters : task.getParameters());
        execution.setRetryCount(retryCount);
        execution.setMaxRetries(maxRetries);
        execution.setIsRetried(retryCount > 0);
        execution.setProgress(DEFAULT_PROGRESS);
        execution.setWorkerId(DEFAULT_WORKER_ID);
        execution.setWorkerName(DEFAULT_WORKER_NAME);
        execution = executionRepository.save(execution);
        log.info("创建任务执行记录 (带重试): executionId={}, taskId={}, retryCount={}",
                execution.getId(), task.getId(), retryCount);
        return runExecution(execution, task);
    }

    /**
     * 处理执行超时（将 RUNNING 状态更新为 TIMEOUT 并更新任务统计）。
     *
     * @param executionId 执行 ID
     * @return 更新后的执行记录
     * @throws ScrmException 执行记录不存在 / 状态非 RUNNING
     */
    @Transactional
    public ScrmTaskExecutionEntity handleTimeout(Long executionId) throws ScrmException {
        ScrmTaskExecutionEntity execution = getExecution(executionId);
        if (!"RUNNING".equals(execution.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_TASK_INVALID_STATE,
                    "执行状态为 " + execution.getStatus() + ", 不允许处理超时");
        }
        ScrmScheduledTaskEntity task = taskRepository.findById(execution.getTaskId()).orElse(null);
        int durationMs = execution.getStartedAt() != null
                ? (int) java.time.Duration.between(execution.getStartedAt(), LocalDateTime.now()).toMillis()
                : 0;
        executionRepository.updateResult(executionId, "TIMEOUT", LocalDateTime.now(), durationMs,
                null, null, "执行超时", null);
        if (task != null) {
            taskRepository.updateTaskStats(task.getId(), LocalDateTime.now(), "TIMEOUT",
                    durationMs, "执行超时", 0, 0, 1, false);
        }
        log.warn("任务执行超时: executionId={}, taskId={}, durationMs={}",
                executionId, execution.getTaskId(), durationMs);
        return executionRepository.findById(executionId).orElse(execution);
    }

    /**
     * 更新执行进度。
     *
     * @param executionId 执行 ID
     * @param progress    进度 0-100
     * @param message     进度消息
     * @throws ScrmException 执行记录不存在 / 进度非法
     */
    @Transactional
    public void updateProgress(Long executionId, Integer progress, String message) throws ScrmException {
        ScrmTaskExecutionEntity execution = getExecution(executionId);
        if (progress == null || progress < 0 || progress > COMPLETE_PROGRESS) {
            throw ScrmException.badRequest("进度必须在 0-100 之间: " + progress);
        }
        if ("RUNNING".equals(execution.getStatus()) || "PENDING".equals(execution.getStatus())) {
            executionRepository.updateProgress(executionId, progress, message);
            log.debug("更新执行进度: executionId={}, progress={}, message={}", executionId, progress, message);
        }
    }

    /**
     * 重试执行（计算退避延迟 → 创建新执行 → 执行）。
     *
     * @param retryDto 重试参数 (原执行 ID + 可选自定义延迟秒)
     * @return 新创建的执行记录
     * @throws ScrmException 原执行记录不存在 / 状态不允许重试
     */
    @Transactional
    public ScrmTaskExecutionEntity retryExecution(ScrmTaskRetryDto retryDto) throws ScrmException {
        if (retryDto == null || retryDto.getExecutionId() == null) {
            throw ScrmException.badRequest("重试参数与执行 ID 不能为空");
        }
        ScrmTaskExecutionEntity original = getExecution(retryDto.getExecutionId());
        if (!"FAILED".equals(original.getStatus()) && !"TIMEOUT".equals(original.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_TASK_INVALID_STATE,
                    "执行状态为 " + original.getStatus() + ", 不允许重试");
        }
        ScrmScheduledTaskEntity task = taskRepository.findById(original.getTaskId())
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "任务不存在: id=" + original.getTaskId()));
        int nextRetryCount = (original.getRetryCount() != null ? original.getRetryCount() : 0) + 1;
        int maxRetries = task.getMaxRetries() != null ? task.getMaxRetries() : DEFAULT_MAX_RETRIES;
        if (nextRetryCount > maxRetries) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_TASK_INVALID_STATE,
                    "超过最大重试次数: maxRetries=" + maxRetries + ", retryCount=" + nextRetryCount);
        }
        // 计算退避延迟
        int delaySeconds = retryDto.getDelaySeconds() != null ? retryDto.getDelaySeconds()
                : calculateRetryDelay(nextRetryCount,
                        task.getRetryDelaySeconds() != null ? task.getRetryDelaySeconds() : DEFAULT_RETRY_DELAY_SECONDS,
                        task.getRetryBackoffMultiplier() != null ? task.getRetryBackoffMultiplier()
                                : DEFAULT_RETRY_BACKOFF_MULTIPLIER);
        // 创建新执行记录
        ScrmTaskExecutionEntity execution = new ScrmTaskExecutionEntity();
        execution.setTaskId(task.getId());
        execution.setTaskName(task.getTaskName());
        execution.setTaskCode(task.getTaskCode());
        execution.setExecutionNo(generateExecutionNo());
        execution.setTriggerType("RETRY");
        execution.setStatus("PENDING");
        execution.setScheduledAt(LocalDateTime.now().plusSeconds(delaySeconds));
        execution.setParameters(original.getParameters() != null ? original.getParameters() : task.getParameters());
        execution.setTriggeredBy(original.getTriggeredBy());
        execution.setTriggeredByName(original.getTriggeredByName());
        execution.setDependencyExecutionId(original.getDependencyExecutionId());
        execution.setRetryCount(nextRetryCount);
        execution.setMaxRetries(maxRetries);
        execution.setIsRetried(true);
        execution.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
        execution.setProgress(DEFAULT_PROGRESS);
        execution.setWorkerId(DEFAULT_WORKER_ID);
        execution.setWorkerName(DEFAULT_WORKER_NAME);
        execution = executionRepository.save(execution);
        // 关联原执行的 retryExecutionId
        executionRepository.updateRetryExecutionId(original.getId(), execution.getId());
        log.info("创建重试执行记录: originalId={}, newExecutionId={}, taskId={}, retryCount={}, delaySeconds={}",
                original.getId(), execution.getId(), task.getId(), nextRetryCount, delaySeconds);
        return runExecution(execution, task);
    }

    /**
     * 计算重试延迟（指数退避: baseDelay * multiplier^retryCount, 上限 1 小时）。
     *
     * @param retryCount       当前重试次数
     * @param baseDelay        基础延迟秒
     * @param backoffMultiplier 退避倍数
     * @return 重试延迟秒数
     */
    public int calculateRetryDelay(int retryCount, int baseDelay, double backoffMultiplier) {
        if (retryCount <= 0 || baseDelay <= 0) {
            return baseDelay > 0 ? baseDelay : DEFAULT_RETRY_DELAY_SECONDS;
        }
        double delay = baseDelay * Math.pow(backoffMultiplier, retryCount);
        return (int) Math.min(delay, MAX_RETRY_DELAY_SECONDS);
    }

    /**
     * 获取最大重试延迟上限（1 小时）。
     *
     * @return 最大重试延迟秒数
     */
    public int getMaxRetryDelay() {
        return MAX_RETRY_DELAY_SECONDS;
    }

    /**
     * 生成唯一执行编号。
     *
     * @return 执行编号
     */
    private String generateExecutionNo() {
        return EXECUTION_NO_PREFIX + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 执行任务（PENDING → RUNNING → 终态）。
     * <p>
     * 流程: 创建执行记录 (PENDING) → 更新为 RUNNING (记录 startedAt) → 白名单内调用真实处理器 →
     * 记录执行结果与耗时 → 更新任务统计与 nextScheduledAt。
     * 失败时记录错误信息, 触发重试逻辑由 {@link #retryExecution} 单独处理。
     * </p>
     *
     * @param execution 执行记录
     * @param task      任务实体
     * @return 终态执行记录
     */
    private ScrmTaskExecutionEntity runExecution(ScrmTaskExecutionEntity execution,
                                                  ScrmScheduledTaskEntity task) {
        long startMs = System.currentTimeMillis();
        // PENDING → RUNNING
        executionRepository.updateStatus(execution.getId(), "RUNNING", LocalDateTime.now(), null);
        execution.setStatus("RUNNING");
        execution.setStartedAt(LocalDateTime.now());
        // 进度上报 (处理器自身无进度回调, 以固定中点表示已开始)
        executionRepository.updateProgress(execution.getId(), 50, "执行中");
        // 调用白名单内的真实处理器
        HandlerInvocationResult invocationResult = invokeHandler(task, execution);
        int durationMs = (int) (System.currentTimeMillis() - startMs);
        // 记录结果
        executionRepository.updateResult(execution.getId(),
                invocationResult.success() ? "SUCCESS" : "FAILED",
                LocalDateTime.now(), durationMs,
                invocationResult.result(), invocationResult.returnValue(),
                invocationResult.errorMessage(), invocationResult.errorStack());
        executionRepository.updateProgress(execution.getId(), COMPLETE_PROGRESS,
                invocationResult.success() ? "完成" : "失败");
        // 更新任务统计
        taskRepository.updateTaskStats(task.getId(), LocalDateTime.now(),
                invocationResult.success() ? "SUCCESS" : "FAILED", durationMs,
                invocationResult.errorMessage(),
                invocationResult.success() ? 1 : 0,
                invocationResult.success() ? 0 : 1,
                0,
                invocationResult.success());
        // 重算下次执行时间
        LocalDateTime nextScheduledAt = taskService.calculateNextScheduledAt(task);
        if (nextScheduledAt != null) {
            taskRepository.updateNextScheduledAt(task.getId(), nextScheduledAt);
        }
        // 重新读取终态记录
        ScrmTaskExecutionEntity result = executionRepository.findById(execution.getId()).orElse(execution);
        log.info("任务执行完成: executionId={}, taskId={}, status={}, durationMs={}",
                result.getId(), task.getId(), result.getStatus(), durationMs);
        return result;
    }

    /**
     * 调用任务处理器。
     * <p>
     * 只接受 {@link ScheduledTaskHandlerRegistry} 白名单内的 {@code handlerClass}, 命中后调用
     * {@link ScheduledTaskHandler#handle} (处理器内部方法不作为可选项, 不做反射调用), 因此:
     * </p>
     * <ul>
     *   <li>白名单为空 或 handlerClass 未注册 → 直接记为 FAILED, 错误信息写明「未在白名单注册」</li>
     *   <li>handlerMethod 不在约定集合内 → 记为 FAILED, 提示只允许 {@code handle}</li>
     *   <li>处理器抛异常 → 记为 FAILED 并保留异常信息与堆栈</li>
     * </ul>
     * <p>
     * 绝不以 {@code Class.forName(handlerClass)} 反射执行数据库里存储的任意类名: 该表对用户可写,
     * 反射等于把任意代码执行权限交给任何能写表的账号。
     * </p>
     *
     * @param task      任务实体
     * @param execution 执行记录
     * @return 调用结果
     */
    private HandlerInvocationResult invokeHandler(ScrmScheduledTaskEntity task,
                                                   ScrmTaskExecutionEntity execution) {
        String handlerClass = task.getHandlerClass();
        Optional<ScheduledTaskHandler> handler = handlerRegistry.find(handlerClass);
        if (handler.isEmpty()) {
            String reason = handlerRegistry.isEmpty()
                    ? "调度处理器白名单为空 (容器内无 ScheduledTaskHandler bean), 拒绝执行"
                    : "处理器未在白名单注册: handlerClass=" + handlerClass
                            + ", 白名单=" + handlerRegistry.registeredHandlers();
            log.warn("处理器调用被拒绝: taskId={}, taskCode={}, reason={}",
                    task.getId(), task.getTaskCode(), reason);
            return new HandlerInvocationResult(false,
                    handlerResultJson(task, execution, false, null, reason),
                    null, truncate(reason, MAX_ERROR_MESSAGE_LENGTH), null);
        }
        if (!handlerRegistry.isAllowedMethod(task.getHandlerMethod())) {
            String reason = "处理器方法名不允许: handlerMethod=" + task.getHandlerMethod()
                    + ", 仅支持 " + ScheduledTaskHandlerRegistry.ALLOWED_HANDLER_METHODS
                    + " (统一调用 ScheduledTaskHandler.handle)";
            log.warn("处理器方法名校验失败: taskId={}, handlerClass={}", task.getId(), handlerClass);
            return new HandlerInvocationResult(false,
                    handlerResultJson(task, execution, false, null, reason),
                    null, truncate(reason, MAX_ERROR_MESSAGE_LENGTH), null);
        }
        log.info("调用处理器: taskId={}, taskCode={}, handlerClass={}, executionNo={}",
                task.getId(), task.getTaskCode(), handlerClass, execution.getExecutionNo());
        try {
            String returnValue = handlerRegistry.invoke(handler.get(), task);
            String truncated = truncate(returnValue, MAX_RETURN_VALUE_LENGTH);
            return new HandlerInvocationResult(true,
                    handlerResultJson(task, execution, true, truncated, null), truncated, null, null);
        } catch (Exception e) {
            String errorMessage = truncate(e.getClass().getSimpleName() + ": " + e.getMessage(),
                    MAX_ERROR_MESSAGE_LENGTH);
            log.error("处理器执行失败: taskId={}, handlerClass={}, err={}",
                    task.getId(), handlerClass, e.getMessage(), e);
            return new HandlerInvocationResult(false,
                    handlerResultJson(task, execution, false, null, errorMessage),
                    null, errorMessage, stackTrace(e));
        }
    }

    /**
     * 组装处理器执行结果 JSON (写入执行记录 result 字段)。
     *
     * @param task      任务实体
     * @param execution 执行记录
     * @param success   是否成功
     * @param returnValue 处理器返回值 (可空)
     * @param error     失败原因 (可空)
     * @return 结果 JSON 字符串
     */
    private String handlerResultJson(ScrmScheduledTaskEntity task, ScrmTaskExecutionEntity execution,
                                     boolean success, String returnValue, String error) {
        return String.format(
                "{\"taskId\":%d,\"executionNo\":\"%s\",\"taskCode\":\"%s\",\"handlerClass\":\"%s\","
                        + "\"dispatch\":\"WHITELIST\",\"success\":%s,\"executedAt\":\"%s\","
                        + "\"returnValue\":%s,\"error\":%s}",
                task.getId(), escapeJson(execution.getExecutionNo()), escapeJson(task.getTaskCode()),
                escapeJson(task.getHandlerClass()), success,
                LocalDateTime.now().format(DATE_TIME_FORMATTER),
                returnValue == null ? "null" : "\"" + escapeJson(returnValue) + "\"",
                error == null ? "null" : "\"" + escapeJson(error) + "\"");
    }

    /**
     * 截断超长文本 (执行记录 return_value / error_message 字段有长度上限)。
     *
     * @param text      原文本
     * @param maxLength 最大长度
     * @return 截断后的文本, 入参为 null 时返回 null
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * 转义 JSON 字符串中的特殊字符。
     *
     * @param text 原文本
     * @return 可作为 JSON 字符串内容使用的文本, 入参为 null 时返回空串
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * 提取异常堆栈文本。
     *
     * @param e 异常
     * @return 堆栈字符串
     */
    private String stackTrace(Exception e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    /**
     * 处理器调用结果。
     *
     * @author Hsi Chu
     * @since V1.0
     * @param success      是否成功
     * @param result       JSON 执行结果
     * @param returnValue  返回值
     * @param errorMessage 错误信息 (失败时)
     * @param errorStack   错误堆栈 (失败时)
     */
    private record HandlerInvocationResult(boolean success, String result, String returnValue,
                                            String errorMessage, String errorStack) {
    }
}
