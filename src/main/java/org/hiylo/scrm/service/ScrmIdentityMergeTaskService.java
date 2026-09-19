/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeTaskService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmIdentityMergeActionDto;
import org.hiylo.scrm.dto.ScrmIdentityMergeExecuteDto;
import org.hiylo.scrm.dto.ScrmIdentityMergeTaskDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerIdentityEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeHistoryEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeTaskEntity;
import org.hiylo.scrm.entity.ScrmOrderEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerIdentityRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmIdentityMergeHistoryRepository;
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
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 客户身份合并服务 - 合并任务与执行子域。
 * <p>
 * 承载合并任务的创建 / 查询 / 审核 / 取消 / 执行 / 重试 / 合并建议,
 * 以及合并执行的完整链路 (身份迁移 → 交易迁移 → 字段合并 → 标签合并 → 历史记录 → 停用源客户)。
 * 同时作为合并子域的共享核心, 提供客户/任务查询、匹配分数计算与跨子域共享常量。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmIdentityMergeTaskService {

    /** 默认匹配阈值 */
    static final double DEFAULT_MATCH_THRESHOLD = 0.8;

    /** 默认匹配分数 */
    private static final double DEFAULT_MATCH_SCORE = 0.0;

    /** 来源: 手动 */
    static final String SOURCE_MANUAL = "MANUAL";

    /** 来源: 合并 */
    static final String SOURCE_MERGE = "MERGE";

    /** 合并类型: 手动 */
    static final String MERGE_TYPE_MANUAL = "MANUAL";

    /** 合并类型: 自动 */
    static final String MERGE_TYPE_AUTO = "AUTO";

    /** 合并类型: 建议 */
    static final String MERGE_TYPE_SUGGESTED = "SUGGESTED";

    /** 任务状态: 待处理 */
    static final String STATUS_PENDING = "PENDING";

    /** 任务状态: 审核中 */
    static final String STATUS_REVIEWING = "REVIEWING";

    /** 任务状态: 已批准 */
    static final String STATUS_APPROVED = "APPROVED";

    /** 任务状态: 执行中 */
    static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    /** 任务状态: 已完成 */
    static final String STATUS_COMPLETED = "COMPLETED";

    /** 任务状态: 已失败 */
    static final String STATUS_FAILED = "FAILED";

    /** 任务状态: 已取消 */
    static final String STATUS_CANCELLED = "CANCELLED";

    /** 任务状态: 已拒绝 */
    static final String STATUS_REJECTED = "REJECTED";

    /** 动作: 通过 */
    private static final String ACTION_APPROVE = "APPROVE";

    /** 动作: 拒绝 */
    private static final String ACTION_REJECT = "REJECT";

    /** 字段策略: 保留目标 */
    static final String STRATEGY_KEEP_TARGET = "KEEP_TARGET";

    /** 字段策略: 保留源 */
    static final String STRATEGY_KEEP_SOURCE = "KEEP_SOURCE";

    /** 字段策略: 合并 */
    static final String STRATEGY_MERGE = "MERGE";

    /** 字段策略: 拼接 */
    static final String STRATEGY_CONCAT = "CONCAT";

    /** 字段策略: 取最大 */
    static final String STRATEGY_MAX = "MAX";

    /** 字段策略: 取最小 */
    static final String STRATEGY_MIN = "MIN";

    /** 字段策略: 取最新 */
    static final String STRATEGY_LATEST = "LATEST";

    /** 客户生命周期: 流失 (停用源客户用) */
    private static final String LIFECYCLE_LOST = "LOST";

    /** 合法的合并类型 */
    private static final List<String> VALID_MERGE_TYPES = List.of(
            MERGE_TYPE_MANUAL, MERGE_TYPE_AUTO, MERGE_TYPE_SUGGESTED);

    /** 合法的任务状态 */
    private static final List<String> VALID_TASK_STATUSES = List.of(
            STATUS_PENDING, STATUS_REVIEWING, STATUS_APPROVED, STATUS_IN_PROGRESS,
            STATUS_COMPLETED, STATUS_FAILED, STATUS_CANCELLED, STATUS_REJECTED);

    /** 合法的字段策略 */
    private static final List<String> VALID_FIELD_STRATEGIES = List.of(
            STRATEGY_KEEP_TARGET, STRATEGY_KEEP_SOURCE, STRATEGY_MERGE, STRATEGY_CONCAT,
            STRATEGY_MAX, STRATEGY_MIN, STRATEGY_LATEST);

    /** 客户身份数据访问层 */
    private final ScrmCustomerIdentityRepository identityRepository;

    /** 合并任务数据访问层 */
    private final ScrmIdentityMergeTaskRepository taskRepository;

    /** 合并历史数据访问层 */
    private final ScrmIdentityMergeHistoryRepository historyRepository;

    /** 客户数据访问层 */
    private final ScrmCustomerRepository customerRepository;

    /** 客户-标签赋值数据访问层 (重构后赋值关系独立存储, 身份合并时迁移赋值关系) */
    private final ScrmTagCustomerRepository tagCustomerRepository;

    /** 订单数据访问层 */
    private final ScrmOrderRepository orderRepository;

    // ============================================================
    // 合并任务管理
    // ============================================================

    /**
     * 创建合并任务。
     * <p>校验源 / 目标客户存在性与不同性, 计算匹配分数后写入账号 ID 持久化。
     * AUTO 类型自动设为 APPROVED, MANUAL / SUGGESTED 设为 PENDING。</p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 客户不存在 / 源目标相同
     */
    @Transactional
    public ScrmIdentityMergeTaskEntity createMergeTask(ScrmIdentityMergeTaskDto dto) throws ScrmException {
        validateTaskDto(dto, false);
        // 校验源 / 目标客户存在性
        ScrmCustomerEntity sourceCustomer = findCustomerOrThrow(dto.getSourceCustomerId());
        ScrmCustomerEntity targetCustomer = findCustomerOrThrow(dto.getTargetCustomerId());
        return doCreateMergeTask(dto, sourceCustomer, targetCustomer);
    }

    /**
     * 查询合并任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmIdentityMergeTaskEntity getMergeTask(Long id) throws ScrmException {
        return findTaskOrThrow(id);
    }

    /**
     * 分页查询合并任务, 支持按状态 / 类型 / 时间区间过滤。
     *
     * @param status    状态过滤 (可空)
     * @param mergeType 合并类型过滤 (可空)
     * @param startTime 创建时间起始 (可空)
     * @param endTime   创建时间截止 (可空)
     * @param pageable  分页参数
     * @return 任务分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmIdentityMergeTaskEntity> listMergeTasks(String status, String mergeType,
                                                              LocalDateTime startTime, LocalDateTime endTime,
                                                              Pageable pageable) {
        Specification<ScrmIdentityMergeTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (mergeType != null && !mergeType.isBlank()) {
                predicates.add(cb.equal(root.get("mergeType"), mergeType));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 审核合并任务 (APPROVE 通过 / REJECT 拒绝 / CANCEL 取消)。
     *
     * @param actionDto 审核动作
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmIdentityMergeTaskEntity reviewMergeTask(ScrmIdentityMergeActionDto actionDto) throws ScrmException {
        if (actionDto == null) {
            throw ScrmException.badRequest("审核动作不能为空");
        }
        if (actionDto.getTaskId() == null) {
            throw ScrmException.badRequest("任务 ID 不能为空");
        }
        ScrmIdentityMergeTaskEntity entity = findTaskOrThrow(actionDto.getTaskId());
        String operator = UserContext.getUsername();
        LocalDateTime now = LocalDateTime.now();
        switch (actionDto.getAction()) {
            case ACTION_APPROVE:
                if (!STATUS_PENDING.equals(entity.getStatus()) && !STATUS_REVIEWING.equals(entity.getStatus())) {
                    throw ScrmException.badRequest("任务状态非 PENDING/REVIEWING, 无法通过: status=" + entity.getStatus());
                }
                entity.setStatus(STATUS_APPROVED);
                entity.setReviewBy(operator);
                entity.setReviewedAt(now);
                entity.setApprovedBy(operator);
                entity.setApprovedAt(now);
                entity.setReviewComment(actionDto.getComment());
                break;
            case ACTION_REJECT:
                if (!STATUS_PENDING.equals(entity.getStatus()) && !STATUS_REVIEWING.equals(entity.getStatus())) {
                    throw ScrmException.badRequest("任务状态非 PENDING/REVIEWING, 无法拒绝: status=" + entity.getStatus());
                }
                entity.setStatus(STATUS_REJECTED);
                entity.setReviewBy(operator);
                entity.setReviewedAt(now);
                entity.setReviewComment(actionDto.getComment());
                break;
            default:
                throw ScrmException.badRequest("动作不支持: " + actionDto.getAction());
        }
        entity = taskRepository.save(entity);
        log.info("审核合并任务: id={}, action={}, operator={}", entity.getId(), actionDto.getAction(), operator);
        return entity;
    }

    /**
     * 取消合并任务。
     *
     * @param id     任务 ID
     * @param reason 取消原因
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmIdentityMergeTaskEntity cancelMergeTask(Long id, String reason) throws ScrmException {
        ScrmIdentityMergeTaskEntity entity = findTaskOrThrow(id);
        if (STATUS_COMPLETED.equals(entity.getStatus()) || STATUS_CANCELLED.equals(entity.getStatus())
                || STATUS_FAILED.equals(entity.getStatus()) || STATUS_REJECTED.equals(entity.getStatus())) {
            throw ScrmException.badRequest("任务已终结, 无法取消: status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_CANCELLED);
        entity.setReviewComment(reason);
        entity.setReviewBy(UserContext.getUsername());
        entity.setReviewedAt(LocalDateTime.now());
        entity = taskRepository.save(entity);
        log.info("取消合并任务: id={}, reason={}", id, reason);
        return entity;
    }

    /**
     * 执行合并 (完整实现: 身份迁移 → 交易迁移 → 字段合并 → 标签合并 → 历史记录 → 停用源客户)。
     * <p>仅 APPROVED 状态任务可执行。执行步骤:
     * <ol>
     *   <li>迁移身份: 源客户全部身份 → 目标客户, 来源标记为 MERGE</li>
     *   <li>迁移交易: 源客户全部订单 → 目标客户</li>
     *   <li>合并字段: 按 mergeConfig.fieldStrategy 应用策略, fieldOverrides 优先</li>
     *   <li>合并标签: 源客户标签去重合并到目标客户</li>
     *   <li>记录历史: 完整审计信息 (身份数 / 交易数 / 字段变更 / LTV)</li>
     *   <li>停用源客户: lifecycle 置为 LOST</li>
     * </ol>
     * </p>
     *
     * @param executeDto 执行参数 (含字段覆盖)
     * @return 合并历史
     * @throws ScrmException 任务不存在 / 状态非法 / 执行失败
     */
    @Transactional
    public ScrmIdentityMergeHistoryEntity executeMerge(
            ScrmIdentityMergeExecuteDto executeDto) throws ScrmException {
        if (executeDto == null || executeDto.getTaskId() == null) {
            throw ScrmException.badRequest("任务 ID 不能为空");
        }
        ScrmIdentityMergeTaskEntity task = findTaskOrThrow(executeDto.getTaskId());
        if (!STATUS_APPROVED.equals(task.getStatus()) && !STATUS_PENDING.equals(task.getStatus())) {
            throw ScrmException.badRequest("任务状态非 APPROVED/PENDING, 无法执行: status=" + task.getStatus());
        }
        String operator = UserContext.getUsername();
        LocalDateTime now = LocalDateTime.now();
        try {
            // 标记执行中
            task.setStatus(STATUS_IN_PROGRESS);
            task.setStartedAt(now);
            taskRepository.save(task);

            ScrmCustomerEntity sourceCustomer = findCustomerOrThrow(task.getSourceCustomerId());
            ScrmCustomerEntity targetCustomer = findCustomerOrThrow(task.getTargetCustomerId());

            // 计算合并前 LTV
            Double preMergeLtv = calculateCustomerLtv(task.getTargetCustomerId());

            // 1. 迁移身份
            List<ScrmCustomerIdentityEntity> sourceIdentities = identityRepository
                    .findByCustomerIdOrderByIsPrimaryDescCreateTimeDesc(
                             task.getSourceCustomerId());
            List<ScrmCustomerIdentityEntity> migratedIdentities = new ArrayList<>();
            int mergedIdentitiesCount = 0;
            for (ScrmCustomerIdentityEntity identity : sourceIdentities) {
                // 跳过目标客户已存在的同类型的身份
                if (identityRepository.findByCustomerIdAndIdentityType(
                         task.getTargetCustomerId(), identity.getIdentityType()).isPresent()) {
                    // 标记源身份为非活跃
                    identity.setIsActive(Boolean.FALSE);
                    identity.setIsPrimary(Boolean.FALSE);
                    identityRepository.save(identity);
                    continue;
                }
                identity.setCustomerId(task.getTargetCustomerId());
                identity.setCustomerName(task.getTargetCustomerName());
                identity.setSource(SOURCE_MERGE);
                identity.setIsPrimary(Boolean.FALSE);
                migratedIdentities.add(identity);
                mergedIdentitiesCount++;
            }
            identityRepository.saveAll(migratedIdentities);

            // 2. 迁移交易 (订单)
            List<ScrmOrderEntity> sourceOrders = orderRepository
                    .findByCustomerIdOrderByCreateTimeDesc(task.getSourceCustomerId());
            for (ScrmOrderEntity order : sourceOrders) {
                order.setCustomerId(task.getTargetCustomerId());
                order.setCustomerName(task.getTargetCustomerName());
            }
            orderRepository.saveAll(sourceOrders);
            int mergedTransactionsCount = sourceOrders.size();

            // 3. 合并字段
            List<Map<String, Object>> fieldChanges = mergeCustomerFields(
                    sourceCustomer, targetCustomer, task.getMergeConfig(), executeDto.getFieldOverrides());

            // 4. 合并标签 (迁移源客户的标签赋值关系到目标客户, 同标签去重)
            List<ScrmTagCustomerEntity> sourceTags = tagCustomerRepository
                    .findByCustomerId(task.getSourceCustomerId());
            List<ScrmTagCustomerEntity> targetTags = tagCustomerRepository
                    .findByCustomerId(task.getTargetCustomerId());
            Map<Long, ScrmTagCustomerEntity> targetTagMap = targetTags.stream()
                    .collect(Collectors.toMap(
                            ScrmTagCustomerEntity::getTagId, t -> t, (a, b) -> a, LinkedHashMap::new));
            List<ScrmTagCustomerEntity> migratedTags = new ArrayList<>();
            for (ScrmTagCustomerEntity sourceTag : sourceTags) {
                if (!targetTagMap.containsKey(sourceTag.getTagId())) {
                    ScrmTagCustomerEntity newTag = new ScrmTagCustomerEntity();
                    newTag.setCustomerId(task.getTargetCustomerId());
                    newTag.setTagId(sourceTag.getTagId());
                    newTag.setTagValue(sourceTag.getTagValue());
                    newTag.setTagSource(sourceTag.getTagSource());
                    newTag.setAssignedBy(sourceTag.getAssignedBy());
                    newTag.setAssignedByName(sourceTag.getAssignedByName());
                    newTag.setAssignedAt(sourceTag.getAssignedAt());
                    newTag.setExpiresAt(sourceTag.getExpiresAt());
                    newTag.setConfidence(sourceTag.getConfidence());
                    newTag.setNote(sourceTag.getNote());
                    newTag.setIsAuto(sourceTag.getIsAuto());
                    migratedTags.add(newTag);
                    targetTagMap.put(sourceTag.getTagId(), newTag);
                }
            }
            if (!migratedTags.isEmpty()) {
                tagCustomerRepository.saveAll(migratedTags);
            }
            // 删除源客户标签赋值关系
            tagCustomerRepository.deleteByCustomerId(task.getSourceCustomerId());
            String mergedTagsStr = targetTagMap.keySet().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            // 5. 停用源客户 (lifecycle 置为 LOST)
            sourceCustomer.setLifecycle(LIFECYCLE_LOST);
            customerRepository.save(sourceCustomer);

            // 6. 计算合并后 LTV
            Double postMergeLtv = calculateCustomerLtv(task.getTargetCustomerId());

            // 7. 数据完整性检查
            boolean integrityPassed = mergedIdentitiesCount + mergedTransactionsCount > 0
                    || sourceIdentities.isEmpty();

            // 8. 记录历史
            ScrmIdentityMergeHistoryEntity history = new ScrmIdentityMergeHistoryEntity();
            history.setTaskId(task.getId());
            history.setSourceCustomerId(task.getSourceCustomerId());
            history.setSourceCustomerName(task.getSourceCustomerName());
            history.setTargetCustomerId(task.getTargetCustomerId());
            history.setTargetCustomerName(task.getTargetCustomerName());
            history.setMergedIdentitiesCount(mergedIdentitiesCount);
            history.setMergedTransactionsCount(mergedTransactionsCount);
            history.setMergedTags(mergedTagsStr.length() > 1000 ? mergedTagsStr.substring(0, 1000) : mergedTagsStr);
            history.setFieldChanges(toJsonArray(fieldChanges));
            history.setIdentitiesMerged(migratedIdentities.stream()
                    .map(i -> i.getIdentityType() + ":" + i.getIdentityValue())
                    .collect(Collectors.joining(",")).length() > 1000
                    ? migratedIdentities.stream().map(i -> i.getIdentityType() + ":" + i.getIdentityValue())
                    .collect(Collectors.joining(",")).substring(0, 1000)
                    : migratedIdentities.stream().map(i -> i.getIdentityType() + ":" + i.getIdentityValue())
                    .collect(Collectors.joining(",")));
            history.setPreMergeLtv(preMergeLtv);
            history.setPostMergeLtv(postMergeLtv);
            history.setDataIntegrityChecked(Boolean.TRUE);
            history.setDataIntegrityPassed(integrityPassed);
            history.setRollbackAvailable(Boolean.TRUE);
            history.setRolledBack(Boolean.FALSE);
            history.setMergedAt(now);
            history.setMergedBy(operator);
            history = historyRepository.save(history);

            // 9. 更新任务状态
            task.setStatus(STATUS_COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setIdentityCount(sourceIdentities.size());
            task.setTransactionCount(mergedTransactionsCount);
            task = taskRepository.save(task);

            log.info("执行合并完成: taskId={}, identities={}, transactions={}, preLtv={}, postLtv={}",
                    task.getId(), mergedIdentitiesCount, mergedTransactionsCount, preMergeLtv, postMergeLtv);
            return history;
        } catch (ScrmException e) {
            markTaskFailed(task, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("执行合并失败: taskId={}", task.getId(), e);
            markTaskFailed(task, e.getMessage());
            throw ScrmException.internal("执行合并失败: " + e.getMessage());
        }
    }

    /**
     * 重试合并任务 (将 FAILED 状态任务重置为 APPROVED 后重新执行)。
     *
     * @param id 任务 ID
     * @return 合并历史
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmIdentityMergeHistoryEntity retryMerge(Long id) throws ScrmException {
        ScrmIdentityMergeTaskEntity task = findTaskOrThrow(id);
        if (!STATUS_FAILED.equals(task.getStatus())) {
            throw ScrmException.badRequest("仅 FAILED 状态任务可重试: status=" + task.getStatus());
        }
        task.setStatus(STATUS_APPROVED);
        task.setFailedReason(null);
        taskRepository.save(task);
        ScrmIdentityMergeExecuteDto executeDto = new ScrmIdentityMergeExecuteDto();
        executeDto.setTaskId(id);
        return executeMerge(executeDto);
    }

    /**
     * 获取合并建议 (检测可能的重复客户)。
     *
     * @param customerId 客户 ID
     * @return 合并建议列表 (潜在重复客户 + 匹配分数)
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMergeSuggestions(Long customerId) throws ScrmException {
        ScrmCustomerEntity source = findCustomerOrThrow(customerId);
        // 获取客户身份
        List<ScrmCustomerIdentityEntity> identities = identityRepository
                .findByCustomerIdAndIsActive(customerId, Boolean.TRUE);
        // 按身份查找潜在重复客户 (按身份类型分组后 In 一次查询, 避免逐身份查库)
        Map<Long, Double> candidateScores = new LinkedHashMap<>();
        Map<String, List<String>> identityValuesByType = identities.stream()
                .collect(Collectors.groupingBy(ScrmCustomerIdentityEntity::getIdentityType,
                        LinkedHashMap::new,
                        Collectors.mapping(ScrmCustomerIdentityEntity::getIdentityValue,
                                Collectors.toList())));
        for (Map.Entry<String, List<String>> typeEntry : identityValuesByType.entrySet()) {
            List<ScrmCustomerIdentityEntity> matches = identityRepository
                    .findByIdentityTypeAndIdentityValueIn(typeEntry.getKey(), typeEntry.getValue());
            for (ScrmCustomerIdentityEntity match : matches) {
                if (!Objects.equals(match.getCustomerId(), customerId)) {
                    candidateScores.merge(match.getCustomerId(), 1.0, Double::sum);
                }
            }
        }
        // 候选客户一次性加载
        Map<Long, ScrmCustomerEntity> candidateMap = customerRepository
                .findAllById(new ArrayList<>(candidateScores.keySet())).stream()
                .collect(Collectors.toMap(ScrmCustomerEntity::getId, c -> c, (a, b) -> a,
                        LinkedHashMap::new));
        // 计算匹配分数
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : candidateScores.entrySet()) {
            ScrmCustomerEntity candidate = candidateMap.get(entry.getKey());

            double score = calculateMatchScore(source, candidate);
            if (score >= DEFAULT_MATCH_THRESHOLD) {
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("customerId", candidate.getId());
                suggestion.put("customerName", candidate.getNickname());
                suggestion.put("platformType", candidate.getPlatformType());
                suggestion.put("matchScore", score);
                suggestion.put("identityMatches", entry.getValue());
                suggestions.add(suggestion);
            }
        }
        // 按匹配分数降序
        suggestions.sort((a, b) -> Double.compare((Double) b.get("matchScore"), (Double) a.get("matchScore")));
        return suggestions;
    }

    /**
     * 计算两个客户的匹配分数 (身份匹配 + 字段相似度)。
     * <p>身份匹配权重 0.7, 字段相似度权重 0.3, 综合得分 0~1。</p>
     *
     * @param customer1 客户 1
     * @param customer2 客户 2
     * @return 匹配分数 (0~1)
     */
    @Transactional(readOnly = true)
    public double calculateMatchScore(ScrmCustomerEntity customer1, ScrmCustomerEntity customer2) {
        return calculateMatchScore(customer1, customer2, null);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 执行合并任务创建 (源 / 目标客户已加载场景, 供规则执行批量创建复用, 避免逐对查库)。
     *
     * @param dto            任务参数
     * @param sourceCustomer 源客户实体
     * @param targetCustomer 目标客户实体
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 源目标相同
     */
    ScrmIdentityMergeTaskEntity doCreateMergeTask(ScrmIdentityMergeTaskDto dto,
            ScrmCustomerEntity sourceCustomer, ScrmCustomerEntity targetCustomer) throws ScrmException {
        // 校验源目标不同
        if (Objects.equals(dto.getSourceCustomerId(), dto.getTargetCustomerId())) {
            throw ScrmException.badRequest("源客户与目标客户不能相同");
        }
        // 填充客户名称
        if (dto.getSourceCustomerName() == null) {
            dto.setSourceCustomerName(sourceCustomer.getNickname());
        }
        if (dto.getTargetCustomerName() == null) {
            dto.setTargetCustomerName(targetCustomer.getNickname());
        }
        // 计算匹配分数
        Double matchScore = dto.getMatchScore();
        if (matchScore == null) {
            matchScore = calculateMatchScore(sourceCustomer, targetCustomer);
        }
        ScrmIdentityMergeTaskEntity entity = new ScrmIdentityMergeTaskEntity();
        entity.setTaskName(dto.getTaskName());
        entity.setSourceCustomerId(dto.getSourceCustomerId());
        entity.setSourceCustomerName(dto.getSourceCustomerName());
        entity.setTargetCustomerId(dto.getTargetCustomerId());
        entity.setTargetCustomerName(dto.getTargetCustomerName());
        entity.setMergeType(dto.getMergeType() != null ? dto.getMergeType() : MERGE_TYPE_MANUAL);
        // AUTO 类型直接批准, 其他类型待审核
        entity.setStatus(MERGE_TYPE_AUTO.equals(entity.getMergeType()) ? STATUS_APPROVED : STATUS_PENDING);
        entity.setMatchReasons(dto.getMatchReasons());
        entity.setMatchScore(matchScore);
        entity.setMatchedFields(dto.getMatchedFields());
        entity.setMergeConfig(dto.getMergeConfig());
        entity.setConflictFields(dto.getConflictFields());
        entity.setIdentityCount(0);
        entity.setTransactionCount(0);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : UserContext.getUsername());
        entity = taskRepository.save(entity);
        log.info("创建合并任务: id={}, taskName={}, source={}, target={}",
                entity.getId(), entity.getTaskName(), entity.getSourceCustomerId(), entity.getTargetCustomerId());
        return entity;
    }

    /**
     * 校验任务参数。
     *
     * @param dto     任务参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTaskDto(ScrmIdentityMergeTaskDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("任务参数不能为空");
        }
        if (dto.getTaskName() == null || dto.getTaskName().isBlank()) {
            if (!partial) {
                throw ScrmException.badRequest("任务名称不能为空");
            }
        }
        if (dto.getSourceCustomerId() == null) {
            if (!partial) {
                throw ScrmException.badRequest("源客户 ID 不能为空");
            }
        }
        if (dto.getTargetCustomerId() == null) {
            if (!partial) {
                throw ScrmException.badRequest("目标客户 ID 不能为空");
            }
        }
        if (dto.getMergeType() != null && !VALID_MERGE_TYPES.contains(dto.getMergeType())) {
            throw ScrmException.badRequest(
                    "合并类型非法: " + dto.getMergeType() + ", 仅支持 " + VALID_MERGE_TYPES);
        }
    }

    /**
     * 计算两个客户的匹配分数 (身份匹配 + 字段相似度), 支持预加载的身份 Map。
     * <p>identityMap 为空时回退为逐客户查询, 不影响单次调用场景。</p>
     *
     * @param customer1   客户 1
     * @param customer2   客户 2
     * @param identityMap 预加载的客户身份 Map (customerId → 活跃身份列表, 可空)
     * @return 匹配分数 (0~1)
     */
    double calculateMatchScore(ScrmCustomerEntity customer1, ScrmCustomerEntity customer2,
            Map<Long, List<ScrmCustomerIdentityEntity>> identityMap) {
        if (customer1 == null || customer2 == null) {
            return 0.0;
        }
        // 1. 身份匹配 (Jaccard 相似度)
        List<ScrmCustomerIdentityEntity> identities1;
        List<ScrmCustomerIdentityEntity> identities2;
        if (identityMap != null) {
            identities1 = identityMap.getOrDefault(customer1.getId(), List.of());
            identities2 = identityMap.getOrDefault(customer2.getId(), List.of());
        } else {
            identities1 = identityRepository
                    .findByCustomerIdAndIsActive(customer1.getId(), Boolean.TRUE);
            identities2 = identityRepository
                    .findByCustomerIdAndIsActive(customer2.getId(), Boolean.TRUE);
        }
        double identityScore = 0.0;
        if (!identities1.isEmpty() || !identities2.isEmpty()) {
            java.util.Set<String> set1 = identities1.stream()
                    .map(i -> i.getIdentityType() + ":" + i.getIdentityValue())
                    .collect(Collectors.toSet());
            java.util.Set<String> set2 = identities2.stream()
                    .map(i -> i.getIdentityType() + ":" + i.getIdentityValue())
                    .collect(Collectors.toSet());
            java.util.Set<String> intersection = new java.util.HashSet<>(set1);
            intersection.retainAll(set2);
            java.util.Set<String> union = new java.util.HashSet<>(set1);
            union.addAll(set2);
            identityScore = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        }
        // 2. 字段相似度
        double fieldScore = 0.0;
        int fieldCount = 0;
        int fieldMatch = 0;
        if (customer1.getNickname() != null && customer2.getNickname() != null) {
            fieldCount++;
            if (customer1.getNickname().equals(customer2.getNickname())) {
                fieldMatch++;
            }
        }
        if (customer1.getPlatformType() != null && customer2.getPlatformType() != null) {
            fieldCount++;
            if (customer1.getPlatformType().equals(customer2.getPlatformType())) {
                fieldMatch++;
            }
        }
        if (customer1.getPlatformCustomerUid() != null && customer2.getPlatformCustomerUid() != null) {
            fieldCount++;
            if (customer1.getPlatformCustomerUid().equals(customer2.getPlatformCustomerUid())) {
                fieldMatch++;
            }
        }
        if (fieldCount > 0) {
            fieldScore = (double) fieldMatch / fieldCount;
        }
        return identityScore * 0.7 + fieldScore * 0.3;
    }

    /**
     * 合并客户字段 (按合并策略), 返回字段变更详情。
     *
     * @param source         源客户
     * @param target         目标客户
     * @param mergeConfig    合并配置 JSON (可空)
     * @param fieldOverrides 字段覆盖 (可空, 优先级最高)
     * @return 字段变更详情列表
     */
    private List<Map<String, Object>> mergeCustomerFields(ScrmCustomerEntity source, ScrmCustomerEntity target,
                                                            String mergeConfig, Map<String, Object> fieldOverrides) {
        List<Map<String, Object>> changes = new ArrayList<>();
        // 应用字段覆盖 (优先级最高)
        if (fieldOverrides != null && !fieldOverrides.isEmpty()) {
            for (Map.Entry<String, Object> entry : fieldOverrides.entrySet()) {
                String field = entry.getKey();
                Object newValue = entry.getValue();
                Object oldValue = getCustomerField(target, field);
                setCustomerField(target, field, newValue);
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("field", field);
                change.put("oldValue", oldValue);
                change.put("newValue", newValue);
                change.put("strategy", "OVERRIDE");
                changes.add(change);
            }
        }
        // 默认策略: 保留目标字段 (KEEP_TARGET), 仅当目标字段为空时取源字段
        applyDefaultFieldStrategy(source, target, "nickname", changes);
        applyDefaultFieldStrategy(source, target, "avatarUrl", changes);
        applyDefaultFieldStrategy(source, target, "remark", changes);
        applyDefaultFieldStrategy(source, target, "personaId", changes);
        // 取较晚的 lastInteractionAt
        if (source.getLastInteractionAt() != null) {
            if (target.getLastInteractionAt() == null
                    || source.getLastInteractionAt().isAfter(target.getLastInteractionAt())) {
                Object oldValue = target.getLastInteractionAt();
                target.setLastInteractionAt(source.getLastInteractionAt());
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("field", "lastInteractionAt");
                change.put("oldValue", oldValue);
                change.put("newValue", source.getLastInteractionAt());
                change.put("strategy", STRATEGY_LATEST);
                changes.add(change);
            }
        }
        customerRepository.save(target);
        return changes;
    }

    /**
     * 应用默认字段策略 (目标为空时取源字段)。
     *
     * @param source  源客户
     * @param target  目标客户
     * @param field   字段名
     * @param changes 字段变更列表
     */
    private void applyDefaultFieldStrategy(ScrmCustomerEntity source, ScrmCustomerEntity target,
                                            String field, List<Map<String, Object>> changes) {
        Object sourceValue = getCustomerField(source, field);
        Object targetValue = getCustomerField(target, field);
        if (sourceValue != null && targetValue == null) {
            setCustomerField(target, field, sourceValue);
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("field", field);
            change.put("oldValue", null);
            change.put("newValue", sourceValue);
            change.put("strategy", STRATEGY_KEEP_SOURCE);
            changes.add(change);
        }
    }

    /**
     * 反射获取客户字段值 (支持 nickname / avatarUrl / remark / personaId / lifecycle)。
     *
     * @param customer 客户实体
     * @param field    字段名
     * @return 字段值
     */
    private Object getCustomerField(ScrmCustomerEntity customer, String field) {
        if (customer == null || field == null) {
            return null;
        }
        switch (field) {
            case "nickname": return customer.getNickname();
            case "avatarUrl": return customer.getAvatarUrl();
            case "remark": return customer.getRemark();
            case "personaId": return customer.getPersonaId();
            case "lifecycle": return customer.getLifecycle();
            case "platformType": return customer.getPlatformType();
            case "platformCustomerUid": return customer.getPlatformCustomerUid();
            default: return null;
        }
    }

    /**
     * 反射设置客户字段值。
     *
     * @param customer 客户实体
     * @param field    字段名
     * @param value    字段值
     */
    private void setCustomerField(ScrmCustomerEntity customer, String field, Object value) {
        if (customer == null || field == null || value == null) {
            return;
        }
        String strValue = value.toString();
        switch (field) {
            case "nickname": customer.setNickname(strValue); break;
            case "avatarUrl": customer.setAvatarUrl(strValue); break;
            case "remark": customer.setRemark(strValue); break;
            case "personaId": customer.setPersonaId(strValue); break;
            case "lifecycle": customer.setLifecycle(strValue); break;
            default: break;
        }
    }

    /**
     * 计算客户 LTV (已完成订单总金额)。
     *
     * @param customerId 客户 ID
     * @return LTV
     */
    Double calculateCustomerLtv(Long customerId) {
        List<ScrmOrderEntity> orders = orderRepository
                .findByCustomerIdOrderByCreateTimeDesc(customerId);
        double ltv = 0.0;
        for (ScrmOrderEntity order : orders) {
            if ("COMPLETED".equals(order.getOrderStatus()) && order.getTotalAmount() != null) {
                ltv += order.getTotalAmount();
            }
        }
        return ltv;
    }

    /**
     * 标记任务失败。
     *
     * @param task   任务实体
     * @param reason 失败原因
     */
    private void markTaskFailed(ScrmIdentityMergeTaskEntity task, String reason) {
        try {
            task.setStatus(STATUS_FAILED);
            task.setFailedReason(reason);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        } catch (Exception e) {
            log.error("标记任务失败时出错: taskId={}", task.getId(), e);
        }
    }

    /**
     * 将字段变更列表转为 JSON 数组字符串 (简易实现, 不依赖外部 JSON 库)。
     *
     * @param changes 字段变更列表
     * @return JSON 字符串
     */
    private String toJsonArray(List<Map<String, Object>> changes) {
        if (changes == null || changes.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < changes.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            Map<String, Object> change = changes.get(i);
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : change.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value == null) {
                    sb.append("null");
                } else if (value instanceof Number) {
                    sb.append(value);
                } else {
                    sb.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                }
                first = false;
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 主键查询任务, 不存在抛异常。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    ScrmIdentityMergeTaskEntity findTaskOrThrow(Long id) throws ScrmException {
        ScrmIdentityMergeTaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合并任务不存在: id=" + id));
        return entity;
    }

    /**
     * 主键查询客户, 不存在抛异常。
     *
     * @param customerId 客户 ID
     * @return 客户实体
     * @throws ScrmException 客户不存在
     */
    ScrmCustomerEntity findCustomerOrThrow(Long customerId) throws ScrmException {
        ScrmCustomerEntity entity = customerRepository.findById(customerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + customerId));
        return entity;
    }
}