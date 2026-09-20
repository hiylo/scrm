/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCouponUsageLogDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 优惠券使用日志 DTO。
 * <p>
 * 仅用于查询返回, 日志由发券/领取/使用/退还/过期流程自动写入, 不接收前端直接入参。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCouponUsageLogDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联优惠券 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long couponId;

    /** 关联模板 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 客户 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    private String customerName;

    /** 操作类型: ISSUE / CLAIM / USE / RETURN / EXPIRE */
    private String actionType;

    /** 操作时间 */
    private LocalDateTime actionTime;

    /** 操作人 ID (可空) */
    private String operatorId;

    /** 操作人名称 (可空) */
    private String operatorName;

    /** 订单金额 (USE 时携带, 可空) */
    private Double orderAmount;

    /** 抵扣金额 (USE 时携带, 可空) */
    private Double discountAmount;

    /** 详情 (可空) */
    private String detail;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 乐观锁版本号 */
    private Long version;
}
