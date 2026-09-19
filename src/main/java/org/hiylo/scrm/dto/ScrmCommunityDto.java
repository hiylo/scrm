/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityDto.java
 * Date : 2026/08/04 08:40:58
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
 * SCRM 社群 DTO。
 * <p>
 * 对应 {@code ScrmCommunityEntity} 的业务字段, 用于社群增删改查接口入参与返回。
 * platformType 标识来源平台, communityType 标识业务群类型, status 控制社群状态。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCommunityDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 社群名称 */
    @NotBlank(message = "社群名称不能为空")
    @Size(max = 200, message = "社群名称长度不能超过 200")
    private String communityName;

    /** 平台类型: WECHAT/WORK_WECHAT/QQ/DISCORD/OTHER */
    @NotBlank(message = "平台类型不能为空")
    @Pattern(regexp = "WECHAT|WORK_WECHAT|QQ|DISCORD|OTHER",
            message = "平台类型仅支持 WECHAT/WORK_WECHAT/QQ/DISCORD/OTHER")
    private String platformType;

    /** 社群类型: CUSTOMER/FAN/VIP/REGION/INTEREST/PRODUCT */
    @NotBlank(message = "社群类型不能为空")
    @Pattern(regexp = "CUSTOMER|FAN|VIP|REGION|INTEREST|PRODUCT",
            message = "社群类型仅支持 CUSTOMER/FAN/VIP/REGION/INTEREST/PRODUCT")
    private String communityType;

    /** 平台群 ID (可空) */
    @Size(max = 200, message = "平台群 ID 长度不能超过 200")
    private String roomId;

    /** 群二维码 URL (可空) */
    @Size(max = 500, message = "群二维码 URL 长度不能超过 500")
    private String qrCode;

    /** 描述 (可空) */
    @Size(max = 500, message = "描述长度不能超过 500")
    private String description;

    /** 群主 ID */
    @NotBlank(message = "群主 ID 不能为空")
    @Size(max = 100, message = "群主 ID 长度不能超过 100")
    private String ownerId;

    /** 群主名称 (可空) */
    @Size(max = 100, message = "群主名称长度不能超过 100")
    private String ownerName;

    /** 群管理员 ID (可空) */
    @Size(max = 100, message = "群管理员 ID 长度不能超过 100")
    private String managerId;

    /** 群管理员名称 (可空) */
    @Size(max = 100, message = "群管理员名称长度不能超过 100")
    private String managerName;

    /** 最大成员数 (可空, 默认 500) */
    private Integer maxMembers;

    /** 标签 (逗号分隔, 可空) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 状态: ACTIVE/INACTIVE/DISSOLVED (创建时可选, 默认 ACTIVE) */
    @Pattern(regexp = "ACTIVE|INACTIVE|DISSOLVED",
            message = "状态仅支持 ACTIVE/INACTIVE/DISSOLVED")
    private String status;

    /** 建群时间 (可空) */
    private LocalDateTime createdAt;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 成员数 (查询返回) */
    private Integer memberCount;

    /** 活跃成员数 (查询返回) */
    private Integer activeMembers;

    /** 今日新增成员数 (查询返回) */
    private Integer todayNewMembers;

    /** 今日消息数 (查询返回) */
    private Integer todayMessages;

    /** 活跃度评分 0-100 (查询返回) */
    private Double activityScore;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
