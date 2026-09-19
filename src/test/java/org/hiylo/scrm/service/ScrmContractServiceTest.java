/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmContractApproveDto;
import org.hiylo.scrm.dto.ScrmContractCreateDto;
import org.hiylo.scrm.dto.ScrmContractDto;
import org.hiylo.scrm.dto.ScrmContractSignDto;
import org.hiylo.scrm.entity.ScrmContractEntity;
import org.hiylo.scrm.entity.ScrmContractTemplateEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmContractReminderRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.hiylo.scrm.repository.ScrmContractTemplateRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmContractService 单元测试
 * <p>
 * 聚焦合同全生命周期管理: 合同创建 (模板渲染 + 编号生成 + 使用次数递增 + 提醒生成)、
 * 审批 (通过 / 驳回)、签署、终止与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmContractService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmContractServiceTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 合同模板数据仓库 Mock 桩 */
    @Mock
    private ScrmContractTemplateRepository templateRepository;
    /** 合同数据仓库 Mock 桩 */
    @Mock
    private ScrmContractRepository contractRepository;
    /** 合同提醒数据仓库 Mock 桩 */
    @Mock
    private ScrmContractReminderRepository reminderRepository;
    /** 客户数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmContractService service;

    @BeforeEach
    void setUp() {
        ScrmContractTemplateService templateService =
                new ScrmContractTemplateService(objectMapper, templateRepository, contractRepository);
        ScrmContractManageService manageService =
                new ScrmContractManageService(contractRepository, reminderRepository, customerRepository, templateService);
        ScrmContractReminderService reminderService =
                new ScrmContractReminderService(reminderRepository, contractRepository, manageService);
        ScrmContractStatService statsService =
                new ScrmContractStatService(contractRepository, reminderRepository);
        service = new ScrmContractService(templateService, manageService, reminderService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的合同模板实体 (用于 findById 返回)
     */
    private ScrmContractTemplateEntity buildTemplateEntity(Long id, String status) {
        ScrmContractTemplateEntity entity = new ScrmContractTemplateEntity();
        entity.setId(id);
        entity.setTemplateName("销售合同模板");
        entity.setTemplateCode("TPL_SALES_001");
        entity.setContractType("SALES");
        entity.setTemplateContent("甲方：{{customerName}}，合同金额：{{amount}}");
        entity.setClauses("条款1");
        entity.setTemplateVersion(1);
        entity.setStatus(status);
        entity.setUsageCount(0);
        return entity;
    }

    /**
     * 构造已持久化的合同实体
     */
    private ScrmContractEntity buildContractEntity(Long id, String status) {
        ScrmContractEntity entity = new ScrmContractEntity();
        entity.setId(id);
        entity.setContractNo("HT202608050001");
        entity.setContractName("销售合同");
        entity.setContractType("SALES");
        entity.setTemplateId(10L);
        entity.setCustomerId(100L);
        entity.setCustomerName("张三");
        entity.setStartDate(LocalDate.now());
        entity.setEndDate(LocalDate.now().plusMonths(12));
        entity.setDurationMonths(12);
        entity.setContractAmount(50000d);
        entity.setCurrency("CNY");
        entity.setStatus(status);
        entity.setRemindersEnabled(true);
        entity.setReminderDaysBefore(30);
        return entity;
    }

    /**
     * 构造已持久化的客户实体
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setNickname("张三");
        return entity;
    }

    @Test
    @DisplayName("createContract: 渲染模板并写入归属账号与 DRAFT 状态, 递增模板使用次数并生成提醒")
    void createContract_success() throws ScrmException {
        ScrmContractTemplateEntity template = buildTemplateEntity(10L, "ACTIVE");
        ScrmCustomerEntity customer = buildCustomerEntity(100L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        final ScrmContractEntity[] holder = new ScrmContractEntity[1];
        when(contractRepository.save(any(ScrmContractEntity.class))).thenAnswer(inv -> {
            ScrmContractEntity e = inv.getArgument(0);
            e.setId(1L);
            holder[0] = e;
            return e;
        });
        when(contractRepository.findById(1L)).thenAnswer(inv -> Optional.of(holder[0]));
        when(templateRepository.save(any(ScrmContractTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(reminderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ScrmContractCreateDto createDto = new ScrmContractCreateDto();
        createDto.setTemplateId(10L);
        createDto.setCustomerId(100L);
        createDto.setContractAmount(50000d);
        createDto.setVariables("{\"amount\":\"50000\"}");

        ScrmContractDto result = service.createContract(createDto);

        ArgumentCaptor<ScrmContractEntity> contractCaptor =
                ArgumentCaptor.forClass(ScrmContractEntity.class);
        verify(contractRepository, times(1)).save(contractCaptor.capture());
        ScrmContractEntity savedContract = contractCaptor.getValue();
        assertThat(savedContract.getStatus()).isEqualTo("DRAFT");
        assertThat(savedContract.getContractNo()).startsWith("HT");
        assertThat(savedContract.getCustomerId()).isEqualTo(100L);
        assertThat(savedContract.getContent()).contains("张三");
        assertThat(savedContract.getContent()).contains("50000");
        ArgumentCaptor<ScrmContractTemplateEntity> templateCaptor =
                ArgumentCaptor.forClass(ScrmContractTemplateEntity.class);
        verify(templateRepository, times(1)).save(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getUsageCount()).isEqualTo(1);
        verify(reminderRepository, times(1)).saveAll(any());
        assertThat(result.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("createContract: 模板未启用时抛 BAD_REQUEST")
    void createContract_templateInactive() {
        ScrmContractTemplateEntity template = buildTemplateEntity(10L, "INACTIVE");
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));

        ScrmContractCreateDto createDto = new ScrmContractCreateDto();
        createDto.setTemplateId(10L);
        createDto.setCustomerId(100L);

        assertThatThrownBy(() -> service.createContract(createDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模板未启用");
        verify(contractRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve: APPROVE 动作将合同状态从 PENDING_REVIEW 流转至 PENDING_SIGNATURE")
    void approve_approveAction() throws ScrmException {
        ScrmContractEntity contract = buildContractEntity(50L, "PENDING_REVIEW");
        when(contractRepository.findById(50L)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(ScrmContractEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmContractApproveDto approveDto = new ScrmContractApproveDto();
        approveDto.setContractId(50L);
        approveDto.setAction("APPROVE");
        approveDto.setApproverId("admin01");
        approveDto.setApproverName("管理员");
        approveDto.setComment("同意");

        ScrmContractDto result = service.approve(approveDto);

        ArgumentCaptor<ScrmContractEntity> captor =
                ArgumentCaptor.forClass(ScrmContractEntity.class);
        verify(contractRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING_SIGNATURE");
        assertThat(captor.getValue().getApproverId()).isEqualTo("admin01");
        assertThat(captor.getValue().getApprovedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PENDING_SIGNATURE");
    }

    @Test
    @DisplayName("approve: REJECT 动作将合同状态回退至 DRAFT")
    void approve_rejectAction() throws ScrmException {
        ScrmContractEntity contract = buildContractEntity(50L, "PENDING_REVIEW");
        when(contractRepository.findById(50L)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(ScrmContractEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmContractApproveDto approveDto = new ScrmContractApproveDto();
        approveDto.setContractId(50L);
        approveDto.setAction("REJECT");
        approveDto.setComment("金额有误");

        ScrmContractDto result = service.approve(approveDto);

        ArgumentCaptor<ScrmContractEntity> captor =
                ArgumentCaptor.forClass(ScrmContractEntity.class);
        verify(contractRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("sign: 签署后状态流转至 SIGNED 并记录签署人")
    void sign_success() throws ScrmException {
        ScrmContractEntity contract = buildContractEntity(50L, "PENDING_SIGNATURE");
        when(contractRepository.findById(50L)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(ScrmContractEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmContractSignDto signDto = new ScrmContractSignDto();
        signDto.setContractId(50L);
        signDto.setSignerId("signer01");
        signDto.setSignerName("签署人");
        signDto.setSignatureMethod("ELECTRONIC");
        signDto.setSignatureUrl("https://example.com/sign.pdf");

        ScrmContractDto result = service.sign(signDto);

        ArgumentCaptor<ScrmContractEntity> captor =
                ArgumentCaptor.forClass(ScrmContractEntity.class);
        verify(contractRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SIGNED");
        assertThat(captor.getValue().getSignerId()).isEqualTo("signer01");
        assertThat(captor.getValue().getSignedDate()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SIGNED");
    }

    @Test
    @DisplayName("terminate: 非 ACTIVE/SIGNED 状态终止时抛 BAD_REQUEST")
    void terminate_invalidStatus() {
        ScrmContractEntity contract = buildContractEntity(50L, "DRAFT");
        when(contractRepository.findById(50L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.terminate(50L, "客户解约", null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("合同状态非法");
        verify(contractRepository, never()).save(any());
    }

    
}
