/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAbTestAssignmentDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM A/B 测试客户分配 DTO。
 * <p>
 * 对应 {@code ScrmAbTestAssignmentEntity} 的业务字段, 用于记录客户在某测试中的分配与转化。
 * 通常由服务端按流量比例自动分配, 也可手动录入。{@link #conversionValue} 记录转化价值,
 * {@link #engagementData} 承载互动数据 JSON。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAbTestAssignmentDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 测试 ID */
    @NotNull(message = "测试 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long testId;

    /** 变体 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long variantId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 分配时间 (缺省取当前时间) */
    private LocalDateTime assignedAt;

    /** 分配方法: RANDOM / STRATIFIED / BLOCKED (默认 RANDOM) */
    @Pattern(regexp = "RANDOM|STRATIFIED|BLOCKED", message = "分配方法仅支持 RANDOM/STRATIFIED/BLOCKED")
    private String assignmentMethod;

    /** 是否转化 (默认 FALSE) */
    private Boolean converted;

    /** 转化时间 (可空) */
    private LocalDateTime convertedAt;

    /** 转化价值 (默认 0) */
    private Double conversionValue;

    /** 互动数据 JSON (可空) */
    private String engagementData;

    /** 会话 ID (可空) */
    @Size(max = 200, message = "会话 ID 长度不能超过 200")
    private String sessionId;

    /** 附加数据 JSON (可空) */
    private String metadata;
}
