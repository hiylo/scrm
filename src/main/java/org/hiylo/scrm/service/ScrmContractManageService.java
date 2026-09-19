/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractManageService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmContractApproveDto;
import org.hiylo.scrm.dto.ScrmContractCreateDto;
import org.hiylo.scrm.dto.ScrmContractDto;
import org.hiylo.scrm.dto.ScrmContractReminderDto;
import org.hiylo.scrm.dto.ScrmContractRenewDto;
import org.hiylo.scrm.dto.ScrmContractSignDto;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmContractReminderEntity;
import org.hiylo.scrm.entity.ScrmContractTemplateEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmContractReminderRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 客户合同管理服务 (合同管理子域)。
 * <p>
 * 承载合同创建 (基于模板渲染) / 更新 / 删除 / 查询 / 审批 / 签署 / 激活 / 终止 / 取消 / 归档 /
 * 续约 / 复制, 以及到期扫描与提醒生成、合同编号生成。同时托管合同模块共享常量
 * (合同状态 / 提醒类型 / 提醒状态 / 操作类型 / 签署方式 / 审批动作 / 编号生成 / 默认值 / 进行中状态集合)
 * 与按主键查询合同、实体转 DTO 能力, 供提醒 / 统计兄弟类以 package 级访问复用。
 * 模板渲染能力委托给 {@link ScrmContractTemplateService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmContractManageService {

    // ==================== 合同状态 (共享) ====================
    /** 合同状态: 草稿 */
    static final String CONTRACT_STATUS_DRAFT = "DRAFT";
    /** 合同状态: 待审批 */
    static final String CONTRACT_STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    /** 合同状态: 待签署 */
    static final String CONTRACT_STATUS_PENDING_SIGNATURE = "PENDING_SIGNATURE";
    /** 合同状态: 已签署 */
    static final String CONTRACT_STATUS_SIGNED = "SIGNED";
    /** 合同状态: 生效中 */
    static final String CONTRACT_STATUS_ACTIVE = "ACTIVE";
    /** 合同状态: 已到期 */
    static final String CONTRACT_STATUS_EXPIRED = "EXPIRED";
    /** 合同状态: 已终止 */
    static final String CONTRACT_STATUS_TERMINATED = "TERMINATED";
    /** 合同状态: 已取消 */
    static final String CONTRACT_STATUS_CANCELLED = "CANCELLED";
    /** 合同状态: 已归档 */
    static final String CONTRACT_STATUS_ARCHIVED = "ARCHIVED";

    // ==================== 提醒类型 (共享) ====================
    /** 提醒类型: 到期 */
    static final String REMINDER_TYPE_EXPIRY = "EXPIRY";
    /** 提醒类型: 付款 */
    static final String REMINDER_TYPE_PAYMENT = "PAYMENT";
    /** 提醒类型: 续约 */
    static final String REMINDER_TYPE_RENEWAL = "RENEWAL";
    /** 提醒类型: 复评 */
    static final String REMINDER_TYPE_REVIEW = "REVIEW";
    /** 提醒类型: 自定义 */
    static final String REMINDER_TYPE_CUSTOM = "CUSTOM";

    // ==================== 提醒状态 (共享) ====================
    /** 提醒状态: 待发送 */
    static final String REMINDER_STATUS_PENDING = "PENDING";
    /** 提醒状态: 已发送 */
    static final String REMINDER_STATUS_SENT = "SENT";
    /** 提醒状态: 发送失败 */
    static final String REMINDER_STATUS_FAILED = "FAILED";
    /** 提醒状态: 已取消 */
    static final String REMINDER_STATUS_CANCELLED = "CANCELLED";

    // ==================== 提醒操作类型 (共享) ====================
    /** 提醒操作: 通知 */
    static final String ACTION_NOTIFY = "NOTIFY";
    /** 提醒操作: 续约 */
    static final String ACTION_RENEW = "RENEW";
    /** 提醒操作: 复评 */
    static final String ACTION_REVIEW = "REVIEW";

    // ==================== 签署方式 (共享) ====================
    /** 签署方式: 电子 */
    private static final String SIGNATURE_METHOD_ELECTRONIC = "ELECTRONIC";
    /** 签署方式: 纸质 */
    private static final String SIGNATURE_METHOD_PAPER = "PAPER";
    /** 签署方式: 盖章 */
    private static final String SIGNATURE_METHOD_STAMP = "STAMP";

    // ==================== 审批动作 ====================
    /** 审批动作: 通过 */
    private static final String APPROVE_ACTION_APPROVE = "APPROVE";
    /** 审批动作: 驳回 */
    private static final String APPROVE_ACTION_REJECT = "REJECT";

    // ==================== 常量 ====================
    /** 合同编号前缀 */
    private static final String CONTRACT_NO_PREFIX = "HT";
    /** 合同编号日期格式 */
    private static final DateTimeFormatter CONTRACT_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 合同序号格式 (4 位, 前补零) */
    private static final String CONTRACT_NO_SEQ_FORMAT = "%04d";
    /** 合同序号上限 (超过则进位到 5 位) */
    private static final int CONTRACT_NO_SEQ_BOUND = 10000;
    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";
    /** 默认合同金额 */
    private static final double DEFAULT_CONTRACT_AMOUNT = 0d;
    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;
    /** 默认提前提醒天数 (共享) */
    static final int DEFAULT_REMINDER_DAYS_BEFORE = 30;
    /** 默认自动续约月数 */
    private static final int DEFAULT_AUTO_RENEW_MONTHS = 0;
    /** 默认合同期限 (月) */
    private static final int DEFAULT_DURATION_MONTHS = 12;
    /** 默认提醒渠道 (共享) */
    static final String DEFAULT_CHANNELS = "EMAIL";
    /** 默认提醒接收人 (取合同销售人员) (共享) */
    static final String DEFAULT_RECIPIENT = "sales";
    /** 进行中状态集合 (到期扫描用, 共享) */
    static final List<String> ACTIVE_STATUSES = Arrays.asList(
            CONTRACT_STATUS_ACTIVE, CONTRACT_STATUS_SIGNED);

    /** 合同实例数据访问层 */
    private final ScrmContractRepository contractRepository;

    /** 合同提醒数据访问层 (删除合同清理与提醒生成) */
    private final ScrmContractReminderRepository reminderRepository;

    /** 客户数据访问层 (解析客户名称) */
    private final ScrmCustomerRepository customerRepository;

    /** 合同模板管理服务 (模板查询 / 渲染 / 使用次数) */
    private final ScrmContractTemplateService templateService;

    // ============================================================
    // 合同管理
    // ============================================================

    /**
     * 创建合同 (基于模板渲染)。
     * <p>选择模板 → 渲染模板内容 (变量替换) → 创建合同记录 → 递增模板使用次数 → 生成提醒。</p>
     *
     * @param createDto 创建请求 (模板 ID + 客户 ID + 变量 + 自定义字段)
     * @return 创建后的合同
     * @throws ScrmException 模板不存在 / 模板未启用 / 客户不存在
     */
    @Transactional
    public ScrmContractDto createContract(ScrmContractCreateDto createDto) throws ScrmException {
        if (createDto == null) {
            throw ScrmException.badRequest("创建参数不能为空");
        }
        if (createDto.getTemplateId() == null) {
            throw ScrmException.badRequest("模板 ID 不能为空");
        }
        if (createDto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        ScrmContractTemplateEntity template = templateService.findTemplateOrThrow(createDto.getTemplateId());
        if (!ScrmContractTemplateService.TEMPLATE_STATUS_ACTIVE.equals(template.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "模板未启用, 不允许创建合同: status=" + template.getStatus());
        }
        String customerName = resolveCustomerName(createDto.getCustomerId());
        // 渲染模板内容
        Map<String, Object> varMap = templateService.parseVariables(createDto.getVariables());
        // 注入客户名称到变量 (便于模板引用)
        if (customerName != null && !varMap.containsKey("customerName")) {
            varMap.put("customerName", customerName);
        }
        String content = templateService.renderContent(template.getTemplateContent(), varMap);
        // 计算合同日期
        LocalDate startDate = createDto.getStartDate() != null ? createDto.getStartDate() : LocalDate.now();
        LocalDate endDate = createDto.getEndDate() != null ? createDto.getEndDate()
                : startDate.plusMonths(DEFAULT_DURATION_MONTHS);
        if (endDate.isBefore(startDate)) {
            throw ScrmException.badRequest("合同结束日期不能早于开始日期");
        }
        int durationMonths = (int) ChronoUnit.MONTHS.between(startDate, endDate);
        // 构建合同实体
        ScrmContractEntity entity = new ScrmContractEntity();
        entity.setContractNo(generateContractNo());
        entity.setContractName(createDto.getContractName() != null ? createDto.getContractName()
                : template.getContractType() + "_" + (customerName != null ? customerName : createDto.getCustomerId()));
        entity.setContractType(template.getContractType());
        entity.setTemplateId(template.getId());
        entity.setCustomerId(createDto.getCustomerId());
        entity.setCustomerName(customerName);
        entity.setTitle(entity.getContractName());
        entity.setContent(content);
        entity.setVariables(createDto.getVariables());
        entity.setContractAmount(
                createDto.getContractAmount() != null ? createDto.getContractAmount() : DEFAULT_CONTRACT_AMOUNT);
        entity.setCurrency(DEFAULT_CURRENCY);
        entity.setStartDate(startDate);
        entity.setEndDate(endDate);
        entity.setDurationMonths(durationMonths);
        entity.setAutoRenew(Boolean.FALSE);
        entity.setAutoRenewMonths(DEFAULT_AUTO_RENEW_MONTHS);
        entity.setStatus(CONTRACT_STATUS_DRAFT);
        entity.setPriority(DEFAULT_PRIORITY);
        entity.setSalesPersonId(createDto.getSalesPersonId());
        entity.setSalesPersonName(createDto.getSalesPersonName());
        entity.setRemindersEnabled(Boolean.TRUE);
        entity.setReminderDaysBefore(DEFAULT_REMINDER_DAYS_BEFORE);
        entity.setTerms(template.getClauses());
        entity.setCustomFields(createDto.getCustomFields());
        entity.setCreatedBy(createDto.getCreatedBy());
        entity = contractRepository.save(entity);
        // 递增模板使用次数
        templateService.incrementUsage(template.getId());
        // 生成到期提醒
        generateRemindersForContract(entity.getId());
        log.info("创建合同: id={}, contractNo={}, customerId={}", entity.getId(), entity.getContractNo(),
                entity.getCustomerId());
        return toContractDto(entity);
    }

    /**
     * 更新合同 (字段非空才覆盖)。
     * <p>仅 DRAFT 状态合同允许更新核心字段 (金额/日期/内容)。</p>
     *
     * @param id  合同 ID
     * @param dto 合同参数
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 参数非法 / 状态不允许更新
     */
    @Transactional
    public ScrmContractDto updateContract(Long id, ScrmContractDto dto) throws ScrmException {
        ScrmContractEntity entity = findContractOrThrow(id);
        boolean isDraft = CONTRACT_STATUS_DRAFT.equals(entity.getStatus());
        if (!isDraft) {
            // 非草稿状态仅允许更新备注/标签/销售人员等非核心字段
            if (dto.getContractAmount() != null && !dto.getContractAmount().equals(entity.getContractAmount())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "非草稿状态合同不允许修改金额: status=" + entity.getStatus());
            }
            if (dto.getStartDate() != null && !dto.getStartDate().equals(entity.getStartDate())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "非草稿状态合同不允许修改开始日期: status=" + entity.getStatus());
            }
            if (dto.getEndDate() != null && !dto.getEndDate().equals(entity.getEndDate())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "非草稿状态合同不允许修改结束日期: status=" + entity.getStatus());
            }
            if (dto.getContent() != null && !dto.getContent().equals(entity.getContent())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "非草稿状态合同不允许修改合同内容: status=" + entity.getStatus());
            }
        }
        if (dto.getContractName() != null) entity.setContractName(dto.getContractName());
        if (dto.getContractType() != null) entity.setContractType(dto.getContractType());
        if (dto.getCustomerName() != null) entity.setCustomerName(dto.getCustomerName());
        if (dto.getCustomerContact() != null) entity.setCustomerContact(dto.getCustomerContact());
        if (dto.getCustomerAddress() != null) entity.setCustomerAddress(dto.getCustomerAddress());
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getVariables() != null) entity.setVariables(dto.getVariables());
        if (dto.getContractAmount() != null) entity.setContractAmount(dto.getContractAmount());
        if (dto.getCurrency() != null) entity.setCurrency(dto.getCurrency());
        if (dto.getPaymentTerms() != null) entity.setPaymentTerms(dto.getPaymentTerms());
        if (dto.getStartDate() != null) entity.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null) entity.setEndDate(dto.getEndDate());
        if (dto.getDurationMonths() != null) entity.setDurationMonths(dto.getDurationMonths());
        if (dto.getAutoRenew() != null) entity.setAutoRenew(dto.getAutoRenew());
        if (dto.getAutoRenewMonths() != null) entity.setAutoRenewMonths(dto.getAutoRenewMonths());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getSalesPersonId() != null) entity.setSalesPersonId(dto.getSalesPersonId());
        if (dto.getSalesPersonName() != null) entity.setSalesPersonName(dto.getSalesPersonName());
        if (dto.getDepartmentId() != null) entity.setDepartmentId(dto.getDepartmentId());
        if (dto.getDepartmentName() != null) entity.setDepartmentName(dto.getDepartmentName());
        if (dto.getAttachments() != null) entity.setAttachments(dto.getAttachments());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getRelatedContracts() != null) entity.setRelatedContracts(dto.getRelatedContracts());
        if (dto.getRemindersEnabled() != null) entity.setRemindersEnabled(dto.getRemindersEnabled());
        if (dto.getReminderDaysBefore() != null) entity.setReminderDaysBefore(dto.getReminderDaysBefore());
        if (dto.getTerms() != null) entity.setTerms(dto.getTerms());
        if (dto.getCustomFields() != null) entity.setCustomFields(dto.getCustomFields());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        // 重新计算期限
        if (dto.getStartDate() != null || dto.getEndDate() != null) {
            entity.setDurationMonths((int) ChronoUnit.MONTHS.between(entity.getStartDate(), entity.getEndDate()));
        }
        entity = contractRepository.save(entity);
        log.info("更新合同: id={}", id);
        return toContractDto(entity);
    }

    /**
     * 删除合同。
     * <p>仅 DRAFT / CANCELLED 状态合同允许删除, 已生效合同请先终止或归档。</p>
     *
     * @param id 合同 ID
     * @throws ScrmException 合同不存在 / 状态不允许删除
     */
    @Transactional
    public void deleteContract(Long id) throws ScrmException {
        ScrmContractEntity entity = findContractOrThrow(id);
        if (!CONTRACT_STATUS_DRAFT.equals(entity.getStatus()) && !CONTRACT_STATUS_CANCELLED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "合同状态不允许删除, 仅 DRAFT/CANCELLED 可删除: status=" + entity.getStatus());
        }
        // 删除关联合同提醒
        Specification<ScrmContractReminderEntity> reminderSpec = (root, query, cb) -> cb.and(
                cb.equal(root.get("contractId"), id));
        List<ScrmContractReminderEntity> reminders = reminderRepository.findAll(reminderSpec);
        if (!reminders.isEmpty()) {
            reminderRepository.deleteAll(reminders);
        }
        contractRepository.delete(entity);
        log.info("删除合同: id={}", id);
    }

    /**
     * 查询合同详情。
     *
     * @param id 合同 ID
     * @return 合同 DTO
     * @throws ScrmException 合同不存在
     */
    @Transactional(readOnly = true)
    public ScrmContractDto getContract(Long id) throws ScrmException {
        return toContractDto(findContractOrThrow(id));
    }

    /**
     * 按合同编号查询合同。
     *
     * @param contractNo 合同编号
     * @return 合同 DTO
     * @throws ScrmException 合同不存在
     */
    @Transactional(readOnly = true)
    public ScrmContractDto getContractByNo(String contractNo) throws ScrmException {
        return toContractDto(findContractByNoOrThrow(contractNo));
    }

    /**
     * 分页查询合同, 支持按合同类型、客户、状态、销售人员、日期范围与关键词过滤。
     *
     * @param contractType 合同类型过滤 (可空)
     * @param customerId   客户 ID 过滤 (可空)
     * @param status       状态过滤 (可空)
     * @param salesPersonId 销售人员 ID 过滤 (可空)
     * @param startDate    开始日期下限 (按合同开始日期, 可空)
     * @param endDate      开始日期上限 (按合同开始日期, 可空)
     * @param keyword      关键词过滤, 匹配合同名称或编号 (可空)
     * @param pageable     分页参数
     * @return 合同分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractDto> listContracts(String contractType, Long customerId, String status,
                                                String salesPersonId, LocalDate startDate, LocalDate endDate,
                                                String keyword, Pageable pageable) {
        Specification<ScrmContractEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (contractType != null && !contractType.isBlank()) {
                predicates.add(cb.equal(root.get("contractType"), contractType));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (salesPersonId != null && !salesPersonId.isBlank()) {
                predicates.add(cb.equal(root.get("salesPersonId"), salesPersonId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), endDate));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("contractName")), like),
                        cb.like(cb.lower(root.get("contractNo")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.findAll(spec, pageable).map(this::toContractDto);
    }

    /**
     * 分页查询客户合同列表。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 合同分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractDto> getContractsByCustomer(Long customerId, Pageable pageable) {
        Specification<ScrmContractEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("customerId"), customerId));
        return contractRepository.findAll(spec, pageable).map(this::toContractDto);
    }

    /**
     * 分页查询销售人员合同列表。
     *
     * @param salesPersonId 销售人员 ID
     * @param pageable      分页参数
     * @return 合同分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractDto> getContractsBySalesPerson(String salesPersonId, Pageable pageable) {
        Specification<ScrmContractEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("salesPersonId"), salesPersonId));
        return contractRepository.findAll(spec, pageable).map(this::toContractDto);
    }

    /**
     * 提交审批: 合同状态由 DRAFT 流转至 PENDING_REVIEW。
     *
     * @param id 合同 ID
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @Transactional
    public ScrmContractDto submitForApproval(Long id) throws ScrmException {
        ScrmContractEntity entity = findContractOrThrow(id);
        if (!CONTRACT_STATUS_DRAFT.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "合同状态非法, 仅 DRAFT 可提交审批: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(CONTRACT_STATUS_PENDING_REVIEW);
        entity = contractRepository.save(entity);
        log.info("提交合同审批: id={}", id);
        return toContractDto(entity);
    }

    /**
     * 审批合同: 通过则流转至 PENDING_SIGNATURE, 驳回则回退至 DRAFT。
     *
     * @param approveDto 审批请求 (合同 ID + 动作 + 意见)
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @Transactional
    public ScrmContractDto approve(ScrmContractApproveDto approveDto) throws ScrmException {
        if (approveDto == null || approveDto.getContractId() == null) {
            throw ScrmException.badRequest("审批参数不能为空");
        }
        if (approveDto.getAction() == null || approveDto.getAction().isBlank()) {
            throw ScrmException.badRequest("审批动作不能为空");
        }
        ScrmContractEntity entity = findContractOrThrow(approveDto.getContractId());
        if (!CONTRACT_STATUS_PENDING_REVIEW.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "合同状态非法, 仅 PENDING_REVIEW 可审批: currentStatus=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setApproverId(approveDto.getApproverId());
        entity.setApproverName(approveDto.getApproverName());
        entity.setApprovedAt(now);
        entity.setApprovalComment(approveDto.getComment());
        if (APPROVE_ACTION_APPROVE.equals(approveDto.getAction())) {
            entity.setStatus(CONTRACT_STATUS_PENDING_SIGNATURE);
        } else if (APPROVE_ACTION_REJECT.equals(approveDto.getAction())) {
            entity.setStatus(CONTRACT_STATUS_DRAFT);
        } else {
            throw ScrmException.badRequest("审批动作非法: action=" + approveDto.getAction());
        }
        entity = contractRepository.save(entity);
        log.info("审批合同: id={}, action={}", approveDto.getContractId(), approveDto.getAction());
        return toContractDto(entity);
    }

    /**
     * 驳回合同 (状态回退至 DRAFT 并记录原因)。
     *
     * @param id     合同 ID
     * @param reason 驳回原因
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @Transactional
    public ScrmContractDto reject(Long id, String reason) throws ScrmException {
        ScrmContractEntity entity = findContractOrThrow(id);
        if (!CONTRACT_STATUS_PENDING_REVIEW.equals(entity.getStatus()) && !CONTRACT_STATUS_PENDING_SIGNATURE.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "合同状态非法, 仅 PENDING_REVIEW/PENDING_SIGNATURE 可驳回: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(CONTRACT_STATUS_DRAFT);
        entity.setApprovalComment(reason);
        entity = contractRepository.save(entity);
        log.info("驳回合同: id={}, reason={}", id, reason);
        return toContractDto(entity);
    }

    /**
     * 签署合同: 状态由 PENDING_SIGNATURE 流转至 SIGNED, 记录签署人与签署方式。
     * <p>签署日期缺省为当天, 生效日期缺省为签署当天。</p>
     *
     * @param signDto 签署请求 (合同 ID + 签署人 + 签署方式 + 签署文件 URL)
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @Transactional
    public ScrmContractDto sign(ScrmContractSignDto signDto) throws ScrmException {
        if (signDto == null || signDto.getContractId() == null) {
            throw ScrmException.badRequest("签署参数不能为空");
        }
        ScrmContractEntity entity = findContractOrThrow(signDto.getContractId());
        if (!CONTRACT_STATUS_PENDING_SIGNATURE.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "合同状态非法, 仅 PENDING_SIGNATURE 可签署: currentStatus=" + entity.getStatus());
        }
        LocalDate today = LocalDate.now();
        entity.setSignerId(signDto.getSignerId());
        entity.setSignerName(signDto.getSignerName());
        entity.setSignatureMethod(signDto.getSignatureMethod());
        entity.setSignatureUrl(signDto.getSignatureUrl());
        entity.setSignedDate(today);
        entity.setEffectiveDate(today);
        entity.setStatus(CONTRACT_STATUS_SIGNED);
        entity = contractRepository.save(entity);
        log.info("签署合同: id={}, signerId={}, method={}", signDto.getContractId(),
                signDto.getSignerId(), signDto.getSignatureMethod());
        return toContractDto(entity);
    }

    /**
     * 激活合同: 状态由 SIGNED 流转至 ACTIVE。
     *
     * @param id 合同 ID
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @Transactional
    public ScrmContractDto activate(Long id) throws ScrmException {
        ScrmContractEntity entity = findContractOrThrow(id);
        if (!CONTRACT_STATUS_SIGNED.equals(entity.getStatus()) && !CONTRACT_STATUS_DRAFT.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "合同状态非法, 仅 SIGNED/DRAFT 可激活: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(CONTRACT_STATUS_ACTIVE);
        if (entity.getEffectiveDate() == null) {
            entity.setEffectiveDate(LocalDate.now());
        }
        entity = contractRepository.save(entity);
        log.info("激活合同: id={}", id);
        return toContractDto(entity);
    }

    /**
     * 终止合同: 状态流转至 TERMINATED, 记录失效日期。
     *
     * @param id              合同 ID
     * @param reason          终止原因
     * @param terminationDate 终止日期 (可空, 缺省为当天)
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @Transactional
    public ScrmContractDto terminate(Long id, String reason, LocalDate terminationDate) throws ScrmException {
        ScrmContractEntity entity = findContractOrThrow(id);
        if (!CONTRACT_STATUS_ACTIVE.equals(entity.getStatus()) && !CONTRACT_STATUS_SIGNED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "合同状态非法, 仅 ACTIVE/SIGNED 可终止: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(CONTRACT_STATUS_TERMINATED);
        entity.setExpiredDate(terminationDate != null ? terminationDate : LocalDate.now());
        entity.setNotes(reason);
        entity = contractRepository.save(entity);
        log.info("终止合同: id={}, reason={}", id, reason);
        return toContractDto(entity);
    }

    /**
     * 取消合同: 状态流转至 CANCELLED。
     *
     * @param id     合同 ID
     * @param reason 取消原因
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @Transactional
    public ScrmContractDto cancel(Long id, String reason) throws ScrmException {
        ScrmContractEntity entity = findContractOrThrow(id);
        if (CONTRACT_STATUS_ARCHIVED.equals(entity.getStatus())
                || CONTRACT_STATUS_TERMINATED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "合同状态非法, 已归档/已终止合同不可取消: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(CONTRACT_STATUS_CANCELLED);
        entity.setNotes(reason);
        entity = contractRepository.save(entity);
        log.info("取消合同: id={}, reason={}", id, reason);
        return toContractDto(entity);
    }

    /**
     * 归档合同: 状态流转至 ARCHIVED。
     *
     * @param id 合同 ID
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @Transactional
    public ScrmContractDto archive(Long id) throws ScrmException {
        ScrmContractEntity entity = findContractOrThrow(id);
        if (CONTRACT_STATUS_DRAFT.equals(entity.getStatus())
                || CONTRACT_STATUS_PENDING_REVIEW.equals(entity.getStatus())
                || CONTRACT_STATUS_PENDING_SIGNATURE.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "合同状态非法, 流程中的合同不可归档: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(CONTRACT_STATUS_ARCHIVED);
        entity = contractRepository.save(entity);
        log.info("归档合同: id={}", id);
        return toContractDto(entity);
    }

    /**
     * 续约合同: 基于原合同创建新合同, 关联原合同并生成提醒。
     * <p>新合同继承原合同的核心字段 (客户/类型/模板/内容), 覆盖结束日期/金额/自动续约配置,
     * 状态置为 DRAFT。原合同的 renewedToId 指向新合同。</p>
     *
     * @param renewDto 续约请求 (原合同 ID + 新结束日期 + 自动续约 + 新金额)
     * @return 新合同
     * @throws ScrmException 原合同不存在 / 状态非法
     */
    @Transactional
    public ScrmContractDto renew(ScrmContractRenewDto renewDto) throws ScrmException {
        if (renewDto == null || renewDto.getContractId() == null) {
            throw ScrmException.badRequest("续约参数不能为空");
        }
        if (renewDto.getNewEndDate() == null) {
            throw ScrmException.badRequest("新合同结束日期不能为空");
        }
        ScrmContractEntity original = findContractOrThrow(renewDto.getContractId());
        if (CONTRACT_STATUS_DRAFT.equals(original.getStatus())
                || CONTRACT_STATUS_PENDING_REVIEW.equals(original.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "原合同状态非法, 流程中的合同不可续约: currentStatus=" + original.getStatus());
        }
        LocalDate newStartDate = original.getEndDate().isAfter(LocalDate.now())
                ? original.getEndDate() : LocalDate.now();
        if (renewDto.getNewEndDate().isBefore(newStartDate)) {
            throw ScrmException.badRequest("新合同结束日期不能早于开始日期");
        }
        ScrmContractEntity entity = new ScrmContractEntity();
        entity.setContractNo(generateContractNo());
        entity.setContractName(original.getContractName() + "_续约");
        entity.setContractType(original.getContractType());
        entity.setTemplateId(original.getTemplateId());
        entity.setCustomerId(original.getCustomerId());
        entity.setCustomerName(original.getCustomerName());
        entity.setCustomerContact(original.getCustomerContact());
        entity.setCustomerAddress(original.getCustomerAddress());
        entity.setTitle(entity.getContractName());
        entity.setDescription(original.getDescription());
        entity.setContent(original.getContent());
        entity.setVariables(original.getVariables());
        entity.setContractAmount(renewDto.getNewAmount() != null ? renewDto.getNewAmount()
                : original.getContractAmount());
        entity.setCurrency(original.getCurrency());
        entity.setPaymentTerms(original.getPaymentTerms());
        entity.setStartDate(newStartDate);
        entity.setEndDate(renewDto.getNewEndDate());
        entity.setDurationMonths((int) ChronoUnit.MONTHS.between(newStartDate, renewDto.getNewEndDate()));
        entity.setAutoRenew(renewDto.getAutoRenew() != null ? renewDto.getAutoRenew() : original.getAutoRenew());
        entity.setAutoRenewMonths(original.getAutoRenewMonths() != null ? original.getAutoRenewMonths()
                : DEFAULT_AUTO_RENEW_MONTHS);
        entity.setStatus(CONTRACT_STATUS_DRAFT);
        entity.setPriority(original.getPriority());
        entity.setSalesPersonId(original.getSalesPersonId());
        entity.setSalesPersonName(original.getSalesPersonName());
        entity.setDepartmentId(original.getDepartmentId());
        entity.setDepartmentName(original.getDepartmentName());
        entity.setRemindersEnabled(original.getRemindersEnabled());
        entity.setReminderDaysBefore(original.getReminderDaysBefore());
        entity.setTerms(original.getTerms());
        entity.setCustomFields(original.getCustomFields());
        entity.setRenewalOfId(original.getId());
        entity.setCreatedBy(original.getCreatedBy());
        entity = contractRepository.save(entity);
        // 原合同关联新合同
        original.setRenewedToId(entity.getId());
        contractRepository.save(original);
        // 递增模板使用次数
        if (original.getTemplateId() != null) {
            templateService.incrementUsage(original.getTemplateId());
        }
        // 生成到期提醒
        generateRemindersForContract(entity.getId());
        log.info("续约合同: originalId={}, newId={}, newEndDate={}", original.getId(), entity.getId(),
                renewDto.getNewEndDate());
        return toContractDto(entity);
    }

    /**
     * 复制合同 (基于已有合同创建新合同, 新合同编号由参数指定)。
     * <p>复制后的合同状态为 DRAFT, 不继承审批/签署信息。</p>
     *
     * @param id    源合同 ID
     * @param newNo 新合同编号
     * @return 新合同
     * @throws ScrmException 源合同不存在 / 新编号重复
     */
    @Transactional
    public ScrmContractDto duplicate(Long id, String newNo) throws ScrmException {
        if (newNo == null || newNo.isBlank()) {
            throw ScrmException.badRequest("新合同编号不能为空");
        }
        ScrmContractEntity source = findContractOrThrow(id);
        if (contractRepository.findByContractNo(newNo).isPresent()) {
            throw ScrmException.conflict("合同编号已存在: no=" + newNo);
        }
        ScrmContractEntity entity = new ScrmContractEntity();
        entity.setContractNo(newNo);
        entity.setContractName(source.getContractName() + "_副本");
        entity.setContractType(source.getContractType());
        entity.setTemplateId(source.getTemplateId());
        entity.setCustomerId(source.getCustomerId());
        entity.setCustomerName(source.getCustomerName());
        entity.setCustomerContact(source.getCustomerContact());
        entity.setCustomerAddress(source.getCustomerAddress());
        entity.setTitle(source.getTitle());
        entity.setDescription(source.getDescription());
        entity.setContent(source.getContent());
        entity.setVariables(source.getVariables());
        entity.setContractAmount(source.getContractAmount());
        entity.setCurrency(source.getCurrency());
        entity.setPaymentTerms(source.getPaymentTerms());
        entity.setStartDate(source.getStartDate());
        entity.setEndDate(source.getEndDate());
        entity.setDurationMonths(source.getDurationMonths());
        entity.setAutoRenew(source.getAutoRenew());
        entity.setAutoRenewMonths(source.getAutoRenewMonths());
        entity.setStatus(CONTRACT_STATUS_DRAFT);
        entity.setPriority(source.getPriority());
        entity.setSalesPersonId(source.getSalesPersonId());
        entity.setSalesPersonName(source.getSalesPersonName());
        entity.setDepartmentId(source.getDepartmentId());
        entity.setDepartmentName(source.getDepartmentName());
        entity.setAttachments(source.getAttachments());
        entity.setTags(source.getTags());
        entity.setRemindersEnabled(source.getRemindersEnabled());
        entity.setReminderDaysBefore(source.getReminderDaysBefore());
        entity.setTerms(source.getTerms());
        entity.setCustomFields(source.getCustomFields());
        entity.setNotes(source.getNotes());
        entity.setCreatedBy(source.getCreatedBy());
        entity = contractRepository.save(entity);
        log.info("复制合同: sourceId={}, newId={}, newNo={}", id, entity.getId(), newNo);
        return toContractDto(entity);
    }

    /**
     * 查询即将到期的合同 (未来 N 天内到期, 状态为 ACTIVE/SIGNED)。
     *
     * @param days 天数
     * @return 合同列表
     */
    @Transactional(readOnly = true)
    public List<ScrmContractDto> getExpiringContracts(int days) {
        LocalDate threshold = LocalDate.now().plusDays(days);
        List<ScrmContractEntity> contracts = contractRepository.findByStatusInAndEndDateLessThanEqual(
                 ACTIVE_STATUSES, threshold);
        return contracts.stream()
                .filter(c -> c.getEndDate() != null && !c.getEndDate().isBefore(LocalDate.now()))
                .map(this::toContractDto)
                .collect(Collectors.toList());
    }

    /**
     * 查询已到期合同 (结束日期早于今天, 状态为 ACTIVE/SIGNED)。
     *
     * @return 合同列表
     */
    @Transactional(readOnly = true)
    public List<ScrmContractDto> getExpiredContracts() {
        LocalDate today = LocalDate.now();
        List<ScrmContractEntity> contracts = contractRepository.findByStatusInAndEndDateLessThanEqual(
                 ACTIVE_STATUSES, today);
        return contracts.stream()
                .filter(c -> c.getEndDate() != null && c.getEndDate().isBefore(today))
                .map(this::toContractDto)
                .collect(Collectors.toList());
    }

    /**
     * 为合同生成提醒 (到期/续约/付款)。
     * <p>根据合同 endDate 与 reminderDaysBefore 生成到期提醒 (EXPIRY),
     * 自动续约合同生成续约提醒 (RENEWAL), 有付款条款的合同生成付款提醒 (PAYMENT)。</p>
     *
     * @param contractId 合同 ID
     * @return 生成的提醒列表
     * @throws ScrmException 合同不存在
     */
    @Transactional
    public List<ScrmContractReminderDto> generateRemindersForContract(Long contractId) throws ScrmException {
        ScrmContractEntity contract = findContractOrThrow(contractId);
        if (contract.getRemindersEnabled() == null || !contract.getRemindersEnabled()) {
            return new ArrayList<>();
        }
        List<ScrmContractReminderEntity> reminders = new ArrayList<>();
        int daysBefore = contract.getReminderDaysBefore() != null ? contract.getReminderDaysBefore()
                : DEFAULT_REMINDER_DAYS_BEFORE;
        // 到期提醒
        if (contract.getEndDate() != null) {
            LocalDate expiryReminderDate = contract.getEndDate().minusDays(daysBefore);
            if (!expiryReminderDate.isBefore(LocalDate.now())) {
                reminders.add(buildReminderEntity(contract, REMINDER_TYPE_EXPIRY, expiryReminderDate,
                        "合同到期提醒", "合同 " + contract.getContractNo() + " 将于 " + contract.getEndDate() + " 到期",
                        ACTION_NOTIFY));
            }
        }
        // 续约提醒 (自动续约合同)
        if (Boolean.TRUE.equals(contract.getAutoRenew()) && contract.getEndDate() != null) {
            LocalDate renewalReminderDate = contract.getEndDate().minusDays(daysBefore);
            if (!renewalReminderDate.isBefore(LocalDate.now())) {
                reminders.add(buildReminderEntity(contract, REMINDER_TYPE_RENEWAL, renewalReminderDate,
                        "合同续约提醒", "合同 " + contract.getContractNo() + " 即将到期, 请及时续约",
                        ACTION_RENEW));
            }
        }
        // 付款提醒 (有付款条款的合同, 提前 7 天生成)
        if (contract.getPaymentTerms() != null && !contract.getPaymentTerms().isBlank() && contract.getStartDate() != null) {
            LocalDate paymentReminderDate = contract.getStartDate().plusDays(7);
            if (!paymentReminderDate.isBefore(LocalDate.now())) {
                reminders.add(buildReminderEntity(contract, REMINDER_TYPE_PAYMENT, paymentReminderDate,
                        "合同付款提醒", "合同 " + contract.getContractNo() + " 付款条款: " + contract.getPaymentTerms(),
                        ACTION_NOTIFY));
            }
        }
        List<ScrmContractReminderEntity> saved = reminderRepository.saveAll(reminders);
        log.info("生成合同提醒: contractId={}, count={}", contractId, saved.size());
        return saved.stream().map(this::toReminderDto).collect(Collectors.toList());
    }

    // ============================================================
    // 合同编号生成
    // ============================================================

    /**
     * 生成合同编号 (HT + 年月日 + 4 位序号)。
     * <p>序号 = 当日已生成合同数 + 1, 超过 9999 则扩展为 5 位。</p>
     *
     * @return 合同编号
     */
    public String generateContractNo() {
        String datePart = LocalDate.now().format(CONTRACT_NO_DATE_FORMAT);
        String prefix = CONTRACT_NO_PREFIX + datePart;
        long count = contractRepository.countByContractNoStartingWith(prefix);
        long seq = count + 1;
        String seqPart = seq < CONTRACT_NO_SEQ_BOUND
                ? String.format(CONTRACT_NO_SEQ_FORMAT, seq)
                : String.valueOf(seq);
        return prefix + seqPart;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询合同, 不存在抛异常
     */
    ScrmContractEntity findContractOrThrow(Long id) throws ScrmException {
        ScrmContractEntity entity = contractRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合同不存在: id=" + id));

        return entity;
    }

    /**
     * 按合同编号查询合同, 不存在抛异常
     */
    ScrmContractEntity findContractByNoOrThrow(String contractNo) throws ScrmException {
        ScrmContractEntity entity = contractRepository.findByContractNo(contractNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合同不存在: contractNo=" + contractNo));

        return entity;
    }

    /**
     * 解析客户名称 (从客户实体 nickname 字段), 客户不存在时返回 null。
     */
    private String resolveCustomerName(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findById(customerId)

                .map(ScrmCustomerEntity::getNickname)
                .orElse(null);
    }

    /**
     * 构建提醒实体 (默认 PENDING 状态, 通知渠道默认 EMAIL)。
     */
    private ScrmContractReminderEntity buildReminderEntity(ScrmContractEntity contract, String reminderType,
                                                            LocalDate reminderDate, String title, String message,
                                                            String actionRequired) {
        ScrmContractReminderEntity entity = new ScrmContractReminderEntity();
        entity.setContractId(contract.getId());
        entity.setReminderType(reminderType);
        entity.setReminderDate(reminderDate);
        entity.setTitle(title);
        entity.setMessage(message);
        entity.setRecipients(contract.getSalesPersonId() != null ? contract.getSalesPersonId() : DEFAULT_RECIPIENT);
        entity.setChannels(DEFAULT_CHANNELS);
        entity.setStatus(REMINDER_STATUS_PENDING);
        entity.setSentCount(0);
        entity.setFailedCount(0);
        entity.setResponseCount(0);
        entity.setIsRecurring(Boolean.FALSE);
        entity.setActionRequired(actionRequired);
        entity.setActionTaken(Boolean.FALSE);
        return entity;
    }

    /**
     * 提醒实体转 DTO (供提醒兄弟类复用)
     */
    ScrmContractReminderDto toReminderDto(ScrmContractReminderEntity entity) {
        ScrmContractReminderDto dto = new ScrmContractReminderDto();
        dto.setId(entity.getId());
        dto.setContractId(entity.getContractId());
        dto.setReminderType(entity.getReminderType());
        dto.setReminderDate(entity.getReminderDate());
        dto.setReminderTime(entity.getReminderTime());
        dto.setTitle(entity.getTitle());
        dto.setMessage(entity.getMessage());
        dto.setRecipients(entity.getRecipients());
        dto.setChannels(entity.getChannels());
        dto.setStatus(entity.getStatus());
        dto.setSentAt(entity.getSentAt());
        dto.setSentCount(entity.getSentCount());
        dto.setFailedCount(entity.getFailedCount());
        dto.setResponseCount(entity.getResponseCount());
        dto.setIsRecurring(entity.getIsRecurring());
        dto.setRecurringConfig(entity.getRecurringConfig());
        dto.setActionRequired(entity.getActionRequired());
        dto.setActionUrl(entity.getActionUrl());
        dto.setActionTaken(entity.getActionTaken());
        dto.setActionTakenAt(entity.getActionTakenAt());
        dto.setActionTakenBy(entity.getActionTakenBy());
        dto.setNotes(entity.getNotes());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 合同实体转 DTO
     */
    private ScrmContractDto toContractDto(ScrmContractEntity entity) {
        ScrmContractDto dto = new ScrmContractDto();
        dto.setId(entity.getId());
        dto.setContractNo(entity.getContractNo());
        dto.setContractName(entity.getContractName());
        dto.setContractType(entity.getContractType());
        dto.setTemplateId(entity.getTemplateId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setCustomerContact(entity.getCustomerContact());
        dto.setCustomerAddress(entity.getCustomerAddress());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setContent(entity.getContent());
        dto.setVariables(entity.getVariables());
        dto.setContractAmount(entity.getContractAmount());
        dto.setCurrency(entity.getCurrency());
        dto.setPaymentTerms(entity.getPaymentTerms());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setDurationMonths(entity.getDurationMonths());
        dto.setAutoRenew(entity.getAutoRenew());
        dto.setAutoRenewMonths(entity.getAutoRenewMonths());
        dto.setSignedDate(entity.getSignedDate());
        dto.setEffectiveDate(entity.getEffectiveDate());
        dto.setExpiredDate(entity.getExpiredDate());
        dto.setStatus(entity.getStatus());
        dto.setPriority(entity.getPriority());
        dto.setSalesPersonId(entity.getSalesPersonId());
        dto.setSalesPersonName(entity.getSalesPersonName());
        dto.setDepartmentId(entity.getDepartmentId());
        dto.setDepartmentName(entity.getDepartmentName());
        dto.setApproverId(entity.getApproverId());
        dto.setApproverName(entity.getApproverName());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setApprovalComment(entity.getApprovalComment());
        dto.setSignerId(entity.getSignerId());
        dto.setSignerName(entity.getSignerName());
        dto.setSignatureMethod(entity.getSignatureMethod());
        dto.setSignatureUrl(entity.getSignatureUrl());
        dto.setAttachments(entity.getAttachments());
        dto.setTags(entity.getTags());
        dto.setRelatedContracts(entity.getRelatedContracts());
        dto.setRenewalOfId(entity.getRenewalOfId());
        dto.setRenewedToId(entity.getRenewedToId());
        dto.setRemindersEnabled(entity.getRemindersEnabled());
        dto.setReminderDaysBefore(entity.getReminderDaysBefore());
        dto.setLastReminderSentAt(entity.getLastReminderSentAt());
        dto.setTerms(entity.getTerms());
        dto.setCustomFields(entity.getCustomFields());
        dto.setNotes(entity.getNotes());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}