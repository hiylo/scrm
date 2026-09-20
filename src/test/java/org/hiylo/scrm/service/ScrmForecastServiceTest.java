/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmForecastServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmForecastModelDto;
import org.hiylo.scrm.dto.ScrmForecastScenarioDto;
import org.hiylo.scrm.entity.ScrmForecastModelEntity;
import org.hiylo.scrm.entity.ScrmForecastScenarioEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmForecastModelRepository;
import org.hiylo.scrm.repository.ScrmForecastResultRepository;
import org.hiylo.scrm.repository.ScrmForecastScenarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
 * ScrmForecastService 单元测试
 * <p>
 * 聚焦销售预测模型管理 (创建 / 更新 / 启停 / 编码唯一性校验)、场景管理 (创建 / 越权隔离)、
 * 模型对比 (最佳模型选取) 与数据隔离校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmForecastService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmForecastServiceTest {

    /** 预测模型仓库 Mock */
    @Mock
    private ScrmForecastModelRepository modelRepository;
    /** 预测场景仓库 Mock */
    @Mock
    private ScrmForecastScenarioRepository scenarioRepository;
    /** 预测结果仓库 Mock */
    @Mock
    private ScrmForecastResultRepository resultRepository;

    /** ObjectMapper 使用真实实例, 不 mock (遵循约束) */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测服务实例 */
    private ScrmForecastService service;

    @BeforeEach
    void setUp() {
        ScrmForecastModelService modelService = new ScrmForecastModelService(
                modelRepository, scenarioRepository, resultRepository, objectMapper);
        ScrmForecastScenarioService scenarioService = new ScrmForecastScenarioService(
                scenarioRepository, resultRepository, objectMapper, modelService);
        ScrmForecastQueryService queryService = new ScrmForecastQueryService(
                resultRepository, scenarioService, modelService);
        ScrmForecastStatsService statsService = new ScrmForecastStatsService(
                modelRepository, scenarioRepository, resultRepository, scenarioService);
        service = new ScrmForecastService(modelService, scenarioService, queryService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的预测模型实体 (用于 findById 返回)
     */
    private ScrmForecastModelEntity buildModelEntity(Long id, String modelCode, String modelType) {
        ScrmForecastModelEntity entity = new ScrmForecastModelEntity();
        entity.setId(id);
        entity.setModelName("销售预测模型");
        entity.setModelCode(modelCode);
        entity.setModelType(modelType);
        entity.setTargetMetric("REVENUE");
        entity.setGranularity("MONTHLY");
        entity.setLookbackPeriods(12);
        entity.setForecastPeriods(3);
        entity.setIsAutoRetrain(Boolean.FALSE);
        entity.setRetrainFrequency("MONTHLY");
        entity.setEnabled(Boolean.TRUE);
        entity.setIsTrained(Boolean.FALSE);
        entity.setModelVersion(1);
        return entity;
    }

    /**
     * 构造已持久化的预测场景实体 (用于 findById 返回)
     */
    private ScrmForecastScenarioEntity buildScenarioEntity(Long id, String scenarioCode, Long modelId) {
        ScrmForecastScenarioEntity entity = new ScrmForecastScenarioEntity();
        entity.setId(id);
        entity.setScenarioName("基线场景");
        entity.setScenarioCode(scenarioCode);
        entity.setModelId(modelId);
        entity.setModelName("销售预测模型");
        entity.setScenarioType("BASELINE");
        entity.setTargetPeriod("2026-Q4");
        entity.setTargetStartDate(LocalDate.of(2026, 10, 1));
        entity.setTargetEndDate(LocalDate.of(2026, 12, 31));
        entity.setGranularity("MONTHLY");
        entity.setStatus("DRAFT");
        entity.setConfidenceLevel(0.95d);
        return entity;
    }

    @Test
    @DisplayName("createModel: 写入账号 ID 与默认值后持久化")
    void createModel_success() throws ScrmException {
        ScrmForecastModelDto dto = new ScrmForecastModelDto();
        dto.setModelName("销售预测模型");
        dto.setModelCode("FCST_001");
        dto.setModelType("EXPONENTIAL_SMOOTHING");
        dto.setTargetMetric("REVENUE");
        when(modelRepository.countByModelCode(eq("FCST_001"))).thenReturn(0L);
        when(modelRepository.save(any(ScrmForecastModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmForecastModelEntity result = service.createModel(dto);

        ArgumentCaptor<ScrmForecastModelEntity> captor =
                ArgumentCaptor.forClass(ScrmForecastModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        ScrmForecastModelEntity saved = captor.getValue();
        // granularity 缺省时填 MONTHLY
        assertThat(saved.getGranularity()).isEqualTo("MONTHLY");
        // retrainFrequency 缺省时填 MONTHLY
        assertThat(saved.getRetrainFrequency()).isEqualTo("MONTHLY");
        // enabled 缺省时填 true
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getModelType()).isEqualTo("EXPONENTIAL_SMOOTHING");
        assertThat(saved.getTargetMetric()).isEqualTo("REVENUE");
        assertThat(result.getModelCode()).isEqualTo("FCST_001");
    }

    @Test
    @DisplayName("createModel: 模型类型非法抛 BAD_REQUEST")
    void createModel_invalidModelType() {
        ScrmForecastModelDto dto = new ScrmForecastModelDto();
        dto.setModelName("非法模型");
        dto.setModelCode("FCST_BAD");
        dto.setModelType("INVALID_TYPE");
        dto.setTargetMetric("REVENUE");

        assertThatThrownBy(() -> service.createModel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模型类型非法");
        verify(modelRepository, never()).save(any());
    }

    @Test
    @DisplayName("createModel: 模型编码重复抛 CONFLICT")
    void createModel_duplicateCode() {
        ScrmForecastModelDto dto = new ScrmForecastModelDto();
        dto.setModelName("销售预测模型");
        dto.setModelCode("FCST_DUP");
        dto.setModelType("MOVING_AVERAGE");
        dto.setTargetMetric("REVENUE");
        when(modelRepository.countByModelCode(eq("FCST_DUP"))).thenReturn(1L);

        assertThatThrownBy(() -> service.createModel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模型编码已存在");
        verify(modelRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateModel: 模型不存在抛 NOT_FOUND")
    void updateModel_notFound() {
        ScrmForecastModelDto dto = new ScrmForecastModelDto();
        dto.setModelName("更新模型");
        dto.setModelCode("FCST_001");
        dto.setModelType("EXPONENTIAL_SMOOTHING");
        dto.setTargetMetric("REVENUE");
        when(modelRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateModel(10L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("预测模型不存在");
        verify(modelRepository, never()).save(any());
    }

    @Test
    @DisplayName("enableModel: 设置 enabled=true 并持久化")
    void enableModel_success() throws ScrmException {
        ScrmForecastModelEntity entity = buildModelEntity(10L, "FCST_001", "MOVING_AVERAGE");
        entity.setEnabled(Boolean.FALSE);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(modelRepository.save(any(ScrmForecastModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.enableModel(10L);

        ArgumentCaptor<ScrmForecastModelEntity> captor =
                ArgumentCaptor.forClass(ScrmForecastModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableModel: 设置 enabled=false 并持久化")
    void disableModel_success() throws ScrmException {
        ScrmForecastModelEntity entity = buildModelEntity(10L, "FCST_001", "MOVING_AVERAGE");
        entity.setEnabled(Boolean.TRUE);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(modelRepository.save(any(ScrmForecastModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.disableModel(10L);

        ArgumentCaptor<ScrmForecastModelEntity> captor =
                ArgumentCaptor.forClass(ScrmForecastModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }

    @Test
    @DisplayName("deleteModel: 存在关联场景时抛 BAD_REQUEST")
    void deleteModel_hasRelatedScenarios() {
        ScrmForecastModelEntity entity = buildModelEntity(10L, "FCST_001", "MOVING_AVERAGE");
        when(modelRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmForecastScenarioEntity scenario = buildScenarioEntity(20L, "SCN_001", 10L);
        when(scenarioRepository.findByModelId(eq(10L)))
                .thenReturn(List.of(scenario));

        assertThatThrownBy(() -> service.deleteModel(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模型存在关联场景");
        verify(modelRepository, never()).delete(any(ScrmForecastModelEntity.class));
    }

    
    
    @Test
    @DisplayName("getModelByCode: 编码为空抛 BAD_REQUEST")
    void getModelByCode_blankCode() {
        assertThatThrownBy(() -> service.getModelByCode(""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模型编码不能为空");
        verify(modelRepository, never()).findByModelCode(any());
    }

    @Test
    @DisplayName("compareModels: 空列表抛 BAD_REQUEST")
    void compareModels_emptyList() {
        assertThatThrownBy(() -> service.compareModels(List.of()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("对比模型 ID 列表不能为空");
    }

    @Test
    @DisplayName("compareModels: 按 accuracyScore 选取最佳模型")
    void compareModels_selectBest() throws ScrmException {
        ScrmForecastModelEntity m1 = buildModelEntity(10L, "FCST_001", "MOVING_AVERAGE");
        m1.setIsTrained(Boolean.TRUE);
        m1.setLastAccuracyScore(0.85d);
        ScrmForecastModelEntity m2 = buildModelEntity(11L, "FCST_002", "EXPONENTIAL_SMOOTHING");
        m2.setIsTrained(Boolean.TRUE);
        m2.setLastAccuracyScore(0.92d);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(m1));
        when(modelRepository.findById(11L)).thenReturn(Optional.of(m2));

        Map<String, Object> result = service.compareModels(List.of(10L, 11L));

        assertThat(result.get("bestModelId")).isEqualTo(11L);
        assertThat(result.get("bestAccuracyScore")).isEqualTo(0.92d);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> models = (List<Map<String, Object>>) result.get("models");
        assertThat(models).hasSize(2);
    }

    @Test
    @DisplayName("createScenario: 目标结束日期早于开始日期抛 BAD_REQUEST")
    void createScenario_invalidDateRange() {
        ScrmForecastModelEntity model = buildModelEntity(10L, "FCST_001", "MOVING_AVERAGE");
        when(modelRepository.findById(10L)).thenReturn(Optional.of(model));
        when(scenarioRepository.countByScenarioCode(eq("SCN_001"))).thenReturn(0L);

        ScrmForecastScenarioDto dto = new ScrmForecastScenarioDto();
        dto.setScenarioName("场景");
        dto.setScenarioCode("SCN_001");
        dto.setModelId(10L);
        dto.setTargetPeriod("2026-Q4");
        dto.setTargetStartDate(LocalDate.of(2026, 12, 31));
        dto.setTargetEndDate(LocalDate.of(2026, 10, 1)); // 早于开始日期

        assertThatThrownBy(() -> service.createScenario(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("目标结束日期不能早于开始日期");
        verify(scenarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("createScenario: 写入账号 ID 与默认场景类型后持久化")
    void createScenario_success() throws ScrmException {
        ScrmForecastModelEntity model = buildModelEntity(10L, "FCST_001", "MOVING_AVERAGE");
        when(modelRepository.findById(10L)).thenReturn(Optional.of(model));
        when(scenarioRepository.countByScenarioCode(eq("SCN_001"))).thenReturn(0L);
        when(scenarioRepository.save(any(ScrmForecastScenarioEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmForecastScenarioDto dto = new ScrmForecastScenarioDto();
        dto.setScenarioName("基线场景");
        dto.setScenarioCode("SCN_001");
        dto.setModelId(10L);
        dto.setTargetPeriod("2026-Q4");
        dto.setTargetStartDate(LocalDate.of(2026, 10, 1));
        dto.setTargetEndDate(LocalDate.of(2026, 12, 31));

        ScrmForecastScenarioEntity result = service.createScenario(dto);

        ArgumentCaptor<ScrmForecastScenarioEntity> captor =
                ArgumentCaptor.forClass(ScrmForecastScenarioEntity.class);
        verify(scenarioRepository, times(1)).save(captor.capture());
        ScrmForecastScenarioEntity saved = captor.getValue();
        // scenarioType 缺省时填 BASELINE
        assertThat(saved.getScenarioType()).isEqualTo("BASELINE");
        // granularity 缺省时填 MONTHLY
        assertThat(saved.getGranularity()).isEqualTo("MONTHLY");
        // confidenceLevel 缺省时填 0.95
        assertThat(saved.getConfidenceLevel()).isEqualTo(0.95d);
        assertThat(saved.getModelId()).isEqualTo(10L);
        assertThat(saved.getModelName()).isEqualTo("销售预测模型");
        assertThat(result.getScenarioCode()).isEqualTo("SCN_001");
    }

    @Test
    @DisplayName("approveScenario: 非已完成状态抛 BAD_REQUEST")
    void approveScenario_invalidStatus() {
        ScrmForecastScenarioEntity entity = buildScenarioEntity(20L, "SCN_001", 10L);
        entity.setStatus("DRAFT"); // 非 COMPLETED
        when(scenarioRepository.findById(20L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.approveScenario(20L, "reviewer"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅已完成的场景可审批");
        verify(scenarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("archiveScenario: 设置状态为 ARCHIVED 并持久化")
    void archiveScenario_success() throws ScrmException {
        ScrmForecastScenarioEntity entity = buildScenarioEntity(20L, "SCN_001", 10L);
        entity.setStatus("COMPLETED");
        when(scenarioRepository.findById(20L)).thenReturn(Optional.of(entity));
        when(scenarioRepository.save(any(ScrmForecastScenarioEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.archiveScenario(20L);

        ArgumentCaptor<ScrmForecastScenarioEntity> captor =
                ArgumentCaptor.forClass(ScrmForecastScenarioEntity.class);
        verify(scenarioRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("getModelAccuracy: 返回模型准确度指标 Map")
    void getModelAccuracy_success() throws ScrmException {
        ScrmForecastModelEntity entity = buildModelEntity(10L, "FCST_001", "MOVING_AVERAGE");
        entity.setIsTrained(Boolean.TRUE);
        entity.setLastAccuracyScore(0.88d);
        entity.setLastMape(12.0d);
        entity.setLastMae(120.0d);
        entity.setLastRmse(150.0d);
        entity.setLastR2(0.75d);
        entity.setCrossValidationScore(0.82d);
        entity.setTrainingDataPoints(36);
        entity.setModelVersion(3);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(entity));

        Map<String, Object> result = service.getModelAccuracy(10L);

        assertThat(result.get("modelId")).isEqualTo(10L);
        assertThat(result.get("modelCode")).isEqualTo("FCST_001");
        assertThat(result.get("isTrained")).isEqualTo(Boolean.TRUE);
        assertThat(result.get("accuracyScore")).isEqualTo(0.88d);
        assertThat(result.get("mape")).isEqualTo(12.0d);
        assertThat(result.get("modelVersion")).isEqualTo(3);
        assertThat(result.get("trainingDataPoints")).isEqualTo(36);
    }
}
