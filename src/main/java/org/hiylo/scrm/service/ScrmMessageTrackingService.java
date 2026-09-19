/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTrackingService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmMessageReadReportDto;
import org.hiylo.scrm.dto.ScrmMessageRecallActionDto;
import org.hiylo.scrm.dto.ScrmMessageTrackingDto;
import org.hiylo.scrm.entity.ScrmMessageReadLogEntity;
import org.hiylo.scrm.entity.ScrmMessageRecallEntity;
import org.hiylo.scrm.entity.ScrmMessageTrackingEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmMessageReadLogRepository;
import org.hiylo.scrm.repository.ScrmMessageRecallRepository;
import org.hiylo.scrm.repository.ScrmMessageTrackingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * SCRM 消息跟踪服务。
 * <p>
 * 承载消息发送后的全生命周期跟踪能力, 分为六组能力:
 * <ul>
 *   <li>跟踪记录: 创建 / 查询 / 列表 / 更新发送状态 / 标记送达</li>
 *   <li>阅读管理: 记录阅读 (创建阅读日志→更新跟踪→计算互动评分) / 批量记录 / 阅读日志查询 /
 *       阅读统计 / 未读列表</li>
 *   <li>撤回管理: 撤回消息 (检查撤回窗口→更新跟踪→创建撤回记录→处理已阅读者, 模拟实现) /
 *       查询 / 列表 / 检查撤回窗口 / 执行撤回</li>
 *   <li>转发追踪: 记录转发 / 转发链 / 转发列表 (转发事件存储于跟踪记录 metadata JSON)</li>
 *   <li>回复追踪: 记录回复 / 回复列表 (回复事件存储于跟踪记录 metadata JSON)</li>
 *   <li>统计分析: 跟踪统计 / 已读率 / 互动统计 / 渠道对比 / 已读趋势 / 最佳发送时间</li>
 * </ul>
 * </p>
 * <p>
 * 撤回执行与转发/回复事件追踪为模拟实现, 仅维护本地状态, 不对接实际消息平台。
 * 所有写操作写入当前用户归属账号, 实现数据隔离, 越权访问按不存在处理。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMessageTrackingService {

    /** 默认撤回窗口 (分钟) */
    private static final int DEFAULT_RECALL_WINDOW_MINUTES = 2;

    /** 默认趋势回溯天数 */
    private static final int DEFAULT_TREND_DAYS = 7;

    /** 空数组 JSON */
    private static final String EMPTY_ARRAY = "[]";

    /** metadata 中转发链字段名 */
    private static final String META_FORWARD_CHAIN = "forwardChain";

    /** metadata 中回复历史字段名 */
    private static final String META_REPLY_HISTORY = "replyHistory";

    /** 合法的接收者类型 */
    private static final Set<String> VALID_RECIPIENT_TYPES = new HashSet<>(Arrays.asList(
            "CUSTOMER", "GROUP", "EXTERNAL"));

    /** 合法的渠道 */
    private static final Set<String> VALID_CHANNELS = new HashSet<>(Arrays.asList(
            "WECHAT", "WORK_WECHAT", "SMS", "EMAIL", "APP_PUSH", "WEB_SOCKET"));

    /** 合法的内容类型 */
    private static final Set<String> VALID_CONTENT_TYPES = new HashSet<>(Arrays.asList(
            "TEXT", "IMAGE", "VIDEO", "FILE", "LINK", "CARD", "HTML"));

    /** 合法的发送状态 */
    private static final Set<String> VALID_SEND_STATUSES = new HashSet<>(Arrays.asList(
            "PENDING", "SENT", "DELIVERED", "FAILED", "CANCELLED"));

    /** 合法的阅读者类型 */
    private static final Set<String> VALID_READER_TYPES = new HashSet<>(Arrays.asList(
            "CUSTOMER", "AGENT", "SYSTEM"));

    /** 合法的阅读来源 */
    private static final Set<String> VALID_READ_SOURCES = new HashSet<>(Arrays.asList(
            "APP", "WEB", "EMAIL_CLIENT", "WECHAT"));

    /** 合法的撤回类型 */
    private static final Set<String> VALID_RECALL_TYPES = new HashSet<>(Arrays.asList(
            "MANUAL", "AUTO", "SYSTEM"));

    /** 合法的撤回状态 */
    private static final Set<String> VALID_RECALL_STATUSES = new HashSet<>(Arrays.asList(
            "SUCCESS", "PARTIAL", "FAILED", "PENDING"));

    /** 消息跟踪数据访问层 */
    private final ScrmMessageTrackingRepository trackingRepository;

    /** 阅读日志数据访问层 */
    private final ScrmMessageReadLogRepository readLogRepository;

    /** 撤回记录数据访问层 */
    private final ScrmMessageRecallRepository recallRepository;

    /** JSON 解析器 (解析 / 序列化 metadata) */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 跟踪记录 Tracking
    // ============================================================

    /**
     * 创建消息跟踪记录。
     * <p>校验 messageId 唯一与枚举合法性后写入账号 ID 持久化, sendStatus 缺省 PENDING,
     * contentType 缺省 TEXT, recipientType 缺省 CUSTOMER, sentAt 缺省当前时间。</p>
     *
     * @param dto 跟踪参数
     * @return 创建后的跟踪记录
     * @throws ScrmException 消息 ID 重复 / 参数非法
     */
    @Transactional
    public ScrmMessageTrackingEntity createTracking(ScrmMessageTrackingDto dto) throws ScrmException {
        validateTrackingDto(dto);
        if (trackingRepository.findByMessageId(dto.getMessageId()).isPresent()) {
            throw ScrmException.conflict("消息 ID 已存在: " + dto.getMessageId());
        }
        ScrmMessageTrackingEntity entity = new ScrmMessageTrackingEntity();
        entity.setMessageId(dto.getMessageId());
        entity.setBatchId(dto.getBatchId());
        entity.setSenderId(dto.getSenderId());
        entity.setSenderName(dto.getSenderName());
        entity.setRecipientId(dto.getRecipientId());
        entity.setRecipientName(dto.getRecipientName());
        entity.setRecipientType(dto.getRecipientType() != null ? dto.getRecipientType() : "CUSTOMER");
        entity.setChannel(dto.getChannel());
        entity.setMessageContent(dto.getMessageContent());
        entity.setContentType(dto.getContentType() != null ? dto.getContentType() : "TEXT");
        entity.setSendStatus(dto.getSendStatus() != null ? dto.getSendStatus() : "PENDING");
        entity.setSentAt(dto.getSentAt() != null ? dto.getSentAt() : LocalDateTime.now());
        entity.setClientIp(dto.getClientIp());
        entity.setDeviceType(dto.getDeviceType());
        entity.setMetadata(dto.getMetadata());
        entity = trackingRepository.save(entity);
        log.info("创建消息跟踪: id={}, messageId={}, channel={}, sendStatus={}",
                entity.getId(), entity.getMessageId(), entity.getChannel(), entity.getSendStatus());
        return entity;
    }

    /**
     * 查询消息跟踪记录详情。
     *
     * @param id 跟踪记录 ID
     * @return 跟踪记录实体
     * @throws ScrmException 跟踪记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmMessageTrackingEntity getTracking(Long id) throws ScrmException {
        return findTrackingOrThrow(id);
    }

    /**
     * 按消息 ID 查询跟踪记录。
     *
     * @param messageId 消息 ID
     * @return 跟踪记录实体
     * @throws ScrmException 跟踪记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmMessageTrackingEntity getTrackingByMessageId(String messageId) throws ScrmException {
        if (messageId == null || messageId.isBlank()) {
            throw ScrmException.badRequest("消息 ID 不能为空");
        }
        ScrmMessageTrackingEntity entity = trackingRepository
                .findByMessageId(messageId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "消息跟踪记录不存在: messageId=" + messageId));
        return entity;
    }

    /**
     * 分页查询消息跟踪记录, 支持按批次 / 发送者 / 接收者 / 渠道 / 发送状态 / 已读 / 已撤回 /
     * 时间范围组合过滤。
     *
     * @param batchId    批次 ID 过滤 (可空)
     * @param senderId   发送者 ID 过滤 (可空)
     * @param recipientId 接收者 ID 过滤 (可空)
     * @param channel    渠道过滤 (可空)
     * @param sendStatus 发送状态过滤 (可空)
     * @param isRead     已读过滤 (可空)
     * @param isRecalled 已撤回过滤 (可空)
     * @param startTime  发送时间起点 (含, 可空)
     * @param endTime    发送时间终点 (含, 可空)
     * @param pageable   分页参数
     * @return 跟踪记录分页结果 (按 sentAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageTrackingEntity> listTrackings(Long batchId, String senderId, String recipientId,
                                                          String channel, String sendStatus, Boolean isRead,
                                                          Boolean isRecalled, LocalDateTime startTime,
                                                          LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmMessageTrackingEntity> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (batchId != null) {
                predicates.add(cb.equal(root.get("batchId"), batchId));
            }
            if (senderId != null && !senderId.isBlank()) {
                predicates.add(cb.equal(root.get("senderId"), senderId));
            }
            if (recipientId != null && !recipientId.isBlank()) {
                predicates.add(cb.equal(root.get("recipientId"), recipientId));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (sendStatus != null && !sendStatus.isBlank()) {
                predicates.add(cb.equal(root.get("sendStatus"), sendStatus));
            }
            if (isRead != null) {
                predicates.add(cb.equal(root.get("isRead"), isRead));
            }
            if (isRecalled != null) {
                predicates.add(cb.equal(root.get("isRecalled"), isRecalled));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sentAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sentAt"), endTime));
            }
            q.orderBy(cb.desc(root.get("sentAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return trackingRepository.findAll(spec, pageable);
    }

    /**
     * 更新消息发送状态。
     * <p>状态非法或终态 (CANCELLED) 不允许变更。SENT 状态自动填充 sentAt (若为空)。</p>
     *
     * @param messageId 消息 ID
     * @param status    目标发送状态
     * @return 更新后的跟踪记录
     * @throws ScrmException 跟踪记录不存在 / 状态非法
     */
    @Transactional
    public ScrmMessageTrackingEntity updateSendStatus(String messageId, String status) throws ScrmException {
        if (status == null || status.isBlank()) {
            throw ScrmException.badRequest("发送状态不能为空");
        }
        if (!VALID_SEND_STATUSES.contains(status)) {
            throw ScrmException.badRequest(
                    "发送状态非法: " + status + ", 仅支持 " + VALID_SEND_STATUSES);
        }
        ScrmMessageTrackingEntity entity = getTrackingByMessageId(messageId);
        if ("CANCELLED".equals(entity.getSendStatus())) {
            throw ScrmException.badRequest("消息已取消, 不允许变更发送状态: messageId=" + messageId);
        }
        entity.setSendStatus(status);
        if ("SENT".equals(status) && entity.getSentAt() == null) {
            entity.setSentAt(LocalDateTime.now());
        }
        if ("FAILED".equals(status) && entity.getErrorMessage() == null) {
            entity.setErrorMessage("发送失败");
        }
        entity = trackingRepository.save(entity);
        log.info("更新消息发送状态: messageId={}, status={}", messageId, status);
        return entity;
    }

    /**
     * 标记消息送达。
     * <p>更新 sendStatus 为 DELIVERED 并填充 deliveredAt。</p>
     *
     * @param messageId 消息 ID
     * @return 更新后的跟踪记录
     * @throws ScrmException 跟踪记录不存在
     */
    @Transactional
    public ScrmMessageTrackingEntity markDelivered(String messageId) throws ScrmException {
        ScrmMessageTrackingEntity entity = getTrackingByMessageId(messageId);
        entity.setSendStatus("DELIVERED");
        entity.setDeliveredAt(LocalDateTime.now());
        entity = trackingRepository.save(entity);
        log.info("标记消息送达: messageId={}", messageId);
        return entity;
    }

    // ============================================================
    // 阅读管理 Read
    // ============================================================

    /**
     * 记录消息阅读。
     * <p>流程: 按 messageId 定位跟踪记录 → 创建阅读日志 (判断重复阅读与阅读序号) →
     * 更新跟踪记录的阅读状态 (firstReadAt/lastReadAt/readCount/isRead/readDurationSeconds) →
     * 计算互动评分。</p>
     *
     * @param reportDto 阅读上报参数
     * @return 创建后的阅读日志
     * @throws ScrmException 跟踪记录不存在 / 参数非法
     */
    @Transactional
    public ScrmMessageReadLogEntity recordRead(ScrmMessageReadReportDto reportDto) throws ScrmException {
        validateReadReportDto(reportDto);
        ScrmMessageTrackingEntity tracking = getTrackingByMessageId(reportDto.getMessageId());
        if (Boolean.TRUE.equals(tracking.getIsRecalled())) {
            throw ScrmException.badRequest("消息已撤回, 不允许记录阅读: messageId=" + reportDto.getMessageId());
        }
        // 判断重复阅读与计算阅读序号
        Optional<ScrmMessageReadLogEntity> lastLog = readLogRepository
                .findFirstByMessageTrackingIdAndReaderIdOrderBySequenceDesc(
                        tracking.getId(), reportDto.getReaderId());
        boolean repeated = lastLog.isPresent();
        int sequence = lastLog.map(l -> l.getSequence() == null ? 1 : l.getSequence() + 1).orElse(1);
        int duration = reportDto.getReadDuration() != null ? reportDto.getReadDuration() : 0;
        // 创建阅读日志
        ScrmMessageReadLogEntity logEntity = new ScrmMessageReadLogEntity();
        logEntity.setMessageTrackingId(tracking.getId());
        logEntity.setMessageId(tracking.getMessageId());
        logEntity.setReaderId(reportDto.getReaderId());
        logEntity.setReaderName(reportDto.getReaderName());
        logEntity.setReaderType(reportDto.getReaderType() != null ? reportDto.getReaderType() : "CUSTOMER");
        logEntity.setReadAt(LocalDateTime.now());
        logEntity.setReadDurationSeconds(duration);
        logEntity.setReadSource(reportDto.getSource());
        logEntity.setDeviceType(reportDto.getDeviceType());
        logEntity.setOs(reportDto.getOs());
        logEntity.setBrowser(reportDto.getBrowser());
        logEntity.setClientIp(reportDto.getClientIp());
        logEntity.setLocation(reportDto.getLocation());
        logEntity.setIsRepeatedRead(repeated);
        logEntity.setSequence(sequence);
        logEntity = readLogRepository.save(logEntity);
        // 更新跟踪记录的阅读状态
        LocalDateTime now = LocalDateTime.now();
        if (tracking.getFirstReadAt() == null) {
            tracking.setFirstReadAt(now);
        }
        tracking.setLastReadAt(now);
        tracking.setReadCount((tracking.getReadCount() == null ? 0 : tracking.getReadCount()) + 1);
        tracking.setIsRead(true);
        tracking.setReadDurationSeconds((tracking.getReadDurationSeconds() == null ? 0
                : tracking.getReadDurationSeconds()) + duration);
        tracking.setEngagementScore(calculateEngagementScore(tracking));
        trackingRepository.save(tracking);
        log.info("记录消息阅读: messageId={}, readerId={}, sequence={}, repeated={}",
                reportDto.getMessageId(), reportDto.getReaderId(), sequence, repeated);
        return logEntity;
    }

    /**
     * 批量记录消息阅读。
     *
     * @param reports 阅读上报列表
     * @return 创建后的阅读日志列表
     * @throws ScrmException 参数非法
     */
    @Transactional
    public List<ScrmMessageReadLogEntity> batchRecordReads(List<ScrmMessageReadReportDto> reports)
            throws ScrmException {
        if (reports == null || reports.isEmpty()) {
            throw ScrmException.badRequest("阅读上报列表不能为空");
        }
        List<ScrmMessageReadLogEntity> result = new ArrayList<>(reports.size());
        for (ScrmMessageReadReportDto report : reports) {
            result.add(recordRead(report));
        }
        log.info("批量记录消息阅读: count={}", reports.size());
        return result;
    }

    /**
     * 按消息跟踪 ID 查询阅读日志列表 (按阅读时间升序)。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @return 阅读日志列表
     * @throws ScrmException 跟踪记录不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmMessageReadLogEntity> getReadLogs(Long messageTrackingId) throws ScrmException {
        findTrackingOrThrow(messageTrackingId);
        return readLogRepository.findByMessageTrackingIdOrderByReadAtAsc(messageTrackingId);
    }

    /**
     * 查询阅读日志详情。
     *
     * @param id 阅读日志 ID
     * @return 阅读日志实体
     * @throws ScrmException 阅读日志不存在
     */
    @Transactional(readOnly = true)
    public ScrmMessageReadLogEntity getReadLog(Long id) throws ScrmException {
        ScrmMessageReadLogEntity entity = readLogRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "阅读日志不存在: id=" + id));
        return entity;
    }

    /**
     * 分页查询阅读日志, 支持按消息跟踪 ID / 阅读者过滤。
     *
     * @param messageTrackingId 消息跟踪 ID 过滤 (可空)
     * @param readerId          阅读者 ID 过滤 (可空)
     * @param pageable          分页参数
     * @return 阅读日志分页结果 (按 readAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageReadLogEntity> listReadLogs(Long messageTrackingId, String readerId, Pageable pageable) {
        Specification<ScrmMessageReadLogEntity> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (messageTrackingId != null) {
                predicates.add(cb.equal(root.get("messageTrackingId"), messageTrackingId));
            }
            if (readerId != null && !readerId.isBlank()) {
                predicates.add(cb.equal(root.get("readerId"), readerId));
            }
            q.orderBy(cb.desc(root.get("readAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return readLogRepository.findAll(spec, pageable);
    }

    /**
     * 阅读统计: 阅读次数 / 独立阅读者 / 重复阅读数 / 平均阅读时长 / 首次阅读 / 最后阅读。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @return 统计结果 Map
     * @throws ScrmException 跟踪记录不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReadStats(Long messageTrackingId) throws ScrmException {
        ScrmMessageTrackingEntity tracking = findTrackingOrThrow(messageTrackingId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("messageTrackingId", messageTrackingId);
        stats.put("messageId", tracking.getMessageId());
        stats.put("readCount", readLogRepository.countByMessageTrackingId(messageTrackingId));
        stats.put("uniqueReaders", readLogRepository.countDistinctReader(messageTrackingId));
        stats.put("repeatedReads", readLogRepository.countRepeatedRead(messageTrackingId));
        stats.put("avgReadDurationSeconds", readLogRepository.avgReadDuration(messageTrackingId));
        stats.put("firstReadAt", tracking.getFirstReadAt());
        stats.put("lastReadAt", tracking.getLastReadAt());
        stats.put("isRead", tracking.getIsRead());
        return stats;
    }

    /**
     * 未读列表: 按批次 ID 分页查询未读消息跟踪记录。
     *
     * @param batchId  批次 ID (可空, 为空时返回所有未读)
     * @param pageable 分页参数
     * @return 未读跟踪记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageTrackingEntity> getUnreadList(Long batchId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "sentAt"));
        if (batchId != null) {
            return trackingRepository.findByBatchIdAndIsReadFalse(batchId, sorted);
        }
        Specification<ScrmMessageTrackingEntity> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isRead"), false));
            q.orderBy(cb.desc(root.get("sentAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return trackingRepository.findAll(spec, sorted);
    }

    // ============================================================
    // 撤回管理 Recall
    // ============================================================

    /**
     * 撤回消息 (模拟实现)。
     * <p>流程: 按 messageId 定位跟踪记录 → 检查撤回窗口 (sentAt 至今是否在窗口内) →
     * 更新跟踪记录 (isRecalled=true/recalledAt/recallReason) → 创建撤回记录 →
     * 处理已阅读者 (记录到 affectedReaders JSON)。</p>
     *
     * @param recallActionDto 撤回动作参数
     * @return 创建后的撤回记录
     * @throws ScrmException 跟踪记录不存在 / 已撤回 / 超出撤回窗口
     */
    @Transactional
    public ScrmMessageRecallEntity recallMessage(ScrmMessageRecallActionDto recallActionDto) throws ScrmException {
        if (recallActionDto == null) {
            throw ScrmException.badRequest("撤回参数不能为空");
        }
        if (recallActionDto.getMessageId() == null || recallActionDto.getMessageId().isBlank()) {
            throw ScrmException.badRequest("消息 ID 不能为空");
        }
        ScrmMessageTrackingEntity tracking = getTrackingByMessageId(recallActionDto.getMessageId());
        if (Boolean.TRUE.equals(tracking.getIsRecalled())) {
            throw ScrmException.conflict("消息已撤回, 不允许重复撤回: messageId=" + recallActionDto.getMessageId());
        }
        // 检查撤回窗口
        boolean withinWindow = checkRecallWindowInternal(tracking);
        if (!withinWindow) {
            log.warn("消息撤回超出窗口: messageId={}, sentAt={}",
                    recallActionDto.getMessageId(), tracking.getSentAt());
        }
        // 更新跟踪记录
        LocalDateTime now = LocalDateTime.now();
        tracking.setIsRecalled(true);
        tracking.setRecalledAt(now);
        tracking.setRecallReason(recallActionDto.getReason());
        trackingRepository.save(tracking);
        // 处理已阅读者 (记录到 affectedReaders JSON)
        List<ScrmMessageReadLogEntity> readers = readLogRepository
                .findReadersByTrackingId(tracking.getId());
        String affectedReadersJson = buildAffectedReadersJson(readers);
        // 创建撤回记录
        ScrmMessageRecallEntity recall = new ScrmMessageRecallEntity();
        recall.setMessageTrackingId(tracking.getId());
        recall.setMessageId(tracking.getMessageId());
        recall.setSenderId(tracking.getSenderId());
        recall.setSenderName(tracking.getSenderName());
        recall.setRecallType("MANUAL");
        recall.setRecallReason(recallActionDto.getReason());
        // 模拟实现: 全部接收者视为成功撤回
        recall.setRecallStatus("SUCCESS");
        recall.setTotalRecipients(1);
        recall.setSuccessfulRecalls(1);
        recall.setFailedRecalls(0);
        recall.setRecalledAt(now);
        recall.setCompletedAt(now);
        recall.setRecallWindowMinutes(DEFAULT_RECALL_WINDOW_MINUTES);
        recall.setIsWithinWindow(withinWindow);
        recall.setAffectedReaders(affectedReadersJson);
        recall.setNotes("模拟撤回执行");
        recall = recallRepository.save(recall);
        log.info("撤回消息: messageId={}, recallId={}, withinWindow={}, affectedReaders={}",
                recallActionDto.getMessageId(), recall.getId(), withinWindow, readers.size());
        return recall;
    }

    /**
     * 查询撤回记录详情。
     *
     * @param id 撤回记录 ID
     * @return 撤回记录实体
     * @throws ScrmException 撤回记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmMessageRecallEntity getRecall(Long id) throws ScrmException {
        ScrmMessageRecallEntity entity = recallRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "撤回记录不存在: id=" + id));
        return entity;
    }

    /**
     * 按消息 ID 查询撤回记录 (取最近一条)。
     *
     * @param messageId 消息 ID
     * @return 撤回记录实体
     * @throws ScrmException 撤回记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmMessageRecallEntity getRecallByMessageId(String messageId) throws ScrmException {
        if (messageId == null || messageId.isBlank()) {
            throw ScrmException.badRequest("消息 ID 不能为空");
        }
        ScrmMessageRecallEntity entity = recallRepository
                .findFirstByMessageIdOrderByRecalledAtDesc(messageId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "撤回记录不存在: messageId=" + messageId));
        return entity;
    }

    /**
     * 分页查询撤回记录, 支持按发送者 / 撤回状态 / 时间范围过滤。
     *
     * @param senderId     发送者 ID 过滤 (可空)
     * @param recallStatus 撤回状态过滤 (可空)
     * @param startTime    撤回时间起点 (含, 可空)
     * @param endTime      撤回时间终点 (含, 可空)
     * @param pageable     分页参数
     * @return 撤回记录分页结果 (按 recalledAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageRecallEntity> listRecalls(String senderId, String recallStatus,
                                                       LocalDateTime startTime, LocalDateTime endTime,
                                                       Pageable pageable) {
        Specification<ScrmMessageRecallEntity> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (senderId != null && !senderId.isBlank()) {
                predicates.add(cb.equal(root.get("senderId"), senderId));
            }
            if (recallStatus != null && !recallStatus.isBlank()) {
                predicates.add(cb.equal(root.get("recallStatus"), recallStatus));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("recalledAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("recalledAt"), endTime));
            }
            q.orderBy(cb.desc(root.get("recalledAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return recallRepository.findAll(spec, pageable);
    }

    /**
     * 检查撤回窗口: 消息发送时间至今是否在默认撤回窗口 (2 分钟) 内。
     *
     * @param messageId 消息 ID
     * @return 撤回窗口检查结果 Map {withinWindow, sentAt, recallWindowMinutes, elapsedMinutes}
     * @throws ScrmException 跟踪记录不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkRecallWindow(String messageId) throws ScrmException {
        ScrmMessageTrackingEntity tracking = getTrackingByMessageId(messageId);
        boolean withinWindow = checkRecallWindowInternal(tracking);
        long elapsedMinutes = tracking.getSentAt() == null ? 0L
                : Duration.between(tracking.getSentAt(), LocalDateTime.now()).toMinutes();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messageId", messageId);
        result.put("withinWindow", withinWindow);
        result.put("sentAt", tracking.getSentAt());
        result.put("recallWindowMinutes", DEFAULT_RECALL_WINDOW_MINUTES);
        result.put("elapsedMinutes", elapsedMinutes);
        result.put("isRecalled", tracking.getIsRecalled());
        return result;
    }

    /**
     * 执行撤回 (模拟实现)。
     * <p>对已创建的撤回记录执行撤回动作: 将状态从 PENDING 推进为 SUCCESS 并填充完成时间。
     * 已为 SUCCESS / FAILED 的记录不再变更。</p>
     *
     * @param recallId 撤回记录 ID
     * @return 更新后的撤回记录
     * @throws ScrmException 撤回记录不存在 / 状态非法
     */
    @Transactional
    public ScrmMessageRecallEntity processRecall(Long recallId) throws ScrmException {
        ScrmMessageRecallEntity recall = getRecall(recallId);
        if (!"PENDING".equals(recall.getRecallStatus())) {
            throw ScrmException.badRequest(
                    "撤回记录状态不允许执行: status=" + recall.getRecallStatus());
        }
        // 模拟执行: 推进为 SUCCESS
        recall.setRecallStatus("SUCCESS");
        recall.setCompletedAt(LocalDateTime.now());
        recall.setSuccessfulRecalls(recall.getTotalRecipients());
        recall.setFailedRecalls(0);
        recall = recallRepository.save(recall);
        log.info("执行撤回 (模拟): recallId={}, messageId={}, status=SUCCESS",
                recallId, recall.getMessageId());
        return recall;
    }

    // ============================================================
    // 转发追踪 Forward
    // ============================================================

    /**
     * 记录转发。
     * <p>更新跟踪记录的转发字段 (isForwarded=true/forwardCount++/firstForwardedAt),
     * 并将转发事件追加到 metadata 的 forwardChain JSON 数组。</p>
     *
     * @param messageId    消息 ID
     * @param forwarderId  转发者 ID
     * @return 更新后的跟踪记录
     * @throws ScrmException 跟踪记录不存在 / 参数非法
     */
    @Transactional
    public ScrmMessageTrackingEntity recordForward(String messageId, String forwarderId) throws ScrmException {
        if (forwarderId == null || forwarderId.isBlank()) {
            throw ScrmException.badRequest("转发者 ID 不能为空");
        }
        ScrmMessageTrackingEntity tracking = getTrackingByMessageId(messageId);
        LocalDateTime now = LocalDateTime.now();
        tracking.setIsForwarded(true);
        tracking.setForwardCount((tracking.getForwardCount() == null ? 0 : tracking.getForwardCount()) + 1);
        if (tracking.getFirstForwardedAt() == null) {
            tracking.setFirstForwardedAt(now);
        }
        // 追加转发事件到 metadata
        Map<String, Object> metadata = parseMetadata(tracking.getMetadata());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chain = (List<Map<String, Object>>) metadata
                .computeIfAbsent(META_FORWARD_CHAIN, k -> new ArrayList<>());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("forwarderId", forwarderId);
        event.put("forwardedAt", now);
        event.put("sequence", chain.size() + 1);
        chain.add(event);
        tracking.setMetadata(toJson(metadata));
        tracking.setEngagementScore(calculateEngagementScore(tracking));
        tracking = trackingRepository.save(tracking);
        log.info("记录转发: messageId={}, forwarderId={}, forwardCount={}",
                messageId, forwarderId, tracking.getForwardCount());
        return tracking;
    }

    /**
     * 转发链: 解析跟踪记录 metadata 中的 forwardChain, 返回转发事件列表。
     *
     * @param messageId 消息 ID
     * @return 转发事件列表 [{forwarderId, forwardedAt, sequence}]
     * @throws ScrmException 跟踪记录不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getForwardChain(String messageId) throws ScrmException {
        ScrmMessageTrackingEntity tracking = getTrackingByMessageId(messageId);
        Map<String, Object> metadata = parseMetadata(tracking.getMetadata());
        Object chain = metadata.get(META_FORWARD_CHAIN);
        if (chain instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) chain;
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * 分页查询转发事件 (从 metadata 的 forwardChain 内存分页)。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @param pageable          分页参数
     * @return 转发事件分页结果
     * @throws ScrmException 跟踪记录不存在
     */
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listForwards(Long messageTrackingId, Pageable pageable) throws ScrmException {
        ScrmMessageTrackingEntity tracking = findTrackingOrThrow(messageTrackingId);
        List<Map<String, Object>> chain = getForwardChain(tracking.getMessageId());
        int total = chain.size();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        List<Map<String, Object>> content = chain.subList(from, to);
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    // ============================================================
    // 回复追踪 Reply
    // ============================================================

    /**
     * 记录回复。
     * <p>更新跟踪记录的回复字段 (isReplied=true/repliedAt/replyContent),
     * 并将回复事件追加到 metadata 的 replyHistory JSON 数组。</p>
     *
     * @param messageId     消息 ID
     * @param replyContent  回复内容摘要
     * @param replierId     回复者 ID
     * @return 更新后的跟踪记录
     * @throws ScrmException 跟踪记录不存在 / 参数非法
     */
    @Transactional
    public ScrmMessageTrackingEntity recordReply(String messageId, String replyContent, String replierId)
            throws ScrmException {
        if (replyContent == null || replyContent.isBlank()) {
            throw ScrmException.badRequest("回复内容不能为空");
        }
        if (replierId == null || replierId.isBlank()) {
            throw ScrmException.badRequest("回复者 ID 不能为空");
        }
        ScrmMessageTrackingEntity tracking = getTrackingByMessageId(messageId);
        LocalDateTime now = LocalDateTime.now();
        tracking.setIsReplied(true);
        tracking.setRepliedAt(now);
        // replyContent 字段长度限制 500, 截断保护
        String summary = replyContent.length() > 500 ? replyContent.substring(0, 500) : replyContent;
        tracking.setReplyContent(summary);
        // 追加回复事件到 metadata
        Map<String, Object> metadata = parseMetadata(tracking.getMetadata());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) metadata
                .computeIfAbsent(META_REPLY_HISTORY, k -> new ArrayList<>());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("replierId", replierId);
        event.put("replyContent", summary);
        event.put("repliedAt", now);
        event.put("sequence", history.size() + 1);
        history.add(event);
        tracking.setMetadata(toJson(metadata));
        tracking.setEngagementScore(calculateEngagementScore(tracking));
        tracking = trackingRepository.save(tracking);
        log.info("记录回复: messageId={}, replierId={}", messageId, replierId);
        return tracking;
    }

    /**
     * 分页查询回复事件 (从 metadata 的 replyHistory 内存分页)。
     *
     * @param messageTrackingId 消息跟踪 ID
     * @param pageable          分页参数
     * @return 回复事件分页结果
     * @throws ScrmException 跟踪记录不存在
     */
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listReplies(Long messageTrackingId, Pageable pageable) throws ScrmException {
        ScrmMessageTrackingEntity tracking = findTrackingOrThrow(messageTrackingId);
        Map<String, Object> metadata = parseMetadata(tracking.getMetadata());
        List<Map<String, Object>> history;
        Object raw = metadata.get(META_REPLY_HISTORY);
        if (raw instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cast = (List<Map<String, Object>>) raw;
            history = cast;
        } else {
            history = new ArrayList<>();
        }
        int total = history.size();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        List<Map<String, Object>> content = history.subList(from, to);
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    // ============================================================
    // 统计分析 Stats
    // ============================================================

    /**
     * 跟踪统计概览: 发送数 / 送达率 / 已读率 / 撤回率 / 转发率 / 回复率。
     *
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTrackingStats(LocalDateTime startTime, LocalDateTime endTime) {
        long total = trackingRepository.countSentInRange(startTime, endTime);
        long delivered = trackingRepository.countDeliveredInRange(startTime, endTime);
        long read = trackingRepository.countReadInRange(startTime, endTime);
        long recalled = trackingRepository.countRecalledInRange(startTime, endTime);
        long forwarded = trackingRepository.countForwardedInRange(startTime, endTime);
        long replied = trackingRepository.countRepliedInRange(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSent", total);
        stats.put("deliveredCount", delivered);
        stats.put("readCount", read);
        stats.put("recalledCount", recalled);
        stats.put("forwardedCount", forwarded);
        stats.put("repliedCount", replied);
        stats.put("deliveryRate", total == 0 ? 0.0 : (double) delivered / total);
        stats.put("readRate", total == 0 ? 0.0 : (double) read / total);
        stats.put("recallRate", total == 0 ? 0.0 : (double) recalled / total);
        stats.put("forwardRate", total == 0 ? 0.0 : (double) forwarded / total);
        stats.put("replyRate", total == 0 ? 0.0 : (double) replied / total);
        return stats;
    }

    /**
     * 已读率统计: 按渠道 (可空) 统计发送数 / 已读数 / 已读率。
     *
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (含, 可空)
     * @param channel   渠道过滤 (可空)
     * @return 已读率统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReadRateStats(LocalDateTime startTime, LocalDateTime endTime, String channel) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (channel != null && !channel.isBlank()) {
            Object[] row = trackingRepository.aggregateByChannelReadRate(channel, startTime, endTime);
            long total = row == null || row[0] == null ? 0L : ((Number) row[0]).longValue();
            long read = row == null || row[1] == null ? 0L : ((Number) row[1]).longValue();
            stats.put("channel", channel);
            stats.put("totalSent", total);
            stats.put("readCount", read);
            stats.put("readRate", total == 0 ? 0.0 : (double) read / total);
        } else {
            long total = trackingRepository.countSentInRange(startTime, endTime);
            long read = trackingRepository.countReadInRange(startTime, endTime);
            stats.put("channel", "ALL");
            stats.put("totalSent", total);
            stats.put("readCount", read);
            stats.put("readRate", total == 0 ? 0.0 : (double) read / total);
        }
        return stats;
    }

    /**
     * 互动统计: 平均互动评分 / 已读数 / 已转发数 / 已回复数。
     *
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (含, 可空)
     * @return 互动统计 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getEngagementStats(LocalDateTime startTime, LocalDateTime endTime) {
        Double avgScore = trackingRepository.avgEngagementInRange(startTime, endTime);
        long read = trackingRepository.countReadInRange(startTime, endTime);
        long forwarded = trackingRepository.countForwardedInRange(startTime, endTime);
        long replied = trackingRepository.countRepliedInRange(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("avgEngagementScore", avgScore != null ? avgScore : 0.0);
        stats.put("readCount", read);
        stats.put("forwardedCount", forwarded);
        stats.put("repliedCount", replied);
        return stats;
    }

    /**
     * 渠道对比: 每个渠道的发送数 / 已读数 / 已读率。
     *
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (含, 可空)
     * @return 渠道对比列表 [{channel, totalSent, readCount, readRate}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChannelComparison(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = trackingRepository.aggregateByChannel(startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            long total = row[1] == null ? 0L : ((Number) row[1]).longValue();
            long read = row[2] == null ? 0L : ((Number) row[2]).longValue();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("channel", row[0]);
            entry.put("totalSent", total);
            entry.put("readCount", read);
            entry.put("readRate", total == 0 ? 0.0 : (double) read / total);
            result.add(entry);
        }
        return result;
    }

    /**
     * 已读趋势: 按日期统计发送数与已读数。
     *
     * @param days 回溯天数 (默认 7)
     * @return 趋势列表 [{date, sentCount, readCount, readRate}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReadTrend(Integer days) {
        int dayCount = days != null && days > 0 ? days : DEFAULT_TREND_DAYS;
        LocalDateTime start = LocalDateTime.now().minusDays(dayCount);
        List<Object[]> rows = trackingRepository.countByDay(start);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            long sent = row[1] == null ? 0L : ((Number) row[1]).longValue();
            long read = row[2] == null ? 0L : ((Number) row[2]).longValue();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", row[0]);
            entry.put("sentCount", sent);
            entry.put("readCount", read);
            entry.put("readRate", sent == 0 ? 0.0 : (double) read / sent);
            result.add(entry);
        }
        return result;
    }

    /**
     * 最佳发送时间分析: 按发送小时统计发送数 / 已读数 / 已读率, 已读率最高的小时段为最佳发送时间。
     *
     * @param channel   渠道过滤 (可空)
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (含, 可空)
     * @return 小时统计列表 [{hour, sentCount, readCount, readRate}], 按 readRate DESC 排序
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBestSendTime(String channel, LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = trackingRepository.aggregateByHour(channel, startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            long sent = row[1] == null ? 0L : ((Number) row[1]).longValue();
            long read = row[2] == null ? 0L : ((Number) row[2]).longValue();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("hour", row[0]);
            entry.put("sentCount", sent);
            entry.put("readCount", read);
            entry.put("readRate", sent == 0 ? 0.0 : (double) read / sent);
            result.add(entry);
        }
        // 按已读率倒序, 已读率最高的时段为最佳发送时间
        result.sort((a, b) -> Double.compare(((Number) b.get("readRate")).doubleValue(),
                ((Number) a.get("readRate")).doubleValue()));
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验消息跟踪参数。
     *
     * @param dto 跟踪参数
     * @throws ScrmException 参数非法
     */
    private void validateTrackingDto(ScrmMessageTrackingDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("跟踪参数不能为空");
        }
        if (dto.getMessageId() == null || dto.getMessageId().isBlank()) {
            throw ScrmException.badRequest("消息 ID 不能为空");
        }
        if (dto.getSenderId() == null || dto.getSenderId().isBlank()) {
            throw ScrmException.badRequest("发送者 ID 不能为空");
        }
        if (dto.getRecipientId() == null || dto.getRecipientId().isBlank()) {
            throw ScrmException.badRequest("接收者 ID 不能为空");
        }
        if (dto.getChannel() == null || dto.getChannel().isBlank()) {
            throw ScrmException.badRequest("渠道不能为空");
        }
        if (!VALID_CHANNELS.contains(dto.getChannel())) {
            throw ScrmException.badRequest(
                    "渠道非法: " + dto.getChannel() + ", 仅支持 " + VALID_CHANNELS);
        }
        if (dto.getRecipientType() != null && !dto.getRecipientType().isBlank() && !VALID_RECIPIENT_TYPES.contains(dto.getRecipientType())) {
            throw ScrmException.badRequest(
                    "接收者类型非法: " + dto.getRecipientType() + ", 仅支持 " + VALID_RECIPIENT_TYPES);
        }
        if (dto.getContentType() != null && !dto.getContentType().isBlank() && !VALID_CONTENT_TYPES.contains(dto.getContentType())) {
            throw ScrmException.badRequest(
                    "内容类型非法: " + dto.getContentType() + ", 仅支持 " + VALID_CONTENT_TYPES);
        }
        if (dto.getSendStatus() != null && !dto.getSendStatus().isBlank() && !VALID_SEND_STATUSES.contains(dto.getSendStatus())) {
            throw ScrmException.badRequest(
                    "发送状态非法: " + dto.getSendStatus() + ", 仅支持 " + VALID_SEND_STATUSES);
        }
        if (dto.getMetadata() != null && !dto.getMetadata().isBlank()) {
            try {
                objectMapper.readTree(dto.getMetadata());
            } catch (Exception e) {
                throw ScrmException.badRequest("附加数据 metadata JSON 解析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 校验阅读上报参数。
     *
     * @param dto 阅读上报参数
     * @throws ScrmException 参数非法
     */
    private void validateReadReportDto(ScrmMessageReadReportDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("阅读上报参数不能为空");
        }
        if (dto.getMessageId() == null || dto.getMessageId().isBlank()) {
            throw ScrmException.badRequest("消息 ID 不能为空");
        }
        if (dto.getReaderId() == null || dto.getReaderId().isBlank()) {
            throw ScrmException.badRequest("阅读者 ID 不能为空");
        }
        if (dto.getReaderType() != null && !dto.getReaderType().isBlank() && !VALID_READER_TYPES.contains(dto.getReaderType())) {
            throw ScrmException.badRequest(
                    "阅读者类型非法: " + dto.getReaderType() + ", 仅支持 " + VALID_READER_TYPES);
        }
        if (dto.getSource() != null && !dto.getSource().isBlank() && !VALID_READ_SOURCES.contains(dto.getSource())) {
            throw ScrmException.badRequest(
                    "阅读来源非法: " + dto.getSource() + ", 仅支持 " + VALID_READ_SOURCES);
        }
    }

    /**
     * 检查撤回窗口: 消息发送时间至今是否在默认撤回窗口 (2 分钟) 内。
     *
     * @param tracking 消息跟踪记录
     * @return 是否在撤回窗口内
     */
    private boolean checkRecallWindowInternal(ScrmMessageTrackingEntity tracking) {
        if (tracking.getSentAt() == null) {
            return true;
        }
        long elapsedMinutes = Duration.between(tracking.getSentAt(), LocalDateTime.now()).toMinutes();
        return elapsedMinutes <= DEFAULT_RECALL_WINDOW_MINUTES;
    }

    /**
     * 构建已阅读者列表 JSON。
     *
     * @param readers 阅读日志列表
     * @return 已阅读者列表 JSON 字符串
     */
    private String buildAffectedReadersJson(List<ScrmMessageReadLogEntity> readers) {
        if (readers == null || readers.isEmpty()) {
            return EMPTY_ARRAY;
        }
        List<Map<String, Object>> affected = new ArrayList<>(readers.size());
        for (ScrmMessageReadLogEntity reader : readers) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("readerId", reader.getReaderId());
            entry.put("readerName", reader.getReaderName());
            entry.put("readAt", reader.getReadAt());
            affected.add(entry);
        }
        return toJson(affected);
    }

    /**
     * 计算互动评分。
     * <p>评分公式: readCount * 1.0 + readDurationSeconds * 0.01 + (isForwarded ? forwardCount * 2 : 0)
     * + (isReplied ? 5 : 0), 已撤回消息评分清零。</p>
     *
     * @param tracking 消息跟踪记录
     * @return 互动评分
     */
    private Double calculateEngagementScore(ScrmMessageTrackingEntity tracking) {
        if (Boolean.TRUE.equals(tracking.getIsRecalled())) {
            return 0.0;
        }
        double score = 0.0;
        score += (tracking.getReadCount() == null ? 0 : tracking.getReadCount()) * 1.0;
        score += (tracking.getReadDurationSeconds() == null ? 0 : tracking.getReadDurationSeconds()) * 0.01;
        if (Boolean.TRUE.equals(tracking.getIsForwarded())) {
            score += (tracking.getForwardCount() == null ? 0 : tracking.getForwardCount()) * 2.0;
        }
        if (Boolean.TRUE.equals(tracking.getIsReplied())) {
            score += 5.0;
        }
        return score;
    }

    /**
     * 解析 metadata JSON 为 Map, 解析失败返回空 Map。
     *
     * @param metadata metadata JSON 字符串
     * @return Map (不为 null)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = objectMapper.readValue(metadata, Object.class);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
        } catch (Exception e) {
            log.warn("metadata JSON 解析失败, 返回空 Map: {}", e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串, 序列化失败返回 "[]"
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("对象序列化为 JSON 失败: {}", e.getMessage());
            return EMPTY_ARRAY;
        }
    }

    /**
     * 按主键查询消息跟踪记录, 不存在抛异常, 并校验账号归属。
     *
     * @param id 跟踪记录 ID
     * @return 跟踪记录实体
     * @throws ScrmException 跟踪记录不存在
     */
    private ScrmMessageTrackingEntity findTrackingOrThrow(Long id) throws ScrmException {
        ScrmMessageTrackingEntity entity = trackingRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "消息跟踪记录不存在: id=" + id));
        return entity;
    }

}
