/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmSegmentCalculationResultDto;
import org.hiylo.scrm.dto.ScrmSegmentCompareDto;
import org.hiylo.scrm.dto.ScrmSegmentDto;
import org.hiylo.scrm.entity.ScrmSegmentEntity;
import org.hiylo.scrm.entity.ScrmSegmentHistoryEntity;
import org.hiylo.scrm.entity.ScrmSegmentMemberEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmSegmentService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * SCRM 客户分群控制器。
 * <p>
 * 提供动态客户分群管理、分群计算、成员管理、历史快照与趋势、分群对比及统计接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/segments")
@RequiredArgsConstructor
public class ScrmSegmentController {

    /** 客户分群服务 */
    private final ScrmSegmentService scrmSegmentService;

    // ============================================================
    // 分群管理
    // ============================================================

    /**
     * 创建客户分群。
     *
     * @param dto 分群参数
     * @return 创建后的分群
     * @throws ScrmException 参数非法 / 编码重复
     */
    @RequirePermission(resource = "scrm_segment", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmSegmentEntity> createSegment(@Valid @RequestBody ScrmSegmentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSegmentService.createSegment(dto));
    }

    /**
     * 更新客户分群。
     *
     * @param id  分群 ID
     * @param dto 分群参数
     * @return 更新后的分群
     * @throws ScrmException 分群不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_segment", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmSegmentEntity> updateSegment(@PathVariable Long id,
                                                                @RequestBody ScrmSegmentDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmSegmentService.updateSegment(id, dto));
    }

    /**
     * 删除客户分群 (连同成员与历史快照一并清理)。
     *
     * @param id 分群 ID
     * @return 空响应
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteSegment(@PathVariable Long id) throws ScrmException {
        scrmSegmentService.deleteSegment(id);
        return OperationResponse.build();
    }

    /**
     * 查询分群详情。
     *
     * @param id 分群 ID
     * @return 分群详情
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmSegmentEntity> getSegment(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSegmentService.getSegment(id));
    }

    /**
     * 按编码查询分群详情。
     *
     * @param code 分群编码
     * @return 分群详情
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/code/{code}")
    public OperationResponse<ScrmSegmentEntity> getSegmentByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmSegmentService.getSegmentByCode(code));
    }

    /**
     * 分页查询分群列表。
     *
     * @param segmentType 分群类型过滤（可空）: DYNAMIC / STATIC / HYBRID
     * @param category    分类过滤（可空）: RFM / LIFECYCLE / VALUE / BEHAVIOR / CUSTOM
     * @param status      状态过滤（可空）: ACTIVE / INACTIVE / DRAFT
     * @param keyword     分群名称关键字模糊匹配（可空）
     * @param page        页码（从 0 开始, 默认 0）
     * @param size        每页大小（默认 20）
     * @return 分群分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmSegmentEntity>> listSegments(
            @RequestParam(required = false) String segmentType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmSegmentService.listSegments(
                segmentType, category, status, keyword, pageable));
    }

    /**
     * 激活分群。
     *
     * @param id 分群 ID
     * @return 更新后的分群
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "update")
    @PostMapping("/{id}/activate")
    public OperationResponse<ScrmSegmentEntity> activateSegment(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSegmentService.activateSegment(id));
    }

    /**
     * 停用分群。
     *
     * @param id 分群 ID
     * @return 更新后的分群
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "update")
    @PostMapping("/{id}/deactivate")
    public OperationResponse<ScrmSegmentEntity> deactivateSegment(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSegmentService.deactivateSegment(id));
    }

    /**
     * 复制分群 (创建副本)。
     *
     * @param id 源分群 ID
     * @return 复制创建的新分群
     * @throws ScrmException 源分群不存在 / 编码冲突
     */
    @RequirePermission(resource = "scrm_segment", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/{id}/copy")
    public OperationResponse<ScrmSegmentEntity> copySegment(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSegmentService.copySegment(id));
    }

    /**
     * 将分群转为静态分群。
     *
     * @param id 分群 ID
     * @return 更新后的分群
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "update")
    @PostMapping("/{id}/convert-static")
    public OperationResponse<ScrmSegmentEntity> convertToStatic(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmSegmentService.convertToStatic(id));
    }

    // ============================================================
    // 分群计算
    // ============================================================

    /**
     * 计算分群成员 (动态分群核心计算)。
     *
     * @param id 分群 ID
     * @return 计算结果 (匹配数 / 新增数 / 流失数 / 样本成员)
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "update")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/{id}/calculate")
    public OperationResponse<ScrmSegmentCalculationResultDto> calculateSegment(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmSegmentService.calculateSegment(id));
    }

    /**
     * 批量计算所有活跃分群。
     *
     * @return 批量计算结果: {total, calculated, failed}
     */
    @RequirePermission(resource = "scrm_segment", action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/batch-calculate")
    public OperationResponse<Map<String, Integer>> batchCalculateSegments() {
        return OperationResponse.build(scrmSegmentService.batchCalculateSegments());
    }

    /**
     * 预览分群 (不保存成员, 仅返回匹配数量与样本)。
     *
     * @param conditions    条件 JSON 字符串: [{field, operator, value, logic}]
     * @param conditionType 条件组合（可空, 默认 ALL）: ALL / ANY / NONE
     * @return 预览结果 (匹配数 / 样本成员)
     * @throws ScrmException 条件 JSON 非法
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/preview")
    public OperationResponse<ScrmSegmentCalculationResultDto> previewSegment(
            @RequestParam String conditions,
            @RequestParam(required = false, defaultValue = "ALL") String conditionType)
            throws ScrmException {
        return OperationResponse.build(scrmSegmentService.previewSegment(conditions, conditionType));
    }

    /**
     * 获取分群计算结果 (实时计算当前匹配数, 不修改成员)。
     *
     * @param id 分群 ID
     * @return 计算结果
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/{id}/result")
    public OperationResponse<ScrmSegmentCalculationResultDto> getCalculationResult(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmSegmentService.getCalculationResult(id));
    }

    // ============================================================
    // 成员管理
    // ============================================================

    /**
     * 分页查询分群当前成员。
     *
     * @param id   分群 ID
     * @param page 页码（从 0 开始, 默认 0）
     * @param size 每页大小（默认 20）
     * @return 成员分页结果 (按加入时间倒序)
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/{id}/members")
    public OperationResponse<Page<ScrmSegmentMemberEntity>> getMembers(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "joinedAt"));
        return OperationResponse.build(scrmSegmentService.getMembers(id, pageable));
    }

    /**
     * 手动添加分群成员。
     *
     * @param id         分群 ID
     * @param customerId 客户 ID
     * @param source     来源（可空, 默认 MANUAL）: AUTO / MANUAL / IMPORT
     * @return 成员实体
     * @throws ScrmException 分群 / 客户不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/{id}/members")
    public OperationResponse<ScrmSegmentMemberEntity> addMember(
            @PathVariable Long id,
            @RequestParam Long customerId,
            @RequestParam(required = false) String source) throws ScrmException {
        return OperationResponse.build(scrmSegmentService.addMember(id, customerId, source));
    }

    /**
     * 批量添加分群成员。
     *
     * @param id          分群 ID
     * @param customerIds 客户 ID 列表
     * @return 添加结果: {total, added, skipped}
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/{id}/members/batch")
    public OperationResponse<Map<String, Integer>> batchAddMembers(
            @PathVariable Long id,
            @RequestBody List<Long> customerIds) throws ScrmException {
        return OperationResponse.build(scrmSegmentService.batchAddMembers(id, customerIds));
    }

    /**
     * 移除分群成员 (标记为已离开, 不删除记录)。
     *
     * @param id         分群 ID
     * @param customerId 客户 ID
     * @return 空响应
     * @throws ScrmException 分群 / 成员不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "update")
    @PostMapping("/{id}/members/remove")
    public OperationResponse<Void> removeMember(@PathVariable Long id,
                                                  @RequestParam Long customerId) throws ScrmException {
        scrmSegmentService.removeMember(id, customerId);
        return OperationResponse.build();
    }

    /**
     * 检查客户是否为分群当前成员。
     *
     * @param id         分群 ID
     * @param customerId 客户 ID
     * @return 成员关系 (不存在返回 null)
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @PostMapping("/{id}/members/check")
    public OperationResponse<ScrmSegmentMemberEntity> checkMembership(@PathVariable Long id,
                                                                       @RequestParam Long customerId)
            throws ScrmException {
        return OperationResponse.build(scrmSegmentService.checkMembership(id, customerId));
    }

    /**
     * 获取客户所在的所有当前分群。
     *
     * @param id         分群 ID (路径占位, 实际按客户维度查询)
     * @param customerId 客户 ID
     * @return 成员关系列表
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/{id}/members/customer/{customerId}")
    public OperationResponse<List<ScrmSegmentMemberEntity>> getCustomerSegments(
            @PathVariable Long id,
            @PathVariable Long customerId) {
        return OperationResponse.build(scrmSegmentService.getCustomerSegments(customerId));
    }

    // ============================================================
    // 历史与趋势
    // ============================================================

    /**
     * 记录分群当日快照。
     *
     * @param segmentId 分群 ID
     * @return 历史快照实体
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "update")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/history/{segmentId}")
    public OperationResponse<ScrmSegmentHistoryEntity> recordHistory(@PathVariable Long segmentId)
            throws ScrmException {
        return OperationResponse.build(scrmSegmentService.recordHistory(segmentId));
    }

    /**
     * 分页查询分群历史快照。
     *
     * @param segmentId 分群 ID
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 历史快照分页结果 (按快照日期倒序)
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/history/{segmentId}")
    public OperationResponse<Page<ScrmSegmentHistoryEntity>> getHistory(
            @PathVariable Long segmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "snapshotDate"));
        return OperationResponse.build(scrmSegmentService.getHistory(segmentId, pageable));
    }

    /**
     * 分群趋势 (近 N 天成员数变化)。
     *
     * @param segmentId 分群 ID
     * @param days      天数（可空, 默认 30）
     * @return 趋势列表 [{snapshotDate, memberCount, addedCount, removedCount}]
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/history/{segmentId}/trend")
    public OperationResponse<List<Map<String, Object>>> getSegmentTrend(
            @PathVariable Long segmentId,
            @RequestParam(required = false, defaultValue = "30") Integer days)
            throws ScrmException {
        return OperationResponse.build(scrmSegmentService.getSegmentTrend(segmentId, days));
    }

    /**
     * 对比两个分群 (交集 / 差集 / 各维度对比)。
     *
     * @param compareDto 对比参数 (segmentId1, segmentId2)
     * @return 对比结果
     * @throws ScrmException 分群不存在
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @PostMapping("/history/compare")
    public OperationResponse<Map<String, Object>> compareSegments(@Valid @RequestBody ScrmSegmentCompareDto compareDto)
            throws ScrmException {
        return OperationResponse.build(scrmSegmentService.compareSegments(compareDto));
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 分群统计概览: 总数、各类型数、各状态数、总覆盖客户数。
     *
     * @return 统计结果 Map
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getSegmentStats() {
        return OperationResponse.build(scrmSegmentService.getSegmentStats());
    }

    /**
     * 分类分布 (按 category 聚合分群数与成员总数)。
     *
     * @return 分类分布列表
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/stats/categories")
    public OperationResponse<List<Map<String, Object>>> getCategoryDistribution() {
        return OperationResponse.build(scrmSegmentService.getCategoryDistribution());
    }

    /**
     * 最大分群 (按成员数降序取前 N)。
     *
     * @param limit 返回数量（可空, 默认 10）
     * @return 分群列表
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/stats/top")
    public OperationResponse<List<Map<String, Object>>> getTopSegments(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return OperationResponse.build(scrmSegmentService.getTopSegments(limit));
    }

    /**
     * 分群重叠分析 (客户同时属于多个分群)。
     *
     * @return 重叠分析结果
     */
    @RequirePermission(resource = "scrm_segment", action = "read")
    @GetMapping("/stats/overlap")
    public OperationResponse<List<Map<String, Object>>> getSegmentOverlap() {
        return OperationResponse.build(scrmSegmentService.getSegmentOverlap());
    }
}
