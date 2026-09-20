/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsOperationDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 积分操作 DTO。
 * <p>
 * 手动调整 / 冻结 / 解冻 / 消耗积分等操作的统一入参。type 为操作类型:
 * EARN (增加) / REDEEM (扣减), points 为正数, reason 为操作原因。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmPointsOperationDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 积分数量 (正数) */
    @NotNull(message = "积分数量不能为空")
    @Positive(message = "积分数量必须为正数")
    private Integer points;

    /** 操作类型: EARN 增加 / REDEEM 扣减 */
    @NotBlank(message = "操作类型不能为空")
    @Pattern(regexp = "EARN|REDEEM", message = "操作类型仅支持 EARN/REDEEM")
    private String type;

    /** 操作原因 */
    @Size(max = 500, message = "操作原因长度不能超过 500")
    private String reason;

    /** 操作人 (可空, 缺省 scrm-system) */
    @Size(max = 100, message = "操作人长度不能超过 100")
    private String operator;
}
