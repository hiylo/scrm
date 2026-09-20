/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocVoiceDto.java
 * Date : 2026/08/05 08:55:12
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
 * SCRM VoC 声音 DTO。
 * <p>
 * 用于声音创建、更新、查询返回。创建时必填来源渠道、声音类型、内容; 优先级、状态、情感、语言
 * 缺省由服务端补全 (MEDIUM / NEW / NEUTRAL / zh-CN), isPublic/isVerified 缺省 FALSE, 计数缺省 0,
 * 收集时间缺省为当前时间。更新时字段非空才覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmVocVoiceDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 声音编号 (创建时由服务端生成, 入参忽略) */
    private String voiceNo;

    /** 客户 ID (可空, 匿名声音时为空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long customerId;

    /** 客户名称 (可空) */
    @Size(max = 200, message = "客户名称长度不能超过 200")
    private String customerName;

    /** 客户联系方式 (可空) */
    @Size(max = 200, message = "客户联系方式长度不能超过 200")
    private String customerContact;

/** 来源渠道: SURVEY/INTERVIEW/REVIEW/SOCIAL_MEDIA/CALL_CENTER/EMAIL/CHAT/APP_STORE/REFERRAL/COMPLAINT/SUGGESTION/OTHER
         * */
    @NotBlank(message = "来源渠道不能为空")
    @Size(max = 30, message = "来源渠道长度不能超过 30")
    private String source;

    /** 来源详情 (可空) */
    @Size(max = 200, message = "来源详情长度不能超过 200")
    private String sourceDetail;

    /** 来源链接 (可空) */
    @Size(max = 500, message = "来源链接长度不能超过 500")
    private String sourceUrl;

    /** 声音类型: COMPLAINT/COMPLIMENT/SUGGESTION/QUESTION/FEEDBACK/REVIEW/RATING/INQUIRY */
    @NotBlank(message = "声音类型不能为空")
    @Size(max = 30, message = "声音类型长度不能超过 30")
    private String voiceType;

    /** 主题 (可空) */
    @Size(max = 500, message = "主题长度不能超过 500")
    private String title;

    /** 声音内容 */
    @NotBlank(message = "声音内容不能为空")
    private String content;

    /** 原始内容 (可空) */
    private String originalContent;

    /** 语言 (默认 zh-CN) */
    @Size(max = 20, message = "语言长度不能超过 20")
    private String language;

    /** 情感: POSITIVE/NEUTRAL/NEGATIVE/MIXED (可空, 服务端按内容自动分析) */
    @Size(max = 20, message = "情感长度不能超过 20")
    private String sentiment;

    /** 情感得分 (-1.0 ~ 1.0) */
    private Double sentimentScore;

    /** 优先级: LOW/MEDIUM/HIGH/URGENT */
    @Size(max = 20, message = "优先级长度不能超过 20")
    private String priority;

    /** 分类 (可空) */
    @Size(max = 100, message = "分类长度不能超过 100")
    private String category;

    /** 子分类 (可空) */
    @Size(max = 100, message = "子分类长度不能超过 100")
    private String subCategory;

    /** 标签 (可空, 逗号分隔) */
    @Size(max = 500, message = "标签长度不能超过 500")
    private String tags;

    /** 评分 (1-5, 可空) */
    private Integer rating;

    /** 关联产品 ID (可空) */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;

    /** 关联产品名称 (可空) */
    @Size(max = 200, message = "产品名称长度不能超过 200")
    private String productName;

    /** 关联订单 ID (可空) */
    @Size(max = 100, message = "订单 ID 长度不能超过 100")
    private String orderId;

    /** 关联服务 ID (可空) */
    @Size(max = 100, message = "服务 ID 长度不能超过 100")
    private String serviceId;

    /** 责任部门 (可空) */
    @Size(max = 200, message = "责任部门长度不能超过 200")
    private String department;

    /** 处理人 (可空) */
    @Size(max = 100, message = "处理人长度不能超过 100")
    private String assignedTo;

    /** 分配时间 (可空) */
    private LocalDateTime assignedAt;

    /** 状态: NEW/ANALYZING/ASSIGNED/IN_PROGRESS/RESOLVED/CLOSED/ARCHIVED/IGNORED */
    @Size(max = 20, message = "状态长度不能超过 20")
    private String status;

    /** 收集时间 (可空, 缺省为当前时间) */
    private LocalDateTime collectedAt;

    /** 收集人 (可空) */
    @Size(max = 100, message = "收集人长度不能超过 100")
    private String collectedBy;

    /** 分析时间 (可空) */
    private LocalDateTime analyzedAt;

    /** 解决时间 (可空) */
    private LocalDateTime resolvedAt;

    /** 解决时长 (小时) */
    private Integer resolutionTimeHours;

    /** 解决方案 (可空) */
    @Size(max = 2000, message = "解决方案长度不能超过 2000")
    private String resolution;

    /** 解决后满意度 (1-5, 可空) */
    private Integer customerSatisfaction;

    /** 是否公开 */
    private Boolean isPublic;

    /** 是否已验证 */
    private Boolean isVerified;

    /** 验证人 (可空) */
    @Size(max = 100, message = "验证人长度不能超过 100")
    private String verifiedBy;

    /** 回复数 */
    private Integer responseCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 查看数 */
    private Integer viewCount;

    /** 分享数 */
    private Integer shareCount;

    /** 附件 (可空, JSON 数组) */
    @Size(max = 1000, message = "附件长度不能超过 1000")
    private String attachments;

    /** 附加数据 (可空, JSON) */
    private String metadata;

    /** 关联声音 ID 列表 (可空, 逗号分隔) */
    @Size(max = 500, message = "关联声音 ID 列表长度不能超过 500")
    private String relatedVoiceIds;

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
