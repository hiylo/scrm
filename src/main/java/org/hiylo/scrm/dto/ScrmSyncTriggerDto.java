/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSyncTriggerDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 外部联系人同步触发 DTO。
 * <p>
 * 触发同步时使用, 指定同步配置 ID 与同步模式 (可空, 缺省回退到配置自身模式)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSyncTriggerDto {

    /** 同步配置 ID */
    @NotNull(message = "同步配置 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long configId;

    /** 同步模式 (可空, 缺省使用配置的 syncMode): INCREMENTAL / FULL */
    @Size(max = 20, message = "同步模式长度不能超过 20")
    @Pattern(regexp = "INCREMENTAL|FULL",
            message = "同步模式仅支持 INCREMENTAL/FULL")
    private String syncMode;
}
