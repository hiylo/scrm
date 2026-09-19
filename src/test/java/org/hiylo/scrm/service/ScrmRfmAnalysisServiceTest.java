/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmAnalysisServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmRfmAnalysisDto;
import org.hiylo.scrm.dto.ScrmRfmCalculateDto;
import org.hiylo.scrm.dto.ScrmRfmConfigDto;
import org.hiylo.scrm.dto.ScrmRfmSegmentStrategyDto;
import org.hiylo.scrm.entity.ScrmConversationEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmRfmAnalysisEntity;
import org.hiylo.scrm.entity.ScrmRfmConfigEntity;
import org.hiylo.scrm.entity.ScrmRfmSegmentStrategyEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmRfmAnalysisRepository;
import org.hiylo.scrm.repository.ScrmRfmConfigRepository;
import org.hiylo.scrm.repository.ScrmRfmSegmentStrategyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collections;
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
 * ScrmRfmAnalysisService 单元测试
 * <p>
 * 聚焦 RFM 配置管理 (创建 / 默认值填充 / 默认配置冲突校验)、客户 RFM 计算 (R/F/M 评分与分群编码)、
 * 评分算法 (R/F/M 评分阈值映射 / 分群编码 / 加权综合价值分)、分群分布统计、分群策略匹配
 * 与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmRfmAnalysisService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmRfmAnalysisServiceTest {

    /** RFM 配置仓库 Mock */
    @Mock
    private ScrmRfmConfigRepository configRepository;
    /** RFM 分析结果仓库 Mock */
    @Mock
    private ScrmRfmAnalysisRepository analysisRepository;
    /** RFM 分群策略仓库 Mock */
    @Mock
    private ScrmRfmSegmentStrategyRepository strategyRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 会话仓库 Mock */
    @Mock
    private ScrmConversationRepository conversationRepository;

    /** 被测服务实例 */
    private ScrmRfmAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new ScrmRfmAnalysisService(configRepository, analysisRepository,
                strategyRepository, customerRepository, conversationRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的 RFM 配置实体 (用于 findById 返回)
     */
    private ScrmRfmConfigEntity buildConfigEntity(Long id) {
        ScrmRfmConfigEntity entity = new ScrmRfmConfigEntity();
        entity.setId(id);
        entity.setConfigName("默认 RFM 配置");
        entity.setRWeight(0.3);
        entity.setFWeight(0.3);
        entity.setMWeight(0.4);
        entity.setRThreshold(30);
        entity.setFThreshold(10);
        entity.setMThreshold(1000d);
        entity.setRecencySource("LAST_INTERACTION");
        entity.setMonetarySource("TOTAL_SPENT");
        entity.setIsDefault(false);
        entity.setEnabled(true);
        return entity;
    }

    /**
     * 构造已持久化的客户实体 (用于 findById 返回)
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id, LocalDateTime lastInteractionAt) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setNickname("张三");
        entity.setLastInteractionAt(lastInteractionAt);
        return entity;
    }

    /**
     * 构造已持久化的会话实体 (用于 findByCustomerId 返回)
     */
    private ScrmConversationEntity buildConversationEntity(Long id) {
        ScrmConversationEntity entity = new ScrmConversationEntity();
        entity.setId(id);
        return entity;
    }

    @Test
    @DisplayName("createConfig: 写入账号 ID 与默认值后持久化")
    void createConfig_success() throws ScrmException {
        ScrmRfmConfigDto dto = new ScrmRfmConfigDto();
        dto.setConfigName("默认 RFM 配置");
        dto.setRWeight(0.3);
        dto.setFWeight(0.3);
        dto.setMWeight(0.4);
        when(configRepository.save(any(ScrmRfmConfigEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmRfmConfigDto result = service.createConfig(dto);

        ArgumentCaptor<ScrmRfmConfigEntity> captor =
                ArgumentCaptor.forClass(ScrmRfmConfigEntity.class);
        verify(configRepository, times(1)).save(captor.capture());
        ScrmRfmConfigEntity saved = captor.getValue();
        // recencySource 缺省时填 LAST_INTERACTION
        assertThat(saved.getRecencySource()).isEqualTo("LAST_INTERACTION");
        // monetarySource 缺省时填 TOTAL_SPENT
        assertThat(saved.getMonetarySource()).isEqualTo("TOTAL_SPENT");
        // isDefault 缺省时填 false
        assertThat(saved.getIsDefault()).isFalse();
        // enabled 缺省时填 true
        assertThat(saved.getEnabled()).isTrue();
        // createdBy 缺省时填 scrm-system
        assertThat(saved.getCreatedBy()).isEqualTo("scrm-system");
        assertThat(result.getConfigName()).isEqualTo("默认 RFM 配置");
    }

    @Test
    @DisplayName("createConfig: 同账号已存在默认配置时抛 CONFLICT")
    void createConfig_defaultConflict() {
        ScrmRfmConfigDto dto = new ScrmRfmConfigDto();
        dto.setConfigName("默认 RFM 配置");
        dto.setRWeight(0.3);
        dto.setFWeight(0.3);
        dto.setMWeight(0.4);
        dto.setIsDefault(true);
        when(configRepository.countByIsDefaultTrue()).thenReturn(1L);

        assertThatThrownBy(() -> service.createConfig(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("同账号下已存在默认 RFM 配置");
        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("createConfig: 权重为负抛 BAD_REQUEST")
    void createConfig_negativeWeight() {
        ScrmRfmConfigDto dto = new ScrmRfmConfigDto();
        dto.setConfigName("默认 RFM 配置");
        dto.setRWeight(-0.1);
        dto.setFWeight(0.3);
        dto.setMWeight(0.4);

        assertThatThrownBy(() -> service.createConfig(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("R/F/M 权重不能为负数");
        verify(configRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("setDefaultConfig: 清理旧默认并设置新默认")
    void setDefaultConfig_success() throws ScrmException {
        ScrmRfmConfigEntity entity = buildConfigEntity(10L);
        entity.setIsDefault(false);
        ScrmRfmConfigEntity oldDefault = buildConfigEntity(9L);
        oldDefault.setIsDefault(true);
        when(configRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(configRepository.findByIsDefaultTrue()).thenReturn(Optional.of(oldDefault));
        when(configRepository.save(any(ScrmRfmConfigEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.setDefaultConfig(10L);

        // 旧默认取消 + 新默认写入, 共 save 两次
        ArgumentCaptor<ScrmRfmConfigEntity> captor =
                ArgumentCaptor.forClass(ScrmRfmConfigEntity.class);
        verify(configRepository, times(2)).save(captor.capture());
        // 第一次保存旧默认 (isDefault=false), 第二次保存新默认 (isDefault=true)
        assertThat(captor.getAllValues().get(0).getIsDefault()).isFalse();
        assertThat(captor.getAllValues().get(1).getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("calculate: 计算 R/F/M 评分与分群编码并持久化分析结果")
    void calculate_success() throws ScrmException {
        ScrmRfmConfigEntity config = buildConfigEntity(10L);
        when(configRepository.findById(10L)).thenReturn(Optional.of(config));
        // 客户最近互动 5 天前 → recencyDays=5 → R 评分 5 (5 <= 30/4)
        ScrmCustomerEntity customer = buildCustomerEntity(100L, LocalDateTime.now().minusDays(5));
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        // 2 条本账号会话 → frequency=2 → F 评分 1 (2 不大于 5)
        when(conversationRepository.findByCustomerId(100L))
                .thenReturn(List.of(
                        buildConversationEntity(1L),
                        buildConversationEntity(2L)));
        // monetary=0 → M 评分 1
        when(analysisRepository.deleteByCustomerId(100L)).thenReturn(0L);
        when(analysisRepository.save(any(ScrmRfmAnalysisEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(configRepository.save(any(ScrmRfmConfigEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmRfmAnalysisDto result = service.calculate(10L, 100L);

        ArgumentCaptor<ScrmRfmAnalysisEntity> captor =
                ArgumentCaptor.forClass(ScrmRfmAnalysisEntity.class);
        verify(analysisRepository, times(1)).save(captor.capture());
        ScrmRfmAnalysisEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(100L);
        assertThat(saved.getCustomerName()).isEqualTo("张三");
        assertThat(saved.getRecencyDays()).isEqualTo(5);
        assertThat(saved.getFrequency()).isEqualTo(2);
        assertThat(saved.getMonetary()).isEqualTo(0d);
        // R=5 (high), F=1 (low), M=1 (low) → segment "100" → 重要挽留客户 → AT_RISK
        assertThat(saved.getRScore()).isEqualTo(5);
        assertThat(saved.getFScore()).isEqualTo(1);
        assertThat(saved.getMScore()).isEqualTo(1);
        assertThat(saved.getRfmSegment()).isEqualTo("100");
        assertThat(saved.getSegmentName()).isEqualTo("重要挽留客户");
        assertThat(saved.getSegmentCategory()).isEqualTo("AT_RISK");
        // valueScore = (5*0.3 + 1*0.3 + 1*0.4) / 1.0 / 5 * 100 = 2.2/5*100 = 44.0
        assertThat(saved.getValueScore()).isEqualTo(44.0);
        assertThat(result.getConfigId()).isEqualTo(10L);
        // 同步更新配置的最近计算时间
        verify(configRepository, times(1)).save(any(ScrmRfmConfigEntity.class));
    }

    @Test
    @DisplayName("calculateBatch: 跳过不存在的客户, 仅返回成功计算的结果")
    void calculateBatch_skipMissing() throws ScrmException {
        ScrmRfmConfigEntity config = buildConfigEntity(10L);
        when(configRepository.findById(10L)).thenReturn(Optional.of(config));
        // 第一个客户存在, 第二个客户不存在
        ScrmCustomerEntity customer = buildCustomerEntity(100L, LocalDateTime.now().minusDays(5));
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(customerRepository.findById(200L)).thenReturn(Optional.empty());
        when(conversationRepository.findByCustomerId(100L)).thenReturn(Collections.emptyList());
        when(analysisRepository.deleteByCustomerId(eq(100L))).thenReturn(0L);
        when(analysisRepository.save(any(ScrmRfmAnalysisEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(configRepository.save(any(ScrmRfmConfigEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmRfmCalculateDto dto = new ScrmRfmCalculateDto();
        dto.setConfigId(10L);
        dto.setCustomerIds(List.of(100L, 200L));
        List<ScrmRfmAnalysisDto> results = service.calculateBatch(dto);

        // 200L 客户不存在被跳过, 仅 100L 成功
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCustomerId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getAnalysis: 分析结果不存在抛 NOT_FOUND")
    void getAnalysis_notFound() {
        when(analysisRepository.findFirstByCustomerIdOrderByCalculatedAtDesc(100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAnalysis(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("RFM 分析结果不存在");
    }

    @Test
    @DisplayName("listAnalysis: 通过 Specification 分页查询并按 valueScore 倒序")
    void listAnalysis_pagination() {
        ScrmRfmAnalysisEntity entity = buildAnalysisEntityForList(20L, 100L, "111");
        when(analysisRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<ScrmRfmAnalysisDto> result = service.listAnalysis("CHAMPION", null, null, null,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCustomerId()).isEqualTo(100L);
        // 验证分页参数携带 valueScore DESC 排序
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(analysisRepository, times(1)).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort().toString()).contains("valueScore: DESC");
    }

    @Test
    @DisplayName("getSegmentDistribution: 聚合分群分布并计算占比")
    void getSegmentDistribution_success() {
        // Object[]{segmentCategory, count, avgValueScore}
        when(analysisRepository.segmentDistribution()).thenReturn(List.of(
                new Object[]{"CHAMPION", 4L, 90.0d},
                new Object[]{"AT_RISK", 6L, 30.0d}));

        List<Map<String, Object>> result = service.getSegmentDistribution();

        assertThat(result).hasSize(2);
        // total = 4 + 6 = 10
        Map<String, Object> champion = result.get(0);
        assertThat(champion.get("segmentCategory")).isEqualTo("CHAMPION");
        assertThat(champion.get("count")).isEqualTo(4L);
        // 4/10*100 = 40.0
        assertThat(champion.get("percentage")).isEqualTo(40.0);
        assertThat(champion.get("avgValueScore")).isEqualTo(90.0);
        Map<String, Object> atRisk = result.get(1);
        assertThat(atRisk.get("count")).isEqualTo(6L);
        assertThat(atRisk.get("percentage")).isEqualTo(60.0);
    }

    @Test
    @DisplayName("calculateRScore: 阈值分界映射 R 评分 (5/4/3/2/1)")
    void calculateRScore_thresholds() {
        // threshold=30: ≤7.5→5, ≤15→4, ≤30→3, ≤60→2, 否则→1
        assertThat(service.calculateRScore(5, 30)).isEqualTo(5);
        assertThat(service.calculateRScore(15, 30)).isEqualTo(4);
        assertThat(service.calculateRScore(20, 30)).isEqualTo(3);
        assertThat(service.calculateRScore(50, 30)).isEqualTo(2);
        assertThat(service.calculateRScore(100, 30)).isEqualTo(1);
        // threshold<=0 返回 SCORE_THRESHOLD (3)
        assertThat(service.calculateRScore(5, 0)).isEqualTo(3);
    }

    @Test
    @DisplayName("calculateFScore: 阈值分界映射 F 评分 (5/4/3/2/1)")
    void calculateFScore_thresholds() {
        // threshold=10: >20→5, >15→4, >10→3, >5→2, 否则→1
        assertThat(service.calculateFScore(25, 10)).isEqualTo(5);
        assertThat(service.calculateFScore(16, 10)).isEqualTo(4);
        assertThat(service.calculateFScore(12, 10)).isEqualTo(3);
        assertThat(service.calculateFScore(6, 10)).isEqualTo(2);
        assertThat(service.calculateFScore(3, 10)).isEqualTo(1);
        // threshold<=0: frequency>0 → 5, 否则 → 1
        assertThat(service.calculateFScore(5, 0)).isEqualTo(5);
        assertThat(service.calculateFScore(0, 0)).isEqualTo(1);
    }

    @Test
    @DisplayName("determineSegment: 以 3 为分界生成 3 位 RFM 编码")
    void determineSegment_encoding() {
        assertThat(service.determineSegment(5, 4, 3)).isEqualTo("111");
        assertThat(service.determineSegment(2, 4, 3)).isEqualTo("011");
        assertThat(service.determineSegment(3, 3, 3)).isEqualTo("111");
        assertThat(service.determineSegment(2, 2, 2)).isEqualTo("000");
    }

    @Test
    @DisplayName("calculateValueScore: 加权评分归一化为 0-100")
    void calculateValueScore_weighted() {
        // 全 5 分 → 100
        assertThat(service.calculateValueScore(5, 5, 5, 0.3, 0.3, 0.4)).isEqualTo(100.0);
        // 全 1 分 → 20
        assertThat(service.calculateValueScore(1, 1, 1, 0.3, 0.3, 0.4)).isEqualTo(20.0);
        // 权重和为 0 返回 0
        assertThat(service.calculateValueScore(5, 5, 5, 0d, 0d, 0d)).isEqualTo(0d);
    }

    @Test
    @DisplayName("createStrategy: 写入账号 ID 与默认值后持久化")
    void createStrategy_success() throws ScrmException {
        ScrmRfmSegmentStrategyDto dto = new ScrmRfmSegmentStrategyDto();
        dto.setStrategyName("冠军客户策略");
        dto.setSegmentCategory("CHAMPION");
        dto.setStrategyType("RETENTION");
        dto.setActions("提供专属客服与定制化推荐");
        when(strategyRepository.save(any(ScrmRfmSegmentStrategyEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmRfmSegmentStrategyDto result = service.createStrategy(dto);

        ArgumentCaptor<ScrmRfmSegmentStrategyEntity> captor =
                ArgumentCaptor.forClass(ScrmRfmSegmentStrategyEntity.class);
        verify(strategyRepository, times(1)).save(captor.capture());
        ScrmRfmSegmentStrategyEntity saved = captor.getValue();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getPriority()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo("scrm-system");
        assertThat(result.getStrategyName()).isEqualTo("冠军客户策略");
    }

    @Test
    @DisplayName("getStrategiesForSegment: segmentCode 为空策略通用, 非空精确匹配")
    void getStrategiesForSegment_matching() {
        ScrmRfmSegmentStrategyEntity general = buildStrategyEntity(10L, "CHAMPION", null);
        ScrmRfmSegmentStrategyEntity specific = buildStrategyEntity(11L, "CHAMPION", "111");
        ScrmRfmSegmentStrategyEntity otherCode = buildStrategyEntity(12L, "CHAMPION", "110");
        when(strategyRepository.findBySegmentCategoryAndEnabledTrueOrderByPriorityDesc("CHAMPION"))
                .thenReturn(List.of(general, specific, otherCode));

        List<ScrmRfmSegmentStrategyDto> result = service.getStrategiesForSegment("CHAMPION", "111");

        // 通用策略 (segmentCode=null) + 精确匹配 segmentCode=111, 排除 110
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ScrmRfmSegmentStrategyDto::getId)
                .containsExactlyInAnyOrder(10L, 11L);
    }

    
    /**
     * 构造已持久化的分析结果实体 (用于 listAnalysis 返回)
     */
    private ScrmRfmAnalysisEntity buildAnalysisEntityForList(Long id, Long customerId, String segment) {
        ScrmRfmAnalysisEntity entity = new ScrmRfmAnalysisEntity();
        entity.setId(id);
        entity.setCustomerId(customerId);
        entity.setCustomerName("张三");
        entity.setConfigId(10L);
        entity.setRfmSegment(segment);
        entity.setValueScore(80.0);
        return entity;
    }

    /**
     * 构造已持久化的分群策略实体 (用于 findBySegmentCategoryAndEnabledTrueOrderByPriorityDesc 返回)
     */
    private ScrmRfmSegmentStrategyEntity buildStrategyEntity(Long id, String category, String segmentCode) {
        ScrmRfmSegmentStrategyEntity entity = new ScrmRfmSegmentStrategyEntity();
        entity.setId(id);
        entity.setStrategyName("策略 " + id);
        entity.setSegmentCategory(category);
        entity.setSegmentCode(segmentCode);
        entity.setStrategyType("RETENTION");
        entity.setActions("动作");
        entity.setEnabled(true);
        entity.setPriority(0);
        return entity;
    }
}
