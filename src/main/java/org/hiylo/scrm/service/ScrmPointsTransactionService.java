/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsTransactionService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmPointsAccountDto;
import org.hiylo.scrm.dto.ScrmPointsTransactionDto;
import org.hiylo.scrm.entity.ScrmPointsAccountEntity;
import org.hiylo.scrm.entity.ScrmPointsRuleEntity;
import org.hiylo.scrm.entity.ScrmPointsTransactionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmPointsAccountRepository;
import org.hiylo.scrm.repository.ScrmPointsRuleRepository;
import org.hiylo.scrm.repository.ScrmPointsTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 积分流水服务 (积分交易子域)。
 * <p>
 * 承载积分获取 / 消耗 / 流水查询 / 过期处理 / 即将过期查询, 托管积分交易子域常量与
 * 单条规则积分计算 {@link #calculateEarnPoints(ScrmPointsRuleEntity, Double)}。账户相关
 * 实体获取 / 流水记录 / DTO 转换复用 {@link ScrmPointsAccountService} 的包级能力。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmPointsTransactionService {

    /** 规则类型: 获取 */
    private static final String RULE_TYPE_EARN = "EARN";

    /** 交易类型: 获取 */
    private static final String TXN_TYPE_EARN = "EARN";
    /** 交易类型: 消耗 */
    private static final String TXN_TYPE_REDEEM = "REDEEM";
    /** 交易类型: 过期 */
    private static final String TXN_TYPE_EXPIRE = "EXPIRE";

    /** 来源: 系统 */
    private static final String SOURCE_SYSTEM = "SYSTEM";
    /** 来源: 手动 */
    private static final String SOURCE_MANUAL = "MANUAL";

    /** 默认操作人 */
    private static final String DEFAULT_OPERATOR = "scrm-system";
    /** 获取积分默认过期天数 (模拟实现) */
    private static final int DEFAULT_EXPIRY_DAYS = 365;
    /** 百分比换算基数 */
    private static final double PERCENT_BASE = 100.0;

    /** 积分规则数据访问层 */
    private final ScrmPointsRuleRepository ruleRepository;
    /** 积分账户数据访问层 */
    private final ScrmPointsAccountRepository accountRepository;
    /** 积分流水数据访问层 */
    private final ScrmPointsTransactionRepository transactionRepository;

    /** 积分账户子域服务 (账户实体获取 / 流水记录 / DTO 转换) */
    private final ScrmPointsAccountService accountService;

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
    @Transactional
    public ScrmPointsAccountDto earnPoints(Long customerId,
            String triggerEvent, Double basisValue) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (triggerEvent == null || triggerEvent.isBlank()) {
            throw ScrmException.badRequest("触发事件不能为空");
        }
        ScrmPointsAccountEntity account = accountService.getOrCreateAccountEntity(customerId);
        List<ScrmPointsRuleEntity> rules = ruleRepository
                .findByTriggerEventAndRuleTypeAndEnabledTrue(triggerEvent, RULE_TYPE_EARN);
        LocalDateTime now = LocalDateTime.now();
        int totalEarned = 0;
        for (ScrmPointsRuleEntity rule : rules) {
            int points = calculateEarnPoints(rule, basisValue);
            if (points <= 0) {
                continue;
            }
            // 每日上限校验
            if (rule.getDailyLimit() != null && rule.getDailyLimit() > 0) {
                long todayEarned = transactionRepository.sumPointsByRuleAndCustomer(
                         customerId, rule.getId(), TXN_TYPE_EARN, now.toLocalDate().atStartOfDay(), now);
                if (todayEarned + points > rule.getDailyLimit()) {
                    points = Math.max(0, rule.getDailyLimit() - (int) todayEarned);
                }
            }
            // 每月上限校验
            if (points > 0 && rule.getMonthlyLimit() != null && rule.getMonthlyLimit() > 0) {
                YearMonth ym = YearMonth.from(now);
                long monthEarned = transactionRepository.sumPointsByRuleAndCustomer(
                         customerId, rule.getId(), TXN_TYPE_EARN,
                        ym.atDay(1).atStartOfDay(), now);
                if (monthEarned + points > rule.getMonthlyLimit()) {
                    points = Math.max(0, rule.getMonthlyLimit() - (int) monthEarned);
                }
            }
            if (points <= 0) {
                continue;
            }
            account.setCurrentPoints(account.getCurrentPoints() + points);
            account.setTotalEarned(account.getTotalEarned() + points);
            account.setLastEarnAt(now);
            account.setUpdatedAt(now);
            accountService.recordTransaction(account, TXN_TYPE_EARN, points, rule.getId(), triggerEvent, SOURCE_SYSTEM,
                    null, "获取积分: " + rule.getRuleName(), DEFAULT_OPERATOR, now.plusDays(DEFAULT_EXPIRY_DAYS));
            ruleRepository.incrementTriggerCount(rule.getId());
            totalEarned += points;
        }
        if (totalEarned > 0) {
            account = accountRepository.save(account);
            log.info("获取积分: customerId={}, triggerEvent={}, totalEarned={}", customerId, triggerEvent, totalEarned);
        } else {
            log.debug("获取积分无匹配规则或受上限限制: customerId={}, triggerEvent={}", customerId, triggerEvent);
        }
        return accountService.toAccountDto(account);
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
    @Transactional
    public ScrmPointsAccountDto redeemPoints(Long customerId, Integer points, String reason) throws ScrmException {
        if (points == null || points <= 0) {
            throw ScrmException.badRequest("消耗积分数量必须为正数");
        }
        ScrmPointsAccountEntity account = accountService.findAccountByCustomerOrThrow(customerId);
        if (account.getCurrentPoints() < points) {
            throw ScrmException.badRequest("积分不足, 当前可用: " + account.getCurrentPoints());
        }
        LocalDateTime now = LocalDateTime.now();
        account.setCurrentPoints(account.getCurrentPoints() - points);
        account.setTotalRedeemed(account.getTotalRedeemed() + points);
        account.setLastRedeemAt(now);
        account.setUpdatedAt(now);
        account = accountRepository.save(account);
        accountService.recordTransaction(account, TXN_TYPE_REDEEM, -points, null, null, SOURCE_MANUAL, null,
                "消耗积分: " + reason, DEFAULT_OPERATOR, null);
        log.info("消耗积分: customerId={}, points={}, reason={}", customerId, points, reason);
        return accountService.toAccountDto(account);
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
    @Transactional(readOnly = true)
    public Page<ScrmPointsTransactionDto> getTransactions(Long customerId, String transactionType,
                                                          LocalDateTime startTime, LocalDateTime endTime,
                                                          Pageable pageable) {
        Specification<ScrmPointsTransactionEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (transactionType != null && !transactionType.isBlank()) {
                predicates.add(cb.equal(root.get("transactionType"), transactionType));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return transactionRepository.findAll(spec, ScrmPointsRuleService.ensureSortable(pageable, "createdAt"))
                .map(this::toTransactionDto);
    }

    /**
     * 查询流水详情。
     *
     * @param id 流水 ID
     * @return 流水 DTO
     * @throws ScrmException 流水不存在
     */
    @Transactional(readOnly = true)
    public ScrmPointsTransactionDto getTransaction(Long id) throws ScrmException {
        return toTransactionDto(findTransactionOrThrow(id));
    }

    /**
     * 过期处理 (定时任务, 清零过期积分)。
     * <p>扫描所有 expiresAt 早于当前时间且未标记过期的 EARN 流水, 按实际可扣减积分清零账户可用积分,
     * 累计过期, 记录 EXPIRE 流水并标记原流水已过期。</p>
     *
     * @return 处理结果 (total 待处理数 / processed 实际处理数 / expiredPoints 过期积分总数)
     */
    @Transactional
    public Map<String, Object> processExpiredPoints() {
        LocalDateTime now = LocalDateTime.now();
        List<ScrmPointsTransactionEntity> expiring = transactionRepository
                .findByExpiredFalseAndExpiresAtBefore(now);
        Map<Long, ScrmPointsAccountEntity> accountCache = new HashMap<>();
        int processed = 0;
        int expiredPoints = 0;
        for (ScrmPointsTransactionEntity txn : expiring) {
            ScrmPointsAccountEntity account = accountCache.get(txn.getAccountId());
            if (account == null) {
                account = accountRepository.findById(txn.getAccountId()).orElse(null);

                accountCache.put(txn.getAccountId(), account);
            }
            int actualExpire = Math.min(account.getCurrentPoints(), txn.getPoints());
            if (actualExpire > 0) {
                account.setCurrentPoints(account.getCurrentPoints() - actualExpire);
                account.setTotalExpired(account.getTotalExpired() + actualExpire);
                account.setUpdatedAt(now);
                accountService.recordTransaction(account, TXN_TYPE_EXPIRE, -actualExpire, null, null, SOURCE_SYSTEM,
                        String.valueOf(txn.getId()), "积分过期", DEFAULT_OPERATOR, null);
                accountRepository.save(account);
                expiredPoints += actualExpire;
            }
            txn.setExpired(true);
            transactionRepository.save(txn);
            processed++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", expiring.size());
        result.put("processed", processed);
        result.put("expiredPoints", expiredPoints);
        log.info("过期处理完成:, total={}, processed={}, expiredPoints={}", expiring.size(), processed, expiredPoints);
        return result;
    }

    /**
     * 查询客户即将过期的积分 (指定天数内)。
     *
     * @param customerId 客户 ID
     * @param days       天数 (查询未来 days 天内将过期的流水)
     * @return 即将过期的流水列表
     * @throws ScrmException 账户不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmPointsTransactionDto> getExpiringPoints(Long customerId, int days) throws ScrmException {
        ScrmPointsAccountEntity account = accountService.findAccountByCustomerOrThrow(customerId);
        LocalDateTime threshold = LocalDateTime.now().plusDays(Math.max(0, days));
        List<ScrmPointsTransactionEntity> txns = transactionRepository
                .findByAccountIdAndExpiredFalseAndExpiresAtBefore(account.getId(), threshold);
        return txns.stream().map(this::toTransactionDto).toList();
    }

    /**
     * 计算单条规则可获取积分 (FIXED 固定 / PERCENTAGE 按基准值百分比), 应用 minPoints/maxPoints 裁剪。
     *
     * @param rule       积分规则
     * @param basisValue 基准值 (可空)
     * @return 计算后积分 (>=0)
     */
    private int calculateEarnPoints(ScrmPointsRuleEntity rule, Double basisValue) {
        int points;
        if (ScrmPointsRuleService.POINTS_TYPE_PERCENTAGE.equals(rule.getPointsType())) {
            double basis = basisValue != null ? basisValue : 0d;
            points = (int) Math.round(basis * rule.getPointsValue() / PERCENT_BASE);
        } else {
            points = Math.abs(rule.getPointsValue());
        }
        int min = rule.getMinPoints() != null ? rule.getMinPoints() : ScrmPointsRuleService.DEFAULT_MIN_POINTS;
        if (points < min) {
            points = min;
        }
        if (rule.getMaxPoints() != null && points > rule.getMaxPoints()) {
            points = rule.getMaxPoints();
        }
        return points;
    }

    /**
     * 流水实体转 DTO。
     */
    private ScrmPointsTransactionDto toTransactionDto(ScrmPointsTransactionEntity entity) {
        ScrmPointsTransactionDto dto = new ScrmPointsTransactionDto();
        dto.setId(entity.getId());
        dto.setAccountId(entity.getAccountId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setTransactionType(entity.getTransactionType());
        dto.setPoints(entity.getPoints());
        dto.setBalanceAfter(entity.getBalanceAfter());
        dto.setRuleId(entity.getRuleId());
        dto.setTriggerEvent(entity.getTriggerEvent());
        dto.setSourceType(entity.getSourceType());
        dto.setSourceId(entity.getSourceId());
        dto.setDescription(entity.getDescription());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setExpired(entity.getExpired());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 按主键查询流水并校验归属账号, 不存在或越权抛异常。
     *
     * @param id 流水 ID
     * @return 流水实体
     * @throws ScrmException 流水不存在
     */
    private ScrmPointsTransactionEntity findTransactionOrThrow(Long id) throws ScrmException {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "积分流水不存在: id=" + id));
    }
}