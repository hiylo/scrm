/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmRfmConfigDto.java
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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM RFM 配置 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmRfmConfigDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 配置名称 */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 200, message = "配置名称长度不能超过 200")
    private String configName;

    /** 配置描述 */
    @Size(max = 500, message = "配置描述长度不能超过 500")
    private String description;

    /** R 权重 (0-1) */
    @NotNull(message = "R 权重不能为空")
    @Min(value = 0, message = "R 权重不能小于 0")
    @Max(value = 1, message = "R 权重不能大于 1")
    private Double rWeight;

    /** F 权重 (0-1) */
    @NotNull(message = "F 权重不能为空")
    @Min(value = 0, message = "F 权重不能小于 0")
    @Max(value = 1, message = "F 权重不能大于 1")
    private Double fWeight;

    /** M 权重 (0-1) */
    @NotNull(message = "M 权重不能为空")
    @Min(value = 0, message = "M 权重不能小于 0")
    @Max(value = 1, message = "M 权重不能大于 1")
    private Double mWeight;

    /** R 高/低阈值天数 */
    @NotNull(message = "R 阈值不能为空")
    @Min(value = 1, message = "R 阈值不能小于 1")
    private Integer rThreshold;

    /** F 高/低阈值次数 */
    @NotNull(message = "F 阈值不能为空")
    @Min(value = 0, message = "F 阈值不能小于 0")
    private Integer fThreshold;

    /** M 高/低阈值金额 */
    @NotNull(message = "M 阈值不能为空")
    @Min(value = 0, message = "M 阈值不能小于 0")
    private Double mThreshold;

    /** R 数据源: LAST_INTERACTION / LAST_ORDER */
    @Pattern(regexp = "LAST_INTERACTION|LAST_ORDER", message = "R 数据源仅支持 LAST_INTERACTION/LAST_ORDER")
    private String recencySource;

    /** M 数据源: TOTAL_SPENT / MANUAL */
    @Pattern(regexp = "TOTAL_SPENT|MANUAL", message = "M 数据源仅支持 TOTAL_SPENT/MANUAL")
    private String monetarySource;

    /** 是否为默认配置 */
    private Boolean isDefault;

    /** 是否启用 */
    private Boolean enabled;

    /** 最近一次计算时间 */
    private LocalDateTime lastCalculatedAt;

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
