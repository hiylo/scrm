/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmPolicyPushController.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.dto.ScrmPolicyPushTaskDto;
import org.hiylo.scrm.entity.ScrmPolicyPushTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.finance.policy.ScrmPolicyPushService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 政策推送控制器（模块 D）。
 * <p>
 * 政策推送任务管理（创建/预计算快照/提交审核/通过/驳回/撤销/发布/重试）+
 * 抽样核验清单 + 任务列表查询。端点一律 {@code @RequirePermission} 声明资源与动作
 * 元数据。<b>双重人工闸门</b>: 政策条目审核（知识库）+ 推送任务抽样/全量核验,
 * 未过审零对外消息（发布端点内部强校验）。任务详情仅创建人、政策审核人、
 * 命中客户对应销售/会计可见（gateway RBAC 归属校验）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/policy-pushes")
@RequiredArgsConstructor
public class ScrmPolicyPushController {

    /** 政策推送核心服务 */
    private final ScrmPolicyPushService policyPushService;

    /** 数据范围服务（政策推送是全局任务，仅管理视角可读，避免快照客户清单外泄） */
    private final org.hiylo.scrm.service.DataScopeService dataScopeService;

    /**
     * 创建政策推送任务（草稿, 预计算命中客户快照）。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 政策条目未审核 / 已有任务
     */
    @RequirePermission(resource = "scrm_policy_push_task", action = "create")
    @PostMapping
    public OperationResponse<ScrmPolicyPushTaskEntity> createTask(
            @Valid @RequestBody ScrmPolicyPushTaskDto dto) throws ScrmException {
        return OperationResponse.build(policyPushService.createTask(dto));
    }

    /**
     * 重新预计算命中客户快照。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许
     */
    @RequirePermission(resource = "scrm_policy_push_task", action = "update")
    @PostMapping("/{id}/refresh")
    public OperationResponse<ScrmPolicyPushTaskEntity> refreshSnapshot(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(policyPushService.refreshSnapshot(id));
    }

    /**
     * 提交审核（DRAFT/REJECTED → PENDING_AUDIT）。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许
     */
    @RequirePermission(resource = "scrm_policy_push_task", action = "update")
    @PostMapping("/{id}/submit")
    public OperationResponse<ScrmPolicyPushTaskEntity> submit(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(policyPushService.submit(id));
    }

    /**
     * 审核通过（PENDING_AUDIT → APPROVED）。
     *
     * @param id      任务 ID
     * @param auditor 审核人（可空, 默认当前操作人）
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许
     */
    @RequirePermission(resource = "scrm_policy_push_task", action = "update")
    @PostMapping("/{id}/approve")
    public OperationResponse<ScrmPolicyPushTaskEntity> approve(
            @PathVariable Long id, @RequestParam(required = false) String auditor) throws ScrmException {
        return OperationResponse.build(policyPushService.approve(id, auditor, null));
    }

    /**
     * 审核驳回（PENDING_AUDIT → REJECTED）。
     *
     * @param id     任务 ID
     * @param reason 驳回原因
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许
     */
    @RequirePermission(resource = "scrm_policy_push_task", action = "update")
    @PostMapping("/{id}/reject")
    public OperationResponse<ScrmPolicyPushTaskEntity> reject(
            @PathVariable Long id, @RequestParam String reason) throws ScrmException {
        return OperationResponse.build(policyPushService.reject(id, null, reason));
    }

    /**
     * 撤销任务（未发布前）。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 已发布不可撤销
     */
    @RequirePermission(resource = "scrm_policy_push_task", action = "update")
    @PostMapping("/{id}/cancel")
    public OperationResponse<ScrmPolicyPushTaskEntity> cancel(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(policyPushService.cancel(id));
    }

    /**
     * 发布（APPROVED → PUBLISHING → COMPLETED, 幂等）。
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 未过审 / 政策条目不可用
     */
    @RequirePermission(resource = "scrm_policy_push_task", action = "execute")
    @PostMapping("/{id}/publish")
    public OperationResponse<ScrmPolicyPushTaskEntity> publish(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(policyPushService.publish(id));
    }

    /**
     * 抽样核验清单（auditMode=SAMPLING 时取前 N 个命中客户供人工核验）。
     *
     * @param id 任务 ID
     * @return 抽样客户列表 [{customerId, customerName}]
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_policy_push_task", action = "read")
    @GetMapping("/{id}/sampling")
    public OperationResponse<List<Map<String, Object>>> samplingList(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(policyPushService.samplingList(id));
    }

    /**
     * 分页查询政策推送任务。
     *
     * @param page    页码（默认 0）
     * @param size    每页大小（默认 20）
     * @param status  状态过滤（可空）
     * @param keyword 标题关键词（可空）
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_policy_push_task", action = "read")
    @GetMapping
    public OperationResponse<Page<ScrmPolicyPushTaskEntity>> listTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        assertManagementRole();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(policyPushService.listTasks(status, keyword, pageable));
    }

    /**
     * 查询政策推送任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_policy_push_task", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmPolicyPushTaskEntity> getTask(@PathVariable Long id)
            throws ScrmException {
        assertManagementRole();
        return OperationResponse.build(policyPushService.getTask(id));
    }

    /**
     * 校验当前用户是否管理类角色。
     * <p>
     * 政策推送任务虽不带 {@code companyId}，但 {@code snapshotCustomerIds}
     * 含全量命中客户清单，会计看到等于跨主体泄露客户范围。
     * 菜单已只对 admin/manager 开放，这里把同一口径下沉到接口层，
     * 避免直接拼 URL 绕过菜单。
     * </p>
     *
     * @throws ScrmException 非管理角色
     */
    private void assertManagementRole() throws ScrmException {
        if (!dataScopeService.isManagement(dataScopeService.getCurrentRole())) {
            throw ScrmException.forbidden("无权访问政策推送任务: 需要管理角色");
        }
    }
}
