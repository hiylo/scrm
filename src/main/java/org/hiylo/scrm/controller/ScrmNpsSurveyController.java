/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNpsSurveyController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmNpsBenchmarkDto;
import org.hiylo.scrm.dto.ScrmSurveyDistributeDto;
import org.hiylo.scrm.dto.ScrmSurveyDto;
import org.hiylo.scrm.dto.ScrmSurveyInvitationDto;
import org.hiylo.scrm.dto.ScrmSurveySubmitDto;
import org.hiylo.scrm.entity.ScrmNpsBenchmarkEntity;
import org.hiylo.scrm.entity.ScrmSurveyEntity;
import org.hiylo.scrm.entity.ScrmSurveyInvitationEntity;
import org.hiylo.scrm.entity.ScrmSurveyResponseEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmNpsSurveyService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户满意度 NPS 调查控制器。
 * <p>
 * 提供调查问卷管理、邀请管理 (创建 / 批量分发 / 发送 / 提醒 / 过期 / 取消)、回复收集与跟进、
 * NPS 基准生成与查询, 以及多维度统计 (问卷统计 / 总体统计 / NPS 趋势 / 回复趋势 / 情感分布 /
 * 高频反馈) 接口。权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/surveys")
@RequiredArgsConstructor
public class ScrmNpsSurveyController {

    /** 权限资源标识 */
    private static final String RESOURCE = "scrm_nps_survey";

    /** NPS 调查服务 */
    private final ScrmNpsSurveyService scrmNpsSurveyService;

    // ============================================================
    // 问卷管理
    // ============================================================

    /**
     * 创建调查问卷。
     *
     * @param dto 问卷参数
     * @return 创建后的问卷
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = RESOURCE, action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmSurveyEntity> createSurvey(@Valid @RequestBody ScrmSurveyDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.createSurvey(dto));
    }

    /**
     * 更新调查问卷。
     *
     * @param id  问卷 ID
     * @param dto 问卷参数
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 已归档 / 参数非法
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmSurveyEntity> updateSurvey(@PathVariable Long id,
                                                              @RequestBody ScrmSurveyDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.updateSurvey(id, dto));
    }

    /**
     * 删除调查问卷。
     *
     * @param id 问卷 ID
     * @return 空响应
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE, action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteSurvey(@PathVariable Long id) throws ScrmException {
        scrmNpsSurveyService.deleteSurvey(id);
        return OperationResponse.build();
    }

    /**
     * 查询问卷详情。
     *
     * @param id 问卷 ID
     * @return 问卷详情
     * @throws ScrmException 问卷不存在
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmSurveyEntity> getSurvey(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.getSurvey(id));
    }

    /**
     * 分页查询问卷列表。
     *
     * @param surveyType 调查类型过滤（可空）: NPS/CSAT/CES/CUSTOM
     * @param status     状态过滤（可空）: DRAFT/ACTIVE/PAUSED/COMPLETED/ARCHIVED
     * @param keyword    问卷名称 / 标题关键字模糊匹配（可空）
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 问卷分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmSurveyEntity>> listSurveys(
            @RequestParam(required = false) String surveyType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmNpsSurveyService.listSurveys(surveyType, status, keyword, pageable));
    }

    /**
     * 激活问卷。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @PostMapping("/{id}/activate")
    public OperationResponse<ScrmSurveyEntity> activateSurvey(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.activateSurvey(id));
    }

    /**
     * 暂停问卷。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @PostMapping("/{id}/pause")
    public OperationResponse<ScrmSurveyEntity> pauseSurvey(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.pauseSurvey(id));
    }

    /**
     * 完成问卷。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @PostMapping("/{id}/complete")
    public OperationResponse<ScrmSurveyEntity> completeSurvey(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.completeSurvey(id));
    }

    /**
     * 归档问卷。
     *
     * @param id 问卷 ID
     * @return 更新后的问卷
     * @throws ScrmException 问卷不存在
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @PostMapping("/{id}/archive")
    public OperationResponse<ScrmSurveyEntity> archiveSurvey(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.archiveSurvey(id));
    }

    /**
     * 复制问卷 (创建副本, 状态重置为 DRAFT, 统计清零)。
     *
     * @param id 源问卷 ID
     * @return 复制后的新问卷
     * @throws ScrmException 源问卷不存在
     */
    @RequirePermission(resource = RESOURCE, action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/{id}/copy")
    public OperationResponse<ScrmSurveyEntity> copySurvey(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.copySurvey(id));
    }

    /**
     * 通过邀请码获取问卷 (客户凭码填写场景)。
     *
     * @param code 邀请码
     * @return 问卷详情
     * @throws ScrmException 邀请不存在 / 问卷不存在
     */
    @GetMapping("/by-code/{code}")
    public OperationResponse<ScrmSurveyEntity> getSurveyByInvitationCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.getSurveyByInvitationCode(code));
    }

    // ============================================================
    // 邀请管理 /invitations
    // ============================================================

    /**
     * 创建调查邀请 (不发送, 状态 PENDING)。
     *
     * @param dto 邀请参数
     * @return 创建后的邀请
     * @throws ScrmException 参数非法 / 问卷不存在 / 重复创建
     */
    @RequirePermission(resource = RESOURCE, action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/invitations")
    public OperationResponse<ScrmSurveyInvitationEntity> createInvitation(
            @Valid @RequestBody ScrmSurveyInvitationDto dto) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.createInvitation(dto));
    }

    /**
     * 批量创建邀请并模拟发送。
     *
     * @param distributeDto 分发参数 (surveyId + customerIds + channel)
     * @return 分发结果: {total, success, failed, invitations}
     * @throws ScrmException 问卷不存在 / 问卷未激活 / 参数非法
     */
    @RequirePermission(resource = RESOURCE, action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/invitations/batch")
    public OperationResponse<Map<String, Object>> batchCreateInvitations(
            @Valid @RequestBody ScrmSurveyDistributeDto distributeDto) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.batchCreateInvitations(distributeDto));
    }

    /**
     * 发送邀请 (模拟实现)。
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @PostMapping("/invitations/{id}/send")
    public OperationResponse<ScrmSurveyInvitationEntity> sendInvitation(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.sendInvitation(id));
    }

    /**
     * 发送提醒 (模拟实现)。
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @PostMapping("/invitations/{id}/remind")
    public OperationResponse<ScrmSurveyInvitationEntity> sendReminder(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.sendReminder(id));
    }

    /**
     * 过期邀请。
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @PostMapping("/invitations/{id}/expire")
    public OperationResponse<ScrmSurveyInvitationEntity> expireInvitation(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.expireInvitation(id));
    }

    /**
     * 取消邀请。
     *
     * @param id 邀请 ID
     * @return 更新后的邀请
     * @throws ScrmException 邀请不存在 / 状态非法
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @PostMapping("/invitations/{id}/cancel")
    public OperationResponse<ScrmSurveyInvitationEntity> cancelInvitation(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.cancelInvitation(id));
    }

    /**
     * 查询邀请详情。
     *
     * @param id 邀请 ID
     * @return 邀请详情
     * @throws ScrmException 邀请不存在
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/invitations/{id}")
    public OperationResponse<ScrmSurveyInvitationEntity> getInvitation(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.getInvitation(id));
    }

    /**
     * 按邀请码查询邀请。
     *
     * @param code 邀请码
     * @return 邀请详情
     * @throws ScrmException 邀请不存在
     */
    @GetMapping("/invitations/by-code/{code}")
    public OperationResponse<ScrmSurveyInvitationEntity> getInvitationByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.getInvitationByCode(code));
    }

    /**
     * 分页查询邀请列表。
     *
     * @param surveyId  问卷 ID 过滤（可空）
     * @param status    状态过滤（可空）: PENDING/SENT/OPENED/IN_PROGRESS/COMPLETED/EXPIRED/BOUNCED
     * @param channel   渠道过滤（可空）: IN_APP/SMS/EMAIL/WECHAT
     * @param startTime 发送时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   发送时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page      页码（从 0 开始, 默认 0）
     * @param size      每页大小（默认 20）
     * @return 邀请分页结果 (按 createTime DESC)
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/invitations/list")
    public OperationResponse<Page<ScrmSurveyInvitationEntity>> listInvitations(
            @RequestParam(required = false) Long surveyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmNpsSurveyService.listInvitations(
                surveyId, status, channel, startTime, endTime, pageable));
    }

    // ============================================================
    // 回复管理 /responses
    // ============================================================

    /**
     * 提交调查回答。
     * <p>客户凭邀请码提交回答, 系统自动计算 NPS / CSAT / CES 分数与情感倾向。</p>
     *
     * @param submitDto 提交参数 (invitationCode + responses + customerId)
     * @return 创建后的回复
     * @throws ScrmException 邀请不存在 / 问卷不存在 / 状态非法 / 回答 JSON 解析失败
     */
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/responses/submit")
    public OperationResponse<ScrmSurveyResponseEntity> submitResponse(
            @Valid @RequestBody ScrmSurveySubmitDto submitDto) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.submitResponse(submitDto));
    }

    /**
     * 查询回复详情。
     *
     * @param id 回复 ID
     * @return 回复详情
     * @throws ScrmException 回复不存在
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/responses/{id}")
    public OperationResponse<ScrmSurveyResponseEntity> getResponse(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.getResponse(id));
    }

    /**
     * 分页查询回复列表。
     *
     * @param surveyId         问卷 ID 过滤（可空）
     * @param customerId       客户 ID 过滤（可空）
     * @param npsScore         NPS 分数过滤（可空, 精确匹配 0-10）
     * @param sentiment        情感过滤（可空）: POSITIVE/NEUTRAL/NEGATIVE
     * @param followUpRequired 是否需要跟进过滤（可空）
     * @param startTime        提交时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime          提交时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page             页码（从 0 开始, 默认 0）
     * @param size             每页大小（默认 20）
     * @return 回复分页结果 (按 submittedAt DESC)
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/responses/list")
    public OperationResponse<Page<ScrmSurveyResponseEntity>> listResponses(
            @RequestParam(required = false) Long surveyId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer npsScore,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) Boolean followUpRequired,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "submittedAt"));
        return OperationResponse.build(scrmNpsSurveyService.listResponses(
                surveyId, customerId, npsScore, sentiment, followUpRequired, startTime, endTime, pageable));
    }

    /**
     * 更新回复跟进状态。
     *
     * @param id         回复 ID
     * @param status     跟进状态: PENDING / IN_PROGRESS / COMPLETED
     * @param assigneeId 跟进人 ID (可空)
     * @return 更新后的回复
     * @throws ScrmException 回复不存在 / 跟进状态非法
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @PostMapping("/responses/{id}/follow-up")
    public OperationResponse<ScrmSurveyResponseEntity> updateFollowUpStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String assigneeId) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.updateFollowUpStatus(id, status, assigneeId));
    }

    /**
     * 添加标签 (追加, 不覆盖已有标签)。
     *
     * @param id   回复 ID
     * @param tags 待添加标签 (逗号分隔)
     * @return 更新后的回复
     * @throws ScrmException 回复不存在
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @PostMapping("/responses/{id}/tags")
    public OperationResponse<ScrmSurveyResponseEntity> addTags(
            @PathVariable Long id,
            @RequestParam String tags) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.addTags(id, tags));
    }

    // ============================================================
    // NPS 基准 /benchmarks
    // ============================================================

    /**
     * 生成 NPS 基准 (按周期统计推荐者 / 被动者 / 贬损者 → 计算 NPS)。
     *
     * @param dto 基准参数 (periodType + periodStart + periodEnd)
     * @return 生成 / 更新后的基准
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = RESOURCE, action = "update")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60)
    @PostMapping("/benchmarks/generate")
    public OperationResponse<ScrmNpsBenchmarkEntity> generateBenchmark(
            @Valid @RequestBody ScrmNpsBenchmarkDto dto) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.generateBenchmark(
                dto.getPeriodType(), dto.getPeriodStart(), dto.getPeriodEnd()));
    }

    /**
     * 查询基准详情。
     *
     * @param id 基准 ID
     * @return 基准详情
     * @throws ScrmException 基准不存在
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/benchmarks/{id}")
    public OperationResponse<ScrmNpsBenchmarkEntity> getBenchmark(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.getBenchmark(id));
    }

    /**
     * 分页查询基准列表。
     *
     * @param periodType 周期类型过滤（可空）: MONTHLY/QUARTERLY/YEARLY
     * @param startDate  周期开始日期起始 (可空, ISO 格式: yyyy-MM-dd)
     * @param endDate    周期结束日期截止 (可空, ISO 格式: yyyy-MM-dd)
     * @param page       页码（从 0 开始, 默认 0）
     * @param size       每页大小（默认 20）
     * @return 基准分页结果 (按 generatedAt DESC)
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/benchmarks/list")
    public OperationResponse<Page<ScrmNpsBenchmarkEntity>> listBenchmarks(
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "generatedAt"));
        return OperationResponse.build(scrmNpsSurveyService.listBenchmarks(periodType, startDate, endDate, pageable));
    }

    /**
     * 查询最新基准 (按周期类型, 取生成时间最近的一条)。
     *
     * @param periodType 周期类型: MONTHLY / QUARTERLY / YEARLY
     * @return 最新基准, 不存在返回 null
     * @throws ScrmException 周期类型非法
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/benchmarks/latest/{periodType}")
    public OperationResponse<ScrmNpsBenchmarkEntity> getLatestBenchmark(@PathVariable String periodType)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.getLatestBenchmark(periodType));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 问卷统计: 回复数、完成率、NPS、CSAT、情感分布。
     *
     * @param surveyId 问卷 ID
     * @return 统计结果 Map
     * @throws ScrmException 问卷不存在
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/stats/survey/{surveyId}")
    public OperationResponse<Map<String, Object>> getSurveyStats(@PathVariable Long surveyId)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.getSurveyStats(surveyId));
    }

    /**
     * 总体统计: 各调查类型回复数、平均 NPS、趋势概览。
     *
     * @param startTime 提交时间起始 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   提交时间截止 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果 Map
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getOverallStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return OperationResponse.build(scrmNpsSurveyService.getOverallStats(startTime, endTime));
    }

    /**
     * NPS 趋势 (按月聚合)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势列表: [{period, total, promoters, passives, detractors, nps}]
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/stats/nps-trend")
    public OperationResponse<List<Map<String, Object>>> getNpsTrend(
            @RequestParam(defaultValue = "6") int months) {
        return OperationResponse.build(scrmNpsSurveyService.getNpsTrend(months));
    }

    /**
     * 回复趋势 (按日聚合)。
     *
     * @param surveyId 问卷 ID (可空, 为空统计全部)
     * @param days     天数 (默认 30)
     * @return 趋势列表: [{date, count}]
     * @throws ScrmException 问卷不存在 (surveyId 非空时)
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/stats/response-trend/{surveyId}")
    public OperationResponse<List<Map<String, Object>>> getResponseTrend(
            @PathVariable Long surveyId,
            @RequestParam(defaultValue = "30") int days) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.getResponseTrend(surveyId, days));
    }

    /**
     * 情感分布。
     *
     * @param surveyId 问卷 ID (可空, 为空统计全部)
     * @return 情感分布 Map: {POSITIVE, NEUTRAL, NEGATIVE}
     * @throws ScrmException 问卷不存在 (surveyId 非空时)
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/stats/sentiment/{surveyId}")
    public OperationResponse<Map<String, Long>> getSentimentDistribution(@PathVariable Long surveyId)
            throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.getSentimentDistribution(surveyId));
    }

    /**
     * 获取高频反馈。
     *
     * @param surveyId 问卷 ID (可空, 为空统计全部)
     * @param limit    返回条数 (默认 10)
     * @return 高频反馈列表: [{feedback, count}]
     * @throws ScrmException 问卷不存在 (surveyId 非空时)
     */
    @RequirePermission(resource = RESOURCE, action = "read")
    @GetMapping("/stats/feedback/{surveyId}")
    public OperationResponse<List<Map<String, Object>>> getTopFeedback(
            @PathVariable Long surveyId,
            @RequestParam(defaultValue = "10") int limit) throws ScrmException {
        return OperationResponse.build(scrmNpsSurveyService.getTopFeedback(surveyId, limit));
    }
}
