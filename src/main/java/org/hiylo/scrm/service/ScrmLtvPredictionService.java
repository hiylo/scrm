/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvPredictionService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmCustomerLtvDto;
import org.hiylo.scrm.dto.ScrmLtvCalculateDto;
import org.hiylo.scrm.dto.ScrmLtvCohortDto;
import org.hiylo.scrm.dto.ScrmLtvModelDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户终身价值预测 (LTV) 服务门面。
 * <p>
 * 统一对外暴露客户终身价值预测全流程能力, 具体实现按子域委托给兄弟服务:
 * 模型管理、LTV 计算、预测与流失、群组与统计。所有 public 方法签名保持不变。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
public class ScrmLtvPredictionService {

    /** LTV 模型管理服务 */
    private final ScrmLtvModelService modelService;
    /** LTV 计算服务 */
    private final ScrmLtvCalculationService calculationService;
    /** 预测与流失服务 */
    private final ScrmLtvForecastService forecastService;
    /** 同期群与统计服务 */
    private final ScrmLtvCohortService cohortService;

    // ============================================================
    // 模型管理
    // ============================================================

    /**
     * 创建 LTV 模型
     *
     * @param dto 模型参数
     * @return 创建后的模型
     * @throws ScrmException 参数非法 / 模型编码冲突 / 默认模型冲突
     */
    public ScrmLtvModelDto createModel(ScrmLtvModelDto dto) throws ScrmException {
        return modelService.createModel(dto);
    }

    /**
     * 更新 LTV 模型
     *
     * @param id  模型 ID
     * @param dto 模型参数
     * @return 更新后的模型
     * @throws ScrmException 模型不存在 / 参数非法
     */
    public ScrmLtvModelDto updateModel(Long id, ScrmLtvModelDto dto) throws ScrmException {
        return modelService.updateModel(id, dto);
    }

    /**
     * 删除 LTV 模型
     *
     * @param id 模型 ID
     * @throws ScrmException 模型不存在
     */
    public void deleteModel(Long id) throws ScrmException {
        modelService.deleteModel(id);
    }

    /**
     * 查询 LTV 模型详情
     *
     * @param id 模型 ID
     * @return 模型 DTO
     * @throws ScrmException 模型不存在
     */
    public ScrmLtvModelDto getModel(Long id) throws ScrmException {
        return modelService.getModel(id);
    }

    /**
     * 按模型编码查询模型
     *
     * @param code 模型编码
     * @return 模型 DTO
     * @throws ScrmException 模型不存在
     */
    public ScrmLtvModelDto getModelByCode(String code) throws ScrmException {
        return modelService.getModelByCode(code);
    }

    /**
     * 分页查询 LTV 模型
     *
     * @param modelType   模型类型过滤 (可空)
     * @param isPublished 发布状态过滤 (可空)
     * @param keyword     名称/编码关键词 (可空)
     * @param pageable    分页参数
     * @return 模型分页结果
     */
    public Page<ScrmLtvModelDto> listModels(String modelType, Boolean isPublished, String keyword,
                                             Pageable pageable) {
        return modelService.listModels(modelType, isPublished, keyword, pageable);
    }

    /**
     * 发布模型
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmLtvModelDto publishModel(Long id) throws ScrmException {
        return modelService.publishModel(id);
    }

    /**
     * 取消发布模型
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmLtvModelDto unpublishModel(Long id) throws ScrmException {
        return modelService.unpublishModel(id);
    }

    /**
     * 设置默认模型
     *
     * @param id 模型 ID
     * @return 更新后的模型
     * @throws ScrmException 模型不存在
     */
    public ScrmLtvModelDto setDefault(Long id) throws ScrmException {
        return modelService.setDefault(id);
    }

    /**
     * 复制模型
     *
     * @param id 源模型 ID
     * @return 复制后的模型
     * @throws ScrmException 源模型不存在
     */
    public ScrmLtvModelDto copyModel(Long id) throws ScrmException {
        return modelService.copyModel(id);
    }

    // ============================================================
    // LTV 计算
    // ============================================================

    /**
     * 计算单个客户 LTV (完整实现)
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return LTV 计算结果
     * @throws ScrmException 模型不存在 / 客户不存在
     */
    public ScrmCustomerLtvDto calculateLtv(Long customerId, Long modelId) throws ScrmException {
        return calculationService.calculateLtv(customerId, modelId);
    }

    /**
     * 批量计算客户 LTV
     *
     * @param calculateDto 批量计算请求 (模型 ID + 客户 ID 列表)
     * @return 计算结果列表
     * @throws ScrmException 模型不存在
     */
    public List<ScrmCustomerLtvDto> batchCalculate(ScrmLtvCalculateDto calculateDto) throws ScrmException {
        return calculationService.batchCalculate(calculateDto);
    }

    /**
     * 计算当前账号下所有客户 LTV
     *
     * @param modelId 模型 ID
     * @return 计算结果列表
     * @throws ScrmException 模型不存在
     */
    public List<ScrmCustomerLtvDto> calculateAll(Long modelId) throws ScrmException {
        return calculationService.calculateAll(modelId);
    }

    /**
     * 重新计算客户 LTV (先清理旧结果再重算)
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID
     * @return 重新计算后的结果
     * @throws ScrmException 模型不存在 / 客户不存在
     */
    public ScrmCustomerLtvDto recalculate(Long customerId, Long modelId) throws ScrmException {
        return calculationService.recalculate(customerId, modelId);
    }

    /**
     * 查询客户 LTV 计算结果详情
     *
     * @param id LTV 结果 ID
     * @return LTV 结果 DTO
     * @throws ScrmException 结果不存在
     */
    public ScrmCustomerLtvDto getLtv(Long id) throws ScrmException {
        return calculationService.getLtv(id);
    }

    /**
     * 按客户 ID 与模型 ID 查询最新 LTV 结果
     *
     * @param customerId 客户 ID
     * @param modelId    模型 ID (可空, 为空时取该客户最新一条)
     * @return LTV 结果 DTO
     * @throws ScrmException 结果不存在
     */
    public ScrmCustomerLtvDto getLtvByCustomer(Long customerId, Long modelId) throws ScrmException {
        return calculationService.getLtvByCustomer(customerId, modelId);
    }

    /**
     * 分页查询客户 LTV 结果
     *
     * @param valueTier      价值层级过滤 (可空)
     * @param minLtv         最小预测 LTV 过滤 (可空)
     * @param maxLtv         最大预测 LTV 过滤 (可空)
     * @param growthPotential 增长潜力过滤 (可空)
     * @param sortBy         排序字段: predictedLtv / historicalLtv / churnProbability / roi (可空)
     * @param pageable       分页参数
     * @return LTV 结果分页
     */
    public Page<ScrmCustomerLtvDto> listLtv(String valueTier, Double minLtv, Double maxLtv,
                                             String growthPotential, String sortBy, Pageable pageable) {
        return calculationService.listLtv(valueTier, minLtv, maxLtv, growthPotential, sortBy, pageable);
    }

    /**
     * 查询高价值客户 (按预测 LTV 降序取 Top N)
     *
     * @param limit 返回条数
     * @return LTV 结果列表
     */
    public List<ScrmCustomerLtvDto> getTopCustomers(int limit) {
        return calculationService.getTopCustomers(limit);
    }

    /**
     * 确定价值层级
     *
     * @param ltv        预测 LTV
     * @param thresholds 模型配置的层级阈值 JSON (可空)
     * @return 价值层级
     */
    public String determineTier(double ltv, String thresholds) {
        return calculationService.determineTier(ltv, thresholds);
    }

    /**
     * 简单 LTV 计算 (按历史购买率线性外推)
     *
     * @param revenue       历史总收入
     * @param orders        历史订单数
     * @param avgOrderValue 平均订单价值
     * @param forecastDays  预测天数
     * @return 预测期内的预计收入
     */
    public double calculateSimpleLtv(double revenue, int orders, double avgOrderValue, int forecastDays) {
        return calculationService.calculateSimpleLtv(revenue, orders, avgOrderValue, forecastDays);
    }

    /**
     * DCF (折现现金流) 计算
     *
     * @param revenue       年化收入
     * @param discountRate  折现率
     * @param forecastDays  预测天数
     * @return 折现后的预计收入
     */
    public double calculateDiscountedCashFlow(double revenue, double discountRate, int forecastDays) {
        return calculationService.calculateDiscountedCashFlow(revenue, discountRate, forecastDays);
    }

    // ============================================================
    // 预测
    // ============================================================

    /**
     * 预测客户未来 LTV (基于历史趋势线性回归)
     *
     * @param customerId   客户 ID
     * @param forecastDays 预测天数
     * @return 预测结果 (含预测 LTV、月度预测序列、回归斜率等)
     * @throws ScrmException 客户不存在
     */
    public Map<String, Object> forecast(Long customerId, int forecastDays) throws ScrmException {
        return forecastService.forecast(customerId, forecastDays);
    }

    /**
     * 预测客户未来收入 (基于线性回归, 返回指定月数的月度预测)
     *
     * @param customerId 客户 ID
     * @param months     预测月数
     * @return 预测结果 (含月度序列与总收入)
     * @throws ScrmException 客户不存在
     */
    public Map<String, Object> forecastRevenue(Long customerId, int months) throws ScrmException {
        return forecastService.forecastRevenue(customerId, months);
    }

    /**
     * 预测客户流失 (基于购买间隔与频次)
     *
     * @param customerId 客户 ID
     * @return 流失预测结果
     * @throws ScrmException 客户 LTV 结果不存在
     */
    public Map<String, Object> predictChurn(Long customerId) throws ScrmException {
        return forecastService.predictChurn(customerId);
    }

    /**
     * 查询高流失风险客户 (按流失概率降序取 Top N)
     *
     * @param limit 返回条数
     * @return LTV 结果列表
     */
    public List<ScrmCustomerLtvDto> getChurnRiskCustomers(int limit) {
        return forecastService.getChurnRiskCustomers(limit);
    }

    // ============================================================
    // 价值层级
    // ============================================================

    /**
     * 价值层级分布统计: 各层级的客户数、占比与平均 LTV
     *
     * @param modelId 模型 ID (可空, 为空时统计全部)
     * @return 层级分布列表
     */
    public List<Map<String, Object>> getTierDistribution(Long modelId) {
        return cohortService.getTierDistribution(modelId);
    }

    /**
     * 各层级统计: 指定层级的客户数、平均/中位 LTV、平均 ROI
     *
     * @param modelId 模型 ID (可空)
     * @param tier    价值层级
     * @return 层级统计
     */
    public Map<String, Object> getTierStats(Long modelId, String tier) {
        return cohortService.getTierStats(modelId, tier);
    }

    // ============================================================
    // 同期群 (Cohort)
    // ============================================================

    /**
     * 创建同期群分组
     *
     * @param dto 分组参数
     * @return 创建后的分组
     * @throws ScrmException 参数非法
     */
    public ScrmLtvCohortDto createCohort(ScrmLtvCohortDto dto) throws ScrmException {
        return cohortService.createCohort(dto);
    }

    /**
     * 更新同期群分组
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    public ScrmLtvCohortDto updateCohort(Long id, ScrmLtvCohortDto dto) throws ScrmException {
        return cohortService.updateCohort(id, dto);
    }

    /**
     * 删除同期群分组
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在
     */
    public void deleteCohort(Long id) throws ScrmException {
        cohortService.deleteCohort(id);
    }

    /**
     * 查询同期群分组详情
     *
     * @param id 分组 ID
     * @return 分组 DTO
     * @throws ScrmException 分组不存在
     */
    public ScrmLtvCohortDto getCohort(Long id) throws ScrmException {
        return cohortService.getCohort(id);
    }

    /**
     * 分页查询同期群分组
     *
     * @param cohortType 分组类型过滤 (可空)
     * @param startDate  分组开始日期下限 (可空)
     * @param endDate    分组开始日期上限 (可空)
     * @param pageable   分页参数
     * @return 分组分页结果
     */
    public Page<ScrmLtvCohortDto> listCohorts(String cohortType, LocalDate startDate, LocalDate endDate,
                                                Pageable pageable) {
        return cohortService.listCohorts(cohortType, startDate, endDate, pageable);
    }

    /**
     * 生成同期群分析
     *
     * @param cohortType 分组类型
     * @param startDate  起始日期 (可空)
     * @param endDate    截止日期 (可空)
     * @return 生成的分组列表
     * @throws ScrmException 参数非法
     */
    public List<ScrmLtvCohortDto> generateCohort(String cohortType, LocalDate startDate, LocalDate endDate)
            throws ScrmException {
        return cohortService.generateCohort(cohortType, startDate, endDate);
    }

    /**
     * 查询同期群趋势 (按平均 LTV 降序返回各分组)
     *
     * @param cohortType 分组类型
     * @return 分组列表
     */
    public List<ScrmLtvCohortDto> getCohortTrend(String cohortType) {
        return cohortService.getCohortTrend(cohortType);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * LTV 统计概览 (指定时间范围内的平均/中位/各层级分布/趋势)
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getLtvStats(LocalDateTime startTime, LocalDateTime endTime) {
        return cohortService.getLtvStats(startTime, endTime);
    }

    /**
     * 模型效果统计 (指定模型的客户数、平均预测/历史 LTV、平均置信度)
     *
     * @param modelId 模型 ID
     * @return 模型效果统计
     * @throws ScrmException 模型不存在
     */
    public Map<String, Object> getModelPerformance(Long modelId) throws ScrmException {
        return cohortService.getModelPerformance(modelId);
    }

    /**
     * LTV 趋势 (近 N 天的平均预测 LTV 按日序列)
     *
     * @param days 天数
     * @return 趋势序列 [{date, avgLtv}]
     */
    public List<Map<String, Object>> getLtvTrend(int days) {
        return cohortService.getLtvTrend(days);
    }

    /**
     * ROI 统计 (指定时间范围内的平均 ROI、平均盈利性、总获客成本、总预测 LTV)
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return ROI 统计
     */
    public Map<String, Object> getRoiStats(LocalDateTime startTime, LocalDateTime endTime) {
        return cohortService.getRoiStats(startTime, endTime);
    }

    /**
     * 同期群对比 (指定分组类型的聚合: 分组数、总客户数、平均 LTV、平均留存率)
     *
     * @param cohortType 分组类型
     * @param startDate  起始日期 (可空)
     * @param endDate    截止日期 (可空)
     * @return 对比结果
     */
    public Map<String, Object> getCohortComparison(String cohortType, LocalDate startDate, LocalDate endDate) {
        return cohortService.getCohortComparison(cohortType, startDate, endDate);
    }
}