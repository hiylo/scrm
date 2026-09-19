/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyLogService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmAutoReplyLogDto;
import org.hiylo.scrm.entity.ScrmAutoReplyLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAutoReplyLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 自动回复日志服务。
 * <p>
 * 承载回复日志的记录 (命中/兜底回复统一入口)、日志详情查询与多条件分页检索。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
public class ScrmAutoReplyLogService {

    /** 默认发送状态 */
    private static final String DEFAULT_STATUS = "SENT";

    /** 自动回复日志数据访问层 */
    private final ScrmAutoReplyLogRepository logRepository;

    /**
     * 记录回复日志。
     *
     * @param dto 日志参数
     * @return 持久化后的日志实体
     */
    @Transactional
    public ScrmAutoReplyLogEntity recordLog(ScrmAutoReplyLogDto dto) {
        ScrmAutoReplyLogEntity entity = new ScrmAutoReplyLogEntity();
        entity.setRuleId(dto.getRuleId());
        entity.setRuleName(dto.getRuleName());
        entity.setRuleType(dto.getRuleType());
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setAccountId(dto.getAccountId());
        entity.setChannel(dto.getChannel());
        entity.setIncomingMessage(dto.getIncomingMessage());
        entity.setMatchedKeyword(dto.getMatchedKeyword());
        entity.setMatchScore(dto.getMatchScore() != null ? dto.getMatchScore() : 0.0);
        entity.setReplyType(dto.getReplyType());
        entity.setReplyContent(dto.getReplyContent());
        entity.setSentAt(dto.getSentAt() != null ? dto.getSentAt() : LocalDateTime.now());
        entity.setResponseTimeMs(dto.getResponseTimeMs());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : DEFAULT_STATUS);
        entity.setErrorMessage(dto.getErrorMessage());
        entity.setIsFallback(dto.getIsFallback() != null ? dto.getIsFallback() : false);
        entity.setSessionId(dto.getSessionId());
        entity.setMetadata(dto.getMetadata());
        return logRepository.save(entity);
    }

    /**
     * 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志实体
     * @throws ScrmException 日志不存在
     */
    @Transactional(readOnly = true)
    public ScrmAutoReplyLogEntity getLog(Long id) throws ScrmException {
        ScrmAutoReplyLogEntity entity = logRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "回复日志不存在: id=" + id));

        return entity;
    }

    /**
     * 分页查询回复日志, 支持按规则 ID、客户 ID、渠道、状态与时间范围过滤。
     *
     * @param ruleId    规则 ID 过滤（可空）
     * @param customerId 客户 ID 过滤（可空）
     * @param channel   渠道过滤（可空）
     * @param status    发送状态过滤（可空）
     * @param startTime 起始时间 (含, 可空)
     * @param endTime   截止时间 (含, 可空)
     * @param pageable  分页参数
     * @return 回复日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAutoReplyLogEntity> listLogs(Long ruleId, Long customerId, String channel,
                                                  String status, LocalDateTime startTime, LocalDateTime endTime,
                                                  Pageable pageable) {
        Specification<ScrmAutoReplyLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ruleId != null) {
                predicates.add(cb.equal(root.get("ruleId"), ruleId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null && endTime != null) {
                predicates.add(cb.between(root.get("sentAt"), startTime, endTime));
            } else if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sentAt"), startTime));
            } else if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sentAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return logRepository.findAll(spec, pageable);
    }

    /**
     * 按客户 ID 分页查询回复日志。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 回复日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAutoReplyLogEntity> getLogsByCustomer(Long customerId, Pageable pageable) {
        return logRepository.findByCustomerIdOrderBySentAtDesc(
                 customerId, pageable);
    }

    /**
     * 查询最近 N 条回复日志。
     *
     * @param limit 返回条数上限
     * @return 回复日志列表
     */
    @Transactional(readOnly = true)
    public List<ScrmAutoReplyLogEntity> getRecentLogs(int limit) {
        int safeLimit = limit > 0 ? limit : 10;
        return logRepository.findAllByOrderBySentAtDesc(
                 PageRequest.of(0, safeLimit));
    }
}
