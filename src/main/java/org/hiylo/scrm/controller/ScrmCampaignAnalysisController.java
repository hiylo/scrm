/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignAnalysisController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCampaignAnalysisDto;
import org.hiylo.scrm.dto.ScrmCampaignChannelDto;
import org.hiylo.scrm.dto.ScrmCampaignComparisonDto;
import org.hiylo.scrm.dto.ScrmCampaignFunnelDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCampaignAnalysisService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SCRM 营销活动效果分析控制器。
 * <p>
 * 提供活动分析、渠道效果、转化漏斗与统计四组接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/campaign-analysis")
@RequiredArgsConstructor
public class ScrmCampaignAnalysisController {

    /** 营销活动效果分析服务 */
    private final ScrmCampaignAnalysisService scrmCampaignAnalysisService;

    // ============================================================
    // 分析管理 /analyses
    // ============================================================

    /**
     * 创建活动分析。
     *
     * @param dto 分析参数
     * @return 创建后的分析
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/analyses")
    public OperationResponse<ScrmCampaignAnalysisDto> createAnalysis(
            @Valid @RequestBody ScrmCampaignAnalysisDto dto) throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.createAnalysis(dto));
    }

    /**
     * 更新活动分析。
     *
     * @param id  分析 ID
     * @param dto 分析参数
     * @return 更新后的分析
     * @throws ScrmException 分析不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/analyses/{id}")
    public OperationResponse<ScrmCampaignAnalysisDto> updateAnalysis(@PathVariable Long id,
                                                                     @RequestBody ScrmCampaignAnalysisDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.updateAnalysis(id, dto));
    }

    /**
     * 删除活动分析。
     *
     * @param id 分析 ID
     * @return 空响应
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "delete")
    @DeleteMapping("/analyses/{id}")
    public OperationResponse<Void> deleteAnalysis(@PathVariable Long id) throws ScrmException {
        scrmCampaignAnalysisService.deleteAnalysis(id);
        return OperationResponse.build();
    }

    /**
     * 查询分析详情。
     *
     * @param id 分析 ID
     * @return 分析详情
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/analyses/{id}")
    public OperationResponse<ScrmCampaignAnalysisDto> getAnalysis(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getAnalysis(id));
    }

    /**
     * 按活动 ID 查询分析。
     *
     * @param campaignId 活动 ID
     * @return 分析详情
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/analyses/by-campaign/{campaignId}")
    public OperationResponse<ScrmCampaignAnalysisDto> getAnalysisByCampaign(@PathVariable Long campaignId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getAnalysisByCampaign(campaignId));
    }

    /**
     * 分页查询活动分析, 支持按活动类型/状态/关键字过滤。
     *
     * @param campaignType 活动类型 (可选)
     * @param status       状态 (可选)
     * @param keyword      关键字 (可选)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 分析分页结果
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/analyses/list")
    public OperationResponse<Page<ScrmCampaignAnalysisDto>> listAnalyses(
            @RequestParam(required = false) String campaignType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(
                scrmCampaignAnalysisService.listAnalyses(campaignType, status, keyword, pageable));
    }

    /**
     * 查询进行中的活动分析。
     *
     * @return 分析列表
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/analyses/running")
    public OperationResponse<List<ScrmCampaignAnalysisDto>> getRunningCampaigns() {
        return OperationResponse.build(scrmCampaignAnalysisService.getRunningCampaigns());
    }

    /**
     * 查询已完成的活动分析。
     *
     * @return 分析列表
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/analyses/completed")
    public OperationResponse<List<ScrmCampaignAnalysisDto>> getCompletedCampaigns() {
        return OperationResponse.build(scrmCampaignAnalysisService.getCompletedCampaigns());
    }

    /**
     * 分析活动 (计算指标→生成分析→识别亮点问题)。
     *
     * @param id 分析 ID
     * @return 更新后的分析
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/analyses/analyze")
    public OperationResponse<ScrmCampaignAnalysisDto> analyzeCampaign(@RequestParam Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.analyzeCampaign(id));
    }

    /**
     * 重新分析活动。
     *
     * @param id 分析 ID
     * @return 更新后的分析
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/analyses/reanalyze")
    public OperationResponse<ScrmCampaignAnalysisDto> reanalyze(@RequestParam Long id) throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.reanalyze(id));
    }

    /**
     * 审批分析。
     *
     * @param id         分析 ID
     * @param approvedBy 审批人
     * @return 更新后的分析
     * @throws ScrmException 分析不存在 / 审批人非法
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "approve")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/analyses/approve")
    public OperationResponse<ScrmCampaignAnalysisDto> approveAnalysis(@RequestParam Long id,
                                                                       @RequestParam String approvedBy)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.approveAnalysis(id, approvedBy));
    }

    /**
     * 分享分析。
     *
     * @param id 分析 ID
     * @return 分享摘要
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @PostMapping("/analyses/share")
    public OperationResponse<Map<String, Object>> shareAnalysis(@RequestParam Long id) throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.shareAnalysis(id));
    }

    /**
     * 查询分析汇总。
     *
     * @param id 分析 ID
     * @return 汇总信息
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/analyses/summary")
    public OperationResponse<Map<String, Object>> getAnalysisSummary(@RequestParam Long id) throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getAnalysisSummary(id));
    }

    /**
     * 多活动效果对比。
     *
     * @param dto 对比参数 (分析 ID 列表)
     * @return 对比结果列表
     * @throws ScrmException 列表为空
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @PostMapping("/analyses/compare")
    public OperationResponse<List<Map<String, Object>>> compareCampaigns(
            @Valid @RequestBody ScrmCampaignComparisonDto dto) throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.compareCampaigns(dto.getCampaignIds()));
    }

    /**
     * 导出分析。
     *
     * @param id 分析 ID
     * @return 导出数据
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/analyses/export")
    public OperationResponse<Map<String, Object>> exportAnalysis(@RequestParam Long id) throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.exportAnalysis(id));
    }

    /**
     * 批量分析活动。
     *
     * @param ids 分析 ID 列表
     * @return 已分析的分析列表
     * @throws ScrmException 列表为空
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/analyses/batch-analyze")
    public OperationResponse<List<ScrmCampaignAnalysisDto>> batchAnalyze(@RequestParam List<Long> ids)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.batchAnalyze(ids));
    }

    // ============================================================
    // 渠道效果 /channels
    // ============================================================

    /**
     * 新增渠道效果。
     *
     * @param dto 渠道参数
     * @return 创建后的渠道
     * @throws ScrmException 参数非法 / 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/channels")
    public OperationResponse<ScrmCampaignChannelDto> addChannel(@Valid @RequestBody ScrmCampaignChannelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.addChannel(dto));
    }

    /**
     * 更新渠道效果。
     *
     * @param id  渠道 ID
     * @param dto 渠道参数
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/channels/{id}")
    public OperationResponse<ScrmCampaignChannelDto> updateChannel(@PathVariable Long id,
                                                                    @RequestBody ScrmCampaignChannelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.updateChannel(id, dto));
    }

    /**
     * 删除渠道效果。
     *
     * @param id 渠道 ID
     * @return 空响应
     * @throws ScrmException 渠道不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "delete")
    @DeleteMapping("/channels/{id}")
    public OperationResponse<Void> deleteChannel(@PathVariable Long id) throws ScrmException {
        scrmCampaignAnalysisService.deleteChannel(id);
        return OperationResponse.build();
    }

    /**
     * 查询渠道详情。
     *
     * @param id 渠道 ID
     * @return 渠道详情
     * @throws ScrmException 渠道不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/channels/{id}")
    public OperationResponse<ScrmCampaignChannelDto> getChannel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getChannel(id));
    }

    /**
     * 按分析 ID 查询渠道列表。
     *
     * @param analysisId 分析 ID
     * @return 渠道列表
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/channels/by-analysis/{analysisId}")
    public OperationResponse<List<ScrmCampaignChannelDto>> getChannelsByAnalysis(@PathVariable Long analysisId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getChannelsByAnalysis(analysisId));
    }

    /**
     * 分析渠道性能 (计算各渠道指标→排名→识别最优最差)。
     *
     * @param analysisId 分析 ID
     * @return 渠道性能分析结果
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/channels/performance")
    public OperationResponse<List<ScrmCampaignChannelDto>> analyzeChannelPerformance(@RequestParam Long analysisId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.analyzeChannelPerformance(analysisId));
    }

    /**
     * 查询最优渠道。
     *
     * @param analysisId 分析 ID
     * @return 最优渠道
     * @throws ScrmException 分析不存在 / 无最优渠道
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/channels/best/{analysisId}")
    public OperationResponse<ScrmCampaignChannelDto> getBestChannel(@PathVariable Long analysisId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getBestChannel(analysisId));
    }

    /**
     * 查询最差渠道。
     *
     * @param analysisId 分析 ID
     * @return 最差渠道
     * @throws ScrmException 分析不存在 / 无最差渠道
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/channels/worst/{analysisId}")
    public OperationResponse<ScrmCampaignChannelDto> getWorstChannel(@PathVariable Long analysisId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getWorstChannel(analysisId));
    }

    /**
     * 多渠道效果对比。
     *
     * @param channelIds 渠道 ID 列表
     * @return 对比结果列表
     * @throws ScrmException 列表为空
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @PostMapping("/channels/compare")
    public OperationResponse<List<Map<String, Object>>> compareChannels(@RequestParam List<Long> channelIds)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.compareChannels(channelIds));
    }

    /**
     * 查询分析的渠道 ROI 汇总。
     *
     * @param analysisId 分析 ID
     * @return 渠道 ROI 汇总
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/channels/roi/{analysisId}")
    public OperationResponse<Map<String, Object>> getChannelROI(@PathVariable Long analysisId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getChannelROI(analysisId));
    }

    /**
     * 计算渠道效率评分。
     *
     * @param channelId 渠道 ID
     * @return 效率评分
     * @throws ScrmException 渠道不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @PostMapping("/channels/efficiency/{channelId}")
    public OperationResponse<Double> calculateChannelEfficiency(@PathVariable Long channelId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.calculateChannelEfficiency(channelId));
    }

    // ============================================================
    // 转化漏斗 /funnels
    // ============================================================

    /**
     * 新增漏斗阶段。
     *
     * @param dto 漏斗阶段参数
     * @return 创建后的漏斗阶段
     * @throws ScrmException 参数非法 / 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/funnels")
    public OperationResponse<ScrmCampaignFunnelDto> addFunnelStage(@Valid @RequestBody ScrmCampaignFunnelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.addFunnelStage(dto));
    }

    /**
     * 更新漏斗阶段。
     *
     * @param id  漏斗阶段 ID
     * @param dto 漏斗阶段参数
     * @return 更新后的漏斗阶段
     * @throws ScrmException 漏斗阶段不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/funnels/{id}")
    public OperationResponse<ScrmCampaignFunnelDto> updateFunnelStage(@PathVariable Long id,
                                                                       @RequestBody ScrmCampaignFunnelDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.updateFunnelStage(id, dto));
    }

    /**
     * 删除漏斗阶段。
     *
     * @param id 漏斗阶段 ID
     * @return 空响应
     * @throws ScrmException 漏斗阶段不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "delete")
    @DeleteMapping("/funnels/{id}")
    public OperationResponse<Void> deleteFunnelStage(@PathVariable Long id) throws ScrmException {
        scrmCampaignAnalysisService.deleteFunnelStage(id);
        return OperationResponse.build();
    }

    /**
     * 查询漏斗阶段详情。
     *
     * @param id 漏斗阶段 ID
     * @return 漏斗阶段详情
     * @throws ScrmException 漏斗阶段不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/funnels/{id}")
    public OperationResponse<ScrmCampaignFunnelDto> getFunnelStage(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getFunnelStage(id));
    }

    /**
     * 按分析 ID 查询漏斗阶段列表。
     *
     * @param analysisId 分析 ID
     * @return 漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/funnels/by-analysis/{analysisId}")
    public OperationResponse<List<ScrmCampaignFunnelDto>> getFunnelByAnalysis(@PathVariable Long analysisId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getFunnelByAnalysis(analysisId));
    }

    /**
     * 构建漏斗 (按类型→阶段→计算转化流失)。
     *
     * @param analysisId 分析 ID
     * @param funnelType 漏斗类型 (可选, 默认 PURCHASE)
     * @return 漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/funnels/build")
    public OperationResponse<List<ScrmCampaignFunnelDto>> buildFunnel(@RequestParam Long analysisId,
                                                                       @RequestParam(defaultValue = "PURCHASE")
                                                                       String funnelType)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.buildFunnel(analysisId, funnelType));
    }

    /**
     * 分析漏斗 (识别瓶颈→优化建议)。
     *
     * @param analysisId 分析 ID
     * @return 漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/funnels/analyze")
    public OperationResponse<List<ScrmCampaignFunnelDto>> analyzeFunnel(@RequestParam Long analysisId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.analyzeFunnel(analysisId));
    }

    /**
     * 查询瓶颈阶段。
     *
     * @param analysisId 分析 ID
     * @return 瓶颈阶段
     * @throws ScrmException 分析不存在 / 无瓶颈
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/funnels/bottleneck/{analysisId}")
    public OperationResponse<ScrmCampaignFunnelDto> getBottleneckStage(@PathVariable Long analysisId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getBottleneckStage(analysisId));
    }

    /**
     * 查询漏斗整体转化率。
     *
     * @param analysisId 分析 ID
     * @return 漏斗转化率
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/funnels/conversion-rate/{analysisId}")
    public OperationResponse<Double> getFunnelConversionRate(@PathVariable Long analysisId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getFunnelConversionRate(analysisId));
    }

    /**
     * 查询漏斗整体流失率。
     *
     * @param analysisId 分析 ID
     * @return 漏斗流失率
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/funnels/dropoff-rate/{analysisId}")
    public OperationResponse<Double> getFunnelDropoffRate(@PathVariable Long analysisId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.getFunnelDropoffRate(analysisId));
    }

    /**
     * 优化漏斗 (为各阶段生成优化建议)。
     *
     * @param analysisId 分析 ID
     * @return 优化后的漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/funnels/optimize")
    public OperationResponse<List<ScrmCampaignFunnelDto>> optimizeFunnel(@RequestParam Long analysisId)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.optimizeFunnel(analysisId));
    }

    /**
     * 多漏斗对比。
     *
     * @param analysisIds 分析 ID 列表
     * @return 对比结果列表
     * @throws ScrmException 列表为空
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @PostMapping("/funnels/compare")
    public OperationResponse<List<Map<String, Object>>> compareFunnels(@RequestParam List<Long> analysisIds)
            throws ScrmException {
        return OperationResponse.build(scrmCampaignAnalysisService.compareFunnels(analysisIds));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 活动概览 (核心 KPI 汇总)。
     *
     * @return 概览数据
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getCampaignOverview() {
        return OperationResponse.build(scrmCampaignAnalysisService.getCampaignOverview());
    }

    /**
     * 活动统计 (按活动类型汇总)。
     *
     * @return 活动统计
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/stats/campaigns")
    public OperationResponse<Map<String, Object>> getCampaignStats() {
        return OperationResponse.build(scrmCampaignAnalysisService.getCampaignStats());
    }

    /**
     * 渠道统计 (按渠道类型汇总)。
     *
     * @return 渠道统计
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/stats/channels")
    public OperationResponse<Map<String, Object>> getChannelStats() {
        return OperationResponse.build(scrmCampaignAnalysisService.getChannelStats());
    }

    /**
     * 漏斗统计 (按漏斗类型汇总)。
     *
     * @return 漏斗统计
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/stats/funnels")
    public OperationResponse<Map<String, Object>> getFunnelStats() {
        return OperationResponse.build(scrmCampaignAnalysisService.getFunnelStats());
    }

    /**
     * ROI 趋势 (最近 months 个月)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/stats/roi-trend")
    public OperationResponse<List<Map<String, Object>>> getROITrend(
            @RequestParam(defaultValue = "6") Integer months) {
        return OperationResponse.build(scrmCampaignAnalysisService.getROITrend(months));
    }

    /**
     * 转化趋势 (最近 months 个月)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/stats/conversion-trend")
    public OperationResponse<List<Map<String, Object>>> getConversionTrend(
            @RequestParam(defaultValue = "6") Integer months) {
        return OperationResponse.build(scrmCampaignAnalysisService.getConversionTrend(months));
    }

    /**
     * 成本趋势 (最近 months 个月)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/stats/cost-trend")
    public OperationResponse<List<Map<String, Object>>> getCostTrend(
            @RequestParam(defaultValue = "6") Integer months) {
        return OperationResponse.build(scrmCampaignAnalysisService.getCostTrend(months));
    }

    /**
     * 收入趋势 (最近 months 个月)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/stats/revenue-trend")
    public OperationResponse<List<Map<String, Object>>> getRevenueTrend(
            @RequestParam(defaultValue = "6") Integer months) {
        return OperationResponse.build(scrmCampaignAnalysisService.getRevenueTrend(months));
    }

    /**
     * Top 活动 (按收入倒序)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 活动排行列表
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/stats/top-campaigns")
    public OperationResponse<List<Map<String, Object>>> getTopCampaigns(
            @RequestParam(defaultValue = "10") Integer limit) {
        return OperationResponse.build(scrmCampaignAnalysisService.getTopCampaigns(limit));
    }

    /**
     * Top 渠道 (按效率评分倒序)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 渠道排行列表
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/stats/top-channels")
    public OperationResponse<List<Map<String, Object>>> getTopChannels(
            @RequestParam(defaultValue = "10") Integer limit) {
        return OperationResponse.build(scrmCampaignAnalysisService.getTopChannels(limit));
    }

    /**
     * 活动性能分布 (按状态分组统计)。
     *
     * @return 性能分布
     */
    @RequirePermission(resource = "scrm_campaign_analysis", action = "read")
    @GetMapping("/stats/distribution")
    public OperationResponse<Map<String, Object>> getCampaignPerformanceDistribution() {
        return OperationResponse.build(scrmCampaignAnalysisService.getCampaignPerformanceDistribution());
    }
}
