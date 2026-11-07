/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmVoucherService.java
 * Date : 2026/10/25 18:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.UserContext;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记账服务（凭证域）。
 * <p>
 * <b>本类的三条硬约束</b>（全部在服务端强制，不依赖前端）：
 * </p>
 * <ol>
 *   <li><b>借贷平衡</b>：每张凭证 {@code sum(借) == sum(贷)} 且大于 0，
 *       用 {@link BigDecimal#compareTo} 精确比较，避免 {@code equals} 因
 *       scale 不同（100 与 100.00）误判；</li>
 *   <li><b>科目受控</b>：分录必须挂在该企业已启用的科目上，禁止自由文本科目；</li>
 *   <li><b>记账后不可改</b>：POSTED 凭证不允许改分录，只能 VOID 作废后重开，
 *       保证历史账目可审计。</li>
 * </ol>
 * <p>
 * <b>草稿为什么不参与余额</b>：草稿是「还没定稿的账」，若计入余额，
 * 会计改一笔草稿就会让报表跳动，且无法解释。只统计 POSTED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmVoucherService {

    /** 金额精度 */
    private static final int MONEY_SCALE = 2;

    /** 单张凭证最大分录行数（防止误粘贴超长明细） */
    private static final int MAX_ENTRY_LINES = 500;

    /** 科目仓储 */
    private final ScrmAccountSubjectRepository subjectRepository;

    /** 凭证仓储 */
    private final ScrmVoucherRepository voucherRepository;

    /** 分录仓储 */
    private final ScrmVoucherEntryRepository entryRepository;

    /** 公司主体仓储 */
    private final ScrmCompanyRepository companyRepository;

    /** 期间服务（已结账期间禁止新增凭证） */
    private final ScrmPeriodCloseService periodCloseService;

    /** 财税数据范围（会计只看到自己负责主体的凭证） */
    private final org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    // ============================================================
    // 科目
    // ============================================================

    /**
     * 初始化企业标准科目表（小企业会计准则常用科目）。
     * <p>
     * 幂等：已存在的科目编码跳过，不会覆盖企业自定义的名称与状态。
     * 已经有科目的企业不会被重复初始化，避免把停用科目重新启用。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @return 本次新增科目数
     * @throws ScrmException 主体不存在
     */
    @Transactional
    public int initSubjects(Long companyId) throws ScrmException {
        ScrmCompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));
        int created = 0;
        for (String[] def : STANDARD_SUBJECTS) {
            String code = def[0];
            if (subjectRepository.findByCompanyIdAndSubjectCode(companyId, code).isPresent()) {
                continue;
            }
            ScrmAccountSubjectEntity entity = new ScrmAccountSubjectEntity();
            entity.setCompanyId(companyId);
            entity.setCompanyName(company.getCompanyName());
            entity.setSubjectCode(code);
            entity.setSubjectName(def[1]);
            entity.setCategory(def[2]);
            entity.setDirection(def[3]);
            entity.setSubjectLevel(code.length() <= 4 ? 1 : 2);
            entity.setParentCode(code.length() <= 4 ? null : code.substring(0, 4));
            entity.setStatus(ScrmAccountSubjectEntity.STATUS_ACTIVE);
            entity.setCreatedBy(operator());
            subjectRepository.save(entity);
            created++;
        }
        log.info("[凭证-科目初始化] companyId={}, created={}", companyId, created);
        return created;
    }

    /**
     * 新增自定义科目。
     *
     * @param entity 科目信息（companyId、subjectCode、subjectName、category、direction 必填）
     * @return 创建后的科目
     * @throws ScrmException 参数非法 / 主体不存在 / 编码重复
     */
    @Transactional
    public ScrmAccountSubjectEntity createSubject(ScrmAccountSubjectEntity entity) throws ScrmException {
        if (entity == null || entity.getCompanyId() == null) {
            throw ScrmException.badRequest("所属公司不能为空");
        }
        if (entity.getSubjectCode() == null || entity.getSubjectCode().isBlank()) {
            throw ScrmException.badRequest("科目编码不能为空");
        }
        if (entity.getSubjectName() == null || entity.getSubjectName().isBlank()) {
            throw ScrmException.badRequest("科目名称不能为空");
        }
        ScrmCompanyEntity company = companyRepository.findById(entity.getCompanyId())
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + entity.getCompanyId()));
        if (subjectRepository.findByCompanyIdAndSubjectCode(
                entity.getCompanyId(), entity.getSubjectCode()).isPresent()) {
            throw ScrmException.conflict("科目编码已存在: " + entity.getSubjectCode());
        }
        entity.setId(null);
        entity.setCompanyName(company.getCompanyName());
        entity.setCreatedBy(operator());
        if (entity.getStatus() == null) {
            entity.setStatus(ScrmAccountSubjectEntity.STATUS_ACTIVE);
        }
        if (entity.getSubjectLevel() == null) {
            entity.setSubjectLevel(entity.getSubjectCode().length() <= 4 ? 1 : 2);
        }
        return subjectRepository.save(entity);
    }

    /**
     * 停用/启用科目。
     *
     * @param id     科目 ID
     * @param status 目标状态
     * @return 更新后的科目
     * @throws ScrmException 科目不存在
     */
    @Transactional
    public ScrmAccountSubjectEntity updateSubjectStatus(Long id, String status) throws ScrmException {
        ScrmAccountSubjectEntity entity = subjectRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("科目不存在: " + id));
        entity.setStatus(status);
        return subjectRepository.save(entity);
    }

    /**
     * 查询企业科目列表。
     *
     * @param companyId 公司主体 ID
     * @return 科目列表（按编码升序）
     */
    @Transactional(readOnly = true)
    public List<ScrmAccountSubjectEntity> listSubjects(Long companyId) {
        return subjectRepository.findByCompanyIdOrderBySubjectCodeAsc(companyId);
    }

    // ============================================================
    // 凭证：保存 / 记账 / 作废
    // ============================================================

    /**
     * 新建凭证草稿。
     *
     * @param companyId 公司主体 ID
     * @param dto       凭证内容
     * @return 创建后的凭证（含分录）
     * @throws ScrmException 参数非法 / 借贷不平 / 科目不存在
     */
    @Transactional
    public Map<String, Object> createVoucher(Long companyId, ScrmVoucherSaveDto dto) throws ScrmException {
        if (companyId == null) {
            throw ScrmException.badRequest("公司主体不能为空");
        }
        ScrmCompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));
        validatePeriod(dto);
        // 已结账期间必须封住，否则出过的报表会因事后补录而失真
        if (periodCloseService.isClosed(companyId, dto.getPeriodYear(), dto.getPeriodMonth())) {
            throw ScrmException.conflict("该期间已结账，不可新增凭证: "
                    + dto.getPeriodYear() + "-" + dto.getPeriodMonth() + "；如需补录请先反结账");
        }

        ScrmVoucherEntity voucher = new ScrmVoucherEntity();
        voucher.setCompanyId(companyId);
        voucher.setCompanyName(company.getCompanyName());
        voucher.setPeriodYear(dto.getPeriodYear());
        voucher.setPeriodMonth(dto.getPeriodMonth());
        voucher.setVoucherWord(normalizeWord(dto.getVoucherWord()));
        voucher.setVoucherDate(dto.getVoucherDate() != null
                ? dto.getVoucherDate() : LocalDate.now());
        voucher.setAttachmentCount(dto.getAttachmentCount() == null ? 0 : dto.getAttachmentCount());
        voucher.setRemark(dto.getRemark());
        voucher.setStatus(ScrmVoucherEntity.STATUS_DRAFT);
        voucher.setCreatedBy(operator());

        // 凭证号自动续号：同企业同期同字内 +1
        Integer maxNo = voucherRepository.findMaxVoucherNo(
                companyId, dto.getPeriodYear(), dto.getPeriodMonth(), voucher.getVoucherWord());
        voucher.setVoucherNo(maxNo == null ? 1 : maxNo + 1);

        List<ScrmVoucherEntryEntity> entries = validateAndBuildEntries(
                companyId, voucher.getId(), dto.getEntries());
        voucher.setTotalDebit(sum(entries, true));
        voucher.setTotalCredit(sum(entries, false));
        voucher.setEntryCount(entries.size());

        ScrmVoucherEntity saved = voucherRepository.save(voucher);
        for (ScrmVoucherEntryEntity entry : entries) {
            entry.setVoucherId(saved.getId());
        }
        entryRepository.saveAll(entries);
        log.info("[凭证-新建] companyId={}, {}-{}-{}, 借贷={}",
                companyId, saved.getPeriodYear(), saved.getPeriodMonth(),
                saved.getVoucherLabel(), saved.getTotalDebit());
        return detail(saved.getId());
    }

    /**
     * 修改凭证草稿（整体替换分录）。
     *
     * @param id  凭证 ID
     * @param dto 新内容
     * @return 更新后的凭证
     * @throws ScrmException 凭证不存在 / 已记账不可改 / 借贷不平
     */
    @Transactional
    public Map<String, Object> updateVoucher(Long id, ScrmVoucherSaveDto dto) throws ScrmException {
        ScrmVoucherEntity voucher = findVoucherOrThrow(id);
        if (!ScrmVoucherEntity.STATUS_DRAFT.equals(voucher.getStatus())) {
            throw ScrmException.conflict("已记账或已作废的凭证不可修改；请先作废后重开: "
                    + voucher.getVoucherLabel());
        }
        validatePeriod(dto);
        if (periodCloseService.isClosed(voucher.getCompanyId(),
                dto.getPeriodYear(), dto.getPeriodMonth())) {
            throw ScrmException.conflict("该期间已结账，不可修改凭证: "
                    + dto.getPeriodYear() + "-" + dto.getPeriodMonth());
        }
        List<ScrmVoucherEntryEntity> entries = validateAndBuildEntries(
                voucher.getCompanyId(), voucher.getId(), dto.getEntries());

        // 整体替换：先删旧分录再写新分录，避免残留行导致合计虚高
        entryRepository.deleteByVoucherId(id);

        voucher.setPeriodYear(dto.getPeriodYear());
        voucher.setPeriodMonth(dto.getPeriodMonth());
        voucher.setVoucherWord(normalizeWord(dto.getVoucherWord()));
        if (dto.getVoucherDate() != null) {
            voucher.setVoucherDate(dto.getVoucherDate());
        }
        if (dto.getAttachmentCount() != null) {
            voucher.setAttachmentCount(dto.getAttachmentCount());
        }
        if (dto.getRemark() != null) {
            voucher.setRemark(dto.getRemark());
        }
        voucher.setTotalDebit(sum(entries, true));
        voucher.setTotalCredit(sum(entries, false));
        voucher.setEntryCount(entries.size());
        voucherRepository.save(voucher);

        entryRepository.saveAll(entries);
        return detail(id);
    }

    /**
     * 记账（DRAFT → POSTED）。
     * <p>记账前再校验一次平衡：草稿可能被并发修改，落库前的校验才是最终防线。</p>
     *
     * @param id 凭证 ID
     * @return 更新后的凭证
     * @throws ScrmException 凭证不存在 / 状态不允许 / 借贷不平
     */
    @Transactional
    public Map<String, Object> postVoucher(Long id) throws ScrmException {
        ScrmVoucherEntity voucher = findVoucherOrThrow(id);
        if (ScrmVoucherEntity.STATUS_POSTED.equals(voucher.getStatus())) {
            throw ScrmException.conflict("凭证已记账，无需重复操作: " + voucher.getVoucherLabel());
        }
        if (ScrmVoucherEntity.STATUS_VOID.equals(voucher.getStatus())) {
            throw ScrmException.conflict("凭证已作废，不可记账: " + voucher.getVoucherLabel());
        }
        if (periodCloseService.isClosed(voucher.getCompanyId(),
                voucher.getPeriodYear(), voucher.getPeriodMonth())) {
            throw ScrmException.conflict("该期间已结账，不可记账: "
                    + voucher.getPeriodYear() + "-" + voucher.getPeriodMonth());
        }
        // 以库里分录为准重新校验，不信任 header 上可能过期的合计
        List<ScrmVoucherEntryEntity> entries = entryRepository.findByVoucherIdOrderByLineNoAsc(id);
        assertBalanced(entries, voucher.getVoucherLabel());

        voucher.setTotalDebit(sum(entries, true));
        voucher.setTotalCredit(sum(entries, false));
        voucher.setEntryCount(entries.size());
        voucher.setStatus(ScrmVoucherEntity.STATUS_POSTED);
        voucher.setPostedBy(operator());
        voucher.setPostedAt(LocalDateTime.now());
        voucherRepository.save(voucher);
        log.info("[凭证-记账] id={}, label={}, 借贷={}",
                id, voucher.getVoucherLabel(), voucher.getTotalDebit());
        return detail(id);
    }

    /**
     * 作废凭证（退出余额，保留痕迹）。
     *
     * @param id     凭证 ID
     * @param reason 作废原因（可选）
     * @return 更新后的凭证
     * @throws ScrmException 凭证不存在 / 已作废
     */
    @Transactional
    public Map<String, Object> voidVoucher(Long id, String reason) throws ScrmException {
        ScrmVoucherEntity voucher = findVoucherOrThrow(id);
        if (ScrmVoucherEntity.STATUS_VOID.equals(voucher.getStatus())) {
            throw ScrmException.conflict("凭证已作废: " + voucher.getVoucherLabel());
        }
        voucher.setStatus(ScrmVoucherEntity.STATUS_VOID);
        voucher.setVoidedBy(operator());
        voucher.setVoidedAt(LocalDateTime.now());
        if (reason != null && !reason.isBlank()) {
            voucher.setRemark(reason.trim());
        }
        voucherRepository.save(voucher);
        log.info("[凭证-作废] id={}, label={}, reason={}", id, voucher.getVoucherLabel(), reason);
        return detail(id);
    }

    /**
     * 删除凭证（仅允许草稿；已记账请用作废）。
     *
     * @param id 凭证 ID
     * @throws ScrmException 凭证不存在 / 非草稿
     */
    @Transactional
    public void deleteVoucher(Long id) throws ScrmException {
        ScrmVoucherEntity voucher = findVoucherOrThrow(id);
        if (!ScrmVoucherEntity.STATUS_DRAFT.equals(voucher.getStatus())) {
            throw ScrmException.conflict("只有草稿可以删除；已记账请用作废保留痕迹: "
                    + voucher.getVoucherLabel());
        }
        entryRepository.deleteByVoucherId(id);
        voucherRepository.delete(voucher);
    }

    // ============================================================
    // 查询 / 报表
    // ============================================================

    /**
     * 分页查询凭证。
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年（可空）
     * @param periodMonth 月（可空）
     * @param status      状态（可空）
     * @param pageable    分页
     * @return 凭证分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmVoucherEntity> listVouchers(Long companyId, Integer periodYear,
                                                Integer periodMonth, String status,
                                                Pageable pageable) {
        // 财税数据范围：会计只能看到自己负责主体的凭证
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        return voucherRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accessibleCompanyIds != null) {
                if (accessibleCompanyIds.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("companyId").in(accessibleCompanyIds));
                }
            }
            if (companyId != null) {
                predicates.add(cb.equal(root.get("companyId"), companyId));
            }
            if (periodYear != null) {
                predicates.add(cb.equal(root.get("periodYear"), periodYear));
            }
            if (periodMonth != null) {
                predicates.add(cb.equal(root.get("periodMonth"), periodMonth));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }

    /**
     * 查询凭证详情（头 + 分录）。
     *
     * @param id 凭证 ID
     * @return 凭证详情
     * @throws ScrmException 凭证不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) throws ScrmException {
        ScrmVoucherEntity voucher = findVoucherOrThrow(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("voucher", voucher);
        result.put("entries", entryRepository.findByVoucherIdOrderByLineNoAsc(id));
        return result;
    }

    /**
     * 试算平衡表（期初 + 本期发生 → 期末）。
     * <p>
     * <b>这是「账做对没有」的唯一校验手段</b>：所有科目期末借方余额合计
     * 必须等于期末贷方余额合计，不等就说明账不平，必须查出来。
     * 因此结果里直接带上 {@code balanced} 与两侧合计，让会计一眼看到。
     * </p>
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @return 试算平衡表
     */
    @Transactional(readOnly = true)
    public Map<String, Object> trialBalance(Long companyId, int periodYear, int periodMonth) {
        int yyyymm = periodYear * 100 + periodMonth;
        Map<String, Object[]> beforeMap = toMap(entryRepository.sumBeforePeriod(companyId, yyyymm));
        Map<String, Object[]> inMap = toMap(entryRepository.sumInPeriod(companyId, yyyymm));

        List<ScrmAccountSubjectEntity> subjects = subjectRepository
                .findByCompanyIdOrderBySubjectCodeAsc(companyId);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalOpeningDebit = BigDecimal.ZERO;
        BigDecimal totalOpeningCredit = BigDecimal.ZERO;
        BigDecimal totalPeriodDebit = BigDecimal.ZERO;
        BigDecimal totalPeriodCredit = BigDecimal.ZERO;
        BigDecimal totalClosingDebit = BigDecimal.ZERO;
        BigDecimal totalClosingCredit = BigDecimal.ZERO;

        for (ScrmAccountSubjectEntity subject : subjects) {
            String code = subject.getSubjectCode();
            Object[] before = beforeMap.get(code);
            Object[] in = inMap.get(code);
            BigDecimal openD = before == null ? BigDecimal.ZERO : toDecimal(before[2]);
            BigDecimal openC = before == null ? BigDecimal.ZERO : toDecimal(before[3]);
            BigDecimal perD = in == null ? BigDecimal.ZERO : toDecimal(in[2]);
            BigDecimal perC = in == null ? BigDecimal.ZERO : toDecimal(in[3]);

            // 只有有发生额或余额的科目才出现在表里，否则科目表一长全是 0 行
            if (openD.compareTo(BigDecimal.ZERO) == 0 && openC.compareTo(BigDecimal.ZERO) == 0
                    && perD.compareTo(BigDecimal.ZERO) == 0 && perC.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // 净额一律按「借 − 贷」算，与科目余额方向无关。
            // 方向只说明科目的正常余额在哪一侧，不参与算术：
            // 贷方科目（如应交税费）也会因多缴而出现借方余额，按方向强行归侧会算错。
            BigDecimal openingNet = openD.subtract(openC);
            BigDecimal openingDebit = openingNet.compareTo(BigDecimal.ZERO) > 0
                    ? openingNet : BigDecimal.ZERO;
            BigDecimal openingCredit = openingNet.compareTo(BigDecimal.ZERO) < 0
                    ? openingNet.negate() : BigDecimal.ZERO;

            BigDecimal closingNet = openD.add(perD).subtract(openC.add(perC));
            BigDecimal closingDebit = closingNet.compareTo(BigDecimal.ZERO) > 0
                    ? closingNet : BigDecimal.ZERO;
            BigDecimal closingCredit = closingNet.compareTo(BigDecimal.ZERO) < 0
                    ? closingNet.negate() : BigDecimal.ZERO;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subjectCode", code);
            row.put("subjectName", subject.getSubjectName());
            row.put("category", subject.getCategory());
            row.put("direction", subject.getDirection());
            row.put("openingDebit", openingDebit);
            row.put("openingCredit", openingCredit);
            row.put("periodDebit", perD);
            row.put("periodCredit", perC);
            row.put("closingDebit", closingDebit);
            row.put("closingCredit", closingCredit);
            rows.add(row);

            totalOpeningDebit = totalOpeningDebit.add(openingDebit);
            totalOpeningCredit = totalOpeningCredit.add(openingCredit);
            totalPeriodDebit = totalPeriodDebit.add(perD);
            totalPeriodCredit = totalPeriodCredit.add(perC);
            totalClosingDebit = totalClosingDebit.add(closingDebit);
            totalClosingCredit = totalClosingCredit.add(closingCredit);
        }

        // 本期借贷发生额根据凭证设计必然相等；期末借贷不等说明账不平
        boolean balanced = totalClosingDebit.compareTo(totalClosingCredit) == 0
                && totalOpeningDebit.compareTo(totalOpeningCredit) == 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", yyyymm);
        result.put("rows", rows);
        result.put("totalOpeningDebit", totalOpeningDebit);
        result.put("totalOpeningCredit", totalOpeningCredit);
        result.put("totalPeriodDebit", totalPeriodDebit);
        result.put("totalPeriodCredit", totalPeriodCredit);
        result.put("totalClosingDebit", totalClosingDebit);
        result.put("totalClosingCredit", totalClosingCredit);
        result.put("balanced", balanced);
        // 注意：rows 里的 direction 只是科目性质（正常余额方向），不是本期余额所在侧。
        // 某科目若出现反方向余额（例如应交税费多缴），看过 closingDebit/closingCredit 即可。
        return result;
    }

    /**
     * 期汇总（凭证数、借贷合计、草稿/已记账/已作废计数）。
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @return 汇总
     */
    @Transactional(readOnly = true)
    public Map<String, Object> periodSummary(Long companyId, int periodYear, int periodMonth) {
        Page<ScrmVoucherEntity> all = listVouchers(companyId, periodYear, periodMonth, null,
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE));
        long draft = 0;
        long posted = 0;
        long voided = 0;
        BigDecimal postedDebit = BigDecimal.ZERO;
        for (ScrmVoucherEntity voucher : all.getContent()) {
            switch (voucher.getStatus()) {
                case ScrmVoucherEntity.STATUS_POSTED -> {
                    posted++;
                    postedDebit = postedDebit.add(voucher.getTotalDebit() == null
                            ? BigDecimal.ZERO : voucher.getTotalDebit());
                }
                case ScrmVoucherEntity.STATUS_VOID -> voided++;
                default -> draft++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.getTotalElements());
        result.put("draft", draft);
        result.put("posted", posted);
        result.put("voided", voided);
        result.put("postedDebitTotal", postedDebit);
        return result;
    }

    // ============================================================
    // 内部校验
    // ============================================================

    /**
     * 校验并构建分录实体。
     * <p>所有校验集中在这里，新建与修改共用同一套规则，避免两条路径口径不一致。</p>
     *
     * @param companyId 公司主体 ID
     * @param voucherId 凭证 ID（新建时为 null）
     * @param dtos      分录入参
     * @return 待持久化的分录
     * @throws ScrmException 分录非法 / 借贷不平 / 科目不存在或停用
     */
    private List<ScrmVoucherEntryEntity> validateAndBuildEntries(Long companyId, Long voucherId,
                                                                 List<ScrmVoucherEntryDto> dtos)
            throws ScrmException {
        if (dtos == null || dtos.isEmpty()) {
            throw ScrmException.badRequest("凭证至少需要 2 条分录");
        }
        if (dtos.size() < 2) {
            throw ScrmException.badRequest("凭证至少需要 2 条分录（有借必有贷）");
        }
        if (dtos.size() > MAX_ENTRY_LINES) {
            throw ScrmException.badRequest("单张凭证分录超过上限 " + MAX_ENTRY_LINES + " 行");
        }

        // 一次取出企业全部科目，避免逐行查库（N+1）
        Map<String, ScrmAccountSubjectEntity> subjectMap = new LinkedHashMap<>();
        for (ScrmAccountSubjectEntity subject : subjectRepository
                .findByCompanyIdOrderBySubjectCodeAsc(companyId)) {
            subjectMap.put(subject.getSubjectCode(), subject);
        }

        List<ScrmVoucherEntryEntity> entries = new ArrayList<>();
        int lineNo = 1;
        for (ScrmVoucherEntryDto dto : dtos) {
            if (dto == null) {
                throw ScrmException.badRequest("第 " + lineNo + " 行分录为空");
            }
            String code = dto.getSubjectCode() == null ? "" : dto.getSubjectCode().trim();
            if (code.isEmpty()) {
                throw ScrmException.badRequest("第 " + lineNo + " 行未选择科目");
            }
            ScrmAccountSubjectEntity subject = subjectMap.get(code);
            if (subject == null) {
                throw ScrmException.badRequest("第 " + lineNo + " 行科目不存在: " + code);
            }
            if (!ScrmAccountSubjectEntity.STATUS_ACTIVE.equals(subject.getStatus())) {
                throw ScrmException.badRequest("第 " + lineNo + " 行科目已停用，不可记账: " + code);
            }

            BigDecimal debit = scale(dto.getDebitAmount());
            BigDecimal credit = scale(dto.getCreditAmount());
            if (debit.compareTo(BigDecimal.ZERO) < 0 || credit.compareTo(BigDecimal.ZERO) < 0) {
                throw ScrmException.badRequest("第 " + lineNo + " 行金额不能为负数（请用借/贷方向表达）");
            }
            if (debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) > 0) {
                throw ScrmException.badRequest("第 " + lineNo + " 行借贷金额不能同时非零");
            }
            if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
                throw ScrmException.badRequest("第 " + lineNo + " 行借贷金额不能同时为零");
            }

            ScrmVoucherEntryEntity entry = new ScrmVoucherEntryEntity();
            entry.setVoucherId(voucherId);
            entry.setCompanyId(companyId);
            entry.setLineNo(lineNo);
            entry.setSubjectCode(subject.getSubjectCode());
            // 科目名快照：科目日后改名不影响历史凭证的可读性
            entry.setSubjectName(subject.getSubjectName());
            entry.setSummary(dto.getSummary());
            entry.setDebitAmount(debit);
            entry.setCreditAmount(credit);
            entries.add(entry);
            lineNo++;
        }

        assertBalanced(entries, "新凭证");
        return entries;
    }

    /**
     * 断言借贷平衡。
     *
     * @param entries 分录
     * @param label   凭证标识（用于错误信息）
     * @throws ScrmException 借贷不平或合计为零
     */
    private void assertBalanced(List<ScrmVoucherEntryEntity> entries, String label)
            throws ScrmException {
        if (entries == null || entries.isEmpty()) {
            throw ScrmException.badRequest("凭证没有分录，无法记账");
        }
        BigDecimal debit = sum(entries, true);
        BigDecimal credit = sum(entries, false);
        if (debit.compareTo(BigDecimal.ZERO) == 0) {
            throw ScrmException.badRequest("凭证金额合计为 0，不能记账: " + label);
        }
        if (debit.compareTo(credit) != 0) {
            throw ScrmException.badRequest(String.format(
                    "借贷不平衡: %s 借方 %s ≠ 贷方 %s（差额 %s）",
                    label, debit.toPlainString(), credit.toPlainString(),
                    debit.subtract(credit).abs().toPlainString()));
        }
    }

    /** 汇总借方或贷方金额。 */
    private BigDecimal sum(List<ScrmVoucherEntryEntity> entries, boolean debit) {
        BigDecimal total = BigDecimal.ZERO;
        for (ScrmVoucherEntryEntity entry : entries) {
            BigDecimal value = debit ? entry.getDebitAmount() : entry.getCreditAmount();
            total = total.add(value == null ? BigDecimal.ZERO : value);
        }
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 校验期间参数。 */
    private void validatePeriod(ScrmVoucherSaveDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("凭证内容不能为空");
        }
        if (dto.getPeriodYear() == null || dto.getPeriodMonth() == null) {
            throw ScrmException.badRequest("会计期间不能为空");
        }
        if (dto.getPeriodMonth() < 1 || dto.getPeriodMonth() > 12) {
            throw ScrmException.badRequest("会计月份必须在 1-12 之间: " + dto.getPeriodMonth());
        }
        if (dto.getPeriodYear() < 2000 || dto.getPeriodYear() > 2100) {
            throw ScrmException.badRequest("会计年度超出合理范围: " + dto.getPeriodYear());
        }
    }

    /** 凭证字归一。 */
    private String normalizeWord(String word) {
        return word == null || word.isBlank() ? ScrmVoucherEntity.WORD_JI : word.trim();
    }

    /** 金额归一到两位小数。 */
    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Object → BigDecimal（原生聚合返回的可能是 BigDecimal 或 Double）。 */
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

    /** 聚合结果转 Map（key = 科目编码）。 */
    private Map<String, Object[]> toMap(List<Object[]> rows) {
        Map<String, Object[]> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put(String.valueOf(row[0]), row);
        }
        return map;
    }

    /**
     * 查询凭证所属的公司主体 ID（越权校验用）。
     * <p>
     * 详情类端点只拿到凭证 ID，需要回查归属主体才能判断当前会计是否有权访问，
     * 因此单独暴露最小信息，避免把整个实体透出到控制器。
     * </p>
     *
     * @param voucherId 凭证 ID
     * @return 公司主体 ID
     * @throws ScrmException 凭证不存在
     */
    @Transactional(readOnly = true)
    public Long findCompanyIdByVoucherId(Long voucherId) throws ScrmException {
        return findVoucherOrThrow(voucherId).getCompanyId();
    }

    /** 查询凭证或抛 404。 */
    private ScrmVoucherEntity findVoucherOrThrow(Long id) throws ScrmException {
        return voucherRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("凭证不存在: " + id));
    }

    /** 当前操作人。 */
    private String operator() {
        String userId = UserContext.getUserId();
        return userId != null && !userId.isBlank() ? userId : "SYSTEM";
    }

    /**
     * 标准科目表（小企业会计准则常用科目）。
     * <p>格式：{编码, 名称, 类别, 余额方向}。只放高频科目，
     * 企业需要的其他科目由会计自行新增，不强塞。</p>
     */
    private static final String[][] STANDARD_SUBJECTS = {
            // ---- 资产 ----
            {"1001", "库存现金", ScrmAccountSubjectEntity.CATEGORY_ASSET, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"1002", "银行存款", ScrmAccountSubjectEntity.CATEGORY_ASSET, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"1122", "应收账款", ScrmAccountSubjectEntity.CATEGORY_ASSET, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"1123", "预付账款", ScrmAccountSubjectEntity.CATEGORY_ASSET, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"1221", "其他应收款", ScrmAccountSubjectEntity.CATEGORY_ASSET, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"1403", "原材料", ScrmAccountSubjectEntity.CATEGORY_ASSET, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"1405", "库存商品", ScrmAccountSubjectEntity.CATEGORY_ASSET, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"1601", "固定资产", ScrmAccountSubjectEntity.CATEGORY_ASSET, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"1602", "累计折旧", ScrmAccountSubjectEntity.CATEGORY_ASSET, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            {"1701", "无形资产", ScrmAccountSubjectEntity.CATEGORY_ASSET, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            // ---- 负债 ----
            {"2001", "短期借款", ScrmAccountSubjectEntity.CATEGORY_LIABILITY, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            {"2202", "应付账款", ScrmAccountSubjectEntity.CATEGORY_LIABILITY, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            {"2203", "预收账款", ScrmAccountSubjectEntity.CATEGORY_LIABILITY, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            {"2211", "应付职工薪酬", ScrmAccountSubjectEntity.CATEGORY_LIABILITY, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            {"2221", "应交税费", ScrmAccountSubjectEntity.CATEGORY_LIABILITY, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            {"2241", "其他应付款", ScrmAccountSubjectEntity.CATEGORY_LIABILITY, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            // ---- 所有者权益 ----
            {"3001", "实收资本", ScrmAccountSubjectEntity.CATEGORY_EQUITY, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            {"3103", "本年利润", ScrmAccountSubjectEntity.CATEGORY_EQUITY, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            {"3104", "利润分配", ScrmAccountSubjectEntity.CATEGORY_EQUITY, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            // ---- 成本 ----
            {"4001", "生产成本", ScrmAccountSubjectEntity.CATEGORY_COST, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"4101", "制造费用", ScrmAccountSubjectEntity.CATEGORY_COST, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            // ---- 损益-收入 ----
            {"5001", "主营业务收入", ScrmAccountSubjectEntity.CATEGORY_INCOME, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            {"5051", "其他业务收入", ScrmAccountSubjectEntity.CATEGORY_INCOME, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            {"5301", "营业外收入", ScrmAccountSubjectEntity.CATEGORY_INCOME, ScrmAccountSubjectEntity.DIRECTION_CREDIT},
            // ---- 损益-支出 ----
            {"5401", "主营业务成本", ScrmAccountSubjectEntity.CATEGORY_EXPENSE, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"5402", "其他业务成本", ScrmAccountSubjectEntity.CATEGORY_EXPENSE, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"5403", "税金及附加", ScrmAccountSubjectEntity.CATEGORY_EXPENSE, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"5601", "销售费用", ScrmAccountSubjectEntity.CATEGORY_EXPENSE, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"5602", "管理费用", ScrmAccountSubjectEntity.CATEGORY_EXPENSE, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"5603", "财务费用", ScrmAccountSubjectEntity.CATEGORY_EXPENSE, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"5711", "营业外支出", ScrmAccountSubjectEntity.CATEGORY_EXPENSE, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
            {"5801", "所得税费用", ScrmAccountSubjectEntity.CATEGORY_EXPENSE, ScrmAccountSubjectEntity.DIRECTION_DEBIT},
    };
}
