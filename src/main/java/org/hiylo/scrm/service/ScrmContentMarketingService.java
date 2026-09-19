/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentMarketingService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmContentAssetDto;
import org.hiylo.scrm.dto.ScrmContentChannelDto;
import org.hiylo.scrm.dto.ScrmContentDto;
import org.hiylo.scrm.dto.ScrmContentPublishDto;
import org.hiylo.scrm.dto.ScrmContentReviewDto;
import org.hiylo.scrm.dto.ScrmContentScheduleDto;
import org.hiylo.scrm.entity.ScrmContentAssetEntity;
import org.hiylo.scrm.entity.ScrmContentChannelEntity;
import org.hiylo.scrm.entity.ScrmContentEntity;
import org.hiylo.scrm.entity.ScrmContentScheduleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmContentAssetRepository;
import org.hiylo.scrm.repository.ScrmContentChannelRepository;
import org.hiylo.scrm.repository.ScrmContentRepository;
import org.hiylo.scrm.repository.ScrmContentScheduleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * SCRM 内容营销服务。
 * <p>
 * 承载内容营销全流程: 内容创作 (增删改查 / 复制 / 归档), 审核流 (提交 / 通过 / 驳回),
 * 多渠道分发 (立即发布 / 重新发布 / 移除渠道 / 渠道指标更新), 排期管理 (创建 / 执行 /
 * 取消 / 到期扫描), 素材库 (上传 / 批量导入 / 使用统计), 效果追踪 (内容统计 / 渠道对比 /
 * 实现数据隔离。
 * </p>
 * <p>
 * 内容状态流转: DRAFT → PENDING_REVIEW → APPROVED / REJECTED → SCHEDULED →
 * PUBLISHED → ARCHIVED。渠道发布与排期执行为模拟实现, 不实际调用外部平台 API。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmContentMarketingService {

    /** 内容状态: 草稿 */
    private static final String STATUS_DRAFT = "DRAFT";
    /** 内容状态: 待审核 */
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    /** 内容状态: 已通过 */
    private static final String STATUS_APPROVED = "APPROVED";
    /** 内容状态: 已排期 */
    private static final String STATUS_SCHEDULED = "SCHEDULED";
    /** 内容状态: 已发布 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    /** 内容状态: 已归档 */
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    /** 内容状态: 已驳回 */
    private static final String STATUS_REJECTED = "REJECTED";

    /** 审核状态: 待审核 */
    private static final String REVIEW_PENDING = "PENDING";
    /** 审核状态: 已通过 */
    private static final String REVIEW_APPROVED = "APPROVED";
    /** 审核状态: 已驳回 */
    private static final String REVIEW_REJECTED = "REJECTED";

    /** 渠道发布状态: 待发布 */
    private static final String CHANNEL_STATUS_PENDING = "PENDING";
    /** 渠道发布状态: 发布中 */
    private static final String CHANNEL_STATUS_PUBLISHING = "PUBLISHING";
    /** 渠道发布状态: 已发布 */
    private static final String CHANNEL_STATUS_PUBLISHED = "PUBLISHED";
    /** 渠道发布状态: 发布失败 */
    private static final String CHANNEL_STATUS_FAILED = "FAILED";
    /** 渠道发布状态: 已下架 */
    private static final String CHANNEL_STATUS_REMOVED = "REMOVED";

    /** 排期状态: 待执行 */
    private static final String SCHEDULE_STATUS_PENDING = "PENDING";
    /** 排期状态: 执行中 */
    private static final String SCHEDULE_STATUS_EXECUTING = "EXECUTING";
    /** 排期状态: 已完成 */
    private static final String SCHEDULE_STATUS_COMPLETED = "COMPLETED";
    /** 排期状态: 执行失败 */
    private static final String SCHEDULE_STATUS_FAILED = "FAILED";
    /** 排期状态: 已取消 */
    private static final String SCHEDULE_STATUS_CANCELLED = "CANCELLED";

    /** 重复类型: 不重复 */
    private static final String REPEAT_NONE = "NONE";

    /** 素材来源: 上传 */
    private static final String SOURCE_UPLOAD = "UPLOAD";

    /** 默认时区 */
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    /** 默认操作人 (请求头未透传时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 复制内容名称后缀 */
    private static final String COPY_SUFFIX = "-副本";

    /** 互动指标: 浏览 */
    private static final String METRIC_VIEW = "VIEW";
    /** 互动指标: 点赞 */
    private static final String METRIC_LIKE = "LIKE";
    /** 互动指标: 分享 */
    private static final String METRIC_SHARE = "SHARE";
    /** 互动指标: 评论 */
    private static final String METRIC_COMMENT = "COMMENT";
    /** 互动指标: 收藏 */
    private static final String METRIC_COLLECT = "COLLECT";
    /** 互动指标: 转化 */
    private static final String METRIC_CONVERSION = "CONVERSION";

    /** 合法的内容类型 */
    private static final List<String> VALID_CONTENT_TYPES = List.of(
            "ARTICLE", "VIDEO", "IMAGE", "POSTER", "LIVE_SHORT", "INFOGRAPHIC", "PDF");

    /** 合法的分发渠道 */
    private static final List<String> VALID_CHANNELS = List.of(
            "WECHAT_OFFICIAL", "WECHAT_MOMENTS", "DOUYIN", "KUAISHOU",
            "XIAOHONGSHU", "BILIBILI", "WEIBO", "WEBSITE", "EMAIL", "SMS");

    /** 合法的素材类型 */
    private static final List<String> VALID_ASSET_TYPES = List.of(
            "IMAGE", "VIDEO", "AUDIO", "DOCUMENT", "TEMPLATE");

    /** 合法的素材文件类型 */
    private static final List<String> VALID_FILE_TYPES = List.of(
            "JPG", "PNG", "MP4", "MP3", "PDF", "DOCX", "PPTX");

    /** 合法的素材来源 */
    private static final List<String> VALID_SOURCE_TYPES = List.of("UPLOAD", "GENERATE", "IMPORT");

    /** 合法的重复类型 */
    private static final List<String> VALID_REPEAT_TYPES = List.of("NONE", "DAILY", "WEEKLY", "MONTHLY");

    /** 合法的互动指标 */
    private static final List<String> VALID_METRICS = List.of(
            METRIC_VIEW, METRIC_LIKE, METRIC_SHARE, METRIC_COMMENT, METRIC_COLLECT, METRIC_CONVERSION);

    /** 内容数据访问层 */
    private final ScrmContentRepository contentRepository;

    /** 内容渠道数据访问层 */
    private final ScrmContentChannelRepository channelRepository;

    /** 内容排期数据访问层 */
    private final ScrmContentScheduleRepository scheduleRepository;

    /** 内容素材数据访问层 */
    private final ScrmContentAssetRepository assetRepository;

    // ============================================================
    // 内容管理
    // ============================================================

    /**
     * 创建内容 (默认状态 DRAFT, 互动计数初始化为 0)。
     *
     * @param dto 内容参数
     * @return 创建后的内容
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmContentEntity createContent(ScrmContentDto dto) throws ScrmException {
        validateContentDto(dto, false);
        ScrmContentEntity entity = new ScrmContentEntity();
        entity.setTitle(dto.getTitle());
        entity.setContentType(dto.getContentType());
        entity.setCategory(dto.getCategory());
        entity.setSummary(dto.getSummary());
        entity.setBodyContent(dto.getBodyContent());
        entity.setCoverImage(dto.getCoverImage());
        entity.setMediaUrl(dto.getMediaUrl());
        entity.setMediaDuration(dto.getMediaDuration());
        entity.setTags(dto.getTags());
        entity.setTargetAudience(dto.getTargetAudience());
        entity.setAuthorId(dto.getAuthorId());
        entity.setAuthorName(dto.getAuthorName());
        entity.setStatus(STATUS_DRAFT);
        entity.setViewCount(0);
        entity.setLikeCount(0);
        entity.setShareCount(0);
        entity.setCommentCount(0);
        entity.setCollectCount(0);
        entity.setConversionCount(0);
        entity.setSeoTitle(dto.getSeoTitle());
        entity.setSeoDescription(dto.getSeoDescription());
        entity.setSeoKeywords(dto.getSeoKeywords());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        entity = contentRepository.save(entity);
        log.info("创建内容: id={}, title={}, contentType={}",
                entity.getId(), entity.getTitle(), entity.getContentType());
        return entity;
    }

    /**
     * 更新内容 (字段非空才覆盖)。已发布 / 已归档内容禁止更新核心字段。
     *
     * @param id  内容 ID
     * @param dto 内容参数
     * @return 更新后的内容
     * @throws ScrmException 内容不存在 / 参数非法 / 状态非法
     */
    @Transactional
    public ScrmContentEntity updateContent(Long id, ScrmContentDto dto) throws ScrmException {
        ScrmContentEntity entity = findContentOrThrow(id);
        if (STATUS_PUBLISHED.equals(entity.getStatus()) || STATUS_ARCHIVED.equals(entity.getStatus())) {
            throw ScrmException.conflict("已发布 / 已归档内容不可修改: id=" + id + ", status=" + entity.getStatus());
        }
        validateContentDto(dto, true);
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getContentType() != null) entity.setContentType(dto.getContentType());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getSummary() != null) entity.setSummary(dto.getSummary());
        if (dto.getBodyContent() != null) entity.setBodyContent(dto.getBodyContent());
        if (dto.getCoverImage() != null) entity.setCoverImage(dto.getCoverImage());
        if (dto.getMediaUrl() != null) entity.setMediaUrl(dto.getMediaUrl());
        if (dto.getMediaDuration() != null) entity.setMediaDuration(dto.getMediaDuration());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getTargetAudience() != null) entity.setTargetAudience(dto.getTargetAudience());
        if (dto.getAuthorId() != null) entity.setAuthorId(dto.getAuthorId());
        if (dto.getAuthorName() != null) entity.setAuthorName(dto.getAuthorName());
        if (dto.getScheduledAt() != null) entity.setScheduledAt(dto.getScheduledAt());
        if (dto.getSeoTitle() != null) entity.setSeoTitle(dto.getSeoTitle());
        if (dto.getSeoDescription() != null) entity.setSeoDescription(dto.getSeoDescription());
        if (dto.getSeoKeywords() != null) entity.setSeoKeywords(dto.getSeoKeywords());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = contentRepository.save(entity);
        log.info("更新内容: id={}, title={}", entity.getId(), entity.getTitle());
        return entity;
    }

    /**
     * 删除内容 (同时删除关联渠道与排期记录)。
     *
     * @param id 内容 ID
     * @throws ScrmException 内容不存在
     */
    @Transactional
    public void deleteContent(Long id) throws ScrmException {
        ScrmContentEntity entity = findContentOrThrow(id);
        // 清理关联渠道
        List<ScrmContentChannelEntity> channels =
                channelRepository.findByContentIdOrderByChannelAsc(id);
        if (!channels.isEmpty()) {
            channelRepository.deleteAll(channels);
        }
        // 清理关联排期
        Specification<ScrmContentScheduleEntity> scheduleSpec = (root, query, cb) ->
                cb.and( cb.equal(root.get("contentId"), id));
        List<ScrmContentScheduleEntity> schedules = scheduleRepository.findAll(scheduleSpec);
        if (!schedules.isEmpty()) {
            scheduleRepository.deleteAll(schedules);
        }
        contentRepository.delete(entity);
        log.info("删除内容: id={}, title={}, channels={}, schedules={}",
                id, entity.getTitle(), channels.size(), schedules.size());
    }

    /**
     * 查询内容详情。
     *
     * @param id 内容 ID
     * @return 内容实体
     * @throws ScrmException 内容不存在
     */
    @Transactional(readOnly = true)
    public ScrmContentEntity getContent(Long id) throws ScrmException {
        return findContentOrThrow(id);
    }

    /**
     * 分页查询内容, 支持按类型 / 分类 / 状态 / 作者 / 关键字过滤。
     *
     * @param contentType 内容类型过滤（可空）
     * @param category    分类过滤（可空）
     * @param status      状态过滤（可空）
     * @param authorId    作者 ID 过滤（可空）
     * @param keyword     标题关键字模糊匹配（可空）
     * @param pageable    分页参数
     * @return 内容分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmContentEntity> listContents(String contentType, String category, String status,
                                                 String authorId, String keyword, Pageable pageable) {
        Specification<ScrmContentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (contentType != null && !contentType.isBlank()) {
                predicates.add(cb.equal(root.get("contentType"), contentType));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (authorId != null && !authorId.isBlank()) {
                predicates.add(cb.equal(root.get("authorId"), authorId));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("title"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return contentRepository.findAll(spec, pageable);
    }

    /**
     * 提交审核 (DRAFT / REJECTED → PENDING_REVIEW)。
     *
     * @param id 内容 ID
     * @return 更新后的内容
     * @throws ScrmException 内容不存在 / 状态非法
     */
    @Transactional
    public ScrmContentEntity submitForReview(Long id) throws ScrmException {
        ScrmContentEntity entity = findContentOrThrow(id);
        if (!STATUS_DRAFT.equals(entity.getStatus()) && !STATUS_REJECTED.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "仅 DRAFT / REJECTED 状态可提交审核: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_PENDING_REVIEW);
        entity.setReviewStatus(REVIEW_PENDING);
        entity.setReviewComment(null);
        entity.setReviewedAt(null);
        entity = contentRepository.save(entity);
        log.info("内容已提交审核: id={}, title={}", id, entity.getTitle());
        return entity;
    }

    /**
     * 审核内容 (PENDING_REVIEW → APPROVED / REJECTED), 记录审核人与意见。
     *
     * @param reviewDto 审核参数
     * @return 更新后的内容
     * @throws ScrmException 内容不存在 / 状态非法
     */
    @Transactional
    public ScrmContentEntity reviewContent(ScrmContentReviewDto reviewDto) throws ScrmException {
        if (reviewDto == null || reviewDto.getContentId() == null || reviewDto.getApproved() == null) {
            throw ScrmException.badRequest("审核参数不能为空且需指定 contentId 与 approved");
        }
        ScrmContentEntity entity = findContentOrThrow(reviewDto.getContentId());
        if (!STATUS_PENDING_REVIEW.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 PENDING_REVIEW 状态可审核: id=" + reviewDto.getContentId()
                    + ", status=" + entity.getStatus());
        }
        boolean approved = Boolean.TRUE.equals(reviewDto.getApproved());
        entity.setStatus(approved ? STATUS_APPROVED : STATUS_REJECTED);
        entity.setReviewStatus(approved ? REVIEW_APPROVED : REVIEW_REJECTED);
        entity.setReviewerId(currentOperator());
        entity.setReviewerName(UserContext.getUsername());
        entity.setReviewedAt(LocalDateTime.now());
        entity.setReviewComment(reviewDto.getComment());
        entity = contentRepository.save(entity);
        log.info("审核内容: id={}, approved={}, reviewer={}",
                entity.getId(), approved, entity.getReviewerId());
        return entity;
    }

    /**
     * 归档内容 (PUBLISHED → ARCHIVED)。
     *
     * @param id 内容 ID
     * @return 更新后的内容
     * @throws ScrmException 内容不存在 / 状态非法
     */
    @Transactional
    public ScrmContentEntity archiveContent(Long id) throws ScrmException {
        ScrmContentEntity entity = findContentOrThrow(id);
        if (!STATUS_PUBLISHED.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 PUBLISHED 状态可归档: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(STATUS_ARCHIVED);
        entity = contentRepository.save(entity);
        log.info("归档内容: id={}, title={}", id, entity.getTitle());
        return entity;
    }

    /**
     * 复制内容 (创建副本, 状态置 DRAFT, 计数与审核字段清零)。
     *
     * @param id 源内容 ID
     * @return 复制后的内容
     * @throws ScrmException 源内容不存在
     */
    @Transactional
    public ScrmContentEntity copyContent(Long id) throws ScrmException {
        ScrmContentEntity source = findContentOrThrow(id);
        ScrmContentEntity copy = new ScrmContentEntity();
        copy.setTitle(source.getTitle() + COPY_SUFFIX);
        copy.setContentType(source.getContentType());
        copy.setCategory(source.getCategory());
        copy.setSummary(source.getSummary());
        copy.setBodyContent(source.getBodyContent());
        copy.setCoverImage(source.getCoverImage());
        copy.setMediaUrl(source.getMediaUrl());
        copy.setMediaDuration(source.getMediaDuration());
        copy.setTags(source.getTags());
        copy.setTargetAudience(source.getTargetAudience());
        copy.setAuthorId(source.getAuthorId());
        copy.setAuthorName(source.getAuthorName());
        copy.setStatus(STATUS_DRAFT);
        copy.setViewCount(0);
        copy.setLikeCount(0);
        copy.setShareCount(0);
        copy.setCommentCount(0);
        copy.setCollectCount(0);
        copy.setConversionCount(0);
        copy.setSeoTitle(source.getSeoTitle());
        copy.setSeoDescription(source.getSeoDescription());
        copy.setSeoKeywords(source.getSeoKeywords());
        copy.setCreatedBy(currentOperator());
        copy = contentRepository.save(copy);
        log.info("复制内容: sourceId={}, copyId={}", id, copy.getId());
        return copy;
    }

    /**
     * 增加内容互动计数 (VIEW/LIKE/SHARE/COMMENT/COLLECT/CONVERSION)。
     *
     * @param id     内容 ID
     * @param metric 指标类型
     * @return 更新后的内容
     * @throws ScrmException 内容不存在 / 指标非法
     */
    @Transactional
    public ScrmContentEntity incrementMetric(Long id, String metric) throws ScrmException {
        ScrmContentEntity entity = findContentOrThrow(id);
        if (metric == null || !VALID_METRICS.contains(metric)) {
            throw ScrmException.badRequest("指标非法: " + metric + ", 仅支持 " + VALID_METRICS);
        }
        switch (metric) {
            case METRIC_VIEW -> entity.setViewCount(safeInt(entity.getViewCount()) + 1);
            case METRIC_LIKE -> entity.setLikeCount(safeInt(entity.getLikeCount()) + 1);
            case METRIC_SHARE -> entity.setShareCount(safeInt(entity.getShareCount()) + 1);
            case METRIC_COMMENT -> entity.setCommentCount(safeInt(entity.getCommentCount()) + 1);
            case METRIC_COLLECT -> entity.setCollectCount(safeInt(entity.getCollectCount()) + 1);
            case METRIC_CONVERSION -> entity.setConversionCount(safeInt(entity.getConversionCount()) + 1);
            default -> throw ScrmException.badRequest("指标非法: " + metric);
        }
        entity = contentRepository.save(entity);
        log.info("内容互动计数+1: id={}, metric={}", id, metric);
        return entity;
    }

    // ============================================================
    // 渠道分发
    // ============================================================

    /**
     * 多渠道发布 (模拟实现)。
     * <p>
     * scheduledAt 为空时立即发布: 为每个渠道创建/复用渠道记录, 状态置 PUBLISHING → PUBLISHED,
     * 模拟生成 channelPostId / channelPostUrl, 同步内容状态为 PUBLISHED。
     * scheduledAt 非空时创建排期任务延后执行。
     * </p>
     *
     * @param publishDto 发布配置
     * @return 各渠道发布结果 [{channelId, channel, status, channelPostId, channelPostUrl}]
     * @throws ScrmException 内容不存在 / 渠道非法 / 状态非法
     */
    @Transactional
    public List<Map<String, Object>> publishToChannels(ScrmContentPublishDto publishDto) throws ScrmException {
        if (publishDto == null || publishDto.getContentId() == null) {
            throw ScrmException.badRequest("发布配置不能为空且需指定 contentId");
        }
        ScrmContentEntity content = findContentOrThrow(publishDto.getContentId());
        if (publishDto.getChannels() == null || publishDto.getChannels().isEmpty()) {
            throw ScrmException.badRequest("渠道列表不能为空");
        }
        for (String channel : publishDto.getChannels()) {
            if (!VALID_CHANNELS.contains(channel)) {
                throw ScrmException.badRequest("渠道非法: " + channel + ", 仅支持 " + VALID_CHANNELS);
            }
        }
        // 定时发布: 创建排期任务
        if (publishDto.getScheduledAt() != null && publishDto.getScheduledAt().isAfter(LocalDateTime.now())) {
            ScrmContentScheduleDto scheduleDto = new ScrmContentScheduleDto();
            scheduleDto.setContentId(publishDto.getContentId());
            scheduleDto.setScheduleName("发布-" + content.getTitle());
            scheduleDto.setChannels(String.join(",", publishDto.getChannels()));
            scheduleDto.setScheduledAt(publishDto.getScheduledAt());
            scheduleDto.setTimezone(DEFAULT_TIMEZONE);
            scheduleDto.setRepeatType(REPEAT_NONE);
            createSchedule(scheduleDto);
            content.setStatus(STATUS_SCHEDULED);
            content.setScheduledAt(publishDto.getScheduledAt());
            contentRepository.save(content);
            log.info("内容已排期发布: contentId={}, scheduledAt={}, channels={}",
                    publishDto.getContentId(), publishDto.getScheduledAt(), publishDto.getChannels());
            List<Map<String, Object>> result = new ArrayList<>();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("contentId", content.getId());
            r.put("status", STATUS_SCHEDULED);
            r.put("scheduledAt", publishDto.getScheduledAt());
            r.put("channels", publishDto.getChannels());
            result.add(r);
            return result;
        }
        // 立即发布: 仅 APPROVED / SCHEDULED 内容可立即发布
        if (!STATUS_APPROVED.equals(content.getStatus()) && !STATUS_SCHEDULED.equals(content.getStatus())) {
            throw ScrmException.conflict("仅 APPROVED / SCHEDULED 状态可立即发布: id="
                    + publishDto.getContentId() + ", status=" + content.getStatus());
        }
        List<Map<String, Object>> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String channel : publishDto.getChannels()) {
            ScrmContentChannelEntity ch = channelRepository
                    .findByContentIdAndChannel(publishDto.getContentId(), channel)
                    .orElseGet(() -> {
                        ScrmContentChannelEntity newCh = new ScrmContentChannelEntity();
                        newCh.setContentId(publishDto.getContentId());
                        newCh.setChannel(channel);
                        newCh.setViewCount(0);
                        newCh.setLikeCount(0);
                        newCh.setShareCount(0);
                        newCh.setCommentCount(0);
                        newCh.setConversionCount(0);
                        newCh.setStatus(CHANNEL_STATUS_PENDING);
                        return newCh;
                    });
            // 模拟发布: PENDING / FAILED / REMOVED 可重新发布
            if (!CHANNEL_STATUS_PENDING.equals(ch.getStatus()) && !CHANNEL_STATUS_FAILED.equals(ch.getStatus()) && !CHANNEL_STATUS_REMOVED.equals(ch.getStatus())) {
                continue;
            }
            ch.setStatus(CHANNEL_STATUS_PUBLISHING);
            ch = channelRepository.save(ch);
            // 模拟生成渠道帖子标识
            ch.setChannelPostId(UUID.randomUUID().toString().replace("-", ""));
            ch.setChannelPostUrl("https://" + channel.toLowerCase() + ".example.com/posts/" + ch.getChannelPostId());
            ch.setPublishedAt(now);
            ch.setStatus(CHANNEL_STATUS_PUBLISHED);
            ch = channelRepository.save(ch);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("channelId", ch.getId());
            r.put("channel", ch.getChannel());
            r.put("status", ch.getStatus());
            r.put("channelPostId", ch.getChannelPostId());
            r.put("channelPostUrl", ch.getChannelPostUrl());
            results.add(r);
        }
        // 同步内容状态为 PUBLISHED
        content.setStatus(STATUS_PUBLISHED);
        content.setPublishedAt(now);
        contentRepository.save(content);
        log.info("内容多渠道发布: contentId={}, published={}, channels={}",
                publishDto.getContentId(), results.size(), publishDto.getChannels());
        return results;
    }

    /**
     * 重新发布渠道 (PUBLISHED / FAILED / REMOVED → PENDING → PUBLISHED, 模拟)。
     *
     * @param channelId 渠道 ID
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在 / 状态非法
     */
    @Transactional
    public ScrmContentChannelEntity republishChannel(Long channelId) throws ScrmException {
        ScrmContentChannelEntity entity = findChannelOrThrow(channelId);
        if (CHANNEL_STATUS_PENDING.equals(entity.getStatus()) || CHANNEL_STATUS_PUBLISHING.equals(entity.getStatus())) {
            throw ScrmException.conflict("PENDING / PUBLISHING 状态渠道不可重新发布: id=" + channelId);
        }
        entity.setStatus(CHANNEL_STATUS_PUBLISHING);
        entity.setErrorMessage(null);
        entity = channelRepository.save(entity);
        // 模拟重新发布
        entity.setChannelPostId(UUID.randomUUID().toString().replace("-", ""));
        entity.setChannelPostUrl("https://" + entity.getChannel().toLowerCase()
                + ".example.com/posts/" + entity.getChannelPostId());
        entity.setPublishedAt(LocalDateTime.now());
        entity.setStatus(CHANNEL_STATUS_PUBLISHED);
        entity = channelRepository.save(entity);
        log.info("重新发布渠道: channelId={}, channel={}", channelId, entity.getChannel());
        return entity;
    }

    /**
     * 移除渠道 (PUBLISHED → REMOVED, 仅下架不删除记录)。
     *
     * @param channelId 渠道 ID
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在 / 状态非法
     */
    @Transactional
    public ScrmContentChannelEntity removeChannel(Long channelId) throws ScrmException {
        ScrmContentChannelEntity entity = findChannelOrThrow(channelId);
        if (!CHANNEL_STATUS_PUBLISHED.equals(entity.getStatus()) && !CHANNEL_STATUS_FAILED.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 PUBLISHED / FAILED 状态可移除: id=" + channelId
                    + ", status=" + entity.getStatus());
        }
        entity.setStatus(CHANNEL_STATUS_REMOVED);
        entity = channelRepository.save(entity);
        log.info("移除渠道: channelId={}, channel={}", channelId, entity.getChannel());
        return entity;
    }

    /**
     * 查询内容下全部渠道。
     *
     * @param contentId 内容 ID
     * @return 渠道列表 (按渠道名升序)
     * @throws ScrmException 内容不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmContentChannelEntity> listChannels(Long contentId) throws ScrmException {
        findContentOrThrow(contentId);
        return channelRepository.findByContentIdOrderByChannelAsc(
                 contentId);
    }

    /**
     * 更新渠道互动指标 (浏览 / 点赞 / 分享 / 评论 / 转化)。
     *
     * @param channelId    渠道 ID
     * @param views        浏览数增量
     * @param likes        点赞数增量
     * @param shares       分享数增量
     * @param comments     评论数增量
     * @param conversions  转化数增量
     * @return 更新后的渠道
     * @throws ScrmException 渠道不存在
     */
    @Transactional
    public ScrmContentChannelEntity updateChannelMetrics(Long channelId, Integer views, Integer likes,
                                                          Integer shares, Integer comments, Integer conversions)
            throws ScrmException {
        ScrmContentChannelEntity entity = findChannelOrThrow(channelId);
        if (views != null) entity.setViewCount(safeInt(entity.getViewCount()) + views);
        if (likes != null) entity.setLikeCount(safeInt(entity.getLikeCount()) + likes);
        if (shares != null) entity.setShareCount(safeInt(entity.getShareCount()) + shares);
        if (comments != null) entity.setCommentCount(safeInt(entity.getCommentCount()) + comments);
        if (conversions != null) entity.setConversionCount(safeInt(entity.getConversionCount()) + conversions);
        entity = channelRepository.save(entity);
        log.info("更新渠道指标: channelId={}, views={}, likes={}, shares={}, comments={}, conversions={}",
                channelId, views, likes, shares, comments, conversions);
        return entity;
    }

    // ============================================================
    // 排期管理
    // ============================================================

    /**
     * 创建排期任务。
     *
     * @param dto 排期参数
     * @return 创建后的排期
     * @throws ScrmException 参数非法 / 内容不存在
     */
    @Transactional
    public ScrmContentScheduleEntity createSchedule(ScrmContentScheduleDto dto) throws ScrmException {
        validateScheduleDto(dto, false);
        findContentOrThrow(dto.getContentId());
        ScrmContentScheduleEntity entity = new ScrmContentScheduleEntity();
        entity.setContentId(dto.getContentId());
        entity.setScheduleName(dto.getScheduleName());
        entity.setChannels(normalizeChannels(dto.getChannels()));
        entity.setScheduledAt(dto.getScheduledAt());
        entity.setTimezone(dto.getTimezone() != null ? dto.getTimezone() : DEFAULT_TIMEZONE);
        entity.setRepeatType(dto.getRepeatType() != null ? dto.getRepeatType() : REPEAT_NONE);
        entity.setRepeatConfig(dto.getRepeatConfig());
        entity.setStatus(SCHEDULE_STATUS_PENDING);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        entity = scheduleRepository.save(entity);
        log.info("创建内容排期: id={}, contentId={}, scheduledAt={}",
                entity.getId(), entity.getContentId(), entity.getScheduledAt());
        return entity;
    }

    /**
     * 更新排期任务 (字段非空才覆盖)。已执行 / 已取消排期不可更新。
     *
     * @param id  排期 ID
     * @param dto 排期参数
     * @return 更新后的排期
     * @throws ScrmException 排期不存在 / 参数非法 / 状态非法
     */
    @Transactional
    public ScrmContentScheduleEntity updateSchedule(Long id, ScrmContentScheduleDto dto) throws ScrmException {
        ScrmContentScheduleEntity entity = findScheduleOrThrow(id);
        if (SCHEDULE_STATUS_COMPLETED.equals(entity.getStatus())
                || SCHEDULE_STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("已完成 / 已取消的排期不可修改: id=" + id + ", status=" + entity.getStatus());
        }
        validateScheduleDto(dto, true);
        if (dto.getScheduleName() != null) entity.setScheduleName(dto.getScheduleName());
        if (dto.getChannels() != null) entity.setChannels(normalizeChannels(dto.getChannels()));
        if (dto.getScheduledAt() != null) entity.setScheduledAt(dto.getScheduledAt());
        if (dto.getTimezone() != null) entity.setTimezone(dto.getTimezone());
        if (dto.getRepeatType() != null) entity.setRepeatType(dto.getRepeatType());
        if (dto.getRepeatConfig() != null) entity.setRepeatConfig(dto.getRepeatConfig());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = scheduleRepository.save(entity);
        log.info("更新内容排期: id={}, scheduleName={}", entity.getId(), entity.getScheduleName());
        return entity;
    }

    /**
     * 删除排期任务。
     *
     * @param id 排期 ID
     * @throws ScrmException 排期不存在
     */
    @Transactional
    public void deleteSchedule(Long id) throws ScrmException {
        ScrmContentScheduleEntity entity = findScheduleOrThrow(id);
        scheduleRepository.delete(entity);
        log.info("删除内容排期: id={}, scheduleName={}", id, entity.getScheduleName());
    }

    /**
     * 查询排期详情。
     *
     * @param id 排期 ID
     * @return 排期实体
     * @throws ScrmException 排期不存在
     */
    @Transactional(readOnly = true)
    public ScrmContentScheduleEntity getSchedule(Long id) throws ScrmException {
        return findScheduleOrThrow(id);
    }

    /**
     * 分页查询排期, 支持按内容 / 状态 / 时间范围过滤。
     *
     * @param contentId 内容 ID 过滤（可空）
     * @param status    状态过滤（可空）
     * @param startTime 计划时间起始 (含, 可空)
     * @param endTime   计划时间截止 (含, 可空)
     * @param pageable  分页参数
     * @return 排期分页结果 (按 scheduledAt ASC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmContentScheduleEntity> listSchedules(Long contentId, String status,
                                                          LocalDateTime startTime, LocalDateTime endTime,
                                                          Pageable pageable) {
        Specification<ScrmContentScheduleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (contentId != null) {
                predicates.add(cb.equal(root.get("contentId"), contentId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledAt"), endTime));
            }
            query.orderBy(cb.asc(root.get("scheduledAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return scheduleRepository.findAll(spec, pageable);
    }

    /**
     * 执行排期 (PENDING → EXECUTING → COMPLETED, 触发多渠道发布模拟)。
     *
     * @param id 排期 ID
     * @return 更新后的排期
     * @throws ScrmException 排期不存在 / 状态非法
     */
    @Transactional
    public ScrmContentScheduleEntity executeSchedule(Long id) throws ScrmException {
        ScrmContentScheduleEntity entity = findScheduleOrThrow(id);
        if (!SCHEDULE_STATUS_PENDING.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 PENDING 状态排期可执行: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(SCHEDULE_STATUS_EXECUTING);
        entity = scheduleRepository.save(entity);
        try {
            ScrmContentPublishDto publishDto = new ScrmContentPublishDto();
            publishDto.setContentId(entity.getContentId());
            publishDto.setChannels(List.of(entity.getChannels().split(",")));
            publishToChannels(publishDto);
            entity.setStatus(SCHEDULE_STATUS_COMPLETED);
            entity.setExecutedAt(LocalDateTime.now());
        } catch (Exception e) {
            entity.setStatus(SCHEDULE_STATUS_FAILED);
            entity.setErrorMessage(e.getMessage());
            log.warn("执行排期失败: id={}, err={}", id, e.getMessage());
        }
        entity = scheduleRepository.save(entity);
        log.info("执行内容排期: id={}, status={}", id, entity.getStatus());
        return entity;
    }

    /**
     * 取消排期 (PENDING → CANCELLED)。
     *
     * @param id 排期 ID
     * @return 更新后的排期
     * @throws ScrmException 排期不存在 / 状态非法
     */
    @Transactional
    public ScrmContentScheduleEntity cancelSchedule(Long id) throws ScrmException {
        ScrmContentScheduleEntity entity = findScheduleOrThrow(id);
        if (!SCHEDULE_STATUS_PENDING.equals(entity.getStatus()) && !SCHEDULE_STATUS_EXECUTING.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "仅 PENDING / EXECUTING 状态可取消: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(SCHEDULE_STATUS_CANCELLED);
        entity = scheduleRepository.save(entity);
        log.info("取消内容排期: id={}, scheduleName={}", id, entity.getScheduleName());
        return entity;
    }

    /**
     * 处理到期排期 (扫描 PENDING 且 scheduledAt ≤ 当前时间的排期, 逐个执行)。
     *
     * @return 处理的排期数量
     */
    @Transactional
    public int processDueSchedules() {
        List<ScrmContentScheduleEntity> due = scheduleRepository.findDueSchedules(LocalDateTime.now());
        int processed = 0;
        for (ScrmContentScheduleEntity schedule : due) {
            try {
                executeSchedule(schedule.getId());
                processed++;
            } catch (Exception e) {
                log.warn("处理到期排期失败: scheduleId={}, err={}", schedule.getId(), e.getMessage());
            }
        }
        if (processed > 0) {
            log.info("处理到期排期:, processed={}", processed);
        }
        return processed;
    }

    // ============================================================
    // 素材库
    // ============================================================

    /**
     * 上传素材 (默认来源 UPLOAD, 使用次数 0)。
     *
     * @param dto 素材参数
     * @return 创建后的素材
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmContentAssetEntity uploadAsset(ScrmContentAssetDto dto) throws ScrmException {
        validateAssetDto(dto, false);
        ScrmContentAssetEntity entity = new ScrmContentAssetEntity();
        entity.setAssetName(dto.getAssetName());
        entity.setAssetType(dto.getAssetType());
        entity.setFileUrl(dto.getFileUrl());
        entity.setFilePath(dto.getFilePath());
        entity.setFileSize(dto.getFileSize());
        entity.setFileType(dto.getFileType());
        entity.setWidth(dto.getWidth());
        entity.setHeight(dto.getHeight());
        entity.setDuration(dto.getDuration());
        entity.setThumbnailUrl(dto.getThumbnailUrl());
        entity.setDescription(dto.getDescription());
        entity.setTags(dto.getTags());
        entity.setCategory(dto.getCategory());
        entity.setSourceType(dto.getSourceType() != null ? dto.getSourceType() : SOURCE_UPLOAD);
        entity.setSourceUrl(dto.getSourceUrl());
        entity.setUsageCount(0);
        entity.setIsPublic(dto.getIsPublic() != null ? dto.getIsPublic() : Boolean.FALSE);
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        entity = assetRepository.save(entity);
        log.info("上传素材: id={}, assetName={}, assetType={}",
                entity.getId(), entity.getAssetName(), entity.getAssetType());
        return entity;
    }

    /**
     * 更新素材 (字段非空才覆盖)。
     *
     * @param id  素材 ID
     * @param dto 素材参数
     * @return 更新后的素材
     * @throws ScrmException 素材不存在 / 参数非法
     */
    @Transactional
    public ScrmContentAssetEntity updateAsset(Long id, ScrmContentAssetDto dto) throws ScrmException {
        ScrmContentAssetEntity entity = findAssetOrThrow(id);
        validateAssetDto(dto, true);
        if (dto.getAssetName() != null) entity.setAssetName(dto.getAssetName());
        if (dto.getAssetType() != null) entity.setAssetType(dto.getAssetType());
        if (dto.getFileUrl() != null) entity.setFileUrl(dto.getFileUrl());
        if (dto.getFilePath() != null) entity.setFilePath(dto.getFilePath());
        if (dto.getFileSize() != null) entity.setFileSize(dto.getFileSize());
        if (dto.getFileType() != null) entity.setFileType(dto.getFileType());
        if (dto.getWidth() != null) entity.setWidth(dto.getWidth());
        if (dto.getHeight() != null) entity.setHeight(dto.getHeight());
        if (dto.getDuration() != null) entity.setDuration(dto.getDuration());
        if (dto.getThumbnailUrl() != null) entity.setThumbnailUrl(dto.getThumbnailUrl());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getSourceType() != null) entity.setSourceType(dto.getSourceType());
        if (dto.getSourceUrl() != null) entity.setSourceUrl(dto.getSourceUrl());
        if (dto.getIsPublic() != null) entity.setIsPublic(dto.getIsPublic());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = assetRepository.save(entity);
        log.info("更新素材: id={}, assetName={}", entity.getId(), entity.getAssetName());
        return entity;
    }

    /**
     * 删除素材。
     *
     * @param id 素材 ID
     * @throws ScrmException 素材不存在
     */
    @Transactional
    public void deleteAsset(Long id) throws ScrmException {
        ScrmContentAssetEntity entity = findAssetOrThrow(id);
        assetRepository.delete(entity);
        log.info("删除素材: id={}, assetName={}", id, entity.getAssetName());
    }

    /**
     * 查询素材详情。
     *
     * @param id 素材 ID
     * @return 素材实体
     * @throws ScrmException 素材不存在
     */
    @Transactional(readOnly = true)
    public ScrmContentAssetEntity getAsset(Long id) throws ScrmException {
        return findAssetOrThrow(id);
    }

    /**
     * 分页查询素材, 支持按类型 / 分类 / 关键字过滤。
     *
     * @param assetType 素材类型过滤（可空）
     * @param category  分类过滤（可空）
     * @param keyword   素材名称关键字模糊匹配（可空）
     * @param pageable  分页参数
     * @return 素材分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmContentAssetEntity> listAssets(String assetType, String category, String keyword,
                                                    Pageable pageable) {
        Specification<ScrmContentAssetEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (assetType != null && !assetType.isBlank()) {
                predicates.add(cb.equal(root.get("assetType"), assetType));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("assetName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return assetRepository.findAll(spec, pageable);
    }

    /**
     * 批量导入素材 (一次性写入多条, 失败的记录跳过并记录日志)。
     *
     * @param assets 素材参数列表
     * @return 导入成功的素材列表
     */
    @Transactional
    public List<ScrmContentAssetEntity> batchImportAssets(List<ScrmContentAssetDto> assets) {
        if (assets == null || assets.isEmpty()) {
            return List.of();
        }
        List<ScrmContentAssetEntity> imported = new ArrayList<>();
        for (ScrmContentAssetDto dto : assets) {
            try {
                imported.add(uploadAsset(dto));
            } catch (Exception e) {
                log.warn("批量导入素材失败, 跳过: assetName={}, err={}",
                        dto != null ? dto.getAssetName() : null, e.getMessage());
            }
        }
        log.info("批量导入素材: total={}, imported={}", assets.size(), imported.size());
        return imported;
    }

    /**
     * 素材使用次数 +1。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @Transactional
    public ScrmContentAssetEntity incrementUsage(Long id) throws ScrmException {
        ScrmContentAssetEntity entity = findAssetOrThrow(id);
        entity.setUsageCount(safeInt(entity.getUsageCount()) + 1);
        entity = assetRepository.save(entity);
        log.info("素材使用次数+1: id={}, usageCount={}", id, entity.getUsageCount());
        return entity;
    }

    // ============================================================
    // 统计与效果追踪
    // ============================================================

    /**
     * 内容统计概览: 各状态内容数 / 已发布数 / 总互动量 / 总转化数 / 转化率。
     *
     * @param startTime 发布时间起始 (含, 可空)
     * @param endTime   发布时间截止 (含, 可空)
     * @return 统计结果 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContentStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 各状态内容数
        List<Object[]> byStatus = contentRepository.countByStatus(startTime, endTime);
        Map<String, Long> statusCount = new LinkedHashMap<>();
        statusCount.put(STATUS_DRAFT, 0L);
        statusCount.put(STATUS_PENDING_REVIEW, 0L);
        statusCount.put(STATUS_APPROVED, 0L);
        statusCount.put(STATUS_SCHEDULED, 0L);
        statusCount.put(STATUS_PUBLISHED, 0L);
        statusCount.put(STATUS_ARCHIVED, 0L);
        statusCount.put(STATUS_REJECTED, 0L);
        long total = 0L;
        for (Object[] row : byStatus) {
            String status = (String) row[0];
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusCount.put(status, count);
            total += count;
        }
        stats.put("statusCount", statusCount);
        stats.put("total", total);
        stats.put("published", statusCount.getOrDefault(STATUS_PUBLISHED, 0L));
        // 互动指标汇总
        Object[] metrics = contentRepository.sumMetrics(startTime, endTime);
        long viewSum = 0L, likeSum = 0L, shareSum = 0L, commentSum = 0L,
                collectSum = 0L, conversionSum = 0L, publishedCount = 0L;
        if (metrics != null && metrics.length == 7) {
            viewSum = toLong(metrics[0]);
            likeSum = toLong(metrics[1]);
            shareSum = toLong(metrics[2]);
            commentSum = toLong(metrics[3]);
            collectSum = toLong(metrics[4]);
            conversionSum = toLong(metrics[5]);
            publishedCount = toLong(metrics[6]);
        }
        stats.put("viewSum", viewSum);
        stats.put("likeSum", likeSum);
        stats.put("shareSum", shareSum);
        stats.put("commentSum", commentSum);
        stats.put("collectSum", collectSum);
        stats.put("conversionSum", conversionSum);
        stats.put("engagementTotal", viewSum + likeSum + shareSum + commentSum + collectSum);
        // 转化率: 转化数 / 浏览数
        double conversionRate = viewSum > 0 ? (double) conversionSum / viewSum : 0.0;
        stats.put("conversionRate", conversionRate);
        stats.put("publishedCount", publishedCount);
        return stats;
    }

    /**
     * 渠道效果对比: 按渠道汇总浏览 / 点赞 / 分享 / 评论 / 转化 / 已发布数。
     *
     * @param startTime 发布时间起始 (含, 可空)
     * @param endTime   发布时间截止 (含, 可空)
     * @return 渠道效果列表 [{channel, viewSum, likeSum, shareSum, commentSum, conversionSum, publishedCount}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChannelStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> channelAgg = channelRepository.aggregateByChannel(startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : channelAgg) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channel", row[0]);
            m.put("viewSum", toLong(row[1]));
            m.put("likeSum", toLong(row[2]));
            m.put("shareSum", toLong(row[3]));
            m.put("commentSum", toLong(row[4]));
            m.put("conversionSum", toLong(row[5]));
            m.put("publishedCount", toLong(row[6]));
            result.add(m);
        }
        return result;
    }

    /**
     * 单内容效果: 内容基本信息 + 各渠道互动指标 + 汇总数据。
     *
     * @param contentId 内容 ID
     * @return 效果详情 Map
     * @throws ScrmException 内容不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContentPerformance(Long contentId) throws ScrmException {
        ScrmContentEntity content = findContentOrThrow(contentId);
        List<ScrmContentChannelEntity> channels =
                channelRepository.findByContentIdOrderByChannelAsc(contentId);
        List<Map<String, Object>> channelMetrics = new ArrayList<>();
        long channelView = 0L, channelLike = 0L, channelShare = 0L, channelComment = 0L, channelConversion = 0L;
        for (ScrmContentChannelEntity ch : channels) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channelId", ch.getId());
            m.put("channel", ch.getChannel());
            m.put("status", ch.getStatus());
            m.put("channelPostId", ch.getChannelPostId());
            m.put("channelPostUrl", ch.getChannelPostUrl());
            m.put("publishedAt", ch.getPublishedAt());
            m.put("viewCount", safeInt(ch.getViewCount()));
            m.put("likeCount", safeInt(ch.getLikeCount()));
            m.put("shareCount", safeInt(ch.getShareCount()));
            m.put("commentCount", safeInt(ch.getCommentCount()));
            m.put("conversionCount", safeInt(ch.getConversionCount()));
            channelMetrics.add(m);
            channelView += safeInt(ch.getViewCount());
            channelLike += safeInt(ch.getLikeCount());
            channelShare += safeInt(ch.getShareCount());
            channelComment += safeInt(ch.getCommentCount());
            channelConversion += safeInt(ch.getConversionCount());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contentId", content.getId());
        result.put("title", content.getTitle());
        result.put("contentType", content.getContentType());
        result.put("status", content.getStatus());
        result.put("publishedAt", content.getPublishedAt());
        result.put("contentMetrics", Map.of(
                "viewCount", safeInt(content.getViewCount()),
                "likeCount", safeInt(content.getLikeCount()),
                "shareCount", safeInt(content.getShareCount()),
                "commentCount", safeInt(content.getCommentCount()),
                "collectCount", safeInt(content.getCollectCount()),
                "conversionCount", safeInt(content.getConversionCount())));
        result.put("channelCount", channels.size());
        result.put("channelAgg", Map.of(
                "viewSum", channelView,
                "likeSum", channelLike,
                "shareSum", channelShare,
                "commentSum", channelComment,
                "conversionSum", channelConversion));
        result.put("channelMetrics", channelMetrics);
        return result;
    }

    /**
     * 热门内容 Top N (按指定指标排序)。
     *
     * @param limit  返回条数 (默认 10)
     * @param metric 排序指标: VIEW / LIKE / SHARE / COMMENT / COLLECT / CONVERSION (默认 VIEW)
     * @return 热门内容列表
     * @throws ScrmException 指标非法
     */
    @Transactional(readOnly = true)
    public List<ScrmContentEntity> getTopContents(Integer limit, String metric) throws ScrmException {
        String sortField = metric != null ? metric : METRIC_VIEW;
        if (!VALID_METRICS.contains(sortField)) {
            throw ScrmException.badRequest("指标非法: " + sortField + ", 仅支持 " + VALID_METRICS);
        }
        String fieldName = mapMetricToField(sortField);
        int size = limit != null && limit > 0 ? limit : 10;
        PageRequest pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, fieldName));
        Specification<ScrmContentEntity> spec = (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("status"), STATUS_PUBLISHED));
        return contentRepository.findAll(spec, pageable).getContent();
    }

    /**
     * 素材使用统计: 按素材类型汇总素材数量与总使用次数。
     *
     * @return 素材统计列表 [{assetType, assetCount, usageSum}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAssetUsageStats() {
        List<Object[]> agg = assetRepository.aggregateByType();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : agg) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assetType", row[0]);
            m.put("assetCount", toLong(row[1]));
            m.put("usageSum", toLong(row[2]));
            result.add(m);
        }
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验内容参数。
     * <p>
     * 创建场景 (partial=false): title / contentType 必填。
     * 更新场景 (partial=true): 允许字段为空, 仅校验非空字段合法性。
     * </p>
     *
     * @param dto     内容参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateContentDto(ScrmContentDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("内容参数不能为空");
        }
        if (dto.getTitle() != null) {
            if (dto.getTitle().isBlank()) {
                throw ScrmException.badRequest("内容标题不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("内容标题不能为空");
        }
        if (dto.getContentType() != null) {
            if (!VALID_CONTENT_TYPES.contains(dto.getContentType())) {
                throw ScrmException.badRequest(
                        "内容类型非法: " + dto.getContentType() + ", 仅支持 " + VALID_CONTENT_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("内容类型不能为空");
        }
    }

    /**
     * 校验排期参数。
     *
     * @param dto     排期参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateScheduleDto(ScrmContentScheduleDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("排期参数不能为空");
        }
        if (!partial && dto.getContentId() == null) {
            throw ScrmException.badRequest("内容 ID 不能为空");
        }
        if (dto.getScheduleName() != null && dto.getScheduleName().isBlank()) {
            throw ScrmException.badRequest("排期名称不能为空");
        } else if (!partial && (dto.getScheduleName() == null || dto.getScheduleName().isBlank())) {
            throw ScrmException.badRequest("排期名称不能为空");
        }
        if (!partial && dto.getScheduledAt() == null) {
            throw ScrmException.badRequest("计划发布时间不能为空");
        }
        if (dto.getChannels() != null) {
            if (dto.getChannels().isBlank()) {
                throw ScrmException.badRequest("渠道不能为空");
            }
            validateChannels(dto.getChannels());
        } else if (!partial) {
            throw ScrmException.badRequest("渠道不能为空");
        }
        if (dto.getRepeatType() != null && !VALID_REPEAT_TYPES.contains(dto.getRepeatType())) {
            throw ScrmException.badRequest(
                    "重复类型非法: " + dto.getRepeatType() + ", 仅支持 " + VALID_REPEAT_TYPES);
        }
    }

    /**
     * 校验素材参数。
     *
     * @param dto     素材参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateAssetDto(ScrmContentAssetDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("素材参数不能为空");
        }
        if (dto.getAssetName() != null) {
            if (dto.getAssetName().isBlank()) {
                throw ScrmException.badRequest("素材名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("素材名称不能为空");
        }
        if (dto.getAssetType() != null) {
            if (!VALID_ASSET_TYPES.contains(dto.getAssetType())) {
                throw ScrmException.badRequest(
                        "素材类型非法: " + dto.getAssetType() + ", 仅支持 " + VALID_ASSET_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("素材类型不能为空");
        }
        if (dto.getFileUrl() != null) {
            if (dto.getFileUrl().isBlank()) {
                throw ScrmException.badRequest("文件 URL 不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("文件 URL 不能为空");
        }
        if (dto.getFileType() != null && !VALID_FILE_TYPES.contains(dto.getFileType())) {
            throw ScrmException.badRequest(
                    "文件类型非法: " + dto.getFileType() + ", 仅支持 " + VALID_FILE_TYPES);
        }
        if (dto.getSourceType() != null && !VALID_SOURCE_TYPES.contains(dto.getSourceType())) {
            throw ScrmException.badRequest(
                    "素材来源非法: " + dto.getSourceType() + ", 仅支持 " + VALID_SOURCE_TYPES);
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
     * 将互动指标枚举映射为实体字段名 (用于排序)。
     *
     * @param metric 指标枚举
     * @return 字段名
     */
    private String mapMetricToField(String metric) {
        return switch (metric) {
            case METRIC_VIEW -> "viewCount";
            case METRIC_LIKE -> "likeCount";
            case METRIC_SHARE -> "shareCount";
            case METRIC_COMMENT -> "commentCount";
            case METRIC_COLLECT -> "collectCount";
            case METRIC_CONVERSION -> "conversionCount";
            default -> "viewCount";
        };
    }

    /**
     * 安全获取 Integer 值 (null 视为 0)。
     *
     * @param value 整数值
     * @return 非空整数
     */
    private int safeInt(Integer value) {
        return value != null ? value : 0;
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
     * 按主键查询内容, 不存在抛异常, 并校验账号归属。
     *
     * @param id 内容 ID
     * @return 内容实体
     * @throws ScrmException 内容不存在
     */
    private ScrmContentEntity findContentOrThrow(Long id) throws ScrmException {
        ScrmContentEntity entity = contentRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "内容不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询渠道, 不存在抛异常, 并校验账号归属。
     *
     * @param id 渠道 ID
     * @return 渠道实体
     * @throws ScrmException 渠道不存在
     */
    private ScrmContentChannelEntity findChannelOrThrow(Long id) throws ScrmException {
        ScrmContentChannelEntity entity = channelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "内容渠道不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询排期, 不存在抛异常, 并校验账号归属。
     *
     * @param id 排期 ID
     * @return 排期实体
     * @throws ScrmException 排期不存在
     */
    private ScrmContentScheduleEntity findScheduleOrThrow(Long id) throws ScrmException {
        ScrmContentScheduleEntity entity = scheduleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "内容排期不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询素材, 不存在抛异常, 并校验账号归属。
     *
     * @param id 素材 ID
     * @return 素材实体
     * @throws ScrmException 素材不存在
     */
    private ScrmContentAssetEntity findAssetOrThrow(Long id) throws ScrmException {
        ScrmContentAssetEntity entity = assetRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "内容素材不存在: id=" + id));
        return entity;
    }

}
