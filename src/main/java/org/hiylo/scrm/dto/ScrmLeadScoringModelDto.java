/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadScoringModelDto.java
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
 * SCRM 销售线索评分模型 DTO。
 * <p>
 * 对应 {@code ScrmLeadScoringModelEntity} 的业务字段, 创建/更新接口入参。
 * dimensions 为 JSON 数组字符串: {@code [{dimension, weight, maxScore, fields:[{field,
 * operator, value, score}]}]}; gradeThresholds 为 JSON 数组字符串:
 * {@code [{grade, minScore, maxScore, color}]}。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmLeadScoringModelDto {

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

    /** 模型类型: RULE_BASED / ML_BASED / HYBRID (默认 RULE_BASED) */
    @Pattern(regexp = "RULE_BASED|ML_BASED|HYBRID",
            message = "模型类型仅支持 RULE_BASED/ML_BASED/HYBRID")
    private String modelType;

    /** 评分维度配置 JSON */
    @NotBlank(message = "评分维度配置不能为空")
    private String dimensions;

    /** 总分上限 (默认 100) */
    @Positive(message = "总分上限必须为正数")
    private Integer totalMaxScore;

    /** 等级阈值 JSON (可空) */
    private String gradeThresholds;

    /** 是否为默认模型 (创建时可选, 默认 false) */
    private Boolean isDefault;

    /** 是否已发布 (创建时可选, 默认 false) */
    private Boolean isPublished;

    /** 模型版本号 (默认 1) */
    private Integer versionNo;

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
