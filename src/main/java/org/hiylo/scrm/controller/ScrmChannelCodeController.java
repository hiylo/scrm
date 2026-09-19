/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChannelCodeController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmChannelCodeDto;
import org.hiylo.scrm.dto.ScrmChannelCodeScanDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmChannelCodeService;
import org.hiylo.scrm.vo.ChannelCodeStatsVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCRM 渠道活码控制器
 * <p>
 * 提供渠道活码的创建、更新、删除、激活/停用, 扫码事件记录与扫码转化统计接口。
 * 权限由 gateway-server 统一鉴权。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/channel-codes")
@RequiredArgsConstructor
public class ScrmChannelCodeController {

    /** 渠道活码服务 */
    private final ScrmChannelCodeService channelCodeService;

    /**
     * 创建渠道活码
     *
     * @param dto 活码参数
     * @return 创建后的活码
     */
    @RequirePermission(resource = "scrm_channel_code", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60, message = "创建活码过于频繁，请稍后重试")
    @PostMapping
    public OperationResponse<ScrmChannelCodeDto> create(@Valid @RequestBody ScrmChannelCodeDto dto)
            throws ScrmException {
        return OperationResponse.build(channelCodeService.createCode(dto));
    }

    /**
     * 更新渠道活码
     *
     * @param id  活码 ID
     * @param dto 活码参数
     * @return 更新后的活码
     */
    @RequirePermission(resource = "scrm_channel_code", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmChannelCodeDto> update(@PathVariable Long id,
                                                         @RequestBody ScrmChannelCodeDto dto)
            throws ScrmException {
        return OperationResponse.build(channelCodeService.updateCode(id, dto));
    }

    /**
     * 删除渠道活码
     * <p>
     * 级联清理扫码记录, 主记录删除。
     * </p>
     *
     * @param id 活码 ID
     * @return 空响应
     * @throws ScrmException 活码不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_channel_code", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        channelCodeService.deleteCode(id);
        return OperationResponse.build(null);
    }

    /**
     * 查询渠道活码详情
     *
     * @param id 活码 ID
     * @return 活码详情
     * @throws ScrmException 活码不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_channel_code", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmChannelCodeDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(channelCodeService.getCode(id));
    }

    /**
     * 分页查询渠道活码, 支持按状态、平台类型、活码类型与关键词过滤
     *
     * @param status       状态过滤 (可选)
     * @param platformType 平台类型过滤 (可选)
     * @param codeType     活码类型过滤 (可选)
     * @param keyword      关键词过滤, 匹配活码名称 (可选)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 活码分页结果
     */
    @RequirePermission(resource = "scrm_channel_code", action = "read")
    @GetMapping({"", "/list"})
    public OperationResponse<Page<ScrmChannelCodeDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String codeType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(channelCodeService.listCodes(status, platformType, codeType, keyword,
                PageRequest.of(page, size)));
    }

    /**
     * 激活渠道活码 (状态置 ACTIVE)
     *
     * @param id 活码 ID
     * @return 更新后的活码
     * @throws ScrmException 活码不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_channel_code", action = "update")
    @PostMapping("/{id}/activate")
    public OperationResponse<ScrmChannelCodeDto> activate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(channelCodeService.activateCode(id));
    }

    /**
     * 停用渠道活码 (状态置 INACTIVE)
     *
     * @param id 活码 ID
     * @return 更新后的活码
     * @throws ScrmException 活码不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_channel_code", action = "update")
    @PostMapping("/{id}/deactivate")
    public OperationResponse<ScrmChannelCodeDto> deactivate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(channelCodeService.deactivateCode(id));
    }

    /**
     * 记录扫码事件并按规则分配账号
     *
     * @param id     活码 ID
     * @param params 扫码参数 (scannerUid / scannerNickname / ip / userAgent)
     * @return 扫码记录 (含分配到的账号 ID)
     */
    @RequirePermission(resource = "scrm_channel_code", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60, message = "扫码记录过于频繁，请稍后重试")
    @PostMapping("/{id}/scan")
    public OperationResponse<ScrmChannelCodeScanDto> scan(@PathVariable Long id,
                                                           @RequestBody ScanRequest params)
            throws ScrmException {
        return OperationResponse.build(channelCodeService.recordScan(id,
                params.getScannerUid(),
                params.getScannerNickname(),
                params.getIp(),
                params.getUserAgent()));
    }

    /**
     * 查询渠道活码扫码转化统计
     *
     * @param id 活码 ID
     * @return 统计 VO
     * @throws ScrmException 活码不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_channel_code", action = "read")
    @GetMapping("/{id}/stats")
    public OperationResponse<ChannelCodeStatsVo> stats(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(channelCodeService.getCodeStats(id));
    }

    /**
     * 扫码请求参数
     * @author Hsi Chu
     */
    @lombok.Data
    public static class ScanRequest {
        /** 扫码者唯一标识 */
        private String scannerUid;
        /** 扫码者昵称 */
        private String scannerNickname;
        /** 扫码者 IP */
        private String ip;
        /** 扫码者 User-Agent */
        private String userAgent;
    }
}
