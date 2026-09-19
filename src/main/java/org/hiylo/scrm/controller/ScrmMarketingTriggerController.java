/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMarketingTriggerController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmMarketingTriggerDto;
import org.hiylo.scrm.dto.ScrmMarketingTriggerEventDto;
import org.hiylo.scrm.dto.ScrmTriggerFireDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmMarketingTriggerService;
import org.hiylo.scrm.vo.TriggerMarketingStatsVo;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 触发式自动营销控制器
 * <p>
 * 提供事件驱动的自动营销规则管理、事件触发、待执行事件处理、
 * 事件记录查询与统计接口。权限由 gateway-server 统一鉴权,
 * {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/marketing-triggers")
@RequiredArgsConstructor
public class ScrmMarketingTriggerController {

    /** 触发式营销服务 */
    private final ScrmMarketingTriggerService triggerService;

    /**
     * 创建触发式营销规则
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数校验失败
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60, message = "创建触发式营销规则过于频繁，请稍后重试")
    @PostMapping
    public OperationResponse<ScrmMarketingTriggerDto> create(@Valid @RequestBody ScrmMarketingTriggerDto dto)
            throws ScrmException {
        return OperationResponse.build(triggerService.createTrigger(dto));
    }

    /**
     * 更新触发式营销规则
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmMarketingTriggerDto> update(@PathVariable Long id,
                                                              @RequestBody ScrmMarketingTriggerDto dto)
            throws ScrmException {
        return OperationResponse.build(triggerService.updateTrigger(id, dto));
    }

    /**
     * 删除触发式营销规则
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        triggerService.deleteTrigger(id);
        return OperationResponse.build();
    }

    /**
     * 查询触发式营销规则详情
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmMarketingTriggerDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(triggerService.getTrigger(id));
    }

    /**
     * 分页查询触发式营销规则, 支持按事件类型、动作类型、启用状态与关键词过滤
     *
     * @param eventType  事件类型过滤 (可选)
     * @param actionType 动作类型过滤 (可选)
     * @param enabled    启用状态过滤 (可选)
     * @param keyword    关键词过滤, 匹配规则名称 (可选)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "read")
    @GetMapping({"", "/list"})
    public OperationResponse<Page<ScrmMarketingTriggerDto>> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(triggerService.listTriggers(eventType, actionType, enabled, keyword,
                PageRequest.of(page, size)));
    }

    /**
     * 启用触发式营销规则
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "update")
    @PostMapping("/{id}/enable")
    public OperationResponse<ScrmMarketingTriggerDto> enable(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(triggerService.enableTrigger(id));
    }

    /**
     * 禁用触发式营销规则
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "update")
    @PostMapping("/{id}/disable")
    public OperationResponse<ScrmMarketingTriggerDto> disable(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(triggerService.disableTrigger(id));
    }

    /**
     * 触发事件
     * <p>
     * 查找匹配的启用规则并生成事件记录 (含延迟), 返回生成的事件列表。
     * </p>
     *
     * @param fireDto 触发入参
     * @return 生成的事件记录列表
     * @throws ScrmException 参数校验失败
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60, message = "触发事件过于频繁，请稍后重试")
    @PostMapping("/fire")
    public OperationResponse<List<ScrmMarketingTriggerEventDto>> fire(@Valid @RequestBody ScrmTriggerFireDto fireDto)
            throws ScrmException {
        return OperationResponse.build(triggerService.fireEvent(fireDto));
    }

    /**
     * 处理待执行事件 (手动触发处理)
     * <p>
     * 捞取已到期且状态为 PENDING 的事件, 逐个执行动作并更新状态。
     * 通常由定时任务调用, 此接口用于手动补偿。
     * </p>
     *
     * @return 本次处理的事件数
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60, message = "处理待执行事件过于频繁，请稍后重试")
    @PostMapping("/process")
    public OperationResponse<Integer> process() {
        return OperationResponse.build(triggerService.processPendingEvents());
    }

    /**
     * 查询指定触发器的事件记录, 支持按状态、客户与时间范围过滤
     *
     * @param id         触发器 ID
     * @param status     事件状态过滤 (可选)
     * @param customerId 客户 ID 过滤 (可选)
     * @param startTime  起始时间过滤 (可选, 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    截止时间过滤 (可选, 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 事件记录分页结果
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "read")
    @GetMapping("/{id}/events")
    public OperationResponse<Page<ScrmMarketingTriggerEventDto>> eventsByTrigger(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(triggerService.getTriggerEvents(id, status, customerId,
                startTime, endTime, PageRequest.of(page, size)));
    }

    /**
     * 分页查询事件记录, 支持按触发器、状态、客户与时间范围过滤
     *
     * @param triggerId  触发器 ID 过滤 (可选)
     * @param status     事件状态过滤 (可选)
     * @param customerId 客户 ID 过滤 (可选)
     * @param startTime  起始时间过滤 (可选, 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    截止时间过滤 (可选, 格式 yyyy-MM-dd'T'HH:mm:ss)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 事件记录分页结果
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "read")
    @GetMapping("/events")
    public OperationResponse<Page<ScrmMarketingTriggerEventDto>> events(
            @RequestParam(required = false) Long triggerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(triggerService.getTriggerEvents(triggerId, status, customerId,
                startTime, endTime, PageRequest.of(page, size)));
    }

    /**
     * 触发式营销统计
     *
     * @param startTime 起始时间 (可选, 默认最近 30 天)
     * @param endTime   截止时间 (可选, 默认当前时间)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_marketing_trigger", action = "read")
    @GetMapping("/stats")
    public OperationResponse<TriggerMarketingStatsVo> stats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(triggerService.getTriggerStats(startTime, endTime));
    }
}
