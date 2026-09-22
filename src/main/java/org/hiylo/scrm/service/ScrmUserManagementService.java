/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmUserManagementService.java
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.user.CreateUserRequest;
import org.hiylo.scrm.dto.user.UpdateUserRequest;
import org.hiylo.scrm.dto.user.UserViewDto;
import org.hiylo.scrm.entity.ScrmUserEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * 系统用户管理服务。
 * <p>
 * 仅 ADMIN 角色可用, 承担「关闭公开注册」后系统的用户准入: 创建下级用户、
 * 分页查询、更新资料 / 角色、启用 / 禁用、重置密码。口令一律 BCrypt 加密落库。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmUserManagementService {

    /** 密码强度规则: 8-72 位且同时包含字母与数字 (72 为 BCrypt 输入上限) */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");

    /** 默认角色 (下级用户) */
    private static final String DEFAULT_ROLE = "OPERATOR";

    /** 角色分隔符 */
    private static final String ROLE_SEPARATOR = ",";

    /** 账号状态: 启用 */
    private static final int STATUS_ENABLED = 1;

    /** 账号状态: 禁用 */
    private static final int STATUS_DISABLED = 0;

    /** 系统用户数据访问层 */
    private final ScrmUserRepository userRepository;

    /** 口令加密器 (BCrypt) */
    private final PasswordEncoder passwordEncoder;

    /** 数据隔离服务 (判断当前操作人是否为 ADMIN) */
    private final DataScopeService dataScopeService;

    /**
     * 创建系统用户 (仅 ADMIN)。
     *
     * @param request 创建请求
     * @return 创建后的用户视图
     * @throws ScrmException 权限不足 / 用户名重复
     */
    @Transactional
    public UserViewDto createUser(CreateUserRequest request) throws ScrmException {
        requireAdmin();
        String username = request.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw ScrmException.conflict("用户名已被占用: " + username);
        }
        ScrmUserEntity user = new ScrmUserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setRoles(normalizeRoles(request.getRoles()));
        user.setStatus(STATUS_ENABLED);
        ScrmUserEntity saved = userRepository.save(user);
        log.info("管理员创建系统用户: username={}, roles={}", username, saved.getRoles());
        return toView(saved);
    }

    /**
     * 分页查询系统用户 (仅 ADMIN)。
     *
     * @param keyword 关键词 (匹配用户名 / 昵称 / 邮箱, 可空)
     * @param page    页码 (从 0 开始)
     * @param size    每页大小
     * @return 用户分页结果
     */
    @Transactional(readOnly = true)
    public Page<UserViewDto> listUsers(String keyword, int page, int size) {
        requireAdmin();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createTime"));
        Page<ScrmUserEntity> entities = (keyword == null || keyword.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                        keyword.trim(), keyword.trim(), pageable);
        return entities.map(this::toView);
    }

    /**
     * 查询用户详情 (仅 ADMIN)。
     *
     * @param id 用户 ID
     * @return 用户视图
     * @throws ScrmException 权限不足 / 用户不存在
     */
    @Transactional(readOnly = true)
    public UserViewDto getUser(Long id) throws ScrmException {
        requireAdmin();
        return toView(findOrThrow(id));
    }

    /**
     * 更新用户资料 / 角色 / 状态 (仅 ADMIN)。
     *
     * @param id      用户 ID
     * @param request 更新请求
     * @return 更新后的用户视图
     * @throws ScrmException 权限不足 / 用户不存在
     */
    @Transactional
    public UserViewDto updateUser(Long id, UpdateUserRequest request) throws ScrmException {
        requireAdmin();
        ScrmUserEntity user = findOrThrow(id);
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getRoles() != null && !request.getRoles().isBlank()) {
            user.setRoles(normalizeRoles(request.getRoles()));
        }
        if (request.getStatus() != null) {
            if (request.getStatus() != STATUS_ENABLED && request.getStatus() != STATUS_DISABLED) {
                throw ScrmException.badRequest("状态非法: 仅支持 1=启用 / 0=禁用");
            }
            user.setStatus(request.getStatus());
        }
        ScrmUserEntity saved = userRepository.save(user);
        log.info("管理员更新系统用户: id={}, username={}", id, saved.getUsername());
        return toView(saved);
    }

    /**
     * 重置用户密码 (仅 ADMIN)。
     *
     * @param id          用户 ID
     * @param newPassword 新口令明文
     * @return 空响应
     * @throws ScrmException 权限不足 / 用户不存在 / 密码强度不足
     */
    @Transactional
    public void resetPassword(Long id, String newPassword) throws ScrmException {
        requireAdmin();
        if (newPassword == null || !PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw ScrmException.badRequest("密码强度不足: 需 8-72 位且同时包含字母与数字");
        }
        ScrmUserEntity user = findOrThrow(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("管理员重置用户密码: id={}, username={}", id, user.getUsername());
    }

    /**
     * 启用 / 禁用用户 (仅 ADMIN)。
     *
     * @param id     用户 ID
     * @param status 1=启用, 0=禁用
     * @return 更新后的用户视图
     * @throws ScrmException 权限不足 / 用户不存在 / 状态非法
     */
    @Transactional
    public UserViewDto setUserStatus(Long id, int status) throws ScrmException {
        requireAdmin();
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw ScrmException.badRequest("状态非法: 仅支持 1=启用 / 0=禁用");
        }
        ScrmUserEntity user = findOrThrow(id);
        user.setStatus(status);
        ScrmUserEntity saved = userRepository.save(user);
        log.info("管理员设置用户状态: id={}, username={}, status={}", id, saved.getUsername(), status);
        return toView(saved);
    }

    /**
     * 校验当前操作人是否为 ADMIN, 否则抛禁止访问异常。
     *
     * @throws ScrmException 当前角色非 ADMIN
     */
    private void requireAdmin() throws ScrmException {
        String role = dataScopeService != null ? dataScopeService.getCurrentRole() : null;
        if (role == null || !dataScopeService.isAdmin(role)) {
            log.warn("用户管理越权访问被拒绝: role={}", role);
            throw new ScrmException(ScrmExceptionConstants.FORBIDDEN, "仅系统管理员可管理用户");
        }
    }

    /**
     * 归一化角色串: 去空白 + 大写, 空则用默认角色 OPERATOR。
     *
     * @param rolesCsv 角色串 (可空)
     * @return 归一化后的角色串
     */
    private String normalizeRoles(String rolesCsv) {
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return DEFAULT_ROLE;
        }
        String normalized = String.join(ROLE_SEPARATOR,
                java.util.Arrays.stream(rolesCsv.split(ROLE_SEPARATOR))
                        .map(String::trim)
                        .filter(r -> !r.isEmpty())
                        .map(String::toUpperCase)
                        .distinct()
                        .toList());
        return normalized.isEmpty() ? DEFAULT_ROLE : normalized;
    }

    /**
     * 按主键查询用户, 不存在抛异常。
     *
     * @param id 用户 ID
     * @return 用户实体
     * @throws ScrmException 用户不存在
     */
    private ScrmUserEntity findOrThrow(Long id) throws ScrmException {
        return userRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.SCRM_AUTH_USER_NOT_FOUND,
                        "用户不存在: id=" + id));
    }

    /**
     * 实体转视图 DTO (不含口令明文)。
     *
     * @param entity 用户实体
     * @return 用户视图
     */
    private UserViewDto toView(ScrmUserEntity entity) {
        UserViewDto dto = new UserViewDto();
        dto.setId(entity.getId());
        dto.setUsername(entity.getUsername());
        dto.setDisplayName(entity.getDisplayName());
        dto.setEmail(entity.getEmail());
        dto.setRoles(entity.getRoles());
        dto.setStatus(entity.getStatus());
        dto.setLastLoginAt(entity.getLastLoginAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}