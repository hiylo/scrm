/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOutboundMessageService.java
 * Date : 2026/10/07 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmAccountDto;
import org.hiylo.scrm.dto.ScrmConversationDto;
import org.hiylo.scrm.dto.ScrmOutboundMessageDto;
import org.hiylo.scrm.dto.callback.OutboundAckCallbackDto;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmOutboundMessageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmOutboundMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SCRM 出站消息队列服务。
 * <p>
 * 决策侧将回复内容写入本队列（{@link #enqueueFromMessage}），执行侧轮询拉取
 * （{@link #takePending} 置 IN_PROGRESS）并 ack 回执（{@link #ack}）。取走超过
 * {@code inProgressTimeoutSeconds} 未回执的消息由 {@link #recoverTimedOut} 退回
 * PENDING 重试。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmOutboundMessageService {

    /** 状态: 待发送 */
    public static final String STATUS_PENDING = "PENDING";

    /** 状态: 发送中 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    /** 状态: 成功 */
    public static final String STATUS_SENT = "SENT";

    /** 状态: 失败 */
    public static final String STATUS_FAILED = "FAILED";

    /** 来源: 自动回复 */
    public static final String SOURCE_AUTO_REPLY = "AUTO_REPLY";

    /** 来源: 人工坐席下发 */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 来源: 营销触达 */
    public static final String SOURCE_CAMPAIGN = "CAMPAIGN";

    /** 默认最大重试次数 */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /** 默认发送中超时秒数（IN_PROGRESS 超时退回 PENDING） */
    public static final int DEFAULT_IN_PROGRESS_TIMEOUT_SECONDS = 120;

    /** 单次拉取上限 */
    private static final int TAKE_BATCH_SIZE = 20;

    /** 出站队列数据访问层 */
    private final ScrmOutboundMessageRepository outboundRepository;

    /** 客户数据访问层（解析目标平台 ID / 昵称） */
    private final ScrmCustomerRepository customerRepository;

    /** 会话服务（解析会话归属账号） */
    private final ScrmConversationService conversationService;

    /** 账号服务（解析平台类型） */
    private final ScrmAccountService accountService;

    // ==================== 入队 ====================

    /**
     * 由已保存的出站消息构建待发送记录并入队。
     * <p>
     * 通过 {@code conversationId + businessMessageId} 防重复入队（同一业务消息只入队一次）。
     * 解析目标平台 ID（个人微信为客户的 platformCustomerUid，即对方 wxid）。
     * </p>
     * <p>
     * 传入消息实体而非仅传 businessMessageId：入队可能在保存消息的同一事务/虚拟线程中
     * 触发，直接使用内存中的实体可避免查询未提交行导致的竞态（找不到刚保存的消息）。
     * </p>
     *
     * @param platformType   平台类型
     * @param source         来源（AUTO_REPLY / MANUAL / CAMPAIGN）
     * @param conversationId 会话 ID
     * @param message        已保存的出站消息实体（须含 messageId / messageType / content）
     * @return 入队记录（重复时返回已存在记录）
     */
    @Transactional(rollbackFor = Exception.class)
    public ScrmOutboundMessageEntity enqueueFromMessage(String platformType, String source,
                                                        Long conversationId,
                                                        ScrmConversationMessageEntity message) {
        String businessMessageId = message.getMessageId();
        // 防重复入队: 同一会话同一业务消息只入队一次
        Optional<ScrmOutboundMessageEntity> existed =
                outboundRepository.findFirstByConversationIdAndBusinessMessageId(
                        conversationId, businessMessageId);
        if (existed.isPresent()) {
            log.debug("出站消息已入队, 跳过: conversationId={}, businessMessageId={}",
                    conversationId, businessMessageId);
            return existed.get();
        }

        ScrmConversationDto conversation = conversationService.getConversation(conversationId);
        if (conversation == null) {
            log.warn("出站消息入队失败: 会话不存在 conversationId={}", conversationId);
            throw ScrmException.badRequest("出站会话不存在: " + conversationId);
        }
        Long accountId = conversation.getAccountId();
        String targetPlatformId = resolveTargetPlatformId(conversation);

        ScrmOutboundMessageEntity entity = new ScrmOutboundMessageEntity();
        entity.setMessageId(message.getId());
        entity.setBusinessMessageId(message.getMessageId());
        entity.setConversationId(conversationId);
        entity.setAccountId(accountId);
        entity.setPlatformType(platformType);
        entity.setTargetPlatformId(targetPlatformId);
        entity.setMessageType(message.getMessageType());
        entity.setContent(message.getContent());
        entity.setSource(source);
        entity.setStatus(STATUS_PENDING);
        entity.setRetryCount(0);
        entity.setMaxRetries(DEFAULT_MAX_RETRIES);
        entity = outboundRepository.save(entity);
        log.info("出站消息入队: id={}, conversationId={}, platform={}, target={}, contentLen={}",
                entity.getId(), conversationId, platformType, targetPlatformId,
                message.getContent() != null ? message.getContent().length() : 0);
        return entity;
    }

    private String resolveTargetPlatformId(ScrmConversationDto conversation) {
        Long customerId = conversation.getCustomerId();
        if (customerId != null) {
            return customerRepository.findById(customerId)
                    .map(ScrmCustomerEntity::getPlatformCustomerUid)
                    .orElse(null);
        }
        return conversation.getPlatformConversationId();
    }

    // ==================== 拉取 ====================

    /**
     * 执行侧拉取待发送消息（PENDING，创建时间升序，最多 {@code limit} 条）。
     * <p>
     * 拉取即原子置为 IN_PROGRESS 并记录 in_progress_at（防止同一条被多个执行侧重复拉取）。
     * 已存在存活 IN_PROGRESS 记录也会一并返回（执行侧崩溃后由超时扫描回收）。
     * </p>
     *
     * @param platformType 平台类型（wechat_personal）
     * @param limit        最大条数（1-20）
     * @return 取走的消息列表（已置 IN_PROGRESS）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<ScrmOutboundMessageDto> takePending(String platformType, int limit) {
        int size = Math.max(1, Math.min(TAKE_BATCH_SIZE, limit));
        LocalDateTime now = LocalDateTime.now();
        List<ScrmOutboundMessageEntity> pending = outboundRepository
                .findByPlatformTypeAndStatusAndNextRetryAtIsNullOrNextRetryAtBeforeOrderByCreateTimeAsc(
                        platformType, STATUS_PENDING, now, PageRequest.of(0, size));
        if (pending.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDateTime inProgressAt = now;
        for (ScrmOutboundMessageEntity entity : pending) {
            entity.setStatus(STATUS_IN_PROGRESS);
            entity.setInProgressAt(inProgressAt);
        }
        List<ScrmOutboundMessageEntity> taken = outboundRepository.saveAll(pending);
        log.info("出站消息拉取: platform={}, 取走 {} 条 (PENDING → IN_PROGRESS)", platformType, taken.size());
        return taken.stream().map(this::toDto).collect(Collectors.toList());
    }

    // ==================== ack ====================

    /**
     * 执行侧回执发送结果（SENT / FAILED）。
     * <p>
     * FAILED 且未超重试上限时退回 PENDING（retry_count+1，next_retry_at 后重试）；
     * 超过上限或 SENT 时进入终态。用乐观锁 version 防止与并发拉取互相覆盖。
     * </p>
     *
     * @param dto ack 回调
     * @throws ScrmException 记录不存在 / 状态非法
     */
    @Transactional(rollbackFor = Exception.class)
    public void ack(OutboundAckCallbackDto dto) throws ScrmException {
        if (dto == null || dto.getOutboundId() == null) {
            throw ScrmException.badRequest("outboundId 不能为空");
        }
        ScrmOutboundMessageEntity entity = outboundRepository.findById(dto.getOutboundId())
                .orElseThrow(() -> ScrmException.notFound("出站消息不存在: id=" + dto.getOutboundId()));

        String status = (dto.getStatus() == null ? "" : dto.getStatus()).toUpperCase();
        if (!STATUS_SENT.equals(status) && !STATUS_FAILED.equals(status)) {
            throw ScrmException.badRequest("非法回执状态: " + status);
        }
        if (STATUS_SENT.equals(status)) {
            entity.setStatus(STATUS_SENT);
            entity.setCompletedAt(LocalDateTime.now());
            entity.setErrorMessage(null);
            entity.setInProgressAt(null);
            outboundRepository.save(entity);
            log.info("出站消息发送成功: id={}, businessMessageId={}", entity.getId(), entity.getBusinessMessageId());
            return;
        }
        // FAILED: 累积重试, 未超上限退回 PENDING（next_retry_at 延迟重试）
        int retryCount = (entity.getRetryCount() == null ? 0 : entity.getRetryCount()) + 1;
        int maxRetries = entity.getMaxRetries() == null ? DEFAULT_MAX_RETRIES : entity.getMaxRetries();
        if (retryCount >= maxRetries) {
            entity.setStatus(STATUS_FAILED);
            entity.setCompletedAt(LocalDateTime.now());
            entity.setErrorMessage(dto.getErrorMessage());
            entity.setInProgressAt(null);
            outboundRepository.save(entity);
            log.warn("出站消息发送失败(终态): id={}, businessMessageId={}, retryCount={}, err={}",
                    entity.getId(), entity.getBusinessMessageId(), retryCount, dto.getErrorMessage());
            return;
        }
        entity.setStatus(STATUS_PENDING);
        entity.setRetryCount(retryCount);
        entity.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds(retryCount)));
        entity.setErrorMessage(dto.getErrorMessage());
        entity.setInProgressAt(null);
        outboundRepository.save(entity);
        log.warn("出站消息发送失败(待重试): id={}, businessMessageId={}, retryCount={}, err={}",
                entity.getId(), entity.getBusinessMessageId(), retryCount, dto.getErrorMessage());
    }

    /** 指数退避: 第 n 次重试延迟 2^(n-1) * 30s（30/60/120…），上限 10 分钟 */
    private long backoffSeconds(int retryCount) {
        long base = 30L;
        for (int i = 1; i < retryCount; i++) {
            base *= 2;
            if (base >= 600) {
                return 600;
            }
        }
        return base;
    }

    // ==================== 超时回收 ====================

    /**
     * 回收超时未回执的 IN_PROGRESS 消息，退回 PENDING（retry_count+1）。
     * <p>
     * 适用于执行侧进程崩溃 / 断网等场景，由定时任务按
     * {@code scrm.outbound.in-progress-timeout-seconds}（默认 120s）周期扫描。
     * </p>
     *
     * @param platformType      平台类型
     * @param timeoutSeconds    超时秒数
     * @return 回收条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int recoverTimedOut(String platformType, int timeoutSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(Math.max(10, timeoutSeconds));
        List<ScrmOutboundMessageEntity> timedOut = outboundRepository
                .findByPlatformTypeAndStatusAndInProgressAtBefore(
                        platformType, STATUS_IN_PROGRESS, threshold, PageRequest.of(0, TAKE_BATCH_SIZE));
        if (timedOut.isEmpty()) {
            return 0;
        }
        for (ScrmOutboundMessageEntity entity : timedOut) {
            int retryCount = (entity.getRetryCount() == null ? 0 : entity.getRetryCount()) + 1;
            if (retryCount >= (entity.getMaxRetries() == null ? DEFAULT_MAX_RETRIES : entity.getMaxRetries())) {
                entity.setStatus(STATUS_FAILED);
                entity.setCompletedAt(LocalDateTime.now());
                entity.setErrorMessage("发送中超时回收，重试次数用尽");
            } else {
                entity.setStatus(STATUS_PENDING);
                entity.setRetryCount(retryCount);
                entity.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds(retryCount)));
                entity.setErrorMessage("发送中超时回收");
            }
            entity.setInProgressAt(null);
        }
        outboundRepository.saveAll(timedOut);
        log.warn("出站消息超时回收: platform={}, 回收 {} 条 (IN_PROGRESS → PENDING/FAILED)",
                platformType, timedOut.size());
        return timedOut.size();
    }

    /** 查询账号在指定平台的 platformAccountUid（供执行侧校验归属） */
    public ScrmAccountDto getAccount(Long accountId) {
        return accountService.getAccount(accountId);
    }

    // ==================== 工具 ====================

    private ScrmOutboundMessageDto toDto(ScrmOutboundMessageEntity entity) {
        ScrmOutboundMessageDto dto = new ScrmOutboundMessageDto();
        dto.setId(entity.getId());
        dto.setBusinessMessageId(entity.getBusinessMessageId());
        dto.setConversationId(entity.getConversationId());
        dto.setAccountId(entity.getAccountId());
        dto.setPlatformType(entity.getPlatformType());
        dto.setTargetPlatformId(entity.getTargetPlatformId());
        dto.setMessageType(entity.getMessageType());
        dto.setContent(entity.getContent());
        dto.setSource(entity.getSource());
        dto.setStatus(entity.getStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}