/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPersonaController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmPersonaDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmPersonaService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCRM 人设控制器
 * <p>
 * 提供人设业务字段的增删改查接口, 创建/绑定人设时同步通过 Feign 调 scrm-server
 * 创建执行侧 Persona。权限由 gateway-server 统一鉴权。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/personas")
@RequiredArgsConstructor
public class ScrmPersonaController {

    /** 人设服务 */
    private final ScrmPersonaService personaService;

    /**
     * 创建人设
     *
     * @param dto 人设参数
     * @return 创建后的人设
     */
    @RequirePermission(resource = "scrm_persona", action = "create")
    @PostMapping
    public OperationResponse<ScrmPersonaDto> create(@Valid @RequestBody ScrmPersonaDto dto)
            throws ScrmException {
        return OperationResponse.build(personaService.createPersona(dto));
    }

    /**
     * 更新人设业务字段
     *
     * @param personaId 人设 ID
     * @param dto       人设参数
     * @return 更新后的人设
     */
    @RequirePermission(resource = "scrm_persona", action = "update")
    @PutMapping("/{personaId}")
    public OperationResponse<ScrmPersonaDto> update(@PathVariable String personaId,
                                                     @RequestBody ScrmPersonaDto dto)
            throws ScrmException {
        return OperationResponse.build(personaService.updatePersona(personaId, dto));
    }

    /**
     * 查询人设
     *
     * @param personaId 人设 ID
     * @return 人设详情
     * @throws ScrmException 人设不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_persona", action = "read")
    @GetMapping("/{personaId}")
    public OperationResponse<ScrmPersonaDto> get(@PathVariable String personaId) throws ScrmException {
        return OperationResponse.build(personaService.getPersona(personaId));
    }

    /**
     * 按账号 ID 查询人设列表
     *
     * @param accountId 账号 ID
     * @return 人设列表
     */
    @RequirePermission(resource = "scrm_persona", action = "read")
    @GetMapping("/by-account/{accountId}")
    public OperationResponse<List<ScrmPersonaDto>> getByAccount(@PathVariable Long accountId) {
        return OperationResponse.build(personaService.getPersonaByAccountId(accountId));
    }

    /**
     * 删除人设
     *
     * @param personaId 人设 ID
     * @return 空响应
     * @throws ScrmException 人设不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_persona", action = "delete")
    @DeleteMapping("/{personaId}")
    public OperationResponse<Void> delete(@PathVariable String personaId) throws ScrmException {
        personaService.deletePersona(personaId);
        return OperationResponse.build();
    }

    /**
     * 分页查询人设
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 人设分页结果
     */
    @RequirePermission(resource = "scrm_persona", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmPersonaDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(personaService.listPersonas(page, size));
    }
}
