/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProductOrderOrderService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmOrderCreateDto;
import org.hiylo.scrm.dto.ScrmOrderDto;
import org.hiylo.scrm.dto.ScrmOrderItemDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmOrderEntity;
import org.hiylo.scrm.entity.ScrmOrderItemEntity;
import org.hiylo.scrm.entity.ScrmProductEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmOrderItemRepository;
import org.hiylo.scrm.repository.ScrmOrderRepository;
import org.hiylo.scrm.repository.ScrmProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SCRM 订单管理服务 (订单子域)。
 * <p>
 * 承载订单管理子域: 创建 (生成订单号 → 计算金额 → 创建订单项 → 扣库存) / 更新 / 删除 / 查询 /
 * 状态流转 (确认 / 支付 / 发货 / 送达 / 完成 / 取消 / 退款 / 回补库存) 与订单编号生成。同时托管
 * 订单状态 / 支付状态 / 订单类型常量, 以及订单 / 订单项查询、金额重算、DTO 转换等 package 级
 * 共享能力, 供订单项 / 统计兄弟类复用。门面 {@link ScrmProductOrderService} 委托本类实现订单管理。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmProductOrderOrderService {

    // ==================== 订单类型 ====================

    /** 订单类型: 销售 */
    private static final String ORDER_TYPE_SALE = "SALE";
    /** 订单类型: 退款 */
    private static final String ORDER_TYPE_REFUND = "REFUND";
    /** 订单类型: 换货 */
    private static final String ORDER_TYPE_EXCHANGE = "EXCHANGE";
    /** 订单类型: 预售 */
    private static final String ORDER_TYPE_PRE_ORDER = "PRE_ORDER";

    // ==================== 订单状态常量 (共享) ====================

    /** 订单状态: 待确认 */
    static final String ORDER_STATUS_PENDING = "PENDING";
    /** 订单状态: 已确认 */
    static final String ORDER_STATUS_CONFIRMED = "CONFIRMED";
    /** 订单状态: 已支付 */
    static final String ORDER_STATUS_PAID = "PAID";
    /** 订单状态: 已发货 */
    static final String ORDER_STATUS_SHIPPED = "SHIPPED";
    /** 订单状态: 已送达 */
    static final String ORDER_STATUS_DELIVERED = "DELIVERED";
    /** 订单状态: 已完成 */
    static final String ORDER_STATUS_COMPLETED = "COMPLETED";
    /** 订单状态: 已取消 */
    static final String ORDER_STATUS_CANCELLED = "CANCELLED";
    /** 订单状态: 已退款 */
    static final String ORDER_STATUS_REFUNDED = "REFUNDED";

    // ==================== 支付状态 ====================

    /** 支付状态: 未支付 */
    private static final String PAYMENT_STATUS_UNPAID = "UNPAID";
    /** 支付状态: 部分支付 */
    private static final String PAYMENT_STATUS_PARTIAL = "PARTIAL";
    /** 支付状态: 已支付 */
    private static final String PAYMENT_STATUS_PAID = "PAID";
    /** 支付状态: 已退款 */
    private static final String PAYMENT_STATUS_REFUNDED = "REFUNDED";

    // ==================== 默认值 ====================

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";
    /** 订单号序号位数 */
    private static final int ORDER_NO_SEQUENCE_LENGTH = 6;
    /** 订单号序号基数 */
    private static final int ORDER_NO_SEQUENCE_RADIX = 10;

    /** 订单数据访问层 */
    private final ScrmOrderRepository orderRepository;
    /** 订单项数据访问层 */
    private final ScrmOrderItemRepository orderItemRepository;
    /** 商品数据访问层 (下单扣库存 / 回补库存) */
    private final ScrmProductRepository productRepository;
    /** 客户数据访问层 (解析客户名称) */
    private final ScrmCustomerRepository customerRepository;
    /** 商品管理子域服务 (查询商品) */
    private final ScrmProductOrderProductService productService;

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
    @Transactional
    public ScrmOrderDto createOrder(ScrmOrderCreateDto createDto) throws ScrmException {
        if (createDto == null) {
            throw ScrmException.badRequest("订单参数不能为空");
        }
        if (createDto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (createDto.getItems() == null || createDto.getItems().isEmpty()) {
            throw ScrmException.badRequest("订单项不能为空");
        }
        ScrmOrderEntity order = new ScrmOrderEntity();
        order.setOrderNo(generateOrderNo());
        order.setCustomerId(createDto.getCustomerId());
        order.setCustomerName(resolveCustomerName(createDto.getCustomerId()));
        order.setOrderType(createDto.getOrderType() != null ? createDto.getOrderType() : ORDER_TYPE_SALE);
        order.setOrderStatus(ORDER_STATUS_PENDING);
        order.setPaymentStatus(PAYMENT_STATUS_UNPAID);
        order.setPaidAmount(0d);
        order.setCurrency(createDto.getCurrency() != null ? createDto.getCurrency() : DEFAULT_CURRENCY);
        order.setCouponId(createDto.getCouponId());
        order.setCouponCode(createDto.getCouponCode());
        order.setSalespersonId(createDto.getSalespersonId());
        order.setSalespersonName(createDto.getSalespersonName());
        order.setChannel(createDto.getChannel());
        order.setShippingAddress(createDto.getShippingAddress());
        order.setShippingName(createDto.getShippingName());
        order.setShippingPhone(createDto.getShippingPhone());
        order.setRemark(createDto.getRemark());
        order.setCreatedBy(createDto.getCreatedBy());
        order = orderRepository.save(order);

        // 创建订单项并扣库存
        List<ScrmOrderItemEntity> items = new ArrayList<>();
        double itemsSubtotal = 0d;
        double itemsDiscount = 0d;
        double itemsTax = 0d;
        for (ScrmOrderItemDto itemDto : createDto.getItems()) {
            ScrmOrderItemEntity item = buildOrderItem(order.getId(), itemDto);
            // 关联商品时扣库存并填充快照
            if (item.getProductId() != null) {
                ScrmProductEntity product = productService.findProductOrThrow(item.getProductId());
                if (!ScrmProductOrderProductService.PRODUCT_STATUS_ACTIVE.equals(product.getStatus())) {
                    throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                            "商品非在售状态, 不允许下单: productId=" + product.getId()
                                    + ", status=" + product.getStatus());
                }
                int stock = product.getStock() != null ? product.getStock() : 0;
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                if (stock < qty) {
                    throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                            "商品库存不足: productId=" + product.getId()
                                    + ", stock=" + stock + ", requested=" + qty);
                }
                product.setStock(stock - qty);
                product.setSalesCount((product.getSalesCount() != null ? product.getSalesCount() : 0) + qty);
                productRepository.save(product);
                // 填充商品快照
                if (item.getProductCode() == null) {
                    item.setProductCode(product.getProductCode());
                }
                if (item.getProductName() == null) {
                    item.setProductName(product.getProductName());
                }
                if (item.getProductImage() == null) {
                    item.setProductImage(product.getImageUrl());
                }
                if (item.getSpec() == null) {
                    item.setSpec(product.getSpec());
                }
                if (item.getUnitPrice() == null || item.getUnitPrice() == 0d) {
                    item.setUnitPrice(product.getPrice());
                }
            }
            // 计算单项小计
            double unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : 0d;
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            double discount = item.getDiscountAmount() != null ? item.getDiscountAmount() : 0d;
            double subtotal = round(unitPrice * qty - discount);
            item.setSubtotal(subtotal);
            // 税额
            double taxRate = item.getTaxRate() != null ? item.getTaxRate() : 0d;
            double taxAmount = round(subtotal * taxRate);
            item.setTaxAmount(taxAmount);
            items.add(item);
            itemsSubtotal += subtotal;
            itemsDiscount += discount;
            itemsTax += taxAmount;
        }
        items = orderItemRepository.saveAll(items);

        // 计算订单金额
        double shipping = createDto.getShippingAmount() != null ? createDto.getShippingAmount() : 0d;
        double discount = createDto.getDiscountAmount() != null ? createDto.getDiscountAmount() : 0d;
        double tax = createDto.getTaxAmount() != null ? createDto.getTaxAmount() : itemsTax;
        double total = round(itemsSubtotal + shipping + tax - discount);
        order.setTotalAmount(total);
        order.setDiscountAmount(round(discount));
        order.setShippingAmount(round(shipping));
        order.setTaxAmount(round(tax));
        order = orderRepository.save(order);

        // 序列化订单项快照
        order.setItemsJson(serializeItems(items));
        order = orderRepository.save(order);
        log.info("创建订单: id={}, orderNo={}, total={}", order.getId(), order.getOrderNo(), total);
        return toOrderDto(order, items);
    }

    /**
     * 更新订单（字段非空才覆盖, 仅 PENDING / CONFIRMED 状态可更新）。
     *
     * @param id  订单 ID
     * @param dto 订单参数
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法不允许修改
     */
    @Transactional
    public ScrmOrderDto updateOrder(Long id, ScrmOrderDto dto) throws ScrmException {
        ScrmOrderEntity order = findOrderOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("订单参数不能为空");
        }
        // 订单编号不允许修改
        if (dto.getOrderNo() != null && !dto.getOrderNo().equals(order.getOrderNo())) {
            throw ScrmException.badRequest("订单编号不允许修改: orderNo=" + order.getOrderNo());
        }
        if (dto.getCustomerName() != null) order.setCustomerName(dto.getCustomerName());
        if (dto.getPaymentMethod() != null) order.setPaymentMethod(dto.getPaymentMethod());
        if (dto.getSalespersonId() != null) order.setSalespersonId(dto.getSalespersonId());
        if (dto.getSalespersonName() != null) order.setSalespersonName(dto.getSalespersonName());
        if (dto.getChannel() != null) order.setChannel(dto.getChannel());
        if (dto.getShippingAddress() != null) order.setShippingAddress(dto.getShippingAddress());
        if (dto.getShippingName() != null) order.setShippingName(dto.getShippingName());
        if (dto.getShippingPhone() != null) order.setShippingPhone(dto.getShippingPhone());
        if (dto.getTrackingNo() != null) order.setTrackingNo(dto.getTrackingNo());
        if (dto.getTrackingCompany() != null) order.setTrackingCompany(dto.getTrackingCompany());
        if (dto.getRemark() != null) order.setRemark(dto.getRemark());
        if (dto.getDiscountAmount() != null) order.setDiscountAmount(dto.getDiscountAmount());
        if (dto.getShippingAmount() != null) order.setShippingAmount(dto.getShippingAmount());
        if (dto.getTaxAmount() != null) order.setTaxAmount(dto.getTaxAmount());
        if (dto.getCurrency() != null) order.setCurrency(dto.getCurrency());
        if (dto.getCouponId() != null) order.setCouponId(dto.getCouponId());
        if (dto.getCouponCode() != null) order.setCouponCode(dto.getCouponCode());
        order = orderRepository.save(order);
        log.info("更新订单: id={}", id);
        return toOrderDto(order, listOrderItemEntities(id));
    }

    /**
     * 删除订单及其订单项。
     * <p>仅 PENDING / CANCELLED 状态可删除, 已支付订单请走退款流程。</p>
     *
     * @param id 订单 ID
     * @throws ScrmException 订单不存在 / 状态非法不允许删除
     */
    @Transactional
    public void deleteOrder(Long id) throws ScrmException {
        ScrmOrderEntity order = findOrderOrThrow(id);
        if (!ORDER_STATUS_PENDING.equals(order.getOrderStatus()) && !ORDER_STATUS_CANCELLED.equals(order.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单状态非法, 不允许删除: status=" + order.getOrderStatus());
        }
        orderItemRepository.deleteByOrderId(id);
        orderRepository.delete(order);
        log.info("删除订单: id={}", id);
    }

    /**
     * 查询订单详情 (含订单项)。
     *
     * @param id 订单 ID
     * @return 订单 DTO
     * @throws ScrmException 订单不存在
     */
    @Transactional(readOnly = true)
    public ScrmOrderDto getOrder(Long id) throws ScrmException {
        ScrmOrderEntity order = findOrderOrThrow(id);
        return toOrderDto(order, listOrderItemEntities(id));
    }

    /**
     * 按订单编号查询订单 (含订单项)。
     *
     * @param orderNo 订单编号
     * @return 订单 DTO
     * @throws ScrmException 订单不存在
     */
    @Transactional(readOnly = true)
    public ScrmOrderDto getOrderByNo(String orderNo) throws ScrmException {
        ScrmOrderEntity order = findOrderByNoOrThrow(orderNo);
        return toOrderDto(order, listOrderItemEntities(order.getId()));
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
    @Transactional(readOnly = true)
    public Page<ScrmOrderDto> listOrders(Long customerId, String orderStatus, String paymentStatus,
                                          String orderType, LocalDateTime startTime, LocalDateTime endTime,
                                          String keyword, Pageable pageable) {
        Specification<ScrmOrderEntity> spec = buildOrderSpec(customerId, orderStatus, paymentStatus,
                orderType, startTime, endTime, keyword);
        return orderRepository.findAll(spec, pageable).map(o -> toOrderDto(o, listOrderItemEntities(o.getId())));
    }

    /**
     * 确认订单 (PENDING → CONFIRMED)。
     *
     * @param id 订单 ID
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    @Transactional
    public ScrmOrderDto confirmOrder(Long id) throws ScrmException {
        ScrmOrderEntity order = findOrderOrThrow(id);
        if (!ORDER_STATUS_PENDING.equals(order.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单状态非法, 仅 PENDING 可确认: currentStatus=" + order.getOrderStatus());
        }
        order.setOrderStatus(ORDER_STATUS_CONFIRMED);
        order = orderRepository.save(order);
        log.info("确认订单: id={}", id);
        return toOrderDto(order, listOrderItemEntities(id));
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
    @Transactional
    public ScrmOrderDto payOrder(Long id, String paymentMethod, Double paidAmount) throws ScrmException {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw ScrmException.badRequest("支付方式不能为空");
        }
        if (paidAmount == null || paidAmount <= 0) {
            throw ScrmException.badRequest("支付金额必须大于 0");
        }
        ScrmOrderEntity order = findOrderOrThrow(id);
        if (ORDER_STATUS_CANCELLED.equals(order.getOrderStatus())
                || ORDER_STATUS_REFUNDED.equals(order.getOrderStatus())
                || ORDER_STATUS_COMPLETED.equals(order.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单状态非法, 不允许支付: currentStatus=" + order.getOrderStatus());
        }
        double total = order.getTotalAmount() != null ? order.getTotalAmount() : 0d;
        double currentPaid = order.getPaidAmount() != null ? order.getPaidAmount() : 0d;
        double newPaid = round(currentPaid + paidAmount);
        order.setPaymentMethod(paymentMethod);
        order.setPaidAmount(newPaid);
        if (newPaid >= total) {
            order.setPaymentStatus(PAYMENT_STATUS_PAID);
            order.setOrderStatus(ORDER_STATUS_PAID);
        } else {
            order.setPaymentStatus(PAYMENT_STATUS_PARTIAL);
        }
        if (order.getPaidAt() == null) {
            order.setPaidAt(LocalDateTime.now());
        }
        order = orderRepository.save(order);
        log.info("支付订单: id={}, paidAmount={}, totalPaid={}", id, paidAmount, newPaid);
        return toOrderDto(order, listOrderItemEntities(id));
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
    @Transactional
    public ScrmOrderDto shipOrder(Long id, String trackingNo, String trackingCompany) throws ScrmException {
        if (trackingNo == null || trackingNo.isBlank()) {
            throw ScrmException.badRequest("物流单号不能为空");
        }
        ScrmOrderEntity order = findOrderOrThrow(id);
        if (!ORDER_STATUS_PAID.equals(order.getOrderStatus()) && !ORDER_STATUS_CONFIRMED.equals(order.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单状态非法, 仅 PAID / CONFIRMED 可发货: currentStatus=" + order.getOrderStatus());
        }
        order.setOrderStatus(ORDER_STATUS_SHIPPED);
        order.setTrackingNo(trackingNo);
        order.setTrackingCompany(trackingCompany);
        order.setShippedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        log.info("发货订单: id={}, trackingNo={}", id, trackingNo);
        return toOrderDto(order, listOrderItemEntities(id));
    }

    /**
     * 确认送达 (SHIPPED → DELIVERED)。
     *
     * @param id 订单 ID
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    @Transactional
    public ScrmOrderDto deliverOrder(Long id) throws ScrmException {
        ScrmOrderEntity order = findOrderOrThrow(id);
        if (!ORDER_STATUS_SHIPPED.equals(order.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单状态非法, 仅 SHIPPED 可确认送达: currentStatus=" + order.getOrderStatus());
        }
        order.setOrderStatus(ORDER_STATUS_DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        order = orderRepository.save(order);
        log.info("确认送达订单: id={}", id);
        return toOrderDto(order, listOrderItemEntities(id));
    }

    /**
     * 完成订单 (DELIVERED → COMPLETED)。
     *
     * @param id 订单 ID
     * @return 更新后的订单
     * @throws ScrmException 订单不存在 / 状态非法
     */
    @Transactional
    public ScrmOrderDto completeOrder(Long id) throws ScrmException {
        ScrmOrderEntity order = findOrderOrThrow(id);
        if (!ORDER_STATUS_DELIVERED.equals(order.getOrderStatus()) && !ORDER_STATUS_PAID.equals(order.getOrderStatus()) && !ORDER_STATUS_SHIPPED.equals(order.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单状态非法, 仅 DELIVERED / PAID / SHIPPED 可完成: currentStatus=" + order.getOrderStatus());
        }
        order.setOrderStatus(ORDER_STATUS_COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        log.info("完成订单: id={}", id);
        return toOrderDto(order, listOrderItemEntities(id));
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
    @Transactional
    public ScrmOrderDto cancelOrder(Long id, String reason) throws ScrmException {
        ScrmOrderEntity order = findOrderOrThrow(id);
        if (!ORDER_STATUS_PENDING.equals(order.getOrderStatus()) && !ORDER_STATUS_CONFIRMED.equals(order.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单状态非法, 仅 PENDING / CONFIRMED 可取消: currentStatus=" + order.getOrderStatus());
        }
        order.setOrderStatus(ORDER_STATUS_CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        if (reason != null && !reason.isBlank()) {
            order.setRemark(reason);
        }
        order = orderRepository.save(order);
        // 回补库存
        restockOrderItems(order);
        log.info("取消订单: id={}, reason={}", id, reason);
        return toOrderDto(order, listOrderItemEntities(id));
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
    @Transactional
    public ScrmOrderDto refundOrder(Long id, String reason) throws ScrmException {
        ScrmOrderEntity order = findOrderOrThrow(id);
        if (!ORDER_STATUS_PAID.equals(order.getOrderStatus()) && !ORDER_STATUS_COMPLETED.equals(order.getOrderStatus()) && !ORDER_STATUS_SHIPPED.equals(order.getOrderStatus()) && !ORDER_STATUS_DELIVERED.equals(order.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单状态非法, 仅已支付订单可退款: currentStatus=" + order.getOrderStatus());
        }
        order.setOrderStatus(ORDER_STATUS_REFUNDED);
        order.setPaymentStatus(PAYMENT_STATUS_REFUNDED);
        if (reason != null && !reason.isBlank()) {
            order.setRemark(reason);
        }
        order = orderRepository.save(order);
        // 回补库存
        restockOrderItems(order);
        log.info("退款订单: id={}, reason={}", id, reason);
        return toOrderDto(order, listOrderItemEntities(id));
    }

    /**
     * 生成订单编号 (年月日 + 6 位序号)。
     * <p>序号基于当日已有订单数 +1, 线程安全考虑下序号可能重复, 由订单编号唯一约束兜底。</p>
     *
     * @return 订单编号
     */
    public String generateOrderNo() {
        String prefix = LocalDateTime.now().toLocalDate().toString().replace("-", "");
        long count = orderRepository.countByOrderNoStartingWith(prefix);
        String sequence = String.format("%0" + ORDER_NO_SEQUENCE_LENGTH + "d", count + 1);
        return prefix + sequence;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 构建订单项实体 (不含小计计算, 由调用方完成)。
     */
    ScrmOrderItemEntity buildOrderItem(Long orderId, ScrmOrderItemDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("订单项参数不能为空");
        }
        if (dto.getProductName() == null || dto.getProductName().isBlank()) {
            throw ScrmException.badRequest("订单项商品名称不能为空");
        }
        ScrmOrderItemEntity item = new ScrmOrderItemEntity();
        item.setOrderId(orderId);
        item.setProductId(dto.getProductId());
        item.setProductCode(dto.getProductCode());
        item.setProductName(dto.getProductName());
        item.setProductImage(dto.getProductImage());
        item.setSpec(dto.getSpec());
        item.setUnitPrice(dto.getUnitPrice() != null ? dto.getUnitPrice() : 0d);
        item.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
        item.setDiscountAmount(dto.getDiscountAmount() != null ? dto.getDiscountAmount() : 0d);
        item.setSubtotal(0d);
        item.setTaxRate(dto.getTaxRate() != null ? dto.getTaxRate() : 0d);
        item.setTaxAmount(0d);
        item.setRemark(dto.getRemark());
        return item;
    }

    /**
     * 回补订单项商品库存 (取消 / 退款时调用)。
     */
    private void restockOrderItems(ScrmOrderEntity order) {
        List<ScrmOrderItemEntity> items = listOrderItemEntities(order.getId());
        for (ScrmOrderItemEntity item : items) {
            if (item.getProductId() != null) {
                productRepository.findById(item.getProductId()).ifPresent(product -> {
                    int stock = product.getStock() != null ? product.getStock() : 0;
                    int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                    product.setStock(stock + qty);
                    int sales = product.getSalesCount() != null ? product.getSalesCount() : 0;
                    product.setSalesCount(Math.max(0, sales - qty));
                    productRepository.save(product);
                });
            }
        }
    }

    /**
     * 重算订单金额 (订单项增删改后调用, 供订单项子域复用)。
     */
    void recalcOrderAmount(ScrmOrderEntity order) {
        List<ScrmOrderItemEntity> items = listOrderItemEntities(order.getId());
        double itemsSubtotal = items.stream()
                .mapToDouble(i -> i.getSubtotal() != null ? i.getSubtotal() : 0d)
                .sum();
        double itemsTax = items.stream()
                .mapToDouble(i -> i.getTaxAmount() != null ? i.getTaxAmount() : 0d)
                .sum();
        double shipping = order.getShippingAmount() != null ? order.getShippingAmount() : 0d;
        double discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : 0d;
        double tax = order.getTaxAmount() != null ? order.getTaxAmount() : itemsTax;
        double total = round(itemsSubtotal + shipping + tax - discount);
        order.setTotalAmount(total);
        order.setItemsJson(serializeItems(items));
        orderRepository.save(order);
    }

    /**
     * 查询订单项实体列表 (按 ID 升序, 供订单项子域复用)。
     */
    List<ScrmOrderItemEntity> listOrderItemEntities(Long orderId) {
        return orderItemRepository.findByOrderIdOrderByIdAsc(orderId);
    }

    /**
     * 序列化订单项为 JSON 快照 (简单拼接, 避免引入额外依赖, 供订单项子域复用)。
     */
    String serializeItems(List<ScrmOrderItemEntity> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            ScrmOrderItemEntity item = items.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"id\":\"").append(item.getId())
                    .append("\",\"productId\":").append(item.getProductId() == null ? "null"
                            : "\"" + item.getProductId() + "\"")
                    .append(",\"productCode\":").append(item.getProductCode() == null ? "null"
                            : "\"" + escape(item.getProductCode()) + "\"")
                    .append(",\"productName\":\"").append(escape(item.getProductName())).append("\"")
                    .append(",\"spec\":").append(item.getSpec() == null ? "null" : "\"" + escape(item.getSpec()) + "\"")
                    .append(",\"unitPrice\":").append(item.getUnitPrice())
                    .append(",\"quantity\":").append(item.getQuantity())
                    .append(",\"discountAmount\":").append(item.getDiscountAmount())
                    .append(",\"subtotal\":").append(item.getSubtotal())
                    .append(",\"taxAmount\":").append(item.getTaxAmount())
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * JSON 字符串转义。
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * 解析客户名称 (从客户实体 nickname 字段), 客户不存在时返回 null, 供统计子域复用。
     */
    String resolveCustomerName(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findById(customerId)
                .map(ScrmCustomerEntity::getNickname)
                .orElse(null);
    }

    /**
     * 构建订单查询条件 Specification。
     */
    private Specification<ScrmOrderEntity> buildOrderSpec(Long customerId, String orderStatus, String paymentStatus,
                                                            String orderType, LocalDateTime startTime,
                                                            LocalDateTime endTime, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (orderStatus != null && !orderStatus.isBlank()) {
                predicates.add(cb.equal(root.get("orderStatus"), orderStatus));
            }
            if (paymentStatus != null && !paymentStatus.isBlank()) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (orderType != null && !orderType.isBlank()) {
                predicates.add(cb.equal(root.get("orderType"), orderType));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderNo")), like),
                        cb.like(cb.lower(root.get("customerName")), like),
                        cb.like(cb.lower(root.get("shippingName")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 按主键查询订单, 不存在抛异常, 供订单项 / 统计子域复用。
     */
    ScrmOrderEntity findOrderOrThrow(Long id) throws ScrmException {
        ScrmOrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "订单不存在: id=" + id));
        return order;
    }

    /**
     * 按订单编号查询订单, 不存在抛异常
     */
    private ScrmOrderEntity findOrderByNoOrThrow(String orderNo) throws ScrmException {
        ScrmOrderEntity order = orderRepository.findByOrderNo(
                        orderNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "订单不存在: orderNo=" + orderNo));
        return order;
    }

    /**
     * 保留两位小数 (供订单项 / 统计子域复用)。
     */
    static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /**
     * 订单实体转 DTO, 供统计子域复用。
     *
     * @param entity 订单实体
     * @param items  订单项列表 (可为 null, 不填充)
     */
    ScrmOrderDto toOrderDto(ScrmOrderEntity entity, List<ScrmOrderItemEntity> items) {
        ScrmOrderDto dto = new ScrmOrderDto();
        dto.setId(entity.getId());
        dto.setOrderNo(entity.getOrderNo());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setOrderType(entity.getOrderType());
        dto.setOrderStatus(entity.getOrderStatus());
        dto.setPaymentStatus(entity.getPaymentStatus());
        dto.setPaymentMethod(entity.getPaymentMethod());
        dto.setTotalAmount(entity.getTotalAmount());
        dto.setDiscountAmount(entity.getDiscountAmount());
        dto.setShippingAmount(entity.getShippingAmount());
        dto.setTaxAmount(entity.getTaxAmount());
        dto.setPaidAmount(entity.getPaidAmount());
        dto.setCurrency(entity.getCurrency());
        dto.setCouponId(entity.getCouponId());
        dto.setCouponCode(entity.getCouponCode());
        dto.setSalespersonId(entity.getSalespersonId());
        dto.setSalespersonName(entity.getSalespersonName());
        dto.setChannel(entity.getChannel());
        dto.setShippingAddress(entity.getShippingAddress());
        dto.setShippingName(entity.getShippingName());
        dto.setShippingPhone(entity.getShippingPhone());
        dto.setTrackingNo(entity.getTrackingNo());
        dto.setTrackingCompany(entity.getTrackingCompany());
        dto.setRemark(entity.getRemark());
        dto.setPaidAt(entity.getPaidAt());
        dto.setShippedAt(entity.getShippedAt());
        dto.setDeliveredAt(entity.getDeliveredAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCancelledAt(entity.getCancelledAt());
        dto.setItemsJson(entity.getItemsJson());
        if (items != null) {
            dto.setItems(items.stream().map(this::toOrderItemDto).collect(Collectors.toList()));
        }
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 订单项实体转 DTO, 供订单项子域复用。
     */
    ScrmOrderItemDto toOrderItemDto(ScrmOrderItemEntity entity) {
        ScrmOrderItemDto dto = new ScrmOrderItemDto();
        dto.setId(entity.getId());
        dto.setOrderId(entity.getOrderId());
        dto.setProductId(entity.getProductId());
        dto.setProductCode(entity.getProductCode());
        dto.setProductName(entity.getProductName());
        dto.setProductImage(entity.getProductImage());
        dto.setSpec(entity.getSpec());
        dto.setUnitPrice(entity.getUnitPrice());
        dto.setQuantity(entity.getQuantity());
        dto.setDiscountAmount(entity.getDiscountAmount());
        dto.setSubtotal(entity.getSubtotal());
        dto.setTaxRate(entity.getTaxRate());
        dto.setTaxAmount(entity.getTaxAmount());
        dto.setRemark(entity.getRemark());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}