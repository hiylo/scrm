/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateCenterService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmMessageTemplateCenterDto;
import org.hiylo.scrm.dto.ScrmMessageTemplateGroupDto;
import org.hiylo.scrm.dto.ScrmTemplateQueryDto;
import org.hiylo.scrm.dto.ScrmTemplateRenderDto;
import org.hiylo.scrm.dto.ScrmTemplateReviewDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
import org.hiylo.scrm.entity.ScrmMessageTemplateGroupEntity;
import org.hiylo.scrm.entity.ScrmMessageTemplateVersionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 消息模板中心服务 (门面)。
 * <p>
 * 统一消息模板管理能力, 覆盖模板分组、模板定义、版本管理、多渠道渲染 (变量替换 + 渠道适配)、
 * 审批流程与统计分析。所有写操作持久化, {@code findOrThrow} 校验资源存在性。
 * </p>
 * <p>
 * 渲染流程: 加载模板 → 校验变量 (必填/类型) → 变量替换 ({@code {{var}}} → 值, 缺失替换为空串)
 * → 渠道适配 (按渠道裁剪字段, 如 SMS 取纯文本并截断, EMAIL 取主题+HTML) → 返回渲染结果。
 * </p>
 * <p>
 * 实际能力按子域委托给 {@link ScrmMessageTemplateCenterGroupService} (分组管理)、
 * {@link ScrmMessageTemplateCenterTemplateService} (模板管理)、
 * {@link ScrmMessageTemplateCenterVersionService} (版本管理)、
 * {@link ScrmMessageTemplateCenterRenderService} (渲染)、
 * {@link ScrmMessageTemplateCenterReviewService} (审批) 与
 * {@link ScrmMessageTemplateCenterStatsService} (统计)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmMessageTemplateCenterService {

    /** 分组管理子域服务 */
    private final ScrmMessageTemplateCenterGroupService groupService;

    /** 模板管理子域服务 */
    private final ScrmMessageTemplateCenterTemplateService templateService;

    /** 版本管理子域服务 */
    private final ScrmMessageTemplateCenterVersionService versionService;

    /** 渲染子域服务 */
    private final ScrmMessageTemplateCenterRenderService renderService;

    /** 审批子域服务 */
    private final ScrmMessageTemplateCenterReviewService reviewService;

    /** 统计子域服务 */
    private final ScrmMessageTemplateCenterStatsService statsService;

    // ============================================================
    // 分组管理
    // ============================================================

    /**
     * 创建模板分组。
     *
     * @param dto 分组参数
     * @return 创建后的分组
     * @throws ScrmException 分组编码已存在 / 参数非法
     */
    public ScrmMessageTemplateGroupEntity createGroup(ScrmMessageTemplateGroupDto dto) throws ScrmException {
        return groupService.createGroup(dto);
    }

    /**
     * 更新模板分组（字段非空才覆盖）。
     *
     * @param id  分组 ID
     * @param dto 分组参数
     * @return 更新后的分组
     * @throws ScrmException 分组不存在 / 分组编码冲突 / 父分组不存在
     */
    public ScrmMessageTemplateGroupEntity updateGroup(
            Long id, ScrmMessageTemplateGroupDto dto) throws ScrmException {
        return groupService.updateGroup(id, dto);
    }

    /**
     * 删除模板分组。
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在
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
    public ScrmMessageTemplateGroupEntity getGroup(Long id) throws ScrmException {
        return groupService.getGroup(id);
    }

    /**
     * 按分组编码查询分组。
     *
     * @param code 分组编码
     * @return 分组实体
     * @throws ScrmException 分组不存在
     */
    public ScrmMessageTemplateGroupEntity getGroupByCode(String code) throws ScrmException {
        return groupService.getGroupByCode(code);
    }

    /**
     * 分页查询分组列表, 支持按分组类型、父分组、启用状态过滤。
     *
     * @param groupType 分组类型过滤（可空）
     * @param parentId  父分组 ID 过滤（可空）
     * @param enabled   启用状态过滤（可空）
     * @param pageable  分页参数
     * @return 分组分页结果
     */
    public Page<ScrmMessageTemplateGroupEntity> listGroups(String groupType, Long parentId,
                                                            Boolean enabled, Pageable pageable) {
        return groupService.listGroups(groupType, parentId, enabled, pageable);
    }

    /**
     * 启用分组。
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在
     */
    public void enableGroup(Long id) throws ScrmException {
        groupService.enableGroup(id);
    }

    /**
     * 禁用分组。
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在
     */
    public void disableGroup(Long id) throws ScrmException {
        groupService.disableGroup(id);
    }

    /**
     * 将源分组下的全部模板迁移到目标分组。
     *
     * @param sourceGroupId 源分组 ID
     * @param targetGroupId 目标分组 ID
     * @return 迁移的模板数量
     * @throws ScrmException 源/目标分组不存在
     */
    public int moveTemplatesToGroup(Long sourceGroupId, Long targetGroupId) throws ScrmException {
        return groupService.moveTemplatesToGroup(sourceGroupId, targetGroupId);
    }

    /**
     * 更新分组的模板数量统计。
     *
     * @param id 分组 ID
     * @throws ScrmException 分组不存在
     */
    public void updateGroupStats(Long id) throws ScrmException {
        groupService.updateGroupStats(id);
    }

    /**
     * 构建分组树 (含子分组递归)。
     *
     * @return 分组树列表
     */
    public List<Map<String, Object>> getGroupTree() {
        return groupService.getGroupTree();
    }

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 模板编码已存在 / 参数非法
     */
    public ScrmMessageTemplateCenterEntity createTemplate(ScrmMessageTemplateCenterDto dto) throws ScrmException {
        return templateService.createTemplate(dto);
    }

    /**
     * 更新模板（字段非空才覆盖）。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 模板编码冲突
     */
    public ScrmMessageTemplateCenterEntity updateTemplate(
            Long id, ScrmMessageTemplateCenterDto dto) throws ScrmException {
        return templateService.updateTemplate(id, dto);
    }

    /**
     * 删除模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    public void deleteTemplate(Long id) throws ScrmException {
        templateService.deleteTemplate(id);
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    public ScrmMessageTemplateCenterEntity getTemplate(Long id) throws ScrmException {
        return templateService.getTemplate(id);
    }

    /**
     * 按模板编码查询模板。
     *
     * @param code 模板编码
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    public ScrmMessageTemplateCenterEntity getTemplateByCode(String code) throws ScrmException {
        return templateService.getTemplateByCode(code);
    }

    /**
     * 分页查询模板列表, 支持按分组、类型、渠道、状态、分类、关键字组合过滤。
     *
     * @param queryDto 查询条件
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    public Page<ScrmMessageTemplateCenterEntity> listTemplates(ScrmTemplateQueryDto queryDto, Pageable pageable) {
        return templateService.listTemplates(queryDto, pageable);
    }

    /**
     * 发布模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    public ScrmMessageTemplateCenterEntity publishTemplate(Long id) throws ScrmException {
        return templateService.publishTemplate(id);
    }

    /**
     * 归档模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    public ScrmMessageTemplateCenterEntity archiveTemplate(Long id) throws ScrmException {
        return templateService.archiveTemplate(id);
    }

    /**
     * 复制模板 (生成新编码的草稿副本)。
     *
     * @param id      源模板 ID
     * @param newCode 新模板编码
     * @return 复制后的模板
     * @throws ScrmException 源模板不存在 / 新编码已存在
     */
    public ScrmMessageTemplateCenterEntity duplicateTemplate(Long id, String newCode) throws ScrmException {
        return templateService.duplicateTemplate(id, newCode);
    }

    /**
     * 将模板设为标准模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    public ScrmMessageTemplateCenterEntity setStandard(Long id) throws ScrmException {
        return templateService.setStandard(id);
    }

    /**
     * 按分组分页查询模板。
     *
     * @param groupId  分组 ID
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    public Page<ScrmMessageTemplateCenterEntity> getTemplatesByGroup(Long groupId, Pageable pageable) {
        return templateService.getTemplatesByGroup(groupId, pageable);
    }

    /**
     * 按渠道分页查询模板 (channels 字段包含该渠道)。
     *
     * @param channel  渠道
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    public Page<ScrmMessageTemplateCenterEntity> getTemplatesByChannel(String channel, Pageable pageable) {
        return templateService.getTemplatesByChannel(channel, pageable);
    }

    /**
     * 按场景分页查询模板 (applicableScenarios 字段包含该场景)。
     *
     * @param scenario 场景
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    public Page<ScrmMessageTemplateCenterEntity> getTemplatesByScenario(String scenario, Pageable pageable) {
        return templateService.getTemplatesByScenario(scenario, pageable);
    }

    /**
     * 更新模板使用统计: 使用次数 +1, 刷新最后使用时间, 按成功标志刷新成功率 (滑动平均)。
     *
     * @param id      模板 ID
     * @param success 是否成功 (null 表示不更新成功率)
     * @throws ScrmException 模板不存在
     */
    public void incrementUsage(Long id, Boolean success) throws ScrmException {
        templateService.incrementUsage(id, success);
    }

    // ============================================================
    // 版本管理
    // ============================================================

    /**
     * 创建新版本: 将当前模板内容固化为版本快照, 标记为 ACTIVE (最新保存状态), 归档其他版本,
     * 模板 versionNumber 与 currentVersionId 同步更新。
     *
     * @param templateId 模板 ID
     * @param changeLog  变更说明（可空）
     * @return 创建后的版本
     * @throws ScrmException 模板不存在
     */
    public ScrmMessageTemplateVersionEntity createVersion(Long templateId, String changeLog) throws ScrmException {
        return versionService.createVersion(templateId, changeLog);
    }

    /**
     * 查询版本详情。
     *
     * @param id 版本 ID
     * @return 版本实体
     * @throws ScrmException 版本不存在
     */
    public ScrmMessageTemplateVersionEntity getVersion(Long id) throws ScrmException {
        return versionService.getVersion(id);
    }

    /**
     * 分页查询模板版本列表 (按版本号降序)。
     *
     * @param templateId 模板 ID
     * @param pageable   分页参数
     * @return 版本分页结果
     */
    public Page<ScrmMessageTemplateVersionEntity> listVersions(Long templateId, Pageable pageable) {
        return versionService.listVersions(templateId, pageable);
    }

    /**
     * 激活版本: 将指定版本内容回填到模板, 标记为 ACTIVE, 归档其他版本, 同步模板版本号与指针。
     *
     * @param versionId 版本 ID
     * @return 激活后的版本
     * @throws ScrmException 版本不存在
     */
    public ScrmMessageTemplateVersionEntity activateVersion(Long versionId) throws ScrmException {
        return versionService.activateVersion(versionId);
    }

    /**
     * 回滚到指定版本: 与激活版本等价, 将模板内容恢复为该版本内容。
     *
     * @param templateId 模板 ID
     * @param versionId  目标版本 ID
     * @return 回滚后的版本
     * @throws ScrmException 模板/版本不存在 / 版本不属于该模板
     */
    public ScrmMessageTemplateVersionEntity rollbackToVersion(
            Long templateId, Long versionId) throws ScrmException {
        return versionService.rollbackToVersion(templateId, versionId);
    }

    /**
     * 对比两个版本差异 (subject/content/plainContent/htmlContent/variables 字段级对比)。
     *
     * @param versionId1 版本 ID 1
     * @param versionId2 版本 ID 2
     * @return 对比结果
     * @throws ScrmException 版本不存在
     */
    public Map<String, Object> compareVersions(Long versionId1, Long versionId2) throws ScrmException {
        return versionService.compareVersions(versionId1, versionId2);
    }

    // ============================================================
    // 渲染
    // ============================================================

    /**
     * 渲染模板: 变量替换 → 渠道适配 → 返回渲染结果。
     *
     * @param renderDto 渲染参数
     * @return 渲染结果 (含 channel 与渠道适配后的字段)
     * @throws ScrmException 模板不存在 / 渠道不适用 / 变量校验失败
     */
    public Map<String, Object> render(ScrmTemplateRenderDto renderDto) throws ScrmException {
        return renderService.render(renderDto);
    }

    /**
     * 按渠道渲染模板: 确定渠道 → 校验变量 → 替换变量 → 渠道适配。
     *
     * @param templateId 模板 ID
     * @param channel    目标渠道（可空, 空则取模板首个适用渠道）
     * @param variables  变量值映射
     * @return 渲染结果
     * @throws ScrmException 模板不存在 / 渠道不适用 / 变量校验失败
     */
    public Map<String, Object> renderForChannel(Long templateId, String channel, Map<String, Object> variables)
            throws ScrmException {
        return renderService.renderForChannel(templateId, channel, variables);
    }

    /**
     * 验证变量: 检查必填项是否提供、类型是否匹配。
     *
     * @param templateId 模板 ID
     * @param variables  变量值映射
     * @return 校验结果 {valid, missingRequired, typeMismatches}
     * @throws ScrmException 模板不存在
     */
    public Map<String, Object> validateVariables(
            Long templateId, Map<String, Object> variables) throws ScrmException {
        return renderService.validateVariables(templateId, variables);
    }

    /**
     * 预览模板: 渲染全部字段 (不做渠道适配, 不校验必填), 用于编辑器实时预览。
     *
     * @param templateId 模板 ID
     * @param variables  变量值映射
     * @return 预览结果
     * @throws ScrmException 模板不存在
     */
    public Map<String, Object> preview(Long templateId, Map<String, Object> variables) throws ScrmException {
        return renderService.preview(templateId, variables);
    }

    /**
     * 从内容中提取变量名列表 (去重保留顺序)。
     *
     * @param content 模板内容
     * @return 变量名列表
     */
    public List<String> extractVariables(String content) {
        return renderService.extractVariables(content);
    }

    /**
     * 批量渲染多个模板。
     *
     * @param renderDtos 渲染参数列表
     * @return 渲染结果列表 (与入参顺序一致, 单条失败时该条置为 error)
     */
    public List<Map<String, Object>> batchRender(List<ScrmTemplateRenderDto> renderDtos) {
        return renderService.batchRender(renderDtos);
    }

    /**
     * 渲染纯文本: 将 content 中的 {{key}} 替换为变量值, 缺失替换为空串。
     *
     * @param content   模板内容
     * @param variables 变量值映射
     * @return 渲染后的纯文本
     */
    public String renderPlainText(String content, Map<String, Object> variables) {
        return renderService.renderPlainText(content, variables);
    }

    /**
     * 渲染 HTML: 将 htmlContent 中的 {{key}} 替换为变量值, 缺失替换为空串。
     *
     * @param htmlContent HTML 内容
     * @param variables   变量值映射
     * @return 渲染后的 HTML
     */
    public String renderHtml(String htmlContent, Map<String, Object> variables) {
        return renderService.renderHtml(htmlContent, variables);
    }

    // ============================================================
    // 审批
    // ============================================================

    /**
     * 提交审核: 将模板置为待审核状态。
     *
     * @param templateId 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    public ScrmMessageTemplateCenterEntity submitForReview(Long templateId) throws ScrmException {
        return reviewService.submitForReview(templateId);
    }

    /**
     * 审核模板: 通过则状态 APPROVED, 驳回则状态 REJECTED。
     *
     * @param reviewDto 审核参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 审核动作非法
     */
    public ScrmMessageTemplateCenterEntity review(ScrmTemplateReviewDto reviewDto) throws ScrmException {
        return reviewService.review(reviewDto);
    }

    /**
     * 批量审核模板。
     *
     * @param templateIds 模板 ID 列表
     * @param action      审核动作 (APPROVE/REJECT)
     * @param comment     审核意见
     * @return 审核结果列表 (每条含 templateId/success/error)
     */
    public List<Map<String, Object>> batchReview(List<Long> templateIds, String action, String comment) {
        return reviewService.batchReview(templateIds, action, comment);
    }

    /**
     * 分页查询待审核模板列表。
     *
     * @param pageable 分页参数
     * @return 待审核模板分页结果
     */
    public Page<ScrmMessageTemplateCenterEntity> getPendingReviews(Pageable pageable) {
        return reviewService.getPendingReviews(pageable);
    }

    /**
     * 查询模板审核历史: 返回该模板下已审批的版本记录 (approvedBy 非空), 按审批时间降序。
     *
     * @param templateId 模板 ID
     * @param pageable   分页参数
     * @return 审核历史分页结果
     */
    public Page<ScrmMessageTemplateVersionEntity> getReviewHistory(Long templateId, Pageable pageable) {
        return reviewService.getReviewHistory(templateId, pageable);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 模板统计: 总数、各类型、各渠道、平均使用率与成功率。
     *
     * @param startTime 起始时间过滤（可空, 按 createTime）
     * @param endTime   结束时间过滤（可空）
     * @return 统计结果
     */
    public Map<String, Object> getTemplateStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getTemplateStats(startTime, endTime);
    }

    /**
     * 分组统计: 各分组下模板数量。
     *
     * @return 分组统计列表
     */
    public List<Map<String, Object>> getGroupStats() {
        return statsService.getGroupStats();
    }

    /**
     * 热门模板: 按使用次数降序取前 N。
     *
     * @param limit 数量
     * @return 模板列表
     */
    public List<ScrmMessageTemplateCenterEntity> getTopTemplates(int limit) {
        return statsService.getTopTemplates(limit);
    }

    /**
     * 模板效果分析: 使用次数、成功率、回复率、最后使用时间等。
     *
     * @param templateId 模板 ID
     * @return 效果分析结果
     * @throws ScrmException 模板不存在
     */
    public Map<String, Object> getTemplateEffectiveness(Long templateId) throws ScrmException {
        return statsService.getTemplateEffectiveness(templateId);
    }

    /**
     * 渠道效果: 各渠道的模板数、累计使用次数、平均成功率。
     *
     * @param startTime 起始时间过滤（可空）
     * @param endTime   结束时间过滤（可空）
     * @return 渠道效果 Map
     */
    public Map<String, Object> getChannelEffectiveness(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getChannelEffectiveness(startTime, endTime);
    }
}