/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceApproveDto.java
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
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 发票审批请求 DTO。
 * <p>
 * {@code action} 为 APPROVE 时审批通过, 发票状态由 PENDING 流转至 APPROVED;
 * 为 REJECT 时审批驳回, 状态流转至 REJECTED 并记录驳回原因。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmInvoiceApproveDto {

    /** 发票 ID */
    @NotNull(message = "发票 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long invoiceId;

    /** 审批动作: APPROVE (通过) / REJECT (驳回) */
    @NotBlank(message = "审批动作不能为空")
    @Pattern(regexp = "APPROVE|REJECT", message = "审批动作仅支持 APPROVE/REJECT")
    private String action;

    /** 审批意见 (可空) */
    @Size(max = 500, message = "审批意见长度不能超过 500")
    private String comment;

    /** 审批人 (可空) */
    @Size(max = 100, message = "审批人长度不能超过 100")
    private String approvedBy;
}
