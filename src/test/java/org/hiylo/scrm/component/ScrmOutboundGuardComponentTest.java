/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOutboundGuardComponentTest.java
 * Date : 2026/10/09 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.component;

import org.hiylo.scrm.dto.ScrmRiskEventDto;
import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmOutboundMessageRepository;
import org.hiylo.scrm.service.ScrmAccountService;
import org.hiylo.scrm.service.ScrmBlacklistRuleService;
import org.hiylo.scrm.service.ScrmBlacklistService;
import org.hiylo.scrm.service.ScrmRiskEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmOutboundGuardComponent} 单元测试。
 * <p>
 * 覆盖四条守卫路径: 账号冻结拦截 / 账号离线拦截 / 目标黑名单拦截 / 发送频率超限拦截 /
 * 全部通过时放行。拦截时应记录 SEND_BLOCKED 风险事件且不抛异常。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmOutboundGuardComponent 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScrmOutboundGuardComponentTest {

    /** 账号仓库 Mock */
    @Mock
    private ScrmAccountRepository accountRepository;
    /** 出站队列仓库 Mock */
    @Mock
    private ScrmOutboundMessageRepository outboundRepository;
    /** 黑名单服务 Mock */
    @Mock
    private ScrmBlacklistService blacklistService;
    /** 黑名单规则服务 Mock (内容敏感词) */
    @Mock
    private ScrmBlacklistRuleService blacklistRuleService;
    /** 风险事件服务 Mock */
    @Mock
    private ScrmRiskEventService riskEventService;
    /** 账号服务 Mock (自动暂停) */
    @Mock
    private ScrmAccountService accountService;

    /** 被测守卫 */
    private ScrmOutboundGuardComponent guard;

    /** 账号实体 (默认 LOGIN) */
    private ScrmAccountEntity account(Long id, String loginState) {
        ScrmAccountEntity a = new ScrmAccountEntity();
        a.setId(id);
        a.setLoginState(loginState);
        return a;
    }

    @BeforeEach
    void setUp() {
        guard = new ScrmOutboundGuardComponent(accountRepository, outboundRepository,
                blacklistService, blacklistRuleService, riskEventService, accountService);
        // @Value 字段单元测试默认值为 0/false, 需反射设置生产默认值
        org.springframework.test.util.ReflectionTestUtils.setField(guard, "rateWindowSeconds", 60L);
        org.springframework.test.util.ReflectionTestUtils.setField(guard, "maxPerWindow", 30L);
        org.springframework.test.util.ReflectionTestUtils.setField(guard, "blockOffline", true);
        org.springframework.test.util.ReflectionTestUtils.setField(guard, "autoPauseOnBlock", true);
    }

    @Test
    @DisplayName("guard: 账号冻结 → 拦截 ACCOUNT_FROZEN 并记录风险事件")
    void guard_frozenAccount() {
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account(100L, "FROZEN")));

        ScrmOutboundGuardComponent.GuardResult result =
                guard.guard("wechat_personal", 100L, "wxid_peer", "你好");

        assertThat(result.allowed()).isFalse();
        assertThat(result.signal()).isEqualTo("ACCOUNT_FROZEN");
        verify(riskEventService, times(1)).createEvent(any(ScrmRiskEventDto.class));
        // 已冻结账号不重复触发暂停
        verify(accountService, never()).updateLoginState(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("guard: 账号离线且开启拦截 → 拦截 ACCOUNT_OFFLINE")
    void guard_offlineAccount() {
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account(100L, "LOGOUT")));

        ScrmOutboundGuardComponent.GuardResult result =
                guard.guard("wechat_personal", 100L, "wxid_peer", "你好");

        assertThat(result.allowed()).isFalse();
        assertThat(result.signal()).isEqualTo("ACCOUNT_OFFLINE");
    }

    @Test
    @DisplayName("guard: 目标客户在黑名单 → 拦截 TARGET_BLACKLISTED")
    void guard_targetBlacklisted() {
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account(100L, "LOGIN")));
        when(blacklistService.checkBlacklist(any()))
                .thenReturn(Map.of("inBlacklist", true, "listType", "BLACK"));

        ScrmOutboundGuardComponent.GuardResult result =
                guard.guard("wechat_personal", 100L, "wxid_bad", "你好");

        assertThat(result.allowed()).isFalse();
        assertThat(result.signal()).isEqualTo("TARGET_BLACKLISTED");
        // 目标黑名单非账号风险, 不自动暂停账号
        verify(accountService, never()).updateLoginState(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("guard: 发送频率超限 → 拦截 SEND_RATE_LIMIT")
    void guard_rateLimit() {
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account(100L, "LOGIN")));
        when(blacklistService.checkBlacklist(any()))
                .thenReturn(Map.of("inBlacklist", false));
        when(outboundRepository.countByAccountIdAndStatusInAndCreateTimeAfter(
                anyLong(), any(), any(LocalDateTime.class))).thenReturn(30L);

        ScrmOutboundGuardComponent.GuardResult result =
                guard.guard("wechat_personal", 100L, "wxid_peer", "你好");

        assertThat(result.allowed()).isFalse();
        assertThat(result.signal()).isEqualTo("SEND_RATE_LIMIT");
        // 拦截原因含频率详情
        assertThat(result.reason()).contains("频率");
        // 自动处置: 频率超限 → 自动暂停账号
        verify(accountService).updateLoginState(eq(100L), eq("FROZEN"), anyString());
    }


    @Test
    @DisplayName("guard: 发送内容命中敏感词规则 → 拦截 SENSITIVE_CONTENT")
    void guard_sensitiveContent() {
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account(100L, "LOGIN")));
        when(blacklistService.checkBlacklist(any()))
                .thenReturn(Map.of("inBlacklist", false));
        when(outboundRepository.countByAccountIdAndStatusInAndCreateTimeAfter(
                anyLong(), any(), any(LocalDateTime.class))).thenReturn(5L);
        when(blacklistRuleService.evaluateAllRules(any()))
                .thenReturn(Map.of("totalTriggered", 2, "maxSeverity", "HIGH",
                        "recommendedAction", "BLOCK", "triggeredRules", java.util.List.of()));

        ScrmOutboundGuardComponent.GuardResult result =
                guard.guard("wechat_personal", 100L, "wxid_peer", "含敏感词的内容");

        assertThat(result.allowed()).isFalse();
        assertThat(result.signal()).isEqualTo("SENSITIVE_CONTENT");
        // 自动处置: 敏感词命中 → 自动暂停账号
        verify(accountService).updateLoginState(eq(100L), eq("FROZEN"), anyString());
    }

    @Test
    @DisplayName("guard: 内容规则评估异常 → 降级放行 (防故障蔓延)")
    void guard_sensitiveRuleErrorDegradesToAllow() {
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account(100L, "LOGIN")));
        when(blacklistService.checkBlacklist(any()))
                .thenReturn(Map.of("inBlacklist", false));
        when(outboundRepository.countByAccountIdAndStatusInAndCreateTimeAfter(
                anyLong(), any(), any(LocalDateTime.class))).thenReturn(5L);
        when(blacklistRuleService.evaluateAllRules(any()))
                .thenThrow(new RuntimeException("规则引擎不可用"));

        ScrmOutboundGuardComponent.GuardResult result =
                guard.guard("wechat_personal", 100L, "wxid_peer", "普通内容");

        assertThat(result.allowed()).isTrue();
    }
    @Test
    @DisplayName("guard: 自动暂停账号异常 → 拦截决定不受影响 (降级)")
    void guard_autoPauseErrorDegradesToBlock() {
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account(100L, "LOGIN")));
        when(blacklistService.checkBlacklist(any()))
                .thenReturn(Map.of("inBlacklist", false));
        when(outboundRepository.countByAccountIdAndStatusInAndCreateTimeAfter(
                anyLong(), any(), any(LocalDateTime.class))).thenReturn(30L);
        org.mockito.Mockito.doThrow(new RuntimeException("账号状态更新失败"))
                .when(accountService).updateLoginState(anyLong(), anyString(), anyString());

        ScrmOutboundGuardComponent.GuardResult result =
                guard.guard("wechat_personal", 100L, "wxid_peer", "你好");

        assertThat(result.allowed()).isFalse();
        assertThat(result.signal()).isEqualTo("SEND_RATE_LIMIT");
    }

    @Test
    @DisplayName("guard: 全部通过 → 放行且不记录风险事件")
    void guard_allow() {
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account(100L, "LOGIN")));
        when(blacklistService.checkBlacklist(any()))
                .thenReturn(Map.of("inBlacklist", false));
        when(outboundRepository.countByAccountIdAndStatusInAndCreateTimeAfter(
                anyLong(), any(), any(LocalDateTime.class))).thenReturn(5L);

        ScrmOutboundGuardComponent.GuardResult result =
                guard.guard("wechat_personal", 100L, "wxid_peer", "你好");

        assertThat(result.allowed()).isTrue();
        verify(riskEventService, never()).createEvent(any(ScrmRiskEventDto.class));
    }

    @Test
    @DisplayName("guard: 黑名单服务异常 → 降级放行 (防故障蔓延)")
    void guard_blacklistServiceErrorDegradesToAllow() {
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account(100L, "LOGIN")));
        when(blacklistService.checkBlacklist(any())).thenThrow(new RuntimeException("黑名单服务不可用"));
        when(outboundRepository.countByAccountIdAndStatusInAndCreateTimeAfter(
                anyLong(), any(), any(LocalDateTime.class))).thenReturn(5L);

        ScrmOutboundGuardComponent.GuardResult result =
                guard.guard("wechat_personal", 100L, "wxid_peer", "你好");

        assertThat(result.allowed()).isTrue();
        verify(riskEventService, never()).createEvent(any(ScrmRiskEventDto.class));
    }
}
