/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractQueryController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmContractDto;
import org.hiylo.scrm.dto.ScrmContractSearchDto;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmContractStatsService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCRM 合同查询控制器。
 * <p>
 * 提供合同的多条件搜索与按维度查询能力: 多条件分页搜索 (编号/名称/类型/状态/客户/相对方/
 * 日期范围/金额范围/销售人员/关键词), 按类型/状态/销售人员/金额范围分页查询,
 * 以及即将到期/已到期合同分页查询。权限由 gateway-server 统一鉴权,
 * {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/contract-query")
@RequiredArgsConstructor
public class ScrmContractQueryController {

    /** 合同统计与查询服务 */
    private final ScrmContractStatsService scrmContractStatsService;

    // ============================================================
    // 多条件搜索
    // ============================================================

    /**
     * 搜索合同: 多条件分页查询。
     * <p>支持按编号/名称/类型/状态/客户/相对方(客户名称)/签订日期范围/到期日期范围/金额范围/
     * 销售人员/关键词过滤, 支持排序字段指定。</p>
     *
     * @param searchDto 搜索条件 (可空)
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 合同分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @PostMapping("/search")
    public OperationResponse<Page<ScrmContractDto>> searchContracts(
            @RequestBody(required = false) ScrmContractSearchDto searchDto,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmContractStatsService.searchContracts(searchDto, pageable));
    }

    // ============================================================
    // 按维度分页查询
    // ============================================================

    /**
     * 按合同类型分页查询。
     *
     * @param type 合同类型: SALES / SERVICE / PARTNERSHIP / NDA / RESELLER / AGENCY / MAINTENANCE / RENTAL / PURCHASE / *
       * CUSTOM * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 合同分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/type/{type}")
    public OperationResponse<Page<ScrmContractDto>> getContractsByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmContractStatsService.getContractsByType(type, pageable));
    }

    /**
     * 按合同状态分页查询。
     *
     * @param status 合同状态: DRAFT / PENDING_REVIEW / PENDING_SIGNATURE / SIGNED / ACTIVE / EXPIRED / TERMINATED / *
       * CANCELLED / ARCHIVED * @param page 页码 (从 0 开始, 默认 0)
     * @param size   每页大小 (默认 20)
     * @return 合同分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/status/{status}")
    public OperationResponse<Page<ScrmContractDto>> getContractsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmContractStatsService.getContractsByStatus(status, pageable));
    }

    /**
     * 按销售人员分页查询。
     *
     * @param salesId 销售人员 ID
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 合同分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/sales/{salesId}")
    public OperationResponse<Page<ScrmContractDto>> getContractsBySales(
            @PathVariable Long salesId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmContractStatsService.getContractsBySales(salesId, pageable));
    }

    /**
     * 按金额范围分页查询。
     *
     * @param minAmount 最小金额 (可空)
     * @param maxAmount 最大金额 (可空)
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 合同分页结果 (按合同金额降序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/valueRange")
    public OperationResponse<Page<ScrmContractDto>> getContractsByValueRange(
            @RequestParam(required = false) Double minAmount,
            @RequestParam(required = false) Double maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "contractAmount"));
        return OperationResponse.build(scrmContractStatsService.getContractsByValueRange(minAmount,
                maxAmount, pageable));
    }

    // ============================================================
    // 即将到期 / 已到期查询
    // ============================================================

    /**
     * 分页查询即将到期的合同 (未来 N 天内到期, 状态为 ACTIVE/SIGNED)。
     *
     * @param days 天数 (默认 30)
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 合同分页结果 (按到期日期升序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/expiring")
    public OperationResponse<Page<ScrmContractDto>> getExpiringContracts(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "endDate"));
        return OperationResponse.build(scrmContractStatsService.getExpiringContracts(days, pageable));
    }

    /**
     * 分页查询已到期合同 (结束日期早于今天, 状态为 ACTIVE/SIGNED)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 合同分页结果 (按到期日期升序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/expired")
    public OperationResponse<Page<ScrmContractDto>> getExpiredContracts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "endDate"));
        return OperationResponse.build(scrmContractStatsService.getExpiredContracts(pageable));
    }
}
