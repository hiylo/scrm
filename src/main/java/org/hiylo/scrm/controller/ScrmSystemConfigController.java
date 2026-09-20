/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmConfigBatchUpdateDto;
import org.hiylo.scrm.dto.ScrmConfigGroupDto;
import org.hiylo.scrm.dto.ScrmConfigHistoryDto;
import org.hiylo.scrm.dto.ScrmConfigImportDto;
import org.hiylo.scrm.dto.ScrmConfigSearchDto;
import org.hiylo.scrm.dto.ScrmConfigUpdateDto;
import org.hiylo.scrm.dto.ScrmSystemConfigDto;
import org.hiylo.scrm.entity.ScrmConfigGroupEntity;
import org.hiylo.scrm.entity.ScrmConfigHistoryEntity;
import org.hiylo.scrm.entity.ScrmSystemConfigEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmSystemConfigService;
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
 * SCRM 系统配置管理控制器。
 * <p>
 * 提供系统配置管理模块的完整接口, 分为四大域:
 * <ul>
 *   <li>Configs: 配置 CRUD / 取值 / 设值 / 批量 / 重置 / 启停 / 验证 /
 *       按分组/模块/环境/角色查询 / 敏感 / 可覆盖 / 搜索 / 树 / 导入导出 / 缓存 / 依赖。</li>
 *   <li>Groups: 配置分组 CRUD / 启停 / 树 / 子分组 / 统计 / 移动 / 计数。</li>
 *   <li>History: 历史 CRUD / 近期变更 / 回滚 / 批量回滚 / 回滚历史 / 版本对比 / 导出。</li>
 *   <li>Stats: 配置概览 / 变更统计 / 分组统计 / 覆盖率 / 热门配置 / 过期配置。</li>
 * </ul>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 * <p><b>手动变更历史端点已启用 (自动补全配置快照)。</b></p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/system-config")
@RequiredArgsConstructor
public class ScrmSystemConfigController {

    /** 系统配置服务 */
    private final ScrmSystemConfigService scrmSystemConfigService;

    // ============================================================
    // 系统配置管理
    // ============================================================

    /**
     * 创建系统配置。
     *
     * @param dto 配置参数
     * @return 创建后的配置
     * @throws ScrmException 参数非法 / 键重复
     */
    @RequirePermission(resource = "scrm_system_config", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/configs")
    public OperationResponse<ScrmSystemConfigEntity> createConfig(@Valid @RequestBody ScrmSystemConfigDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.createConfig(dto));
    }

    /**
     * 更新系统配置。
     *
     * @param id  配置 ID
     * @param dto 配置参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 参数非法 / 键重复
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/configs/{id}")
    public OperationResponse<ScrmSystemConfigEntity> updateConfig(@PathVariable Long id,
                                                                    @RequestBody ScrmSystemConfigDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.updateConfig(id, dto));
    }

    /**
     * 删除系统配置 (系统级配置不允许删除)。
     *
     * @param id 配置 ID
     * @return 空响应
     * @throws ScrmException 配置不存在 / 系统级不可删
     */
    @RequirePermission(resource = "scrm_system_config", action = "delete")
    @DeleteMapping("/configs/{id}")
    public OperationResponse<Void> deleteConfig(@PathVariable Long id) throws ScrmException {
        scrmSystemConfigService.deleteConfig(id);
        return OperationResponse.build();
    }

    /**
     * 查询配置详情。
     *
     * @param id 配置 ID
     * @return 配置详情
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/{id}")
    public OperationResponse<ScrmSystemConfigEntity> getConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.getConfig(id));
    }

    /**
     * 按配置键查询配置。
     *
     * @param key 配置键
     * @return 配置详情
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/by-key/{key}")
    public OperationResponse<ScrmSystemConfigEntity> getConfigByKey(@PathVariable String key)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.getConfigByKey(key));
    }

    /**
     * 分页查询配置列表。
     *
     * @param group       配置分组过滤（可空）
     * @param type        配置类型过滤（可空）
     * @param environment 环境限定过滤（可空）
     * @param enabled     启用状态过滤（可空）
     * @param keyword     关键字模糊匹配（可空）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 配置分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/list")
    public OperationResponse<Page<ScrmSystemConfigEntity>> listConfigs(
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemConfigService.listConfigs(
                group, type, environment, enabled, keyword, pageable));
    }

    /**
     * 按配置分组分页查询配置。
     *
     * @param group 配置分组
     * @param page  页码（从 0 开始, 默认 0）
     * @param size  每页大小（默认 20）
     * @return 配置分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/by-group/{group}")
    public OperationResponse<Page<ScrmSystemConfigEntity>> getConfigsByGroup(@PathVariable String group,
                                                                               @RequestParam(
                                                                                       defaultValue = "0") int page,
                                                                               @RequestParam(
                                                                                       defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "displayOrder"));
        return OperationResponse.build(scrmSystemConfigService.getConfigsByGroup(group, pageable));
    }

    /**
     * 获取配置值 (带缓存)。
     *
     * @param key 配置键
     * @return 配置值
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/value/{key}")
    public OperationResponse<String> getConfigValue(@PathVariable String key) {
        return OperationResponse.build(scrmSystemConfigService.getConfigValue(key));
    }

    /**
     * 获取配置显示值 (解密/格式化)。
     *
     * @param key 配置键
     * @return 显示值
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/display-value/{key}")
    public OperationResponse<String> getConfigDisplayValue(@PathVariable String key) throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.getConfigDisplayValue(key));
    }

    /**
     * 设置配置值 (验证 → 记录历史 → 清除缓存)。
     *
     * @param updateDto 更新参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 只读 / 值非法
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/configs/value")
    public OperationResponse<ScrmSystemConfigEntity> setConfigValue(@Valid @RequestBody ScrmConfigUpdateDto updateDto)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.setConfigValue(updateDto));
    }

    /**
     * 批量更新配置值。
     *
     * @param batchDto 批量更新参数
     * @return 批量处理结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/configs/batch-update")
    public OperationResponse<Map<String, Object>> batchUpdate(@Valid @RequestBody ScrmConfigBatchUpdateDto batchDto) {
        return OperationResponse.build(scrmSystemConfigService.batchUpdate(batchDto));
    }

    /**
     * 重置配置为默认值。
     *
     * @param key 配置键
     * @return 重置后的配置
     * @throws ScrmException 配置不存在 / 无默认值
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @PostMapping("/configs/reset")
    public OperationResponse<ScrmSystemConfigEntity> resetConfig(@RequestParam String key) throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.resetConfig(key));
    }

    /**
     * 启用配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @PostMapping("/configs/{id}/enable")
    public OperationResponse<ScrmSystemConfigEntity> enableConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.enableConfig(id));
    }

    /**
     * 禁用配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @PostMapping("/configs/{id}/disable")
    public OperationResponse<ScrmSystemConfigEntity> disableConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.disableConfig(id));
    }

    /**
     * 切换配置启用状态。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @PostMapping("/configs/{id}/toggle")
    public OperationResponse<ScrmSystemConfigEntity> toggleConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.toggleConfig(id));
    }

    /**
     * 验证配置值合法性。
     *
     * @param key   配置键
     * @param value 待验证值
     * @return 验证结果
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @PostMapping("/configs/validate")
    public OperationResponse<Map<String, Object>> validateConfigValue(@RequestParam String key,
                                                                       @RequestParam(required = false) String value)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.validateConfigValue(key, value));
    }

    /**
     * 按适用模块分页查询配置。
     *
     * @param module 模块名
     * @param page   页码
     * @param size   每页大小
     * @return 配置分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/by-module/{module}")
    public OperationResponse<Page<ScrmSystemConfigEntity>> getConfigsByModule(@PathVariable String module,
                                                                                @RequestParam(
                                                                                        defaultValue = "0") int page,
                                                                                @RequestParam(
                                                                                        defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemConfigService.getConfigsByModule(module, pageable));
    }

    /**
     * 按环境限定分页查询配置。
     *
     * @param env  环境限定: ALL/DEV/STAGING/PRODUCTION
     * @param page 页码
     * @param size 每页大小
     * @return 配置分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/by-environment/{env}")
    public OperationResponse<Page<ScrmSystemConfigEntity>> getConfigsByEnvironment(@PathVariable String env,
                                                                                      @RequestParam(defaultValue = "0")
                                                                                          int page,
                                                                                      @RequestParam(defaultValue = "20")
                                                                                          int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemConfigService.getConfigsByEnvironment(env, pageable));
    }

    /**
     * 按可见角色分页查询配置。
     *
     * @param role 角色名
     * @param page 页码
     * @param size 每页大小
     * @return 配置分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/by-role/{role}")
    public OperationResponse<Page<ScrmSystemConfigEntity>> getConfigsByRole(@PathVariable String role,
                                                                              @RequestParam(
                                                                                      defaultValue = "0") int page,
                                                                              @RequestParam(
                                                                                      defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemConfigService.getConfigsByRole(role, pageable));
    }

    /**
     * 查询敏感配置。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 配置分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/sensitive")
    public OperationResponse<Page<ScrmSystemConfigEntity>> getSensitiveConfigs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemConfigService.getSensitiveConfigs(pageable));
    }

    /**
     * 查询可覆盖配置。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 配置分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/overridable")
    public OperationResponse<Page<ScrmSystemConfigEntity>> getOverridableConfigs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemConfigService.getOverridableConfigs(pageable));
    }

    /**
     * 高级搜索配置。
     *
     * @param searchDto 搜索参数
     * @param page      页码
     * @param size      每页大小
     * @return 配置分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @PostMapping("/configs/search")
    public OperationResponse<Page<ScrmSystemConfigEntity>> searchConfigs(@RequestBody ScrmConfigSearchDto searchDto,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSystemConfigService.searchConfigs(searchDto, pageable));
    }

    /**
     * 获取配置树 (分组 → 配置)。
     *
     * @return 配置树
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/tree")
    public OperationResponse<List<Map<String, Object>>> getConfigTree() {
        return OperationResponse.build(scrmSystemConfigService.getConfigTree());
    }

    /**
     * 导出配置。
     *
     * @param group       配置分组（可空）
     * @param environment 环境限定（可空）
     * @return 导出结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/export")
    public OperationResponse<Map<String, Object>> exportConfigs(
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String environment) {
        return OperationResponse.build(scrmSystemConfigService.exportConfigs(group, environment));
    }

    /**
     * 导入配置。
     *
     * @param importDto 导入参数
     * @return 导入结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/configs/import")
    public OperationResponse<Map<String, Object>> importConfigs(@Valid @RequestBody ScrmConfigImportDto importDto) {
        return OperationResponse.build(scrmSystemConfigService.importConfigs(importDto));
    }

    /**
     * 刷新配置缓存。
     *
     * @return 刷新结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @PostMapping("/configs/refresh-cache")
    public OperationResponse<Map<String, Object>> refreshCache() {
        return OperationResponse.build(scrmSystemConfigService.refreshCache());
    }

    /**
     * 查询配置依赖。
     *
     * @param key 配置键
     * @return 依赖配置列表
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/configs/dependencies/{key}")
    public OperationResponse<List<ScrmSystemConfigEntity>> getConfigDependencies(@PathVariable String key)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.getConfigDependencies(key));
    }

    // ============================================================
    // 配置分组管理
    // ============================================================

    /**
     * 创建配置分组。
     *
     * @param dto 分组参数
     * @return 创建后的分组
     * @throws ScrmException 参数非法 / 编码重复 / 父分组不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/groups")
    public OperationResponse<ScrmConfigGroupEntity> createGroup(@Valid @RequestBody ScrmConfigGroupDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.createGroup(dto));
    }

    /**
     * 更新配置分组。
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     * @throws ScrmException 分组不存在 / 参数非法 / 编码重复 / 父分组不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/groups/{id}")
    public OperationResponse<ScrmConfigGroupEntity> updateGroup(@PathVariable Long id,
                                                                  @RequestBody ScrmConfigGroupDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.updateGroup(id, dto));
    }

    /**
     * 删除配置分组 (仅允许删除空分组)。
     *
     * @param id 分组 ID
     * @return 空响应
     * @throws ScrmException 分组不存在 / 非空不可删
     */
    @RequirePermission(resource = "scrm_system_config", action = "delete")
    @DeleteMapping("/groups/{id}")
    public OperationResponse<Void> deleteGroup(@PathVariable Long id) throws ScrmException {
        scrmSystemConfigService.deleteGroup(id);
        return OperationResponse.build();
    }

    /**
     * 查询分组详情。
     *
     * @param id 分组 ID
     * @return 分组详情
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/groups/{id}")
    public OperationResponse<ScrmConfigGroupEntity> getGroup(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.getGroup(id));
    }

    /**
     * 按分组编码查询分组。
     *
     * @param code 分组编码
     * @return 分组详情
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/groups/code/{code}")
    public OperationResponse<ScrmConfigGroupEntity> getGroupByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.getGroupByCode(code));
    }

    /**
     * 分页查询分组列表。
     *
     * @param parentGroupCode 父分组编码（可空）
     * @param enabled         启用状态（可空）
     * @param keyword         关键字模糊匹配（可空）
     * @param page            页码
     * @param size            每页大小
     * @return 分组分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/groups/list")
    public OperationResponse<Page<ScrmConfigGroupEntity>> listGroups(
            @RequestParam(required = false) String parentGroupCode,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "displayOrder"));
        return OperationResponse.build(scrmSystemConfigService.listGroups(
                parentGroupCode, enabled, keyword, pageable));
    }

    /**
     * 获取分组树。
     *
     * @return 分组树
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/groups/tree")
    public OperationResponse<List<Map<String, Object>>> getGroupTree() {
        return OperationResponse.build(scrmSystemConfigService.getGroupTree());
    }

    /**
     * 查询子分组。
     *
     * @param parentCode 父分组编码
     * @return 子分组列表
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/groups/children/{parentCode}")
    public OperationResponse<List<ScrmConfigGroupEntity>> getChildGroups(@PathVariable String parentCode) {
        return OperationResponse.build(scrmSystemConfigService.getChildGroups(parentCode));
    }

    /**
     * 启用分组。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @PostMapping("/groups/{id}/enable")
    public OperationResponse<ScrmConfigGroupEntity> enableGroup(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.enableGroup(id));
    }

    /**
     * 禁用分组。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @PostMapping("/groups/{id}/disable")
    public OperationResponse<ScrmConfigGroupEntity> disableGroup(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.disableGroup(id));
    }

    /**
     * 更新分组配置数 (重新统计)。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @PostMapping("/groups/{id}/stats")
    public OperationResponse<ScrmConfigGroupEntity> updateGroupStats(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.updateGroupStats(id));
    }

    /**
     * 移动配置到指定分组。
     *
     * @param configId  配置 ID
     * @param groupCode 目标分组编码
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 分组不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @PostMapping("/groups/move-config")
    public OperationResponse<ScrmSystemConfigEntity> moveConfigToGroup(@RequestParam Long configId,
                                                                        @RequestParam String groupCode)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.moveConfigToGroup(configId, groupCode));
    }

    /**
     * 查询分组配置数。
     *
     * @param groupCode 分组编码
     * @return 配置数
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/groups/count/{groupCode}")
    public OperationResponse<Integer> getGroupConfigCount(@PathVariable String groupCode) {
        return OperationResponse.build(scrmSystemConfigService.getGroupConfigCount(groupCode));
    }

    // ============================================================
    // 变更历史管理
    // ============================================================

    /**
     * 查询历史详情。
     *
     * @param id 历史 ID
     * @return 历史详情
     * @throws ScrmException 历史不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/history/{id}")
    public OperationResponse<ScrmConfigHistoryEntity> getHistory(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.getHistory(id));
    }

    /**
     * 分页查询历史列表。
     *
     * @param configId   配置 ID（可空）
     * @param configKey  配置键（可空）
     * @param changeType 变更类型（可空）
     * @param changedBy  变更人（可空）
     * @param startTime  起始时间（可空）
     * @param endTime    结束时间（可空）
     * @param page       页码
     * @param size       每页大小
     * @return 历史分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/history/list")
    public OperationResponse<Page<ScrmConfigHistoryEntity>> listHistory(
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) String configKey,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "changedAt"));
        return OperationResponse.build(scrmSystemConfigService.listHistory(
                configId, configKey, changeType, changedBy, startTime, endTime, pageable));
    }

    /**
     * 按配置 ID 分页查询历史。
     *
     * @param configId 配置 ID
     * @param page     页码
     * @param size     每页大小
     * @return 历史分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/history/by-config/{configId}")
    public OperationResponse<Page<ScrmConfigHistoryEntity>> getHistoryByConfig(@PathVariable Long configId,
                                                                                 @RequestParam(
                                                                                         defaultValue = "0") int page,
                                                                                  @RequestParam(
                                                                                          defaultValue =
                                                                                                "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "changedAt"));
        return OperationResponse.build(scrmSystemConfigService.getHistoryByConfig(configId, pageable));
    }

    /**
     * 按配置键分页查询历史。
     *
     * @param key  配置键
     * @param page 页码
     * @param size 每页大小
     * @return 历史分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/history/by-key/{key}")
    public OperationResponse<Page<ScrmConfigHistoryEntity>> getHistoryByKey(@PathVariable String key,
                                                                              @RequestParam(
                                                                                      defaultValue = "0") int page,
                                                                              @RequestParam(
                                                                                      defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "changedAt"));
        return OperationResponse.build(scrmSystemConfigService.getHistoryByKey(key, pageable));
    }

    /**
     * 按变更人分页查询历史。
     *
     * @param userId 变更人 ID
     * @param page   页码
     * @param size   每页大小
     * @return 历史分页结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/history/by-user/{userId}")
    public OperationResponse<Page<ScrmConfigHistoryEntity>> getHistoryByUser(@PathVariable String userId,
                                                                              @RequestParam(
                                                                                      defaultValue = "0") int page,
                                                                              @RequestParam(
                                                                                      defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "changedAt"));
        return OperationResponse.build(scrmSystemConfigService.getHistoryByUser(userId, pageable));
    }

    /**
     * 查询近期变更 (最近 N 天)。
     *
     * @param days 天数
     * @return 历史列表
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/history/recent/{days}")
    public OperationResponse<List<ScrmConfigHistoryEntity>> getRecentChanges(@PathVariable int days) {
        return OperationResponse.build(scrmSystemConfigService.getRecentChanges(days));
    }

    /**
     * 回滚配置 (恢复旧值 → 创建新历史 → 更新配置)。
     *
     * @param historyId    历史 ID
     * @param rolledBackBy 回滚人
     * @return 更新后的配置
     * @throws ScrmException 历史不存在 / 不可回滚 / 已回滚
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/history/rollback")
    public OperationResponse<ScrmSystemConfigEntity> rollback(@RequestParam Long historyId,
                                                                @RequestParam(required = false) String rolledBackBy)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.rollback(historyId, rolledBackBy));
    }

    /**
     * 批量回滚。
     *
     * @param historyIds   历史 ID 列表
     * @param rolledBackBy 回滚人
     * @return 批量处理结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/history/batch-rollback")
    public OperationResponse<Map<String, Object>> batchRollback(@RequestParam List<Long> historyIds,
                                                                  @RequestParam(required = false) String rolledBackBy) {
        return OperationResponse.build(scrmSystemConfigService.batchRollback(historyIds, rolledBackBy));
    }

    /**
     * 查询回滚历史 (哪些回滚操作关联了指定历史)。
     *
     * @param historyId 历史 ID
     * @return 回滚历史列表
     * @throws ScrmException 历史不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/history/rollback-history/{historyId}")
    public OperationResponse<List<ScrmConfigHistoryEntity>> getRollbackHistory(@PathVariable Long historyId)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.getRollbackHistory(historyId));
    }

    /**
     * 版本对比。
     *
     * @param historyId1 历史 ID 1
     * @param historyId2 历史 ID 2
     * @return 对比结果
     * @throws ScrmException 历史不存在
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @PostMapping("/history/compare")
    public OperationResponse<Map<String, Object>> compareVersions(@RequestParam Long historyId1,
                                                                    @RequestParam Long historyId2)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.compareVersions(historyId1, historyId2));
    }

    /**
     * 导出历史。
     *
     * @param configId  配置 ID（可空）
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 导出结果
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/history/export")
    public OperationResponse<Map<String, Object>> exportHistory(
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSystemConfigService.exportHistory(configId, startTime, endTime));
    }

    // ============================================================
    // 统计分析
    // ============================================================

    /**
     * 配置概览。
     *
     * @return 概览 Map
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getConfigOverview() {
        return OperationResponse.build(scrmSystemConfigService.getConfigOverview());
    }

    /**
     * 配置统计 (总数/各类型/各分组/各环境)。
     *
     * @return 配置统计 Map
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/stats/configs")
    public OperationResponse<Map<String, Object>> getConfigStats() {
        return OperationResponse.build(scrmSystemConfigService.getConfigStats());
    }

    /**
     * 变更统计 (次数/各类型/各用户)。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 变更统计 Map
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/stats/changes")
    public OperationResponse<Map<String, Object>> getChangeStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmSystemConfigService.getChangeStats(startTime, endTime));
    }

    /**
     * 分组统计。
     *
     * @return 分组统计 Map
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/stats/groups")
    public OperationResponse<Map<String, Object>> getGroupStats() {
        return OperationResponse.build(scrmSystemConfigService.getGroupStats());
    }

    /**
     * 配置覆盖率。
     *
     * @return 覆盖率 Map
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/stats/coverage")
    public OperationResponse<Map<String, Object>> getConfigCoverage() {
        return OperationResponse.build(scrmSystemConfigService.getConfigCoverage());
    }

    /**
     * 热门配置 (按变更次数倒序)。
     *
     * @param limit 返回数量
     * @return 热门配置列表
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/stats/popular")
    public OperationResponse<List<ScrmSystemConfigEntity>> getPopularConfigs(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmSystemConfigService.getPopularConfigs(limit));
    }

    /**
     * 长期未更新配置。
     *
     * @param days 天数
     * @return 过期配置列表
     */
    @RequirePermission(resource = "scrm_system_config", action = "read")
    @GetMapping("/stats/stale")
    public OperationResponse<List<ScrmSystemConfigEntity>> getStaleConfigs(
            @RequestParam(defaultValue = "90") int days) {
        return OperationResponse.build(scrmSystemConfigService.getStaleConfigs(days));
    }

    /**
     * 创建变更历史 (手动记录场景, 适用于外部系统导入 / 审计补录)。
     * <p>若配置键存在则自动补全 configId / configName / configGroup 快照; DTO 中已提供的字段优先。</p>
     *
     * @param dto 历史参数 (configKey + changeType + changedBy 必填)
     * @return 创建后的历史实体
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_system_config", action = "create")
    @PostMapping("/history")
    public OperationResponse<ScrmConfigHistoryEntity> createHistory(@Valid @RequestBody ScrmConfigHistoryDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSystemConfigService.createManualHistory(dto));
    }
}
