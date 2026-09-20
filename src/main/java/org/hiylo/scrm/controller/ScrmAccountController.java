/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAccountController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmAccountDto;
import org.hiylo.scrm.entity.ScrmAccountLoginLogEntity;

import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.repository.ScrmAccountLoginLogRepository;
import org.hiylo.scrm.service.ScrmAccountService;
import org.hiylo.scrm.wework.WeworkContactSyncService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import java.util.Map;

/**
 * SCRM 平台账号控制器
 * <p>
 * 提供多平台社媒账号的增删改查、设备绑定、登录态维护与人设绑定接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/accounts")
@RequiredArgsConstructor
public class ScrmAccountController {

    /** 平台账号服务 */
    private final ScrmAccountService accountService;

    /** 账号登录态日志数据访问层 */
    private final ScrmAccountLoginLogRepository accountLoginLogRepository;

    /** 企微外部联系人同步服务 */
    private final WeworkContactSyncService weworkContactSyncService;

    /**
     * 创建账号
     *
     * @param dto 账号参数
     * @return 创建后的账号
     */
    @RequirePermission(resource = "scrm_account", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmAccountDto> create(@Valid @RequestBody ScrmAccountDto dto)
            throws ScrmException {
        return OperationResponse.build(accountService.createAccount(dto));
    }

    /**
     * 更新账号
     *
     * @param id  账号 ID
     * @param dto 账号参数
     * @return 更新后的账号
     */
    @RequirePermission(resource = "scrm_account", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmAccountDto> update(@PathVariable Long id,
                                                     @RequestBody ScrmAccountDto dto)
            throws ScrmException {
        return OperationResponse.build(accountService.updateAccount(id, dto));
    }

    /**
     * 查询账号
     *
     * @param id 账号 ID
     * @return 账号详情
     * @throws ScrmException 账号不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_account", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmAccountDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(accountService.getAccount(id));
    }

    /**
     * 按平台类型与平台账号 UID 查询账号
     *
     * @param platformType       平台类型
     * @param platformAccountUid 平台账号 UID
     * @return 账号详情 (不存在返回 data=null)
     */
    @RequirePermission(resource = "scrm_account", action = "read")
    @GetMapping("/by-platform")
    public OperationResponse<ScrmAccountDto> getByPlatform(@RequestParam String platformType,
                                                            @RequestParam String platformAccountUid) {
        return OperationResponse.build(accountService.getAccountByPlatform(platformType, platformAccountUid));
    }

    /**
     * 按设备 ID 查询关联账号列表
     *
     * @param deviceId 设备 ID
     * @return 账号列表
     */
    @RequirePermission(resource = "scrm_account", action = "read")
    @GetMapping("/by-device/{deviceId}")
    public OperationResponse<List<ScrmAccountDto>> getByDevice(@PathVariable String deviceId) {
        return OperationResponse.build(accountService.getAccountsByDeviceId(deviceId));
    }

    /**
     * 绑定设备
     *
     * @param accountId 账号 ID
     * @param deviceId  设备 ID
     * @return 更新后的账号
     */
    @RequirePermission(resource = "scrm_account", action = "update")
    @PostMapping("/{accountId}/bind-device")
    public OperationResponse<ScrmAccountDto> bindDevice(@PathVariable Long accountId,
                                                         @RequestParam String deviceId)
            throws ScrmException {
        return OperationResponse.build(accountService.bindDevice(accountId, deviceId));
    }

    /**
     * 解绑设备
     *
     * @param accountId 账号 ID
     * @return 更新后的账号
     */
    @RequirePermission(resource = "scrm_account", action = "update")
    @PostMapping("/{accountId}/unbind-device")
    public OperationResponse<ScrmAccountDto> unbindDevice(@PathVariable Long accountId)
            throws ScrmException {
        return OperationResponse.build(accountService.unbindDevice(accountId));
    }

    /**
     * 更新账号登录态
     *
     * @param accountId 账号 ID
     * @param state     新登录态: LOGIN / LOGOUT / FROZEN / UNKNOWN
     * @param reason    变更原因 (可选)
     * @return 更新后的账号
     */
    @RequirePermission(resource = "scrm_account", action = "update")
    @PutMapping("/{accountId}/login-state")
    public OperationResponse<ScrmAccountDto> updateLoginState(@PathVariable Long accountId,
                                                               @RequestParam String state,
                                                               @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(accountService.updateLoginState(accountId, state, reason));
    }

    /**
     * 绑定人设
     * <p>
     * 同时通过 Feign 调 scrm-server 创建执行侧 Persona。
     * </p>
     *
     * @param accountId 账号 ID
     * @param personaId 人设 ID
     * @return 更新后的账号
     */
    @RequirePermission(resource = "scrm_account", action = "update")
    @PostMapping("/{accountId}/bind-persona")
    public OperationResponse<ScrmAccountDto> bindPersona(@PathVariable Long accountId,
                                                          @RequestParam String personaId)
            throws ScrmException {
        return OperationResponse.build(accountService.bindPersona(accountId, personaId));
    }

    /**
     * 解绑人设
     * <p>
     * 清除账号的 personaId 引用, 不删除人设本身。解绑后人设可重新分配给其他账号。
     * </p>
     *
     * @param accountId 账号 ID
     * @return 更新后的账号
     */
    @RequirePermission(resource = "scrm_account", action = "update")
    @PostMapping("/{accountId}/unbind-persona")
    public OperationResponse<ScrmAccountDto> unbindPersona(@PathVariable Long accountId)
            throws ScrmException {
        return OperationResponse.build(accountService.unbindPersona(accountId));
    }

    /**
     * 删除账号
     * <p>
     * 级联清理营销任务关联、设备绑定、人设绑定引用, 登录日志保留用于审计。
     * 建议在删除前先停止该账号关联的运行中营销任务。
     * </p>
     *
     * @param id 账号 ID
     * @return 空响应
     * @throws ScrmException 账号不存在 / 权限不足 / 存在运行中任务
     */
    @RequirePermission(resource = "scrm_account", action = "delete")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        accountService.deleteAccount(id);
        return OperationResponse.build(null);
    }

    /**
     * 分页查询账号
     *
     * @param platformType 平台类型过滤 (可选)
     * @param loginState   登录态过滤 (可选)
     * @param status       状态过滤 (可选, 映射到 loginState)
     * @param keyword      关键词搜索 (可选, 匹配账号名/显示名)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 账号分页结果
     */
    @RequirePermission(resource = "scrm_account", action = "read")
    @GetMapping({"", "/list"})
    public OperationResponse<Page<ScrmAccountDto>> list(
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String loginState,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // status 参数映射到 loginState（前端兼容）
        String effectiveLoginState = loginState != null ? loginState : status;
        return OperationResponse.build(accountService.listAccounts(platformType, effectiveLoginState, keyword, page,
                size));
    }

    /**
     * 分页查询账号登录日志, 按操作时间倒序返回
     * <p>
     * 用于排查登录异常与冻结原因, 数据源为 {@link ScrmAccountLoginLogRepository}。
     * Repository 当前仅提供 List 返回, 这里在 Controller 层做内存分页。
     * </p>
     *
     * @param id   账号 ID
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 登录日志分页结果
     */
    @RequirePermission(resource = "scrm_account", action = "read")
    @GetMapping("/{id}/login-logs")
    public OperationResponse<Page<ScrmAccountLoginLogEntity>> getLoginLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        List<ScrmAccountLoginLogEntity> all =
                accountLoginLogRepository.findByAccountIdOrderByOperateAtDesc(id);
        int total = all.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);
        List<ScrmAccountLoginLogEntity> sub = start > total ? List.of() : all.subList(start, end);
        return OperationResponse.build(new PageImpl<>(sub, pageable, total));
    }

    /**
     * 手动同步企微外部联系人
     * <p>
     * 仅支持企微平台账号, 触发指定账号的外部联系人同步到 scrm_customer。
     * </p>
     *
     * @param id 账号 ID
     * @return 同步结果统计
     */
    @RequirePermission(resource = "scrm_account", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/{id}/sync-contacts")
    public OperationResponse<Map<String, Integer>> syncContacts(@PathVariable Long id)
            throws ScrmException {
        ScrmAccountDto account = accountService.getAccount(id);
        if (account == null) {
            throw ScrmException.notFound("账号不存在: " + id);
        }
        if (!"wework".equalsIgnoreCase(account.getPlatformType())) {
            throw ScrmException.badRequest("仅支持企微账号同步联系人, 当前平台: " + account.getPlatformType());
        }
        WeworkContactSyncService.SyncResult result = weworkContactSyncService.syncExternalContactsByAccount(
                id);
        Map<String, Integer> stats = Map.of(
                "total", result.getTotal(),
                "newCount", result.getNewCount(),
                "updatedCount", result.getUpdatedCount()
        );
        return OperationResponse.build(stats);
    }
}
