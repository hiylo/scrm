/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionConversionDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 营销效果归因转化 DTO。
 * <p>
 * 对应 {@code ScrmAttributionConversionEntity} 的业务字段, 记录转化接口入参与查询返回。
 * attributionDetails 为 JSON 数组字符串: {@code [{touchpointId, type, channel, weight, value}]};
 * metadata 为 JSON 附加数据字符串。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAttributionConversionDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 转化类型: PURCHASE/SIGNUP/FORM_SUBMIT/APPOINTMENT/DOWNLOAD/ADD_TO_CART/CHECKOUT/UPGRADE/RENEWAL/CUSTOM */
    @NotBlank(message = "转化类型不能为空")
    @Size(max = 50, message = "转化类型长度不能超过 50")
    private String conversionType;

    /** 转化时间 */
    @NotNull(message = "转化时间不能为空")
    private LocalDateTime conversionTime;

    /** 转化价值 (默认 0) */
    @PositiveOrZero(message = "转化价值不能为负数")
    private Double conversionValue;

    /** 转化次数 (可空, 缺省 1) */
    private Integer conversionCount;

    /** 关联订单号 (可空) */
    @Size(max = 100, message = "订单号长度不能超过 100")
    private String orderId;

    /** JSON 附加数据 (可空) */
    private String metadata;

    /** 归因模型 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 归因模型名称 (查询返回) */
    private String modelName;

    /** JSON 归因详情 (查询返回): [{touchpointId, type, channel, weight, value}] */
    private String attributionDetails;

    /** 总触点数 (查询返回) */
    private Integer totalTouchpoints;

    /** 归因触点数 (查询返回) */
    private Integer attributedTouchpoints;

    /** 首次触点类型 (查询返回) */
    private String firstTouchType;

    /** 首次触点渠道 (查询返回) */
    private String firstTouchChannel;

    /** 末次触点类型 (查询返回) */
    private String lastTouchType;

    /** 末次触点渠道 (查询返回) */
    private String lastTouchChannel;

    /** 转化窗口天数 (查询返回) */
    private Integer conversionWindowDays;

    /** 转化耗时小时 (查询返回) */
    private Integer timeToConversionHours;

    /** 归因时间 (查询返回) */
    private LocalDateTime attributedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
