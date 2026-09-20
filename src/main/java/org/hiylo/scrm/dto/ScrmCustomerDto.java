/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerDto.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCustomerDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 平台类型 */
    @NotBlank(message = "平台类型不能为空")
    @Size(max = 30, message = "平台类型长度不能超过 30")
    @Pattern(regexp = "wework|douyin|kuaishou|xiaohongshu|bilibili|wechat_personal",
            message = "平台类型仅支持 wework/douyin/kuaishou/xiaohongshu/bilibili/wechat_personal")
    private String platformType;

    /** 平台客户唯一标识 */
    @NotBlank(message = "平台客户 UID 不能为空")
    @Size(max = 200, message = "平台客户 UID 长度不能超过 200")
    private String platformCustomerUid;

    /** 客户昵称 */
    @Size(max = 200, message = "昵称长度不能超过 200")
    private String nickname;

    /** 客户头像 URL */
    @Size(max = 500, message = "头像 URL 长度不能超过 500")
    private String avatarUrl;

    /** 归属账号 ID */
    @NotNull(message = "归属账号 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerAccountId;

    /** 绑定人设 ID（可空） */
    @Size(max = 100, message = "人设 ID 长度不能超过 100")
    private String personaId;

    /** 生命周期：NEW 新 / PROSPECT 意向 / ACTIVE 活跃 / DORMANT 沉睡 / CHURNED 流失 / CONVERTED 已转化 */
    @Size(max = 20, message = "生命周期长度不能超过 20")
    @Pattern(regexp = "NEW|PROSPECT|ACTIVE|DORMANT|CHURNED|CONVERTED",
            message = "生命周期仅支持 NEW/PROSPECT/ACTIVE/DORMANT/CHURNED/CONVERTED")
    private String lifecycle;

    /** 最后交互时间 */
    private LocalDateTime lastInteractionAt;

    /** 下次跟进时间 (用于跟进提醒调度) */
    private LocalDateTime nextFollowUpAt;

    /** 客户备注 (业务员私有备注) */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
