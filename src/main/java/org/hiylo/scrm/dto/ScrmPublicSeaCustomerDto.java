/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPublicSeaCustomerDto.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户公海池 DTO。
 * <p>
 * 用于公海池线索的创建、查询、领取、分配、转移、回收与转正接口参数传递。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmPublicSeaCustomerDto {

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

    /** 来源渠道 */
    @Size(max = 100, message = "来源渠道长度不能超过 100")
    private String sourceChannel;

    /** 来源渠道码 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceChannelCodeId;

    /** 生命周期: NEW/PROSPECT/ACTIVE/DORMANT/CHURNED */
    @Size(max = 20, message = "生命周期长度不能超过 20")
    @Pattern(regexp = "NEW|PROSPECT|ACTIVE|DORMANT|CHURNED",
            message = "生命周期仅支持 NEW/PROSPECT/ACTIVE/DORMANT/CHURNED")
    private String lifecycle;

    /** 客户标签 (逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 备注 */
    private String remark;

    /** 当前归属人 userId */
    @Size(max = 100, message = "归属人 userId 长度不能超过 100")
    private String assignedTo;

    /** 最近一次分配时间 */
    private LocalDateTime assignedAt;

    /** 分配过期时间 (超时自动回收) */
    private LocalDateTime assignmentExpireAt;

    /** 被回收次数 */
    private Integer recallCount;

    /** 最近一次分配时间 */
    private LocalDateTime lastAssignedAt;

    /** 公海状态: AVAILABLE/ASSIGNED/LOCKED/RECALLED */
    @Size(max = 20, message = "公海状态长度不能超过 20")
    @Pattern(regexp = "AVAILABLE|ASSIGNED|LOCKED|RECALLED",
            message = "公海状态仅支持 AVAILABLE/ASSIGNED/LOCKED/RECALLED")
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
