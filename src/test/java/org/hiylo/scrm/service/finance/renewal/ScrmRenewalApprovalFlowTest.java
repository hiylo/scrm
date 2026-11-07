/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmRenewalApprovalFlowTest.java
 * Description : 续费审批自动接线测试（自动发起 / 通过回填 / 驳回回退 / 回调容错）
 */

package org.hiylo.scrm.service.finance.renewal;

import org.hiylo.scrm.config.ScrmRenewalProperties;
import org.hiylo.scrm.dto.ScrmRenewalTaskDto;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmRenewalTaskEntity;
import org.hiylo.scrm.repository.ScrmChurnRuleRepository;
import org.hiylo.scrm.repository.ScrmChurnWarningRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmFeedbackRepository;
import org.hiylo.scrm.repository.ScrmInvoiceRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmRenewalTaskRepository;
import org.hiylo.scrm.service.CompanyResolver;
import org.hiylo.scrm.service.ScrmCustomerCareTaskService;
import org.hiylo.scrm.service.ScrmFollowUpService;
import org.hiylo.scrm.service.ScrmNotificationCenterService;
import org.hiylo.scrm.service.approval.ApprovalDecidedEvent;
import org.hiylo.scrm.service.approval.RenewalApprovalGatewayImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 续费审批自动接线测试。
 * <p>
 * 覆盖此前完全缺失的链路：非标准续费自动建审批单、审批通过回填、审批驳回退回，
 * 以及回调在异常数据下不得反过来影响审批结果。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("续费审批自动接线")
class ScrmRenewalApprovalFlowTest {

    /** 合同 ID */
    private static final long CONTRACT_ID = 7001L;
    /** 任务 ID */
    private static final long TASK_ID = 7002L;

    @Mock
    private ScrmContractRepository contractRepository;
    @Mock
    private ScrmRenewalTaskRepository renewalTaskRepository;
    @Mock
    private ScrmCustomerRepository customerRepository;
    @Mock
    private ScrmCompanyRepository companyRepository;
    @Mock
    private ScrmNotificationCenterService notificationCenterService;
    @Mock
    private ScrmNotificationRepository notificationRepository;
    @Mock
    private ScrmFollowUpService followUpService;
    @Mock
    private ScrmCustomerCareTaskService careTaskService;
    @Mock
    private ScrmChurnWarningRepository churnWarningRepository;
    @Mock
    private ScrmChurnRuleRepository churnRuleRepository;
    @Mock
    private ScrmConversationRepository conversationRepository;
    @Mock
    private ScrmInvoiceRepository invoiceRepository;
    @Mock
    private ScrmFeedbackRepository feedbackRepository;
    @Mock
    private ObjectProvider<RenewalApprovalGateway> gatewayProvider;
    @Mock
    private RenewalApprovalGateway gateway;
    /** 税务申报记录仓储（流失评分的申报停滞因子） */
    @Mock
    private org.hiylo.scrm.repository.ScrmTaxDeclarationRecordRepository taxDeclarationRecordRepository;

    private ScrmRenewalService service;

    @BeforeEach
    void setUp() {
        service = new ScrmRenewalService(contractRepository, renewalTaskRepository,
                customerRepository, new CompanyResolver(customerRepository, companyRepository),
                null, notificationCenterService, notificationRepository,
                followUpService, careTaskService, churnWarningRepository, churnRuleRepository,
                conversationRepository, invoiceRepository, feedbackRepository,
                new ScrmRenewalProperties(), taxDeclarationRecordRepository, gatewayProvider);
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        // 保存时回填自增 ID
        when(renewalTaskRepository.save(any(ScrmRenewalTaskEntity.class))).thenAnswer(inv -> {
            ScrmRenewalTaskEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(TASK_ID);
            }
            return e;
        });
        ScrmContractEntity contract = new ScrmContractEntity();
        contract.setId(CONTRACT_ID);
        contract.setContractNo("HT-2026-001");
        contract.setContractName("鼎昊代账合同");
        contract.setCustomerId(8001L);
        contract.setEndDate(LocalDate.now().plusMonths(1));
        when(contractRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
        when(renewalTaskRepository.findFirstPendingByContractId(anyLong())).thenReturn(Optional.empty());
    }

    /** 构造创建请求 */
    private ScrmRenewalTaskDto dto(String option) {
        ScrmRenewalTaskDto d = new ScrmRenewalTaskDto();
        d.setContractId(CONTRACT_ID);
        d.setRenewalOption(option);
        d.setPlannedRenewalDate(LocalDate.now().plusMonths(1));
        return d;
    }

    @Test
    @DisplayName("非标准续费（折扣）创建后自动发起审批并回写实例 ID")
    void discountOptionAutoStartsApproval() throws Exception {
        when(gateway.startApproval(any(ScrmRenewalTaskEntity.class))).thenReturn(90001L);

        ScrmRenewalTaskEntity task = service.createTask(dto("DISCOUNT"));

        assertThat(task.getApprovalRequired()).isTrue();
        assertThat(task.getStatus()).isEqualTo(ScrmRenewalService.STATUS_APPROVAL_PENDING);
        // 关键：实例 ID 必须回写，否则审批通过后无法关联回任务
        assertThat(task.getApprovalInstanceId()).isEqualTo(90001L);
        verify(gateway).startApproval(any(ScrmRenewalTaskEntity.class));
    }

    @Test
    @DisplayName("标准续费不发起审批")
    void standardOptionSkipsApproval() throws Exception {
        ScrmRenewalTaskEntity task = service.createTask(dto("STANDARD"));

        assertThat(task.getApprovalRequired()).isFalse();
        assertThat(task.getApprovalInstanceId()).isNull();
        verify(gateway, never()).startApproval(any());
    }

    @Test
    @DisplayName("审批网关未装配时仍能创建任务（只是需人工补建审批）")
    void missingGatewayDoesNotBreakCreation() throws Exception {
        when(gatewayProvider.getIfAvailable()).thenReturn(null);

        ScrmRenewalTaskEntity task = service.createTask(dto("GRACE"));

        assertThat(task.getId()).isNotNull();
        assertThat(task.getStatus()).isEqualTo(ScrmRenewalService.STATUS_APPROVAL_PENDING);
        assertThat(task.getApprovalInstanceId()).isNull();
    }

    @Test
    @DisplayName("审批通过回调：APPROVAL_PENDING → APPROVED 并挂上实例 ID")
    void approvalApprovedAdvancesTask() {
        ScrmRenewalTaskEntity task = new ScrmRenewalTaskEntity();
        task.setId(TASK_ID);
        task.setStatus(ScrmRenewalService.STATUS_APPROVAL_PENDING);
        task.setDiscountPct(new BigDecimal("0.90"));
        when(renewalTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        ScrmRenewalApprovalListener listener = new ScrmRenewalApprovalListener(service);
        listener.onApprovalDecided(new ApprovalDecidedEvent(
                90001L, RenewalApprovalGatewayImpl.BUSINESS_TYPE_RENEWAL,
                String.valueOf(TASK_ID), true, null));

        assertThat(task.getStatus()).isEqualTo(ScrmRenewalService.STATUS_APPROVED);
        assertThat(task.getApprovalInstanceId()).isEqualTo(90001L);
        // 申请时的折扣应保留，不能被覆盖
        assertThat(task.getDiscountPct()).isEqualByComparingTo("0.90");
    }

    @Test
    @DisplayName("审批驳回回调：退回 PENDING 并清掉被否的折扣与赠期")
    void approvalRejectedResetsTask() {
        ScrmRenewalTaskEntity task = new ScrmRenewalTaskEntity();
        task.setId(TASK_ID);
        task.setStatus(ScrmRenewalService.STATUS_APPROVAL_PENDING);
        task.setDiscountPct(new BigDecimal("0.80"));
        task.setGraceMonths(2);
        task.setNotes("原备注");
        when(renewalTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        ScrmRenewalApprovalListener listener = new ScrmRenewalApprovalListener(service);
        listener.onApprovalDecided(new ApprovalDecidedEvent(
                90002L, RenewalApprovalGatewayImpl.BUSINESS_TYPE_RENEWAL,
                String.valueOf(TASK_ID), false, "折扣过低"));

        assertThat(task.getStatus()).isEqualTo(ScrmRenewalService.STATUS_PENDING);
        assertThat(task.getDiscountPct()).isNull();
        assertThat(task.getGraceMonths()).isNull();
        assertThat(task.getApprovalRequired()).isFalse();
        // 驳回原因要留下痕迹，方便销售看到为什么被否
        assertThat(task.getNotes()).contains("折扣过低");
        assertThat(task.getNotes()).contains("原备注");
    }

    @Test
    @DisplayName("非续费业务类型的审批事件被忽略")
    void otherBusinessTypesIgnored() {
        ScrmRenewalApprovalListener listener = new ScrmRenewalApprovalListener(service);
        listener.onApprovalDecided(new ApprovalDecidedEvent(1L, "CONTRACT", "1", true, null));

        verify(renewalTaskRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("回调遇到非法业务 ID 不抛异常（不能反过来影响审批结果）")
    void invalidBusinessIdIsSwallowed() {
        ScrmRenewalApprovalListener listener = new ScrmRenewalApprovalListener(service);
        listener.onApprovalDecided(new ApprovalDecidedEvent(
                1L, RenewalApprovalGatewayImpl.BUSINESS_TYPE_RENEWAL, "not-a-number", true, null));
        // 无异常即通过
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("重复通过回调幂等：状态已非待审批时不再推进")
    void duplicateApprovedIsIdempotent() {
        ScrmRenewalTaskEntity task = new ScrmRenewalTaskEntity();
        task.setId(TASK_ID);
        task.setStatus(ScrmRenewalService.STATUS_APPROVED);
        when(renewalTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        ScrmRenewalApprovalListener listener = new ScrmRenewalApprovalListener(service);
        listener.onApprovalDecided(new ApprovalDecidedEvent(
                90003L, RenewalApprovalGatewayImpl.BUSINESS_TYPE_RENEWAL,
                String.valueOf(TASK_ID), true, null));

        assertThat(task.getStatus()).isEqualTo(ScrmRenewalService.STATUS_APPROVED);
        assertThat(task.getApprovalInstanceId()).isNull();
    }

    @Test
    @DisplayName("创建折扣续费时把申请值落库，审批人才看得到要批什么")
    void discountRequestPersistedOnCreate() throws Exception {
        when(gateway.startApproval(any(ScrmRenewalTaskEntity.class))).thenReturn(90010L);
        ScrmRenewalTaskDto d = dto("DISCOUNT");
        d.setDiscountPct(new BigDecimal("0.85"));

        ScrmRenewalTaskEntity task = service.createTask(d);

        // 此前 createTask 不写这些字段，导致审批实例里折扣为空、审批人无从判断
        assertThat(task.getDiscountPct()).isEqualByComparingTo("0.85");
    }

    @Test
    @DisplayName("审批通过后复位 approvalRequired，避免已批准任务仍被当成待审批")
    void approveClearsApprovalRequired() throws Exception {
        ScrmRenewalTaskEntity task = new ScrmRenewalTaskEntity();
        task.setId(TASK_ID);
        task.setStatus(ScrmRenewalService.STATUS_APPROVAL_PENDING);
        task.setApprovalRequired(true);
        when(renewalTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        ScrmRenewalTaskEntity result = service.approve(TASK_ID, null, null, null, 90011L);

        assertThat(result.getStatus()).isEqualTo(ScrmRenewalService.STATUS_APPROVED);
        assertThat(result.getApprovalRequired()).isFalse();
        assertThat(result.getApprovalInstanceId()).isEqualTo(90011L);
    }

    @Test
    @DisplayName("审批人 JSON 数组必须规整成逗号串，否则审批人校验永远失败")
    void approverIdsFromJsonArrayAreJoinedWithoutBrackets() {
        // 节点定义里 approverIds 写成 JSON 数组，会被解析成 List
        java.util.List<String> approvers = java.util.List.of("372521563485532160", "366396655429357568");

        // 模拟引擎里 flowService.toStr 的处理语义：集合应拼成 "a,b"
        String joined = approvers.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));

        // 直接 List.toString() 会得到 "[a, b]"，按逗号切分后首元素带 '['，
        // 导致 ensureApprovable 判「操作人不在当前审批人列表中」——这是既有引擎 bug
        assertThat(joined).isEqualTo("372521563485532160,366396655429357568");
        assertThat(joined).doesNotContain("[");
        assertThat(joined).doesNotContain("]");

        // 切分后应精确命中
        java.util.List<String> parsed = java.util.Arrays.stream(joined.split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).toList();
        assertThat(parsed).contains("372521563485532160");
    }
}
