/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmTicketAssignDto;
import org.hiylo.scrm.dto.ScrmTicketCommentDto;
import org.hiylo.scrm.dto.ScrmTicketDto;
import org.hiylo.scrm.dto.ScrmTicketHistoryDto;
import org.hiylo.scrm.dto.ScrmTicketSatisfactionDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmTicketService;
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
 * SCRM 客户工单/售后服务管理控制器。
 * <p>
 * 提供工单增删改查、分配/状态变更/优先级变更/升级/重新打开/关闭、评论与内部备注、
 * 流转历史与时间线、SLA 跟踪、满意度评价以及工单统计接口。权限由 gateway-server 统一鉴权,
 * {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/tickets")
@RequiredArgsConstructor
public class ScrmTicketController {

    /** 工单服务 */
    private final ScrmTicketService scrmTicketService;

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
    @RequirePermission(resource = "scrm_ticket", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建工单过于频繁，请稍后重试")
    @PostMapping
    public OperationResponse<ScrmTicketDto> createTicket(@Valid @RequestBody ScrmTicketDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.createTicket(dto));
    }

    /**
     * 更新工单 (字段非空才覆盖)。
     *
     * @param id  工单 ID
     * @param dto 工单参数
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_ticket", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmTicketDto> updateTicket(@PathVariable Long id,
                                                          @RequestBody ScrmTicketDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.updateTicket(id, dto));
    }

    /**
     * 删除工单 (级联清理评论与历史)。
     *
     * @param id 工单 ID
     * @return 空响应
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_ticket", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteTicket(@PathVariable Long id) throws ScrmException {
        scrmTicketService.deleteTicket(id);
        return OperationResponse.build();
    }

    /**
     * 查询工单详情。
     *
     * @param id 工单 ID
     * @return 工单详情
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_ticket", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmTicketDto> getTicket(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTicketService.getTicket(id));
    }

    /**
     * 分页查询工单, 支持按状态、优先级、类别、处理人、客户、来源、时间范围与关键词过滤。
     *
     * @param status     状态过滤 (可空): OPEN / IN_PROGRESS / RESOLVED / CLOSED / REOPENED / CANCELLED
     * @param priority   优先级过滤 (可空): URGENT / HIGH / MEDIUM / LOW
     * @param category 类别过滤 (可空): PRODUCT_ISSUE / SERVICE_COMPLAINT / REFUND / EXCHANGE / TECHNICAL / DELIVERY / *
       * BILLING / OTHER * @param assigneeId 处理人 ID 过滤 (可空)
     * @param customerId 客户 ID 过滤 (可空)
     * @param source     来源过滤 (可空): CUSTOMER / AGENT / SYSTEM / PHONE / EMAIL / CHAT
     * @param startTime  起始时间 (按创建时间, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime    截止时间 (按创建时间, 可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param keyword    关键词过滤, 匹配工单编号/标题/描述 (可空)
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 工单分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_ticket", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmTicketDto>> listTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmTicketService.listTickets(status, priority, category, assigneeId,
                customerId, source, startTime, endTime, keyword, pageable));
    }

    /**
     * 按工单编号查询工单。
     *
     * @param ticketNo 工单编号
     * @return 工单详情
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_ticket", action = "read")
    @GetMapping("/no/{ticketNo}")
    public OperationResponse<ScrmTicketDto> getTicketByNo(@PathVariable String ticketNo) throws ScrmException {
        return OperationResponse.build(scrmTicketService.getTicketByNo(ticketNo));
    }

    // ============================================================
    // 工单动作
    // ============================================================

    /**
     * 分配工单 (assigneeId 与 teamId 至少传其一)。
     *
     * @param id        工单 ID
     * @param assignDto 分配请求
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 参数非法 / 工单已关闭
     */
    @RequirePermission(resource = "scrm_ticket", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/assign")
    public OperationResponse<ScrmTicketDto> assignTicket(@PathVariable Long id,
                                                          @Valid @RequestBody ScrmTicketAssignDto assignDto)
            throws ScrmException {
        assignDto.setTicketId(id);
        return OperationResponse.build(scrmTicketService.assignTicket(assignDto));
    }

    /**
     * 变更工单状态。
     *
     * @param id     工单 ID
     * @param status 目标状态: OPEN / IN_PROGRESS / RESOLVED / CLOSED / REOPENED / CANCELLED
     * @param note   变更备注 (可空)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法 / 状态流转非法
     */
    @RequirePermission(resource = "scrm_ticket", action = "execute")
    @PostMapping("/{id}/status")
    public OperationResponse<ScrmTicketDto> changeStatus(@PathVariable Long id,
                                                          @RequestParam String status,
                                                          @RequestParam(required = false) String note)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.changeStatus(id, status, note));
    }

    /**
     * 变更工单优先级。
     *
     * @param id       工单 ID
     * @param priority 目标优先级: URGENT / HIGH / MEDIUM / LOW
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 优先级非法 / 工单已关闭
     */
    @RequirePermission(resource = "scrm_ticket", action = "execute")
    @PostMapping("/{id}/priority")
    public OperationResponse<ScrmTicketDto> changePriority(@PathVariable Long id,
                                                            @RequestParam String priority)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.changePriority(id, priority));
    }

    /**
     * 升级处理 (优先级提升一级, 状态置 IN_PROGRESS)。
     *
     * @param id     工单 ID
     * @param reason 升级原因 (可空)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 工单已关闭
     */
    @RequirePermission(resource = "scrm_ticket", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/{id}/escalate")
    public OperationResponse<ScrmTicketDto> escalateTicket(@PathVariable Long id,
                                                            @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.escalateTicket(id, reason));
    }

    /**
     * 重新打开工单 (仅 RESOLVED / CLOSED 状态可重新打开)。
     *
     * @param id     工单 ID
     * @param reason 重新打开原因 (可空)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_ticket", action = "execute")
    @PostMapping("/{id}/reopen")
    public OperationResponse<ScrmTicketDto> reopenTicket(@PathVariable Long id,
                                                          @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.reopenTicket(id, reason));
    }

    /**
     * 关闭工单。
     *
     * @param id         工单 ID
     * @param resolution 解决方案 (可空)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_ticket", action = "execute")
    @PostMapping("/{id}/close")
    public OperationResponse<ScrmTicketDto> closeTicket(@PathVariable Long id,
                                                         @RequestParam(required = false) String resolution)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.closeTicket(id, resolution));
    }

    // ============================================================
    // 评论
    // ============================================================

    /**
     * 添加评论。
     *
     * @param dto 评论参数
     * @return 创建后的评论
     * @throws ScrmException 工单不存在 / 参数非法 / 工单已关闭
     */
    @RequirePermission(resource = "scrm_ticket", action = "execute")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/comments")
    public OperationResponse<ScrmTicketCommentDto> addComment(@Valid @RequestBody ScrmTicketCommentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.addComment(dto));
    }

    /**
     * 查询工单评论列表 (按评论发生时间升序)。
     *
     * @param ticketId 工单 ID
     * @return 评论列表
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_ticket", action = "read")
    @GetMapping("/comments/{ticketId}")
    public OperationResponse<List<ScrmTicketCommentDto>> listComments(@PathVariable Long ticketId)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.listComments(ticketId));
    }

    /**
     * 添加内部备注。
     *
     * @param ticketId 工单 ID
     * @param content  备注内容
     * @param authorId 作者 ID
     * @return 创建后的评论
     * @throws ScrmException 工单不存在 / 参数非法 / 工单已关闭
     */
    @RequirePermission(resource = "scrm_ticket", action = "execute")
    @PostMapping("/comments/{ticketId}/internal")
    public OperationResponse<ScrmTicketCommentDto> addInternalNote(
            @PathVariable Long ticketId,
            @RequestParam String content,
            @RequestParam String authorId) throws ScrmException {
        return OperationResponse.build(scrmTicketService.addInternalNote(ticketId, content, authorId));
    }

    // ============================================================
    // 历史 / 时间线
    // ============================================================

    /**
     * 查询工单流转历史列表 (按动作时间升序)。
     *
     * @param ticketId 工单 ID
     * @return 历史列表
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_ticket", action = "read")
    @GetMapping("/history/{ticketId}")
    public OperationResponse<List<ScrmTicketHistoryDto>> listHistory(@PathVariable Long ticketId)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.listHistory(ticketId));
    }

    /**
     * 查询工单时间线 (评论与历史合并, 按时间升序)。
     *
     * @param ticketId 工单 ID
     * @return 时间线列表
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_ticket", action = "read")
    @GetMapping("/timeline/{ticketId}")
    public OperationResponse<List<Map<String, Object>>> getTicketTimeline(@PathVariable Long ticketId)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.getTicketTimeline(ticketId));
    }

    // ============================================================
    // SLA
    // ============================================================

    /**
     * 检查工单 SLA 状态 (是否超期 / 剩余分钟数)。
     *
     * @param id 工单 ID
     * @return SLA 状态
     * @throws ScrmException 工单不存在
     */
    @RequirePermission(resource = "scrm_ticket", action = "read")
    @GetMapping("/sla/{id}")
    public OperationResponse<Map<String, Object>> checkSla(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmTicketService.checkSla(id));
    }

    /**
     * 更新工单 SLA 到期时间。
     *
     * @param id        工单 ID
     * @param slaDueAt  SLA 到期时间 (ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 工单已关闭
     */
    @RequirePermission(resource = "scrm_ticket", action = "update")
    @PostMapping("/sla/{id}")
    public OperationResponse<ScrmTicketDto> updateSla(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime slaDueAt)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.updateSla(id, slaDueAt));
    }

    /**
     * 分页查询 SLA 超期工单。
     *
     * @param page 页码 (从 0 开始, 默认 0)
     * @param size 每页大小 (默认 20)
     * @return 超期工单分页结果
     */
    @RequirePermission(resource = "scrm_ticket", action = "read")
    @GetMapping("/overdue")
    public OperationResponse<Page<ScrmTicketDto>> getOverdueTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "slaDueAt"));
        return OperationResponse.build(scrmTicketService.getOverdueTickets(pageable));
    }

    // ============================================================
    // 满意度
    // ============================================================

    /**
     * 提交满意度评价 (仅 RESOLVED / CLOSED / REOPENED 状态可提交)。
     *
     * @param dto 评价请求
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 评分越界 / 状态非法
     */
    @RequirePermission(resource = "scrm_ticket", action = "execute")
    @PostMapping("/satisfaction")
    public OperationResponse<ScrmTicketDto> submitSatisfaction(@Valid @RequestBody ScrmTicketSatisfactionDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.submitSatisfaction(dto));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 工单统计: 总数 / 各状态 / 各优先级 / 平均解决时长 / 平均满意度。
     * <p>时间范围按工单创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_ticket", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getTicketStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmTicketService.getTicketStats(startTime, endTime));
    }

    /**
     * 处理人工作量统计: 各状态工单数。
     *
     * @param assigneeId 处理人 ID
     * @return 统计结果
     * @throws ScrmException 处理人 ID 为空
     */
    @RequirePermission(resource = "scrm_ticket", action = "read")
    @GetMapping("/stats/assignee/{assigneeId}")
    public OperationResponse<Map<String, Object>> getAssigneeWorkload(@PathVariable String assigneeId)
            throws ScrmException {
        return OperationResponse.build(scrmTicketService.getAssigneeWorkload(assigneeId));
    }

    /**
     * 分类统计: 各工单类别数量。
     * <p>时间范围按工单创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_ticket", action = "read")
    @GetMapping("/stats/category")
    public OperationResponse<Map<String, Object>> getCategoryStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmTicketService.getCategoryStats(startTime, endTime));
    }
}
