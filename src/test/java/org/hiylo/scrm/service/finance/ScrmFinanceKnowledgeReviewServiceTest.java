/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmFinanceKnowledgeReviewServiceTest.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance;

import org.hiylo.scrm.entity.ScrmAiKnowledgeDocumentEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAiKnowledgeDocumentRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.hiylo.scrm.service.ScrmKnowledgeArticleService;
import org.hiylo.scrm.service.ScrmKnowledgeCategoryService;
import org.hiylo.scrm.service.ScrmKnowledgeVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模块③ 版本复核服务单元测试。
 * <p>
 * 覆盖: AiKnowledgeDocument 复核（日期/周期落库）/ 知识文章复核（版本快照创建 +
 * 日期回写 + 变更说明）/ 文档与文章待复核检索 / 待复核概览 / 文档不存在抛异常。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmFinanceKnowledgeReviewService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmFinanceKnowledgeReviewServiceTest {

    @Mock
    private ScrmAiKnowledgeDocumentRepository documentRepository;

    @Mock
    private ScrmKnowledgeArticleRepository articleRepository;

    private ScrmKnowledgeArticleService articleService;

    private ScrmKnowledgeVersionService versionService;

    private ScrmFinanceKnowledgeReviewService service;

    @BeforeEach
    void setUp() {
        ScrmKnowledgeCategoryService categoryService = new ScrmKnowledgeCategoryService(
                org.mockito.Mockito.mock(org.hiylo.scrm.repository.ScrmKnowledgeCategoryRepository.class),
                articleRepository);
        articleService = new ScrmKnowledgeArticleService(articleRepository,
                org.mockito.Mockito.mock(org.hiylo.scrm.repository.ScrmKnowledgeFeedbackRepository.class),
                categoryService);
        versionService = new ScrmKnowledgeVersionService(articleRepository, articleService);
        service = new ScrmFinanceKnowledgeReviewService(
                documentRepository, articleRepository, versionService);
    }

    @Test
    @DisplayName("reviewDocument: 更新最后复核日期/下次复核日期/复核周期")
    void reviewDocument() {
        ScrmAiKnowledgeDocumentEntity doc = new ScrmAiKnowledgeDocumentEntity();
        doc.setId(1L);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(ScrmAiKnowledgeDocumentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime reviewed = LocalDateTime.of(2026, 10, 1, 0, 0);
        ScrmAiKnowledgeDocumentEntity result = service.reviewDocument(1L, reviewed, 180, "李会计");

        assertThat(result.getLastReviewedAt()).isEqualTo(reviewed);
        assertThat(result.getReviewCycleDays()).isEqualTo(180);
        assertThat(result.getNextReviewAt()).isEqualTo(reviewed.plusDays(180));
    }

    @Test
    @DisplayName("reviewDocument_notFound: 文档不存在抛 ScrmException")
    void reviewDocument_notFound() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reviewDocument(99L, null, null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("reviewArticle: 创建版本快照 + 主文章复核日期回写")
    void reviewArticle() {
        ScrmKnowledgeArticleEntity main = new ScrmKnowledgeArticleEntity();
        main.setId(10L);
        main.setArticleCode("R-ACCT-0001");
        main.setVersionNumber(1);
        main.setStatus("PUBLISHED");
        main.setTitle("小规模纳税人报税时间");
        when(articleRepository.findById(10L)).thenReturn(Optional.of(main));
        when(articleRepository.existsByArticleCode("R-ACCT-0001_v2")).thenReturn(false);
        when(articleRepository.save(any(ScrmKnowledgeArticleEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime reviewed = LocalDateTime.of(2026, 10, 1, 0, 0);
        ScrmKnowledgeArticleEntity result = service.reviewArticle(10L, reviewed, 180, "核对申报期", "李会计");

        // 版本快照创建
        verify(articleRepository).existsByArticleCode("R-ACCT-0001_v2");
        // 版本快照 save + 主文章 currentVersionId save + 主文章复核日期 save
        verify(articleRepository, times(3)).save(any(ScrmKnowledgeArticleEntity.class));
        // 主文章复核日期
        assertThat(result.getLastReviewedAt()).isEqualTo(reviewed);
        assertThat(result.getNextReviewAt()).isEqualTo(reviewed.plusDays(180));
        assertThat(result.getLastModifiedAt()).isEqualTo(reviewed);
    }

    @Test
    @DisplayName("listDocumentsDueForReview: 待复核文档分页")
    void listDocumentsDueForReview() {
        ScrmAiKnowledgeDocumentEntity doc = new ScrmAiKnowledgeDocumentEntity();
        doc.setId(1L);
        doc.setTitle("待复核文档");
        Page<ScrmAiKnowledgeDocumentEntity> page =
                new PageImpl<>(List.of(doc), PageRequest.of(0, 20), 1);
        when(documentRepository.findDueForReview(eq(null), any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(page);

        Page<ScrmAiKnowledgeDocumentEntity> result = service.listDocumentsDueForReview(null, PageRequest.of(0, 20));
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("待复核文档");
    }

    @Test
    @DisplayName("getReviewOverview: 两个域待复核数量")
    void getReviewOverview() {
        when(documentRepository.countDueForReview(eq(null), any(LocalDateTime.class))).thenReturn(3L);
        when(articleRepository.countDueForReview(any(LocalDateTime.class), any())).thenReturn(5L);

        var overview = service.getReviewOverview();
        assertThat(overview.get("documentsDue")).isEqualTo(3L);
        assertThat(overview.get("articlesDue")).isEqualTo(5L);
        assertThat(overview.get("reviewCycleDays")).isEqualTo(180);
    }
}
