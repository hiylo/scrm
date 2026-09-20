/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponIssueDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SCRM 优惠券批量发放请求 DTO。
 * <p>
 * {@code templateId} 指定发券模板, {@code customerIds} 为目标客户列表 (可空, 为空时发放未归属券),
 * {@code issueCount} 为发放数量 (customerIds 非空时为每客户发放数, 为空时为总发放数)。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCouponIssueDto {

    /** 模板 ID */
    @NotNull(message = "模板 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 目标客户 ID 列表 (可空, 为空时发放未归属券) */
    private List<@NotNull(message = "客户 ID 不能为空") Long> customerIds;

    /** 发放数量 (customerIds 非空时为每客户发放数, 为空时为总发放数) */
    @NotNull(message = "发放数量不能为空")
    @Min(value = 1, message = "发放数量必须大于 0")
    private Integer issueCount;

    /** 发放人 (可空) */
    @Size(max = 100, message = "发放人长度不能超过 100")
    private String issuedBy;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;
}
