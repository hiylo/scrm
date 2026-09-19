/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWebhookEventDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM Webhook 事件发布 DTO。
 * <p>
 * 外部系统或定时任务通过此 DTO 投递事件, 由服务查找订阅该事件类型的活跃 Webhook,
 * 创建日志记录并异步推送。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmWebhookEventDto {

    /** 事件类型 (如 CUSTOMER_CREATED / MESSAGE_RECEIVED) */
    @NotBlank(message = "事件类型不能为空")
    @Size(max = 50, message = "事件类型长度不能超过 50")
    private String eventType;

    /** 事件数据 JSON（可空, 携带事件上下文） */
    private String eventData;

    /** 关联实体 ID（可空, 用于过滤与定位） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long entityId;
}
