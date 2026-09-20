/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReferralCreateDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 推荐创建 DTO。
 * <p>
 * 用于发起一次推荐: 指定推荐活动、推荐人与被推荐人联系方式及推荐渠道。
 * 服务端校验活动有效性后生成推荐码与推荐记录, 推荐渠道缺省 CODE。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmReferralCreateDto {

    /** 推荐活动 ID */
    @NotNull(message = "推荐活动 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long programId;

    /** 推荐人客户 ID */
    @NotNull(message = "推荐人客户 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long referrerCustomerId;

    /** 推荐人名称 (可空) */
    @Size(max = 200, message = "推荐人名称长度不能超过 200")
    private String referrerName;

    /** 被推荐人联系方式 (可空) */
    @Size(max = 200, message = "被推荐人联系方式长度不能超过 200")
    private String refereeContact;

    /** 推荐渠道: LINK / QR_CODE / CODE / EMAIL / SMS / WECHAT (可空, 缺省 CODE) */
    @Size(max = 30, message = "推荐渠道长度不能超过 30")
    private String channel;

    /** 备注 (可空) */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String notes;
}
