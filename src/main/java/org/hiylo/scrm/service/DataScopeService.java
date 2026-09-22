/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DataScopeService.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.repository.ScrmAccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据隔离服务
 * <p>
 * 基于「账号归属用户 (scrm_account.owner_user_id) + ADMIN 角色」模型计算当前用户
 * 可访问的账号 ID 集合, 供 Service 层在 list 查询时按 {@code owner_user_id} 过滤数据。
 * </p>
 * <p>
 * 用户身份从请求头获取（由 {@code JwtIdentityRequestWrapper} 从验签后的 JWT 声明注入）：
 * <ul>
 *   <li>{@code X-User-Id}      用户 ID（scrm_user.id）</li>
 *   <li>{@code X-User-Role}    用户角色（ADMIN / OPERATOR 等, 逗号分隔）</li>
 * </ul>
 * </p>
 * <p>
 * 角色与可见范围对照：
 * <ul>
 *   <li>ADMIN   → null（不过滤，看所有数据、操作所有数据）</li>
 *   <li>OPERATOR → 当前用户归属的账号 (owner_user_id = 当前用户) 列表</li>
 *   <li>VIEWER  → null（不过滤, 但 Service 层会设为只读）</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataScopeService {

    /** ADMIN 角色名 */
    public static final String ROLE_ADMIN = "ADMIN";
    /** VIEWER 角色名 */
    public static final String ROLE_VIEWER = "VIEWER";

    /** 请求头：用户 ID */
    public static final String HEADER_USER_ID = "X-User-Id";
    /** 请求头：用户角色 */
    public static final String HEADER_USER_ROLE = "X-User-Role";
    /** 请求头：部门 ID（保留兼容, 当前模型不再使用） */
    public static final String HEADER_DEPARTMENT_ID = "X-Department-Id";

    /** 账号数据访问层（按 owner_user_id 计算可访问账号范围） */
    private final ScrmAccountRepository accountRepository;

    /**
     * 获取当前用户可访问的账号 ID 列表, null 表示不限制。
     * <p>
     * 从当前请求头读取用户身份, 根据角色返回对应的账号 ID 集合：
     * <ul>
     *   <li>ADMIN   → null（不过滤）</li>
     *   <li>OPERATOR → 当前用户归属的账号 (owner_user_id = 当前用户) 列表</li>
     *   <li>VIEWER  → null（不过滤, 由调用方按 isReadOnly 控制写操作）</li>
     * </ul>
     * </p>
     *
     * @param userId       用户 ID（由请求头解析）
     * @param role         用户角色（由请求头解析）
     * @param departmentId 部门 ID（保留参数, 当前模型不使用）
     * @return 可访问的账号 ID 列表, null 表示不限制
     */
    public List<Long> getAccessibleAccountIds(String userId, String role, String departmentId) {
        log.debug("getAccessibleAccountIds: userId={}, role={}, departmentId={}", userId, role, departmentId);

        if (role == null || role.isBlank()) {
            log.warn("getAccessibleAccountIds: 角色为空, 默认不限制");
            return null;
        }

        // ADMIN/VIEWER 不在 Service 层过滤（VIEWER 由 isReadOnly 控制写操作）
        if (isAdmin(role) || ROLE_VIEWER.equals(role)) {
            return null;
        }

        // OPERATOR 等其他角色: 仅可见当前用户归属的账号
        if (userId == null || userId.isBlank()) {
            log.warn("getAccessibleAccountIds: 下级用户 ID 为空, 返回空列表");
            return Collections.emptyList();
        }
        Long ownerUserId = parseUserId(userId);
        if (ownerUserId == null) {
            log.warn("getAccessibleAccountIds: 用户 ID 格式非法, 返回空列表: userId={}", userId);
            return Collections.emptyList();
        }
        List<Long> accountIds = accountRepository.findByOwnerUserId(ownerUserId).stream()
                .map(ScrmAccountEntity::getId)
                .distinct()
                .collect(Collectors.toList());
        log.debug("getAccessibleAccountIds: 下级用户 userId={} → accountIds={}", userId, accountIds);
        return accountIds;
    }

    /**
     * 判断当前用户是否管理员（ADMIN 角色）。
     *
     * @param role 用户角色（逗号分隔, 如 {@code ADMIN} 或 {@code OPERATOR}）
     * @return true=角色列表包含 ADMIN
     */
    public boolean isAdmin(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        for (String item : role.split(",")) {
            if (ROLE_ADMIN.equals(item.trim().toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前用户是否只读。
     * <p>
     * VIEWER 角色为只读, 不允许任何写操作（创建/更新/删除）。
     * </p>
     *
     * @param role 用户角色（由请求头解析）
     * @return 只读返回 true, 否则 false
     */
    public boolean isReadOnly(String role) {
        return ROLE_VIEWER.equals(role);
    }

    /**
     * 从当前 HTTP 请求头获取指定值。
     *
     * @param headerName 请求头名称
     * @return 请求头值, 不存在或无请求上下文时返回 null
     */
    public String getHeader(String headerName) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes)) {
            return null;
        }
        HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
        String value = request.getHeader(headerName);
        return (value != null && !value.isBlank()) ? value : null;
    }

    /**
     * 便捷方法: 从请求头获取当前用户 ID。
     *
     * @return 用户 ID, 不存在时返回 null
     */
    public String getCurrentUserId() {
        return getHeader(HEADER_USER_ID);
    }

    /**
     * 便捷方法: 从请求头获取当前用户角色。
     *
     * @return 用户角色, 不存在时返回 null
     */
    public String getCurrentRole() {
        return getHeader(HEADER_USER_ROLE);
    }

    /**
     * 便捷方法: 从请求头获取当前用户部门 ID。
     *
     * @return 部门 ID, 不存在时返回 null
     */
    public String getCurrentDepartmentId() {
        return getHeader(HEADER_DEPARTMENT_ID);
    }

    /**
     * 将用户 ID 字符串解析为 Long, 无法解析时返回 null。
     *
     * @param userId 用户 ID 字符串
     * @return 解析后的用户 ID, 格式非法返回 null
     */
    private Long parseUserId(String userId) {
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
