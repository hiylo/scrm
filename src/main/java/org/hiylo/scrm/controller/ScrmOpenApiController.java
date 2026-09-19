/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpenApiController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmApiAccessLogDto;
import org.hiylo.scrm.dto.ScrmApiAppCreateDto;
import org.hiylo.scrm.dto.ScrmApiAppDto;
import org.hiylo.scrm.dto.ScrmApiKeyDto;
import org.hiylo.scrm.dto.ScrmApiScopeDto;
import org.hiylo.scrm.dto.ScrmApiStatsDto;
import org.hiylo.scrm.dto.ScrmOAuthTokenDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmOpenApiService;
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
 * SCRM 开放API / 第三方接入控制器
 * <p>
 * 提供API应用管理、API密钥管理、权限范围管理、OAuth2授权、API调用日志检索、
 * 速率限制检查与调用统计等接口。权限由 gateway-server 统一鉴权, 此处通过
 * {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 * <p><b>OAuth2 授权码 / 令牌 / 刷新 / 吊销 / 验证端点已启用 (令牌为模拟生成, 待对接真实 OAuth2 服务器)。</b></p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/open-api")
@RequiredArgsConstructor
public class ScrmOpenApiController {

    /** OpenAPI 服务 */
    private final ScrmOpenApiService openApiService;

    // ============================================================
    // API 应用管理
    // ============================================================

    /**
     * 创建API应用 (自动生成 clientId 与 clientSecret)
     *
     * @param dto 应用创建参数
     * @return 创建后的应用 (含明文 clientSecret, 仅此一次返回)
     */
    @RequirePermission(resource = "scrm_open_api", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/apps")
    public OperationResponse<ScrmApiAppDto> createApp(@Valid @RequestBody ScrmApiAppCreateDto dto)
            throws ScrmException {
        return OperationResponse.build(openApiService.createApp(dto));
    }

    /**
     * 更新API应用信息
     *
     * @param id  应用 ID
     * @param dto 应用参数
     * @return 更新后的应用
     */
    @RequirePermission(resource = "scrm_open_api", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/apps/{id}")
    public OperationResponse<ScrmApiAppDto> updateApp(@PathVariable Long id,
                                                       @RequestBody ScrmApiAppDto dto)
            throws ScrmException {
        return OperationResponse.build(openApiService.updateApp(id, dto));
    }

    /**
     * 删除API应用 (级联清理关联密钥)
     *
     * @param id 应用 ID
     * @return 空响应
     * @throws ScrmException 应用不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_open_api", action = "delete")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @DeleteMapping("/apps/{id}")
    public OperationResponse<Void> deleteApp(@PathVariable Long id) throws ScrmException {
        openApiService.deleteApp(id);
        return OperationResponse.build();
    }

    /**
     * 查询API应用详情
     *
     * @param id 应用 ID
     * @return 应用详情
     * @throws ScrmException 应用不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/apps/{id}")
    public OperationResponse<ScrmApiAppDto> getApp(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.getApp(id));
    }

    /**
     * 分页查询API应用列表, 支持按应用类型 / 状态 / 关键词过滤
     *
     * @param appType 应用类型过滤 (可选)
     * @param status  状态过滤 (可选)
     * @param keyword 关键词过滤, 匹配应用名称 / 应用编码 / clientId (可选)
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 应用分页结果
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/apps/list")
    public OperationResponse<Page<ScrmApiAppDto>> listApps(
            @RequestParam(required = false) String appType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(openApiService.listApps(appType, status, keyword, pageable));
    }

    /**
     * 暂停API应用 (状态置为 SUSPENDED)
     *
     * @param id 应用 ID
     * @return 更新后的应用
     * @throws ScrmException 应用不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "update")
    @PostMapping("/apps/{id}/suspend")
    public OperationResponse<ScrmApiAppDto> suspendApp(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.suspendApp(id));
    }

    /**
     * 激活API应用 (状态置为 ACTIVE)
     *
     * @param id 应用 ID
     * @return 更新后的应用
     * @throws ScrmException 应用不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "update")
    @PostMapping("/apps/{id}/activate")
    public OperationResponse<ScrmApiAppDto> activateApp(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.activateApp(id));
    }

    /**
     * 吊销API应用 (状态置为 REVOKED, 不可恢复)
     *
     * @param id 应用 ID
     * @return 更新后的应用
     * @throws ScrmException 应用不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "update")
    @PostMapping("/apps/{id}/revoke")
    public OperationResponse<ScrmApiAppDto> revokeApp(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.revokeApp(id));
    }

    /**
     * 重新生成API应用密钥 (轮换 clientId 与 clientSecret, 旧凭证立即失效)
     *
     * @param id 应用 ID
     * @return 更新后的应用 (含新明文 clientSecret, 仅此一次返回)
     * @throws ScrmException 应用不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/apps/{id}/regenerate-secret")
    public OperationResponse<ScrmApiAppDto> regenerateSecret(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.regenerateSecret(id));
    }

    // ============================================================
    // API 密钥管理
    // ============================================================

    /**
     * 为指定应用创建API密钥 (自动生成 apiKey)
     *
     * @param appId 应用 ID
     * @param dto   密钥参数
     * @return 创建后的密钥 (含明文 apiKey, 仅此一次返回)
     * @throws ScrmException 应用不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/apps/{appId}/keys")
    public OperationResponse<ScrmApiKeyDto> createKey(@PathVariable Long appId,
                                                       @Valid @RequestBody ScrmApiKeyDto dto)
            throws ScrmException {
        return OperationResponse.build(openApiService.createKey(appId, dto));
    }

    /**
     * 分页查询指定应用的API密钥列表
     *
     * @param appId  应用 ID
     * @param status 状态过滤 (可选)
     * @param page   页码 (从 0 开始, 默认 0)
     * @param size   每页大小 (默认 20)
     * @return 密钥分页结果
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/apps/{appId}/keys")
    public OperationResponse<Page<ScrmApiKeyDto>> listKeys(@PathVariable Long appId,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(openApiService.listKeys(appId, status, pageable));
    }

    /**
     * 查询密钥详情
     *
     * @param id 密钥 ID
     * @return 密钥详情
     * @throws ScrmException 密钥不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/keys/{id}")
    public OperationResponse<ScrmApiKeyDto> getKey(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.getKey(id));
    }

    /**
     * 删除密钥 (软删除, 状态置为 REVOKED)
     *
     * @param id 密钥 ID
     * @return 更新后的密钥
     * @throws ScrmException 密钥不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "delete")
    @DeleteMapping("/keys/{id}")
    public OperationResponse<ScrmApiKeyDto> deleteKey(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.revokeKey(id));
    }

    /**
     * 吊销密钥 (状态置为 REVOKED)
     *
     * @param id 密钥 ID
     * @return 更新后的密钥
     * @throws ScrmException 密钥不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "update")
    @PostMapping("/keys/{id}/revoke")
    public OperationResponse<ScrmApiKeyDto> revokeKey(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.revokeKey(id));
    }

    // ============================================================
    // 权限范围管理
    // ============================================================

    /**
     * 创建权限范围
     *
     * @param dto 权限范围参数
     * @return 创建后的权限范围
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/scopes")
    public OperationResponse<ScrmApiScopeDto> createScope(@Valid @RequestBody ScrmApiScopeDto dto)
            throws ScrmException {
        return OperationResponse.build(openApiService.createScope(dto));
    }

    /**
     * 更新权限范围
     *
     * @param id  权限范围 ID
     * @param dto 权限范围参数
     * @return 更新后的权限范围
     * @throws ScrmException 权限范围不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "update")
    @PutMapping("/scopes/{id}")
    public OperationResponse<ScrmApiScopeDto> updateScope(@PathVariable Long id,
                                                           @RequestBody ScrmApiScopeDto dto)
            throws ScrmException {
        return OperationResponse.build(openApiService.updateScope(id, dto));
    }

    /**
     * 删除权限范围
     *
     * @param id 权限范围 ID
     * @return 空响应
     * @throws ScrmException 权限范围不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_open_api", action = "delete")
    @DeleteMapping("/scopes/{id}")
    public OperationResponse<Void> deleteScope(@PathVariable Long id) throws ScrmException {
        openApiService.deleteScope(id);
        return OperationResponse.build();
    }

    /**
     * 查询权限范围详情
     *
     * @param id 权限范围 ID
     * @return 权限范围详情
     * @throws ScrmException 权限范围不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/scopes/{id}")
    public OperationResponse<ScrmApiScopeDto> getScope(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.getScope(id));
    }

    /**
     * 分页查询权限范围列表, 支持按资源 / 启用状态过滤
     *
     * @param resource 资源过滤 (可选)
     * @param enabled  启用状态过滤 (可选)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 权限范围分页结果
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/scopes/list")
    public OperationResponse<Page<ScrmApiScopeDto>> listScopes(
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(openApiService.listScopes(resource, enabled, pageable));
    }

    /**
     * 启用权限范围
     *
     * @param id 权限范围 ID
     * @return 更新后的权限范围
     * @throws ScrmException 权限范围不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "update")
    @PostMapping("/scopes/{id}/enable")
    public OperationResponse<ScrmApiScopeDto> enableScope(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.enableScope(id));
    }

    /**
     * 禁用权限范围
     *
     * @param id 权限范围 ID
     * @return 更新后的权限范围
     * @throws ScrmException 权限范围不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "update")
    @PostMapping("/scopes/{id}/disable")
    public OperationResponse<ScrmApiScopeDto> disableScope(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.disableScope(id));
    }

    // ============================================================
    // OAuth2 授权 (模拟实现)
    // ============================================================

    /**
     * OAuth2 授权码生成 (模拟)
     *
     * @param clientId    客户端 ID
     * @param redirectUri 回调地址
     * @param scopes      权限范围 (可选)
     * @param state       state 参数 (可选, 防 CSRF)
     * @return 授权结果: code / state / redirectUri
     * @throws ScrmException 客户端不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @PostMapping("/oauth/authorize")
    public OperationResponse<Map<String, Object>> authorize(@RequestParam String clientId,
                                                             @RequestParam(required = false) String redirectUri,
                                                             @RequestParam(required = false) String scopes,
                                                             @RequestParam(required = false) String state)
            throws ScrmException {
        return OperationResponse.build(openApiService.authorize(clientId, redirectUri, scopes, state));
    }

    /**
     * OAuth2 获取令牌 (模拟)
     *
     * @param tokenDto 令牌请求
     * @return 令牌响应: access_token / refresh_token / token_type / expires_in / scope
     * @throws ScrmException 应用不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/oauth/token")
    public OperationResponse<Map<String, Object>> getToken(@Valid @RequestBody ScrmOAuthTokenDto tokenDto)
            throws ScrmException {
        return OperationResponse.build(openApiService.getToken(tokenDto));
    }

    /**
     * OAuth2 刷新令牌 (模拟)
     *
     * @param refreshToken 刷新令牌
     * @return 新的令牌响应
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @PostMapping("/oauth/token/refresh")
    public OperationResponse<Map<String, Object>> refreshToken(@RequestParam String refreshToken) {
        return OperationResponse.build(openApiService.refreshToken(refreshToken));
    }

    /**
     * OAuth2 吊销令牌 (模拟)
     *
     * @param token 访问令牌或刷新令牌
     * @return 吊销结果
     */
    @RequirePermission(resource = "scrm_open_api", action = "update")
    @PostMapping("/oauth/token/revoke")
    public OperationResponse<Map<String, Object>> revokeToken(@RequestParam String token) {
        return OperationResponse.build(openApiService.revokeToken(token));
    }

    /**
     * OAuth2 验证令牌 (模拟)
     *
     * @param token 访问令牌
     * @return 令牌信息: active / token_type / expires_in
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @PostMapping("/oauth/token/validate")
    public OperationResponse<Map<String, Object>> validateToken(@RequestParam String token) {
        return OperationResponse.build(openApiService.validateToken(token));
    }

    // ============================================================
    // 访问日志
    // ============================================================

    /**
     * 分页查询访问日志, 支持按应用 / clientId / 端点 / 方法 / 状态码 / 时间区间过滤
     *
     * @param appId          应用 ID 过滤 (可选)
     * @param clientId       客户端 ID 过滤 (可选)
     * @param endpoint       端点过滤 (可选, 模糊匹配)
     * @param method         HTTP 方法过滤 (可选)
     * @param responseStatus 响应状态码过滤 (可选)
     * @param startTime      起始时间 (可选, ISO 格式)
     * @param endTime        截止时间 (可选, ISO 格式)
     * @param page           页码 (从 0 开始, 默认 0)
     * @param size           每页大小 (默认 20)
     * @return 日志分页结果
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/access-logs/list")
    public OperationResponse<Page<ScrmApiAccessLogDto>> listAccessLogs(
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer responseStatus,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(openApiService.listAccessLogs(
                appId, clientId, endpoint, method, responseStatus, startTime, endTime, pageable));
    }

    /**
     * 查询访问日志详情
     *
     * @param id 日志 ID
     * @return 日志详情
     * @throws ScrmException 日志不存在
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/access-logs/{id}")
    public OperationResponse<ScrmApiAccessLogDto> getAccessLog(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(openApiService.getAccessLog(id));
    }

    /**
     * 按应用分页查询访问日志
     *
     * @param appId 应用 ID
     * @param page  页码 (从 0 开始, 默认 0)
     * @param size  每页大小 (默认 20)
     * @return 日志分页结果
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/access-logs/app/{appId}")
    public OperationResponse<Page<ScrmApiAccessLogDto>> getAccessLogsByApp(@PathVariable Long appId,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(
                                                                                    defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(openApiService.getAccessLogsByApp(appId, pageable));
    }

    /**
     * 按请求 IP 分页查询访问日志
     *
     * @param ip   请求 IP
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 日志分页结果
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/access-logs/ip/{ip}")
    public OperationResponse<Page<ScrmApiAccessLogDto>> getAccessLogsByIp(@PathVariable String ip,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(openApiService.getAccessLogsByIp(ip, pageable));
    }

    /**
     * 查询最近 N 条错误日志 (HTTP 状态码 >= 400)
     *
     * @param limit 返回条数 (默认 20)
     * @return 错误日志列表
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/access-logs/errors/recent")
    public OperationResponse<List<ScrmApiAccessLogDto>> getRecentErrors(
            @RequestParam(defaultValue = "20") int limit) {
        return OperationResponse.build(openApiService.getRecentErrors(limit));
    }

    // ============================================================
    // 速率限制 (模拟实现)
    // ============================================================

    /**
     * 检查速率限制 (模拟)
     *
     * @param appId    应用 ID (可选)
     * @param apiKeyId 密钥 ID (可选)
     * @return true 允许访问, false 触发限流
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @PostMapping("/rate-limit/check")
    public OperationResponse<Boolean> checkRateLimit(@RequestParam(required = false) Long appId,
                                                      @RequestParam(required = false) Long apiKeyId)
            throws ScrmException {
        return OperationResponse.build(openApiService.checkRateLimit(appId, apiKeyId));
    }

    /**
     * 获取应用速率限制状态
     *
     * @param appId 应用 ID
     * @return 速率限制状态
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/rate-limit/status/{appId}")
    public OperationResponse<Map<String, Object>> getRateLimitStatus(@PathVariable Long appId)
            throws ScrmException {
        return OperationResponse.build(openApiService.getRateLimitStatus(appId));
    }

    /**
     * 重置应用速率限制计数 (今日请求计数清零)
     *
     * @param appId 应用 ID
     * @return 重置后的速率限制状态
     * @throws ScrmException 应用不存在
     */
    @RequirePermission(resource = "scrm_open_api", action = "update")
    @PostMapping("/rate-limit/reset/{appId}")
    public OperationResponse<Map<String, Object>> resetRateLimit(@PathVariable Long appId) throws ScrmException {
        return OperationResponse.build(openApiService.resetRateLimit(appId));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 获取API总体统计 (请求数 / 错误率 / 平均响应时间 / 活跃应用数)
     *
     * @param startTime 起始时间 (可选, ISO 格式)
     * @param endTime   截止时间 (可选, ISO 格式)
     * @return API 统计
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<ScrmApiStatsDto> getApiStats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(openApiService.getApiStats(startTime, endTime));
    }

    /**
     * 获取应用统计 (请求数 / 错误率 / 平均响应时间 / 最后访问时间)
     *
     * @param appId 应用 ID
     * @return 应用统计
     * @throws ScrmException 应用不存在
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/stats/app/{appId}")
    public OperationResponse<Map<String, Object>> getAppStats(@PathVariable Long appId) throws ScrmException {
        return OperationResponse.build(openApiService.getAppStats(appId));
    }

    /**
     * 获取端点统计 (热门端点排行)
     *
     * @param startTime 起始时间 (可选, ISO 格式)
     * @param endTime   截止时间 (可选, ISO 格式)
     * @return 端点统计列表
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/stats/endpoints")
    public OperationResponse<List<Map<String, Object>>> getEndpointStats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(openApiService.getEndpointStats(startTime, endTime));
    }

    /**
     * 获取错误统计 (按错误码分布)
     *
     * @param startTime 起始时间 (可选, ISO 格式)
     * @param endTime   截止时间 (可选, ISO 格式)
     * @return 错误统计列表
     */
    @RequirePermission(resource = "scrm_open_api", action = "read")
    @GetMapping("/stats/errors")
    public OperationResponse<List<Map<String, Object>>> getErrorStats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(openApiService.getErrorStats(startTime, endTime));
    }
}
