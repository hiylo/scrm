/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmCampaignLaunchDto;
import org.hiylo.scrm.dto.ScrmMarketingCampaignChannelDto;
import org.hiylo.scrm.dto.ScrmMarketingCampaignDto;
import org.hiylo.scrm.dto.ScrmMarketingCampaignParticipantDto;
import org.hiylo.scrm.entity.ScrmMarketingCampaignChannelEntity;
import org.hiylo.scrm.entity.ScrmMarketingCampaignEntity;
import org.hiylo.scrm.entity.ScrmMarketingCampaignParticipantEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmMarketingCampaignChannelRepository;
import org.hiylo.scrm.repository.ScrmMarketingCampaignParticipantRepository;
import org.hiylo.scrm.repository.ScrmMarketingCampaignRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SCRM 营销活动服务。
 * <p>
 * 承载多渠道营销活动的核心能力: 活动策划 (增删改查与生命周期管理), 渠道配置与发送
 * (启动渠道发送模拟实现), 参与者记录与转化追踪, 渠道指标更新, 活动指标汇总与 ROI 计算,
 * 实现数据隔离。
 * </p>
 * <p>
 * 活动状态流转: DRAFT → SCHEDULED → RUNNING → PAUSED / COMPLETED / CANCELLED。
 * 渠道状态流转: PENDING → SENDING → SENT / FAILED。ROI 计算公式: (转化金额 - 花费) / 花费。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMarketingCampaignService {

    /** 默认预算 */
    private static final double DEFAULT_BUDGET = 0.0;

    /** 默认实际花费 */
    private static final double DEFAULT_ACTUAL_COST = 0.0;

    /** 默认转化金额 */
    private static final double DEFAULT_CONVERSION_VALUE = 0.0;

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 默认渠道花费 */
    private static final double DEFAULT_CHANNEL_COST = 0.0;

    /** 活动状态: 草稿 */
    private static final String STATUS_DRAFT = "DRAFT";
    /** 活动状态: 已排期 */
    private static final String STATUS_SCHEDULED = "SCHEDULED";
    /** 活动状态: 进行中 */
    private static final String STATUS_RUNNING = "RUNNING";
    /** 活动状态: 已暂停 */
    private static final String STATUS_PAUSED = "PAUSED";
    /** 活动状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";
    /** 活动状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    /** 渠道状态: 待发送 */
    private static final String CHANNEL_STATUS_PENDING = "PENDING";
    /** 渠道状态: 发送中 */
    private static final String CHANNEL_STATUS_SENDING = "SENDING";
    /** 渠道状态: 已发送 */
    private static final String CHANNEL_STATUS_SENT = "SENT";
    /** 渠道状态: 发送失败 */
    private static final String CHANNEL_STATUS_FAILED = "FAILED";

    /** 合法的活动类型 */
    private static final List<String> VALID_CAMPAIGN_TYPES = List.of(
            "PROMOTION", "NEW_PRODUCT", "SEASONAL", "RETENTION",
            "ACQUISITION", "BRAND_AWARENESS", "FLASH_SALE");

    /** 合法的渠道 */
    private static final List<String> VALID_CHANNELS = List.of(
            "WECHAT", "WORK_WECHAT", "SMS", "EMAIL",
            "DOUYIN", "KUAISHOU", "XIAOHONGSHU", "BILIBILI");

    /** 合法的活动状态 */
    private static final List<String> VALID_CAMPAIGN_STATUSES = List.of(
            STATUS_DRAFT, STATUS_SCHEDULED, STATUS_RUNNING,
            STATUS_PAUSED, STATUS_COMPLETED, STATUS_CANCELLED);

    /** 默认操作人 (请求头未透传时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 复制活动名称后缀 */
    private static final String COPY_SUFFIX = "-副本";

    /** 活动数据访问层 */
    private final ScrmMarketingCampaignRepository campaignRepository;

    /** 活动渠道数据访问层 */
    private final ScrmMarketingCampaignChannelRepository channelRepository;

    /** 活动参与者数据访问层 */
    private final ScrmMarketingCampaignParticipantRepository participantRepository;

    /** JSON 解析器 (序列化 metricsJson) */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 活动管理
    // ============================================================

    /**
     * 创建营销活动。
     * <p>校验 campaignType / channels / 日期区间合法性后写入账号 ID 持久化,
     * budget / actualCost / status / priority 缺省时填默认值。</p>
     *
     * @param dto 活动参数
     * @return 创建后的活动
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmMarketingCampaignEntity createCampaign(ScrmMarketingCampaignDto dto) throws ScrmException {
        validateCampaignDto(dto, false);
        ScrmMarketingCampaignEntity entity = new ScrmMarketingCampaignEntity();
        entity.setCampaignName(dto.getCampaignName());
        entity.setCampaignType(dto.getCampaignType());
        entity.setDescription(dto.getDescription());
        entity.setObjective(dto.getObjective());
        entity.setTargetSegment(dto.getTargetSegment());
        entity.setChannels(normalizeChannels(dto.getChannels()));
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setBudget(dto.getBudget() != null ? dto.getBudget() : DEFAULT_BUDGET);
        entity.setActualCost(DEFAULT_ACTUAL_COST);
        entity.setStatus(STATUS_DRAFT);
        entity.setManagerId(dto.getManagerId());
        entity.setManagerName(dto.getManagerName());
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setTags(dto.getTags());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = campaignRepository.save(entity);
        log.info("创建营销活动: id={}, campaignName={}, campaignType={}",
                entity.getId(), entity.getCampaignName(), entity.getCampaignType());
        return entity;
    }

    /**
     * 更新营销活动（字段非空才覆盖）。
     * <p>状态为 RUNNING / COMPLETED / CANCELLED 时禁止更新核心字段 (channels / 日期区间)。</p>
     *
     * @param id  活动 ID
     * @param dto 活动参数
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 参数非法 / 状态非法
     */
    @Transactional
    public ScrmMarketingCampaignEntity updateCampaign(Long id, ScrmMarketingCampaignDto dto) throws ScrmException {
        ScrmMarketingCampaignEntity entity = findCampaignOrThrow(id);
        validateCampaignDto(dto, true);
        if (dto.getCampaignName() != null) entity.setCampaignName(dto.getCampaignName());
        if (dto.getCampaignType() != null) entity.setCampaignType(dto.getCampaignType());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getObjective() != null) entity.setObjective(dto.getObjective());
        if (dto.getTargetSegment() != null) entity.setTargetSegment(dto.getTargetSegment());
        if (dto.getChannels() != null) entity.setChannels(normalizeChannels(dto.getChannels()));
        if (dto.getStartDate() != null) entity.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) entity.setEndDate(dto.getEndDate());
        if (dto.getBudget() != null) entity.setBudget(dto.getBudget());
        if (dto.getManagerId() != null) entity.setManagerId(dto.getManagerId());
        if (dto.getManagerName() != null) entity.setManagerName(dto.getManagerName());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        // 更新后再次校验日期区间合法性
        validateDateRange(entity.getStartDate(), entity.getEndDate());
        entity = campaignRepository.save(entity);
        log.info("更新营销活动: id={}, campaignName={}", entity.getId(), entity.getCampaignName());
        return entity;
    }

    /**
     * 删除营销活动 (同时删除关联渠道与参与者记录)。
     *
     * @param id 活动 ID
     * @throws ScrmException 活动不存在
     */
    @Transactional
    public void deleteCampaign(Long id) throws ScrmException {
        ScrmMarketingCampaignEntity entity = findCampaignOrThrow(id);
        // 清理关联渠道
        List<ScrmMarketingCampaignChannelEntity> channels =
                channelRepository.findByCampaignIdOrderByChannelAsc(id);
        if (!channels.isEmpty()) {
            channelRepository.deleteAll(channels);
        }
        // 清理关联参与者
        Specification<ScrmMarketingCampaignParticipantEntity> participantSpec = (root, query, cb) ->
                cb.and( cb.equal(root.get("campaignId"), id));
        List<ScrmMarketingCampaignParticipantEntity> participants = participantRepository.findAll(participantSpec);
        if (!participants.isEmpty()) {
            participantRepository.deleteAll(participants);
        }
        campaignRepository.delete(entity);
        log.info("删除营销活动: id={}, campaignName={}, channels={}, participants={}",
                id, entity.getCampaignName(), channels.size(), participants.size());
    }

    /**
     * 查询活动详情。
     *
     * @param id 活动 ID
     * @return 活动实体
     * @throws ScrmException 活动不存在
     */
    @Transactional(readOnly = true)
    public ScrmMarketingCampaignEntity getCampaign(Long id) throws ScrmException {
        return findCampaignOrThrow(id);
    }

    /**
     * 分页查询活动, 支持按类型 / 状态 / 负责人 / 时间范围 / 关键字过滤。
     *
     * @param campaignType 活动类型过滤（可空）
     * @param status       状态过滤（可空）
     * @param managerId    负责人 ID 过滤（可空）
     * @param startDate    开始日期过滤 (查询活动开始日期 ≥ 此值, 可空)
     * @param endDate      结束日期过滤 (查询活动结束日期 ≤ 此值, 可空)
     * @param keyword      活动名称关键字模糊匹配（可空）
     * @param pageable     分页参数
     * @return 活动分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmMarketingCampaignEntity> listCampaigns(String campaignType, String status, String managerId,
                                                            LocalDate startDate, LocalDate endDate, String keyword,
                                                            Pageable pageable) {
        Specification<ScrmMarketingCampaignEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (campaignType != null && !campaignType.isBlank()) {
                predicates.add(cb.equal(root.get("campaignType"), campaignType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (managerId != null && !managerId.isBlank()) {
                predicates.add(cb.equal(root.get("managerId"), managerId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), endDate));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("campaignName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return campaignRepository.findAll(spec, pageable);
    }

    /**
     * 排期活动 (DRAFT → SCHEDULED)。
     *
     * @param id          活动 ID
     * @param scheduledAt 计划开始时间 (仅记录日志, 不持久化)
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 状态非法
     */
    @Transactional
    public ScrmMarketingCampaignEntity scheduleCampaign(Long id, LocalDateTime scheduledAt) throws ScrmException {
        ScrmMarketingCampaignEntity entity = findCampaignOrThrow(id);
        if (!STATUS_DRAFT.equals(entity.getStatus()) && !STATUS_SCHEDULED.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 DRAFT / SCHEDULED 状态可排期: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_SCHEDULED);
        entity = campaignRepository.save(entity);
        log.info("营销活动已排期: id={}, scheduledAt={}", id, scheduledAt);
        return entity;
    }

    /**
     * 启动活动 (SCHEDULED / PAUSED → RUNNING)。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 状态非法
     */
    @Transactional
    public ScrmMarketingCampaignEntity startCampaign(Long id) throws ScrmException {
        ScrmMarketingCampaignEntity entity = findCampaignOrThrow(id);
        if (!STATUS_SCHEDULED.equals(entity.getStatus()) && !STATUS_PAUSED.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "仅 SCHEDULED / PAUSED 状态可启动: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_RUNNING);
        entity = campaignRepository.save(entity);
        log.info("启动营销活动: id={}, campaignName={}", id, entity.getCampaignName());
        return entity;
    }

    /**
     * 暂停活动 (RUNNING → PAUSED)。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 状态非法
     */
    @Transactional
    public ScrmMarketingCampaignEntity pauseCampaign(Long id) throws ScrmException {
        ScrmMarketingCampaignEntity entity = findCampaignOrThrow(id);
        if (!STATUS_RUNNING.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 RUNNING 状态可暂停: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_PAUSED);
        entity = campaignRepository.save(entity);
        log.info("暂停营销活动: id={}, campaignName={}", id, entity.getCampaignName());
        return entity;
    }

    /**
     * 完成活动 (RUNNING / PAUSED → COMPLETED)。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 状态非法
     */
    @Transactional
    public ScrmMarketingCampaignEntity completeCampaign(Long id) throws ScrmException {
        ScrmMarketingCampaignEntity entity = findCampaignOrThrow(id);
        if (!STATUS_RUNNING.equals(entity.getStatus()) && !STATUS_PAUSED.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 RUNNING / PAUSED 状态可完成: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_COMPLETED);
        // 完成时刷新汇总指标
        refreshMetricsJson(entity);
        entity = campaignRepository.save(entity);
        log.info("完成营销活动: id={}, campaignName={}", id, entity.getCampaignName());
        return entity;
    }

    /**
     * 取消活动 (DRAFT / SCHEDULED / RUNNING / PAUSED → CANCELLED)。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 状态非法
     */
    @Transactional
    public ScrmMarketingCampaignEntity cancelCampaign(Long id) throws ScrmException {
        ScrmMarketingCampaignEntity entity = findCampaignOrThrow(id);
        if (STATUS_COMPLETED.equals(entity.getStatus()) || STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("已完成 / 已取消的活动不可取消: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_CANCELLED);
        entity = campaignRepository.save(entity);
        log.info("取消营销活动: id={}, campaignName={}", id, entity.getCampaignName());
        return entity;
    }

    /**
     * 复制活动 (创建副本, 状态置为 DRAFT, 不复制渠道与参与者)。
     *
     * @param id 源活动 ID
     * @return 复制后的活动
     * @throws ScrmException 源活动不存在
     */
    @Transactional
    public ScrmMarketingCampaignEntity copyCampaign(Long id) throws ScrmException {
        ScrmMarketingCampaignEntity source = findCampaignOrThrow(id);
        ScrmMarketingCampaignEntity copy = new ScrmMarketingCampaignEntity();
        copy.setCampaignName(source.getCampaignName() + COPY_SUFFIX);
        copy.setCampaignType(source.getCampaignType());
        copy.setDescription(source.getDescription());
        copy.setObjective(source.getObjective());
        copy.setTargetSegment(source.getTargetSegment());
        copy.setChannels(source.getChannels());
        copy.setStartDate(source.getStartDate());
        copy.setEndDate(source.getEndDate());
        copy.setBudget(source.getBudget());
        copy.setActualCost(DEFAULT_ACTUAL_COST);
        copy.setStatus(STATUS_DRAFT);
        copy.setManagerId(source.getManagerId());
        copy.setManagerName(source.getManagerName());
        copy.setPriority(source.getPriority());
        copy.setTags(source.getTags());
        copy.setCreatedBy(currentOperator());
        copy = campaignRepository.save(copy);
        // 复制渠道配置 (状态重置为 PENDING, 计数清零)
        List<ScrmMarketingCampaignChannelEntity> sourceChannels =
                channelRepository.findByCampaignIdOrderByChannelAsc(id);
        for (ScrmMarketingCampaignChannelEntity src : sourceChannels) {
            ScrmMarketingCampaignChannelEntity ch = new ScrmMarketingCampaignChannelEntity();
            ch.setCampaignId(copy.getId());
            ch.setChannel(src.getChannel());
            ch.setChannelConfig(src.getChannelConfig());
            ch.setContentTitle(src.getContentTitle());
            ch.setContentBody(src.getContentBody());
            ch.setContentImage(src.getContentImage());
            ch.setLinkUrl(src.getLinkUrl());
            ch.setScheduledAt(src.getScheduledAt());
            ch.setTargetCount(src.getTargetCount());
            ch.setSentCount(0);
            ch.setDeliveredCount(0);
            ch.setReadCount(0);
            ch.setClickCount(0);
            ch.setConvertCount(0);
            ch.setCost(src.getCost());
            ch.setStatus(CHANNEL_STATUS_PENDING);
            channelRepository.save(ch);
        }
        log.info("复制营销活动: sourceId={}, copyId={}, channels={}", id, copy.getId(), sourceChannels.size());
        return copy;
    }

    // ============================================================
    // 渠道管理
    // ============================================================

    /**
     * 新增活动渠道 (同一活动下渠道不可重复)。
     *
     * @param campaignId 活动 ID
     * @param dto        渠道参数
     * @return 创建后的渠道
     * @throws ScrmException 活动不存在 / 渠道非法 / 渠道已存在
     */
    @Transactional
    public ScrmMarketingCampaignChannelEntity addChannel(Long campaignId, ScrmMarketingCampaignChannelDto dto)
            throws ScrmException {
        ScrmMarketingCampaignEntity campaign = findCampaignOrThrow(campaignId);
        if (dto.getChannel() == null || !VALID_CHANNELS.contains(dto.getChannel())) {
            throw ScrmException.badRequest(
                    "渠道非法: " + dto.getChannel() + ", 仅支持 " + VALID_CHANNELS);
        }
        if (channelRepository.findByCampaignIdAndChannel(
                 campaignId, dto.getChannel()).isPresent()) {
            throw ScrmException.conflict("活动下渠道已存在: campaignId=" + campaignId + ", channel=" + dto.getChannel());
        }
        ScrmMarketingCampaignChannelEntity entity = new ScrmMarketingCampaignChannelEntity();
        entity.setCampaignId(campaignId);
        entity.setChannel(dto.getChannel());
        entity.setChannelConfig(dto.getChannelConfig());
        entity.setContentTitle(dto.getContentTitle());
        entity.setContentBody(dto.getContentBody());
        entity.setContentImage(dto.getContentImage());
        entity.setLinkUrl(dto.getLinkUrl());
        entity.setScheduledAt(dto.getScheduledAt());
        entity.setTargetCount(dto.getTargetCount() != null ? dto.getTargetCount() : 0);
        entity.setSentCount(0);
        entity.setDeliveredCount(0);
        entity.setReadCount(0);
        entity.setClickCount(0);
        entity.setConvertCount(0);
        entity.setCost(dto.getCost() != null ? dto.getCost() : DEFAULT_CHANNEL_COST);
        entity.setStatus(CHANNEL_STATUS_PENDING);
        entity = channelRepository.save(entity);
        // 同步活动 channels 字段
        appendChannelIfAbsent(campaign, dto.getChannel());
        campaignRepository.save(campaign);
        log.info("新增活动渠道: campaignId={}, channelId={}, channel={}",
                campaignId, entity.getId(), entity.getChannel());
        return entity;
    }

    /**
     * 更新渠道配置（字段非空才覆盖）。
     *
     * @param id  渠道 ID
     * @param dto 渠道参数
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在 / 渠道非法
     */
    @Transactional
    public ScrmMarketingCampaignChannelEntity updateChannel(Long id, ScrmMarketingCampaignChannelDto dto)
            throws ScrmException {
        ScrmMarketingCampaignChannelEntity entity = findChannelOrThrow(id);
        if (dto.getChannel() != null && !VALID_CHANNELS.contains(dto.getChannel())) {
            throw ScrmException.badRequest(
                    "渠道非法: " + dto.getChannel() + ", 仅支持 " + VALID_CHANNELS);
        }
        if (dto.getChannelConfig() != null) entity.setChannelConfig(dto.getChannelConfig());
        if (dto.getContentTitle() != null) entity.setContentTitle(dto.getContentTitle());
        if (dto.getContentBody() != null) entity.setContentBody(dto.getContentBody());
        if (dto.getContentImage() != null) entity.setContentImage(dto.getContentImage());
        if (dto.getLinkUrl() != null) entity.setLinkUrl(dto.getLinkUrl());
        if (dto.getScheduledAt() != null) entity.setScheduledAt(dto.getScheduledAt());
        if (dto.getTargetCount() != null) entity.setTargetCount(dto.getTargetCount());
        if (dto.getCost() != null) entity.setCost(dto.getCost());
        entity = channelRepository.save(entity);
        log.info("更新活动渠道: channelId={}, channel={}", id, entity.getChannel());
        return entity;
    }

    /**
     * 删除渠道。
     *
     * @param id 渠道 ID
     * @throws ScrmException 渠道不存在
     */
    @Transactional
    public void removeChannel(Long id) throws ScrmException {
        ScrmMarketingCampaignChannelEntity entity = findChannelOrThrow(id);
        channelRepository.delete(entity);
        log.info("删除活动渠道: channelId={}, channel={}", id, entity.getChannel());
    }

    /**
     * 查询活动下全部渠道。
     *
     * @param campaignId 活动 ID
     * @return 渠道列表 (按渠道名升序)
     * @throws ScrmException 活动不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmMarketingCampaignChannelEntity> listChannels(Long campaignId) throws ScrmException {
        findCampaignOrThrow(campaignId);
        return channelRepository.findByCampaignIdOrderByChannelAsc(
                 campaignId);
    }

    /**
     * 启动渠道发送 (遍历渠道 → 记录发送 → 更新计数)。
     * <p>
     * launchDto.channelConfigs 为空时启动活动下全部 PENDING 渠道; 非空时按配置覆盖渠道内容并启动。
     * 渠道发送为模拟实现: 状态置 SENDING → 模拟送达/已读/点击 → 状态置 SENT, 同步更新活动实际花费。
     * </p>
     *
     * @param launchDto 启动配置
     * @return 各渠道发送结果 [{channelId, channel, status, sentCount}]
     * @throws ScrmException 活动不存在 / 渠道不存在
     */
    @Transactional
    public List<Map<String, Object>> launchChannels(ScrmCampaignLaunchDto launchDto) throws ScrmException {
        if (launchDto == null || launchDto.getCampaignId() == null) {
            throw ScrmException.badRequest("启动配置不能为空且需指定 campaignId");
        }
        ScrmMarketingCampaignEntity campaign = findCampaignOrThrow(launchDto.getCampaignId());
        List<ScrmMarketingCampaignChannelEntity> targets = new ArrayList<>();
        if (launchDto.getChannelConfigs() == null || launchDto.getChannelConfigs().isEmpty()) {
            // 启动全部 PENDING 渠道
            targets = channelRepository.findByCampaignIdAndStatus(
                     launchDto.getCampaignId(), CHANNEL_STATUS_PENDING);
        } else {
            for (ScrmCampaignLaunchDto.ChannelLaunchConfig config : launchDto.getChannelConfigs()) {
                ScrmMarketingCampaignChannelEntity channel = channelRepository
                        .findByCampaignIdAndChannel(launchDto.getCampaignId(), config.getChannel())
                        .orElseGet(() -> {
                            ScrmMarketingCampaignChannelEntity newChannel = new ScrmMarketingCampaignChannelEntity();
                            newChannel.setCampaignId(launchDto.getCampaignId());
                            newChannel.setChannel(config.getChannel());
                            newChannel.setTargetCount(0);
                            newChannel.setSentCount(0);
                            newChannel.setDeliveredCount(0);
                            newChannel.setReadCount(0);
                            newChannel.setClickCount(0);
                            newChannel.setConvertCount(0);
                            newChannel.setCost(DEFAULT_CHANNEL_COST);
                            newChannel.setStatus(CHANNEL_STATUS_PENDING);
                            return channelRepository.save(newChannel);
                        });
                // 覆盖渠道配置
                if (config.getContentTitle() != null) channel.setContentTitle(config.getContentTitle());
                if (config.getContentBody() != null) channel.setContentBody(config.getContentBody());
                if (config.getContentImage() != null) channel.setContentImage(config.getContentImage());
                if (config.getLinkUrl() != null) channel.setLinkUrl(config.getLinkUrl());
                if (config.getScheduledAt() != null) channel.setScheduledAt(config.getScheduledAt());
                if (config.getTargetCount() != null) channel.setTargetCount(config.getTargetCount());
                if (config.getCost() != null) channel.setCost(config.getCost());
                targets.add(channel);
            }
        }
        List<Map<String, Object>> results = new ArrayList<>();
        double totalCost = 0.0;
        for (ScrmMarketingCampaignChannelEntity channel : targets) {
            // 仅 PENDING / FAILED 渠道可启动
            if (!CHANNEL_STATUS_PENDING.equals(channel.getStatus()) && !CHANNEL_STATUS_FAILED.equals(channel.getStatus())) {
                continue;
            }
            // 模拟发送: 标记发送中
            channel.setStatus(CHANNEL_STATUS_SENDING);
            channel.setSentAt(LocalDateTime.now());
            channel.setSentCount(channel.getTargetCount() != null ? channel.getTargetCount() : 0);
            // 模拟送达漏斗: 送达 95% / 已读 60% / 点击 20% / 转化 5%
            int target = channel.getTargetCount() != null ? channel.getTargetCount() : 0;
            channel.setDeliveredCount((int) (target * 0.95));
            channel.setReadCount((int) (target * 0.60));
            channel.setClickCount((int) (target * 0.20));
            channel.setConvertCount((int) (target * 0.05));
            channel.setStatus(CHANNEL_STATUS_SENT);
            channel = channelRepository.save(channel);
            double cost = channel.getCost() != null ? channel.getCost() : 0.0;
            totalCost += cost;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("channelId", channel.getId());
            r.put("channel", channel.getChannel());
            r.put("status", channel.getStatus());
            r.put("sentCount", channel.getSentCount());
            r.put("deliveredCount", channel.getDeliveredCount());
            r.put("cost", cost);
            results.add(r);
        }
        // 累加活动实际花费
        double currentActual = campaign.getActualCost() != null ? campaign.getActualCost() : 0.0;
        campaign.setActualCost(currentActual + totalCost);
        campaignRepository.save(campaign);
        log.info("启动营销活动渠道发送: campaignId={}, launched={}, totalCost={}",
                launchDto.getCampaignId(), results.size(), totalCost);
        return results;
    }

    // ============================================================
    // 参与者管理
    // ============================================================

    /**
     * 记录参与者 (同一活动 + 客户 + 渠道允许多条, 保留完整参与轨迹)。
     *
     * @param dto 参与者参数
     * @return 创建后的参与者记录
     * @throws ScrmException 活动不存在
     */
    @Transactional
    public ScrmMarketingCampaignParticipantEntity recordParticipant(ScrmMarketingCampaignParticipantDto dto)
            throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("参与者参数不能为空");
        }
        if (dto.getCampaignId() == null || dto.getCustomerId() == null || dto.getChannel() == null) {
            throw ScrmException.badRequest("活动 ID / 客户 ID / 渠道不能为空");
        }
        findCampaignOrThrow(dto.getCampaignId());
        if (!VALID_CHANNELS.contains(dto.getChannel())) {
            throw ScrmException.badRequest(
                    "渠道非法: " + dto.getChannel() + ", 仅支持 " + VALID_CHANNELS);
        }
        ScrmMarketingCampaignParticipantEntity entity = new ScrmMarketingCampaignParticipantEntity();
        entity.setCampaignId(dto.getCampaignId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setChannel(dto.getChannel());
        entity.setParticipatedAt(dto.getParticipatedAt() != null ? dto.getParticipatedAt() : LocalDateTime.now());
        entity.setActions(dto.getActions());
        entity.setConverted(dto.getConverted() != null ? dto.getConverted() : false);
        entity.setConversionValue(
                dto.getConversionValue() != null ? dto.getConversionValue() : DEFAULT_CONVERSION_VALUE);
        if (Boolean.TRUE.equals(entity.getConverted()) && entity.getConvertedAt() == null) {
            entity.setConvertedAt(LocalDateTime.now());
        }
        entity.setResponseContent(dto.getResponseContent());
        entity = participantRepository.save(entity);
        log.info("记录营销活动参与者: id={}, campaignId={}, customerId={}, channel={}",
                entity.getId(), entity.getCampaignId(), entity.getCustomerId(), entity.getChannel());
        return entity;
    }

    /**
     * 更新参与者转化状态。
     *
     * @param id              参与者 ID
     * @param converted       是否转化
     * @param conversionValue 转化金额
     * @return 更新后的参与者记录
     * @throws ScrmException 参与者不存在
     */
    @Transactional
    public ScrmMarketingCampaignParticipantEntity updateParticipantConversion(Long id, Boolean converted,
                                                                              Double conversionValue)
            throws ScrmException {
        ScrmMarketingCampaignParticipantEntity entity = findParticipantOrThrow(id);
        if (converted == null) {
            converted = false;
        }
        entity.setConverted(converted);
        entity.setConversionValue(conversionValue != null ? conversionValue : DEFAULT_CONVERSION_VALUE);
        entity.setConvertedAt(Boolean.TRUE.equals(converted) ? LocalDateTime.now() : null);
        entity = participantRepository.save(entity);
        log.info("更新参与者转化: id={}, converted={}, conversionValue={}",
                id, converted, entity.getConversionValue());
        return entity;
    }

    /**
     * 分页查询参与者, 支持按活动 / 渠道 / 转化状态过滤。
     *
     * @param campaignId 活动 ID 过滤（可空）
     * @param channel    渠道过滤（可空）
     * @param converted  转化状态过滤（可空）
     * @param pageable   分页参数
     * @return 参与者分页结果 (按 participatedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmMarketingCampaignParticipantEntity> listParticipants(Long campaignId, String channel,
                                                                           Boolean converted, Pageable pageable) {
        Specification<ScrmMarketingCampaignParticipantEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (campaignId != null) {
                predicates.add(cb.equal(root.get("campaignId"), campaignId));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (converted != null) {
                predicates.add(cb.equal(root.get("converted"), converted));
            }
            query.orderBy(cb.desc(root.get("participatedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return participantRepository.findAll(spec, pageable);
    }

    // ============================================================
    // 指标与 ROI
    // ============================================================

    /**
     * 更新渠道指标 (送达 / 已读 / 点击 / 转化)。
     *
     * @param channelId  渠道 ID
     * @param delivered  送达数增量
     * @param read       已读数增量
     * @param click      点击数增量
     * @param convert    转化数增量
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在
     */
    @Transactional
    public ScrmMarketingCampaignChannelEntity updateChannelMetrics(Long channelId, Integer delivered, Integer read,
                                                                    Integer click, Integer convert)
            throws ScrmException {
        ScrmMarketingCampaignChannelEntity entity = findChannelOrThrow(channelId);
        if (delivered != null) {
            entity.setDeliveredCount((entity.getDeliveredCount() != null ? entity.getDeliveredCount() : 0) + delivered);
        }
        if (read != null) {
            entity.setReadCount((entity.getReadCount() != null ? entity.getReadCount() : 0) + read);
        }
        if (click != null) {
            entity.setClickCount((entity.getClickCount() != null ? entity.getClickCount() : 0) + click);
        }
        if (convert != null) {
            entity.setConvertCount((entity.getConvertCount() != null ? entity.getConvertCount() : 0) + convert);
        }
        entity = channelRepository.save(entity);
        log.info("更新渠道指标: channelId={}, delivered={}, read={}, click={}, convert={}",
                channelId, delivered, read, click, convert);
        return entity;
    }

    /**
     * 活动指标汇总: 各渠道指标 + 总花费 + 总转化金额 + ROI。
     *
     * @param campaignId 活动 ID
     * @return 指标汇总 Map
     * @throws ScrmException 活动不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignMetrics(Long campaignId) throws ScrmException {
        ScrmMarketingCampaignEntity campaign = findCampaignOrThrow(campaignId);
        List<ScrmMarketingCampaignChannelEntity> channels =
                channelRepository.findByCampaignIdOrderByChannelAsc(campaignId);
        List<Map<String, Object>> channelMetrics = new ArrayList<>();
        double totalCost = 0.0;
        int totalSent = 0;
        int totalDelivered = 0;
        int totalRead = 0;
        int totalClick = 0;
        int totalConvert = 0;
        for (ScrmMarketingCampaignChannelEntity ch : channels) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channelId", ch.getId());
            m.put("channel", ch.getChannel());
            m.put("status", ch.getStatus());
            m.put("targetCount", ch.getTargetCount() != null ? ch.getTargetCount() : 0);
            m.put("sentCount", ch.getSentCount() != null ? ch.getSentCount() : 0);
            m.put("deliveredCount", ch.getDeliveredCount() != null ? ch.getDeliveredCount() : 0);
            m.put("readCount", ch.getReadCount() != null ? ch.getReadCount() : 0);
            m.put("clickCount", ch.getClickCount() != null ? ch.getClickCount() : 0);
            m.put("convertCount", ch.getConvertCount() != null ? ch.getConvertCount() : 0);
            double cost = ch.getCost() != null ? ch.getCost() : 0.0;
            m.put("cost", cost);
            channelMetrics.add(m);
            totalCost += cost;
            totalSent += ch.getSentCount() != null ? ch.getSentCount() : 0;
            totalDelivered += ch.getDeliveredCount() != null ? ch.getDeliveredCount() : 0;
            totalRead += ch.getReadCount() != null ? ch.getReadCount() : 0;
            totalClick += ch.getClickCount() != null ? ch.getClickCount() : 0;
            totalConvert += ch.getConvertCount() != null ? ch.getConvertCount() : 0;
        }
        // 转化金额汇总 (来自参与者表)
        Object[] conversionStats = participantRepository.sumConversionByCampaign(campaignId);
        double totalConversionValue = 0.0;
        int conversionCount = 0;
        if (conversionStats != null && conversionStats.length == 2) {
            totalConversionValue = conversionStats[0] == null ? 0.0 : ((Number) conversionStats[0]).doubleValue();
            conversionCount = conversionStats[1] == null ? 0 : ((Number) conversionStats[1]).intValue();
        }
        double roi = calculateROIInternal(totalConversionValue, totalCost);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("campaignId", campaignId);
        metrics.put("campaignName", campaign.getCampaignName());
        metrics.put("status", campaign.getStatus());
        metrics.put("budget", campaign.getBudget() != null ? campaign.getBudget() : 0.0);
        metrics.put("actualCost", totalCost);
        metrics.put("totalConversionValue", totalConversionValue);
        metrics.put("conversionCount", conversionCount);
        metrics.put("roi", roi);
        metrics.put("totalSent", totalSent);
        metrics.put("totalDelivered", totalDelivered);
        metrics.put("totalRead", totalRead);
        metrics.put("totalClick", totalClick);
        metrics.put("totalConvert", totalConvert);
        metrics.put("channelMetrics", channelMetrics);
        return metrics;
    }

    /**
     * 计算 ROI: (转化金额 - 花费) / 花费。
     * <p>花费为 0 时返回 0 (避免除零)。</p>
     *
     * @param campaignId 活动 ID
     * @return ROI (小数, 如 1.5 表示 150% 收益率)
     * @throws ScrmException 活动不存在
     */
    @Transactional(readOnly = true)
    public double calculateROI(Long campaignId) throws ScrmException {
        findCampaignOrThrow(campaignId);
        double totalCost = 0.0;
        List<ScrmMarketingCampaignChannelEntity> channels =
                channelRepository.findByCampaignIdOrderByChannelAsc(campaignId);
        for (ScrmMarketingCampaignChannelEntity ch : channels) {
            totalCost += ch.getCost() != null ? ch.getCost() : 0.0;
        }
        Object[] conversionStats = participantRepository.sumConversionByCampaign(campaignId);
        double totalConversionValue = 0.0;
        if (conversionStats != null && conversionStats.length == 2) {
            totalConversionValue = conversionStats[0] == null ? 0.0 : ((Number) conversionStats[0]).doubleValue();
        }
        double roi = calculateROIInternal(totalConversionValue, totalCost);
        log.info("计算活动 ROI: campaignId={}, cost={}, conversionValue={}, roi={}",
                campaignId, totalCost, totalConversionValue, roi);
        return roi;
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 活动统计: 活动数 / 进行中 / 已完成 / 平均 ROI / 总转化。
     *
     * @param startTime 创建时间起始 (含, 可空)
     * @param endTime   创建时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 各状态活动数
        List<Object[]> byStatus = campaignRepository.countByStatus(startTime, endTime);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : VALID_CAMPAIGN_STATUSES) {
            statusCount.put(s, 0L);
        }
        long total = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
        }
        stats.put("statusCount", statusCount);
        stats.put("total", total);
        stats.put("running", statusCount.getOrDefault(STATUS_RUNNING, 0L));
        stats.put("completed", statusCount.getOrDefault(STATUS_COMPLETED, 0L));
        // 总实际花费
        Double sumCost = campaignRepository.sumActualCost(startTime, endTime);
        double totalCost = sumCost == null ? 0.0 : sumCost;
        stats.put("totalActualCost", totalCost);
        // 平均 ROI: 需遍历活动汇总转化金额
        List<ScrmMarketingCampaignEntity> campaigns = campaignRepository.findAll(
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    if (startTime != null) {
                        predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
                    }
                    if (endTime != null) {
                        predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                });
        double totalConversionValue = 0.0;
        for (ScrmMarketingCampaignEntity campaign : campaigns) {
            Object[] conv = participantRepository.sumConversionByCampaign(campaign.getId());
            if (conv != null && conv.length == 2) {
                totalConversionValue += conv[0] == null ? 0.0 : ((Number) conv[0]).doubleValue();
            }
        }
        double avgRoi = campaigns.isEmpty() ? 0.0 : calculateROIInternal(totalConversionValue, totalCost);
        stats.put("averageROI", avgRoi);
        stats.put("totalConversionValue", totalConversionValue);
        // 总转化数
        long totalConvert = 0L;
        for (ScrmMarketingCampaignEntity campaign : campaigns) {
            Object[] conv = participantRepository.sumConversionByCampaign(campaign.getId());
            if (conv != null && conv.length == 2) {
                totalConvert += conv[1] == null ? 0L : ((Number) conv[1]).longValue();
            }
        }
        stats.put("totalConvert", totalConvert);
        return stats;
    }

    /**
     * 渠道效果对比: 按渠道汇总花费 / 送达 / 点击 / 转化 / 转化金额。
     *
     * @param startTime 发送时间起始 (含, 可空)
     * @param endTime   发送时间截止 (含, 可空)
     * @return 渠道效果列表 [{channel, cost, sent, delivered, read, click, convert, conversionValue}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChannelEffectiveness(LocalDateTime startTime, LocalDateTime endTime) {
        // 渠道发送指标
        List<Object[]> channelAgg = channelRepository.aggregateByChannel(startTime, endTime);
        // 渠道转化指标 (参与者表)
        List<Object[]> participantAgg = participantRepository.aggregateByChannel(startTime, endTime);
        Map<String, Object[]> conversionByChannel = new LinkedHashMap<>();
        for (Object[] row : participantAgg) {
            conversionByChannel.put((String) row[0], row);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : channelAgg) {
            String channel = (String) row[0];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channel", channel);
            m.put("cost", toDouble(row[1]));
            m.put("sentCount", toLong(row[2]));
            m.put("deliveredCount", toLong(row[3]));
            m.put("readCount", toLong(row[4]));
            m.put("clickCount", toLong(row[5]));
            m.put("convertCount", toLong(row[6]));
            Object[] conv = conversionByChannel.get(channel);
            double conversionValue = 0.0;
            long conversionCnt = 0L;
            long participantCnt = 0L;
            if (conv != null && conv.length == 4) {
                conversionValue = toDouble(conv[1]);
                conversionCnt = toLong(conv[2]);
                participantCnt = toLong(conv[3]);
            }
            m.put("conversionValue", conversionValue);
            m.put("conversionCount", conversionCnt);
            m.put("participantCount", participantCnt);
            double cost = toDouble(row[1]);
            m.put("roi", calculateROIInternal(conversionValue, cost));
            result.add(m);
        }
        return result;
    }

    /**
     * 活动时间线: 活动基本信息 + 各渠道发送节点 + 参与者转化节点。
     *
     * @param campaignId 活动 ID
     * @return 时间线 Map {campaign, channels, participants, timeline}
     * @throws ScrmException 活动不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignTimeline(Long campaignId) throws ScrmException {
        ScrmMarketingCampaignEntity campaign = findCampaignOrThrow(campaignId);
        List<ScrmMarketingCampaignChannelEntity> channels =
                channelRepository.findByCampaignIdOrderByChannelAsc(campaignId);
        Page<ScrmMarketingCampaignParticipantEntity> participantsPage = participantRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("campaignId"), campaignId)),
                org.springframework.data.domain.PageRequest.of(0, 100,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "participatedAt")));
        List<Map<String, Object>> timeline = new ArrayList<>();
        // 活动创建节点
        Map<String, Object> createdNode = new LinkedHashMap<>();
        createdNode.put("type", "CAMPAIGN_CREATED");
        createdNode.put("time", campaign.getCreateTime());
        createdNode.put("detail", "活动创建: " + campaign.getCampaignName());
        timeline.add(createdNode);
        // 渠道发送节点
        for (ScrmMarketingCampaignChannelEntity ch : channels) {
            if (ch.getSentAt() != null) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("type", "CHANNEL_SENT");
                node.put("time", ch.getSentAt());
                node.put("channel", ch.getChannel());
                node.put("detail", "渠道发送: sent=" + ch.getSentCount());
                timeline.add(node);
            }
        }
        // 参与者转化节点
        for (ScrmMarketingCampaignParticipantEntity p : participantsPage.getContent()) {
            if (Boolean.TRUE.equals(p.getConverted()) && p.getConvertedAt() != null) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("type", "PARTICIPANT_CONVERTED");
                node.put("time", p.getConvertedAt());
                node.put("channel", p.getChannel());
                node.put("customerId", p.getCustomerId());
                node.put("detail", "客户转化: value=" + p.getConversionValue());
                timeline.add(node);
            }
        }
        // 按时间升序排列
        timeline.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("time");
            LocalDateTime tb = (LocalDateTime) b.get("time");
            if (ta == null || tb == null) {
                return 0;
            }
            return ta.compareTo(tb);
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("campaign", campaign);
        result.put("channels", channels);
        result.put("participants", participantsPage.getContent());
        result.put("timeline", timeline);
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验营销活动参数。
     * <p>
     * 创建场景 (partial=false): campaignName / campaignType / channels / startDate / endDate 必填。
     * 更新场景 (partial=true): 允许字段为空, 仅校验非空字段合法性。
     * </p>
     *
     * @param dto     活动参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateCampaignDto(ScrmMarketingCampaignDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("活动参数不能为空");
        }
        if (dto.getCampaignName() != null) {
            if (dto.getCampaignName().isBlank()) {
                throw ScrmException.badRequest("活动名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("活动名称不能为空");
        }
        if (dto.getCampaignType() != null) {
            if (!VALID_CAMPAIGN_TYPES.contains(dto.getCampaignType())) {
                throw ScrmException.badRequest(
                        "活动类型非法: " + dto.getCampaignType() + ", 仅支持 " + VALID_CAMPAIGN_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("活动类型不能为空");
        }
        if (dto.getChannels() != null) {
            if (dto.getChannels().isBlank()) {
                throw ScrmException.badRequest("渠道不能为空");
            }
            validateChannels(dto.getChannels());
        } else if (!partial) {
            throw ScrmException.badRequest("渠道不能为空");
        }
        if (!partial) {
            if (dto.getStartDate() == null) {
                throw ScrmException.badRequest("开始日期不能为空");
            }
            if (dto.getEndDate() == null) {
                throw ScrmException.badRequest("结束日期不能为空");
            }
        }
        if (dto.getStartDate() != null || dto.getEndDate() != null) {
            validateDateRange(dto.getStartDate(), dto.getEndDate());
        }
    }

    /**
     * 校验渠道字符串合法性 (逗号分隔, 每项须为合法渠道)。
     *
     * @param channels 渠道字符串
     * @throws ScrmException 渠道非法
     */
    private void validateChannels(String channels) throws ScrmException {
        String[] arr = channels.split(",");
        for (String c : arr) {
            String trimmed = c.trim();
            if (!VALID_CHANNELS.contains(trimmed)) {
                throw ScrmException.badRequest(
                        "渠道非法: " + trimmed + ", 仅支持 " + VALID_CHANNELS);
            }
        }
    }

    /**
     * 规范化渠道字符串 (去空白, 去重)。
     *
     * @param channels 渠道字符串
     * @return 规范化后的渠道字符串
     */
    private String normalizeChannels(String channels) {
        if (channels == null || channels.isBlank()) {
            return channels;
        }
        String[] arr = channels.split(",");
        List<String> normalized = new ArrayList<>();
        for (String c : arr) {
            String trimmed = c.trim();
            if (!trimmed.isEmpty() && !normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return String.join(",", normalized);
    }

    /**
     * 校验日期区间合法性 (startDate ≤ endDate, 且均非空时校验)。
     *
     * @param startDate 开始日期 (可空)
     * @param endDate   结束日期 (可空)
     * @throws ScrmException 日期区间非法
     */
    private void validateDateRange(LocalDate startDate, LocalDate endDate) throws ScrmException {
        if (startDate == null || endDate == null) {
            return;
        }
        if (startDate.isAfter(endDate)) {
            throw ScrmException.badRequest("开始日期不能晚于结束日期");
        }
    }

    /**
     * 若活动 channels 字段不包含该渠道, 追加之。
     *
     * @param campaign 活动实体
     * @param channel  渠道
     */
    private void appendChannelIfAbsent(ScrmMarketingCampaignEntity campaign, String channel) {
        String channels = campaign.getChannels();
        if (channels == null || channels.isBlank()) {
            campaign.setChannels(channel);
            return;
        }
        List<String> list = new ArrayList<>(List.of(channels.split(",")));
        boolean exists = false;
        for (String c : list) {
            if (c.trim().equals(channel)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            list.add(channel);
            campaign.setChannels(String.join(",", list));
        }
    }

    /**
     * 刷新活动 metricsJson (汇总各渠道指标为 JSON)。
     *
     * @param campaign 活动实体
     */
    private void refreshMetricsJson(ScrmMarketingCampaignEntity campaign) {
        try {
            Map<String, Object> metrics = getCampaignMetrics(campaign.getId());
            campaign.setMetricsJson(objectMapper.writeValueAsString(metrics));
        } catch (Exception e) {
            log.warn("刷新活动 metricsJson 失败, 忽略: campaignId={}, err={}",
                    campaign.getId(), e.getMessage());
        }
    }

    /**
     * 计算 ROI: (转化金额 - 花费) / 花费 (花费为 0 返回 0)。
     *
     * @param conversionValue 转化金额
     * @param cost            花费
     * @return ROI
     */
    private double calculateROIInternal(double conversionValue, double cost) {
        if (cost <= 0) {
            return 0.0;
        }
        return (conversionValue - cost) / cost;
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    private double toDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 将对象转换为 long 数值。
     *
     * @param obj 对象
     * @return long 值, 不可转换时返回 0
     */
    private long toLong(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取当前操作人 (优先从 UserContext 获取)。
     *
     * @return 操作人用户名
     */
    private String currentOperator() {
        String username = UserContext.getUsername();
        return username != null ? username : DEFAULT_OPERATOR;
    }

    /**
     * 按主键查询活动, 不存在抛异常, 并校验账号归属。
     *
     * @param id 活动 ID
     * @return 活动实体
     * @throws ScrmException 活动不存在
     */
    private ScrmMarketingCampaignEntity findCampaignOrThrow(Long id) throws ScrmException {
        ScrmMarketingCampaignEntity entity = campaignRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "营销活动不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询渠道, 不存在抛异常, 并校验账号归属。
     *
     * @param id 渠道 ID
     * @return 渠道实体
     * @throws ScrmException 渠道不存在
     */
    private ScrmMarketingCampaignChannelEntity findChannelOrThrow(Long id) throws ScrmException {
        ScrmMarketingCampaignChannelEntity entity = channelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "营销活动渠道不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询参与者, 不存在抛异常, 并校验账号归属。
     *
     * @param id 参与者 ID
     * @return 参与者实体
     * @throws ScrmException 参与者不存在
     */
    private ScrmMarketingCampaignParticipantEntity findParticipantOrThrow(Long id) throws ScrmException {
        ScrmMarketingCampaignParticipantEntity entity = participantRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "营销活动参与者不存在: id=" + id));
        return entity;
    }

}
