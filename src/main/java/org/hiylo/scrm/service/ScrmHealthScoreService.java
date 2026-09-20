/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthScoreService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmHealthAlertActionDto;
import org.hiylo.scrm.dto.ScrmHealthAlertDto;
import org.hiylo.scrm.dto.ScrmHealthCalculateDto;
import org.hiylo.scrm.entity.ScrmCustomerHealthScoreEntity;
import org.hiylo.scrm.entity.ScrmHealthAlertEntity;
import org.hiylo.scrm.entity.ScrmHealthScoreModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户健康度评分服务 (门面)。
 * <p>
 * 作为客户健康度评分模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给下列
 * 子域服务:
 * {@link ScrmHealthScoreModelService} (模型管理)、{@link ScrmHealthScoreCalculationService} (评分计算)、
 * {@link ScrmHealthScoreAlertService} (告警管理) 与 {@link ScrmHealthScoreStatsService} (统计趋势)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmHealthScoreService {

    /** 健康度模型子域服务 */
    private final ScrmHealthScoreModelService modelService;
    /** 健康度评分计算子域服务 */
    private final ScrmHealthScoreCalculationService calculationService;
    /** 健康度告警子域服务 */
    private final ScrmHealthScoreAlertService alertService;
    /** 健康度统计趋势子域服务 */
    private final ScrmHealthScoreStatsService statsService;

    /**
     * 创建健康度模型。
     * <p>校验 scoringType / metrics 合法性与 modelCode 唯一性后写入账号 ID 持久化,
     * scoringType / totalMaxScore / versionNo / isDefault / isPublished / appliedCount /
     * updateFrequency 缺省时填默认值。</p>
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / modelCode 重复
     */
    public ScrmHealthScoreModelEntity createModel(org.hiylo.scrm.dto.ScrmHealthScoreModelDto dto)
            throws ScrmException {
        return modelService.createModel(dto);
    }

    /**
     * 更新健康度模型（字段非空才覆盖）。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / modelCode 重复
     */
    public ScrmHealthScoreModelEntity updateModel(Long id,
                                                  org.hiylo.scrm.dto.ScrmHealthScoreModelDto dto)
            throws ScrmException {
        return modelService.updateModel(id, dto);
    }

    /**
     * 删除健康度模型。
     *
     * @param id 模型 ID
     * @throws ScrmException 模型不存在
     */
    public void deleteModel(Long id) throws ScrmException {
        modelService.deleteModel(id);
    }

    /**
     * 查询模型详情。
     *
     * @param id 模型 ID
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    public ScrmHealthScoreModelEntity getModel(Long id) throws ScrmException {
        return modelService.getModel(id);
    }

    /**
     * 按模型编码查询模型。
     *
     * @param code 模型编码
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    public ScrmHealthScoreModelEntity getModelByCode(String code) throws ScrmException {
        return modelService.getModelByCode(code);
    }

    /**
     * 分页查询模型, 支持按发布状态 / 关键字过滤。
     *
     * @param isPublished 发布状态过滤（可空）
     * @param keyword     模型名称关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 模型分页结果 (按 createTime DESC)
     */
    public Page<ScrmHealthScoreModelEntity> listModels(Boolean isPublished, String keyword, Pageable pageable) {
        return modelService.listModels(isPublished, keyword, pageable);
    }

    /**
     * 发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmHealthScoreModelEntity publishModel(Long id) throws ScrmException {
        return modelService.publishModel(id);
    }

    /**
     * 取消发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmHealthScoreModelEntity unpublishModel(Long id) throws ScrmException {
        return modelService.unpublishModel(id);
    }

    /**
     * 设置为默认模型 (清理旧默认)。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmHealthScoreModelEntity setDefault(Long id) throws ScrmException {
        return modelService.setDefault(id);
    }

    /**
     * 复制模型 (深拷贝模型配置, 新模型默认未发布且非默认, modelCode 加 _copy 后缀)。
     *
     * @param id 源模型 ID
     * @return 复制后的新模型
     * @throws ScrmException 源模型不存在 / modelCode 冲突
     */
    public ScrmHealthScoreModelEntity copyModel(Long id) throws ScrmException {
        return modelService.copyModel(id);
    }

    /**
     * 验证模型配置 (校验 metrics JSON 合法性 / 指标权重 / maxScore 总和等)。
     *
     * @param id 模型 ID
     * @return 验证结果 Map: {valid, issues, metricCount, totalWeight, totalMaxScore}
     * @throws ScrmException 模型不存在
     */
    public Map<String, Object> validateModel(Long id) throws ScrmException {
        return modelService.validateModel(id);
    }

    /**
     * 计算单客户健康度评分 (加载模型→收集指标数据→逐指标评分→汇总→确定等级→生成建议)。
     * <p>完整实现: 解析 metrics 配置, 评估每个指标的 scoringRules, 按 scoringType 汇总得分,
     * 根据阈值确定健康等级, 计算风险等级 / 风险因素 / 建议动作, 更新评分记录 (含趋势对比)。
     * 若该客户在该模型下已有评分记录且未强制重算, 则按频率判断是否跳过。</p>
     *
     * @param calculateDto 计算参数 (customerId + modelId + forceRecalculate)
     * @return 健康度评分记录
     * @throws ScrmException 客户 / 模型不存在
     */
    public ScrmCustomerHealthScoreEntity calculateHealthScore(ScrmHealthCalculateDto calculateDto)
            throws ScrmException {
        return calculationService.calculateHealthScore(calculateDto);
    }

    /**
     * 批量计算客户健康度评分。
     * <p>单个客户失败跳过, 不阻断其他客户。</p>
     *
     * @param customerIds 客户 ID 列表
     * @param modelId     模型 ID
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    public Map<String, Integer> batchCalculate(List<Long> customerIds, Long modelId) throws ScrmException {
        return calculationService.batchCalculate(customerIds, modelId);
    }

    /**
     * 计算所有客户健康度评分 (按下所有客户)。
     *
     * @param modelId 模型 ID
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    public Map<String, Integer> calculateAll(Long modelId) throws ScrmException {
        return calculationService.calculateAll(modelId);
    }

    /**
     * 重新计算指定评分记录 (按评分记录的 customerId 与 modelId 重算, 强制重算)。
     *
     * @param scoreId 评分记录 ID
     * @return 健康度评分记录
     * @throws ScrmException 评分记录不存在
     */
    public ScrmCustomerHealthScoreEntity recalculate(Long scoreId) throws ScrmException {
        return calculationService.recalculate(scoreId);
    }

    /**
     * 查询评分详情。
     *
     * @param id 评分记录 ID
     * @return 评分实体
     * @throws ScrmException 评分记录不存在
     */
    public ScrmCustomerHealthScoreEntity getScore(Long id) throws ScrmException {
        return calculationService.getScore(id);
    }

    /**
     * 按客户与模型查询评分。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 评分实体 (不存在返回 null)
     */
    public ScrmCustomerHealthScoreEntity getScoreByCustomer(Long customerId, Long modelId) {
        return calculationService.getScoreByCustomer(customerId, modelId);
    }

    /**
     * 分页查询评分, 支持按健康等级 / 风险客户 / 风险等级 / 分数区间过滤与排序。
     *
     * @param healthLevel 健康等级过滤（可空）
     * @param isAtRisk    风险客户过滤（可空）
     * @param riskLevel   风险等级过滤（可空）
     * @param minScore    最低分过滤（可空）
     * @param maxScore    最高分过滤（可空）
     * @param sortBy      排序字段: totalScore / calculatedAt / scorePercent（可空, 默认 totalScore）
     * @param pageable    分页参数
     * @return 评分分页结果
     */
    public Page<ScrmCustomerHealthScoreEntity> listScores(String healthLevel, Boolean isAtRisk, String riskLevel,
                                                          Double minScore, Double maxScore, String sortBy,
                                                          Pageable pageable) {
        return calculationService.listScores(healthLevel, isAtRisk, riskLevel, minScore, maxScore, sortBy, pageable);
    }

    /**
     * 风险客户列表 (按 totalScore ASC, 分越低越危险)。
     *
     * @param limit 返回数量
     * @return 评分列表
     */
    public List<ScrmCustomerHealthScoreEntity> getAtRiskCustomers(int limit) {
        return calculationService.getAtRiskCustomers(limit);
    }

    /**
     * 危急客户列表 (健康等级为 CRITICAL, 按 totalScore ASC)。
     *
     * @param limit 返回数量
     * @return 评分列表
     */
    public List<ScrmCustomerHealthScoreEntity> getCriticalCustomers(int limit) {
        return calculationService.getCriticalCustomers(limit);
    }

    /**
     * 根据分数与健康阈值确定健康等级。
     * <p>healthThresholds 非空时按其配置 (JSON: [{level, minScore, maxScore, color, action}]) 匹配;
     * 否则按默认阈值 (基于分数百分比): ≥85% EXCELLENT, ≥70% HEALTHY, ≥50% NEUTRAL,
     * ≥30% AT_RISK, 否则 CRITICAL。</p>
     *
     * @param score      分数
     * @param maxScore   满分
     * @param thresholds 健康阈值 JSON (可空)
     * @return 健康等级
     */
    public String determineLevel(double score, double maxScore, String thresholds) {
        return calculationService.determineLevel(score, maxScore, thresholds);
    }

    /**
     * 等级分布统计 (按模型)。
     * <p>返回各健康等级客户数, 含全部默认等级 (无客户的等级为 0)。</p>
     *
     * @param modelId 模型 ID
     * @return 等级分布 Map
     * @throws ScrmException 模型不存在
     */
    public Map<String, Object> getLevelDistribution(Long modelId) throws ScrmException {
        return statsService.getLevelDistribution(modelId);
    }

    /**
     * 分数分布统计 (按模型)。
     * <p>按分数区间 [0,20), [20,40), [40,60), [60,80), [80,100] 聚合客户数。</p>
     *
     * @param modelId 模型 ID
     * @return 分数分布 Map
     * @throws ScrmException 模型不存在
     */
    public Map<String, Object> getScoreDistribution(Long modelId) throws ScrmException {
        return statsService.getScoreDistribution(modelId);
    }

    /**
     * 风险分布统计 (按模型)。
     * <p>返回各风险等级客户数, 含全部默认风险等级 (无客户的等级为 0)。</p>
     *
     * @param modelId 模型 ID
     * @return 风险分布 Map
     * @throws ScrmException 模型不存在
     */
    public Map<String, Object> getRiskDistribution(Long modelId) throws ScrmException {
        return statsService.getRiskDistribution(modelId);
    }

    /**
     * 创建告警。
     *
     * @param dto 告警参数
     * @return 创建后的告警
     * @throws ScrmException 参数非法
     */
    public ScrmHealthAlertEntity createAlert(ScrmHealthAlertDto dto) throws ScrmException {
        return alertService.createAlert(dto);
    }

    /**
     * 检查客户健康度并生成告警 (评分下降 / 低分 / 不活跃 / 流失风险 / 阈值突破等)。
     * <p>基于客户最新评分与上下文生成多条告警, 已存在的同类活跃告警不重复生成。</p>
     *
     * @param customerId 客户 ID
     * @return 生成的告警列表
     * @throws ScrmException 客户不存在
     */
    public List<ScrmHealthAlertEntity> checkAndGenerateAlerts(Long customerId) throws ScrmException {
        return alertService.checkAndGenerateAlerts(customerId);
    }

    /**
     * 查询告警详情。
     *
     * @param id 告警 ID
     * @return 告警实体
     * @throws ScrmException 告警不存在
     */
    public ScrmHealthAlertEntity getAlert(Long id) throws ScrmException {
        return alertService.getAlert(id);
    }

    /**
     * 分页查询告警, 支持按客户 / 告警类型 / 严重度 / 状态 / 时间区间过滤。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param alertType  告警类型过滤（可空）
     * @param severity   严重度过滤（可空）
     * @param status     状态过滤（可空）
     * @param startTime  触发起始时间（可空）
     * @param endTime    触发截止时间（可空）
     * @param pageable   分页参数
     * @return 告警分页结果 (按 triggeredAt DESC)
     */
    public Page<ScrmHealthAlertEntity> listAlerts(Long customerId, String alertType, String severity, String status,
                                                  LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        return alertService.listAlerts(customerId, alertType, severity, status, startTime, endTime, pageable);
    }

    /**
     * 活跃告警列表 (status = ACTIVE, 按 triggeredAt DESC)。
     *
     * @param limit 返回数量
     * @return 告警列表
     */
    public List<ScrmHealthAlertEntity> getActiveAlerts(int limit) {
        return alertService.getActiveAlerts(limit);
    }

    /**
     * 确认告警 (ACTIVE → ACKNOWLEDGED)。
     *
     * @param actionDto 动作参数 (alertId + note + assigneeId)
     * @return 更新后的告警
     * @throws ScrmException 告警不存在 / 状态非法
     */
    public ScrmHealthAlertEntity acknowledgeAlert(ScrmHealthAlertActionDto actionDto) throws ScrmException {
        return alertService.acknowledgeAlert(actionDto);
    }

    /**
     * 解决告警 (ACKNOWLEDGED → RESOLVED)。
     *
     * @param actionDto 动作参数 (alertId + note + assigneeId)
     * @return 更新后的告警
     * @throws ScrmException 告警不存在 / 状态非法
     */
    public ScrmHealthAlertEntity resolveAlert(ScrmHealthAlertActionDto actionDto) throws ScrmException {
        return alertService.resolveAlert(actionDto);
    }

    /**
     * 忽略告警 (任意状态 → DISMISSED)。
     *
     * @param actionDto 动作参数 (alertId + note + assigneeId)
     * @return 更新后的告警
     * @throws ScrmException 告警不存在
     */
    public ScrmHealthAlertEntity dismissAlert(ScrmHealthAlertActionDto actionDto) throws ScrmException {
        return alertService.dismissAlert(actionDto);
    }

    /**
     * 分配告警给负责人。
     *
     * @param alertId    告警 ID
     * @param assigneeId 负责人用户标识
     * @return 更新后的告警
     * @throws ScrmException 告警不存在
     */
    public ScrmHealthAlertEntity assignAlert(Long alertId, String assigneeId) throws ScrmException {
        return alertService.assignAlert(alertId, assigneeId);
    }

    /**
     * 批量检查所有风险客户的告警 (遍历账号下所有风险客户, 逐一检查并生成告警)。
     *
     * @return 检查结果: {checked, generated}
     */
    public Map<String, Integer> batchCheckAlerts() {
        return alertService.batchCheckAlerts();
    }

    /**
     * 评分趋势: 返回最近 days 天每日的总分均值。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @param days       天数
     * @return 趋势列表 (按日期升序)
     */
    public List<Map<String, Object>> getScoreTrend(Long customerId, Long modelId, int days) {
        return statsService.getScoreTrend(customerId, modelId, days);
    }

    /**
     * 趋势分析: 改善 / 下降 / 稳定。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 趋势分析 Map: {trend, change, currentScore, previousScore, analysis}
     */
    public Map<String, Object> getTrendAnalysis(Long customerId, Long modelId) {
        return statsService.getTrendAnalysis(customerId, modelId);
    }

    /**
     * 对比分析: 与客群 / 同期 / 上次对比。
     *
     * @param customerId   客户 ID
     * @param modelId      模型 ID
     * @param compareWith  对比维度: SEGMENT / PERIOD / PREVIOUS
     * @return 对比分析 Map
     */
    public Map<String, Object> getComparison(Long customerId, Long modelId, String compareWith) {
        return statsService.getComparison(customerId, modelId, compareWith);
    }

    /**
     * 健康度统计: 总数 / 风险客户数 / 平均分 / 各等级分布。
     *
     * @param startTime 起始时间 (含, 可空, 按 calculatedAt 过滤)
     * @param endTime   截止时间 (含, 可空, 按 calculatedAt 过滤)
     * @return 统计结果 Map
     */
    public Map<String, Object> getHealthStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getHealthStats(startTime, endTime);
    }

    /**
     * 告警统计: 总数 / 已解决数 / 已确认数 / 解决率 / 各类型 / 各严重度。
     *
     * @param startTime 起始时间 (含, 可空, 按 triggeredAt 过滤)
     * @param endTime   截止时间 (含, 可空, 按 triggeredAt 过滤)
     * @return 告警统计 Map
     */
    public Map<String, Object> getAlertStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getAlertStats(startTime, endTime);
    }

    /**
     * 指标统计: 按模型与指标编码统计平均分 / 命中数 / 分布。
     *
     * @param modelId    模型 ID
     * @param metricCode 指标编码
     * @return 指标统计 Map
     * @throws ScrmException 模型不存在
     */
    public Map<String, Object> getMetricStats(Long modelId, String metricCode) throws ScrmException {
        return statsService.getMetricStats(modelId, metricCode);
    }

    /**
     * 健康度趋势: 返回最近 days 天每日的平均分 / 风险客户数。
     *
     * @param days 天数
     * @return 趋势列表 (按日期升序)
     */
    public List<Map<String, Object>> getHealthTrend(int days) {
        return statsService.getHealthTrend(days);
    }

    /**
     * 风险分析: 时间区间内的风险等级分布 / 流失风险客户数 / 主要风险因素。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 风险分析 Map
     */
    public Map<String, Object> getRiskAnalysis(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getRiskAnalysis(startTime, endTime);
    }
}