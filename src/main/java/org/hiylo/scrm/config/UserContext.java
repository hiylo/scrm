/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : UserContext.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.config;

/**
 * 用户上下文
 * <p>
 * 基于 ThreadLocal 持有当前请求的操作人信息 (用户 ID 和用户名),
 * 由 {@code JwtAuthenticationFilter} 从验签后的 JWT 声明解析并写入。
 * Service 层可通过 getUserId() / getUsername() 获取操作人用于审计日志。
 * 请求结束时必须调用 clear() 清理, 避免线程池复用导致用户串号。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public final class UserContext {

    /** 当前线程绑定的操作人用户 ID 容器 */
    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();
    /** 当前线程绑定的操作人用户名容器 */
    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();

    private UserContext() {
        // 工具类, 禁止实例化
    }

    /**
     * 设置当前操作人用户 ID
     *
     * @param userId 用户 ID
     */
    public static void setUserId(String userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取当前操作人用户 ID, 未设置时返回 null
     *
     * @return 用户 ID
     */
    public static String getUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 设置当前操作人用户名
     *
     * @param username 用户名
     */
    public static void setUsername(String username) {
        USERNAME_HOLDER.set(username);
    }

    /**
     * 获取当前操作人用户名, 未设置时返回 null
     *
     * @return 用户名
     */
    public static String getUsername() {
        return USERNAME_HOLDER.get();
    }

    /**
     * 清理当前线程的用户上下文, 必须在请求结束后调用
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
        USERNAME_HOLDER.remove();
    }
}
