/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCampaignDto;
import org.hiylo.scrm.dto.ScrmCampaignTemplateDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCampaignReportService;
import org.hiylo.scrm.service.ScrmCampaignService;
import org.hiylo.scrm.service.ScrmCampaignTemplateService;
import org.hiylo.scrm.vo.CampaignReportVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCRM 营销任务控制器
 * <p>
 * 提供营销任务的创建、更新、查询、生命周期管理 (启动/暂停/恢复/停止)、账号分配与 SOP 模板接口。
 * 启动任务时通过 Feign 调 scrm-server 创建并执行行为流。权限由 gateway-server 统一鉴权。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/campaigns")
@RequiredArgsConstructor
public class ScrmCampaignController {

    /** 营销任务服务 */
    private final ScrmCampaignService campaignService;

    /** SOP 模板服务, 独立承载模板增删改查 */
    private final ScrmCampaignTemplateService campaignTemplateService;

    /** 营销任务效果分析服务, 基于执行日志聚合生成报告 */
    private final ScrmCampaignReportService campaignReportService;

    /**
     * 创建营销任务
     *
     * @param dto 任务参数
     * @return 创建后的任务
     */
    @RequirePermission(resource = "scrm_campaign", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60, message = "创建任务过于频繁，请稍后重试")
    @PostMapping
    public OperationResponse<ScrmCampaignDto> create(@Valid @RequestBody ScrmCampaignDto dto) {
        return OperationResponse.build(campaignService.createCampaign(dto));
    }

    /**
     * 更新营销任务
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     */
    @RequirePermission(resource = "scrm_campaign", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmCampaignDto> update(@PathVariable Long id,
                                                      @RequestBody ScrmCampaignDto dto)
            throws ScrmException {
        return OperationResponse.build(campaignService.updateCampaign(id, dto));
    }

    /**
     * 查询营销任务
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws ScrmException 任务不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_campaign", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCampaignDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(campaignService.getCampaign(id));
    }

    /**
     * 删除营销任务
     * <p>
     * 运行中的任务不允许删除 (需先停止), 删除时级联清理账号关联记录, 执行日志保留用于审计。
     * </p>
     *
     * @param id 任务 ID
     * @return 空响应
     * @throws ScrmException 任务不存在 / 权限不足 / 任务运行中
     */
    @RequirePermission(resource = "scrm_campaign", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        campaignService.deleteCampaign(id);
        return OperationResponse.build(null);
    }

    /**
     * 查询营销任务效果分析报告
     * <p>
     * 基于执行日志聚合生成, 包含执行计数、成功率 / 失败率、按天趋势与 Top 错误统计。
     * </p>
     *
     * @param id 任务 ID
     * @return 效果分析报告
     */
    @RequirePermission(resource = "scrm_campaign", action = "read")
    @GetMapping("/{id}/report")
    public OperationResponse<CampaignReportVo> report(@PathVariable Long id) {
        log.info("查询营销任务效果分析报告: campaignId={}", id);
        return OperationResponse.build(campaignReportService.generateReport(id));
    }

    /**
     * 启动营销任务
     * <p>
     * 通过 Feign 调 scrm-server 创建行为流并执行, 保存 behaviorFlowId 后状态置 RUNNING。
     * </p>
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_campaign", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60, message = "执行任务过于频繁，请稍后重试")
    @PostMapping("/{id}/start")
    public OperationResponse<ScrmCampaignDto> start(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(campaignService.startCampaign(id));
    }

    /**
     * 暂停营销任务 (状态置 PAUSED)
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_campaign", action = "execute")
    @PostMapping("/{id}/pause")
    public OperationResponse<ScrmCampaignDto> pause(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(campaignService.pauseCampaign(id));
    }

    /**
     * 恢复营销任务 (状态置 RUNNING)
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_campaign", action = "execute")
    @PostMapping("/{id}/resume")
    public OperationResponse<ScrmCampaignDto> resume(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(campaignService.resumeCampaign(id));
    }

    /**
     * 停止营销任务 (状态置 COMPLETED)
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_campaign", action = "execute")
    @PostMapping("/{id}/stop")
    public OperationResponse<ScrmCampaignDto> stop(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(campaignService.stopCampaign(id));
    }

    /**
     * 批量分配账号到营销任务
     * <p>
     * 分配前校验账号存在性 / 平台一致性 / 登录态, 校验失败的账号跳过并记录日志,
     * 不阻断其他账号分配。已分配的账号自动跳过 (幂等)。
     * </p>
     *
     * @param id         任务 ID
     * @param accountIds 账号 ID 列表 (请求体)
     * @return 当前任务关联的账号 ID 列表 (含历史已分配)
     */
    @RequirePermission(resource = "scrm_campaign", action = "update")
    @PostMapping("/{id}/accounts")
    public OperationResponse<List<Long>> assignAccounts(@PathVariable Long id,
                                                         @RequestBody List<Long> accountIds)
            throws ScrmException {
        return OperationResponse.build(campaignService.assignAccounts(id, accountIds));
    }

    /**
     * 批量移除营销任务的账号分配
     * <p>
     * 删除任务-账号关联记录, 不存在的 accountId 静默跳过 (幂等)。
     * </p>
     *
     * @param id         任务 ID
     * @param accountIds 账号 ID 列表 (请求体)
     * @return 移除后任务剩余的账号 ID 列表
     */
    @RequirePermission(resource = "scrm_campaign", action = "update")
    @DeleteMapping("/{id}/accounts")
    public OperationResponse<List<Long>> unassignAccounts(@PathVariable Long id,
                                                           @RequestBody List<Long> accountIds)
            throws ScrmException {
        return OperationResponse.build(campaignService.unassignAccounts(id, accountIds));
    }

    /**
     * 查询营销任务已分配的账号 ID 列表
     *
     * @param id 任务 ID
     * @return 账号 ID 列表
     */
    @RequirePermission(resource = "scrm_campaign", action = "read")
    @GetMapping("/{id}/accounts")
    public OperationResponse<List<Long>> getAssignedAccounts(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(campaignService.getAssignedAccountIds(id));
    }

    /**
     * 分页查询营销任务, 支持按状态与平台类型过滤
     *
     * @param status       任务状态过滤 (可选)
     * @param platformType 平台类型过滤 (可选)
     * @param campaignType 任务类型过滤 (可选)
     * @param keyword      任务名称关键字过滤 (可选)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_campaign", action = "read")
    @GetMapping({"", "/list"})
    public OperationResponse<Page<ScrmCampaignDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String campaignType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(campaignService.listCampaigns(status,
                platformType, campaignType, keyword, page, size));
    }

    /**
     * 创建 SOP 模板
     * <p>
     * 委托 {@link ScrmCampaignTemplateService#create} 完成持久化。
     * </p>
     *
     * @param dto 模板参数
     * @return 创建后的模板
     */
    @RequirePermission(resource = "scrm_campaign_template", action = "create")
    @PostMapping("/templates")
    public OperationResponse<ScrmCampaignTemplateDto> createTemplate(
            @Valid @RequestBody ScrmCampaignTemplateDto dto) {
        return OperationResponse.build(campaignTemplateService.create(dto));
    }

    /**
     * 分页查询 SOP 模板, 支持按任务类型与平台类型过滤
     * <p>
     * 委托 {@link ScrmCampaignTemplateService#list} 完成查询, 返回分页结果。
     * </p>
     *
     * @param campaignType 任务类型过滤 (可选)
     * @param platformType 平台类型过滤 (可选)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 模板分页结果
     */
    @RequirePermission(resource = "scrm_campaign_template", action = "read")
    @GetMapping("/templates/list")
    public OperationResponse<Page<ScrmCampaignTemplateDto>> listTemplates(
            @RequestParam(required = false) String campaignType,
            @RequestParam(required = false) String platformType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(
                campaignTemplateService.list(campaignType, platformType, page, size));
    }

    /**
     * 查询 SOP 模板详情
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    @RequirePermission(resource = "scrm_campaign_template", action = "read")
    @GetMapping("/templates/{id}")
    public OperationResponse<ScrmCampaignTemplateDto> getTemplate(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(campaignTemplateService.findById(id));
    }

    /**
     * 更新 SOP 模板 (字段非空才覆盖)
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     */
    @RequirePermission(resource = "scrm_campaign_template", action = "update")
    @PutMapping("/templates/{id}")
    public OperationResponse<ScrmCampaignTemplateDto> updateTemplate(@PathVariable Long id,
                                                                      @RequestBody ScrmCampaignTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(campaignTemplateService.update(id, dto));
    }

    /**
     * 删除 SOP 模板
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_campaign_template", action = "delete")
    @DeleteMapping("/templates/{id}")
    public OperationResponse<Void> deleteTemplate(@PathVariable Long id) throws ScrmException {
        campaignTemplateService.delete(id);
        return OperationResponse.build();
    }
}
