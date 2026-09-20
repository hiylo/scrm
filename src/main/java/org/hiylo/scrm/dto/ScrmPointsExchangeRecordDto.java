/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsExchangeRecordDto.java
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
 * SCRM 积分兑换记录 DTO。
 * <p>
 * 对应 {@code ScrmPointsExchangeRecordEntity} 的业务字段, 主要用于查询返回。兑换记录由
 * {@code ScrmPointsService.exchange} 在客户兑换商品时自动产生。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmPointsExchangeRecordDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联兑换商品 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long exchangeId;

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 冗余客户名称 */
    private String customerName;

    /** 消耗积分 */
    private Integer pointsCost;

    /** 兑换数量 */
    private Integer quantity;

    /** 兑换码 (唯一) */
    private String exchangeCode;

    /** 状态: PENDING/SHIPPED/COMPLETED/CANCELLED */
    private String status;

    /** 收货地址 */
    private String shippingAddress;

    /** 物流单号 */
    private String shippingNo;

    /** 兑换时间 */
    private LocalDateTime exchangedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 取消时间 */
    private LocalDateTime cancelledAt;

    /** 备注 */
    private String notes;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
