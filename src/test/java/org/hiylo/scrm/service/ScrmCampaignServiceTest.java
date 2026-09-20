/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignServiceTest.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmCampaignDto;
import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.entity.ScrmCampaignAccountEntity;
import org.hiylo.scrm.entity.ScrmCampaignEntity;
import org.hiylo.scrm.execution.TaskExecutionService;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmCampaignAccountRepository;
import org.hiylo.scrm.repository.ScrmCampaignRepository;
import org.hiylo.scrm.repository.ScrmCampaignTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockSettings;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmCampaignService 单元测试
 * <p>
 * 验证营销任务创建、启动 (含 TaskExecutionService 扩展点调用)、账号分配等关键流程,
 * 使用 Mockito 隔离 Repository 与执行引擎扩展点。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCampaignService 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ScrmCampaignServiceTest {

    /** 营销任务数据访问层 Mock */
    @Mock
    private ScrmCampaignRepository campaignRepository;

    /** SCRM 账号数据访问层 Mock (assignAccounts 校验账号存在性 + 数据隔离) */
    @Mock
    private ScrmAccountRepository accountRepository;

    /** 任务-账号关联数据访问层 Mock */
    @Mock
    private ScrmCampaignAccountRepository campaignAccountRepository;

    /** SOP 模板数据访问层 Mock */
    @Mock
    private ScrmCampaignTemplateRepository templateRepository;

    /** 营销任务执行引擎扩展点 Mock */
    @Mock
    private TaskExecutionService taskExecutionService;

    /** 被测对象 */
    @InjectMocks
    private ScrmCampaignService campaignService;

    /**
     * 测试前设置请求上下文
     */
    @BeforeEach
    void setUp() {
    }

    /**
     * 测试后清理请求上下文
     */
    @AfterEach
    void tearDown() {
    }

    /**
     * 构造测试用营销任务实体
     */
    private ScrmCampaignEntity buildCampaignEntity(Long id, String status, Long behaviorFlowId, Long fleetId) {
        ScrmCampaignEntity entity = new ScrmCampaignEntity();
        entity.setId(id);
        entity.setCampaignName("campaign-" + id);
        entity.setCampaignType("AUTO_ADD_FRIEND");
        entity.setPlatformType("wework");
        entity.setStatus(status);
        entity.setBehaviorFlowId(behaviorFlowId);
        entity.setFleetId(fleetId);
        return entity;
    }

    @Test
    @DisplayName("createCampaign_success: 创建营销任务, 默认状态 DRAFT, 写入归属账号")
    void createCampaign_success() {
        // ===== Given =====
        ScrmCampaignDto dto = new ScrmCampaignDto();
        dto.setCampaignName("wework-add-friend-001");
        dto.setCampaignType("AUTO_ADD_FRIEND");
        dto.setPlatformType("wework");
        // 不传 status, 验证默认 DRAFT
        when(campaignRepository.save(any(ScrmCampaignEntity.class))).thenAnswer(invocation -> {
            ScrmCampaignEntity entity = invocation.getArgument(0);
            entity.setId(101L);
            return entity;
        });

        // ===== When =====
        ScrmCampaignDto result = campaignService.createCampaign(dto);

        // ===== Then =====
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getCampaignName()).isEqualTo("wework-add-friend-001");
        assertThat(result.getCampaignType()).isEqualTo("AUTO_ADD_FRIEND");
        assertThat(result.getPlatformType()).isEqualTo("wework");
        // 默认状态应为 DRAFT
        assertThat(result.getStatus()).isEqualTo("DRAFT");

        // 验证 save 调用, 实体字段正确
        ArgumentCaptor<ScrmCampaignEntity> entityCaptor = ArgumentCaptor.forClass(ScrmCampaignEntity.class);
        verify(campaignRepository, times(1)).save(entityCaptor.capture());
        ScrmCampaignEntity saved = entityCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getCampaignName()).isEqualTo("wework-add-friend-001");
    }

    @Test
    @DisplayName("startCampaign_success: TaskExecutionService.submitCampaign 返回 ID, 保存并置 RUNNING")
    void startCampaign_success() throws ScrmException {
        // ===== Given =====
        Long campaignId = 200L;
        // DRAFT 状态, 无 behaviorFlowId, 无 fleetId (跳过 executeCampaign)
        ScrmCampaignEntity entity = buildCampaignEntity(campaignId, "DRAFT", null, null);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(entity));

        // 扩展点返回执行任务 ID=888
        when(taskExecutionService.submitCampaign(entity)).thenReturn(888L);

        // save 返回更新后的实体
        when(campaignRepository.save(any(ScrmCampaignEntity.class))).thenAnswer(invocation -> invocation
                .getArgument(0));

        // ===== When =====
        ScrmCampaignDto result = campaignService.startCampaign(campaignId);

        // ===== Then =====
        assertThat(result).isNotNull();
        // behaviorFlowId (语义已变更为执行任务 ID) 已保存
        assertThat(result.getBehaviorFlowId()).isEqualTo(888L);
        // 状态已置为 RUNNING
        assertThat(result.getStatus()).isEqualTo("RUNNING");

        // 验证 submitCampaign 调用一次
        verify(taskExecutionService, times(1)).submitCampaign(any(ScrmCampaignEntity.class));

        // 验证 executeCampaign 未被调用 (因 fleetId 为 null)
        verify(taskExecutionService, never()).executeCampaign(anyLong(), anyLong());

        // 验证 save 调用, behaviorFlowId 与 status 正确
        ArgumentCaptor<ScrmCampaignEntity> entityCaptor = ArgumentCaptor.forClass(ScrmCampaignEntity.class);
        verify(campaignRepository, times(1)).save(entityCaptor.capture());
        ScrmCampaignEntity saved = entityCaptor.getValue();
        assertThat(saved.getBehaviorFlowId()).isEqualTo(888L);
        assertThat(saved.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("startCampaign_noExecutionEngine: 扩展点返回 null 时不抛异常, 仍置 RUNNING")
    void startCampaign_noExecutionEngine() throws ScrmException {
        // ===== Given =====
        Long campaignId = 203L;
        ScrmCampaignEntity entity = buildCampaignEntity(campaignId, "DRAFT", null, null);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(entity));

        // 扩展点返回 null (无外部执行引擎)
        when(taskExecutionService.submitCampaign(entity)).thenReturn(null);

        when(campaignRepository.save(any(ScrmCampaignEntity.class))).thenAnswer(invocation -> invocation
                .getArgument(0));

        // ===== When =====
        ScrmCampaignDto result = campaignService.startCampaign(campaignId);

        // ===== Then =====
        // 不抛异常, 状态仍置为 RUNNING, behaviorFlowId 保持 null
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("RUNNING");
        assertThat(result.getBehaviorFlowId()).isNull();

        // 验证 save 被调用 (状态写入)
        verify(campaignRepository, times(1)).save(any(ScrmCampaignEntity.class));
    }

    @Test
    @DisplayName("startCampaign_withFleetDispatched: 关联 fleetId 时调用 executeCampaign, 派发成功")
    void startCampaign_withFleetDispatched() throws ScrmException {
        // ===== Given =====
        Long campaignId = 204L;
        Long fleetId = 500L;
        ScrmCampaignEntity entity = buildCampaignEntity(campaignId, "DRAFT", null, fleetId);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(entity));

        when(taskExecutionService.submitCampaign(entity)).thenReturn(889L);
        when(taskExecutionService.executeCampaign(889L, fleetId)).thenReturn(true);
        when(campaignRepository.save(any(ScrmCampaignEntity.class))).thenAnswer(invocation -> invocation
                .getArgument(0));

        // ===== When =====
        ScrmCampaignDto result = campaignService.startCampaign(campaignId);

        // ===== Then =====
        assertThat(result.getStatus()).isEqualTo("RUNNING");
        assertThat(result.getBehaviorFlowId()).isEqualTo(889L);

        // 验证 submitCampaign 与 executeCampaign 调用
        verify(taskExecutionService, times(1)).submitCampaign(any(ScrmCampaignEntity.class));
        verify(taskExecutionService, times(1)).executeCampaign(889L, fleetId);
    }

    @Test
    @DisplayName("startCampaign_fleetDispatchFailed: executeCampaign 抛异常时不阻断启动 (失败容忍)")
    void startCampaign_fleetDispatchFailed() throws ScrmException {
        // ===== Given =====
        Long campaignId = 205L;
        Long fleetId = 501L;
        ScrmCampaignEntity entity = buildCampaignEntity(campaignId, "DRAFT", null, fleetId);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(entity));

        when(taskExecutionService.submitCampaign(entity)).thenReturn(890L);
        when(taskExecutionService.executeCampaign(890L, fleetId))
                .thenThrow(new RuntimeException("执行引擎不可达"));
        when(campaignRepository.save(any(ScrmCampaignEntity.class))).thenAnswer(invocation -> invocation
                .getArgument(0));

        // ===== When =====
        ScrmCampaignDto result = campaignService.startCampaign(campaignId);

        // ===== Then =====
        // 派发失败不阻断启动, 状态仍置为 RUNNING
        assertThat(result.getStatus()).isEqualTo("RUNNING");
        assertThat(result.getBehaviorFlowId()).isEqualTo(890L);

        // 验证 save 调用 (状态仍写入)
        verify(campaignRepository, times(1)).save(any(ScrmCampaignEntity.class));
        verify(taskExecutionService, times(1)).executeCampaign(890L, fleetId);
    }

    @Test
    @DisplayName("assignAccounts_success: 批量写入 scrm_campaign_account 关联表, 已存在的 accountId 跳过 (幂等)")
    void assignAccounts_success() throws ScrmException {
        // ===== Given =====
        Long campaignId = 300L;
        ScrmCampaignEntity campaign = buildCampaignEntity(campaignId, "DRAFT", null, null);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        // 已存在的关联 (accountId=1 已绑定, 应跳过)
        ScrmCampaignAccountEntity existingRel = new ScrmCampaignAccountEntity();
        existingRel.setCampaignId(campaignId);
        existingRel.setAccountId(1L);
        when(campaignAccountRepository.findByCampaignId(campaignId))
                .thenReturn(Collections.singletonList(existingRel));
        // save 直接返回传入实体
        when(campaignAccountRepository.save(any(ScrmCampaignAccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // 账号 2/3 存在且归属当前账号, 已登录, 平台类型为空 (跳过平台一致性校验)
        ScrmAccountEntity account2 = new ScrmAccountEntity();
        account2.setId(2L);
        account2.setLoginState("LOGIN");
        ScrmAccountEntity account3 = new ScrmAccountEntity();
        account3.setId(3L);
        account3.setLoginState("LOGIN");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(account2));
        when(accountRepository.findById(3L)).thenReturn(Optional.of(account3));

        // ===== When =====
        // 传入 [1, 2, 3, null] - 1 已存在跳过, null 跳过, 2/3 新写入
        // 注意: List.of 不允许 null 元素, 使用 Arrays.asList 支持 null
        List<Long> result = campaignService.assignAccounts(campaignId, java.util.Arrays.asList(1L, 2L, 3L, null));

        // ===== Then =====
        // 返回列表应包含 1 (已有) + 2, 3 (新加)
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L);

        // 验证 save 调用 2 次 (accountId=2 与 accountId=3)
        ArgumentCaptor<ScrmCampaignAccountEntity> relCaptor = ArgumentCaptor.forClass(ScrmCampaignAccountEntity.class);
        verify(campaignAccountRepository, times(2)).save(relCaptor.capture());
        // 所有保存的关联记录 campaignId / 归属账号正确
        relCaptor.getAllValues().forEach(rel -> {
            assertThat(rel.getCampaignId()).isEqualTo(campaignId);
        });
        // 新写入的 accountId 应为 2 和 3
        assertThat(relCaptor.getAllValues())
                .extracting(ScrmCampaignAccountEntity::getAccountId)
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("startCampaign_invalidStatus: 非 DRAFT / PAUSED 状态启动抛 ScrmException")
    void startCampaign_invalidStatus() {
        // ===== Given =====
        Long campaignId = 206L;
        ScrmCampaignEntity entity = buildCampaignEntity(campaignId, "COMPLETED", null, null);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(entity));

        // ===== When / Then =====
        assertThatThrownBy(() -> campaignService.startCampaign(campaignId))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("任务状态非法");

        // 未通过状态校验时不应调用扩展点
        verify(taskExecutionService, never()).submitCampaign(any(ScrmCampaignEntity.class));
        verify(campaignRepository, never()).save(any(ScrmCampaignEntity.class));
    }
}
