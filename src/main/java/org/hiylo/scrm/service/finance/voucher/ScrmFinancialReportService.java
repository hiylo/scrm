/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmFinancialReportService.java
 * Date : 2026/10/25 21:50:00
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务报表服务（凭证域）：资产负债表 + 利润表。
 * <p>
 * <b>两个必须讲清的口径</b>：
 * </p>
 * <ol>
 *   <li><b>未分配利润要包含未结转的损益</b>。会计恒等式是
 *       资产 = 负债 + 所有者权益。若当期损益还没结转，损益类科目仍有余额，
 *       此时只按 3103/3104 取数会让报表不平。因此未分配利润
 *       = 3103 + 3104 + 未结转损益净额，这样<b>任何时点</b>都能对上，
 *       不需要会计先结账再看报表。</li>
 *   <li><b>利润表必须排除结转凭证</b>。结转凭证的分录也落在损益类科目上
 *       （借收入/贷费用清零），若不过滤，结账后利润会算成 0。
 *       判断依据是凭证上的 {@code is_closing} 标记，不是凭证字。</li>
 * </ol>
 * <p>
 * 科目按<b>前 4 位</b>归并到报表行，这样企业自建的明细科目
 * （如 560201 办公费）也能自动归入「管理费用」。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmFinancialReportService {

    /** 金额精度 */
    private static final int MONEY_SCALE = 2;

    /** 科目前缀长度（一级科目） */
    private static final int LEVEL1_LENGTH = 4;

    /** 本年利润 */
    private static final String SUBJECT_CURRENT_PROFIT = "3103";
    /** 利润分配 */
    private static final String SUBJECT_PROFIT_DISTRIBUTION = "3104";

    /** 科目仓储 */
    private final ScrmAccountSubjectRepository subjectRepository;

    /** 分录仓储 */
    private final ScrmVoucherEntryRepository entryRepository;

    /** 主体仓储 */
    private final ScrmCompanyRepository companyRepository;

    /**
     * 资产负债表。
     *
     * @param companyId 公司主体 ID
     * @param year      年
     * @param month     月
     * @return 资产/负债/权益三张表 + 恒等式校验
     * @throws ScrmException 主体不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> balanceSheet(Long companyId, int year, int month) throws ScrmException {
        companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));

        int yyyymm = year * 100 + month;
        // 期末：截至本期的累计发生额
        Map<String, BigDecimal[]> closing = aggregate(entryRepository.sumUpTo(companyId, yyyymm));
        // 年初：截至上一年 12 月
        Map<String, BigDecimal[]> opening = aggregate(
                entryRepository.sumUpTo(companyId, (year - 1) * 100 + 12));

        List<Map<String, Object>> assets = new ArrayList<>();
        List<Map<String, Object>> liabilities = new ArrayList<>();
        List<Map<String, Object>> equities = new ArrayList<>();

        BigDecimal totalAssetClosing = BigDecimal.ZERO;
        BigDecimal totalAssetOpening = BigDecimal.ZERO;
        BigDecimal totalLiabilityClosing = BigDecimal.ZERO;
        BigDecimal totalLiabilityOpening = BigDecimal.ZERO;
        BigDecimal totalEquityClosing = BigDecimal.ZERO;
        BigDecimal totalEquityOpening = BigDecimal.ZERO;

        for (Object[] lineDef : BALANCE_SHEET_LINES) {
            String name = (String) lineDef[0];
            String side = (String) lineDef[1];
            String[] codes = (String[]) lineDef[2];

            BigDecimal c;
            BigDecimal o;
            if ("ASSET".equals(side)) {
                // 资产取借记净额；备抵科目（累计折旧/摊销）净额为负，天然抵减
                c = sumNet(closing, codes, true);
                o = sumNet(opening, codes, true);
            } else {
                // 负债/权益取贷记净额
                c = sumNet(closing, codes, false);
                o = sumNet(opening, codes, false);
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("closing", c);
            row.put("opening", o);

            if ("ASSET".equals(side)) {
                assets.add(row);
                totalAssetClosing = totalAssetClosing.add(c);
                totalAssetOpening = totalAssetOpening.add(o);
            } else if ("LIABILITY".equals(side)) {
                liabilities.add(row);
                totalLiabilityClosing = totalLiabilityClosing.add(c);
                totalLiabilityOpening = totalLiabilityOpening.add(o);
            } else {
                equities.add(row);
                totalEquityClosing = totalEquityClosing.add(c);
                totalEquityOpening = totalEquityOpening.add(o);
            }
        }

        // 未分配利润 = 本年利润 + 利润分配 + 未结转损益净额
        // 最后一项是让报表在任何时点（含未结账）都能平衡的关键
        BigDecimal unclosedProfitClosing = unclosedProfit(closing);
        BigDecimal unclosedProfitOpening = unclosedProfit(opening);
        BigDecimal retainedClosing = sumNet(closing,
                new String[]{SUBJECT_CURRENT_PROFIT, SUBJECT_PROFIT_DISTRIBUTION}, false)
                .add(unclosedProfitClosing);
        BigDecimal retainedOpening = sumNet(opening,
                new String[]{SUBJECT_CURRENT_PROFIT, SUBJECT_PROFIT_DISTRIBUTION}, false)
                .add(unclosedProfitOpening);

        Map<String, Object> retained = new LinkedHashMap<>();
        retained.put("name", "未分配利润");
        retained.put("closing", retainedClosing);
        retained.put("opening", retainedOpening);
        equities.add(retained);
        totalEquityClosing = totalEquityClosing.add(retainedClosing);
        totalEquityOpening = totalEquityOpening.add(retainedOpening);

        BigDecimal liabilityAndEquityClosing = totalLiabilityClosing.add(totalEquityClosing);
        BigDecimal liabilityAndEquityOpening = totalLiabilityOpening.add(totalEquityOpening);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", yyyymm);
        result.put("assets", assets);
        result.put("liabilities", liabilities);
        result.put("equities", equities);
        result.put("totalAssetClosing", totalAssetClosing);
        result.put("totalAssetOpening", totalAssetOpening);
        result.put("totalLiabilityClosing", totalLiabilityClosing);
        result.put("totalLiabilityOpening", totalLiabilityOpening);
        result.put("totalEquityClosing", totalEquityClosing);
        result.put("totalEquityOpening", totalEquityOpening);
        result.put("totalLiabilityAndEquityClosing", liabilityAndEquityClosing);
        result.put("totalLiabilityAndEquityOpening", liabilityAndEquityOpening);
        // 会计恒等式：资产 = 负债 + 所有者权益
        result.put("balanced", totalAssetClosing.compareTo(liabilityAndEquityClosing) == 0
                && totalAssetOpening.compareTo(liabilityAndEquityOpening) == 0);
        return result;
    }

    /**
     * 利润表（本期 + 本年累计）。
     *
     * @param companyId 公司主体 ID
     * @param year      年
     * @param month     月
     * @return 利润表各行 + 净利润
     * @throws ScrmException 主体不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> incomeStatement(Long companyId, int year, int month)
            throws ScrmException {
        companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));

        // 本期与本年累计都排除结转凭证，否则结账后利润会被清零
        Map<String, BigDecimal[]> current = aggregate(
                entryRepository.sumBetweenExcludingClosing(
                        companyId, year * 100 + month, year * 100 + month));
        Map<String, BigDecimal[]> yearToDate = aggregate(
                entryRepository.sumBetweenExcludingClosing(
                        companyId, year * 100 + 1, year * 100 + month));

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal revenueCur = income(current, "5001", "5051");
        BigDecimal revenueYtd = income(yearToDate, "5001", "5051");
        BigDecimal costCur = expense(current, "5401", "5402");
        BigDecimal costYtd = expense(yearToDate, "5401", "5402");
        BigDecimal taxCur = expense(current, "5403");
        BigDecimal taxYtd = expense(yearToDate, "5403");
        BigDecimal sellingCur = expense(current, "5601");
        BigDecimal sellingYtd = expense(yearToDate, "5601");
        BigDecimal adminCur = expense(current, "5602");
        BigDecimal adminYtd = expense(yearToDate, "5602");
        BigDecimal financeCur = expense(current, "5603");
        BigDecimal financeYtd = expense(yearToDate, "5603");

        addRow(rows, "一、营业收入", revenueCur, revenueYtd);
        addRow(rows, "减：营业成本", costCur, costYtd);
        addRow(rows, "　　税金及附加", taxCur, taxYtd);
        addRow(rows, "　　销售费用", sellingCur, sellingYtd);
        addRow(rows, "　　管理费用", adminCur, adminYtd);
        addRow(rows, "　　财务费用", financeCur, financeYtd);

        BigDecimal opProfitCur = revenueCur.subtract(costCur).subtract(taxCur)
                .subtract(sellingCur).subtract(adminCur).subtract(financeCur);
        BigDecimal opProfitYtd = revenueYtd.subtract(costYtd).subtract(taxYtd)
                .subtract(sellingYtd).subtract(adminYtd).subtract(financeYtd);
        addRow(rows, "二、营业利润", opProfitCur, opProfitYtd);

        BigDecimal nonOpInCur = income(current, "5301");
        BigDecimal nonOpInYtd = income(yearToDate, "5301");
        BigDecimal nonOpOutCur = expense(current, "5711");
        BigDecimal nonOpOutYtd = expense(yearToDate, "5711");
        addRow(rows, "加：营业外收入", nonOpInCur, nonOpInYtd);
        addRow(rows, "减：营业外支出", nonOpOutCur, nonOpOutYtd);

        BigDecimal totalProfitCur = opProfitCur.add(nonOpInCur).subtract(nonOpOutCur);
        BigDecimal totalProfitYtd = opProfitYtd.add(nonOpInYtd).subtract(nonOpOutYtd);
        addRow(rows, "三、利润总额", totalProfitCur, totalProfitYtd);

        BigDecimal taxExpenseCur = expense(current, "5801");
        BigDecimal taxExpenseYtd = expense(yearToDate, "5801");
        addRow(rows, "减：所得税费用", taxExpenseCur, taxExpenseYtd);

        BigDecimal netProfitCur = totalProfitCur.subtract(taxExpenseCur);
        BigDecimal netProfitYtd = totalProfitYtd.subtract(taxExpenseYtd);
        addRow(rows, "四、净利润", netProfitCur, netProfitYtd);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", year * 100 + month);
        result.put("rows", rows);
        result.put("revenueCurrent", revenueCur);
        result.put("revenueYtd", revenueYtd);
        result.put("netProfitCurrent", netProfitCur);
        result.put("netProfitYtd", netProfitYtd);
        return result;
    }

    /**
     * 汇总损益类未结转净额（收入 − 费用），用于让资产负债表在未结账时也平衡。
     *
     * @param aggregate 科目 → [借, 贷]
     * @return 未结转利润（正为盈利）
     */
    private BigDecimal unclosedProfit(Map<String, BigDecimal[]> aggregate) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> entry : aggregate.entrySet()) {
            String code = entry.getKey();
            BigDecimal debit = entry.getValue()[0];
            BigDecimal credit = entry.getValue()[1];
            // 损益类科目编码前两位为 5（小企业会计准则损益类）
            if (!code.startsWith("5")) {
                continue;
            }
            // 收入类净额为贷方（credit-debit>0），费用类净额为借方
            BigDecimal net = credit.subtract(debit);
            if (net.compareTo(BigDecimal.ZERO) > 0) {
                income = income.add(net);
            } else {
                expense = expense.add(net.negate());
            }
        }
        return income.subtract(expense);
    }

    /** 聚合结果转 Map：key = 科目前 4 位，value = [借合计, 贷合计]（明细科目已归并）。 */
    private Map<String, BigDecimal[]> aggregate(List<Object[]> rows) {
        Map<String, BigDecimal[]> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String code = String.valueOf(row[0]);
            String key = code.length() > LEVEL1_LENGTH ? code.substring(0, LEVEL1_LENGTH) : code;
            BigDecimal debit = toDecimal(row[2]);
            BigDecimal credit = toDecimal(row[3]);
            BigDecimal[] slot = map.computeIfAbsent(key,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            slot[0] = slot[0].add(debit);
            slot[1] = slot[1].add(credit);
        }
        return map;
    }

    /** 按科目集合求借方净额（借−贷）或贷方净额（贷−借）。 */
    private BigDecimal sumNet(Map<String, BigDecimal[]> aggregate, String[] codes, boolean debitSide) {
        BigDecimal total = BigDecimal.ZERO;
        for (String code : codes) {
            BigDecimal[] slot = aggregate.get(code);
            if (slot == null) {
                continue;
            }
            total = total.add(debitSide
                    ? slot[0].subtract(slot[1]) : slot[1].subtract(slot[0]));
        }
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 收入类净额（贷−借），支持多个科目。 */
    private BigDecimal income(Map<String, BigDecimal[]> aggregate, String... codes) {
        BigDecimal total = BigDecimal.ZERO;
        for (String code : codes) {
            BigDecimal[] slot = aggregate.get(code);
            if (slot != null) {
                total = total.add(slot[1].subtract(slot[0]));
            }
        }
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 费用类净额（借−贷），支持多个科目。 */
    private BigDecimal expense(Map<String, BigDecimal[]> aggregate, String... codes) {
        BigDecimal total = BigDecimal.ZERO;
        for (String code : codes) {
            BigDecimal[] slot = aggregate.get(code);
            if (slot != null) {
                total = total.add(slot[0].subtract(slot[1]));
            }
        }
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 追加一行。 */
    private void addRow(List<Map<String, Object>> rows, String name,
                        BigDecimal current, BigDecimal ytd) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("current", current.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        row.put("ytd", ytd.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        rows.add(row);
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

    /**
     * 资产负债表映射：{行名, 侧(ASSET/LIABILITY/EQUITY), 科目编码数组}。
     * <p>
     * 按小企业会计准则常用科目编排。累计折旧/累计摊销属资产类但为贷方余额，
     * 其借记净额为负，直接相加即天然抵减，无需特殊处理。
     * </p>
     */
    private static final Object[][] BALANCE_SHEET_LINES = {
            // ---- 资产 ----
            {"货币资金", "ASSET", new String[]{"1001", "1002", "1012"}},
            {"应收票据", "ASSET", new String[]{"1121"}},
            {"应收账款", "ASSET", new String[]{"1122"}},
            {"预付账款", "ASSET", new String[]{"1123"}},
            {"其他应收款", "ASSET", new String[]{"1221"}},
            // 成本类科目（生产成本/制造费用/研发支出/工程施工）期末有余额时属于存货，
            // 漏掉它们会让资产负债表差额恰好多出这部分
            {"存货", "ASSET", new String[]{"1401", "1402", "1403", "1404", "1405", "1406", "1408", "1411",
                    "4001", "4101", "4301", "4401"}},
            {"固定资产原价", "ASSET", new String[]{"1601"}},
            {"减：累计折旧", "ASSET", new String[]{"1602"}},
            {"在建工程", "ASSET", new String[]{"1604"}},
            {"无形资产", "ASSET", new String[]{"1701"}},
            {"减：累计摊销", "ASSET", new String[]{"1702"}},
            {"长期待摊费用", "ASSET", new String[]{"1801"}},
            // ---- 负债 ----
            {"短期借款", "LIABILITY", new String[]{"2001"}},
            {"应付票据", "LIABILITY", new String[]{"2201"}},
            {"应付账款", "LIABILITY", new String[]{"2202"}},
            {"预收账款", "LIABILITY", new String[]{"2203"}},
            {"应付职工薪酬", "LIABILITY", new String[]{"2211"}},
            {"应交税费", "LIABILITY", new String[]{"2221"}},
            {"应付利息", "LIABILITY", new String[]{"2231"}},
            {"应付利润", "LIABILITY", new String[]{"2232"}},
            {"其他应付款", "LIABILITY", new String[]{"2241"}},
            {"长期借款", "LIABILITY", new String[]{"2501"}},
            // ---- 所有者权益（未分配利润单独计算）----
            {"实收资本", "EQUITY", new String[]{"3001"}},
            {"资本公积", "EQUITY", new String[]{"3002"}},
            {"盈余公积", "EQUITY", new String[]{"3101"}},
    };
}
