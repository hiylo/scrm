/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeReviewService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmKnowledgeReviewDto;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
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
import java.util.List;
import java.util.Map;

/**
 * SCRM 知识文章审核服务。
 * <p>
 * 承载知识库审核子域: 提交审核 / 单个与批量审核 (通过或驳回) / 待审核文章分页 / 审核历史。
 * 文章状态单取自 {@link ScrmKnowledgeArticleService}, 审核历史委托给 {@link ScrmKnowledgeVersionService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmKnowledgeReviewService {

    /** 审核动作: 通过 */
    private static final String ACTION_APPROVE = "APPROVE";
    /** 审核动作: 驳回 */
    private static final String ACTION_REJECT = "REJECT";

    /** 知识文章数据访问层 */
    private final ScrmKnowledgeArticleRepository articleRepository;

    /** 知识文章管理服务 (文章查找 / 文章状态与审核状态常量 / 当前操作人) */
    private final ScrmKnowledgeArticleService articleService;

    /** 知识版本管理服务 (审核历史按版本快照查询) */
    private final ScrmKnowledgeVersionService versionService;

    // ============================================================
    // 审核
    // ============================================================

    /**
     * 提交审核 (DRAFT / REJECTED → PENDING_REVIEW)。
     *
     * @param articleId 文章 ID
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    @Transactional
    public ScrmKnowledgeArticleEntity submitForReview(Long articleId) throws ScrmException {
        ScrmKnowledgeArticleEntity entity = articleService.findArticleOrThrow(articleId);
        if (!ScrmKnowledgeArticleService.STATUS_DRAFT.equals(entity.getStatus())
                && !ScrmKnowledgeArticleService.STATUS_REJECTED.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 DRAFT / REJECTED 状态可提交审核: id=" + articleId
                    + ", status=" + entity.getStatus());
        }
        entity.setStatus(ScrmKnowledgeArticleService.STATUS_PENDING_REVIEW);
        entity.setReviewStatus(ScrmKnowledgeArticleService.REVIEW_PENDING);
        entity.setReviewComment(null);
        entity.setReviewedAt(null);
        entity = articleRepository.save(entity);
        log.info("知识文章已提交审核: id={}, title={}", articleId, entity.getTitle());
        return entity;
    }

    /**
     * 审核文章 (PENDING_REVIEW → PUBLISHED / REJECTED)。
     *
     * @param reviewDto 审核参数
     * @return 更新后的文章
     * @throws ScrmException 文章不存在 / 状态非法
     */
    @Transactional
    public ScrmKnowledgeArticleEntity review(ScrmKnowledgeReviewDto reviewDto) throws ScrmException {
        if (reviewDto == null || reviewDto.getArticleId() == null || reviewDto.getAction() == null) {
            throw ScrmException.badRequest("审核参数不能为空且需指定 articleId 与 action");
        }
        ScrmKnowledgeArticleEntity entity = articleService.findArticleOrThrow(reviewDto.getArticleId());
        if (!ScrmKnowledgeArticleService.STATUS_PENDING_REVIEW.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅 PENDING_REVIEW 状态可审核: id=" + reviewDto.getArticleId()
                    + ", status=" + entity.getStatus());
        }
        boolean approved = ACTION_APPROVE.equals(reviewDto.getAction());
        if (!approved && !ACTION_REJECT.equals(reviewDto.getAction())) {
            throw ScrmException.badRequest("审核动作非法: " + reviewDto.getAction());
        }
        if (approved) {
            entity.setStatus(ScrmKnowledgeCategoryService.STATUS_PUBLISHED);
            entity.setReviewStatus(ScrmKnowledgeArticleService.REVIEW_APPROVED);
            entity.setPublishedAt(LocalDateTime.now());
        } else {
            entity.setStatus(ScrmKnowledgeArticleService.STATUS_REJECTED);
            entity.setReviewStatus(ScrmKnowledgeArticleService.REVIEW_REJECTED);
        }
        entity.setReviewedBy(ScrmKnowledgeCategoryService.currentOperator());
        entity.setReviewedAt(LocalDateTime.now());
        entity.setReviewComment(reviewDto.getComment());
        entity = articleRepository.save(entity);
        log.info("审核知识文章: id={}, action={}, reviewer={}",
                entity.getId(), reviewDto.getAction(), entity.getReviewedBy());
        return entity;
    }

    /**
     * 批量审核文章。
     *
     * @param articleIds 文章 ID 列表
     * @param action     审核动作 APPROVE / REJECT
     * @param comment    审核意见
     * @return 各文章审核结果 [{id, success, message}]
     */
    @Transactional
    public List<Map<String, Object>> batchReview(List<Long> articleIds, String action, String comment) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (articleIds == null || articleIds.isEmpty()) {
            return results;
        }
        for (Long articleId : articleIds) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", articleId);
            try {
                ScrmKnowledgeReviewDto dto = new ScrmKnowledgeReviewDto();
                dto.setArticleId(articleId);
                dto.setAction(action);
                dto.setComment(comment);
                // 仅 PENDING_REVIEW 状态会被处理, 其他状态记录跳过
                ScrmKnowledgeArticleEntity entity = articleRepository.findById(articleId).orElse(null);
                if (entity == null) {
                    m.put("success", false);
                    m.put("message", "文章不存在");
                } else if (!ScrmKnowledgeArticleService.STATUS_PENDING_REVIEW.equals(entity.getStatus())) {
                    m.put("success", false);
                    m.put("message", "状态非 PENDING_REVIEW, 跳过: " + entity.getStatus());
                } else {
                    review(dto);
                    m.put("success", true);
                    m.put("message", "审核完成");
                }
            } catch (ScrmException e) {
                m.put("success", false);
                m.put("message", e.getMessage());
            }
            results.add(m);
        }
        return results;
    }

    /**
     * 获取待审核文章 (PENDING_REVIEW, 按 createTime DESC)。
     *
     * @param pageable 分页参数
     * @return 待审核文章分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmKnowledgeArticleEntity> getPendingReviews(Pageable pageable) {
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<ScrmKnowledgeArticleEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ScrmKnowledgeArticleService.STATUS_PENDING_REVIEW));
        return articleRepository.findAll(spec, sortedPageable);
    }

    /**
     * 获取文章的审核历史 (该文章的全部版本快照, 含审核状态)。
     *
     * @param articleId 文章 ID
     * @param pageable  分页参数
     * @return 版本分页结果 (按 versionNumber DESC)
     * @throws ScrmException 文章不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmKnowledgeArticleEntity> getReviewHistory(
            Long articleId, Pageable pageable) throws ScrmException {
        return versionService.getVersions(articleId, pageable);
    }
}