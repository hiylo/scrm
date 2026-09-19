/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleTransitionService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmLifecycleTransitionActionDto;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleEntity;
import org.hiylo.scrm.entity.ScrmLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmLifecycleStageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerLifecycleRepository;
import org.hiylo.scrm.repository.ScrmLifecycleHistoryRepository;
import org.hiylo.scrm.repository.ScrmLifecycleStageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * SCRM 客户生命周期转换记录管理服务。
 * <p>
 * 承载客户生命周期转换历史的管理能力: 转换记录查询 / 撤销流转 / 流转模式分析 / 瓶颈阶段 /
 * 转化漏斗。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerLifecycleTransitionService {

    /** 既有生命周期服务 (复用阶段定义 / 流转规则 / 转换历史能力) */
    private final ScrmLifecycleService scrmLifecycleService;

    /** 评分与统计分析服务 (复用派生指标计算) */
    private final ScrmCustomerLifecycleAnalyticsService analyticsService;

    /** 阶段数据访问层 */
    private final ScrmLifecycleStageRepository stageRepository;

    /** 客户生命周期数据访问层 */
    private final ScrmCustomerLifecycleRepository customerLifecycleRepository;

    /** 转换历史数据访问层 */
    private final ScrmLifecycleHistoryRepository historyRepository;

    /**
     * 查询转换记录详情。
     *
     * @param id 转换记录 ID
     * @return 转换历史实体
     * @throws ScrmException 转换记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmLifecycleHistoryEntity getTransition(Long id) throws ScrmException {
        ScrmLifecycleHistoryEntity entity = historyRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "转换记录不存在: id=" + id));
        return entity;
    }

    /**
     * 查询客户全部转换记录 (按时间降序)。
     *
     * @param customerId 客户 ID
     * @return 转换记录列表
     * @throws ScrmException 客户 ID 非法
     */
    @Transactional(readOnly = true)
    public List<ScrmLifecycleHistoryEntity> getTransitionsByCustomer(Long customerId) throws ScrmException {
        return scrmLifecycleService.getCustomerHistory(customerId);
    }

    /**
     * 按阶段分页查询转换记录 (源或目标阶段匹配)。
     *
     * @param stage    阶段编码
     * @param pageable 分页参数
     * @return 转换记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmLifecycleHistoryEntity> getTransitionsByStage(String stage, Pageable pageable) {
        Specification<ScrmLifecycleHistoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (stage != null && !stage.isBlank()) {
                predicates.add(cb.or(cb.equal(root.get("fromStageCode"), stage),
                        cb.equal(root.get("toStageCode"), stage)));
            }
            query.orderBy(cb.desc(root.get("transitionTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return historyRepository.findAll(spec, pageable);
    }

    /**
     * 按流转类型分页查询转换记录。
     *
     * @param type     流转类型
     * @param pageable 分页参数
     * @return 转换记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmLifecycleHistoryEntity> getTransitionsByType(String type, Pageable pageable) {
        Specification<ScrmLifecycleHistoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("transitionType"), type));
            }
            query.orderBy(cb.desc(root.get("transitionTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return historyRepository.findAll(spec, pageable);
    }

    /**
     * 按时间区间分页查询转换记录。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @param pageable  分页参数
     * @return 转换记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmLifecycleHistoryEntity> getTransitionsByDateRange(LocalDateTime startTime,
                                                                       LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmLifecycleHistoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transitionTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transitionTime"), endTime));
            }
            query.orderBy(cb.desc(root.get("transitionTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return historyRepository.findAll(spec, pageable);
    }

    /**
     * 撤销流转: 将客户回退到源阶段。
     *
     * @param id         转换记录 ID
     * @param reason     撤销原因
     * @param reversedBy 撤销人
     * @return 撤销结果 Map
     * @throws ScrmException 转换记录不存在 / 无法撤销
     */
    @Transactional
    public Map<String, Object> reverseTransition(Long id, String reason, String reversedBy) throws ScrmException {
        ScrmLifecycleHistoryEntity history = getTransition(id);
        if (history.getFromStageCode() == null || history.getFromStageCode().isBlank()) {
            throw ScrmException.badRequest("转换记录无源阶段, 无法撤销: id=" + id);
        }
        ScrmLifecycleTransitionActionDto action = new ScrmLifecycleTransitionActionDto();
        action.setCustomerId(history.getCustomerId());
        action.setCustomerName(history.getCustomerName());
        action.setToStageCode(history.getFromStageCode());
        action.setTriggerEvent("MANUAL");
        action.setDescription("撤销流转: " + reason);
        action.setOperatorId(reversedBy);
        ScrmCustomerLifecycleEntity updated = scrmLifecycleService.transitionCustomer(action);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reversedTransitionId", id);
        result.put("customerId", history.getCustomerId());
        result.put("fromStage", history.getToStageCode());
        result.put("toStage", history.getFromStageCode());
        result.put("reason", reason);
        result.put("reversedBy", reversedBy);
        result.put("currentLifecycle", updated);
        log.info("撤销流转: id={}, customerId={}, by={}", id, history.getCustomerId(), reversedBy);
        return result;
    }

    /**
     * 转换统计概览。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTransitionStats() {
        return scrmLifecycleService.getTransitionStats(null, null);
    }

    /**
     * 分析流转模式: 各阶段转化率 / 平均停留 / 流失率。
     *
     * @return 模式分析结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> analyzeTransitionPatterns() {
        List<ScrmLifecycleStageEntity> stages = stageRepository.findAllByOrderByStageOrderAsc();
        Set<Long> churnStageIds = analyticsService.churnStageIds();
        List<ScrmLifecycleHistoryEntity> allHistory = historyRepository.findAll(
                (root, query, cb) -> cb.and());
        List<Map<String, Object>> patterns = new ArrayList<>();
        for (ScrmLifecycleStageEntity stage : stages) {
            long entered = stage.getTotalEnteredCount() != null ? stage.getTotalEnteredCount() : 0;
            long left = allHistory.stream()
                    .filter(h -> Objects.equals(h.getFromStageId(), stage.getId())).count();
            long churned = allHistory.stream()
                    .filter(h -> Objects.equals(h.getFromStageId(), stage.getId()) && h.getToStageId() != null && churnStageIds.contains(h.getToStageId())).count();
            double avgDuration = allHistory.stream()
                    .filter(h -> Objects.equals(h.getFromStageId(), stage.getId()))
                    .mapToInt(h -> h.getDurationInPreviousStage() != null ? h.getDurationInPreviousStage() : 0)
                    .average().orElse(0.0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stageId", stage.getId());
            m.put("stageName", stage.getStageName());
            m.put("stageCode", stage.getStageCode());
            m.put("enteredCount", entered);
            m.put("leftCount", left);
            m.put("conversionRate", entered > 0 ? analyticsService.round2(left * 1.0 / entered) : 0.0);
            m.put("avgDurationDays", analyticsService.round2(avgDuration));
            m.put("churnedCount", churned);
            m.put("churnRate", left > 0 ? analyticsService.round2(churned * 1.0 / left) : 0.0);
            patterns.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stages", patterns);
        result.put("totalTransitions", allHistory.size());
        return result;
    }

    /**
     * 识别瓶颈阶段 (转化率低或超期客户多)。
     *
     * @return 瓶颈阶段列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBottleneckStages() {
        List<ScrmLifecycleStageEntity> stages = stageRepository.findAllByOrderByStageOrderAsc();
        List<Map<String, Object>> bottlenecks = new ArrayList<>();
        for (ScrmLifecycleStageEntity stage : stages) {
            long overdueCount = customerLifecycleRepository
                    .countByCurrentStageIdAndIsOverdueTrue(stage.getId());
            double conversionRate = stage.getConversionRate() != null ? stage.getConversionRate() : 0.0;
            int customerCount = stage.getCustomerCount() != null ? stage.getCustomerCount() : 0;
            if ((conversionRate < 0.2 && customerCount > 0) || overdueCount > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("stageId", stage.getId());
                m.put("stageName", stage.getStageName());
                m.put("stageCode", stage.getStageCode());
                m.put("customerCount", customerCount);
                m.put("conversionRate", conversionRate);
                m.put("overdueCount", overdueCount);
                m.put("reasons", overdueCount > 0 ? Arrays.asList("超期客户堆积", "转化率偏低")
                        : Arrays.asList("转化率偏低"));
                bottlenecks.add(m);
            }
        }
        return bottlenecks;
    }

    /**
     * 转化漏斗 (各阶段 → 下一阶段转化率)。
     *
     * @return 漏斗数据列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConversionFunnel() {
        return scrmLifecycleService.getConversionFunnel();
    }
}
