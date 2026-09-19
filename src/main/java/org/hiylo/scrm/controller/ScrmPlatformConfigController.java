/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPlatformConfigController.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmPlatformConfigDto;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.config.WeworkConfigProvider;
import org.hiylo.scrm.service.ScrmPlatformConfigService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCRM 平台配置控制器
 * <p>
 * 提供各平台（企微/抖音/快手/小红书等）连接配置的增删改查与连接测试接口。
 * 每平台类型仅一条配置记录，配置优先级：数据库记录 > YAML 文件默认值。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/platform-configs")
@RequiredArgsConstructor
public class ScrmPlatformConfigController {

    /** 平台配置服务 */
    private final ScrmPlatformConfigService platformConfigService;

    /** 企微配置动态提供者（保存/删除企微配置后刷新 WeworkConfig Bean） */
    private final WeworkConfigProvider weworkConfigProvider;

    /**
     * 获取平台配置
     *
     * @param platformType 平台类型
     * @return 平台配置详情，不存在返回 data=null
     */
    @RequirePermission(resource = "scrm_platform_config", action = "read")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @GetMapping("/{platformType}")
    public OperationResponse<ScrmPlatformConfigDto> get(@PathVariable String platformType) {
        return OperationResponse.build(platformConfigService.getConfig(platformType));
    }

    /**
     * 获取账号下所有平台配置
     *
     * @return 平台配置列表
     */
    @RequirePermission(resource = "scrm_platform_config", action = "read")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @GetMapping({"", "/list"})
    public OperationResponse<List<ScrmPlatformConfigDto>> list() {
        return OperationResponse.build(platformConfigService.listConfigs());
    }

    /**
     * 保存平台配置（upsert 语义）
     *
     * @param platformType 平台类型
     * @param dto          平台配置参数
     * @return 保存后的平台配置
     */
    @RequirePermission(resource = "scrm_platform_config", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PutMapping("/{platformType}")
    public OperationResponse<ScrmPlatformConfigDto> save(@PathVariable String platformType,
                                                          @Valid @RequestBody ScrmPlatformConfigDto dto) {
        // 强制 path 中的 platformType 与 body 一致
        dto.setPlatformType(platformType);
        ScrmPlatformConfigDto result = platformConfigService.saveConfig(platformType, dto);
        // 企微配置保存后立即刷新 WeworkConfig Bean
        if ("wework".equalsIgnoreCase(platformType)) {
            weworkConfigProvider.refreshConfig();
        }
        return OperationResponse.build(result);
    }

    /**
     * 测试平台连接
     * <p>
     * 对企微平台：调用 gettoken API 验证 corpId/secret 是否正确。
     * 对其他平台：检查配置完整性。
     * </p>
     *
     * @param platformType 平台类型
     * @return 测试结果（含连接状态）
     */
    @RequirePermission(resource = "scrm_platform_config", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/{platformType}/test")
    public OperationResponse<ScrmPlatformConfigDto> testConnection(@PathVariable String platformType) {
        return OperationResponse.build(platformConfigService.testConnection(platformType));
    }

    /**
     * 删除平台配置
     *
     * @param platformType 平台类型
     * @return 操作结果
     */
    @RequirePermission(resource = "scrm_platform_config", action = "delete")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @DeleteMapping("/{platformType}")
    public OperationResponse<Void> delete(@PathVariable String platformType) {
        platformConfigService.deleteConfig(platformType);
        // 企微配置删除后刷新 WeworkConfig Bean，使其回退到 YAML 默认值
        if ("wework".equalsIgnoreCase(platformType)) {
            weworkConfigProvider.refreshConfig();
        }
        return OperationResponse.build(null);
    }
}
