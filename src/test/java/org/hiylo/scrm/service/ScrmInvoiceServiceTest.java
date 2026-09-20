/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmInvoiceApplyDto;
import org.hiylo.scrm.dto.ScrmInvoiceApproveDto;
import org.hiylo.scrm.dto.ScrmInvoiceDto;
import org.hiylo.scrm.dto.ScrmInvoiceRedFlushDto;
import org.hiylo.scrm.dto.ScrmInvoiceTemplateDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmInvoiceEntity;
import org.hiylo.scrm.entity.ScrmInvoiceTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmInvoiceRepository;
import org.hiylo.scrm.repository.ScrmInvoiceTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
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
 * ScrmInvoiceService 单元测试
 * <p>
 * 聚焦发票全生命周期管理 (申请 / 审批 / 开具 / 发送 / 送达 / 作废 / 红冲)、
 * 发票模板管理 (创建 / 唯一性校验 / 启停)、编号生成、税额计算、
 * 参数校验与越权隔离等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmInvoiceService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmInvoiceServiceTest {

    /** 发票仓库 Mock */
    @Mock
    private ScrmInvoiceRepository invoiceRepository;
    /** 发票模板仓库 Mock */
    @Mock
    private ScrmInvoiceTemplateRepository templateRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmInvoiceService service;

    @BeforeEach
    void setUp() {
        service = new ScrmInvoiceService(invoiceRepository, templateRepository, customerRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的发票实体 (用于 findById 返回)
     */
    private ScrmInvoiceEntity buildInvoiceEntity(Long id, String status) {
        ScrmInvoiceEntity entity = new ScrmInvoiceEntity();
        entity.setId(id);
        entity.setInvoiceNo("FP202608050001");
        entity.setApplicationNo("AP202608050001");
        entity.setInvoiceType("GENERAL");
        entity.setInvoiceCategory("NORMAL");
        entity.setTitleType("ENTERPRISE");
        entity.setInvoiceTitle("测试公司");
        entity.setCustomerId(100L);
        entity.setCustomerName("张三");
        entity.setAmount(1000d);
        entity.setTaxRate(0.13);
        entity.setTaxAmount(130d);
        entity.setTotalAmount(1130d);
        entity.setDiscountAmount(0d);
        entity.setActualAmount(1130d);
        entity.setCurrency("CNY");
        entity.setStatus(status);
        return entity;
    }

    /**
     * 构造发票申请 DTO
     */
    private ScrmInvoiceApplyDto buildApplyDto() {
        ScrmInvoiceApplyDto dto = new ScrmInvoiceApplyDto();
        dto.setCustomerId(100L);
        dto.setInvoiceType("GENERAL");
        dto.setTitleType("ENTERPRISE");
        dto.setInvoiceTitle("测试公司");
        dto.setAmount(1000d);
        dto.setTaxRate(0.13);
        return dto;
    }

    @Test
    @DisplayName("applyInvoice: 生成编号与税额, 状态置 PENDING 后持久化")
    void applyInvoice_success() throws ScrmException {
        ScrmInvoiceApplyDto dto = buildApplyDto();
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(100L);
        customer.setNickname("张三");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(invoiceRepository.countByInvoiceNoStartingWith(any(String.class)))
                .thenReturn(0L);
        when(invoiceRepository.countByApplicationNoStartingWith(any(String.class)))
                .thenReturn(0L);
        when(invoiceRepository.save(any(ScrmInvoiceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmInvoiceDto result = service.applyInvoice(dto);

        ArgumentCaptor<ScrmInvoiceEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceEntity.class);
        verify(invoiceRepository, times(1)).save(captor.capture());
        ScrmInvoiceEntity saved = captor.getValue();
        // 发票编号以 FP 开头
        assertThat(saved.getInvoiceNo()).startsWith("FP");
        // 申请编号以 AP 开头
        assertThat(saved.getApplicationNo()).startsWith("AP");
        // 税额 = 1000 * 0.13 = 130
        assertThat(saved.getTaxAmount()).isEqualTo(130.0);
        // 价税合计 = 1000 + 130 = 1130
        assertThat(saved.getTotalAmount()).isEqualTo(1130.0);
        // 状态初值为 PENDING
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        // invoiceCategory 默认 NORMAL
        assertThat(saved.getInvoiceCategory()).isEqualTo("NORMAL");
        // 币种默认 CNY
        assertThat(saved.getCurrency()).isEqualTo("CNY");
        assertThat(result.getCustomerName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("applyInvoice: 发票类型非法抛 BAD_REQUEST")
    void applyInvoice_invalidType() {
        ScrmInvoiceApplyDto dto = buildApplyDto();
        dto.setInvoiceType("INVALID_TYPE");

        assertThatThrownBy(() -> service.applyInvoice(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("发票类型非法");
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyInvoice: 抬头类型非法抛 BAD_REQUEST")
    void applyInvoice_invalidTitleType() {
        ScrmInvoiceApplyDto dto = buildApplyDto();
        dto.setTitleType("INVALID_TITLE");

        assertThatThrownBy(() -> service.applyInvoice(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("抬头类型非法");
        verify(invoiceRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("approveInvoice: APPROVE 动作流转至 APPROVED")
    void approveInvoice_approve() throws ScrmException {
        ScrmInvoiceEntity entity = buildInvoiceEntity(10L, "PENDING");
        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(invoiceRepository.save(any(ScrmInvoiceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmInvoiceApproveDto dto = new ScrmInvoiceApproveDto();
        dto.setInvoiceId(10L);
        dto.setAction("APPROVE");
        dto.setApprovedBy("admin");
        dto.setComment("通过");
        ScrmInvoiceDto result = service.approveInvoice(dto);

        ArgumentCaptor<ScrmInvoiceEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceEntity.class);
        verify(invoiceRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("APPROVED");
        assertThat(captor.getValue().getApprovedBy()).isEqualTo("admin");
        assertThat(result.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("approveInvoice: REJECT 动作流转至 REJECTED")
    void approveInvoice_reject() throws ScrmException {
        ScrmInvoiceEntity entity = buildInvoiceEntity(10L, "PENDING");
        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(invoiceRepository.save(any(ScrmInvoiceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmInvoiceApproveDto dto = new ScrmInvoiceApproveDto();
        dto.setInvoiceId(10L);
        dto.setAction("REJECT");
        dto.setApprovedBy("admin");
        service.approveInvoice(dto);

        ArgumentCaptor<ScrmInvoiceEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceEntity.class);
        verify(invoiceRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("approveInvoice: 非 PENDING 状态不可审批抛 BAD_REQUEST")
    void approveInvoice_invalidStatus() {
        ScrmInvoiceEntity entity = buildInvoiceEntity(10L, "ISSUED");
        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(entity));

        ScrmInvoiceApproveDto dto = new ScrmInvoiceApproveDto();
        dto.setInvoiceId(10L);
        dto.setAction("APPROVE");
        assertThatThrownBy(() -> service.approveInvoice(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PENDING 可审批");
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("batchApprove: 批量审批返回成功数, 跳过状态非法的发票")
    void batchApprove_partialSuccess() throws ScrmException {
        // 第一张 PENDING 可审批, 第二张 ISSUED 不可审批被跳过
        ScrmInvoiceEntity ok = buildInvoiceEntity(10L, "PENDING");
        ScrmInvoiceEntity skip = buildInvoiceEntity(11L, "ISSUED");
        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(ok));
        when(invoiceRepository.findById(11L)).thenReturn(Optional.of(skip));
        when(invoiceRepository.save(any(ScrmInvoiceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int success = service.batchApprove(List.of(10L, 11L), "APPROVE", "批量通过");

        assertThat(success).isEqualTo(1);
        verify(invoiceRepository, times(1)).save(any(ScrmInvoiceEntity.class));
    }

    @Test
    @DisplayName("batchApprove: 非法动作抛 BAD_REQUEST")
    void batchApprove_invalidAction() {
        assertThatThrownBy(() -> service.batchApprove(List.of(10L), "INVALID", null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("审批动作非法");
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("issueInvoice: APPROVED → ISSUED 并生成税控号码")
    void issueInvoice_success() throws ScrmException {
        ScrmInvoiceEntity entity = buildInvoiceEntity(10L, "APPROVED");
        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(invoiceRepository.countByInvoiceNoStartingWith(any(String.class)))
                .thenReturn(5L);
        when(invoiceRepository.save(any(ScrmInvoiceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.issueInvoice(10L, "issuer");

        ArgumentCaptor<ScrmInvoiceEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceEntity.class);
        verify(invoiceRepository, times(1)).save(captor.capture());
        ScrmInvoiceEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ISSUED");
        assertThat(saved.getInvoiceCode()).startsWith("044");
        assertThat(saved.getInvoiceNumber()).isNotBlank();
        assertThat(saved.getCheckCode()).isNotBlank();
        assertThat(saved.getInvoiceDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getIssuedBy()).isEqualTo("issuer");
    }

    @Test
    @DisplayName("issueInvoice: 非 APPROVED 状态不可开具抛 BAD_REQUEST")
    void issueInvoice_invalidStatus() {
        ScrmInvoiceEntity entity = buildInvoiceEntity(10L, "PENDING");
        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.issueInvoice(10L, "issuer"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 APPROVED 可开具");
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendInvoice: ISSUED → SENT, 交付状态置 SENT")
    void sendInvoice_success() throws ScrmException {
        ScrmInvoiceEntity entity = buildInvoiceEntity(10L, "ISSUED");
        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(invoiceRepository.save(any(ScrmInvoiceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.sendInvoice(10L);

        ArgumentCaptor<ScrmInvoiceEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceEntity.class);
        verify(invoiceRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SENT");
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo("SENT");
        assertThat(captor.getValue().getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("markDelivered: SENT → RECEIVED, 交付状态置 DELIVERED")
    void markDelivered_success() throws ScrmException {
        ScrmInvoiceEntity entity = buildInvoiceEntity(10L, "SENT");
        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(invoiceRepository.save(any(ScrmInvoiceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.markDelivered(10L);

        ArgumentCaptor<ScrmInvoiceEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceEntity.class);
        verify(invoiceRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("RECEIVED");
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("voidInvoice: ISSUED → VOIDED, 记录作废原因")
    void voidInvoice_success() throws ScrmException {
        ScrmInvoiceEntity entity = buildInvoiceEntity(10L, "ISSUED");
        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(invoiceRepository.save(any(ScrmInvoiceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.voidInvoice(10L, "信息错误", "admin");

        ArgumentCaptor<ScrmInvoiceEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceEntity.class);
        verify(invoiceRepository, times(1)).save(captor.capture());
        ScrmInvoiceEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("VOIDED");
        assertThat(saved.getVoidReason()).isEqualTo("信息错误");
        assertThat(saved.getVoidedBy()).isEqualTo("admin");
        assertThat(saved.getVoidedAt()).isNotNull();
    }

    @Test
    @DisplayName("voidInvoice: PENDING 状态不允许作废抛 BAD_REQUEST")
    void voidInvoice_invalidStatus() {
        ScrmInvoiceEntity entity = buildInvoiceEntity(10L, "PENDING");
        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.voidInvoice(10L, "原因", "admin"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("当前状态不允许作废");
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("redFlush: 创建红冲发票金额取负, 原发票状态置 RED_FLUSHED")
    void redFlush_success() throws ScrmException {
        ScrmInvoiceEntity original = buildInvoiceEntity(10L, "ISSUED");
        original.setAmount(1000d);
        original.setTaxAmount(130d);
        original.setTotalAmount(1130d);
        original.setActualAmount(1130d);
        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(original));
        when(invoiceRepository.countByInvoiceNoStartingWith(any(String.class)))
                .thenReturn(0L);
        when(invoiceRepository.countByApplicationNoStartingWith(any(String.class)))
                .thenReturn(0L);
        when(invoiceRepository.save(any(ScrmInvoiceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmInvoiceRedFlushDto dto = new ScrmInvoiceRedFlushDto();
        dto.setInvoiceId(10L);
        dto.setReason("开票有误");
        dto.setRedFlushedBy("admin");
        ScrmInvoiceDto result = service.redFlush(dto);

        // 验证红冲发票: 金额取负, category=RED, originalInvoiceId 指向原发票
        ArgumentCaptor<ScrmInvoiceEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceEntity.class);
        verify(invoiceRepository, times(2)).save(captor.capture());
        ScrmInvoiceEntity red = captor.getAllValues().get(0); // 第一次保存红冲发票
        assertThat(red.getInvoiceCategory()).isEqualTo("RED");
        assertThat(red.getAmount()).isEqualTo(-1000.0);
        assertThat(red.getTaxAmount()).isEqualTo(-130.0);
        assertThat(red.getTotalAmount()).isEqualTo(-1130.0);
        assertThat(red.getStatus()).isEqualTo("ISSUED");
        assertThat(red.getOriginalInvoiceId()).isEqualTo(10L);
        // 第二次保存原发票: 状态置 RED_FLUSHED
        ScrmInvoiceEntity updatedOriginal = captor.getAllValues().get(1);
        assertThat(updatedOriginal.getStatus()).isEqualTo("RED_FLUSHED");
        assertThat(updatedOriginal.getRedFlushInvoiceId()).isEqualTo(red.getId());
        assertThat(result.getInvoiceCategory()).isEqualTo("RED");
    }

    @Test
    @DisplayName("redFlush: 红冲原因为空抛 BAD_REQUEST")
    void redFlush_blankReason() {
        ScrmInvoiceRedFlushDto dto = new ScrmInvoiceRedFlushDto();
        dto.setInvoiceId(10L);
        dto.setReason("");

        assertThatThrownBy(() -> service.redFlush(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("红冲原因不能为空");
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("generateInvoiceNo: FP + 年月日 + 4 位序号")
    void generateInvoiceNo_format() {
        when(invoiceRepository.countByInvoiceNoStartingWith(any(String.class)))
                .thenReturn(3L);

        String invoiceNo = service.generateInvoiceNo();

        assertThat(invoiceNo).startsWith("FP");
        // 序号 = 3 + 1 = 4 → 0004
        assertThat(invoiceNo).endsWith("0004");
    }

    @Test
    @DisplayName("createTemplate: 写入账号 ID 与默认值后持久化")
    void createTemplate_success() throws ScrmException {
        ScrmInvoiceTemplateDto dto = new ScrmInvoiceTemplateDto();
        dto.setTemplateName("通用发票模板");
        dto.setTemplateCode("TPL_001");
        dto.setInvoiceType("GENERAL");
        when(templateRepository.findByTemplateCode("TPL_001")).thenReturn(Optional.empty());
        when(templateRepository.save(any(ScrmInvoiceTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmInvoiceTemplateDto result = service.createTemplate(dto);

        ArgumentCaptor<ScrmInvoiceTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceTemplateEntity.class);
        verify(templateRepository, times(1)).save(captor.capture());
        ScrmInvoiceTemplateEntity saved = captor.getValue();
        // defaultTaxRate 缺省时填 0.13
        assertThat(saved.getDefaultTaxRate()).isEqualTo(0.13);
        // enabled 缺省时填 true
        assertThat(saved.getEnabled()).isTrue();
        // usageCount 初值为 0
        assertThat(saved.getUsageCount()).isZero();
        assertThat(result.getTemplateCode()).isEqualTo("TPL_001");
    }

    @Test
    @DisplayName("createTemplate: 模板编码重复抛 CONFLICT")
    void createTemplate_duplicateCode() {
        ScrmInvoiceTemplateDto dto = new ScrmInvoiceTemplateDto();
        dto.setTemplateName("通用发票模板");
        dto.setTemplateCode("TPL_001");
        dto.setInvoiceType("GENERAL");
        ScrmInvoiceTemplateEntity existing = new ScrmInvoiceTemplateEntity();
        existing.setTemplateCode("TPL_001");
        when(templateRepository.findByTemplateCode("TPL_001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模板编码已存在");
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("enableTemplate: 设置 enabled=true 并持久化")
    void enableTemplate_success() throws ScrmException {
        ScrmInvoiceTemplateEntity entity = new ScrmInvoiceTemplateEntity();
        entity.setId(10L);
        entity.setEnabled(false);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(templateRepository.save(any(ScrmInvoiceTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.enableTemplate(10L);

        ArgumentCaptor<ScrmInvoiceTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceTemplateEntity.class);
        verify(templateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableTemplate: 设置 enabled=false 并持久化")
    void disableTemplate_success() throws ScrmException {
        ScrmInvoiceTemplateEntity entity = new ScrmInvoiceTemplateEntity();
        entity.setId(10L);
        entity.setEnabled(true);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(templateRepository.save(any(ScrmInvoiceTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.disableTemplate(10L);

        ArgumentCaptor<ScrmInvoiceTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceTemplateEntity.class);
        verify(templateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }

    @Test
    @DisplayName("incrementUsage: 使用次数递增")
    void incrementUsage_success() throws ScrmException {
        ScrmInvoiceTemplateEntity entity = new ScrmInvoiceTemplateEntity();
        entity.setId(10L);
        entity.setUsageCount(5);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(templateRepository.save(any(ScrmInvoiceTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.incrementUsage(10L);

        ArgumentCaptor<ScrmInvoiceTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmInvoiceTemplateEntity.class);
        verify(templateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUsageCount()).isEqualTo(6);
    }

    
    @Test
    @DisplayName("listInvoices: 通过 Specification 分页查询")
    void listInvoices_pagination() {
        ScrmInvoiceEntity entity = buildInvoiceEntity(10L, "PENDING");
        when(invoiceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<ScrmInvoiceDto> result = service.listInvoices("GENERAL", null, "PENDING",
                null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getInvoiceType()).isEqualTo("GENERAL");
        verify(invoiceRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getInvoiceStats: 聚合发票总数/各状态/总金额")
    void getInvoiceStats_success() {
        ScrmInvoiceEntity e1 = buildInvoiceEntity(10L, "PENDING");
        e1.setAmount(1000d);
        e1.setTaxAmount(130d);
        e1.setActualAmount(1130d);
        ScrmInvoiceEntity e2 = buildInvoiceEntity(11L, "ISSUED");
        e2.setAmount(2000d);
        e2.setTaxAmount(260d);
        e2.setActualAmount(2260d);
        when(invoiceRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(e1, e2));

        java.util.Map<String, Object> stats = service.getInvoiceStats(null, null);

        assertThat(stats.get("total")).isEqualTo(2);
        // totalAmount = 1000 + 2000 = 3000
        assertThat(stats.get("totalAmount")).isEqualTo(3000.0);
        // totalTaxAmount = 130 + 260 = 390
        assertThat(stats.get("totalTaxAmount")).isEqualTo(390.0);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Long> byStatus = (java.util.Map<String, Long>) stats.get("byStatus");
        assertThat(byStatus.get("PENDING")).isEqualTo(1L);
        assertThat(byStatus.get("ISSUED")).isEqualTo(1L);
    }

    @Test
    @DisplayName("getVoidRate: 计算作废率 = 作废数 / 总数")
    void getVoidRate_success() {
        ScrmInvoiceEntity e1 = buildInvoiceEntity(10L, "VOIDED");
        ScrmInvoiceEntity e2 = buildInvoiceEntity(11L, "ISSUED");
        ScrmInvoiceEntity e3 = buildInvoiceEntity(12L, "ISSUED");
        ScrmInvoiceEntity e4 = buildInvoiceEntity(13L, "ISSUED");
        when(invoiceRepository.findAll()).thenReturn(List.of(e1, e2, e3, e4));

        java.util.Map<String, Object> stats = service.getVoidRate();

        assertThat(stats.get("total")).isEqualTo(4L);
        assertThat(stats.get("voidedCount")).isEqualTo(1L);
        // voidRate = 1/4*100 = 25.0
        assertThat(stats.get("voidRate")).isEqualTo(25.0);
    }
}
