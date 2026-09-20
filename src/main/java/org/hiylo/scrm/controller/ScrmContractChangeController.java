/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractChangeController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmContractChangeDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmContractChangeService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import java.util.List;

/**
 * SCRM 合同变更管理控制器。
 * <p>
 * 提供合同变更的全生命周期管理: 变更增删改查、按合同/编号/类型查询、变更历史、
 * 审批变更 (批准/驳回)、执行变更 (应用变更到合同)、取消变更。权限由 gateway-server
 * 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/contract-changes")
@RequiredArgsConstructor
public class ScrmContractChangeController {

    /** 合同变更服务 */
    private final ScrmContractChangeService scrmContractChangeService;

    // ============================================================
    // 变更 CRUD
    // ============================================================

    /**
     * 创建合同变更。
     *
     * @param dto 变更参数
     * @return 创建后的变更
     * @throws ScrmException 参数非法 / 合同不存在 / 变更编号重复
     */
    @RequirePermission(resource = "scrm_contract", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建变更过于频繁，请稍后重试")
    @PostMapping
    public OperationResponse<ScrmContractChangeDto> createChange(@Valid @RequestBody ScrmContractChangeDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContractChangeService.createChange(dto));
    }

    /**
     * 更新合同变更 (字段非空才覆盖)。
     * <p>仅 PENDING / IN_REVIEW 状态变更允许更新。</p>
     *
     * @param id  变更 ID
     * @param dto 变更参数
     * @return 更新后的变更
     * @throws ScrmException 变更不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmContractChangeDto> updateChange(@PathVariable Long id,
                                                                  @RequestBody ScrmContractChangeDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContractChangeService.updateChange(id, dto));
    }

    /**
     * 删除合同变更。
     * <p>仅 PENDING / CANCELLED / REJECTED 状态变更允许删除。</p>
     *
     * @param id 变更 ID
     * @return 空响应
     * @throws ScrmException 变更不存在 / 状态不允许删除
     */
    @RequirePermission(resource = "scrm_contract", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteChange(@PathVariable Long id) throws ScrmException {
        scrmContractChangeService.deleteChange(id);
        return OperationResponse.build();
    }

    /**
     * 查询变更详情。
     *
     * @param id 变更 ID
     * @return 变更详情
     * @throws ScrmException 变更不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmContractChangeDto> getChange(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractChangeService.getChange(id));
    }

    /**
     * 按变更编号查询变更。
     *
     * @param changeNo 变更编号
     * @return 变更详情
     * @throws ScrmException 变更不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/no/{changeNo}")
    public OperationResponse<ScrmContractChangeDto> getChangeByNo(@PathVariable String changeNo)
            throws ScrmException {
        return OperationResponse.build(scrmContractChangeService.getChangeByNo(changeNo));
    }

    /**
     * 按合同 ID 查询全部变更 (按创建时间升序)。
     *
     * @param contractId 合同 ID
     * @return 变更列表
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/contract/{contractId}")
    public OperationResponse<List<ScrmContractChangeDto>> getChangesByContract(@PathVariable Long contractId) {
        return OperationResponse.build(scrmContractChangeService.getChangesByContract(contractId));
    }

    /**
     * 按变更类型分页查询变更。
     *
      * @param changeType 变更类型: AMENDMENT / SUPPLEMENT / RENEWAL / TERMINATION / TRANSFER / PRICE_CHANGE /
      * SCOPE_CHANGE / TERM_CHANGE / OTHER
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 变更分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/type/{changeType}")
    public OperationResponse<Page<ScrmContractChangeDto>> getChangesByType(
            @PathVariable String changeType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmContractChangeService.getChangesByType(changeType, pageable));
    }

    /**
     * 查询合同变更历史 (按创建时间升序)。
     *
     * @param contractId 合同 ID
     * @return 变更历史列表
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/history/contract/{contractId}")
    public OperationResponse<List<ScrmContractChangeDto>> getChangeHistory(@PathVariable Long contractId) {
        return OperationResponse.build(scrmContractChangeService.getChangeHistory(contractId));
    }

    // ============================================================
    // 变更审批与执行
    // ============================================================

    /**
     * 审批变更: 状态由 PENDING/IN_REVIEW 流转至 APPROVED, 记录审批人。
     *
     * @param id         变更 ID
     * @param approverId 审批人 ID
     * @return 更新后的变更
     * @throws ScrmException 变更不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "approve")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/approve")
    public OperationResponse<ScrmContractChangeDto> approveChange(@PathVariable Long id,
                                                                   @RequestParam Long approverId)
            throws ScrmException {
        return OperationResponse.build(scrmContractChangeService.approveChange(id, approverId));
    }

    /**
     * 驳回变更: 状态流转至 REJECTED, 记录驳回原因。
     *
     * @param id         变更 ID
     * @param approverId 审批人 ID
     * @param reason     驳回原因 (可空)
     * @return 更新后的变更
     * @throws ScrmException 变更不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "approve")
    @PostMapping("/{id}/reject")
    public OperationResponse<ScrmContractChangeDto> rejectChange(@PathVariable Long id,
                                                                  @RequestParam Long approverId,
                                                                  @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmContractChangeService.rejectChange(id, approverId, reason));
    }

    /**
     * 执行变更: 将变更应用到合同 (更新合同字段与金额), 变更状态置 EXECUTED。
     * <p>仅 APPROVED 状态变更允许执行。</p>
     *
     * @param id 变更 ID
     * @return 更新后的变更
     * @throws ScrmException 变更不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/{id}/execute")
    public OperationResponse<ScrmContractChangeDto> executeChange(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractChangeService.executeChange(id));
    }

    /**
     * 取消变更 (状态置 CANCELLED)。
     * <p>已执行/已取消的变更不允许取消。</p>
     *
     * @param id 变更 ID
     * @return 更新后的变更
     * @throws ScrmException 变更不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @PostMapping("/{id}/cancel")
    public OperationResponse<ScrmContractChangeDto> cancelChange(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractChangeService.cancelChange(id));
    }
}
