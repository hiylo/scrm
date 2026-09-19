/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmCompetitorDto;
import org.hiylo.scrm.dto.ScrmCompetitorProductDto;
import org.hiylo.scrm.entity.ScrmCompetitorEntity;
import org.hiylo.scrm.entity.ScrmCompetitorProductEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCompetitorActivityRepository;
import org.hiylo.scrm.repository.ScrmCompetitorProductRepository;
import org.hiylo.scrm.repository.ScrmCompetitorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * ScrmCompetitorService 单元测试
 * <p>
 * 聚焦竞品管理 (创建 / 默认值填充 / 编码唯一性校验)、竞品产品管理
 * (创建 / 价格统计初始化 / 竞品名称回填)、监测频率校验、归档与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCompetitorService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCompetitorServiceTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 竞对数据仓库 Mock 桩 */
    @Mock
    private ScrmCompetitorRepository competitorRepository;
    /** 竞对产品数据仓库 Mock 桩 */
    @Mock
    private ScrmCompetitorProductRepository productRepository;
    /** 竞对活动数据仓库 Mock 桩 */
    @Mock
    private ScrmCompetitorActivityRepository activityRepository;

    /** 被测服务实例 */
    private ScrmCompetitorService service;

    @BeforeEach
    void setUp() {
        ScrmCompetitorAnalysisService analysisService = new ScrmCompetitorAnalysisService(
                competitorRepository, productRepository, activityRepository, objectMapper);
        ScrmCompetitorManagementService managementService = new ScrmCompetitorManagementService(
                competitorRepository, productRepository, activityRepository, analysisService);
        ScrmCompetitorProductService productService = new ScrmCompetitorProductService(
                managementService, productRepository, activityRepository, objectMapper);
        ScrmCompetitorActivityService activityService = new ScrmCompetitorActivityService(
                managementService, activityRepository);
        service = new ScrmCompetitorService(managementService, productService, activityService, analysisService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的竞品实体 (用于 findById 返回)
     */
    private ScrmCompetitorEntity buildCompetitorEntity(Long id) {
        ScrmCompetitorEntity entity = new ScrmCompetitorEntity();
        entity.setId(id);
        entity.setCompetitorName("竞品A");
        entity.setCompetitorCode("COMP001");
        entity.setThreatLevel("HIGH");
        entity.setMonitoringFrequency("DAILY");
        entity.setStatus("ACTIVE");
        return entity;
    }

    @Test
    @DisplayName("createCompetitor: 写入归属账号与默认值后持久化")
    void createCompetitor_success() throws ScrmException {
        when(competitorRepository.findByCompetitorCode(eq("COMP001")))
                .thenReturn(Optional.empty());
        when(competitorRepository.save(any(ScrmCompetitorEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCompetitorDto dto = new ScrmCompetitorDto();
        dto.setCompetitorName("竞品A");
        dto.setCompetitorCode("COMP001");
        dto.setCreatedBy("admin01");

        ScrmCompetitorEntity result = service.createCompetitor(dto);

        ArgumentCaptor<ScrmCompetitorEntity> captor =
                ArgumentCaptor.forClass(ScrmCompetitorEntity.class);
        verify(competitorRepository, times(1)).save(captor.capture());
        ScrmCompetitorEntity saved = captor.getValue();
        assertThat(saved.getThreatLevel()).isEqualTo("MEDIUM");
        assertThat(saved.getMonitoringEnabled()).isTrue();
        assertThat(saved.getMonitoringFrequency()).isEqualTo("DAILY");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getMarketShare()).isEqualTo(0.0);
        assertThat(saved.getTotalFunding()).isEqualTo(0.0);
        assertThat(saved.getCreatedBy()).isEqualTo("admin01");
        assertThat(result.getCompetitorName()).isEqualTo("竞品A");
    }

    @Test
    @DisplayName("createCompetitor: 竞品编码重复时抛 CONFLICT")
    void createCompetitor_codeConflict() {
        when(competitorRepository.findByCompetitorCode(eq("COMP001")))
                .thenReturn(Optional.of(buildCompetitorEntity(10L)));

        ScrmCompetitorDto dto = new ScrmCompetitorDto();
        dto.setCompetitorName("竞品A");
        dto.setCompetitorCode("COMP001");

        assertThatThrownBy(() -> service.createCompetitor(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("竞品编码已存在");
        verify(competitorRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProduct: 创建产品时回填竞品名称并初始化价格统计")
    void createProduct_success() throws ScrmException {
        ScrmCompetitorEntity competitor = buildCompetitorEntity(10L);
        when(competitorRepository.findById(10L)).thenReturn(Optional.of(competitor));
        when(productRepository.save(any(ScrmCompetitorProductEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCompetitorProductDto dto = new ScrmCompetitorProductDto();
        dto.setCompetitorId(10L);
        dto.setProductName("旗舰产品X");
        dto.setCurrentPrice(100.0);
        dto.setOriginalPrice(200.0);
        dto.setCreatedBy("admin01");

        ScrmCompetitorProductEntity result = service.createProduct(dto);

        ArgumentCaptor<ScrmCompetitorProductEntity> captor =
                ArgumentCaptor.forClass(ScrmCompetitorProductEntity.class);
        verify(productRepository, times(1)).save(captor.capture());
        ScrmCompetitorProductEntity saved = captor.getValue();
        assertThat(saved.getCompetitorId()).isEqualTo(10L);
        // 竞品名称从竞品实体回填
        assertThat(saved.getCompetitorName()).isEqualTo("竞品A");
        assertThat(saved.getCurrentPrice()).isEqualTo(100.0);
        assertThat(saved.getOriginalPrice()).isEqualTo(200.0);
        // 折扣率 = (200-100)/200 = 0.5
        assertThat(saved.getDiscountRate()).isEqualTo(0.5);
        assertThat(saved.getCurrency()).isEqualTo("CNY");
        // 价格统计初值 = 当前价
        assertThat(saved.getLowestPrice()).isEqualTo(100.0);
        assertThat(saved.getHighestPrice()).isEqualTo(100.0);
        assertThat(saved.getAvgPrice()).isEqualTo(100.0);
        assertThat(saved.getPriceChangeCount()).isZero();
        assertThat(saved.getLastPriceChangePercent()).isEqualTo(0.0);
        // 我方价为 0 时, 价格对比为 HIGHER, 优势评分 0
        assertThat(saved.getPriceComparison()).isEqualTo("HIGHER");
        assertThat(saved.getAdvantageScore()).isZero();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getProductName()).isEqualTo("旗舰产品X");
    }

    @Test
    @DisplayName("archiveCompetitor: 归档后状态置 ARCHIVED")
    void archiveCompetitor_success() throws ScrmException {
        ScrmCompetitorEntity competitor = buildCompetitorEntity(10L);
        when(competitorRepository.findById(10L)).thenReturn(Optional.of(competitor));
        when(competitorRepository.save(any(ScrmCompetitorEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.archiveCompetitor(10L);

        ArgumentCaptor<ScrmCompetitorEntity> captor =
                ArgumentCaptor.forClass(ScrmCompetitorEntity.class);
        verify(competitorRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("updateMonitoringFrequency: 监测频率非法时抛 BAD_REQUEST")
    void updateMonitoringFrequency_invalid() {
        assertThatThrownBy(() -> service.updateMonitoringFrequency(10L, "INVALID"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("监测频率非法");
        verify(competitorRepository, never()).save(any());
    }

    
}
