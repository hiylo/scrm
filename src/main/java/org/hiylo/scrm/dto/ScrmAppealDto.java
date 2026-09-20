/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAppealDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 黑名单申诉 DTO。
 * <p>
 * 用于对黑名单条目发起申诉, blacklistId 与 reason 必填。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmAppealDto {

    /** 黑名单条目 ID */
    @NotNull(message = "黑名单条目 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long blacklistId;

    /** 申诉原因 */
    @Size(max = 1000, message = "申诉原因长度不能超过 1000")
    private String reason;

    /** 申诉人 (可空, 缺省取当前用户) */
    @Size(max = 100, message = "申诉人长度不能超过 100")
    private String appealedBy;
}
