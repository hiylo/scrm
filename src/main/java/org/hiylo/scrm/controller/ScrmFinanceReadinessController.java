/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmFinanceReadinessController.java
 * Date : 2026/10/24 16:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.DataScopeService;
import org.hiylo.scrm.service.finance.ScrmFinanceReadinessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 财税域运维端点。
 * <p>
 * 只放「交付/运维自检」类接口，不放业务读写。当前提供就绪状态汇总：
 * 一次看清 AI / OCR / 工商 API / 公告抓取 / 个推 / 企微 六条外部依赖链路
 * 各自是「没开」还是「开了但缺凭证」。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@RestController
@RequestMapping("/scrm/finance")
@RequiredArgsConstructor
public class ScrmFinanceReadinessController {

    /** 就绪状态汇总服务 */
    private final ScrmFinanceReadinessService readinessService;

    /** 数据范围服务（判定管理类角色） */
    private final DataScopeService dataScopeService;

    /**
     * 查询财税域各外部依赖链路的就绪状态。
     * <p>
     * <b>仅管理角色可读</b>：它暴露的是外部依赖链路（数据库 / OCR / 工商 API 等）
     * 是否可用、缺哪个凭据，属于运维视角信息。菜单本就只对 admin 开放，
     * 这里把同一口径下沉到接口层，避免直接拼 URL 绕过菜单拿到基础设施细节。
     * </p>
     *
     * @return 就绪汇总（不包含任何凭证明文）
     * @throws ScrmException 非管理角色
     */
    @GetMapping("/readiness")
    @RequirePermission(resource = "scrm_inspection_task", action = "read")
    public OperationResponse<Map<String, Object>> readiness() throws ScrmException {
        if (!dataScopeService.isManagement(dataScopeService.getCurrentRole())) {
            throw ScrmException.forbidden("无权访问就绪看板: 需要管理角色");
        }
        return OperationResponse.build(readinessService.readiness());
    }
}
