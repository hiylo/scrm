/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmRenewalTaskDto.java
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
 * 服务续费任务 DTO（续费流失域）。
 * <p>
 * 承载续费任务创建/展示参数与结果录入（RENEWED / LOST / CANCELLED）。{@code renewalOption}
 * = DISCOUNT / GRACE 时 approvalRequired=true, 审批通过后回填 discountPct / graceMonths /
 * renewalAmount 并转 APPROVED。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmRenewalTaskDto {

    /** 任务 ID */
    private Long id;

    /** 任务编号 RN-YYYYMMDD-序号 */
    private String taskNo;

    /** 待续约合同 ID */
    @NotNull(message = "合同 ID 不能为空")
    private Long contractId;

    /** 冗余: 合同编号 */
    private String contractNo;

    /** 冗余: 合同名称 */
    private String contractName;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 销售 ID */
    private String salesPersonId;

    /** 销售名称 */
    private String salesPersonName;

    /** 计划续费日期（=合同到期日） */
    private LocalDate plannedRenewalDate;

    /** 提醒窗口天数（默认 30） */
    private Integer remindDaysBefore;

    /** 续费选项: STANDARD / DISCOUNT / GRACE / CANCEL */
    @Pattern(regexp = "STANDARD|DISCOUNT|GRACE|CANCEL", message = "续费选项仅支持 STANDARD/DISCOUNT/GRACE/CANCEL")
    private String renewalOption;

    /** 折扣比例（审批通过后回填） */
    private BigDecimal discountPct;

    /** 赠期月数（审批通过后回填） */
    private Integer graceMonths;

    /** 续约价（审批通过后回填） */
    private BigDecimal renewalAmount;

    /** 关联审批实例 ID */
    private Long approvalInstanceId;

    /** 是否需审批 */
    private Boolean approvalRequired;

    /** 状态: PENDING / APPROVAL_PENDING / APPROVED / IN_PROGRESS / RENEWED / LOST / OVERDUE / CANCELLED */
    @Pattern(regexp = "PENDING|APPROVAL_PENDING|APPROVED|IN_PROGRESS|RENEWED|LOST|OVERDUE|CANCELLED",
            message = "状态仅支持 PENDING/APPROVAL_PENDING/APPROVED/IN_PROGRESS/RENEWED/LOST/OVERDUE/CANCELLED")
    private String status;

    /** 生成来源 */
    private String source;

    /** 上次提醒时间 */
    private LocalDateTime lastRemindedAt;

    /** 累计提醒次数 */
    private Integer remindCount;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;

    /** 创建时间 */
    private LocalDateTime createTime;
}
