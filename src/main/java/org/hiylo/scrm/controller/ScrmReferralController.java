/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmReferralCreateDto;
import org.hiylo.scrm.dto.ScrmReferralDto;
import org.hiylo.scrm.dto.ScrmReferralProgramDto;
import org.hiylo.scrm.dto.ScrmReferralQualifyDto;
import org.hiylo.scrm.dto.ScrmReferralRewardDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmReferralService;
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
 * SCRM 客户推荐管理控制器。
 * <p>
 * 提供推荐活动管理 (增删改查/启停/完成/统计刷新)、推荐关系管理 (创建/注册/达标/取消/过期/
 * 推荐码与链接生成)、奖励管理 (发放/兑换/过期/批量发放/客户奖励列表) 以及推荐统计
 * (总览/活动统计/推荐人排行/趋势/转化漏斗/奖励统计) 接口。权限由 gateway-server
 * 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/referrals")
@RequiredArgsConstructor
public class ScrmReferralController {

    /** 推荐服务 */
    private final ScrmReferralService scrmReferralService;

    // ============================================================
    // 推荐活动 Program
    // ============================================================

    /**
     * 创建推荐活动。
     *
     * @param dto 活动参数
     * @return 创建后的活动
     * @throws ScrmException 参数非法 / 活动编码重复
     */
    @RequirePermission(resource = "scrm_referral", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建推荐活动过于频繁，请稍后重试")
    @PostMapping("/programs")
    public OperationResponse<ScrmReferralProgramDto> createProgram(@Valid @RequestBody ScrmReferralProgramDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmReferralService.createProgram(dto));
    }

    /**
     * 更新推荐活动 (字段非空才覆盖)。
     *
     * @param id  活动 ID
     * @param dto 活动参数
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 参数非法 / 活动编码重复
     */
    @RequirePermission(resource = "scrm_referral", action = "update")
    @PutMapping("/programs/{id}")
    public OperationResponse<ScrmReferralProgramDto> updateProgram(@PathVariable Long id,
                                                                    @RequestBody ScrmReferralProgramDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmReferralService.updateProgram(id, dto));
    }

    /**
     * 删除推荐活动。
     *
     * @param id 活动 ID
     * @return 空响应
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "delete")
    @DeleteMapping("/programs/{id}")
    public OperationResponse<Void> deleteProgram(@PathVariable Long id) throws ScrmException {
        scrmReferralService.deleteProgram(id);
        return OperationResponse.build();
    }

    /**
     * 查询推荐活动详情。
     *
     * @param id 活动 ID
     * @return 活动详情
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/programs/{id}")
    public OperationResponse<ScrmReferralProgramDto> getProgram(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmReferralService.getProgram(id));
    }

    /**
     * 按活动编码查询推荐活动。
     *
     * @param code 活动编码
     * @return 活动详情
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/programs/code/{code}")
    public OperationResponse<ScrmReferralProgramDto> getProgramByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmReferralService.getProgramByCode(code));
    }

    /**
     * 分页查询推荐活动, 支持按活动类型、状态与关键词过滤。
     *
     * @param programType 活动类型过滤 (可空)
     * @param status      状态过滤 (可空)
     * @param keyword     关键词过滤 (可空)
     * @param page        页码 (从 0 开始, 默认 0)
     * @param size        每页大小 (默认 20)
     * @return 活动分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/programs/list")
    public OperationResponse<Page<ScrmReferralProgramDto>> listPrograms(
            @RequestParam(required = false) String programType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmReferralService.listPrograms(programType, status, keyword, pageable));
    }

    /**
     * 激活推荐活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "execute")
    @PostMapping("/programs/{id}/activate")
    public OperationResponse<ScrmReferralProgramDto> activateProgram(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmReferralService.activateProgram(id));
    }

    /**
     * 暂停推荐活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "execute")
    @PostMapping("/programs/{id}/pause")
    public OperationResponse<ScrmReferralProgramDto> pauseProgram(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmReferralService.pauseProgram(id));
    }

    /**
     * 完成推荐活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "execute")
    @PostMapping("/programs/{id}/complete")
    public OperationResponse<ScrmReferralProgramDto> completeProgram(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmReferralService.completeProgram(id));
    }

    // ============================================================
    // 推荐关系 Referral
    // ============================================================

    /**
     * 创建推荐 (生成推荐码→创建推荐记录)。
     *
     * @param createDto 创建参数
     * @return 创建后的推荐
     * @throws ScrmException 活动不存在 / 活动非活跃 / 超出推荐上限
     */
    @RequirePermission(resource = "scrm_referral", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60, message = "创建推荐过于频繁，请稍后重试")
    @PostMapping("/create")
    public OperationResponse<ScrmReferralDto> createReferral(@Valid @RequestBody ScrmReferralCreateDto createDto)
            throws ScrmException {
        return OperationResponse.build(scrmReferralService.createReferral(createDto));
    }

    /**
     * 查询推荐详情。
     *
     * @param id 推荐 ID
     * @return 推荐详情
     * @throws ScrmException 推荐不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmReferralDto> getReferral(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmReferralService.getReferral(id));
    }

    /**
     * 按推荐码查询推荐。
     *
     * @param code 推荐码
     * @return 推荐详情
     * @throws ScrmException 推荐不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/code/{code}")
    public OperationResponse<ScrmReferralDto> getReferralByCode(@PathVariable String code) throws ScrmException {
        return OperationResponse.build(scrmReferralService.getReferralByCode(code));
    }

    /**
     * 分页查询推荐, 支持按活动、推荐人、状态与时间范围过滤。
     *
     * @param programId          活动过滤 (可空)
     * @param referrerCustomerId 推荐人过滤 (可空)
     * @param status             状态过滤 (可空)
     * @param startTime          起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime            截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param page               页码 (从 0 开始, 默认 0)
     * @param size               每页大小 (默认 20)
     * @return 推荐分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmReferralDto>> listReferrals(
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long referrerCustomerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmReferralService.listReferrals(programId, referrerCustomerId,
                status, startTime, endTime, pageable));
    }

    /**
     * 查询推荐人的推荐列表。
     *
     * @param customerId 推荐人客户 ID
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 推荐分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/by-referrer/{customerId}")
    public OperationResponse<Page<ScrmReferralDto>> getReferralsByReferrer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmReferralService.getReferralsByReferrer(customerId, pageable));
    }

    /**
     * 被推荐人注册 (按推荐码)。
     *
     * @param referralCode      推荐码
     * @param refereeCustomerId 被推荐人客户 ID
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_referral", action = "execute")
    @PostMapping("/signup")
    public OperationResponse<ScrmReferralDto> signUpReferral(
            @RequestParam String referralCode,
            @RequestParam Long refereeCustomerId) throws ScrmException {
        return OperationResponse.build(scrmReferralService.signUpReferral(referralCode, refereeCustomerId));
    }

    /**
     * 推荐达标 (检查触发条件→更新状态)。
     *
     * @param qualifyDto 达标参数
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法 / 未达触发条件
     */
    @RequirePermission(resource = "scrm_referral", action = "execute")
    @PostMapping("/qualify")
    public OperationResponse<ScrmReferralDto> qualifyReferral(@Valid @RequestBody ScrmReferralQualifyDto qualifyDto)
            throws ScrmException {
        return OperationResponse.build(scrmReferralService.qualifyReferral(qualifyDto));
    }

    /**
     * 取消推荐。
     *
     * @param id     推荐 ID
     * @param reason 取消原因 (可空)
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_referral", action = "execute")
    @PostMapping("/{id}/cancel")
    public OperationResponse<ScrmReferralDto> cancelReferral(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) throws ScrmException {
        return OperationResponse.build(scrmReferralService.cancelReferral(id, reason));
    }

    /**
     * 过期推荐。
     *
     * @param id 推荐 ID
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_referral", action = "execute")
    @PostMapping("/{id}/expire")
    public OperationResponse<ScrmReferralDto> expireReferral(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmReferralService.expireReferral(id));
    }

    /**
     * 生成推荐码。
     *
     * @param programId  活动 ID
     * @param customerId 推荐人客户 ID
     * @return 推荐码
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @PostMapping("/generate-code")
    public OperationResponse<String> generateReferralCode(
            @RequestParam Long programId,
            @RequestParam Long customerId) throws ScrmException {
        return OperationResponse.build(scrmReferralService.generateReferralCode(programId, customerId));
    }

    /**
     * 生成推荐链接。
     *
     * @param programId  活动 ID
     * @param customerId 推荐人客户 ID
     * @param channel    推荐渠道 (可空, 缺省 LINK)
     * @return 推荐链接
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @PostMapping("/generate-link")
    public OperationResponse<String> generateReferralLink(
            @RequestParam Long programId,
            @RequestParam Long customerId,
            @RequestParam(required = false) String channel) throws ScrmException {
        return OperationResponse.build(scrmReferralService.generateReferralLink(programId, customerId, channel));
    }

    // ============================================================
    // 奖励 Reward
    // ============================================================

    /**
     * 发放奖励 (模拟实现: 创建奖励记录→更新状态)。
     *
     * @param referralId    推荐记录 ID
     * @param recipientType 接收者类型: REFERRER / REFEREE
     * @return 创建后的奖励
     * @throws ScrmException 推荐不存在 / 接收者类型非法 / 奖励已发放
     */
    @RequirePermission(resource = "scrm_referral", action = "execute")
    @PostMapping("/rewards/issue")
    public OperationResponse<ScrmReferralRewardDto> issueReward(
            @RequestParam Long referralId,
            @RequestParam String recipientType) throws ScrmException {
        return OperationResponse.build(scrmReferralService.issueReward(referralId, recipientType));
    }

    /**
     * 兑换奖励。
     *
     * @param id 奖励 ID
     * @return 更新后的奖励
     * @throws ScrmException 奖励不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_referral", action = "execute")
    @PostMapping("/rewards/{id}/redeem")
    public OperationResponse<ScrmReferralRewardDto> redeemReward(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmReferralService.redeemReward(id));
    }

    /**
     * 过期奖励。
     *
     * @param id 奖励 ID
     * @return 更新后的奖励
     * @throws ScrmException 奖励不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_referral", action = "execute")
    @PostMapping("/rewards/{id}/expire")
    public OperationResponse<ScrmReferralRewardDto> expireReward(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmReferralService.expireReward(id));
    }

    /**
     * 查询奖励详情。
     *
     * @param id 奖励 ID
     * @return 奖励详情
     * @throws ScrmException 奖励不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/rewards/{id}")
    public OperationResponse<ScrmReferralRewardDto> getReward(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmReferralService.getReward(id));
    }

    /**
     * 分页查询奖励, 支持按推荐记录、接收者类型与状态过滤。
     *
     * @param referralId    推荐记录过滤 (可空)
     * @param recipientType 接收者类型过滤 (可空)
     * @param status        状态过滤 (可空)
     * @param page          页码 (从 0 开始, 默认 0)
     * @param size          每页大小 (默认 20)
     * @return 奖励分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/rewards/list")
    public OperationResponse<Page<ScrmReferralRewardDto>> listRewards(
            @RequestParam(required = false) Long referralId,
            @RequestParam(required = false) String recipientType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmReferralService.listRewards(referralId, recipientType, status, pageable));
    }

    /**
     * 查询客户奖励列表。
     *
     * @param customerId 客户 ID
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 奖励分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/rewards/customer/{customerId}")
    public OperationResponse<Page<ScrmReferralRewardDto>> getCustomerRewards(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return OperationResponse.build(scrmReferralService.getCustomerRewards(customerId, pageable));
    }

    /**
     * 批量发放奖励。
     *
     * @param referralIds 推荐记录 ID 列表
     * @return 成功发放的奖励列表
     */
    @RequirePermission(resource = "scrm_referral", action = "execute")
    @PostMapping("/rewards/batch-issue")
    public OperationResponse<List<ScrmReferralRewardDto>> batchIssueRewards(
            @RequestBody List<Long> referralIds) {
        return OperationResponse.build(scrmReferralService.batchIssueRewards(referralIds));
    }

    // ============================================================
    // 统计 Stats
    // ============================================================

    /**
     * 推荐统计: 总推荐数 / 成功率 / 转化率 / 总奖励。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/stats/overview")
    public OperationResponse<Map<String, Object>> getReferralStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmReferralService.getReferralStats(startTime, endTime));
    }

    /**
     * 活动统计: 总推荐数 / 成功推荐数 / 累计奖励价值 / 状态分布。
     *
     * @param programId 活动 ID
     * @return 统计结果
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/stats/program/{programId}")
    public OperationResponse<Map<String, Object>> getProgramStats(@PathVariable Long programId)
            throws ScrmException {
        return OperationResponse.build(scrmReferralService.getProgramStats(programId));
    }

    /**
     * 推荐人排行: 按推荐数与成功推荐数倒序返回 Top N。
     *
     * @param limit 返回条数 (默认 10)
     * @return 推荐人排行列表
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/stats/leaderboard")
    public OperationResponse<List<Map<String, Object>>> getReferrerLeaderboard(
            @RequestParam(defaultValue = "10") int limit) {
        return OperationResponse.build(scrmReferralService.getReferrerLeaderboard(limit));
    }

    /**
     * 推荐趋势: 按天统计最近 N 天的推荐数量。
     *
     * @param days 天数 (默认 7)
     * @return 趋势数据 (date + count)
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<List<Map<String, Object>>> getReferralTrend(
            @RequestParam(defaultValue = "7") int days) {
        return OperationResponse.build(scrmReferralService.getReferralTrend(days));
    }

    /**
     * 转化漏斗: 推荐 → 注册 → 达标 → 奖励。
     *
     * @param programId 活动 ID
     * @return 漏斗数据 (stage + count + conversionRate)
     * @throws ScrmException 活动不存在
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/stats/funnel/{programId}")
    public OperationResponse<Map<String, Object>> getConversionFunnel(@PathVariable Long programId)
            throws ScrmException {
        return OperationResponse.build(scrmReferralService.getConversionFunnel(programId));
    }

    /**
     * 奖励统计: 各类型 / 发放率 / 兑换率。
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_referral", action = "read")
    @GetMapping("/stats/rewards")
    public OperationResponse<Map<String, Object>> getRewardStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmReferralService.getRewardStats(startTime, endTime));
    }
}
