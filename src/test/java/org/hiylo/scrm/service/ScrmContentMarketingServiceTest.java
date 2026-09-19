/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContentMarketingServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmContentAssetDto;
import org.hiylo.scrm.dto.ScrmContentDto;
import org.hiylo.scrm.dto.ScrmContentPublishDto;
import org.hiylo.scrm.dto.ScrmContentReviewDto;
import org.hiylo.scrm.dto.ScrmContentScheduleDto;
import org.hiylo.scrm.entity.ScrmContentAssetEntity;
import org.hiylo.scrm.entity.ScrmContentChannelEntity;
import org.hiylo.scrm.entity.ScrmContentEntity;
import org.hiylo.scrm.entity.ScrmContentScheduleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmContentAssetRepository;
import org.hiylo.scrm.repository.ScrmContentChannelRepository;
import org.hiylo.scrm.repository.ScrmContentRepository;
import org.hiylo.scrm.repository.ScrmContentScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmContentMarketingService 单元测试
 * <p>
 * 聚焦内容营销全流程关键业务逻辑: 内容 CRUD / 状态流转 (DRAFT→PENDING_REVIEW→
 * APPROVED/REJECTED→SCHEDULED→PUBLISHED→ARCHIVED) / 多渠道立即发布与定时排期 /
 * 渠道状态流转 (PENDING→PUBLISHED→REMOVED) / 排期执行 (PENDING→EXECUTING→COMPLETED) /
 * 互动计数 / 素材库 / 越权访问 / 内容统计与效果追踪。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmContentMarketingService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmContentMarketingServiceTest {

    /** 内容数据仓库 Mock 桩 */
    @Mock
    private ScrmContentRepository contentRepository;
    /** 内容渠道数据仓库 Mock 桩 */
    @Mock
    private ScrmContentChannelRepository channelRepository;
    /** 内容排期数据仓库 Mock 桩 */
    @Mock
    private ScrmContentScheduleRepository scheduleRepository;
    /** 内容资产数据仓库 Mock 桩 */
    @Mock
    private ScrmContentAssetRepository assetRepository;

    /** 被测服务实例 */
    private ScrmContentMarketingService service;

    @BeforeEach
    void setUp() {
        UserContext.setUsername("tester");
        service = new ScrmContentMarketingService(contentRepository, channelRepository,
                scheduleRepository, assetRepository);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * 构造内容 DTO (ARTICLE 类型)
     */
    private ScrmContentDto buildContentDto() {
        ScrmContentDto dto = new ScrmContentDto();
        dto.setTitle("双 11 营销文案");
        dto.setContentType("ARTICLE");
        dto.setCategory("PROMOTION");
        dto.setSummary("大促预热");
        dto.setBodyContent("## 立即抢购");
        dto.setTags("大促,11月");
        return dto;
    }

    /**
     * 构造已持久化的内容实体
     */
    private ScrmContentEntity buildContentEntity(Long id, String status) {
        ScrmContentEntity entity = new ScrmContentEntity();
        entity.setId(id);
        entity.setTitle("双 11 营销文案");
        entity.setContentType("ARTICLE");
        entity.setCategory("PROMOTION");
        entity.setStatus(status);
        entity.setViewCount(0);
        entity.setLikeCount(0);
        entity.setShareCount(0);
        entity.setCommentCount(0);
        entity.setCollectCount(0);
        entity.setConversionCount(0);
        return entity;
    }

    /**
     * 构造渠道实体
     */
    private ScrmContentChannelEntity buildChannelEntity(Long id, Long contentId, String channel, String status) {
        ScrmContentChannelEntity entity = new ScrmContentChannelEntity();
        entity.setId(id);
        entity.setContentId(contentId);
        entity.setChannel(channel);
        entity.setStatus(status);
        entity.setViewCount(0);
        entity.setLikeCount(0);
        entity.setShareCount(0);
        entity.setCommentCount(0);
        entity.setConversionCount(0);
        return entity;
    }

    /**
     * 构造排期实体
     */
    private ScrmContentScheduleEntity buildScheduleEntity(Long id, Long contentId, String status) {
        ScrmContentScheduleEntity entity = new ScrmContentScheduleEntity();
        entity.setId(id);
        entity.setContentId(contentId);
        entity.setScheduleName("排期-双11");
        entity.setChannels("WECHAT_OFFICIAL");
        entity.setScheduledAt(LocalDateTime.now().plusDays(1));
        entity.setTimezone("Asia/Shanghai");
        entity.setRepeatType("NONE");
        entity.setStatus(status);
        return entity;
    }

    /**
     * 构造素材 DTO
     */
    private ScrmContentAssetDto buildAssetDto() {
        ScrmContentAssetDto dto = new ScrmContentAssetDto();
        dto.setAssetName("双 11 海报");
        dto.setAssetType("IMAGE");
        dto.setFileUrl("https://cdn.example.com/poster.png");
        dto.setFileType("PNG");
        return dto;
    }

    /**
     * 构造已持久化的素材实体
     */
    private ScrmContentAssetEntity buildAssetEntity(Long id) {
        ScrmContentAssetEntity entity = new ScrmContentAssetEntity();
        entity.setId(id);
        entity.setAssetName("双 11 海报");
        entity.setAssetType("IMAGE");
        entity.setFileUrl("https://cdn.example.com/poster.png");
        entity.setSourceType("UPLOAD");
        entity.setUsageCount(0);
        entity.setIsPublic(Boolean.FALSE);
        return entity;
    }

    // ==================== 内容管理 ====================

    @Test
    @DisplayName("createContent: 写入归属账号/默认 DRAFT/计数 0 并持久化")
    void createContent_success() throws ScrmException {
        ScrmContentDto dto = buildContentDto();
        when(contentRepository.save(any(ScrmContentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmContentEntity result = service.createContent(dto);

        ArgumentCaptor<ScrmContentEntity> captor =
                ArgumentCaptor.forClass(ScrmContentEntity.class);
        verify(contentRepository, times(1)).save(captor.capture());
        ScrmContentEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.getLikeCount()).isZero();
        assertThat(saved.getConversionCount()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo("tester");
        assertThat(result.getTitle()).isEqualTo("双 11 营销文案");
    }

    @Test
    @DisplayName("createContent: 标题为空抛 BAD_REQUEST")
    void createContent_blankTitle() {
        ScrmContentDto dto = buildContentDto();
        dto.setTitle(" ");
        assertThatThrownBy(() -> service.createContent(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("内容标题不能为空");
        verify(contentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createContent: 内容类型非法抛 BAD_REQUEST")
    void createContent_invalidType() {
        ScrmContentDto dto = buildContentDto();
        dto.setContentType("UNKNOWN");
        assertThatThrownBy(() -> service.createContent(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("内容类型非法");
        verify(contentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateContent: 已发布内容不可修改 (CONFLICT)")
    void updateContent_publishedRejects() {
        ScrmContentEntity entity = buildContentEntity(10L, "PUBLISHED");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateContent(10L, buildContentDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已发布 / 已归档内容不可修改");
        verify(contentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateContent: 部分更新仅覆盖非空字段")
    void updateContent_partial() throws ScrmException {
        ScrmContentEntity entity = buildContentEntity(10L, "DRAFT");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(contentRepository.save(any(ScrmContentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmContentDto dto = new ScrmContentDto();
        dto.setTitle("新标题");

        service.updateContent(10L, dto);

        ArgumentCaptor<ScrmContentEntity> captor =
                ArgumentCaptor.forClass(ScrmContentEntity.class);
        verify(contentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("新标题");
        // 未传字段保留原值
        assertThat(captor.getValue().getContentType()).isEqualTo("ARTICLE");
    }

    @Test
    @DisplayName("submitForReview: DRAFT → PENDING_REVIEW 并清空审核字段")
    void submitForReview_draftToPending() throws ScrmException {
        ScrmContentEntity entity = buildContentEntity(10L, "DRAFT");
        entity.setReviewComment("历史意见");
        entity.setReviewedAt(LocalDateTime.now().minusDays(1));
        when(contentRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(contentRepository.save(any(ScrmContentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.submitForReview(10L);

        ArgumentCaptor<ScrmContentEntity> captor =
                ArgumentCaptor.forClass(ScrmContentEntity.class);
        verify(contentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(captor.getValue().getReviewStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getReviewComment()).isNull();
        assertThat(captor.getValue().getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("submitForReview: 已发布状态提交审核抛 CONFLICT")
    void submitForReview_invalidStatus() {
        ScrmContentEntity entity = buildContentEntity(10L, "PUBLISHED");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.submitForReview(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 DRAFT / REJECTED 状态可提交审核");
        verify(contentRepository, never()).save(any());
    }

    @Test
    @DisplayName("reviewContent: 通过审核 → APPROVED 并记录审核人")
    void reviewContent_approve() throws ScrmException {
        ScrmContentEntity entity = buildContentEntity(10L, "PENDING_REVIEW");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(contentRepository.save(any(ScrmContentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmContentReviewDto reviewDto = new ScrmContentReviewDto();
        reviewDto.setContentId(10L);
        reviewDto.setApproved(Boolean.TRUE);
        reviewDto.setComment("内容优质");

        service.reviewContent(reviewDto);

        ArgumentCaptor<ScrmContentEntity> captor =
                ArgumentCaptor.forClass(ScrmContentEntity.class);
        verify(contentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("APPROVED");
        assertThat(captor.getValue().getReviewStatus()).isEqualTo("APPROVED");
        assertThat(captor.getValue().getReviewerId()).isEqualTo("tester");
        assertThat(captor.getValue().getReviewedAt()).isNotNull();
        assertThat(captor.getValue().getReviewComment()).isEqualTo("内容优质");
    }

    @Test
    @DisplayName("reviewContent: 驳回 → REJECTED")
    void reviewContent_reject() throws ScrmException {
        ScrmContentEntity entity = buildContentEntity(10L, "PENDING_REVIEW");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(contentRepository.save(any(ScrmContentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmContentReviewDto reviewDto = new ScrmContentReviewDto();
        reviewDto.setContentId(10L);
        reviewDto.setApproved(Boolean.FALSE);
        reviewDto.setComment("内容不合规");

        service.reviewContent(reviewDto);

        ArgumentCaptor<ScrmContentEntity> captor =
                ArgumentCaptor.forClass(ScrmContentEntity.class);
        verify(contentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("REJECTED");
        assertThat(captor.getValue().getReviewStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("reviewContent: 参数缺失抛 BAD_REQUEST")
    void reviewContent_missingParams() {
        ScrmContentReviewDto reviewDto = new ScrmContentReviewDto();
        reviewDto.setContentId(10L);
        // approved 为 null

        assertThatThrownBy(() -> service.reviewContent(reviewDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("审核参数不能为空");
        verify(contentRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("archiveContent: PUBLISHED → ARCHIVED")
    void archiveContent_publishedToArchived() throws ScrmException {
        ScrmContentEntity entity = buildContentEntity(10L, "PUBLISHED");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(contentRepository.save(any(ScrmContentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.archiveContent(10L);

        ArgumentCaptor<ScrmContentEntity> captor =
                ArgumentCaptor.forClass(ScrmContentEntity.class);
        verify(contentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("archiveContent: 非 PUBLISHED 状态归档抛 CONFLICT")
    void archiveContent_invalidStatus() {
        ScrmContentEntity entity = buildContentEntity(10L, "DRAFT");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.archiveContent(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PUBLISHED 状态可归档");
        verify(contentRepository, never()).save(any());
    }

    @Test
    @DisplayName("copyContent: 复制后状态置 DRAFT, 计数清零, 标题加 -副本")
    void copyContent_success() throws ScrmException {
        ScrmContentEntity source = buildContentEntity(10L, "PUBLISHED");
        source.setViewCount(999);
        source.setLikeCount(88);
        when(contentRepository.findById(10L)).thenReturn(Optional.of(source));
        when(contentRepository.save(any(ScrmContentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.copyContent(10L);

        ArgumentCaptor<ScrmContentEntity> captor =
                ArgumentCaptor.forClass(ScrmContentEntity.class);
        verify(contentRepository, times(1)).save(captor.capture());
        ScrmContentEntity copy = captor.getValue();
        assertThat(copy.getStatus()).isEqualTo("DRAFT");
        assertThat(copy.getViewCount()).isZero();
        assertThat(copy.getLikeCount()).isZero();
        assertThat(copy.getTitle()).isEqualTo("双 11 营销文案-副本");
    }

    @Test
    @DisplayName("incrementMetric: VIEW 指标 +1")
    void incrementMetric_view() throws ScrmException {
        ScrmContentEntity entity = buildContentEntity(10L, "PUBLISHED");
        entity.setViewCount(5);
        when(contentRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(contentRepository.save(any(ScrmContentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.incrementMetric(10L, "VIEW");

        ArgumentCaptor<ScrmContentEntity> captor =
                ArgumentCaptor.forClass(ScrmContentEntity.class);
        verify(contentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getViewCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("incrementMetric: 非法指标抛 BAD_REQUEST")
    void incrementMetric_invalid() {
        ScrmContentEntity entity = buildContentEntity(10L, "PUBLISHED");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.incrementMetric(10L, "INVALID"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("指标非法");
        verify(contentRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("deleteContent: 级联清理渠道与排期")
    void deleteContent_cascade() throws ScrmException {
        ScrmContentEntity entity = buildContentEntity(10L, "DRAFT");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmContentChannelEntity ch = buildChannelEntity(1L, 10L, "WECHAT_OFFICIAL", "PUBLISHED");
        when(channelRepository.findByContentIdOrderByChannelAsc(10L))
                .thenReturn(List.of(ch));
        when(scheduleRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.emptyList());

        service.deleteContent(10L);

        verify(channelRepository, times(1)).deleteAll(List.of(ch));
        verify(scheduleRepository, never()).deleteAll(any());
        verify(contentRepository, times(1)).delete(entity);
    }

    // ==================== 渠道分发 ====================

    @Test
    @DisplayName("publishToChannels: 定时发布创建排期任务并将内容置 SCHEDULED")
    void publishToChannels_scheduled() throws ScrmException {
        ScrmContentEntity content = buildContentEntity(10L, "APPROVED");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(content));
        when(scheduleRepository.save(any(ScrmContentScheduleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(contentRepository.save(any(ScrmContentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmContentPublishDto publishDto = new ScrmContentPublishDto();
        publishDto.setContentId(10L);
        publishDto.setChannels(List.of("WECHAT_OFFICIAL"));
        publishDto.setScheduledAt(LocalDateTime.now().plusDays(1));

        List<Map<String, Object>> result = service.publishToChannels(publishDto);

        // 验证创建了排期任务
        verify(scheduleRepository, times(1)).save(any(ScrmContentScheduleEntity.class));
        // 验证内容状态置 SCHEDULED
        ArgumentCaptor<ScrmContentEntity> contentCaptor =
                ArgumentCaptor.forClass(ScrmContentEntity.class);
        verify(contentRepository, times(1)).save(contentCaptor.capture());
        assertThat(contentCaptor.getValue().getStatus()).isEqualTo("SCHEDULED");
        assertThat(contentCaptor.getValue().getScheduledAt()).isNotNull();
        // 验证返回结果
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("status")).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("publishToChannels: 立即发布 APPROVED 内容创建 PUBLISHED 渠道记录")
    void publishToChannels_immediate() throws ScrmException {
        ScrmContentEntity content = buildContentEntity(10L, "APPROVED");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(content));
        // 渠道不存在 → 新建
        when(channelRepository.findByContentIdAndChannel(10L, "WECHAT_OFFICIAL"))
                .thenReturn(Optional.empty());
        when(channelRepository.save(any(ScrmContentChannelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(contentRepository.save(any(ScrmContentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmContentPublishDto publishDto = new ScrmContentPublishDto();
        publishDto.setContentId(10L);
        publishDto.setChannels(List.of("WECHAT_OFFICIAL"));

        List<Map<String, Object>> result = service.publishToChannels(publishDto);

        // 渠道保存两次 (PUBLISHING 与 PUBLISHED)
        verify(channelRepository, times(2)).save(any(ScrmContentChannelEntity.class));
        // 内容状态置 PUBLISHED
        ArgumentCaptor<ScrmContentEntity> contentCaptor =
                ArgumentCaptor.forClass(ScrmContentEntity.class);
        verify(contentRepository, times(1)).save(contentCaptor.capture());
        assertThat(contentCaptor.getValue().getStatus()).isEqualTo("PUBLISHED");
        assertThat(contentCaptor.getValue().getPublishedAt()).isNotNull();
        // 验证返回的渠道结果
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("status")).isEqualTo("PUBLISHED");
        assertThat(result.get(0).get("channelPostId")).isNotNull();
        assertThat(result.get(0).get("channelPostUrl")).asString().contains("wechat_official");
    }

    @Test
    @DisplayName("publishToChannels: DRAFT 状态不可立即发布 (CONFLICT)")
    void publishToChannels_draftImmediate() {
        ScrmContentEntity content = buildContentEntity(10L, "DRAFT");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(content));
        ScrmContentPublishDto publishDto = new ScrmContentPublishDto();
        publishDto.setContentId(10L);
        publishDto.setChannels(List.of("WECHAT_OFFICIAL"));

        assertThatThrownBy(() -> service.publishToChannels(publishDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 APPROVED / SCHEDULED 状态可立即发布");
        verify(channelRepository, never()).save(any());
    }

    @Test
    @DisplayName("publishToChannels: 渠道非法抛 BAD_REQUEST")
    void publishToChannels_invalidChannel() {
        ScrmContentEntity content = buildContentEntity(10L, "APPROVED");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(content));
        ScrmContentPublishDto publishDto = new ScrmContentPublishDto();
        publishDto.setContentId(10L);
        publishDto.setChannels(List.of("UNKNOWN_CHANNEL"));

        assertThatThrownBy(() -> service.publishToChannels(publishDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("渠道非法");
        verify(channelRepository, never()).save(any());
    }

    @Test
    @DisplayName("publishToChannels: 渠道列表为空抛 BAD_REQUEST")
    void publishToChannels_emptyChannels() {
        ScrmContentEntity content = buildContentEntity(10L, "APPROVED");
        when(contentRepository.findById(10L)).thenReturn(Optional.of(content));
        ScrmContentPublishDto publishDto = new ScrmContentPublishDto();
        publishDto.setContentId(10L);
        publishDto.setChannels(Collections.emptyList());

        assertThatThrownBy(() -> service.publishToChannels(publishDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("渠道列表不能为空");
    }

    @Test
    @DisplayName("removeChannel: PUBLISHED → REMOVED")
    void removeChannel_publishedToRemoved() throws ScrmException {
        ScrmContentChannelEntity ch = buildChannelEntity(1L, 10L, "WECHAT_OFFICIAL", "PUBLISHED");
        when(channelRepository.findById(1L)).thenReturn(Optional.of(ch));
        when(channelRepository.save(any(ScrmContentChannelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.removeChannel(1L);

        ArgumentCaptor<ScrmContentChannelEntity> captor =
                ArgumentCaptor.forClass(ScrmContentChannelEntity.class);
        verify(channelRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("REMOVED");
    }

    @Test
    @DisplayName("removeChannel: PENDING 状态不可移除 (CONFLICT)")
    void removeChannel_pendingRejects() {
        ScrmContentChannelEntity ch = buildChannelEntity(1L, 10L, "WECHAT_OFFICIAL", "PENDING");
        when(channelRepository.findById(1L)).thenReturn(Optional.of(ch));

        assertThatThrownBy(() -> service.removeChannel(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PUBLISHED / FAILED 状态可移除");
        verify(channelRepository, never()).save(any());
    }

    @Test
    @DisplayName("republishChannel: PENDING 状态不可重发 (CONFLICT)")
    void republishChannel_pendingRejects() {
        ScrmContentChannelEntity ch = buildChannelEntity(1L, 10L, "WECHAT_OFFICIAL", "PENDING");
        when(channelRepository.findById(1L)).thenReturn(Optional.of(ch));

        assertThatThrownBy(() -> service.republishChannel(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("PENDING / PUBLISHING 状态渠道不可重新发布");
        verify(channelRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateChannelMetrics: 增量更新各指标")
    void updateChannelMetrics_increment() throws ScrmException {
        ScrmContentChannelEntity ch = buildChannelEntity(1L, 10L, "WECHAT_OFFICIAL", "PUBLISHED");
        when(channelRepository.findById(1L)).thenReturn(Optional.of(ch));
        when(channelRepository.save(any(ScrmContentChannelEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.updateChannelMetrics(1L, 10, 5, 2, 1, 1);

        ArgumentCaptor<ScrmContentChannelEntity> captor =
                ArgumentCaptor.forClass(ScrmContentChannelEntity.class);
        verify(channelRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getViewCount()).isEqualTo(10);
        assertThat(captor.getValue().getLikeCount()).isEqualTo(5);
        assertThat(captor.getValue().getShareCount()).isEqualTo(2);
        assertThat(captor.getValue().getCommentCount()).isEqualTo(1);
        assertThat(captor.getValue().getConversionCount()).isEqualTo(1);
    }

    // ==================== 排期管理 ====================

    @Test
    @DisplayName("createSchedule: 写入默认值并置 PENDING")
    void createSchedule_success() throws ScrmException {
        when(contentRepository.findById(10L)).thenReturn(Optional.of(buildContentEntity(10L, "APPROVED")));
        when(scheduleRepository.save(any(ScrmContentScheduleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmContentScheduleDto dto = new ScrmContentScheduleDto();
        dto.setContentId(10L);
        dto.setScheduleName("双 11 预热");
        dto.setChannels("WECHAT_OFFICIAL,DOUYIN");
        dto.setScheduledAt(LocalDateTime.now().plusDays(1));

        service.createSchedule(dto);

        ArgumentCaptor<ScrmContentScheduleEntity> captor =
                ArgumentCaptor.forClass(ScrmContentScheduleEntity.class);
        verify(scheduleRepository, times(1)).save(captor.capture());
        ScrmContentScheduleEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getTimezone()).isEqualTo("Asia/Shanghai");
        assertThat(saved.getRepeatType()).isEqualTo("NONE");
        // channels 规范化 (去空白)
        assertThat(saved.getChannels()).isEqualTo("WECHAT_OFFICIAL,DOUYIN");
    }

    @Test
    @DisplayName("createSchedule: 计划时间为空抛 BAD_REQUEST")
    void createSchedule_noScheduledAt() {
        ScrmContentScheduleDto dto = new ScrmContentScheduleDto();
        dto.setContentId(10L);
        dto.setScheduleName("双 11 预热");
        dto.setChannels("WECHAT_OFFICIAL");

        assertThatThrownBy(() -> service.createSchedule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("计划发布时间不能为空");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSchedule: 渠道非法抛 BAD_REQUEST")
    void createSchedule_invalidChannel() {
        ScrmContentScheduleDto dto = new ScrmContentScheduleDto();
        dto.setContentId(10L);
        dto.setScheduleName("双 11 预热");
        dto.setChannels("UNKNOWN_CHANNEL");
        dto.setScheduledAt(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> service.createSchedule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("渠道非法");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateSchedule: 已完成排期不可修改 (CONFLICT)")
    void updateSchedule_completedRejects() {
        ScrmContentScheduleEntity entity = buildScheduleEntity(1L, 10L, "COMPLETED");
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(entity));
        ScrmContentScheduleDto dto = new ScrmContentScheduleDto();
        dto.setScheduleName("新名称");

        assertThatThrownBy(() -> service.updateSchedule(1L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已完成 / 已取消的排期不可修改");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelSchedule: PENDING → CANCELLED")
    void cancelSchedule_pendingToCancelled() throws ScrmException {
        ScrmContentScheduleEntity entity = buildScheduleEntity(1L, 10L, "PENDING");
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(scheduleRepository.save(any(ScrmContentScheduleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.cancelSchedule(1L);

        ArgumentCaptor<ScrmContentScheduleEntity> captor =
                ArgumentCaptor.forClass(ScrmContentScheduleEntity.class);
        verify(scheduleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("cancelSchedule: COMPLETED 状态不可取消 (CONFLICT)")
    void cancelSchedule_completedRejects() {
        ScrmContentScheduleEntity entity = buildScheduleEntity(1L, 10L, "COMPLETED");
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.cancelSchedule(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PENDING / EXECUTING 状态可取消");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("executeSchedule: 非 PENDING 状态不可执行 (CONFLICT)")
    void executeSchedule_nonPendingRejects() {
        ScrmContentScheduleEntity entity = buildScheduleEntity(1L, 10L, "COMPLETED");
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.executeSchedule(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PENDING 状态排期可执行");
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("processDueSchedules: 无到期排期返回 0")
    void processDueSchedules_empty() {
        when(scheduleRepository.findDueSchedules(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        int processed = service.processDueSchedules();

        assertThat(processed).isZero();
        verify(scheduleRepository, never()).save(any());
    }

    // ==================== 素材库 ====================

    @Test
    @DisplayName("uploadAsset: 默认来源 UPLOAD, 使用次数 0, 公开标志 FALSE")
    void uploadAsset_success() throws ScrmException {
        ScrmContentAssetDto dto = buildAssetDto();
        when(assetRepository.save(any(ScrmContentAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.uploadAsset(dto);

        ArgumentCaptor<ScrmContentAssetEntity> captor =
                ArgumentCaptor.forClass(ScrmContentAssetEntity.class);
        verify(assetRepository, times(1)).save(captor.capture());
        ScrmContentAssetEntity saved = captor.getValue();
        assertThat(saved.getSourceType()).isEqualTo("UPLOAD");
        assertThat(saved.getUsageCount()).isZero();
        assertThat(saved.getIsPublic()).isFalse();
        assertThat(saved.getCreatedBy()).isEqualTo("tester");
    }

    @Test
    @DisplayName("uploadAsset: 素材类型非法抛 BAD_REQUEST")
    void uploadAsset_invalidType() {
        ScrmContentAssetDto dto = buildAssetDto();
        dto.setAssetType("UNKNOWN");
        assertThatThrownBy(() -> service.uploadAsset(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("素材类型非法");
        verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("uploadAsset: 文件 URL 为空抛 BAD_REQUEST")
    void uploadAsset_blankFileUrl() {
        ScrmContentAssetDto dto = buildAssetDto();
        dto.setFileUrl(" ");
        assertThatThrownBy(() -> service.uploadAsset(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("文件 URL 不能为空");
        verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("incrementUsage: 使用次数 +1")
    void incrementUsage_success() throws ScrmException {
        ScrmContentAssetEntity entity = buildAssetEntity(1L);
        entity.setUsageCount(3);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(assetRepository.save(any(ScrmContentAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.incrementUsage(1L);

        ArgumentCaptor<ScrmContentAssetEntity> captor =
                ArgumentCaptor.forClass(ScrmContentAssetEntity.class);
        verify(assetRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUsageCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("batchImportAssets: 全部失败时返回空列表, 不抛异常")
    void batchImportAssets_allFail() {
        ScrmContentAssetDto dto = buildAssetDto();
        dto.setAssetType("UNKNOWN"); // 触发校验失败
        List<ScrmContentAssetEntity> result = service.batchImportAssets(List.of(dto));
        assertThat(result).isEmpty();
        verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("batchImportAssets: 空列表返回空结果")
    void batchImportAssets_empty() {
        List<ScrmContentAssetEntity> result = service.batchImportAssets(Collections.emptyList());
        assertThat(result).isEmpty();
        verify(assetRepository, never()).save(any());
    }

    // ==================== 统计与效果追踪 ====================

    @Test
    @DisplayName("getContentStats: 汇总状态分布与互动指标, 计算转化率")
    void getContentStats_success() {
        when(contentRepository.countByStatus(any(), any()))
                .thenReturn(List.of(new Object[]{"PUBLISHED", 5L}, new Object[]{"DRAFT", 3L}));
        // viewSum=100, likeSum=20, shareSum=10, commentSum=5, collectSum=8, conversionSum=4, publishedCount=5
        when(contentRepository.sumMetrics(any(), any()))
                .thenReturn(new Object[]{100L, 20L, 10L, 5L, 8L, 4L, 5L});

        Map<String, Object> stats = service.getContentStats(null, null);

        assertThat(stats.get("total")).isEqualTo(8L);
        assertThat(stats.get("published")).isEqualTo(5L);
        @SuppressWarnings("unchecked")
        Map<String, Long> statusCount = (Map<String, Long>) stats.get("statusCount");
        assertThat(statusCount.get("PUBLISHED")).isEqualTo(5L);
        assertThat(statusCount.get("DRAFT")).isEqualTo(3L);
        assertThat(stats.get("viewSum")).isEqualTo(100L);
        assertThat(stats.get("conversionSum")).isEqualTo(4L);
        assertThat(stats.get("engagementTotal")).isEqualTo(143L);
        assertThat((Double) stats.get("conversionRate")).isEqualTo(0.04);
    }

    @Test
    @DisplayName("getContentStats: 无数据时返回 0 与默认转化率")
    void getContentStats_empty() {
        when(contentRepository.countByStatus(any(), any()))
                .thenReturn(Collections.emptyList());
        when(contentRepository.sumMetrics(any(), any()))
                .thenReturn(new Object[]{0L, 0L, 0L, 0L, 0L, 0L, 0L});

        Map<String, Object> stats = service.getContentStats(null, null);

        assertThat(stats.get("total")).isEqualTo(0L);
        assertThat(stats.get("published")).isEqualTo(0L);
        assertThat((Double) stats.get("conversionRate")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getChannelStats: 按渠道汇总互动指标")
    void getChannelStats_success() {
        when(channelRepository.aggregateByChannel(any(), any()))
                .thenReturn(Collections.singletonList(
                        new Object[]{"WECHAT_OFFICIAL", 50L, 10L, 5L, 3L, 2L, 1L}));

        List<Map<String, Object>> result = service.getChannelStats(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("channel")).isEqualTo("WECHAT_OFFICIAL");
        assertThat(result.get(0).get("viewSum")).isEqualTo(50L);
        assertThat(result.get(0).get("publishedCount")).isEqualTo(1L);
    }

    @Test
    @DisplayName("getContentPerformance: 聚合单内容多渠道指标")
    void getContentPerformance_success() throws ScrmException {
        ScrmContentEntity content = buildContentEntity(10L, "PUBLISHED");
        content.setViewCount(100);
        content.setLikeCount(20);
        when(contentRepository.findById(10L)).thenReturn(Optional.of(content));
        ScrmContentChannelEntity ch1 = buildChannelEntity(1L, 10L, "WECHAT_OFFICIAL", "PUBLISHED");
        ch1.setViewCount(60);
        ch1.setLikeCount(12);
        ScrmContentChannelEntity ch2 = buildChannelEntity(2L, 10L, "DOUYIN", "PUBLISHED");
        ch2.setViewCount(40);
        ch2.setLikeCount(8);
        when(channelRepository.findByContentIdOrderByChannelAsc(10L))
                .thenReturn(List.of(ch1, ch2));

        Map<String, Object> result = service.getContentPerformance(10L);

        assertThat(result.get("title")).isEqualTo("双 11 营销文案");
        assertThat(result.get("channelCount")).isEqualTo(2);
        assertThat(result.get("channelAgg")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> agg = (Map<String, Object>) result.get("channelAgg");
        assertThat(agg.get("viewSum")).isEqualTo(100L);
        assertThat(agg.get("likeSum")).isEqualTo(20L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> channelMetrics = (List<Map<String, Object>>) result.get("channelMetrics");
        assertThat(channelMetrics).hasSize(2);
    }

    @Test
    @DisplayName("getTopContents: 非法指标抛 BAD_REQUEST")
    void getTopContents_invalidMetric() {
        assertThatThrownBy(() -> service.getTopContents(10, "INVALID"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("指标非法");
        verify(contentRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getTopContents: 按 VIEW 排序返回 PUBLISHED 内容")
    void getTopContents_byView() throws ScrmException {
        ScrmContentEntity c1 = buildContentEntity(1L, "PUBLISHED");
        c1.setViewCount(100);
        ScrmContentEntity c2 = buildContentEntity(2L, "PUBLISHED");
        c2.setViewCount(50);
        when(contentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c1, c2)));

        List<ScrmContentEntity> result = service.getTopContents(10, "VIEW");

        assertThat(result).hasSize(2);
        verify(contentRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getAssetUsageStats: 按素材类型聚合统计")
    void getAssetUsageStats_success() {
        when(assetRepository.aggregateByType())
                .thenReturn(List.of(new Object[]{"IMAGE", 10L, 25L}, new Object[]{"VIDEO", 5L, 8L}));

        List<Map<String, Object>> result = service.getAssetUsageStats();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("assetType")).isEqualTo("IMAGE");
        assertThat(result.get(0).get("assetCount")).isEqualTo(10L);
        assertThat(result.get(0).get("usageSum")).isEqualTo(25L);
    }

    // ==================== 列表查询 ====================

    @Test
    @DisplayName("listContents: 按条件分页查询")
    void listContents_filter() {
        ScrmContentEntity c1 = buildContentEntity(1L, "PUBLISHED");
        when(contentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c1)));

        var page = service.listContents("ARTICLE", null, "PUBLISHED",
                null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        verify(contentRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("listChannels: 校验内容存在后返回渠道列表")
    void listChannels_success() throws ScrmException {
        when(contentRepository.findById(10L)).thenReturn(Optional.of(buildContentEntity(10L, "PUBLISHED")));
        ScrmContentChannelEntity ch = buildChannelEntity(1L, 10L, "WECHAT_OFFICIAL", "PUBLISHED");
        when(channelRepository.findByContentIdOrderByChannelAsc(10L))
                .thenReturn(List.of(ch));

        List<ScrmContentChannelEntity> result = service.listChannels(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChannel()).isEqualTo("WECHAT_OFFICIAL");
    }
}
