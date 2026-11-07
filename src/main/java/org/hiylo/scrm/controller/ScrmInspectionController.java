/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionController.java
 * Date : 2026/10/23 11:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.dto.ScrmInspectionSnapshotDto;
import org.hiylo.scrm.dto.ScrmInspectionTaskDto;
import org.hiylo.scrm.entity.ScrmInspectionSnapshotEntity;
import org.hiylo.scrm.entity.ScrmInspectionTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.repository.ScrmInspectionSnapshotRepository;
import org.hiylo.scrm.entity.ScrmInspectionRunEntity;
import org.hiylo.scrm.service.ScrmInspectionRunService;
import org.hiylo.scrm.service.ScrmInspectionQueryService;
import org.hiylo.scrm.service.TaxNoticeScrapeService;
import org.hiylo.scrm.service.ScrmInspectionSnapshotService;
import org.hiylo.scrm.service.ScrmInspectionTaskService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

import java.util.Optional;

/**
 * 工商税务巡检控制器。
 * <p>
 * 巡检任务管理（企业 + 周期任务 CRUD/启停）+ 巡检快照查询（按任务/维度看历史与最新）+
 * 手动触发巡检（工商 API + 税务抓取）。端点一律 {@code @RequirePermission} 声明资源
 * 与动作元数据（RBAC 由 gateway 统一鉴权, 与仓库内其它控制器一致）。
 * 巡检结果仅供内部风控告警与客户服务使用, 不对外提供查询服务（立项红线）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/inspections")
@RequiredArgsConstructor
public class ScrmInspectionController {

    /** 巡检任务服务 */
    private final ScrmInspectionTaskService taskService;

    /** 巡检快照数据访问层 */
    private final ScrmInspectionSnapshotRepository snapshotRepository;

    /** 工商巡检服务 */
    private final ScrmInspectionQueryService inspectionQueryService;

    /** 巡检快照服务 */
    private final ScrmInspectionSnapshotService snapshotService;

    /** 税务公告抓取服务（手动触发 + 公告源自检） */
    private final TaxNoticeScrapeService taxNoticeScrapeService;

    /** 巡检/抓取执行历史服务 */
    private final ScrmInspectionRunService inspectionRunService;

    // ============================================================
    // 巡检任务管理
    // ============================================================

    /**
     * 创建巡检任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 重复
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "create")
    @PostMapping
    public OperationResponse<ScrmInspectionTaskEntity> createTask(
            @Valid @RequestBody ScrmInspectionTaskDto dto) throws ScrmException {
        return OperationResponse.build(taskService.create(dto));
    }

    /**
     * 更新巡检任务。
     *
     * @param id  任务 ID
     * @param dto 更新参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmInspectionTaskEntity> updateTask(
            @PathVariable Long id, @RequestBody ScrmInspectionTaskDto dto) throws ScrmException {
        return OperationResponse.build(taskService.update(id, dto));
    }

    /**
     * 删除巡检任务。
     *
     * @param id 任务 ID
     * @return 空响应
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteTask(@PathVariable Long id) throws ScrmException {
        taskService.delete(id);
        return OperationResponse.build();
    }

    /**
     * 查询巡检任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmInspectionTaskEntity> getTask(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(taskService.get(id));
    }

    /**
     * 分页查询巡检任务。
     *
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @param cycleType 周期过滤（可空）
     * @param isEnabled 启用状态过滤（可空）
     * @param keyword   企业名称/信用代码关键字（可空）
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "read")
    @GetMapping
    public OperationResponse<Page<ScrmInspectionTaskEntity>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cycleType,
            @RequestParam(required = false) Boolean isEnabled,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(taskService.list(cycleType, isEnabled, keyword, pageable));
    }

    /**
     * 启用巡检任务。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "update")
    @PostMapping("/{id}/enable")
    public OperationResponse<ScrmInspectionTaskEntity> enableTask(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(taskService.setEnabled(id, true));
    }

    /**
     * 禁用巡检任务。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "update")
    @PostMapping("/{id}/disable")
    public OperationResponse<ScrmInspectionTaskEntity> disableTask(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(taskService.setEnabled(id, false));
    }

    // ============================================================
    // 巡检快照查询
    // ============================================================

    /**
     * 查询某任务某维度的最新快照。
     *
     * @param taskId    任务 ID
     * @param dimension 维度（BUSINESS / TAX, 可空）
     * @return 最新快照（可能为空）
     */
    @RequirePermission(resource = "scrm_inspection_snapshot", action = "read")
    @GetMapping("/{taskId}/snapshots/latest")
    public OperationResponse<ScrmInspectionSnapshotDto> latestSnapshot(
            @PathVariable Long taskId,
            @RequestParam(required = false) String dimension) {
        Optional<ScrmInspectionSnapshotEntity> latest = dimension == null || dimension.isBlank()
                ? snapshotRepository.findByTaskIdAndIsLatestTrue(taskId).stream().findFirst()
                : snapshotService.latest(taskId, dimension);
        return OperationResponse.build(latest.map(this::toSnapshotDto).orElse(null));
    }

    /**
     * 分页查询某任务某维度的历史快照。
     *
     * @param taskId    任务 ID
     * @param dimension 维度（必填）
     * @param page      页码（默认 0）
     * @param size      每页大小（默认 20）
     * @return 快照分页结果
     */
    @RequirePermission(resource = "scrm_inspection_snapshot", action = "read")
    @GetMapping("/{taskId}/snapshots")
    public OperationResponse<Page<ScrmInspectionSnapshotDto>> listSnapshots(
            @PathVariable Long taskId,
            @RequestParam String dimension,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "inspectAt"));
        Page<ScrmInspectionSnapshotDto> result = snapshotRepository
                .findByTaskIdAndDimension(taskId, dimension, pageable)
                .map(this::toSnapshotDto);
        return OperationResponse.build(result);
    }

    // ============================================================
    // 手动触发巡检
    // ============================================================

    /**
     * 手动触发一轮全量工商巡检。
     *
     * @return 巡检汇总
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "execute")
    @PostMapping("/run-business")
    public OperationResponse<ScrmInspectionQueryService.BusinessSummary> runBusinessInspection() {
        java.time.LocalDateTime startedAt = java.time.LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        ScrmInspectionQueryService.BusinessSummary summary = inspectionQueryService.inspectAll();
        inspectionRunService.recordBusiness(summary, ScrmInspectionRunEntity.TRIGGER_MANUAL,
                startedAt, System.currentTimeMillis() - startMs);
        return OperationResponse.build(summary);
    }

    /**
     * 查询启信宝接入状态（是否启用 / 是否已配 token / 是否就绪）。
     * <p>用于「先保留这条线、稍后加 token」的运维自检：不消耗 API 额度。</p>
     *
     * @return 接入状态（不回显 token 本身）
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "read")
    @GetMapping("/api-status")
    public OperationResponse<java.util.Map<String, Object>> apiStatus() {
        return OperationResponse.build(inspectionQueryService.apiStatus());
    }

    /**
     * 手动触发一轮税务公告抓取。
     * <p>
     * 此前税务抓取只能等每天 03:00 的定时任务，要验证一次得改系统时间或等一天。
     * 本端点直接复用调度器调用的同一个 {@code scrapeAll()}。
     * </p>
     *
     * @return 抓取汇总（扫描任务数 / 命中记录 / 验证码阻断 / 失败）
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "execute")
    @PostMapping("/run-tax")
    public OperationResponse<TaxNoticeScrapeService.ScrapeSummary> runTaxInspection() {
        java.time.LocalDateTime startedAt = java.time.LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        TaxNoticeScrapeService.ScrapeSummary summary = taxNoticeScrapeService.scrapeAll();
        inspectionRunService.recordTax(summary, ScrmInspectionRunEntity.TRIGGER_MANUAL,
                startedAt, System.currentTimeMillis() - startMs);
        return OperationResponse.build(summary);
    }

    /**
     * 公告源接入状态自检（各源开关 / 参数是否齐备 / 已知不可用原因）。
     *
     * @return 公告源状态
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "read")
    @GetMapping("/notice-status")
    public OperationResponse<java.util.Map<String, Object>> noticeStatus() {
        return OperationResponse.build(taxNoticeScrapeService.noticeStatus());
    }

    /**
     * 分页查询巡检/抓取执行历史。
     *
     * @param runType 执行类型（BUSINESS / TAX，可空）
     * @param page    页码（0 起）
     * @param size    每页条数
     * @return 历史分页
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "read")
    @GetMapping("/runs")
    public OperationResponse<org.springframework.data.domain.Page<ScrmInspectionRunEntity>> listRuns(
            @RequestParam(required = false) String runType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(inspectionRunService.list(runType, page, size));
    }

    /**
     * 最近 N 天执行统计（次数 / 成功 / 部分 / 失败 / 跳过 / 扫描量）。
     *
     * @param days 统计天数（默认 7）
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_inspection_task", action = "read")
    @GetMapping("/runs/stats")
    public OperationResponse<java.util.Map<String, Object>> runStats(
            @RequestParam(required = false) Integer days) {
        return OperationResponse.build(inspectionRunService.recentStats(days));
    }

    /**
     * 快照实体转 DTO。
     *
     * @param entity 快照实体
     * @return DTO
     */
    private ScrmInspectionSnapshotDto toSnapshotDto(ScrmInspectionSnapshotEntity entity) {
        ScrmInspectionSnapshotDto dto = new ScrmInspectionSnapshotDto();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setDimension(entity.getDimension());
        dto.setCreditCode(entity.getCreditCode());
        dto.setEnterpriseName(entity.getEnterpriseName());
        dto.setStateHash(entity.getStateHash());
        dto.setIsLatest(entity.getIsLatest());
        dto.setInspectAt(entity.getInspectAt());
        dto.setSource(entity.getSource());
        dto.setStatus(entity.getStatus());
        dto.setDetailJson(entity.getDetailJson());
        dto.setChanged(entity.getChanged());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}