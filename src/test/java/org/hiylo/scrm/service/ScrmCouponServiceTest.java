/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmCouponDto;
import org.hiylo.scrm.dto.ScrmCouponTemplateDto;
import org.hiylo.scrm.dto.ScrmCouponUseDto;
import org.hiylo.scrm.entity.ScrmCouponEntity;
import org.hiylo.scrm.entity.ScrmCouponTemplateEntity;
import org.hiylo.scrm.entity.ScrmCouponUsageLogEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCouponRepository;
import org.hiylo.scrm.repository.ScrmCouponTemplateRepository;
import org.hiylo.scrm.repository.ScrmCouponUsageLogRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmCouponService 单元测试
 * <p>
 * 聚焦优惠券模板管理 / 发券 / 领取 / 核销 / 退还 与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCouponService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCouponServiceTest {

    /** 优惠券模板数据仓库 Mock 桩 */
    @Mock
    private ScrmCouponTemplateRepository templateRepository;
    /** 优惠券数据仓库 Mock 桩 */
    @Mock
    private ScrmCouponRepository couponRepository;
    /** 优惠券使用日志数据仓库 Mock 桩 */
    @Mock
    private ScrmCouponUsageLogRepository usageLogRepository;
    /** 客户数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmCouponService service;

    @BeforeEach
    void setUp() {
        service = new ScrmCouponService(templateRepository, couponRepository, usageLogRepository, customerRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的优惠券模板实体 (用于 findById 返回)
     */
    private ScrmCouponTemplateEntity buildTemplateEntity(Long id, String status) {
        ScrmCouponTemplateEntity entity = new ScrmCouponTemplateEntity();
        entity.setId(id);
        entity.setTemplateName("满100减20券");
        entity.setCouponType("FIXED_AMOUNT");
        entity.setFaceValue(20d);
        entity.setThresholdAmount(100d);
        entity.setValidType("RELATIVE");
        entity.setValidDays(30);
        entity.setTotalQuantity(100);
        entity.setIssuedQuantity(0);
        entity.setUsedQuantity(0);
        entity.setClaimedQuantity(0);
        entity.setPerUserLimit(1);
        entity.setStatus(status);
        return entity;
    }

    /**
     * 构造已持久化的优惠券实例实体
     */
    private ScrmCouponEntity buildCouponEntity(Long id, String code, String status, Long customerId) {
        ScrmCouponEntity entity = new ScrmCouponEntity();
        entity.setId(id);
        entity.setTemplateId(10L);
        entity.setCouponCode(code);
        entity.setCustomerId(customerId);
        entity.setCustomerName(customerId != null ? "张三" : null);
        entity.setClaimSource("MANUAL");
        entity.setClaimedAt(customerId != null ? LocalDateTime.now() : null);
        entity.setExpiresAt(LocalDateTime.now().plusDays(30));
        entity.setStatus(status);
        return entity;
    }

    @Test
    @DisplayName("createTemplate: 写入归属账号与默认值后持久化")
    void createTemplate_success() throws ScrmException {
        ScrmCouponTemplateDto dto = new ScrmCouponTemplateDto();
        dto.setTemplateName("满100减20券");
        dto.setCouponType("FIXED_AMOUNT");
        dto.setFaceValue(20d);
        dto.setThresholdAmount(100d);
        dto.setValidType("RELATIVE");
        dto.setValidDays(30);
        dto.setTotalQuantity(100);
        when(templateRepository.save(any(ScrmCouponTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCouponTemplateDto result = service.createTemplate(dto);

        ArgumentCaptor<ScrmCouponTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmCouponTemplateEntity.class);
        verify(templateRepository, times(1)).save(captor.capture());
        ScrmCouponTemplateEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getIssuedQuantity()).isZero();
        assertThat(saved.getUsedQuantity()).isZero();
        assertThat(saved.getClaimedQuantity()).isZero();
        assertThat(saved.getPerUserLimit()).isEqualTo(1);
        assertThat(saved.getThresholdAmount()).isEqualTo(100d);
        assertThat(result.getTemplateName()).isEqualTo("满100减20券");
    }

    @Test
    @DisplayName("createTemplate: DISCOUNT 类型面值超过 1 抛 BAD_REQUEST")
    void createTemplate_discountFaceValueOutOfRange() {
        ScrmCouponTemplateDto dto = new ScrmCouponTemplateDto();
        dto.setTemplateName("折扣券");
        dto.setCouponType("DISCOUNT");
        dto.setFaceValue(1.5d); // 折扣率超过 1
        dto.setValidType("RELATIVE");
        dto.setValidDays(30);
        dto.setTotalQuantity(100);

        assertThatThrownBy(() -> service.createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("折扣类型面值需为 0~1 的折扣率");
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("issueToCustomer: 向客户发券, 写入券码并累加模板发放量")
    void issueToCustomer_success() throws ScrmException {
        ScrmCouponTemplateEntity template = buildTemplateEntity(10L, "ACTIVE");
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(100L);
        customer.setNickname("张三");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(couponRepository.findByCouponCode(any(String.class))).thenReturn(Optional.empty());
        when(couponRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepository.save(any(ScrmCouponTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(usageLogRepository.save(any(ScrmCouponUsageLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<ScrmCouponDto> result = service.issueToCustomer(10L, 100L, 2);

        ArgumentCaptor<ScrmCouponTemplateEntity> templateCaptor =
                ArgumentCaptor.forClass(ScrmCouponTemplateEntity.class);
        verify(templateRepository, times(1)).save(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getIssuedQuantity()).isEqualTo(2);
        assertThat(templateCaptor.getValue().getClaimedQuantity()).isEqualTo(2);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCouponCode()).isNotBlank();
        assertThat(result.get(0).getCustomerId()).isEqualTo(100L);
        assertThat(result.get(0).getClaimSource()).isEqualTo("MANUAL");
        assertThat(result.get(0).getStatus()).isEqualTo("UNUSED");
        verify(usageLogRepository, times(2)).save(any(ScrmCouponUsageLogEntity.class));
    }

    @Test
    @DisplayName("issueToCustomer: 模板未启用时抛 BAD_REQUEST")
    void issueToCustomer_templateNotActive() {
        ScrmCouponTemplateEntity template = buildTemplateEntity(10L, "INACTIVE");
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.issueToCustomer(10L, 100L, 1))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模板未启用");
        verify(couponRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("claimCoupon: 未归属券领取成功, 写入客户与领取时间")
    void claimCoupon_success() throws ScrmException {
        ScrmCouponEntity coupon = buildCouponEntity(50L, "CPN001", "UNUSED", null);
        ScrmCouponTemplateEntity template = buildTemplateEntity(10L, "ACTIVE");
        when(couponRepository.findByCouponCode("CPN001")).thenReturn(Optional.of(coupon));
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(couponRepository.findByTemplateIdAndCustomerIdAndStatusIn(eq(10L), eq(100L), any())).thenReturn(List.of());
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(100L);
        customer.setNickname("张三");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(couponRepository.save(any(ScrmCouponEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(templateRepository.save(any(ScrmCouponTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(usageLogRepository.save(any(ScrmCouponUsageLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.claimCoupon("CPN001", 100L);

        ArgumentCaptor<ScrmCouponEntity> captor =
                ArgumentCaptor.forClass(ScrmCouponEntity.class);
        verify(couponRepository, times(1)).save(captor.capture());
        ScrmCouponEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(100L);
        assertThat(saved.getCustomerName()).isEqualTo("张三");
        assertThat(saved.getClaimedAt()).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo(100L);
        verify(usageLogRepository, times(1)).save(any(ScrmCouponUsageLogEntity.class));
    }

    @Test
    @DisplayName("claimCoupon: 已被领取的券抛 CONFLICT")
    void claimCoupon_alreadyClaimed() {
        ScrmCouponEntity coupon = buildCouponEntity(50L, "CPN001", "UNUSED", 200L);
        when(couponRepository.findByCouponCode("CPN001")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.claimCoupon("CPN001", 100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("优惠券已被领取");
        verify(couponRepository, never()).save(any());
    }

    @Test
    @DisplayName("useCoupon: 满减券核销后状态置 USED 并记录抵扣金额")
    void useCoupon_success() throws ScrmException {
        ScrmCouponEntity coupon = buildCouponEntity(50L, "CPN001", "UNUSED", 100L);
        ScrmCouponTemplateEntity template = buildTemplateEntity(10L, "ACTIVE");
        template.setCouponType("FIXED_AMOUNT");
        template.setFaceValue(20d);
        template.setThresholdAmount(100d);
        when(couponRepository.findByCouponCode("CPN001")).thenReturn(Optional.of(coupon));
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(couponRepository.save(any(ScrmCouponEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(templateRepository.save(any(ScrmCouponTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(usageLogRepository.save(any(ScrmCouponUsageLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCouponUseDto useDto = new ScrmCouponUseDto();
        useDto.setCouponCode("CPN001");
        useDto.setOrderId("ORD-20260805-001");
        useDto.setOrderAmount(150d);

        var result = service.useCoupon(useDto);

        ArgumentCaptor<ScrmCouponEntity> captor =
                ArgumentCaptor.forClass(ScrmCouponEntity.class);
        verify(couponRepository, times(1)).save(captor.capture());
        ScrmCouponEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("USED");
        assertThat(saved.getUsedAt()).isNotNull();
        assertThat(saved.getUsedOrder()).isEqualTo("ORD-20260805-001");
        // FIXED_AMOUNT 抵扣 = min(面值, 订单金额) = min(20, 150) = 20
        assertThat(saved.getUsedAmount()).isEqualTo(20d);
        ArgumentCaptor<ScrmCouponTemplateEntity> templateCaptor =
                ArgumentCaptor.forClass(ScrmCouponTemplateEntity.class);
        verify(templateRepository, times(1)).save(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getUsedQuantity()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo("USED");
    }

    @Test
    @DisplayName("useCoupon: 订单金额未达门槛时抛 BAD_REQUEST")
    void useCoupon_belowThreshold() {
        ScrmCouponEntity coupon = buildCouponEntity(50L, "CPN001", "UNUSED", 100L);
        ScrmCouponTemplateEntity template = buildTemplateEntity(10L, "ACTIVE");
        template.setThresholdAmount(100d);
        when(couponRepository.findByCouponCode("CPN001")).thenReturn(Optional.of(coupon));
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        ScrmCouponUseDto useDto = new ScrmCouponUseDto();
        useDto.setCouponCode("CPN001");
        useDto.setOrderId("ORD-001");
        useDto.setOrderAmount(50d);

        assertThatThrownBy(() -> service.useCoupon(useDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("订单金额未达使用门槛");
        verify(couponRepository, never()).save(any());
    }

    @Test
    @DisplayName("returnCoupon: 已使用券退还后状态置 RETURNED 并扣减已使用量")
    void returnCoupon_success() throws ScrmException {
        ScrmCouponEntity coupon = buildCouponEntity(50L, "CPN001", "USED", 100L);
        coupon.setUsedAmount(20d);
        ScrmCouponTemplateEntity template = buildTemplateEntity(10L, "ACTIVE");
        template.setUsedQuantity(1);
        when(couponRepository.findById(50L)).thenReturn(Optional.of(coupon));
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(couponRepository.save(any(ScrmCouponEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(templateRepository.save(any(ScrmCouponTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(usageLogRepository.save(any(ScrmCouponUsageLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.returnCoupon(50L, "订单退款");

        ArgumentCaptor<ScrmCouponEntity> captor =
                ArgumentCaptor.forClass(ScrmCouponEntity.class);
        verify(couponRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("RETURNED");
        ArgumentCaptor<ScrmCouponTemplateEntity> templateCaptor =
                ArgumentCaptor.forClass(ScrmCouponTemplateEntity.class);
        verify(templateRepository, times(1)).save(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getUsedQuantity()).isZero();
        assertThat(result.getStatus()).isEqualTo("RETURNED");
    }

    @Test
    @DisplayName("returnCoupon: 非 USED 状态退还抛 BAD_REQUEST")
    void returnCoupon_invalidStatus() {
        ScrmCouponEntity coupon = buildCouponEntity(50L, "CPN001", "UNUSED", 100L);
        when(couponRepository.findById(50L)).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.returnCoupon(50L, "误退"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("优惠券状态非法");
        verify(couponRepository, never()).save(any());
    }

    
}
