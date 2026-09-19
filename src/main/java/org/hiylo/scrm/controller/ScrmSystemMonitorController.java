/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemMonitorController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmAlertAcknowledgeDto;
import org.hiylo.scrm.dto.ScrmAlertRuleDto;
import org.hiylo.scrm.dto.ScrmAlertEventDto;
import org.hiylo.scrm.dto.ScrmMetricRecordDto;
import org.hiylo.scrm.dto.ScrmMonitorMetricDto;
import org.hiylo.scrm.entity.ScrmAlertEventEntity;
import org.hiylo.scrm.entity.ScrmAlertRuleEntity;
import org.hiylo.scrm.entity.ScrmMonitorMetricEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmSystemMonitorService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 系统监控告警控制器。
 * <p>
 * 提供系统监控告警模块的完整接口, 分为五大域:
 * <ul>
 *   <li>Metric: 监控指标 CRUD / 启停 / 记录值 / 批量记录 / 历史查询 / 当前值 /
 *       汇总 / 统计更新 / 计算 / 分组查询 / 告警中指标 / 趋势。</li>
 *   <li>Rule: 告警规则 CRUD / 启停 / 评估 / 批量评估 / 按指标 / 按严重度 / 触发统计。</li>
 *   <li>Event: 告警事件触发 / 查询 / 触发中 / 严重事件 / 确认 / 恢复 / 抑制 /
 *       自动恢复检查 / 升级 / 时间线 / 相关联事件 / 统计 / 批量处理 / 通知。</li>
 *   <li>Health: 系统健康度 / 组件健康度 / 健康历史 / 可用性 / 性能 / 资源 / 仪表盘。</li>
 *   <li>Stats: 监控统计 / 告警统计 / 指标概览 / Top 指标 / 告警趋势 / 严重度分布。</li>
 * </ul>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/system-monitor")
@RequiredArgsConstructor
public class ScrmSystemMonitorController {

    /** 系统监控告警服务 */
    private final ScrmSystemMonitorService scrmSystemMonitorService;

    // ============================================================
    // 监控指标管理
    // ============================================================

    /**
     * 创建监控指标。
     *
     * @param dto 指标参数
     * @return 创建后的指标
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/metrics")
    public OperationResponse<ScrmMonitorMetricEntity> createMetric(@Valid @RequestBody ScrmMonitorMetricDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.createMetric(dto));
    }

    /**
     * 更新监控指标。
     *
     * @param id  指标 ID
     * @param dto 指标参数
     * @return 更新后的指标
     * @throws ScrmException 指标不存在 / 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/metrics/{id}")
    public OperationResponse<ScrmMonitorMetricEntity> updateMetric(@PathVariable Long id,
                                                                     @RequestBody ScrmMonitorMetricDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.updateMetric(id, dto));
    }

    /**
     * 删除监控指标 (同时清理关联告警规则)。
     *
     * @param id 指标 ID
     * @return 空响应
     * @throws ScrmException 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "delete")
    @DeleteMapping("/metrics/{id}")
    public OperationResponse<Void> deleteMetric(@PathVariable Long id) throws ScrmException {
        scrmSystemMonitorService.deleteMetric(id);
        return OperationResponse.build();
    }

    /**
     * 查询指标详情。
     *
     * @param id 指标 ID
     * @return 指标详情
     * @throws ScrmException 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/metrics/{id}")
    public OperationResponse<ScrmMonitorMetricEntity> getMetric(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.getMetric(id));
    }

    /**
     * 按编码查询指标。
     *
     * @param code 指标编码
     * @return 指标详情
     * @throws ScrmException 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/metrics/code/{code}")
    public OperationResponse<ScrmMonitorMetricEntity> getMetricByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.getMetricByCode(code));
    }

    /**
     * 分页查询指标列表。
     *
     * @param metricGroup 指标分组过滤（可空）: *
       * SYSTEM/BUSINESS/PERFORMANCE/AVAILABILITY/SECURITY/RESOURCE/API/DATABASE/CACHE/QUEUE * @param metricType
       * 指标类型过滤（可空）: GAUGE/COUNTER/HISTOGRAM/TIMER/SUMMARY
     * @param enabled     启用状态过滤（可空）
     * @param keyword     指标名称关键字模糊匹配（可空）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 指标分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/metrics/list")
    public OperationResponse<Page<ScrmMonitorMetricEntity>> listMetrics(
            @RequestParam(required = false) String metricGroup,
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemMonitorService.listMetrics(
                metricGroup, metricType, enabled, keyword, pageable));
    }

    /**
     * 启用指标。
     *
     * @param id 指标 ID
     * @return 更新后的指标
     * @throws ScrmException 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "update")
    @PostMapping("/metrics/{id}/enable")
    public OperationResponse<ScrmMonitorMetricEntity> enableMetric(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.enableMetric(id));
    }

    /**
     * 禁用指标。
     *
     * @param id 指标 ID
     * @return 更新后的指标
     * @throws ScrmException 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "update")
    @PostMapping("/metrics/{id}/disable")
    public OperationResponse<ScrmMonitorMetricEntity> disableMetric(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.disableMetric(id));
    }

    /**
     * 记录指标值: 更新当前值 → 追加历史 → 更新统计 → 检查阈值 → 可能触发告警。
     *
     * @param recordDto 指标记录 DTO (metricCode + value + timestamp)
     * @return 更新后的指标
     * @throws ScrmException 指标不存在 / 已禁用
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @RateLimit(capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @PostMapping("/metrics/record")
    public OperationResponse<ScrmMonitorMetricEntity> recordMetric(@Valid @RequestBody ScrmMetricRecordDto recordDto)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.recordMetric(recordDto));
    }

    /**
     * 批量记录指标值。
     *
     * @param records 指标记录列表
     * @return 处理结果列表 (每条记录的处理状态)
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/metrics/record/batch")
    public OperationResponse<List<Map<String, Object>>> batchRecordMetrics(
            @RequestBody List<ScrmMetricRecordDto> records) {
        return OperationResponse.build(scrmSystemMonitorService.batchRecordMetrics(records));
    }

    /**
     * 查询指标历史数据 (按时间区间过滤)。
     *
     * @param id        指标 ID
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 历史数据列表 [{timestamp, value}]
     * @throws ScrmException 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/metrics/{id}/history")
    public OperationResponse<List<Map<String, Object>>> getMetricHistory(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.getMetricHistory(id, startTime, endTime));
    }

    /**
     * 获取指标当前值。
     *
     * @param code 指标编码
     * @return 当前值 Map {metricCode, metricName, value, unit, lastCollectedAt}
     * @throws ScrmException 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/metrics/value/{code}")
    public OperationResponse<Map<String, Object>> getMetricValue(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.getMetricValue(code));
    }

    /**
     * 指标汇总: 按分组聚合指标数与告警态指标数。
     *
     * @param metricGroup 指标分组（可空, 空则汇总全部分组）
     * @return 汇总结果 Map
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/metrics/summary")
    public OperationResponse<Map<String, Object>> getMetricSummary(
            @RequestParam(required = false) String metricGroup) {
        return OperationResponse.build(scrmSystemMonitorService.getMetricSummary(metricGroup));
    }

    /**
     * 更新指标统计 (最小/最大/平均, 基于历史数据重算)。
     *
     * @param id 指标 ID
     * @return 更新后的指标
     * @throws ScrmException 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @PostMapping("/metrics/{id}/refresh-stats")
    public OperationResponse<ScrmMonitorMetricEntity> updateMetricStats(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.updateMetricStats(id));
    }

    /**
     * 计算指标值 (模拟采集, 基于当前值小幅波动)。
     *
     * @param id 指标 ID
     * @return 计算后的指标
     * @throws ScrmException 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @PostMapping("/metrics/{id}/calculate")
    public OperationResponse<ScrmMonitorMetricEntity> calculateMetricValue(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.calculateMetricValue(id));
    }

    /**
     * 按分组分页查询指标。
     *
     * @param group 指标分组: SYSTEM/BUSINESS/PERFORMANCE/AVAILABILITY/SECURITY/RESOURCE/API/DATABASE/CACHE/QUEUE
     * @param page  页码（从 0 开始, 默认 0）
     * @param size  每页大小（默认 20）
     * @return 指标分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/metrics/by-group/{group}")
    public OperationResponse<Page<ScrmMonitorMetricEntity>> getMetricsByGroup(
            @PathVariable String group,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemMonitorService.getMetricsByGroup(group, pageable));
    }

    /**
     * 查询告警中的指标 (isAlertActive=TRUE)。
     *
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 指标分页结果 (按 triggerTime DESC)
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/metrics/alerting")
    public OperationResponse<Page<ScrmMonitorMetricEntity>> getAlertingMetrics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemMonitorService.getAlertingMetrics(pageable));
    }

    /**
     * 指标趋势: 最近 N 天的指标值变化。
     *
     * @param code 指标编码
     * @param days 天数 (默认 7)
     * @return 趋势数据列表 [{date, avg, min, max, count}]
     * @throws ScrmException 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/metrics/trend/{code}")
    public OperationResponse<List<Map<String, Object>>> getMetricTrend(
            @PathVariable String code,
            @RequestParam(defaultValue = "7") int days) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.getMetricTrend(code, days));
    }

    // ============================================================
    // 告警规则管理
    // ============================================================

    /**
     * 创建告警规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 编码重复 / 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmAlertRuleEntity> createRule(@Valid @RequestBody ScrmAlertRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.createRule(dto));
    }

    /**
     * 更新告警规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 编码重复 / 指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmAlertRuleEntity> updateRule(@PathVariable Long id,
                                                                @RequestBody ScrmAlertRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.updateRule(id, dto));
    }

    /**
     * 删除告警规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        scrmSystemMonitorService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmAlertRuleEntity> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.getRule(id));
    }

    /**
     * 按编码查询规则。
     *
     * @param code 规则编码
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/rules/code/{code}")
    public OperationResponse<ScrmAlertRuleEntity> getRuleByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.getRuleByCode(code));
    }

    /**
     * 分页查询规则列表。
     *
     * @param metricId 指标 ID 过滤（可空）
     * @param severity 严重程度过滤（可空）: INFO/WARNING/CRITICAL/FATAL
     * @param enabled  启用状态过滤（可空）
     * @param keyword  规则名称关键字模糊匹配（可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 规则分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmAlertRuleEntity>> listRules(
            @RequestParam(required = false) Long metricId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemMonitorService.listRules(
                metricId, severity, enabled, keyword, pageable));
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "update")
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<ScrmAlertRuleEntity> enableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.enableRule(id));
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "update")
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<ScrmAlertRuleEntity> disableRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.disableRule(id));
    }

    /**
     * 评估规则: 获取指标当前值 → 比较阈值 → 返回是否触发。
     *
     * @param id 规则 ID
     * @return 评估结果 Map {triggered, currentValue, thresholdValue, condition, severity, message}
     * @throws ScrmException 规则不存在 / 已禁用
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @PostMapping("/rules/{id}/evaluate")
    public OperationResponse<Map<String, Object>> evaluateRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.evaluateRule(id));
    }

    /**
     * 评估所有启用规则: 批量检查 → 触发告警 (冷却期内不重复触发)。
     *
     * @return 评估结果 Map {totalRules, evaluated, triggered, results}
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/rules/evaluate-all")
    public OperationResponse<Map<String, Object>> evaluateAllRules() {
        return OperationResponse.build(scrmSystemMonitorService.evaluateAllRules());
    }

    /**
     * 按指标查询规则。
     *
     * @param metricId 指标 ID
     * @return 规则列表
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/rules/by-metric/{metricId}")
    public OperationResponse<List<ScrmAlertRuleEntity>> getRulesByMetric(@PathVariable Long metricId) {
        return OperationResponse.build(scrmSystemMonitorService.getRulesByMetric(metricId));
    }

    /**
     * 按严重程度分页查询规则。
     *
     * @param severity 严重程度: INFO/WARNING/CRITICAL/FATAL
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 规则分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/rules/by-severity/{severity}")
    public OperationResponse<Page<ScrmAlertRuleEntity>> getRulesBySeverity(
            @PathVariable String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemMonitorService.getRulesBySeverity(severity, pageable));
    }

    /**
     * 更新规则触发统计 (triggerCount + lastTriggeredAt)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @PostMapping("/rules/{id}/refresh-stats")
    public OperationResponse<ScrmAlertRuleEntity> updateRuleStats(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.updateRuleStats(id));
    }

    // ============================================================
    // 告警事件管理
    // ============================================================

    /**
     * 手动触发告警事件。
     *
     * @param eventDto 事件参数 (ruleId / metricId / severity / triggerValue 等)
     * @return 创建的告警事件
     * @throws ScrmException 规则/指标不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/events/fire")
    public OperationResponse<ScrmAlertEventEntity> fireEvent(@Valid @RequestBody ScrmAlertEventDto eventDto)
            throws ScrmException {
        Long ruleId = eventDto.getRuleId();
        Long metricId = eventDto.getMetricId();
        Double triggerValue = eventDto.getTriggerValue() != null ? eventDto.getTriggerValue() : 0.0;
        return OperationResponse.build(scrmSystemMonitorService.fireEvent(ruleId, metricId, triggerValue));
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件 ID
     * @return 事件详情
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/events/{id}")
    public OperationResponse<ScrmAlertEventEntity> getEvent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.getEvent(id));
    }

    /**
     * 按编号查询事件。
     *
     * @param eventNo 事件编号
     * @return 事件详情
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/events/by-no/{eventNo}")
    public OperationResponse<ScrmAlertEventEntity> getEventByNo(@PathVariable String eventNo)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.getEventByNo(eventNo));
    }

    /**
     * 分页查询事件列表。
     *
     * @param ruleId    规则 ID 过滤（可空）
     * @param metricId  指标 ID 过滤（可空）
     * @param severity  严重程度过滤（可空）: INFO/WARNING/CRITICAL/FATAL
     * @param status    状态过滤（可空）: FIRING/PENDING/RESOLVED/ACKNOWLEDGED/SUPPRESSED/EXPIRED
     * @param startTime 触发时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   触发时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/events/list")
    public OperationResponse<Page<ScrmAlertEventEntity>> listEvents(
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) Long metricId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "triggerTime"));
        return OperationResponse.build(scrmSystemMonitorService.listEvents(
                ruleId, metricId, severity, status, startTime, endTime, pageable));
    }

    /**
     * 查询触发中的事件 (status=FIRING)。
     *
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/events/firing")
    public OperationResponse<Page<ScrmAlertEventEntity>> getFiringEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "triggerTime"));
        return OperationResponse.build(scrmSystemMonitorService.getFiringEvents(pageable));
    }

    /**
     * 查询严重事件 (severity=CRITICAL 或 FATAL)。
     *
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/events/critical")
    public OperationResponse<Page<ScrmAlertEventEntity>> getCriticalEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "triggerTime"));
        return OperationResponse.build(scrmSystemMonitorService.getCriticalEvents(pageable));
    }

    /**
     * 确认告警: 支持 ACKNOWLEDGE / RESOLVE / SUPPRESS 三种操作。
     *
     * @param acknowledgeDto 确认参数
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法 / 参数非法
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @RateLimit(capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @PostMapping("/events/acknowledge")
    public OperationResponse<ScrmAlertEventEntity> acknowledgeEvent(
            @Valid @RequestBody ScrmAlertAcknowledgeDto acknowledgeDto) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.acknowledgeEvent(acknowledgeDto));
    }

    /**
     * 手动恢复告警。
     *
     * @param id         事件 ID
     * @param resolvedBy 恢复人
     * @param note       恢复备注 (可空)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @PostMapping("/events/{id}/resolve")
    public OperationResponse<ScrmAlertEventEntity> resolveEvent(
            @PathVariable Long id,
            @RequestParam String resolvedBy,
            @RequestParam(required = false) String note) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.resolveEvent(id, resolvedBy, note));
    }

    /**
     * 抑制告警 (指定时长内不再通知)。
     *
     * @param id       事件 ID
     * @param reason   抑制原因 (可空)
     * @param duration 抑制时长分钟 (默认 60)
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @PostMapping("/events/{id}/suppress")
    public OperationResponse<ScrmAlertEventEntity> suppressEvent(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "60") int duration) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.suppressEvent(id, reason, duration));
    }

    /**
     * 检查自动恢复: 评估规则当前是否不再触发, 若是则自动恢复事件。
     *
     * @param id 事件 ID
     * @return 检查结果 Map {autoResolved, reason}
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @PostMapping("/events/{id}/check-auto-resolve")
    public OperationResponse<Map<String, Object>> checkAutoResolve(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.checkAutoResolve(id));
    }

    /**
     * 升级告警: 超过升级时间后通知升级接收人。
     *
     * @param id 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 已升级 / 状态非法
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @PostMapping("/events/{id}/escalate")
    public OperationResponse<ScrmAlertEventEntity> escalateEvent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.escalateEvent(id));
    }

    /**
     * 获取事件时间线 (触发 / 确认 / 通知 / 升级 / 恢复等关键节点)。
     *
     * @param id 事件 ID
     * @return 时间线列表 [{timestamp, action, detail}]
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/events/{id}/timeline")
    public OperationResponse<List<Map<String, Object>>> getEventTimeline(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.getEventTimeline(id));
    }

    /**
     * 获取相关联事件 (同规则或同指标的其它事件)。
     *
     * @param id 事件 ID
     * @return 相关联事件列表
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/events/{id}/related")
    public OperationResponse<List<ScrmAlertEventEntity>> getRelatedEvents(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.getRelatedEvents(id));
    }

    /**
     * 事件统计: 总数 / 各状态数 / 各严重度数。
     *
     * @param startTime 触发时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   触发时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/events/stats")
    public OperationResponse<Map<String, Object>> getEventStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSystemMonitorService.getEventStats(startTime, endTime));
    }

    /**
     * 批量处理告警事件 (统一确认 / 恢复 / 抑制)。
     *
     * @param eventIds 事件 ID 列表
     * @param action   操作类型: ACKNOWLEDGE/RESOLVE/SUPPRESS
     * @param note     操作备注 (可空)
     * @param operator 操作人 (可空)
     * @return 批量处理结果 Map {total, success, failed, results}
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/events/batch-acknowledge")
    public OperationResponse<Map<String, Object>> batchAcknowledge(
            @RequestParam List<Long> eventIds,
            @RequestParam String action,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String operator) {
        return OperationResponse.build(scrmSystemMonitorService.batchAcknowledge(eventIds, action, note, operator));
    }

    /**
     * 发送通知 (按规则通知渠道分发, 记录发送数与失败数)。
     *
     * @param id 事件 ID
     * @return 通知结果 Map {channels, sent, failures}
     * @throws ScrmException 事件不存在
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "execute")
    @PostMapping("/events/{id}/notify")
    public OperationResponse<Map<String, Object>> sendNotification(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemMonitorService.sendNotification(id));
    }

    // ============================================================
    // 系统健康度
    // ============================================================

    /**
     * 系统健康度: 汇总各指标 → 计算健康分 → 返回状态。
     *
     * @return 健康度 Map {score, status, totalMetrics, alertingMetrics, severityBreakdown}
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/health")
    public OperationResponse<Map<String, Object>> getSystemHealth() {
        return OperationResponse.build(scrmSystemMonitorService.getSystemHealth());
    }

    /**
     * 组件健康度: 按指标分组查询组件健康状态。
     *
     * @param component 组件名 (指标分组): SYSTEM/BUSINESS/PERFORMANCE/AVAILABILITY/SECURITY/RESOURCE/API/DATABASE/CACHE/QUEUE
     * @return 组件健康度 Map
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/health/components/{component}")
    public OperationResponse<Map<String, Object>> getComponentHealth(@PathVariable String component) {
        return OperationResponse.build(scrmSystemMonitorService.getComponentHealth(component));
    }

    /**
     * 健康历史: 最近 N 天每日健康分。
     *
     * @param days 天数 (默认 7)
     * @return 健康历史列表 [{date, score, alertCount}]
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/health/history")
    public OperationResponse<List<Map<String, Object>>> getHealthHistory(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmSystemMonitorService.getHealthHistory(days));
    }

    /**
     * 可用性统计: 基于 AVAILABILITY 分组指标计算可用率。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 可用性统计 Map
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/health/availability")
    public OperationResponse<Map<String, Object>> getAvailabilityStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSystemMonitorService.getAvailabilityStats(startTime, endTime));
    }

    /**
     * 性能统计: 基于 PERFORMANCE 分组指标汇总。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 性能统计 Map
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/health/performance")
    public OperationResponse<Map<String, Object>> getPerformanceStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSystemMonitorService.getPerformanceStats(startTime, endTime));
    }

    /**
     * 资源统计: CPU / 内存 / 磁盘 等 RESOURCE 分组指标汇总。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 资源统计 Map
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/health/resource")
    public OperationResponse<Map<String, Object>> getResourceStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSystemMonitorService.getResourceStats(startTime, endTime));
    }

    /**
     * 仪表盘数据: 实时指标 + 活跃告警 + 趋势。
     *
     * @return 仪表盘 Map
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/dashboard")
    public OperationResponse<Map<String, Object>> getDashboardData() {
        return OperationResponse.build(scrmSystemMonitorService.getDashboardData());
    }

    // ============================================================
    // 监控统计
    // ============================================================

    /**
     * 监控统计: 指标数 / 告警数 / 平均恢复时间 (MTTR)。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 监控统计 Map
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getMonitorStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSystemMonitorService.getMonitorStats(startTime, endTime));
    }

    /**
     * 告警统计: 各严重度 / 各规则 / MTTR / MTTA。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 告警统计 Map
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/stats/alerts")
    public OperationResponse<Map<String, Object>> getAlertStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSystemMonitorService.getAlertStats(startTime, endTime));
    }

    /**
     * 指标统计概览: 总数 / 各分组数 / 告警态数。
     *
     * @return 指标统计概览 Map
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/stats/metrics")
    public OperationResponse<Map<String, Object>> getMetricStatsOverview() {
        return OperationResponse.build(scrmSystemMonitorService.getMetricStatsOverview());
    }

    /**
     * 告警最多的指标 (Top N)。
     *
     * @param limit 返回数量 (默认 10)
     * @return Top 指标列表 [{metricId, metricName, metricCode, alertCount}]
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/stats/top-alerted-metrics")
    public OperationResponse<List<Map<String, Object>>> getTopAlertedMetrics(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmSystemMonitorService.getTopAlertedMetrics(limit));
    }

    /**
     * 告警趋势: 最近 N 天每日告警事件数。
     *
     * @param days 天数 (默认 7)
     * @return 趋势列表 [{date, count}]
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/stats/alert-trend")
    public OperationResponse<List<Map<String, Object>>> getAlertTrend(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmSystemMonitorService.getAlertTrend(days));
    }

    /**
     * 严重度分布: 按严重度聚合事件数。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   结束时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 严重度分布 Map {INFO, WARNING, CRITICAL, FATAL, total}
     */
    @RequirePermission(resource = "scrm_system_monitor", action = "read")
    @GetMapping("/stats/severity-distribution")
    public OperationResponse<Map<String, Object>> getSeverityDistribution(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSystemMonitorService.getSeverityDistribution(startTime, endTime));
    }
}
