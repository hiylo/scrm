/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAccountServiceTest.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmAccountDto;
import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.entity.ScrmAccountLoginLogEntity;
import org.hiylo.scrm.execution.TaskExecutionService;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountLoginLogRepository;
import org.hiylo.scrm.repository.ScrmAccountRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmAccountService 单元测试
 * <p>
 * 使用 Mockito 验证账号创建、查询、设备绑定、登录态维护等核心逻辑, 不依赖数据库。
 * 通过 {@link MockitoExtension} 注入 Mock 依赖, {@link InjectMocks} 自动构造 Service 实例。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmAccountService 单元测试")
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ScrmAccountServiceTest {

    /** 账号数据访问层 Mock */
    @Mock
    private ScrmAccountRepository accountRepository;

    /** 账号登录日志数据访问层 Mock */
    @Mock
    private ScrmAccountLoginLogRepository loginLogRepository;

    /** 营销任务-账号关联数据访问层 Mock (删除账号时级联清理) */
    @Mock
    private org.hiylo.scrm.repository.ScrmCampaignAccountRepository campaignAccountRepository;

    /** 营销任务执行引擎扩展点 Mock */
    @Mock
    private TaskExecutionService taskExecutionService;

    /** 企业微信服务 Mock */
    @Mock
    private WeworkService weworkService;

    /** 被测对象, 自动注入上述 Mock */
    @InjectMocks
    private ScrmAccountService accountService;

    /**
     * 测试前设置请求上下文 (Service 写入实体时读取当前用户归属账号)
     */
    @BeforeEach
    void setUp() {
    }

    /**
     * 测试后清理请求上下文, 避免线程池复用导致串号
     */
    @AfterEach
    void tearDown() {
    }

    /**
     * 构造测试用账号实体
     */
    private ScrmAccountEntity buildEntity(Long id, String platformType, String accountUid) {
        ScrmAccountEntity entity = new ScrmAccountEntity();
        entity.setId(id);
        entity.setPlatformType(platformType);
        entity.setPlatformAccountUid(accountUid);
        entity.setDisplayName("test-account-" + id);
        entity.setAvatarUrl("https://example.com/avatar/" + id + ".png");
        entity.setLoginState("UNKNOWN");
        return entity;
    }

    @Test
    @DisplayName("createAccount_success: 平台账号不存在时创建成功, 登录态为 UNKNOWN, 记录登录日志")
    void createAccount_success() throws ScrmException {
        // ===== Given =====
        ScrmAccountDto dto = new ScrmAccountDto();
        dto.setPlatformType("DOUYIN");
        dto.setPlatformAccountUid("uid-001");
        dto.setDisplayName("douyin-account-001");
        dto.setAvatarUrl("https://example.com/a.png");

        // 平台账号不存在
        when(accountRepository.findByPlatformTypeAndPlatformAccountUid("DOUYIN", "uid-001"))
                .thenReturn(Optional.empty());
        // save 返回带 id 的实体
        when(accountRepository.save(any(ScrmAccountEntity.class))).thenAnswer(invocation -> {
            ScrmAccountEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        // ===== When =====
        ScrmAccountDto result = accountService.createAccount(dto);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getPlatformType()).isEqualTo("DOUYIN");
        assertThat(result.getPlatformAccountUid()).isEqualTo("uid-001");
        // 新建账号登录态必须为 UNKNOWN
        assertThat(result.getLoginState()).isEqualTo("UNKNOWN");

        // 验证调用了 save 一次
        verify(accountRepository, times(1)).save(any(ScrmAccountEntity.class));

        // 验证记录了登录日志 (fromState=null, toState=UNKNOWN, reason=账号创建)
        ArgumentCaptor<ScrmAccountLoginLogEntity> logCaptor = ArgumentCaptor.forClass(ScrmAccountLoginLogEntity.class);
        verify(loginLogRepository, times(1)).save(logCaptor.capture());
        ScrmAccountLoginLogEntity logEntity = logCaptor.getValue();
        assertThat(logEntity.getAccountId()).isEqualTo(1L);
        assertThat(logEntity.getFromState()).isNull();
        assertThat(logEntity.getToState()).isEqualTo("UNKNOWN");
        assertThat(logEntity.getReason()).isEqualTo("账号创建");
        assertThat(logEntity.getOperateAt()).isNotNull();
    }

    @Test
    @DisplayName("createAccount_duplicate: 平台账号已存在时抛 ScrmException (code=SCRM_CONFLICT)")
    void createAccount_duplicate() {
        // ===== Given =====
        ScrmAccountDto dto = new ScrmAccountDto();
        dto.setPlatformType("DOUYIN");
        dto.setPlatformAccountUid("uid-001");

        // 平台账号已存在
        ScrmAccountEntity existing = buildEntity(99L, "DOUYIN", "uid-001");
        when(accountRepository.findByPlatformTypeAndPlatformAccountUid("DOUYIN", "uid-001"))
                .thenReturn(Optional.of(existing));

        // ===== When / Then =====
        assertThatThrownBy(() -> accountService.createAccount(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("平台账号已存在")
                .hasFieldOrPropertyWithValue("code", "SCRM_CONFLICT");

        // 不应再调用 save
        verify(accountRepository, never()).save(any(ScrmAccountEntity.class));
        // 不应记录登录日志
        verify(loginLogRepository, never()).save(any(ScrmAccountLoginLogEntity.class));
    }

    @Test
    @DisplayName("getAccount_notFound: 主键查询不存在时抛 ScrmException (code=SCRM_ACCOUNT_NOT_FOUND)")
    void getAccount_notFound() {
        // ===== Given =====
        Long id = 404L;
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        // ===== When / Then =====
        assertThatThrownBy(() -> accountService.getAccount(id))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("SCRM 账号不存在")
                .hasFieldOrPropertyWithValue("code", "SCRM_ACCOUNT_NOT_FOUND");

        verify(accountRepository, times(1)).findById(id);
    }

    @Test
    @DisplayName("bindDevice_success: 账号存在时绑定设备成功, deviceId 正确写入")
    void bindDevice_success() throws ScrmException {
        // ===== Given =====
        Long accountId = 10L;
        String deviceId = "device-xyz-001";
        ScrmAccountEntity entity = buildEntity(accountId, "WEWORK", "wework-uid-001");
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(entity));
        when(accountRepository.save(any(ScrmAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ===== When =====
        ScrmAccountDto result = accountService.bindDevice(accountId, deviceId);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(accountId);
        assertThat(result.getDeviceId()).isEqualTo(deviceId);

        // 验证实体 deviceId 已更新
        verify(accountRepository, times(1)).findById(accountId);
        ArgumentCaptor<ScrmAccountEntity> entityCaptor = ArgumentCaptor.forClass(ScrmAccountEntity.class);
        verify(accountRepository, times(1)).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getDeviceId()).isEqualTo(deviceId);
    }

    @Test
    @DisplayName("updateLoginState_success: 账号存在时更新登录态并记录日志, LOGIN 时同步更新 lastLoginAt")
    void updateLoginState_success() throws ScrmException {
        // ===== Given =====
        Long accountId = 20L;
        ScrmAccountEntity entity = buildEntity(accountId, "DOUYIN", "douyin-uid-002");
        entity.setLoginState("LOGOUT");
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(entity));
        when(accountRepository.save(any(ScrmAccountEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ===== When =====
        ScrmAccountDto result = accountService.updateLoginState(accountId, "LOGIN", "手动登录");

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getLoginState()).isEqualTo("LOGIN");
        // LOGIN 状态下应同步更新 lastLoginAt
        assertThat(result.getLastLoginAt()).isNotNull();

        // 验证实体 save 调用
        ArgumentCaptor<ScrmAccountEntity> entityCaptor = ArgumentCaptor.forClass(ScrmAccountEntity.class);
        verify(accountRepository, times(1)).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getLoginState()).isEqualTo("LOGIN");
        assertThat(entityCaptor.getValue().getLastLoginAt()).isNotNull();

        // 验证登录日志记录 (fromState=LOGOUT, toState=LOGIN, reason=手动登录)
        ArgumentCaptor<ScrmAccountLoginLogEntity> logCaptor = ArgumentCaptor.forClass(ScrmAccountLoginLogEntity.class);
        verify(loginLogRepository, times(1)).save(logCaptor.capture());
        ScrmAccountLoginLogEntity logEntity = logCaptor.getValue();
        assertThat(logEntity.getAccountId()).isEqualTo(accountId);
        assertThat(logEntity.getFromState()).isEqualTo("LOGOUT");
        assertThat(logEntity.getToState()).isEqualTo("LOGIN");
        assertThat(logEntity.getReason()).isEqualTo("手动登录");
        assertThat(logEntity.getOperateAt()).isNotNull();
    }
}
