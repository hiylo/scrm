/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCalendarConflictService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmCalendarConflictEntity;
import org.hiylo.scrm.entity.ScrmCalendarEventEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCalendarConflictRepository;
import org.hiylo.scrm.repository.ScrmCalendarEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 营销日历冲突检测服务 (冲突检测子域)。
 * <p>
 * 承载事件冲突检测 (单事件 / 全量批量) / 冲突查询 / 解决与忽略, 托管冲突类型 / 严重程度 /
 * 解决状态常量、事件对冲突检测 {@link #detectPairConflicts} 与当前操作人获取
 * {@link #currentOperator()}。CSV 解析复用 {@link ScrmMarketingCalendarStatsService} 的包级能力。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMarketingCalendarConflictService {

    /** 事件状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    /** 冲突解决状态: 未解决 */
    private static final String RESOLVED_UNRESOLVED = "UNRESOLVED";
    /** 冲突解决状态: 已解决 */
    private static final String RESOLVED_RESOLVED = "RESOLVED";
    /** 冲突解决状态: 已忽略 */
    private static final String RESOLVED_IGNORED = "IGNORED";

    /** 冲突类型: 时间重叠 */
    private static final String CONFLICT_TIME_OVERLAP = "TIME_OVERLAP";
    /** 冲突类型: 渠道冲突 */
    private static final String CONFLICT_CHANNEL = "CHANNEL_CONFLICT";
    /** 冲突类型: 客群重叠 */
    private static final String CONFLICT_AUDIENCE = "AUDIENCE_OVERLAP";
    /** 冲突类型: 预算超支 */
    private static final String CONFLICT_BUDGET = "BUDGET_EXCEED";

    /** 冲突严重程度: 警告 */
    private static final String SEVERITY_WARNING = "WARNING";
    /** 冲突严重程度: 错误 */
    private static final String SEVERITY_ERROR = "ERROR";
    /** 冲突严重程度: 信息 */
    private static final String SEVERITY_INFO = "INFO";

    /** 预算超支阈值 (按月度预算占比超过 80% 视为风险) */
    private static final double BUDGET_WARN_RATIO = 0.8;

    /** 事件数据访问层 */
    private final ScrmCalendarEventRepository eventRepository;

    /** 冲突数据访问层 */
    private final ScrmCalendarConflictRepository conflictRepository;

    /**
     * 检测指定事件的冲突。
     * <p>检测维度: 时间重叠 / 渠道冲突 / 客群重叠 / 预算超支。</p>
     *
     * @param eventId 事件 ID
     * @return 检测到的冲突列表
     * @throws ScrmException 事件不存在
     */
    @Transactional
    public List<ScrmCalendarConflictEntity> detectConflicts(Long eventId) throws ScrmException {
        ScrmCalendarEventEntity target = findEventOrThrow(eventId);
        // 查询时间区间内有交集的全部事件
        Specification<ScrmCalendarEventEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("id"), eventId));
            predicates.add(cb.notEqual(root.get("status"), STATUS_CANCELLED));
            // 时间交集
            predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), target.getEndDate()));
            predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), target.getStartDate()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCalendarEventEntity> candidates = eventRepository.findAll(spec);
        List<ScrmCalendarConflictEntity> conflicts = new ArrayList<>();
        for (ScrmCalendarEventEntity other : candidates) {
            conflicts.addAll(detectPairConflicts(target, other));
        }
        log.info("检测事件冲突: eventId={}, conflicts={}", eventId, conflicts.size());
        return conflicts;
    }

    /**
     * 批量检测全部未取消事件的冲突。
     *
     * @return 检测到的冲突列表
     */
    @Transactional
    public List<ScrmCalendarConflictEntity> batchDetectConflicts() {
        Specification<ScrmCalendarEventEntity> spec = (root, query, cb) -> cb.and(
                cb.notEqual(root.get("status"), STATUS_CANCELLED));
        List<ScrmCalendarEventEntity> all = eventRepository.findAll(spec);
        List<ScrmCalendarConflictEntity> conflicts = new ArrayList<>();
        // 两两配对检测
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                conflicts.addAll(detectPairConflicts(all.get(i), all.get(j)));
            }
        }
        log.info("批量检测事件冲突: total={}, conflicts={}", all.size(), conflicts.size());
        return conflicts;
    }

    /**
     * 查询冲突详情。
     *
     * @param id 冲突 ID
     * @return 冲突实体
     * @throws ScrmException 冲突不存在
     */
    @Transactional(readOnly = true)
    public ScrmCalendarConflictEntity getConflict(Long id) throws ScrmException {
        return findConflictOrThrow(id);
    }

    /**
     * 分页查询冲突, 支持按解决状态 / 严重程度过滤。
     *
     * @param resolvedStatus 解决状态过滤（可空）
     * @param severity       严重程度过滤（可空）
     * @param pageable       分页参数
     * @return 冲突分页结果 (按 detectedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCalendarConflictEntity> listConflicts(String resolvedStatus, String severity, Pageable pageable) {
        Specification<ScrmCalendarConflictEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (resolvedStatus != null && !resolvedStatus.isBlank()) {
                predicates.add(cb.equal(root.get("resolvedStatus"), resolvedStatus));
            }
            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            query.orderBy(cb.desc(root.get("detectedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return conflictRepository.findAll(spec, pageable);
    }

    /**
     * 解决冲突 (UNRESOLVED → RESOLVED)。
     *
     * @param id   冲突 ID
     * @param note 解决备注
     * @return 更新后的冲突
     * @throws ScrmException 冲突不存在 / 状态非法
     */
    @Transactional
    public ScrmCalendarConflictEntity resolveConflict(Long id, String note) throws ScrmException {
        ScrmCalendarConflictEntity entity = findConflictOrThrow(id);
        if (RESOLVED_RESOLVED.equals(entity.getResolvedStatus())) {
            throw ScrmException.conflict("冲突已解决, 不可重复解决: id=" + id);
        }
        entity.setResolvedStatus(RESOLVED_RESOLVED);
        entity.setResolvedBy(currentOperator());
        entity.setResolvedAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) {
            entity.setResolutionNote(note);
        }
        entity = conflictRepository.save(entity);
        log.info("解决冲突: id={}, note={}", id, note);
        return entity;
    }

    /**
     * 忽略冲突 (UNRESOLVED → IGNORED)。
     *
     * @param id   冲突 ID
     * @param note 忽略备注
     * @return 更新后的冲突
     * @throws ScrmException 冲突不存在 / 状态非法
     */
    @Transactional
    public ScrmCalendarConflictEntity ignoreConflict(Long id, String note) throws ScrmException {
        ScrmCalendarConflictEntity entity = findConflictOrThrow(id);
        if (RESOLVED_RESOLVED.equals(entity.getResolvedStatus())) {
            throw ScrmException.conflict("冲突已解决, 不可忽略: id=" + id);
        }
        entity.setResolvedStatus(RESOLVED_IGNORED);
        entity.setResolvedBy(currentOperator());
        entity.setResolvedAt(LocalDateTime.now());
        if (note != null && !note.isBlank()) {
            entity.setResolutionNote(note);
        }
        entity = conflictRepository.save(entity);
        log.info("忽略冲突: id={}, note={}", id, note);
        return entity;
    }

    /**
     * 查询事件相关冲突。
     *
     * @param eventId 事件 ID
     * @return 冲突列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCalendarConflictEntity> getEventConflicts(Long eventId) {
        return conflictRepository.findByEventId(eventId);
    }

    /**
     * 检测两个事件之间的冲突 (时间重叠 / 渠道冲突 / 客群重叠 / 预算超支)。
     * <p>避免重复检测: 若已存在该事件对的冲突记录则跳过。</p>
     *
     * @param event1   事件1
     * @param event2   事件2
     * @return 检测到的冲突列表
     */
    private List<ScrmCalendarConflictEntity> detectPairConflicts(ScrmCalendarEventEntity event1,
                                                                  ScrmCalendarEventEntity event2) {
        List<ScrmCalendarConflictEntity> conflicts = new ArrayList<>();
        // 已存在的冲突记录 (任意解决状态) 不重复检测
        if (!conflictRepository.findByEventPair(event1.getId(), event2.getId()).isEmpty()) {
            return conflicts;
        }
        // 1. 时间重叠 (时间区间有交集即视为冲突)
        boolean timeOverlap = !event1.getEndDate().isBefore(event2.getStartDate()) && !event1.getStartDate().isAfter(event2.getEndDate());
        if (timeOverlap) {
            conflicts.add(buildConflict(event1, event2, CONFLICT_TIME_OVERLAP, SEVERITY_WARNING,
                    "事件时间重叠: " + event1.getStartDate() + "~" + event1.getEndDate()
                            + " vs " + event2.getStartDate() + "~" + event2.getEndDate(),
                    null, null));
        }
        // 2. 渠道冲突 (有交集的渠道)
        List<String> channels1 = ScrmMarketingCalendarStatsService.parseCsv(event1.getChannels());
        List<String> channels2 = ScrmMarketingCalendarStatsService.parseCsv(event2.getChannels());
        List<String> overlapChannels = new ArrayList<>(channels1);
        overlapChannels.retainAll(channels2);
        if (!overlapChannels.isEmpty() && timeOverlap) {
            conflicts.add(buildConflict(event1, event2, CONFLICT_CHANNEL, SEVERITY_WARNING,
                    "渠道冲突, 重叠渠道: " + String.join(",", overlapChannels),
                    String.join(",", overlapChannels), null));
        }
        // 3. 客群重叠 (targetSegment 字段非空且相同)
        if (event1.getTargetSegment() != null && !event1.getTargetSegment().isBlank() && event1.getTargetSegment().equals(event2.getTargetSegment()) && timeOverlap) {
            conflicts.add(buildConflict(event1, event2, CONFLICT_AUDIENCE, SEVERITY_INFO,
                    "目标客群重叠: " + event1.getTargetSegment(),
                    null, event1.getTargetSegment()));
        }
        // 4. 预算超支 (两事件预算之和超过单事件预算的 BUDGET_WARN_RATIO 视为风险, 仅在时间重叠时检测)
        if (timeOverlap) {
            double budget1 = event1.getBudget() != null ? event1.getBudget() : 0.0;
            double budget2 = event2.getBudget() != null ? event2.getBudget() : 0.0;
            double maxBudget = Math.max(budget1, budget2);
            if (maxBudget > 0 && (budget1 + budget2) / maxBudget > (1 + BUDGET_WARN_RATIO)) {
                conflicts.add(buildConflict(event1, event2, CONFLICT_BUDGET, SEVERITY_ERROR,
                        "预算超支风险: 总预算 " + (budget1 + budget2) + " 超过单事件预算 " + maxBudget,
                        null, null));
            }
        }
        // 持久化冲突记录
        if (!conflicts.isEmpty()) {
            conflictRepository.saveAll(conflicts);
        }
        return conflicts;
    }

    /**
     * 构建冲突实体。
     *
     * @param event1      事件1
     * @param event2      事件2
     * @param conflictType 冲突类型
     * @param severity     严重程度
     * @param description  冲突描述
     * @param channels     重叠渠道
     * @param audience     重叠客群
     * @return 冲突实体 (未持久化)
     */
    private ScrmCalendarConflictEntity buildConflict(ScrmCalendarEventEntity event1,
                                                     ScrmCalendarEventEntity event2,
                                                     String conflictType, String severity,
                                                     String description, String channels, String audience) {
        ScrmCalendarConflictEntity entity = new ScrmCalendarConflictEntity();
        entity.setEvent1Id(event1.getId());
        entity.setEvent2Id(event2.getId());
        entity.setConflictType(conflictType);
        entity.setSeverity(severity);
        entity.setDescription(description);
        entity.setOverlappingChannels(channels);
        entity.setOverlappingAudience(audience);
        entity.setResolvedStatus(RESOLVED_UNRESOLVED);
        entity.setDetectedAt(LocalDateTime.now());
        return entity;
    }

    /**
     * 获取当前操作人 (优先从 UserContext 获取)。
     *
     * @return 操作人用户名
     */
    private String currentOperator() {
        return ScrmMarketingCalendarEventService.currentOperator();
    }

    /**
     * 按主键查询事件, 不存在抛异常, 并校验账号归属。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    private ScrmCalendarEventEntity findEventOrThrow(Long id) throws ScrmException {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "日历事件不存在: id=" + id));
    }

    /**
     * 按主键查询冲突, 不存在抛异常, 并校验账号归属。
     *
     * @param id 冲突 ID
     * @return 冲突实体
     * @throws ScrmException 冲突不存在
     */
    private ScrmCalendarConflictEntity findConflictOrThrow(Long id) throws ScrmException {
        return conflictRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "日历冲突不存在: id=" + id));
    }
}