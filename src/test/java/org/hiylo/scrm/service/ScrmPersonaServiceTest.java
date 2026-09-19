/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPersonaServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmPersonaDto;
import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.hiylo.scrm.entity.ScrmPersonaEntity;
import org.hiylo.scrm.execution.TaskExecutionService;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAccountRepository;
import org.hiylo.scrm.repository.ScrmPersonaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmPersonaService 单元测试
 * <p>
 * 聚焦人设业务字段管理 (创建 / 更新 / 查询 / 删除 / 分页)、参数校验 (personaId/nNickname/gender)、
 * 唯一性校验 (同账号内 personaId 唯一)、越权隔离、删除前账号引用检查,
 * 以及执行引擎扩展点失败容忍策略等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmPersonaService 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ScrmPersonaServiceTest {

    /** 人设业务字段仓库 Mock */
    @Mock
    private ScrmPersonaRepository personaRepository;
    /** 营销任务执行引擎扩展点 Mock */
    @Mock
    private TaskExecutionService taskExecutionService;
    /** 账号仓库 Mock */
    @Mock
    private ScrmAccountRepository accountRepository;

    /** 被测服务实例 */
    private ScrmPersonaService service;

    @BeforeEach
    void setUp() {
        service = new ScrmPersonaService(personaRepository, taskExecutionService, accountRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的人设实体 (用于 findByPersonaId 返回)
     */
    private ScrmPersonaEntity buildEntity(String personaId, String nickname) {
        ScrmPersonaEntity entity = new ScrmPersonaEntity();
        entity.setId(10L);
        entity.setPersonaId(personaId);
        entity.setNickname(nickname);
        entity.setGender("MALE");
        entity.setAgeRange("25-30");
        entity.setRegion("深圳");
        entity.setSignature("专注销售");
        entity.setStyleTags("[\"热情\"]");
        entity.setScriptTemplateIds("[\"tpl_001\"]");
        entity.setTags("[\"VIP\"]");
        entity.setVersion(0L);
        return entity;
    }

    /**
     * 构造创建人设入参 DTO
     */
    private ScrmPersonaDto buildCreateDto() {
        ScrmPersonaDto dto = new ScrmPersonaDto();
        dto.setPersonaId("p_001");
        dto.setAccountId(100L);
        dto.setNickname("小薇");
        dto.setAvatarUrl("https://cdn/avatar.png");
        dto.setGender("FEMALE");
        dto.setAgeRange("18-24");
        dto.setRegion("广州");
        dto.setSignature("热爱生活");
        dto.setStyleTags("[\"活泼\"]");
        dto.setScriptTemplateIds("[\"tpl_002\"]");
        dto.setTags("[\"新客\"]");
        return dto;
    }

    @Test
    @DisplayName("createPersona: 写入账号 ID 与业务字段后持久化, 并同步调用扩展点确认执行侧 Persona")
    void createPersona_success() throws ScrmException {
        ScrmPersonaDto dto = buildCreateDto();
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.empty());
        when(personaRepository.save(any(ScrmPersonaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        // 账号存在, 提供 platformType
        ScrmAccountEntity account = new ScrmAccountEntity();
        account.setId(100L);
        account.setPlatformType("wework");
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account));
        when(taskExecutionService.ensureExecutionPersona(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(2001L);

        ScrmPersonaDto result = service.createPersona(dto);

        ArgumentCaptor<ScrmPersonaEntity> captor =
                ArgumentCaptor.forClass(ScrmPersonaEntity.class);
        verify(personaRepository, times(1)).save(captor.capture());
        ScrmPersonaEntity saved = captor.getValue();
        assertThat(saved.getPersonaId()).isEqualTo("p_001");
        assertThat(saved.getAccountId()).isEqualTo(100L);
        assertThat(saved.getNickname()).isEqualTo("小薇");
        assertThat(saved.getGender()).isEqualTo("FEMALE");
        assertThat(result.getPersonaId()).isEqualTo("p_001");
        // 验证扩展点调用参数映射 (personaId / platformType / nickname / avatarUrl)
        verify(taskExecutionService, times(1))
                .ensureExecutionPersona("p_001", "wework", "小薇", "https://cdn/avatar.png");
    }

    @Test
    @DisplayName("createPersona: dto 为空抛 BAD_REQUEST")
    void createPersona_nullDto() {
        assertThatThrownBy(() -> service.createPersona(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("人设参数不能为空");
        verify(personaRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPersona: personaId 为空抛 BAD_REQUEST")
    void createPersona_blankPersonaId() {
        ScrmPersonaDto dto = buildCreateDto();
        dto.setPersonaId("  ");

        assertThatThrownBy(() -> service.createPersona(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("人设 ID 不能为空");
        verify(personaRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPersona: nickname 为空抛 BAD_REQUEST")
    void createPersona_blankNickname() {
        ScrmPersonaDto dto = buildCreateDto();
        dto.setNickname("");

        assertThatThrownBy(() -> service.createPersona(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("人设昵称不能为空");
        verify(personaRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPersona: gender 非法抛 BAD_REQUEST")
    void createPersona_invalidGender() {
        ScrmPersonaDto dto = buildCreateDto();
        dto.setGender("OTHER");

        assertThatThrownBy(() -> service.createPersona(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("性别值非法");
        verify(personaRepository, never()).save(any());
    }

    
    
    @Test
    @DisplayName("createPersona: 扩展点抛异常时不阻断业务侧创建 (失败容忍)")
    void createPersona_executionExceptionTolerated() throws ScrmException {
        ScrmPersonaDto dto = buildCreateDto();
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.empty());
        when(personaRepository.save(any(ScrmPersonaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ScrmAccountEntity account = new ScrmAccountEntity();
        account.setId(100L);
        account.setPlatformType("wework");
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account));
        when(taskExecutionService.ensureExecutionPersona(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("执行引擎不可用"));

        ScrmPersonaDto result = service.createPersona(dto);

        assertThat(result.getPersonaId()).isEqualTo("p_001");
        verify(personaRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("createPersona: 扩展点返回 null (无执行引擎) 时不阻断业务侧创建")
    void createPersona_noExecutionEngine() throws ScrmException {
        ScrmPersonaDto dto = buildCreateDto();
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.empty());
        when(personaRepository.save(any(ScrmPersonaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ScrmAccountEntity account = new ScrmAccountEntity();
        account.setId(100L);
        account.setPlatformType("wework");
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account));
        when(taskExecutionService.ensureExecutionPersona(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        ScrmPersonaDto result = service.createPersona(dto);

        assertThat(result.getPersonaId()).isEqualTo("p_001");
        verify(personaRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("updatePersona: 字段非空才覆盖, 保留未提供字段原值")
    void updatePersona_partialUpdate() throws ScrmException {
        ScrmPersonaEntity entity = buildEntity("p_001", "小薇");
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.of(entity));
        when(personaRepository.save(any(ScrmPersonaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ScrmPersonaDto dto = new ScrmPersonaDto();
        dto.setNickname("更新后的昵称");
        dto.setRegion("上海");
        ScrmPersonaDto result = service.updatePersona("p_001", dto);

        assertThat(result.getNickname()).isEqualTo("更新后的昵称");
        assertThat(result.getRegion()).isEqualTo("上海");
        // 未提供的字段保留原值
        assertThat(result.getGender()).isEqualTo("MALE");
        assertThat(result.getAgeRange()).isEqualTo("25-30");
        assertThat(result.getSignature()).isEqualTo("专注销售");
    }

    @Test
    @DisplayName("updatePersona: gender 非法抛 BAD_REQUEST")
    void updatePersona_invalidGender() {
        ScrmPersonaEntity entity = buildEntity("p_001", "小薇");
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.of(entity));

        ScrmPersonaDto dto = new ScrmPersonaDto();
        dto.setGender("INVALID");

        assertThatThrownBy(() -> service.updatePersona("p_001", dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("性别值非法");
        verify(personaRepository, never()).save(any());
    }

    @Test
    @DisplayName("updatePersona: 人设不存在抛 SCRM_PERSONA_NOT_FOUND")
    void updatePersona_notFound() {
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.empty());

        ScrmPersonaDto dto = new ScrmPersonaDto();
        dto.setNickname("更新");
        assertThatThrownBy(() -> service.updatePersona("p_001", dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("人设不存在");
        verify(personaRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("getPersona: 返回人设 DTO")
    void getPersona_success() throws ScrmException {
        ScrmPersonaEntity entity = buildEntity("p_001", "小薇");
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.of(entity));

        ScrmPersonaDto result = service.getPersona("p_001");

        assertThat(result.getPersonaId()).isEqualTo("p_001");
        assertThat(result.getNickname()).isEqualTo("小薇");
    }

    @Test
    @DisplayName("getPersona: 人设不存在抛 SCRM_PERSONA_NOT_FOUND")
    void getPersona_notFound() {
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPersona("p_001"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("人设不存在");
    }

    
    
    @Test
    @DisplayName("getPersonaByAccountId: 无匹配人设返回空列表")
    void getPersonaByAccountId_empty() {
        when(personaRepository.findByAccountId(100L)).thenReturn(Collections.emptyList());

        List<ScrmPersonaDto> result = service.getPersonaByAccountId(100L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deletePersona: 无账号引用时删除业务侧记录")
    void deletePersona_success() throws ScrmException {
        ScrmPersonaEntity entity = buildEntity("p_001", "小薇");
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.of(entity));
        when(accountRepository.findByPersonaId("p_001")).thenReturn(Collections.emptyList());

        service.deletePersona("p_001");

        verify(personaRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("deletePersona: 人设不存在抛 SCRM_PERSONA_NOT_FOUND")
    void deletePersona_notFound() {
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePersona("p_001"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("人设不存在");
        verify(personaRepository, never()).delete(any(ScrmPersonaEntity.class));
    }

    @Test
    @DisplayName("deletePersona: 仍有账号引用时抛 CONFLICT")
    void deletePersona_hasReferences() {
        ScrmPersonaEntity entity = buildEntity("p_001", "小薇");
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.of(entity));
        ScrmAccountEntity ref1 = new ScrmAccountEntity();
        ref1.setId(1L);
        ScrmAccountEntity ref2 = new ScrmAccountEntity();
        ref2.setId(2L);
        when(accountRepository.findByPersonaId("p_001")).thenReturn(List.of(ref1, ref2));

        assertThatThrownBy(() -> service.deletePersona("p_001"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("无法删除人设")
                .hasMessageContaining("2");
        verify(personaRepository, never()).delete(any(ScrmPersonaEntity.class));
    }

    
    @Test
    @DisplayName("listPersonas: 按账号过滤分页查询, 按创建时间倒序")
    void listPersonas_success() {
        ScrmPersonaEntity entity = buildEntity("p_001", "小薇");
        Page<ScrmPersonaEntity> page = new PageImpl<>(List.of(entity));
        when(personaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<ScrmPersonaDto> result = service.listPersonas(0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPersonaId()).isEqualTo("p_001");
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(personaRepository, times(1)).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("listPersonas: 无数据返回空页")
    void listPersonas_empty() {
        Page<ScrmPersonaEntity> page = new PageImpl<>(Collections.emptyList());
        when(personaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<ScrmPersonaDto> result = service.listPersonas(0, 10);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("createPersona: gender 为空时不校验, 直接持久化")
    void createPersona_nullGenderAllowed() throws ScrmException {
        ScrmPersonaDto dto = buildCreateDto();
        dto.setGender(null);
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.empty());
        when(personaRepository.save(any(ScrmPersonaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ScrmAccountEntity account = new ScrmAccountEntity();
        account.setId(100L);
        account.setPlatformType("wework");
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account));
        when(taskExecutionService.ensureExecutionPersona(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(2001L);

        ScrmPersonaDto result = service.createPersona(dto);

        ArgumentCaptor<ScrmPersonaEntity> captor =
                ArgumentCaptor.forClass(ScrmPersonaEntity.class);
        verify(personaRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getGender()).isNull();
        assertThat(result.getPersonaId()).isEqualTo("p_001");
    }

    @Test
    @DisplayName("createPersona: gender 为 UNKNOWN 合法值正常持久化")
    void createPersona_unknownGenderAllowed() throws ScrmException {
        ScrmPersonaDto dto = buildCreateDto();
        dto.setGender("UNKNOWN");
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.empty());
        when(personaRepository.save(any(ScrmPersonaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        ScrmAccountEntity account = new ScrmAccountEntity();
        account.setId(100L);
        account.setPlatformType("wework");
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account));
        when(taskExecutionService.ensureExecutionPersona(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(2001L);

        ScrmPersonaDto result = service.createPersona(dto);

        assertThat(result.getGender()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("updatePersona: accountId 非空时更新归属账号")
    void updatePersona_accountId() throws ScrmException {
        ScrmPersonaEntity entity = buildEntity("p_001", "小薇");
        entity.setAccountId(100L);
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.of(entity));
        when(personaRepository.save(any(ScrmPersonaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ScrmPersonaDto dto = new ScrmPersonaDto();
        dto.setAccountId(200L);
        ScrmPersonaDto result = service.updatePersona("p_001", dto);

        assertThat(result.getAccountId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("updatePersona: 全字段覆盖更新")
    void updatePersona_fullUpdate() throws ScrmException {
        ScrmPersonaEntity entity = buildEntity("p_001", "小薇");
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.of(entity));
        when(personaRepository.save(any(ScrmPersonaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ScrmPersonaDto dto = new ScrmPersonaDto();
        dto.setNickname("大薇");
        dto.setAvatarUrl("https://cdn/new.png");
        dto.setGender("MALE");
        dto.setAgeRange("30-35");
        dto.setRegion("北京");
        dto.setSignature("新签名");
        dto.setStyleTags("[\"专业\"]");
        dto.setScriptTemplateIds("[\"tpl_003\"]");
        dto.setTags("[\"老客\"]");
        dto.setAccountId(300L);
        ScrmPersonaDto result = service.updatePersona("p_001", dto);

        assertThat(result.getNickname()).isEqualTo("大薇");
        assertThat(result.getAvatarUrl()).isEqualTo("https://cdn/new.png");
        assertThat(result.getGender()).isEqualTo("MALE");
        assertThat(result.getAgeRange()).isEqualTo("30-35");
        assertThat(result.getRegion()).isEqualTo("北京");
        assertThat(result.getSignature()).isEqualTo("新签名");
        assertThat(result.getStyleTags()).isEqualTo("[\"专业\"]");
        assertThat(result.getScriptTemplateIds()).isEqualTo("[\"tpl_003\"]");
        assertThat(result.getTags()).isEqualTo("[\"老客\"]");
        assertThat(result.getAccountId()).isEqualTo(300L);
    }

    @Test
    @DisplayName("listPersonas: 分页参数透传 (第 2 页, 每页 20 条)")
    void listPersonas_pagination() {
        Page<ScrmPersonaEntity> page = new PageImpl<>(Collections.emptyList());
        when(personaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        service.listPersonas(1, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(personaRepository, times(1)).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("createPersona: 未关联 accountId 时 platformType 传 null, 扩展点仍被调用")
    void createPersona_withoutAccountId() throws ScrmException {
        ScrmPersonaDto dto = buildCreateDto();
        dto.setAccountId(null);
        when(personaRepository.findByPersonaId("p_001")).thenReturn(Optional.empty());
        when(personaRepository.save(any(ScrmPersonaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskExecutionService.ensureExecutionPersona(anyString(), any(), anyString(), anyString()))
                .thenReturn(2002L);

        ScrmPersonaDto result = service.createPersona(dto);

        assertThat(result.getPersonaId()).isEqualTo("p_001");
        // platformType 为 null (无账号关联)
        verify(taskExecutionService, times(1))
                .ensureExecutionPersona(eq("p_001"), eq(null), eq("小薇"), eq("https://cdn/avatar.png"));
    }
}
