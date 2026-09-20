/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketAssignDto.java
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
 * SCRM 工单分配请求 DTO。
 * <p>
 * 用于将工单分配给处理人或处理团队。assigneeId 与 teamId 至少传其一;
 * assigneeName 可选, 缺省由服务端原值保留。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTicketAssignDto {

    /** 工单 ID */
    @NotNull(message = "工单 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ticketId;

    /** 处理人 ID (与 teamId 至少传其一) */
    @Size(max = 100, message = "处理人 ID 长度不能超过 100")
    private String assigneeId;

    /** 处理人名称 (可空) */
    @Size(max = 100, message = "处理人名称长度不能超过 100")
    private String assigneeName;

    /** 处理团队 ID (与 assigneeId 至少传其一) */
    @Size(max = 100, message = "处理团队 ID 长度不能超过 100")
    private String teamId;
}
