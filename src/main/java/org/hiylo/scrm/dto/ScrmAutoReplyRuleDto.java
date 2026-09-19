/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAutoReplyRuleDto.java
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

/**
 * SCRM 消息自动回复规则 DTO。
 * <p>
 * 对应 {@code ScrmAutoReplyRuleEntity} 的业务字段, 不含公共字段
 * (id/createTime/updateTime/version/triggerCount/lastTriggeredAt)。
 * 创建/更新接口入参, 校验注解保证必填字段与取值约束。replyType=TEMPLATE 时
 * replyTemplateId 必填, 由 Service 层校验模板存在性。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmAutoReplyRuleDto {

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称长度不能超过 200")
    private String ruleName;

    /** 规则类型: KEYWORD/WELCOME/TIMEOUT/OFFLINE/FORM/EVENT/CONDITIONAL */
    @NotBlank(message = "规则类型不能为空")
    @Size(max = 30, message = "规则类型长度不能超过 30")
    @Pattern(regexp = "KEYWORD|WELCOME|TIMEOUT|OFFLINE|FORM|EVENT|CONDITIONAL",
            message = "规则类型仅支持 KEYWORD/WELCOME/TIMEOUT/OFFLINE/FORM/EVENT/CONDITIONAL")
    private String ruleType;

    /** 规则描述 */
    @Size(max = 500, message = "规则描述长度不能超过 500")
    private String description;

    /** 匹配方式: EXACT/CONTAINS/STARTS_WITH/ENDS_WITH/REGEX/FUZZY（默认 EXACT） */
    @Size(max = 20, message = "匹配方式长度不能超过 20")
    @Pattern(regexp = "EXACT|CONTAINS|STARTS_WITH|ENDS_WITH|REGEX|FUZZY|",
            message = "匹配方式仅支持 EXACT/CONTAINS/STARTS_WITH/ENDS_WITH/REGEX/FUZZY")
    private String matchType;

    /** 关键词（逗号分隔）或正则表达式 */
    @Size(max = 1000, message = "关键词长度不能超过 1000")
    private String keywords;

    /** 匹配范围: MESSAGE/FULL_TEXT/SUBJECT（默认 MESSAGE） */
    @Size(max = 20, message = "匹配范围长度不能超过 20")
    @Pattern(regexp = "MESSAGE|FULL_TEXT|SUBJECT|",
            message = "匹配范围仅支持 MESSAGE/FULL_TEXT/SUBJECT")
    private String matchScope;

    /** 回复类型: TEXT/IMAGE/LINK/FILE/TEMPLATE/HTML/RICH_TEXT（默认 TEXT） */
    @Size(max = 20, message = "回复类型长度不能超过 20")
    @Pattern(regexp = "TEXT|IMAGE|LINK|FILE|TEMPLATE|HTML|RICH_TEXT|",
            message = "回复类型仅支持 TEXT/IMAGE/LINK/FILE/TEMPLATE/HTML/RICH_TEXT")
    private String replyType;

    /** 回复内容 */
    @NotBlank(message = "回复内容不能为空")
    private String replyContent;

    /** 回复模板 ID（replyType=TEMPLATE 时必填） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long replyTemplateId;

    /** 媒体文件 URL */
    @Size(max = 500, message = "媒体文件 URL 长度不能超过 500")
    private String mediaUrl;

    /** 链接 URL */
    @Size(max = 500, message = "链接 URL 长度不能超过 500")
    private String linkUrl;

    /** 链接标题 */
    @Size(max = 200, message = "链接标题长度不能超过 200")
    private String linkTitle;

    /** 链接描述 */
    @Size(max = 500, message = "链接描述长度不能超过 500")
    private String linkDescription;

    /** 链接缩略图 URL */
    @Size(max = 500, message = "链接缩略图 URL 长度不能超过 500")
    private String linkThumbnail;

    /** 适用渠道（逗号分隔: WECHAT/WORK_WECHAT/WEB/APP/SMS/EMAIL） */
    @Size(max = 500, message = "适用渠道长度不能超过 500")
    private String applicableChannels;

    /** 适用账号 ID（逗号分隔） */
    @Size(max = 500, message = "适用账号 ID 长度不能超过 500")
    private String applicableAccounts;

    /** 仅工作时间触发（默认 FALSE） */
    private Boolean workTimeOnly;

    /** 工作时间开始（HH:mm） */
    @Size(max = 10, message = "工作时间开始长度不能超过 10")
    private String workTimeStart;

    /** 工作时间结束（HH:mm） */
    @Size(max = 10, message = "工作时间结束长度不能超过 10")
    private String workTimeEnd;

    /** 工作日（1-7 逗号分隔, 1=周一） */
    @Size(max = 50, message = "工作日长度不能超过 50")
    private String workDays;

    /** 超时秒数（TIMEOUT 类型使用） */
    private Integer timeoutSeconds;

    /** 优先级（数字越小越优先, 默认 0） */
    private Integer priority;

    /** 每客户最大触发次数（0=无限） */
    private Integer maxTriggerPerCustomer;

    /** 冷却时间（分钟, 0=无冷却） */
    private Integer cooldownMinutes;

    /** 是否兜底规则（默认 FALSE） */
    private Boolean fallbackRule;

    /** 是否启用（默认 TRUE） */
    private Boolean enabled;

    /** 创建人 */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
