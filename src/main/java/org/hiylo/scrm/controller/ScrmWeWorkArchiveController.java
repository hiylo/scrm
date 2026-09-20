/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWeWorkArchiveController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmWeWorkArchiveConfigDto;
import org.hiylo.scrm.dto.ScrmWeWorkFetchResultDto;
import org.hiylo.scrm.entity.ScrmWeWorkArchiveConfigEntity;
import org.hiylo.scrm.entity.ScrmWeWorkArchiveMessageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmWeWorkArchiveService;
import org.hiylo.scrm.vo.ScrmWeWorkArchiveStatsVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 企微会话存档控制器
 * <p>
 * 提供存档配置管理、会话拉取、消息检索与拉取统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/wework-archive")
@RequiredArgsConstructor
public class ScrmWeWorkArchiveController {

    /** 企微会话存档服务 */
    private final ScrmWeWorkArchiveService archiveService;

    // ==================== 配置管理 ====================

    /**
     * 创建存档配置
     *
     * @param dto 配置参数
     * @return 创建后的配置
     * @throws ScrmException 参数非法 / 配置编码重复
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/configs")
    public OperationResponse<ScrmWeWorkArchiveConfigEntity> createConfig(
            @Valid @RequestBody ScrmWeWorkArchiveConfigDto dto) throws ScrmException {
        return OperationResponse.build(archiveService.createConfig(dto));
    }

    /**
     * 更新存档配置
     *
     * @param id  配置 ID
     * @param dto 配置参数
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "update")
    @PutMapping("/configs/{id}")
    public OperationResponse<ScrmWeWorkArchiveConfigEntity> updateConfig(
            @PathVariable Long id, @RequestBody ScrmWeWorkArchiveConfigDto dto) throws ScrmException {
        return OperationResponse.build(archiveService.updateConfig(id, dto));
    }

    /**
     * 删除存档配置
     *
     * @param id 配置 ID
     * @return 空响应
     * @throws ScrmException 配置不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "delete")
    @DeleteMapping("/configs/{id}")
    public OperationResponse<Void> deleteConfig(@PathVariable Long id) throws ScrmException {
        archiveService.deleteConfig(id);
        return OperationResponse.build();
    }

    /**
     * 查询存档配置
     *
     * @param id 配置 ID
     * @return 配置
     * @throws ScrmException 配置不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "read")
    @GetMapping("/configs/{id}")
    public OperationResponse<ScrmWeWorkArchiveConfigEntity> getConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(archiveService.getConfig(id));
    }

    /**
     * 分页查询存档配置
     *
     * @param status 状态过滤 (可选)
     * @param page   页码 (默认 0)
     * @param size   每页大小 (默认 20)
     * @return 配置分页
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "read")
    @GetMapping("/configs/list")
    public OperationResponse<Page<ScrmWeWorkArchiveConfigEntity>> listConfigs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(archiveService.listConfigs(status, pageable));
    }

    /**
     * 启用存档配置
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "control")
    @PostMapping("/configs/{id}/activate")
    public OperationResponse<ScrmWeWorkArchiveConfigEntity> activateConfig(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(archiveService.activateConfig(id));
    }

    /**
     * 停用存档配置
     *
     * @param id 配置 ID
     * @return 更新后的配置
     * @throws ScrmException 配置不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "control")
    @PostMapping("/configs/{id}/deactivate")
    public OperationResponse<ScrmWeWorkArchiveConfigEntity> deactivateConfig(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(archiveService.deactivateConfig(id));
    }

    /**
     * 测试存档配置是否可用 (验证 corpid / secret)
     *
     * @param id 配置 ID
     * @return 测试结果
     * @throws ScrmException 配置不存在 / 校验失败
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "control")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/configs/{id}/test")
    public OperationResponse<Map<String, Object>> testConfig(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(archiveService.testConfig(id));
    }

    // ==================== 拉取管理 ====================

    /**
     * 拉取单个配置的会话存档
     *
     * @param configId 存档配置 ID
     * @param limit    单次拉取上限 (默认 100)
     * @return 拉取结果
     * @throws ScrmException 配置不存在 / 拉取失败
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "control")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/fetch/{configId}")
    public OperationResponse<ScrmWeWorkFetchResultDto> fetchMessages(
            @PathVariable Long configId,
            @RequestParam(defaultValue = "100") int limit) throws ScrmException {
        return OperationResponse.build(archiveService.fetchMessages(configId, limit));
    }

    /**
     * 批量拉取所有活跃配置的会话存档
     *
     * @param limit 单配置单次拉取上限 (默认 100)
     * @return 各配置拉取结果列表
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "control")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/fetch/batch")
    public OperationResponse<List<ScrmWeWorkFetchResultDto>> batchFetch(
            @RequestParam(defaultValue = "100") int limit) {
        return OperationResponse.build(archiveService.batchFetch(limit));
    }

    // ==================== 消息查询 ====================

    /**
     * 分页查询存档消息, 支持多维度过滤
     *
     * @param configId  配置 ID (可选)
     * @param fromUser  发送者过滤 (可选)
     * @param toUser    接收者过滤 (可选)
     * @param roomId    群 ID 过滤 (可选)
     * @param msgType   消息类型过滤 (可选)
     * @param startTime 发送时间起点 (可选)
     * @param endTime   发送时间终点 (可选)
     * @param keyword   关键词过滤 (可选)
     * @param page      页码 (默认 0)
     * @param size      每页大小 (默认 20)
     * @return 消息分页
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "read")
    @GetMapping("/messages/list")
    public OperationResponse<Page<ScrmWeWorkArchiveMessageEntity>> listMessages(
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) String fromUser,
            @RequestParam(required = false) String toUser,
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) String msgType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));
        return OperationResponse.build(archiveService.getMessages(configId, fromUser, toUser, roomId, msgType,
                startTime, endTime, keyword, pageable));
    }

    /**
     * 查询存档消息详情
     *
     * @param id 消息 ID
     * @return 消息
     * @throws ScrmException 消息不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "read")
    @GetMapping("/messages/{id}")
    public OperationResponse<ScrmWeWorkArchiveMessageEntity> getMessage(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(archiveService.getMessage(id));
    }

    /**
     * 全文检索存档消息 (跨配置)
     *
     * @param keyword   关键词
     * @param startTime 发送时间起点 (可选)
     * @param endTime   发送时间终点 (可选)
     * @param page      页码 (默认 0)
     * @param size      每页大小 (默认 20)
     * @return 消息分页
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "read")
    @GetMapping("/messages/search")
    public OperationResponse<Page<ScrmWeWorkArchiveMessageEntity>> searchMessages(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));
        return OperationResponse.build(archiveService.searchMessages(keyword, startTime, endTime, pageable));
    }

    // ==================== 统计 ====================

    /**
     * 获取拉取统计 (消息数 / 类型分布)
     *
     * @param configId  配置 ID (可选, 缺省为全部配置)
     * @param startTime 起始时间 (可选)
     * @param endTime   结束时间 (可选)
     * @return 统计 VO
     */
    @RequirePermission(resource = "scrm_wework_archive", action = "read")
    @GetMapping("/stats/{configId}")
    public OperationResponse<ScrmWeWorkArchiveStatsVo> getStats(
            @PathVariable Long configId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(archiveService.getFetchStats(configId, startTime, endTime));
    }
}
