/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignExecutionLogController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmCampaignExecutionLogEntity;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCampaignExecutionLogService;
import org.hiylo.scrm.service.ScrmExportService;
import org.hiylo.scrm.service.ScrmExportService.ExportColumn;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销任务执行日志控制器。
 * <p>
 * 提供营销任务执行日志的分页查询、近 N 小时日志查询、失败日志查询与执行统计接口。
 * 权限由 gateway-server 统一鉴权, 资源标识为 {@code scrm_campaign_log}。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/campaigns/{campaignId}/execution-logs")
@RequiredArgsConstructor
public class ScrmCampaignExecutionLogController {

    /** 营销任务执行日志服务 */
    private final ScrmCampaignExecutionLogService executionLogService;

    /** 数据导出服务 (Excel / CSV) */
    private final ScrmExportService exportService;

    /**
     * 分页查询指定营销任务的执行日志, 操作时间倒序返回。
     *
     * @param campaignId 营销任务 ID
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 执行日志分页结果
     */
    @RequirePermission(resource = "scrm_campaign_log", action = "read")
    @GetMapping
    public OperationResponse<Page<ScrmCampaignExecutionLogEntity>> list(
            @PathVariable Long campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(executionLogService.getExecutionLogs(campaignId, page, size));
    }

    /**
     * 查询近 N 小时的执行日志 (跨任务, 按当前账号隔离)。
     *
     * @param campaignId 营销任务 ID (路径占位, 当前实现按查询不限定单任务)
     * @param hours      时间窗口 (小时, 默认 24)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 执行日志分页结果
     */
    @RequirePermission(resource = "scrm_campaign_log", action = "read")
    @GetMapping("/recent")
    public OperationResponse<Page<ScrmCampaignExecutionLogEntity>> recent(
            @PathVariable Long campaignId,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(executionLogService.getRecentLogs(hours, page, size));
    }

    /**
     * 查询失败日志 (status=FAILED), 跨任务, 按当前账号隔离。
     *
     * @param campaignId 营销任务 ID (路径占位, 当前实现按查询不限定单任务)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 失败日志分页结果
     */
    @RequirePermission(resource = "scrm_campaign_log", action = "read")
    @GetMapping("/failed")
    public OperationResponse<Page<ScrmCampaignExecutionLogEntity>> failed(
            @PathVariable Long campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(executionLogService.getFailedLogs(page, size));
    }

    /**
     * 查询指定营销任务的执行统计 (总次数/成功/失败/平均耗时)。
     *
     * @param campaignId 营销任务 ID
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_campaign_log", action = "read")
    @GetMapping("/stats")
    public OperationResponse<Map<String, Object>> stats(@PathVariable Long campaignId) {
        return OperationResponse.build(executionLogService.getExecutionStats(campaignId));
    }

    /**
     * 导出指定营销任务的执行日志 (支持 xlsx / csv 格式)。
     * <p>
     * 取大页数据 (最多 10000 条), 时间字段格式化为 {@code yyyy-MM-dd HH:mm:ss},
     * 空值导出为空字符串。文件名格式: campaign_execution_logs_&lt;campaignId&gt;_&lt;timestamp&gt;.&lt;ext&gt;。
     * </p>
     *
     * @param campaignId 营销任务 ID
     * @param format     导出格式: xlsx (默认) / csv
     * @return 包含导出文件的响应实体
     */
    @RequirePermission(resource = "scrm_campaign_log", action = "read")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@PathVariable Long campaignId,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        // 取大页数据用于导出
        List<ScrmCampaignExecutionLogEntity> data = executionLogService
                .getExecutionLogs(campaignId, 0, 10000).getContent();
        // 时间格式化器
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // 构建导出列: 日志ID / 营销任务ID / 行为流ID / 动作 / 状态 / 错误码 / 错误消息 / 操作人 / 操作时间
        List<ExportColumn<ScrmCampaignExecutionLogEntity>> columns = List.of(
                ExportColumn.of("日志ID", ScrmCampaignExecutionLogEntity::getId),
                ExportColumn.of("营销任务ID", ScrmCampaignExecutionLogEntity::getCampaignId),
                ExportColumn.of("行为流ID", ScrmCampaignExecutionLogEntity::getBehaviorFlowId),
                ExportColumn.of("动作", ScrmCampaignExecutionLogEntity::getAction),
                ExportColumn.of("状态", ScrmCampaignExecutionLogEntity::getStatus),
                ExportColumn.of("错误码", ScrmCampaignExecutionLogEntity::getErrorCode),
                ExportColumn.of("错误消息", ScrmCampaignExecutionLogEntity::getErrorMessage),
                ExportColumn.of("操作人", ScrmCampaignExecutionLogEntity::getOperatedBy),
                ExportColumn.of("操作时间",
                        e -> e.getOperatedAt() == null ? null : e.getOperatedAt().format(fmt))
        );
        return exportService.export(data, columns, "营销任务执行日志",
                "campaign_execution_logs_" + campaignId, format);
    }
}
