/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationController.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.dto.ScrmTaxDeclarationProfileDto;
import org.hiylo.scrm.dto.ScrmTaxDeclarationRecordDto;
import org.hiylo.scrm.dto.ScrmTaxDeclarationSheetDto;
import org.hiylo.scrm.dto.ScrmTaxRiskProfileDto;
import org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.finance.FinanceDataScope;
import org.hiylo.scrm.service.finance.tax.ScrmTaxDeclarationService;
import org.hiylo.scrm.service.finance.tax.ScrmTaxDeclarationSheetService;
import org.hiylo.scrm.service.finance.tax.ScrmTaxRiskProfileService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报税日历控制器（模块 A）。
 * <p>
 * 申报档案管理（客户×税种×周期 CRUD）+ 申报记录状态机（申报完成/缴款完成/补报/
 * 归档/取消）+ 日历视图聚合查询（按客户时间线 / 月视图）。端点一律
 * {@code @RequirePermission} 声明资源与动作元数据（RBAC 由 gateway 统一鉴权）。
 * 「申报完成」「缴款完成」按钮仅会计角色可操作（权限元数据 action=declare,
 * 由 gateway 在 RBAC 角色上做会计角色守卫）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/tax-calendar")
@RequiredArgsConstructor
public class ScrmTaxDeclarationController {

    /** 报税日历核心服务 */
    private final ScrmTaxDeclarationService taxDeclarationService;

    /** 申报表生成 + CSV 导出服务（内部闭环, 不依赖电子税局） */
    private final ScrmTaxDeclarationSheetService sheetService;

    /** 财税数据范围（会计越权校验：companyId 入参必须过白名单） */
    private final FinanceDataScope financeDataScope;

    /** 税负风险画像服务（只读聚合, 不改任何数据） */
    private final ScrmTaxRiskProfileService riskProfileService;

    // ============================================================
    // 申报档案管理
    // ============================================================

    /**
     * 创建申报档案。
     *
     * @param dto 档案参数
     * @return 创建后的档案
     * @throws ScrmException 参数非法 / 档案重复
     */
    @RequirePermission(resource = "scrm_tax_profile", action = "create")
    @PostMapping("/profiles")
    public OperationResponse<ScrmTaxDeclarationProfileEntity> createProfile(
            @Valid @RequestBody ScrmTaxDeclarationProfileDto dto) throws ScrmException {
        return OperationResponse.build(taxDeclarationService.createProfile(dto));
    }

    /**
     * 更新申报档案。
     *
     * @param id  档案 ID
     * @param dto 更新参数
     * @return 更新后的档案
     * @throws ScrmException 档案不存在
     */
    @RequirePermission(resource = "scrm_tax_profile", action = "update")
    @PutMapping("/profiles/{id}")
    public OperationResponse<ScrmTaxDeclarationProfileEntity> updateProfile(
            @PathVariable Long id, @RequestBody ScrmTaxDeclarationProfileDto dto) throws ScrmException {
        return OperationResponse.build(taxDeclarationService.updateProfile(id, dto));
    }

    /**
     * 删除申报档案（历史记录保留可查）。
     *
     * @param id 档案 ID
     * @return 空响应
     * @throws ScrmException 档案不存在
     */
    @RequirePermission(resource = "scrm_tax_profile", action = "delete")
    @DeleteMapping("/profiles/{id}")
    public OperationResponse<Void> deleteProfile(@PathVariable Long id) throws ScrmException {
        taxDeclarationService.deleteProfile(id);
        return OperationResponse.build();
    }

    /**
     * 查询申报档案详情。
     *
     * @param id 档案 ID
     * @return 档案实体
     * @throws ScrmException 档案不存在
     */
    @RequirePermission(resource = "scrm_tax_profile", action = "read")
    @GetMapping("/profiles/{id}")
    public OperationResponse<ScrmTaxDeclarationProfileEntity> getProfile(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(taxDeclarationService.getProfile(id));
    }

    /**
     * 分页查询申报档案。
     *
     * @param page       页码（默认 0）
     * @param size       每页大小（默认 20）
     * @param customerId 客户过滤（可空）
     * @param companyId     公司主体过滤（可空；代账场景主查询维度）
     * @param accountantId  负责会计过滤（可空；支撑「我的客户」视图）
     * @param taxType    税种过滤（可空）
     * @param status     状态过滤（可空）
     * @return 档案分页结果
     */
    @RequirePermission(resource = "scrm_tax_profile", action = "read")
    @GetMapping("/profiles")
    public OperationResponse<Page<ScrmTaxDeclarationProfileEntity>> listProfiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long accountantId,
            @RequestParam(required = false) String taxType,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(
                taxDeclarationService.listProfiles(customerId, companyId, accountantId,
                        taxType, status, pageable));
    }

    // ============================================================
    // 申报记录状态机
    // ============================================================

    /**
     * 手动生成当期申报记录（调度补充）。
     * <p>
     * 常规生成由 {@code FinanceTaxScheduler} 每日 01:00 执行，但新建档案后若不在当日
     * 触发，会计在页面上要等到次日才能看到记录。此端点让会计在完成建档后可立即生成，
     * 逻辑与调度完全一致（同一幂等键：档案 × 所属期），重复调用不会产生重复记录。
     * </p>
     *
     * @return 本次生成的记录数
     */
    @RequirePermission(resource = "scrm_tax_record", action = "create")
    @PostMapping("/records/generate")
    public OperationResponse<Integer> generateRecords() {
        return OperationResponse.build(taxDeclarationService.generateNextRecords());
    }

    /**
     * 申报完成（会计动作, PENDING/OVERDUE → FILED）。
     *
     * @param id         记录 ID
     * @param taxAmount  申报税额（可空）
     * @param receiptNo  完税凭证号（可空）
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
     */
    @RequirePermission(resource = "scrm_tax_record", action = "declare")
    @PostMapping("/records/{id}/filed")
    public OperationResponse<ScrmTaxDeclarationRecordEntity> markFiled(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal taxAmount,
            @RequestParam(required = false) String receiptNo) throws ScrmException {
        return OperationResponse.build(taxDeclarationService.markFiled(id, taxAmount, receiptNo, null));
    }

    /**
     * 批量申报完成（征期收尾高频动作）。
     * <p>
     * 逐条复用单条申报的状态校验；某条失败（状态不允许/记录不存在）不影响其它条，
     * 返回成功/失败明细供会计核对。只批量申报，不批量缴款（缴款每条实缴金额不同）。
     * </p>
     *
     * @param ids 记录 ID 列表
     * @return {successCount, failCount, failures:[{id, message}]}
     */
    @RequirePermission(resource = "scrm_tax_record", action = "declare")
    @PostMapping("/records/batch-filed")
    public OperationResponse<Map<String, Object>> batchMarkFiled(@RequestBody List<Long> ids) {
        return OperationResponse.build(taxDeclarationService.batchMarkFiled(ids));
    }

    /**
     * 缴款完成（会计动作, FILED/OVERDUE → PAID）。
     *
     * <p><b>paidAmount 必填</b>：进入 PAID 必须带实缴金额。缺失（null）与零（0）语义
     * 不同——零申报 / 减免请显式传 {@code paidAmount=0}；完全不传会被拒绝，避免落出
     * 「已缴款但金额为 null」的记录（对账按 sum(paid_amount) 统计时会静默漏掉）。</p>
     *
     * @param id         记录 ID
     * @param paidAmount 实缴税额（必填，零税额传 0）
     * @param receiptNo  完税凭证号（可空）
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许 / 未提供实缴税额
     */
    @RequirePermission(resource = "scrm_tax_record", action = "declare")
    @PostMapping("/records/{id}/paid")
    public OperationResponse<ScrmTaxDeclarationRecordEntity> markPaid(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal paidAmount,
            @RequestParam(required = false) String receiptNo) throws ScrmException {
        return OperationResponse.build(taxDeclarationService.markPaid(id, paidAmount, receiptNo));
    }

    /**
     * 补报完成（会计动作, OVERDUE → PAID 快捷）。
     *
     * <p><b>paidAmount 必填</b>，理由同 {@link #markPaid}：补报直接进 PAID，
     * 零税额须显式传 {@code paidAmount=0}。{@code taxAmount} 可空，传了则校验非负。</p>
     *
     * @param id         记录 ID
     * @param taxAmount  申报税额（可空）
     * @param paidAmount 实缴税额（必填，零税额传 0）
     * @param receiptNo  完税凭证号（可空）
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许 / 未提供实缴税额
     */
    @RequirePermission(resource = "scrm_tax_record", action = "declare")
    @PostMapping("/records/{id}/backfill")
    public OperationResponse<ScrmTaxDeclarationRecordEntity> markBackFilledPaid(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal taxAmount,
            @RequestParam(required = false) BigDecimal paidAmount,
            @RequestParam(required = false) String receiptNo) throws ScrmException {
        return OperationResponse.build(
                taxDeclarationService.markBackFilledPaid(id, taxAmount, paidAmount, receiptNo));
    }

    /**
     * 期后归档（PAID → ARCHIVED）。
     *
     * @param id 记录 ID
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
     */
    @RequirePermission(resource = "scrm_tax_record", action = "update")
    @PostMapping("/records/{id}/archive")
    public OperationResponse<ScrmTaxDeclarationRecordEntity> archive(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(taxDeclarationService.archive(id));
    }

    /**
     * 取消记录（减免/政策豁免 → CANCELED）。
     *
     * @param id     记录 ID
     * @param remark 取消原因
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
     */
    @RequirePermission(resource = "scrm_tax_record", action = "update")
    @PostMapping("/records/{id}/cancel")
    public OperationResponse<ScrmTaxDeclarationRecordEntity> cancel(
            @PathVariable Long id, @RequestParam(required = false) String remark) throws ScrmException {
        return OperationResponse.build(taxDeclarationService.cancel(id, remark));
    }

    // ============================================================
    // 日历视图查询
    // ============================================================

    /**
     * 按档案查询申报记录（会计工作台时间线）。
     *
     * @param profileId 档案 ID
     * @return 记录列表（按所属期升序）
     */
    @RequirePermission(resource = "scrm_tax_record", action = "read")
    @GetMapping("/profiles/{profileId}/records")
    public OperationResponse<List<ScrmTaxDeclarationRecordEntity>> listRecordsByProfile(
            @PathVariable Long profileId) {
        return OperationResponse.build(taxDeclarationService.listRecordsByProfile(profileId));
    }

    /**
     * 按客户查询申报记录（客户时间线钻取）。
     *
     * @param customerId 客户 ID
     * @return 记录列表（按所属期升序）
     */
    @RequirePermission(resource = "scrm_tax_record", action = "read")
    @GetMapping("/customers/{customerId}/records")
    public OperationResponse<List<ScrmTaxDeclarationRecordEntity>> listRecordsByCustomer(
            @PathVariable Long customerId) {
        return OperationResponse.build(taxDeclarationService.listRecordsByCustomer(customerId));
    }

    /**
     * 按公司主体查询申报记录（公司 360 / 代账工作台钻取）。
     *
     * @param companyId 公司主体 ID
     * @return 申报记录列表（按所属期升序）
     */
    @RequirePermission(resource = "scrm_tax_record", action = "read")
    @GetMapping("/companies/{companyId}/records")
    public OperationResponse<List<ScrmTaxDeclarationRecordEntity>> listRecordsByCompany(
            @PathVariable Long companyId) {
        return OperationResponse.build(taxDeclarationService.listRecordsByCompany(companyId));
    }

    /**
     * 报税日历月视图（聚合查询, 非逾期按税种着色、逾期红色加粗）。
     *
     * @param year  年份
     * @param month 月份（1-12）
     * @return 记录列表（按截止日升序）
     */
    @RequirePermission(resource = "scrm_tax_record", action = "read")
    @GetMapping("/records/month")
    public OperationResponse<List<ScrmTaxDeclarationRecordEntity>> listRecordsByMonth(
            @RequestParam int year, @RequestParam int month) {
        return OperationResponse.build(taxDeclarationService.listRecordsByMonth(year, month));
    }

    /**
     * 会计工作台「本月申报待办」（按截止日落在当月聚合）。
     * <p>
     * 分三组返回当前登录会计可见的申报记录：
     * <ul>
     *   <li><b>pending</b>（待申报）：NOT_STARTED + PENDING 状态；</li>
     *   <li><b>toPay</b>（待缴款）：FILED 已申报未缴；</li>
     *   <li><b>overdue</b>（已逾期）：OVERDUE 状态。</li>
     * </ul>
     * 每条含户名（companyName，经 record→profile→company 反查）。
     * 数据隔离：只能看到当前会计负责主体的记录。
     * </p>
     *
     * @param year  年份
     * @param month 月份（1-12）
     * @return {pending, toPay, overdue, pendingCount, toPayCount, overdueCount}
     */
    @RequirePermission(resource = "scrm_tax_record", action = "read")
    @GetMapping("/records/month/todo")
    public OperationResponse<Map<String, Object>> listMonthTodo(
            @RequestParam int year, @RequestParam int month) {
        return OperationResponse.build(taxDeclarationService.listMonthTodo(year, month));
    }

    /**
     * 月历默认月份建议（本月为空时自动跳到下一个有记录的月份）。
     *
     * @return {year, month} 建议展示的年月
     */
    @RequirePermission(resource = "scrm_tax_record", action = "read")
    @GetMapping("/records/month/suggest")
    public OperationResponse<Map<String, Object>> suggestMonth() {
        int[] ym = taxDeclarationService.suggestDefaultMonth();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", ym[0]);
        result.put("month", ym[1]);
        return OperationResponse.build(result);
    }

    /**
     * 申报记录详情。
     *
     * @param id 记录 ID
     * @return 记录实体
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_tax_record", action = "read")
    @GetMapping("/records/{id}")
    public OperationResponse<ScrmTaxDeclarationRecordEntity> getRecord(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(taxDeclarationService.getRecord(id));
    }

    // ============================================================
    // 申报表生成 + 导出（P0④）
    // ============================================================

    /**
     * 生成申报表（按主体 + 税种 + 所属期聚合出表样）。
     * <p>
     * 纯内部闭环：数据只取自本系统申报档案 + 申报记录，不调用电子税局，
     * 因此税局接口不可用时仍能出表。该所属期无申报记录时不报错——返回行项目
     * 全 0、状态 {@code NOT_DECLARED} 的空表，前端据此提示会计补录。
     * </p>
     *
     * @param companyId   公司主体 ID
     * @param taxType     税种（VAT / IIT / CIT / SOCIAL_SECURITY）
     * @param periodYear  所属期年份
     * @param periodMonth 所属期月份（1-12）
     * @return 申报表（表头 + 行项目 + 合计行）
     * @throws ScrmException 参数非法 / 无权访问该主体
     */
    @RequirePermission(resource = "scrm_tax_record", action = "read")
    @GetMapping("/companies/{companyId}/sheet")
    public OperationResponse<ScrmTaxDeclarationSheetDto> buildSheet(
            @PathVariable Long companyId,
            @RequestParam String taxType,
            @RequestParam Integer periodYear,
            @RequestParam Integer periodMonth) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(
                sheetService.buildSheet(companyId, taxType, periodYear, periodMonth));
    }

    /**
     * 导出申报表 CSV（Excel 可直接打开, 含 UTF-8 BOM 不乱码）。
     * <p>
     * 返回附件而非 JSON：会计要把申报表存进客户档案 / 发给客户，
     * 浏览器直接下载比前端再拼 Blob 更可靠。内容为 {@code toCsv} 的原始结果
     * （首字符即 BOM），文件名 RFC 5987 编码为 {@code 申报表_YYYYMM.csv}。
     * </p>
     *
     * @param companyId   公司主体 ID
     * @param taxType     税种
     * @param periodYear  所属期年份
     * @param periodMonth 所属期月份（1-12）
     * @return CSV 附件响应
     * @throws ScrmException 参数非法 / 无权访问该主体
     */
    @RequirePermission(resource = "scrm_tax_record", action = "read")
    @GetMapping("/companies/{companyId}/sheet/export")
    public ResponseEntity<byte[]> exportSheetCsv(
            @PathVariable Long companyId,
            @RequestParam String taxType,
            @RequestParam Integer periodYear,
            @RequestParam Integer periodMonth) throws ScrmException {
        assertCompanyAccessible(companyId);
        String csv = sheetService.toCsv(
                sheetService.buildSheet(companyId, taxType, periodYear, periodMonth));
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        String fileName = String.format("申报表_%04d%02d.csv", periodYear, periodMonth);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        // 同时给出 ASCII filename 与 RFC 5987 filename*, 兼容中文文件名
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        headers.add("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(body.length);
        log.info("[报税日历-申报表] 导出 CSV: companyId={}, taxType={}, period={}-{}",
                companyId, taxType, periodYear, periodMonth);
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }

    /**
     * 税负风险画像（只读）。
     * <p>
     * 按主体 + 年度聚合申报数据，给出税负率与风险等级，供会计在客户档案页
     * 一眼看到风险信号。纯读操作：不写任何表，不触发申报状态机变更，因此
     * 动作权限统一为 {@code read}。与申报表端点一样以 {@code companyId} 为
     * 入参，必须逐个过数据范围校验，防止会计改 ID 拉到别家画像。
     * </p>
     *
     * @param companyId 公司主体 ID
     * @param year      统计年度
     * @return 税负风险画像
     * @throws ScrmException 参数非法 / 无权访问该主体
     */
    @RequirePermission(resource = "scrm_tax_record", action = "read")
    @GetMapping("/companies/{companyId}/risk-profile")
    public OperationResponse<ScrmTaxRiskProfileDto> riskProfile(
            @PathVariable Long companyId, @RequestParam int year) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(riskProfileService.buildProfile(companyId, year));
    }

    /**
     * 校验当前用户是否有权访问指定主体。
     * <p>
     * 申报表端点以 {@code companyId} 为入参，若不校验归属，会计改一个 ID 就能
     * 拉到别家的申报表（含纳税人识别号）。这是这些端点的<b>唯一</b>防线，
     * 必须逐个调用，不能只依赖前端隐藏入口。
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
}
