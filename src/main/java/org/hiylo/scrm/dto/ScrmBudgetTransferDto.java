/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetTransferDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 营销预算调拨 DTO。
 * <p>
 * 预算调拨接口入参: 从源分配 ({@code fromAllocationId}) 调拨金额到目标分配
 * ({@code toAllocationId}), 附带调拨原因。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmBudgetTransferDto {

    /** 源分配 ID */
    @NotNull(message = "源分配 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromAllocationId;

    /** 目标分配 ID */
    @NotNull(message = "目标分配 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toAllocationId;

    /** 调拨金额 */
    @NotNull(message = "调拨金额不能为空")
    private Double amount;

    /** 调拨原因 (可空) */
    @Size(max = 500, message = "调拨原因长度不能超过 500")
    private String reason;
}
