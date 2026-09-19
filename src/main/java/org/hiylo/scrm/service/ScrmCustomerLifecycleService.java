/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerLifecycleDto;
import org.hiylo.scrm.dto.ScrmLifecycleBulkTransitionDto;
import org.hiylo.scrm.dto.ScrmLifecycleStageDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionActionDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionRequestDto;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleEntity;
import org.hiylo.scrm.entity.ScrmLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmLifecycleStageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户生命周期管理服务门面。
 * <p>
 * 在既有 {@link ScrmLifecycleService} 阶段定义 / 流转规则 / 转换历史能力之上, 扩展完整的
 * 客户生命周期管理闭环。本类仅作为门面, 按子域委托给
 * {@link ScrmCustomerLifecycleStageService} (阶段管理)、
 * {@link ScrmCustomerLifecycleRecordService} (生命周期记录与流转)、
 * {@link ScrmCustomerLifecycleAnalyticsService} (评分与统计) 与
 * {@link ScrmCustomerLifecycleTransitionService} (转换记录分析) 兄弟服务。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerLifecycleService {

    /** 阶段管理兄弟服务 */
    private final ScrmCustomerLifecycleStageService stageService;

    /** 生命周期记录与流转兄弟服务 */
    private final ScrmCustomerLifecycleRecordService recordService;

    /** 评分与统计兄弟服务 */
    private final ScrmCustomerLifecycleAnalyticsService analyticsService;

    /** 转换记录分析兄弟服务 */
    private final ScrmCustomerLifecycleTransitionService transitionService;

    /**
     * 创建生命周期阶段。
     *
     * @param dto 阶段参数
     * @return 创建后的阶段
     * @throws ScrmException 参数非法 / 编码重复
     */
    public ScrmLifecycleStageEntity createStage(ScrmLifecycleStageDto dto) throws ScrmException {
        return stageService.createStage(dto);
    }

    /**
     * 更新生命周期阶段 (字段非空才覆盖)。
     *
     * @param id  阶段 ID
     * @param dto 阶段参数
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在 / 参数非法 / 编码重复
     */
    public ScrmLifecycleStageEntity updateStage(Long id, ScrmLifecycleStageDto dto) throws ScrmException {
        return stageService.updateStage(id, dto);
    }

    /**
     * 删除生命周期阶段 (阶段下有客户时拒绝)。
     *
     * @param id 阶段 ID
     * @throws ScrmException 阶段不存在 / 阶段下仍有客户
     */
    public void deleteStage(Long id) throws ScrmException {
        stageService.deleteStage(id);
    }

    /**
     * 查询阶段详情。
     *
     * @param id 阶段 ID
     * @return 阶段实体
     * @throws ScrmException 阶段不存在
     */
    public ScrmLifecycleStageEntity getStage(Long id) throws ScrmException {
        return stageService.getStage(id);
    }

    /**
     * 按编码查询阶段。
     *
     * @param code 阶段编码
     * @return 阶段实体
     * @throws ScrmException 阶段不存在
     */
    public ScrmLifecycleStageEntity getStageByCode(String code) throws ScrmException {
        return stageService.getStageByCode(code);
    }

    /**
     * 分页查询阶段, 支持按类别 / 启用状态过滤。
     *
     * @param stageCategory 阶段类别过滤 (可空)
     * @param enabled       启用状态过滤 (可空)
     * @param pageable      分页参数
     * @return 阶段分页结果
     */
    public Page<ScrmLifecycleStageEntity> listStages(String stageCategory, Boolean enabled, Pageable pageable) {
        return stageService.listStages(stageCategory, enabled, pageable);
    }

    /**
     * 按阶段类别查询全部阶段 (按 stageOrder ASC)。
     *
     * @param category 阶段类别
     * @return 阶段列表
     * @throws ScrmException 阶段类别为空
     */
    public List<ScrmLifecycleStageEntity> getStagesByCategory(String category) throws ScrmException {
        return stageService.getStagesByCategory(category);
    }

    /**
     * 阶段树: 按类别分组, 每组按 stageOrder ASC。
     *
     * @return 阶段树 (类别 → 阶段列表)
     */
    public Map<String, Object> getStageTree() {
        return stageService.getStageTree();
    }

    /**
     * 查询指定阶段的下一阶段 (stageOrder 更大的阶段)。
     *
     * @param id 阶段 ID
     * @return 下一阶段列表
     * @throws ScrmException 阶段不存在
     */
    public List<ScrmLifecycleStageEntity> getNextStages(Long id) throws ScrmException {
        return stageService.getNextStages(id);
    }

    /**
     * 查询指定阶段的上一阶段 (stageOrder 更小的阶段)。
     *
     * @param id 阶段 ID
     * @return 上一阶段列表
     * @throws ScrmException 阶段不存在
     */
    public List<ScrmLifecycleStageEntity> getPreviousStages(Long id) throws ScrmException {
        return stageService.getPreviousStages(id);
    }

    /**
     * 启用阶段。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    public ScrmLifecycleStageEntity enableStage(Long id) throws ScrmException {
        return stageService.enableStage(id);
    }

    /**
     * 禁用阶段。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    public ScrmLifecycleStageEntity disableStage(Long id) throws ScrmException {
        return stageService.disableStage(id);
    }

    /**
     * 刷新阶段统计 (客户数 / 平均停留 / 转化率)。
     *
     * @param id 阶段 ID
     * @return 更新后的阶段
     * @throws ScrmException 阶段不存在
     */
    public ScrmLifecycleStageEntity updateStageStats(Long id) throws ScrmException {
        return stageService.updateStageStats(id);
    }

    /**
     * 重排阶段顺序。
     *
     * @param stageOrders 阶段 ID → 新顺序映射
     * @return 更新后的阶段列表 (按 stageOrder ASC)
     * @throws ScrmException 阶段不存在 / 顺序映射为空
     */
    public List<ScrmLifecycleStageEntity> reorderStages(Map<Long, Integer> stageOrders) throws ScrmException {
        return stageService.reorderStages(stageOrders);
    }

    /**
     * 阶段流转图: 节点 (阶段) + 边 (流转规则)。
     *
     * @return 流转图 {nodes, edges}
     */
    public Map<String, Object> getStageFlow() {
        return stageService.getStageFlow();
    }

    /**
     * 创建客户生命周期记录 (新客户初始化)。
     *
     * @param dto 生命周期参数
     * @return 创建后的生命周期
     * @throws ScrmException 参数非法
     */
    public ScrmCustomerLifecycleEntity createLifecycle(ScrmCustomerLifecycleDto dto) throws ScrmException {
        return recordService.createLifecycle(dto);
    }

    /**
     * 更新客户生命周期 (字段非空才覆盖)。
     *
     * @param id  生命周期 ID
     * @param dto 生命周期参数
     * @return 更新后的生命周期
     * @throws ScrmException 生命周期不存在
     */
    public ScrmCustomerLifecycleEntity updateLifecycle(Long id, ScrmCustomerLifecycleDto dto) throws ScrmException {
        return recordService.updateLifecycle(id, dto);
    }

    /**
     * 删除客户生命周期记录。
     *
     * @param id 生命周期 ID
     * @throws ScrmException 生命周期不存在
     */
    public void deleteLifecycle(Long id) throws ScrmException {
        recordService.deleteLifecycle(id);
    }

    /**
     * 查询生命周期详情。
     *
     * @param id 生命周期 ID
     * @return 生命周期实体
     * @throws ScrmException 生命周期不存在
     */
    public ScrmCustomerLifecycleEntity getLifecycle(Long id) throws ScrmException {
        return recordService.getLifecycle(id);
    }

    /**
     * 按客户查询生命周期。
     *
     * @param customerId 客户 ID
     * @return 生命周期实体
     * @throws ScrmException 客户生命周期不存在
     */
    public ScrmCustomerLifecycleEntity getLifecycleByCustomer(Long customerId) throws ScrmException {
        return recordService.getLifecycleByCustomer(customerId);
    }

    /**
     * 分页查询全部客户生命周期 (按 enteredCurrentStageAt DESC)。
     *
     * @param pageable 分页参数
     * @return 生命周期分页结果
     */
    public Page<ScrmCustomerLifecycleEntity> listLifecycles(Pageable pageable) {
        return recordService.listLifecycles(pageable);
    }

    /**
     * 按当前阶段编码分页查询客户生命周期。
     *
     * @param stage    阶段编码
     * @param pageable 分页参数
     * @return 生命周期分页结果
     */
    public Page<ScrmCustomerLifecycleEntity> getLifecyclesByStage(String stage, Pageable pageable) {
        return recordService.getLifecyclesByStage(stage, pageable);
    }

    /**
     * 按价值分层分页查询客户 (派生指标, 内存过滤)。
     *
     * @param valueSegment 价值分层
     * @param pageable     分页参数
     * @return 生命周期分页结果
     */
    public Page<ScrmCustomerLifecycleEntity> getLifecyclesByValueSegment(String valueSegment, Pageable pageable) {
        return recordService.getLifecyclesByValueSegment(valueSegment, pageable);
    }

    /**
     * 按风险等级分页查询客户 (派生指标, 内存过滤)。
     *
     * @param riskLevel 风险等级
     * @param pageable  分页参数
     * @return 生命周期分页结果
     */
    public Page<ScrmCustomerLifecycleEntity> getLifecyclesByRiskLevel(String riskLevel, Pageable pageable) {
        return recordService.getLifecyclesByRiskLevel(riskLevel, pageable);
    }

    /**
     * 分页查询风险客户 (流失风险 ≥ 阈值或超期)。
     *
     * @param pageable 分页参数
     * @return 风险客户分页结果
     */
    public Page<ScrmCustomerLifecycleEntity> getAtRiskCustomers(Pageable pageable) {
        return recordService.getAtRiskCustomers(pageable);
    }

    /**
     * 分页查询流失客户 (当前处于流失阶段)。
     *
     * @param pageable 分页参数
     * @return 流失客户分页结果
     */
    public Page<ScrmCustomerLifecycleEntity> getChurnedCustomers(Pageable pageable) {
        return recordService.getChurnedCustomers(pageable);
    }

    /**
     * 分页查询高价值客户 (派生指标, 内存过滤)。
     *
     * @param pageable 分页参数
     * @return 高价值客户分页结果
     */
    public Page<ScrmCustomerLifecycleEntity> getHighValueCustomers(Pageable pageable) {
        return recordService.getHighValueCustomers(pageable);
    }

    /**
     * 检索客户生命周期 (支持多条件过滤)。
     *
     * @param criteria 检索条件
     * @param pageable 分页参数
     * @return 生命周期分页结果
     */
    public Page<ScrmCustomerLifecycleEntity> searchLifecycles(Map<String, Object> criteria, Pageable pageable) {
        return recordService.searchLifecycles(criteria, pageable);
    }

    /**
     * 流转客户 (验证 → 更新阶段 → 记录历史 → 触发自动化)。
     *
     * @param request 流转请求
     * @return 更新后的客户生命周期
     * @throws ScrmException 阶段不存在 / 无可用转换规则 / 冷却期内
     */
    public ScrmCustomerLifecycleEntity transitionCustomer(ScrmLifecycleTransitionRequestDto request)
            throws ScrmException {
        return recordService.transitionCustomer(request);
    }

    /**
     * 批量流转客户阶段。
     *
     * @param customerIds 客户 ID 列表
     * @param toStage     目标阶段编码
     * @param trigger     触发原因
     * @return 转换结果列表
     * @throws ScrmException 参数非法
     */
    public List<ScrmCustomerLifecycleEntity> batchTransition(List<Long> customerIds, String toStage, String trigger)
            throws ScrmException {
        return recordService.batchTransition(customerIds, toStage, trigger);
    }

    /**
     * 自动流转: 检查条件 → 触发流转。
     *
     * @param customerId 客户 ID
     * @return 流转后的客户生命周期
     * @throws ScrmException 客户生命周期不存在
     */
    public ScrmCustomerLifecycleEntity autoTransition(Long customerId) throws ScrmException {
        return recordService.autoTransition(customerId);
    }

    /**
     * 唤醒流失客户 (从流失阶段流转到唤醒/互动阶段)。
     *
     * @param customerId 客户 ID
     * @param campaign   唤醒活动名称
     * @return 流转后的客户生命周期
     * @throws ScrmException 客户生命周期不存在 / 无可用唤醒阶段
     */
    public ScrmCustomerLifecycleEntity reactivateCustomer(Long customerId, String campaign) throws ScrmException {
        return recordService.reactivateCustomer(customerId, campaign);
    }

    /**
     * 标记客户流失 (流转到流失阶段)。
     *
     * @param customerId 客户 ID
     * @param reason     流失原因
     * @return 流转后的客户生命周期
     * @throws ScrmException 客户生命周期不存在 / 无流失阶段
     */
    public ScrmCustomerLifecycleEntity markChurned(Long customerId, String reason) throws ScrmException {
        return recordService.markChurned(customerId, reason);
    }

    /**
     * 计算并返回客户流失风险。
     *
     * @param customerId 客户 ID
     * @return 风险评分结果 Map
     * @throws ScrmException 客户生命周期不存在
     */
    public Map<String, Object> updateChurnRisk(Long customerId) throws ScrmException {
        return analyticsService.updateChurnRisk(customerId);
    }

    /**
     * 计算并返回客户 LTV。
     *
     * @param customerId 客户 ID
     * @return LTV 估算结果 Map
     * @throws ScrmException 客户生命周期不存在
     */
    public Map<String, Object> updateLTV(Long customerId) throws ScrmException {
        return analyticsService.updateLTV(customerId);
    }

    /**
     * 计算并返回客户活跃度评分。
     *
     * @param customerId 客户 ID
     * @return 活跃度评分结果 Map
     * @throws ScrmException 客户生命周期不存在
     */
    public Map<String, Object> updateEngagementScore(Long customerId) throws ScrmException {
        return analyticsService.updateEngagementScore(customerId);
    }

    /**
     * 重算所有客户评分 (流失风险 / LTV / 活跃度)。
     *
     * @return 重算汇总 Map
     */
    public Map<String, Object> recalculateAllScores() {
        return analyticsService.recalculateAllScores();
    }

    /**
     * 生命周期时间线 (最近 N 月的转换记录)。
     *
     * @param customerId 客户 ID
     * @param months     月数
     * @return 时间线列表
     * @throws ScrmException 客户 ID 非法
     */
    public List<Map<String, Object>> getLifecycleTimeline(Long customerId, int months) throws ScrmException {
        return analyticsService.getLifecycleTimeline(customerId, months);
    }

    /**
     * 下一步最佳行动 (基于当前阶段与流失风险)。
     *
     * @param customerId 客户 ID
     * @return 行动建议 Map
     * @throws ScrmException 客户生命周期不存在
     */
    public Map<String, Object> getNextBestAction(Long customerId) throws ScrmException {
        return analyticsService.getNextBestAction(customerId);
    }

    /**
     * 客户旅程 (按时间升序的转换历史)。
     *
     * @param customerId 客户 ID
     * @return 旅程节点列表
     * @throws ScrmException 客户 ID 非法
     */
    public List<Map<String, Object>> getCustomerJourney(Long customerId) throws ScrmException {
        return analyticsService.getCustomerJourney(customerId);
    }

    /**
     * 查询转换记录详情。
     *
     * @param id 转换记录 ID
     * @return 转换历史实体
     * @throws ScrmException 转换记录不存在
     */
    public ScrmLifecycleHistoryEntity getTransition(Long id) throws ScrmException {
        return transitionService.getTransition(id);
    }

    /**
     * 查询客户全部转换记录 (按时间降序)。
     *
     * @param customerId 客户 ID
     * @return 转换记录列表
     * @throws ScrmException 客户 ID 非法
     */
    public List<ScrmLifecycleHistoryEntity> getTransitionsByCustomer(Long customerId) throws ScrmException {
        return transitionService.getTransitionsByCustomer(customerId);
    }

    /**
     * 按阶段分页查询转换记录 (源或目标阶段匹配)。
     *
     * @param stage    阶段编码
     * @param pageable 分页参数
     * @return 转换记录分页结果
     */
    public Page<ScrmLifecycleHistoryEntity> getTransitionsByStage(String stage, Pageable pageable) {
        return transitionService.getTransitionsByStage(stage, pageable);
    }

    /**
     * 按流转类型分页查询转换记录。
     *
     * @param type     流转类型
     * @param pageable 分页参数
     * @return 转换记录分页结果
     */
    public Page<ScrmLifecycleHistoryEntity> getTransitionsByType(String type, Pageable pageable) {
        return transitionService.getTransitionsByType(type, pageable);
    }

    /**
     * 按时间区间分页查询转换记录。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @param pageable  分页参数
     * @return 转换记录分页结果
     */
    public Page<ScrmLifecycleHistoryEntity> getTransitionsByDateRange(LocalDateTime startTime,
                                                                       LocalDateTime endTime, Pageable pageable) {
        return transitionService.getTransitionsByDateRange(startTime, endTime, pageable);
    }

    /**
     * 撤销流转: 将客户回退到源阶段。
     *
     * @param id         转换记录 ID
     * @param reason     撤销原因
     * @param reversedBy 撤销人
     * @return 撤销结果 Map
     * @throws ScrmException 转换记录不存在 / 无法撤销
     */
    public Map<String, Object> reverseTransition(Long id, String reason, String reversedBy) throws ScrmException {
        return transitionService.reverseTransition(id, reason, reversedBy);
    }

    /**
     * 转换统计概览。
     *
     * @return 统计结果 Map
     */
    public Map<String, Object> getTransitionStats() {
        return transitionService.getTransitionStats();
    }

    /**
     * 分析流转模式: 各阶段转化率 / 平均停留 / 流失率。
     *
     * @return 模式分析结果 Map
     */
    public Map<String, Object> analyzeTransitionPatterns() {
        return transitionService.analyzeTransitionPatterns();
    }

    /**
     * 识别瓶颈阶段 (转化率低或超期客户多)。
     *
     * @return 瓶颈阶段列表
     */
    public List<Map<String, Object>> getBottleneckStages() {
        return transitionService.getBottleneckStages();
    }

    /**
     * 转化漏斗 (各阶段 → 下一阶段转化率)。
     *
     * @return 漏斗数据列表
     */
    public List<Map<String, Object>> getConversionFunnel() {
        return transitionService.getConversionFunnel();
    }

    /**
     * 生命周期统计概览。
     *
     * @return 统计结果 Map
     */
    public Map<String, Object> getLifecycleOverview() {
        return analyticsService.getLifecycleOverview();
    }

    /**
     * 生命周期统计 (各阶段客户数 / 分布 / 平均停留)。
     *
     * @return 统计结果 Map
     */
    public Map<String, Object> getLifecycleStats() {
        return analyticsService.getLifecycleStats();
    }

    /**
     * 阶段分布统计。
     *
     * @return 阶段分布列表
     */
    public List<Map<String, Object>> getStageDistribution() {
        return analyticsService.getStageDistribution();
    }

    /**
     * 价值分层分布 (派生指标, 内存计算)。
     *
     * @return 价值分层分布 Map
     */
    public Map<String, Object> getValueSegmentDistribution() {
        return analyticsService.getValueSegmentDistribution();
    }

    /**
     * 风险等级分布 (派生指标, 内存计算)。
     *
     * @return 风险等级分布 Map
     */
    public Map<String, Object> getRiskLevelDistribution() {
        return analyticsService.getRiskLevelDistribution();
    }

    /**
     * 整体流失率 (流失客户数 / 总客户数)。
     *
     * @return 流失率统计 Map
     */
    public Map<String, Object> getChurnRate() {
        return analyticsService.getChurnRate();
    }

    /**
     * 整体留存率 (1 - 流失率)。
     *
     * @return 留存率统计 Map
     */
    public Map<String, Object> getRetentionRate() {
        return analyticsService.getRetentionRate();
    }

    /**
     * 平均生命周期时长 (基于转换历史的平均停留天数)。
     *
     * @return 平均周期统计 Map
     */
    public Map<String, Object> getAverageLifecycleDuration() {
        return analyticsService.getAverageLifecycleDuration();
    }

    /**
     * 各阶段转化率。
     *
     * @return 阶段转化率列表
     */
    public List<Map<String, Object>> getStageConversionRates() {
        return analyticsService.getStageConversionRates();
    }

    /**
     * 获客渠道统计 (实体未承载渠道字段, 返回空结构占位)。
     *
     * @return 渠道统计 Map
     */
    public Map<String, Object> getAcquisitionChannelStats() {
        return analyticsService.getAcquisitionChannelStats();
    }

    /**
     * 生命周期趋势 (最近 N 月每月转换数)。
     *
     * @param months 月数
     * @return 趋势数据列表
     */
    public List<Map<String, Object>> getLifecycleTrend(int months) {
        return analyticsService.getLifecycleTrend(months);
    }
}
