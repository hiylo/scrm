/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSurveyDistributeDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * SCRM 调查问卷批量分发 DTO。
 * <p>
 * 用于批量创建调查邀请 (模拟发送): 按指定 channel 向 customerIds 列表中的客户逐一生成邀请记录,
 * 邀请码由系统自动生成。sourceEvent / sourceId 用于追溯触发来源 (可空)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmSurveyDistributeDto {

    /** 调查问卷 ID */
    @NotNull(message = "调查问卷 ID 不能为空")
    private Long surveyId;

    /** 目标客户 ID 列表 */
    @NotNull(message = "客户 ID 列表不能为空")
    private List<@NotNull(message = "客户 ID 不能为空") Long> customerIds;

    /** 分发渠道: IN_APP / SMS / EMAIL / WECHAT */
    @NotBlank(message = "分发渠道不能为空")
    @Pattern(regexp = "IN_APP|SMS|EMAIL|WECHAT",
            message = "分发渠道仅支持 IN_APP/SMS/EMAIL/WECHAT")
    private String channel;

    /** 触发来源事件 (可空, 如 PURCHASE / SERVICE_TICKET) */
    private String sourceEvent;

    /** 触发来源 ID (可空, 如订单号 / 工单号) */
    private String sourceId;
}
