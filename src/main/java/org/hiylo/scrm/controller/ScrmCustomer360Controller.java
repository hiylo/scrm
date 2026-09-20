/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomer360Controller.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmCustomerTimelineEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCustomer360Service;
import org.hiylo.scrm.vo.ScrmCustomer360Vo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRM 客户 360° 视图控制器。
 * <p>
 * 提供客户全景视图、时间线查询与事件添加、互动统计、客户概要与批量概要接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/customer-360")
@RequiredArgsConstructor
public class ScrmCustomer360Controller {

    /** 客户 360° 视图聚合服务 */
    private final ScrmCustomer360Service customer360Service;

    /**
     * 查询客户 360° 全景视图。
     * <p>
     * 聚合客户基本信息、标签、分组、生命周期历史、商机、旅程进度、
     * 最近会话消息、最近时间线事件与互动统计为统一视图。
     * </p>
     *
     * @param customerId 客户 ID
     * @return 客户 360° 视图
     * @throws ScrmException 客户不存在或越权访问
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping("/{customerId}")
    public OperationResponse<ScrmCustomer360Vo> get360(@PathVariable Long customerId) throws ScrmException {
        return OperationResponse.build(customer360Service.getCustomer360(customerId));
    }

    /**
     * 查询客户时间线, 支持按事件类型与时间范围过滤。
     *
     * @param customerId 客户 ID
     * @param eventType  事件类型过滤 (可选)
     * @param startTime  起始时间 (含, 可选, 格式 yyyy-MM-dd HH:mm:ss)
     * @param endTime    截止时间 (含, 可选, 格式 yyyy-MM-dd HH:mm:ss)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 时间线事件分页
     * @throws ScrmException 客户不存在或越权访问
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping("/{customerId}/timeline")
    public OperationResponse<Page<ScrmCustomerTimelineEntity>> getTimeline(
            @PathVariable Long customerId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "eventTime"));
        return OperationResponse.build(
                customer360Service.getTimeline(customerId, eventType, startTime, endTime, pageable));
    }

    /**
     * 添加客户时间线事件。
     * <p>
     * 请求体携带事件类型、标题、详情、操作人与重要级别。
     * operatorId / operatorName 为空时, 自动回退当前请求用户。
     * </p>
     *
     * @param customerId 客户 ID
     * @param request    事件请求体
     * @return 持久化后的事件
     * @throws ScrmException 客户不存在 / 越权访问 / 参数非法
     */
    @RequirePermission(resource = "scrm_customer", action = "update")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/{customerId}/timeline")
    public OperationResponse<ScrmCustomerTimelineEntity> addTimelineEvent(
            @PathVariable Long customerId,
            @Valid @RequestBody TimelineEventRequest request) throws ScrmException {
        if (request == null) {
            throw ScrmException.badRequest("时间线事件请求体不能为空");
        }
        ScrmCustomerTimelineEntity entity = customer360Service.addTimelineEvent(
                customerId,
                request.getEventType(),
                request.getEventTitle(),
                request.getEventDetail(),
                request.getOperatorId(),
                request.getOperatorName(),
                request.getImportance());
        return OperationResponse.build(entity);
    }

    /**
     * 查询客户互动统计。
     * <p>
     * 返回总消息数 / 总跟进次数 / 最后互动时间 / 客户天数 / 商机总额。
     * </p>
     *
     * @param customerId 客户 ID
     * @return 互动统计
     * @throws ScrmException 客户不存在或越权访问
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping("/{customerId}/stats")
    public OperationResponse<ScrmCustomer360Vo.InteractionStats> getStats(
            @PathVariable Long customerId) throws ScrmException {
        return OperationResponse.build(customer360Service.getInteractionStats(customerId));
    }

    /**
     * 查询客户概要 (基本信息 + 关键指标)。
     * <p>
     * 比 360° 视图轻量, 仅返回客户 DTO 与互动统计, 适合列表页悬浮卡片。
     * </p>
     *
     * @param customerId 客户 ID
     * @return 客户概要
     * @throws ScrmException 客户不存在或越权访问
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping("/{customerId}/overview")
    public OperationResponse<ScrmCustomer360Vo> getOverview(
            @PathVariable Long customerId) throws ScrmException {
        return OperationResponse.build(customer360Service.getCustomerOverview(customerId));
    }

    /**
     * 批量查询客户概要。
     * <p>
     * 请求体携带客户 ID 列表, 逐一查询客户概要, 单个客户失败不影响其他客户。
     * 适合客户列表页批量预览。
     * </p>
     *
     * @param customerIds 客户 ID 列表
     * @return 客户概要列表 (仅含成功的客户)
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/overview/batch")
    public OperationResponse<List<ScrmCustomer360Vo>> batchOverview(
            @RequestBody List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        return OperationResponse.build(customer360Service.batchGetOverview(customerIds));
    }

    /**
     * 添加时间线事件请求体
     * @author Hsi Chu
     */
    @lombok.Data
    public static class TimelineEventRequest {

        /** 事件类型 */
        private String eventType;

        /** 事件标题 */
        private String eventTitle;

        /** 事件详情 (JSON, 可空) */
        private String eventDetail;

        /** 操作人 ID (可空, 自动回退当前用户) */
        private String operatorId;

        /** 操作人姓名 (可空, 自动回退当前用户) */
        private String operatorName;

        /** 重要级别: HIGH / NORMAL / LOW (默认 NORMAL) */
        private String importance;
    }
}
