/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadScoringServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmLeadScoreResultDto;
import org.hiylo.scrm.dto.ScrmLeadScoringModelDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmLeadScoreEntity;
import org.hiylo.scrm.entity.ScrmLeadScoringModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmLeadDimensionRepository;
import org.hiylo.scrm.repository.ScrmLeadScoreRepository;
import org.hiylo.scrm.repository.ScrmLeadScoringModelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
 * ScrmLeadScoringService 单元测试
 * <p>
 * 聚焦销售线索评分模型管理 (创建 / 默认值填充 / 编码唯一性校验)、线索评分计算
 * (规则匹配 / 等级判定 / 趋势计算 / 模型应用统计递增) 与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmLeadScoringService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmLeadScoringServiceTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 线索评分模型仓库 Mock */
    @Mock
    private ScrmLeadScoringModelRepository modelRepository;
    /** 线索评分仓库 Mock */
    @Mock
    private ScrmLeadScoreRepository scoreRepository;
    /** 线索评分维度仓库 Mock */
    @Mock
    private ScrmLeadDimensionRepository dimensionRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmLeadScoringService service;

    @BeforeEach
    void setUp() {
        ScrmLeadScoringModelService modelService =
                new ScrmLeadScoringModelService(modelRepository, objectMapper);
        ScrmLeadDimensionService dimensionService =
                new ScrmLeadDimensionService(dimensionRepository, objectMapper, modelService);
        ScrmLeadScoringCalculationService calculationService =
                new ScrmLeadScoringCalculationService(modelRepository, scoreRepository, customerRepository,
                        objectMapper, modelService);
        ScrmLeadAssignmentService assignmentService =
                new ScrmLeadAssignmentService(scoreRepository, modelService, calculationService);
        service = new ScrmLeadScoringService(modelService, dimensionService, calculationService, assignmentService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的评分模型实体 (用于 findById 返回)
     */
    private ScrmLeadScoringModelEntity buildModelEntity(Long id) {
        ScrmLeadScoringModelEntity entity = new ScrmLeadScoringModelEntity();
        entity.setId(id);
        entity.setModelName("默认评分模型");
        entity.setModelCode("MODEL_DEFAULT");
        entity.setModelType("RULE_BASED");
        // 维度配置: 单维度 ENGAGEMENT, 权重 3.0, 上限 20, 规则 lastInteractionDays>30 得 20 分
        entity.setDimensions("[{\"dimension\":\"ENGAGEMENT\",\"weight\":3.0,\"maxScore\":20,"
                + "\"fields\":[{\"field\":\"lastInteractionDays\",\"operator\":\"gt\",\"value\":30,\"score\":20}]}]");
        entity.setTotalMaxScore(100);
        entity.setIsDefault(false);
        entity.setIsPublished(true);
        entity.setVersionNo(1);
        entity.setAppliedCount(0);
        return entity;
    }

    /**
     * 构造已持久化的客户实体 (lastInteractionAt 设为 60 天前, 使 lastInteractionDays=60 命中 >30 规则)
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setNickname("张三");
        entity.setLifecycle("ACTIVE");
        entity.setPlatformType("WECHAT");
        entity.setLastInteractionAt(LocalDateTime.now().minusDays(60));
        entity.setCreateTime(LocalDateTime.now().minusDays(100));
        return entity;
    }

    @Test
    @DisplayName("createModel: 写入账号 ID 与默认值后持久化")
    void createModel_success() throws ScrmException {
        when(modelRepository.findByModelCode("MODEL_DEFAULT"))
                .thenReturn(Optional.empty());
        when(modelRepository.save(any(ScrmLeadScoringModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmLeadScoringModelDto dto = new ScrmLeadScoringModelDto();
        dto.setModelName("默认评分模型");
        dto.setModelCode("MODEL_DEFAULT");
        dto.setDimensions("[{\"dimension\":\"ENGAGEMENT\",\"weight\":1.0,\"maxScore\":20}]");
        dto.setCreatedBy("admin01");

        ScrmLeadScoringModelEntity result = service.createModel(dto);

        ArgumentCaptor<ScrmLeadScoringModelEntity> captor =
                ArgumentCaptor.forClass(ScrmLeadScoringModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        ScrmLeadScoringModelEntity saved = captor.getValue();
        assertThat(saved.getModelType()).isEqualTo("RULE_BASED");
        assertThat(saved.getTotalMaxScore()).isEqualTo(100);
        assertThat(saved.getVersionNo()).isEqualTo(1);
        assertThat(saved.getIsDefault()).isFalse();
        assertThat(saved.getIsPublished()).isFalse();
        assertThat(saved.getAppliedCount()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo("admin01");
        assertThat(result.getModelCode()).isEqualTo("MODEL_DEFAULT");
    }

    @Test
    @DisplayName("createModel: modelCode 重复时抛 CONFLICT")
    void createModel_duplicateCode() {
        ScrmLeadScoringModelEntity existing = buildModelEntity(10L);
        when(modelRepository.findByModelCode("MODEL_DEFAULT"))
                .thenReturn(Optional.of(existing));

        ScrmLeadScoringModelDto dto = new ScrmLeadScoringModelDto();
        dto.setModelName("默认评分模型");
        dto.setModelCode("MODEL_DEFAULT");
        dto.setDimensions("[{\"dimension\":\"ENGAGEMENT\",\"weight\":1.0,\"maxScore\":20}]");

        assertThatThrownBy(() -> service.createModel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模型编码已存在");
        verify(modelRepository, never()).save(any());
    }

    @Test
    @DisplayName("calculateScore: 命中规则后写入评分记录与等级, 递增模型应用次数")
    void calculateScore_success() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        ScrmLeadScoringModelEntity model = buildModelEntity(10L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(modelRepository.findById(10L)).thenReturn(Optional.of(model));
        when(scoreRepository.findByCustomerIdAndModelId(100L, 10L))
                .thenReturn(Optional.empty());
        when(scoreRepository.save(any(ScrmLeadScoreEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmLeadScoreResultDto result = service.calculateScore(100L, 10L);

        ArgumentCaptor<ScrmLeadScoreEntity> captor =
                ArgumentCaptor.forClass(ScrmLeadScoreEntity.class);
        verify(scoreRepository, times(1)).save(captor.capture());
        ScrmLeadScoreEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(100L);
        assertThat(saved.getModelId()).isEqualTo(10L);
        assertThat(saved.getCustomerName()).isEqualTo("张三");
        // dimScore=20, weight=3.0, totalScore=60, capped at 100
        assertThat(saved.getTotalScore()).isEqualTo(60.0);
        assertThat(saved.getMaxScore()).isEqualTo(100.0);
        assertThat(saved.getScorePercent()).isEqualTo(60.0);
        // percent=0.6 → GRADE_HOT (0.55~0.70)
        assertThat(saved.getGrade()).isEqualTo("HOT");
        assertThat(saved.getGradeLabel()).isEqualTo("热线索");
        // isHotLead: HOT → true; isQualified: not DEAD/COLD and 0.6>=0.4 → true
        assertThat(saved.getIsHotLead()).isTrue();
        assertThat(saved.getIsQualified()).isTrue();
        // probability = 0.6*0.6 + 0.4*1 = 0.76
        assertThat(saved.getConversionProbability()).isEqualTo(0.76);
        assertThat(saved.getPredictedValue()).isEqualTo(760.0);
        // previousScore=0, newScore=60, change=60 > 0.5 → UP
        assertThat(saved.getPreviousScore()).isEqualTo(0.0);
        assertThat(saved.getScoreTrend()).isEqualTo("UP");
        assertThat(saved.getTrendChange()).isEqualTo(60.0);
        // 递增模型应用次数
        verify(modelRepository, times(1)).incrementAppliedCount(eq(10L), any(LocalDateTime.class));
        // 返回 DTO
        assertThat(result.getTotalScore()).isEqualTo(60.0);
        assertThat(result.getGrade()).isEqualTo("HOT");
        assertThat(result.getConversionProbability()).isEqualTo(0.76);
    }

    
    
    @Test
    @DisplayName("getScoreByCustomer: 按客户与模型查询返回评分记录")
    void getScoreByCustomer_success() {
        ScrmLeadScoreEntity entity = new ScrmLeadScoreEntity();
        entity.setId(50L);
        entity.setCustomerId(100L);
        entity.setModelId(10L);
        entity.setTotalScore(60.0);
        entity.setGrade("HOT");
        when(scoreRepository.findByCustomerIdAndModelId(100L, 10L))
                .thenReturn(Optional.of(entity));

        ScrmLeadScoreEntity result = service.getScoreByCustomer(100L, 10L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(50L);
        assertThat(result.getTotalScore()).isEqualTo(60.0);
        assertThat(result.getGrade()).isEqualTo("HOT");
    }
}
