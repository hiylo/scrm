/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmFinanceKnowledgeCitationService.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmAiKnowledgeDocumentEntity;
import org.hiylo.scrm.entity.ScrmKnowledgeArticleEntity;
import org.hiylo.scrm.repository.ScrmAiKnowledgeDocumentRepository;
import org.hiylo.scrm.repository.ScrmKnowledgeArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块③ 引用溯源: AI 答复附知识条目来源。
 * <p>
 * 按立项方案 §2 模块③「引用溯源」与 §5 合规清单「AI 责任」, 对外答复需:
 * </p>
 * <ul>
 *   <li>标注「仅供参考，以最新政策/人工确认为准」免责行;</li>
 *   <li>附引用知识条目（id/title/编号/分类）, 规避代账专业责任。</li>
 * </ul>
 * <p>
 * 最小实现: 知识库搜索命中文档后, 组装引用列表并拼接免责行。该能力供
 * {@code ScrmAiAssistantService.searchKnowledge} 输出侧使用（答复带引用,
 * 不改动 AI 对话主链路）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmFinanceKnowledgeCitationService {

    /** 免责行 (对外答复固定追加) */
    public static final String DISCLAIMER =
            "以上内容仅供参考，以最新政策及主管机关确认为准。";

    /** 搜索结果单知识库关键词检索条数上限 */
    private static final int SEARCH_LIMIT = 20;

    /** AI 知识库文档数据访问层 */
    private final ScrmAiKnowledgeDocumentRepository documentRepository;

    /** 知识文章数据访问层 */
    private final ScrmKnowledgeArticleRepository articleRepository;

    /**
     * 检索知识库文档并按相关度排序, 返回引用列表 + 免责行。
     * <p>
     * 复用既有搜索语义（标题/内容 LIKE 匹配）; 若答复文本尚未带免责行,
     * 由调用方追加 {@link #DISCLAIMER}。
     * </p>
     *
     * @param knowledgeBaseId 知识库 ID (可空, 为空查询全部启用文档)
     * @param query           查询关键词
     * @return 引用列表 [{id, title, articleCode, category, knowledgeBaseId, lastReviewedAt, snippet}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> buildCitations(Long knowledgeBaseId, String query) {
        List<Map<String, Object>> citations = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return citations;
        }
        String pattern = "%" + query + "%";
        List<ScrmAiKnowledgeDocumentEntity> docs;
        if (knowledgeBaseId != null) {
            // 单知识库: 仅引用启用文档 (标题/内容命中)
            Specification<ScrmAiKnowledgeDocumentEntity> singleSpec = (root, q, cb) -> cb.and(
                    cb.equal(root.get("knowledgeBaseId"), knowledgeBaseId),
                    cb.equal(root.get("enabled"), true),
                    cb.or(cb.like(root.get("title"), pattern),
                            cb.like(root.get("content"), pattern)));
            Page<ScrmAiKnowledgeDocumentEntity> page = documentRepository.findAll(
                    singleSpec, PageRequest.of(0, SEARCH_LIMIT));
            docs = page.getContent();
        } else {
            Specification<ScrmAiKnowledgeDocumentEntity> spec = (root, q, cb) -> cb.and(
                    cb.equal(root.get("enabled"), true),
                    cb.or(cb.like(root.get("title"), pattern),
                            cb.like(root.get("content"), pattern)));
            docs = documentRepository.findAll(spec);
            if (docs.size() > SEARCH_LIMIT) {
                docs = docs.subList(0, SEARCH_LIMIT);
            }
        }
        for (ScrmAiKnowledgeDocumentEntity doc : docs) {
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("id", doc.getId());
            citation.put("title", doc.getTitle());
            citation.put("articleCode", extractArticleCode(doc));
            citation.put("category", extractCategory(doc));
            citation.put("knowledgeBaseId", doc.getKnowledgeBaseId());
            citation.put("lastReviewedAt", doc.getLastReviewedAt());
            citation.put("snippet", buildSnippet(doc.getContent(), query));
            citations.add(citation);
        }
        log.info("知识库引用溯源: knowledgeBaseId={}, query={}, citations={}",
                knowledgeBaseId, query, citations.size());
        return citations;
    }

    /**
     * 按知识文章检索构建引用列表。
     *
     * @param keyword 关键词
     * @param limit   返回条数
     * @return 引用列表 [{id, title, articleCode, categoryId, lastReviewedAt}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> buildArticleCitations(String keyword, int limit) {
        List<Map<String, Object>> citations = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return citations;
        }
        String pattern = "%" + keyword + "%";
        Specification<ScrmKnowledgeArticleEntity> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("status"), "PUBLISHED"),
                cb.or(cb.like(root.get("title"), pattern),
                        cb.like(root.get("content"), pattern)));
        int size = limit > 0 ? Math.min(limit, SEARCH_LIMIT) : SEARCH_LIMIT;
        Page<ScrmKnowledgeArticleEntity> page = articleRepository.findAll(
                spec, PageRequest.of(0, size));
        for (ScrmKnowledgeArticleEntity article : page.getContent()) {
            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("id", article.getId());
            citation.put("title", article.getTitle());
            citation.put("articleCode", article.getArticleCode());
            citation.put("categoryId", article.getCategoryId());
            citation.put("categoryName", article.getCategoryName());
            citation.put("lastReviewedAt", article.getLastReviewedAt());
            citations.add(citation);
        }
        return citations;
    }

    /**
     * 免责声明常量 (供答复拼接)。
     *
     * @return 免责行
     */
    public String disclaimer() {
        return DISCLAIMER;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 从文档正文/标签提取规则编号 (R-XXX-0000)。
     *
     * @param doc 文档
     * @return 规则编号, 无则 null
     */
    private String extractArticleCode(ScrmAiKnowledgeDocumentEntity doc) {
        String text = (doc.getTitle() == null ? "" : doc.getTitle()) + "\n"
                + (doc.getContent() == null ? "" : doc.getContent());
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("R-[A-Z]+-\\d{4,}")
                .matcher(text);
        return m.find() ? m.group() : null;
    }

    /**
     * 从标签提取分类。
     *
     * @param doc 文档
     * @return 分类, 无则 null
     */
    private String extractCategory(ScrmAiKnowledgeDocumentEntity doc) {
        if (doc.getTags() == null) {
            return null;
        }
        for (String tag : doc.getTags().split(",")) {
            String t = tag.trim();
            if (t.contains("营业执照") || t.contains("注销") || t.contains("资质") || t.contains("代账")) {
                return t;
            }
        }
        return null;
    }

    /**
     * 构建命中片段。
     *
     * @param content 文档内容
     * @param query   查询关键词
     * @return 摘要 (最多 200 字符)
     */
    private String buildSnippet(String content, String query) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String lowerContent = content.toLowerCase(java.util.Locale.ROOT);
        String lowerQuery = query.toLowerCase(java.util.Locale.ROOT);
        int idx = lowerContent.indexOf(lowerQuery);
        if (idx < 0) {
            return content.length() <= 200 ? content : content.substring(0, 200) + "...";
        }
        int start = Math.max(0, idx - 50);
        int end = Math.min(content.length(), idx + query.length() + 100);
        String snippet = (start > 0 ? "..." : "") + content.substring(start, end)
                + (end < content.length() ? "..." : "");
        return snippet.length() <= 200 ? snippet : snippet.substring(0, 200) + "...";
    }
}
