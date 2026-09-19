/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSurveyInvitationRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmSurveyInvitationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 调查邀请数据访问层。
 * <p>
 * 提供按邀请码查询邀请 (供客户凭码提交回答), 以及按调查问卷 / 客户分页查询邀请等能力,
 * 供 {@code ScrmNpsSurveyService.submitResponse} / {@code listInvitations} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmSurveyInvitationRepository extends JpaRepository<ScrmSurveyInvitationEntity, Long>,
        JpaSpecificationExecutor<ScrmSurveyInvitationEntity> {

    /**
     * 按邀请码查询邀请 (客户凭码提交回答用)。
     *
     * @param invitationCode 邀请码
     * @return 邀请实体 (不存在返回 empty)
     */
    Optional<ScrmSurveyInvitationEntity> findByInvitationCode(String invitationCode);

    /**
     * 按与调查问卷分页查询邀请 (按创建时间倒序)。
     *
     * @param surveyId 调查问卷 ID
     * @param pageable 分页参数
     * @return 邀请分页结果
     */
    Page<ScrmSurveyInvitationEntity> findBySurveyId(Long surveyId, Pageable pageable);

    /**
     * 按、调查问卷与客户查询全部邀请 (去重 / 校验用)。
     *
     * @param surveyId  调查问卷 ID
     * @param customerId 客户 ID
     * @return 邀请列表
     */
    List<ScrmSurveyInvitationEntity> findBySurveyIdAndCustomerId(Long surveyId, Long customerId);

    /**
     * 按与调查问卷统计邀请总数 (回复率计算用)。
     *
     * @param surveyId 调查问卷 ID
     * @return 邀请总数
     */
    long countBySurveyId(Long surveyId);

    /**
     * 按与调查问卷统计已完成邀请数 (回复率计算用)。
     *
     * @param surveyId 调查问卷 ID
     * @return 已完成邀请数
     */
    long countBySurveyIdAndStatus(Long surveyId, String status);
}
