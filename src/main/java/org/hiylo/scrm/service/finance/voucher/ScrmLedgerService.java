/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmLedgerService.java
 * Date : 2026/10/25 22:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountSubjectRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmVoucherEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账簿服务（凭证域）：总账 / 明细账 / 序时账。
 * <p>
 * 三者回答不同问题，不能互相替代：
 * </p>
 * <ul>
 *   <li><b>总账</b>：一级科目的期初/本期发生/期末，看整体结构，验证试算平衡；</li>
 *   <li><b>明细账</b>：单科目逐笔流水与<b>逐笔余额</b>，查「这笔钱是怎么走成现在这样的」；</li>
 *   <li><b>序时账</b>：按期顺序列全部凭证分录，查「那天的账记了什么」，审计视角。</li>
 * </ul>
 * <p>
 * 只统计已记账（POSTED）凭证——草稿是没定稿的账，作废凭证已退出余额。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmLedgerService {

    /** 金额精度 */
    private static final int MONEY_SCALE = 2;

    /** 一级科目编码长度 */
    private static final int LEVEL1_LENGTH = 4;

    /** 科目仓储 */
    private final ScrmAccountSubjectRepository subjectRepository;

    /** 分录仓储 */
    private final ScrmVoucherEntryRepository entryRepository;

    /** 主体仓储 */
    private final ScrmCompanyRepository companyRepository;

    /**
     * 总账：按一级科目汇总期初/本期/期末。
     *
     * @param companyId 公司主体 ID
     * @param year      年
     * @param month     月
     * @return 总账行 + 合计
     * @throws ScrmException 主体不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generalLedger(Long companyId, int year, int month) throws ScrmException {
        companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));

        int yyyymm = year * 100 + month;
        Map<String, BigDecimal[]> opening = rollUp(entryRepository.sumBeforePeriod(companyId, yyyymm));
        Map<String, BigDecimal[]> current = rollUp(entryRepository.sumInPeriod(companyId, yyyymm));

        // 科目名称取一级科目（明细科目已归并）
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, String> categories = new LinkedHashMap<>();
        for (ScrmAccountSubjectEntity subject : subjectRepository
                .findByCompanyIdOrderBySubjectCodeAsc(companyId)) {
            String code = subject.getSubjectCode();
            if (code.length() <= LEVEL1_LENGTH) {
                names.put(code, subject.getSubjectName());
                categories.put(code, subject.getCategory());
            }
        }

        List<String> allCodes = new ArrayList<>();
        for (String code : opening.keySet()) {
            if (!allCodes.contains(code)) {
                allCodes.add(code);
            }
        }
        for (String code : current.keySet()) {
            if (!allCodes.contains(code)) {
                allCodes.add(code);
            }
        }
        allCodes.sort(String::compareTo);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalOpeningDebit = BigDecimal.ZERO;
        BigDecimal totalOpeningCredit = BigDecimal.ZERO;
        BigDecimal totalPeriodDebit = BigDecimal.ZERO;
        BigDecimal totalPeriodCredit = BigDecimal.ZERO;
        BigDecimal totalClosingDebit = BigDecimal.ZERO;
        BigDecimal totalClosingCredit = BigDecimal.ZERO;

        for (String code : allCodes) {
            BigDecimal[] open = opening.getOrDefault(code,
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] cur = current.getOrDefault(code,
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

            BigDecimal openingNet = open[0].subtract(open[1]);
            BigDecimal openingDebit = openingNet.compareTo(BigDecimal.ZERO) > 0
                    ? openingNet : BigDecimal.ZERO;
            BigDecimal openingCredit = openingNet.compareTo(BigDecimal.ZERO) < 0
                    ? openingNet.negate() : BigDecimal.ZERO;

            BigDecimal closingNet = open[0].add(cur[0]).subtract(open[1].add(cur[1]));
            BigDecimal closingDebit = closingNet.compareTo(BigDecimal.ZERO) > 0
                    ? closingNet : BigDecimal.ZERO;
            BigDecimal closingCredit = closingNet.compareTo(BigDecimal.ZERO) < 0
                    ? closingNet.negate() : BigDecimal.ZERO;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subjectCode", code);
            row.put("subjectName", names.getOrDefault(code, code));
            row.put("category", categories.getOrDefault(code, ""));
            row.put("openingDebit", scale(openingDebit));
            row.put("openingCredit", scale(openingCredit));
            row.put("periodDebit", scale(cur[0]));
            row.put("periodCredit", scale(cur[1]));
            row.put("closingDebit", scale(closingDebit));
            row.put("closingCredit", scale(closingCredit));
            rows.add(row);

            totalOpeningDebit = totalOpeningDebit.add(openingDebit);
            totalOpeningCredit = totalOpeningCredit.add(openingCredit);
            totalPeriodDebit = totalPeriodDebit.add(cur[0]);
            totalPeriodCredit = totalPeriodCredit.add(cur[1]);
            totalClosingDebit = totalClosingDebit.add(closingDebit);
            totalClosingCredit = totalClosingCredit.add(closingCredit);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", yyyymm);
        result.put("rows", rows);
        result.put("totalOpeningDebit", scale(totalOpeningDebit));
        result.put("totalOpeningCredit", scale(totalOpeningCredit));
        result.put("totalPeriodDebit", scale(totalPeriodDebit));
        result.put("totalPeriodCredit", scale(totalPeriodCredit));
        result.put("totalClosingDebit", scale(totalClosingDebit));
        result.put("totalClosingCredit", scale(totalClosingCredit));
        result.put("balanced", totalClosingDebit.compareTo(totalClosingCredit) == 0);
        return result;
    }

    /**
     * 明细账：单科目逐笔流水 + 逐笔余额。
     *
     * @param companyId   公司主体 ID
     * @param subjectCode 科目编码（可为一级或明细）
     * @param year        年
     * @param month       月
     * @return 期初 + 逐笔明细 + 本期合计 + 期末
     * @throws ScrmException 主体不存在 / 科目编码为空
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detailLedger(Long companyId, String subjectCode, int year, int month)
            throws ScrmException {
        if (subjectCode == null || subjectCode.isBlank()) {
            throw ScrmException.badRequest("科目编码不能为空");
        }
        companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));

        String code = subjectCode.trim();
        int yyyymm = year * 100 + month;

        // 期初：该科目（含下级明细）在此期之前的累计
        BigDecimal[] before = sumByPrefix(entryRepository.sumBeforePeriod(companyId, yyyymm), code);
        BigDecimal openingNet = before[0].subtract(before[1]);

        // 逐笔：本期该科目的所有分录
        List<Object[]> details = entryRepository.findEntryDetails(
                companyId, yyyymm, yyyymm, code);

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal running = openingNet;
        BigDecimal periodDebit = BigDecimal.ZERO;
        BigDecimal periodCredit = BigDecimal.ZERO;

        for (Object[] row : details) {
            LocalDate voucherDate = row[0] instanceof LocalDate d ? d
                    : LocalDate.parse(String.valueOf(row[0]));
            String word = String.valueOf(row[1]);
            Integer no = row[2] == null ? 0 : ((Number) row[2]).intValue();
            String summary = row[3] == null ? "" : String.valueOf(row[3]);
            String entrySubjectCode = String.valueOf(row[4]);
            String entrySubjectName = row[5] == null ? "" : String.valueOf(row[5]);
            BigDecimal debit = toDecimal(row[6]);
            BigDecimal credit = toDecimal(row[7]);
            String voucherRemark = row[8] == null ? "" : String.valueOf(row[8]);

            running = running.add(debit).subtract(credit);
            periodDebit = periodDebit.add(debit);
            periodCredit = periodCredit.add(credit);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("voucherDate", voucherDate.toString());
            item.put("voucherLabel", word + "-" + String.format("%04d", no));
            item.put("summary", summary);
            item.put("voucherRemark", voucherRemark);
            item.put("subjectCode", entrySubjectCode);
            item.put("subjectName", entrySubjectName);
            item.put("debitAmount", scale(debit));
            item.put("creditAmount", scale(credit));
            // 余额一律用「借−贷」表示，负数即贷方余额
            item.put("balance", scale(running));
            item.put("balanceDirection", running.compareTo(BigDecimal.ZERO) >= 0 ? "借" : "贷");
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", yyyymm);
        result.put("subjectCode", code);
        result.put("openingBalance", scale(openingNet));
        result.put("openingDirection", openingNet.compareTo(BigDecimal.ZERO) >= 0 ? "借" : "贷");
        result.put("items", items);
        result.put("periodDebit", scale(periodDebit));
        result.put("periodCredit", scale(periodCredit));
        result.put("closingBalance", scale(running));
        result.put("closingDirection", running.compareTo(BigDecimal.ZERO) >= 0 ? "借" : "贷");
        return result;
    }

    /**
     * 序时账：按期顺序列出全部凭证分录。
     *
     * @param companyId 公司主体 ID
     * @param year      年
     * @param month     月
     * @return 分录流水 + 借贷合计
     * @throws ScrmException 主体不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> journal(Long companyId, int year, int month) throws ScrmException {
        companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));

        int yyyymm = year * 100 + month;
        List<Object[]> details = entryRepository.findEntryDetails(
                companyId, yyyymm, yyyymm, null);

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (Object[] row : details) {
            LocalDate voucherDate = row[0] instanceof LocalDate d ? d
                    : LocalDate.parse(String.valueOf(row[0]));
            String word = String.valueOf(row[1]);
            Integer no = row[2] == null ? 0 : ((Number) row[2]).intValue();
            BigDecimal debit = toDecimal(row[6]);
            BigDecimal credit = toDecimal(row[7]);
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("voucherDate", voucherDate.toString());
            item.put("voucherLabel", word + "-" + String.format("%04d", no));
            item.put("summary", row[3] == null ? "" : String.valueOf(row[3]));
            item.put("subjectCode", String.valueOf(row[4]));
            item.put("subjectName", row[5] == null ? "" : String.valueOf(row[5]));
            item.put("debitAmount", scale(debit));
            item.put("creditAmount", scale(credit));
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", yyyymm);
        result.put("items", items);
        result.put("totalDebit", scale(totalDebit));
        result.put("totalCredit", scale(totalCredit));
        result.put("balanced", totalDebit.compareTo(totalCredit) == 0);
        return result;
    }

    /**
     * 把明细科目发生额归并到一级科目。
     *
     * @param rows 原始聚合行
     * @return 一级科目 → [借, 贷]
     */
    private Map<String, BigDecimal[]> rollUp(List<Object[]> rows) {
        Map<String, BigDecimal[]> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String code = String.valueOf(row[0]);
            String key = code.length() > LEVEL1_LENGTH ? code.substring(0, LEVEL1_LENGTH) : code;
            BigDecimal[] slot = map.computeIfAbsent(key,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            slot[0] = slot[0].add(toDecimal(row[2]));
            slot[1] = slot[1].add(toDecimal(row[3]));
        }
        return map;
    }

    /** 按科目前缀汇总（明细账期初用）。 */
    private BigDecimal[] sumByPrefix(List<Object[]> rows, String prefix) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (Object[] row : rows) {
            String code = String.valueOf(row[0]);
            if (code.startsWith(prefix)) {
                debit = debit.add(toDecimal(row[2]));
                credit = credit.add(toDecimal(row[3]));
            }
        }
        return new BigDecimal[]{debit, credit};
    }

    /** 金额归一。 */
    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
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
}
