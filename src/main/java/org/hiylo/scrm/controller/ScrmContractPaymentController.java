/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractPaymentController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmContractPaymentDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmContractPaymentService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * SCRM 合同付款管理控制器。
 * <p>
 * 提供合同付款的全生命周期管理: 付款增删改查、按合同查询付款计划、到期/逾期付款查询、
 * 记录付款 (更新金额与状态)、取消付款、开票、发送付款提醒、定时检查并发送提醒,
 * 以及按合同的付款统计。权限由 gateway-server 统一鉴权, {@code @RequirePermission}
 * 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/contract-payments")
@RequiredArgsConstructor
public class ScrmContractPaymentController {

    /** 合同付款服务 */
    private final ScrmContractPaymentService scrmContractPaymentService;

    // ============================================================
    // 付款 CRUD
    // ============================================================

    /**
     * 添加合同付款。
     *
     * @param dto 付款参数
     * @return 创建后的付款
     * @throws ScrmException 参数非法 / 合同不存在 / 付款编号重复
     */
    @RequirePermission(resource = "scrm_contract", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建付款过于频繁，请稍后重试")
    @PostMapping
    public OperationResponse<ScrmContractPaymentDto> addPayment(@Valid @RequestBody ScrmContractPaymentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContractPaymentService.addPayment(dto));
    }

    /**
     * 更新合同付款 (字段非空才覆盖)。
     *
     * @param id  付款 ID
     * @param dto 付款参数
     * @return 更新后的付款
     * @throws ScrmException 付款不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_contract", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmContractPaymentDto> updatePayment(@PathVariable Long id,
                                                                    @RequestBody ScrmContractPaymentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContractPaymentService.updatePayment(id, dto));
    }

    /**
     * 删除合同付款。
     * <p>仅 PENDING / CANCELLED 状态付款允许删除。</p>
     *
     * @param id 付款 ID
     * @return 空响应
     * @throws ScrmException 付款不存在 / 状态不允许删除
     */
    @RequirePermission(resource = "scrm_contract", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deletePayment(@PathVariable Long id) throws ScrmException {
        scrmContractPaymentService.deletePayment(id);
        return OperationResponse.build();
    }

    /**
     * 查询付款详情。
     *
     * @param id 付款 ID
     * @return 付款详情
     * @throws ScrmException 付款不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmContractPaymentDto> getPayment(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractPaymentService.getPayment(id));
    }

    /**
     * 按合同 ID 查询全部付款 (按计划日期升序)。
     *
     * @param contractId 合同 ID
     * @return 付款列表
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/contract/{contractId}")
    public OperationResponse<List<ScrmContractPaymentDto>> getPaymentsByContract(@PathVariable Long contractId) {
        return OperationResponse.build(scrmContractPaymentService.getPaymentsByContract(contractId));
    }

    /**
     * 分页查询到期付款 (状态为 PENDING/DUE 且计划日期在当前或之前)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 付款分页结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/due")
    public OperationResponse<Page<ScrmContractPaymentDto>> getDuePayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "plannedDate"));
        return OperationResponse.build(scrmContractPaymentService.getDuePayments(pageable));
    }

    /**
     * 分页查询逾期付款 (状态为 OVERDUE 或计划日期已过且未付清)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 付款分页结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/overdue")
    public OperationResponse<Page<ScrmContractPaymentDto>> getOverduePayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "plannedDate"));
        return OperationResponse.build(scrmContractPaymentService.getOverduePayments(pageable));
    }

    // ============================================================
    // 付款操作
    // ============================================================

    /**
     * 记录付款: 更新已付/未付金额、实际付款日期、交易号, 并根据付款完成度更新状态。
     *
     * @param id            付款 ID
     * @param actualAmount  本次实付金额
     * @param actualDate    实际付款日期 (可空, 缺省为当天, ISO 格式: yyyy-MM-dd)
     * @param method        付款方式 (可空)
     * @param transactionNo 交易号 (可空)
     * @return 更新后的付款
     * @throws ScrmException 付款不存在 / 状态非法 / 金额非法
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/record")
    public OperationResponse<ScrmContractPaymentDto> recordPayment(
            @PathVariable Long id,
            @RequestParam Double actualAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate actualDate,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String transactionNo)
            throws ScrmException {
        return OperationResponse.build(scrmContractPaymentService.recordPayment(id, actualAmount, actualDate,
                method, transactionNo));
    }

    /**
     * 取消付款 (状态置 CANCELLED)。
     *
     * @param id 付款 ID
     * @return 更新后的付款
     * @throws ScrmException 付款不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @PostMapping("/{id}/cancel")
    public OperationResponse<ScrmContractPaymentDto> cancelPayment(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractPaymentService.cancelPayment(id));
    }

    /**
     * 开票: 记录发票号与开票日期, 标记已开票。
     *
     * @param id        付款 ID
     * @param invoiceNo 发票号
     * @return 更新后的付款
     * @throws ScrmException 付款不存在 / 发票号为空
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @PostMapping("/{id}/invoice")
    public OperationResponse<ScrmContractPaymentDto> issueInvoice(@PathVariable Long id,
                                                                   @RequestParam String invoiceNo)
            throws ScrmException {
        return OperationResponse.build(scrmContractPaymentService.issueInvoice(id, invoiceNo));
    }

    /**
     * 发送付款提醒: 标记已发送, 累加提醒次数, 刷新提醒日期。
     *
     * @param id 付款 ID
     * @return 更新后的付款
     * @throws ScrmException 付款不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/reminder")
    public OperationResponse<ScrmContractPaymentDto> sendPaymentReminder(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContractPaymentService.sendPaymentReminder(id));
    }

    /**
     * 检查并发送付款提醒 (定时任务): 扫描当前账号下计划日期已到但未付清的付款,
     * 自动更新逾期状态并发送提醒。
     *
     * @return 处理的付款数量
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @RateLimit(capacity = 1, refillTokens = 1, refillPeriodSeconds = 60)
    @PostMapping("/checkReminders")
    public OperationResponse<Integer> checkPaymentReminders() {
        return OperationResponse.build(scrmContractPaymentService.checkPaymentReminders());
    }

    // ============================================================
    // 付款统计与计划
    // ============================================================

    /**
     * 付款统计: 按合同 ID 统计计划/已付/未付金额, 各状态分布, 逾期数量。
     *
     * @param contractId 合同 ID
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/stats/contract/{contractId}")
    public OperationResponse<Map<String, Object>> getPaymentStats(@PathVariable Long contractId) {
        return OperationResponse.build(scrmContractPaymentService.getPaymentStats(contractId));
    }

    /**
     * 付款计划: 按合同 ID 返回付款计划列表 (按计划日期升序)。
     *
     * @param contractId 合同 ID
     * @return 付款计划列表
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/schedule/contract/{contractId}")
    public OperationResponse<List<ScrmContractPaymentDto>> getPaymentSchedule(@PathVariable Long contractId) {
        return OperationResponse.build(scrmContractPaymentService.getPaymentSchedule(contractId));
    }
}
