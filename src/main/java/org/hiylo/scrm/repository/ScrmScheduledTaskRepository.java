/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmScheduledTaskRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 任务调度配置数据访问层。
 * <p>
 * 提供按加载启用任务、按编码查询、按下次执行时间扫描待调度任务以及统计字段增量更新等能力,
 * 供 {@code ScrmTaskSchedulerService} 调度与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmScheduledTaskRepository extends JpaRepository<ScrmScheduledTaskEntity, Long>,
        JpaSpecificationExecutor<ScrmScheduledTaskEntity> {

    /**
     * 按与任务编码查询任务。
     *
     * @param taskCode 任务编码
     * @return 任务实体（不存在时返回空）
     */
    Optional<ScrmScheduledTaskEntity> findByTaskCode(String taskCode);

    /**
     * 按查询全部启用任务, 按优先级降序返回。
     *
     * @return 启用任务列表
     */
    List<ScrmScheduledTaskEntity> findByIsEnabledTrueOrderByPriorityDesc();

    /**
     * 按状态查询任务。
     *
     * @param status   状态
     * @return 任务列表
     */
    List<ScrmScheduledTaskEntity> findByStatus(String status);

    /**
     * 按状态分页查询任务, 按创建时间倒序返回。
     *
     * @param status   状态
     * @param pageable 分页参数
     * @return 任务分页结果
     */
    Page<ScrmScheduledTaskEntity> findByStatus(String status, Pageable pageable);

    /**
     * 扫描下次执行时间早于指定时刻的启用任务（调度器轮询调用）。
     *
     * @param time 截止时刻 (含)
     * @return 待调度任务列表
     */
    @Query("SELECT t FROM ScrmScheduledTaskEntity t WHERE t.isEnabled = true "
            + "AND t.status = 'ACTIVE' AND t.nextScheduledAt IS NOT NULL "
            + "AND t.nextScheduledAt <= :time ORDER BY t.nextScheduledAt ASC")
    List<ScrmScheduledTaskEntity> findDueTasks(@Param("time") LocalDateTime time);

    /**
     * 增量更新任务统计字段（避免乐观锁冲突, 直接 SQL 更新）。
     *
     * @param taskId             任务 ID
     * @param executedAt         执行时间
     * @param executionStatus    执行状态 (SUCCESS/FAILED/TIMEOUT/RUNNING)
     * @param durationMs         本次执行耗时（毫秒, 可空）
     * @param errorMessage       错误信息（可空）
     * @param successIncrement   成功次数增量
     * @param failureIncrement   失败次数增量
     * @param timeoutIncrement   超时次数增量
     * @param resetConsecutive   是否重置连续失败计数（成功时为 true）
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmScheduledTaskEntity t SET t.totalExecutions = t.totalExecutions + 1, t.successCount ="
                          + "t.successCount + :successIncrement, t.failureCount = t.failureCount + :failureIncrement,"
                          + "t.timeoutCount = t.timeoutCount + :timeoutIncrement, t.lastExecutedAt = :executedAt,"
                          + "t.lastExecutionStatus = :executionStatus, t.lastExecutionDurationMs = :durationMs,"
                          + "t.lastErrorMessage = :errorMessage, t.consecutiveFailures = CASE WHEN :resetConsecutive = true "
                          + "THEN 0 ELSE t.consecutiveFailures + 1 END WHERE t.id = :taskId")
    int updateTaskStats(@Param("taskId") Long taskId,
                         @Param("executedAt") LocalDateTime executedAt,
                         @Param("executionStatus") String executionStatus,
                         @Param("durationMs") Integer durationMs,
                         @Param("errorMessage") String errorMessage,
                         @Param("successIncrement") int successIncrement,
                         @Param("failureIncrement") int failureIncrement,
                         @Param("timeoutIncrement") int timeoutIncrement,
                         @Param("resetConsecutive") boolean resetConsecutive);

    /**
     * 更新任务下次计划执行时间。
     *
     * @param taskId         任务 ID
     * @param nextScheduledAt 下次执行时间
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE ScrmScheduledTaskEntity t SET t.nextScheduledAt = :nextScheduledAt "
            + "WHERE t.id = :taskId")
    int updateNextScheduledAt(@Param("taskId") Long taskId,
                              @Param("nextScheduledAt") LocalDateTime nextScheduledAt);

    /**
     * 更新任务状态。
     *
     * @param taskId 任务 ID
     * @param status 状态
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE ScrmScheduledTaskEntity t SET t.status = :status "
            + "WHERE t.id = :taskId")
    int updateStatus(@Param("taskId") Long taskId, @Param("status") String status);
}
