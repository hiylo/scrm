/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : JwtIdentityRequestWrapper.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 身份头注入请求包装器。
 * <p>
 * 项目脱离网关后, {@code DataScopeService} / {@code FeignAuthConfig} 等历史代码仍通过
 * 请求头 {@code X-User-Id}、{@code X-User-Name}、{@code X-Username}、{@code X-User-Role}
 * 读取操作人身份与角色。本包装器将 JWT 声明中的身份写入这些头, 保持既有代码无需改造。
 * </p>
 * <p>
 * 注入值一律来自服务端验签后的 JWT 声明, 覆盖客户端原始携带的同名头, 防止身份伪造。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public class JwtIdentityRequestWrapper extends HttpServletRequestWrapper {

    /** 用户 ID 请求头 (与 {@code DataScopeService.HEADER_USER_ID} 一致) */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 用户名请求头 (新命名) */
    public static final String HEADER_USER_NAME = "X-User-Name";

    /** 用户名请求头 (旧命名, 兼容历史代码读取) */
    public static final String HEADER_USERNAME = "X-Username";

    /** 角色请求头 (与 {@code DataScopeService.HEADER_USER_ROLE} 一致, 逗号分隔) */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /** 身份头映射 (键名大小写不敏感, 头名按 Servlet 规范大小写不敏感) */
    private final Map<String, String> identityHeaders;

    /**
     * 构造身份头注入包装器。
     *
     * @param request   原始请求
     * @param userId    JWT 声明中的用户 ID
     * @param username  JWT 声明中的用户名
     * @param rolesCsv  JWT 声明中的角色字符串 (逗号分隔)
     */
    public JwtIdentityRequestWrapper(HttpServletRequest request, String userId,
                                     String username, String rolesCsv) {
        super(request);
        Map<String, String> headers = new LinkedHashMap<>(4);
        if (userId != null && !userId.isBlank()) {
            headers.put(HEADER_USER_ID, userId);
        }
        if (username != null && !username.isBlank()) {
            headers.put(HEADER_USER_NAME, username);
            headers.put(HEADER_USERNAME, username);
        }
        if (rolesCsv != null && !rolesCsv.isBlank()) {
            headers.put(HEADER_USER_ROLE, rolesCsv);
        }
        this.identityHeaders = Collections.unmodifiableMap(headers);
    }

    /**
     * 读取请求头: 命中身份头时返回 JWT 声明值, 否则委托原始请求。
     *
     * @param name 头名称
     * @return 头值, 不存在返回 null
     */
    @Override
    public String getHeader(String name) {
        String injected = identityHeaders.get(name);
        return injected != null ? injected : super.getHeader(name);
    }

    /**
     * 读取请求头集合: 命中身份头时仅返回 JWT 声明值, 否则委托原始请求。
     *
     * @param name 头名称
     * @return 头值集合, 不存在返回空枚举
     */
    @Override
    public java.util.Enumeration<String> getHeaders(String name) {
        String injected = identityHeaders.get(name);
        if (injected == null) {
            return super.getHeaders(name);
        }
        return Collections.enumeration(List.of(injected));
    }

    /**
     * 列出全部请求头名称 (叠加注入的身份头)。
     *
     * @return 头名称枚举
     */
    @Override
    public java.util.Enumeration<String> getHeaderNames() {
        if (identityHeaders.isEmpty()) {
            return super.getHeaderNames();
        }
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        java.util.Enumeration<String> original = super.getHeaderNames();
        while (original.hasMoreElements()) {
            names.add(original.nextElement());
        }
        names.addAll(identityHeaders.keySet());
        return Collections.enumeration(names);
    }
}
