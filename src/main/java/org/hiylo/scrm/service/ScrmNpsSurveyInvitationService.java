/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNpsSurveyInvitationService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmSurveyDistributeDto;
import org.hiylo.scrm.dto.ScrmSurveyInvitationDto;
import org.hiylo.scrm.entity.ScrmSurveyEntity;
import org.hiylo.scrm.entity.ScrmSurveyInvitationEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmSurveyInvitationRepository;
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
import java.util.UUID;

/**
 * SCRM 客户满意度 NPS 调查 - 邀请管理子域服务。
 * <p>
 * 承载调查邀请管理能力: 邀请创建 / 批量分发 / 发送 / 提醒 / 过期 / 取消 (均为模拟实现,
 * 仅更新状态与时间戳, 不实际触发短信 / 邮件 / 推送系统)。共享问卷管理子域的
 * {@link ScrmNpsSurveyManageService#findSurveyOrThrow} 与
 * {@link ScrmNpsSurveyManageService#findInvitationByCodeOrThrow}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmNpsSurveyInvitationService {

    /** 问卷状态: 进行中 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /** 邀请状态: 待发送 */
    private static final String INVITATION_PENDING = "PENDING";
    /** 邀请状态: 已发送 */
    private static final String INVITATION_SENT = "SENT";
    /** 邀请状态: 已打开 */
    private static final String INVITATION_OPENED = "OPENED";
    /** 邀请状态: 进行中 */
    private static final String INVITATION_IN_PROGRESS = "IN_PROGRESS";
    /** 邀请状态: 已完成 */
    private static final String INVITATION_COMPLETED = "COMPLETED";
    /** 邀请状态: 已过期 */
    private static final String INVITATION_EXPIRED = "EXPIRED";

    /** 合法的邀请状态 */
    private static final List<String> VALID_INVITATION_STATUS = List.of(
            INVITATION_PENDING, INVITATION_SENT, INVITATION_OPENED, INVITATION_IN_PROGRESS,
            INVITATION_COMPLETED, INVITATION_EXPIRED, "BOUNCED");

    /** 合法的渠道 */
    private static final List<String> VALID_CHANNELS = List.of("IN_APP", "SMS", "EMAIL", "WECHAT");

    /** 调查邀请数据访问层 */
    private final ScrmSurveyInvitationRepository invitationRepository;

    /** 问卷管理子域服务 (共享问卷与邀请查询) */
    private final ScrmNpsSurveyManageService manageService;

    /**
     * 创建调查邀请 (不发送, 状态 PENDING)。
     * <p>customerId 必填; invitationCode 由系统生成; 同问卷同客户已存在未完成邀请则拒绝重复创建。</p>
     *
     * @param dto 邀请参数
     * @return 创建后的邀请
     * @throws ScrmException 参数非法 / 问卷不存在 / 重复创建
     */
    @Transactional
    public ScrmSurveyInvitationEntity createInvitation(ScrmSurveyInvitationDto dto) throws ScrmException {
        validateInvitationDto(dto, false);
        ScrmSurveyEntity survey = manageService.findSurveyOrThrow(dto.getSurveyId());
        if (!STATUS_ACTIVE.equals(survey.getStatus())) {
            throw ScrmException.conflict("问卷未激活, 不允许创建邀请: surveyId=" + survey.getId()
                    + ", status=" + survey.getStatus());
        }
        List<ScrmSurveyInvitationEntity> existing = invitationRepository
                .findBySurveyIdAndCustomerId(dto.getSurveyId(), dto.getCustomerId());
        for (ScrmSurveyInvitationEntity inv : existing) {
            if (!INVITATION_COMPLETED.equals(inv.getStatus()) && !INVITATION_EXPIRED.equals(inv.getStatus())) {
                throw ScrmException.conflict("客户已存在未完成邀请, 不允许重复创建: customerId="
                        + dto.getCustomerId() + ", invitationId=" + inv.getId());
            }
        }
        ScrmSurveyInvitationEntity entity = new ScrmSurveyInvitationEntity();
        entity.setSurveyId(dto.getSurveyId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setChannel(dto.getChannel());
        entity.setContactInfo(dto.getContactInfo());
        entity.setInvitationCode(generateInvitationCode());
        entity.setStatus(INVITATION_PENDING);
        entity.setReminderCount(0);
        entity.setSourceEvent(dto.getSourceEvent());
        entity.setSourceId(dto.getSourceId());
        if (dto.getExpiredAt() != null) {
            entity.setExpiredAt(dto.getExpiredAt());
        }
        entity = invitationRepository.save(entity);
        log.info("创建调查邀请: id={}, surveyId={}, customerId={}, code={}",
                entity.getId(), entity.getSurveyId(), entity.getCustomerId(), entity.getInvitationCode());
        return entity;
    }

    /**
     * 批量创建邀请并模拟发送。
     * <p>遍历 customerIds 逐一生成邀请 (邀请码唯一), 创建后立即标记 SENT (模拟发送)。
     * 单个客户失败跳过不阻断其他客户。返回 {total, success, failed, invitations}。</p>
     * <p>模拟实现: 不实际触发短信 / 邮件 / 推送系统, 待对接。</p>
     *
     * @param distributeDto 分发参数 (surveyId + customerIds + channel)
     * @return 分发结果
     * @throws ScrmException 问卷不存在 / 问卷未激活
     */
    @Transactional
    public Map<String, Object> batchCreateInvitations(ScrmSurveyDistributeDto distributeDto) throws ScrmException {
        if (distributeDto == null) {
            throw ScrmException.badRequest("分发参数不能为空");
        }
        if (distributeDto.getCustomerIds() == null || distributeDto.getCustomerIds().isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        ScrmSurveyEntity survey = manageService.findSurveyOrThrow(distributeDto.getSurveyId());
        if (!STATUS_ACTIVE.equals(survey.getStatus())) {
            throw ScrmException.conflict("问卷未激活, 不允许分发: surveyId=" + survey.getId()
                    + ", status=" + survey.getStatus());
        }
        if (!VALID_CHANNELS.contains(distributeDto.getChannel())) {
            throw ScrmException.badRequest(
                    "分发渠道非法: " + distributeDto.getChannel() + ", 仅支持 " + VALID_CHANNELS);
        }
        int success = 0;
        int failed = 0;
        List<ScrmSurveyInvitationEntity> invitations = new ArrayList<>();
        for (Long customerId : distributeDto.getCustomerIds()) {
            if (customerId == null) {
                continue;
            }
            try {
                ScrmSurveyInvitationEntity entity = new ScrmSurveyInvitationEntity();
                entity.setSurveyId(distributeDto.getSurveyId());
                entity.setCustomerId(customerId);
                entity.setChannel(distributeDto.getChannel());
                entity.setInvitationCode(generateInvitationCode());
                entity.setStatus(INVITATION_SENT);
                entity.setSentAt(LocalDateTime.now());
                entity.setReminderCount(0);
                entity.setSourceEvent(distributeDto.getSourceEvent());
                entity.setSourceId(distributeDto.getSourceId());
                entity = invitationRepository.save(entity);
                invitations.add(entity);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("批量创建邀请失败, 跳过: surveyId={}, customerId={}, err={}",
                        distributeDto.getSurveyId(), customerId, e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", distributeDto.getCustomerIds().size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("invitations", invitations);
        log.info("批量分发调查邀请: surveyId={}, total={}, success={}, failed={}",
                distributeDto.getSurveyId(), distributeDto.getCustomerIds().size(), success, failed);
        return result;
    }

    /**
     * 查询邀请详情。
     *
     * @param id 邀请 ID
     * @return 邀请实体
     * @throws ScrmException 邀请不存在
     */
    @Transactional(readOnly = true)
    public ScrmSurveyInvitationEntity getInvitation(Long id) throws ScrmException {
        return findInvitationOrThrow(id);
    }

    /**
     * 按邀请码查询邀请。
     *
     * @param code 邀请码
     * @return 邀请实体
     * @throws ScrmException 邀请不存在
     */
    @Transactional(readOnly = true)
    public ScrmSurveyInvitationEntity getInvitationByCode(String code) throws ScrmException {
        return manageService.findInvitationByCodeOrThrow(code);
    }

    /**
     * 分页查询邀请, 支持按问卷、状态、渠道与时间范围过滤。
     *
     * @param surveyId  问卷 ID 过滤（可空）
     * @param status    状态过滤（可空）
     * @param channel   渠道过滤（可空）
     * @param startTime 发送时间起始 (含, 可空)
     * @param endTime   发送时间截止 (含, 可空)
     * @param pageable  分页参数
     * @return 邀请分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmSurveyInvitationEntity> listInvitations(Long surveyId, String status, String channel,
                                                              LocalDateTime startTime, LocalDateTime endTime,
                                                              Pageable pageable) {
        Specification<ScrmSurveyInvitationEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (surveyId != null) {
                predicates.add(cb.equal(root.get("surveyId"), surveyId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sentAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sentAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return invitationRepository.findAll(spec, pageable);
    }

    /**
     * 发送邀请 (模拟实现)。
     * <p>PENDING → SENT, 记录 sentAt。模拟实现: 不实际触发短信 / 邮件 / 推送系统, 待对接。</p>
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    @Transactional
    public ScrmSurveyInvitationEntity sendInvitation(Long id) throws ScrmException {
        ScrmSurveyInvitationEntity entity = findInvitationOrThrow(id);
        if (!INVITATION_PENDING.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "邀请状态不允许发送: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(INVITATION_SENT);
        entity.setSentAt(LocalDateTime.now());
        entity = invitationRepository.save(entity);
        // 模拟实现: 待对接短信 / 邮件 / 推送系统
        log.info("发送调查邀请 (模拟): id={}, code={}, channel={}", id,
                entity.getInvitationCode(), entity.getChannel());
        return entity;
    }

    /**
     * 发送提醒 (模拟实现)。
     * <p>仅 SENT / OPENED / IN_PROGRESS 状态可提醒, 增量 reminderCount 并记录 lastReminderAt。
     * 模拟实现: 不实际触发短信 / 邮件 / 推送系统, 待对接。</p>
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    @Transactional
    public ScrmSurveyInvitationEntity sendReminder(Long id) throws ScrmException {
        ScrmSurveyInvitationEntity entity = findInvitationOrThrow(id);
        if (!INVITATION_SENT.equals(entity.getStatus()) && !INVITATION_OPENED.equals(entity.getStatus()) && !INVITATION_IN_PROGRESS.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "邀请状态不允许提醒: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setReminderCount((entity.getReminderCount() != null ? entity.getReminderCount() : 0) + 1);
        entity.setLastReminderAt(LocalDateTime.now());
        entity = invitationRepository.save(entity);
        // 模拟实现: 待对接短信 / 邮件 / 推送系统
        log.info("发送调查邀请提醒 (模拟): id={}, code={}, reminderCount={}",
                id, entity.getInvitationCode(), entity.getReminderCount());
        return entity;
    }

    /**
     * 过期邀请 (PENDING/SENT/OPENED/IN_PROGRESS → EXPIRED)。
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    @Transactional
    public ScrmSurveyInvitationEntity expireInvitation(Long id) throws ScrmException {
        ScrmSurveyInvitationEntity entity = findInvitationOrThrow(id);
        if (INVITATION_COMPLETED.equals(entity.getStatus()) || INVITATION_EXPIRED.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "邀请状态不允许过期: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(INVITATION_EXPIRED);
        entity.setExpiredAt(LocalDateTime.now());
        entity = invitationRepository.save(entity);
        log.info("过期调查邀请: id={}, code={}", id, entity.getInvitationCode());
        return entity;
    }

    /**
     * 取消邀请 (PENDING/SENT/OPENED/IN_PROGRESS → EXPIRED, 标记为过期)。
     * <p>邀请状态机无 CANCELLED 状态, 取消视为手动过期。</p>
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    @Transactional
    public ScrmSurveyInvitationEntity cancelInvitation(Long id) throws ScrmException {
        ScrmSurveyInvitationEntity entity = findInvitationOrThrow(id);
        if (INVITATION_COMPLETED.equals(entity.getStatus()) || INVITATION_EXPIRED.equals(entity.getStatus())) {
            throw ScrmException.conflict(
                    "邀请状态不允许取消: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(INVITATION_EXPIRED);
        entity.setExpiredAt(LocalDateTime.now());
        entity = invitationRepository.save(entity);
        log.info("取消调查邀请: id={}, code={}", id, entity.getInvitationCode());
        return entity;
    }

    /**
     * 校验邀请参数。
     *
     * @param dto     邀请参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateInvitationDto(ScrmSurveyInvitationDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("邀请参数不能为空");
        }
        if (dto.getSurveyId() == null && !partial) {
            throw ScrmException.badRequest("调查问卷 ID 不能为空");
        }
        if (dto.getCustomerId() == null && !partial) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getChannel() != null && !VALID_CHANNELS.contains(dto.getChannel())) {
            throw ScrmException.badRequest(
                    "分发渠道非法: " + dto.getChannel() + ", 仅支持 " + VALID_CHANNELS);
        } else if (dto.getChannel() == null && !partial) {
            throw ScrmException.badRequest("分发渠道不能为空");
        }
        if (dto.getStatus() != null && !VALID_INVITATION_STATUS.contains(dto.getStatus())) {
            throw ScrmException.badRequest(
                    "邀请状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_INVITATION_STATUS);
        }
    }

    /**
     * 生成唯一邀请码 (UUID 去横线)。
     *
     * @return 邀请码
     */
    private String generateInvitationCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 按主键查询邀请, 不存在抛异常, 并校验归属账号。
     *
     * @param id 邀请 ID
     * @return 邀请实体
     * @throws ScrmException 邀请不存在
     */
    private ScrmSurveyInvitationEntity findInvitationOrThrow(Long id) throws ScrmException {
        ScrmSurveyInvitationEntity entity = invitationRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "调查邀请不存在: id=" + id));
        return entity;
    }
}