/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiKnowledgeBaseRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAiKnowledgeBaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * SCRM AI 知识库数据访问层。
 * <p>
 * 提供按分页查询知识库的标准能力, 知识库分类/启用状态过滤通过
 * {@link JpaSpecificationExecutor} 在 Service 层动态拼装。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAiKnowledgeBaseRepository extends JpaRepository<ScrmAiKnowledgeBaseEntity, Long>,
        JpaSpecificationExecutor<ScrmAiKnowledgeBaseEntity> {
}
