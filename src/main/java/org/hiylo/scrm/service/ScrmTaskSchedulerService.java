/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskSchedulerService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmTaskDependencyDto;
import org.hiylo.scrm.dto.ScrmTaskExecuteDto;
import org.hiylo.scrm.dto.ScrmTaskRetryDto;
import org.hiylo.scrm.dto.ScrmScheduledTaskDto;
import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.hiylo.scrm.entity.ScrmTaskDependencyEntity;
import org.hiylo.scrm.entity.ScrmTaskExecutionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 任务调度引擎服务 (门面)。
 * <p>
 * 保留对外全部 public 方法签名, 按子域委托给兄弟服务: 任务管理
 * ({@link ScrmTaskSchedulerTaskService})、执行与重试 ({@link ScrmTaskSchedulerExecutionService})、
 * 依赖关系 ({@link ScrmTaskSchedulerDependencyService})、统计 ({@link ScrmTaskSchedulerStatsService})。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
public class ScrmTaskSchedulerService {

    /** 任务管理子域服务 */
    private final ScrmTaskSchedulerTaskService taskService;

    /** 任务执行与重试子域服务 */
    private final ScrmTaskSchedulerExecutionService executionService;

    /** 任务依赖关系子域服务 */
    private final ScrmTaskSchedulerDependencyService dependencyService;

    /** 任务统计子域服务 */
    private final ScrmTaskSchedulerStatsService statsService;

    /**
     * 创建任务调度配置。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 任务编码重复
     */
    public ScrmScheduledTaskEntity createTask(ScrmScheduledTaskDto dto) throws ScrmException {
        return taskService.createTask(dto);
    }

    /**
     * 更新任务调度配置（字段非空才覆盖）。
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 参数非法
     */
    public ScrmScheduledTaskEntity updateTask(Long id, ScrmScheduledTaskDto dto) throws ScrmException {
        return taskService.updateTask(id, dto);
    }

    /**
     * 删除任务调度配置。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在 / 仍有执行记录或依赖引用
     */
    public void deleteTask(Long id) throws ScrmException {
        taskService.deleteTask(id);
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    public ScrmScheduledTaskEntity getTask(Long id) throws ScrmException {
        return taskService.getTask(id);
    }

    /**
     * 按任务编码查询任务详情。
     *
     * @param code 任务编码
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    public ScrmScheduledTaskEntity getTaskByCode(String code) throws ScrmException {
        return taskService.getTaskByCode(code);
    }

    /**
     * 分页查询任务。
     *
     * @param taskCategory 任务类别过滤（可空）
     * @param taskType     任务类型过滤（可空）
     * @param status       状态过滤（可空）
     * @param keyword      关键字过滤（可空）
     * @param pageable     分页参数
     * @return 任务分页结果
     */
    public Page<ScrmScheduledTaskEntity> listTasks(String taskCategory, String taskType, String status,
                                                     String keyword, Pageable pageable) {
        return taskService.listTasks(taskCategory, taskType, status, keyword, pageable);
    }

    /**
     * 启用任务。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    public void enableTask(Long id) throws ScrmException {
        taskService.enableTask(id);
    }

    /**
     * 禁用任务。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    public void disableTask(Long id) throws ScrmException {
        taskService.disableTask(id);
    }

    /**
     * 暂停任务。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    public void pauseTask(Long id) throws ScrmException {
        taskService.pauseTask(id);
    }

    /**
     * 恢复任务。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    public void resumeTask(Long id) throws ScrmException {
        taskService.resumeTask(id);
    }

    /**
     * 复制任务。
     *
     * @param id      源任务 ID
     * @param newCode 新任务编码
     * @return 复制后的任务
     * @throws ScrmException 源任务不存在 / 新编码重复
     */
    public ScrmScheduledTaskEntity copyTask(Long id, String newCode) throws ScrmException {
        return taskService.copyTask(id, newCode);
    }

    /**
     * 校验 Cron 表达式合法性。
     *
     * @param cronExpression Cron 表达式
     * @return true 表示合法
     * @throws ScrmException 表达式非法
     */
    public boolean validateCron(String cronExpression) throws ScrmException {
        return taskService.validateCron(cronExpression);
    }

    /**
     * 计算指定 Cron 表达式的下次执行时间。
     *
     * @param cronExpression Cron 表达式
     * @return 下次执行时间
     * @throws ScrmException 表达式非法
     */
    public LocalDateTime calculateNextExecution(String cronExpression) throws ScrmException {
        return taskService.calculateNextExecution(cronExpression);
    }

    /**
     * 更新任务统计字段。
     *
     * @param id              任务 ID
     * @param executionStatus 执行状态
     * @param durationMs      执行耗时（毫秒, 可空）
     * @throws ScrmException 任务不存在 / 状态非法
     */
    public void updateTaskStats(Long id, String executionStatus, Integer durationMs) throws ScrmException {
        taskService.updateTaskStats(id, executionStatus, durationMs);
    }

    /**
     * 执行任务。
     *
     * @param executeDto 执行参数
     * @return 执行记录实体 (终态)
     * @throws ScrmException 任务不存在 / 状态不允许执行
     */
    public ScrmTaskExecutionEntity executeTask(ScrmTaskExecuteDto executeDto) throws ScrmException {
        return executionService.executeTask(executeDto);
    }

    /**
     * 查询执行记录详情。
     *
     * @param id 执行 ID
     * @return 执行记录实体
     * @throws ScrmException 执行记录不存在
     */
    public ScrmTaskExecutionEntity getExecution(Long id) throws ScrmException {
        return executionService.getExecution(id);
    }

    /**
     * 按执行编号查询执行记录详情。
     *
     * @param executionNo 执行编号
     * @return 执行记录实体
     * @throws ScrmException 执行记录不存在
     */
    public ScrmTaskExecutionEntity getExecutionByNo(String executionNo) throws ScrmException {
        return executionService.getExecutionByNo(executionNo);
    }

    /**
     * 分页查询执行记录。
     *
     * @param taskId     任务 ID 过滤（可空）
     * @param status     执行状态过滤（可空）
     * @param triggerType 触发类型过滤（可空）
     * @param startTime  起始时间 (含, 可空)
     * @param endTime    截止时间 (含, 可空)
     * @param pageable   分页参数
     * @return 执行记录分页结果
     */
    public Page<ScrmTaskExecutionEntity> listExecutions(Long taskId, String status, String triggerType,
                                                          LocalDateTime startTime, LocalDateTime endTime,
                                                          Pageable pageable) {
        return executionService.listExecutions(taskId, status, triggerType, startTime, endTime, pageable);
    }

    /**
     * 取消执行。
     *
     * @param id     执行 ID
     * @param reason 取消原因
     * @return 更新后的执行记录
     * @throws ScrmException 执行记录不存在 / 状态不允许取消
     */
    public ScrmTaskExecutionEntity cancelExecution(Long id, String reason) throws ScrmException {
        return executionService.cancelExecution(id, reason);
    }

    /**
     * 查询运行中（RUNNING）的执行记录。
     *
     * @return 运行中执行记录列表
     */
    public List<ScrmTaskExecutionEntity> getRunningExecutions() {
        return executionService.getRunningExecutions();
    }

    /**
     * 查询任务最近一次执行记录。
     *
     * @param taskId 任务 ID
     * @return 最近一次执行记录（不存在时返回 null）
     */
    public ScrmTaskExecutionEntity getLatestExecution(Long taskId) {
        return executionService.getLatestExecution(taskId);
    }

    /**
     * 带重试执行任务。
     *
     * @param taskId     任务 ID
     * @param parameters 本次参数
     * @param retryCount 当前重试次数
     * @return 执行记录实体
     * @throws ScrmException 任务不存在 / 超过最大重试次数
     */
    public ScrmTaskExecutionEntity executeTaskWithRetry(Long taskId, String parameters, int retryCount)
            throws ScrmException {
        return executionService.executeTaskWithRetry(taskId, parameters, retryCount);
    }

    /**
     * 处理执行超时。
     *
     * @param executionId 执行 ID
     * @return 更新后的执行记录
     * @throws ScrmException 执行记录不存在 / 状态非 RUNNING
     */
    public ScrmTaskExecutionEntity handleTimeout(Long executionId) throws ScrmException {
        return executionService.handleTimeout(executionId);
    }

    /**
     * 更新执行进度。
     *
     * @param executionId 执行 ID
     * @param progress    进度 0-100
     * @param message     进度消息
     * @throws ScrmException 执行记录不存在 / 进度非法
     */
    public void updateProgress(Long executionId, Integer progress, String message) throws ScrmException {
        executionService.updateProgress(executionId, progress, message);
    }

    /**
     * 重试执行。
     *
     * @param retryDto 重试参数
     * @return 新创建的执行记录
     * @throws ScrmException 原执行记录不存在 / 状态不允许重试
     */
    public ScrmTaskExecutionEntity retryExecution(ScrmTaskRetryDto retryDto) throws ScrmException {
        return executionService.retryExecution(retryDto);
    }

    /**
     * 计算重试延迟。
     *
     * @param retryCount       当前重试次数
     * @param baseDelay        基础延迟秒
     * @param backoffMultiplier 退避倍数
     * @return 重试延迟秒数
     */
    public int calculateRetryDelay(int retryCount, int baseDelay, double backoffMultiplier) {
        return executionService.calculateRetryDelay(retryCount, baseDelay, backoffMultiplier);
    }

    /**
     * 获取最大重试延迟上限。
     *
     * @return 最大重试延迟秒数
     */
    public int getMaxRetryDelay() {
        return executionService.getMaxRetryDelay();
    }

    /**
     * 创建任务依赖关系。
     *
     * @param dto 依赖参数
     * @return 创建后的依赖关系
     * @throws ScrmException 参数非法 / 重复依赖
     */
    public ScrmTaskDependencyEntity createDependency(ScrmTaskDependencyDto dto) throws ScrmException {
        return dependencyService.createDependency(dto);
    }

    /**
     * 更新任务依赖关系。
     *
     * @param id  依赖 ID
     * @param dto 依赖参数
     * @return 更新后的依赖关系
     * @throws ScrmException 依赖关系不存在 / 参数非法
     */
    public ScrmTaskDependencyEntity updateDependency(Long id, ScrmTaskDependencyDto dto) throws ScrmException {
        return dependencyService.updateDependency(id, dto);
    }

    /**
     * 删除任务依赖关系。
     *
     * @param id 依赖 ID
     * @throws ScrmException 依赖关系不存在
     */
    public void deleteDependency(Long id) throws ScrmException {
        dependencyService.deleteDependency(id);
    }

    /**
     * 查询任务依赖关系详情。
     *
     * @param id 依赖 ID
     * @return 依赖关系实体
     * @throws ScrmException 依赖关系不存在
     */
    public ScrmTaskDependencyEntity getDependency(Long id) throws ScrmException {
        return dependencyService.getDependency(id);
    }

    /**
     * 分页查询任务依赖关系。
     *
     * @param taskId         主任务 ID 过滤（可空）
     * @param dependsOnTaskId 依赖任务 ID 过滤（可空）
     * @param pageable        分页参数
     * @return 依赖关系分页结果
     */
    public Page<ScrmTaskDependencyEntity> listDependencies(Long taskId, Long dependsOnTaskId, Pageable pageable) {
        return dependencyService.listDependencies(taskId, dependsOnTaskId, pageable);
    }

    /**
     * 检查任务依赖是否满足。
     *
     * @param taskId 任务 ID
     * @return true 表示所有必须依赖均已满足
     * @throws ScrmException 任务不存在
     */
    public boolean checkDependencies(Long taskId) throws ScrmException {
        return dependencyService.checkDependencies(taskId);
    }

    /**
     * 执行依赖任务。
     *
     * @param parentExecutionId 父执行 ID
     * @return 触发的依赖执行记录列表
     * @throws ScrmException 父执行不存在
     */
    public List<ScrmTaskExecutionEntity> executeDependentTasks(Long parentExecutionId) throws ScrmException {
        return dependencyService.executeDependentTasks(parentExecutionId);
    }

    /**
     * 获取任务的依赖链。
     *
     * @param taskId 任务 ID
     * @return 依赖链
     * @throws ScrmException 任务不存在
     */
    public List<ScrmTaskDependencyEntity> getDependencyChain(Long taskId) throws ScrmException {
        return dependencyService.getDependencyChain(taskId);
    }

    /**
     * 任务统计概览。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 任务统计概览
     */
    public Map<String, Object> getTaskStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getTaskStats(startTime, endTime);
    }

    /**
     * 执行统计。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 执行统计
     */
    public Map<String, Object> getExecutionStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getExecutionStats(startTime, endTime);
    }

    /**
     * 失败分析。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 失败分析结果
     */
    public Map<String, Object> getFailureAnalysis(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getFailureAnalysis(startTime, endTime);
    }

    /**
     * 性能统计。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 性能统计
     */
    public Map<String, Object> getPerformanceStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getPerformanceStats(startTime, endTime);
    }

    /**
     * 任务健康度。
     *
     * @return 任务健康度列表
     */
    public List<Map<String, Object>> getTaskHealth() {
        return statsService.getTaskHealth();
    }

    /**
     * 执行趋势。
     *
     * @param days 统计天数 (默认 7)
     * @return 趋势数据列表
     */
    public List<Map<String, Object>> getExecutionTrend(Integer days) {
        return statsService.getExecutionTrend(days);
    }
}