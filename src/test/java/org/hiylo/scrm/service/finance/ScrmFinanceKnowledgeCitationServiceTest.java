/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmFinanceKnowledgeCitationServiceTest.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance;

import org.hiylo.scrm.entity.ScrmAiKnowledgeDocumentEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.repository.ScrmAiKnowledgeDocumentRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 模块③ 引用溯源服务单元测试。
 * <p>
 * 覆盖: 单知识库引用构建（含规则编号/分类/复核日期提取）/ 空关键词返回空列表 /
 * 知识文章引用 / 免责行常量。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmFinanceKnowledgeCitationService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmFinanceKnowledgeCitationServiceTest {

    @Mock
    private ScrmAiKnowledgeDocumentRepository documentRepository;

    @Mock
    private ScrmKnowledgeArticleRepository articleRepository;

    private ScrmFinanceKnowledgeCitationService service;

    @BeforeEach
    void setUp() {
        service = new ScrmFinanceKnowledgeCitationService(documentRepository, articleRepository);
    }

    @Test
    @DisplayName("buildCitations: 单知识库检索命中文档并组装引用 (id/title/code/category/reviewedAt)")
    void buildCitations() {
        ScrmAiKnowledgeDocumentEntity doc = new ScrmAiKnowledgeDocumentEntity();
        doc.setId(100L);
        doc.setTitle("小规模纳税人报税时间");
        doc.setKnowledgeBaseId(1L);
        doc.setTags("代账报税规则,全国通用,T06");
        doc.setContent("# 小规模纳税人报税时间\n- 规则编号：R-ACCT-0001\n增值税按季申报，季度终了15日内。");
        doc.setLastReviewedAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        Page<ScrmAiKnowledgeDocumentEntity> page =
                new PageImpl<>(List.of(doc), PageRequest.of(0, 20), 1);
        when(documentRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(page);

        List<Map<String, Object>> citations = service.buildCitations(1L, "报税");

        assertThat(citations).hasSize(1);
        Map<String, Object> citation = citations.get(0);
        assertThat(citation.get("id")).isEqualTo(100L);
        assertThat(citation.get("title")).isEqualTo("小规模纳税人报税时间");
        assertThat(citation.get("articleCode")).isEqualTo("R-ACCT-0001");
        assertThat(citation.get("category")).isEqualTo("代账报税规则");
        assertThat(citation.get("knowledgeBaseId")).isEqualTo(1L);
        assertThat(citation.get("lastReviewedAt")).isNotNull();
        assertThat(citation.get("snippet").toString()).contains("报税");
    }

    @Test
    @DisplayName("buildCitations_blankQuery: 空关键词返回空列表")
    void buildCitations_blankQuery() {
        List<Map<String, Object>> citations = service.buildCitations(1L, "  ");
        assertThat(citations).isEmpty();
    }

    @Test
    @DisplayName("buildArticleCitations: 知识文章引用")
    void buildArticleCitations() {
        ScrmKnowledgeArticleEntity article = new ScrmKnowledgeArticleEntity();
        article.setId(200L);
        article.setTitle("注册公司需要什么材料");
        article.setArticleCode("R-BIZ-0001");
        article.setCategoryId(1L);
        article.setCategoryName("营业执照办理");
        article.setLastReviewedAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        Page<ScrmKnowledgeArticleEntity> page =
                new PageImpl<>(List.of(article), PageRequest.of(0, 5), 1);
        when(articleRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 5))))
                .thenReturn(page);

        List<Map<String, Object>> citations = service.buildArticleCitations("注册公司", 5);

        assertThat(citations).hasSize(1);
        assertThat(citations.get(0).get("articleCode")).isEqualTo("R-BIZ-0001");
        assertThat(citations.get(0).get("categoryName")).isEqualTo("营业执照办理");
    }

    @Test
    @DisplayName("disclaimer: 免责行包含『仅供参考』与『以最新政策及主管机关确认为准』")
    void disclaimer() {
        assertThat(service.disclaimer()).contains("仅供参考");
        assertThat(service.disclaimer()).contains("以最新政策及主管机关确认为准");
    }
}
