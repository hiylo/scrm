/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetExpenseApproveDto.java
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
 * SCRM 营销预算支出审批 DTO。
 * <p>
 * 审批接口入参: 指定待审批支出 ID 与审批动作 (APPROVE 通过 / REJECT 驳回),
 * 可附带审批备注。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmBudgetExpenseApproveDto {

    /** 待审批支出 ID */
    @NotNull(message = "支出 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long expenseId;

    /** 审批动作: APPROVE 通过 / REJECT 驳回 */
    @NotBlank(message = "审批动作不能为空")
    @Pattern(regexp = "APPROVE|REJECT",
            message = "审批动作仅支持 APPROVE/REJECT")
    private String action;

    /** 审批备注 (可空) */
    @Size(max = 500, message = "审批备注长度不能超过 500")
    private String comment;
}
