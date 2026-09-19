/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPointsAccountDto.java
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
 * SCRM 积分账户 DTO。
 * <p>
 * 对应 {@code ScrmPointsAccountEntity} 的业务字段, 主要用于查询返回。账户由
 * {@code ScrmPointsService.getOrCreateAccount} 自动创建, 一般不通过接口直接新建。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmPointsAccountDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 客户 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 冗余客户名称 */
    private String customerName;

    /** 当前可用积分 */
    private Integer currentPoints;

    /** 冻结积分 */
    private Integer frozenPoints;

    /** 累计获取 */
    private Integer totalEarned;

    /** 累计消耗 */
    private Integer totalRedeemed;

    /** 累计过期 */
    private Integer totalExpired;

    /** 积分等级 */
    private String level;

    /** 最近获取时间 */
    private LocalDateTime lastEarnAt;

    /** 最近消耗时间 */
    private LocalDateTime lastRedeemAt;

    /** 积分变动时间 */
    private LocalDateTime updatedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
