/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmWorkOrderAssignDto;
import org.hiylo.scrm.dto.ScrmWorkOrderDto;
import org.hiylo.scrm.dto.ScrmWorkOrderLogDto;
import org.hiylo.scrm.dto.ScrmWorkOrderSearchDto;
import org.hiylo.scrm.dto.ScrmWorkOrderSlaDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmWorkOrderService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 * SCRM 工单管理控制器。
 * <p>
 * 提供工单增删改查、分配/接受/开始/解决/关闭/取消/重开/升级动作、响应与附件、时间线与关联工单、
 * 合并/拆分/克隆/批量分配/批量关闭/导出; 工单日志增删查与内部备注; SLA 策略管理与 SLA 计算/
 * 违规扫描/预警/升级/达标率统计; 工单统计与多维度分析接口。权限由 gateway-server 统一鉴权,
 * {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/work-orders")
@RequiredArgsConstructor
public class ScrmWorkOrderController {

    /** 工单服务 */
    private final ScrmWorkOrderService scrmWorkOrderService;

    // ============================================================
    // 工单 CRUD
    // ============================================================

    /**
     * 创建工单。
     *
     * @param dto 工单参数
     * @return 创建后的工单
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_work_order", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建工单过于频繁，请稍后重试")
    @PostMapping("/orders")
    public OperationResponse<ScrmWorkOrderDto> createOrder(@Valid @RequestBody ScrmWorkOrderDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.createOrder(dto));
    }

    /**
     * 更新工单 (字段非空才覆盖)。
     *
     * @param id  工单 ID
     * @param dto 工单参数
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_work_order", action = "update")
    @PutMapping("/orders/{id}")
    public OperationResponse<ScrmWorkOrderDto> updateOrder(@PathVariable Long id,
                                                              @RequestBody ScrmWorkOrderDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.updateOrder(id, dto));
    }

    /**
     * 删除工单 (级联清理日志)。
     *
     * @param id 工单 ID
     * @return 空响应
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "delete")
    @DeleteMapping("/orders/{id}")
    public OperationResponse<Void> deleteOrder(@PathVariable Long id) throws ScrmException {
        scrmWorkOrderService.deleteOrder(id);
        return OperationResponse.build();
    }

    /**
     * 查询工单详情。
     *
     * @param id 工单 ID
     * @return 工单详情
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/{id}")
    public OperationResponse<ScrmWorkOrderDto> getOrder(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.getOrder(id));
    }

    /**
     * 按工单编号查询工单。
     *
     * @param orderNo 工单编号
     * @return 工单详情
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/by-no/{orderNo}")
    public OperationResponse<ScrmWorkOrderDto> getOrderByNo(@PathVariable String orderNo) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.getOrderByNo(orderNo));
    }

    /**
     * 分页查询工单 (按创建时间倒序)。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 工单分页结果
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/list")
    public OperationResponse<Page<ScrmWorkOrderDto>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmWorkOrderService.listOrders(pageable));
    }

    /**
     * 高级搜索工单。
     *
     * @param searchDto 搜索条件
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 工单分页结果
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @PostMapping("/orders/search")
    public OperationResponse<Page<ScrmWorkOrderDto>> searchOrders(@RequestBody ScrmWorkOrderSearchDto searchDto,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createTime");
        if (searchDto != null && searchDto.getSortBy() != null && !searchDto.getSortBy().isBlank()) {
            sort = Sort.by(Sort.Direction.DESC, searchDto.getSortBy());
        }
        PageRequest pageable = PageRequest.of(page, size, sort);
        return OperationResponse.build(scrmWorkOrderService.searchOrders(searchDto, pageable));
    }

    /**
     * 按客户查询工单。
     *
     * @param customerId 客户 ID
     * @return 工单列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/by-customer/{customerId}")
    public OperationResponse<List<ScrmWorkOrderDto>> getOrdersByCustomer(@PathVariable Long customerId) {
        return OperationResponse.build(scrmWorkOrderService.getOrdersByCustomer(customerId));
    }

    /**
     * 按工单类型查询工单。
     *
     * @param type 工单类型
     * @return 工单列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/by-type/{type}")
    public OperationResponse<List<ScrmWorkOrderDto>> getOrdersByType(@PathVariable String type) {
        return OperationResponse.build(scrmWorkOrderService.getOrdersByType(type));
    }

    /**
     * 按工单状态查询工单。
     *
     * @param status 工单状态
     * @return 工单列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/by-status/{status}")
    public OperationResponse<List<ScrmWorkOrderDto>> getOrdersByStatus(@PathVariable String status) {
        return OperationResponse.build(scrmWorkOrderService.getOrdersByStatus(status));
    }

    /**
     * 按处理人查询工单。
     *
     * @param assigneeId 处理人 ID
     * @return 工单列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/by-assignee/{assigneeId}")
    public OperationResponse<List<ScrmWorkOrderDto>> getOrdersByAssignee(@PathVariable Long assigneeId) {
        return OperationResponse.build(scrmWorkOrderService.getOrdersByAssignee(assigneeId));
    }

    /**
     * 按优先级查询工单。
     *
     * @param priority 优先级
     * @return 工单列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/by-priority/{priority}")
    public OperationResponse<List<ScrmWorkOrderDto>> getOrdersByPriority(@PathVariable String priority) {
        return OperationResponse.build(scrmWorkOrderService.getOrdersByPriority(priority));
    }

    /**
     * 查询紧急工单。
     *
     * @return 紧急工单列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/urgent")
    public OperationResponse<List<ScrmWorkOrderDto>> getUrgentOrders() {
        return OperationResponse.build(scrmWorkOrderService.getUrgentOrders());
    }

    /**
     * 查询超期工单。
     *
     * @return 超期工单列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/overdue")
    public OperationResponse<List<ScrmWorkOrderDto>> getOverdueOrders() {
        return OperationResponse.build(scrmWorkOrderService.getOverdueOrders());
    }

    /**
     * 查询 SLA 违规工单。
     *
     * @return 违规工单列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/sla-breached")
    public OperationResponse<List<ScrmWorkOrderDto>> getSlaBreachedOrders() {
        return OperationResponse.build(scrmWorkOrderService.getSlaBreachedOrders());
    }

    // ============================================================
    // 工单动作
    // ============================================================

    /**
     * 分配工单 (更新处理人 → 记录日志 → 更新 SLA)。
     *
     * @param assignDto 分配请求
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 参数非法 / 工单已关闭
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/orders/assign")
    public OperationResponse<ScrmWorkOrderDto> assignOrder(@Valid @RequestBody ScrmWorkOrderAssignDto assignDto)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.assignOrder(assignDto));
    }

    /**
     * 接受工单。
     *
     * @param id          工单 ID
     * @param acceptorId  接受人 ID
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/orders/{id}/accept")
    public OperationResponse<ScrmWorkOrderDto> acceptOrder(@PathVariable Long id,
                                                            @RequestParam Long acceptorId) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.acceptOrder(id, acceptorId));
    }

    /**
     * 开始处理工单。
     *
     * @param id 工单 ID
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 工单已关闭
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/orders/{id}/start")
    public OperationResponse<ScrmWorkOrderDto> startOrder(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.startOrder(id));
    }

    /**
     * 解决工单 (记录解决方案 → 计算时长 → 更新 SLA)。
     *
     * @param id             工单 ID
     * @param resolution     解决方案 (可空)
     * @param resolutionCode 解决编码 (可空)
     * @param rootCause      根本原因 (可空)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/orders/{id}/resolve")
    public OperationResponse<ScrmWorkOrderDto> resolveOrder(@PathVariable Long id,
                                                              @RequestParam(required = false) String resolution,
                                                              @RequestParam(required = false) String resolutionCode,
                                                              @RequestParam(required = false) String rootCause)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.resolveOrder(id, resolution, resolutionCode, rootCause));
    }

    /**
     * 关闭工单 (记录满意度 → 关闭)。
     *
     * @param id                  工单 ID
     * @param satisfactionScore   满意度评分 1-5 (可空)
     * @param satisfactionComment 满意度评价 (可空)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 评分越界 / 状态非法
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/orders/{id}/close")
    public OperationResponse<ScrmWorkOrderDto> closeOrder(@PathVariable Long id,
                                                            @RequestParam(required = false) Integer satisfactionScore,
                                                            @RequestParam(required = false) String satisfactionComment)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.closeOrder(id, satisfactionScore, satisfactionComment));
    }

    /**
     * 取消工单。
     *
     * @param id     工单 ID
     * @param reason 取消原因 (可空)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/orders/{id}/cancel")
    public OperationResponse<ScrmWorkOrderDto> cancelOrder(@PathVariable Long id,
                                                              @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.cancelOrder(id, reason));
    }

    /**
     * 重新打开工单。
     *
     * @param id     工单 ID
     * @param reason 重开原因 (可空)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/orders/{id}/reopen")
    public OperationResponse<ScrmWorkOrderDto> reopenOrder(@PathVariable Long id,
                                                            @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.reopenOrder(id, reason));
    }

    /**
     * 升级工单。
     *
     * @param id           工单 ID
     * @param escalatedTo  升级到 (可空)
     * @param reason       升级原因 (可空)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 工单已关闭
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/orders/{id}/escalate")
    public OperationResponse<ScrmWorkOrderDto> escalateOrder(@PathVariable Long id,
                                                              @RequestParam(required = false) String escalatedTo,
                                                              @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.escalateOrder(id, escalatedTo, reason));
    }

    /**
     * 添加响应 (记录日志)。
     *
     * @param id         工单 ID
     * @param content    响应内容
     * @param isInternal 是否内部响应 (默认 false)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 内容为空
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/orders/{id}/response")
    public OperationResponse<ScrmWorkOrderDto> addResponse(@PathVariable Long id,
                                                            @RequestParam String content,
                                                            @RequestParam(defaultValue = "false") Boolean isInternal)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.addResponse(id, content, isInternal));
    }

    /**
     * 添加附件。
     *
     * @param id            工单 ID
     * @param attachmentUrl 附件 URL
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 附件 URL 为空
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/orders/{id}/attachment")
    public OperationResponse<ScrmWorkOrderDto> addAttachment(@PathVariable Long id,
                                                              @RequestParam String attachmentUrl)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.addAttachment(id, attachmentUrl));
    }

    /**
     * 查询工单时间线 (日志按时间升序分页)。
     *
     * @param id   工单 ID
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 日志分页结果
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/{id}/timeline")
    public OperationResponse<Page<ScrmWorkOrderLogDto>> getOrderTimeline(@PathVariable Long id,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size)
            throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createTime"));
        return OperationResponse.build(scrmWorkOrderService.getOrderTimeline(id, pageable));
    }

    /**
     * 查询关联工单。
     *
     * @param id 工单 ID
     * @return 关联工单列表
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/{id}/related")
    public OperationResponse<List<ScrmWorkOrderDto>> getRelatedOrders(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.getRelatedOrders(id));
    }

    /**
     * 合并工单。
     *
     * @param sourceId 源工单 ID
     * @param targetId 目标工单 ID
     * @return 目标工单
     * @throws ScrmException 工单不存在 / 源与目标相同
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/orders/merge")
    public OperationResponse<ScrmWorkOrderDto> mergeOrders(@RequestParam Long sourceId,
                                                            @RequestParam Long targetId) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.mergeOrders(sourceId, targetId));
    }

    /**
     * 拆分工单。
     *
     * @param id        原工单 ID
     * @param splitData 拆分数据 (title, description, orderType 等)
     * @return 新工单
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/orders/split")
    public OperationResponse<ScrmWorkOrderDto> splitOrder(@RequestParam Long id,
                                                          @RequestBody Map<String, Object> splitData)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.splitOrder(id, splitData));
    }

    /**
     * 克隆工单。
     *
     * @param id      原工单 ID
     * @param newTitle 新标题 (可空)
     * @return 新工单
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/orders/{id}/clone")
    public OperationResponse<ScrmWorkOrderDto> cloneOrder(@PathVariable Long id,
                                                            @RequestParam(required = false) String newTitle)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.cloneOrder(id, newTitle));
    }

    /**
     * 批量分配工单。
     *
     * @param orderIds     工单 ID 列表
     * @param assigneeId   处理人 ID
     * @param assigneeName 处理人名称 (可空)
     * @param department   处理部门 (可空)
     * @return 分配成功的工单数
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/orders/batch-assign")
    public OperationResponse<Integer> batchAssign(@RequestParam List<Long> orderIds,
                                                    @RequestParam Long assigneeId,
                                                    @RequestParam(required = false) String assigneeName,
                                                    @RequestParam(required = false) String department) {
        return OperationResponse.build(scrmWorkOrderService.batchAssign(orderIds, assigneeId, assigneeName,
                department));
    }

    /**
     * 批量关闭工单。
     *
     * @param orderIds            工单 ID 列表
     * @param satisfactionScore   满意度评分 (可空)
     * @param satisfactionComment 满意度评价 (可空)
     * @return 关闭成功的工单数
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/orders/batch-close")
    public OperationResponse<Integer> batchClose(@RequestParam List<Long> orderIds,
                                                   @RequestParam(required = false) Integer satisfactionScore,
                                                   @RequestParam(required = false) String satisfactionComment) {
        return OperationResponse.build(scrmWorkOrderService.batchClose(orderIds, satisfactionScore,
                satisfactionComment));
    }

    /**
     * 导出工单 (工单详情 + 日志时间线)。
     *
     * @param id 工单 ID
     * @return 导出数据
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/orders/{id}/export")
    public OperationResponse<Map<String, Object>> exportOrder(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.exportOrder(id));
    }

    // ============================================================
    // 工单日志
    // ============================================================

    /**
     * 添加工单日志。
     *
     * @param dto 日志参数
     * @return 创建后的日志
     * @throws ScrmException 工单不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/logs")
    public OperationResponse<ScrmWorkOrderLogDto> addLog(@Valid @RequestBody ScrmWorkOrderLogDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.addLog(dto));
    }

    /**
     * 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志详情
     * @throws ScrmException 日志不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/logs/{id}")
    public OperationResponse<ScrmWorkOrderLogDto> getLog(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.getLog(id));
    }

    /**
     * 按工单查询日志列表。
     *
     * @param orderId 工单 ID
     * @return 日志列表
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/logs/by-order/{orderId}")
    public OperationResponse<List<ScrmWorkOrderLogDto>> getLogsByOrder(@PathVariable Long orderId)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.getLogsByOrder(orderId));
    }

    /**
     * 按日志类型查询日志列表。
     *
     * @param type 日志类型
     * @return 日志列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/logs/by-type/{type}")
    public OperationResponse<List<ScrmWorkOrderLogDto>> getLogsByType(@PathVariable String type) {
        return OperationResponse.build(scrmWorkOrderService.getLogsByType(type));
    }

    /**
     * 按操作人查询日志列表。
     *
     * @param operatorId 操作人 ID
     * @return 日志列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/logs/by-operator/{operatorId}")
    public OperationResponse<List<ScrmWorkOrderLogDto>> getLogsByOperator(@PathVariable Long operatorId) {
        return OperationResponse.build(scrmWorkOrderService.getLogsByOperator(operatorId));
    }

    /**
     * 添加内部备注。
     *
     * @param orderId      工单 ID
     * @param note         备注内容
     * @param operatorName 操作人名称
     * @return 创建后的日志
     * @throws ScrmException 工单不存在 / 内容为空
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/logs/note")
    public OperationResponse<ScrmWorkOrderLogDto> addInternalNote(@RequestParam Long orderId,
                                                                    @RequestParam String note,
                                                                    @RequestParam String operatorName)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.addInternalNote(orderId, note, operatorName));
    }

    /**
     * 查询工单完整历史。
     *
     * @param orderId 工单 ID
     * @return 日志列表
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/logs/history/{orderId}")
    public OperationResponse<List<ScrmWorkOrderLogDto>> getOrderHistory(@PathVariable Long orderId)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.getOrderHistory(orderId));
    }

    // ============================================================
    // SLA 策略管理
    // ============================================================

    /**
     * 创建 SLA 策略。
     *
     * @param dto SLA 策略参数
     * @return 创建后的 SLA 策略
     * @throws ScrmException 参数非法 / 名称或编码重复
     */
    @RequirePermission(resource = "scrm_work_order", action = "create")
    @PostMapping("/sla/policies")
    public OperationResponse<ScrmWorkOrderSlaDto> createSlaPolicy(@Valid @RequestBody ScrmWorkOrderSlaDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.createSlaPolicy(dto));
    }

    /**
     * 更新 SLA 策略 (字段非空才覆盖)。
     *
     * @param id  SLA 策略 ID
     * @param dto SLA 策略参数
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_work_order", action = "update")
    @PutMapping("/sla/policies/{id}")
    public OperationResponse<ScrmWorkOrderSlaDto> updateSlaPolicy(@PathVariable Long id,
                                                                    @RequestBody ScrmWorkOrderSlaDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.updateSlaPolicy(id, dto));
    }

    /**
     * 删除 SLA 策略。
     *
     * @param id SLA 策略 ID
     * @return 空响应
     * @throws ScrmException 策略不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "delete")
    @DeleteMapping("/sla/policies/{id}")
    public OperationResponse<Void> deleteSlaPolicy(@PathVariable Long id) throws ScrmException {
        scrmWorkOrderService.deleteSlaPolicy(id);
        return OperationResponse.build();
    }

    /**
     * 查询 SLA 策略详情。
     *
     * @param id SLA 策略 ID
     * @return SLA 策略详情
     * @throws ScrmException 策略不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/sla/policies/{id}")
    public OperationResponse<ScrmWorkOrderSlaDto> getSlaPolicy(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.getSlaPolicy(id));
    }

    /**
     * 按策略编码查询 SLA 策略。
     *
     * @param code 策略编码
     * @return SLA 策略详情
     * @throws ScrmException 策略不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/sla/policies/code/{code}")
    public OperationResponse<ScrmWorkOrderSlaDto> getSlaPolicyByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.getSlaPolicyByCode(code));
    }

    /**
     * 查询 SLA 策略列表。
     *
     * @return SLA 策略列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/sla/policies/list")
    public OperationResponse<List<ScrmWorkOrderSlaDto>> listSlaPolicies() {
        return OperationResponse.build(scrmWorkOrderService.listSlaPolicies());
    }

    /**
     * 启用 SLA 策略。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/sla/policies/{id}/enable")
    public OperationResponse<ScrmWorkOrderSlaDto> enableSlaPolicy(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.enableSlaPolicy(id));
    }

    /**
     * 禁用 SLA 策略。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/sla/policies/{id}/disable")
    public OperationResponse<ScrmWorkOrderSlaDto> disableSlaPolicy(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.disableSlaPolicy(id));
    }

    /**
     * 设置默认 SLA 策略。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/sla/policies/{id}/default")
    public OperationResponse<ScrmWorkOrderSlaDto> setDefaultSlaPolicy(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.setDefaultSlaPolicy(id));
    }

    /**
     * 应用 SLA 策略到工单 (计算截止时间)。
     *
     * @param orderId  工单 ID
     * @param policyId SLA 策略 ID
     * @return 更新后的工单
     * @throws ScrmException 工单或策略不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/sla/apply")
    public OperationResponse<ScrmWorkOrderDto> applySlaPolicy(@RequestParam Long orderId,
                                                                @RequestParam Long policyId) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.applySlaPolicy(orderId, policyId));
    }

    /**
     * 检查 SLA 违规 (扫描超时工单 → 标记违规 → 触发升级)。
     *
     * @return 标记违规的工单数
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/sla/check-breaches")
    public OperationResponse<Integer> checkSlaBreaches() {
        return OperationResponse.build(scrmWorkOrderService.checkSlaBreaches());
    }

    /**
     * 发送 SLA 预警。
     *
     * @return 预警的工单数
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/sla/send-warnings")
    public OperationResponse<Integer> sendSlaWarnings() {
        return OperationResponse.build(scrmWorkOrderService.sendSlaWarnings());
    }

    /**
     * SLA 升级工单 (按级别)。
     *
     * @param orderId 工单 ID
     * @param level   升级级别 (1/2/3)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/sla/escalate")
    public OperationResponse<ScrmWorkOrderDto> escalateSla(@RequestParam Long orderId,
                                                            @RequestParam int level) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.escalateOrder(orderId, level));
    }

    /**
     * 查询 SLA 达标率。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return SLA 达标率统计
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/sla/compliance")
    public OperationResponse<Map<String, Object>> getSlaCompliance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmWorkOrderService.getSlaCompliance(startTime, endTime));
    }

    /**
     * 查询单个 SLA 策略的统计。
     *
     * @param policyId SLA 策略 ID
     * @return SLA 统计
     * @throws ScrmException 策略不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/sla/stats/{policyId}")
    public OperationResponse<Map<String, Object>> getSlaStats(@PathVariable Long policyId) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.getSlaStats(policyId));
    }

    /**
     * 更新 SLA 策略的统计字段。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    @RequirePermission(resource = "scrm_work_order", action = "execute")
    @PostMapping("/sla/policies/{id}/stats")
    public OperationResponse<ScrmWorkOrderSlaDto> updateSlaStats(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.updateSlaStats(id));
    }

    /**
     * 按 SLA 状态查询工单。
     *
     * @param slaStatus SLA 状态: BREACHED / MET / PENDING
     * @param page      页码 (从 0 开始, 默认 0)
     * @param size      每页大小 (默认 20)
     * @return 工单分页结果
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/sla/by-sla/{slaStatus}")
    public OperationResponse<Page<ScrmWorkOrderDto>> getOrdersBySla(@PathVariable String slaStatus,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmWorkOrderService.getOrdersBySla(slaStatus, pageable));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 工单概览。
     *
     * @return 概览数据
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getWorkOrderOverview() {
        return OperationResponse.build(scrmWorkOrderService.getWorkOrderOverview());
    }

    /**
     * 工单统计: 总数 / 各类型 / 各状态 / 各优先级。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/work-orders")
    public OperationResponse<Map<String, Object>> getWorkOrderStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmWorkOrderService.getWorkOrderStats(startTime, endTime));
    }

    /**
     * SLA 统计。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return SLA 统计
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/sla")
    public OperationResponse<Map<String, Object>> getSlaStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmWorkOrderService.getSlaStats(startTime, endTime));
    }

    /**
     * 响应时间统计。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 响应时间统计
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/response-time")
    public OperationResponse<Map<String, Object>> getResponseTimeStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmWorkOrderService.getResponseTimeStats(startTime, endTime));
    }

    /**
     * 解决时间统计。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 解决时间统计
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/resolution-time")
    public OperationResponse<Map<String, Object>> getResolutionTimeStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmWorkOrderService.getResolutionTimeStats(startTime, endTime));
    }

    /**
     * 满意度统计。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 满意度统计
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/satisfaction")
    public OperationResponse<Map<String, Object>> getSatisfactionStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmWorkOrderService.getSatisfactionStats(startTime, endTime));
    }

    /**
     * 处理人工作量统计。
     *
     * @param assigneeId 处理人 ID
     * @return 统计结果
     * @throws ScrmException 处理人 ID 为空
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/workload/{assigneeId}")
    public OperationResponse<Map<String, Object>> getAssigneeWorkload(@PathVariable Long assigneeId)
            throws ScrmException {
        return OperationResponse.build(scrmWorkOrderService.getAssigneeWorkload(assigneeId));
    }

    /**
     * 工单类型分布。
     *
     * @return 类型分布
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/type-distribution")
    public OperationResponse<Map<String, Object>> getOrderTypeDistribution() {
        return OperationResponse.build(scrmWorkOrderService.getOrderTypeDistribution());
    }

    /**
     * 工单状态分布。
     *
     * @return 状态分布
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/status-distribution")
    public OperationResponse<Map<String, Object>> getOrderStatusDistribution() {
        return OperationResponse.build(scrmWorkOrderService.getOrderStatusDistribution());
    }

    /**
     * 工单趋势 (按月统计)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<Map<String, Object>> getOrderTrend(
            @RequestParam(defaultValue = "6") int months) {
        return OperationResponse.build(scrmWorkOrderService.getOrderTrend(months));
    }

    /**
     * Top 处理人 (按已解决/已关闭工单数排名)。
     *
     * @param limit 返回数量 (默认 10)
     * @return Top 处理人列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/top-assignees")
    public OperationResponse<List<Map<String, Object>>> getTopAssignees(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmWorkOrderService.getTopAssignees(limit));
    }

    /**
     * Top 产品 (按工单数排名)。
     *
     * @param limit 返回数量 (默认 10)
     * @return Top 产品列表
     */
    @RequirePermission(resource = "scrm_work_order", action = "read")
    @GetMapping("/stats/top-products")
    public OperationResponse<List<Map<String, Object>>> getTopProducts(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmWorkOrderService.getTopProducts(limit));
    }
}
