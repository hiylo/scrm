/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractApproveDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 合同审批请求 DTO。
 * <p>
 * {@code action} 为 APPROVE 时审批通过, 合同状态流转至 PENDING_SIGNATURE;
 * 为 REJECT 时审批驳回, 合同状态回退至 DRAFT 并记录驳回原因。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmContractApproveDto {

    /** 合同 ID */
    @NotNull(message = "合同 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 审批动作: APPROVE (通过) / REJECT (驳回) */
    @NotBlank(message = "审批动作不能为空")
    @Pattern(regexp = "APPROVE|REJECT", message = "审批动作仅支持 APPROVE/REJECT")
    private String action;

    /** 审批意见 (可空) */
    @Size(max = 500, message = "审批意见长度不能超过 500")
    private String comment;

    /** 审批人 ID (可空) */
    @Size(max = 100, message = "审批人 ID 长度不能超过 100")
    private String approverId;

    /** 审批人名称 (可空) */
    @Size(max = 100, message = "审批人名称长度不能超过 100")
    private String approverName;
}
