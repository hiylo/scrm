/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLifecycleStageDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 客户生命周期阶段 DTO。
 * <p>
 * 对应 {@code ScrmLifecycleStageEntity} 的业务字段, 创建/更新接口入参。
 * stageCategory 以枚举字符串校验合法性; isStartStage / isEndStage / isChurnStage
 * / enabled 缺省时由服务端填充默认值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmLifecycleStageDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 阶段名称 */
    @NotBlank(message = "阶段名称不能为空")
    @Size(max = 100, message = "阶段名称长度不能超过 100")
    private String stageName;

    /** 阶段编码 (全局唯一) */
    @NotBlank(message = "阶段编码不能为空")
    @Size(max = 50, message = "阶段编码长度不能超过 50")
    private String stageCode;

    /** 阶段描述 (可空) */
    @Size(max = 500, message = "阶段描述长度不能超过 500")
    private String description;

    /** 阶段顺序 (值越小越靠前) */
    private Integer stageOrder;

    /** 阶段类别: ACQUISITION/ENGAGEMENT/ACTIVATION/RETENTION/ADVOCACY/CHURN/REACTIVATION */
    @NotBlank(message = "阶段类别不能为空")
    @Pattern(regexp = "ACQUISITION|ENGAGEMENT|ACTIVATION|RETENTION|ADVOCACY|CHURN|REACTIVATION",
            message = "阶段类别仅支持 ACQUISITION/ENGAGEMENT/ACTIVATION/RETENTION/ADVOCACY/CHURN/REACTIVATION")
    private String stageCategory;

    /** 阶段颜色标识 (可空) */
    @Size(max = 20, message = "颜色标识长度不能超过 20")
    private String color;

    /** 阶段图标 (可空) */
    @Size(max = 100, message = "图标长度不能超过 100")
    private String icon;

    /** 进入条件 JSON (可空) */
    private String entryCriteria;

    /** 退出条件 JSON (可空) */
    private String exitCriteria;

    /** 目标停留天数 (可空, 用于超期判定) */
    private Integer targetDurationDays;

    /** 是否起始阶段 (默认 FALSE) */
    private Boolean isStartStage;

    /** 是否终止阶段 (默认 FALSE) */
    private Boolean isEndStage;

    /** 是否流失阶段 (默认 FALSE) */
    private Boolean isChurnStage;

    /** 是否启用 (默认 TRUE) */
    private Boolean enabled;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 当前客户数 (查询返回) */
    private Integer customerCount;

    /** 累计进入数 (查询返回) */
    private Integer totalEnteredCount;

    /** 平均停留天数 (查询返回) */
    private Double avgDurationDays;

    /** 转化率到下一阶段 (查询返回) */
    private Double conversionRate;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
