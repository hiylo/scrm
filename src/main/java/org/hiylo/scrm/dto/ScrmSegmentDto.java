/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 客户分群定义 DTO。
 * <p>
 * 对应 {@code ScrmSegmentEntity} 的业务字段, 创建 / 更新分群接口入参。
 * segmentType 标注分群类型, conditionType 控制条件间逻辑关系, conditions 为 JSON 条件数组。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSegmentDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分群名称 */
    @NotBlank(message = "分群名称不能为空")
    @Size(max = 200, message = "分群名称长度不能超过 200")
    private String segmentName;

    /** 分群编码 (唯一) */
    @NotBlank(message = "分群编码不能为空")
    @Size(max = 50, message = "分群编码长度不能超过 50")
    private String segmentCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 分群类型: DYNAMIC / STATIC / HYBRID (默认 DYNAMIC) */
    @Pattern(regexp = "DYNAMIC|STATIC|HYBRID", message = "分群类型仅支持 DYNAMIC/STATIC/HYBRID")
    private String segmentType;

    /** 分群分类: RFM / LIFECYCLE / VALUE / BEHAVIOR / CUSTOM (可空) */
    @Size(max = 100, message = "分类长度不能超过 100")
    private String category;

    /** 条件组合: ALL / ANY / NONE (默认 ALL) */
    @Pattern(regexp = "ALL|ANY|NONE", message = "条件组合仅支持 ALL/ANY/NONE")
    private String conditionType;

    /** 条件 JSON 数组: [{field, operator, value, logic}] */
    @NotBlank(message = "条件 JSON 不能为空")
    private String conditions;

    /** 状态: ACTIVE / INACTIVE / DRAFT (默认 ACTIVE) */
    @Pattern(regexp = "ACTIVE|INACTIVE|DRAFT", message = "状态仅支持 ACTIVE/INACTIVE/DRAFT")
    private String status;

    /** 计算频率: REALTIME / HOURLY / DAILY / WEEKLY / MANUAL (默认 DAILY) */
    @Pattern(regexp = "REALTIME|HOURLY|DAILY|WEEKLY|MANUAL",
            message = "计算频率仅支持 REALTIME/HOURLY/DAILY/WEEKLY/MANUAL")
    private String calculationFrequency;

    /** 是否自动更新成员 (默认 true) */
    private Boolean autoUpdate;

    /** 颜色 (可空) */
    @Size(max = 20, message = "颜色长度不能超过 20")
    private String color;

    /** 图标 (可空) */
    @Size(max = 100, message = "图标长度不能超过 100")
    private String icon;

    /** 标签 (可空, 逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 当前成员数 (查询返回) */
    private Integer memberCount;

    /** 最后计算时间 (查询返回) */
    private LocalDateTime lastCalculatedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
