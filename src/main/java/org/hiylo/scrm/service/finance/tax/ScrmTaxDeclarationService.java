/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationService.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.tax;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.ScrmTaxCalendarProperties;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.dto.ScrmTaxDeclarationProfileDto;
import org.hiylo.scrm.dto.ScrmTaxDeclarationRecordDto;
import org.hiylo.scrm.entity.ScrmCalendarEventEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.hiylo.scrm.entity.ScrmTaxHolidayEntity;
import org.hiylo.scrm.entity.ScrmTaxWorkdayOverrideEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.service.CompanyResolver;
import org.hiylo.scrm.repository.ScrmCalendarEventRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationProfileRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationRecordRepository;
import org.hiylo.scrm.service.ScrmNotificationCenterService;
import org.hiylo.scrm.service.finance.FinanceDataScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 税种申报档案 + 申报记录状态机服务（报税日历域核心, T08A）。
 * <p>
 * 职责: 档案 CRUD（客户×税种×周期, 唯一键去重）、周期记录生成（月/季/年/汇算）、
 * 状态机推进（NOT_STARTED→PENDING、截止日 24:00 逾期判定、申报/缴款/补报/归档/取消）、
 * 日历事件落库（复用 {@code ScrmCalendarEventEntity}, eventType=TAX_DEADLINE/TAX_OVERDUE/
 * TAX_PAID, 按税种聚合着色）、逾期预警推送（截止日前 N 天推会计 + 到期日当天推销售 +
 * 逾期升级主管并建 HIGH 工单, 复用 NotificationCenter）。
 * </p>
 * <p>
 * 预警幂等: 同一记录同一触发节点只推一次（relatedType=TAX_RECORD + relatedId=记录ID +
 * 模板编码组合去重）。推送失败只记日志不抛出, 不阻断状态机主流程。申报期口径
 * 引用 T06 R-ACCT-0001/0003/0004/0007, 档案行可覆盖 deadlineRule（属地自定义）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmTaxDeclarationService {

    /** 截止日顺延查找上限（天），防止假期数据异常时死循环 */
    private static final int MAX_SHIFT_DAYS = 60;

    // ==================== 税种与周期常量 ====================

    /** 税种: 增值税 */
    public static final String TAX_VAT = "VAT";
    /** 税种: 个人所得税 */
    public static final String TAX_IIT = "IIT";
    /** 税种: 企业所得税 */
    public static final String TAX_CIT = "CIT";
    /** 税种: 社保 */
    public static final String TAX_SOCIAL_SECURITY = "SOCIAL_SECURITY";

    /** 周期: 月 */
    public static final String CYCLE_MONTHLY = "MONTHLY";
    /** 周期: 季 */
    public static final String CYCLE_QUARTERLY = "QUARTERLY";
    /** 周期: 年 */
    public static final String CYCLE_YEARLY = "YEARLY";
    /** 周期: 年度汇算 */
    public static final String CYCLE_ANNUAL_SETTLEMENT = "ANNUAL_SETTLEMENT";

    /** 征期规则: 次月/季终后 15 日 */
    public static final String DEADLINE_DAY15 = "DAY15";
    /** 征期规则: 汇算次年 3/31 */
    public static final String DEADLINE_DAY31 = "DAY31";
    /** 征期规则: 汇算次年 5/31 */
    public static final String DEADLINE_DAY05_31 = "DAY05_31";
    /** 征期规则: 属地自定义 */
    public static final String DEADLINE_LOCAL = "LOCAL";

    // ==================== 状态机常量 ====================

    /** 状态: 未开始 */
    public static final String STATUS_NOT_STARTED = "NOT_STARTED";
    /** 状态: 待申报 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态: 已申报 */
    public static final String STATUS_FILED = "FILED";
    /** 状态: 已缴 */
    public static final String STATUS_PAID = "PAID";
    /** 状态: 逾期 */
    public static final String STATUS_OVERDUE = "OVERDUE";
    /** 状态: 已归档 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    /** 状态: 已取消（减免/政策豁免） */
    public static final String STATUS_CANCELED = "CANCELED";

    /** 档案状态: 启用 */
    private static final String PROFILE_ACTIVE = "ACTIVE";

    // ==================== 日历事件常量 ====================

    /** 日历事件类型: 报税截止 */
    public static final String EVENT_TAX_DEADLINE = "TAX_DEADLINE";
    /** 日历事件类型: 报税逾期 */
    public static final String EVENT_TAX_OVERDUE = "TAX_OVERDUE";
    /** 日历事件类型: 报税已缴 */
    public static final String EVENT_TAX_PAID = "TAX_PAID";

    // ==================== 推送常量 ====================

    /** 推送关联类型: 申报记录（预警幂等键） */
    private static final String RELATED_TYPE_TAX_RECORD = "TAX_RECORD";

    /** 推送发送者 ID */
    private static final String SENDER_ID = "tax-system";

    /** 工单类别（升级督办, 复用既有合法类别之外的 TAX_OVERDUE 需服务端扩容, 用 OTHER 兜底） */
    private static final String TICKET_CATEGORY = "OTHER";

    /** 工单优先级（升级督办 HIGH） */
    private static final String TICKET_PRIORITY = "HIGH";

    // ==================== 依赖 ====================

    /** 申报档案数据访问层 */
    private final ScrmTaxDeclarationProfileRepository profileRepository;

    /** 申报记录数据访问层 */
    private final ScrmTaxDeclarationRecordRepository recordRepository;

    /** 日历事件数据访问层（TAX_* 展示事件落库） */
    private final ScrmCalendarEventRepository calendarEventRepository;

    /** 通知中心门面（站内信推送） */
    private final ScrmNotificationCenterService notificationCenterService;

    /** 通知记录数据访问层（推送幂等去重） */
    private final ScrmNotificationRepository notificationRepository;

    /** 客户数据访问层（客户名称补全） */
    private final ScrmCustomerRepository customerRepository;

    /** 公司主体归属解析器（业务数据自动挂公司） */
    private final CompanyResolver companyResolver;

    /** 公司主体数据访问层（按负责会计筛选主体集合） */
    private final org.hiylo.scrm.repository.ScrmCompanyRepository companyRepository;

    /** 工单服务（逾期 ≥5 天自动建 HIGH 工单） */
    private final org.hiylo.scrm.service.ScrmTicketService ticketService;

    /** 报税日历配置 */
    private final ScrmTaxCalendarProperties properties;

    /** 财税数据范围（会计只看到自己负责主体的申报记录） */
    private final FinanceDataScope financeDataScope;

    /** 税务假期仓储（法定节假日顺延，独立于营销节日表） */
    private final org.hiylo.scrm.repository.ScrmTaxHolidayRepository taxHolidayRepository;

    /** 调休上班日仓储（周末补班视为工作日，否则截止日会被误推） */
    private final org.hiylo.scrm.repository.ScrmTaxWorkdayOverrideRepository workdayOverrideRepository;

    // ============================================================
    // 档案管理
    // ============================================================

    /**
     * 创建申报档案。
     *
     * @param dto 档案参数
     * @return 创建后的档案
     * @throws ScrmException 参数非法 / 档案唯一键重复
     */
    @Transactional
    public ScrmTaxDeclarationProfileEntity createProfile(ScrmTaxDeclarationProfileDto dto)
            throws ScrmException {
        if (dto == null || dto.getCustomerId() == null) {
            throw ScrmException.badRequest("档案参数与客户 ID 不能为空");
        }
        String taxType = dto.getTaxType();
        if (taxType == null || taxType.isBlank()) {
            throw ScrmException.badRequest("税种不能为空");
        }
        String taxpayerType = dto.getTaxpayerType() != null ? dto.getTaxpayerType() : "SMALL";
        if (dto.getStartDate() == null) {
            throw ScrmException.badRequest("有效开始日期不能为空");
        }
        if (profileRepository.findByCustomerIdAndTaxTypeAndTaxpayerTypeAndStartDate(
                dto.getCustomerId(), taxType, taxpayerType, dto.getStartDate()).isPresent()) {
            throw ScrmException.conflict("该客户同税种同纳税人类型同开始日期的档案已存在");
        }
        ScrmTaxDeclarationProfileEntity entity = new ScrmTaxDeclarationProfileEntity();
        applyProfileDto(entity, dto);
        entity.setTaxpayerType(taxpayerType);
        if (entity.getDeclarationCycle() == null) {
            entity.setDeclarationCycle(defaultCycle(taxType));
        }
        if (entity.getDeadlineRule() == null) {
            entity.setDeadlineRule(defaultDeadlineRule(taxType, entity.getDeclarationCycle()));
        }
        if (entity.getStatus() == null) {
            entity.setStatus(PROFILE_ACTIVE);
        }
        if (entity.getCustomerName() == null || entity.getCustomerName().isBlank()) {
            entity.setCustomerName(resolveCustomerName(dto.getCustomerId()));
        }
        entity.setCreatedBy(operator());
        entity = profileRepository.save(entity);
        log.info("[报税日历-档案] 创建: id={}, customerId={}, taxType={}, cycle={}",
                entity.getId(), entity.getCustomerId(), entity.getTaxType(), entity.getDeclarationCycle());
        return entity;
    }

    /**
     * 更新申报档案（字段非空才覆盖）。
     *
     * @param id  档案 ID
     * @param dto 更新参数
     * @return 更新后的档案
     * @throws ScrmException 档案不存在 / 参数非法
     */
    @Transactional
    public ScrmTaxDeclarationProfileEntity updateProfile(Long id, ScrmTaxDeclarationProfileDto dto)
            throws ScrmException {
        ScrmTaxDeclarationProfileEntity entity = findProfileOrThrow(id);
        if (dto == null) {
            return entity;
        }
        if (dto.getCustomerName() != null) {
            entity.setCustomerName(dto.getCustomerName());
        }
        if (dto.getTaxType() != null) {
            entity.setTaxType(dto.getTaxType());
        }
        if (dto.getDeclarationCycle() != null) {
            entity.setDeclarationCycle(dto.getDeclarationCycle());
        }
        if (dto.getTaxpayerType() != null) {
            entity.setTaxpayerType(dto.getTaxpayerType());
        }
        if (dto.getDeadlineRule() != null) {
            entity.setDeadlineRule(dto.getDeadlineRule());
        }
        if (dto.getRegion() != null) {
            entity.setRegion(dto.getRegion());
        }
        if (dto.getStartDate() != null) {
            entity.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            entity.setEndDate(dto.getEndDate());
        }
        if (dto.getDeclarationOrg() != null) {
            entity.setDeclarationOrg(dto.getDeclarationOrg());
        }
        if (dto.getRemark() != null) {
            entity.setRemark(dto.getRemark());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getLastVerifiedAt() != null) {
            entity.setLastVerifiedAt(dto.getLastVerifiedAt());
        }
        return profileRepository.save(entity);
    }

    /**
     * 删除申报档案（不删除历史申报记录, 记录保持可查）。
     *
     * @param id 档案 ID
     * @throws ScrmException 档案不存在
     */
    @Transactional
    public void deleteProfile(Long id) throws ScrmException {
        ScrmTaxDeclarationProfileEntity entity = findProfileOrThrow(id);
        profileRepository.delete(entity);
        log.info("[报税日历-档案] 删除: id={}, customerId={}, taxType={}",
                id, entity.getCustomerId(), entity.getTaxType());
    }

    /**
     * 查询申报档案详情。
     *
     * @param id 档案 ID
     * @return 档案实体
     * @throws ScrmException 档案不存在
     */
    public ScrmTaxDeclarationProfileEntity getProfile(Long id) throws ScrmException {
        return findProfileOrThrow(id);
    }

    /**
     * 分页查询申报档案。
     * <p>
     * 支持公司维度与客户维度两个过滤条件，二者可同时使用（公司 + 其经办联系人）。
     * 统一走 Specification 查询，分页与排序下沉到数据库——此前客户维度分支是
     * 内存全量过滤后包装 PageImpl，数据量上来后分页结果会失真。
     * </p>
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param companyId  公司主体 ID 过滤（可空；代账场景主查询维度）
     * @param taxType    税种过滤（可空）
     * @param status     状态过滤（可空）
     * @param pageable   分页参数
     * @return 档案分页结果
     */
    public Page<ScrmTaxDeclarationProfileEntity> listProfiles(Long customerId, Long companyId,
                                                               Long accountantId, String taxType,
                                                               String status, Pageable pageable) {
        return profileRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            if (accountantId != null) {
                // 按负责会计筛选：先取该会计负责的主体集合, 再以 IN 下推到 SQL,
                // 避免先查档案再在内存里过滤（数据量上来会全表扫）
                List<Long> companyIds = companyRepository.findIdsByAccountantId(accountantId);
                if (companyIds.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("companyId").in(companyIds));
                }
            }
            if (taxType != null && !taxType.isBlank()) {
                predicates.add(cb.equal(root.get("taxType"), taxType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable);
    }

    // ============================================================
    // 申报记录生成与状态机
    // ============================================================

    /**
     * 生成下期申报记录（调度入口, 每日 01:00）。
     * <p>对全部 ACTIVE 档案按周期规则（月/季/年/汇算）生成尚未生成的当期记录,
     * 同一档案同一所属期唯一（uk_record_profile_period + 服务端先查后建）。</p>
     *
     * @return 生成本期记录数
     */
    @Transactional
    public int generateNextRecords() {
        LocalDate today = LocalDate.now();
        List<ScrmTaxDeclarationProfileEntity> profiles =
                profileRepository.findByStatus(PROFILE_ACTIVE);
        int created = 0;
        for (ScrmTaxDeclarationProfileEntity profile : profiles) {
            if (profile.getEndDate() != null && profile.getEndDate().isBefore(today)) {
                continue;
            }
            try {
                if (createCurrentRecordIfMissing(profile, today)) {
                    created++;
                }
            } catch (Exception e) {
                log.warn("[报税日历-生成] 档案记录生成失败, 跳过: profileId={}, err={}",
                        profile.getId(), e.getMessage());
            }
        }
        log.info("[报税日历-生成] 本期记录生成完成: profiles={}, created={}", profiles.size(), created);
        return created;
    }

    /**
     * 状态推进（调度入口, 每日 08:00）。
     * <p>NOT_STARTED → PENDING（档案激活 + 周期触达）; 截止日 24:00 未申报 → OVERDUE
     * （逾期天数随推进累加）; OVERDUE 持续 ≥ 阈值天自动升级（置 escalatedAt/escalatedTo
     * + 建 HIGH 工单）。</p>
     *
     * @return 推进汇总 [pending 数, overdue 数, escalated 数]
     */
    @Transactional
    public int[] advanceStates() {
        LocalDate today = LocalDate.now();
        int pending = 0;
        int overdue = 0;
        int escalated = 0;
        List<ScrmTaxDeclarationRecordEntity> all = recordRepository.findAll();
        for (ScrmTaxDeclarationRecordEntity record : all) {
            if (STATUS_OVERDUE.equals(record.getDeclarationStatus())) {
                int days = (int) java.time.temporal.ChronoUnit.DAYS.between(record.getDueDate(), today);
                record.setOverdueDays(Math.max(days, record.getOverdueDays() == null ? 0 : record.getOverdueDays()));
                if (days >= properties.getEscalateOverdueDays() && record.getEscalatedAt() == null) {
                    escalateRecord(record);
                    escalated++;
                }
                recordRepository.save(record);
                overdue++;
                continue;
            }
            if (STATUS_NOT_STARTED.equals(record.getDeclarationStatus())) {
                record.setDeclarationStatus(STATUS_PENDING);
                recordRepository.save(record);
                pending++;
                continue;
            }
            if (STATUS_PENDING.equals(record.getDeclarationStatus())
                    && record.getDueDate().isBefore(today)) {
                record.setDeclarationStatus(STATUS_OVERDUE);
                record.setOverdueDays((int) java.time.temporal.ChronoUnit.DAYS
                        .between(record.getDueDate(), today));
                if (record.getOverdueDays() >= properties.getEscalateOverdueDays()
                        && record.getEscalatedAt() == null) {
                    escalateRecord(record);
                    escalated++;
                }
                recordRepository.save(record);
                overdue++;
            }
        }
        log.info("[报税日历-推进] 状态推进完成: pending={}, overdue={}, escalated={}",
                pending, overdue, escalated);
        return new int[]{pending, overdue, escalated};
    }

    /**
     * 申报完成（会计动作）: PENDING/OVERDUE → FILED, 回填申报税额/凭证号/操作人。
     *
     * @param id         记录 ID
     * @param taxAmount  申报税额（可空）
     * @param receiptNo  完税凭证号（可空）
     * @param remark     备注（可空）
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
     */
    @Transactional
    public ScrmTaxDeclarationRecordEntity markFiled(Long id, java.math.BigDecimal taxAmount,
                                                    String receiptNo, String remark) throws ScrmException {
        ScrmTaxDeclarationRecordEntity record = findRecordOrThrow(id);
        String status = record.getDeclarationStatus();
        // NOT_STARTED 也允许申报：申报本身可以提前做（征期开始前即完成），
        // 而 NOT_STARTED → PENDING 的推进要等每日 08:00 调度，若只放行 PENDING
        // 会导致会计当天建档后点「申报完成」被拒，形成无谓等待。
        if (!STATUS_NOT_STARTED.equals(status) && !STATUS_PENDING.equals(status)
                && !STATUS_OVERDUE.equals(status)) {
            throw ScrmException.conflict("仅 NOT_STARTED / PENDING / OVERDUE 状态可申报完成: id=" + id
                    + ", status=" + status);
        }
        record.setDeclarationStatus(STATUS_FILED);
        record.setFiledAt(LocalDateTime.now());
        record.setFiledBy(operator());
        if (taxAmount != null) {
            record.setTaxAmount(taxAmount);
        }
        if (receiptNo != null) {
            record.setTaxReceiptNo(receiptNo);
        }
        if (remark != null) {
            record.setRemark(remark);
        }
        record = recordRepository.save(record);
        log.info("[报税日历-状态机] 申报完成: id={}, profileId={}, filedBy={}",
                record.getId(), record.getProfileId(), record.getFiledBy());
        return record;
    }

    /**
     * 缴款完成（会计动作）: FILED / OVERDUE → PAID, 回填实缴税额。
     *
     * @param id         记录 ID
     * @param paidAmount 实缴税额（可空）
     * @param receiptNo  完税凭证号（可空）
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
     */
    @Transactional
    public ScrmTaxDeclarationRecordEntity markPaid(Long id, java.math.BigDecimal paidAmount,
                                                   String receiptNo) throws ScrmException {
        ScrmTaxDeclarationRecordEntity record = findRecordOrThrow(id);
        String status = record.getDeclarationStatus();
        if (!STATUS_FILED.equals(status) && !STATUS_OVERDUE.equals(status)) {
            throw ScrmException.conflict("仅 FILED / OVERDUE 状态可缴款完成: id=" + id
                    + ", status=" + status);
        }
        record.setDeclarationStatus(STATUS_PAID);
        record.setPaidAt(LocalDateTime.now());
        if (paidAmount != null) {
            record.setPaidAmount(paidAmount);
        }
        if (receiptNo != null) {
            record.setTaxReceiptNo(receiptNo);
        }
        record.setOverdueDays(0);
        record = recordRepository.save(record);
        syncCalendarEvent(record, EVENT_TAX_PAID);
        log.info("[报税日历-状态机] 缴款完成: id={}, profileId={}", record.getId(), record.getProfileId());
        return record;
    }

    /**
     * 补报成功（OVERDUE → FILED 走申报完成, 再由缴款进 PAID; 此处提供直接补报进 PAID 的快捷动作）。
     *
     * @param id         记录 ID
     * @param taxAmount  申报税额
     * @param paidAmount 实缴税额
     * @param receiptNo  完税凭证号
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
     */
    @Transactional
    public ScrmTaxDeclarationRecordEntity markBackFilledPaid(Long id, java.math.BigDecimal taxAmount,
                                                             java.math.BigDecimal paidAmount,
                                                             String receiptNo) throws ScrmException {
        ScrmTaxDeclarationRecordEntity record = findRecordOrThrow(id);
        if (!STATUS_OVERDUE.equals(record.getDeclarationStatus())) {
            throw ScrmException.conflict("仅 OVERDUE 状态可补报: id=" + id
                    + ", status=" + record.getDeclarationStatus());
        }
        record.setDeclarationStatus(STATUS_PAID);
        record.setFiledAt(LocalDateTime.now());
        record.setFiledBy(operator());
        record.setPaidAt(LocalDateTime.now());
        if (taxAmount != null) {
            record.setTaxAmount(taxAmount);
        }
        if (paidAmount != null) {
            record.setPaidAmount(paidAmount);
        }
        if (receiptNo != null) {
            record.setTaxReceiptNo(receiptNo);
        }
        record.setOverdueDays(0);
        record = recordRepository.save(record);
        syncCalendarEvent(record, EVENT_TAX_PAID);
        log.info("[报税日历-状态机] 补报完成: id={}, profileId={}", record.getId(), record.getProfileId());
        return record;
    }

    /**
     * 期后归档: PAID → ARCHIVED（终态, 期后审计可查）。
     *
     * @param id 记录 ID
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
     */
    @Transactional
    public ScrmTaxDeclarationRecordEntity archive(Long id) throws ScrmException {
        ScrmTaxDeclarationRecordEntity record = findRecordOrThrow(id);
        if (!STATUS_PAID.equals(record.getDeclarationStatus())) {
            throw ScrmException.conflict("仅 PAID 状态可归档: id=" + id
                    + ", status=" + record.getDeclarationStatus());
        }
        record.setDeclarationStatus(STATUS_ARCHIVED);
        return recordRepository.save(record);
    }

    /**
     * 取消记录（减免/政策豁免）: 任意非终态 → CANCELED（终态）。
     *
     * @param id     记录 ID
     * @param remark 取消原因
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
     */
    @Transactional
    public ScrmTaxDeclarationRecordEntity cancel(Long id, String remark) throws ScrmException {
        ScrmTaxDeclarationRecordEntity record = findRecordOrThrow(id);
        String status = record.getDeclarationStatus();
        if (STATUS_ARCHIVED.equals(status) || STATUS_CANCELED.equals(status) || STATUS_PAID.equals(status)) {
            throw ScrmException.conflict("终态/已缴记录不可取消: id=" + id + ", status=" + status);
        }
        record.setDeclarationStatus(STATUS_CANCELED);
        if (remark != null) {
            record.setRemark(remark);
        }
        return recordRepository.save(record);
    }

    // ============================================================
    // 预警推送（调度入口, 每日 09:00）
    // ============================================================

    /**
     * 逾期预警推送（调度入口, 每日 09:00）。
     * <p>按 T08A §3.1 节奏: PENDING 且距截止日 ∈ {7,3,1} 天推会计（幂等）;
     * 截止日当天 PENDING 推会计 + 销售; OVERDUE 0~4 天每天推会计 + 销售;
     * OVERDUE ≥5 天（或已由状态推进升级）推主管（升级由 advanceStates 建工单,
     * 此处补推主管站内信）。</p>
     *
     * @return 推送记录数
     */
    @Transactional
    public int pushWarnings() {
        LocalDate today = LocalDate.now();
        int pushed = 0;
        List<Integer> remindDays = parseRemindDays();
        for (int day : remindDays) {
            LocalDate target = today.plusDays(day);
            List<ScrmTaxDeclarationRecordEntity> records =
                    recordRepository.findByDeclarationStatusAndDueDateBetween(
                            STATUS_PENDING, target, target);
            for (ScrmTaxDeclarationRecordEntity record : records) {
                if (pushOnce(record, "remind-d" + day,
                        "距截止还有 " + day + " 天", String.valueOf(day), 0)) {
                    pushed++;
                }
            }
        }
        // 截止日当天 + 逾期 0~4 天（逐日推, 幂等键含日期）
        List<ScrmTaxDeclarationRecordEntity> dueToday =
                recordRepository.findByDeclarationStatusAndDueDateBetween(STATUS_PENDING, today, today);
        for (ScrmTaxDeclarationRecordEntity record : dueToday) {
            if (pushOnce(record, "due-today", "今日到期", "0", 0)) {
                pushed++;
            }
        }
        List<ScrmTaxDeclarationRecordEntity> overdue =
                recordRepository.findOverdueByStatus(STATUS_OVERDUE);
        for (ScrmTaxDeclarationRecordEntity record : overdue) {
            int days = record.getOverdueDays() == null ? 0 : record.getOverdueDays();
            if (days < properties.getEscalateOverdueDays()) {
                if (pushOnce(record, "overdue-d" + today, "已逾期 " + days + " 天",
                        String.valueOf(days), 0)) {
                    pushed++;
                }
            } else if (record.getEscalatedAt() != null) {
                if (pushOnce(record, "escalated-d" + today, "已逾期 " + days + " 天（已升级主管）",
                        String.valueOf(days), 2)) {
                    pushed++;
                }
            }
        }
        log.info("[报税日历-预警] 预警推送完成: pushed={}", pushed);
        return pushed;
    }

    /**
     * 预警推送单条（幂等: 同一记录同一触发节点只推一次, relatedId=记录ID+节点后缀）。
     *
     * @param record     申报记录
     * @param nodeKey    触发节点键（remind-d7 / remind-d3 / remind-d1 / due-today / overdue-d<date>）
     * @param statusDesc 状态描述
     * @param daysText   天数文本
     * @param priority   通知优先级
     * @return 是否实际推送
     */
    private boolean pushOnce(ScrmTaxDeclarationRecordEntity record, String nodeKey,
                             String statusDesc, String daysText, int priority) {
        String relatedId = record.getId() + ":" + nodeKey;
        try {
            if (notificationRepository.existsByRelatedTypeAndRelatedIdAndTemplateCode(
                    RELATED_TYPE_TAX_RECORD, relatedId, properties.getRemindTemplateCode())) {
                return false;
            }
            ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
            dto.setTemplateCode(properties.getRemindTemplateCode());
            dto.setRecipientType("USER");
            // 代账口径：提醒发给「负责该主体的会计」；未指派公司时回落配置的默认收件人，
            // 保证存量数据（company_id 为空）行为与改动前完全一致。
            Long accountantId = companyResolver.resolveAccountantId(
                    profileRepository.findById(record.getProfileId())
                            .map(ScrmTaxDeclarationProfileEntity::getCompanyId)
                            .orElse(null));
            dto.setRecipients(List.of(accountantId != null
                    ? String.valueOf(accountantId) : properties.getNotifyRecipient()));
            dto.setSenderId(SENDER_ID);
            dto.setSenderName("报税系统");
            dto.setPriority(priority);
            dto.setRelatedType(RELATED_TYPE_TAX_RECORD);
            dto.setRelatedId(relatedId);
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("customerName", resolveCustomerName(record.getCustomerId()));
            variables.put("taxTypeLabel", taxTypeLabel(record.getTaxType()));
            variables.put("periodLabel", record.getPeriodStart() + " ~ " + record.getPeriodEnd());
            variables.put("dueDate", String.valueOf(record.getDueDate()));
            variables.put("statusLabel", statusDesc);
            variables.put("daysLeft", daysText);
            variables.put("overdueDays", String.valueOf(record.getOverdueDays()));
            variables.put("region", record.getRegion() != null ? record.getRegion() : "");
            dto.setVariables(variables);
            notificationCenterService.sendNotification(dto);
            return true;
        } catch (Exception e) {
            log.warn("[报税日历-预警] 推送失败 (不影响主流程): recordId={}, node={}, err={}",
                    record.getId(), nodeKey, e.getMessage());
            return false;
        }
    }

    // ============================================================
    // 日历事件落库（复用 ScrmCalendarEventEntity）
    // ============================================================

    /**
     * 同步日历事件（截止日 TAX_DEADLINE / 逾期 TAX_OVERDUE / 已缴 TAX_PAID）。
     * <p>按记录聚合生成展示事件: 非逾期=截止日事件（税种着色）, 逾期=红色加粗醒目标识
     * 「逾期 N 天」, 已缴=缴款日事件。同一记录同一事件类型幂等（去重查询先查后建）。</p>
     *
     * @param record    申报记录
     * @param eventType 事件类型
     */
    @Transactional
    public void syncCalendarEvent(ScrmTaxDeclarationRecordEntity record, String eventType) {
        if (!properties.isWriteCalendarEvents()) {
            return;
        }
        try {
            String title;
            String color;
            LocalDate date;
            if (EVENT_TAX_OVERDUE.equals(eventType)) {
                title = "【报税逾期】" + resolveCustomerName(record.getCustomerId())
                        + " " + taxTypeLabel(record.getTaxType())
                        + " 逾期 " + (record.getOverdueDays() == null ? 0 : record.getOverdueDays()) + " 天";
                color = "#f5222d";
                date = record.getDueDate();
            } else if (EVENT_TAX_PAID.equals(eventType)) {
                title = "已缴 " + taxTypeLabel(record.getTaxType())
                        + " " + record.getPeriodStart() + "~" + record.getPeriodEnd();
                color = "#52c41a";
                date = record.getPaidAt() != null ? record.getPaidAt().toLocalDate() : record.getDueDate();
            } else {
                title = "报税截止 " + taxTypeLabel(record.getTaxType())
                        + " " + record.getPeriodStart() + "~" + record.getPeriodEnd();
                color = taxColor(record.getTaxType());
                date = record.getDueDate();
            }
            boolean exists = calendarEventRepository.findAll().stream()
                    .anyMatch(e -> eventType.equals(e.getEventType())
                            && title.equals(e.getEventTitle())
                            && e.getStartDate() != null && e.getStartDate().equals(date));
            if (exists) {
                return;
            }
            ScrmCalendarEventEntity event = new ScrmCalendarEventEntity();
            event.setEventTitle(title);
            event.setEventType(eventType);
            event.setDescription("申报记录 ID: " + record.getId() + ", 客户: "
                    + resolveCustomerName(record.getCustomerId()));
            event.setStartDate(date);
            event.setEndDate(date);
            event.setIsAllDay(Boolean.TRUE);
            event.setIsRecurring(Boolean.FALSE);
            event.setStatus("PLANNED");
            event.setPriority(0);
            event.setColor(color);
            event.setOwnerId(properties.getNotifyRecipient());
            event.setOwnerName("会计组");
            event.setTags("报税," + record.getTaxType());
            event.setCreatedBy(SENDER_ID);
            calendarEventRepository.save(event);
        } catch (Exception e) {
            log.warn("[报税日历-日历] 日历事件同步失败 (不影响主流程): recordId={}, eventType={}, err={}",
                    record.getId(), eventType, e.getMessage());
        }
    }

    // ============================================================
    // 查询
    // ============================================================

    /**
     * 查询申报记录详情。
     *
     * @param id 记录 ID
     * @return 记录实体
     * @throws ScrmException 记录不存在
     */
    public ScrmTaxDeclarationRecordEntity getRecord(Long id) throws ScrmException {
        return findRecordOrThrow(id);
    }

    /**
     * 按档案查询申报记录（会计工作台时间线）。
     *
     * @param profileId 档案 ID
     * @return 记录列表（按所属期升序）
     */
    public List<ScrmTaxDeclarationRecordEntity> listRecordsByProfile(Long profileId) {
        return recordRepository.findByProfileIdOrderByPeriodStartAsc(profileId);
    }

    /**
     * 按客户查询申报记录（客户时间线钻取）。
     *
     * @param customerId 客户 ID
     * @return 记录列表（按所属期升序）
     */
    public List<ScrmTaxDeclarationRecordEntity> listRecordsByCustomer(Long customerId) {
        return recordRepository.findByCustomerIdOrderByPeriodStartAsc(customerId);
    }

    /**
     * 按公司主体查询申报记录（公司 360 / 代账工作台的记录钻取）。
     * <p>
     * 经由「公司 → 档案 → 记录」两级关联取数，不使用记录表上的冗余字段，
     * 保证主体归属变更后查询结果同步正确。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @return 申报记录列表（按所属期升序）；公司下无档案时返回空列表
     */
    public List<ScrmTaxDeclarationRecordEntity> listRecordsByCompany(Long companyId) {
        if (companyId == null) {
            return List.of();
        }
        List<Long> profileIds = profileRepository
                .findByCompanyIdOrderByCreateTimeDesc(companyId).stream()
                .map(ScrmTaxDeclarationProfileEntity::getId)
                .collect(Collectors.toList());
        if (profileIds.isEmpty()) {
            return List.of();
        }
        return recordRepository.findByProfileIdInOrderByPeriodStartAsc(profileIds);
    }

    /**
     * 报税日历月视图聚合（前端月视图数据源: 非逾期按税种着色, 逾期红色加粗）。
     * <p>
     * 返回该月<b>全部状态</b>的申报记录（待申报 / 已申报 / 已缴款 / 逾期 / 已归档），
     * 仅排除已取消。此前只查 {@code PENDING}，导致会计在月中完成申报后记录从月历上消失，
     * 无法回溯当月办结情况。
     * </p>
     *
     * @param year  年份
     * @param month 月份（1-12）
     * @return 记录列表（按截止日升序）
     */
    public List<ScrmTaxDeclarationRecordEntity> listRecordsByMonth(int year, int month) {
        LocalDate from = YearMonth.of(year, month).atDay(1);
        LocalDate to = YearMonth.of(year, month).atEndOfMonth();
        List<ScrmTaxDeclarationRecordEntity> records = recordRepository.findByDueDateBetween(from, to);
        // 财税数据范围：会计只能看到自己负责主体的申报记录。
        // 记录表只存 profileId，主体归属要经 profile.companyId 反查，
        // 因此先按可见主体取该月可见的档案 ID 集合，再过滤记录。
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        if (accessibleCompanyIds == null) {
            return records.stream()
                    .filter(r -> !STATUS_CANCELED.equals(r.getDeclarationStatus()))
                    .collect(Collectors.toList());
        }
        if (accessibleCompanyIds.isEmpty()) {
            return List.of();
        }
        java.util.Set<Long> accessibleProfileIds = profileRepository
                .findByCompanyIdIn(accessibleCompanyIds).stream()
                .map(org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity::getId)
                .collect(Collectors.toSet());
        return records.stream()
                .filter(r -> !STATUS_CANCELED.equals(r.getDeclarationStatus()))
                .filter(r -> accessibleProfileIds.contains(r.getProfileId()))
                .collect(Collectors.toList());
    }

    /**
     * 解析月历默认应展示的年月。
     * <p>
     * 为什么需要：月历此前固定默认本月，但申报记录的截止日通常落在<b>次月</b>
     * （如 10 月所属期在 11 月 15 日截止），于是会计月初打开页面经常看到空白月，
     * 只能自己翻。这里按「本月 → 下一个有记录的月 → 最近一个有记录的月」依次回落，
     * 保证默认视图尽量有内容。
     * </p>
     *
     * @return [year, month]；库中无任何记录时返回当前年月
     */
    @Transactional(readOnly = true)
    public int[] suggestDefaultMonth() {
        YearMonth now = YearMonth.now();
        List<Object[]> rows = recordRepository.findDistinctDueMonths();
        if (rows.isEmpty()) {
            return new int[]{now.getYear(), now.getMonthValue()};
        }
        YearMonth next = null;
        YearMonth latest = null;
        for (Object[] row : rows) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            YearMonth candidate = YearMonth.of(year, month);
            if (!candidate.isBefore(now)) {
                if (next == null || candidate.isBefore(next)) {
                    next = candidate;
                }
            }
            if (latest == null || candidate.isAfter(latest)) {
                latest = candidate;
            }
        }
        YearMonth chosen = next != null ? next : latest;
        return new int[]{chosen.getYear(), chosen.getMonthValue()};
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 档案默认申报周期（按税种）。
     *
     * @param taxType 税种
     * @return 默认周期
     */
    private String defaultCycle(String taxType) {
        if (TAX_VAT.equals(taxType)) {
            return CYCLE_QUARTERLY; // 小规模默认季报
        }
        if (TAX_CIT.equals(taxType)) {
            return CYCLE_QUARTERLY; // 企业所得税默认季预缴
        }
        if (TAX_SOCIAL_SECURITY.equals(taxType)) {
            return CYCLE_MONTHLY;
        }
        return CYCLE_MONTHLY; // IIT 默认月代扣
    }

    /**
     * 档案默认征期规则（按税种 + 周期）。
     *
     * @param taxType 税种
     * @param cycle   周期
     * @return 默认征期规则
     */
    private String defaultDeadlineRule(String taxType, String cycle) {
        if (CYCLE_ANNUAL_SETTLEMENT.equals(cycle)) {
            return TAX_CIT.equals(taxType) ? DEADLINE_DAY05_31 : DEADLINE_DAY31;
        }
        return DEADLINE_DAY15;
    }

    /**
     * 生成当期记录（先查后建, 唯一键防重复）。
     *
     * @param profile 档案
     * @param today   当日
     * @return 是否新建
     */
    private boolean createCurrentRecordIfMissing(ScrmTaxDeclarationProfileEntity profile, LocalDate today) {
        LocalDate periodStart = currentPeriodStart(profile.getDeclarationCycle(), today);
        LocalDate periodEnd = currentPeriodEnd(profile.getDeclarationCycle(), periodStart);
        if (periodStart == null) {
            return false;
        }
        if (recordRepository.findByProfileIdAndPeriodStartAndPeriodEnd(
                profile.getId(), periodStart, periodEnd).isPresent()) {
            return false;
        }
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setProfileId(profile.getId());
        record.setCustomerId(profile.getCustomerId());
        record.setTaxType(profile.getTaxType());
        record.setRegion(profile.getRegion());
        record.setPeriodStart(periodStart);
        record.setPeriodEnd(periodEnd);
        record.setDueDate(calcDueDate(profile, periodEnd));
        record.setDeclarationStatus(STATUS_NOT_STARTED);
        record.setOverdueDays(0);
        record.setSource("SYSTEM");
        record.setCreatedBy("tax-system");
        recordRepository.save(record);
        return true;
    }

    /**
     * 当前所属期开始（月: 当月 1 日; 季: 季首月 1 日; 年/汇算: 1 月 1 日; 超出档案生效范围返回 null）。
     *
     * @param cycle 周期
     * @param today 当日
     * @return 所属期开始
     */
    private LocalDate currentPeriodStart(String cycle, LocalDate today) {
        if (CYCLE_MONTHLY.equals(cycle)) {
            return today.withDayOfMonth(1);
        }
        if (CYCLE_QUARTERLY.equals(cycle)) {
            int month = ((today.getMonthValue() - 1) / 3) * 3 + 1;
            return LocalDate.of(today.getYear(), month, 1);
        }
        if (CYCLE_YEARLY.equals(cycle) || CYCLE_ANNUAL_SETTLEMENT.equals(cycle)) {
            return LocalDate.of(today.getYear(), 1, 1);
        }
        return null;
    }

    /**
     * 所属期结束（月: 月末; 季: 季末; 年/汇算: 12/31; 汇算记录指向上一纳税年度）。
     *
     * @param cycle       周期
     * @param periodStart 所属期开始
     * @return 所属期结束
     */
    private LocalDate currentPeriodEnd(String cycle, LocalDate periodStart) {
        if (CYCLE_MONTHLY.equals(cycle)) {
            return periodStart.withDayOfMonth(periodStart.lengthOfMonth());
        }
        if (CYCLE_QUARTERLY.equals(cycle)) {
            return periodStart.plusMonths(2).withDayOfMonth(
                    periodStart.plusMonths(2).lengthOfMonth());
        }
        if (CYCLE_YEARLY.equals(cycle)) {
            return LocalDate.of(periodStart.getYear(), 12, 31);
        }
        if (CYCLE_ANNUAL_SETTLEMENT.equals(cycle)) {
            // 汇算: 当期记录在次年 1 月生成, 所属期是上一纳税年度
            return LocalDate.of(periodStart.getYear() - 1, 12, 31);
        }
        return periodStart;
    }

    /**
     * 按征期规则计算申报截止日期（节假日顺延: 截止日逢周末/法定节假日顺延到下一工作日, 最小实现）。
     *
     * @param profile    档案
     * @param periodEnd  所属期结束
     * @return 截止日期
     */
    private LocalDate calcDueDate(ScrmTaxDeclarationProfileEntity profile, LocalDate periodEnd) {
        String rule = profile.getDeadlineRule() != null ? profile.getDeadlineRule() : DEADLINE_DAY15;
        LocalDate base;
        if (DEADLINE_DAY31.equals(rule)) {
            base = LocalDate.of(periodEnd.getYear() + 1, 3, 31);
        } else if (DEADLINE_DAY05_31.equals(rule)) {
            base = LocalDate.of(periodEnd.getYear() + 1, 5, 31);
        } else if (DEADLINE_LOCAL.equals(rule)) {
            // 属地自定义: 默认按 DAY15 兜底, 人工复核 lastVerifiedAt 后可覆盖
            base = periodEnd.plusDays(15);
        } else {
            base = periodEnd.plusDays(15);
        }
        return nextWorkday(base);
    }

    /**
     * 截止日顺延到下一工作日。
     * <p>
     * 工作日 = 非周六日 <b>且</b> 不在公共假期内。假期取自
     * {@code scrm_calendar_holiday}（{@code holiday_type='PUBLIC_HOLIDAY'} 且启用），
     * {@code duration_days} 为含起始日的连续天数：国庆 10-01 起 7 天 ⇒ 10-01~10-07 均非工作日。
     * </p>
     * <p>
     * 属地税务机关临时公告的顺延不自动处理（是否生效、影响哪些税种需人工判断），
     * 由会计在申报档案上直接调整截止日。
     * </p>
     *
     * @param date 原始截止日
     * @return 顺延后的截止日
     */
    private LocalDate nextWorkday(LocalDate date) {
        // 一次取足顺延窗口内的假期与补班日，避免循环里反复查库
        LocalDate windowEnd = date.plusDays(MAX_SHIFT_DAYS);
        Set<LocalDate> holidays = loadHolidays(date, windowEnd);
        Set<LocalDate> extraWorkdays = loadWorkdayOverrides(date, windowEnd);
        LocalDate result = date;
        for (int i = 0; i < MAX_SHIFT_DAYS; i++) {
            // 调休补班日优先：即便落在周末也视为工作日
            if (extraWorkdays.contains(result)) {
                return result;
            }
            boolean weekend = result.getDayOfWeek().getValue() >= 6;
            if (!weekend && !holidays.contains(result)) {
                return result;
            }
            result = result.plusDays(1);
        }
        log.warn("[报税-截止日] 顺延超过 {} 天仍未找到工作日, 原样返回: date={}", MAX_SHIFT_DAYS, date);
        return date;
    }

    /**
     * 加载顺延窗口内的法定节假日日期集合。
     * <p>
     * 假期用起止区间存储，这里按天展开成集合，供逐日判断。
     * 查库失败时退化为空集合（只按周末顺延），保证申报记录仍能生成——
     * 假期表不可用不应导致整个报税日历停摆。
     * </p>
     *
     * @param from 窗口起点
     * @param to   窗口终点
     * @return 假期日期集合
     */
    private Set<LocalDate> loadHolidays(LocalDate from, LocalDate to) {
        try {
            List<ScrmTaxHolidayEntity> holidays = taxHolidayRepository.findActiveBetween(from, to);
            Set<LocalDate> dates = new java.util.HashSet<>();
            for (ScrmTaxHolidayEntity holiday : holidays) {
                LocalDate cursor = holiday.getStartDate();
                while (cursor != null && holiday.getEndDate() != null
                        && !cursor.isAfter(holiday.getEndDate())) {
                    dates.add(cursor);
                    cursor = cursor.plusDays(1);
                }
            }
            return dates;
        } catch (RuntimeException e) {
            log.warn("[报税-截止日] 加载法定节假日失败, 本次仅按周末顺延", e);
            return Set.of();
        }
    }

    /**
     * 加载顺延窗口内的调休上班日。
     *
     * @param from 窗口起点
     * @param to   窗口终点
     * @return 需上班的日期集合
     */
    private Set<LocalDate> loadWorkdayOverrides(LocalDate from, LocalDate to) {
        try {
            Set<LocalDate> dates = new java.util.HashSet<>();
            for (ScrmTaxWorkdayOverrideEntity override : workdayOverrideRepository.findActiveBetween(from, to)) {
                if (override.getWorkDate() != null) {
                    dates.add(override.getWorkDate());
                }
            }
            return dates;
        } catch (RuntimeException e) {
            log.warn("[报税-截止日] 加载调休上班日失败, 本次忽略补班", e);
            return Set.of();
        }
    }

    /**
     * 逾期记录升级: 置 escalatedAt/escalatedTo + 建 HIGH 工单（复用 ScrmTicketService）。
     *
     * @param record 逾期记录
     */
    private void escalateRecord(ScrmTaxDeclarationRecordEntity record) {
        record.setEscalatedAt(LocalDateTime.now());
        record.setEscalatedTo(properties.getNotifyRecipient());
        try {
            org.hiylo.scrm.dto.ScrmTicketDto dto = new org.hiylo.scrm.dto.ScrmTicketDto();
            dto.setTitle("报税逾期升级督办: " + resolveCustomerName(record.getCustomerId())
                    + " " + taxTypeLabel(record.getTaxType())
                    + " " + record.getPeriodStart() + "~" + record.getPeriodEnd());
            dto.setDescription("已逾期 " + (record.getOverdueDays() == null ? 0 : record.getOverdueDays())
                    + " 天未申报, 截止日 " + record.getDueDate() + ", 请主管介入定责并跟进补报。");
            dto.setCustomerId(record.getCustomerId());
            dto.setCustomerName(resolveCustomerName(record.getCustomerId()));
            dto.setCategory(TICKET_CATEGORY);
            dto.setPriority(TICKET_PRIORITY);
            dto.setAssigneeId(properties.getNotifyRecipient());
            dto.setAssigneeName("会计主管");
            dto.setSource("SYSTEM");
            dto.setCreatedBy("tax-system");
            ticketService.createTicket(dto);
            log.warn("[报税日历-升级] 逾期升级督办: recordId={}, customerId={}, overdueDays={}",
                    record.getId(), record.getCustomerId(), record.getOverdueDays());
        } catch (Exception e) {
            log.warn("[报税日历-升级] 升级工单创建失败 (不阻断状态推进): recordId={}, err={}",
                    record.getId(), e.getMessage());
        }
    }

    /**
     * 解析预警节奏（逗号分隔天数, 去重升序）。
     *
     * @return 预警天数列表
     */
    private List<Integer> parseRemindDays() {
        String raw = properties.getRemindDaysBefore() != null
                ? properties.getRemindDaysBefore() : "7,3,1";
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Integer::valueOf)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 税种中文标签。
     *
     * @param taxType 税种
     * @return 中文标签
     */
    public static String taxTypeLabel(String taxType) {
        if (TAX_VAT.equals(taxType)) {
            return "增值税";
        }
        if (TAX_IIT.equals(taxType)) {
            return "个人所得税";
        }
        if (TAX_CIT.equals(taxType)) {
            return "企业所得税";
        }
        if (TAX_SOCIAL_SECURITY.equals(taxType)) {
            return "社保";
        }
        return taxType;
    }

    /**
     * 税种日历颜色（VAT 蓝 / IIT 绿 / CIT 橙 / 社保 紫）。
     *
     * @param taxType 税种
     * @return 颜色
     */
    private String taxColor(String taxType) {
        if (TAX_VAT.equals(taxType)) {
            return "#1677ff";
        }
        if (TAX_IIT.equals(taxType)) {
            return "#52c41a";
        }
        if (TAX_CIT.equals(taxType)) {
            return "#fa8c16";
        }
        return "#722ed1";
    }

    /**
     * 档案 DTO 应用（创建场景）。
     *
     * @param entity 实体
     * @param dto    参数
     */
    private void applyProfileDto(ScrmTaxDeclarationProfileEntity entity, ScrmTaxDeclarationProfileDto dto) {
        entity.setCustomerId(dto.getCustomerId());
        // 业务数据自动挂公司（联系人已归属公司时自动带上）
        entity.setCompanyId(companyResolver.resolveByCustomerId(dto.getCustomerId()));
        entity.setCustomerName(dto.getCustomerName());
        entity.setTaxType(dto.getTaxType());
        entity.setDeclarationCycle(dto.getDeclarationCycle());
        entity.setTaxpayerType(dto.getTaxpayerType());
        entity.setDeadlineRule(dto.getDeadlineRule());
        entity.setRegion(dto.getRegion());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setDeclarationOrg(dto.getDeclarationOrg());
        entity.setRemark(dto.getRemark());
        entity.setStatus(dto.getStatus());
        entity.setLastVerifiedAt(dto.getLastVerifiedAt());
    }

    /**
     * 查询档案, 不存在抛 404。
     *
     * @param id 档案 ID
     * @return 档案实体
     * @throws ScrmException 档案不存在
     */
    private ScrmTaxDeclarationProfileEntity findProfileOrThrow(Long id) throws ScrmException {
        if (id == null) {
            throw ScrmException.badRequest("档案 ID 不能为空");
        }
        return profileRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("税种申报档案不存在: id=" + id));
    }

    /**
     * 查询记录, 不存在抛 404。
     *
     * @param id 记录 ID
     * @return 记录实体
     * @throws ScrmException 记录不存在
     */
    private ScrmTaxDeclarationRecordEntity findRecordOrThrow(Long id) throws ScrmException {
        if (id == null) {
            throw ScrmException.badRequest("申报记录 ID 不能为空");
        }
        return recordRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("税种申报记录不存在: id=" + id));
    }

    /**
     * 客户名称（冗余字段兜底解析）。
     *
     * @param customerId 客户 ID
     * @return 客户名称
     */
    private String resolveCustomerName(Long customerId) {
        try {
            return customerRepository.findById(customerId)
                    .map(c -> c.getNickname() != null ? c.getNickname() : "客户#" + customerId)
                    .orElse("客户#" + customerId);
        } catch (Exception e) {
            return "客户#" + customerId;
        }
    }

    /**
     * 当前操作人。
     *
     * @return 操作人 ID, 缺省 system
     */
    private String operator() {
        String userId = org.hiylo.scrm.config.UserContext.getUserId();
        return userId != null && !userId.isBlank() ? userId : "system";
    }
}
