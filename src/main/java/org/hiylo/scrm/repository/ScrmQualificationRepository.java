/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmQualificationRepository.java
 * Date : 2026/10/25 10:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmQualificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

/**
 * 资质许可数据访问层（合规域）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmQualificationRepository
        extends JpaRepository<ScrmQualificationEntity, Long>,
        JpaSpecificationExecutor<ScrmQualificationEntity> {

    /**
     * 按公司查询全部证照（公司 360 钻取，到期日升序）。
     *
     * @param companyId 公司主体 ID
     * @return 证照列表
     */
    List<ScrmQualificationEntity> findByCompanyIdOrderByExpireDateAsc(Long companyId);

    /**
     * 按状态 + 到期日区间查询（到期推进用）。
     *
     * @param status 状态
     * @param from   到期日下限（含）
     * @param to     到期日上限（含）
     * @return 证照列表
     */
    List<ScrmQualificationEntity> findByStatusAndExpireDateBetween(String status,
                                                                   LocalDate from, LocalDate to);

    /**
     * 按状态 + 到期日查询（提醒扫描用）。
     *
     * @param status   状态
     * @param expireDate 到期日
     * @return 证照列表
     */
    List<ScrmQualificationEntity> findByStatusAndExpireDate(String status, LocalDate expireDate);

    /**
     * 统计某主体关联的证照数量（公司 360 聚合）。
     *
     * @param companyId 公司主体 ID
     * @return 数量
     */
    long countByCompanyId(Long companyId);
}
