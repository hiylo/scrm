/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmCommunityDto;
import org.hiylo.scrm.dto.ScrmCommunityMemberDto;
import org.hiylo.scrm.entity.ScrmCommunityEntity;
import org.hiylo.scrm.entity.ScrmCommunityMemberEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCommunityMemberRepository;
import org.hiylo.scrm.repository.ScrmCommunityMessageRepository;
import org.hiylo.scrm.repository.ScrmCommunityRepository;
import org.hiylo.scrm.repository.ScrmCommunitySopRepository;
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

import java.util.Arrays;
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
 * ScrmCommunityService 单元测试
 * <p>
 * 聚焦社群管理 (创建 / 更新 / 越权校验)、群成员管理 (加入默认值 / 移除状态流转)、
 * 分页查询与社群统计 (成员数 / 活跃率 / 消息趋势) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCommunityService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCommunityServiceTest {

    /** 社群数据仓库 Mock 桩 */
    @Mock
    private ScrmCommunityRepository communityRepository;
    /** 社群成员数据仓库 Mock 桩 */
    @Mock
    private ScrmCommunityMemberRepository memberRepository;
    /** 社群 SOP 数据仓库 Mock 桩 */
    @Mock
    private ScrmCommunitySopRepository sopRepository;
    /** 社群消息数据仓库 Mock 桩 */
    @Mock
    private ScrmCommunityMessageRepository messageRepository;

    /** 被测服务实例 */
    private ScrmCommunityService service;

    @BeforeEach
    void setUp() {
        service = new ScrmCommunityService(communityRepository, memberRepository, sopRepository, messageRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的社群实体 (用于 findById 返回)
     */
    private ScrmCommunityEntity buildCommunityEntity(Long id, String status) {
        ScrmCommunityEntity entity = new ScrmCommunityEntity();
        entity.setId(id);
        entity.setCommunityName("测试社群");
        entity.setPlatformType("WECHAT");
        entity.setCommunityType("CUSTOMER");
        entity.setOwnerId("owner-001");
        entity.setStatus(status);
        entity.setMemberCount(10);
        entity.setMaxMembers(500);
        entity.setActiveMembers(8);
        entity.setTodayNewMembers(2);
        entity.setTodayMessages(20);
        entity.setActivityScore(50.0);
        return entity;
    }

    @Test
    @DisplayName("createCommunity: 写入归属账号与默认值后持久化")
    void createCommunity_success() throws ScrmException {
        ScrmCommunityDto dto = new ScrmCommunityDto();
        dto.setCommunityName("新社群");
        dto.setPlatformType("WECHAT");
        dto.setCommunityType("CUSTOMER");
        dto.setOwnerId("owner-001");
        when(communityRepository.save(any(ScrmCommunityEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCommunityDto result = service.createCommunity(dto);

        ArgumentCaptor<ScrmCommunityEntity> captor =
                ArgumentCaptor.forClass(ScrmCommunityEntity.class);
        verify(communityRepository, times(1)).save(captor.capture());
        ScrmCommunityEntity saved = captor.getValue();
        // 默认 status=ACTIVE
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        // 默认 memberCount=0
        assertThat(saved.getMemberCount()).isZero();
        // 默认 maxMembers=500
        assertThat(saved.getMaxMembers()).isEqualTo(500);
        // 默认 activeMembers=0
        assertThat(saved.getActiveMembers()).isZero();
        // 默认 activityScore=0.0
        assertThat(saved.getActivityScore()).isEqualTo(0.0);
        // 默认 createdAt 由当前时间填充
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(result.getCommunityName()).isEqualTo("新社群");
    }

    @Test
    @DisplayName("createCommunity: 参数为空抛 BAD_REQUEST")
    void createCommunity_nullDto() {
        assertThatThrownBy(() -> service.createCommunity(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("社群参数不能为空");
        verify(communityRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCommunity: 字段非空才覆盖, 保留未提供字段原值")
    void updateCommunity_partialUpdate() throws ScrmException {
        ScrmCommunityEntity entity = buildCommunityEntity(10L, "ACTIVE");
        when(communityRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(communityRepository.save(any(ScrmCommunityEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCommunityDto dto = new ScrmCommunityDto();
        dto.setCommunityName("更新后的社群名");
        dto.setMaxMembers(1000);
        ScrmCommunityDto result = service.updateCommunity(10L, dto);

        assertThat(result.getCommunityName()).isEqualTo("更新后的社群名");
        assertThat(result.getMaxMembers()).isEqualTo(1000);
        // 未提供的字段保留原值
        assertThat(result.getPlatformType()).isEqualTo("WECHAT");
        assertThat(result.getCommunityType()).isEqualTo("CUSTOMER");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    
    @Test
    @DisplayName("listCommunities: 分页查询返回社群列表")
    void listCommunities_paged() {
        ScrmCommunityEntity entity = buildCommunityEntity(10L, "ACTIVE");
        Page<ScrmCommunityEntity> page = new PageImpl<>(List.of(entity));
        when(communityRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(page);

        Page<ScrmCommunityDto> result =
                service.listCommunities("WECHAT", null, "ACTIVE", null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCommunityName()).isEqualTo("测试社群");
        verify(communityRepository, times(1)).findAll(
                any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("addMember: 写入默认角色/状态, 社群成员数 +1 与今日新增 +1")
    void addMember_success() throws ScrmException {
        ScrmCommunityEntity community = buildCommunityEntity(10L, "ACTIVE");
        community.setMemberCount(5);
        community.setTodayNewMembers(1);
        when(communityRepository.findById(10L)).thenReturn(Optional.of(community));
        when(memberRepository.save(any(ScrmCommunityMemberEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(communityRepository.save(any(ScrmCommunityEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCommunityMemberDto dto = new ScrmCommunityMemberDto();
        dto.setCommunityId(10L);
        dto.setMemberName("张三");
        ScrmCommunityMemberDto result = service.addMember(dto);

        ArgumentCaptor<ScrmCommunityMemberEntity> memberCaptor =
                ArgumentCaptor.forClass(ScrmCommunityMemberEntity.class);
        verify(memberRepository, times(1)).save(memberCaptor.capture());
        ScrmCommunityMemberEntity saved = memberCaptor.getValue();
        // 默认 role=MEMBER
        assertThat(saved.getRole()).isEqualTo("MEMBER");
        // 默认 joinType=INVITED
        assertThat(saved.getJoinType()).isEqualTo("INVITED");
        // 默认 status=ACTIVE
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        // 默认 isActive=true
        assertThat(saved.getIsActive()).isTrue();
        // 默认 messageCount=0
        assertThat(saved.getMessageCount()).isZero();
        // 默认 joinAt 由当前时间填充
        assertThat(saved.getJoinAt()).isNotNull();
        // 社群成员数 +1
        ArgumentCaptor<ScrmCommunityEntity> communityCaptor =
                ArgumentCaptor.forClass(ScrmCommunityEntity.class);
        verify(communityRepository, times(1)).save(communityCaptor.capture());
        assertThat(communityCaptor.getValue().getMemberCount()).isEqualTo(6);
        // 今日新增 +1
        assertThat(communityCaptor.getValue().getTodayNewMembers()).isEqualTo(2);
        assertThat(result.getMemberName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("addMember: 成员名称为空抛 BAD_REQUEST")
    void addMember_blankName() {
        ScrmCommunityMemberDto dto = new ScrmCommunityMemberDto();
        dto.setCommunityId(10L);
        dto.setMemberName("");

        assertThatThrownBy(() -> service.addMember(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("成员名称不能为空");
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeMember: 状态置 REMOVED 并递减社群成员数")
    void removeMember_success() throws ScrmException {
        ScrmCommunityMemberEntity member = new ScrmCommunityMemberEntity();
        member.setId(20L);
        member.setCommunityId(10L);
        member.setStatus("ACTIVE");
        member.setIsActive(true);
        ScrmCommunityEntity community = buildCommunityEntity(10L, "ACTIVE");
        community.setMemberCount(5);
        when(memberRepository.findById(20L)).thenReturn(Optional.of(member));
        when(memberRepository.save(any(ScrmCommunityMemberEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(communityRepository.findById(10L)).thenReturn(Optional.of(community));
        when(communityRepository.save(any(ScrmCommunityEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.removeMember(20L, "违规操作");

        ArgumentCaptor<ScrmCommunityMemberEntity> memberCaptor =
                ArgumentCaptor.forClass(ScrmCommunityMemberEntity.class);
        verify(memberRepository, times(1)).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getStatus()).isEqualTo("REMOVED");
        assertThat(memberCaptor.getValue().getIsActive()).isFalse();
        assertThat(memberCaptor.getValue().getLeftAt()).isNotNull();
        ArgumentCaptor<ScrmCommunityEntity> communityCaptor =
                ArgumentCaptor.forClass(ScrmCommunityEntity.class);
        verify(communityRepository, times(1)).save(communityCaptor.capture());
        // 成员数 -1
        assertThat(communityCaptor.getValue().getMemberCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("getCommunityStats: 返回成员数 / 活跃率 / 消息趋势")
    void getCommunityStats_success() throws ScrmException {
        ScrmCommunityEntity entity = buildCommunityEntity(10L, "ACTIVE");
        when(communityRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(messageRepository.dailyMessageStats(eq(10L), any(), any()))
                .thenReturn(Arrays.asList(
                        new Object[]{"2026-08-01", 10L},
                        new Object[]{"2026-08-02", 5L}));

        Map<String, Object> stats = service.getCommunityStats(10L);

        assertThat(stats.get("communityId")).isEqualTo(10L);
        assertThat(stats.get("memberCount")).isEqualTo(10);
        assertThat(stats.get("activeMembers")).isEqualTo(8);
        // activeRate = 8/10 = 0.8
        assertThat((Double) stats.get("activeRate")).isEqualTo(0.8);
        // 消息趋势 7 天, dailyMessageStats 返回 2 条
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) stats.get("messageTrend");
        assertThat(trend).hasSize(2);
        verify(messageRepository, times(1)).dailyMessageStats(eq(10L), any(), any());
    }
}
