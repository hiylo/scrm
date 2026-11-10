/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmVoucherController.java
 * Date : 2026/10/25 19:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.dto.ScrmAiVoucherSuggestionDto;
import org.hiylo.scrm.dto.ScrmVoucherSaveDto;
import org.hiylo.scrm.entity.ScrmAccountOpeningBalanceEntity;
import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.hiylo.scrm.entity.ScrmVoucherAttachmentEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.entity.ScrmAccountPeriodEntity;
import org.hiylo.scrm.service.finance.voucher.ScrmAiVoucherAssistService;
import org.hiylo.scrm.service.finance.voucher.ScrmFinancialReportService;
import org.hiylo.scrm.service.finance.voucher.ScrmLedgerService;
import org.hiylo.scrm.service.finance.voucher.ScrmPeriodCloseService;
import org.hiylo.scrm.service.finance.voucher.ScrmAccountOpeningBalanceService;
import org.hiylo.scrm.service.finance.voucher.ScrmVoucherAttachmentService;
import org.hiylo.scrm.service.finance.voucher.ScrmVoucherService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 记账控制器（凭证域）。
 * <p>
 * 记账是代账公司最核心的交付物，因此这里的接口都围绕
 * 「账做对没有」设计：{@code trial-balance} 是唯一能证明账平的手段，
 * 借贷平衡校验则完全在服务端做，前端拿不到绕过口子。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/vouchers")
@RequiredArgsConstructor
public class ScrmVoucherController {

    /** 凭证服务 */
    private final ScrmVoucherService voucherService;

    /** 期末结账服务 */
    private final ScrmPeriodCloseService periodCloseService;

    /** 财务报表服务 */
    private final ScrmFinancialReportService financialReportService;

    /** 账簿服务 */
    private final ScrmLedgerService ledgerService;

    /** 原始单据服务（凭证电子附件） */
    private final ScrmVoucherAttachmentService voucherAttachmentService;

    /** 期初余额服务（接户结转数） */
    private final ScrmAccountOpeningBalanceService openingBalanceService;

    /** AI 智能凭证助手（票据识别 → 凭证草稿建议） */
    private final ScrmAiVoucherAssistService aiVoucherAssistService;

    /** 财税数据范围（会计越权校验：所有 companyId 入参都要过白名单） */
    private final org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    // ============================================================
    // 科目
    // ============================================================

    /**
     * 初始化企业标准科目表（幂等，已存在的科目不会覆盖）。
     *
     * @param companyId 公司主体 ID
     * @return 本次新增科目数
     * @throws ScrmException 主体不存在
     */
    @RequirePermission(resource = "scrm_voucher", action = "create")
    @PostMapping("/subjects/init")
    public OperationResponse<Integer> initSubjects(@RequestParam Long companyId) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(voucherService.initSubjects(companyId));
    }

    /**
     * 查询企业科目列表。
     *
     * @param companyId 公司主体 ID
     * @return 科目列表
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/subjects")
    public OperationResponse<List<ScrmAccountSubjectEntity>> listSubjects(@RequestParam Long companyId) {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(voucherService.listSubjects(companyId));
    }

    /**
     * 新增自定义科目。
     *
     * @param entity 科目信息
     * @return 创建后的科目
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_voucher", action = "create")
    @PostMapping("/subjects")
    public OperationResponse<ScrmAccountSubjectEntity> createSubject(
            @RequestBody ScrmAccountSubjectEntity entity) throws ScrmException {
        return OperationResponse.build(voucherService.createSubject(entity));
    }

    /**
     * 启用/停用科目。
     *
     * @param id     科目 ID
     * @param status 目标状态（ACTIVE/DISABLED）
     * @return 更新后的科目
     * @throws ScrmException 科目不存在
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PutMapping("/subjects/{id}/status")
    public OperationResponse<ScrmAccountSubjectEntity> updateSubjectStatus(
            @PathVariable Long id, @RequestParam String status) throws ScrmException {
        return OperationResponse.build(voucherService.updateSubjectStatus(id, status));
    }

    // ============================================================
    // 凭证
    // ============================================================

    /**
     * 新建凭证草稿。
     *
     * @param companyId 公司主体 ID
     * @param dto       凭证内容
     * @return 凭证详情（头 + 分录）
     * @throws ScrmException 借贷不平 / 科目不存在
     */
    @RequirePermission(resource = "scrm_voucher", action = "create")
    @PostMapping
    public OperationResponse<Map<String, Object>> createVoucher(
            @RequestParam Long companyId, @RequestBody ScrmVoucherSaveDto dto) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(voucherService.createVoucher(companyId, dto));
    }

    /**
     * 修改凭证草稿（整体替换分录）。
     *
     * @param id  凭证 ID
     * @param dto 新内容
     * @return 凭证详情
     * @throws ScrmException 已记账不可改 / 借贷不平
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<Map<String, Object>> updateVoucher(
            @PathVariable Long id, @RequestBody ScrmVoucherSaveDto dto) throws ScrmException {
        assertCompanyAccessible(voucherService.findCompanyIdByVoucherId(id));
        return OperationResponse.build(voucherService.updateVoucher(id, dto));
    }

    /**
     * 分页查询凭证。
     *
     * @param page        页码（默认 0）
     * @param size        每页大小（默认 20）
     * @param companyId   公司主体 ID
     * @param periodYear  年（可空）
     * @param periodMonth 月（可空）
     * @param status      状态（可空）
     * @param keyword     关键词（可空，摘要/凭证号模糊匹配）
     * @return 凭证分页结果
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping
    public OperationResponse<Page<ScrmVoucherEntity>> listVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam Long companyId,
            @RequestParam(required = false) Integer periodYear,
            @RequestParam(required = false) Integer periodMonth,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        // 显式校验：会计传他人 companyId 应 403，而不是静默返回空列表
        assertCompanyAccessible(companyId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.ASC, "periodYear", "periodMonth", "voucherNo"));
        return OperationResponse.build(
                voucherService.listVouchers(companyId, periodYear, periodMonth, status, keyword, pageable));
    }

    /**
     * 查询凭证详情（头 + 分录）。
     *
     * @param id 凭证 ID
     * @return 凭证详情
     * @throws ScrmException 凭证不存在
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<Map<String, Object>> getVoucher(@PathVariable Long id) throws ScrmException {
        // 越权防护：凭证 ID 属于谁的账套，必须回查主体再校验
        assertCompanyAccessible(voucherService.findCompanyIdByVoucherId(id));
        return OperationResponse.build(voucherService.detail(id));
    }

    /**
     * 记账（草稿 → 已记账）。
     *
     * @param id 凭证 ID
     * @return 更新后的凭证详情
     * @throws ScrmException 状态不允许 / 借贷不平
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PostMapping("/{id}/post")
    public OperationResponse<Map<String, Object>> postVoucher(@PathVariable Long id) throws ScrmException {
        assertCompanyAccessible(voucherService.findCompanyIdByVoucherId(id));
        return OperationResponse.build(voucherService.postVoucher(id));
    }

    /**
     * 作废凭证（退出余额，保留痕迹）。
     *
     * @param id     凭证 ID
     * @param reason 作废原因（可空）
     * @return 更新后的凭证详情
     * @throws ScrmException 凭证不存在 / 已作废
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PostMapping("/{id}/void")
    public OperationResponse<Map<String, Object>> voidVoucher(
            @PathVariable Long id, @RequestParam(required = false) String reason)
            throws ScrmException {
        assertCompanyAccessible(voucherService.findCompanyIdByVoucherId(id));
        return OperationResponse.build(voucherService.voidVoucher(id, reason));
    }

    /**
     * 批量记账：把选中的草稿凭证一次性记账。
     * <p>逐条校验，单张失败（已记账/已作废/已结账/借贷不平）不影响其余凭证，
     * 返回成功数与失败明细供前端提示。</p>
     *
     * @param ids 凭证 ID 列表
     * @return {successCount, failCount, failures:[{id, label, message}]}
     * @throws ScrmException 任一凭证不属于当前账号可访问的主体
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PostMapping("/batch-post")
    public OperationResponse<Map<String, Object>> batchPost(@RequestBody List<Long> ids)
            throws ScrmException {
        assertAllAccessible(ids);
        return OperationResponse.build(voucherService.batchPost(ids));
    }

    /**
     * 批量作废：把选中的凭证一次性作废（保留痕迹，退出余额）。
     *
     * @param ids    凭证 ID 列表
     * @param reason 作废原因（可空，统一写入各凭证备注）
     * @return {successCount, failCount, failures:[{id, label, message}]}
     * @throws ScrmException 任一凭证不属于当前账号可访问的主体
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PostMapping("/batch-void")
    public OperationResponse<Map<String, Object>> batchVoid(
            @RequestBody List<Long> ids, @RequestParam(required = false) String reason)
            throws ScrmException {
        assertAllAccessible(ids);
        return OperationResponse.build(voucherService.batchVoid(ids, reason));
    }

    /**
     * 删除凭证（仅草稿）。
     *
     * @param id 凭证 ID
     * @return 空响应
     * @throws ScrmException 凭证不存在 / 非草稿
     */
    @RequirePermission(resource = "scrm_voucher", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteVoucher(@PathVariable Long id) throws ScrmException {
        assertCompanyAccessible(voucherService.findCompanyIdByVoucherId(id));
        voucherService.deleteVoucher(id);
        return OperationResponse.build(null);
    }

    // ============================================================
    // 原始单据（电子附件）
    // ============================================================

    /**
     * 给凭证挂一份原始单据（发票 / 银行回单 / 合同 / 工资表）。
     * <p>
     * 文件本体进对象存储，库里只留元数据与 object_key；下载走预签名 URL 直连存储。
     * 已记账凭证也可补挂——单据是证据，事后补交是常态，不应被凭证状态挡住。
     * </p>
     *
     * @param id      凭证 ID
     * @param file    文件（multipart 字段名 file）
     * @param docType 单据类型（INVOICE/BANK_RECEIPT/CONTRACT/PAYROLL/RECEIPT/OTHER，可空）
     * @param remark  备注（可空）
     * @return 挂载后的单据记录
     * @throws ScrmException 凭证不存在 / 无权访问 / 文件为空或超限 / 存储失败
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PostMapping("/{id}/attachments")
    public OperationResponse<ScrmVoucherAttachmentEntity> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String remark) throws ScrmException {
        assertCompanyAccessible(voucherService.findCompanyIdByVoucherId(id));
        return OperationResponse.build(
                voucherAttachmentService.upload(id, file, docType, remark));
    }

    /**
     * 查某张凭证的全部原始单据。
     *
     * @param id 凭证 ID
     * @return 单据列表（上传时间倒序）
     * @throws ScrmException 凭证不存在 / 无权访问
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/{id}/attachments")
    public OperationResponse<List<ScrmVoucherAttachmentEntity>> listAttachments(@PathVariable Long id)
            throws ScrmException {
        assertCompanyAccessible(voucherService.findCompanyIdByVoucherId(id));
        return OperationResponse.build(voucherAttachmentService.list(id));
    }

    /**
     * 生成原始单据的下载预签名 URL（直连对象存储，不经应用中转）。
     *
     * @param id 单据 ID
     * @return {url} 预签名下载地址
     * @throws ScrmException 单据不存在 / 无权访问 / 生成失败
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/attachments/{id}/download-url")
    public OperationResponse<Map<String, String>> attachmentDownloadUrl(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(Map.of("url", voucherAttachmentService.presignDownload(id)));
    }

    /**
     * 删除一份原始单据（删记录 + 删存储对象）。
     *
     * @param id 单据 ID
     * @return 空响应
     * @throws ScrmException 单据不存在 / 无权访问
     */
    @RequirePermission(resource = "scrm_voucher", action = "delete")
    @DeleteMapping("/attachments/{id}")
    public OperationResponse<Void> deleteAttachment(@PathVariable Long id) throws ScrmException {
        voucherAttachmentService.delete(id);
        return OperationResponse.build(null);
    }

    // ============================================================
    // 报表
    // ============================================================

    /**
     * 试算平衡表（期初 + 本期发生 → 期末，含 balanced 判定）。
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @return 试算平衡表
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/trial-balance")
    public OperationResponse<Map<String, Object>> trialBalance(
            @RequestParam Long companyId,
            @RequestParam int periodYear,
            @RequestParam int periodMonth) {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(
                voucherService.trialBalance(companyId, periodYear, periodMonth));
    }

    // ============================================================
    // 期末结账 / 反结账
    // ============================================================

    /**
     * 结账：生成结转损益凭证并封账。
     *
     * <p>
     * 未录入期初余额的企业，需显式传 {@code confirmNoOpening=true} 表示「本户从零起账」，
     * 否则拒绝结账——避免中途接手的客户忘了录期初就把利润算错并封盘。
     * </p>
     *
     * @param companyId        公司主体 ID
     * @param periodYear       年
     * @param periodMonth      月
     * @param confirmNoOpening 是否确认本户无期初（从零起账），默认 false
     * @return 结账结果（结转凭证、结转利润）
     * @throws ScrmException 已结账 / 存在草稿 / 未录期初且未确认
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PostMapping("/close-period")
    public OperationResponse<Map<String, Object>> closePeriod(
            @RequestParam Long companyId,
            @RequestParam int periodYear,
            @RequestParam int periodMonth,
            @RequestParam(defaultValue = "false") boolean confirmNoOpening) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(
                periodCloseService.closePeriod(companyId, periodYear, periodMonth, confirmNoOpening));
    }

    /**
     * 反结账：作废结转凭证并恢复未结账。
     *
     * @param companyId 公司主体 ID
     * @param periodYear 年
     * @param periodMonth 月
     * @return 结果
     * @throws ScrmException 期间未结账
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PostMapping("/reopen-period")
    public OperationResponse<Map<String, Object>> reopenPeriod(
            @RequestParam Long companyId,
            @RequestParam int periodYear,
            @RequestParam int periodMonth) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(
                periodCloseService.reopenPeriod(companyId, periodYear, periodMonth));
    }

    /**
     * 期间状态清单（哪几期已结账）。
     *
     * @param companyId 公司主体 ID
     * @return 期间列表
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/periods")
    public OperationResponse<List<ScrmAccountPeriodEntity>> listPeriods(@RequestParam Long companyId) {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(periodCloseService.listPeriods(companyId));
    }

    // ============================================================
    // 财务报表
    // ============================================================

    /**
     * 资产负债表（含会计恒等式校验）。
     *
     * @param companyId 公司主体 ID
     * @param periodYear 年
     * @param periodMonth 月
     * @return 资产负债表
     * @throws ScrmException 主体不存在
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/balance-sheet")
    public OperationResponse<Map<String, Object>> balanceSheet(
            @RequestParam Long companyId,
            @RequestParam int periodYear,
            @RequestParam int periodMonth) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(
                financialReportService.balanceSheet(companyId, periodYear, periodMonth));
    }

    /**
     * 利润表（本期 + 本年累计）。
     *
     * @param companyId 公司主体 ID
     * @param periodYear 年
     * @param periodMonth 月
     * @return 利润表
     * @throws ScrmException 主体不存在
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/income-statement")
    public OperationResponse<Map<String, Object>> incomeStatement(
            @RequestParam Long companyId,
            @RequestParam int periodYear,
            @RequestParam int periodMonth) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(
                financialReportService.incomeStatement(companyId, periodYear, periodMonth));
    }

    // ============================================================
    // 账簿
    // ============================================================

    /**
     * 总账（一级科目汇总）。
     *
     * @param companyId 公司主体 ID
     * @param periodYear 年
     * @param periodMonth 月
     * @return 总账
     * @throws ScrmException 主体不存在
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/general-ledger")
    public OperationResponse<Map<String, Object>> generalLedger(
            @RequestParam Long companyId,
            @RequestParam int periodYear,
            @RequestParam int periodMonth) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(
                ledgerService.generalLedger(companyId, periodYear, periodMonth));
    }

    /**
     * 明细账（单科目逐笔 + 逐笔余额）。
     *
     * @param companyId 公司主体 ID
     * @param subjectCode 科目编码
     * @param periodYear 年
     * @param periodMonth 月
     * @return 明细账
     * @throws ScrmException 主体不存在 / 科目为空
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/detail-ledger")
    public OperationResponse<Map<String, Object>> detailLedger(
            @RequestParam Long companyId,
            @RequestParam String subjectCode,
            @RequestParam int periodYear,
            @RequestParam int periodMonth) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(
                ledgerService.detailLedger(companyId, subjectCode, periodYear, periodMonth));
    }

    /**
     * 序时账（按期顺序列全部分录）。
     *
     * @param companyId 公司主体 ID
     * @param periodYear 年
     * @param periodMonth 月
     * @return 序时账
     * @throws ScrmException 主体不存在
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/journal")
    public OperationResponse<Map<String, Object>> journal(
            @RequestParam Long companyId,
            @RequestParam int periodYear,
            @RequestParam int periodMonth) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(
                ledgerService.journal(companyId, periodYear, periodMonth));
    }

    /**
     * 期间汇总（凭证数、借贷合计、各状态计数）。
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @return 汇总
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/period-summary")
    public OperationResponse<Map<String, Object>> periodSummary(
            @RequestParam Long companyId,
            @RequestParam int periodYear,
            @RequestParam int periodMonth) {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(
                voucherService.periodSummary(companyId, periodYear, periodMonth));
    }

    // ============================================================
    // 期初余额（接户结转数）
    // ============================================================

    /**
     * 查询某户期初余额录入视图（科目全量清单 + 合计 + 平衡判定）。
     * <p>
     * 中途接手客户时，上家账套的余额就是本系统的起点。没有它，试算平衡的期初
     * 与资产负债表年初余额永远是 0，账接不上、年报不能用。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @return {openingPeriod, items, totalDebit, totalCredit, balanced, configured}
     * @throws ScrmException 主体不存在 / 无权访问
     */
    /**
     * 期初录入状态总览：按数据范围列出各主体的录入情况。
     * <p>
     * 供代账主管一眼找出「哪些户还没录期初」。结账虽会拦未录期初，但那是事后才知道；
     * 有了这个总览才能在结账前提前补齐。只提示「未录期初 <b>且</b> 已建科目」的主体——
     * 没有科目根本没法录期初，提示了只是噪音。
     * </p>
     *
     * @return {items:[{companyId, companyName, configured, openingPeriod, totalDebit,
     *         totalCredit, balanced, subjectCount, suggestOpening, ...}], pendingCount, totalCount}
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/opening-balance/summary")
    public OperationResponse<Map<String, Object>> openingBalanceSummary() {
        return OperationResponse.build(openingBalanceService.summary());
    }

    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/opening-balance")
    public OperationResponse<Map<String, Object>> openingBalance(@RequestParam Long companyId)
            throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(openingBalanceService.view(companyId));
    }

    /**
     * 重设某户期初余额（整户替换）。
     * <p>
     * 强制借贷平衡，并拒绝「启用期之前已有已记账凭证」的户——否则期初与历史
     * 会重复计算，余额凭空变大。保存后试算平衡/总账/明细账/资产负债表/结转损益
     * 会自动带上这批期初，不需要额外操作。
     * </p>
     *
     * @param companyId     公司主体 ID
     * @param openingPeriod 启用期间（形如 202601）
     * @param items         科目余额行 [{subjectCode, debitAmount, creditAmount, remark}]
     * @return 保存后的视图
     * @throws ScrmException 金额非法 / 借贷不平 / 科目不存在 / 启用期前有凭证
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PutMapping("/opening-balance")
    public OperationResponse<Map<String, Object>> saveOpeningBalance(
            @RequestParam Long companyId,
            @RequestParam Integer openingPeriod,
            @RequestBody List<ScrmAccountOpeningBalanceEntity> items) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(
                openingBalanceService.save(companyId, openingPeriod, items));
    }

    /**
     * 清空某户期初余额（回到「全部按本系统凭证累计」的状态）。
     *
     * @param companyId 公司主体 ID
     * @return 删除行数
     * @throws ScrmException 无权访问
     */
    @RequirePermission(resource = "scrm_voucher", action = "delete")
    @DeleteMapping("/opening-balance")
    public OperationResponse<Map<String, Object>> clearOpeningBalance(@RequestParam Long companyId)
            throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(Map.of("deleted", openingBalanceService.clear(companyId)));
    }

    /**
     * AI 智能凭证：上传票据图片（发票/银行回单），返回识别出的凭证草稿建议。
     * <p>
     * 建议含票据字段 + 借贷分录（科目已按企业科目表过滤、借贷已校验平衡），
     * 由会计人工确认后调 {@link #createVoucher(Long, ScrmVoucherSaveDto)} 落草稿，
     * 绝不自动入账。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @param file      票据图片
     * @return 凭证草稿建议
     * @throws ScrmException 文件非法 / 未配置多模态 Provider / 识别失败
     */
    @RequirePermission(resource = "scrm_voucher", action = "create")
    @PostMapping("/ai/recognize-entry")
    public OperationResponse<ScrmAiVoucherSuggestionDto> aiRecognizeEntry(
            @RequestParam Long companyId, @RequestParam("file") MultipartFile file)
            throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(aiVoucherAssistService.recognizeEntry(companyId, file));
    }

    /**
     * 校验当前用户是否有权访问指定主体。
     * <p>
     * 报表、账簿、结账等端点都以 {@code companyId} 为入参，若不校验归属，
     * 会计改一个 ID 就能拉到别家的资产负债表。这里的校验是这些端点的
     * <b>唯一</b>防线，必须逐个调用，不能只依赖前端隐藏入口。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @throws ScrmException 无权访问
     */
    private void assertCompanyAccessible(Long companyId) throws ScrmException {
        if (!financeDataScope.canAccessCompany(companyId)) {
            throw ScrmException.forbidden("无权访问该主体: " + companyId);
        }
    }

    /**
     * 批量端点的主体归属校验。
     * <p>
     * 批量接口没有 {@code companyId} 入参，只能逐张反查凭证归属的主体；
     * 只要有任意一张不在当前账号的数据范围内就整体拒绝，避免"部分越权写入"。
     * </p>
     *
     * @param ids 凭证 ID 列表
     * @throws ScrmException 凭证不存在 / 无权访问
     */
    private void assertAllAccessible(List<Long> ids) throws ScrmException {
        if (ids == null || ids.isEmpty()) {
            throw ScrmException.badRequest("请至少选择一张凭证");
        }
        for (Long id : ids) {
            assertCompanyAccessible(voucherService.findCompanyIdByVoucherId(id));
        }
    }
}
