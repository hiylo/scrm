/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmEngagementLevelDto.java
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
 * SCRM 互动活跃度等级 DTO。
 * <p>
 * 对应 {@code ScrmEngagementLevelEntity} 的业务字段, 创建/更新接口入参。levelCode 取值
 * INACTIVE/LOW/MEDIUM/HIGH/VERY_HIGH, priority 数字越大等级越高, maxScore 为空表示无上限。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmEngagementLevelDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 等级名称 */
    @NotBlank(message = "等级名称不能为空")
    @Size(max = 100, message = "等级名称长度不能超过 100")
    private String levelName;

    /** 等级编码: INACTIVE/LOW/MEDIUM/HIGH/VERY_HIGH */
    @NotBlank(message = "等级编码不能为空")
    @Pattern(regexp = "INACTIVE|LOW|MEDIUM|HIGH|VERY_HIGH",
            message = "等级编码仅支持 INACTIVE/LOW/MEDIUM/HIGH/VERY_HIGH")
    private String levelCode;

    /** 最低分 (含) */
    @NotNull(message = "最低分不能为空")
    private Double minScore;

    /** 最高分 (不含, 可空表示无上限) */
    private Double maxScore;

    /** 等级颜色 */
    @Size(max = 20, message = "颜色长度不能超过 20")
    private String color;

    /** 描述 */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 建议动作 */
    @Size(max = 500, message = "建议动作长度不能超过 500")
    private String recommendedAction;

    /** 优先级 (数字越大等级越高) */
    private Integer priority;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
