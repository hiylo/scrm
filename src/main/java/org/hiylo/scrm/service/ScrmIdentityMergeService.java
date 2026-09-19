/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmCustomerIdentityDto;
import org.hiylo.scrm.dto.ScrmIdentityMergeActionDto;
import org.hiylo.scrm.dto.ScrmIdentityMergeExecuteDto;
import org.hiylo.scrm.dto.ScrmIdentityMergeRuleDto;
import org.hiylo.scrm.dto.ScrmIdentityMergeTaskDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerIdentityEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeHistoryEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeRuleEntity;
import org.hiylo.scrm.entity.ScrmIdentityMergeTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户身份合并服务 (门面)。
 * <p>
 * 作为客户身份合并模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmIdentityMergeIdentityService} (身份标识管理)、{@link ScrmIdentityMergeTaskService} (合并任务与执行)、
 * {@link ScrmIdentityMergeRuleService} (规则执行与重复检测) 与
 * {@link ScrmIdentityMergeHistoryService} (历史与统计)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmIdentityMergeService {

    /** 身份标识管理子域服务 */
    private final ScrmIdentityMergeIdentityService identityService;

    /** 合并任务与执行子域服务 */
    private final ScrmIdentityMergeTaskService taskService;

    /** 规则执行与重复检测子域服务 */
    private final ScrmIdentityMergeRuleService ruleService;

    /** 历史与统计子域服务 */
    private final ScrmIdentityMergeHistoryService historyService;

    // ============================================================
    // 身份标识管理
    // ============================================================

    /**
     * 添加客户身份标识。
     *
     * @param dto 身份参数
     * @return 创建后的身份
     * @throws ScrmException 参数非法 / 身份已存在
     */
    public ScrmCustomerIdentityEntity addIdentity(ScrmCustomerIdentityDto dto) throws ScrmException {
        return identityService.addIdentity(dto);
    }

    /**
     * 移除客户身份标识 (软删除, 置为非活跃)。
     *
     * @param id 身份 ID
     * @throws ScrmException 身份不存在
     */
    public void removeIdentity(Long id) throws ScrmException {
        identityService.removeIdentity(id);
    }

    /**
     * 查询身份详情。
     *
     * @param id 身份 ID
     * @return 身份实体
     * @throws ScrmException 身份不存在
     */
    public ScrmCustomerIdentityEntity getIdentity(Long id) throws ScrmException {
        return identityService.getIdentity(id);
    }

    /**
     * 分页查询客户身份, 支持按客户 / 身份类型 / 平台过滤。
     *
     * @param customerId   客户 ID 过滤 (可空)
     * @param identityType 身份类型过滤 (可空)
     * @param platform     平台过滤 (可空)
     * @param pageable     分页参数
     * @return 身份分页结果
     */
    public Page<ScrmCustomerIdentityEntity> listIdentities(Long customerId, String identityType,
                                                            String platform, Pageable pageable) {
        return identityService.listIdentities(customerId, identityType, platform, pageable);
    }

    /**
     * 获取客户主身份。
     *
     * @param customerId 客户 ID
     * @return 主身份实体
     * @throws ScrmException 主身份不存在
     */
    public ScrmCustomerIdentityEntity getPrimaryIdentity(Long customerId) throws ScrmException {
        return identityService.getPrimaryIdentity(customerId);
    }

    /**
     * 设置客户主身份 (取消原主身份, 设置新主身份)。
     *
     * @param customerId    客户 ID
     * @param identityType  身份类型
     * @param identityValue 身份值
     * @return 更新后的主身份
     * @throws ScrmException 身份不存在
     */
    public ScrmCustomerIdentityEntity setPrimaryIdentity(Long customerId, String identityType,
                                                          String identityValue) throws ScrmException {
        return identityService.setPrimaryIdentity(customerId, identityType, identityValue);
    }

    /**
     * 验证身份 (标记已验证, 记录验证时间)。
     *
     * @param id 身份 ID
     * @return 更新后的身份
     * @throws ScrmException 身份不存在
     */
    public ScrmCustomerIdentityEntity verifyIdentity(Long id) throws ScrmException {
        return identityService.verifyIdentity(id);
    }

    /**
     * 按身份查找客户。
     *
     * @param identityType  身份类型
     * @param identityValue 身份值
     * @return 客户实体
     * @throws ScrmException 身份不存在 / 客户不存在
     */
    public ScrmCustomerEntity findCustomerByIdentity(String identityType, String identityValue)
            throws ScrmException {
        return identityService.findCustomerByIdentity(identityType, identityValue);
    }

    /**
     * 分页查询全部身份 (跨客户)。
     *
     * @param identityType 身份类型过滤 (可空)
     * @param platform     平台过滤 (可空)
     * @param keyword      关键词过滤 (可空)
     * @param pageable     分页参数
     * @return 身份分页结果
     */
    public Page<ScrmCustomerIdentityEntity> listAllIdentities(String identityType, String platform,
                                                               String keyword, Pageable pageable) {
        return identityService.listAllIdentities(identityType, platform, keyword, pageable);
    }

    // ============================================================
    // 合并任务管理
    // ============================================================

    /**
     * 创建合并任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 客户不存在 / 源目标相同
     */
    public ScrmIdentityMergeTaskEntity createMergeTask(ScrmIdentityMergeTaskDto dto) throws ScrmException {
        return taskService.createMergeTask(dto);
    }

    /**
     * 查询合并任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    public ScrmIdentityMergeTaskEntity getMergeTask(Long id) throws ScrmException {
        return taskService.getMergeTask(id);
    }

    /**
     * 分页查询合并任务, 支持按状态 / 类型 / 时间区间过滤。
     *
     * @param status    状态过滤 (可空)
     * @param mergeType 合并类型过滤 (可空)
     * @param startTime 创建时间起始 (可空)
     * @param endTime   创建时间截止 (可空)
     * @param pageable  分页参数
     * @return 任务分页结果
     */
    public Page<ScrmIdentityMergeTaskEntity> listMergeTasks(String status, String mergeType,
                                                              LocalDateTime startTime, LocalDateTime endTime,
                                                              Pageable pageable) {
        return taskService.listMergeTasks(status, mergeType, startTime, endTime, pageable);
    }

    /**
     * 审核合并任务 (APPROVE 通过 / REJECT 拒绝 / CANCEL 取消)。
     *
     * @param actionDto 审核动作
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    public ScrmIdentityMergeTaskEntity reviewMergeTask(ScrmIdentityMergeActionDto actionDto) throws ScrmException {
        return taskService.reviewMergeTask(actionDto);
    }

    /**
     * 取消合并任务。
     *
     * @param id     任务 ID
     * @param reason 取消原因
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    public ScrmIdentityMergeTaskEntity cancelMergeTask(Long id, String reason) throws ScrmException {
        return taskService.cancelMergeTask(id, reason);
    }

    /**
     * 执行合并 (身份迁移 → 交易迁移 → 字段合并 → 标签合并 → 历史记录 → 停用源客户)。
     *
     * @param executeDto 执行参数
     * @return 合并历史
     * @throws ScrmException 任务不存在 / 状态非法 / 执行失败
     */
    public ScrmIdentityMergeHistoryEntity executeMerge(
            ScrmIdentityMergeExecuteDto executeDto) throws ScrmException {
        return taskService.executeMerge(executeDto);
    }

    /**
     * 重试合并任务。
     *
     * @param id 任务 ID
     * @return 合并历史
     * @throws ScrmException 任务不存在 / 状态非法
     */
    public ScrmIdentityMergeHistoryEntity retryMerge(Long id) throws ScrmException {
        return taskService.retryMerge(id);
    }

    /**
     * 获取合并建议 (检测可能的重复客户)。
     *
     * @param customerId 客户 ID
     * @return 合并建议列表
     * @throws ScrmException 客户不存在
     */
    public List<Map<String, Object>> getMergeSuggestions(Long customerId) throws ScrmException {
        return taskService.getMergeSuggestions(customerId);
    }

    /**
     * 计算两个客户的匹配分数。
     *
     * @param customer1 客户 1
     * @param customer2 客户 2
     * @return 匹配分数 (0~1)
     */
    public double calculateMatchScore(ScrmCustomerEntity customer1, ScrmCustomerEntity customer2) {
        return taskService.calculateMatchScore(customer1, customer2);
    }

    // ============================================================
    // 合并规则管理
    // ============================================================

    /**
     * 创建合并规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    public ScrmIdentityMergeRuleEntity createRule(ScrmIdentityMergeRuleDto dto) throws ScrmException {
        return ruleService.createRule(dto);
    }

    /**
     * 更新合并规则 (字段非空才覆盖)。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    public ScrmIdentityMergeRuleEntity updateRule(Long id, ScrmIdentityMergeRuleDto dto) throws ScrmException {
        return ruleService.updateRule(id, dto);
    }

    /**
     * 删除合并规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    public void deleteRule(Long id) throws ScrmException {
        ruleService.deleteRule(id);
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    public ScrmIdentityMergeRuleEntity getRule(Long id) throws ScrmException {
        return ruleService.getRule(id);
    }

    /**
     * 分页查询规则, 支持按启用状态 / 关键词过滤。
     *
     * @param enabled  启用状态过滤 (可空)
     * @param keyword  关键词过滤 (可空)
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    public Page<ScrmIdentityMergeRuleEntity> listRules(Boolean enabled, String keyword, Pageable pageable) {
        return ruleService.listRules(enabled, keyword, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmIdentityMergeRuleEntity enableRule(Long id) throws ScrmException {
        return ruleService.enableRule(id);
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmIdentityMergeRuleEntity disableRule(Long id) throws ScrmException {
        return ruleService.disableRule(id);
    }

    /**
     * 执行规则 (扫描所有客户 → 匹配 → 创建合并任务)。
     *
     * @param id 规则 ID
     * @return 创建的合并任务列表
     * @throws ScrmException 规则不存在
     */
    public List<ScrmIdentityMergeTaskEntity> executeRule(Long id) throws ScrmException {
        return ruleService.executeRule(id);
    }

    /**
     * 批量执行所有启用的规则。
     *
     * @return 各规则创建的任务数统计
     */
    public List<Map<String, Object>> batchExecuteRules() {
        return ruleService.batchExecuteRules();
    }

    // ============================================================
    // 重复检测
    // ============================================================

    /**
     * 检测指定客户的重复。
     *
     * @param customerId 客户 ID
     * @return 重复列表
     * @throws ScrmException 客户不存在
     */
    public List<Map<String, Object>> detectDuplicates(Long customerId) throws ScrmException {
        return ruleService.detectDuplicates(customerId);
    }

    /**
     * 批量检测重复 (扫描全部客户)。
     *
     * @return 重复检测结果列表
     */
    public List<Map<String, Object>> batchDetectDuplicates() {
        return ruleService.batchDetectDuplicates();
    }

    /**
     * 查找潜在重复客户 (按关键词模糊匹配客户名称)。
     *
     * @param keyword 关键词
     * @param limit   返回数量上限
     * @return 潜在重复客户列表
     */
    public List<Map<String, Object>> findPotentialDuplicates(String keyword, int limit) {
        return ruleService.findPotentialDuplicates(keyword, limit);
    }

    /**
     * 重复报告 (按匹配类型统计)。
     *
     * @return 报告 Map
     */
    public Map<String, Object> getDuplicateReport() {
        return ruleService.getDuplicateReport();
    }

    // ============================================================
    // 合并历史
    // ============================================================

    /**
     * 查询合并历史详情。
     *
     * @param id 历史 ID
     * @return 历史实体
     * @throws ScrmException 历史不存在
     */
    public ScrmIdentityMergeHistoryEntity getHistory(Long id) throws ScrmException {
        return historyService.getHistory(id);
    }

    /**
     * 按任务 ID 查询合并历史。
     *
     * @param taskId 任务 ID
     * @return 历史实体
     * @throws ScrmException 历史不存在
     */
    public ScrmIdentityMergeHistoryEntity getHistoryByTask(Long taskId) throws ScrmException {
        return historyService.getHistoryByTask(taskId);
    }

    /**
     * 分页查询合并历史。
     *
     * @param customerId 客户 ID 过滤 (可空)
     * @param startTime  合并时间起始 (可空)
     * @param endTime    合并时间截止 (可空)
     * @param pageable   分页参数
     * @return 历史分页结果
     */
    public Page<ScrmIdentityMergeHistoryEntity> listHistory(Long customerId, LocalDateTime startTime,
                                                             LocalDateTime endTime, Pageable pageable) {
        return historyService.listHistory(customerId, startTime, endTime, pageable);
    }

    /**
     * 回滚合并。
     *
     * @param historyId 历史 ID
     * @param reason    回滚原因
     * @return 更新后的历史
     * @throws ScrmException 历史不存在 / 不可回滚
     */
    public ScrmIdentityMergeHistoryEntity rollbackMerge(Long historyId, String reason) throws ScrmException {
        return historyService.rollbackMerge(historyId, reason);
    }

    /**
     * 合并影响分析。
     *
     * @param taskId 任务 ID
     * @return 影响分析 Map
     * @throws ScrmException 任务不存在
     */
    public Map<String, Object> getMergeImpact(Long taskId) throws ScrmException {
        return historyService.getMergeImpact(taskId);
    }

    /**
     * 合并统计 (任务数 / 完成率 / 平均合并时间)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getMergeStats(LocalDateTime startTime, LocalDateTime endTime) {
        return historyService.getMergeStats(startTime, endTime);
    }

    /**
     * 重复统计。
     *
     * @return 统计结果 Map
     */
    public Map<String, Object> getDuplicateStats() {
        return historyService.getDuplicateStats();
    }

    /**
     * 规则统计。
     *
     * @return 统计结果 Map
     */
    public Map<String, Object> getRuleStats() {
        return historyService.getRuleStats();
    }

    /**
     * 身份统计。
     *
     * @return 统计结果 Map
     */
    public Map<String, Object> getIdentityStats() {
        return historyService.getIdentityStats();
    }

}