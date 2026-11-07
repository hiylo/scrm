/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationServiceTest.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.tax;

import org.hiylo.scrm.config.ScrmTaxCalendarProperties;
import org.hiylo.scrm.dto.ScrmTaxDeclarationProfileDto;
import org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity;
import org.hiylo.scrm.entity.ScrmTaxHolidayEntity;
import org.hiylo.scrm.entity.ScrmTaxWorkdayOverrideEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.service.CompanyResolver;
import org.hiylo.scrm.repository.ScrmCalendarEventRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationProfileRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationRecordRepository;
import org.hiylo.scrm.service.ScrmNotificationCenterService;
import org.hiylo.scrm.service.ScrmTicketService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmTaxDeclarationService} 单元测试（报税日历状态机）。
 * <p>
 * 锁定关键业务语义:
 * <ul>
 *   <li>档案创建默认值（小规模增值税默认季报 / 汇算默认 5/31）+ 唯一键冲突拒绝</li>
 *   <li>状态机推进: PENDING 截止日未申报 → OVERDUE; 申报完成/缴款完成/补报/归档/取消守卫</li>
 *   <li>逾期升级: 逾期 ≥5 天自动建 HIGH 工单 + 置 escalated 标记</li>
 *   <li>日历事件落库幂等（同一记录同一事件类型不重复建）</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmTaxDeclarationService 报税日历状态机")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScrmTaxDeclarationServiceTest {

    /** 申报档案仓库 Mock */
    @Mock
    private ScrmTaxDeclarationProfileRepository profileRepository;
    /** 申报记录仓库 Mock */
    @Mock
    private ScrmTaxDeclarationRecordRepository recordRepository;
    /** 日历事件仓库 Mock */
    @Mock
    private ScrmCalendarEventRepository calendarEventRepository;
    /** 通知中心门面 Mock */
    @Mock
    private ScrmNotificationCenterService notificationCenterService;
    /** 通知记录仓库 Mock（幂等去重） */
    @Mock
    private ScrmNotificationRepository notificationRepository;
    /** 客户仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;

    @Mock
    private ScrmCompanyRepository companyRepository;
    /** 工单服务 Mock（升级督办） */
    @Mock
    private ScrmTicketService ticketService;
    /** 财税数据范围（测试默认不限制：resolveAccessibleCompanyIds 返回 null） */
    @Mock
    private org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;
    /** 税务假期仓储（法定节假日顺延） */
    @Mock
    private org.hiylo.scrm.repository.ScrmTaxHolidayRepository taxHolidayRepository;

    /** 调休上班日仓储（周末补班视为工作日） */
    @Mock
    private org.hiylo.scrm.repository.ScrmTaxWorkdayOverrideRepository workdayOverrideRepository;


    /** 被测服务 */
    private ScrmTaxDeclarationService service;

    /** 测试配置 */
    private ScrmTaxCalendarProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ScrmTaxCalendarProperties();
        service = new ScrmTaxDeclarationService(profileRepository, recordRepository,
                calendarEventRepository, notificationCenterService, notificationRepository,
                customerRepository, new CompanyResolver(customerRepository, companyRepository),
                companyRepository, ticketService, properties, financeDataScope, taxHolidayRepository, workdayOverrideRepository);
    }

    @Test
    @DisplayName("createProfile: 小规模增值税默认季报 + 季终后15日, 客户名补全")
    void createProfile_vatDefaults() {
        ScrmTaxDeclarationProfileDto dto = new ScrmTaxDeclarationProfileDto();
        dto.setCustomerId(100L);
        dto.setTaxType("VAT");
        dto.setStartDate(LocalDate.of(2026, 10, 1));
        when(profileRepository.findByCustomerIdAndTaxTypeAndTaxpayerTypeAndStartDate(
                anyLong(), anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(profileRepository.save(any(ScrmTaxDeclarationProfileEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        org.hiylo.scrm.entity.ScrmCustomerEntity customer = new org.hiylo.scrm.entity.ScrmCustomerEntity();
        customer.setNickname("杭州某贸易有限公司");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));

        ScrmTaxDeclarationProfileEntity result = service.createProfile(dto);

        assertThat(result.getDeclarationCycle()).isEqualTo("QUARTERLY");
        assertThat(result.getDeadlineRule()).isEqualTo("DAY15");
        assertThat(result.getTaxpayerType()).isEqualTo("SMALL");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getCustomerName()).isEqualTo("杭州某贸易有限公司");
    }

    @Test
    @DisplayName("createProfile: 同客户同税种同纳税人同开始日期 → CONFLICT")
    void createProfile_duplicateRejected() {
        ScrmTaxDeclarationProfileDto dto = new ScrmTaxDeclarationProfileDto();
        dto.setCustomerId(100L);
        dto.setTaxType("VAT");
        dto.setStartDate(LocalDate.of(2026, 10, 1));
        when(profileRepository.findByCustomerIdAndTaxTypeAndTaxpayerTypeAndStartDate(
                anyLong(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(new ScrmTaxDeclarationProfileEntity()));

        assertThatThrownBy(() -> service.createProfile(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("markFiled: PENDING → FILED, 回填申报时间/操作人/税额")
    void markFiled_pendingToFiled() {
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(1L);
        record.setDeclarationStatus("PENDING");
        when(recordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(recordRepository.save(any(ScrmTaxDeclarationRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmTaxDeclarationRecordEntity result = service.markFiled(1L, new BigDecimal("100.00"), "RCPT-001", null);

        assertThat(result.getDeclarationStatus()).isEqualTo("FILED");
        assertThat(result.getFiledAt()).isNotNull();
        assertThat(result.getTaxAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getTaxReceiptNo()).isEqualTo("RCPT-001");
    }

    @Test
    @DisplayName("markFiled: PAID 终态不允许再申报 → CONFLICT")
    void markFiled_paidRejected() {
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(1L);
        record.setDeclarationStatus("PAID");
        when(recordRepository.findById(1L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.markFiled(1L, null, null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 NOT_STARTED / PENDING / OVERDUE");
        verify(recordRepository, never()).save(any());
    }

    @Test
    @DisplayName("markFiled: NOT_STARTED 可提前申报（不必等调度推进为 PENDING）")
    void markFiled_notStartedAllowed() throws Exception {
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(1L);
        record.setProfileId(9L);
        record.setCustomerId(100L);
        record.setTaxType("VAT");
        record.setDueDate(LocalDate.of(2026, 11, 16));
        record.setPeriodStart(LocalDate.of(2026, 10, 1));
        record.setPeriodEnd(LocalDate.of(2026, 10, 31));
        record.setDeclarationStatus("NOT_STARTED");
        record.setOverdueDays(0);
        when(recordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(recordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScrmTaxDeclarationRecordEntity result = service.markFiled(1L, null, null, null);

        assertThat(result.getDeclarationStatus()).isEqualTo("FILED");
        assertThat(result.getFiledAt()).isNotNull();
        verify(recordRepository).save(any());
    }

    @Test
    @DisplayName("markPaid: FILED → PAID, 逾期清零 + 日历已缴事件落库")
    void markPaid_filedToPaid() {
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(1L);
        record.setProfileId(9L);
        record.setCustomerId(100L);
        record.setTaxType("VAT");
        record.setDueDate(LocalDate.of(2026, 10, 15));
        record.setPeriodStart(LocalDate.of(2026, 9, 1));
        record.setPeriodEnd(LocalDate.of(2026, 9, 30));
        record.setDeclarationStatus("FILED");
        record.setOverdueDays(2);
        when(recordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(recordRepository.save(any(ScrmTaxDeclarationRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(calendarEventRepository.findAll()).thenReturn(java.util.List.of());

        ScrmTaxDeclarationRecordEntity result = service.markPaid(1L, new BigDecimal("50.00"), null);

        assertThat(result.getDeclarationStatus()).isEqualTo("PAID");
        assertThat(result.getPaidAt()).isNotNull();
        assertThat(result.getOverdueDays()).isZero();
        verify(calendarEventRepository).save(any(org.hiylo.scrm.entity.ScrmCalendarEventEntity.class));
    }

    @Test
    @DisplayName("markBackFilledPaid: OVERDUE → PAID（补报快捷）")
    void markBackFilledPaid_overdueToPaid() {
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(1L);
        record.setCustomerId(100L);
        record.setTaxType("VAT");
        record.setDueDate(LocalDate.of(2026, 10, 15));
        record.setPeriodStart(LocalDate.of(2026, 9, 1));
        record.setPeriodEnd(LocalDate.of(2026, 9, 30));
        record.setDeclarationStatus("OVERDUE");
        record.setOverdueDays(6);
        when(recordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(recordRepository.save(any(ScrmTaxDeclarationRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(calendarEventRepository.findAll()).thenReturn(java.util.List.of());

        ScrmTaxDeclarationRecordEntity result = service.markBackFilledPaid(
                1L, new BigDecimal("100"), new BigDecimal("100"), "RCPT-999");

        assertThat(result.getDeclarationStatus()).isEqualTo("PAID");
        assertThat(result.getFiledAt()).isNotNull();
        assertThat(result.getPaidAt()).isNotNull();
        assertThat(result.getOverdueDays()).isZero();
    }

    @Test
    @DisplayName("markBackFilledPaid: 完全不传金额 → BAD_REQUEST（不落 PAID 空金额）")
    void markBackFilledPaid_rejectsMissingAmount() {
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(1L);
        record.setDeclarationStatus("OVERDUE");
        when(recordRepository.findById(1L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.markBackFilledPaid(1L, null, null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("实缴税额不能为空");
        verify(recordRepository, never()).save(any());
    }

    @Test
    @DisplayName("markBackFilledPaid: 显式传 0（零申报）→ 允许进 PAID")
    void markBackFilledPaid_zeroAmountAllowed() throws Exception {
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(1L);
        record.setProfileId(9L);
        record.setCustomerId(100L);
        record.setTaxType("VAT");
        record.setDueDate(LocalDate.of(2026, 10, 15));
        record.setPeriodStart(LocalDate.of(2026, 9, 1));
        record.setPeriodEnd(LocalDate.of(2026, 9, 30));
        record.setDeclarationStatus("OVERDUE");
        record.setOverdueDays(6);
        when(recordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(recordRepository.save(any(ScrmTaxDeclarationRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(calendarEventRepository.findAll()).thenReturn(java.util.List.of());

        ScrmTaxDeclarationRecordEntity result = service.markBackFilledPaid(
                1L, BigDecimal.ZERO, BigDecimal.ZERO, "RCPT-ZERO");

        assertThat(result.getDeclarationStatus()).isEqualTo("PAID");
        assertThat(result.getTaxAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("markPaid: 不传实缴税额 → BAD_REQUEST（空金额不许进 PAID）")
    void markPaid_rejectsMissingAmount() {
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(1L);
        record.setDeclarationStatus("FILED");
        when(recordRepository.findById(1L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.markPaid(1L, null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("实缴税额不能为空");
        verify(recordRepository, never()).save(any());
    }

    @Test
    @DisplayName("markPaid: 负数实缴税额 → BAD_REQUEST")
    void markPaid_rejectsNegativeAmount() {
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(1L);
        record.setDeclarationStatus("FILED");
        when(recordRepository.findById(1L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.markPaid(1L, new BigDecimal("-1.00"), null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不能为负数");
        verify(recordRepository, never()).save(any());
    }

    @Test
    @DisplayName("advanceStates: 截止日已过且 PENDING → OVERDUE; OVERDUE 持续 ≥5 天 → 升级建工单")
    void advanceStates_overdueAndEscalation() {
        // 档案: 小规模 VAT 季报, 起 2026-01-01
        ScrmTaxDeclarationProfileEntity profile = new ScrmTaxDeclarationProfileEntity();
        profile.setId(1L);
        profile.setCustomerId(100L);
        profile.setCustomerName("测试客户");
        profile.setTaxType("VAT");
        profile.setDeclarationCycle("QUARTERLY");
        profile.setDeadlineRule("DAY15");
        profile.setStartDate(LocalDate.of(2026, 1, 1));
        profile.setRegion("浙江");

        // 记录: 2026 Q3（2026-07-01~2026-09-30, 截止 2026-10-15 → 已过）
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(1L);
        record.setProfileId(1L);
        record.setCustomerId(100L);
        record.setTaxType("VAT");
        record.setPeriodStart(LocalDate.of(2026, 7, 1));
        record.setPeriodEnd(LocalDate.of(2026, 9, 30));
        record.setDueDate(LocalDate.of(2026, 10, 15));
        record.setDeclarationStatus("PENDING");
        record.setOverdueDays(0);
        when(recordRepository.findAll()).thenReturn(java.util.List.of(record));
        when(recordRepository.save(any(ScrmTaxDeclarationRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int[] stats = service.advanceStates();

        // 2026-10-23 距 2026-10-15 已 8 天 ≥5 → OVERDUE 且升级
        assertThat(record.getDeclarationStatus()).isEqualTo("OVERDUE");
        assertThat(record.getEscalatedAt()).isNotNull();
        assertThat(stats[1]).isEqualTo(1);
        assertThat(stats[2]).isEqualTo(1);
        verify(ticketService).createTicket(any(org.hiylo.scrm.dto.ScrmTicketDto.class));
    }

    @Test
    @DisplayName("syncCalendarEvent: 同记录同事件类型幂等, 不重复建")
    void syncCalendarEvent_idempotent() {
        ScrmTaxDeclarationRecordEntity record = new ScrmTaxDeclarationRecordEntity();
        record.setId(1L);
        record.setCustomerId(100L);
        record.setTaxType("VAT");
        record.setDueDate(LocalDate.of(2026, 10, 15));
        record.setPeriodStart(LocalDate.of(2026, 9, 1));
        record.setPeriodEnd(LocalDate.of(2026, 9, 30));
        org.hiylo.scrm.entity.ScrmCalendarEventEntity existing = new org.hiylo.scrm.entity.ScrmCalendarEventEntity();
        existing.setEventType("TAX_DEADLINE");
        existing.setEventTitle("报税截止 增值税 2026-09-01~2026-09-30");
        existing.setStartDate(LocalDate.of(2026, 10, 15));
        when(calendarEventRepository.findAll()).thenReturn(java.util.List.of(existing));

        service.syncCalendarEvent(record, "TAX_DEADLINE");

        verify(calendarEventRepository, never()).save(any(org.hiylo.scrm.entity.ScrmCalendarEventEntity.class));
    }

    /**
     * 构造一条假期记录。
     *
     * @param name  假期名称
     * @param start 起始日（yyyy-MM-dd）
     * @param end   结束日（yyyy-MM-dd）
     * @return 假期实体
     */
    private ScrmTaxHolidayEntity holiday(String name, String start, String end) {
        ScrmTaxHolidayEntity h = new ScrmTaxHolidayEntity();
        h.setHolidayName(name);
        h.setStartDate(LocalDate.parse(start));
        h.setEndDate(LocalDate.parse(end));
        h.setIsActive(Boolean.TRUE);
        return h;
    }

    /**
     * 构造一条调休上班日。
     *
     * @param date 上班日期（yyyy-MM-dd）
     * @return 调休实体
     */
    private ScrmTaxWorkdayOverrideEntity workday(String date) {
        ScrmTaxWorkdayOverrideEntity w = new ScrmTaxWorkdayOverrideEntity();
        w.setWorkDate(LocalDate.parse(date));
        return w;
    }

    @Test
    @DisplayName("截止日顺延：落在国庆假期内顺延到假期后首个工作日")
    void nextWorkday_skipsNationalDay() throws Exception {
        // 2026 国庆 10-01~10-08，10-09 是周五
        when(taxHolidayRepository.findActiveBetween(any(), any()))
                .thenReturn(java.util.List.of(holiday("国庆节", "2026-10-01", "2026-10-08")));
        when(workdayOverrideRepository.findActiveBetween(any(), any())).thenReturn(java.util.List.of());

        assertThat(invokeNextWorkday(LocalDate.of(2026, 10, 3)))
                .isEqualTo(LocalDate.of(2026, 10, 9));
        // 假期最后一天 10-08，次日即工作日
        assertThat(invokeNextWorkday(LocalDate.of(2026, 10, 8)))
                .isEqualTo(LocalDate.of(2026, 10, 9));
    }

    @Test
    @DisplayName("截止日顺延：调休补班的周六视为工作日，不能被推后")
    void nextWorkday_honorsAdjustedWorkday() throws Exception {
        // 2026-10-10 是周六，但被列为补班日
        when(taxHolidayRepository.findActiveBetween(any(), any())).thenReturn(java.util.List.of());
        when(workdayOverrideRepository.findActiveBetween(any(), any()))
                .thenReturn(java.util.List.of(workday("2026-10-10")));

        // 补班日当天即工作日，不应推到 10-12
        assertThat(invokeNextWorkday(LocalDate.of(2026, 10, 10)))
                .isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    @DisplayName("截止日顺延：普通周末仍然顺延到周一")
    void nextWorkday_skipsPlainWeekend() throws Exception {
        when(taxHolidayRepository.findActiveBetween(any(), any())).thenReturn(java.util.List.of());
        when(workdayOverrideRepository.findActiveBetween(any(), any())).thenReturn(java.util.List.of());

        // 2026-10-17 是周六 → 10-19 周一
        assertThat(invokeNextWorkday(LocalDate.of(2026, 10, 17)))
                .isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    @DisplayName("截止日顺延：假期表不可用时退化为只跳周末，不抛异常")
    void nextWorkday_fallsBackWhenHolidayTableBroken() throws Exception {
        when(taxHolidayRepository.findActiveBetween(any(), any()))
                .thenThrow(new RuntimeException("db down"));
        when(workdayOverrideRepository.findActiveBetween(any(), any()))
                .thenThrow(new RuntimeException("db down"));

        assertThat(invokeNextWorkday(LocalDate.of(2026, 10, 17)))
                .isEqualTo(LocalDate.of(2026, 10, 19));
    }

    /**
     * 反射调用私有 nextWorkday（顺延规则是内部实现，直测最贴近真实行为）。
     *
     * @param date 入参日期
     * @return 顺延后的日期
     * @throws Exception 反射调用失败
     */
    private LocalDate invokeNextWorkday(LocalDate date) throws Exception {
        java.lang.reflect.Method m = ScrmTaxDeclarationService.class
                .getDeclaredMethod("nextWorkday", LocalDate.class);
        m.setAccessible(true);
        return (LocalDate) m.invoke(service, date);
    }
}
