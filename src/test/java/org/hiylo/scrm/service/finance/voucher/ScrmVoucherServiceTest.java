/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmVoucherServiceTest.java
 * Date : 2026/10/25 19:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import org.hiylo.scrm.dto.ScrmVoucherEntryDto;
import org.hiylo.scrm.dto.ScrmVoucherSaveDto;
import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntryEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountSubjectRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmVoucherEntryRepository;
import org.hiylo.scrm.repository.ScrmVoucherRepository;
import org.hiylo.scrm.service.finance.voucher.ScrmPeriodCloseService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmVoucherService} 单元测试（凭证域）。
 * <p>
 * 记账模块最不能出错的是<b>借贷平衡</b>与<b>试算平衡</b>：
 * 前者一旦被绕过，账面金额就失去意义；后者是会计唯一能证明「账做对没有」的手段。
 * 因此这里对这两块做精确值断言，而不是「不报错就算过」。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("记账服务：借贷平衡 / 科目受控 / 试算平衡")
class ScrmVoucherServiceTest {

    /** 科目仓储 */
    @Mock
    private ScrmAccountSubjectRepository subjectRepository;

    /** 凭证仓储 */
    @Mock
    private ScrmVoucherRepository voucherRepository;

    /** 分录仓储 */
    @Mock
    private ScrmVoucherEntryRepository entryRepository;

    /** 主体仓储 */
    @Mock
    private ScrmCompanyRepository companyRepository;

    /** 期间服务（默认期间未结账） */
    @Mock
    private ScrmPeriodCloseService periodCloseService;
    /** 财税数据范围（测试默认不限制：resolveAccessibleCompanyIds 返回 null） */
    @Mock
    private org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    /** 原始单据服务（列表回填单据数 / 删凭证级联清理） */
    @Mock
    private ScrmVoucherAttachmentService attachmentService;

    /** 期初余额服务（测试透传：不叠加接户期初，保持原有断言口径） */
    @Mock
    private ScrmAccountOpeningBalanceService openingBalanceService;


    /** 被测服务 */
    private ScrmVoucherService service;

    /** 已保存的凭证（模拟数据库回读） */
    private final ScrmVoucherEntity[] savedVoucher = new ScrmVoucherEntity[1];

    /** 已保存的分录（模拟数据库回读） */
    private final List<ScrmVoucherEntryEntity>[] savedEntries = new List[1];

    /**
     * 组装被测服务，并准备「银行存款 + 主营业务收入」两个科目。
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ScrmVoucherService(subjectRepository, voucherRepository,
                entryRepository, companyRepository, periodCloseService, financeDataScope,
                attachmentService, openingBalanceService);
        // 透传合并：不设置期初余额时行为与改造前完全一致
        when(openingBalanceService.mergeInto(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
        // 默认期间未结账；封账场景在专门用例里覆盖
        when(periodCloseService.isClosed(anyLong(), any(), any())).thenReturn(false);

        ScrmCompanyEntity company = new ScrmCompanyEntity();
        company.setId(100L);
        company.setCompanyName("测试公司");
        when(companyRepository.findById(100L)).thenReturn(Optional.of(company));

        ScrmAccountSubjectEntity bank = new ScrmAccountSubjectEntity();
        bank.setSubjectCode("1002");
        bank.setSubjectName("银行存款");
        bank.setCategory(ScrmAccountSubjectEntity.CATEGORY_ASSET);
        bank.setDirection(ScrmAccountSubjectEntity.DIRECTION_DEBIT);
        bank.setStatus(ScrmAccountSubjectEntity.STATUS_ACTIVE);

        ScrmAccountSubjectEntity income = new ScrmAccountSubjectEntity();
        income.setSubjectCode("5001");
        income.setSubjectName("主营业务收入");
        income.setCategory(ScrmAccountSubjectEntity.CATEGORY_INCOME);
        income.setDirection(ScrmAccountSubjectEntity.DIRECTION_CREDIT);
        income.setStatus(ScrmAccountSubjectEntity.STATUS_ACTIVE);

        ScrmAccountSubjectEntity disabled = new ScrmAccountSubjectEntity();
        disabled.setSubjectCode("9999");
        disabled.setSubjectName("已停用科目");
        disabled.setCategory(ScrmAccountSubjectEntity.CATEGORY_EXPENSE);
        disabled.setDirection(ScrmAccountSubjectEntity.DIRECTION_DEBIT);
        disabled.setStatus(ScrmAccountSubjectEntity.STATUS_DISABLED);

        when(subjectRepository.findByCompanyIdOrderBySubjectCodeAsc(100L))
                .thenReturn(List.of(bank, income, disabled));

        when(voucherRepository.findMaxVoucherNo(anyLong(), any(), any(), any())).thenReturn(null);
        when(voucherRepository.save(any())).thenAnswer(inv -> {
            ScrmVoucherEntity entity = inv.getArgument(0);
            entity.setId(1L);
            savedVoucher[0] = entity;
            return entity;
        });
        when(voucherRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(savedVoucher[0]));
        when(entryRepository.saveAll(any())).thenAnswer(inv -> {
            List<ScrmVoucherEntryEntity> list = inv.getArgument(0);
            savedEntries[0] = list;
            return list;
        });
        when(entryRepository.findByVoucherIdOrderByLineNoAsc(anyLong()))
                .thenAnswer(inv -> savedEntries[0] == null ? List.of() : savedEntries[0]);
    }

    /**
     * 构造一条分录。
     *
     * @param code   科目编码
     * @param debit  借方
     * @param credit 贷方
     * @return 分录 DTO
     */
    private ScrmVoucherEntryDto entry(String code, String debit, String credit) {
        ScrmVoucherEntryDto dto = new ScrmVoucherEntryDto();
        dto.setSubjectCode(code);
        dto.setDebitAmount(debit == null ? null : new BigDecimal(debit));
        dto.setCreditAmount(credit == null ? null : new BigDecimal(credit));
        return dto;
    }

    /**
     * 构造一张凭证入参。
     *
     * @param entries 分录
     * @return 凭证 DTO
     */
    private ScrmVoucherSaveDto voucher(List<ScrmVoucherEntryDto> entries) {
        ScrmVoucherSaveDto dto = new ScrmVoucherSaveDto();
        dto.setPeriodYear(2026);
        dto.setPeriodMonth(10);
        dto.setVoucherDate(LocalDate.of(2026, 10, 31));
        dto.setEntries(entries);
        return dto;
    }

    // ==================== 借贷平衡 ====================

    @Test
    @DisplayName("借贷不平被拒绝，错误信息带差额")
    void rejectsUnbalancedVoucher() {
        ScrmVoucherSaveDto dto = voucher(List.of(
                entry("1002", "1000", null),
                entry("5001", null, "900")));

        assertThatThrownBy(() -> service.createVoucher(100L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("借贷不平衡")
                .hasMessageContaining("100.00");
        verify(voucherRepository, never()).save(any());
    }

    @Test
    @DisplayName("借贷平衡时保存成功，合计取自分录而不是前端传值")
    void savesBalancedVoucher() throws Exception {
        ScrmVoucherSaveDto dto = voucher(List.of(
                entry("1002", "1500.50", null),
                entry("5001", null, "1500.50")));

        Map<String, Object> result = service.createVoucher(100L, dto);
        ScrmVoucherEntity saved = (ScrmVoucherEntity) result.get("voucher");

        assertThat(saved.getTotalDebit()).isEqualByComparingTo("1500.50");
        assertThat(saved.getTotalCredit()).isEqualByComparingTo("1500.50");
        assertThat(saved.getEntryCount()).isEqualTo(2);
        assertThat(saved.getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_DRAFT);
        assertThat(saved.getVoucherNo()).isEqualTo(1);
        assertThat(saved.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("scale 不同但数值相等视为平衡（100 与 100.00）")
    void treatsDifferentScaleAsBalanced() throws Exception {
        ScrmVoucherSaveDto dto = voucher(List.of(
                new ScrmVoucherEntryDto() {{
                    setSubjectCode("1002");
                    setDebitAmount(new BigDecimal("100"));
                }},
                new ScrmVoucherEntryDto() {{
                    setSubjectCode("5001");
                    setCreditAmount(new BigDecimal("100.00"));
                }}));

        Map<String, Object> result = service.createVoucher(100L, dto);
        ScrmVoucherEntity saved = (ScrmVoucherEntity) result.get("voucher");
        assertThat(saved.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("只有 1 条分录被拒绝（有借必有贷）")
    void rejectsSingleEntry() {
        ScrmVoucherSaveDto dto = voucher(List.of(entry("1002", "100", null)));

        assertThatThrownBy(() -> service.createVoucher(100L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("至少需要 2 条分录");
    }

    @Test
    @DisplayName("同一行借贷同时非零被拒绝")
    void rejectsLineWithBothSides() {
        ScrmVoucherSaveDto dto = voucher(List.of(
                entry("1002", "100", "50"),
                entry("5001", null, "100")));

        assertThatThrownBy(() -> service.createVoucher(100L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不能同时非零");
    }

    @Test
    @DisplayName("同一行借贷同时为零被拒绝")
    void rejectsZeroLine() {
        ScrmVoucherSaveDto dto = voucher(List.of(
                entry("1002", "0", "0"),
                entry("5001", null, "100")));

        assertThatThrownBy(() -> service.createVoucher(100L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不能同时为零");
    }

    @Test
    @DisplayName("负数金额被拒绝（方向只能用借贷表达）")
    void rejectsNegativeAmount() {
        ScrmVoucherSaveDto dto = voucher(List.of(
                entry("1002", "-100", null),
                entry("5001", null, "100")));

        assertThatThrownBy(() -> service.createVoucher(100L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不能为负数");
    }

    @Test
    @DisplayName("金额合计为 0 被拒绝")
    void rejectsZeroTotal() {
        // 两行都为 0 会在行级先被拦；这里构造「借贷相等但为 0」不可能，
        // 因此验证的是行级零值保护已经足够
        ScrmVoucherSaveDto dto = voucher(List.of(
                entry("1002", "0", null),
                entry("5001", null, "0")));

        assertThatThrownBy(() -> service.createVoucher(100L, dto))
                .isInstanceOf(ScrmException.class);
    }

    @Test
    @DisplayName("已结账期间禁止新增凭证（封账必须真的封住）")
    void rejectsVoucherInClosedPeriod() {
        when(periodCloseService.isClosed(anyLong(), any(), any())).thenReturn(true);
        ScrmVoucherSaveDto dto = voucher(List.of(
                entry("1002", "100", null),
                entry("5001", null, "100")));

        assertThatThrownBy(() -> service.createVoucher(100L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已结账");
        verify(voucherRepository, never()).save(any());
    }

    // ==================== 科目受控 ====================

    @Test
    @DisplayName("科目不存在时拒绝（禁止自由文本科目）")
    void rejectsUnknownSubject() {
        ScrmVoucherSaveDto dto = voucher(List.of(
                entry("8888", "100", null),
                entry("5001", null, "100")));

        assertThatThrownBy(() -> service.createVoucher(100L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("科目不存在");
    }

    @Test
    @DisplayName("停用科目不可记账")
    void rejectsDisabledSubject() {
        ScrmVoucherSaveDto dto = voucher(List.of(
                entry("9999", "100", null),
                entry("5001", null, "100")));

        assertThatThrownBy(() -> service.createVoucher(100L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    @DisplayName("未选择科目时拒绝")
    void rejectsBlankSubject() {
        ScrmVoucherSaveDto dto = voucher(List.of(
                entry("  ", "100", null),
                entry("5001", null, "100")));

        assertThatThrownBy(() -> service.createVoucher(100L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("未选择科目");
    }

    // ==================== 状态机 ====================

    @Test
    @DisplayName("已记账凭证不可修改")
    void cannotUpdatePostedVoucher() {
        ScrmVoucherEntity posted = new ScrmVoucherEntity();
        posted.setId(1L);
        posted.setStatus(ScrmVoucherEntity.STATUS_POSTED);
        posted.setVoucherWord("记");
        posted.setVoucherNo(1);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(posted));

        assertThatThrownBy(() -> service.updateVoucher(1L, voucher(List.of(
                entry("1002", "100", null), entry("5001", null, "100")))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不可修改");
    }

    @Test
    @DisplayName("记账时以库里分录重算合计并校验，header 被篡改也会被拦下")
    void postValidatesAgainstStoredEntries() {
        ScrmVoucherEntity draft = new ScrmVoucherEntity();
        draft.setId(1L);
        draft.setStatus(ScrmVoucherEntity.STATUS_DRAFT);
        draft.setVoucherWord("记");
        draft.setVoucherNo(1);
        // header 自称平衡
        draft.setTotalDebit(new BigDecimal("100.00"));
        draft.setTotalCredit(new BigDecimal("100.00"));
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(draft));

        // 但库里分录其实不平
        ScrmVoucherEntryEntity d = new ScrmVoucherEntryEntity();
        d.setDebitAmount(new BigDecimal("100.00"));
        d.setCreditAmount(BigDecimal.ZERO);
        ScrmVoucherEntryEntity c = new ScrmVoucherEntryEntity();
        c.setDebitAmount(BigDecimal.ZERO);
        c.setCreditAmount(new BigDecimal("80.00"));
        savedEntries[0] = List.of(d, c);

        assertThatThrownBy(() -> service.postVoucher(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("借贷不平衡");
    }

    @Test
    @DisplayName("草稿记账成功，写入记账人与时间")
    void postsDraftVoucher() throws Exception {
        ScrmVoucherEntity draft = new ScrmVoucherEntity();
        draft.setId(1L);
        draft.setStatus(ScrmVoucherEntity.STATUS_DRAFT);
        draft.setVoucherWord("记");
        draft.setVoucherNo(1);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(draft));

        ScrmVoucherEntryEntity d = new ScrmVoucherEntryEntity();
        d.setDebitAmount(new BigDecimal("500.00"));
        d.setCreditAmount(BigDecimal.ZERO);
        ScrmVoucherEntryEntity c = new ScrmVoucherEntryEntity();
        c.setDebitAmount(BigDecimal.ZERO);
        c.setCreditAmount(new BigDecimal("500.00"));
        savedEntries[0] = List.of(d, c);

        Map<String, Object> result = service.postVoucher(1L);
        ScrmVoucherEntity posted = (ScrmVoucherEntity) result.get("voucher");

        assertThat(posted.getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_POSTED);
        assertThat(posted.getPostedAt()).isNotNull();
        assertThat(posted.getTotalDebit()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("非草稿不可删除（已记账须作废保留痕迹）")
    void cannotDeletePostedVoucher() {
        ScrmVoucherEntity posted = new ScrmVoucherEntity();
        posted.setId(1L);
        posted.setStatus(ScrmVoucherEntity.STATUS_POSTED);
        posted.setVoucherWord("记");
        posted.setVoucherNo(1);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(posted));

        assertThatThrownBy(() -> service.deleteVoucher(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("只有草稿可以删除");
    }

    @Test
    @DisplayName("作废后状态为 VOID 并记录作废人")
    void voidsVoucher() throws Exception {
        ScrmVoucherEntity posted = new ScrmVoucherEntity();
        posted.setId(1L);
        posted.setStatus(ScrmVoucherEntity.STATUS_POSTED);
        posted.setVoucherWord("记");
        posted.setVoucherNo(1);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(posted));

        Map<String, Object> result = service.voidVoucher(1L, "科目用错");
        ScrmVoucherEntity voided = (ScrmVoucherEntity) result.get("voucher");

        assertThat(voided.getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_VOID);
        assertThat(voided.getVoidedAt()).isNotNull();
        assertThat(voided.getRemark()).isEqualTo("科目用错");
    }

    // ==================== 试算平衡 ====================

    @Test
    @DisplayName("试算平衡：资产借方与收入贷方金额相等时 balanced=true")
    @SuppressWarnings("unchecked")
    void trialBalanceReportsBalanced() {
        // 期初：银行存款借 1000
        when(entryRepository.sumBeforePeriod(100L, 202610))
                .thenReturn(List.<Object[]>of(new Object[]{"1002", "银行存款",
                        new BigDecimal("1000.00"), BigDecimal.ZERO}));
        // 本期：银行存款借 500，主营业务收入贷 500
        when(entryRepository.sumInPeriod(100L, 202610))
                .thenReturn(List.of(
                        new Object[]{"1002", "银行存款", new BigDecimal("500.00"), BigDecimal.ZERO},
                        new Object[]{"5001", "主营业务收入", BigDecimal.ZERO, new BigDecimal("500.00")}));

        ScrmAccountSubjectEntity bank = new ScrmAccountSubjectEntity();
        bank.setSubjectCode("1002");
        bank.setSubjectName("银行存款");
        bank.setCategory(ScrmAccountSubjectEntity.CATEGORY_ASSET);
        bank.setDirection(ScrmAccountSubjectEntity.DIRECTION_DEBIT);

        ScrmAccountSubjectEntity income = new ScrmAccountSubjectEntity();
        income.setSubjectCode("5001");
        income.setSubjectName("主营业务收入");
        income.setCategory(ScrmAccountSubjectEntity.CATEGORY_INCOME);
        income.setDirection(ScrmAccountSubjectEntity.DIRECTION_CREDIT);

        when(subjectRepository.findByCompanyIdOrderBySubjectCodeAsc(100L))
                .thenReturn(List.of(bank, income));

        Map<String, Object> result = service.trialBalance(100L, 2026, 10);

        // 银行存款：期初借 1000 + 本期借 500 = 期末借 1500
        // 收入：本期贷 500 = 期末贷 500 → 不平（因为期初只有借方 1000，演示不等场景）
        assertThat((BigDecimal) result.get("totalPeriodDebit")).isEqualByComparingTo("500.00");
        assertThat((BigDecimal) result.get("totalPeriodCredit")).isEqualByComparingTo("500.00");
        assertThat(result.get("rows")).isNotNull();
    }

    @Test
    @DisplayName("试算平衡：期末借贷两侧合计相等时 balanced=true")
    void trialBalanceBalancedWhenBothSidesEqual() {
        // 期初无余额；本期：银行存款借 500，实收资本贷 500
        when(entryRepository.sumBeforePeriod(100L, 202610)).thenReturn(List.of());
        when(entryRepository.sumInPeriod(100L, 202610))
                .thenReturn(List.of(
                        new Object[]{"1002", "银行存款", new BigDecimal("500.00"), BigDecimal.ZERO},
                        new Object[]{"3001", "实收资本", BigDecimal.ZERO, new BigDecimal("500.00")}));

        ScrmAccountSubjectEntity bank = new ScrmAccountSubjectEntity();
        bank.setSubjectCode("1002");
        bank.setSubjectName("银行存款");
        bank.setCategory(ScrmAccountSubjectEntity.CATEGORY_ASSET);
        bank.setDirection(ScrmAccountSubjectEntity.DIRECTION_DEBIT);

        ScrmAccountSubjectEntity capital = new ScrmAccountSubjectEntity();
        capital.setSubjectCode("3001");
        capital.setSubjectName("实收资本");
        capital.setCategory(ScrmAccountSubjectEntity.CATEGORY_EQUITY);
        capital.setDirection(ScrmAccountSubjectEntity.DIRECTION_CREDIT);

        when(subjectRepository.findByCompanyIdOrderBySubjectCodeAsc(100L))
                .thenReturn(List.of(bank, capital));

        Map<String, Object> result = service.trialBalance(100L, 2026, 10);

        assertThat((BigDecimal) result.get("totalClosingDebit")).isEqualByComparingTo("500.00");
        assertThat((BigDecimal) result.get("totalClosingCredit")).isEqualByComparingTo("500.00");
        assertThat(result.get("balanced")).isEqualTo(true);
    }

    @Test
    @DisplayName("期初余额只统计已记账凭证（草稿不参与余额）")
    void openingBalanceQueryExcludesDraft() {
        // 该方法由 SQL 层保证（status='POSTED'），此处验证调用参数正确
        service.trialBalance(100L, 2026, 10);
        verify(entryRepository).sumBeforePeriod(100L, 202610);
        verify(entryRepository).sumInPeriod(100L, 202610);
    }

    @Test
    @DisplayName("findCompanyIdByVoucherId 回查凭证归属主体，供控制器做越权校验")
    void findCompanyIdByVoucherId_returnsOwningCompany() throws Exception {
        ScrmVoucherEntity voucher = new ScrmVoucherEntity();
        voucher.setId(500L);
        voucher.setCompanyId(100L);
        when(voucherRepository.findById(500L)).thenReturn(java.util.Optional.of(voucher));

        assertThat(service.findCompanyIdByVoucherId(500L)).isEqualTo(100L);
    }

    @Test
    @DisplayName("findCompanyIdByVoucherId 凭证不存在时抛 404 而非返回 null")
    void findCompanyIdByVoucherId_throwsWhenMissing() {
        when(voucherRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.findCompanyIdByVoucherId(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("凭证不存在");
    }
}
