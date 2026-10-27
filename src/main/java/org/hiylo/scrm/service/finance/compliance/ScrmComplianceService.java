/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmComplianceService.java
 * Date : 2026/10/25 10:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.compliance;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.ScrmComplianceProperties;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.entity.ScrmAnnualReportEntity;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmQualificationEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAnnualReportRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmQualificationRepository;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.service.CompanyResolver;
import org.hiylo.scrm.service.ScrmNotificationCenterService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合规域服务：工商年报 + 资质许可到期管理。
 * <p>
 * <b>为什么这两件事放在一起</b>：它们同属「代账公司的法定提醒责任」——
 * 晚提醒一次，客户就可能进异常名录或被主管部门处罚，责任会回溯到代账方。
 * 因此两者共用同一套「生成 → 推进 → 提醒 → 回填」节奏。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmComplianceService {

    /** 通知关联类型：工商年报 */
    private static final String RELATED_ANNUAL = "ANNUAL_REPORT";
    /** 通知关联类型：资质许可 */
    private static final String RELATED_QUALIFICATION = "QUALIFICATION";
    /** 通知发送人 */
    private static final String SENDER = "compliance-system";

    /** 年报仓库 */
    private final ScrmAnnualReportRepository annualReportRepository;

    /** 资质仓库 */
    private final ScrmQualificationRepository qualificationRepository;

    /** 公司主体仓库（取在服企业与负责会计） */
    private final ScrmCompanyRepository companyRepository;

    /** 通知中心门面 */
    private final ScrmNotificationCenterService notificationCenterService;

    /** 通知仓库（提醒幂等去重） */
    private final ScrmNotificationRepository notificationRepository;

    /** 公司归属解析器（提醒发给负责会计） */
    private final CompanyResolver companyResolver;

    /** 合规域配置 */
    private final ScrmComplianceProperties properties;

    /** 财税数据范围（会计只看到自己负责主体的年报与资质） */
    private final org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    // ============================================================
    // 工商年报：生成 / 推进 / 提醒 / 回填
    // ============================================================

    /**
     * 生成指定年度的工商年报任务（调度入口，默认每年 1 月 1 日）。
     * <p>
     * 为所有「在服且未终止」的企业生成年报记录，截止日固定为次年 6 月 30 日。
     * 幂等：同一公司同一年度只生成一条（唯一键 + 先查后建）。
     * </p>
     *
     * @param reportYear 报送年度（如 2025 表示 2025 年度报告）
     * @return 本次生成条数
     */
    @Transactional
    public int generateAnnualReports(int reportYear) {
        LocalDate dueDate = LocalDate.of(reportYear + 1, 6, 30);
        List<ScrmCompanyEntity> companies = companyRepository.findAll();
        int created = 0;
        for (ScrmCompanyEntity company : companies) {
            // 已终止服务的公司不再代办法定报送
            if (ScrmCompanyEntity.SERVICE_TERMINATED.equals(company.getServiceStatus())) {
                continue;
            }
            if (annualReportRepository
                    .findByCompanyIdAndReportYear(company.getId(), reportYear).isPresent()) {
                continue;
            }
            ScrmAnnualReportEntity entity = new ScrmAnnualReportEntity();
            entity.setCompanyId(company.getId());
            entity.setCompanyName(company.getCompanyName());
            entity.setReportYear(reportYear);
            entity.setDueDate(dueDate);
            entity.setStatus(ScrmAnnualReportEntity.STATUS_PENDING);
            entity.setCreatedBy("SYSTEM");
            annualReportRepository.save(entity);
            created++;
        }
        log.info("[合规-年报生成] 年度={}, 生成={} 条", reportYear, created);
        return created;
    }

    /**
     * 推进年报状态（调度入口，每日）：到期未报送 → OVERDUE。
     *
     * @return 本次推进为逾期的条数
     */
    @Transactional
    public int advanceAnnualReports() {
        LocalDate today = LocalDate.now();
        List<ScrmAnnualReportEntity> pending = annualReportRepository
                .findByStatusAndDueDateBetween(ScrmAnnualReportEntity.STATUS_PENDING,
                        LocalDate.of(2000, 1, 1), today.minusDays(1));
        for (ScrmAnnualReportEntity entity : pending) {
            entity.setStatus(ScrmAnnualReportEntity.STATUS_OVERDUE);
            annualReportRepository.save(entity);
        }
        if (!pending.isEmpty()) {
            log.info("[合规-年报推进] 转逾期 {} 条", pending.size());
        }
        return pending.size();
    }

    /**
     * 推送年报提醒（调度入口，每日）。
     * <p>
     * 提醒对象：距截止日还有 N 天内的 PENDING 记录 + 已逾期的 OVERDUE 记录。
     * 收件人优先取主体负责会计，未指派回落到配置默认收件人。
     * </p>
     *
     * @return 本次推送条数
     */
    @Transactional
    public int pushAnnualReminders() {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(properties.getAnnualRemindDaysBefore());
        List<ScrmAnnualReportEntity> targets = new ArrayList<>(
                annualReportRepository.findByStatusAndDueDateBetween(
                        ScrmAnnualReportEntity.STATUS_PENDING, today, horizon));
        targets.addAll(annualReportRepository.findByStatusAndDueDateBetween(
                ScrmAnnualReportEntity.STATUS_OVERDUE, LocalDate.of(2000, 1, 1), today));

        int pushed = 0;
        for (ScrmAnnualReportEntity entity : targets) {
            if (pushed >= properties.getMaxRemindPerRound()) {
                log.warn("[合规-年报提醒] 达到单轮上限 {}, 剩余留到下轮", properties.getMaxRemindPerRound());
                break;
            }
            if (pushAnnualOnce(entity)) {
                entity.setRemindCount(entity.getRemindCount() == null ? 1 : entity.getRemindCount() + 1);
                entity.setLastRemindedAt(LocalDateTime.now());
                annualReportRepository.save(entity);
                pushed++;
            }
        }
        log.info("[合规-年报提醒] 推送完成: pushed={}", pushed);
        return pushed;
    }

    /**
     * 查询年报详情。
     *
     * @param id 年报 ID
     * @return 年报记录
     * @throws ScrmException 记录不存在
     */
    @Transactional(readOnly = true)
    public ScrmAnnualReportEntity getAnnualReport(Long id) throws ScrmException {
        return findAnnualOrThrow(id);
    }

    /**
     * 标记年报已报送。
     *
     * @param id        年报记录 ID
     * @param receiptNo 公示系统回执编号（可空）
     * @param remark    备注（可空）
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
     */
    @Transactional
    public ScrmAnnualReportEntity markAnnualSubmitted(Long id, String receiptNo, String remark)
            throws ScrmException {
        ScrmAnnualReportEntity entity = findAnnualOrThrow(id);
        String status = entity.getStatus();
        if (ScrmAnnualReportEntity.STATUS_SUBMITTED.equals(status)
                || ScrmAnnualReportEntity.STATUS_CANCELED.equals(status)) {
            throw ScrmException.conflict("当前状态不可标记报送: id=" + id + ", status=" + status);
        }
        entity.setStatus(ScrmAnnualReportEntity.STATUS_SUBMITTED);
        entity.setSubmittedAt(LocalDateTime.now());
        entity.setSubmittedBy(operator());
        if (receiptNo != null && !receiptNo.isBlank()) {
            entity.setReceiptNo(receiptNo.trim());
        }
        if (remark != null) {
            entity.setRemark(remark);
        }
        return annualReportRepository.save(entity);
    }

    /**
     * 标记年报免报（当年注销、未开展经营等），免报不计入逾期。
     *
     * @param id     年报记录 ID
     * @param remark 免报原因（必填，用于日后追溯）
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 原因为空
     */
    @Transactional
    public ScrmAnnualReportEntity markAnnualExempt(Long id, String remark) throws ScrmException {
        if (remark == null || remark.isBlank()) {
            throw ScrmException.badRequest("免报原因不能为空");
        }
        ScrmAnnualReportEntity entity = findAnnualOrThrow(id);
        entity.setStatus(ScrmAnnualReportEntity.STATUS_EXEMPT);
        entity.setRemark(remark.trim());
        return annualReportRepository.save(entity);
    }

    /**
     * 分页查询年报。
     *
     * @param status      状态过滤（可空）
     * @param reportYear  年度过滤（可空）
     * @param companyId   主体过滤（可空）
     * @param accountantId 负责会计过滤（可空，先解析其负责主体集合）
     * @param pageable    分页参数
     * @return 年报分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAnnualReportEntity> listAnnualReports(String status, Integer reportYear,
                                                          Long companyId, Long accountantId,
                                                          Pageable pageable) {
        // 财税数据范围：会计只能看到自己负责主体的年报
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        return annualReportRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accessibleCompanyIds != null) {
                if (accessibleCompanyIds.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("companyId").in(accessibleCompanyIds));
                }
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (reportYear != null) {
                predicates.add(cb.equal(root.get("reportYear"), reportYear));
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            if (accountantId != null) {
                List<Long> ids = companyRepository.findIdsByAccountantId(accountantId);
                if (ids.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("companyId").in(ids));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    /**
     * 年报概览统计（前端顶部卡片）。
     *
     * @param reportYear 年度（可空 = 不限年度）
     * @return 各状态计数
     */
    @Transactional(readOnly = true)
    public Map<String, Object> annualSummary(Integer reportYear) {
        // 财税数据范围：会计只统计自己负责主体的年报
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        List<ScrmAnnualReportEntity> all = annualReportRepository.findAll().stream()
                .filter(e -> accessibleCompanyIds == null
                        || accessibleCompanyIds.contains(e.getCompanyId()))
                .collect(java.util.stream.Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        long pending = 0;
        long submitted = 0;
        long overdue = 0;
        long exempt = 0;
        for (ScrmAnnualReportEntity entity : all) {
            if (reportYear != null && !reportYear.equals(entity.getReportYear())) {
                continue;
            }
            switch (entity.getStatus()) {
                case ScrmAnnualReportEntity.STATUS_PENDING -> pending++;
                case ScrmAnnualReportEntity.STATUS_SUBMITTED -> submitted++;
                case ScrmAnnualReportEntity.STATUS_OVERDUE -> overdue++;
                case ScrmAnnualReportEntity.STATUS_EXEMPT -> exempt++;
                default -> { }
            }
        }
        result.put("pending", pending);
        result.put("submitted", submitted);
        result.put("overdue", overdue);
        result.put("exempt", exempt);
        result.put("total", pending + submitted + overdue + exempt);
        return result;
    }

    // ============================================================
    // 资质许可：台账 / 推进 / 提醒
    // ============================================================

    /**
     * 新增资质许可。
     *
     * @param entity 证照信息（companyId 与 qualificationName 必填）
     * @return 创建后的证照
     * @throws ScrmException 参数非法 / 主体不存在
     */
    @Transactional
    public ScrmQualificationEntity createQualification(ScrmQualificationEntity entity)
            throws ScrmException {
        if (entity == null || entity.getCompanyId() == null) {
            throw ScrmException.badRequest("公司主体不能为空");
        }
        if (entity.getQualificationName() == null || entity.getQualificationName().isBlank()) {
            throw ScrmException.badRequest("证照名称不能为空");
        }
        ScrmCompanyEntity company = companyRepository.findById(entity.getCompanyId())
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + entity.getCompanyId()));
        entity.setId(null);
        entity.setCompanyName(company.getCompanyName());
        entity.setCreatedBy(operator());
        if (entity.getStatus() == null) {
            entity.setStatus(ScrmQualificationEntity.STATUS_VALID);
        }
        return qualificationRepository.save(entity);
    }

    /**
     * 更新资质许可（null 字段不覆盖）。
     *
     * @param id     证照 ID
     * @param entity 更新内容
     * @return 更新后的证照
     * @throws ScrmException 证照不存在
     */
    @Transactional
    public ScrmQualificationEntity updateQualification(Long id, ScrmQualificationEntity entity)
            throws ScrmException {
        ScrmQualificationEntity existing = findQualificationOrThrow(id);
        if (entity == null) {
            return existing;
        }
        if (entity.getQualificationName() != null) {
            existing.setQualificationName(entity.getQualificationName());
        }
        if (entity.getQualificationType() != null) {
            existing.setQualificationType(entity.getQualificationType());
        }
        if (entity.getCertNo() != null) {
            existing.setCertNo(entity.getCertNo());
        }
        if (entity.getIssuer() != null) {
            existing.setIssuer(entity.getIssuer());
        }
        if (entity.getIssueDate() != null) {
            existing.setIssueDate(entity.getIssueDate());
        }
        if (entity.getExpireDate() != null) {
            existing.setExpireDate(entity.getExpireDate());
        }
        if (entity.getRemindDaysBefore() != null) {
            existing.setRemindDaysBefore(entity.getRemindDaysBefore());
        }
        if (entity.getRemark() != null) {
            existing.setRemark(entity.getRemark());
        }
        if (entity.getStatus() != null) {
            existing.setStatus(entity.getStatus());
        }
        return qualificationRepository.save(existing);
    }

    /**
     * 删除资质许可。
     *
     * @param id 证照 ID
     * @throws ScrmException 证照不存在
     */
    @Transactional
    public void deleteQualification(Long id) throws ScrmException {
        qualificationRepository.delete(findQualificationOrThrow(id));
    }

    /**
     * 标记证照已续期（更新到期日后回到 VALID）。
     *
     * @param id         证照 ID
     * @param newExpire  新到期日
     * @param remark     备注（可空）
     * @return 更新后的证照
     * @throws ScrmException 证照不存在 / 新到期日为空
     */
    @Transactional
    public ScrmQualificationEntity renewQualification(Long id, LocalDate newExpire, String remark)
            throws ScrmException {
        if (newExpire == null) {
            throw ScrmException.badRequest("新到期日不能为空");
        }
        ScrmQualificationEntity entity = findQualificationOrThrow(id);
        entity.setExpireDate(newExpire);
        entity.setStatus(ScrmQualificationEntity.STATUS_VALID);
        entity.setRemindCount(0);
        if (remark != null) {
            entity.setRemark(remark);
        }
        return qualificationRepository.save(entity);
    }

    /**
     * 推进证照状态（调度入口，每日）：进入提醒窗口 → EXPIRING，已过到期日 → EXPIRED。
     *
     * @return 推进条数
     */
    @Transactional
    public int advanceQualifications() {
        LocalDate today = LocalDate.now();
        int changed = 0;
        // 有效 → 即将到期（进入各自 remind_days_before 窗口）
        for (ScrmQualificationEntity entity : qualificationRepository
                .findByStatusAndExpireDateBetween(ScrmQualificationEntity.STATUS_VALID,
                        today, today.plusDays(365))) {
            int days = entity.getRemindDaysBefore() == null ? 60 : entity.getRemindDaysBefore();
            if (!entity.getExpireDate().isAfter(today.plusDays(days))) {
                entity.setStatus(ScrmQualificationEntity.STATUS_EXPIRING);
                qualificationRepository.save(entity);
                changed++;
            }
        }
        // 即将到期 → 已过期
        for (ScrmQualificationEntity entity : qualificationRepository
                .findByStatusAndExpireDateBetween(ScrmQualificationEntity.STATUS_EXPIRING,
                        LocalDate.of(2000, 1, 1), today.minusDays(1))) {
            entity.setStatus(ScrmQualificationEntity.STATUS_EXPIRED);
            qualificationRepository.save(entity);
            changed++;
        }
        if (changed > 0) {
            log.info("[合规-资质推进] 状态变更 {} 条", changed);
        }
        return changed;
    }

    /**
     * 推送资质到期提醒（调度入口，每日）。
     *
     * @return 本次推送条数
     */
    @Transactional
    public int pushQualificationReminders() {
        LocalDate today = LocalDate.now();
        List<ScrmQualificationEntity> targets = new ArrayList<>(
                qualificationRepository.findByStatusAndExpireDateBetween(
                        ScrmQualificationEntity.STATUS_EXPIRING, today, today.plusDays(365)));
        targets.addAll(qualificationRepository.findByStatusAndExpireDateBetween(
                ScrmQualificationEntity.STATUS_EXPIRED, today.minusDays(90), today));

        int pushed = 0;
        for (ScrmQualificationEntity entity : targets) {
            if (pushed >= properties.getMaxRemindPerRound()) {
                break;
            }
            if (pushQualificationOnce(entity)) {
                entity.setRemindCount(entity.getRemindCount() == null ? 1 : entity.getRemindCount() + 1);
                entity.setLastRemindedAt(LocalDateTime.now());
                qualificationRepository.save(entity);
                pushed++;
            }
        }
        log.info("[合规-资质提醒] 推送完成: pushed={}", pushed);
        return pushed;
    }

    /**
     * 分页查询资质许可。
     *
     * @param status       状态过滤（可空）
     * @param companyId    主体过滤（可空）
     * @param accountantId 负责会计过滤（可空）
     * @param keyword      证照名/证号关键字（可空）
     * @param pageable     分页参数
     * @return 证照分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmQualificationEntity> listQualifications(String status, Long companyId,
                                                            Long accountantId, String keyword,
                                                            Pageable pageable) {
        // 财税数据范围：会计只能看到自己负责主体的资质
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        return qualificationRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accessibleCompanyIds != null) {
                if (accessibleCompanyIds.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("companyId").in(accessibleCompanyIds));
                }
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            if (accountantId != null) {
                List<Long> ids = companyRepository.findIdsByAccountantId(accountantId);
                if (ids.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("companyId").in(ids));
                }
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("qualificationName")), like),
                        cb.like(cb.lower(root.get("certNo")), like),
                        cb.like(cb.lower(root.get("companyName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    /**
     * 资质概览统计（前端顶部卡片）。
     *
     * @return 各状态计数 + 90 天内到期数
     */
    @Transactional(readOnly = true)
    public Map<String, Object> qualificationSummary() {
        LocalDate today = LocalDate.now();
        // 财税数据范围：会计只统计自己负责主体的资质
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        List<ScrmQualificationEntity> all = qualificationRepository.findAll().stream()
                .filter(e -> accessibleCompanyIds == null
                        || accessibleCompanyIds.contains(e.getCompanyId()))
                .collect(java.util.stream.Collectors.toList());
        long valid = 0;
        long expiring = 0;
        long expired = 0;
        long renewed = 0;
        long dueSoon = 0;
        for (ScrmQualificationEntity entity : all) {
            switch (entity.getStatus()) {
                case ScrmQualificationEntity.STATUS_VALID -> valid++;
                case ScrmQualificationEntity.STATUS_EXPIRING -> expiring++;
                case ScrmQualificationEntity.STATUS_EXPIRED -> expired++;
                case ScrmQualificationEntity.STATUS_RENEWED -> renewed++;
                default -> { }
            }
            if (entity.getExpireDate() != null
                    && !entity.getExpireDate().isBefore(today)
                    && !entity.getExpireDate().isAfter(today.plusDays(90))
                    && !ScrmQualificationEntity.STATUS_CANCELED.equals(entity.getStatus())) {
                dueSoon++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("expiring", expiring);
        result.put("expired", expired);
        result.put("renewed", renewed);
        result.put("dueSoon90d", dueSoon);
        result.put("total", all.size());
        return result;
    }

    /**
     * 按主体查询年报与证照（公司 360 钻取）。
     *
     * @param companyId 公司主体 ID
     * @return 年报列表 + 证照列表
     */
    @Transactional(readOnly = true)
    public Map<String, Object> companyCompliance(Long companyId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("annualReports",
                annualReportRepository.findByCompanyIdOrderByReportYearDesc(companyId));
        result.put("qualifications",
                qualificationRepository.findByCompanyIdOrderByExpireDateAsc(companyId));
        return result;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 推送单条年报提醒（幂等：同一条同一截止日只推一次）。 */
    private boolean pushAnnualOnce(ScrmAnnualReportEntity entity) {
        String relatedId = entity.getId() + ":d" + ChronoUnit.DAYS.between(
                LocalDate.now(), entity.getDueDate());
        try {
            if (notificationRepository.existsByRelatedTypeAndRelatedIdAndTemplateCode(
                    RELATED_ANNUAL, relatedId, properties.getAnnualRemindTemplateCode())) {
                return false;
            }
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), entity.getDueDate());
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("companyName", nullTo(entity.getCompanyName(), "未命名主体"));
            variables.put("reportYear", String.valueOf(entity.getReportYear()));
            variables.put("dueDate", String.valueOf(entity.getDueDate()));
            variables.put("statusLabel", ScrmAnnualReportEntity.STATUS_OVERDUE.equals(entity.getStatus())
                    ? "已逾期" : "待报送");
            variables.put("daysLeft", String.valueOf(daysLeft));

            ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
            dto.setTemplateCode(properties.getAnnualRemindTemplateCode());
            dto.setRecipientType("USER");
            dto.setRecipients(List.of(resolveRecipient(entity.getCompanyId())));
            dto.setSenderId(SENDER);
            dto.setSenderName("合规系统");
            dto.setPriority(daysLeft <= 0 ? 2 : 1);
            dto.setRelatedType(RELATED_ANNUAL);
            dto.setRelatedId(relatedId);
            dto.setVariables(variables);
            notificationCenterService.sendNotification(dto);
            return true;
        } catch (Exception e) {
            log.warn("[合规-年报提醒] 推送失败(不影响主流程): annualId={}, err={}",
                    entity.getId(), e.getMessage());
            return false;
        }
    }

    /** 推送单条资质到期提醒（幂等：按到期日去重，续期后到期日变化会重新提醒）。 */
    private boolean pushQualificationOnce(ScrmQualificationEntity entity) {
        String relatedId = entity.getId() + ":d" + entity.getExpireDate();
        try {
            if (notificationRepository.existsByRelatedTypeAndRelatedIdAndTemplateCode(
                    RELATED_QUALIFICATION, relatedId, properties.getQualificationRemindTemplateCode())) {
                return false;
            }
            long daysLeft = entity.getExpireDate() == null ? 0
                    : ChronoUnit.DAYS.between(LocalDate.now(), entity.getExpireDate());
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("companyName", nullTo(entity.getCompanyName(), "未命名主体"));
            variables.put("qualificationName", nullTo(entity.getQualificationName(), "证照"));
            variables.put("certNo", nullTo(entity.getCertNo(), "-"));
            variables.put("expireDate", String.valueOf(entity.getExpireDate()));
            variables.put("statusLabel", ScrmQualificationEntity.STATUS_EXPIRED.equals(entity.getStatus())
                    ? "已过期" : "即将到期");
            variables.put("daysLeft", String.valueOf(daysLeft));

            ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
            dto.setTemplateCode(properties.getQualificationRemindTemplateCode());
            dto.setRecipientType("USER");
            dto.setRecipients(List.of(resolveRecipient(entity.getCompanyId())));
            dto.setSenderId(SENDER);
            dto.setSenderName("合规系统");
            dto.setPriority(daysLeft <= 0 ? 2 : 1);
            dto.setRelatedType(RELATED_QUALIFICATION);
            dto.setRelatedId(relatedId);
            dto.setVariables(variables);
            notificationCenterService.sendNotification(dto);
            return true;
        } catch (Exception e) {
            log.warn("[合规-资质提醒] 推送失败(不影响主流程): qualId={}, err={}",
                    entity.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 解析提醒收件人：负责会计 > 配置默认。
     *
     * @param companyId 公司主体 ID
     * @return 收件人标识
     */
    private String resolveRecipient(Long companyId) {
        Long accountantId = companyResolver.resolveAccountantId(companyId);
        return accountantId != null
                ? String.valueOf(accountantId) : properties.getNotifyRecipient();
    }

    /** 查询年报或抛 404。 */
    private ScrmAnnualReportEntity findAnnualOrThrow(Long id) throws ScrmException {
        return annualReportRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("工商年报记录不存在: " + id));
    }

    /** 查询证照或抛 404。 */
    private ScrmQualificationEntity findQualificationOrThrow(Long id) throws ScrmException {
        return qualificationRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("资质许可不存在: " + id));
    }

    /** null 转默认值。 */
    private String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 当前操作人。 */
    private String operator() {
        String userId = UserContext.getUserId();
        return userId != null && !userId.isBlank() ? userId : "SYSTEM";
    }
}
