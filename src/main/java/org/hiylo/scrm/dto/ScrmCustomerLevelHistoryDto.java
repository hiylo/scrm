/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelHistoryDto.java
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
 * SCRM 客户等级变更历史 DTO。
 * <p>
 * 仅用于查询返回, 不接收前端入参。等级变更通过 {@link ScrmCustomerLevelAssignDto} (手动分配)
 * 或 {@code evaluateRules} (自动评估) 触发, 由服务层写入历史记录。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmCustomerLevelHistoryDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (变更时快照) */
    private String customerName;

    /** 原等级 ID (首次为 null) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromLevelId;

    /** 原等级名称 */
    private String fromLevelName;

    /** 新等级 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toLevelId;

    /** 新等级名称 */
    private String toLevelName;

    /** 变更类型: UPGRADE / DOWNGRADE / INITIAL / MANUAL / AUTO */
    private String changeType;

    /** 变更原因 */
    private String changeReason;

    /** 操作人 */
    private String changedBy;

    /** 变更时间 */
    private LocalDateTime changedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 乐观锁版本号 */
    private Long version;
}
