/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractPaymentDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 合同付款 DTO。
 * <p>
 * 用于合同付款的创建/更新入参与查询返回。付款编号全局唯一, 付款类型与状态使用枚举字符串,
 * 金额字段不允许为负数。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmContractPaymentDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 合同 ID */
    @NotNull(message = "合同 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 合同编号 (可空) */
    @Size(max = 100, message = "合同编号长度不能超过 100")
    private String contractNo;

    /** 付款编号 (唯一) */
    @NotBlank(message = "付款编号不能为空")
    @Size(max = 100, message = "付款编号长度不能超过 100")
    private String paymentNo;

    /** 付款名称 (可空) */
    @Size(max = 200, message = "付款名称长度不能超过 200")
    private String paymentName;

    /** 付款类型: ADVANCE / MILESTONE / PERIODIC / FINAL / DEPOSIT / REFUND / PENALTY */
    @NotBlank(message = "付款类型不能为空")
    @Size(max = 30, message = "付款类型长度不能超过 30")
    private String paymentType;

    /** 付款状态: PENDING / DUE / OVERDUE / PARTIAL / PAID / CANCELLED */
    @Size(max = 20, message = "付款状态长度不能超过 20")
    private String paymentStatus;

    /** 计划金额 */
    @PositiveOrZero(message = "计划金额不能为负数")
    private Double plannedAmount;

    /** 已付金额 */
    @PositiveOrZero(message = "已付金额不能为负数")
    private Double paidAmount;

    /** 未付金额 */
    @PositiveOrZero(message = "未付金额不能为负数")
    private Double unpaidAmount;

    /** 币种 (默认 CNY) */
    @Size(max = 10, message = "币种长度不能超过 10")
    private String currency;

    /** 税率 */
    @PositiveOrZero(message = "税率不能为负数")
    private Double taxRate;

    /** 税额 */
    @PositiveOrZero(message = "税额不能为负数")
    private Double taxAmount;

    /** 计划付款日期 */
    @NotNull(message = "计划付款日期不能为空")
    private LocalDate plannedDate;

    /** 实际付款日期 (可空) */
    private LocalDate actualDate;

    /** 到期日 (可空) */
    private LocalDate dueDate;

    /** 逾期天数 */
    @PositiveOrZero(message = "逾期天数不能为负数")
    private Integer overdueDays;

    /** 付款方式: BANK_TRANSFER / CHECK / CASH / CREDIT_CARD / ALIPAY / WECHAT / OTHER (可空) */
    @Size(max = 50, message = "付款方式长度不能超过 50")
    private String paymentMethod;

    /** 银行账户 (可空) */
    @Size(max = 200, message = "银行账户长度不能超过 200")
    private String bankAccount;

    /** 交易号 (可空) */
    @Size(max = 200, message = "交易号长度不能超过 200")
    private String transactionNo;

    /** 发票号 (可空) */
    @Size(max = 100, message = "发票号长度不能超过 100")
    private String invoiceNo;

    /** 是否已开票 */
    private Boolean invoiceIssued;

    /** 发票日期 (可空) */
    private LocalDate invoiceDate;

    /** 里程碑 (可空) */
    @Size(max = 200, message = "里程碑长度不能超过 200")
    private String milestone;

    /** 里程碑描述 (可空) */
    @Size(max = 500, message = "里程碑描述长度不能超过 500")
    private String milestoneDescription;

    /** 完成率 */
    @PositiveOrZero(message = "完成率不能为负数")
    private Double completionRate;

    /** 是否已发送提醒 */
    private Boolean reminderSent;

    /** 提醒日期 (可空) */
    private LocalDate reminderDate;

    /** 提醒次数 */
    @PositiveOrZero(message = "提醒次数不能为负数")
    private Integer reminderCount;

    /** 备注 (可空) */
    @Size(max = 1000, message = "备注长度不能超过 1000")
    private String notes;

    /** 附件 JSON (可空) */
    @Size(max = 1000, message = "附件长度不能超过 1000")
    private String attachments;

    /** 确认人 (可空) */
    @Size(max = 100, message = "确认人长度不能超过 100")
    private String confirmedBy;

    /** 确认时间 (可空) */
    private LocalDateTime confirmedAt;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
