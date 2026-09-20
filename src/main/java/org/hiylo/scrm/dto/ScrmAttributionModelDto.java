/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionModelDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 营销效果归因模型 DTO。
 * <p>
 * 对应 {@code ScrmAttributionModelEntity} 的业务字段, 创建/更新接口入参。
 * positionWeights 为 JSON 对象字符串: {@code {first, last, middle}} (用于位置归因 / U 型 / W 型);
 * customWeights 为 JSON 自定义权重规则字符串 (CUSTOM 模型使用)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAttributionModelDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 模型名称 */
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 200, message = "模型名称长度不能超过 200")
    private String modelName;

    /** 模型编码 (唯一) */
    @NotBlank(message = "模型编码不能为空")
    @Size(max = 50, message = "模型编码长度不能超过 50")
    private String modelCode;

    /** 模型描述 (可空) */
    @Size(max = 500, message = "模型描述长度不能超过 500")
    private String description;

    /** 模型类型: FIRST_TOUCH/LAST_TOUCH/LINEAR/TIME_DECAY/POSITION_BASED/U_SHAPED/W_SHAPED/CUSTOM */
    @Pattern(regexp = "FIRST_TOUCH|LAST_TOUCH|LINEAR|TIME_DECAY|POSITION_BASED|U_SHAPED|W_SHAPED|CUSTOM",
            message = "模型类型仅支持 FIRST_TOUCH/LAST_TOUCH/LINEAR/TIME_DECAY/POSITION_BASED/U_SHAPED/W_SHAPED/CUSTOM")
    private String modelType;

    /** 回溯天数 (默认 30, 仅回溯该天数内的触点参与归因) */
    @Positive(message = "回溯天数必须为正数")
    private Integer lookbackDays;

    /** 位置权重 JSON (可空): {first, last, middle} */
    private String positionWeights;

    /** 时间衰减半衰期天数 (默认 7) */
    @Positive(message = "时间衰减半衰期必须为正数")
    private Integer timeDecayHalfLife;

    /** 自定义权重规则 JSON (可空) */
    private String customWeights;

    /** 转化窗口天数 (默认 7, 触点至转化的有效窗口) */
    @Positive(message = "转化窗口天数必须为正数")
    private Integer conversionWindowDays;

    /** 是否为默认模型 (创建时可选, 默认 false) */
    private Boolean isDefault;

    /** 是否已发布 (创建时可选, 默认 false) */
    private Boolean isPublished;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 应用次数 (查询返回) */
    private Integer appliedCount;

    /** 最近应用时间 (查询返回) */
    private LocalDateTime lastAppliedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
