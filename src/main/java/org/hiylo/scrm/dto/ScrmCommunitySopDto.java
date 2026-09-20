/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunitySopDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
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
 * SCRM 社群 SOP DTO。
 * <p>
 * 对应 {@code ScrmCommunitySopEntity} 的业务字段, 用于 SOP 增删改查接口入参与返回。
 * triggerType 标识触发类型, actionType 标识动作类型, triggerConfig 为 JSON 配置字符串。
 * communityId 为空表示对所有群生效。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmCommunitySopDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** SOP 名称 */
    @NotBlank(message = "SOP 名称不能为空")
    @Size(max = 200, message = "SOP 名称长度不能超过 200")
    private String sopName;

    /** 关联社群 ID (可空, 空=所有群) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long communityId;

    /** 触发类型: TIME_BASED/MEMBER_JOIN/MEMBER_LEAVE/INACTIVE/KEYWORD/MANUAL */
    @NotBlank(message = "触发类型不能为空")
    @Pattern(regexp = "TIME_BASED|MEMBER_JOIN|MEMBER_LEAVE|INACTIVE|KEYWORD|MANUAL",
            message = "触发类型仅支持 TIME_BASED/MEMBER_JOIN/MEMBER_LEAVE/INACTIVE/KEYWORD/MANUAL")
    private String triggerType;

    /** 触发配置 (JSON: {time, days, keywords, inactiveDays}) */
    private String triggerConfig;

    /** 动作类型: SEND_MESSAGE/SEND_WELCOME/SEND_REMINDER/ADD_TAG/NOTIFY_MANAGER */
    @NotBlank(message = "动作类型不能为空")
    @Pattern(regexp = "SEND_MESSAGE|SEND_WELCOME|SEND_REMINDER|ADD_TAG|NOTIFY_MANAGER",
            message = "动作类型仅支持 SEND_MESSAGE/SEND_WELCOME/SEND_REMINDER/ADD_TAG/NOTIFY_MANAGER")
    private String actionType;

    /** 动作内容 (消息模板) */
    @NotBlank(message = "动作内容不能为空")
    private String actionContent;

    /** 延迟执行分钟 (默认 0) */
    private Integer delayMinutes;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 执行次数 (查询返回) */
    private Integer executionCount;

    /** 最后执行时间 (查询返回) */
    private LocalDateTime lastExecutedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
