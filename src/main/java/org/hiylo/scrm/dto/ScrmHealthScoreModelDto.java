/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmHealthScoreModelDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 客户健康度评分模型 DTO。
 * <p>
 * 对应 {@code ScrmHealthScoreModelEntity} 的业务字段, 创建/更新接口入参。
 * metrics 为 JSON 数组字符串: {@code [{metricCode, metricName, weight, maxScore,
 * scoringType, scoringRules}]}; healthThresholds 为 JSON 数组字符串:
 * {@code [{level, minScore, maxScore, color, action}]}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmHealthScoreModelDto {

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

    /** 适用客群 (可空) */
    @Size(max = 500, message = "适用客群长度不能超过 500")
    private String applicableSegment;

    /** 指标配置 JSON */
    @NotBlank(message = "指标配置不能为空")
    private String metrics;

    /** 评分类型: SIMPLE / WEIGHTED / DYNAMIC (默认 WEIGHTED) */
    @Pattern(regexp = "SIMPLE|WEIGHTED|DYNAMIC",
            message = "评分类型仅支持 SIMPLE/WEIGHTED/DYNAMIC")
    private String scoringType;

    /** 总分上限 (默认 100) */
    @Positive(message = "总分上限必须为正数")
    private Integer totalMaxScore;

    /** 健康阈值 JSON (可空) */
    private String healthThresholds;

    /** 是否为默认模型 (创建时可选, 默认 false) */
    private Boolean isDefault;

    /** 是否已发布 (创建时可选, 默认 false) */
    private Boolean isPublished;

    /** 模型版本号 (默认 1) */
    private Integer versionNo;

    /** 更新频率: REALTIME / DAILY / WEEKLY / MONTHLY (默认 DAILY) */
    @Pattern(regexp = "REALTIME|DAILY|WEEKLY|MONTHLY",
            message = "更新频率仅支持 REALTIME/DAILY/WEEKLY/MONTHLY")
    private String updateFrequency;

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
