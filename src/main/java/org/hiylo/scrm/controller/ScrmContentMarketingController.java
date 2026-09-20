/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentMarketingController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmContentAssetDto;
import org.hiylo.scrm.dto.ScrmContentDto;
import org.hiylo.scrm.dto.ScrmContentPublishDto;
import org.hiylo.scrm.dto.ScrmContentReviewDto;
import org.hiylo.scrm.dto.ScrmContentScheduleDto;
import org.hiylo.scrm.entity.ScrmContentAssetEntity;
import org.hiylo.scrm.entity.ScrmContentChannelEntity;
import org.hiylo.scrm.entity.ScrmContentEntity;
import org.hiylo.scrm.entity.ScrmContentScheduleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmContentMarketingService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 内容营销控制器。
 * <p>
 * 提供内容营销全流程接口: 内容创作 (CRUD / 复制 / 归档 / 互动计数), 审核流
 * (提交 / 审核), 多渠道分发 (发布 / 重新发布 / 移除 / 指标更新), 排期管理
 * (CRUD / 执行 / 取消 / 到期扫描), 素材库 (上传 / 批量导入 / 使用统计),
 * 效果追踪 (内容统计 / 渠道对比 / 单内容效果 / 热门内容 / 素材使用)。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/contents")
@RequiredArgsConstructor
public class ScrmContentMarketingController {

    /** 内容营销服务 */
    private final ScrmContentMarketingService scrmContentMarketingService;

    // ============================================================
    // 内容管理
    // ============================================================

    /**
     * 创建内容。
     *
     * @param dto 内容参数
     * @return 创建后的内容
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_content", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmContentEntity> createContent(@Valid @RequestBody ScrmContentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.createContent(dto));
    }

    /**
     * 更新内容。
     *
     * @param id  内容 ID
     * @param dto 内容参数
     * @return 更新后的内容
     * @throws ScrmException 内容不存在 / 参数非法 / 状态非法
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmContentEntity> updateContent(@PathVariable Long id,
                                                                @RequestBody ScrmContentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.updateContent(id, dto));
    }

    /**
     * 删除内容 (同时删除关联渠道与排期记录)。
     *
     * @param id 内容 ID
     * @return 空响应
     * @throws ScrmException 内容不存在
     */
    @RequirePermission(resource = "scrm_content", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteContent(@PathVariable Long id) throws ScrmException {
        scrmContentMarketingService.deleteContent(id);
        return OperationResponse.build();
    }

    /**
     * 查询内容详情。
     *
     * @param id 内容 ID
     * @return 内容详情
     * @throws ScrmException 内容不存在
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmContentEntity> getContent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.getContent(id));
    }

    /**
     * 分页查询内容列表。
     *
     * @param contentType 内容类型过滤（可空）: ARTICLE/VIDEO/IMAGE/POSTER/LIVE_SHORT/INFOGRAPHIC/PDF
     * @param category    分类过滤（可空）
     * @param status      状态过滤（可空）: DRAFT/PENDING_REVIEW/APPROVED/SCHEDULED/PUBLISHED/ARCHIVED/REJECTED
     * @param authorId    作者 ID 过滤（可空）
     * @param keyword     标题关键字模糊匹配（可空）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 内容分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmContentEntity>> listContents(
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmContentMarketingService.listContents(
                contentType, category, status, authorId, keyword, pageable));
    }

    /**
     * 提交审核。
     *
     * @param id 内容 ID
     * @return 更新后的内容
     * @throws ScrmException 内容不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @PostMapping("/{id}/submit-review")
    public OperationResponse<ScrmContentEntity> submitForReview(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.submitForReview(id));
    }

    /**
     * 审核内容。
     *
     * @param reviewDto 审核参数 (contentId + approved + comment)
     * @return 更新后的内容
     * @throws ScrmException 内容不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_content", action = "review")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/review")
    public OperationResponse<ScrmContentEntity> reviewContent(@Valid @RequestBody ScrmContentReviewDto reviewDto)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.reviewContent(reviewDto));
    }

    /**
     * 归档内容。
     *
     * @param id 内容 ID
     * @return 更新后的内容
     * @throws ScrmException 内容不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @PostMapping("/{id}/archive")
    public OperationResponse<ScrmContentEntity> archiveContent(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.archiveContent(id));
    }

    /**
     * 复制内容 (创建副本, 状态置 DRAFT, 计数与审核字段清零)。
     *
     * @param id 源内容 ID
     * @return 复制后的内容
     * @throws ScrmException 源内容不存在
     */
    @RequirePermission(resource = "scrm_content", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/{id}/copy")
    public OperationResponse<ScrmContentEntity> copyContent(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.copyContent(id));
    }

    /**
     * 增加内容互动计数。
     *
     * @param id     内容 ID
     * @param metric 指标类型: VIEW/LIKE/SHARE/COMMENT/COLLECT/CONVERSION
     * @return 更新后的内容
     * @throws ScrmException 内容不存在 / 指标非法
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @RateLimit(capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @PostMapping("/{id}/metric")
    public OperationResponse<ScrmContentEntity> incrementMetric(@PathVariable Long id,
                                                                 @RequestParam String metric)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.incrementMetric(id, metric));
    }

    // ============================================================
    // 渠道分发
    // ============================================================

    /**
     * 多渠道发布 (模拟实现, scheduledAt 非空时创建排期延后发布)。
     *
     * @param publishDto 发布配置 (contentId + channels + scheduledAt)
     * @return 各渠道发布结果
     * @throws ScrmException 内容不存在 / 渠道非法 / 状态非法
     */
    @RequirePermission(resource = "scrm_content", action = "publish")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/channels/publish")
    public OperationResponse<List<Map<String, Object>>> publishToChannels(
            @Valid @RequestBody ScrmContentPublishDto publishDto) throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.publishToChannels(publishDto));
    }

    /**
     * 重新发布渠道。
     *
     * @param id 渠道 ID
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_content", action = "publish")
    @PostMapping("/channels/{id}/republish")
    public OperationResponse<ScrmContentChannelEntity> republishChannel(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.republishChannel(id));
    }

    /**
     * 移除渠道 (下架)。
     *
     * @param id 渠道 ID
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @DeleteMapping("/channels/{id}")
    public OperationResponse<ScrmContentChannelEntity> removeChannel(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.removeChannel(id));
    }

    /**
     * 查询内容下全部渠道。
     *
     * @param contentId 内容 ID
     * @return 渠道列表 (按渠道名升序)
     * @throws ScrmException 内容不存在
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/channels/content/{contentId}")
    public OperationResponse<List<ScrmContentChannelEntity>> listChannels(@PathVariable Long contentId)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.listChannels(contentId));
    }

    /**
     * 更新渠道互动指标。
     *
     * @param id          渠道 ID
     * @param body        指标增量 (views / likes / shares / comments / conversions)
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @PostMapping("/channels/{id}/metrics")
    public OperationResponse<ScrmContentChannelEntity> updateChannelMetrics(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, Object> body) throws ScrmException {
        Integer views = toInt(body, "views");
        Integer likes = toInt(body, "likes");
        Integer shares = toInt(body, "shares");
        Integer comments = toInt(body, "comments");
        Integer conversions = toInt(body, "conversions");
        return OperationResponse.build(scrmContentMarketingService.updateChannelMetrics(
                id, views, likes, shares, comments, conversions));
    }

    // ============================================================
    // 排期管理
    // ============================================================

    /**
     * 创建排期任务。
     *
     * @param dto 排期参数
     * @return 创建后的排期
     * @throws ScrmException 参数非法 / 内容不存在
     */
    @RequirePermission(resource = "scrm_content", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/schedules")
    public OperationResponse<ScrmContentScheduleEntity> createSchedule(@Valid @RequestBody ScrmContentScheduleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.createSchedule(dto));
    }

    /**
     * 更新排期任务。
     *
     * @param id  排期 ID
     * @param dto 排期参数
     * @return 更新后的排期
     * @throws ScrmException 排期不存在 / 参数非法 / 状态非法
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @PutMapping("/schedules/{id}")
    public OperationResponse<ScrmContentScheduleEntity> updateSchedule(@PathVariable Long id,
                                                                        @RequestBody ScrmContentScheduleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.updateSchedule(id, dto));
    }

    /**
     * 删除排期任务。
     *
     * @param id 排期 ID
     * @return 空响应
     * @throws ScrmException 排期不存在
     */
    @RequirePermission(resource = "scrm_content", action = "delete")
    @DeleteMapping("/schedules/{id}")
    public OperationResponse<Void> deleteSchedule(@PathVariable Long id) throws ScrmException {
        scrmContentMarketingService.deleteSchedule(id);
        return OperationResponse.build();
    }

    /**
     * 查询排期详情。
     *
     * @param id 排期 ID
     * @return 排期详情
     * @throws ScrmException 排期不存在
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/schedules/{id}")
    public OperationResponse<ScrmContentScheduleEntity> getSchedule(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.getSchedule(id));
    }

    /**
     * 分页查询排期列表。
     *
     * @param contentId 内容 ID 过滤（可空）
     * @param status    状态过滤（可空）: PENDING/EXECUTING/COMPLETED/FAILED/CANCELLED
     * @param startTime 计划时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   计划时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 排期分页结果 (按 scheduledAt ASC)
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/schedules/list")
    public OperationResponse<Page<ScrmContentScheduleEntity>> listSchedules(
            @RequestParam(required = false) Long contentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "scheduledAt"));
        return OperationResponse.build(scrmContentMarketingService.listSchedules(
                contentId, status, startTime, endTime, pageable));
    }

    /**
     * 执行排期 (立即触发多渠道发布模拟)。
     *
     * @param id 排期 ID
     * @return 更新后的排期
     * @throws ScrmException 排期不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/schedules/{id}/execute")
    public OperationResponse<ScrmContentScheduleEntity> executeSchedule(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.executeSchedule(id));
    }

    /**
     * 取消排期。
     *
     * @param id 排期 ID
     * @return 更新后的排期
     * @throws ScrmException 排期不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @PostMapping("/schedules/{id}/cancel")
    public OperationResponse<ScrmContentScheduleEntity> cancelSchedule(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.cancelSchedule(id));
    }

    /**
     * 处理到期排期 (调度器轮询入口, 扫描并执行 PENDING 且到期的排期)。
     *
     * @return 处理的排期数量
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/schedules/process-due")
    public OperationResponse<Integer> processDueSchedules() {
        return OperationResponse.build(scrmContentMarketingService.processDueSchedules());
    }

    // ============================================================
    // 素材库
    // ============================================================

    /**
     * 上传素材。
     *
     * @param dto 素材参数
     * @return 创建后的素材
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_content", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/assets")
    public OperationResponse<ScrmContentAssetEntity> uploadAsset(@Valid @RequestBody ScrmContentAssetDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.uploadAsset(dto));
    }

    /**
     * 更新素材。
     *
     * @param id  素材 ID
     * @param dto 素材参数
     * @return 更新后的素材
     * @throws ScrmException 素材不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @PutMapping("/assets/{id}")
    public OperationResponse<ScrmContentAssetEntity> updateAsset(@PathVariable Long id,
                                                                  @RequestBody ScrmContentAssetDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.updateAsset(id, dto));
    }

    /**
     * 删除素材。
     *
     * @param id 素材 ID
     * @return 空响应
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_content", action = "delete")
    @DeleteMapping("/assets/{id}")
    public OperationResponse<Void> deleteAsset(@PathVariable Long id) throws ScrmException {
        scrmContentMarketingService.deleteAsset(id);
        return OperationResponse.build();
    }

    /**
     * 查询素材详情。
     *
     * @param id 素材 ID
     * @return 素材详情
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/assets/{id}")
    public OperationResponse<ScrmContentAssetEntity> getAsset(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.getAsset(id));
    }

    /**
     * 分页查询素材列表。
     *
     * @param assetType 素材类型过滤（可空）: IMAGE/VIDEO/AUDIO/DOCUMENT/TEMPLATE
     * @param category  分类过滤（可空）
     * @param keyword   素材名称关键字模糊匹配（可空）
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 素材分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/assets/list")
    public OperationResponse<Page<ScrmContentAssetEntity>> listAssets(
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmContentMarketingService.listAssets(
                assetType, category, keyword, pageable));
    }

    /**
     * 批量导入素材。
     *
     * @param assets 素材参数列表
     * @return 导入成功的素材列表
     */
    @RequirePermission(resource = "scrm_content", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/assets/batch-import")
    public OperationResponse<List<ScrmContentAssetEntity>> batchImportAssets(
            @RequestBody List<ScrmContentAssetDto> assets) {
        return OperationResponse.build(scrmContentMarketingService.batchImportAssets(assets));
    }

    /**
     * 素材使用次数 +1。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @RequirePermission(resource = "scrm_content", action = "update")
    @PostMapping("/assets/{id}/usage")
    public OperationResponse<ScrmContentAssetEntity> incrementUsage(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.incrementUsage(id));
    }

    // ============================================================
    // 统计与效果追踪
    // ============================================================

    /**
     * 内容统计概览: 各状态内容数 / 已发布数 / 总互动量 / 总转化数 / 转化率。
     *
     * @param startTime 发布时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   发布时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getContentStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmContentMarketingService.getContentStats(startTime, endTime));
    }

    /**
     * 渠道效果对比。
     *
     * @param startTime 发布时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   发布时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 渠道效果列表
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/stats/channels")
    public OperationResponse<List<Map<String, Object>>> getChannelStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmContentMarketingService.getChannelStats(startTime, endTime));
    }

    /**
     * 单内容效果详情。
     *
     * @param contentId 内容 ID
     * @return 效果详情 Map
     * @throws ScrmException 内容不存在
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/stats/content/{contentId}")
    public OperationResponse<Map<String, Object>> getContentPerformance(@PathVariable Long contentId)
            throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.getContentPerformance(contentId));
    }

    /**
     * 热门内容 Top N。
     *
     * @param limit  返回条数 (默认 10)
     * @param metric 排序指标: VIEW/LIKE/SHARE/COMMENT/COLLECT/CONVERSION (默认 VIEW)
     * @return 热门内容列表
     * @throws ScrmException 指标非法
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/stats/top")
    public OperationResponse<List<ScrmContentEntity>> getTopContents(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(defaultValue = "VIEW") String metric) throws ScrmException {
        return OperationResponse.build(scrmContentMarketingService.getTopContents(limit, metric));
    }

    /**
     * 素材使用统计: 按素材类型汇总素材数量与总使用次数。
     *
     * @return 素材统计列表
     */
    @RequirePermission(resource = "scrm_content", action = "read")
    @GetMapping("/stats/assets")
    public OperationResponse<List<Map<String, Object>>> getAssetUsageStats() {
        return OperationResponse.build(scrmContentMarketingService.getAssetUsageStats());
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 从请求体 Map 中读取整型参数 (缺失或不可转换时返回 null)。
     *
     * @param body 请求体 Map
     * @param key  参数键
     * @return 整型值 (缺失返回 null)
     */
    private Integer toInt(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        try {
            return Integer.valueOf(body.get(key).toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
