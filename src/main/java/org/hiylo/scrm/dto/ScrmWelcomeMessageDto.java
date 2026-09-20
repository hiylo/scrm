/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWelcomeMessageDto.java
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
 * SCRM 企微欢迎语配置 DTO。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmWelcomeMessageDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 绑定账号 ID（可空, 空=所有账号） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 绑定渠道活码 ID（可空, 空=非渠道来源） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long channelCodeId;

    /** 适用平台（默认 wework） */
    @Size(max = 30, message = "平台类型长度不能超过 30")
    private String platformType;

    /** 消息类型: TEXT / IMAGE / LINK / MINIPROGRAM / MIXED */
    @Size(max = 20, message = "消息类型长度不能超过 20")
    @Pattern(regexp = "TEXT|IMAGE|LINK|MINIPROGRAM|MIXED",
            message = "消息类型仅支持 TEXT/IMAGE/LINK/MINIPROGRAM/MIXED")
    private String messageType;

    /** 文本内容（支持 ${nickname} 等变量） */
    @NotBlank(message = "文本内容不能为空")
    private String content;

    /** 图片/文件 URL（可空） */
    @Size(max = 500, message = "媒体 URL 长度不能超过 500")
    private String mediaUrl;

    /** 链接标题（可空） */
    @Size(max = 200, message = "链接标题长度不能超过 200")
    private String linkTitle;

    /** 链接 URL（可空） */
    @Size(max = 500, message = "链接 URL 长度不能超过 500")
    private String linkUrl;

    /** 链接描述（可空） */
    @Size(max = 500, message = "链接描述长度不能超过 500")
    private String linkDesc;

    /** 小程序标题（可空） */
    @Size(max = 200, message = "小程序标题长度不能超过 200")
    private String miniprogramTitle;

    /** 小程序 AppId（可空） */
    @Size(max = 100, message = "小程序 AppId 长度不能超过 100")
    private String miniprogramAppId;

    /** 小程序页面路径（可空） */
    @Size(max = 500, message = "小程序页面路径长度不能超过 500")
    private String miniprogramPage;

    /** 跟进消息序列（可空, JSON 数组, 欢迎语后的二次触达消息） */
    private String secondaryMessages;

    /** 延迟发送秒数（默认 0, 立即发送） */
    private Integer delaySeconds;

    /** 同一客户冷却期分钟数（默认 0, 不限制） */
    private Integer cooldownMinutes;

    /** 生效开始时间 HH:mm（可空, 空=不限时段） */
    @Size(max = 5, message = "生效开始时间长度不能超过 5")
    private String effectiveTimeStart;

    /** 生效结束时间 HH:mm（可空, 空=不限时段） */
    @Size(max = 5, message = "生效结束时间长度不能超过 5")
    private String effectiveTimeEnd;

    /** 周末是否生效（默认 true） */
    private Boolean weekendEnabled;

    /** 优先级（默认 0, 多规则时取最高者） */
    private Integer priority;

    /** 状态: ACTIVE / INACTIVE */
    @Size(max = 20, message = "状态长度不能超过 20")
    @Pattern(regexp = "ACTIVE|INACTIVE",
            message = "状态仅支持 ACTIVE/INACTIVE")
    private String status;

    /** 触发次数 */
    private Integer triggerCount;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
