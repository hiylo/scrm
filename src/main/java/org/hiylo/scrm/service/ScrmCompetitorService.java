/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmCompetitorActivityDto;
import org.hiylo.scrm.dto.ScrmCompetitorAnalysisDto;
import org.hiylo.scrm.dto.ScrmCompetitorDto;
import org.hiylo.scrm.dto.ScrmCompetitorProductDto;
import org.hiylo.scrm.dto.ScrmPriceMonitorDto;
import org.hiylo.scrm.entity.ScrmCompetitorActivityEntity;
import org.hiylo.scrm.entity.ScrmCompetitorEntity;
import org.hiylo.scrm.entity.ScrmCompetitorProductEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 竞品监测服务门面。
 * <p>
 * 统一暴露竞品信息管理 {@link ScrmCompetitorManagementService}、竞品产品
 * {@link ScrmCompetitorProductService}、竞品动态 {@link ScrmCompetitorActivityService}
 * 与竞争分析统计 {@link ScrmCompetitorAnalysisService} 四个子域的全部能力, 原各 public 方法
 * 均保留原签名并委托给对应子域服务实现。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmCompetitorService {

    /** 竞品信息管理服务 */
    private final ScrmCompetitorManagementService competitorService;
    /** 竞品产品管理服务 */
    private final ScrmCompetitorProductService productService;
    /** 竞品动态追踪服务 */
    private final ScrmCompetitorActivityService activityService;
    /** 竞品分析统计服务 */
    private final ScrmCompetitorAnalysisService analysisService;

    // ============================================================
    // 竞品管理
    // ============================================================

    /** 创建竞品 */
    public ScrmCompetitorEntity createCompetitor(ScrmCompetitorDto dto) throws ScrmException {
        return competitorService.createCompetitor(dto);
    }

    /** 更新竞品（字段非空才覆盖） */
    public ScrmCompetitorEntity updateCompetitor(Long id, ScrmCompetitorDto dto) throws ScrmException {
        return competitorService.updateCompetitor(id, dto);
    }

    /** 删除竞品 */
    public void deleteCompetitor(Long id) throws ScrmException {
        competitorService.deleteCompetitor(id);
    }

    /** 查询竞品详情 */
    public ScrmCompetitorEntity getCompetitor(Long id) throws ScrmException {
        return competitorService.getCompetitor(id);
    }

    /** 按编码查询竞品 */
    public ScrmCompetitorEntity getCompetitorByCode(String code) throws ScrmException {
        return competitorService.getCompetitorByCode(code);
    }

    /** 分页查询竞品 */
    public Page<ScrmCompetitorEntity> listCompetitors(String industry, String threatLevel, String status,
                                                       String keyword, Pageable pageable) {
        return competitorService.listCompetitors(industry, threatLevel, status, keyword, pageable);
    }

    /** 启用竞品监测 */
    public ScrmCompetitorEntity enableMonitoring(Long id) throws ScrmException {
        return competitorService.enableMonitoring(id);
    }

    /** 停止竞品监测 */
    public ScrmCompetitorEntity disableMonitoring(Long id) throws ScrmException {
        return competitorService.disableMonitoring(id);
    }

    /** 更新竞品监测频率 */
    public ScrmCompetitorEntity updateMonitoringFrequency(Long id, String frequency) throws ScrmException {
        return competitorService.updateMonitoringFrequency(id, frequency);
    }

    /** 更新竞品最近监测时间为当前时间 */
    public ScrmCompetitorEntity updateLastMonitored(Long id) throws ScrmException {
        return competitorService.updateLastMonitored(id);
    }

    /** 按威胁等级分页查询竞品 */
    public Page<ScrmCompetitorEntity> getCompetitorsByThreat(String level, Pageable pageable)
            throws ScrmException {
        return competitorService.getCompetitorsByThreat(level, pageable);
    }

    /** 按行业分页查询竞品 */
    public Page<ScrmCompetitorEntity> getCompetitorsByIndustry(String industry, Pageable pageable) {
        return competitorService.getCompetitorsByIndustry(industry, pageable);
    }

    /** 归档竞品 */
    public ScrmCompetitorEntity archiveCompetitor(Long id) throws ScrmException {
        return competitorService.archiveCompetitor(id);
    }

    /** 竞品概要 */
    public Map<String, Object> getCompetitorSummary(Long id) throws ScrmException {
        return competitorService.getCompetitorSummary(id);
    }

    // ============================================================
    // 竞品产品
    // ============================================================

    /** 创建竞品产品 */
    public ScrmCompetitorProductEntity createProduct(ScrmCompetitorProductDto dto) throws ScrmException {
        return productService.createProduct(dto);
    }

    /** 更新竞品产品（字段非空才覆盖） */
    public ScrmCompetitorProductEntity updateProduct(Long id, ScrmCompetitorProductDto dto) throws ScrmException {
        return productService.updateProduct(id, dto);
    }

    /** 删除竞品产品 */
    public void deleteProduct(Long id) throws ScrmException {
        productService.deleteProduct(id);
    }

    /** 查询产品详情 */
    public ScrmCompetitorProductEntity getProduct(Long id) throws ScrmException {
        return productService.getProduct(id);
    }

    /** 分页查询产品 */
    public Page<ScrmCompetitorProductEntity> listProducts(Long competitorId, String productCategory, String status,
                                                           String keyword, Pageable pageable) {
        return productService.listProducts(competitorId, productCategory, status, keyword, pageable);
    }

    /** 按竞品分页查询产品 */
    public Page<ScrmCompetitorProductEntity> getProductsByCompetitor(Long competitorId, Pageable pageable) {
        return productService.getProductsByCompetitor(competitorId, pageable);
    }

    /** 更新产品价格 */
    public ScrmCompetitorProductEntity updatePrice(ScrmPriceMonitorDto dto) throws ScrmException {
        return productService.updatePrice(dto);
    }

    /** 查询产品价格历史 */
    public List<Map<String, Object>> getPriceHistory(Long productId) throws ScrmException {
        return productService.getPriceHistory(productId);
    }

    /** 价格对比: 我方 vs 竞品 */
    public Map<String, Object> getPriceComparison(Long competitorId) throws ScrmException {
        return productService.getPriceComparison(competitorId);
    }

    /** 产品对比: 指定竞品产品与我方产品对比 */
    public Map<String, Object> compareWithOurProduct(Long productId, String ourProductId) throws ScrmException {
        return productService.compareWithOurProduct(productId, ourProductId);
    }

    /** 批量更新价格 */
    public Map<String, Integer> batchUpdatePrices(List<ScrmPriceMonitorDto> updates) {
        return productService.batchUpdatePrices(updates);
    }

    /** 按分类分页查询产品 */
    public Page<ScrmCompetitorProductEntity> getProductsByCategory(String category, Pageable pageable) {
        return productService.getProductsByCategory(category, pageable);
    }

    /** 促销产品查询 */
    public Page<ScrmCompetitorProductEntity> getDiscountedProducts(Pageable pageable) {
        return productService.getDiscountedProducts(pageable);
    }

    /** 近期价格变化产品查询 */
    public List<ScrmCompetitorProductEntity> getPriceChangeProducts(int days) {
        return productService.getPriceChangeProducts(days);
    }

    // ============================================================
    // 竞品动态
    // ============================================================

    /** 创建竞品动态 */
    public ScrmCompetitorActivityEntity createActivity(ScrmCompetitorActivityDto dto) throws ScrmException {
        return activityService.createActivity(dto);
    }

    /** 更新竞品动态（字段非空才覆盖） */
    public ScrmCompetitorActivityEntity updateActivity(Long id, ScrmCompetitorActivityDto dto)
            throws ScrmException {
        return activityService.updateActivity(id, dto);
    }

    /** 删除竞品动态 */
    public void deleteActivity(Long id) throws ScrmException {
        activityService.deleteActivity(id);
    }

    /** 查询动态详情 */
    public ScrmCompetitorActivityEntity getActivity(Long id) throws ScrmException {
        return activityService.getActivity(id);
    }

    /** 分页查询动态 */
    public Page<ScrmCompetitorActivityEntity> listActivities(Long competitorId, String activityType,
            String impactLevel, String responseStatus, LocalDate startTime, LocalDate endTime,
            Pageable pageable) {
        return activityService.listActivities(competitorId, activityType, impactLevel, responseStatus,
                startTime, endTime, pageable);
    }

    /** 按竞品分页查询动态 */
    public Page<ScrmCompetitorActivityEntity> getActivitiesByCompetitor(Long competitorId, Pageable pageable) {
        return activityService.getActivitiesByCompetitor(competitorId, pageable);
    }

    /** 按动态类型分页查询 */
    public Page<ScrmCompetitorActivityEntity> getActivitiesByType(String activityType, Pageable pageable) {
        return activityService.getActivitiesByType(activityType, pageable);
    }

    /** 近期动态查询 */
    public Page<ScrmCompetitorActivityEntity> getRecentActivities(int days, Pageable pageable) {
        return activityService.getRecentActivities(days, pageable);
    }

    /** 重大动态查询 */
    public List<ScrmCompetitorActivityEntity> getCriticalActivities(int limit) {
        return activityService.getCriticalActivities(limit);
    }

    /** 待响应动态查询 */
    public Page<ScrmCompetitorActivityEntity> getPendingResponses(Pageable pageable) {
        return activityService.getPendingResponses(pageable);
    }

    /** 制定应对策略 */
    public ScrmCompetitorActivityEntity planResponse(Long id, String response, String owner, LocalDate dueDate)
            throws ScrmException {
        return activityService.planResponse(id, response, owner, dueDate);
    }

    /** 执行应对 */
    public ScrmCompetitorActivityEntity executeResponse(Long id) throws ScrmException {
        return activityService.executeResponse(id);
    }

    /** 完成应对 */
    public ScrmCompetitorActivityEntity completeResponse(Long id, String outcome) throws ScrmException {
        return activityService.completeResponse(id, outcome);
    }

    /** 验证动态 */
    public ScrmCompetitorActivityEntity verifyActivity(Long id, String verifierId) throws ScrmException {
        return activityService.verifyActivity(id, verifierId);
    }

    /** 更新动态重要性评分 */
    public ScrmCompetitorActivityEntity updateImportance(Long id, int score) throws ScrmException {
        return activityService.updateImportance(id, score);
    }

    /** 动态时间线 */
    public List<ScrmCompetitorActivityEntity> getActivityTimeline(Long competitorId, int months)
            throws ScrmException {
        return activityService.getActivityTimeline(competitorId, months);
    }

    // ============================================================
    // 竞争分析
    // ============================================================

    /** 竞争分析: 按分析维度生成完整报告 */
    public Map<String, Object> analyzeCompetitor(ScrmCompetitorAnalysisDto analysisDto) throws ScrmException {
        return analysisService.analyzeCompetitor(analysisDto);
    }

    /** 多竞品对比 */
    public Map<String, Object> compareCompetitors(List<Long> competitorIds) throws ScrmException {
        return analysisService.compareCompetitors(competitorIds);
    }

    /** 市场概览 */
    public Map<String, Object> getMarketOverview(String industry) {
        return analysisService.getMarketOverview(industry);
    }

    /** 价格分析 */
    public Map<String, Object> getPriceAnalysis(Long competitorId, int months) throws ScrmException {
        return analysisService.getPriceAnalysis(competitorId, months);
    }

    /** 动态分析 */
    public Map<String, Object> getActivityAnalysis(Long competitorId, int months) throws ScrmException {
        return analysisService.getActivityAnalysis(competitorId, months);
    }

    /** 威胁评估 */
    public Map<String, Object> getThreatAssessment(Long competitorId) throws ScrmException {
        return analysisService.getThreatAssessment(competitorId);
    }

    /** 趋势分析 */
    public Map<String, Object> getTrendAnalysis(Long competitorId, int months) throws ScrmException {
        return analysisService.getTrendAnalysis(competitorId, months);
    }

    // ============================================================
    // 统计
    // ============================================================

    /** 竞品统计 */
    public Map<String, Object> getCompetitorStats() {
        return analysisService.getCompetitorStats();
    }

    /** 动态统计 */
    public Map<String, Object> getActivityStats(LocalDateTime startTime, LocalDateTime endTime) {
        return analysisService.getActivityStats(startTime, endTime);
    }

    /** 价格统计 */
    public Map<String, Object> getPriceStats(LocalDateTime startTime, LocalDateTime endTime) {
        return analysisService.getPriceStats(startTime, endTime);
    }

    /** 应对统计 */
    public Map<String, Object> getResponseStats(LocalDateTime startTime, LocalDateTime endTime) {
        return analysisService.getResponseStats(startTime, endTime);
    }

    /** 竞品趋势 */
    public Map<String, Object> getCompetitorTrend(int months) {
        return analysisService.getCompetitorTrend(months);
    }

    /** 动态趋势 */
    public Map<String, Object> getActivityTrend(int days) {
        return analysisService.getActivityTrend(days);
    }
}