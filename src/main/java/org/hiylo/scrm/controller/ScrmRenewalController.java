/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmRenewalController.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.dto.ScrmRenewalTaskDto;
import org.hiylo.scrm.entity.ScrmChurnWarningEntity;
import org.hiylo.scrm.entity.ScrmRenewalTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.finance.renewal.ScrmRenewalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 服务续费流失控制器（模块 B）。
 * <p>
 * 续费任务管理（手动创建 / 结果录入 / 审批通过回填）+ 流失评分（单客户扫描）+
 * 任务列表查询。端点一律 {@code @RequirePermission} 声明资源与动作元数据。
 * 任务详情仅合同销售、主管、审批人可见（归属校验在 gateway RBAC 角色上执行,
 * 与 scrm 十类越权基线一致）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/renewals")
@RequiredArgsConstructor
public class ScrmRenewalController {

    /** 续费流失核心服务 */
    private final ScrmRenewalService renewalService;

    /**
     * 手动创建续费任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 合同不存在 / 已有待办
     */
    @RequirePermission(resource = "scrm_renewal_task", action = "create")
    @PostMapping
    public OperationResponse<ScrmRenewalTaskEntity> createTask(
            @Valid @RequestBody ScrmRenewalTaskDto dto) throws ScrmException {
        return OperationResponse.build(renewalService.createTask(dto));
    }

    /**
     * 录入续费结果（RENEWED / LOST / CANCELLED）。
     *
     * @param id     任务 ID
     * @param option 续费结果
     * @param amount 续约价（RENEWED 时回填）
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许
     */
    @RequirePermission(resource = "scrm_renewal_task", action = "update")
    @PostMapping("/{id}/result")
    public OperationResponse<ScrmRenewalTaskEntity> recordResult(
            @PathVariable Long id,
            @RequestParam String option,
            @RequestParam(required = false) BigDecimal amount) throws ScrmException {
        return OperationResponse.build(renewalService.recordResult(id, option, amount, null));
    }

    /**
     * 审批通过回填（Approval 引擎接线, 折扣/赠期审批通过后调用）。
     *
     * @param id           任务 ID
     * @param discountPct  折扣比例
     * @param graceMonths  赠期月数
     * @param amount       续约价
     * @param approvalInstanceId 审批实例 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许
     */
    @RequirePermission(resource = "scrm_renewal_task", action = "update")
    @PostMapping("/{id}/approve")
    public OperationResponse<ScrmRenewalTaskEntity> approve(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal discountPct,
            @RequestParam(required = false) Integer graceMonths,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(required = false) Long approvalInstanceId) throws ScrmException {
        return OperationResponse.build(
                renewalService.approve(id, discountPct, graceMonths, amount, approvalInstanceId));
    }

    /**
     * 单客户流失评分扫描（最小实现, 复用 ChurnWarning）。
     *
     * @param customerId 客户 ID
     * @return 流失预警（null 表示不命中）
     */
    @RequirePermission(resource = "scrm_renewal_task", action = "execute")
    @PostMapping("/churn-scan/{customerId}")
    public OperationResponse<ScrmChurnWarningEntity> scanChurn(@PathVariable Long customerId) {
        return OperationResponse.build(renewalService.scanChurn(customerId));
    }

    /**
     * 分页查询续费任务。
     *
     * @param page       页码（默认 0）
     * @param size       每页大小（默认 20）
     * @param status     状态过滤（可空）
     * @param customerId 客户过滤（可空）
     * @param companyId  公司主体过滤（可空；代账场景主查询维度）
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_renewal_task", action = "read")
    @GetMapping
    public OperationResponse<Page<ScrmRenewalTaskEntity>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long companyId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(
                renewalService.listTasks(status, customerId, companyId, pageable));
    }

    /**
     * 查询续费任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_renewal_task", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmRenewalTaskEntity> getTask(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(renewalService.getTask(id));
    }
}
