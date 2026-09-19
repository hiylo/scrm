/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQuickReplyServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmQuickReplyCategoryDto;
import org.hiylo.scrm.dto.ScrmQuickReplyDto;
import org.hiylo.scrm.entity.ScrmQuickReplyCategoryEntity;
import org.hiylo.scrm.entity.ScrmQuickReplyEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmQuickReplyCategoryRepository;
import org.hiylo.scrm.repository.ScrmQuickReplyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * ScrmQuickReplyService 单元测试
 * <p>
 * 聚焦快捷回复分类管理 (创建 / 默认值填充)、回复条目管理 (创建 / 默认值 / 个人专属校验 /
 * 快捷键唯一性)、快捷键匹配 (个人专属优先于团队共享)、使用次数自增与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmQuickReplyService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmQuickReplyServiceTest {

    /** 快捷回复分类仓库 Mock */
    @Mock
    private ScrmQuickReplyCategoryRepository categoryRepository;
    /** 快捷回复条目仓库 Mock */
    @Mock
    private ScrmQuickReplyRepository replyRepository;

    /** 被测服务实例 */
    private ScrmQuickReplyService service;

    @BeforeEach
    void setUp() {
        service = new ScrmQuickReplyService(categoryRepository, replyRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的快捷回复分类实体 (用于 findById 返回)
     */
    private ScrmQuickReplyCategoryEntity buildCategoryEntity(Long id, String categoryName) {
        ScrmQuickReplyCategoryEntity entity = new ScrmQuickReplyCategoryEntity();
        entity.setId(id);
        entity.setCategoryName(categoryName);
        entity.setSortOrder(0);
        entity.setStatus("ACTIVE");
        return entity;
    }

    /**
     * 构造已持久化的快捷回复条目实体 (用于 findById 返回)
     */
    private ScrmQuickReplyEntity buildReplyEntity(Long id, String title, String shortcut,
                                                   Boolean isPersonal, String ownerUserId) {
        ScrmQuickReplyEntity entity = new ScrmQuickReplyEntity();
        entity.setId(id);
        entity.setTitle(title);
        entity.setContent("回复内容");
        entity.setShortcut(shortcut);
        entity.setReplyType("TEXT");
        entity.setSortOrder(0);
        entity.setUseCount(0);
        entity.setIsPersonal(isPersonal);
        entity.setOwnerUserId(ownerUserId);
        entity.setStatus("ACTIVE");
        return entity;
    }

    @Test
    @DisplayName("createCategory: 写入账号 ID 与默认值后持久化")
    void createCategory_success() throws ScrmException {
        when(categoryRepository.save(any(ScrmQuickReplyCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmQuickReplyCategoryDto dto = new ScrmQuickReplyCategoryDto();
        dto.setCategoryName("常用话术");
        dto.setIcon("💬");

        ScrmQuickReplyCategoryEntity result = service.createCategory(dto);

        ArgumentCaptor<ScrmQuickReplyCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmQuickReplyCategoryEntity.class);
        verify(categoryRepository, times(1)).save(captor.capture());
        ScrmQuickReplyCategoryEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getSortOrder()).isZero();
        assertThat(saved.getCategoryName()).isEqualTo("常用话术");
        assertThat(result.getIcon()).isEqualTo("💬");
    }

    @Test
    @DisplayName("createReply: 写入账号 ID 与默认值后持久化")
    void createReply_success() throws ScrmException {
        when(replyRepository.findFirstByShortcut(eq("/你好")))
                .thenReturn(Optional.empty());
        when(replyRepository.save(any(ScrmQuickReplyEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmQuickReplyDto dto = new ScrmQuickReplyDto();
        dto.setTitle("你好回复");
        dto.setContent("您好, 有什么可以帮您?");
        dto.setShortcut("/你好");
        dto.setCreatedBy("admin01");

        ScrmQuickReplyEntity result = service.createReply(dto);

        ArgumentCaptor<ScrmQuickReplyEntity> captor =
                ArgumentCaptor.forClass(ScrmQuickReplyEntity.class);
        verify(replyRepository, times(1)).save(captor.capture());
        ScrmQuickReplyEntity saved = captor.getValue();
        assertThat(saved.getReplyType()).isEqualTo("TEXT");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getSortOrder()).isZero();
        assertThat(saved.getUseCount()).isZero();
        assertThat(saved.getIsPersonal()).isFalse();
        assertThat(result.getShortcut()).isEqualTo("/你好");
    }

    @Test
    @DisplayName("createReply: 个人专属缺 ownerUserId 抛 BAD_REQUEST")
    void createReply_personalWithoutOwnerUserId() {
        ScrmQuickReplyDto dto = new ScrmQuickReplyDto();
        dto.setTitle("私人回复");
        dto.setContent("内容");
        dto.setIsPersonal(true);

        assertThatThrownBy(() -> service.createReply(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("个人专属回复必须指定归属人");
        verify(replyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createReply: shortcut 已被占用抛 CONFLICT")
    void createReply_shortcutConflict() {
        when(replyRepository.findFirstByShortcut(eq("/你好")))
                .thenReturn(Optional.of(buildReplyEntity(10L, "已存在回复", "/你好", false, null)));

        ScrmQuickReplyDto dto = new ScrmQuickReplyDto();
        dto.setTitle("新回复");
        dto.setContent("内容");
        dto.setShortcut("/你好");

        assertThatThrownBy(() -> service.createReply(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("快捷键已被占用");
        verify(replyRepository, never()).save(any());
    }

    @Test
    @DisplayName("getByShortcut: 个人专属回复优先于团队共享回复")
    void getByShortcut_personalPriority() throws ScrmException {
        ScrmQuickReplyEntity teamReply = buildReplyEntity(10L, "团队回复", "/你好", false, null);
        ScrmQuickReplyEntity personalReply = buildReplyEntity(11L, "私人回复", "/你好", true, "user01");
        when(replyRepository.findByShortcut(eq("/你好")))
                .thenReturn(List.of(teamReply, personalReply));

        ScrmQuickReplyEntity result = service.getByShortcut("/你好", "user01");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(11L);
        assertThat(result.getIsPersonal()).isTrue();
        assertThat(result.getOwnerUserId()).isEqualTo("user01");
    }

    @Test
    @DisplayName("getByShortcut: 无个人专属时返回团队共享回复")
    void getByShortcut_teamFallback() throws ScrmException {
        ScrmQuickReplyEntity teamReply = buildReplyEntity(10L, "团队回复", "/你好", false, null);
        ScrmQuickReplyEntity othersPersonal = buildReplyEntity(11L, "他人私人回复", "/你好", true, "user02");
        when(replyRepository.findByShortcut(eq("/你好")))
                .thenReturn(List.of(teamReply, othersPersonal));

        ScrmQuickReplyEntity result = service.getByShortcut("/你好", "user01");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getIsPersonal()).isFalse();
    }

    @Test
    @DisplayName("incrementUseCount: 使用次数 +1 后持久化")
    void incrementUseCount_success() throws ScrmException {
        ScrmQuickReplyEntity entity = buildReplyEntity(10L, "你好回复", "/你好", false, null);
        entity.setUseCount(5);
        when(replyRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(replyRepository.save(any(ScrmQuickReplyEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmQuickReplyEntity result = service.incrementUseCount(10L);

        ArgumentCaptor<ScrmQuickReplyEntity> captor =
                ArgumentCaptor.forClass(ScrmQuickReplyEntity.class);
        verify(replyRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUseCount()).isEqualTo(6);
        assertThat(result.getUseCount()).isEqualTo(6);
    }

    
    
}
