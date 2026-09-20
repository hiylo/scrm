/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmInvoiceApplyDto;
import org.hiylo.scrm.dto.ScrmInvoiceApproveDto;
import org.hiylo.scrm.dto.ScrmInvoiceDto;
import org.hiylo.scrm.dto.ScrmInvoiceRedFlushDto;
import org.hiylo.scrm.dto.ScrmInvoiceTemplateDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmInvoiceEntity;
import org.hiylo.scrm.entity.ScrmInvoiceTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmInvoiceRepository;
import org.hiylo.scrm.repository.ScrmInvoiceTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 发票管理服务。
 * <p>
 * 承载发票全生命周期管理能力: 发票申请 (生成编号/校验金额/创建记录) / 查询 / 审批 (通过/驳回/批量) /
 * 开具 (生成税控号码/设置日期, 模拟) / 寄送 (邮件/邮寄, 模拟) / 送达 / 作废 / 红冲 (创建红冲发票并关联原发票) /
 * PDF 获取 (模拟返回 URL), 发票模板增删改查/启停/使用统计, 以及发票统计 (总数/各类型/各状态/总金额/总税额/
 * 月度/客户/税务/趋势/作废率/红冲)。
 * </p>
 * <p>
 * 所有写操作写入当前用户归属账号, 实现数据隔离, 读操作通过
 * JPA Specification 始终按当前用户可见账号范围过滤。校验失败抛出 {@link ScrmException} 携带
 * 通用错误码 (NOT_FOUND / BAD_REQUEST / CONFLICT)。发票编号格式为 FP + 年月日 + 4 位序号,
 * 申请编号格式为 AP + 年月日 + 4 位序号。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmInvoiceService {

    // ==================== 发票状态 ====================
    /** 发票状态: 待审批 */
    private static final String INVOICE_STATUS_PENDING = "PENDING";
    /** 发票状态: 已审批 */
    private static final String INVOICE_STATUS_APPROVED = "APPROVED";
    /** 发票状态: 已开具 */
    private static final String INVOICE_STATUS_ISSUED = "ISSUED";
    /** 发票状态: 已发送 */
    private static final String INVOICE_STATUS_SENT = "SENT";
    /** 发票状态: 已送达 */
    private static final String INVOICE_STATUS_RECEIVED = "RECEIVED";
    /** 发票状态: 已作废 */
    private static final String INVOICE_STATUS_VOIDED = "VOIDED";
    /** 发票状态: 已红冲 */
    private static final String INVOICE_STATUS_RED_FLUSHED = "RED_FLUSHED";
    /** 发票状态: 已驳回 */
    private static final String INVOICE_STATUS_REJECTED = "REJECTED";

    // ==================== 交付状态 ====================
    /** 交付状态: 待发送 */
    private static final String DELIVERY_STATUS_PENDING = "PENDING";
    /** 交付状态: 已发送 */
    private static final String DELIVERY_STATUS_SENT = "SENT";
    /** 交付状态: 已送达 */
    private static final String DELIVERY_STATUS_DELIVERED = "DELIVERED";

    // ==================== 审批动作 ====================
    /** 审批动作: 通过 */
    private static final String APPROVE_ACTION_APPROVE = "APPROVE";
    /** 审批动作: 驳回 */
    private static final String APPROVE_ACTION_REJECT = "REJECT";

    // ==================== 发票类别 ====================
    /** 发票类别: 普通 */
    private static final String INVOICE_CATEGORY_NORMAL = "NORMAL";
    /** 发票类别: 红冲 */
    private static final String INVOICE_CATEGORY_RED = "RED";

    // ==================== 合法值集合 ====================
    /** 发票类型合法集合 */
    private static final Set<String> VALID_INVOICE_TYPES = new HashSet<>(Arrays.asList(
            "GENERAL", "SPECIAL", "ELECTRONIC", "PLAIN_DIGITAL", "RED_REDUCED"));
    /** 发票类别合法集合 */
    private static final Set<String> VALID_INVOICE_CATEGORIES = new HashSet<>(Arrays.asList(
            "NORMAL", "RED", "SPECIAL_VAT", "ELECTRONIC"));
    /** 抬头类型合法集合 */
    private static final Set<String> VALID_TITLE_TYPES = new HashSet<>(Arrays.asList("PERSONAL", "ENTERPRISE"));
    /** 交付方式合法集合 */
    private static final Set<String> VALID_DELIVERY_METHODS = new HashSet<>(Arrays.asList(
            "EMAIL", "MAIL", "SELF", "DIGITAL"));
    /** 可作废状态集合 */
    private static final List<String> VOIDABLE_STATUSES = Arrays.asList(
            INVOICE_STATUS_ISSUED, INVOICE_STATUS_SENT, INVOICE_STATUS_RECEIVED, INVOICE_STATUS_APPROVED);
    /** 可红冲状态集合 (已开具的发票允许红冲) */
    private static final List<String> RED_FLUSHABLE_STATUSES = Arrays.asList(
            INVOICE_STATUS_ISSUED, INVOICE_STATUS_SENT, INVOICE_STATUS_RECEIVED);

    // ==================== 常量 ====================
    /** 发票编号前缀 */
    private static final String INVOICE_NO_PREFIX = "FP";
    /** 申请编号前缀 */
    private static final String APPLICATION_NO_PREFIX = "AP";
    /** 编号日期格式 */
    private static final DateTimeFormatter NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 编号序号格式 (4 位, 前补零) */
    private static final String NO_SEQ_FORMAT = "%04d";
    /** 编号序号上限 (超过则进位到 5 位) */
    private static final int NO_SEQ_BOUND = 10000;
    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";
    /** 默认金额 */
    private static final double DEFAULT_AMOUNT = 0d;
    /** 默认税率 */
    private static final double DEFAULT_TAX_RATE = 0d;
    /** 模拟 PDF URL 前缀 */
    private static final String PDF_URL_PREFIX = "/scrm/invoices/pdf/";
    /** 模拟税控发票代码前缀 */
    private static final String INVOICE_CODE_PREFIX = "044";
    /** 月度统计日期格式 */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 发票实例数据访问层 */
    private final ScrmInvoiceRepository invoiceRepository;
    /** 发票模板数据访问层 */
    private final ScrmInvoiceTemplateRepository templateRepository;
    /** 客户数据访问层 (解析客户名称) */
    private final ScrmCustomerRepository customerRepository;

    // ============================================================
    // 发票申请与查询
    // ============================================================

    /**
     * 申请发票: 生成编号 → 校验金额 → 创建记录。
     * <p>生成发票编号 (FP+年月日+序号) 与申请编号 (AP+年月日+序号), 计算税额与价税合计,
     * 创建状态为 PENDING 的发票实例。客户名称从客户实体解析。</p>
     *
     * @param applyDto 申请请求
     * @return 创建后的发票
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmInvoiceDto applyInvoice(ScrmInvoiceApplyDto applyDto) throws ScrmException {
        if (applyDto == null) {
            throw ScrmException.badRequest("申请参数不能为空");
        }
        if (applyDto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (!VALID_INVOICE_TYPES.contains(applyDto.getInvoiceType())) {
            throw ScrmException.badRequest("发票类型非法: " + applyDto.getInvoiceType());
        }
        if (!VALID_TITLE_TYPES.contains(applyDto.getTitleType())) {
            throw ScrmException.badRequest("抬头类型非法: " + applyDto.getTitleType());
        }
        if (applyDto.getDeliveryMethod() != null && !VALID_DELIVERY_METHODS.contains(applyDto.getDeliveryMethod())) {
            throw ScrmException.badRequest("交付方式非法: " + applyDto.getDeliveryMethod());
        }
        double amount = applyDto.getAmount() != null ? applyDto.getAmount() : DEFAULT_AMOUNT;
        double taxRate = applyDto.getTaxRate() != null ? applyDto.getTaxRate() : DEFAULT_TAX_RATE;
        double taxAmount = round(amount * taxRate);
        double totalAmount = round(amount + taxAmount);
        String customerName = resolveCustomerName(applyDto.getCustomerId());
        LocalDateTime now = LocalDateTime.now();
        ScrmInvoiceEntity entity = new ScrmInvoiceEntity();
        entity.setInvoiceNo(generateInvoiceNo());
        entity.setApplicationNo(generateApplicationNo());
        entity.setInvoiceType(applyDto.getInvoiceType());
        entity.setInvoiceCategory(INVOICE_CATEGORY_NORMAL);
        entity.setTitleType(applyDto.getTitleType());
        entity.setInvoiceTitle(applyDto.getInvoiceTitle());
        entity.setTaxNumber(applyDto.getTaxNumber());
        entity.setCustomerId(applyDto.getCustomerId());
        entity.setCustomerName(customerName);
        entity.setOrderId(applyDto.getOrderId());
        entity.setAmount(amount);
        entity.setTaxRate(taxRate);
        entity.setTaxAmount(taxAmount);
        entity.setTotalAmount(totalAmount);
        entity.setDiscountAmount(0d);
        entity.setActualAmount(totalAmount);
        entity.setCurrency(DEFAULT_CURRENCY);
        entity.setInvoiceItems(applyDto.getInvoiceItems());
        entity.setStatus(INVOICE_STATUS_PENDING);
        entity.setApplyReason(applyDto.getApplyReason());
        entity.setAppliedBy(applyDto.getAppliedBy());
        entity.setAppliedAt(now);
        entity.setDeliveryMethod(applyDto.getDeliveryMethod());
        entity.setDeliveryStatus(DELIVERY_STATUS_PENDING);
        entity = invoiceRepository.save(entity);
        log.info("申请发票: id={}, invoiceNo={}, customerId={}", entity.getId(), entity.getInvoiceNo(),
                entity.getCustomerId());
        return toInvoiceDto(entity);
    }

    /**
     * 查询发票详情。
     *
     * @param id 发票 ID
     * @return 发票 DTO
     * @throws ScrmException 发票不存在
     */
    @Transactional(readOnly = true)
    public ScrmInvoiceDto getInvoice(Long id) throws ScrmException {
        return toInvoiceDto(findInvoiceOrThrow(id));
    }

    /**
     * 按发票编号查询发票。
     *
     * @param invoiceNo 发票编号
     * @return 发票 DTO
     * @throws ScrmException 发票不存在
     */
    @Transactional(readOnly = true)
    public ScrmInvoiceDto getInvoiceByNo(String invoiceNo) throws ScrmException {
        return toInvoiceDto(findInvoiceByNoOrThrow(invoiceNo));
    }

    /**
     * 分页查询发票, 支持按发票类型、客户、状态、日期范围与关键词过滤。
     *
     * @param invoiceType 发票类型过滤 (可空)
     * @param customerId  客户 ID 过滤 (可空)
     * @param status      状态过滤 (可空)
     * @param startTime   创建时间下限 (可空)
     * @param endTime     创建时间上限 (可空)
     * @param keyword     关键词过滤, 匹配发票编号/申请编号/抬头 (可空)
     * @param pageable    分页参数
     * @return 发票分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmInvoiceDto> listInvoices(String invoiceType, Long customerId, String status,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              String keyword, Pageable pageable) {
        Specification<ScrmInvoiceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (invoiceType != null && !invoiceType.isBlank()) {
                predicates.add(cb.equal(root.get("invoiceType"), invoiceType));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("invoiceNo")), like),
                        cb.like(cb.lower(root.get("applicationNo")), like),
                        cb.like(cb.lower(root.get("invoiceTitle")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return invoiceRepository.findAll(spec, pageable).map(this::toInvoiceDto);
    }

    /**
     * 分页查询客户发票列表。
     *
     * @param customerId 客户 ID
     * @param pageable  分页参数
     * @return 发票分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmInvoiceDto> getInvoicesByCustomer(Long customerId, Pageable pageable) {
        Specification<ScrmInvoiceEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("customerId"), customerId));
        return invoiceRepository.findAll(spec, pageable).map(this::toInvoiceDto);
    }

    /**
     * 按订单 ID 查询关联发票列表。
     *
     * @param orderId 订单 ID
     * @return 发票列表
     */
    @Transactional(readOnly = true)
    public List<ScrmInvoiceDto> getInvoicesByOrder(String orderId) {
        return invoiceRepository.findByOrderId(orderId).stream()

                .map(this::toInvoiceDto)
                .collect(Collectors.toList());
    }

    /**
     * 审批发票: 通过则流转至 APPROVED, 驳回则流转至 REJECTED。
     *
     * @param approveDto 审批请求
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法
     */
    @Transactional
    public ScrmInvoiceDto approveInvoice(ScrmInvoiceApproveDto approveDto) throws ScrmException {
        if (approveDto == null || approveDto.getInvoiceId() == null) {
            throw ScrmException.badRequest("审批参数不能为空");
        }
        if (approveDto.getAction() == null || approveDto.getAction().isBlank()) {
            throw ScrmException.badRequest("审批动作不能为空");
        }
        ScrmInvoiceEntity entity = findInvoiceOrThrow(approveDto.getInvoiceId());
        if (!INVOICE_STATUS_PENDING.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "发票状态非法, 仅 PENDING 可审批: currentStatus=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setApprovedBy(approveDto.getApprovedBy());
        entity.setApprovedAt(now);
        entity.setApprovalComment(approveDto.getComment());
        if (APPROVE_ACTION_APPROVE.equals(approveDto.getAction())) {
            entity.setStatus(INVOICE_STATUS_APPROVED);
        } else if (APPROVE_ACTION_REJECT.equals(approveDto.getAction())) {
            entity.setStatus(INVOICE_STATUS_REJECTED);
        } else {
            throw ScrmException.badRequest("审批动作非法: action=" + approveDto.getAction());
        }
        entity = invoiceRepository.save(entity);
        log.info("审批发票: id={}, action={}", approveDto.getInvoiceId(), approveDto.getAction());
        return toInvoiceDto(entity);
    }

    /**
     * 批量审批发票。
     *
     * @param invoiceIds 发票 ID 列表
     * @param action     审批动作: APPROVE / REJECT
     * @param comment    审批意见 (可空)
     * @return 审批成功的发票数量
     * @throws ScrmException 参数非法
     */
    @Transactional
    public int batchApprove(List<Long> invoiceIds, String action, String comment) throws ScrmException {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            throw ScrmException.badRequest("发票 ID 列表不能为空");
        }
        if (!APPROVE_ACTION_APPROVE.equals(action) && !APPROVE_ACTION_REJECT.equals(action)) {
            throw ScrmException.badRequest("审批动作非法: action=" + action);
        }
        int success = 0;
        for (Long invoiceId : invoiceIds) {
            ScrmInvoiceApproveDto approveDto = new ScrmInvoiceApproveDto();
            approveDto.setInvoiceId(invoiceId);
            approveDto.setAction(action);
            approveDto.setComment(comment);
            try {
                approveInvoice(approveDto);
                success++;
            } catch (ScrmException e) {
                log.warn("批量审批发票失败: invoiceId={}, error={}", invoiceId, e.getMessage());
            }
        }
        log.info("批量审批发票: total={}, success={}", invoiceIds.size(), success);
        return success;
    }

    /**
     * 开具发票: 生成税控发票号码 → 设置开票日期 → 更新状态 (模拟)。
     * <p>仅 APPROVED 状态发票可开具, 开具后状态流转至 ISSUED, 模拟生成发票代码/号码/校验码。</p>
     *
     * @param id       发票 ID
     * @param issuedBy 开票人 (可空)
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法
     */
    @Transactional
    public ScrmInvoiceDto issueInvoice(Long id, String issuedBy) throws ScrmException {
        ScrmInvoiceEntity entity = findInvoiceOrThrow(id);
        if (!INVOICE_STATUS_APPROVED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "发票状态非法, 仅 APPROVED 可开具: currentStatus=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        // 模拟生成税控发票代码/号码/校验码
        String datePart = LocalDate.now().format(NO_DATE_FORMAT);
        entity.setInvoiceCode(INVOICE_CODE_PREFIX + datePart);
        entity.setInvoiceNumber(String.format(NO_SEQ_FORMAT,
                (invoiceRepository.countByInvoiceNoStartingWith(
                         INVOICE_NO_PREFIX + datePart)) % NO_SEQ_BOUND));
        entity.setCheckCode(String.valueOf(System.currentTimeMillis()));
        entity.setInvoiceDate(LocalDate.now());
        entity.setIssuedBy(issuedBy);
        entity.setIssuedAt(now);
        entity.setInvoiceUrl(PDF_URL_PREFIX + entity.getId());
        entity.setStatus(INVOICE_STATUS_ISSUED);
        entity = invoiceRepository.save(entity);
        log.info("开具发票: id={}, invoiceNo={}", id, entity.getInvoiceNo());
        return toInvoiceDto(entity);
    }

    /**
     * 发送发票 (邮件/邮寄, 模拟): 交付状态置 SENT, 发票状态置 SENT。
     * <p>仅 ISSUED 状态发票可发送。</p>
     *
     * @param id 发票 ID
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法
     */
    @Transactional
    public ScrmInvoiceDto sendInvoice(Long id) throws ScrmException {
        ScrmInvoiceEntity entity = findInvoiceOrThrow(id);
        if (!INVOICE_STATUS_ISSUED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "发票状态非法, 仅 ISSUED 可发送: currentStatus=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setDeliveryStatus(DELIVERY_STATUS_SENT);
        entity.setSentAt(now);
        entity.setStatus(INVOICE_STATUS_SENT);
        entity = invoiceRepository.save(entity);
        log.info("发送发票: id={}, deliveryMethod={}", id, entity.getDeliveryMethod());
        return toInvoiceDto(entity);
    }

    /**
     * 标记发票已送达: 交付状态置 DELIVERED, 发票状态置 RECEIVED。
     *
     * @param id 发票 ID
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法
     */
    @Transactional
    public ScrmInvoiceDto markDelivered(Long id) throws ScrmException {
        ScrmInvoiceEntity entity = findInvoiceOrThrow(id);
        if (!INVOICE_STATUS_SENT.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "发票状态非法, 仅 SENT 可标记送达: currentStatus=" + entity.getStatus());
        }
        entity.setDeliveryStatus(DELIVERY_STATUS_DELIVERED);
        entity.setDeliveredAt(LocalDateTime.now());
        entity.setStatus(INVOICE_STATUS_RECEIVED);
        entity = invoiceRepository.save(entity);
        log.info("标记发票送达: id={}", id);
        return toInvoiceDto(entity);
    }

    /**
     * 作废发票: 状态流转至 VOIDED, 记录作废原因与人。
     * <p>仅已开具/已发送/已送达/已审批的发票允许作废。</p>
     *
     * @param id       发票 ID
     * @param reason   作废原因
     * @param voidedBy 作废人 (可空)
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法
     */
    @Transactional
    public ScrmInvoiceDto voidInvoice(Long id, String reason, String voidedBy) throws ScrmException {
        ScrmInvoiceEntity entity = findInvoiceOrThrow(id);
        if (!VOIDABLE_STATUSES.contains(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "发票状态非法, 当前状态不允许作废: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(INVOICE_STATUS_VOIDED);
        entity.setVoidReason(reason);
        entity.setVoidedBy(voidedBy);
        entity.setVoidedAt(LocalDateTime.now());
        entity = invoiceRepository.save(entity);
        log.info("作废发票: id={}, reason={}", id, reason);
        return toInvoiceDto(entity);
    }

    /**
     * 红冲发票: 创建红冲发票 (金额取负) → 关联原发票 → 更新原发票状态为 RED_FLUSHED。
     * <p>仅已开具的发票 (ISSUED/SENT/RECEIVED) 允许红冲。红冲发票类别为 RED,
     * originalInvoiceId 指向原发票, 原发票 redFlushInvoiceId 指向红冲发票。</p>
     *
     * @param redFlushDto 红冲请求
     * @return 红冲发票
     * @throws ScrmException 原发票不存在 / 状态非法
     */
    @Transactional
    public ScrmInvoiceDto redFlush(ScrmInvoiceRedFlushDto redFlushDto) throws ScrmException {
        if (redFlushDto == null || redFlushDto.getInvoiceId() == null) {
            throw ScrmException.badRequest("红冲参数不能为空");
        }
        if (redFlushDto.getReason() == null || redFlushDto.getReason().isBlank()) {
            throw ScrmException.badRequest("红冲原因不能为空");
        }
        ScrmInvoiceEntity original = findInvoiceOrThrow(redFlushDto.getInvoiceId());
        if (!RED_FLUSHABLE_STATUSES.contains(original.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "发票状态非法, 仅已开具发票可红冲: currentStatus=" + original.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        // 创建红冲发票 (金额取负)
        ScrmInvoiceEntity red = new ScrmInvoiceEntity();
        red.setInvoiceNo(generateInvoiceNo());
        red.setApplicationNo(generateApplicationNo());
        red.setInvoiceType(original.getInvoiceType());
        red.setInvoiceCategory(INVOICE_CATEGORY_RED);
        red.setTitleType(original.getTitleType());
        red.setInvoiceTitle(original.getInvoiceTitle());
        red.setTaxNumber(original.getTaxNumber());
        red.setBankName(original.getBankName());
        red.setBankAccount(original.getBankAccount());
        red.setCompanyAddress(original.getCompanyAddress());
        red.setCompanyPhone(original.getCompanyPhone());
        red.setCustomerId(original.getCustomerId());
        red.setCustomerName(original.getCustomerName());
        red.setCustomerContact(original.getCustomerContact());
        red.setCustomerPhone(original.getCustomerPhone());
        red.setCustomerEmail(original.getCustomerEmail());
        red.setOrderId(original.getOrderId());
        red.setContractId(original.getContractId());
        red.setAmount(-Math.abs(original.getAmount() != null ? original.getAmount() : 0d));
        red.setTaxRate(original.getTaxRate());
        red.setTaxAmount(-Math.abs(original.getTaxAmount() != null ? original.getTaxAmount() : 0d));
        red.setTotalAmount(-Math.abs(original.getTotalAmount() != null ? original.getTotalAmount() : 0d));
        red.setDiscountAmount(original.getDiscountAmount());
        red.setActualAmount(-Math.abs(original.getActualAmount() != null ? original.getActualAmount() : 0d));
        red.setCurrency(original.getCurrency());
        red.setInvoiceDate(LocalDate.now());
        red.setInvoiceItems(original.getInvoiceItems());
        red.setRemark(original.getRemark());
        red.setStatus(INVOICE_STATUS_ISSUED);
        red.setApplyReason(redFlushDto.getReason());
        red.setAppliedBy(redFlushDto.getRedFlushedBy());
        red.setAppliedAt(now);
        red.setIssuedBy(redFlushDto.getRedFlushedBy());
        red.setIssuedAt(now);
        red.setOriginalInvoiceId(original.getId());
        red.setInvoiceUrl(PDF_URL_PREFIX);
        red = invoiceRepository.save(red);
        // 更新原发票
        original.setStatus(INVOICE_STATUS_RED_FLUSHED);
        original.setRedFlushReason(redFlushDto.getReason());
        original.setRedFlushedBy(redFlushDto.getRedFlushedBy());
        original.setRedFlushedAt(now);
        original.setRedFlushInvoiceId(red.getId());
        invoiceRepository.save(original);
        log.info("红冲发票: originalId={}, redFlushId={}", original.getId(), red.getId());
        return toInvoiceDto(red);
    }

    /**
     * 获取发票 PDF (模拟返回 URL)。
     *
     * @param id 发票 ID
     * @return 发票 PDF URL
     * @throws ScrmException 发票不存在
     */
    @Transactional(readOnly = true)
    public String getInvoicePdf(Long id) throws ScrmException {
        ScrmInvoiceEntity entity = findInvoiceOrThrow(id);
        if (entity.getInvoiceUrl() != null && !entity.getInvoiceUrl().isBlank()) {
            return entity.getInvoiceUrl();
        }
        return PDF_URL_PREFIX + entity.getId();
    }

    /**
     * 修改发票 (仅 PENDING 状态允许修改核心字段)。
     *
     * @param id  发票 ID
     * @param dto 发票参数
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法 / 参数非法
     */
    @Transactional
    public ScrmInvoiceDto updateInvoice(Long id, ScrmInvoiceDto dto) throws ScrmException {
        ScrmInvoiceEntity entity = findInvoiceOrThrow(id);
        if (!INVOICE_STATUS_PENDING.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "发票状态非法, 仅 PENDING 可修改: currentStatus=" + entity.getStatus());
        }
        if (dto.getInvoiceType() != null && !VALID_INVOICE_TYPES.contains(dto.getInvoiceType())) {
            throw ScrmException.badRequest("发票类型非法: " + dto.getInvoiceType());
        }
        if (dto.getInvoiceCategory() != null && !VALID_INVOICE_CATEGORIES.contains(dto.getInvoiceCategory())) {
            throw ScrmException.badRequest("发票类别非法: " + dto.getInvoiceCategory());
        }
        if (dto.getTitleType() != null && !VALID_TITLE_TYPES.contains(dto.getTitleType())) {
            throw ScrmException.badRequest("抬头类型非法: " + dto.getTitleType());
        }
        if (dto.getDeliveryMethod() != null && !VALID_DELIVERY_METHODS.contains(dto.getDeliveryMethod())) {
            throw ScrmException.badRequest("交付方式非法: " + dto.getDeliveryMethod());
        }
        if (dto.getInvoiceType() != null) entity.setInvoiceType(dto.getInvoiceType());
        if (dto.getInvoiceCategory() != null) entity.setInvoiceCategory(dto.getInvoiceCategory());
        if (dto.getTitleType() != null) entity.setTitleType(dto.getTitleType());
        if (dto.getInvoiceTitle() != null) entity.setInvoiceTitle(dto.getInvoiceTitle());
        if (dto.getTaxNumber() != null) entity.setTaxNumber(dto.getTaxNumber());
        if (dto.getBankName() != null) entity.setBankName(dto.getBankName());
        if (dto.getBankAccount() != null) entity.setBankAccount(dto.getBankAccount());
        if (dto.getCompanyAddress() != null) entity.setCompanyAddress(dto.getCompanyAddress());
        if (dto.getCompanyPhone() != null) entity.setCompanyPhone(dto.getCompanyPhone());
        if (dto.getCustomerName() != null) entity.setCustomerName(dto.getCustomerName());
        if (dto.getCustomerContact() != null) entity.setCustomerContact(dto.getCustomerContact());
        if (dto.getCustomerPhone() != null) entity.setCustomerPhone(dto.getCustomerPhone());
        if (dto.getCustomerEmail() != null) entity.setCustomerEmail(dto.getCustomerEmail());
        if (dto.getOrderId() != null) entity.setOrderId(dto.getOrderId());
        if (dto.getContractId() != null) entity.setContractId(dto.getContractId());
        if (dto.getCurrency() != null) entity.setCurrency(dto.getCurrency());
        if (dto.getInvoiceItems() != null) entity.setInvoiceItems(dto.getInvoiceItems());
        if (dto.getRemark() != null) entity.setRemark(dto.getRemark());
        if (dto.getApplyReason() != null) entity.setApplyReason(dto.getApplyReason());
        if (dto.getAppliedBy() != null) entity.setAppliedBy(dto.getAppliedBy());
        if (dto.getDeliveryMethod() != null) entity.setDeliveryMethod(dto.getDeliveryMethod());
        if (dto.getDeliveryAddress() != null) entity.setDeliveryAddress(dto.getDeliveryAddress());
        if (dto.getDeliveryRecipient() != null) entity.setDeliveryRecipient(dto.getDeliveryRecipient());
        if (dto.getDeliveryPhone() != null) entity.setDeliveryPhone(dto.getDeliveryPhone());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        // 金额/税率变更时重算税额与价税合计
        if (dto.getAmount() != null || dto.getTaxRate() != null || dto.getDiscountAmount() != null) {
            double amount = dto.getAmount() != null ? dto.getAmount()
                    : (entity.getAmount() != null ? entity.getAmount() : 0d);
            double taxRate = dto.getTaxRate() != null ? dto.getTaxRate()
                    : (entity.getTaxRate() != null ? entity.getTaxRate() : 0d);
            double discount = dto.getDiscountAmount() != null ? dto.getDiscountAmount()
                    : (entity.getDiscountAmount() != null ? entity.getDiscountAmount() : 0d);
            double taxAmount = round(amount * taxRate);
            double totalAmount = round(amount + taxAmount);
            entity.setAmount(amount);
            entity.setTaxRate(taxRate);
            entity.setTaxAmount(taxAmount);
            entity.setTotalAmount(totalAmount);
            entity.setDiscountAmount(discount);
            entity.setActualAmount(round(totalAmount - discount));
        }
        entity = invoiceRepository.save(entity);
        log.info("修改发票: id={}", id);
        return toInvoiceDto(entity);
    }

    /**
     * 查询待审批发票 (状态为 PENDING)。
     *
     * @param pageable 分页参数
     * @return 待审批发票分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmInvoiceDto> getPendingInvoices(Pageable pageable) {
        Specification<ScrmInvoiceEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), INVOICE_STATUS_PENDING));
        return invoiceRepository.findAll(spec, pageable).map(this::toInvoiceDto);
    }

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建发票模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法 / 模板编码重复
     */
    @Transactional
    public ScrmInvoiceTemplateDto createTemplate(ScrmInvoiceTemplateDto dto) throws ScrmException {
        validateTemplateDto(dto, false);
        if (templateRepository.findByTemplateCode(dto.getTemplateCode()).isPresent()) {
            throw ScrmException.conflict("模板编码已存在: code=" + dto.getTemplateCode());
        }
        ScrmInvoiceTemplateEntity entity = new ScrmInvoiceTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setTemplateCode(dto.getTemplateCode());
        entity.setDescription(dto.getDescription());
        entity.setInvoiceType(dto.getInvoiceType());
        entity.setDefaultTaxRate(dto.getDefaultTaxRate() != null ? dto.getDefaultTaxRate() : 0.13);
        entity.setDefaultItems(dto.getDefaultItems());
        entity.setApplicableProducts(dto.getApplicableProducts());
        entity.setRemarks(dto.getRemarks());
        entity.setRequiredFields(dto.getRequiredFields());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setUsageCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("创建发票模板: id={}, templateName={}, invoiceType={}",
                entity.getId(), entity.getTemplateName(), entity.getInvoiceType());
        return toTemplateDto(entity);
    }

    /**
     * 更新发票模板 (字段非空才覆盖)。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法 / 模板编码重复
     */
    @Transactional
    public ScrmInvoiceTemplateDto updateTemplate(Long id, ScrmInvoiceTemplateDto dto) throws ScrmException {
        ScrmInvoiceTemplateEntity entity = findTemplateOrThrow(id);
        validateTemplateDto(dto, true);
        if (dto.getTemplateCode() != null && !dto.getTemplateCode().equals(entity.getTemplateCode())) {
            if (templateRepository.findByTemplateCode(dto.getTemplateCode()).isPresent()) {
                throw ScrmException.conflict("模板编码已存在: code=" + dto.getTemplateCode());
            }
            entity.setTemplateCode(dto.getTemplateCode());
        }
        if (dto.getTemplateName() != null) entity.setTemplateName(dto.getTemplateName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getInvoiceType() != null) entity.setInvoiceType(dto.getInvoiceType());
        if (dto.getDefaultTaxRate() != null) entity.setDefaultTaxRate(dto.getDefaultTaxRate());
        if (dto.getDefaultItems() != null) entity.setDefaultItems(dto.getDefaultItems());
        if (dto.getApplicableProducts() != null) entity.setApplicableProducts(dto.getApplicableProducts());
        if (dto.getRemarks() != null) entity.setRemarks(dto.getRemarks());
        if (dto.getRequiredFields() != null) entity.setRequiredFields(dto.getRequiredFields());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("更新发票模板: id={}", id);
        return toTemplateDto(entity);
    }

    /**
     * 删除发票模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void deleteTemplate(Long id) throws ScrmException {
        ScrmInvoiceTemplateEntity entity = findTemplateOrThrow(id);
        templateRepository.delete(entity);
        log.info("删除发票模板: id={}", id);
    }

    /**
     * 查询发票模板详情。
     *
     * @param id 模板 ID
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmInvoiceTemplateDto getTemplate(Long id) throws ScrmException {
        return toTemplateDto(findTemplateOrThrow(id));
    }

    /**
     * 按模板编码查询发票模板。
     *
     * @param code 模板编码
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmInvoiceTemplateDto getTemplateByCode(String code) throws ScrmException {
        ScrmInvoiceTemplateEntity entity = templateRepository.findByTemplateCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "发票模板不存在: code=" + code));

        return toTemplateDto(entity);
    }

    /**
     * 分页查询发票模板, 支持按发票类型与启用状态过滤。
     *
     * @param invoiceType 发票类型过滤 (可空)
     * @param enabled     启用状态过滤 (可空)
     * @param pageable    分页参数
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmInvoiceTemplateDto> listTemplates(String invoiceType, Boolean enabled, Pageable pageable) {
        Specification<ScrmInvoiceTemplateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (invoiceType != null && !invoiceType.isBlank()) {
                predicates.add(cb.equal(root.get("invoiceType"), invoiceType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return templateRepository.findAll(spec, pageable).map(this::toTemplateDto);
    }

    /**
     * 启用发票模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmInvoiceTemplateDto enableTemplate(Long id) throws ScrmException {
        ScrmInvoiceTemplateEntity entity = findTemplateOrThrow(id);
        entity.setEnabled(Boolean.TRUE);
        entity = templateRepository.save(entity);
        log.info("启用发票模板: id={}", id);
        return toTemplateDto(entity);
    }

    /**
     * 禁用发票模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmInvoiceTemplateDto disableTemplate(Long id) throws ScrmException {
        ScrmInvoiceTemplateEntity entity = findTemplateOrThrow(id);
        entity.setEnabled(Boolean.FALSE);
        entity = templateRepository.save(entity);
        log.info("禁用发票模板: id={}", id);
        return toTemplateDto(entity);
    }

    /**
     * 递增模板使用次数。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void incrementUsage(Long id) throws ScrmException {
        ScrmInvoiceTemplateEntity entity = findTemplateOrThrow(id);
        int usage = entity.getUsageCount() != null ? entity.getUsageCount() : 0;
        entity.setUsageCount(usage + 1);
        templateRepository.save(entity);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 发票统计: 总数 / 各类型 / 各状态 / 总金额 / 总税额。
     * <p>时间范围按发票创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getInvoiceStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmInvoiceEntity> invoices = listInvoicesByTimeRange(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", invoices.size());
        stats.put("byType", invoices.stream()
                .collect(Collectors.groupingBy(ScrmInvoiceEntity::getInvoiceType, Collectors.counting())));
        stats.put("byStatus", invoices.stream()
                .collect(Collectors.groupingBy(ScrmInvoiceEntity::getStatus, Collectors.counting())));
        stats.put("byCategory", invoices.stream()
                .collect(Collectors.groupingBy(ScrmInvoiceEntity::getInvoiceCategory, Collectors.counting())));
        stats.put("totalAmount", round(invoices.stream()
                .mapToDouble(i -> i.getAmount() != null ? i.getAmount() : 0d).sum()));
        stats.put("totalTaxAmount", round(invoices.stream()
                .mapToDouble(i -> i.getTaxAmount() != null ? i.getTaxAmount() : 0d).sum()));
        stats.put("totalActualAmount", round(invoices.stream()
                .mapToDouble(i -> i.getActualAmount() != null ? i.getActualAmount() : 0d).sum()));
        return stats;
    }

    /**
     * 月度统计: 指定月份的发票数量/金额/各状态分布。
     *
     * @param month 月份 (格式 yyyy-MM)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMonthlyStats(String month) {
        YearMonth ym = parseMonthOrThrow(month);
        LocalDateTime startTime = ym.atDay(1).atStartOfDay();
        LocalDateTime endTime = ym.atEndOfMonth().atTime(23, 59, 59);
        List<ScrmInvoiceEntity> invoices = listInvoicesByTimeRange(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("month", month);
        stats.put("total", invoices.size());
        stats.put("byStatus", invoices.stream()
                .collect(Collectors.groupingBy(ScrmInvoiceEntity::getStatus, Collectors.counting())));
        stats.put("totalAmount", round(invoices.stream()
                .mapToDouble(i -> i.getAmount() != null ? i.getAmount() : 0d).sum()));
        stats.put("totalTaxAmount", round(invoices.stream()
                .mapToDouble(i -> i.getTaxAmount() != null ? i.getTaxAmount() : 0d).sum()));
        return stats;
    }

    /**
     * 客户发票统计: 客户发票数 / 总金额 / 各状态分布。
     *
     * @param customerId 客户 ID
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerInvoiceStats(Long customerId) {
        Specification<ScrmInvoiceEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("customerId"), customerId));
        List<ScrmInvoiceEntity> invoices = invoiceRepository.findAll(spec);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("customerId", customerId);
        stats.put("total", invoices.size());
        stats.put("byStatus", invoices.stream()
                .collect(Collectors.groupingBy(ScrmInvoiceEntity::getStatus, Collectors.counting())));
        stats.put("byType", invoices.stream()
                .collect(Collectors.groupingBy(ScrmInvoiceEntity::getInvoiceType, Collectors.counting())));
        stats.put("totalAmount", round(invoices.stream()
                .mapToDouble(i -> i.getAmount() != null ? i.getAmount() : 0d).sum()));
        stats.put("totalTaxAmount", round(invoices.stream()
                .mapToDouble(i -> i.getTaxAmount() != null ? i.getTaxAmount() : 0d).sum()));
        return stats;
    }

    /**
     * 税务统计: 各税率分布 / 总税额 / 总金额。
     * <p>时间范围按发票创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTaxStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmInvoiceEntity> invoices = listInvoicesByTimeRange(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAmount", round(invoices.stream()
                .mapToDouble(i -> i.getAmount() != null ? i.getAmount() : 0d).sum()));
        stats.put("totalTaxAmount", round(invoices.stream()
                .mapToDouble(i -> i.getTaxAmount() != null ? i.getTaxAmount() : 0d).sum()));
        // 按税率分组
        Map<String, Double> taxByRate = new LinkedHashMap<>();
        invoices.stream()
                .collect(Collectors.groupingBy(i -> i.getTaxRate() != null ? String.valueOf(i.getTaxRate()) : "0"))
                .forEach((rate, list) -> taxByRate.put(rate, round(list.stream()
                        .mapToDouble(i -> i.getTaxAmount() != null ? i.getTaxAmount() : 0d).sum())));
        stats.put("taxByRate", taxByRate);
        Map<String, Long> countByRate = invoices.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getTaxRate() != null ? String.valueOf(i.getTaxRate()) : "0", Collectors.counting()));
        stats.put("countByRate", countByRate);
        return stats;
    }

    /**
     * 发票趋势: 过去 N 个月每月新增发票数与金额。
     *
     * @param months 月数
     * @return 趋势数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getInvoiceTrend(int months) {
        LocalDateTime startTime = LocalDateTime.now().minusMonths(months);
        Specification<ScrmInvoiceEntity> spec = (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
        List<ScrmInvoiceEntity> invoices = invoiceRepository.findAll(spec);
        Map<String, Long> countByMonth = invoices.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getCreateTime().format(MONTH_FORMAT), Collectors.counting()));
        Map<String, Double> amountByMonth = new LinkedHashMap<>();
        invoices.stream()
                .collect(Collectors.groupingBy(i -> i.getCreateTime().format(MONTH_FORMAT)))
                .forEach((month, list) -> amountByMonth.put(month, round(list.stream()
                        .mapToDouble(i -> i.getTotalAmount() != null ? i.getTotalAmount() : 0d).sum())));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("months", months);
        stats.put("countByMonth", countByMonth);
        stats.put("amountByMonth", amountByMonth);
        return stats;
    }

    /**
     * 作废率统计: 作废发票数 / 总数。
     *
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getVoidRate() {
        List<ScrmInvoiceEntity> invoices = invoiceRepository.findAll();
        long total = invoices.size();
        long voided = invoices.stream().filter(i -> INVOICE_STATUS_VOIDED.equals(i.getStatus())).count();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("voidedCount", voided);
        stats.put("voidRate", total > 0 ? round(voided * 100d / total) : 0d);
        return stats;
    }

    /**
     * 红冲统计: 红冲发票数 / 红冲金额。
     * <p>时间范围按发票创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRedFlushStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmInvoiceEntity> invoices = listInvoicesByTimeRange(startTime, endTime);
        List<ScrmInvoiceEntity> redFlushed = invoices.stream()
                .filter(i -> INVOICE_CATEGORY_RED.equals(i.getInvoiceCategory())
                        || INVOICE_STATUS_RED_FLUSHED.equals(i.getStatus()))
                .collect(Collectors.toList());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("redFlushCount", redFlushed.size());
        stats.put("redFlushAmount", round(redFlushed.stream()
                .mapToDouble(i -> i.getTotalAmount() != null ? i.getTotalAmount() : 0d).sum()));
        stats.put("totalInvoices", invoices.size());
        stats.put("redFlushRate", invoices.isEmpty() ? 0d
                : round(redFlushed.size() * 100d / invoices.size()));
        return stats;
    }

    // ============================================================
    // 编号生成
    // ============================================================

    /**
     * 生成发票编号 (FP + 年月日 + 4 位序号)。
     * <p>序号 = 当日已生成发票数 + 1, 超过 9999 则扩展为 5 位。</p>
     *
     * @return 发票编号
     */
    public String generateInvoiceNo() {
        String datePart = LocalDate.now().format(NO_DATE_FORMAT);
        String prefix = INVOICE_NO_PREFIX + datePart;
        long count = invoiceRepository.countByInvoiceNoStartingWith(prefix);
        long seq = count + 1;
        String seqPart = seq < NO_SEQ_BOUND
                ? String.format(NO_SEQ_FORMAT, seq)
                : String.valueOf(seq);
        return prefix + seqPart;
    }

    /**
     * 生成申请编号 (AP + 年月日 + 4 位序号)。
     *
     * @return 申请编号
     */
    public String generateApplicationNo() {
        String datePart = LocalDate.now().format(NO_DATE_FORMAT);
        String prefix = APPLICATION_NO_PREFIX + datePart;
        long count = invoiceRepository.countByApplicationNoStartingWith(prefix);
        long seq = count + 1;
        String seqPart = seq < NO_SEQ_BOUND
                ? String.format(NO_SEQ_FORMAT, seq)
                : String.valueOf(seq);
        return prefix + seqPart;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

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
     * 按时间范围查询当前账号发票列表 (统计用)。
     */
    private List<ScrmInvoiceEntity> listInvoicesByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmInvoiceEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return invoiceRepository.findAll(spec);
    }

    /**
     * 解析月份字符串 (yyyy-MM) 为 YearMonth, 非法时抛异常。
     */
    private YearMonth parseMonthOrThrow(String month) throws ScrmException {
        if (month == null || month.isBlank()) {
            throw ScrmException.badRequest("月份不能为空");
        }
        try {
            return YearMonth.parse(month, MONTH_FORMAT);
        } catch (Exception e) {
            throw ScrmException.badRequest("月份格式非法, 应为 yyyy-MM: " + month);
        }
    }

    /**
     * 校验模板参数。
     *
     * @param dto     模板参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTemplateDto(ScrmInvoiceTemplateDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模板参数不能为空");
        }
        if (!partial) {
            if (dto.getTemplateName() == null || dto.getTemplateName().isBlank()) {
                throw ScrmException.badRequest("模板名称不能为空");
            }
            if (dto.getTemplateCode() == null || dto.getTemplateCode().isBlank()) {
                throw ScrmException.badRequest("模板编码不能为空");
            }
            if (dto.getInvoiceType() == null || dto.getInvoiceType().isBlank()) {
                throw ScrmException.badRequest("发票类型不能为空");
            }
        }
        if (dto.getInvoiceType() != null && !VALID_INVOICE_TYPES.contains(dto.getInvoiceType())) {
            throw ScrmException.badRequest("发票类型非法: " + dto.getInvoiceType());
        }
    }

    /**
     * 按主键查询发票, 不存在抛异常
     */
    private ScrmInvoiceEntity findInvoiceOrThrow(Long id) throws ScrmException {
        ScrmInvoiceEntity entity = invoiceRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "发票不存在: id=" + id));

        return entity;
    }

    /**
     * 按发票编号查询发票, 不存在抛异常
     */
    private ScrmInvoiceEntity findInvoiceByNoOrThrow(String invoiceNo) throws ScrmException {
        ScrmInvoiceEntity entity = invoiceRepository.findByInvoiceNo(invoiceNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "发票不存在: invoiceNo=" + invoiceNo));

        return entity;
    }

    /**
     * 按主键查询模板, 不存在抛异常
     */
    private ScrmInvoiceTemplateEntity findTemplateOrThrow(Long id) throws ScrmException {
        ScrmInvoiceTemplateEntity entity = templateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "发票模板不存在: id=" + id));

        return entity;
    }

    /**
     * 保留两位小数。
     */
    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /**
     * 发票实体转 DTO
     */
    private ScrmInvoiceDto toInvoiceDto(ScrmInvoiceEntity entity) {
        ScrmInvoiceDto dto = new ScrmInvoiceDto();
        dto.setId(entity.getId());
        dto.setInvoiceNo(entity.getInvoiceNo());
        dto.setApplicationNo(entity.getApplicationNo());
        dto.setInvoiceType(entity.getInvoiceType());
        dto.setInvoiceCategory(entity.getInvoiceCategory());
        dto.setTitleType(entity.getTitleType());
        dto.setInvoiceTitle(entity.getInvoiceTitle());
        dto.setTaxNumber(entity.getTaxNumber());
        dto.setBankName(entity.getBankName());
        dto.setBankAccount(entity.getBankAccount());
        dto.setCompanyAddress(entity.getCompanyAddress());
        dto.setCompanyPhone(entity.getCompanyPhone());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setCustomerContact(entity.getCustomerContact());
        dto.setCustomerPhone(entity.getCustomerPhone());
        dto.setCustomerEmail(entity.getCustomerEmail());
        dto.setOrderId(entity.getOrderId());
        dto.setContractId(entity.getContractId());
        dto.setAmount(entity.getAmount());
        dto.setTaxRate(entity.getTaxRate());
        dto.setTaxAmount(entity.getTaxAmount());
        dto.setTotalAmount(entity.getTotalAmount());
        dto.setDiscountAmount(entity.getDiscountAmount());
        dto.setActualAmount(entity.getActualAmount());
        dto.setCurrency(entity.getCurrency());
        dto.setInvoiceDate(entity.getInvoiceDate());
        dto.setInvoiceItems(entity.getInvoiceItems());
        dto.setRemark(entity.getRemark());
        dto.setStatus(entity.getStatus());
        dto.setApplyReason(entity.getApplyReason());
        dto.setAppliedBy(entity.getAppliedBy());
        dto.setAppliedAt(entity.getAppliedAt());
        dto.setApprovedBy(entity.getApprovedBy());
        dto.setApprovedAt(entity.getApprovedAt());
        dto.setApprovalComment(entity.getApprovalComment());
        dto.setIssuedBy(entity.getIssuedBy());
        dto.setIssuedAt(entity.getIssuedAt());
        dto.setInvoiceUrl(entity.getInvoiceUrl());
        dto.setInvoiceImage(entity.getInvoiceImage());
        dto.setDeliveryMethod(entity.getDeliveryMethod());
        dto.setDeliveryStatus(entity.getDeliveryStatus());
        dto.setSentAt(entity.getSentAt());
        dto.setDeliveredAt(entity.getDeliveredAt());
        dto.setTrackingNumber(entity.getTrackingNumber());
        dto.setDeliveryAddress(entity.getDeliveryAddress());
        dto.setDeliveryRecipient(entity.getDeliveryRecipient());
        dto.setDeliveryPhone(entity.getDeliveryPhone());
        dto.setVoidReason(entity.getVoidReason());
        dto.setVoidedBy(entity.getVoidedBy());
        dto.setVoidedAt(entity.getVoidedAt());
        dto.setRedFlushReason(entity.getRedFlushReason());
        dto.setRedFlushedBy(entity.getRedFlushedBy());
        dto.setRedFlushedAt(entity.getRedFlushedAt());
        dto.setOriginalInvoiceId(entity.getOriginalInvoiceId());
        dto.setRedFlushInvoiceId(entity.getRedFlushInvoiceId());
        dto.setTaxBureauCode(entity.getTaxBureauCode());
        dto.setTaxBureauName(entity.getTaxBureauName());
        dto.setDeviceNo(entity.getDeviceNo());
        dto.setInvoiceCode(entity.getInvoiceCode());
        dto.setInvoiceNumber(entity.getInvoiceNumber());
        dto.setCheckCode(entity.getCheckCode());
        dto.setQrCode(entity.getQrCode());
        dto.setTags(entity.getTags());
        dto.setNotes(entity.getNotes());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 模板实体转 DTO
     */
    private ScrmInvoiceTemplateDto toTemplateDto(ScrmInvoiceTemplateEntity entity) {
        ScrmInvoiceTemplateDto dto = new ScrmInvoiceTemplateDto();
        dto.setId(entity.getId());
        dto.setTemplateName(entity.getTemplateName());
        dto.setTemplateCode(entity.getTemplateCode());
        dto.setDescription(entity.getDescription());
        dto.setInvoiceType(entity.getInvoiceType());
        dto.setDefaultTaxRate(entity.getDefaultTaxRate());
        dto.setDefaultItems(entity.getDefaultItems());
        dto.setApplicableProducts(entity.getApplicableProducts());
        dto.setRemarks(entity.getRemarks());
        dto.setRequiredFields(entity.getRequiredFields());
        dto.setEnabled(entity.getEnabled());
        dto.setUsageCount(entity.getUsageCount());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
