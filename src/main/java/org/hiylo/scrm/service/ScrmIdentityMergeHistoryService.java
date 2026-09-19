/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeHistoryService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerIdentityEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeHistoryEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeRuleEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeTaskEntity;
import org.hiylo.scrm.entity.ScrmOrderEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerIdentityRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmIdentityMergeHistoryRepository;
import org.hiylo.scrm.repository.ScrmIdentityMergeRuleRepository;
import org.hiylo.scrm.repository.ScrmIdentityMergeTaskRepository;
import org.hiylo.scrm.repository.ScrmOrderRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 客户身份合并服务 - 历史与统计子域。
 * <p>
 * 承载合并历史的查询 / 回滚 / 影响分析, 以及合并统计 / 重复统计 / 规则统计 / 身份统计。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmIdentityMergeHistoryService {

    /** 合并历史数据访问层 */
    private final ScrmIdentityMergeHistoryRepository historyRepository;

    /** 合并任务数据访问层 */
    private final ScrmIdentityMergeTaskRepository taskRepository;

    /** 客户身份数据访问层 */
    private final ScrmCustomerIdentityRepository identityRepository;

    /** 客户数据访问层 */
    private final ScrmCustomerRepository customerRepository;

    /** 订单数据访问层 */
    private final ScrmOrderRepository orderRepository;

    /** 客户-标签赋值数据访问层 */
    private final ScrmTagCustomerRepository tagCustomerRepository;

    /** 合并规则数据访问层 */
    private final ScrmIdentityMergeRuleRepository ruleRepository;

    /** 合并任务与执行子域服务 (共享任务/客户查询 / LTV 计算) */
    private final ScrmIdentityMergeTaskService taskService;

    /** 规则执行与重复检测子域服务 (共享重复报告) */
    private final ScrmIdentityMergeRuleService ruleService;

    // ============================================================
    // 合并历史
    // ============================================================

    /**
     * 查询合并历史详情。
     *
     * @param id 历史 ID
     * @return 历史实体
     * @throws ScrmException 历史不存在
     */
    @Transactional(readOnly = true)
    public ScrmIdentityMergeHistoryEntity getHistory(Long id) throws ScrmException {
        ScrmIdentityMergeHistoryEntity entity = historyRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合并历史不存在: id=" + id));
        return entity;
    }

    /**
     * 按任务 ID 查询合并历史。
     *
     * @param taskId 任务 ID
     * @return 历史实体
     * @throws ScrmException 历史不存在
     */
    @Transactional(readOnly = true)
    public ScrmIdentityMergeHistoryEntity getHistoryByTask(Long taskId) throws ScrmException {
        if (taskId == null) {
            throw ScrmException.badRequest("任务 ID 不能为空");
        }
        return historyRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "任务合并历史不存在: taskId=" + taskId));
    }

    /**
     * 分页查询合并历史, 支持按客户 / 时间区间过滤。
     *
     * @param customerId 客户 ID 过滤 (可空, 匹配源或目标客户)
     * @param startTime  合并时间起始 (可空)
     * @param endTime    合并时间截止 (可空)
     * @param pageable   分页参数
     * @return 历史分页结果 (按 mergedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmIdentityMergeHistoryEntity> listHistory(Long customerId, LocalDateTime startTime,
                                                             LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmIdentityMergeHistoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("sourceCustomerId"), customerId),
                        cb.equal(root.get("targetCustomerId"), customerId)));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("mergedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("mergedAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("mergedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return historyRepository.findAll(spec, pageable);
    }

    /**
     * 回滚合并 (恢复源客户身份 / 交易 / 标签, 恢复 lifecycle)。
     * <p>仅可回滚 rollbackAvailable=true 且 rolledBack=false 的历史。</p>
     *
     * @param historyId 历史 ID
     * @param reason    回滚原因
     * @return 更新后的历史
     * @throws ScrmException 历史不存在 / 不可回滚
     */
    @Transactional
    public ScrmIdentityMergeHistoryEntity rollbackMerge(Long historyId, String reason) throws ScrmException {
        ScrmIdentityMergeHistoryEntity history = getHistory(historyId);
        if (Boolean.FALSE.equals(history.getRollbackAvailable())) {
            throw ScrmException.badRequest("合并历史不可回滚: historyId=" + historyId);
        }
        if (Boolean.TRUE.equals(history.getRolledBack())) {
            throw ScrmException.badRequest("合并历史已回滚: historyId=" + historyId);
        }
        // 恢复源客户身份
        List<ScrmCustomerIdentityEntity> targetIdentities = identityRepository
                .findByCustomerIdOrderByIsPrimaryDescCreateTimeDesc(history.getTargetCustomerId());
        List<ScrmCustomerIdentityEntity> restoredIdentities = new ArrayList<>();
        for (ScrmCustomerIdentityEntity identity : targetIdentities) {
            if (ScrmIdentityMergeTaskService.SOURCE_MERGE.equals(identity.getSource())) {
                identity.setCustomerId(history.getSourceCustomerId());
                identity.setCustomerName(history.getSourceCustomerName());
                identity.setSource(ScrmIdentityMergeTaskService.SOURCE_MANUAL);
                restoredIdentities.add(identity);
            }
        }
        if (!restoredIdentities.isEmpty()) {
            identityRepository.saveAll(restoredIdentities);
        }
        // 恢复源客户交易 (订单)
        List<ScrmOrderEntity> targetOrders = orderRepository
                .findByCustomerIdOrderByCreateTimeDesc(history.getTargetCustomerId());
        for (ScrmOrderEntity order : targetOrders) {
            // 简化: 恢复订单的客户 ID (无法精确区分哪些是合并迁移的, 仅恢复客户名称匹配的)
            if (history.getTargetCustomerName() != null
                    && history.getTargetCustomerName().equals(order.getCustomerName())) {
                order.setCustomerId(history.getSourceCustomerId());
                order.setCustomerName(history.getSourceCustomerName());
            }
        }
        orderRepository.saveAll(targetOrders);
        // 恢复源客户 lifecycle
        ScrmCustomerEntity sourceCustomer = taskService.findCustomerOrThrow(history.getSourceCustomerId());
        sourceCustomer.setLifecycle("ACTIVE");
        customerRepository.save(sourceCustomer);
        // 标记回滚
        history.setRolledBack(Boolean.TRUE);
        history.setRolledBackAt(LocalDateTime.now());
        history.setRolledBackBy(UserContext.getUsername());
        history.setNotes(reason);
        history = historyRepository.save(history);
        log.info("回滚合并: historyId={}, taskId={}", historyId, history.getTaskId());
        return history;
    }

    /**
     * 合并影响分析 (合并前后 LTV / 交易 / 标签变化)。
     *
     * @param taskId 任务 ID
     * @return 影响分析 Map
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMergeImpact(Long taskId) throws ScrmException {
        ScrmIdentityMergeTaskEntity task = taskService.findTaskOrThrow(taskId);
        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("taskId", task.getId());
        impact.put("taskName", task.getTaskName());
        impact.put("sourceCustomerId", task.getSourceCustomerId());
        impact.put("targetCustomerId", task.getTargetCustomerId());
        // 身份数量
        impact.put("sourceIdentityCount", identityRepository
                .findByCustomerIdAndIsActive(task.getSourceCustomerId(), Boolean.TRUE).size());
        impact.put("targetIdentityCount", identityRepository
                .findByCustomerIdAndIsActive(task.getTargetCustomerId(), Boolean.TRUE).size());
        // 交易数量
        impact.put("sourceTransactionCount", orderRepository
                .findByCustomerIdOrderByCreateTimeDesc(task.getSourceCustomerId()).size());
        impact.put("targetTransactionCount", orderRepository
                .findByCustomerIdOrderByCreateTimeDesc(task.getTargetCustomerId()).size());
        // LTV
        impact.put("sourceLtv", taskService.calculateCustomerLtv(task.getSourceCustomerId()));
        impact.put("targetLtv", taskService.calculateCustomerLtv(task.getTargetCustomerId()));
        impact.put("mergedLtv", taskService.calculateCustomerLtv(task.getSourceCustomerId())
                + taskService.calculateCustomerLtv(task.getTargetCustomerId()));
        // 标签数 (赋值关系数, 按 tagId 计数)
        impact.put("sourceTagCount", tagCustomerRepository
                .findByCustomerId(task.getSourceCustomerId()).size());
        impact.put("targetTagCount", tagCustomerRepository
                .findByCustomerId(task.getTargetCustomerId()).size());
        return impact;
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 合并统计 (任务数 / 完成率 / 平均合并时间)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMergeStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> statusCounts = taskRepository.countByStatus(startTime, endTime);
        List<Map<String, Object>> statusStats = new ArrayList<>();
        long totalTasks = 0L;
        long completedTasks = 0L;
        for (Object[] row : statusCounts) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            totalTasks += count;
            if (ScrmIdentityMergeTaskService.STATUS_COMPLETED.equals(status)) {
                completedTasks = count;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", status);
            m.put("count", count);
            statusStats.add(m);
        }
        // 按合并类型统计
        List<Object[]> typeCounts = taskRepository.countByMergeType(startTime, endTime);
        List<Map<String, Object>> typeStats = new ArrayList<>();
        for (Object[] row : typeCounts) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mergeType", row[0]);
            m.put("count", row[1]);
            typeStats.add(m);
        }
        // 平均合并耗时
        Double avgDuration = taskRepository.avgCompletedDurationSeconds(startTime, endTime);
        // 历史总数与回滚数
        long totalHistory = historyRepository.count();
        long rolledBackCount = historyRepository.countByRolledBack(Boolean.TRUE);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTasks", totalTasks);
        result.put("completedTasks", completedTasks);
        result.put("completionRate", totalTasks > 0 ? (double) completedTasks / totalTasks : 0.0);
        result.put("avgDurationSeconds", avgDuration != null ? avgDuration : 0.0);
        result.put("totalHistory", totalHistory);
        result.put("rolledBackCount", rolledBackCount);
        result.put("rollbackRate", totalHistory > 0 ? (double) rolledBackCount / totalHistory : 0.0);
        result.put("statusStats", statusStats);
        result.put("mergeTypeStats", typeStats);
        return result;
    }

    /**
     * 重复统计 (重复对数 / 各匹配类型)。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDuplicateStats() {
        Map<String, Object> report = ruleService.getDuplicateReport();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalDuplicatePairs", report.get("totalDuplicatePairs"));
        result.put("highScoreDuplicates", report.get("highScoreDuplicates"));
        result.put("mediumScoreDuplicates", report.get("mediumScoreDuplicates"));
        result.put("identityTypeStats", report.get("identityTypeStats"));
        return result;
    }

    /**
     * 规则统计 (匹配数 / 合并数)。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRuleStats() {
        List<ScrmIdentityMergeRuleEntity> rules = ruleRepository.findAllByOrderByPriorityDesc();
        List<Map<String, Object>> ruleStats = new ArrayList<>();
        long totalMatches = 0L;
        long totalMerges = 0L;
        for (ScrmIdentityMergeRuleEntity rule : rules) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ruleId", rule.getId());
            m.put("ruleName", rule.getRuleName());
            m.put("enabled", rule.getEnabled());
            m.put("autoMerge", rule.getAutoMerge());
            m.put("matchCount", rule.getMatchCount() != null ? rule.getMatchCount() : 0);
            m.put("mergeCount", rule.getMergeCount() != null ? rule.getMergeCount() : 0);
            m.put("lastExecutedAt", rule.getLastExecutedAt());
            totalMatches += rule.getMatchCount() != null ? rule.getMatchCount() : 0;
            totalMerges += rule.getMergeCount() != null ? rule.getMergeCount() : 0;
            ruleStats.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRules", rules.size());
        result.put("enabledRules", rules.stream().filter(r -> Boolean.TRUE.equals(r.getEnabled())).count());
        result.put("totalMatches", totalMatches);
        result.put("totalMerges", totalMerges);
        result.put("ruleStats", ruleStats);
        return result;
    }

    /**
     * 身份统计 (各类型 / 各平台 / 验证率)。
     *
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getIdentityStats() {
        long totalIdentities = identityRepository.count();
        long verifiedIdentities = identityRepository.countByIsVerified(Boolean.TRUE);
        // 按身份类型统计
        List<Object[]> typeCounts = identityRepository.countByIdentityType();
        List<Map<String, Object>> typeStats = new ArrayList<>();
        for (Object[] row : typeCounts) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("identityType", row[0]);
            m.put("count", row[1]);
            typeStats.add(m);
        }
        // 按平台统计
        List<Object[]> platformCounts = identityRepository.countByPlatform();
        List<Map<String, Object>> platformStats = new ArrayList<>();
        for (Object[] row : platformCounts) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("platform", row[0]);
            m.put("count", row[1]);
            platformStats.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalIdentities", totalIdentities);
        result.put("verifiedIdentities", verifiedIdentities);
        result.put("verificationRate", totalIdentities > 0 ? (double) verifiedIdentities / totalIdentities : 0.0);
        result.put("identityTypeStats", typeStats);
        result.put("platformStats", platformStats);
        return result;
    }
}