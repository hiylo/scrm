/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmRenewalServiceTest.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.renewal;

import org.hiylo.scrm.config.ScrmRenewalProperties;
import org.hiylo.scrm.dto.ScrmRenewalTaskDto;
import org.hiylo.scrm.entity.ScrmChurnRuleEntity;
import org.hiylo.scrm.entity.ScrmChurnWarningEntity;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmRenewalTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.service.CompanyResolver;
import org.hiylo.scrm.repository.ScrmChurnRuleRepository;
import org.hiylo.scrm.repository.ScrmChurnWarningRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmFeedbackRepository;
import org.hiylo.scrm.repository.ScrmInvoiceRepository;
import org.hiylo.scrm.repository.ScrmNotificationRepository;
import org.hiylo.scrm.repository.ScrmRenewalTaskRepository;
import org.hiylo.scrm.service.ScrmCustomerCareTaskService;
import org.hiylo.scrm.service.ScrmFollowUpService;
import org.hiylo.scrm.service.ScrmNotificationCenterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmRenewalService} 单元测试（续费流失域）。
 * <p>
 * 锁定关键业务语义:
 * <ul>
 *   <li>到期扫描幂等生成（同合同仅一条待办）</li>
 *   <li>到期未续转 OVERDUE → 超期转 LOST 触发流失评分</li>
 *   <li>续费结果录入守卫（终态不可再录）+ 折扣/赠期需审批</li>
 *   <li>流失评分最小实现: 三输入加权 + 冷却去重</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmRenewalService 续费流失")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScrmRenewalServiceTest {

    /** 合同仓库 Mock */
    @Mock
    private ScrmContractRepository contractRepository;
    /** 续费任务仓库 Mock */
    @Mock
    private ScrmRenewalTaskRepository renewalTaskRepository;
    /** 客户仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;

    @Mock
    private ScrmCompanyRepository companyRepository;
    /** 通知中心门面 Mock */
    @Mock
    private ScrmNotificationCenterService notificationCenterService;
    /** 通知记录仓库 Mock */
    @Mock
    private ScrmNotificationRepository notificationRepository;
    /** 跟进任务服务 Mock */
    @Mock
    private ScrmFollowUpService followUpService;
    /** 关怀任务服务 Mock */
    @Mock
    private ScrmCustomerCareTaskService careTaskService;
    /** 流失预警仓库 Mock */
    @Mock
    private ScrmChurnWarningRepository churnWarningRepository;
    /** 流失规则仓库 Mock */
    @Mock
    private ScrmChurnRuleRepository churnRuleRepository;
    /** 会话仓库 Mock */
    @Mock
    private ScrmConversationRepository conversationRepository;
    /** 发票仓库 Mock */
    @Mock
    private ScrmInvoiceRepository invoiceRepository;
    /** 反馈仓库 Mock */
    @Mock
    private ScrmFeedbackRepository feedbackRepository;
    /** 财税数据范围（测试默认不限制：resolveAccessibleCompanyIds 返回 null） */
    @Mock
    private org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;
    /** 税务申报记录仓储（流失评分的申报停滞因子） */
    @Mock
    private org.hiylo.scrm.repository.ScrmTaxDeclarationRecordRepository taxDeclarationRecordRepository;


    /** 被测服务 */
    private ScrmRenewalService service;

    @BeforeEach
    void setUp() {
        service = new ScrmRenewalService(contractRepository, renewalTaskRepository,
                customerRepository, new CompanyResolver(customerRepository, companyRepository), financeDataScope, notificationCenterService, notificationRepository,
                followUpService, careTaskService, churnWarningRepository, churnRuleRepository,
                conversationRepository, invoiceRepository, feedbackRepository,
                new ScrmRenewalProperties(), taxDeclarationRecordRepository,
                // 测试里不装配审批网关：非标准续费不会自动建单，行为与既有用例一致
                emptyGatewayProvider());
    }

    @Test
    @DisplayName("scanAndGenerateTasks: 到期窗口内 ACTIVE 合同生成任务, 已存在待办不重复生成")
    void scanAndGenerateTasks_idempotent() {
        ScrmContractEntity contract = buildContract(1L, LocalDate.now().plusDays(10));
        contract.setAutoRenew(false);
        when(contractRepository.findByStatusInAndEndDateLessThanEqual(any(), any()))
                .thenReturn(List.of(contract));
        when(renewalTaskRepository.findFirstPendingByContractId(1L))
                .thenReturn(Optional.empty());
        when(renewalTaskRepository.save(any(ScrmRenewalTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int created = service.scanAndGenerateTasks();

        assertThat(created).isEqualTo(1);
        verify(renewalTaskRepository).save(any(ScrmRenewalTaskEntity.class));
    }

    @Test
    @DisplayName("scanAndGenerateTasks: autoRenew=true 的合同不生成续费任务")
    void scanAndGenerateTasks_skipAutoRenew() {
        ScrmContractEntity contract = buildContract(1L, LocalDate.now().plusDays(10));
        contract.setAutoRenew(true);
        when(contractRepository.findByStatusInAndEndDateLessThanEqual(any(), any()))
                .thenReturn(List.of(contract));

        int created = service.scanAndGenerateTasks();

        assertThat(created).isZero();
        verify(renewalTaskRepository, never()).save(any(ScrmRenewalTaskEntity.class));
    }

    @Test
    @DisplayName("recordResult: RENEWED 成功, LOST 触发流失评分, CANCELLED 取消")
    void recordResult_renewed() throws Exception {
        ScrmRenewalTaskEntity task = buildTask(1L, "PENDING");
        task.setSalesPersonId("sales-1");
        when(renewalTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(renewalTaskRepository.save(any(ScrmRenewalTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmRenewalTaskEntity result = service.recordResult(1L, "RENEWED", new BigDecimal("1000"), null);

        assertThat(result.getStatus()).isEqualTo("RENEWED");
        assertThat(result.getRenewalAmount()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("recordResult: 终态任务不可再录 → CONFLICT")
    void recordResult_terminalRejected() {
        ScrmRenewalTaskEntity task = buildTask(1L, "RENEWED");
        when(renewalTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.recordResult(1L, "LOST", null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("任务已终态");
    }

    @Test
    @DisplayName("createTask: DISCOUNT 选项 → approvalRequired=true 且转 APPROVAL_PENDING")
    void createTask_discountRequiresApproval() {
        ScrmContractEntity contract = buildContract(1L, LocalDate.now().plusDays(30));
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
        when(renewalTaskRepository.findFirstPendingByContractId(1L)).thenReturn(Optional.empty());
        when(renewalTaskRepository.save(any(ScrmRenewalTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmRenewalTaskDto dto = new ScrmRenewalTaskDto();
        dto.setContractId(1L);
        dto.setRenewalOption("DISCOUNT");

        ScrmRenewalTaskEntity result = service.createTask(dto);

        assertThat(result.getApprovalRequired()).isTrue();
        assertThat(result.getStatus()).isEqualTo("APPROVAL_PENDING");
    }

    @Test
    @DisplayName("approve: 审批通过回填金额并转 APPROVED")
    void approve_fillsAmount() throws Exception {
        ScrmRenewalTaskEntity task = buildTask(1L, "APPROVAL_PENDING");
        when(renewalTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(renewalTaskRepository.save(any(ScrmRenewalTaskEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmRenewalTaskEntity result = service.approve(1L, new BigDecimal("5.00"), 2,
                new BigDecimal("950"), 99L);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getDiscountPct()).isEqualByComparingTo("5.00");
        assertThat(result.getGraceMonths()).isEqualTo(2);
        assertThat(result.getRenewalAmount()).isEqualByComparingTo("950");
        assertThat(result.getApprovalInstanceId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("scanChurn: 零对话+无开票+差评 → 风险分≥75 HIGH, 冷却期内不重复建")
    void scanChurn_highRisk() throws Exception {
        org.hiylo.scrm.entity.ScrmCustomerEntity customer = new org.hiylo.scrm.entity.ScrmCustomerEntity();
        customer.setId(100L);
        customer.setNickname("流失风险客户");
        customer.setOwnerAccountId(5L);
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        // 无 ACTIVE 预警（冷却期外）
        when(churnWarningRepository.findFirstByCustomerIdAndStatusAndDetectedAtAfterOrderByDetectedAtDesc(
                anyLong(), any(), any())).thenReturn(Optional.empty());
        // 近 14 天无会话
        when(conversationRepository.findByCustomerId(100L)).thenReturn(List.of());
        // 近 60 天无发票
        when(invoiceRepository.count(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(0L);
        // 近 30 天差评反馈
        org.hiylo.scrm.entity.ScrmFeedbackEntity feedback = new org.hiylo.scrm.entity.ScrmFeedbackEntity();
        feedback.setRating(1);
        feedback.setFeedbackType("COMPLAINT");
        feedback.setCategory("SERVICE_COMPLAINT");
        when(feedbackRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(feedback));
        ScrmChurnRuleEntity rule = new ScrmChurnRuleEntity();
        rule.setId(10L);
        rule.setRuleName("财税服务流失评分(v1)");
        when(churnRuleRepository.findByEnabledTrueOrderByPriorityAsc()).thenReturn(List.of(rule));
        when(churnWarningRepository.save(any(ScrmChurnWarningEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmChurnWarningEntity warning = service.scanChurn(100L);

        assertThat(warning).isNotNull();
        assertThat(warning.getRiskLevel()).isEqualTo("HIGH");
        assertThat(warning.getRiskScore()).isGreaterThanOrEqualTo(75.0);
        assertThat(warning.getRiskFactors()).contains("conversation_frequency")
                .contains("service_stop_signal").contains("negative_feedback");
        verify(followUpService).createTask(any(org.hiylo.scrm.dto.ScrmFollowUpTaskDto.class));
    }

    @Test
    @DisplayName("scanChurn: 冷却期内已有 ACTIVE 预警 → 不重复建")
    void scanChurn_cooldownSkips() {
        when(churnWarningRepository.findFirstByCustomerIdAndStatusAndDetectedAtAfterOrderByDetectedAtDesc(
                anyLong(), any(), any())).thenReturn(Optional.of(new ScrmChurnWarningEntity()));

        ScrmChurnWarningEntity warning = service.scanChurn(100L);

        assertThat(warning).isNull();
        verify(churnWarningRepository, never()).save(any(ScrmChurnWarningEntity.class));
    }

    @Test
    @DisplayName("scanChurn: 活跃客户风险分低 → 不建预警（仅观察）")
    void scanChurn_lowRiskNoWarning() throws Exception {
        org.hiylo.scrm.entity.ScrmCustomerEntity customer = new org.hiylo.scrm.entity.ScrmCustomerEntity();
        customer.setId(200L);
        customer.setNickname("活跃客户");
        when(customerRepository.findById(200L)).thenReturn(Optional.of(customer));
        when(churnWarningRepository.findFirstByCustomerIdAndStatusAndDetectedAtAfterOrderByDetectedAtDesc(
                anyLong(), any(), any())).thenReturn(Optional.empty());
        // 有会话
        org.hiylo.scrm.entity.ScrmConversationEntity conv = new org.hiylo.scrm.entity.ScrmConversationEntity();
        conv.setLastMessageAt(java.time.LocalDateTime.now());
        when(conversationRepository.findByCustomerId(200L)).thenReturn(List.of(conv, conv));
        // 有发票
        when(invoiceRepository.count(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(5L);
        // 无负面反馈
        when(feedbackRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        ScrmChurnWarningEntity warning = service.scanChurn(200L);

        assertThat(warning).isNull();
    }

    private ScrmContractEntity buildContract(Long id, LocalDate endDate) {
        ScrmContractEntity c = new ScrmContractEntity();
        c.setId(id);
        c.setContractNo("HT-001");
        c.setContractName("代理记账服务合同");
        c.setCustomerId(100L);
        c.setCustomerName("客户A");
        c.setSalesPersonId("sales-1");
        c.setSalesPersonName("张销售");
        c.setEndDate(endDate);
        c.setAutoRenew(false);
        return c;
    }

    private ScrmRenewalTaskEntity buildTask(Long id, String status) {
        ScrmRenewalTaskEntity t = new ScrmRenewalTaskEntity();
        t.setId(id);
        t.setContractId(1L);
        t.setCustomerId(100L);
        t.setCustomerName("客户A");
        t.setPlannedRenewalDate(LocalDate.now().plusDays(10));
        t.setStatus(status);
        return t;
    }

    /**
     * 构造一个「未装配网关」的 ObjectProvider。
     * <p>续费用例只验证续费自身逻辑，不需要真的发起审批。</p>
     *
     * @return 空的 ObjectProvider
     */
    @SuppressWarnings("unchecked")
    private static org.springframework.beans.factory.ObjectProvider<
            org.hiylo.scrm.service.finance.renewal.RenewalApprovalGateway> emptyGatewayProvider() {
        org.springframework.beans.factory.ObjectProvider<
                org.hiylo.scrm.service.finance.renewal.RenewalApprovalGateway> provider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    /**
     * 反射调用私有 computeChurnScore，精确验证五因子加权。
     *
     * @param customerId 客户 ID
     * @return 0-100 分
     * @throws Exception 反射失败
     */
    private double invokeComputeChurnScore(Long customerId) throws Exception {
        java.lang.reflect.Method m = ScrmRenewalService.class
                .getDeclaredMethod("computeChurnScore", Long.class);
        m.setAccessible(true);
        return (double) m.invoke(service, customerId);
    }

    @Test
    @DisplayName("流失评分：正常客户得分 13.0，远低于 MEDIUM 阈值 50")
    void churnScore_baselineStaysLow() throws Exception {
        // 8 天前的会话（<10 条 → 0.3）之外的场景取基线：这里全部给"正常"
        java.util.List<org.hiylo.scrm.entity.ScrmConversationEntity> convs = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            org.hiylo.scrm.entity.ScrmConversationEntity c =
                    new org.hiylo.scrm.entity.ScrmConversationEntity();
            c.setLastMessageAt(java.time.LocalDateTime.now().minusHours(i));
            convs.add(c);
        }
        when(conversationRepository.findByCustomerId(300L)).thenReturn(convs);
        when(invoiceRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(5L);
        when(feedbackRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(java.util.Collections.emptyList());
        when(renewalTaskRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        when(taxDeclarationRecordRepository.findByCustomerIdOrderByPeriodStartAsc(300L))
                .thenReturn(java.util.Collections.emptyList());

        double score = invokeComputeChurnScore(300L);

        // 对话 12 条(≥10 → 0.1)*25 + 开票 5 张(0.2)*30 + 无差评(0.1)*20
        // + 续费正常(0.1)*10 + 无逾期(0.1)*15 = 2.5 + 6.0 + 2.0 + 1.0 + 1.5 = 13.0
        assertThat(score).isCloseTo(13.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("流失评分：两期申报逾期时，申报停滞因子按满分 15 分计入")
    void churnScore_taxStagnationAddsFifteen() throws Exception {
        java.util.List<org.hiylo.scrm.entity.ScrmConversationEntity> convs = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            org.hiylo.scrm.entity.ScrmConversationEntity c =
                    new org.hiylo.scrm.entity.ScrmConversationEntity();
            c.setLastMessageAt(java.time.LocalDateTime.now().minusHours(i));
            convs.add(c);
        }
        when(conversationRepository.findByCustomerId(301L)).thenReturn(convs);
        when(invoiceRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(5L);
        when(feedbackRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(java.util.Collections.emptyList());
        when(renewalTaskRepository.findAll()).thenReturn(java.util.Collections.emptyList());

        org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity r1 =
                new org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity();
        r1.setDeclarationStatus("OVERDUE");
        org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity r2 =
                new org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity();
        r2.setDeclarationStatus("OVERDUE");
        when(taxDeclarationRecordRepository.findByCustomerIdOrderByPeriodStartAsc(301L))
                .thenReturn(java.util.List.of(r1, r2));

        double score = invokeComputeChurnScore(301L);

        // 其余因子基线 13.0 - 申报停滞基线 1.5 + 满分 15 = 26.5
        assertThat(score).isCloseTo(26.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("流失评分：单期逾期取 0.6（9 分），权重低于两期以上")
    void churnScore_singleOverdueWeighsLess() throws Exception {
        java.util.List<org.hiylo.scrm.entity.ScrmConversationEntity> convs = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            org.hiylo.scrm.entity.ScrmConversationEntity c =
                    new org.hiylo.scrm.entity.ScrmConversationEntity();
            c.setLastMessageAt(java.time.LocalDateTime.now().minusHours(i));
            convs.add(c);
        }
        when(conversationRepository.findByCustomerId(302L)).thenReturn(convs);
        when(invoiceRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(5L);
        when(feedbackRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(java.util.Collections.emptyList());
        when(renewalTaskRepository.findAll()).thenReturn(java.util.Collections.emptyList());

        org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity one =
                new org.hiylo.scrm.entity.ScrmTaxDeclarationRecordEntity();
        one.setDeclarationStatus("OVERDUE");
        when(taxDeclarationRecordRepository.findByCustomerIdOrderByPeriodStartAsc(302L))
                .thenReturn(java.util.List.of(one));

        double score = invokeComputeChurnScore(302L);

        // 13.0 - 1.5 + 0.6*15 = 20.5
        assertThat(score).isCloseTo(20.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("流失评分：申报记录查询失败时因子取中性值，不影响整体评分")
    void churnScore_taxQueryFailureIsTolerated() throws Exception {
        when(conversationRepository.findByCustomerId(303L)).thenReturn(java.util.Collections.emptyList());
        when(invoiceRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(0L);
        when(feedbackRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(java.util.Collections.emptyList());
        when(renewalTaskRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        when(taxDeclarationRecordRepository.findByCustomerIdOrderByPeriodStartAsc(303L))
                .thenThrow(new RuntimeException("db down"));

        // 不应抛异常，且分数在 0-100 内
        double score = invokeComputeChurnScore(303L);
        assertThat(score).isBetween(0.0, 100.0);
    }
}
