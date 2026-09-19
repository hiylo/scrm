/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmInvoiceTemplateDto.java
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
 * SCRM 发票模板 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmInvoiceTemplateDto {

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

    /** 发票类型: GENERAL / SPECIAL / ELECTRONIC / PLAIN_DIGITAL / RED_REDUCED */
    @NotBlank(message = "发票类型不能为空")
    @Size(max = 30, message = "发票类型长度不能超过 30")
    @Pattern(regexp = "GENERAL|SPECIAL|ELECTRONIC|PLAIN_DIGITAL|RED_REDUCED",
            message = "发票类型仅支持 GENERAL/SPECIAL/ELECTRONIC/PLAIN_DIGITAL/RED_REDUCED")
    private String invoiceType;

    /** 默认税率 (0-1, 如 0.13) */
    @PositiveOrZero(message = "默认税率不能为负数")
    private Double defaultTaxRate;

    /** JSON 默认明细项: [{name,spec,unit,quantity,price,amount,taxRate,taxAmount}] (可空) */
    private String defaultItems;

    /** 适用商品 ID 逗号分隔 (可空) */
    @Size(max = 500, message = "适用商品长度不能超过 500")
    private String applicableProducts;

    /** 默认备注 (可空) */
    @Size(max = 500, message = "默认备注长度不能超过 500")
    private String remarks;

    /** 必填字段逗号分隔 (可空) */
    @Size(max = 500, message = "必填字段长度不能超过 500")
    private String requiredFields;

    /** 是否启用 */
    private Boolean enabled;

    /** 使用次数 (基于此模板创建的发票数) */
    private Integer usageCount;

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
