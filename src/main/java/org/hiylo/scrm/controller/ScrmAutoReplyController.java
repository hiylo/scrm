/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmAutoReplyMatchDto;
import org.hiylo.scrm.dto.ScrmAutoReplyRuleDto;
import org.hiylo.scrm.dto.ScrmAutoReplyTemplateDto;
import org.hiylo.scrm.dto.ScrmAutoReplyTestDto;
import org.hiylo.scrm.entity.ScrmAutoReplyLogEntity;
import org.hiylo.scrm.entity.ScrmAutoReplyRuleEntity;
import org.hiylo.scrm.entity.ScrmAutoReplyTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmAutoReplyService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * SCRM 消息自动回复引擎控制器。
 * <p>
 * 提供消息自动回复规则的增删改查、启用/禁用、兜底设置、匹配测试、回复日志查询、
 * 模板管理与统计分析接口。规则由关键词匹配 (KEYWORD)、欢迎语 (WELCOME)、超时回复
 * (TIMEOUT) 等多种类型组成, 在收到客户消息时由 {@code ScrmAutoReplyService.matchReply}
 * 自动评估, {@code POST /match} 端点提供手动匹配入口用于规则测试。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/auto-reply")
@RequiredArgsConstructor
public class ScrmAutoReplyController {

    /** 自动回复服务 */
    private final ScrmAutoReplyService scrmAutoReplyService;

    // ============================================================
    // 规则管理 /rules
    // ============================================================

    /**
     * 创建自动回复规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmAutoReplyRuleEntity> createRule(@Valid @RequestBody ScrmAutoReplyRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAutoReplyService.createRule(dto));
    }

    /**
     * 更新自动回复规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmAutoReplyRuleEntity> updateRule(@PathVariable Long id,
                                                                  @RequestBody ScrmAutoReplyRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAutoReplyService.updateRule(id, dto));
    }

    /**
     * 删除自动回复规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在 / 仍有回复日志引用
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        scrmAutoReplyService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmAutoReplyRuleEntity> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAutoReplyService.getRule(id));
    }

    /**
     * 分页查询规则列表。
     *
     * @param ruleType  规则类型过滤（可空）
     * @param matchType 匹配方式过滤（可空）
     * @param enabled   启用状态过滤（可空）
     * @param keyword   关键字过滤（按规则名称/描述模糊匹配, 可空）
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmAutoReplyRuleEntity>> listRules(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String matchType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmAutoReplyService.listRules(
                ruleType, matchType, enabled, keyword, pageable));
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "update")
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<Void> enableRule(@PathVariable Long id) throws ScrmException {
        scrmAutoReplyService.enableRule(id);
        return OperationResponse.build();
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "update")
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<Void> disableRule(@PathVariable Long id) throws ScrmException {
        scrmAutoReplyService.disableRule(id);
        return OperationResponse.build();
    }

    /**
     * 设置兜底规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "update")
    @PostMapping("/rules/{id}/fallback")
    public OperationResponse<ScrmAutoReplyRuleEntity> setFallbackRule(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmAutoReplyService.setFallbackRule(id));
    }

    // ============================================================
    // 匹配 /match
    // ============================================================

    /**
     * 匹配回复。
     * <p>根据消息内容匹配规则, 命中后返回回复内容并模拟发送, 同时记录回复日志。</p>
     *
     * @param dto 匹配参数
     * @return 回复日志实体 (无命中时 data 为 null)
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/match")
    public OperationResponse<ScrmAutoReplyLogEntity> match(@Valid @RequestBody ScrmAutoReplyMatchDto dto) {
        return OperationResponse.build(scrmAutoReplyService.matchReply(dto));
    }

    /**
     * 批量匹配。
     *
     * @param messages 匹配参数列表
     * @return 匹配结果列表 (与入参顺序一致, 无命中时对应位置为 null)
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/match/batch")
    public OperationResponse<List<ScrmAutoReplyLogEntity>> batchMatch(
            @RequestBody List<ScrmAutoReplyMatchDto> messages) {
        return OperationResponse.build(scrmAutoReplyService.batchMatch(messages));
    }

    /**
     * 测试匹配 (不发送、不记录日志)。
     *
     * @param dto 测试参数
     * @return 匹配详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @PostMapping("/test")
    public OperationResponse<Map<String, Object>> testMatch(@Valid @RequestBody ScrmAutoReplyTestDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAutoReplyService.testMatch(dto));
    }

    // ============================================================
    // 日志 /logs
    // ============================================================

    /**
     * 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志详情
     * @throws ScrmException 日志不存在
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @GetMapping("/logs/{id}")
    public OperationResponse<ScrmAutoReplyLogEntity> getLog(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAutoReplyService.getLog(id));
    }

    /**
     * 分页查询回复日志。
     *
     * @param ruleId    规则 ID 过滤（可空）
     * @param customerId 客户 ID 过滤（可空）
     * @param channel   渠道过滤（可空）
     * @param status    发送状态过滤（可空）
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 回复日志分页结果
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @GetMapping("/logs/list")
    public OperationResponse<Page<ScrmAutoReplyLogEntity>> listLogs(
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "sentAt"));
        return OperationResponse.build(scrmAutoReplyService.listLogs(
                ruleId, customerId, channel, status, startTime, endTime, pageable));
    }

    /**
     * 按客户 ID 分页查询回复日志。
     *
     * @param customerId 客户 ID
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 回复日志分页结果
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @GetMapping("/logs/customer/{customerId}")
    public OperationResponse<Page<ScrmAutoReplyLogEntity>> getLogsByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "sentAt"));
        return OperationResponse.build(scrmAutoReplyService.getLogsByCustomer(customerId, pageable));
    }

    /**
     * 查询最近 N 条回复日志。
     *
     * @param limit 返回条数上限（默认 10）
     * @return 回复日志列表
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @GetMapping("/logs/recent")
    public OperationResponse<List<ScrmAutoReplyLogEntity>> getRecentLogs(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmAutoReplyService.getRecentLogs(limit));
    }

    // ============================================================
    // 模板管理 /templates
    // ============================================================

    /**
     * 创建回复模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_auto_reply_template", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/templates")
    public OperationResponse<ScrmAutoReplyTemplateEntity> createTemplate(
            @Valid @RequestBody ScrmAutoReplyTemplateDto dto) throws ScrmException {
        return OperationResponse.build(scrmAutoReplyService.createTemplate(dto));
    }

    /**
     * 更新回复模板。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_auto_reply_template", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/templates/{id}")
    public OperationResponse<ScrmAutoReplyTemplateEntity> updateTemplate(@PathVariable Long id,
                                                                          @RequestBody ScrmAutoReplyTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAutoReplyService.updateTemplate(id, dto));
    }

    /**
     * 删除回复模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在 / 仍有规则引用
     */
    @RequirePermission(resource = "scrm_auto_reply_template", action = "delete")
    @DeleteMapping("/templates/{id}")
    public OperationResponse<Void> deleteTemplate(@PathVariable Long id) throws ScrmException {
        scrmAutoReplyService.deleteTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_auto_reply_template", action = "read")
    @GetMapping("/templates/{id}")
    public OperationResponse<ScrmAutoReplyTemplateEntity> getTemplate(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmAutoReplyService.getTemplate(id));
    }

    /**
     * 分页查询模板列表。
     *
     * @param templateType 模板类型过滤（可空）
     * @param category     分类过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param keyword      关键字过滤（按模板名称模糊匹配, 可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 模板分页结果
     */
    @RequirePermission(resource = "scrm_auto_reply_template", action = "read")
    @GetMapping("/templates/list")
    public OperationResponse<Page<ScrmAutoReplyTemplateEntity>> listTemplates(
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmAutoReplyService.listTemplates(
                templateType, category, enabled, keyword, pageable));
    }

    /**
     * 启用模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_auto_reply_template", action = "update")
    @PostMapping("/templates/{id}/enable")
    public OperationResponse<Void> enableTemplate(@PathVariable Long id) throws ScrmException {
        scrmAutoReplyService.enableTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 禁用模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_auto_reply_template", action = "update")
    @PostMapping("/templates/{id}/disable")
    public OperationResponse<Void> disableTemplate(@PathVariable Long id) throws ScrmException {
        scrmAutoReplyService.disableTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 渲染模板 (变量替换)。
     *
     * @param id       模板 ID
     * @param variables 变量 Map
     * @return 渲染后的内容
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_auto_reply_template", action = "read")
    @PostMapping("/templates/{id}/render")
    public OperationResponse<String> renderTemplate(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, String> variables)
            throws ScrmException {
        return OperationResponse.build(scrmAutoReplyService.renderTemplate(id, variables));
    }

    /**
     * 增加模板使用次数。
     *
     * @param id 模板 ID
     * @return 空响应
     */
    @RequirePermission(resource = "scrm_auto_reply_template", action = "update")
    @PostMapping("/templates/{id}/usage")
    public OperationResponse<Void> incrementUsage(@PathVariable Long id) {
        scrmAutoReplyService.incrementUsage(id);
        return OperationResponse.build();
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 回复统计概览 (总触发次数/各类型/各渠道/发送成功率)。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getReplyStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmAutoReplyService.getReplyStats(startTime, endTime));
    }

    /**
     * 规则效果统计 (各规则触发次数/匹配率)。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 统计结果列表
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @GetMapping("/stats/rules")
    public OperationResponse<List<Map<String, Object>>> getRuleEffectiveness(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmAutoReplyService.getRuleEffectiveness(startTime, endTime));
    }

    /**
     * 响应时间统计 (平均/最大/最小/P50/P95)。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 响应时间统计
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @GetMapping("/stats/response-time")
    public OperationResponse<Map<String, Object>> getResponseTimeStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmAutoReplyService.getResponseTimeStats(startTime, endTime));
    }

    /**
     * 匹配率统计 (匹配成功 / 总消息)。
     *
     * @param startTime 起始时间 (含, 可空, 默认近 7 天)
     * @param endTime   截止时间 (含, 可空, 默认当前时间)
     * @return 匹配率统计
     */
    @RequirePermission(resource = "scrm_auto_reply_rule", action = "read")
    @GetMapping("/stats/match-rate")
    public OperationResponse<Map<String, Object>> getMatchRate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmAutoReplyService.getMatchRate(startTime, endTime));
    }
}
