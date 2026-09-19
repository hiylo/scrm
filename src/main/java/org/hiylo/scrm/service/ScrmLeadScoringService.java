/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadScoringService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmLeadAssignDto;
import org.hiylo.scrm.dto.ScrmLeadDimensionDto;
import org.hiylo.scrm.dto.ScrmLeadScoreResultDto;
import org.hiylo.scrm.dto.ScrmLeadScoringModelDto;
import org.hiylo.scrm.entity.ScrmLeadDimensionEntity;
import org.hiylo.scrm.entity.ScrmLeadScoreEntity;
import org.hiylo.scrm.entity.ScrmLeadScoringModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 销售线索评分服务 (门面)。
 * <p>
 * 作为销售线索评分模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmLeadScoringModelService} (评分模型管理)、{@link ScrmLeadDimensionService} (评分维度管理)、
 * {@link ScrmLeadScoringCalculationService} (评分计算 / 等级判定 / 转化预测) 与
 * {@link ScrmLeadAssignmentService} (线索分配与统计)。
 * </p>
 * <p>
 * 评分计算与转化预测均为模拟实现, 基于规则简单匹配, 不依赖外部 ML 服务。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmLeadScoringService {

    /** 评分模型管理子域服务 */
    private final ScrmLeadScoringModelService modelService;
    /** 评分维度管理子域服务 */
    private final ScrmLeadDimensionService dimensionService;
    /** 评分计算 / 等级判定 / 转化预测子域服务 */
    private final ScrmLeadScoringCalculationService calculationService;
    /** 线索分配与统计子域服务 */
    private final ScrmLeadAssignmentService assignmentService;

    // ============================================================
    // 模型管理
    // ============================================================

    /**
     * 创建评分模型。
     * <p>校验 modelType / dimensions 合法性与 modelCode 唯一性后写入账号 ID 持久化,
     * modelType / totalMaxScore / versionNo / isDefault / isPublished / appliedCount 缺省时填默认值。</p>
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / modelCode 重复
     */
    public ScrmLeadScoringModelEntity createModel(ScrmLeadScoringModelDto dto) throws ScrmException {
        return modelService.createModel(dto);
    }

    /**
     * 更新评分模型（字段非空才覆盖）。
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法 / modelCode 重复
     */
    public ScrmLeadScoringModelEntity updateModel(Long id, ScrmLeadScoringModelDto dto) throws ScrmException {
        return modelService.updateModel(id, dto);
    }

    /**
     * 删除评分模型。
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
    public ScrmLeadScoringModelEntity getModel(Long id) throws ScrmException {
        return modelService.getModel(id);
    }

    /**
     * 按模型编码查询模型。
     *
     * @param code 模型编码
     * @return 模型实体
     * @throws ScrmException 模型不存在
     */
    public ScrmLeadScoringModelEntity getModelByCode(String code) throws ScrmException {
        return modelService.getModelByCode(code);
    }

    /**
     * 分页查询模型, 支持按模型类型 / 发布状态 / 关键字过滤。
     *
     * @param modelType   模型类型过滤（可空）
     * @param isPublished 发布状态过滤（可空）
     * @param keyword     模型名称关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 模型分页结果 (按 createTime DESC)
     */
    public Page<ScrmLeadScoringModelEntity> listModels(String modelType, Boolean isPublished,
                                                        String keyword, Pageable pageable) {
        return modelService.listModels(modelType, isPublished, keyword, pageable);
    }

    /**
     * 发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmLeadScoringModelEntity publishModel(Long id) throws ScrmException {
        return modelService.publishModel(id);
    }

    /**
     * 取消发布模型。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmLeadScoringModelEntity unpublishModel(Long id) throws ScrmException {
        return modelService.unpublishModel(id);
    }

    /**
     * 设置为默认模型 (清理旧默认)。
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmLeadScoringModelEntity setDefaultModel(Long id) throws ScrmException {
        return modelService.setDefaultModel(id);
    }

    /**
     * 复制模型 (深拷贝模型配置, 新模型默认未发布且非默认, modelCode 加 _copy 后缀)。
     *
     * @param id 源模型 ID
     * @return 复制后的新模型
     * @throws ScrmException 源模型不存在 / modelCode 冲突
     */
    public ScrmLeadScoringModelEntity copyModel(Long id) throws ScrmException {
        return modelService.copyModel(id);
    }

    // ============================================================
    // 维度管理
    // ============================================================

    /**
     * 创建评分维度。
     * <p>校验 dimensionCategory / scoringRules 合法性与 dimensionCode 唯一性后写入账号 ID 持久化,
     * defaultWeight / defaultMaxScore / enabled / usageCount 缺省时填默认值。</p>
     *
     * @param dto 维度参数
     * @return 创建后的维度
     * @throws ScrmException 参数非法 / dimensionCode 重复
     */
    public ScrmLeadDimensionEntity createDimension(ScrmLeadDimensionDto dto) throws ScrmException {
        return dimensionService.createDimension(dto);
    }

    /**
     * 更新评分维度（字段非空才覆盖）。
     *
     * @param id  维度 ID
     * @param dto 维度参数
     * @return 更新后的维度
     * @throws ScrmException 维度不存在 / 参数非法 / dimensionCode 重复
     */
    public ScrmLeadDimensionEntity updateDimension(Long id, ScrmLeadDimensionDto dto) throws ScrmException {
        return dimensionService.updateDimension(id, dto);
    }

    /**
     * 删除评分维度。
     *
     * @param id 维度 ID
     * @throws ScrmException 维度不存在
     */
    public void deleteDimension(Long id) throws ScrmException {
        dimensionService.deleteDimension(id);
    }

    /**
     * 查询维度详情。
     *
     * @param id 维度 ID
     * @return 维度实体
     * @throws ScrmException 维度不存在
     */
    public ScrmLeadDimensionEntity getDimension(Long id) throws ScrmException {
        return dimensionService.getDimension(id);
    }

    /**
     * 按维度编码查询维度。
     *
     * @param code 维度编码
     * @return 维度实体
     * @throws ScrmException 维度不存在
     */
    public ScrmLeadDimensionEntity getDimensionByCode(String code) throws ScrmException {
        return dimensionService.getDimensionByCode(code);
    }

    /**
     * 分页查询维度, 支持按维度类别 / 启用状态 / 关键字过滤。
     *
     * @param dimensionCategory 维度类别过滤（可空）
     * @param enabled           启用状态过滤（可空）
     * @param keyword           维度名称关键字模糊匹配（可空）
     * @param pageable          分页参数
     * @return 维度分页结果 (按 createTime DESC)
     */
    public Page<ScrmLeadDimensionEntity> listDimensions(String dimensionCategory, Boolean enabled,
                                                         String keyword, Pageable pageable) {
        return dimensionService.listDimensions(dimensionCategory, enabled, keyword, pageable);
    }

    /**
     * 启用维度。
     *
     * @param id 维度 ID
     * @return 更新后的维度
     * @throws ScrmException 维度不存在
     */
    public ScrmLeadDimensionEntity enableDimension(Long id) throws ScrmException {
        return dimensionService.enableDimension(id);
    }

    /**
     * 禁用维度。
     *
     * @param id 维度 ID
     * @return 更新后的维度
     * @throws ScrmException 维度不存在
     */
    public ScrmLeadDimensionEntity disableDimension(Long id) throws ScrmException {
        return dimensionService.disableDimension(id);
    }

    // ============================================================
    // 评分计算
    // ============================================================

    /**
     * 计算单客户线索评分 (遍历维度→评估规则→汇总得分→确定等级→计算转化概率)。
     * <p>模拟实现, 基于规则简单匹配。若该客户在该模型下已有评分记录, 则更新 (含趋势对比);
     * 否则新建评分记录。同时增量更新模型应用次数与最近应用时间。</p>
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 评分计算结果
     * @throws ScrmException 客户 / 模型不存在
     */
    public ScrmLeadScoreResultDto calculateScore(Long customerId, Long modelId) throws ScrmException {
        return calculationService.calculateScore(customerId, modelId);
    }

    /**
     * 批量计算客户线索评分。
     * <p>单个客户失败跳过, 不阻断其他客户。</p>
     *
     * @param modelId     模型 ID
     * @param customerIds 客户 ID 列表
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    public Map<String, Integer> batchCalculateScores(Long modelId, List<Long> customerIds) throws ScrmException {
        return calculationService.batchCalculateScores(modelId, customerIds);
    }

    /**
     * 计算所有客户评分 (按下所有客户)。
     *
     * @param modelId 模型 ID
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    public Map<String, Integer> calculateAllScores(Long modelId) throws ScrmException {
        return calculationService.calculateAllScores(modelId);
    }

    /**
     * 重新计算指定评分记录 (按评分记录的 customerId 与 modelId 重算)。
     *
     * @param scoreId 评分记录 ID
     * @return 评分计算结果
     * @throws ScrmException 评分记录不存在
     */
    public ScrmLeadScoreResultDto recalculateScore(Long scoreId) throws ScrmException {
        return calculationService.recalculateScore(scoreId);
    }

    /**
     * 查询评分详情。
     *
     * @param scoreId 评分记录 ID
     * @return 评分实体
     * @throws ScrmException 评分记录不存在
     */
    public ScrmLeadScoreEntity getScore(Long scoreId) throws ScrmException {
        return calculationService.getScore(scoreId);
    }

    /**
     * 按客户与模型查询评分。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 评分实体 (不存在返回 null)
     */
    public ScrmLeadScoreEntity getScoreByCustomer(Long customerId, Long modelId) {
        return calculationService.getScoreByCustomer(customerId, modelId);
    }

    /**
     * 分页查询评分, 支持按等级 / 热线索 / 合格 / 已转化 / 模型 / 分数区间过滤与排序。
     *
     * @param grade       等级过滤（可空）
     * @param isHotLead   热线索过滤（可空）
     * @param isQualified 合格线索过滤（可空）
     * @param isConverted 已转化过滤（可空）
     * @param modelId     模型 ID 过滤（可空）
     * @param minScore    最低分过滤（可空）
     * @param maxScore    最高分过滤（可空）
     * @param sortBy      排序字段: totalScore / conversionProbability / lastCalculatedAt（可空, 默认 totalScore）
     * @param pageable    分页参数
     * @return 评分分页结果
     */
    public Page<ScrmLeadScoreEntity> listScores(String grade, Boolean isHotLead, Boolean isQualified,
                                                 Boolean isConverted, Long modelId, Double minScore, Double maxScore,
                                                 String sortBy, Pageable pageable) {
        return assignmentService.listScores(grade, isHotLead, isQualified, isConverted, modelId, minScore, maxScore,
                sortBy, pageable);
    }

    /**
     * 热线索列表 (按模型过滤, 缺省使用账号下全部)。
     *
     * @param modelId 模型 ID（可空, 缺省取全部）
     * @param limit   返回数量
     * @return 评分列表 (按 totalScore DESC)
     */
    public List<ScrmLeadScoreEntity> getHotLeads(Long modelId, int limit) {
        return assignmentService.getHotLeads(modelId, limit);
    }

    /**
     * 合格线索列表 (按模型过滤, 缺省使用账号下全部)。
     *
     * @param modelId 模型 ID（可空, 缺省取全部）
     * @param limit   返回数量
     * @return 评分列表 (按 totalScore DESC)
     */
    public List<ScrmLeadScoreEntity> getQualifiedLeads(Long modelId, int limit) {
        return assignmentService.getQualifiedLeads(modelId, limit);
    }

    // ============================================================
    // 等级
    // ============================================================

    /**
     * 根据分数与等级阈值确定等级。
     * <p>gradeThresholds 非空时按其配置 (JSON: [{grade, minScore, maxScore, color}]) 匹配;
     * 否则按默认阈值 (基于分数百分比): ≥85% A_PLUS, ≥70% SUPER_HOT, ≥55% HOT,
     * ≥40% WARM, ≥20% COLD, 否则 DEAD。</p>
     *
     * @param score      分数
     * @param maxScore   满分
     * @param thresholds 等级阈值 JSON (可空)
     * @return 等级编码
     */
    public String determineGrade(double score, double maxScore, String thresholds) {
        return calculationService.determineGrade(score, maxScore, thresholds);
    }

    /**
     * 等级分布统计 (按模型)。
     * <p>返回各等级客户数, 含全部默认等级 (无客户的等级为 0)。</p>
     *
     * @param modelId 模型 ID
     * @return 等级分布 Map
     * @throws ScrmException 模型不存在
     */
    public Map<String, Object> getGradeDistribution(Long modelId) throws ScrmException {
        return assignmentService.getGradeDistribution(modelId);
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
        return assignmentService.getScoreDistribution(modelId);
    }

    // ============================================================
    // 转化预测
    // ============================================================

    /**
     * 预测客户转化概率 (模拟, 基于分数和维度)。
     * <p>调用 {@link #calculateScore} 重算后返回转化概率, 若评分记录不存在则按 0 处理。</p>
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 转化概率 (0-1)
     * @throws ScrmException 客户 / 模型不存在
     */
    public Double predictConversion(Long customerId, Long modelId) throws ScrmException {
        return calculationService.predictConversion(customerId, modelId);
    }

    /**
     * 转化统计: 总线索数 / 已转化数 / 转化率 / 平均转化概率 / 总转化价值。
     *
     * @param modelId   模型 ID
     * @param startTime 起始时间 (含, 可空, 按 lastCalculatedAt 过滤)
     * @param endTime   截止时间 (含, 可空, 按 lastCalculatedAt 过滤)
     * @return 转化统计 Map
     * @throws ScrmException 模型不存在
     */
    public Map<String, Object> getConversionStats(Long modelId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        return assignmentService.getConversionStats(modelId, startTime, endTime);
    }

    /**
     * 转化漏斗: 各等级 → 线索数 / 转化数 / 转化率。
     *
     * @param modelId 模型 ID
     * @return 漏斗列表 [{grade, totalLeads, convertedLeads, conversionRate}]
     * @throws ScrmException 模型不存在
     */
    public List<Map<String, Object>> getConversionFunnel(Long modelId) throws ScrmException {
        return assignmentService.getConversionFunnel(modelId);
    }

    // ============================================================
    // 分配
    // ============================================================

    /**
     * 分配线索给负责人。
     *
     * @param assignDto 分配参数
     * @return 更新后的评分记录
     * @throws ScrmException 评分记录不存在
     */
    public ScrmLeadScoreEntity assignLead(ScrmLeadAssignDto assignDto) throws ScrmException {
        return assignmentService.assignLead(assignDto);
    }

    /**
     * 批量分配线索给同一负责人。
     *
     * @param scoreIds   评分记录 ID 列表
     * @param assigneeId 负责人用户标识
     * @return 分配结果: {total, assigned, failed}
     */
    public Map<String, Integer> batchAssignLeads(List<Long> scoreIds, String assigneeId) {
        return assignmentService.batchAssignLeads(scoreIds, assigneeId);
    }

    /**
     * 获取分配给负责人的线索 (按分配时间倒序)。
     *
     * @param assigneeId 负责人用户标识
     * @param pageable   分页参数
     * @return 评分分页结果
     */
    public Page<ScrmLeadScoreEntity> getAssignedLeads(String assigneeId, Pageable pageable) {
        return assignmentService.getAssignedLeads(assigneeId, pageable);
    }

    /**
     * 标记线索为已转化。
     *
     * @param scoreId         评分记录 ID
     * @param conversionValue 转化价值
     * @return 更新后的评分记录
     * @throws ScrmException 评分记录不存在 / 已转化
     */
    public ScrmLeadScoreEntity markConverted(Long scoreId, Double conversionValue) throws ScrmException {
        return assignmentService.markConverted(scoreId, conversionValue);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 线索统计: 总数 / 已转化数 / 转化率 / 平均分 / 各等级客户数。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getLeadStats(LocalDateTime startTime, LocalDateTime endTime) {
        return assignmentService.getLeadStats(startTime, endTime);
    }

    /**
     * 模型效果: 准确率 (转化预测准确率) / 转化率对比 (高分 vs 低分)。
     *
     * @param modelId 模型 ID
     * @return 模型效果 Map
     * @throws ScrmException 模型不存在
     */
    public Map<String, Object> getModelPerformance(Long modelId) throws ScrmException {
        return assignmentService.getModelPerformance(modelId);
    }

    /**
     * 评分趋势: 返回最近 days 天每日的总分均值 / 评分次数。
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @param days       天数
     * @return 趋势列表 (按日期升序)
     */
    public List<Map<String, Object>> getScoreTrend(Long customerId, Long modelId, int days) {
        return assignmentService.getScoreTrend(customerId, modelId, days);
    }

    /**
     * 排行: 按指定字段排序的顶级线索。
     *
     * @param limit  返回数量
     * @param sortBy 排序字段: totalScore / conversionProbability / predictedValue（可空, 默认 totalScore）
     * @return 评分列表
     */
    public List<ScrmLeadScoreEntity> getTopLeads(int limit, String sortBy) {
        return assignmentService.getTopLeads(limit, sortBy);
    }
}