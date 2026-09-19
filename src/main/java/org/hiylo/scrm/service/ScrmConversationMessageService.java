/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmConversationMessageService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.callback.ConversationEventCallbackDto;

import org.hiylo.scrm.dto.ScrmAccountDto;
import org.hiylo.scrm.dto.ScrmConversationDto;
import org.hiylo.scrm.dto.ScrmConversationMessageDto;
import org.hiylo.scrm.entity.ScrmConversationEntity;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.integration.wework.dto.WeworkMessageSendDto;
import org.hiylo.scrm.integration.wework.service.WeworkService;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SCRM 会话消息服务
 * <p>
 * 负责消息的保存、回调入库（含 platformMessageId 去重）、分页查询、关键字搜索与媒体 URL 生成。
 * 文本类消息（TEXT/LINK/SYSTEM）直接存 content；媒体类消息（IMAGE/VOICE/VIDEO/FILE）
 * 通过 {@link ConversationMediaService} 上传对象存储后保存 mediaObjectKey。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmConversationMessageService {

    /** 文本类消息类型集合（直接存 content） */
    private static final Set<String> TEXT_MESSAGE_TYPES = Set.of("TEXT", "LINK", "SYSTEM");

    /** 媒体类消息类型集合（存 mediaObjectKey） */
    private static final Set<String> MEDIA_MESSAGE_TYPES = Set.of("IMAGE", "VOICE", "VIDEO", "FILE");

    /** 消息数据访问层 */
    private final ScrmConversationMessageRepository messageRepository;

    /** 会话数据访问层（用于校验会话存在性与归属账号 ID 透传） */
    private final ScrmConversationRepository conversationRepository;

    /** 会话服务（用于查找 / 创建会话与更新最后消息） */
    private final ScrmConversationService conversationService;

    /** 账号服务（用于查找账号对应的平台和设备） */
    private final ScrmAccountService accountService;

    /** 媒体存储服务 */
    private final ConversationMediaService mediaService;

    /** 实时通知服务（保存消息后推送 WebSocket 通知） */
    private final ScrmNotificationService notificationService;

    /** 企业微信开放 API 客户端（OUT 消息通过企微 API 直接发送） */
    private final WeworkService weworkService;

    /** 客户数据访问层（解析客户昵称用于搜索联系人） */
    private final ScrmCustomerRepository customerRepository;

    /**
     * 保存消息
     * <p>
     * 根据消息类型分流处理：
     * <ul>
     *   <li>TEXT / LINK / SYSTEM:直接存 content,清空 mediaObjectKey</li>
     *   <li>IMAGE / VOICE / VIDEO / FILE:清空 content,要求 mediaObjectKey 不为空（媒体已通过上传接口存入对象存储）</li>
     * </ul>
     * 保存后联动更新会话的最后消息时间与摘要。
     * </p>
     *
     * @param dto 消息数据
     * @return 保存后的消息数据（含生成的 ID）
     * @throws ScrmException 消息类型非法 / 媒体消息缺少 mediaObjectKey / 会话不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public ScrmConversationMessageDto saveMessage(ScrmConversationMessageDto dto) throws ScrmException {
        // 自动填充默认值：messageId、messageType、direction、sentAt
        if (dto.getMessageId() == null || dto.getMessageId().isBlank()) {
            dto.setMessageId(java.util.UUID.randomUUID().toString());
        }
        // contentType 兼容：前端可能发 contentType 而非 messageType
        if (dto.getMessageType() == null || dto.getMessageType().isBlank()) {
            if (dto.getContentType() != null && !dto.getContentType().isBlank()) {
                dto.setMessageType(dto.getContentType().toUpperCase());
            } else {
                dto.setMessageType("TEXT");
            }
        }
        if (dto.getDirection() == null || dto.getDirection().isBlank()) {
            dto.setDirection("OUT");
        }
        if (dto.getSentAt() == null) {
            dto.setSentAt(LocalDateTime.now());
        }

        validateMessageType(dto.getMessageType());
        validateDirection(dto.getDirection());
        ensureConversationExists(dto.getConversationId());

        String messageType = dto.getMessageType().toUpperCase();
        ScrmConversationMessageEntity entity = toEntity(dto);
        entity.setId(null);

        if (TEXT_MESSAGE_TYPES.contains(messageType)) {
            if (dto.getContent() == null || dto.getContent().isBlank()) {
                throw new ScrmException(ScrmExceptionConstants.SCRM_MESSAGE_CONTENT_MISSING,
                        "文本消息内容不能为空: messageId=" + dto.getMessageId());
            }
            entity.setContent(dto.getContent());
            entity.setMediaObjectKey(null);
            entity.setMediaSize(null);
        } else if (MEDIA_MESSAGE_TYPES.contains(messageType)) {
            if (dto.getMediaObjectKey() == null || dto.getMediaObjectKey().isBlank()) {
                throw new ScrmException(ScrmExceptionConstants.SCRM_MESSAGE_MEDIA_MISSING,
                        "媒体消息缺少 mediaObjectKey: messageId=" + dto.getMessageId());
            }
            entity.setContent(null);
            entity.setMediaObjectKey(dto.getMediaObjectKey());
            if (dto.getMediaSize() != null) {
                entity.setMediaSize(dto.getMediaSize());
            }
        }

        ScrmConversationMessageEntity saved = messageRepository.save(entity);
        log.info("保存会话消息: id={}, messageId={}, conversationId={}, type={}",
                saved.getId(), saved.getMessageId(), saved.getConversationId(), saved.getMessageType());

        // 联动更新会话最后消息时间与摘要
        String summary = buildSummary(saved);
        try {
            conversationService.updateLastMessage(saved.getConversationId(), summary, saved.getSentAt());
        } catch (ScrmException e) {
            // 会话更新失败不影响消息保存,仅记录日志
            log.warn("更新会话最后消息失败: conversationId={}, err={}",
                    saved.getConversationId(), e.getMessage());
        }

        // 通过 WebSocket 推送新消息通知给在线用户（异步非阻塞）
        try {
            notificationService.notifyNewMessage(
                    saved.getConversationId(), saved.getDirection(),
                    saved.getMessageType(), saved.getContent());
        } catch (Exception e) {
            log.warn("新消息通知推送失败 (不影响消息保存): conversationId={}, err={}",
                    saved.getConversationId(), e.getMessage());
        }

        // 事务提交后推送看板统计更新 (DASHBOARD_STAT_UPDATE), 避免未提交数据被前端提前看到。
        // 在 afterCommit 回调中执行, 此时事务已成功落库, 前端可安全递增消息计数。
        try {
            final Long statConversationId = saved.getConversationId();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    /**
                     * 事务提交后推送看板消息计数更新。
                     */
                    @Override
                    public void afterCommit() {
                        // 事务提交后异步推送看板消息计数 +1
                        notificationService.sendDashboardStatUpdate(
                                "messages", statConversationId);
                    }
                });
            } else {
                // 无事务上下文时直接推送 (兼容非事务调用场景)
                notificationService.sendDashboardStatUpdate("messages", statConversationId);
            }
        } catch (Exception e) {
            log.warn("注册看板统计更新回调失败 (不影响消息保存): conversationId={}, err={}",
                    saved.getConversationId(), e.getMessage());
        }

        // OUT 方向消息：异步调用平台适配层发送到社媒设备
        if ("OUT".equals(saved.getDirection())) {
            dispatchOutMessageAsync(saved);
        }

        // IN 方向消息：递增会话未读数
        if ("IN".equals(saved.getDirection())) {
            try {
                conversationService.incrementUnreadCount(saved.getConversationId());
            } catch (Exception e) {
                log.warn("递增未读数失败 (不影响消息保存): conversationId={}, err={}",
                        saved.getConversationId(), e.getMessage());
            }
        }

        return toDto(saved);
    }

    /**
     * 接收 scrm-server 回调的会话事件并转为消息存储
     * <p>
     * 处理流程：
     * <ol>
       * <li>从回调 DTO 提取 platformType / accountId / customerId / messageType / direction / content / mediaObjectKey / *
       * sentAt</li> * <li>规范化 messageType 为大写,direction INBOUND→IN / OUTBOUND→OUT</li> * <li>按 platformMessageId
        * 去重,已存在则直接返回已有消息</li>
     *   <li>优先按 platformConversationId 查找会话；找不到再按 accountId + customerId 查找；均不存在则创建新会话</li>
     *   <li>调用 {@link #saveMessage} 保存消息</li>
     * </ol>
     * </p>
     *
     * @param callback 回调事件 DTO
     * @return 保存后的消息数据（去重命中时返回已存在的记录）
     * @throws ScrmException 参数非法或保存失败
     */
    @Transactional(rollbackFor = Exception.class)
    public ScrmConversationMessageDto saveMessageFromCallback(ConversationEventCallbackDto callback)
            throws ScrmException {
        if (callback == null) {
            throw ScrmException.badRequest("回调事件不能为空");
        }

        // 按 platformMessageId 去重
        if (callback.getPlatformMessageId() != null && !callback.getPlatformMessageId().isBlank()) {
            Optional<ScrmConversationMessageEntity> existed =
                    messageRepository.findByPlatformMessageId(callback.getPlatformMessageId());
            if (existed.isPresent()) {
                log.debug("消息已存在,跳过保存: platformMessageId={}", callback.getPlatformMessageId());
                return toDto(existed.get());
            }
        }

        Long accountId = parseLong(callback.getAccountId(), "accountId");
        Long customerId = parseLong(callback.getCustomerId(), "customerId");
        String messageType = normalizeMessageType(callback.getMessageType());
        String direction = normalizeDirection(callback.getDirection());
        LocalDateTime sentAt = callback.getSentAt() != null ? callback.getSentAt() : LocalDateTime.now();

        // 查找或创建会话
        ScrmConversationDto conversation = resolveOrCreateConversation(callback, accountId, customerId);

        // 构建消息 DTO
        ScrmConversationMessageDto messageDto = new ScrmConversationMessageDto();
        // 业务消息 ID：优先用平台消息 ID,否则生成 UUID
        String messageId = (callback.getPlatformMessageId() != null && !callback.getPlatformMessageId().isBlank())
                ? callback.getPlatformMessageId()
                : "msg_" + UUID.randomUUID().toString().replace("-", "");
        messageDto.setMessageId(messageId);
        messageDto.setConversationId(conversation.getId());
        messageDto.setMessageType(messageType);
        messageDto.setDirection(direction);
        messageDto.setContent(callback.getContent());
        messageDto.setMediaObjectKey(callback.getMediaObjectKey());
        messageDto.setPlatformMessageId(callback.getPlatformMessageId());
        messageDto.setSentAt(sentAt);

        return saveMessage(messageDto);
    }

    /**
     * 分页查询会话消息（按发送时间倒序）
     *
     * @param conversationId 会话 ID
     * @param page           页码（从 0 开始）
     * @param size           每页大小
     * @return 消息分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmConversationMessageDto> getMessages(Long conversationId, int page, int size) {
        // 数据隔离: 校验会话归属当前账号
        ensureConversationExists(conversationId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        return messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable)
                .map(this::toDto);
    }

    /**
     * 按时间范围查询会话消息（按发送时间倒序）
     *
     * @param conversationId 会话 ID
     * @param start          起始时间（含）
     * @param end            截止时间（含）
     * @return 消息列表
     */
    @Transactional(readOnly = true)
    public List<ScrmConversationMessageDto> getMessagesByTimeRange(Long conversationId,
                                                                     LocalDateTime start, LocalDateTime end) {
        return messageRepository.findByConversationIdAndSentAtBetweenOrderBySentAtDesc(
                        conversationId, start, end).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 简单 LIKE 关键字搜索会话消息（按发送时间倒序）
     *
     * @param keyword        搜索关键字
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    @Transactional(readOnly = true)
    public List<ScrmConversationMessageDto> searchMessages(String keyword, Long conversationId) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        // 数据隔离: 校验会话归属当前账号
        ensureConversationExists(conversationId);
        Pageable pageable = PageRequest.of(0, 200);
        return messageRepository
                .findByConversationIdAndContentContainingIgnoreCaseOrderBySentAtDesc(
                        conversationId, keyword, pageable)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 获取消息媒体预签名 URL
     *
     * @param messageId 消息业务 ID（messageId 字段）
     * @return 媒体预签名 URL
     * @throws ScrmException 消息不存在 / 消息无媒体对象
     */
    @Transactional(readOnly = true)
    public String getMessageMediaUrl(String messageId) throws ScrmException {
        if (messageId == null || messageId.isBlank()) {
            throw ScrmException.badRequest("消息 ID 不能为空");
        }
        ScrmConversationMessageEntity entity = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.SCRM_MESSAGE_NOT_FOUND,
                        "消息不存在: messageId=" + messageId));
        // 数据隔离: 校验消息归属当前账号

        if (entity.getMediaObjectKey() == null || entity.getMediaObjectKey().isBlank()) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_MESSAGE_MEDIA_MISSING,
                    "消息无媒体对象: messageId=" + messageId);
        }
        return mediaService.getMediaUrl(entity.getMediaObjectKey());
    }

    // ==================== 内部工具方法 ====================

    /**
     * 异步发送出站消息到社媒平台。
     * <p>
     * 通过平台适配层调用 scrm-server 创建行为流，在云手机上执行发送操作。
     * 发送失败不回滚消息保存（消息已持久化，发送可重试）。
     * 当前仅支持微信个人号平台，其他平台待扩展。
     * </p>
     *
     * @param message 已保存的出站消息实体
     */
    private void dispatchOutMessageAsync(ScrmConversationMessageEntity message) {
        Long conversationId = message.getConversationId();
        Long accountId = resolveAccountId(conversationId);
        if (accountId == null) {
            log.warn("出站消息发送跳过: 无法解析 accountId, conversationId={}", conversationId);
            return;
        }
        // 解析目标客户昵称（用于搜索联系人）
        String targetCustomerName = resolveCustomerName(conversationId);
        if (targetCustomerName == null || targetCustomerName.isBlank()) {
            log.warn("出站消息发送跳过: 无法解析目标客户昵称, conversationId={}", conversationId);
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                // 查找账号对应的平台类型
                ScrmAccountDto account = accountService.getAccount(accountId);
                String platformType = account.getPlatformType();
                log.info("异步发送出站消息: conversationId={}, accountId={}, platform={}, target={}, content={}",
                        conversationId, accountId, platformType, targetCustomerName,
                        message.getContent() != null && message.getContent().length() > 30
                                ? message.getContent().substring(0, 30) + "..."
                                : message.getContent());
                // 企业微信: 通过开放 API 直接发送, 需要客户的 platformCustomerUid (即 external_userid)
                if (!"WEWORK".equalsIgnoreCase(platformType)) {
                    log.warn("出站消息发送暂不支持平台: platform={}, conversationId={}",
                            platformType, conversationId);
                    return;
                }
                String externalUserId = resolveCustomerPlatformUid(conversationId);
                if (externalUserId == null || externalUserId.isBlank()) {
                    log.warn("企微出站消息发送跳过: 无法解析客户 external_userid, conversationId={}", conversationId);
                    return;
                }
                boolean sent;
                try {
                    WeworkMessageSendDto result = weworkService.sendMessageToExternal(
                            externalUserId, message.getContent());
                    sent = result != null && Boolean.TRUE.equals(result.getSuccess());
                } catch (Exception e) {
                    log.warn("企微出站消息发送异常: conversationId={}, err={}", conversationId, e.getMessage());
                    sent = false;
                }
                if (sent) {
                    log.info("出站消息平台发送成功: conversationId={}, accountId={}, target={}",
                            conversationId, accountId, targetCustomerName);
                } else {
                    log.warn("出站消息平台发送失败: conversationId={}, accountId={}, target={}",
                            conversationId, accountId, targetCustomerName);
                }
            } catch (Exception e) {
                log.warn("出站消息异步发送失败 (不影响消息保存): conversationId={}, err={}",
                        conversationId, e.getMessage());
            }
        });
    }

    /**
     * 从会话中解析账号 ID
     *
     * @param conversationId 会话 ID
     * @return 账号 ID，解析失败返回 null
     */
    private Long resolveAccountId(Long conversationId) {
        try {
            ScrmConversationDto conv = conversationService.getConversation(conversationId);
            return conv != null ? conv.getAccountId() : null;
        } catch (Exception e) {
            log.warn("解析会话账号 ID 失败: conversationId={}, err={}", conversationId, e.getMessage());
            return null;
        }
    }

    /**
     * 从会话中解析目标客户昵称（用于搜索联系人）
     * <p>
     * 通过客户 ID 查询 scrm_customer 表获取 nickname，
     * 优先使用昵称作为微信搜索关键词。
     * </p>
     *
     * @param conversationId 会话 ID
     * @return 客户昵称，解析失败返回 null
     */
    private String resolveCustomerName(Long conversationId) {
        try {
            ScrmConversationDto conv = conversationService.getConversation(conversationId);
            if (conv == null) {
                return null;
            }
            Long customerId = conv.getCustomerId();
            if (customerId != null) {
                return customerRepository.findById(customerId)
                        .map(ScrmCustomerEntity::getNickname)
                        .orElse(null);
            }
            return null;
        } catch (Exception e) {
            log.warn("解析会话客户昵称失败: conversationId={}, err={}", conversationId, e.getMessage());
            return null;
        }
    }

    /**
     * 从会话中解析客户的平台唯一标识 (platformCustomerUid)。
     * <p>
     * 企微场景下该值为 external_userid, 用于通过企微 API 直接发送消息。
     * </p>
     *
     * @param conversationId 会话 ID
     * @return 客户平台唯一标识, 解析失败返回 null
     */
    private String resolveCustomerPlatformUid(Long conversationId) {
        try {
            ScrmConversationDto conv = conversationService.getConversation(conversationId);
            if (conv == null) {
                return null;
            }
            Long customerId = conv.getCustomerId();
            if (customerId != null) {
                return customerRepository.findById(customerId)
                        .map(ScrmCustomerEntity::getPlatformCustomerUid)
                        .orElse(null);
            }
            return null;
        } catch (Exception e) {
            log.warn("解析客户平台标识失败: conversationId={}, err={}", conversationId, e.getMessage());
            return null;
        }
    }

    /**
     * 校验消息类型合法性
     *
     * @param messageType 消息类型
     * @throws ScrmException 消息类型非法
     */
    private void validateMessageType(String messageType) throws ScrmException {
        if (messageType == null || messageType.isBlank()) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_MESSAGE_TYPE_INVALID,
                    "消息类型不能为空");
        }
        String upper = messageType.toUpperCase();
        if (!TEXT_MESSAGE_TYPES.contains(upper) && !MEDIA_MESSAGE_TYPES.contains(upper)) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_MESSAGE_TYPE_INVALID,
                    "消息类型非法: " + messageType);
        }
    }

    /**
     * 校验消息方向合法性
     *
     * @param direction 消息方向
     * @throws ScrmException 消息方向非法
     */
    private void validateDirection(String direction) throws ScrmException {
        if (direction == null || direction.isBlank()) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_MESSAGE_DIRECTION_INVALID,
                    "消息方向不能为空");
        }
        String upper = direction.toUpperCase();
        if (!"IN".equals(upper) && !"OUT".equals(upper)) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_MESSAGE_DIRECTION_INVALID,
                    "消息方向非法: " + direction);
        }
    }

    /**
     * 校验会话存在性
     *
     * @param conversationId 会话 ID
     * @throws ScrmException 会话不存在
     */
    private void ensureConversationExists(Long conversationId) throws ScrmException {
        if (conversationId == null) {
            throw ScrmException.badRequest("会话 ID 不能为空");
        }
        // 校验会话存在
        ScrmConversationEntity conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.SCRM_CONVERSATION_NOT_FOUND,
                        "会话不存在: id=" + conversationId));

    }

    /**
     * 查找或创建会话
     * <p>
     * 优先按 platformConversationId 查找,其次按 accountId + customerId 查找,
     * 均不存在则创建新会话。
     * </p>
     *
     * @param callback   回调事件
     * @param accountId  账号 ID
     * @param customerId 客户 ID
     * @return 会话 DTO
     * @throws ScrmException 创建会话失败
     */
    private ScrmConversationDto resolveOrCreateConversation(ConversationEventCallbackDto callback,
                                                             Long accountId,
                                                                     Long customerId) throws ScrmException {
        String platformConversationId = callback.getConversationId();

        // 1. 优先按 platformConversationId 查找
        if (platformConversationId != null && !platformConversationId.isBlank()) {
            ScrmConversationDto exist = conversationService.getConversationByPlatformId(platformConversationId);
            if (exist != null) {
                return exist;
            }
        }

        // 2. 按 accountId + customerId 查找
        ScrmConversationDto byAccount = conversationService.getConversationByAccountAndCustomer(accountId, customerId);
        if (byAccount != null) {
            return byAccount;
        }

        // 3. 创建新会话
        ScrmConversationDto newConversation = new ScrmConversationDto();
        newConversation.setPlatformType(callback.getPlatformType());
        newConversation.setAccountId(accountId);
        newConversation.setCustomerId(customerId);
        newConversation.setConversationType("SINGLE");
        newConversation.setPlatformConversationId(platformConversationId);
        newConversation.setLastMessageAt(callback.getSentAt() != null ? callback.getSentAt() : LocalDateTime.now());
        log.info("回调创建新会话: platformType={}, accountId={}, customerId={}, platformConversationId={}",
                callback.getPlatformType(), accountId, customerId, platformConversationId);
        return conversationService.createConversation(newConversation);
    }

    /**
     * 规范化消息类型为大写
     *
     * @param messageType 原始消息类型
     * @return 大写消息类型
     * @throws ScrmException 消息类型非法
     */
    private String normalizeMessageType(String messageType) throws ScrmException {
        validateMessageType(messageType);
        return messageType.toUpperCase();
    }

    /**
     * 规范化消息方向
     * <p>
     * INBOUND / IN → IN
     * OUTBOUND / OUT → OUT
     * </p>
     *
     * @param direction 原始消息方向
     * @return 规范化后的消息方向
     * @throws ScrmException 消息方向非法
     */
    private String normalizeDirection(String direction) throws ScrmException {
        if (direction == null || direction.isBlank()) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_MESSAGE_DIRECTION_INVALID,
                    "消息方向不能为空");
        }
        String upper = direction.toUpperCase();
        if ("INBOUND".equals(upper) || "IN".equals(upper)) {
            return "IN";
        }
        if ("OUTBOUND".equals(upper) || "OUT".equals(upper)) {
            return "OUT";
        }
        throw new ScrmException(ScrmExceptionConstants.SCRM_MESSAGE_DIRECTION_INVALID,
                "消息方向非法: " + direction);
    }

    /**
     * 将字符串解析为 Long,失败抛出参数异常
     *
     * @param value 原始字符串
     * @param field 字段名（用于异常信息）
     * @return Long 值
     * @throws ScrmException 解析失败
     */
    private Long parseLong(String value, String field) throws ScrmException {
        if (value == null || value.isBlank()) {
            throw ScrmException.badRequest(field + " 不能为空");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw ScrmException.badRequest(field + " 格式非法: " + value);
        }
    }

    /**
     * 构建消息摘要（用于会话列表预览）
     *
     * @param entity 消息实体
     * @return 摘要文本
     */
    private String buildSummary(ScrmConversationMessageEntity entity) {
        if (entity.getContent() != null && !entity.getContent().isBlank()) {
            String content = entity.getContent();
            return content.length() > 100 ? content.substring(0, 100) : content;
        }
        String type = entity.getMessageType();
        if (type == null) {
            return "[消息]";
        }
        return switch (type) {
            case "IMAGE" -> "[图片]";
            case "VOICE" -> "[语音]";
            case "VIDEO" -> "[视频]";
            case "FILE" -> "[文件]";
            case "LINK" -> "[链接]";
            case "SYSTEM" -> "[系统消息]";
            default -> "[消息]";
        };
    }

    /**
     * 实体转 DTO
     *
     * @param entity 消息实体
     * @return 消息 DTO
     */
    private ScrmConversationMessageDto toDto(ScrmConversationMessageEntity entity) {
        if (entity == null) {
            return null;
        }
        ScrmConversationMessageDto dto = new ScrmConversationMessageDto();
        dto.setId(entity.getId());
        dto.setMessageId(entity.getMessageId());
        dto.setConversationId(entity.getConversationId());
        dto.setMessageType(entity.getMessageType());
        dto.setDirection(entity.getDirection());
        dto.setContent(entity.getContent());
        dto.setMediaObjectKey(entity.getMediaObjectKey());
        dto.setMediaSize(entity.getMediaSize());
        dto.setPlatformMessageId(entity.getPlatformMessageId());
        dto.setSentAt(entity.getSentAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * DTO 转实体（不复制 ID,用于新建场景）
     *
     * @param dto 消息 DTO
     * @return 消息实体
     */
    private ScrmConversationMessageEntity toEntity(ScrmConversationMessageDto dto) {
        ScrmConversationMessageEntity entity = new ScrmConversationMessageEntity();
        entity.setMessageId(dto.getMessageId());
        entity.setConversationId(dto.getConversationId());
        entity.setMessageType(dto.getMessageType().toUpperCase());
        entity.setDirection(dto.getDirection().toUpperCase());
        entity.setContent(dto.getContent());
        entity.setMediaObjectKey(dto.getMediaObjectKey());
        entity.setMediaSize(dto.getMediaSize());
        entity.setPlatformMessageId(dto.getPlatformMessageId());
        entity.setSentAt(dto.getSentAt());
        if (dto.getVersion() != null) {
            entity.setVersion(dto.getVersion());
        }
        return entity;
    }
}
