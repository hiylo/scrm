/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmClientPortalService.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.portal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationProfileRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationRecordRepository;
import org.hiylo.scrm.service.finance.asset.ScrmFixedAssetService;
import org.hiylo.scrm.service.finance.tax.ScrmTaxRiskProfileService;
import org.hiylo.scrm.service.finance.voucher.ScrmFinancialReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客户查账门户（V28：老板查账 · 低成本 H5 版，竞品差距 P0③）。
 * <p>
 * 面向代账客户的<b>只读查账面板</b>：把公司概况、报税进度、财务报表、
 * 固定资产、税负风险五块既有数据聚合到一个视图，减少会计反复回答
 * 「这个月报了吗 / 回执给我」的重复劳动。
 * </p>
 * <p>
 * 设计约束：
 * </p>
 * <ul>
 *   <li><b>纯只读</b>——不写任何表，不触发状态机（安全，可直接放开给客户）</li>
 *   <li><b>无新表、无新外部依赖</b>——完全复用 申报/报表/资产/税负 既有服务</li>
 *   <li><b>逐块容错</b>——任一区块异常只置该块为 null，不拖垮整页
 *       （复用 FinanceOverview 的 allSettled 思路）</li>
 *   <li><b>数据隔离</b>——controller 层过 {@code financeDataScope.canAccessCompany}</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmClientPortalService {

    /** 已取消状态（欠缴统计排除） */
    private static final String STATUS_CANCELED = "CANCELED";

    /** 逾期状态 */
    private static final String STATUS_OVERDUE = "OVERDUE";

    /** 待申报状态（未开始 + 待申报） */
    private static final List<String> PENDING_STATUSES = List.of("NOT_STARTED", "PENDING");

    /** 公司主体数据访问 */
    private final ScrmCompanyRepository companyRepository;

    /** 申报档案数据访问 */
    private final ScrmTaxDeclarationProfileRepository profileRepository;

    /** 申报记录数据访问 */
    private final ScrmTaxDeclarationRecordRepository recordRepository;

    /** 财务报表服务（资产负债表 + 利润表） */
    private final ScrmFinancialReportService financialReportService;

    /** 固定资产服务（公司资产摘要） */
    private final ScrmFixedAssetService fixedAssetService;

    /** 税负风险画像服务 */
    private final ScrmTaxRiskProfileService riskProfileService;

    /**
     * 聚合单主体的查账全景（公司概况 + 报税进度 + 近 3 期报表 + 资产 + 税负）。
     *
     * @param companyId 公司主体 ID
     * @param year      查账年度
     * @return 聚合 Map（各区块独立容错）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> overview(Long companyId, int year) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", companyId);
        result.put("year", year);
        result.put("company", companyInfo(companyId));
        result.put("taxSummary", taxSummary(companyId, year));
        result.put("reports", reports(companyId, year));
        result.put("assets", safeAssetSummary(companyId));
        result.put("taxRisk", safeRiskProfile(companyId, year));
        return result;
    }

    /** 公司概况块（关键字段） */
    private Map<String, Object> companyInfo(Long companyId) {
        Optional<ScrmCompanyEntity> opt = companyRepository.findById(companyId);
        if (opt.isEmpty()) {
            return null;
        }
        ScrmCompanyEntity c = opt.get();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("companyName", c.getCompanyName());
        info.put("creditCode", c.getCreditCode());
        info.put("legalRepresentative", c.getLegalRepresentative());
        info.put("companyType", c.getCompanyType());
        info.put("registeredCapital", c.getRegisteredCapital());
        info.put("industry", c.getIndustry());
        info.put("businessStatus", c.getBusinessStatus());
        info.put("taxStatus", c.getTaxStatus());
        info.put("serviceStatus", c.getServiceStatus());
        info.put("serviceEndDate", c.getServiceEndDate());
        info.put("accountantName", c.getAccountantName());
        return info;
    }

    /** 报税进度块（统计 + 最近记录） */
    private Map<String, Object> taxSummary(Long companyId, int year) {
        List<ScrmTaxDeclarationProfileEntity> profiles =
                profileRepository.findByCompanyIdOrderByCreateTimeDesc(companyId);
        if (profiles.isEmpty()) {
            return emptyTaxSummary();
        }
        List<Long> profileIds = profiles.stream()
                .map(ScrmTaxDeclarationProfileEntity::getId).toList();
        List<ScrmTaxDeclarationRecordEntity> all =
                recordRepository.findByProfileIdInOrderByPeriodStartAsc(profileIds);
        List<ScrmTaxDeclarationRecordEntity> yearRecords = all.stream()
                .filter(r -> r.getPeriodStart() != null && r.getPeriodStart().getYear() == year)
                .toList();

        long overdue = 0L;
        long underpaid = 0L;
        long pending = 0L;
        long filed = 0L;
        for (ScrmTaxDeclarationRecordEntity r : yearRecords) {
            if (STATUS_OVERDUE.equals(r.getDeclarationStatus())
                    || (r.getOverdueDays() != null && r.getOverdueDays() > 0)) {
                overdue++;
            }
            boolean paid = r.getPaidAmount() != null && r.getTaxAmount() != null
                    && r.getPaidAmount().compareTo(r.getTaxAmount()) >= 0;
            boolean due = r.getDueDate() != null
                    && !r.getDueDate().isAfter(LocalDate.now());
            if (!paid && due && !STATUS_CANCELED.equals(r.getDeclarationStatus())) {
                underpaid++;
            }
            if (PENDING_STATUSES.contains(r.getDeclarationStatus())) {
                pending++;
            } else if ("FILED".equals(r.getDeclarationStatus())
                    || "PAID".equals(r.getDeclarationStatus())
                    || "ARCHIVED".equals(r.getDeclarationStatus())) {
                filed++;
            }
        }
        List<Map<String, Object>> recent = new ArrayList<>();
        int from = Math.max(0, yearRecords.size() - 6);
        for (ScrmTaxDeclarationRecordEntity r : yearRecords.subList(from, yearRecords.size())) {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("id", r.getId());
            rec.put("taxType", r.getTaxType());
            rec.put("periodStart", r.getPeriodStart());
            rec.put("periodEnd", r.getPeriodEnd());
            rec.put("dueDate", r.getDueDate());
            rec.put("taxAmount", r.getTaxAmount());
            rec.put("paidAmount", r.getPaidAmount());
            rec.put("declarationStatus", r.getDeclarationStatus());
            rec.put("overdueDays", r.getOverdueDays());
            recent.add(rec);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("recordTotal", yearRecords.size());
        summary.put("pendingCount", pending);
        summary.put("filedCount", filed);
        summary.put("overdueCount", overdue);
        summary.put("underpaidCount", underpaid);
        summary.put("recentRecords", recent);
        return summary;
    }

    private Map<String, Object> emptyTaxSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("recordTotal", 0L);
        summary.put("pendingCount", 0L);
        summary.put("filedCount", 0L);
        summary.put("overdueCount", 0L);
        summary.put("underpaidCount", 0L);
        summary.put("recentRecords", List.of());
        return summary;
    }

    /** 财务报表块（近 3 期利润表 + 最新资产负债表，均容错） */
    private Map<String, Object> reports(Long companyId, int year) {
        LocalDate now = LocalDate.now();
        int latestMonth = year == now.getYear() ? now.getMonthValue() : 12;

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("year", year);
        block.put("latestMonth", latestMonth);
        block.put("incomeStatements", incomeStatements(companyId, year, latestMonth));
        block.put("balanceSheet", safeBalanceSheet(companyId, year, latestMonth));
        return block;
    }

    /** 近 3 期利润表（month-2 / month-1 / month） */
    private List<Map<String, Object>> incomeStatements(Long companyId, int year, int latestMonth) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 2; i >= 0; i--) {
            int m = latestMonth - i;
            if (m < 1) {
                continue;
            }
            list.add(safeIncomeStatement(companyId, year, m));
        }
        return list;
    }

    private Map<String, Object> safeIncomeStatement(Long companyId, int year, int month) {
        try {
            Map<String, Object> stmt = financialReportService.incomeStatement(companyId, year, month);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("period", s(stmt.get("period")));
            out.put("revenueCurrent", stmt.get("revenueCurrent"));
            out.put("revenueYtd", stmt.get("revenueYtd"));
            out.put("netProfitCurrent", stmt.get("netProfitCurrent"));
            out.put("netProfitYtd", stmt.get("netProfitYtd"));
            return out;
        } catch (Exception e) {
            log.debug("[查账-报表] 利润表取数失败: companyId={}, {}-{}, err={}",
                    companyId, year, month, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> safeBalanceSheet(Long companyId, int year, int month) {
        try {
            Map<String, Object> sheet = financialReportService.balanceSheet(companyId, year, month);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("period", s(sheet.get("period")));
            out.put("totalAssetClosing", sheet.get("totalAssetClosing"));
            out.put("totalLiabilityClosing", sheet.get("totalLiabilityClosing"));
            out.put("totalEquityClosing", sheet.get("totalEquityClosing"));
            out.put("totalLiabilityAndEquityClosing", sheet.get("totalLiabilityAndEquityClosing"));
            out.put("balanced", sheet.get("balanced"));
            return out;
        } catch (Exception e) {
            log.debug("[查账-报表] 资产负债表取数失败: companyId={}, {}-{}, err={}",
                    companyId, year, month, e.getMessage());
            return null;
        }
    }

    /** 固定资产摘要（容错） */
    private Map<String, Object> safeAssetSummary(Long companyId) {
        try {
            return fixedAssetService.companySummary(companyId);
        } catch (Exception e) {
            log.debug("[查账-资产] 固定资产汇总失败: companyId={}, err={}", companyId, e.getMessage());
            return null;
        }
    }

    /** 税负风险画像（容错，只取趋势摘要 + 风险项） */
    private Map<String, Object> safeRiskProfile(Long companyId, int year) {
        try {
            var profile = riskProfileService.buildProfile(companyId, year);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("recordTotal", profile.getRecordTotal());
            out.put("overdueCount", profile.getOverdueCount());
            out.put("underpaidCount", profile.getUnderpaidCount());
            out.put("risks", profile.getRisks());
            out.put("trend", profile.getTrend());
            out.put("generatedAt", profile.getGeneratedAt());
            return out;
        } catch (Exception e) {
            log.debug("[查账-税负] 税负风险画像失败: companyId={}, year={}, err={}",
                    companyId, year, e.getMessage());
            return null;
        }
    }

    private String s(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}