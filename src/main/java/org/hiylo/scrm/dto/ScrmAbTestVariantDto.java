/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestVariantDto.java
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
 * SCRM A/B 测试变体 DTO。
 * <p>
 * 对应 {@code ScrmAbTestVariantEntity} 的业务字段, 创建/更新变体接口入参。
 * variantType 取值 CONTROL (对照组) / VARIANT (实验组), trafficPercent 控制流量分配。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAbTestVariantDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 测试 ID (创建时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long testId;

    /** 变体名称 */
    @NotBlank(message = "变体名称不能为空")
    @Size(max = 100, message = "变体名称长度不能超过 100")
    private String variantName;

    /** 变体编码 */
    @NotBlank(message = "变体编码不能为空")
    @Size(max = 50, message = "变体编码长度不能超过 50")
    private String variantCode;

    /** 变体类型: CONTROL / VARIANT (默认 VARIANT) */
    @Pattern(regexp = "CONTROL|VARIANT", message = "变体类型仅支持 CONTROL/VARIANT")
    private String variantType;

    /** 变体描述 (可空) */
    @Size(max = 500, message = "变体描述长度不能超过 500")
    private String description;

    /** 变体内容配置 JSON: {title, body, image, cta, templateId, ...} (可空) */
    private String contentConfig;

    /** 流量分配百分比 (默认 50) */
    private Integer trafficPercent;

    /** 是否对照组 (默认 FALSE) */
    private Boolean isControl;

    /** 参与人数 (查询返回) */
    private Integer participants;

    /** 转化数 (查询返回) */
    private Integer conversions;

    /** 转化率 (查询返回) */
    private Double conversionRate;

    /** 收入 (查询返回) */
    private Double revenue;

    /** 平均订单价值 (查询返回) */
    private Double avgOrderValue;

    /** 互动评分 (查询返回) */
    private Double engagementScore;

    /** 是否胜出 (查询返回) */
    private Boolean isWinner;

    /** 变体颜色标识 (可空) */
    @Size(max = 20, message = "变体颜色标识长度不能超过 20")
    private String color;

    /** 排序序号 (默认 0) */
    private Integer sortOrder;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
