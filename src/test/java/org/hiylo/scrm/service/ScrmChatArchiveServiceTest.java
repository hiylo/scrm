/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChatArchiveServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmArchiveRuleDto;
import org.hiylo.scrm.dto.ScrmChatArchiveDto;
import org.hiylo.scrm.entity.ScrmArchiveRuleEntity;
import org.hiylo.scrm.entity.ScrmChatArchiveEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmArchiveRuleRepository;
import org.hiylo.scrm.repository.ScrmChatArchiveRepository;
import org.hiylo.scrm.vo.ChatArchiveStatsVo;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmChatArchiveService 单元测试
 * <p>
 * 聚焦会话存档消息归档 (参数校验 / 默认值填充 qualityFlag/qualityFlag/archiveSource)、
 * 归档查询 (不存在抛 NOT_FOUND)、存档统计 (总数 / 质量分布)、归档规则管理
 * (创建校验 / 唯一性校验 / 默认值填充 / 启停切换 / 删除) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmChatArchiveService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmChatArchiveServiceTest {

    /** 会话存档数据仓库 Mock 桩 */
    @Mock
    private ScrmChatArchiveRepository archiveRepository;
    /** 存档规则数据仓库 Mock 桩 */
    @Mock
    private ScrmArchiveRuleRepository ruleRepository;

    /** 被测服务实例 */
    private ScrmChatArchiveService service;

    @BeforeEach
    void setUp() {
        service = new ScrmChatArchiveService(archiveRepository, ruleRepository);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 消息归档 ====================

    @Test
    @DisplayName("archiveMessage: 成功归档, qualityFlag 缺省 NORMAL, archiveSource 缺省 AUTO")
    void archiveMessage_success_defaultsFilled() throws Exception {
        ScrmChatArchiveDto dto = buildArchiveDto();
        dto.setQualityFlag(null);
        dto.setArchiveSource(null);
        when(archiveRepository.save(any(ScrmChatArchiveEntity.class)))
                .thenAnswer(inv -> assignArchiveId(inv.getArgument(0), 100L));

        ScrmChatArchiveEntity result = service.archiveMessage(dto);

        assertThat(result.getId()).isEqualTo(100L);
        ArgumentCaptor<ScrmChatArchiveEntity> captor = ArgumentCaptor.forClass(ScrmChatArchiveEntity.class);
        verify(archiveRepository).save(captor.capture());
        ScrmChatArchiveEntity saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(500L);
        assertThat(saved.getPlatformType()).isEqualTo("WECHAT");
        assertThat(saved.getDirection()).isEqualTo("INBOUND");
        assertThat(saved.getMessageType()).isEqualTo("TEXT");
        assertThat(saved.getContent()).isEqualTo("你好");
        assertThat(saved.getSentAt()).isEqualTo(LocalDateTime.of(2026, 8, 5, 10, 0));
        assertThat(saved.getArchivedAt()).isNotNull();
        assertThat(saved.getQualityFlag()).isEqualTo("NORMAL");
        assertThat(saved.getArchiveSource()).isEqualTo("AUTO");
    }

    @Test
    @DisplayName("archiveMessage: MANUAL 来源与 SENSITIVE 质量标记透传")
    void archiveMessage_manualAndSensitive() throws Exception {
        ScrmChatArchiveDto dto = buildArchiveDto();
        dto.setArchiveSource("MANUAL");
        dto.setQualityFlag("SENSITIVE");
        dto.setRiskLevel("HIGH");
        when(archiveRepository.save(any(ScrmChatArchiveEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmChatArchiveEntity result = service.archiveMessage(dto);

        assertThat(result.getArchiveSource()).isEqualTo("MANUAL");
        assertThat(result.getQualityFlag()).isEqualTo("SENSITIVE");
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("archiveMessage: accountId 为 null 抛 BAD_REQUEST")
    void archiveMessage_nullAccountId_badRequest() {
        ScrmChatArchiveDto dto = buildArchiveDto();
        dto.setAccountId(null);

        assertThatThrownBy(() -> service.archiveMessage(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归属账号 ID 不能为空");
        verify(archiveRepository, never()).save(any(ScrmChatArchiveEntity.class));
    }

    @Test
    @DisplayName("archiveMessage: platformType 为空抛 BAD_REQUEST")
    void archiveMessage_blankPlatformType_badRequest() {
        ScrmChatArchiveDto dto = buildArchiveDto();
        dto.setPlatformType("  ");

        assertThatThrownBy(() -> service.archiveMessage(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("平台类型不能为空");
    }

    @Test
    @DisplayName("archiveMessage: direction 为空抛 BAD_REQUEST")
    void archiveMessage_blankDirection_badRequest() {
        ScrmChatArchiveDto dto = buildArchiveDto();
        dto.setDirection(null);

        assertThatThrownBy(() -> service.archiveMessage(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息方向不能为空");
    }

    @Test
    @DisplayName("archiveMessage: sentAt 为 null 抛 BAD_REQUEST")
    void archiveMessage_nullSentAt_badRequest() {
        ScrmChatArchiveDto dto = buildArchiveDto();
        dto.setSentAt(null);

        assertThatThrownBy(() -> service.archiveMessage(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息发送时间不能为空");
    }

    @Test
    @DisplayName("getArchive: 归档不存在抛 NOT_FOUND")
    void getArchive_notFound() {
        when(archiveRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getArchive(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归档消息不存在");
    }

    @Test
    @DisplayName("getStats: 聚合总数与质量分布")
    void getStats_aggregates() {
        when(archiveRepository.count()).thenReturn(100L);
        when(archiveRepository.countByQualityFlag("NORMAL")).thenReturn(80L);
        when(archiveRepository.countByQualityFlag("SENSITIVE")).thenReturn(15L);
        when(archiveRepository.countByQualityFlag("VIOLATION")).thenReturn(5L);

        ChatArchiveStatsVo stats = service.getStats();

        assertThat(stats.getTotalArchived()).isEqualTo(100L);
        assertThat(stats.getNormalCount()).isEqualTo(80L);
        assertThat(stats.getSensitiveCount()).isEqualTo(15L);
        assertThat(stats.getViolationCount()).isEqualTo(5L);
        assertThat(stats.getQualityDistribution().get("NORMAL")).isEqualTo(80L);
        assertThat(stats.getQualityDistribution().get("SENSITIVE")).isEqualTo(15L);
        assertThat(stats.getQualityDistribution().get("VIOLATION")).isEqualTo(5L);
    }

    @Test
    @DisplayName("getArchivesByAccount: 按账号分页查询")
    void getArchivesByAccount_returnsPage() {
        ScrmChatArchiveEntity entity = buildArchiveEntity(100L);
        Page<ScrmChatArchiveEntity> page = new PageImpl<>(List.of(entity));
        when(archiveRepository.findByAccountIdOrderBySentAtDesc(eq(500L), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmChatArchiveEntity> result = service.getArchivesByAccount(500L, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(100L);
    }

    // ==================== 归档规则管理 ====================

    @Test
    @DisplayName("createRule: 成功创建, enabled 缺省 TRUE, priority 缺省 0")
    void createRule_success_defaultsFilled() throws Exception {
        ScrmArchiveRuleDto dto = buildRuleDto();
        dto.setEnabled(null);
        dto.setPriority(null);
        when(ruleRepository.findByRuleName("敏感词归档")).thenReturn(Collections.emptyList());
        when(ruleRepository.save(any(ScrmArchiveRuleEntity.class)))
                .thenAnswer(inv -> assignRuleId(inv.getArgument(0), 200L));

        ScrmArchiveRuleEntity result = service.createRule(dto);

        assertThat(result.getId()).isEqualTo(200L);
        ArgumentCaptor<ScrmArchiveRuleEntity> captor = ArgumentCaptor.forClass(ScrmArchiveRuleEntity.class);
        verify(ruleRepository).save(captor.capture());
        ScrmArchiveRuleEntity saved = captor.getValue();
        assertThat(saved.getRuleName()).isEqualTo("敏感词归档");
        assertThat(saved.getPlatformType()).isEqualTo("WECHAT");
        assertThat(saved.getDirection()).isEqualTo("INBOUND");
        assertThat(saved.getMessageTypes()).isEqualTo("TEXT,IMAGE");
        assertThat(saved.getKeywords()).isEqualTo("加微信,转账");
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getPriority()).isZero();
    }

    @Test
    @DisplayName("createRule: ruleName 为空抛 BAD_REQUEST")
    void createRule_blankName_badRequest() {
        ScrmArchiveRuleDto dto = buildRuleDto();
        dto.setRuleName("  ");

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("规则名称不能为空");
        verify(ruleRepository, never()).save(any(ScrmArchiveRuleEntity.class));
    }

    @Test
    @DisplayName("createRule: 规则名称重复抛 CONFLICT")
    void createRule_duplicateName_conflict() {
        ScrmArchiveRuleDto dto = buildRuleDto();
        when(ruleRepository.findByRuleName("敏感词归档"))
                .thenReturn(List.of(buildRuleEntity(200L)));

        assertThatThrownBy(() -> service.createRule(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归档规则名称已存在");
        verify(ruleRepository, never()).save(any(ScrmArchiveRuleEntity.class));
    }

    @Test
    @DisplayName("updateRule: 字段非空覆盖, null 字段保留原值")
    void updateRule_partialUpdate() throws Exception {
        ScrmArchiveRuleEntity entity = buildRuleEntity(200L);
        entity.setRuleName("原规则");
        entity.setPriority(5);
        entity.setEnabled(true);
        when(ruleRepository.findById(200L)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(any(ScrmArchiveRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmArchiveRuleDto dto = new ScrmArchiveRuleDto();
        dto.setRuleName("新规则");
        dto.setEnabled(false);
        ScrmArchiveRuleEntity result = service.updateRule(200L, dto);

        assertThat(result.getRuleName()).isEqualTo("新规则");
        assertThat(result.getEnabled()).isFalse();
        // 未传字段保留原值
        assertThat(result.getPriority()).isEqualTo(5);
    }

    @Test
    @DisplayName("updateRule: 规则不存在抛 NOT_FOUND")
    void updateRule_notFound() {
        when(ruleRepository.findById(999L)).thenReturn(Optional.empty());
        ScrmArchiveRuleDto dto = new ScrmArchiveRuleDto();
        dto.setRuleName("新规则");

        assertThatThrownBy(() -> service.updateRule(999L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归档规则不存在");
    }

    @Test
    @DisplayName("deleteRule: 成功删除规则")
    void deleteRule_success() throws Exception {
        ScrmArchiveRuleEntity entity = buildRuleEntity(200L);
        when(ruleRepository.findById(200L)).thenReturn(Optional.of(entity));

        service.deleteRule(200L);

        verify(ruleRepository).delete(entity);
    }

    @Test
    @DisplayName("deleteRule: 规则不存在抛 NOT_FOUND")
    void deleteRule_notFound() {
        when(ruleRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteRule(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归档规则不存在");
        verify(ruleRepository, never()).delete(any(ScrmArchiveRuleEntity.class));
    }

    @Test
    @DisplayName("toggleRule: 切换启用状态为 false")
    void toggleRule_disables() throws Exception {
        ScrmArchiveRuleEntity entity = buildRuleEntity(200L);
        entity.setEnabled(true);
        when(ruleRepository.findById(200L)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(any(ScrmArchiveRuleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmArchiveRuleEntity result = service.toggleRule(200L, false);

        assertThat(result.getEnabled()).isFalse();
    }

    @Test
    @DisplayName("listRules: enabledOnly=true 只返回启用规则")
    void listRules_enabledOnly() {
        ScrmArchiveRuleEntity rule = buildRuleEntity(200L);
        rule.setEnabled(true);
        when(ruleRepository.findByEnabledOrderByPriorityDesc(Boolean.TRUE))
                .thenReturn(List.of(rule));

        List<ScrmArchiveRuleEntity> rules = service.listRules(true);

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getId()).isEqualTo(200L);
        verify(ruleRepository).findByEnabledOrderByPriorityDesc(Boolean.TRUE);
    }

    @Test
    @DisplayName("listRules: enabledOnly=false 返回全部规则")
    void listRules_all() {
        when(ruleRepository.findAll())
                .thenReturn(List.of(buildRuleEntity(200L)));

        List<ScrmArchiveRuleEntity> rules = service.listRules(false);

        assertThat(rules).hasSize(1);
        verify(ruleRepository).findAll();
    }

    // ==================== 辅助构建方法 ====================

    private ScrmChatArchiveDto buildArchiveDto() {
        ScrmChatArchiveDto dto = new ScrmChatArchiveDto();
        dto.setAccountId(500L);
        dto.setCustomerId(600L);
        dto.setConversationId(700L);
        dto.setPlatformType("WECHAT");
        dto.setDirection("INBOUND");
        dto.setMessageType("TEXT");
        dto.setContent("你好");
        dto.setRawContent("{\"raw\":\"你好\"}");
        dto.setSentAt(LocalDateTime.of(2026, 8, 5, 10, 0));
        return dto;
    }

    private ScrmArchiveRuleDto buildRuleDto() {
        ScrmArchiveRuleDto dto = new ScrmArchiveRuleDto();
        dto.setRuleName("敏感词归档");
        dto.setPlatformType("WECHAT");
        dto.setAccountId(500L);
        dto.setDirection("INBOUND");
        dto.setMessageTypes("TEXT,IMAGE");
        dto.setKeywords("加微信,转账");
        dto.setRiskLevelFilter("HIGH");
        return dto;
    }

    private ScrmChatArchiveEntity buildArchiveEntity(Long id) {
        ScrmChatArchiveEntity entity = new ScrmChatArchiveEntity();
        entity.setId(id);
        entity.setAccountId(500L);
        entity.setCustomerId(600L);
        entity.setConversationId(700L);
        entity.setPlatformType("WECHAT");
        entity.setDirection("INBOUND");
        entity.setMessageType("TEXT");
        entity.setContent("你好");
        entity.setSentAt(LocalDateTime.of(2026, 8, 5, 10, 0));
        entity.setArchivedAt(LocalDateTime.of(2026, 8, 5, 10, 1));
        entity.setQualityFlag("NORMAL");
        entity.setArchiveSource("AUTO");
        return entity;
    }

    private ScrmArchiveRuleEntity buildRuleEntity(Long id) {
        ScrmArchiveRuleEntity entity = new ScrmArchiveRuleEntity();
        entity.setId(id);
        entity.setRuleName("敏感词归档");
        entity.setPlatformType("WECHAT");
        entity.setDirection("INBOUND");
        entity.setMessageTypes("TEXT,IMAGE");
        entity.setKeywords("加微信,转账");
        entity.setEnabled(true);
        entity.setPriority(0);
        return entity;
    }

    private ScrmChatArchiveEntity assignArchiveId(ScrmChatArchiveEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }

    private ScrmArchiveRuleEntity assignRuleId(ScrmArchiveRuleEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
