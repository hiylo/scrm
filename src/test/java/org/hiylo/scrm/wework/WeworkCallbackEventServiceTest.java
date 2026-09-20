/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WeworkCallbackEventServiceTest.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.wework;

import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.integration.wework.dto.WeworkUserDto;
import org.hiylo.scrm.integration.wework.dto.WeworkExternalContactDto;
import org.hiylo.scrm.integration.wework.service.WeworkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WeworkCallbackEventService 单元测试
 * <p>
 * 验证企微回调事件处理服务的外部联系人变更与通讯录变更分发逻辑,
 * 使用 Mockito 隔离 WeworkService 与 Repository。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("WeworkCallbackEventService 单元测试")
@ExtendWith(MockitoExtension.class)
class WeworkCallbackEventServiceTest {

    /** 企业微信服务 Mock */
    @Mock
    private WeworkService weworkService;

    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 账号仓库 Mock */
    @Mock
    private ScrmAccountRepository accountRepository;

    /** 被测企微回调事件服务实例 */
    @InjectMocks
    private WeworkCallbackEventService eventService;

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== handleExternalContactChange ====================

    @Test
    @DisplayName("handleExternalContactChange_add: add_external_contact 事件新增客户")
    void handleExternalContactChange_add() {
        // ===== Given =====
        WeworkCallbackEventDto event = new WeworkCallbackEventDto();
        event.setChangeType("add_external_contact");
        event.setExternalUserId("ext_user_001");
        event.setUserId("staff_001");

        WeworkExternalContactDto detail = WeworkExternalContactDto.success("ext_user_001", "张三");
        detail.setAvatar("https://example.com/avatar.png");
        when(weworkService.getExternalContactDetail("ext_user_001")).thenReturn(detail);
        when(customerRepository.findByPlatformTypeAndPlatformCustomerUid("wework", "ext_user_001"))
                .thenReturn(Optional.empty());

        // ===== When =====
        eventService.handleExternalContactChange(event);

        // ===== Then =====
        ArgumentCaptor<ScrmCustomerEntity> captor = ArgumentCaptor.forClass(ScrmCustomerEntity.class);
        verify(customerRepository, times(1)).save(captor.capture());

        ScrmCustomerEntity saved = captor.getValue();
        assertThat(saved.getPlatformType()).isEqualTo("wework");
        assertThat(saved.getPlatformCustomerUid()).isEqualTo("ext_user_001");
        assertThat(saved.getNickname()).isEqualTo("张三");
        assertThat(saved.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(saved.getLifecycle()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("handleExternalContactChange_del: del_external_contact 事件标记客户流失")
    void handleExternalContactChange_del() {
        // ===== Given =====
        WeworkCallbackEventDto event = new WeworkCallbackEventDto();
        event.setChangeType("del_external_contact");
        event.setExternalUserId("ext_user_002");

        ScrmCustomerEntity existing = new ScrmCustomerEntity();
        existing.setId(100L);
        existing.setPlatformCustomerUid("ext_user_002");
        existing.setLifecycle("NEW");
        when(customerRepository.findByPlatformTypeAndPlatformCustomerUid("wework", "ext_user_002"))
                .thenReturn(Optional.of(existing));

        // ===== When =====
        eventService.handleExternalContactChange(event);

        // ===== Then =====
        ArgumentCaptor<ScrmCustomerEntity> captor = ArgumentCaptor.forClass(ScrmCustomerEntity.class);
        verify(customerRepository, times(1)).save(captor.capture());

        ScrmCustomerEntity saved = captor.getValue();
        assertThat(saved.getLifecycle()).isEqualTo("LOST");
    }

    @Test
    @DisplayName("handleExternalContactChange_blankExternalUserId: externalUserId 为空时忽略事件")
    void handleExternalContactChange_blankExternalUserId() {
        WeworkCallbackEventDto event = new WeworkCallbackEventDto();
        event.setChangeType("add_external_contact");
        event.setExternalUserId("");

        eventService.handleExternalContactChange(event);

        verify(weworkService, never()).getExternalContactDetail(anyString());
        verify(customerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ==================== handleContactChange ====================

    @Test
    @DisplayName("handleContactChange_createUser: create_user 事件创建企微成员账号")
    void handleContactChange_createUser() {
        // ===== Given =====
        WeworkCallbackEventDto event = new WeworkCallbackEventDto();
        event.setChangeType("create_user");
        event.setUserId("staff_002");

        when(accountRepository.findByPlatformTypeAndPlatformAccountUid("wework", "staff_002"))
                .thenReturn(Optional.empty());

        WeworkUserDto userInfo = WeworkUserDto.builder()
                .platformUserId("staff_002")
                .nickname("李四")
                .avatar("https://example.com/staff_avatar.png")
                .build();
        when(weworkService.getUserDetail("staff_002")).thenReturn(userInfo);

        // ===== When =====
        eventService.handleContactChange(event);

        // ===== Then =====
        ArgumentCaptor<ScrmAccountEntity> captor = ArgumentCaptor.forClass(ScrmAccountEntity.class);
        verify(accountRepository, times(1)).save(captor.capture());

        ScrmAccountEntity saved = captor.getValue();
        assertThat(saved.getPlatformType()).isEqualTo("wework");
        assertThat(saved.getPlatformAccountUid()).isEqualTo("staff_002");
        assertThat(saved.getDisplayName()).isEqualTo("李四");
        assertThat(saved.getAvatarUrl()).isEqualTo("https://example.com/staff_avatar.png");
        assertThat(saved.getLoginState()).isEqualTo("LOGIN");
    }

    @Test
    @DisplayName("handleContactChange_blankUserId: userId 为空时忽略事件")
    void handleContactChange_blankUserId() {
        WeworkCallbackEventDto event = new WeworkCallbackEventDto();
        event.setChangeType("create_user");
        event.setUserId(null);

        eventService.handleContactChange(event);

        verify(accountRepository, never()).findByPlatformTypeAndPlatformAccountUid(anyString(), anyString());
        verify(accountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
