/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmFixedAssetServiceTest.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.asset;

import org.hiylo.scrm.dto.ScrmVoucherSaveDto;
import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmFixedAssetDepreciationEntity;
import org.hiylo.scrm.entity.ScrmFixedAssetEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountSubjectRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmFixedAssetDepreciationRepository;
import org.hiylo.scrm.repository.ScrmFixedAssetRepository;
import org.hiylo.scrm.service.finance.voucher.ScrmVoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmFixedAssetService} 单元测试。
 * <p>覆盖：直线法月折旧、最后月补差、已提期间跳过幂等、缺科目报错、
 * 凭证草稿生成。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmFixedAssetService 固定资产折旧")
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ScrmFixedAssetServiceTest {

    @Mock
    private ScrmFixedAssetRepository assetRepository;

    @Mock
    private ScrmFixedAssetDepreciationRepository depreciationRepository;

    @Mock
    private ScrmVoucherService voucherService;

    @Mock
    private ScrmCompanyRepository companyRepository;

    @Mock
    private ScrmAccountSubjectRepository subjectRepository;

    private ScrmFixedAssetService service;

    @BeforeEach
    void setUp() {
        service = new ScrmFixedAssetService(assetRepository, depreciationRepository,
                voucherService, companyRepository, subjectRepository);
    }

    /** 构造原值 120000、残值率 5%、寿命 60 个月的电子设备 */
    private ScrmFixedAssetEntity asset(Long id, BigDecimal accumulated) {
        ScrmFixedAssetEntity a = new ScrmFixedAssetEntity();
        a.setId(id);
        a.setCompanyId(100L);
        a.setCompanyName("测试企业");
        a.setAssetName("办公电脑");
        a.setAssetCategory("ELECTRONIC");
        a.setOriginalValue(new BigDecimal("120000.00"));
        a.setResidualRate(new BigDecimal("0.0500"));
        a.setUsefulLifeMonths(60);
        a.setStartDate(LocalDate.of(2026, 1, 1));
        a.setAccumulatedDepreciation(accumulated);
        a.setStatus(ScrmFixedAssetEntity.STATUS_ACTIVE);
        return a;
    }

    private ScrmAccountSubjectEntity subject(String code) {
        ScrmAccountSubjectEntity s = new ScrmAccountSubjectEntity();
        s.setCompanyId(100L);
        s.setSubjectCode(code);
        s.setSubjectName(code + " 科目");
        s.setStatus(ScrmAccountSubjectEntity.STATUS_ACTIVE);
        return s;
    }

    @Test
    @DisplayName("直线法：月折旧 = 原值×(1-残值率)/寿命 = 120000×0.95/60 = 1900.00")
    void monthlyDepreciation_straightLine() {
        ScrmFixedAssetEntity a = asset(10L, BigDecimal.ZERO);
        assertThat(service.monthlyDepreciation(a)).isEqualByComparingTo("1900.00");
    }

    @Test
    @DisplayName("最后一个折旧月补差：剩余应提小于月折旧时只提剩余")
    void monthlyDepreciation_lastMonthClamps() {
        // 已提 113000，剩余应提 114000-113000=1000 < 1900 → 提 1000
        ScrmFixedAssetEntity a = asset(10L, new BigDecimal("113000.00"));
        assertThat(service.monthlyDepreciation(a)).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("累计已 ≥ 应提总额则不再计提（返回 0）")
    void monthlyDepreciation_fullyDepreciated() {
        ScrmFixedAssetEntity a = asset(10L, new BigDecimal("114000.00"));
        assertThat(service.monthlyDepreciation(a)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("计提：已计提期间跳过（幂等），发生额合计正确且生成凭证草稿")
    void monthlyDepreciate_idempotentAndCreatesVoucher() throws ScrmException {
        ScrmFixedAssetEntity a1 = asset(10L, BigDecimal.ZERO);
        ScrmFixedAssetEntity a2 = asset(11L, BigDecimal.ZERO);
        when(assetRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(100L,
                ScrmFixedAssetEntity.STATUS_ACTIVE)).thenReturn(List.of(a1, a2));
        // a2 本月已计提过 → 跳过（幂等）
        when(depreciationRepository.findByAssetIdAndPeriodYearAndPeriodMonth(
                11L, 2026, 11)).thenReturn(Optional.of(new ScrmFixedAssetDepreciationEntity()));
        // 科目齐全
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1601"))
                .thenReturn(Optional.of(subject("1601")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1602"))
                .thenReturn(Optional.of(subject("1602")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "6602"))
                .thenReturn(Optional.of(subject("6602")));
        when(assetRepository.save(any(ScrmFixedAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(depreciationRepository.save(any(ScrmFixedAssetDepreciationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(voucherService.createVoucher(anyLong(), any(ScrmVoucherSaveDto.class)))
                .thenReturn(Map.of("voucher", Map.of("id", 999L)));

        Map<String, Object> result = service.monthlyDepreciate(100L, 2026, 11, null);

        assertThat(result.get("count")).isEqualTo(1);
        assertThat(new BigDecimal(result.get("totalAmount").toString()))
                .isEqualByComparingTo("1900.00");
        assertThat(result.get("voucherId")).isEqualTo(999L);
        // a1 折旧 1900，累计从 0 → 1900；a2 已计提跳过，累计不动
        assertThat(a1.getAccumulatedDepreciation()).isEqualByComparingTo("1900.00");
        assertThat(a2.getAccumulatedDepreciation()).isEqualByComparingTo("0.00");
        // 凭证草稿「借 6602 贷 1602」、借贷平衡
        ArgumentCaptor<ScrmVoucherSaveDto> captor = ArgumentCaptor.forClass(ScrmVoucherSaveDto.class);
        verify(voucherService, times(1)).createVoucher(anyLong(), captor.capture());
        ScrmVoucherSaveDto dto = captor.getValue();
        assertThat(dto.getEntries()).hasSize(2);
        assertThat(dto.getEntries().get(0).getSubjectCode()).isEqualTo("6602");
        assertThat(dto.getEntries().get(0).getDebitAmount()).isEqualByComparingTo("1900.00");
        assertThat(dto.getEntries().get(1).getSubjectCode()).isEqualTo("1602");
        assertThat(dto.getEntries().get(1).getCreditAmount()).isEqualByComparingTo("1900.00");
    }

    @Test
    @DisplayName("缺累计折旧科目 → 抛错且不生成凭证")
    void monthlyDepreciate_missingSubject() {
        ScrmFixedAssetEntity a = asset(10L, BigDecimal.ZERO);
        when(assetRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(100L,
                ScrmFixedAssetEntity.STATUS_ACTIVE)).thenReturn(List.of(a));
        // 1601 存在，1602 缺失 → 报累计折旧科目缺失
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1601"))
                .thenReturn(Optional.of(subject("1601")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1602"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.monthlyDepreciate(100L, 2026, 11, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("累计折旧");
        verify(voucherService, never()).createVoucher(anyLong(), any(ScrmVoucherSaveDto.class));
    }

    @Test
    @DisplayName("新增卡片：参数非法抛错")
    void create_validation() {
        ScrmFixedAssetEntity bad = new ScrmFixedAssetEntity();
        bad.setCompanyId(100L);
        bad.setAssetName("");
        assertThatThrownBy(() -> service.create(bad))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("资产名称");
    }

    @Test
    @DisplayName("公司汇总：在用资产数/原值/已提/净值")
    void companySummary_calculates() throws ScrmException {
        ScrmFixedAssetEntity a1 = asset(10L, new BigDecimal("1900.00"));
        ScrmFixedAssetEntity a2 = asset(11L, BigDecimal.ZERO);
        when(assetRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(100L,
                ScrmFixedAssetEntity.STATUS_ACTIVE)).thenReturn(List.of(a1, a2));

        Map<String, Object> summary = service.companySummary(100L);

        assertThat(summary.get("assetCount")).isEqualTo(2);
        assertThat(new BigDecimal(summary.get("originalTotal").toString()))
                .isEqualByComparingTo("240000.00");
        assertThat(new BigDecimal(summary.get("accumulatedTotal").toString()))
                .isEqualByComparingTo("1900.00");
        assertThat(new BigDecimal(summary.get("netTotal").toString()))
                .isEqualByComparingTo("238100.00");
    }

    @Test
    @DisplayName("新增卡片成功：公司名冗余写入 + 净值=原值")
    void create_success() throws ScrmException {
        ScrmFixedAssetEntity a = asset(null, BigDecimal.ZERO);
        when(companyRepository.findById(100L))
                .thenReturn(Optional.of(new ScrmCompanyEntity()));
        when(assetRepository.save(any(ScrmFixedAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.create(a);

        assertThat(a.getStatus()).isEqualTo(ScrmFixedAssetEntity.STATUS_ACTIVE);
        assertThat(a.getNetValue()).isEqualByComparingTo("120000.00");
        assertThat(a.getAccumulatedDepreciation()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("期间非法（月份 13）→ 抛错")
    void monthlyDepreciate_invalidPeriod() {
        assertThatThrownBy(() -> service.monthlyDepreciate(100L, 2026, 13, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("月份");
    }

    @Test
    @DisplayName("未指定费用科目 + 旧准则企业（只有 5602）→ 自动探测 5602 成功计提")
    void monthlyDepreciate_autoDetectsLegacyExpenseSubject() throws ScrmException {
        ScrmFixedAssetEntity a = asset(10L, BigDecimal.ZERO);
        when(assetRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(100L,
                ScrmFixedAssetEntity.STATUS_ACTIVE)).thenReturn(List.of(a));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1601"))
                .thenReturn(Optional.of(subject("1601")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1602"))
                .thenReturn(Optional.of(subject("1602")));
        // 新准则 6602 不存在，旧准则 5602 存在
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "6602"))
                .thenReturn(Optional.empty());
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "5602"))
                .thenReturn(Optional.of(subject("5602")));
        when(assetRepository.save(any(ScrmFixedAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(depreciationRepository.save(any(ScrmFixedAssetDepreciationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(voucherService.createVoucher(anyLong(), any(ScrmVoucherSaveDto.class)))
                .thenReturn(Map.of("voucher", Map.of("id", 777L)));

        Map<String, Object> result = service.monthlyDepreciate(100L, 2026, 11, null);

        assertThat(result.get("count")).isEqualTo(1);
        // 凭证借方应落在自动探测出的 5602
        ArgumentCaptor<ScrmVoucherSaveDto> captor = ArgumentCaptor.forClass(ScrmVoucherSaveDto.class);
        verify(voucherService, times(1)).createVoucher(anyLong(), captor.capture());
        assertThat(captor.getValue().getEntries().get(0).getSubjectCode()).isEqualTo("5602");
    }

    @Test
    @DisplayName("未指定费用科目 + 新准则企业（6602 与 5602 都有）→ 优先 6602")
    void monthlyDepreciate_prefersNewStandardSubject() throws ScrmException {
        ScrmFixedAssetEntity a = asset(10L, BigDecimal.ZERO);
        when(assetRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(100L,
                ScrmFixedAssetEntity.STATUS_ACTIVE)).thenReturn(List.of(a));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1601"))
                .thenReturn(Optional.of(subject("1601")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1602"))
                .thenReturn(Optional.of(subject("1602")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "6602"))
                .thenReturn(Optional.of(subject("6602")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "5602"))
                .thenReturn(Optional.of(subject("5602")));
        when(assetRepository.save(any(ScrmFixedAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(depreciationRepository.save(any(ScrmFixedAssetDepreciationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(voucherService.createVoucher(anyLong(), any(ScrmVoucherSaveDto.class)))
                .thenReturn(Map.of("voucher", Map.of("id", 888L)));

        service.monthlyDepreciate(100L, 2026, 11, null);

        ArgumentCaptor<ScrmVoucherSaveDto> captor = ArgumentCaptor.forClass(ScrmVoucherSaveDto.class);
        verify(voucherService, times(1)).createVoucher(anyLong(), captor.capture());
        assertThat(captor.getValue().getEntries().get(0).getSubjectCode()).isEqualTo("6602");
    }

    @Test
    @DisplayName("未指定费用科目 + 企业无任何费用科目 → 报错提示两套准则科目号")
    void monthlyDepreciate_noExpenseSubjectAtAll() {
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1601"))
                .thenReturn(Optional.of(subject("1601")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1602"))
                .thenReturn(Optional.of(subject("1602")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "6602"))
                .thenReturn(Optional.empty());
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "5602"))
                .thenReturn(Optional.empty());
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "6601"))
                .thenReturn(Optional.empty());
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "5601"))
                .thenReturn(Optional.empty());
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "6603"))
                .thenReturn(Optional.empty());
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "5603"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.monthlyDepreciate(100L, 2026, 11, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("费用类科目");
    }

    @Test
    @DisplayName("batchDepreciate：多户逐户计提，某户缺科目失败不影响其它户")
    void batchDepreciate_partialFailureIsolated() {
        // 户 100 正常（有资产、科目齐全）；户 200 缺 1601 → 该户失败
        ScrmFixedAssetEntity a100 = asset(10L, BigDecimal.ZERO);
        a100.setCompanyId(100L);
        when(assetRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(100L,
                ScrmFixedAssetEntity.STATUS_ACTIVE)).thenReturn(List.of(a100));
        when(assetRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(200L,
                ScrmFixedAssetEntity.STATUS_ACTIVE)).thenReturn(List.of());
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1601"))
                .thenReturn(Optional.of(subject("1601")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "1602"))
                .thenReturn(Optional.of(subject("1602")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(100L, "6602"))
                .thenReturn(Optional.of(subject("6602")));
        when(subjectRepository.findByCompanyIdAndSubjectCode(200L, "1601"))
                .thenReturn(Optional.empty());
        when(assetRepository.save(any(ScrmFixedAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(depreciationRepository.save(any(ScrmFixedAssetDepreciationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(voucherService.createVoucher(anyLong(), any(ScrmVoucherSaveDto.class)))
                .thenReturn(Map.of("voucher", Map.of("id", 555L)));
        when(companyRepository.findById(anyLong())).thenReturn(Optional.empty());

        Map<String, Object> result =
                service.batchDepreciate(List.of(100L, 200L), 2026, 11, null);

        assertThat(result.get("totalCount")).isEqualTo(2);
        assertThat(result.get("successCount")).isEqualTo(1);
        assertThat(result.get("failCount")).isEqualTo(1);
        assertThat(new BigDecimal(result.get("totalAmount").toString()))
                .isEqualByComparingTo("1900.00");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        assertThat(details).hasSize(2);
        assertThat(details.get(0).get("success")).isEqualTo(true);
        assertThat(details.get(1).get("success")).isEqualTo(false);
    }

    @Test
    @DisplayName("batchDepreciate：空列表 → 0 户 0 金额，不抛")
    void batchDepreciate_emptySafe() {
        Map<String, Object> result = service.batchDepreciate(List.of(), 2026, 11, null);
        assertThat(result.get("totalCount")).isEqualTo(0);
        assertThat(result.get("successCount")).isEqualTo(0);
        assertThat(result.get("failCount")).isEqualTo(0);
    }
}