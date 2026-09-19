/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoTagController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmAutoTagRuleDto;
import org.hiylo.scrm.dto.ScrmTagRuleEvaluateDto;
import org.hiylo.scrm.entity.ScrmAutoTagRuleEntity;
import org.hiylo.scrm.entity.ScrmAutoTagRuleLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmAutoTagService;
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
 * SCRM 客户自动标签规则引擎控制器。
 * <p>
 * 提供自动标签规则的增删改查、启用/禁用、手动评估、执行日志查询与统计接口。
 * 规则由条件 (conditions JSON) 与动作 (actionType + actionParams) 两部分组成,
 * 在客户事件触发时由 {@code ScrmAutoTagService.evaluateAll} 自动评估,
 * {@code POST /evaluate} 端点提供手动评估入口用于规则测试。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/auto-tags")
@RequiredArgsConstructor
public class ScrmAutoTagController {

    /** 自动标签规则服务 */
    private final ScrmAutoTagService scrmAutoTagService;

    // ============================================================
    // 规则管理 /rules
    // ============================================================

    /**
     * 创建自动标签规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / conditions 或 actionParams 非合法 JSON
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmAutoTagRuleEntity> createRule(@Valid @RequestBody ScrmAutoTagRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAutoTagService.createRule(dto));
    }

    /**
     * 更新自动标签规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmAutoTagRuleEntity> updateRule(@PathVariable Long id,
                                                                @RequestBody ScrmAutoTagRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAutoTagService.updateRule(id, dto));
    }

    /**
     * 删除自动标签规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在 / 仍有执行日志引用
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        scrmAutoTagService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmAutoTagRuleEntity> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAutoTagService.getRule(id));
    }

    /**
     * 分页查询规则列表。
     *
     * @param triggerEvent 触发事件过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param keyword      关键字过滤（按规则名称/描述模糊匹配, 可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmAutoTagRuleEntity>> listRules(
            @RequestParam(required = false) String triggerEvent,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmAutoTagService.listRules(triggerEvent, enabled, keyword, pageable));
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "update")
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<Void> enableRule(@PathVariable Long id) throws ScrmException {
        scrmAutoTagService.enableRule(id);
        return OperationResponse.build();
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "update")
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<Void> disableRule(@PathVariable Long id) throws ScrmException {
        scrmAutoTagService.disableRule(id);
        return OperationResponse.build();
    }

    // ============================================================
    // 规则评估 /evaluate
    // ============================================================

    /**
     * 手动评估客户 (传入 customerId + triggerEvent + customerContext)。
     * <p>同步返回命中的规则执行日志列表, 用于规则配置后的效果验证。</p>
     *
     * @param dto 评估参数
     * @return 命中的规则执行日志列表
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "read")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/evaluate")
    public OperationResponse<List<ScrmAutoTagRuleLogEntity>> evaluate(@Valid @RequestBody ScrmTagRuleEvaluateDto dto) {
        return OperationResponse.build(scrmAutoTagService.evaluateAll(
                dto.getCustomerId(), dto.getTriggerEvent(), dto.getCustomerContext()));
    }

    // ============================================================
    // 日志与统计 /logs /stats
    // ============================================================

    /**
     * 查询指定规则的执行日志。
     *
     * @param id        规则 ID
     * @param customerId 客户 ID 过滤（可空）
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 执行日志分页结果
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "read")
    @GetMapping("/rules/{id}/logs")
    public OperationResponse<Page<ScrmAutoTagRuleLogEntity>> getRuleLogs(
            @PathVariable Long id,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "executedAt"));
        return OperationResponse.build(scrmAutoTagService.getRuleLogs(id, customerId, startTime, endTime, pageable));
    }

    /**
     * 查询执行日志 (跨规则, 按当前账号隔离)。
     *
     * @param ruleId    规则 ID 过滤（可空）
     * @param customerId 客户 ID 过滤（可空）
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 执行日志分页结果
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "read")
    @GetMapping("/logs")
    public OperationResponse<Page<ScrmAutoTagRuleLogEntity>> getLogs(
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "executedAt"));
        return OperationResponse.build(scrmAutoTagService.getRuleLogs(ruleId,
                customerId, startTime, endTime, pageable));
    }

    /**
     * 查询规则统计 (各规则匹配次数/成功率)。
     * <p>startTime / endTime 缺省时默认统计近 7 天。</p>
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 统计结果列表
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "read")
    @GetMapping("/stats")
    public OperationResponse<List<Map<String, Object>>> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmAutoTagService.getRuleStats(startTime, endTime));
    }

    // ============================================================
    // 元数据 /fields /operators
    // ============================================================

    /**
     * 可用条件字段列表。
     *
     * @return 字段列表 (每项包含 name 与 description)
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "read")
    @GetMapping("/fields")
    public OperationResponse<List<Map<String, String>>> listAvailableFields() {
        return OperationResponse.build(scrmAutoTagService.listAvailableFields());
    }

    /**
     * 可用操作符列表。
     *
     * @return 操作符列表 (每项包含 name 与 description)
     */
    @RequirePermission(resource = "scrm_auto_tag_rule", action = "read")
    @GetMapping("/operators")
    public OperationResponse<List<Map<String, String>>> listAvailableOperators() {
        return OperationResponse.build(scrmAutoTagService.listAvailableOperators());
    }
}
