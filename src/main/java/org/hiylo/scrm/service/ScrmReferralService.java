/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmReferralCreateDto;
import org.hiylo.scrm.dto.ScrmReferralDto;
import org.hiylo.scrm.dto.ScrmReferralProgramDto;
import org.hiylo.scrm.dto.ScrmReferralQualifyDto;
import org.hiylo.scrm.dto.ScrmReferralRewardDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户推荐管理服务 (门面)。
 * <p>
 * 作为推荐模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmReferralProgramService} (推荐活动)、{@link ScrmReferralRelationshipService}
 * (推荐关系)、{@link ScrmReferralRewardService} (奖励管理) 与
 * {@link ScrmReferralStatsService} (统计排行)。推荐码格式为 RF + 推荐人 ID 的 Base36
 * + 随机串。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmReferralService {

    /** 推荐活动子域服务 */
    private final ScrmReferralProgramService programService;

    /** 推荐关系子域服务 */
    private final ScrmReferralRelationshipService relationshipService;

    /** 奖励管理子域服务 */
    private final ScrmReferralRewardService rewardService;

    /** 统计排行子域服务 */
    private final ScrmReferralStatsService statsService;

    // ============================================================
    // 推荐活动 Program
    // ============================================================

    /**
     * 创建推荐活动。
     * <p>校验活动编码唯一与枚举合法性后写入归属账号 ID 持久化, 奖励触发条件缺省 SIGNUP,
     * 双向奖励缺省 TRUE, 推荐上限缺省 0 (无限), 状态缺省 ACTIVE, 统计字段缺省 0。</p>
     *
     * @param dto 活动参数
     * @return 创建后的活动
     * @throws ScrmException 参数非法 / 活动编码重复
     */
    public ScrmReferralProgramDto createProgram(ScrmReferralProgramDto dto) throws ScrmException {
        return programService.createProgram(dto);
    }

    /**
     * 更新推荐活动（字段非空才覆盖）。
     * <p>状态、统计字段通过专用接口维护, 此处不直接修改。活动编码变更时校验唯一性。</p>
     *
     * @param id  活动 ID
     * @param dto 活动参数
     * @return 更新后的活动
     * @throws ScrmException 活动不存在 / 参数非法 / 活动编码重复
     */
    public ScrmReferralProgramDto updateProgram(Long id, ScrmReferralProgramDto dto) throws ScrmException {
        return programService.updateProgram(id, dto);
    }

    /**
     * 删除推荐活动。
     *
     * @param id 活动 ID
     * @throws ScrmException 活动不存在
     */
    public void deleteProgram(Long id) throws ScrmException {
        programService.deleteProgram(id);
    }

    /**
     * 查询推荐活动详情。
     *
     * @param id 活动 ID
     * @return 活动 DTO
     * @throws ScrmException 活动不存在
     */
    public ScrmReferralProgramDto getProgram(Long id) throws ScrmException {
        return programService.getProgram(id);
    }

    /**
     * 按活动编码查询推荐活动。
     *
     * @param code 活动编码
     * @return 活动 DTO
     * @throws ScrmException 活动不存在
     */
    public ScrmReferralProgramDto getProgramByCode(String code) throws ScrmException {
        return programService.getProgramByCode(code);
    }

    /**
     * 分页查询推荐活动, 支持按活动类型、状态与关键词过滤。
     *
     * @param programType 活动类型过滤 (可空)
     * @param status      状态过滤 (可空)
     * @param keyword     关键词过滤, 匹配活动名称/编码/描述 (可空)
     * @param pageable    分页参数
     * @return 活动分页结果 (按创建时间倒序)
     */
    public Page<ScrmReferralProgramDto> listPrograms(String programType, String status, String keyword,
                                                       Pageable pageable) {
        return programService.listPrograms(programType, status, keyword, pageable);
    }

    /**
     * 激活推荐活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在
     */
    public ScrmReferralProgramDto activateProgram(Long id) throws ScrmException {
        return programService.activateProgram(id);
    }

    /**
     * 暂停推荐活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在
     */
    public ScrmReferralProgramDto pauseProgram(Long id) throws ScrmException {
        return programService.pauseProgram(id);
    }

    /**
     * 完成推荐活动。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在
     */
    public ScrmReferralProgramDto completeProgram(Long id) throws ScrmException {
        return programService.completeProgram(id);
    }

    /**
     * 更新活动统计 (总推荐数/成功推荐数/累计奖励价值)。
     *
     * @param id 活动 ID
     * @return 更新后的活动
     * @throws ScrmException 活动不存在
     */
    public ScrmReferralProgramDto updateProgramStats(Long id) throws ScrmException {
        return programService.updateProgramStats(id);
    }

    // ============================================================
    // 推荐关系 Referral
    // ============================================================

    /**
     * 创建推荐。
     * <p>校验活动有效 (ACTIVE 且在活动周期内)、单人推荐上限, 生成推荐码后创建推荐记录,
     * 推荐渠道缺省 CODE, 状态缺省 PENDING, 奖励状态缺省 PENDING, 同时刷新活动统计。</p>
     *
     * @param createDto 创建参数
     * @return 创建后的推荐
     * @throws ScrmException 活动不存在 / 活动非活跃 / 超出推荐上限
     */
    public ScrmReferralDto createReferral(ScrmReferralCreateDto createDto) throws ScrmException {
        return relationshipService.createReferral(createDto);
    }

    /**
     * 查询推荐详情。
     *
     * @param id 推荐 ID
     * @return 推荐 DTO
     * @throws ScrmException 推荐不存在
     */
    public ScrmReferralDto getReferral(Long id) throws ScrmException {
        return relationshipService.getReferral(id);
    }

    /**
     * 按推荐码查询推荐。
     *
     * @param code 推荐码
     * @return 推荐 DTO
     * @throws ScrmException 推荐不存在
     */
    public ScrmReferralDto getReferralByCode(String code) throws ScrmException {
        return relationshipService.getReferralByCode(code);
    }

    /**
     * 分页查询推荐, 支持按活动、推荐人、状态与时间范围过滤。
     *
     * @param programId          活动过滤 (可空)
     * @param referrerCustomerId 推荐人过滤 (可空)
     * @param status             状态过滤 (可空)
     * @param startTime          起始时间 (按创建时间, 可空)
     * @param endTime            截止时间 (按创建时间, 可空)
     * @param pageable           分页参数
     * @return 推荐分页结果 (按创建时间倒序)
     */
    public Page<ScrmReferralDto> listReferrals(Long programId, Long referrerCustomerId, String status,
                                                LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        return relationshipService.listReferrals(programId, referrerCustomerId, status,
                startTime, endTime, pageable);
    }

    /**
     * 查询推荐人的推荐列表。
     *
     * @param customerId 推荐人客户 ID
     * @param pageable   分页参数
     * @return 推荐分页结果 (按创建时间倒序)
     */
    public Page<ScrmReferralDto> getReferralsByReferrer(Long customerId, Pageable pageable) {
        return relationshipService.getReferralsByReferrer(customerId, pageable);
    }

    /**
     * 被推荐人注册 (按推荐码)。
     * <p>校验推荐码存在且状态为 PENDING, 关联被推荐人客户 ID, 状态置 SIGNED_UP, 设置 signedUpAt。
     * 若活动触发条件为 SIGNUP, 则自动达标 (置 QUALIFIED); 否则保持 SIGNED_UP 等待达标。</p>
     *
     * @param referralCode      推荐码
     * @param refereeCustomerId 被推荐人客户 ID
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法
     */
    public ScrmReferralDto signUpReferral(String referralCode, Long refereeCustomerId) throws ScrmException {
        return relationshipService.signUpReferral(referralCode, refereeCustomerId);
    }

    /**
     * 推荐达标 (检查触发条件→更新状态)。
     * <p>按活动奖励触发条件校验: FIRST_PURCHASE 需 purchaseAmount&gt;0; PURCHASE_AMOUNT 需
     * purchaseAmount≥触发条件值; RETENTION_DAYS 需注册后达到留存天数 (按 signedUpAt 与触发条件值估算);
     * SIGNUP 无需调用此接口 (注册时自动达标)。达标后状态置 QUALIFIED, 设置 qualifiedAt 与 purchaseAmount。</p>
     *
     * @param qualifyDto 达标参数
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法 / 未达触发条件
     */
    public ScrmReferralDto qualifyReferral(ScrmReferralQualifyDto qualifyDto) throws ScrmException {
        return relationshipService.qualifyReferral(qualifyDto);
    }

    /**
     * 取消推荐。
     *
     * @param id     推荐 ID
     * @param reason 取消原因 (可空)
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法
     */
    public ScrmReferralDto cancelReferral(Long id, String reason) throws ScrmException {
        return relationshipService.cancelReferral(id, reason);
    }

    /**
     * 过期推荐。
     *
     * @param id 推荐 ID
     * @return 更新后的推荐
     * @throws ScrmException 推荐不存在 / 状态非法
     */
    public ScrmReferralDto expireReferral(Long id) throws ScrmException {
        return relationshipService.expireReferral(id);
    }

    /**
     * 生成推荐码 (RF + 推荐人 ID 的 Base36 + 随机串, 保证唯一)。
     *
     * @param programId  活动 ID
     * @param customerId 推荐人客户 ID
     * @return 推荐码
     */
    public String generateReferralCode(Long programId, Long customerId) {
        return relationshipService.generateReferralCode(programId, customerId);
    }

    /**
     * 生成推荐链接 (基于推荐码与渠道, 模拟实现)。
     * <p>格式: https://referral.example.com/r/{referralCode}?channel={channel}</p>
     *
     * @param programId  活动 ID
     * @param customerId 推荐人客户 ID
     * @param channel    推荐渠道 (可空, 缺省 LINK)
     * @return 推荐链接
     * @throws ScrmException 活动不存在
     */
    public String generateReferralLink(Long programId, Long customerId, String channel) throws ScrmException {
        return relationshipService.generateReferralLink(programId, customerId, channel);
    }

    // ============================================================
    // 奖励 Reward
    // ============================================================

    /**
     * 发放奖励 (模拟实现: 创建奖励记录→更新状态)。
     * <p>按接收者类型从活动读取奖励配置创建奖励记录, 状态置 ISSUED, 设置 issuedAt 与交易 ID。
     * 同时更新推荐记录对应方向的奖励状态, 双方均已发放时推荐状态置 REWARDED 并设置 rewardedAt。
     * 最终刷新活动统计 (累计奖励价值)。</p>
     *
     * @param referralId    推荐记录 ID
     * @param recipientType 接收者类型: REFERRER / REFEREE
     * @return 创建后的奖励
     * @throws ScrmException 推荐不存在 / 接收者类型非法 / 奖励已发放
     */
    public ScrmReferralRewardDto issueReward(Long referralId, String recipientType) throws ScrmException {
        return rewardService.issueReward(referralId, recipientType);
    }

    /**
     * 兑换奖励。
     *
     * @param rewardId 奖励 ID
     * @return 更新后的奖励
     * @throws ScrmException 奖励不存在 / 状态非法
     */
    public ScrmReferralRewardDto redeemReward(Long rewardId) throws ScrmException {
        return rewardService.redeemReward(rewardId);
    }

    /**
     * 过期奖励。
     *
     * @param rewardId 奖励 ID
     * @return 更新后的奖励
     * @throws ScrmException 奖励不存在 / 状态非法
     */
    public ScrmReferralRewardDto expireReward(Long rewardId) throws ScrmException {
        return rewardService.expireReward(rewardId);
    }

    /**
     * 查询奖励详情。
     *
     * @param id 奖励 ID
     * @return 奖励 DTO
     * @throws ScrmException 奖励不存在
     */
    public ScrmReferralRewardDto getReward(Long id) throws ScrmException {
        return rewardService.getReward(id);
    }

    /**
     * 分页查询奖励, 支持按推荐记录、接收者类型与状态过滤。
     *
     * @param referralId    推荐记录过滤 (可空)
     * @param recipientType 接收者类型过滤 (可空)
     * @param status        状态过滤 (可空)
     * @param pageable      分页参数
     * @return 奖励分页结果 (按创建时间倒序)
     */
    public Page<ScrmReferralRewardDto> listRewards(Long referralId, String recipientType, String status,
                                                    Pageable pageable) {
        return rewardService.listRewards(referralId, recipientType, status, pageable);
    }

    /**
     * 查询客户奖励列表。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 奖励分页结果 (按创建时间倒序)
     */
    public Page<ScrmReferralRewardDto> getCustomerRewards(Long customerId, Pageable pageable) {
        return rewardService.getCustomerRewards(customerId, pageable);
    }

    /**
     * 批量发放奖励。
     * <p>按推荐记录 ID 列表逐个发放推荐人方向奖励, 跳过失败记录 (记录日志), 返回成功发放的奖励列表。</p>
     *
     * @param referralIds 推荐记录 ID 列表
     * @return 成功发放的奖励列表
     */
    public List<ScrmReferralRewardDto> batchIssueRewards(List<Long> referralIds) {
        return rewardService.batchIssueRewards(referralIds);
    }

    // ============================================================
    // 统计 Stats
    // ============================================================

    /**
     * 推荐统计: 总推荐数 / 成功率 / 转化率 / 总奖励。
     * <p>时间范围按推荐创建时间过滤, 为空时统计全量。转化率 = 成功推荐数 / 总推荐数;
     * 奖励统计来自奖励表汇总 (排除已取消)。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getReferralStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getReferralStats(startTime, endTime);
    }

    /**
     * 活动统计: 总推荐数 / 成功推荐数 / 累计奖励价值 / 状态分布。
     *
     * @param programId 活动 ID
     * @return 统计结果
     * @throws ScrmException 活动不存在
     */
    public Map<String, Object> getProgramStats(Long programId) throws ScrmException {
        return statsService.getProgramStats(programId);
    }

    /**
     * 推荐人排行: 按推荐数与成功推荐数倒序返回 Top N。
     *
     * @param limit 返回条数 (默认 10)
     * @return 推荐人排行列表
     */
    public List<Map<String, Object>> getReferrerLeaderboard(int limit) {
        return statsService.getReferrerLeaderboard(limit);
    }

    /**
     * 推荐趋势: 按天统计最近 N 天的推荐数量。
     *
     * @param days 天数 (默认 7)
     * @return 趋势数据 (date + count)
     */
    public List<Map<String, Object>> getReferralTrend(int days) {
        return statsService.getReferralTrend(days);
    }

    /**
     * 转化漏斗: 推荐 → 注册 → 达标 → 奖励。
     *
     * @param programId 活动 ID
     * @return 漏斗数据 (stage + count + conversionRate)
     * @throws ScrmException 活动不存在
     */
    public Map<String, Object> getConversionFunnel(Long programId) throws ScrmException {
        return statsService.getConversionFunnel(programId);
    }

    /**
     * 奖励统计: 各类型 / 发放率 / 兑换率。
     * <p>时间范围按奖励创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getRewardStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getRewardStats(startTime, endTime);
    }
}