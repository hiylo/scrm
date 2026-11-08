/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmFixedAssetService.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.asset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmVoucherEntryDto;
import org.hiylo.scrm.dto.ScrmVoucherSaveDto;
import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmFixedAssetDepreciationEntity;
import org.hiylo.scrm.entity.ScrmFixedAssetEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountSubjectRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmFixedAssetDepreciationRepository;
import org.hiylo.scrm.repository.ScrmFixedAssetRepository;
import org.hiylo.scrm.service.finance.voucher.ScrmVoucherService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 固定资产折旧服务（卡片 CRUD + 直线法计提 + 凭证草稿联动）。
 * <p>
 * 算法：月折旧 = 原值 × (1 − 残值率) / 寿命月数，保留 2 位；最后一个折旧月
 * 补差使累计恰好 = 原值 × (1 − 残值率)。计提结果生成一张「借：费用科目
 * 贷：1602 累计折旧」的凭证草稿（调用 {@link ScrmVoucherService#createVoucher}，
 * status=DRAFT），人工确认后记账——绝不自动 POSTED。
 * </p>
 * <p>
 * 幂等：同资产同期间唯一（DB 唯一索引兜底 + Service 前置查重），
 * 重复计提同一期间直接跳过。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmFixedAssetService {

    /** 固定资产科目编码（标准科目表） */
    private static final String SUBJECT_FIXED_ASSET = "1601";

    /** 累计折旧科目编码（标准科目表） */
    private static final String SUBJECT_ACCUM_DEPRECIATION = "1602";

    /** 默认费用科目：管理费用 */
    private static final String DEFAULT_EXPENSE_SUBJECT = "6602";

    /** 凭证字：折旧用「转」 */
    private static final String VOUCHER_WORD = "转";

    /** 资产数据访问 */
    private final ScrmFixedAssetRepository assetRepository;

    /** 折旧记录数据访问 */
    private final ScrmFixedAssetDepreciationRepository depreciationRepository;

    /** 记账服务（生成折旧凭证草稿） */
    private final ScrmVoucherService voucherService;

    /** 企业数据访问（写公司名冗余字段） */
    private final ScrmCompanyRepository companyRepository;

    /** 科目数据访问（校验 1601/1602/费用科目存在） */
    private final ScrmAccountSubjectRepository subjectRepository;

    /**
     * 新增资产卡片。
     *
     * @param entity 卡片数据（companyId/assetName/originalValue/usefulLifeMonths 必填）
     * @return 保存后的卡片
     * @throws ScrmException 参数非法 / 企业不存在
     */
    @Transactional
    public ScrmFixedAssetEntity create(ScrmFixedAssetEntity entity) throws ScrmException {
        validateCreate(entity);
        ScrmCompanyEntity company = companyRepository.findById(entity.getCompanyId())
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + entity.getCompanyId()));
        entity.setCompanyName(company.getCompanyName());
        entity.setCreatedBy(UserContext.getUsername());
        entity.setStatus(ScrmFixedAssetEntity.STATUS_ACTIVE);
        entity.setAccumulatedDepreciation(BigDecimal.ZERO);
        entity.setNetValue(entity.getOriginalValue());
        ScrmFixedAssetEntity saved = assetRepository.save(entity);
        log.info("[固定资产-新增] companyId={}, asset={}, 原值={}", saved.getCompanyId(),
                saved.getAssetName(), saved.getOriginalValue());
        return saved;
    }

    /**
     * 修改资产卡片基本信息（仅在用可改；不重算已提折旧）。
     *
     * @param companyId 企业 ID
     * @param id        卡片 ID
     * @param patch     变更字段（原值/残值率/寿命变更时重算净值）
     * @return 更新后的卡片
     * @throws ScrmException 不存在 / 已报废
     */
    @Transactional
    public ScrmFixedAssetEntity update(Long companyId, Long id, ScrmFixedAssetEntity patch)
            throws ScrmException {
        ScrmFixedAssetEntity entity = assetRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> ScrmException.notFound("固定资产不存在: id=" + id));
        if (!ScrmFixedAssetEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
            throw ScrmException.badRequest("已报废的资产不可修改");
        }
        if (patch.getAssetName() != null && !patch.getAssetName().isBlank()) {
            entity.setAssetName(patch.getAssetName());
        }
        if (patch.getAssetCategory() != null && !patch.getAssetCategory().isBlank()) {
            entity.setAssetCategory(patch.getAssetCategory());
        }
        if (patch.getOriginalValue() != null) {
            if (patch.getOriginalValue().signum() <= 0) {
                throw ScrmException.badRequest("原值必须大于 0");
            }
            entity.setOriginalValue(patch.getOriginalValue());
        }
        if (patch.getResidualRate() != null) {
            if (patch.getResidualRate().signum() < 0
                    || patch.getResidualRate().compareTo(BigDecimal.ONE) >= 0) {
                throw ScrmException.badRequest("残值率必须在 [0, 1) 之间");
            }
            entity.setResidualRate(patch.getResidualRate());
        }
        if (patch.getUsefulLifeMonths() != null) {
            if (patch.getUsefulLifeMonths() <= 0) {
                throw ScrmException.badRequest("使用寿命必须大于 0 个月");
            }
            entity.setUsefulLifeMonths(patch.getUsefulLifeMonths());
        }
        if (patch.getStartDate() != null) {
            entity.setStartDate(patch.getStartDate());
        }
        if (patch.getBoughtDate() != null) {
            entity.setBoughtDate(patch.getBoughtDate());
        }
        if (patch.getRemark() != null) {
            entity.setRemark(patch.getRemark());
        }
        // 重新计算净值（已提折旧不变，只跟随原值变化）
        entity.setNetValue(entity.getOriginalValue().subtract(entity.getAccumulatedDepreciation()));
        ScrmFixedAssetEntity saved = assetRepository.save(entity);
        log.info("[固定资产-修改] id={}, asset={}", saved.getId(), saved.getAssetName());
        return saved;
    }

    /**
     * 资产报废（状态 → SCRAPPED）。
     *
     * @param companyId 企业 ID
     * @param id        卡片 ID
     * @return 更新后的卡片
     * @throws ScrmException 不存在 / 已报废
     */
    @Transactional
    public ScrmFixedAssetEntity scrap(Long companyId, Long id) throws ScrmException {
        ScrmFixedAssetEntity entity = assetRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> ScrmException.notFound("固定资产不存在: id=" + id));
        if (ScrmFixedAssetEntity.STATUS_SCRAPPED.equals(entity.getStatus())) {
            throw ScrmException.badRequest("资产已是报废状态");
        }
        entity.setStatus(ScrmFixedAssetEntity.STATUS_SCRAPPED);
        ScrmFixedAssetEntity saved = assetRepository.save(entity);
        log.info("[固定资产-报废] id={}, asset={}", saved.getId(), saved.getAssetName());
        return saved;
    }

    /**
     * 分页查询资产卡片（状态/类别过滤）。
     *
     * @param companyId 企业 ID
     * @param status    状态过滤（可空）
     * @param category  类别过滤（可空）
     * @param pageable  分页
     * @return 卡片分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmFixedAssetEntity> list(Long companyId, String status, String category,
                                           Pageable pageable) {
        return assetRepository.findByCompanyIdOrderByCreateTimeDesc(companyId, pageable);
    }

    /**
     * 企业资产汇总（在用资产的数/原值/已提/净值）。
     *
     * @param companyId 企业 ID
     * @return 汇总 Map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> companySummary(Long companyId) {
        List<ScrmFixedAssetEntity> actives =
                assetRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(
                        companyId, ScrmFixedAssetEntity.STATUS_ACTIVE);
        BigDecimal originalTotal = BigDecimal.ZERO;
        BigDecimal accumulatedTotal = BigDecimal.ZERO;
        for (ScrmFixedAssetEntity a : actives) {
            originalTotal = originalTotal.add(a.getOriginalValue() == null
                    ? BigDecimal.ZERO : a.getOriginalValue());
            accumulatedTotal = accumulatedTotal.add(a.getAccumulatedDepreciation() == null
                    ? BigDecimal.ZERO : a.getAccumulatedDepreciation());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetCount", actives.size());
        result.put("originalTotal", originalTotal);
        result.put("accumulatedTotal", accumulatedTotal);
        result.put("netTotal", originalTotal.subtract(accumulatedTotal));
        return result;
    }

    /**
     * 按月计提折旧（企业全部在用资产），生成一张凭证草稿。
     * <p>
     * 直线法：月折旧 = 原值 × (1 − 残值率) / 寿命月数，保留 2 位；
     * 最后一个折旧月补差至累计 = 原值 × (1 − 残值率)。
     * 已计提过的期间跳过（幂等）。发生额 &gt; 0 时调用凭证服务生成
     * 「借：费用科目 贷：1602 累计折旧」草稿。
     * </p>
     *
     * @param companyId          企业 ID
     * @param periodYear         会计年度
     * @param periodMonth        会计月份（1-12）
     * @param expenseSubjectCode 费用科目（默认 6602 管理费用）
     * @return {count, totalAmount, voucherId, message}
     * @throws ScrmException 期间非法 / 无固定资产科目 / 无费用科目
     */
    @Transactional
    public Map<String, Object> monthlyDepreciate(Long companyId, int periodYear, int periodMonth,
                                                 String expenseSubjectCode) throws ScrmException {
        if (periodMonth < 1 || periodMonth > 12) {
            throw ScrmException.badRequest("会计月份必须在 1-12 之间");
        }
        requireSubject(companyId, SUBJECT_FIXED_ASSET, "固定资产");
        requireSubject(companyId, SUBJECT_ACCUM_DEPRECIATION, "累计折旧");
        String expenseCode = expenseSubjectCode == null || expenseSubjectCode.isBlank()
                ? DEFAULT_EXPENSE_SUBJECT : expenseSubjectCode.trim();
        requireSubject(companyId, expenseCode, "费用");

        List<ScrmFixedAssetEntity> actives =
                assetRepository.findByCompanyIdAndStatusOrderByCreateTimeDesc(
                        companyId, ScrmFixedAssetEntity.STATUS_ACTIVE);
        BigDecimal totalAmount = BigDecimal.ZERO;
        int count = 0;
        for (ScrmFixedAssetEntity asset : actives) {
            if (depreciationRepository
                    .findByAssetIdAndPeriodYearAndPeriodMonth(asset.getId(), periodYear, periodMonth)
                    .isPresent()) {
                log.debug("[固定资产-折旧] 已计提跳过: assetId={}, {}-{}",
                        asset.getId(), periodYear, periodMonth);
                continue;
            }
            BigDecimal monthly = monthlyDepreciation(asset);
            if (monthly.signum() <= 0) {
                continue;
            }
            BigDecimal newAccumulated = asset.getAccumulatedDepreciation().add(monthly);
            ScrmFixedAssetDepreciationEntity record = new ScrmFixedAssetDepreciationEntity();
            record.setAssetId(asset.getId());
            record.setPeriodYear(periodYear);
            record.setPeriodMonth(periodMonth);
            record.setDepreciationAmount(monthly);
            record.setAccumulatedAfter(newAccumulated);
            record.setRemark("计提 " + periodYear + "年" + periodMonth + "月折旧");
            depreciationRepository.save(record);
            asset.setAccumulatedDepreciation(newAccumulated);
            asset.setNetValue(asset.getOriginalValue().subtract(newAccumulated));
            assetRepository.save(asset);
            totalAmount = totalAmount.add(monthly);
            count++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);
        result.put("totalAmount", totalAmount);
        if (count == 0 || totalAmount.signum() <= 0) {
            result.put("voucherId", null);
            result.put("message", "该期间没有需要计提的在用资产（可能已全部计提）");
            return result;
        }
        // 生成「借：费用 贷：累计折旧」凭证草稿
        ScrmVoucherSaveDto voucherDto = new ScrmVoucherSaveDto();
        voucherDto.setPeriodYear(periodYear);
        voucherDto.setPeriodMonth(periodMonth);
        voucherDto.setVoucherWord(VOUCHER_WORD);
        voucherDto.setVoucherDate(YearMonth.of(periodYear, periodMonth).atEndOfMonth());
        voucherDto.setRemark("计提 " + periodYear + "年" + periodMonth + "月折旧 " + count + " 项");
        voucherDto.setEntries(depreciationEntries(expenseCode, totalAmount));
        Map<String, Object> voucherDetail = voucherService.createVoucher(companyId, voucherDto);
        Object voucherId = extractId(voucherDetail);
        result.put("voucherId", voucherId);
        result.put("message", "计提完成并生成凭证草稿");
        log.info("[固定资产-折旧] companyId={}, {}-{}, 计提 {} 项 共 {} 元, 凭证={}",
                companyId, periodYear, periodMonth, count, totalAmount, voucherId);
        return result;
    }

    /**
     * 单资产直线法月折旧额（保留 2 位；最后月补差）。
     * <p>若累计已 ≥ 应提总额则返回 0（已提满）。</p>
     *
     * @param asset 资产
     * @return 当月折旧额（0 表示无需计提）
     */
    BigDecimal monthlyDepreciation(ScrmFixedAssetEntity asset) {
        BigDecimal original = asset.getOriginalValue();
        BigDecimal residual = asset.getResidualRate() == null
                ? ScrmFixedAssetEntity.DEFAULT_RESIDUAL_RATE : asset.getResidualRate();
        BigDecimal depreciable = original.multiply(BigDecimal.ONE.subtract(residual));
        BigDecimal accumulated = asset.getAccumulatedDepreciation() == null
                ? BigDecimal.ZERO : asset.getAccumulatedDepreciation();
        if (accumulated.compareTo(depreciable) >= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal monthly = depreciable.divide(BigDecimal.valueOf(asset.getUsefulLifeMonths()),
                2, RoundingMode.HALF_UP);
        BigDecimal remaining = depreciable.subtract(accumulated);
        if (monthly.compareTo(remaining) > 0) {
            monthly = remaining;
        }
        return monthly;
    }

    /** 组装「借：费用 贷：累计折旧」分录（二行，借贷平衡） */
    private List<ScrmVoucherEntryDto> depreciationEntries(String expenseCode, BigDecimal amount) {
        ScrmVoucherEntryDto debit = new ScrmVoucherEntryDto();
        debit.setSubjectCode(expenseCode);
        debit.setSummary("计提折旧");
        debit.setDebitAmount(amount);
        debit.setCreditAmount(BigDecimal.ZERO);

        ScrmVoucherEntryDto credit = new ScrmVoucherEntryDto();
        credit.setSubjectCode(SUBJECT_ACCUM_DEPRECIATION);
        credit.setSummary("计提折旧");
        credit.setDebitAmount(BigDecimal.ZERO);
        credit.setCreditAmount(amount);

        List<ScrmVoucherEntryDto> entries = new ArrayList<>();
        entries.add(debit);
        entries.add(credit);
        return entries;
    }

    private void validateCreate(ScrmFixedAssetEntity entity) throws ScrmException {
        if (entity.getCompanyId() == null) {
            throw ScrmException.badRequest("公司主体不能为空");
        }
        if (entity.getAssetName() == null || entity.getAssetName().isBlank()) {
            throw ScrmException.badRequest("资产名称不能为空");
        }
        if (entity.getOriginalValue() == null || entity.getOriginalValue().signum() <= 0) {
            throw ScrmException.badRequest("原值必须大于 0");
        }
        if (entity.getUsefulLifeMonths() == null || entity.getUsefulLifeMonths() <= 0) {
            throw ScrmException.badRequest("使用寿命必须大于 0 个月");
        }
        if (entity.getResidualRate() != null
                && (entity.getResidualRate().signum() < 0
                || entity.getResidualRate().compareTo(BigDecimal.ONE) >= 0)) {
            throw ScrmException.badRequest("残值率必须在 [0, 1) 之间");
        }
    }

    private void requireSubject(Long companyId, String code, String label) throws ScrmException {
        Optional<ScrmAccountSubjectEntity> subject =
                subjectRepository.findByCompanyIdAndSubjectCode(companyId, code);
        if (subject.isEmpty()
                || !ScrmAccountSubjectEntity.STATUS_ACTIVE.equals(subject.get().getStatus())) {
            throw ScrmException.badRequest(
                    "缺少可用科目「" + code + " " + label + "」，请先点「初始化标准科目」或补充该科目");
        }
    }

    private Object extractId(Map<String, Object> voucherDetail) {
        if (voucherDetail == null) {
            return null;
        }
        Object voucher = voucherDetail.get("voucher");
        if (voucher instanceof Map<?, ?> map) {
            return map.get("id");
        }
        if (voucher instanceof ScrmVoucherEntity entity) {
            return entity.getId();
        }
        return voucherDetail.get("id");
    }
}