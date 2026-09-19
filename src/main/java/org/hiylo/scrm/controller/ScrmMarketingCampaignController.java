/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCampaignLaunchDto;
import org.hiylo.scrm.dto.ScrmMarketingCampaignChannelDto;
import org.hiylo.scrm.dto.ScrmMarketingCampaignDto;
import org.hiylo.scrm.dto.ScrmMarketingCampaignParticipantDto;
import org.hiylo.scrm.entity.ScrmMarketingCampaignChannelEntity;
import org.hiylo.scrm.entity.ScrmMarketingCampaignEntity;
import org.hiylo.scrm.entity.ScrmMarketingCampaignParticipantEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmMarketingCampaignService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销活动控制器。
 * <p>
 * 提供多渠道营销活动的策划、执行、效果分析接口。涵盖活动 CRUD 与生命周期管理
 * (排期/启动/暂停/完成/取消/复制), 渠道配置与启动发送, 参与者记录与转化追踪,
 * 活动指标汇总与 ROI, 活动统计 / 渠道效果对比 / 活动时间线。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/marketing-campaigns")
@RequiredArgsConstructor
public class ScrmMarketingCampaignController {

    /** 营销活动服务 */
    private final ScrmMarketingCampaignService scrmMarketingCampaignService;

    // ============================================================
    // 活动管理
    // ============================================================

    /**
     * 创建营销活动。
     *
     * @param dto 活动参数
     * @return 创建后的活动
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmMarketingCampaignEntity>
            createCampaign(@Valid @RequestBody ScrmMarketingCampaignDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.createCampaign(dto));
    }

    /**
     * 更新营销活动。
     *
     * @param id  活动 ID
     * @param dto 活动参数
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 参数非法 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmMarketingCampaignEntity> updateCampaign(@PathVariable Long id,
                                                                           @RequestBody ScrmMarketingCampaignDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.updateCampaign(id, dto));
    }

    /**
     * 删除营销活动 (同时删除关联渠道与参与者)。
     *
     * @param id 活动 ID
     * @return 空响应
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteCampaign(@PathVariable Long id) throws ScrmException {
        scrmMarketingCampaignService.deleteCampaign(id);
        return OperationResponse.build();
    }

    /**
     * 查询活动详情。
     *
     * @param id 活动 ID
     * @return 活动详情
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmMarketingCampaignEntity> getCampaign(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.getCampaign(id));
    }

    /**
     * 分页查询活动列表。
     *
     * @param campaignType 活动类型过滤（可空）: PROMOTION/NEW_PRODUCT/SEASONAL/RETENTION/ACQUISITION/BRAND_AWARENESS/FLASH_SALE
     * @param status       状态过滤（可空）: DRAFT/SCHEDULED/RUNNING/PAUSED/COMPLETED/CANCELLED
     * @param managerId    负责人 ID 过滤（可空）
     * @param startDate    开始日期过滤 (活动开始日期 ≥ 此值, 可空, ISO 格式: yyyy-MM-dd)
     * @param endDate      结束日期过滤 (活动结束日期 ≤ 此值, 可空, ISO 格式: yyyy-MM-dd)
     * @param keyword      活动名称关键字模糊匹配（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 活动分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmMarketingCampaignEntity>> listCampaigns(
            @RequestParam(required = false) String campaignType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String managerId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmMarketingCampaignService.listCampaigns(
                campaignType, status, managerId, startDate, endDate, keyword, pageable));
    }

    /**
     * 排期活动。
     *
     * @param id   活动 ID
     * @param body 请求体, 可包含 scheduledAt 字段 (计划开始时间, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "update")
    @PostMapping("/{id}/schedule")
    public OperationResponse<ScrmMarketingCampaignEntity> scheduleCampaign(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, Object> body) throws ScrmException {
        LocalDateTime scheduledAt = body != null && body.get("scheduledAt") != null
                ? LocalDateTime.parse(body.get("scheduledAt").toString()) : null;
        return OperationResponse.build(scrmMarketingCampaignService.scheduleCampaign(id, scheduledAt));
    }

    /**
     * 启动活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "update")
    @PostMapping("/{id}/start")
    public OperationResponse<ScrmMarketingCampaignEntity> startCampaign(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.startCampaign(id));
    }

    /**
     * 暂停活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "update")
    @PostMapping("/{id}/pause")
    public OperationResponse<ScrmMarketingCampaignEntity> pauseCampaign(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.pauseCampaign(id));
    }

    /**
     * 完成活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "update")
    @PostMapping("/{id}/complete")
    public OperationResponse<ScrmMarketingCampaignEntity> completeCampaign(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.completeCampaign(id));
    }

    /**
     * 取消活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "update")
    @PostMapping("/{id}/cancel")
    public OperationResponse<ScrmMarketingCampaignEntity> cancelCampaign(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.cancelCampaign(id));
    }

    /**
     * 复制活动 (创建副本, 状态置为 DRAFT, 同步复制渠道配置)。
     *
     * @param id 源活动 ID
     * @return 复制后的活动
     * @throws ScrmException 源活动不存在
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/{id}/copy")
    public OperationResponse<ScrmMarketingCampaignEntity> copyCampaign(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.copyCampaign(id));
    }

    // ============================================================
    // 渠道管理
    // ============================================================

    /**
     * 新增活动渠道。
     *
     * @param campaignId 活动 ID
     * @param dto        渠道参数
     * @return 创建后的渠道
     * @throws ScrmException 活动不存在 / 渠道非法 / 渠道已存在
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{campaignId}/channels")
    public OperationResponse<ScrmMarketingCampaignChannelEntity> addChannel(
            @PathVariable Long campaignId,
            @Valid @RequestBody ScrmMarketingCampaignChannelDto dto) throws ScrmException {
        dto.setCampaignId(campaignId);
        return OperationResponse.build(scrmMarketingCampaignService.addChannel(campaignId, dto));
    }

    /**
     * 查询活动下全部渠道。
     *
     * @param campaignId 活动 ID
     * @return 渠道列表 (按渠道名升序)
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "read")
    @GetMapping("/{campaignId}/channels")
    public OperationResponse<List<ScrmMarketingCampaignChannelEntity>> listChannels(@PathVariable Long campaignId)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.listChannels(campaignId));
    }

    /**
     * 更新渠道配置。
     *
     * @param id  渠道 ID
     * @param dto 渠道参数
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在 / 渠道非法
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "update")
    @PutMapping("/channels/{id}")
    public OperationResponse<ScrmMarketingCampaignChannelEntity> updateChannel(
            @PathVariable Long id,
            @RequestBody ScrmMarketingCampaignChannelDto dto) throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.updateChannel(id, dto));
    }

    /**
     * 删除渠道。
     *
     * @param id 渠道 ID
     * @return 空响应
     * @throws ScrmException 渠道不存在
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "delete")
    @DeleteMapping("/channels/{id}")
    public OperationResponse<Void> removeChannel(@PathVariable Long id) throws ScrmException {
        scrmMarketingCampaignService.removeChannel(id);
        return OperationResponse.build();
    }

    /**
     * 启动渠道发送 (模拟实现)。
     *
     * @param launchDto 启动配置 (campaignId + channelConfigs)
     * @return 各渠道发送结果 [{channelId, channel, status, sentCount}]
     * @throws ScrmException 活动不存在 / 渠道不存在
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/launch")
    public OperationResponse<List<Map<String, Object>>> launchChannels(
            @Valid @RequestBody ScrmCampaignLaunchDto launchDto) throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.launchChannels(launchDto));
    }

    // ============================================================
    // 参与者管理
    // ============================================================

    /**
     * 记录参与者。
     *
     * @param dto 参与者参数
     * @return 创建后的参与者记录
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/participants")
    public OperationResponse<ScrmMarketingCampaignParticipantEntity> recordParticipant(
            @Valid @RequestBody ScrmMarketingCampaignParticipantDto dto) throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.recordParticipant(dto));
    }

    /**
     * 分页查询参与者列表。
     *
     * @param campaignId 活动 ID 过滤（可空）
     * @param channel    渠道过滤（可空）: WECHAT/WORK_WECHAT/SMS/EMAIL/DOUYIN/KUAISHOU/XIAOHONGSHU/BILIBILI
     * @param converted  转化状态过滤（可空）
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 参与者分页结果 (按 participatedAt DESC)
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "read")
    @GetMapping("/participants/list")
    public OperationResponse<Page<ScrmMarketingCampaignParticipantEntity>> listParticipants(
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean converted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "participatedAt"));
        return OperationResponse.build(scrmMarketingCampaignService.listParticipants(
                campaignId, channel, converted, pageable));
    }

    /**
     * 更新参与者转化状态。
     *
     * @param id              参与者 ID
     * @param body            请求体, 可包含 converted (布尔) / conversionValue (数值) 字段
     * @return 更新后的参与者记录
     * @throws ScrmException 参与者不存在
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "update")
    @PostMapping("/participants/{id}/conversion")
    public OperationResponse<ScrmMarketingCampaignParticipantEntity> updateParticipantConversion(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, Object> body) throws ScrmException {
        Boolean converted = null;
        Double conversionValue = null;
        if (body != null) {
            if (body.get("converted") != null) {
                converted = Boolean.parseBoolean(body.get("converted").toString());
            }
            if (body.get("conversionValue") != null) {
                conversionValue = Double.valueOf(body.get("conversionValue").toString());
            }
        }
        return OperationResponse.build(scrmMarketingCampaignService.updateParticipantConversion(
                id, converted, conversionValue));
    }

    // ============================================================
    // 指标与统计
    // ============================================================

    /**
     * 活动指标汇总: 各渠道指标 + 总花费 + 总转化金额 + ROI。
     *
     * @param campaignId 活动 ID
     * @return 指标汇总 Map
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "read")
    @GetMapping("/metrics/{campaignId}")
    public OperationResponse<Map<String, Object>> getCampaignMetrics(@PathVariable Long campaignId)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.getCampaignMetrics(campaignId));
    }

    /**
     * 活动统计概览: 活动数 / 进行中 / 已完成 / 平均 ROI / 总转化。
     *
     * @param startTime 创建时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   创建时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getCampaignStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmMarketingCampaignService.getCampaignStats(startTime, endTime));
    }

    /**
     * 渠道效果对比。
     *
     * @param startTime 发送时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   发送时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 渠道效果列表
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "read")
    @GetMapping("/stats/channels")
    public OperationResponse<List<Map<String, Object>>> getChannelEffectiveness(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmMarketingCampaignService.getChannelEffectiveness(startTime, endTime));
    }

    /**
     * 活动时间线。
     *
     * @param campaignId 活动 ID
     * @return 时间线 Map {campaign, channels, participants, timeline}
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_marketing_campaign", action = "read")
    @GetMapping("/stats/timeline/{campaignId}")
    public OperationResponse<Map<String, Object>> getCampaignTimeline(@PathVariable Long campaignId)
            throws ScrmException {
        return OperationResponse.build(scrmMarketingCampaignService.getCampaignTimeline(campaignId));
    }
}
