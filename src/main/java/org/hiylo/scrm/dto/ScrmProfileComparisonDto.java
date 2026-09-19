/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProfileComparisonDto.java
 * Date : 2026/08/05 08:55:12
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

import java.time.LocalDateTime;

/**
 * SCRM 画像对比 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmProfileComparisonDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID 1 */
    @NotNull(message = "客户 ID 1 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId1;

    /** 客户名称 1 (可空) */
    @Size(max = 200, message = "客户名称 1 长度不能超过 200")
    private String customerName1;

    /** 客户 ID 2 */
    @NotNull(message = "客户 ID 2 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId2;

    /** 客户名称 2 (可空) */
    @Size(max = 200, message = "客户名称 2 长度不能超过 200")
    private String customerName2;

    /** 对比类型: INDIVIDUAL / SEGMENT / TEMPLATE */
    @NotBlank(message = "对比类型不能为空")
    @Size(max = 30, message = "对比类型长度不能超过 30")
    @Pattern(regexp = "INDIVIDUAL|SEGMENT|TEMPLATE",
            message = "对比类型仅支持 INDIVIDUAL/SEGMENT/TEMPLATE")
    private String comparisonType;

    /** 对比维度结果 JSON */
    private String dimensions;

    /** 总体相似度 (0-1) */
    private Double overallSimilarity;

    /** 共同特征 */
    @Size(max = 1000, message = "共同特征长度不能超过 1000")
    private String commonTraits;

    /** 关键差异 */
    @Size(max = 1000, message = "关键差异长度不能超过 1000")
    private String keyDifferences;

    /** 对比建议 */
    @Size(max = 500, message = "对比建议长度不能超过 500")
    private String recommendation;

    /** 对比时间 */
    private LocalDateTime comparedAt;

    /** 对比人 */
    @Size(max = 100, message = "对比人长度不能超过 100")
    private String comparedBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
