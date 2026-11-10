/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmAccountSubjectRepository.java
 * Date : 2026/10/25 18:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAccountSubjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 会计科目数据访问层（凭证域）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmAccountSubjectRepository
        extends JpaRepository<ScrmAccountSubjectEntity, Long> {

    /**
     * 按企业 + 科目编码查询（唯一键定位）。
     *
     * @param companyId   公司主体 ID
     * @param subjectCode 科目编码
     * @return 科目（可能为空）
     */
    Optional<ScrmAccountSubjectEntity> findByCompanyIdAndSubjectCode(Long companyId, String subjectCode);

    /**
     * 按企业查询全部科目（按编码升序，天然是树形顺序）。
     *
     * @param companyId 公司主体 ID
     * @return 科目列表
     */
    List<ScrmAccountSubjectEntity> findByCompanyIdOrderBySubjectCodeAsc(Long companyId);

    /**
     * 按企业 + 状态查询科目。
     *
     * @param companyId 公司主体 ID
     * @param status    状态
     * @return 科目列表
     */
    List<ScrmAccountSubjectEntity> findByCompanyIdAndStatusOrderBySubjectCodeAsc(
            Long companyId, String status);

    /**
     * 统计企业科目数（判断是否已初始化科目表）。
     *
     * @param companyId 公司主体 ID
     * @return 数量
     */
    long countByCompanyId(Long companyId);

    /**
     * 取企业当前最大科目编码（新增明细科目时推算编码用）。
     *
     * @param companyId 公司主体 ID
     * @param prefix    编码前缀
     * @return 最大编码，无匹配返回 null
     */
    @Query("select max(s.subjectCode) from ScrmAccountSubjectEntity s "
            + "where s.companyId = :companyId and s.subjectCode like concat(:prefix, '%')")
    String findMaxCodeByPrefix(@Param("companyId") Long companyId, @Param("prefix") String prefix);

    /**
     * 按主体统计科目数（期初录入总览用，一次查询避免逐户 N+1）。
     *
     * @return {@code [companyId, count]}
     */
    @Query("select s.companyId, count(s) from ScrmAccountSubjectEntity s group by s.companyId")
    List<Object[]> countByCompanyGrouped();
}
