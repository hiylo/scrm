/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProductOrderService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmOrderCreateDto;
import org.hiylo.scrm.dto.ScrmOrderDto;
import org.hiylo.scrm.dto.ScrmOrderItemDto;
import org.hiylo.scrm.dto.ScrmProductDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 商品/订单管理服务 (门面)。
 * <p>
 * 作为商品/订单模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmProductOrderProductService} (产品管理)、{@link ScrmProductOrderOrderService}
 * (订单管理)、{@link ScrmProductOrderItemService} (订单项管理) 与
 * {@link ScrmProductOrderStatsService} (统计)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmProductOrderService {

    /** 商品管理子域服务 */
    private final ScrmProductOrderProductService productService;

    /** 订单管理子域服务 */
    private final ScrmProductOrderOrderService orderService;

    /** 订单项管理子域服务 */
    private final ScrmProductOrderItemService itemService;

    /** 统计子域服务 */
    private final ScrmProductOrderStatsService statsService;

    // ============================================================
    // 商品管理
    // ============================================================

    /**
     * 创建商品。
     * <p>校验商品编码唯一性后写入归属账号 ID 持久化, 状态缺省 ACTIVE, 计数字段缺省 0。</p>
     *
     * @param dto 商品参数
     * @return 创建后的商品
     * @throws ScrmException 参数非法 / 商品编码已存在
     */
    public ScrmProductDto createProduct(ScrmProductDto dto) throws ScrmException {
        return productService.createProduct(dto);
    }

    /**
     * 更新商品（字段非空才覆盖）。
     * <p>商品编码不允许修改, 避免与已有订单项快照不一致。</p>
     *
     * @param id  商品 ID
     * @param dto 商品参数
     * @return 更新后的商品
     * @throws ScrmException 商品不存在 / 商品编码已存在
     */
    public ScrmProductDto updateProduct(Long id, ScrmProductDto dto) throws ScrmException {
        return productService.updateProduct(id, dto);
    }

    /**
     * 删除商品。
     * <p>已停用商品方可删除, 在售商品请先下架。</p>
     *
     * @param id 商品 ID
     * @throws ScrmException 商品不存在 / 在售商品不允许删除
     */
    public void deleteProduct(Long id) throws ScrmException {
        productService.deleteProduct(id);
    }

    /**
     * 查询商品详情。
     *
     * @param id 商品 ID
     * @return 商品 DTO
     * @throws ScrmException 商品不存在
     */
    public ScrmProductDto getProduct(Long id) throws ScrmException {
        return productService.getProduct(id);
    }

    /**
     * 按商品编码查询商品。
     *
     * @param code 商品编码
     * @return 商品 DTO
     * @throws ScrmException 商品不存在
     */
    public ScrmProductDto getProductByCode(String code) throws ScrmException {
        return productService.getProductByCode(code);
    }

    /**
     * 分页查询商品, 支持按分类、品牌、状态与关键词过滤。
     * <p>关键词匹配商品编码、名称、SKU 与条码。</p>
     *
     * @param category 分类过滤 (可空)
     * @param brand    品牌过滤 (可空)
     * @param status   状态过滤 (可空)
     * @param keyword  关键词过滤 (可空)
     * @param pageable 分页参数
     * @return 商品分页结果
     */
    public Page<ScrmProductDto> listProducts(String category, String brand, String status,
                                                String keyword, Pageable pageable) {
        return productService.listProducts(category, brand, status, keyword, pageable);
    }

    /**
     * 调整库存 (delta 可正可负, 正数为入库, 负数为出库)。
     *
     * @param id     商品 ID
     * @param delta  库存变化量 (正入库 / 负出库)
     * @param reason 调整原因 (可空)
     * @return 更新后的商品
     * @throws ScrmException 商品不存在 / 库存不足
     */
    public ScrmProductDto adjustStock(Long id, int delta, String reason) throws ScrmException {
        return productService.adjustStock(id, delta, reason);
    }

    /**
     * 更新商品状态 (ACTIVE / INACTIVE / DISCONTINUED)。
     *
     * @param id     商品 ID
     * @param status 目标状态
     * @return 更新后的商品
     * @throws ScrmException 商品不存在 / 状态非法
     */
    public ScrmProductDto updateStatus(Long id, String status) throws ScrmException {
        return productService.updateStatus(id, status);
    }

    /**
     * 批量导入商品。
     * <p>已存在的商品编码跳过 (幂等), 返回成功导入的商品列表。</p>
     *
     * @param products 商品参数列表
     * @return 导入成功的商品列表
     * @throws ScrmException 参数非法
     */
    public List<ScrmProductDto> batchImport(List<ScrmProductDto> products) throws ScrmException {
        return productService.batchImport(products);
    }

    // ============================================================
    // 订单管理
    // ============================================================

    /**
     * 创建订单: 生成订单号 → 计算金额 → 创建订单项 → 扣库存。
     * <p>
     * 订单号由 {@link #generateOrderNo()} 生成 (年月日 + 6 位序号), 订单项按入参构建并持久化,
     * 金额 (总金额 / 折扣 / 运费 / 税费) 由订单项汇总与入参合并计算, 已支付状态由支付金额决定。
     * 商品库存按订单项数量扣减 (库存不足抛异常回滚)。
     * </p>
     *
     * @param createDto 创建请求
     * @return 创建后的订单 (含订单项)
     * @throws ScrmException 参数非法 / 商品不存在 / 库存不足
     */
    public ScrmOrderDto createOrder(ScrmOrderCreateDto createDto) throws ScrmException {
        return orderService.createOrder(createDto);
    }

    /**
     * 更新订单（字段非空才覆盖, 仅 PENDING / CONFIRMED 状态可更新）。
     *
     * @param id  订单 ID
     * @param dto 订单参数
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法不允许修改
     */
    public ScrmOrderDto updateOrder(Long id, ScrmOrderDto dto) throws ScrmException {
        return orderService.updateOrder(id, dto);
    }

    /**
     * 删除订单及其订单项。
     * <p>仅 PENDING / CANCELLED 状态可删除, 已支付订单请走退款流程。</p>
     *
     * @param id 订单 ID
     * @throws ScrmException 订单不存在 / 状态非法不允许删除
     */
    public void deleteOrder(Long id) throws ScrmException {
        orderService.deleteOrder(id);
    }

    /**
     * 查询订单详情 (含订单项)。
     *
     * @param id 订单 ID
     * @return 订单 DTO
     * @throws ScrmException 订单不存在
     */
    public ScrmOrderDto getOrder(Long id) throws ScrmException {
        return orderService.getOrder(id);
    }

    /**
     * 按订单编号查询订单 (含订单项)。
     *
     * @param orderNo 订单编号
     * @return 订单 DTO
     * @throws ScrmException 订单不存在
     */
    public ScrmOrderDto getOrderByNo(String orderNo) throws ScrmException {
        return orderService.getOrderByNo(orderNo);
    }

    /**
     * 分页查询订单, 支持按客户、订单状态、支付状态、订单类型、时间范围与关键词过滤。
     * <p>关键词匹配订单编号、客户名称与收件人。</p>
     *
     * @param customerId     客户 ID 过滤 (可空)
     * @param orderStatus    订单状态过滤 (可空)
     * @param paymentStatus  支付状态过滤 (可空)
     * @param orderType      订单类型过滤 (可空)
     * @param startTime      起始时间 (按创建时间, 可空)
     * @param endTime        截止时间 (按创建时间, 可空)
     * @param keyword        关键词过滤 (可空)
     * @param pageable       分页参数
     * @return 订单分页结果
     */
    public Page<ScrmOrderDto> listOrders(Long customerId, String orderStatus, String paymentStatus,
                                          String orderType, LocalDateTime startTime, LocalDateTime endTime,
                                          String keyword, Pageable pageable) {
        return orderService.listOrders(customerId, orderStatus, paymentStatus,
                orderType, startTime, endTime, keyword, pageable);
    }

    /**
     * 确认订单 (PENDING → CONFIRMED)。
     *
     * @param id 订单 ID
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    public ScrmOrderDto confirmOrder(Long id) throws ScrmException {
        return orderService.confirmOrder(id);
    }

    /**
     * 支付订单。
     * <p>更新支付方式与已付金额, 已付金额 ≥ 总金额则置 PAID, 否则置 PARTIAL;
     * 订单状态由 CONFIRMED / PENDING 流转为 PAID, 记录支付时间。</p>
     *
     * @param id            订单 ID
     * @param paymentMethod 支付方式
     * @param paidAmount    本次支付金额
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法 / 支付金额非法
     */
    public ScrmOrderDto payOrder(Long id, String paymentMethod, Double paidAmount) throws ScrmException {
        return orderService.payOrder(id, paymentMethod, paidAmount);
    }

    /**
     * 发货 (PAID → SHIPPED)。
     * <p>记录物流单号、物流公司与发货时间。仅 PAID 状态可发货。</p>
     *
     * @param id               订单 ID
     * @param trackingNo       物流单号
     * @param trackingCompany  物流公司
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    public ScrmOrderDto shipOrder(Long id, String trackingNo, String trackingCompany) throws ScrmException {
        return orderService.shipOrder(id, trackingNo, trackingCompany);
    }

    /**
     * 确认送达 (SHIPPED → DELIVERED)。
     *
     * @param id 订单 ID
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    public ScrmOrderDto deliverOrder(Long id) throws ScrmException {
        return orderService.deliverOrder(id);
    }

    /**
     * 完成订单 (DELIVERED → COMPLETED)。
     *
     * @param id 订单 ID
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    public ScrmOrderDto completeOrder(Long id) throws ScrmException {
        return orderService.completeOrder(id);
    }

    /**
     * 取消订单 (PENDING / CONFIRMED → CANCELLED)。
     * <p>已支付订单不允许直接取消, 请先退款。取消时回补商品库存。</p>
     *
     * @param id     订单 ID
     * @param reason 取消原因 (可空)
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    public ScrmOrderDto cancelOrder(Long id, String reason) throws ScrmException {
        return orderService.cancelOrder(id, reason);
    }

    /**
     * 退款 (PAID → REFUNDED)。
     * <p>仅已支付订单可退款, 退款后支付状态置 REFUNDED, 订单状态置 REFUNDED, 回补商品库存。</p>
     *
     * @param id     订单 ID
     * @param reason 退款原因 (可空)
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    public ScrmOrderDto refundOrder(Long id, String reason) throws ScrmException {
        return orderService.refundOrder(id, reason);
    }

    // ============================================================
    // 订单项管理
    // ============================================================

    /**
     * 查询订单项列表。
     *
     * @param orderId 订单 ID
     * @return 订单项列表
     */
    public List<ScrmOrderItemDto> listOrderItems(Long orderId) {
        return itemService.listOrderItems(orderId);
    }

    /**
     * 添加订单项 (仅 PENDING / CONFIRMED 状态可添加, 添加后重算订单金额)。
     *
     * @param orderId 订单 ID
     * @param dto     订单项参数
     * @return 添加后的订单项
     * @throws ScrmException 订单不存在 / 状态非法
     */
    public ScrmOrderItemDto addOrderItem(Long orderId, ScrmOrderItemDto dto) throws ScrmException {
        return itemService.addOrderItem(orderId, dto);
    }

    /**
     * 更新订单项 (字段非空才覆盖, 仅 PENDING / CONFIRMED 状态可更新, 更新后重算订单金额)。
     *
     * @param id  订单项 ID
     * @param dto 订单项参数
     * @return 更新后的订单项
     * @throws ScrmException 订单项不存在 / 订单状态非法
     */
    public ScrmOrderItemDto updateOrderItem(Long id, ScrmOrderItemDto dto) throws ScrmException {
        return itemService.updateOrderItem(id, dto);
    }

    /**
     * 移除订单项 (仅 PENDING / CONFIRMED 状态可移除, 移除后重算订单金额)。
     *
     * @param id 订单项 ID
     * @throws ScrmException 订单项不存在 / 订单状态非法
     */
    public void removeOrderItem(Long id) throws ScrmException {
        itemService.removeOrderItem(id);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 商品统计: 商品总数 / 各状态数 / 库存总量 / 销量总量 / 评分分布。
     * <p>时间范围按创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getProductStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getProductStats(startTime, endTime);
    }

    /**
     * 订单统计: 订单总数 / 各状态数 / 总金额 / 已付金额 / 转化率。
     * <p>时间范围按创建时间过滤, 为空时统计全量。转化率 = 已完成订单数 / 总订单数。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getOrderStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getOrderStats(startTime, endTime);
    }

    /**
     * 客户购买历史: 订单列表与汇总 (订单数 / 总消费 / 已完成订单数 / 最近下单时间)。
     *
     * @param customerId 客户 ID
     * @return 购买历史
     */
    public Map<String, Object> getCustomerPurchaseHistory(Long customerId) {
        return statsService.getCustomerPurchaseHistory(customerId);
    }

    /**
     * 热销商品 (按销量排序, 取前 limit 条)。
     *
     * @param limit     返回条数 (默认 10)
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 热销商品列表: [{productId, productName, salesCount, salesAmount}]
     */
    public List<Map<String, Object>> getTopProducts(Integer limit, LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getTopProducts(limit, startTime, endTime);
    }

    /**
     * 销售趋势 (按日聚合, 最近 days 天)。
     * <p>聚合订单数与已付金额 (不含取消订单), 按日期升序返回。</p>
     *
     * @param days 天数 (默认 30, 最大 365)
     * @return 趋势列表: [{date, orderCount, revenue}]
     */
    public List<Map<String, Object>> getSalesTrend(Integer days) {
        return statsService.getSalesTrend(days);
    }

    /**
     * 渠道收入: 按下单渠道聚合订单数与总金额 (不含取消订单)。
     * <p>时间范围按创建时间过滤, 为空时统计全量。channel 为空的归入 UNKNOWN。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 渠道收入列表: [{channel, orderCount, revenue}]
     */
    public List<Map<String, Object>> getRevenueByChannel(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getRevenueByChannel(startTime, endTime);
    }

    /**
     * 生成订单编号 (年月日 + 6 位序号)。
     * <p>序号基于当日已有订单数 +1, 线程安全考虑下序号可能重复, 由订单编号唯一约束兜底。</p>
     *
     * @return 订单编号
     */
    public String generateOrderNo() {
        return orderService.generateOrderNo();
    }
}