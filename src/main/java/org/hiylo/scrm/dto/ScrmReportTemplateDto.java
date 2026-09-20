/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReportTemplateDto.java
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
 * SCRM 自定义报表模板 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmReportTemplateDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200")
    private String templateName;

    /** 报表类型：CUSTOMER / OPPORTUNITY / CAMPAIGN / CONVERSATION / MASS_SEND / ARCHIVE / GENERAL */
    @NotBlank(message = "报表类型不能为空")
    @Size(max = 30, message = "报表类型长度不能超过 30")
    @Pattern(regexp = "CUSTOMER|OPPORTUNITY|CAMPAIGN|CONVERSATION|MASS_SEND|ARCHIVE|GENERAL",
            message = "报表类型仅支持 CUSTOMER/OPPORTUNITY/CAMPAIGN/CONVERSATION/MASS_SEND/ARCHIVE/GENERAL")
    private String reportType;

    /** 数据源表名 */
    @NotBlank(message = "数据源不能为空")
    @Size(max = 50, message = "数据源长度不能超过 50")
    private String dataSource;

    /** 维度字段 JSON 数组 */
    @NotBlank(message = "维度字段不能为空")
    private String dimensions;

    /** 指标字段 JSON 数组 */
    @NotBlank(message = "指标字段不能为空")
    private String metrics;

    /** 筛选条件 JSON */
    private String filters;

    /** 时间范围字段 */
    @Size(max = 100, message = "时间范围字段长度不能超过 100")
    private String timeRangeField;

    /** 图表类型：TABLE / BAR / LINE / PIE */
    @Size(max = 30, message = "图表类型长度不能超过 30")
    @Pattern(regexp = "TABLE|BAR|LINE|PIE", message = "图表类型仅支持 TABLE/BAR/LINE/PIE")
    private String chartType;

    /** 模板描述 */
    @Size(max = 500, message = "模板描述长度不能超过 500")
    private String description;

    /** 是否公开 */
    private Boolean isPublic;

    /** 创建人用户 ID */
    private String createdBy;

    /** 最近执行时间 */
    private LocalDateTime lastRunAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
