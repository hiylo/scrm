/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiKnowledgeDocumentRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAiKnowledgeDocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * SCRM AI 知识库文档数据访问层。
 * <p>
 * 提供按知识库分页查询文档, 关键词模糊匹配检索, 以及增量更新浏览次数与最近使用时间等能力,
 * 供 {@code ScrmAiAssistantService} 文档管理与知识库搜索使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAiKnowledgeDocumentRepository extends JpaRepository<ScrmAiKnowledgeDocumentEntity, Long>,
        JpaSpecificationExecutor<ScrmAiKnowledgeDocumentEntity> {

    /**
     * 按知识库 ID 分页查询启用文档 (按创建时间倒序)。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param pageable       分页参数
     * @return 文档分页结果
     */
    Page<ScrmAiKnowledgeDocumentEntity> findByKnowledgeBaseIdAndEnabledTrue(Long knowledgeBaseId, Pageable pageable);

    /**
     * 按知识库 ID 与标题/内容关键词模糊匹配分页查询 (按创建时间倒序)。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param titleKeyword   标题关键词
     * @param contentKeyword 内容关键词
     * @param pageable       分页参数
     * @return 文档分页结果
     */
    @Query(value = "SELECT d FROM ScrmAiKnowledgeDocumentEntity d WHERE d.knowledgeBaseId = :knowledgeBaseId AND "
                          + "d.enabled = true AND (d.title LIKE :titleKeyword OR d.content LIKE :contentKeyword)")
    Page<ScrmAiKnowledgeDocumentEntity> searchByKeyword(
                                                        @Param("knowledgeBaseId") Long knowledgeBaseId,
                                                        @Param("titleKeyword") String titleKeyword,
                                                        @Param("contentKeyword") String contentKeyword,
                                                        Pageable pageable);

    /**
     * 统计知识库内启用文档数量。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 启用文档数
     */
    long countByKnowledgeBaseIdAndEnabledTrue(Long knowledgeBaseId);

    /**
     * 增量更新文档浏览次数与最近使用时间 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param documentId 文档 ID
     * @param usedAt     使用时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmAiKnowledgeDocumentEntity d SET d.viewCount = d.viewCount + 1, d.lastUsedAt = :usedAt "
                          + "WHERE d.id = :documentId")
    int incrementViewCount(@Param("documentId") Long documentId, @Param("usedAt") LocalDateTime usedAt);
}
