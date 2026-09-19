/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 系统配置管理服务 (门面)。
 * <p>
 * 作为系统配置模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmSystemConfigItemService} (配置项管理)、{@link ScrmSystemConfigQueryService} (检索/导入导出)、
 * {@link ScrmSystemConfigGroupService} (配置组管理)、{@link ScrmSystemConfigHistoryService} (历史与回滚)、
 * {@link ScrmSystemConfigStatsService} (统计概览) 与 {@link ScrmSystemConfigValueService} (值校验/格式化/解析)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmSystemConfigService {

    /** 配置项管理子域服务 */
    private final ScrmSystemConfigItemService itemService;
    /** 配置检索与运维子域服务 */
    private final ScrmSystemConfigQueryService queryService;
    /** 配置分组管理子域服务 */
    private final ScrmSystemConfigGroupService groupService;
    /** 历史版本与回滚子域服务 */
    private final ScrmSystemConfigHistoryService historyService;
    /** 配置统计子域服务 */
    private final ScrmSystemConfigStatsService statsService;
    /** 配置值处理子域服务 */
    private final ScrmSystemConfigValueService valueService;

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
    public ScrmSystemConfigEntity createConfig(ScrmSystemConfigDto dto) throws ScrmException {
        return itemService.createConfig(dto);
    }

    /**
     * 更新系统配置。
     *
     * @param id  配置 ID
     * @param dto 配置参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 参数非法 / 键重复
     */
    public ScrmSystemConfigEntity updateConfig(Long id, ScrmSystemConfigDto dto) throws ScrmException {
        return itemService.updateConfig(id, dto);
    }

    /**
     * 删除系统配置。
     *
     * @param id 配置 ID
     * @throws ScrmException 配置不存在 / 系统级不可删
     */
    public void deleteConfig(Long id) throws ScrmException {
        itemService.deleteConfig(id);
    }

    /**
     * 查询配置详情。
     *
     * @param id 配置 ID
     * @return 配置实体
     * @throws ScrmException 配置不存在
     */
    public ScrmSystemConfigEntity getConfig(Long id) throws ScrmException {
        return itemService.getConfig(id);
    }

    /**
     * 按配置键查询配置。
     *
     * @param key 配置键
     * @return 配置实体
     * @throws ScrmException 配置不存在
     */
    public ScrmSystemConfigEntity getConfigByKey(String key) throws ScrmException {
        return itemService.getConfigByKey(key);
    }

    /**
     * 分页查询配置列表。
     *
     * @param group       配置分组过滤（可空）
     * @param type        配置类型过滤（可空）
     * @param environment 环境限定过滤（可空）
     * @param enabled     启用状态过滤（可空）
     * @param keyword     关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 配置分页结果
     */
    public Page<ScrmSystemConfigEntity> listConfigs(String group, String type, String environment,
                                                     Boolean enabled, String keyword, Pageable pageable) {
        return itemService.listConfigs(group, type, environment, enabled, keyword, pageable);
    }

    /**
     * 按配置分组分页查询配置。
     *
     * @param group    配置分组
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    public Page<ScrmSystemConfigEntity> getConfigsByGroup(String group, Pageable pageable) {
        return itemService.getConfigsByGroup(group, pageable);
    }

    /**
     * 获取配置值 (带缓存)。
     *
     * @param key 配置键
     * @return 配置值
     */
    public String getConfigValue(String key) {
        return itemService.getConfigValue(key);
    }

    /**
     * 获取配置显示值。
     *
     * @param key 配置键
     * @return 显示值
     * @throws ScrmException 配置不存在
     */
    public String getConfigDisplayValue(String key) throws ScrmException {
        return itemService.getConfigDisplayValue(key);
    }

    /**
     * 设置配置值。
     *
     * @param updateDto 更新参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 只读 / 值非法
     */
    public ScrmSystemConfigEntity setConfigValue(ScrmConfigUpdateDto updateDto) throws ScrmException {
        return itemService.setConfigValue(updateDto);
    }

    /**
     * 批量更新配置值。
     *
     * @param batchDto 批量更新参数
     * @return 批量处理结果
     */
    public Map<String, Object> batchUpdate(ScrmConfigBatchUpdateDto batchDto) {
        return itemService.batchUpdate(batchDto);
    }

    /**
     * 重置配置为默认值。
     *
     * @param key 配置键
     * @return 重置后的配置
     * @throws ScrmException 配置不存在 / 无默认值
     */
    public ScrmSystemConfigEntity resetConfig(String key) throws ScrmException {
        return itemService.resetConfig(key);
    }

    /**
     * 启用配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    public ScrmSystemConfigEntity enableConfig(Long id) throws ScrmException {
        return itemService.enableConfig(id);
    }

    /**
     * 禁用配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    public ScrmSystemConfigEntity disableConfig(Long id) throws ScrmException {
        return itemService.disableConfig(id);
    }

    /**
     * 切换配置启用状态。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    public ScrmSystemConfigEntity toggleConfig(Long id) throws ScrmException {
        return itemService.toggleConfig(id);
    }

    /**
     * 验证配置值合法性。
     *
     * @param key   配置键
     * @param value 待验证值
     * @return 验证结果
     * @throws ScrmException 配置不存在
     */
    public Map<String, Object> validateConfigValue(String key, String value) throws ScrmException {
        return itemService.validateConfigValue(key, value);
    }

    /**
     * 按适用模块分页查询配置。
     *
     * @param module   模块名
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    public Page<ScrmSystemConfigEntity> getConfigsByModule(String module, Pageable pageable) {
        return queryService.getConfigsByModule(module, pageable);
    }

    /**
     * 按环境限定分页查询配置。
     *
     * @param env      环境限定
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    public Page<ScrmSystemConfigEntity> getConfigsByEnvironment(String env, Pageable pageable) {
        return queryService.getConfigsByEnvironment(env, pageable);
    }

    /**
     * 按可见角色分页查询配置。
     *
     * @param role     角色名
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    public Page<ScrmSystemConfigEntity> getConfigsByRole(String role, Pageable pageable) {
        return queryService.getConfigsByRole(role, pageable);
    }

    /**
     * 查询敏感配置。
     *
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    public Page<ScrmSystemConfigEntity> getSensitiveConfigs(Pageable pageable) {
        return queryService.getSensitiveConfigs(pageable);
    }

    /**
     * 查询可覆盖配置。
     *
     * @param pageable 分页参数
     * @return 配置分页结果
     */
    public Page<ScrmSystemConfigEntity> getOverridableConfigs(Pageable pageable) {
        return queryService.getOverridableConfigs(pageable);
    }

    /**
     * 高级搜索配置。
     *
     * @param searchDto 搜索参数
     * @param pageable  分页参数
     * @return 配置分页结果
     */
    public Page<ScrmSystemConfigEntity> searchConfigs(ScrmConfigSearchDto searchDto, Pageable pageable) {
        return queryService.searchConfigs(searchDto, pageable);
    }

    /**
     * 获取配置树。
     *
     * @return 配置树
     */
    public List<Map<String, Object>> getConfigTree() {
        return queryService.getConfigTree();
    }

    /**
     * 导出配置。
     *
     * @param group       配置分组（可空）
     * @param environment 环境限定（可空）
     * @return 导出结果
     */
    public Map<String, Object> exportConfigs(String group, String environment) {
        return queryService.exportConfigs(group, environment);
    }

    /**
     * 导入配置。
     *
     * @param importDto 导入参数
     * @return 导入结果
     */
    public Map<String, Object> importConfigs(ScrmConfigImportDto importDto) {
        return queryService.importConfigs(importDto);
    }

    /**
     * 刷新配置缓存。
     *
     * @return 刷新结果
     */
    public Map<String, Object> refreshCache() {
        return itemService.refreshCache();
    }

    /**
     * 查询配置依赖。
     *
     * @param key 配置键
     * @return 依赖配置列表
     * @throws ScrmException 配置不存在
     */
    public List<ScrmSystemConfigEntity> getConfigDependencies(String key) throws ScrmException {
        return itemService.getConfigDependencies(key);
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
    public ScrmConfigGroupEntity createGroup(ScrmConfigGroupDto dto) throws ScrmException {
        return groupService.createGroup(dto);
    }

    /**
     * 更新配置分组。
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     * @throws ScrmException 分组不存在 / 参数非法 / 编码重复 / 父分组不存在
     */
    public ScrmConfigGroupEntity updateGroup(Long id, ScrmConfigGroupDto dto) throws ScrmException {
        return groupService.updateGroup(id, dto);
    }

    /**
     * 删除配置分组。
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在 / 非空不可删
     */
    public void deleteGroup(Long id) throws ScrmException {
        groupService.deleteGroup(id);
    }

    /**
     * 查询分组详情。
     *
     * @param id 分组 ID
     * @return 分组实体
     * @throws ScrmException 分组不存在
     */
    public ScrmConfigGroupEntity getGroup(Long id) throws ScrmException {
        return groupService.getGroup(id);
    }

    /**
     * 按分组编码查询分组。
     *
     * @param code 分组编码
     * @return 分组实体
     * @throws ScrmException 分组不存在
     */
    public ScrmConfigGroupEntity getGroupByCode(String code) throws ScrmException {
        return groupService.getGroupByCode(code);
    }

    /**
     * 分页查询分组列表。
     *
     * @param parentGroupCode 父分组编码（可空）
     * @param enabled         启用状态（可空）
     * @param keyword         关键字模糊匹配（可空）
     * @param pageable        分页参数
     * @return 分组分页结果
     */
    public Page<ScrmConfigGroupEntity> listGroups(String parentGroupCode, Boolean enabled,
                                                    String keyword, Pageable pageable) {
        return groupService.listGroups(parentGroupCode, enabled, keyword, pageable);
    }

    /**
     * 获取分组树。
     *
     * @return 分组树
     */
    public List<Map<String, Object>> getGroupTree() {
        return groupService.getGroupTree();
    }

    /**
     * 查询子分组。
     *
     * @param parentCode 父分组编码
     * @return 子分组列表
     */
    public List<ScrmConfigGroupEntity> getChildGroups(String parentCode) {
        return groupService.getChildGroups(parentCode);
    }

    /**
     * 启用分组。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    public ScrmConfigGroupEntity enableGroup(Long id) throws ScrmException {
        return groupService.enableGroup(id);
    }

    /**
     * 禁用分组。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    public ScrmConfigGroupEntity disableGroup(Long id) throws ScrmException {
        return groupService.disableGroup(id);
    }

    /**
     * 更新分组配置数。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    public ScrmConfigGroupEntity updateGroupStats(Long id) throws ScrmException {
        return groupService.updateGroupStats(id);
    }

    /**
     * 移动配置到指定分组。
     *
     * @param configId  配置 ID
     * @param groupCode 目标分组编码
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 分组不存在
     */
    public ScrmSystemConfigEntity moveConfigToGroup(Long configId, String groupCode) throws ScrmException {
        return groupService.moveConfigToGroup(configId, groupCode);
    }

    /**
     * 查询分组配置数。
     *
     * @param groupCode 分组编码
     * @return 配置数
     */
    public int getGroupConfigCount(String groupCode) {
        return groupService.getGroupConfigCount(groupCode);
    }

    // ============================================================
    // 变更历史管理
    // ============================================================

    /**
     * 查询历史详情。
     *
     * @param id 历史 ID
     * @return 历史实体
     * @throws ScrmException 历史不存在
     */
    public ScrmConfigHistoryEntity getHistory(Long id) throws ScrmException {
        return historyService.getHistory(id);
    }

    /**
     * 手动创建配置变更历史记录。
     *
     * @param dto 历史参数
     * @return 创建后的历史实体
     * @throws ScrmException 参数非法
     */
    public ScrmConfigHistoryEntity createManualHistory(ScrmConfigHistoryDto dto) throws ScrmException {
        return historyService.createManualHistory(dto);
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
     * @param pageable   分页参数
     * @return 历史分页结果
     */
    public Page<ScrmConfigHistoryEntity> listHistory(Long configId, String configKey, String changeType,
                                                       String changedBy, LocalDateTime startTime,
                                                       LocalDateTime endTime, Pageable pageable) {
        return historyService.listHistory(configId, configKey, changeType, changedBy, startTime, endTime, pageable);
    }

    /**
     * 按配置 ID 分页查询历史。
     *
     * @param configId 配置 ID
     * @param pageable 分页参数
     * @return 历史分页结果
     */
    public Page<ScrmConfigHistoryEntity> getHistoryByConfig(Long configId, Pageable pageable) {
        return historyService.getHistoryByConfig(configId, pageable);
    }

    /**
     * 按配置键分页查询历史。
     *
     * @param key      配置键
     * @param pageable 分页参数
     * @return 历史分页结果
     */
    public Page<ScrmConfigHistoryEntity> getHistoryByKey(String key, Pageable pageable) {
        return historyService.getHistoryByKey(key, pageable);
    }

    /**
     * 按变更人分页查询历史。
     *
     * @param userId   变更人 ID
     * @param pageable 分页参数
     * @return 历史分页结果
     */
    public Page<ScrmConfigHistoryEntity> getHistoryByUser(String userId, Pageable pageable) {
        return historyService.getHistoryByUser(userId, pageable);
    }

    /**
     * 查询近期变更。
     *
     * @param days 天数
     * @return 历史列表
     */
    public List<ScrmConfigHistoryEntity> getRecentChanges(int days) {
        return historyService.getRecentChanges(days);
    }

    /**
     * 回滚配置。
     *
     * @param historyId    历史 ID
     * @param rolledBackBy 回滚人
     * @return 更新后的配置
     * @throws ScrmException 历史不存在 / 不可回滚 / 已回滚
     */
    public ScrmSystemConfigEntity rollback(Long historyId, String rolledBackBy) throws ScrmException {
        return historyService.rollback(historyId, rolledBackBy);
    }

    /**
     * 批量回滚。
     *
     * @param historyIds   历史 ID 列表
     * @param rolledBackBy 回滚人
     * @return 批量处理结果
     */
    public Map<String, Object> batchRollback(List<Long> historyIds, String rolledBackBy) {
        return historyService.batchRollback(historyIds, rolledBackBy);
    }

    /**
     * 查询回滚历史。
     *
     * @param historyId 历史 ID
     * @return 回滚历史列表
     * @throws ScrmException 历史不存在
     */
    public List<ScrmConfigHistoryEntity> getRollbackHistory(Long historyId) throws ScrmException {
        return historyService.getRollbackHistory(historyId);
    }

    /**
     * 版本对比。
     *
     * @param historyId1 历史 ID 1
     * @param historyId2 历史 ID 2
     * @return 对比结果
     * @throws ScrmException 历史不存在
     */
    public Map<String, Object> compareVersions(Long historyId1, Long historyId2) throws ScrmException {
        return historyService.compareVersions(historyId1, historyId2);
    }

    /**
     * 导出历史。
     *
     * @param configId  配置 ID（可空）
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 导出结果
     */
    public Map<String, Object> exportHistory(Long configId, LocalDateTime startTime, LocalDateTime endTime) {
        return historyService.exportHistory(configId, startTime, endTime);
    }

    // ============================================================
    // 统计分析
    // ============================================================

    /**
     * 配置统计。
     *
     * @return 配置统计 Map
     */
    public Map<String, Object> getConfigStats() {
        return statsService.getConfigStats();
    }

    /**
     * 变更统计。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 变更统计 Map
     */
    public Map<String, Object> getChangeStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getChangeStats(startTime, endTime);
    }

    /**
     * 分组统计。
     *
     * @return 分组统计 Map
     */
    public Map<String, Object> getGroupStats() {
        return statsService.getGroupStats();
    }

    /**
     * 配置覆盖率。
     *
     * @return 覆盖率 Map
     */
    public Map<String, Object> getConfigCoverage() {
        return statsService.getConfigCoverage();
    }

    /**
     * 热门配置。
     *
     * @param limit 返回数量
     * @return 热门配置列表
     */
    public List<ScrmSystemConfigEntity> getPopularConfigs(int limit) {
        return statsService.getPopularConfigs(limit);
    }

    /**
     * 长期未更新配置。
     *
     * @param days 天数
     * @return 过期配置列表
     */
    public List<ScrmSystemConfigEntity> getStaleConfigs(int days) {
        return statsService.getStaleConfigs(days);
    }

    /**
     * 配置概览。
     *
     * @return 概览 Map
     */
    public Map<String, Object> getConfigOverview() {
        return statsService.getConfigOverview();
    }

    // ============================================================
    // 值验证 / 显示值格式化 / 值解析
    // ============================================================

    /**
     * 验证配置值合法性。
     *
     * @param value     待验证值
     * @param type      配置类型
     * @param regex     验证正则（可空）
     * @param min       最小值（可空）
     * @param max       最大值（可空）
     * @param maxLength 最大长度（可空）
     * @throws ScrmException 校验失败
     */
    public void validateValue(String value, String type, String regex, Double min, Double max, Integer maxLength)
            throws ScrmException {
        valueService.validateValue(value, type, regex, min, max, maxLength);
    }

    /**
     * 格式化显示值。
     *
     * @param value 值
     * @param type  类型
     * @return 显示值
     */
    public String formatDisplayValue(String value, String type) {
        return valueService.formatDisplayValue(value, type);
    }

    /**
     * 解析值。
     *
     * @param value 值
     * @param type  类型
     * @return 解析后的 Java 对象
     */
    public Object parseValue(String value, String type) {
        return valueService.parseValue(value, type);
    }
}