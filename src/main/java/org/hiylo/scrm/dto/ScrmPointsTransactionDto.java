/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsTransactionDto.java
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
 * SCRM 积分流水 DTO。
 * <p>
 * 对应 {@code ScrmPointsTransactionEntity} 的业务字段, 主要用于查询返回。流水由系统在
 * 获取/消耗/冻结/解冻/过期/调整积分时自动产生, 一般不通过接口直接新建。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmPointsTransactionDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联积分账户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 冗余客户名称 */
    private String customerName;

    /** 交易类型: EARN/REDEEM/FREEZE/UNFREEZE/EXPIRE/ADJUST */
    private String transactionType;

    /** 积分变动值 (正/负) */
    private Integer points;

    /** 变动后余额 */
    private Integer balanceAfter;

    /** 关联规则 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;

    /** 触发事件 */
    private String triggerEvent;

    /** 来源: SYSTEM/MANUAL/EXCHANGE/ORDER */
    private String sourceType;

    /** 来源 ID */
    private String sourceId;

    /** 描述 */
    private String description;

    /** 积分过期时间 */
    private LocalDateTime expiresAt;

    /** 是否已过期 */
    private Boolean expired;

    /** 流水发生时间 */
    private LocalDateTime createdAt;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
