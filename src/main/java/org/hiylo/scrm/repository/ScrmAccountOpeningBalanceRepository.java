/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmAccountOpeningBalanceRepository.java
 * Date : 2026/11/10 17:35:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAccountOpeningBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 期初余额数据访问层（V29）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmAccountOpeningBalanceRepository
        extends JpaRepository<ScrmAccountOpeningBalanceEntity, Long> {

    /**
     * 取某户全部期初余额（按科目编码升序）。
     *
     * @param companyId 公司主体 ID
     * @return 期初余额列表
     */
    List<ScrmAccountOpeningBalanceEntity> findByCompanyIdOrderBySubjectCodeAsc(Long companyId);

    /**
     * 统计某户是否已设置期初余额。
     *
     * @param companyId 公司主体 ID
     * @return 记录数
     */
    long countByCompanyId(Long companyId);

    /**
     * 删除某户全部期初余额（重设时整户替换）。
     *
     * @param companyId 公司主体 ID
     * @return 删除行数
     */
    @Modifying
    @Query("delete from ScrmAccountOpeningBalanceEntity o where o.companyId = :companyId")
    int deleteByCompanyId(@Param("companyId") Long companyId);
}
