/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistCheckDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 黑名单检查 DTO。
 * <p>
 * 用于检查指定目标是否在黑名单 / 灰名单 / 观察名单中。targetType 与 targetValue 必填。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmBlacklistCheckDto {

    /** 目标类型: CUSTOMER / PHONE / EMAIL / IP / DEVICE / ID_CARD / BANK_CARD / ADDRESS / WECHAT_ID / COMPANY */
    @NotBlank(message = "目标类型不能为空")
    @Size(max = 30, message = "目标类型长度不能超过 30")
    private String targetType;

    /** 目标值 */
    @NotBlank(message = "目标值不能为空")
    @Size(max = 500, message = "目标值长度不能超过 500")
    private String targetValue;

    /** 名单类型过滤 (可空, 为空则检查所有名单类型) */
    @Size(max = 20, message = "名单类型长度不能超过 20")
    private String listType;
}
