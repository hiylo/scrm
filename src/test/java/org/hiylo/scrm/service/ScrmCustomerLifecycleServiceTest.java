/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLifecycleServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmCustomerLifecycleDto;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleEntity;
import org.hiylo.scrm.entity.ScrmLifecycleStageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerLifecycleRepository;
import org.hiylo.scrm.repository.ScrmLifecycleHistoryRepository;
import org.hiylo.scrm.repository.ScrmLifecycleStageRepository;
import org.hiylo.scrm.repository.ScrmLifecycleTransitionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmCustomerLifecycleService 单元测试
 * <p>
 * 聚焦客户生命周期记录管理 (创建 / 更新 / 唯一性校验)、阶段树查询、
 * 风险评分计算与越权访问等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCustomerLifecycleService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCustomerLifecycleServiceTest {

    /** 客户生命周期服务 Mock 桩 */
    @Mock
    private ScrmLifecycleService scrmLifecycleService;
    /** 生命周期阶段数据仓库 Mock 桩 */
    @Mock
    private ScrmLifecycleStageRepository stageRepository;
    /** 客户生命周期数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerLifecycleRepository customerLifecycleRepository;
    /** 生命周期流转规则仓库 Mock */
    @Mock
    private ScrmLifecycleTransitionRepository transitionRepository;
    /** 生命周期历史记录仓库 Mock */
    @Mock
    private ScrmLifecycleHistoryRepository historyRepository;

    /** 被测服务实例 */
    private ScrmCustomerLifecycleService service;
    /** 生命周期分析兄弟服务 (持有 computeRiskLevel/calculateChurnRisk 私有方法) */
    private ScrmCustomerLifecycleAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new ScrmCustomerLifecycleAnalyticsService(scrmLifecycleService, stageRepository,
                customerLifecycleRepository, historyRepository);
        ScrmCustomerLifecycleStageService stageService =
                new ScrmCustomerLifecycleStageService(scrmLifecycleService, stageRepository,
                        transitionRepository);
        ScrmCustomerLifecycleRecordService recordService =
                new ScrmCustomerLifecycleRecordService(scrmLifecycleService, analyticsService,
                        stageRepository, customerLifecycleRepository, transitionRepository);
        ScrmCustomerLifecycleTransitionService transitionService =
                new ScrmCustomerLifecycleTransitionService(scrmLifecycleService, analyticsService,
                        stageRepository, customerLifecycleRepository, historyRepository);
        service = new ScrmCustomerLifecycleService(stageService, recordService, analyticsService,
                transitionService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的客户生命周期实体 (用于 findById 返回)
     */
    private ScrmCustomerLifecycleEntity buildLifecycleEntity(Long id, Long customerId) {
        ScrmCustomerLifecycleEntity entity = new ScrmCustomerLifecycleEntity();
        entity.setId(id);
        entity.setCustomerId(customerId);
        entity.setCustomerName("张三");
        entity.setCurrentStageId(10L);
        entity.setCurrentStageCode("ENGAGEMENT");
        entity.setCurrentStageName("互动期");
        entity.setEnteredCurrentStageAt(LocalDateTime.now().minusDays(10));
        entity.setDurationInStageDays(10);
        entity.setStageHistoryCount(2);
        entity.setIsOverdue(false);
        entity.setOverdueDays(0);
        entity.setLastUpdatedAt(LocalDateTime.now());
        return entity;
    }

    /**
     * 构造阶段实体
     */
    private ScrmLifecycleStageEntity buildStageEntity(Long id, String code, String category, int order) {
        ScrmLifecycleStageEntity entity = new ScrmLifecycleStageEntity();
        entity.setId(id);
        entity.setStageName(code);
        entity.setStageCode(code);
        entity.setStageCategory(category);
        entity.setStageOrder(order);
        entity.setIsStartStage(false);
        entity.setIsEndStage(false);
        entity.setIsChurnStage(false);
        entity.setEnabled(true);
        return entity;
    }

    @Test
    @DisplayName("createLifecycle: 写入归属账号与默认值后持久化")
    void createLifecycle_success() throws ScrmException {
        ScrmCustomerLifecycleDto dto = new ScrmCustomerLifecycleDto();
        dto.setCustomerId(100L);
        dto.setCustomerName("张三");
        dto.setCurrentStageId(10L);
        dto.setCurrentStageCode("ENGAGEMENT");
        dto.setCurrentStageName("互动期");
        when(customerLifecycleRepository.findByCustomerId(100L))
                .thenReturn(Optional.empty());
        when(customerLifecycleRepository.save(any(ScrmCustomerLifecycleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerLifecycleEntity result = service.createLifecycle(dto);

        ArgumentCaptor<ScrmCustomerLifecycleEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerLifecycleEntity.class);
        verify(customerLifecycleRepository, times(1)).save(captor.capture());
        ScrmCustomerLifecycleEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(100L);
        assertThat(saved.getStageHistoryCount()).isZero();
        assertThat(saved.getIsOverdue()).isFalse();
        assertThat(saved.getOverdueDays()).isZero();
        assertThat(saved.getDurationInStageDays()).isZero();
        assertThat(saved.getEnteredCurrentStageAt()).isNotNull();
        assertThat(saved.getLastUpdatedAt()).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("createLifecycle: 客户生命周期已存在时抛 CONFLICT")
    void createLifecycle_alreadyExists() {
        ScrmCustomerLifecycleDto dto = new ScrmCustomerLifecycleDto();
        dto.setCustomerId(100L);
        dto.setCurrentStageId(10L);
        dto.setCurrentStageCode("ENGAGEMENT");
        when(customerLifecycleRepository.findByCustomerId(100L))
                .thenReturn(Optional.of(buildLifecycleEntity(1L, 100L)));

        assertThatThrownBy(() -> service.createLifecycle(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户生命周期已存在");
        verify(customerLifecycleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createLifecycle: 客户 ID 为空时抛 BAD_REQUEST")
    void createLifecycle_nullCustomerId() {
        ScrmCustomerLifecycleDto dto = new ScrmCustomerLifecycleDto();
        dto.setCurrentStageId(10L);
        dto.setCurrentStageCode("ENGAGEMENT");

        assertThatThrownBy(() -> service.createLifecycle(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 不能为空");
        verify(customerLifecycleRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateLifecycle: 字段非空才覆盖, 保留未提供字段原值")
    void updateLifecycle_partialUpdate() throws ScrmException {
        ScrmCustomerLifecycleEntity entity = buildLifecycleEntity(1L, 100L);
        when(customerLifecycleRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(customerLifecycleRepository.save(any(ScrmCustomerLifecycleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerLifecycleDto dto = new ScrmCustomerLifecycleDto();
        dto.setCustomerName("李四");
        dto.setCurrentStageCode("RETENTION");
        ScrmCustomerLifecycleEntity result = service.updateLifecycle(1L, dto);

        assertThat(result.getCustomerName()).isEqualTo("李四");
        assertThat(result.getCurrentStageCode()).isEqualTo("RETENTION");
        // 未提供的字段保留原值
        assertThat(result.getCustomerId()).isEqualTo(100L);
        assertThat(result.getCurrentStageId()).isEqualTo(10L);
    }

    
    @Test
    @DisplayName("getStagesByCategory: 按阶段类别过滤返回匹配阶段")
    void getStagesByCategory_filter() throws ScrmException {
        ScrmLifecycleStageEntity s1 = buildStageEntity(10L, "LEAD", "ACQUISITION", 1);
        ScrmLifecycleStageEntity s2 = buildStageEntity(20L, "ENGAGEMENT", "ENGAGEMENT", 2);
        ScrmLifecycleStageEntity s3 = buildStageEntity(30L, "SIGN", "ACQUISITION", 3);
        when(stageRepository.findAllByOrderByStageOrderAsc())
                .thenReturn(List.of(s1, s2, s3));

        List<ScrmLifecycleStageEntity> result = service.getStagesByCategory("ACQUISITION");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ScrmLifecycleStageEntity::getStageCode)
                .containsExactly("LEAD", "SIGN");
    }

    @Test
    @DisplayName("getStagesByCategory: 类别为空时抛 BAD_REQUEST")
    void getStagesByCategory_blankCategory() {
        assertThatThrownBy(() -> service.getStagesByCategory(""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阶段类别不能为空");
    }

    @Test
    @DisplayName("getStageTree: 按类别分组并返回树结构")
    void getStageTree_success() {
        ScrmLifecycleStageEntity s1 = buildStageEntity(10L, "LEAD", "ACQUISITION", 1);
        ScrmLifecycleStageEntity s2 = buildStageEntity(20L, "ENGAGEMENT", "ENGAGEMENT", 2);
        ScrmLifecycleStageEntity s3 = buildStageEntity(30L, "SIGN", "ACQUISITION", 3);
        when(stageRepository.findAllByOrderByStageOrderAsc())
                .thenReturn(List.of(s1, s2, s3));

        Map<String, Object> tree = service.getStageTree();

        assertThat(tree).containsKeys("categories", "tree", "total");
        assertThat(tree.get("total")).isEqualTo(3);
        @SuppressWarnings("unchecked")
        Map<String, List<ScrmLifecycleStageEntity>> grouped =
                (Map<String, List<ScrmLifecycleStageEntity>>) tree.get("tree");
        assertThat(grouped).containsKeys("ACQUISITION", "ENGAGEMENT");
        assertThat(grouped.get("ACQUISITION")).hasSize(2);
        assertThat(grouped.get("ENGAGEMENT")).hasSize(1);
    }

    @Test
    @DisplayName("computeRiskLevel: 流失风险 ≥ 80 返回 CRITICAL, ≥ 60 返回 HIGH, ≥ 40 返回 MEDIUM, 其他 LOW")
    void computeRiskLevel_thresholds() {
        Object r1 = ReflectionTestUtils.invokeMethod(analyticsService, "computeRiskLevel", 85.0d);
        assertThat(r1).isEqualTo("CRITICAL");
        Object r2 = ReflectionTestUtils.invokeMethod(analyticsService, "computeRiskLevel", 65.0d);
        assertThat(r2).isEqualTo("HIGH");
        Object r3 = ReflectionTestUtils.invokeMethod(analyticsService, "computeRiskLevel", 45.0d);
        assertThat(r3).isEqualTo("MEDIUM");
        Object r4 = ReflectionTestUtils.invokeMethod(analyticsService, "computeRiskLevel", 15.0d);
        assertThat(r4).isEqualTo("LOW");
    }

    @Test
    @DisplayName("calculateChurnRisk: 流失阶段加成 30 分, 超期天数累加")
    void calculateChurnRisk_churnStageBonus() {
        // 构造流失阶段实体
        ScrmLifecycleStageEntity churnStage = buildStageEntity(50L, "CHURN", "CHURN", 99);
        churnStage.setIsChurnStage(true);
        when(stageRepository.findById(50L)).thenReturn(Optional.of(churnStage));
        // 构造处于流失阶段的客户生命周期
        ScrmCustomerLifecycleEntity lifecycle = buildLifecycleEntity(1L, 100L);
        lifecycle.setCurrentStageId(50L);
        lifecycle.setIsOverdue(true);
        lifecycle.setOverdueDays(15);
        lifecycle.setStageHistoryCount(1);

        Double risk = (Double) ReflectionTestUtils.invokeMethod(analyticsService, "calculateChurnRisk", lifecycle);

        assertThat(risk).isNotNull();
        // 流失阶段直接 +30, 超期天数 +15 (cap 20), 历史次数低 +18 (20-1*2)
        // 风险值至少应大于等于流失阶段加成 + 超期 (>= 45)
        assertThat(risk).isGreaterThanOrEqualTo(45.0d);
    }

    @Test
    @DisplayName("calculateChurnRisk: null 入参返回 0")
    void calculateChurnRisk_nullLifecycle() {
        Double risk = (Double) ReflectionTestUtils.invokeMethod(analyticsService, "calculateChurnRisk",
                (Object) null);
        assertThat(risk).isZero();
    }
}
