/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SecurityConfig.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.auth.JwtTokenProvider;
import org.hiylo.scrm.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Spring Security 配置 (自建 JWT 认证)。
 * <p>
 * 服务已脱离网关独立运行, 认证由 {@link JwtAuthenticationFilter} 完成: 校验
 * {@code Authorization: Bearer <token>} 后写入 {@code SecurityContext}。
 * 安全策略: 无状态会话 (STATELESS)、关闭 CSRF、开启 CORS、口令编码使用 BCrypt。
 * </p>
 * <p>
 * 放行清单 (匿名可访问), 各条目用途:
 * <ul>
 *   <li>{@code /scrm/auth/login} - 登录 (公开注册已关闭, 用户由管理员创建)</li>
 *   <li>{@code /scrm/callback/**}、{@code /scrm/wework/callback} - 真实入站回调
 *       (任务/风控/会话/AI 回调与企微回调, 各自持有共享密钥/签名/IP 白名单鉴权,
 *       并叠加 {@code @RateLimit} 限流保护, 不依赖登录态)</li>
 *   <li>{@code /actuator/health/**}、{@code /actuator/info} - 健康检查与只读服务信息 (探活与监控用)</li>
 *   <li>{@code /ws/**} - WebSocket (握手阶段由 {@code WebSocketAuthInterceptor} 独立鉴权)</li>
 *   <li>{@code /error}、{@code /favicon.ico} - 错误页与静态资源</li>
 * </ul>
 * {@code /scrm/webhooks/**} (Webhook 配置管理) 与 {@code /actuator/**} (health/info 之外的
 * 监控端点, 含 metrics/prometheus) 已移出匿名放行, 一律要求 JWT 认证, 作为纵深防御。
 * 其余 {@code /scrm/**} 一律需要认证; 未认证返回 401, 已认证但角色不足返回 403。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 匿名放行的请求路径集合 (顺序无关, 由 Spring Security 按顺序匹配) */
    private static final List<String> PERMIT_ALL_PATHS = List.of(
            "/scrm/auth/login",
            "/scrm/callback/**",
            "/scrm/wework/callback",
            "/actuator/health/**",
            "/actuator/info",
            "/ws/**",
            "/error",
            "/favicon.ico"
    );

    /** 允许的前端来源, 逗号分隔 (与 {@link CorsConfig} 共用同一配置项) */
    @Value("${scrm.cors.allowed-origins:http://localhost:3002}")
    private String[] allowedOrigins;

    /** 请求体映射器, 用于写出 JSON 格式的 401/403 响应 */
    private final ObjectMapper objectMapper;

    /** JWT 签发与解析组件 */
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 构造安全配置。
     *
     * @param objectMapper   请求体映射器
     * @param jwtTokenProvider JWT 签发与解析组件
     */
    public SecurityConfig(ObjectMapper objectMapper, JwtTokenProvider jwtTokenProvider) {
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 安全过滤链配置: 无状态会话, 关闭 CSRF, 开启 CORS, 注册 JWT 过滤器。
     *
     * @param http HttpSecurity 构建器
     * @return SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_ALL_PATHS.toArray(new String[0])).permitAll()
                        .requestMatchers("/scrm/**").authenticated()
                        .anyRequest().authenticated());

        log.info("SCRM 安全配置已生效: 放行 {} 条路径, 其余 /scrm/** 需 JWT 认证", PERMIT_ALL_PATHS.size());
        return http.build();
    }

    /**
     * CORS 配置源, 复用 {@code scrm.cors.allowed-origins} 配置项。
     *
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/scrm/**", configuration);
        source.registerCorsConfiguration("/actuator/**", configuration);
        return source;
    }

    /**
     * 密码编码器 (BCrypt), 用于登录用户口令的加密存储与登录比对。
     *
     * @return PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 未认证入口点: 对受保护端点的匿名访问返回 401 JSON 响应。
     *
     * @return AuthenticationEntryPoint
     */
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException e) ->
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或令牌已失效");
    }

    /**
     * 访问拒绝处理器: 对已认证但权限不足的访问返回 403 JSON 响应。
     *
     * @return AccessDeniedHandler
     */
    @Bean
    public AccessDeniedHandler forbiddenHandler() {
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) ->
                writeJson(response, HttpServletResponse.SC_FORBIDDEN, "权限不足");
    }

    /**
     * 写出统一 JSON 错误响应体。
     *
     * @param response HTTP 响应
     * @param status   HTTP 状态码
     * @param message  错误消息
     * @throws IOException 写出失败
     */
    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = Map.of(
                "status", "ERROR",
                "code", status,
                "message", message,
                "timestamp", Instant.now().toString()
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
