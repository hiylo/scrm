/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTemplateRenderDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * SCRM 模板渲染 DTO。
 * <p>
 * 渲染接口入参, 指定模板 ID、变量值映射与目标渠道。{@code variables} 的 value 支持任意类型
 * (字符串/数字/布尔等), 渲染时统一转为字符串。{@code channel} 为空时按模板默认渠道适配。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTemplateRenderDto {

    /** 模板 ID */
    @NotNull(message = "模板 ID 不能为空")
    private Long templateId;

    /** 变量值映射（key 为变量名, value 为替换值, 可空） */
    private Map<String, Object> variables;

    /** 目标渠道（WECHAT/WORK_WECHAT/SMS/EMAIL/APP_PUSH/WEB_SOCKET/DOUYIN/KUAISHOU, 可空） */
    private String channel;
}
