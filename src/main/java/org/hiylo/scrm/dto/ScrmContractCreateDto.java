/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractCreateDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 合同创建请求 DTO。
 * <p>
 * 基于模板创建合同时, {@code templateId} 指定模板, {@code variables} 提供模板变量值,
 * {@code customFields} 提供自定义字段。创建后由服务层渲染模板内容并生成合同实例。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmContractCreateDto {

    /** 模板 ID (基于模板创建时必填, 渲染模板内容) */
    @NotNull(message = "模板 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** JSON 变量值 (键值对, 用于渲染模板内容, 可空) */
    private String variables;

    /** JSON 自定义字段 (可空) */
    private String customFields;

    /** 合同名称 (可空, 为空则由模板类型 + 客户名生成) */
    @Size(max = 500, message = "合同名称长度不能超过 500")
    private String contractName;

    /** 合同金额 (可空, 为空则取模板默认或 0) */
    private Double contractAmount;

    /** 合同开始日期 (可空, 为空则取今天) */
    private java.time.LocalDate startDate;

    /** 合同结束日期 (可空, 为空则按默认期限计算) */
    private java.time.LocalDate endDate;

    /** 销售人员 ID (可空) */
    @Size(max = 100, message = "销售人员 ID 长度不能超过 100")
    private String salesPersonId;

    /** 销售人员名称 (可空) */
    @Size(max = 100, message = "销售人员名称长度不能超过 100")
    private String salesPersonName;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
