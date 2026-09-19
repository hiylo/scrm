/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateCenterReviewService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmTemplateReviewDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateCenterEntity;
import org.hiylo.scrm.entity.ScrmMessageTemplateVersionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmMessageTemplateCenterRepository;
import org.hiylo.scrm.repository.ScrmMessageTemplateVersionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 消息模板中心审批子域服务。
 * <p>
 * 承载模板的提交审核 / 审核 / 批量审核 / 待审核查询与审核历史查询,
 * 模板实体查询复用 {@link ScrmMessageTemplateCenterTemplateService} 的 {@code findTemplateOrThrow}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMessageTemplateCenterReviewService {

    /** 模板定义数据访问层 */
    private final ScrmMessageTemplateCenterRepository centerRepository;

    /** 模板版本数据访问层 (审核历史查询) */
    private final ScrmMessageTemplateVersionRepository versionRepository;

    /** 模板管理子域服务 (查询模板实体) */
    private final ScrmMessageTemplateCenterTemplateService templateService;

    /**
     * 提交审核: 将模板置为待审核状态。
     *
     * @param templateId 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmMessageTemplateCenterEntity submitForReview(Long templateId) throws ScrmException {
        ScrmMessageTemplateCenterEntity entity = templateService.findTemplateOrThrow(templateId);
        entity.setStatus("PENDING_REVIEW");
        entity.setReviewStatus("PENDING");
        entity.setReviewedBy(null);
        entity.setReviewedAt(null);
        entity.setReviewComment(null);
        entity = centerRepository.save(entity);
        log.info("提交模板审核: id={}", templateId);
        return entity;
    }

    /**
     * 审核模板: 通过则状态 APPROVED, 驳回则状态 REJECTED。
     *
     * @param reviewDto 审核参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 审核动作非法
     */
    @Transactional
    public ScrmMessageTemplateCenterEntity review(ScrmTemplateReviewDto reviewDto) throws ScrmException {
        if (reviewDto == null || reviewDto.getTemplateId() == null) {
            throw ScrmException.badRequest("审核参数不能为空");
        }
        ScrmMessageTemplateCenterEntity entity = templateService.findTemplateOrThrow(reviewDto.getTemplateId());
        String action = reviewDto.getAction();
        if ("APPROVE".equalsIgnoreCase(action)) {
            entity.setReviewStatus("APPROVED");
            entity.setStatus("APPROVED");
        } else if ("REJECT".equalsIgnoreCase(action)) {
            entity.setReviewStatus("REJECTED");
            entity.setStatus("REJECTED");
        } else {
            throw ScrmException.badRequest("审核动作非法, 仅支持 APPROVE/REJECT: action=" + action);
        }
        entity.setReviewedBy(reviewDto.getReviewer());
        entity.setReviewedAt(LocalDateTime.now());
        entity.setReviewComment(reviewDto.getComment());
        entity = centerRepository.save(entity);
        log.info("审核模板: id={}, action={}", reviewDto.getTemplateId(), action);
        return entity;
    }

    /**
     * 批量审核模板。
     *
     * @param templateIds 模板 ID 列表
     * @param action      审核动作 (APPROVE/REJECT)
     * @param comment     审核意见
     * @return 审核结果列表 (每条含 templateId/success/error)
     */
    @Transactional
    public List<Map<String, Object>> batchReview(List<Long> templateIds, String action, String comment) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (templateIds == null) {
            return results;
        }
        for (Long templateId : templateIds) {
            ScrmTemplateReviewDto dto = new ScrmTemplateReviewDto();
            dto.setTemplateId(templateId);
            dto.setAction(action);
            dto.setComment(comment);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("templateId", templateId);
            try {
                review(dto);
                item.put("success", true);
            } catch (ScrmException e) {
                item.put("success", false);
                item.put("error", e.getMessage());
            }
            results.add(item);
        }
        return results;
    }

    /**
     * 分页查询待审核模板列表。
     *
     * @param pageable 分页参数
     * @return 待审核模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageTemplateCenterEntity> getPendingReviews(Pageable pageable) {
        return centerRepository.findByReviewStatus("PENDING", pageable);
    }

    /**
     * 查询模板审核历史: 返回该模板下已审批的版本记录 (approvedBy 非空), 按审批时间降序。
     *
     * @param templateId 模板 ID
     * @param pageable   分页参数
     * @return 审核历史分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMessageTemplateVersionEntity> getReviewHistory(Long templateId, Pageable pageable) {
        Specification<ScrmMessageTemplateVersionEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("templateId"), templateId));
            predicates.add(cb.isNotNull(root.get("approvedBy")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return versionRepository.findAll(spec, pageable);
    }
}