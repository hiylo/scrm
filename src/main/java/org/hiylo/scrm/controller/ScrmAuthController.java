/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAuthController.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.auth.AuthService;
import org.hiylo.scrm.auth.JwtTokenProvider;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.auth.LoginRequestDto;
import org.hiylo.scrm.dto.auth.LoginResponseDto;
import org.hiylo.scrm.dto.auth.RegisterRequestDto;

import org.hiylo.scrm.entity.ScrmUserEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.security.JwtAuthenticationFilter;

import org.hiylo.scrm.common.OperationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * SCRM 认证控制器。
 * <p>
 * 提供登录、注册与当前登录用户信息查询接口。认证由本服务自建的 JWT 体系承担
 * (见 {@code JwtAuthenticationFilter}), 不再依赖网关验签。
 * </p>
 * <ul>
 *   <li>{@code POST /scrm/auth/login} - 登录, 返回访问令牌</li>
 *   <li>{@code POST /scrm/auth/register} - 注册新用户</li>
 *   <li>{@code GET /scrm/auth/me} - 当前登录用户信息 (含账号 ID 与角色)</li>
 * </ul>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/auth")
@RequiredArgsConstructor
public class ScrmAuthController {

    /** 认证服务 (注册 / 登录 / 当前用户) */
    private final AuthService authService;

    /** JWT 签发与解析组件, 用于 {@code /me} 读取令牌中的角色声明 */
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 登录并签发访问令牌。
     *
     * @param request 登录请求
     * @return 登录响应 (含 accessToken / tokenType / expiresIn / userId / username / roles)
     * @throws ScrmException 凭证错误 (401)
     */
    @PostMapping("/login")
    public OperationResponse<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return OperationResponse.build(authService.login(request));
    }

    /**
     * 注册新用户 (默认角色 OPERATOR, 默认账号取当前请求上下文)。
     *
     * @param request 注册请求
     * @return 当前用户信息视图
     * @throws ScrmException 用户名重复 (409) 或密码强度不足 (400)
     */
    @PostMapping("/register")
    public OperationResponse<CurrentUserInfoDto> register(@Valid @RequestBody RegisterRequestDto request) {
        ScrmUserEntity user = authService.register(request);
        CurrentUserInfoDto dto = new CurrentUserInfoDto();
        dto.setUserId(String.valueOf(user.getId()));
        dto.setUsername(user.getUsername());
        dto.setRoles(user.getRoles());
        return OperationResponse.build(dto);
    }

    /**
     * 获取当前登录用户信息 (含账号 ID 与角色)。
     * <p>
     * 账号 ID 解析优先级:
     * <ol>
         *   <li>scrm_user 表 (按 userId 查询, 取用户所属账号)</li>
         *   <li>默认账号 (0)</li>
     * </ol>
     * 角色来自 JWT 声明, 用于前端做菜单与按钮级渲染。
     * </p>
     *
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public OperationResponse<CurrentUserInfoDto> me() {
        String userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        String roles = currentRoles();

        CurrentUserInfoDto dto = new CurrentUserInfoDto();
        dto.setUserId(userId);
        dto.setUsername(username);
        dto.setRoles(roles);
        return OperationResponse.build(dto);
    }

    /**
     * 从当前请求的 JWT 令牌中提取角色字符串, 无有效令牌时返回 null。
     *
     * @return 逗号分隔的角色字符串, 无有效令牌返回 null
     */
    private String currentRoles() {
        String token = extractToken();
        if (token == null) {
            return null;
        }
        try {
            return String.join(",", jwtTokenProvider.parse(token).roles());
        } catch (Exception e) {
            log.debug("读取 JWT 角色声明失败: error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 从当前请求读取 {@code Authorization: Bearer} 令牌, 无请求上下文或缺失时返回 null。
     *
     * @return 令牌字符串, 可为 null
     */
    private String extractToken() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String header = request.getHeader(JwtAuthenticationFilter.HEADER_AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 当前用户信息 DTO
     *
     * @author Hsi Chu
     */
    @Data
    public static class CurrentUserInfoDto {
        /** 用户 ID */
        private String userId;
        /** 用户名 */
        private String username;
        /** 角色列表 (逗号分隔, 如 ADMIN,OPERATOR) */
        private String roles;
    }
}
