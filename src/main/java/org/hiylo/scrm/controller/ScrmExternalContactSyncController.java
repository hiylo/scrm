/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExternalContactSyncController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmExternalContactMappingDto;
import org.hiylo.scrm.dto.ScrmExternalContactSyncConfigDto;
import org.hiylo.scrm.dto.ScrmExternalContactSyncLogDto;
import org.hiylo.scrm.dto.ScrmExternalContactSyncTaskDto;
import org.hiylo.scrm.dto.ScrmSyncTriggerDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmExternalContactSyncService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 * SCRM 外部联系人同步控制器。
 * <p>
 * 提供企业微信 / 抖音 / 快手 / 小红书等平台外部联系人同步的配置管理、任务管理、
 * 日志查询、联系人映射管理与同步统计接口。同步执行为模拟实现, 待对接企微 API。
 * 权限由 gateway-server 统一鉴权。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/external-contact-sync")
@RequiredArgsConstructor
public class ScrmExternalContactSyncController {

    /** 外部联系人同步服务 */
    private final ScrmExternalContactSyncService syncService;

    // ============================================================
    // 配置管理 /configs
    // ============================================================

    /**
     * 创建同步配置。
     *
     * @param dto 配置参数
     * @return 创建后的配置
     * @throws ScrmException 参数非法 / 配置编码重复
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60, message = "创建配置过于频繁，请稍后重试")
    @PostMapping("/configs")
    public OperationResponse<ScrmExternalContactSyncConfigDto> createConfig(
            @Valid @RequestBody ScrmExternalContactSyncConfigDto dto) throws ScrmException {
        return OperationResponse.build(syncService.createConfig(dto));
    }

    /**
     * 更新同步配置 (字段非空才覆盖)。
     *
     * @param id  配置 ID
     * @param dto 配置参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "update")
    @PutMapping("/configs/{id}")
    public OperationResponse<ScrmExternalContactSyncConfigDto> updateConfig(
            @PathVariable Long id, @RequestBody ScrmExternalContactSyncConfigDto dto)
            throws ScrmException {
        return OperationResponse.build(syncService.updateConfig(id, dto));
    }

    /**
     * 删除同步配置。
     *
     * @param id 配置 ID
     * @return 空响应
     * @throws ScrmException 配置不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "delete")
    @DeleteMapping("/configs/{id}")
    public OperationResponse<Void> deleteConfig(@PathVariable Long id) throws ScrmException {
        syncService.deleteConfig(id);
        return OperationResponse.build();
    }

    /**
     * 查询同步配置详情。
     *
     * @param id 配置 ID
     * @return 配置详情
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/configs/{id}")
    public OperationResponse<ScrmExternalContactSyncConfigDto> getConfig(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(syncService.getConfig(id));
    }

    /**
     * 分页查询同步配置, 支持按平台、启用状态与关键字过滤。
     *
     * @param platform 平台过滤 (可选)
     * @param enabled  启用状态过滤 (可选)
     * @param keyword  关键字模糊匹配配置名称 (可选)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 配置分页结果
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/configs/list")
    public OperationResponse<Page<ScrmExternalContactSyncConfigDto>> listConfigs(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(syncService.listConfigs(platform, enabled, keyword, pageable));
    }

    /**
     * 启用同步配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "update")
    @PostMapping("/configs/{id}/enable")
    public OperationResponse<ScrmExternalContactSyncConfigDto> enableConfig(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(syncService.enableConfig(id));
    }

    /**
     * 禁用同步配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "update")
    @PostMapping("/configs/{id}/disable")
    public OperationResponse<ScrmExternalContactSyncConfigDto> disableConfig(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(syncService.disableConfig(id));
    }

    /**
     * 测试同步配置连接 (模拟)。
     *
     * @param id 配置 ID
     * @return 连接测试结果
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @PostMapping("/configs/{id}/test")
    public OperationResponse<Map<String, Object>> testConnection(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(syncService.testConnection(id));
    }

    // ============================================================
    // 任务管理 /tasks
    // ============================================================

    /**
     * 创建同步任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 配置不存在
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "create")
    @PostMapping("/tasks")
    public OperationResponse<ScrmExternalContactSyncTaskDto> createTask(
            @Valid @RequestBody ScrmExternalContactSyncTaskDto dto) throws ScrmException {
        return OperationResponse.build(syncService.createTask(dto));
    }

    /**
     * 查询同步任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/tasks/{id}")
    public OperationResponse<ScrmExternalContactSyncTaskDto> getTask(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(syncService.getTask(id));
    }

    /**
     * 分页查询同步任务, 支持按配置 ID、状态与时间区间过滤。
     *
     * @param configId  配置 ID 过滤 (可选)
     * @param status    任务状态过滤 (可选)
     * @param startTime 起始时间过滤 (可选, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间过滤 (可选, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/tasks/list")
    public OperationResponse<Page<ScrmExternalContactSyncTaskDto>> listTasks(
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(syncService.listTasks(configId, status, startTime, endTime, pageable));
    }

    /**
     * 取消同步任务 (仅 PENDING / RUNNING 状态可取消)。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "execute")
    @PostMapping("/tasks/{id}/cancel")
    public OperationResponse<ScrmExternalContactSyncTaskDto> cancelTask(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(syncService.cancelTask(id));
    }

    /**
     * 重试失败任务 (创建新任务并执行)。
     *
     * @param id 原任务 ID
     * @return 重试生成的新任务
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60, message = "重试任务过于频繁，请稍后重试")
    @PostMapping("/tasks/{id}/retry")
    public OperationResponse<ScrmExternalContactSyncTaskDto> retryTask(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(syncService.retryTask(id));
    }

    /**
     * 触发同步: 创建任务 → 执行 → 记录日志 → 更新映射。
     *
     * @param triggerDto 触发参数
     * @return 执行后的任务
     * @throws ScrmException 参数非法 / 配置不存在 / 同步失败
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60, message = "触发同步过于频繁，请稍后重试")
    @PostMapping("/tasks/trigger")
    public OperationResponse<ScrmExternalContactSyncTaskDto> triggerSync(
            @Valid @RequestBody ScrmSyncTriggerDto triggerDto) throws ScrmException {
        return OperationResponse.build(syncService.triggerSync(triggerDto));
    }

    // ============================================================
    // 日志管理 /logs
    // ============================================================

    /**
     * 分页查询同步日志, 支持按任务 ID、操作类型与处理状态过滤。
     *
     * @param taskId        任务 ID 过滤 (可选)
     * @param operationType 操作类型过滤 (可选)
     * @param status        处理状态过滤 (可选)
     * @param page          页码 (从 0 开始, 默认 0)
     * @param size          每页大小 (默认 20)
     * @return 日志分页结果
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/logs/list")
    public OperationResponse<Page<ScrmExternalContactSyncLogDto>> listLogs(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "processedAt"));
        return OperationResponse.build(syncService.listLogs(taskId, operationType, status, pageable));
    }

    /**
     * 查询同步日志详情。
     *
     * @param id 日志 ID
     * @return 日志详情
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/logs/{id}")
    public OperationResponse<ScrmExternalContactSyncLogDto> getLog(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(syncService.getLog(id));
    }

    /**
     * 查询指定任务的全部同步日志。
     *
     * @param taskId 任务 ID
     * @return 日志列表
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/logs/task/{taskId}")
    public OperationResponse<List<ScrmExternalContactSyncLogDto>> getTaskLogs(@PathVariable Long taskId)
            throws ScrmException {
        return OperationResponse.build(syncService.getTaskLogs(taskId));
    }

    // ============================================================
    // 映射管理 /mappings
    // ============================================================

    /**
     * 查询联系人映射详情。
     *
     * @param id 映射 ID
     * @return 映射详情
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/mappings/{id}")
    public OperationResponse<ScrmExternalContactMappingDto> getMapping(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(syncService.getMapping(id));
    }

    /**
     * 按平台与外部联系人 ID 查询映射。
     *
     * @param platform          平台
     * @param externalContactId 平台外部联系人 ID
     * @return 映射详情 (不存在返回 null)
     * @throws ScrmException 权限不足
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/mappings/by-external")
    public OperationResponse<ScrmExternalContactMappingDto> getMappingByExternal(
            @RequestParam String platform,
            @RequestParam String externalContactId) throws ScrmException {
        return OperationResponse.build(syncService.getMappingByExternal(platform, externalContactId));
    }

    /**
     * 按客户 ID 查询映射列表 (一个客户可能映射到多个平台联系人)。
     *
     * @param customerId SCRM 客户 ID
     * @return 映射列表
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @PostMapping("/mappings/by-customer/{customerId}")
    public OperationResponse<List<ScrmExternalContactMappingDto>> getMappingByCustomer(
            @PathVariable Long customerId) {
        return OperationResponse.build(syncService.getMappingByCustomer(customerId));
    }

    /**
     * 分页查询联系人映射, 支持按平台、同步状态与关键字过滤。
     *
     * @param platform   平台过滤 (可选)
     * @param syncStatus 同步状态过滤 (可选)
     * @param keyword    关键字模糊匹配外部名称 / 客户名称 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 映射分页结果
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/mappings/list")
    public OperationResponse<Page<ScrmExternalContactMappingDto>> listMappings(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String syncStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastSyncAt"));
        return OperationResponse.build(syncService.listMappings(platform, syncStatus, keyword, pageable));
    }

    /**
     * 移除联系人映射 (软删除)。
     *
     * @param id 映射 ID
     * @return 空响应
     * @throws ScrmException 映射不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "delete")
    @DeleteMapping("/mappings/{id}")
    public OperationResponse<Void> removeMapping(@PathVariable Long id) throws ScrmException {
        syncService.removeMapping(id);
        return OperationResponse.build();
    }

    /**
     * 合并联系人: 将映射指向目标客户。
     *
     * @param id              映射 ID
     * @param targetCustomerId 目标客户 ID
     * @return 更新后的映射
     * @throws ScrmException 映射不存在 / 客户不存在
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "update")
    @PostMapping("/mappings/{id}/merge")
    public OperationResponse<ScrmExternalContactMappingDto> mergeContact(
            @PathVariable Long id,
            @RequestParam Long targetCustomerId) throws ScrmException {
        return OperationResponse.build(syncService.mergeContact(id, targetCustomerId));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 同步统计概览: 任务数、成功率、新增、更新、失败。
     *
     * @param startTime 起始时间 (可选, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可选, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getSyncStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(syncService.getSyncStats(startTime, endTime));
    }

    /**
     * 配置统计: 指定配置的任务数、成功率、新增、更新、失败。
     *
     * @param configId 配置 ID
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/stats/config/{configId}")
    public OperationResponse<Map<String, Object>> getConfigStats(@PathVariable Long configId)
            throws ScrmException {
        return OperationResponse.build(syncService.getConfigStats(configId));
    }

    /**
     * 同步趋势: 近 N 天每日任务创建数。
     *
     * @param days 天数 (默认 7, 上限 90)
     * @return 趋势列表
     */
    @RequirePermission(resource = "scrm_external_contact_sync", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getSyncTrend(
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        return OperationResponse.build(syncService.getSyncTrend(days));
    }
}
