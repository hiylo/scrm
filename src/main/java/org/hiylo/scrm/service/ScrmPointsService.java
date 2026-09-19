/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmPointsAccountDto;
import org.hiylo.scrm.dto.ScrmPointsExchangeDto;
import org.hiylo.scrm.dto.ScrmPointsExchangeRecordDto;
import org.hiylo.scrm.dto.ScrmPointsOperationDto;
import org.hiylo.scrm.dto.ScrmPointsRuleDto;
import org.hiylo.scrm.dto.ScrmPointsTransactionDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 积分/会员体系服务 (门面)。
 * <p>
 * 作为积分模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmPointsRuleService} (积分规则)、{@link ScrmPointsAccountService} (积分账户)、
 * {@link ScrmPointsTransactionService} (积分交易)、{@link ScrmPointsExchangeService}
 * (兑换管理) 与 {@link ScrmPointsStatsService} (统计排行)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmPointsService {

    /** 积分规则管理子域服务 */
    private final ScrmPointsRuleService ruleService;

    /** 积分账户管理子域服务 */
    private final ScrmPointsAccountService accountService;

    /** 积分交易子域服务 */
    private final ScrmPointsTransactionService transactionService;

    /** 积分兑换管理子域服务 */
    private final ScrmPointsExchangeService exchangeService;

    /** 积分统计排行子域服务 */
    private final ScrmPointsStatsService statsService;

    // ============================================================
    // 规则管理
    // ============================================================

    /**
     * 创建积分规则。
     * <p>校验参数合法性后写入归属账号 ID 持久化, pointsType / enabled / minPoints / triggerCount
     * 缺省时填默认值。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    public ScrmPointsRuleDto createRule(ScrmPointsRuleDto dto) throws ScrmException {
        return ruleService.createRule(dto);
    }

    /**
     * 更新积分规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    public ScrmPointsRuleDto updateRule(Long id, ScrmPointsRuleDto dto) throws ScrmException {
        return ruleService.updateRule(id, dto);
    }

    /**
     * 删除积分规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    public void deleteRule(Long id) throws ScrmException {
        ruleService.deleteRule(id);
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则 DTO
     * @throws ScrmException 规则不存在
     */
    public ScrmPointsRuleDto getRule(Long id) throws ScrmException {
        return ruleService.getRule(id);
    }

    /**
     * 分页查询积分规则, 支持按规则类型 / 触发事件 / 启用状态过滤。
     *
     * @param ruleType     规则类型过滤: EARN / REDEEM（可空）
     * @param triggerEvent 触发事件过滤（可空）
     * @param enabled      启用状态过滤（可空）
     * @param pageable     分页参数
     * @return 规则分页结果 (按 createTime DESC)
     */
    public Page<ScrmPointsRuleDto> listRules(String ruleType, String triggerEvent,
                                             Boolean enabled, Pageable pageable) {
        return ruleService.listRules(ruleType, triggerEvent, enabled, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmPointsRuleDto enableRule(Long id) throws ScrmException {
        return ruleService.enableRule(id);
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmPointsRuleDto disableRule(Long id) throws ScrmException {
        return ruleService.disableRule(id);
    }

    // ============================================================
    // 账户管理
    // ============================================================

    /**
     * 获取或创建积分账户 (不存在则初始化为 0 积分)。
     *
     * @param customerId 客户 ID
     * @return 积分账户 DTO
     * @throws ScrmException 客户 ID 非法
     */
    public ScrmPointsAccountDto getOrCreateAccount(Long customerId) throws ScrmException {
        return accountService.getOrCreateAccount(customerId);
    }

    /**
     * 查询客户积分账户 (不存在抛异常)。
     *
     * @param customerId 客户 ID
     * @return 积分账户 DTO
     * @throws ScrmException 账户不存在
     */
    public ScrmPointsAccountDto getAccount(Long customerId) throws ScrmException {
        return accountService.getAccount(customerId);
    }

    /**
     * 分页查询积分账户, 支持按积分区间与客户名称关键字过滤。
     *
     * @param minPoints 最小积分过滤（可空）
     * @param maxPoints 最大积分过滤（可空）
     * @param keyword   客户名称关键字过滤（可空）
     * @param pageable  分页参数
     * @return 账户分页结果 (按 currentPoints DESC)
     */
    public Page<ScrmPointsAccountDto> listAccounts(Integer minPoints, Integer maxPoints,
                                                   String keyword, Pageable pageable) {
        return accountService.listAccounts(minPoints, maxPoints, keyword, pageable);
    }

    /**
     * 手动调整积分 (管理员加/减积分)。
     * <p>type=EARN 增加积分并累计获取, type=REDEEM 扣减积分并累计消耗, 记录 ADJUST 流水
     * (sourceType=MANUAL)。</p>
     *
     * @param dto 操作参数 (customerId + points + type + reason)
     * @return 更新后的账户
     * @throws ScrmException 参数非法 / 积分不足
     */
    public ScrmPointsAccountDto adjustPoints(ScrmPointsOperationDto dto) throws ScrmException {
        return accountService.adjustPoints(dto);
    }

    /**
     * 冻结积分 (从可用转入冻结)。
     *
     * @param customerId 客户 ID
     * @param points     冻结积分数量
     * @param reason     冻结原因
     * @return 更新后的账户
     * @throws ScrmException 账户不存在 / 积分不足
     */
    public ScrmPointsAccountDto freezePoints(Long customerId, Integer points, String reason) throws ScrmException {
        return accountService.freezePoints(customerId, points, reason);
    }

    /**
     * 解冻积分 (从冻结转回可用)。
     *
     * @param customerId 客户 ID
     * @param points     解冻积分数量
     * @param reason     解冻原因
     * @return 更新后的账户
     * @throws ScrmException 账户不存在 / 冻结积分不足
     */
    public ScrmPointsAccountDto unfreezePoints(Long customerId,
            Integer points, String reason) throws ScrmException {
        return accountService.unfreezePoints(customerId, points, reason);
    }

    // ============================================================
    // 流水 (获取 / 消耗 / 查询)
    // ============================================================

    /**
     * 获取积分 (匹配规则→计算积分→更新账户→记录流水)。
     * <p>按触发事件匹配启用 EARN 规则, 逐条计算积分 (FIXED 固定 / PERCENTAGE 按 basisValue 百分比),
     * 应用 minPoints/maxPoints 与每日/每月上限后累加到账户, 记录 EARN 流水 (默认 365 天后过期)。</p>
     *
     * @param customerId   客户 ID
     * @param triggerEvent 触发事件
     * @param basisValue   基准值 (如订单金额, PERCENTAGE 规则使用, 可空)
     * @return 更新后的账户
     * @throws ScrmException 参数非法
     */
    public ScrmPointsAccountDto earnPoints(Long customerId,
            String triggerEvent, Double basisValue) throws ScrmException {
        return transactionService.earnPoints(customerId, triggerEvent, basisValue);
    }

    /**
     * 消耗积分 (手动扣减)。
     *
     * @param customerId 客户 ID
     * @param points     消耗积分数量
     * @param reason     消耗原因
     * @return 更新后的账户
     * @throws ScrmException 账户不存在 / 积分不足
     */
    public ScrmPointsAccountDto redeemPoints(Long customerId, Integer points, String reason) throws ScrmException {
        return transactionService.redeemPoints(customerId, points, reason);
    }

    /**
     * 分页查询积分流水, 支持按客户 / 交易类型 / 时间区间过滤。
     *
     * @param customerId      客户 ID 过滤（可空）
     * @param transactionType 交易类型过滤（可空）
     * @param startTime       起始时间过滤（可空）
     * @param endTime         截止时间过滤（可空）
     * @param pageable        分页参数
     * @return 流水分页结果 (按 createdAt DESC)
     */
    public Page<ScrmPointsTransactionDto> getTransactions(Long customerId, String transactionType,
                                                          LocalDateTime startTime, LocalDateTime endTime,
                                                          Pageable pageable) {
        return transactionService.getTransactions(customerId, transactionType, startTime, endTime, pageable);
    }

    /**
     * 查询流水详情。
     *
     * @param id 流水 ID
     * @return 流水 DTO
     * @throws ScrmException 流水不存在
     */
    public ScrmPointsTransactionDto getTransaction(Long id) throws ScrmException {
        return transactionService.getTransaction(id);
    }

    // ============================================================
    // 兑换商品管理
    // ============================================================

    /**
     * 创建积分兑换商品。
     *
     * @param dto 商品参数
     * @return 创建后的商品
     * @throws ScrmException 参数非法
     */
    public ScrmPointsExchangeDto createExchangeItem(ScrmPointsExchangeDto dto) throws ScrmException {
        return exchangeService.createExchangeItem(dto);
    }

    /**
     * 更新兑换商品（字段非空才覆盖）。
     *
     * @param id  商品 ID
     * @param dto 商品参数
     * @return 更新后的商品
     * @throws ScrmException 商品不存在 / 参数非法
     */
    public ScrmPointsExchangeDto updateExchangeItem(Long id, ScrmPointsExchangeDto dto) throws ScrmException {
        return exchangeService.updateExchangeItem(id, dto);
    }

    /**
     * 删除兑换商品。
     *
     * @param id 商品 ID
     * @throws ScrmException 商品不存在
     */
    public void deleteExchangeItem(Long id) throws ScrmException {
        exchangeService.deleteExchangeItem(id);
    }

    /**
     * 查询兑换商品详情。
     *
     * @param id 商品 ID
     * @return 商品 DTO
     * @throws ScrmException 商品不存在
     */
    public ScrmPointsExchangeDto getExchangeItem(Long id) throws ScrmException {
        return exchangeService.getExchangeItem(id);
    }

    /**
     * 分页查询兑换商品, 支持按分类 / 状态过滤。
     *
     * @param category 分类过滤（可空）
     * @param status   状态过滤（可空）
     * @param pageable 分页参数
     * @return 商品分页结果 (按 createTime DESC)
     */
    public Page<ScrmPointsExchangeDto> listExchangeItems(String category, String status, Pageable pageable) {
        return exchangeService.listExchangeItems(category, status, pageable);
    }

    /**
     * 上架兑换商品。
     *
     * @param id 商品 ID
     * @return 更新后的商品
     * @throws ScrmException 商品不存在
     */
    public ScrmPointsExchangeDto activateExchangeItem(Long id) throws ScrmException {
        return exchangeService.activateExchangeItem(id);
    }

    /**
     * 下架兑换商品。
     *
     * @param id 商品 ID
     * @return 更新后的商品
     * @throws ScrmException 商品不存在
     */
    public ScrmPointsExchangeDto deactivateExchangeItem(Long id) throws ScrmException {
        return exchangeService.deactivateExchangeItem(id);
    }

    // ============================================================
    // 兑换动作 (兑换 / 完成 / 取消 / 记录查询)
    // ============================================================

    /**
     * 积分兑换商品 (加锁校验积分→扣减→生成兑换码→记录)。
     * <p>校验商品可兑换、库存充足、未超出每人限兑、积分充足, 扣减积分与库存, 生成兑换码与兑换记录,
     * 记录 REDEEM 流水 (sourceType=EXCHANGE)。库存为 0 时自动置为 SOLD_OUT。</p>
     * <p>并发防护: 兑换商品行与积分账户行均以 {@code PESSIMISTIC_WRITE} 悲观锁锁定 (同一事务内先商品后账户,
     * 顺序一致无死锁), 串行化同一商品 / 同一客户并发兑换; 库存扣减使用原子 UPDATE 并带
     * {@code stock_quantity >= :qty} 守卫, 行数=0 视为库存不足回滚事务。</p>
     *
     * @param customerId 客户 ID
     * @param exchangeId 兑换商品 ID
     * @param quantity   兑换数量
     * @return 兑换记录 DTO
     * @throws ScrmException 商品不存在 / 不可兑换 / 库存不足 / 超出限兑 / 积分不足
     */
    public ScrmPointsExchangeRecordDto exchange(Long customerId, Long exchangeId, Integer quantity)
            throws ScrmException {
        return exchangeService.exchange(customerId, exchangeId, quantity);
    }

    /**
     * 完成兑换 (填写物流单号并标记完成)。
     *
     * @param recordId   兑换记录 ID
     * @param shippingNo 物流单号 (可空)
     * @return 更新后的兑换记录
     * @throws ScrmException 记录不存在 / 状态非法
     */
    public ScrmPointsExchangeRecordDto completeExchange(Long recordId, String shippingNo) throws ScrmException {
        return exchangeService.completeExchange(recordId, shippingNo);
    }

    /**
     * 取消兑换 (返还积分并恢复库存)。
     * <p>并发防护: 与 {@link #exchange} 使用同一套悲观锁 (先商品行后账户行, 加锁顺序一致无死锁),
     * 串行化同一商品/同一客户的兑换与取消操作, 避免并发时积分返还或库存恢复丢失更新。</p>
     *
     * @param recordId 兑换记录 ID
     * @param reason   取消原因
     * @return 更新后的兑换记录
     * @throws ScrmException 记录不存在 / 已完成不允许取消
     */
    public ScrmPointsExchangeRecordDto cancelExchange(Long recordId, String reason) throws ScrmException {
        return exchangeService.cancelExchange(recordId, reason);
    }

    /**
     * 分页查询兑换记录, 支持按客户 / 商品 / 状态过滤。
     *
     * @param customerId 客户 ID 过滤（可空）
     * @param exchangeId 兑换商品 ID 过滤（可空）
     * @param status     状态过滤（可空）
     * @param pageable   分页参数
     * @return 兑换记录分页结果 (按 exchangedAt DESC)
     */
    public Page<ScrmPointsExchangeRecordDto> getExchangeRecords(Long customerId, Long exchangeId,
                                                                String status, Pageable pageable) {
        return exchangeService.getExchangeRecords(customerId, exchangeId, status, pageable);
    }

    // ============================================================
    // 过期处理
    // ============================================================

    /**
     * 过期处理 (定时任务, 清零过期积分)。
     * <p>扫描所有 expiresAt 早于当前时间且未标记过期的 EARN 流水, 按实际可扣减积分清零账户可用积分,
     * 累计过期, 记录 EXPIRE 流水并标记原流水已过期。</p>
     *
     * @return 处理结果 (total 待处理数 / processed 实际处理数 / expiredPoints 过期积分总数)
     */
    public Map<String, Object> processExpiredPoints() {
        return transactionService.processExpiredPoints();
    }

    /**
     * 查询客户即将过期的积分 (指定天数内)。
     *
     * @param customerId 客户 ID
     * @param days       天数 (查询未来 days 天内将过期的流水)
     * @return 即将过期的流水列表
     * @throws ScrmException 账户不存在
     */
    public List<ScrmPointsTransactionDto> getExpiringPoints(Long customerId, int days) throws ScrmException {
        return transactionService.getExpiringPoints(customerId, days);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 积分总览 (总发放 / 总消耗 / 总过期 / 活跃账户数)。
     *
     * @return 积分总览统计
     */
    public Map<String, Object> getPointsStats() {
        return statsService.getPointsStats();
    }

    /**
     * 积分排行榜 (按当前可用积分倒序)。
     *
     * @param pageable 分页参数
     * @return 账户分页结果 (按 currentPoints DESC)
     */
    public Page<ScrmPointsAccountDto> getCustomerPointsRanking(Pageable pageable) {
        return statsService.getCustomerPointsRanking(pageable);
    }

    /**
     * 兑换统计 (指定时间区间内的兑换记录数与消耗积分总额)。
     *
     * @param startTime 起始时间（可空）
     * @param endTime   截止时间（可空）
     * @return 兑换统计
     */
    public Map<String, Object> getExchangeStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getExchangeStats(startTime, endTime);
    }
}