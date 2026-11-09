/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmFixedAssetRepository.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmFixedAssetEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 固定资产卡片数据访问。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmFixedAssetRepository extends JpaRepository<ScrmFixedAssetEntity, Long> {

    /** 按企业分页查询（按创建时间倒序） */
    Page<ScrmFixedAssetEntity> findByCompanyIdOrderByCreateTimeDesc(Long companyId, Pageable pageable);

    /** 企业全部在用资产（折旧计提用） */
    List<ScrmFixedAssetEntity> findByCompanyIdAndStatusOrderByCreateTimeDesc(
            Long companyId, String status);

    /** 企业资产总数 */
    long countByCompanyId(Long companyId);

    /** 企业 + ID */
    Optional<ScrmFixedAssetEntity> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * 查询拥有指定状态资产的企业 ID 集合（去重）。
     * <p>批量计提时用于「管理角色 → 全部有在管资产的企业」，避免遍历所有主体。</p>
     *
     * @param status 资产状态（如 ACTIVE）
     * @return 企业 ID 列表（可能为空）
     */
    @Query("select distinct a.companyId from ScrmFixedAssetEntity a where a.status = :status")
    List<Long> findDistinctCompanyIdsByStatus(@Param("status") String status);
}