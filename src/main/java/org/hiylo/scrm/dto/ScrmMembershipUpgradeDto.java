/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMembershipUpgradeDto.java
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
 * SCRM 会员升级 DTO。
 * <p>
 * 用于会员升级场景: 传入会员记录 ID 与目标等级 ID, 可空传入升级原因。
 * 服务端校验目标等级高于当前等级且等级有效后, 更新会员等级、赠送升级积分并记录升降级历史。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMembershipUpgradeDto {

    /** 会员记录 ID */
    @NotNull(message = "会员记录 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long membershipId;

    /** 目标等级 ID */
    @NotNull(message = "目标等级 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetTierId;

    /** 升级原因 (可空) */
    @Size(max = 500, message = "升级原因长度不能超过 500")
    private String reason;
}
