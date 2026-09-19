/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RequirePermissionAspect.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.security.JwtAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Set;

/**
 * {@link RequirePermission} 角色校验切面。
 * <p>
 * 从 {@link JwtAuthenticationFilter} 写入 request attribute 的 JWT 声明角色列表中解析当前用户角色,
 * 按 {@link RequirePermission} 声明的动作 (action) 与角色做匹配:
 * <ul>
 *   <li>{@code read} 类动作 - 任何已登录用户可访问;</li>
 *   <li>其余写类动作 (create/update/delete/execute/control 等) - 需要
 *       {@code ADMIN / SUPERADMIN / OPERATOR / MANAGER} 之一, VIEWER 只读角色返回 403;</li>
 *   <li>未携带可信 JWT 声明 (匿名或兼容模式) - 返回 401。</li>
 * </ul>
 * </p>
 * <p>
 * 角色判定仅使用 JWT 声明中的角色 (由服务端签名保证不可伪造), 不读取客户端可伪造的
 * {@code X-User-Role} 请求头。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Aspect
@Component
public class RequirePermissionAspect {

    /** 只读动作名, 任何已登录用户可访问 */
    private static final Set<String> READ_ACTIONS = Set.of("read");

    /** 允许执行写类动作的角色集合 */
    private static final Set<String> WRITE_ALLOWED_ROLES = Set.of(
            "ADMIN", "SUPERADMIN", "OPERATOR", "MANAGER"
    );

    /**
     * 拦截 {@link RequirePermission} 注解方法, 校验当前用户角色是否满足动作要求。
     *
     * @param joinPoint  切点
     * @param permission 权限注解声明
     * @return 目标方法返回值
     * @throws Throwable 目标方法抛出的异常, 或角色不足时抛出的 {@link ScrmException}
     */
    @Around("@annotation(permission)")
    public Object check(ProceedingJoinPoint joinPoint, RequirePermission permission) throws Throwable {
        List<String> roles = currentRoles();
        if (roles.isEmpty()) {
            throw ScrmException.unauthorized("未认证: 需要有效的登录令牌");
        }
        if (READ_ACTIONS.contains(permission.action())) {
            return joinPoint.proceed();
        }
        boolean allowed = roles.stream()
                .anyMatch(role -> WRITE_ALLOWED_ROLES.contains(role.trim().toUpperCase()));
        if (!allowed) {
            log.warn("权限校验失败: resource={}, action={}, roles={}",
                    permission.resource(), permission.action(), roles);
            throw ScrmException.forbidden("角色 " + roles + " 无权执行 " + permission.action());
        }
        return joinPoint.proceed();
    }

    /**
     * 从当前请求的 JWT 声明 attribute 中读取角色列表。
     *
     * @return 角色列表, 无请求上下文或未认证时返回空列表
     */
    private List<String> currentRoles() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return List.of();
        }
        HttpServletRequest request = servletAttributes.getRequest();
        Object roles = request.getAttribute(JwtAuthenticationFilter.VERIFIED_ROLES_ATTR);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
