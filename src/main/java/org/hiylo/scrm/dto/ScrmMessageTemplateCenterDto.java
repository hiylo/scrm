/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTemplateCenterDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 消息模板中心模板定义 DTO。
 * <p>
 * 对应 {@code ScrmMessageTemplateCenterEntity} 的业务字段, 不含公共字段
 * (id/createTime/updateTime/version)。创建/更新模板接口入参, 校验注解保证
 * 必填字段与长度约束。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmMessageTemplateCenterDto {

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称长度不能超过 200")
    private String templateName;

    /** 模板编码（全局唯一） */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 50, message = "模板编码长度不能超过 50")
    private String templateCode;

    /** 所属分组 ID（可空） */
    private Long groupId;

    /** 所属分组名称（可空, 冗余字段便于展示） */
    @Size(max = 200, message = "分组名称长度不能超过 200")
    private String groupName;

    /** 模板描述（可空） */
    @Size(max = 500, message = "模板描述长度不能超过 500")
    private String description;

    /** 模板类型: TEXT/RICH_TEXT/HTML/CARD/IMAGE_TEXT/VIDEO_TEXT/PROGRAM/JSON */
    @NotBlank(message = "模板类型不能为空")
    @Size(max = 30, message = "模板类型长度不能超过 30")
    private String templateType;

    /** 适用渠道（逗号分隔: WECHAT/WORK_WECHAT/SMS/EMAIL/APP_PUSH/WEB_SOCKET/DOUYIN/KUAISHOU） */
    @NotBlank(message = "适用渠道不能为空")
    @Size(max = 500, message = "适用渠道长度不能超过 500")
    private String channels;

    /** 邮件主题/推送标题（可空） */
    @Size(max = 500, message = "主题长度不能超过 500")
    private String subject;

    /** 模板内容, 支持变量占位符 {{variable}} */
    @NotBlank(message = "模板内容不能为空")
    private String content;

    /** 纯文本内容（可空） */
    @Size(max = 2000, message = "纯文本内容长度不能超过 2000")
    private String plainContent;

    /** HTML 内容（可空） */
    private String htmlContent;

    /** 企微链接（可空） */
    @Size(max = 500, message = "企微链接长度不能超过 500")
    private String wechatLink;

    /** 小程序路径（可空） */
    @Size(max = 500, message = "小程序路径长度不能超过 500")
    private String miniProgramPath;

    /** 变量定义 JSON: [{name,type,defaultValue,description,required}]（可空） */
    private String variables;

    /** 附件列表 JSON（可空） */
    @Size(max = 1000, message = "附件列表长度不能超过 1000")
    private String attachments;

    /** 模板分类（可空） */
    @Size(max = 100, message = "模板分类长度不能超过 100")
    private String category;

    /** 标签（逗号分隔, 可空） */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 适用场景（逗号分隔, 可空） */
    @Size(max = 500, message = "适用场景长度不能超过 500")
    private String applicableScenarios;

    /** 语言（默认 zh_CN） */
    @Size(max = 20, message = "语言长度不能超过 20")
    private String language;

    /** 状态: DRAFT/PENDING_REVIEW/APPROVED/REJECTED/PUBLISHED/ARCHIVED */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 创建人（可空） */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;
}
