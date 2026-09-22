/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmUserController.java
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.dto.user.CreateUserRequest;
import org.hiylo.scrm.dto.user.ResetPasswordRequest;
import org.hiylo.scrm.dto.user.UpdateUserRequest;
import org.hiylo.scrm.dto.user.UserViewDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmUserManagementService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统用户管理控制器 (仅系统管理员)。
 * <p>
 * 提供系统用户的创建、分页查询、详情、资料 / 角色更新、启用 / 禁用与重置密码,
 * 供前端「用户管理」页对接。服务层统一校验当前操作人必须为 ADMIN。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/users")
@RequiredArgsConstructor
public class ScrmUserController {

    /** 系统用户管理服务 */
    private final ScrmUserManagementService userManagementService;

    /**
     * 创建系统用户。
     *
     * @param request 创建请求
     * @return 创建后的用户视图
     * @throws ScrmException 权限不足 / 用户名重复
     */
    @RequirePermission(resource = "scrm_user", action = "create")
    @PostMapping
    public OperationResponse<UserViewDto> create(@Valid @RequestBody CreateUserRequest request)
            throws ScrmException {
        log.info("创建系统用户请求: username={}", request.getUsername());
        return OperationResponse.build(userManagementService.createUser(request));
    }

    /**
     * 分页查询系统用户。
     *
     * @param keyword 关键词 (匹配用户名 / 昵称, 可空)
     * @param page    页码 (从 0 开始, 默认 0)
     * @param size    每页大小 (默认 20)
     * @return 用户分页结果
     */
    @RequirePermission(resource = "scrm_user", action = "read")
    @GetMapping({"/list", ""})
    public OperationResponse<Page<UserViewDto>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(userManagementService.listUsers(keyword, page, size));
    }

    /**
     * 查询用户详情。
     *
     * @param id 用户 ID
     * @return 用户视图
     * @throws ScrmException 权限不足 / 用户不存在
     */
    @RequirePermission(resource = "scrm_user", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<UserViewDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(userManagementService.getUser(id));
    }

    /**
     * 更新用户资料 / 角色 / 状态。
     *
     * @param id      用户 ID
     * @param request 更新请求
     * @return 更新后的用户视图
     * @throws ScrmException 权限不足 / 用户不存在
     */
    @RequirePermission(resource = "scrm_user", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<UserViewDto> update(@PathVariable Long id,
                                                 @Valid @RequestBody UpdateUserRequest request)
            throws ScrmException {
        return OperationResponse.build(userManagementService.updateUser(id, request));
    }

    /**
     * 启用 / 禁用用户。
     *
     * @param id     用户 ID
     * @param status 1=启用, 0=禁用
     * @return 更新后的用户视图
     * @throws ScrmException 权限不足 / 用户不存在
     */
    @RequirePermission(resource = "scrm_user", action = "update")
    @PutMapping("/{id}/status")
    public OperationResponse<UserViewDto> setStatus(@PathVariable Long id,
                                                     @RequestParam int status)
            throws ScrmException {
        return OperationResponse.build(userManagementService.setUserStatus(id, status));
    }

    /**
     * 重置用户密码。
     *
     * @param id      用户 ID
     * @param request 新口令
     * @return 空响应
     * @throws ScrmException 权限不足 / 用户不存在 / 密码强度不足
     */
    @RequirePermission(resource = "scrm_user", action = "update")
    @PostMapping("/{id}/reset-password")
    public OperationResponse<Void> resetPassword(@PathVariable Long id,
                                                  @Valid @RequestBody ResetPasswordRequest request)
            throws ScrmException {
        userManagementService.resetPassword(id, request.getNewPassword());
        return OperationResponse.build();
    }
}