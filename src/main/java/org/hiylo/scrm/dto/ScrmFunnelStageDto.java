/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFunnelStageDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 销售漏斗阶段 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmFunnelStageDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属漏斗 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long funnelId;

    /** 阶段名称 */
    @NotBlank(message = "阶段名称不能为空")
    @Size(max = 100, message = "阶段名称长度不能超过 100")
    private String stageName;

    /** 阶段顺序 (数字越小越靠前) */
    @NotNull(message = "阶段顺序不能为空")
    private Integer stageOrder;

    /** 阶段描述 */
    @Size(max = 500, message = "阶段描述长度不能超过 500")
    private String description;

    /** 进入条件 */
    @Size(max = 500, message = "进入条件长度不能超过 500")
    private String enterCondition;

    /** 退出条件 */
    @Size(max = 500, message = "退出条件长度不能超过 500")
    private String exitCondition;

    /** 是否为成单阶段 */
    private Boolean isClosedStage;

    /** 是否为输单阶段 */
    private Boolean isLostStage;

    /** 成交概率 (0-100) */
    @Min(value = 0, message = "成交概率不能小于 0")
    @Max(value = 100, message = "成交概率不能大于 100")
    private Integer probability;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
