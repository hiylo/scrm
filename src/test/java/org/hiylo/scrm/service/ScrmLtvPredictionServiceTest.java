/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLtvPredictionServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmLtvModelDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerLtvEntity;
import org.hiylo.scrm.entity.ScrmLtvModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerLtvRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmLtvCohortRepository;
import org.hiylo.scrm.repository.ScrmLtvModelRepository;
import org.hiylo.scrm.repository.ScrmOrderRepository;
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
 * ScrmLtvPredictionService 单元测试
 * <p>
 * 聚焦 LTV 模型管理 (创建 / 更新 / 发布 / 设默认 / 复制 / 编码唯一性校验)、
 * LTV 计算 (模型/客户校验 / 无订单默认值)、价值分层判定 (默认阈值 / 自定义阈值)、
 * 简单 LTV 与 DCF 计算、流失预测与数据隔离校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmLtvPredictionService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmLtvPredictionServiceTest {

    /** LTV 模型仓库 Mock */
    @Mock
    private ScrmLtvModelRepository modelRepository;
    /** 客户 LTV 仓库 Mock */
    @Mock
    private ScrmCustomerLtvRepository ltvRepository;
    /** LTV 队列分析仓库 Mock */
    @Mock
    private ScrmLtvCohortRepository cohortRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 订单仓库 Mock */
    @Mock
    private ScrmOrderRepository orderRepository;

    /** 被测服务实例 */
    private ScrmLtvPredictionService service;

    @BeforeEach
    void setUp() {
        ScrmLtvModelService modelService = new ScrmLtvModelService(modelRepository, ltvRepository);
        ScrmLtvCalculationService calculationService =
                new ScrmLtvCalculationService(ltvRepository, customerRepository, orderRepository, modelService);
        ScrmLtvForecastService forecastService =
                new ScrmLtvForecastService(ltvRepository, orderRepository, calculationService);
        ScrmLtvCohortService cohortService =
                new ScrmLtvCohortService(ltvRepository, cohortRepository, customerRepository, modelService);
        service = new ScrmLtvPredictionService(modelService, calculationService, forecastService, cohortService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的 LTV 模型实体 (用于 findById 返回)
     */
    private ScrmLtvModelEntity buildModelEntity(Long id, String modelCode, String calculationMethod) {
        ScrmLtvModelEntity entity = new ScrmLtvModelEntity();
        entity.setId(id);
        entity.setModelName("历史型 LTV 模型");
        entity.setModelCode(modelCode);
        entity.setModelType("HISTORICAL");
        entity.setCalculationMethod(calculationMethod);
        entity.setLookbackDays(365);
        entity.setForecastDays(365);
        entity.setDiscountRate(0.1d);
        entity.setChurnRate(0.05d);
        entity.setAvgProfitMargin(0.3d);
        entity.setPurchaseFrequencyThreshold(2);
        entity.setIsDefault(false);
        entity.setIsPublished(false);
        entity.setModelVersion(1);
        entity.setAppliedCount(0);
        entity.setCreatedBy("scrm-system");
        return entity;
    }

    /**
     * 构造已持久化的客户实体 (用于 findById 返回)
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setNickname("张三");
        entity.setCreateTime(LocalDateTime.now().minusDays(100));
        return entity;
    }

    @Test
    @DisplayName("createModel: 写入账号 ID 与默认值后持久化")
    void createModel_success() throws ScrmException {
        ScrmLtvModelDto dto = new ScrmLtvModelDto();
        dto.setModelName("历史型 LTV 模型");
        dto.setModelCode("LTV_001");
        dto.setModelType("HISTORICAL");
        dto.setCalculationMethod("SIMPLE_AVG");
        when(modelRepository.countByModelCode(eq("LTV_001"))).thenReturn(0L);
        // 注: isDefault=false (缺省) 时 createModel 不调用 countByIsDefaultTrue, 无需 stub
        when(modelRepository.save(any(ScrmLtvModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmLtvModelDto result = service.createModel(dto);

        ArgumentCaptor<ScrmLtvModelEntity> captor =
                ArgumentCaptor.forClass(ScrmLtvModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        ScrmLtvModelEntity saved = captor.getValue();
        // lookbackDays 缺省时填 365
        assertThat(saved.getLookbackDays()).isEqualTo(365);
        // forecastDays 缺省时填 365
        assertThat(saved.getForecastDays()).isEqualTo(365);
        // discountRate 缺省时填 0.1
        assertThat(saved.getDiscountRate()).isEqualTo(0.1d);
        // churnRate 缺省时填 0.05
        assertThat(saved.getChurnRate()).isEqualTo(0.05d);
        // avgProfitMargin 缺省时填 0.3
        assertThat(saved.getAvgProfitMargin()).isEqualTo(0.3d);
        // purchaseFrequencyThreshold 缺省时填 2
        assertThat(saved.getPurchaseFrequencyThreshold()).isEqualTo(2);
        // isDefault 缺省时填 false
        assertThat(saved.getIsDefault()).isFalse();
        // isPublished 缺省时填 false
        assertThat(saved.getIsPublished()).isFalse();
        // modelVersion 缺省时填 1
        assertThat(saved.getModelVersion()).isEqualTo(1);
        // appliedCount 初值为 0
        assertThat(saved.getAppliedCount()).isZero();
        // createdBy 缺省时填 scrm-system
        assertThat(saved.getCreatedBy()).isEqualTo("scrm-system");
        assertThat(result.getModelCode()).isEqualTo("LTV_001");
    }

    @Test
    @DisplayName("createModel: 模型名称为空抛 BAD_REQUEST")
    void createModel_blankName() {
        ScrmLtvModelDto dto = new ScrmLtvModelDto();
        dto.setModelName("");
        dto.setModelCode("LTV_001");
        dto.setModelType("HISTORICAL");
        dto.setCalculationMethod("SIMPLE_AVG");

        assertThatThrownBy(() -> service.createModel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模型名称不能为空");
        verify(modelRepository, never()).save(any());
    }

    @Test
    @DisplayName("createModel: 模型编码为空抛 BAD_REQUEST")
    void createModel_blankCode() {
        ScrmLtvModelDto dto = new ScrmLtvModelDto();
        dto.setModelName("历史型 LTV 模型");
        dto.setModelCode("");
        dto.setModelType("HISTORICAL");
        dto.setCalculationMethod("SIMPLE_AVG");

        assertThatThrownBy(() -> service.createModel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模型编码不能为空");
        verify(modelRepository, never()).save(any());
    }

    @Test
    @DisplayName("createModel: 模型编码重复抛 CONFLICT")
    void createModel_duplicateCode() {
        ScrmLtvModelDto dto = new ScrmLtvModelDto();
        dto.setModelName("历史型 LTV 模型");
        dto.setModelCode("LTV_DUP");
        dto.setModelType("HISTORICAL");
        dto.setCalculationMethod("SIMPLE_AVG");
        when(modelRepository.countByModelCode(eq("LTV_DUP"))).thenReturn(1L);

        assertThatThrownBy(() -> service.createModel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模型编码已存在");
        verify(modelRepository, never()).save(any());
    }

    @Test
    @DisplayName("createModel: 已存在默认模型时 isDefault=true 抛 CONFLICT")
    void createModel_defaultConflict() {
        ScrmLtvModelDto dto = new ScrmLtvModelDto();
        dto.setModelName("默认模型");
        dto.setModelCode("LTV_DEFAULT");
        dto.setModelType("HISTORICAL");
        dto.setCalculationMethod("SIMPLE_AVG");
        dto.setIsDefault(true);
        when(modelRepository.countByModelCode(eq("LTV_DEFAULT"))).thenReturn(0L);
        when(modelRepository.countByIsDefaultTrue()).thenReturn(1L);

        assertThatThrownBy(() -> service.createModel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已存在默认 LTV 模型");
        verify(modelRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateModel: 模型不存在抛 NOT_FOUND")
    void updateModel_notFound() {
        ScrmLtvModelDto dto = new ScrmLtvModelDto();
        dto.setModelName("更新模型");
        when(modelRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateModel(10L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("LTV 模型不存在");
        verify(modelRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("getModelByCode: 模型不存在抛 NOT_FOUND")
    void getModelByCode_notFound() {
        when(modelRepository.findByModelCode(eq("LTV_MISSING")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getModelByCode("LTV_MISSING"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("LTV 模型不存在");
    }

    @Test
    @DisplayName("publishModel: 设置 isPublished=true 并持久化")
    void publishModel_success() throws ScrmException {
        ScrmLtvModelEntity entity = buildModelEntity(10L, "LTV_001", "SIMPLE_AVG");
        entity.setIsPublished(false);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(modelRepository.save(any(ScrmLtvModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.publishModel(10L);

        ArgumentCaptor<ScrmLtvModelEntity> captor =
                ArgumentCaptor.forClass(ScrmLtvModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getIsPublished()).isTrue();
    }

    @Test
    @DisplayName("unpublishModel: 设置 isPublished=false 并持久化")
    void unpublishModel_success() throws ScrmException {
        ScrmLtvModelEntity entity = buildModelEntity(10L, "LTV_001", "SIMPLE_AVG");
        entity.setIsPublished(true);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(modelRepository.save(any(ScrmLtvModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.unpublishModel(10L);

        ArgumentCaptor<ScrmLtvModelEntity> captor =
                ArgumentCaptor.forClass(ScrmLtvModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getIsPublished()).isFalse();
    }

    @Test
    @DisplayName("setDefault: 清理旧默认并设置新默认")
    void setDefault_success() throws ScrmException {
        ScrmLtvModelEntity newDefault = buildModelEntity(11L, "LTV_002", "SIMPLE_AVG");
        newDefault.setIsDefault(false);
        ScrmLtvModelEntity oldDefault = buildModelEntity(10L, "LTV_001", "SIMPLE_AVG");
        oldDefault.setIsDefault(true);
        when(modelRepository.findById(11L)).thenReturn(Optional.of(newDefault));
        when(modelRepository.findByIsDefaultTrue()).thenReturn(Optional.of(oldDefault));
        when(modelRepository.save(any(ScrmLtvModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.setDefault(11L);

        // 旧默认被取消
        ArgumentCaptor<ScrmLtvModelEntity> captor =
                ArgumentCaptor.forClass(ScrmLtvModelEntity.class);
        verify(modelRepository, times(2)).save(captor.capture());
        // 新默认被设置
        assertThat(newDefault.getIsDefault()).isTrue();
        assertThat(oldDefault.getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("copyModel: 复制模型追加 _copy 后缀, 版本号递增")
    void copyModel_success() throws ScrmException {
        ScrmLtvModelEntity source = buildModelEntity(10L, "LTV_001", "SIMPLE_AVG");
        source.setModelVersion(2);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(source));
        when(modelRepository.countByModelCode(eq("LTV_001_copy"))).thenReturn(0L);
        when(modelRepository.save(any(ScrmLtvModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmLtvModelDto result = service.copyModel(10L);

        ArgumentCaptor<ScrmLtvModelEntity> captor =
                ArgumentCaptor.forClass(ScrmLtvModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        ScrmLtvModelEntity saved = captor.getValue();
        assertThat(saved.getModelCode()).isEqualTo("LTV_001_copy");
        assertThat(saved.getModelName()).contains("副本");
        assertThat(saved.getModelVersion()).isEqualTo(3); // 源版本 2 + 1
        assertThat(saved.getIsDefault()).isFalse();
        assertThat(saved.getIsPublished()).isFalse();
        assertThat(saved.getAppliedCount()).isZero();
        assertThat(result.getModelCode()).isEqualTo("LTV_001_copy");
    }

    @Test
    @DisplayName("calculateLtv: 模型不存在抛 NOT_FOUND")
    void calculateLtv_modelNotFound() {
        when(modelRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculateLtv(100L, 10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("LTV 模型不存在");
        verify(orderRepository, never()).findByCustomerIdOrderByCreateTimeDesc(any());
    }

    @Test
    @DisplayName("calculateLtv: 客户不存在抛 NOT_FOUND")
    void calculateLtv_customerNotFound() {
        ScrmLtvModelEntity model = buildModelEntity(10L, "LTV_001", "SIMPLE_AVG");
        when(modelRepository.findById(10L)).thenReturn(Optional.of(model));
        when(customerRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculateLtv(100L, 10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户不存在");
        verify(orderRepository, never()).findByCustomerIdOrderByCreateTimeDesc(any());
    }

    @Test
    @DisplayName("calculateLtv: 无订单时返回默认 0 值并持久化")
    void calculateLtv_noOrders() throws ScrmException {
        ScrmLtvModelEntity model = buildModelEntity(10L, "LTV_001", "SIMPLE_AVG");
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(model));
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdOrderByCreateTimeDesc(eq(100L)))
                .thenReturn(List.of());
        when(ltvRepository.findFirstByCustomerIdAndModelIdOrderByCalculatedAtDesc(eq(100L), eq(10L))).thenReturn(Optional.empty());
        when(ltvRepository.save(any(ScrmCustomerLtvEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.calculateLtv(100L, 10L);

        ArgumentCaptor<ScrmCustomerLtvEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerLtvEntity.class);
        verify(ltvRepository, times(1)).save(captor.capture());
        ScrmCustomerLtvEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(100L);
        assertThat(saved.getModelId()).isEqualTo(10L);
        // 无订单时历史 LTV / 预测 LTV / 总收入均为 0
        assertThat(saved.getHistoricalLtv()).isEqualTo(0.0);
        assertThat(saved.getTotalRevenue()).isEqualTo(0.0);
        assertThat(saved.getTotalOrders()).isZero();
        assertThat(result.getCustomerId()).isEqualTo(100L);
    }

    
    @Test
    @DisplayName("getLtvByCustomer: 结果不存在抛 NOT_FOUND")
    void getLtvByCustomer_notFound() {
        when(ltvRepository.findFirstByCustomerIdAndModelIdOrderByCalculatedAtDesc(eq(100L), eq(10L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLtvByCustomer(100L, 10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 LTV 结果不存在");
    }

    @Test
    @DisplayName("predictChurn: LTV 结果不存在抛 NOT_FOUND")
    void predictChurn_notFound() {
        when(ltvRepository.findFirstByCustomerIdOrderByCalculatedAtDesc(eq(100L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.predictChurn(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 LTV 结果不存在");
    }

    @Test
    @DisplayName("determineTier: 默认阈值 VIP/HIGH/MEDIUM/LOW/AT_RISK 分层")
    void determineTier_defaultThresholds() {
        assertThat(service.determineTier(15000d, null)).isEqualTo("VIP");
        assertThat(service.determineTier(8000d, null)).isEqualTo("HIGH");
        assertThat(service.determineTier(3000d, null)).isEqualTo("MEDIUM");
        assertThat(service.determineTier(500d, null)).isEqualTo("LOW");
        assertThat(service.determineTier(50d, null)).isEqualTo("AT_RISK");
    }

    @Test
    @DisplayName("determineTier: 自定义阈值 JSON 优先匹配")
    void determineTier_customThresholds() {
        // 自定义: DIAMOND [20000,+∞), VIP [10000,20000)
        String thresholds = "[{\"tier\":\"DIAMOND\",\"minValue\":20000,\"maxValue\":999999},"
                + "{\"tier\":\"VIP\",\"minValue\":10000,\"maxValue\":20000}]";

        assertThat(service.determineTier(25000d, thresholds)).isEqualTo("DIAMOND");
        assertThat(service.determineTier(15000d, thresholds)).isEqualTo("VIP");
    }

    @Test
    @DisplayName("determineTier: 阈值 JSON 非法时回退默认阈值")
    void determineTier_invalidThresholdsFallback() {
        // 非法 JSON 回退默认阈值
        assertThat(service.determineTier(15000d, "invalid json")).isEqualTo("VIP");
    }

    @Test
    @DisplayName("calculateSimpleLtv: orders<=0 返回 0")
    void calculateSimpleLtv_noOrders() {
        assertThat(service.calculateSimpleLtv(1000d, 0, 100d, 365)).isEqualTo(0d);
    }

    @Test
    @DisplayName("calculateSimpleLtv: forecastDays<=0 返回 0")
    void calculateSimpleLtv_noForecastDays() {
        assertThat(service.calculateSimpleLtv(1000d, 5, 100d, 0)).isEqualTo(0d);
    }

    @Test
    @DisplayName("calculateSimpleLtv: 按平均订单价值与年化频次预测")
    void calculateSimpleLtv_success() {
        // orders=10, avgOrderValue=100, forecastDays=365
        // dailyRate = 10/365, projectedOrders = dailyRate*365 = 10, result = 100*10 = 1000
        double result = service.calculateSimpleLtv(1000d, 10, 100d, 365);
        assertThat(result).isCloseTo(1000d, within(0.01d));
    }

    @Test
    @DisplayName("calculateDiscountedCashFlow: revenue<=0 返回 0")
    void calculateDiscountedCashFlow_noRevenue() {
        assertThat(service.calculateDiscountedCashFlow(0d, 0.1d, 365)).isEqualTo(0d);
    }

    @Test
    @DisplayName("calculateDiscountedCashFlow: 按月折现汇总 (discountRate=0 时月份越多总额越大)")
    void calculateDiscountedCashFlow_success() {
        // annualRevenue=1200, discountRate=0, forecastDays=365
        // monthlyCashFlow=1200/12=100, months=ceil(365/(365/12))=ceil(12.0)=12
        // discountRate=0 → 折现因子恒为 1, dcf = 100 * 12 = 1200
        double result = service.calculateDiscountedCashFlow(1200d, 0d, 365);
        assertThat(result).isCloseTo(1200d, within(0.01d));
    }

    
    @Test
    @DisplayName("deleteModel: 存在时清理 LTV 结果并删除模型")
    void deleteModel_success() throws ScrmException {
        ScrmLtvModelEntity entity = buildModelEntity(10L, "LTV_001", "SIMPLE_AVG");
        when(modelRepository.findById(10L)).thenReturn(Optional.of(entity));

        service.deleteModel(10L);

        verify(ltvRepository, times(1)).deleteByModelId(eq(10L));
        verify(modelRepository, times(1)).delete(eq(entity));
    }

    @Test
    @DisplayName("getTopCustomers: 按预测 LTV 降序返回 Top N")
    void getTopCustomers_success() {
        ScrmCustomerLtvEntity ltv1 = new ScrmCustomerLtvEntity();
        ltv1.setId(1L);
        ltv1.setCustomerId(100L);
        ltv1.setPredictedLtv(8000d);
        when(ltvRepository.findAllByOrderByPredictedLtvDesc(any()))
                .thenReturn(List.of(ltv1));

        var result = service.getTopCustomers(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPredictedLtv()).isEqualTo(8000d);
    }

    @Test
    @DisplayName("getChurnRiskCustomers: 按流失概率降序返回 Top N")
    void getChurnRiskCustomers_success() {
        ScrmCustomerLtvEntity ltv1 = new ScrmCustomerLtvEntity();
        ltv1.setId(1L);
        ltv1.setCustomerId(100L);
        ltv1.setChurnProbability(0.85d);
        when(ltvRepository.findAllByOrderByChurnProbabilityDesc(any()))
                .thenReturn(List.of(ltv1));

        var result = service.getChurnRiskCustomers(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChurnProbability()).isEqualTo(0.85d);
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
