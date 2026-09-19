/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationCenterQueryService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmNotificationDto;
import org.hiylo.scrm.entity.ScrmNotificationEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 通知中心查询与已读管理服务。
 * <p>
 * 承载通知记录的查询与已读管理能力: 通知详情 / 分页查询, 单条 / 批量 / 全部已读,
 * 以及用户未读站内信计数。实体查找与 DTO 转换复用发送服务提供的公共能力。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmNotificationCenterQueryService {

    /** 通知记录数据访问层 */
    private final ScrmNotificationRepository notificationRepository;
    /** 模板管理服务 (分页排序工具) */
    private final ScrmNotificationCenterTemplateService templateService;
    /** 发送服务 (通知实体查找 / DTO 转换) */
    private final ScrmNotificationCenterSendService sendService;

    /**
     * 查询通知详情。
     *
     * @param id 通知 ID
     * @return 通知 DTO
     * @throws ScrmException 通知不存在
     */
    @Transactional(readOnly = true)
    public ScrmNotificationDto getNotification(Long id) throws ScrmException {
        return sendService.toNotificationDto(sendService.findNotificationOrThrow(id));
    }

    /**
     * 分页查询通知, 支持按渠道 / 分类 / 状态 / 接收者 / 时间区间过滤。
     *
     * @param channel     渠道过滤 (可空)
     * @param category    分类过滤 (可空)
     * @param status      状态过滤 (可空)
     * @param recipientId 接收者 ID 过滤 (可空)
     * @param startTime   发送时间起点 (含, 可空)
     * @param endTime     发送时间终点 (不含, 可空)
     * @param pageable    分页参数
     * @return 通知分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmNotificationDto> listNotifications(String channel, String category, String status,
                                                       String recipientId, LocalDateTime startTime,
                                                       LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmNotificationEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (recipientId != null && !recipientId.isBlank()) {
                predicates.add(cb.equal(root.get("recipientId"), recipientId));
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
        return notificationRepository.findAll(spec, templateService.ensureSort(pageable, "createTime"))
                .map(sendService::toNotificationDto);
    }

    /**
     * 标记通知为已读 (状态置为 READ, 记录已读时间)。
     *
     * @param id 通知 ID
     * @return 更新后的通知
     * @throws ScrmException 通知不存在
     */
    @Transactional
    public ScrmNotificationDto markAsRead(Long id) throws ScrmException {
        ScrmNotificationEntity entity = sendService.findNotificationOrThrow(id);
        if (!ScrmNotificationCenterSendService.STATUS_READ.equals(entity.getStatus())) {
            entity.setStatus(ScrmNotificationCenterSendService.STATUS_READ);
            entity.setReadAt(LocalDateTime.now());
            entity = notificationRepository.save(entity);
        }
        log.info("标记通知已读: id={}", id);
        return sendService.toNotificationDto(entity);
    }

    /**
     * 批量标记通知为已读。
     *
     * @param ids 通知 ID 列表
     * @return 已标记的通知列表
     */
    @Transactional
    public List<ScrmNotificationDto> batchMarkAsRead(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ScrmNotificationDto> result = new ArrayList<>();
        for (Long id : ids) {
            try {
                result.add(markAsRead(id));
            } catch (ScrmException e) {
                log.warn("批量标记已读失败: id={}, error={}", id, e.getMessage());
            }
        }
        log.info("批量标记已读完成: total={}, success={}", ids.size(), result.size());
        return result;
    }

    /**
     * 将用户的全部未读站内信标记为已读。
     *
     * @param userId 用户 ID
     * @return 影响行数
     */
    @Transactional
    public int markAllAsRead(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        int updated = notificationRepository.markAllAsRead(userId, LocalDateTime.now());
        log.info("全部标记已读: userId={}, updated={}", userId, updated);
        return updated;
    }

    /**
     * 获取用户未读站内信数。
     *
     * @param userId 用户 ID
     * @return 未读数
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        return notificationRepository.countUnread(userId);
    }
}