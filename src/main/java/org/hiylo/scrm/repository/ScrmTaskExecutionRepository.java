/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTaskExecutionRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTaskExecutionEntity;
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
 * SCRM 任务执行记录数据访问层。
 * <p>
 * 提供按执行编号查询、按任务 ID 查询最近一次执行、按状态查询运行中执行、
 * 按时间范围聚合统计等能力, 供 {@code ScrmTaskSchedulerService} 执行流转与统计使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmTaskExecutionRepository extends JpaRepository<ScrmTaskExecutionEntity, Long>,
        JpaSpecificationExecutor<ScrmTaskExecutionEntity> {

    /**
     * 按与执行编号查询执行记录。
     *
     * @param executionNo 执行编号
     * @return 执行记录（不存在时返回空）
     */
    Optional<ScrmTaskExecutionEntity> findByExecutionNo(String executionNo);

    /**
     * 按任务 ID 查询最近一次执行记录（按 startedAt 倒序取第一条）。
     *
     * @param taskId 任务 ID
     * @return 最近一次执行记录（不存在时返回空）
     */
    Optional<ScrmTaskExecutionEntity> findFirstByTaskIdOrderByStartedAtDesc(Long taskId);

    /**
     * 按状态查询执行记录, 按开始时间倒序返回。
     *
     * @param status   状态
     * @return 执行记录列表
     */
    List<ScrmTaskExecutionEntity> findByStatusOrderByStartedAtDesc(String status);

    /**
     * 按查询运行中（RUNNING）执行记录。
     *
     * @return 运行中执行记录列表
     */
    List<ScrmTaskExecutionEntity> findByStatus(String status);

    /**
     * 按任务 ID 与状态分页查询执行记录, 按开始时间倒序返回。
     *
     * @param taskId  任务 ID
     * @param status  状态
     * @param pageable 分页参数
     * @return 执行记录分页结果
     */
    Page<ScrmTaskExecutionEntity> findByTaskIdAndStatusOrderByStartedAtDesc(Long taskId, String status,
                                                                             Pageable pageable);

    /**
     * 按与时间范围查询全部执行记录（统计聚合用, 不分页）。
     *
     * @param start    起始时间 (含)
     * @param end      截止时间 (含)
     * @return 执行记录列表
     */
    List<ScrmTaskExecutionEntity> findByStartedAtBetweenOrderByStartedAtAsc(LocalDateTime start, LocalDateTime end);

    /**
     * 增量更新执行状态与起止时间（避免乐观锁冲突, 直接 SQL 更新）。
     *
     * @param executionId 执行 ID
     * @param status       新状态
     * @param startedAt    开始时间（可空, 仅 RUNNING 时设置）
     * @param completedAt  完成时间（可空, 仅终态时设置）
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmTaskExecutionEntity e SET e.status = :status, e.startedAt = CASE WHEN e.startedAt IS "
                          + "NULL THEN :startedAt ELSE e.startedAt END, e.completedAt = :completedAt WHERE e.id ="
                          + ":executionId")
    int updateStatus(@Param("executionId") Long executionId,
                     @Param("status") String status,
                     @Param("startedAt") LocalDateTime startedAt,
                     @Param("completedAt") LocalDateTime completedAt);

    /**
     * 增量更新执行进度与进度消息（避免乐观锁冲突, 直接 SQL 更新）。
     *
     * @param executionId 执行 ID
     * @param progress     进度 0-100
     * @param message      进度消息
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE ScrmTaskExecutionEntity e SET e.progress = :progress, e.progressMessage = :message "
            + "WHERE e.id = :executionId")
    int updateProgress(@Param("executionId") Long executionId,
                       @Param("progress") Integer progress,
                       @Param("message") String message);

    /**
     * 更新执行结果与完成信息（避免乐观锁冲突, 直接 SQL 更新）。
     *
     * @param executionId 执行 ID
     * @param status       终态状态
     * @param completedAt  完成时间
     * @param durationMs    执行耗时毫秒
     * @param result       JSON 执行结果
     * @param returnValue  返回值
     * @param errorMessage 错误信息
     * @param errorStack    错误堆栈
     * @return 受影响行数
     */
    @Modifying
    @Query(value = "UPDATE ScrmTaskExecutionEntity e SET e.status = :status, e.completedAt = :completedAt,"
                          + "e.durationMs = :durationMs, e.result = :result, e.returnValue = :returnValue, e.errorMessage ="
                          + ":errorMessage, e.errorStack = :errorStack WHERE e.id = :executionId")
    int updateResult(@Param("executionId") Long executionId,
                     @Param("status") String status,
                     @Param("completedAt") LocalDateTime completedAt,
                     @Param("durationMs") Integer durationMs,
                     @Param("result") String result,
                     @Param("returnValue") String returnValue,
                     @Param("errorMessage") String errorMessage,
                     @Param("errorStack") String errorStack);

    /**
     * 更新重试执行 ID（关联到新创建的重试执行记录）。
     *
     * @param executionId       原执行 ID
     * @param retryExecutionId 重试执行 ID
     * @return 受影响行数
     */
    @Modifying
    @Query(value = "UPDATE ScrmTaskExecutionEntity e SET e.retryExecutionId = :retryExecutionId, e.isRetried = true "
                          + "WHERE e.id = :executionId")
    int updateRetryExecutionId(@Param("executionId") Long executionId,
                               @Param("retryExecutionId") Long retryExecutionId);
}
