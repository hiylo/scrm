/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConversationService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmConversationDto;
import org.hiylo.scrm.entity.ScrmConversationEntity;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.feign.AiChatClient;
import org.hiylo.scrm.feign.dto.AiChatRequest;
import org.hiylo.scrm.feign.dto.AiChatResponse;

import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.vo.ConversationSummaryVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 会话服务
 * <p>
 * 负责会话的创建、查询、最后消息更新等业务逻辑。
 * 数据隔离：写入时写入当前用户归属账号；
 * 查询时按 ID 过滤（解析失败时使用 0 作为默认账号）。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmConversationService {

    /** 会话数据访问层 */
    private final ScrmConversationRepository repository;

    /** 会话消息数据访问层（用于 AI 总结时查询最近消息） */
    private final ScrmConversationMessageRepository messageRepository;

    /** 客户仓库（填充会话 DTO 中的客户昵称和头像） */
    private final ScrmCustomerRepository customerRepository;

    /** 账号仓库（填充会话 DTO 中的账号名称） */
    private final ScrmAccountRepository accountRepository;

    /** ai-server Feign 客户端 */
    private final AiChatClient aiChatClient;

    /** AI 模型名称，默认 gpt-4o-mini */
    @Value("${scrm.ai.model:gpt-4o-mini}")
    private String aiModel;

    /** 采样温度，默认 0.7 */
    @Value("${scrm.ai.temperature:0.7}")
    private Double aiTemperature;

    /** 生成最大 token 数，默认 500 */
    @Value("${scrm.ai.max-tokens:500}")
    private Integer aiMaxTokens;

    /** AI 总结时查询的最近消息条数 */
    private static final int SUMMARY_MESSAGE_LIMIT = 50;

    /** 系统提示词角色 */
    private static final String ROLE_SYSTEM = "system";

    /** 用户消息角色 */
    private static final String ROLE_USER = "user";

    /** 空消息时的兜底总结 */
    private static final String EMPTY_SUMMARY = "该会话暂无消息记录。";

    /** AI 调用失败时的兜底总结 */
    private static final String FALLBACK_SUMMARY = "会话总结生成失败，请稍后重试。";

    /** 系统提示词 */
    private static final String SUMMARY_SYSTEM_PROMPT =
            "你是一个会话分析助手，请根据以下对话记录生成简洁的会话总结。总结应包括：1) 主要话题 2) 关键信息 3) 待办事项（如有）。总字数控制在 200 字以内。";

    /**
     * 创建会话
     * <p>
     * 若传入 platformConversationId 且数据库已存在相同值的会话,抛出冲突异常。
     * </p>
     *
     * @param dto 会话数据
     * @return 创建后的会话数据（含生成的 ID）
     * @throws ScrmException 会话重复或参数非法
     */
    @Transactional
    public ScrmConversationDto createConversation(ScrmConversationDto dto) throws ScrmException {
        if (dto.getPlatformConversationId() != null && !dto.getPlatformConversationId().isBlank()) {
            Optional<ScrmConversationEntity> exist = repository
                    .findByPlatformConversationId(dto.getPlatformConversationId());
            if (exist.isPresent()) {
                throw new ScrmException(ScrmExceptionConstants.SCRM_CONVERSATION_DUPLICATED,
                        "会话已存在: platformConversationId=" + dto.getPlatformConversationId());
            }
        }
        ScrmConversationEntity entity = toEntity(dto);
        entity.setId(null);
        ScrmConversationEntity saved = repository.save(entity);
        log.info("创建会话: id={}, platformType={}, accountId={}, customerId={}",
                saved.getId(), saved.getPlatformType(), saved.getAccountId(), saved.getCustomerId());
        return toDto(saved);
    }

    /**
     * 根据主键 ID 查询会话
     *
     * @param id 会话主键
     * @return 会话数据
     * @throws ScrmException 会话不存在
     */
    @Transactional(readOnly = true)
    public ScrmConversationDto getConversation(Long id) throws ScrmException {
        ScrmConversationEntity entity = findOrThrow(id);
        return toDto(entity);
    }

    /**
     * 根据平台会话 ID 查询会话
     *
     * @param platformConversationId 平台会话 ID
     * @return 会话数据（不存在返回 null）
     */
    @Transactional(readOnly = true)
    public ScrmConversationDto getConversationByPlatformId(String platformConversationId) {
        if (platformConversationId == null || platformConversationId.isBlank()) {
            return null;
        }
        return repository.findByPlatformConversationId(platformConversationId)
                .map(this::toDto)
                .orElse(null);
    }

    /**
     * 根据账号 ID 分页查询会话列表（按最后消息时间倒序）
     *
     * @param accountId 账号 ID
     * @param page      页码（从 0 开始）
     * @param size      每页大小
     * @return 会话分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmConversationDto> getConversationsByAccount(Long accountId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        return repository.findByAccountIdOrderByLastMessageAtDesc(accountId, pageable).map(this::toDto);
    }

    /**
     * 根据客户 ID 分页查询会话列表（按最后消息时间倒序）
     *
     * @param customerId 客户 ID
     * @param page       页码（从 0 开始）
     * @param size       每页大小
     * @return 会话分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmConversationDto> getConversationsByCustomer(Long customerId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        return repository.findByCustomerIdOrderByLastMessageAtDesc(customerId, pageable).map(this::toDto);
    }

    /**
     * 更新会话状态
     *
     * @param id     会话主键
     * @param status 目标状态（ACTIVE / CLOSED / PENDING）
     * @return 更新后的会话数据
     * @throws ScrmException 会话不存在或状态非法
     */
    @Transactional
    public ScrmConversationDto updateStatus(Long id, String status) throws ScrmException {
        if (status == null || status.isBlank()) {
            throw ScrmException.badRequest("状态不能为空");
        }
        String upper = status.toUpperCase();
        if (!"ACTIVE".equals(upper) && !"CLOSED".equals(upper) && !"PENDING".equals(upper)) {
            throw ScrmException.badRequest("状态非法: " + status + ", 仅支持 ACTIVE/CLOSED/PENDING");
        }
        ScrmConversationEntity entity = findOrThrow(id);
        entity.setStatus(upper);
        repository.save(entity);
        log.info("更新会话状态: id={}, status={}", id, upper);
        return toDto(entity);
    }

    /**
     * 递增会话未读消息数
     *
     * @param conversationId 会话 ID
     */
    @Transactional
    public void incrementUnreadCount(Long conversationId) {
        try {
            ScrmConversationEntity entity = findOrThrow(conversationId);
            entity.setUnreadCount(entity.getUnreadCount() != null ? entity.getUnreadCount() + 1 : 1L);
            repository.save(entity);
        } catch (Exception e) {
            log.warn("递增未读数失败: conversationId={}, err={}", conversationId, e.getMessage());
        }
    }

    /**
     * 重置会话未读消息数为 0
     *
     * @param conversationId 会话 ID
     */
    @Transactional
    public void resetUnreadCount(Long conversationId) {
        try {
            ScrmConversationEntity entity = findOrThrow(conversationId);
            entity.setUnreadCount(0L);
            repository.save(entity);
        } catch (Exception e) {
            log.warn("重置未读数失败: conversationId={}, err={}", conversationId, e.getMessage());
        }
    }

    /**
     * 更新会话最后消息时间和摘要
     * <p>
     * 用于消息保存后联动更新会话列表的预览与排序。
     * 通过原生 save 覆盖更新,由 @Version 乐观锁保证并发安全。
     * </p>
     *
     * @param conversationId 会话 ID
     * @param summary        最后消息摘要
     * @param messageAt      最后消息时间
     * @throws ScrmException 会话不存在
     */
    @Transactional
    public void updateLastMessage(
            Long conversationId, String summary, LocalDateTime messageAt) throws ScrmException {
        ScrmConversationEntity entity = findOrThrow(conversationId);
        entity.setLastMessageAt(messageAt);
        if (summary != null && summary.length() > 500) {
            summary = summary.substring(0, 500);
        }
        entity.setLastMessageSummary(summary);
        repository.save(entity);
    }

    /**
     * 按最后消息时间范围分页查询会话列表
     *
     * @param accountId  账号 ID（保留参数,当前按 + 时间范围过滤）
     * @param startTime  起始时间（含）
     * @param endTime    截止时间（含）
     * @param page       页码（从 0 开始）
     * @param size       每页大小
     * @return 会话分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmConversationDto> listConversations(Long accountId, LocalDateTime startTime,
                                                        LocalDateTime endTime, int page, int size) {
        return listConversations(accountId, startTime, endTime, null, null, page, size);
    }

    /**
     * 分页查询会话列表，支持状态和关键词筛选
     *
     * @param accountId  账号 ID（保留参数）
     * @param startTime  起始时间（含，可空）
     * @param endTime    截止时间（含，可空）
     * @param status     会话状态筛选（ACTIVE / CLOSED / PENDING，可空）
     * @param keyword    关键词搜索（匹配最后消息摘要，可空）
     * @param page       页码（从 0 开始）
     * @param size       每页大小
     * @return 会话分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmConversationDto> listConversations(Long accountId, LocalDateTime startTime,
                                                        LocalDateTime endTime, String status,
                                                        String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        LocalDateTime from = startTime != null ? startTime : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime to = endTime != null ? endTime : LocalDateTime.now().plusYears(1);

        Page<ScrmConversationEntity> result;
        if (status != null && !status.isBlank()) {
            // 按状态 + 时间范围查询
            result = repository.findByStatusAndLastMessageAtBetweenOrderByLastMessageAtDesc(
                     status.toUpperCase(), from, to, pageable);
        } else if (startTime == null && endTime == null) {
            // 仅按查询（无时间范围限制）
            result = repository.findAllByOrderByLastMessageAtDesc(pageable);
        } else {
            // 按 + 时间范围查询
            result = repository.findByLastMessageAtBetweenOrderByLastMessageAtDesc(
                     from, to, pageable);
        }

        // 关键词在内存中过滤（关联客户昵称/账号名/消息摘要）
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            List<ScrmConversationDto> filtered = result.stream()
                    .map(this::toDto)
                    .filter(dto -> matchesKeyword(dto, kw))
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(filtered, pageable, result.getTotalElements());
        }

        return result.map(this::toDto);
    }

    /**
     * 判断会话 DTO 是否匹配关键词（匹配客户昵称、账号名称、消息摘要）
     *
     * @param dto 会话 DTO
     * @param kw  小写关键词
     * @return 是否匹配
     */
    private boolean matchesKeyword(ScrmConversationDto dto, String kw) {
        if (dto == null) return false;
        if (dto.getCustomerNickname() != null && dto.getCustomerNickname().toLowerCase().contains(kw)) return true;
        if (dto.getAccountName() != null && dto.getAccountName().toLowerCase().contains(kw)) return true;
        if (dto.getLastMessageSummary() != null && dto.getLastMessageSummary().toLowerCase().contains(kw)) return true;
        return false;
    }

    /**
     * 根据账号 ID 与客户 ID 查询会话（用于回调定位会话）
     *
     * @param accountId  账号 ID
     * @param customerId 客户 ID
     * @return 会话数据（不存在返回 null）
     */
    @Transactional(readOnly = true)
    public ScrmConversationDto getConversationByAccountAndCustomer(Long accountId, Long customerId) {
        if (accountId == null || customerId == null) {
            return null;
        }
        return repository.findByAccountIdAndCustomerId(accountId, customerId)
                .map(this::toDto)
                .orElse(null);
    }

    /**
     * AI 总结会话。
     * <p>
     * 流程：
     * <ol>
     *   <li>查询会话实体（不存在抛 404）</li>
     *   <li>查询最近 {@value #SUMMARY_MESSAGE_LIMIT} 条消息（按 sentAt 升序）</li>
     *   <li>消息为空时返回空消息兜底总结</li>
     *   <li>拼接对话文本，构建 system + user 消息调用 ai-server</li>
     *   <li>识别降级标记或调用异常时返回兜底总结</li>
     *   <li>将总结持久化到会话实体的 {@code lastMessageSummary} 字段（不覆盖 lastMessageAt）</li>
     * </ol>
     * AI 调用失败不抛异常，返回兜底总结 {@value #FALLBACK_SUMMARY}。
     *
     * @param conversationId 会话 ID
     * @return 会话总结 VO
     * @throws ScrmException 会话不存在
     */
    @Transactional
    public ConversationSummaryVo summarizeConversation(Long conversationId) throws ScrmException {
        ScrmConversationEntity entity = findOrThrow(conversationId);
        List<ScrmConversationMessageEntity> messages = loadRecentMessages(conversationId);
        long startMs = System.currentTimeMillis();

        if (messages.isEmpty()) {
            log.info("会话无消息记录，返回空消息兜底总结: conversationId={}", conversationId);
            return buildVo(conversationId, EMPTY_SUMMARY, 0, aiModel, 0L);
        }

        String conversationText = buildConversationText(messages);
        String summary;
        String usedModel = aiModel;
        try {
            AiChatRequest request = buildSummaryRequest(SUMMARY_SYSTEM_PROMPT, conversationText);
            AiChatResponse response = aiChatClient.chatCompletion(request);
            String content = response == null ? null : response.getFirstContent();
            if (response != null && response.getModel() != null) {
                usedModel = response.getModel();
            }
            // 响应为空 / 触发 Feign 降级 / 内容为空 → 返回兜底总结
            if (content == null || content.isBlank()
                    || AiChatClient.FALLBACK_CONTENT.equals(content)) {
                log.warn("AI 总结内容为空或触发降级，返回兜底总结: conversationId={}", conversationId);
                summary = FALLBACK_SUMMARY;
            } else {
                summary = content;
            }
        } catch (Exception e) {
            log.warn("调用 ai-server 生成会话总结异常，返回兜底总结: conversationId={}, err={}",
                    conversationId, e.getMessage());
            summary = FALLBACK_SUMMARY;
        }

        long latencyMs = System.currentTimeMillis() - startMs;

        // 持久化总结到会话实体（仅更新 lastMessageSummary，不覆盖 lastMessageAt）
        persistSummary(entity, summary);

        log.info("会话总结生成完成: conversationId={}, messageCount={}, model={}, latencyMs={}",
                conversationId, messages.size(), usedModel, latencyMs);
        return buildVo(conversationId, summary, messages.size(), usedModel, latencyMs);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 根据主键 ID 查询实体,不存在抛出 404 异常
     *
     * @param id 会话主键
     * @return 会话实体
     * @throws ScrmException 会话不存在
     */
    private ScrmConversationEntity findOrThrow(Long id) throws ScrmException {
        return repository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.SCRM_CONVERSATION_NOT_FOUND,
                        "会话不存在: id=" + id));
    }

    /**
     * 查询会话最近 {@value #SUMMARY_MESSAGE_LIMIT} 条消息（按 sentAt 升序返回）。
     * <p>
     * Repository 现有方法按 sentAt 倒序,这里取首页 {@value #SUMMARY_MESSAGE_LIMIT} 条后反转,
     * 得到按时间升序的最近消息序列,便于 AI 理解对话流。
     *
     * @param conversationId 会话 ID
     * @return 按发送时间升序排列的最近消息列表
     */
    private List<ScrmConversationMessageEntity> loadRecentMessages(Long conversationId) {
        Pageable pageable = PageRequest.of(0, SUMMARY_MESSAGE_LIMIT);
        List<ScrmConversationMessageEntity> desc = messageRepository
                .findByConversationIdOrderBySentAtDesc(conversationId, pageable).getContent();
        if (desc.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScrmConversationMessageEntity> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    /**
     * 将消息列表拼接为 AI 可理解的对话文本。
     * <p>
     * 格式：{@code [incoming] 用户: xxx} / {@code [outgoing] 我方: xxx}，每条消息一行。
     * 非文本消息以类型占位符（如 {@code [IMAGE]}）代替内容；direction 兼容 IN/OUT 与
     * INCOMING/OUTGOING 两种取值。
     *
     * @param messages 按时间升序排列的消息列表
     * @return 拼接后的对话文本
     */
    private String buildConversationText(List<ScrmConversationMessageEntity> messages) {
        StringBuilder sb = new StringBuilder();
        for (ScrmConversationMessageEntity msg : messages) {
            boolean incoming = isIncoming(msg.getDirection());
            String role = incoming ? "用户" : "我方";
            String tag = incoming ? "incoming" : "outgoing";
            String body = resolveMessageBody(msg);
            sb.append('[').append(tag).append("] ").append(role).append(": ").append(body).append('\n');
        }
        return sb.toString();
    }

    /**
     * 解析消息正文：文本消息返回 content，非文本消息返回类型占位符。
     *
     * @param msg 会话消息实体
     * @return 消息正文文本
     */
    private String resolveMessageBody(ScrmConversationMessageEntity msg) {
        if (msg.getContent() != null && !msg.getContent().isBlank()) {
            return msg.getContent();
        }
        String type = msg.getMessageType() == null ? "UNKNOWN" : msg.getMessageType();
        return "[" + type + "]";
    }

    /**
     * 判断消息方向是否为入站（兼容 IN / INCOMING 两种取值）。
     *
     * @param direction 消息方向字段值
     * @return true 表示入站消息
     */
    private boolean isIncoming(String direction) {
        if (direction == null) {
            return false;
        }
        return direction.startsWith("IN");
    }

    /**
     * 构建 AI 对话补全请求（system 提示词 + user 对话文本）。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息（对话文本）
     * @return AI 对话补全请求
     */
    private AiChatRequest buildSummaryRequest(String systemPrompt, String userMessage) {
        return AiChatRequest.builder()
                .model(aiModel)
                .temperature(aiTemperature)
                .maxTokens(aiMaxTokens)
                .messages(List.of(
                        new AiChatRequest.AiChatMessage(ROLE_SYSTEM, systemPrompt),
                        new AiChatRequest.AiChatMessage(ROLE_USER, userMessage)
                ))
                .build();
    }

    /**
     * 持久化 AI 总结到会话实体。
     * <p>
     * 仅更新 {@code lastMessageSummary} 字段,不覆盖 {@code lastMessageAt},
     * 通过加载实体后局部修改并 save 实现,由 @Version 乐观锁保证并发安全。
     * 超过 500 字符时截断以匹配列长度。
     *
     * @param entity  会话实体
     * @param summary AI 生成的总结
     */
    private void persistSummary(ScrmConversationEntity entity, String summary) {
        if (summary == null) {
            return;
        }
        String truncated = summary.length() > 500 ? summary.substring(0, 500) : summary;
        entity.setLastMessageSummary(truncated);
        repository.save(entity);
    }

    /**
     * 构建会话总结 VO。
     *
     * @param conversationId 会话 ID
     * @param summary        总结文本
     * @param messageCount   参与总结的消息数
     * @param model          使用的 AI 模型
     * @param latencyMs      生成耗时（毫秒）
     * @return 会话总结 VO
     */
    private ConversationSummaryVo buildVo(Long conversationId, String summary, int messageCount,
                                          String model, long latencyMs) {
        return ConversationSummaryVo.builder()
                .conversationId(conversationId)
                .summary(summary)
                .messageCount(messageCount)
                .model(model)
                .latencyMs(latencyMs)
                .summarizedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 实体转 DTO
     *
     * @param entity 会话实体
     * @return 会话 DTO
     */
    private ScrmConversationDto toDto(ScrmConversationEntity entity) {
        if (entity == null) {
            return null;
        }
        ScrmConversationDto dto = new ScrmConversationDto();
        dto.setId(entity.getId());
        dto.setPlatformType(entity.getPlatformType());
        dto.setAccountId(entity.getAccountId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setConversationType(entity.getConversationType());
        dto.setPlatformConversationId(entity.getPlatformConversationId());
        dto.setLastMessageAt(entity.getLastMessageAt());
        dto.setLastMessageSummary(entity.getLastMessageSummary());
        dto.setStatus(entity.getStatus());
        dto.setUnreadCount(entity.getUnreadCount());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        // 填充客户昵称和头像
        if (entity.getCustomerId() != null) {
            customerRepository.findById(entity.getCustomerId()).ifPresent(customer -> {
                dto.setCustomerNickname(customer.getNickname());
                dto.setCustomerAvatarUrl(customer.getAvatarUrl());
            });
        }
        // 填充账号名称
        if (entity.getAccountId() != null) {
            accountRepository.findById(entity.getAccountId()).ifPresent(account -> {
                dto.setAccountName(account.getDisplayName());
            });
        }
        // 填充消息总数
        dto.setMessageCount(messageRepository.countByConversationId(entity.getId()));
        return dto;
    }

    /**
     * DTO 转实体（不复制 ID,用于新建场景）
     *
     * @param dto 会话 DTO
     * @return 会话实体
     */
    private ScrmConversationEntity toEntity(ScrmConversationDto dto) {
        ScrmConversationEntity entity = new ScrmConversationEntity();
        entity.setId(dto.getId());
        entity.setPlatformType(dto.getPlatformType());
        entity.setAccountId(dto.getAccountId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setConversationType(dto.getConversationType());
        entity.setPlatformConversationId(dto.getPlatformConversationId());
        entity.setLastMessageAt(dto.getLastMessageAt());
        entity.setLastMessageSummary(dto.getLastMessageSummary());
        entity.setStatus(dto.getStatus());
        entity.setUnreadCount(dto.getUnreadCount() != null ? dto.getUnreadCount() : 0L);
        if (dto.getVersion() != null) {
            entity.setVersion(dto.getVersion());
        }
        return entity;
    }
}
