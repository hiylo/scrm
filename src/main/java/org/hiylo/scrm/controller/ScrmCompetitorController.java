/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCompetitorActivityDto;
import org.hiylo.scrm.dto.ScrmCompetitorAnalysisDto;
import org.hiylo.scrm.dto.ScrmCompetitorDto;
import org.hiylo.scrm.dto.ScrmCompetitorProductDto;
import org.hiylo.scrm.dto.ScrmPriceMonitorDto;
import org.hiylo.scrm.entity.ScrmCompetitorActivityEntity;
import org.hiylo.scrm.entity.ScrmCompetitorEntity;
import org.hiylo.scrm.entity.ScrmCompetitorProductEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCompetitorService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 竞品监测控制器。
 * <p>
 * 提供竞品信息管理、产品与价格监测、动态追踪与应对、竞争分析、多维统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/competitors")
@RequiredArgsConstructor
public class ScrmCompetitorController {

    /** 竞品监测服务 */
    private final ScrmCompetitorService scrmCompetitorService;

    // ============================================================
    // 竞品管理
    // ============================================================

    /**
     * 创建竞品。
     *
     * @param dto 竞品参数
     * @return 创建后的竞品
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_competitor", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmCompetitorEntity> createCompetitor(@Valid @RequestBody ScrmCompetitorDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.createCompetitor(dto));
    }

    /**
     * 更新竞品。
     *
     * @param id  竞品 ID
     * @param dto 竞品参数
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmCompetitorEntity> updateCompetitor(@PathVariable Long id,
                                                                     @RequestBody ScrmCompetitorDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.updateCompetitor(id, dto));
    }

    /**
     * 删除竞品。
     *
     * @param id 竞品 ID
     * @return 空响应
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteCompetitor(@PathVariable Long id) throws ScrmException {
        scrmCompetitorService.deleteCompetitor(id);
        return OperationResponse.build();
    }

    /**
     * 查询竞品详情。
     *
     * @param id 竞品 ID
     * @return 竞品详情
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCompetitorEntity> getCompetitor(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getCompetitor(id));
    }

    /**
     * 按编码查询竞品。
     *
     * @param code 竞品编码
     * @return 竞品详情
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/code/{code}")
    public OperationResponse<ScrmCompetitorEntity> getCompetitorByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getCompetitorByCode(code));
    }

    /**
     * 分页查询竞品列表。
     *
     * @param industry    行业过滤（可空）
     * @param threatLevel 威胁等级过滤（可空）: LOW / MEDIUM / HIGH / CRITICAL
     * @param status      状态过滤（可空）: ACTIVE / INACTIVE / ARCHIVED
     * @param keyword     名称/编码/简称关键字模糊匹配（可空）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 竞品分页结果 (按 updateTime DESC)
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmCompetitorEntity>> listCompetitors(
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String threatLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "updateTime"));
        return OperationResponse.build(scrmCompetitorService.listCompetitors(industry,
                threatLevel, status, keyword, pageable));
    }

    /**
     * 启用竞品监测。
     *
     * @param id 竞品 ID
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @PostMapping("/{id}/monitoring/enable")
    public OperationResponse<ScrmCompetitorEntity> enableMonitoring(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.enableMonitoring(id));
    }

    /**
     * 停止竞品监测。
     *
     * @param id 竞品 ID
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @PostMapping("/{id}/monitoring/disable")
    public OperationResponse<ScrmCompetitorEntity> disableMonitoring(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.disableMonitoring(id));
    }

    /**
     * 更新竞品监测频率。
     *
     * @param id        竞品 ID
     * @param frequency 监测频率: REALTIME / DAILY / WEEKLY / MONTHLY
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在 / 频率非法
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @PostMapping("/{id}/monitoring/frequency")
    public OperationResponse<ScrmCompetitorEntity> updateMonitoringFrequency(
            @PathVariable Long id, @RequestParam String frequency) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.updateMonitoringFrequency(id, frequency));
    }

    /**
     * 更新竞品最近监测时间为当前时间。
     *
     * @param id 竞品 ID
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @PostMapping("/{id}/last-monitored")
    public OperationResponse<ScrmCompetitorEntity> updateLastMonitored(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.updateLastMonitored(id));
    }

    /**
     * 按威胁等级分页查询竞品。
     *
     * @param level 威胁等级: LOW / MEDIUM / HIGH / CRITICAL
     * @param page  页码（从 0 开始, 默认 0）
     * @param size  每页大小（默认 20）
     * @return 竞品分页结果
     * @throws ScrmException 威胁等级非法
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/by-threat/{level}")
    public OperationResponse<Page<ScrmCompetitorEntity>> getCompetitorsByThreat(
            @PathVariable String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return OperationResponse.build(scrmCompetitorService.getCompetitorsByThreat(level, pageable));
    }

    /**
     * 按行业分页查询竞品。
     *
     * @param industry 行业
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 竞品分页结果
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/by-industry/{industry}")
    public OperationResponse<Page<ScrmCompetitorEntity>> getCompetitorsByIndustry(
            @PathVariable String industry,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return OperationResponse.build(scrmCompetitorService.getCompetitorsByIndustry(industry, pageable));
    }

    /**
     * 归档竞品。
     *
     * @param id 竞品 ID
     * @return 更新后的竞品
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @PostMapping("/{id}/archive")
    public OperationResponse<ScrmCompetitorEntity> archiveCompetitor(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.archiveCompetitor(id));
    }

    /**
     * 竞品概要。
     *
     * @param id 竞品 ID
     * @return 概要 Map
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/{id}/summary")
    public OperationResponse<Map<String, Object>> getCompetitorSummary(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getCompetitorSummary(id));
    }

    // ============================================================
    // 产品管理 /products
    // ============================================================

    /**
     * 创建竞品产品。
     *
     * @param dto 产品参数
     * @return 创建后的产品
     * @throws ScrmException 参数非法 / 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/products")
    public OperationResponse<ScrmCompetitorProductEntity> createProduct(
            @Valid @RequestBody ScrmCompetitorProductDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.createProduct(dto));
    }

    /**
     * 更新竞品产品。
     *
     * @param id  产品 ID
     * @param dto 产品参数
     * @return 更新后的产品
     * @throws ScrmException 产品不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/products/{id}")
    public OperationResponse<ScrmCompetitorProductEntity> updateProduct(@PathVariable Long id,
                                                                         @RequestBody ScrmCompetitorProductDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.updateProduct(id, dto));
    }

    /**
     * 删除竞品产品。
     *
     * @param id 产品 ID
     * @return 空响应
     * @throws ScrmException 产品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "delete")
    @DeleteMapping("/products/{id}")
    public OperationResponse<Void> deleteProduct(@PathVariable Long id) throws ScrmException {
        scrmCompetitorService.deleteProduct(id);
        return OperationResponse.build();
    }

    /**
     * 查询产品详情。
     *
     * @param id 产品 ID
     * @return 产品详情
     * @throws ScrmException 产品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/products/{id}")
    public OperationResponse<ScrmCompetitorProductEntity> getProduct(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getProduct(id));
    }

    /**
     * 分页查询产品列表。
     *
     * @param competitorId    竞品 ID 过滤（可空）
     * @param productCategory 分类过滤（可空）
     * @param status          状态过滤（可空）: ACTIVE / INACTIVE / DISCONTINUED
     * @param keyword         产品名称/编码关键字模糊匹配（可空）
     * @param page            页码（从 0 开始, 默认 0）
     * @param size            每页大小（默认 20）
     * @return 产品分页结果 (按 updateTime DESC)
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/products/list")
    public OperationResponse<Page<ScrmCompetitorProductEntity>> listProducts(
            @RequestParam(required = false) Long competitorId,
            @RequestParam(required = false) String productCategory,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return OperationResponse.build(scrmCompetitorService.listProducts(competitorId,
                productCategory, status, keyword, pageable));
    }

    /**
     * 按竞品分页查询产品。
     *
     * @param competitorId 竞品 ID
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 产品分页结果 (按 updateTime DESC)
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/products/by-competitor/{competitorId}")
    public OperationResponse<Page<ScrmCompetitorProductEntity>> getProductsByCompetitor(
            @PathVariable Long competitorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return OperationResponse.build(scrmCompetitorService.getProductsByCompetitor(competitorId, pageable));
    }

    /**
     * 更新产品价格 (追加价格历史 → 计算变化 → 刷新统计 → 生成动态)。
     *
     * @param dto 价格监测参数
     * @return 更新后的产品
     * @throws ScrmException 产品不存在 / 价格非法
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/products/price-update")
    public OperationResponse<ScrmCompetitorProductEntity> updatePrice(@Valid @RequestBody ScrmPriceMonitorDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.updatePrice(dto));
    }

    /**
     * 查询产品价格历史。
     *
     * @param productId 产品 ID
     * @return 价格历史列表 [{date, price, change}]
     * @throws ScrmException 产品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/products/price-history/{productId}")
    public OperationResponse<List<Map<String, Object>>> getPriceHistory(@PathVariable Long productId)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getPriceHistory(productId));
    }

    /**
     * 价格对比: 我方 vs 竞品。
     *
     * @param competitorId 竞品 ID
     * @return 对比结果
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/products/price-comparison/{competitorId}")
    public OperationResponse<Map<String, Object>> getPriceComparison(@PathVariable Long competitorId)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getPriceComparison(competitorId));
    }

    /**
     * 产品对比: 竞品产品与我方产品对比。
     *
     * @param productId    竞品产品 ID
     * @param ourProductId 我方产品 ID
     * @return 对比结果
     * @throws ScrmException 产品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @PostMapping("/products/compare")
    public OperationResponse<Map<String, Object>> compareWithOurProduct(
            @RequestParam Long productId, @RequestParam String ourProductId) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.compareWithOurProduct(productId, ourProductId));
    }

    /**
     * 批量更新价格。
     *
     * @param updates 价格监测参数列表
     * @return 批量结果 {total, success, failed}
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/products/batch-prices")
    public OperationResponse<Map<String, Integer>> batchUpdatePrices(@RequestBody List<ScrmPriceMonitorDto> updates) {
        return OperationResponse.build(scrmCompetitorService.batchUpdatePrices(updates));
    }

    /**
     * 按分类分页查询产品。
     *
     * @param category 分类
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 产品分页结果
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/products/by-category/{category}")
    public OperationResponse<Page<ScrmCompetitorProductEntity>> getProductsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateTime"));
        return OperationResponse.build(scrmCompetitorService.getProductsByCategory(category, pageable));
    }

    /**
     * 促销产品查询 (折扣率大于 0 的在售产品)。
     *
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 产品分页结果 (按折扣率倒序)
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/products/discounted")
    public OperationResponse<Page<ScrmCompetitorProductEntity>> getDiscountedProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmCompetitorService.getDiscountedProducts(pageable));
    }

    /**
     * 近期价格变化产品查询。
     *
     * @param days 回溯天数（默认 30）
     * @return 产品列表 (按最近价格变化日期倒序)
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/products/price-changes/{days}")
    public OperationResponse<List<ScrmCompetitorProductEntity>> getPriceChangeProducts(@PathVariable int days) {
        return OperationResponse.build(scrmCompetitorService.getPriceChangeProducts(days));
    }

    // ============================================================
    // 动态管理 /activities
    // ============================================================

    /**
     * 创建竞品动态。
     *
     * @param dto 动态参数
     * @return 创建后的动态
     * @throws ScrmException 参数非法 / 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/activities")
    public OperationResponse<ScrmCompetitorActivityEntity> createActivity(
            @Valid @RequestBody ScrmCompetitorActivityDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.createActivity(dto));
    }

    /**
     * 更新竞品动态。
     *
     * @param id  动态 ID
     * @param dto 动态参数
     * @return 更新后的动态
     * @throws ScrmException 动态不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/activities/{id}")
    public OperationResponse<ScrmCompetitorActivityEntity> updateActivity(@PathVariable Long id,
                                                                           @RequestBody ScrmCompetitorActivityDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.updateActivity(id, dto));
    }

    /**
     * 删除竞品动态。
     *
     * @param id 动态 ID
     * @return 空响应
     * @throws ScrmException 动态不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "delete")
    @DeleteMapping("/activities/{id}")
    public OperationResponse<Void> deleteActivity(@PathVariable Long id) throws ScrmException {
        scrmCompetitorService.deleteActivity(id);
        return OperationResponse.build();
    }

    /**
     * 查询动态详情。
     *
     * @param id 动态 ID
     * @return 动态详情
     * @throws ScrmException 动态不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/activities/{id}")
    public OperationResponse<ScrmCompetitorActivityEntity> getActivity(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getActivity(id));
    }

    /**
     * 分页查询动态列表。
     *
     * @param competitorId   竞品 ID 过滤（可空）
     * @param activityType   动态类型过滤（可空）
     * @param impactLevel    影响等级过滤（可空）: LOW / MEDIUM / HIGH / CRITICAL
     * @param responseStatus 应对状态过滤（可空）: PENDING / PLANNING / EXECUTING / COMPLETED / NO_ACTION
     * @param startTime      动态日期起始 (可空, ISO 格式: yyyy-MM-dd)
     * @param endTime        动态日期截止 (可空, ISO 格式: yyyy-MM-dd)
     * @param page           页码（从 0 开始, 默认 0）
     * @param size           每页大小（默认 20）
     * @return 动态分页结果 (按 activityDate DESC)
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/activities/list")
    public OperationResponse<Page<ScrmCompetitorActivityEntity>> listActivities(
            @RequestParam(required = false) Long competitorId,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String impactLevel,
            @RequestParam(required = false) String responseStatus,
            @RequestParam(required = false) LocalDate startTime,
            @RequestParam(required = false) LocalDate endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "activityDate"));
        return OperationResponse.build(scrmCompetitorService.listActivities(
                competitorId, activityType, impactLevel, responseStatus, startTime, endTime, pageable));
    }

    /**
     * 按竞品分页查询动态。
     *
     * @param competitorId 竞品 ID
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 动态分页结果 (按 activityDate DESC)
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/activities/by-competitor/{competitorId}")
    public OperationResponse<Page<ScrmCompetitorActivityEntity>> getActivitiesByCompetitor(
            @PathVariable Long competitorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "activityDate"));
        return OperationResponse.build(scrmCompetitorService.getActivitiesByCompetitor(competitorId, pageable));
    }

    /**
     * 按动态类型分页查询。
     *
     * @param activityType 动态类型
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 动态分页结果 (按 activityDate DESC)
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/activities/by-type/{activityType}")
    public OperationResponse<Page<ScrmCompetitorActivityEntity>> getActivitiesByType(
            @PathVariable String activityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "activityDate"));
        return OperationResponse.build(scrmCompetitorService.getActivitiesByType(activityType, pageable));
    }

    /**
     * 近期动态查询。
     *
     * @param days 回溯天数
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 动态分页结果 (按 activityDate DESC)
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/activities/recent/{days}")
    public OperationResponse<Page<ScrmCompetitorActivityEntity>> getRecentActivities(
            @PathVariable int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "activityDate"));
        return OperationResponse.build(scrmCompetitorService.getRecentActivities(days, pageable));
    }

    /**
     * 重大动态查询 (影响等级 HIGH / CRITICAL)。
     *
     * @param limit 返回条数（默认 20）
     * @return 动态列表 (按重要性评分倒序)
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/activities/critical")
    public OperationResponse<List<ScrmCompetitorActivityEntity>> getCriticalActivities(
            @RequestParam(defaultValue = "20") int limit) {
        return OperationResponse.build(scrmCompetitorService.getCriticalActivities(limit));
    }

    /**
     * 待响应动态查询 (responseStatus = PENDING)。
     *
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 动态分页结果 (按重要性评分倒序)
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/activities/pending-responses")
    public OperationResponse<Page<ScrmCompetitorActivityEntity>> getPendingResponses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmCompetitorService.getPendingResponses(pageable));
    }

    /**
     * 制定应对策略。
     *
     * @param id       动态 ID
     * @param response 应对策略
     * @param owner    应对负责人
     * @param dueDate  应对截止日期 (可空, ISO 格式: yyyy-MM-dd)
     * @return 更新后的动态
     * @throws ScrmException 动态不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @PostMapping("/activities/{id}/plan-response")
    public OperationResponse<ScrmCompetitorActivityEntity> planResponse(
            @PathVariable Long id,
            @RequestParam String response,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) LocalDate dueDate) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.planResponse(id, response, owner, dueDate));
    }

    /**
     * 执行应对。
     *
     * @param id 动态 ID
     * @return 更新后的动态
     * @throws ScrmException 动态不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @PostMapping("/activities/{id}/execute-response")
    public OperationResponse<ScrmCompetitorActivityEntity> executeResponse(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.executeResponse(id));
    }

    /**
     * 完成应对。
     *
     * @param id      动态 ID
     * @param outcome 应对结果
     * @return 更新后的动态
     * @throws ScrmException 动态不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @PostMapping("/activities/{id}/complete-response")
    public OperationResponse<ScrmCompetitorActivityEntity> completeResponse(
            @PathVariable Long id, @RequestParam(required = false) String outcome) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.completeResponse(id, outcome));
    }

    /**
     * 验证动态。
     *
     * @param id          动态 ID
     * @param verifierId 验证人 ID (可空, 缺省取当前用户)
     * @return 更新后的动态
     * @throws ScrmException 动态不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @PostMapping("/activities/{id}/verify")
    public OperationResponse<ScrmCompetitorActivityEntity> verifyActivity(
            @PathVariable Long id, @RequestParam(required = false) String verifierId) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.verifyActivity(id, verifierId));
    }

    /**
     * 更新动态重要性评分。
     *
     * @param id    动态 ID
     * @param score 重要性评分 (0-100)
     * @return 更新后的动态
     * @throws ScrmException 动态不存在 / 评分越界
     */
    @RequirePermission(resource = "scrm_competitor", action = "update")
    @PostMapping("/activities/{id}/importance")
    public OperationResponse<ScrmCompetitorActivityEntity> updateImportance(
            @PathVariable Long id, @RequestParam int score) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.updateImportance(id, score));
    }

    /**
     * 动态时间线 (按竞品聚合最近 months 个月的动态)。
     *
     * @param competitorId 竞品 ID
     * @param months       回溯月数（默认 6）
     * @return 动态列表 (按动态日期倒序)
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/activities/timeline/{competitorId}")
    public OperationResponse<List<ScrmCompetitorActivityEntity>> getActivityTimeline(
            @PathVariable Long competitorId,
            @RequestParam(defaultValue = "6") int months) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getActivityTimeline(competitorId, months));
    }

    // ============================================================
    // 竞争分析 /analysis
    // ============================================================

    /**
     * 竞争分析 (价格策略 / 产品矩阵 / 市场定位 / 动态频次 / 威胁评估 / 完整报告)。
     *
     * @param dto 分析请求
     * @return 分析报告 Map
     * @throws ScrmException 竞品不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @PostMapping("/analysis/competitor")
    public OperationResponse<Map<String, Object>> analyzeCompetitor(@Valid @RequestBody ScrmCompetitorAnalysisDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.analyzeCompetitor(dto));
    }

    /**
     * 多竞品对比。
     *
     * @param competitorIds 竞品 ID 列表
     * @return 对比结果
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @PostMapping("/analysis/compare")
    public OperationResponse<Map<String, Object>> compareCompetitors(@RequestBody List<Long> competitorIds)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.compareCompetitors(competitorIds));
    }

    /**
     * 市场概览。
     *
     * @param industry 行业过滤（可空, 为空则全部数据）
     * @return 市场概览 Map
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/analysis/market")
    public OperationResponse<Map<String, Object>> getMarketOverview(@RequestParam(required = false) String industry) {
        return OperationResponse.build(scrmCompetitorService.getMarketOverview(industry));
    }

    /**
     * 价格分析。
     *
     * @param competitorId 竞品 ID
     * @param months       回溯月数（默认 6）
     * @return 价格分析 Map
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/analysis/price")
    public OperationResponse<Map<String, Object>> getPriceAnalysis(
            @RequestParam Long competitorId, @RequestParam(defaultValue = "6") int months) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getPriceAnalysis(competitorId, months));
    }

    /**
     * 动态分析。
     *
     * @param competitorId 竞品 ID
     * @param months       回溯月数（默认 6）
     * @return 动态分析 Map
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/analysis/activity")
    public OperationResponse<Map<String, Object>> getActivityAnalysis(
            @RequestParam Long competitorId, @RequestParam(defaultValue = "6") int months) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getActivityAnalysis(competitorId, months));
    }

    /**
     * 威胁评估。
     *
     * @param competitorId 竞品 ID
     * @return 威胁评估 Map
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @PostMapping("/analysis/threat")
    public OperationResponse<Map<String, Object>> getThreatAssessment(@RequestParam Long competitorId)
            throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getThreatAssessment(competitorId));
    }

    /**
     * 趋势分析 (按月聚合动态与价格变化)。
     *
     * @param competitorId 竞品 ID
     * @param months       回溯月数（默认 6）
     * @return 趋势分析 Map
     * @throws ScrmException 竞品不存在
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/analysis/trend")
    public OperationResponse<Map<String, Object>> getTrendAnalysis(
            @RequestParam Long competitorId, @RequestParam(defaultValue = "6") int months) throws ScrmException {
        return OperationResponse.build(scrmCompetitorService.getTrendAnalysis(competitorId, months));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 竞品统计概览: 总数 / 各行业 / 各威胁等级 / 监测状态 / 状态分布。
     *
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getCompetitorStats() {
        return OperationResponse.build(scrmCompetitorService.getCompetitorStats());
    }

    /**
     * 动态统计: 各类型 / 各影响等级 / 应对率。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/stats/activities")
    public OperationResponse<Map<String, Object>> getActivityStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmCompetitorService.getActivityStats(startTime, endTime));
    }

    /**
     * 价格统计: 价格变化频率 / 平均涨跌幅。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/stats/prices")
    public OperationResponse<Map<String, Object>> getPriceStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmCompetitorService.getPriceStats(startTime, endTime));
    }

    /**
     * 应对统计: 应对率 / 平均响应时间。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/stats/responses")
    public OperationResponse<Map<String, Object>> getResponseStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmCompetitorService.getResponseStats(startTime, endTime));
    }

    /**
     * 竞品趋势: 按月统计新增竞品数。
     *
     * @param months 回溯月数（默认 12）
     * @return 趋势结果 Map
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/stats/competitor-trend")
    public OperationResponse<Map<String, Object>> getCompetitorTrend(
            @RequestParam(defaultValue = "12") int months) {
        return OperationResponse.build(scrmCompetitorService.getCompetitorTrend(months));
    }

    /**
     * 动态趋势: 按天统计动态数。
     *
     * @param days 回溯天数（默认 30）
     * @return 趋势结果 Map
     */
    @RequirePermission(resource = "scrm_competitor", action = "read")
    @GetMapping("/stats/activity-trend")
    public OperationResponse<Map<String, Object>> getActivityTrend(
            @RequestParam(defaultValue = "30") int days) {
        return OperationResponse.build(scrmCompetitorService.getActivityTrend(days));
    }
}
