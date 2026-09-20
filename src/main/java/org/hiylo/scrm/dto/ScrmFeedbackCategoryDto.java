/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackCategoryDto.java
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
 * SCRM 反馈分类 DTO。
 * <p>
 * 用于分类创建、更新、查询返回。创建时必填分类名称与编码; 默认优先级缺省 MEDIUM,
 * SLA 时长缺省 48 小时, 启用状态缺省 TRUE。
 * </p>
 *
 * @author Hsi Chu
 */
@Data
public class ScrmFeedbackCategoryDto {

    /** 主键 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 100, message = "分类名称长度不能超过 100")
    private String categoryName;

    /** 分类编码 (唯一) */
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 50, message = "分类编码长度不能超过 50")
    private String categoryCode;

    /** 分类描述 (可空) */
    @Size(max = 500, message = "分类描述长度不能超过 500")
    private String description;

    /** 适用反馈类型 (可空, 逗号分隔: SUGGESTION,COMPLAINT,...) */
    @Size(max = 500, message = "适用反馈类型长度不能超过 500")
    private String applicableTypes;

    /** 默认优先级: URGENT / HIGH / MEDIUM / LOW */
    @Size(max = 10, message = "默认优先级长度不能超过 10")
    private String defaultPriority;

    /** 默认处理人 ID (可空) */
    @Size(max = 100, message = "默认处理人 ID 长度不能超过 100")
    private String defaultAssigneeId;

    /** 默认处理团队 ID (可空) */
    @Size(max = 100, message = "默认处理团队 ID 长度不能超过 100")
    private String defaultTeamId;

    /** SLA 响应时长 (小时) */
    private Integer slaHours;

    /** 自动标签 (可空, 逗号分隔) */
    @Size(max = 200, message = "自动标签长度不能超过 200")
    private String autoTag;

    /** 排序值 (数字越小越靠前) */
    private Integer sortOrder;

    /** 反馈计数 */
    private Integer feedbackCount;

    /** 是否启用 */
    private Boolean enabled;

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
