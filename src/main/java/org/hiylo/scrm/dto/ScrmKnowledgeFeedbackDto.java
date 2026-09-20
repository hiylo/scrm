/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmKnowledgeFeedbackDto.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SCRM 知识反馈 DTO。
 * <p>
 * 对应 {@code ScrmKnowledgeFeedbackEntity} 的业务字段, 创建反馈接口入参。
 * resolvedBy / resolvedAt / resolutionNote / upvoteCount 为查询返回, 由服务端管理。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class ScrmKnowledgeFeedbackDto {

    /** 主键 ID (更新时必填) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 文章 ID */
    @NotNull(message = "文章 ID 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;

    /** 反馈类型: HELPFUL/NOT_HELPFUL/LIKE/DISLIKE/FAVORITE/SHARE/RATING/COMMENT/REPORT */
    @NotBlank(message = "反馈类型不能为空")
    @Pattern(regexp = "HELPFUL|NOT_HELPFUL|LIKE|DISLIKE|FAVORITE|SHARE|RATING|COMMENT|REPORT",
            message = "反馈类型非法")
    private String feedbackType;

    /** 用户 ID */
    @NotBlank(message = "用户 ID 不能为空")
    @Size(max = 100, message = "用户 ID 长度不能超过 100")
    private String userId;

    /** 用户名称 (可空) */
    @Size(max = 100, message = "用户名称长度不能超过 100")
    private String userName;

    /** 用户角色 (可空) */
    @Size(max = 50, message = "用户角色长度不能超过 50")
    private String userRole;

    /** 评分 1-5 (可空, 仅 RATING 类型有效) */
    @Min(value = 1, message = "评分最小为 1")
    @Max(value = 5, message = "评分最大为 5")
    private Integer rating;

    /** 评论内容 (可空) */
    @Size(max = 2000, message = "评论内容长度不能超过 2000")
    private String comment;

    /** 评论类型 (可空): COMMENT/QUESTION/SUGGESTION/ISSUE */
    @Pattern(regexp = "COMMENT|QUESTION|SUGGESTION|ISSUE|",
            message = "评论类型仅支持 COMMENT/QUESTION/SUGGESTION/ISSUE")
    private String commentType;

    /** 父评论 ID (可空, 用于回复) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentCommentId;

    /** 是否内部备注 (默认 FALSE) */
    private Boolean isInternal;

    /** 举报原因 (可空, 仅 REPORT 类型有效) */
    @Size(max = 500, message = "举报原因长度不能超过 500")
    private String reportReason;

    /** 状态 (查询返回): ACTIVE/RESOLVED/DISMISSED/HIDDEN */
    private String status;

    /** JSON 附加数据 (可空) */
    private String metadata;
}
