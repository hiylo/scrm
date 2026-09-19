/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsAccountService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmPointsAccountDto;
import org.hiylo.scrm.dto.ScrmPointsOperationDto;
import org.hiylo.scrm.entity.ScrmPointsAccountEntity;
import org.hiylo.scrm.entity.ScrmPointsTransactionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmPointsAccountRepository;
import org.hiylo.scrm.repository.ScrmPointsTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 积分账户服务 (积分账户子域)。
 * <p>
 * 承载积分账户的获取 / 创建 / 分页查询 / 手动调整 (加/减) / 冻结 / 解冻, 并托管账户子域共享能力
 * (账户实体获取 / 悲观锁查询 / 默认账户创建 / DTO 转换 / 流水记录) 供交易 / 兑换 / 统计
 * 兄弟类以包级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmPointsAccountService {

    /** 规则类型: 获取 */
    private static final String RULE_TYPE_EARN = "EARN";
    /** 规则类型: 消耗 */
    private static final String RULE_TYPE_REDEEM = "REDEEM";

    /** 交易类型: 手动调整 */
    private static final String TXN_TYPE_ADJUST = "ADJUST";
    /** 交易类型: 冻结 */
    private static final String TXN_TYPE_FREEZE = "FREEZE";
    /** 交易类型: 解冻 */
    private static final String TXN_TYPE_UNFREEZE = "UNFREEZE";

    /** 来源: 手动 */
    private static final String SOURCE_MANUAL = "MANUAL";

    /** 默认操作人 */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 积分账户数据访问层 */
    private final ScrmPointsAccountRepository accountRepository;
    /** 积分流水数据访问层 */
    private final ScrmPointsTransactionRepository transactionRepository;

    /** JPA 实体管理器 (首次并发创建账户冲突时清空持久化上下文, 丢弃失败插入) */
    private final EntityManager entityManager;

    /**
     * 获取或创建积分账户 (不存在则初始化为 0 积分)。
     *
     * @param customerId 客户 ID
     * @return 积分账户 DTO
     * @throws ScrmException 客户 ID 非法
     */
    @Transactional
    public ScrmPointsAccountDto getOrCreateAccount(Long customerId) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        return toAccountDto(getOrCreateAccountEntity(customerId));
    }

    /**
     * 查询客户积分账户 (不存在抛异常)。
     *
     * @param customerId 客户 ID
     * @return 积分账户 DTO
     * @throws ScrmException 账户不存在
     */
    @Transactional(readOnly = true)
    public ScrmPointsAccountDto getAccount(Long customerId) throws ScrmException {
        return toAccountDto(findAccountByCustomerOrThrow(customerId));
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
    @Transactional(readOnly = true)
    public Page<ScrmPointsAccountDto> listAccounts(Integer minPoints, Integer maxPoints,
                                                   String keyword, Pageable pageable) {
        Specification<ScrmPointsAccountEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (minPoints != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("currentPoints"), minPoints));
            }
            if (maxPoints != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("currentPoints"), maxPoints));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("customerName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.desc(root.get("currentPoints")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return accountRepository.findAll(spec, ScrmPointsRuleService.ensureSortable(pageable, "currentPoints"))
                .map(this::toAccountDto);
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
    @Transactional
    public ScrmPointsAccountDto adjustPoints(ScrmPointsOperationDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("操作参数不能为空");
        }
        if (dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getPoints() == null || dto.getPoints() <= 0) {
            throw ScrmException.badRequest("积分数量必须为正数");
        }
        if (dto.getType() == null || dto.getType().isBlank()) {
            throw ScrmException.badRequest("操作类型不能为空");
        }
        ScrmPointsAccountEntity account = getOrCreateAccountEntity(dto.getCustomerId());
        int points = dto.getPoints();
        LocalDateTime now = LocalDateTime.now();
        String operator = dto.getOperator() != null ? dto.getOperator() : DEFAULT_OPERATOR;
        if (RULE_TYPE_EARN.equals(dto.getType())) {
            account.setCurrentPoints(account.getCurrentPoints() + points);
            account.setTotalEarned(account.getTotalEarned() + points);
            account.setLastEarnAt(now);
            account.setUpdatedAt(now);
            account = accountRepository.save(account);
            recordTransaction(account, TXN_TYPE_ADJUST, points, null, null, SOURCE_MANUAL, null,
                    dto.getReason(), operator, null);
            log.info("手动增加积分: customerId={}, points={}, reason={}", account.getCustomerId(), points, dto.getReason());
        } else if (RULE_TYPE_REDEEM.equals(dto.getType())) {
            if (account.getCurrentPoints() < points) {
                throw ScrmException.badRequest("积分不足, 当前可用: " + account.getCurrentPoints());
            }
            account.setCurrentPoints(account.getCurrentPoints() - points);
            account.setTotalRedeemed(account.getTotalRedeemed() + points);
            account.setLastRedeemAt(now);
            account.setUpdatedAt(now);
            account = accountRepository.save(account);
            recordTransaction(account, TXN_TYPE_ADJUST, -points, null, null, SOURCE_MANUAL, null,
                    dto.getReason(), operator, null);
            log.info("手动扣减积分: customerId={}, points={}, reason={}", account.getCustomerId(), points, dto.getReason());
        } else {
            throw ScrmException.badRequest("操作类型非法: " + dto.getType() + ", 仅支持 EARN/REDEEM");
        }
        return toAccountDto(account);
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
    @Transactional
    public ScrmPointsAccountDto freezePoints(Long customerId, Integer points, String reason) throws ScrmException {
        if (points == null || points <= 0) {
            throw ScrmException.badRequest("冻结积分数量必须为正数");
        }
        ScrmPointsAccountEntity account = findAccountByCustomerOrThrow(customerId);
        if (account.getCurrentPoints() < points) {
            throw ScrmException.badRequest("积分不足, 当前可用: " + account.getCurrentPoints());
        }
        LocalDateTime now = LocalDateTime.now();
        account.setCurrentPoints(account.getCurrentPoints() - points);
        account.setFrozenPoints(account.getFrozenPoints() + points);
        account.setUpdatedAt(now);
        account = accountRepository.save(account);
        recordTransaction(account, TXN_TYPE_FREEZE, -points, null, null, SOURCE_MANUAL, null,
                "冻结积分: " + reason, DEFAULT_OPERATOR, null);
        log.info("冻结积分: customerId={}, points={}", customerId, points);
        return toAccountDto(account);
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
    @Transactional
    public ScrmPointsAccountDto unfreezePoints(Long customerId,
            Integer points, String reason) throws ScrmException {
        if (points == null || points <= 0) {
            throw ScrmException.badRequest("解冻积分数量必须为正数");
        }
        ScrmPointsAccountEntity account = findAccountByCustomerOrThrow(customerId);
        if (account.getFrozenPoints() < points) {
            throw ScrmException.badRequest("冻结积分不足, 当前冻结: " + account.getFrozenPoints());
        }
        LocalDateTime now = LocalDateTime.now();
        account.setFrozenPoints(account.getFrozenPoints() - points);
        account.setCurrentPoints(account.getCurrentPoints() + points);
        account.setUpdatedAt(now);
        account = accountRepository.save(account);
        recordTransaction(account, TXN_TYPE_UNFREEZE, points, null, null, SOURCE_MANUAL, null,
                "解冻积分: " + reason, DEFAULT_OPERATOR, null);
        log.info("解冻积分: customerId={}, points={}", customerId, points);
        return toAccountDto(account);
    }

    /**
     * 获取或创建积分账户实体 (不存在则初始化为 0 积分)。
     *
     * @param customerId 客户 ID
     * @return 积分账户实体
     */
    ScrmPointsAccountEntity getOrCreateAccountEntity(Long customerId) {
        return accountRepository.findByCustomerId(customerId)
                .orElseGet(() -> createDefaultAccount(customerId));
    }

    /**
     * 按客户锁定并获取积分账户 (不存在则创建), 用于兑换路径的并发扣减防护。
     *
     * @param customerId 客户 ID
     * @return 积分账户实体 (已加悲观写锁)
     */
    ScrmPointsAccountEntity findAccountForUpdateOrCreate(Long customerId) {
        return accountRepository.findByCustomerIdForUpdate(customerId)
                .orElseGet(() -> createDefaultAccount(customerId));
    }

    /**
     * 按客户加悲观写锁查询积分账户, 不存在抛异常 (取消兑换路径使用, 与 exchange 扣减共用同一账户锁)。
     *
     * @param customerId 客户 ID
     * @return 积分账户实体 (已加悲观写锁)
     * @throws ScrmException 账户不存在
     */
    ScrmPointsAccountEntity findAccountForUpdateByCustomerOrThrow(Long customerId) throws ScrmException {
        return accountRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "积分账户不存在: customerId=" + customerId));
    }

    /**
     * 创建初始化为 0 积分的默认账户。
     * <p>使用 {@code saveAndFlush} 立即刷出 INSERT, 使 {@code (customer_id)} 唯一约束冲突
     * 在创建点确定性地抛出。同一客户首次并发创建账户时 (双方均查询不到账户后同时插入), 输方在此
     * 捕获 {@link DataIntegrityViolationException}, 清空持久化上下文丢弃失败插入, 回查胜方已提交的
     * 账户并返回, 保证幂等并发安全。</p>
     *
     * @param customerId 客户 ID
     * @return 创建后的积分账户实体
     * @throws ScrmException 创建冲突且回查仍为空 (极端情况)
     */
    ScrmPointsAccountEntity createDefaultAccount(Long customerId) throws ScrmException {
        ScrmPointsAccountEntity account = new ScrmPointsAccountEntity();
        account.setCustomerId(customerId);
        account.setCurrentPoints(0);
        account.setFrozenPoints(0);
        account.setTotalEarned(0);
        account.setTotalRedeemed(0);
        account.setTotalExpired(0);
        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // 同客户首次并发创建冲突: 清空持久化上下文丢弃失败插入, 同一事务内回查胜方已提交的账户
            entityManager.clear();
            return accountRepository.findByCustomerId(customerId)
                    .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.INTERNAL_ERROR,
                            "积分账户创建冲突且回查为空: customerId=" + customerId, e));
        }
    }

    /**
     * 按客户查询积分账户, 不存在抛异常。
     *
     * @param customerId 客户 ID
     * @return 积分账户实体
     * @throws ScrmException 账户不存在
     */
    ScrmPointsAccountEntity findAccountByCustomerOrThrow(Long customerId) throws ScrmException {
        return accountRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "积分账户不存在: customerId=" + customerId));
    }

    /**
     * 记录积分流水 (内部调用, balanceAfter 取账户当前可用积分)。
     *
     * @param account       积分账户
     * @param type          交易类型
     * @param points        积分变动值 (正/负)
     * @param ruleId        关联规则 ID (可空)
     * @param triggerEvent  触发事件 (可空)
     * @param sourceType    来源
     * @param sourceId      来源 ID (可空)
     * @param description   描述 (可空)
     * @param operator      操作人
     * @param expiresAt     过期时间 (可空, 仅 EARN 类设置)
     * @return 创建后的流水
     */
    ScrmPointsTransactionEntity recordTransaction(ScrmPointsAccountEntity account, String type,
                                                  int points, Long ruleId, String triggerEvent,
                                                  String sourceType, String sourceId,
                                                  String description, String operator,
                                                  LocalDateTime expiresAt) {
        ScrmPointsTransactionEntity txn = new ScrmPointsTransactionEntity();
        txn.setAccountId(account.getId());
        txn.setCustomerId(account.getCustomerId());
        txn.setCustomerName(account.getCustomerName());
        txn.setTransactionType(type);
        txn.setPoints(points);
        txn.setBalanceAfter(account.getCurrentPoints());
        txn.setRuleId(ruleId);
        txn.setTriggerEvent(triggerEvent);
        txn.setSourceType(sourceType);
        txn.setSourceId(sourceId);
        txn.setDescription(description);
        txn.setExpired(false);
        txn.setExpiresAt(expiresAt);
        txn.setCreatedBy(operator);
        return transactionRepository.save(txn);
    }

    /**
     * 账户实体转 DTO。
     */
    ScrmPointsAccountDto toAccountDto(ScrmPointsAccountEntity entity) {
        ScrmPointsAccountDto dto = new ScrmPointsAccountDto();
        dto.setId(entity.getId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setCurrentPoints(entity.getCurrentPoints());
        dto.setFrozenPoints(entity.getFrozenPoints());
        dto.setTotalEarned(entity.getTotalEarned());
        dto.setTotalRedeemed(entity.getTotalRedeemed());
        dto.setTotalExpired(entity.getTotalExpired());
        dto.setLevel(entity.getLevel());
        dto.setLastEarnAt(entity.getLastEarnAt());
        dto.setLastRedeemAt(entity.getLastRedeemAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}