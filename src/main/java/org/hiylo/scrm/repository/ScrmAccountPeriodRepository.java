/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmAccountPeriodRepository.java
 * Date : 2026/10/25 21:05:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAccountPeriodEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 会计期间数据访问层（凭证域）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmAccountPeriodRepository
        extends JpaRepository<ScrmAccountPeriodEntity, Long> {

    /**
     * 按企业 + 年月查询期间。
     *
     * @param companyId   公司主体 ID
     * @param periodYear  年
     * @param periodMonth 月
     * @return 期间（可能为空 = 尚未建过期间记录，视同 OPEN）
     */
    Optional<ScrmAccountPeriodEntity> findByCompanyIdAndPeriodYearAndPeriodMonth(
            Long companyId, Integer periodYear, Integer periodMonth);

    /**
     * 查某企业全部期间（按年月升序）。
     *
     * @param companyId 公司主体 ID
     * @return 期间列表
     */
    List<ScrmAccountPeriodEntity> findByCompanyIdOrderByPeriodYearAscPeriodMonthAsc(Long companyId);
}
