/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkCallbackEventService.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.wework;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.service.ScrmConversationMessageService;
import org.hiylo.scrm.dto.callback.ConversationEventCallbackDto;
import org.hiylo.scrm.integration.wework.dto.WeworkExternalContactDto;
import org.hiylo.scrm.integration.wework.dto.WeworkUserDto;
import org.hiylo.scrm.integration.wework.service.WeworkService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 企业微信回调事件处理服务
 * <p>
 * 负责分发与处理从企业微信回调推送过来的各类事件, 包括:
 * <ul>
 *   <li>外部联系人变更 (添加 / 删除 / 编辑)</li>
 *   <li>内部通讯录变更 (创建 / 更新 / 删除)</li>
 *   <li>会话内容审计 (进入会话 / 消息审计)</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeworkCallbackEventService {

    /** 企微平台类型标识 */
    private static final String PLATFORM_TYPE_WEWORK = "wework";

    /** 新客户默认生命周期 */
    private static final String LIFECYCLE_NEW = "NEW";

    /** 流失客户生命周期 */
    private static final String LIFECYCLE_LOST = "LOST";

    /** 账号默认登录态 */
    private static final String LOGIN_STATE_LOGIN = "LOGIN";

    /** 账号冻结态 */
    private static final String LOGIN_STATE_FROZEN = "FROZEN";

    /** 企业微信平台服务 */
    private final WeworkService weworkService;
    /** 客户数据仓库 */
    private final ScrmCustomerRepository customerRepository;
    /** 账户数据仓库 */
    private final ScrmAccountRepository accountRepository;
    /** 会话消息服务, 用于持久化企微会话审计消息 */
    private final ScrmConversationMessageService conversationMessageService;

    /**
     * 处理外部联系人变更事件
     * <p>
     * 外部联系人变更包括: 添加外部联系人 (add_external_contact)、
     * 删除外部联系人 (del_external_contact / del_follow_user)、
     * 编辑外部联系人 (edit_external_contact) 等。
     * </p>
     *
     * @param event 回调事件 DTO
     */
    public void handleExternalContactChange(WeworkCallbackEventDto event) {
        String changeType = event.getChangeType();
        String externalUserId = event.getExternalUserId();
        log.info("企业微信外部联系人变更: changeType={}, userId={}, externalUserId={}, timestamp={}",
                changeType, event.getUserId(), externalUserId, event.getTimestamp());

        if (externalUserId == null || externalUserId.isBlank()) {
            log.warn("外部联系人变更事件缺少 externalUserId, 忽略");
            return;
        }

        switch (changeType) {
            case "add_external_contact":
                upsertCustomer(externalUserId);
                break;
            case "del_external_contact":
            case "del_follow_user":
                markCustomerLost(externalUserId);
                break;
            case "edit_external_contact":
                upsertCustomer(externalUserId);
                break;
            default:
                log.info("未处理的外部联系人变更类型: changeType={}", changeType);
                break;
        }
    }

    /**
     * 处理内部通讯录变更事件
     * <p>
     * 通讯录变更包括: 创建成员 (create_user)、更新成员 (update_user)、
     * 删除成员 (delete_user)、部门变更等。
     * </p>
     *
     * @param event 回调事件 DTO
     */
    public void handleContactChange(WeworkCallbackEventDto event) {
        String changeType = event.getChangeType();
        String userId = event.getUserId();
        log.info("企业微信通讯录变更: changeType={}, userId={}, departmentId={}, timestamp={}",
                changeType, userId, event.getDepartmentId(), event.getTimestamp());

        if (userId == null || userId.isBlank()) {
            log.warn("通讯录变更事件缺少 userId, 忽略");
            return;
        }

        switch (changeType) {
            case "create_user":
                createAccount(userId);
                break;
            case "update_user":
                updateAccount(userId);
                break;
            case "delete_user":
                freezeAccount(userId);
                break;
            default:
                log.info("未处理的通讯录变更类型: changeType={}", changeType);
                break;
        }
    }

    /**
     * 处理会话内容审计事件
     * <p>
     * 会话审计事件包括: 进入会话 (enter_chat)、消息审计 (msg_audit) 等。
     * 将回调事件映射为 {@link ConversationEventCallbackDto}, 通过
     * {@link ScrmConversationMessageService#saveMessageFromCallback} 持久化到
     * scrm_conversation_message 表, 实现企微会话消息自动入库。
     * </p>
     * <p>
     * 映射策略:
     * <ul>
     *   <li>platformType = "wework"</li>
     *   <li>accountId = 通过 userId 查找 ScrmAccountEntity</li>
     *   <li>customerId = 通过 externalUserId 查找 ScrmCustomerEntity</li>
     *   <li>content = rawXml (保留原始回调数据, 后续可扩展 XML 解析提取正文)</li>
     *   <li>direction = INBOUND (企微回调默认为入站消息)</li>
     *   <li>messageType = event (事件类型, 后续可扩展按 msgType 细分)</li>
     *   <li>platformMessageId = chatId + timestamp (去重标识)</li>
     *   <li>sentAt = 从 timestamp 字符串解析 (企微时间戳为秒级 Unix)</li>
     * </ul>
     * </p>
     *
     * @param event 回调事件 DTO
     */
    public void handleChatAudit(WeworkCallbackEventDto event) {
        log.info("企业微信会话审计: eventType={}, chatId={}, userId={}, timestamp={}",
                event.getEventType(), event.getChatId(), event.getUserId(), event.getTimestamp());

        try {
            ConversationEventCallbackDto callback = mapToConversationCallback(event);
            if (callback == null) {
                log.warn("[回调同步] 企微会话审计事件映射失败, 跳过持久化: chatId={}, userId={}",
                        event.getChatId(), event.getUserId());
                return;
            }
            conversationMessageService.saveMessageFromCallback(callback);
            log.info("[回调同步] 企微会话审计消息已持久化: chatId={}, userId={}",
                    event.getChatId(), event.getUserId());
        } catch (Exception e) {
            log.error("[回调同步] 企微会话审计消息持久化异常: chatId={}, userId={}, error={}",
                    event.getChatId(), event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * 将企微回调事件映射为会话消息回调 DTO。
     * <p>
     * 通过 userId 查找账号 ID, 通过 externalUserId 查找客户 ID。
     * 若两者都未找到则返回 null (无法关联到已有实体)。
     * </p>
     *
     * @param event 企微回调事件
     * @return 会话消息回调 DTO, 无法映射时返回 null
     */
    private ConversationEventCallbackDto mapToConversationCallback(WeworkCallbackEventDto event) {
        // 解析时间戳 (企微时间戳为秒级 Unix 时间戳字符串)
        LocalDateTime sentAt = parseTimestamp(event.getTimestamp());

        // 通过 userId 查找企微账号 ID
        String accountId = null;
        if (event.getUserId() != null && !event.getUserId().isBlank()) {
            Optional<ScrmAccountEntity> account = accountRepository
                    .findByPlatformTypeAndPlatformAccountUid(PLATFORM_TYPE_WEWORK, event.getUserId());
            if (account.isPresent()) {
                accountId = String.valueOf(account.get().getId());
            }
        }

        // 通过 externalUserId 查找客户 ID
        String customerId = null;
        if (event.getExternalUserId() != null && !event.getExternalUserId().isBlank()) {
            Optional<ScrmCustomerEntity> customer = customerRepository
                    .findByPlatformTypeAndPlatformCustomerUid(PLATFORM_TYPE_WEWORK, event.getExternalUserId());
            if (customer.isPresent()) {
                customerId = String.valueOf(customer.get().getId());
            }
        }

        // 账号和客户都未找到时, 无法关联, 跳过持久化
        if (accountId == null && customerId == null) {
            log.debug("[回调同步] 企微会话审计事件无法关联到账号或客户, 跳过: userId={}, externalUserId={}",
                    event.getUserId(), event.getExternalUserId());
            return null;
        }

        // 构建去重标识: chatId + timestamp
        String platformMessageId = (event.getChatId() != null ? event.getChatId() : "chat")
                + "_" + (event.getTimestamp() != null ? event.getTimestamp() : System.currentTimeMillis());

        ConversationEventCallbackDto callback = new ConversationEventCallbackDto();
        callback.setPlatformType(PLATFORM_TYPE_WEWORK);
        callback.setAccountId(accountId);
        callback.setCustomerId(customerId);
        callback.setMessageType("event");
        callback.setDirection("INBOUND");
        callback.setContent(event.getRawXml());
        callback.setPlatformMessageId(platformMessageId);
        callback.setSentAt(sentAt);
        return callback;
    }

    /**
     * 解析企微时间戳字符串为 LocalDateTime。
     * <p>
     * 企微回调时间戳为秒级 Unix 时间戳, 需转换为 Asia/Shanghai 时区。
     * 解析失败时返回当前时间。
     * </p>
     *
     * @param timestamp 时间戳字符串 (秒级 Unix)
     * @return LocalDateTime, 解析失败返回当前时间
     */
    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            long epochSeconds = Long.parseLong(timestamp.trim());
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of("Asia/Shanghai"));
        } catch (NumberFormatException e) {
            log.debug("[回调同步] 企微时间戳解析失败, 使用当前时间: timestamp={}", timestamp);
            return LocalDateTime.now();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 新增或更新外部联系人到 scrm_customer
     *
     * @param externalUserId 外部联系人 userid
     */
    private void upsertCustomer(String externalUserId) {
        try {
            WeworkExternalContactDto detail = weworkService.getExternalContactDetail(
                    externalUserId);
            if (detail == null) {
                log.warn("[回调同步] 获取外部联系人详情返回 null, 跳过:, externalUserId={}", externalUserId);
                return;
            }
            if (!detail.isSuccess()) {
                log.warn("[回调同步] 获取外部联系人详情失败, 跳过:, externalUserId={}, errorCode={}, errorMessage={}", externalUserId, detail.getErrorCode(), detail.getErrorMessage());
                return;
            }
            Optional<ScrmCustomerEntity> existing = customerRepository
                    .findByPlatformTypeAndPlatformCustomerUid(PLATFORM_TYPE_WEWORK, externalUserId);

            if (existing.isPresent()) {
                ScrmCustomerEntity entity = existing.get();
                boolean changed = false;
                if (detail.getName() != null && !detail.getName().equals(entity.getNickname())) {
                    entity.setNickname(detail.getName());
                    changed = true;
                }
                if (detail.getAvatar() != null && !detail.getAvatar().equals(entity.getAvatarUrl())) {
                    entity.setAvatarUrl(detail.getAvatar());
                    changed = true;
                }
                if (changed) {
                    customerRepository.save(entity);
                    log.info("[回调同步] 更新外部联系人: externalUserId={}", externalUserId);
                }
            } else {
                ScrmCustomerEntity entity = new ScrmCustomerEntity();
                entity.setPlatformType(PLATFORM_TYPE_WEWORK);
                entity.setPlatformCustomerUid(externalUserId);
                entity.setNickname(detail.getName());
                entity.setAvatarUrl(detail.getAvatar());
                // 回调事件中无 accountId 信息，置为 0 由后续手动补充
                entity.setOwnerAccountId(0L);
                entity.setLifecycle(LIFECYCLE_NEW);
                customerRepository.save(entity);
                log.info("[回调同步] 新增外部联系人: externalUserId={}", externalUserId);
            }
        } catch (Exception e) {
            log.error("[回调同步] 同步外部联系人异常: externalUserId={}, error={}",
                    externalUserId, e.getMessage(), e);
        }
    }

    /**
     * 标记外部联系人为流失状态
     *
     * @param externalUserId 外部联系人 userid
     */
    private void markCustomerLost(String externalUserId) {
        try {
            Optional<ScrmCustomerEntity> existing = customerRepository
                    .findByPlatformTypeAndPlatformCustomerUid(PLATFORM_TYPE_WEWORK, externalUserId);
            if (existing.isPresent()) {
                ScrmCustomerEntity entity = existing.get();
                entity.setLifecycle(LIFECYCLE_LOST);
                customerRepository.save(entity);
                log.info("[回调同步] 标记外部联系人流失: externalUserId={}", externalUserId);
            } else {
                log.warn("[回调同步] 标记流失时未找到外部联系人: externalUserId={}", externalUserId);
            }
        } catch (Exception e) {
            log.error("[回调同步] 标记外部联系人流失异常: externalUserId={}, error={}",
                    externalUserId, e.getMessage(), e);
        }
    }

    /**
     * 创建企微成员账号
     *
     * @param userId 企微成员 userid
     */
    private void createAccount(String userId) {
        try {
            Optional<ScrmAccountEntity> existing = accountRepository
                    .findByPlatformTypeAndPlatformAccountUid(PLATFORM_TYPE_WEWORK, userId);
            if (existing.isPresent()) {
                log.info("[回调同步] 企微成员账号已存在, 跳过创建: userId={}", userId);
                return;
            }

            ScrmAccountEntity entity = new ScrmAccountEntity();
            entity.setPlatformType(PLATFORM_TYPE_WEWORK);
            entity.setPlatformAccountUid(userId);
            entity.setLoginState(LOGIN_STATE_LOGIN);

            // 尝试从企微 API 获取详情补充名称和头像
            try {
                WeworkUserDto userInfo = weworkService.getUserDetail(userId);
                if (userInfo == null) {
                    log.warn("[回调同步] 获取企微成员详情返回 null, 使用默认值:, userId={}", userId);
                } else {
                    entity.setDisplayName(userInfo.getNickname());
                    entity.setAvatarUrl(userInfo.getAvatar());
                }
            } catch (Exception e) {
                log.warn("[回调同步] 获取企微成员详情失败, 使用默认值: userId={}, error={}", userId, e.getMessage());
            }

            accountRepository.save(entity);
            log.info("[回调同步] 创建企微成员账号: userId={}", userId);
        } catch (Exception e) {
            log.error("[回调同步] 创建企微成员账号异常: userId={}, error={}", userId, e.getMessage(), e);
        }
    }

    /**
     * 更新企微成员账号信息
     *
     * @param userId 企微成员 userid
     */
    private void updateAccount(String userId) {
        try {
            Optional<ScrmAccountEntity> existing = accountRepository
                    .findByPlatformTypeAndPlatformAccountUid(PLATFORM_TYPE_WEWORK, userId);
            if (!existing.isPresent()) {
                log.warn("[回调同步] 更新企微成员账号时未找到: userId={}", userId);
                return;
            }

            ScrmAccountEntity entity = existing.get();
            try {
                WeworkUserDto userInfo = weworkService.getUserDetail(userId);
                if (userInfo == null) {
                    log.warn("[回调同步] 获取企微成员详情返回 null, 跳过更新:, userId={}", userId);
                } else {
                    boolean changed = false;
                    if (userInfo.getNickname() != null && !userInfo.getNickname().equals(entity.getDisplayName())) {
                        entity.setDisplayName(userInfo.getNickname());
                        changed = true;
                    }
                    if (userInfo.getAvatar() != null && !userInfo.getAvatar().equals(entity.getAvatarUrl())) {
                        entity.setAvatarUrl(userInfo.getAvatar());
                        changed = true;
                    }
                    if (changed) {
                        accountRepository.save(entity);
                        log.info("[回调同步] 更新企微成员账号: userId={}", userId);
                    }
                }
            } catch (Exception e) {
                log.warn("[回调同步] 获取企微成员详情失败: userId={}, error={}", userId, e.getMessage());
            }
        } catch (Exception e) {
            log.error("[回调同步] 更新企微成员账号异常: userId={}, error={}", userId, e.getMessage(), e);
        }
    }

    /**
     * 冻结企微成员账号
     *
     * @param userId 企微成员 userid
     */
    private void freezeAccount(String userId) {
        try {
            Optional<ScrmAccountEntity> existing = accountRepository
                    .findByPlatformTypeAndPlatformAccountUid(PLATFORM_TYPE_WEWORK, userId);
            if (existing.isPresent()) {
                ScrmAccountEntity entity = existing.get();
                entity.setLoginState(LOGIN_STATE_FROZEN);
                accountRepository.save(entity);
                log.info("[回调同步] 冻结企微成员账号: userId={}", userId);
            } else {
                log.warn("[回调同步] 冻结企微成员账号时未找到: userId={}", userId);
            }
        } catch (Exception e) {
            log.error("[回调同步] 冻结企微成员账号异常: userId={}, error={}", userId, e.getMessage(), e);
        }
    }
}
