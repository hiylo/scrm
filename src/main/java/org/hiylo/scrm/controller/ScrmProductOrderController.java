/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProductOrderController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmOrderCreateDto;
import org.hiylo.scrm.dto.ScrmOrderDto;
import org.hiylo.scrm.dto.ScrmOrderItemDto;
import org.hiylo.scrm.dto.ScrmProductDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmProductOrderService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 商品/订单管理控制器。
 * <p>
 * 提供商品管理 (增删改查 / 编码查询 / 库存调整 / 状态更新 / 批量导入)、订单管理 (创建 / 更新 /
 * 删除 / 查询 / 状态流转: 确认 / 支付 / 发货 / 送达 / 完成 / 取消 / 退款)、订单项管理 (增删改查)
 * 以及多维度统计 (商品统计 / 订单统计 / 客户购买历史 / 热销商品 / 销售趋势 / 渠道收入) 接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 * <p>
 * 本控制器承载三个基础路径: {@code /scrm/products} (商品)、{@code /scrm/orders} (订单) 与
 * {@code /scrm/products-orders/stats} (统计), 各方法显式声明完整路径。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ScrmProductOrderController {

    /** 权限资源标识 (商品) */
    private static final String RESOURCE_PRODUCT = "scrm_product";
    /** 权限资源标识 (订单) */
    private static final String RESOURCE_ORDER = "scrm_order";

    /** 商品/订单服务 */
    private final ScrmProductOrderService scrmProductOrderService;

    // ============================================================
    // 商品管理 /scrm/products
    // ============================================================

    /**
     * 创建商品。
     *
     * @param dto 商品参数
     * @return 创建后的商品
     * @throws ScrmException 参数非法 / 商品编码已存在
     */
    @RequirePermission(resource = RESOURCE_PRODUCT, action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建商品过于频繁，请稍后重试")
    @PostMapping("/scrm/products")
    public OperationResponse<ScrmProductDto> createProduct(@Valid @RequestBody ScrmProductDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.createProduct(dto));
    }

    /**
     * 更新商品（字段非空才覆盖）。
     *
     * @param id  商品 ID
     * @param dto 商品参数
     * @return 更新后的商品
     * @throws ScrmException 商品不存在 / 商品编码已存在
     */
    @RequirePermission(resource = RESOURCE_PRODUCT, action = "update")
    @PutMapping("/scrm/products/{id}")
    public OperationResponse<ScrmProductDto> updateProduct(@PathVariable Long id,
                                                             @RequestBody ScrmProductDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.updateProduct(id, dto));
    }

    /**
     * 删除商品 (仅已下架 / 停产商品可删除)。
     *
     * @param id 商品 ID
     * @return 空响应
     * @throws ScrmException 商品不存在 / 在售商品不允许删除
     */
    @RequirePermission(resource = RESOURCE_PRODUCT, action = "delete")
    @DeleteMapping("/scrm/products/{id}")
    public OperationResponse<Void> deleteProduct(@PathVariable Long id) throws ScrmException {
        scrmProductOrderService.deleteProduct(id);
        return OperationResponse.build();
    }

    /**
     * 查询商品详情。
     *
     * @param id 商品 ID
     * @return 商品详情
     * @throws ScrmException 商品不存在
     */
    @RequirePermission(resource = RESOURCE_PRODUCT, action = "read")
    @GetMapping("/scrm/products/{id}")
    public OperationResponse<ScrmProductDto> getProduct(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.getProduct(id));
    }

    /**
     * 按商品编码查询商品。
     *
     * @param code 商品编码
     * @return 商品详情
     * @throws ScrmException 商品不存在
     */
    @RequirePermission(resource = RESOURCE_PRODUCT, action = "read")
    @GetMapping("/scrm/products/code/{code}")
    public OperationResponse<ScrmProductDto> getProductByCode(@PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.getProductByCode(code));
    }

    /**
     * 分页查询商品, 支持按分类、品牌、状态与关键词过滤。
     *
     * @param category 分类过滤 (可空)
     * @param brand    品牌过滤 (可空)
     * @param status   状态过滤 (可空): ACTIVE / INACTIVE / DISCONTINUED
     * @param keyword  关键词过滤, 匹配商品编码 / 名称 / SKU / 条码 (可空)
     * @param page     页码 (从 0 开始, 默认 0)
     * @param size     每页大小 (默认 20)
     * @return 商品分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = RESOURCE_PRODUCT, action = "read")
    @GetMapping("/scrm/products/list")
    public OperationResponse<Page<ScrmProductDto>> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(
                scrmProductOrderService.listProducts(category, brand, status, keyword, pageable));
    }

    /**
     * 调整商品库存 (delta 可正可负)。
     *
     * @param id     商品 ID
     * @param delta  库存变化量 (正入库 / 负出库)
     * @param reason 调整原因 (可空)
     * @return 更新后的商品
     * @throws ScrmException 商品不存在 / 库存不足
     */
    @RequirePermission(resource = RESOURCE_PRODUCT, action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/scrm/products/{id}/stock")
    public OperationResponse<ScrmProductDto> adjustStock(@PathVariable Long id,
                                                           @RequestParam int delta,
                                                           @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.adjustStock(id, delta, reason));
    }

    /**
     * 更新商品状态。
     *
     * @param id     商品 ID
     * @param status 目标状态: ACTIVE / INACTIVE / DISCONTINUED
     * @return 更新后的商品
     * @throws ScrmException 商品不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE_PRODUCT, action = "update")
    @PostMapping("/scrm/products/{id}/status")
    public OperationResponse<ScrmProductDto> updateProductStatus(@PathVariable Long id,
                                                                    @RequestParam String status)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.updateStatus(id, status));
    }

    /**
     * 批量导入商品 (已存在的商品编码跳过)。
     *
     * @param products 商品参数列表
     * @return 导入成功的商品列表
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = RESOURCE_PRODUCT, action = "create")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60, message = "批量导入过于频繁，请稍后重试")
    @PostMapping("/scrm/products/batch-import")
    public OperationResponse<List<ScrmProductDto>> batchImportProducts(
            @Valid @RequestBody List<ScrmProductDto> products)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.batchImport(products));
    }

    // ============================================================
    // 订单管理 /scrm/orders
    // ============================================================

    /**
     * 创建订单: 生成订单号 → 计算金额 → 创建订单项 → 扣库存。
     *
     * @param createDto 创建请求
     * @return 创建后的订单 (含订单项)
     * @throws ScrmException 参数非法 / 商品不存在 / 库存不足
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建订单过于频繁，请稍后重试")
    @PostMapping("/scrm/orders")
    public OperationResponse<ScrmOrderDto> createOrder(@Valid @RequestBody ScrmOrderCreateDto createDto)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.createOrder(createDto));
    }

    /**
     * 更新订单（字段非空才覆盖）。
     *
     * @param id  订单 ID
     * @param dto 订单参数
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法不允许修改
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "update")
    @PutMapping("/scrm/orders/{id}")
    public OperationResponse<ScrmOrderDto> updateOrder(@PathVariable Long id, @RequestBody ScrmOrderDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.updateOrder(id, dto));
    }

    /**
     * 删除订单及其订单项 (仅 PENDING / CANCELLED 状态可删除)。
     *
     * @param id 订单 ID
     * @return 空响应
     * @throws ScrmException 订单不存在 / 状态非法不允许删除
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "delete")
    @DeleteMapping("/scrm/orders/{id}")
    public OperationResponse<Void> deleteOrder(@PathVariable Long id) throws ScrmException {
        scrmProductOrderService.deleteOrder(id);
        return OperationResponse.build();
    }

    /**
     * 查询订单详情 (含订单项)。
     *
     * @param id 订单 ID
     * @return 订单详情
     * @throws ScrmException 订单不存在
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "read")
    @GetMapping("/scrm/orders/{id}")
    public OperationResponse<ScrmOrderDto> getOrder(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.getOrder(id));
    }

    /**
     * 按订单编号查询订单 (含订单项)。
     *
     * @param orderNo 订单编号
     * @return 订单详情
     * @throws ScrmException 订单不存在
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "read")
    @GetMapping("/scrm/orders/no/{orderNo}")
    public OperationResponse<ScrmOrderDto> getOrderByNo(@PathVariable String orderNo) throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.getOrderByNo(orderNo));
    }

    /**
     * 分页查询订单, 支持按客户、订单状态、支付状态、订单类型、时间范围与关键词过滤。
     *
     * @param customerId    客户 ID 过滤 (可空)
     * @param orderStatus   订单状态过滤 (可空)
     * @param paymentStatus 支付状态过滤 (可空)
     * @param orderType     订单类型过滤 (可空)
     * @param startTime     起始时间 (按创建时间, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime       截止时间 (按创建时间, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param keyword       关键词过滤, 匹配订单编号 / 客户名称 / 收件人 (可空)
     * @param page          页码 (从 0 开始, 默认 0)
     * @param size          每页大小 (默认 20)
     * @return 订单分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "read")
    @GetMapping("/scrm/orders/list")
    public OperationResponse<Page<ScrmOrderDto>> listOrders(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmProductOrderService.listOrders(customerId, orderStatus, paymentStatus,
                orderType, startTime, endTime, keyword, pageable));
    }

    /**
     * 确认订单 (PENDING → CONFIRMED)。
     *
     * @param id 订单 ID
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/scrm/orders/{id}/confirm")
    public OperationResponse<ScrmOrderDto> confirmOrder(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.confirmOrder(id));
    }

    /**
     * 支付订单。
     *
     * @param id            订单 ID
     * @param paymentMethod 支付方式: WECHAT / ALIPAY / BANK / CARD / COD / OTHER
     * @param paidAmount    本次支付金额
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法 / 支付金额非法
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/scrm/orders/{id}/pay")
    public OperationResponse<ScrmOrderDto> payOrder(@PathVariable Long id,
                                                     @RequestParam String paymentMethod,
                                                     @RequestParam Double paidAmount) throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.payOrder(id, paymentMethod, paidAmount));
    }

    /**
     * 发货 (PAID / CONFIRMED → SHIPPED)。
     *
     * @param id              订单 ID
     * @param trackingNo      物流单号
     * @param trackingCompany 物流公司 (可空)
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/scrm/orders/{id}/ship")
    public OperationResponse<ScrmOrderDto> shipOrder(@PathVariable Long id,
                                                       @RequestParam String trackingNo,
                                                       @RequestParam(required = false) String trackingCompany)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.shipOrder(id, trackingNo, trackingCompany));
    }

    /**
     * 确认送达 (SHIPPED → DELIVERED)。
     *
     * @param id 订单 ID
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "execute")
    @PostMapping("/scrm/orders/{id}/deliver")
    public OperationResponse<ScrmOrderDto> deliverOrder(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.deliverOrder(id));
    }

    /**
     * 完成订单 (DELIVERED / PAID / SHIPPED → COMPLETED)。
     *
     * @param id 订单 ID
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "execute")
    @PostMapping("/scrm/orders/{id}/complete")
    public OperationResponse<ScrmOrderDto> completeOrder(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.completeOrder(id));
    }

    /**
     * 取消订单 (PENDING / CONFIRMED → CANCELLED, 回补库存)。
     *
     * @param id     订单 ID
     * @param reason 取消原因 (可空)
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/scrm/orders/{id}/cancel")
    public OperationResponse<ScrmOrderDto> cancelOrder(@PathVariable Long id,
                                                        @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.cancelOrder(id, reason));
    }

    /**
     * 退款 (PAID / SHIPPED / DELIVERED / COMPLETED → REFUNDED, 回补库存)。
     *
     * @param id     订单 ID
     * @param reason 退款原因 (可空)
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/scrm/orders/{id}/refund")
    public OperationResponse<ScrmOrderDto> refundOrder(@PathVariable Long id,
                                                        @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.refundOrder(id, reason));
    }

    // ============================================================
    // 订单项管理 /scrm/orders/{orderId}/items
    // ============================================================

    /**
     * 查询订单项列表。
     *
     * @param orderId 订单 ID
     * @return 订单项列表
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "read")
    @GetMapping("/scrm/orders/{orderId}/items")
    public OperationResponse<List<ScrmOrderItemDto>> listOrderItems(@PathVariable Long orderId) {
        return OperationResponse.build(scrmProductOrderService.listOrderItems(orderId));
    }

    /**
     * 添加订单项 (仅 PENDING / CONFIRMED 状态可添加, 添加后重算订单金额)。
     *
     * @param orderId 订单 ID
     * @param dto     订单项参数
     * @return 添加后的订单项
     * @throws ScrmException 订单不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "update")
    @PostMapping("/scrm/orders/{orderId}/items")
    public OperationResponse<ScrmOrderItemDto> addOrderItem(@PathVariable Long orderId,
                                                              @Valid @RequestBody ScrmOrderItemDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.addOrderItem(orderId, dto));
    }

    /**
     * 更新订单项 (字段非空才覆盖, 仅 PENDING / CONFIRMED 状态可更新, 更新后重算订单金额)。
     *
     * @param id  订单项 ID
     * @param dto 订单项参数
     * @return 更新后的订单项
     * @throws ScrmException 订单项不存在 / 订单状态非法
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "update")
    @PutMapping("/scrm/orders/items/{id}")
    public OperationResponse<ScrmOrderItemDto> updateOrderItem(@PathVariable Long id,
                                                                  @RequestBody ScrmOrderItemDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmProductOrderService.updateOrderItem(id, dto));
    }

    /**
     * 移除订单项 (仅 PENDING / CONFIRMED 状态可移除, 移除后重算订单金额)。
     *
     * @param id 订单项 ID
     * @return 空响应
     * @throws ScrmException 订单项不存在 / 订单状态非法
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "update")
    @DeleteMapping("/scrm/orders/items/{id}")
    public OperationResponse<Void> removeOrderItem(@PathVariable Long id) throws ScrmException {
        scrmProductOrderService.removeOrderItem(id);
        return OperationResponse.build();
    }

    // ============================================================
    // 统计 /scrm/products-orders/stats
    // ============================================================

    /**
     * 商品统计: 商品总数 / 各状态数 / 库存总量 / 销量总量 / 评分分布。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = RESOURCE_PRODUCT, action = "read")
    @GetMapping("/scrm/products-orders/stats/products")
    public OperationResponse<Map<String, Object>> getProductStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmProductOrderService.getProductStats(startTime, endTime));
    }

    /**
     * 订单统计: 订单总数 / 各状态数 / 总金额 / 已付金额 / 转化率。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "read")
    @GetMapping("/scrm/products-orders/stats/orders")
    public OperationResponse<Map<String, Object>> getOrderStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmProductOrderService.getOrderStats(startTime, endTime));
    }

    /**
     * 客户购买历史: 订单列表与汇总。
     *
     * @param customerId 客户 ID
     * @return 购买历史 Map
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "read")
    @GetMapping("/scrm/products-orders/stats/customer/{customerId}/history")
    public OperationResponse<Map<String, Object>> getCustomerPurchaseHistory(@PathVariable Long customerId) {
        return OperationResponse.build(scrmProductOrderService.getCustomerPurchaseHistory(customerId));
    }

    /**
     * 热销商品 (按销量排序)。
     *
     * @param limit     返回条数 (默认 10)
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 热销商品列表
     */
    @RequirePermission(resource = RESOURCE_PRODUCT, action = "read")
    @GetMapping("/scrm/products-orders/stats/top-products")
    public OperationResponse<List<Map<String, Object>>> getTopProducts(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmProductOrderService.getTopProducts(limit, startTime, endTime));
    }

    /**
     * 销售趋势 (按日聚合)。
     *
     * @param days 天数 (默认 30, 最大 365)
     * @return 趋势列表: [{date, orderCount, revenue}]
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "read")
    @GetMapping("/scrm/products-orders/stats/sales-trend")
    public OperationResponse<List<Map<String, Object>>> getSalesTrend(
            @RequestParam(required = false) Integer days) {
        return OperationResponse.build(scrmProductOrderService.getSalesTrend(days));
    }

    /**
     * 渠道收入 (按下单渠道聚合)。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 渠道收入列表: [{channel, orderCount, revenue}]
     */
    @RequirePermission(resource = RESOURCE_ORDER, action = "read")
    @GetMapping("/scrm/products-orders/stats/revenue-by-channel")
    public OperationResponse<List<Map<String, Object>>> getRevenueByChannel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmProductOrderService.getRevenueByChannel(startTime, endTime));
    }
}
