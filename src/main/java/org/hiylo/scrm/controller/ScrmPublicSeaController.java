/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPublicSeaController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmLeadAssignmentDto;
import org.hiylo.scrm.dto.ScrmPublicSeaCustomerDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmPublicSeaService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户公海/线索分配控制器
 * <p>
 * 提供公海池线索的列表查询、详情查看、销售领取、管理员分配/批量分配、转移、回收、
 * 转正与分配历史查询接口。权限由 gateway-server 统一鉴权,
 * {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/public-sea")
@RequiredArgsConstructor
public class ScrmPublicSeaController {

    /** 公海服务 */
    private final ScrmPublicSeaService publicSeaService;

    /**
     * 分页查询公海池列表, 支持按平台类型、生命周期、状态与关键词过滤。
     *
     * @param platformType 平台类型过滤 (可选)
     * @param lifecycle    生命周期过滤 (可选)
     * @param status       公海状态过滤 (可选)
     * @param keyword      关键词过滤 (可选)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 公海客户分页结果
     */
    @RequirePermission(resource = "scrm_public_sea", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmPublicSeaCustomerDto>> list(
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(publicSeaService.listPublicSea(
                platformType, lifecycle, status, keyword, PageRequest.of(page, size)));
    }

    /**
     * 查询公海客户详情。
     *
     * @param id 公海客户 ID
     * @return 公海客户详情
     * @throws ScrmException 客户不存在
     */
    @RequirePermission(resource = "scrm_public_sea", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmPublicSeaCustomerDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(publicSeaService.getCustomer(id));
    }

    /**
     * 销售领取公海客户 (AVAILABLE → ASSIGNED)。
     *
     * @param id     公海客户 ID
     * @param userId 领取人 userId
     * @return 更新后的公海客户
     * @throws ScrmException 客户不存在 / 状态不允许领取
     */
    @RequirePermission(resource = "scrm_public_sea", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/claim")
    public OperationResponse<ScrmPublicSeaCustomerDto> claim(@PathVariable Long id,
                                                              @RequestParam String userId)
            throws ScrmException {
        return OperationResponse.build(publicSeaService.claimCustomer(id, userId));
    }

    /**
     * 管理员分配公海客户 (AVAILABLE → ASSIGNED)。
     *
     * @param id         公海客户 ID
     * @param userId     被分配人 userId
     * @param assignedBy 分配人 userId (管理员)
     * @return 更新后的公海客户
     * @throws ScrmException 客户不存在 / 状态不允许分配
     */
    @RequirePermission(resource = "scrm_public_sea", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/assign")
    public OperationResponse<ScrmPublicSeaCustomerDto> assign(@PathVariable Long id,
                                                               @RequestParam String userId,
                                                               @RequestParam(required = false) String assignedBy)
            throws ScrmException {
        return OperationResponse.build(publicSeaService.assignCustomer(id, userId, assignedBy));
    }

    /**
     * 转移公海客户给他人 (ASSIGNED → ASSIGNED)。
     *
     * @param id       公海客户 ID
     * @param toUserId 接收人 userId
     * @param body     包含 note 字段的请求体 (可选)
     * @return 更新后的公海客户
     * @throws ScrmException 客户不存在 / 状态不允许转移
     */
    @RequirePermission(resource = "scrm_public_sea", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/transfer")
    public OperationResponse<ScrmPublicSeaCustomerDto> transfer(@PathVariable Long id,
                                                                  @RequestParam String toUserId,
                                                                   @RequestBody(required = false)
                                                                           java.util.Map<String, String> body)
            throws ScrmException {
        String note = body != null ? body.get("note") : null;
        return OperationResponse.build(publicSeaService.transferCustomer(id, toUserId, note));
    }

    /**
     * 回收公海客户到公海 (ASSIGNED → AVAILABLE)。
     *
     * @param id     公海客户 ID
     * @param reason 回收原因 (可选)
     * @return 更新后的公海客户
     * @throws ScrmException 客户不存在 / 状态不允许回收
     */
    @RequirePermission(resource = "scrm_public_sea", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/recall")
    public OperationResponse<ScrmPublicSeaCustomerDto> recall(@PathVariable Long id,
                                                               @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(publicSeaService.recallCustomer(id, reason));
    }

    /**
     * 批量分配公海客户。
     * <p>
     * 请求体携带客户 ID 列表, 逐个分配给指定销售。单个客户失败不影响其他客户,
     * 返回成功数与失败列表。
     * </p>
     *
     * @param request 批量分配请求体
     * @return 批量操作结果
     */
    @RequirePermission(resource = "scrm_public_sea", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/batch-assign")
    public OperationResponse<ScrmPublicSeaService.BatchAssignResult> batchAssign(
            @RequestBody BatchAssignRequest request) {
        if (request.customerIds() == null || request.customerIds().isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        if (request.userId() == null || request.userId().isBlank()) {
            throw ScrmException.badRequest("被分配人 userId 不能为空");
        }
        return OperationResponse.build(
                publicSeaService.batchAssign(request.customerIds(), request.userId(), request.assignedBy()));
    }

    /**
     * 批量分配请求体
 * @since V1.0
     * @author Hsi Chu
     */
    public record BatchAssignRequest(List<Long> customerIds, String userId, String assignedBy) {
    }

    /**
     * 将公海客户转为正式客户 (从公海移出)。
     *
     * @param id             公海客户 ID
     * @param ownerAccountId 正式客户归属账号 ID
     * @return 更新后的公海客户
     * @throws ScrmException 客户不存在 / 状态不允许转正
     */
    @RequirePermission(resource = "scrm_public_sea", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/convert")
    public OperationResponse<ScrmPublicSeaCustomerDto> convert(@PathVariable Long id,
                                                                @RequestParam Long ownerAccountId)
            throws ScrmException {
        return OperationResponse.build(publicSeaService.convertToCustomer(id, ownerAccountId));
    }

    /**
     * 查询公海客户的分配历史 (按 ID 倒序, 最新分配在前)。
     *
     * @param id 公海客户 ID
     * @return 分配流水列表
     * @throws ScrmException 客户不存在
     */
    @RequirePermission(resource = "scrm_public_sea", action = "read")
    @GetMapping("/{id}/assignments")
    public OperationResponse<List<ScrmLeadAssignmentDto>> assignments(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(publicSeaService.getAssignmentHistory(id));
    }

    /**
     * 查询我的线索 (当前归属人为 userId 的公海客户)。
     *
     * @param userId 归属人 userId
     * @param status 公海状态过滤 (可选)
     * @param page   页码 (从 0 开始, 默认 0)
     * @param size   每页大小 (默认 20)
     * @return 我的线索分页结果
     */
    @RequirePermission(resource = "scrm_public_sea", action = "read")
    @GetMapping("/my-leads")
    public OperationResponse<Page<ScrmPublicSeaCustomerDto>> myLeads(@RequestParam String userId,
                                                                       @RequestParam(required = false) String status,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(
                publicSeaService.getMyLeads(userId, status, PageRequest.of(page, size)));
    }
}
