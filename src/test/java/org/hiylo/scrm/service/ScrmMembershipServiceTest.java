/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmMembershipEnrollDto;
import org.hiylo.scrm.dto.ScrmMembershipTierDto;
import org.hiylo.scrm.dto.ScrmMembershipUpgradeDto;
import org.hiylo.scrm.entity.ScrmCustomerMembershipEntity;
import org.hiylo.scrm.entity.ScrmMembershipTierEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerMembershipRepository;
import org.hiylo.scrm.repository.ScrmMembershipBenefitRepository;
import org.hiylo.scrm.repository.ScrmMembershipTierRepository;
import org.springframework.data.jpa.domain.Specification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmMembershipService 单元测试
 * <p>
 * 聚焦会员等级管理 / 会员档案生命周期 (注册/升级/冻结) 与越权访问校验。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmMembershipService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmMembershipServiceTest {

    /** 会员等级仓库 Mock */
    @Mock
    private ScrmMembershipTierRepository tierRepository;
    /** 客户会员档案仓库 Mock */
    @Mock
    private ScrmCustomerMembershipRepository membershipRepository;
    /** 会员权益仓库 Mock */
    @Mock
    private ScrmMembershipBenefitRepository benefitRepository;

    /** 被测服务实例 */
    private ScrmMembershipService service;

    @BeforeEach
    void setUp() {
        ScrmMembershipTierService tierService =
                new ScrmMembershipTierService(tierRepository, membershipRepository);
        ScrmMembershipMemberService memberService =
                new ScrmMembershipMemberService(tierService, membershipRepository, tierRepository);
        ScrmMembershipBenefitService benefitService =
                new ScrmMembershipBenefitService(tierService, benefitRepository, membershipRepository);
        ScrmMembershipStatsService statsService =
                new ScrmMembershipStatsService(tierRepository, membershipRepository, benefitRepository);
        service = new ScrmMembershipService(tierService, memberService, benefitService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造等级实体 (用于 findById 返回)
     */
    private ScrmMembershipTierEntity buildTierEntity(Long id, Integer level) {
        ScrmMembershipTierEntity entity = new ScrmMembershipTierEntity();
        entity.setId(id);
        entity.setTierName("V" + level);
        entity.setTierCode("TIER-" + level);
        entity.setTierLevel(level);
        entity.setUpgradeThreshold(0d);
        entity.setDowngradeThreshold(0d);
        entity.setValidityPeriodMonths(12);
        entity.setUpgradeRuleType("SPEND");
        entity.setPointMultiplier(1.0);
        entity.setDiscountRate(1.0);
        entity.setEnabled(Boolean.TRUE);
        entity.setIsVisible(Boolean.TRUE);
        entity.setSignupBonusPoints(0);
        entity.setTierUpBonusPoints(0);
        return entity;
    }

    /**
     * 构造已持久化的会员实体
     */
    private ScrmCustomerMembershipEntity buildMembershipEntity(Long id, Long tierId, Integer tierLevel) {
        ScrmCustomerMembershipEntity entity = new ScrmCustomerMembershipEntity();
        entity.setId(id);
        entity.setCustomerId(100L);
        entity.setTierId(tierId);
        entity.setTierLevel(tierLevel);
        entity.setTierName("V" + tierLevel);
        entity.setMemberCardNo("VM20260805000001");
        entity.setMemberCardType("STANDARD");
        entity.setMembershipStatus("ACTIVE");
        entity.setJoinDate(LocalDate.now());
        entity.setCurrentTierDate(LocalDate.now());
        entity.setTotalSpend(0d);
        entity.setTotalOrders(0);
        entity.setTotalPoints(0);
        entity.setAvailablePoints(0);
        entity.setSpendInPeriod(0d);
        entity.setOrdersInPeriod(0);
        return entity;
    }

    @Test
    @DisplayName("createTier: 写入账号 ID 与默认值后持久化")
    void createTier_success() throws ScrmException {
        ScrmMembershipTierDto dto = new ScrmMembershipTierDto();
        dto.setTierName("银卡");
        dto.setTierCode("TIER-SILVER");
        dto.setTierLevel(2);
        when(tierRepository.findByTierCode("TIER-SILVER")).thenReturn(Optional.empty());
        when(tierRepository.findByTierLevel(2)).thenReturn(Optional.empty());
        when(tierRepository.save(any(ScrmMembershipTierEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMembershipTierDto result = service.createTier(dto);

        ArgumentCaptor<ScrmMembershipTierEntity> captor =
                ArgumentCaptor.forClass(ScrmMembershipTierEntity.class);
        verify(tierRepository, times(1)).save(captor.capture());
        ScrmMembershipTierEntity saved = captor.getValue();
        assertThat(saved.getUpgradeThreshold()).isZero();
        assertThat(saved.getUpgradeRuleType()).isEqualTo("SPEND");
        assertThat(saved.getValidityPeriodMonths()).isEqualTo(12);
        assertThat(saved.getPointMultiplier()).isEqualTo(1.0);
        assertThat(saved.getDiscountRate()).isEqualTo(1.0);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getIsVisible()).isTrue();
        assertThat(saved.getMemberCount()).isZero();
        assertThat(saved.getTotalSpend()).isZero();
        assertThat(result.getTierCode()).isEqualTo("TIER-SILVER");
    }

    @Test
    @DisplayName("createTier: 等级编码重复时抛 CONFLICT")
    void createTier_duplicateCode() {
        ScrmMembershipTierDto dto = new ScrmMembershipTierDto();
        dto.setTierName("银卡");
        dto.setTierCode("TIER-DUP");
        dto.setTierLevel(2);
        when(tierRepository.findByTierCode("TIER-DUP"))
                .thenReturn(Optional.of(buildTierEntity(99L, 2)));

        assertThatThrownBy(() -> service.createTier(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("等级编码已存在");
        verify(tierRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTier: 等级序号越界 (1-10) 抛 BAD_REQUEST")
    void createTier_levelOutOfRange() {
        ScrmMembershipTierDto dto = new ScrmMembershipTierDto();
        dto.setTierName("测试等级");
        dto.setTierCode("TIER-OOR");
        dto.setTierLevel(11);

        assertThatThrownBy(() -> service.createTier(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("等级序号需在 1-10 之间");
        verify(tierRepository, never()).save(any());
    }

    @Test
    @DisplayName("enroll: 客户已注册会员时抛 CONFLICT")
    void enroll_customerAlreadyEnrolled() {
        ScrmMembershipEnrollDto dto = new ScrmMembershipEnrollDto();
        dto.setCustomerId(100L);
        dto.setTierId(10L);
        when(membershipRepository.findByCustomerId(100L))
                .thenReturn(Optional.of(buildMembershipEntity(50L, 10L, 1)));

        assertThatThrownBy(() -> service.enroll(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户已注册会员");
        verify(membershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("enroll: 注册成功生成卡号并设置初始等级与状态")
    void enroll_success() throws ScrmException {
        ScrmMembershipEnrollDto dto = new ScrmMembershipEnrollDto();
        dto.setCustomerId(100L);
        dto.setTierId(10L);
        dto.setCustomerName("张三");
        ScrmMembershipTierEntity tier = buildTierEntity(10L, 1);
        tier.setSignupBonusPoints(100);
        when(membershipRepository.findByCustomerId(100L))
                .thenReturn(Optional.empty());
        when(tierRepository.findById(10L)).thenReturn(Optional.of(tier));
        when(membershipRepository.save(any(ScrmCustomerMembershipEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // updateTierStats 调用链
        when(membershipRepository.countByTierId(eq(10L))).thenReturn(1L);
        when(membershipRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());
        when(tierRepository.save(any(ScrmMembershipTierEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // populateNextTierInfo 调用
        when(tierRepository.findByTierLevelGreaterThanOrderByTierLevelAsc(eq(1)))
                .thenReturn(Collections.emptyList());

        var result = service.enroll(dto);

        ArgumentCaptor<ScrmCustomerMembershipEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerMembershipEntity.class);
        verify(membershipRepository, times(1)).save(captor.capture());
        ScrmCustomerMembershipEntity saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo(100L);
        assertThat(saved.getTierId()).isEqualTo(10L);
        assertThat(saved.getTierLevel()).isEqualTo(1);
        assertThat(saved.getMemberCardNo()).startsWith("VM");
        assertThat(saved.getMembershipStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getMemberCardType()).isEqualTo("STANDARD");
        assertThat(saved.getJoinDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getTierExpiryDate()).isNotNull();
        assertThat(saved.getTotalPoints()).isEqualTo(100);
        assertThat(saved.getAvailablePoints()).isEqualTo(100);
        assertThat(saved.getDowngradeRisk()).isFalse();
        assertThat(result.getMemberCardNo()).startsWith("VM");
    }

    @Test
    @DisplayName("upgrade: 目标等级序号不高于当前等级抛 BAD_REQUEST")
    void upgrade_targetNotHigher() {
        ScrmCustomerMembershipEntity membership = buildMembershipEntity(50L, 10L, 3);
        when(membershipRepository.findById(50L)).thenReturn(Optional.of(membership));
        ScrmMembershipTierEntity targetTier = buildTierEntity(11L, 2);
        when(tierRepository.findById(11L)).thenReturn(Optional.of(targetTier));

        ScrmMembershipUpgradeDto dto = new ScrmMembershipUpgradeDto();
        dto.setMembershipId(50L);
        dto.setTargetTierId(11L);

        assertThatThrownBy(() -> service.upgrade(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("目标等级序号不高于当前等级");
        verify(membershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("upgrade: 成功升级等级并赠送升级积分")
    void upgrade_success() throws ScrmException {
        ScrmCustomerMembershipEntity membership = buildMembershipEntity(50L, 10L, 1);
        membership.setTotalPoints(100);
        membership.setAvailablePoints(100);
        when(membershipRepository.findById(50L)).thenReturn(Optional.of(membership));
        ScrmMembershipTierEntity fromTier = buildTierEntity(10L, 1);
        when(tierRepository.findById(10L)).thenReturn(Optional.of(fromTier));
        ScrmMembershipTierEntity targetTier = buildTierEntity(11L, 2);
        targetTier.setTierUpBonusPoints(200);
        when(tierRepository.findById(11L)).thenReturn(Optional.of(targetTier));
        when(membershipRepository.save(any(ScrmCustomerMembershipEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // updateTierStats 调用链 (会被调用两次: fromTier 10L 与 targetTier 11L)
        when(membershipRepository.countByTierId(any(Long.class))).thenReturn(1L);
        when(membershipRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());
        when(tierRepository.save(any(ScrmMembershipTierEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // populateNextTierInfo: 等级 2 已是最高
        when(tierRepository.findByTierLevelGreaterThanOrderByTierLevelAsc(eq(2)))
                .thenReturn(Collections.emptyList());

        ScrmMembershipUpgradeDto dto = new ScrmMembershipUpgradeDto();
        dto.setMembershipId(50L);
        dto.setTargetTierId(11L);
        dto.setReason("消费达标");

        var result = service.upgrade(dto);

        ArgumentCaptor<ScrmCustomerMembershipEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerMembershipEntity.class);
        verify(membershipRepository, times(1)).save(captor.capture());
        ScrmCustomerMembershipEntity saved = captor.getValue();
        assertThat(saved.getTierId()).isEqualTo(11L);
        assertThat(saved.getTierLevel()).isEqualTo(2);
        assertThat(saved.getTierName()).isEqualTo("V2");
        // 100 (原) + 200 (升级赠送) = 300
        assertThat(saved.getTotalPoints()).isEqualTo(300);
        assertThat(saved.getAvailablePoints()).isEqualTo(300);
        assertThat(saved.getLastUpgradeDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getCurrentTierDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getSpendInPeriod()).isZero();
        assertThat(saved.getDowngradeRisk()).isFalse();
        assertThat(saved.getUpgradeHistory()).contains("UPGRADE");
        assertThat(result.getTierLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("freeze: 已冻结会员再次冻结抛 BAD_REQUEST")
    void freeze_alreadyFrozen() {
        ScrmCustomerMembershipEntity membership = buildMembershipEntity(50L, 10L, 1);
        membership.setMembershipStatus("FROZEN");
        when(membershipRepository.findById(50L)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.freeze(50L, "重复冻结"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("会员已冻结");
        verify(membershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("freeze: 活跃会员冻结后状态置 FROZEN")
    void freeze_success() throws ScrmException {
        ScrmCustomerMembershipEntity membership = buildMembershipEntity(50L, 10L, 1);
        when(membershipRepository.findById(50L)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any(ScrmCustomerMembershipEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.freeze(50L, "违规操作");

        ArgumentCaptor<ScrmCustomerMembershipEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerMembershipEntity.class);
        verify(membershipRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getMembershipStatus()).isEqualTo("FROZEN");
        assertThat(captor.getValue().getNotes()).isEqualTo("违规操作");
        assertThat(result.getMembershipStatus()).isEqualTo("FROZEN");
    }

    
}
