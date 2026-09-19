/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAccountHealthController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmAccountHealthEntity;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmAccountHealthService;
import org.hiylo.scrm.vo.AccountHealthStatsVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCRM 账号健康度检测控制器。
 * <p>
 * 提供手动触发健康检测、查询检测历史、离线账号列表与健康度统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/accounts/health")
@RequiredArgsConstructor
public class ScrmAccountHealthController {

    /** 账号健康度检测服务 */
    private final ScrmAccountHealthService accountHealthService;

    /**
     * 手动检测单个账号健康度。
     *
     * @param accountId 账号 ID
     * @return 健康检测记录
     */
    @RequirePermission(resource = "scrm_account_health", action = "check")
    @PostMapping("/check/{accountId}")
    public OperationResponse<ScrmAccountHealthEntity> check(@PathVariable Long accountId) {
        return OperationResponse.build(accountHealthService.checkAccountHealth(accountId));
    }

    /**
     * 手动触发全量健康检测。
     * <p>
     * 异步遍历所有账号, 返回检测摘要 (检测数 / 异常数)。
     * </p>
     *
     * @return 检测摘要
     */
    @RequirePermission(resource = "scrm_account_health", action = "check_all")
    @PostMapping("/check-all")
    public OperationResponse<ScrmAccountHealthService.CheckSummary> checkAll() {
        return OperationResponse.build(accountHealthService.checkAllAccounts());
    }

    /**
     * 获取指定账号最新一条健康检测记录。
     *
     * @param accountId 账号 ID
     * @return 最新健康记录 (无记录返回 data=null)
     */
    @RequirePermission(resource = "scrm_account_health", action = "read")
    @GetMapping("/{accountId}/latest")
    public OperationResponse<ScrmAccountHealthEntity> latest(@PathVariable Long accountId) {
        return OperationResponse.build(accountHealthService.getLatestHealth(accountId));
    }

    /**
     * 分页查询指定账号的健康检测历史。
     *
     * @param accountId 账号 ID
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 健康记录分页结果
     */
    @RequirePermission(resource = "scrm_account_health", action = "read")
    @GetMapping("/{accountId}/history")
    public OperationResponse<Page<ScrmAccountHealthEntity>> history(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(accountHealthService.getHealthHistory(accountId, page, size));
    }

    /**
     * 分页查询离线账号检测记录 (checkResult=OFFLINE)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 离线检测记录分页结果
     */
    @RequirePermission(resource = "scrm_account_health", action = "read")
    @GetMapping("/offline")
    public OperationResponse<Page<ScrmAccountHealthEntity>> offline(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(accountHealthService.getOfflineAccounts(page, size));
    }

    /**
     * 账号健康度统计: 总账号 / 健康 / 离线 / 冻结 / 不健康 / 在线率。
     *
     * @return 健康度统计 VO
     */
    @RequirePermission(resource = "scrm_account_health", action = "read")
    @GetMapping("/stats")
    public OperationResponse<AccountHealthStatsVo> stats() {
        return OperationResponse.build(accountHealthService.getHealthStats());
    }
}
