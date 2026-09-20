/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFunnelDto.java
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
 * SCRM 销售漏斗 DTO。
 *
 * @author Hsi Chu
 */
@Data
public class ScrmFunnelDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 漏斗名称 */
    @NotBlank(message = "漏斗名称不能为空")
    @Size(max = 200, message = "漏斗名称长度不能超过 200")
    private String funnelName;

    /** 漏斗描述 */
    @Size(max = 500, message = "漏斗描述长度不能超过 500")
    private String description;

    /** 是否为默认漏斗 */
    private Boolean isDefault;

    /** 状态：ACTIVE / INACTIVE */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|INACTIVE", message = "状态仅支持 ACTIVE/INACTIVE")
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
