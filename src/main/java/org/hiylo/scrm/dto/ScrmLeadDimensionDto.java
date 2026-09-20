/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadDimensionDto.java
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
 * SCRM 销售线索评分维度 DTO。
 * <p>
 * 对应 {@code ScrmLeadDimensionEntity} 的业务字段, 创建/更新接口入参。
 * scoringRules 为 JSON 数组字符串: {@code [{field, operator, value, score, description}]}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmLeadDimensionDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 维度名称 */
    @NotBlank(message = "维度名称不能为空")
    @Size(max = 100, message = "维度名称长度不能超过 100")
    private String dimensionName;

    /** 维度编码 (唯一) */
    @NotBlank(message = "维度编码不能为空")
    @Size(max = 50, message = "维度编码长度不能超过 50")
    private String dimensionCode;

    /** 维度描述 (可空) */
    @Size(max = 500, message = "维度描述长度不能超过 500")
    private String description;

    /** 维度类别: DEMOGRAPHIC / BEHAVIORAL / ENGAGEMENT / FIRMOGRAPHIC / TECHNOGRAPHIC / NEED_BASED / TIMING */
    @NotBlank(message = "维度类别不能为空")
    @Pattern(regexp = "DEMOGRAPHIC|BEHAVIORAL|ENGAGEMENT|FIRMOGRAPHIC|TECHNOGRAPHIC|NEED_BASED|TIMING",
            message = "维度类别仅支持 DEMOGRAPHIC/BEHAVIORAL/ENGAGEMENT/FIRMOGRAPHIC/TECHNOGRAPHIC/NEED_BASED/TIMING")
    private String dimensionCategory;

    /** 默认权重 (默认 1.0) */
    @Positive(message = "默认权重必须为正数")
    private Double defaultWeight;

    /** 默认最高分 (默认 20) */
    @Positive(message = "默认最高分必须为正数")
    private Integer defaultMaxScore;

    /** 评分规则 JSON */
    @NotBlank(message = "评分规则不能为空")
    private String scoringRules;

    /** 可用字段 (逗号分隔, 可空) */
    @Size(max = 500, message = "可用字段长度不能超过 500")
    private String applicableFields;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 使用次数 (查询返回) */
    private Integer usageCount;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
