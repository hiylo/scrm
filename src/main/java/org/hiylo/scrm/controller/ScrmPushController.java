/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPushController.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmDeviceRegisterDto;
import org.hiylo.scrm.entity.ScrmUserDeviceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.repository.ScrmUserDeviceRepository;
import org.hiylo.scrm.service.DataScopeService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 推送设备控制器。
 * <p>
 * 提供业务员 APP (Android / iOS) 的推送设备注册 / 注销 / 查询能力。APP 启动并获取到
 * 个推 client_id 后调用 {@code POST /scrm/push/register} 上报, 服务端 upsert 到
 * {@code scrm_user_device} 表; 注销时调用 {@code POST /scrm/push/unregister}。
 * </p>
 * <p>
 * 当前用户 ID 由 {@link DataScopeService#getCurrentUserId()} 从请求头 {@code X-User-Id}
 * 读取 (由网关 {@code VerifyTokenFilter} 注入)。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/push")
@RequiredArgsConstructor
public class ScrmPushController {

    /** 设备状态: 活跃 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /** 用户设备数据访问层 */
    private final ScrmUserDeviceRepository userDeviceRepository;

    /** 数据隔离服务 (读取当前用户身份) */
    private final DataScopeService dataScopeService;

    /**
     * 注册设备推送 token (upsert)。
     * <p>
     * 已存在 (user_id, client_id) 则更新设备信息并重置为 ACTIVE, 否则新建记录。
     * </p>
     *
     * @param dto 设备注册参数
     * @return 注册后的设备记录
     * @throws ScrmException 请求头缺少 X-User-Id
     */
    @RequirePermission(resource = "scrm_push", action = "write")
    @PostMapping("/register")
    public OperationResponse<ScrmUserDeviceEntity> register(@Valid @RequestBody ScrmDeviceRegisterDto dto)
            throws ScrmException {
        String userId = requireCurrentUserId();

        ScrmUserDeviceEntity entity = userDeviceRepository
                .findByUserIdAndClientId(userId, dto.getClientId())
                .map(existing -> {
                    existing.setDeviceId(dto.getDeviceId());
                    existing.setPlatform(dto.getPlatform());
                    existing.setStatus(STATUS_ACTIVE);
                    existing.setLastActiveAt(LocalDateTime.now());
                    return existing;
                })
                .orElseGet(() -> {
                    ScrmUserDeviceEntity fresh = new ScrmUserDeviceEntity();
                    fresh.setUserId(userId);
                    fresh.setClientId(dto.getClientId());
                    fresh.setDeviceId(dto.getDeviceId());
                    fresh.setPlatform(dto.getPlatform());
                    fresh.setStatus(STATUS_ACTIVE);
                    fresh.setLastActiveAt(LocalDateTime.now());
                    return fresh;
                });
        ScrmUserDeviceEntity saved = userDeviceRepository.save(entity);
        return OperationResponse.build(saved);
    }

    /**
     * 注销设备推送 token。
     *
     * @param clientId 个推 client_id
     * @return 空响应
     * @throws ScrmException 请求头缺少 X-User-Id
     */
    @RequirePermission(resource = "scrm_push", action = "write")
    @PostMapping("/unregister")
    public OperationResponse<Void> unregister(@RequestParam String clientId) throws ScrmException {
        String userId = requireCurrentUserId();
        userDeviceRepository.deleteByUserIdAndClientId(userId, clientId);
        return OperationResponse.build();
    }

    /**
     * 查询当前用户的所有注册设备。
     *
     * @return 设备列表
     * @throws ScrmException 请求头缺少 X-User-Id
     */
    @RequirePermission(resource = "scrm_push", action = "read")
    @GetMapping("/devices")
    public OperationResponse<List<ScrmUserDeviceEntity>> devices() throws ScrmException {
        String userId = requireCurrentUserId();
        return OperationResponse.build(userDeviceRepository.findByUserId(userId));
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 获取当前用户 ID, 缺失时抛出参数异常。
     *
     * @return 当前用户 ID
     * @throws ScrmException 请求头缺少 X-User-Id
     */
    private String requireCurrentUserId() throws ScrmException {
        String userId = dataScopeService.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw ScrmException.badRequest("请求头缺少 X-User-Id, 无法识别当前用户");
        }
        return userId;
    }
}
