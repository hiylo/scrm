/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmReferralCreateDto;
import org.hiylo.scrm.dto.ScrmReferralProgramDto;
import org.hiylo.scrm.dto.ScrmReferralQualifyDto;
import org.hiylo.scrm.entity.ScrmReferralEntity;
import org.hiylo.scrm.entity.ScrmReferralProgramEntity;
import org.hiylo.scrm.entity.ScrmReferralRewardEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmReferralProgramRepository;
import org.hiylo.scrm.repository.ScrmReferralRepository;
import org.hiylo.scrm.repository.ScrmReferralRewardRepository;
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
 * ScrmReferralService 单元测试
 * <p>
 * 聚焦推荐活动创建 / 推荐关系生命周期 / 奖励发放的关键业务逻辑与越权访问校验。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmReferralService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmReferralServiceTest {

    /** 推荐活动仓库 Mock */
    @Mock
    private ScrmReferralProgramRepository programRepository;
    /** 推荐关系仓库 Mock */
    @Mock
    private ScrmReferralRepository referralRepository;
    /** 推荐奖励仓库 Mock */
    @Mock
    private ScrmReferralRewardRepository rewardRepository;

    /** 被测服务实例 */
    private ScrmReferralService service;

    @BeforeEach
    void setUp() {
        ScrmReferralProgramService programService =
                new ScrmReferralProgramService(programRepository, referralRepository, rewardRepository);
        ScrmReferralRelationshipService relationshipService =
                new ScrmReferralRelationshipService(referralRepository, programService);
        ScrmReferralRewardService rewardService =
                new ScrmReferralRewardService(rewardRepository, referralRepository, programService,
                        relationshipService);
        ScrmReferralStatsService statsService =
                new ScrmReferralStatsService(referralRepository, rewardRepository, programService);
        service = new ScrmReferralService(programService, relationshipService, rewardService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的活动实体 (用于 findById 返回)
     */
    private ScrmReferralProgramEntity buildProgramEntity(Long id, String status) {
        ScrmReferralProgramEntity entity = new ScrmReferralProgramEntity();
        entity.setId(id);
        entity.setProgramName("老带新活动");
        entity.setProgramCode("PROG-" + id);
        entity.setProgramType("REFERRAL");
        entity.setReferrerRewardType("POINTS");
        entity.setReferrerRewardValue(50d);
        entity.setRefereeRewardType("COUPON");
        entity.setRefereeRewardValue(20d);
        entity.setRewardTrigger("SIGNUP");
        entity.setMaxReferralsPerReferrer(0);
        entity.setMaxReferralsTotal(0);
        entity.setDoubleSidedReward(Boolean.TRUE);
        entity.setStartDate(LocalDate.now().minusDays(1));
        entity.setStatus(status);
        entity.setTotalReferrals(0);
        entity.setSuccessfulReferrals(0);
        entity.setTotalRewardValue(0d);
        return entity;
    }

    @Test
    @DisplayName("createProgram: 写入账号 ID 并填充默认值后持久化")
    void createProgram_success() throws ScrmException {
        ScrmReferralProgramDto dto = new ScrmReferralProgramDto();
        dto.setProgramName("老带新活动");
        dto.setProgramCode("PROG-NEW");
        dto.setProgramType("REFERRAL");
        dto.setReferrerRewardType("POINTS");
        dto.setReferrerRewardValue(50d);
        dto.setRefereeRewardType("COUPON");
        dto.setRefereeRewardValue(20d);
        dto.setStartDate(LocalDate.now());
        when(programRepository.findByProgramCode("PROG-NEW")).thenReturn(Optional.empty());
        when(programRepository.save(any(ScrmReferralProgramEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmReferralProgramDto result = service.createProgram(dto);

        ArgumentCaptor<ScrmReferralProgramEntity> captor =
                ArgumentCaptor.forClass(ScrmReferralProgramEntity.class);
        verify(programRepository, times(1)).save(captor.capture());
        ScrmReferralProgramEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getRewardTrigger()).isEqualTo("SIGNUP");
        assertThat(saved.getDoubleSidedReward()).isTrue();
        assertThat(saved.getTotalReferrals()).isZero();
        assertThat(saved.getTotalRewardValue()).isZero();
        assertThat(result.getProgramCode()).isEqualTo("PROG-NEW");
    }

    @Test
    @DisplayName("createProgram: 活动编码重复时抛 CONFLICT")
    void createProgram_duplicateCode() {
        ScrmReferralProgramDto dto = new ScrmReferralProgramDto();
        dto.setProgramName("老带新活动");
        dto.setProgramCode("PROG-DUP");
        dto.setProgramType("REFERRAL");
        dto.setReferrerRewardType("POINTS");
        dto.setReferrerRewardValue(10d);
        dto.setRefereeRewardType("POINTS");
        dto.setRefereeRewardValue(10d);
        dto.setStartDate(LocalDate.now());
        when(programRepository.findByProgramCode("PROG-DUP"))
                .thenReturn(Optional.of(buildProgramEntity(99L, "ACTIVE")));

        assertThatThrownBy(() -> service.createProgram(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("活动编码已存在");
        verify(programRepository, never()).save(any());
    }

    @Test
    @DisplayName("createReferral: 活动非活跃时拒绝创建推荐")
    void createReferral_programNotActive() {
        ScrmReferralProgramEntity program = buildProgramEntity(10L, "PAUSED");
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        ScrmReferralCreateDto dto = new ScrmReferralCreateDto();
        dto.setProgramId(10L);
        dto.setReferrerCustomerId(100L);

        assertThatThrownBy(() -> service.createReferral(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("推荐活动非活跃状态");
        verify(referralRepository, never()).save(any());
    }

    @Test
    @DisplayName("createReferral: 活跃活动生成推荐码并持久化推荐记录")
    void createReferral_success() throws ScrmException {
        ScrmReferralProgramEntity program = buildProgramEntity(10L, "ACTIVE");
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        when(referralRepository.findByReferralCode(any(String.class))).thenReturn(Optional.empty());
        when(referralRepository.save(any(ScrmReferralEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // updateProgramStats 调用链
        when(referralRepository.countByProgramId(eq(10L))).thenReturn(1L);
        when(referralRepository.countByProgramIdAndStatusIn(eq(10L), any()))
                .thenReturn(0L);
        when(rewardRepository.findAll(any(Specification.class))).thenReturn(java.util.Collections.emptyList());
        when(programRepository.save(any(ScrmReferralProgramEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmReferralCreateDto dto = new ScrmReferralCreateDto();
        dto.setProgramId(10L);
        dto.setReferrerCustomerId(100L);
        dto.setReferrerName("张三");

        var result = service.createReferral(dto);

        ArgumentCaptor<ScrmReferralEntity> captor =
                ArgumentCaptor.forClass(ScrmReferralEntity.class);
        verify(referralRepository, times(1)).save(captor.capture());
        ScrmReferralEntity saved = captor.getValue();
        assertThat(saved.getProgramId()).isEqualTo(10L);
        assertThat(saved.getReferrerCustomerId()).isEqualTo(100L);
        assertThat(saved.getReferralCode()).startsWith("RF");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getReferrerRewardStatus()).isEqualTo("PENDING");
        assertThat(saved.getReferralChannel()).isEqualTo("CODE");
        assertThat(result.getReferralCode()).startsWith("RF");
    }

    @Test
    @DisplayName("signUpReferral: SIGNUP 触发条件自动达标, 状态置 QUALIFIED")
    void signUpReferral_autoQualify() throws ScrmException {
        ScrmReferralEntity referral = new ScrmReferralEntity();
        referral.setId(50L);
        referral.setProgramId(10L);
        referral.setReferralCode("RFABC123");
        referral.setStatus("PENDING");
        when(referralRepository.findByReferralCode("RFABC123")).thenReturn(Optional.of(referral));
        ScrmReferralProgramEntity program = buildProgramEntity(10L, "ACTIVE");
        program.setRewardTrigger("SIGNUP");
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        when(referralRepository.save(any(ScrmReferralEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(referralRepository.countByProgramId(eq(10L))).thenReturn(1L);
        when(referralRepository.countByProgramIdAndStatusIn(eq(10L), any()))
                .thenReturn(1L);
        when(rewardRepository.findAll(any(Specification.class))).thenReturn(java.util.Collections.emptyList());
        when(programRepository.save(any(ScrmReferralProgramEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.signUpReferral("RFABC123", 200L);

        ArgumentCaptor<ScrmReferralEntity> captor =
                ArgumentCaptor.forClass(ScrmReferralEntity.class);
        verify(referralRepository, times(1)).save(captor.capture());
        ScrmReferralEntity saved = captor.getValue();
        assertThat(saved.getRefereeCustomerId()).isEqualTo(200L);
        assertThat(saved.getSignedUpAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo("QUALIFIED");
        assertThat(saved.getQualifiedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("QUALIFIED");
    }

    @Test
    @DisplayName("qualifyReferral: FIRST_PURCHASE 触发条件金额<=0 抛 BAD_REQUEST")
    void qualifyReferral_firstPurchaseAmountNotMet() {
        ScrmReferralEntity referral = new ScrmReferralEntity();
        referral.setId(50L);
        referral.setProgramId(10L);
        referral.setStatus("SIGNED_UP");
        when(referralRepository.findById(50L)).thenReturn(Optional.of(referral));
        ScrmReferralProgramEntity program = buildProgramEntity(10L, "ACTIVE");
        program.setRewardTrigger("FIRST_PURCHASE");
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));

        ScrmReferralQualifyDto dto = new ScrmReferralQualifyDto();
        dto.setReferralId(50L);
        dto.setPurchaseAmount(0d);

        assertThatThrownBy(() -> service.qualifyReferral(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("首单触发条件未满足");
        verify(referralRepository, never()).save(any());
    }

    @Test
    @DisplayName("issueReward: 推荐未达标时拒绝发放奖励")
    void issueReward_referralNotQualified() {
        ScrmReferralEntity referral = new ScrmReferralEntity();
        referral.setId(50L);
        referral.setProgramId(10L);
        referral.setStatus("SIGNED_UP");
        when(referralRepository.findById(50L)).thenReturn(Optional.of(referral));
        ScrmReferralProgramEntity program = buildProgramEntity(10L, "ACTIVE");
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> service.issueReward(50L, "REFERRER"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("推荐尚未达标");
        verify(rewardRepository, never()).save(any());
    }

    @Test
    @DisplayName("issueReward: REFERRER 奖励发放后写入奖励记录与状态")
    void issueReward_success() throws ScrmException {
        ScrmReferralEntity referral = new ScrmReferralEntity();
        referral.setId(50L);
        referral.setProgramId(10L);
        referral.setReferrerCustomerId(100L);
        referral.setReferrerName("张三");
        referral.setStatus("QUALIFIED");
        referral.setReferrerRewardStatus("PENDING");
        referral.setRefereeRewardStatus("PENDING");
        when(referralRepository.findById(50L)).thenReturn(Optional.of(referral));
        ScrmReferralProgramEntity program = buildProgramEntity(10L, "ACTIVE");
        program.setDoubleSidedReward(Boolean.FALSE);
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        when(rewardRepository.save(any(ScrmReferralRewardEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(referralRepository.save(any(ScrmReferralEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(referralRepository.countByProgramId(eq(10L))).thenReturn(1L);
        when(referralRepository.countByProgramIdAndStatusIn(eq(10L), any()))
                .thenReturn(1L);
        when(rewardRepository.findAll(any(Specification.class))).thenReturn(java.util.Collections.emptyList());
        when(programRepository.save(any(ScrmReferralProgramEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.issueReward(50L, "REFERRER");

        ArgumentCaptor<ScrmReferralRewardEntity> rewardCaptor =
                ArgumentCaptor.forClass(ScrmReferralRewardEntity.class);
        verify(rewardRepository, times(1)).save(rewardCaptor.capture());
        ScrmReferralRewardEntity savedReward = rewardCaptor.getValue();
        assertThat(savedReward.getReferralId()).isEqualTo(50L);
        assertThat(savedReward.getProgramId()).isEqualTo(10L);
        assertThat(savedReward.getRecipientType()).isEqualTo("REFERRER");
        assertThat(savedReward.getRecipientCustomerId()).isEqualTo(100L);
        assertThat(savedReward.getRewardType()).isEqualTo("POINTS");
        assertThat(savedReward.getRewardValue()).isEqualTo(50d);
        assertThat(savedReward.getStatus()).isEqualTo("ISSUED");
        assertThat(savedReward.getIssuedAt()).isNotNull();
        assertThat(savedReward.getTransactionId()).startsWith("TX");
        ArgumentCaptor<ScrmReferralEntity> referralCaptor =
                ArgumentCaptor.forClass(ScrmReferralEntity.class);
        verify(referralRepository, times(1)).save(referralCaptor.capture());
        assertThat(referralCaptor.getValue().getReferrerRewardStatus()).isEqualTo("ISSUED");
        // 单向奖励, 推荐状态应置 REWARDED
        assertThat(referralCaptor.getValue().getStatus()).isEqualTo("REWARDED");
        assertThat(result.getStatus()).isEqualTo("ISSUED");
    }

    
}
