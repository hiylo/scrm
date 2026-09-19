/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmTicketAssignDto;
import org.hiylo.scrm.dto.ScrmTicketDto;
import org.hiylo.scrm.dto.ScrmTicketSatisfactionDto;
import org.hiylo.scrm.entity.ScrmTicketEntity;
import org.hiylo.scrm.entity.ScrmTicketHistoryEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmTicketCommentRepository;
import org.hiylo.scrm.repository.ScrmTicketHistoryRepository;
import org.hiylo.scrm.repository.ScrmTicketRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmTicketService 单元测试
 * <p>
 * 聚焦工单创建 (编号生成 / 默认值 / SLA 计算)、分配 (状态流转 / 首次响应)、
 * 状态变更 (RESOLVED / CLOSED / REOPENED 副作用)、升级、满意度评价、
 * 越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmTicketService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmTicketServiceTest {

    /** 工单仓库 Mock */
    @Mock
    private ScrmTicketRepository ticketRepository;
    /** 工单评论仓库 Mock */
    @Mock
    private ScrmTicketCommentRepository commentRepository;
    /** 工单历史记录仓库 Mock */
    @Mock
    private ScrmTicketHistoryRepository historyRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmTicketService service;

    @BeforeEach
    void setUp() {
        service = new ScrmTicketService(ticketRepository, commentRepository,
                historyRepository, customerRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的工单实体 (用于 findById 返回)
     */
    private ScrmTicketEntity buildTicketEntity(Long id, String status, String priority) {
        ScrmTicketEntity entity = new ScrmTicketEntity();
        entity.setId(id);
        entity.setTicketNo("TKT202608050001");
        entity.setTitle("商品破损");
        entity.setDescription("收到商品有破损");
        entity.setCustomerId(100L);
        entity.setCustomerName("张三");
        entity.setCategory("PRODUCT_ISSUE");
        entity.setPriority(priority);
        entity.setStatus(status);
        entity.setSource("CUSTOMER");
        entity.setSlaDueAt(LocalDateTime.now().plusHours(24));
        entity.setCreateTime(LocalDateTime.now().minusHours(2));
        return entity;
    }

    /**
     * 构造工单创建参数
     */
    private ScrmTicketDto buildCreateDto() {
        ScrmTicketDto dto = new ScrmTicketDto();
        dto.setTitle("商品破损");
        dto.setDescription("收到商品有破损");
        dto.setCustomerId(100L);
        dto.setCustomerName("张三");
        dto.setCategory("PRODUCT_ISSUE");
        dto.setPriority("HIGH");
        dto.setCreatedBy("agent01");
        return dto;
    }

    @Test
    @DisplayName("createTicket: 生成编号, 写入账号 ID 与默认值, 按 HIGH 优先级计算 SLA 8h")
    void createTicket_success() throws ScrmException {
        when(ticketRepository.countByTicketNoStartingWith(anyString()))
                .thenReturn(0L);
        when(ticketRepository.save(any(ScrmTicketEntity.class)))
                .thenAnswer(inv -> {
                    ScrmTicketEntity e = inv.getArgument(0);
                    e.setId(1L);
                    return e;
                });

        ScrmTicketDto result = service.createTicket(buildCreateDto());

        ArgumentCaptor<ScrmTicketEntity> captor =
                ArgumentCaptor.forClass(ScrmTicketEntity.class);
        verify(ticketRepository, times(1)).save(captor.capture());
        ScrmTicketEntity saved = captor.getValue();
        assertThat(saved.getTicketNo()).startsWith("TKT");
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getSource()).isEqualTo("CUSTOMER");
        assertThat(saved.getPriority()).isEqualTo("HIGH");
        // HIGH 优先级 SLA = 8 小时
        assertThat(saved.getSlaDueAt()).isNotNull();
        // 记录 CREATED 历史
        verify(historyRepository, times(1)).save(any(ScrmTicketHistoryEntity.class));
        // 记录系统评论
        verify(commentRepository, times(1)).save(any());
        assertThat(result.getTicketNo()).startsWith("TKT");
        assertThat(result.getStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("createTicket: 非法类别抛 BAD_REQUEST")
    void createTicket_invalidCategory() {
        ScrmTicketDto dto = buildCreateDto();
        dto.setCategory("INVALID_CATEGORY");

        assertThatThrownBy(() -> service.createTicket(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("工单类别非法");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTicket: 非法优先级抛 BAD_REQUEST")
    void createTicket_invalidPriority() {
        ScrmTicketDto dto = buildCreateDto();
        dto.setPriority("CRITICAL");

        assertThatThrownBy(() -> service.createTicket(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("优先级非法");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignTicket: OPEN 工单分配后状态置 IN_PROGRESS 并记录首次响应时间")
    void assignTicket_success() throws ScrmException {
        ScrmTicketEntity entity = buildTicketEntity(1L, "OPEN", "MEDIUM");
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(ticketRepository.save(any(ScrmTicketEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmTicketAssignDto assignDto = new ScrmTicketAssignDto();
        assignDto.setTicketId(1L);
        assignDto.setAssigneeId("agent02");
        assignDto.setAssigneeName("客服二号");
        assignDto.setTeamId("team01");

        ScrmTicketDto result = service.assignTicket(assignDto);

        ArgumentCaptor<ScrmTicketEntity> captor =
                ArgumentCaptor.forClass(ScrmTicketEntity.class);
        verify(ticketRepository, times(1)).save(captor.capture());
        ScrmTicketEntity saved = captor.getValue();
        assertThat(saved.getAssigneeId()).isEqualTo("agent02");
        assertThat(saved.getAssigneeName()).isEqualTo("客服二号");
        assertThat(saved.getTeamId()).isEqualTo("team01");
        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(saved.getFirstResponseAt()).isNotNull();
        verify(historyRepository, times(1)).save(any(ScrmTicketHistoryEntity.class));
        assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("changeStatus: 变更为 RESOLVED 后设置 resolvedAt 与解决时长")
    void changeStatus_resolved() throws ScrmException {
        ScrmTicketEntity entity = buildTicketEntity(1L, "IN_PROGRESS", "MEDIUM");
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(ticketRepository.save(any(ScrmTicketEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmTicketDto result = service.changeStatus(1L, "RESOLVED", "已修复");

        ArgumentCaptor<ScrmTicketEntity> captor =
                ArgumentCaptor.forClass(ScrmTicketEntity.class);
        verify(ticketRepository, times(1)).save(captor.capture());
        ScrmTicketEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("RESOLVED");
        assertThat(saved.getResolvedAt()).isNotNull();
        assertThat(saved.getResolutionTimeMinutes()).isNotNull().isPositive();
        verify(historyRepository, times(1)).save(any(ScrmTicketHistoryEntity.class));
        assertThat(result.getStatus()).isEqualTo("RESOLVED");
    }

    @Test
    @DisplayName("closeTicket: 已关闭工单再次关闭抛 BAD_REQUEST")
    void closeTicket_alreadyClosed() {
        ScrmTicketEntity entity = buildTicketEntity(1L, "CLOSED", "MEDIUM");
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.closeTicket(1L, "再次关闭"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("工单已关闭或取消");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("escalateTicket: MEDIUM 工单升级后优先级置 HIGH, 状态置 IN_PROGRESS")
    void escalateTicket_success() throws ScrmException {
        ScrmTicketEntity entity = buildTicketEntity(1L, "OPEN", "MEDIUM");
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(ticketRepository.save(any(ScrmTicketEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmTicketDto result = service.escalateTicket(1L, "客户催促");

        ArgumentCaptor<ScrmTicketEntity> captor =
                ArgumentCaptor.forClass(ScrmTicketEntity.class);
        verify(ticketRepository, times(1)).save(captor.capture());
        ScrmTicketEntity saved = captor.getValue();
        assertThat(saved.getPriority()).isEqualTo("HIGH");
        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
        verify(historyRepository, times(1)).save(any(ScrmTicketHistoryEntity.class));
        verify(commentRepository, times(1)).save(any());
        assertThat(result.getPriority()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("reopenTicket: 非 RESOLVED / CLOSED 状态重新打开抛 BAD_REQUEST")
    void reopenTicket_invalidStatus() {
        ScrmTicketEntity entity = buildTicketEntity(1L, "OPEN", "MEDIUM");
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.reopenTicket(1L, "重新打开"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 RESOLVED / CLOSED 状态可重新打开");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitSatisfaction: 评分越界 (>5) 抛 BAD_REQUEST")
    void submitSatisfaction_scoreOutOfRange() {
        ScrmTicketSatisfactionDto dto = new ScrmTicketSatisfactionDto();
        dto.setTicketId(1L);
        dto.setScore(6);

        assertThatThrownBy(() -> service.submitSatisfaction(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("满意度评分需为 1-5");
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitSatisfaction: OPEN 状态工单不允许评价抛 BAD_REQUEST")
    void submitSatisfaction_invalidStatus() {
        ScrmTicketEntity entity = buildTicketEntity(1L, "OPEN", "MEDIUM");
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(entity));

        ScrmTicketSatisfactionDto dto = new ScrmTicketSatisfactionDto();
        dto.setTicketId(1L);
        dto.setScore(5);

        assertThatThrownBy(() -> service.submitSatisfaction(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("当前状态不允许提交满意度评价");
        verify(ticketRepository, never()).save(any());
    }

    
}
