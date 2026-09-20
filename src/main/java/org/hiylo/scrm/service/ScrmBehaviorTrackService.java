/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBehaviorTrackService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmBehaviorQueryDto;
import org.hiylo.scrm.dto.ScrmBehaviorRecordDto;
import org.hiylo.scrm.dto.ScrmTouchpointDto;
import org.hiylo.scrm.entity.ScrmBehaviorPathEntity;
import org.hiylo.scrm.entity.ScrmBehaviorTrackEntity;
import org.hiylo.scrm.entity.ScrmTouchpointEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmBehaviorPathRepository;
import org.hiylo.scrm.repository.ScrmBehaviorTrackRepository;
import org.hiylo.scrm.repository.ScrmTouchpointRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * SCRM 客户行为轨迹服务。
 * <p>
 * 承载客户在各触点行为追踪的核心能力, 分为四组能力:
 * <ul>
 *   <li>行为记录: 单条 / 批量记录行为事件, 行为时间线, 按类型 / 触点查询</li>
 *   <li>触点管理: 触点增删改查, 启用 / 禁用, 触点统计重算 (记录行为时增量维护)</li>
 *   <li>行为路径: 从会话行为事件聚合路径, 常见路径 / 转化路径 / 流失点分析</li>
 *   <li>行为分析: 行为统计概览, 漏斗统计, 触点效果对比, 行为趋势, 热力图, 转化归因</li>
 * </ul>
 * </p>
 * <p>
 * 路径构建 ({@link #buildPath}): 加载客户 + 会话内行为事件 (behaviorTime ASC) →
 * 生成行为序列 JSON [{type, time, touchpoint, page}] → 聚合触点列表 / 入口出口 /
 * 总行为数 / 总停留时长 / 转化标记 → 已存在则更新, 否则新建。
 * </p>
 * <p>
 * 所有写操作写入归属账号实现数据隔离, 越权访问按不存在处理。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmBehaviorTrackService {

    /** 默认转化价值 */
    private static final double DEFAULT_CONVERSION_VALUE = 0.0;

    /** 默认趋势回溯天数 */
    private static final int DEFAULT_TREND_DAYS = 7;

    /** 默认常见路径返回条数 */
    private static final int DEFAULT_COMMON_PATH_LIMIT = 10;

    /** 默认转化路径返回条数 */
    private static final int DEFAULT_CONVERSION_PATH_LIMIT = 20;

    /** 行为序列 JSON 空数组 */
    private static final String EMPTY_SEQUENCE = "[]";

    /** 合法的设备类型 */
    private static final List<String> VALID_DEVICE_TYPES = List.of(
            "MOBILE", "PC", "TABLET", "TV", "OTHER");

    /** 合法的漏斗阶段 (有序, 用于转化率计算) */
    private static final List<String> FUNNEL_STAGES = List.of(
            "AWARENESS", "INTEREST", "DESIRE", "ACTION", "RETENTION");

    /** 合法的触点类型 */
    private static final List<String> VALID_TOUCHPOINTS = List.of(
            "WEBSITE", "APP", "WECHAT_OFFICIAL", "WECHAT_MINI", "WORK_WECHAT",
            "DOUYIN", "KUAISHOU", "XIAOHONGSHU", "STORE", "PHONE", "EMAIL", "SMS", "OTHER");

    /** 合法的行为类型 */
    private static final List<String> VALID_BEHAVIOR_TYPES = List.of(
            "PAGE_VIEW", "CLICK", "SCROLL", "SEARCH", "FORM_SUBMIT",
            "VIDEO_PLAY", "VIDEO_COMPLETE", "SHARE", "FAVORITE", "COMMENT",
            "PURCHASE", "ADD_TO_CART", "REMOVE_FROM_CART", "CHECKOUT", "PAYMENT",
            "LOGIN", "LOGOUT", "DOWNLOAD", "UPLOAD", "CALL",
            "MESSAGE_SEND", "MESSAGE_READ", "APPOINTMENT", "CANCEL", "REFUND", "REVIEW");

    /** 行为事件数据访问层 */
    private final ScrmBehaviorTrackRepository trackRepository;

    /** 触点管理数据访问层 */
    private final ScrmTouchpointRepository touchpointRepository;

    /** 行为路径数据访问层 */
    private final ScrmBehaviorPathRepository pathRepository;

    /** JSON 解析器 (解析 / 序列化 behaviorSequence / metadata / config) */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 行为记录
    // ============================================================

    /**
     * 记录客户行为事件。
     * <p>校验 behaviorType / touchpoint / funnelStage 合法性后写入归属账号 ID 持久化,
     * behaviorTime 缺省取当前时间, isConversion 缺省 false, conversionValue 缺省 0。
     * 写入后增量更新触点统计 (累计事件数 / 转化数), 失败仅告警不阻断。</p>
     *
     * @param recordDto 行为记录参数
     * @return 创建后的行为事件
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmBehaviorTrackEntity recordBehavior(ScrmBehaviorRecordDto recordDto) throws ScrmException {
        validateRecordDto(recordDto);
        LocalDateTime behaviorTime = recordDto.getBehaviorTime() != null
                ? recordDto.getBehaviorTime() : LocalDateTime.now();
        ScrmBehaviorTrackEntity entity = new ScrmBehaviorTrackEntity();
        entity.setCustomerId(recordDto.getCustomerId());
        entity.setCustomerName(recordDto.getCustomerName());
        entity.setAccountId(recordDto.getAccountId());
        entity.setBehaviorType(recordDto.getBehaviorType());
        entity.setTouchpoint(recordDto.getTouchpoint());
        entity.setPageUrl(recordDto.getPageUrl());
        entity.setPageTitle(recordDto.getPageTitle());
        entity.setReferrer(recordDto.getReferrer());
        entity.setBehaviorTime(behaviorTime);
        entity.setDurationSeconds(recordDto.getDurationSeconds());
        entity.setDeviceType(recordDto.getDeviceType());
        entity.setOs(recordDto.getOs());
        entity.setBrowser(recordDto.getBrowser());
        entity.setAppVersion(recordDto.getAppVersion());
        entity.setIp(recordDto.getIp());
        entity.setLocation(recordDto.getLocation());
        entity.setSessionId(recordDto.getSessionId());
        entity.setMetadata(recordDto.getMetadata());
        entity.setUtmSource(recordDto.getUtmSource());
        entity.setUtmMedium(recordDto.getUtmMedium());
        entity.setUtmCampaign(recordDto.getUtmCampaign());
        entity.setUtmContent(recordDto.getUtmContent());
        entity.setUtmTerm(recordDto.getUtmTerm());
        entity.setConversionValue(recordDto.getConversionValue() != null
                ? recordDto.getConversionValue() : DEFAULT_CONVERSION_VALUE);
        boolean conversion = recordDto.getIsConversion() != null && recordDto.getIsConversion();
        entity.setIsConversion(conversion);
        entity.setFunnelStage(recordDto.getFunnelStage());
        entity = trackRepository.save(entity);
        // 增量更新触点统计 (best-effort)
        updateTouchpointStatsOnEvent(entity.getTouchpoint(), behaviorTime, conversion);
        log.info("记录客户行为: id={}, customerId={}, behaviorType={}, touchpoint={}, conversion={}",
                entity.getId(), entity.getCustomerId(), entity.getBehaviorType(),
                entity.getTouchpoint(), conversion);
        return entity;
    }

    /**
     * 批量记录客户行为事件。
     *
     * @param records 行为记录列表
     * @return 创建后的行为事件列表
     * @throws ScrmException 参数非法
     */
    @Transactional
    public List<ScrmBehaviorTrackEntity> batchRecord(List<ScrmBehaviorRecordDto> records) throws ScrmException {
        if (records == null || records.isEmpty()) {
            throw ScrmException.badRequest("行为记录列表不能为空");
        }
        List<ScrmBehaviorTrackEntity> result = new ArrayList<>(records.size());
        for (ScrmBehaviorRecordDto record : records) {
            result.add(recordBehavior(record));
        }
        log.info("批量记录客户行为: count={}", records.size());
        return result;
    }

    /**
     * 查询行为事件详情。
     *
     * @param id 行为事件 ID
     * @return 行为事件实体
     * @throws ScrmException 行为事件不存在
     */
    @Transactional(readOnly = true)
    public ScrmBehaviorTrackEntity getTrack(Long id) throws ScrmException {
        return findTrackOrThrow(id);
    }

    /**
     * 分页查询行为事件, 支持按客户 / 行为类型 / 触点 / 时间范围 / 漏斗阶段组合过滤。
     *
     * @param queryDto 查询参数
     * @param pageable 分页参数
     * @return 行为事件分页结果 (按 behaviorTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmBehaviorTrackEntity> listTracks(ScrmBehaviorQueryDto queryDto, Pageable pageable) {
        final ScrmBehaviorQueryDto query = queryDto != null ? queryDto : new ScrmBehaviorQueryDto();
        Specification<ScrmBehaviorTrackEntity> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.getCustomerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), query.getCustomerId()));
            }
            if (query.getBehaviorType() != null && !query.getBehaviorType().isBlank()) {
                predicates.add(cb.equal(root.get("behaviorType"), query.getBehaviorType()));
            }
            if (query.getTouchpoint() != null && !query.getTouchpoint().isBlank()) {
                predicates.add(cb.equal(root.get("touchpoint"), query.getTouchpoint()));
            }
            if (query.getStartTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("behaviorTime"), query.getStartTime()));
            }
            if (query.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("behaviorTime"), query.getEndTime()));
            }
            if (query.getFunnelStage() != null && !query.getFunnelStage().isBlank()) {
                predicates.add(cb.equal(root.get("funnelStage"), query.getFunnelStage()));
            }
            q.orderBy(cb.desc(root.get("behaviorTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return trackRepository.findAll(spec, pageable);
    }

    /**
     * 客户行为时间线: 按客户与时间范围查询行为 (按行为时间升序)。
     *
     * @param customerId 客户 ID
     * @param startTime  行为时间起点 (含, 可空)
     * @param endTime    行为时间终点 (含, 可空)
     * @return 行为事件列表 (behaviorTime ASC)
     */
    @Transactional(readOnly = true)
    public List<ScrmBehaviorTrackEntity> getCustomerTimeline(Long customerId, LocalDateTime startTime,
                                                              LocalDateTime endTime) {
        return trackRepository.findCustomerTimeline(
                 customerId, startTime, endTime);
    }

    /**
     * 按行为类型分页查询行为事件。
     *
     * @param behaviorType 行为类型
     * @param startTime    行为时间起点 (含, 可空)
     * @param endTime      行为时间终点 (含, 可空)
     * @param pageable     分页参数
     * @return 行为事件分页结果 (按 behaviorTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmBehaviorTrackEntity> getTracksByType(String behaviorType, LocalDateTime startTime,
                                                          LocalDateTime endTime, Pageable pageable) {
        return trackRepository.findByType(
                 behaviorType, startTime, endTime, pageable);
    }

    /**
     * 按触点分页查询行为事件。
     *
     * @param touchpoint 触点
     * @param startTime  行为时间起点 (含, 可空)
     * @param endTime    行为时间终点 (含, 可空)
     * @param pageable   分页参数
     * @return 行为事件分页结果 (按 behaviorTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmBehaviorTrackEntity> getTracksByTouchpoint(String touchpoint, LocalDateTime startTime,
                                                                LocalDateTime endTime, Pageable pageable) {
        return trackRepository.findByTouchpoint(
                 touchpoint, startTime, endTime, pageable);
    }

    // ============================================================
    // 触点管理
    // ============================================================

    /**
     * 创建触点。
     * <p>校验触点编码唯一与触点类型合法性后写入归属账号 ID 持久化,
     * isActive 缺省 true。</p>
     *
     * @param dto 触点参数
     * @return 创建后的触点
     * @throws ScrmException 触点编码重复 / 参数非法
     */
    @Transactional
    public ScrmTouchpointEntity createTouchpoint(ScrmTouchpointDto dto) throws ScrmException {
        validateTouchpointDto(dto, false);
        if (touchpointRepository.existsByTouchpointCode(dto.getTouchpointCode())) {
            throw ScrmException.conflict("触点编码已存在: " + dto.getTouchpointCode());
        }
        ScrmTouchpointEntity entity = new ScrmTouchpointEntity();
        entity.setTouchpointName(dto.getTouchpointName());
        entity.setTouchpointCode(dto.getTouchpointCode());
        entity.setTouchpointType(dto.getTouchpointType());
        entity.setUrl(dto.getUrl());
        entity.setAppId(dto.getAppId());
        entity.setDescription(dto.getDescription());
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        entity.setTotalEvents(0);
        entity.setUniqueVisitors(0);
        entity.setConversionCount(0);
        entity.setConfig(dto.getConfig());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = touchpointRepository.save(entity);
        log.info("创建触点: id={}, code={}, type={}",
                entity.getId(), entity.getTouchpointCode(), entity.getTouchpointType());
        return entity;
    }

    /**
     * 更新触点（字段非空才覆盖, touchpointCode 不允许变更）。
     *
     * @param id  触点 ID
     * @param dto 触点参数
     * @return 更新后的触点
     * @throws ScrmException 触点不存在 / 参数非法
     */
    @Transactional
    public ScrmTouchpointEntity updateTouchpoint(Long id, ScrmTouchpointDto dto) throws ScrmException {
        ScrmTouchpointEntity entity = findTouchpointOrThrow(id);
        validateTouchpointDto(dto, true);
        if (dto.getTouchpointName() != null) entity.setTouchpointName(dto.getTouchpointName());
        if (dto.getTouchpointType() != null) entity.setTouchpointType(dto.getTouchpointType());
        if (dto.getUrl() != null) entity.setUrl(dto.getUrl());
        if (dto.getAppId() != null) entity.setAppId(dto.getAppId());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());
        if (dto.getConfig() != null) entity.setConfig(dto.getConfig());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = touchpointRepository.save(entity);
        log.info("更新触点: id={}, code={}", entity.getId(), entity.getTouchpointCode());
        return entity;
    }

    /**
     * 删除触点。
     *
     * @param id 触点 ID
     * @throws ScrmException 触点不存在
     */
    @Transactional
    public void deleteTouchpoint(Long id) throws ScrmException {
        ScrmTouchpointEntity entity = findTouchpointOrThrow(id);
        touchpointRepository.delete(entity);
        log.info("删除触点: id={}, code={}", id, entity.getTouchpointCode());
    }

    /**
     * 查询触点详情。
     *
     * @param id 触点 ID
     * @return 触点实体
     * @throws ScrmException 触点不存在
     */
    @Transactional(readOnly = true)
    public ScrmTouchpointEntity getTouchpoint(Long id) throws ScrmException {
        return findTouchpointOrThrow(id);
    }

    /**
     * 按触点编码查询触点。
     *
     * @param code 触点编码
     * @return 触点实体
     * @throws ScrmException 触点不存在
     */
    @Transactional(readOnly = true)
    public ScrmTouchpointEntity getTouchpointByCode(String code) throws ScrmException {
        ScrmTouchpointEntity entity = touchpointRepository
                .findByTouchpointCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "触点不存在: code=" + code));
        return entity;
    }

    /**
     * 分页查询触点, 支持按触点类型与启用状态过滤。
     *
     * @param touchpointType 触点类型过滤（可空）
     * @param isActive       启用状态过滤（可空）
     * @param pageable       分页参数
     * @return 触点分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmTouchpointEntity> listTouchpoints(String touchpointType, Boolean isActive, Pageable pageable) {
        if (touchpointType != null && !touchpointType.isBlank()) {
            return touchpointRepository.findByTouchpointType(touchpointType, pageable);
        }
        if (isActive != null) {
            return touchpointRepository.findByIsActive(isActive, pageable);
        }
        return touchpointRepository.findAll(pageable);
    }

    /**
     * 启用触点。
     *
     * @param id 触点 ID
     * @return 更新后的触点
     * @throws ScrmException 触点不存在
     */
    @Transactional
    public ScrmTouchpointEntity activateTouchpoint(Long id) throws ScrmException {
        ScrmTouchpointEntity entity = findTouchpointOrThrow(id);
        entity.setIsActive(true);
        entity = touchpointRepository.save(entity);
        log.info("启用触点: id={}, code={}", id, entity.getTouchpointCode());
        return entity;
    }

    /**
     * 禁用触点。
     *
     * @param id 触点 ID
     * @return 更新后的触点
     * @throws ScrmException 触点不存在
     */
    @Transactional
    public ScrmTouchpointEntity deactivateTouchpoint(Long id) throws ScrmException {
        ScrmTouchpointEntity entity = findTouchpointOrThrow(id);
        entity.setIsActive(false);
        entity = touchpointRepository.save(entity);
        log.info("禁用触点: id={}, code={}", id, entity.getTouchpointCode());
        return entity;
    }

    /**
     * 重算触点统计: 从行为事件表聚合事件总数 / 独立访客 / 转化数 / 最近事件时间。
     *
     * @param id 触点 ID
     * @return 更新后的触点
     * @throws ScrmException 触点不存在
     */
    @Transactional
    public ScrmTouchpointEntity updateTouchpointStats(Long id) throws ScrmException {
        ScrmTouchpointEntity entity = findTouchpointOrThrow(id);
        Object[] stats = trackRepository.aggregateSingleTouchpoint(
                 entity.getTouchpointCode());
        if (stats != null && stats.length == 4) {
            entity.setTotalEvents(stats[0] == null ? 0 : ((Number) stats[0]).intValue());
            entity.setUniqueVisitors(stats[1] == null ? 0 : ((Number) stats[1]).intValue());
            entity.setConversionCount(stats[2] == null ? 0 : ((Number) stats[2]).intValue());
            entity.setLastEventAt((LocalDateTime) stats[3]);
        }
        entity = touchpointRepository.save(entity);
        log.info("重算触点统计: id={}, code={}, totalEvents={}, conversions={}",
                entity.getId(), entity.getTouchpointCode(), entity.getTotalEvents(), entity.getConversionCount());
        return entity;
    }

    // ============================================================
    // 行为路径
    // ============================================================

    /**
     * 构建客户行为路径: 从客户 + 会话内的行为事件聚合。
     * <p>
     * 流程: 加载会话行为事件 (behaviorTime ASC) → 生成行为序列 JSON
     * [{type, time, touchpoint, page}] → 聚合触点列表 / 入口出口 / 总行为数 /
     * 总停留时长 / 触点数 / 转化标记与转化点 → 已存在则更新, 否则新建。
     * </p>
     *
     * @param customerId 客户 ID
     * @param sessionId  会话 ID
     * @return 构建后的行为路径
     * @throws ScrmException 会话 ID 为空 / 无行为事件
     */
    @Transactional
    public ScrmBehaviorPathEntity buildPath(Long customerId, String sessionId) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw ScrmException.badRequest("会话 ID 不能为空");
        }
        List<ScrmBehaviorTrackEntity> events = trackRepository
                .findByCustomerIdAndSessionIdOrderByBehaviorTimeAsc(customerId, sessionId);
        if (events.isEmpty()) {
            throw ScrmException.badRequest("会话内无行为事件, 无法构建路径: customerId=" + customerId
                    + ", sessionId=" + sessionId);
        }
        // 构建行为序列 JSON
        List<Map<String, Object>> sequence = new ArrayList<>(events.size());
        Set<String> touchpointSet = new LinkedHashSet<>();
        int totalDuration = 0;
        boolean hasConversion = false;
        String conversionPoint = null;
        for (ScrmBehaviorTrackEntity event : events) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", event.getBehaviorType());
            node.put("time", event.getBehaviorTime());
            node.put("touchpoint", event.getTouchpoint());
            node.put("page", event.getPageUrl());
            sequence.add(node);
            if (event.getTouchpoint() != null) {
                touchpointSet.add(event.getTouchpoint());
            }
            if (event.getDurationSeconds() != null) {
                totalDuration += event.getDurationSeconds();
            }
            if (Boolean.TRUE.equals(event.getIsConversion()) && !hasConversion) {
                hasConversion = true;
                conversionPoint = event.getBehaviorType() + "@" + event.getTouchpoint();
            }
        }
        ScrmBehaviorTrackEntity first = events.get(0);
        ScrmBehaviorTrackEntity last = events.get(events.size() - 1);
        String touchpoints = String.join(",", touchpointSet);
        // 已存在则更新, 否则新建
        Optional<ScrmBehaviorPathEntity> existing = pathRepository
                .findByCustomerIdAndSessionId(customerId, sessionId);
        ScrmBehaviorPathEntity entity = existing.orElseGet(ScrmBehaviorPathEntity::new);
        entity.setCustomerId(customerId);
        entity.setSessionStartTime(first.getBehaviorTime());
        entity.setSessionEndTime(last.getBehaviorTime());
        entity.setTouchpoints(touchpoints);
        entity.setBehaviorSequence(toJson(sequence));
        entity.setTotalBehaviors(events.size());
        entity.setTotalDurationSeconds(totalDuration);
        entity.setTouchpointCount(touchpointSet.size());
        entity.setHasConversion(hasConversion);
        entity.setConversionPoint(conversionPoint);
        entity.setEntryTouchpoint(first.getTouchpoint());
        entity.setExitTouchpoint(last.getTouchpoint());
        entity.setDeviceType(first.getDeviceType());
        entity.setSessionId(sessionId);
        entity = pathRepository.save(entity);
        log.info("构建行为路径: id={}, customerId={}, sessionId={}, behaviors={}, touchpoints={}, conversion={}",
                entity.getId(), customerId, sessionId, events.size(), touchpointSet.size(), hasConversion);
        return entity;
    }

    /**
     * 查询行为路径详情。
     *
     * @param id 路径 ID
     * @return 路径实体
     * @throws ScrmException 路径不存在
     */
    @Transactional(readOnly = true)
    public ScrmBehaviorPathEntity getPath(Long id) throws ScrmException {
        return findPathOrThrow(id);
    }

    /**
     * 分页查询行为路径, 支持按客户 / 转化标记 / 会话时间范围过滤。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param hasConversion 转化标记过滤（可空）
     * @param startTime  会话开始时间起点 (含, 可空)
     * @param endTime    会话开始时间终点 (含, 可空)
     * @param pageable   分页参数
     * @return 路径分页结果 (按 sessionStartTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmBehaviorPathEntity> listPaths(Long customerId, Boolean hasConversion,
                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                   Pageable pageable) {
        Specification<ScrmBehaviorPathEntity> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (hasConversion != null) {
                predicates.add(cb.equal(root.get("hasConversion"), hasConversion));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sessionStartTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sessionStartTime"), endTime));
            }
            q.orderBy(cb.desc(root.get("sessionStartTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return pathRepository.findAll(spec, pageable);
    }

    /**
     * 常见路径分析: 按触点序列分组统计, 取出现次数最多的路径。
     *
     * @param limit 返回条数 (默认 10)
     * @return 常见路径列表 [{touchpoints, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCommonPaths(Integer limit) {
        int size = limit != null && limit > 0 ? limit : DEFAULT_COMMON_PATH_LIMIT;
        List<Object[]> rows = pathRepository.findCommonPaths(
                 PageRequest.of(0, size));
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("touchpoints", row[0]);
            entry.put("count", row[1] == null ? 0L : ((Number) row[1]).longValue());
            result.add(entry);
        }
        return result;
    }

    /**
     * 转化路径: 查询含转化的行为路径 (按会话开始时间倒序)。
     *
     * @param limit 返回条数 (默认 20)
     * @return 转化路径列表
     */
    @Transactional(readOnly = true)
    public List<ScrmBehaviorPathEntity> getConversionPaths(Integer limit) {
        int size = limit != null && limit > 0 ? limit : DEFAULT_CONVERSION_PATH_LIMIT;
        return pathRepository
                .findByHasConversionTrueOrderBySessionStartTimeDesc(
                         PageRequest.of(0, size))
                .getContent();
    }

    /**
     * 流失点分析: 按出口触点统计路径数与流失率。
     * <p>流失率 = 该触点作为出口的路径数 / 全部路径数。</p>
     *
     * @return 流失点列表 [{touchpoint, exitCount, dropoffRate}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDropoffPoints() {
        List<Object[]> rows = pathRepository.countByExitTouchpoint();
        long totalPaths = pathRepository.findAllByOrderBySessionStartTimeDesc(
                 PageRequest.of(0, 1)).getTotalElements();
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            long exitCount = row[1] == null ? 0L : ((Number) row[1]).longValue();
            entry.put("touchpoint", row[0]);
            entry.put("exitCount", exitCount);
            entry.put("dropoffRate", totalPaths == 0 ? 0.0 : (double) exitCount / totalPaths);
            result.add(entry);
        }
        return result;
    }

    // ============================================================
    // 行为分析
    // ============================================================

    /**
     * 行为统计概览: 总事件数 / 独立客户数 / 行为类型分布 / 触点分布。
     *
     * @param startTime 行为时间起点 (含, 可空)
     * @param endTime   行为时间终点 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBehaviorStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalEvents", trackRepository.countInRange(startTime, endTime));
        stats.put("uniqueCustomers", trackRepository.countDistinctCustomerInRange(startTime, endTime));
        // 行为类型分布
        List<Object[]> byType = trackRepository.countByBehaviorType(startTime, endTime);
        Map<String, Long> behaviorTypeDistribution = new LinkedHashMap<>();
        for (Object[] row : byType) {
            behaviorTypeDistribution.put((String) row[0],
                    row[1] == null ? 0L : ((Number) row[1]).longValue());
        }
        stats.put("behaviorTypeDistribution", behaviorTypeDistribution);
        // 触点分布
        List<Object[]> byTouchpoint = trackRepository.aggregateByTouchpoint(startTime, endTime);
        Map<String, Long> touchpointDistribution = new LinkedHashMap<>();
        for (Object[] row : byTouchpoint) {
            touchpointDistribution.put((String) row[0],
                    row[1] == null ? 0L : ((Number) row[1]).longValue());
        }
        stats.put("touchpointDistribution", touchpointDistribution);
        return stats;
    }

    /**
     * 漏斗统计: 各漏斗阶段独立客户数与阶段间转化率。
     * <p>转化率 = 当前阶段人数 / 上一阶段人数 (首阶段为 null)。</p>
     *
     * @param startTime 行为时间起点 (含, 可空)
     * @param endTime   行为时间终点 (含, 可空)
     * @return 漏斗统计列表 [{stage, customerCount, conversionRate}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFunnelStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = trackRepository.countDistinctCustomerByFunnelStage(startTime, endTime);
        Map<String, Long> stageCount = new LinkedHashMap<>();
        for (Object[] row : rows) {
            stageCount.put((String) row[0], row[1] == null ? 0L : ((Number) row[1]).longValue());
        }
        List<Map<String, Object>> result = new ArrayList<>(FUNNEL_STAGES.size());
        long previous = 0L;
        for (String stage : FUNNEL_STAGES) {
            long count = stageCount.getOrDefault(stage, 0L);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("stage", stage);
            entry.put("customerCount", count);
            entry.put("conversionRate", previous == 0 ? null : (double) count / previous);
            result.add(entry);
            previous = count;
        }
        return result;
    }

    /**
     * 触点效果对比: 每个触点的事件数 / 独立访客 / 转化数 / 转化率。
     *
     * @param startTime 行为时间起点 (含, 可空)
     * @param endTime   行为时间终点 (含, 可空)
     * @return 触点效果列表 [{touchpoint, totalEvents, uniqueVisitors, conversionCount, conversionRate}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTouchpointEffectiveness(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = trackRepository.aggregateByTouchpoint(startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            long totalEvents = row[1] == null ? 0L : ((Number) row[1]).longValue();
            long uniqueVisitors = row[2] == null ? 0L : ((Number) row[2]).longValue();
            long conversionCount = row[3] == null ? 0L : ((Number) row[3]).longValue();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("touchpoint", row[0]);
            entry.put("totalEvents", totalEvents);
            entry.put("uniqueVisitors", uniqueVisitors);
            entry.put("conversionCount", conversionCount);
            entry.put("conversionRate", totalEvents == 0 ? 0.0 : (double) conversionCount / totalEvents);
            result.add(entry);
        }
        return result;
    }

    /**
     * 行为趋势: 按日期统计事件数 (可按行为类型过滤)。
     *
     * @param days         回溯天数 (默认 7)
     * @param behaviorType 行为类型过滤（可空）
     * @return 趋势列表 [{date, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBehaviorTrend(Integer days, String behaviorType) {
        int dayCount = days != null && days > 0 ? days : DEFAULT_TREND_DAYS;
        LocalDateTime start = LocalDateTime.now().minusDays(dayCount);
        List<Object[]> rows = (behaviorType != null && !behaviorType.isBlank())
                ? trackRepository.countByDayAndType(behaviorType, start)
                : trackRepository.countByDay(start);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", row[0]);
            entry.put("count", row[1] == null ? 0L : ((Number) row[1]).longValue());
            result.add(entry);
        }
        return result;
    }

    /**
     * 行为热力图数据: 按页面 URL + 时段聚合事件数。
     *
     * @param touchpoint 触点过滤（可空）
     * @param startTime  行为时间起点 (含, 可空)
     * @param endTime    行为时间终点 (含, 可空)
     * @return 热力图数据列表 [{pageUrl, hour, count}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHeatmap(String touchpoint, LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = trackRepository.countByPageAndHour(touchpoint, startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("pageUrl", row[0]);
            entry.put("hour", row[1]);
            entry.put("count", row[2] == null ? 0L : ((Number) row[2]).longValue());
            result.add(entry);
        }
        return result;
    }

    /**
     * 转化归因分析: 各触点的转化贡献。
     * <p>贡献率 = 该触点转化事件数 / 全部转化事件数。</p>
     *
     * @param startTime 行为时间起点 (含, 可空)
     * @param endTime   行为时间终点 (含, 可空)
     * @return 归因分析列表 [{touchpoint, conversionCount, contribution}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConversionAttribution(LocalDateTime startTime, LocalDateTime endTime) {
        long totalConversions = trackRepository.countConversionsInRange(startTime, endTime);
        List<Object[]> rows = trackRepository.aggregateByTouchpoint(startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            long conversionCount = row[3] == null ? 0L : ((Number) row[3]).longValue();
            if (conversionCount <= 0) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("touchpoint", row[0]);
            entry.put("conversionCount", conversionCount);
            entry.put("contribution", totalConversions == 0 ? 0.0 : (double) conversionCount / totalConversions);
            result.add(entry);
        }
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验行为记录参数。
     *
     * @param dto 行为记录参数
     * @throws ScrmException 参数非法
     */
    private void validateRecordDto(ScrmBehaviorRecordDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("行为记录参数不能为空");
        }
        if (dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getBehaviorType() == null || dto.getBehaviorType().isBlank()) {
            throw ScrmException.badRequest("行为类型不能为空");
        }
        if (!VALID_BEHAVIOR_TYPES.contains(dto.getBehaviorType())) {
            throw ScrmException.badRequest(
                    "行为类型非法: " + dto.getBehaviorType() + ", 仅支持 " + VALID_BEHAVIOR_TYPES);
        }
        if (dto.getTouchpoint() == null || dto.getTouchpoint().isBlank()) {
            throw ScrmException.badRequest("触点不能为空");
        }
        if (!VALID_TOUCHPOINTS.contains(dto.getTouchpoint())) {
            throw ScrmException.badRequest(
                    "触点非法: " + dto.getTouchpoint() + ", 仅支持 " + VALID_TOUCHPOINTS);
        }
        if (dto.getDeviceType() != null && !dto.getDeviceType().isBlank() && !VALID_DEVICE_TYPES.contains(dto.getDeviceType())) {
            throw ScrmException.badRequest(
                    "设备类型非法: " + dto.getDeviceType() + ", 仅支持 " + VALID_DEVICE_TYPES);
        }
        if (dto.getFunnelStage() != null && !dto.getFunnelStage().isBlank() && !FUNNEL_STAGES.contains(dto.getFunnelStage())) {
            throw ScrmException.badRequest(
                    "漏斗阶段非法: " + dto.getFunnelStage() + ", 仅支持 " + FUNNEL_STAGES);
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
     * 校验触点参数。
     *
     * @param dto     触点参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTouchpointDto(ScrmTouchpointDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("触点参数不能为空");
        }
        if (dto.getTouchpointName() != null) {
            if (dto.getTouchpointName().isBlank()) {
                throw ScrmException.badRequest("触点名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("触点名称不能为空");
        }
        if (dto.getTouchpointCode() != null) {
            if (dto.getTouchpointCode().isBlank()) {
                throw ScrmException.badRequest("触点编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("触点编码不能为空");
        }
        if (dto.getTouchpointType() != null) {
            if (!VALID_TOUCHPOINTS.contains(dto.getTouchpointType())) {
                throw ScrmException.badRequest(
                        "触点类型非法: " + dto.getTouchpointType() + ", 仅支持 " + VALID_TOUCHPOINTS);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("触点类型不能为空");
        }
        if (dto.getConfig() != null && !dto.getConfig().isBlank()) {
            try {
                objectMapper.readTree(dto.getConfig());
            } catch (Exception e) {
                throw ScrmException.badRequest("触点配置 config JSON 解析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 记录行为事件时增量更新触点统计 (best-effort, 失败仅告警)。
     *
     * @param touchpoint 触点编码
     * @param eventTime  事件时间
     * @param conversion 是否转化行为
     */
    private void updateTouchpointStatsOnEvent(String touchpoint,
                                               LocalDateTime eventTime, boolean conversion) {
        try {
            touchpointRepository.incrementEvents(touchpoint, eventTime);
            if (conversion) {
                touchpointRepository.incrementConversions(touchpoint);
            }
        } catch (Exception e) {
            log.warn("增量更新触点统计失败, 忽略: touchpoint={}, err={}", touchpoint, e.getMessage());
        }
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
            return EMPTY_SEQUENCE;
        }
    }

    /**
     * 按主键查询行为事件, 不存在抛异常, 并校验账号归属。
     *
     * @param id 行为事件 ID
     * @return 行为事件实体
     * @throws ScrmException 行为事件不存在
     */
    private ScrmBehaviorTrackEntity findTrackOrThrow(Long id) throws ScrmException {
        ScrmBehaviorTrackEntity entity = trackRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "行为事件不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询触点, 不存在抛异常, 并校验账号归属。
     *
     * @param id 触点 ID
     * @return 触点实体
     * @throws ScrmException 触点不存在
     */
    private ScrmTouchpointEntity findTouchpointOrThrow(Long id) throws ScrmException {
        ScrmTouchpointEntity entity = touchpointRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "触点不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询行为路径, 不存在抛异常, 并校验账号归属。
     *
     * @param id 路径 ID
     * @return 路径实体
     * @throws ScrmException 路径不存在
     */
    private ScrmBehaviorPathEntity findPathOrThrow(Long id) throws ScrmException {
        ScrmBehaviorPathEntity entity = pathRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "行为路径不存在: id=" + id));
        return entity;
    }

}
