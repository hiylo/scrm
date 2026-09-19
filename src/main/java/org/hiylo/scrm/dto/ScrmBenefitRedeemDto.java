/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBenefitRedeemDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 权益兑换 DTO。
 * <p>
 * 用于会员兑换权益: 传入会员记录 ID 与权益 ID, 可空传入关联订单 ID 与消费金额。
 * 服务端校验会员状态有效、权益有效且在使用限制内后, 记录兑换、更新权益与会员统计并返回优惠结果。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmBenefitRedeemDto {

    /** 会员记录 ID */
    @NotNull(message = "会员记录 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long membershipId;

    /** 权益 ID */
    @NotNull(message = "权益 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long benefitId;

    /** 关联订单 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 消费金额 (可空, 用于计算优惠金额) */
    private Double amount;

    /** 兑换备注 (可空) */
    @Size(max = 500, message = "兑换备注长度不能超过 500")
    private String notes;
}
