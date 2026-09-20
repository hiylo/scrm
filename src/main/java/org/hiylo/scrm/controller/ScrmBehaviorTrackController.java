/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBehaviorTrackController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmBehaviorQueryDto;
import org.hiylo.scrm.dto.ScrmBehaviorRecordDto;
import org.hiylo.scrm.dto.ScrmTouchpointDto;
import org.hiylo.scrm.entity.ScrmBehaviorPathEntity;
import org.hiylo.scrm.entity.ScrmBehaviorTrackEntity;
import org.hiylo.scrm.entity.ScrmTouchpointEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmBehaviorTrackService;
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
 * SCRM 客户行为轨迹控制器。
 * <p>
 * 提供行为事件记录与查询、触点管理、行为路径分析与行为统计分析接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/behavior-tracks")
@RequiredArgsConstructor
public class ScrmBehaviorTrackController {

    /** 客户行为轨迹服务 */
    private final ScrmBehaviorTrackService scrmBehaviorTrackService;

    // ============================================================
    // 行为记录
    // ============================================================

    /**
     * 记录客户行为事件。
     *
     * @param dto 行为记录参数
     * @return 创建后的行为事件
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/record")
    public OperationResponse<ScrmBehaviorTrackEntity> recordBehavior(@Valid @RequestBody ScrmBehaviorRecordDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBehaviorTrackService.recordBehavior(dto));
    }

    /**
     * 批量记录客户行为事件。
     *
     * @param records 行为记录列表
     * @return 创建后的行为事件列表
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/batch-record")
    public OperationResponse<List<ScrmBehaviorTrackEntity>> batchRecord(
            @RequestBody List<ScrmBehaviorRecordDto> records) throws ScrmException {
        return OperationResponse.build(scrmBehaviorTrackService.batchRecord(records));
    }

    /**
     * 查询行为事件详情。
     *
     * @param id 行为事件 ID
     * @return 行为事件详情
     * @throws ScrmException 行为事件不存在
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmBehaviorTrackEntity> getTrack(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBehaviorTrackService.getTrack(id));
    }

    /**
     * 分页查询行为事件, 支持按客户 / 行为类型 / 触点 / 时间范围 / 漏斗阶段组合过滤。
     *
     * @param queryDto 查询参数 (customerId/behaviorType/touchpoint/startTime/endTime/funnelStage)
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 行为事件分页结果 (按 behaviorTime DESC)
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmBehaviorTrackEntity>> listTracks(
            ScrmBehaviorQueryDto queryDto,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "behaviorTime"));
        return OperationResponse.build(scrmBehaviorTrackService.listTracks(queryDto, pageable));
    }

    /**
     * 客户行为时间线。
     *
     * @param customerId 客户 ID
     * @param startTime  行为时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    行为时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 行为事件列表 (behaviorTime ASC)
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/customer/{customerId}/timeline")
    public OperationResponse<List<ScrmBehaviorTrackEntity>> getCustomerTimeline(
            @PathVariable Long customerId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBehaviorTrackService.getCustomerTimeline(customerId, startTime, endTime));
    }

    /**
     * 按行为类型分页查询行为事件。
     *
     * @param behaviorType 行为类型
     * @param startTime    行为时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime      行为时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 行为事件分页结果 (按 behaviorTime DESC)
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/by-type")
    public OperationResponse<Page<ScrmBehaviorTrackEntity>> getTracksByType(
            @RequestParam String behaviorType,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "behaviorTime"));
        return OperationResponse.build(scrmBehaviorTrackService.getTracksByType(
                behaviorType, startTime, endTime, pageable));
    }

    /**
     * 按触点分页查询行为事件。
     *
     * @param touchpoint 触点
     * @param startTime  行为时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    行为时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 行为事件分页结果 (按 behaviorTime DESC)
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/by-touchpoint")
    public OperationResponse<Page<ScrmBehaviorTrackEntity>> getTracksByTouchpoint(
            @RequestParam String touchpoint,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "behaviorTime"));
        return OperationResponse.build(scrmBehaviorTrackService.getTracksByTouchpoint(
                touchpoint, startTime, endTime, pageable));
    }

    // ============================================================
    // 触点管理 /touchpoints
    // ============================================================

    /**
     * 创建触点。
     *
     * @param dto 触点参数
     * @return 创建后的触点
     * @throws ScrmException 触点编码重复 / 参数非法
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/touchpoints")
    public OperationResponse<ScrmTouchpointEntity> createTouchpoint(@Valid @RequestBody ScrmTouchpointDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBehaviorTrackService.createTouchpoint(dto));
    }

    /**
     * 更新触点。
     *
     * @param id  触点 ID
     * @param dto 触点参数
     * @return 更新后的触点
     * @throws ScrmException 触点不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/touchpoints/{id}")
    public OperationResponse<ScrmTouchpointEntity> updateTouchpoint(@PathVariable Long id,
                                                                     @RequestBody ScrmTouchpointDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmBehaviorTrackService.updateTouchpoint(id, dto));
    }

    /**
     * 删除触点。
     *
     * @param id 触点 ID
     * @return 空响应
     * @throws ScrmException 触点不存在
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "delete")
    @DeleteMapping("/touchpoints/{id}")
    public OperationResponse<Void> deleteTouchpoint(@PathVariable Long id) throws ScrmException {
        scrmBehaviorTrackService.deleteTouchpoint(id);
        return OperationResponse.build();
    }

    /**
     * 查询触点详情。
     *
     * @param id 触点 ID
     * @return 触点详情
     * @throws ScrmException 触点不存在
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/touchpoints/{id}")
    public OperationResponse<ScrmTouchpointEntity> getTouchpoint(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBehaviorTrackService.getTouchpoint(id));
    }

    /**
     * 按触点编码查询触点。
     *
     * @param code 触点编码
     * @return 触点详情
     * @throws ScrmException 触点不存在
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/touchpoints/code/{code}")
    public OperationResponse<ScrmTouchpointEntity> getTouchpointByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmBehaviorTrackService.getTouchpointByCode(code));
    }

    /**
     * 分页查询触点列表。
     *
     * @param touchpointType 触点类型过滤（可空）: WEBSITE/APP/WECHAT_OFFICIAL/...
     * @param isActive       启用状态过滤（可空）
     * @param page           页码（从 0 开始, 默认 0）
     * @param size           每页大小（默认 20）
     * @return 触点分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/touchpoints/list")
    public OperationResponse<Page<ScrmTouchpointEntity>> listTouchpoints(
            @RequestParam(required = false) String touchpointType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmBehaviorTrackService.listTouchpoints(touchpointType, isActive, pageable));
    }

    /**
     * 启用触点。
     *
     * @param id 触点 ID
     * @return 更新后的触点
     * @throws ScrmException 触点不存在
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "update")
    @PostMapping("/touchpoints/{id}/activate")
    public OperationResponse<ScrmTouchpointEntity> activateTouchpoint(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBehaviorTrackService.activateTouchpoint(id));
    }

    /**
     * 禁用触点。
     *
     * @param id 触点 ID
     * @return 更新后的触点
     * @throws ScrmException 触点不存在
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "update")
    @PostMapping("/touchpoints/{id}/deactivate")
    public OperationResponse<ScrmTouchpointEntity> deactivateTouchpoint(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBehaviorTrackService.deactivateTouchpoint(id));
    }

    // ============================================================
    // 行为路径 /paths
    // ============================================================

    /**
     * 构建客户行为路径: 从客户 + 会话内的行为事件聚合。
     *
     * @param customerId 客户 ID
     * @param sessionId  会话 ID
     * @return 构建后的行为路径
     * @throws ScrmException 会话 ID 为空 / 无行为事件
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/paths/build/{customerId}")
    public OperationResponse<ScrmBehaviorPathEntity> buildPath(
            @PathVariable Long customerId,
            @RequestParam String sessionId) throws ScrmException {
        return OperationResponse.build(scrmBehaviorTrackService.buildPath(customerId, sessionId));
    }

    /**
     * 查询行为路径详情。
     *
     * @param id 路径 ID
     * @return 路径详情
     * @throws ScrmException 路径不存在
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/paths/{id}")
    public OperationResponse<ScrmBehaviorPathEntity> getPath(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmBehaviorTrackService.getPath(id));
    }

    /**
     * 分页查询行为路径。
     *
     * @param customerId    客户 ID 过滤（可空）
     * @param hasConversion 转化标记过滤（可空）
     * @param startTime     会话开始时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime       会话开始时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page          页码（从 0 开始, 默认 0）
     * @param size          每页大小（默认 20）
     * @return 路径分页结果 (按 sessionStartTime DESC)
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/paths/list")
    public OperationResponse<Page<ScrmBehaviorPathEntity>> listPaths(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Boolean hasConversion,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "sessionStartTime"));
        return OperationResponse.build(scrmBehaviorTrackService.listPaths(
                customerId, hasConversion, startTime, endTime, pageable));
    }

    /**
     * 常见路径分析。
     *
     * @param limit 返回条数（默认 10）
     * @return 常见路径列表 [{touchpoints, count}]
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/paths/common")
    public OperationResponse<List<Map<String, Object>>> getCommonPaths(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmBehaviorTrackService.getCommonPaths(limit));
    }

    /**
     * 转化路径分析。
     *
     * @param limit 返回条数（默认 20）
     * @return 转化路径列表
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/paths/conversions")
    public OperationResponse<List<ScrmBehaviorPathEntity>> getConversionPaths(
            @RequestParam(defaultValue = "20") int limit) {
        return OperationResponse.build(scrmBehaviorTrackService.getConversionPaths(limit));
    }

    /**
     * 流失点分析。
     *
     * @return 流失点列表 [{touchpoint, exitCount, dropoffRate}]
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/paths/dropoff")
    public OperationResponse<List<Map<String, Object>>> getDropoffPoints() {
        return OperationResponse.build(scrmBehaviorTrackService.getDropoffPoints());
    }

    // ============================================================
    // 行为统计 /stats
    // ============================================================

    /**
     * 行为统计概览: 总事件数 / 独立客户数 / 行为类型分布 / 触点分布。
     *
     * @param startTime 行为时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   行为时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getBehaviorStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBehaviorTrackService.getBehaviorStats(startTime, endTime));
    }

    /**
     * 漏斗统计: 各漏斗阶段独立客户数与阶段间转化率。
     *
     * @param startTime 行为时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   行为时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 漏斗统计列表 [{stage, customerCount, conversionRate}]
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/stats/funnel")
    public OperationResponse<List<Map<String, Object>>> getFunnelStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBehaviorTrackService.getFunnelStats(startTime, endTime));
    }

    /**
     * 触点效果对比: 每个触点的事件数 / 独立访客 / 转化数 / 转化率。
     *
     * @param startTime 行为时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   行为时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 触点效果列表
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/stats/touchpoint-effectiveness")
    public OperationResponse<List<Map<String, Object>>> getTouchpointEffectiveness(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBehaviorTrackService.getTouchpointEffectiveness(startTime, endTime));
    }

    /**
     * 行为趋势: 按日期统计事件数 (可按行为类型过滤)。
     *
     * @param days         回溯天数（默认 7）
     * @param behaviorType 行为类型过滤（可空）
     * @return 趋势列表 [{date, count}]
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getBehaviorTrend(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String behaviorType) {
        return OperationResponse.build(scrmBehaviorTrackService.getBehaviorTrend(days, behaviorType));
    }

    /**
     * 行为热力图数据: 按页面 URL + 时段聚合事件数。
     *
     * @param touchpoint 触点过滤（可空）
     * @param startTime  行为时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    行为时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 热力图数据列表 [{pageUrl, hour, count}]
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/stats/heatmap")
    public OperationResponse<List<Map<String, Object>>> getHeatmap(
            @RequestParam(required = false) String touchpoint,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBehaviorTrackService.getHeatmap(touchpoint, startTime, endTime));
    }

    /**
     * 转化归因分析: 各触点的转化贡献。
     *
     * @param startTime 行为时间起点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   行为时间终点 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 归因分析列表 [{touchpoint, conversionCount, contribution}]
     */
    @RequirePermission(resource = "scrm_behavior_track", action = "read")
    @GetMapping("/stats/attribution")
    public OperationResponse<List<Map<String, Object>>> getConversionAttribution(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmBehaviorTrackService.getConversionAttribution(startTime, endTime));
    }
}
