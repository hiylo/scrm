/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmMessageTemplateDto;
import org.hiylo.scrm.entity.ScrmMessageTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmMessageTemplateRepository;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmMessageTemplateService 单元测试
 * <p>
 * 聚焦消息模板 CRUD / 名称唯一性校验 / 变量自动提取 / 启用禁用 /
 * 变量插值渲染 (含缺失变量替换为空串) 与数据隔离等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmMessageTemplateService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmMessageTemplateServiceTest {

    /** 消息模板仓库 Mock */
    @Mock
    private ScrmMessageTemplateRepository messageTemplateRepository;

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测服务实例 */
    private ScrmMessageTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ScrmMessageTemplateService(messageTemplateRepository, objectMapper);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造模板 DTO
     */
    private ScrmMessageTemplateDto buildDto() {
        ScrmMessageTemplateDto dto = new ScrmMessageTemplateDto();
        dto.setTemplateName("问候模板");
        dto.setCategory("greeting");
        dto.setContent("你好 {{nickname}}，欢迎来到 {{platformType}}！");
        return dto;
    }

    /**
     * 构造已持久化的模板实体
     */
    private ScrmMessageTemplateEntity buildEntity(Long id, String name, boolean enabled) {
        ScrmMessageTemplateEntity entity = new ScrmMessageTemplateEntity();
        entity.setId(id);
        entity.setTemplateName(name);
        entity.setCategory("greeting");
        entity.setContent("你好 {{nickname}}，欢迎来到 {{platformType}}！");
        entity.setPlatformType("wechat");
        entity.setVariables("[\"nickname\",\"platformType\"]");
        entity.setEnabled(enabled);
        entity.setSortOrder(0);
        return entity;
    }

    // ==================== 创建模板 ====================

    @Test
    @DisplayName("createTemplate: 写入账号 ID 与默认值, 自动提取变量列表")
    void createTemplate_success() throws ScrmException {
        ScrmMessageTemplateDto dto = buildDto();
        when(messageTemplateRepository.findByTemplateName("问候模板"))
                .thenReturn(Optional.empty());
        when(messageTemplateRepository.save(any(ScrmMessageTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageTemplateEntity result = service.createTemplate(dto);

        ArgumentCaptor<ScrmMessageTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmMessageTemplateEntity.class);
        verify(messageTemplateRepository, times(1)).save(captor.capture());
        ScrmMessageTemplateEntity saved = captor.getValue();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getSortOrder()).isZero();
        assertThat(saved.getVariables()).contains("nickname").contains("platformType");
        assertThat(result.getTemplateName()).isEqualTo("问候模板");
    }

    @Test
    @DisplayName("createTemplate: 模板名称已存在抛 CONFLICT")
    void createTemplate_duplicateName() {
        ScrmMessageTemplateDto dto = buildDto();
        when(messageTemplateRepository.findByTemplateName("问候模板"))
                .thenReturn(Optional.of(buildEntity(1L, "问候模板", true)));

        assertThatThrownBy(() -> service.createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模板名称已存在");
        verify(messageTemplateRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTemplate: dto 为空抛 BAD_REQUEST")
    void createTemplate_nullDto() {
        assertThatThrownBy(() -> service.createTemplate(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模板参数不能为空");
        verify(messageTemplateRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTemplate: 模板名称为空抛 BAD_REQUEST")
    void createTemplate_blankName() {
        ScrmMessageTemplateDto dto = buildDto();
        dto.setTemplateName("  ");

        assertThatThrownBy(() -> service.createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模板名称不能为空");
        verify(messageTemplateRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTemplate: 模板内容为空抛 BAD_REQUEST")
    void createTemplate_blankContent() {
        ScrmMessageTemplateDto dto = buildDto();
        dto.setContent("");

        assertThatThrownBy(() -> service.createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模板内容不能为空");
        verify(messageTemplateRepository, never()).save(any());
    }

    // ==================== 更新模板 ====================

    @Test
    @DisplayName("updateTemplate: content 变更时重新提取变量列表")
    void updateTemplate_contentChangeReextractesVariables() throws ScrmException {
        ScrmMessageTemplateEntity entity = buildEntity(10L, "问候模板", true);
        when(messageTemplateRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(messageTemplateRepository.save(any(ScrmMessageTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmMessageTemplateDto dto = new ScrmMessageTemplateDto();
        dto.setContent("你好 {{customerName}}，订单 {{orderNo}} 已发货");

        service.updateTemplate(10L, dto);

        ArgumentCaptor<ScrmMessageTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmMessageTemplateEntity.class);
        verify(messageTemplateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("你好 {{customerName}}，订单 {{orderNo}} 已发货");
        assertThat(captor.getValue().getVariables()).contains("customerName").contains("orderNo");
    }

    @Test
    @DisplayName("updateTemplate: 模板名称被其他模板占用抛 CONFLICT")
    void updateTemplate_nameConflictWithOther() {
        ScrmMessageTemplateEntity entity = buildEntity(10L, "旧名称", true);
        ScrmMessageTemplateEntity other = buildEntity(20L, "新名称", true);
        when(messageTemplateRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(messageTemplateRepository.findByTemplateName("新名称"))
                .thenReturn(Optional.of(other));

        ScrmMessageTemplateDto dto = new ScrmMessageTemplateDto();
        dto.setTemplateName("新名称");

        assertThatThrownBy(() -> service.updateTemplate(10L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模板名称已被其他模板占用");
        verify(messageTemplateRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTemplate: 模板不存在抛 NOT_FOUND")
    void updateTemplate_notFound() {
        when(messageTemplateRepository.findById(10L)).thenReturn(Optional.empty());

        ScrmMessageTemplateDto dto = new ScrmMessageTemplateDto();
        dto.setTemplateName("新名称");
        assertThatThrownBy(() -> service.updateTemplate(10L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息模板不存在");
        verify(messageTemplateRepository, never()).save(any());
    }

    // ==================== 删除 / 查询 ====================

    @Test
    @DisplayName("deleteTemplate: 删除成功调用 deleteById")
    void deleteTemplate_success() throws ScrmException {
        ScrmMessageTemplateEntity entity = buildEntity(10L, "问候模板", true);
        when(messageTemplateRepository.findById(10L)).thenReturn(Optional.of(entity));

        service.deleteTemplate(10L);

        verify(messageTemplateRepository, times(1)).deleteById(10L);
    }

    @Test
    @DisplayName("getTemplate: 模板不存在抛 NOT_FOUND")
    void getTemplate_notFound() {
        when(messageTemplateRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplate(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息模板不存在");
    }

    
    @Test
    @DisplayName("listTemplates: 通过 Specification 分页查询")
    void listTemplates_usesSpecification() {
        Page<ScrmMessageTemplateEntity> page = new PageImpl<>(Collections.emptyList());
        when(messageTemplateRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmMessageTemplateEntity> result = service.listTemplates("greeting", true, "问候", 0, 10);

        verify(messageTemplateRepository, times(1))
                .findAll(any(Specification.class), any(Pageable.class));
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("getApplicableTemplates: 调用 repository 按账号与平台查询")
    void getApplicableTemplates_delegatesToRepository() {
        List<ScrmMessageTemplateEntity> templates = List.of(buildEntity(1L, "模板A", true));
        when(messageTemplateRepository.findApplicableTemplates("wechat"))
                .thenReturn(templates);

        List<ScrmMessageTemplateEntity> result = service.getApplicableTemplates("wechat");

        assertThat(result).hasSize(1);
        verify(messageTemplateRepository, times(1)).findApplicableTemplates("wechat");
    }

    // ==================== 启用 / 禁用 ====================

    @Test
    @DisplayName("enableTemplate: 设置 enabled=true 并持久化")
    void enableTemplate_success() throws ScrmException {
        ScrmMessageTemplateEntity entity = buildEntity(10L, "问候模板", false);
        when(messageTemplateRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(messageTemplateRepository.save(any(ScrmMessageTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.enableTemplate(10L);

        ArgumentCaptor<ScrmMessageTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmMessageTemplateEntity.class);
        verify(messageTemplateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableTemplate: 设置 enabled=false 并持久化")
    void disableTemplate_success() throws ScrmException {
        ScrmMessageTemplateEntity entity = buildEntity(10L, "问候模板", true);
        when(messageTemplateRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(messageTemplateRepository.save(any(ScrmMessageTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.disableTemplate(10L);

        ArgumentCaptor<ScrmMessageTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmMessageTemplateEntity.class);
        verify(messageTemplateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }

    // ==================== 模板渲染 ====================

    @Test
    @DisplayName("renderTemplate: 加载模板并替换变量为实际值")
    void renderTemplate_success() throws ScrmException {
        ScrmMessageTemplateEntity entity = buildEntity(10L, "问候模板", true);
        when(messageTemplateRepository.findById(10L)).thenReturn(Optional.of(entity));

        String result = service.renderTemplate(10L, Map.of("nickname", "Alice", "platformType", "wechat"));

        assertThat(result).isEqualTo("你好 Alice，欢迎来到 wechat！");
    }

    @Test
    @DisplayName("renderContent: 静态方法替换变量为实际值")
    void renderContent_replacesVariables() {
        String content = "Hello {{nickname}}, welcome to {{platformType}}!";
        Map<String, String> vars = Map.of("nickname", "Alice", "platformType", "wechat");

        String result = ScrmMessageTemplateService.renderContent(content, vars);

        assertThat(result).isEqualTo("Hello Alice, welcome to wechat!");
    }

    @Test
    @DisplayName("renderContent: 缺失变量替换为空字符串")
    void renderContent_missingVariableReplacedWithEmpty() {
        String content = "Hello {{nickname}}, your code is {{code}}";
        Map<String, String> vars = Map.of("nickname", "Alice");

        String result = ScrmMessageTemplateService.renderContent(content, vars);

        assertThat(result).isEqualTo("Hello Alice, your code is ");
    }

    @Test
    @DisplayName("renderContent: variables 为 null 时全部替换为空字符串")
    void renderContent_nullVariablesAllEmpty() {
        String content = "Hello {{nickname}}";

        String result = ScrmMessageTemplateService.renderContent(content, null);

        assertThat(result).isEqualTo("Hello ");
    }

    @Test
    @DisplayName("renderContent: content 为 null 时返回 null")
    void renderContent_nullContentReturnsNull() {
        assertThat(ScrmMessageTemplateService.renderContent(null, null)).isNull();
    }

    @Test
    @DisplayName("renderContent: 无变量占位符时原样返回")
    void renderContent_noPlaceholders() {
        String content = "这是一条固定消息";

        String result = ScrmMessageTemplateService.renderContent(content, Map.of("nickname", "Alice"));

        assertThat(result).isEqualTo("这是一条固定消息");
    }
}
