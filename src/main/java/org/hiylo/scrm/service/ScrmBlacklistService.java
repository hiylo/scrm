/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmAppealDto;
import org.hiylo.scrm.dto.ScrmBlacklistCheckDto;
import org.hiylo.scrm.dto.ScrmBlacklistDto;
import org.hiylo.scrm.dto.ScrmBlacklistRuleDto;
import org.hiylo.scrm.dto.ScrmRiskAssessmentDto;
import org.hiylo.scrm.dto.ScrmRiskEventDto;
import org.hiylo.scrm.entity.ScrmBlacklistEntity;
import org.hiylo.scrm.entity.ScrmBlacklistRuleEntity;
import org.hiylo.scrm.entity.ScrmRiskEventEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 黑名单风控服务 (门面)。
 * <p>
 * 作为风控模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmBlacklistManageService} (黑名单管理)、{@link ScrmBlacklistRuleService} (规则管理)、
 * {@link ScrmRiskEventService} (风险事件) 与 {@link ScrmBlacklistStatsService} (统计与风险评估)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmBlacklistService {

    /** 黑名单管理子域服务 */
    private final ScrmBlacklistManageService blacklistManageService;
    /** 风控规则子域服务 */
    private final ScrmBlacklistRuleService ruleService;
    /** 风险事件子域服务 */
    private final ScrmRiskEventService eventService;
    /** 黑名单统计与风险评估子域服务 */
    private final ScrmBlacklistStatsService statsService;

    // ============================================================
    // 黑名单管理
    // ============================================================

    /**
     * 加入名单 (黑名单/灰名单/白名单/观察名单)。
     *
     * @param dto 名单参数
     * @return 创建后的名单条目
     * @throws ScrmException 参数非法
     */
    public ScrmBlacklistEntity addToBlacklist(ScrmBlacklistDto dto) throws ScrmException {
        return blacklistManageService.addToBlacklist(dto);
    }

    /**
     * 从名单移出 (状态置为 REMOVED)。
     *
     * @param id        名单条目 ID
     * @param reason    移除原因
     * @param removedBy 移除人
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 状态非法
     */
    public ScrmBlacklistEntity removeFromBlacklist(Long id, String reason, String removedBy) throws ScrmException {
        return blacklistManageService.removeFromBlacklist(id, reason, removedBy);
    }

    /**
     * 查询名单详情。
     *
     * @param id 名单条目 ID
     * @return 名单条目
     * @throws ScrmException 名单不存在
     */
    public ScrmBlacklistEntity getBlacklist(Long id) throws ScrmException {
        return blacklistManageService.getBlacklist(id);
    }

    /**
     * 分页查询名单, 支持按名单类型 / 目标类型 / 风险等级 / 状态 / 关键字过滤。
     *
     * @param listType   名单类型过滤（可空）
     * @param targetType 目标类型过滤（可空）
     * @param riskLevel  风险等级过滤（可空）
     * @param status     状态过滤（可空）
     * @param keyword    目标值/目标名称关键字模糊匹配（可空）
     * @param pageable   分页参数
     * @return 名单分页结果 (按 updateTime DESC)
     */
    public Page<ScrmBlacklistEntity> listBlacklist(String listType, String targetType, String riskLevel,
                                                    String status, String keyword, Pageable pageable) {
        return blacklistManageService.listBlacklist(listType, targetType, riskLevel, status, keyword, pageable);
    }

    /**
     * 检查目标是否在名单中 (完整实现)。
     *
     * @param checkDto 检查参数
     * @return 检查结果 Map
     * @throws ScrmException 参数非法
     */
    public Map<String, Object> checkBlacklist(ScrmBlacklistCheckDto checkDto) throws ScrmException {
        return blacklistManageService.checkBlacklist(checkDto);
    }

    /**
     * 批量检查多个目标是否在名单中。
     *
     * @param targets 检查参数列表
     * @return 检查结果列表
     */
    public List<Map<String, Object>> batchCheck(List<ScrmBlacklistCheckDto> targets) {
        return blacklistManageService.batchCheck(targets);
    }

    /**
     * 按目标查询名单条目 (所有状态)。
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @return 名单条目列表
     */
    public List<ScrmBlacklistEntity> getByTarget(String targetType, String targetValue) {
        return blacklistManageService.getByTarget(targetType, targetValue);
    }

    /**
     * 查询已过期名单 (状态 ACTIVE 且 expiryDate 早于今天)。
     *
     * @return 已过期名单列表
     */
    public List<ScrmBlacklistEntity> getExpiredList() {
        return blacklistManageService.getExpiredList();
    }

    /**
     * 查询即将到期名单 (状态 ACTIVE 且 expiryDate 在今天起 days 天内)。
     *
     * @param days 天数
     * @return 即将到期名单列表
     */
    public List<ScrmBlacklistEntity> getExpiringSoon(int days) {
        return blacklistManageService.getExpiringSoon(days);
    }

    /**
     * 发起申诉 (状态置为 APPEALED, 申诉状态置为 PENDING)。
     *
     * @param appealDto 申诉参数
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 状态非法
     */
    public ScrmBlacklistEntity appeal(ScrmAppealDto appealDto) throws ScrmException {
        return blacklistManageService.appeal(appealDto);
    }

    /**
     * 审核申诉。
     *
     * @param blacklistId 名单条目 ID
     * @param action      审核动作: APPROVED / REJECTED
     * @param reviewerId  审核人 ID
     * @param result      审核结果说明
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 动作非法
     */
    public ScrmBlacklistEntity reviewAppeal(Long blacklistId, String action, String reviewerId, String result)
            throws ScrmException {
        return blacklistManageService.reviewAppeal(blacklistId, action, reviewerId, result);
    }

    /**
     * 恢复已移除的名单条目 (状态置为 RESTORED → ACTIVE)。
     *
     * @param blacklistId 名单条目 ID
     * @param reason      恢复原因
     * @param restoredBy  恢复人
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 状态非法
     */
    public ScrmBlacklistEntity restore(Long blacklistId, String reason, String restoredBy) throws ScrmException {
        return blacklistManageService.restore(blacklistId, reason, restoredBy);
    }

    /**
     * 延期名单到期日。
     *
     * @param blacklistId    名单条目 ID
     * @param newExpiryDate  新到期日期
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 日期非法
     */
    public ScrmBlacklistEntity extendExpiry(Long blacklistId, LocalDate newExpiryDate) throws ScrmException {
        return blacklistManageService.extendExpiry(blacklistId, newExpiryDate);
    }

    /**
     * 更新名单风险评分。
     *
     * @param blacklistId 名单条目 ID
     * @param score       风险评分 0-100
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 评分越界
     */
    public ScrmBlacklistEntity updateRiskScore(Long blacklistId, double score) throws ScrmException {
        return blacklistManageService.updateRiskScore(blacklistId, score);
    }

    /**
     * 按客户分页查询名单。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 名单分页结果
     */
    public Page<ScrmBlacklistEntity> getBlacklistByCustomer(Long customerId, Pageable pageable) {
        return blacklistManageService.getBlacklistByCustomer(customerId, pageable);
    }

    /**
     * 按风险等级分页查询名单。
     *
     * @param level    风险等级
     * @param pageable 分页参数
     * @return 名单分页结果
     * @throws ScrmException 风险等级非法
     */
    public Page<ScrmBlacklistEntity> getBlacklistByRiskLevel(
            String level, Pageable pageable) throws ScrmException {
        return blacklistManageService.getBlacklistByRiskLevel(level, pageable);
    }

    /**
     * 批量导入名单。
     *
     * @param items 名单参数列表
     * @return 导入结果 {total, success, failed}
     */
    public Map<String, Integer> importBlacklist(List<ScrmBlacklistDto> items) {
        return blacklistManageService.importBlacklist(items);
    }

    /**
     * 导出指定名单类型的所有条目。
     *
     * @param listType 名单类型 (可空, 为空导出全部)
     * @return 名单条目列表
     */
    public List<ScrmBlacklistEntity> exportBlacklist(String listType) {
        return blacklistManageService.exportBlacklist(listType);
    }

    // ============================================================
    // 风控规则管理
    // ============================================================

    /**
     * 创建风控规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 编码重复
     */
    public ScrmBlacklistRuleEntity createRule(ScrmBlacklistRuleDto dto) throws ScrmException {
        return ruleService.createRule(dto);
    }

    /**
     * 更新风控规则 (字段非空才覆盖)。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 编码重复
     */
    public ScrmBlacklistRuleEntity updateRule(Long id, ScrmBlacklistRuleDto dto) throws ScrmException {
        return ruleService.updateRule(id, dto);
    }

    /**
     * 删除风控规则。
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
    public ScrmBlacklistRuleEntity getRule(Long id) throws ScrmException {
        return ruleService.getRule(id);
    }

    /**
     * 按规则代码查询规则。
     *
     * @param code 规则代码
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    public ScrmBlacklistRuleEntity getRuleByCode(String code) throws ScrmException {
        return ruleService.getRuleByCode(code);
    }

    /**
     * 分页查询规则, 支持按规则类型 / 风险类别 / 严重程度 / 启用状态 / 关键字过滤。
     *
     * @param ruleType      规则类型过滤（可空）
     * @param riskCategory  风险类别过滤（可空）
     * @param severity      严重程度过滤（可空）
     * @param enabled       启用状态过滤（可空）
     * @param keyword       名称/代码关键字模糊匹配（可空）
     * @param pageable      分页参数
     * @return 规则分页结果 (按 priority ASC, updateTime DESC)
     */
    public Page<ScrmBlacklistRuleEntity> listRules(String ruleType, String riskCategory, String severity,
                                                    Boolean enabled, String keyword, Pageable pageable) {
        return ruleService.listRules(ruleType, riskCategory, severity, enabled, keyword, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmBlacklistRuleEntity enableRule(Long id) throws ScrmException {
        return ruleService.enableRule(id);
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmBlacklistRuleEntity disableRule(Long id) throws ScrmException {
        return ruleService.disableRule(id);
    }

    /**
     * 评估单条规则 (完整实现)。
     *
     * @param ruleId  规则 ID
     * @param context 评估上下文
     * @return 评估结果 Map {triggered, ruleId, ruleCode, ruleName, field, value, condition, expected,
     *         severity, action}
     * @throws ScrmException 规则不存在
     */
    public Map<String, Object> evaluateRule(Long ruleId, Map<String, Object> context) throws ScrmException {
        return ruleService.evaluateRule(ruleId, context);
    }

    /**
     * 评估所有启用规则 (完整实现)。
     *
     * @param assessmentDto 风险评估参数
     * @return 评估结果 Map {triggeredRules, events, totalTriggered, maxSeverity, recommendedAction}
     * @throws ScrmException 参数非法
     */
    public Map<String, Object> evaluateAllRules(ScrmRiskAssessmentDto assessmentDto) throws ScrmException {
        return ruleService.evaluateAllRules(assessmentDto);
    }

    /**
     * 按风险类别分页查询规则。
     *
     * @param category 风险类别
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    public Page<ScrmBlacklistRuleEntity> getRulesByCategory(String category, Pageable pageable) {
        return ruleService.getRulesByCategory(category, pageable);
    }

    /**
     * 按适用模块分页查询规则。
     *
     * @param module   模块名
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    public Page<ScrmBlacklistRuleEntity> getRulesByModule(String module, Pageable pageable) {
        return ruleService.getRulesByModule(module, pageable);
    }

    /**
     * 更新规则统计 (重新计算误报率 / 准确率)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmBlacklistRuleEntity updateRuleStats(Long id) throws ScrmException {
        return ruleService.updateRuleStats(id);
    }

    /**
     * 标记规则误报 (误报次数 +1, 关联事件置为误报)。
     *
     * @param ruleId  规则 ID
     * @param eventId 事件 ID
     * @return 更新后的规则
     * @throws ScrmException 规则 / 事件不存在
     */
    public ScrmBlacklistRuleEntity markFalsePositive(Long ruleId, Long eventId) throws ScrmException {
        return ruleService.markFalsePositive(ruleId, eventId);
    }

    /**
     * 查询规则准确率。
     *
     * @param id 规则 ID
     * @return 准确率 Map {accuracyRate, falsePositiveRate, triggerCount, falsePositiveCount}
     * @throws ScrmException 规则不存在
     */
    public Map<String, Object> getRuleAccuracy(Long id) throws ScrmException {
        return ruleService.getRuleAccuracy(id);
    }

    /**
     * 复制规则 (生成新代码的副本)。
     *
     * @param id      源规则 ID
     * @param newCode 新规则代码
     * @return 复制后的规则
     * @throws ScrmException 规则不存在 / 编码重复
     */
    public ScrmBlacklistRuleEntity duplicateRule(Long id, String newCode) throws ScrmException {
        return ruleService.duplicateRule(id, newCode);
    }

    /**
     * 查询规则触发历史 (按规则 ID 分页查询关联风险事件)。
     *
     * @param ruleId   规则 ID
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    public Page<ScrmRiskEventEntity> getRuleTriggerHistory(Long ruleId, Pageable pageable) {
        return ruleService.getRuleTriggerHistory(ruleId, pageable);
    }

    // ============================================================
    // 风险事件管理
    // ============================================================

    /**
     * 创建风险事件。
     *
     * @param dto 事件参数
     * @return 创建后的事件
     * @throws ScrmException 参数非法
     */
    public ScrmRiskEventEntity createEvent(ScrmRiskEventDto dto) throws ScrmException {
        return eventService.createEvent(dto);
    }

    /**
     * 查询事件详情。
     *
     * @param id 事件 ID
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    public ScrmRiskEventEntity getEvent(Long id) throws ScrmException {
        return eventService.getEvent(id);
    }

    /**
     * 按事件编号查询事件。
     *
     * @param eventNo 事件编号
     * @return 事件实体
     * @throws ScrmException 事件不存在
     */
    public ScrmRiskEventEntity getEventByNo(String eventNo) throws ScrmException {
        return eventService.getEventByNo(eventNo);
    }

    /**
     * 分页查询事件, 支持按规则 / 客户 / 风险类别 / 风险等级 / 状态 / 时间区间过滤。
     *
     * @param ruleId       规则 ID 过滤（可空）
     * @param customerId   客户 ID 过滤（可空）
     * @param riskCategory 风险类别过滤（可空）
     * @param riskLevel    风险等级过滤（可空）
     * @param status       状态过滤（可空）
     * @param startTime    触发起始时间（可空）
     * @param endTime      触发截止时间（可空）
     * @param pageable     分页参数
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    public Page<ScrmRiskEventEntity> listEvents(Long ruleId, Long customerId, String riskCategory, String riskLevel,
                                                 String status, LocalDateTime startTime, LocalDateTime endTime,
                                                 Pageable pageable) {
        return eventService.listEvents(ruleId, customerId, riskCategory, riskLevel, status, startTime,
                endTime, pageable);
    }

    /**
     * 触发风险事件 (完整实现)。
     *
     * @param ruleId      规则 ID
     * @param customerId  客户 ID (可空)
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @param triggerData JSON 触发数据
     * @return 创建后的事件
     * @throws ScrmException 规则不存在
     */
    public ScrmRiskEventEntity fireEvent(Long ruleId, Long customerId, String targetType, String targetValue,
                                          String triggerData) throws ScrmException {
        return eventService.fireEvent(ruleId, customerId, targetType, targetValue, triggerData);
    }

    /**
     * 分配事件处理人。
     *
     * @param id         事件 ID
     * @param assigneeId 处理人 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    public ScrmRiskEventEntity assignEvent(Long id, String assigneeId) throws ScrmException {
        return eventService.assignEvent(id, assigneeId);
    }

    /**
     * 调查事件。
     *
     * @param id             事件 ID
     * @param investigatorId 调查人 ID
     * @param notes          调查备注
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    public ScrmRiskEventEntity investigate(Long id, String investigatorId, String notes) throws ScrmException {
        return eventService.investigate(id, investigatorId, notes);
    }

    /**
     * 确认风险。
     *
     * @param id           事件 ID
     * @param confirmedBy 确认人 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    public ScrmRiskEventEntity confirmRisk(Long id, String confirmedBy) throws ScrmException {
        return eventService.confirmRisk(id, confirmedBy);
    }

    /**
     * 标记事件为误报。
     *
     * @param id     事件 ID
     * @param reason 误报原因
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    public ScrmRiskEventEntity markFalsePositive(Long id, String reason) throws ScrmException {
        return eventService.markFalsePositive(id, reason);
    }

    /**
     * 解决事件。
     *
     * @param id         事件 ID
     * @param resolution 处理结果
     * @param resolvedBy 解决人 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    public ScrmRiskEventEntity resolveEvent(Long id, String resolution, String resolvedBy) throws ScrmException {
        return eventService.resolveEvent(id, resolution, resolvedBy);
    }

    /**
     * 升级事件。
     *
     * @param id           事件 ID
     * @param escalatedTo  升级给
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    public ScrmRiskEventEntity escalateEvent(Long id, String escalatedTo) throws ScrmException {
        return eventService.escalateEvent(id, escalatedTo);
    }

    /**
     * 查询待处理事件 (status = OPEN)。
     *
     * @param pageable 分页参数
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    public Page<ScrmRiskEventEntity> getOpenEvents(Pageable pageable) {
        return eventService.getOpenEvents(pageable);
    }

    /**
     * 查询严重事件 (riskLevel = HIGH / CRITICAL)。
     *
     * @param pageable 分页参数
     * @return 事件分页结果 (按 triggerTime DESC)
     */
    public Page<ScrmRiskEventEntity> getCriticalEvents(Pageable pageable) {
        return eventService.getCriticalEvents(pageable);
    }

    /**
     * 按客户分页查询事件。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 事件分页结果
     */
    public Page<ScrmRiskEventEntity> getEventsByCustomer(Long customerId, Pageable pageable) {
        return eventService.getEventsByCustomer(customerId, pageable);
    }

    /**
     * 按规则分页查询事件。
     *
     * @param ruleId   规则 ID
     * @param pageable 分页参数
     * @return 事件分页结果
     */
    public Page<ScrmRiskEventEntity> getEventsByRule(Long ruleId, Pageable pageable) {
        return eventService.getEventsByRule(ruleId, pageable);
    }

    /**
     * 事件时间线 (按事件创建时间正序返回该事件及其相关事件)。
     *
     * @param id 事件 ID
     * @return 事件列表
     * @throws ScrmException 事件不存在
     */
    public List<ScrmRiskEventEntity> getEventTimeline(Long id) throws ScrmException {
        return eventService.getEventTimeline(id);
    }

    /**
     * 查询相关事件 (同一目标的其它事件)。
     *
     * @param id 事件 ID
     * @return 相关事件列表
     * @throws ScrmException 事件不存在
     */
    public List<ScrmRiskEventEntity> getRelatedEvents(Long id) throws ScrmException {
        return eventService.getRelatedEvents(id);
    }

    /**
     * 执行事件动作 (完整实现)。
     *
     * @param eventId 事件 ID
     * @return 更新后的事件
     * @throws ScrmException 事件不存在
     */
    public ScrmRiskEventEntity executeAction(Long eventId) throws ScrmException {
        return eventService.executeAction(eventId);
    }

    /**
     * 批量解决事件。
     *
     * @param eventIds  事件 ID 列表
     * @param resolution 处理结果
     * @param resolvedBy 解决人 ID
     * @return 批量结果 {total, success, failed}
     */
    public Map<String, Integer> batchResolve(List<Long> eventIds, String resolution, String resolvedBy) {
        return eventService.batchResolve(eventIds, resolution, resolvedBy);
    }

    // ============================================================
    // 风险评估与统计
    // ============================================================

    /**
     * 综合风险评估 (完整实现)。
     *
     * @param assessmentDto 评估参数
     * @return 评估结果 Map {targetType, targetValue, blacklistHit, triggeredRules, riskScore, riskLevel,
     *         recommendation}
     * @throws ScrmException 参数非法
     */
    public Map<String, Object> assessRisk(ScrmRiskAssessmentDto assessmentDto) throws ScrmException {
        return statsService.assessRisk(assessmentDto);
    }

    /**
     * 客户风险评分 (基于客户关联名单与事件)。
     *
     * @param customerId 客户 ID
     * @return 风险评分 Map {customerId, riskScore, riskLevel, blacklistCount, eventCount}
     */
    public Map<String, Object> getCustomerRiskScore(Long customerId) {
        return statsService.getCustomerRiskScore(customerId);
    }

    /**
     * 目标风险评分 (基于目标关联名单与事件)。
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @return 风险评分 Map {targetType, targetValue, riskScore, riskLevel, blacklistCount, eventCount}
     */
    public Map<String, Object> getTargetRiskScore(String targetType, String targetValue) {
        return statsService.getTargetRiskScore(targetType, targetValue);
    }

    /**
     * 计算风险分 (加权计算 0-100, 完整实现)。
     *
     * @param factors 风险因子 Map {因子名: 因子分值(0-100)}
     * @return 评分结果 Map {score, breakdown}
     */
    public Map<String, Object> calculateRiskScore(Map<String, Double> factors) {
        return statsService.calculateRiskScore(factors);
    }

    /**
     * 获取目标风险因子 (名单命中 / 事件数 / 风险等级等)。
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @return 风险因子 Map
     */
    public Map<String, Object> getRiskFactors(String targetType, String targetValue) {
        return statsService.getRiskFactors(targetType, targetValue);
    }

    /**
     * 风险历史 (按月聚合客户风险事件)。
     *
     * @param customerId 客户 ID
     * @param months     回溯月数
     * @return 风险历史列表 [{month, eventCount, highRiskCount}]
     */
    public List<Map<String, Object>> getRiskHistory(Long customerId, int months) {
        return statsService.getRiskHistory(customerId, months);
    }

    /**
     * 各名单类型活跃数统计。
     *
     * @return 统计 Map {BLACKLIST, GRAYLIST, WHITELIST, WATCHLIST, total}
     */
    public Map<String, Long> getActiveCount() {
        return statsService.getActiveCount();
    }

    /**
     * 名单统计 (各类型 / 各目标 / 各等级)。
     *
     * @return 统计结果 Map
     */
    public Map<String, Object> getBlacklistStats() {
        return statsService.getBlacklistStats();
    }

    /**
     * 风险统计 (事件数 / 各类别 / 各等级 / 解决率)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getRiskStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getRiskStats(startTime, endTime);
    }

    /**
     * 规则表现统计 (触发率 / 准确率 / 误报率)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getRulePerformanceStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getRulePerformanceStats(startTime, endTime);
    }

    /**
     * 风险趋势 (按月统计事件数)。
     *
     * @param months 回溯月数
     * @return 趋势结果 Map {month, labels, eventCounts, highRiskCounts}
     */
    public Map<String, Object> getRiskTrend(int months) {
        return statsService.getRiskTrend(months);
    }

    /**
     * 触发最多的规则 Top N。
     *
     * @param limit 返回条数
     * @return 规则列表 (按 triggerCount DESC)
     */
    public List<ScrmBlacklistRuleEntity> getTopRiskRules(int limit) {
        return statsService.getTopRiskRules(limit);
    }

    /**
     * 高风险客户 Top N (按事件数)。
     *
     * @param limit 返回条数
     * @return 高风险客户列表 [{customerId, eventCount, highRiskCount}]
     */
    public List<Map<String, Object>> getTopRiskCustomers(int limit) {
        return statsService.getTopRiskCustomers(limit);
    }

    /**
     * 误报率统计。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 误报率 Map {totalEvents, falsePositiveCount, falsePositiveRate}
     */
    public Map<String, Object> getFalsePositiveRate(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getFalsePositiveRate(startTime, endTime);
    }

    /**
     * 平均处理时间 (小时)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 平均处理时间 Map {resolvedCount, averageResolutionHours}
     */
    public Map<String, Object> getAverageResolutionTime(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getAverageResolutionTime(startTime, endTime);
    }

    /**
     * 风险概览 (名单统计 + 风险统计 + 规则表现 + 待处理 / 严重事件数)。
     *
     * @return 概览 Map
     */
    public Map<String, Object> getRiskOverview() {
        return statsService.getRiskOverview();
    }

    /**
     * 生成事件编号: RISK + 年月日 + 4 位序号。
     *
     * @return 事件编号
     */
    public String generateEventNo() {
        return eventService.generateEventNo();
    }
}