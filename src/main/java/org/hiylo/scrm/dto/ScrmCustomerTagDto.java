/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerTagDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户标签 DTO。
 * <p>
 * 对应 {@code ScrmCustomerTagEntity} 的业务字段, 创建/更新接口入参。
 * tagType: MANUAL/RULE/DERIVED/SYSTEM/AI; valueType: BOOLEAN/ENUM/NUMERIC/STRING/DATE;
 * category: DEMOGRAPHIC/BEHAVIORAL/PSYCHOGRAPHIC/TRANSACTIONAL/SOCIAL/PREFERENCE/LIFECYCLE/RISK/VALUE。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCustomerTagDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 标签名称 */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 200, message = "标签名称长度不能超过 200")
    private String tagName;

    /** 标签编码 (唯一) */
    @NotBlank(message = "标签编码不能为空")
    @Size(max = 50, message = "标签编码长度不能超过 50")
    private String tagCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 标签分组 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long groupId;

    /** 标签分组名称 (可空) */
    @Size(max = 200, message = "标签分组名称长度不能超过 200")
    private String groupName;

    /** 标签类型: MANUAL/RULE/DERIVED/SYSTEM/AI */
    @NotBlank(message = "标签类型不能为空")
    @Pattern(regexp = "MANUAL|RULE|DERIVED|SYSTEM|AI",
            message = "标签类型仅支持 MANUAL/RULE/DERIVED/SYSTEM/AI")
    private String tagType;

    /** 值类型: BOOLEAN/ENUM/NUMERIC/STRING/DATE (默认 BOOLEAN) */
    @Pattern(regexp = "BOOLEAN|ENUM|NUMERIC|STRING|DATE",
            message = "值类型仅支持 BOOLEAN/ENUM/NUMERIC/STRING/DATE")
    private String valueType;

    /** 枚举选项 JSON (可空) */
    @Size(max = 1000, message = "枚举选项长度不能超过 1000")
    private String enumOptions;

    /** 默认值 (可空) */
    @Size(max = 200, message = "默认值长度不能超过 200")
    private String defaultValue;

    /** 标签分类 (可空) */
    @Size(max = 100, message = "标签分类长度不能超过 100")
    private String category;

    /** 子分类 (可空) */
    @Size(max = 100, message = "子分类长度不能超过 100")
    private String subCategory;

    /** 是否系统标签 (默认 FALSE) */
    private Boolean isSystem;

    /** 是否必填 (默认 FALSE) */
    private Boolean isRequired;

    /** 是否可见 (默认 TRUE) */
    private Boolean isVisible;

    /** 是否可搜索 (默认 TRUE) */
    private Boolean isSearchable;

    /** 是否多值标签 (默认 FALSE) */
    private Boolean isMultiple;

    /** 颜色 (可空) */
    @Size(max = 20, message = "颜色长度不能超过 20")
    private String color;

    /** 图标 (可空) */
    @Size(max = 200, message = "图标长度不能超过 200")
    private String icon;

    /** 展示顺序 (默认 0) */
    private Integer displayOrder;

    /** 帮助文本 (可空) */
    @Size(max = 500, message = "帮助文本长度不能超过 500")
    private String helpText;

    /** 适用客群 (可空) */
    @Size(max = 500, message = "适用客群长度不能超过 500")
    private String applicableSegments;

    /** 规则表达式 (可空) */
    @Size(max = 2000, message = "规则表达式长度不能超过 2000")
    private String ruleExpression;

    /** 规则条件 JSON (可空): [{field,operator,value,groupBy}] */
    private String ruleConditions;

    /** 规则逻辑: AND/OR (默认 AND) */
    @Pattern(regexp = "AND|OR", message = "规则逻辑仅支持 AND/OR")
    private String ruleLogic;

    /** 是否自动应用 (默认 FALSE) */
    private Boolean autoApply;

    /** 评估频率: REALTIME/HOURLY/DAILY/WEEKLY/MONTHLY (默认 DAILY) */
    @Pattern(regexp = "REALTIME|HOURLY|DAILY|WEEKLY|MONTHLY",
            message = "评估频率仅支持 REALTIME/HOURLY/DAILY/WEEKLY/MONTHLY")
    private String evaluationFrequency;

    /** 最近评估时间 (查询返回) */
    private LocalDateTime lastEvaluatedAt;

    /** 客户数 (查询返回) */
    private Integer customerCount;

    /** 覆盖率% (查询返回) */
    private Double coverageRate;

    /** 正向数 (查询返回) */
    private Integer positiveCount;

    /** 负向数 (查询返回) */
    private Integer negativeCount;

    /** 中性数 (查询返回) */
    private Integer neutralCount;

    /** BOOLEAN 类型 true 数 (查询返回) */
    private Integer trueCount;

    /** BOOLEAN 类型 false 数 (查询返回) */
    private Integer falseCount;

    /** NUMERIC 平均值 (查询返回) */
    private Double avgNumericValue;

    /** TOP 值 JSON (查询返回) */
    @Size(max = 1000, message = "TOP 值长度不能超过 1000")
    private String topValues;

    /** 趋势: RISING/STABLE/FALLING (查询返回) */
    @Pattern(regexp = "RISING|STABLE|FALLING", message = "趋势仅支持 RISING/STABLE/FALLING")
    private String trend;

    /** 趋势百分比 (查询返回) */
    private Double trendPercent;

    /** 优先级 (默认 0) */
    private Integer priority;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

    /** 标签 (可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
