/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AuthServiceImpl.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.auth.AuthService;
import org.hiylo.scrm.auth.JwtTokenProvider;

import org.hiylo.scrm.dto.auth.LoginRequestDto;
import org.hiylo.scrm.dto.auth.LoginResponseDto;
import org.hiylo.scrm.entity.ScrmUserEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 认证服务实现。
 * <p>
 * 用户名唯一性依赖 {@code scrm_user.username} 唯一索引兜底; 密码强度使用
 * {@link #PASSWORD_PATTERN} 静态正则校验; 口令只以 BCrypt 密文落库, 明文不出现在日志中。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 账号状态: 启用 */
    private static final int STATUS_ENABLED = 1;

    /** 令牌类型标识 */
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    /** SCRM 登录用户数据访问层 */
    private final ScrmUserRepository userRepository;

    /** 口令加密器 (BCrypt) */
    private final PasswordEncoder passwordEncoder;

    /** JWT 令牌签发与解析组件 */
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {
        ScrmUserEntity user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> ScrmException.unauthorized("用户名或密码错误"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw ScrmException.unauthorized("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw ScrmException.unauthorized("账号已禁用: " + user.getUsername());
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        String token = jwtTokenProvider.issueToken(user);
        List<String> roles = splitRoles(user.getRoles());
        log.info("用户登录成功: username={}, roles={}",
                user.getUsername(), roles);

        return LoginResponseDto.builder()
                .accessToken(token)
                .tokenType(TOKEN_TYPE_BEARER)
                .expiresIn(jwtTokenProvider.getExpirationSeconds())
                .userId(user.getId())
                .username(user.getUsername())
                .roles(roles)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ScrmUserEntity getCurrentUser(String userId) {
        Long uid = parseUserId(userId);
        return userRepository.findById(uid)
                .orElseThrow(() -> ScrmException.unauthorized("用户不存在或令牌已失效: uid=" + uid));
    }

    /**
     * 将 JWT 声明中的 uid 解析为 Long, 格式非法时按未认证处理。
     *
     * @param userId 用户 ID 字符串, 可为 null
     * @return 解析后的用户 ID
     * @throws ScrmException 缺失或格式非法 (401)
     */
    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw ScrmException.unauthorized("未提供用户 ID");
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            throw ScrmException.unauthorized("用户 ID 格式非法: " + userId);
        }
    }

    /**
     * 将逗号分隔的角色字符串拆为角色列表。
     *
     * @param rolesCsv 逗号分隔的角色字符串, 可为 null
     * @return 角色列表, 无角色返回空列表
     */
    private static List<String> splitRoles(String rolesCsv) {
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rolesCsv.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();
    }
}
