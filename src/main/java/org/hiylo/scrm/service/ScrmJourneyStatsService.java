/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmJourneyProgressLogDto;
import org.hiylo.scrm.entity.ScrmCustomerJourneyEntity;
import org.hiylo.scrm.entity.ScrmJourneyProgressLogEntity;
import org.hiylo.scrm.entity.ScrmJourneyStepEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmJourneyEnrollmentRepository;
import org.hiylo.scrm.repository.ScrmJourneyProgressLogRepository;
import org.hiylo.scrm.repository.ScrmJourneyStepRepository;
import org.hiylo.scrm.vo.JourneyStatsVo;
import org.hiylo.scrm.vo.JourneyStepStatsVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 客户旅程统计与进度子域服务
 * <p>
 * 负责旅程整体统计 / 各步骤统计 / 入营进度日志查询。旅程与入营记录的存在性校验
 * 复用 {@link ScrmJourneyStepExecutionService} 的共享查询。本服务为
 * {@link ScrmCustomerJourneyService} 门面的子域拆分, 不反向依赖门面。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmJourneyStatsService {

    /** 入营状态: 进行中 */
    private static final String ENROLLMENT_STATUS_ACTIVE = "ACTIVE";

    /** 执行结果: 成功 */
    private static final String RESULT_SUCCESS = "SUCCESS";
    /** 执行结果: 失败 */
    private static final String RESULT_FAILED = "FAILED";
    /** 执行结果: 跳过 */
    private static final String RESULT_SKIPPED = "SKIPPED";
    /** 执行结果: 等待中 */
    private static final String RESULT_WAITING = "WAITING";

    /** 旅程加入数据仓库 */
    private final ScrmJourneyEnrollmentRepository enrollmentRepository;
    /** 旅程步骤数据仓库 */
    private final ScrmJourneyStepRepository stepRepository;
    /** 旅程进度日志数据仓库 */
    private final ScrmJourneyProgressLogRepository progressLogRepository;
    /** 步骤执行兄弟服务 (提供共享查询) */
    private final ScrmJourneyStepExecutionService stepExecutionService;

    /**
     * 旅程统计: 入旅程数 / 完成数 / 退出数 / 转化率 / 当前活跃数 / 各步骤通过率
     *
     * @param journeyId 旅程 ID
     * @return 旅程统计结果
     * @throws ScrmException 旅程不存在
     */
    @Transactional(readOnly = true)
    public JourneyStatsVo getJourneyStats(Long journeyId) throws ScrmException {
        ScrmCustomerJourneyEntity journey = stepExecutionService.findJourneyOrThrow(journeyId);
        // 活跃数: 当前 ACTIVE 入营记录数
        long activeCount = enrollmentRepository.count((root, query, cb) -> cb.and(
                cb.equal(root.get("journeyId"), journeyId),
                cb.equal(root.get("status"), ENROLLMENT_STATUS_ACTIVE)));
        // 各步骤通过率 (基于进度日志聚合)
        List<JourneyStatsVo.StepPassRateVo> stepPassRates = buildStepPassRates(journeyId);
        return JourneyStatsVo.builder()
                .journeyId(journeyId)
                .journeyName(journey.getJourneyName())
                .status(journey.getStatus())
                .enrolledCount(journey.getEnrolledCount())
                .completedCount(journey.getCompletedCount())
                .exitedCount(journey.getExitedCount())
                .activeCount((int) activeCount)
                .conversionRate(journey.getConversionRate())
                .steps(stepPassRates)
                .build();
    }

    /**
     * 各步骤统计: 进入数 / 成功 / 失败 / 跳过 / 等待 / 通过率
     *
     * @param journeyId 旅程 ID
     * @return 步骤统计列表 (按 stepOrder 升序)
     * @throws ScrmException 旅程不存在
     */
    @Transactional(readOnly = true)
    public List<JourneyStepStatsVo> getStepStats(Long journeyId) throws ScrmException {
        stepExecutionService.findJourneyOrThrow(journeyId);
        // 步骤列表 (按 stepOrder 升序)
        List<ScrmJourneyStepEntity> steps = stepRepository
                .findByJourneyIdOrderByStepOrderAsc(journeyId);
        // 聚合进度日志: stepId + actionResult → count
        List<Object[]> aggRows = progressLogRepository.aggregateByStepAndResult(journeyId);
        Map<Long, Map<String, Long>> stepAgg = new HashMap<>();
        for (Object[] row : aggRows) {
            Long stepId = ((Number) row[0]).longValue();
            String result = (String) row[1];
            long count = ((Number) row[2]).longValue();
            stepAgg.computeIfAbsent(stepId, k -> new HashMap<>()).put(result, count);
        }
        List<JourneyStepStatsVo> result = new ArrayList<>();
        for (ScrmJourneyStepEntity step : steps) {
            Map<String, Long> agg = stepAgg.getOrDefault(step.getId(), new HashMap<>());
            long entered = agg.values().stream().mapToLong(Long::longValue).sum();
            long passed = agg.getOrDefault(RESULT_SUCCESS, 0L);
            long failed = agg.getOrDefault(RESULT_FAILED, 0L);
            long skipped = agg.getOrDefault(RESULT_SKIPPED, 0L);
            long waiting = agg.getOrDefault(RESULT_WAITING, 0L);
            double passRate = entered > 0 ? Math.round(passed * 100d / entered * 100d) / 100d : 0d;
            result.add(JourneyStepStatsVo.builder()
                    .stepId(step.getId())
                    .stepName(step.getStepName())
                    .stepType(step.getStepType())
                    .stepOrder(step.getStepOrder())
                    .enteredCount((int) entered)
                    .passedCount((int) passed)
                    .failedCount((int) failed)
                    .skippedCount((int) skipped)
                    .waitingCount((int) waiting)
                    .passRate(passRate)
                    .build());
        }
        return result;
    }

    /**
     * 查询入营记录的进度日志 (按执行时间升序)
     *
     * @param enrollmentId 入营记录 ID
     * @return 进度日志列表
     * @throws ScrmException 入营记录不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmJourneyProgressLogDto> getProgressLog(Long enrollmentId) throws ScrmException {
        stepExecutionService.findEnrollmentOrThrow(enrollmentId);
        return progressLogRepository
                .findByEnrollmentIdOrderByExecutedAtAsc(enrollmentId)
                .stream()
                .map(this::toProgressLogDto)
                .collect(Collectors.toList());
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 构建各步骤通过率概要 (旅程统计用)
     *
     * @param journeyId 旅程 ID
     * @return 步骤通过率列表 (按 stepOrder 升序)
     */
    private List<JourneyStatsVo.StepPassRateVo> buildStepPassRates(Long journeyId) {
        List<ScrmJourneyStepEntity> steps = stepRepository
                .findByJourneyIdOrderByStepOrderAsc(journeyId);
        List<Object[]> aggRows = progressLogRepository.aggregateByStepAndResult(journeyId);
        Map<Long, long[]> stepAgg = new HashMap<>();
        for (Object[] row : aggRows) {
            Long stepId = ((Number) row[0]).longValue();
            String result = (String) row[1];
            long count = ((Number) row[2]).longValue();
            long[] agg = stepAgg.computeIfAbsent(stepId, k -> new long[]{0L, 0L});
            agg[0] += count;
            if (RESULT_SUCCESS.equals(result)) {
                agg[1] += count;
            }
        }
        List<JourneyStatsVo.StepPassRateVo> result = new ArrayList<>();
        for (ScrmJourneyStepEntity step : steps) {
            long[] agg = stepAgg.getOrDefault(step.getId(), new long[]{0L, 0L});
            long entered = agg[0];
            long passed = agg[1];
            double passRate = entered > 0 ? Math.round(passed * 100d / entered * 100d) / 100d : 0d;
            result.add(JourneyStatsVo.StepPassRateVo.builder()
                    .stepId(step.getId())
                    .stepName(step.getStepName())
                    .stepType(step.getStepType())
                    .stepOrder(step.getStepOrder())
                    .enteredCount((int) entered)
                    .passedCount((int) passed)
                    .passRate(passRate)
                    .build());
        }
        return result;
    }

    /**
     * 进度日志实体转 DTO
     */
    private ScrmJourneyProgressLogDto toProgressLogDto(ScrmJourneyProgressLogEntity entity) {
        ScrmJourneyProgressLogDto dto = new ScrmJourneyProgressLogDto();
        dto.setId(entity.getId());
        dto.setEnrollmentId(entity.getEnrollmentId());
        dto.setJourneyId(entity.getJourneyId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setStepId(entity.getStepId());
        dto.setStepName(entity.getStepName());
        dto.setStepType(entity.getStepType());
        dto.setActionResult(entity.getActionResult());
        dto.setActionDetail(entity.getActionDetail());
        dto.setExecutedAt(entity.getExecutedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
