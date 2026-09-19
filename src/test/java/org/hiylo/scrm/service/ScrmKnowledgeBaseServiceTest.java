/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeBaseServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmKnowledgeArticleDto;
import org.hiylo.scrm.dto.ScrmKnowledgeCategoryDto;
import org.hiylo.scrm.dto.ScrmKnowledgeFeedbackDto;
import org.hiylo.scrm.dto.ScrmKnowledgeReviewDto;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeCategoryEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeFeedbackEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeCategoryRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeFeedbackRepository;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmKnowledgeBaseService 单元测试
 * <p>
 * 聚焦知识库分类 CRUD / 文章状态流转 (DRAFT→PENDING_REVIEW→PUBLISHED→ARCHIVED) /
 * 审核动作 (APPROVE/REJECT) / 精选置顶 / 复制 / 浏览计数 / 反馈去重与数据隔离等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmKnowledgeBaseService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmKnowledgeBaseServiceTest {

    /** 知识分类仓库 Mock */
    @Mock
    private ScrmKnowledgeCategoryRepository categoryRepository;
    /** 知识文章仓库 Mock */
    @Mock
    private ScrmKnowledgeArticleRepository articleRepository;
    /** 知识反馈仓库 Mock */
    @Mock
    private ScrmKnowledgeFeedbackRepository feedbackRepository;

    /** 被测服务实例 */
    private ScrmKnowledgeBaseService service;

    @BeforeEach
    void setUp() {
        ScrmKnowledgeCategoryService categoryService =
                new ScrmKnowledgeCategoryService(categoryRepository, articleRepository);
        ScrmKnowledgeArticleService articleService =
                new ScrmKnowledgeArticleService(articleRepository, feedbackRepository, categoryService);
        ScrmKnowledgeVersionService versionService =
                new ScrmKnowledgeVersionService(articleRepository, articleService);
        ScrmKnowledgeSearchFeedbackService searchFeedbackService =
                new ScrmKnowledgeSearchFeedbackService(articleRepository, feedbackRepository, articleService);
        ScrmKnowledgeReviewService reviewService =
                new ScrmKnowledgeReviewService(articleRepository, articleService, versionService);
        ScrmKnowledgeStatsService statsService = new ScrmKnowledgeStatsService(
                articleRepository, categoryRepository, feedbackRepository, articleService, searchFeedbackService);
        service = new ScrmKnowledgeBaseService(
                categoryService, articleService, versionService, searchFeedbackService, reviewService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造分类 DTO
     */
    private ScrmKnowledgeCategoryDto buildCategoryDto() {
        ScrmKnowledgeCategoryDto dto = new ScrmKnowledgeCategoryDto();
        dto.setCategoryName("售前 FAQ");
        dto.setCategoryCode("FAQ_001");
        dto.setDescription("售前常见问题");
        dto.setSortOrder(1);
        return dto;
    }

    /**
     * 构造已持久化的分类实体
     */
    private ScrmKnowledgeCategoryEntity buildCategoryEntity(Long id) {
        ScrmKnowledgeCategoryEntity entity = new ScrmKnowledgeCategoryEntity();
        entity.setId(id);
        entity.setCategoryName("售前 FAQ");
        entity.setCategoryCode("FAQ_001");
        entity.setEnabled(Boolean.TRUE);
        entity.setArticleCount(0);
        return entity;
    }

    /**
     * 构造文章 DTO
     */
    private ScrmKnowledgeArticleDto buildArticleDto() {
        ScrmKnowledgeArticleDto dto = new ScrmKnowledgeArticleDto();
        dto.setTitle("如何配置企业微信");
        dto.setArticleCode("ART_001");
        dto.setContent("## 步骤一\n下载企业微信...");
        dto.setContentType("MARKDOWN");
        dto.setArticleType("FAQ");
        dto.setDifficultyLevel("BEGINNER");
        return dto;
    }

    /**
     * 构造已持久化的文章实体
     */
    private ScrmKnowledgeArticleEntity buildArticleEntity(Long id, String status) {
        ScrmKnowledgeArticleEntity entity = new ScrmKnowledgeArticleEntity();
        entity.setId(id);
        entity.setTitle("如何配置企业微信");
        entity.setArticleCode("ART_001");
        entity.setContent("## 步骤一\n下载企业微信...");
        entity.setContentType("MARKDOWN");
        entity.setArticleType("FAQ");
        entity.setDifficultyLevel("BEGINNER");
        entity.setStatus(status);
        entity.setReviewStatus("PENDING");
        entity.setVersionNumber(1);
        entity.setViewCount(0);
        entity.setLikeCount(0);
        entity.setDislikeCount(0);
        entity.setFavoriteCount(0);
        entity.setShareCount(0);
        entity.setCommentCount(0);
        entity.setHelpfulCount(0);
        entity.setNotHelpfulCount(0);
        entity.setIsFeatured(Boolean.FALSE);
        entity.setIsPinned(Boolean.FALSE);
        return entity;
    }

    /**
     * 构造反馈 DTO
     */
    private ScrmKnowledgeFeedbackDto buildFeedbackDto(Long articleId, String type) {
        ScrmKnowledgeFeedbackDto dto = new ScrmKnowledgeFeedbackDto();
        dto.setArticleId(articleId);
        dto.setFeedbackType(type);
        dto.setUserId("user1");
        dto.setUserName("张三");
        return dto;
    }

    // ==================== 分类管理 ====================

    @Test
    @DisplayName("createCategory: 写入账号 ID 与默认值并持久化")
    void createCategory_success() throws ScrmException {
        ScrmKnowledgeCategoryDto dto = buildCategoryDto();
        when(categoryRepository.existsByCategoryCode("FAQ_001")).thenReturn(false);
        when(categoryRepository.save(any(ScrmKnowledgeCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmKnowledgeCategoryEntity result = service.createCategory(dto);

        ArgumentCaptor<ScrmKnowledgeCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeCategoryEntity.class);
        verify(categoryRepository, times(1)).save(captor.capture());
        ScrmKnowledgeCategoryEntity saved = captor.getValue();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getArticleCount()).isZero();
        assertThat(saved.getTotalViews()).isZero();
        assertThat(saved.getTotalLikes()).isZero();
        // sortOrder 由 dto 提供
        assertThat(saved.getSortOrder()).isEqualTo(1);
        assertThat(result.getCategoryCode()).isEqualTo("FAQ_001");
    }

    @Test
    @DisplayName("createCategory: 编码重复抛 CONFLICT")
    void createCategory_duplicateCode() {
        ScrmKnowledgeCategoryDto dto = buildCategoryDto();
        when(categoryRepository.existsByCategoryCode("FAQ_001")).thenReturn(true);

        assertThatThrownBy(() -> service.createCategory(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("分类编码已存在");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCategory: dto 为空抛 BAD_REQUEST")
    void createCategory_nullDto() {
        assertThatThrownBy(() -> service.createCategory(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("分类参数不能为空");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCategory: 父分类为自身抛 BAD_REQUEST")
    void updateCategory_parentSelf() {
        ScrmKnowledgeCategoryEntity entity = buildCategoryEntity(10L);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmKnowledgeCategoryDto dto = buildCategoryDto();
        dto.setParentId(10L);

        assertThatThrownBy(() -> service.updateCategory(10L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("父分类不能为自身");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCategory: 仅更新 categoryName 字段")
    void updateCategory_partial() throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = buildCategoryEntity(10L);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(categoryRepository.save(any(ScrmKnowledgeCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmKnowledgeCategoryDto dto = new ScrmKnowledgeCategoryDto();
        dto.setCategoryName("新分类名");

        service.updateCategory(10L, dto);

        ArgumentCaptor<ScrmKnowledgeCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeCategoryEntity.class);
        verify(categoryRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getCategoryName()).isEqualTo("新分类名");
        // 其他字段保留原值
        assertThat(captor.getValue().getCategoryCode()).isEqualTo("FAQ_001");
    }

    @Test
    @DisplayName("getCategory: 不存在抛 NOT_FOUND")
    void getCategory_notFound() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCategory(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("知识分类不存在");
    }

    
    @Test
    @DisplayName("getCategoryByCode: 不存在抛 NOT_FOUND")
    void getCategoryByCode_notFound() {
        when(categoryRepository.findByCategoryCode("FAQ_X"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCategoryByCode("FAQ_X"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("知识分类不存在");
    }

    @Test
    @DisplayName("deleteCategory: 存在子分类抛 CONFLICT")
    void deleteCategory_hasChildren() {
        ScrmKnowledgeCategoryEntity entity = buildCategoryEntity(10L);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(categoryRepository.countByParentId(10L)).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteCategory(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("存在子分类");
        verify(categoryRepository, never()).delete(any(ScrmKnowledgeCategoryEntity.class));
    }

    @Test
    @DisplayName("deleteCategory: 分类下存在文章抛 CONFLICT")
    void deleteCategory_hasArticles() {
        ScrmKnowledgeCategoryEntity entity = buildCategoryEntity(10L);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(categoryRepository.countByParentId(10L)).thenReturn(0L);
        Page<ScrmKnowledgeArticleEntity> page = new PageImpl<>(
                List.of(buildArticleEntity(1L, "PUBLISHED")), PageRequest.of(0, 1), 1L);
        when(articleRepository.findByCategoryIdAndStatus(eq(10L), eq("PUBLISHED")
            , any(Pageable.class)))
                .thenReturn(page);

        assertThatThrownBy(() -> service.deleteCategory(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("存在文章");
        verify(categoryRepository, never()).delete(any(ScrmKnowledgeCategoryEntity.class));
    }

    @Test
    @DisplayName("deleteCategory: 无子分类无文章时删除成功")
    void deleteCategory_success() throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = buildCategoryEntity(10L);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(categoryRepository.countByParentId(10L)).thenReturn(0L);
        Page<ScrmKnowledgeArticleEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(articleRepository.findByCategoryIdAndStatus(eq(10L), eq("PUBLISHED")
            , any(Pageable.class)))
                .thenReturn(emptyPage);

        service.deleteCategory(10L);

        verify(categoryRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("enableCategory: 设置 enabled=true")
    void enableCategory_success() throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = buildCategoryEntity(10L);
        entity.setEnabled(false);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(categoryRepository.save(any(ScrmKnowledgeCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.enableCategory(10L);

        ArgumentCaptor<ScrmKnowledgeCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeCategoryEntity.class);
        verify(categoryRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("disableCategory: 设置 enabled=false")
    void disableCategory_success() throws ScrmException {
        ScrmKnowledgeCategoryEntity entity = buildCategoryEntity(10L);
        entity.setEnabled(true);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(categoryRepository.save(any(ScrmKnowledgeCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.disableCategory(10L);

        ArgumentCaptor<ScrmKnowledgeCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeCategoryEntity.class);
        verify(categoryRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
    }

    // ==================== 文章管理 ====================

    @Test
    @DisplayName("createArticle: 写入账号 ID 与默认值并持久化")
    void createArticle_success() throws ScrmException {
        ScrmKnowledgeArticleDto dto = buildArticleDto();
        when(articleRepository.existsByArticleCode("ART_001")).thenReturn(false);
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmKnowledgeArticleEntity result = service.createArticle(dto);

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        ScrmKnowledgeArticleEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getReviewStatus()).isEqualTo("PENDING");
        assertThat(saved.getVersionNumber()).isEqualTo(1);
        assertThat(saved.getIsFeatured()).isFalse();
        assertThat(saved.getIsPinned()).isFalse();
        assertThat(saved.getReadingTimeMinutes()).isEqualTo(5);
        assertThat(result.getArticleCode()).isEqualTo("ART_001");
    }

    @Test
    @DisplayName("createArticle: 标题为空抛 BAD_REQUEST")
    void createArticle_blankTitle() {
        ScrmKnowledgeArticleDto dto = buildArticleDto();
        dto.setTitle("  ");

        assertThatThrownBy(() -> service.createArticle(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("文章标题不能为空");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createArticle: 内容为空抛 BAD_REQUEST")
    void createArticle_blankContent() {
        ScrmKnowledgeArticleDto dto = buildArticleDto();
        dto.setContent("");

        assertThatThrownBy(() -> service.createArticle(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("文章内容不能为空");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("createArticle: 编码重复抛 CONFLICT")
    void createArticle_duplicateCode() {
        ScrmKnowledgeArticleDto dto = buildArticleDto();
        when(articleRepository.existsByArticleCode("ART_001")).thenReturn(true);

        assertThatThrownBy(() -> service.createArticle(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("文章编码已存在");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateArticle: 已发布文章禁止修改抛 CONFLICT")
    void updateArticle_publishedCannotUpdate() {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PUBLISHED");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateArticle(10L, buildArticleDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已发布 / 已归档文章不可修改");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateArticle: 已归档文章禁止修改抛 CONFLICT")
    void updateArticle_archivedCannotUpdate() {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "ARCHIVED");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateArticle(10L, buildArticleDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已发布 / 已归档文章不可修改");
    }

    @Test
    @DisplayName("updateArticle: DRAFT 状态部分更新成功并刷新 lastModifiedAt")
    void updateArticle_partialUpdate() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "DRAFT");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmKnowledgeArticleDto dto = new ScrmKnowledgeArticleDto();
        dto.setTitle("新标题");

        service.updateArticle(10L, dto);

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("新标题");
        assertThat(captor.getValue().getLastModifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("getArticle: 每次查询浏览量自增 1")
    void getArticle_incrementsViewCount() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PUBLISHED");
        entity.setViewCount(5);
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.getArticle(10L);

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getViewCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("getArticle: null viewCount 视为 0 后自增")
    void getArticle_nullViewCount() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PUBLISHED");
        entity.setViewCount(null);
        entity.setCategoryId(null);
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.getArticle(10L);

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getViewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getArticleByCode: 不存在抛 NOT_FOUND")
    void getArticleByCode_notFound() {
        when(articleRepository.findByArticleCode("ART_X"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getArticleByCode("ART_X"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("知识文章不存在");
    }

    @Test
    @DisplayName("publishArticle: 已发布状态幂等返回不抛异常")
    void publishArticle_alreadyPublished() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PUBLISHED");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));

        service.publishArticle(10L);

        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("publishArticle: PENDING_REVIEW 状态不可直接发布抛 CONFLICT")
    void publishArticle_invalidStatus() {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PENDING_REVIEW");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.publishArticle(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 DRAFT / REJECTED / ARCHIVED 状态可发布");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("publishArticle: DRAFT → PUBLISHED 成功并记录发布时间")
    void publishArticle_draftToPublished() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "DRAFT");
        entity.setCategoryId(null);
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.publishArticle(10L);

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PUBLISHED");
        assertThat(captor.getValue().getReviewStatus()).isEqualTo("APPROVED");
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("archiveArticle: 非 PUBLISHED 状态不可归档抛 CONFLICT")
    void archiveArticle_invalidStatus() {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "DRAFT");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.archiveArticle(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PUBLISHED 状态可归档");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("archiveArticle: PUBLISHED → ARCHIVED 成功")
    void archiveArticle_success() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PUBLISHED");
        entity.setCategoryId(null);
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.archiveArticle(10L);

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("featureArticle: 设置 isFeatured=true")
    void featureArticle_success() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PUBLISHED");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.featureArticle(10L);

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getIsFeatured()).isTrue();
    }

    @Test
    @DisplayName("pinArticle: 设置 isPinned=true")
    void pinArticle_success() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PUBLISHED");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.pinArticle(10L);

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getIsPinned()).isTrue();
    }

    @Test
    @DisplayName("duplicateArticle: 新编码为空抛 BAD_REQUEST")
    void duplicateArticle_blankNewCode() {
        assertThatThrownBy(() -> service.duplicateArticle(10L, "  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("新文章编码不能为空");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("duplicateArticle: 新编码已存在抛 CONFLICT")
    void duplicateArticle_duplicateCode() {
        when(articleRepository.existsByArticleCode("ART_DUP")).thenReturn(true);

        assertThatThrownBy(() -> service.duplicateArticle(10L, "ART_DUP"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("文章编码已存在");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("duplicateArticle: 复制成功, 状态置 DRAFT 计数清零")
    void duplicateArticle_success() throws ScrmException {
        ScrmKnowledgeArticleEntity source = buildArticleEntity(10L, "PUBLISHED");
        when(articleRepository.existsByArticleCode("ART_COPY")).thenReturn(false);
        when(articleRepository.findById(10L)).thenReturn(Optional.of(source));
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.duplicateArticle(10L, "ART_COPY");

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        ScrmKnowledgeArticleEntity copy = captor.getValue();
        assertThat(copy.getArticleCode()).isEqualTo("ART_COPY");
        assertThat(copy.getTitle()).isEqualTo("如何配置企业微信-副本");
        assertThat(copy.getStatus()).isEqualTo("DRAFT");
        assertThat(copy.getVersionNumber()).isEqualTo(1);
        assertThat(copy.getIsFeatured()).isFalse();
        assertThat(copy.getIsPinned()).isFalse();
    }

    
    // ==================== 文章审核 ====================

    @Test
    @DisplayName("submitForReview: 非 DRAFT/REJECTED 状态抛 CONFLICT")
    void submitForReview_invalidStatus() {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PUBLISHED");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.submitForReview(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 DRAFT / REJECTED 状态可提交审核");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitForReview: DRAFT → PENDING_REVIEW 成功")
    void submitForReview_success() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "DRAFT");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.submitForReview(10L);

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(captor.getValue().getReviewStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getReviewComment()).isNull();
        assertThat(captor.getValue().getReviewedAt()).isNull();
    }

    @Test
    @DisplayName("review: 审核参数为空抛 BAD_REQUEST")
    void review_nullDto() {
        assertThatThrownBy(() -> service.review(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("审核参数不能为空");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("review: 非 PENDING_REVIEW 状态抛 CONFLICT")
    void review_invalidStatus() {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "DRAFT");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmKnowledgeReviewDto dto = new ScrmKnowledgeReviewDto();
        dto.setArticleId(10L);
        dto.setAction("APPROVE");

        assertThatThrownBy(() -> service.review(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅 PENDING_REVIEW 状态可审核");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("review: 非法审核动作抛 BAD_REQUEST")
    void review_invalidAction() {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PENDING_REVIEW");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmKnowledgeReviewDto dto = new ScrmKnowledgeReviewDto();
        dto.setArticleId(10L);
        dto.setAction("INVALID");

        assertThatThrownBy(() -> service.review(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("审核动作非法");
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("review: APPROVE 通过后状态置 PUBLISHED")
    void review_approve() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PENDING_REVIEW");
        entity.setCategoryId(null);
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmKnowledgeReviewDto dto = new ScrmKnowledgeReviewDto();
        dto.setArticleId(10L);
        dto.setAction("APPROVE");
        dto.setComment("内容合规");

        service.review(dto);

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        ScrmKnowledgeArticleEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PUBLISHED");
        assertThat(saved.getReviewStatus()).isEqualTo("APPROVED");
        assertThat(saved.getPublishedAt()).isNotNull();
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(saved.getReviewComment()).isEqualTo("内容合规");
    }

    @Test
    @DisplayName("review: REJECT 驳回后状态置 REJECTED")
    void review_reject() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "PENDING_REVIEW");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmKnowledgeReviewDto dto = new ScrmKnowledgeReviewDto();
        dto.setArticleId(10L);
        dto.setAction("REJECT");
        dto.setComment("内容不合规");

        service.review(dto);

        ArgumentCaptor<ScrmKnowledgeArticleEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeArticleEntity.class);
        verify(articleRepository, times(1)).save(captor.capture());
        ScrmKnowledgeArticleEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("REJECTED");
        assertThat(saved.getReviewStatus()).isEqualTo("REJECTED");
        assertThat(saved.getReviewComment()).isEqualTo("内容不合规");
    }

    // ==================== 反馈管理 ====================

    @Test
    @DisplayName("addFeedback: dto 为空抛 BAD_REQUEST")
    void addFeedback_nullDto() {
        assertThatThrownBy(() -> service.addFeedback(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("反馈参数不能为空");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("addFeedback: articleId 为空抛 BAD_REQUEST")
    void addFeedback_nullArticleId() {
        ScrmKnowledgeFeedbackDto dto = buildFeedbackDto(null, "LIKE");

        assertThatThrownBy(() -> service.addFeedback(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("文章 ID 不能为空");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("addFeedback: userId 为空抛 BAD_REQUEST")
    void addFeedback_blankUserId() {
        ScrmKnowledgeFeedbackDto dto = buildFeedbackDto(10L, "LIKE");
        dto.setUserId("  ");

        assertThatThrownBy(() -> service.addFeedback(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("用户 ID 不能为空");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("addFeedback: 文章不存在抛 NOT_FOUND")
    void addFeedback_articleNotFound() {
        ScrmKnowledgeFeedbackDto dto = buildFeedbackDto(10L, "LIKE");
        when(articleRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addFeedback(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("知识文章不存在");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("addFeedback: 唯一反馈类型重复抛 CONFLICT")
    void addFeedback_duplicateUniqueType() {
        ScrmKnowledgeFeedbackDto dto = buildFeedbackDto(10L, "LIKE");
        ScrmKnowledgeArticleEntity article = buildArticleEntity(10L, "PUBLISHED");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(article));
        when(feedbackRepository.findByArticleIdAndUserIdAndFeedbackType(10L, "user1", "LIKE"))
                .thenReturn(Optional.of(new ScrmKnowledgeFeedbackEntity()));

        assertThatThrownBy(() -> service.addFeedback(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("已存在相同反馈");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    @DisplayName("addFeedback: COMMENT 类型无需去重, 直接创建")
    void addFeedback_commentNoDedup() throws ScrmException {
        ScrmKnowledgeFeedbackDto dto = buildFeedbackDto(10L, "COMMENT");
        dto.setComment("好文章");
        ScrmKnowledgeArticleEntity article = buildArticleEntity(10L, "PUBLISHED");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(article));
        when(feedbackRepository.save(any(ScrmKnowledgeFeedbackEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // updateArticleStats 调用 save article
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.addFeedback(dto);

        ArgumentCaptor<ScrmKnowledgeFeedbackEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeFeedbackEntity.class);
        verify(feedbackRepository, times(1)).save(captor.capture());
        ScrmKnowledgeFeedbackEntity saved = captor.getValue();
        assertThat(saved.getArticleId()).isEqualTo(10L);
        assertThat(saved.getFeedbackType()).isEqualTo("COMMENT");
        assertThat(saved.getUserId()).isEqualTo("user1");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getUpvoteCount()).isZero();
        // COMMENT 不需去重查询
        verify(feedbackRepository, never()).findByArticleIdAndUserIdAndFeedbackType(any(), any(), any());
    }

    
    @Test
    @DisplayName("resolveFeedback: 状态置 RESOLVED 并记录解决人")
    void resolveFeedback_success() throws ScrmException {
        ScrmKnowledgeFeedbackEntity entity = new ScrmKnowledgeFeedbackEntity();
        entity.setId(1L);
        entity.setStatus("ACTIVE");
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(feedbackRepository.save(any(ScrmKnowledgeFeedbackEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.resolveFeedback(1L, "已修复");

        ArgumentCaptor<ScrmKnowledgeFeedbackEntity> captor =
                ArgumentCaptor.forClass(ScrmKnowledgeFeedbackEntity.class);
        verify(feedbackRepository, times(1)).save(captor.capture());
        ScrmKnowledgeFeedbackEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("RESOLVED");
        assertThat(saved.getResolvedAt()).isNotNull();
        assertThat(saved.getResolutionNote()).isEqualTo("已修复");
    }

    @Test
    @DisplayName("deleteArticle: 同时清理关联反馈")
    void deleteArticle_cascadeFeedback() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "DRAFT");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmKnowledgeFeedbackEntity fb = new ScrmKnowledgeFeedbackEntity();
        fb.setId(1L);
        when(feedbackRepository.findAll(any(Specification.class))).thenReturn(List.of(fb));

        service.deleteArticle(10L);

        verify(feedbackRepository, times(1)).deleteAll(List.of(fb));
        verify(articleRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("deleteArticle: 无关联反馈时仅删除文章")
    void deleteArticle_noFeedback() throws ScrmException {
        ScrmKnowledgeArticleEntity entity = buildArticleEntity(10L, "DRAFT");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(feedbackRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        service.deleteArticle(10L);

        verify(feedbackRepository, never()).deleteAll(any());
        verify(articleRepository, times(1)).delete(entity);
    }
}
