/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmAccountOpeningBalanceService.java
 * Date : 2026/11/10 17:45:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.entity.ScrmAccountOpeningBalanceEntity;
import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountOpeningBalanceRepository;
import org.hiylo.scrm.repository.ScrmAccountSubjectRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmVoucherRepository;
import org.hiylo.scrm.service.finance.FinanceDataScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 期初余额服务（V29）。
 * <p>
 * <b>解决的问题</b>：代账公司中途接手客户是常态，但客户的账在此之前是别家做的。
 * 本系统所有期初数都是「按已记账凭证累计」倒推出来的，新客户没有任何历史凭证，
 * 于是试算平衡的期初全 0、资产负债表年初余额全 0——账接不上、年报不能用。
 * 这不是锦上添花，是接户第一天的硬阻塞。
 * </p>
 * <p>
 * <b>核心机制</b>：把期初余额表达为「启用期间之前的虚拟分录」。
 * 本服务提供 {@link #mergeInto(List, Integer)}，把期初余额并进任何一份
 * 分录聚合结果（与 {@code sumBeforePeriod}/{@code sumUpTo} 返回结构一致），
 * 因此试算平衡 / 总账 / 明细账 / 资产负债表 / 结转损益都<b>只需在取数处调一次合并</b>，
 * 不必各自实现期初逻辑。
 * </p>
 * <p>
 * <b>两条不能错的规则</b>：
 * </p>
 * <ol>
 *   <li><b>只进「期初/累计」口径，绝不进「本期发生额」</b>。期初余额是上家账的
 *       结转数，不是本月做的业务；混进本期发生额会让本期借贷虚增、利润表失真。</li>
 *   <li><b>查询期早于启用期时不生效</b>。会计翻看接户之前的历史月份，看到的应当是
 *       当年真实发生额（本系统里的凭证），而不是把未来的期初倒灌回去。</li>
 * </ol>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmAccountOpeningBalanceService {

    /** 金额精度 */
    private static final int MONEY_SCALE = 2;

    /** 期初余额仓储 */
    private final ScrmAccountOpeningBalanceRepository openingRepository;

    /** 科目仓储（校验科目存在、取名称快照） */
    private final ScrmAccountSubjectRepository subjectRepository;

    /** 主体仓储（校验主体存在） */
    private final ScrmCompanyRepository companyRepository;

    /** 凭证仓储（校验启用期之前没有任何有效凭证，避免期初与历史重复计算） */
    private final ScrmVoucherRepository voucherRepository;

    /** 财税数据范围（会计只能设置自己负责主体的期初） */
    private final FinanceDataScope financeDataScope;

    // ============================================================
    // 读
    // ============================================================

    /**
     * 查询某户的期初余额录入视图。
     * <p>
     * 返回科目维度全量清单（已设期初的带金额，未设的为 0），会计打开页面即可
     * 直接照着上家报表逐行录入，不需要先在空表里逐个新增科目行。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @return {openingPeriod, items:[...], totalDebit, totalCredit, balanced, closed}
     * @throws ScrmException 主体不存在 / 无权访问
     */
    @Transactional(readOnly = true)
    public Map<String, Object> view(Long companyId) throws ScrmException {
        assertCompanyAccessible(companyId);
        companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));

        List<ScrmAccountOpeningBalanceEntity> saved =
                openingRepository.findByCompanyIdOrderBySubjectCodeAsc(companyId);
        Map<String, ScrmAccountOpeningBalanceEntity> savedMap = new LinkedHashMap<>();
        Integer openingPeriod = null;
        for (ScrmAccountOpeningBalanceEntity entity : saved) {
            savedMap.put(entity.getSubjectCode(), entity);
            if (openingPeriod == null) {
                openingPeriod = entity.getOpeningPeriod();
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (ScrmAccountSubjectEntity subject : subjectRepository
                .findByCompanyIdOrderBySubjectCodeAsc(companyId)) {
            // 停用科目若已有期初余额仍要展示，否则会计看不到这笔钱却还在参与试算平衡
            ScrmAccountOpeningBalanceEntity hit = savedMap.get(subject.getSubjectCode());
            boolean active = ScrmAccountSubjectEntity.STATUS_ACTIVE.equals(subject.getStatus());
            if (!active && hit == null) {
                continue;
            }
            BigDecimal debit = hit == null ? BigDecimal.ZERO : scale(hit.getDebitAmount());
            BigDecimal credit = hit == null ? BigDecimal.ZERO : scale(hit.getCreditAmount());
            if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
                // 未设期初且金额 0 的科目仍列出（会计要往下填），但不计入合计
                items.add(item(subject, openingPeriod, debit, credit, hit, active));
                continue;
            }
            items.add(item(subject, openingPeriod, debit, credit, hit, active));
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", String.valueOf(companyId));
        result.put("openingPeriod", openingPeriod);
        result.put("items", items);
        result.put("totalDebit", scale(totalDebit));
        result.put("totalCredit", scale(totalCredit));
        result.put("balanced", totalDebit.compareTo(totalCredit) == 0);
        result.put("configured", !saved.isEmpty());
        return result;
    }

    /**
     * 取某户期初余额的「启用期间」（未设置时返回 null）。
     *
     * @param companyId 公司主体 ID
     * @return 启用期间（形如 202601），未设置返回 null
     */
    @Transactional(readOnly = true)
    public Integer openingPeriod(Long companyId) {
        List<ScrmAccountOpeningBalanceEntity> saved =
                openingRepository.findByCompanyIdOrderBySubjectCodeAsc(companyId);
        return saved.isEmpty() ? null : saved.get(0).getOpeningPeriod();
    }

    // ============================================================
    // 写
    // ============================================================

    /**
     * 整体替换某户的期初余额（语义是「重设」而不是「增量修改」）。
     * <p>
     * <b>强制借贷平衡</b>：不平的期初等于账从一开始就是错的，而且这个错误会被
     * 后续每一张报表继承，事后极难发现。所以宁可在录入时拦住，也不允许存进去。
     * </p>
     * <p>
     * <b>拒绝覆盖启用期之前的历史</b>：若启用期之前已存在已记账凭证，说明这笔账
     * 在本系统里已经有了发生额，再叠加期初会重复计算（余额凭空变大）。
     * </p>
     *
     * @param companyId     公司主体 ID
     * @param openingPeriod 启用期间（形如 202601）
     * @param items         科目余额行（至少一行；金额为 0 的行会被忽略）
     * @return 保存后的视图（同 {@link #view(Long)}）
     * @throws ScrmException 金额非法 / 借贷不平 / 科目不存在 / 启用期前有凭证
     */
    @Transactional
    public Map<String, Object> save(Long companyId, Integer openingPeriod,
                                    List<ScrmAccountOpeningBalanceEntity> items) throws ScrmException {
        assertCompanyAccessible(companyId);
        companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));
        if (openingPeriod == null) {
            throw ScrmException.badRequest("请选择账套启用期间");
        }
        int month = openingPeriod % 100;
        int year = openingPeriod / 100;
        if (month < 1 || month > 12 || year < 1900 || year > 2999) {
            throw ScrmException.badRequest("启用期间不合法: " + openingPeriod + "（应为 YYYYMM）");
        }

        // 启用期之前不允许存在任何有效凭证（草稿也算），否则期初 + 历史会重复计算。
        // 只看「已记账」是不够的：草稿日后一旦记账，余额就会凭空变大，
        // 而这种错误要等出报表时才会暴露，届时已很难定位是哪一笔重复。
        long before = voucherRepository.countEffectiveBeforePeriod(companyId, openingPeriod);
        if (before > 0) {
            throw ScrmException.conflict("启用期间 " + openingPeriod
                    + " 之前已存在 " + before + " 张未作废凭证（含草稿），不能再设置期初余额（会重复计算）。"
                    + "请改用更早的启用期间，或先清理该期间之前的凭证");
        }

        List<ScrmAccountOpeningBalanceEntity> rows = new ArrayList<>();
        Map<String, ScrmAccountSubjectEntity> subjectMap = new LinkedHashMap<>();
        for (ScrmAccountSubjectEntity subject : subjectRepository
                .findByCompanyIdOrderBySubjectCodeAsc(companyId)) {
            subjectMap.put(subject.getSubjectCode(), subject);
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        Set<String> seen = new LinkedHashSet<>();
        if (items != null) {
            int lineNo = 0;
            for (ScrmAccountOpeningBalanceEntity item : items) {
                lineNo++;
                if (item == null) {
                    continue;
                }
                String code = item.getSubjectCode() == null ? "" : item.getSubjectCode().trim();
                if (code.isEmpty()) {
                    throw ScrmException.badRequest("第 " + lineNo + " 行未选择科目");
                }
                if (!seen.add(code)) {
                    throw ScrmException.badRequest("科目 " + code + " 重复出现，请合并为一行");
                }
                ScrmAccountSubjectEntity subject = subjectMap.get(code);
                if (subject == null) {
                    throw ScrmException.badRequest("第 " + lineNo + " 行科目不存在: " + code);
                }
                BigDecimal debit = scale(item.getDebitAmount());
                BigDecimal credit = scale(item.getCreditAmount());
                if (debit.compareTo(BigDecimal.ZERO) < 0 || credit.compareTo(BigDecimal.ZERO) < 0) {
                    throw ScrmException.badRequest("第 " + lineNo + " 行余额不能为负数（请用借/贷两列表达方向）");
                }
                if (debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) > 0) {
                    throw ScrmException.badRequest("第 " + lineNo + " 行借贷不能同时非零");
                }
                if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
                    // 0 行不入库：既省空间，也避免"设了个 0"被误认为已处理
                    continue;
                }
                ScrmAccountOpeningBalanceEntity entity = new ScrmAccountOpeningBalanceEntity();
                entity.setCompanyId(companyId);
                entity.setOpeningPeriod(openingPeriod);
                entity.setSubjectCode(code);
                entity.setSubjectName(subject.getSubjectName());
                entity.setDebitAmount(debit);
                entity.setCreditAmount(credit);
                entity.setRemark(item.getRemark());
                entity.setCreatedBy(operator());
                entity.setUpdatedBy(operator());
                rows.add(entity);
                totalDebit = totalDebit.add(debit);
                totalCredit = totalCredit.add(credit);
            }
        }

        if (rows.isEmpty()) {
            throw ScrmException.badRequest("请至少录入一行有余额的科目");
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            BigDecimal diff = totalDebit.subtract(totalCredit);
            throw ScrmException.badRequest("期初借贷不平衡：借方合计 " + totalDebit
                    + "，贷方合计 " + totalCredit + "，差额 " + diff
                    + "。期初不平会导致后续所有报表都不平，请先对上上家余额表");
        }

        // 整户替换：先删后建，语义是"重设期初"，避免残留旧科目行
        openingRepository.deleteByCompanyId(companyId);
        openingRepository.saveAll(rows);
        log.info("[期初余额] 保存成功: companyId={}, openingPeriod={}, 科目 {} 个, 借={}, 贷={}",
                companyId, openingPeriod, rows.size(), totalDebit, totalCredit);
        return view(companyId);
    }

    /**
     * 清空某户期初余额（回到「全部按本系统凭证累计」的状态）。
     *
     * @param companyId 公司主体 ID
     * @return 删除行数
     * @throws ScrmException 无权访问
     */
    @Transactional
    public int clear(Long companyId) throws ScrmException {
        assertCompanyAccessible(companyId);
        int deleted = openingRepository.deleteByCompanyId(companyId);
        log.info("[期初余额] 清空: companyId={}, 删除 {} 行", companyId, deleted);
        return deleted;
    }

    // ============================================================
    // 聚合合并（供试算平衡/总账/明细账/资产负债表/结转损益调用）
    // ============================================================

    /**
     * 把期初余额并进一份分录聚合结果。
     * <p>
     * 输入输出结构一致：每行 {@code [subjectCode, subjectName, sumDebit, sumCredit]}，
     * 与 {@code ScrmVoucherEntryRepository} 的原生聚合查询返回一致，
     * 因此调用方不需要改任何下游逻辑。
     * </p>
     *
     * @param rows   原聚合结果（可为空列表，但不可为 null）
     * @param yyyymm 查询期间（形如 202610）
     * @return 合并后的聚合结果（新列表，不修改入参）
     */
    @Transactional(readOnly = true)
    public List<Object[]> mergeInto(List<Object[]> rows, Integer yyyymm) {
        return mergeInto(rows, yyyymm, null);
    }

    /**
     * 把指定主体的期初余额并进一份分录聚合结果。
     * <p>
     * 传入 {@code companyId} 时只读该户的期初；传 null 时按 {@code rows} 原样返回
     * （用于没有主体上下文、或不愿承担一次查询开销的场景）。
     * </p>
     *
     * @param rows      原聚合结果
     * @param yyyymm    查询期间（形如 202610）
     * @param companyId 公司主体 ID（可空；为空则不合并）
     * @return 合并后的聚合结果
     */
    @Transactional(readOnly = true)
    public List<Object[]> mergeInto(List<Object[]> rows, Integer yyyymm, Long companyId) {
        List<Object[]> base = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        if (companyId == null || yyyymm == null) {
            return base;
        }
        List<ScrmAccountOpeningBalanceEntity> opening =
                openingRepository.findByCompanyIdOrderBySubjectCodeAsc(companyId);
        if (opening.isEmpty()) {
            return base;
        }
        Integer openingPeriod = opening.get(0).getOpeningPeriod();
        if (openingPeriod == null || yyyymm < openingPeriod) {
            // 查询期早于启用期：不合并，让会计看到接户之前的真实发生额
            return base;
        }

        // 用 LinkedHashMap 按科目合并，保持原有行的顺序语义
        Map<String, Object[]> merged = new LinkedHashMap<>();
        for (Object[] row : base) {
            merged.put(String.valueOf(row[0]), row);
        }
        for (ScrmAccountOpeningBalanceEntity entity : opening) {
            String code = entity.getSubjectCode();
            BigDecimal debit = scale(entity.getDebitAmount());
            BigDecimal credit = scale(entity.getCreditAmount());
            if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            Object[] existing = merged.get(code);
            if (existing == null) {
                merged.put(code, new Object[]{code, entity.getSubjectName(), debit, credit});
            } else {
                BigDecimal sumD = toDecimal(existing[2]).add(debit);
                BigDecimal sumC = toDecimal(existing[3]).add(credit);
                merged.put(code, new Object[]{code, existing[1], sumD, sumC});
            }
        }
        List<Object[]> result = new ArrayList<>(merged.values());
        log.debug("[期初余额] 已合并: companyId={}, yyyymm={}, 期初 {} 行 → 聚合 {} 行",
                companyId, yyyymm, opening.size(), result.size());
        return result;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /** 组装单行视图（含是否已设、科目是否停用）。 */
    private Map<String, Object> item(ScrmAccountSubjectEntity subject, Integer openingPeriod,
                                     BigDecimal debit, BigDecimal credit,
                                     ScrmAccountOpeningBalanceEntity saved, boolean active) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("subjectCode", subject.getSubjectCode());
        row.put("subjectName", subject.getSubjectName());
        row.put("category", subject.getCategory());
        row.put("direction", subject.getDirection());
        row.put("debitAmount", debit);
        row.put("creditAmount", credit);
        row.put("configured", saved != null
                && (debit.compareTo(BigDecimal.ZERO) != 0 || credit.compareTo(BigDecimal.ZERO) != 0));
        row.put("active", active);
        row.put("openingPeriod", openingPeriod);
        row.put("remark", saved == null ? null : saved.getRemark());
        return row;
    }

    /**
     * 校验当前用户有权访问该主体。
     *
     * @param companyId 公司主体 ID
     * @throws ScrmException 无权访问
     */
    private void assertCompanyAccessible(Long companyId) throws ScrmException {
        if (!financeDataScope.canAccessCompany(companyId)) {
            throw ScrmException.forbidden("无权访问该主体: " + companyId);
        }
    }

    /** 金额归一到两位小数。 */
    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Object → BigDecimal（原生聚合返回的可能是 BigDecimal 或 Double）。 */
    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value.toString()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** 当前操作人。 */
    private String operator() {
        String userId = UserContext.getUserId();
        return userId != null && !userId.isBlank() ? userId : "SYSTEM";
    }
}
