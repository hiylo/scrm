/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReportResultRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmReportResultEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * SCRM 报表执行结果数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmReportResultRepository extends JpaRepository<ScrmReportResultEntity, Long> {

    /**
     * 按模板 ID 分页查询执行结果 (按执行时间倒序)。
     *
     * @param templateId 模板 ID
     * @param pageable   分页参数
     * @return 执行结果分页
     */
    Page<ScrmReportResultEntity> findByTemplateIdOrderByRunAtDesc(Long templateId, Pageable pageable);

    /**
     * 查询模板最近一次执行结果 (按执行时间倒序取首条)。
     *
     * @param templateId 模板 ID
     * @return 最近一次执行结果 (可能为空)
     */
    Optional<ScrmReportResultEntity> findFirstByTemplateIdOrderByRunAtDesc(Long templateId);
}
