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

import org.hiylo.scrm.entity.ScrmUserAccountEntity;
import org.hiylo.scrm.repository.ScrmUserAccountRepository;

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
 * 基于"四层角色（ADMIN/MANAGER/SALES/VIEWER）+ 部门"模型计算当前用户可访问的账号 ID 集合,
 * 供 Service 层在 list 查询时按 {@code owner_account_id} 过滤数据。
 * </p>
 * <p>
 * 用户身份从请求头获取（由网关 {@code VerifyTokenFilter} 注入）：
 * <ul>
 *   <li>{@code X-User-Id}      用户 ID（sys_user.user_id）</li>
 *   <li>{@code X-User-Role}    用户角色（ADMIN/MANAGER/SALES/VIEWER/USER）</li>
 *   <li>{@code X-Department-Id} 部门 ID（sys_user.department_id）</li>
 * </ul>
 * </p>
 * <p>
 * 角色与可见范围对照：
 * <ul>
 *   <li>ADMIN   → null（不过滤，看所有数据）</li>
 *   <li>MANAGER → 本部门所有用户关联的 account_id 列表</li>
 *   <li>SALES   → 当前用户关联的 account_id 列表</li>
 *   <li>VIEWER  → null（不过滤，但 Service 层会设为只读）</li>
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
    /** MANAGER 角色名 */
    public static final String ROLE_MANAGER = "MANAGER";
    /** SALES 角色名 */
    public static final String ROLE_SALES = "SALES";
    /** VIEWER 角色名 */
    public static final String ROLE_VIEWER = "VIEWER";

    /** 请求头：用户 ID */
    public static final String HEADER_USER_ID = "X-User-Id";
    /** 请求头：用户角色 */
    public static final String HEADER_USER_ROLE = "X-User-Role";
    /** 请求头：部门 ID */
    public static final String HEADER_DEPARTMENT_ID = "X-Department-Id";

    /** 用户-账号关联数据访问层 */
    private final ScrmUserAccountRepository userAccountRepository;

    /**
     * 获取当前用户可访问的账号 ID 列表, null 表示不限制。
     * <p>
     * 从当前请求头读取用户身份, 根据角色返回对应的账号 ID 集合：
     * <ul>
     *   <li>ADMIN   → null（不过滤）</li>
     *   <li>MANAGER → 本部门关联的 account_id 列表</li>
     *   <li>SALES   → 当前用户关联的 account_id 列表</li>
     *   <li>VIEWER  → null（不过滤, 由调用方按 isReadOnly 控制写操作）</li>
     * </ul>
     * </p>
     *
     * @param userId       用户 ID（由请求头解析）
     * @param role         用户角色（由请求头解析）
     * @param departmentId 部门 ID（由请求头解析）
     * @return 可访问的账号 ID 列表, null 表示不限制
     */
    public List<Long> getAccessibleAccountIds(String userId, String role, String departmentId) {
        log.debug("getAccessibleAccountIds: userId={}, role={}, departmentId={}", userId, role, departmentId);

        if (role == null || role.isBlank()) {
            log.warn("getAccessibleAccountIds: 角色为空, 默认不限制");
            return null;
        }

        // ADMIN/VIEWER 不在 Service 层过滤（VIEWER 由 isReadOnly 控制写操作）
        if (ROLE_ADMIN.equals(role) || ROLE_VIEWER.equals(role)) {
            return null;
        }

        // MANAGER: 本部门所有用户关联的 account_id
        if (ROLE_MANAGER.equals(role)) {
            if (departmentId == null || departmentId.isBlank()) {
                log.warn("getAccessibleAccountIds: MANAGER 部门 ID 为空, 返回空列表");
                return Collections.emptyList();
            }
            List<ScrmUserAccountEntity> entities = userAccountRepository.findByDepartmentId(departmentId);
            List<Long> accountIds = entities.stream()
                    .map(ScrmUserAccountEntity::getAccountId)
                    .distinct()
                    .collect(Collectors.toList());
            log.debug("getAccessibleAccountIds: MANAGER departmentId={} → accountIds={}", departmentId, accountIds);
            return accountIds;
        }

        // SALES: 当前用户关联的 account_id
        if (ROLE_SALES.equals(role)) {
            if (userId == null || userId.isBlank()) {
                log.warn("getAccessibleAccountIds: SALES 用户 ID 为空, 返回空列表");
                return Collections.emptyList();
            }
            List<ScrmUserAccountEntity> entities = userAccountRepository.findByUserId(userId);
            List<Long> accountIds = entities.stream()
                    .map(ScrmUserAccountEntity::getAccountId)
                    .distinct()
                    .collect(Collectors.toList());
            log.debug("getAccessibleAccountIds: SALES userId={} → accountIds={}", userId, accountIds);
            return accountIds;
        }

        // 其他角色（如 USER）默认不限制
        return null;
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
}
