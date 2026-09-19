/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignAnalysisFunnelService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCampaignFunnelDto;
import org.hiylo.scrm.entity.ScrmCampaignAnalysisEntity;
import org.hiylo.scrm.entity.ScrmCampaignFunnelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCampaignFunnelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 营销活动效果分析服务 - 转化漏斗子域。
 * <p>
 * 承载漏斗阶段的增删改查 / 构建漏斗 (按类型 → 阶段 → 计算转化流失) /
 * 分析漏斗 (识别瓶颈 → 优化建议) / 瓶颈阶段 / 转化率与流失率 / 优化 / 对比。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCampaignAnalysisFunnelService {

    /** 漏斗类型: 认知 */
    private static final String FUNNEL_AWARENESS = "AWARENESS";
    /** 漏斗类型: 注册 */
    private static final String FUNNEL_REGISTRATION = "REGISTRATION";
    /** 漏斗类型: 购买 */
    private static final String FUNNEL_PURCHASE = "PURCHASE";
    /** 漏斗类型: 互动 */
    private static final String FUNNEL_ENGAGEMENT = "ENGAGEMENT";
    /** 漏斗类型: 留存 */
    private static final String FUNNEL_RETENTION = "RETENTION";
    /** 漏斗类型: 自定义 */
    private static final String FUNNEL_CUSTOM = "CUSTOM";

    /** 转化漏斗数据访问层 */
    private final ScrmCampaignFunnelRepository funnelRepository;

    /** 分析管理子域服务 (共享分析查询 / 安全取值 / 精度处理) */
    private final ScrmCampaignAnalysisManageService manageService;

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
    @Transactional
    public ScrmCampaignFunnelDto addFunnelStage(ScrmCampaignFunnelDto dto) throws ScrmException {
        validateFunnelDto(dto, false);
        manageService.findAnalysisOrThrow(dto.getAnalysisId());
        ScrmCampaignFunnelEntity entity = new ScrmCampaignFunnelEntity();
        entity.setAnalysisId(dto.getAnalysisId());
        entity.setCampaignId(dto.getCampaignId());
        entity.setFunnelName(dto.getFunnelName());
        entity.setFunnelType(dto.getFunnelType() != null ? dto.getFunnelType() : FUNNEL_PURCHASE);
        entity.setStageName(dto.getStageName());
        entity.setStageOrder(dto.getStageOrder());
        entity.setStageType(dto.getStageType());
        entity.setEntryCount(dto.getEntryCount());
        entity.setExitCount(dto.getExitCount());
        entity.setConversionCount(dto.getConversionCount());
        entity.setAvgTimeSpent(dto.getAvgTimeSpent());
        entity.setRevenue(dto.getRevenue());
        entity.setCost(dto.getCost());
        entity.setOptimizationNotes(dto.getOptimizationNotes());
        entity.setMetadata(dto.getMetadata());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : manageService.DEFAULT_OPERATOR);
        entity.setIsBottleneck(false);
        recalcFunnelMetrics(entity);
        entity = funnelRepository.save(entity);
        log.info("新增漏斗阶段: id={}, analysisId={}, stageName={}",
                entity.getId(), entity.getAnalysisId(), entity.getStageName());
        return toFunnelDto(entity);
    }

    /**
     * 更新漏斗阶段 (字段非空才覆盖)。
     *
     * @param id  漏斗阶段 ID
     * @param dto 漏斗阶段参数
     * @return 更新后的漏斗阶段
     * @throws ScrmException 漏斗阶段不存在 / 参数非法
     */
    @Transactional
    public ScrmCampaignFunnelDto updateFunnelStage(Long id, ScrmCampaignFunnelDto dto) throws ScrmException {
        ScrmCampaignFunnelEntity entity = findFunnelOrThrow(id);
        validateFunnelDto(dto, true);
        if (dto.getFunnelName() != null) entity.setFunnelName(dto.getFunnelName());
        if (dto.getFunnelType() != null) entity.setFunnelType(dto.getFunnelType());
        if (dto.getStageName() != null) entity.setStageName(dto.getStageName());
        if (dto.getStageOrder() != null) entity.setStageOrder(dto.getStageOrder());
        if (dto.getStageType() != null) entity.setStageType(dto.getStageType());
        if (dto.getEntryCount() != null) entity.setEntryCount(dto.getEntryCount());
        if (dto.getExitCount() != null) entity.setExitCount(dto.getExitCount());
        if (dto.getConversionCount() != null) entity.setConversionCount(dto.getConversionCount());
        if (dto.getAvgTimeSpent() != null) entity.setAvgTimeSpent(dto.getAvgTimeSpent());
        if (dto.getRevenue() != null) entity.setRevenue(dto.getRevenue());
        if (dto.getCost() != null) entity.setCost(dto.getCost());
        if (dto.getOptimizationNotes() != null) entity.setOptimizationNotes(dto.getOptimizationNotes());
        if (dto.getMetadata() != null) entity.setMetadata(dto.getMetadata());
        recalcFunnelMetrics(entity);
        entity = funnelRepository.save(entity);
        log.info("更新漏斗阶段: id={}, stageName={}", entity.getId(), entity.getStageName());
        return toFunnelDto(entity);
    }

    /**
     * 删除漏斗阶段。
     *
     * @param id 漏斗阶段 ID
     * @throws ScrmException 漏斗阶段不存在
     */
    @Transactional
    public void deleteFunnelStage(Long id) throws ScrmException {
        ScrmCampaignFunnelEntity entity = findFunnelOrThrow(id);
        funnelRepository.delete(entity);
        log.info("删除漏斗阶段: id={}, stageName={}", id, entity.getStageName());
    }

    /**
     * 查询漏斗阶段详情。
     *
     * @param id 漏斗阶段 ID
     * @return 漏斗阶段 DTO
     * @throws ScrmException 漏斗阶段不存在
     */
    @Transactional(readOnly = true)
    public ScrmCampaignFunnelDto getFunnelStage(Long id) throws ScrmException {
        return toFunnelDto(findFunnelOrThrow(id));
    }

    /**
     * 按分析 ID 查询漏斗阶段列表 (按阶段顺序升序)。
     *
     * @param analysisId 分析 ID
     * @return 漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmCampaignFunnelDto> getFunnelByAnalysis(Long analysisId) throws ScrmException {
        manageService.findAnalysisOrThrow(analysisId);
        return funnelRepository
                .findByAnalysisIdOrderByStageOrderAsc(analysisId)
                .stream().map(this::toFunnelDto).collect(Collectors.toList());
    }

    /**
     * 构建漏斗: 按类型 → 阶段 → 计算转化流失。
     * <p>
     * 根据漏斗类型自动生成标准阶段模板, 并按各阶段入口/转化数计算转化率与流失率,
     * 识别瓶颈阶段 (转化率最低)。
     * </p>
     *
     * @param analysisId 分析 ID
     * @param funnelType 漏斗类型
     * @return 漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    @Transactional
    public List<ScrmCampaignFunnelDto> buildFunnel(Long analysisId, String funnelType) throws ScrmException {
        ScrmCampaignAnalysisEntity analysis = manageService.findAnalysisOrThrow(analysisId);
        // 清理旧漏斗
        List<ScrmCampaignFunnelEntity> existing = funnelRepository
                .findByAnalysisId(analysisId);
        if (!existing.isEmpty()) {
            funnelRepository.deleteAll(existing);
        }
        List<String[]> stages = defaultFunnelStages(funnelType);
        List<ScrmCampaignFunnelEntity> entities = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            String[] stage = stages.get(i);
            ScrmCampaignFunnelEntity entity = new ScrmCampaignFunnelEntity();
            entity.setAnalysisId(analysisId);
            entity.setCampaignId(analysis.getCampaignId());
            entity.setFunnelName(funnelType + "_FUNNEL");
            entity.setFunnelType(funnelType != null ? funnelType : FUNNEL_PURCHASE);
            entity.setStageName(stage[0]);
            entity.setStageOrder(i + 1);
            entity.setStageType(stage[1]);
            entity.setIsBottleneck(false);
            entities.add(entity);
        }
        funnelRepository.saveAll(entities);
        log.info("构建漏斗: analysisId={}, funnelType={}, stageCount={}",
                analysisId, funnelType, entities.size());
        return entities.stream().map(this::toFunnelDto).collect(Collectors.toList());
    }

    /**
     * 分析漏斗: 识别瓶颈 → 生成优化建议。
     * <p>
     * 重算各阶段转化率与流失率, 将转化率最低阶段标记为瓶颈, 并写入优化建议。
     * </p>
     *
     * @param analysisId 分析 ID
     * @return 漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    @Transactional
    public List<ScrmCampaignFunnelDto> analyzeFunnel(Long analysisId) throws ScrmException {
        manageService.findAnalysisOrThrow(analysisId);
        List<ScrmCampaignFunnelEntity> funnels = funnelRepository
                .findByAnalysisIdOrderByStageOrderAsc(analysisId);
        if (funnels.isEmpty()) {
            return new ArrayList<>();
        }
        funnels.forEach(this::recalcFunnelMetrics);
        // 识别瓶颈: 转化率最低 (entry>0)
        ScrmCampaignFunnelEntity bottleneck = funnels.stream()
                .filter(f -> manageService.safe(f.getEntryCount()) > 0)
                .min(Comparator.comparingDouble(f -> manageService.safe(f.getConversionRate())))
                .orElse(null);
        funnels.forEach(f -> {
            f.setIsBottleneck(bottleneck != null && f.getId().equals(bottleneck.getId()));
            if (Boolean.TRUE.equals(f.getIsBottleneck())) {
                f.setOptimizationNotes("该阶段为转化瓶颈, 转化率仅"
                        + manageService.round2(manageService.safe(f.getConversionRate()) * manageService.PERCENT_FACTOR)
                        + "%, 建议优化转化路径");
            }
        });
        funnelRepository.saveAll(funnels);
        log.info("分析漏斗: analysisId={}, stageCount={}, bottleneckStage={}",
                analysisId, funnels.size(), bottleneck != null ? bottleneck.getStageName() : "无");
        return funnels.stream().map(this::toFunnelDto).collect(Collectors.toList());
    }

    /**
     * 查询瓶颈阶段。
     *
     * @param analysisId 分析 ID
     * @return 瓶颈阶段 DTO
     * @throws ScrmException 分析不存在 / 无瓶颈
     */
    @Transactional(readOnly = true)
    public ScrmCampaignFunnelDto getBottleneckStage(Long analysisId) throws ScrmException {
        manageService.findAnalysisOrThrow(analysisId);
        List<ScrmCampaignFunnelEntity> funnels = funnelRepository
                .findByAnalysisIdOrderByStageOrderAsc(analysisId);
        ScrmCampaignFunnelEntity bottleneck = funnels.stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsBottleneck()))
                .findFirst()
                .orElseGet(() -> funnels.stream()
                        .filter(f -> manageService.safe(f.getEntryCount()) > 0)
                        .min(Comparator.comparingDouble(f -> manageService.safe(f.getConversionRate())))
                        .orElse(null));
        if (bottleneck == null) {
            throw new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                    "未找到瓶颈阶段: analysisId=" + analysisId);
        }
        return toFunnelDto(bottleneck);
    }

    /**
     * 查询漏斗整体转化率 (首阶段入口 → 末阶段转化)。
     *
     * @param analysisId 分析 ID
     * @return 漏斗转化率
     * @throws ScrmException 分析不存在
     */
    @Transactional(readOnly = true)
    public Double getFunnelConversionRate(Long analysisId) throws ScrmException {
        manageService.findAnalysisOrThrow(analysisId);
        List<ScrmCampaignFunnelEntity> funnels = funnelRepository
                .findByAnalysisIdOrderByStageOrderAsc(analysisId);
        if (funnels.isEmpty()) {
            return 0d;
        }
        int firstEntry = manageService.safe(funnels.get(0).getEntryCount());
        int lastConversion = manageService.safe(funnels.get(funnels.size() - 1).getConversionCount());
        return firstEntry > 0 ? manageService.round2((double) lastConversion / firstEntry) : 0d;
    }

    /**
     * 查询漏斗整体流失率 (1 - 转化率)。
     *
     * @param analysisId 分析 ID
     * @return 漏斗流失率
     * @throws ScrmException 分析不存在
     */
    @Transactional(readOnly = true)
    public Double getFunnelDropoffRate(Long analysisId) throws ScrmException {
        double conversionRate = getFunnelConversionRate(analysisId);
        return manageService.round2(1d - conversionRate);
    }

    /**
     * 优化漏斗: 为各阶段生成优化建议。
     *
     * @param analysisId 分析 ID
     * @return 优化后的漏斗阶段列表
     * @throws ScrmException 分析不存在
     */
    @Transactional
    public List<ScrmCampaignFunnelDto> optimizeFunnel(Long analysisId) throws ScrmException {
        analyzeFunnel(analysisId);
        List<ScrmCampaignFunnelEntity> entities = funnelRepository
                .findByAnalysisIdOrderByStageOrderAsc(analysisId);
        for (ScrmCampaignFunnelEntity entity : entities) {
            if (entity.getOptimizationNotes() == null || entity.getOptimizationNotes().isBlank()) {
                double dropoffRate = manageService.safe(entity.getDropoffRate());
                if (dropoffRate > 0.5d) {
                    entity.setOptimizationNotes("流失率较高 ("
                            + manageService.round2(dropoffRate * manageService.PERCENT_FACTOR)
                            + "%), 建议简化流程并强化引导");
                } else {
                    entity.setOptimizationNotes("表现良好, 建议保持当前策略");
                }
            }
        }
        funnelRepository.saveAll(entities);
        return entities.stream().map(this::toFunnelDto).collect(Collectors.toList());
    }

    /**
     * 多漏斗对比。
     *
     * @param analysisIds 分析 ID 列表
     * @return 对比结果列表
     * @throws ScrmException 列表为空
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> compareFunnels(List<Long> analysisIds) throws ScrmException {
        if (analysisIds == null || analysisIds.isEmpty()) {
            throw ScrmException.badRequest("对比分析 ID 列表不能为空");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long analysisId : analysisIds) {
            List<ScrmCampaignFunnelEntity> funnels = funnelRepository
                    .findByAnalysisIdOrderByStageOrderAsc(analysisId);
            int firstEntry = funnels.isEmpty() ? 0 : manageService.safe(funnels.get(0).getEntryCount());
            int lastConversion = funnels.isEmpty() ? 0
                    : manageService.safe(funnels.get(funnels.size() - 1).getConversionCount());
            double overallRate = firstEntry > 0 ? (double) lastConversion / firstEntry : 0d;
            long bottleneckCount = funnels.stream()
                    .filter(f -> Boolean.TRUE.equals(f.getIsBottleneck())).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("analysisId", analysisId);
            m.put("stageCount", funnels.size());
            m.put("firstStageEntry", firstEntry);
            m.put("lastStageConversion", lastConversion);
            m.put("overallConversionRate", manageService.round2(overallRate));
            m.put("overallDropoffRate", manageService.round2(1d - overallRate));
            m.put("bottleneckCount", bottleneckCount);
            result.add(m);
        }
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询漏斗阶段, 不存在抛异常。
     */
    private ScrmCampaignFunnelEntity findFunnelOrThrow(Long id) throws ScrmException {
        ScrmCampaignFunnelEntity entity = funnelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "漏斗阶段不存在: id=" + id));
        return entity;
    }

    /**
     * 重算漏斗阶段派生指标 (转化数/流失数/转化率/流失率)。
     */
    private void recalcFunnelMetrics(ScrmCampaignFunnelEntity f) {
        int entry = manageService.safe(f.getEntryCount());
        int exit = manageService.safe(f.getExitCount());
        int conversion = manageService.safe(f.getConversionCount());
        if (conversion == 0 && exit > 0) {
            conversion = exit;
            f.setConversionCount(conversion);
        }
        int dropoff = Math.max(0, entry - conversion);
        f.setDropoffCount(dropoff);
        f.setConversionRate(entry > 0 ? manageService.round2((double) conversion / entry) : 0d);
        f.setDropoffRate(entry > 0 ? manageService.round2((double) dropoff / entry) : 0d);
    }

    /**
     * 按漏斗类型返回默认阶段模板 (stageName, stageType)。
     */
    private List<String[]> defaultFunnelStages(String funnelType) {
        List<String[]> stages = new ArrayList<>();
        if (FUNNEL_AWARENESS.equals(funnelType)) {
            stages.add(new String[]{"曝光", "AWARENESS"});
            stages.add(new String[]{"点击", "INTEREST"});
            stages.add(new String[]{"互动", "CONSIDERATION"});
            stages.add(new String[]{"分享", "ADVOCACY"});
        } else if (FUNNEL_REGISTRATION.equals(funnelType)) {
            stages.add(new String[]{"访问", "AWARENESS"});
            stages.add(new String[]{"浏览", "INTEREST"});
            stages.add(new String[]{"注册", "INTENT"});
            stages.add(new String[]{"激活", "RETENTION"});
        } else if (FUNNEL_ENGAGEMENT.equals(funnelType)) {
            stages.add(new String[]{"触达", "AWARENESS"});
            stages.add(new String[]{"打开", "INTEREST"});
            stages.add(new String[]{"互动", "CONSIDERATION"});
            stages.add(new String[]{"深度互动", "ADVOCACY"});
        } else if (FUNNEL_RETENTION.equals(funnelType)) {
            stages.add(new String[]{"首购", "PURCHASE"});
            stages.add(new String[]{"复购", "RETENTION"});
            stages.add(new String[]{"活跃", "ADVOCACY"});
        } else if (FUNNEL_CUSTOM.equals(funnelType)) {
            stages.add(new String[]{"阶段一", "AWARENESS"});
            stages.add(new String[]{"阶段二", "INTEREST"});
            stages.add(new String[]{"阶段三", "CONSIDERATION"});
        } else {
            // 默认 PURCHASE 购买漏斗
            stages.add(new String[]{"认知", "AWARENESS"});
            stages.add(new String[]{"兴趣", "INTEREST"});
            stages.add(new String[]{"考虑", "CONSIDERATION"});
            stages.add(new String[]{"意向", "INTENT"});
            stages.add(new String[]{"购买", "PURCHASE"});
        }
        return stages;
    }

    /**
     * 校验漏斗 DTO。
     */
    private void validateFunnelDto(ScrmCampaignFunnelDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("漏斗参数不能为空");
        }
        if (dto.getStageName() != null) {
            if (dto.getStageName().isBlank()) {
                throw ScrmException.badRequest("阶段名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("阶段名称不能为空");
        }
        if (!partial && dto.getAnalysisId() == null) {
            throw ScrmException.badRequest("分析 ID 不能为空");
        }
        if (!partial && dto.getStageOrder() == null) {
            throw ScrmException.badRequest("阶段顺序不能为空");
        }
        if (!partial && dto.getStageType() == null) {
            throw ScrmException.badRequest("阶段类型不能为空");
        }
        if (!partial && dto.getFunnelName() == null) {
            throw ScrmException.badRequest("漏斗名称不能为空");
        }
    }

    /**
     * 漏斗阶段实体转 DTO。
     */
    ScrmCampaignFunnelDto toFunnelDto(ScrmCampaignFunnelEntity f) {
        ScrmCampaignFunnelDto dto = new ScrmCampaignFunnelDto();
        dto.setId(f.getId());
        dto.setAnalysisId(f.getAnalysisId());
        dto.setCampaignId(f.getCampaignId());
        dto.setFunnelName(f.getFunnelName());
        dto.setFunnelType(f.getFunnelType());
        dto.setStageName(f.getStageName());
        dto.setStageOrder(f.getStageOrder());
        dto.setStageType(f.getStageType());
        dto.setEntryCount(f.getEntryCount());
        dto.setExitCount(f.getExitCount());
        dto.setConversionCount(f.getConversionCount());
        dto.setDropoffCount(f.getDropoffCount());
        dto.setConversionRate(f.getConversionRate());
        dto.setDropoffRate(f.getDropoffRate());
        dto.setAvgTimeSpent(f.getAvgTimeSpent());
        dto.setRevenue(f.getRevenue());
        dto.setCost(f.getCost());
        dto.setIsBottleneck(f.getIsBottleneck());
        dto.setOptimizationNotes(f.getOptimizationNotes());
        dto.setMetadata(f.getMetadata());
        dto.setCreatedBy(f.getCreatedBy());
        dto.setCreateTime(f.getCreateTime());
        dto.setUpdateTime(f.getUpdateTime());
        return dto;
    }
}