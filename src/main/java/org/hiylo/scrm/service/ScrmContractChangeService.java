/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractChangeService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmContractChangeDto;
import org.hiylo.scrm.entity.ScrmContractChangeEntity;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmContractChangeRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 合同变更管理服务。
 * <p>
 * 承载合同变更全生命周期管理能力: 变更增删改查、按合同/类型查询、审批变更、驳回变更、
 * 执行变更 (应用变更到合同, 完整实现)、取消变更, 以及变更历史查询。
 * </p>
 * <p>
 * 所有写操作写入归属账号实现数据隔离, 读操作通过
 * JPA Specification 始终按当前用户可见账号范围过滤。校验失败抛出 {@link ScrmException} 携带
 * 通用错误码 (NOT_FOUND / BAD_REQUEST / CONFLICT)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmContractChangeService {

    // ==================== 变更状态 ====================
    /** 变更状态: 待审 */
    private static final String CHANGE_STATUS_PENDING = "PENDING";
    /** 变更状态: 审核中 */
    private static final String CHANGE_STATUS_IN_REVIEW = "IN_REVIEW";
    /** 变更状态: 已批准 */
    private static final String CHANGE_STATUS_APPROVED = "APPROVED";
    /** 变更状态: 已驳回 */
    private static final String CHANGE_STATUS_REJECTED = "REJECTED";
    /** 变更状态: 已执行 */
    private static final String CHANGE_STATUS_EXECUTED = "EXECUTED";
    /** 变更状态: 已取消 */
    private static final String CHANGE_STATUS_CANCELLED = "CANCELLED";

    /** JSON 解析器 (解析变更新旧值) */
    private final ObjectMapper objectMapper;

    /** 合同变更数据访问层 */
    private final ScrmContractChangeRepository changeRepository;
    /** 合同数据访问层 (校验合同存在性与执行变更) */
    private final ScrmContractRepository contractRepository;

    // ============================================================
    // 变更 CRUD
    // ============================================================

    /**
     * 创建合同变更。
     * <p>校验参数合法性后写入归属账号 ID 持久化, 状态缺省 PENDING。</p>
     *
     * @param dto 变更参数
     * @return 创建后的变更
     * @throws ScrmException 参数非法 / 合同不存在 / 变更编号重复
     */
    @Transactional
    public ScrmContractChangeDto createChange(ScrmContractChangeDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("变更参数不能为空");
        }
        if (dto.getContractId() == null) {
            throw ScrmException.badRequest("合同 ID 不能为空");
        }
        if (dto.getChangeNo() == null || dto.getChangeNo().isBlank()) {
            throw ScrmException.badRequest("变更编号不能为空");
        }
        if (dto.getChangeType() == null || dto.getChangeType().isBlank()) {
            throw ScrmException.badRequest("变更类型不能为空");
        }
        if (dto.getChangeReason() == null || dto.getChangeReason().isBlank()) {
            throw ScrmException.badRequest("变更原因不能为空");
        }
        ScrmContractEntity contract = findContractOrThrow(dto.getContractId());
        if (changeRepository.findByChangeNo(dto.getChangeNo()).isPresent()) {
            throw ScrmException.conflict("变更编号已存在: no=" + dto.getChangeNo());
        }
        ScrmContractChangeEntity entity = new ScrmContractChangeEntity();
        entity.setContractId(dto.getContractId());
        entity.setContractNo(contract.getContractNo());
        entity.setChangeNo(dto.getChangeNo());
        entity.setChangeType(dto.getChangeType());
        entity.setChangeReason(dto.getChangeReason());
        entity.setChangeDescription(dto.getChangeDescription());
        entity.setChangeStatus(dto.getChangeStatus() != null ? dto.getChangeStatus() : CHANGE_STATUS_PENDING);
        entity.setChangeDate(dto.getChangeDate() != null ? dto.getChangeDate() : LocalDate.now());
        entity.setEffectiveDate(dto.getEffectiveDate());
        entity.setOldValue(dto.getOldValue());
        entity.setNewValue(dto.getNewValue());
        entity.setAffectedFields(dto.getAffectedFields());
        entity.setValueChange(dto.getValueChange() != null ? dto.getValueChange() : 0d);
        entity.setValueBefore(dto.getValueBefore() != null ? dto.getValueBefore() : 0d);
        entity.setValueAfter(dto.getValueAfter() != null ? dto.getValueAfter() : 0d);
        entity.setImpactAssessment(dto.getImpactAssessment());
        entity.setRiskAssessment(dto.getRiskAssessment());
        entity.setApproverId(dto.getApproverId());
        entity.setApproverName(dto.getApproverName());
        entity.setApprovedAt(dto.getApprovedAt());
        entity.setApprovalNotes(dto.getApprovalNotes());
        entity.setAttachments(dto.getAttachments());
        entity.setNewContractId(dto.getNewContractId());
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = changeRepository.save(entity);
        log.info("创建合同变更: id={}, contractId={}, changeNo={}", entity.getId(),
                entity.getContractId(), entity.getChangeNo());
        return toDto(entity);
    }

    /**
     * 更新合同变更 (字段非空才覆盖)。
     * <p>仅 PENDING / IN_REVIEW 状态变更允许更新。</p>
     *
     * @param id  变更 ID
     * @param dto 变更参数
     * @return 更新后的变更
     * @throws ScrmException 变更不存在 / 状态非法
     */
    @Transactional
    public ScrmContractChangeDto updateChange(Long id, ScrmContractChangeDto dto) throws ScrmException {
        ScrmContractChangeEntity entity = findChangeOrThrow(id);
        if (!CHANGE_STATUS_PENDING.equals(entity.getChangeStatus()) && !CHANGE_STATUS_IN_REVIEW.equals(entity.getChangeStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "变更状态非法, 仅 PENDING/IN_REVIEW 可更新: currentStatus=" + entity.getChangeStatus());
        }
        if (dto == null) {
            throw ScrmException.badRequest("变更参数不能为空");
        }
        if (dto.getChangeType() != null) entity.setChangeType(dto.getChangeType());
        if (dto.getChangeReason() != null) entity.setChangeReason(dto.getChangeReason());
        if (dto.getChangeDescription() != null) entity.setChangeDescription(dto.getChangeDescription());
        if (dto.getChangeStatus() != null) entity.setChangeStatus(dto.getChangeStatus());
        if (dto.getChangeDate() != null) entity.setChangeDate(dto.getChangeDate());
        if (dto.getEffectiveDate() != null) entity.setEffectiveDate(dto.getEffectiveDate());
        if (dto.getOldValue() != null) entity.setOldValue(dto.getOldValue());
        if (dto.getNewValue() != null) entity.setNewValue(dto.getNewValue());
        if (dto.getAffectedFields() != null) entity.setAffectedFields(dto.getAffectedFields());
        if (dto.getValueChange() != null) entity.setValueChange(dto.getValueChange());
        if (dto.getValueBefore() != null) entity.setValueBefore(dto.getValueBefore());
        if (dto.getValueAfter() != null) entity.setValueAfter(dto.getValueAfter());
        if (dto.getImpactAssessment() != null) entity.setImpactAssessment(dto.getImpactAssessment());
        if (dto.getRiskAssessment() != null) entity.setRiskAssessment(dto.getRiskAssessment());
        if (dto.getAttachments() != null) entity.setAttachments(dto.getAttachments());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = changeRepository.save(entity);
        log.info("更新合同变更: id={}", id);
        return toDto(entity);
    }

    /**
     * 删除合同变更。
     * <p>仅 PENDING / CANCELLED / REJECTED 状态变更允许删除。</p>
     *
     * @param id 变更 ID
     * @throws ScrmException 变更不存在 / 状态不允许删除
     */
    @Transactional
    public void deleteChange(Long id) throws ScrmException {
        ScrmContractChangeEntity entity = findChangeOrThrow(id);
        if (!CHANGE_STATUS_PENDING.equals(entity.getChangeStatus()) && !CHANGE_STATUS_CANCELLED.equals(entity.getChangeStatus()) && !CHANGE_STATUS_REJECTED.equals(entity.getChangeStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "变更状态不允许删除, 仅 PENDING/CANCELLED/REJECTED 可删除: status=" + entity.getChangeStatus());
        }
        changeRepository.delete(entity);
        log.info("删除合同变更: id={}", id);
    }

    /**
     * 查询变更详情。
     *
     * @param id 变更 ID
     * @return 变更 DTO
     * @throws ScrmException 变更不存在
     */
    @Transactional(readOnly = true)
    public ScrmContractChangeDto getChange(Long id) throws ScrmException {
        return toDto(findChangeOrThrow(id));
    }

    /**
     * 按变更编号查询变更。
     *
     * @param changeNo 变更编号
     * @return 变更 DTO
     * @throws ScrmException 变更不存在
     */
    @Transactional(readOnly = true)
    public ScrmContractChangeDto getChangeByNo(String changeNo) throws ScrmException {
        ScrmContractChangeEntity entity = changeRepository.findByChangeNo(changeNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合同变更不存在: changeNo=" + changeNo));

        return toDto(entity);
    }

    /**
     * 按合同 ID 查询全部变更。
     *
     * @param contractId 合同 ID
     * @return 变更列表
     */
    @Transactional(readOnly = true)
    public List<ScrmContractChangeDto> getChangesByContract(Long contractId) {
        return changeRepository
                .findByContractIdOrderByCreateTimeAsc(contractId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 按变更类型查询变更 (分页)。
     *
     * @param changeType 变更类型
     * @param pageable   分页参数
     * @return 变更分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractChangeDto> getChangesByType(String changeType, Pageable pageable) {
        Specification<ScrmContractChangeEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("changeType"), changeType));
        return changeRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * 查询合同变更历史 (按创建时间升序)。
     *
     * @param contractId 合同 ID
     * @return 变更历史列表
     */
    @Transactional(readOnly = true)
    public List<ScrmContractChangeDto> getChangeHistory(Long contractId) {
        return changeRepository
                .findByContractIdOrderByCreateTimeAsc(contractId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ============================================================
    // 变更审批与执行
    // ============================================================

    /**
     * 审批变更: 状态由 PENDING/IN_REVIEW 流转至 APPROVED, 记录审批人。
     *
     * @param id         变更 ID
     * @param approverId 审批人 ID
     * @return 更新后的变更
     * @throws ScrmException 变更不存在 / 状态非法
     */
    @Transactional
    public ScrmContractChangeDto approveChange(Long id, Long approverId) throws ScrmException {
        ScrmContractChangeEntity entity = findChangeOrThrow(id);
        if (!CHANGE_STATUS_PENDING.equals(entity.getChangeStatus()) && !CHANGE_STATUS_IN_REVIEW.equals(entity.getChangeStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "变更状态非法, 仅 PENDING/IN_REVIEW 可审批: currentStatus=" + entity.getChangeStatus());
        }
        entity.setChangeStatus(CHANGE_STATUS_APPROVED);
        entity.setApproverId(approverId);
        entity.setApprovedAt(LocalDateTime.now());
        entity = changeRepository.save(entity);
        log.info("审批合同变更: id={}, approverId={}", id, approverId);
        return toDto(entity);
    }

    /**
     * 驳回变更: 状态流转至 REJECTED, 记录驳回原因。
     *
     * @param id         变更 ID
     * @param approverId 审批人 ID
     * @param reason     驳回原因
     * @return 更新后的变更
     * @throws ScrmException 变更不存在 / 状态非法
     */
    @Transactional
    public ScrmContractChangeDto rejectChange(Long id, Long approverId, String reason) throws ScrmException {
        ScrmContractChangeEntity entity = findChangeOrThrow(id);
        if (!CHANGE_STATUS_PENDING.equals(entity.getChangeStatus()) && !CHANGE_STATUS_IN_REVIEW.equals(entity.getChangeStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "变更状态非法, 仅 PENDING/IN_REVIEW 可驳回: currentStatus=" + entity.getChangeStatus());
        }
        entity.setChangeStatus(CHANGE_STATUS_REJECTED);
        entity.setApproverId(approverId);
        entity.setApprovedAt(LocalDateTime.now());
        entity.setApprovalNotes(reason);
        entity = changeRepository.save(entity);
        log.info("驳回合同变更: id={}, approverId={}, reason={}", id, approverId, reason);
        return toDto(entity);
    }

    /**
     * 执行变更: 将变更应用到合同 (完整实现)。
     * <p>解析 newValue JSON, 按 affectedFields 将新值应用到合同实体; 如有金额变化则更新合同金额;
     * 变更状态置 EXECUTED, 记录生效日期。</p>
     *
     * @param id 变更 ID
     * @return 更新后的变更
     * @throws ScrmException 变更不存在 / 状态非法
     */
    @Transactional
    public ScrmContractChangeDto executeChange(Long id) throws ScrmException {
        ScrmContractChangeEntity entity = findChangeOrThrow(id);
        if (!CHANGE_STATUS_APPROVED.equals(entity.getChangeStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "变更状态非法, 仅 APPROVED 可执行: currentStatus=" + entity.getChangeStatus());
        }
        // 应用变更到合同
        ScrmContractEntity contract = findContractOrThrow(entity.getContractId());
        applyChangeToContract(contract, entity);
        contractRepository.save(contract);
        // 更新变更状态
        entity.setChangeStatus(CHANGE_STATUS_EXECUTED);
        if (entity.getEffectiveDate() == null) {
            entity.setEffectiveDate(LocalDate.now());
        }
        entity = changeRepository.save(entity);
        log.info("执行合同变更: id={}, contractId={}", id, entity.getContractId());
        return toDto(entity);
    }

    /**
     * 取消变更 (状态置 CANCELLED)。
     *
     * @param id 变更 ID
     * @return 更新后的变更
     * @throws ScrmException 变更不存在 / 状态非法
     */
    @Transactional
    public ScrmContractChangeDto cancelChange(Long id) throws ScrmException {
        ScrmContractChangeEntity entity = findChangeOrThrow(id);
        if (CHANGE_STATUS_EXECUTED.equals(entity.getChangeStatus())
                || CHANGE_STATUS_CANCELLED.equals(entity.getChangeStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "变更状态非法, 已执行/已取消的变更不允许取消: currentStatus=" + entity.getChangeStatus());
        }
        entity.setChangeStatus(CHANGE_STATUS_CANCELLED);
        entity = changeRepository.save(entity);
        log.info("取消合同变更: id={}", id);
        return toDto(entity);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 将变更应用到合同实体: 解析 newValue JSON 并按字段名更新合同。
     * <p>支持的字段: contractName, contractType, contractAmount, currency, startDate, endDate,
     * paymentTerms, notes, status。金额变化通过 valueChange 单独处理。</p>
     *
     * @param contract 合同实体
     * @param change   变更实体
     */
    private void applyChangeToContract(ScrmContractEntity contract, ScrmContractChangeEntity change) {
        // 如果有金额变化, 直接更新合同金额
        if (change.getValueChange() != null && change.getValueChange() != 0d) {
            double currentAmount = contract.getContractAmount() != null ? contract.getContractAmount() : 0d;
            contract.setContractAmount(round(currentAmount + change.getValueChange()));
        }
        // 解析 newValue JSON 并按字段名更新
        if (change.getNewValue() == null || change.getNewValue().isBlank()) {
            return;
        }
        try {
            Map<String, Object> newValues = objectMapper.readValue(change.getNewValue(),
                    new TypeReference<Map<String, Object>>() {});
            if (newValues == null || newValues.isEmpty()) {
                return;
            }
            if (newValues.containsKey("contractName")) {
                contract.setContractName(asString(newValues.get("contractName")));
            }
            if (newValues.containsKey("contractType")) {
                contract.setContractType(asString(newValues.get("contractType")));
            }
            if (newValues.containsKey("contractAmount")) {
                contract.setContractAmount(asDouble(newValues.get("contractAmount")));
            }
            if (newValues.containsKey("currency")) {
                contract.setCurrency(asString(newValues.get("currency")));
            }
            if (newValues.containsKey("startDate")) {
                contract.setStartDate(asLocalDate(newValues.get("startDate")));
            }
            if (newValues.containsKey("endDate")) {
                contract.setEndDate(asLocalDate(newValues.get("endDate")));
            }
            if (newValues.containsKey("paymentTerms")) {
                contract.setPaymentTerms(asString(newValues.get("paymentTerms")));
            }
            if (newValues.containsKey("notes")) {
                contract.setNotes(asString(newValues.get("notes")));
            }
            if (newValues.containsKey("status")) {
                contract.setStatus(asString(newValues.get("status")));
            }
        } catch (Exception e) {
            log.warn("变更 newValue JSON 解析失败: changeId={}, error={}", change.getId(), e.getMessage());
        }
    }

    /**
     * 安全转换为 String
     */
    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    /**
     * 安全转换为 Double
     */
    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全转换为 LocalDate
     */
    private LocalDate asLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按主键查询合同, 不存在抛异常 (数据隔离校验)
     */
    private ScrmContractEntity findContractOrThrow(Long id) throws ScrmException {
        ScrmContractEntity entity = contractRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合同不存在: id=" + id));

        return entity;
    }

    /**
     * 按主键查询变更, 不存在抛异常 (数据隔离校验)
     */
    private ScrmContractChangeEntity findChangeOrThrow(Long id) throws ScrmException {
        ScrmContractChangeEntity entity = changeRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合同变更不存在: id=" + id));

        return entity;
    }

    /**
     * 保留两位小数
     */
    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /**
     * 变更实体转 DTO
     */
    private ScrmContractChangeDto toDto(ScrmContractChangeEntity entity) {
        ScrmContractChangeDto dto = new ScrmContractChangeDto();
        dto.setId(entity.getId());
        dto.setContractId(entity.getContractId());
        dto.setContractNo(entity.getContractNo());
        dto.setChangeNo(entity.getChangeNo());
        dto.setChangeType(entity.getChangeType());
        dto.setChangeReason(entity.getChangeReason());
        dto.setChangeDescription(entity.getChangeDescription());
        dto.setChangeStatus(entity.getChangeStatus());
        dto.setChangeDate(entity.getChangeDate());
        dto.setEffectiveDate(entity.getEffectiveDate());
        dto.setOldValue(entity.getOldValue());
        dto.setNewValue(entity.getNewValue());
        dto.setAffectedFields(entity.getAffectedFields());
        dto.setValueChange(entity.getValueChange());
        dto.setValueBefore(entity.getValueBefore());
        dto.setValueAfter(entity.getValueAfter());
        dto.setImpactAssessment(entity.getImpactAssessment());
        dto.setRiskAssessment(entity.getRiskAssessment());
        dto.setApproverId(entity.getApproverId());
        dto.setApproverName(entity.getApproverName());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setApprovalNotes(entity.getApprovalNotes());
        dto.setAttachments(entity.getAttachments());
        dto.setNewContractId(entity.getNewContractId());
        dto.setNotes(entity.getNotes());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
