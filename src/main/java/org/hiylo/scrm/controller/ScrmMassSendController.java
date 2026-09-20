/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMassSendController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmMassSendTaskDto;
import org.hiylo.scrm.dto.ScrmMassSendTargetDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmMassSendService;
import org.hiylo.scrm.vo.MassSendReportVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCRM 群发任务控制器
 * <p>
 * 提供群发任务的创建、更新、发布与生命周期管理 (暂停/恢复/取消),
 * 目标明细分页查询与发送结果统计报告接口。权限由 gateway-server 统一鉴权。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/mass-send")
@RequiredArgsConstructor
public class ScrmMassSendController {

    /** 群发任务服务 */
    private final ScrmMassSendService massSendService;

    /**
     * 创建群发任务
     *
     * @param dto 任务参数
     * @return 创建后的任务
     */
    @RequirePermission(resource = "scrm_mass_send", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60, message = "创建群发任务过于频繁，请稍后重试")
    @PostMapping
    public OperationResponse<ScrmMassSendTaskDto> create(@Valid @RequestBody ScrmMassSendTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(massSendService.createTask(dto));
    }

    /**
     * 更新群发任务草稿
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     */
    @RequirePermission(resource = "scrm_mass_send", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmMassSendTaskDto> update(@PathVariable Long id,
                                                          @RequestBody ScrmMassSendTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(massSendService.updateTask(id, dto));
    }

    /**
     * 发布群发任务
     * <p>
     * 按 targetType 筛选目标客户生成 target 明细, 状态置 RUNNING。
     * </p>
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_mass_send", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60, message = "发布群发任务过于频繁，请稍后重试")
    @PostMapping("/{id}/publish")
    public OperationResponse<ScrmMassSendTaskDto> publish(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(massSendService.publishTask(id));
    }

    /**
     * 暂停群发任务 (RUNNING → PAUSED)
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_mass_send", action = "execute")
    @PostMapping("/{id}/pause")
    public OperationResponse<ScrmMassSendTaskDto> pause(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(massSendService.pauseTask(id));
    }

    /**
     * 恢复群发任务 (PAUSED → RUNNING)
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_mass_send", action = "execute")
    @PostMapping("/{id}/resume")
    public OperationResponse<ScrmMassSendTaskDto> resume(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(massSendService.resumeTask(id));
    }

    /**
     * 取消群发任务 (→ COMPLETED, 未发送目标明细标记失败)
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_mass_send", action = "execute")
    @PostMapping("/{id}/cancel")
    public OperationResponse<ScrmMassSendTaskDto> cancel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(massSendService.cancelTask(id));
    }

    /**
     * 查询群发任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws ScrmException 任务不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_mass_send", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmMassSendTaskDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(massSendService.getTask(id));
    }

    /**
     * 分页查询群发任务, 支持按状态、平台类型与关键词过滤
     *
     * @param status       任务状态过滤 (可选)
     * @param platformType 平台类型过滤 (可选)
     * @param keyword      关键词过滤, 匹配任务名称 (可选)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_mass_send", action = "read")
    @GetMapping({"", "/list"})
    public OperationResponse<Page<ScrmMassSendTaskDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(massSendService.listTasks(status, platformType, keyword,
                PageRequest.of(page, size)));
    }

    /**
     * 分页查询群发任务的目标明细, 支持按发送状态过滤
     *
     * @param id     任务 ID
     * @param status 发送状态过滤 (可选: PENDING/SENT/FAILED)
     * @param page   页码 (从 0 开始, 默认 0)
     * @param size   每页大小 (默认 20)
     * @return 目标明细分页结果
     * @throws ScrmException 任务不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_mass_send", action = "read")
    @GetMapping("/{id}/targets")
    public OperationResponse<Page<ScrmMassSendTargetDto>> targets(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        return OperationResponse.build(massSendService.getTaskTargets(id, status, PageRequest.of(page, size)));
    }

    /**
     * 查询群发任务发送结果报告
     *
     * @param id 任务 ID
     * @return 发送报告
     * @throws ScrmException 任务不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_mass_send", action = "read")
    @GetMapping("/{id}/report")
    public OperationResponse<MassSendReportVo> report(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(massSendService.getTaskReport(id));
    }
}
