/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipEnrollDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM 会员注册 DTO。
 * <p>
 * 用于将客户注册为会员: 指定客户 ID 与初始等级 ID, 可空传入会员推荐码与客户名称。
 * 服务端校验等级有效后生成会员卡号、设置初始等级并赠送注册积分。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMembershipEnrollDto {

    /** 客户 ID */
    @NotNull(message = "客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 等级 ID (可空, 为空时取最低等级) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tierId;

    /** 会员推荐码 (可空) */
    @Size(max = 100, message = "会员推荐码长度不能超过 100")
    private String referralCode;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 会员卡类型: STANDARD / VIP / BLACK_GOLD / DIAMOND / CUSTOM (可空, 缺省 STANDARD) */
    @Size(max = 30, message = "会员卡类型长度不能超过 30")
    private String memberCardType;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
