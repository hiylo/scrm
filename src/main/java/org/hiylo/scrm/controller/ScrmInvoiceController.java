/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmInvoiceApplyDto;
import org.hiylo.scrm.dto.ScrmInvoiceApproveDto;
import org.hiylo.scrm.dto.ScrmInvoiceDto;
import org.hiylo.scrm.dto.ScrmInvoiceRedFlushDto;
import org.hiylo.scrm.dto.ScrmInvoiceTemplateDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmInvoiceService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 发票管理控制器。
 * <p>
 * 提供发票全生命周期管理 (申请/查询/审批/开具/寄送/送达/作废/红冲/PDF/修改/批量审批/待审批)、
 * 发票模板管理 (增删改查/启停/使用统计) 以及发票统计 (总览/月度/客户/税务/趋势/作废率/红冲) 接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/invoices")
@RequiredArgsConstructor
public class ScrmInvoiceController {

    /** 发票服务 */
    private final ScrmInvoiceService scrmInvoiceService;

    // ============================================================
    // 发票生命周期
    // ============================================================

    /**
     * 申请发票: 生成编号 → 校验金额 → 创建记录。
     *
     * @param applyDto 申请请求
     * @return 创建后的发票
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_invoice", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "申请发票过于频繁，请稍后重试")
    @PostMapping("/apply")
    public OperationResponse<ScrmInvoiceDto> applyInvoice(@Valid @RequestBody ScrmInvoiceApplyDto applyDto)
            throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.applyInvoice(applyDto));
    }

    /**
     * 查询发票详情。
     *
     * @param id 发票 ID
     * @return 发票详情
     * @throws ScrmException 发票不存在
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmInvoiceDto> getInvoice(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.getInvoice(id));
    }

    /**
     * 按发票编号查询发票。
     *
     * @param invoiceNo 发票编号
     * @return 发票详情
     * @throws ScrmException 发票不存在
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/by-no/{invoiceNo}")
    public OperationResponse<ScrmInvoiceDto> getInvoiceByNo(@PathVariable String invoiceNo)
            throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.getInvoiceByNo(invoiceNo));
    }

    /**
     * 分页查询发票, 支持按发票类型、客户、状态、日期范围与关键词过滤。
     *
     * @param invoiceType 发票类型过滤 (可空): GENERAL / SPECIAL / ELECTRONIC / PLAIN_DIGITAL / RED_REDUCED
     * @param customerId  客户 ID 过滤 (可空)
     * @param status      状态过滤 (可空): PENDING / APPROVED / ISSUED / SENT / RECEIVED / VOIDED / RED_FLUSHED / REJECTED
     * @param startTime   创建时间下限 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime     创建时间上限 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param keyword     关键词过滤, 匹配发票编号/申请编号/抬头 (可空)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 发票分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmInvoiceDto>> listInvoices(
            @RequestParam(required = false) String invoiceType,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmInvoiceService.listInvoices(invoiceType, customerId, status,
                startTime, endTime, keyword, pageable));
    }

    /**
     * 分页查询客户发票列表。
     *
     * @param customerId 客户 ID
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 发票分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/by-customer/{customerId}")
    public OperationResponse<Page<ScrmInvoiceDto>> getInvoicesByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmInvoiceService.getInvoicesByCustomer(customerId, pageable));
    }

    /**
     * 按订单 ID 查询关联发票列表。
     *
     * @param orderId 订单 ID
     * @return 发票列表
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/by-order/{orderId}")
    public OperationResponse<List<ScrmInvoiceDto>> getInvoicesByOrder(@PathVariable String orderId) {
        return OperationResponse.build(scrmInvoiceService.getInvoicesByOrder(orderId));
    }

    /**
     * 审批发票: 通过则流转至 APPROVED, 驳回则流转至 REJECTED。
     *
     * @param approveDto 审批请求 (发票 ID + 动作 + 意见)
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_invoice", action = "approve")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/approve")
    public OperationResponse<ScrmInvoiceDto> approveInvoice(@Valid @RequestBody ScrmInvoiceApproveDto approveDto)
            throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.approveInvoice(approveDto));
    }

    /**
     * 批量审批发票。
     *
     * @param invoiceIds 发票 ID 列表 (逗号分隔)
     * @param action     审批动作: APPROVE / REJECT
     * @param comment    审批意见 (可空)
     * @return 审批成功的发票数量
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_invoice", action = "approve")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/batch-approve")
    public OperationResponse<Integer> batchApprove(
            @RequestParam List<Long> invoiceIds,
            @RequestParam String action,
            @RequestParam(required = false) String comment) throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.batchApprove(invoiceIds, action, comment));
    }

    /**
     * 查询待审批发票 (状态为 PENDING)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 待审批发票分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/pending")
    public OperationResponse<Page<ScrmInvoiceDto>> getPendingInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmInvoiceService.getPendingInvoices(pageable));
    }

    /**
     * 开具发票: 生成税控发票号码 → 设置开票日期 → 更新状态 (模拟)。
     *
     * @param id       发票 ID
     * @param issuedBy 开票人 (可空)
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_invoice", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/issue")
    public OperationResponse<ScrmInvoiceDto> issueInvoice(@PathVariable Long id,
                                                           @RequestParam(required = false) String issuedBy)
            throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.issueInvoice(id, issuedBy));
    }

    /**
     * 发送发票 (邮件/邮寄, 模拟)。
     *
     * @param id 发票 ID
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_invoice", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/send")
    public OperationResponse<ScrmInvoiceDto> sendInvoice(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.sendInvoice(id));
    }

    /**
     * 标记发票已送达。
     *
     * @param id 发票 ID
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_invoice", action = "execute")
    @PostMapping("/{id}/delivered")
    public OperationResponse<ScrmInvoiceDto> markDelivered(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.markDelivered(id));
    }

    /**
     * 作废发票。
     *
     * @param id       发票 ID
     * @param reason   作废原因 (可空)
     * @param voidedBy 作废人 (可空)
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_invoice", action = "execute")
    @PostMapping("/void")
    public OperationResponse<ScrmInvoiceDto> voidInvoice(@RequestParam Long id,
                                                          @RequestParam(required = false) String reason,
                                                          @RequestParam(required = false) String voidedBy)
            throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.voidInvoice(id, reason, voidedBy));
    }

    /**
     * 红冲发票: 创建红冲发票 → 关联原发票 → 更新原发票状态。
     *
     * @param redFlushDto 红冲请求 (原发票 ID + 原因)
     * @return 红冲发票
     * @throws ScrmException 原发票不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_invoice", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/red-flush")
    public OperationResponse<ScrmInvoiceDto> redFlush(@Valid @RequestBody ScrmInvoiceRedFlushDto redFlushDto)
            throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.redFlush(redFlushDto));
    }

    /**
     * 获取发票 PDF (模拟返回 URL)。
     *
     * @param id 发票 ID
     * @return 发票 PDF URL
     * @throws ScrmException 发票不存在
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/{id}/pdf")
    public OperationResponse<String> getInvoicePdf(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.getInvoicePdf(id));
    }

    /**
     * 修改发票 (仅 PENDING 状态允许修改)。
     *
     * @param id  发票 ID
     * @param dto 发票参数
     * @return 更新后的发票
     * @throws ScrmException 发票不存在 / 状态非法 / 参数非法
     */
    @RequirePermission(resource = "scrm_invoice", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmInvoiceDto> updateInvoice(@PathVariable Long id,
                                                           @RequestBody ScrmInvoiceDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.updateInvoice(id, dto));
    }

    // ============================================================
    // 模板管理 /templates
    // ============================================================

    /**
     * 创建发票模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法 / 模板编码重复
     */
    @RequirePermission(resource = "scrm_invoice", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建模板过于频繁，请稍后重试")
    @PostMapping("/templates")
    public OperationResponse<ScrmInvoiceTemplateDto> createTemplate(
            @Valid @RequestBody ScrmInvoiceTemplateDto dto) throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.createTemplate(dto));
    }

    /**
     * 更新发票模板 (字段非空才覆盖)。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法 / 模板编码重复
     */
    @RequirePermission(resource = "scrm_invoice", action = "update")
    @PutMapping("/templates/{id}")
    public OperationResponse<ScrmInvoiceTemplateDto> updateTemplate(@PathVariable Long id,
                                                                    @RequestBody ScrmInvoiceTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.updateTemplate(id, dto));
    }

    /**
     * 删除发票模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_invoice", action = "delete")
    @DeleteMapping("/templates/{id}")
    public OperationResponse<Void> deleteTemplate(@PathVariable Long id) throws ScrmException {
        scrmInvoiceService.deleteTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 查询发票模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/templates/{id}")
    public OperationResponse<ScrmInvoiceTemplateDto> getTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.getTemplate(id));
    }

    /**
     * 按模板编码查询发票模板。
     *
     * @param code 模板编码
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/templates/code/{code}")
    public OperationResponse<ScrmInvoiceTemplateDto> getTemplateByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.getTemplateByCode(code));
    }

    /**
     * 分页查询发票模板, 支持按发票类型与启用状态过滤。
     *
     * @param invoiceType 发票类型过滤 (可空)
     * @param enabled     启用状态过滤 (可空)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 模板分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/templates/list")
    public OperationResponse<Page<ScrmInvoiceTemplateDto>> listTemplates(
            @RequestParam(required = false) String invoiceType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmInvoiceService.listTemplates(invoiceType, enabled, pageable));
    }

    /**
     * 启用发票模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_invoice", action = "update")
    @PostMapping("/templates/{id}/enable")
    public OperationResponse<ScrmInvoiceTemplateDto> enableTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.enableTemplate(id));
    }

    /**
     * 禁用发票模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_invoice", action = "update")
    @PostMapping("/templates/{id}/disable")
    public OperationResponse<ScrmInvoiceTemplateDto> disableTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmInvoiceService.disableTemplate(id));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 发票统计总览: 总数 / 各类型 / 各状态 / 总金额 / 总税额。
     * <p>时间范围按发票创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getInvoiceStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmInvoiceService.getInvoiceStats(startTime, endTime));
    }

    /**
     * 月度统计: 指定月份的发票数量/金额/各状态分布。
     *
     * @param month 月份 (格式 yyyy-MM, 默认当前月)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/stats/monthly")
    public OperationResponse<Map<String, Object>> getMonthlyStats(
            @RequestParam(required = false) String month) {
        String targetMonth = (month == null || month.isBlank())
                ? LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")) : month;
        return OperationResponse.build(scrmInvoiceService.getMonthlyStats(targetMonth));
    }

    /**
     * 客户发票统计: 客户发票数 / 总金额 / 各状态分布。
     *
     * @param customerId 客户 ID
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/stats/customer/{customerId}")
    public OperationResponse<Map<String, Object>> getCustomerInvoiceStats(
            @PathVariable Long customerId) {
        return OperationResponse.build(scrmInvoiceService.getCustomerInvoiceStats(customerId));
    }

    /**
     * 税务统计: 各税率分布 / 总税额 / 总金额。
     * <p>时间范围按发票创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/stats/tax")
    public OperationResponse<Map<String, Object>> getTaxStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmInvoiceService.getTaxStats(startTime, endTime));
    }

    /**
     * 发票趋势: 过去 N 个月每月新增发票数与金额。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<Map<String, Object>> getInvoiceTrend(
            @RequestParam(defaultValue = "6") int months) {
        return OperationResponse.build(scrmInvoiceService.getInvoiceTrend(months));
    }

    /**
     * 作废率统计: 作废发票数 / 总数。
     *
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/stats/void-rate")
    public OperationResponse<Map<String, Object>> getVoidRate() {
        return OperationResponse.build(scrmInvoiceService.getVoidRate());
    }

    /**
     * 红冲统计: 红冲发票数 / 红冲金额。
     * <p>时间范围按发票创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_invoice", action = "read")
    @GetMapping("/stats/red-flush")
    public OperationResponse<Map<String, Object>> getRedFlushStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmInvoiceService.getRedFlushStats(startTime, endTime));
    }
}
