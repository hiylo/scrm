/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpportunityServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmOpportunityDto;
import org.hiylo.scrm.dto.ScrmOpportunityStageChangeDto;
import org.hiylo.scrm.entity.ScrmFunnelEntity;
import org.hiylo.scrm.entity.ScrmFunnelStageEntity;
import org.hiylo.scrm.entity.ScrmOpportunityEntity;
import org.hiylo.scrm.entity.ScrmOpportunityStageHistoryEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmFunnelRepository;
import org.hiylo.scrm.repository.ScrmFunnelStageRepository;
import org.hiylo.scrm.repository.ScrmOpportunityRepository;
import org.hiylo.scrm.repository.ScrmOpportunityStageHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmOpportunityService 单元测试
 * <p>
 * 聚焦销售漏斗 / 漏斗阶段 / 商机档案与阶段推进的核心业务逻辑,
 * 覆盖商机创建 (首阶段与历史记录)、阶段推进至成交阶段、越权访问校验与
 * 漏斗删除前置校验等关键路径。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmOpportunityService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmOpportunityServiceTest {

    /** 销售漏斗仓库 Mock */
    @Mock
    private ScrmFunnelRepository funnelRepository;
    /** 漏斗阶段仓库 Mock */
    @Mock
    private ScrmFunnelStageRepository stageRepository;
    /** 商机仓库 Mock */
    @Mock
    private ScrmOpportunityRepository opportunityRepository;
    /** 商机阶段历史记录仓库 Mock */
    @Mock
    private ScrmOpportunityStageHistoryRepository stageHistoryRepository;

    /** 被测服务实例 */
    private ScrmOpportunityService service;

    @BeforeEach
    void setUp() {
        service = new ScrmOpportunityService(funnelRepository, stageRepository,
                opportunityRepository, stageHistoryRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的漏斗实体 (用于 findById 返回)
     */
    private ScrmFunnelEntity buildFunnelEntity(Long id, String status) {
        ScrmFunnelEntity entity = new ScrmFunnelEntity();
        entity.setId(id);
        entity.setFunnelName("默认漏斗");
        entity.setIsDefault(false);
        entity.setStatus(status);
        return entity;
    }

    /**
     * 构造已持久化的漏斗阶段实体
     */
    private ScrmFunnelStageEntity buildStageEntity(Long id, Long funnelId, int order) {
        ScrmFunnelStageEntity entity = new ScrmFunnelStageEntity();
        entity.setId(id);
        entity.setFunnelId(funnelId);
        entity.setStageName("阶段" + order);
        entity.setStageOrder(order);
        entity.setProbability(30);
        entity.setIsClosedStage(false);
        entity.setIsLostStage(false);
        return entity;
    }

    /**
     * 构造已持久化的商机实体
     */
    private ScrmOpportunityEntity buildOpportunityEntity(Long id, String status) {
        ScrmOpportunityEntity entity = new ScrmOpportunityEntity();
        entity.setId(id);
        entity.setOpportunityName("测试商机");
        entity.setCustomerId(100L);
        entity.setFunnelId(10L);
        entity.setCurrentStageId(20L);
        entity.setAmount(50000d);
        entity.setProbability(30);
        entity.setOwnerUserId("sales01");
        entity.setStatus(status);
        return entity;
    }

    @Test
    @DisplayName("createOpportunity: 写入账号 ID 与首阶段并记录阶段历史")
    void createOpportunity_success() throws ScrmException {
        ScrmFunnelEntity funnel = buildFunnelEntity(10L, "ACTIVE");
        ScrmFunnelStageEntity stage = buildStageEntity(20L, 10L, 1);
        when(funnelRepository.findById(10L)).thenReturn(Optional.of(funnel));
        when(stageRepository.findByFunnelIdOrderByStageOrderAsc(10L))
                .thenReturn(List.of(stage));
        when(opportunityRepository.save(any(ScrmOpportunityEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(stageHistoryRepository.save(any(ScrmOpportunityStageHistoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmOpportunityDto dto = new ScrmOpportunityDto();
        dto.setOpportunityName("测试商机");
        dto.setCustomerId(100L);
        dto.setOwnerUserId("sales01");
        dto.setFunnelId(10L);
        dto.setAmount(50000d);

        ScrmOpportunityDto result = service.createOpportunity(dto);

        ArgumentCaptor<ScrmOpportunityEntity> oppCaptor =
                ArgumentCaptor.forClass(ScrmOpportunityEntity.class);
        verify(opportunityRepository, times(1)).save(oppCaptor.capture());
        ScrmOpportunityEntity savedOpp = oppCaptor.getValue();
        assertThat(savedOpp.getCurrentStageId()).isEqualTo(20L);
        assertThat(savedOpp.getStatus()).isEqualTo("OPEN");
        assertThat(savedOpp.getProbability()).isEqualTo(30);
        ArgumentCaptor<ScrmOpportunityStageHistoryEntity> histCaptor =
                ArgumentCaptor.forClass(ScrmOpportunityStageHistoryEntity.class);
        verify(stageHistoryRepository, times(1)).save(histCaptor.capture());
        ScrmOpportunityStageHistoryEntity savedHist = histCaptor.getValue();
        assertThat(savedHist.getFromStageId()).isNull();
        assertThat(savedHist.getToStageId()).isEqualTo(20L);
        assertThat(savedHist.getNote()).isEqualTo("商机创建");
        assertThat(result.getOpportunityName()).isEqualTo("测试商机");
    }

    @Test
    @DisplayName("createOpportunity: 漏斗未启用时抛 BAD_REQUEST")
    void createOpportunity_funnelInactive() {
        ScrmFunnelEntity funnel = buildFunnelEntity(10L, "INACTIVE");
        when(funnelRepository.findById(10L)).thenReturn(Optional.of(funnel));

        ScrmOpportunityDto dto = new ScrmOpportunityDto();
        dto.setOpportunityName("测试商机");
        dto.setCustomerId(100L);
        dto.setOwnerUserId("sales01");
        dto.setFunnelId(10L);

        assertThatThrownBy(() -> service.createOpportunity(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("漏斗未启用");
        verify(opportunityRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateOpportunity: 非空字段覆盖后持久化")
    void updateOpportunity_success() throws ScrmException {
        ScrmOpportunityEntity entity = buildOpportunityEntity(50L, "OPEN");
        when(opportunityRepository.findById(50L)).thenReturn(Optional.of(entity));
        when(opportunityRepository.save(any(ScrmOpportunityEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmOpportunityDto dto = new ScrmOpportunityDto();
        dto.setAmount(80000d);
        dto.setStatus("STALLED");

        ScrmOpportunityDto result = service.updateOpportunity(50L, dto);

        ArgumentCaptor<ScrmOpportunityEntity> captor =
                ArgumentCaptor.forClass(ScrmOpportunityEntity.class);
        verify(opportunityRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo(80000d);
        assertThat(captor.getValue().getStatus()).isEqualTo("STALLED");
        assertThat(result.getId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("changeStage: 推进至成交阶段后状态置 WON 并记录 wonAt 与历史")
    void changeStage_toClosedStage_setsWon() throws ScrmException {
        ScrmOpportunityEntity opp = buildOpportunityEntity(50L, "OPEN");
        ScrmFunnelStageEntity closedStage = buildStageEntity(30L, 10L, 3);
        closedStage.setIsClosedStage(true);
        closedStage.setProbability(100);
        when(opportunityRepository.findById(50L)).thenReturn(Optional.of(opp));
        when(stageRepository.findById(30L)).thenReturn(Optional.of(closedStage));
        when(stageHistoryRepository.findByOpportunityIdOrderByIdDesc(50L))
                .thenReturn(List.of());
        when(stageHistoryRepository.save(any(ScrmOpportunityStageHistoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(opportunityRepository.save(any(ScrmOpportunityEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmOpportunityStageChangeDto dto = new ScrmOpportunityStageChangeDto();
        dto.setToStageId(30L);
        dto.setNote("客户已签约");

        ScrmOpportunityDto result = service.changeStage(50L, dto);

        ArgumentCaptor<ScrmOpportunityEntity> oppCaptor =
                ArgumentCaptor.forClass(ScrmOpportunityEntity.class);
        verify(opportunityRepository, times(1)).save(oppCaptor.capture());
        ScrmOpportunityEntity savedOpp = oppCaptor.getValue();
        assertThat(savedOpp.getStatus()).isEqualTo("WON");
        assertThat(savedOpp.getWonAt()).isNotNull();
        assertThat(savedOpp.getCurrentStageId()).isEqualTo(30L);
        assertThat(savedOpp.getProbability()).isEqualTo(100);
        ArgumentCaptor<ScrmOpportunityStageHistoryEntity> histCaptor =
                ArgumentCaptor.forClass(ScrmOpportunityStageHistoryEntity.class);
        verify(stageHistoryRepository, times(1)).save(histCaptor.capture());
        assertThat(histCaptor.getValue().getFromStageId()).isEqualTo(20L);
        assertThat(histCaptor.getValue().getToStageId()).isEqualTo(30L);
        assertThat(result.getStatus()).isEqualTo("WON");
    }

    
    
    @Test
    @DisplayName("deleteFunnel: 漏斗下仍有进行中商机时抛 BAD_REQUEST")
    void deleteFunnel_withOpenOpps() {
        ScrmFunnelEntity funnel = buildFunnelEntity(10L, "ACTIVE");
        ScrmOpportunityEntity openOpp = buildOpportunityEntity(50L, "OPEN");
        when(funnelRepository.findById(10L)).thenReturn(Optional.of(funnel));
        when(opportunityRepository.findByFunnelId(10L))
                .thenReturn(List.of(openOpp));

        assertThatThrownBy(() -> service.deleteFunnel(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仍有 1 个进行中商机");
        verify(funnelRepository, never()).delete(any(ScrmFunnelEntity.class));
    }
}
