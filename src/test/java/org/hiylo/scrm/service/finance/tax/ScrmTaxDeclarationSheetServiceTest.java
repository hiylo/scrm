/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationSheetServiceTest.java
 * Date : 2026/11/08 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.tax;

import org.hiylo.scrm.dto.ScrmTaxDeclarationSheetDto;
import org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmTaxDeclarationProfileRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmTaxDeclarationSheetService} 单元测试（申报表生成 + CSV 导出）。
 * <p>
 * 锁定关键语义:
 * <ul>
 *   <li>行项目聚合: 应纳税额/已缴税额取自申报记录, 累计数取本年度记录之和</li>
 *   <li>无申报记录: 行项目全 0 且状态 NOT_DECLARED、备注标注「未申报」</li>
 *   <li>CSV: 首字符必须是 UTF-8 BOM, 含表头与合计行, 含逗号/引号字段按 RFC4180 转义</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmTaxDeclarationSheetService 申报表生成与 CSV 导出")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScrmTaxDeclarationSheetServiceTest {

    /** 公司主体 ID */
    private static final long COMPANY_ID = 9001L;

    /** 申报档案 ID */
    private static final long PROFILE_ID = 7001L;

    /** 申报档案仓库 Mock */
    @Mock
    private ScrmTaxDeclarationProfileRepository profileRepository;

    /** 申报记录仓库 Mock */
    @Mock
    private ScrmTaxDeclarationRecordRepository recordRepository;

    /** 被测服务 */
    private ScrmTaxDeclarationSheetService service;

    @BeforeEach
    void setUp() {
        service = new ScrmTaxDeclarationSheetService(profileRepository, recordRepository);
    }

    /**
     * 构造申报档案（小规模增值税, 启用户季度申报）。
     *
     * @return 档案实体
     */
    private ScrmTaxDeclarationProfileEntity vatProfile() {
        ScrmTaxDeclarationProfileEntity profile = new ScrmTaxDeclarationProfileEntity();
        profile.setId(PROFILE_ID);
        profile.setCustomerId(3001L);
        profile.setCompanyId(COMPANY_ID);
        profile.setCustomerName("张三");
        profile.setTaxType("VAT");
        profile.setTaxpayerType("SMALL");
        profile.setDeclarationCycle("MONTHLY");
        profile.setDeadlineRule("DAY15");
        profile.setDeclarationOrg("某某科技有限公司");
        profile.setStartDate(LocalDate.of(2026, 1, 1));
        profile.setStatus("ACTIVE");
        return profile;
    }

    /**
     * 构造申报记录。
     *
     * @param periodMonth 所属期月份
     * @param taxAmount   申报税额
     * @param paidAmount  实缴税额
     * @param status      状态
     * @return 申报记录实体
     */
    private ScrmTaxDeclarationRecordEntity record(int periodMonth, String taxAmount,
                                                  String paidAmount, String status) {
        LocalDate start = LocalDate.of(2026, periodMonth, 1);
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(5000L + periodMonth);
        record.setProfileId(PROFILE_ID);
        record.setCustomerId(3001L);
        record.setTaxType("VAT");
        record.setPeriodStart(start);
        record.setPeriodEnd(start.withDayOfMonth(start.lengthOfMonth()));
        record.setDueDate(start.plusMonths(1).withDayOfMonth(15));
        record.setTaxAmount(taxAmount == null ? null : new BigDecimal(taxAmount));
        record.setPaidAmount(paidAmount == null ? null : new BigDecimal(paidAmount));
        record.setDeclarationStatus(status);
        record.setOverdueDays(0);
        record.setFiledAt(LocalDateTime.of(2026, periodMonth, 25, 10, 20, 30));
        record.setTaxReceiptNo("PZ2026" + periodMonth);
        return record;
    }

    /**
     * 按行名取行项目。
     *
     * @param sheet 申报表
     * @param label 行名
     * @return 行项目
     */
    private ScrmTaxDeclarationSheetDto.SheetRow row(ScrmTaxDeclarationSheetDto sheet, String label) {
        return sheet.getRows().stream()
                .filter(r -> label.equals(r.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到行项目: " + label));
    }

    @Test
    @DisplayName("buildSheet: 应纳税额/已缴税额取自申报记录, 累计数取本年度记录之和")
    void buildSheetAggregatesAmountsFromRecords() {
        // 10 月当期记录 + 本年度 8/9 月历史记录（累计口径）
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(COMPANY_ID))
                .thenReturn(List.of(vatProfile()));
        when(recordRepository.findByProfileIdOrderByPeriodStartAsc(PROFILE_ID))
                .thenReturn(List.of(
                        record(8, "8000.00", "8000.00", "ARCHIVED"),
                        record(9, "9000.00", "9000.00", "PAID"),
                        record(10, "12000.00", "12000.00", "FILED")));

        ScrmTaxDeclarationSheetDto sheet = service.buildSheet(COMPANY_ID, "VAT", 2026, 10);

        // 表头
        assertThat(sheet.getSheetName()).isEqualTo("增值税及附加税费申报表（小规模纳税人适用）");
        assertThat(sheet.getTaxType()).isEqualTo("VAT");
        assertThat(sheet.getTaxTypeLabel()).isEqualTo("增值税");
        assertThat(sheet.getPeriodYear()).isEqualTo(2026);
        assertThat(sheet.getPeriodMonth()).isEqualTo(10);
        assertThat(sheet.getPeriodLabel()).isEqualTo("2026年10月");
        assertThat(sheet.getTaxpayerName()).isEqualTo("某某科技有限公司");
        assertThat(sheet.getDeclarationStatus()).isEqualTo("FILED");
        assertThat(sheet.getTaxReceiptNo()).isEqualTo("PZ202610");

        // 行项目: 本期取 10 月记录, 累计取 8/9/10 三个月之和
        assertThat(row(sheet, "应纳税额").getCurrent()).isEqualByComparingTo("12000.00");
        assertThat(row(sheet, "应纳税额").getCumulative()).isEqualByComparingTo("29000.00");
        assertThat(row(sheet, "本期已缴税额").getCurrent()).isEqualByComparingTo("12000.00");
        assertThat(row(sheet, "本期已缴税额").getCumulative()).isEqualByComparingTo("29000.00");
        // 税基行本系统无数据支撑 → 0 且标注需人工补录（绝不臆造）
        assertThat(row(sheet, "应征增值税不含税销售额（销售收入）").getCurrent())
                .isEqualByComparingTo("0.00");
        assertThat(row(sheet, "应征增值税不含税销售额（销售收入）").getRemark())
                .contains("人工补录");

        // 增值税表样行序: 销售收入 / 销项税 / 进项税 / 应纳税额 / 已缴
        assertThat(sheet.getRows()).extracting(ScrmTaxDeclarationSheetDto.SheetRow::getLabel)
                .containsExactly("应征增值税不含税销售额（销售收入）", "销项税额", "进项税额",
                        "应纳税额", "本期已缴税额");

        // 合计行只累加应纳税额类行, 不能把税基/已缴行也加进来
        assertThat(sheet.getTotal().getLabel()).isEqualTo("合计");
        assertThat(sheet.getTotal().getCurrent()).isEqualByComparingTo("12000.00");
        assertThat(sheet.getTotal().getCumulative()).isEqualByComparingTo("29000.00");
    }

    @Test
    @DisplayName("buildSheet: 已取消记录不进累计口径")
    void buildSheetExcludesCanceledFromCumulative() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(COMPANY_ID))
                .thenReturn(List.of(vatProfile()));
        when(recordRepository.findByProfileIdOrderByPeriodStartAsc(PROFILE_ID))
                .thenReturn(List.of(
                        record(9, "5000.00", "5000.00", "CANCELED"),
                        record(10, "12000.00", "12000.00", "FILED")));

        ScrmTaxDeclarationSheetDto sheet = service.buildSheet(COMPANY_ID, "VAT", 2026, 10);

        assertThat(row(sheet, "应纳税额").getCumulative()).isEqualByComparingTo("12000.00");
    }

    @Test
    @DisplayName("buildSheet: 无申报记录时行项目全 0 且标注未申报")
    void buildSheetWithoutRecordFillsZeroAndNotes() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(COMPANY_ID))
                .thenReturn(List.of(vatProfile()));
        when(recordRepository.findByProfileIdOrderByPeriodStartAsc(PROFILE_ID))
                .thenReturn(List.of());

        ScrmTaxDeclarationSheetDto sheet = service.buildSheet(COMPANY_ID, "VAT", 2026, 10);

        assertThat(sheet.getDeclarationStatus())
                .isEqualTo(ScrmTaxDeclarationSheetService.STATUS_NOT_DECLARED);
        assertThat(sheet.getDeclarationStatusLabel()).contains("未申报");
        assertThat(sheet.getRows()).isNotEmpty();
        // 每一行都必须是 0, 且备注明确告知「未申报 / 待补录」
        assertThat(sheet.getRows()).allSatisfy(r -> {
            assertThat(r.getCurrent()).isEqualByComparingTo("0.00");
            assertThat(r.getCumulative()).isEqualByComparingTo("0.00");
            assertThat(r.getRemark()).contains("未申报");
        });
        assertThat(sheet.getTotal().getCurrent()).isEqualByComparingTo("0.00");
        assertThat(sheet.getTotal().getCumulative()).isEqualByComparingTo("0.00");
        assertThat(sheet.getRemark()).contains("无申报记录");
    }

    @Test
    @DisplayName("buildSheet: 该月无记录时累计列也必须归 0（不得与「未申报」备注自相矛盾）")
    void buildSheetWithoutCurrentRecordZeroesCumulative() {
        // 本年度 8/9 月有记录, 但查询的 11 月没有 —— 此时整张表按未申报出
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(COMPANY_ID))
                .thenReturn(List.of(vatProfile()));
        when(recordRepository.findByProfileIdOrderByPeriodStartAsc(PROFILE_ID))
                .thenReturn(List.of(
                        record(8, "8000.00", "8000.00", "PAID"),
                        record(9, "9000.00", "9000.00", "PAID")));

        ScrmTaxDeclarationSheetDto sheet = service.buildSheet(COMPANY_ID, "VAT", 2026, 11);

        assertThat(sheet.getDeclarationStatus())
                .isEqualTo(ScrmTaxDeclarationSheetService.STATUS_NOT_DECLARED);
        // 备注说「未申报」而行里却显示历史累计, 会让会计误判可报数, 一律归 0
        assertThat(sheet.getRows()).allSatisfy(r -> {
            assertThat(r.getCurrent()).isEqualByComparingTo("0.00");
            assertThat(r.getCumulative()).isEqualByComparingTo("0.00");
        });
        assertThat(sheet.getTotal().getCumulative()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("buildSheet: 无申报档案时也返回空表而不抛异常")
    void buildSheetWithoutProfileReturnsEmptySheet() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(COMPANY_ID))
                .thenReturn(List.of());

        ScrmTaxDeclarationSheetDto sheet = service.buildSheet(COMPANY_ID, "IIT", 2026, 10);

        assertThat(sheet.getSheetName()).isEqualTo("个人所得税扣缴申报表");
        assertThat(sheet.getDeclarationStatus())
                .isEqualTo(ScrmTaxDeclarationSheetService.STATUS_NOT_DECLARED);
        assertThat(sheet.getTaxpayerName()).isEmpty();
        assertThat(sheet.getRows()).extracting(ScrmTaxDeclarationSheetDto.SheetRow::getLabel)
                .containsExactly("收入额", "费用扣除", "应纳税所得额", "应纳税额", "本期已缴税额");
        assertThat(sheet.getRemark()).contains("未建立该税种申报档案");
    }

    @Test
    @DisplayName("buildSheet: 参数非法直接拒绝")
    void buildSheetRejectsIllegalArguments() {
        assertThatThrownBy(() -> service.buildSheet(null, "VAT", 2026, 10))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("公司主体 ID");
        assertThatThrownBy(() -> service.buildSheet(COMPANY_ID, "  ", 2026, 10))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("税种");
        assertThatThrownBy(() -> service.buildSheet(COMPANY_ID, "VAT", 2026, 13))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("所属期");
    }

    @Test
    @DisplayName("toCsv: 首字符为 UTF-8 BOM, 含表头与合计行")
    void toCsvStartsWithBomAndContainsHeaderAndTotal() {
        when(profileRepository.findByCompanyIdOrderByCreateTimeDesc(COMPANY_ID))
                .thenReturn(List.of(vatProfile()));
        when(recordRepository.findByProfileIdOrderByPeriodStartAsc(PROFILE_ID))
                .thenReturn(List.of(record(10, "12000.00", "12000.00", "FILED")));

        String csv = service.toCsv(service.buildSheet(COMPANY_ID, "VAT", 2026, 10));

        // BOM: Excel 靠它判定 UTF-8, 少一个字节中文就乱码
        assertThat(csv.charAt(0)).isEqualTo('\uFEFF');
        assertThat(csv).startsWith("\uFEFF");
        // 表头 + 表头信息区
        assertThat(csv).contains("项目,本期数,累计数,备注");
        assertThat(csv).contains("申报表名称,增值税及附加税费申报表（小规模纳税人适用）");
        assertThat(csv).contains("所属期,2026年10月");
        // 行项目 + 合计行
        assertThat(csv).contains("应纳税额,12000.00,12000.00,");
        assertThat(csv).contains("合计,12000.00,12000.00,");
        // 每行都以 CRLF 结束（RFC4180）
        assertThat(csv).contains("\r\n");
        assertThat(csv).endsWith("\r\n");
    }

    @Test
    @DisplayName("toCsv: 含逗号/引号/换行的字段按 RFC4180 转义")
    void toCsvEscapesCommaAndQuote() {
        ScrmTaxDeclarationSheetDto sheet = ScrmTaxDeclarationSheetDto.builder()
                .sheetName("增值税及附加税费申报表（小规模纳税人适用）")
                .taxTypeLabel("增值税")
                .periodLabel("2026年10月")
                // 名称里带逗号: 必须整体加引号, 否则会被 Excel 拆成两列
                .taxpayerName("某某科技有限公司,分公司")
                // 备注里带双引号: 内部引号需翻倍
                .taxpayerTaxNo("91310000\"MA1\"")
                .declarationStatusLabel("已申报")
                .remark("数据来源：申报记录\r\n含换行")
                .rows(List.of(ScrmTaxDeclarationSheetDto.SheetRow.builder()
                        .label("应纳税额")
                        .current(new BigDecimal("12000.00"))
                        .cumulative(new BigDecimal("29000.00"))
                        .remark("取自申报记录「申报税额」")
                        .build()))
                .total(ScrmTaxDeclarationSheetDto.SheetRow.builder()
                        .label("合计")
                        .current(new BigDecimal("12000.00"))
                        .cumulative(new BigDecimal("29000.00"))
                        .remark("各税目应纳税额合计")
                        .build())
                .build();

        String csv = service.toCsv(sheet);

        assertThat(csv).startsWith("\uFEFF");
        // 逗号字段被双引号包裹
        assertThat(csv).contains("\"某某科技有限公司,分公司\"");
        // 引号翻倍转义
        assertThat(csv).contains("\"91310000\"\"MA1\"\"\"");
        // 换行字段被双引号包裹, 且行内换行不会破坏列结构
        assertThat(csv).contains("\"数据来源：申报记录\r\n含换行\"");
        // 不含逗号/引号的普通字段保持原样, 不无谓加引号
        assertThat(csv).contains("应纳税额,12000.00,29000.00,取自申报记录「申报税额」");
        assertThat(csv).contains("合计,12000.00,29000.00,各税目应纳税额合计");
    }

    @Test
    @DisplayName("toCsv: null 申报表只输出 BOM + 表头, 不抛异常也不打印 null")
    void toCsvHandlesNullSheet() {
        String csv = service.toCsv(null);

        assertThat(csv).isEqualTo("\uFEFF项目,本期数,累计数,备注\r\n");
        assertThat(csv).doesNotContain("null");
    }
}
