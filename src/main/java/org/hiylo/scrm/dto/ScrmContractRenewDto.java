/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractRenewDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDate;

/**
 * SCRM 合同续约请求 DTO。
 * <p>
 * 续约时基于原合同创建新合同, {@code newEndDate} 指定新合同结束日期,
 * {@code autoRenew} 与 {@code newAmount} 可覆盖原合同配置。新合同自动关联原合同
 * (renewalOfId) 并生成到期/续约提醒。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmContractRenewDto {

    /** 原合同 ID */
    @NotNull(message = "合同 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 新合同结束日期 */
    @NotNull(message = "新合同结束日期不能为空")
    private LocalDate newEndDate;

    /** 是否自动续约 (可空, 为空则继承原合同配置) */
    private Boolean autoRenew;

    /** 新合同金额 (可空, 为空则继承原合同金额) */
    @PositiveOrZero(message = "新合同金额不能为负数")
    private Double newAmount;
}
