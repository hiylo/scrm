/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityMemberDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
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
 * SCRM 社群成员 DTO。
 * <p>
 * 对应 {@code ScrmCommunityMemberEntity} 的业务字段, 用于成员增删改查接口入参与返回。
 * role 标识群角色, joinType 标识入群方式, status 标识在群状态。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCommunityMemberDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 社群 ID */
    @NotNull(message = "社群 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long communityId;

    /** 关联客户 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 成员名称 */
    @NotBlank(message = "成员名称不能为空")
    @Size(max = 200, message = "成员名称长度不能超过 200")
    private String memberName;

    /** 群昵称 (可空) */
    @Size(max = 200, message = "群昵称长度不能超过 200")
    private String memberAlias;

    /** 平台 UID (可空) */
    @Size(max = 200, message = "平台 UID 长度不能超过 200")
    private String platformUid;

    /** 群角色: OWNER/ADMIN/MEMBER/GUEST (默认 MEMBER) */
    @Pattern(regexp = "OWNER|ADMIN|MEMBER|GUEST",
            message = "群角色仅支持 OWNER/ADMIN/MEMBER/GUEST")
    private String role;

    /** 入群方式: INVITED/QR_CODE/SEARCH/OTHER (默认 INVITED) */
    @Pattern(regexp = "INVITED|QR_CODE|SEARCH|OTHER",
            message = "入群方式仅支持 INVITED/QR_CODE/SEARCH/OTHER")
    private String joinType;

    /** 入群时间 */
    @NotNull(message = "入群时间不能为空")
    private LocalDateTime joinAt;

    /** 最后活跃时间 (可空) */
    private LocalDateTime lastActiveAt;

    /** 邀请人 (可空) */
    @Size(max = 100, message = "邀请人长度不能超过 100")
    private String invitedBy;

    /** 状态: ACTIVE/REMOVED/LEFT/MUTED (查询返回, 默认 ACTIVE) */
    @Pattern(regexp = "ACTIVE|REMOVED|LEFT|MUTED",
            message = "状态仅支持 ACTIVE/REMOVED/LEFT/MUTED")
    private String status;

    /** 消息数 (查询返回) */
    private Integer messageCount;

    /** 是否活跃 (查询返回) */
    private Boolean isActive;

    /** 退群时间 (查询返回) */
    private LocalDateTime leftAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
