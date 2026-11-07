/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmTaxHolidayControllerTest.java
 * Date : 2026/10/27 17:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import org.hiylo.scrm.entity.ScrmTaxHolidayEntity;
import org.hiylo.scrm.entity.ScrmTaxWorkdayOverrideEntity;
import org.hiylo.scrm.repository.ScrmTaxHolidayDraftRepository;
import org.hiylo.scrm.repository.ScrmTaxHolidayRepository;
import org.hiylo.scrm.repository.ScrmTaxWorkdayOverrideRepository;
import org.hiylo.scrm.service.finance.tax.ScrmTaxHolidayAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmTaxHolidayController} 数据自检单元测试。
 * <p>
 * 重点锁住「条数对但日期错」这类笔误的检出能力。历史问题：2026 年国庆调休补班日
 * 曾误录为 9/19（官方为 9/20），当时假期 7 条、补班 5 条，条数与正确数据完全一致，
 * 现有自检（只查条数）完全无感，错误数据在库中潜伏。因此补充语义交叉校验：
 * 补班日「本该放假、改为上班」，不可能落在假期区间内，一旦重叠必有一处录错。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("税务假期自检：条数之外还要查语义冲突")
class ScrmTaxHolidayControllerTest {

    /** 目标年份 */
    private static final int YEAR = 2026;

    @Mock
    private ScrmTaxHolidayRepository holidayRepository;

    @Mock
    private ScrmTaxWorkdayOverrideRepository workdayOverrideRepository;

    @Mock
    private ScrmTaxHolidayAiService aiService;

    @Mock
    private ScrmTaxHolidayDraftRepository draftRepository;

    private ScrmTaxHolidayController controller;

    @BeforeEach
    void setUp() {
        controller = new ScrmTaxHolidayController(
                holidayRepository, workdayOverrideRepository, aiService, draftRepository);
    }

    /** 构造一个假期实体 */
    private ScrmTaxHolidayEntity holiday(String name, String start, String end) {
        ScrmTaxHolidayEntity entity = new ScrmTaxHolidayEntity();
        entity.setHolidayName(name);
        entity.setHolidayYear(YEAR);
        entity.setStartDate(LocalDate.parse(start));
        entity.setEndDate(LocalDate.parse(end));
        return entity;
    }

    /** 构造一个调休上班日实体 */
    private ScrmTaxWorkdayOverrideEntity workday(String date) {
        ScrmTaxWorkdayOverrideEntity entity = new ScrmTaxWorkdayOverrideEntity();
        entity.setWorkDate(LocalDate.parse(date));
        entity.setRemark("调休上班");
        return entity;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runCoverage() throws Exception {
        var response = controller.coverage(YEAR);
        return (Map<String, Object>) response.getData();
    }

    @Test
    @DisplayName("7 假 5 补班且日期全对时 complete=true，无告警")
    void coverage_completeWhenConsistent() throws Exception {
        when(holidayRepository.findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(YEAR))
                .thenReturn(List.of(
                        holiday("元旦", "2026-01-01", "2026-01-03"),
                        holiday("春节", "2026-02-15", "2026-02-23"),
                        holiday("清明节", "2026-04-04", "2026-04-06"),
                        holiday("劳动节", "2026-05-01", "2026-05-05"),
                        holiday("端午节", "2026-06-19", "2026-06-21"),
                        holiday("中秋节", "2026-09-25", "2026-09-27"),
                        holiday("国庆节", "2026-10-01", "2026-10-07")));
        when(workdayOverrideRepository.findActiveBetween(any(), any()))
                .thenReturn(List.of(
                        workday("2026-01-04"), workday("2026-02-14"), workday("2026-02-28"),
                        workday("2026-05-09"), workday("2026-09-20"), workday("2026-10-10")));

        Map<String, Object> result = runCoverage();

        assertThat(result.get("complete")).isEqualTo(true);
        assertThat((List<?>) result.get("warnings")).isEmpty();
    }

    @Test
    @DisplayName("补班日落在假期区间内时报冲突：条数对也要查出来")
    void coverage_flagsWorkdayInsideHoliday() throws Exception {
        when(holidayRepository.findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(YEAR))
                .thenReturn(List.of(
                        holiday("元旦", "2026-01-01", "2026-01-03"),
                        holiday("春节", "2026-02-15", "2026-02-23"),
                        holiday("清明节", "2026-04-04", "2026-04-06"),
                        holiday("劳动节", "2026-05-01", "2026-05-05"),
                        holiday("端午节", "2026-06-19", "2026-06-21"),
                        holiday("中秋节", "2026-09-25", "2026-09-27"),
                        holiday("国庆节", "2026-10-01", "2026-10-07")));
        // 春节区间内被误录了一个「上班日」：同一天既放假又上班，必然有一处错
        when(workdayOverrideRepository.findActiveBetween(any(), any()))
                .thenReturn(List.of(
                        workday("2026-01-04"), workday("2026-02-16"), workday("2026-02-28"),
                        workday("2026-05-09"), workday("2026-09-20"), workday("2026-10-10")));

        Map<String, Object> result = runCoverage();

        assertThat(result.get("complete")).isEqualTo(false);
        List<?> warnings = (List<?>) result.get("warnings");
        assertThat(warnings).isNotEmpty();
        assertThat(warnings.toString()).contains("2026-02-16").contains("春节");
    }

    @Test
    @DisplayName("假期条数不足时仍告警（原有行为不回退）")
    void coverage_warnsWhenTooFewHolidays() throws Exception {
        when(holidayRepository.findByHolidayYearAndIsActiveTrueOrderByStartDateAsc(YEAR))
                .thenReturn(List.of(holiday("元旦", "2026-01-01", "2026-01-03")));
        when(workdayOverrideRepository.findActiveBetween(any(), any())).thenReturn(List.of());

        Map<String, Object> result = runCoverage();

        assertThat(result.get("complete")).isEqualTo(false);
        assertThat(((List<?>) result.get("warnings")).toString()).contains("仅有 1 个假期");
    }
}
