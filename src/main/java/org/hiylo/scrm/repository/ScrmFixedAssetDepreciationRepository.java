/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmFixedAssetDepreciationRepository.java
 * Date : 2026/11/08 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmFixedAssetDepreciationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 固定资产折旧记录数据访问（幂等查询）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmFixedAssetDepreciationRepository
        extends JpaRepository<ScrmFixedAssetDepreciationEntity, Long> {

    /** 同资产同期间（幂等检查） */
    Optional<ScrmFixedAssetDepreciationEntity> findByAssetIdAndPeriodYearAndPeriodMonth(
            Long assetId, Integer periodYear, Integer periodMonth);

    /** 资产全部折旧记录（按期间倒序） */
    List<ScrmFixedAssetDepreciationEntity> findByAssetIdOrderByPeriodYearDescPeriodMonthDesc(
            Long assetId);
}