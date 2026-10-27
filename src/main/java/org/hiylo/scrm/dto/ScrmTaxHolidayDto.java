/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxHolidayDto.java
 * Date : 2026/10/27 11:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 税务假期维护入参/出参。
 * <p>
 * 假期用起止日期表示（含首尾），不是「起始日 + 天数」：法定节假日每年由国务院公告，
 * 常伴随调休，直接给完整区间最不容易算错。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Getter
@Setter
public class ScrmTaxHolidayDto {

    /** 假期 ID（新建时为空） */
    private Long id;

    /** 假期名称 */
    @NotBlank(message = "假期名称不能为空")
    @Size(max = 100, message = "假期名称不超过 100 个字符")
    private String holidayName;

    /** 归属年份（不传时取起始日期的年份） */
    private Integer holidayYear;

    /** 假期起始日（含） */
    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    /** 假期结束日（含） */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    /** 假期类型：STATUTORY 法定 / ADJUSTED 调休放假 */
    private String holidayKind;

    /** 说明（如通知文号） */
    @Size(max = 500, message = "说明不超过 500 个字符")
    private String remark;
}
