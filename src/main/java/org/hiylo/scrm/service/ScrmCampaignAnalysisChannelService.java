/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignAnalysisChannelService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCampaignAnalysisDto;
import org.hiylo.scrm.dto.ScrmCampaignChannelDto;
import org.hiylo.scrm.dto.ScrmCampaignFunnelDto;
import org.hiylo.scrm.entity.ScrmCampaignChannelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCampaignChannelRepository;
import org.hiylo.scrm.repository.ScrmCampaignFunnelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 营销活动效果分析服务 - 渠道管理子域。
 * <p>
 * 承载渠道效果的增删改查 / 渠道性能分析 (计算各渠道指标 → 排名 → 识别最优最差) /
 * 最优与最差渠道 / 渠道对比 / 渠道 ROI / 渠道效率评分, 以及分析导出 (含渠道与漏斗)。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCampaignAnalysisChannelService {

    /** 效率评分满分 */
    private static final double EFFICIENCY_FULL_SCORE = 100d;

    /** 渠道效果数据访问层 */
    private final ScrmCampaignChannelRepository channelRepository;

    /** 转化漏斗数据访问层 */
    private final ScrmCampaignFunnelRepository funnelRepository;

    /** 分析管理子域服务 (共享分析查询 / 安全取值 / 指标计算) */
    private final ScrmCampaignAnalysisManageService manageService;

    /** 漏斗分析子域服务 (共享漏斗 DTO 转换) */
    private final ScrmCampaignAnalysisFunnelService funnelService;

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
    @Transactional
    public ScrmCampaignChannelDto addChannel(ScrmCampaignChannelDto dto) throws ScrmException {
        validateChannelDto(dto, false);
        manageService.findAnalysisOrThrow(dto.getAnalysisId());
        ScrmCampaignChannelEntity entity = new ScrmCampaignChannelEntity();
        entity.setAnalysisId(dto.getAnalysisId());
        entity.setCampaignId(dto.getCampaignId());
        entity.setChannelName(dto.getChannelName());
        entity.setChannelType(dto.getChannelType());
        entity.setChannelCost(dto.getChannelCost() != null ? dto.getChannelCost() : 0d);
        entity.setReachCount(dto.getReachCount());
        entity.setImpressionCount(dto.getImpressionCount());
        entity.setClickCount(dto.getClickCount());
        entity.setRegistrationCount(dto.getRegistrationCount());
        entity.setParticipationCount(dto.getParticipationCount());
        entity.setConversionCount(dto.getConversionCount());
        entity.setRevenue(dto.getRevenue());
        entity.setNewCustomerCount(dto.getNewCustomerCount());
        entity.setRepeatCustomerCount(dto.getRepeatCustomerCount());
        entity.setAverageOrderValue(dto.getAverageOrderValue());
        entity.setEngagementRate(dto.getEngagementRate());
        entity.setBounceRate(dto.getBounceRate());
        entity.setShareRate(dto.getShareRate());
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : manageService.DEFAULT_OPERATOR);
        entity.setIsBestPerformer(false);
        entity.setIsUnderperforming(false);
        recalcChannelMetrics(entity);
        entity = channelRepository.save(entity);
        log.info("新增渠道效果: id={}, analysisId={}, channelName={}",
                entity.getId(), entity.getAnalysisId(), entity.getChannelName());
        return toChannelDto(entity);
    }

    /**
     * 更新渠道效果 (字段非空才覆盖)。
     *
     * @param id  渠道 ID
     * @param dto 渠道参数
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在 / 参数非法
     */
    @Transactional
    public ScrmCampaignChannelDto updateChannel(Long id, ScrmCampaignChannelDto dto) throws ScrmException {
        ScrmCampaignChannelEntity entity = findChannelOrThrow(id);
        validateChannelDto(dto, true);
        if (dto.getChannelName() != null) entity.setChannelName(dto.getChannelName());
        if (dto.getChannelType() != null) entity.setChannelType(dto.getChannelType());
        if (dto.getChannelCost() != null) entity.setChannelCost(dto.getChannelCost());
        if (dto.getReachCount() != null) entity.setReachCount(dto.getReachCount());
        if (dto.getImpressionCount() != null) entity.setImpressionCount(dto.getImpressionCount());
        if (dto.getClickCount() != null) entity.setClickCount(dto.getClickCount());
        if (dto.getRegistrationCount() != null) entity.setRegistrationCount(dto.getRegistrationCount());
        if (dto.getParticipationCount() != null) entity.setParticipationCount(dto.getParticipationCount());
        if (dto.getConversionCount() != null) entity.setConversionCount(dto.getConversionCount());
        if (dto.getRevenue() != null) entity.setRevenue(dto.getRevenue());
        if (dto.getNewCustomerCount() != null) entity.setNewCustomerCount(dto.getNewCustomerCount());
        if (dto.getRepeatCustomerCount() != null) entity.setRepeatCustomerCount(dto.getRepeatCustomerCount());
        if (dto.getAverageOrderValue() != null) entity.setAverageOrderValue(dto.getAverageOrderValue());
        if (dto.getEngagementRate() != null) entity.setEngagementRate(dto.getEngagementRate());
        if (dto.getBounceRate() != null) entity.setBounceRate(dto.getBounceRate());
        if (dto.getShareRate() != null) entity.setShareRate(dto.getShareRate());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        recalcChannelMetrics(entity);
        entity = channelRepository.save(entity);
        log.info("更新渠道效果: id={}, channelName={}", entity.getId(), entity.getChannelName());
        return toChannelDto(entity);
    }

    /**
     * 删除渠道效果。
     *
     * @param id 渠道 ID
     * @throws ScrmException 渠道不存在
     */
    @Transactional
    public void deleteChannel(Long id) throws ScrmException {
        ScrmCampaignChannelEntity entity = findChannelOrThrow(id);
        channelRepository.delete(entity);
        log.info("删除渠道效果: id={}, channelName={}", id, entity.getChannelName());
    }

    /**
     * 查询渠道详情。
     *
     * @param id 渠道 ID
     * @return 渠道 DTO
     * @throws ScrmException 渠道不存在
     */
    @Transactional(readOnly = true)
    public ScrmCampaignChannelDto getChannel(Long id) throws ScrmException {
        return toChannelDto(findChannelOrThrow(id));
    }

    /**
     * 按分析 ID 查询渠道列表。
     *
     * @param analysisId 分析 ID
     * @return 渠道列表
     * @throws ScrmException 分析不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmCampaignChannelDto> getChannelsByAnalysis(Long analysisId) throws ScrmException {
        manageService.findAnalysisOrThrow(analysisId);
        return channelRepository.findByAnalysisId(analysisId)
                .stream().map(this::toChannelDto).collect(Collectors.toList());
    }

    /**
     * 分析渠道性能: 计算各渠道指标 → 排名 → 识别最优/最差渠道。
     *
     * @param analysisId 分析 ID
     * @return 渠道性能分析结果
     * @throws ScrmException 分析不存在
     */
    @Transactional
    public List<ScrmCampaignChannelDto> analyzeChannelPerformance(Long analysisId) throws ScrmException {
        manageService.findAnalysisOrThrow(analysisId);
        List<ScrmCampaignChannelEntity> channels = channelRepository
                .findByAnalysisId(analysisId);
        if (channels.isEmpty()) {
            return new ArrayList<>();
        }
        double totalCost = channels.stream()
                .mapToDouble(c -> c.getChannelCost() != null ? c.getChannelCost() : 0d).sum();
        double totalRevenue = channels.stream()
                .mapToDouble(c -> c.getRevenue() != null ? c.getRevenue() : 0d).sum();
        // 重算各渠道指标与效率评分
        for (ScrmCampaignChannelEntity c : channels) {
            recalcChannelMetrics(c);
            c.setCostWeight(totalCost > 0
                    ? manageService.round2(manageService.safe(c.getChannelCost()) / totalCost) : 0d);
            c.setRevenueWeight(totalRevenue > 0
                    ? manageService.round2(manageService.safe(c.getRevenue()) / totalRevenue) : 0d);
            c.setEfficiencyScore(manageService.round2(calculateChannelEfficiencyScore(c)));
        }
        // 重置最优/最差标记, 按 ROI 降序排名
        channels.forEach(c -> {
            c.setIsBestPerformer(false);
            c.setIsUnderperforming(false);
        });
        channels.sort(Comparator.comparingDouble(
                (ScrmCampaignChannelEntity c) -> manageService.safe(c.getRoi())).reversed());
        if (channels.size() == 1) {
            channels.get(0).setIsBestPerformer(true);
        } else if (channels.size() > 1) {
            channels.get(0).setIsBestPerformer(true);
            channels.get(channels.size() - 1).setIsUnderperforming(true);
        }
        channelRepository.saveAll(channels);
        log.info("分析渠道性能: analysisId={}, channelCount={}", analysisId, channels.size());
        return channels.stream().map(this::toChannelDto).collect(Collectors.toList());
    }

    /**
     * 查询最优渠道。
     *
     * @param analysisId 分析 ID
     * @return 最优渠道 DTO
     * @throws ScrmException 分析不存在 / 无最优渠道
     */
    @Transactional(readOnly = true)
    public ScrmCampaignChannelDto getBestChannel(Long analysisId) throws ScrmException {
        manageService.findAnalysisOrThrow(analysisId);
        List<ScrmCampaignChannelEntity> channels = channelRepository
                .findByAnalysisId(analysisId);
        ScrmCampaignChannelEntity best = channels.stream()
                .max(Comparator.comparingDouble(c -> manageService.safe(c.getRoi())))
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "未找到最优渠道: analysisId=" + analysisId));
        return toChannelDto(best);
    }

    /**
     * 查询最差渠道。
     *
     * @param analysisId 分析 ID
     * @return 最差渠道 DTO
     * @throws ScrmException 分析不存在 / 无最差渠道
     */
    @Transactional(readOnly = true)
    public ScrmCampaignChannelDto getWorstChannel(Long analysisId) throws ScrmException {
        manageService.findAnalysisOrThrow(analysisId);
        List<ScrmCampaignChannelEntity> channels = channelRepository
                .findByAnalysisId(analysisId);
        ScrmCampaignChannelEntity worst = channels.stream()
                .min(Comparator.comparingDouble(c -> manageService.safe(c.getRoi())))
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "未找到最差渠道: analysisId=" + analysisId));
        return toChannelDto(worst);
    }

    /**
     * 多渠道效果对比。
     *
     * @param channelIds 渠道 ID 列表
     * @return 对比结果列表
     * @throws ScrmException 列表为空
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> compareChannels(List<Long> channelIds) throws ScrmException {
        if (channelIds == null || channelIds.isEmpty()) {
            throw ScrmException.badRequest("对比渠道 ID 列表不能为空");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : channelIds) {
            ScrmCampaignChannelEntity entity = channelRepository.findById(id)
                    .filter(e -> true)
                    .orElse(null);
            if (entity == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channelId", entity.getId());
            m.put("channelName", entity.getChannelName());
            m.put("channelType", entity.getChannelType());
            m.put("channelCost", manageService.round2(manageService.safe(entity.getChannelCost())));
            m.put("revenue", manageService.round2(manageService.safe(entity.getRevenue())));
            m.put("roi", entity.getRoi());
            m.put("cpa", entity.getCpa());
            m.put("cpc", entity.getCpc());
            m.put("conversionRate", entity.getConversionRate());
            m.put("efficiencyScore", entity.getEfficiencyScore());
            result.add(m);
        }
        return result;
    }

    /**
     * 查询分析的渠道 ROI 汇总。
     *
     * @param analysisId 分析 ID
     * @return 渠道 ROI 汇总
     * @throws ScrmException 分析不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChannelROI(Long analysisId) throws ScrmException {
        manageService.findAnalysisOrThrow(analysisId);
        List<ScrmCampaignChannelEntity> channels = channelRepository
                .findByAnalysisId(analysisId);
        double totalCost = channels.stream()
                .mapToDouble(c -> manageService.safe(c.getChannelCost())).sum();
        double totalRevenue = channels.stream()
                .mapToDouble(c -> manageService.safe(c.getRevenue())).sum();
        double totalProfit = totalRevenue - totalCost;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysisId", analysisId);
        result.put("channelCount", channels.size());
        result.put("totalCost", manageService.round2(totalCost));
        result.put("totalRevenue", manageService.round2(totalRevenue));
        result.put("totalProfit", manageService.round2(totalProfit));
        result.put("overallRoi", totalCost > 0 ? manageService.round2(totalProfit / totalCost) : 0d);
        result.put("channels", channels.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channelId", c.getId());
            m.put("channelName", c.getChannelName());
            m.put("channelType", c.getChannelType());
            m.put("cost", manageService.round2(manageService.safe(c.getChannelCost())));
            m.put("revenue", manageService.round2(manageService.safe(c.getRevenue())));
            m.put("roi", c.getRoi());
            m.put("efficiencyScore", c.getEfficiencyScore());
            return m;
        }).collect(Collectors.toList()));
        return result;
    }

    /**
     * 计算渠道效率评分。
     * <p>
     * 综合考量 ROI (40%) / 转化率 (30%) / 收入占比 (30%), 满分 100。
     * </p>
     *
     * @param channelId 渠道 ID
     * @return 效率评分
     * @throws ScrmException 渠道不存在
     */
    @Transactional
    public Double calculateChannelEfficiency(Long channelId) throws ScrmException {
        ScrmCampaignChannelEntity entity = findChannelOrThrow(channelId);
        return manageService.round2(calculateChannelEfficiencyScore(entity));
    }

    /**
     * 导出分析 (返回完整分析数据, 含渠道与漏斗)。
     *
     * @param id 分析 ID
     * @return 导出数据
     * @throws ScrmException 分析不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportAnalysis(Long id) throws ScrmException {
        ScrmCampaignAnalysisDto analysis = manageService.getAnalysis(id);
        List<ScrmCampaignChannelDto> channels = channelRepository
                .findByAnalysisId(id).stream()
                .map(this::toChannelDto).collect(Collectors.toList());
        List<ScrmCampaignFunnelDto> funnels = funnelRepository
                .findByAnalysisIdOrderByStageOrderAsc(id).stream()
                .map(funnelService::toFunnelDto).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysis", analysis);
        result.put("channels", channels);
        result.put("funnels", funnels);
        result.put("exportedAt", LocalDateTime.now());
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询渠道, 不存在抛异常。
     */
    private ScrmCampaignChannelEntity findChannelOrThrow(Long id) throws ScrmException {
        ScrmCampaignChannelEntity entity = channelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "渠道效果不存在: id=" + id));
        return entity;
    }

    /**
     * 重算渠道效果派生指标 (CTR/CVR/ROI/CPC/CPA/CPM/利润)。
     */
    private void recalcChannelMetrics(ScrmCampaignChannelEntity c) {
        double cost = manageService.safe(c.getChannelCost());
        double revenue = manageService.safe(c.getRevenue());
        int impression = manageService.safe(c.getImpressionCount());
        int click = manageService.safe(c.getClickCount());
        int conversion = manageService.safe(c.getConversionCount());
        int reach = manageService.safe(c.getReachCount());
        c.setClickThroughRate(impression > 0 ? manageService.round2((double) click / impression) : 0d);
        c.setConversionRate(reach > 0 ? manageService.round2((double) conversion / reach) : 0d);
        c.setProfit(manageService.round2(revenue - cost));
        c.setRoi(manageService.calculateROI(revenue, cost));
        c.setCpc(click > 0 ? manageService.round2(cost / click) : 0d);
        c.setCpa(conversion > 0 ? manageService.round2(cost / conversion) : 0d);
        c.setCpm(impression > 0 ? manageService.round2(cost / impression * manageService.CPM_FACTOR) : 0d);
    }

    /**
     * 计算渠道效率评分: ROI(40%) + 转化率(30%) + 收入占比(30%), 满分 100。
     */
    private double calculateChannelEfficiencyScore(ScrmCampaignChannelEntity c) {
        double roi = manageService.safe(c.getRoi());
        double conversionRate = manageService.safe(c.getConversionRate());
        double revenueWeight = manageService.safe(c.getRevenueWeight());
        // ROI 归一化到 0-100 (ROI>=1 视为满分)
        double roiScore = Math.min(1d, roi) * EFFICIENCY_FULL_SCORE;
        double conversionScore = Math.min(1d, conversionRate) * EFFICIENCY_FULL_SCORE;
        double revenueScore = revenueWeight * EFFICIENCY_FULL_SCORE;
        return manageService.round2(roiScore * 0.4d + conversionScore * 0.3d + revenueScore * 0.3d);
    }

    /**
     * 校验渠道 DTO。
     */
    private void validateChannelDto(ScrmCampaignChannelDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("渠道参数不能为空");
        }
        if (dto.getChannelName() != null) {
            if (dto.getChannelName().isBlank()) {
                throw ScrmException.badRequest("渠道名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("渠道名称不能为空");
        }
        if (!partial && dto.getAnalysisId() == null) {
            throw ScrmException.badRequest("分析 ID 不能为空");
        }
        if (!partial && dto.getChannelType() == null) {
            throw ScrmException.badRequest("渠道类型不能为空");
        }
    }

    /**
     * 渠道效果实体转 DTO。
     */
    private ScrmCampaignChannelDto toChannelDto(ScrmCampaignChannelEntity c) {
        ScrmCampaignChannelDto dto = new ScrmCampaignChannelDto();
        dto.setId(c.getId());
        dto.setAnalysisId(c.getAnalysisId());
        dto.setCampaignId(c.getCampaignId());
        dto.setChannelName(c.getChannelName());
        dto.setChannelType(c.getChannelType());
        dto.setChannelCost(c.getChannelCost());
        dto.setReachCount(c.getReachCount());
        dto.setImpressionCount(c.getImpressionCount());
        dto.setClickCount(c.getClickCount());
        dto.setClickThroughRate(c.getClickThroughRate());
        dto.setRegistrationCount(c.getRegistrationCount());
        dto.setParticipationCount(c.getParticipationCount());
        dto.setConversionCount(c.getConversionCount());
        dto.setConversionRate(c.getConversionRate());
        dto.setRevenue(c.getRevenue());
        dto.setProfit(c.getProfit());
        dto.setRoi(c.getRoi());
        dto.setCpc(c.getCpc());
        dto.setCpa(c.getCpa());
        dto.setCpm(c.getCpm());
        dto.setNewCustomerCount(c.getNewCustomerCount());
        dto.setRepeatCustomerCount(c.getRepeatCustomerCount());
        dto.setAverageOrderValue(c.getAverageOrderValue());
        dto.setEngagementRate(c.getEngagementRate());
        dto.setBounceRate(c.getBounceRate());
        dto.setShareRate(c.getShareRate());
        dto.setCostWeight(c.getCostWeight());
        dto.setRevenueWeight(c.getRevenueWeight());
        dto.setEfficiencyScore(c.getEfficiencyScore());
        dto.setIsBestPerformer(c.getIsBestPerformer());
        dto.setIsUnderperforming(c.getIsUnderperforming());
        dto.setNotes(c.getNotes());
        dto.setCreatedBy(c.getCreatedBy());
        dto.setCreateTime(c.getCreateTime());
        dto.setUpdateTime(c.getUpdateTime());
        return dto;
    }
}