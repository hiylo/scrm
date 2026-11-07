/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxWorkdayOverrideDto.java
 * Date : 2026/10/27 11:22:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 调休上班日维护入参/出参。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Getter
@Setter
public class ScrmTaxWorkdayOverrideDto {

    /** 记录 ID（新建时为空） */
    private Long id;

    /** 需上班的日期（通常是周末补班） */
    @NotNull(message = "上班日期不能为空")
    private LocalDate workDate;

    /** 说明 */
    @Size(max = 500, message = "说明不超过 500 个字符")
    private String remark;
}
