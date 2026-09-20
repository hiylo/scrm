/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerGroupEntity;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerGroupMemberRepository;
import org.hiylo.scrm.repository.ScrmCustomerGroupRepository;
import org.hiylo.scrm.repository.ScrmCustomerLifecycleHistoryRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * ScrmCustomerService 单元测试
 * <p>
 * 聚焦客户档案维护 (创建 / 更新 / 查询 / 删除)、生命周期阶段切换、分组成员管理
 * 与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCustomerService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCustomerServiceTest {

    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 客户标签仓库 Mock */
    @Mock
    private ScrmCustomerTagRepository tagRepository;
    /** 客户标签关联仓库 Mock */
    @Mock
    private ScrmTagCustomerRepository tagCustomerRepository;
    /** 客户分组仓库 Mock */
    @Mock
    private ScrmCustomerGroupRepository groupRepository;
    /** 客户分组与客户关联仓库 Mock */
    @Mock
    private ScrmCustomerGroupMemberRepository groupMemberRepository;
    /** 客户生命周期历史记录仓库 Mock */
    @Mock
    private ScrmCustomerLifecycleHistoryRepository lifecycleHistoryRepository;
    /** 数据权限范围服务 Mock */
    @Mock
    private DataScopeService dataScopeService;

    /** 被测服务实例 */
    private ScrmCustomerService service;

    @BeforeEach
    void setUp() {
        service = new ScrmCustomerService(customerRepository, tagRepository, tagCustomerRepository,
                groupRepository, groupMemberRepository, lifecycleHistoryRepository, dataScopeService);
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
        entity.setPlatformCustomerUid("wx_uid_" + id);
        entity.setNickname("张三");
        entity.setOwnerAccountId(10L);
        entity.setLifecycle("ACTIVE");
        entity.setCreateTime(LocalDateTime.now().minusDays(30));
        return entity;
    }

    /**
     * 构造客户创建参数
     */
    private ScrmCustomerDto buildCreateDto() {
        ScrmCustomerDto dto = new ScrmCustomerDto();
        dto.setPlatformType("WECHAT");
        dto.setPlatformCustomerUid("wx_uid_new");
        dto.setNickname("李四");
        dto.setOwnerAccountId(10L);
        return dto;
    }

    @Test
    @DisplayName("createCustomer: 写入归属账号与默认生命周期 NEW 后持久化")
    void createCustomer_success() throws ScrmException {
        when(dataScopeService.getCurrentRole()).thenReturn("ADMIN");
        when(dataScopeService.isReadOnly("ADMIN")).thenReturn(false);
        when(customerRepository.findByPlatformTypeAndPlatformCustomerUidAndOwnerAccountId(
                "WECHAT", "wx_uid_new", 10L)).thenReturn(Optional.empty());
        when(customerRepository.save(any(ScrmCustomerEntity.class)))
                .thenAnswer(inv -> {
                    ScrmCustomerEntity e = inv.getArgument(0);
                    e.setId(100L);
                    return e;
                });

        ScrmCustomerDto result = service.createCustomer(buildCreateDto());

        ArgumentCaptor<ScrmCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerEntity.class);
        verify(customerRepository, times(1)).save(captor.capture());
        ScrmCustomerEntity saved = captor.getValue();
        assertThat(saved.getLifecycle()).isEqualTo("NEW");
        assertThat(saved.getPlatformType()).isEqualTo("WECHAT");
        assertThat(saved.getPlatformCustomerUid()).isEqualTo("wx_uid_new");
        assertThat(saved.getOwnerAccountId()).isEqualTo(10L);
        assertThat(result.getLifecycle()).isEqualTo("NEW");
        assertThat(result.getNickname()).isEqualTo("李四");
    }

    @Test
    @DisplayName("createCustomer: 同账号已存在相同三元组抛 CONFLICT")
    void createCustomer_duplicate() {
        when(dataScopeService.getCurrentRole()).thenReturn("ADMIN");
        when(dataScopeService.isReadOnly("ADMIN")).thenReturn(false);
        ScrmCustomerEntity existed = buildCustomerEntity(100L);
        when(customerRepository.findByPlatformTypeAndPlatformCustomerUidAndOwnerAccountId(
                "WECHAT", "wx_uid_new", 10L)).thenReturn(Optional.of(existed));

        assertThatThrownBy(() -> service.createCustomer(buildCreateDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户已存在");
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCustomer: 非法 lifecycle 值抛 BAD_REQUEST")
    void createCustomer_invalidLifecycle() {
        when(dataScopeService.getCurrentRole()).thenReturn("ADMIN");
        when(dataScopeService.isReadOnly("ADMIN")).thenReturn(false);
        ScrmCustomerDto dto = buildCreateDto();
        dto.setLifecycle("INVALID");

        assertThatThrownBy(() -> service.createCustomer(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("非法的生命周期值");
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCustomer: VIEWER 角色抛 FORBIDDEN")
    void createCustomer_viewerForbidden() {
        when(dataScopeService.getCurrentRole()).thenReturn("VIEWER");
        when(dataScopeService.isReadOnly("VIEWER")).thenReturn(true);

        assertThatThrownBy(() -> service.createCustomer(buildCreateDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("VIEWER");
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCustomer: 部分更新非空字段后持久化")
    void updateCustomer_success() throws ScrmException {
        when(dataScopeService.getCurrentRole()).thenReturn("ADMIN");
        when(dataScopeService.isReadOnly("ADMIN")).thenReturn(false);
        ScrmCustomerEntity entity = buildCustomerEntity(100L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(entity));
        when(customerRepository.save(any(ScrmCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerDto dto = new ScrmCustomerDto();
        dto.setNickname("王五");
        dto.setLifecycle("DORMANT");
        dto.setRemark("沉睡客户");

        ScrmCustomerDto result = service.updateCustomer(100L, dto);

        ArgumentCaptor<ScrmCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerEntity.class);
        verify(customerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("王五");
        assertThat(captor.getValue().getLifecycle()).isEqualTo("DORMANT");
        assertThat(captor.getValue().getRemark()).isEqualTo("沉睡客户");
        assertThat(result.getNickname()).isEqualTo("王五");
    }

    
    @Test
    @DisplayName("deleteCustomer: 级联清理标签赋值与分组成员后删除客户主记录")
    void deleteCustomer_success() throws ScrmException {
        when(dataScopeService.getCurrentRole()).thenReturn("ADMIN");
        when(dataScopeService.isReadOnly("ADMIN")).thenReturn(false);
        ScrmCustomerEntity entity = buildCustomerEntity(100L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(entity));
        ScrmTagCustomerEntity tag = new ScrmTagCustomerEntity();
        tag.setId(1L);
        tag.setCustomerId(100L);
        tag.setTagId(5L);
        when(tagCustomerRepository.findByCustomerId(100L))
                .thenReturn(List.of(tag));

        service.deleteCustomer(100L);

        // 验证级联清理标签赋值
        verify(tagCustomerRepository, times(1)).deleteAll(List.of(tag));
        // 验证级联清理分组成员关系
        verify(groupMemberRepository, times(1)).deleteByCustomerId(100L);
        // 验证删除客户主记录
        verify(customerRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("updateLifecycle: 阶段变更后持久化历史记录")
    void updateLifecycle_success() throws ScrmException {
        when(dataScopeService.getCurrentRole()).thenReturn("ADMIN");
        when(dataScopeService.isReadOnly("ADMIN")).thenReturn(false);
        ScrmCustomerEntity entity = buildCustomerEntity(100L);
        entity.setLifecycle("ACTIVE");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(entity));
        when(customerRepository.save(any(ScrmCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(dataScopeService.getCurrentUserId()).thenReturn("admin01");
        when(dataScopeService.getHeader("X-Username")).thenReturn("管理员");

        ScrmCustomerDto result = service.updateLifecycle(100L, "DORMANT", "客户沉睡");

        ArgumentCaptor<ScrmCustomerEntity> customerCaptor =
                ArgumentCaptor.forClass(ScrmCustomerEntity.class);
        verify(customerRepository, times(1)).save(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getLifecycle()).isEqualTo("DORMANT");
        ArgumentCaptor<ScrmCustomerLifecycleHistoryEntity> historyCaptor =
                ArgumentCaptor.forClass(ScrmCustomerLifecycleHistoryEntity.class);
        verify(lifecycleHistoryRepository, times(1)).save(historyCaptor.capture());
        ScrmCustomerLifecycleHistoryEntity history = historyCaptor.getValue();
        assertThat(history.getCustomerId()).isEqualTo(100L);
        assertThat(history.getPreviousLifecycle()).isEqualTo("ACTIVE");
        assertThat(history.getNewLifecycle()).isEqualTo("DORMANT");
        assertThat(history.getRemark()).isEqualTo("客户沉睡");
        assertThat(history.getOperatorId()).isEqualTo("admin01");
        assertThat(history.getOperatorName()).isEqualTo("管理员");
        assertThat(result.getLifecycle()).isEqualTo("DORMANT");
    }

    @Test
    @DisplayName("updateLifecycle: 阶段未变化时不写历史记录")
    void updateLifecycle_sameValue() throws ScrmException {
        when(dataScopeService.getCurrentRole()).thenReturn("ADMIN");
        when(dataScopeService.isReadOnly("ADMIN")).thenReturn(false);
        ScrmCustomerEntity entity = buildCustomerEntity(100L);
        entity.setLifecycle("ACTIVE");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(entity));

        ScrmCustomerDto result = service.updateLifecycle(100L, "ACTIVE", "无变化");

        verify(customerRepository, never()).save(any());
        verify(lifecycleHistoryRepository, never()).save(any());
        assertThat(result.getLifecycle()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("addToGroup: 客户已存在于分组时跳过写入 (幂等)")
    void addToGroup_idempotent() throws ScrmException {
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        ScrmCustomerGroupEntity group = new ScrmCustomerGroupEntity();
        group.setId(20L);
        group.setGroupName("VIP");
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndCustomerId(20L, 100L)).thenReturn(true);

        service.addToGroup(20L, 100L);

        verify(groupMemberRepository, never()).save(any());
    }

    
}
