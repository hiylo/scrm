/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingCampaignServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmCampaignLaunchDto;
import org.hiylo.scrm.dto.ScrmMarketingCampaignChannelDto;
import org.hiylo.scrm.dto.ScrmMarketingCampaignDto;
import org.hiylo.scrm.dto.ScrmMarketingCampaignParticipantDto;
import org.hiylo.scrm.entity.ScrmMarketingCampaignChannelEntity;
import org.hiylo.scrm.entity.ScrmMarketingCampaignEntity;
import org.hiylo.scrm.entity.ScrmMarketingCampaignParticipantEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmMarketingCampaignChannelRepository;
import org.hiylo.scrm.repository.ScrmMarketingCampaignParticipantRepository;
import org.hiylo.scrm.repository.ScrmMarketingCampaignRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
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
 * ScrmMarketingCampaignService 单元测试
 * <p>
 * 聚焦营销活动生命周期管理 (创建 / 更新 / 排期 / 启动 / 暂停 / 完成 / 取消 / 复制)、
 * 渠道管理 (新增 / 唯一性校验 / 启动发送漏斗)、参与者记录与转化、ROI 计算、
 * 参数校验与越权隔离等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmMarketingCampaignService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmMarketingCampaignServiceTest {

    /** 营销活动仓库 Mock */
    @Mock
    private ScrmMarketingCampaignRepository campaignRepository;
    /** 营销活动渠道仓库 Mock */
    @Mock
    private ScrmMarketingCampaignChannelRepository channelRepository;
    /** 营销活动参与者仓库 Mock */
    @Mock
    private ScrmMarketingCampaignParticipantRepository participantRepository;

    /** ObjectMapper 使用真实实例, 不 mock (遵循约束) */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测服务实例 */
    private ScrmMarketingCampaignService service;

    @BeforeEach
    void setUp() {
        service = new ScrmMarketingCampaignService(campaignRepository, channelRepository,
                participantRepository, objectMapper);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的营销活动实体 (用于 findById 返回)
     */
    private ScrmMarketingCampaignEntity buildCampaignEntity(Long id, String status) {
        ScrmMarketingCampaignEntity entity = new ScrmMarketingCampaignEntity();
        entity.setId(id);
        entity.setCampaignName("双十一大促");
        entity.setCampaignType("PROMOTION");
        entity.setChannels("WECHAT,SMS");
        entity.setStartDate(LocalDate.of(2026, 11, 1));
        entity.setEndDate(LocalDate.of(2026, 11, 11));
        entity.setBudget(10000d);
        entity.setActualCost(0d);
        entity.setStatus(status);
        entity.setPriority(0);
        return entity;
    }

    /**
     * 构造创建活动参数 DTO
     */
    private ScrmMarketingCampaignDto buildCreateDto() {
        ScrmMarketingCampaignDto dto = new ScrmMarketingCampaignDto();
        dto.setCampaignName("双十一大促");
        dto.setCampaignType("PROMOTION");
        dto.setChannels("WECHAT,SMS");
        dto.setStartDate(LocalDate.of(2026, 11, 1));
        dto.setEndDate(LocalDate.of(2026, 11, 11));
        dto.setBudget(10000d);
        return dto;
    }

    @Test
    @DisplayName("createCampaign: 写入账号 ID 与默认值后持久化")
    void createCampaign_success() throws ScrmException {
        ScrmMarketingCampaignDto dto = buildCreateDto();
        when(campaignRepository.save(any(ScrmMarketingCampaignEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMarketingCampaignEntity result = service.createCampaign(dto);

        ArgumentCaptor<ScrmMarketingCampaignEntity> captor =
                ArgumentCaptor.forClass(ScrmMarketingCampaignEntity.class);
        verify(campaignRepository, times(1)).save(captor.capture());
        ScrmMarketingCampaignEntity saved = captor.getValue();
        // actualCost 初值为 0
        assertThat(saved.getActualCost()).isEqualTo(0.0);
        // status 初值为 DRAFT
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        // priority 缺省时填 0
        assertThat(saved.getPriority()).isZero();
        // channels 规范化 (去空白去重, 保持顺序)
        assertThat(saved.getChannels()).isEqualTo("WECHAT,SMS");
        assertThat(result.getCampaignName()).isEqualTo("双十一大促");
    }

    @Test
    @DisplayName("createCampaign: 活动类型非法抛 BAD_REQUEST")
    void createCampaign_invalidType() {
        ScrmMarketingCampaignDto dto = buildCreateDto();
        dto.setCampaignType("INVALID_TYPE");

        assertThatThrownBy(() -> service.createCampaign(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("活动类型非法");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCampaign: 渠道非法抛 BAD_REQUEST")
    void createCampaign_invalidChannel() {
        ScrmMarketingCampaignDto dto = buildCreateDto();
        dto.setChannels("WECHAT,INVALID_CHANNEL");

        assertThatThrownBy(() -> service.createCampaign(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("渠道非法");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCampaign: 开始日期晚于结束日期抛 BAD_REQUEST")
    void createCampaign_invalidDateRange() {
        ScrmMarketingCampaignDto dto = buildCreateDto();
        dto.setStartDate(LocalDate.of(2026, 12, 1));
        dto.setEndDate(LocalDate.of(2026, 11, 1));

        assertThatThrownBy(() -> service.createCampaign(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("开始日期不能晚于结束日期");
        verify(campaignRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("getCampaign: 不存在抛 NOT_FOUND")
    void getCampaign_notFound() {
        when(campaignRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCampaign(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("营销活动不存在");
    }

    @Test
    @DisplayName("scheduleCampaign: DRAFT → SCHEDULED")
    void scheduleCampaign_success() throws ScrmException {
        ScrmMarketingCampaignEntity entity = buildCampaignEntity(10L, "DRAFT");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(campaignRepository.save(any(ScrmMarketingCampaignEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMarketingCampaignEntity result = service.scheduleCampaign(10L, LocalDateTime.now());

        ArgumentCaptor<ScrmMarketingCampaignEntity> captor =
                ArgumentCaptor.forClass(ScrmMarketingCampaignEntity.class);
        verify(campaignRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SCHEDULED");
        assertThat(result.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("scheduleCampaign: RUNNING 状态不可排期抛 CONFLICT")
    void scheduleCampaign_invalidStatus() {
        ScrmMarketingCampaignEntity entity = buildCampaignEntity(10L, "RUNNING");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.scheduleCampaign(10L, LocalDateTime.now()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 DRAFT / SCHEDULED 状态可排期");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("startCampaign: SCHEDULED → RUNNING")
    void startCampaign_success() throws ScrmException {
        ScrmMarketingCampaignEntity entity = buildCampaignEntity(10L, "SCHEDULED");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(campaignRepository.save(any(ScrmMarketingCampaignEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.startCampaign(10L);

        ArgumentCaptor<ScrmMarketingCampaignEntity> captor =
                ArgumentCaptor.forClass(ScrmMarketingCampaignEntity.class);
        verify(campaignRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("pauseCampaign: RUNNING → PAUSED")
    void pauseCampaign_success() throws ScrmException {
        ScrmMarketingCampaignEntity entity = buildCampaignEntity(10L, "RUNNING");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(campaignRepository.save(any(ScrmMarketingCampaignEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.pauseCampaign(10L);

        ArgumentCaptor<ScrmMarketingCampaignEntity> captor =
                ArgumentCaptor.forClass(ScrmMarketingCampaignEntity.class);
        verify(campaignRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("pauseCampaign: 非 RUNNING 状态不可暂停抛 CONFLICT")
    void pauseCampaign_invalidStatus() {
        ScrmMarketingCampaignEntity entity = buildCampaignEntity(10L, "DRAFT");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.pauseCampaign(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 RUNNING 状态可暂停");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("completeCampaign: RUNNING → COMPLETED 并刷新 metricsJson")
    void completeCampaign_success() throws ScrmException {
        ScrmMarketingCampaignEntity entity = buildCampaignEntity(10L, "RUNNING");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(entity));
        // completeCampaign 调用 refreshMetricsJson → getCampaignMetrics → 查询渠道与转化
        when(channelRepository.findByCampaignIdOrderByChannelAsc(10L))
                .thenReturn(Collections.emptyList());
        when(participantRepository.sumConversionByCampaign(10L))
                .thenReturn(new Object[]{0d, 0L});
        when(campaignRepository.save(any(ScrmMarketingCampaignEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.completeCampaign(10L);

        ArgumentCaptor<ScrmMarketingCampaignEntity> captor =
                ArgumentCaptor.forClass(ScrmMarketingCampaignEntity.class);
        verify(campaignRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
        // metricsJson 被刷新 (非空)
        assertThat(captor.getValue().getMetricsJson()).isNotBlank();
    }

    @Test
    @DisplayName("cancelCampaign: DRAFT → CANCELLED")
    void cancelCampaign_success() throws ScrmException {
        ScrmMarketingCampaignEntity entity = buildCampaignEntity(10L, "DRAFT");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(campaignRepository.save(any(ScrmMarketingCampaignEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.cancelCampaign(10L);

        ArgumentCaptor<ScrmMarketingCampaignEntity> captor =
                ArgumentCaptor.forClass(ScrmMarketingCampaignEntity.class);
        verify(campaignRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("cancelCampaign: COMPLETED 状态不可取消抛 CONFLICT")
    void cancelCampaign_completedConflict() {
        ScrmMarketingCampaignEntity entity = buildCampaignEntity(10L, "COMPLETED");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.cancelCampaign(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已完成 / 已取消的活动不可取消");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("addChannel: 写入账号 ID 与默认计数后持久化, 并追加活动 channels 字段")
    void addChannel_success() throws ScrmException {
        ScrmMarketingCampaignEntity campaign = buildCampaignEntity(10L, "DRAFT");
        campaign.setChannels("WECHAT");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        when(channelRepository.findByCampaignIdAndChannel(10L, "SMS"))
                .thenReturn(Optional.empty());
        when(channelRepository.save(any(ScrmMarketingCampaignChannelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepository.save(any(ScrmMarketingCampaignEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMarketingCampaignChannelDto dto = new ScrmMarketingCampaignChannelDto();
        dto.setChannel("SMS");
        dto.setTargetCount(100);
        dto.setCost(50.0);
        ScrmMarketingCampaignChannelEntity result = service.addChannel(10L, dto);

        ArgumentCaptor<ScrmMarketingCampaignChannelEntity> captor =
                ArgumentCaptor.forClass(ScrmMarketingCampaignChannelEntity.class);
        verify(channelRepository, times(1)).save(captor.capture());
        ScrmMarketingCampaignChannelEntity saved = captor.getValue();
        assertThat(saved.getCampaignId()).isEqualTo(10L);
        // 计数初值为 0
        assertThat(saved.getSentCount()).isZero();
        assertThat(saved.getDeliveredCount()).isZero();
        assertThat(saved.getConvertCount()).isZero();
        // status 初值为 PENDING
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(result.getChannel()).isEqualTo("SMS");
        // 活动channels字段被追加 SMS
        verify(campaignRepository, times(1)).save(any(ScrmMarketingCampaignEntity.class));
        assertThat(campaign.getChannels()).contains("SMS");
    }

    @Test
    @DisplayName("addChannel: 渠道已存在抛 CONFLICT")
    void addChannel_duplicateConflict() {
        ScrmMarketingCampaignEntity campaign = buildCampaignEntity(10L, "DRAFT");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        ScrmMarketingCampaignChannelEntity existing = new ScrmMarketingCampaignChannelEntity();
        existing.setChannel("SMS");
        when(channelRepository.findByCampaignIdAndChannel(10L, "SMS"))
                .thenReturn(Optional.of(existing));

        ScrmMarketingCampaignChannelDto dto = new ScrmMarketingCampaignChannelDto();
        dto.setChannel("SMS");
        assertThatThrownBy(() -> service.addChannel(10L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("活动下渠道已存在");
        verify(channelRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordParticipant: 写入账号 ID 与默认转化值后持久化")
    void recordParticipant_success() throws ScrmException {
        ScrmMarketingCampaignEntity campaign = buildCampaignEntity(10L, "RUNNING");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        when(participantRepository.save(any(ScrmMarketingCampaignParticipantEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMarketingCampaignParticipantDto dto = new ScrmMarketingCampaignParticipantDto();
        dto.setCampaignId(10L);
        dto.setCustomerId(100L);
        dto.setCustomerName("张三");
        dto.setChannel("WECHAT");
        dto.setConverted(true);
        dto.setConversionValue(199.0);
        ScrmMarketingCampaignParticipantEntity result = service.recordParticipant(dto);

        ArgumentCaptor<ScrmMarketingCampaignParticipantEntity> captor =
                ArgumentCaptor.forClass(ScrmMarketingCampaignParticipantEntity.class);
        verify(participantRepository, times(1)).save(captor.capture());
        ScrmMarketingCampaignParticipantEntity saved = captor.getValue();
        assertThat(saved.getCampaignId()).isEqualTo(10L);
        // converted=true 时 convertedAt 被自动填充
        assertThat(saved.getConverted()).isTrue();
        assertThat(saved.getConvertedAt()).isNotNull();
        assertThat(result.getChannel()).isEqualTo("WECHAT");
    }

    @Test
    @DisplayName("recordParticipant: 渠道非法抛 BAD_REQUEST")
    void recordParticipant_invalidChannel() {
        ScrmMarketingCampaignEntity campaign = buildCampaignEntity(10L, "RUNNING");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));

        ScrmMarketingCampaignParticipantDto dto = new ScrmMarketingCampaignParticipantDto();
        dto.setCampaignId(10L);
        dto.setCustomerId(100L);
        dto.setChannel("INVALID_CHANNEL");
        assertThatThrownBy(() -> service.recordParticipant(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("渠道非法");
        verify(participantRepository, never()).save(any());
    }

    @Test
    @DisplayName("launchChannels: 启动全部 PENDING 渠道, 模拟发送漏斗并累加活动实际花费")
    void launchChannels_allPending() throws ScrmException {
        ScrmMarketingCampaignEntity campaign = buildCampaignEntity(10L, "RUNNING");
        campaign.setActualCost(0d);
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        ScrmMarketingCampaignChannelEntity ch = new ScrmMarketingCampaignChannelEntity();
        ch.setId(20L);
        ch.setCampaignId(10L);
        ch.setChannel("SMS");
        ch.setTargetCount(100);
        ch.setCost(80.0);
        ch.setStatus("PENDING");
        when(channelRepository.findByCampaignIdAndStatus(10L, "PENDING"))
                .thenReturn(List.of(ch));
        when(channelRepository.save(any(ScrmMarketingCampaignChannelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(campaignRepository.save(any(ScrmMarketingCampaignEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCampaignLaunchDto launchDto = new ScrmCampaignLaunchDto();
        launchDto.setCampaignId(10L);
        List<Map<String, Object>> results = service.launchChannels(launchDto);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("status")).isEqualTo("SENT");
        // 模拟漏斗: 送达 95, 已读 60, 点击 20, 转化 5
        ArgumentCaptor<ScrmMarketingCampaignChannelEntity> captor =
                ArgumentCaptor.forClass(ScrmMarketingCampaignChannelEntity.class);
        verify(channelRepository, times(1)).save(captor.capture());
        ScrmMarketingCampaignChannelEntity saved = captor.getValue();
        assertThat(saved.getSentCount()).isEqualTo(100);
        assertThat(saved.getDeliveredCount()).isEqualTo(95);
        assertThat(saved.getReadCount()).isEqualTo(60);
        assertThat(saved.getClickCount()).isEqualTo(20);
        assertThat(saved.getConvertCount()).isEqualTo(5);
        // 活动实际花费累加 80
        assertThat(campaign.getActualCost()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("launchChannels: campaignId 为空抛 BAD_REQUEST")
    void launchChannels_nullCampaignId() {
        ScrmCampaignLaunchDto launchDto = new ScrmCampaignLaunchDto();
        launchDto.setCampaignId(null);

        assertThatThrownBy(() -> service.launchChannels(launchDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("启动配置不能为空且需指定 campaignId");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    @DisplayName("listCampaigns: 通过 Specification 分页查询")
    void listCampaigns_pagination() {
        ScrmMarketingCampaignEntity entity = buildCampaignEntity(10L, "RUNNING");
        when(campaignRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(entity)));

        Page<ScrmMarketingCampaignEntity> result = service.listCampaigns("PROMOTION", "RUNNING",
                null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCampaignType()).isEqualTo("PROMOTION");
        verify(campaignRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("calculateROIInternal: 花费为 0 返回 0 避免除零")
    void calculateROIInternal_zeroCost() {
        Double roi = ReflectionTestUtils.invokeMethod(service, "calculateROIInternal", 1000.0, 0.0);
        assertThat(roi).isEqualTo(0.0);
    }

    @Test
    @DisplayName("calculateROIInternal: (转化金额 - 花费) / 花费")
    void calculateROIInternal_normalCase() {
        // (1500 - 1000) / 1000 = 0.5
        Double roi = ReflectionTestUtils.invokeMethod(service, "calculateROIInternal", 1500.0, 1000.0);
        assertThat(roi).isEqualTo(0.5);
    }

    @Test
    @DisplayName("normalizeChannels: 去空白去重保持顺序")
    void normalizeChannels_dedupAndTrim() {
        String result = ReflectionTestUtils.invokeMethod(service, "normalizeChannels",
                " WECHAT , SMS ,, WECHAT, ");
        assertThat(result).isEqualTo("WECHAT,SMS");
    }

    @Test
    @DisplayName("normalizeChannels: 空字符串原样返回")
    void normalizeChannels_blank() {
        String result = ReflectionTestUtils.invokeMethod(service, "normalizeChannels", "");
        assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("updateChannelMetrics: 增量累加渠道指标")
    void updateChannelMetrics_success() throws ScrmException {
        ScrmMarketingCampaignChannelEntity channel = new ScrmMarketingCampaignChannelEntity();
        channel.setId(20L);
        channel.setDeliveredCount(10);
        channel.setReadCount(5);
        channel.setClickCount(2);
        channel.setConvertCount(1);
        when(channelRepository.findById(20L)).thenReturn(Optional.of(channel));
        when(channelRepository.save(any(ScrmMarketingCampaignChannelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.updateChannelMetrics(20L, 5, 3, 1, 1);

        ArgumentCaptor<ScrmMarketingCampaignChannelEntity> captor =
                ArgumentCaptor.forClass(ScrmMarketingCampaignChannelEntity.class);
        verify(channelRepository, times(1)).save(captor.capture());
        ScrmMarketingCampaignChannelEntity saved = captor.getValue();
        assertThat(saved.getDeliveredCount()).isEqualTo(15);
        assertThat(saved.getReadCount()).isEqualTo(8);
        assertThat(saved.getClickCount()).isEqualTo(3);
        assertThat(saved.getConvertCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("getCampaignMetrics: 汇总渠道指标与转化金额, 计算 ROI")
    void getCampaignMetrics_success() throws ScrmException {
        ScrmMarketingCampaignEntity campaign = buildCampaignEntity(10L, "RUNNING");
        campaign.setActualCost(80d);
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        ScrmMarketingCampaignChannelEntity ch = new ScrmMarketingCampaignChannelEntity();
        ch.setId(20L);
        ch.setChannel("SMS");
        ch.setStatus("SENT");
        ch.setTargetCount(100);
        ch.setSentCount(100);
        ch.setDeliveredCount(95);
        ch.setReadCount(60);
        ch.setClickCount(20);
        ch.setConvertCount(5);
        ch.setCost(80.0);
        when(channelRepository.findByCampaignIdOrderByChannelAsc(10L))
                .thenReturn(List.of(ch));
        // 转化金额 800, 转化数 5
        when(participantRepository.sumConversionByCampaign(10L))
                .thenReturn(new Object[]{800d, 5L});

        Map<String, Object> metrics = service.getCampaignMetrics(10L);

        assertThat(metrics.get("campaignId")).isEqualTo(10L);
        assertThat(metrics.get("actualCost")).isEqualTo(80.0);
        assertThat(metrics.get("totalConversionValue")).isEqualTo(800.0);
        assertThat(metrics.get("conversionCount")).isEqualTo(5);
        // ROI = (800 - 80) / 80 = 9.0
        assertThat(metrics.get("roi")).isEqualTo(9.0);
        assertThat(metrics.get("totalSent")).isEqualTo(100);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> channelMetrics = (List<Map<String, Object>>) metrics.get("channelMetrics");
        assertThat(channelMetrics).hasSize(1);
        assertThat(channelMetrics.get(0).get("channel")).isEqualTo("SMS");
    }

    @Test
    @DisplayName("deleteCampaign: 级联清理渠道与参与者后删除活动")
    void deleteCampaign_success() throws ScrmException {
        ScrmMarketingCampaignEntity campaign = buildCampaignEntity(10L, "DRAFT");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(campaign));
        ScrmMarketingCampaignChannelEntity ch = new ScrmMarketingCampaignChannelEntity();
        ch.setId(20L);
        when(channelRepository.findByCampaignIdOrderByChannelAsc(10L))
                .thenReturn(List.of(ch));
        when(participantRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.emptyList());

        service.deleteCampaign(10L);

        verify(channelRepository, times(1)).deleteAll(List.of(ch));
        verify(campaignRepository, times(1)).delete(campaign);
    }

    @Test
    @DisplayName("copyCampaign: 复制活动为 DRAFT 状态并重置渠道计数")
    void copyCampaign_success() throws ScrmException {
        ScrmMarketingCampaignEntity source = buildCampaignEntity(10L, "COMPLETED");
        when(campaignRepository.findById(10L)).thenReturn(Optional.of(source));
        when(campaignRepository.save(any(ScrmMarketingCampaignEntity.class)))
                .thenAnswer(inv -> {
                    ScrmMarketingCampaignEntity e = inv.getArgument(0);
                    e.setId(11L);
                    return e;
                });
        ScrmMarketingCampaignChannelEntity srcChannel = new ScrmMarketingCampaignChannelEntity();
        srcChannel.setId(20L);
        srcChannel.setChannel("SMS");
        srcChannel.setTargetCount(100);
        srcChannel.setSentCount(80);
        srcChannel.setCost(50.0);
        srcChannel.setStatus("SENT");
        when(channelRepository.findByCampaignIdOrderByChannelAsc(10L))
                .thenReturn(List.of(srcChannel));
        when(channelRepository.save(any(ScrmMarketingCampaignChannelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMarketingCampaignEntity copy = service.copyCampaign(10L);

        assertThat(copy.getStatus()).isEqualTo("DRAFT");
        assertThat(copy.getCampaignName()).isEqualTo("双十一大促-副本");
        assertThat(copy.getActualCost()).isEqualTo(0.0);
        // 验证复制的渠道: 计数清零, 状态置 PENDING
        ArgumentCaptor<ScrmMarketingCampaignChannelEntity> captor =
                ArgumentCaptor.forClass(ScrmMarketingCampaignChannelEntity.class);
        verify(channelRepository, times(1)).save(captor.capture());
        ScrmMarketingCampaignChannelEntity copied = captor.getValue();
        assertThat(copied.getCampaignId()).isEqualTo(11L);
        assertThat(copied.getStatus()).isEqualTo("PENDING");
        assertThat(copied.getSentCount()).isZero();
        assertThat(copied.getTargetCount()).isEqualTo(100);
        assertThat(copied.getCost()).isEqualTo(50.0);
    }
}
