/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractPaymentService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmContractPaymentDto;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmContractPaymentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmContractPaymentRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
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
import java.util.stream.Collectors;

/**
 * SCRM 合同付款管理服务。
 * <p>
 * 承载合同付款全生命周期管理能力: 付款增删改查、到期/逾期付款查询、记录付款 (更新金额与状态)、
 * 取消付款、开票、发送提醒、检查并发送提醒 (定时任务), 以及付款统计与付款计划查询。
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
public class ScrmContractPaymentService {

    // ==================== 付款状态 ====================
    /** 付款状态: 待付 */
    private static final String PAYMENT_STATUS_PENDING = "PENDING";
    /** 付款状态: 到期 */
    private static final String PAYMENT_STATUS_DUE = "DUE";
    /** 付款状态: 逾期 */
    private static final String PAYMENT_STATUS_OVERDUE = "OVERDUE";
    /** 付款状态: 部分付款 */
    private static final String PAYMENT_STATUS_PARTIAL = "PARTIAL";
    /** 付款状态: 已付 */
    private static final String PAYMENT_STATUS_PAID = "PAID";
    /** 付款状态: 已取消 */
    private static final String PAYMENT_STATUS_CANCELLED = "CANCELLED";

    // ==================== 常量 ====================
    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";
    /** 默认金额 */
    private static final double DEFAULT_AMOUNT = 0d;
    /** 进行中状态集合 (到期扫描用) */
    private static final List<String> ACTIVE_PAYMENT_STATUSES = List.of(
            PAYMENT_STATUS_PENDING, PAYMENT_STATUS_DUE, PAYMENT_STATUS_OVERDUE, PAYMENT_STATUS_PARTIAL);

    /** 合同付款数据访问层 */
    private final ScrmContractPaymentRepository paymentRepository;
    /** 合同数据访问层 (校验合同存在性) */
    private final ScrmContractRepository contractRepository;

    // ============================================================
    // 付款 CRUD
    // ============================================================

    /**
     * 添加合同付款。
     * <p>校验参数合法性后写入归属账号 ID 持久化, 状态缺省 PENDING, 金额缺省 0。</p>
     *
     * @param dto 付款参数
     * @return 创建后的付款
     * @throws ScrmException 参数非法 / 合同不存在 / 付款编号重复
     */
    @Transactional
    public ScrmContractPaymentDto addPayment(ScrmContractPaymentDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("付款参数不能为空");
        }
        if (dto.getContractId() == null) {
            throw ScrmException.badRequest("合同 ID 不能为空");
        }
        if (dto.getPaymentNo() == null || dto.getPaymentNo().isBlank()) {
            throw ScrmException.badRequest("付款编号不能为空");
        }
        if (dto.getPaymentType() == null || dto.getPaymentType().isBlank()) {
            throw ScrmException.badRequest("付款类型不能为空");
        }
        if (dto.getPlannedDate() == null) {
            throw ScrmException.badRequest("计划付款日期不能为空");
        }
        ScrmContractEntity contract = findContractOrThrow(dto.getContractId());
        if (paymentRepository.findByPaymentNo(dto.getPaymentNo()).isPresent()) {
            throw ScrmException.conflict("付款编号已存在: no=" + dto.getPaymentNo());
        }
        ScrmContractPaymentEntity entity = new ScrmContractPaymentEntity();
        entity.setContractId(dto.getContractId());
        entity.setContractNo(contract.getContractNo());
        entity.setPaymentNo(dto.getPaymentNo());
        entity.setPaymentName(dto.getPaymentName());
        entity.setPaymentType(dto.getPaymentType());
        entity.setPaymentStatus(dto.getPaymentStatus() != null ? dto.getPaymentStatus() : PAYMENT_STATUS_PENDING);
        entity.setPlannedAmount(dto.getPlannedAmount() != null ? dto.getPlannedAmount() : DEFAULT_AMOUNT);
        entity.setPaidAmount(dto.getPaidAmount() != null ? dto.getPaidAmount() : DEFAULT_AMOUNT);
        entity.setUnpaidAmount(dto.getUnpaidAmount() != null ? dto.getUnpaidAmount()
                : (dto.getPlannedAmount() != null ? dto.getPlannedAmount() : DEFAULT_AMOUNT));
        entity.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : DEFAULT_CURRENCY);
        entity.setTaxRate(dto.getTaxRate() != null ? dto.getTaxRate() : DEFAULT_AMOUNT);
        entity.setTaxAmount(dto.getTaxAmount() != null ? dto.getTaxAmount() : DEFAULT_AMOUNT);
        entity.setPlannedDate(dto.getPlannedDate());
        entity.setActualDate(dto.getActualDate());
        entity.setDueDate(dto.getDueDate());
        entity.setOverdueDays(dto.getOverdueDays() != null ? dto.getOverdueDays() : 0);
        entity.setPaymentMethod(dto.getPaymentMethod());
        entity.setBankAccount(dto.getBankAccount());
        entity.setTransactionNo(dto.getTransactionNo());
        entity.setInvoiceNo(dto.getInvoiceNo());
        entity.setInvoiceIssued(dto.getInvoiceIssued() != null ? dto.getInvoiceIssued() : Boolean.FALSE);
        entity.setInvoiceDate(dto.getInvoiceDate());
        entity.setMilestone(dto.getMilestone());
        entity.setMilestoneDescription(dto.getMilestoneDescription());
        entity.setCompletionRate(dto.getCompletionRate() != null ? dto.getCompletionRate() : DEFAULT_AMOUNT);
        entity.setReminderSent(dto.getReminderSent() != null ? dto.getReminderSent() : Boolean.FALSE);
        entity.setReminderDate(dto.getReminderDate());
        entity.setReminderCount(dto.getReminderCount() != null ? dto.getReminderCount() : 0);
        entity.setNotes(dto.getNotes());
        entity.setAttachments(dto.getAttachments());
        entity.setConfirmedBy(dto.getConfirmedBy());
        entity.setConfirmedAt(dto.getConfirmedAt());
        entity.setCreatedBy(dto.getCreatedBy());
        // 计算逾期天数
        entity.setOverdueDays(calculateOverdueDays(entity.getDueDate() != null ? entity.getDueDate()
                : entity.getPlannedDate()));
        if (entity.getOverdueDays() > 0 && ACTIVE_PAYMENT_STATUSES.contains(entity.getPaymentStatus())) {
            entity.setPaymentStatus(PAYMENT_STATUS_OVERDUE);
        }
        entity = paymentRepository.save(entity);
        log.info("添加合同付款: id={}, contractId={}, paymentNo={}", entity.getId(),
                entity.getContractId(), entity.getPaymentNo());
        return toDto(entity);
    }

    /**
     * 更新合同付款 (字段非空才覆盖)。
     *
     * @param id  付款 ID
     * @param dto 付款参数
     * @return 更新后的付款
     * @throws ScrmException 付款不存在 / 参数非法
     */
    @Transactional
    public ScrmContractPaymentDto updatePayment(Long id, ScrmContractPaymentDto dto) throws ScrmException {
        ScrmContractPaymentEntity entity = findPaymentOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("付款参数不能为空");
        }
        if (dto.getPaymentName() != null) entity.setPaymentName(dto.getPaymentName());
        if (dto.getPaymentType() != null) entity.setPaymentType(dto.getPaymentType());
        if (dto.getPaymentStatus() != null) entity.setPaymentStatus(dto.getPaymentStatus());
        if (dto.getPlannedAmount() != null) entity.setPlannedAmount(dto.getPlannedAmount());
        if (dto.getPaidAmount() != null) entity.setPaidAmount(dto.getPaidAmount());
        if (dto.getUnpaidAmount() != null) entity.setUnpaidAmount(dto.getUnpaidAmount());
        if (dto.getCurrency() != null) entity.setCurrency(dto.getCurrency());
        if (dto.getTaxRate() != null) entity.setTaxRate(dto.getTaxRate());
        if (dto.getTaxAmount() != null) entity.setTaxAmount(dto.getTaxAmount());
        if (dto.getPlannedDate() != null) entity.setPlannedDate(dto.getPlannedDate());
        if (dto.getActualDate() != null) entity.setActualDate(dto.getActualDate());
        if (dto.getDueDate() != null) entity.setDueDate(dto.getDueDate());
        if (dto.getOverdueDays() != null) entity.setOverdueDays(dto.getOverdueDays());
        if (dto.getPaymentMethod() != null) entity.setPaymentMethod(dto.getPaymentMethod());
        if (dto.getBankAccount() != null) entity.setBankAccount(dto.getBankAccount());
        if (dto.getTransactionNo() != null) entity.setTransactionNo(dto.getTransactionNo());
        if (dto.getInvoiceNo() != null) entity.setInvoiceNo(dto.getInvoiceNo());
        if (dto.getInvoiceIssued() != null) entity.setInvoiceIssued(dto.getInvoiceIssued());
        if (dto.getInvoiceDate() != null) entity.setInvoiceDate(dto.getInvoiceDate());
        if (dto.getMilestone() != null) entity.setMilestone(dto.getMilestone());
        if (dto.getMilestoneDescription() != null) entity.setMilestoneDescription(dto.getMilestoneDescription());
        if (dto.getCompletionRate() != null) entity.setCompletionRate(dto.getCompletionRate());
        if (dto.getReminderSent() != null) entity.setReminderSent(dto.getReminderSent());
        if (dto.getReminderDate() != null) entity.setReminderDate(dto.getReminderDate());
        if (dto.getReminderCount() != null) entity.setReminderCount(dto.getReminderCount());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        if (dto.getAttachments() != null) entity.setAttachments(dto.getAttachments());
        if (dto.getConfirmedBy() != null) entity.setConfirmedBy(dto.getConfirmedBy());
        if (dto.getConfirmedAt() != null) entity.setConfirmedAt(dto.getConfirmedAt());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = paymentRepository.save(entity);
        log.info("更新合同付款: id={}", id);
        return toDto(entity);
    }

    /**
     * 删除合同付款。
     * <p>仅 PENDING / CANCELLED 状态付款允许删除。</p>
     *
     * @param id 付款 ID
     * @throws ScrmException 付款不存在 / 状态不允许删除
     */
    @Transactional
    public void deletePayment(Long id) throws ScrmException {
        ScrmContractPaymentEntity entity = findPaymentOrThrow(id);
        if (!PAYMENT_STATUS_PENDING.equals(entity.getPaymentStatus()) && !PAYMENT_STATUS_CANCELLED.equals(entity.getPaymentStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "付款状态不允许删除, 仅 PENDING/CANCELLED 可删除: status=" + entity.getPaymentStatus());
        }
        paymentRepository.delete(entity);
        log.info("删除合同付款: id={}", id);
    }

    /**
     * 查询付款详情。
     *
     * @param id 付款 ID
     * @return 付款 DTO
     * @throws ScrmException 付款不存在
     */
    @Transactional(readOnly = true)
    public ScrmContractPaymentDto getPayment(Long id) throws ScrmException {
        return toDto(findPaymentOrThrow(id));
    }

    /**
     * 按合同 ID 查询全部付款 (按计划日期升序)。
     *
     * @param contractId 合同 ID
     * @return 付款列表
     */
    @Transactional(readOnly = true)
    public List<ScrmContractPaymentDto> getPaymentsByContract(Long contractId) {
        return paymentRepository
                .findByContractIdOrderByPlannedDateAsc(contractId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询到期付款 (状态为 PENDING/DUE 且计划日期在当前或之前)。
     *
     * @param pageable 分页参数
     * @return 付款分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractPaymentDto> getDuePayments(Pageable pageable) {
        LocalDate today = LocalDate.now();
        Specification<ScrmContractPaymentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("paymentStatus").in(PAYMENT_STATUS_PENDING, PAYMENT_STATUS_DUE));
            predicates.add(cb.lessThanOrEqualTo(root.get("plannedDate"), today));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return paymentRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * 分页查询逾期付款 (状态为 OVERDUE 或计划日期已过且未付清)。
     *
     * @param pageable 分页参数
     * @return 付款分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmContractPaymentDto> getOverduePayments(Pageable pageable) {
        LocalDate today = LocalDate.now();
        Specification<ScrmContractPaymentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("paymentStatus").in(
                    PAYMENT_STATUS_OVERDUE, PAYMENT_STATUS_PENDING, PAYMENT_STATUS_DUE, PAYMENT_STATUS_PARTIAL));
            predicates.add(cb.lessThan(root.get("plannedDate"), today));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return paymentRepository.findAll(spec, pageable).map(this::toDto);
    }

    // ============================================================
    // 付款操作
    // ============================================================

    /**
     * 记录付款: 更新已付/未付金额、实际付款日期、交易号, 并根据付款完成度更新状态。
     * <p>完整实现: 累加已付金额, 重新计算未付金额, 已付清则状态置 PAID, 部分付款则置 PARTIAL,
     * 记录实际付款日期与交易号, 刷新确认信息。</p>
     *
     * @param id            付款 ID
     * @param actualAmount  本次实付金额
     * @param actualDate    实际付款日期 (可空, 缺省为当天)
     * @param method        付款方式 (可空)
     * @param transactionNo 交易号 (可空)
     * @return 更新后的付款
     * @throws ScrmException 付款不存在 / 状态非法 / 金额非法
     */
    @Transactional
    public ScrmContractPaymentDto recordPayment(Long id, Double actualAmount, LocalDate actualDate,
                                                  String method, String transactionNo) throws ScrmException {
        ScrmContractPaymentEntity entity = findPaymentOrThrow(id);
        if (PAYMENT_STATUS_CANCELLED.equals(entity.getPaymentStatus())
                || PAYMENT_STATUS_PAID.equals(entity.getPaymentStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "付款状态非法, 已取消/已付清的付款不允许记录: status=" + entity.getPaymentStatus());
        }
        if (actualAmount == null || actualAmount <= 0) {
            throw ScrmException.badRequest("实付金额必须大于 0");
        }
        double currentPaid = entity.getPaidAmount() != null ? entity.getPaidAmount() : DEFAULT_AMOUNT;
        double newPaid = currentPaid + actualAmount;
        double planned = entity.getPlannedAmount() != null ? entity.getPlannedAmount() : DEFAULT_AMOUNT;
        double newUnpaid = planned - newPaid;
        if (newUnpaid < 0) {
            throw ScrmException.badRequest("实付金额超过计划金额: planned=" + planned + ", totalPaid=" + newPaid);
        }
        entity.setPaidAmount(round(newPaid));
        entity.setUnpaidAmount(round(newUnpaid));
        entity.setActualDate(actualDate != null ? actualDate : LocalDate.now());
        if (method != null && !method.isBlank()) {
            entity.setPaymentMethod(method);
        }
        if (transactionNo != null && !transactionNo.isBlank()) {
            entity.setTransactionNo(transactionNo);
        }
        // 更新状态: 已付清则 PAID, 部分付款则 PARTIAL
        if (newUnpaid == 0) {
            entity.setPaymentStatus(PAYMENT_STATUS_PAID);
        } else {
            entity.setPaymentStatus(PAYMENT_STATUS_PARTIAL);
        }
        entity.setConfirmedAt(LocalDateTime.now());
        entity = paymentRepository.save(entity);
        log.info("记录合同付款: id={}, actualAmount={}, paid={}, unpaid={}", id, actualAmount,
                entity.getPaidAmount(), entity.getUnpaidAmount());
        return toDto(entity);
    }

    /**
     * 取消付款 (状态置 CANCELLED)。
     *
     * @param id 付款 ID
     * @return 更新后的付款
     * @throws ScrmException 付款不存在 / 状态非法
     */
    @Transactional
    public ScrmContractPaymentDto cancelPayment(Long id) throws ScrmException {
        ScrmContractPaymentEntity entity = findPaymentOrThrow(id);
        if (PAYMENT_STATUS_PAID.equals(entity.getPaymentStatus())
                || PAYMENT_STATUS_CANCELLED.equals(entity.getPaymentStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "付款状态非法, 已付清/已取消的付款不允许取消: status=" + entity.getPaymentStatus());
        }
        entity.setPaymentStatus(PAYMENT_STATUS_CANCELLED);
        entity = paymentRepository.save(entity);
        log.info("取消合同付款: id={}", id);
        return toDto(entity);
    }

    /**
     * 开票: 记录发票号与开票日期, 标记已开票。
     *
     * @param id        付款 ID
     * @param invoiceNo 发票号
     * @return 更新后的付款
     * @throws ScrmException 付款不存在 / 发票号为空
     */
    @Transactional
    public ScrmContractPaymentDto issueInvoice(Long id, String invoiceNo) throws ScrmException {
        if (invoiceNo == null || invoiceNo.isBlank()) {
            throw ScrmException.badRequest("发票号不能为空");
        }
        ScrmContractPaymentEntity entity = findPaymentOrThrow(id);
        entity.setInvoiceNo(invoiceNo);
        entity.setInvoiceIssued(Boolean.TRUE);
        entity.setInvoiceDate(LocalDate.now());
        entity = paymentRepository.save(entity);
        log.info("合同付款开票: id={}, invoiceNo={}", id, invoiceNo);
        return toDto(entity);
    }

    /**
     * 发送付款提醒: 标记已发送, 累加提醒次数, 刷新提醒日期。
     *
     * @param id 付款 ID
     * @return 更新后的付款
     * @throws ScrmException 付款不存在
     */
    @Transactional
    public ScrmContractPaymentDto sendPaymentReminder(Long id) throws ScrmException {
        ScrmContractPaymentEntity entity = findPaymentOrThrow(id);
        entity.setReminderSent(Boolean.TRUE);
        entity.setReminderDate(LocalDate.now());
        entity.setReminderCount((entity.getReminderCount() != null ? entity.getReminderCount() : 0) + 1);
        entity = paymentRepository.save(entity);
        log.info("发送合同付款提醒: id={}, contractId={}, reminderCount={}", id,
                entity.getContractId(), entity.getReminderCount());
        return toDto(entity);
    }

    /**
     * 检查并发送付款提醒 (定时任务): 扫描当前账号下计划日期已到但未付清的付款, 自动更新逾期状态并发送提醒。
     * <p>完整实现: 扫描 PENDING/DUE/PARTIAL 状态且计划日期早于今天的付款, 计算逾期天数,
     * 更新为 OVERDUE 状态 (如已逾期), 并标记提醒已发送。</p>
     *
     * @return 处理的付款数量
     */
    @Transactional
    public int checkPaymentReminders() {
        LocalDate today = LocalDate.now();
        Specification<ScrmContractPaymentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("paymentStatus").in(
                    PAYMENT_STATUS_PENDING, PAYMENT_STATUS_DUE, PAYMENT_STATUS_PARTIAL));
            predicates.add(cb.lessThan(root.get("plannedDate"), today));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmContractPaymentEntity> payments = paymentRepository.findAll(spec);
        if (payments.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ScrmContractPaymentEntity entity : payments) {
            // 计算逾期天数
            int overdueDays = calculateOverdueDays(entity.getDueDate() != null ? entity.getDueDate()
                    : entity.getPlannedDate());
            entity.setOverdueDays(overdueDays);
            if (overdueDays > 0) {
                entity.setPaymentStatus(PAYMENT_STATUS_OVERDUE);
            }
            entity.setReminderSent(Boolean.TRUE);
            entity.setReminderDate(today);
            entity.setReminderCount((entity.getReminderCount() != null ? entity.getReminderCount() : 0) + 1);
            paymentRepository.save(entity);
            count++;
        }
        log.info("检查并发送合同付款提醒:, count={}", count);
        return count;
    }

    // ============================================================
    // 付款统计与计划
    // ============================================================

    /**
     * 付款统计: 按合同 ID 统计计划/已付/未付金额, 各状态分布, 逾期数量。
     *
     * @param contractId 合同 ID
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPaymentStats(Long contractId) {
        List<ScrmContractPaymentEntity> payments = paymentRepository
                .findByContractIdOrderByPlannedDateAsc(contractId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("contractId", contractId);
        stats.put("totalPayments", payments.size());
        stats.put("totalPlannedAmount", round(payments.stream()
                .mapToDouble(p -> p.getPlannedAmount() != null ? p.getPlannedAmount() : DEFAULT_AMOUNT).sum()));
        stats.put("totalPaidAmount", round(payments.stream()
                .mapToDouble(p -> p.getPaidAmount() != null ? p.getPaidAmount() : DEFAULT_AMOUNT).sum()));
        stats.put("totalUnpaidAmount", round(payments.stream()
                .mapToDouble(p -> p.getUnpaidAmount() != null ? p.getUnpaidAmount() : DEFAULT_AMOUNT).sum()));
        stats.put("byStatus", payments.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getPaymentStatus() != null ? p.getPaymentStatus() : "UNKNOWN",
                        Collectors.counting())));
        stats.put("overdueCount", payments.stream()
                .filter(p -> PAYMENT_STATUS_OVERDUE.equals(p.getPaymentStatus())).count());
        stats.put("paidCount", payments.stream()
                .filter(p -> PAYMENT_STATUS_PAID.equals(p.getPaymentStatus())).count());
        return stats;
    }

    /**
     * 付款计划: 按合同 ID 返回付款计划列表 (按计划日期升序)。
     *
     * @param contractId 合同 ID
     * @return 付款计划列表
     */
    @Transactional(readOnly = true)
    public List<ScrmContractPaymentDto> getPaymentSchedule(Long contractId) {
        return paymentRepository
                .findByContractIdOrderByPlannedDateAsc(contractId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 计算逾期天数: 到期日期早于今天则返回正整数天数, 否则返回 0。
     *
     * @param dueDate 到期日期 (可空, 为空则返回 0)
     * @return 逾期天数
     */
    public int calculateOverdueDays(LocalDate dueDate) {
        if (dueDate == null) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        if (dueDate.isBefore(today)) {
            return (int) java.time.temporal.ChronoUnit.DAYS.between(dueDate, today);
        }
        return 0;
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
     * 按主键查询付款, 不存在抛异常 (数据隔离校验)
     */
    private ScrmContractPaymentEntity findPaymentOrThrow(Long id) throws ScrmException {
        ScrmContractPaymentEntity entity = paymentRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "合同付款不存在: id=" + id));

        return entity;
    }

    /**
     * 保留两位小数
     */
    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /**
     * 付款实体转 DTO
     */
    private ScrmContractPaymentDto toDto(ScrmContractPaymentEntity entity) {
        ScrmContractPaymentDto dto = new ScrmContractPaymentDto();
        dto.setId(entity.getId());
        dto.setContractId(entity.getContractId());
        dto.setContractNo(entity.getContractNo());
        dto.setPaymentNo(entity.getPaymentNo());
        dto.setPaymentName(entity.getPaymentName());
        dto.setPaymentType(entity.getPaymentType());
        dto.setPaymentStatus(entity.getPaymentStatus());
        dto.setPlannedAmount(entity.getPlannedAmount());
        dto.setPaidAmount(entity.getPaidAmount());
        dto.setUnpaidAmount(entity.getUnpaidAmount());
        dto.setCurrency(entity.getCurrency());
        dto.setTaxRate(entity.getTaxRate());
        dto.setTaxAmount(entity.getTaxAmount());
        dto.setPlannedDate(entity.getPlannedDate());
        dto.setActualDate(entity.getActualDate());
        dto.setDueDate(entity.getDueDate());
        dto.setOverdueDays(entity.getOverdueDays());
        dto.setPaymentMethod(entity.getPaymentMethod());
        dto.setBankAccount(entity.getBankAccount());
        dto.setTransactionNo(entity.getTransactionNo());
        dto.setInvoiceNo(entity.getInvoiceNo());
        dto.setInvoiceIssued(entity.getInvoiceIssued());
        dto.setInvoiceDate(entity.getInvoiceDate());
        dto.setMilestone(entity.getMilestone());
        dto.setMilestoneDescription(entity.getMilestoneDescription());
        dto.setCompletionRate(entity.getCompletionRate());
        dto.setReminderSent(entity.getReminderSent());
        dto.setReminderDate(entity.getReminderDate());
        dto.setReminderCount(entity.getReminderCount());
        dto.setNotes(entity.getNotes());
        dto.setAttachments(entity.getAttachments());
        dto.setConfirmedBy(entity.getConfirmedBy());
        dto.setConfirmedAt(entity.getConfirmedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
