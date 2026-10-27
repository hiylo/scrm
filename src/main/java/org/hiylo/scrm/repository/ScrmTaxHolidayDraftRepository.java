/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxHolidayDraftRepository.java
 * Date : 2026/10/27 14:15:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmTaxHolidayDraftEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 假期 AI 抽取草稿数据访问层。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Repository
public interface ScrmTaxHolidayDraftRepository extends JpaRepository<ScrmTaxHolidayDraftEntity, Long> {

    /**
     * 按年份与状态查询草稿。
     *
     * @param holidayYear 年份
     * @param draftStatus 状态
     * @return 草稿列表
     */
    List<ScrmTaxHolidayDraftEntity> findByHolidayYearAndDraftStatus(
            Integer holidayYear, String draftStatus);

    /**
     * 按年份查询全部草稿。
     *
     * @param holidayYear 年份
     * @return 草稿列表
     */
    List<ScrmTaxHolidayDraftEntity> findByHolidayYear(Integer holidayYear);

    /**
     * 按状态查询全部草稿（倒序，最近的在前面）。
     *
     * @param draftStatus 状态
     * @return 草稿列表
     */
    List<ScrmTaxHolidayDraftEntity> findByDraftStatusOrderByCreateTimeDesc(String draftStatus);

    /**
     * 按状态统计（自检用）。
     *
     * @param draftStatus 状态
     * @return 数量
     */
    long countByDraftStatus(String draftStatus);
}
