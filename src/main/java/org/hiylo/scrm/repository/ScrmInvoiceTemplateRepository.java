/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceTemplateRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmInvoiceTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM 发票模板数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmInvoiceTemplateRepository extends JpaRepository<ScrmInvoiceTemplateEntity, Long>,
        JpaSpecificationExecutor<ScrmInvoiceTemplateEntity> {

    /**
     * 按模板编码查询发票模板 (全局唯一)。
     *
     * @param templateCode 模板编码
     * @return 发票模板 (可能为空)
     */
    Optional<ScrmInvoiceTemplateEntity> findByTemplateCode(String templateCode);
}
