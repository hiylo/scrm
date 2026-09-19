/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiAssistantService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */

package org.hiylo.scrm.service;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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

/**
 * SCRM AI 智能对话助手服务门面。
 * <p>
 * 门面模式: 按子域拆分后保留全部 {@code public} 方法签名, 方法体委托给兄弟服务:
 * 配置与意图 ({@link ScrmAiAssistantConfigService} / {@link ScrmAiAssistantIntentService})、
 * 对话与推荐 ({@link ScrmAiAssistantConversationService})、
 * 知识库与文档 ({@link ScrmAiAssistantKnowledgeService})、统计 ({@link ScrmAiAssistantStatsService})。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */

@Service
@RequiredArgsConstructor
public class ScrmAiAssistantService {

    /** AI 助手配置管理兄弟服务 */
    private final ScrmAiAssistantConfigService configService;

    /** AI 意图识别兄弟服务 */
    private final ScrmAiAssistantIntentService intentService;

    /** AI 对话与推荐兄弟服务 */
    private final ScrmAiAssistantConversationService conversationService;

    /** AI 知识库与文档兄弟服务 */
    private final ScrmAiAssistantKnowledgeService knowledgeService;

    /** AI 助手统计兄弟服务 */
    private final ScrmAiAssistantStatsService statsService;

    /**
     * 创建 AI 助手配置。
     * <p>校验 provider 合法性后写入归属账号 ID 持久化, model / temperature / maxTokens /
     * enabled / isDefault 缺省时填默认值。设为默认配置时清除其他默认标记。</p>
     *
     * @param dto 配置参数
     * @return 创建后的配置
     * @throws ScrmException 参数非法
     */
    public ScrmAiAssistantConfigEntity createConfig(ScrmAiAssistantConfigDto dto) throws ScrmException {
        return configService.createConfig(dto);
    }

    /**
     * 更新 AI 助手配置（字段非空才覆盖）。
     *
     * @param id  配置 ID
     * @param dto 配置参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 参数非法
     */
    public ScrmAiAssistantConfigEntity updateConfig(Long id, ScrmAiAssistantConfigDto dto) throws ScrmException {
        return configService.updateConfig(id, dto);
    }

    /**
     * 删除 AI 助手配置。
     *
     * @param id 配置 ID
     * @throws ScrmException 配置不存在
     */
    public void deleteConfig(Long id) throws ScrmException {
        configService.deleteConfig(id);
    }

    /**
     * 查询配置详情。
     *
     * @param id 配置 ID
     * @return 配置实体
     * @throws ScrmException 配置不存在
     */
    public ScrmAiAssistantConfigEntity getConfig(Long id) throws ScrmException {
        return configService.getConfig(id);
    }

    /**
     * 分页查询配置, 支持按服务提供方与启用状态过滤。
     *
     * @param provider 服务提供方过滤（可空）: OPENAI / AZURE / LOCAL / ZHIPU / QWEN
     * @param enabled  启用状态过滤（可空）
     * @param pageable 分页参数
     * @return 配置分页结果 (按 createTime DESC)
     */
    public Page<ScrmAiAssistantConfigEntity> listConfigs(String provider, Boolean enabled, Pageable pageable) {
        return configService.listConfigs(provider, enabled, pageable);
    }

    /**
     * 设置为默认配置。
     * <p>清除其他默认标记后, 将当前配置置为默认。</p>
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    public ScrmAiAssistantConfigEntity setDefaultConfig(Long id) throws ScrmException {
        return configService.setDefaultConfig(id);
    }

    /**
     * 启用配置。
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    public ScrmAiAssistantConfigEntity enableConfig(Long id) throws ScrmException {
        return configService.enableConfig(id);
    }

    /**
     * 禁用配置。
     * <p>禁用默认配置时会同时清除默认标记, 避免下次默认配置查询失败。</p>
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在
     */
    public ScrmAiAssistantConfigEntity disableConfig(Long id) throws ScrmException {
        return configService.disableConfig(id);
    }

    /**
     * 创建 AI 意图。
     * <p>校验 intentCategory / suggestedAction 合法性后写入归属账号 ID 持久化,
     * priority / enabled 缺省时填默认值。</p>
     *
     * @param dto 意图参数
     * @return 创建后的意图
     * @throws ScrmException 参数非法
     */
    public ScrmAiIntentEntity createIntent(ScrmAiIntentDto dto) throws ScrmException {
        return intentService.createIntent(dto);
    }

    /**
     * 更新 AI 意图（字段非空才覆盖）。
     *
     * @param id  意图 ID
     * @param dto 意图参数
     * @return 更新后的意图
     * @throws ScrmException 意图不存在 / 参数非法
     */
    public ScrmAiIntentEntity updateIntent(Long id, ScrmAiIntentDto dto) throws ScrmException {
        return intentService.updateIntent(id, dto);
    }

    /**
     * 删除 AI 意图。
     *
     * @param id 意图 ID
     * @throws ScrmException 意图不存在
     */
    public void deleteIntent(Long id) throws ScrmException {
        intentService.deleteIntent(id);
    }

    /**
     * 查询意图详情。
     *
     * @param id 意图 ID
     * @return 意图实体
     * @throws ScrmException 意图不存在
     */
    public ScrmAiIntentEntity getIntent(Long id) throws ScrmException {
        return intentService.getIntent(id);
    }

    /**
     * 分页查询意图, 支持按意图类别、启用状态与关键字过滤。
     *
     * @param category 意图类别过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  意图名称/关键词模糊匹配（可空）
     * @param pageable 分页参数
     * @return 意图分页结果 (按 priority DESC, createTime DESC)
     */
    public Page<ScrmAiIntentEntity> listIntents(String category, Boolean enabled, String keyword, Pageable pageable) {
        return intentService.listIntents(category, enabled, keyword, pageable);
    }

    /**
     * 启用意图。
     *
     * @param id 意图 ID
     * @return 更新后的意图
     * @throws ScrmException 意图不存在
     */
    public ScrmAiIntentEntity enableIntent(Long id) throws ScrmException {
        return intentService.enableIntent(id);
    }

    /**
     * 禁用意图。
     *
     * @param id 意图 ID
     * @return 更新后的意图
     * @throws ScrmException 意图不存在
     */
    public ScrmAiIntentEntity disableIntent(Long id) throws ScrmException {
        return intentService.disableIntent(id);
    }

    /**
     * 意图识别: 关键词匹配 + 示例相似度, 返回识别结果 (意图 + 置信度)。
     * <p>
     * 流程:
     * <ol>
     *   <li>加载账号启用意图 (priority DESC)</li>
     *   <li>对每条意图: 若 keywords 任一命中消息, 置信度 0.8; 若 examples 任一与消息
     *       Jaccard 相似度 ≥ 0.3, 置信度 0.5 + 相似度 * 0.5 (上限 1.0)</li>
     *   <li>取置信度最高且 ≥ 0.3 的意图为识别结果, 增量更新其匹配次数</li>
     *   <li>未识别返回 UNKNOWN 意图, 置信度 0</li>
     * </ol>
     * </p>
     *
     * @param message 客户消息
     * @return 识别结果 Map: {intentName, intentCategory, confidence, suggestedAction, responseTemplate}
     */
    public Map<String, Object> detectIntent(String message) {
        return intentService.detectIntent(message);
    }

    /**
     * AI 对话: 意图识别 → 情感分析 → 推荐回复 → AI 生成回复 → 记录。
     * <p>
     * 流程:
     * <ol>
     *   <li>解析 AI 配置 (优先 dto.configId, 否则账号默认配置, 都无则使用模拟配置)</li>
     *   <li>调用 {@link #detectIntent} 进行意图识别</li>
     *   <li>调用 {@link #analyzeSentiment} 进行情感分析</li>
     *   <li>基于意图模板生成推荐回复 (不调用 AI)</li>
       * <li>调用 {@link org.hiylo.scrm.service.evaluator.AiResponseGenerator#generate} 生成 AI 回复 (可插拔, *
       * 默认模拟实现)</li> * <li>持久化对话记录, 增量更新配置请求次数</li>     * </ol>
     * </p>
     *
     * @param chatDto 对话请求
     * @return 对话记录实体 (含识别意图 / 情感 / 推荐回复 / AI 回复)
     * @throws ScrmException 客户消息为空
     */
    public ScrmAiConversationEntity chat(ScrmAiChatDto chatDto) throws ScrmException {
        return conversationService.chat(chatDto);
    }

    /**
     * 批量对话: 逐条调用 {@link #chat}, 单条失败跳过不阻断其他。
     *
     * @param chatDtos 对话请求列表
     * @return 对话记录列表 (与入参顺序一致, 失败项不包含)
     * @stub 占位实现: 逐条调用 {@link #chat}, 其 AI 回复生成 (generateAiResponse) 为占位实现。
     *       待对接真实 AI API 后可启用。
     */
    @SuppressWarnings("all")
    public List<ScrmAiConversationEntity> batchChat(List<ScrmAiChatDto> chatDtos) {
        return conversationService.batchChat(chatDtos);
    }

    /**
     * 获取推荐回复 (不调用 AI, 基于意图模板)。
     * <p>识别消息意图后, 根据意图模板与情感生成多条推荐回复。</p>
     *
     * @param customerId 客户 ID (当前未使用, 预留以便后续按客户画像生成)
     * @param message    客户消息
     * @return 推荐回复列表 (JSON 数组字符串)
     */
    public String getRecommendedReplies(Long customerId, String message) {
        return conversationService.getRecommendedReplies(customerId, message);
    }

    /**
     * 提供对话反馈。
     * <p>feedback 仅支持 GOOD/BAD/NONE, NONE 表示撤销反馈。</p>
     *
     * @param feedbackDto 反馈请求
     * @return 更新后的对话记录
     * @throws ScrmException 对话不存在 / 反馈非法
     */
    public ScrmAiConversationEntity provideFeedback(ScrmAiReplyFeedbackDto feedbackDto) throws ScrmException {
        return conversationService.provideFeedback(feedbackDto);
    }

    /**
     * 查询对话记录详情。
     *
     * @param id 对话记录 ID
     * @return 对话记录实体
     * @throws ScrmException 对话不存在
     */
    public ScrmAiConversationEntity getConversation(Long id) throws ScrmException {
        return conversationService.getConversation(id);
    }

    /**
     * 分页查询对话记录, 支持按客户、会话、识别意图、情感与时间范围过滤。
     *
     * @param customerId     客户 ID 过滤（可空）
     * @param conversationId 会话 ID 过滤（可空）
     * @param detectedIntent 识别意图过滤（可空）
     * @param sentiment      情感过滤（可空）
     * @param startTime      对话时间起始 (含, 可空)
     * @param endTime        对话时间截止 (含, 可空)
     * @param pageable       分页参数
     * @return 对话记录分页结果 (按 createdAt DESC)
     */
    public Page<ScrmAiConversationEntity> listConversations(Long customerId, Long conversationId,
                                                             String detectedIntent, String sentiment,
                                                             LocalDateTime startTime, LocalDateTime endTime,
                                                             Pageable pageable) {
        return conversationService.listConversations(customerId, conversationId, detectedIntent, sentiment, startTime, endTime, pageable);
    }

    /**
     * 情感分析: 关键词法, 返回 sentiment 与 score (-1 到 1)。
     * <p>
     * 优先级: ANGRY (含愤怒关键词) > HAPPY (含开心关键词) > POSITIVE / NEGATIVE
     * (含积极/消极关键词) > NEUTRAL。score 按关键词命中数与情感倾向计算。
     * </p>
     *
     * @param text 待分析文本
     * @return 分析结果 Map: {sentiment, score}
     */
    public Map<String, Object> analyzeSentiment(String text) {
        return conversationService.analyzeSentiment(text);
    }

    /**
     * 创建知识库。
     *
     * @param dto 知识库参数
     * @return 创建后的知识库
     * @throws ScrmException 参数非法
     */
    public ScrmAiKnowledgeBaseEntity createKnowledgeBase(ScrmAiKnowledgeBaseDto dto) throws ScrmException {
        return knowledgeService.createKnowledgeBase(dto);
    }

    /**
     * 更新知识库（字段非空才覆盖）。
     *
     * @param id  知识库 ID
     * @param dto 知识库参数
     * @return 更新后的知识库
     * @throws ScrmException 知识库不存在 / 参数非法
     */
    public ScrmAiKnowledgeBaseEntity updateKnowledgeBase(
            Long id, ScrmAiKnowledgeBaseDto dto) throws ScrmException {
        return knowledgeService.updateKnowledgeBase(id, dto);
    }

    /**
     * 删除知识库。
     * <p>同时删除知识库下所有文档。</p>
     *
     * @param id 知识库 ID
     * @throws ScrmException 知识库不存在
     */
    public void deleteKnowledgeBase(Long id) throws ScrmException {
        knowledgeService.deleteKnowledgeBase(id);
    }

    /**
     * 查询知识库详情。
     *
     * @param id 知识库 ID
     * @return 知识库实体
     * @throws ScrmException 知识库不存在
     */
    public ScrmAiKnowledgeBaseEntity getKnowledgeBase(Long id) throws ScrmException {
        return knowledgeService.getKnowledgeBase(id);
    }

    /**
     * 分页查询知识库, 支持按分类与启用状态过滤。
     *
     * @param category 分类过滤（可空）: PRODUCT / FAQ / POLICY / SCRIPT / PROCESS
     * @param enabled  启用状态过滤（可空）
     * @param pageable 分页参数
     * @return 知识库分页结果 (按 createTime DESC)
     */
    public Page<ScrmAiKnowledgeBaseEntity> listKnowledgeBases(String category, Boolean enabled, Pageable pageable) {
        return knowledgeService.listKnowledgeBases(category, enabled, pageable);
    }

    /**
     * 知识库搜索: 关键词匹配 (标题 + 内容)。
     * <p>对启用文档按 title / content LIKE 模糊匹配, 命中文档增量更新浏览次数。</p>
     *
     * @param knowledgeBaseId 知识库 ID (可空, 为空则不限制知识库)
     * @param query           搜索关键词
     * @return 搜索结果列表 [{id, title, snippet, knowledgeBaseId, score}]
     */
    public List<Map<String, Object>> searchKnowledge(Long knowledgeBaseId, String query) {
        return knowledgeService.searchKnowledge(knowledgeBaseId, query);
    }

    /**
     * 添加文档到知识库。
     * <p>校验知识库存在且归属当前账号, contentType / sourceType 缺省时填默认值。
     * 持久化后同步递增知识库的 documentCount。</p>
     *
     * @param knowledgeBaseId 知识库 ID
     * @param dto             文档参数
     * @return 创建后的文档
     * @throws ScrmException 知识库不存在 / 参数非法
     */
    public ScrmAiKnowledgeDocumentEntity addDocument(Long knowledgeBaseId, ScrmAiKnowledgeDocumentDto dto)
            throws ScrmException {
        return knowledgeService.addDocument(knowledgeBaseId, dto);
    }

    /**
     * 更新文档（字段非空才覆盖）。
     *
     * @param id  文档 ID
     * @param dto 文档参数
     * @return 更新后的文档
     * @throws ScrmException 文档不存在 / 参数非法
     */
    public ScrmAiKnowledgeDocumentEntity updateDocument(Long id, ScrmAiKnowledgeDocumentDto dto)
            throws ScrmException {
        return knowledgeService.updateDocument(id, dto);
    }

    /**
     * 删除文档。
     * <p>同步递减所属知识库的 documentCount。</p>
     *
     * @param id 文档 ID
     * @throws ScrmException 文档不存在
     */
    public void deleteDocument(Long id) throws ScrmException {
        knowledgeService.deleteDocument(id);
    }

    /**
     * 查询文档详情。
     *
     * @param id 文档 ID
     * @return 文档实体
     * @throws ScrmException 文档不存在
     */
    public ScrmAiKnowledgeDocumentEntity getDocument(Long id) throws ScrmException {
        return knowledgeService.getDocument(id);
    }

    /**
     * 分页查询知识库文档, 支持按关键词模糊匹配 (title + content)。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param keyword         关键词模糊匹配（可空）
     * @param pageable        分页参数
     * @return 文档分页结果 (按 createTime DESC)
     */
    public Page<ScrmAiKnowledgeDocumentEntity> listDocuments(Long knowledgeBaseId, String keyword, Pageable pageable) {
        return knowledgeService.listDocuments(knowledgeBaseId, keyword, pageable);
    }

    /**
     * AI 助手统计概览: 对话数、意图分布、情感分布、平均置信度、反馈率。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 统计结果 Map
     */
    public Map<String, Object> getAssistantStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getAssistantStats(startTime, endTime);
    }

    /**
     * 意图统计: 按识别意图聚合对话数。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 意图统计 Map {intent -> count}
     */
    public Map<String, Long> getIntentStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getIntentStats(startTime, endTime);
    }

    /**
     * 反馈统计: 好评率 / 差评率。
     *
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @return 反馈统计 Map {total, good, bad, goodRate, badRate}
     */
    public Map<String, Object> getFeedbackStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getFeedbackStats(startTime, endTime);
    }

}
