/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCommunityBroadcastDto;
import org.hiylo.scrm.dto.ScrmCommunityDto;
import org.hiylo.scrm.dto.ScrmCommunityMemberDto;
import org.hiylo.scrm.dto.ScrmCommunityMessageDto;
import org.hiylo.scrm.dto.ScrmCommunitySopDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCommunityService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
 * SCRM 社群运营管理控制器
 * <p>
 * 提供社群管理、群成员管理、群 SOP、群消息与群广播、社群统计等接口。
 * 权限由 gateway-server 统一鉴权, 此处通过 {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/communities")
@RequiredArgsConstructor
public class ScrmCommunityController {

    /** 社群服务 */
    private final ScrmCommunityService communityService;

    // ============================================================
    // 社群管理
    // ============================================================

    /**
     * 创建社群
     *
     * @param dto 社群参数
     * @return 创建后的社群
     */
    @RequirePermission(resource = "scrm_community", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmCommunityDto> create(@Valid @RequestBody ScrmCommunityDto dto)
            throws ScrmException {
        return OperationResponse.build(communityService.createCommunity(dto));
    }

    /**
     * 更新社群
     *
     * @param id  社群 ID
     * @param dto 社群参数
     * @return 更新后的社群
     */
    @RequirePermission(resource = "scrm_community", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmCommunityDto> update(@PathVariable Long id,
                                                       @RequestBody ScrmCommunityDto dto)
            throws ScrmException {
        return OperationResponse.build(communityService.updateCommunity(id, dto));
    }

    /**
     * 删除社群 (级联清理成员与消息记录)
     *
     * @param id 社群 ID
     * @return 空响应
     * @throws ScrmException 社群不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_community", action = "delete")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        communityService.deleteCommunity(id);
        return OperationResponse.build();
    }

    /**
     * 查询社群详情
     *
     * @param id 社群 ID
     * @return 社群详情
     * @throws ScrmException 社群不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_community", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCommunityDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(communityService.getCommunity(id));
    }

    /**
     * 分页查询社群列表, 支持按平台类型 / 社群类型 / 状态 / 关键词过滤
     *
     * @param platformType  平台类型过滤 (可选)
     * @param communityType 社群类型过滤 (可选)
     * @param status        状态过滤 (可选)
     * @param keyword       关键词过滤, 匹配社群名称 / 群主名称 (可选)
     * @param page          页码 (从 0 开始, 默认 0)
     * @param size          每页大小 (默认 20)
     * @return 社群分页结果
     */
    @RequirePermission(resource = "scrm_community", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmCommunityDto>> list(
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String communityType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(communityService.listCommunities(
                platformType, communityType, status, keyword, pageable));
    }

    /**
     * 启用社群 (状态置为 ACTIVE)
     *
     * @param id 社群 ID
     * @return 更新后的社群
     * @throws ScrmException 社群不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_community", action = "update")
    @PostMapping("/{id}/activate")
    public OperationResponse<ScrmCommunityDto> activate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(communityService.activateCommunity(id));
    }

    /**
     * 停用社群 (状态置为 INACTIVE)
     *
     * @param id 社群 ID
     * @return 更新后的社群
     * @throws ScrmException 社群不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_community", action = "update")
    @PostMapping("/{id}/deactivate")
    public OperationResponse<ScrmCommunityDto> deactivate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(communityService.deactivateCommunity(id));
    }

    /**
     * 更新社群活跃度评分 (基于消息数 / 活跃成员 / 新增成员)
     *
     * @param id 社群 ID
     * @return 更新后的社群
     */
    @RequirePermission(resource = "scrm_community", action = "update")
    @PostMapping("/{id}/activity-score")
    public OperationResponse<ScrmCommunityDto> updateActivityScore(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(communityService.updateActivityScore(id));
    }

    // ============================================================
    // 群成员管理
    // ============================================================

    /**
     * 添加群成员
     *
     * @param communityId 社群 ID
     * @param dto         成员参数
     * @return 创建后的成员
     */
    @RequirePermission(resource = "scrm_community_member", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{communityId}/members")
    public OperationResponse<ScrmCommunityMemberDto> addMember(@PathVariable Long communityId,
                                                                @Valid @RequestBody ScrmCommunityMemberDto dto)
            throws ScrmException {
        dto.setCommunityId(communityId);
        return OperationResponse.build(communityService.addMember(dto));
    }

    /**
     * 分页查询社群成员, 支持按角色 / 活跃状态 / 关键词过滤
     *
     * @param communityId 社群 ID
     * @param role        群角色过滤 (可选)
     * @param isActive    活跃状态过滤 (可选)
     * @param keyword     关键词过滤, 匹配成员名称 / 群昵称 / 平台 UID (可选)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 成员分页结果
     */
    @RequirePermission(resource = "scrm_community_member", action = "read")
    @GetMapping("/{communityId}/members")
    public OperationResponse<Page<ScrmCommunityMemberDto>> listMembers(
            @PathVariable Long communityId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(communityService.listMembers(
                communityId, role, isActive, keyword, pageable));
    }

    /**
     * 批量添加群成员
     *
     * @param communityId 社群 ID
     * @param members     成员参数列表
     * @return 批量操作结果 (成功数 + 失败的社群 ID)
     */
    @RequirePermission(resource = "scrm_community_member", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/{communityId}/members/batch")
    public OperationResponse<ScrmCommunityService.BatchResult> batchAddMembers(
            @PathVariable Long communityId,
            @RequestBody List<ScrmCommunityMemberDto> members) {
        return OperationResponse.build(communityService.batchAddMembers(communityId, members));
    }

    /**
     * 查询社群不活跃成员 (最后活跃时间早于指定天数前, 或从未活跃)
     *
     * @param communityId 社群 ID
     * @param days        不活跃天数阈值 (默认 7)
     * @return 不活跃成员列表
     * @throws ScrmException 社群不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_community_member", action = "read")
    @PostMapping("/{communityId}/members/inactive")
    public OperationResponse<List<ScrmCommunityMemberDto>> getInactiveMembers(
            @PathVariable Long communityId,
            @RequestParam(defaultValue = "7") int days) throws ScrmException {
        return OperationResponse.build(communityService.getInactiveMembers(communityId, days));
    }

    /**
     * 更新成员群角色
     *
     * @param id   成员 ID
     * @param role 群角色: OWNER/ADMIN/MEMBER/GUEST
     * @return 更新后的成员
     */
    @RequirePermission(resource = "scrm_community_member", action = "update")
    @PutMapping("/members/{id}")
    public OperationResponse<ScrmCommunityMemberDto> updateMemberRole(@PathVariable Long id,
                                                                       @RequestParam String role)
            throws ScrmException {
        return OperationResponse.build(communityService.updateMemberRole(id, role));
    }

    /**
     * 移除群成员 (状态置为 REMOVED)
     *
     * @param id     成员 ID
     * @param reason 移除原因 (可选)
     * @return 更新后的成员
     */
    @RequirePermission(resource = "scrm_community_member", action = "delete")
    @DeleteMapping("/members/{id}")
    public OperationResponse<ScrmCommunityMemberDto> removeMember(@PathVariable Long id,
                                                                   @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(communityService.removeMember(id, reason));
    }

    /**
     * 禁言成员 (状态置为 MUTED)
     *
     * @param id       成员 ID
     * @param duration 禁言分钟数 (默认 60)
     * @return 更新后的成员
     */
    @RequirePermission(resource = "scrm_community_member", action = "update")
    @PostMapping("/members/{id}/mute")
    public OperationResponse<ScrmCommunityMemberDto> muteMember(@PathVariable Long id,
                                                                 @RequestParam(defaultValue = "60") int duration)
            throws ScrmException {
        return OperationResponse.build(communityService.muteMember(id, duration));
    }

    // ============================================================
    // 群 SOP
    // ============================================================

    /**
     * 创建社群 SOP
     *
     * @param dto SOP 参数
     * @return 创建后的 SOP
     */
    @RequirePermission(resource = "scrm_community_sop", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/sops")
    public OperationResponse<ScrmCommunitySopDto> createSop(@Valid @RequestBody ScrmCommunitySopDto dto)
            throws ScrmException {
        return OperationResponse.build(communityService.createSop(dto));
    }

    /**
     * 更新 SOP
     *
     * @param id  SOP ID
     * @param dto SOP 参数
     * @return 更新后的 SOP
     */
    @RequirePermission(resource = "scrm_community_sop", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/sops/{id}")
    public OperationResponse<ScrmCommunitySopDto> updateSop(@PathVariable Long id,
                                                             @RequestBody ScrmCommunitySopDto dto)
            throws ScrmException {
        return OperationResponse.build(communityService.updateSop(id, dto));
    }

    /**
     * 删除 SOP
     *
     * @param id SOP ID
     * @return 空响应
     * @throws ScrmException SOP 不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_community_sop", action = "delete")
    @DeleteMapping("/sops/{id}")
    public OperationResponse<Void> deleteSop(@PathVariable Long id) throws ScrmException {
        communityService.deleteSop(id);
        return OperationResponse.build();
    }

    /**
     * 查询 SOP 详情
     *
     * @param id SOP ID
     * @return SOP 详情
     * @throws ScrmException SOP 不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_community_sop", action = "read")
    @GetMapping("/sops/{id}")
    public OperationResponse<ScrmCommunitySopDto> getSop(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(communityService.getSop(id));
    }

    /**
     * 分页查询 SOP, 支持按社群 / 触发类型 / 启用状态过滤
     *
     * @param communityId 社群 ID 过滤 (可选)
     * @param triggerType 触发类型过滤 (可选)
     * @param enabled     启用状态过滤 (可选)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return SOP 分页结果
     */
    @RequirePermission(resource = "scrm_community_sop", action = "read")
    @GetMapping("/sops/list")
    public OperationResponse<Page<ScrmCommunitySopDto>> listSops(
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(communityService.listSops(communityId, triggerType, enabled, pageable));
    }

    /**
     * 启用 SOP
     *
     * @param id SOP ID
     * @return 更新后的 SOP
     * @throws ScrmException SOP 不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_community_sop", action = "update")
    @PostMapping("/sops/{id}/enable")
    public OperationResponse<ScrmCommunitySopDto> enableSop(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(communityService.enableSop(id));
    }

    /**
     * 禁用 SOP
     *
     * @param id SOP ID
     * @return 更新后的 SOP
     * @throws ScrmException SOP 不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_community_sop", action = "update")
    @PostMapping("/sops/{id}/disable")
    public OperationResponse<ScrmCommunitySopDto> disableSop(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(communityService.disableSop(id));
    }

    /**
     * 执行 SOP 动作 (模拟实现)
     *
     * @param sopId       SOP ID
     * @param communityId 目标社群 ID (可选, 为空时取 SOP 绑定的社群)
     * @return 执行结果描述
     */
    @RequirePermission(resource = "scrm_community_sop", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/sops/{sopId}/execute")
    public OperationResponse<String> executeSop(@PathVariable Long sopId,
                                                 @RequestParam(required = false) Long communityId)
            throws ScrmException {
        return OperationResponse.build(communityService.executeSop(sopId, communityId));
    }

    /**
     * 处理定时触发 SOP (调度入口, 模拟实现)
     *
     * @return 处理的 SOP 数量
     */
    @RequirePermission(resource = "scrm_community_sop", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/sops/process")
    public OperationResponse<Integer> processTimeBasedSops() {
        return OperationResponse.build(communityService.processTimeBasedSops());
    }

    // ============================================================
    // 群消息
    // ============================================================

    /**
     * 记录社群消息
     *
     * @param dto 消息参数
     * @return 记录后的消息
     */
    @RequirePermission(resource = "scrm_community_message", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/messages")
    public OperationResponse<ScrmCommunityMessageDto> recordMessage(@Valid @RequestBody ScrmCommunityMessageDto dto)
            throws ScrmException {
        return OperationResponse.build(communityService.recordMessage(dto));
    }

    /**
     * 分页查询社群消息, 支持按发送者类型 / 消息类型 / 时间区间 / 关键词过滤
     *
     * @param communityId 社群 ID 过滤 (可选)
     * @param senderType  发送者类型过滤 (可选)
     * @param messageType 消息类型过滤 (可选)
     * @param startTime   发送时间起点 (可选, ISO 格式)
     * @param endTime     发送时间终点 (可选, ISO 格式)
     * @param keyword     关键词过滤, 匹配消息内容 / 发送者名称 (可选)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 消息分页结果
     */
    @RequirePermission(resource = "scrm_community_message", action = "read")
    @GetMapping("/messages/list")
    public OperationResponse<Page<ScrmCommunityMessageDto>> listMessages(
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) String senderType,
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(communityService.listMessages(
                communityId, senderType, messageType, startTime, endTime, keyword, pageable));
    }

    /**
     * 查询消息详情
     *
     * @param id 消息 ID
     * @return 消息详情
     * @throws ScrmException 消息不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_community_message", action = "read")
    @GetMapping("/messages/{id}")
    public OperationResponse<ScrmCommunityMessageDto> getMessage(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(communityService.getMessage(id));
    }

    /**
     * 群广播 (多群发送, 模拟实现)
     *
     * @param dto 广播参数 (communityIds + messageType + content)
     * @return 批量操作结果 (成功数 + 失败的社群 ID)
     */
    @RequirePermission(resource = "scrm_community_message", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/messages/broadcast")
    public OperationResponse<ScrmCommunityService.BatchResult> broadcast(
            @Valid @RequestBody ScrmCommunityBroadcastDto dto) {
        return OperationResponse.build(communityService.broadcast(dto));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 获取社群统计 (成员数 / 活跃度 / 消息趋势 / 活跃率)
     *
     * @param communityId 社群 ID
     * @return 社群统计
     */
    @RequirePermission(resource = "scrm_community_stats", action = "read")
    @GetMapping("/stats/{communityId}")
    public OperationResponse<Map<String, Object>> getCommunityStats(@PathVariable Long communityId)
            throws ScrmException {
        return OperationResponse.build(communityService.getCommunityStats(communityId));
    }

    /**
     * 获取总体统计 (群数 / 总成员 / 平均活跃度 / 总消息)
     *
     * @param startTime 建群时间起点 (可选, ISO 格式)
     * @param endTime   建群时间终点 (可选, ISO 格式)
     * @return 总体统计
     */
    @RequirePermission(resource = "scrm_community_stats", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getOverallStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(communityService.getOverallStats(startTime, endTime));
    }

    /**
     * 获取社群活跃度趋势 (最近 N 天的每日消息数)
     *
     * @param communityId 社群 ID
     * @param days        天数 (默认 7)
     * @return 活跃度趋势
     * @throws ScrmException 社群不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_community_stats", action = "read")
    @GetMapping("/stats/activity-trend/{communityId}")
    public OperationResponse<List<Map<String, Object>>> getActivityTrend(
            @PathVariable Long communityId,
            @RequestParam(defaultValue = "7") int days) throws ScrmException {
        return OperationResponse.build(communityService.getActivityTrend(communityId, days));
    }

    /**
     * 获取活跃群排行 (按 activityScore 倒序)
     *
     * @param limit 限制条数 (默认 10, 最大 100)
     * @return 活跃群列表
     */
    @RequirePermission(resource = "scrm_community_stats", action = "read")
    @GetMapping("/stats/top")
    public OperationResponse<List<ScrmCommunityDto>> getTopCommunities(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(communityService.getTopCommunities(limit));
    }
}
