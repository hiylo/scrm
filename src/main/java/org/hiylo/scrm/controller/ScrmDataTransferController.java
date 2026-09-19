/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataTransferController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmExportTaskDto;
import org.hiylo.scrm.dto.ScrmImportTaskDto;
import org.hiylo.scrm.dto.ScrmImportTemplateDto;
import org.hiylo.scrm.dto.ScrmImportTriggerDto;
import org.hiylo.scrm.entity.ScrmDataTransferLogEntity;
import org.hiylo.scrm.entity.ScrmExportTaskEntity;
import org.hiylo.scrm.entity.ScrmImportTaskEntity;
import org.hiylo.scrm.entity.ScrmImportTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmDataTransferService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 数据导入导出中心控制器。
 * <p>
 * 提供导入模板管理、导入任务、导出任务、数据校验、导入历史与错误处理接口,
 * 支撑客户/联系人/跟进记录/标签/商品/订单等多类型数据的批量导入导出。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明,
 * {@code @RateLimit} 对写操作与执行类接口进行限流保护。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/data-transfer")
@RequiredArgsConstructor
public class ScrmDataTransferController {

    /** 数据导入导出服务 */
    private final ScrmDataTransferService dataTransferService;

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建导入模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/templates")
    public OperationResponse<ScrmImportTemplateEntity> createTemplate(@Valid @RequestBody ScrmImportTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(dataTransferService.createTemplate(dto));
    }

    /**
     * 更新导入模板。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/templates/{id}")
    public OperationResponse<ScrmImportTemplateEntity> updateTemplate(@PathVariable Long id,
                                                                       @RequestBody ScrmImportTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(dataTransferService.updateTemplate(id, dto));
    }

    /**
     * 删除导入模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "delete")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @DeleteMapping("/templates/{id}")
    public OperationResponse<Void> deleteTemplate(@PathVariable Long id) throws ScrmException {
        dataTransferService.deleteTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 查询导入模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/templates/{id}")
    public OperationResponse<ScrmImportTemplateEntity> getTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(dataTransferService.getTemplate(id));
    }

    /**
     * 分页查询导入模板列表。
     *
     * @param dataType 数据类型过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  关键字过滤（按模板名称模糊匹配, 可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 模板分页结果
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/templates/list")
    public OperationResponse<Page<ScrmImportTemplateEntity>> listTemplates(
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(
                dataTransferService.listTemplates(dataType, enabled, keyword, pageable));
    }

    /**
     * 启用导入模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/templates/{id}/enable")
    public OperationResponse<Void> enableTemplate(@PathVariable Long id) throws ScrmException {
        dataTransferService.enableTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 禁用导入模板。
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/templates/{id}/disable")
    public OperationResponse<Void> disableTemplate(@PathVariable Long id) throws ScrmException {
        dataTransferService.disableTemplate(id);
        return OperationResponse.build();
    }

    // ============================================================
    // 导入任务管理
    // ============================================================

    /**
     * 创建导入任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/imports")
    public OperationResponse<ScrmImportTaskEntity> createImportTask(@Valid @RequestBody ScrmImportTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(dataTransferService.createImportTask(dto));
    }

    /**
     * 查询导入任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/imports/{id}")
    public OperationResponse<ScrmImportTaskEntity> getImportTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(dataTransferService.getImportTask(id));
    }

    /**
     * 分页查询导入任务列表。
     *
     * @param dataType  数据类型过滤（可空）
     * @param status    状态过滤（可空）
     * @param startTime 创建时间下限 (yyyy-MM-dd HH:mm:ss, 可空)
     * @param endTime   创建时间上限 (yyyy-MM-dd HH:mm:ss, 可空)
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/imports/list")
    public OperationResponse<Page<ScrmImportTaskEntity>> listImportTasks(
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(
                dataTransferService.listImportTasks(dataType, status, startTime, endTime, pageable));
    }

    /**
     * 取消导入任务。
     *
     * @param id 任务 ID
     * @return 取消后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/imports/{id}/cancel")
    public OperationResponse<ScrmImportTaskEntity> cancelImportTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(dataTransferService.cancelImportTask(id));
    }

    /**
     * 重试导入任务。
     *
     * @param id 任务 ID
     * @return 重试后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/imports/{id}/retry")
    public OperationResponse<ScrmImportTaskEntity> retryImportTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(dataTransferService.retryImportTask(id));
    }

    /**
     * 触发导入 (创建任务 → 验证 → 导入 → 记录日志)。
     *
     * @param dto 触发参数 (templateId + fileName + options)
     * @return 导入任务实体
     * @throws ScrmException 模板不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/imports/trigger")
    public OperationResponse<ScrmImportTaskEntity> triggerImport(@Valid @RequestBody ScrmImportTriggerDto dto)
            throws ScrmException {
        return OperationResponse.build(dataTransferService.triggerImport(dto));
    }

    /**
     * 数据验证 (模拟实现)。
     *
     * @param id 任务 ID
     * @return 验证后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/imports/{id}/validate")
    public OperationResponse<ScrmImportTaskEntity> validateData(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(dataTransferService.validateData(id));
    }

    // ============================================================
    // 导出任务管理
    // ============================================================

    /**
     * 创建导出任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/exports")
    public OperationResponse<ScrmExportTaskEntity> createExportTask(@Valid @RequestBody ScrmExportTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(dataTransferService.createExportTask(dto));
    }

    /**
     * 查询导出任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/exports/{id}")
    public OperationResponse<ScrmExportTaskEntity> getExportTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(dataTransferService.getExportTask(id));
    }

    /**
     * 分页查询导出任务列表。
     *
     * @param dataType  数据类型过滤（可空）
     * @param status    状态过滤（可空）
     * @param startTime 创建时间下限 (yyyy-MM-dd HH:mm:ss, 可空)
     * @param endTime   创建时间上限 (yyyy-MM-dd HH:mm:ss, 可空)
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/exports/list")
    public OperationResponse<Page<ScrmExportTaskEntity>> listExportTasks(
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(
                dataTransferService.listExportTasks(dataType, status, startTime, endTime, pageable));
    }

    /**
     * 取消导出任务。
     *
     * @param id 任务 ID
     * @return 取消后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/exports/{id}/cancel")
    public OperationResponse<ScrmExportTaskEntity> cancelExportTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(dataTransferService.cancelExportTask(id));
    }

    /**
     * 触发导出 (查询 → 导出 → 记录日志, 模拟实现)。
     *
     * @param id 任务 ID
     * @return 导出后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/exports/{id}/trigger")
    public OperationResponse<ScrmExportTaskEntity> triggerExport(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(dataTransferService.triggerExport(id));
    }

    // ============================================================
    // 日志查询
    // ============================================================

    /**
     * 分页查询导入导出日志列表。
     *
     * @param taskId   任务 ID 过滤（可空）
     * @param taskType 任务类型过滤（可空: IMPORT/EXPORT）
     * @param status   状态过滤（可空: SUCCESS/WARNING/ERROR）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 日志分页结果
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/logs/list")
    public OperationResponse<Page<ScrmDataTransferLogEntity>> listLogs(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "processedAt"));
        return OperationResponse.build(dataTransferService.listLogs(taskId, taskType, status, pageable));
    }

    /**
     * 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志详情
     * @throws ScrmException 日志不存在
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/logs/{id}")
    public OperationResponse<ScrmDataTransferLogEntity> getLog(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(dataTransferService.getLog(id));
    }

    /**
     * 查询某任务的全部日志。
     *
     * @param taskId 任务 ID
     * @return 日志列表
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/logs/task/{taskId}")
    public OperationResponse<List<ScrmDataTransferLogEntity>> getTaskLogs(
            @PathVariable Long taskId) throws ScrmException {
        return OperationResponse.build(dataTransferService.getTaskLogs(taskId));
    }

    /**
     * 查询某任务的错误日志 (status=ERROR)。
     *
     * @param taskId 任务 ID
     * @return 错误日志列表
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/logs/task/{taskId}/errors")
    public OperationResponse<List<ScrmDataTransferLogEntity>> getErrorLogs(
            @PathVariable Long taskId) throws ScrmException {
        return OperationResponse.build(dataTransferService.getErrorLogs(taskId));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 数据导入导出统计概览: 导入/导出任务数、成功率、记录数。
     *
     * @param startTime 起始时间 (yyyy-MM-dd HH:mm:ss, 可空)
     * @param endTime   截止时间 (yyyy-MM-dd HH:mm:ss, 可空)
     * @return 统计信息
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getTransferStats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return OperationResponse.build(dataTransferService.getTransferStats(startTime, endTime));
    }

    /**
     * 模板使用统计: 各模板使用次数与启用状态。
     *
     * @return 模板统计列表
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/stats/templates")
    public OperationResponse<List<Map<String, Object>>> getTemplateStats() {
        return OperationResponse.build(dataTransferService.getTemplateStats());
    }

    /**
     * 最近导入/导出任务 (按创建时间倒序合并)。
     *
     * @param limit 返回条数上限（默认 10）
     * @return 最近任务列表
     */
    @RequirePermission(resource = "scrm_data_transfer", action = "read")
    @GetMapping("/stats/recent")
    public OperationResponse<List<Map<String, Object>>> getRecentTransfers(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(dataTransferService.getRecentTransfers(limit));
    }
}
