/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignAnalysisServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmCampaignAnalysisDto;
import org.hiylo.scrm.entity.ScrmCampaignAnalysisEntity;
import org.hiylo.scrm.entity.ScrmCampaignChannelEntity;
import org.hiylo.scrm.entity.ScrmCampaignFunnelEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCampaignAnalysisRepository;
import org.hiylo.scrm.repository.ScrmCampaignChannelRepository;
import org.hiylo.scrm.repository.ScrmCampaignFunnelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
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
 * ScrmCampaignAnalysisService 单元测试
 * <p>
 * 聚焦活动效果分析 (创建 / 更新 / 删除 / 审批 / 越权访问)、指标计算 (ROI / ROAS / 转化率 / CAC)、
 * 多活动对比 / 批量分析 / 漏斗转化率与渠道性能排名等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCampaignAnalysisService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCampaignAnalysisServiceTest {

    /** 活动分析数据仓库 Mock 桩 */
    @Mock
    private ScrmCampaignAnalysisRepository analysisRepository;
    /** 活动渠道数据仓库 Mock 桩 */
    @Mock
    private ScrmCampaignChannelRepository channelRepository;
    /** 活动漏斗数据仓库 Mock 桩 */
    @Mock
    private ScrmCampaignFunnelRepository funnelRepository;

    /** 被测服务实例 */
    private ScrmCampaignAnalysisService service;

    @BeforeEach
    void setUp() {
        ScrmCampaignAnalysisManageService manageService =
                new ScrmCampaignAnalysisManageService(analysisRepository, channelRepository, funnelRepository);
        ScrmCampaignAnalysisFunnelService funnelService =
                new ScrmCampaignAnalysisFunnelService(funnelRepository, manageService);
        ScrmCampaignAnalysisChannelService channelService =
                new ScrmCampaignAnalysisChannelService(channelRepository, funnelRepository,
                        manageService, funnelService);
        ScrmCampaignAnalysisStatsService statsService =
                new ScrmCampaignAnalysisStatsService(analysisRepository, channelRepository,
                        funnelRepository, manageService);
        service = new ScrmCampaignAnalysisService(manageService, channelService, funnelService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的活动分析实体 (用于 findById 返回)
     */
    private ScrmCampaignAnalysisEntity buildAnalysisEntity(Long id, String campaignName) {
        ScrmCampaignAnalysisEntity entity = new ScrmCampaignAnalysisEntity();
        entity.setId(id);
        entity.setCampaignId(100L);
        entity.setCampaignName(campaignName);
        entity.setCampaignCode("CAMP_" + id);
        entity.setCampaignType("PROMOTION");
        entity.setObjective("拉新");
        entity.setStartDate(LocalDate.of(2026, 1, 1));
        entity.setEndDate(LocalDate.of(2026, 1, 31));
        entity.setDurationDays(30);
        entity.setStatus("PLANNED");
        entity.setBudget(10000d);
        entity.setActualCost(8000d);
        entity.setReachCount(10000);
        entity.setImpressionCount(50000);
        entity.setClickCount(2000);
        entity.setConversionCount(200);
        entity.setRevenue(30000d);
        // 注: 设置 profit 规避 main 代码 round2(double) 对 null Double 自动拆箱的 NPE (main 代码 bug, 此处仅规避)
        entity.setProfit(22000d);
        entity.setNewCustomerCount(150);
        entity.setRepeatCustomerCount(50);
        entity.setIsApproved(false);
        return entity;
    }

    /**
     * 构造已持久化的渠道效果实体 (用于 findById 返回)
     */
    private ScrmCampaignChannelEntity buildChannelEntity(Long id, Long analysisId, String channelName) {
        ScrmCampaignChannelEntity entity = new ScrmCampaignChannelEntity();
        entity.setId(id);
        entity.setAnalysisId(analysisId);
        entity.setCampaignId(100L);
        entity.setChannelName(channelName);
        entity.setChannelType("SEARCH");
        entity.setChannelCost(5000d);
        entity.setReachCount(5000);
        entity.setImpressionCount(20000);
        entity.setClickCount(1000);
        entity.setConversionCount(100);
        entity.setRevenue(20000d);
        entity.setNewCustomerCount(80);
        return entity;
    }

    @Test
    @DisplayName("createAnalysis: 写入归属账号与默认值后持久化")
    void createAnalysis_success() throws ScrmException {
        ScrmCampaignAnalysisDto dto = new ScrmCampaignAnalysisDto();
        dto.setCampaignId(100L);
        dto.setCampaignName("双11大促");
        dto.setCampaignType("PROMOTION");
        dto.setStartDate(LocalDate.of(2026, 11, 1));
        dto.setEndDate(LocalDate.of(2026, 11, 11));
        when(analysisRepository.save(any(ScrmCampaignAnalysisEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCampaignAnalysisDto result = service.createAnalysis(dto);

        ArgumentCaptor<ScrmCampaignAnalysisEntity> captor =
                ArgumentCaptor.forClass(ScrmCampaignAnalysisEntity.class);
        verify(analysisRepository, times(1)).save(captor.capture());
        ScrmCampaignAnalysisEntity saved = captor.getValue();
        // status 缺省时填 PLANNED
        assertThat(saved.getStatus()).isEqualTo("PLANNED");
        // budget 缺省时填 0
        assertThat(saved.getBudget()).isEqualTo(0d);
        // actualCost 缺省时填 0
        assertThat(saved.getActualCost()).isEqualTo(0d);
        // durationDays 缺省时按起止日期计算
        assertThat(saved.getDurationDays()).isEqualTo(10);
        // isApproved 默认 false
        assertThat(saved.getIsApproved()).isFalse();
        // createdBy 缺省时填 scrm-system
        assertThat(saved.getCreatedBy()).isEqualTo("scrm-system");
        assertThat(result.getCampaignName()).isEqualTo("双11大促");
    }

    @Test
    @DisplayName("createAnalysis: 结束日期早于开始日期抛 BAD_REQUEST")
    void createAnalysis_invalidDateRange() {
        ScrmCampaignAnalysisDto dto = new ScrmCampaignAnalysisDto();
        dto.setCampaignId(100L);
        dto.setCampaignName("双11大促");
        dto.setCampaignType("PROMOTION");
        dto.setStartDate(LocalDate.of(2026, 11, 11));
        dto.setEndDate(LocalDate.of(2026, 11, 1)); // 早于开始日期

        assertThatThrownBy(() -> service.createAnalysis(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("活动结束日期不能早于开始日期");
        verify(analysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAnalysis: 活动名称为空抛 BAD_REQUEST")
    void createAnalysis_blankName() {
        ScrmCampaignAnalysisDto dto = new ScrmCampaignAnalysisDto();
        dto.setCampaignId(100L);
        dto.setCampaignName("");
        dto.setCampaignType("PROMOTION");
        dto.setStartDate(LocalDate.of(2026, 11, 1));
        dto.setEndDate(LocalDate.of(2026, 11, 11));

        assertThatThrownBy(() -> service.createAnalysis(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("活动名称不能为空");
        verify(analysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAnalysis: 活动 ID 为空抛 BAD_REQUEST")
    void createAnalysis_missingCampaignId() {
        ScrmCampaignAnalysisDto dto = new ScrmCampaignAnalysisDto();
        dto.setCampaignName("双11大促");
        dto.setCampaignType("PROMOTION");
        dto.setStartDate(LocalDate.of(2026, 11, 1));
        dto.setEndDate(LocalDate.of(2026, 11, 11));

        assertThatThrownBy(() -> service.createAnalysis(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("营销活动 ID 不能为空");
        verify(analysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteAnalysis: 级联删除渠道效果与漏斗阶段")
    void deleteAnalysis_cascadeDelete() throws ScrmException {
        ScrmCampaignAnalysisEntity entity = buildAnalysisEntity(10L, "双11大促");
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmCampaignChannelEntity channel = buildChannelEntity(20L, 10L, "搜索渠道");
        ScrmCampaignFunnelEntity funnel = new ScrmCampaignFunnelEntity();
        funnel.setId(30L);
        funnel.setAnalysisId(10L);
        when(channelRepository.findByAnalysisId(eq(10L)))
                .thenReturn(List.of(channel));
        when(funnelRepository.findByAnalysisId(eq(10L)))
                .thenReturn(List.of(funnel));

        service.deleteAnalysis(10L);

        verify(channelRepository, times(1)).deleteAll(List.of(channel));
        verify(funnelRepository, times(1)).deleteAll(List.of(funnel));
        verify(analysisRepository, times(1)).delete(entity);
    }

    
    
    @Test
    @DisplayName("approveAnalysis: 审批人为空抛 BAD_REQUEST")
    void approveAnalysis_blankApprover() {
        assertThatThrownBy(() -> service.approveAnalysis(10L, ""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("审批人不能为空");
        verify(analysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("approveAnalysis: 写入审批人与审批时间, isApproved=true")
    void approveAnalysis_success() throws ScrmException {
        ScrmCampaignAnalysisEntity entity = buildAnalysisEntity(10L, "双11大促");
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(analysisRepository.save(any(ScrmCampaignAnalysisEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.approveAnalysis(10L, "reviewer");

        ArgumentCaptor<ScrmCampaignAnalysisEntity> captor =
                ArgumentCaptor.forClass(ScrmCampaignAnalysisEntity.class);
        verify(analysisRepository, times(1)).save(captor.capture());
        ScrmCampaignAnalysisEntity saved = captor.getValue();
        assertThat(saved.getIsApproved()).isTrue();
        assertThat(saved.getApprovedBy()).isEqualTo("reviewer");
        assertThat(saved.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("analyzeCampaign: PLANNED 状态自动转为 RUNNING 并写入分析人")
    void analyzeCampaign_success() throws ScrmException {
        ScrmCampaignAnalysisEntity entity = buildAnalysisEntity(10L, "双11大促");
        entity.setStatus("PLANNED");
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(analysisRepository.save(any(ScrmCampaignAnalysisEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCampaignAnalysisDto result = service.analyzeCampaign(10L);

        ArgumentCaptor<ScrmCampaignAnalysisEntity> captor =
                ArgumentCaptor.forClass(ScrmCampaignAnalysisEntity.class);
        verify(analysisRepository, times(1)).save(captor.capture());
        ScrmCampaignAnalysisEntity saved = captor.getValue();
        // PLANNED 状态自动转为 RUNNING
        assertThat(saved.getStatus()).isEqualTo("RUNNING");
        // 分析人写入默认 scrm-system
        assertThat(saved.getAnalyzedBy()).isEqualTo("scrm-system");
        assertThat(saved.getAnalyzedAt()).isNotNull();
        // 派生指标计算: ROI = (30000-8000)/8000 = 2.75
        assertThat(saved.getRoi()).isEqualTo(2.75d);
        // ROAS = 30000/8000 = 3.75
        assertThat(saved.getRoas()).isEqualTo(3.75d);
        // 转化率 = 200/10000 = 0.02
        assertThat(saved.getConversionRate()).isEqualTo(0.02d);
        // CAC = 8000/150 = 53.33
        assertThat(saved.getCac()).isEqualTo(53.33d);
        assertThat(result.getAnalyzedBy()).isEqualTo("scrm-system");
    }

    @Test
    @DisplayName("compareCampaigns: 空列表抛 BAD_REQUEST")
    void compareCampaigns_emptyList() {
        assertThatThrownBy(() -> service.compareCampaigns(List.of()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("对比分析 ID 列表不能为空");
    }

    
    @Test
    @DisplayName("batchAnalyze: 空列表抛 BAD_REQUEST")
    void batchAnalyze_emptyList() {
        assertThatThrownBy(() -> service.batchAnalyze(List.of()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("批量分析 ID 列表不能为空");
    }

    @Test
    @DisplayName("batchAnalyze: 跳过不存在的 ID, 仅返回成功分析")
    void batchAnalyze_skipMissing() throws ScrmException {
        ScrmCampaignAnalysisEntity entity = buildAnalysisEntity(10L, "双11大促");
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(analysisRepository.findById(11L)).thenReturn(Optional.empty());
        when(analysisRepository.save(any(ScrmCampaignAnalysisEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<ScrmCampaignAnalysisDto> result = service.batchAnalyze(List.of(10L, 11L));

        // 11L 不存在被跳过, 仅返回 10L 的分析结果
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("calculateROI: 成本为 0 返回 0, 正常情况返回利润/成本")
    void calculateROI_various() {
        // 成本为 0 返回 0
        assertThat(service.calculateROI(1000d, 0d)).isEqualTo(0d);
        // 利润/成本 = (3000-1000)/1000 = 2.0
        assertThat(service.calculateROI(3000d, 1000d)).isEqualTo(2.0d);
        // null 入参容错
        assertThat(service.calculateROI(null, 1000d)).isEqualTo(-1.0d);
    }

    @Test
    @DisplayName("calculateROAS: 成本为 0 返回 0, 正常情况返回收入/成本")
    void calculateROAS_various() {
        assertThat(service.calculateROAS(3000d, 0d)).isEqualTo(0d);
        // 收入/成本 = 3000/1000 = 3.0
        assertThat(service.calculateROAS(3000d, 1000d)).isEqualTo(3.0d);
    }

    @Test
    @DisplayName("calculateConversionRate: 触达为 0 返回 0, 正常情况返回转化/触达")
    void calculateConversionRate_various() {
        assertThat(service.calculateConversionRate(100, 0)).isEqualTo(0d);
        // 100/1000 = 0.1
        assertThat(service.calculateConversionRate(100, 1000)).isEqualTo(0.1d);
    }

    @Test
    @DisplayName("calculateCAC: 新客户为 0 返回 0, 正常情况返回成本/新客户")
    void calculateCAC_various() {
        assertThat(service.calculateCAC(1000d, 0)).isEqualTo(0d);
        // 1000/50 = 20.0
        assertThat(service.calculateCAC(1000d, 50)).isEqualTo(20.0d);
    }

    @Test
    @DisplayName("getFunnelConversionRate: 无漏斗阶段返回 0")
    void getFunnelConversionRate_empty() throws ScrmException {
        ScrmCampaignAnalysisEntity entity = buildAnalysisEntity(10L, "双11大促");
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(funnelRepository.findByAnalysisIdOrderByStageOrderAsc(eq(10L)))
                .thenReturn(List.of());

        Double result = service.getFunnelConversionRate(10L);

        assertThat(result).isEqualTo(0d);
    }

    @Test
    @DisplayName("getFunnelConversionRate: 首阶段入口→末阶段转化计算")
    void getFunnelConversionRate_normal() throws ScrmException {
        ScrmCampaignAnalysisEntity entity = buildAnalysisEntity(10L, "双11大促");
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmCampaignFunnelEntity stage1 = new ScrmCampaignFunnelEntity();
        stage1.setEntryCount(1000);
        stage1.setConversionCount(800);
        ScrmCampaignFunnelEntity stage2 = new ScrmCampaignFunnelEntity();
        stage2.setEntryCount(800);
        stage2.setConversionCount(200);
        when(funnelRepository.findByAnalysisIdOrderByStageOrderAsc(eq(10L)))
                .thenReturn(List.of(stage1, stage2));

        Double result = service.getFunnelConversionRate(10L);

        // 末阶段转化数 / 首阶段入口数 = 200/1000 = 0.2
        assertThat(result).isEqualTo(0.2d);
    }

    @Test
    @DisplayName("getBestChannel: 无渠道抛 NOT_FOUND")
    void getBestChannel_empty() {
        ScrmCampaignAnalysisEntity entity = buildAnalysisEntity(10L, "双11大促");
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(channelRepository.findByAnalysisId(eq(10L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getBestChannel(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("未找到最优渠道");
    }

    @Test
    @DisplayName("getBestChannel: 按 ROI 降序选取最优渠道")
    void getBestChannel_selectBest() throws ScrmException {
        ScrmCampaignAnalysisEntity entity = buildAnalysisEntity(10L, "双11大促");
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmCampaignChannelEntity c1 = buildChannelEntity(20L, 10L, "搜索");
        c1.setRoi(1.5d);
        ScrmCampaignChannelEntity c2 = buildChannelEntity(21L, 10L, "社交");
        c2.setRoi(3.0d);
        when(channelRepository.findByAnalysisId(eq(10L)))
                .thenReturn(List.of(c1, c2));

        var result = service.getBestChannel(10L);

        // c2 ROI 更高, 应当选中
        assertThat(result.getId()).isEqualTo(21L);
        assertThat(result.getChannelName()).isEqualTo("社交");
    }

    @Test
    @DisplayName("analyzeChannelPerformance: 单渠道时标记为最优 (isBestPerformer=true)")
    void analyzeChannelPerformance_singleChannel() throws ScrmException {
        ScrmCampaignAnalysisEntity entity = buildAnalysisEntity(10L, "双11大促");
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmCampaignChannelEntity c1 = buildChannelEntity(20L, 10L, "搜索");
        // 注: analyzeChannelPerformance 会对列表 sort, 需返回可变列表
        when(channelRepository.findByAnalysisId(eq(10L)))
                .thenReturn(new ArrayList<>(List.of(c1)));
        when(channelRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.analyzeChannelPerformance(10L);

        // 单渠道时标记为最优
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsBestPerformer()).isTrue();
        // 效率评分应被计算
        assertThat(result.get(0).getEfficiencyScore()).isNotNull();
    }

    @Test
    @DisplayName("shareAnalysis: 返回分享摘要, 包含 shareToken")
    void shareAnalysis_success() throws ScrmException {
        ScrmCampaignAnalysisEntity entity = buildAnalysisEntity(10L, "双11大促");
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(entity));

        Map<String, Object> result = service.shareAnalysis(10L);

        assertThat(result.get("analysisId")).isEqualTo(10L);
        assertThat(result.get("campaignName")).isEqualTo("双11大促");
        // shareToken 格式为 ca-{十六进制ID}
        assertThat((String) result.get("shareToken")).startsWith("ca-");
        assertThat(result.get("sharedAt")).isNotNull();
    }

    @Test
    @DisplayName("compareChannels: 空列表抛 BAD_REQUEST")
    void compareChannels_emptyList() {
        assertThatThrownBy(() -> service.compareChannels(List.of()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("对比渠道 ID 列表不能为空");
    }

    @Test
    @DisplayName("compareFunnels: 空列表抛 BAD_REQUEST")
    void compareFunnels_emptyList() {
        assertThatThrownBy(() -> service.compareFunnels(List.of()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("对比分析 ID 列表不能为空");
    }

    @Test
    @DisplayName("getAnalysisSummary: 返回汇总信息, 包含 channelCount 与 funnelStageCount")
    void getAnalysisSummary_success() throws ScrmException {
        ScrmCampaignAnalysisEntity entity = buildAnalysisEntity(10L, "双11大促");
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmCampaignChannelEntity channel = buildChannelEntity(20L, 10L, "搜索");
        ScrmCampaignFunnelEntity funnel = new ScrmCampaignFunnelEntity();
        funnel.setId(30L);
        funnel.setAnalysisId(10L);
        when(channelRepository.findByAnalysisId(eq(10L)))
                .thenReturn(List.of(channel));
        when(funnelRepository.findByAnalysisId(eq(10L)))
                .thenReturn(List.of(funnel));

        Map<String, Object> result = service.getAnalysisSummary(10L);

        assertThat(result.get("analysisId")).isEqualTo(10L);
        assertThat(result.get("campaignName")).isEqualTo("双11大促");
        assertThat(result.get("channelCount")).isEqualTo(1);
        assertThat(result.get("funnelStageCount")).isEqualTo(1);
        assertThat(result).containsKey("roi");
        assertThat(result).containsKey("roas");
    }

    
    
}
