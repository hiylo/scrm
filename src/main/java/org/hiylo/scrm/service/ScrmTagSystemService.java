/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTagSystemService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmBatchTagDto;
import org.hiylo.scrm.dto.ScrmTagDto;
import org.hiylo.scrm.dto.ScrmTagGroupDto;
import org.hiylo.scrm.dto.ScrmTagRuleDto;
import org.hiylo.scrm.dto.ScrmTagRuleTestDto;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmTagEntity;
import org.hiylo.scrm.entity.ScrmTagGroupEntity;
import org.hiylo.scrm.entity.ScrmTagRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * SCRM 客户标签体系管理服务（门面）。
 * <p>
 * 承载客户标签体系的核心能力, 实际实现已按子域拆分到兄弟服务: 分组管理
 * {@link ScrmTagSystemGroupService}、标签管理与客户标签 {@link ScrmTagSystemTagService}、
 * 规则管理 {@link ScrmTagSystemRuleService}、统计 {@link ScrmTagSystemStatsService}。
 * 本门面只做请求委托, 对外保持全部 public 方法签名不变。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
public class ScrmTagSystemService {

    /** 标签分组管理服务 */
    private final ScrmTagSystemGroupService groupService;

    /** 标签管理与客户标签管理服务 */
    private final ScrmTagSystemTagService tagService;

    /** 标签规则管理服务 */
    private final ScrmTagSystemRuleService ruleService;

    /** 标签体系统计分析服务 */
    private final ScrmTagSystemStatsService statsService;

    // ============================================================
    // 分组管理 Group
    // ============================================================

    /**
     * 创建标签分组。
     * <p>校验分组编码在唯一后写入归属账号 ID 持久化, isSystem / enabled / sortOrder
     * 缺省时填默认值。</p>
     *
     * @param dto 分组参数
     * @return 创建后的分组
     * @throws ScrmException 参数非法 / 分组编码重复
     */
    public ScrmTagGroupEntity createGroup(ScrmTagGroupDto dto) throws ScrmException {
        return groupService.createGroup(dto);
    }

    /**
     * 更新标签分组（字段非空才覆盖, groupCode 不允许修改）。
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     * @throws ScrmException 分组不存在 / 参数非法 / 系统内置分组不可修改编码
     */
    public ScrmTagGroupEntity updateGroup(Long id, ScrmTagGroupDto dto) throws ScrmException {
        return groupService.updateGroup(id, dto);
    }

    /**
     * 删除标签分组。
     * <p>系统内置分组 (is_system=true) 不可删除。删除前检查分组下是否仍有标签, 若有则拒绝。
     * 业务上应先迁移或删除分组下全部标签再删除分组。</p>
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在 / 系统内置 / 仍有标签引用
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
    public ScrmTagGroupEntity getGroup(Long id) throws ScrmException {
        return groupService.getGroup(id);
    }

    /**
     * 分页查询分组, 支持按启用状态与关键字过滤。
     *
     * @param enabled 启用状态过滤（可空）
     * @param keyword 关键字过滤（按 groupName / description / groupCode 模糊匹配, 可空）
     * @param pageable 分页参数
     * @return 分组分页结果
     */
    public Page<ScrmTagGroupEntity> listGroups(Boolean enabled, String keyword, Pageable pageable) {
        return groupService.listGroups(enabled, keyword, pageable);
    }

    /**
     * 启用分组。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    public ScrmTagGroupEntity enableGroup(Long id) throws ScrmException {
        return groupService.enableGroup(id);
    }

    /**
     * 禁用分组。
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    public ScrmTagGroupEntity disableGroup(Long id) throws ScrmException {
        return groupService.disableGroup(id);
    }

    /**
     * 更新分组统计 (标签数 + 覆盖客户数)。
     * <p>标签数取分组下标签总数; 覆盖客户数取分组下所有标签的客户关联去重后数量。</p>
     *
     * @param id 分组 ID
     * @return 更新后的分组
     * @throws ScrmException 分组不存在
     */
    public ScrmTagGroupEntity updateGroupStats(Long id) throws ScrmException {
        return groupService.updateGroupStats(id);
    }

    // ============================================================
    // 标签管理 Tag
    // ============================================================

    /**
     * 创建标签。
     * <p>校验标签编码在唯一、所属分组存在后写入归属账号 ID 持久化。AUTO 类型标签需要
     * 后续创建关联规则后由 Service 反向写入 ruleId。</p>
     *
     * @param dto 标签参数
     * @return 创建后的标签
     * @throws ScrmException 参数非法 / 标签编码重复 / 所属分组不存在
     */
    public ScrmTagEntity createTag(ScrmTagDto dto) throws ScrmException {
        return tagService.createTag(dto);
    }

    /**
     * 更新标签（字段非空才覆盖, tagCode / tagType 不允许修改）。
     *
     * @param id  标签 ID
     * @param dto 标签参数
     * @return 更新后的标签
     * @throws ScrmException 标签不存在 / 参数非法 / 系统内置标签不可修改编码
     */
    public ScrmTagEntity updateTag(Long id, ScrmTagDto dto) throws ScrmException {
        return tagService.updateTag(id, dto);
    }

    /**
     * 删除标签。
     * <p>系统内置标签 (is_system=true) 不可删除。删除前清理客户标签关联与关联规则。
     * AUTO 类型标签若仍有关联的 ACTIVE 规则, 拒绝删除 (需先禁用规则)。</p>
     *
     * @param id 标签 ID
     * @throws ScrmException 标签不存在 / 系统内置 / 仍有活跃规则
     */
    public void deleteTag(Long id) throws ScrmException {
        tagService.deleteTag(id);
    }

    /**
     * 查询标签详情。
     *
     * @param id 标签 ID
     * @return 标签实体
     * @throws ScrmException 标签不存在
     */
    public ScrmTagEntity getTag(Long id) throws ScrmException {
        return tagService.getTag(id);
    }

    /**
     * 按编码查询标签。
     *
     * @param code 标签编码
     * @return 标签实体
     * @throws ScrmException 标签不存在
     */
    public ScrmTagEntity getTagByCode(String code) throws ScrmException {
        return tagService.getTagByCode(code);
    }

    /**
     * 分页查询标签, 支持按分组、类型、启用状态与关键字过滤。
     *
     * @param groupId 分组 ID 过滤（可空）
     * @param tagType 标签类型过滤（可空）
     * @param enabled 启用状态过滤（可空）
     * @param keyword 关键字过滤（按 tagName / description / tagCode 模糊匹配, 可空）
     * @param pageable 分页参数
     * @return 标签分页结果
     */
    public Page<ScrmTagEntity> listTags(Long groupId, String tagType, Boolean enabled,
                                        String keyword, Pageable pageable) {
        return tagService.listTags(groupId, tagType, enabled, keyword, pageable);
    }

    /**
     * 启用标签。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    public ScrmTagEntity enableTag(Long id) throws ScrmException {
        return tagService.enableTag(id);
    }

    /**
     * 禁用标签。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    public ScrmTagEntity disableTag(Long id) throws ScrmException {
        return tagService.disableTag(id);
    }

    /**
     * 合并标签: 将源标签的全部客户关联迁移到目标标签, 然后删除源标签。
     * <p>
     * 迁移策略: 源标签下所有客户关联的 tagId 更新为目标标签 ID。若客户已存在目标标签关联,
     * 该源关联直接被覆盖 (依赖唯一约束 customer_id+tag_id)。迁移完成后删除源标签
     * 及其关联规则 (非 ACTIVE)。
     * </p>
     *
     * @param sourceId 源标签 ID (将被删除)
     * @param targetId 目标标签 ID (将接收客户关联)
     * @return 迁移的客户关联数
     * @throws ScrmException 标签不存在 / 同一标签 / 源标签为系统内置
     */
    public int mergeTags(Long sourceId, Long targetId) throws ScrmException {
        return tagService.mergeTags(sourceId, targetId);
    }

    /**
     * 更新标签的被打标客户数。
     *
     * @param id 标签 ID
     * @return 更新后的标签
     * @throws ScrmException 标签不存在
     */
    public ScrmTagEntity updateTagCustomerCount(Long id) throws ScrmException {
        return tagService.updateTagCustomerCount(id);
    }

    // ============================================================
    // 规则管理 Rule
    // ============================================================

    /**
     * 创建自动标签规则。
     * <p>校验 conditions 为合法 JSON、关联标签存在且为 AUTO 类型后写入归属账号 ID 持久化。
     * 创建后将规则的 ruleId 反向写入关联标签。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 关联标签不存在或非 AUTO 类型 / conditions 非合法 JSON
     */
    public ScrmTagRuleEntity createRule(ScrmTagRuleDto dto) throws ScrmException {
        return ruleService.createRule(dto);
    }

    /**
     * 更新规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 关联标签不存在或非 AUTO 类型
     */
    public ScrmTagRuleEntity updateRule(Long id, ScrmTagRuleDto dto) throws ScrmException {
        return ruleService.updateRule(id, dto);
    }

    /**
     * 删除规则。
     * <p>删除后清理关联标签的 ruleId 反向引用。</p>
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
    public ScrmTagRuleEntity getRule(Long id) throws ScrmException {
        return ruleService.getRule(id);
    }

    /**
     * 分页查询规则, 支持按标签 ID、状态与关键字过滤。
     *
     * @param tagId   标签 ID 过滤（可空）
     * @param status  状态过滤（可空）
     * @param keyword 关键字过滤（按 ruleName / description 模糊匹配, 可空）
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    public Page<ScrmTagRuleEntity> listRules(Long tagId, String status,
                                             String keyword, Pageable pageable) {
        return ruleService.listRules(tagId, status, keyword, pageable);
    }

    /**
     * 启用规则 (状态置为 ACTIVE)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmTagRuleEntity enableRule(Long id) throws ScrmException {
        return ruleService.enableRule(id);
    }

    /**
     * 禁用规则 (状态置为 INACTIVE)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmTagRuleEntity disableRule(Long id) throws ScrmException {
        return ruleService.disableRule(id);
    }

    /**
     * 执行自动打标规则: 遍历当前账号全部客户 → 评估条件 → 命中则打标。
     * <p>模拟实现: 加载全部客户逐条评估, 命中的客户写入客户标签关联 (tag_source=AUTO,
     * is_auto=true)。执行完成后增量更新规则的 matchedCount 与 lastExecutedAt。</p>
     *
     * @param id 规则 ID
     * @return 命中的客户数
     * @throws ScrmException 规则不存在 / 关联标签不存在
     */
    public int executeRule(Long id) throws ScrmException {
        return ruleService.executeRule(id);
    }

    /**
     * 批量执行所有活跃规则。
     *
     * @return 各规则执行结果: [{ruleId, ruleName, matchedCount}]
     */
    public List<Map<String, Object>> batchExecuteRules() {
        return ruleService.batchExecuteRules();
    }

    /**
     * 测试规则匹配 (不实际打标)。
     * <p>对入参客户列表逐个评估规则条件, 返回命中的客户 ID 列表与匹配详情。</p>
     *
     * @param testDto 测试参数 (ruleId + customerIds)
     * @return 测试结果: {matchedCustomerIds, details}
     * @throws ScrmException 规则不存在
     */
    public Map<String, Object> testRule(ScrmTagRuleTestDto testDto) throws ScrmException {
        return ruleService.testRule(testDto);
    }

    /**
     * 评估条件是否匹配 (模拟实现, 基于客户属性)。
     * <p>
     * 解析 conditions JSON 数组, 按 conditionType (ALL/ANY/NONE) 评估。条件 field 引用
     * 客户属性, 支持字段: customer_name (昵称) / lifecycle / platform_type / order_count /
     * total_amount / registration_days / last_interaction_days。
     * </p>
     *
     * @param customerId     客户 ID
     * @param conditionsJson 条件 JSON 数组字符串
     * @param conditionType  条件组合类型: ALL / ANY / NONE
     * @return 条件是否匹配
     * @throws ScrmException 客户不存在
     */
    public boolean evaluateCondition(Long customerId, String conditionsJson,
                                     String conditionType) throws ScrmException {
        return ruleService.evaluateCondition(customerId, conditionsJson, conditionType);
    }

    // ============================================================
    // 客户标签 CustomerTag
    // ============================================================

    /**
     * 为客户打标。
     * <p>校验标签存在且启用。同一标签同一客户默认覆盖更新 tag_value 与打标人信息,
     * 重复打标不增加新记录 (依赖唯一约束)。AUTO 来源打标会设置 is_auto=true。</p>
     *
     * @param customerId 客户 ID
     * @param tagId      标签 ID
     * @param tagValue   标签值 (可空)
     * @param source     标签来源 (可空, 默认 MANUAL)
     * @param assignedBy 打标人 (可空)
     * @return 创建或更新后的客户标签关联
     * @throws ScrmException 标签不存在或已禁用
     */
    public ScrmTagCustomerEntity assignTag(Long customerId, Long tagId, String tagValue,
                                           String source, String assignedBy) throws ScrmException {
        return tagService.assignTag(customerId, tagId, tagValue, source, assignedBy);
    }

    /**
     * 批量打标/去标。
     * <p>对入参客户 ID × 标签 ID 笛卡尔积执行 ADD 或 REMOVE 动作, 返回成功操作数。</p>
     *
     * @param batchDto 批量参数
     * @return 成功操作数
     * @throws ScrmException 标签不存在或已禁用
     */
    public int batchAssign(ScrmBatchTagDto batchDto) throws ScrmException {
        return tagService.batchAssign(batchDto);
    }

    /**
     * 移除客户标签。
     * <p>AUTO 来源标签 (is_auto=true) 不允许手动去标, 需先禁用关联规则。</p>
     *
     * @param customerId 客户 ID
     * @param tagId      标签 ID
     * @throws ScrmException 关联不存在 / 自动标签不可手动去标
     */
    public void removeTag(Long customerId, Long tagId) throws ScrmException {
        tagService.removeTag(customerId, tagId);
    }

    /**
     * 查询客户的所有标签关联。
     *
     * @param customerId 客户 ID
     * @return 标签关联列表
     */
    public List<ScrmTagCustomerEntity> getCustomerTags(Long customerId) {
        return tagService.getCustomerTags(customerId);
    }

    /**
     * 分页查询标签下的客户。
     *
     * @param tagId    标签 ID
     * @param pageable 分页参数
     * @return 客户关联分页结果
     */
    public Page<ScrmTagCustomerEntity> getTagCustomers(Long tagId, Pageable pageable) {
        return tagService.getTagCustomers(tagId, pageable);
    }

    /**
     * 检查客户是否具有指定标签。
     *
     * @param customerId 客户 ID
     * @param tagId      标签 ID
     * @return 是否具有标签
     */
    public boolean checkTag(Long customerId, Long tagId) {
        return tagService.checkTag(customerId, tagId);
    }

    /**
     * 批量获取客户标签关联。
     *
     * @param customerIds 客户 ID 列表
     * @return 客户标签关联列表 (按 customerId 分组)
     */
    public Map<Long, List<ScrmTagCustomerEntity>> getTagsByCustomerIds(List<Long> customerIds) {
        return tagService.getTagsByCustomerIds(customerIds);
    }

    // ============================================================
    // 统计 Stats
    // ============================================================

    /**
     * 标签统计概览: 总标签数 / 各类型分布 / 各分组分布 / 覆盖率。
     *
     * @return 统计结果
     */
    public Map<String, Object> getTagStats() {
        return statsService.getTagStats();
    }

    /**
     * 标签云: 按 customer_count 倒序返回热门标签。
     *
     * @param limit 返回数量 (默认 100)
     * @return 标签云列表 (tagName / tagCode / customerCount / color)
     */
    public List<Map<String, Object>> getTagCloud(int limit) {
        return statsService.getTagCloud(limit);
    }

    /**
     * 客户标签数。
     *
     * @param customerId 客户 ID
     * @return 标签数
     */
    public long getCustomerTagCount(Long customerId) {
        return statsService.getCustomerTagCount(customerId);
    }

    /**
     * 分组覆盖率: 各分组的标签数与覆盖客户数。
     *
     * @return 分组覆盖率列表
     */
    public List<Map<String, Object>> getGroupCoverage() {
        return statsService.getGroupCoverage();
    }

    /**
     * 规则统计: 各规则的执行次数与匹配率。
     *
     * @return 规则统计列表
     */
    public List<Map<String, Object>> getRuleStats() {
        return statsService.getRuleStats();
    }
}