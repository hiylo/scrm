/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmFeedbackCategoryDto;
import org.hiylo.scrm.dto.ScrmFeedbackCommentDto;
import org.hiylo.scrm.dto.ScrmFeedbackDto;
import org.hiylo.scrm.dto.ScrmFeedbackProcessDto;
import org.hiylo.scrm.entity.ScrmFeedbackCategoryEntity;
import org.hiylo.scrm.entity.ScrmFeedbackCommentEntity;
import org.hiylo.scrm.entity.ScrmFeedbackEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmFeedbackCategoryRepository;
import org.hiylo.scrm.repository.ScrmFeedbackCommentRepository;
import org.hiylo.scrm.repository.ScrmFeedbackRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmFeedbackService 单元测试
 * <p>
 * 聚焦客户反馈 CRUD / 状态流转 (NEW→IN_PROGRESS→RESOLVED→CLOSED / REJECTED / DUPLICATE) /
 * 分配 / 评论 / 分类管理 / 情感分析 / 编号生成 / 级联清理与数据隔离等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmFeedbackService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmFeedbackServiceTest {

    /** 客户反馈仓库 Mock */
    @Mock
    private ScrmFeedbackRepository feedbackRepository;
    /** 反馈评论仓库 Mock */
    @Mock
    private ScrmFeedbackCommentRepository commentRepository;
    /** 反馈分类仓库 Mock */
    @Mock
    private ScrmFeedbackCategoryRepository categoryRepository;

    /** 被测服务实例 */
    private ScrmFeedbackService service;

    @BeforeEach
    void setUp() {
        ScrmFeedbackAnalysisService analysisService =
                new ScrmFeedbackAnalysisService(feedbackRepository, categoryRepository);
        ScrmFeedbackManagementService managementService = new ScrmFeedbackManagementService(
                feedbackRepository, commentRepository, categoryRepository, analysisService);
        analysisService.managementService = managementService;
        ScrmFeedbackCommentService commentService =
                new ScrmFeedbackCommentService(commentRepository, feedbackRepository, managementService);
        ScrmFeedbackCategoryService categoryService =
                new ScrmFeedbackCategoryService(categoryRepository, feedbackRepository, managementService);
        service = new ScrmFeedbackService(managementService, commentService, categoryService, analysisService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造反馈 DTO (创建用, 含必填字段)
     */
    private ScrmFeedbackDto buildFeedbackDto() {
        ScrmFeedbackDto dto = new ScrmFeedbackDto();
        dto.setTitle("配送延迟反馈");
        dto.setContent("物流太慢了, 等待多日未送达");
        dto.setFeedbackType("SERVICE_ISSUE");
        dto.setCategory("SUPPORT");
        return dto;
    }

    /**
     * 构造已持久化的反馈实体
     */
    private ScrmFeedbackEntity buildFeedbackEntity(Long id, String status) {
        ScrmFeedbackEntity entity = new ScrmFeedbackEntity();
        entity.setId(id);
        entity.setFeedbackNo("FB202608050001");
        entity.setTitle("配送延迟反馈");
        entity.setContent("物流太慢了");
        entity.setFeedbackType("SERVICE_ISSUE");
        entity.setCategory("SUPPORT");
        entity.setPriority("MEDIUM");
        entity.setStatus(status);
        entity.setSource("CUSTOMER");
        entity.setIsPublic(false);
        entity.setIsAnonymous(false);
        entity.setViewCount(0);
        entity.setUpvoteCount(0);
        entity.setCommentCount(0);
        return entity;
    }

    /**
     * 构造分类 DTO
     */
    private ScrmFeedbackCategoryDto buildCategoryDto() {
        ScrmFeedbackCategoryDto dto = new ScrmFeedbackCategoryDto();
        dto.setCategoryName("售后支持");
        dto.setCategoryCode("SUPPORT");
        dto.setDescription("售后问题分类");
        return dto;
    }

    /**
     * 构造已持久化的分类实体
     */
    private ScrmFeedbackCategoryEntity buildCategoryEntity(Long id) {
        ScrmFeedbackCategoryEntity entity = new ScrmFeedbackCategoryEntity();
        entity.setId(id);
        entity.setCategoryName("售后支持");
        entity.setCategoryCode("SUPPORT");
        entity.setDefaultPriority("MEDIUM");
        entity.setSlaHours(48);
        entity.setSortOrder(0);
        entity.setFeedbackCount(0);
        entity.setEnabled(Boolean.TRUE);
        return entity;
    }

    // ==================== 反馈 CRUD ====================

    @Test
    @DisplayName("createFeedback: 写入账号 ID 与默认值并生成反馈编号")
    void createFeedback_success() throws ScrmException {
        ScrmFeedbackDto dto = buildFeedbackDto();
        when(feedbackRepository.countByFeedbackNoStartingWith(anyString())).thenReturn(0L);
        when(feedbackRepository.save(any(ScrmFeedbackEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ScrmFeedbackDto result = service.createFeedback(dto);

        ArgumentCaptor<ScrmFeedbackEntity> captor =
                ArgumentCaptor.forClass(ScrmFeedbackEntity.class);
        verify(feedbackRepository, times(1)).save(captor.capture());
        ScrmFeedbackEntity saved = captor.getValue();
        assertThat(saved.getPriority()).isEqualTo("MEDIUM");
        assertThat(saved.getStatus()).isEqualTo("NEW");
        assertThat(saved.getSource()).isEqualTo("CUSTOMER");
        assertThat(saved.getIsPublic()).isFalse();
        assertThat(saved.getIsAnonymous()).isFalse();
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.getUpvoteCount()).isZero();
        assertThat(saved.getCommentCount()).isZero();
        assertThat(saved.getFeedbackNo()).startsWith("FB");
        assertThat(saved.getSentiment()).isEqualTo("NEGATIVE");
        assertThat(result.getFeedbackType()).isEqualTo("SERVICE_ISSUE");
    }

    @Test
    @DisplayName("createFeedback: 标题为空抛 BAD_REQUEST")
    void createFeedback_blankTitle() {
        ScrmFeedbackDto dto = buildFeedbackDto();
        dto.setTitle("  ");
        assertThatThrownBy(() -> service.createFeedback(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈标题不能为空");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("createFeedback: 反馈类型非法抛 BAD_REQUEST")
    void createFeedback_invalidType() {
        ScrmFeedbackDto dto = buildFeedbackDto();
        dto.setFeedbackType("INVALID");
        assertThatThrownBy(() -> service.createFeedback(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈类型非法");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("createFeedback: 优先级非法抛 BAD_REQUEST")
    void createFeedback_invalidPriority() {
        ScrmFeedbackDto dto = buildFeedbackDto();
        dto.setPriority("CRITICAL");
        assertThatThrownBy(() -> service.createFeedback(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("优先级非法");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateFeedback: 终态反馈不允许更新抛 BAD_REQUEST")
    void updateFeedback_terminalRejects() {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "CLOSED");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> service.updateFeedback(10L, buildFeedbackDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈已处于终态");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateFeedback: 内容变更时重算情感")
    void updateFeedback_contentChangeRecalcSentiment() throws ScrmException {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "NEW");
        entity.setContent("旧内容");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(feedbackRepository.save(any(ScrmFeedbackEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ScrmFeedbackDto dto = new ScrmFeedbackDto();
        dto.setContent("产品很好用, 非常满意");

        service.updateFeedback(10L, dto);

        ArgumentCaptor<ScrmFeedbackEntity> captor =
                ArgumentCaptor.forClass(ScrmFeedbackEntity.class);
        verify(feedbackRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("产品很好用, 非常满意");
        assertThat(captor.getValue().getSentiment()).isEqualTo("POSITIVE");
    }

    
    @Test
    @DisplayName("getFeedbackByNo: 编号为空抛 BAD_REQUEST")
    void getFeedbackByNo_blank() {
        assertThatThrownBy(() -> service.getFeedbackByNo("  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈编号不能为空");
    }

    
    @Test
    @DisplayName("deleteFeedback: 级联清理关联评论")
    void deleteFeedback_cascadeComments() throws ScrmException {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "NEW");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmFeedbackCommentEntity comment = new ScrmFeedbackCommentEntity();
        comment.setId(1L);
        when(commentRepository.findByFeedbackIdOrderByCreatedAtAsc(10L))
                .thenReturn(List.of(comment));

        service.deleteFeedback(10L);

        verify(commentRepository, times(1)).deleteAll(List.of(comment));
        verify(feedbackRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("deleteFeedback: 无评论时仅删除反馈")
    void deleteFeedback_noComments() throws ScrmException {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "NEW");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(commentRepository.findByFeedbackIdOrderByCreatedAtAsc(10L))
                .thenReturn(Collections.emptyList());

        service.deleteFeedback(10L);

        verify(commentRepository, never()).deleteAll(any());
        verify(feedbackRepository, times(1)).delete(entity);
    }

    // ==================== 反馈动作 ====================

    @Test
    @DisplayName("assignFeedback: 处理人与团队均空抛 BAD_REQUEST")
    void assignFeedback_noAssigneeNoTeam() {
        assertThatThrownBy(() -> service.assignFeedback(10L, null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("处理人 ID 与处理团队 ID 至少传其一");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignFeedback: NEW 状态分配后置 IN_PROGRESS 并记录首次响应时间")
    void assignFeedback_newToInProgress() throws ScrmException {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "NEW");
        entity.setCreateTime(LocalDateTime.now().minusHours(2));
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(feedbackRepository.save(any(ScrmFeedbackEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.assignFeedback(10L, "agent1", "team1");

        ArgumentCaptor<ScrmFeedbackEntity> captor =
                ArgumentCaptor.forClass(ScrmFeedbackEntity.class);
        verify(feedbackRepository, times(1)).save(captor.capture());
        ScrmFeedbackEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(saved.getAssigneeId()).isEqualTo("agent1");
        assertThat(saved.getTeamId()).isEqualTo("team1");
        assertThat(saved.getAssignedAt()).isNotNull();
        assertThat(saved.getFirstResponseAt()).isNotNull();
        assertThat(saved.getResponseTimeHours()).isNotNull();
    }

    @Test
    @DisplayName("changeStatus: 目标状态与当前相同抛 BAD_REQUEST")
    void changeStatus_sameStatus() {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "NEW");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmFeedbackProcessDto dto = new ScrmFeedbackProcessDto();
        dto.setFeedbackId(10L);
        dto.setStatus("NEW");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈状态未变更");
    }

    @Test
    @DisplayName("changeStatus: 终态反馈不允许变更抛 BAD_REQUEST")
    void changeStatus_terminalRejects() {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "CLOSED");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmFeedbackProcessDto dto = new ScrmFeedbackProcessDto();
        dto.setFeedbackId(10L);
        dto.setStatus("RESOLVED");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈已处于终态");
    }

    @Test
    @DisplayName("changeStatus: 流转到 RESOLVED 设置 resolvedAt 与解决时长")
    void changeStatus_toResolved() throws ScrmException {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "IN_PROGRESS");
        entity.setCreateTime(LocalDateTime.now().minusHours(5));
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(feedbackRepository.save(any(ScrmFeedbackEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ScrmFeedbackProcessDto dto = new ScrmFeedbackProcessDto();
        dto.setFeedbackId(10L);
        dto.setStatus("RESOLVED");
        dto.setResolution("已修复");

        service.changeStatus(dto);

        ArgumentCaptor<ScrmFeedbackEntity> captor =
                ArgumentCaptor.forClass(ScrmFeedbackEntity.class);
        verify(feedbackRepository, times(1)).save(captor.capture());
        ScrmFeedbackEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("RESOLVED");
        assertThat(saved.getResolvedAt()).isNotNull();
        assertThat(saved.getResolutionTimeHours()).isNotNull();
        assertThat(saved.getResolution()).isEqualTo("已修复");
    }

    @Test
    @DisplayName("changeStatus: 流转到 CLOSED 设置 closedAt")
    void changeStatus_toClosed() throws ScrmException {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "RESOLVED");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(feedbackRepository.save(any(ScrmFeedbackEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ScrmFeedbackProcessDto dto = new ScrmFeedbackProcessDto();
        dto.setFeedbackId(10L);
        dto.setStatus("CLOSED");

        service.changeStatus(dto);

        ArgumentCaptor<ScrmFeedbackEntity> captor =
                ArgumentCaptor.forClass(ScrmFeedbackEntity.class);
        verify(feedbackRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CLOSED");
        assertThat(captor.getValue().getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("changePriority: 优先级非法抛 BAD_REQUEST")
    void changePriority_invalid() {
        assertThatThrownBy(() -> service.changePriority(10L, "CRITICAL"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("优先级非法");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePriority: 优先级未变更抛 BAD_REQUEST")
    void changePriority_samePriority() {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "NEW");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> service.changePriority(10L, "MEDIUM"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈优先级未变更");
    }

    @Test
    @DisplayName("resolveFeedback: 终态反馈不允许解决抛 BAD_REQUEST")
    void resolveFeedback_terminalRejects() {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "CLOSED");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> service.resolveFeedback(10L, "fixed"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈已处于终态");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("closeFeedback: 已关闭反馈不允许再次关闭抛 BAD_REQUEST")
    void closeFeedback_alreadyClosed() {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "CLOSED");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> service.closeFeedback(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈已关闭");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejectFeedback: 终态反馈不允许驳回抛 BAD_REQUEST")
    void rejectFeedback_terminalRejects() {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "CLOSED");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> service.rejectFeedback(10L, "reason"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈已处于终态");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("markDuplicate: 原始反馈 ID 与当前相同抛 BAD_REQUEST")
    void markDuplicate_sameId() {
        assertThatThrownBy(() -> service.markDuplicate(10L, 10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("原始反馈 ID 不能与当前反馈 ID 相同");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("mergeFeedback: 合并反馈置 DUPLICATE 并追加系统评论")
    void mergeFeedback_success() throws ScrmException {
        ScrmFeedbackEntity entity = buildFeedbackEntity(10L, "NEW");
        entity.setFeedbackNo("FB202608050001");
        ScrmFeedbackEntity target = buildFeedbackEntity(20L, "NEW");
        target.setFeedbackNo("FB202608050002");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(feedbackRepository.findById(20L)).thenReturn(Optional.of(target));
        when(feedbackRepository.save(any(ScrmFeedbackEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.mergeFeedback(10L, 20L);

        ArgumentCaptor<ScrmFeedbackEntity> captor =
                ArgumentCaptor.forClass(ScrmFeedbackEntity.class);
        verify(feedbackRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DUPLICATE");
        assertThat(captor.getValue().getClosedAt()).isNotNull();
        assertThat(captor.getValue().getResolution()).contains("FB202608050002");
        verify(commentRepository, times(1)).save(any(ScrmFeedbackCommentEntity.class));
    }

    // ==================== 评论 ====================

    @Test
    @DisplayName("addComment: 参数为空抛 BAD_REQUEST")
    void addComment_nullDto() {
        assertThatThrownBy(() -> service.addComment(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("评论参数不能为空");
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("addComment: 创建评论并刷新反馈评论计数")
    void addComment_success() throws ScrmException {
        ScrmFeedbackEntity feedback = buildFeedbackEntity(10L, "NEW");
        when(feedbackRepository.findById(10L)).thenReturn(Optional.of(feedback));
        when(commentRepository.save(any(ScrmFeedbackCommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentRepository.countByFeedbackId(10L)).thenReturn(1L);
        when(feedbackRepository.save(any(ScrmFeedbackEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ScrmFeedbackCommentDto dto = new ScrmFeedbackCommentDto();
        dto.setFeedbackId(10L);
        dto.setCommentType("CUSTOMER");
        dto.setAuthorId("user1");
        dto.setContent("好评");

        service.addComment(dto);

        ArgumentCaptor<ScrmFeedbackCommentEntity> captor =
                ArgumentCaptor.forClass(ScrmFeedbackCommentEntity.class);
        verify(commentRepository, times(1)).save(captor.capture());
        ScrmFeedbackCommentEntity saved = captor.getValue();
        assertThat(saved.getFeedbackId()).isEqualTo(10L);
        assertThat(saved.getCommentType()).isEqualTo("CUSTOMER");
        assertThat(saved.getIsInternal()).isFalse();
        assertThat(saved.getUpvoteCount()).isZero();
    }

    @Test
    @DisplayName("addComment: 评论类型非法抛 BAD_REQUEST")
    void addComment_invalidType() {
        ScrmFeedbackCommentDto dto = new ScrmFeedbackCommentDto();
        dto.setFeedbackId(10L);
        dto.setCommentType("INVALID");
        dto.setAuthorId("user1");
        dto.setContent("内容");
        assertThatThrownBy(() -> service.addComment(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("评论类型非法");
        verify(commentRepository, never()).save(any());
    }

    
    // ==================== 分类管理 ====================

    @Test
    @DisplayName("createCategory: 写入账号 ID 与默认值并持久化")
    void createCategory_success() throws ScrmException {
        ScrmFeedbackCategoryDto dto = buildCategoryDto();
        when(categoryRepository.findByCategoryCode("SUPPORT")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(ScrmFeedbackCategoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createCategory(dto);

        ArgumentCaptor<ScrmFeedbackCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmFeedbackCategoryEntity.class);
        verify(categoryRepository, times(1)).save(captor.capture());
        ScrmFeedbackCategoryEntity saved = captor.getValue();
        assertThat(saved.getDefaultPriority()).isEqualTo("MEDIUM");
        assertThat(saved.getSlaHours()).isEqualTo(48);
        assertThat(saved.getSortOrder()).isZero();
        assertThat(saved.getFeedbackCount()).isZero();
        assertThat(saved.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("createCategory: 分类编码重复抛 CONFLICT")
    void createCategory_duplicateCode() {
        ScrmFeedbackCategoryDto dto = buildCategoryDto();
        when(categoryRepository.findByCategoryCode("SUPPORT"))
                .thenReturn(Optional.of(new ScrmFeedbackCategoryEntity()));
        assertThatThrownBy(() -> service.createCategory(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("分类编码已存在");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("enableCategory: 设置 enabled=true")
    void enableCategory_success() throws ScrmException {
        ScrmFeedbackCategoryEntity entity = buildCategoryEntity(10L);
        entity.setEnabled(false);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(categoryRepository.save(any(ScrmFeedbackCategoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enableCategory(10L);

        ArgumentCaptor<ScrmFeedbackCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmFeedbackCategoryEntity.class);
        verify(categoryRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableCategory: 设置 enabled=false")
    void disableCategory_success() throws ScrmException {
        ScrmFeedbackCategoryEntity entity = buildCategoryEntity(10L);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(categoryRepository.save(any(ScrmFeedbackCategoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.disableCategory(10L);

        ArgumentCaptor<ScrmFeedbackCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmFeedbackCategoryEntity.class);
        verify(categoryRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }

    
    @Test
    @DisplayName("listFeedbacks: 按条件 Specification 分页查询")
    void listFeedbacks_filter() {
        ScrmFeedbackEntity fb = buildFeedbackEntity(1L, "NEW");
        org.springframework.data.domain.Page<ScrmFeedbackEntity> page =
                new org.springframework.data.domain.PageImpl<>(List.of(fb));
        when(feedbackRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        org.springframework.data.domain.Page<ScrmFeedbackDto> result =
                service.listFeedbacks(new org.hiylo.scrm.dto.ScrmFeedbackQueryDto(),
                        org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        verify(feedbackRepository, times(1))
                .findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class));
    }

    // ==================== 分析与编号 ====================

    @Test
    @DisplayName("analyzeSentiment: 正面关键词返回 POSITIVE")
    void analyzeSentiment_positive() {
        assertThat(service.analyzeSentiment("产品很好用, 非常满意")).isEqualTo("POSITIVE");
    }

    @Test
    @DisplayName("analyzeSentiment: 负面关键词返回 NEGATIVE")
    void analyzeSentiment_negative() {
        assertThat(service.analyzeSentiment("太慢了, 投诉")).isEqualTo("NEGATIVE");
    }

    @Test
    @DisplayName("analyzeSentiment: 无关键词返回 NEUTRAL")
    void analyzeSentiment_neutral() {
        assertThat(service.analyzeSentiment("测试文本")).isEqualTo("NEUTRAL");
    }

    @Test
    @DisplayName("generateFeedbackNo: 基于当日序号生成编号")
    void generateFeedbackNo_success() {
        when(feedbackRepository.countByFeedbackNoStartingWith(anyString())).thenReturn(3L);
        String no = service.generateFeedbackNo();
        assertThat(no).startsWith("FB");
        assertThat(no).endsWith("0004");
    }

    @Test
    @DisplayName("generateSummary: 反馈不存在抛 NOT_FOUND")
    void generateSummary_notFound() {
        when(feedbackRepository.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generateSummary(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈不存在");
    }
}
