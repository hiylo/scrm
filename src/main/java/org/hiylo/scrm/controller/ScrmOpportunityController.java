/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpportunityController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmFunnelDto;
import org.hiylo.scrm.dto.ScrmFunnelStageDto;
import org.hiylo.scrm.dto.ScrmOpportunityDto;
import org.hiylo.scrm.dto.ScrmOpportunityStageChangeDto;
import org.hiylo.scrm.dto.ScrmOpportunityStageHistoryDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmOpportunityService;
import org.hiylo.scrm.vo.FunnelAnalysisVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import java.util.Map;

/**
 * SCRM 销售漏斗与商机控制器
 * <p>
 * 提供销售漏斗定义、漏斗阶段、商机档案、阶段推进、漏斗分析与销售预测接口。
 * 权限由 gateway-server 统一鉴权, 此处通过 {@link RequirePermission} 声明资源与动作元数据。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm")
@RequiredArgsConstructor
public class ScrmOpportunityController {

    /** 商机服务 */
    private final ScrmOpportunityService opportunityService;

    // ============================================================
    // 漏斗管理
    // ============================================================

    /**
     * 创建销售漏斗
     *
     * @param dto 漏斗参数
     * @return 创建后的漏斗
     */
    @RequirePermission(resource = "scrm_funnel", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/funnels")
    public OperationResponse<ScrmFunnelDto> createFunnel(@Valid @RequestBody ScrmFunnelDto dto)
            throws ScrmException {
        return OperationResponse.build(opportunityService.createFunnel(dto));
    }

    /**
     * 更新销售漏斗
     *
     * @param id  漏斗 ID
     * @param dto 漏斗参数
     * @return 更新后的漏斗
     */
    @RequirePermission(resource = "scrm_funnel", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/funnels/{id}")
    public OperationResponse<ScrmFunnelDto> updateFunnel(@PathVariable Long id,
                                                          @RequestBody ScrmFunnelDto dto)
            throws ScrmException {
        return OperationResponse.build(opportunityService.updateFunnel(id, dto));
    }

    /**
     * 删除销售漏斗
     *
     * @param id 漏斗 ID
     * @return 空响应
     * @throws ScrmException 漏斗不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_funnel", action = "delete")
    @DeleteMapping("/funnels/{id}")
    public OperationResponse<Void> deleteFunnel(@PathVariable Long id) throws ScrmException {
        opportunityService.deleteFunnel(id);
        return OperationResponse.build();
    }

    /**
     * 查询销售漏斗详情
     *
     * @param id 漏斗 ID
     * @return 漏斗详情
     * @throws ScrmException 漏斗不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_funnel", action = "read")
    @GetMapping("/funnels/{id}")
    public OperationResponse<ScrmFunnelDto> getFunnel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(opportunityService.getFunnel(id));
    }

    /**
     * 分页查询销售漏斗, 支持按状态过滤
     *
     * @param status 状态过滤 (可选)
     * @param page   页码 (从 0 开始, 默认 0)
     * @param size   每页大小 (默认 20)
     * @return 漏斗分页结果
     */
    @RequirePermission(resource = "scrm_funnel", action = "read")
    @GetMapping({"/funnels", "/funnels/list"})
    public OperationResponse<Page<ScrmFunnelDto>> listFunnels(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(opportunityService.listFunnels(status, pageable));
    }

    /**
     * 设置默认漏斗
     *
     * @param id 漏斗 ID
     * @return 更新后的漏斗
     * @throws ScrmException 漏斗不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_funnel", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/funnels/{id}/default")
    public OperationResponse<ScrmFunnelDto> setDefaultFunnel(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(opportunityService.setDefaultFunnel(id));
    }

    /**
     * 漏斗分析: 各阶段商机数、金额、转化率与平均停留天数
     *
     * @param funnelId 漏斗 ID
     * @return 漏斗分析结果
     */
    @RequirePermission(resource = "scrm_funnel", action = "read")
    @GetMapping("/funnels/{funnelId}/analysis")
    public OperationResponse<FunnelAnalysisVo> getFunnelAnalysis(@PathVariable Long funnelId)
            throws ScrmException {
        return OperationResponse.build(opportunityService.getFunnelAnalysis(funnelId));
    }

    // ============================================================
    // 漏斗阶段管理
    // ============================================================

    /**
     * 新增漏斗阶段
     *
     * @param funnelId 漏斗 ID
     * @param dto      阶段参数
     * @return 创建后的阶段
     */
    @RequirePermission(resource = "scrm_funnel", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/funnels/{funnelId}/stages")
    public OperationResponse<ScrmFunnelStageDto> addStage(@PathVariable Long funnelId,
                                                            @Valid @RequestBody ScrmFunnelStageDto dto)
            throws ScrmException {
        return OperationResponse.build(opportunityService.addStage(funnelId, dto));
    }

    /**
     * 查询漏斗的全部阶段 (按 stageOrder 升序)
     *
     * @param funnelId 漏斗 ID
     * @return 阶段列表
     */
    @RequirePermission(resource = "scrm_funnel", action = "read")
    @GetMapping("/funnels/{funnelId}/stages")
    public OperationResponse<List<ScrmFunnelStageDto>> listStages(@PathVariable Long funnelId)
            throws ScrmException {
        return OperationResponse.build(opportunityService.listStages(funnelId));
    }

    /**
     * 更新漏斗阶段
     *
     * @param funnelId 漏斗 ID
     * @param id       阶段 ID
     * @param dto      阶段参数
     * @return 更新后的阶段
     */
    @RequirePermission(resource = "scrm_funnel", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/funnels/{funnelId}/stages/{id}")
    public OperationResponse<ScrmFunnelStageDto> updateStage(@PathVariable Long funnelId,
                                                              @PathVariable Long id,
                                                              @RequestBody ScrmFunnelStageDto dto)
            throws ScrmException {
        return OperationResponse.build(opportunityService.updateStage(id, dto));
    }

    /**
     * 删除漏斗阶段
     *
     * @param funnelId 漏斗 ID
     * @param id       阶段 ID
     * @return 空响应
     * @throws ScrmException 阶段不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_funnel", action = "update")
    @DeleteMapping("/funnels/{funnelId}/stages/{id}")
    public OperationResponse<Void> deleteStage(@PathVariable Long funnelId,
                                                @PathVariable Long id) throws ScrmException {
        opportunityService.deleteStage(id);
        return OperationResponse.build();
    }

    /**
     * 批量重排漏斗阶段顺序
     *
     * @param funnelId 漏斗 ID
     * @param stageIds 阶段 ID 列表 (按新顺序排列)
     * @return 重排后的阶段列表
     */
    @RequirePermission(resource = "scrm_funnel", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/funnels/{funnelId}/stages/reorder")
    public OperationResponse<List<ScrmFunnelStageDto>> reorderStages(@PathVariable Long funnelId,
                                                                      @RequestBody List<Long> stageIds)
            throws ScrmException {
        return OperationResponse.build(opportunityService.reorderStages(funnelId, stageIds));
    }

    // ============================================================
    // 商机管理
    // ============================================================

    /**
     * 创建商机
     *
     * @param dto 商机参数
     * @return 创建后的商机
     */
    @RequirePermission(resource = "scrm_opportunity", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/opportunities")
    public OperationResponse<ScrmOpportunityDto> createOpportunity(@Valid @RequestBody ScrmOpportunityDto dto)
            throws ScrmException {
        return OperationResponse.build(opportunityService.createOpportunity(dto));
    }

    /**
     * 查询商机详情
     *
     * @param id 商机 ID
     * @return 商机详情
     * @throws ScrmException 商机不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_opportunity", action = "read")
    @GetMapping("/opportunities/{id}")
    public OperationResponse<ScrmOpportunityDto> getOpportunity(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(opportunityService.getOpportunity(id));
    }

    /**
     * 更新商机
     *
     * @param id  商机 ID
     * @param dto 商机参数
     * @return 更新后的商机
     */
    @RequirePermission(resource = "scrm_opportunity", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/opportunities/{id}")
    public OperationResponse<ScrmOpportunityDto> updateOpportunity(@PathVariable Long id,
                                                                    @RequestBody ScrmOpportunityDto dto)
            throws ScrmException {
        return OperationResponse.build(opportunityService.updateOpportunity(id, dto));
    }

    /**
     * 删除商机
     *
     * @param id 商机 ID
     * @return 空响应
     * @throws ScrmException 商机不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_opportunity", action = "delete")
    @DeleteMapping("/opportunities/{id}")
    public OperationResponse<Void> deleteOpportunity(@PathVariable Long id) throws ScrmException {
        opportunityService.deleteOpportunity(id);
        return OperationResponse.build();
    }

    /**
     * 分页查询商机, 支持按状态、负责人、客户、漏斗、阶段过滤
     *
     * @param status     状态过滤 (可选)
     * @param ownerId    负责人过滤 (可选)
     * @param customerId 客户 ID 过滤 (可选)
     * @param funnelId   漏斗 ID 过滤 (可选)
     * @param stageId    阶段 ID 过滤 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 商机分页结果
     */
    @RequirePermission(resource = "scrm_opportunity", action = "read")
    @GetMapping({"/opportunities", "/opportunities/list"})
    public OperationResponse<Page<ScrmOpportunityDto>> listOpportunities(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long funnelId,
            @RequestParam(required = false) Long stageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(opportunityService.listOpportunities(
                status, ownerId, customerId, funnelId, stageId, pageable));
    }

    /**
     * 商机阶段推进
     *
     * @param id  商机 ID
     * @param dto 阶段推进请求
     * @return 更新后的商机
     */
    @RequirePermission(resource = "scrm_opportunity", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/opportunities/{id}/change-stage")
    public OperationResponse<ScrmOpportunityDto> changeStage(@PathVariable Long id,
                                                              @Valid @RequestBody ScrmOpportunityStageChangeDto dto)
            throws ScrmException {
        dto.setOpportunityId(id);
        return OperationResponse.build(opportunityService.changeStage(id, dto));
    }

    /**
     * 查询商机阶段变更历史
     *
     * @param id 商机 ID
     * @return 变更历史列表
     */
    @RequirePermission(resource = "scrm_opportunity", action = "read")
    @GetMapping("/opportunities/{id}/history")
    public OperationResponse<List<ScrmOpportunityStageHistoryDto>> getStageHistory(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(opportunityService.getStageHistory(id));
    }

    /**
     * 销售预测: 基于 OPEN 商机的加权金额
     *
     * @param ownerId 负责人过滤 (可选)
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 预测结果 (含汇总与分页明细)
     */
    @RequirePermission(resource = "scrm_opportunity", action = "read")
    @GetMapping("/opportunities/forecast")
    public OperationResponse<Map<String, Object>> getForecast(
            @RequestParam(required = false) String ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(opportunityService.getForecast(ownerId, pageable));
    }
}
