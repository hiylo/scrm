/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmFixedAssetController.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.entity.ScrmFixedAssetEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.finance.FinanceDataScope;
import org.hiylo.scrm.service.finance.asset.ScrmFixedAssetService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * 固定资产控制器（折旧域）。
 * <p>
 * 卡片 CRUD + 按月计提折旧（生成凭证草稿）。全部端点走
 * {@code financeDataScope.canAccessCompany} 数据隔离——会计改一个主体 ID
 * 也拉不到别家资产。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/fixed-assets")
@RequiredArgsConstructor
public class ScrmFixedAssetController {

    /** 固定资产服务 */
    private final ScrmFixedAssetService fixedAssetService;

    /** 财税数据范围（数据隔离） */
    private final FinanceDataScope financeDataScope;

    /**
     * 分页查询资产卡片。
     *
     * @param companyId 企业 ID
     * @param status    状态过滤（可空）
     * @param category  类别过滤（可空）
     * @param page      页码
     * @param size      每页大小
     * @return 卡片分页
     * @throws ScrmException 无权访问
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping
    public OperationResponse<Page<ScrmFixedAssetEntity>> list(
            @RequestParam Long companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        assertCompanyAccessible(companyId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(fixedAssetService.list(companyId, status, category, pageable));
    }

    /**
     * 新增资产卡片。
     *
     * @param companyId 企业 ID
     * @param entity    卡片数据
     * @return 保存后的卡片
     * @throws ScrmException 无权访问 / 参数非法
     */
    @RequirePermission(resource = "scrm_voucher", action = "create")
    @PostMapping
    public OperationResponse<ScrmFixedAssetEntity> create(
            @RequestParam Long companyId, @RequestBody ScrmFixedAssetEntity entity)
            throws ScrmException {
        assertCompanyAccessible(companyId);
        entity.setCompanyId(companyId);
        return OperationResponse.build(fixedAssetService.create(entity));
    }

    /**
     * 修改资产卡片。
     *
     * @param companyId 企业 ID
     * @param id        卡片 ID
     * @param patch     变更字段
     * @return 更新后的卡片
     * @throws ScrmException 无权访问 / 不存在 / 已报废
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmFixedAssetEntity> update(
            @RequestParam Long companyId, @PathVariable Long id,
            @RequestBody ScrmFixedAssetEntity patch) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(fixedAssetService.update(companyId, id, patch));
    }

    /**
     * 资产报废。
     *
     * @param companyId 企业 ID
     * @param id        卡片 ID
     * @return 更新后的卡片
     * @throws ScrmException 无权访问 / 不存在
     */
    @RequirePermission(resource = "scrm_voucher", action = "update")
    @PostMapping("/{id}/scrap")
    public OperationResponse<ScrmFixedAssetEntity> scrap(
            @RequestParam Long companyId, @PathVariable Long id) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(fixedAssetService.scrap(companyId, id));
    }

    /**
     * 按月计提折旧（生成凭证草稿）。
     *
     * @param companyId          企业 ID
     * @param periodYear         会计年度
     * @param periodMonth        会计月份
     * @param expenseSubjectCode 费用科目（可空，默认 6602 管理费用）
     * @return 计提结果（count/totalAmount/voucherId）
     * @throws ScrmException 无权访问 / 科目缺失
     */
    @RequirePermission(resource = "scrm_voucher", action = "create")
    @PostMapping("/depreciate")
    public OperationResponse<Map<String, Object>> depreciate(
            @RequestParam Long companyId,
            @RequestParam int periodYear,
            @RequestParam int periodMonth,
            @RequestParam(required = false) String expenseSubjectCode) throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(fixedAssetService.monthlyDepreciate(
                companyId, periodYear, periodMonth, expenseSubjectCode));
    }

    /**
     * 企业资产汇总。
     *
     * @param companyId 企业 ID
     * @return 汇总（资产数/原值/已提/净值）
     * @throws ScrmException 无权访问
     */
    @RequirePermission(resource = "scrm_voucher", action = "read")
    @GetMapping("/company-summary")
    public OperationResponse<Map<String, Object>> companySummary(@RequestParam Long companyId)
            throws ScrmException {
        assertCompanyAccessible(companyId);
        return OperationResponse.build(fixedAssetService.companySummary(companyId));
    }

    /**
     * 数据隔离：会计只能访问自己负责的主体。
     *
     * @param companyId 主体 ID
     * @throws ScrmException 无权访问
     */
    private void assertCompanyAccessible(Long companyId) throws ScrmException {
        if (!financeDataScope.canAccessCompany(companyId)) {
            throw ScrmException.forbidden("无权访问该主体: " + companyId);
        }
    }
}