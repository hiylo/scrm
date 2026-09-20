/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmJourneyStepDto.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 旅程步骤 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmJourneyStepDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属旅程 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long journeyId;

    /** 步骤名称 */
    @NotBlank(message = "步骤名称不能为空")
    @Size(max = 200, message = "步骤名称长度不能超过 200")
    private String stepName;

    /** 步骤类型: SEND_MESSAGE / WAIT / CONDITION / ADD_TAG / SET_LIFECYCLE / WEBHOOK / END */
    @NotBlank(message = "步骤类型不能为空")
    @Size(max = 30, message = "步骤类型长度不能超过 30")
    @Pattern(regexp = "SEND_MESSAGE|WAIT|CONDITION|ADD_TAG|SET_LIFECYCLE|WEBHOOK|END",
            message = "步骤类型仅支持 SEND_MESSAGE/WAIT/CONDITION/ADD_TAG/SET_LIFECYCLE/WEBHOOK/END")
    private String stepType;

    /** 步骤顺序 (数字越小越靠前) */
    @NotNull(message = "步骤顺序不能为空")
    private Integer stepOrder;

    /** 步骤配置 (JSON) */
    @NotBlank(message = "步骤配置不能为空")
    private String config;

    /** 下一步 ID (CONDITION 类型由 config 中的 trueNextStep / falseNextStep 决定) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long nextStepId;

    /** 是否为入口步骤 */
    private Boolean isEntryPoint;

    /** 步骤描述 */
    @Size(max = 500, message = "步骤描述长度不能超过 500")
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
