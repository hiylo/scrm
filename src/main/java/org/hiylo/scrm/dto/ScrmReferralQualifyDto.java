/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralQualifyDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SCRM 推荐达标 DTO。
 * <p>
 * 用于推荐达标 (qualify) 场景: 传入推荐记录 ID、被推荐人消费金额与被推荐人客户 ID。
 * 服务端按活动奖励触发条件校验是否达标 (消费金额/首单/留存等), 达标后更新推荐状态为 QUALIFIED。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmReferralQualifyDto {

    /** 推荐记录 ID */
    @NotNull(message = "推荐记录 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long referralId;

    /** 被推荐人消费金额 (可空, 默认 0) */
    private Double purchaseAmount;

    /** 被推荐人客户 ID (可空, 已注册时可补全关联) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long refereeCustomerId;
}
