/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthScoreServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmHealthAlertDto;
import org.hiylo.scrm.dto.ScrmHealthCalculateDto;
import org.hiylo.scrm.dto.ScrmHealthScoreModelDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerHealthScoreEntity;
import org.hiylo.scrm.entity.ScrmHealthAlertEntity;
import org.hiylo.scrm.entity.ScrmHealthScoreModelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerHealthScoreRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmHealthAlertRepository;
import org.hiylo.scrm.repository.ScrmHealthScoreModelRepository;
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
 * ScrmHealthScoreService 单元测试
 * <p>
 * 聚焦健康度模型管理 (创建 / 更新 / 默认值填充 / 编码唯一性校验)、健康度评分计算
 * (指标规则评估 / 加权汇总 / 等级判定 / 趋势对比)、健康等级判定 (默认阈值 / 自定义阈值)、
 * 告警管理 (创建 / 检查生成) 与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmHealthScoreService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmHealthScoreServiceTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 健康度评分模型仓库 Mock */
    @Mock
    private ScrmHealthScoreModelRepository modelRepository;
    /** 客户健康度评分仓库 Mock */
    @Mock
    private ScrmCustomerHealthScoreRepository scoreRepository;
    /** 健康告警仓库 Mock */
    @Mock
    private ScrmHealthAlertRepository alertRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmHealthScoreService service;

    @BeforeEach
    void setUp() {
        ScrmHealthScoreModelService modelService =
                new ScrmHealthScoreModelService(modelRepository, objectMapper);
        ScrmHealthScoreCalculationService calculationService = new ScrmHealthScoreCalculationService(
                scoreRepository, modelRepository, customerRepository, objectMapper);
        ScrmHealthScoreAlertService alertService =
                new ScrmHealthScoreAlertService(alertRepository, scoreRepository, customerRepository);
        ScrmHealthScoreStatsService statsService = new ScrmHealthScoreStatsService(
                scoreRepository, alertRepository, modelRepository, objectMapper, calculationService);
        service = new ScrmHealthScoreService(modelService, calculationService, alertService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的健康度模型实体 (用于 findById 返回)
     */
    private ScrmHealthScoreModelEntity buildModelEntity(Long id, String modelCode) {
        ScrmHealthScoreModelEntity entity = new ScrmHealthScoreModelEntity();
        entity.setId(id);
        entity.setModelName("标准健康度模型");
        entity.setModelCode(modelCode);
        entity.setMetrics("[]");
        entity.setScoringType("WEIGHTED");
        entity.setTotalMaxScore(100);
        entity.setIsDefault(false);
        entity.setIsPublished(false);
        entity.setVersionNo(1);
        entity.setAppliedCount(0);
        entity.setUpdateFrequency("DAILY");
        return entity;
    }

    /**
     * 构造客户实体 (用于 findById 返回)
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id, String nickname, String lifecycle) {
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(id);
        customer.setNickname(nickname);
        customer.setPlatformType("WECHAT");
        customer.setPlatformCustomerUid("wx_" + id);
        customer.setOwnerAccountId(1L);
        customer.setLifecycle(lifecycle);
        customer.setLastInteractionAt(LocalDateTime.now());
        return customer;
    }

    @Test
    @DisplayName("createModel: 写入账号 ID 与默认值后持久化")
    void createModel_success() throws ScrmException {
        ScrmHealthScoreModelDto dto = new ScrmHealthScoreModelDto();
        dto.setModelName("标准健康度模型");
        dto.setModelCode("MODEL_001");
        dto.setMetrics("[{\"metricCode\":\"ENGAGE\",\"metricName\":\"互动\",\"weight\":1.0,\"maxScore\":50,\"scoringRules\":[]}]");
        dto.setCreatedBy("admin01");
        when(modelRepository.findByModelCode(eq("MODEL_001")))
                .thenReturn(Optional.empty());
        when(modelRepository.save(any(ScrmHealthScoreModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmHealthScoreModelEntity result = service.createModel(dto);

        ArgumentCaptor<ScrmHealthScoreModelEntity> captor =
                ArgumentCaptor.forClass(ScrmHealthScoreModelEntity.class);
        verify(modelRepository, times(1)).save(captor.capture());
        ScrmHealthScoreModelEntity saved = captor.getValue();
        assertThat(saved.getScoringType()).isEqualTo("WEIGHTED");
        assertThat(saved.getTotalMaxScore()).isEqualTo(100);
        assertThat(saved.getVersionNo()).isEqualTo(1);
        assertThat(saved.getAppliedCount()).isZero();
        assertThat(saved.getIsDefault()).isFalse();
        assertThat(saved.getIsPublished()).isFalse();
        assertThat(saved.getUpdateFrequency()).isEqualTo("DAILY");
        assertThat(result.getModelCode()).isEqualTo("MODEL_001");
    }

    @Test
    @DisplayName("createModel: modelCode 重复抛 CONFLICT")
    void createModel_duplicateModelCode() {
        ScrmHealthScoreModelDto dto = new ScrmHealthScoreModelDto();
        dto.setModelName("标准健康度模型");
        dto.setModelCode("MODEL_001");
        dto.setMetrics("[]");
        when(modelRepository.findByModelCode(eq("MODEL_001")))
                .thenReturn(Optional.of(buildModelEntity(10L, "MODEL_001")));

        assertThatThrownBy(() -> service.createModel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模型编码已存在");
        verify(modelRepository, never()).save(any());
    }

    @Test
    @DisplayName("createModel: 评分类型非法抛 BAD_REQUEST")
    void createModel_invalidScoringType() {
        ScrmHealthScoreModelDto dto = new ScrmHealthScoreModelDto();
        dto.setModelName("标准健康度模型");
        dto.setModelCode("MODEL_001");
        dto.setMetrics("[]");
        dto.setScoringType("INVALID");

        assertThatThrownBy(() -> service.createModel(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("评分类型非法");
        verify(modelRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateModel: 字段非空才覆盖, 保留未提供字段原值")
    void updateModel_partialUpdate() throws ScrmException {
        ScrmHealthScoreModelEntity entity = buildModelEntity(10L, "MODEL_001");
        entity.setScoringType("SIMPLE");
        entity.setTotalMaxScore(80);
        when(modelRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(modelRepository.save(any(ScrmHealthScoreModelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmHealthScoreModelDto dto = new ScrmHealthScoreModelDto();
        dto.setModelName("更新后的模型名");
        dto.setTotalMaxScore(120);
        ScrmHealthScoreModelEntity result = service.updateModel(10L, dto);

        assertThat(result.getModelName()).isEqualTo("更新后的模型名");
        assertThat(result.getTotalMaxScore()).isEqualTo(120);
        // 未提供的字段保留原值
        assertThat(result.getModelCode()).isEqualTo("MODEL_001");
        assertThat(result.getScoringType()).isEqualTo("SIMPLE");
        assertThat(result.getUpdateFrequency()).isEqualTo("DAILY");
    }

    @Test
    @DisplayName("calculateHealthScore: 命中规则后写入得分与健康等级")
    void calculateHealthScore_success() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L, "张三", "ACTIVE");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        ScrmHealthScoreModelEntity model = buildModelEntity(10L, "MODEL_001");
        model.setScoringType("WEIGHTED");
        model.setTotalMaxScore(100);
        // 指标: lifecycle == ACTIVE 时得分 80, 权重 1.0, 满分 80
        model.setMetrics("[{\"metricCode\":\"ENGAGE\",\"metricName\":\"互动\",\"weight\":1.0,\"maxScore\":80,"
                + "\"scoringRules\":[{\"field\":\"lifecycle\",\"operator\":\"eq\",\"value\":\"ACTIVE\",\"score\":80}]}]");
        when(modelRepository.findById(10L)).thenReturn(Optional.of(model));
        when(scoreRepository.findByCustomerIdAndModelId(eq(100L), eq(10L)))
                .thenReturn(Optional.empty());
        when(scoreRepository.save(any(ScrmCustomerHealthScoreEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmHealthCalculateDto calcDto = new ScrmHealthCalculateDto();
        calcDto.setCustomerId(100L);
        calcDto.setModelId(10L);
        calcDto.setForceRecalculate(true);

        ScrmCustomerHealthScoreEntity result = service.calculateHealthScore(calcDto);

        ArgumentCaptor<ScrmCustomerHealthScoreEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerHealthScoreEntity.class);
        verify(scoreRepository, times(1)).save(captor.capture());
        ScrmCustomerHealthScoreEntity saved = captor.getValue();
        // totalScore = 80 (规则命中 80 分, 权重 1.0)
        assertThat(saved.getTotalScore()).isEqualTo(80.0);
        assertThat(saved.getMaxScore()).isEqualTo(100.0);
        // 80/100 = 0.8 落在 [0.70, 0.85) → HEALTHY
        assertThat(saved.getHealthLevel()).isEqualTo("HEALTHY");
        assertThat(saved.getHealthLabel()).isEqualTo("健康");
        assertThat(saved.getIsAtRisk()).isFalse();
        assertThat(saved.getIsChurnRisk()).isFalse();
        assertThat(saved.getScoreTrend()).isEqualTo("IMPROVING");
        assertThat(saved.getRiskLevel()).isEqualTo("NONE");
        assertThat(saved.getCustomerName()).isEqualTo("张三");
        assertThat(result.getTotalScore()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("determineLevel: 默认阈值按分数百分比映射")
    void determineLevel_defaultThresholds() {
        // maxScore=100, 阈值: ≥85 EXCELLENT, ≥70 HEALTHY, ≥50 NEUTRAL, ≥30 AT_RISK, 否则 CRITICAL
        assertThat(service.determineLevel(85, 100, null)).isEqualTo("EXCELLENT");
        assertThat(service.determineLevel(70, 100, null)).isEqualTo("HEALTHY");
        assertThat(service.determineLevel(50, 100, null)).isEqualTo("NEUTRAL");
        assertThat(service.determineLevel(30, 100, null)).isEqualTo("AT_RISK");
        assertThat(service.determineLevel(20, 100, null)).isEqualTo("CRITICAL");
        assertThat(service.determineLevel(0, 100, null)).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("determineLevel: 自定义阈值 JSON 按区间匹配")
    void determineLevel_customThresholds() {
        String thresholds = "["
                + "{\"level\":\"EXCELLENT\",\"minScore\":85,\"maxScore\":100},"
                + "{\"level\":\"HEALTHY\",\"minScore\":70,\"maxScore\":84},"
                + "{\"level\":\"NEUTRAL\",\"minScore\":50,\"maxScore\":69}"
                + "]";
        assertThat(service.determineLevel(75, 100, thresholds)).isEqualTo("HEALTHY");
        assertThat(service.determineLevel(90, 100, thresholds)).isEqualTo("EXCELLENT");
        assertThat(service.determineLevel(55, 100, thresholds)).isEqualTo("NEUTRAL");
    }

    @Test
    @DisplayName("createAlert: 写入账号 ID 与默认值后持久化")
    void createAlert_success() throws ScrmException {
        when(alertRepository.save(any(ScrmHealthAlertEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmHealthAlertDto dto = new ScrmHealthAlertDto();
        dto.setAlertName("低分告警");
        dto.setCustomerId(100L);
        dto.setCustomerName("张三");
        dto.setAlertType("LOW_SCORE");
        dto.setTriggerValue(20.0);
        dto.setThresholdValue(30.0);
        dto.setCreatedBy("admin01");

        ScrmHealthAlertEntity result = service.createAlert(dto);

        ArgumentCaptor<ScrmHealthAlertEntity> captor =
                ArgumentCaptor.forClass(ScrmHealthAlertEntity.class);
        verify(alertRepository, times(1)).save(captor.capture());
        ScrmHealthAlertEntity saved = captor.getValue();
        assertThat(saved.getSeverity()).isEqualTo("WARNING");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getTriggeredAt()).isNotNull();
        assertThat(result.getAlertType()).isEqualTo("LOW_SCORE");
    }

    @Test
    @DisplayName("checkAndGenerateAlerts: 低分客户生成 LOW_SCORE 告警")
    void checkAndGenerateAlerts_lowScore() throws ScrmException {
        ScrmCustomerHealthScoreEntity score = new ScrmCustomerHealthScoreEntity();
        score.setId(50L);
        score.setCustomerId(100L);
        score.setModelId(10L);
        score.setTotalScore(20.0);
        score.setPreviousScore(20.0);
        score.setHealthLevel("NEUTRAL");
        score.setIsChurnRisk(false);
        score.setIsAtRisk(true);
        score.setLastInteractionDays(0);
        score.setOpenTickets(0);
        score.setCalculatedAt(LocalDateTime.now());
        when(scoreRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(score));
        ScrmCustomerEntity customer = buildCustomerEntity(100L, "张三", "ACTIVE");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(alertRepository.save(any(ScrmHealthAlertEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<ScrmHealthAlertEntity> result = service.checkAndGenerateAlerts(100L);

        assertThat(result).hasSize(1);
        ArgumentCaptor<ScrmHealthAlertEntity> captor =
                ArgumentCaptor.forClass(ScrmHealthAlertEntity.class);
        verify(alertRepository, times(1)).save(captor.capture());
        ScrmHealthAlertEntity saved = captor.getValue();
        assertThat(saved.getAlertType()).isEqualTo("LOW_SCORE");
        assertThat(saved.getSeverity()).isEqualTo("URGENT");
        assertThat(saved.getTriggerValue()).isEqualTo(20.0);
        assertThat(saved.getThresholdValue()).isEqualTo(30.0);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCustomerId()).isEqualTo(100L);
    }

    
}
