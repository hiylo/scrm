/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackCommentService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmFeedbackCommentDto;
import org.hiylo.scrm.entity.ScrmFeedbackCommentEntity;
import org.hiylo.scrm.entity.ScrmFeedbackEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmFeedbackCommentRepository;
import org.hiylo.scrm.repository.ScrmFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户反馈评论服务: 添加评论、评论列表查询、内部备注、评论回复 (坐席回复)、删除评论,
 * 评论后自动刷新反馈的 commentCount, 评论类型包含 CUSTOMER / AGENT / INTERNAL / SYSTEM。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmFeedbackCommentService {

    // ==================== 评论类型 ====================
    /** 评论类型: 客户 */
    private static final String COMMENT_TYPE_CUSTOMER = "CUSTOMER";
    /** 评论类型: 坐席 */
    private static final String COMMENT_TYPE_AGENT = "AGENT";
    /** 评论类型: 内部备注 */
    private static final String COMMENT_TYPE_INTERNAL = "INTERNAL";
    /** 评论类型: 系统 */
    private static final String COMMENT_TYPE_SYSTEM = "SYSTEM";

    /** 合法评论类型集合 */
    private static final Set<String> VALID_COMMENT_TYPES = new HashSet<>(Arrays.asList(
            COMMENT_TYPE_CUSTOMER, COMMENT_TYPE_AGENT, COMMENT_TYPE_INTERNAL, COMMENT_TYPE_SYSTEM));

    /** 反馈评论数据访问层 */
    private final ScrmFeedbackCommentRepository commentRepository;
    /** 反馈数据访问层 (刷新评论计数) */
    private final ScrmFeedbackRepository feedbackRepository;
    /** 反馈管理服务 (反馈存在性校验与终态校验) */
    private final ScrmFeedbackManagementService managementService;

    // ============================================================
    // 评论
    // ============================================================

    /**
     * 添加评论。
     * <p>校验评论类型合法, isInternal 缺省按评论类型推断 (INTERNAL 默认 true, 其他默认 false),
     * 评论后刷新反馈的 commentCount。</p>
     *
     * @param dto 评论参数
     * @return 创建后的评论
     * @throws ScrmException 反馈不存在 / 参数非法 / 反馈已关闭
     */
    @Transactional
    public ScrmFeedbackCommentDto addComment(ScrmFeedbackCommentDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("评论参数不能为空");
        }
        if (dto.getFeedbackId() == null) {
            throw ScrmException.badRequest("反馈 ID 不能为空");
        }
        if (dto.getCommentType() == null || dto.getCommentType().isBlank()) {
            throw ScrmException.badRequest("评论类型不能为空");
        }
        validateCommentType(dto.getCommentType());
        if (dto.getAuthorId() == null || dto.getAuthorId().isBlank()) {
            throw ScrmException.badRequest("作者 ID 不能为空");
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw ScrmException.badRequest("评论内容不能为空");
        }
        ScrmFeedbackEntity feedback = managementService.findFeedbackOrThrow(dto.getFeedbackId());
        managementService.ensureNotTerminal(feedback);
        LocalDateTime now = LocalDateTime.now();
        ScrmFeedbackCommentEntity entity = new ScrmFeedbackCommentEntity();
        entity.setFeedbackId(feedback.getId());
        entity.setCommentType(dto.getCommentType());
        entity.setAuthorId(dto.getAuthorId());
        entity.setAuthorName(dto.getAuthorName());
        entity.setAuthorRole(dto.getAuthorRole());
        entity.setContent(dto.getContent());
        entity.setAttachments(dto.getAttachments());
        // isInternal 缺省: INTERNAL 类型默认 true, 其他默认 false
        entity.setIsInternal(dto.getIsInternal() != null ? dto.getIsInternal()
                : COMMENT_TYPE_INTERNAL.equals(dto.getCommentType()));
        entity.setUpvoteCount(0);
        entity.setParentCommentId(dto.getParentCommentId());
        entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : now);
        entity = commentRepository.save(entity);
        // 刷新反馈评论计数
        refreshCommentCount(feedback.getId());
        log.info("添加反馈评论: feedbackId={}, commentId={}, type={}",
                feedback.getId(), entity.getId(), dto.getCommentType());
        return toCommentDto(entity);
    }

    /**
     * 查询反馈评论列表 (按评论发生时间升序)。
     *
     * @param feedbackId 反馈 ID
     * @return 评论列表
     * @throws ScrmException 反馈不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmFeedbackCommentDto> listComments(Long feedbackId) throws ScrmException {
        managementService.findFeedbackOrThrow(feedbackId);
        return commentRepository
                .findByFeedbackIdOrderByCreatedAtAsc(feedbackId)
                .stream().map(this::toCommentDto).collect(Collectors.toList());
    }

    /**
     * 添加内部备注。
     * <p>评论类型固定 INTERNAL, isInternal=true。</p>
     *
     * @param feedbackId 反馈 ID
     * @param content    备注内容
     * @param authorId   作者 ID
     * @return 创建后的评论
     * @throws ScrmException 反馈不存在 / 参数非法 / 反馈已关闭
     */
    @Transactional
    public ScrmFeedbackCommentDto addInternalNote(Long feedbackId, String content, String authorId)
            throws ScrmException {
        if (content == null || content.isBlank()) {
            throw ScrmException.badRequest("备注内容不能为空");
        }
        if (authorId == null || authorId.isBlank()) {
            throw ScrmException.badRequest("作者 ID 不能为空");
        }
        ScrmFeedbackCommentDto dto = new ScrmFeedbackCommentDto();
        dto.setFeedbackId(feedbackId);
        dto.setCommentType(COMMENT_TYPE_INTERNAL);
        dto.setAuthorId(authorId);
        dto.setContent(content);
        dto.setIsInternal(true);
        return addComment(dto);
    }

    /**
     * 回复评论。
     * <p>在指定反馈下回复指定父评论, 评论类型固定 AGENT (坐席回复), parentCommentId 为父评论 ID。
     * 父评论必须存在且属于该反馈。</p>
     *
     * @param feedbackId      反馈 ID
     * @param parentCommentId 父评论 ID
     * @param content         回复内容
     * @param authorId        作者 ID
     * @return 创建后的评论
     * @throws ScrmException 反馈不存在 / 父评论不存在 / 参数非法
     */
    @Transactional
    public ScrmFeedbackCommentDto replyToComment(Long feedbackId, Long parentCommentId,
                                                  String content, String authorId) throws ScrmException {
        if (parentCommentId == null) {
            throw ScrmException.badRequest("父评论 ID 不能为空");
        }
        if (content == null || content.isBlank()) {
            throw ScrmException.badRequest("回复内容不能为空");
        }
        if (authorId == null || authorId.isBlank()) {
            throw ScrmException.badRequest("作者 ID 不能为空");
        }
        managementService.findFeedbackOrThrow(feedbackId);
        ScrmFeedbackCommentEntity parent = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "父评论不存在: id=" + parentCommentId));

        ScrmFeedbackCommentDto dto = new ScrmFeedbackCommentDto();
        dto.setFeedbackId(feedbackId);
        dto.setCommentType(COMMENT_TYPE_AGENT);
        dto.setAuthorId(authorId);
        dto.setContent(content);
        dto.setIsInternal(false);
        dto.setParentCommentId(parentCommentId);
        return addComment(dto);
    }

    /**
     * 删除评论。
     * <p>删除后刷新反馈的 commentCount。</p>
     *
     * @param id 评论 ID
     * @throws ScrmException 评论不存在
     */
    @Transactional
    public void deleteComment(Long id) throws ScrmException {
        ScrmFeedbackCommentEntity entity = commentRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "评论不存在: id=" + id));

        Long feedbackId = entity.getFeedbackId();
        commentRepository.delete(entity);
        refreshCommentCount(feedbackId);
        log.info("删除反馈评论: id={}, feedbackId={}", id, feedbackId);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验评论类型合法性。
     *
     * @param type 评论类型
     * @throws ScrmException 评论类型非法
     */
    private void validateCommentType(String type) throws ScrmException {
        if (!VALID_COMMENT_TYPES.contains(type)) {
            throw ScrmException.badRequest("评论类型非法: " + type
                    + ", 合法值: CUSTOMER / AGENT / INTERNAL / SYSTEM");
        }
    }

    /**
     * 刷新反馈的评论计数。
     *
     * @param feedbackId 反馈 ID
     */
    private void refreshCommentCount(Long feedbackId) {
        feedbackRepository.findById(feedbackId).ifPresent(f -> {
            long count = commentRepository.countByFeedbackId(feedbackId);
            f.setCommentCount((int) count);
            feedbackRepository.save(f);
        });
    }

    /**
     * 评论实体转 DTO
     *
     * @param entity 评论实体
     * @return 评论 DTO
     */
    private ScrmFeedbackCommentDto toCommentDto(ScrmFeedbackCommentEntity entity) {
        ScrmFeedbackCommentDto dto = new ScrmFeedbackCommentDto();
        dto.setId(entity.getId());
        dto.setFeedbackId(entity.getFeedbackId());
        dto.setCommentType(entity.getCommentType());
        dto.setAuthorId(entity.getAuthorId());
        dto.setAuthorName(entity.getAuthorName());
        dto.setAuthorRole(entity.getAuthorRole());
        dto.setContent(entity.getContent());
        dto.setAttachments(entity.getAttachments());
        dto.setIsInternal(entity.getIsInternal());
        dto.setUpvoteCount(entity.getUpvoteCount());
        dto.setParentCommentId(entity.getParentCommentId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}