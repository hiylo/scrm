/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerDuplicateDto.java
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

import java.time.LocalDateTime;

/**
 * SCRM 客户重复记录 DTO。
 * <p>
 * 对应 {@code ScrmCustomerDuplicateEntity} 的业务字段, 用于重复记录查询与展示。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCustomerDuplicateDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 主客户 ID */
    @NotNull(message = "主客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long primaryCustomerId;

    /** 重复客户 ID */
    @NotNull(message = "重复客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long duplicateCustomerId;

    /** 匹配类型: EXACT 精确 / FUZZY 模糊 */
    @NotBlank(message = "匹配类型不能为空")
    @Size(max = 20, message = "匹配类型长度不能超过 20")
    @Pattern(regexp = "EXACT|FUZZY", message = "匹配类型仅支持 EXACT/FUZZY")
    private String matchType;

    /** 匹配置信度 (0~1) */
    private Double matchScore;

    /** 匹配字段 (如 nickname / platform_customer_uid) */
    @Size(max = 100, message = "匹配字段长度不能超过 100")
    private String matchField;

    /** 状态: PENDING/CONFIRMED/IGNORED/MERGED */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "PENDING|CONFIRMED|IGNORED|MERGED|",
            message = "状态仅支持 PENDING/CONFIRMED/IGNORED/MERGED")
    private String status;

    /** 检测时间 */
    private LocalDateTime detectedAt;

    /** 处理时间 */
    private LocalDateTime resolvedAt;

    /** 处理人 */
    @Size(max = 100, message = "处理人长度不能超过 100")
    private String resolvedBy;
}
