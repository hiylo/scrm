/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AuthServiceImplTest.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.auth.impl;

import org.hiylo.scrm.auth.JwtTokenProvider;
import org.hiylo.scrm.dto.auth.LoginRequestDto;
import org.hiylo.scrm.dto.auth.LoginResponseDto;
import org.hiylo.scrm.entity.ScrmUserEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 单元测试。
 * <p>
 * 覆盖登录凭证校验/账号禁用/lastLoginAt 回写与令牌签发、按 uid 查询当前用户的正常与异常分支。
 * 公开注册已关闭, 不再有注册用例。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("AuthServiceImpl 认证服务单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    /** 密码加密器 Mock */
    @Mock
    private PasswordEncoder passwordEncoder;

    /** JWT 组件 Mock */
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    /** 用户数据访问层 Mock */
    @Mock
    private ScrmUserRepository userRepository;

    /** 被测服务 */
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtTokenProvider);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== login ====================

    @Test
    @DisplayName("登录成功: 签发令牌, 回写 lastLoginAt, 响应字段完整")
    void login_success_tokenIssuedAndLastLoginUpdated() {
        ScrmUserEntity existing = existingUser("alice", "$2a$10$hashed", 1, 200L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("Passw0rd!", "$2a$10$hashed")).thenReturn(true);
        when(jwtTokenProvider.issueToken(existing)).thenReturn("jwt-token");
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(86400L);

        LoginResponseDto response = authService.login(loginDto("alice", "Passw0rd!"));

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(86400L);
        assertThat(response.getUserId()).isEqualTo(200L);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRoles()).containsExactly("OPERATOR");
        assertThat(existing.getLastLoginAt()).isNotNull();
        verify(userRepository).save(existing);
    }

    @Test
    @DisplayName("登录: 用户不存在抛 401 且不签发令牌")
    void login_unknownUser_unauthorized() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginDto("ghost", "Passw0rd!")))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException e = (ScrmException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).contains("用户名或密码错误");
                });
        verify(jwtTokenProvider, never()).issueToken(any());
    }

    @Test
    @DisplayName("登录: 口令不匹配抛 401 且不签发令牌")
    void login_wrongPassword_unauthorized() {
        ScrmUserEntity existing = existingUser("alice", "$2a$10$hashed", 1, 200L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong-pass", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginDto("alice", "wrong-pass")))
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.UNAUTHORIZED);
        verify(jwtTokenProvider, never()).issueToken(any());
    }

    @Test
    @DisplayName("登录: 账号禁用抛 401 且不签发令牌")
    void login_disabledUser_unauthorized() {
        ScrmUserEntity existing = existingUser("alice", "$2a$10$hashed", 0, 200L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("Passw0rd!", "$2a$10$hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginDto("alice", "Passw0rd!")))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException e = (ScrmException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).contains("账号已禁用");
                });
        verify(jwtTokenProvider, never()).issueToken(any());
    }

    @Test
    @DisplayName("登录: 用户名前后空格被 trim 后查询")
    void login_usernameTrimmed() {
        ScrmUserEntity existing = existingUser("alice", "$2a$10$hashed", 1, 200L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtTokenProvider.issueToken(existing)).thenReturn("jwt-token");
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(86400L);

        authService.login(loginDto("  alice  ", "Passw0rd!"));

        verify(userRepository).findByUsername("alice");
    }

    // ==================== getCurrentUser ====================

    @Test
    @DisplayName("getCurrentUser: 按 uid 查找到用户")
    void getCurrentUser_found() {
        ScrmUserEntity existing = existingUser("alice", "$2a$10$hashed", 1, 200L);
        when(userRepository.findById(200L)).thenReturn(Optional.of(existing));

        ScrmUserEntity found = authService.getCurrentUser("200");

        assertThat(found.getUsername()).isEqualTo("alice");
        assertThat(found.getId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("getCurrentUser: 用户不存在抛 401")
    void getCurrentUser_notFound_unauthorized() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser("999"))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException e = (ScrmException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).contains("用户不存在");
                });
    }

    @Test
    @DisplayName("getCurrentUser: userId 为空/null 抛 401 且不查库")
    void getCurrentUser_blankId_unauthorized() {
        assertThatThrownBy(() -> authService.getCurrentUser(null))
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.UNAUTHORIZED);
        assertThatThrownBy(() -> authService.getCurrentUser("   "))
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.UNAUTHORIZED);
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("getCurrentUser: userId 非数字抛 401 且不查库")
    void getCurrentUser_malformedId_unauthorized() {
        assertThatThrownBy(() -> authService.getCurrentUser("not-a-number"))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException e = (ScrmException) ex;
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).contains("格式非法");
                });
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("getCurrentUser: userId 含前后空格被 trim 后解析")
    void getCurrentUser_idTrimmed() {
        ScrmUserEntity existing = existingUser("alice", "$2a$10$hashed", 1, 200L);
        when(userRepository.findById(200L)).thenReturn(Optional.of(existing));

        assertThat(authService.getCurrentUser(" 200 ").getUsername()).isEqualTo("alice");
        verify(userRepository).findById(eq(200L));
    }

    // ==================== 测试数据构造 ====================

    /**
     * 构造登录请求 DTO。
     *
     * @param username 用户名
     * @param password 口令明文
     * @return 登录请求 DTO
     */
    private static LoginRequestDto loginDto(String username, String password) {
        LoginRequestDto dto = new LoginRequestDto();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    /**
     * 构造已存在的用户实体。
     *
     * @param username   用户名
     * @param password   BCrypt 密文
     * @param status     状态 (可为 null)
     * @param userId     用户 ID
     * @return 用户实体
     */
    private static ScrmUserEntity existingUser(String username, String password,
                                                Integer status, Long userId) {
        ScrmUserEntity entity = new ScrmUserEntity();
        entity.setId(userId);
        entity.setUsername(username);
        entity.setPassword(password);
        entity.setRoles("OPERATOR");
        entity.setStatus(status);
        return entity;
    }
}
