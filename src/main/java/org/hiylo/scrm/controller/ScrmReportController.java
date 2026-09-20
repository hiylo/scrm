/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReportController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmReportResultDto;
import org.hiylo.scrm.dto.ScrmReportTemplateDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmReportService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

/**
 * SCRM 自定义报表控制器
 * <p>
 * 提供报表模板管理 (CRUD)、报表执行、执行结果查询接口。
 * 权限由 gateway-server 统一鉴权, 此处通过 {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm")
@RequiredArgsConstructor
public class ScrmReportController {

    /** 报表服务 */
    private final ScrmReportService reportService;

    // ============================================================
    // 报表模板管理
    // ============================================================

    /**
     * 创建报表模板
     *
     * @param dto 模板参数
     * @return 创建后的模板
     */
    @RequirePermission(resource = "scrm_report", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/reports/templates")
    public OperationResponse<ScrmReportTemplateDto> createTemplate(@Valid @RequestBody ScrmReportTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(reportService.createTemplate(dto));
    }

    /**
     * 更新报表模板
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     */
    @RequirePermission(resource = "scrm_report", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/reports/templates/{id}")
    public OperationResponse<ScrmReportTemplateDto> updateTemplate(@PathVariable Long id,
                                                                    @RequestBody ScrmReportTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(reportService.updateTemplate(id, dto));
    }

    /**
     * 删除报表模板
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_report", action = "delete")
    @DeleteMapping("/reports/templates/{id}")
    public OperationResponse<Void> deleteTemplate(@PathVariable Long id) throws ScrmException {
        reportService.deleteTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 查询报表模板详情
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws ScrmException 模板不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_report", action = "read")
    @GetMapping("/reports/templates/{id}")
    public OperationResponse<ScrmReportTemplateDto> getTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(reportService.getTemplate(id));
    }

    /**
     * 分页查询报表模板, 支持按报表类型、是否公开、创建人过滤
     *
     * @param reportType 报表类型过滤 (可选)
     * @param isPublic   是否公开过滤 (可选)
     * @param createdBy  创建人过滤 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 模板分页结果
     */
    @RequirePermission(resource = "scrm_report", action = "read")
    @GetMapping({"/reports/templates", "/reports/templates/list"})
    public OperationResponse<Page<ScrmReportTemplateDto>> listTemplates(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) Boolean isPublic,
            @RequestParam(required = false) String createdBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(reportService.listTemplates(reportType, isPublic, createdBy, pageable));
    }

    // ============================================================
    // 报表执行
    // ============================================================

    /**
     * 执行报表
     * <p>
     * 基于模板配置动态构建 SQL 并执行, 返回执行结果。结果同时持久化到 scrm_report_result 表。
     * </p>
     *
     * @param id             模板 ID
     * @param timeRangeStart 时间范围起始 (可选, 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @param timeRangeEnd   时间范围结束 (可选, 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @param runBy          执行人 (可选)
     * @return 执行结果
     * @throws ScrmException 模板不存在 / SQL 校验失败 / 执行失败
     */
    @RequirePermission(resource = "scrm_report", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/reports/templates/{id}/execute")
    public OperationResponse<ScrmReportResultDto> executeReport(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeRangeStart,
            @RequestParam(required = false) @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timeRangeEnd,
            @RequestParam(required = false) String runBy) throws ScrmException {
        return OperationResponse.build(reportService.executeReport(id, timeRangeStart, timeRangeEnd, runBy));
    }

    /**
     * 查询模板最近一次执行结果
     *
     * @param id 模板 ID
     * @return 最近执行结果 (可能为空)
     * @throws ScrmException 模板不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_report", action = "read")
    @GetMapping("/reports/templates/{id}/latest-result")
    public OperationResponse<ScrmReportResultDto> getLatestResult(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(reportService.getLatestResult(id));
    }

    /**
     * 分页查询报表执行结果历史
     *
     * @param id   模板 ID
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 执行结果分页
     * @throws ScrmException 模板不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_report", action = "read")
    @GetMapping("/reports/templates/{id}/results")
    public OperationResponse<Page<ScrmReportResultDto>> listResults(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(reportService.listResults(id, pageable));
    }

    /**
     * 查询某次执行结果详情
     *
     * @param resultId 结果 ID
     * @return 执行结果详情
     * @throws ScrmException 结果不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_report", action = "read")
    @GetMapping("/reports/results/{resultId}")
    public OperationResponse<ScrmReportResultDto> getResult(@PathVariable Long resultId) throws ScrmException {
        return OperationResponse.build(reportService.getResult(resultId));
    }
}
