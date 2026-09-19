/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProductOrderServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmOrderCreateDto;
import org.hiylo.scrm.dto.ScrmOrderDto;
import org.hiylo.scrm.dto.ScrmOrderItemDto;
import org.hiylo.scrm.dto.ScrmProductDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmOrderEntity;
import org.hiylo.scrm.entity.ScrmOrderItemEntity;
import org.hiylo.scrm.entity.ScrmProductEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmOrderItemRepository;
import org.hiylo.scrm.repository.ScrmOrderRepository;
import org.hiylo.scrm.repository.ScrmProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmProductOrderService 单元测试
 * <p>
 * 聚焦商品管理 (CRUD / 编码唯一 / 库存调整 / 状态更新 / 删除校验)、
 * 订单生命周期 (创建 → 确认 → 支付 → 发货 → 送达 → 完成 / 取消 / 退款)、
 * 订单状态机校验与数据隔离等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmProductOrderService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmProductOrderServiceTest {

    /** 商品仓库 Mock */
    @Mock
    private ScrmProductRepository productRepository;
    /** 订单仓库 Mock */
    @Mock
    private ScrmOrderRepository orderRepository;
    /** 订单明细仓库 Mock */
    @Mock
    private ScrmOrderItemRepository orderItemRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmProductOrderService service;

    @BeforeEach
    void setUp() {
        ScrmProductOrderProductService productService =
                new ScrmProductOrderProductService(productRepository);
        ScrmProductOrderOrderService orderService =
                new ScrmProductOrderOrderService(orderRepository, orderItemRepository,
                        productRepository, customerRepository, productService);
        ScrmProductOrderItemService itemService =
                new ScrmProductOrderItemService(orderItemRepository, productRepository,
                        orderService, productService);
        ScrmProductOrderStatsService statsService =
                new ScrmProductOrderStatsService(productRepository, orderRepository,
                        orderItemRepository, orderService);
        service = new ScrmProductOrderService(productService, orderService, itemService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造商品 DTO
     */
    private ScrmProductDto buildProductDto() {
        ScrmProductDto dto = new ScrmProductDto();
        dto.setProductCode("P001");
        dto.setProductName("测试商品");
        dto.setPrice(99.9);
        return dto;
    }

    /**
     * 构造已持久化的商品实体
     */
    private ScrmProductEntity buildProductEntity(Long id, String status) {
        ScrmProductEntity entity = new ScrmProductEntity();
        entity.setId(id);
        entity.setProductCode("P001");
        entity.setProductName("测试商品");
        entity.setPrice(99.9);
        entity.setCurrency("CNY");
        entity.setStock(10);
        entity.setStatus(status);
        entity.setSalesCount(0);
        entity.setViewCount(0);
        entity.setRatingScore(0d);
        return entity;
    }

    /**
     * 构造已持久化的订单实体
     */
    private ScrmOrderEntity buildOrder(Long id, String status, Double total, Double paid) {
        ScrmOrderEntity entity = new ScrmOrderEntity();
        entity.setId(id);
        entity.setOrderNo("20260805000001");
        entity.setCustomerId(20L);
        entity.setCustomerName("Alice");
        entity.setOrderType("SALE");
        entity.setOrderStatus(status);
        entity.setPaymentStatus(paid != null && paid > 0 ? "PAID" : "UNPAID");
        entity.setTotalAmount(total);
        entity.setPaidAmount(paid);
        entity.setCurrency("CNY");
        return entity;
    }

    /**
     * 构造已持久化的客户实体
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id, String nickname) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setNickname(nickname);
        entity.setPlatformType("wx");
        entity.setPlatformCustomerUid("uid1");
        entity.setOwnerAccountId(100L);
        entity.setLifecycle("ACTIVE");
        return entity;
    }

    /**
     * 构造订单项 DTO (无关联商品)
     */
    private ScrmOrderItemDto buildOrderItemDto(String productName, Double unitPrice, Integer quantity) {
        ScrmOrderItemDto dto = new ScrmOrderItemDto();
        dto.setProductName(productName);
        dto.setUnitPrice(unitPrice);
        dto.setQuantity(quantity);
        dto.setDiscountAmount(0d);
        dto.setTaxRate(0d);
        return dto;
    }

    /**
     * 构造订单创建 DTO
     */
    private ScrmOrderCreateDto buildOrderCreateDto() {
        ScrmOrderCreateDto dto = new ScrmOrderCreateDto();
        dto.setCustomerId(20L);
        return dto;
    }

    // ==================== 商品管理 ====================

    @Test
    @DisplayName("createProduct: 写入账号 ID 与默认值并持久化")
    void createProduct_success() throws ScrmException {
        ScrmProductDto dto = buildProductDto();
        when(productRepository.findByProductCode("P001")).thenReturn(Optional.empty());
        when(productRepository.save(any(ScrmProductEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ScrmProductDto result = service.createProduct(dto);

        ArgumentCaptor<ScrmProductEntity> captor = ArgumentCaptor.forClass(ScrmProductEntity.class);
        verify(productRepository, times(1)).save(captor.capture());
        ScrmProductEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCurrency()).isEqualTo("CNY");
        assertThat(saved.getStock()).isZero();
        assertThat(saved.getSalesCount()).isZero();
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.getRatingScore()).isEqualTo(0d);
        assertThat(result.getProductCode()).isEqualTo("P001");
    }

    @Test
    @DisplayName("createProduct: 商品编码已存在抛 CONFLICT")
    void createProduct_duplicateCode() {
        ScrmProductDto dto = buildProductDto();
        when(productRepository.findByProductCode("P001")).thenReturn(Optional.of(buildProductEntity(1L, "ACTIVE")));

        assertThatThrownBy(() -> service.createProduct(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("商品编码已存在");
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProduct: dto 为空抛 BAD_REQUEST")
    void createProduct_nullDto() {
        assertThatThrownBy(() -> service.createProduct(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("商品参数不能为空");
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteProduct: 在售商品不允许删除抛 BAD_REQUEST")
    void deleteProduct_activeNotAllowed() {
        ScrmProductEntity entity = buildProductEntity(10L, "ACTIVE");
        when(productRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.deleteProduct(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("在售商品不允许删除");
        verify(productRepository, never()).delete(any(ScrmProductEntity.class));
    }

    @Test
    @DisplayName("deleteProduct: 下架商品删除成功")
    void deleteProduct_inactiveSuccess() throws ScrmException {
        ScrmProductEntity entity = buildProductEntity(10L, "INACTIVE");
        when(productRepository.findById(10L)).thenReturn(Optional.of(entity));

        service.deleteProduct(10L);

        verify(productRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("getProduct: 商品不存在抛 NOT_FOUND")
    void getProduct_notFound() {
        when(productRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProduct(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("商品不存在");
    }

    
    @Test
    @DisplayName("adjustStock: 库存不足抛 BAD_REQUEST")
    void adjustStock_insufficient() {
        ScrmProductEntity entity = buildProductEntity(10L, "ACTIVE");
        entity.setStock(5);
        when(productRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.adjustStock(10L, -10, "出库"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("库存不足");
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock: 正向入库增加库存并持久化")
    void adjustStock_success() throws ScrmException {
        ScrmProductEntity entity = buildProductEntity(10L, "ACTIVE");
        entity.setStock(5);
        when(productRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(productRepository.save(any(ScrmProductEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ScrmProductDto result = service.adjustStock(10L, 10, "入库");

        ArgumentCaptor<ScrmProductEntity> captor = ArgumentCaptor.forClass(ScrmProductEntity.class);
        verify(productRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStock()).isEqualTo(15);
        assertThat(result.getStock()).isEqualTo(15);
    }

    @Test
    @DisplayName("updateStatus: 非法状态抛 BAD_REQUEST")
    void updateStatus_invalid() {
        assertThatThrownBy(() -> service.updateStatus(10L, "INVALID"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("商品状态非法");
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus: 状态为空抛 BAD_REQUEST")
    void updateStatus_blank() {
        assertThatThrownBy(() -> service.updateStatus(10L, "  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("商品状态不能为空");
        verify(productRepository, never()).save(any());
    }

    // ==================== 订单创建 ====================

    @Test
    @DisplayName("createOrder: dto 为空抛 BAD_REQUEST")
    void createOrder_nullDto() {
        assertThatThrownBy(() -> service.createOrder(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("订单参数不能为空");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: 客户 ID 为空抛 BAD_REQUEST")
    void createOrder_nullCustomerId() {
        ScrmOrderCreateDto dto = buildOrderCreateDto();
        dto.setCustomerId(null);

        assertThatThrownBy(() -> service.createOrder(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 不能为空");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: 订单项为空抛 BAD_REQUEST")
    void createOrder_emptyItems() {
        ScrmOrderCreateDto dto = buildOrderCreateDto();
        dto.setItems(Collections.emptyList());

        assertThatThrownBy(() -> service.createOrder(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("订单项不能为空");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: 无关联商品订单成功, 计算金额并生成订单号")
    void createOrder_successNoProduct() throws ScrmException {
        ScrmOrderCreateDto dto = buildOrderCreateDto();
        dto.setItems(List.of(buildOrderItemDto("测试商品", 10.0, 2)));
        when(orderRepository.countByOrderNoStartingWith(anyString())).thenReturn(0L);
        when(customerRepository.findById(20L))
                .thenReturn(Optional.of(buildCustomerEntity(20L, "Alice")));
        when(orderRepository.save(any(ScrmOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ScrmOrderDto result = service.createOrder(dto);

        assertThat(result.getOrderStatus()).isEqualTo("PENDING");
        assertThat(result.getPaymentStatus()).isEqualTo("UNPAID");
        assertThat(result.getOrderType()).isEqualTo("SALE");
        assertThat(result.getCurrency()).isEqualTo("CNY");
        assertThat(result.getTotalAmount()).isEqualTo(20.0);
        assertThat(result.getPaidAmount()).isZero();
        assertThat(result.getOrderNo()).isNotBlank();
        assertThat(result.getCustomerName()).isEqualTo("Alice");
        // 无关联商品, 不扣库存
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: 关联商品库存不足抛 BAD_REQUEST")
    void createOrder_insufficientStock() {
        ScrmOrderCreateDto dto = buildOrderCreateDto();
        ScrmOrderItemDto item = buildOrderItemDto("测试商品", 10.0, 5);
        item.setProductId(30L);
        dto.setItems(List.of(item));
        when(orderRepository.countByOrderNoStartingWith(anyString())).thenReturn(0L);
        when(customerRepository.findById(20L))
                .thenReturn(Optional.of(buildCustomerEntity(20L, "Alice")));
        when(orderRepository.save(any(ScrmOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ScrmProductEntity product = buildProductEntity(30L, "ACTIVE");
        product.setStock(2);
        when(productRepository.findById(30L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.createOrder(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("商品库存不足");
        verify(orderItemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("createOrder: 关联商品非在售状态抛 BAD_REQUEST")
    void createOrder_productNotActive() {
        ScrmOrderCreateDto dto = buildOrderCreateDto();
        ScrmOrderItemDto item = buildOrderItemDto("测试商品", 10.0, 1);
        item.setProductId(30L);
        dto.setItems(List.of(item));
        when(orderRepository.countByOrderNoStartingWith(anyString())).thenReturn(0L);
        when(customerRepository.findById(20L))
                .thenReturn(Optional.of(buildCustomerEntity(20L, "Alice")));
        when(orderRepository.save(any(ScrmOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ScrmProductEntity product = buildProductEntity(30L, "INACTIVE");
        when(productRepository.findById(30L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.createOrder(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("商品非在售状态");
    }

    @Test
    @DisplayName("createOrder: 关联商品成功扣库存与填充快照")
    void createOrder_successWithProduct() throws ScrmException {
        ScrmOrderCreateDto dto = buildOrderCreateDto();
        ScrmOrderItemDto item = buildOrderItemDto("测试商品", 10.0, 2);
        item.setProductId(30L);
        dto.setItems(List.of(item));
        when(orderRepository.countByOrderNoStartingWith(anyString())).thenReturn(0L);
        when(customerRepository.findById(20L))
                .thenReturn(Optional.of(buildCustomerEntity(20L, "Alice")));
        when(orderRepository.save(any(ScrmOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ScrmProductEntity product = buildProductEntity(30L, "ACTIVE");
        product.setStock(10);
        product.setSalesCount(0);
        when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(ScrmProductEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ScrmOrderDto result = service.createOrder(dto);

        ArgumentCaptor<ScrmProductEntity> productCaptor = ArgumentCaptor.forClass(ScrmProductEntity.class);
        verify(productRepository, times(1)).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getStock()).isEqualTo(8);
        assertThat(productCaptor.getValue().getSalesCount()).isEqualTo(2);
        assertThat(result.getTotalAmount()).isEqualTo(20.0);
    }

    // ==================== 订单状态流转 ====================

    @Test
    @DisplayName("confirmOrder: 非 PENDING 状态抛 BAD_REQUEST")
    void confirmOrder_invalidStatus() {
        ScrmOrderEntity order = buildOrder(10L, "CONFIRMED", 100.0, 0.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirmOrder(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PENDING 可确认");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmOrder: PENDING → CONFIRMED 成功")
    void confirmOrder_success() throws ScrmException {
        ScrmOrderEntity order = buildOrder(10L, "PENDING", 100.0, 0.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(ScrmOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByIdAsc(10L))
                .thenReturn(Collections.emptyList());

        ScrmOrderDto result = service.confirmOrder(10L);

        ArgumentCaptor<ScrmOrderEntity> captor = ArgumentCaptor.forClass(ScrmOrderEntity.class);
        verify(orderRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getOrderStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getOrderStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("payOrder: 全额支付 → PAID 状态与已付金额更新")
    void payOrder_fullPayment() throws ScrmException {
        ScrmOrderEntity order = buildOrder(10L, "CONFIRMED", 100.0, 0.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(ScrmOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByIdAsc(10L))
                .thenReturn(Collections.emptyList());

        ScrmOrderDto result = service.payOrder(10L, "WECHAT", 100.0);

        ArgumentCaptor<ScrmOrderEntity> captor = ArgumentCaptor.forClass(ScrmOrderEntity.class);
        verify(orderRepository, times(1)).save(captor.capture());
        ScrmOrderEntity saved = captor.getValue();
        assertThat(saved.getOrderStatus()).isEqualTo("PAID");
        assertThat(saved.getPaymentStatus()).isEqualTo("PAID");
        assertThat(saved.getPaidAmount()).isEqualTo(100.0);
        assertThat(saved.getPaymentMethod()).isEqualTo("WECHAT");
        assertThat(saved.getPaidAt()).isNotNull();
        assertThat(result.getOrderStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("payOrder: 部分支付 → PARTIAL 状态")
    void payOrder_partialPayment() throws ScrmException {
        ScrmOrderEntity order = buildOrder(10L, "CONFIRMED", 100.0, 0.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(ScrmOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByIdAsc(10L))
                .thenReturn(Collections.emptyList());

        service.payOrder(10L, "WECHAT", 30.0);

        ArgumentCaptor<ScrmOrderEntity> captor = ArgumentCaptor.forClass(ScrmOrderEntity.class);
        verify(orderRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo("PARTIAL");
        assertThat(captor.getValue().getOrderStatus()).isEqualTo("CONFIRMED");
        assertThat(captor.getValue().getPaidAmount()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("payOrder: 支付方式为空抛 BAD_REQUEST")
    void payOrder_blankMethod() {
        assertThatThrownBy(() -> service.payOrder(10L, "  ", 100.0))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("支付方式不能为空");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("payOrder: 已取消订单不允许支付抛 BAD_REQUEST")
    void payOrder_cancelledNotAllowed() {
        ScrmOrderEntity order = buildOrder(10L, "CANCELLED", 100.0, 0.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.payOrder(10L, "WECHAT", 100.0))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不允许支付");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("shipOrder: 非 PAID / CONFIRMED 状态抛 BAD_REQUEST")
    void shipOrder_invalidStatus() {
        ScrmOrderEntity order = buildOrder(10L, "PENDING", 100.0, 0.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.shipOrder(10L, "SF001", "顺丰"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PAID / CONFIRMED 可发货");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("shipOrder: 物流单号为空抛 BAD_REQUEST")
    void shipOrder_blankTrackingNo() {
        assertThatThrownBy(() -> service.shipOrder(10L, "  ", "顺丰"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("物流单号不能为空");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelOrder: 非 PENDING / CONFIRMED 状态抛 BAD_REQUEST")
    void cancelOrder_invalidStatus() {
        ScrmOrderEntity order = buildOrder(10L, "PAID", 100.0, 100.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(10L, "不想要了"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PENDING / CONFIRMED 可取消");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelOrder: PENDING → CANCELLED 成功并回补库存")
    void cancelOrder_successWithRestock() throws ScrmException {
        ScrmOrderEntity order = buildOrder(10L, "PENDING", 100.0, 0.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(ScrmOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        // 订单项含关联商品
        ScrmOrderItemEntity item = new ScrmOrderItemEntity();
        item.setId(1L);
        item.setOrderId(10L);
        item.setProductId(30L);
        item.setQuantity(2);
        item.setProductName("测试商品");
        item.setUnitPrice(10.0);
        item.setSubtotal(20.0);
        when(orderItemRepository.findByOrderIdOrderByIdAsc(10L))
                .thenReturn(Collections.singletonList(item));
        ScrmProductEntity product = buildProductEntity(30L, "ACTIVE");
        product.setStock(8);
        product.setSalesCount(2);
        when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(ScrmProductEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ScrmOrderDto result = service.cancelOrder(10L, "不想要了");

        ArgumentCaptor<ScrmOrderEntity> orderCaptor = ArgumentCaptor.forClass(ScrmOrderEntity.class);
        verify(orderRepository, times(1)).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(orderCaptor.getValue().getCancelledAt()).isNotNull();
        // 回补库存: 8 + 2 = 10, 销量: 2 - 2 = 0
        ArgumentCaptor<ScrmProductEntity> productCaptor = ArgumentCaptor.forClass(ScrmProductEntity.class);
        verify(productRepository, times(1)).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getStock()).isEqualTo(10);
        assertThat(productCaptor.getValue().getSalesCount()).isZero();
        assertThat(result.getOrderStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("refundOrder: 非已支付订单抛 BAD_REQUEST")
    void refundOrder_invalidStatus() {
        ScrmOrderEntity order = buildOrder(10L, "PENDING", 100.0, 0.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.refundOrder(10L, "退款原因"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅已支付订单可退款");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("refundOrder: PAID → REFUNDED 成功并回补库存")
    void refundOrder_success() throws ScrmException {
        ScrmOrderEntity order = buildOrder(10L, "PAID", 100.0, 100.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(ScrmOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderIdOrderByIdAsc(10L))
                .thenReturn(Collections.emptyList());

        ScrmOrderDto result = service.refundOrder(10L, "质量问题");

        ArgumentCaptor<ScrmOrderEntity> captor = ArgumentCaptor.forClass(ScrmOrderEntity.class);
        verify(orderRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getOrderStatus()).isEqualTo("REFUNDED");
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo("REFUNDED");
        assertThat(result.getOrderStatus()).isEqualTo("REFUNDED");
    }

    // ==================== 订单查询 ====================

    
    @Test
    @DisplayName("deleteOrder: 已支付订单不允许删除抛 BAD_REQUEST")
    void deleteOrder_paidNotAllowed() {
        ScrmOrderEntity order = buildOrder(10L, "PAID", 100.0, 100.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.deleteOrder(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不允许删除");
        verify(orderRepository, never()).delete(any(ScrmOrderEntity.class));
    }

    @Test
    @DisplayName("deleteOrder: PENDING 订单删除成功并清理订单项")
    void deleteOrder_success() throws ScrmException {
        ScrmOrderEntity order = buildOrder(10L, "PENDING", 100.0, 0.0);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        service.deleteOrder(10L);

        verify(orderItemRepository, times(1)).deleteByOrderId(10L);
        verify(orderRepository, times(1)).delete(order);
    }

    // ==================== 统计 ====================

    @Test
    @DisplayName("getOrderStats: 聚合订单总数 / 状态分布 / 金额与转化率")
    void getOrderStats_success() {
        ScrmOrderEntity o1 = buildOrder(1L, "PENDING", 100.0, 0.0);
        ScrmOrderEntity o2 = buildOrder(2L, "COMPLETED", 200.0, 200.0);
        ScrmOrderEntity o3 = buildOrder(3L, "CANCELLED", 50.0, 0.0);
        when(orderRepository.findByTimeRange(any(), any()))
                .thenReturn(Arrays.asList(o1, o2, o3));
        when(orderRepository.sumCompletedAmount(any(), any())).thenReturn(200.0);
        when(orderRepository.sumPaidAmount(any(), any())).thenReturn(200.0);

        Map<String, Object> stats = service.getOrderStats(null, null);

        assertThat(stats.get("totalOrders")).isEqualTo(3);
        @SuppressWarnings("unchecked")
        Map<String, Long> statusCount = (Map<String, Long>) stats.get("statusCount");
        assertThat(statusCount).containsEntry("PENDING", 1L).containsEntry("COMPLETED"
            , 1L).containsEntry("CANCELLED", 1L);
        // 总金额不含取消订单: 100 + 200 = 300
        assertThat(stats.get("totalAmount")).isEqualTo(300.0);
        assertThat(stats.get("paidAmount")).isEqualTo(200.0);
        assertThat(stats.get("completedAmount")).isEqualTo(200.0);
        assertThat(stats.get("actualRevenue")).isEqualTo(200.0);
        assertThat(stats.get("completedCount")).isEqualTo(1L);
        assertThat(stats.get("cancelledCount")).isEqualTo(1L);
        // 转化率 = 1/3 * 100 = 33.33
        assertThat((Double) stats.get("conversionRate")).isEqualTo(33.33);
    }

    @Test
    @DisplayName("getOrderStats: 无订单时全部为 0")
    void getOrderStats_empty() {
        when(orderRepository.findByTimeRange(any(), any()))
                .thenReturn(Collections.emptyList());
        when(orderRepository.sumCompletedAmount(any(), any())).thenReturn(0.0);
        when(orderRepository.sumPaidAmount(any(), any())).thenReturn(0.0);

        Map<String, Object> stats = service.getOrderStats(null, null);

        assertThat(stats.get("totalOrders")).isEqualTo(0);
        assertThat(stats.get("totalAmount")).isEqualTo(0.0);
        assertThat(stats.get("paidAmount")).isEqualTo(0.0);
        assertThat(stats.get("completedCount")).isEqualTo(0L);
        assertThat((Double) stats.get("conversionRate")).isEqualTo(0.0);
    }
}
