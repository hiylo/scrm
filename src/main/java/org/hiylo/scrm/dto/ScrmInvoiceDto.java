/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SCRM 发票实例 DTO。
 * <p>
 * 用于发票实例的修改入参与查询返回。申请创建请使用 {@link ScrmInvoiceApplyDto},
 * 审批请使用 {@link ScrmInvoiceApproveDto}, 红冲请使用 {@link ScrmInvoiceRedFlushDto}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInvoiceDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 发票编号 (唯一) */
    @Size(max = 100, message = "发票编号长度不能超过 100")
    private String invoiceNo;

    /** 申请编号 (可空) */
    @Size(max = 100, message = "申请编号长度不能超过 100")
    private String applicationNo;

    /** 发票类型: GENERAL / SPECIAL / ELECTRONIC / PLAIN_DIGITAL / RED_REDUCED */
    @NotBlank(message = "发票类型不能为空")
    @Size(max = 30, message = "发票类型长度不能超过 30")
    @Pattern(regexp = "GENERAL|SPECIAL|ELECTRONIC|PLAIN_DIGITAL|RED_REDUCED",
            message = "发票类型仅支持 GENERAL/SPECIAL/ELECTRONIC/PLAIN_DIGITAL/RED_REDUCED")
    private String invoiceType;

    /** 发票类别: NORMAL / RED / SPECIAL_VAT / ELECTRONIC */
    @NotBlank(message = "发票类别不能为空")
    @Size(max = 30, message = "发票类别长度不能超过 30")
    @Pattern(regexp = "NORMAL|RED|SPECIAL_VAT|ELECTRONIC",
            message = "发票类别仅支持 NORMAL/RED/SPECIAL_VAT/ELECTRONIC")
    private String invoiceCategory;

    /** 抬头类型: PERSONAL / ENTERPRISE */
    @NotBlank(message = "抬头类型不能为空")
    @Size(max = 20, message = "抬头类型长度不能超过 20")
    @Pattern(regexp = "PERSONAL|ENTERPRISE", message = "抬头类型仅支持 PERSONAL/ENTERPRISE")
    private String titleType;

    /** 发票抬头 */
    @NotBlank(message = "发票抬头不能为空")
    @Size(max = 500, message = "发票抬头长度不能超过 500")
    private String invoiceTitle;

    /** 税号 (可空) */
    @Size(max = 50, message = "税号长度不能超过 50")
    private String taxNumber;

    /** 开户行名称 (可空) */
    @Size(max = 200, message = "开户行名称长度不能超过 200")
    private String bankName;

    /** 开户账号 (可空) */
    @Size(max = 100, message = "开户账号长度不能超过 100")
    private String bankAccount;

    /** 公司地址 (可空) */
    @Size(max = 500, message = "公司地址长度不能超过 500")
    private String companyAddress;

    /** 公司电话 (可空) */
    @Size(max = 50, message = "公司电话长度不能超过 50")
    private String companyPhone;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 客户联系方式 (可空) */
    @Size(max = 200, message = "客户联系方式长度不能超过 200")
    private String customerContact;

    /** 客户电话 (可空) */
    @Size(max = 50, message = "客户电话长度不能超过 50")
    private String customerPhone;

    /** 客户邮箱 (可空) */
    @Size(max = 200, message = "客户邮箱长度不能超过 200")
    private String customerEmail;

    /** 关联订单 ID (可空) */
    @Size(max = 100, message = "关联订单 ID 长度不能超过 100")
    private String orderId;

    /** 关联合同 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 发票金额 (不含税) */
    @PositiveOrZero(message = "发票金额不能为负数")
    private Double amount;

    /** 税率 (0-1, 如 0.13) */
    @PositiveOrZero(message = "税率不能为负数")
    private Double taxRate;

    /** 税额 */
    @PositiveOrZero(message = "税额不能为负数")
    private Double taxAmount;

    /** 价税合计 */
    @PositiveOrZero(message = "价税合计不能为负数")
    private Double totalAmount;

    /** 折扣金额 */
    @PositiveOrZero(message = "折扣金额不能为负数")
    private Double discountAmount;

    /** 实开金额 */
    @PositiveOrZero(message = "实开金额不能为负数")
    private Double actualAmount;

    /** 币种: CNY / USD / EUR 等, 默认 CNY */
    @Size(max = 10, message = "币种长度不能超过 10")
    private String currency;

    /** 开票日期 (可空) */
    private LocalDate invoiceDate;

    /** JSON 发票明细: [{name,spec,unit,quantity,price,amount,taxRate,taxAmount}] (可空) */
    private String invoiceItems;

    /** 发票备注 (可空) */
    @Size(max = 500, message = "发票备注长度不能超过 500")
    private String remark;

    /** 状态: PENDING / APPROVED / ISSUED / SENT / RECEIVED / VOIDED / RED_FLUSHED / REJECTED */
    @Pattern(regexp = "PENDING|APPROVED|ISSUED|SENT|RECEIVED|VOIDED|RED_FLUSHED|REJECTED",
            message = "状态仅支持 PENDING/APPROVED/ISSUED/SENT/RECEIVED/VOIDED/RED_FLUSHED/REJECTED")
    private String status;

    /** 申请原因 (可空) */
    @Size(max = 500, message = "申请原因长度不能超过 500")
    private String applyReason;

    /** 申请人 (可空) */
    @Size(max = 100, message = "申请人长度不能超过 100")
    private String appliedBy;

    /** 申请时间 (可空) */
    private LocalDateTime appliedAt;

    /** 审批人 (可空) */
    @Size(max = 100, message = "审批人长度不能超过 100")
    private String approvedBy;

    /** 审批时间 (可空) */
    private LocalDateTime approvedAt;

    /** 审批意见 (可空) */
    @Size(max = 500, message = "审批意见长度不能超过 500")
    private String approvalComment;

    /** 开票人 (可空) */
    @Size(max = 100, message = "开票人长度不能超过 100")
    private String issuedBy;

    /** 开票时间 (可空) */
    private LocalDateTime issuedAt;

    /** 发票文件 URL (可空) */
    @Size(max = 500, message = "发票文件 URL 长度不能超过 500")
    private String invoiceUrl;

    /** 发票图片 (可空) */
    @Size(max = 500, message = "发票图片长度不能超过 500")
    private String invoiceImage;

    /** 交付方式: EMAIL / MAIL / SELF / DIGITAL (可空) */
    @Size(max = 20, message = "交付方式长度不能超过 20")
    @Pattern(regexp = "EMAIL|MAIL|SELF|DIGITAL|",
            message = "交付方式仅支持 EMAIL/MAIL/SELF/DIGITAL")
    private String deliveryMethod;

    /** 交付状态: PENDING / SENT / DELIVERED / FAILED */
    @Pattern(regexp = "PENDING|SENT|DELIVERED|FAILED|",
            message = "交付状态仅支持 PENDING/SENT/DELIVERED/FAILED")
    private String deliveryStatus;

    /** 发送时间 (可空) */
    private LocalDateTime sentAt;

    /** 送达时间 (可空) */
    private LocalDateTime deliveredAt;

    /** 快递单号 (可空) */
    @Size(max = 200, message = "快递单号长度不能超过 200")
    private String trackingNumber;

    /** 邮寄地址 (可空) */
    @Size(max = 500, message = "邮寄地址长度不能超过 500")
    private String deliveryAddress;

    /** 收件人 (可空) */
    @Size(max = 100, message = "收件人长度不能超过 100")
    private String deliveryRecipient;

    /** 收件人电话 (可空) */
    @Size(max = 50, message = "收件人电话长度不能超过 50")
    private String deliveryPhone;

    /** 作废原因 (可空) */
    @Size(max = 500, message = "作废原因长度不能超过 500")
    private String voidReason;

    /** 作废人 (可空) */
    @Size(max = 100, message = "作废人长度不能超过 100")
    private String voidedBy;

    /** 作废时间 (可空) */
    private LocalDateTime voidedAt;

    /** 红冲原因 (可空) */
    @Size(max = 500, message = "红冲原因长度不能超过 500")
    private String redFlushReason;

    /** 红冲人 (可空) */
    @Size(max = 100, message = "红冲人长度不能超过 100")
    private String redFlushedBy;

    /** 红冲时间 (可空) */
    private LocalDateTime redFlushedAt;

    /** 原发票 ID (红冲关联, 可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long originalInvoiceId;

    /** 红冲发票 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long redFlushInvoiceId;

    /** 税务局代码 (可空) */
    @Size(max = 50, message = "税务局代码长度不能超过 50")
    private String taxBureauCode;

    /** 税务局名称 (可空) */
    @Size(max = 200, message = "税务局名称长度不能超过 200")
    private String taxBureauName;

    /** 设备号 (可空) */
    @Size(max = 100, message = "设备号长度不能超过 100")
    private String deviceNo;

    /** 发票代码 (可空) */
    @Size(max = 50, message = "发票代码长度不能超过 50")
    private String invoiceCode;

    /** 发票号码 (可空) */
    @Size(max = 50, message = "发票号码长度不能超过 50")
    private String invoiceNumber;

    /** 校验码 (可空) */
    @Size(max = 100, message = "校验码长度不能超过 100")
    private String checkCode;

    /** 二维码内容 (可空) */
    @Size(max = 1000, message = "二维码内容长度不能超过 1000")
    private String qrCode;

    /** 标签逗号分隔 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 备注 (可空) */
    @Size(max = 1000, message = "备注长度不能超过 1000")
    private String notes;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
