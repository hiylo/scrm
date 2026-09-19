/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCouponDto;
import org.hiylo.scrm.dto.ScrmCouponIssueDto;
import org.hiylo.scrm.dto.ScrmCouponTemplateDto;
import org.hiylo.scrm.dto.ScrmCouponUsageLogDto;
import org.hiylo.scrm.dto.ScrmCouponUseDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCouponService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 * SCRM 优惠券/卡券管理控制器。
 * <p>
 * 提供优惠券模板管理 (增删改查/启停/统计)、批量发券与单客户发券、领取与核销、退还与过期处理、
 * 优惠券与使用日志查询以及优惠券统计接口。权限由 gateway-server 统一鉴权,
 * {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/coupons")
@RequiredArgsConstructor
public class ScrmCouponController {

    /** 优惠券服务 */
    private final ScrmCouponService scrmCouponService;

    // ============================================================
    // 模板管理 /templates
    // ============================================================

    /**
     * 创建优惠券模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_coupon", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建模板过于频繁，请稍后重试")
    @PostMapping("/templates")
    public OperationResponse<ScrmCouponTemplateDto> createTemplate(@Valid @RequestBody ScrmCouponTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCouponService.createTemplate(dto));
    }

    /**
     * 更新优惠券模板 (字段非空才覆盖)。
     * <p>已发放量大于 0 时不允许修改优惠券类型与面值。</p>
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法 / 已发行券不允许修改关键参数
     */
    @RequirePermission(resource = "scrm_coupon", action = "update")
    @PutMapping("/templates/{id}")
    public OperationResponse<ScrmCouponTemplateDto> updateTemplate(@PathVariable Long id,
                                                                    @RequestBody ScrmCouponTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmCouponService.updateTemplate(id, dto));
    }

    /**
     * 删除优惠券模板。
     * <p>已有券实例的模板不允许删除, 请先停用。</p>
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在 / 已发行券不允许删除
     */
    @RequirePermission(resource = "scrm_coupon", action = "delete")
    @DeleteMapping("/templates/{id}")
    public OperationResponse<Void> deleteTemplate(@PathVariable Long id) throws ScrmException {
        scrmCouponService.deleteTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 查询优惠券模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_coupon", action = "read")
    @GetMapping("/templates/{id}")
    public OperationResponse<ScrmCouponTemplateDto> getTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCouponService.getTemplate(id));
    }

    /**
     * 分页查询优惠券模板, 支持按优惠券类型、状态与关键词过滤。
     *
     * @param couponType 优惠券类型过滤 (可空): DISCOUNT / FIXED_AMOUNT / EXCHANGE / GIFT / CASH_VOUCHER
     * @param status     状态过滤 (可空): ACTIVE / INACTIVE / EXPIRED / SOLD_OUT
     * @param keyword    关键词过滤, 匹配模板名称 (可空)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 模板分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_coupon", action = "read")
    @GetMapping("/templates/list")
    public OperationResponse<Page<ScrmCouponTemplateDto>> listTemplates(
            @RequestParam(required = false) String couponType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmCouponService.listTemplates(couponType, status, keyword, pageable));
    }

    /**
     * 启用优惠券模板 (状态置 ACTIVE)。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_coupon", action = "update")
    @PostMapping("/templates/{id}/activate")
    public OperationResponse<ScrmCouponTemplateDto> activateTemplate(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCouponService.activateTemplate(id));
    }

    /**
     * 停用优惠券模板 (状态置 INACTIVE), 已发放券不受影响。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_coupon", action = "update")
    @PostMapping("/templates/{id}/deactivate")
    public OperationResponse<ScrmCouponTemplateDto> deactivateTemplate(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmCouponService.deactivateTemplate(id));
    }

    /**
     * 模板统计: 发放/领取/使用/过期数与总抵扣金额。
     *
     * @param id 模板 ID
     * @return 统计结果
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_coupon", action = "read")
    @GetMapping("/templates/{id}/stats")
    public OperationResponse<Map<String, Object>> getTemplateStats(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCouponService.getTemplateStats(id));
    }

    // ============================================================
    // 发券 /issue
    // ============================================================

    /**
     * 批量发券: 生成券码 → 分配客户 → 记录日志。
     * <p>
     * customerIds 非空时为每个客户发放 issueCount 张券; 为空时发放 issueCount 张未归属券 (待领取)。
     * </p>
     *
     * @param issueDto 发券请求
     * @return 发放的优惠券列表
     * @throws ScrmException 模板不存在 / 模板未启用 / 库存不足
     */
    @RequirePermission(resource = "scrm_coupon", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60, message = "发券操作过于频繁，请稍后重试")
    @PostMapping("/issue")
    public OperationResponse<List<ScrmCouponDto>> issueCoupons(@Valid @RequestBody ScrmCouponIssueDto issueDto)
            throws ScrmException {
        return OperationResponse.build(scrmCouponService.issueCoupons(issueDto));
    }

    /**
     * 发券给单客户。
     *
     * @param templateId 模板 ID
     * @param customerId 客户 ID
     * @param count      发放数量 (默认 1)
     * @return 发放的优惠券列表
     * @throws ScrmException 模板不存在 / 模板未启用 / 库存不足
     */
    @RequirePermission(resource = "scrm_coupon", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/issue/customer")
    public OperationResponse<List<ScrmCouponDto>> issueToCustomer(
            @RequestParam Long templateId,
            @RequestParam Long customerId,
            @RequestParam(defaultValue = "1") int count) throws ScrmException {
        return OperationResponse.build(scrmCouponService.issueToCustomer(templateId, customerId, count));
    }

    // ============================================================
    // 领取 / 使用 / 退还 / 过期
    // ============================================================

    /**
     * 领取优惠券: 将未归属券分配给客户。
     * <p>校验券状态为 UNUSED 且未归属, 校验客户每人限领数量。</p>
     *
     * @param couponCode 优惠券码
     * @param customerId 客户 ID
     * @return 更新后的优惠券
     * @throws ScrmException 优惠券不存在 / 状态非法 / 已被领取 / 超出每人限领
     */
    @RequirePermission(resource = "scrm_coupon", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/claim")
    public OperationResponse<ScrmCouponDto> claimCoupon(
            @RequestParam String couponCode,
            @RequestParam Long customerId) throws ScrmException {
        return OperationResponse.build(scrmCouponService.claimCoupon(couponCode, customerId));
    }

    /**
     * 使用优惠券 (核销): 校验 → 计算抵扣 → 标记已用 → 记录日志。
     *
     * @param useDto 使用请求 (券码 + 订单号 + 订单金额)
     * @return 更新后的优惠券
     * @throws ScrmException 优惠券不存在 / 状态非法 / 已过期 / 未达使用门槛 / 未领取
     */
    @RequirePermission(resource = "scrm_coupon", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/use")
    public OperationResponse<ScrmCouponDto> useCoupon(@Valid @RequestBody ScrmCouponUseDto useDto)
            throws ScrmException {
        return OperationResponse.build(scrmCouponService.useCoupon(useDto));
    }

    /**
     * 退还优惠券 (状态置 RETURNED)。
     * <p>仅 USED 状态可退还, 退还后券可重新核销。</p>
     *
     * @param id     优惠券 ID
     * @param reason 退还原因 (可空)
     * @return 更新后的优惠券
     * @throws ScrmException 优惠券不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_coupon", action = "execute")
    @PostMapping("/{id}/return")
    public OperationResponse<ScrmCouponDto> returnCoupon(@PathVariable Long id,
                                                          @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmCouponService.returnCoupon(id, reason));
    }

    /**
     * 过期处理 (定时任务调用): 将过期未使用券置为 EXPIRED。
     *
     * @return 过期处理的优惠券数量
     */
    @RequirePermission(resource = "scrm_coupon", action = "execute")
    @RateLimit(capacity = 1, refillTokens = 1, refillPeriodSeconds = 60)
    @PostMapping("/expire")
    public OperationResponse<Integer> expireCoupons() {
        return OperationResponse.build(scrmCouponService.expireCoupons());
    }

    // ============================================================
    // 优惠券查询
    // ============================================================

    /**
     * 查询优惠券详情。
     *
     * @param id 优惠券 ID
     * @return 优惠券详情
     * @throws ScrmException 优惠券不存在
     */
    @RequirePermission(resource = "scrm_coupon", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCouponDto> getCoupon(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmCouponService.getCoupon(id));
    }

    /**
     * 按券码查询优惠券。
     *
     * @param code 优惠券码
     * @return 优惠券详情
     * @throws ScrmException 优惠券不存在
     */
    @RequirePermission(resource = "scrm_coupon", action = "read")
    @GetMapping("/code/{code}")
    public OperationResponse<ScrmCouponDto> getCouponByCode(@PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmCouponService.getCouponByCode(code));
    }

    /**
     * 分页查询优惠券, 支持按模板、客户、状态与时间范围过滤。
     *
     * @param templateId 模板 ID 过滤 (可空)
     * @param customerId 客户 ID 过滤 (可空)
     * @param status     状态过滤 (可空): UNUSED / USED / EXPIRED / RETURNED
     * @param startTime  起始时间 (按创建时间, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    截止时间 (按创建时间, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 优惠券分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_coupon", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmCouponDto>> listCoupons(
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmCouponService.listCoupons(templateId, customerId, status,
                startTime, endTime, pageable));
    }

    /**
     * 查询客户优惠券列表, 支持按状态过滤。
     *
     * @param customerId 客户 ID
     * @param status     状态过滤 (可空): UNUSED / USED / EXPIRED / RETURNED
     * @return 优惠券列表 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_coupon", action = "read")
    @GetMapping("/customer/{customerId}")
    public OperationResponse<List<ScrmCouponDto>> getCustomerCoupons(
            @PathVariable Long customerId,
            @RequestParam(required = false) String status) {
        return OperationResponse.build(scrmCouponService.getCustomerCoupons(customerId, status));
    }

    /**
     * 分页查询优惠券使用日志, 支持按券 ID、模板 ID 与动作类型过滤。
     *
     * @param couponId   优惠券 ID 过滤 (可空)
     * @param templateId 模板 ID 过滤 (可空)
     * @param actionType 动作类型过滤 (可空): ISSUE / CLAIM / USE / RETURN / EXPIRE
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 使用日志分页结果 (按动作时间倒序)
     */
    @RequirePermission(resource = "scrm_coupon", action = "read")
    @GetMapping("/logs/list")
    public OperationResponse<Page<ScrmCouponUsageLogDto>> getUsageLogs(
            @RequestParam(required = false) Long couponId,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "actionTime"));
        return OperationResponse.build(scrmCouponService.getUsageLogs(couponId, templateId, actionType, pageable));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 优惠券统计: 发放率 / 使用率 / 核销率 / 总抵扣金额。
     * <p>时间范围按优惠券创建时间过滤, 为空时统计全量。</p>
     *
     * @param templateId 模板 ID
     * @param startTime  起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_coupon", action = "read")
    @GetMapping("/stats")
    public OperationResponse<Map<String, Object>> getCouponStats(
            @RequestParam Long templateId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime)
            throws ScrmException {
        return OperationResponse.build(scrmCouponService.getCouponStats(templateId, startTime, endTime));
    }

    /**
     * 客户优惠券统计: 各状态持有数量。
     *
     * @param customerId 客户 ID
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_coupon", action = "read")
    @GetMapping("/customer/{customerId}/stats")
    public OperationResponse<Map<String, Object>> getCustomerCouponStats(@PathVariable Long customerId) {
        return OperationResponse.build(scrmCouponService.getCustomerCouponStats(customerId));
    }
}
