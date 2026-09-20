/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInheritanceController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmInheritanceItemDto;
import org.hiylo.scrm.dto.ScrmInheritanceTaskDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmInheritanceService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 离职继承控制器
 * <p>
 * 提供离职继承任务的创建、启动、查询、明细查看与失败项重试接口。
 * 员工离职时, 管理员创建任务将其名下客户/群/会话转移给接收人, 保护客户资产。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/inheritance")
@RequiredArgsConstructor
public class ScrmInheritanceController {

    /** 离职继承服务 */
    private final ScrmInheritanceService inheritanceService;

    /**
     * 创建离职继承任务 (PENDING)。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_inheritance", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmInheritanceTaskDto> create(@Valid @RequestBody ScrmInheritanceTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(inheritanceService.createTask(dto));
    }

    /**
     * 启动继承任务 (PENDING → RUNNING), 枚举 fromUserId 的客户/群并执行转移。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许启动
     */
    @RequirePermission(resource = "scrm_inheritance", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/{id}/start")
    public OperationResponse<ScrmInheritanceTaskDto> start(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(inheritanceService.startTask(id));
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_inheritance", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmInheritanceTaskDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(inheritanceService.getTask(id));
    }

    /**
     * 分页查询任务列表, 支持按状态、离职人、接收人过滤。
     *
     * @param status     任务状态过滤 (可选)
     * @param fromUserId 离职人 userId 过滤 (可选)
     * @param toUserId   接收人 userId 过滤 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_inheritance", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmInheritanceTaskDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fromUserId,
            @RequestParam(required = false) String toUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(
                inheritanceService.listTasks(status, fromUserId, toUserId, PageRequest.of(page, size)));
    }

    /**
     * 分页查询任务明细, 支持按状态过滤。
     *
     * @param id     任务 ID
     * @param status 明细状态过滤 (可选)
     * @param page   页码 (从 0 开始, 默认 0)
     * @param size   每页大小 (默认 20)
     * @return 明细分页结果
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_inheritance", action = "read")
    @GetMapping("/{id}/items")
    public OperationResponse<Page<ScrmInheritanceItemDto>> items(@PathVariable Long id,
                                                                   @RequestParam(required = false) String status,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size)
            throws ScrmException {
        return OperationResponse.build(
                inheritanceService.getTaskItems(id, status, PageRequest.of(page, size)));
    }

    /**
     * 重试失败明细。
     *
     * @param id     任务 ID
     * @param itemId 明细 ID
     * @return 更新后的明细
     * @throws ScrmException 任务/明细不存在 / 明细状态不允许重试
     */
    @RequirePermission(resource = "scrm_inheritance", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/items/{itemId}/retry")
    public OperationResponse<ScrmInheritanceItemDto> retryItem(@PathVariable Long id,
                                                                 @PathVariable Long itemId)
            throws ScrmException {
        return OperationResponse.build(inheritanceService.retryItem(id, itemId));
    }
}
