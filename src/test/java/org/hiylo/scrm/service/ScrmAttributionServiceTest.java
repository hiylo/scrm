/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmAttributionCalculateDto;
import org.hiylo.scrm.dto.ScrmAttributionConversionDto;
import org.hiylo.scrm.dto.ScrmAttributionModelDto;
import org.hiylo.scrm.dto.ScrmAttributionTouchpointDto;
import org.hiylo.scrm.entity.ScrmAttributionConversionEntity;
import org.hiylo.scrm.entity.ScrmAttributionModelEntity;
import org.hiylo.scrm.entity.ScrmAttributionTouchpointEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAttributionConversionRepository;
import org.hiylo.scrm.repository.ScrmAttributionModelRepository;
import org.hiylo.scrm.repository.ScrmAttributionTouchpointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
 * ScrmAttributionService 单元测试
 * <p>
 * 聚焦归因模型管理 (创建 / 更新 / 发布 / 设默认 / 编码唯一性校验)、触点与转化记录校验、
 * 归因算法 (首次 / 末次 / 线性 / 时间衰减 / 位置归因)、归因计算流程与数据隔离校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmAttributionService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmAttributionServiceTest {

    /** 归因模型数据仓库 Mock 桩 */
    @Mock
    private ScrmAttributionModelRepository modelRepository;
    /** 归因触点数据仓库 Mock 桩 */
    @Mock
    private ScrmAttributionTouchpointRepository touchpointRepository;
    /** 归因转化数据仓库 Mock 桩 */
    @Mock
    private ScrmAttributionConversionRepository conversionRepository;

    /** ObjectMapper 使用真实实例, 不 mock (遵循约束) */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测归因服务实例 */
    private ScrmAttributionService service;

    @BeforeEach
    void setUp() {
        ScrmAttributionModelService modelService =
                new ScrmAttributionModelService(modelRepository, objectMapper);
        ScrmAttributionTouchpointService touchpointService =
                new ScrmAttributionTouchpointService(touchpointRepository, objectMapper);
        ScrmAttributionConversionService conversionService =
                new ScrmAttributionConversionService(conversionRepository, objectMapper);
        ScrmAttributionCalculateService calculateService = new ScrmAttributionCalculateService(
                modelRepository, touchpointRepository, conversionRepository, objectMapper, modelService);
        ScrmAttributionReportService reportService = new ScrmAttributionReportService(
                modelRepository, touchpointRepository, conversionRepository, modelService, conversionService);
        service = new ScrmAttributionService(modelService, touchpointService, conversionService,
                calculateService, reportService,
                new ScrmAttributionStatsService(conversionRepository, touchpointRepository,
                        modelRepository, reportService));
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的归因模型实体 (用于 findById 返回)
     */
    private ScrmAttributionModelEntity buildModelEntity(Long id, String modelCode, String modelType) {
        ScrmAttributionModelEntity entity = new ScrmAttributionModelEntity();
        entity.setId(id);
        entity.setModelName("首触点归因模型");
        entity.setModelCode(modelCode);
        entity.setModelType(modelType);
        entity.setLookbackDays(30);
        entity.setTimeDecayHalfLife(7);
        entity.setConversionWindowDays(7);
        entity.setIsDefault(false);
        entity.setIsPublished(false);
        entity.setAppliedCount(0);
        return entity;
    }

    /**
     * 构造已持久化的归因触点实体 (用于 findCustomerTouchpointChain 返回)
     */
    private ScrmAttributionTouchpointEntity buildTouchpointEntity(Long id, Long customerId, LocalDateTime time) {
        ScrmAttributionTouchpointEntity entity = new ScrmAttributionTouchpointEntity();
        entity.setId(id);
        entity.setCustomerId(customerId);
        entity.setCustomerName("张三");
        entity.setTouchpointOrder(id.intValue());
        entity.setTouchpointType("AD_CLICK");
        entity.setChannel("SEARCH");
        entity.setTouchpointTime(time);
        entity.setTouchpointValue(0.0);
        entity.setIsAttributed(false);
        entity.setAttributionWeight(0.0);
        entity.setAttributionValue(0.0);
        return entity;
    }

    /**
     * 构造已持久化的归因转化实体 (用于 findById 返回)
     */
    private ScrmAttributionConversionEntity buildConversionEntity(Long id, Long customerId) {
        ScrmAttributionConversionEntity entity = new ScrmAttributionConversionEntity();
        entity.setId(id);
        entity.setCustomerId(customerId);
        entity.setCustomerName("张三");
        entity.setConversionType("PURCHASE");
        entity.setConversionTime(LocalDateTime.now());
        entity.setConversionValue(1000.0);
        entity.setConversionCount(1);
        entity.setTotalTouchpoints(0);
        entity.setAttributedTouchpoints(0);
        entity.setConversionWindowDays(7);
        return entity;
    }

    /**
     * 验证创建归因模型成功场景, 期望写入归属账号与默认值后持久化实体
     */
    @Test
    @DisplayName("createModel: 写入归属账号与默认值后持久化")
    void createModel_success() throws ScrmException {
        ScrmAttributionModelDto dto = new ScrmAttributionModelDto();
        dto.setModelName("首触点归因模型");
        dto.setModelCode("ATT_001");
        dto.setModelType("FIRST_TOUCH");
        when(modelRepository.findByModelCode(eq("ATT_001")))
                .thenReturn(Optional.empty());
        when(modelRepository.save(any(ScrmAttributionModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAttributionModelEntity result = service.createModel(dto);

        ArgumentCaptor<ScrmAttributionModelEntity> captor =
                ArgumentCaptor.forClass(ScrmAttributionModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        ScrmAttributionModelEntity saved = captor.getValue();
        // modelType 缺省时填 FIRST_TOUCH
        assertThat(saved.getModelType()).isEqualTo("FIRST_TOUCH");
        // lookbackDays 缺省时填 30
        assertThat(saved.getLookbackDays()).isEqualTo(30);
        // timeDecayHalfLife 缺省时填 7
        assertThat(saved.getTimeDecayHalfLife()).isEqualTo(7);
        // conversionWindowDays 缺省时填 7
        assertThat(saved.getConversionWindowDays()).isEqualTo(7);
        // isDefault 缺省时填 false
        assertThat(saved.getIsDefault()).isFalse();
        // isPublished 缺省时填 false
        assertThat(saved.getIsPublished()).isFalse();
        // appliedCount 初值为 0
        assertThat(saved.getAppliedCount()).isZero();
        assertThat(result.getModelCode()).isEqualTo("ATT_001");
    }

    /**
     * 验证模型类型非法场景, 期望抛出 BAD_REQUEST 异常且不执行保存
     */
    @Test
    @DisplayName("createModel: 模型类型非法抛 BAD_REQUEST")
    void createModel_invalidModelType() {
        ScrmAttributionModelDto dto = new ScrmAttributionModelDto();
        dto.setModelName("非法模型");
        dto.setModelCode("ATT_BAD");
        dto.setModelType("INVALID_TYPE");

        assertThatThrownBy(() -> service.createModel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模型类型非法");
        verify(modelRepository, never()).save(any());
    }

    /**
     * 验证模型编码重复场景, 期望抛出 CONFLICT 异常且不执行保存
     */
    @Test
    @DisplayName("createModel: 模型编码重复抛 CONFLICT")
    void createModel_duplicateCode() {
        ScrmAttributionModelDto dto = new ScrmAttributionModelDto();
        dto.setModelName("首触点归因模型");
        dto.setModelCode("ATT_DUP");
        dto.setModelType("FIRST_TOUCH");
        when(modelRepository.findByModelCode(eq("ATT_DUP")))
                .thenReturn(Optional.of(buildModelEntity(10L, "ATT_DUP", "FIRST_TOUCH")));

        assertThatThrownBy(() -> service.createModel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归因模型编码已存在");
        verify(modelRepository, never()).save(any());
    }

    /**
     * 验证创建默认模型场景, 期望清理该账号原有的默认模型标记
     */
    @Test
    @DisplayName("createModel: isDefault=true 时清理旧默认标记")
    void createModel_clearOldDefault() throws ScrmException {
        ScrmAttributionModelDto dto = new ScrmAttributionModelDto();
        dto.setModelName("默认模型");
        dto.setModelCode("ATT_DEFAULT");
        dto.setModelType("LINEAR");
        dto.setIsDefault(true);
        when(modelRepository.findByModelCode(eq("ATT_DEFAULT")))
                .thenReturn(Optional.empty());
        when(modelRepository.save(any(ScrmAttributionModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createModel(dto);

        // isDefault=true 时应清理旧默认
        verify(modelRepository, times(1)).clearDefaultFlag();
    }

    /**
     * 验证更新不存在的归因模型场景, 期望抛出 NOT_FOUND 异常且不执行保存
     */
    @Test
    @DisplayName("updateModel: 模型不存在抛 NOT_FOUND")
    void updateModel_notFound() {
        ScrmAttributionModelDto dto = new ScrmAttributionModelDto();
        dto.setModelName("更新模型");
        dto.setModelCode("ATT_001");
        dto.setModelType("FIRST_TOUCH");
        when(modelRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateModel(10L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归因模型不存在");
        verify(modelRepository, never()).save(any());
    }

    /**
     * 验证越权访问归因模型场景, 期望按不存在处理抛出 NOT_FOUND 异常
     */
    
    /**
     * 验证模型编码为空场景, 期望抛出 BAD_REQUEST 异常且不执行查询
     */
    @Test
    @DisplayName("getModelByCode: 编码为空抛 BAD_REQUEST")
    void getModelByCode_blankCode() {
        assertThatThrownBy(() -> service.getModelByCode(""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模型编码不能为空");
        verify(modelRepository, never()).findByModelCode(any());
    }

    /**
     * 验证发布归因模型场景, 期望设置 isPublished 为 true 并持久化
     */
    @Test
    @DisplayName("publishModel: 设置 isPublished=true 并持久化")
    void publishModel_success() throws ScrmException {
        ScrmAttributionModelEntity entity = buildModelEntity(10L, "ATT_001", "FIRST_TOUCH");
        entity.setIsPublished(false);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(modelRepository.save(any(ScrmAttributionModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.publishModel(10L);

        ArgumentCaptor<ScrmAttributionModelEntity> captor =
                ArgumentCaptor.forClass(ScrmAttributionModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getIsPublished()).isTrue();
    }

    /**
     * 验证取消发布归因模型场景, 期望设置 isPublished 为 false 并持久化
     */
    @Test
    @DisplayName("unpublishModel: 设置 isPublished=false 并持久化")
    void unpublishModel_success() throws ScrmException {
        ScrmAttributionModelEntity entity = buildModelEntity(10L, "ATT_001", "FIRST_TOUCH");
        entity.setIsPublished(true);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(modelRepository.save(any(ScrmAttributionModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.unpublishModel(10L);

        ArgumentCaptor<ScrmAttributionModelEntity> captor =
                ArgumentCaptor.forClass(ScrmAttributionModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getIsPublished()).isFalse();
    }

    /**
     * 验证设置默认归因模型场景, 期望清理旧默认标记并设置新默认
     */
    @Test
    @DisplayName("setDefault: 清理旧默认并设置新默认")
    void setDefault_success() throws ScrmException {
        ScrmAttributionModelEntity entity = buildModelEntity(10L, "ATT_001", "FIRST_TOUCH");
        entity.setIsDefault(false);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(modelRepository.save(any(ScrmAttributionModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.setDefault(10L);

        verify(modelRepository, times(1)).clearDefaultFlag();
        ArgumentCaptor<ScrmAttributionModelEntity> captor =
                ArgumentCaptor.forClass(ScrmAttributionModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getIsDefault()).isTrue();
    }

    /**
     * 验证记录归因触点成功场景, 期望写入归属账号与默认值后持久化实体
     */
    @Test
    @DisplayName("recordTouchpoint: 写入归属账号与默认值后持久化")
    void recordTouchpoint_success() throws ScrmException {
        ScrmAttributionTouchpointDto dto = new ScrmAttributionTouchpointDto();
        dto.setCustomerId(100L);
        dto.setCustomerName("张三");
        dto.setTouchpointType("AD_CLICK");
        dto.setChannel("SEARCH");
        dto.setTouchpointTime(LocalDateTime.now());
        // touchpointOrder 缺省时由 nextTouchpointOrder 回填
        when(touchpointRepository.findCustomerTouchpointChain(eq(100L), any(), any()))
                .thenReturn(List.of());
        when(touchpointRepository.save(any(ScrmAttributionTouchpointEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAttributionTouchpointEntity result = service.recordTouchpoint(dto);

        ArgumentCaptor<ScrmAttributionTouchpointEntity> captor =
                ArgumentCaptor.forClass(ScrmAttributionTouchpointEntity.class);
        verify(touchpointRepository, times(1)).save(captor.capture());
        ScrmAttributionTouchpointEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(100L);
        // touchpointValue 缺省时填 0.0
        assertThat(saved.getTouchpointValue()).isEqualTo(0.0);
        // touchpointOrder 缺省时回填 (空触点链 → order=1)
        assertThat(saved.getTouchpointOrder()).isEqualTo(1);
        assertThat(result.getTouchpointType()).isEqualTo("AD_CLICK");
    }

    /**
     * 验证触点类型非法场景, 期望抛出 BAD_REQUEST 异常且不执行保存
     */
    @Test
    @DisplayName("recordTouchpoint: 触点类型非法抛 BAD_REQUEST")
    void recordTouchpoint_invalidType() {
        ScrmAttributionTouchpointDto dto = new ScrmAttributionTouchpointDto();
        dto.setCustomerId(100L);
        dto.setTouchpointType("INVALID_TYPE");
        dto.setChannel("SEARCH");

        assertThatThrownBy(() -> service.recordTouchpoint(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触点类型非法");
        verify(touchpointRepository, never()).save(any());
    }

    /**
     * 验证触点渠道非法场景, 期望抛出 BAD_REQUEST 异常且不执行保存
     */
    @Test
    @DisplayName("recordTouchpoint: 渠道非法抛 BAD_REQUEST")
    void recordTouchpoint_invalidChannel() {
        ScrmAttributionTouchpointDto dto = new ScrmAttributionTouchpointDto();
        dto.setCustomerId(100L);
        dto.setTouchpointType("AD_CLICK");
        dto.setChannel("INVALID_CHANNEL");

        assertThatThrownBy(() -> service.recordTouchpoint(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("渠道非法");
        verify(touchpointRepository, never()).save(any());
    }

    /**
     * 验证记录归因转化成功场景, 期望写入归属账号与默认值后持久化实体
     */
    @Test
    @DisplayName("recordConversion: 写入归属账号与默认值后持久化")
    void recordConversion_success() throws ScrmException {
        ScrmAttributionConversionDto dto = new ScrmAttributionConversionDto();
        dto.setCustomerId(100L);
        dto.setCustomerName("张三");
        dto.setConversionType("PURCHASE");
        dto.setConversionTime(LocalDateTime.now());
        dto.setConversionValue(500.0);
        when(conversionRepository.save(any(ScrmAttributionConversionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAttributionConversionEntity result = service.recordConversion(dto);

        ArgumentCaptor<ScrmAttributionConversionEntity> captor =
                ArgumentCaptor.forClass(ScrmAttributionConversionEntity.class);
        verify(conversionRepository, times(1)).save(captor.capture());
        ScrmAttributionConversionEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(100L);
        // conversionCount 缺省时填 1
        assertThat(saved.getConversionCount()).isEqualTo(1);
        // 归因字段初值为 0
        assertThat(saved.getTotalTouchpoints()).isZero();
        assertThat(saved.getAttributedTouchpoints()).isZero();
        // conversionWindowDays 缺省时填 7
        assertThat(saved.getConversionWindowDays()).isEqualTo(7);
        assertThat(result.getConversionValue()).isEqualTo(500.0);
    }

    /**
     * 验证转化类型非法场景, 期望抛出 BAD_REQUEST 异常且不执行保存
     */
    @Test
    @DisplayName("recordConversion: 转化类型非法抛 BAD_REQUEST")
    void recordConversion_invalidType() {
        ScrmAttributionConversionDto dto = new ScrmAttributionConversionDto();
        dto.setCustomerId(100L);
        dto.setConversionType("INVALID_TYPE");
        dto.setConversionTime(LocalDateTime.now());

        assertThatThrownBy(() -> service.recordConversion(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("转化类型非法");
        verify(conversionRepository, never()).save(any());
    }

    /**
     * 验证归因计算参数为空场景, 期望抛出 BAD_REQUEST 异常且不查询模型
     */
    @Test
    @DisplayName("calculateAttribution: 参数为空抛 BAD_REQUEST")
    void calculateAttribution_nullParams() {
        assertThatThrownBy(() -> service.calculateAttribution(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归因计算参数与模型 ID 不能为空");
        verify(modelRepository, never()).findById(any());
    }

    /**
     * 验证归因模型不存在场景, 期望抛出 NOT_FOUND 异常
     */
    @Test
    @DisplayName("calculateAttribution: 模型不存在抛 NOT_FOUND")
    void calculateAttribution_modelNotFound() {
        ScrmAttributionCalculateDto dto = new ScrmAttributionCalculateDto();
        dto.setModelId(10L);
        when(modelRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculateAttribution(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归因模型不存在");
    }

    /**
     * 验证无待归因转化场景, 期望返回全零结果并增量更新模型应用统计
     */
    @Test
    @DisplayName("calculateAttribution: 无待归因转化时返回全零结果")
    void calculateAttribution_noConversions() throws ScrmException {
        ScrmAttributionModelEntity model = buildModelEntity(10L, "ATT_001", "FIRST_TOUCH");
        when(modelRepository.findById(10L)).thenReturn(Optional.of(model));
        ScrmAttributionCalculateDto dto = new ScrmAttributionCalculateDto();
        dto.setModelId(10L);
        dto.setConversionIds(List.of());
        // 空 conversionIds 走时间范围分支, 返回空列表
        when(conversionRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(modelRepository.incrementAppliedCount(eq(10L), any())).thenReturn(1);

        Map<String, Integer> result = service.calculateAttribution(dto);

        assertThat(result.get("total")).isZero();
        assertThat(result.get("processed")).isZero();
        assertThat(result.get("failed")).isZero();
        // 增量更新模型应用统计 (best-effort)
        verify(modelRepository, times(1)).incrementAppliedCount(eq(10L), any());
    }

    /**
     * 验证按 FIRST_TOUCH 模型归因成功场景, 期望首触点权重为 1.0 且回填转化归因结果
     */
    @Test
    @DisplayName("calculateAttribution: 指定 conversionIds 时按 FIRST_TOUCH 归因成功")
    void calculateAttribution_firstTouchSuccess() throws ScrmException {
        ScrmAttributionModelEntity model = buildModelEntity(10L, "ATT_001", "FIRST_TOUCH");
        ScrmAttributionConversionEntity conversion = buildConversionEntity(20L, 100L);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(model));
        ScrmAttributionCalculateDto dto = new ScrmAttributionCalculateDto();
        dto.setModelId(10L);
        dto.setConversionIds(List.of(20L));
        when(conversionRepository.findById(20L)).thenReturn(Optional.of(conversion));
        // 回溯窗口内两条触点
        ScrmAttributionTouchpointEntity tp1 = buildTouchpointEntity(1L, 100L, LocalDateTime.now().minusDays(5));
        ScrmAttributionTouchpointEntity tp2 = buildTouchpointEntity(2L, 100L, LocalDateTime.now().minusDays(1));
        when(touchpointRepository.findCustomerTouchpointChain(eq(100L), any(), any()))
                .thenReturn(List.of(tp1, tp2));
        when(touchpointRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversionRepository.save(any(ScrmAttributionConversionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(modelRepository.incrementAppliedCount(eq(10L), any())).thenReturn(1);

        Map<String, Integer> result = service.calculateAttribution(dto);

        assertThat(result.get("total")).isEqualTo(1);
        assertThat(result.get("processed")).isEqualTo(1);
        assertThat(result.get("failed")).isZero();
        // 验证触点归因标记: 首触点权重 1.0, 末触点权重 0
        ArgumentCaptor<List<ScrmAttributionTouchpointEntity>> tpCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(touchpointRepository, times(1)).saveAll(tpCaptor.capture());
        @SuppressWarnings("unchecked")
        List<ScrmAttributionTouchpointEntity> savedTps = (List<ScrmAttributionTouchpointEntity>) tpCaptor.getValue();
        assertThat(savedTps.get(0).getIsAttributed()).isTrue();
        assertThat(savedTps.get(0).getAttributionWeight()).isEqualTo(1.0);
        assertThat(savedTps.get(1).getIsAttributed()).isFalse();
        // 验证转化回填
        ArgumentCaptor<ScrmAttributionConversionEntity> convCaptor =
                ArgumentCaptor.forClass(ScrmAttributionConversionEntity.class);
        verify(conversionRepository, times(1)).save(convCaptor.capture());
        ScrmAttributionConversionEntity savedConv = convCaptor.getValue();
        assertThat(savedConv.getModelId()).isEqualTo(10L);
        assertThat(savedConv.getTotalTouchpoints()).isEqualTo(2);
        assertThat(savedConv.getAttributedTouchpoints()).isEqualTo(1);
    }

    /**
     * 验证越权转化场景, 期望该转化被过滤而不进入归因计算
     */
    
    /**
     * 验证首触点归因算法场景, 期望首触点权重为 1.0 其余触点为 0
     */
    @Test
    @DisplayName("applyFirstTouch: 首触点权重 1.0, 其余 0")
    void applyFirstTouch_success() {
        ScrmAttributionTouchpointEntity tp1 = buildTouchpointEntity(1L, 100L, LocalDateTime.now().minusDays(5));
        ScrmAttributionTouchpointEntity tp2 = buildTouchpointEntity(2L, 100L, LocalDateTime.now().minusDays(3));
        ScrmAttributionTouchpointEntity tp3 = buildTouchpointEntity(3L, 100L, LocalDateTime.now().minusDays(1));

        Map<Long, Double> weights = service.applyFirstTouch(List.of(tp1, tp2, tp3), 1000.0);

        assertThat(weights).hasSize(3);
        assertThat(weights.get(1L)).isEqualTo(1.0);
        assertThat(weights.get(2L)).isEqualTo(0.0);
        assertThat(weights.get(3L)).isEqualTo(0.0);
    }

    /**
     * 验证末触点归因算法场景, 期望末触点权重为 1.0 其余触点为 0
     */
    @Test
    @DisplayName("applyLastTouch: 末触点权重 1.0, 其余 0")
    void applyLastTouch_success() {
        ScrmAttributionTouchpointEntity tp1 = buildTouchpointEntity(1L, 100L, LocalDateTime.now().minusDays(5));
        ScrmAttributionTouchpointEntity tp2 = buildTouchpointEntity(2L, 100L, LocalDateTime.now().minusDays(3));
        ScrmAttributionTouchpointEntity tp3 = buildTouchpointEntity(3L, 100L, LocalDateTime.now().minusDays(1));

        Map<Long, Double> weights = service.applyLastTouch(List.of(tp1, tp2, tp3), 1000.0);

        assertThat(weights).hasSize(3);
        assertThat(weights.get(1L)).isEqualTo(0.0);
        assertThat(weights.get(2L)).isEqualTo(0.0);
        assertThat(weights.get(3L)).isEqualTo(1.0);
    }

    /**
     * 验证线性归因算法场景, 期望所有触点均分权重 1/n
     */
    @Test
    @DisplayName("applyLinear: 所有触点均分权重 1/n")
    void applyLinear_success() {
        ScrmAttributionTouchpointEntity tp1 = buildTouchpointEntity(1L, 100L, LocalDateTime.now().minusDays(5));
        ScrmAttributionTouchpointEntity tp2 = buildTouchpointEntity(2L, 100L, LocalDateTime.now().minusDays(3));
        ScrmAttributionTouchpointEntity tp3 = buildTouchpointEntity(3L, 100L, LocalDateTime.now().minusDays(1));

        Map<Long, Double> weights = service.applyLinear(List.of(tp1, tp2, tp3), 1000.0);

        assertThat(weights).hasSize(3);
        // 三触点均分 1/3
        assertThat(weights.get(1L)).isCloseTo(1.0 / 3, within(0.0001));
        assertThat(weights.get(2L)).isCloseTo(1.0 / 3, within(0.0001));
        assertThat(weights.get(3L)).isCloseTo(1.0 / 3, within(0.0001));
    }

    /**
     * 验证时间衰减归因算法场景, 期望越接近转化的触点权重越大且权重和归一化为 1
     */
    @Test
    @DisplayName("applyTimeDecay: 越接近转化的触点权重越大, 权重和为 1")
    void applyTimeDecay_success() {
        LocalDateTime now = LocalDateTime.now();
        ScrmAttributionTouchpointEntity tp1 = buildTouchpointEntity(1L, 100L, now.minusDays(14));
        ScrmAttributionTouchpointEntity tp2 = buildTouchpointEntity(2L, 100L, now.minusDays(7));
        ScrmAttributionTouchpointEntity tp3 = buildTouchpointEntity(3L, 100L, now.minusDays(1));

        Map<Long, Double> weights = service.applyTimeDecay(List.of(tp1, tp2, tp3), 1000.0, 7);

        assertThat(weights).hasSize(3);
        // 越接近转化时间权重越大
        assertThat(weights.get(3L)).isGreaterThan(weights.get(2L));
        assertThat(weights.get(2L)).isGreaterThan(weights.get(1L));
        // 权重和归一化为 1
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        assertThat(sum).isCloseTo(1.0, within(0.0001));
    }

    /**
     * 验证 U 型位置归因算法场景, 期望首末触点权重高、中间触点权重低
     */
    @Test
    @DisplayName("applyPositionBased: U 型归因首末触点权重高, 中间触点均分")
    void applyPositionBased_uShaped() {
        ScrmAttributionTouchpointEntity tp1 = buildTouchpointEntity(1L, 100L, LocalDateTime.now().minusDays(10));
        ScrmAttributionTouchpointEntity tp2 = buildTouchpointEntity(2L, 100L, LocalDateTime.now().minusDays(5));
        ScrmAttributionTouchpointEntity tp3 = buildTouchpointEntity(3L, 100L, LocalDateTime.now().minusDays(1));

        // U 型权重: first=0.4, last=0.4, middle=0.2
        Map<Long, Double> weights = service.applyPositionBased(List.of(tp1, tp2, tp3), 1000.0,
                Map.of("first", 0.4, "last", 0.4, "middle", 0.2));

        assertThat(weights).hasSize(3);
        // 首末触点权重 0.4, 中间触点 0.2
        assertThat(weights.get(1L)).isCloseTo(0.4, within(0.0001));
        assertThat(weights.get(3L)).isCloseTo(0.4, within(0.0001));
        assertThat(weights.get(2L)).isCloseTo(0.2, within(0.0001));
    }

    /**
     * 验证单触点位置归因场景, 期望唯一触点权重为 1.0
     */
    @Test
    @DisplayName("applyPositionBased: 单触点权重 1.0")
    void applyPositionBased_singleTouchpoint() {
        ScrmAttributionTouchpointEntity tp1 = buildTouchpointEntity(1L, 100L, LocalDateTime.now().minusDays(1));

        Map<Long, Double> weights = service.applyPositionBased(List.of(tp1), 1000.0,
                Map.of("first", 0.4, "last", 0.4, "middle", 0.2));

        assertThat(weights).hasSize(1);
        assertThat(weights.get(1L)).isEqualTo(1.0);
    }

    /**
     * 验证首触点归因空触点列表场景, 期望返回空权重映射
     */
    @Test
    @DisplayName("applyFirstTouch: 空触点列表返回空 Map")
    void applyFirstTouch_emptyList() {
        Map<Long, Double> weights = service.applyFirstTouch(List.of(), 1000.0);
        assertThat(weights).isEmpty();
    }

    /**
     * 验证客户 ID 为空时查询窗口内触点场景, 期望返回空列表且不执行查询
     */
    @Test
    @DisplayName("getTouchpointsInWindow: customerId 为空返回空列表")
    void getTouchpointsInWindow_nullCustomerId() {
        List<ScrmAttributionTouchpointEntity> result =
                service.getTouchpointsInWindow(null, LocalDateTime.now(), 30);
        assertThat(result).isEmpty();
        verify(touchpointRepository, never()).findCustomerTouchpointChain(any(), any(), any());
    }

    /**
     * 验证回溯天数为非正值场景, 期望返回空列表且不执行查询
     */
    @Test
    @DisplayName("getTouchpointsInWindow: lookbackDays 非正返回空列表")
    void getTouchpointsInWindow_invalidLookback() {
        List<ScrmAttributionTouchpointEntity> result =
                service.getTouchpointsInWindow(100L, LocalDateTime.now(), 0);
        assertThat(result).isEmpty();
        verify(touchpointRepository, never()).findCustomerTouchpointChain(any(), any(), any());
    }

    /**
     * 验证客户 ID 为空时查询全部触点场景, 期望返回空列表且不执行查询
     */
    @Test
    @DisplayName("getCustomerTouchpoints: customerId 为空返回空列表")
    void getCustomerTouchpoints_nullCustomerId() {
        List<ScrmAttributionTouchpointEntity> result = service.getCustomerTouchpoints(null);
        assertThat(result).isEmpty();
        verify(touchpointRepository, never()).findCustomerTouchpointChain(any(), any(), any());
    }

    /**
     * 验证订单号为空时查询转化场景, 期望抛出 BAD_REQUEST 异常且不执行查询
     */
    @Test
    @DisplayName("getConversionByOrder: 订单号为空抛 BAD_REQUEST")
    void getConversionByOrder_blankOrderId() {
        assertThatThrownBy(() -> service.getConversionByOrder(""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("订单号不能为空");
        verify(conversionRepository, never())
                .findFirstByOrderIdOrderByConversionTimeDesc(any());
    }

    /**
     * 验证越权访问归因转化场景, 期望按不存在处理抛出 NOT_FOUND 异常
     */
    
    /**
     * 验证越权访问归因触点场景, 期望按不存在处理抛出 NOT_FOUND 异常
     */
    
    /**
     * 验证越权删除归因模型场景, 期望抛出 NOT_FOUND 异常且不执行删除
     */
    
    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
