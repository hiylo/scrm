/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignAnalysisService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmCampaignAnalysisDto;
import org.hiylo.scrm.dto.ScrmCampaignChannelDto;
import org.hiylo.scrm.dto.ScrmCampaignFunnelDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * SCRM 营销活动效果分析服务 (门面)。
 * <p>
 * 作为营销活动效果分析模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmCampaignAnalysisManageService} (分析管理 / 指标计算)、{@link ScrmCampaignAnalysisChannelService} (渠道管理 / 导出)、
 * {@link ScrmCampaignAnalysisFunnelService} (转化漏斗) 与
 * {@link ScrmCampaignAnalysisStatsService} (统计)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmCampaignAnalysisService {

    /** 分析管理/指标计算子域服务 */
    private final ScrmCampaignAnalysisManageService manageService;

    /** 渠道管理/导出子域服务 */
    private final ScrmCampaignAnalysisChannelService channelService;

    /** 转化漏斗子域服务 */
    private final ScrmCampaignAnalysisFunnelService funnelService;

    /** 统计子域服务 */
    private final ScrmCampaignAnalysisStatsService statsService;

    // ============================================================
    // 分析管理
    // ============================================================

    /**
     * 创建活动分析。
     *
     * @param dto 分析参数
     * @return 创建后的分析
     * @throws ScrmException 参数非法
     */
    public ScrmCampaignAnalysisDto createAnalysis(ScrmCampaignAnalysisDto dto) throws ScrmException {
        return manageService.createAnalysis(dto);
    }

    /**
     * 更新活动分析 (字段非空才覆盖)。
     *
     * @param id  分析 ID
     * @param dto 分析参数
     * @return 更新后的分析
     * @throws ScrmException 分析不存在 / 参数非法
     */
    public ScrmCampaignAnalysisDto updateAnalysis(Long id, ScrmCampaignAnalysisDto dto) throws ScrmException {
        return manageService.updateAnalysis(id, dto);
    }

    /**
     * 删除活动分析。
     *
     * @param id 分析 ID
     * @throws ScrmException 分析不存在
     */
    public void deleteAnalysis(Long id) throws ScrmException {
        manageService.deleteAnalysis(id);
    }

    /**
     * 查询分析详情。
     *
     * @param id 分析 ID
     * @return 分析 DTO
     * @throws ScrmException 分析不存在
     */
    public ScrmCampaignAnalysisDto getAnalysis(Long id) throws ScrmException {
        return manageService.getAnalysis(id);
    }

    /**
     * 按活动 ID 查询分析。
     *
     * @param campaignId 活动 ID
     * @return 分析 DTO
     * @throws ScrmException 分析不存在
     */
    public ScrmCampaignAnalysisDto getAnalysisByCampaign(Long campaignId) throws ScrmException {
        return manageService.getAnalysisByCampaign(campaignId);
    }

    /**
     * 分页查询活动分析。
     *
     * @param campaignType 活动类型 (可空)
     * @param status       状态 (可空)
     * @param keyword      关键字 (可空)
     * @param pageable     分页参数
     * @return 分析分页结果
     */
    public Page<ScrmCampaignAnalysisDto> listAnalyses(String campaignType, String status,
                                                       String keyword, Pageable pageable) {
        return manageService.listAnalyses(campaignType, status, keyword, pageable);
    }

    /**
     * 查询进行中的活动分析列表。
     *
     * @return 分析列表
     */
    public List<ScrmCampaignAnalysisDto> getRunningCampaigns() {
        return manageService.getRunningCampaigns();
    }

    /**
     * 查询已完成的活动分析列表。
     *
     * @return 分析列表
     */
    public List<ScrmCampaignAnalysisDto> getCompletedCampaigns() {
        return manageService.getCompletedCampaigns();
    }

    /**
     * 分析活动: 计算指标 → 生成分析 → 识别亮点与问题。
     *
     * @param id 分析 ID
     * @return 更新后的分析
     * @throws ScrmException 分析不存在
     */
    public ScrmCampaignAnalysisDto analyzeCampaign(Long id) throws ScrmException {
        return manageService.analyzeCampaign(id);
    }

    /**
     * 重新分析活动。
     *
     * @param id 分析 ID
     * @return 更新后的分析
     * @throws ScrmException 分析不存在
     */
    public ScrmCampaignAnalysisDto reanalyze(Long id) throws ScrmException {
        return manageService.reanalyze(id);
    }

    /**
     * 审批分析。
     *
     * @param id         分析 ID
     * @param approvedBy 审批人
     * @return 更新后的分析
     * @throws ScrmException 分析不存在 / 审批人非法
     */
    public ScrmCampaignAnalysisDto approveAnalysis(Long id, String approvedBy) throws ScrmException {
        return manageService.approveAnalysis(id, approvedBy);
    }

    /**
     * 分享分析 (返回分享摘要信息)。
     *
     * @param id 分析 ID
     * @return 分享摘要
     * @throws ScrmException 分析不存在
     */
    public Map<String, Object> shareAnalysis(Long id) throws ScrmException {
        return manageService.shareAnalysis(id);
    }

    /**
     * 查询分析汇总 (核心指标聚合)。
     *
     * @param id 分析 ID
     * @return 汇总信息
     * @throws ScrmException 分析不存在
     */
    public Map<String, Object> getAnalysisSummary(Long id) throws ScrmException {
        return manageService.getAnalysisSummary(id);
    }

    /**
     * 多活动效果对比。
     *
     * @param campaignIds 分析 ID 列表
     * @return 对比结果列表
     * @throws ScrmException 列表为空
     */
    public List<Map<String, Object>> compareCampaigns(List<Long> campaignIds) throws ScrmException {
        return manageService.compareCampaigns(campaignIds);
    }

    /**
     * 批量分析活动。
     *
     * @param ids 分析 ID 列表
     * @return 已分析的分析列表
     * @throws ScrmException 列表为空
     */
    public List<ScrmCampaignAnalysisDto> batchAnalyze(List<Long> ids) throws ScrmException {
        return manageService.batchAnalyze(ids);
    }

    // ============================================================
    // 指标计算
    // ============================================================

    /**
     * 计算 ROI = 利润 / 成本 (成本为 0 返回 0)。
     *
     * @param revenue 收入
     * @param cost    成本
     * @return ROI
     */
    public double calculateROI(Double revenue, Double cost) {
        return manageService.calculateROI(revenue, cost);
    }

    /**
     * 计算 ROAS = 收入 / 成本 (成本为 0 返回 0)。
     *
     * @param revenue 收入
     * @param cost    成本
     * @return ROAS
     */
    public double calculateROAS(Double revenue, Double cost) {
        return manageService.calculateROAS(revenue, cost);
    }

    /**
     * 计算转化率 = 转化数 / 触达数 (触达为 0 返回 0)。
     *
     * @param conversionCount 转化数
     * @param reachCount      触达数
     * @return 转化率
     */
    public double calculateConversionRate(Integer conversionCount, Integer reachCount) {
        return manageService.calculateConversionRate(conversionCount, reachCount);
    }

    /**
     * 计算 CAC = 成本 / 新客户数 (新客户数为 0 返回 0)。
     *
     * @param cost             成本
     * @param newCustomerCount 新客户数
     * @return CAC
     */
    public double calculateCAC(Double cost, Integer newCustomerCount) {
        return manageService.calculateCAC(cost, newCustomerCount);
    }

    // ============================================================
    // 渠道效果
    // ============================================================

    /**
     * 新增渠道效果。
     *
     * @param dto 渠道参数
     * @return 创建后的渠道
     * @throws ScrmException 参数非法 / 分析不存在
     */
    public ScrmCampaignChannelDto addChannel(ScrmCampaignChannelDto dto) throws ScrmException {
        return channelService.addChannel(dto);
    }

    /**
     * 更新渠道效果 (字段非空才覆盖)。
     *
     * @param id  渠道 ID
     * @param dto 渠道参数
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在 / 参数非法
     */
    public ScrmCampaignChannelDto updateChannel(Long id, ScrmCampaignChannelDto dto) throws ScrmException {
        return channelService.updateChannel(id, dto);
    }

    /**
     * 删除渠道效果。
     *
     * @param id 渠道 ID
     * @throws ScrmException 渠道不存在
     */
    public void deleteChannel(Long id) throws ScrmException {
        channelService.deleteChannel(id);
    }

    /**
     * 查询渠道详情。
     *
     * @param id 渠道 ID
     * @return 渠道 DTO
     * @throws ScrmException 渠道不存在
     */
    public ScrmCampaignChannelDto getChannel(Long id) throws ScrmException {
        return channelService.getChannel(id);
    }

    /**
     * 按分析 ID 查询渠道列表。
     *
     * @param analysisId 分析 ID
     * @return 渠道列表
     * @throws ScrmException 分析不存在
     */
    public List<ScrmCampaignChannelDto> getChannelsByAnalysis(Long analysisId) throws ScrmException {
        return channelService.getChannelsByAnalysis(analysisId);
    }

    /**
     * 分析渠道性能。
     *
     * @param analysisId 分析 ID
     * @return 渠道性能分析结果
     * @throws ScrmException 分析不存在
     */
    public List<ScrmCampaignChannelDto> analyzeChannelPerformance(Long analysisId) throws ScrmException {
        return channelService.analyzeChannelPerformance(analysisId);
    }

    /**
     * 查询最优渠道。
     *
     * @param analysisId 分析 ID
     * @return 最优渠道 DTO
     * @throws ScrmException 分析不存在 / 无最优渠道
     */
    public ScrmCampaignChannelDto getBestChannel(Long analysisId) throws ScrmException {
        return channelService.getBestChannel(analysisId);
    }

    /**
     * 查询最差渠道。
     *
     * @param analysisId 分析 ID
     * @return 最差渠道 DTO
     * @throws ScrmException 分析不存在 / 无最差渠道
     */
    public ScrmCampaignChannelDto getWorstChannel(Long analysisId) throws ScrmException {
        return channelService.getWorstChannel(analysisId);
    }

    /**
     * 多渠道效果对比。
     *
     * @param channelIds 渠道 ID 列表
     * @return 对比结果列表
     * @throws ScrmException 列表为空
     */
    public List<Map<String, Object>> compareChannels(List<Long> channelIds) throws ScrmException {
        return channelService.compareChannels(channelIds);
    }

    /**
     * 查询分析的渠道 ROI 汇总。
     *
     * @param analysisId 分析 ID
     * @return 渠道 ROI 汇总
     * @throws ScrmException 分析不存在
     */
    public Map<String, Object> getChannelROI(Long analysisId) throws ScrmException {
        return channelService.getChannelROI(analysisId);
    }

    /**
     * 计算渠道效率评分。
     *
     * @param channelId 渠道 ID
     * @return 效率评分
     * @throws ScrmException 渠道不存在
     */
    public Double calculateChannelEfficiency(Long channelId) throws ScrmException {
        return channelService.calculateChannelEfficiency(channelId);
    }

    /**
     * 导出分析 (返回完整分析数据, 含渠道与漏斗)。
     *
     * @param id 分析 ID
     * @return 导出数据
     * @throws ScrmException 分析不存在
     */
    public Map<String, Object> exportAnalysis(Long id) throws ScrmException {
        return channelService.exportAnalysis(id);
    }

    // ============================================================
    // 转化漏斗
    // ============================================================

    /**
     * 新增漏斗阶段。
     *
     * @param dto 漏斗阶段参数
     * @return 创建后的漏斗阶段
     * @throws ScrmException 参数非法 / 分析不存在
     */
    public ScrmCampaignFunnelDto addFunnelStage(ScrmCampaignFunnelDto dto) throws ScrmException {
        return funnelService.addFunnelStage(dto);
    }

    /**
     * 更新漏斗阶段 (字段非空才覆盖)。
     *
     * @param id  漏斗阶段 ID
     * @param dto 漏斗阶段参数
     * @return 更新后的漏斗阶段
     * @throws ScrmException 漏斗阶段不存在 / 参数非法
     */
    public ScrmCampaignFunnelDto updateFunnelStage(Long id, ScrmCampaignFunnelDto dto) throws ScrmException {
        return funnelService.updateFunnelStage(id, dto);
    }

    /**
     * 删除漏斗阶段。
     *
     * @param id 漏斗阶段 ID
     * @throws ScrmException 漏斗阶段不存在
     */
    public void deleteFunnelStage(Long id) throws ScrmException {
        funnelService.deleteFunnelStage(id);
    }

    /**
     * 查询漏斗阶段详情。
     *
     * @param id 漏斗阶段 ID
     * @return 漏斗阶段 DTO
     * @throws ScrmException 漏斗阶段不存在
     */
    public ScrmCampaignFunnelDto getFunnelStage(Long id) throws ScrmException {
        return funnelService.getFunnelStage(id);
    }

    /**
     * 按分析 ID 查询漏斗阶段列表。
     *
     * @param analysisId 分析 ID
     * @return 漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    public List<ScrmCampaignFunnelDto> getFunnelByAnalysis(Long analysisId) throws ScrmException {
        return funnelService.getFunnelByAnalysis(analysisId);
    }

    /**
     * 构建漏斗。
     *
     * @param analysisId 分析 ID
     * @param funnelType 漏斗类型
     * @return 漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    public List<ScrmCampaignFunnelDto> buildFunnel(Long analysisId, String funnelType) throws ScrmException {
        return funnelService.buildFunnel(analysisId, funnelType);
    }

    /**
     * 分析漏斗。
     *
     * @param analysisId 分析 ID
     * @return 漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    public List<ScrmCampaignFunnelDto> analyzeFunnel(Long analysisId) throws ScrmException {
        return funnelService.analyzeFunnel(analysisId);
    }

    /**
     * 查询瓶颈阶段。
     *
     * @param analysisId 分析 ID
     * @return 瓶颈阶段 DTO
     * @throws ScrmException 分析不存在 / 无瓶颈
     */
    public ScrmCampaignFunnelDto getBottleneckStage(Long analysisId) throws ScrmException {
        return funnelService.getBottleneckStage(analysisId);
    }

    /**
     * 查询漏斗整体转化率。
     *
     * @param analysisId 分析 ID
     * @return 漏斗转化率
     * @throws ScrmException 分析不存在
     */
    public Double getFunnelConversionRate(Long analysisId) throws ScrmException {
        return funnelService.getFunnelConversionRate(analysisId);
    }

    /**
     * 查询漏斗整体流失率。
     *
     * @param analysisId 分析 ID
     * @return 漏斗流失率
     * @throws ScrmException 分析不存在
     */
    public Double getFunnelDropoffRate(Long analysisId) throws ScrmException {
        return funnelService.getFunnelDropoffRate(analysisId);
    }

    /**
     * 优化漏斗。
     *
     * @param analysisId 分析 ID
     * @return 优化后的漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    public List<ScrmCampaignFunnelDto> optimizeFunnel(Long analysisId) throws ScrmException {
        return funnelService.optimizeFunnel(analysisId);
    }

    /**
     * 多漏斗对比。
     *
     * @param analysisIds 分析 ID 列表
     * @return 对比结果列表
     * @throws ScrmException 列表为空
     */
    public List<Map<String, Object>> compareFunnels(List<Long> analysisIds) throws ScrmException {
        return funnelService.compareFunnels(analysisIds);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 活动统计。
     *
     * @return 活动统计
     */
    public Map<String, Object> getCampaignStats() {
        return statsService.getCampaignStats();
    }

    /**
     * 渠道统计。
     *
     * @return 渠道统计
     */
    public Map<String, Object> getChannelStats() {
        return statsService.getChannelStats();
    }

    /**
     * 漏斗统计。
     *
     * @return 漏斗统计
     */
    public Map<String, Object> getFunnelStats() {
        return statsService.getFunnelStats();
    }

    /**
     * ROI 趋势。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    public List<Map<String, Object>> getROITrend(Integer months) {
        return statsService.getROITrend(months);
    }

    /**
     * 转化趋势。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    public List<Map<String, Object>> getConversionTrend(Integer months) {
        return statsService.getConversionTrend(months);
    }

    /**
     * 成本趋势。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    public List<Map<String, Object>> getCostTrend(Integer months) {
        return statsService.getCostTrend(months);
    }

    /**
     * 收入趋势。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    public List<Map<String, Object>> getRevenueTrend(Integer months) {
        return statsService.getRevenueTrend(months);
    }

    /**
     * Top 活动。
     *
     * @param limit 返回条数 (默认 10)
     * @return 活动排行列表
     */
    public List<Map<String, Object>> getTopCampaigns(Integer limit) {
        return statsService.getTopCampaigns(limit);
    }

    /**
     * Top 渠道。
     *
     * @param limit 返回条数 (默认 10)
     * @return 渠道排行列表
     */
    public List<Map<String, Object>> getTopChannels(Integer limit) {
        return statsService.getTopChannels(limit);
    }

    /**
     * 活动性能分布。
     *
     * @return 性能分布
     */
    public Map<String, Object> getCampaignPerformanceDistribution() {
        return statsService.getCampaignPerformanceDistribution();
    }

    /**
     * 活动概览。
     *
     * @return 概览数据
     */
    public Map<String, Object> getCampaignOverview() {
        return statsService.getCampaignOverview();
    }

}