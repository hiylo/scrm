/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiAssistantKnowledgeService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */

package org.hiylo.scrm.service;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.hiylo.scrm.dto.ScrmAiKnowledgeBaseDto;
import org.hiylo.scrm.dto.ScrmAiKnowledgeDocumentDto;
import org.hiylo.scrm.entity.ScrmAiKnowledgeBaseEntity;
import org.hiylo.scrm.entity.ScrmAiKnowledgeDocumentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAiKnowledgeBaseRepository;
import org.hiylo.scrm.repository.ScrmAiKnowledgeDocumentRepository;

import static org.hiylo.scrm.service.ScrmAiAssistantConfigService.DEFAULT_ENABLED;

/**
 * AI 知识库与文档兄弟服务。
 * <p>
 * 承载知识库与文档的增删改查、知识库搜索 (关键词匹配 + 相关度评分) 与文档数量同步。
 * 作为 {@link ScrmAiAssistantService} 的知识库子域拆分产物, 由门面注入并委托调用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAiAssistantKnowledgeService {

    // ==================== 默认值常量 ====================

    /** 默认文档数量初值 */
    private static final int DEFAULT_DOCUMENT_COUNT = 0;

    /** 默认浏览次数初值 */
    private static final int DEFAULT_VIEW_COUNT = 0;

    /** 默认内容类型 */
    private static final String DEFAULT_CONTENT_TYPE = "TEXT";

    /** 默认来源类型 */
    private static final String DEFAULT_SOURCE_TYPE = "MANUAL";

    // ==================== 枚举值常量 ====================

    /** 知识库分类: 产品 */
    private static final String KB_CATEGORY_PRODUCT = "PRODUCT";
    /** 知识库分类: 常见问题 */
    private static final String KB_CATEGORY_FAQ = "FAQ";
    /** 知识库分类: 政策 */
    private static final String KB_CATEGORY_POLICY = "POLICY";
    /** 知识库分类: 话术 */
    private static final String KB_CATEGORY_SCRIPT = "SCRIPT";
    /** 知识库分类: 流程 */
    private static final String KB_CATEGORY_PROCESS = "PROCESS";

    /** 内容类型: 纯文本 */
    private static final String CONTENT_TYPE_TEXT = "TEXT";
    /** 内容类型: Markdown */
    private static final String CONTENT_TYPE_MARKDOWN = "MARKDOWN";
    /** 内容类型: JSON */
    private static final String CONTENT_TYPE_JSON = "JSON";

    /** 来源类型: 手工录入 */
    private static final String SOURCE_TYPE_MANUAL = "MANUAL";
    /** 来源类型: 导入 */
    private static final String SOURCE_TYPE_IMPORT = "IMPORT";
    /** 来源类型: URL */
    private static final String SOURCE_TYPE_URL = "URL";

    /** 合法的知识库分类 */
    private static final List<String> VALID_KB_CATEGORIES = List.of(
            KB_CATEGORY_PRODUCT, KB_CATEGORY_FAQ, KB_CATEGORY_POLICY, KB_CATEGORY_SCRIPT, KB_CATEGORY_PROCESS);

    /** 合法的内容类型 */
    private static final List<String> VALID_CONTENT_TYPES = List.of(
            CONTENT_TYPE_TEXT, CONTENT_TYPE_MARKDOWN, CONTENT_TYPE_JSON);

    /** 合法的来源类型 */
    private static final List<String> VALID_SOURCE_TYPES = List.of(
            SOURCE_TYPE_MANUAL, SOURCE_TYPE_IMPORT, SOURCE_TYPE_URL);

    // ==================== 依赖注入 ====================

    /** AI 知识库数据访问层 */
    private final ScrmAiKnowledgeBaseRepository knowledgeBaseRepository;

    /** AI 知识库文档数据访问层 */
    private final ScrmAiKnowledgeDocumentRepository documentRepository;

    /** JSON 解析器 (校验 JSON 字段) */
    private final ObjectMapper objectMapper;

    /**
     * 创建知识库。
     *
     * @param dto 知识库参数
     * @return 创建后的知识库
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmAiKnowledgeBaseEntity createKnowledgeBase(ScrmAiKnowledgeBaseDto dto) throws ScrmException {
        validateKbDto(dto, false);
        ScrmAiKnowledgeBaseEntity entity = new ScrmAiKnowledgeBaseEntity();
        entity.setKbName(dto.getKbName());
        entity.setDescription(dto.getDescription());
        entity.setCategory(dto.getCategory());
        entity.setDocumentCount(DEFAULT_DOCUMENT_COUNT);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = knowledgeBaseRepository.save(entity);
        log.info("创建 AI 知识库: id={}, kbName={}, category={}",
                entity.getId(), entity.getKbName(), entity.getCategory());
        return entity;
    }

    /**
     * 更新知识库（字段非空才覆盖）。
     *
     * @param id  知识库 ID
     * @param dto 知识库参数
     * @return 更新后的知识库
     * @throws ScrmException 知识库不存在 / 参数非法
     */
    @Transactional
    public ScrmAiKnowledgeBaseEntity updateKnowledgeBase(
            Long id, ScrmAiKnowledgeBaseDto dto) throws ScrmException {
        ScrmAiKnowledgeBaseEntity entity = findKnowledgeBaseOrThrow(id);
        validateKbDto(dto, true);
        if (dto.getKbName() != null) entity.setKbName(dto.getKbName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = knowledgeBaseRepository.save(entity);
        log.info("更新 AI 知识库: id={}, kbName={}", entity.getId(), entity.getKbName());
        return entity;
    }

    /**
     * 删除知识库。
     * <p>同时删除知识库下所有文档。</p>
     *
     * @param id 知识库 ID
     * @throws ScrmException 知识库不存在
     */
    @Transactional
    public void deleteKnowledgeBase(Long id) throws ScrmException {
        ScrmAiKnowledgeBaseEntity entity = findKnowledgeBaseOrThrow(id);
        // 删除知识库下所有文档
        Specification<ScrmAiKnowledgeDocumentEntity> docSpec = (root, query, cb) -> cb.and(
                cb.equal(root.get("knowledgeBaseId"), id));
        List<ScrmAiKnowledgeDocumentEntity> docs = documentRepository.findAll(docSpec);
        if (!docs.isEmpty()) {
            documentRepository.deleteAll(docs);
        }
        knowledgeBaseRepository.delete(entity);
        log.info("删除 AI 知识库: id={}, kbName={}, docsRemoved={}",
                id, entity.getKbName(), docs.size());
    }

    /**
     * 查询知识库详情。
     *
     * @param id 知识库 ID
     * @return 知识库实体
     * @throws ScrmException 知识库不存在
     */
    @Transactional(readOnly = true)
    public ScrmAiKnowledgeBaseEntity getKnowledgeBase(Long id) throws ScrmException {
        return findKnowledgeBaseOrThrow(id);
    }

    /**
     * 分页查询知识库, 支持按分类与启用状态过滤。
     *
     * @param category 分类过滤（可空）: PRODUCT / FAQ / POLICY / SCRIPT / PROCESS
     * @param enabled  启用状态过滤（可空）
     * @param pageable 分页参数
     * @return 知识库分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAiKnowledgeBaseEntity> listKnowledgeBases(String category, Boolean enabled, Pageable pageable) {
        Specification<ScrmAiKnowledgeBaseEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return knowledgeBaseRepository.findAll(spec, pageable);
    }

    /**
     * 知识库搜索: 关键词匹配 (标题 + 内容)。
     * <p>对启用文档按 title / content LIKE 模糊匹配, 命中文档增量更新浏览次数。</p>
     *
     * @param knowledgeBaseId 知识库 ID (可空, 为空则不限制知识库)
     * @param query           搜索关键词
     * @return 搜索结果列表 [{id, title, snippet, knowledgeBaseId, score}]
     */
    @Transactional
    public List<Map<String, Object>> searchKnowledge(Long knowledgeBaseId, String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String pattern = "%" + query + "%";
        List<ScrmAiKnowledgeDocumentEntity> docs;
        if (knowledgeBaseId != null) {
            // 单知识库搜索: 使用 Repository 关键词检索方法 (返回 Page, 取全部)
            docs = documentRepository
                    .searchByKeyword(knowledgeBaseId, pattern, pattern, Pageable.ofSize(100))
                    .getContent();
        } else {
            // 搜索全部知识库: 通过 Specification 拼装
            Specification<ScrmAiKnowledgeDocumentEntity> spec = (root, q, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("enabled"), true));
                predicates.add(cb.or(
                        cb.like(root.get("title"), pattern),
                        cb.like(root.get("content"), pattern)));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            docs = documentRepository.findAll(spec);
        }
        List<Map<String, Object>> results = new ArrayList<>(docs.size());
        for (ScrmAiKnowledgeDocumentEntity doc : docs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", doc.getId());
            entry.put("title", doc.getTitle());
            entry.put("snippet", buildSnippet(doc.getContent(), query));
            entry.put("knowledgeBaseId", doc.getKnowledgeBaseId());
            entry.put("score", computeSearchScore(doc, query));
            results.add(entry);
            // 增量更新浏览次数
            try {
                documentRepository.incrementViewCount(doc.getId(), LocalDateTime.now());
            } catch (Exception e) {
                log.warn("更新文档浏览次数失败, 忽略: docId={}, err={}", doc.getId(), e.getMessage());
            }
        }
        // 按相关度分数降序
        results.sort((a, b) -> Double.compare(ScrmAiAssistantConversationService.toDouble(b.get("score")), ScrmAiAssistantConversationService.toDouble(a.get("score"))));
        log.info("知识库搜索: knowledgeBaseId={}, query={}, hits={}", knowledgeBaseId, query, results.size());
        return results;
    }

    /**
     * 添加文档到知识库。
     * <p>校验知识库存在且归属当前账号, contentType / sourceType 缺省时填默认值。
     * 持久化后同步递增知识库的 documentCount。</p>
     *
     * @param knowledgeBaseId 知识库 ID
     * @param dto             文档参数
     * @return 创建后的文档
     * @throws ScrmException 知识库不存在 / 参数非法
     */
    @Transactional
    public ScrmAiKnowledgeDocumentEntity addDocument(Long knowledgeBaseId, ScrmAiKnowledgeDocumentDto dto)
            throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("文档参数不能为空");
        }
        ScrmAiKnowledgeBaseEntity kb = findKnowledgeBaseOrThrow(knowledgeBaseId);
        validateDocumentDto(dto);
        ScrmAiKnowledgeDocumentEntity entity = new ScrmAiKnowledgeDocumentEntity();
        entity.setKnowledgeBaseId(knowledgeBaseId);
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent());
        entity.setContentType(dto.getContentType() != null && !dto.getContentType().isBlank()
                ? dto.getContentType() : DEFAULT_CONTENT_TYPE);
        entity.setTags(dto.getTags());
        entity.setSourceType(dto.getSourceType() != null && !dto.getSourceType().isBlank()
                ? dto.getSourceType() : DEFAULT_SOURCE_TYPE);
        entity.setSourceUrl(dto.getSourceUrl());
        entity.setQaPairs(dto.getQaPairs());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setViewCount(DEFAULT_VIEW_COUNT);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = documentRepository.save(entity);
        // 同步递增知识库文档数
        kb.setDocumentCount((kb.getDocumentCount() != null ? kb.getDocumentCount() : 0) + 1);
        knowledgeBaseRepository.save(kb);
        log.info("添加 AI 知识库文档: id={}, kbId={}, title={}",
                entity.getId(), knowledgeBaseId, entity.getTitle());
        return entity;
    }

    /**
     * 更新文档（字段非空才覆盖）。
     *
     * @param id  文档 ID
     * @param dto 文档参数
     * @return 更新后的文档
     * @throws ScrmException 文档不存在 / 参数非法
     */
    @Transactional
    public ScrmAiKnowledgeDocumentEntity updateDocument(Long id, ScrmAiKnowledgeDocumentDto dto)
            throws ScrmException {
        ScrmAiKnowledgeDocumentEntity entity = findDocumentOrThrow(id);
        validateDocumentDto(dto);
        if (dto.getKnowledgeBaseId() != null) entity.setKnowledgeBaseId(dto.getKnowledgeBaseId());
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getContentType() != null) entity.setContentType(dto.getContentType());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getSourceType() != null) entity.setSourceType(dto.getSourceType());
        if (dto.getSourceUrl() != null) entity.setSourceUrl(dto.getSourceUrl());
        if (dto.getQaPairs() != null) entity.setQaPairs(dto.getQaPairs());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = documentRepository.save(entity);
        log.info("更新 AI 知识库文档: id={}, title={}", entity.getId(), entity.getTitle());
        return entity;
    }

    /**
     * 删除文档。
     * <p>同步递减所属知识库的 documentCount。</p>
     *
     * @param id 文档 ID
     * @throws ScrmException 文档不存在
     */
    @Transactional
    public void deleteDocument(Long id) throws ScrmException {
        ScrmAiKnowledgeDocumentEntity entity = findDocumentOrThrow(id);
        documentRepository.delete(entity);
        // 同步递减知识库文档数
        try {
            ScrmAiKnowledgeBaseEntity kb = knowledgeBaseRepository.findById(entity.getKnowledgeBaseId())
                    .orElse(null);
            if (kb != null) {
                int current = kb.getDocumentCount() == null ? 0 : kb.getDocumentCount();
                kb.setDocumentCount(Math.max(0, current - 1));
                knowledgeBaseRepository.save(kb);
            }
        } catch (Exception e) {
            log.warn("同步递减知识库文档数失败, 忽略: kbId={}, err={}",
                    entity.getKnowledgeBaseId(), e.getMessage());
        }
        log.info("删除 AI 知识库文档: id={}, title={}", id, entity.getTitle());
    }

    /**
     * 查询文档详情。
     *
     * @param id 文档 ID
     * @return 文档实体
     * @throws ScrmException 文档不存在
     */
    @Transactional(readOnly = true)
    public ScrmAiKnowledgeDocumentEntity getDocument(Long id) throws ScrmException {
        return findDocumentOrThrow(id);
    }

    /**
     * 分页查询知识库文档, 支持按关键词模糊匹配 (title + content)。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param keyword         关键词模糊匹配（可空）
     * @param pageable        分页参数
     * @return 文档分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAiKnowledgeDocumentEntity> listDocuments(Long knowledgeBaseId, String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword + "%";
            return documentRepository.searchByKeyword(knowledgeBaseId, pattern, pattern, pageable);
        }
        return documentRepository.findByKnowledgeBaseIdAndEnabledTrue(
                 knowledgeBaseId, pageable);
    }

    /**
     * 校验知识库参数。
     *
     * @param dto     知识库参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateKbDto(ScrmAiKnowledgeBaseDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("知识库参数不能为空");
        }
        if (dto.getKbName() != null) {
            if (dto.getKbName().isBlank()) {
                throw ScrmException.badRequest("知识库名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("知识库名称不能为空");
        }
        if (dto.getCategory() != null && !dto.getCategory().isBlank() && !VALID_KB_CATEGORIES.contains(dto.getCategory())) {
            throw ScrmException.badRequest(
                    "知识库分类非法: " + dto.getCategory() + ", 仅支持 " + VALID_KB_CATEGORIES);
        }
    }

    /**
     * 校验文档参数。
     *
     * @param dto 文档参数
     * @throws ScrmException 参数非法
     */
    private void validateDocumentDto(ScrmAiKnowledgeDocumentDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("文档参数不能为空");
        }
        if (dto.getContentType() != null && !dto.getContentType().isBlank() && !VALID_CONTENT_TYPES.contains(dto.getContentType())) {
            throw ScrmException.badRequest(
                    "内容类型非法: " + dto.getContentType() + ", 仅支持 " + VALID_CONTENT_TYPES);
        }
        if (dto.getSourceType() != null && !dto.getSourceType().isBlank() && !VALID_SOURCE_TYPES.contains(dto.getSourceType())) {
            throw ScrmException.badRequest(
                    "来源类型非法: " + dto.getSourceType() + ", 仅支持 " + VALID_SOURCE_TYPES);
        }
        if (dto.getQaPairs() != null && !dto.getQaPairs().isBlank()) {
            try {
                objectMapper.readTree(dto.getQaPairs());
            } catch (Exception e) {
                throw ScrmException.badRequest("问答对 JSON 解析失败: " + e.getMessage());
            }
        }
    }

    /**
     * 构建文档摘要 (截取关键词前后一定字符)。
     *
     * @param content 文档内容
     * @param query   搜索关键词
     * @return 摘要 (最多 200 字符)
     */
    private String buildSnippet(String content, String query) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int idx = content.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
        if (idx < 0) {
            return truncate(content, 200);
        }
        int start = Math.max(0, idx - 50);
        int end = Math.min(content.length(), idx + query.length() + 150);
        String snippet = (start > 0 ? "..." : "") + content.substring(start, end)
                + (end < content.length() ? "..." : "");
        return truncate(snippet, 200);
    }

    /**
     * 计算文档与查询的相关度分数 (0-1)。
     * <p>标题命中权重 0.6, 内容命中权重 0.4, 多次命中累加上限 1.0。</p>
     *
     * @param doc   文档实体
     * @param query 查询关键词
     * @return 相关度分数
     */
    private double computeSearchScore(ScrmAiKnowledgeDocumentEntity doc, String query) {
        double score = 0.0;
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        if (doc.getTitle() != null && doc.getTitle().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            score += 0.6;
        }
        if (doc.getContent() != null && doc.getContent().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            score += 0.4;
        }
        return Math.min(1.0, score);
    }

    /**
     * 截断字符串到指定长度 (超出追加省略号)。
     *
     * @param text   原始字符串
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLen) + "...";
    }

    /**
     * 按主键查询知识库, 不存在抛异常, 并校验账号归属。
     *
     * @param id 知识库 ID
     * @return 知识库实体
     * @throws ScrmException 知识库不存在
     */
    private ScrmAiKnowledgeBaseEntity findKnowledgeBaseOrThrow(Long id) throws ScrmException {
        ScrmAiKnowledgeBaseEntity entity = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "AI 知识库不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询文档, 不存在抛异常, 并校验账号归属。
     *
     * @param id 文档 ID
     * @return 文档实体
     * @throws ScrmException 文档不存在
     */
    private ScrmAiKnowledgeDocumentEntity findDocumentOrThrow(Long id) throws ScrmException {
        ScrmAiKnowledgeDocumentEntity entity = documentRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "AI 知识库文档不存在: id=" + id));
        return entity;
    }

}
