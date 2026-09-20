/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProfileTemplateDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 画像模板 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmProfileTemplateDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200")
    private String templateName;

    /** 模板编码 (全局唯一) */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 50, message = "模板编码长度不能超过 50")
    private String templateCode;

    /** 模板描述 */
    @Size(max = 500, message = "模板描述长度不能超过 500")
    private String description;

    /** 适用客群 */
    @Size(max = 500, message = "适用客群长度不能超过 500")
    private String applicableSegment;

    /** 维度配置 JSON */
    @NotBlank(message = "维度配置不能为空")
    private String dimensions;

    /** 评分模型 JSON */
    private String scoringModel;

    /** 标签规则 JSON */
    private String tagRules;

    /** 摘要模板 */
    @Size(max = 1000, message = "摘要模板长度不能超过 1000")
    private String summaryTemplate;

    /** 最低置信度 (默认 0.5) */
    private Double minConfidence;

    /** 更新频率: REALTIME / DAILY / WEEKLY / MONTHLY */
    @NotNull(message = "更新频率不能为空")
    @Size(max = 20, message = "更新频率长度不能超过 20")
    @Pattern(regexp = "REALTIME|DAILY|WEEKLY|MONTHLY",
            message = "更新频率仅支持 REALTIME/DAILY/WEEKLY/MONTHLY")
    private String updateFrequency;

    /** 是否默认模板 */
    private Boolean isDefault;

    /** 是否启用 */
    private Boolean enabled;

    /** 使用次数 */
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
