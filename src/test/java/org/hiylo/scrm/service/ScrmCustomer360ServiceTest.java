/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomer360ServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmCustomerTimelineEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerGroupMemberRepository;
import org.hiylo.scrm.repository.ScrmCustomerGroupRepository;
import org.hiylo.scrm.repository.ScrmCustomerLifecycleHistoryRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmCustomerTimelineRepository;
import org.hiylo.scrm.repository.ScrmJourneyEnrollmentRepository;
import org.hiylo.scrm.repository.ScrmOpportunityRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.vo.ScrmCustomer360Vo;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
 * ScrmCustomer360Service 单元测试
 * <p>
 * 聚焦客户 360° 视图聚合 (基本信息 / 标签 / 分组 / 商机 / 旅程 / 消息 / 时间线 / 互动统计)、
 * 时间线事件管理 (添加 / 参数校验 / 操作人回退)、批量概要查询容错与数据隔离校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCustomer360Service 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCustomer360ServiceTest {

    /** 客户数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 客户标签数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerTagRepository customerTagRepository;
    /** 客户标签关联数据仓库 Mock 桩 */
    @Mock
    private ScrmTagCustomerRepository tagCustomerRepository;
    /** 客户分组成员数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerGroupMemberRepository customerGroupMemberRepository;
    /** 客户分组数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerGroupRepository customerGroupRepository;
    /** 商机数据仓库 Mock 桩 */
    @Mock
    private ScrmOpportunityRepository opportunityRepository;
    /** 旅程加入数据仓库 Mock 桩 */
    @Mock
    private ScrmJourneyEnrollmentRepository journeyEnrollmentRepository;
    /** 客户生命周期历史数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerLifecycleHistoryRepository lifecycleHistoryRepository;
    /** 会话数据仓库 Mock 桩 */
    @Mock
    private ScrmConversationRepository conversationRepository;
    /** 会话消息数据仓库 Mock 桩 */
    @Mock
    private ScrmConversationMessageRepository conversationMessageRepository;
    /** 客户时间轴数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerTimelineRepository customerTimelineRepository;
    /** 数据权限服务 Mock 桩 */
    @Mock
    private DataScopeService dataScopeService;

    /** 被测服务实例 */
    private ScrmCustomer360Service service;

    @BeforeEach
    void setUp() {
        service = new ScrmCustomer360Service(customerRepository, customerTagRepository, tagCustomerRepository,
                customerGroupMemberRepository, customerGroupRepository, opportunityRepository,
                journeyEnrollmentRepository, lifecycleHistoryRepository, conversationRepository,
                conversationMessageRepository, customerTimelineRepository, dataScopeService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的客户实体 (用于 findById 返回)
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setPlatformType("WECHAT");
        entity.setNickname("张三");
        entity.setLifecycle("ACTIVE");
        entity.setCreateTime(LocalDateTime.now().minusDays(30));
        entity.setLastInteractionAt(LocalDateTime.now().minusDays(1));
        return entity;
    }

    /**
     * 构造空分页结果 (用于会话/消息/时间线查询返回)
     */
    private <T> Page<T> emptyPage() {
        return new PageImpl<>(List.of());
    }

    @Test
    @DisplayName("getCustomer360: 客户不存在抛 NOT_FOUND")
    void getCustomer360_notFound() {
        when(customerRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomer360(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户不存在");
    }

    
    @Test
    @DisplayName("getCustomer360: 聚合空数据源时返回基本视图")
    void getCustomer360_emptyDataSources() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        // 各数据源返回空
        when(tagCustomerRepository.findByCustomerId(eq(100L)))
                .thenReturn(List.of());
        when(customerGroupMemberRepository.findByCustomerId(eq(100L))).thenReturn(List.of());
        when(lifecycleHistoryRepository.findByCustomerIdOrderByIdDesc(eq(100L)))
                .thenReturn(List.of());
        when(opportunityRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(journeyEnrollmentRepository.findByCustomerId(eq(100L))).thenReturn(List.of());
        when(conversationRepository.findByCustomerIdOrderByLastMessageAtDesc(eq(100L), any())).thenReturn(emptyPage());
        when(customerTimelineRepository.findByCustomerIdOrderByEventTimeDesc(eq(100L), any())).thenReturn(emptyPage());
        // 注: tags 为空时 deriveLevel 不调用 findByTagCode, 无需 stub

        ScrmCustomer360Vo result = service.getCustomer360(100L);

        assertThat(result).isNotNull();
        assertThat(result.getCustomer()).isNotNull();
        assertThat(result.getCustomer().getId()).isEqualTo(100L);
        assertThat(result.getCustomer().getNickname()).isEqualTo("张三");
        // 空数据源
        assertThat(result.getTags()).isEmpty();
        assertThat(result.getGroups()).isEmpty();
        assertThat(result.getLifecycleHistory()).isEmpty();
        assertThat(result.getOpportunities()).isEmpty();
        assertThat(result.getJourneyEnrollments()).isEmpty();
        assertThat(result.getRecentMessages()).isEmpty();
        assertThat(result.getRecentTimeline()).isEmpty();
        // 无 level 标签 → null
        assertThat(result.getLevel()).isNull();
        // 互动统计: 无消息 / 无商机
        assertThat(result.getStats()).isNotNull();
        assertThat(result.getStats().getTotalMessages()).isZero();
        assertThat(result.getStats().getTotalOpportunityAmount()).isEqualTo(0.0);
        // 客户天数: createTime=30 天前 → 30
        assertThat(result.getStats().getCustomerDays()).isGreaterThanOrEqualTo(30L);
    }

    @Test
    @DisplayName("getCustomer360: 带 level 标签时推导出 level")
    void getCustomer360_withLevelTag() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        // level 标签定义: id=50, tagCode=level
        ScrmCustomerTagEntity levelTag = new ScrmCustomerTagEntity();
        levelTag.setId(50L);
        levelTag.setTagCode("level");
        when(customerTagRepository.findByTagCode(eq("level")))
                .thenReturn(Optional.of(levelTag));
        // 客户赋值: tagId=50, tagValue=VIP
        ScrmTagCustomerEntity tagAssign = new ScrmTagCustomerEntity();
        tagAssign.setId(1L);
        tagAssign.setCustomerId(100L);
        tagAssign.setTagId(50L);
        tagAssign.setTagValue("VIP");
        when(tagCustomerRepository.findByCustomerId(eq(100L)))
                .thenReturn(List.of(tagAssign));
        when(customerGroupMemberRepository.findByCustomerId(eq(100L))).thenReturn(List.of());
        when(lifecycleHistoryRepository.findByCustomerIdOrderByIdDesc(eq(100L)))
                .thenReturn(List.of());
        when(opportunityRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(journeyEnrollmentRepository.findByCustomerId(eq(100L))).thenReturn(List.of());
        when(conversationRepository.findByCustomerIdOrderByLastMessageAtDesc(eq(100L), any())).thenReturn(emptyPage());
        when(customerTimelineRepository.findByCustomerIdOrderByEventTimeDesc(eq(100L), any())).thenReturn(emptyPage());

        ScrmCustomer360Vo result = service.getCustomer360(100L);

        assertThat(result.getLevel()).isEqualTo("VIP");
        assertThat(result.getTags()).hasSize(1);
    }

    @Test
    @DisplayName("addTimelineEvent: 写入归属账号与默认值后持久化")
    void addTimelineEvent_success() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(dataScopeService.getCurrentUserId()).thenReturn("user-001");
        when(dataScopeService.getHeader("X-Username")).thenReturn("张三");
        when(customerTimelineRepository.save(any(ScrmCustomerTimelineEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerTimelineEntity result = service.addTimelineEvent(100L, "FOLLOW_UP",
                "首次跟进", "{}", null, null, null);

        ArgumentCaptor<ScrmCustomerTimelineEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerTimelineEntity.class);
        verify(customerTimelineRepository, times(1)).save(captor.capture());
        ScrmCustomerTimelineEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(100L);
        assertThat(saved.getEventType()).isEqualTo("FOLLOW_UP");
        assertThat(saved.getEventTitle()).isEqualTo("首次跟进");
        assertThat(saved.getEventTime()).isNotNull();
        // operatorId 为空时回退当前用户
        assertThat(saved.getOperatorId()).isEqualTo("user-001");
        // operatorName 为空时回退 X-Username
        assertThat(saved.getOperatorName()).isEqualTo("张三");
        // importance 为空时默认 NORMAL
        assertThat(saved.getImportance()).isEqualTo("NORMAL");
        assertThat(result.getEventType()).isEqualTo("FOLLOW_UP");
    }

    @Test
    @DisplayName("addTimelineEvent: 客户不存在抛 NOT_FOUND")
    void addTimelineEvent_customerNotFound() {
        when(customerRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addTimelineEvent(100L, "FOLLOW_UP", "首次跟进",
                null, null, null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户不存在");
        verify(customerTimelineRepository, never()).save(any());
    }

    @Test
    @DisplayName("addTimelineEvent: 事件类型为空抛 BAD_REQUEST")
    void addTimelineEvent_blankEventType() {
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.addTimelineEvent(100L, "", "首次跟进",
                null, null, null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("事件类型不能为空");
        verify(customerTimelineRepository, never()).save(any());
    }

    @Test
    @DisplayName("addTimelineEvent: 事件标题为空抛 BAD_REQUEST")
    void addTimelineEvent_blankEventTitle() {
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.addTimelineEvent(100L, "FOLLOW_UP", "",
                null, null, null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("事件标题不能为空");
        verify(customerTimelineRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("getInteractionStats: 客户不存在抛 NOT_FOUND")
    void getInteractionStats_notFound() {
        when(customerRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInteractionStats(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户不存在");
    }

    @Test
    @DisplayName("getInteractionStats: 返回消息数与客户天数")
    void getInteractionStats_success() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(conversationRepository.findByCustomerIdOrderByLastMessageAtDesc(eq(100L), any())).thenReturn(emptyPage());
        when(opportunityRepository.findAll(any(Specification.class))).thenReturn(List.of());

        var stats = service.getInteractionStats(100L);

        assertThat(stats).isNotNull();
        assertThat(stats.getTotalMessages()).isZero();
        // totalFollowUps 固定为 0 (跟进模块未接入)
        assertThat(stats.getTotalFollowUps()).isZero();
        // lastInteractionAt 取客户实体
        assertThat(stats.getLastInteractionAt()).isEqualTo(customer.getLastInteractionAt());
        // 客户天数 >= 30
        assertThat(stats.getCustomerDays()).isGreaterThanOrEqualTo(30L);
        assertThat(stats.getTotalOpportunityAmount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getCustomerOverview: 客户不存在抛 NOT_FOUND")
    void getCustomerOverview_notFound() {
        when(customerRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomerOverview(100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户不存在");
    }

    @Test
    @DisplayName("getCustomerOverview: 返回轻量概要视图")
    void getCustomerOverview_success() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(conversationRepository.findByCustomerIdOrderByLastMessageAtDesc(eq(100L), any())).thenReturn(emptyPage());
        when(opportunityRepository.findAll(any(Specification.class))).thenReturn(List.of());

        ScrmCustomer360Vo result = service.getCustomerOverview(100L);

        assertThat(result).isNotNull();
        assertThat(result.getCustomer().getId()).isEqualTo(100L);
        // 概要视图不查询标签 / 分组 / 时间线, 均为空
        assertThat(result.getTags()).isEmpty();
        assertThat(result.getGroups()).isEmpty();
        assertThat(result.getRecentTimeline()).isEmpty();
        assertThat(result.getStats()).isNotNull();
    }

    @Test
    @DisplayName("batchGetOverview: 空列表返回空结果")
    void batchGetOverview_emptyList() {
        List<ScrmCustomer360Vo> result = service.batchGetOverview(List.of());
        assertThat(result).isEmpty();
        verify(customerRepository, never()).findById(any());
    }

    @Test
    @DisplayName("batchGetOverview: null 列表返回空结果")
    void batchGetOverview_nullList() {
        List<ScrmCustomer360Vo> result = service.batchGetOverview(null);
        assertThat(result).isEmpty();
        verify(customerRepository, never()).findById(any());
    }

    @Test
    @DisplayName("batchGetOverview: 单个客户失败被跳过, 不影响其他客户")
    void batchGetOverview_partialFailure() {
        // 第一个客户不存在, 第二个客户存在
        when(customerRepository.findById(100L)).thenReturn(Optional.empty());
        ScrmCustomerEntity customer = buildCustomerEntity(200L);
        when(customerRepository.findById(200L)).thenReturn(Optional.of(customer));
        when(conversationRepository.findByCustomerIdOrderByLastMessageAtDesc(eq(200L), any())).thenReturn(emptyPage());
        when(opportunityRepository.findAll(any(Specification.class))).thenReturn(List.of());

        List<ScrmCustomer360Vo> result = service.batchGetOverview(List.of(100L, 200L));

        // 仅返回成功的客户
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomer().getId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("getTimeline: 客户不存在抛 NOT_FOUND")
    void getTimeline_notFound() {
        when(customerRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTimeline(100L, null, null, null,
                PageRequest.of(0, 10)))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户不存在");
        verify(customerTimelineRepository, never())
                .findAll(any(Specification.class), any(Pageable.class));
    }

    
}
