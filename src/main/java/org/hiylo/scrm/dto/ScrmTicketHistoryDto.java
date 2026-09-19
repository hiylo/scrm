/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketHistoryDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 工单流转历史 DTO。
 * <p>
 * 仅用于查询返回, 由服务端在工单动作执行时自动记录。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmTicketHistoryDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联工单 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ticketId;

/** 动作类型: CREATED / ASSIGNED / STATUS_CHANGED / PRIORITY_CHANGED / CATEGORY_CHANGED / COMMENTED / ESCALATED /
         * REOPENED / CLOSED */
    private String actionType;

    /** 变更前值 (可空) */
    private String fromValue;

    /** 变更后值 (可空) */
    private String toValue;

    /** 操作人 ID */
    private String operatorId;

    /** 操作人名称 (可空) */
    private String operatorName;

    /** 动作发生时间 */
    private LocalDateTime actionTime;

    /** 备注 (可空) */
    private String note;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 乐观锁版本号 */
    private Long version;
}
