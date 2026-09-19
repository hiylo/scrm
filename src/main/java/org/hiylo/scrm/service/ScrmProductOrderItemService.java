/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProductOrderItemService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmOrderItemDto;
import org.hiylo.scrm.entity.ScrmOrderEntity;
import org.hiylo.scrm.entity.ScrmOrderItemEntity;
import org.hiylo.scrm.entity.ScrmProductEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmOrderItemRepository;
import org.hiylo.scrm.repository.ScrmProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SCRM 订单项管理服务 (订单项子域)。
 * <p>
 * 承载订单项管理子域: 订单项列表查询 / 添加 / 更新 / 移除, 添加与更新时校验订单状态、
 * 关联商品快照填充与库存扣减, 变更后通过订单子域重算订单金额。订单查询、金额重算与
 * DTO 转换等能力托管在 {@link ScrmProductOrderOrderService}, 本类通过注入复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmProductOrderItemService {

    /** 订单项数据访问层 */
    private final ScrmOrderItemRepository orderItemRepository;

    /** 商品数据访问层 (添加订单项时扣库存) */
    private final ScrmProductRepository productRepository;

    /** 订单管理子域服务 (订单查询 / 金额重算 / 转换 / 共享常量) */
    private final ScrmProductOrderOrderService orderService;

    /** 商品管理子域服务 (查询商品) */
    private final ScrmProductOrderProductService productService;

    // ============================================================
    // 订单项管理
    // ============================================================

    /**
     * 查询订单项列表。
     *
     * @param orderId 订单 ID
     * @return 订单项列表
     */
    @Transactional(readOnly = true)
    public List<ScrmOrderItemDto> listOrderItems(Long orderId) {
        return orderService.listOrderItemEntities(orderId).stream()
                .map(orderService::toOrderItemDto)
                .collect(Collectors.toList());
    }

    /**
     * 添加订单项 (仅 PENDING / CONFIRMED 状态可添加, 添加后重算订单金额)。
     *
     * @param orderId 订单 ID
     * @param dto     订单项参数
     * @return 添加后的订单项
     * @throws ScrmException 订单不存在 / 状态非法
     */
    @Transactional
    public ScrmOrderItemDto addOrderItem(Long orderId, ScrmOrderItemDto dto) throws ScrmException {
        ScrmOrderEntity order = orderService.findOrderOrThrow(orderId);
        if (!ScrmProductOrderOrderService.ORDER_STATUS_PENDING.equals(order.getOrderStatus()) && !ScrmProductOrderOrderService.ORDER_STATUS_CONFIRMED.equals(order.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单状态非法, 仅 PENDING / CONFIRMED 可添加订单项: currentStatus=" + order.getOrderStatus());
        }
        ScrmOrderItemEntity item = orderService.buildOrderItem(orderId, dto);
        // 关联商品时填充快照并扣库存
        if (item.getProductId() != null) {
            ScrmProductEntity product = productService.findProductOrThrow(item.getProductId());
            int stock = product.getStock() != null ? product.getStock() : 0;
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            if (stock < qty) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "商品库存不足: productId=" + product.getId());
            }
            product.setStock(stock - qty);
            product.setSalesCount((product.getSalesCount() != null ? product.getSalesCount() : 0) + qty);
            productRepository.save(product);
            if (item.getProductCode() == null) item.setProductCode(product.getProductCode());
            if (item.getProductName() == null) item.setProductName(product.getProductName());
            if (item.getProductImage() == null) item.setProductImage(product.getImageUrl());
            if (item.getSpec() == null) item.setSpec(product.getSpec());
            if (item.getUnitPrice() == null || item.getUnitPrice() == 0d) item.setUnitPrice(product.getPrice());
        }
        double unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : 0d;
        int qty = item.getQuantity() != null ? item.getQuantity() : 0;
        double discount = item.getDiscountAmount() != null ? item.getDiscountAmount() : 0d;
        item.setSubtotal(ScrmProductOrderOrderService.round(unitPrice * qty - discount));
        double taxRate = item.getTaxRate() != null ? item.getTaxRate() : 0d;
        item.setTaxAmount(ScrmProductOrderOrderService.round(item.getSubtotal() * taxRate));
        item = orderItemRepository.save(item);
        orderService.recalcOrderAmount(order);
        log.info("添加订单项: orderId={}, itemId={}", orderId, item.getId());
        return orderService.toOrderItemDto(item);
    }

    /**
     * 更新订单项 (字段非空才覆盖, 仅 PENDING / CONFIRMED 状态可更新, 更新后重算订单金额)。
     *
     * @param id  订单项 ID
     * @param dto 订单项参数
     * @return 更新后的订单项
     * @throws ScrmException 订单项不存在 / 订单状态非法
     */
    @Transactional
    public ScrmOrderItemDto updateOrderItem(Long id, ScrmOrderItemDto dto) throws ScrmException {
        ScrmOrderItemEntity item = findOrderItemOrThrow(id);
        ScrmOrderEntity order = orderService.findOrderOrThrow(item.getOrderId());
        if (!ScrmProductOrderOrderService.ORDER_STATUS_PENDING.equals(order.getOrderStatus()) && !ScrmProductOrderOrderService.ORDER_STATUS_CONFIRMED.equals(order.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单状态非法, 仅 PENDING / CONFIRMED 可更新订单项: currentStatus=" + order.getOrderStatus());
        }
        if (dto == null) {
            throw ScrmException.badRequest("订单项参数不能为空");
        }
        if (dto.getProductId() != null) item.setProductId(dto.getProductId());
        if (dto.getProductCode() != null) item.setProductCode(dto.getProductCode());
        if (dto.getProductName() != null) item.setProductName(dto.getProductName());
        if (dto.getProductImage() != null) item.setProductImage(dto.getProductImage());
        if (dto.getSpec() != null) item.setSpec(dto.getSpec());
        if (dto.getUnitPrice() != null) item.setUnitPrice(dto.getUnitPrice());
        if (dto.getQuantity() != null) item.setQuantity(dto.getQuantity());
        if (dto.getDiscountAmount() != null) item.setDiscountAmount(dto.getDiscountAmount());
        if (dto.getTaxRate() != null) item.setTaxRate(dto.getTaxRate());
        if (dto.getRemark() != null) item.setRemark(dto.getRemark());
        // 重算小计与税额
        double unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : 0d;
        int qty = item.getQuantity() != null ? item.getQuantity() : 0;
        double discount = item.getDiscountAmount() != null ? item.getDiscountAmount() : 0d;
        item.setSubtotal(ScrmProductOrderOrderService.round(unitPrice * qty - discount));
        double taxRate = item.getTaxRate() != null ? item.getTaxRate() : 0d;
        item.setTaxAmount(ScrmProductOrderOrderService.round(item.getSubtotal() * taxRate));
        item = orderItemRepository.save(item);
        orderService.recalcOrderAmount(order);
        log.info("更新订单项: id={}, orderId={}", id, item.getOrderId());
        return orderService.toOrderItemDto(item);
    }

    /**
     * 移除订单项 (仅 PENDING / CONFIRMED 状态可移除, 移除后重算订单金额)。
     *
     * @param id 订单项 ID
     * @throws ScrmException 订单项不存在 / 订单状态非法
     */
    @Transactional
    public void removeOrderItem(Long id) throws ScrmException {
        ScrmOrderItemEntity item = findOrderItemOrThrow(id);
        ScrmOrderEntity order = orderService.findOrderOrThrow(item.getOrderId());
        if (!ScrmProductOrderOrderService.ORDER_STATUS_PENDING.equals(order.getOrderStatus()) && !ScrmProductOrderOrderService.ORDER_STATUS_CONFIRMED.equals(order.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "订单状态非法, 仅 PENDING / CONFIRMED 可移除订单项: currentStatus=" + order.getOrderStatus());
        }
        orderItemRepository.delete(item);
        orderService.recalcOrderAmount(order);
        log.info("移除订单项: id={}, orderId={}", id, item.getOrderId());
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询订单项, 不存在抛异常
     */
    private ScrmOrderItemEntity findOrderItemOrThrow(Long id) throws ScrmException {
        ScrmOrderItemEntity item = orderItemRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "订单项不存在: id=" + id));

        return item;
    }
}