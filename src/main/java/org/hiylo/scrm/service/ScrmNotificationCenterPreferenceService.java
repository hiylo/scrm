/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationCenterPreferenceService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmNotificationPreferenceDto;
import org.hiylo.scrm.entity.ScrmNotificationPreferenceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmNotificationPreferenceRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 通知中心偏好与统计服务。
 * <p>
 * 承载通知偏好的全部能力 (查询 / 更新 / 列表 / 发送前偏好检查, 含免打扰时段判定),
 * 以及通知统计能力 (总览 / 渠道 / 分类 / 送达率)。
 * </p>
 * <p>
 * 偏好检查综合启用状态 / 最低优先级 / 免打扰时段判断是否允许发送, 无偏好记录时默认允许;
 * 统计中的状态常量复用发送服务定义的共享常量。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmNotificationCenterPreferenceService {

    /** 通知偏好数据访问层 */
    private final ScrmNotificationPreferenceRepository preferenceRepository;
    /** 通知记录数据访问层 (统计聚合) */
    private final ScrmNotificationRepository notificationRepository;

    /**
     * 查询用户通知偏好列表。
     *
     * @param userId 用户 ID
     * @return 偏好列表
     */
    @Transactional(readOnly = true)
    public List<ScrmNotificationPreferenceDto> getPreference(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return preferenceRepository.findByUserId(userId).stream()
                .map(this::toPreferenceDto).toList();
    }

    /**
     * 更新 (或创建) 用户通知偏好。
     * <p>按 + 用户 + 渠道 + 分类定位偏好, 不存在则新建。仅更新非空字段。</p>
     *
     * @param userId   用户 ID
     * @param channel  通知渠道
     * @param category 分类
     * @param dto      偏好参数
     * @return 更新后的偏好
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmNotificationPreferenceDto updatePreference(String userId, String channel, String category,
                                                           ScrmNotificationPreferenceDto dto)
                                                               throws ScrmException {
        if (userId == null || userId.isBlank()) {
            throw ScrmException.badRequest("用户 ID 不能为空");
        }
        if (channel == null || channel.isBlank()) {
            throw ScrmException.badRequest("通知渠道不能为空");
        }
        if (category == null || category.isBlank()) {
            throw ScrmException.badRequest("分类不能为空");
        }
        if (dto == null) {
            throw ScrmException.badRequest("偏好参数不能为空");
        }
        ScrmNotificationPreferenceEntity entity = preferenceRepository
                .findByUserIdAndChannelAndCategory(userId, channel, category)
                .orElseGet(() -> {
                    ScrmNotificationPreferenceEntity pref = new ScrmNotificationPreferenceEntity();
                    pref.setUserId(userId);
                    pref.setChannel(channel);
                    pref.setCategory(category);
                    pref.setEnabled(ScrmNotificationCenterTemplateService.DEFAULT_ENABLED);
                    pref.setMinPriority(0);
                    return pref;
                });
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getQuietHoursStart() != null) entity.setQuietHoursStart(dto.getQuietHoursStart());
        if (dto.getQuietHoursEnd() != null) entity.setQuietHoursEnd(dto.getQuietHoursEnd());
        if (dto.getMinPriority() != null) entity.setMinPriority(dto.getMinPriority());
        entity = preferenceRepository.save(entity);
        log.info("更新通知偏好: userId={}, channel={}, category={}, enabled={}",
                userId, channel, category, entity.getEnabled());
        return toPreferenceDto(entity);
    }

    /**
     * 查询用户通知偏好列表 (与 getPreference 等价, 显式列表入口)。
     *
     * @param userId 用户 ID
     * @return 偏好列表
     */
    @Transactional(readOnly = true)
    public List<ScrmNotificationPreferenceDto> listPreferences(String userId) {
        return getPreference(userId);
    }

    /**
     * 检查偏好 (是否允许发送)。
     * <p>无偏好记录时默认允许; 偏好禁用 / 优先级低于阈值 / 当前时间处于免打扰时段则拒绝。</p>
     *
     * @param userId   用户 ID
     * @param channel  通知渠道
     * @param category 分类
     * @param priority 通知优先级
     * @return true 允许发送, false 拒绝
     */
    @Transactional(readOnly = true)
    public boolean checkPreference(String userId, String channel, String category, int priority) {
        if (userId == null || userId.isBlank()) {
            return true;
        }
        return preferenceRepository
                .findByUserIdAndChannelAndCategory(userId, channel, category)
                .map(pref -> {
                    if (Boolean.FALSE.equals(pref.getEnabled())) {
                        return false;
                    }
                    int minPriority = pref.getMinPriority() != null ? pref.getMinPriority() : 0;
                    if (priority < minPriority) {
                        return false;
                    }
                    return !inQuietHours(pref.getQuietHoursStart(), pref.getQuietHoursEnd());
                })
                .orElse(true);
    }

    /**
     * 获取通知总览统计 (发送数 / 成功率 / 已读率 / 各渠道分布)。
     *
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (不含, 可空)
     * @return 总览统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getNotificationStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDate.of(1970, 1, 1).atStartOfDay();
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now().plusYears(100);
        Map<String, Object> stats = new LinkedHashMap<>();
        // 已发送数 = SENT + DELIVERED + READ (成功推出)
        long sent = countByStatus(ScrmNotificationCenterSendService.STATUS_SENT, start, end)
                + countByStatus(ScrmNotificationCenterSendService.STATUS_DELIVERED, start, end)
                + countByStatus(ScrmNotificationCenterSendService.STATUS_READ, start, end);
        long failed = countByStatus(ScrmNotificationCenterSendService.STATUS_FAILED, start, end);
        long read = countByStatus(ScrmNotificationCenterSendService.STATUS_READ, start, end);
        stats.put("sentCount", sent);
        stats.put("failedCount", failed);
        stats.put("readCount", read);
        long attempted = sent + failed;
        stats.put("successRate", attempted > 0 ? Math.round((double) sent / attempted * 10000) / 100.0 : 0.0);
        stats.put("readRate", sent > 0 ? Math.round((double) read / sent * 10000) / 100.0 : 0.0);
        // 各渠道分布
        Map<String, Long> channelDistribution = new LinkedHashMap<>();
        for (Object[] row : notificationRepository.countByChannel(start, end)) {
            channelDistribution.put((String) row[0], (Long) row[1]);
        }
        stats.put("channelDistribution", channelDistribution);
        return stats;
    }

    /**
     * 获取渠道统计 (每渠道发送数)。
     *
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (不含, 可空)
     * @return 渠道统计 (channel → count)
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getChannelStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDate.of(1970, 1, 1).atStartOfDay();
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now().plusYears(100);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : notificationRepository.countByChannel(start, end)) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }

    /**
     * 获取分类统计 (每分类发送数)。
     *
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (不含, 可空)
     * @return 分类统计 (category → count)
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getCategoryStats(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDate.of(1970, 1, 1).atStartOfDay();
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now().plusYears(100);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : notificationRepository.countByCategory(start, end)) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }

    /**
     * 获取送达率 (已送达 / 已发送)。
     * <p>已送达 = DELIVERED + READ, 已发送 = SENT + DELIVERED + READ。</p>
     *
     * @param channel   渠道 (可空, 为空则统计全部渠道)
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (不含, 可空)
     * @return 送达率 (百分比, 保留两位小数)
     */
    @Transactional(readOnly = true)
    public double getDeliveryRate(String channel, LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDate.of(1970, 1, 1).atStartOfDay();
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now().plusYears(100);
        long sent;
        long delivered;
        if (channel != null && !channel.isBlank()) {
            sent = notificationRepository.countByChannelAndTimeRange(channel, start, end);
        } else {
            sent = 0;
            for (Object[] row : notificationRepository.countByChannel(start, end)) {
                sent += (Long) row[1];
            }
        }
        delivered = countByStatus(ScrmNotificationCenterSendService.STATUS_DELIVERED, start, end)
                + countByStatus(ScrmNotificationCenterSendService.STATUS_READ, start, end);
        return sent > 0 ? Math.round((double) delivered / sent * 10000) / 100.0 : 0.0;
    }

    /**
     * 判断当前时间是否处于免打扰时段。
     * <p>支持跨午夜时段 (start > end), 任一端为空视为无免打扰。</p>
     *
     * @param startStr 开始时间 HH:mm (可空)
     * @param endStr   结束时间 HH:mm (可空)
     * @return true 处于免打扰时段
     */
    private boolean inQuietHours(String startStr, String endStr) {
        if (startStr == null || startStr.isBlank() || endStr == null || endStr.isBlank()) {
            return false;
        }
        try {
            LocalTime start = LocalTime.parse(startStr);
            LocalTime end = LocalTime.parse(endStr);
            LocalTime now = LocalTime.now();
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            }
            // 跨午夜: now >= start 或 now < end
            return !now.isBefore(start) || now.isBefore(end);
        } catch (DateTimeParseException e) {
            log.warn("免打扰时段格式非法: start={}, end={}", startStr, endStr);
            return false;
        }
    }

    /**
     * 统计账号下指定状态与时间区间的通知数。
     *
     * @param status 状态
     * @param start  区间起点 (含)
     * @param end    区间终点 (不含)
     * @return 数量
     */
    private long countByStatus(String status, LocalDateTime start, LocalDateTime end) {
        return notificationRepository.countByStatusAndTimeRange(status, start, end);
    }

    /**
     * 偏好实体转 DTO。
     */
    private ScrmNotificationPreferenceDto toPreferenceDto(ScrmNotificationPreferenceEntity entity) {
        ScrmNotificationPreferenceDto dto = new ScrmNotificationPreferenceDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setChannel(entity.getChannel());
        dto.setCategory(entity.getCategory());
        dto.setEnabled(entity.getEnabled());
        dto.setQuietHoursStart(entity.getQuietHoursStart());
        dto.setQuietHoursEnd(entity.getQuietHoursEnd());
        dto.setMinPriority(entity.getMinPriority());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}