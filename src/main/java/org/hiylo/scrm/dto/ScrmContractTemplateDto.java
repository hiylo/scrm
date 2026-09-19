/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractTemplateDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 合同模板 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmContractTemplateDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200")
    private String templateName;

    /** 模板编码 (唯一) */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 50, message = "模板编码长度不能超过 50")
    private String templateCode;

    /** 模板描述 (可空) */
    @Size(max = 500, message = "模板描述长度不能超过 500")
    private String description;

    /** 合同类型: SALES / SERVICE / PARTNERSHIP / NDA / RESELLER / AGENCY / MAINTENANCE / RENTAL / PURCHASE / CUSTOM */
    @NotBlank(message = "合同类型不能为空")
    @Size(max = 30, message = "合同类型长度不能超过 30")
    @Pattern(regexp = "SALES|SERVICE|PARTNERSHIP|NDA|RESELLER|AGENCY|MAINTENANCE|RENTAL|PURCHASE|CUSTOM",
            message = "合同类型仅支持 SALES/SERVICE/PARTNERSHIP/NDA/RESELLER/AGENCY/MAINTENANCE/RENTAL/PURCHASE/CUSTOM")
    private String contractType;

    /** 合同模板内容 (支持变量占位符渲染, 如 {{customerName}}) */
    @NotBlank(message = "模板内容不能为空")
    private String templateContent;

    /** JSON 变量定义: [{name, type, defaultValue, required}] (可空) */
    private String variables;

    /** 适用商品 ID 逗号分隔 (可空) */
    @Size(max = 500, message = "适用商品长度不能超过 500")
    private String applicableProducts;

    /** JSON 条款配置: [{clauseName, isRequired, editable}] (可空) */
    private String clauses;

    /** 模板版本号 (业务版本, 每次更新递增) */
    @PositiveOrZero(message = "模板版本号不能为负数")
    private Integer templateVersion;

    /** 状态: ACTIVE / INACTIVE / DRAFT */
    @Pattern(regexp = "ACTIVE|INACTIVE|DRAFT", message = "状态仅支持 ACTIVE/INACTIVE/DRAFT")
    private String status;

    /** 使用次数 (基于此模板创建的合同数) */
    private Integer usageCount;

    /** 最后使用时间 (可空) */
    private LocalDateTime lastUsedAt;

    /** 审核人 (可空) */
    @Size(max = 100, message = "审核人长度不能超过 100")
    private String reviewedBy;

    /** 审核时间 (可空) */
    private LocalDateTime approvedAt;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
