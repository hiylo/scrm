/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmSegmentStrategyDto.java
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
 * SCRM RFM 分群策略 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmRfmSegmentStrategyDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 策略名称 */
    @NotBlank(message = "策略名称不能为空")
    @Size(max = 200, message = "策略名称长度不能超过 200")
    private String strategyName;

    /** 目标分群大类 */
    @NotBlank(message = "目标分群大类不能为空")
    @Size(max = 30, message = "目标分群大类长度不能超过 30")
    private String segmentCategory;

    /** 目标 RFM 编码 (可空, 为空表示该大类所有编码) */
    @Size(max = 20, message = "目标 RFM 编码长度不能超过 20")
    private String segmentCode;

    /** 策略类型: RETAIN/ACTIVATE/RECOVER/UPGRADE/MAINTAIN */
    @NotBlank(message = "策略类型不能为空")
    @Pattern(regexp = "RETAIN|ACTIVATE|RECOVER|UPGRADE|MAINTAIN",
            message = "策略类型仅支持 RETAIN/ACTIVATE/RECOVER/UPGRADE/MAINTAIN")
    private String strategyType;

    /** 策略描述 */
    @Size(max = 500, message = "策略描述长度不能超过 500")
    private String description;

    /** 推荐动作列表 (JSON 数组) */
    @NotBlank(message = "推荐动作不能为空")
    private String actions;

    /** 是否启用 */
    private Boolean enabled;

    /** 优先级 */
    private Integer priority;

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
