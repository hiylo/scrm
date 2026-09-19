/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitTemplateRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmVisitTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM 客户回访模板数据访问层。
 * <p>
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmVisitTemplateRepository extends JpaRepository<ScrmVisitTemplateEntity, Long>,
        JpaSpecificationExecutor<ScrmVisitTemplateEntity> {

    /**
     * 按模板编码查询 (数据隔离)。
     *
     * @param templateCode 模板编码
     * @return 模板实体 (不存在返回 empty)
     */
    Optional<ScrmVisitTemplateEntity> findByTemplateCode(String templateCode);
}
