/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemMonitorService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmAlertAcknowledgeDto;
import org.hiylo.scrm.dto.ScrmAlertRuleDto;
import org.hiylo.scrm.dto.ScrmMetricRecordDto;
import org.hiylo.scrm.dto.ScrmMonitorMetricDto;
import org.hiylo.scrm.entity.ScrmAlertEventEntity;
import org.hiylo.scrm.entity.ScrmAlertRuleEntity;
import org.hiylo.scrm.entity.ScrmMonitorMetricEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 系统监控告警服务 (门面)。
 * <p>
 * 作为系统健康监控与告警模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmSystemMonitorMetricService} (指标管理)、{@link ScrmSystemMonitorRuleService} (告警规则)、
 * {@link ScrmSystemMonitorEventService} (告警事件) 与
 * {@link ScrmSystemMonitorHealthService} (健康概览与统计)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmSystemMonitorService {

    /** 监控指标子域服务 */
    private final ScrmSystemMonitorMetricService metricService;
    /** 告警规则子域服务 */
    private final ScrmSystemMonitorRuleService ruleService;
    /** 告警事件子域服务 */
    private final ScrmSystemMonitorEventService eventService;
    /** 健康概览与统计子域服务 */
    private final ScrmSystemMonitorHealthService healthService;

    /**
     * 创建监控指标。
     * <p>校验 metricGroup / metricType / thresholdDirection / collectionMethod 合法性与
     * metricCode 唯一性后写入归属账号 ID 持久化, 各阈值与统计字段缺省时填默认值。</p>
     *
     * @param dto 指标参数
     * @return 创建后的指标
     * @throws ScrmException 参数非法 / 编码重复
     */
    public ScrmMonitorMetricEntity createMetric(ScrmMonitorMetricDto dto) throws ScrmException {
        return metricService.createMetric(dto);
    }

    /**
     * 更新监控指标（字段非空才覆盖）。
     *
     * @param id  指标 ID
     * @param dto 指标参数
     * @return 更新后的指标
     * @throws ScrmException 指标不存在 / 参数非法 / 编码重复
     */
    public ScrmMonitorMetricEntity updateMetric(Long id, ScrmMonitorMetricDto dto) throws ScrmException {
        return metricService.updateMetric(id, dto);
    }

    /**
     * 删除监控指标 (同时清理关联告警规则)。
     *
     * @param id 指标 ID
     * @throws ScrmException 指标不存在
     */
    public void deleteMetric(Long id) throws ScrmException {
        metricService.deleteMetric(id);
    }

    /**
     * 查询指标详情。
     *
     * @param id 指标 ID
     * @return 指标实体
     * @throws ScrmException 指标不存在
     */
    public ScrmMonitorMetricEntity getMetric(Long id) throws ScrmException {
        return metricService.getMetric(id);
    }

    /**
     * 按编码查询指标。
     *
     * @param code 指标编码
     * @return 指标实体
     * @throws ScrmException 指标不存在
     */
    public ScrmMonitorMetricEntity getMetricByCode(String code) throws ScrmException {
        return metricService.getMetricByCode(code);
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
    public Page<ScrmMonitorMetricEntity> listMetrics(String metricGroup, String metricType, Boolean enabled,
                                                      String keyword, Pageable pageable) {
        return metricService.listMetrics(metricGroup, metricType, enabled, keyword, pageable);
    }

    /**
     * 启用指标。
     *
     * @param id 指标 ID
     * @return 更新后的指标
     * @throws ScrmException 指标不存在
     */
    public ScrmMonitorMetricEntity enableMetric(Long id) throws ScrmException {
        return metricService.enableMetric(id);
    }

    /**
     * 禁用指标。
     *
     * @param id 指标 ID
     * @return 更新后的指标
     * @throws ScrmException 指标不存在
     */
    public ScrmMonitorMetricEntity disableMetric(Long id) throws ScrmException {
        return metricService.disableMetric(id);
    }

    /**
     * 记录指标值: 更新当前值 → 追加历史 → 更新统计 → 检查阈值 → 可能触发告警。
     *
     * @param recordDto 指标记录 DTO (metricCode + value + timestamp)
     * @return 更新后的指标
     * @throws ScrmException 指标不存在 / 已禁用
     */
    public ScrmMonitorMetricEntity recordMetric(ScrmMetricRecordDto recordDto) throws ScrmException {
        return metricService.recordMetric(recordDto);
    }

    /**
     * 批量记录指标值。
     *
     * @param records 指标记录列表
     * @return 处理结果列表 (每条记录的处理状态)
     */
    public List<Map<String, Object>> batchRecordMetrics(List<ScrmMetricRecordDto> records) {
        return metricService.batchRecordMetrics(records);
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
    public List<Map<String, Object>> getMetricHistory(Long metricId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        return metricService.getMetricHistory(metricId, startTime, endTime);
    }

    /**
     * 获取指标当前值。
     *
     * @param metricCode 指标编码
     * @return 当前值 Map {metricCode, metricName, value, unit, lastCollectedAt}
     * @throws ScrmException 指标不存在
     */
    public Map<String, Object> getMetricValue(String metricCode) throws ScrmException {
        return metricService.getMetricValue(metricCode);
    }

    /**
     * 指标汇总: 按分组聚合指标数与告警态指标数。
     *
     * @param metricGroup 指标分组（可空, 空则汇总全部分组）
     * @return 汇总结果 Map
     */
    public Map<String, Object> getMetricSummary(String metricGroup) {
        return metricService.getMetricSummary(metricGroup);
    }

    /**
     * 更新指标统计 (最小/最大/平均, 基于历史数据重算)。
     *
     * @param id 指标 ID
     * @return 更新后的指标
     * @throws ScrmException 指标不存在
     */
    public ScrmMonitorMetricEntity updateMetricStats(Long id) throws ScrmException {
        return metricService.updateMetricStats(id);
    }

    /**
     * 计算指标值 (模拟采集, 基于当前值小幅波动)。
     *
     * @param id 指标 ID
     * @return 计算后的指标
     * @throws ScrmException 指标不存在
     */
    public ScrmMonitorMetricEntity calculateMetricValue(Long id) throws ScrmException {
        return metricService.calculateMetricValue(id);
    }

    /**
     * 按分组分页查询指标 (按 createTime DESC)。
     *
     * @param group    指标分组
     * @param pageable 分页参数
     * @return 指标分页结果
     */
    public Page<ScrmMonitorMetricEntity> getMetricsByGroup(String group, Pageable pageable) {
        return metricService.getMetricsByGroup(group, pageable);
    }

    /**
     * 查询告警中的指标 (isAlertActive=TRUE)。
     *
     * @param pageable 分页参数
     * @return 指标分页结果
     */
    public Page<ScrmMonitorMetricEntity> getAlertingMetrics(Pageable pageable) {
        return metricService.getAlertingMetrics(pageable);
    }

    /**
     * 指标趋势: 最近 N 天的指标值变化 (从历史数据按天聚合)。
     *
     * @param metricCode 指标编码
     * @param days       天数
     * @return 趋势数据列表 [{date, avg, min, max, count}]
     * @throws ScrmException 指标不存在
     */
    public List<Map<String, Object>> getMetricTrend(String metricCode, int days) throws ScrmException {
        return metricService.getMetricTrend(metricCode, days);
    }

    /**
     * 创建告警规则。
     * <p>校验 condition / severity / notificationChannels 合法性与 ruleCode 唯一性,
     * 关联指标必须存在, 写入归属账号 ID 持久化。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 编码重复 / 指标不存在
     */
    public ScrmAlertRuleEntity createRule(ScrmAlertRuleDto dto) throws ScrmException {
        return ruleService.createRule(dto);
    }

    /**
     * 更新告警规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 编码重复 / 指标不存在
     */
    public ScrmAlertRuleEntity updateRule(Long id, ScrmAlertRuleDto dto) throws ScrmException {
        return ruleService.updateRule(id, dto);
    }

    /**
     * 删除告警规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    public void deleteRule(Long id) throws ScrmException {
        ruleService.deleteRule(id);
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    public ScrmAlertRuleEntity getRule(Long id) throws ScrmException {
        return ruleService.getRule(id);
    }

    /**
     * 按编码查询规则。
     *
     * @param code 规则编码
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    public ScrmAlertRuleEntity getRuleByCode(String code) throws ScrmException {
        return ruleService.getRuleByCode(code);
    }

    /**
     * 分页查询规则, 支持按指标 / 严重程度 / 启用状态 / 关键字过滤。
     *
     * @param metricId 指标 ID 过滤（可空）
     * @param severity 严重程度过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  规则名称关键字模糊匹配（可空）
     * @param pageable 分页参数
     * @return 规则分页结果 (按 createTime DESC)
     */
    public Page<ScrmAlertRuleEntity> listRules(Long metricId, String severity, Boolean enabled,
                                                String keyword, Pageable pageable) {
        return ruleService.listRules(metricId, severity, enabled, keyword, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmAlertRuleEntity enableRule(Long id) throws ScrmException {
        return ruleService.enableRule(id);
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmAlertRuleEntity disableRule(Long id) throws ScrmException {
        return ruleService.disableRule(id);
    }

    /**
     * 评估规则: 获取指标当前值 → 比较阈值 → 返回是否触发。
     *
     * @param ruleId 规则 ID
     * @return 评估结果 Map {triggered, currentValue, thresholdValue, condition, severity, message}
     * @throws ScrmException 规则不存在 / 已禁用
     */
    public Map<String, Object> evaluateRule(Long ruleId) throws ScrmException {
        return ruleService.evaluateRule(ruleId);
    }

    /**
     * 评估所有启用规则: 批量检查 → 触发告警 (冷却期内不重复触发)。
     *
     * @return 评估结果 Map {totalRules, evaluated, triggered, results}
     */
    public Map<String, Object> evaluateAllRules() {
        return ruleService.evaluateAllRules();
    }

    /**
     * 条件检查: 比较当前值与阈值是否满足触发条件。
     *
     * @param currentValue 当前值
     * @param condition    条件操作符: GT/GTE/LT/LTE/EQ/NE/CONTAINS/NOT_CONTAINS
     * @param threshold1   阈值 1
     * @param threshold2   阈值 2 (范围用)
     * @return 是否触发
     */
    public boolean checkCondition(double currentValue, String condition, double threshold1, double threshold2) {
        return ruleService.checkCondition(currentValue, condition, threshold1, threshold2);
    }

    /**
     * 按指标查询规则。
     *
     * @param metricId 指标 ID
     * @return 规则列表
     */
    public List<ScrmAlertRuleEntity> getRulesByMetric(Long metricId) {
        return ruleService.getRulesByMetric(metricId);
    }

    /**
     * 按严重程度分页查询规则。
     *
     * @param severity 严重程度
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    public Page<ScrmAlertRuleEntity> getRulesBySeverity(String severity, Pageable pageable) {
        return ruleService.getRulesBySeverity(severity, pageable);
    }

    /**
     * 更新规则触发统计 (triggerCount + lastTriggeredAt)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmAlertRuleEntity updateRuleStats(Long id) throws ScrmException {
        return ruleService.updateRuleStats(id);
    }

    /**
     * 触发告警事件 (完整实现: 创建事件 → 发送通知 → 更新统计 → 标记指标告警态)。
     *
     * @param ruleId       规则 ID (可空)
     * @param metricId     指标 ID (可空)
     * @param triggerValue 触发值
     * @return 创建的告警事件
     * @throws ScrmException 规则/指标不存在
     */
    public ScrmAlertEventEntity fireEvent(Long ruleId, Long metricId, Double triggerValue) throws ScrmException {
        return eventService.fireEvent(ruleId, metricId, triggerValue);
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    public ScrmAlertEventEntity getEvent(Long id) throws ScrmException {
        return eventService.getEvent(id);
    }

    /**
     * 按编号查询事件。
     *
     * @param eventNo 事件编号
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    public ScrmAlertEventEntity getEventByNo(String eventNo) throws ScrmException {
        return eventService.getEventByNo(eventNo);
    }

    /**
     * 分页查询事件, 支持按规则 / 指标 / 严重度 / 状态 / 时间区间过滤。
     *
     * @param ruleId     规则 ID 过滤（可空）
     * @param metricId   指标 ID 过滤（可空）
     * @param severity   严重程度过滤（可空）
     * @param status     状态过滤（可空）
     * @param startTime  触发时间起始（可空）
     * @param endTime    触发时间截止（可空）
     * @param pageable   分页参数
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    public Page<ScrmAlertEventEntity> listEvents(Long ruleId, Long metricId, String severity, String status,
                                                  LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        return eventService.listEvents(ruleId, metricId, severity, status, startTime, endTime, pageable);
    }

    /**
     * 查询触发中的事件 (status=FIRING)。
     *
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    public Page<ScrmAlertEventEntity> getFiringEvents(Pageable pageable) {
        return eventService.getFiringEvents(pageable);
    }

    /**
     * 查询严重事件 (severity=CRITICAL 或 FATAL)。
     *
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    public Page<ScrmAlertEventEntity> getCriticalEvents(Pageable pageable) {
        return eventService.getCriticalEvents(pageable);
    }

    /**
     * 确认告警: 支持 ACKNOWLEDGE / RESOLVE / SUPPRESS 三种操作。
     *
     * @param acknowledgeDto 确认参数
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法 / 参数非法
     */
    public ScrmAlertEventEntity acknowledgeEvent(ScrmAlertAcknowledgeDto acknowledgeDto) throws ScrmException {
        return eventService.acknowledgeEvent(acknowledgeDto);
    }

    /**
     * 手动恢复告警。
     *
     * @param eventId    事件 ID
     * @param resolvedBy 恢复人
     * @param note       恢复备注
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    public ScrmAlertEventEntity resolveEvent(Long eventId, String resolvedBy, String note) throws ScrmException {
        return eventService.resolveEvent(eventId, resolvedBy, note);
    }

    /**
     * 抑制告警 (指定时长内不再通知)。
     *
     * @param eventId   事件 ID
     * @param reason    抑制原因
     * @param duration  抑制时长分钟
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 状态非法
     */
    public ScrmAlertEventEntity suppressEvent(Long eventId, String reason, int duration) throws ScrmException {
        return eventService.suppressEvent(eventId, reason, duration);
    }

    /**
     * 检查自动恢复: 评估规则当前是否不再触发, 若是则自动恢复事件。
     *
     * @param eventId 事件 ID
     * @return 检查结果 Map {autoResolved, reason}
     * @throws ScrmException 事件不存在
     */
    public Map<String, Object> checkAutoResolve(Long eventId) throws ScrmException {
        return eventService.checkAutoResolve(eventId);
    }

    /**
     * 升级告警: 超过升级时间后通知升级接收人。
     *
     * @param eventId 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在 / 已升级 / 状态非法
     */
    public ScrmAlertEventEntity escalateEvent(Long eventId) throws ScrmException {
        return eventService.escalateEvent(eventId);
    }

    /**
     * 获取事件时间线 (触发 / 确认 / 通知 / 升级 / 恢复等关键节点)。
     *
     * @param eventId 事件 ID
     * @return 时间线列表 [{timestamp, action, detail}]
     * @throws ScrmException 事件不存在
     */
    public List<Map<String, Object>> getEventTimeline(Long eventId) throws ScrmException {
        return eventService.getEventTimeline(eventId);
    }

    /**
     * 获取相关联事件 (同规则或同指标的其它事件)。
     *
     * @param eventId 事件 ID
     * @return 相关联事件列表
     * @throws ScrmException 事件不存在
     */
    public List<ScrmAlertEventEntity> getRelatedEvents(Long eventId) throws ScrmException {
        return eventService.getRelatedEvents(eventId);
    }

    /**
     * 事件统计: 总数 / 各状态数 / 各严重度数。
     *
     * @param startTime 触发时间起始（可空）
     * @param endTime   触发时间截止（可空）
     * @return 统计结果 Map
     */
    public Map<String, Object> getEventStats(LocalDateTime startTime, LocalDateTime endTime) {
        return eventService.getEventStats(startTime, endTime);
    }

    /**
     * 批量处理告警事件 (统一确认 / 恢复 / 抑制)。
     *
     * @param eventIds 事件 ID 列表
     * @param action   操作类型: ACKNOWLEDGE/RESOLVE/SUPPRESS
     * @param note     操作备注
     * @param operator 操作人
     * @return 批量处理结果 Map {total, success, failed, results}
     */
    public Map<String, Object> batchAcknowledge(List<Long> eventIds, String action, String note, String operator) {
        return eventService.batchAcknowledge(eventIds, action, note, operator);
    }

    /**
     * 发送通知 (模拟实现: 按规则通知渠道分发, 记录发送数与失败数)。
     *
     * @param eventId 事件 ID
     * @return 通知结果 Map {channels, sent, failures}
     * @throws ScrmException 事件不存在
     */
    public Map<String, Object> sendNotification(Long eventId) throws ScrmException {
        return eventService.sendNotification(eventId);
    }

    /**
     * 检查冷却期: 规则最近一次触发是否在冷却期内。
     *
     * @param ruleId 规则 ID
     * @return true 表示可触发 (不在冷却期), false 表示在冷却期内
     * @throws ScrmException 规则不存在
     */
    public boolean checkCooldown(Long ruleId) throws ScrmException {
        return ruleService.checkCooldown(ruleId);
    }

    /**
     * 系统健康度: 汇总各指标 → 计算健康分 → 返回状态。
     * <p>健康分 = 100 - 告警指标扣分 (CRITICAL/FATAL 各扣 10/20 分, WARNING 扣 5 分)。</p>
     *
     * @return 健康度 Map {score, status, totalMetrics, alertingMetrics, severityBreakdown}
     */
    public Map<String, Object> getSystemHealth() {
        return healthService.getSystemHealth();
    }

    /**
     * 组件健康度: 按指标分组查询组件健康状态。
     *
     * @param component 组件名 (指标分组)
     * @return 组件健康度 Map
     */
    public Map<String, Object> getComponentHealth(String component) {
        return healthService.getComponentHealth(component);
    }

    /**
     * 健康历史: 最近 N 天每日健康分 (基于每日告警事件数推算)。
     *
     * @param days 天数
     * @return 健康历史列表 [{date, score, alertCount}]
     */
    public List<Map<String, Object>> getHealthHistory(int days) {
        return healthService.getHealthHistory(days);
    }

    /**
     * 可用性统计: 基于 AVAILABILITY 分组指标计算可用率。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 可用性统计 Map
     */
    public Map<String, Object> getAvailabilityStats(LocalDateTime startTime, LocalDateTime endTime) {
        return healthService.getAvailabilityStats(startTime, endTime);
    }

    /**
     * 性能统计: 基于 PERFORMANCE 分组指标汇总。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 性能统计 Map
     */
    public Map<String, Object> getPerformanceStats(LocalDateTime startTime, LocalDateTime endTime) {
        return healthService.getPerformanceStats(startTime, endTime);
    }

    /**
     * 资源统计: CPU / 内存 / 磁盘 等 RESOURCE 分组指标汇总。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 资源统计 Map
     */
    public Map<String, Object> getResourceStats(LocalDateTime startTime, LocalDateTime endTime) {
        return healthService.getResourceStats(startTime, endTime);
    }

    /**
     * 仪表盘数据: 实时指标 + 活跃告警 + 趋势。
     *
     * @return 仪表盘 Map
     */
    public Map<String, Object> getDashboardData() {
        return healthService.getDashboardData();
    }

    /**
     * 监控统计: 指标数 / 告警数 / 平均恢复时间 (MTTR)。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 监控统计 Map
     */
    public Map<String, Object> getMonitorStats(LocalDateTime startTime, LocalDateTime endTime) {
        return healthService.getMonitorStats(startTime, endTime);
    }

    /**
     * 告警统计: 各严重度 / 各规则 / MTTR / MTTA。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 告警统计 Map
     */
    public Map<String, Object> getAlertStats(LocalDateTime startTime, LocalDateTime endTime) {
        return healthService.getAlertStats(startTime, endTime);
    }

    /**
     * 指标统计概览: 总数 / 各分组数 / 告警态数。
     *
     * @return 指标统计概览 Map
     */
    public Map<String, Object> getMetricStatsOverview() {
        return healthService.getMetricStatsOverview();
    }

    /**
     * 告警最多的指标 (Top N)。
     *
     * @param limit 返回数量
     * @return Top 指标列表 [{metricId, metricName, metricCode, alertCount}]
     */
    public List<Map<String, Object>> getTopAlertedMetrics(int limit) {
        return healthService.getTopAlertedMetrics(limit);
    }

    /**
     * 告警趋势: 最近 N 天每日告警事件数。
     *
     * @param days 天数
     * @return 趋势列表 [{date, count}]
     */
    public List<Map<String, Object>> getAlertTrend(int days) {
        return healthService.getAlertTrend(days);
    }

    /**
     * 严重度分布: 按严重度聚合事件数。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 严重度分布 Map {INFO, WARNING, CRITICAL, FATAL}
     */
    public Map<String, Object> getSeverityDistribution(LocalDateTime startTime, LocalDateTime endTime) {
        return healthService.getSeverityDistribution(startTime, endTime);
    }

    /**
     * 生成事件编号: ALERT + 年月日 + 4 位序号。
     *
     * @return 事件编号
     */
    public String generateEventNo() {
        return eventService.generateEventNo();
    }
}