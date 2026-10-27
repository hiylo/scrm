/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
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
import org.hiylo.scrm.entity.ScrmTaxDeclarationProfileEntity;
import org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.finance.tax.ScrmTaxDeclarationService;
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

import java.math.BigDecimal;
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
     * 缴款完成（会计动作, FILED/OVERDUE → PAID）。
     *
     * @param id         记录 ID
     * @param paidAmount 实缴税额（可空）
     * @param receiptNo  完税凭证号（可空）
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
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
     * @param id         记录 ID
     * @param taxAmount  申报税额（可空）
     * @param paidAmount 实缴税额（可空）
     * @param receiptNo  完税凭证号（可空）
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
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
}
