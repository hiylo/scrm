/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionCalculateDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SCRM 销售佣金计算 DTO。
 * <p>
 * 佣金计算接口入参: 指定方案与销售人员的单笔订单 (订单金额 / 利润 / 产品分类 / 客户类型),
 * 服务端将匹配方案与规则后产出佣金记录。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCommissionCalculateDto {

    /** 方案 ID */
    @NotNull(message = "方案 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long planId;

    /** 销售人员 ID */
    @NotBlank(message = "销售人员 ID 不能为空")
    private String salesPersonId;

    /** 销售人员名称 (可空) */
    private String salesPersonName;

    /** 团队 ID (可空) */
    private String teamId;

    /** 团队名称 (可空) */
    private String teamName;

    /** 关联订单 ID (可空) */
    private String orderId;

    /** 订单金额 */
    private Double orderAmount;

    /** 订单利润 (可空) */
    private Double orderProfit;

    /** 产品分类 (可空, 用于规则匹配) */
    private String productCategory;

    /** 客户类型 (可空, 用于规则匹配) */
    private String customerType;
}
