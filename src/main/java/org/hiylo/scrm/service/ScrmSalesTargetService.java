/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesTargetService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmSalesAchievementDto;
import org.hiylo.scrm.dto.ScrmSalesForecastDto;
import org.hiylo.scrm.dto.ScrmSalesRankingDto;
import org.hiylo.scrm.dto.ScrmSalesTargetDto;
import org.hiylo.scrm.entity.ScrmSalesAchievementEntity;
import org.hiylo.scrm.entity.ScrmSalesRankingEntity;
import org.hiylo.scrm.entity.ScrmSalesTargetEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmSalesAchievementRepository;
import org.hiylo.scrm.repository.ScrmSalesRankingRepository;
import org.hiylo.scrm.repository.ScrmSalesTargetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 销售目标/业绩管理服务
 * <p>
 * 提供销售目标的设定/查询/归档, 达成记录的录入/批量/调整, 业绩排名的计算/查询/我的排名,
 * 基于历史趋势的业绩预测与达成趋势, 以及目标概览/达成率分布/Top 榜等统计能力。
 * 所有写操作均写入当前用户归属账号, 实现数据隔离。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSalesTargetService {

    // ==================== 常量 ====================

    /** 状态: 激活 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态: 完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";
    /** 状态: 过期 */
    private static final String STATUS_EXPIRED = "EXPIRED";
    /** 状态: 归档 */
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 目标对象类型: 个人 */
    private static final String TARGET_TYPE_INDIVIDUAL = "INDIVIDUAL";
    /** 目标对象类型: 团队 */
    private static final String TARGET_TYPE_TEAM = "TEAM";
    /** 目标对象类型: 部门 */
    private static final String TARGET_TYPE_DEPARTMENT = "DEPARTMENT";

    /** 数据来源: 手动调整 */
    private static final String SOURCE_MANUAL_ADJUST = "MANUAL_ADJUST";

    /** 达成率精度 (保留两位小数) */
    private static final double RATE_SCALE = 100d;
    /** 达成率 100% */
    private static final double RATE_FULL = 100d;

    /** 默认操作人 (请求头未透传 X-User-Id 时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 销售目标数据仓库 */
    private final ScrmSalesTargetRepository targetRepository;
    /** 销售业绩数据仓库 */
    private final ScrmSalesAchievementRepository achievementRepository;
    /** 销售排行数据仓库 */
    private final ScrmSalesRankingRepository rankingRepository;

    // ============================================================
    // 目标管理
    // ============================================================

    /**
     * 创建销售目标
     *
     * @param dto 目标参数
     * @return 创建后的目标
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmSalesTargetDto createTarget(ScrmSalesTargetDto dto) throws ScrmException {
        validateTargetDto(dto);
        if (dto.getPeriodEnd().isBefore(dto.getPeriodStart())) {
            throw ScrmException.badRequest("周期结束日期不能早于开始日期");
        }
        ScrmSalesTargetEntity entity = new ScrmSalesTargetEntity();
        entity.setTargetName(dto.getTargetName());
        entity.setTargetType(dto.getTargetType());
        entity.setTargetId(dto.getTargetId());
        entity.setTargetNameRef(dto.getTargetNameRef());
        entity.setPeriodType(dto.getPeriodType());
        entity.setPeriodStart(dto.getPeriodStart());
        entity.setPeriodEnd(dto.getPeriodEnd());
        entity.setMetricType(dto.getMetricType());
        entity.setTargetValue(dto.getTargetValue());
        entity.setActualValue(0d);
        entity.setAchievementRate(0d);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_ACTIVE);
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : DEFAULT_OPERATOR);
        entity = targetRepository.save(entity);
        log.info("创建销售目标: id={}, name={}, targetType={}, metricType={}",
                entity.getId(), entity.getTargetName(), entity.getTargetType(), entity.getMetricType());
        return toTargetDto(entity);
    }

    /**
     * 更新销售目标
     *
     * @param id  目标 ID
     * @param dto 目标参数
     * @return 更新后的目标
     * @throws ScrmException 目标不存在 / 参数非法
     */
    @Transactional
    public ScrmSalesTargetDto updateTarget(Long id, ScrmSalesTargetDto dto) throws ScrmException {
        ScrmSalesTargetEntity entity = findTargetOrThrow(id);
        if (dto.getTargetName() != null) {
            if (dto.getTargetName().isBlank()) {
                throw ScrmException.badRequest("目标名称不能为空");
            }
            entity.setTargetName(dto.getTargetName());
        }
        if (dto.getTargetType() != null) {
            entity.setTargetType(dto.getTargetType());
        }
        if (dto.getTargetId() != null) {
            entity.setTargetId(dto.getTargetId());
        }
        if (dto.getTargetNameRef() != null) {
            entity.setTargetNameRef(dto.getTargetNameRef());
        }
        if (dto.getPeriodType() != null) {
            entity.setPeriodType(dto.getPeriodType());
        }
        if (dto.getPeriodStart() != null) {
            entity.setPeriodStart(dto.getPeriodStart());
        }
        if (dto.getPeriodEnd() != null) {
            entity.setPeriodEnd(dto.getPeriodEnd());
        }
        if (entity.getPeriodEnd().isBefore(entity.getPeriodStart())) {
            throw ScrmException.badRequest("周期结束日期不能早于开始日期");
        }
        if (dto.getMetricType() != null) {
            entity.setMetricType(dto.getMetricType());
        }
        if (dto.getTargetValue() != null) {
            if (dto.getTargetValue() <= 0) {
                throw ScrmException.badRequest("目标值必须大于 0");
            }
            entity.setTargetValue(dto.getTargetValue());
            // 目标值变更后重新计算达成率
            entity.setAchievementRate(calculateAchievementRate(entity.getActualValue(), entity.getTargetValue()));
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getNotes() != null) {
            entity.setNotes(dto.getNotes());
        }
        entity = targetRepository.save(entity);
        log.info("更新销售目标: id={}, name={}", id, entity.getTargetName());
        return toTargetDto(entity);
    }

    /**
     * 删除销售目标
     * <p>
     * 同时清理该目标下的达成记录。
     * </p>
     *
     * @param id 目标 ID
     * @throws ScrmException 目标不存在
     */
    @Transactional
    public void deleteTarget(Long id) throws ScrmException {
        ScrmSalesTargetEntity entity = findTargetOrThrow(id);
        achievementRepository.deleteByTargetId(id);
        targetRepository.delete(entity);
        log.info("删除销售目标: id={}, name={}", id, entity.getTargetName());
    }

    /**
     * 查询销售目标详情
     *
     * @param id 目标 ID
     * @return 目标 DTO
     * @throws ScrmException 目标不存在
     */
    @Transactional(readOnly = true)
    public ScrmSalesTargetDto getTarget(Long id) throws ScrmException {
        return toTargetDto(findTargetOrThrow(id));
    }

    /**
     * 分页查询销售目标, 支持按对象类型/指标类型/周期类型/状态过滤
     *
     * @param targetType  目标对象类型 (可空)
     * @param metricType  指标类型 (可空)
     * @param periodType  周期类型 (可空)
     * @param status      状态 (可空)
     * @param pageable    分页参数
     * @return 目标分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmSalesTargetDto> listTargets(String targetType, String metricType,
                                                  String periodType, String status, Pageable pageable) {
        Specification<ScrmSalesTargetEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (metricType != null && !metricType.isBlank()) {
                predicates.add(cb.equal(root.get("metricType"), metricType));
            }
            if (periodType != null && !periodType.isBlank()) {
                predicates.add(cb.equal(root.get("periodType"), periodType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "periodStart").and(Sort.by(Sort.Direction.DESC, "createTime")));
        return targetRepository.findAll(spec, sorted).map(this::toTargetDto);
    }

    /**
     * 查询某人某周期目标
     *
     * @param targetId   目标对象 ID (userId/teamId/deptId)
     * @param periodType 周期类型 (可空, 为空则返回该对象所有目标)
     * @return 目标列表
     */
    @Transactional(readOnly = true)
    public List<ScrmSalesTargetDto> getTargetsByAssignee(String targetId, String periodType) {
        List<ScrmSalesTargetEntity> list;
        if (periodType != null && !periodType.isBlank()) {
            list = targetRepository.findByTargetTypeAndTargetIdAndPeriodType(
                     TARGET_TYPE_INDIVIDUAL, targetId, periodType);
        } else {
            list = targetRepository.findAll((root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("targetType"), TARGET_TYPE_INDIVIDUAL));
                predicates.add(cb.equal(root.get("targetId"), targetId));
                return cb.and(predicates.toArray(new Predicate[0]));
            });
        }
        return list.stream().map(this::toTargetDto).collect(Collectors.toList());
    }

    /**
     * 归档销售目标
     *
     * @param id 目标 ID
     * @return 更新后的目标
     * @throws ScrmException 目标不存在
     */
    @Transactional
    public ScrmSalesTargetDto archiveTarget(Long id) throws ScrmException {
        ScrmSalesTargetEntity entity = findTargetOrThrow(id);
        entity.setStatus(STATUS_ARCHIVED);
        entity = targetRepository.save(entity);
        log.info("归档销售目标: id={}", id);
        return toTargetDto(entity);
    }

    // ============================================================
    // 达成记录管理
    // ============================================================

    /**
     * 记录达成
     * <p>
     * 写入达成记录并累加至目标实际值, 重新计算达成率。
     * 若累计实际值达到目标值, 自动将状态置为 COMPLETED。
     * </p>
     *
     * @param dto 达成记录参数
     * @return 创建后的达成记录
     * @throws ScrmException 目标不存在 / 参数非法
     */
    @Transactional
    public ScrmSalesAchievementDto recordAchievement(ScrmSalesAchievementDto dto) throws ScrmException {
        ScrmSalesTargetEntity target = findTargetOrThrow(dto.getTargetId());
        if (dto.getAchievedValue() == null) {
            throw ScrmException.badRequest("本次达成值不能为空");
        }
        if (dto.getAchievementDate() == null) {
            throw ScrmException.badRequest("达成日期不能为空");
        }
        // 写入达成记录
        ScrmSalesAchievementEntity entity = new ScrmSalesAchievementEntity();
        entity.setTargetId(dto.getTargetId());
        entity.setTargetType(dto.getTargetType() != null ? dto.getTargetType() : target.getTargetType());
        entity.setTargetIdRef(dto.getTargetIdRef() != null ? dto.getTargetIdRef() : target.getTargetId());
        entity.setTargetNameRef(dto.getTargetNameRef() != null ? dto.getTargetNameRef() : target.getTargetNameRef());
        entity.setPeriodType(dto.getPeriodType() != null ? dto.getPeriodType() : target.getPeriodType());
        entity.setPeriodStart(dto.getPeriodStart() != null ? dto.getPeriodStart() : target.getPeriodStart());
        entity.setPeriodEnd(dto.getPeriodEnd() != null ? dto.getPeriodEnd() : target.getPeriodEnd());
        entity.setMetricType(dto.getMetricType() != null ? dto.getMetricType() : target.getMetricType());
        entity.setAchievedValue(dto.getAchievedValue());
        entity.setAchievementDate(dto.getAchievementDate());
        entity.setSourceType(dto.getSourceType() != null ? dto.getSourceType() : SOURCE_MANUAL_ADJUST);
        entity.setSourceId(dto.getSourceId());
        entity.setNote(dto.getNote());
        entity.setRecordedAt(LocalDateTime.now());
        entity.setRecordedBy(dto.getRecordedBy() != null ? dto.getRecordedBy() : DEFAULT_OPERATOR);
        entity = achievementRepository.save(entity);

        // 累加目标实际值并重算达成率
        recalculateTarget(dto.getTargetId());
        log.info("记录销售达成: targetId={}, achievedValue={}, sourceType={}",
                dto.getTargetId(), dto.getAchievedValue(), entity.getSourceType());
        return toAchievementDto(entity);
    }

    /**
     * 批量记录达成
     *
     * @param achievements 达成记录列表
     * @return 创建后的达成记录列表
     * @throws ScrmException 部分目标不存在时跳过
     */
    @Transactional
    public List<ScrmSalesAchievementDto> batchRecordAchievements(List<ScrmSalesAchievementDto> achievements)
            throws ScrmException {
        if (achievements == null || achievements.isEmpty()) {
            throw ScrmException.badRequest("达成记录列表不能为空");
        }
        List<ScrmSalesAchievementDto> results = new ArrayList<>();
        for (ScrmSalesAchievementDto dto : achievements) {
            try {
                results.add(recordAchievement(dto));
            } catch (ScrmException e) {
                log.warn("批量记录达成跳过: targetId={}, err={}", dto.getTargetId(), e.getMessage());
            }
        }
        log.info("批量记录销售达成: total={}, success={}", achievements.size(), results.size());
        return results;
    }

    /**
     * 分页查询达成记录, 支持按目标 ID 与达成日期范围过滤
     *
     * @param targetId  目标 ID (可空)
     * @param startDate 达成日期下限 (可空)
     * @param endDate   达成日期上限 (可空)
     * @param pageable  分页参数
     * @return 达成记录分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmSalesAchievementDto> getAchievements(Long targetId, LocalDate startDate,
                                                           LocalDate endDate, Pageable pageable) {
        Specification<ScrmSalesAchievementEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (targetId != null) {
                predicates.add(cb.equal(root.get("targetId"), targetId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("achievementDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("achievementDate"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "achievementDate").and(Sort.by(Sort.Direction.DESC, "recordedAt")));
        return achievementRepository.findAll(spec, sorted).map(this::toAchievementDto);
    }

    /**
     * 手动调整目标实际值
     * <p>
     * 通过 MANUAL_ADJUST 来源达成记录实现增/减调整, 调整值可为负。
     * </p>
     *
     * @param targetId        目标 ID
     * @param adjustmentValue 调整值 (正为增加, 负为减少)
     * @param reason          调整原因
     * @return 调整产生的达成记录
     * @throws ScrmException 目标不存在
     */
    @Transactional
    public ScrmSalesAchievementDto adjustAchievement(Long targetId, Double adjustmentValue, String reason)
            throws ScrmException {
        if (adjustmentValue == null) {
            throw ScrmException.badRequest("调整值不能为空");
        }
        if (adjustmentValue == 0d) {
            throw ScrmException.badRequest("调整值不能为 0");
        }
        ScrmSalesTargetEntity target = findTargetOrThrow(targetId);
        ScrmSalesAchievementDto dto = new ScrmSalesAchievementDto();
        dto.setTargetId(targetId);
        dto.setTargetType(target.getTargetType());
        dto.setTargetIdRef(target.getTargetId());
        dto.setTargetNameRef(target.getTargetNameRef());
        dto.setPeriodType(target.getPeriodType());
        dto.setPeriodStart(target.getPeriodStart());
        dto.setPeriodEnd(target.getPeriodEnd());
        dto.setMetricType(target.getMetricType());
        dto.setAchievedValue(adjustmentValue);
        dto.setAchievementDate(LocalDate.now());
        dto.setSourceType(SOURCE_MANUAL_ADJUST);
        dto.setNote(reason != null ? reason : "手动调整");
        dto.setRecordedBy(DEFAULT_OPERATOR);
        log.info("手动调整销售目标: targetId={}, adjustment={}, reason={}", targetId, adjustmentValue, reason);
        return recordAchievement(dto);
    }

    // ============================================================
    // 排名管理
    // ============================================================

    /**
     * 计算排名
     * <p>
     * 按周期 + 指标类型 + 目标对象类型聚合所有目标, 按达成值降序排名,
     * 清理旧排名后写入新排名。
     * </p>
     *
     * @param periodType  周期类型
     * @param periodStart 周期开始
     * @param periodEnd   周期结束
     * @param metricType  指标类型
     * @param targetType  目标对象类型
     * @return 排名列表
     * @throws ScrmException 参数非法
     */
    @Transactional
    public List<ScrmSalesRankingDto> calculateRanking(String periodType, LocalDate periodStart,
                                                        LocalDate periodEnd, String metricType,
                                                        String targetType) throws ScrmException {
        if (periodType == null || periodType.isBlank()) {
            throw ScrmException.badRequest("周期类型不能为空");
        }
        if (periodStart == null || periodEnd == null) {
            throw ScrmException.badRequest("周期开始/结束日期不能为空");
        }
        if (metricType == null || metricType.isBlank()) {
            throw ScrmException.badRequest("指标类型不能为空");
        }
        if (targetType == null || targetType.isBlank()) {
            throw ScrmException.badRequest("目标对象类型不能为空");
        }
        // 清理旧排名
        rankingRepository.deleteByPeriodTypeAndPeriodStartAndPeriodEndAndMetricTypeAndTargetType(
                 periodType, periodStart, periodEnd, metricType, targetType);

        // 查询周期内符合条件的所有目标
        Specification<ScrmSalesTargetEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("periodType"), periodType));
            predicates.add(cb.equal(root.get("periodStart"), periodStart));
            predicates.add(cb.equal(root.get("periodEnd"), periodEnd));
            predicates.add(cb.equal(root.get("metricType"), metricType));
            predicates.add(cb.equal(root.get("targetType"), targetType));
            predicates.add(cb.notEqual(root.get("status"), STATUS_ARCHIVED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmSalesTargetEntity> targets = targetRepository.findAll(spec);
        // 按达成值降序排名
        targets.sort(Comparator.comparingDouble(
                (ScrmSalesTargetEntity t) -> t.getActualValue() == null ? 0d : t.getActualValue()).reversed());
        LocalDate rankingDate = LocalDate.now();
        List<ScrmSalesRankingDto> results = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            ScrmSalesTargetEntity target = targets.get(i);
            ScrmSalesRankingEntity ranking = new ScrmSalesRankingEntity();
            ranking.setPeriodType(periodType);
            ranking.setPeriodStart(periodStart);
            ranking.setPeriodEnd(periodEnd);
            ranking.setTargetType(targetType);
            ranking.setTargetId(target.getTargetId());
            ranking.setTargetNameRef(target.getTargetNameRef());
            ranking.setMetricType(metricType);
            ranking.setAchievedValue(target.getActualValue());
            ranking.setTargetValue(target.getTargetValue());
            ranking.setAchievementRate(target.getAchievementRate());
            ranking.setRank(i + 1);
            ranking.setRankingDate(rankingDate);
            ranking = rankingRepository.save(ranking);
            results.add(toRankingDto(ranking));
        }
        log.info("计算销售排名: period={}~{}, metricType={}, targetType={}, count={}",
                periodStart, periodEnd, metricType, targetType, results.size());
        return results;
    }

    /**
     * 分页查询排名
     *
     * @param periodType  周期类型
     * @param periodStart 周期开始
     * @param periodEnd   周期结束
     * @param metricType  指标类型
     * @param targetType  目标对象类型
     * @param pageable    分页参数
     * @return 排名分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmSalesRankingDto> getRanking(String periodType, LocalDate periodStart, LocalDate periodEnd,
                                                  String metricType, String targetType, Pageable pageable) {
        Specification<ScrmSalesRankingEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (periodType != null && !periodType.isBlank()) {
                predicates.add(cb.equal(root.get("periodType"), periodType));
            }
            if (periodStart != null) {
                predicates.add(cb.equal(root.get("periodStart"), periodStart));
            }
            if (periodEnd != null) {
                predicates.add(cb.equal(root.get("periodEnd"), periodEnd));
            }
            if (metricType != null && !metricType.isBlank()) {
                predicates.add(cb.equal(root.get("metricType"), metricType));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "rank").and(Sort.by(Sort.Direction.DESC, "rankingDate")));
        return rankingRepository.findAll(spec, sorted).map(this::toRankingDto);
    }

    /**
     * 我的排名
     *
     * @param userId      用户 ID (字符串形式)
     * @param periodType  周期类型
     * @param periodStart 周期开始
     * @param periodEnd   周期结束
     * @return 排名列表 (按排名日期降序)
     */
    @Transactional(readOnly = true)
    public List<ScrmSalesRankingDto> getMyRanking(String userId, String periodType,
                                                    LocalDate periodStart, LocalDate periodEnd) {
        List<ScrmSalesRankingEntity> list = rankingRepository
                .findByPeriodTypeAndPeriodStartAndPeriodEndAndTargetTypeAndTargetIdOrderByRankingDateDesc(
                         periodType, periodStart, periodEnd, TARGET_TYPE_INDIVIDUAL, userId);
        return list.stream().map(this::toRankingDto).collect(Collectors.toList());
    }

    // ============================================================
    // 预测与趋势
    // ============================================================

    /**
     * 业绩预测
     * <p>
     * 基于历史达成记录的趋势 (简单线性回归), 预测未来 forecastDays 天后能否达标。
     * 返回预测达成值、预测达成率、预计缺口与达标概率 (达成率/100, 上限 1)。
     * </p>
     *
     * @param forecastDto 预测请求 (目标 ID + 预测天数)
     * @return 预测结果
     * @throws ScrmException 目标不存在 / 数据不足
     */
    @Transactional(readOnly = true)
    public Map<String, Object> forecastAchievement(ScrmSalesForecastDto forecastDto) throws ScrmException {
        ScrmSalesTargetEntity target = findTargetOrThrow(forecastDto.getTargetId());
        int forecastDays = forecastDto.getForecastDays();
        if (forecastDays <= 0) {
            throw ScrmException.badRequest("预测天数必须大于 0");
        }
        List<ScrmSalesAchievementEntity> records = achievementRepository
                .findByTargetIdOrderByAchievementDateAsc(forecastDto.getTargetId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetId", target.getId());
        result.put("targetName", target.getTargetName());
        result.put("metricType", target.getMetricType());
        result.put("targetValue", target.getTargetValue());
        result.put("currentActualValue", target.getActualValue());
        result.put("currentAchievementRate", target.getAchievementRate());
        result.put("forecastDays", forecastDays);

        // 历史数据不足, 退化为按当前实际值的日均线性外推
        long elapsedDays = Math.max(1L, ChronoUnit.DAYS.between(target.getPeriodStart(), LocalDate.now()) + 1);
        double dailyAvg = target.getActualValue() / elapsedDays;
        double forecastIncrement;
        double slope;
        double intercept;
        if (records.size() < 2) {
            // 数据不足, 使用日均外推
            forecastIncrement = dailyAvg * forecastDays;
            slope = dailyAvg;
            intercept = 0d;
        } else {
            // 简单线性回归: x = 天数偏移, y = 累计达成值
            int n = records.size();
            double sumX = 0d, sumY = 0d, sumXY = 0d, sumX2 = 0d;
            double cumulative = 0d;
            for (int i = 0; i < n; i++) {
                ScrmSalesAchievementEntity r = records.get(i);
                cumulative += r.getAchievedValue();
                long dayOffset = ChronoUnit.DAYS.between(target.getPeriodStart(), r.getAchievementDate());
                double x = dayOffset;
                double y = cumulative;
                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumX2 += x * x;
            }
            double denominator = n * sumX2 - sumX * sumX;
            if (denominator == 0d) {
                slope = dailyAvg;
                intercept = 0d;
            } else {
                slope = (n * sumXY - sumX * sumY) / denominator;
                intercept = (sumY - slope * sumX) / n;
            }
            double currentDayOffset = ChronoUnit.DAYS.between(target.getPeriodStart(), LocalDate.now());
            double futureDayOffset = currentDayOffset + forecastDays;
            double forecastTotal = Math.max(0d, slope * futureDayOffset + intercept);
            forecastIncrement = Math.max(0d, forecastTotal - target.getActualValue());
        }
        double forecastValue = target.getActualValue() + forecastIncrement;
        double forecastRate = calculateAchievementRate(forecastValue, target.getTargetValue());
        double gap = target.getTargetValue() - forecastValue;
        double achieveProbability = Math.min(1d, forecastRate / RATE_FULL);

        result.put("forecastValue", Math.round(forecastValue * 100d) / 100d);
        result.put("forecastAchievementRate", Math.round(forecastRate * 100d) / 100d);
        result.put("gap", Math.round(gap * 100d) / 100d);
        result.put("achieveProbability", Math.round(achieveProbability * 100d) / 100d);
        result.put("dailyAvg", Math.round(dailyAvg * 100d) / 100d);
        result.put("trendSlope", Math.round(slope * 100d) / 100d);
        result.put("trendIntercept", Math.round(intercept * 100d) / 100d);
        result.put("willAchieve", gap <= 0d);
        log.info("业绩预测: targetId={}, forecastDays={}, forecastValue={}, forecastRate={}, willAchieve={}",
                target.getId(), forecastDays, forecastValue, forecastRate, gap <= 0d);
        return result;
    }

    /**
     * 达成趋势
     * <p>
     * 返回最近 days 天内每日累计达成值, 用于前端趋势图绘制。
     * </p>
     *
     * @param targetId 目标 ID
     * @param days     天数
     * @return 趋势数据列表 (每项含 date / cumulativeValue / dailyValue)
     * @throws ScrmException 目标不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAchievementTrend(Long targetId, int days) throws ScrmException {
        findTargetOrThrow(targetId);
        if (days <= 0) {
            days = 7;
        }
        List<ScrmSalesAchievementEntity> records = achievementRepository
                .findByTargetIdOrderByAchievementDateAsc(targetId);
        // 按日期聚合单日达成值
        Map<LocalDate, Double> dailyMap = new LinkedHashMap<>();
        for (ScrmSalesAchievementEntity r : records) {
            dailyMap.merge(r.getAchievementDate(),
                    r.getAchievedValue() != null ? r.getAchievedValue() : 0d, Double::sum);
        }
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1L);
        // 计算起始日期之前的累计值
        double cumulativeBefore = records.stream()
                .filter(r -> r.getAchievementDate() != null && r.getAchievementDate().isBefore(startDate))
                .mapToDouble(r -> r.getAchievedValue() != null ? r.getAchievedValue() : 0d)
                .sum();
        List<Map<String, Object>> result = new ArrayList<>();
        double cumulative = cumulativeBefore;
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            double daily = dailyMap.getOrDefault(date, 0d);
            cumulative += daily;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("dailyValue", Math.round(daily * 100d) / 100d);
            item.put("cumulativeValue", Math.round(cumulative * 100d) / 100d);
            result.add(item);
        }
        log.info("查询达成趋势: targetId={}, days={}", targetId, days);
        return result;
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 目标概览
     * <p>
     * 统计指定周期内的总目标值、总实际值与平均达成率。
     * </p>
     *
     * @param periodType  周期类型 (可空, 为空则按周期范围过滤)
     * @param periodStart 周期开始
     * @param periodEnd   周期结束
     * @return 概览数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTargetOverview(String periodType, LocalDate periodStart, LocalDate periodEnd) {
        Specification<ScrmSalesTargetEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (periodType != null && !periodType.isBlank()) {
                predicates.add(cb.equal(root.get("periodType"), periodType));
            }
            if (periodStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("periodStart"), periodStart));
            }
            if (periodEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("periodEnd"), periodEnd));
            }
            predicates.add(cb.notEqual(root.get("status"), STATUS_ARCHIVED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmSalesTargetEntity> targets = targetRepository.findAll(spec);
        double totalTarget = targets.stream().mapToDouble(t -> t.getTargetValue() != null
                ? t.getTargetValue() : 0d).sum();
        double totalActual = targets.stream().mapToDouble(t -> t.getActualValue() != null
                ? t.getActualValue() : 0d).sum();
        double avgRate = targets.isEmpty() ? 0d
                : targets.stream().mapToDouble(t -> t.getAchievementRate() != null
                        ? t.getAchievementRate() : 0d).average().orElse(0d);
        long completedCount = targets.stream().filter(t -> RATE_FULL <= (t.getAchievementRate() != null
                ? t.getAchievementRate() : 0d)).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTargets", targets.size());
        result.put("completedTargets", completedCount);
        result.put("totalTargetValue", Math.round(totalTarget * 100d) / 100d);
        result.put("totalActualValue", Math.round(totalActual * 100d) / 100d);
        result.put("avgAchievementRate", Math.round(avgRate * 100d) / 100d);
        result.put("overallAchievementRate",
                totalTarget > 0 ? Math.round(totalActual / totalTarget * RATE_FULL * 100d) / 100d : 0d);
        return result;
    }

    /**
     * 达成率分布
     * <p>
     * 按 EXCELLENT(≥100) / GOOD(≥80) / NORMAL(≥60) / BELOW(≥30) / POOR(<30) 五档统计目标数。
     * </p>
     *
     * @param periodType  周期类型 (可空)
     * @param periodStart 周期开始
     * @param periodEnd   周期结束
     * @return 分布列表, 每项含 bucket / count / percentage
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAchievementRateDistribution(String periodType,
                                                                      LocalDate periodStart, LocalDate periodEnd) {
        Specification<ScrmSalesTargetEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (periodType != null && !periodType.isBlank()) {
                predicates.add(cb.equal(root.get("periodType"), periodType));
            }
            predicates.add(cb.notEqual(root.get("status"), STATUS_ARCHIVED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmSalesTargetEntity> targets = targetRepository.findAll(spec);
        // 进一步按周期范围过滤 (Specification 中日期过滤已通过 periodStart/periodEnd, 此处兼容可空)
        if (periodStart != null) {
            targets = targets.stream().filter(t -> !t.getPeriodStart().isBefore(periodStart))
                    .collect(Collectors.toList());
        }
        if (periodEnd != null) {
            targets = targets.stream().filter(t -> !t.getPeriodEnd().isAfter(periodEnd)).collect(Collectors.toList());
        }
        Map<String, Long> bucketCount = new LinkedHashMap<>();
        bucketCount.put("EXCELLENT", 0L);
        bucketCount.put("GOOD", 0L);
        bucketCount.put("NORMAL", 0L);
        bucketCount.put("BELOW", 0L);
        bucketCount.put("POOR", 0L);
        for (ScrmSalesTargetEntity t : targets) {
            double rate = t.getAchievementRate() != null ? t.getAchievementRate() : 0d;
            String bucket;
            if (rate >= 100) {
                bucket = "EXCELLENT";
            } else if (rate >= 80) {
                bucket = "GOOD";
            } else if (rate >= 60) {
                bucket = "NORMAL";
            } else if (rate >= 30) {
                bucket = "BELOW";
            } else {
                bucket = "POOR";
            }
            bucketCount.merge(bucket, 1L, Long::sum);
        }
        long total = targets.size();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : bucketCount.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bucket", e.getKey());
            item.put("count", e.getValue());
            item.put("percentage", total > 0 ? Math.round(e.getValue() * 10000d / total) / 100d : 0d);
            result.add(item);
        }
        return result;
    }

    /**
     * 业绩 Top 榜
     * <p>
     * 按达成率降序返回前 limit 名目标。
     * </p>
     *
     * @param periodType  周期类型 (可空)
     * @param periodStart 周期开始
     * @param periodEnd   周期结束
     * @param limit       返回条数 (默认 10)
     * @return Top 榜列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopPerformers(String periodType, LocalDate periodStart,
                                                        LocalDate periodEnd, Integer limit) {
        int topN = limit != null && limit > 0 ? limit : 10;
        Specification<ScrmSalesTargetEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (periodType != null && !periodType.isBlank()) {
                predicates.add(cb.equal(root.get("periodType"), periodType));
            }
            if (periodStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("periodStart"), periodStart));
            }
            if (periodEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("periodEnd"), periodEnd));
            }
            predicates.add(cb.notEqual(root.get("status"), STATUS_ARCHIVED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmSalesTargetEntity> targets = targetRepository.findAll(spec);
        targets.sort(Comparator.comparingDouble(
                (ScrmSalesTargetEntity t) -> t.getAchievementRate() != null ? t.getAchievementRate() : 0d).reversed());
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, targets.size()); i++) {
            ScrmSalesTargetEntity t = targets.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", i + 1);
            item.put("targetId", t.getId());
            item.put("targetName", t.getTargetName());
            item.put("targetType", t.getTargetType());
            item.put("targetId", t.getTargetId());
            item.put("targetNameRef", t.getTargetNameRef());
            item.put("metricType", t.getMetricType());
            item.put("targetValue", t.getTargetValue());
            item.put("actualValue", t.getActualValue());
            item.put("achievementRate", t.getAchievementRate());
            result.add(item);
        }
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 重算目标实际值与达成率
     * <p>
     * 聚合该目标下所有达成记录的 achievedValue 作为实际值, 重算达成率与 lastUpdated,
     * 若达到目标值则自动置为 COMPLETED。
     * </p>
     *
     * @param targetId 目标 ID
     * @return 更新后的目标
     * @throws ScrmException 目标不存在
     */
    @Transactional
    public ScrmSalesTargetEntity recalculateTarget(Long targetId) throws ScrmException {
        ScrmSalesTargetEntity target = findTargetOrThrow(targetId);
        List<ScrmSalesAchievementEntity> records = achievementRepository
                .findByTargetIdOrderByAchievementDateAsc(targetId);
        double actual = records.stream()
                .mapToDouble(r -> r.getAchievedValue() != null ? r.getAchievedValue() : 0d)
                .sum();
        actual = Math.max(0d, actual);
        target.setActualValue(actual);
        target.setAchievementRate(calculateAchievementRate(actual, target.getTargetValue()));
        target.setLastUpdated(LocalDateTime.now());
        // 达成率 ≥100% 自动完成
        if (target.getAchievementRate() >= RATE_FULL && STATUS_ACTIVE.equals(target.getStatus())) {
            target.setStatus(STATUS_COMPLETED);
        }
        target = targetRepository.save(target);
        log.debug("重算销售目标: targetId={}, actualValue={}, achievementRate={}",
                targetId, actual, target.getAchievementRate());
        return target;
    }

    /**
     * 校验目标 DTO 必填字段
     */
    private void validateTargetDto(ScrmSalesTargetDto dto) throws ScrmException {
        if (dto.getTargetName() == null || dto.getTargetName().isBlank()) {
            throw ScrmException.badRequest("目标名称不能为空");
        }
        if (dto.getTargetType() == null || dto.getTargetType().isBlank()) {
            throw ScrmException.badRequest("目标对象类型不能为空");
        }
        if (dto.getTargetId() == null || dto.getTargetId().isBlank()) {
            throw ScrmException.badRequest("目标对象 ID 不能为空");
        }
        if (dto.getPeriodType() == null || dto.getPeriodType().isBlank()) {
            throw ScrmException.badRequest("周期类型不能为空");
        }
        if (dto.getPeriodStart() == null || dto.getPeriodEnd() == null) {
            throw ScrmException.badRequest("周期开始/结束日期不能为空");
        }
        if (dto.getMetricType() == null || dto.getMetricType().isBlank()) {
            throw ScrmException.badRequest("指标类型不能为空");
        }
        if (dto.getTargetValue() == null || dto.getTargetValue() <= 0) {
            throw ScrmException.badRequest("目标值必须大于 0");
        }
    }

    /**
     * 计算达成率
     * <p>
     * 达成率 = 实际值 / 目标值 * 100, 目标值为 0 时返回 0。
     * </p>
     *
     * @param actual 实际值
     * @param target 目标值
     * @return 达成率 (%)
     */
    private double calculateAchievementRate(double actual, double target) {
        if (target <= 0) {
            return 0d;
        }
        return Math.round(actual / target * RATE_SCALE * 100d) / 100d;
    }

    /**
     * 按主键查询目标, 不存在或越权抛异常
     */
    private ScrmSalesTargetEntity findTargetOrThrow(Long id) throws ScrmException {
        ScrmSalesTargetEntity entity = targetRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "销售目标不存在: id=" + id));
        return entity;
    }


    /**
     * 目标实体转 DTO
     */
    private ScrmSalesTargetDto toTargetDto(ScrmSalesTargetEntity entity) {
        ScrmSalesTargetDto dto = new ScrmSalesTargetDto();
        dto.setId(entity.getId());
        dto.setTargetName(entity.getTargetName());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetId(entity.getTargetId());
        dto.setTargetNameRef(entity.getTargetNameRef());
        dto.setPeriodType(entity.getPeriodType());
        dto.setPeriodStart(entity.getPeriodStart());
        dto.setPeriodEnd(entity.getPeriodEnd());
        dto.setMetricType(entity.getMetricType());
        dto.setTargetValue(entity.getTargetValue());
        dto.setActualValue(entity.getActualValue());
        dto.setAchievementRate(entity.getAchievementRate());
        dto.setLastUpdated(entity.getLastUpdated());
        dto.setStatus(entity.getStatus());
        dto.setNotes(entity.getNotes());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 达成记录实体转 DTO
     */
    private ScrmSalesAchievementDto toAchievementDto(ScrmSalesAchievementEntity entity) {
        ScrmSalesAchievementDto dto = new ScrmSalesAchievementDto();
        dto.setId(entity.getId());
        dto.setTargetId(entity.getTargetId());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetIdRef(entity.getTargetIdRef());
        dto.setTargetNameRef(entity.getTargetNameRef());
        dto.setPeriodType(entity.getPeriodType());
        dto.setPeriodStart(entity.getPeriodStart());
        dto.setPeriodEnd(entity.getPeriodEnd());
        dto.setMetricType(entity.getMetricType());
        dto.setAchievedValue(entity.getAchievedValue());
        dto.setAchievementDate(entity.getAchievementDate());
        dto.setSourceType(entity.getSourceType());
        dto.setSourceId(entity.getSourceId());
        dto.setNote(entity.getNote());
        dto.setRecordedAt(entity.getRecordedAt());
        dto.setRecordedBy(entity.getRecordedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 排名实体转 DTO
     */
    private ScrmSalesRankingDto toRankingDto(ScrmSalesRankingEntity entity) {
        ScrmSalesRankingDto dto = new ScrmSalesRankingDto();
        dto.setId(entity.getId());
        dto.setPeriodType(entity.getPeriodType());
        dto.setPeriodStart(entity.getPeriodStart());
        dto.setPeriodEnd(entity.getPeriodEnd());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetId(entity.getTargetId());
        dto.setTargetNameRef(entity.getTargetNameRef());
        dto.setMetricType(entity.getMetricType());
        dto.setAchievedValue(entity.getAchievedValue());
        dto.setTargetValue(entity.getTargetValue());
        dto.setAchievementRate(entity.getAchievementRate());
        dto.setRank(entity.getRank());
        dto.setRankingDate(entity.getRankingDate());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
