/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmPeriodCloseService.java
 * Date : 2026/10/25 21:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.entity.ScrmAccountPeriodEntity;
import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntryEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountPeriodRepository;
import org.hiylo.scrm.repository.ScrmAccountSubjectRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmVoucherEntryRepository;
import org.hiylo.scrm.repository.ScrmVoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 期末结账服务（凭证域）。
 * <p>
 * <b>结账做两件事</b>：
 * </p>
 * <ol>
 *   <li><b>结转损益</b>：把损益类科目（收入/费用）余额结转到「本年利润」，
 *       让损益科目回到 0，利润落到权益里。不做这一步，年末「本年利润」不会生成，
 *       利润表与资产负债表也对不上。</li>
 *   <li><b>封账</b>：把期间置为 CLOSED，禁止再往里加凭证。否则已经出过的报表
 *       会因为事后补录而失真，且结转凭证本身也会算错。</li>
 * </ol>
 * <p>
 * <b>结转口径</b>：取「截至该期的累计发生额」。因为每期结账后损益科目已归零，
 * 所以这个累计实际就是当期发生额，逐月结账天然幂等。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmPeriodCloseService {

    /** 金额精度 */
    private static final int MONEY_SCALE = 2;

    /** 本年利润科目编码（小企业会计准则 3103） */
    private static final String SUBJECT_CURRENT_PROFIT = "3103";

    /** 期间仓储 */
    private final ScrmAccountPeriodRepository periodRepository;

    /** 科目仓储 */
    private final ScrmAccountSubjectRepository subjectRepository;

    /** 凭证仓储 */
    private final ScrmVoucherRepository voucherRepository;

    /** 分录仓储 */
    private final ScrmVoucherEntryRepository entryRepository;

    /** 主体仓储 */
    private final ScrmCompanyRepository companyRepository;

    /**
     * 判断期间是否已结账（凭证新增/记账前必须校验）。
     *
     * @param companyId 公司主体 ID
     * @param year      年
     * @param month     月
     * @return 已结账返回 true
     */
    @Transactional(readOnly = true)
    public boolean isClosed(Long companyId, Integer year, Integer month) {
        if (companyId == null || year == null || month == null) {
            return false;
        }
        return periodRepository.findByCompanyIdAndPeriodYearAndPeriodMonth(companyId, year, month)
                .map(p -> ScrmAccountPeriodEntity.STATUS_CLOSED.equals(p.getStatus()))
                .orElse(false);
    }

    /**
     * 结账：生成结转损益凭证并封账。
     *
     * @param companyId 公司主体 ID
     * @param year      年
     * @param month     月
     * @return 结账结果（结转凭证号、结转金额、期末状态）
     * @throws ScrmException 主体不存在 / 期间已结账 / 存在未记账凭证
     */
    @Transactional
    public Map<String, Object> closePeriod(Long companyId, int year, int month) throws ScrmException {
        ScrmCompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));
        ScrmAccountPeriodEntity period = periodRepository
                .findByCompanyIdAndPeriodYearAndPeriodMonth(companyId, year, month)
                .orElse(null);
        if (period != null && ScrmAccountPeriodEntity.STATUS_CLOSED.equals(period.getStatus())) {
            throw ScrmException.conflict("该期间已结账: " + year + "-" + month);
        }

        // 有草稿未记账时不允许结账：否则草稿会永远留在已封的期间里
        long draftCount = voucherRepository.countByCompanyIdAndPeriodYearAndPeriodMonthAndStatus(
                companyId, year, month, ScrmVoucherEntity.STATUS_DRAFT);
        if (draftCount > 0) {
            throw ScrmException.badRequest("本期还有 " + draftCount
                    + " 张草稿凭证未记账，请先记账或删除后再结账");
        }

        int yyyymm = year * 100 + month;
        // 科目类别表，用于判断哪些是损益类
        Map<String, ScrmAccountSubjectEntity> subjects = new LinkedHashMap<>();
        for (ScrmAccountSubjectEntity subject : subjectRepository
                .findByCompanyIdOrderBySubjectCodeAsc(companyId)) {
            subjects.put(subject.getSubjectCode(), subject);
        }

        ScrmVoucherEntity closingVoucher = null;
        BigDecimal profit = BigDecimal.ZERO;

        // 以库里已记账分录为准计算损益余额（不信任任何缓存合计）
        List<Object[]> rows = entryRepository.sumUpTo(companyId, yyyymm);
        List<ScrmVoucherEntryEntity> entries = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int lineNo = 1;

        for (Object[] row : rows) {
            String code = String.valueOf(row[0]);
            ScrmAccountSubjectEntity subject = subjects.get(code);
            if (subject == null) {
                continue;
            }
            boolean isIncome = ScrmAccountSubjectEntity.CATEGORY_INCOME.equals(subject.getCategory());
            boolean isExpense = ScrmAccountSubjectEntity.CATEGORY_EXPENSE.equals(subject.getCategory());
            if (!isIncome && !isExpense) {
                continue;
            }
            BigDecimal debit = toDecimal(row[2]);
            BigDecimal credit = toDecimal(row[3]);
            BigDecimal net = debit.subtract(credit);

            if (isIncome) {
                // 收入为贷方余额（net 为负）。借方冲平，金额 = 贷方超出部分
                BigDecimal amount = credit.subtract(debit);
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    entries.add(buildEntry(companyId, lineNo++, code, subject.getSubjectName(),
                            amount, BigDecimal.ZERO, "结转收入至本年利润"));
                    totalDebit = totalDebit.add(amount);
                }
            } else {
                // 费用为借方余额。贷方冲平
                BigDecimal amount = net;
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    entries.add(buildEntry(companyId, lineNo++, code, subject.getSubjectName(),
                            BigDecimal.ZERO, amount, "结转费用至本年利润"));
                    totalCredit = totalCredit.add(amount);
                }
            }
        }

        // 差额落到本年利润：借>贷为盈利，贷>借为亏损
        if (!entries.isEmpty()) {
            BigDecimal diff = totalDebit.subtract(totalCredit);
            ScrmAccountSubjectEntity profitSubject = subjects.get(SUBJECT_CURRENT_PROFIT);
            if (profitSubject == null) {
                throw ScrmException.badRequest("缺少「本年利润」科目（" + SUBJECT_CURRENT_PROFIT
                        + "），无法结转损益；请先在会计科目里补充该科目");
            }
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                // 盈利：贷本年利润
                entries.add(buildEntry(companyId, lineNo, SUBJECT_CURRENT_PROFIT,
                        profitSubject.getSubjectName(), BigDecimal.ZERO, diff, "本期净利润"));
                totalCredit = totalCredit.add(diff);
                profit = diff;
            } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                // 亏损：借本年利润
                BigDecimal loss = diff.negate();
                entries.add(buildEntry(companyId, lineNo, SUBJECT_CURRENT_PROFIT,
                        profitSubject.getSubjectName(), loss, BigDecimal.ZERO, "本期净亏损"));
                totalDebit = totalDebit.add(loss);
                profit = loss.negate();
            } else {
                profit = BigDecimal.ZERO;
            }

            ScrmVoucherEntity voucher = new ScrmVoucherEntity();
            voucher.setCompanyId(companyId);
            voucher.setCompanyName(company.getCompanyName());
            voucher.setPeriodYear(year);
            voucher.setPeriodMonth(month);
            voucher.setVoucherWord("转");
            Integer maxNo = voucherRepository.findMaxVoucherNo(companyId, year, month, "转");
            voucher.setVoucherNo(maxNo == null ? 1 : maxNo + 1);
            voucher.setVoucherDate(LocalDate.of(year, month,
                    java.time.YearMonth.of(year, month).lengthOfMonth()));
            voucher.setTotalDebit(totalDebit.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            voucher.setTotalCredit(totalCredit.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            voucher.setEntryCount(entries.size());
            // 系统生成且借贷自平，直接置为已记账，避免"结账后还要人工点记账"
            voucher.setStatus(ScrmVoucherEntity.STATUS_POSTED);
            voucher.setIsClosing(true);
            voucher.setRemark("期末结转损益 " + year + "-" + month);
            voucher.setCreatedBy(operator());
            voucher.setPostedBy(operator());
            voucher.setPostedAt(LocalDateTime.now());

            closingVoucher = voucherRepository.save(voucher);
            for (ScrmVoucherEntryEntity entry : entries) {
                entry.setVoucherId(closingVoucher.getId());
            }
            entryRepository.saveAll(entries);
            log.info("[结账] 结转损益: companyId={}, {}-{}, 凭证={}, 利润={}",
                    companyId, year, month, closingVoucher.getVoucherLabel(), profit);
        }

        if (period == null) {
            period = new ScrmAccountPeriodEntity();
            period.setCompanyId(companyId);
            period.setPeriodYear(year);
            period.setPeriodMonth(month);
        }
        period.setStatus(ScrmAccountPeriodEntity.STATUS_CLOSED);
        period.setClosingVoucherId(closingVoucher == null ? null : closingVoucher.getId());
        period.setClosedBy(operator());
        period.setClosedAt(LocalDateTime.now());
        periodRepository.save(period);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", year + "-" + month);
        result.put("status", ScrmAccountPeriodEntity.STATUS_CLOSED);
        result.put("profit", profit);
        result.put("closingVoucherId", closingVoucher == null ? null : String.valueOf(closingVoucher.getId()));
        result.put("closingVoucherLabel", closingVoucher == null ? null : closingVoucher.getVoucherLabel());
        result.put("entryCount", closingVoucher == null ? 0 : closingVoucher.getEntryCount());
        result.put("message", closingVoucher == null
                ? "本期无损益发生额，已直接结账"
                : "已结账，损益已结转至本年利润");
        return result;
    }

    /**
     * 反结账：作废结转凭证并恢复期间为 OPEN。
     *
     * @param companyId 公司主体 ID
     * @param year      年
     * @param month     月
     * @return 结果
     * @throws ScrmException 期间未结账
     */
    @Transactional
    public Map<String, Object> reopenPeriod(Long companyId, int year, int month) throws ScrmException {
        ScrmAccountPeriodEntity period = periodRepository
                .findByCompanyIdAndPeriodYearAndPeriodMonth(companyId, year, month)
                .orElseThrow(() -> ScrmException.conflict("该期间未结账: " + year + "-" + month));
        if (!ScrmAccountPeriodEntity.STATUS_CLOSED.equals(period.getStatus())) {
            throw ScrmException.conflict("该期间未结账: " + year + "-" + month);
        }

        // 结转凭证不能直接删（它是已记账的历史），作废保留痕迹
        if (period.getClosingVoucherId() != null) {
            voucherRepository.findById(period.getClosingVoucherId()).ifPresent(voucher -> {
                if (!ScrmVoucherEntity.STATUS_VOID.equals(voucher.getStatus())) {
                    voucher.setStatus(ScrmVoucherEntity.STATUS_VOID);
                    voucher.setVoidedBy(operator());
                    voucher.setVoidedAt(LocalDateTime.now());
                    voucher.setRemark("反结账作废 " + voucher.getRemark());
                    voucherRepository.save(voucher);
                }
            });
        }

        period.setStatus(ScrmAccountPeriodEntity.STATUS_OPEN);
        period.setReopenedBy(operator());
        period.setReopenedAt(LocalDateTime.now());
        periodRepository.save(period);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", year + "-" + month);
        result.put("status", ScrmAccountPeriodEntity.STATUS_OPEN);
        result.put("message", "已反结账，结转凭证已作废，该期间可继续录入");
        return result;
    }

    /**
     * 期间状态清单（前端展示哪几期已结账）。
     *
     * @param companyId 公司主体 ID
     * @return 期间列表
     */
    @Transactional(readOnly = true)
    public List<ScrmAccountPeriodEntity> listPeriods(Long companyId) {
        return periodRepository.findByCompanyIdOrderByPeriodYearAscPeriodMonthAsc(companyId);
    }

    /** 构建一条分录。 */
    private ScrmVoucherEntryEntity buildEntry(Long companyId, int lineNo, String code, String name,
                                              BigDecimal debit, BigDecimal credit, String summary) {
        ScrmVoucherEntryEntity entry = new ScrmVoucherEntryEntity();
        entry.setCompanyId(companyId);
        entry.setLineNo(lineNo);
        entry.setSubjectCode(code);
        entry.setSubjectName(name);
        entry.setDebitAmount(debit.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        entry.setCreditAmount(credit.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        entry.setSummary(summary);
        return entry;
    }

    /** Object → BigDecimal。 */
    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value.toString()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 当前操作人。 */
    private String operator() {
        String userId = UserContext.getUserId();
        return userId != null && !userId.isBlank() ? userId : "SYSTEM";
    }
}
