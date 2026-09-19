/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractTemplateRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmContractTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * SCRM 合同模板数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmContractTemplateRepository extends JpaRepository<ScrmContractTemplateEntity, Long>,
        JpaSpecificationExecutor<ScrmContractTemplateEntity> {

    /**
     * 按模板编码查询合同模板 (全局唯一)。
     *
     * @param templateCode 模板编码
     * @return 合同模板 (可能为空)
     */
    Optional<ScrmContractTemplateEntity> findByTemplateCode(String templateCode);
}
