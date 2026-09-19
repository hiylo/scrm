/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVisitTemplateDto.java
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
 * SCRM 客户回访模板 DTO。
 * <p>
 * 对应 {@code ScrmVisitTemplateEntity} 的业务字段, 用于模板创建/更新接口入参。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmVisitTemplateDto {

    /** 主键 ID (更新时必填) */
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

    /** 回访类型: REGULAR/FOLLOW_UP/SATISFACTION/RENEWAL/UPSELL/CROSS_SELL/CARE/COMPLAINT_FOLLOWUP/CUSTOM */
    @NotBlank(message = "回访类型不能为空")
    @Size(max = 30, message = "回访类型长度不能超过 30")
    private String visitType;

    /** 回访方式: PHONE/ON_SITE/VIDEO/WECHAT/EMAIL/SMS/MIXED */
    @NotBlank(message = "回访方式不能为空")
    @Pattern(regexp = "PHONE|ON_SITE|VIDEO|WECHAT|EMAIL|SMS|MIXED",
            message = "回访方式仅支持 PHONE/ON_SITE/VIDEO/WECHAT/EMAIL/SMS/MIXED")
    private String visitMethod;

    /** 问题列表 JSON: [{id,question,type,required,options,skipCondition}] */
    @NotBlank(message = "问题列表不能为空")
    private String questions;

    /** 开场白 (可空) */
    @Size(max = 1000, message = "开场白长度不能超过 1000")
    private String introduction;

    /** 结束语 (可空) */
    @Size(max = 1000, message = "结束语长度不能超过 1000")
    private String closing;

    /** 成功标准 (可空) */
    @Size(max = 500, message = "成功标准长度不能超过 500")
    private String successCriteria;

    /** 预计时长分钟 (默认 15) */
    private Integer estimatedDurationMinutes;

    /** 适用产品 (逗号分隔, 可空) */
    @Size(max = 500, message = "适用产品长度不能超过 500")
    private String applicableProducts;

    /** 标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
