/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationSheetService.java
 * Date : 2026/11/08 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.tax;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmTaxDeclarationSheetDto;
import org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmTaxDeclarationProfileRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 申报表生成 + CSV 导出服务（报税日历域, 竞品差距 P0④）。
 * <p>
 * 纯内部闭环产物：只消费本系统已有的「申报档案 + 申报记录」，不调用电子税局、
 * 不依赖发票/账务明细，因此不会因为外部接口不可用而失效。
 * </p>
 * <p>
 * <b>行项目口径（刻意保守）</b>：申报记录只落「申报税额 / 实缴税额」两个金额，
 * 税基类行项目（销售收入 / 收入额 / 营业收入…）在本系统没有发票数据支撑。
 * 这里的做法是——能对上的行（应纳税额 / 已缴税额）如实填，对不上的行一律填 0
 * 并在<b>行备注</b>里写明「未收录、需人工补录」。宁可留空让会计补，也不臆造数值：
 * 申报表是要报给税局的，任何凭空的数字都比没有数字更危险。
 * </p>
 * <p>
 * <b>CSV 口径</b>：{@link #toCsv} 返回的字符串以 UTF-8 BOM（{@code \uFEFF}）开头，
 * 保证 Excel 直接双击打开不乱码；字段按 RFC4180 转义（含逗号/引号/换行时用双引号
 * 包裹，内部引号翻倍），行结束符为 CRLF。中文原样输出，编码由 BOM 声明。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmTaxDeclarationSheetService {

    /** UTF-8 BOM: Excel 靠它识别 UTF-8, 缺失则中文乱码 */
    private static final String UTF8_BOM = "\uFEFF";

    /** CSV 行结束符（RFC4180 CRLF, Excel 兼容性最好） */
    private static final String CRLF = "\r\n";

    /** CSV 表头（4 列统一结构: 项目 / 本期数 / 累计数 / 备注） */
    private static final String CSV_HEADER = "项目,本期数,累计数,备注";

    /** 金额精度（元, 两位小数, 与申报表纸质表样一致） */
    private static final int AMOUNT_SCALE = 2;

    /** 档案状态: 启用（优先取启用档案生成申报表） */
    private static final String PROFILE_ACTIVE = "ACTIVE";

    /** 申报状态: 无申报记录（本服务内部状态, 不落库） */
    public static final String STATUS_NOT_DECLARED = "NOT_DECLARED";

    /** 税种中文名映射用常量（与 ScrmTaxDeclarationService 保持同一口径） */
    private static final String TAX_VAT = ScrmTaxDeclarationService.TAX_VAT;
    private static final String TAX_IIT = ScrmTaxDeclarationService.TAX_IIT;
    private static final String TAX_CIT = ScrmTaxDeclarationService.TAX_CIT;
    private static final String TAX_SOCIAL_SECURITY = ScrmTaxDeclarationService.TAX_SOCIAL_SECURITY;

    /** 申报日期展示格式 */
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 申报档案数据访问层 */
    private final ScrmTaxDeclarationProfileRepository profileRepository;

    /** 申报记录数据访问层 */
    private final ScrmTaxDeclarationRecordRepository recordRepository;

    // ============================================================
    // 申报表生成
    // ============================================================

    /**
     * 生成指定主体 + 税种 + 所属期的申报表。
     * <p>
     * 聚合路径：公司主体 → 申报档案（同税种, 优先 ACTIVE）→ 申报记录（所属期覆盖目标月）→
     * 按税种表样生成行项目。找不到档案或记录时不报错：返回一张行项目全 0、状态
     * {@code NOT_DECLARED} 的空表并标注原因——「这个月还没申报」是正常业务状态，
     * 不是异常；前端据此提示会计补录，而不是弹错误框。
     * </p>
     *
     * @param companyId   公司主体 ID
     * @param taxType     税种（VAT / IIT / CIT / SOCIAL_SECURITY）
     * @param periodYear  所属期年份
     * @param periodMonth 所属期月份（1-12）
     * @return 申报表（含行项目与合计行）
     * @throws ScrmException 参数非法
     */
    @Transactional(readOnly = true)
    public ScrmTaxDeclarationSheetDto buildSheet(Long companyId, String taxType,
                                                 Integer periodYear, Integer periodMonth)
            throws ScrmException {
        if (companyId == null) {
            throw ScrmException.badRequest("公司主体 ID 不能为空");
        }
        if (taxType == null || taxType.isBlank()) {
            throw ScrmException.badRequest("税种不能为空");
        }
        if (periodYear == null || periodMonth == null
                || periodMonth < 1 || periodMonth > 12
                || periodYear < 2000 || periodYear > 2100) {
            throw ScrmException.badRequest("所属期年份/月份非法: year=" + periodYear
                    + ", month=" + periodMonth);
        }
        String normalizedTaxType = taxType.trim().toUpperCase();
        YearMonth period = YearMonth.of(periodYear, periodMonth);
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();

        ScrmTaxDeclarationProfileEntity profile = resolveProfile(companyId, normalizedTaxType);
        ScrmTaxDeclarationRecordEntity record = resolveRecord(profile, periodStart, periodEnd);
        LocalDate dataEnd = record != null ? record.getPeriodEnd() : periodEnd;
        // 当期无记录时整张表按「未申报」出：累计数也必须归 0。
        // 否则会出现「行备注说未申报、累计列却显示 21000」的自相矛盾表——
        // 会计一眼看不出哪个数可信，反而比空表更容易误报。
        List<ScrmTaxDeclarationRecordEntity> yearRecords = record != null
                ? resolveYearRecords(profile, periodYear, dataEnd)
                : List.of();

        ScrmTaxDeclarationSheetDto sheet = ScrmTaxDeclarationSheetDto.builder()
                .companyId(companyId)
                .taxType(normalizedTaxType)
                .taxTypeLabel(taxTypeLabel(normalizedTaxType))
                .periodYear(periodYear)
                .periodMonth(periodMonth)
                .periodLabel(periodYear + "年" + periodMonth + "月")
                .sheetName(sheetName(normalizedTaxType, profile))
                .taxpayerName(resolveTaxpayerName(profile))
                .taxpayerTaxNo("")
                .declarationStatus(record != null ? record.getDeclarationStatus() : STATUS_NOT_DECLARED)
                .declarationStatusLabel(statusLabel(record != null ? record.getDeclarationStatus() : null))
                .dueDate(record != null && record.getDueDate() != null
                        ? String.valueOf(record.getDueDate()) : "")
                .filedAt(record != null && record.getFiledAt() != null
                        ? record.getFiledAt().format(DATE_TIME_FORMAT) : "")
                .taxReceiptNo(record != null && record.getTaxReceiptNo() != null
                        ? record.getTaxReceiptNo() : "")
                .generatedAt(LocalDateTime.now())
                .build();

        List<ScrmTaxDeclarationSheetDto.SheetRow> rows =
                buildRows(normalizedTaxType, profile, record, yearRecords, periodEnd);
        sheet.setRows(rows);
        sheet.setTotal(buildTotalRow(rows));
        sheet.setRemark(buildSheetRemark(record, profile));
        log.debug("[申报表] 生成: companyId={}, taxType={}, period={}-{}, rows={}, status={}",
                companyId, normalizedTaxType, periodYear, periodMonth, rows.size(),
                sheet.getDeclarationStatus());
        return sheet;
    }

    /**
     * 定位该主体该税种的申报档案（优先 ACTIVE, 否则取最近一条）。
     *
     * @param companyId 公司主体 ID
     * @param taxType   税种（已归一化大写）
     * @return 档案；不存在返回 null
     */
    private ScrmTaxDeclarationProfileEntity resolveProfile(Long companyId, String taxType) {
        List<ScrmTaxDeclarationProfileEntity> profiles =
                profileRepository.findByCompanyIdOrderByCreateTimeDesc(companyId);
        if (profiles == null || profiles.isEmpty()) {
            return null;
        }
        List<ScrmTaxDeclarationProfileEntity> matched = profiles.stream()
                .filter(p -> p != null && p.getTaxType() != null
                        && taxType.equalsIgnoreCase(p.getTaxType()))
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            return null;
        }
        // 启用的档案才有当期申报记录；暂停/取消的档案只用于「查历史」兜底
        return matched.stream()
                .filter(p -> PROFILE_ACTIVE.equals(p.getStatus()))
                .findFirst()
                .orElse(matched.get(0));
    }

    /**
     * 定位覆盖目标月份的申报记录（月报取其本身, 季报取覆盖该月的季度记录）。
     *
     * @param profile     档案（可空）
     * @param periodStart 目标月首日
     * @param periodEnd   目标月末日
     * @return 申报记录；无档案或无记录返回 null
     */
    private ScrmTaxDeclarationRecordEntity resolveRecord(ScrmTaxDeclarationProfileEntity profile,
                                                        LocalDate periodStart, LocalDate periodEnd) {
        if (profile == null || profile.getId() == null) {
            return null;
        }
        List<ScrmTaxDeclarationRecordEntity> records =
                recordRepository.findByProfileIdOrderByPeriodStartAsc(profile.getId());
        if (records == null) {
            return null;
        }
        return records.stream()
                .filter(r -> r != null && r.getPeriodStart() != null && r.getPeriodEnd() != null)
                .filter(r -> !r.getPeriodStart().isAfter(periodEnd) && !r.getPeriodEnd().isBefore(periodStart))
                // 季度记录只需命中一个所属期, 择「所属期开始最近」的一条最稳
                .max((a, b) -> a.getPeriodStart().compareTo(b.getPeriodStart()))
                .orElse(null);
    }

    /**
     * 取本年度截至目标所属期末的记录（累计数口径）。
     *
     * @param profile     档案（可空）
     * @param periodYear  所属期年份
     * @param periodEnd   累计截止日（含）
     * @return 记录列表；无档案返回空列表
     */
    private List<ScrmTaxDeclarationRecordEntity> resolveYearRecords(
            ScrmTaxDeclarationProfileEntity profile, Integer periodYear, LocalDate periodEnd) {
        if (profile == null || profile.getId() == null) {
            return List.of();
        }
        List<ScrmTaxDeclarationRecordEntity> records =
                recordRepository.findByProfileIdOrderByPeriodStartAsc(profile.getId());
        if (records == null) {
            return List.of();
        }
        return records.stream()
                .filter(r -> r != null && r.getPeriodEnd() != null && !r.getPeriodEnd().isAfter(periodEnd))
                .filter(r -> r.getPeriodStart() != null
                        && r.getPeriodStart().getYear() == periodYear)
                // 已取消（减免/政策豁免）的记录不进累计, 否则累计会虚高
                .filter(r -> !ScrmTaxDeclarationService.STATUS_CANCELED
                        .equals(r.getDeclarationStatus()))
                .collect(Collectors.toList());
    }

    /**
     * 按税种表样生成行项目。
     *
     * @param taxType     税种（已归一化）
     * @param profile     档案（可空, 决定纳税人类型表样）
     * @param record      当期申报记录（可空）
     * @param yearRecords 本年度累计口径记录
     * @param periodEnd   所属期末日
     * @return 行项目列表
     */
    private List<ScrmTaxDeclarationSheetDto.SheetRow> buildRows(
            String taxType, ScrmTaxDeclarationProfileEntity profile,
            ScrmTaxDeclarationRecordEntity record,
            List<ScrmTaxDeclarationRecordEntity> yearRecords, LocalDate periodEnd) {
        List<ScrmTaxDeclarationSheetDto.SheetRow> rows = new ArrayList<>();
        boolean declared = record != null;
        String missing = "未找到该所属期申报记录（未申报），数值待会计补录";

        BigDecimal taxAmount = record != null ? nvl(record.getTaxAmount()) : BigDecimal.ZERO;
        BigDecimal paidAmount = record != null ? nvl(record.getPaidAmount()) : BigDecimal.ZERO;
        BigDecimal taxCumulative = sum(yearRecords, true);
        BigDecimal paidCumulative = sum(yearRecords, false);

        if (TAX_VAT.equals(taxType)) {
            rows.add(taxBaseRow("应征增值税不含税销售额（销售收入）", declared));
            rows.add(taxBaseRow("销项税额", declared));
            rows.add(taxBaseRow("进项税额", declared));
            rows.add(amountRow("应纳税额", taxAmount, taxCumulative,
                    declared ? "取自申报记录「申报税额」" : missing));
            rows.add(amountRow("本期已缴税额", paidAmount, paidCumulative,
                    declared ? "取自申报记录「实缴税额」" : missing));
        } else if (TAX_IIT.equals(taxType)) {
            rows.add(taxBaseRow("收入额", declared));
            rows.add(taxBaseRow("费用扣除", declared));
            rows.add(taxBaseRow("应纳税所得额", declared));
            rows.add(amountRow("应纳税额", taxAmount, taxCumulative,
                    declared ? "取自申报记录「申报税额」" : missing));
            rows.add(amountRow("本期已缴税额", paidAmount, paidCumulative,
                    declared ? "取自申报记录「实缴税额」" : missing));
        } else if (TAX_CIT.equals(taxType)) {
            rows.add(taxBaseRow("营业收入", declared));
            rows.add(taxBaseRow("营业成本", declared));
            rows.add(taxBaseRow("实际利润额", declared));
            rows.add(taxBaseRow("应纳税所得额", declared));
            rows.add(amountRow("应纳所得税额", taxAmount, taxCumulative,
                    declared ? "取自申报记录「申报税额」" : missing));
            rows.add(amountRow("本期已缴税额", paidAmount, paidCumulative,
                    declared ? "取自申报记录「实缴税额」" : missing));
        } else if (TAX_SOCIAL_SECURITY.equals(taxType)) {
            rows.add(taxBaseRow("缴费基数", declared));
            rows.add(taxBaseRow("单位缴费额", declared));
            rows.add(taxBaseRow("个人缴费额", declared));
            rows.add(amountRow("应缴合计", taxAmount, taxCumulative,
                    declared ? "取自申报记录「申报税额」" : missing));
            rows.add(amountRow("本期已缴合计", paidAmount, paidCumulative,
                    declared ? "取自申报记录「实缴税额」" : missing));
        } else {
            // 未纳入固定表样的税种（如印花税/房产税）: 只给最小可填结构, 不臆造行名
            rows.add(taxBaseRow("计税依据", declared));
            rows.add(amountRow("应纳税额", taxAmount, taxCumulative,
                    declared ? "取自申报记录「申报税额」" : missing));
            rows.add(amountRow("本期已缴税额", paidAmount, paidCumulative,
                    declared ? "取自申报记录「实缴税额」" : missing));
        }
        // 附加税费提示（增值税/企业所得税附征城建税等, 本系统不单独维护税率）
        if (TAX_VAT.equals(taxType) && profile != null) {
            rows.get(0).setRemark(rows.get(0).getRemark()
                    + "；附征城建税/教育费附加未纳入本表");
        }
        return rows;
    }

    /**
     * 构造税基类行项目（本系统无发票/账务明细, 数值一律 0 并标注）。
     *
     * @param label   行名
     * @param declared 当期是否有申报记录
     * @return 行项目
     */
    private ScrmTaxDeclarationSheetDto.SheetRow taxBaseRow(String label, boolean declared) {
        return ScrmTaxDeclarationSheetDto.SheetRow.builder()
                .label(label)
                .current(BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP))
                .cumulative(BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP))
                .remark(declared
                        ? "本系统未收录发票/账务明细，需按账套数据人工补录"
                        : "未找到该所属期申报记录（未申报），数值待会计补录")
                .build();
    }

    /**
     * 构造带数值的行项目（金额统一两位小数）。
     *
     * @param label      行名
     * @param current    本期数
     * @param cumulative 累计数
     * @param remark     备注
     * @return 行项目
     */
    private ScrmTaxDeclarationSheetDto.SheetRow amountRow(String label, BigDecimal current,
                                                          BigDecimal cumulative, String remark) {
        return ScrmTaxDeclarationSheetDto.SheetRow.builder()
                .label(label)
                .current(nvl(current))
                .cumulative(nvl(cumulative))
                .remark(remark)
                .build();
    }

    /**
     * 合计行（各税目应纳税额合计: 本期=各行本期数之和, 累计=各行累计数之和）。
     * <p>
     * 合计取「应纳税额」类行之和而非全部行之和——税基行与已缴行不能与应纳税额相加，
     * 否则合计会翻倍虚高。当前表样里应纳税额行了然可辨, 这里按行名后缀匹配。
     * </p>
     *
     * @param rows 行项目
     * @return 合计行
     */
    private ScrmTaxDeclarationSheetDto.SheetRow buildTotalRow(
            List<ScrmTaxDeclarationSheetDto.SheetRow> rows) {
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal cumulative = BigDecimal.ZERO;
        for (ScrmTaxDeclarationSheetDto.SheetRow row : rows) {
            String label = row.getLabel() == null ? "" : row.getLabel();
            boolean isTaxLine = label.endsWith("应纳税额") || label.endsWith("应纳所得税额")
                    || label.endsWith("应缴合计");
            if (!isTaxLine) {
                continue;
            }
            current = current.add(nvl(row.getCurrent()));
            cumulative = cumulative.add(nvl(row.getCumulative()));
        }
        return ScrmTaxDeclarationSheetDto.SheetRow.builder()
                .label("合计")
                .current(nvl(current))
                .cumulative(nvl(cumulative))
                .remark("各税目应纳税额合计（不含税基与已缴行）")
                .build();
    }

    /**
     * 表级备注（数据来源与缺失项提示）。
     *
     * @param record  当期申报记录（可空）
     * @param profile 申报档案（可空）
     * @return 备注文案
     */
    private String buildSheetRemark(ScrmTaxDeclarationRecordEntity record,
                                    ScrmTaxDeclarationProfileEntity profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("数据来源：本系统申报档案 + 申报记录（内部闭环，未对接电子税局）");
        if (profile == null) {
            sb.append("；该主体未建立该税种申报档案");
        }
        if (record == null) {
            sb.append("；该所属期无申报记录，行项目数值为 0 待补录");
        }
        if (profile != null && (profile.getTaxpayerType() == null
                || profile.getDeclarationOrg() == null)) {
            sb.append("；纳税人识别号未登记，需人工补填后再报送");
        }
        return sb.toString();
    }

    // ============================================================
    // CSV 导出
    // ============================================================

    /**
     * 申报表转 CSV 字符串。
     * <p>
     * 输出结构（4 列统一，Excel 直接打开不乱码）：
     * <pre>
     * \uFEFF项目,本期数,累计数,备注
     * 申报表名称,增值税及附加税费申报表（小规模纳税人适用）,,
     * 税种,增值税,,
     * 所属期,2026年10月,,
     * 纳税人名称,某某公司,,
     * 纳税人识别号,,,
     * 申报状态,已申报,,
     * 申报截止日期,2026-11-16,,
     * 申报日期,2026-11-10 10:20:30,,
     * 完税凭证号,,,
     *
     * 应征增值税不含税销售额（销售收入）,0.00,0.00,需人工补录
     * 应纳税额,12000.00,96000.00,取自申报记录「申报税额」
     * 合计,12000.00,96000.00,各税目应纳税额合计
     * </pre>
     * </p>
     *
     * @param sheet 申报表（可空, 空表输出仅含表头与 BOM）
     * @return CSV 文本（以 {@code \uFEFF} 开头）
     */
    public String toCsv(ScrmTaxDeclarationSheetDto sheet) {
        StringBuilder sb = new StringBuilder(UTF8_BOM.length() + 512);
        sb.append(UTF8_BOM);
        appendCsvLine(sb, CSV_HEADER.split(",", -1));
        if (sheet == null) {
            return sb.toString();
        }
        // 表头信息区：键值对写入 4 列结构（值放第 2 列, 空列留白保持列数一致）
        appendCsvLine(sb, new String[]{"申报表名称", nullToEmpty(sheet.getSheetName()), "", ""});
        appendCsvLine(sb, new String[]{"税种", nullToEmpty(sheet.getTaxTypeLabel()), "", ""});
        appendCsvLine(sb, new String[]{"所属期", nullToEmpty(sheet.getPeriodLabel()), "", ""});
        appendCsvLine(sb, new String[]{"纳税人名称", nullToEmpty(sheet.getTaxpayerName()), "", ""});
        appendCsvLine(sb, new String[]{"纳税人识别号", nullToEmpty(sheet.getTaxpayerTaxNo()), "", ""});
        appendCsvLine(sb, new String[]{"申报状态", nullToEmpty(sheet.getDeclarationStatusLabel()), "", ""});
        appendCsvLine(sb, new String[]{"申报截止日期", nullToEmpty(sheet.getDueDate()), "", ""});
        appendCsvLine(sb, new String[]{"申报日期", nullToEmpty(sheet.getFiledAt()), "", ""});
        appendCsvLine(sb, new String[]{"完税凭证号", nullToEmpty(sheet.getTaxReceiptNo()), "", ""});
        if (sheet.getRemark() != null && !sheet.getRemark().isBlank()) {
            appendCsvLine(sb, new String[]{"备注", sheet.getRemark(), "", ""});
        }
        // 空行分隔表头信息与行项目明细（Excel 里可读性更好, RFC4180 允许）
        sb.append(CRLF);
        List<ScrmTaxDeclarationSheetDto.SheetRow> rows = sheet.getRows();
        if (rows != null) {
            for (ScrmTaxDeclarationSheetDto.SheetRow row : rows) {
                if (row == null) {
                    continue;
                }
                appendCsvLine(sb, new String[]{
                        nullToEmpty(row.getLabel()),
                        formatAmount(row.getCurrent()),
                        formatAmount(row.getCumulative()),
                        nullToEmpty(row.getRemark())});
            }
        }
        ScrmTaxDeclarationSheetDto.SheetRow total = sheet.getTotal();
        if (total != null) {
            appendCsvLine(sb, new String[]{
                    nullToEmpty(total.getLabel()),
                    formatAmount(total.getCurrent()),
                    formatAmount(total.getCumulative()),
                    nullToEmpty(total.getRemark())});
        }
        return sb.toString();
    }

    /**
     * 追加一行 CSV（字段逐个按 RFC4180 转义后以逗号连接 + CRLF）。
     *
     * @param sb     目标缓冲
     * @param values 字段值
     */
    private void appendCsvLine(StringBuilder sb, String[] values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escapeCsv(nullToEmpty(values[i])));
        }
        sb.append(CRLF);
    }

    /**
     * CSV 字段转义（RFC4180）。
     * <p>
     * 含逗号 / 双引号 / 换行 / 回车时用双引号包裹，内部双引号翻倍；其余原样输出。
     * 中文不做任何转义——乱码问题由文件开头的 BOM 解决，而不是靠转义。
     * </p>
     *
     * @param value 原始值（不可为 null）
     * @return 转义后的字段
     */
    private String escapeCsv(String value) {
        boolean needQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * 金额格式化（两位小数, 不带千分位——千分位逗号会引入不必要的 CSV 转义）。
     *
     * @param value 金额（可空）
     * @return 格式化字符串；空值返回空串
     */
    private String formatAmount(BigDecimal value) {
        return nvl(value).toPlainString();
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 汇总某金额字段（不含 CANCELED 记录）。
     *
     * @param records 记录列表
     * @param tax     true=申报税额, false=实缴税额
     * @return 合计（两位小数）
     */
    private BigDecimal sum(List<ScrmTaxDeclarationRecordEntity> records, boolean tax) {
        BigDecimal total = BigDecimal.ZERO;
        if (records != null) {
            for (ScrmTaxDeclarationRecordEntity record : records) {
                total = total.add(nvl(tax ? record.getTaxAmount() : record.getPaidAmount()));
            }
        }
        return total;
    }

    /**
     * null 安全 + 统一两位小数。
     *
     * @param value 金额
     * @return 非空且两位小数的金额
     */
    private BigDecimal nvl(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * null → 空串（CSV 里 null 必须落成空字段, 不能打印 "null"）。
     *
     * @param value 原始值
     * @return 非 null 字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 申报表名称（按税种 + 纳税人类型套用正式表样名）。
     *
     * @param taxType 税种（已归一化）
     * @param profile 申报档案（可空, 用于纳税人类型区分）
     * @return 表名
     */
    private String sheetName(String taxType, ScrmTaxDeclarationProfileEntity profile) {
        String taxpayerType = profile != null ? profile.getTaxpayerType() : null;
        if (TAX_VAT.equals(taxType)) {
            return "GENERAL".equalsIgnoreCase(taxpayerType)
                    ? "增值税及附加税费申报表（一般纳税人适用）"
                    : "增值税及附加税费申报表（小规模纳税人适用）";
        }
        if (TAX_IIT.equals(taxType)) {
            return "个人所得税扣缴申报表";
        }
        if (TAX_CIT.equals(taxType)) {
            return "企业所得税月（季）度预缴纳税申报表（A类）";
        }
        if (TAX_SOCIAL_SECURITY.equals(taxType)) {
            return "社会保险费申报表";
        }
        return taxTypeLabel(taxType) + "纳税申报表";
    }

    /**
     * 纳税人名称（申报主体优先, 回落客户/联系名称）。
     *
     * @param profile 申报档案（可空）
     * @return 名称；档案缺失返回空串
     */
    private String resolveTaxpayerName(ScrmTaxDeclarationProfileEntity profile) {
        if (profile == null) {
            return "";
        }
        if (profile.getDeclarationOrg() != null && !profile.getDeclarationOrg().isBlank()) {
            return profile.getDeclarationOrg();
        }
        return nullToEmpty(profile.getCustomerName());
    }

    /**
     * 税种中文名。
     *
     * @param taxType 税种
     * @return 中文名；未知税种原样返回
     */
    private String taxTypeLabel(String taxType) {
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
            return "社保费";
        }
        return taxType;
    }

    /**
     * 申报状态中文名。
     *
     * @param status 状态码（可空 = 无申报记录）
     * @return 中文名
     */
    private String statusLabel(String status) {
        if (status == null) {
            return "未申报（无申报记录）";
        }
        return switch (status) {
            case ScrmTaxDeclarationService.STATUS_NOT_STARTED -> "未开始";
            case ScrmTaxDeclarationService.STATUS_PENDING -> "待申报";
            case ScrmTaxDeclarationService.STATUS_FILED -> "已申报";
            case ScrmTaxDeclarationService.STATUS_PAID -> "已缴款";
            case ScrmTaxDeclarationService.STATUS_OVERDUE -> "逾期未申报";
            case ScrmTaxDeclarationService.STATUS_ARCHIVED -> "已归档";
            case ScrmTaxDeclarationService.STATUS_CANCELED -> "已取消";
            default -> status;
        };
    }
}
