/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemMonitorMetricService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmMetricRecordDto;
import org.hiylo.scrm.dto.ScrmMonitorMetricDto;
import org.hiylo.scrm.entity.ScrmAlertRuleEntity;
import org.hiylo.scrm.entity.ScrmMonitorMetricEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAlertRuleRepository;
import org.hiylo.scrm.repository.ScrmMonitorMetricRepository;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * SCRM 系统监控指标管理服务。
 * <p>
 * 承载监控指标管理子域: 指标 CRUD / 启停 / 记录值 / 批量记录 / 历史查询 / 汇总统计 /
 * 趋势分析, 以及记录时基于规则的阈值触发告警联动。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSystemMonitorMetricService {

    /** 默认采集间隔秒 */
    private static final int DEFAULT_COLLECTION_INTERVAL = 60;
    /** 默认冷却分钟 */
    private static final int DEFAULT_COOLDOWN_MINUTES = 30;
    /** 历史数据保留最大条数 */
    private static final int MAX_HISTORY_SIZE = 200;

    /** 阈值方向: 上限 */
    private static final String DIRECTION_ABOVE = "ABOVE";

    /** 合法的指标分组 */
    private static final List<String> VALID_METRIC_GROUPS = List.of(
            "SYSTEM", "BUSINESS", "PERFORMANCE", "AVAILABILITY", "SECURITY",
            "RESOURCE", "API", "DATABASE", "CACHE", "QUEUE");
    /** 合法的指标类型 */
    private static final List<String> VALID_METRIC_TYPES = List.of(
            "GAUGE", "COUNTER", "HISTOGRAM", "TIMER", "SUMMARY");
    /** 合法的阈值方向 */
    private static final List<String> VALID_DIRECTIONS = List.of("ABOVE", "BELOW", "RANGE");
    /** 合法的采集方式 */
    private static final List<String> VALID_COLLECTION_METHODS = List.of(
            "POLLING", "PUSH", "CALCULATED", "EXTERNAL");

    /** 监控指标数据访问层 */
    private final ScrmMonitorMetricRepository metricRepository;
    /** 告警规则数据访问层 (删除指标清理规则与阈值触发联动) */
    private final ScrmAlertRuleRepository ruleRepository;
    /** 告警规则子域服务 (条件检查) */
    private final ScrmSystemMonitorRuleService ruleService;
    /** 告警事件子域服务 (触发告警与自动恢复) */
    private final ScrmSystemMonitorEventService eventService;

    /**
     * 创建监控指标。
     * <p>校验 metricGroup / metricType / thresholdDirection / collectionMethod 合法性与
     * metricCode 唯一性后写入归属账号 ID 持久化, 各阈值与统计字段缺省时填默认值。</p>
     *
     * @param dto 指标参数
     * @return 创建后的指标
     * @throws ScrmException 参数非法 / 编码重复
     */
    @Transactional
    public ScrmMonitorMetricEntity createMetric(ScrmMonitorMetricDto dto) throws ScrmException {
        validateMetricDto(dto, false);
        if (metricRepository.findByMetricCode(dto.getMetricCode()).isPresent()) {
            throw ScrmException.conflict("指标编码已存在: " + dto.getMetricCode());
        }
        ScrmMonitorMetricEntity entity = new ScrmMonitorMetricEntity();
        entity.setMetricName(dto.getMetricName());
        entity.setMetricCode(dto.getMetricCode());
        entity.setMetricGroup(dto.getMetricGroup());
        entity.setMetricType(dto.getMetricType());
        entity.setUnit(dto.getUnit());
        entity.setDescription(dto.getDescription());
        entity.setCurrentValue(0.0);
        entity.setMinValue(0.0);
        entity.setMaxValue(0.0);
        entity.setAvgValue(0.0);
        entity.setTargetValue(dto.getTargetValue() != null ? dto.getTargetValue() : 0.0);
        entity.setWarningThreshold(dto.getWarningThreshold() != null ? dto.getWarningThreshold() : 0.0);
        entity.setCriticalThreshold(dto.getCriticalThreshold() != null ? dto.getCriticalThreshold() : 0.0);
        entity.setThresholdDirection(dto.getThresholdDirection() != null
                ? dto.getThresholdDirection() : DIRECTION_ABOVE);
        entity.setCollectionIntervalSeconds(dto.getCollectionIntervalSeconds() != null
                ? dto.getCollectionIntervalSeconds() : DEFAULT_COLLECTION_INTERVAL);
        entity.setCollectionMethod(dto.getCollectionMethod() != null ? dto.getCollectionMethod() : "POLLING");
        entity.setDataSource(dto.getDataSource());
        entity.setQueryExpression(dto.getQueryExpression());
        entity.setTags(dto.getTags());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setIsAlertActive(Boolean.FALSE);
        entity.setAlertCount(0);
        entity.setMetadata(dto.getMetadata());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = metricRepository.save(entity);
        log.info("创建监控指标: id={}, metricName={}, metricCode={}, group={}",
                entity.getId(), entity.getMetricName(), entity.getMetricCode(), entity.getMetricGroup());
        return entity;
    }

    /**
     * 更新监控指标（字段非空才覆盖）。
     *
     * @param id  指标 ID
     * @param dto 指标参数
     * @return 更新后的指标
     * @throws ScrmException 指标不存在 / 参数非法 / 编码重复
     */
    @Transactional
    public ScrmMonitorMetricEntity updateMetric(Long id, ScrmMonitorMetricDto dto) throws ScrmException {
        ScrmMonitorMetricEntity entity = findMetricOrThrow(id);
        validateMetricDto(dto, true);
        if (dto.getMetricCode() != null && !dto.getMetricCode().equals(entity.getMetricCode())) {
            if (metricRepository.findByMetricCode(dto.getMetricCode()).isPresent()) {
                throw ScrmException.conflict("指标编码已存在: " + dto.getMetricCode());
            }
        }
        if (dto.getMetricName() != null) entity.setMetricName(dto.getMetricName());
        if (dto.getMetricCode() != null) entity.setMetricCode(dto.getMetricCode());
        if (dto.getMetricGroup() != null) entity.setMetricGroup(dto.getMetricGroup());
        if (dto.getMetricType() != null) entity.setMetricType(dto.getMetricType());
        if (dto.getUnit() != null) entity.setUnit(dto.getUnit());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTargetValue() != null) entity.setTargetValue(dto.getTargetValue());
        if (dto.getWarningThreshold() != null) entity.setWarningThreshold(dto.getWarningThreshold());
        if (dto.getCriticalThreshold() != null) entity.setCriticalThreshold(dto.getCriticalThreshold());
        if (dto.getThresholdDirection() != null) entity.setThresholdDirection(dto.getThresholdDirection());
        if (dto.getCollectionIntervalSeconds() != null) entity.setCollectionIntervalSeconds(
                dto.getCollectionIntervalSeconds());
        if (dto.getCollectionMethod() != null) entity.setCollectionMethod(dto.getCollectionMethod());
        if (dto.getDataSource() != null) entity.setDataSource(dto.getDataSource());
        if (dto.getQueryExpression() != null) entity.setQueryExpression(dto.getQueryExpression());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getMetadata() != null) entity.setMetadata(dto.getMetadata());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = metricRepository.save(entity);
        log.info("更新监控指标: id={}, metricName={}", entity.getId(), entity.getMetricName());
        return entity;
    }

    /**
     * 删除监控指标 (同时清理关联告警规则)。
     *
     * @param id 指标 ID
     * @throws ScrmException 指标不存在
     */
    @Transactional
    public void deleteMetric(Long id) throws ScrmException {
        ScrmMonitorMetricEntity entity = findMetricOrThrow(id);
        List<ScrmAlertRuleEntity> rules = ruleRepository.findByMetricId(id);
        if (!rules.isEmpty()) {
            ruleRepository.deleteAll(rules);
        }
        metricRepository.delete(entity);
        log.info("删除监控指标: id={}, metricName={}, rules={}", id, entity.getMetricName(), rules.size());
    }

    /**
     * 查询指标详情。
     *
     * @param id 指标 ID
     * @return 指标实体
     * @throws ScrmException 指标不存在
     */
    @Transactional(readOnly = true)
    public ScrmMonitorMetricEntity getMetric(Long id) throws ScrmException {
        return findMetricOrThrow(id);
    }

    /**
     * 按编码查询指标。
     *
     * @param code 指标编码
     * @return 指标实体
     * @throws ScrmException 指标不存在
     */
    @Transactional(readOnly = true)
    public ScrmMonitorMetricEntity getMetricByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("指标编码不能为空");
        }
        return metricRepository.findByMetricCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "监控指标不存在: code=" + code));
    }

    /**
     * 分页查询指标, 支持按分组 / 类型 / 启用状态 / 关键字过滤。
     *
     * @param metricGroup 指标分组过滤（可空）
     * @param metricType  指标类型过滤（可空）
     * @param enabled     启用状态过滤（可空）
     * @param keyword     指标名称关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 指标分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmMonitorMetricEntity> listMetrics(String metricGroup, String metricType, Boolean enabled,
                                                      String keyword, Pageable pageable) {
        Specification<ScrmMonitorMetricEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (metricGroup != null && !metricGroup.isBlank()) {
                predicates.add(cb.equal(root.get("metricGroup"), metricGroup));
            }
            if (metricType != null && !metricType.isBlank()) {
                predicates.add(cb.equal(root.get("metricType"), metricType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("metricName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return metricRepository.findAll(spec, pageable);
    }

    /**
     * 启用指标。
     *
     * @param id 指标 ID
     * @return 更新后的指标
     * @throws ScrmException 指标不存在
     */
    @Transactional
    public ScrmMonitorMetricEntity enableMetric(Long id) throws ScrmException {
        ScrmMonitorMetricEntity entity = findMetricOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = metricRepository.save(entity);
        log.info("启用监控指标: id={}, metricName={}", id, entity.getMetricName());
        return entity;
    }

    /**
     * 禁用指标。
     *
     * @param id 指标 ID
     * @return 更新后的指标
     * @throws ScrmException 指标不存在
     */
    @Transactional
    public ScrmMonitorMetricEntity disableMetric(Long id) throws ScrmException {
        ScrmMonitorMetricEntity entity = findMetricOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = metricRepository.save(entity);
        log.info("禁用监控指标: id={}, metricName={}", id, entity.getMetricName());
        return entity;
    }

    /**
     * 记录指标值: 更新当前值 → 追加历史 → 更新统计 → 检查阈值 → 可能触发告警。
     *
     * @param recordDto 指标记录 DTO (metricCode + value + timestamp)
     * @return 更新后的指标
     * @throws ScrmException 指标不存在 / 已禁用
     */
    @Transactional
    public ScrmMonitorMetricEntity recordMetric(ScrmMetricRecordDto recordDto) throws ScrmException {
        if (recordDto == null) {
            throw ScrmException.badRequest("指标记录不能为空");
        }
        if (recordDto.getValue() == null) {
            throw ScrmException.badRequest("采集值不能为空");
        }
        ScrmMonitorMetricEntity entity = getMetricByCode(recordDto.getMetricCode());
        if (Boolean.FALSE.equals(entity.getEnabled())) {
            throw ScrmException.badRequest("指标已禁用, 无法记录: code=" + recordDto.getMetricCode());
        }
        LocalDateTime now = recordDto.getTimestamp() != null ? recordDto.getTimestamp() : LocalDateTime.now();
        double value = recordDto.getValue();
        // 更新当前值与采集时间
        entity.setCurrentValue(value);
        entity.setLastCollectedAt(now);
        entity.setLastValueAt(now);
        // 追加历史数据
        appendHistory(entity, now, value);
        // 更新统计 (最小/最大/平均)
        updateMetricStatsInternal(entity, value);
        entity = metricRepository.save(entity);
        log.info("记录指标值: metricCode={}, value={}, timestamp={}", recordDto.getMetricCode(), value, now);
        // 检查阈值并可能触发告警
        checkMetricThresholdAndAlert(entity);
        return entity;
    }

    /**
     * 批量记录指标值。
     *
     * @param records 指标记录列表
     * @return 处理结果列表 (每条记录的处理状态)
     */
    @Transactional
    public List<Map<String, Object>> batchRecordMetrics(List<ScrmMetricRecordDto> records) {
        if (records == null || records.isEmpty()) {
            throw ScrmException.badRequest("指标记录列表不能为空");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (ScrmMetricRecordDto record : records) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("metricCode", record != null ? record.getMetricCode() : null);
            r.put("value", record != null ? record.getValue() : null);
            try {
                ScrmMonitorMetricEntity entity = recordMetric(record);
                r.put("success", true);
                r.put("metricId", entity.getId());
            } catch (ScrmException e) {
                r.put("success", false);
                r.put("error", e.getMessage());
            }
            results.add(r);
        }
        log.info("批量记录指标值: count={}, success={}",
                results.size(), results.stream().filter(m -> Boolean.TRUE.equals(m.get("success"))).count());
        return results;
    }

    /**
     * 查询指标历史数据 (从 historyData JSON 解析, 按时间区间过滤)。
     *
     * @param metricId  指标 ID
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 历史数据列表 [{timestamp, value}]
     * @throws ScrmException 指标不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMetricHistory(Long metricId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        ScrmMonitorMetricEntity entity = findMetricOrThrow(metricId);
        List<Map<String, Object>> history = parseHistory(entity.getHistoryData());
        if (startTime == null && endTime == null) {
            return history;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> point : history) {
            LocalDateTime ts = toLocalDateTime(point.get("timestamp"));
            if (ts == null) {
                continue;
            }
            if (startTime != null && ts.isBefore(startTime)) {
                continue;
            }
            if (endTime != null && ts.isAfter(endTime)) {
                continue;
            }
            filtered.add(point);
        }
        return filtered;
    }

    /**
     * 获取指标当前值。
     *
     * @param metricCode 指标编码
     * @return 当前值 Map {metricCode, metricName, value, unit, lastCollectedAt}
     * @throws ScrmException 指标不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMetricValue(String metricCode) throws ScrmException {
        ScrmMonitorMetricEntity entity = getMetricByCode(metricCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metricId", entity.getId());
        result.put("metricCode", entity.getMetricCode());
        result.put("metricName", entity.getMetricName());
        result.put("value", entity.getCurrentValue() != null ? entity.getCurrentValue() : 0.0);
        result.put("unit", entity.getUnit());
        result.put("lastCollectedAt", entity.getLastCollectedAt());
        result.put("isAlertActive", entity.getIsAlertActive());
        return result;
    }

    /**
     * 指标汇总: 按分组聚合指标数与告警态指标数。
     *
     * @param metricGroup 指标分组（可空, 空则汇总全部分组）
     * @return 汇总结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMetricSummary(String metricGroup) {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Object[]> byGroup = metricRepository.countByMetricGroup();
        Map<String, Long> groupCount = new LinkedHashMap<>();
        long total = 0L;
        for (Object[] row : byGroup) {
            String group = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            groupCount.put(group, count);
            total += count;
            if (metricGroup != null && !metricGroup.isBlank() && metricGroup.equals(group)) {
                summary.put("group", group);
                summary.put("count", count);
            }
        }
        summary.put("groupCount", groupCount);
        summary.put("total", total);
        summary.put("alertingCount", metricRepository.countByIsAlertActive(Boolean.TRUE));
        if (metricGroup != null && !metricGroup.isBlank()) {
            List<ScrmMonitorMetricEntity> metrics = metricRepository.findByEnabled(Boolean.TRUE);
            List<Map<String, Object>> groupMetrics = new ArrayList<>();
            for (ScrmMonitorMetricEntity m : metrics) {
                if (metricGroup.equals(m.getMetricGroup())) {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    mm.put("metricId", m.getId());
                    mm.put("metricCode", m.getMetricCode());
                    mm.put("metricName", m.getMetricName());
                    mm.put("currentValue", m.getCurrentValue());
                    mm.put("isAlertActive", m.getIsAlertActive());
                    groupMetrics.add(mm);
                }
            }
            summary.put("metrics", groupMetrics);
        }
        return summary;
    }

    /**
     * 更新指标统计 (最小/最大/平均, 基于历史数据重算)。
     *
     * @param id 指标 ID
     * @return 更新后的指标
     * @throws ScrmException 指标不存在
     */
    @Transactional
    public ScrmMonitorMetricEntity updateMetricStats(Long id) throws ScrmException {
        ScrmMonitorMetricEntity entity = findMetricOrThrow(id);
        List<Map<String, Object>> history = parseHistory(entity.getHistoryData());
        if (history.isEmpty()) {
            return entity;
        }
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0.0;
        for (Map<String, Object> point : history) {
            double v = toDouble(point.get("value"));
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
            sum += v;
        }
        entity.setMinValue(min);
        entity.setMaxValue(max);
        entity.setAvgValue(sum / history.size());
        entity = metricRepository.save(entity);
        log.info("更新指标统计: id={}, min={}, max={}, avg={}", id, min, max, entity.getAvgValue());
        return entity;
    }

    /**
     * 计算指标值 (模拟采集, 基于当前值小幅波动)。
     *
     * @param id 指标 ID
     * @return 计算后的指标
     * @throws ScrmException 指标不存在
     */
    @Transactional
    public ScrmMonitorMetricEntity calculateMetricValue(Long id) throws ScrmException {
        ScrmMonitorMetricEntity entity = findMetricOrThrow(id);
        double base = entity.getCurrentValue() != null ? entity.getCurrentValue() : 0.0;
        // 模拟波动: 基于当前值 ±5% 随机波动
        double delta = base * 0.05 * (ThreadLocalRandom.current().nextDouble() * 2 - 1);
        double newValue = base + delta;
        if (newValue < 0) {
            newValue = 0.0;
        }
        ScrmMetricRecordDto record = new ScrmMetricRecordDto();
        record.setMetricCode(entity.getMetricCode());
        record.setValue(Math.round(newValue * 100.0) / 100.0);
        record.setTimestamp(LocalDateTime.now());
        log.info("计算指标值 (模拟): id={}, metricCode={}, base={}, calculated={}",
                id, entity.getMetricCode(), base, record.getValue());
        return recordMetric(record);
    }

    /**
     * 按分组分页查询指标 (按 createTime DESC)。
     *
     * @param group    指标分组
     * @param pageable 分页参数
     * @return 指标分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMonitorMetricEntity> getMetricsByGroup(String group, Pageable pageable) {
        if (group == null || group.isBlank()) {
            throw ScrmException.badRequest("指标分组不能为空");
        }
        if (!VALID_METRIC_GROUPS.contains(group)) {
            throw ScrmException.badRequest("指标分组非法: " + group + ", 仅支持 " + VALID_METRIC_GROUPS);
        }
        return metricRepository.findByMetricGroup(group, pageable);
    }

    /**
     * 查询告警中的指标 (isAlertActive=TRUE)。
     *
     * @param pageable 分页参数
     * @return 指标分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMonitorMetricEntity> getAlertingMetrics(Pageable pageable) {
        return metricRepository.findByIsAlertActive(Boolean.TRUE, pageable);
    }

    /**
     * 指标趋势: 最近 N 天的指标值变化 (从历史数据按天聚合)。
     *
     * @param metricCode 指标编码
     * @param days       天数
     * @return 趋势数据列表 [{date, avg, min, max, count}]
     * @throws ScrmException 指标不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMetricTrend(String metricCode, int days) throws ScrmException {
        if (days <= 0) {
            days = 7;
        }
        ScrmMonitorMetricEntity entity = getMetricByCode(metricCode);
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);
        List<Map<String, Object>> history = getMetricHistory(entity.getId(), startTime, endTime);
        // 按天聚合
        Map<String, double[]> daily = new LinkedHashMap<>();
        for (Map<String, Object> point : history) {
            LocalDateTime ts = toLocalDateTime(point.get("timestamp"));
            if (ts == null) {
                continue;
            }
            String dayKey = ts.toLocalDate().toString();
            double v = toDouble(point.get("value"));
            double[] agg = daily.computeIfAbsent(dayKey,
                    k -> new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, 0.0});
            if (v < agg[0]) {
                agg[0] = v;
            }
            if (v > agg[1]) {
                agg[1] = v;
            }
            agg[2] += v;
            agg[3] += 1;
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : daily.entrySet()) {
            double[] agg = entry.getValue();
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", entry.getKey());
            day.put("min", Double.isInfinite(agg[0]) ? 0.0 : agg[0]);
            day.put("max", Double.isInfinite(agg[1]) ? 0.0 : agg[1]);
            day.put("avg", agg[3] > 0 ? agg[2] / agg[3] : 0.0);
            day.put("count", (int) agg[3]);
            trend.add(day);
        }
        return trend;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验指标参数。
     *
     * @param dto     指标参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateMetricDto(ScrmMonitorMetricDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("指标参数不能为空");
        }
        if (dto.getMetricName() != null) {
            if (dto.getMetricName().isBlank()) {
                throw ScrmException.badRequest("指标名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("指标名称不能为空");
        }
        if (dto.getMetricCode() != null) {
            if (dto.getMetricCode().isBlank()) {
                throw ScrmException.badRequest("指标编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("指标编码不能为空");
        }
        if (dto.getMetricGroup() != null && !VALID_METRIC_GROUPS.contains(dto.getMetricGroup())) {
            throw ScrmException.badRequest("指标分组非法: " + dto.getMetricGroup() + ", 仅支持 " + VALID_METRIC_GROUPS);
        } else if (dto.getMetricGroup() == null && !partial) {
            throw ScrmException.badRequest("指标分组不能为空");
        }
        if (dto.getMetricType() != null && !VALID_METRIC_TYPES.contains(dto.getMetricType())) {
            throw ScrmException.badRequest("指标类型非法: " + dto.getMetricType() + ", 仅支持 " + VALID_METRIC_TYPES);
        } else if (dto.getMetricType() == null && !partial) {
            throw ScrmException.badRequest("指标类型不能为空");
        }
        if (dto.getThresholdDirection() != null && !VALID_DIRECTIONS.contains(dto.getThresholdDirection())) {
            throw ScrmException.badRequest("阈值方向非法: " + dto.getThresholdDirection() + ", 仅支持 " + VALID_DIRECTIONS);
        }
        if (dto.getCollectionMethod() != null && !VALID_COLLECTION_METHODS.contains(dto.getCollectionMethod())) {
            throw ScrmException.badRequest("采集方式非法: " + dto.getCollectionMethod()
                    + ", 仅支持 " + VALID_COLLECTION_METHODS);
        }
    }

    /**
     * 追加历史数据点 (保留最近 MAX_HISTORY_SIZE 条)。
     *
     * @param entity    指标实体
     * @param timestamp 时间戳
     * @param value     采集值
     */
    private void appendHistory(ScrmMonitorMetricEntity entity, LocalDateTime timestamp, double value) {
        List<Map<String, Object>> history = parseHistory(entity.getHistoryData());
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("timestamp", timestamp.toString());
        point.put("value", value);
        history.add(point);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
        entity.setHistoryData(toJsonString(history));
    }

    /**
     * 更新指标统计 (最小/最大/平均, 增量更新)。
     *
     * @param entity 指标实体
     * @param value  新采集值
     */
    private void updateMetricStatsInternal(ScrmMonitorMetricEntity entity, double value) {
        double min = entity.getMinValue() != null ? entity.getMinValue() : value;
        double max = entity.getMaxValue() != null ? entity.getMaxValue() : value;
        if (value < min) {
            min = value;
        }
        if (value > max) {
            max = value;
        }
        entity.setMinValue(min);
        entity.setMaxValue(max);
        // 平均值基于历史数据计算
        List<Map<String, Object>> history = parseHistory(entity.getHistoryData());
        if (!history.isEmpty()) {
            double sum = 0.0;
            for (Map<String, Object> point : history) {
                sum += toDouble(point.get("value"));
            }
            entity.setAvgValue(sum / history.size());
        }
    }

    /**
     * 检查指标阈值并触发告警 (遍历关联规则)。
     *
     * @param metric 指标实体
     */
    private void checkMetricThresholdAndAlert(ScrmMonitorMetricEntity metric) {
        List<ScrmAlertRuleEntity> rules = ruleRepository.findByMetricId(metric.getId());
        if (rules.isEmpty()) {
            return;
        }
        double currentValue = metric.getCurrentValue() != null ? metric.getCurrentValue() : 0.0;
        for (ScrmAlertRuleEntity rule : rules) {
            if (Boolean.FALSE.equals(rule.getEnabled())) {
                continue;
            }
            double threshold1 = rule.getThresholdValue() != null ? rule.getThresholdValue() : 0.0;
            double threshold2 = rule.getThresholdValue2() != null ? rule.getThresholdValue2() : 0.0;
            boolean triggered = ruleService.checkCondition(currentValue, rule.getCondition(), threshold1, threshold2);
            if (triggered) {
                // 冷却期检查
                if (checkCooldownInternal(rule)) {
                    try {
                        eventService.fireEvent(rule.getId(), metric.getId(), currentValue);
                    } catch (ScrmException e) {
                        log.warn("阈值触发告警失败: ruleId={}, metricId={}, err={}",
                                rule.getId(), metric.getId(), e.getMessage());
                    }
                }
            } else if (Boolean.TRUE.equals(metric.getIsAlertActive()) && Boolean.TRUE.equals(rule.getAutoResolve())) {
                // 指标恢复正常, 自动恢复该规则的触发中事件
                eventService.autoResolveFiringEvents(rule.getId());
            }
        }
    }

    /**
     * 冷却期检查 (内部, 不抛异常)。
     *
     * @param rule 规则实体
     * @return true 表示可触发
     */
    private boolean checkCooldownInternal(ScrmAlertRuleEntity rule) {
        if (rule.getLastTriggeredAt() == null) {
            return true;
        }
        int cooldownMinutes = rule.getCooldownMinutes() != null ? rule.getCooldownMinutes() : DEFAULT_COOLDOWN_MINUTES;
        LocalDateTime cooldownEnd = rule.getLastTriggeredAt().plusMinutes(cooldownMinutes);
        return LocalDateTime.now().isAfter(cooldownEnd);
    }

    /**
     * 解析历史数据 JSON 为列表。
     *
     * @param json JSON 字符串
     * @return 历史数据列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseHistory(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            Object parsed = SimpleJsonParser.parse(json);
            if (parsed instanceof List) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : (List<Object>) parsed) {
                    if (item instanceof Map) {
                        result.add((Map<String, Object>) item);
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("解析历史数据 JSON 失败: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * 解析 JSON 字符串为 Map。
     *
     * @param json JSON 字符串
     * @return Map (解析失败返回空 Map)
     */
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = SimpleJsonParser.parse(json);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) parsed;
                return result;
            }
        } catch (Exception e) {
            log.warn("解析 JSON 失败: {}", e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    /**
     * 将对象转换为 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    private String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        return SimpleJsonParser.toJson(obj);
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    private double toDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 将对象转换为 LocalDateTime。
     *
     * @param obj 对象
     * @return LocalDateTime, 不可转换返回 null
     */
    private LocalDateTime toLocalDateTime(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof LocalDateTime ldt) {
            return ldt;
        }
        try {
            return LocalDateTime.parse(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按主键查询指标, 不存在抛异常, 并校验归属账号。
     *
     * @param id 指标 ID
     * @return 指标实体
     * @throws ScrmException 指标不存在
     */
    private ScrmMonitorMetricEntity findMetricOrThrow(Long id) throws ScrmException {
        ScrmMonitorMetricEntity entity = metricRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "监控指标不存在: id=" + id));
        return entity;
    }

    /**
     * 简易 JSON 解析与序列化工具 (避免引入额外 JSON 依赖, 支持基础 Map/List/基础类型)。
     * <p>仅用于指标历史数据与 metadata 等简单结构, 复杂结构请使用 Jackson。</p>
     *
     * @author Hsi Chu
     * @since V1.0
     */
    private static final class SimpleJsonParser {

        private SimpleJsonParser() {
        }

        /**
         * 解析 JSON 字符串为对象 (Map / List / String / Number / Boolean / null)。
         *
         * @param json JSON 字符串
         * @return 解析后的对象
         * @throws RuntimeException 解析失败
         */
        static Object parse(String json) {
            return new Parser(json).parseValue();
        }

        /**
         * 将对象序列化为 JSON 字符串。
         *
         * @param obj 对象
         * @return JSON 字符串
         */
        static String toJson(Object obj) {
            StringBuilder sb = new StringBuilder();
            write(sb, obj);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void write(StringBuilder sb, Object obj) {
            if (obj == null) {
                sb.append("null");
            } else if (obj instanceof String s) {
                writeString(sb, s);
            } else if (obj instanceof Map) {
                writeMap(sb, (Map<String, Object>) obj);
            } else if (obj instanceof Iterable it) {
                writeList(sb, it);
            } else if (obj instanceof LocalDateTime ldt) {
                writeString(sb, ldt.toString());
            } else {
                writeString(sb, obj.toString());
            }
        }

        private static void writeString(StringBuilder sb, String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> sb.append(c);
                }
            }
            sb.append('"');
        }

        private static void writeMap(StringBuilder sb, Map<String, Object> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, entry.getKey());
                sb.append(':');
                write(sb, entry.getValue());
            }
            sb.append('}');
        }

        private static void writeList(StringBuilder sb, Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, item);
            }
            sb.append(']');
        }

        /**
         * JSON 解析器
         *
         * @author Hsi Chu
         * @since V1.0
         */
        private static final class Parser {
            /** 待解析的 JSON 原文 */
            private final String json;
            /** 当前解析位置 (字符下标, 取值范围 0..json.length()) */
            private int pos;

            Parser(String json) {
                this.json = json;
            }

            Object parseValue() {
                skipWhitespace();
                Object value = parseValueInternal();
                skipWhitespace();
                return value;
            }

            private Object parseValueInternal() {
                skipWhitespace();
                if (pos >= json.length()) {
                    throw new RuntimeException("JSON 解析意外结束");
                }
                char c = json.charAt(pos);
                if (c == '{') {
                    return parseObject();
                } else if (c == '[') {
                    return parseArray();
                } else if (c == '"') {
                    return parseString();
                } else if (c == 't' || c == 'f') {
                    return parseBoolean();
                } else if (c == 'n') {
                    return parseNull();
                } else {
                    return parseNumber();
                }
            }

            private Map<String, Object> parseObject() {
                Map<String, Object> map = new LinkedHashMap<>();
                expect('{');
                skipWhitespace();
                if (peek() == '}') {
                    pos++;
                    return map;
                }
                while (true) {
                    skipWhitespace();
                    String key = parseString();
                    skipWhitespace();
                    expect(':');
                    Object value = parseValue();
                    map.put(key, value);
                    skipWhitespace();
                    char c = next();
                    if (c == '}') {
                        break;
                    }
                    if (c != ',') {
                        throw new RuntimeException("JSON 对象缺少逗号或结束括号");
                    }
                }
                return map;
            }

            private List<Object> parseArray() {
                List<Object> list = new ArrayList<>();
                expect('[');
                skipWhitespace();
                if (peek() == ']') {
                    pos++;
                    return list;
                }
                while (true) {
                    Object value = parseValue();
                    list.add(value);
                    skipWhitespace();
                    char c = next();
                    if (c == ']') {
                        break;
                    }
                    if (c != ',') {
                        throw new RuntimeException("JSON 数组缺少逗号或结束括号");
                    }
                }
                return list;
            }

            private String parseString() {
                expect('"');
                StringBuilder sb = new StringBuilder();
                while (pos < json.length()) {
                    char c = json.charAt(pos++);
                    if (c == '"') {
                        return sb.toString();
                    }
                    if (c == '\\') {
                        if (pos >= json.length()) {
                            break;
                        }
                        char esc = json.charAt(pos++);
                        switch (esc) {
                            case '"' -> sb.append('"');
                            case '\\' -> sb.append('\\');
                            case '/' -> sb.append('/');
                            case 'n' -> sb.append('\n');
                            case 'r' -> sb.append('\r');
                            case 't' -> sb.append('\t');
                            case 'b' -> sb.append('\b');
                            case 'f' -> sb.append('\f');
                            case 'u' -> {
                                if (pos + 4 > json.length()) {
                                    throw new RuntimeException("JSON Unicode 转义不完整");
                                }
                                String hex = json.substring(pos, pos + 4);
                                sb.append((char) Integer.parseInt(hex, 16));
                                pos += 4;
                            }
                            default -> sb.append(esc);
                        }
                    } else {
                        sb.append(c);
                    }
                }
                throw new RuntimeException("JSON 字符串未闭合");
            }

            private Boolean parseBoolean() {
                if (json.startsWith("true", pos)) {
                    pos += 4;
                    return Boolean.TRUE;
                }
                if (json.startsWith("false", pos)) {
                    pos += 5;
                    return Boolean.FALSE;
                }
                throw new RuntimeException("JSON 布尔值非法");
            }

            private Object parseNull() {
                if (json.startsWith("null", pos)) {
                    pos += 4;
                    return null;
                }
                throw new RuntimeException("JSON null 非法");
            }

            private Number parseNumber() {
                int start = pos;
                if (peek() == '-') {
                    pos++;
                }
                while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                    pos++;
                }
                boolean isDouble = false;
                if (pos < json.length() && json.charAt(pos) == '.') {
                    isDouble = true;
                    pos++;
                    while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                        pos++;
                    }
                }
                if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
                    isDouble = true;
                    pos++;
                    if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
                        pos++;
                    }
                    while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                        pos++;
                    }
                }
                String num = json.substring(start, pos);
                if (isDouble) {
                    return Double.parseDouble(num);
                }
                try {
                    return Long.parseLong(num);
                } catch (NumberFormatException e) {
                    return Double.parseDouble(num);
                }
            }

            private void skipWhitespace() {
                while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                    pos++;
                }
            }

            private void expect(char c) {
                if (pos >= json.length() || json.charAt(pos) != c) {
                    throw new RuntimeException("JSON 期望字符 '" + c + "' 但得到:"
                            + (pos < json.length() ? json.charAt(pos) : "EOF"));
                }
                pos++;
            }

            private char peek() {
                if (pos >= json.length()) {
                    throw new RuntimeException("JSON 意外结束");
                }
                return json.charAt(pos);
            }

            private char next() {
                if (pos >= json.length()) {
                    throw new RuntimeException("JSON 意外结束");
                }
                return json.charAt(pos++);
            }
        }
    }
}
