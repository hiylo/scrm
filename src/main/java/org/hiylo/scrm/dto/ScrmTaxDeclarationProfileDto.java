/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationProfileDto.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 税种申报档案 DTO（报税日历域）。
 * <p>
 * 承载申报档案的创建/更新参数: 客户 + 税种 + 申报周期 + 纳税人类型 + 征期规则 +
 * 属地 + 有效期 + 申报主体。{@code taxType} 仅支持 VAT / IIT / CIT / SOCIAL_SECURITY。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmTaxDeclarationProfileDto {

    /** 主键 ID（查询返回时填充） */
    private Long id;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    private Long customerId;

    /** 客户名称（冗余, 可空由服务端补全） */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 税种: VAT / IIT / CIT / SOCIAL_SECURITY */
    @NotBlank(message = "税种不能为空")
    @Pattern(regexp = "VAT|IIT|CIT|SOCIAL_SECURITY", message = "税种仅支持 VAT/IIT/CIT/SOCIAL_SECURITY")
    private String taxType;

    /** 申报周期: MONTHLY / QUARTERLY / YEARLY / ANNUAL_SETTLEMENT */
    @Pattern(regexp = "MONTHLY|QUARTERLY|YEARLY|ANNUAL_SETTLEMENT",
            message = "申报周期仅支持 MONTHLY/QUARTERLY/YEARLY/ANNUAL_SETTLEMENT")
    private String declarationCycle;

    /** 纳税人类型: SMALL / GENERAL / INDIVIDUAL */
    @Pattern(regexp = "SMALL|GENERAL|INDIVIDUAL", message = "纳税人类型仅支持 SMALL/GENERAL/INDIVIDUAL")
    private String taxpayerType;

    /** 征期规则: DAY15 / DAY31 / DAY05_31 / LOCAL */
    @Pattern(regexp = "DAY15|DAY31|DAY05_31|LOCAL", message = "征期规则仅支持 DAY15/DAY31/DAY05_31/LOCAL")
    private String deadlineRule;

    /** 属地（可空） */
    @Size(max = 100, message = "属地长度不能超过 100")
    private String region;

    /** 有效开始日期 */
    @NotNull(message = "有效开始日期不能为空")
    private LocalDate startDate;

    /** 有效结束日期（可空） */
    private LocalDate endDate;

    /** 申报主体（可空） */
    @Size(max = 200, message = "申报主体长度不能超过 200")
    private String declarationOrg;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 状态: ACTIVE / PAUSED / CANCELED */
    @Pattern(regexp = "ACTIVE|PAUSED|CANCELED", message = "状态仅支持 ACTIVE/PAUSED/CANCELED")
    private String status;

    /** 最近人工复核日期 */
    private LocalDate lastVerifiedAt;
}
