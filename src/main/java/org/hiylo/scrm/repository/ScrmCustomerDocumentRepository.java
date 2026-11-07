/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmCustomerDocumentRepository.java
 * Date : 2026/10/25 16:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

/**
 * 客户资料归档数据访问层（资料域）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmCustomerDocumentRepository
        extends JpaRepository<ScrmCustomerDocumentEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerDocumentEntity> {

    /**
     * 查某公司全部资料（按上传时间倒序）。
     *
     * @param companyId 公司主体 ID
     * @return 资料列表
     */
    List<ScrmCustomerDocumentEntity> findByCompanyIdOrderByUploadedAtDesc(Long companyId);

    /**
     * 查某公司某类型下的有效资料（资料齐全度判定用）。
     *
     * @param companyId 公司主体 ID
     * @param docType   资料类型
     * @param status    状态
     * @return 资料列表
     */
    List<ScrmCustomerDocumentEntity> findByCompanyIdAndDocTypeAndStatus(
            Long companyId, String docType, String status);

    /**
     * 按状态 + 到期日区间查询（到期扫描用）。
     *
     * @param status 状态
     * @param from   到期日下限（含）
     * @param to     到期日上限（含）
     * @return 资料列表
     */
    List<ScrmCustomerDocumentEntity> findByStatusAndExpireDateBetween(
            String status, LocalDate from, LocalDate to);

    /**
     * 统计某公司某类型下的有效资料数量。
     *
     * @param companyId 公司主体 ID
     * @param docType   资料类型
     * @param status    状态
     * @return 数量
     */
    long countByCompanyIdAndDocTypeAndStatus(Long companyId, String docType, String status);

    /**
     * 查询已归档资料的公司 ID 去重清单（齐全度总览用）。
     *
     * @return 公司 ID 列表
     */
    @Query("select distinct d.companyId from ScrmCustomerDocumentEntity d "
            + "where d.status = org.hiylo.scrm.entity.ScrmCustomerDocumentEntity.STATUS_ACTIVE")
    List<Long> findDistinctActiveCompanyIds();
}
