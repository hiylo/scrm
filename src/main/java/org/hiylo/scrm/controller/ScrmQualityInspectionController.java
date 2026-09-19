/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQualityInspectionController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmInspectionExecuteDto;
import org.hiylo.scrm.dto.ScrmQualityInspectionResultDto;
import org.hiylo.scrm.dto.ScrmQualityInspectionRuleDto;
import org.hiylo.scrm.dto.ScrmQualityInspectionTaskDto;
import org.hiylo.scrm.entity.ScrmQualityInspectionRuleEntity;
import org.hiylo.scrm.entity.ScrmQualityInspectionTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmQualityInspectionService;
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
 * SCRM 智能质检/会话质检控制器。
 * <p>
 * 提供质检规则管理、质检任务批量执行、单会话即时质检、质检结果查询与统计排名接口。
 * AI 自动会话质检支持话术规范/服务态度/敏感词/响应时长/专业度多维度评估。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明,
 * {@code @RateLimit} 对写操作与执行类接口进行限流保护。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/quality-inspections")
@RequiredArgsConstructor
public class ScrmQualityInspectionController {

    /** 智能质检服务 */
    private final ScrmQualityInspectionService qualityInspectionService;

    // ============================================================
    // 质检规则管理
    // ============================================================

    /**
     * 创建质检规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules")
    public OperationResponse<ScrmQualityInspectionRuleEntity> createRule(
            @Valid @RequestBody ScrmQualityInspectionRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(qualityInspectionService.createRule(dto));
    }

    /**
     * 更新质检规则。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/rules/{id}")
    public OperationResponse<ScrmQualityInspectionRuleEntity> updateRule(@PathVariable Long id,
                                                                          @RequestBody ScrmQualityInspectionRuleDto dto)
            throws ScrmException {
        return OperationResponse.build(qualityInspectionService.updateRule(id, dto));
    }

    /**
     * 删除质检规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "delete")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @DeleteMapping("/rules/{id}")
    public OperationResponse<Void> deleteRule(@PathVariable Long id) throws ScrmException {
        qualityInspectionService.deleteRule(id);
        return OperationResponse.build();
    }

    /**
     * 查询质检规则详情。
     *
     * @param id 规则 ID
     * @return 规则详情
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "read")
    @GetMapping("/rules/{id}")
    public OperationResponse<ScrmQualityInspectionRuleEntity> getRule(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(qualityInspectionService.getRule(id));
    }

    /**
     * 分页查询质检规则列表。
     *
     * @param category 质检类别过滤（可空）
     * @param ruleType 规则类型过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  关键字过滤（按规则名称模糊匹配, 可空）
     * @param page     页码（从 0 开始, 默认 0）
     * @param size     每页大小（默认 20）
     * @return 规则分页结果
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "read")
    @GetMapping("/rules/list")
    public OperationResponse<Page<ScrmQualityInspectionRuleEntity>> listRules(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(
                qualityInspectionService.listRules(category, ruleType, enabled, keyword, pageable));
    }

    /**
     * 启用质检规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules/{id}/enable")
    public OperationResponse<Void> enableRule(@PathVariable Long id) throws ScrmException {
        qualityInspectionService.enableRule(id);
        return OperationResponse.build();
    }

    /**
     * 禁用质检规则。
     *
     * @param id 规则 ID
     * @return 空响应
     * @throws ScrmException 规则不存在
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/rules/{id}/disable")
    public OperationResponse<Void> disableRule(@PathVariable Long id) throws ScrmException {
        qualityInspectionService.disableRule(id);
        return OperationResponse.build();
    }

    // ============================================================
    // 质检任务管理
    // ============================================================

    /**
     * 创建质检任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/tasks")
    public OperationResponse<ScrmQualityInspectionTaskEntity> createTask(
            @Valid @RequestBody ScrmQualityInspectionTaskDto dto)
            throws ScrmException {
        return OperationResponse.build(qualityInspectionService.createTask(dto));
    }

    /**
     * 查询质检任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "read")
    @GetMapping("/tasks/{id}")
    public OperationResponse<ScrmQualityInspectionTaskEntity> getTask(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(qualityInspectionService.getTask(id));
    }

    /**
     * 分页查询质检任务列表。
     *
     * @param status 任务状态过滤（可空）
     * @param page   页码（从 0 开始, 默认 0）
     * @param size   每页大小（默认 20）
     * @return 任务分页结果
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "read")
    @GetMapping("/tasks/list")
    public OperationResponse<Page<ScrmQualityInspectionTaskEntity>> listTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(qualityInspectionService.listTasks(status, pageable));
    }

    /**
     * 执行质检任务。
     *
     * @param id 任务 ID
     * @return 执行后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "execute")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/tasks/{id}/execute")
    public OperationResponse<ScrmQualityInspectionTaskEntity> executeTask(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(qualityInspectionService.executeTask(id));
    }

    /**
     * 查询质检任务进度。
     *
     * @param id 任务 ID
     * @return 进度信息
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "read")
    @GetMapping("/tasks/{id}/progress")
    public OperationResponse<Map<String, Object>> getTaskProgress(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(qualityInspectionService.getTaskProgress(id));
    }

    // ============================================================
    // 会话质检
    // ============================================================

    /**
     * 质检单个会话。
     *
     * @param dto 质检执行参数 (conversationId + ruleIds)
     * @return 质检结果
     * @throws ScrmException 会话不存在 / 规则不存在
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/inspect/conversation")
    public OperationResponse<ScrmQualityInspectionResultDto> inspectConversation(
            @Valid @RequestBody ScrmInspectionExecuteDto dto) throws ScrmException {
        return OperationResponse.build(qualityInspectionService.inspectConversation(dto));
    }

    /**
     * 质检某销售的所有会话。
     *
     * @param assigneeId 被质检人 ID (账号 ID)
     * @param startTime  起始时间 (yyyy-MM-dd HH:mm:ss)
     * @param endTime    截止时间 (yyyy-MM-dd HH:mm:ss)
     * @param ruleIds    规则 ID 列表 JSON 数组
     * @return 质检结果列表
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "execute")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/inspect/assignee")
    public OperationResponse<List<ScrmQualityInspectionResultDto>> inspectByAssignee(
            @RequestParam String assigneeId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam String ruleIds) throws ScrmException {
        return OperationResponse.build(
                qualityInspectionService.inspectByAssignee(assigneeId, startTime, endTime, ruleIds));
    }

    // ============================================================
    // 质检结果查询
    // ============================================================

    /**
     * 分页查询质检结果列表 (多条件过滤)。
     *
     * @param taskId         任务 ID 过滤（可空）
     * @param conversationId 会话 ID 过滤（可空）
     * @param assigneeId     被质检人 ID 过滤（可空）
     * @param passed         是否通过过滤（可空）
     * @param minValueScore  总分下限（可空）
     * @param maxValueScore  总分上限（可空）
     * @param startTime       质检时间下限（可空）
     * @param endTime         质检时间上限（可空）
     * @param page           页码（从 0 开始, 默认 0）
     * @param size           每页大小（默认 20）
     * @return 质检结果分页
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "read")
    @GetMapping("/results/list")
    public OperationResponse<Page<ScrmQualityInspectionResultDto>> listResults(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) Long conversationId,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) Boolean passed,
            @RequestParam(required = false) Double minValueScore,
            @RequestParam(required = false) Double maxValueScore,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "inspectedAt"));
        return OperationResponse.build(qualityInspectionService.listResults(
                taskId, conversationId, assigneeId, passed, minValueScore, maxValueScore,
                startTime, endTime, pageable));
    }

    /**
     * 查询质检结果详情。
     *
     * @param id 结果 ID
     * @return 质检结果
     * @throws ScrmException 结果不存在
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "read")
    @GetMapping("/results/{id}")
    public OperationResponse<ScrmQualityInspectionResultDto> getResult(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(qualityInspectionService.getResult(id));
    }

    /**
     * 查询某销售时间范围内的质检结果。
     *
     * @param assigneeId 被质检人 ID
     * @param startTime  起始时间 (yyyy-MM-dd HH:mm:ss)
     * @param endTime    截止时间 (yyyy-MM-dd HH:mm:ss)
     * @return 质检结果列表
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "read")
    @GetMapping("/results/assignee/{assigneeId}")
    public OperationResponse<List<ScrmQualityInspectionResultDto>> getResultsByAssignee(
            @PathVariable String assigneeId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return OperationResponse.build(
                qualityInspectionService.getResultsByAssignee(assigneeId, startTime, endTime));
    }

    // ============================================================
    // 统计与排名
    // ============================================================

    /**
     * 质检统计概览: 总质检数 / 通过率 / 平均分 / 各类别得分。
     *
     * @param startTime 起始时间 (yyyy-MM-dd HH:mm:ss)
     * @param endTime   截止时间 (yyyy-MM-dd HH:mm:ss)
     * @return 统计信息
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getInspectionStats(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return OperationResponse.build(qualityInspectionService.getInspectionStats(startTime, endTime));
    }

    /**
     * 销售质检排名 (按平均分降序)。
     *
     * @param startTime 起始时间 (yyyy-MM-dd HH:mm:ss)
     * @param endTime   截止时间 (yyyy-MM-dd HH:mm:ss)
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 排名分页结果
     */
    @RequirePermission(resource = "scrm_quality_inspection", action = "read")
    @GetMapping("/stats/ranking")
    public OperationResponse<Page<Map<String, Object>>> getAssigneeRanking(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(
                qualityInspectionService.getAssigneeRanking(startTime, endTime, pageable));
    }
}
