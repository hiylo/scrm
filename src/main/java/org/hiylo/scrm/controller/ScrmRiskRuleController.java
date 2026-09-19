/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRiskRuleController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.callback.ConversationEventCallbackDto;
import org.hiylo.scrm.dto.ScrmRiskRuleDto;
import org.hiylo.scrm.entity.ScrmRiskRuleEntity;
import org.hiylo.scrm.entity.ScrmRiskSignalEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmRiskRuleService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCRM 风险规则控制器。
 * <p>
 * 提供风险规则的增删改查、启用/禁用与手动评估接口。规则由 SpEL 表达式定义,
 * 在会话事件回调时由 {@code ScrmRiskRuleService.evaluateMessage} 自动评估,
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
@RequestMapping("/scrm/risk-rules")
@RequiredArgsConstructor
public class ScrmRiskRuleController {

    /** 风险规则服务 */
    private final ScrmRiskRuleService scrmRiskRuleService;

    /**
     * 创建风险规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException ruleCode 已存在
     */
    @RequirePermission(resource = "scrm_risk_rule", action = "create")
    @PostMapping
    public OperationResponse<ScrmRiskRuleEntity> create(@Valid @RequestBody ScrmRiskRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmRiskRuleService.createRule(dto));
    }

    /**
     * 更新风险规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / ruleCode 冲突
     */
    @RequirePermission(resource = "scrm_risk_rule", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmRiskRuleEntity> update(@PathVariable Long id,
                                                         @RequestBody ScrmRiskRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmRiskRuleService.updateRule(id, dto));
    }

    /**
     * 删除风险规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk_rule", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        scrmRiskRuleService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk_rule", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmRiskRuleEntity> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmRiskRuleService.getRule(id));
    }

    /**
     * 分页查询规则列表。
     *
     * @param enabled 启用状态过滤（可空）
     * @param keyword 关键字过滤（按规则名称/代码模糊匹配, 可空）
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_risk_rule", action = "read")
    @GetMapping
    public OperationResponse<Page<ScrmRiskRuleEntity>> list(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(scrmRiskRuleService.listRules(enabled, keyword, page, size));
    }

    /**
     * 启用风险规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk_rule", action = "update")
    @PostMapping("/{id}/enable")
    public OperationResponse<Void> enable(@PathVariable Long id) throws ScrmException {
        scrmRiskRuleService.enableRule(id);
        return OperationResponse.build();
    }

    /**
     * 禁用风险规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_risk_rule", action = "update")
    @PostMapping("/{id}/disable")
    public OperationResponse<Void> disable(@PathVariable Long id) throws ScrmException {
        scrmRiskRuleService.disableRule(id);
        return OperationResponse.build();
    }

    /**
     * 手动评估一条会话消息（规则测试用）。
     * <p>同步返回命中的风控信号列表, 用于规则配置后的效果验证。</p>
     *
     * @param dto 会话事件回调 DTO
     * @return 命中的风控信号列表
     */
    @RequirePermission(resource = "scrm_risk_rule", action = "read")
    @PostMapping("/evaluate")
    public OperationResponse<List<ScrmRiskSignalEntity>> evaluate(@RequestBody ConversationEventCallbackDto dto) {
        return OperationResponse.build(scrmRiskRuleService.evaluateMessage(dto));
    }
}
