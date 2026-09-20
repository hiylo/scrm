/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWelcomeMessageController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmWelcomeMessageDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmWelcomeMessageService;
import org.hiylo.scrm.service.ScrmWelcomeMessageService.TriggerResult;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.Map;

/**
 * SCRM 企微欢迎语配置控制器
 * <p>
 * 提供欢迎语规则的创建、更新、删除、查询、激活/停用, 规则匹配、欢迎语触发与触发统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/welcome-messages")
@RequiredArgsConstructor
public class ScrmWelcomeMessageController {

    /** 欢迎语服务 */
    private final ScrmWelcomeMessageService welcomeMessageService;

    /**
     * 创建欢迎语规则
     *
     * @param dto 规则参数
     * @return 创建后的规则
     */
    @RequirePermission(resource = "scrm_welcome_message", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmWelcomeMessageDto> create(@Valid @RequestBody ScrmWelcomeMessageDto dto)
            throws ScrmException {
        return OperationResponse.build(welcomeMessageService.createRule(dto));
    }

    /**
     * 更新欢迎语规则
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     */
    @RequirePermission(resource = "scrm_welcome_message", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmWelcomeMessageDto> update(@PathVariable Long id,
                                                            @RequestBody ScrmWelcomeMessageDto dto)
            throws ScrmException {
        return OperationResponse.build(welcomeMessageService.updateRule(id, dto));
    }

    /**
     * 删除欢迎语规则
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_welcome_message", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        welcomeMessageService.deleteRule(id);
        return OperationResponse.build(null);
    }

    /**
     * 查询欢迎语规则详情
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_welcome_message", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmWelcomeMessageDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(welcomeMessageService.getRule(id));
    }

    /**
     * 分页查询欢迎语规则, 支持按账号、渠道活码、平台类型、状态与关键词过滤
     *
     * @param accountId     账号 ID 过滤 (可选)
     * @param channelCodeId 渠道活码 ID 过滤 (可选)
     * @param platformType  平台类型过滤 (可选)
     * @param status        状态过滤 (可选)
     * @param keyword       关键词过滤, 匹配规则名称 (可选)
     * @param page          页码 (从 0 开始, 默认 0)
     * @param size          每页大小 (默认 20)
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_welcome_message", action = "read")
    @GetMapping({"", "/list"})
    public OperationResponse<Page<ScrmWelcomeMessageDto>> list(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long channelCodeId,
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(welcomeMessageService.listRules(
                accountId, channelCodeId, platformType, status, keyword, PageRequest.of(page, size)));
    }

    /**
     * 激活欢迎语规则 (状态置 ACTIVE)
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_welcome_message", action = "update")
    @PostMapping("/{id}/activate")
    public OperationResponse<ScrmWelcomeMessageDto> activate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(welcomeMessageService.activateRule(id));
    }

    /**
     * 停用欢迎语规则 (状态置 INACTIVE)
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_welcome_message", action = "update")
    @PostMapping("/{id}/deactivate")
    public OperationResponse<ScrmWelcomeMessageDto> deactivate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(welcomeMessageService.deactivateRule(id));
    }

    /**
     * 匹配最优欢迎语规则
     * <p>
     * 按 渠道活码 > 账号 > 全局 优先级, 同优先级取 priority 高者。
     * </p>
     *
     * @param request 匹配参数 (accountId / channelCodeId / platformType)
     * @return 匹配到的规则 (未匹配返回 data=null)
     */
    @RequirePermission(resource = "scrm_welcome_message", action = "read")
    @PostMapping("/match")
    public OperationResponse<ScrmWelcomeMessageDto> match(@RequestBody MatchRequest request) {
        return OperationResponse.build(welcomeMessageService.matchRule(
                request.getAccountId(),
                request.getChannelCodeId(),
                request.getPlatformType()));
    }

    /**
     * 触发欢迎语
     * <p>
     * 流程: 匹配规则 -> 渲染变量 -> 递增触发次数 -> 返回发送内容。
     * </p>
     *
     * @param request 触发参数 (accountId / customerId / channelCodeId)
     * @return 触发结果 (含渲染后的发送内容)
     */
    @RequirePermission(resource = "scrm_welcome_message", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/trigger")
    public OperationResponse<TriggerResult> trigger(@RequestBody TriggerRequest request) {
        return OperationResponse.build(welcomeMessageService.triggerWelcome(
                request.getAccountId(),
                request.getCustomerId(),
                request.getChannelCodeId()));
    }

    /**
     * 查询欢迎语规则触发统计
     *
     * @param id        规则 ID
     * @param startTime 起始时间 (可选, 格式 yyyy-MM-dd HH:mm:ss)
     * @param endTime   结束时间 (可选, 格式 yyyy-MM-dd HH:mm:ss)
     * @return 统计信息
     * @throws ScrmException 规则不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_welcome_message", action = "read")
    @GetMapping("/{id}/stats")
    public OperationResponse<Map<String, Object>> stats(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime)
            throws ScrmException {
        return OperationResponse.build(welcomeMessageService.getTriggerStats(id, startTime, endTime));
    }

    /**
     * 匹配规则请求参数
     * @author Hsi Chu
     */
    @lombok.Data
    public static class MatchRequest {
        /** 账号 ID (可空) */
        private Long accountId;
        /** 渠道活码 ID (可空) */
        private Long channelCodeId;
        /** 平台类型 (可空, 默认 wework) */
        private String platformType;
    }

    /**
     * 触发欢迎语请求参数
     * @author Hsi Chu
     */
    @lombok.Data
    public static class TriggerRequest {
        /** 账号 ID (可空) */
        private Long accountId;
        /** 客户 ID (可空, 用于变量替换) */
        private Long customerId;
        /** 渠道活码 ID (可空) */
        private Long channelCodeId;
    }
}
