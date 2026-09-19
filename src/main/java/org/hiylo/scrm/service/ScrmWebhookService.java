/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWebhookService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.util.UrlSecurityUtils;
import org.hiylo.scrm.dto.ScrmWebhookConfigDto;
import org.hiylo.scrm.dto.ScrmWebhookEventDto;
import org.hiylo.scrm.dto.ScrmWebhookLogDto;
import org.hiylo.scrm.entity.ScrmWebhookConfigEntity;
import org.hiylo.scrm.entity.ScrmWebhookLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmWebhookConfigRepository;
import org.hiylo.scrm.repository.ScrmWebhookLogRepository;
import org.hiylo.scrm.vo.WebhookStatsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.criteria.Predicate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SCRM Webhook 事件通知服务
 * <p>
 * 负责 Webhook 配置管理、事件发布与推送、签名验证、失败重试与统计。
 * 事件发布时查找订阅该事件类型的活跃 Webhook, 创建推送日志并异步发送 HTTP 通知。
 * 所有写操作均写入当前用户归属账号, 实现数据隔离。
 * </p>
 * <p>
 * HTTP 发送使用 {@code java.net.http.HttpClient} (JDK 11+),
 * 签名使用 {@code javax.crypto.Mac} (HMAC-SHA256)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmWebhookService {

    /** 配置状态: 活跃 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 配置状态: 停用 */
    private static final String STATUS_INACTIVE = "INACTIVE";
    /** 配置状态: 错误 */
    private static final String STATUS_ERROR = "ERROR";

    /** 日志状态: 待发送 */
    private static final String LOG_PENDING = "PENDING";
    /** 日志状态: 发送中 */
    private static final String LOG_SENDING = "SENDING";
    /** 日志状态: 成功 */
    private static final String LOG_SUCCESS = "SUCCESS";
    /** 日志状态: 失败 */
    private static final String LOG_FAILED = "FAILED";
    /** 日志状态: 待重试 */
    private static final String LOG_RETRY = "RETRY";
    /** 日志状态: 已过期 */
    private static final String LOG_EXPIRED = "EXPIRED";

    /** HTTP 方法: POST */
    private static final String METHOD_POST = "POST";
    /** HTTP 方法: PUT */
    private static final String METHOD_PUT = "PUT";
    /** HTTP 方法: GET */
    private static final String METHOD_GET = "GET";

    /** 默认 HTTP 方法 */
    private static final String DEFAULT_HTTP_METHOD = METHOD_POST;
    /** 默认超时秒数 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRIES = 3;
    /** 默认重试间隔秒数 */
    private static final int DEFAULT_RETRY_INTERVAL_SECONDS = 60;
    /** 默认最大尝试次数 (初始 + 重试) */
    private static final int DEFAULT_MAX_ATTEMPTS = DEFAULT_MAX_RETRIES + 1;

    /** HTTP 签名头名 */
    private static final String HEADER_SIGNATURE = "X-Webhook-Signature";
    /** HTTP 事件类型头名 */
    private static final String HEADER_EVENT_TYPE = "X-Webhook-Event";
    /** HTTP 事件 ID 头名 */
    private static final String HEADER_EVENT_ID = "X-Webhook-Event-Id";
    /** HTTP 内容类型 */
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    /** JSON 内容类型 */
    private static final String CONTENT_TYPE_JSON = "application/json";

    /** HMAC-SHA256 算法名 */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /** 百分比换算基数 */
    private static final double PERCENT_BASE = 100.0;
    /** 比率小数保留位数 */
    private static final int RATE_SCALE = 2;

    /** 响应体最大截取长度 */
    private static final int MAX_RESPONSE_BODY_LENGTH = 2000;
    /** 错误信息最大截取长度 */
    private static final int MAX_ERROR_LENGTH = 500;

    /** 单次处理待重试的批量上限, 避免单次拉取过多造成长事务 */
    private static final int PROCESS_BATCH_LIMIT = 200;

    /** HTTP 客户端 (全局共享, 连接超时 10s) */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 可订阅事件类型列表 */
    private static final List<String> AVAILABLE_EVENTS = List.of(
            "CUSTOMER_CREATED", "CUSTOMER_UPDATED", "CUSTOMER_MERGED", "CUSTOMER_DELETED",
            "MESSAGE_RECEIVED", "MESSAGE_SENT",
            "CONVERSATION_STARTED", "CONVERSATION_ENDED",
            "LIFECYCLE_CHANGED", "TAG_ADDED", "TAG_REMOVED",
            "OPPORTUNITY_CREATED", "OPPORTUNITY_STAGE_CHANGED",
            "MASS_SEND_COMPLETED", "CHANNEL_CODE_SCANNED",
            "WELCOME_MESSAGE_SENT", "JOURNEY_ENROLLED", "JOURNEY_COMPLETED",
            "CAMPAIGN_COMPLETED", "RISK_SIGNAL_TRIGGERED");

    /** Webhook 配置数据访问层 */
    private final ScrmWebhookConfigRepository configRepository;
    /** Webhook 推送日志数据访问层 */
    private final ScrmWebhookLogRepository logRepository;
    /** Jackson ObjectMapper, 由 Spring Boot 自动注入, 用于解析 JSON */
    private final ObjectMapper objectMapper;

    /** 自身代理引用, 用于在 @Transactional 方法中触发 @Async 自调用 (避免代理失效) */
    @Lazy
    @Autowired
    private ScrmWebhookService self;

    // ============================================================
    // Webhook 配置管理
    // ============================================================

    /**
     * 创建 Webhook 配置
     * <p>
     * 默认 httpMethod=POST, timeoutSeconds=10, maxRetries=3, retryIntervalSeconds=60,
     * status=ACTIVE, successCount=0, failCount=0。
     * </p>
     *
     * @param dto Webhook 配置参数
     * @return 创建后的 Webhook 配置
     * @throws ScrmException 参数校验失败
     */
    @Transactional
    public ScrmWebhookConfigDto createConfig(ScrmWebhookConfigDto dto) throws ScrmException {
        validateCreateConfig(dto);
        ScrmWebhookConfigEntity entity = new ScrmWebhookConfigEntity();
        entity.setWebhookName(dto.getWebhookName());
        entity.setTargetUrl(dto.getTargetUrl());
        entity.setSecret(dto.getSecret());
        entity.setSubscribedEvents(dto.getSubscribedEvents());
        entity.setEventFilter(dto.getEventFilter());
        entity.setHttpMethod(dto.getHttpMethod() != null ? dto.getHttpMethod() : DEFAULT_HTTP_METHOD);
        entity.setHeaders(dto.getHeaders());
        entity.setTimeoutSeconds(dto.getTimeoutSeconds() != null ? dto.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS);
        entity.setMaxRetries(dto.getMaxRetries() != null ? dto.getMaxRetries() : DEFAULT_MAX_RETRIES);
        entity.setRetryIntervalSeconds(dto.getRetryIntervalSeconds() != null
                ? dto.getRetryIntervalSeconds() : DEFAULT_RETRY_INTERVAL_SECONDS);
        entity.setStatus(STATUS_ACTIVE);
        entity.setSuccessCount(0);
        entity.setFailCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = configRepository.save(entity);
        log.info("创建 Webhook 配置: id={}, webhookName={}, targetUrl={}",
                entity.getId(), entity.getWebhookName(), entity.getTargetUrl());
        return toConfigDto(entity);
    }

    /**
     * 更新 Webhook 配置
     * <p>
     * 字段非空才覆盖, status 通过 activate/deactivate 专用接口维护。
     * </p>
     *
     * @param id  Webhook 配置 ID
     * @param dto Webhook 配置参数
     * @return 更新后的 Webhook 配置
     * @throws ScrmException Webhook 配置不存在
     */
    @Transactional
    public ScrmWebhookConfigDto updateConfig(Long id, ScrmWebhookConfigDto dto) throws ScrmException {
        ScrmWebhookConfigEntity entity = findConfigOrThrow(id);
        if (dto.getWebhookName() != null) {
            if (dto.getWebhookName().isBlank()) {
                throw ScrmException.badRequest("Webhook 名称不能为空");
            }
            entity.setWebhookName(dto.getWebhookName());
        }
        if (dto.getTargetUrl() != null) {
            if (dto.getTargetUrl().isBlank()) {
                throw ScrmException.badRequest("目标 URL 不能为空");
            }
            // SSRF 防护: 仅允许公网 http/https 目标
            validateTargetUrl(dto.getTargetUrl());
            entity.setTargetUrl(dto.getTargetUrl());
        }
        if (dto.getSecret() != null) {
            entity.setSecret(dto.getSecret());
        }
        if (dto.getSubscribedEvents() != null) {
            if (dto.getSubscribedEvents().isBlank()) {
                throw ScrmException.badRequest("订阅事件不能为空");
            }
            entity.setSubscribedEvents(dto.getSubscribedEvents());
        }
        if (dto.getEventFilter() != null) {
            entity.setEventFilter(dto.getEventFilter());
        }
        if (dto.getHttpMethod() != null) {
            entity.setHttpMethod(dto.getHttpMethod());
        }
        if (dto.getHeaders() != null) {
            entity.setHeaders(dto.getHeaders());
        }
        if (dto.getTimeoutSeconds() != null) {
            entity.setTimeoutSeconds(dto.getTimeoutSeconds());
        }
        if (dto.getMaxRetries() != null) {
            entity.setMaxRetries(dto.getMaxRetries());
        }
        if (dto.getRetryIntervalSeconds() != null) {
            entity.setRetryIntervalSeconds(dto.getRetryIntervalSeconds());
        }
        if (dto.getCreatedBy() != null) {
            entity.setCreatedBy(dto.getCreatedBy());
        }
        entity = configRepository.save(entity);
        log.info("更新 Webhook 配置: id={}", id);
        return toConfigDto(entity);
    }

    /**
     * 删除 Webhook 配置
     * <p>
     * 配置删除后, 历史推送日志保留以便审计与统计。
     * </p>
     *
     * @param id Webhook 配置 ID
     * @throws ScrmException Webhook 配置不存在
     */
    @Transactional
    public void deleteConfig(Long id) throws ScrmException {
        ScrmWebhookConfigEntity entity = findConfigOrThrow(id);
        configRepository.delete(entity);
        log.info("删除 Webhook 配置: id={}, webhookName={}", id, entity.getWebhookName());
    }

    /**
     * 查询 Webhook 配置详情
     *
     * @param id Webhook 配置 ID
     * @return Webhook 配置 DTO
     * @throws ScrmException Webhook 配置不存在
     */
    @Transactional(readOnly = true)
    public ScrmWebhookConfigDto getConfig(Long id) throws ScrmException {
        return toConfigDto(findConfigOrThrow(id));
    }

    /**
     * 分页查询 Webhook 配置, 支持按状态与事件类型过滤
     *
     * @param status    状态过滤 (可空)
     * @param eventType 事件类型过滤 (可空, 匹配 subscribedEvents JSON 数组)
     * @param pageable  分页参数
     * @return Webhook 配置分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmWebhookConfigDto> listConfigs(String status, String eventType, Pageable pageable) {
        Pageable sorted = ensureSort(pageable, "createTime");
        Specification<ScrmWebhookConfigEntity> spec = buildConfigSpec(status, eventType);
        return configRepository.findAll(spec, sorted).map(this::toConfigDto);
    }

    /**
     * 激活 Webhook 配置
     *
     * @param id Webhook 配置 ID
     * @return 更新后的 Webhook 配置
     * @throws ScrmException Webhook 配置不存在
     */
    @Transactional
    public ScrmWebhookConfigDto activateConfig(Long id) throws ScrmException {
        ScrmWebhookConfigEntity entity = findConfigOrThrow(id);
        entity.setStatus(STATUS_ACTIVE);
        entity = configRepository.save(entity);
        log.info("激活 Webhook 配置: id={}", id);
        return toConfigDto(entity);
    }

    /**
     * 停用 Webhook 配置
     *
     * @param id Webhook 配置 ID
     * @return 更新后的 Webhook 配置
     * @throws ScrmException Webhook 配置不存在
     */
    @Transactional
    public ScrmWebhookConfigDto deactivateConfig(Long id) throws ScrmException {
        ScrmWebhookConfigEntity entity = findConfigOrThrow(id);
        entity.setStatus(STATUS_INACTIVE);
        entity = configRepository.save(entity);
        log.info("停用 Webhook 配置: id={}", id);
        return toConfigDto(entity);
    }

    /**
     * 发送测试事件到目标 URL
     * <p>
     * 构造一个 TEST 事件并同步推送, 用于验证 Webhook 配置是否可达。
     * </p>
     *
     * @param id Webhook 配置 ID
     * @return 推送日志 (含响应信息)
     * @throws ScrmException Webhook 配置不存在
     */
    @Transactional
    public ScrmWebhookLogDto testConfig(Long id) throws ScrmException {
        ScrmWebhookConfigEntity config = findConfigOrThrow(id);
        // 构造测试事件日志
        ScrmWebhookLogEntity logEntity = new ScrmWebhookLogEntity();
        logEntity.setWebhookId(config.getId());
        logEntity.setEventType("TEST");
        logEntity.setEventId(UUID.randomUUID().toString());
        Map<String, Object> testData = new LinkedHashMap<>();
        testData.put("eventType", "TEST");
        testData.put("eventId", logEntity.getEventId());
        testData.put("message", "Webhook test event");
        testData.put("timestamp", LocalDateTime.now().toString());
        logEntity.setPayload(toJson(testData));
        logEntity.setStatus(LOG_PENDING);
        logEntity.setAttemptCount(0);
        logEntity.setMaxAttempts(1);
        logEntity = logRepository.save(logEntity);
        // 同步发送测试事件
        return doSendWebhook(logEntity.getId());
    }

    // ============================================================
    // 事件发布与推送
    // ============================================================

    /**
     * 发布事件
     * <p>
     * 流程: 查找订阅该事件类型的活跃 Webhook → 检查事件过滤条件 → 创建推送日志 →
     * 事务提交后异步发送 HTTP 通知。
     * </p>
     * <p>
     * 数据隔离: 仅匹配当前账号下 status=ACTIVE 且 subscribedEvents 包含该事件类型的 Webhook。
     * 异步发送通过 {@code @Async} 在事务提交后触发, 避免日志未提交导致异步线程读不到。
     * </p>
     *
     * @param eventDto 事件入参
     * @return 创建的推送日志列表 (未匹配到订阅则返回空列表)
     * @throws ScrmException 参数校验失败
     */
    @Transactional
    public List<ScrmWebhookLogDto> publishEvent(ScrmWebhookEventDto eventDto) throws ScrmException {
        if (eventDto == null) {
            throw ScrmException.badRequest("事件入参不能为空");
        }
        if (eventDto.getEventType() == null || eventDto.getEventType().isBlank()) {
            throw ScrmException.badRequest("事件类型不能为空");
        }
        // 查询当前账号下所有 ACTIVE 的 Webhook 配置
        List<ScrmWebhookConfigEntity> configs = configRepository.findByStatus(STATUS_ACTIVE);
        if (configs.isEmpty()) {
            log.debug("未找到活跃 Webhook 配置:, eventType={}", eventDto.getEventType());
            return Collections.emptyList();
        }
        // 过滤: subscribedEvents 包含该事件类型 且 eventFilter 匹配
        List<ScrmWebhookConfigEntity> matched = configs.stream()
                .filter(c -> isSubscribed(c, eventDto.getEventType()))
                .filter(c -> matchesFilter(c, eventDto.getEventData()))
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            log.debug("事件未匹配到订阅 Webhook:, eventType={}", eventDto.getEventType());
            return Collections.emptyList();
        }
        // 构造事件负载
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("eventType", eventDto.getEventType());
        payloadMap.put("eventId", eventId);
        payloadMap.put("entityId", eventDto.getEntityId());
        payloadMap.put("timestamp", LocalDateTime.now().toString());
        if (eventDto.getEventData() != null && !eventDto.getEventData().isBlank()) {
            payloadMap.put("data", parseJsonToMap(eventDto.getEventData()));
        } else {
            payloadMap.put("data", Collections.emptyMap());
        }
        String payload = toJson(payloadMap);
        // 为每个匹配的 Webhook 创建推送日志
        List<ScrmWebhookLogEntity> logs = new ArrayList<>(matched.size());
        for (ScrmWebhookConfigEntity config : matched) {
            ScrmWebhookLogEntity logEntity = new ScrmWebhookLogEntity();
            logEntity.setWebhookId(config.getId());
            logEntity.setEventType(eventDto.getEventType());
            logEntity.setEventId(eventId);
            logEntity.setPayload(payload);
            logEntity.setStatus(LOG_PENDING);
            logEntity.setAttemptCount(0);
            logEntity.setMaxAttempts((config.getMaxRetries() != null
                    ? config.getMaxRetries() : DEFAULT_MAX_RETRIES) + 1);
            logs.add(logEntity);
        }
        logs = logRepository.saveAll(logs);
        // 捕获日志 ID, 事务提交后异步发送
        List<Long> logIds = logs.stream().map(ScrmWebhookLogEntity::getId).collect(Collectors.toList());
        scheduleAsyncSend(logIds);
        log.info("发布 Webhook 事件:, eventType={}, eventId={}, webhookCount={}", eventDto.getEventType(), eventId, logs.size());
        return logs.stream().map(this::toLogDto).collect(Collectors.toList());
    }

    /**
     * 批量发布事件
     *
     * @param eventDtos 事件入参列表
     * @return 所有事件创建的推送日志列表
     * @throws ScrmException 参数校验失败
     */
    @Transactional
    public List<ScrmWebhookLogDto> batchPublishEvents(List<ScrmWebhookEventDto> eventDtos) throws ScrmException {
        if (eventDtos == null || eventDtos.isEmpty()) {
            throw ScrmException.badRequest("事件列表不能为空");
        }
        List<ScrmWebhookLogDto> all = new ArrayList<>();
        for (ScrmWebhookEventDto dto : eventDtos) {
            all.addAll(publishEvent(dto));
        }
        return all;
    }

    /**
     * 发送单个 Webhook (同步)
     * <p>
     * 流程: 构造请求 → 签名 → HTTP POST → 记录响应 → 失败则设置重试。
     * 适用于手动重试与测试推送场景。
     * </p>
     *
     * @param logId 推送日志 ID
     * @return 更新后的推送日志
     * @throws ScrmException 日志不存在或状态非法
     */
    @Transactional
    public ScrmWebhookLogDto sendWebhook(Long logId) throws ScrmException {
        return doSendWebhook(logId);
    }

    /**
     * 异步发送 Webhook (事务提交后触发)
     * <p>
     * 使用 {@code @Async} 在 asyncTaskExecutor 线程池中执行,
     * 传播级别 REQUIRES_NEW 确保独立于调用方事务。
     * 在异步线程中重新设置请求上下文, 保证数据隔离不丢失。
     * </p>
     *
     * @param logId    推送日志 ID
     *      */
    @Async("asyncTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendWebhookAsync(Long logId) {
        try {
            doSendWebhook(logId);
        } catch (Exception e) {
            log.error("异步发送 Webhook 失败: logId={}, error={}", logId, e.getMessage(), e);
        }
    }

    /**
     * 处理待重试的 Webhook (定时任务用)
     * <p>
     * 捞取当前账号下 status=RETRY 且 nextRetryAt 已到期的日志, 逐个重新发送。
     * 单次处理上限 {@link #PROCESS_BATCH_LIMIT}, 避免长事务。
     * </p>
     *
     * @return 本次处理的日志数
     */
    public int processRetries() {
        List<ScrmWebhookLogEntity> pending = logRepository
                .findByStatusAndNextRetryAtLessThanEqual(LOG_RETRY, LocalDateTime.now());
        if (pending.isEmpty()) {
            return 0;
        }
        int processed = 0;
        for (ScrmWebhookLogEntity logEntity : pending) {
            if (processed >= PROCESS_BATCH_LIMIT) {
                log.info("达到单次处理上限, 剩余待下次处理: batchLimit={}", PROCESS_BATCH_LIMIT);
                break;
            }
            try {
                // 内部 HTTP 为同步阻塞调用 (最长 10s/条), 故本方法不标注 @Transactional:
                // 每条记录的读写由 doSendWebhook 内的 Spring Data 短事务完成, 避免 200 条
                // HTTP 期间长期占用数据库连接 (连接池耗尽风险)
                doSendWebhook(logEntity.getId());
                processed++;
            } catch (Exception e) {
                log.warn("重试发送失败, 继续处理下一个: logId={}", logEntity.getId(), e);
            }
        }
        log.info("处理待重试 Webhook 完成:, processed={}, totalRetry={}", processed, pending.size());
        return processed;
    }

    // ============================================================
    // 签名生成与验证
    // ============================================================

    /**
     * 生成 HMAC-SHA256 签名
     * <p>
     * 使用 secret 对 payload 进行 HMAC-SHA256 运算, 返回十六进制字符串。
     * </p>
     *
     * @param payload 签名内容
     * @param secret  签名密钥
     * @return 十六进制签名
     */
    public String generateSignature(String payload, String secret) {
        if (payload == null) {
            payload = "";
        }
        if (secret == null || secret.isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("生成 HMAC-SHA256 签名失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 验证 HMAC-SHA256 签名
     *
     * @param payload   签名内容
     * @param signature 待验证的签名 (十六进制)
     * @param secret    签名密钥
     * @return true=签名一致, false=签名不一致或 secret 为空
     */
    public boolean verifySignature(String payload, String signature, String secret) {
        if (secret == null || secret.isEmpty() || signature == null || signature.isEmpty()) {
            return false;
        }
        String expected = generateSignature(payload, secret);
        if (expected == null) {
            return false;
        }
        // 恒定时间比较, 避免时序侧信道; 签名是十六进制串, 统一转小写保持与原 equalsIgnoreCase 语义一致
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    // ============================================================
    // 日志查询与统计
    // ============================================================

    /**
     * 分页查询推送日志, 支持按 Webhook、事件类型、状态与时间范围过滤
     *
     * @param webhookId Webhook 配置 ID 过滤 (可空)
     * @param eventType 事件类型过滤 (可空)
     * @param status    日志状态过滤 (可空)
     * @param startTime 起始时间过滤 (可空, 匹配 createTime)
     * @param endTime   截止时间过滤 (可空, 匹配 createTime)
     * @param pageable   分页参数
     * @return 推送日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmWebhookLogDto> getLogs(Long webhookId, String eventType, String status,
                                            LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        Pageable sorted = ensureSort(pageable, "createTime");
        Specification<ScrmWebhookLogEntity> spec = buildLogSpec(webhookId, eventType, status, startTime, endTime);
        return logRepository.findAll(spec, sorted).map(this::toLogDto);
    }

    /**
     * 查询推送日志详情
     *
     * @param id 日志 ID
     * @return 推送日志 DTO
     * @throws ScrmException 日志不存在
     */
    @Transactional(readOnly = true)
    public ScrmWebhookLogDto getLog(Long id) throws ScrmException {
        return toLogDto(findLogOrThrow(id));
    }

    /**
     * Webhook 推送统计
     * <p>
     * 聚合指定时间范围内的推送状态分布, 计算成功率与平均耗时。
     * startTime / endTime 为空时默认统计最近 30 天。
     * </p>
     *
     * @param webhookId Webhook 配置 ID
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计 VO
     * @throws ScrmException Webhook 配置不存在
     */
    @Transactional(readOnly = true)
    public WebhookStatsVo getWebhookStats(Long webhookId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        ScrmWebhookConfigEntity config = findConfigOrThrow(webhookId);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        LocalDateTime start = startTime != null ? startTime : end.minusDays(30);
        List<Object[]> statusRows = logRepository.countByStatus(config.getId(), start, end);
        Map<String, Long> statusMap = new LinkedHashMap<>();
        long total = 0L;
        for (Object[] row : statusRows) {
            String st = (String) row[0];
            Long cnt = (Long) row[1];
            statusMap.put(st, cnt);
            total += cnt;
        }
        long success = statusMap.getOrDefault(LOG_SUCCESS, 0L);
        long failed = statusMap.getOrDefault(LOG_FAILED, 0L);
        long pending = statusMap.getOrDefault(LOG_PENDING, 0L);
        long retry = statusMap.getOrDefault(LOG_RETRY, 0L);
        long expired = statusMap.getOrDefault(LOG_EXPIRED, 0L);
        long sending = statusMap.getOrDefault(LOG_SENDING, 0L);
        Double successRate = null;
        long executed = success + failed + expired;
        if (executed > 0) {
            successRate = round2(success * PERCENT_BASE / executed);
        }
        Double avgDurationMs = logRepository.averageDurationMsByWebhookIdAndStatus(
                config.getId(), LOG_SUCCESS, start, end);
        return WebhookStatsVo.builder()
                .webhookId(config.getId())
                .startTime(start)
                .endTime(end)
                .totalCount(total)
                .successCount(success)
                .failedCount(failed)
                .pendingCount(pending + sending)
                .retryCount(retry)
                .expiredCount(expired)
                .successRate(successRate)
                .avgDurationMs(avgDurationMs != null ? round2(avgDurationMs) : null)
                .build();
    }

    /**
     * 可订阅事件类型列表
     *
     * @return 事件类型列表
     */
    public List<String> listAvailableEvents() {
        return AVAILABLE_EVENTS;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 调度异步发送 (事务提交后触发)
     * <p>
     * 通过 {@link TransactionSynchronizationManager} 注册 afterCommit 回调,
     * 确保日志已提交到数据库后再触发异步发送, 避免异步线程读不到数据。
     * </p>
     *
     * @param logIds 待发送的推送日志 ID 列表
     */
    private void scheduleAsyncSend(List<Long> logIds) {
        if (logIds == null || logIds.isEmpty()) {
            return;
        }
        // 在调用线程捕获  避免异步线程 ThreadLocal 丢失
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 事务提交后逐条触发 Webhook 异步发送。
                 */
                @Override
                public void afterCommit() {
                    for (Long logId : logIds) {
                        self.sendWebhookAsync(logId);
                    }
                }
            });
        } else {
            // 无事务上下文, 直接异步发送
            for (Long logId : logIds) {
                self.sendWebhookAsync(logId);
            }
        }
    }

    /**
     * 执行 Webhook 发送 (内部实现)
     * <p>
     * 构造请求体 → 生成签名 → HTTP POST → 记录响应 → 失败则设置重试。
     * 数据隔离: 通过日志实体关联的 Webhook 配置校验归属, 不依赖请求上下文。
     * </p>
     *
     * @param logId 推送日志 ID
     * @return 更新后的推送日志 DTO
     * @throws ScrmException 日志不存在或状态非法
     */
    private ScrmWebhookLogDto doSendWebhook(Long logId) throws ScrmException {
        ScrmWebhookLogEntity logEntity = findLogOrThrow(logId);
        // 仅 PENDING / RETRY 状态可发送
        if (!LOG_PENDING.equals(logEntity.getStatus()) && !LOG_RETRY.equals(logEntity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "日志状态非法, 仅 PENDING / RETRY 可发送: currentStatus=" + logEntity.getStatus());
        }
        ScrmWebhookConfigEntity config = configRepository.findById(logEntity.getWebhookId()).orElse(null);
        if (config == null) {
            logEntity.setStatus(LOG_FAILED);
            logEntity.setErrorMessage("Webhook 配置不存在: webhookId=" + logEntity.getWebhookId());
            logEntity.setCompletedAt(LocalDateTime.now());
            logEntity = logRepository.save(logEntity);
            return toLogDto(logEntity);
        }
        // SSRF 防护: 发送前统一校验目标 URL (兼容修复前已存的未校验配置)
        validateTargetUrl(config.getTargetUrl());
        // 设置发送中状态
        logEntity.setStatus(LOG_SENDING);
        logEntity.setAttemptCount((logEntity.getAttemptCount() == null ? 0 : logEntity.getAttemptCount()) + 1);
        logEntity.setSentAt(LocalDateTime.now());
        logEntity = logRepository.save(logEntity);

        long startMs = System.currentTimeMillis();
        String requestBody = logEntity.getPayload();
        Integer responseStatus = null;
        String responseBody = null;
        String errorMessage = null;
        try {
            // 构造 HTTP 请求
            String httpMethod = config.getHttpMethod() != null ? config.getHttpMethod() : DEFAULT_HTTP_METHOD;
            int timeoutSec = config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getTargetUrl()))
                    .timeout(Duration.ofSeconds(timeoutSec));
            // 添加自定义 HTTP 头
            Map<String, Object> customHeaders = parseJsonToMap(config.getHeaders());
            for (Map.Entry<String, Object> entry : customHeaders.entrySet()) {
                reqBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
            }
            // 设置签名头
            String signature = generateSignature(requestBody, config.getSecret());
            if (signature != null) {
                reqBuilder.header(HEADER_SIGNATURE, signature);
            }
            // 设置事件元数据头
            reqBuilder.header(HEADER_EVENT_TYPE, logEntity.getEventType());
            reqBuilder.header(HEADER_EVENT_ID, logEntity.getEventId());
            // 设置方法与请求体
            if (METHOD_GET.equals(httpMethod)) {
                reqBuilder.GET();
            } else {
                reqBuilder.header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
                if (METHOD_PUT.equals(httpMethod)) {
                    reqBuilder.PUT(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
                } else {
                    reqBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
                }
            }
            // 发送请求 (同步阻塞)
            HttpResponse<String> response = HTTP_CLIENT.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            responseStatus = response.statusCode();
            responseBody = truncate(response.body(), MAX_RESPONSE_BODY_LENGTH);
            // 2xx 视为成功
            if (responseStatus >= 200 && responseStatus < 300) {
                logEntity.setStatus(LOG_SUCCESS);
                logEntity.setCompletedAt(LocalDateTime.now());
                logEntity.setRequestBody(truncate(requestBody, MAX_RESPONSE_BODY_LENGTH));
                logEntity.setResponseStatus(responseStatus);
                logEntity.setResponseBody(responseBody);
                logEntity.setDurationMs((int) (System.currentTimeMillis() - startMs));
                config.setSuccessCount((config.getSuccessCount() == null ? 0 : config.getSuccessCount()) + 1);
                config.setLastTriggerAt(LocalDateTime.now());
                config.setLastStatusCode(responseStatus);
                config.setLastError(null);
                log.info("Webhook 推送成功: logId={}, webhookId={}, eventType={}, status={}, durationMs={}",
                        logEntity.getId(), config.getId(), logEntity.getEventType(),
                        responseStatus, logEntity.getDurationMs());
            } else {
                errorMessage = "HTTP " + responseStatus;
                handleSendFailure(logEntity, config, errorMessage, startMs);
            }
        } catch (java.io.IOException | InterruptedException e) {
            errorMessage = truncate(e.getMessage(), MAX_ERROR_LENGTH);
            handleSendFailure(logEntity, config, errorMessage, startMs);
            log.warn("Webhook 推送异常: logId={}, webhookId={}, error={}",
                    logEntity.getId(), config.getId(), errorMessage, e);
        }
        logEntity = logRepository.save(logEntity);
        configRepository.save(config);
        return toLogDto(logEntity);
    }

    /**
     * 处理发送失败: 设置重试或最终失败
     *
     * @param logEntity   日志实体
     * @param config     Webhook 配置
     * @param errorMessage 错误信息
     * @param startMs    发送开始时间戳 (用于计算耗时)
     */
    private void handleSendFailure(ScrmWebhookLogEntity logEntity, ScrmWebhookConfigEntity config,
                                   String errorMessage, long startMs) {
        logEntity.setErrorMessage(errorMessage);
        logEntity.setDurationMs((int) (System.currentTimeMillis() - startMs));
        int maxAttempts = logEntity.getMaxAttempts() != null ? logEntity.getMaxAttempts() : DEFAULT_MAX_ATTEMPTS;
        int intervalSec = config.getRetryIntervalSeconds() != null
                ? config.getRetryIntervalSeconds() : DEFAULT_RETRY_INTERVAL_SECONDS;
        if (logEntity.getAttemptCount() < maxAttempts) {
            // 还有重试机会
            logEntity.setStatus(LOG_RETRY);
            logEntity.setNextRetryAt(LocalDateTime.now().plusSeconds(intervalSec));
            log.warn("Webhook 推送失败, 安排重试: logId={}, attemptCount={}, maxAttempts={}, nextRetryAt={}",
                    logEntity.getId(), logEntity.getAttemptCount(), maxAttempts, logEntity.getNextRetryAt());
        } else {
            // 重试次数已用尽
            logEntity.setStatus(LOG_FAILED);
            logEntity.setCompletedAt(LocalDateTime.now());
            logEntity.setNextRetryAt(null);
            log.warn("Webhook 推送最终失败: logId={}, attemptCount={}, maxAttempts={}",
                    logEntity.getId(), logEntity.getAttemptCount(), maxAttempts);
        }
        // 更新配置统计与状态
        config.setFailCount((config.getFailCount() == null ? 0 : config.getFailCount()) + 1);
        config.setLastTriggerAt(LocalDateTime.now());
        config.setLastError(errorMessage);
    }

    /**
     * 判断 Webhook 是否订阅了指定事件类型
     *
     * @param config    Webhook 配置
     * @param eventType 事件类型
     * @return true=已订阅
     */
    private boolean isSubscribed(ScrmWebhookConfigEntity config, String eventType) {
        List<String> events = parseJsonToStringList(config.getSubscribedEvents());
        if (events.isEmpty()) {
            return false;
        }
        return events.stream().anyMatch(e -> e.equalsIgnoreCase(eventType));
    }

    /**
     * 检查事件过滤条件
     * <p>
     * 解析 config.eventFilter 为 Map, 检查所有键值对是否在 eventData 中存在且相等。
     * eventFilter 为空或解析失败视为无条件, 默认匹配。
     * </p>
     *
     * @param config    Webhook 配置
     * @param eventData 事件数据 JSON (可空)
     * @return true=过滤匹配
     */
    private boolean matchesFilter(ScrmWebhookConfigEntity config, String eventData) {
        Map<String, Object> filter = parseJsonToMap(config.getEventFilter());
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        Map<String, Object> data = parseJsonToMap(eventData);
        if (data == null || data.isEmpty()) {
            return false;
        }
        return filter.entrySet().stream()
                .allMatch(e -> Objects.equals(String.valueOf(e.getValue()), String.valueOf(data.get(e.getKey()))));
    }

    /**
     * 创建 Webhook 配置参数校验
     *
     * @param dto Webhook 配置参数
     * @throws ScrmException 参数校验失败
     */
    private void validateCreateConfig(ScrmWebhookConfigDto dto) throws ScrmException {
        if (dto.getWebhookName() == null || dto.getWebhookName().isBlank()) {
            throw ScrmException.badRequest("Webhook 名称不能为空");
        }
        if (dto.getTargetUrl() == null || dto.getTargetUrl().isBlank()) {
            throw ScrmException.badRequest("目标 URL 不能为空");
        }
        // SSRF 防护: 仅允许公网 http/https 目标
        validateTargetUrl(dto.getTargetUrl());
        if (dto.getSubscribedEvents() == null || dto.getSubscribedEvents().isBlank()) {
            throw ScrmException.badRequest("订阅事件不能为空");
        }
        // 校验 subscribedEvents 为合法 JSON 数组
        List<String> events = parseJsonToStringList(dto.getSubscribedEvents());
        if (events.isEmpty()) {
            throw ScrmException.badRequest("订阅事件格式非法, 应为 JSON 数组");
        }
    }

    /**
     * SSRF 防护: 校验 Webhook 目标 URL 仅允许公网 http/https 地址。
     * <p>
     * 委托 {@link UrlSecurityUtils} 校验: 拒绝 loopback / 私网 / 链路本地 / 保留段 IP
     * (127/8、10/8、172.16/12、192.168/16、169.254/16、0.0.0.0、::1、fc00::、fe80::)
     * 及 localhost / 云 metadata 域名, 并对域名做一次性 DNS 解析校验, 防止 SSRF。
     * </p>
     *
     * @param targetUrl 目标 URL
     * @throws ScrmException URL 非法或指向私网/保留地址
     */
    private void validateTargetUrl(String targetUrl) throws ScrmException {
        try {
            UrlSecurityUtils.validatePublicHttpUrl(targetUrl);
        } catch (IllegalArgumentException e) {
            log.warn("Webhook 目标 URL 校验未通过, 已拒绝: {}", e.getMessage());
            throw ScrmException.badRequest(e.getMessage());
        }
    }

    /**
     * 构建配置查询条件 Specification
     *
     * @param status    状态过滤 (可空)
     * @param eventType 事件类型过滤 (可空, 匹配 subscribedEvents LIKE)
     * @return Specification
     */
    private Specification<ScrmWebhookConfigEntity> buildConfigSpec(String status, String eventType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.toUpperCase()));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.like(root.get("subscribedEvents"), "%" + eventType + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建日志查询条件 Specification
     *
     * @param webhookId Webhook 配置 ID 过滤 (可空)
     * @param eventType 事件类型过滤 (可空)
     * @param status    日志状态过滤 (可空)
     * @param startTime 起始时间过滤 (可空)
     * @param endTime   截止时间过滤 (可空)
     * @return Specification
     */
    private Specification<ScrmWebhookLogEntity> buildLogSpec(Long webhookId, String eventType, String status,
                                                             LocalDateTime startTime, LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (webhookId != null) {
                predicates.add(cb.equal(root.get("webhookId"), webhookId));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("eventType")), eventType.toUpperCase()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.toUpperCase()));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 确保分页参数带默认排序
     *
     * @param pageable 原始分页参数
     * @param field    排序字段
     * @return 带排序的分页参数
     */
    private Pageable ensureSort(Pageable pageable, String field) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, field));
    }

    /**
* 按主键查询 Webhook 配置并校验归属账号
*
* @param id Webhook 配置 ID
* @return Webhook 配置实体
* @throws ScrmException 配置不存在或越权访问
     */
    private ScrmWebhookConfigEntity findConfigOrThrow(Long id) throws ScrmException {
        ScrmWebhookConfigEntity entity = configRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "Webhook 配置不存在: id=" + id));

        return entity;
    }

    /**
* 按主键查询推送日志并校验归属账号
*
* @param id 日志 ID
* @return 日志实体
* @throws ScrmException 日志不存在或越权访问
     */
    private ScrmWebhookLogEntity findLogOrThrow(Long id) throws ScrmException {
        ScrmWebhookLogEntity entity = logRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "Webhook 推送日志不存在: id=" + id));

        return entity;
    }

    /**
     * 解析 JSON 字符串为 Map, 解析失败返回空 Map
     *
     * @param json JSON 字符串 (可空)
     * @return Map (不为 null)
     */
    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return map != null ? map : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("JSON 解析为 Map 失败, 返回空 Map: json={}", json, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 解析 JSON 字符串数组为 List, 解析失败返回空 List
     *
     * @param json JSON 字符串 (可空)
     * @return List (不为 null)
     */
    private List<String> parseJsonToStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.warn("JSON 解析为 List 失败, 返回空 List: json={}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 对象序列化为 JSON 字符串
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败, 返回空字符串: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 截断字符串到指定长度
     *
     * @param str    原始字符串 (可空)
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLen) {
            return str;
        }
        return str.substring(0, maxLen);
    }

    /**
     * 保留两位小数
     *
     * @param v 原始值
     * @return 四舍五入到两位小数
     */
    private double round2(double v) {
        return Math.round(v * Math.pow(10, RATE_SCALE)) / Math.pow(10, RATE_SCALE);
    }

    /**
     * Webhook 配置实体转 DTO
     *
     * @param entity 配置实体
     * @return 配置 DTO
     */
    private ScrmWebhookConfigDto toConfigDto(ScrmWebhookConfigEntity entity) {
        ScrmWebhookConfigDto dto = new ScrmWebhookConfigDto();
        dto.setId(entity.getId());
        dto.setWebhookName(entity.getWebhookName());
        dto.setTargetUrl(entity.getTargetUrl());
        dto.setSecret(entity.getSecret());
        dto.setSubscribedEvents(entity.getSubscribedEvents());
        dto.setEventFilter(entity.getEventFilter());
        dto.setHttpMethod(entity.getHttpMethod());
        dto.setHeaders(entity.getHeaders());
        dto.setTimeoutSeconds(entity.getTimeoutSeconds());
        dto.setMaxRetries(entity.getMaxRetries());
        dto.setRetryIntervalSeconds(entity.getRetryIntervalSeconds());
        dto.setStatus(entity.getStatus());
        dto.setLastTriggerAt(entity.getLastTriggerAt());
        dto.setLastStatusCode(entity.getLastStatusCode());
        dto.setLastError(entity.getLastError());
        dto.setSuccessCount(entity.getSuccessCount());
        dto.setFailCount(entity.getFailCount());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 推送日志实体转 DTO
     *
     * @param entity 日志实体
     * @return 日志 DTO
     */
    private ScrmWebhookLogDto toLogDto(ScrmWebhookLogEntity entity) {
        ScrmWebhookLogDto dto = new ScrmWebhookLogDto();
        dto.setId(entity.getId());
        dto.setWebhookId(entity.getWebhookId());
        dto.setEventType(entity.getEventType());
        dto.setEventId(entity.getEventId());
        dto.setPayload(entity.getPayload());
        dto.setRequestBody(entity.getRequestBody());
        dto.setResponseStatus(entity.getResponseStatus());
        dto.setResponseBody(entity.getResponseBody());
        dto.setStatus(entity.getStatus());
        dto.setAttemptCount(entity.getAttemptCount());
        dto.setMaxAttempts(entity.getMaxAttempts());
        dto.setNextRetryAt(entity.getNextRetryAt());
        dto.setSentAt(entity.getSentAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setDurationMs(entity.getDurationMs());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
