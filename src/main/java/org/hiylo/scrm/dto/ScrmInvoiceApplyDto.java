/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceApplyDto.java
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

/**
 * SCRM 发票申请请求 DTO。
 * <p>
 * 用于客户/订单场景下提交发票申请, 由服务层生成申请编号与发票编号, 计算税额与价税合计,
 * 创建状态为 PENDING 的发票实例并写入请求上下文。抬头类型为 ENTERPRISE 时建议填写税号。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInvoiceApplyDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 关联订单 ID (可空) */
    @Size(max = 100, message = "关联订单 ID 长度不能超过 100")
    private String orderId;

    /** 发票类型: GENERAL / SPECIAL / ELECTRONIC / PLAIN_DIGITAL / RED_REDUCED */
    @NotBlank(message = "发票类型不能为空")
    @Size(max = 30, message = "发票类型长度不能超过 30")
    @Pattern(regexp = "GENERAL|SPECIAL|ELECTRONIC|PLAIN_DIGITAL|RED_REDUCED",
            message = "发票类型仅支持 GENERAL/SPECIAL/ELECTRONIC/PLAIN_DIGITAL/RED_REDUCED")
    private String invoiceType;

    /** 抬头类型: PERSONAL / ENTERPRISE */
    @NotBlank(message = "抬头类型不能为空")
    @Size(max = 20, message = "抬头类型长度不能超过 20")
    @Pattern(regexp = "PERSONAL|ENTERPRISE", message = "抬头类型仅支持 PERSONAL/ENTERPRISE")
    private String titleType;

    /** 发票抬头 */
    @NotBlank(message = "发票抬头不能为空")
    @Size(max = 500, message = "发票抬头长度不能超过 500")
    private String invoiceTitle;

    /** 税号 (可空, ENTERPRISE 抬头建议填写) */
    @Size(max = 50, message = "税号长度不能超过 50")
    private String taxNumber;

    /** 发票金额 (不含税) */
    @NotNull(message = "发票金额不能为空")
    @PositiveOrZero(message = "发票金额不能为负数")
    private Double amount;

    /** 税率 (0-1, 如 0.13, 可空则按 0 处理) */
    @PositiveOrZero(message = "税率不能为负数")
    private Double taxRate;

    /** JSON 发票明细: [{name,spec,unit,quantity,price,amount,taxRate,taxAmount}] (可空) */
    private String invoiceItems;

    /** 申请原因 (可空) */
    @Size(max = 500, message = "申请原因长度不能超过 500")
    private String applyReason;

    /** 交付方式: EMAIL / MAIL / SELF / DIGITAL (可空) */
    @Size(max = 20, message = "交付方式长度不能超过 20")
    @Pattern(regexp = "EMAIL|MAIL|SELF|DIGITAL|",
            message = "交付方式仅支持 EMAIL/MAIL/SELF/DIGITAL")
    private String deliveryMethod;

    /** 申请人 (可空) */
    @Size(max = 100, message = "申请人长度不能超过 100")
    private String appliedBy;
}
