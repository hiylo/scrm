/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : JwtAuthenticationFilter.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.auth.JwtTokenProvider;
import org.hiylo.scrm.auth.JwtTokenProvider.JwtClaims;

import org.hiylo.scrm.config.UserContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器。
 * <p>
 * 从 {@code Authorization: Bearer <token>} 解析并校验 JWT, 校验通过后:
 * <ol>
 *   <li>写入 {@link UserContext} ThreadLocal, 供 Service 层读取操作人;</li>
 *   <li>写入 {@link SecurityContextHolder}, 供 {@code SecurityConfig} 的 {@code authenticated()}
 *       授权规则与角色表达式生效;</li>
 *   <li>将可信用户 ID 与角色列表写入 request attribute, 供 {@code RequirePermissionAspect}
 *       做角色校验。</li>
 * </ol>
 * </p>
 * <p>
 * <b>兼容模式</b>: 未携带 {@code Authorization} 头但携带 {@code X-User-Id} / {@code X-User-Name} 时,
 * 按旧网关注入头的语义填充上下文, 便于内网直连调试。该模式不校验任何签名,
 * 客户端可伪造, 仅限内网环境使用, 因此不写入可信用户 attribute。
 * </p>
 * <p>
 * {@code finally} 中统一清理 ThreadLocal 与 SecurityContext, 避免线程池复用导致的身份串号。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 校验通过后写入 request attribute 的可信用户 ID 键名 */
    public static final String VERIFIED_USER_ATTR = "jwtVerifiedUserId";

    /** 校验通过后写入 request attribute 的角色列表键名 */
    public static final String VERIFIED_ROLES_ATTR = "jwtVerifiedRoles";

    /** 授权头名称 */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** Bearer 令牌前缀 */
    private static final String BEARER_PREFIX = "Bearer";

    /** 角色权限前缀 (Spring Security 约定) */
    private static final String ROLE_PREFIX = "ROLE_";

    /** 兼容模式请求头: 用户 ID */
    private static final String HEADER_USER_ID = "X-User-Id";

    /** 兼容模式请求头: 用户名 */
    private static final String HEADER_USER_NAME = "X-User-Name";

    /** 兼容模式请求头: 角色列表 (逗号分隔) */
    /** 兼容模式请求头: 角色列表 (逗号分隔) */
    private static final String HEADER_USER_ROLE = "X-User-Role";

    /** JWT 签发与解析组件 */
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 执行认证过滤: 解析令牌并填充上下文, 无论是否认证成功都在 finally 中清理上下文。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException 过滤器链异常
     * @throws IOException      过滤器链异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            HttpServletRequest effectiveRequest = request;
            if (token != null) {
                effectiveRequest = authenticateByToken(request, token);
            } else {
                authenticateByCompatHeaders(request);
            }
            filterChain.doFilter(effectiveRequest, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * JWT 模式认证: 校验失败仅记录日志并继续放行, 由 {@code SecurityConfig} 的授权规则对受保护
     * 端点返回 401, 从而保证 {@code /scrm/auth/login} 等免认证端点在令牌失效时仍可用。
     *
     * @param request HTTP 请求
     * @param token   JWT 令牌字符串
     * @return 已注入身份头的请求包装器 (校验失败时返回原始请求)
     */
    private HttpServletRequest authenticateByToken(HttpServletRequest request, String token) {
        if (!jwtTokenProvider.validate(token)) {
            log.warn("[JwtAuth] 令牌校验失败, 按匿名请求继续: path={}", request.getRequestURI());
            return request;
        }
        try {
            JwtClaims claims = jwtTokenProvider.parse(token);
            HttpServletRequest wrapped = applyClaims(request, claims);
            if (log.isDebugEnabled()) {
                log.debug("[JwtAuth] 令牌校验通过: uid={}, username={}, roles={}",
                        claims.uid(), claims.username(), claims.roles());
            }
            return wrapped;
        } catch (Exception e) {
            log.warn("[JwtAuth] 令牌解析异常, 按匿名请求继续: path={}, error={}",
                    request.getRequestURI(), e.getMessage());
            return request;
        }
    }

    /**
     * 兼容模式认证: 无 {@code Authorization} 头时按旧网关注入的 {@code X-User-Id} 头填充上下文。
     * <p>
     * 兼容模式不校验签名, 也不写入 request attribute, 因此 {@code RequirePermissionAspect} 的
     * 可信角色校验不会生效。
     * </p>
     *
     * @param request HTTP 请求
     */
    private void authenticateByCompatHeaders(HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            return;
        }
        String trimmedUserId = userId.trim();
        UserContext.setUserId(trimmedUserId);

        String username = request.getHeader(HEADER_USER_NAME);
        if (username != null && !username.isBlank()) {
            UserContext.setUsername(username.trim());
        }

        List<String> roles = splitRoles(request.getHeader(HEADER_USER_ROLE));
        if (log.isDebugEnabled()) {
            log.debug("[JwtAuth] 兼容模式填充上下文 (未验签): userId={}, roles={}",
                    trimmedUserId, roles);
        }
    }

    /**
     * 将令牌声明写入 ThreadLocal、request attribute 与 SecurityContext,
     * 并返回注入身份头的请求包装器供下游沿用历史请求头读取方式。
     *
     * @param request HTTP 请求
     * @param claims  令牌声明
     * @return 注入身份头的请求包装器
     */
    private HttpServletRequest applyClaims(HttpServletRequest request, JwtClaims claims) {
        String userId = claims.uid() != null ? String.valueOf(claims.uid()) : claims.username();
        UserContext.setUserId(userId);
        UserContext.setUsername(claims.username());
        request.setAttribute(VERIFIED_USER_ATTR, userId);
        request.setAttribute(VERIFIED_ROLES_ATTR, claims.roles());

        List<SimpleGrantedAuthority> authorities = claims.roles().stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.trim()))
                .collect(Collectors.toList());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(claims, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String rolesCsv = String.join(",", claims.roles());
        return new JwtIdentityRequestWrapper(request, userId, claims.username(), rolesCsv);
    }

    /**
     * 从 {@code Authorization} 头提取 Bearer 令牌, 缺失或格式不符时返回 null。
     *
     * @param request HTTP 请求
     * @return 令牌字符串, 不存在返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 将逗号分隔的角色字符串拆为角色列表。
     *
     * @param rolesCsv 逗号分隔的角色字符串, 可为 null
     * @return 角色列表, 无角色返回空列表
     */
    private static List<String> splitRoles(String rolesCsv) {
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(rolesCsv.split(","));
    }
}
