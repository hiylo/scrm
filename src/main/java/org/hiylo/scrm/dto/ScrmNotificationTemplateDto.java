/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationTemplateDto.java
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
 * SCRM 通知模板 DTO。
 * <p>
 * 对应 {@code ScrmNotificationTemplateEntity} 的业务字段, 用于模板增删改查接口入参与返回。
 * templateCode 唯一, channel 标识发送渠道, content 支持变量占位符。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmNotificationTemplateDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200")
    private String templateName;

    /** 模板编码 (唯一) */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 100, message = "模板编码长度不能超过 100")
    private String templateCode;

    /** 分类: SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION */
    @NotBlank(message = "分类不能为空")
    @Pattern(regexp = "SYSTEM|MARKETING|SERVICE|ALERT|REMINDER|VERIFICATION",
            message = "分类仅支持 SYSTEM/MARKETING/SERVICE/ALERT/REMINDER/VERIFICATION")
    private String category;

    /** 通知渠道: IN_APP/EMAIL/SMS/PUSH/WEBHOOK */
    @NotBlank(message = "通知渠道不能为空")
    @Pattern(regexp = "IN_APP|EMAIL|SMS|PUSH|WEBHOOK",
            message = "通知渠道仅支持 IN_APP/EMAIL/SMS/PUSH/WEBHOOK")
    private String channel;

    /** 标题模板 */
    @NotBlank(message = "标题模板不能为空")
    @Size(max = 200, message = "标题模板长度不能超过 200")
    private String title;

    /** 内容模板 (支持变量: {customerName} / {amount}) */
    @NotBlank(message = "内容模板不能为空")
    private String content;

    /** 可用变量 (逗号分隔, 可空) */
    @Size(max = 500, message = "可用变量长度不能超过 500")
    private String variables;

    /** 发送者名称 (可空) */
    @Size(max = 100, message = "发送者名称长度不能超过 100")
    private String senderName;

    /** 邮件发送者 (可空) */
    @Size(max = 200, message = "邮件发送者长度不能超过 200")
    private String senderEmail;

    /** 短信签名 (可空) */
    @Size(max = 100, message = "短信签名长度不能超过 100")
    private String smsSignName;

    /** 是否 HTML 内容 (创建时可选, 默认 false) */
    private Boolean isHtml;

    /** 是否启用 (创建时可选, 默认 true) */
    private Boolean enabled;

    /** 使用次数 (查询返回) */
    private Integer usageCount;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 (查询返回) */
    private LocalDateTime createTime;

    /** 更新时间 (查询返回) */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 (查询返回) */
    private Long version;
}
