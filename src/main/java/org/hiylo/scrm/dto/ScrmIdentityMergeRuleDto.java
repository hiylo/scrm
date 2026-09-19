/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmIdentityMergeRuleDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户身份合并规则 DTO。
 * <p>
 * 对应 {@code ScrmIdentityMergeRuleEntity} 的业务字段, 用于创建 / 更新 / 查询规则。
 * matchFields 必填, matchThreshold 必填且不小于 0。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmIdentityMergeRuleDto {

    /** 主键 ID (查询返回) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 匹配字段 (逗号分隔): phone,email,name,wechat_unionid */
    @NotBlank(message = "匹配字段不能为空")
    @Size(max = 500, message = "匹配字段长度不能超过 500")
    private String matchFields;

    /** 匹配阈值 (0~1) */
    @NotNull(message = "匹配阈值不能为空")
    @DecimalMin(value = "0.0", message = "匹配阈值不能小于 0")
    private Double matchThreshold;

    /** 模糊匹配字段 (可空): name,address */
    @Size(max = 500, message = "模糊匹配字段长度不能超过 500")
    private String fuzzyMatchFields;

    /** 模糊匹配阈值 (0~1, 可空) */
    @DecimalMin(value = "0.0", message = "模糊匹配阈值不能小于 0")
    private Double fuzzyMatchThreshold;

    /** 自动合并 (可空, 默认 FALSE) */
    private Boolean autoMerge;

    /** JSON 字段策略 (可空): [{field,strategy:KEEP_TARGET/KEEP_SOURCE/MERGE/CONCAT/MAX/MIN/LATEST}] */
    private String fieldStrategy;

    /** 排除字段 (可空, 逗号分隔) */
    @Size(max = 500, message = "排除字段长度不能超过 500")
    private String excludeFields;

    /** 优先级 (可空, 默认 0) */
    private Integer priority;

    /** 是否启用 (可空, 默认 TRUE) */
    private Boolean enabled;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 匹配次数 (查询返回) */
    private Integer matchCount;

    /** 合并次数 (查询返回) */
    private Integer mergeCount;

    /** 最后执行时间 (查询返回) */
    private LocalDateTime lastExecutedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;
}
