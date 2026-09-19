/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmAutoReplyLogDto;
import org.hiylo.scrm.dto.ScrmAutoReplyMatchDto;
import org.hiylo.scrm.dto.ScrmAutoReplyRuleDto;
import org.hiylo.scrm.dto.ScrmAutoReplyTemplateDto;
import org.hiylo.scrm.dto.ScrmAutoReplyTestDto;
import org.hiylo.scrm.entity.ScrmAutoReplyLogEntity;
import org.hiylo.scrm.entity.ScrmAutoReplyRuleEntity;
import org.hiylo.scrm.entity.ScrmAutoReplyTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 消息自动回复引擎服务。
 * <p>
 * 承载消息自动回复规则的增删改查、匹配引擎、回复日志、模板管理与统计分析能力,
 * 按子域委托给 {@link ScrmAutoReplyRuleService} / {@link ScrmAutoReplyMatchService} /
 * {@link ScrmAutoReplyTemplateService} / {@link ScrmAutoReplyLogService} /
 * {@link ScrmAutoReplyStatsService} 实现, 本类为门面, 保持公开 API 契约不变。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmAutoReplyService {

    /** 规则管理服务 */
    private final ScrmAutoReplyRuleService ruleService;

    /** 匹配引擎服务 */
    private final ScrmAutoReplyMatchService matchService;

    /** 话术模板服务 */
    private final ScrmAutoReplyTemplateService templateService;

    /** 回复日志服务 */
    private final ScrmAutoReplyLogService logService;

    /** 统计服务 */
    private final ScrmAutoReplyStatsService statsService;

    /**
     * 创建自动回复规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    public ScrmAutoReplyRuleEntity createRule(ScrmAutoReplyRuleDto dto) throws ScrmException {
        return ruleService.createRule(dto);
    }

    /**
     * 更新自动回复规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    public ScrmAutoReplyRuleEntity updateRule(Long id, ScrmAutoReplyRuleDto dto) throws ScrmException {
        return ruleService.updateRule(id, dto);
    }

    /**
     * 删除自动回复规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在 / 仍有回复日志引用
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
    public ScrmAutoReplyRuleEntity getRule(Long id) throws ScrmException {
        return ruleService.getRule(id);
    }

    /**
     * 分页查询规则, 支持按规则类型、匹配方式、启用状态与关键字过滤。
     *
     * @param ruleType  规则类型过滤（可空）
     * @param matchType 匹配方式过滤（可空）
     * @param enabled   启用状态过滤（可空）
     * @param keyword   关键字过滤（按规则名称/描述模糊匹配, 可空）
     * @param pageable  分页参数
     * @return 规则分页结果
     */
    public Page<ScrmAutoReplyRuleEntity> listRules(String ruleType, String matchType, Boolean enabled,
                                                    String keyword, Pageable pageable) {
        return ruleService.listRules(ruleType, matchType, enabled, keyword, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    public void enableRule(Long id) throws ScrmException {
        ruleService.enableRule(id);
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    public void disableRule(Long id) throws ScrmException {
        ruleService.disableRule(id);
    }

    /**
     * 设置兜底规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmAutoReplyRuleEntity setFallbackRule(Long id) throws ScrmException {
        return ruleService.setFallbackRule(id);
    }

    /**
     * 增加规则触发次数（直接 SQL 更新, 避免乐观锁冲突）。
     *
     * @param id 规则 ID
     */
    public void incrementTriggerCount(Long id) {
        ruleService.incrementTriggerCount(id);
    }

    /**
     * 检查冷却时间是否已过期。
     *
     * @param ruleId    规则 ID
     * @param customerId 客户 ID
     * @return true 表示冷却已过期可触发, false 表示冷却中
     */
    public boolean checkCooldown(Long ruleId, Long customerId) {
        return matchService.checkCooldown(ruleId, customerId);
    }

    /**
     * 检查当前时间是否在规则的工作时间内。
     *
     * @param rule 规则实体
     * @return true 表示在工作时间内
     */
    public boolean checkWorkTime(ScrmAutoReplyRuleEntity rule) {
        return matchService.checkWorkTime(rule);
    }

    /**
     * 匹配回复。
     * <p>
     * 加载当前账号全部启用规则 (按 priority ASC), 逐条评估 matchType 与 matchScope,
     * 命中后渲染回复内容并模拟发送, 同时记录回复日志。无规则命中时返回兜底规则回复。
     * </p>
     *
     * @param matchDto 匹配参数 (客户 ID、消息、渠道、账号 ID)
     * @return 回复日志实体 (包含回复内容与发送状态), 无任何回复时返回 null
     */
    public ScrmAutoReplyLogEntity matchReply(ScrmAutoReplyMatchDto matchDto) {
        return matchService.matchReply(matchDto);
    }

    /**
     * 按规则类型匹配回复。
     * <p>仅评估指定 ruleType 的规则, 用于 WELCOME/TIMEOUT/OFFLINE 等场景的定向匹配。</p>
     *
     * @param message  消息内容
     * @param ruleType 规则类型
     * @param channel  渠道（可空, 用于过滤适用渠道）
     * @return 命中的规则列表
     */
    public List<ScrmAutoReplyRuleEntity> matchByType(String message, String ruleType, String channel) {
        return matchService.matchByType(message, ruleType, channel);
    }

    /**
     * 模糊匹配 (Jaccard 相似度)。
     *
     * @param message  消息内容
     * @param keywords 关键词列表 (逗号分隔)
     * @return 最大相似度 (0-1), 0 表示无匹配
     */
    public double fuzzyMatch(String message, String keywords) {
        return matchService.fuzzyMatch(message, keywords);
    }

    /**
     * 测试匹配 (不发送、不记录日志)。
     *
     * @param testDto 测试参数 (规则 ID + 测试消息)
     * @return 匹配详情
     * @throws ScrmException 规则不存在
     */
    public Map<String, Object> testMatch(ScrmAutoReplyTestDto testDto) throws ScrmException {
        return matchService.testMatch(testDto);
    }

    /**
     * 批量匹配。
     * <p>对多条消息逐一调用 matchReply, 返回每条消息的匹配结果。</p>
     *
     * @param messages 匹配参数列表
     * @return 匹配结果列表 (与入参顺序一致, 无命中时对应位置为 null)
     */
    public List<ScrmAutoReplyLogEntity> batchMatch(List<ScrmAutoReplyMatchDto> messages) {
        return matchService.batchMatch(messages);
    }

    /**
     * 记录回复日志。
     *
     * @param dto 日志参数
     * @return 持久化后的日志实体
     */
    public ScrmAutoReplyLogEntity recordLog(ScrmAutoReplyLogDto dto) {
        return logService.recordLog(dto);
    }

    /**
     * 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志实体
     * @throws ScrmException 日志不存在
     */
    public ScrmAutoReplyLogEntity getLog(Long id) throws ScrmException {
        return logService.getLog(id);
    }

    /**
     * 分页查询回复日志, 支持按规则 ID、客户 ID、渠道、状态与时间范围过滤。
     *
     * @param ruleId    规则 ID 过滤（可空）
     * @param customerId 客户 ID 过滤（可空）
     * @param channel   渠道过滤（可空）
     * @param status    发送状态过滤（可空）
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @param pageable  分页参数
     * @return 回复日志分页结果
     */
    public Page<ScrmAutoReplyLogEntity> listLogs(Long ruleId, Long customerId, String channel,
                                                  String status, LocalDateTime startTime, LocalDateTime endTime,
                                                  Pageable pageable) {
        return logService.listLogs(ruleId, customerId, channel, status, startTime, endTime, pageable);
    }

    /**
     * 按客户 ID 分页查询回复日志。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 回复日志分页结果
     */
    public Page<ScrmAutoReplyLogEntity> getLogsByCustomer(Long customerId, Pageable pageable) {
        return logService.getLogsByCustomer(customerId, pageable);
    }

    /**
     * 查询最近 N 条回复日志。
     *
     * @param limit 返回条数上限
     * @return 回复日志列表
     */
    public List<ScrmAutoReplyLogEntity> getRecentLogs(int limit) {
        return logService.getRecentLogs(limit);
    }

    /**
     * 创建回复模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法
     */
    public ScrmAutoReplyTemplateEntity createTemplate(ScrmAutoReplyTemplateDto dto) throws ScrmException {
        return templateService.createTemplate(dto);
    }

    /**
     * 更新回复模板（字段非空才覆盖）。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法
     */
    public ScrmAutoReplyTemplateEntity updateTemplate(Long id, ScrmAutoReplyTemplateDto dto)
            throws ScrmException {
        return templateService.updateTemplate(id, dto);
    }

    /**
     * 删除回复模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在 / 仍有规则引用
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
    public ScrmAutoReplyTemplateEntity getTemplate(Long id) throws ScrmException {
        return templateService.getTemplate(id);
    }

    /**
     * 分页查询模板, 支持按模板类型、分类、启用状态与关键字过滤。
     *
     * @param templateType 模板类型过滤（可空）
     * @param category     分类过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param keyword      关键字过滤（按模板名称模糊匹配, 可空）
     * @param pageable     分页参数
     * @return 模板分页结果
     */
    public Page<ScrmAutoReplyTemplateEntity> listTemplates(String templateType, String category,
                                                            Boolean enabled, String keyword, Pageable pageable) {
        return templateService.listTemplates(templateType, category, enabled, keyword, pageable);
    }

    /**
     * 启用模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    public void enableTemplate(Long id) throws ScrmException {
        templateService.enableTemplate(id);
    }

    /**
     * 禁用模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    public void disableTemplate(Long id) throws ScrmException {
        templateService.disableTemplate(id);
    }

    /**
     * 渲染模板 (变量替换)。
     * <p>支持变量: {customerName}/{nickname}/{time}。</p>
     *
     * @param id       模板 ID
     * @param variables 变量 Map
     * @return 渲染后的内容
     * @throws ScrmException 模板不存在
     */
    public String renderTemplate(Long id, Map<String, String> variables) throws ScrmException {
        return templateService.renderTemplate(id, variables);
    }

    /**
     * 增加模板使用次数（直接 SQL 更新, 避免乐观锁冲突）。
     *
     * @param id 模板 ID
     */
    public void incrementUsage(Long id) {
        templateService.incrementUsage(id);
    }

    /**
     * 回复统计。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 统计结果
     */
    public Map<String, Object> getReplyStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getReplyStats(startTime, endTime);
    }

    /**
     * 规则效果统计。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 统计结果列表
     */
    public List<Map<String, Object>> getRuleEffectiveness(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getRuleEffectiveness(startTime, endTime);
    }

    /**
     * 响应时间统计。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 响应时间统计
     */
    public Map<String, Object> getResponseTimeStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getResponseTimeStats(startTime, endTime);
    }

    /**
     * 匹配率统计 (匹配成功 / 总消息)。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 匹配率统计
     */
    public Map<String, Object> getMatchRate(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getMatchRate(startTime, endTime);
    }
}