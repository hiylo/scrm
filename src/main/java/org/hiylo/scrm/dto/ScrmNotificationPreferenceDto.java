/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationPreferenceDto.java
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
 * SCRM 通知偏好 DTO。
 * <p>
 * 对应 {@code ScrmNotificationPreferenceEntity} 的业务字段, 用于偏好查询与更新接口。
 * quietHoursStart / quietHoursEnd 格式 HH:mm, minPriority 为最低优先级阈值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmNotificationPreferenceDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 用户 ID */
    @NotBlank(message = "用户 ID 不能为空")
    @Size(max = 100, message = "用户 ID 长度不能超过 100")
    private String userId;

    /** 通知渠道: IN_APP/EMAIL/SMS/PUSH */
    @NotBlank(message = "通知渠道不能为空")
    @Pattern(regexp = "IN_APP|EMAIL|SMS|PUSH",
            message = "通知渠道仅支持 IN_APP/EMAIL/SMS/PUSH")
    private String channel;

    /** 分类: SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION */
    @NotBlank(message = "分类不能为空")
    @Pattern(regexp = "SYSTEM|MARKETING|SERVICE|ALERT|REMINDER|VERIFICATION",
            message = "分类仅支持 SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION")
    private String category;

    /** 是否启用 (可空, 默认 true) */
    private Boolean enabled;

    /** 免打扰开始时间 HH:mm (可空) */
    @Size(max = 10, message = "免打扰开始时间长度不能超过 10")
    private String quietHoursStart;

    /** 免打扰结束时间 HH:mm (可空) */
    @Size(max = 10, message = "免打扰结束时间长度不能超过 10")
    private String quietHoursEnd;

    /** 最低优先级阈值 (可空, 默认 0) */
    private Integer minPriority;

    /** 偏好更新时间 (查询返回) */
    private LocalDateTime updatedAt;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
