/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmClientPortalController.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.finance.FinanceDataScope;
import org.hiylo.scrm.service.finance.portal.ScrmClientPortalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 客户查账门户控制器（V28：老板查账 · 低成本 H5 版）。
 * <p>
 * <b>只读端点</b>：按主体聚合公司概况/报税进度/近 3 期报表/固定资产/税负风险。
 * 与其它财税端点一样，以 {@code companyId} 为入参必须逐个过数据范围校验，
 * 防止把别家主体数据给到无权查看的人。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/client-portal")
@RequiredArgsConstructor
public class ScrmClientPortalController {

    /** 客户查账门户服务 */
    private final ScrmClientPortalService portalService;

    /** 财税数据范围（数据隔离） */
    private final FinanceDataScope financeDataScope;

    /**
     * 单主体查账全景。
     *
     * @param companyId 公司主体 ID
     * @param year      查账年度（默认当前年）
     * @return 聚合的只读查账数据
     * @throws ScrmException 无权访问该主体
     */
    @RequirePermission(resource = "scrm_company", action = "read")
    @GetMapping("/{companyId}/overview")
    public OperationResponse<Map<String, Object>> overview(
            @PathVariable Long companyId,
            @RequestParam(required = false) Integer year) throws ScrmException {
        assertCompanyAccessible(companyId);
        int y = year == null ? java.time.LocalDate.now().getYear() : year;
        return OperationResponse.build(portalService.overview(companyId, y));
    }

    /**
     * 数据隔离：会计只能访问自己负责的主体。
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