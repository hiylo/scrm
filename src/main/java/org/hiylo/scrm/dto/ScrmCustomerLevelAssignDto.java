/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelAssignDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 客户等级手动分配请求 DTO。
 * <p>
 * 单个客户的等级手动分配入参, {@code levelId} 为目标等级 ID, {@code reason} 为变更原因 (可空)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCustomerLevelAssignDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 目标等级 ID */
    @NotNull(message = "等级 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long levelId;

    /** 变更原因 (可空) */
    @Size(max = 500, message = "变更原因长度不能超过 500")
    private String reason;
}
