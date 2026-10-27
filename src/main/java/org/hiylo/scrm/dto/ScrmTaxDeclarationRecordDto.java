/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationRecordDto.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 税种申报记录 DTO（报税日历域, 状态机）。
 * <p>
 * 承载申报记录展示与状态机动作参数: 申报完成（FILED, 回填申报税额/凭证号）、
 * 缴款完成（PAID, 回填实缴税额/缴款时间）、补报（OVERDUE → PAID）。日历视图
 * 仅做聚合查询, 前端交互按钮只允许会计角色（@RequirePermission 守卫）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmTaxDeclarationRecordDto {

    /** 记录 ID */
    private Long id;

    /** 档案 ID */
    private Long profileId;

    /** 客户 ID（冗余） */
    private Long customerId;

    /** 税种（冗余） */
    private String taxType;

    /** 所属期开始 */
    private LocalDate periodStart;

    /** 所属期结束 */
    private LocalDate periodEnd;

    /** 申报截止日期 */
    private LocalDate dueDate;

    /** 申报税额 */
    private BigDecimal taxAmount;

    /** 实缴税额 */
    private BigDecimal paidAmount;

    /** 状态: NOT_STARTED / PENDING / FILED / PAID / OVERDUE / ARCHIVED / CANCELED */
    @Pattern(regexp = "NOT_STARTED|PENDING|FILED|PAID|OVERDUE|ARCHIVED|CANCELED",
            message = "状态仅支持 NOT_STARTED/PENDING/FILED/PAID/OVERDUE/ARCHIVED/CANCELED")
    private String declarationStatus;

    /** 逾期天数 */
    private Integer overdueDays;

    /** 升级时间 */
    private LocalDateTime escalatedAt;

    /** 升级对象 */
    private String escalatedTo;

    /** 申报完成时间 */
    private LocalDateTime filedAt;

    /** 申报操作人 */
    private String filedBy;

    /** 缴款完成时间 */
    private LocalDateTime paidAt;

    /** 完税凭证号 */
    @Size(max = 100, message = "完税凭证号长度不能超过 100")
    private String taxReceiptNo;

    /** 生成来源 */
    private String source;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
