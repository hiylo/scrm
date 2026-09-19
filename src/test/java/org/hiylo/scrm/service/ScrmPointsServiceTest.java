/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmPointsExchangeDto;
import org.hiylo.scrm.dto.ScrmPointsOperationDto;
import org.hiylo.scrm.dto.ScrmPointsRuleDto;
import org.hiylo.scrm.entity.ScrmPointsAccountEntity;
import org.hiylo.scrm.entity.ScrmPointsExchangeEntity;
import org.hiylo.scrm.entity.ScrmPointsExchangeRecordEntity;
import org.hiylo.scrm.entity.ScrmPointsRuleEntity;
import org.hiylo.scrm.entity.ScrmPointsTransactionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmPointsAccountRepository;
import org.hiylo.scrm.repository.ScrmPointsExchangeRecordRepository;
import org.hiylo.scrm.repository.ScrmPointsExchangeRepository;
import org.hiylo.scrm.repository.ScrmPointsRuleRepository;
import org.hiylo.scrm.repository.ScrmPointsTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmPointsService 单元测试
 * <p>
 * 聚焦积分规则管理 / 积分账户调整 (手动加减/冻结) / 规则匹配获取积分 / 积分兑换商品
 * 与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmPointsService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmPointsServiceTest {

    /** 积分规则仓库 Mock */
    @Mock
    private ScrmPointsRuleRepository ruleRepository;
    /** 积分账户仓库 Mock */
    @Mock
    private ScrmPointsAccountRepository accountRepository;
    /** 积分流水仓库 Mock */
    @Mock
    private ScrmPointsTransactionRepository transactionRepository;
    /** 积分兑换商品仓库 Mock */
    @Mock
    private ScrmPointsExchangeRepository exchangeRepository;
    /** 积分兑换记录仓库 Mock */
    @Mock
    private ScrmPointsExchangeRecordRepository exchangeRecordRepository;

    /** JPA EntityManager Mock (createDefaultAccount 并发冲突回查路径) */
    @Mock
    private jakarta.persistence.EntityManager entityManager;

    /** 被测服务实例 */
    private ScrmPointsService service;

    @BeforeEach
    void setUp() {
        ScrmPointsRuleService ruleService = new ScrmPointsRuleService(ruleRepository);
        ScrmPointsAccountService accountService = new ScrmPointsAccountService(accountRepository,
                transactionRepository, entityManager);
        ScrmPointsTransactionService transactionService = new ScrmPointsTransactionService(ruleRepository,
                accountRepository, transactionRepository, accountService);
        ScrmPointsExchangeService exchangeService = new ScrmPointsExchangeService(exchangeRepository,
                exchangeRecordRepository, accountRepository, accountService);
        ScrmPointsStatsService statsService = new ScrmPointsStatsService(accountRepository,
                exchangeRecordRepository, accountService);
        service = new ScrmPointsService(ruleService, accountService, transactionService, exchangeService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的积分规则实体 (用于 findById 返回)
     */
    private ScrmPointsRuleEntity buildRuleEntity(Long id, String ruleType, String triggerEvent) {
        ScrmPointsRuleEntity entity = new ScrmPointsRuleEntity();
        entity.setId(id);
        entity.setRuleName("下单获取积分");
        entity.setRuleType(ruleType);
        entity.setTriggerEvent(triggerEvent);
        entity.setPointsValue(10);
        entity.setPointsType("FIXED");
        entity.setEnabled(true);
        entity.setTriggerCount(0);
        return entity;
    }

    /**
     * 构造已持久化的积分账户实体
     */
    private ScrmPointsAccountEntity buildAccountEntity(Long id, Long customerId, int currentPoints) {
        ScrmPointsAccountEntity entity = new ScrmPointsAccountEntity();
        entity.setId(id);
        entity.setCustomerId(customerId);
        entity.setCustomerName("张三");
        entity.setCurrentPoints(currentPoints);
        entity.setFrozenPoints(0);
        entity.setTotalEarned(currentPoints);
        entity.setTotalRedeemed(0);
        entity.setTotalExpired(0);
        return entity;
    }

    /**
     * 构造已持久化的兑换商品实体
     */
    private ScrmPointsExchangeEntity buildExchangeEntity(Long id, String status, int stock, int pointsRequired) {
        ScrmPointsExchangeEntity entity = new ScrmPointsExchangeEntity();
        entity.setId(id);
        entity.setItemName("积分兑换券");
        entity.setPointsRequired(pointsRequired);
        entity.setStockQuantity(stock);
        entity.setExchangedQuantity(0);
        entity.setPerUserLimit(1);
        entity.setExchangeType("COUPON");
        entity.setStatus(status);
        return entity;
    }

    @Test
    @DisplayName("createRule: 写入账号 ID 与默认值后持久化")
    void createRule_success() throws ScrmException {
        ScrmPointsRuleDto dto = new ScrmPointsRuleDto();
        dto.setRuleName("下单获取积分");
        dto.setRuleType("EARN");
        dto.setTriggerEvent("PURCHASE");
        dto.setPointsValue(10);
        when(ruleRepository.save(any(ScrmPointsRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmPointsRuleDto result = service.createRule(dto);

        ArgumentCaptor<ScrmPointsRuleEntity> captor =
                ArgumentCaptor.forClass(ScrmPointsRuleEntity.class);
        verify(ruleRepository, times(1)).save(captor.capture());
        ScrmPointsRuleEntity saved = captor.getValue();
        assertThat(saved.getPointsType()).isEqualTo("FIXED");
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getMinPoints()).isZero();
        assertThat(saved.getTriggerCount()).isZero();
        assertThat(result.getRuleName()).isEqualTo("下单获取积分");
    }

    @Test
    @DisplayName("createRule: 规则类型非法时抛 BAD_REQUEST")
    void createRule_invalidRuleType() {
        ScrmPointsRuleDto dto = new ScrmPointsRuleDto();
        dto.setRuleName("非法规则");
        dto.setRuleType("INVALID");
        dto.setTriggerEvent("PURCHASE");
        dto.setPointsValue(10);

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("规则类型非法");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustPoints: EARN 类型增加积分并记录 ADJUST 流水")
    void adjustPoints_earn() throws ScrmException {
        ScrmPointsAccountEntity account = buildAccountEntity(50L, 100L, 100);
        when(accountRepository.findByCustomerId(100L))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(any(ScrmPointsAccountEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(ScrmPointsTransactionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmPointsOperationDto dto = new ScrmPointsOperationDto();
        dto.setCustomerId(100L);
        dto.setPoints(50);
        dto.setType("EARN");
        dto.setReason("客服补发");
        dto.setOperator("admin");

        var result = service.adjustPoints(dto);

        ArgumentCaptor<ScrmPointsAccountEntity> accountCaptor =
                ArgumentCaptor.forClass(ScrmPointsAccountEntity.class);
        verify(accountRepository, times(1)).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getCurrentPoints()).isEqualTo(150);
        assertThat(accountCaptor.getValue().getTotalEarned()).isEqualTo(150);
        assertThat(accountCaptor.getValue().getLastEarnAt()).isNotNull();
        ArgumentCaptor<ScrmPointsTransactionEntity> txnCaptor =
                ArgumentCaptor.forClass(ScrmPointsTransactionEntity.class);
        verify(transactionRepository, times(1)).save(txnCaptor.capture());
        assertThat(txnCaptor.getValue().getTransactionType()).isEqualTo("ADJUST");
        assertThat(txnCaptor.getValue().getPoints()).isEqualTo(50);
        assertThat(txnCaptor.getValue().getSourceType()).isEqualTo("MANUAL");
        assertThat(result.getCurrentPoints()).isEqualTo(150);
    }

    @Test
    @DisplayName("adjustPoints: REDEEM 类型积分不足时抛 BAD_REQUEST")
    void adjustPoints_redeemInsufficient() {
        ScrmPointsAccountEntity account = buildAccountEntity(50L, 100L, 30);
        when(accountRepository.findByCustomerId(100L))
                .thenReturn(Optional.of(account));

        ScrmPointsOperationDto dto = new ScrmPointsOperationDto();
        dto.setCustomerId(100L);
        dto.setPoints(50);
        dto.setType("REDEEM");
        dto.setReason("扣减");

        assertThatThrownBy(() -> service.adjustPoints(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("积分不足");
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("freezePoints: 从可用积分转入冻结积分并记录 FREEZE 流水")
    void freezePoints_success() throws ScrmException {
        ScrmPointsAccountEntity account = buildAccountEntity(50L, 100L, 100);
        when(accountRepository.findByCustomerId(100L))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(any(ScrmPointsAccountEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(ScrmPointsTransactionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.freezePoints(100L, 30, "风控冻结");

        ArgumentCaptor<ScrmPointsAccountEntity> captor =
                ArgumentCaptor.forClass(ScrmPointsAccountEntity.class);
        verify(accountRepository, times(1)).save(captor.capture());
        ScrmPointsAccountEntity saved = captor.getValue();
        assertThat(saved.getCurrentPoints()).isEqualTo(70);
        assertThat(saved.getFrozenPoints()).isEqualTo(30);
        ArgumentCaptor<ScrmPointsTransactionEntity> txnCaptor =
                ArgumentCaptor.forClass(ScrmPointsTransactionEntity.class);
        verify(transactionRepository, times(1)).save(txnCaptor.capture());
        assertThat(txnCaptor.getValue().getTransactionType()).isEqualTo("FREEZE");
        assertThat(txnCaptor.getValue().getPoints()).isEqualTo(-30);
        assertThat(result.getFrozenPoints()).isEqualTo(30);
    }

    @Test
    @DisplayName("redeemPoints: 账户不存在时抛 NOT_FOUND")
    void redeemPoints_accountNotFound() {
        when(accountRepository.findByCustomerId(100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeemPoints(100L, 50, "消耗"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("积分账户不存在");
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("earnPoints: FIXED 规则匹配后累计积分并记录 EARN 流水")
    void earnPoints_fixedRule() throws ScrmException {
        ScrmPointsAccountEntity account = buildAccountEntity(50L, 100L, 0);
        ScrmPointsRuleEntity rule = buildRuleEntity(10L, "EARN", "PURCHASE");
        rule.setPointsValue(20);
        when(accountRepository.findByCustomerId(100L))
                .thenReturn(Optional.of(account));
        when(ruleRepository.findByTriggerEventAndRuleTypeAndEnabledTrue("PURCHASE", "EARN"))
                .thenReturn(List.of(rule));
        when(accountRepository.save(any(ScrmPointsAccountEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(ScrmPointsTransactionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ruleRepository.incrementTriggerCount(10L)).thenReturn(1);

        var result = service.earnPoints(100L, "PURCHASE", null);

        ArgumentCaptor<ScrmPointsAccountEntity> accountCaptor =
                ArgumentCaptor.forClass(ScrmPointsAccountEntity.class);
        verify(accountRepository, times(1)).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getCurrentPoints()).isEqualTo(20);
        assertThat(accountCaptor.getValue().getTotalEarned()).isEqualTo(20);
        ArgumentCaptor<ScrmPointsTransactionEntity> txnCaptor =
                ArgumentCaptor.forClass(ScrmPointsTransactionEntity.class);
        verify(transactionRepository, times(1)).save(txnCaptor.capture());
        assertThat(txnCaptor.getValue().getTransactionType()).isEqualTo("EARN");
        assertThat(txnCaptor.getValue().getPoints()).isEqualTo(20);
        assertThat(txnCaptor.getValue().getRuleId()).isEqualTo(10L);
        assertThat(txnCaptor.getValue().getExpiresAt()).isNotNull();
        verify(ruleRepository, times(1)).incrementTriggerCount(10L);
        assertThat(result.getCurrentPoints()).isEqualTo(20);
    }

    @Test
    @DisplayName("earnPoints: 无匹配规则时不更新账户也不记录流水")
    void earnPoints_noMatchingRule() throws ScrmException {
        ScrmPointsAccountEntity account = buildAccountEntity(50L, 100L, 0);
        when(accountRepository.findByCustomerId(100L))
                .thenReturn(Optional.of(account));
        when(ruleRepository.findByTriggerEventAndRuleTypeAndEnabledTrue("PURCHASE", "EARN"))
                .thenReturn(Collections.emptyList());

        var result = service.earnPoints(100L, "PURCHASE", null);

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        assertThat(result.getCurrentPoints()).isZero();
    }

    @Test
    @DisplayName("exchange: 成功兑换扣减积分、扣减库存并生成兑换码")
    void exchange_success() throws ScrmException {
        ScrmPointsExchangeEntity item = buildExchangeEntity(10L, "ACTIVE", 5, 50);
        ScrmPointsAccountEntity account = buildAccountEntity(50L, 100L, 100);
        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(item));
        when(exchangeRepository.deductStock(10L, 1)).thenReturn(1);
        when(accountRepository.findByCustomerIdForUpdate(100L))
                .thenReturn(Optional.of(account));
        when(exchangeRecordRepository.countByExchangeIdAndCustomerIdAndStatusNot(10L, 100L, "CANCELLED")).thenReturn(0L);
        when(accountRepository.save(any(ScrmPointsAccountEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(exchangeRepository.save(any(ScrmPointsExchangeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(exchangeRecordRepository.save(any(ScrmPointsExchangeRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(ScrmPointsTransactionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.exchange(100L, 10L, 1);

        ArgumentCaptor<ScrmPointsAccountEntity> accountCaptor =
                ArgumentCaptor.forClass(ScrmPointsAccountEntity.class);
        verify(accountRepository, times(1)).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getCurrentPoints()).isEqualTo(50);
        assertThat(accountCaptor.getValue().getTotalRedeemed()).isEqualTo(50);
        ArgumentCaptor<ScrmPointsExchangeEntity> itemCaptor =
                ArgumentCaptor.forClass(ScrmPointsExchangeEntity.class);
        verify(exchangeRepository, times(1)).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getStockQuantity()).isEqualTo(4);
        assertThat(itemCaptor.getValue().getExchangedQuantity()).isEqualTo(1);
        ArgumentCaptor<ScrmPointsExchangeRecordEntity> recordCaptor =
                ArgumentCaptor.forClass(ScrmPointsExchangeRecordEntity.class);
        verify(exchangeRecordRepository, times(1)).save(recordCaptor.capture());
        ScrmPointsExchangeRecordEntity savedRecord = recordCaptor.getValue();
        assertThat(savedRecord.getExchangeId()).isEqualTo(10L);
        assertThat(savedRecord.getCustomerId()).isEqualTo(100L);
        assertThat(savedRecord.getPointsCost()).isEqualTo(50);
        assertThat(savedRecord.getQuantity()).isEqualTo(1);
        assertThat(savedRecord.getExchangeCode()).startsWith("EX");
        assertThat(savedRecord.getStatus()).isEqualTo("PENDING");
        assertThat(result.getExchangeCode()).startsWith("EX");
    }

    @Test
    @DisplayName("exchange: 商品非 ACTIVE 状态时抛 BAD_REQUEST")
    void exchange_itemNotActive() {
        ScrmPointsExchangeEntity item = buildExchangeEntity(10L, "INACTIVE", 5, 50);
        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.exchange(100L, 10L, 1))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("商品不可兑换");
        verify(accountRepository, never()).save(any());
        verify(exchangeRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("createExchangeItem: 写入账号 ID 与默认值后持久化")
    void createExchangeItem_success() throws ScrmException {
        ScrmPointsExchangeDto dto = new ScrmPointsExchangeDto();
        dto.setItemName("10元优惠券");
        dto.setItemCategory("COUPON");
        dto.setPointsRequired(100);
        dto.setStockQuantity(50);
        dto.setExchangeType("COUPON");
        when(exchangeRepository.save(any(ScrmPointsExchangeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.createExchangeItem(dto);

        ArgumentCaptor<ScrmPointsExchangeEntity> captor =
                ArgumentCaptor.forClass(ScrmPointsExchangeEntity.class);
        verify(exchangeRepository, times(1)).save(captor.capture());
        ScrmPointsExchangeEntity saved = captor.getValue();
        assertThat(saved.getExchangedQuantity()).isZero();
        assertThat(saved.getPerUserLimit()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getItemName()).isEqualTo("10元优惠券");
    }

    
}
