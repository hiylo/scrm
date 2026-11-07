/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmVoucherEntryDto.java
 * Date : 2026/10/25 18:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 凭证分录入参 DTO。
 * <p>
 * 借/贷金额分开传，服务层会校验「至多一个非零」，
 * 不接受用负数表达方向的传法。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmVoucherEntryDto {

    /** 科目编码（必须在该企业已启用科目内） */
    private String subjectCode;

    /** 摘要 */
    private String summary;

    /** 借方金额 */
    private BigDecimal debitAmount;

    /** 贷方金额 */
    private BigDecimal creditAmount;
}
