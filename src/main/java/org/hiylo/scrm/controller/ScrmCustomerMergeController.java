/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerMergeController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCustomerMergeRequestDto;
import org.hiylo.scrm.entity.ScrmCustomerDuplicateEntity;
import org.hiylo.scrm.entity.ScrmCustomerMergeRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCustomerMergeService;
import org.hiylo.scrm.vo.CustomerMergeStatsVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * SCRM 客户去重 / 合并控制器
 * <p>
 * 提供重复检测、确认 / 忽略、客户合并与合并回滚接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/customer-merge")
@RequiredArgsConstructor
public class ScrmCustomerMergeController {

    /** 客户合并服务 */
    private final ScrmCustomerMergeService customerMergeService;

    /**
     * 检测重复客户
     * <p>
     * 扫描当前账号下的全部客户, 按精确匹配 (platformType + platformCustomerUid)
     * 和模糊匹配 (昵称) 创建重复检测记录。
     * </p>
     *
     * @return 新检测到的重复对数
     */
    @RequirePermission(resource = "scrm_customer_merge", action = "create")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60,
            message = "检测操作过于频繁，请稍后重试")
    @PostMapping("/detect")
    public OperationResponse<Map<String, Integer>> detect() {
        int detected = customerMergeService.detectDuplicates();
        return OperationResponse.build(Map.of("detected", detected));
    }

    /**
     * 分页查询重复检测结果
     *
     * @param status 处理状态过滤 (可选: PENDING / CONFIRMED / IGNORED / MERGED)
     * @param page   页码 (默认 0)
     * @param size   每页大小 (默认 20)
     * @return 重复检测分页
     */
    @RequirePermission(resource = "scrm_customer_merge", action = "read")
    @GetMapping("/duplicates")
    public OperationResponse<Page<ScrmCustomerDuplicateEntity>> duplicates(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(customerMergeService.getDuplicates(status, page, size));
    }

    /**
     * 确认重复 (将状态从 PENDING 改为 CONFIRMED)
     *
     * @param id 检测记录 ID
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_customer_merge", action = "update")
    @PutMapping("/duplicates/{id}/confirm")
    public OperationResponse<ScrmCustomerDuplicateEntity> confirmDuplicate(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(customerMergeService.confirmDuplicate(id));
    }

    /**
     * 忽略重复 (将状态改为 IGNORED)
     *
     * @param id 检测记录 ID
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_customer_merge", action = "update")
    @PutMapping("/duplicates/{id}/ignore")
    public OperationResponse<ScrmCustomerDuplicateEntity> ignoreDuplicate(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(customerMergeService.ignoreDuplicate(id));
    }

    /**
     * 合并客户
     * <p>
     * 将多个客户合并到主客户。主客户保留, 被合并客户标记为已合并,
     * 合并记录支持后续回滚。
     * </p>
     *
     * @param request 合并请求
     * @return 合并记录
     * @throws ScrmException 参数非法 / 客户不存在 / 合并冲突
     */
    @RequirePermission(resource = "scrm_customer_merge", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60,
            message = "合并操作过于频繁，请稍后重试")
    @PostMapping("/merge")
    public OperationResponse<ScrmCustomerMergeRecordEntity> merge(
            @Valid @RequestBody ScrmCustomerMergeRequestDto request) throws ScrmException {
        return OperationResponse.build(customerMergeService.mergeCustomers(request));
    }

    /**
     * 回滚合并
     *
     * @param recordId 合并记录 ID
     * @return 更新后的合并记录
     * @throws ScrmException 合并记录不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_customer_merge", action = "update")
    @PutMapping("/records/{recordId}/revert")
    public OperationResponse<ScrmCustomerMergeRecordEntity> revert(
            @PathVariable Long recordId) throws ScrmException {
        return OperationResponse.build(customerMergeService.revertMerge(recordId));
    }

    /**
     * 分页查询合并记录
     *
     * @param status 合并状态过滤 (可选: COMPLETED / REVERTED)
     * @param page   页码 (默认 0)
     * @param size   每页大小 (默认 20)
     * @return 合并记录分页
     */
    @RequirePermission(resource = "scrm_customer_merge", action = "read")
    @GetMapping("/records")
    public OperationResponse<Page<ScrmCustomerMergeRecordEntity>> records(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(customerMergeService.getMergeRecords(status, page, size));
    }

    /**
     * 获取合并统计
     *
     * @return 统计 VO
     */
    @RequirePermission(resource = "scrm_customer_merge", action = "read")
    @GetMapping("/stats")
    public OperationResponse<CustomerMergeStatsVo> stats() {
        return OperationResponse.build(customerMergeService.getStats());
    }
}
