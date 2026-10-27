/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmVoucherSaveDto.java
 * Date : 2026/10/25 18:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 凭证保存入参 DTO（新建/修改草稿共用）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmVoucherSaveDto {

    /** 会计年度 */
    private Integer periodYear;

    /** 会计月份（1-12） */
    private Integer periodMonth;

    /** 凭证字（默认 记） */
    private String voucherWord;

    /** 凭证日期 */
    private LocalDate voucherDate;

    /** 附件张数 */
    private Integer attachmentCount;

    /** 备注 */
    private String remark;

    /** 分录列表（至少 2 行且借贷平衡） */
    private List<ScrmVoucherEntryDto> entries;
}
