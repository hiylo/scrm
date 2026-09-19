/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractReminderService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmContractReminderDto;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmContractReminderEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmContractReminderRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SCRM 合同提醒管理服务 (提醒子域)。
 * <p>
 * 承载合同提醒的增删改查 / 发送 (单条与批量) / 取消 / 待发送查询 / 已处理标记, 以及
 * 定时扫描待提醒合同并生成提醒。共享常量与合同查询、提醒生成能力托管在
 * {@link ScrmContractManageService}, 本类通过注入复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmContractReminderService {

    /** 合同提醒数据访问层 */
    private final ScrmContractReminderRepository reminderRepository;

    /** 合同实例数据访问层 (发送提醒时刷新合同最后提醒时间 / 到期扫描) */
    private final ScrmContractRepository contractRepository;

    /** 合同管理服务 (合同查询 / 提醒生成 / 共享常量与转换) */
    private final ScrmContractManageService manageService;

    // ============================================================
    // 提醒管理
    // ============================================================

    /**
     * 创建合同提醒。
     *
     * @param dto 提醒参数
     * @return 创建后的提醒
     * @throws ScrmException 参数非法 / 合同不存在
     */
    @Transactional
    public ScrmContractReminderDto createReminder(ScrmContractReminderDto dto) throws ScrmException {
        validateReminderDto(dto, false);
        manageService.findContractOrThrow(dto.getContractId());
        ScrmContractReminderEntity entity = new ScrmContractReminderEntity();
        entity.setContractId(dto.getContractId());
        entity.setReminderType(dto.getReminderType());
        entity.setReminderDate(dto.getReminderDate());
        entity.setReminderTime(dto.getReminderTime());
        entity.setTitle(dto.getTitle());
        entity.setMessage(dto.getMessage());
        entity.setRecipients(dto.getRecipients());
        entity.setChannels(dto.getChannels());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : ScrmContractManageService.REMINDER_STATUS_PENDING);
        entity.setSentCount(0);
        entity.setFailedCount(0);
        entity.setResponseCount(0);
        entity.setIsRecurring(dto.getIsRecurring() != null ? dto.getIsRecurring() : Boolean.FALSE);
        entity.setRecurringConfig(dto.getRecurringConfig());
        entity.setActionRequired(dto.getActionRequired() != null ? dto.getActionRequired() : ScrmContractManageService.ACTION_NOTIFY);
        entity.setActionUrl(dto.getActionUrl());
        entity.setActionTaken(Boolean.FALSE);
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = reminderRepository.save(entity);
        log.info("创建合同提醒: id={}, contractId={}, type={}", entity.getId(),
                entity.getContractId(), entity.getReminderType());
        return manageService.toReminderDto(entity);
    }

    /**
     * 更新合同提醒 (字段非空才覆盖)。
     *
     * @param id  提醒 ID
     * @param dto 提醒参数
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在 / 参数非法
     */
    @Transactional
    public ScrmContractReminderDto updateReminder(Long id, ScrmContractReminderDto dto) throws ScrmException {
        ScrmContractReminderEntity entity = findReminderOrThrow(id);
        validateReminderDto(dto, true);
        if (dto.getReminderType() != null) entity.setReminderType(dto.getReminderType());
        if (dto.getReminderDate() != null) entity.setReminderDate(dto.getReminderDate());
        if (dto.getReminderTime() != null) entity.setReminderTime(dto.getReminderTime());
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getMessage() != null) entity.setMessage(dto.getMessage());
        if (dto.getRecipients() != null) entity.setRecipients(dto.getRecipients());
        if (dto.getChannels() != null) entity.setChannels(dto.getChannels());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getIsRecurring() != null) entity.setIsRecurring(dto.getIsRecurring());
        if (dto.getRecurringConfig() != null) entity.setRecurringConfig(dto.getRecurringConfig());
        if (dto.getActionRequired() != null) entity.setActionRequired(dto.getActionRequired());
        if (dto.getActionUrl() != null) entity.setActionUrl(dto.getActionUrl());
        if (dto.getActionTaken() != null) entity.setActionTaken(dto.getActionTaken());
        if (dto.getActionTakenBy() != null) entity.setActionTakenBy(dto.getActionTakenBy());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = reminderRepository.save(entity);
        log.info("更新合同提醒: id={}", id);
        return manageService.toReminderDto(entity);
    }

    /**
     * 删除合同提醒。
     *
     * @param id 提醒 ID
     * @throws ScrmException 提醒不存在
     */
    @Transactional
    public void deleteReminder(Long id) throws ScrmException {
        ScrmContractReminderEntity entity = findReminderOrThrow(id);
        reminderRepository.delete(entity);
        log.info("删除合同提醒: id={}", id);
    }

    /**
     * 查询合同提醒详情。
     *
     * @param id 提醒 ID
     * @return 提醒 DTO
     * @throws ScrmException 提醒不存在
     */
    @Transactional(readOnly = true)
    public ScrmContractReminderDto getReminder(Long id) throws ScrmException {
        return manageService.toReminderDto(findReminderOrThrow(id));
    }

    /**
     * 分页查询合同提醒, 支持按合同 ID、提醒类型、状态与日期范围过滤。
     *
     * @param contractId   合同 ID 过滤 (可空)
     * @param reminderType 提醒类型过滤 (可空)
     * @param status       状态过滤 (可空)
     * @param startDate    起始日期 (按提醒日期, 可空)
     * @param endDate      截止日期 (按提醒日期, 可空)
     * @param pageable     分页参数
     * @return 提醒分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractReminderDto> listReminders(Long contractId, String reminderType, String status,
                                                        LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Specification<ScrmContractReminderEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (contractId != null) {
                predicates.add(cb.equal(root.get("contractId"), contractId));
            }
            if (reminderType != null && !reminderType.isBlank()) {
                predicates.add(cb.equal(root.get("reminderType"), reminderType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("reminderDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("reminderDate"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return reminderRepository.findAll(spec, pageable).map(manageService::toReminderDto);
    }

    /**
     * 发送提醒 (模拟): 状态置 SENT, 累加发送次数, 刷新合同最后提醒时间。
     *
     * @param id 提醒 ID
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在 / 状态非法
     */
    @Transactional
    public ScrmContractReminderDto sendReminder(Long id) throws ScrmException {
        ScrmContractReminderEntity entity = findReminderOrThrow(id);
        if (!ScrmContractManageService.REMINDER_STATUS_PENDING.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "提醒状态非法, 仅 PENDING 可发送: currentStatus=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(ScrmContractManageService.REMINDER_STATUS_SENT);
        entity.setSentAt(now);
        entity.setSentCount((entity.getSentCount() != null ? entity.getSentCount() : 0) + 1);
        entity = reminderRepository.save(entity);
        // 刷新合同最后提醒时间
        contractRepository.findById(entity.getContractId()).ifPresent(c -> {
            c.setLastReminderSentAt(now);
            contractRepository.save(c);
        });
        log.info("发送合同提醒: id={}, contractId={}", id, entity.getContractId());
        return manageService.toReminderDto(entity);
    }

    /**
     * 批量发送提醒 (定时任务): 扫描当前账号下 reminderDate 早于今天的 PENDING 提醒并发送。
     *
     * @return 发送的提醒数量
     */
    @Transactional
    public int batchSendReminders() {
        LocalDate today = LocalDate.now();
        List<ScrmContractReminderEntity> pending = reminderRepository
                .findByStatusAndReminderDateLessThanEqual(
                         ScrmContractManageService.REMINDER_STATUS_PENDING, today);
        if (pending.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (ScrmContractReminderEntity entity : pending) {
            entity.setStatus(ScrmContractManageService.REMINDER_STATUS_SENT);
            entity.setSentAt(now);
            entity.setSentCount((entity.getSentCount() != null ? entity.getSentCount() : 0) + 1);
            reminderRepository.save(entity);
        }
        log.info("批量发送合同提醒:, count={}", pending.size());
        return pending.size();
    }

    /**
     * 取消提醒 (状态置 CANCELLED)。
     *
     * @param id 提醒 ID
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在 / 状态非法
     */
    @Transactional
    public ScrmContractReminderDto cancelReminder(Long id) throws ScrmException {
        ScrmContractReminderEntity entity = findReminderOrThrow(id);
        if (!ScrmContractManageService.REMINDER_STATUS_PENDING.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "提醒状态非法, 仅 PENDING 可取消: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(ScrmContractManageService.REMINDER_STATUS_CANCELLED);
        entity = reminderRepository.save(entity);
        log.info("取消合同提醒: id={}", id);
        return manageService.toReminderDto(entity);
    }

    /**
     * 查询待发送提醒 (状态为 PENDING, 按提醒日期升序)。
     *
     * @return 待发送提醒列表
     */
    @Transactional(readOnly = true)
    public List<ScrmContractReminderDto> getPendingReminders() {
        Specification<ScrmContractReminderEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ScrmContractManageService.REMINDER_STATUS_PENDING));
        return reminderRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "reminderDate")).stream()
                .map(manageService::toReminderDto)
                .collect(Collectors.toList());
    }

    /**
     * 检查并生成提醒 (定时任务): 扫描即将到期的合同并生成提醒。
     * <p>扫描当前账号下状态为 ACTIVE/SIGNED 且 endDate 在未来 N 天内的合同,
     * 为尚未生成到期提醒的合同生成提醒。</p>
     *
     * @return 生成提醒的合同数量
     */
    @Transactional
    public int checkAndGenerateReminders() {
        LocalDate threshold = LocalDate.now().plusDays(ScrmContractManageService.DEFAULT_REMINDER_DAYS_BEFORE);
        List<ScrmContractEntity> contracts = contractRepository.findByStatusInAndEndDateLessThanEqual(
                 ScrmContractManageService.ACTIVE_STATUSES, threshold);
        int generated = 0;
        for (ScrmContractEntity contract : contracts) {
            if (contract.getEndDate() == null || contract.getEndDate().isBefore(LocalDate.now())) {
                continue;
            }
            // 检查是否已有到期提醒
            Specification<ScrmContractReminderEntity> spec = (root, query, cb) -> cb.and(
                    cb.equal(root.get("contractId"), contract.getId()),
                    cb.equal(root.get("reminderType"), ScrmContractManageService.REMINDER_TYPE_EXPIRY));
            long existingCount = reminderRepository.count(spec);
            if (existingCount == 0) {
                manageService.generateRemindersForContract(contract.getId());
                generated++;
            }
        }
        log.info("检查并生成合同提醒:, generatedContracts={}", generated);
        return generated;
    }

    /**
     * 标记提醒已处理。
     *
     * @param id        提醒 ID
     * @param actionBy 处理人
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在
     */
    @Transactional
    public ScrmContractReminderDto markActionTaken(Long id, String actionBy) throws ScrmException {
        ScrmContractReminderEntity entity = findReminderOrThrow(id);
        entity.setActionTaken(Boolean.TRUE);
        entity.setActionTakenAt(LocalDateTime.now());
        entity.setActionTakenBy(actionBy);
        entity = reminderRepository.save(entity);
        log.info("标记合同提醒已处理: id={}, actionBy={}", id, actionBy);
        return manageService.toReminderDto(entity);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验提醒参数。
     *
     * @param dto     提醒参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateReminderDto(ScrmContractReminderDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("提醒参数不能为空");
        }
        if (!partial) {
            if (dto.getContractId() == null) {
                throw ScrmException.badRequest("合同 ID 不能为空");
            }
            if (dto.getReminderType() == null || dto.getReminderType().isBlank()) {
                throw ScrmException.badRequest("提醒类型不能为空");
            }
            if (dto.getReminderDate() == null) {
                throw ScrmException.badRequest("提醒日期不能为空");
            }
            if (dto.getTitle() == null || dto.getTitle().isBlank()) {
                throw ScrmException.badRequest("提醒标题不能为空");
            }
        }
    }

    /**
     * 按主键查询提醒, 不存在抛异常
     */
    private ScrmContractReminderEntity findReminderOrThrow(Long id) throws ScrmException {
        ScrmContractReminderEntity entity = reminderRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合同提醒不存在: id=" + id));

        return entity;
    }
}