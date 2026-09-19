/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAuditLogController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmAuditLogEntity;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmAuditLogService;
import org.hiylo.scrm.service.ScrmExportService;
import org.hiylo.scrm.service.ScrmExportService.ExportColumn;
import org.hiylo.scrm.vo.AuditStatsVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SCRM 操作审计日志控制器。
 * <p>
 * 提供审计日志的多维查询、失败操作检索、统计聚合与最近活动能力。
 * 全部端点为只读 (GET), 不会被 {@code AuditLogAspect} 拦截审计。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/audit-logs")
@RequiredArgsConstructor
public class ScrmAuditLogController {

    /** 审计日志服务 */
    private final ScrmAuditLogService auditLogService;

    /** 数据导出服务 (Excel / CSV) */
    private final ScrmExportService exportService;

    /**
     * 综合查询审计日志, 支持按用户 / 资源 / 结果 / 时间区间过滤。
     * <p>
     * 各过滤参数均为可选, 互斥条件下优先级: userId > resource > result > 时间区间 > 默认账号列表。
     * </p>
     *
     * @param userId    操作人用户 ID (可选)
     * @param resource  资源标识 (可选)
     * @param result    操作结果 SUCCESS / FAILED (可选)
     * @param startTime 起始时间 (可选, ISO 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可选, ISO 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 审计日志分页结果
     */
    @RequirePermission(resource = "scrm_audit_log", action = "read")
    @GetMapping
    public OperationResponse<Page<ScrmAuditLogEntity>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String result,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 多条件互斥查询: 按优先级选择单一维度, 避免组合条件查询复杂化
        if (userId != null && !userId.isBlank()) {
            return OperationResponse.build(auditLogService.getByUser(userId, page, size));
        }
        if (resource != null && !resource.isBlank()) {
            return OperationResponse.build(auditLogService.getByResource(resource, page, size));
        }
        if (result != null && !result.isBlank()) {
            return OperationResponse.build(auditLogService.getFailedOperations(page, size));
        }
        if (startTime != null && endTime != null) {
            return OperationResponse.build(
                    auditLogService.getByTimeRange(startTime, endTime, page, size));
        }
        // 无过滤条件: 按当前账号倒序分页
        return OperationResponse.build(auditLogService.list(page, size));
    }

    /**
     * 按操作人用户 ID 查询审计日志。
     *
     * @param userId 操作人用户 ID
     * @param page   页码 (从 0 开始, 默认 0)
     * @param size   每页大小 (默认 20)
     * @return 审计日志分页结果
     */
    @RequirePermission(resource = "scrm_audit_log", action = "read")
    @GetMapping("/users/{userId}")
    public OperationResponse<Page<ScrmAuditLogEntity>> getByUser(@PathVariable String userId,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(auditLogService.getByUser(userId, page, size));
    }

    /**
     * 按资源查询审计日志。
     *
     * @param resource 资源标识
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 审计日志分页结果
     */
    @RequirePermission(resource = "scrm_audit_log", action = "read")
    @GetMapping("/resources/{resource}")
    public OperationResponse<Page<ScrmAuditLogEntity>> getByResource(@PathVariable String resource,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(auditLogService.getByResource(resource, page, size));
    }

    /**
     * 查询失败操作审计日志。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 失败操作分页结果
     */
    @RequirePermission(resource = "scrm_audit_log", action = "read")
    @GetMapping("/failed")
    public OperationResponse<Page<ScrmAuditLogEntity>> getFailed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(auditLogService.getFailedOperations(page, size));
    }

    /**
     * 审计统计: 总操作数 / 成功数 / 失败数 / 成功率 / 按资源分布。
     *
     * @return 审计统计 VO
     */
    @RequirePermission(resource = "scrm_audit_log", action = "read")
    @GetMapping("/stats")
    public OperationResponse<AuditStatsVo> getStats() {
        return OperationResponse.build(auditLogService.getAuditStats());
    }

    /**
     * 查询最近 N 条审计日志。
     *
     * @param limit 返回条数 (默认 20)
     * @return 审计日志列表
     */
    @RequirePermission(resource = "scrm_audit_log", action = "read")
    @GetMapping("/recent")
    public OperationResponse<List<ScrmAuditLogEntity>> getRecent(
            @RequestParam(defaultValue = "20") int limit) {
        return OperationResponse.build(auditLogService.getRecentActivities(limit));
    }

    /**
     * 导出审计日志 (支持 xlsx / csv 格式)。
     * <p>
     * 过滤参数优先级与 {@link #list} 一致: userId > resource > result > 默认账号列表。
     * 取大页数据 (最多 10000 条), 时间字段格式化为 {@code yyyy-MM-dd HH:mm:ss}。
     * </p>
     *
     * @param format   导出格式: xlsx (默认) / csv
     * @param userId   操作人用户 ID 过滤 (可选)
     * @param resource 资源标识过滤 (可选)
     * @param result   操作结果过滤 (可选, 任意非空值触发失败日志查询)
     * @return 包含导出文件的响应实体
     */
    @RequirePermission(resource = "scrm_audit_log", action = "read")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String result) {
        // 多条件互斥查询: 与 list 端点保持一致, 取大页数据用于导出
        Page<ScrmAuditLogEntity> page;
        if (userId != null && !userId.isBlank()) {
            page = auditLogService.getByUser(userId, 0, 10000);
        } else if (resource != null && !resource.isBlank()) {
            page = auditLogService.getByResource(resource, 0, 10000);
        } else if (result != null && !result.isBlank()) {
            page = auditLogService.getFailedOperations(0, 10000);
        } else {
            page = auditLogService.list(0, 10000);
        }
        List<ScrmAuditLogEntity> data = page.getContent();
        // 时间格式化器
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // 构建导出列: 日志ID / 用户ID / 用户名 / 资源 / 动作 / 方法 / 请求URI / 结果 / 执行耗时 / 客户端IP / 操作时间
        List<ExportColumn<ScrmAuditLogEntity>> columns = List.of(
                ExportColumn.of("日志ID", ScrmAuditLogEntity::getId),
                ExportColumn.of("用户ID", ScrmAuditLogEntity::getUserId),
                ExportColumn.of("用户名", ScrmAuditLogEntity::getUsername),
                ExportColumn.of("资源", ScrmAuditLogEntity::getResource),
                ExportColumn.of("动作", ScrmAuditLogEntity::getAction),
                ExportColumn.of("方法", ScrmAuditLogEntity::getMethod),
                ExportColumn.of("请求URI", ScrmAuditLogEntity::getRequestUri),
                ExportColumn.of("结果", ScrmAuditLogEntity::getResult),
                ExportColumn.of("执行耗时(ms)", ScrmAuditLogEntity::getExecutionTime),
                ExportColumn.of("客户端IP", ScrmAuditLogEntity::getClientIp),
                ExportColumn.of("操作时间",
                        e -> e.getOperatedAt() == null ? null : e.getOperatedAt().format(fmt))
        );
        return exportService.export(data, columns, "审计日志", "audit_logs", format);
    }
}
