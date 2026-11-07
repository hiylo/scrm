/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmPayrollService.java
 * Date : 2026/10/25 13:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.payroll;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.ScrmPayrollProperties;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmEmployeeEntity;
import org.hiylo.scrm.entity.ScrmIitRecordEntity;
import org.hiylo.scrm.entity.ScrmSocialSecurityRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmEmployeeRepository;
import org.hiylo.scrm.repository.ScrmIitRecordRepository;
import org.hiylo.scrm.repository.ScrmSocialSecurityRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 薪酬域服务：员工档案 + 社保明细 + 个税明细。
 * <p>
 * <b>业务链路</b>：员工档案是唯一数据源 → 每月按人算社保分项 → 个税以社保
 * 个人部分为专项扣除、按累计预扣法计算 → 申报/缴款状态回落。
 * 三步是串行的，跳过任何一步都会算错。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmPayrollService {

    /** 员工仓库 */
    private final ScrmEmployeeRepository employeeRepository;

    /** 社保明细仓库 */
    private final ScrmSocialSecurityRecordRepository socialRepository;

    /** 个税明细仓库 */
    private final ScrmIitRecordRepository iitRepository;

    /** 公司主体仓库（校验归属、补公司名） */
    private final ScrmCompanyRepository companyRepository;

    /** 计算器（金额唯一来源） */
    private final ScrmPayrollCalculator calculator;

    /** 薪酬域配置 */
    private final ScrmPayrollProperties properties;

    /** 财税数据范围（会计只看到自己负责主体的员工） */
    private final org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    // ============================================================
    // 员工档案
    // ============================================================

    /**
     * 新增员工。
     *
     * @param entity 员工信息（companyId 与 name 必填）
     * @return 创建后的员工
     * @throws ScrmException 参数非法 / 主体不存在
     */
    @Transactional
    public ScrmEmployeeEntity createEmployee(ScrmEmployeeEntity entity) throws ScrmException {
        if (entity == null || entity.getCompanyId() == null) {
            throw ScrmException.badRequest("所属公司不能为空");
        }
        if (entity.getName() == null || entity.getName().isBlank()) {
            throw ScrmException.badRequest("员工姓名不能为空");
        }
        ScrmCompanyEntity company = companyRepository.findById(entity.getCompanyId())
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + entity.getCompanyId()));
        entity.setId(null);
        entity.setCompanyName(company.getCompanyName());
        entity.setCreatedBy(operator());
        if (entity.getStatus() == null) {
            entity.setStatus(ScrmEmployeeEntity.STATUS_ACTIVE);
        }
        return employeeRepository.save(entity);
    }

    /**
     * 更新员工（null 字段不覆盖）。
     *
     * @param id     员工 ID
     * @param entity 更新内容
     * @return 更新后的员工
     * @throws ScrmException 员工不存在
     */
    @Transactional
    public ScrmEmployeeEntity updateEmployee(Long id, ScrmEmployeeEntity entity) throws ScrmException {
        ScrmEmployeeEntity existing = findEmployeeOrThrow(id);
        if (entity == null) {
            return existing;
        }
        if (entity.getName() != null) {
            existing.setName(entity.getName());
        }
        if (entity.getIdCardNo() != null) {
            existing.setIdCardNo(entity.getIdCardNo());
        }
        if (entity.getPhone() != null) {
            existing.setPhone(entity.getPhone());
        }
        if (entity.getPosition() != null) {
            existing.setPosition(entity.getPosition());
        }
        if (entity.getHireDate() != null) {
            existing.setHireDate(entity.getHireDate());
        }
        if (entity.getLeaveDate() != null) {
            existing.setLeaveDate(entity.getLeaveDate());
        }
        if (entity.getSocialStatus() != null) {
            existing.setSocialStatus(entity.getSocialStatus());
        }
        if (entity.getSocialBase() != null) {
            existing.setSocialBase(entity.getSocialBase());
        }
        if (entity.getHousingFundBase() != null) {
            existing.setHousingFundBase(entity.getHousingFundBase());
        }
        if (entity.getSpecialDeduction() != null) {
            existing.setSpecialDeduction(entity.getSpecialDeduction());
        }
        if (entity.getStatus() != null) {
            existing.setStatus(entity.getStatus());
            // 办理离职时自动落离职日期，避免忘记写导致社保继续计提
            if (ScrmEmployeeEntity.STATUS_LEFT.equals(entity.getStatus())
                    && existing.getLeaveDate() == null) {
                existing.setLeaveDate(LocalDate.now());
            }
        }
        if (entity.getRemark() != null) {
            existing.setRemark(entity.getRemark());
        }
        return employeeRepository.save(existing);
    }

    /**
     * 删除员工（仅限从未产生社保/个税明细的，避免破坏历史账）。
     *
     * @param id 员工 ID
     * @throws ScrmException 员工不存在 / 已有明细
     */
    @Transactional
    public void deleteEmployee(Long id) throws ScrmException {
        ScrmEmployeeEntity employee = findEmployeeOrThrow(id);
        boolean hasSocial = !socialRepository
                .findByEmployeeIdAndPeriodYearOrderByPeriodMonthAsc(id, LocalDate.now().getYear())
                .isEmpty();
        boolean hasIit = !iitRepository
                .findByEmployeeIdAndPeriodYearOrderByPeriodMonthAsc(id, LocalDate.now().getYear())
                .isEmpty();
        if (hasSocial || hasIit) {
            throw ScrmException.conflict("该员工已有社保/个税明细，不能删除；请改为「离职」保留历史账");
        }
        employeeRepository.delete(employee);
    }

    /**
     * 分页查询员工。
     *
     * @param companyId 主体过滤（可空）
     * @param status    在职状态过滤（可空）
     * @param keyword   姓名/岗位/电话关键字（可空）
     * @param pageable  分页
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmEmployeeEntity> listEmployees(Long companyId, String status, String keyword,
                                                  Pageable pageable) {
        // 财税数据范围：会计只能看到自己负责主体的员工
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        return employeeRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accessibleCompanyIds != null) {
                if (accessibleCompanyIds.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("companyId").in(accessibleCompanyIds));
                }
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("position")), like),
                        cb.like(cb.lower(root.get("phone")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    /**
     * 查询员工详情。
     *
     * @param id 员工 ID
     * @return 员工
     * @throws ScrmException 不存在
     */
    @Transactional(readOnly = true)
    public ScrmEmployeeEntity getEmployee(Long id) throws ScrmException {
        return findEmployeeOrThrow(id);
    }

    // ============================================================
    // 社保 / 个税批量生成
    // ============================================================

    /**
     * 为指定公司某月批量生成社保 + 个税明细。
     * <p>
     * 幂等：已存在（员工 + 年月）的明细跳过，不覆盖人工调整过的数据。
     * 生成顺序固定为「先社保后个税」——个税的专项扣除要取社保个人部分。
     * </p>
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月（1-12）
     * @param monthlyIncomeResolver 各员工本月税前收入（key=员工 ID；缺省用 0）
     * @return 生成统计
     * @throws ScrmException 参数非法 / 主体不存在
     */
    @Transactional
    public Map<String, Object> generateMonthly(Long companyId, int periodYear, int periodMonth,
                                               Map<Long, BigDecimal> monthlyIncomeResolver)
            throws ScrmException {
        if (periodMonth < 1 || periodMonth > 12) {
            throw ScrmException.badRequest("月份必须在 1-12 之间: " + periodMonth);
        }
        ScrmCompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));

        List<ScrmEmployeeEntity> employees = employeeRepository
                .findByCompanyIdAndStatusOrderByHireDateAsc(companyId, ScrmEmployeeEntity.STATUS_ACTIVE);
        if (employees.size() > properties.getMaxGenerateBatch()) {
            throw ScrmException.badRequest("在册员工 " + employees.size()
                    + " 人超过单次上限 " + properties.getMaxGenerateBatch() + " 人");
        }

        int socialCreated = 0;
        int socialSkipped = 0;
        int iitCreated = 0;
        int iitSkipped = 0;

        for (ScrmEmployeeEntity employee : employees) {
            // 未参保员工不产生社保明细（也不参与个税专项扣除）
            ScrmSocialSecurityRecordEntity social = null;
            if (ScrmEmployeeEntity.SOCIAL_INSURED.equals(employee.getSocialStatus())) {
                var existing = socialRepository.findByEmployeeIdAndPeriodYearAndPeriodMonth(
                        employee.getId(), periodYear, periodMonth);
                if (existing.isPresent()) {
                    social = existing.get();
                    socialSkipped++;
                } else {
                    social = calculator.calculateSocial(employee, periodYear, periodMonth);
                    social.setCompanyName(company.getCompanyName());
                    socialRepository.save(social);
                    socialCreated++;
                }
            }

            // 个税：专项扣除取社保个人部分（公积金也算专项扣除）
            var existingIit = iitRepository.findByEmployeeIdAndPeriodYearAndPeriodMonth(
                    employee.getId(), periodYear, periodMonth);
            if (existingIit.isPresent()) {
                iitSkipped++;
                continue;
            }
            BigDecimal socialDeduction = social == null
                    ? BigDecimal.ZERO : social.getTotalPersonal();
            BigDecimal income = monthlyIncomeResolver == null
                    ? BigDecimal.ZERO : monthlyIncomeResolver.getOrDefault(employee.getId(), BigDecimal.ZERO);
            List<ScrmIitRecordEntity> priors = iitRepository
                    .findByEmployeeIdAndPeriodYearOrderByPeriodMonthAsc(employee.getId(), periodYear);
            ScrmIitRecordEntity iit = calculator.calculateIit(
                    employee, periodYear, periodMonth, income, socialDeduction, priors);
            iit.setCompanyName(company.getCompanyName());
            iitRepository.save(iit);
            iitCreated++;
        }

        // 累计预扣法：一旦补齐历史月份，后续月份累计数必须重算，否则税额是错的
        Map<String, Object> recalc = recalculateIitChain(companyId, periodYear, periodMonth + 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeCount", employees.size());
        result.put("socialCreated", socialCreated);
        result.put("socialSkipped", socialSkipped);
        result.put("iitCreated", iitCreated);
        result.put("iitSkipped", iitSkipped);
        result.put("recalculated", recalc.get("recalculated"));
        result.put("skippedLocked", recalc.get("skippedLocked"));
        log.info("[薪酬-生成] company={}, {}-{}: {}", companyId, periodYear, periodMonth, result);
        return result;
    }

    /**
     * 重算某公司某年度「从指定月份起」的个税累计链。
     * <p>
     * <b>为什么必须有这一步</b>：累计预扣法下每期税额依赖本年度历史累计。
     * 如果会计先做了 10 月、再补 8 月，那 9/10 月的累计应纳税所得额就少算了 8 月，
     * 税额随之错误。生成之后自动重算后续月份，可以把这条链重新对齐。
     * </p>
     * <p>
     * <b>安全边界</b>：只重算 {@code PENDING} 的月份；已申报/已缴款的月份
     * 金额已对外报送，不能被悄悄改掉，只统计为「跳过」返回给调用方，
     * 由会计走更正申报流程。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @param periodYear 年
     * @param fromMonth 从该月起重算（含）
     * @return 重算统计（recalculated / skippedLocked / changed）
     */
    @Transactional
    public Map<String, Object> recalculateIitChain(Long companyId, int periodYear, int fromMonth) {
        List<ScrmEmployeeEntity> employees = employeeRepository
                .findByCompanyIdOrderByHireDateAsc(companyId);
        int recalculated = 0;
        int skippedLocked = 0;

        for (ScrmEmployeeEntity employee : employees) {
            List<ScrmIitRecordEntity> records = iitRepository
                    .findByEmployeeIdAndPeriodYearOrderByPeriodMonthAsc(employee.getId(), periodYear);
            if (records.isEmpty()) {
                continue;
            }
            // 从 1 月起按顺序累加，前序月份的记录本身也要参与累加
            BigDecimal cumulativeIncome = BigDecimal.ZERO;
            BigDecimal cumulativeDeduction = BigDecimal.ZERO;
            BigDecimal paidTax = BigDecimal.ZERO;

            for (ScrmIitRecordEntity record : records) {
                int month = record.getPeriodMonth() == null ? 0 : record.getPeriodMonth();
                cumulativeIncome = cumulativeIncome.add(nz(record.getIncomeAmount()));
                cumulativeDeduction = cumulativeDeduction
                        .add(nz(record.getBasicDeduction()))
                        .add(nz(record.getSocialDeduction()))
                        .add(nz(record.getSpecialDeduction()));

                if (month < fromMonth) {
                    // 前序月份不动，但要把它的税额计入「累计已预扣」
                    paidTax = paidTax.add(nz(record.getCurrentTax()));
                    continue;
                }
                if (!ScrmIitRecordEntity.STATUS_PENDING.equals(record.getStatus())) {
                    // 已申报/已缴款不能改，仍然累加它的税额保持链一致
                    paidTax = paidTax.add(nz(record.getCurrentTax()));
                    skippedLocked++;
                    continue;
                }

                BigDecimal cumulativeTaxable = cumulativeIncome.subtract(cumulativeDeduction)
                        .max(BigDecimal.ZERO);
                BigDecimal[] bracket = calculator.resolveBracket(cumulativeTaxable);
                BigDecimal taxRate = bracket[0];
                BigDecimal quickDeduction = bracket[1];
                BigDecimal cumulativeTax = cumulativeTaxable.multiply(taxRate)
                        .subtract(quickDeduction)
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO);
                BigDecimal currentTax = cumulativeTax.subtract(paidTax)
                        .setScale(2, java.math.RoundingMode.HALF_UP);

                record.setCumulativeIncome(cumulativeIncome.setScale(2, java.math.RoundingMode.HALF_UP));
                record.setCumulativeDeduction(cumulativeDeduction.setScale(2, java.math.RoundingMode.HALF_UP));
                record.setCumulativeTaxable(cumulativeTaxable.setScale(2, java.math.RoundingMode.HALF_UP));
                record.setTaxRate(taxRate);
                record.setQuickDeduction(quickDeduction);
                record.setCumulativeTax(cumulativeTax);
                record.setPaidTax(paidTax.setScale(2, java.math.RoundingMode.HALF_UP));
                record.setCurrentTax(currentTax);
                iitRepository.save(record);

                paidTax = paidTax.add(currentTax);
                recalculated++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recalculated", recalculated);
        result.put("skippedLocked", skippedLocked);
        log.info("[薪酬-重算] company={}, {} 年 {} 月起: recalculated={}, skippedLocked={}",
                companyId, periodYear, fromMonth, recalculated, skippedLocked);
        return result;
    }

    // ============================================================
    // 明细查询与状态流转
    // ============================================================

    /**
     * 查某公司某月社保明细。
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @return 明细列表
     */
    @Transactional(readOnly = true)
    public List<ScrmSocialSecurityRecordEntity> listSocialRecords(Long companyId,
                                                                 int periodYear, int periodMonth) {
        return socialRepository
                .findByCompanyIdAndPeriodYearAndPeriodMonthOrderByEmployeeNameAsc(
                        companyId, periodYear, periodMonth);
    }

    /**
     * 查某公司某月个税明细。
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @return 明细列表
     */
    @Transactional(readOnly = true)
    public List<ScrmIitRecordEntity> listIitRecords(Long companyId,
                                                    int periodYear, int periodMonth) {
        return iitRepository
                .findByCompanyIdAndPeriodYearAndPeriodMonthOrderByEmployeeNameAsc(
                        companyId, periodYear, periodMonth);
    }

    /**
     * 标记社保明细已申报 / 已缴费。
     *
     * @param id     明细 ID
     * @param toPaid 是否推进到已缴费（false 仅推进到已申报）
     * @return 更新后的明细
     * @throws ScrmException 明细不存在
     */
    @Transactional
    public ScrmSocialSecurityRecordEntity markSocialStatus(Long id, boolean toPaid)
            throws ScrmException {
        ScrmSocialSecurityRecordEntity record = socialRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("社保明细不存在: " + id));
        if (toPaid) {
            record.setStatus(ScrmSocialSecurityRecordEntity.STATUS_PAID);
            record.setPaidAt(LocalDateTime.now());
            if (record.getDeclaredAt() == null) {
                record.setDeclaredAt(LocalDateTime.now());
            }
        } else {
            record.setStatus(ScrmSocialSecurityRecordEntity.STATUS_DECLARED);
            record.setDeclaredAt(LocalDateTime.now());
        }
        return socialRepository.save(record);
    }

    /**
     * 标记个税明细已申报 / 已缴款。
     *
     * @param id     明细 ID
     * @param toPaid 是否推进到已缴款
     * @return 更新后的明细
     * @throws ScrmException 明细不存在
     */
    @Transactional
    public ScrmIitRecordEntity markIitStatus(Long id, boolean toPaid) throws ScrmException {
        ScrmIitRecordEntity record = iitRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("个税明细不存在: " + id));
        if (toPaid) {
            record.setStatus(ScrmIitRecordEntity.STATUS_PAID);
            record.setPaidAt(LocalDateTime.now());
            if (record.getDeclaredAt() == null) {
                record.setDeclaredAt(LocalDateTime.now());
            }
        } else {
            record.setStatus(ScrmIitRecordEntity.STATUS_DECLARED);
            record.setDeclaredAt(LocalDateTime.now());
        }
        return iitRepository.save(record);
    }

    /**
     * 某公司某月社保汇总（前端顶部卡片 + 对账）。
     *
     * @param companyId   公司主体 ID（可空 = 全量）
     * @param periodYear  年
     * @param periodMonth 月
     * @return 人数、个人合计、单位合计、总计、各状态计数
     */
    @Transactional(readOnly = true)
    public Map<String, Object> socialSummary(Long companyId, int periodYear, int periodMonth) {
        List<ScrmSocialSecurityRecordEntity> records = companyId == null
                ? socialRepository.findAll().stream()
                    .filter(r -> periodYear == r.getPeriodYear() && periodMonth == r.getPeriodMonth())
                    .toList()
                : listSocialRecords(companyId, periodYear, periodMonth);

        BigDecimal personal = BigDecimal.ZERO;
        BigDecimal companyPart = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        long pending = 0;
        long declared = 0;
        long paid = 0;
        for (ScrmSocialSecurityRecordEntity record : records) {
            personal = personal.add(nz(record.getTotalPersonal()));
            companyPart = companyPart.add(nz(record.getTotalCompany()));
            total = total.add(nz(record.getTotalAmount()));
            switch (record.getStatus()) {
                case ScrmSocialSecurityRecordEntity.STATUS_DECLARED -> declared++;
                case ScrmSocialSecurityRecordEntity.STATUS_PAID -> paid++;
                default -> pending++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headcount", records.size());
        result.put("personalTotal", personal);
        result.put("companyTotal", companyPart);
        result.put("grandTotal", total);
        result.put("pending", pending);
        result.put("declared", declared);
        result.put("paid", paid);
        return result;
    }

    /**
     * 某公司某月个税汇总。
     *
     * @param companyId   公司主体 ID（可空 = 全量）
     * @param periodYear  年
     * @param periodMonth 月
     * @return 人数、收入合计、本期税额合计、各状态计数
     */
    @Transactional(readOnly = true)
    public Map<String, Object> iitSummary(Long companyId, int periodYear, int periodMonth) {
        List<ScrmIitRecordEntity> records = companyId == null
                ? iitRepository.findAll().stream()
                    .filter(r -> periodYear == r.getPeriodYear() && periodMonth == r.getPeriodMonth())
                    .toList()
                : listIitRecords(companyId, periodYear, periodMonth);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        long pending = 0;
        long declared = 0;
        long paid = 0;
        for (ScrmIitRecordEntity record : records) {
            income = income.add(nz(record.getIncomeAmount()));
            tax = tax.add(nz(record.getCurrentTax()));
            switch (record.getStatus()) {
                case ScrmIitRecordEntity.STATUS_DECLARED -> declared++;
                case ScrmIitRecordEntity.STATUS_PAID -> paid++;
                default -> pending++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headcount", records.size());
        result.put("incomeTotal", income);
        result.put("taxTotal", tax);
        result.put("pending", pending);
        result.put("declared", declared);
        result.put("paid", paid);
        return result;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 查询员工或抛 404。 */
    private ScrmEmployeeEntity findEmployeeOrThrow(Long id) throws ScrmException {
        return employeeRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("员工不存在: " + id));
    }

    /** null 安全取零。 */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 当前操作人。 */
    private String operator() {
        String userId = UserContext.getUserId();
        return userId != null && !userId.isBlank() ? userId : "SYSTEM";
    }
}
