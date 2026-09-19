/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionApproveDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SCRM 销售佣金审批 DTO。
 * <p>
 * 审批接口入参: 指定待审批佣金记录 ID 列表与审批动作 (APPROVE 通过 / REJECT 驳回),
 * 可附带审批备注。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCommissionApproveDto {

    /** 待审批佣金记录 ID 列表 */
    @NotEmpty(message = "佣金记录 ID 列表不能为空")
    private List<Long> recordIds;

    /** 审批动作: APPROVE 通过 / REJECT 驳回 */
    @NotBlank(message = "审批动作不能为空")
    @Pattern(regexp = "APPROVE|REJECT",
            message = "审批动作仅支持 APPROVE/REJECT")
    private String action;

    /** 审批备注 (可空) */
    @Size(max = 500, message = "审批备注长度不能超过 500")
    private String note;
}
