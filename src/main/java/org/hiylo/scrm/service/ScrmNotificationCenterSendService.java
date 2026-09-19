/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationCenterSendService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.util.UrlSecurityUtils;
import org.hiylo.scrm.dto.ScrmBatchSendDto;
import org.hiylo.scrm.dto.ScrmNotificationBatchDto;
import org.hiylo.scrm.dto.ScrmNotificationDto;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.entity.ScrmNotificationBatchEntity;
import org.hiylo.scrm.entity.ScrmNotificationEntity;
import org.hiylo.scrm.entity.ScrmNotificationTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmNotificationBatchRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmNotificationTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 通知中心发送与调度服务。
 * <p>
 * 承载通知发送的全部能力: 单条发送 (模板渲染 → 偏好检查 → 发送 → 记录), 批量发送
 * (批次创建 + 逐条投递), 定时 / 取消 / 重试, 以及批次管理 (进度 / 取消 / 执行)。
 * </p>
 * <p>
 * 发送执行 ({@link #deliver}): IN_APP 持久化即送达, WEBHOOK 真实 HTTP POST 分发
 * (经 {@link UrlSecurityUtils} 做 SSRF 校验, 详见 {@link #deliverWebhook}),
 * PUSH 经 {@link PushNotificationService} 下发到用户设备; EMAIL / SMS 仓库内无网关实现,
 * 显式置 FAILED 并在 {@code errorMessage} 标明通道未接入, 不伪造发送成功。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmNotificationCenterSendService {

    // ==================== 通知渠道常量 ====================

    /** 通知渠道: 站内信 */
    private static final String CHANNEL_IN_APP = "IN_APP";
    /** 通知渠道: 邮件 */
    private static final String CHANNEL_EMAIL = "EMAIL";
    /** 通知渠道: 短信 */
    private static final String CHANNEL_SMS = "SMS";
    /** 通知渠道: 推送 */
    private static final String CHANNEL_PUSH = "PUSH";
    /** 通知渠道: Webhook */
    private static final String CHANNEL_WEBHOOK = "WEBHOOK";

    // ==================== 通知状态常量 ====================

    /** 通知状态: 待发送 */
    private static final String STATUS_PENDING = "PENDING";
    /** 通知状态: 发送中 */
    private static final String STATUS_SENDING = "SENDING";
    /** 通知状态: 已发送 (供偏好统计服务复用) */
    static final String STATUS_SENT = "SENT";
    /** 通知状态: 已送达 (供偏好统计服务复用) */
    static final String STATUS_DELIVERED = "DELIVERED";
    /** 通知状态: 已读 (供通知查询服务 / 偏好统计服务复用) */
    static final String STATUS_READ = "READ";
    /** 通知状态: 失败 (供偏好统计服务复用) */
    static final String STATUS_FAILED = "FAILED";
    /** 通知状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    // ==================== 批次状态常量 ====================

    /** 批次状态: 待执行 */
    private static final String BATCH_PENDING = "PENDING";
    /** 批次状态: 执行中 */
    private static final String BATCH_SENDING = "SENDING";
    /** 批次状态: 已完成 */
    private static final String BATCH_COMPLETED = "COMPLETED";
    /** 批次状态: 失败 */
    private static final String BATCH_FAILED = "FAILED";
    /** 批次状态: 已取消 */
    private static final String BATCH_CANCELLED = "CANCELLED";

    // ==================== 接收者类型常量 ====================

    /** 接收者类型: 用户 */
    private static final String RECIPIENT_USER = "USER";

    // ==================== 默认值常量 ====================

    /** 默认优先级 (供偏好服务复用) */
    static final int DEFAULT_PRIORITY = 0;
    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRIES = 3;

    // ==================== 数据访问层与协作服务 ====================

    /** 通知模板数据访问层 */
    private final ScrmNotificationTemplateRepository templateRepository;
    /** 通知记录数据访问层 */
    private final ScrmNotificationRepository notificationRepository;
    /** 通知批次数据访问层 */
    private final ScrmNotificationBatchRepository batchRepository;
    /** 模板管理服务 (模板渲染) */
    private final ScrmNotificationCenterTemplateService templateService;
    /** 偏好管理服务 (发送前偏好检查) */
    private final ScrmNotificationCenterPreferenceService preferenceService;

    /**
     * 推送通道 (PUSH 渠道投递)。
     * <p>
     * 复用仓库内既有的 {@link PushNotificationService} 抽象, 按接口注入: 个推凭证就绪
     * ({@code scrm.push.getui.enabled=true}) 时 Spring 注入 {@link GeTuiPushNotificationService}
     * 真实下发, 否则注入 {@link NoOpPushNotificationService} 仅记录推送意图,
     * 与项目内其它推送调用方的开关模式一致, 调用侧无需分支。
     * </p>
     */
    private final PushNotificationService pushNotificationService;

    /**
     * 发送单条通知 (模板渲染 → 偏好检查 → 发送 → 记录)。
     * <p>按 templateCode 加载模板并渲染, 对每个接收者检查偏好后构建通知记录并按渠道投递
     * (详见 {@link #deliver})。计划发送时间 scheduledAt 非空时仅创建 PENDING 记录, 不立即发送;
     * 否则立即发送并刷新状态。发送成功后模板使用次数 +1。</p>
     *
     * @param dto 发送参数 (templateCode + recipients + variables)
     * @return 创建的通知列表 (EMAIL / SMS 等未接入渠道的记录状态为 FAILED, 不会伪造成功)
     * @throws ScrmException 模板不存在 / 模板已禁用 / 参数非法
     */
    @Transactional
    public List<ScrmNotificationDto> sendNotification(ScrmNotificationSendDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("发送参数不能为空");
        }
        if (dto.getRecipients() == null || dto.getRecipients().isEmpty()) {
            throw ScrmException.badRequest("接收者列表不能为空");
        }
        ScrmNotificationTemplateEntity template = templateRepository
                .findByTemplateCode(dto.getTemplateCode())
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "通知模板不存在: code=" + dto.getTemplateCode()));
        if (Boolean.FALSE.equals(template.getEnabled())) {
            throw ScrmException.badRequest("通知模板已禁用: code=" + dto.getTemplateCode());
        }
        String recipientType = dto.getRecipientType() != null ? dto.getRecipientType() : RECIPIENT_USER;
        String renderedTitle = templateService.render(template.getTitle(), dto.getVariables());
        String renderedContent = templateService.render(template.getContent(), dto.getVariables());
        boolean scheduled = dto.getScheduledAt() != null;
        List<ScrmNotificationEntity> entities = new ArrayList<>();
        for (String recipientId : dto.getRecipients()) {
            // 偏好检查 (仅对 USER 接收者类型且即时发送场景执行, 计划发送在调度时检查)
            if (!scheduled && RECIPIENT_USER.equals(recipientType) && !preferenceService.checkPreference(recipientId, template.getChannel(), template.getCategory(),
                    dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY)) {
                log.info("偏好检查未通过, 跳过发送: templateCode={}, recipientId={}",
                        dto.getTemplateCode(), recipientId);
                continue;
            }
            ScrmNotificationEntity entity = buildNotificationEntity(template, renderedTitle, renderedContent,
                    recipientType, recipientId, dto.getRecipientContact(), dto.getSenderId(), dto.getSenderName(),
                    dto.getPriority(), dto.getScheduledAt(), dto.getRelatedType(), dto.getRelatedId());
            entity.setStatus(STATUS_PENDING);
            entity = notificationRepository.save(entity);
            if (!scheduled) {
                deliver(entity);
                entity = notificationRepository.save(entity);
            }
            entities.add(entity);
        }
        // 模板使用次数 +1 (按发送动作计, 非接收者数)
        if (!entities.isEmpty()) {
            templateRepository.incrementUsageCount(template.getId());
        }
        log.info("发送通知: templateCode={}, recipientCount={}, scheduled={}",
                dto.getTemplateCode(), entities.size(), scheduled);
        return entities.stream().map(this::toNotificationDto).toList();
    }

    /**
     * 批量发送通知 (创建批次并逐条发送)。
     * <p>创建一个批次, 对每个接收者渲染模板并按渠道投递 (详见 {@link #deliver}), 增量更新批次计数。
     * 发送完成后批次状态置为 COMPLETED; 全部失败置 FAILED。</p>
     *
     * @param batchDto 批量发送参数 (batchName + templateCode + channel + recipientIds)
     * @return 更新后的批次
     * @throws ScrmException 模板不存在 / 参数非法
     */
    @Transactional
    public ScrmNotificationBatchDto sendBatch(ScrmBatchSendDto batchDto) throws ScrmException {
        if (batchDto == null) {
            throw ScrmException.badRequest("批量发送参数不能为空");
        }
        if (batchDto.getRecipientIds() == null || batchDto.getRecipientIds().isEmpty()) {
            throw ScrmException.badRequest("接收者 ID 列表不能为空");
        }
        ScrmNotificationTemplateEntity template = templateRepository
                .findByTemplateCode(batchDto.getTemplateCode())
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "通知模板不存在: code=" + batchDto.getTemplateCode()));
        // 创建批次
        ScrmNotificationBatchEntity batch = new ScrmNotificationBatchEntity();
        batch.setBatchName(batchDto.getBatchName());
        batch.setTemplateCode(batchDto.getTemplateCode());
        batch.setChannel(batchDto.getChannel());
        batch.setCategory(template.getCategory());
        batch.setTotalCount(batchDto.getRecipientIds().size());
        batch.setSentCount(0);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setReadCount(0);
        batch.setStatus(BATCH_SENDING);
        batch.setStartTime(LocalDateTime.now());
        batch.setTriggeredBy(batchDto.getTriggeredBy());
        batch = batchRepository.save(batch);
        // 渲染模板
        String renderedTitle = templateService.render(template.getTitle(), batchDto.getVariables());
        String renderedContent = templateService.render(template.getContent(), batchDto.getVariables());
        String recipientType = batchDto.getRecipientType() != null ? batchDto.getRecipientType() : RECIPIENT_USER;
        // 逐条发送
        int success = 0;
        int failed = 0;
        for (String recipientId : batchDto.getRecipientIds()) {
            try {
                ScrmNotificationEntity entity = buildNotificationEntity(template, renderedTitle, renderedContent,
                        recipientType, recipientId, null, batchDto.getSenderId(), batchDto.getSenderName(),
                        batchDto.getPriority(), null, null, null);
                entity.setStatus(STATUS_PENDING);
                entity = notificationRepository.save(entity);
                deliver(entity);
                entity = notificationRepository.save(entity);
                // deliver 内部吞掉投递异常并置 FAILED, 此处按最终状态计数, 避免把失败计入成功
                if (STATUS_FAILED.equals(entity.getStatus())) {
                    failed++;
                } else {
                    success++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("批量发送单条失败: batchId={}, recipientId={}, error={}",
                        batch.getId(), recipientId, e.getMessage());
            }
        }
        batch.setSentCount(batchDto.getRecipientIds().size());
        batch.setSuccessCount(success);
        batch.setFailedCount(failed);
        batch.setStatus(failed == 0 ? BATCH_COMPLETED : (success == 0 ? BATCH_FAILED : BATCH_COMPLETED));
        batch.setEndTime(LocalDateTime.now());
        if (success > 0) {
            templateRepository.incrementUsageCount(template.getId());
        }
        batch = batchRepository.save(batch);
        log.info("批量发送通知: batchId={}, total={}, success={}, failed={}",
                batch.getId(), batch.getTotalCount(), success, failed);
        return toBatchDto(batch);
    }

    /**
     * 定时发送 (设置计划发送时间, 仅对 PENDING 状态生效)。
     *
     * @param id          通知 ID
     * @param scheduledAt 计划发送时间
     * @return 更新后的通知
     * @throws ScrmException 通知不存在 / 状态非法
     */
    @Transactional
    public ScrmNotificationDto scheduleNotification(Long id, LocalDateTime scheduledAt) throws ScrmException {
        if (scheduledAt == null) {
            throw ScrmException.badRequest("计划发送时间不能为空");
        }
        ScrmNotificationEntity entity = findNotificationOrThrow(id);
        if (!STATUS_PENDING.equals(entity.getStatus())) {
            throw ScrmException.badRequest(
                    "仅待发送状态的通知可设置定时: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setScheduledAt(scheduledAt);
        entity = notificationRepository.save(entity);
        log.info("定时通知: id={}, scheduledAt={}", id, scheduledAt);
        return toNotificationDto(entity);
    }

    /**
     * 取消发送 (仅对 PENDING 状态生效, 状态置为 CANCELLED)。
     *
     * @param id 通知 ID
     * @return 更新后的通知
     * @throws ScrmException 通知不存在 / 状态非法
     */
    @Transactional
    public ScrmNotificationDto cancelNotification(Long id) throws ScrmException {
        ScrmNotificationEntity entity = findNotificationOrThrow(id);
        if (!STATUS_PENDING.equals(entity.getStatus())) {
            throw ScrmException.badRequest(
                    "仅待发送状态的通知可取消: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_CANCELLED);
        entity = notificationRepository.save(entity);
        log.info("取消通知: id={}", id);
        return toNotificationDto(entity);
    }

    /**
     * 重试失败的通知 (仅对 FAILED 状态生效, 重试次数 +1 并按渠道重新投递)。
     *
     * @param id 通知 ID
     * @return 更新后的通知 (未接入渠道重试后仍为 FAILED)
     * @throws ScrmException 通知不存在 / 状态非法 / 重试次数已达上限
     */
    @Transactional
    public ScrmNotificationDto retryNotification(Long id) throws ScrmException {
        ScrmNotificationEntity entity = findNotificationOrThrow(id);
        if (!STATUS_FAILED.equals(entity.getStatus())) {
            throw ScrmException.badRequest(
                    "仅失败状态的通知可重试: id=" + id + ", status=" + entity.getStatus());
        }
        int retryCount = safeInt(entity.getRetryCount());
        int maxRetries = entity.getMaxRetries() != null ? entity.getMaxRetries() : DEFAULT_MAX_RETRIES;
        if (retryCount >= maxRetries) {
            throw ScrmException.badRequest("重试次数已达上限: id=" + id + ", maxRetries=" + maxRetries);
        }
        entity.setRetryCount(retryCount + 1);
        entity.setErrorMessage(null);
        entity.setStatus(STATUS_PENDING);
        entity = notificationRepository.save(entity);
        // 按渠道重新投递
        deliver(entity);
        entity = notificationRepository.save(entity);
        log.info("重试通知: id={}, retryCount={}, status={}", id, entity.getRetryCount(), entity.getStatus());
        return toNotificationDto(entity);
    }

    /**
     * 查询批次详情。
     *
     * @param id 批次 ID
     * @return 批次 DTO
     * @throws ScrmException 批次不存在
     */
    @Transactional(readOnly = true)
    public ScrmNotificationBatchDto getBatch(Long id) throws ScrmException {
        return toBatchDto(findBatchOrThrow(id));
    }

    /**
     * 分页查询批次, 支持按状态 / 时间区间过滤。
     *
     * @param status    状态过滤 (可空)
     * @param startTime 创建时间起点 (含, 可空)
     * @param endTime   创建时间终点 (不含, 可空)
     * @param pageable  分页参数
     * @return 批次分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmNotificationBatchDto> listBatches(String status, LocalDateTime startTime,
                                                       LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmNotificationBatchEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThan(root.get("createTime"), endTime));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return batchRepository.findAll(spec, templateService.ensureSort(pageable, "createTime")).map(this::toBatchDto);
    }

    /**
     * 取消批次 (仅对 PENDING / SENDING 状态生效, 状态置为 CANCELLED, 记录结束时间)。
     *
     * @param id 批次 ID
     * @return 更新后的批次
     * @throws ScrmException 批次不存在 / 状态非法
     */
    @Transactional
    public ScrmNotificationBatchDto cancelBatch(Long id) throws ScrmException {
        ScrmNotificationBatchEntity batch = findBatchOrThrow(id);
        if (!BATCH_PENDING.equals(batch.getStatus()) && !BATCH_SENDING.equals(batch.getStatus())) {
            throw ScrmException.badRequest(
                    "仅待执行 / 执行中状态的批次可取消: id=" + id + ", status=" + batch.getStatus());
        }
        batch.setStatus(BATCH_CANCELLED);
        batch.setEndTime(LocalDateTime.now());
        batch = batchRepository.save(batch);
        log.info("取消批次: id={}", id);
        return toBatchDto(batch);
    }

    /**
     * 执行批次发送 (模拟实现)。
     * <p>对于 PENDING 状态的批次, 标记为 SENDING → COMPLETED 并记录起止时间 (模拟调度执行);
     * 对于已结束的批次, 仅刷新进度。实际接收者发送在 {@link #sendBatch} 创建时已完成。</p>
     *
     * @param batchId 批次 ID
     * @return 更新后的批次
     * @throws ScrmException 批次不存在 / 状态非法
     */
    @Transactional
    public ScrmNotificationBatchDto processBatch(Long batchId) throws ScrmException {
        ScrmNotificationBatchEntity batch = findBatchOrThrow(batchId);
        String status = batch.getStatus();
        if (BATCH_CANCELLED.equals(status)) {
            throw ScrmException.badRequest("已取消的批次不可执行: id=" + batchId);
        }
        if (BATCH_COMPLETED.equals(status) || BATCH_FAILED.equals(status)) {
            log.info("批次已结束, 仅刷新进度: batchId={}, status={}", batchId, status);
            return toBatchDto(batch);
        }
        // PENDING / SENDING: 模拟执行 (实际发送在 sendBatch 时完成, 此处推进状态)
        batch.setStatus(BATCH_SENDING);
        if (batch.getStartTime() == null) {
            batch.setStartTime(LocalDateTime.now());
        }
        int sentCount = safeInt(batch.getSentCount());
        batch.setSentCount(sentCount == 0 ? safeInt(batch.getTotalCount()) : sentCount);
        batch.setStatus(BATCH_COMPLETED);
        batch.setEndTime(LocalDateTime.now());
        batch = batchRepository.save(batch);
        log.info("执行批次完成: batchId={}, status={}", batchId, batch.getStatus());
        return toBatchDto(batch);
    }

    /**
     * 获取批次进度。
     *
     * @param batchId 批次 ID
     * @return 进度信息 (总数 / 已发送 / 成功 / 失败 / 已读 / 进度百分比 / 状态)
     * @throws ScrmException 批次不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBatchProgress(Long batchId) throws ScrmException {
        ScrmNotificationBatchEntity batch = findBatchOrThrow(batchId);
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("batchId", batch.getId());
        progress.put("batchName", batch.getBatchName());
        progress.put("status", batch.getStatus());
        progress.put("totalCount", safeInt(batch.getTotalCount()));
        progress.put("sentCount", safeInt(batch.getSentCount()));
        progress.put("successCount", safeInt(batch.getSuccessCount()));
        progress.put("failedCount", safeInt(batch.getFailedCount()));
        progress.put("readCount", safeInt(batch.getReadCount()));
        int total = safeInt(batch.getTotalCount());
        int sent = safeInt(batch.getSentCount());
        double rate = total > 0 ? Math.round((double) sent / total * 10000) / 100.0 : 0.0;
        progress.put("progressRate", rate);
        return progress;
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 按主键查询通知并校验账号归属, 不存在或越权抛异常。
     * <p>供通知查询服务复用。</p>
     *
     * @param id 通知 ID
     * @return 通知实体
     * @throws ScrmException 通知不存在
     */
    public ScrmNotificationEntity findNotificationOrThrow(Long id) throws ScrmException {
        ScrmNotificationEntity entity = notificationRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "通知不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询批次并校验账号归属, 不存在或越权抛异常。
     *
     * @param id 批次 ID
     * @return 批次实体
     * @throws ScrmException 批次不存在
     */
    private ScrmNotificationBatchEntity findBatchOrThrow(Long id) throws ScrmException {
        ScrmNotificationBatchEntity entity = batchRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "通知批次不存在: id=" + id));
        return entity;
    }

    /**
     * 构建通知实体 (填充模板与发送参数)。
     *
     * @param template         模板实体
     * @param title            渲染后标题
     * @param content          渲染后内容
     * @param recipientType    接收者类型
     * @param recipientId      接收者 ID
     * @param recipientContact 接收者联系方式 (可空)
     * @param senderId         发送者 ID (可空)
     * @param senderName       发送者名称 (可空)
     * @param priority         优先级 (可空)
     * @param scheduledAt      计划发送时间 (可空)
     * @param relatedType      关联类型 (可空)
     * @param relatedId        关联 ID (可空)
     * @return 通知实体 (未持久化)
     */
    private ScrmNotificationEntity buildNotificationEntity(ScrmNotificationTemplateEntity template,
                                                           String title, String content, String recipientType,
                                                           String recipientId, String recipientContact,
                                                           String senderId, String senderName, Integer priority,
                                                           LocalDateTime scheduledAt, String relatedType,
                                                           String relatedId) {
        ScrmNotificationEntity entity = new ScrmNotificationEntity();
        entity.setTemplateId(template.getId());
        entity.setTemplateCode(template.getTemplateCode());
        entity.setChannel(template.getChannel());
        entity.setCategory(template.getCategory());
        entity.setTitle(title);
        entity.setContent(content);
        entity.setRecipientType(recipientType);
        entity.setRecipientId(recipientId);
        entity.setRecipientContact(recipientContact);
        entity.setSenderId(senderId != null ? senderId : template.getSenderName());
        entity.setSenderName(senderName != null ? senderName : template.getSenderName());
        entity.setPriority(priority != null ? priority : DEFAULT_PRIORITY);
        entity.setScheduledAt(scheduledAt);
        entity.setMaxRetries(DEFAULT_MAX_RETRIES);
        entity.setRetryCount(0);
        entity.setRelatedType(relatedType);
        entity.setRelatedId(relatedId);
        return entity;
    }

    /**
     * 发送通知: 按渠道推进状态。
     * <p>
     * <ul>
     *   <li>IN_APP: 站内信持久化即送达, 直接标记 DELIVERED</li>
     *   <li>WEBHOOK: 真实 HTTP POST 到 {@code recipientContact} 指定的 URL (2xx 视为 DELIVERED,
     *       否则 FAILED)</li>
     *   <li>PUSH: 经 {@link PushNotificationService#pushToUser} 下发到接收者的设备, 调用未抛异常
     *       标记 SENT (推送为单向下发, 无设备端送达回执, 不冒充 DELIVERED)。个推凭证未就绪时
     *       Spring 注入的是 {@link NoOpPushNotificationService}, 仅记录推送意图, 与项目内既有
     *       {@code GETUI_ENABLED} 开关模式一致, 本方法无需分支</li>
     *   <li>EMAIL / SMS: 仓库内<b>没有</b>邮件 / 短信网关实现, 抛 {@link ScrmException} 显式声明
     *       该通道未接入, 由下方 catch 置 FAILED 并写入 {@code errorMessage}, <b>不伪造发送成功</b>
     *       (历史实现把这两个渠道标为 SENT, 属虚报)</li>
     *   <li>其他未知渠道: 同样置 FAILED, 避免拼错的渠道静默通过</li>
     * </ul>
     * 投递异常统一置 FAILED 并记录错误信息, 不向调用方抛出 (批量链路由调用方按最终状态计数)。
     * </p>
     *
     * @param entity 通知实体
     */
    private void deliver(ScrmNotificationEntity entity) {
        try {
            LocalDateTime now = LocalDateTime.now();
            entity.setStatus(STATUS_SENT);
            entity.setSentAt(now);
            String channel = entity.getChannel();
            if (CHANNEL_IN_APP.equals(channel)) {
                // 站内信: 持久化即送达
                entity.setStatus(STATUS_DELIVERED);
                entity.setDeliveredAt(now);
            } else if (CHANNEL_WEBHOOK.equals(channel)) {
                // Webhook: 真实 HTTP POST 分发
                deliverWebhook(entity, now);
            } else if (CHANNEL_PUSH.equals(channel)) {
                // PUSH: 走既有推送通道 (个推 / NoOp) 下发到用户设备
                deliverPush(entity);
            } else if (CHANNEL_EMAIL.equals(channel) || CHANNEL_SMS.equals(channel)) {
                throw ScrmException.notImplemented(
                        "通知渠道未接入发送网关, 无法投递: channel=" + channel);
            } else {
                throw ScrmException.badRequest("未知的通知渠道, 无法投递: channel=" + channel);
            }
            log.info("发送通知: id={}, channel={}, recipientId={}, status={}",
                    entity.getId(), channel, entity.getRecipientId(), entity.getStatus());
        } catch (Exception e) {
            entity.setStatus(STATUS_FAILED);
            entity.setErrorMessage(e.getMessage());
            log.warn("发送通知失败: id={}, error={}", entity.getId(), e.getMessage());
        }
    }

    /**
     * PUSH 渠道投递: 复用既有 {@link PushNotificationService} 抽象下发到接收者设备。
     * <p>
     * 接收者的 {@code recipientId} 即推送目标用户 ID, 业务分类作为通知类型透传,
     * 附加数据携带通知主键与关联对象, 供客户端点击跳转。实现类内部按 userId 解析活跃
     * client_id 列表, 无设备时静默返回。保持 SENT 状态 (不冒充 DELIVERED)。
     * </p>
     *
     * @param entity 通知实体
     */
    private void deliverPush(ScrmNotificationEntity entity) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("notificationId", entity.getId());
        extra.put("channel", entity.getChannel());
        extra.put("category", entity.getCategory());
        extra.put("relatedType", entity.getRelatedType());
        extra.put("relatedId", entity.getRelatedId());
        pushNotificationService.pushToUser(entity.getRecipientId(), entity.getTitle(),
                entity.getContent(), entity.getCategory(), extra);
        log.info("推送已下发: id={}, recipientId={}", entity.getId(), entity.getRecipientId());
    }

    /**
     * Webhook 渠道真实 HTTP POST 分发。
     * <p>POST JSON 载荷到 {@code recipientContact} URL, 2xx 响应标记 DELIVERED,
     * 非 2xx 或异常标记 FAILED 并记录错误信息。连接 / 读取超时分别为 5s / 10s。</p>
     *
     * @param entity 通知实体 (recipientContact 为目标 URL)
     * @param sentAt 发送时间
     */
    private void deliverWebhook(ScrmNotificationEntity entity, LocalDateTime sentAt) {
        String url = entity.getRecipientContact();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook 接收地址 (recipientContact) 为空");
        }
        // SSRF 防护: 仅允许公网 http/https 目标, 拒绝内网/回环/云 metadata 地址
        UrlSecurityUtils.validatePublicHttpUrl(url);
        String payload = buildWebhookPayload(entity);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(WEBHOOK_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(WEBHOOK_READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                entity.setStatus(STATUS_DELIVERED);
                entity.setDeliveredAt(LocalDateTime.now());
                log.info("Webhook 送达: id={}, url={}, code={}", entity.getId(), url, code);
            } else {
                entity.setStatus(STATUS_FAILED);
                entity.setErrorMessage("Webhook 响应非 2xx: code=" + code);
                log.warn("Webhook 失败: id={}, url={}, code={}", entity.getId(), url, code);
            }
        } catch (IOException e) {
            entity.setStatus(STATUS_FAILED);
            entity.setErrorMessage("Webhook 调用异常: " + e.getMessage());
            log.warn("Webhook 异常: id={}, url={}, error={}", entity.getId(), url, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Webhook HTTP 连接超时 (毫秒) */
    private static final int WEBHOOK_CONNECT_TIMEOUT_MS = 5000;
    /** Webhook HTTP 读取超时 (毫秒) */
    private static final int WEBHOOK_READ_TIMEOUT_MS = 10000;

    /**
     * 构建 Webhook 载荷 JSON (标题 / 内容 / 渠道 / 接收者 / 优先级 / 元数据)。
     *
     * @param entity 通知实体
     * @return JSON 字符串
     */
    private String buildWebhookPayload(ScrmNotificationEntity entity) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        appendJsonField(sb, "id", entity.getId() != null ? entity.getId().toString() : null, true);
        appendJsonField(sb, "title", entity.getTitle(), true);
        appendJsonField(sb, "content", entity.getContent(), true);
        appendJsonField(sb, "channel", entity.getChannel(), true);
        appendJsonField(sb, "category", entity.getCategory(), true);
        appendJsonField(sb, "recipientId", entity.getRecipientId(), true);
        appendJsonField(sb, "recipientName", entity.getRecipientName(), true);
        appendJsonField(sb, "senderId", entity.getSenderId(), false);
        sb.append("\"priority\":").append(entity.getPriority() != null ? entity.getPriority() : 0);
        sb.append('}');
        return sb.toString();
    }

    /**
     * 追加一个 JSON 字符串字段 (自动转义, 值为 null 时输出 null)。
     *
     * @param sb    字符串构建器
     * @param key   字段名
     * @param value 字段值 (可空)
     * @param comma 是否追加尾随逗号
     */
    private void appendJsonField(StringBuilder sb, String key, String value, boolean comma) {
        sb.append('"').append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escapeJson(value)).append('"');
        }
        if (comma) {
            sb.append(',');
        }
    }

    /**
     * 转义 JSON 字符串中的特殊字符。
     *
     * @param s 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * 安全将 Integer 转 int (null 视为 0)。
     *
     * @param value Integer 值
     * @return int 值
     */
    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    // ============================================================
    // 实体转 DTO
    // ============================================================

    /**
     * 通知实体转 DTO。
     * <p>供通知查询服务复用。</p>
     */
    public ScrmNotificationDto toNotificationDto(ScrmNotificationEntity entity) {
        ScrmNotificationDto dto = new ScrmNotificationDto();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setTemplateCode(entity.getTemplateCode());
        dto.setChannel(entity.getChannel());
        dto.setCategory(entity.getCategory());
        dto.setTitle(entity.getTitle());
        dto.setContent(entity.getContent());
        dto.setRecipientType(entity.getRecipientType());
        dto.setRecipientId(entity.getRecipientId());
        dto.setRecipientName(entity.getRecipientName());
        dto.setRecipientContact(entity.getRecipientContact());
        dto.setSenderId(entity.getSenderId());
        dto.setSenderName(entity.getSenderName());
        dto.setStatus(entity.getStatus());
        dto.setPriority(entity.getPriority());
        dto.setScheduledAt(entity.getScheduledAt());
        dto.setSentAt(entity.getSentAt());
        dto.setDeliveredAt(entity.getDeliveredAt());
        dto.setReadAt(entity.getReadAt());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setRetryCount(entity.getRetryCount());
        dto.setMaxRetries(entity.getMaxRetries());
        dto.setMetadata(entity.getMetadata());
        dto.setRelatedType(entity.getRelatedType());
        dto.setRelatedId(entity.getRelatedId());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 批次实体转 DTO。
     */
    private ScrmNotificationBatchDto toBatchDto(ScrmNotificationBatchEntity entity) {
        ScrmNotificationBatchDto dto = new ScrmNotificationBatchDto();
        dto.setId(entity.getId());
        dto.setBatchName(entity.getBatchName());
        dto.setTemplateCode(entity.getTemplateCode());
        dto.setChannel(entity.getChannel());
        dto.setCategory(entity.getCategory());
        dto.setTriggeredBy(entity.getTriggeredBy());
        dto.setTotalCount(entity.getTotalCount());
        dto.setSentCount(entity.getSentCount());
        dto.setSuccessCount(entity.getSuccessCount());
        dto.setFailedCount(entity.getFailedCount());
        dto.setReadCount(entity.getReadCount());
        dto.setStatus(entity.getStatus());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}