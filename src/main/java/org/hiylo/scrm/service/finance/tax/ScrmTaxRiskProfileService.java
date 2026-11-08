/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxRiskProfileService.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.tax;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmTaxRiskProfileDto;
import org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.hiylo.scrm.repository.ScrmTaxDeclarationProfileRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationRecordRepository;
import org.hiylo.scrm.service.finance.voucher.ScrmFinancialReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 税负风险画像服务（竞品差距 P1，纯只读分析）。
 * <p>
 * 从申报记录 + 利润表收入聚合出：
 * </p>
 * <ul>
 *   <li><b>增值税税负率趋势</b>（按月 = 应纳增值税 / 当月营业收入，取利润表
 *       {@code revenueCurrent}）；连续 3 个月 &lt; 1.0% → 偏低 WARN，
 *       连续 3 个月 &gt; 4.5% → 偏高 WARN</li>
 *   <li><b>申报逾期率</b>：OVERDUE 记录 / 总记录 &gt; 20% → CRITICAL</li>
 *   <li><b>欠缴</b>：已到截止日但实缴 &lt; 应缴且未取消 → WARN</li>
 * </ul>
 * <p>不做任何写操作；未找到记录时返回空 profile（不算错误）。
 * 收入/税负率取不到时标记 {@code 数据不足}。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmTaxRiskProfileService {

    /** 增值税税种标识 */
    private static final String TAX_TYPE_VAT = "VAT";

    /** 已取消状态（欠缴计算排除） */
    private static final String STATUS_CANCELED = "CANCELED";

    /** 逾期状态 */
    private static final String STATUS_OVERDUE = "OVERDUE";

    /** 税负率偏低阈值（连续 3 月低于此值预警） */
    private static final BigDecimal LOW_RATIO_THRESHOLD = new BigDecimal("1.0");

    /** 税负率偏高阈值（连续 3 月高于此值预警） */
    private static final BigDecimal HIGH_RATIO_THRESHOLD = new BigDecimal("4.5");

    /** 连续月数窗口 */
    private static final int CONSECUTIVE_MONTHS = 3;

    /** 逾期率预警阈值 */
    private static final BigDecimal OVERDUE_RATE_THRESHOLD = new BigDecimal("20.0");

    /** 申报档案数据访问 */
    private final ScrmTaxDeclarationProfileRepository profileRepository;

    /** 申报记录数据访问 */
    private final ScrmTaxDeclarationRecordRepository recordRepository;

    /** 财务报表服务（取营业收入） */
    private final ScrmFinancialReportService financialReportService;

    /**
     * 构建企业税负风险画像（指定年度，近 12 个月窗口内取申报记录）。
     *
     * @param companyId 企业 ID
     * @param year      分析年度
     * @return 画像（空记录 → 空画像）
     */
    @Transactional(readOnly = true)
    public ScrmTaxRiskProfileDto buildProfile(Long companyId, int year) {
        ScrmTaxRiskProfileDto profile = ScrmTaxRiskProfileDto.builder()
                .companyId(companyId)
                .year(year)
                .trend(new ArrayList<>())
                .risks(new ArrayList<>())
                .generatedAt(LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();

        // 1) 该企业申报档案 → 申报记录
        List<ScrmTaxDeclarationProfileEntity> profiles =
                profileRepository.findByCompanyIdOrderByCreateTimeDesc(companyId);
        List<ScrmTaxDeclarationRecordEntity> records = new ArrayList<>();
        if (!profiles.isEmpty()) {
            List<Long> profileIds = profiles.stream().map(ScrmTaxDeclarationProfileEntity::getId).toList();
            records = recordRepository.findByProfileIdInOrderByPeriodStartAsc(profileIds);
        }
        // 只保留指定年度的记录
        List<ScrmTaxDeclarationRecordEntity> yearRecords = records.stream()
                .filter(r -> r.getPeriodStart() != null && r.getPeriodStart().getYear() == year)
                .toList();
        profile.setRecordTotal(yearRecords.size());

        // 2) 逾期与欠缴统计
        long overdueCount = 0;
        long underpaidCount = 0;
        for (ScrmTaxDeclarationRecordEntity r : yearRecords) {
            if (STATUS_OVERDUE.equals(r.getDeclarationStatus())
                    || (r.getOverdueDays() != null && r.getOverdueDays() > 0)) {
                overdueCount++;
            }
            boolean paid = r.getPaidAmount() != null && r.getTaxAmount() != null
                    && r.getPaidAmount().compareTo(r.getTaxAmount()) >= 0;
            boolean due = r.getDueDate() != null
                    && !r.getDueDate().isAfter(java.time.LocalDate.now());
            if (!paid && due && !STATUS_CANCELED.equals(r.getDeclarationStatus())) {
                underpaidCount++;
            }
        }
        profile.setOverdueCount(overdueCount);
        profile.setUnderpaidCount(underpaidCount);

        // 3) 税负率趋势（按月聚合 VAT 申报 + 利润表收入）
        profile.setTrend(buildTrend(companyId, year, yearRecords));

        // 4) 风险规则
        buildRisks(profile);
        return profile;
    }

    /** 按月构建税负率趋势（近 12 个月或全年 12 个月） */
    private List<ScrmTaxRiskProfileDto.TrendItem> buildTrend(
            Long companyId, int year, List<ScrmTaxDeclarationRecordEntity> yearRecords) {
        Map<Integer, BigDecimal> vatByMonth = new LinkedHashMap<>();
        for (ScrmTaxDeclarationRecordEntity r : yearRecords) {
            if (TAX_TYPE_VAT.equals(r.getTaxType())
                    && r.getPeriodStart() != null
                    && r.getTaxAmount() != null) {
                int month = r.getPeriodStart().getMonthValue();
                vatByMonth.merge(month, r.getTaxAmount(), BigDecimal::add);
            }
        }
        List<ScrmTaxRiskProfileDto.TrendItem> trend = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            String monthLabel = String.format("%04d-%02d", year, month);
            BigDecimal vat = vatByMonth.get(month);
            BigDecimal revenue = null;
            try {
                Map<String, Object> stmt = financialReportService.incomeStatement(companyId, year, month);
                Object rev = stmt == null ? null : stmt.get("revenueCurrent");
                revenue = rev instanceof Number ? new BigDecimal(rev.toString()) : null;
            } catch (Exception e) {
                log.debug("[税负画像] 利润表取数失败, 标记数据不足: companyId={}, {}-{}, err={}",
                        companyId, year, month, e.getMessage());
            }
            ScrmTaxRiskProfileDto.TrendItem item = new ScrmTaxRiskProfileDto.TrendItem();
            item.setMonth(monthLabel);
            item.setRevenue(revenue == null ? "-" : revenue.stripTrailingZeros().toPlainString());
            if (vat == null || vat.signum() == 0 || revenue == null || revenue.signum() == 0) {
                item.setTaxRate("-");
                item.setFlag("NA");
            } else {
                BigDecimal ratio = vat.multiply(BigDecimal.valueOf(100))
                        .divide(revenue, 2, RoundingMode.HALF_UP);
                item.setTaxRate(ratio.stripTrailingZeros().toPlainString() + "%");
                item.setFlag(ratio.compareTo(LOW_RATIO_THRESHOLD) < 0
                        ? "LOW"
                        : ratio.compareTo(HIGH_RATIO_THRESHOLD) > 0 ? "HIGH" : "NORMAL");
            }
            trend.add(item);
        }
        return trend;
    }

    /** 应用风险规则（逾期率 / 连续低高税负） */
    private void buildRisks(ScrmTaxRiskProfileDto profile) {
        if (profile.getRecordTotal() > 0) {
            BigDecimal overdueRate = BigDecimal.valueOf(profile.getOverdueCount())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(profile.getRecordTotal()), 2, RoundingMode.HALF_UP);
            if (overdueRate.compareTo(OVERDUE_RATE_THRESHOLD) > 0) {
                profile.getRisks().add(ScrmTaxRiskProfileDto.RiskItem.builder()
                        .level("CRITICAL")
                        .message(String.format("申报逾期率 %.0f%%（%d/%d 条逾期）",
                                overdueRate, profile.getOverdueCount(), profile.getRecordTotal()))
                        .suggestion("尽快补报逾期记录，并排查报税流程是否存在卡点（征期规则/提醒未触达）")
                        .build());
            }
        }
        if (profile.getUnderpaidCount() > 0) {
            profile.getRisks().add(ScrmTaxRiskProfileDto.RiskItem.builder()
                    .level("WARN")
                    .message("存在 " + profile.getUnderpaidCount() + " 条已到期欠缴记录（实缴 < 应缴）")
                    .suggestion("核对税局实际入库状态，及时补缴并回填实缴金额/凭证")
                    .build());
        }
        // 连续 3 月低/高税负
        String prevFlag = null;
        int run = 1;
        for (ScrmTaxRiskProfileDto.TrendItem t : profile.getTrend()) {
            if ("LOW".equals(t.getFlag()) || "HIGH".equals(t.getFlag())) {
                if (t.getFlag().equals(prevFlag)) {
                    run++;
                } else {
                    prevFlag = t.getFlag();
                    run = 1;
                }
                if (run >= CONSECUTIVE_MONTHS) {
                    boolean low = "LOW".equals(t.getFlag());
                    profile.getRisks().add(ScrmTaxRiskProfileDto.RiskItem.builder()
                            .level("WARN")
                            .message((low ? "增值税税负率连续 " : "增值税税负率连续 ")
                                    + run + " 个月"
                                    + (low ? "低于 1% 预警阈值" : "高于 4.5% 预警阈值")
                                    + "（最新 " + t.getMonth() + "：" + t.getTaxRate() + "）")
                            .suggestion(low
                                    ? "排查进项抵扣是否充分、收入确认是否完整（偏低可能触发税局关注）"
                                    : "核查收入申报口径与进项抵扣是否合规（偏高可能因进项票据未及时认证）")
                            .build());
                    run = 1; // 只报一次
                }
            } else {
                prevFlag = null;
                run = 1;
            }
        }
    }
}