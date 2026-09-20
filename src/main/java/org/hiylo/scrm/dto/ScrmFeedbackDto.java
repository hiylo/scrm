/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackDto.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SCRM 客户反馈 DTO。
 * <p>
 * 用于反馈创建、更新、查询返回。创建时必填标题、内容与反馈类型; 优先级、状态、来源缺省
 * 由服务端补全 (MEDIUM / NEW / CUSTOMER)。更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmFeedbackDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 反馈编号 (创建时由服务端生成, 入参忽略) */
    private String feedbackNo;

    /** 反馈标题 */
    @NotBlank(message = "反馈标题不能为空")
    @Size(max = 200, message = "反馈标题长度不能超过 200")
    private String title;

    /** 反馈内容 */
    @NotBlank(message = "反馈内容不能为空")
    private String content;

/** 反馈类型: SUGGESTION / COMPLAINT / COMPLIMENT / BUG_REPORT / FEATURE_REQUEST / SERVICE_ISSUE / PRODUCT_ISSUE / OTHER
         * */
    @NotBlank(message = "反馈类型不能为空")
    @Size(max = 30, message = "反馈类型长度不能超过 30")
    private String feedbackType;

    /** 反馈分类 (可空) */
    @Size(max = 100, message = "反馈分类长度不能超过 100")
    private String category;

    /** 优先级: URGENT / HIGH / MEDIUM / LOW */
    @Size(max = 10, message = "优先级长度不能超过 10")
    private String priority;

    /** 状态: NEW / IN_REVIEW / IN_PROGRESS / RESOLVED / CLOSED / REJECTED / DUPLICATE */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 来源: CUSTOMER / AGENT / SYSTEM / SURVEY / SOCIAL / EMAIL / PHONE / CHAT */
    @Size(max = 30, message = "来源长度不能超过 30")
    private String source;

    /** 客户 ID (可空, 匿名反馈时为空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 客户电话 (可空) */
    @Size(max = 50, message = "客户电话长度不能超过 50")
    private String customerPhone;

    /** 客户邮箱 (可空) */
    @Size(max = 200, message = "客户邮箱长度不能超过 200")
    private String customerEmail;

    /** 客户等级 (可空) */
    @Size(max = 50, message = "客户等级长度不能超过 50")
    private String customerLevel;

    /** 关联订单 ID (可空) */
    @Size(max = 100, message = "关联订单 ID 长度不能超过 100")
    private String orderId;

    /** 关联产品 ID (可空) */
    @Size(max = 100, message = "关联产品 ID 长度不能超过 100")
    private String productId;

    /** 关联工单 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ticketId;

    /** 情感: POSITIVE / NEUTRAL / NEGATIVE (可空) */
    @Size(max = 20, message = "情感长度不能超过 20")
    private String sentiment;

    /** 情感得分 (-1.0 ~ 1.0) */
    private Double sentimentScore;

    /** 评分 (1-5, 可空) */
    private Integer rating;

    /** 标签 (可空, 逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 附件列表 (可空, JSON 数组) */
    @Size(max = 1000, message = "附件列表长度不能超过 1000")
    private String attachments;

    /** 处理人 ID (可空) */
    @Size(max = 100, message = "处理人 ID 长度不能超过 100")
    private String assigneeId;

    /** 处理人名称 (可空) */
    @Size(max = 100, message = "处理人名称长度不能超过 100")
    private String assigneeName;

    /** 处理团队 ID (可空) */
    @Size(max = 100, message = "处理团队 ID 长度不能超过 100")
    private String teamId;

    /** 分配时间 (可空) */
    private LocalDateTime assignedAt;

    /** 首次响应时间 (可空) */
    private LocalDateTime firstResponseAt;

    /** 解决时间 (可空) */
    private LocalDateTime resolvedAt;

    /** 关闭时间 (可空) */
    private LocalDateTime closedAt;

    /** 响应时长 (小时, 可空) */
    private Integer responseTimeHours;

    /** 解决时长 (小时, 可空) */
    private Integer resolutionTimeHours;

    /** 解决方案 (可空) */
    @Size(max = 1000, message = "解决方案长度不能超过 1000")
    private String resolution;

    /** 处理满意度评分 (1-5, 可空) */
    private Integer satisfactionScore;

    /** 是否公开 */
    private Boolean isPublic;

    /** 是否匿名 */
    private Boolean isAnonymous;

    /** 浏览数 */
    private Integer viewCount;

    /** 赞同数 */
    private Integer upvoteCount;

    /** 评论数 */
    private Integer commentCount;

    /** 创建人 (可空) */
    @Size(max = 100, message = "创建人长度不能超过 100")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    private Long version;
}
