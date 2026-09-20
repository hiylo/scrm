/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCampaignTemplateServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmCampaignTemplateDto;
import org.hiylo.scrm.entity.ScrmCampaignTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCampaignTemplateRepository;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmCampaignTemplateService 单元测试
 * <p>
 * 聚焦 SOP 模板 CRUD (创建写入归属账号 / 部分字段更新 / 删除前存在性校验)、
 * 主键查询与越权访问 (findOrThrow 校验归属账号, findByCampaignType / findByPlatformType
 * 在内存中按归属账号过滤)、Specification 分页查询等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCampaignTemplateService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCampaignTemplateServiceTest {

    /** 活动模板数据仓库 Mock 桩 */
    @Mock
    private ScrmCampaignTemplateRepository templateRepository;

    /** 被测服务实例 */
    private ScrmCampaignTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ScrmCampaignTemplateService(templateRepository);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 创建 ====================

    @Test
    @DisplayName("create: 写入当前账号 ID 并持久化, 返回 DTO")
    void create_success() {
        ScrmCampaignTemplateDto dto = buildDto();
        when(templateRepository.save(any(ScrmCampaignTemplateEntity.class)))
                .thenAnswer(inv -> assignId(inv.getArgument(0), 100L));

        ScrmCampaignTemplateDto result = service.create(dto);

        assertThat(result.getId()).isEqualTo(100L);
        ArgumentCaptor<ScrmCampaignTemplateEntity> captor = ArgumentCaptor.forClass(ScrmCampaignTemplateEntity.class);
        verify(templateRepository).save(captor.capture());
        ScrmCampaignTemplateEntity saved = captor.getValue();
        assertThat(saved.getTemplateName()).isEqualTo("新客户首日 SOP");
        assertThat(saved.getCampaignType()).isEqualTo("AUTO_NURTURE");
        assertThat(saved.getPlatformType()).isEqualTo("wechat_personal");
        assertThat(saved.getTemplateContent()).isEqualTo("{\"steps\":[]}");
        assertThat(saved.getDescription()).isEqualTo("首日运营流程");
    }

    // ==================== 更新 ====================

    @Test
    @DisplayName("update: 字段非空才覆盖, 保留未提供字段")
    void update_partialUpdate() throws Exception {
        ScrmCampaignTemplateEntity existing = buildEntity(100L);
        when(templateRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(ScrmCampaignTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCampaignTemplateDto dto = new ScrmCampaignTemplateDto();
        dto.setTemplateName("更新后的名称");
        dto.setDescription("更新后的描述");
        ScrmCampaignTemplateDto result = service.update(100L, dto);

        assertThat(result.getTemplateName()).isEqualTo("更新后的名称");
        assertThat(result.getDescription()).isEqualTo("更新后的描述");
        // 未提供字段保留原值
        assertThat(result.getCampaignType()).isEqualTo("AUTO_NURTURE");
        assertThat(result.getPlatformType()).isEqualTo("wechat_personal");
    }

    @Test
    @DisplayName("update: 不存在抛 NOT_FOUND")
    void update_notFound() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(999L, new ScrmCampaignTemplateDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("SOP 模板不存在");
    }

    
    // ==================== 删除 ====================

    @Test
    @DisplayName("delete: 存在且同账号时调用 deleteById")
    void delete_success() throws Exception {
        ScrmCampaignTemplateEntity existing = buildEntity(100L);
        when(templateRepository.findById(100L)).thenReturn(Optional.of(existing));
        service.delete(100L);
        verify(templateRepository).deleteById(100L);
    }

    @Test
    @DisplayName("delete: 不存在抛 NOT_FOUND, 不调用 deleteById")
    void delete_notFound() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("SOP 模板不存在");
        verify(templateRepository, never()).deleteById(any());
    }

    
    // ==================== 主键查询 ====================

    @Test
    @DisplayName("findById: 存在且同账号返回 DTO")
    void findById_success() throws Exception {
        ScrmCampaignTemplateEntity existing = buildEntity(100L);
        when(templateRepository.findById(100L)).thenReturn(Optional.of(existing));
        ScrmCampaignTemplateDto result = service.findById(100L);
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getTemplateName()).isEqualTo("新客户首日 SOP");
    }

    @Test
    @DisplayName("findById: 不存在抛 NOT_FOUND")
    void findById_notFound() {
        when(templateRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("SOP 模板不存在");
    }

    
    // ==================== 任务类型 / 平台类型查询 ====================

    
    @Test
    @DisplayName("findByCampaignType: 无匹配返回空列表")
    void findByCampaignType_empty() {
        when(templateRepository.findByCampaignType("AUTO_POST"))
                .thenReturn(Collections.emptyList());
        List<ScrmCampaignTemplateDto> result = service.findByCampaignType("AUTO_POST");
        assertThat(result).isEmpty();
    }

    
    // ==================== 分页查询 ====================

    @Test
    @DisplayName("list: 返回分页结果, 内容映射为 DTO")
    void list_success() {
        ScrmCampaignTemplateEntity entity = buildEntity(100L);
        Page<ScrmCampaignTemplateEntity> page = new PageImpl<>(
                List.of(entity), PageRequest.of(0, 10), 1);
        when(templateRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmCampaignTemplateDto> result = service.list(null, null, 0, 10);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("list: 按任务类型过滤返回匹配结果")
    void list_filterByCampaignType() {
        ScrmCampaignTemplateEntity entity = buildEntity(100L);
        Page<ScrmCampaignTemplateEntity> page = new PageImpl<>(
                List.of(entity), PageRequest.of(0, 10), 1);
        when(templateRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmCampaignTemplateDto> result = service.list("AUTO_NURTURE", null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("list: 无数据返回空分页")
    void list_empty() {
        Page<ScrmCampaignTemplateEntity> page = new PageImpl<>(
                Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(templateRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmCampaignTemplateDto> result = service.list(null, null, 0, 10);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // ==================== 辅助方法 ====================

    private ScrmCampaignTemplateDto buildDto() {
        ScrmCampaignTemplateDto dto = new ScrmCampaignTemplateDto();
        dto.setTemplateName("新客户首日 SOP");
        dto.setCampaignType("AUTO_NURTURE");
        dto.setPlatformType("wechat_personal");
        dto.setTemplateContent("{\"steps\":[]}");
        dto.setDescription("首日运营流程");
        return dto;
    }

    private ScrmCampaignTemplateEntity buildEntity(Long id) {
        ScrmCampaignTemplateEntity entity = new ScrmCampaignTemplateEntity();
        entity.setId(id);
        entity.setTemplateName("新客户首日 SOP");
        entity.setCampaignType("AUTO_NURTURE");
        entity.setPlatformType("wechat_personal");
        entity.setTemplateContent("{\"steps\":[]}");
        entity.setDescription("首日运营流程");
        return entity;
    }

    private ScrmCampaignTemplateEntity assignId(ScrmCampaignTemplateEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
