/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackCommentDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 反馈评论 DTO。
 * <p>
 * 用于评论创建与查询返回。创建时必填反馈 ID、评论类型、作者 ID 与内容;
 * isInternal 缺省由服务端按评论类型推断 (INTERNAL 类型默认 true, 其他默认 false);
 * parentCommentId 用于回复指定评论。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmFeedbackCommentDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 关联反馈 ID */
    @NotNull(message = "反馈 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long feedbackId;

    /** 评论类型: CUSTOMER / AGENT / INTERNAL / SYSTEM */
    @NotBlank(message = "评论类型不能为空")
    @Size(max = 20, message = "评论类型长度不能超过 20")
    private String commentType;

    /** 作者 ID */
    @NotBlank(message = "作者 ID 不能为空")
    @Size(max = 100, message = "作者 ID 长度不能超过 100")
    private String authorId;

    /** 作者名称 (可空) */
    @Size(max = 100, message = "作者名称长度不能超过 100")
    private String authorName;

    /** 作者角色 (可空) */
    @Size(max = 50, message = "作者角色长度不能超过 50")
    private String authorRole;

    /** 评论内容 */
    @NotBlank(message = "评论内容不能为空")
    private String content;

    /** 附件列表 (可空, JSON 数组) */
    @Size(max = 1000, message = "附件列表长度不能超过 1000")
    private String attachments;

    /** 是否内部备注 */
    private Boolean isInternal;

    /** 赞同数 */
    private Integer upvoteCount;

    /** 父评论 ID (可空, 用于回复) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentCommentId;

    /** 评论发生时间 */
    private LocalDateTime createdAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
