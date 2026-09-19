/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiAssistantController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmAiAssistantConfigDto;
import org.hiylo.scrm.dto.ScrmAiChatDto;
import org.hiylo.scrm.dto.ScrmAiIntentDto;
import org.hiylo.scrm.dto.ScrmAiKnowledgeBaseDto;
import org.hiylo.scrm.dto.ScrmAiKnowledgeDocumentDto;
import org.hiylo.scrm.dto.ScrmAiReplyFeedbackDto;
import org.hiylo.scrm.entity.ScrmAiAssistantConfigEntity;
import org.hiylo.scrm.entity.ScrmAiConversationEntity;
import org.hiylo.scrm.entity.ScrmAiIntentEntity;
import org.hiylo.scrm.entity.ScrmAiKnowledgeBaseEntity;
import org.hiylo.scrm.entity.ScrmAiKnowledgeDocumentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmAiAssistantService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * SCRM AI 智能对话助手控制器。
 * <p>
 * 提供 AI 助手配置、意图管理、对话生成、情感分析、知识库问答与对话统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 * <p><b>对话 / 批量对话端点已启用 (意图识别 + 情感分析 + 推荐回复为真实逻辑, AI 回复生成待对接 LLM API)。</b></p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/ai-assistant")
@RequiredArgsConstructor
public class ScrmAiAssistantController {

    /** AI 助手服务 */
    private final ScrmAiAssistantService scrmAiAssistantService;

    // ============================================================
    // 配置管理 /configs
    // ============================================================

    /**
     * 创建 AI 助手配置。
     *
     * @param dto 配置参数
     * @return 创建后的配置
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/configs")
    public OperationResponse<ScrmAiAssistantConfigEntity> createConfig(@Valid @RequestBody ScrmAiAssistantConfigDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.createConfig(dto));
    }

    /**
     * 更新 AI 助手配置。
     *
     * @param id  配置 ID
     * @param dto 配置参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/configs/{id}")
    public OperationResponse<ScrmAiAssistantConfigEntity> updateConfig(@PathVariable Long id,
                                                                        @RequestBody ScrmAiAssistantConfigDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.updateConfig(id, dto));
    }

    /**
     * 删除 AI 助手配置。
     *
     * @param id 配置 ID
     * @return 空响应
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "delete")
    @DeleteMapping("/configs/{id}")
    public OperationResponse<Void> deleteConfig(@PathVariable Long id) throws ScrmException {
        scrmAiAssistantService.deleteConfig(id);
        return OperationResponse.build();
    }

    /**
     * 查询配置详情。
     *
     * @param id 配置 ID
     * @return 配置详情
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/configs/{id}")
    public OperationResponse<ScrmAiAssistantConfigEntity> getConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.getConfig(id));
    }

    /**
     * 分页查询配置列表。
     *
     * @param provider 服务提供方过滤（可空）: OPENAI / AZURE / LOCAL / ZHIPU / QWEN
     * @param enabled  启用状态过滤（可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 配置分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/configs/list")
    public OperationResponse<Page<ScrmAiAssistantConfigEntity>> listConfigs(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmAiAssistantService.listConfigs(provider, enabled, pageable));
    }

    /**
     * 设置为默认配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "update")
    @PostMapping("/configs/{id}/default")
    public OperationResponse<ScrmAiAssistantConfigEntity> setDefaultConfig(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.setDefaultConfig(id));
    }

    /**
     * 启用配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "update")
    @PostMapping("/configs/{id}/enable")
    public OperationResponse<ScrmAiAssistantConfigEntity> enableConfig(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.enableConfig(id));
    }

    /**
     * 禁用配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "update")
    @PostMapping("/configs/{id}/disable")
    public OperationResponse<ScrmAiAssistantConfigEntity> disableConfig(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.disableConfig(id));
    }

    // ============================================================
    // 意图管理 /intents
    // ============================================================

    /**
     * 创建 AI 意图。
     *
     * @param dto 意图参数
     * @return 创建后的意图
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/intents")
    public OperationResponse<ScrmAiIntentEntity> createIntent(@Valid @RequestBody ScrmAiIntentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.createIntent(dto));
    }

    /**
     * 更新 AI 意图。
     *
     * @param id  意图 ID
     * @param dto 意图参数
     * @return 更新后的意图
     * @throws ScrmException 意图不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/intents/{id}")
    public OperationResponse<ScrmAiIntentEntity> updateIntent(@PathVariable Long id,
                                                                @RequestBody ScrmAiIntentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.updateIntent(id, dto));
    }

    /**
     * 删除 AI 意图。
     *
     * @param id 意图 ID
     * @return 空响应
     * @throws ScrmException 意图不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "delete")
    @DeleteMapping("/intents/{id}")
    public OperationResponse<Void> deleteIntent(@PathVariable Long id) throws ScrmException {
        scrmAiAssistantService.deleteIntent(id);
        return OperationResponse.build();
    }

    /**
     * 查询意图详情。
     *
     * @param id 意图 ID
     * @return 意图详情
     * @throws ScrmException 意图不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/intents/{id}")
    public OperationResponse<ScrmAiIntentEntity> getIntent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.getIntent(id));
    }

    /**
     * 分页查询意图列表。
     *
     * @param category 意图类别过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  意图名称/关键词模糊匹配（可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 意图分页结果 (按 priority DESC, createTime DESC)
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/intents/list")
    public OperationResponse<Page<ScrmAiIntentEntity>> listIntents(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "priority")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        return OperationResponse.build(scrmAiAssistantService.listIntents(category, enabled, keyword, pageable));
    }

    /**
     * 启用意图。
     *
     * @param id 意图 ID
     * @return 更新后的意图
     * @throws ScrmException 意图不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "update")
    @PostMapping("/intents/{id}/enable")
    public OperationResponse<ScrmAiIntentEntity> enableIntent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.enableIntent(id));
    }

    /**
     * 禁用意图。
     *
     * @param id 意图 ID
     * @return 更新后的意图
     * @throws ScrmException 意图不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "update")
    @PostMapping("/intents/{id}/disable")
    public OperationResponse<ScrmAiIntentEntity> disableIntent(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.disableIntent(id));
    }

    /**
     * 意图识别。
     *
     * @param body 请求体, 包含 message 字段 (客户消息)
     * @return 识别结果 {intentName, intentCategory, confidence, suggestedAction, responseTemplate}
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/intents/detect")
    public OperationResponse<Map<String, Object>> detectIntent(@RequestBody Map<String, String> body) {
        String message = body != null ? body.get("message") : null;
        return OperationResponse.build(scrmAiAssistantService.detectIntent(message));
    }

    // ============================================================
    // 对话 /chat
    // ============================================================

    /**
     * AI 对话。
     *
     * @param dto 对话请求
     * @return 对话记录 (含识别意图 / 情感 / 推荐回复 / AI 回复)
     * @throws ScrmException 客户消息为空
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/chat")
    public OperationResponse<ScrmAiConversationEntity> chat(@Valid @RequestBody ScrmAiChatDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.chat(dto));
    }

    /**
     * 批量对话。
     *
     * @param dtos 对话请求列表
     * @return 对话记录列表 (与入参顺序一致, 失败项不包含)
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/chat/batch")
    public OperationResponse<List<ScrmAiConversationEntity>> batchChat(@RequestBody List<ScrmAiChatDto> dtos) {
        return OperationResponse.build(scrmAiAssistantService.batchChat(dtos));
    }

    /**
     * 获取推荐回复 (不调用 AI, 基于意图模板)。
     *
     * @param body 请求体, 包含 customerId 与 message 字段
     * @return 推荐回复列表 (JSON 数组字符串)
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/chat/recommended-replies")
    public OperationResponse<String> getRecommendedReplies(@RequestBody Map<String, Object> body) {
        Long customerId = body != null && body.get("customerId") != null
                ? Long.valueOf(body.get("customerId").toString()) : null;
        String message = body != null && body.get("message") != null
                ? body.get("message").toString() : null;
        return OperationResponse.build(scrmAiAssistantService.getRecommendedReplies(customerId, message));
    }

    /**
     * 提供对话反馈。
     *
     * @param dto 反馈请求
     * @return 更新后的对话记录
     * @throws ScrmException 对话不存在 / 反馈非法
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "update")
    @PostMapping("/chat/feedback")
    public OperationResponse<ScrmAiConversationEntity> provideFeedback(@Valid @RequestBody ScrmAiReplyFeedbackDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.provideFeedback(dto));
    }

    // ============================================================
    // 对话查询 /conversations
    // ============================================================

    /**
     * 分页查询对话记录列表。
     *
     * @param customerId     客户 ID 过滤（可空）
     * @param conversationId 会话 ID 过滤（可空）
     * @param detectedIntent 识别意图过滤（可空）
     * @param sentiment      情感过滤（可空）: POSITIVE / NEUTRAL / NEGATIVE / ANGRY / HAPPY
     * @param startTime      对话时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime        对话时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page           页码（从 0 开始, 默认 0）
     * @param size           每页大小（默认 20）
     * @return 对话记录分页结果 (按 createdAt DESC)
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/conversations/list")
    public OperationResponse<Page<ScrmAiConversationEntity>> listConversations(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long conversationId,
            @RequestParam(required = false) String detectedIntent,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return OperationResponse.build(scrmAiAssistantService.listConversations(
                customerId, conversationId, detectedIntent, sentiment, startTime, endTime, pageable));
    }

    /**
     * 查询对话记录详情。
     *
     * @param id 对话记录 ID
     * @return 对话记录详情
     * @throws ScrmException 对话不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/conversations/{id}")
    public OperationResponse<ScrmAiConversationEntity> getConversation(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.getConversation(id));
    }

    // ============================================================
    // 知识库管理 /knowledge-bases
    // ============================================================

    /**
     * 创建知识库。
     *
     * @param dto 知识库参数
     * @return 创建后的知识库
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/knowledge-bases")
    public OperationResponse<ScrmAiKnowledgeBaseEntity> createKnowledgeBase(
            @Valid @RequestBody ScrmAiKnowledgeBaseDto dto) throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.createKnowledgeBase(dto));
    }

    /**
     * 更新知识库。
     *
     * @param id  知识库 ID
     * @param dto 知识库参数
     * @return 更新后的知识库
     * @throws ScrmException 知识库不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/knowledge-bases/{id}")
    public OperationResponse<ScrmAiKnowledgeBaseEntity> updateKnowledgeBase(@PathVariable Long id,
                                                                             @RequestBody ScrmAiKnowledgeBaseDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.updateKnowledgeBase(id, dto));
    }

    /**
     * 删除知识库。
     *
     * @param id 知识库 ID
     * @return 空响应
     * @throws ScrmException 知识库不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "delete")
    @DeleteMapping("/knowledge-bases/{id}")
    public OperationResponse<Void> deleteKnowledgeBase(@PathVariable Long id) throws ScrmException {
        scrmAiAssistantService.deleteKnowledgeBase(id);
        return OperationResponse.build();
    }

    /**
     * 查询知识库详情。
     *
     * @param id 知识库 ID
     * @return 知识库详情
     * @throws ScrmException 知识库不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/knowledge-bases/{id}")
    public OperationResponse<ScrmAiKnowledgeBaseEntity> getKnowledgeBase(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.getKnowledgeBase(id));
    }

    /**
     * 分页查询知识库列表。
     *
     * @param category 分类过滤（可空）: PRODUCT / FAQ / POLICY / SCRIPT / PROCESS
     * @param enabled  启用状态过滤（可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 知识库分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/knowledge-bases/list")
    public OperationResponse<Page<ScrmAiKnowledgeBaseEntity>> listKnowledgeBases(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmAiAssistantService.listKnowledgeBases(category, enabled, pageable));
    }

    /**
     * 知识库搜索。
     *
     * @param body 请求体, 包含 knowledgeBaseId (可空) 与 query 字段
     * @return 搜索结果列表 [{id, title, snippet, knowledgeBaseId, score}]
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/knowledge-bases/search")
    public OperationResponse<List<Map<String, Object>>> searchKnowledge(@RequestBody Map<String, Object> body) {
        Long knowledgeBaseId = body != null && body.get("knowledgeBaseId") != null
                ? Long.valueOf(body.get("knowledgeBaseId").toString()) : null;
        String query = body != null && body.get("query") != null
                ? body.get("query").toString() : null;
        return OperationResponse.build(scrmAiAssistantService.searchKnowledge(knowledgeBaseId, query));
    }

    // ============================================================
    // 知识库文档管理 /knowledge-bases/{kbId}/documents, /knowledge-bases/documents/{id}
    // ============================================================

    /**
     * 添加文档到知识库。
     *
     * @param kbId 知识库 ID
     * @param dto  文档参数
     * @return 创建后的文档
     * @throws ScrmException 知识库不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/knowledge-bases/{kbId}/documents")
    public OperationResponse<ScrmAiKnowledgeDocumentEntity> addDocument(@PathVariable Long kbId,
                                                                         @Valid @RequestBody ScrmAiKnowledgeDocumentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.addDocument(kbId, dto));
    }

    /**
     * 分页查询知识库文档列表。
     *
     * @param kbId    知识库 ID
     * @param keyword 关键词模糊匹配（可空）
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 文档分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/knowledge-bases/{kbId}/documents")
    public OperationResponse<Page<ScrmAiKnowledgeDocumentEntity>> listDocuments(
            @PathVariable Long kbId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmAiAssistantService.listDocuments(kbId, keyword, pageable));
    }

    /**
     * 更新文档。
     *
     * @param id  文档 ID
     * @param dto 文档参数
     * @return 更新后的文档
     * @throws ScrmException 文档不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/knowledge-bases/documents/{id}")
    public OperationResponse<ScrmAiKnowledgeDocumentEntity> updateDocument(@PathVariable Long id,
                                                                             @RequestBody ScrmAiKnowledgeDocumentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.updateDocument(id, dto));
    }

    /**
     * 删除文档。
     *
     * @param id 文档 ID
     * @return 空响应
     * @throws ScrmException 文档不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "delete")
    @DeleteMapping("/knowledge-bases/documents/{id}")
    public OperationResponse<Void> deleteDocument(@PathVariable Long id) throws ScrmException {
        scrmAiAssistantService.deleteDocument(id);
        return OperationResponse.build();
    }

    /**
     * 查询文档详情。
     *
     * @param id 文档 ID
     * @return 文档详情
     * @throws ScrmException 文档不存在
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/knowledge-bases/documents/{id}")
    public OperationResponse<ScrmAiKnowledgeDocumentEntity> getDocument(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmAiAssistantService.getDocument(id));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * AI 助手统计概览: 对话数、意图分布、情感分布、平均置信度、反馈率。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getAssistantStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmAiAssistantService.getAssistantStats(startTime, endTime));
    }

    /**
     * 意图统计: 按识别意图聚合对话数。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 意图统计 Map {intent -> count}
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/stats/intents")
    public OperationResponse<Map<String, Long>> getIntentStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmAiAssistantService.getIntentStats(startTime, endTime));
    }

    /**
     * 反馈统计: 好评率 / 差评率。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 反馈统计 Map {total, good, bad, goodRate, badRate}
     */
    @RequirePermission(resource = "scrm_ai_assistant", action = "read")
    @GetMapping("/stats/feedback")
    public OperationResponse<Map<String, Object>> getFeedbackStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmAiAssistantService.getFeedbackStats(startTime, endTime));
    }
}
