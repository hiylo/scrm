/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOrderStatusDto.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 订单状态流转请求 DTO。
 * <p>
 * {@code orderId} 指定目标订单, {@code status} 为目标状态 (须符合订单状态机),
 * {@code note} 为可选备注 (取消 / 退款原因等)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmOrderStatusDto {

    /** 订单 ID */
    @NotNull(message = "订单 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 订单状态: PENDING / CONFIRMED / PAID / SHIPPED / DELIVERED / COMPLETED / CANCELLED / REFUNDED */
    @NotBlank(message = "订单状态不能为空")
    @Pattern(regexp = "PENDING|CONFIRMED|PAID|SHIPPED|DELIVERED|COMPLETED|CANCELLED|REFUNDED",
            message = "订单状态非法")
    private String status;

    /** 备注 (可空, 取消 / 退款原因等) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String note;
}
