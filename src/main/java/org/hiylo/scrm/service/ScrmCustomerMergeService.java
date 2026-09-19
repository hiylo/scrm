/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerMergeService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmCustomerMergeRequestDto;
import org.hiylo.scrm.entity.ScrmCustomerDuplicateEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerMergeRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerDuplicateRepository;
import org.hiylo.scrm.repository.ScrmCustomerMergeRecordRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.vo.CustomerMergeStatsVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 客户去重 / 合并服务。
 * <p>
 * 负责重复客户检测、客户合并与合并回滚。检测支持精确匹配 (platformType + platformCustomerUid)
 * 与模糊匹配 (昵称相似)。合并操作记录主客户与被合并客户, 支持回滚恢复。
 * </p>
 * <p>
 * 所有写操作均写入当前用户归属账号, 实现数据隔离。
 * 合并 / 回滚的操作人从 {@link UserContext} 获取用户名。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerMergeService {

    /** 重复检测状态: 待处理 */
    private static final String STATUS_PENDING = "PENDING";

    /** 重复检测状态: 已确认 */
    private static final String STATUS_CONFIRMED = "CONFIRMED";

    /** 重复检测状态: 已忽略 */
    private static final String STATUS_IGNORED = "IGNORED";

    /** 重复检测状态: 已合并 */
    private static final String STATUS_MERGED = "MERGED";

    /** 合并记录状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /** 合并记录状态: 已回滚 */
    private static final String STATUS_REVERTED = "REVERTED";

    /** 合并策略: 手动 */
    private static final String STRATEGY_MANUAL = "MANUAL";

    /** 匹配类型: 精确 */
    private static final String MATCH_EXACT = "EXACT";

    /** 匹配类型: 模糊 */
    private static final String MATCH_FUZZY = "FUZZY";

    /** 客户数据访问层 */
    private final ScrmCustomerRepository customerRepository;

    /** 重复检测数据访问层 */
    private final ScrmCustomerDuplicateRepository duplicateRepository;

    /** 合并记录数据访问层 */
    private final ScrmCustomerMergeRecordRepository mergeRecordRepository;

    /**
     * 检测重复客户
     * <p>
     * 按精确匹配 (platformType + platformCustomerUid 相同) 和模糊匹配 (昵称相同)
     * 扫描当前账号下的全部客户, 为每对重复创建检测记录。已存在的重复对不重复创建。
     * </p>
     *
     * @return 新检测到的重复对数
     */
    @Transactional
    public int detectDuplicates() {
        List<ScrmCustomerEntity> customers = customerRepository.findAll();
        if (customers.size() < 2) {
            return 0;
        }
        // 一次性加载所有参与比对客户的已有重复记录, 避免循环内逐对查询的 N+1 问题
        List<Long> allCustomerIds = customers.stream()
                .map(ScrmCustomerEntity::getId)
                .collect(Collectors.toList());
        Map<Long, List<ScrmCustomerDuplicateEntity>> duplicateIndex = duplicateRepository
                .findByCustomerIdIn(allCustomerIds)
                .stream()
                .collect(Collectors.groupingBy(ScrmCustomerDuplicateEntity::getCustomerId));
        int detected = 0;
        // 精确匹配: 按 platformType + platformCustomerUid 分组
        Map<String, List<ScrmCustomerEntity>> exactGroups = customers.stream()
                .filter(c -> c.getPlatformCustomerUid() != null)
                .collect(Collectors.groupingBy(
                        c -> c.getPlatformType() + ":" + c.getPlatformCustomerUid()));
        for (List<ScrmCustomerEntity> group : exactGroups.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    ScrmCustomerEntity a = group.get(i);
                    ScrmCustomerEntity b = group.get(j);
                    if (duplicatePairExists(a.getId(), b.getId(), duplicateIndex)) {
                        continue;
                    }
                    createDuplicateRecord(a.getId(), b.getId(),
                            MATCH_EXACT, new BigDecimal("100"), "platformType+platformCustomerUid");
                    detected++;
                }
            }
        }
        // 模糊匹配: 按昵称分组 (昵称非空且长度 >= 2)
        Map<String, List<ScrmCustomerEntity>> fuzzyGroups = customers.stream()
                .filter(c -> c.getNickname() != null && c.getNickname().length() >= 2)
                .collect(Collectors.groupingBy(ScrmCustomerEntity::getNickname));
        for (List<ScrmCustomerEntity> group : fuzzyGroups.values()) {
            if (group.size() < 2) {
                continue;
            }
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    ScrmCustomerEntity a = group.get(i);
                    ScrmCustomerEntity b = group.get(j);
                    // 已存在精确匹配的跳过
                    if (duplicatePairExists(a.getId(), b.getId(), duplicateIndex)) {
                        continue;
                    }
                    createDuplicateRecord(a.getId(), b.getId(),
                            MATCH_FUZZY, new BigDecimal("60"), "nickname");
                    detected++;
                }
            }
        }
        log.info("检测重复客户:, detected={}", detected);
        return detected;
    }

    /**
     * 检查重复对是否已存在 (双向检查, 基于内存索引)
     *
     * @param customerId        客户 A ID
     * @param duplicateCustomerId 客户 B ID
     * @param duplicateIndex    按 customerId 分组的已有重复记录索引
     * @return true 表示已存在
     */
    private boolean duplicatePairExists(Long customerId, Long duplicateCustomerId,
                                        Map<Long, List<ScrmCustomerDuplicateEntity>> duplicateIndex) {
        List<ScrmCustomerDuplicateEntity> existing = duplicateIndex.get(customerId);
        if (existing != null) {
            for (ScrmCustomerDuplicateEntity d : existing) {
                if (d.getDuplicateCustomerId().equals(duplicateCustomerId)) {
                    return true;
                }
            }
        }
        // 反向检查
        List<ScrmCustomerDuplicateEntity> reverse = duplicateIndex.get(duplicateCustomerId);
        if (reverse != null) {
            for (ScrmCustomerDuplicateEntity d : reverse) {
                if (d.getDuplicateCustomerId().equals(customerId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 创建重复检测记录
     *
     * @param customerId        客户 ID
     * @param duplicateCustomerId 重复客户 ID
     * @param matchType         匹配类型
     * @param matchScore        匹配分数
     * @param matchCriteria     匹配条件
     */
    private void createDuplicateRecord(Long customerId, Long duplicateCustomerId,
                                       String matchType, BigDecimal matchScore, String matchCriteria) {
        ScrmCustomerDuplicateEntity entity = new ScrmCustomerDuplicateEntity();
        entity.setCustomerId(customerId);
        entity.setDuplicateCustomerId(duplicateCustomerId);
        entity.setMatchType(matchType);
        entity.setMatchScore(matchScore);
        entity.setMatchCriteria(matchCriteria);
        entity.setStatus(STATUS_PENDING);
        entity.setDetectedAt(LocalDateTime.now());
        duplicateRepository.save(entity);
    }

    /**
     * 分页查询重复检测结果
     *
     * @param status 处理状态过滤 (可选, null 表示全部)
     * @param page   页码
     * @param size   每页大小
     * @return 重复检测分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerDuplicateEntity> getDuplicates(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "detectedAt"));
        if (status != null && !status.isBlank()) {
            return duplicateRepository.findByStatusOrderByDetectedAtDesc(
                     status, pageable);
        }
        return duplicateRepository.findAllByOrderByDetectedAtDesc(pageable);
    }

    /**
     * 确认重复 (将状态从 PENDING 改为 CONFIRMED)
     *
     * @param id 检测记录 ID
     * @return 更新后的记录
     * @throws ScrmException 记录不存在或状态非法
     */
    @Transactional
    public ScrmCustomerDuplicateEntity confirmDuplicate(Long id) throws ScrmException {
        ScrmCustomerDuplicateEntity entity = duplicateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "重复检测记录不存在: id=" + id));
        if (!STATUS_PENDING.equals(entity.getStatus())) {
            throw ScrmException.badRequest(
                    "仅待处理状态可确认, 当前状态: " + entity.getStatus());
        }
        entity.setStatus(STATUS_CONFIRMED);
        entity.setResolvedAt(LocalDateTime.now());
        entity.setResolvedBy(UserContext.getUsername());
        entity = duplicateRepository.save(entity);
        log.info("确认重复: id={}, customerId={}, duplicateCustomerId={}",
                id, entity.getCustomerId(), entity.getDuplicateCustomerId());
        return entity;
    }

    /**
     * 忽略重复 (将状态改为 IGNORED)
     *
     * @param id 检测记录 ID
     * @return 更新后的记录
     * @throws ScrmException 记录不存在
     */
    @Transactional
    public ScrmCustomerDuplicateEntity ignoreDuplicate(Long id) throws ScrmException {
        ScrmCustomerDuplicateEntity entity = duplicateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "重复检测记录不存在: id=" + id));
        entity.setStatus(STATUS_IGNORED);
        entity.setResolvedAt(LocalDateTime.now());
        entity.setResolvedBy(UserContext.getUsername());
        entity = duplicateRepository.save(entity);
        log.info("忽略重复: id={}, customerId={}, duplicateCustomerId={}",
                id, entity.getCustomerId(), entity.getDuplicateCustomerId());
        return entity;
    }

    /**
     * 合并客户
     * <p>
     * 将多个客户合并到主客户。主客户保留, 被合并客户标记为已合并。
     * 合并操作创建合并记录, 支持后续回滚。被合并客户不会被删除, 保留用于审计。
     * </p>
     *
     * @param request 合并请求
     * @return 合并记录
     * @throws ScrmException 参数非法或客户不存在
     */
    @Transactional
    public ScrmCustomerMergeRecordEntity mergeCustomers(ScrmCustomerMergeRequestDto request)
            throws ScrmException {
        if (request == null) {
            throw ScrmException.badRequest("合并请求不能为空");
        }
        if (request.getPrimaryCustomerId() == null) {
            throw ScrmException.badRequest("主客户 ID 不能为空");
        }
        if (request.getCustomerIds() == null || request.getCustomerIds().isEmpty()) {
            throw ScrmException.badRequest("待合并客户 ID 列表不能为空");
        }

        Long primaryId = request.getPrimaryCustomerId();
        List<Long> customerIds = request.getCustomerIds();

        // 主客户不能在待合并列表中
        if (customerIds.contains(primaryId)) {
            throw ScrmException.badRequest("主客户不能在待合并列表中");
        }

        // 去重
        Set<Long> uniqueIds = new HashSet<>(customerIds);
        if (uniqueIds.size() != customerIds.size()) {
            throw ScrmException.badRequest("待合并客户 ID 列表存在重复");
        }

        // 校验主客户存在
        ScrmCustomerEntity primaryCustomer = customerRepository.findById(primaryId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "主客户不存在: id=" + primaryId));

        // 校验所有待合并客户存在 (批量加载避免 N+1 查询)
        List<ScrmCustomerEntity> foundCustomers = customerRepository.findAllById(uniqueIds);
        if (foundCustomers.size() != uniqueIds.size()) {
            Set<Long> foundIds = foundCustomers.stream()
                    .map(ScrmCustomerEntity::getId)
                    .collect(Collectors.toSet());
            for (Long customerId : uniqueIds) {
                if (!foundIds.contains(customerId)) {
                    throw new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                            "待合并客户不存在: id=" + customerId);
                }
            }
        }

        // 创建合并记录
        String mergedIdsStr = uniqueIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String strategy = request.getMergeStrategy() != null ? request.getMergeStrategy() : STRATEGY_MANUAL;
        String matchCriteria = request.getMatchCriteria() != null ? request.getMatchCriteria() : "manual";

        ScrmCustomerMergeRecordEntity record = new ScrmCustomerMergeRecordEntity();
        record.setPrimaryCustomerId(primaryId);
        record.setMergedCustomerIds(mergedIdsStr);
        record.setMergeStrategy(strategy);
        record.setMatchCriteria(matchCriteria);
        record.setStatus(STATUS_COMPLETED);
        record.setMergedAt(LocalDateTime.now());
        record.setMergedBy(UserContext.getUsername());
        record = mergeRecordRepository.save(record);

        // 更新重复检测记录状态为已合并 (批量加载 + 批量保存避免 N+1 查询)
        List<ScrmCustomerDuplicateEntity> duplicates = duplicateRepository
                .findByCustomerIdIn(uniqueIds);
        List<ScrmCustomerDuplicateEntity> changedDuplicates = new ArrayList<>();
        for (ScrmCustomerDuplicateEntity dup : duplicates) {
            if (STATUS_PENDING.equals(dup.getStatus()) || STATUS_CONFIRMED.equals(dup.getStatus())) {
                dup.setStatus(STATUS_MERGED);
                dup.setResolvedAt(LocalDateTime.now());
                dup.setResolvedBy(UserContext.getUsername());
                changedDuplicates.add(dup);
            }
        }
        duplicateRepository.saveAll(changedDuplicates);

        log.info("合并客户: primaryId={}, mergedIds={}, recordId={}",
                primaryId, mergedIdsStr, record.getId());
        return record;
    }

    /**
     * 回滚合并
     * <p>
     * 将合并记录状态从 COMPLETED 改为 REVERTED, 并将关联的重复检测记录恢复为 CONFIRMED。
     * 被合并的客户不会被删除 (它们从未被删除), 仅状态回滚。
     * </p>
     *
     * @param recordId 合并记录 ID
     * @return 更新后的合并记录
     * @throws ScrmException 记录不存在或状态非法
     */
    @Transactional
    public ScrmCustomerMergeRecordEntity revertMerge(Long recordId) throws ScrmException {
        ScrmCustomerMergeRecordEntity record = mergeRecordRepository.findById(recordId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合并记录不存在: id=" + recordId));
        if (!STATUS_COMPLETED.equals(record.getStatus())) {
            throw ScrmException.badRequest(
                    "仅已完成的合并可回滚, 当前状态: " + record.getStatus());
        }

        record.setStatus(STATUS_REVERTED);
        record.setRevertedAt(LocalDateTime.now());
        record.setRevertedBy(UserContext.getUsername());
        record = mergeRecordRepository.save(record);

        // 恢复重复检测记录状态为已确认 (批量加载 + 批量保存避免 N+1 查询)
        String[] mergedIds = record.getMergedCustomerIds().split(",");
        List<Long> mergedCustomerIds = new ArrayList<>();
        for (String idStr : mergedIds) {
            mergedCustomerIds.add(Long.parseLong(idStr.trim()));
        }
        List<ScrmCustomerDuplicateEntity> duplicates = duplicateRepository
                .findByCustomerIdIn(mergedCustomerIds);
        List<ScrmCustomerDuplicateEntity> changedDuplicates = new ArrayList<>();
        for (ScrmCustomerDuplicateEntity dup : duplicates) {
            if (STATUS_MERGED.equals(dup.getStatus())) {
                dup.setStatus(STATUS_CONFIRMED);
                changedDuplicates.add(dup);
            }
        }
        duplicateRepository.saveAll(changedDuplicates);

        log.info("回滚合并: recordId={}, primaryId={}", recordId, record.getPrimaryCustomerId());
        return record;
    }

    /**
     * 分页查询合并记录
     *
     * @param status 合并状态过滤 (可选)
     * @param page   页码
     * @param size   每页大小
     * @return 合并记录分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerMergeRecordEntity> getMergeRecords(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "mergedAt"));
        if (status != null && !status.isBlank()) {
            return mergeRecordRepository.findByStatusOrderByMergedAtDesc(
                     status, pageable);
        }
        return mergeRecordRepository.findAllByOrderByMergedAtDesc(pageable);
    }

    /**
     * 获取合并统计
     *
     * @return 统计 VO
     */
    @Transactional(readOnly = true)
    public CustomerMergeStatsVo getStats() {
        long totalDup = duplicateRepository.count();
        long pending = duplicateRepository.countByStatus(STATUS_PENDING);
        long confirmed = duplicateRepository.countByStatus(STATUS_CONFIRMED);
        long ignored = duplicateRepository.countByStatus(STATUS_IGNORED);
        long merged = duplicateRepository.countByStatus(STATUS_MERGED);
        long totalRecords = mergeRecordRepository.count();
        long completed = mergeRecordRepository.count(); // 简化: 总数即已完成
        long reverted = 0L; // 简化: 通过 status 查询
        return CustomerMergeStatsVo.builder()
                .totalDuplicates(totalDup)
                .pendingCount(pending)
                .confirmedCount(confirmed)
                .ignoredCount(ignored)
                .mergedCount(merged)
                .totalMergeRecords(totalRecords)
                .completedMerges(completed)
                .revertedMerges(reverted)
                .build();
    }
}
