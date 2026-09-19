/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConversationController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmConversationDto;
import org.hiylo.scrm.dto.ScrmConversationMessageDto;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmConversationMessageService;
import org.hiylo.scrm.service.ScrmConversationService;
import org.hiylo.scrm.vo.ConversationSummaryVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
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

/**
 * SCRM 会话控制器
 * <p>
 * 提供会话与消息的增删查接口,统一前缀 {@code /scrm/conversations}。
 * 媒体上传与删除由 {@link ConversationMediaController} 提供 ({@code /scrm/media}),
 * 本控制器只暴露会话消息及其媒体 URL 查询接口。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/conversations")
@RequiredArgsConstructor
public class ScrmConversationController {

    /** 会话服务 */
    private final ScrmConversationService conversationService;

    /** 会话消息服务 */
    private final ScrmConversationMessageService messageService;

    /**
     * 创建会话
     *
     * @param dto 会话数据
     * @return 创建后的会话数据
     */
    @RequirePermission(resource = "conversation", action = "write")
    @PostMapping
    public OperationResponse<ScrmConversationDto> create(@Valid @RequestBody ScrmConversationDto dto) {
        return OperationResponse.build(conversationService.createConversation(dto));
    }

    /**
     * 查询会话详情
     *
     * @param id 会话主键
     * @return 会话数据
     */
    @RequirePermission(resource = "conversation", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmConversationDto> get(@PathVariable Long id) {
        return OperationResponse.build(conversationService.getConversation(id));
    }

    /**
     * AI 总结会话
     * <p>
     * 基于会话最近消息调用 ai-server 生成会话总结,并将总结持久化到会话实体的
     * {@code lastMessageSummary} 字段。AI 不可达时返回兜底总结,不抛异常。
     * </p>
     *
     * @param id 会话主键
     * @return 会话总结结果
     */
    @RequirePermission(resource = "scrm_conversation", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60,
            message = "AI 总结请求过于频繁，请稍后重试")
    @PostMapping("/{id}/summarize")
    public OperationResponse<ConversationSummaryVo> summarize(@PathVariable Long id) {
        log.info("AI 总结会话请求: conversationId={}", id);
        return OperationResponse.build(conversationService.summarizeConversation(id));
    }

    /**
     * 按平台会话 ID 查询会话
     *
     * @param platformConversationId 平台会话 ID
     * @return 会话数据（不存在时 data 为 null）
     */
    @RequirePermission(resource = "conversation", action = "read")
    @GetMapping("/by-platform")
    public OperationResponse<ScrmConversationDto> getByPlatform(
            @RequestParam("platformConversationId") String platformConversationId) {
        return OperationResponse.build(conversationService.getConversationByPlatformId(platformConversationId));
    }

    /**
     * 按账号查询会话列表
     *
     * @param accountId 账号 ID
     * @param page      页码（从 0 开始,默认 0）
     * @param size      每页大小（默认 20）
     * @return 会话分页结果
     */
    @RequirePermission(resource = "conversation", action = "read")
    @GetMapping("/by-account/{accountId}")
    public OperationResponse<Page<ScrmConversationDto>> listByAccount(
            @PathVariable Long accountId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return OperationResponse.build(conversationService.getConversationsByAccount(accountId, page, size));
    }

    /**
     * 按客户查询会话列表
     *
     * @param customerId 客户 ID
     * @param page       页码（从 0 开始,默认 0）
     * @param size       每页大小（默认 20）
     * @return 会话分页结果
     */
    @RequirePermission(resource = "conversation", action = "read")
    @GetMapping("/by-customer/{customerId}")
    public OperationResponse<Page<ScrmConversationDto>> listByCustomer(
            @PathVariable Long customerId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return OperationResponse.build(conversationService.getConversationsByCustomer(customerId, page, size));
    }

    /**
     * 更新会话状态（关闭 / 重开）
     *
     * @param id     会话主键
     * @param status 目标状态（ACTIVE / CLOSED / PENDING）
     * @return 更新后的会话数据
     */
    @RequirePermission(resource = "conversation", action = "write")
    @PutMapping("/{id}/status")
    public OperationResponse<ScrmConversationDto> updateStatus(
            @PathVariable Long id,
            @RequestParam("status") String status) {
        return OperationResponse.build(conversationService.updateStatus(id, status));
    }

    /**
     * 重置会话未读消息数为 0（用户查看会话时调用）
     *
     * @param id 会话主键
     * @return 空响应
     */
    @RequirePermission(resource = "conversation", action = "write")
    @PutMapping("/{id}/read")
    public OperationResponse<Void> markAsRead(@PathVariable Long id) {
        conversationService.resetUnreadCount(id);
        return OperationResponse.build();
    }

    /**
     * 按时间范围查询会话列表，支持状态和关键词筛选
     *
     * @param accountId 账号 ID（保留参数）
     * @param startTime 起始时间（含,ISO 格式 yyyy-MM-dd'T'HH:mm:ss）
     * @param endTime   截止时间（含,ISO 格式 yyyy-MM-dd'T'HH:mm:ss）
     * @param status    会话状态筛选（ACTIVE / CLOSED / PENDING，可选）
     * @param keyword   关键词搜索（匹配客户昵称/账号名/消息摘要，可选）
     * @param page      页码（从 0 开始,默认 0）
     * @param size      每页大小（默认 20）
     * @return 会话分页结果
     */
    @RequirePermission(resource = "conversation", action = "read")
    @GetMapping({"", "/list"})
    public OperationResponse<Page<ScrmConversationDto>> list(
            @RequestParam(value = "accountId", required = false) Long accountId,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return OperationResponse.build(
                conversationService.listConversations(accountId, startTime, endTime, status, keyword, page, size));
    }

    /**
     * 分页查询会话消息（按发送时间倒序）
     *
     * @param id   会话主键
     * @param page 页码（从 0 开始,默认 0）
     * @param size 每页大小（默认 50）
     * @return 消息分页结果
     */
    @RequirePermission(resource = "message", action = "read")
    @GetMapping("/{id}/messages")
    public OperationResponse<Page<ScrmConversationMessageDto>> listMessages(
            @PathVariable Long id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return OperationResponse.build(messageService.getMessages(id, page, size));
    }

    /**
     * 搜索会话消息（LIKE 关键字）
     *
     * @param id      会话主键
     * @param keyword 搜索关键字
     * @return 匹配的消息列表
     */
    @RequirePermission(resource = "message", action = "read")
    @GetMapping("/{id}/messages/search")
    public OperationResponse<List<ScrmConversationMessageDto>> searchMessages(
            @PathVariable Long id,
            @RequestParam("keyword") String keyword) {
        return OperationResponse.build(messageService.searchMessages(keyword, id));
    }

    /**
     * 获取消息媒体预签名 URL
     *
     * @param messageId 消息业务 ID（messageId 字段）
     * @return 媒体预签名 URL
     */
    @RequirePermission(resource = "message", action = "read")
    @GetMapping("/messages/{messageId}/media")
    public OperationResponse<String> getMessageMediaUrl(@PathVariable String messageId) {
        return OperationResponse.build(messageService.getMessageMediaUrl(messageId));
    }

    /**
     * 手动保存消息
     *
     * @param dto 消息数据
     * @return 保存后的消息数据
     */
    @RequirePermission(resource = "message", action = "write")
    @PostMapping("/messages")
    public OperationResponse<ScrmConversationMessageDto> saveMessage(
            @Valid @RequestBody ScrmConversationMessageDto dto) {
        return OperationResponse.build(messageService.saveMessage(dto));
    }

    /**
     * 导出会话消息
     * <p>
     * 支持按时间范围过滤: 提供 startTime + endTime 时调用
     * {@link ScrmConversationMessageService#getMessagesByTimeRange},
     * 否则取首页较大 size 简化导出。format 参数当前仅支持 json, 返回消息列表。
     * </p>
     *
     * @param id        会话主键
     * @param format    导出格式 (默认 json, 当前简化实现固定返回 JSON)
     * @param startTime 起始时间 (可选, ISO 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可选, ISO 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @return 消息列表
     */
    @RequirePermission(resource = "scrm_conversation", action = "read")
    @GetMapping("/{id}/export")
    public OperationResponse<List<ScrmConversationMessageDto>> exportMessages(
            @PathVariable Long id,
            @RequestParam(value = "format", defaultValue = "json") String format,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        if (startTime != null && endTime != null) {
            return OperationResponse.build(messageService.getMessagesByTimeRange(id, startTime, endTime));
        }
        return OperationResponse.build(messageService.getMessages(id, 0, 10000).getContent());
    }
}
