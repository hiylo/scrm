/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmVoucherBatchTest.java
 * Date : 2026/11/10 15:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntryEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountSubjectRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmVoucherEntryRepository;
import org.hiylo.scrm.repository.ScrmVoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 凭证批量记账 / 批量作废单元测试。
 * <p>
 * 锁定的核心语义：<b>批量操作是「逐条独立」的</b>——单张凭证因已记账、已作废、
 * 期间已结账或借贷不平而失败时，必须只计入 failures，不能拖垮整批。
 * 月末会计常常一次勾几十张，任何一条脏数据导致整批回滚都会让人放弃用批量功能。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("记账服务：批量记账 / 批量作废")
class ScrmVoucherBatchTest {

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

    /** 期间服务 */
    @Mock
    private ScrmPeriodCloseService periodCloseService;

    /** 财税数据范围 */
    @Mock
    private org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    /** 被测服务 */
    private ScrmVoucherService service;

    /** 内存凭证表（模拟数据库） */
    private final Map<Long, ScrmVoucherEntity> vouchers = new HashMap<>();

    /** 内存分录表：voucherId → 分录 */
    private final Map<Long, List<ScrmVoucherEntryEntity>> entries = new HashMap<>();

    /** 公司 ID */
    private static final Long COMPANY_ID = 100L;

    /**
     * 组装被测服务与内存仓储。
     */
    @BeforeEach
    void setUp() {
        service = new ScrmVoucherService(subjectRepository, voucherRepository,
                entryRepository, companyRepository, periodCloseService, financeDataScope);

        // 默认期间未结账；结账场景在专门用例里覆盖
        when(periodCloseService.isClosed(anyLong(), any(), any())).thenReturn(false);

        ScrmCompanyEntity company = new ScrmCompanyEntity();
        company.setId(COMPANY_ID);
        company.setCompanyName("测试公司");
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        when(voucherRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(vouchers.get(inv.getArgument(0))));
        when(voucherRepository.save(any())).thenAnswer(inv -> {
            ScrmVoucherEntity entity = inv.getArgument(0);
            vouchers.put(entity.getId(), entity);
            return entity;
        });
        when(entryRepository.findByVoucherIdOrderByLineNoAsc(anyLong()))
                .thenAnswer(inv -> {
                    Long id = inv.getArgument(0);
                    return entries.getOrDefault(id, List.of());
                });
    }

    /**
     * 在内存里放一张凭证。
     *
     * @param id     凭证 ID
     * @param status 状态（DRAFT / POSTED / VOID）
     * @param debit  借方合计
     * @param credit 贷方合计
     * @return 凭证实体
     */
    private ScrmVoucherEntity putVoucher(Long id, String status, String debit, String credit) {
        ScrmVoucherEntity voucher = new ScrmVoucherEntity();
        voucher.setId(id);
        voucher.setCompanyId(COMPANY_ID);
        voucher.setVoucherWord("记");
        voucher.setVoucherNo(id.intValue());
        voucher.setStatus(status);
        voucher.setPeriodYear(2026);
        voucher.setPeriodMonth(11);
        vouchers.put(id, voucher);

        if (debit != null && credit != null) {
            ScrmVoucherEntryEntity d = new ScrmVoucherEntryEntity();
            d.setVoucherId(id);
            d.setLineNo(1);
            d.setSubjectCode("1002");
            d.setDebitAmount(new BigDecimal(debit));
            d.setCreditAmount(BigDecimal.ZERO);

            ScrmVoucherEntryEntity c = new ScrmVoucherEntryEntity();
            c.setVoucherId(id);
            c.setLineNo(2);
            c.setSubjectCode("5001");
            c.setDebitAmount(BigDecimal.ZERO);
            c.setCreditAmount(new BigDecimal(credit));

            entries.put(id, List.of(d, c));
        } else {
            entries.put(id, List.of());
        }
        return voucher;
    }

    // ==================== 批量记账 ====================

    @Test
    @DisplayName("批量记账：多张草稿全部成功，状态变 POSTED")
    void batchPostAllSucceed() {
        putVoucher(1L, ScrmVoucherEntity.STATUS_DRAFT, "100.00", "100.00");
        putVoucher(2L, ScrmVoucherEntity.STATUS_DRAFT, "200.00", "200.00");
        putVoucher(3L, ScrmVoucherEntity.STATUS_DRAFT, "300.00", "300.00");

        Map<String, Object> result = service.batchPost(List.of(1L, 2L, 3L));

        assertThat(result.get("successCount")).isEqualTo(3);
        assertThat(result.get("failCount")).isEqualTo(0);
        assertThat((List<?>) result.get("failures")).isEmpty();
        assertThat(vouchers.get(1L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_POSTED);
        assertThat(vouchers.get(2L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_POSTED);
        assertThat(vouchers.get(3L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_POSTED);
    }

    @Test
    @DisplayName("批量记账：已记账的凭证计入失败，不影响其余凭证入账")
    void batchPostSkipsAlreadyPosted() {
        putVoucher(1L, ScrmVoucherEntity.STATUS_DRAFT, "100.00", "100.00");
        putVoucher(2L, ScrmVoucherEntity.STATUS_POSTED, "200.00", "200.00");
        putVoucher(3L, ScrmVoucherEntity.STATUS_DRAFT, "300.00", "300.00");

        Map<String, Object> result = service.batchPost(List.of(1L, 2L, 3L));

        assertThat(result.get("successCount")).isEqualTo(2);
        assertThat(result.get("failCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).get("id")).isEqualTo(2L);
        assertThat((String) failures.get(0).get("message")).contains("已记账");
        // 其余两张照常入账
        assertThat(vouchers.get(1L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_POSTED);
        assertThat(vouchers.get(3L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_POSTED);
    }

    @Test
    @DisplayName("批量记账：已作废凭证记不进，失败明细带凭证号")
    void batchPostSkipsVoided() {
        putVoucher(1L, ScrmVoucherEntity.STATUS_VOID, "100.00", "100.00");

        Map<String, Object> result = service.batchPost(List.of(1L));

        assertThat(result.get("successCount")).isEqualTo(0);
        assertThat(result.get("failCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
        assertThat(failures.get(0).get("label")).isEqualTo("记-0001");
        assertThat((String) failures.get(0).get("message")).contains("已作废");
        assertThat(vouchers.get(1L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_VOID);
    }

    @Test
    @DisplayName("批量记账：期间已结账时整批被拒，凭证仍是草稿")
    void batchPostRejectsClosedPeriod() {
        putVoucher(1L, ScrmVoucherEntity.STATUS_DRAFT, "100.00", "100.00");
        when(periodCloseService.isClosed(anyLong(), any(), any())).thenReturn(true);

        Map<String, Object> result = service.batchPost(List.of(1L));

        assertThat(result.get("successCount")).isEqualTo(0);
        assertThat(result.get("failCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
        assertThat((String) failures.get(0).get("message")).contains("已结账");
        assertThat(vouchers.get(1L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_DRAFT);
    }

    @Test
    @DisplayName("批量记账：借贷不平的凭证被拦下，同批其他凭证照常成功")
    void batchPostSkipsUnbalanced() {
        putVoucher(1L, ScrmVoucherEntity.STATUS_DRAFT, "100.00", "100.00");
        putVoucher(2L, ScrmVoucherEntity.STATUS_DRAFT, "500.00", "400.00");

        Map<String, Object> result = service.batchPost(List.of(1L, 2L));

        assertThat(result.get("successCount")).isEqualTo(1);
        assertThat(result.get("failCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
        assertThat(failures.get(0).get("id")).isEqualTo(2L);
        assertThat((String) failures.get(0).get("message")).contains("借贷不平衡");
        assertThat(vouchers.get(2L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_DRAFT);
    }

    @Test
    @DisplayName("批量记账：不存在的凭证计入失败，不抛异常中断整批")
    void batchPostReportsMissingVoucher() {
        putVoucher(1L, ScrmVoucherEntity.STATUS_DRAFT, "100.00", "100.00");

        Map<String, Object> result = service.batchPost(List.of(1L, 999L));

        assertThat(result.get("successCount")).isEqualTo(1);
        assertThat(result.get("failCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
        assertThat(failures.get(0).get("id")).isEqualTo(999L);
        assertThat((String) failures.get(0).get("message")).contains("凭证不存在");
    }

    @Test
    @DisplayName("批量记账：空列表返回 0/0 且不报错")
    void batchPostEmptyList() {
        Map<String, Object> result = service.batchPost(new ArrayList<>());

        assertThat(result.get("successCount")).isEqualTo(0);
        assertThat(result.get("failCount")).isEqualTo(0);
        assertThat(result.get("failures")).isNotNull();
    }

    // ==================== 批量作废 ====================

    @Test
    @DisplayName("批量作废：多张凭证全部作废，原因写入备注")
    void batchVoidAllSucceed() {
        putVoucher(1L, ScrmVoucherEntity.STATUS_POSTED, "100.00", "100.00");
        putVoucher(2L, ScrmVoucherEntity.STATUS_DRAFT, "200.00", "200.00");

        Map<String, Object> result = service.batchVoid(List.of(1L, 2L), "录入错误");

        assertThat(result.get("successCount")).isEqualTo(2);
        assertThat(result.get("failCount")).isEqualTo(0);
        assertThat(vouchers.get(1L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_VOID);
        assertThat(vouchers.get(2L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_VOID);
        assertThat(vouchers.get(1L).getRemark()).isEqualTo("录入错误");
    }

    @Test
    @DisplayName("批量作废：已作废的凭证跳过并计入失败，其余照常作废")
    void batchVoidSkipsAlreadyVoided() {
        putVoucher(1L, ScrmVoucherEntity.STATUS_POSTED, "100.00", "100.00");
        putVoucher(2L, ScrmVoucherEntity.STATUS_VOID, "200.00", "200.00");

        Map<String, Object> result = service.batchVoid(List.of(1L, 2L), null);

        assertThat(result.get("successCount")).isEqualTo(1);
        assertThat(result.get("failCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
        assertThat(failures.get(0).get("id")).isEqualTo(2L);
        assertThat((String) failures.get(0).get("message")).contains("已作废");
        assertThat(vouchers.get(1L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_VOID);
    }

    @Test
    @DisplayName("批量作废：空白原因不覆盖已有备注")
    void batchVoidBlankReasonKeepsRemark() {
        ScrmVoucherEntity v = putVoucher(1L, ScrmVoucherEntity.STATUS_POSTED, "100.00", "100.00");
        v.setRemark("原有备注");

        service.batchVoid(List.of(1L), "   ");

        assertThat(vouchers.get(1L).getRemark()).isEqualTo("原有备注");
        assertThat(vouchers.get(1L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_VOID);
    }

    @Test
    @DisplayName("批量作废：作废后不再参与记账（先作废再记账必然失败）")
    void voidedCannotBePostedAgain() {
        putVoucher(1L, ScrmVoucherEntity.STATUS_DRAFT, "100.00", "100.00");
        service.batchVoid(List.of(1L), null);

        Map<String, Object> result = service.batchPost(List.of(1L));

        assertThat(result.get("successCount")).isEqualTo(0);
        assertThat(result.get("failCount")).isEqualTo(1);
        assertThat(vouchers.get(1L).getStatus()).isEqualTo(ScrmVoucherEntity.STATUS_VOID);
    }

    @Test
    @DisplayName("批量记账后 findCompanyIdByVoucherId 仍可查到归属主体（数据域校验依赖它）")
    void findCompanyIdStillWorksAfterBatch() throws ScrmException {
        putVoucher(1L, ScrmVoucherEntity.STATUS_DRAFT, "100.00", "100.00");
        service.batchPost(List.of(1L));

        assertThat(service.findCompanyIdByVoucherId(1L)).isEqualTo(COMPANY_ID);
    }
}
