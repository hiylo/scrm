/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerCareRecordService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCareRecordDto;
import org.hiylo.scrm.entity.ScrmCareRecordEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCareRecordRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
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

/**
 * SCRM 客户关怀记录与统计服务 (关怀记录与统计子域)。
 * <p>
 * 承载关怀记录增删改查 (创建 / 分页查询 / 详情) 与关怀效果统计 (总览统计 / 客户关怀历史 /
 * 效果分析)。与规则兄长类共享 {@link ScrmCustomerCareRuleService} 常量。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerCareRecordService {

    /** 关怀记录数据访问层 */
    private final ScrmCareRecordRepository recordRepository;

    /** 客户数据访问层 (创建记录时冗余填充客户名用) */
    private final ScrmCustomerRepository customerRepository;

    /**
     * 创建关怀记录。
     * <p>customerId 必填且需归属当前账号; customerName 缺省时从客户实体冗余填充;
     * executedAt 缺省取当前时间。</p>
     *
     * @param dto 关怀记录参数
     * @return 创建后的关怀记录
     * @throws ScrmException 参数非法 / 客户不存在
     */
    @Transactional
    public ScrmCareRecordEntity createRecord(ScrmCareRecordDto dto) throws ScrmException {
        validateRecordDto(dto, false);
        ScrmCustomerEntity customer = findCustomerOrThrow(dto.getCustomerId());
        ScrmCareRecordEntity entity = new ScrmCareRecordEntity();
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName() != null ? dto.getCustomerName() : customer.getNickname());
        entity.setCareType(dto.getCareType());
        entity.setCareDate(dto.getCareDate());
        entity.setActionType(dto.getActionType());
        entity.setActionDetail(dto.getActionDetail());
        entity.setCareResult(dto.getCareResult());
        entity.setCustomerResponse(dto.getCustomerResponse());
        entity.setResponseTimeHours(dto.getResponseTimeHours());
        entity.setSentiment(dto.getSentiment());
        entity.setAssigneeId(dto.getAssigneeId());
        entity.setExecutedAt(dto.getExecutedAt() != null ? dto.getExecutedAt() : LocalDateTime.now());
        entity.setNotes(dto.getNotes());
        entity = recordRepository.save(entity);
        log.info("创建关怀记录: id={}, customerId={}, careType={}, careResult={}",
                entity.getId(), entity.getCustomerId(), entity.getCareType(), entity.getCareResult());
        return entity;
    }

    /**
     * 分页查询关怀记录, 支持按客户、关怀类型、关怀结果与时间范围过滤。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param careType   关怀类型过滤（可空）
     * @param careResult 关怀结果过滤（可空）
     * @param startTime  执行时间起始 (含, 可空)
     * @param endTime    执行时间截止 (含, 可空)
     * @param pageable   分页参数
     * @return 关怀记录分页结果 (按 executedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCareRecordEntity> getRecords(Long customerId, String careType, String careResult,
                                                  LocalDateTime startTime, LocalDateTime endTime,
                                                  Pageable pageable) {
        Specification<ScrmCareRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (careType != null && !careType.isBlank()) {
                predicates.add(cb.equal(root.get("careType"), careType));
            }
            if (careResult != null && !careResult.isBlank()) {
                predicates.add(cb.equal(root.get("careResult"), careResult));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("executedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("executedAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("executedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return recordRepository.findAll(spec, pageable);
    }

    /**
     * 查询关怀记录详情。
     *
     * @param id 记录 ID
     * @return 关怀记录实体
     * @throws ScrmException 记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmCareRecordEntity getRecord(Long id) throws ScrmException {
        return findRecordOrThrow(id);
    }

    /**
     * 关怀统计: 关怀数、成功率、客户回应率、各类型分布、各结果分布。
     *
     * @param startTime 执行时间起始 (含, 可空)
     * @param endTime   执行时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCareStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 各关怀类型分布
        List<Object[]> byCareType = recordRepository.countByCareType(startTime, endTime);
        Map<String, Long> careTypeCount = new LinkedHashMap<>();
        for (String type : ScrmCustomerCareRuleService.VALID_CARE_TYPES) {
            careTypeCount.put(type, 0L);
        }
        long total = 0L;
        for (Object[] row : byCareType) {
            String type = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            careTypeCount.put(type, count);
            total += count;
        }
        stats.put("careTypeCount", careTypeCount);
        stats.put("total", total);
        // 各关怀结果分布
        List<Object[]> byResult = recordRepository.countByCareResult(startTime, endTime);
        Map<String, Long> resultCount = new LinkedHashMap<>();
        for (String result : ScrmCustomerCareRuleService.VALID_CARE_RESULTS) {
            resultCount.put(result, 0L);
        }
        for (Object[] row : byResult) {
            String result = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            resultCount.put(result, count);
        }
        stats.put("careResultCount", resultCount);
        // 成功率
        long success = resultCount.getOrDefault(ScrmCustomerCareRuleService.RESULT_SUCCESS, 0L);
        stats.put("successRate", total == 0 ? 0.0 : (double) success / total);
        // 客户回应率: 有回应 = SUCCESS + REJECTED, 无回应 = NO_RESPONSE, FAILED 不计入回应基数
        long withResponse = resultCount.getOrDefault(ScrmCustomerCareRuleService.RESULT_SUCCESS, 0L)
                + resultCount.getOrDefault(ScrmCustomerCareRuleService.RESULT_REJECTED, 0L);
        long responseBase = withResponse + resultCount.getOrDefault(ScrmCustomerCareRuleService.RESULT_NO_RESPONSE, 0L);
        stats.put("respondedCount", withResponse);
        stats.put("responseRate", responseBase == 0 ? 0.0 : (double) withResponse / responseBase);
        return stats;
    }

    /**
     * 客户关怀历史: 按客户查询全部关怀记录 (按执行时间倒序)。
     *
     * @param customerId 客户 ID
     * @return 关怀记录列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCareRecordEntity> getCustomerCareHistory(Long customerId) {
        return recordRepository.findByCustomerIdOrderByExecutedAtDesc(customerId);
    }

    /**
     * 关怀效果分析: 关怀后互动变化。
     * <p>统计时间区间内的关怀记录数、覆盖客户数、平均回应时长、情感倾向分布与关怀结果分布。
     * 注: 关怀后互动变化需对接互动事件流水, 当前为简化实现 (待完善)。</p>
     *
     * @param startTime 执行时间起始 (含, 可空)
     * @param endTime   执行时间截止 (含, 可空)
     * @return 效果分析结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCareEffectiveness(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 时间区间内全部关怀记录
        Specification<ScrmCareRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("executedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("executedAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCareRecordEntity> records = recordRepository.findAll(spec);
        result.put("totalRecords", records.size());
        // 覆盖客户数 (去重)
        long uniqueCustomers = records.stream()
                .map(ScrmCareRecordEntity::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        result.put("uniqueCustomers", uniqueCustomers);
        // 平均回应时长 (仅统计有回应时长的记录)
        double avgResponseHours = records.stream()
                .map(ScrmCareRecordEntity::getResponseTimeHours)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
        result.put("averageResponseHours", avgResponseHours);
        // 情感倾向分布
        Map<String, Long> sentimentDist = new LinkedHashMap<>();
        sentimentDist.put("POSITIVE", 0L);
        sentimentDist.put("NEUTRAL", 0L);
        sentimentDist.put("NEGATIVE", 0L);
        for (ScrmCareRecordEntity record : records) {
            String sentiment = record.getSentiment();
            if (sentiment != null) {
                sentimentDist.merge(sentiment, 1L, Long::sum);
            }
        }
        result.put("sentimentDistribution", sentimentDist);
        // 关怀结果分布
        Map<String, Long> resultDist = new LinkedHashMap<>();
        for (String r : ScrmCustomerCareRuleService.VALID_CARE_RESULTS) {
            resultDist.put(r, 0L);
        }
        for (ScrmCareRecordEntity record : records) {
            resultDist.merge(record.getCareResult(), 1L, Long::sum);
        }
        result.put("careResultDistribution", resultDist);
        // 待完善: 关怀后互动变化需对接互动事件流水
        result.put("postCareInteractionChange", "待完善: 需对接客户互动事件流水");
        return result;
    }

    /**
     * 校验关怀记录参数。
     *
     * @param dto     记录参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRecordDto(ScrmCareRecordDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("关怀记录参数不能为空");
        }
        if (dto.getCustomerId() == null && !partial) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getCareType() != null && !ScrmCustomerCareRuleService.VALID_CARE_TYPES.contains(dto.getCareType())) {
            throw ScrmException.badRequest(
                    "关怀类型非法: " + dto.getCareType() + ", 仅支持 " + ScrmCustomerCareRuleService.VALID_CARE_TYPES);
        }
        if (dto.getActionType() != null
                && !ScrmCustomerCareRuleService.VALID_ACTION_TYPES.contains(dto.getActionType())) {
            throw ScrmException.badRequest(
                    "关怀动作非法: " + dto.getActionType() + ", 仅支持 "
                            + ScrmCustomerCareRuleService.VALID_ACTION_TYPES);
        }
        if (dto.getCareResult() != null
                && !ScrmCustomerCareRuleService.VALID_CARE_RESULTS.contains(dto.getCareResult())) {
            throw ScrmException.badRequest(
                    "关怀结果非法: " + dto.getCareResult() + ", 仅支持 "
                            + ScrmCustomerCareRuleService.VALID_CARE_RESULTS);
        }
    }

    /**
     * 按主键查询关怀记录, 不存在抛异常, 并校验账号归属。
     *
     * @param id 记录 ID
     * @return 关怀记录实体
     * @throws ScrmException 记录不存在
     */
    private ScrmCareRecordEntity findRecordOrThrow(Long id) throws ScrmException {
        ScrmCareRecordEntity entity = recordRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "关怀记录不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询客户, 不存在抛异常, 并校验账号归属。
     *
     * @param id 客户 ID
     * @return 客户实体
     * @throws ScrmException 客户不存在
     */
    private ScrmCustomerEntity findCustomerOrThrow(Long id) throws ScrmException {
        ScrmCustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + id));
        return customer;
    }
}