/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChatArchiveController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmArchiveRuleDto;
import org.hiylo.scrm.dto.ScrmChatArchiveDto;
import org.hiylo.scrm.entity.ScrmArchiveRuleEntity;
import org.hiylo.scrm.entity.ScrmChatArchiveEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmChatArchiveService;
import org.hiylo.scrm.vo.ChatArchiveStatsVo;
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
import java.util.Map;

/**
 * SCRM 会话存档控制器
 * <p>
 * 提供消息归档、归档查询、统计与归档规则管理接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/chat-archives")
@RequiredArgsConstructor
public class ScrmChatArchiveController {

    /** 会话存档服务 */
    private final ScrmChatArchiveService chatArchiveService;

    /**
     * 归档消息
     *
     * @param dto 归档参数
     * @return 归档后的记录
     */
    @RequirePermission(resource = "scrm_chat_archive", action = "create")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmChatArchiveEntity> archive(@Valid @RequestBody ScrmChatArchiveDto dto)
            throws ScrmException {
        return OperationResponse.build(chatArchiveService.archiveMessage(dto));
    }

    /**
     * 查询归档消息
     *
     * @param id 归档 ID
     * @return 归档记录
     * @throws ScrmException 归档记录不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_chat_archive", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmChatArchiveEntity> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(chatArchiveService.getArchive(id));
    }

    /**
     * 分页查询归档消息, 支持按平台类型、方向、质量标记、账号过滤
     *
     * @param platformType 平台类型过滤 (可选)
     * @param direction    消息方向过滤 (可选)
     * @param qualityFlag  质量标记过滤 (可选)
     * @param accountId    账号 ID 过滤 (可选)
     * @param page         页码 (默认 0)
     * @param size         每页大小 (默认 20)
     * @return 归档消息分页
     */
    @RequirePermission(resource = "scrm_chat_archive", action = "read")
    @GetMapping({"", "/list"})
    public OperationResponse<Page<ScrmChatArchiveEntity>> list(
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String qualityFlag,
            @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(
                chatArchiveService.listArchives(platformType, direction, qualityFlag, accountId, page, size));
    }

    /**
     * 按账号 ID 分页查询归档消息
     *
     * @param accountId 账号 ID
     * @param page      页码
     * @param size      每页大小
     * @return 归档消息分页
     */
    @RequirePermission(resource = "scrm_chat_archive", action = "read")
    @GetMapping("/by-account/{accountId}")
    public OperationResponse<Page<ScrmChatArchiveEntity>> byAccount(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(chatArchiveService.getArchivesByAccount(accountId, page, size));
    }

    /**
     * 按客户 ID 分页查询归档消息
     *
     * @param customerId 客户 ID
     * @param page       页码
     * @param size       每页大小
     * @return 归档消息分页
     */
    @RequirePermission(resource = "scrm_chat_archive", action = "read")
    @GetMapping("/by-customer/{customerId}")
    public OperationResponse<Page<ScrmChatArchiveEntity>> byCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(chatArchiveService.getArchivesByCustomer(customerId, page, size));
    }

    /**
     * 按会话 ID 分页查询归档消息
     *
     * @param conversationId 会话 ID
     * @param page            页码
     * @param size            每页大小
     * @return 归档消息分页
     */
    @RequirePermission(resource = "scrm_chat_archive", action = "read")
    @GetMapping("/by-conversation/{conversationId}")
    public OperationResponse<Page<ScrmChatArchiveEntity>> byConversation(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(chatArchiveService.getArchivesByConversation(conversationId, page, size));
    }

    /**
     * 获取存档统计
     *
     * @return 统计 VO
     */
    @RequirePermission(resource = "scrm_chat_archive", action = "read")
    @GetMapping("/stats")
    public OperationResponse<ChatArchiveStatsVo> stats() {
        return OperationResponse.build(chatArchiveService.getStats());
    }

    // ==================== 归档规则管理 ====================

    /**
     * 创建归档规则
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_archive_rule", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmArchiveRuleEntity> createRule(
            @Valid @RequestBody ScrmArchiveRuleDto dto) throws ScrmException {
        return OperationResponse.build(chatArchiveService.createRule(dto));
    }

    /**
     * 更新归档规则
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_archive_rule", action = "update")
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmArchiveRuleEntity> updateRule(
            @PathVariable Long id, @RequestBody ScrmArchiveRuleDto dto) throws ScrmException {
        return OperationResponse.build(chatArchiveService.updateRule(id, dto));
    }

    /**
     * 删除归档规则
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_archive_rule", action = "delete")
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        chatArchiveService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询归档规则列表
     *
     * @param enabledOnly 是否只返回启用的规则 (默认 false)
     * @return 规则列表
     */
    @RequirePermission(resource = "scrm_archive_rule", action = "read")
    @GetMapping("/rules")
    public OperationResponse<List<ScrmArchiveRuleEntity>> listRules(
            @RequestParam(defaultValue = "false") boolean enabledOnly) {
        return OperationResponse.build(chatArchiveService.listRules(enabledOnly));
    }

    /**
     * 切换归档规则启用状态
     *
     * @param id   规则 ID
     * @param body 包含 enabled 字段的请求体
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_archive_rule", action = "update")
    @PutMapping("/rules/{id}/toggle")
    public OperationResponse<ScrmArchiveRuleEntity> toggleRule(
            @PathVariable Long id, @RequestBody Map<String, Boolean> body) throws ScrmException {
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("enabled"));
        return OperationResponse.build(chatArchiveService.toggleRule(id, enabled));
    }
}
